package com.ziqizhu.maidodyssey.maid;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ExtraMaidBrainManager;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.ziqizhu.maidodyssey.MaidOdyssey;
import com.ziqizhu.maidodyssey.gt.GtCompat;
import com.ziqizhu.maidodyssey.maid.task.TaskCleanMuffler;
import com.ziqizhu.maidodyssey.maid.task.TaskGtHousekeeping;
import com.ziqizhu.maidodyssey.maid.task.TaskMaintenanceHatch;

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

    @Override
    public void addExtraMaidBrain(ExtraMaidBrainManager manager) {
        manager.addExtraMaidBrain(new HazardAvoidBrain());
    }
}
