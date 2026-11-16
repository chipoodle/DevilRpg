package com.chipoodle.devilrpg.entity.nav;

/** Pure combat-intent decisions used by the single-authority navigation migration. */
public final class NavCombatIntentPolicy {
    public static final double MELEE_RANGE_SQR = 4.0D;
    public static final double TACTICAL_LEASH_SQR = 36.0D;

    private NavCombatIntentPolicy() {
    }

    public static boolean withinTacticalLeash(final double distanceSqr) {
        return distanceSqr <= TACTICAL_LEASH_SQR;
    }

    public static boolean holdInMelee(final double distanceSqr) {
        return distanceSqr <= MELEE_RANGE_SQR;
    }

    public static boolean tacticalDestinationChanged(final int previousX, final int previousY, final int previousZ,
                                                      final int currentX, final int currentY, final int currentZ) {
        return previousX != currentX || previousY != currentY || previousZ != currentZ;
    }
}
