package com.ziqizhu.maidodyssey.maid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import com.ziqizhu.maidodyssey.MaidOdysseyConfig;
import com.ziqizhu.maidodyssey.gt.GtCompat;
import com.ziqizhu.maidodyssey.hazard.HazardTracker;
import com.ziqizhu.maidodyssey.maid.work.AshEating;
import com.ziqizhu.maidodyssey.report.MaidReporter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.jetbrains.annotations.Nullable;

/**
 * CORE behaviour: keep the maid out of muffler exhaust and GTO heat while she walks or wanders.
 * <p>
 * Random stroll lives on IDLE/WORK/REST and writes {@code WALK_TARGET}; CORE
 * {@code MoveToTargetSink} (priority 2) then consumes it. This runs at priority 1 so a dangerous
 * destination is rewritten before she takes a step into it.
 */
public class MaidAvoidHazardTask extends Behavior<EntityMaid> {
    private static final float FLEE_SPEED = 0.65F;
    private static final int FLEE_SEARCH = 12;

    public MaidAvoidHazardTask() {
        super(ImmutableMap.of(), 1);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        return MaidOdysseyConfig.avoidHazards() && GtCompat.isLoaded();
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        HazardTracker hazards = HazardTracker.of(maid);
        hazards.refresh(level, maid);

        BlockPos feet = maid.blockPosition();
        boolean inDanger = hazards.isOccupied(feet);
        if (AshEating.isBusy(maid) && !inDanger) {
            cancelWalk(maid);
            return;
        }
        if (maid.isSleeping() && !inDanger) {
            return;
        }

        if (inDanger) {
            leaveSitOrMount(maid);
            BlockPos safe = findSafeSpot(level, maid, hazards);
            if (safe != null) {
                BehaviorUtils.setWalkAndLookTargetMemories(maid, safe, FLEE_SPEED, 0);
                MaidReporter.problem(maid, feet, "message.maid_odyssey.hazard.flee",
                        MaidReporter.red(Component.translatable("message.maid_odyssey.hazard.poison")),
                        MaidReporter.red(Component.translatable("message.maid_odyssey.hazard.burn")));
            } else {
                cancelWalk(maid);
            }
            return;
        }

        if (walkTargetDangerous(maid, hazards) || pathGoesThroughDanger(maid, hazards)) {
            BlockPos safe = findSafeSpot(level, maid, hazards);
            if (safe != null) {
                BehaviorUtils.setWalkAndLookTargetMemories(maid, safe, FLEE_SPEED, 1);
            } else {
                cancelWalk(maid);
            }
        }
    }

    private static boolean walkTargetDangerous(EntityMaid maid, HazardTracker hazards) {
        return maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                .map(WalkTarget::getTarget)
                .map(target -> hazards.isOccupied(target.currentBlockPosition()))
                .orElse(false);
    }

    private static boolean pathGoesThroughDanger(EntityMaid maid, HazardTracker hazards) {
        Path path = maid.getNavigation().getPath();
        if (path == null) {
            return false;
        }
        int start = Math.max(path.getNextNodeIndex(), 0);
        for (int i = start; i < path.getNodeCount(); i++) {
            Node node = path.getNode(i);
            if (hazards.isOccupied(node.asBlockPos())) {
                return true;
            }
        }
        return false;
    }

    private static void cancelWalk(EntityMaid maid) {
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getNavigation().stop();
    }

    private static void leaveSitOrMount(EntityMaid maid) {
        if (maid.isMaidInSittingPose()) {
            maid.setInSittingPose(false);
        }
        if (maid.isPassenger()) {
            maid.stopRiding();
        }
    }

    @Nullable
    private static BlockPos findSafeSpot(ServerLevel level, EntityMaid maid, HazardTracker hazards) {
        BlockPos origin = maid.blockPosition();
        int max = FLEE_SEARCH;
        if (maid.hasRestriction()) {
            max = Math.min(max, Math.max(2, (int) maid.getRestrictRadius()));
        }
        for (int radius = 1; radius <= max; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos candidate = origin.offset(dx, dy, dz);
                        if (!isUsableSpot(level, maid, hazards, candidate)) {
                            continue;
                        }
                        return candidate.immutable();
                    }
                }
            }
        }
        return null;
    }

    private static boolean isUsableSpot(ServerLevel level, EntityMaid maid, HazardTracker hazards, BlockPos pos) {
        if (hazards.isOccupied(pos)) {
            return false;
        }
        if (maid.hasRestriction() && !maid.isWithinRestriction(pos)) {
            return false;
        }
        if (!level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }
        BlockPathTypes type = WalkNodeEvaluator.getBlockPathTypeStatic(level, pos.mutable());
        return type == BlockPathTypes.WALKABLE || type == BlockPathTypes.WALKABLE_DOOR;
    }
}
