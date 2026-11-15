package com.chipoodle.devilrpg.entity.nav;

/** Preserves a temporary navigation objective while the same primary mission is re-submitted. */
public final class NavTacticalObjectivePolicy {
    private NavTacticalObjectivePolicy() {
    }

    public static boolean preserveTacticalObjective(final boolean tacticalActive, final boolean sameMission) {
        return tacticalActive && sameMission;
    }
}
