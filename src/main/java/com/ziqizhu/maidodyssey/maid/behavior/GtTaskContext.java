package com.ziqizhu.maidodyssey.maid.behavior;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Scratch state shared by the two behaviors of one maid task instance.
 * <p>
 * Machines she cannot service (missing tools, unreachable) are parked here for a while.
 * Leftover muffler ash is not parked: a standing maid must be able to eat the next stack.
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
