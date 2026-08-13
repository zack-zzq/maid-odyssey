package com.zackzzq.maidodyssey.maid.behavior;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Scratch state shared by the two behaviors of one maid task instance.
 * <p>
 * Machines the maid could not service are parked here for a while, otherwise she would walk back
 * to the same full muffler hatch every five seconds forever.
 */
public final class GtTaskContext {
    private final Map<BlockPos, Long> ignoredUntil = new HashMap<>();

    public void ignore(BlockPos pos, long gameTime, int ticks) {
        ignoredUntil.put(pos.immutable(), gameTime + ticks);
    }

    public boolean isIgnored(BlockPos pos, long gameTime) {
        Long until = ignoredUntil.get(pos);
        return until != null && until > gameTime;
    }

    public void prune(long gameTime) {
        if (!ignoredUntil.isEmpty()) {
            ignoredUntil.values().removeIf(until -> until <= gameTime);
        }
    }
}
