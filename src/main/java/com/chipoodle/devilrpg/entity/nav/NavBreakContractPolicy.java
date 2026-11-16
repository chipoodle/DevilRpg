package com.chipoodle.devilrpg.entity.nav;

/** Pure BREAK contract constants, usable without Minecraft classes. */
public final class NavBreakContractPolicy {
    public static final double ACTION_REACH_SQR = 6.25D;
    public static final int CANDIDATE_OFFSETS = 8;

    private NavBreakContractPolicy() {
    }

    public static boolean withinReach(final int dx, final int dy, final int dz) {
        return dx * dx + dy * dy + dz * dz <= ACTION_REACH_SQR;
    }
}
