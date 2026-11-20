package com.choodle.devilrpg.entity.nav;

/** Ordinary navigation never ascends more than one block in a single movement edge. */
public final class NavVerticalTransitionPolicy {
    public static final int MAX_ORDINARY_ASCENT = 1;

    private NavVerticalTransitionPolicy() { }

    /** Returns null when the vertical delta is not an ordinary movement transition. */
    public static NavRoute.Transition ordinaryTransition(int delta) {
        if (delta == 0) return NavRoute.Transition.WALK;
        if (delta == 1) return NavRoute.Transition.STEP_UP;
        return null;
    }

    public static boolean allowsOrdinaryDelta(int delta) {
        return delta >= 0 && delta <= MAX_ORDINARY_ASCENT;
    }
}
