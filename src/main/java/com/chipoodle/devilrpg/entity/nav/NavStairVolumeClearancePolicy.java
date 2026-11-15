package com.chipoodle.devilrpg.entity.nav;

/** Selects only the first occupied cell in a stair's three-block body volume, and only if it is breakable. */
public final class NavStairVolumeClearancePolicy {
    private NavStairVolumeClearancePolicy() {
    }

    public static int firstBreakableBlockIndex(final boolean[] occupied, final boolean[] breakable,
                                              final boolean candidateAllowed, final boolean hasSupport) {
        if (!candidateAllowed || !hasSupport || occupied == null || breakable == null
                || occupied.length < 3 || breakable.length < 3) {
            return -1;
        }
        for (int i = 0; i < 3; i++) {
            if (!occupied[i]) {
                continue;
            }
            return breakable[i] ? i : -1;
        }
        return -1;
    }
}
