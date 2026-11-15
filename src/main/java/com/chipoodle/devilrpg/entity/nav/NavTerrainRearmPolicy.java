package com.chipoodle.devilrpg.entity.nav;

/** Bounds one stair-budget rearm to a verified terrain change within the same mission. */
public final class NavTerrainRearmPolicy {
    private NavTerrainRearmPolicy() {
    }

    public static boolean shouldRearm(final boolean terrainChangePending, final boolean localStairLimitReached,
                                     final boolean alreadyRearmedThisMission) {
        return terrainChangePending && localStairLimitReached && !alreadyRearmedThisMission;
    }

    /** A verified terrain change may refresh the soft local stair budget independently of the terminal rearm. */
    public static boolean shouldResetLocalStairBudget(final boolean terrainChangePending,
                                                      final boolean localStairLimitReached,
                                                      final int localResetsUsed, final int maxLocalResets) {
        return terrainChangePending && localStairLimitReached && localResetsUsed < maxLocalResets;
    }
}
