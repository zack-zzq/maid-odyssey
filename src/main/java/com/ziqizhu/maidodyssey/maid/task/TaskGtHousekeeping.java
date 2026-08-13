package com.ziqizhu.maidodyssey.maid.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import com.ziqizhu.maidodyssey.MaidOdyssey;
import com.ziqizhu.maidodyssey.gt.GtJob;
import com.ziqizhu.maidodyssey.maid.work.MaintenanceWork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Both chores at once, for a maid dedicated to looking after a whole factory floor. */
public class TaskGtHousekeeping extends AbstractGtTask {
    public static final ResourceLocation UID = MaidOdyssey.id("gt_housekeeping");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    protected Set<GtJob> jobs() {
        return GtJob.ALL;
    }

    @Override
    protected String[] iconCandidates() {
        return new String[]{"gtceu:duct_tape", "gtceu:maintenance_hatch"};
    }

    @Override
    protected Item fallbackIcon() {
        return Items.SHEARS;
    }

    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) {
        return Lists.newArrayList(Pair.of("has_tool", MaintenanceWork::hasAnyTool));
    }

    @Override
    public String getMaidActionSummary() {
        return "Clean GregTech muffler hatch ash and repair maintenance hatches around the factory";
    }
}
