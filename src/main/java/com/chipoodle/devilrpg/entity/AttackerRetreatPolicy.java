package com.chipoodle.devilrpg.entity;

/** Bounded tactical step-back. Never a strategic retreat: the siege objective always resumes. */
public final class AttackerRetreatPolicy {
    public static final int MAX_RETREATS = 2;
    public static final int RETREAT_TICKS = 40;
    public static final int COOLDOWN_TICKS = 160;
    public static final float HEALTH_FRACTION = 0.85F;
    public static final double THREAT_RANGE = 8.0D;
    public static final double SAFE_DISTANCE = 3.0D;
    public static final double MAX_DISTANCE = 7.0D;

    private AttackerRetreatPolicy() {
    }

    public static boolean shouldRetreat(final float health, final float maxHealth,
                                        final double nearestThreatDistance, final int retreatsUsed,
                                        final long now, final long lastRetreatTick) {
        if (health <= 0.0F || maxHealth <= 0.0F || retreatsUsed >= MAX_RETREATS) {
            return false;
        }
        if (health > maxHealth * HEALTH_FRACTION) {
            return false;
        }
        if (nearestThreatDistance > THREAT_RANGE) {
            return false;
        }
        return now - lastRetreatTick >= COOLDOWN_TICKS;
    }

    /** Only the finite timed maneuver is evasive; there is no indefinite critical-evasion state. */
    public static boolean shouldRemainEvasive(final float health, final float maxHealth,
                                              final double nearestThreatDistance) {
        return false;
    }

    public static boolean isValidActivation(final float health, final boolean alive) {
        return alive && health > 0.0F;
    }
}
