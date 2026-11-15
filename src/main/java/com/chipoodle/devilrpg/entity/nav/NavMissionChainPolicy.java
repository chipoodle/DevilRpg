package com.chipoodle.devilrpg.entity.nav;

/** Keeps the active raid approach stage stable while the same village mission is in progress. */
public final class NavMissionChainPolicy {
    private NavMissionChainPolicy() {
    }

    public static boolean retainActiveApproachChain(boolean sameRoot, boolean hasCurrentObjective,
                                                      boolean hasRemainingStages, boolean incomingInteriorOnly) {
        return sameRoot && hasCurrentObjective && hasRemainingStages && !incomingInteriorOnly;
    }
}
