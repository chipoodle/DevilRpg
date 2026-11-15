package com.chipoodle.devilrpg.entity.nav;

/** Pure lifecycle rule for the siege bell objective. */
public final class NavBellObjectivePolicy {
    private NavBellObjectivePolicy() {
    }

    public static boolean objectiveValid(final boolean blockIsBell, final boolean destroyed) {
        return blockIsBell && !destroyed;
    }

    public static boolean canDestroy(final boolean alive, final boolean insideEnclosure,
                                     final double distanceSqr, final double reachSqr) {
        return alive && insideEnclosure && distanceSqr <= reachSqr;
    }
}
