package com.ziqizhu.maidodyssey.hazard;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.ziqizhu.maidodyssey.MaidOdysseyConfig;
import com.ziqizhu.maidodyssey.gt.GtCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-maid cache of blocks that currently hurt to stand in: muffler exhaust and GTO heat.
 * <p>
 * Scans nearby chunk block entities instead of walking every block. Heat is read from
 * {@code IHeatContainer} when gtolib is present; otherwise known heater block ids are treated
 * as hot because residual heat after shutdown is still lethal.
 */
public final class HazardTracker {
    private static final Map<UUID, HazardTracker> TRACKERS = new ConcurrentHashMap<>();
    private static final int MAX_TRACKERS = 256;

    private final Set<Long> danger = new HashSet<>();
    private long lastScanTick = Long.MIN_VALUE;
    @Nullable
    private BlockPos lastScanOrigin;

    private HazardTracker() {
    }

    public static HazardTracker of(EntityMaid maid) {
        if (TRACKERS.size() > MAX_TRACKERS) {
            TRACKERS.clear();
        }
        return TRACKERS.computeIfAbsent(maid.getUUID(), ignored -> new HazardTracker());
    }

    public void forceRescan() {
        lastScanTick = Long.MIN_VALUE;
    }

    public boolean isOccupied(BlockPos feet) {
        return danger.contains(feet.asLong()) || danger.contains(feet.above().asLong());
    }

    /**
     * Work-search helper: the global cache plus this machine's own muffler plume, so a roof
     * hatch just outside the last scan still cannot be used as a standing spot.
     */
    public static boolean isUnsafeStanding(ServerLevel level, EntityMaid maid, BlockPos standing, BlockPos machinePos) {
        if (!MaidOdysseyConfig.avoidHazards()) {
            return false;
        }
        HazardTracker tracker = of(maid);
        tracker.refresh(level, maid);
        if (tracker.isOccupied(standing)) {
            return true;
        }
        Object machine = GtCompat.getMachine(level, machinePos);
        return machine != null && GtCompat.isMuffler(machine)
                && touchesMufflerHazard(standing, machinePos, GtCompat.mufflerExhaustFacing(machine));
    }

    public void refresh(ServerLevel level, EntityMaid maid) {
        if (!MaidOdysseyConfig.avoidHazards() || !GtCompat.isLoaded()) {
            danger.clear();
            return;
        }
        long time = level.getGameTime();
        BlockPos origin = maid.blockPosition();
        int interval = MaidOdysseyConfig.hazardScanInterval();
        if (time - lastScanTick < interval
                && lastScanOrigin != null
                && lastScanOrigin.distManhattan(origin) < 4) {
            return;
        }
        lastScanTick = time;
        lastScanOrigin = origin.immutable();
        rescan(level, origin);
    }

    private void rescan(ServerLevel level, BlockPos origin) {
        danger.clear();
        int radius = MaidOdysseyConfig.hazardScanRadius();
        int minCx = (origin.getX() - radius) >> 4;
        int maxCx = (origin.getX() + radius) >> 4;
        int minCz = (origin.getZ() - radius) >> 4;
        int maxCz = (origin.getZ() + radius) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    BlockPos pos = blockEntity.getBlockPos();
                    if (chebyshev(origin, pos) > radius) {
                        continue;
                    }
                    inspect(level, blockEntity, pos);
                }
            }
        }
    }

    private void inspect(ServerLevel level, BlockEntity blockEntity, BlockPos pos) {
        BlockState state = blockEntity.getBlockState();
        if (!GtCompat.isMaybeHazardBlock(state)) {
            return;
        }
        Object machine = GtCompat.getMachine(level, pos);
        if (machine != null && GtCompat.isMuffler(machine)) {
            markMuffler(pos, GtCompat.mufflerExhaustFacing(machine));
        }
        if (isHot(machine, blockEntity, state)) {
            markRadius(pos, MaidOdysseyConfig.heatAvoidRadius());
        }
    }

    private static boolean isHot(@Nullable Object machine, BlockEntity blockEntity, BlockState state) {
        Object container = GtCompat.findHeatContainer(machine, blockEntity);
        double temperature = GtCompat.getHeatTemperature(container);
        if (!Double.isNaN(temperature)) {
            return temperature >= MaidOdysseyConfig.heatDangerKelvin();
        }
        return GtCompat.isKnownHeatMachineBlock(state) || (machine != null && GtCompat.isHeaterLike(machine));
    }

    private void markMuffler(BlockPos muffler, Direction facing) {
        int radius = MaidOdysseyConfig.mufflerAvoidRadius();
        markRadius(muffler, radius);
        int length = MaidOdysseyConfig.mufflerExhaustLength();
        for (int i = 1; i <= length; i++) {
            markRadius(muffler.relative(facing, i), radius);
        }
    }

    private void markRadius(BlockPos center, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    danger.add(BlockPos.asLong(center.getX() + dx, center.getY() + dy, center.getZ() + dz));
                }
            }
        }
    }

    public static boolean touchesMufflerHazard(BlockPos standing, BlockPos muffler, Direction facing) {
        int radius = MaidOdysseyConfig.mufflerAvoidRadius();
        if (withinChebyshev(standing, muffler, radius) || withinChebyshev(standing.above(), muffler, radius)) {
            return true;
        }
        int length = MaidOdysseyConfig.mufflerExhaustLength();
        for (int i = 1; i <= length; i++) {
            BlockPos cell = muffler.relative(facing, i);
            if (withinChebyshev(standing, cell, radius) || withinChebyshev(standing.above(), cell, radius)) {
                return true;
            }
        }
        return false;
    }

    private static boolean withinChebyshev(BlockPos a, BlockPos b, int radius) {
        return chebyshev(a, b) <= radius;
    }

    static int chebyshev(BlockPos a, BlockPos b) {
        return Math.max(Math.abs(a.getX() - b.getX()),
                Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
    }

    public static boolean isHeatWave(DamageSource source) {
        return source.typeHolder().unwrapKey()
                .map(key -> "machine_heat_wave".equals(key.location().getPath()))
                .orElseGet(() -> {
                    String messageId = source.getMsgId();
                    return messageId != null && messageId.contains("machine_heat_wave");
                });
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        if (!isHeatWave(event.getSource())) {
            return;
        }
        of(maid).forceRescan();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TRACKERS.clear();
    }
}
