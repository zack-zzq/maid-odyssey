package com.ziqizhu.maidodyssey.maid.work;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import com.ziqizhu.maidodyssey.MaidOdysseyConfig;
import com.ziqizhu.maidodyssey.gt.GtCompat;
import com.ziqizhu.maidodyssey.gt.MaintenanceProblem;
import com.ziqizhu.maidodyssey.report.MaidReporter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;

import java.util.ArrayList;
import java.util.List;

/** Repairs a GregTech maintenance hatch with the tools the maid is carrying. */
public final class MaintenanceWork {
    private MaintenanceWork() {
    }

    /**
     * @return true when problems are left that the maid has no tool for, so the caller should stop
     * bothering this hatch for a while.
     */
    public static boolean repair(EntityMaid maid, Object machine, BlockPos pos) {
        byte mask = GtCompat.maintenanceMask(machine);
        CombinedInvWrapper inventory = maid.getAvailableInv(true);
        List<MaintenanceProblem> missing = new ArrayList<>();
        int repaired = 0;

        for (MaintenanceProblem problem : MaintenanceProblem.VALUES) {
            if (!problem.isBroken(mask)) {
                continue;
            }
            ItemStack tool = takeToolInHand(maid, inventory, problem);
            if (tool.isEmpty()) {
                missing.add(problem);
                continue;
            }
            if (!GtCompat.markProblemFixed(machine, problem.index())) {
                MaidReporter.problem(maid, pos, "message.maid_odyssey.gt.api_mismatch",
                        MaidReporter.red(GtCompat.bindingProblem()));
                return true;
            }
            GtCompat.damageTool(tool, maid);
            maid.swing(InteractionHand.MAIN_HAND);
            repaired++;
        }

        boolean taped = false;
        if (!missing.isEmpty() && MaidOdysseyConfig.useDuctTape()) {
            int tapeSlot = ItemsUtil.findStackSlot(inventory, GtCompat::isDuctTape);
            if (tapeSlot >= 0 && !inventory.extractItem(tapeSlot, 1, false).isEmpty()) {
                GtCompat.fixEverything(machine);
                repaired += missing.size();
                missing.clear();
                taped = true;
            }
        }

        if (repaired > 0) {
            GtCompat.setTaped(machine, taped);
            if (MaidOdysseyConfig.resetMaintenanceTimer()) {
                GtCompat.resetMaintenanceTimer(machine);
            }
            MaidReporter.success(maid, pos, taped
                    ? "message.maid_odyssey.maintenance.duct_taped"
                    : "message.maid_odyssey.maintenance.repaired", MaidReporter.gold(repaired));
        }
        if (!missing.isEmpty()) {
            MaidReporter.problem(maid, pos, "message.maid_odyssey.maintenance.missing_tools",
                    MaidReporter.aqua(listToolNames(missing)));
            return true;
        }
        return false;
    }

    /** Does the maid carry at least one tool for any of the six problems? Used for the GUI hint. */
    public static boolean hasAnyTool(EntityMaid maid) {
        IItemHandlerModifiable inventory = maid.getAvailableInv(true);
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (GtCompat.isDuctTape(stack)) {
                return true;
            }
            for (MaintenanceProblem problem : MaintenanceProblem.VALUES) {
                if (GtCompat.isToolFor(stack, problem)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Moves a matching tool into the maid's main hand so the repair is actually done "with the tool
     * she is holding", swapping whatever she held into the tool's old slot.
     *
     * @return the held tool, or an empty stack when she owns none.
     */
    private static ItemStack takeToolInHand(EntityMaid maid, CombinedInvWrapper inventory, MaintenanceProblem problem) {
        if (GtCompat.isToolFor(maid.getMainHandItem(), problem)) {
            return maid.getMainHandItem();
        }
        int slot = ItemsUtil.findStackSlot(inventory, stack -> GtCompat.isToolFor(stack, problem));
        if (slot < 0) {
            return ItemStack.EMPTY;
        }
        ItemStack tool = inventory.getStackInSlot(slot).copy();
        inventory.setStackInSlot(slot, maid.getMainHandItem().copy());
        maid.setItemInHand(InteractionHand.MAIN_HAND, tool);
        return maid.getMainHandItem();
    }

    private static MutableComponent listToolNames(List<MaintenanceProblem> problems) {
        MutableComponent joined = Component.empty();
        for (int i = 0; i < problems.size(); i++) {
            if (i > 0) {
                joined.append(Component.translatable("message.maid_odyssey.list_separator"));
            }
            joined.append(problems.get(i).displayName());
        }
        return joined;
    }
}
