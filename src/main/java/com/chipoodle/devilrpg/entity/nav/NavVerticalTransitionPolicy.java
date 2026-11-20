package com.chipoodle.devilrpg.entity.nav;

/** Ordinary movement is limited to level travel or one-block ascent. */
public final class NavVerticalTransitionPolicy {
    public static final int MAX_ORDINARY_ASCENT = 1;

    private NavVerticalTransitionPolicy() { }

    /** Returns null for a vertical rise that must be rejected or represented by BUILD. */
    public static NavRoute.Transition ordinaryTransition(int delta) {
        if (delta == 0) return NavRoute.Transition.WALK;
        if (delta == 1) return NavRoute.Transition.STEP_UP;
        return null;
    }

    public static boolean allowsOrdinaryDelta(int delta) {
        return delta >= 0 && delta <= MAX_ORDINARY_ASCENT;
    }
}
