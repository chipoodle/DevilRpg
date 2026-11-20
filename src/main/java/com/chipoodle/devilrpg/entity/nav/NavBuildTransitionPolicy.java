package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

/** Pure representation and completion rules for a route-directed one-block BUILD step. */
public final class NavBuildTransitionPolicy {
    private NavBuildTransitionPolicy() { }

    /** Placement feasibility and the existing vertical envelope must both admit this BUILD edge. */
    public static boolean canBuildStep(boolean placementAllowed, int feetDelta, int maxSafeDrop) {
        return placementAllowed && allowsEdgeDelta(feetDelta, maxSafeDrop);
    }

    /** A BUILD edge may rise at most one block and may not exceed the existing safe-drop bound. */
    public static boolean allowsEdgeDelta(int feetDelta, int maxSafeDrop) {
        return feetDelta <= NavVerticalTransitionPolicy.MAX_ORDINARY_ASCENT
                && feetDelta >= -maxSafeDrop;
    }

    /** The placement cell becomes support; the graph successor is the feet cell directly above it. */
    public static BlockPos successorFeet(BlockPos placementCell) {
        return placementCell.above();
    }

    /** A BUILD node is complete only after the placed support is occupied physically and safely. */
    public static boolean physicallyArrived(boolean placementVerified, boolean exactFeetCell,
                                            boolean grounded, boolean supported, boolean fitsActualAabb) {
        return placementVerified && exactFeetCell && grounded && supported && fitsActualAabb;
    }

    /** Once this route's placement is verified, subsequent ticks must approach its feet cell, not place again. */
    public static boolean shouldPlace(boolean placementAlreadyVerified) {
        return !placementAlreadyVerified;
    }
}
