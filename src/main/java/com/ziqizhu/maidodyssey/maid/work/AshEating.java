package com.ziqizhu.maidodyssey.maid.work;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.ziqizhu.maidodyssey.report.MaidReporter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * When the backpack cannot take more ash, the maid eats up to one stack instead of asking the
 * owner to empty her bag. Looks and sounds like a normal meal: {@code startUsingItem} so Gecko
 * models play the use animation, item crumbs and eat sounds on the way, no nutrition or healing.
 */
public final class AshEating {
    private static final int EAT_DURATION_TICKS = 32;
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private AshEating() {
    }

    /**
     * Pulls at most one stack out of the hatch and starts the eat animation.
     *
     * @return how many ash items are being eaten, or 0 when she could not start
     */
    public static int start(EntityMaid maid, IItemHandler hatch, BlockPos pos) {
        if (maid.isUsingItem() || maid.level().isClientSide()) {
            return 0;
        }
        ItemStack ash = takeOneStack(hatch);
        if (ash.isEmpty()) {
            return 0;
        }

        InteractionHand hand = InteractionHand.OFF_HAND;
        ItemStack previous = maid.getItemInHand(hand).copy();
        maid.setItemInHand(hand, ash);
        if (!previous.isEmpty()) {
            maid.memoryHandItemStack(previous);
        }

        SESSIONS.put(maid.getUUID(), new Session(pos.immutable(), ash.getCount()));
        maid.startUsingItem(hand);
        if (!maid.isUsingItem()) {
            abort(maid, hatch, ash, previous);
            return 0;
        }
        return ash.getCount();
    }

    @SubscribeEvent
    public static void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (SESSIONS.containsKey(event.getEntity().getUUID())) {
            event.setDuration(EAT_DURATION_TICKS);
        }
    }

    @SubscribeEvent
    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || !SESSIONS.containsKey(maid.getUUID())) {
            return;
        }
        if (event.getDuration() % 4 != 0) {
            return;
        }
        maid.spawnItemParticles(event.getItem(), 5);
        float pitch = (maid.getRandom().nextFloat() - maid.getRandom().nextFloat()) * 0.2F + 1.0F;
        maid.playSound(SoundEvents.GENERIC_EAT, 0.5F + 0.5F * maid.getRandom().nextFloat(), pitch);
    }

    @SubscribeEvent
    public static void onUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        Session session = SESSIONS.remove(maid.getUUID());
        if (session == null) {
            return;
        }
        event.setResultStack(ItemStack.EMPTY);
        finish(maid, session);
    }

    @SubscribeEvent
    public static void onUseStop(LivingEntityUseItemEvent.Stop event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        Session session = SESSIONS.remove(maid.getUUID());
        if (session == null) {
            return;
        }
        maid.setItemInHand(maid.getUsedItemHand(), ItemStack.EMPTY);
        finish(maid, session);
    }

    private static void finish(EntityMaid maid, Session session) {
        maid.getFavorabilityManager().reduce(1);
        maid.playSound(SoundEvents.PLAYER_BURP, 0.5F, maid.getRandom().nextFloat() * 0.1F + 0.9F);
        if (maid.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                    maid.getX(), maid.getEyeY(), maid.getZ(), 4, 0.25, 0.2, 0.25, 0);
        }
        MaidReporter.problem(maid, session.pos,
                "message.maid_odyssey.muffler.ate_ash",
                MaidReporter.gold(session.count),
                MaidReporter.red("-1"));
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

    private static void abort(EntityMaid maid, IItemHandler hatch, ItemStack ash, ItemStack previous) {
        SESSIONS.remove(maid.getUUID());
        maid.setItemInHand(InteractionHand.OFF_HAND, previous);
        ItemStack leftover = ItemHandlerHelper.insertItemStacked(hatch, ash, false);
        if (!leftover.isEmpty() && maid.level() instanceof ServerLevel) {
            maid.spawnAtLocation(leftover);
        }
    }

    private record Session(BlockPos pos, int count) {
    }
}
