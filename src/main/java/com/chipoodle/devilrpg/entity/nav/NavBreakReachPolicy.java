package com.chipoodle.devilrpg.entity.nav;

/** Rejects BREAK action nodes that cannot be reached from the current feet position. */
public final class NavBreakReachPolicy {
    public static final int MAX_STEP_UP = 2;

    private NavBreakReachPolicy() {
    }

    public static boolean reachable(final int feetY, final int nodeY,
                                    final double distanceSqr, final double actionReachSqr) {
        return nodeY - feetY <= MAX_STEP_UP && distanceSqr <= actionReachSqr;
    }
}
