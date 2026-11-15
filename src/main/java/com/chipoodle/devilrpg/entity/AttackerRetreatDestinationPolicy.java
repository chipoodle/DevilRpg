package com.chipoodle.devilrpg.entity;

/** Pure acceptance rule for a retreat destination; world checks are supplied by the entity. */
public final class AttackerRetreatDestinationPolicy {
    private AttackerRetreatDestinationPolicy() {
    }

    public static boolean viable(final boolean feetClear, final boolean headClear,
                                 final boolean support, final boolean hazard,
                                 final boolean pathReachable, final double threatDistance,
                                 final int dy, final double minThreatDistance) {
        return feetClear && headClear && support && !hazard && pathReachable
                && threatDistance >= minThreatDistance
                && dy >= -3 && dy <= 2;
    }

    /** An attacker already inside the enclosure must not retreat to an outside cell. */
    public static boolean preservesInterior(final boolean startInside, final boolean candidateInside) {
        return !startInside || candidateInside;
    }
}
