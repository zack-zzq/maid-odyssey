package com.ziqizhu.maidodyssey.maid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidMoveToBlockTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidPathFindingBFS;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.ziqizhu.maidodyssey.MaidOdysseyConfig;
import com.ziqizhu.maidodyssey.gt.GtCompat;
import com.ziqizhu.maidodyssey.gt.GtJob;
import com.ziqizhu.maidodyssey.hazard.HazardTracker;
import com.ziqizhu.maidodyssey.maid.work.AshEating;
import com.ziqizhu.maidodyssey.report.MaidReporter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Looks for a GregTech machine that needs attention and walks the maid to a standable block
 * within reach of it.
 * <p>
 * The parent always sets the walk target to the machine itself, which a roof-mounted muffler
 * hatch is not: the maid cannot stand on it, and the 3×3×2 neighbourhood around it is usually
 * more of the multiblock. After the scan we rewrite the walk target to the standing spot we
 * already found, while leaving {@code TARGET_POS} pointing at the machine so the work behavior
 * knows what to service.
 */
public class MaidGtSearchTask extends MaidMoveToBlockTask {
    private final Set<GtJob> jobs;
    private final GtTaskContext context;
    private final float movementSpeed;

    /** Standing spot of the machine the scan just picked, or null. */
    @Nullable
    private BlockPos lastStandingSpot;

    public MaidGtSearchTask(Set<GtJob> jobs, GtTaskContext context, float movementSpeed) {
        super(movementSpeed, MaidOdysseyConfig.verticalSearchRange());
        this.jobs = jobs;
        this.context = context;
        this.movementSpeed = movementSpeed;
        setMaxCheckRate(40);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (AshEating.isBusy(maid)) {
            return false;
        }
        return super.checkExtraStartConditions(level, maid);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        context.prune(gameTime);
        lastStandingSpot = null;
        if (!GtCompat.isLoaded()) {
            MaidReporter.problem(maid, null, "message.maid_odyssey.gt.not_installed");
            return;
        }
        searchForDestination(level, maid);
        if (lastStandingSpot != null) {
            BehaviorUtils.setWalkAndLookTargetMemories(maid, lastStandingSpot, movementSpeed, 0);
            maid.getBrain().getMemory(InitEntities.TARGET_POS.get()).ifPresent(target ->
                    maid.getBrain().setMemory(MemoryModuleType.LOOK_TARGET,
                            new BlockPosTracker(target.currentBlockPosition())));
        }
    }

    @Override
    protected boolean shouldMoveTo(ServerLevel level, EntityMaid maid, BlockPos pos) {
        if (context.isIgnored(pos, level.getGameTime())) {
            return false;
        }
        return GtCompat.findJob(level, pos, jobs) != null;
    }

    @Override
    protected boolean checkPathReach(EntityMaid maid, MaidPathFindingBFS pathFinding, BlockPos pos) {
        BlockPos spot = findStandingSpot(pathFinding, maid, pos);
        if (spot == null) {
            lastStandingSpot = null;
            return false;
        }
        lastStandingSpot = spot;
        return true;
    }

    /**
     * Closest block the maid can actually stand in that is still within {@code workReach} of the
     * machine. Searching includes the ground several blocks below, which is where she ends up for
     * a muffler hatch sitting on the roof of a blast furnace.
     */
    @Nullable
    static BlockPos findStandingSpot(MaidPathFindingBFS pathFinding, EntityMaid maid, BlockPos machine) {
        int reach = MaidOdysseyConfig.workReach();
        BlockPos maidPos = maid.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int y = -reach; y <= 1; y++) {
            for (int x = -reach; x <= reach; x++) {
                for (int z = -reach; z <= reach; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    if (x * x + y * y + z * z > reach * reach) {
                        continue;
                    }
                    BlockPos candidate = machine.offset(x, y, z);
                    if (maid.level() instanceof ServerLevel serverLevel
                            && HazardTracker.isUnsafeStanding(serverLevel, maid, candidate, machine)) {
                        continue;
                    }
                    if (!pathFinding.canPathReach(candidate)) {
                        continue;
                    }
                    double distance = candidate.distSqr(maidPos);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = candidate.immutable();
                    }
                }
            }
        }
        return best;
    }

    @Override
    protected int getHorizontalSearchRange(EntityMaid maid) {
        return Math.min(super.getHorizontalSearchRange(maid), MaidOdysseyConfig.maxSearchRadius());
    }
}
