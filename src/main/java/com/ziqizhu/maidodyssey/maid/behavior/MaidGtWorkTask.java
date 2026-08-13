package com.ziqizhu.maidodyssey.maid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.google.common.collect.ImmutableMap;
import com.ziqizhu.maidodyssey.MaidOdysseyConfig;
import com.ziqizhu.maidodyssey.gt.GtCompat;
import com.ziqizhu.maidodyssey.gt.GtJob;
import com.ziqizhu.maidodyssey.maid.work.AshEating;
import com.ziqizhu.maidodyssey.maid.work.MaintenanceWork;
import com.ziqizhu.maidodyssey.maid.work.MufflerWork;
import com.ziqizhu.maidodyssey.report.MaidReporter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Runs once the maid is within reach of the machine picked by {@link MaidGtSearchTask}.
 * <p>
 * She stops, lets the vanilla look AI turn her toward the hatch, then works. Head pitch is left
 * to {@code LookControl} so a roof muffler does not make her nod every tick.
 */
public class MaidGtWorkTask extends Behavior<EntityMaid> {
    private static final int SETTLE_TICKS = 10;
    private static final int MAX_DURATION = 30;

    private final Set<GtJob> jobs;
    private final GtTaskContext context;
    private boolean finished;
    private int settleTicks;

    public MaidGtWorkTask(Set<GtJob> jobs, GtTaskContext context) {
        super(ImmutableMap.of(InitEntities.TARGET_POS.get(), MemoryStatus.VALUE_PRESENT), MAX_DURATION);
        this.jobs = jobs;
        this.context = context;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        Brain<EntityMaid> brain = maid.getBrain();
        if (AshEating.isBusy(maid)) {
            return false;
        }
        return brain.getMemory(InitEntities.TARGET_POS.get()).map(target -> {
            Vec3 targetPosition = target.currentPosition();
            double reach = MaidOdysseyConfig.workReach() + 0.75D;
            if (maid.distanceToSqr(targetPosition) <= reach * reach) {
                return true;
            }
            if (maid.getNavigation().isInProgress() || brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                return false;
            }
            BlockPos pos = target.currentBlockPosition();
            MaidReporter.problem(maid, pos, "message.maid_odyssey.unreachable");
            context.ignore(pos, level.getGameTime(), MaidOdysseyConfig.unreachableRetryDelay());
            brain.eraseMemory(InitEntities.TARGET_POS.get());
            return false;
        }).orElse(false);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        finished = false;
        settleTicks = 0;
        maid.getNavigation().stop();
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        lookAtMachine(maid);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        return !finished && maid.getBrain().hasMemoryValue(InitEntities.TARGET_POS.get());
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        settleTicks++;
        if (settleTicks >= SETTLE_TICKS) {
            tryWork(level, maid, gameTime);
        }
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        if (!finished) {
            tryWork(level, maid, gameTime);
        }
    }

    private void tryWork(ServerLevel level, EntityMaid maid, long gameTime) {
        if (finished) {
            return;
        }
        maid.getBrain().getMemory(InitEntities.TARGET_POS.get()).ifPresent(target -> {
            BlockPos pos = target.currentBlockPosition();
            if (workOn(level, maid, pos)) {
                context.ignore(pos, gameTime, MaidOdysseyConfig.blockedRetryDelay());
            }
        });
        finished = true;
        maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    /** @return true when the machine could not be fully serviced. */
    private boolean workOn(ServerLevel level, EntityMaid maid, BlockPos pos) {
        Object machine = GtCompat.getMachine(level, pos);
        if (machine == null) {
            return false;
        }
        if (jobs.contains(GtJob.MUFFLER) && GtCompat.isMuffler(machine)) {
            return MufflerWork.clean(maid, machine, pos);
        }
        if (jobs.contains(GtJob.MAINTENANCE) && GtCompat.isMaintenance(machine)
                && !GtCompat.isFullAutoMaintenance(machine)) {
            return MaintenanceWork.repair(maid, machine, pos);
        }
        return false;
    }

    private static void lookAtMachine(EntityMaid maid) {
        maid.getBrain().getMemory(InitEntities.TARGET_POS.get()).ifPresent(target -> {
            BlockPos pos = target.currentBlockPosition();
            maid.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(pos));
            Vec3 aim = Vec3.atBottomCenterOf(pos);
            double dx = aim.x - maid.getX();
            double dz = aim.z - maid.getZ();
            if (dx * dx + dz * dz < 1.0E-4D) {
                return;
            }
            float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
            maid.setYBodyRot(yaw);
            maid.setYRot(yaw);
        });
    }
}
