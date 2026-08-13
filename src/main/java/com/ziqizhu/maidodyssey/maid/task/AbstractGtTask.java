package com.ziqizhu.maidodyssey.maid.task;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import com.ziqizhu.maidodyssey.gt.GtCompat;
import com.ziqizhu.maidodyssey.gt.GtJob;
import com.ziqizhu.maidodyssey.maid.behavior.GtTaskContext;
import com.ziqizhu.maidodyssey.maid.behavior.MaidGtSearchTask;
import com.ziqizhu.maidodyssey.maid.behavior.MaidGtWorkTask;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/** Shared plumbing for the three GregTech chores. */
public abstract class AbstractGtTask implements IMaidTask {
    private static final float MOVEMENT_SPEED = 0.6F;

    @Nullable
    private Item cachedIcon;

    /** Which chores this task performs. */
    protected abstract Set<GtJob> jobs();

    /** GregTech item ids tried in order for the task button. */
    protected abstract String[] iconCandidates();

    /** Shown instead when GregTech is missing. */
    protected abstract Item fallbackIcon();

    @Override
    public ItemStack getIcon() {
        if (cachedIcon == null) {
            cachedIcon = GtCompat.itemOrFallback(fallbackIcon(), iconCandidates());
        }
        return cachedIcon.getDefaultInstance();
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return null;
    }

    @Override
    public List<String> getDescription(EntityMaid maid) {
        String base = "task.%s.%s.desc".formatted(getUid().getNamespace(), getUid().getPath());
        return Lists.newArrayList(base, base + "_2");
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        GtTaskContext context = new GtTaskContext();
        MaidGtSearchTask search = new MaidGtSearchTask(jobs(), context, MOVEMENT_SPEED);
        MaidGtWorkTask work = new MaidGtWorkTask(jobs(), context);
        return Lists.newArrayList(Pair.of(5, search), Pair.of(6, work));
    }

    @Override
    public boolean isEnable(EntityMaid maid) {
        return GtCompat.isLoaded();
    }

    @Override
    public boolean isHidden(EntityMaid maid) {
        return !GtCompat.isLoaded();
    }
}
