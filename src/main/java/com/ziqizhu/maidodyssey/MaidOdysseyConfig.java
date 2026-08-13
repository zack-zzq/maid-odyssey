package com.ziqizhu.maidodyssey;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class MaidOdysseyConfig {
    public static final ForgeConfigSpec SPEC;
    private static final MaidOdysseyConfig INSTANCE;

    public enum AshHandling {
        /** Move the ash into the maid's backpack. */
        COLLECT,
        /** Destroy the ash on the spot, the maid never carries it. */
        VOID
    }

    public final ForgeConfigSpec.BooleanValue chatReportEnabled;
    public final ForgeConfigSpec.BooleanValue chatReportToEveryone;
    public final ForgeConfigSpec.BooleanValue chatReportSuccess;
    public final ForgeConfigSpec.IntValue chatReportCooldown;

    public final ForgeConfigSpec.IntValue maxSearchRadius;
    public final ForgeConfigSpec.IntValue verticalSearchRange;
    public final ForgeConfigSpec.IntValue workReach;
    public final ForgeConfigSpec.IntValue blockedRetryDelay;
    public final ForgeConfigSpec.IntValue unreachableRetryDelay;

    public final ForgeConfigSpec.EnumValue<AshHandling> ashHandling;

    public final ForgeConfigSpec.BooleanValue useDuctTape;
    public final ForgeConfigSpec.BooleanValue resetMaintenanceTimer;
    public final ForgeConfigSpec.IntValue toolDurabilityReserve;

    public final ForgeConfigSpec.BooleanValue avoidHazards;
    public final ForgeConfigSpec.IntValue mufflerAvoidRadius;
    public final ForgeConfigSpec.IntValue mufflerExhaustLength;
    public final ForgeConfigSpec.IntValue heatAvoidRadius;
    public final ForgeConfigSpec.IntValue heatDangerKelvin;
    public final ForgeConfigSpec.IntValue hazardScanRadius;
    public final ForgeConfigSpec.IntValue hazardScanInterval;

    static {
        Pair<MaidOdysseyConfig, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(MaidOdysseyConfig::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    private MaidOdysseyConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("Chat feedback the maid gives while working on GregTech machines.").push("report");
        chatReportEnabled = builder
                .comment("Master switch for the maid's chat messages.")
                .define("chatReportEnabled", true);
        chatReportToEveryone = builder
                .comment("true: broadcast to every player on the server.",
                        "false: only tell the maid's owner.")
                .define("chatReportToEveryone", true);
        chatReportSuccess = builder
                .comment("Also announce successful work, not just problems.")
                .define("chatReportSuccess", false);
        chatReportCooldown = builder
                .comment("Minimum seconds between two identical messages from the same maid about the same block.")
                .defineInRange("chatReportCooldown", 60, 1, 3600);
        builder.pop();

        builder.comment("How the maid looks for work.").push("search");
        maxSearchRadius = builder
                .comment("Hard cap on the horizontal search radius, in blocks.",
                        "The effective radius is min(this value, the maid's own work range).",
                        "Lower it if you see lag with very large work ranges.")
                .defineInRange("maxSearchRadius", 24, 1, 64);
        verticalSearchRange = builder
                .comment("How many blocks above and below her home point the maid looks.",
                        "Muffler hatches sit on the roof of a multiblock, three blocks above the",
                        "controller on an Electric Blast Furnace, so this needs headroom.")
                .defineInRange("verticalSearchRange", 8, 1, 16);
        workReach = builder
                .comment("How far the maid can service a machine from, in blocks.",
                        "Machine blocks are solid, and a roof mounted muffler hatch usually has no",
                        "standable spot next to it at all, so she works from the ground below.",
                        "Raise this for very large multiblocks; lower it if you dislike her",
                        "reaching through a wall.")
                .defineInRange("workReach", 5, 1, 16);
        blockedRetryDelay = builder
                .comment("When a machine cannot be serviced (backpack full, tool missing, ...),",
                        "the maid ignores that block for this many ticks before trying again.")
                .defineInRange("blockedRetryDelay", 600, 20, 24000);
        unreachableRetryDelay = builder
                .comment("When the maid gives up walking to a machine, she ignores it for this",
                        "many ticks. Stops her ping-ponging towards something she cannot reach.")
                .defineInRange("unreachableRetryDelay", 200, 20, 24000);
        builder.pop();

        builder.comment("Muffler hatch ash cleaning.").push("muffler");
        ashHandling = builder
                .comment("COLLECT: put the ash into the maid's backpack (she stops when it is full).",
                        "VOID: destroy the ash immediately, the maid never runs out of space.")
                .defineEnum("ashHandling", AshHandling.COLLECT);
        builder.pop();

        builder.comment("Maintenance hatch repair.").push("maintenance");
        useDuctTape = builder
                .comment("Let the maid fall back to GregTech duct tape when a required tool is missing.")
                .define("useDuctTape", true);
        resetMaintenanceTimer = builder
                .comment("Reset the hatch wear timer after a manual repair, mirroring what GTOCore does",
                        "when a player repairs the hatch by hand.")
                .define("resetMaintenanceTimer", true);
        toolDurabilityReserve = builder
                .comment("Keep this much durability in reserve: the maid refuses to use a tool whose",
                        "remaining durability is at or below this number, so your good tools survive.")
                .defineInRange("toolDurabilityReserve", 0, 0, 1000);
        builder.pop();

        builder.comment("Keep the maid out of muffler exhaust and GTO heat.").push("hazard");
        avoidHazards = builder
                .comment("When walking or wandering, skip muffler exhaust and hot machines.")
                .define("avoidHazards", true);
        mufflerAvoidRadius = builder
                .comment("Chebyshev radius around a muffler hatch the maid will not stand in,",
                        "including the block directly above it.")
                .defineInRange("mufflerAvoidRadius", 1, 0, 8);
        mufflerExhaustLength = builder
                .comment("How many blocks along the muffler's front face count as exhaust.",
                        "GTO also checks three air blocks in front of the hatch.")
                .defineInRange("mufflerExhaustLength", 2, 0, 8);
        heatAvoidRadius = builder
                .comment("Chebyshev radius around a hot heat container (heater, heated boiler,",
                        "cauldron, heat hatch, ...).")
                .defineInRange("heatAvoidRadius", 2, 1, 8);
        heatDangerKelvin = builder
                .comment("Treat an IHeatContainer as dangerous at or above this temperature (Kelvin).",
                        "Ambient is about 293 K; cauldron recipes start around 340 K.")
                .defineInRange("heatDangerKelvin", 340, 300, 4000);
        hazardScanRadius = builder
                .comment("How far around the maid to look for hazards, in blocks.")
                .defineInRange("hazardScanRadius", 16, 4, 48);
        hazardScanInterval = builder
                .comment("How often the maid rescans nearby machines for hazards, in ticks.")
                .defineInRange("hazardScanInterval", 10, 1, 200);
        builder.pop();
    }

    public static boolean chatReportEnabled() {
        return INSTANCE.chatReportEnabled.get();
    }

    public static boolean chatReportToEveryone() {
        return INSTANCE.chatReportToEveryone.get();
    }

    public static boolean chatReportSuccess() {
        return INSTANCE.chatReportSuccess.get();
    }

    public static int chatReportCooldownTicks() {
        return INSTANCE.chatReportCooldown.get() * 20;
    }

    public static int maxSearchRadius() {
        return INSTANCE.maxSearchRadius.get();
    }

    public static int verticalSearchRange() {
        return INSTANCE.verticalSearchRange.get();
    }

    public static int workReach() {
        return INSTANCE.workReach.get();
    }

    public static int blockedRetryDelay() {
        return INSTANCE.blockedRetryDelay.get();
    }

    public static int unreachableRetryDelay() {
        return INSTANCE.unreachableRetryDelay.get();
    }

    public static AshHandling ashHandling() {
        return INSTANCE.ashHandling.get();
    }

    public static boolean useDuctTape() {
        return INSTANCE.useDuctTape.get();
    }

    public static boolean resetMaintenanceTimer() {
        return INSTANCE.resetMaintenanceTimer.get();
    }

    public static int toolDurabilityReserve() {
        return INSTANCE.toolDurabilityReserve.get();
    }

    public static boolean avoidHazards() {
        return INSTANCE.avoidHazards.get();
    }

    public static int mufflerAvoidRadius() {
        return INSTANCE.mufflerAvoidRadius.get();
    }

    public static int mufflerExhaustLength() {
        return INSTANCE.mufflerExhaustLength.get();
    }

    public static int heatAvoidRadius() {
        return INSTANCE.heatAvoidRadius.get();
    }

    public static int heatDangerKelvin() {
        return INSTANCE.heatDangerKelvin.get();
    }

    public static int hazardScanRadius() {
        return INSTANCE.hazardScanRadius.get();
    }

    public static int hazardScanInterval() {
        return INSTANCE.hazardScanInterval.get();
    }
}
