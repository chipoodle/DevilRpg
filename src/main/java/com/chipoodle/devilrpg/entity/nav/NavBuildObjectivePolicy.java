package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

/** Fail-closed provenance contract for a global BUILD waypoint handed to the local planner. */
public final class NavBuildObjectivePolicy {
    private NavBuildObjectivePolicy() { }

    /**
     * A global BUILD waypoint is authorized only when its exact placement cell is retained and
     * the graph successor is the feet cell directly above that placement.
     */
    public static boolean isValid(BlockPos feetCell, BlockPos placementCell) {
        return feetCell != null && placementCell != null
                && NavBuildTransitionPolicy.successorFeet(placementCell).equals(feetCell);
    }
}
