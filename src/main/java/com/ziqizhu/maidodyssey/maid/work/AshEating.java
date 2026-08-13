package com.ziqizhu.maidodyssey.maid.work;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.ziqizhu.maidodyssey.report.MaidReporter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.IItemHandler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Backpack-full fallback: destroy up to one stack of muffler ash and play a fake eat.
 * <p>
 * GregTech ash is not food, so this must not call {@code startUsingItem} / {@code eat}. Those
 * paths either do nothing or shove the leftover stack into a full backpack and drop it on the
 * ground. The maid only gets crumbs, chew sounds, and a short stand-still.
 */
public final class AshEating {
    private static final int ANIMATION_TICKS = 32;
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private AshEating() {
    }

    public static boolean isBusy(EntityMaid maid) {
        Session session = SESSIONS.get(maid.getUUID());
        if (session == null) {
            return false;
        }
        if (maid.level().getGameTime() - session.startedAt > ANIMATION_TICKS + 20) {
            SESSIONS.remove(maid.getUUID());
            return false;
        }
        return true;
    }

    /**
     * Pulls at most one stack out of the hatch and eats it.
     *
     * @return how many ash items were eaten, or 0 when she could not start
     */
    public static int start(EntityMaid maid, IItemHandler hatch, BlockPos pos) {
        if (isBusy(maid) || maid.level().isClientSide()) {
            return 0;
        }
        ItemStack ash = takeOneStack(hatch);
        if (ash.isEmpty()) {
            return 0;
        }
        begin(maid, ash, pos);
        return ash.getCount();
    }

    /**
     * Eat a stack that is already in the maid's hands (for example a backpack reject that
     * no longer fits in the hatch). The stack is destroyed; it is never dropped.
     */
    public static void consume(EntityMaid maid, ItemStack ash, BlockPos pos) {
        if (ash.isEmpty() || maid.level().isClientSide()) {
            return;
        }
        if (isBusy(maid)) {
            ash.setCount(0);
            return;
        }
        begin(maid, ash.copy(), pos);
        ash.setCount(0);
    }

    private static void begin(EntityMaid maid, ItemStack ash, BlockPos pos) {
        int count = ash.getCount();
        ItemStack crumb = ash.copy();
        crumb.setCount(1);
        SESSIONS.put(maid.getUUID(), new Session(crumb, ANIMATION_TICKS, maid.level().getGameTime()));
        maid.getNavigation().stop();
        maid.swing(InteractionHand.MAIN_HAND);
        maid.spawnItemParticles(crumb, 8);
        maid.playSound(SoundEvents.GENERIC_EAT, 0.8F, 1.0F);
        maid.getFavorabilityManager().reduce(1);
        MaidReporter.problem(maid, pos,
                "message.maid_odyssey.muffler.ate_ash",
                MaidReporter.gold(count),
                MaidReporter.red("-1"));
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || maid.level().isClientSide()) {
            return;
        }
        Session session = SESSIONS.get(maid.getUUID());
        if (session == null) {
            return;
        }
        session.ticksLeft--;
        if (session.ticksLeft > 0 && session.ticksLeft % 4 == 0) {
            maid.spawnItemParticles(session.crumb, 5);
            maid.playSound(SoundEvents.GENERIC_EAT, 0.5F + 0.5F * maid.getRandom().nextFloat(),
                    (maid.getRandom().nextFloat() - maid.getRandom().nextFloat()) * 0.2F + 1.0F);
        }
        if (session.ticksLeft <= 0) {
            SESSIONS.remove(maid.getUUID());
            maid.playSound(SoundEvents.PLAYER_BURP, 0.5F, maid.getRandom().nextFloat() * 0.1F + 0.9F);
            if (maid.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                        maid.getX(), maid.getEyeY(), maid.getZ(), 4, 0.25, 0.2, 0.25, 0);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SESSIONS.clear();
    }

    private static ItemStack takeOneStack(IItemHandler hatch) {
        for (int slot = 0; slot < hatch.getSlots(); slot++) {
            int stored = hatch.getStackInSlot(slot).getCount();
            if (stored <= 0) {
                continue;
            }
            int take = Math.min(stored, hatch.getStackInSlot(slot).getMaxStackSize());
            ItemStack extracted = hatch.extractItem(slot, take, false);
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    private static final class Session {
        private final ItemStack crumb;
        private final long startedAt;
        private int ticksLeft;

        private Session(ItemStack crumb, int ticksLeft, long startedAt) {
            this.crumb = crumb;
            this.ticksLeft = ticksLeft;
            this.startedAt = startedAt;
        }
    }
}
