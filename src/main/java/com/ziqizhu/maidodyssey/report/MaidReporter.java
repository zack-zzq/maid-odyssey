package com.ziqizhu.maidodyssey.report;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.ziqizhu.maidodyssey.MaidOdysseyConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends the maid's work log to chat.
 * <p>
 * Identical messages about the same block are throttled, otherwise a maid standing in front of a
 * machine she cannot service would flood the chat every few seconds.
 */
public final class MaidReporter {
    private static final int MAX_TRACKED_MESSAGES = 512;
    private static final Map<String, Long> LAST_SENT = new ConcurrentHashMap<>();

    private MaidReporter() {
    }

    /** Something is stopping the maid from finishing her job. */
    public static void problem(EntityMaid maid, @Nullable BlockPos pos, String key, Object... args) {
        send(maid, pos, Component.translatable(key, args));
    }

    /** The maid finished a job; only shown when the player opted in. */
    public static void success(EntityMaid maid, @Nullable BlockPos pos, String key, Object... args) {
        if (!MaidOdysseyConfig.chatReportSuccess()) {
            return;
        }
        send(maid, pos, Component.translatable(key, args));
    }

    public static MutableComponent gold(Object value) {
        return Component.literal(String.valueOf(value)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    public static MutableComponent red(Object value) {
        return Component.literal(String.valueOf(value)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
    }

    public static MutableComponent red(Component value) {
        return value.copy().withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
    }

    public static MutableComponent aqua(Component value) {
        return value.copy().withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
    }

    private static void send(EntityMaid maid, @Nullable BlockPos pos, Component body) {
        if (!MaidOdysseyConfig.chatReportEnabled()) {
            return;
        }
        Level level = maid.level();
        if (level.isClientSide()) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        String throttleKey = body.getContents() instanceof TranslatableContents translatable
                ? translatable.getKey()
                : body.getString();
        if (!passesThrottle(maid, pos, throttleKey, level.getGameTime())) {
            return;
        }

        MutableComponent message = Component.literal("[Maid Odyssey] ").withStyle(ChatFormatting.DARK_AQUA)
                .append(maid.getDisplayName().copy().withStyle(ChatFormatting.LIGHT_PURPLE));
        if (pos != null) {
            message.append(Component.literal(" [%d, %d, %d]".formatted(pos.getX(), pos.getY(), pos.getZ()))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        message.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(body);

        if (MaidOdysseyConfig.chatReportToEveryone()) {
            server.getPlayerList().broadcastSystemMessage(message, false);
        } else if (maid.getOwner() instanceof ServerPlayer owner) {
            owner.sendSystemMessage(message);
        }
    }

    private static boolean passesThrottle(LivingEntity maid, @Nullable BlockPos pos, String key, long gameTime) {
        String throttleKey = maid.getUUID() + "|" + key + "|" + (pos == null ? "-" : pos.asLong());
        long cooldown = MaidOdysseyConfig.chatReportCooldownTicks();
        Long previous = LAST_SENT.get(throttleKey);
        if (previous != null && gameTime - previous < cooldown && gameTime >= previous) {
            return false;
        }
        if (LAST_SENT.size() > MAX_TRACKED_MESSAGES) {
            LAST_SENT.entrySet().removeIf(entry -> gameTime - entry.getValue() >= cooldown);
        }
        LAST_SENT.put(throttleKey, gameTime);
        return true;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_SENT.clear();
    }
}
