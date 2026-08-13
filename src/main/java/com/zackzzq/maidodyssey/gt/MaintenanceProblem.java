package com.zackzzq.maidodyssey.gt;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * The six maintenance problems of a GregTech maintenance hatch.
 * <p>
 * GregTech stores them as a six bit mask where a <em>set</em> bit means the problem is already
 * fixed. The bit order below is the one hardcoded in {@code MaintenanceHatchPartMachine}.
 */
public enum MaintenanceProblem {
    WRENCH(0, "wrench", "WRENCH",
            "gtceu:tools/crafting_wrenches", "forge:tools/wrenches", "forge:tools/wrench"),
    SCREWDRIVER(1, "screwdriver", "SCREWDRIVER",
            "gtceu:tools/crafting_screwdrivers", "forge:tools/screwdrivers", "forge:tools/screwdriver"),
    SOFT_MALLET(2, "soft_mallet", "SOFT_MALLET",
            "gtceu:tools/crafting_mallets", "forge:tools/mallets", "forge:tools/mallet"),
    HARD_HAMMER(3, "hard_hammer", "HARD_HAMMER",
            "gtceu:tools/crafting_hammers", "forge:tools/hammers", "forge:tools/hammer"),
    WIRE_CUTTER(4, "wire_cutter", "WIRE_CUTTER",
            "gtceu:tools/crafting_wire_cutters", "forge:tools/wire_cutters", "forge:tools/wire_cutter"),
    CROWBAR(5, "crowbar", "CROWBAR",
            "gtceu:tools/crafting_crowbars", "forge:tools/crowbars", "forge:tools/crowbar");

    public static final MaintenanceProblem[] VALUES = values();

    private final int index;
    private final String key;
    private final String gtToolTypeField;
    private final List<TagKey<Item>> fallbackTags;

    MaintenanceProblem(int index, String key, String gtToolTypeField, String... tags) {
        this.index = index;
        this.key = key;
        this.gtToolTypeField = gtToolTypeField;
        this.fallbackTags = java.util.Arrays.stream(tags)
                .map(id -> TagKey.create(Registries.ITEM, new ResourceLocation(id)))
                .toList();
    }

    public int index() {
        return index;
    }

    /** Name of the {@code public static final GTToolType} field this problem needs. */
    public String gtToolTypeField() {
        return gtToolTypeField;
    }

    /**
     * Item tags used to recognise the tool when GregTech's own {@code ToolHelper} cannot be
     * reached through reflection.
     */
    public List<TagKey<Item>> fallbackTags() {
        return fallbackTags;
    }

    public MutableComponent displayName() {
        return Component.translatable("message.maid_odyssey.tool." + key);
    }

    /** @return true when this problem is still broken in the given GregTech problem mask. */
    public boolean isBroken(byte mask) {
        return ((mask >> index) & 1) == 0;
    }
}
