package com.zackzzq.maidodyssey.maid.behavior;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidMoveToBlockTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidPathFindingBFS;
import com.zackzzq.maidodyssey.MaidOdysseyConfig;
import com.zackzzq.maidodyssey.gt.GtCompat;
import com.zackzzq.maidodyssey.gt.GtJob;
import com.zackzzq.maidodyssey.report.MaidReporter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;

/**
 * Looks for a GregTech machine that needs attention and walks the maid over to it.
 * <p>
 * The base class handles the spiral scan, the work range and the check throttle; all this adds is
 * the "does this block need work" test plus reachability of the blocks <em>around</em> the machine,
 * since a machine block itself can never be stood on.
 */
public class MaidGtSearchTask extends MaidMoveToBlockTask {
    private final Set<GtJob> jobs;
    private final GtTaskContext context;

    public MaidGtSearchTask(Set<GtJob> jobs, GtTaskContext context, float movementSpeed) {
        super(movementSpeed, IMaidTask.VERTICAL_SEARCH_RANGE);
        this.jobs = jobs;
        this.context = context;
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        context.prune(gameTime);
        if (!GtCompat.isLoaded()) {
            MaidReporter.problem(maid, null, "message.maid_odyssey.gt.not_installed");
            return;
        }
        searchForDestination(level, maid);
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
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (pathFinding.canPathReach(pos.offset(x, y, z))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    protected int getHorizontalSearchRange(EntityMaid maid) {
        return Math.min(super.getHorizontalSearchRange(maid), MaidOdysseyConfig.maxSearchRadius());
    }
}
