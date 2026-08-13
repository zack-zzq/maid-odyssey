package com.zackzzq.maidodyssey.maid;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.zackzzq.maidodyssey.MaidOdyssey;
import com.zackzzq.maidodyssey.gt.GtCompat;
import com.zackzzq.maidodyssey.maid.task.TaskCleanMuffler;
import com.zackzzq.maidodyssey.maid.task.TaskGtHousekeeping;
import com.zackzzq.maidodyssey.maid.task.TaskMaintenanceHatch;

/**
 * Touhou Little Maid discovers this class by scanning for the annotation, so it needs to stay
 * public with a no-argument constructor.
 */
@LittleMaidExtension
public class LittleMaidPlugin implements ILittleMaid {
    @Override
    public void addMaidTask(TaskManager manager) {
        manager.add(new TaskCleanMuffler());
        manager.add(new TaskMaintenanceHatch());
        manager.add(new TaskGtHousekeeping());
        MaidOdyssey.LOGGER.info("Registered GregTech maid tasks (GregTech present: {})", GtCompat.isLoaded());
    }
}
