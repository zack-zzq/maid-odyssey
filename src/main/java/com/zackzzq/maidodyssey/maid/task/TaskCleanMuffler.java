package com.zackzzq.maidodyssey.maid.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.datafixers.util.Pair;
import com.zackzzq.maidodyssey.MaidOdyssey;
import com.zackzzq.maidodyssey.MaidOdysseyConfig;
import com.zackzzq.maidodyssey.gt.GtJob;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandler;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Walks around the base emptying muffler hatches. */
public class TaskCleanMuffler extends AbstractGtTask {
    public static final ResourceLocation UID = MaidOdyssey.id("clean_muffler");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    protected Set<GtJob> jobs() {
        return GtJob.ONLY_MUFFLER;
    }

    @Override
    protected String[] iconCandidates() {
        return new String[]{"gtceu:lv_muffler_hatch", "gtceu:mv_muffler_hatch"};
    }

    @Override
    protected Item fallbackIcon() {
        return Items.HOPPER;
    }

    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) {
        return Collections.singletonList(Pair.of("has_room", TaskCleanMuffler::hasRoom));
    }

    @Override
    public String getMaidActionSummary() {
        return "Collect the ash that GregTech muffler hatches produce while the machines run";
    }

    private static boolean hasRoom(EntityMaid maid) {
        if (MaidOdysseyConfig.ashHandling() == MaidOdysseyConfig.AshHandling.VOID) {
            return true;
        }
        IItemHandler inventory = maid.getAvailableInv(false);
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getCount() < Math.min(stack.getMaxStackSize(), inventory.getSlotLimit(slot))) {
                return true;
            }
        }
        return false;
    }
}
