package com.ziqizhu.maidodyssey.maid;

import com.github.tartaricacid.touhoulittlemaid.api.entity.ai.IExtraMaidBrain;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import com.ziqizhu.maidodyssey.maid.behavior.MaidAvoidHazardTask;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;

import java.util.List;

/**
 * Injects hazard avoidance into every activity. Priority 1 runs before CORE
 * {@code MoveToTargetSink} (priority 2), so a dangerous stroll destination is rewritten
 * before the maid walks into it.
 */
public final class HazardAvoidBrain implements IExtraMaidBrain {
    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> getCoreBehaviors() {
        return Lists.newArrayList(Pair.of(1, new MaidAvoidHazardTask()));
    }
}
