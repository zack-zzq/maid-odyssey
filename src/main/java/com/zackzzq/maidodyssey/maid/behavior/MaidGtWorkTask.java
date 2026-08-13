package com.zackzzq.maidodyssey.maid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.google.common.collect.ImmutableMap;
import com.zackzzq.maidodyssey.MaidOdysseyConfig;
import com.zackzzq.maidodyssey.gt.GtCompat;
import com.zackzzq.maidodyssey.gt.GtJob;
import com.zackzzq.maidodyssey.maid.work.MaintenanceWork;
import com.zackzzq.maidodyssey.maid.work.MufflerWork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Set;

/**
 * Runs once the maid has arrived next to the machine picked by {@link MaidGtSearchTask}.
 * <p>
 * Both memories are erased when we are done so the search behavior is free to pick the next
 * machine, which is the handshake every Touhou Little Maid block task uses.
 */
public class MaidGtWorkTask extends Behavior<EntityMaid> {
    /** Machines are solid, so the maid stops next to them rather than on them. */
    private static final double CLOSE_ENOUGH = 3.5D;

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
        return brain.getMemory(InitEntities.TARGET_POS.get()).map(target -> {
            Vec3 targetPosition = target.currentPosition();
            if (maid.distanceToSqr(targetPosition) <= CLOSE_ENOUGH * CLOSE_ENOUGH) {
                return true;
            }
            // Still walking. If the walk target got dropped we will never arrive, so let the
            // search behavior start over.
            Optional<WalkTarget> walkTarget = brain.getMemory(MemoryModuleType.WALK_TARGET);
            if (walkTarget.isEmpty() || !walkTarget.get().getTarget().currentPosition().equals(targetPosition)) {
                brain.eraseMemory(InitEntities.TARGET_POS.get());
            }
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
