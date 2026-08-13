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
 * {@code TARGET_POS} is the machine, {@code WALK_TARGET} is a standable block near it — they are
 * deliberately different, so this must not treat a mismatched walk target as "give up".
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
        maid.getBrain().getMemory(InitEntities.TARGET_POS.get()).ifPresent(target -> {
            BlockPos pos = target.currentBlockPosition();
            if (workOn(level, maid, pos)) {
                context.ignore(pos, gameTime, MaidOdysseyConfig.blockedRetryDelay());
            }
            maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        });
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
}
