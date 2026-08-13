package com.ziqizhu.maidodyssey.gt;

import java.util.Set;

/** A kind of chore the maid can perform on a GregTech machine. */
public enum GtJob {
    /** Empty the ash out of a muffler hatch. */
    MUFFLER,
    /** Repair the problems of a maintenance hatch. */
    MAINTENANCE;

    public static final Set<GtJob> ALL = Set.of(MUFFLER, MAINTENANCE);
    public static final Set<GtJob> ONLY_MUFFLER = Set.of(MUFFLER);
    public static final Set<GtJob> ONLY_MAINTENANCE = Set.of(MAINTENANCE);
}
