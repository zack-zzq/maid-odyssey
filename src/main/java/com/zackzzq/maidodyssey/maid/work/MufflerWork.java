package com.zackzzq.maidodyssey.maid.work;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.zackzzq.maidodyssey.MaidOdysseyConfig;
import com.zackzzq.maidodyssey.gt.GtCompat;
import com.zackzzq.maidodyssey.report.MaidReporter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/** Empties the ash out of a GregTech Odyssey muffler hatch. */
public final class MufflerWork {
    private MufflerWork() {
    }

    /**
     * @return true when the hatch still holds ash the maid could not take, so the caller should
     * stop bothering it for a while.
     */
    public static boolean clean(EntityMaid maid, Object machine, BlockPos pos) {
        IItemHandler hatch = GtCompat.getMufflerInventory(machine);
        if (hatch == null) {
            MaidReporter.problem(maid, pos, "message.maid_odyssey.gt.api_mismatch",
                    Component.literal(GtCompat.bindingProblem()));
            return true;
        }

        boolean destroyAsh = MaidOdysseyConfig.ashHandling() == MaidOdysseyConfig.AshHandling.VOID;
        IItemHandler backpack = maid.getAvailableInv(false);
        int taken = 0;
        boolean leftBehind = false;

        for (int slot = 0; slot < hatch.getSlots(); slot++) {
            int stored = hatch.getStackInSlot(slot).getCount();
            if (stored <= 0) {
                continue;
            }
            if (destroyAsh) {
                taken += hatch.extractItem(slot, stored, false).getCount();
                continue;
            }

            ItemStack preview = hatch.extractItem(slot, stored, true);
            if (preview.isEmpty()) {
                continue;
            }
            int accepted = preview.getCount()
                    - ItemHandlerHelper.insertItemStacked(backpack, preview.copy(), true).getCount();
            if (accepted <= 0) {
                leftBehind = true;
                continue;
            }

            ItemStack removed = hatch.extractItem(slot, accepted, false);
            int removedCount = removed.getCount();
            ItemStack rejected = ItemHandlerHelper.insertItemStacked(backpack, removed, false);
            taken += removedCount - rejected.getCount();
            if (!rejected.isEmpty()) {
                // Should not happen after the simulation above, but never void a player's items.
                ItemHandlerHelper.insertItemStacked(hatch, rejected, false);
                leftBehind = true;
            }
            if (accepted < preview.getCount()) {
                leftBehind = true;
            }
        }

        if (taken > 0) {
            maid.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            MaidReporter.success(maid, pos, destroyAsh
                    ? "message.maid_odyssey.muffler.voided"
                    : "message.maid_odyssey.muffler.cleaned", taken);
        }
        if (leftBehind) {
            MaidReporter.problem(maid, pos, "message.maid_odyssey.muffler.backpack_full");
        }
        return leftBehind;
    }
}
