package com.chipoodle.devilrpg.entity;

/** Bounded multi-threat disengagement rule for siege attackers. */
public final class AttackerThreatPolicy {
    private AttackerThreatPolicy() {
    }

    public static boolean shouldDisengageFromGroup(final float health, final float maxHealth,
                                                   final int nearbyThreats, final double nearestDistance) {
        return health > 0.0F && maxHealth > 0.0F
                && nearbyThreats >= 2
                && health < maxHealth * 0.95F
                && nearestDistance <= 8.0D;
    }
}
