package com.zackzzq.maidodyssey.maid.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.datafixers.util.Pair;
import com.zackzzq.maidodyssey.MaidOdyssey;
import com.zackzzq.maidodyssey.gt.GtJob;
import com.zackzzq.maidodyssey.maid.work.MaintenanceWork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Walks around the base repairing maintenance hatches with the tools in the maid's backpack. */
public class TaskMaintenanceHatch extends AbstractGtTask {
    public static final ResourceLocation UID = MaidOdyssey.id("maintenance_hatch");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    protected Set<GtJob> jobs() {
        return GtJob.ONLY_MAINTENANCE;
    }

    @Override
    protected String[] iconCandidates() {
        return new String[]{"gtceu:maintenance_hatch", "gtceu:configurable_maintenance_hatch"};
    }

    @Override
    protected Item fallbackIcon() {
        return Items.ANVIL;
    }

    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) {
        return Collections.singletonList(Pair.of("has_tool", MaintenanceWork::hasAnyTool));
    }

    @Override
    public String getMaidActionSummary() {
        return "Repair broken GregTech maintenance hatches using the tools carried in the backpack";
    }
}
