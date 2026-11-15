package com.chipoodle.devilrpg.entity;

/** Keeps the stair candidate origin anchored to the attacker's current support. */
public final class AttackerStairAnchorPolicy {
    public static final double MAX_ANCHOR_DISTANCE_SQR = 4.0D;

    private AttackerStairAnchorPolicy() {
    }

    public static boolean anchorIsCurrent(final double anchorX, final double anchorY, final double anchorZ,
                                          final double mobX, final double mobY, final double mobZ) {
        double dx = anchorX - mobX;
        double dy = anchorY - mobY;
        double dz = anchorZ - mobZ;
        return dx * dx + dy * dy + dz * dz <= MAX_ANCHOR_DISTANCE_SQR;
    }
}
