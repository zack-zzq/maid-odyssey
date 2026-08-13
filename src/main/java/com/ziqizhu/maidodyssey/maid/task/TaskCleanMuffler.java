package com.ziqizhu.maidodyssey.maid.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.ziqizhu.maidodyssey.MaidOdyssey;
import com.ziqizhu.maidodyssey.gt.GtJob;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Set;

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
    public String getMaidActionSummary() {
        return "Collect the ash that GregTech muffler hatches produce while the machines run";
    }
}
