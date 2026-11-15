package com.chipoodle.devilrpg.entity.nav;

/** Guards BREAK blocker selection against hitting the floor beneath an elevated action node. */
public final class NavBreakTargetPolicy {
    private NavBreakTargetPolicy() {
    }

    public static boolean isSupportBelowTarget(final int hitY, final int targetY, final boolean sameColumn) {
        return sameColumn && hitY < targetY;
    }
}
