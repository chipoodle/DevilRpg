package com.chipoodle.devilrpg.entity.nav;

/** Bounded movement rule: melee range does not stop the assault; distant targets do not own MOVE. */
public final class AdvanceUnderFirePolicy {
    public static final double MELEE_RANGE_SQR = 4.0D;
    public static final double COMBAT_LEASH_SQR = 36.0D;

    private AdvanceUnderFirePolicy() {
    }

    public static boolean executorMayAdvance(final boolean customNavigation,
                                             final boolean hasCombatTarget,
                                             final boolean targetInMeleeRange) {
        return customNavigation && (!hasCombatTarget || targetInMeleeRange);
    }

    public static boolean shouldChase(final boolean bellObjectiveActive,
                                      final double targetDistanceSqr) {
        return !bellObjectiveActive || targetDistanceSqr <= COMBAT_LEASH_SQR;
    }

    /** A distant target must not suppress both combat pursuit and the strategic village march. */
    public static boolean shouldMarchToVillage(final boolean bellObjectiveActive,
                                               final boolean hasLiveTarget,
                                               final double targetDistanceSqr) {
        return !hasLiveTarget || !shouldChase(bellObjectiveActive, targetDistanceSqr);
    }
}
