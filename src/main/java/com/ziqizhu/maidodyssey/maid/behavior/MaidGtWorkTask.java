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
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Runs once the maid is within reach of the machine picked by {@link MaidGtSearchTask}.
 * <p>
 * Work is a one-shot: no facing, no settle wait. Those were fighting the look AI (head bobbing)
 * and could skip the actual clean/eat.
 */
public class MaidGtWorkTask extends Behavior<EntityMaid> {
    private final Set<GtJob> jobs;
    private final GtTaskContext context;

    public MaidGtWorkTask(Set<GtJob> jobs, GtTaskContext context) {
        super(ImmutableMap.of(InitEntities.TARGET_POS.get(), MemoryStatus.VALUE_PRESENT));
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
            if (inWorkReach(maid, targetPosition)) {
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
        maid.getBrain().getMemory(InitEntities.TARGET_POS.get()).ifPresent(target -> {
            BlockPos pos = target.currentBlockPosition();
            if (workOn(level, maid, pos)) {
                context.ignore(pos, gameTime, MaidOdysseyConfig.blockedRetryDelay());
            }
        });
        maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    /** @return true when the machine could not be fully serviced and should be skipped for a while. */
    private boolean workOn(ServerLevel level, EntityMaid maid, BlockPos pos) {
        Object machine = GtCompat.getMachine(level, pos);
        if (machine == null) {
            return false;
        }
        if (jobs.contains(GtJob.MUFFLER) && GtCompat.isMuffler(machine)) {
            MufflerWork.clean(maid, machine, pos);
            // Leftover ash is eaten on later visits; parking the hatch would freeze a standing maid.
            return false;
        }
        if (jobs.contains(GtJob.MAINTENANCE) && GtCompat.isMaintenance(machine)
                && !GtCompat.isFullAutoMaintenance(machine)) {
            return MaintenanceWork.repair(maid, machine, pos);
        }
        return false;
    }

    static boolean inWorkReach(EntityMaid maid, BlockPos machine) {
        return inWorkReach(maid, Vec3.atBottomCenterOf(machine));
    }

    static boolean inWorkReach(EntityMaid maid, Vec3 target) {
        double reach = MaidOdysseyConfig.workReach() + 0.75D;
        return maid.distanceToSqr(target) <= reach * reach;
    }
}
