package com.chipoodle.devilrpg.entity.nav;

/** Guard for a bounded MoveControl nudge into an adjacent, already validated movement cell. */
public final class NavAdjacentApproachPolicy {
    private NavAdjacentApproachPolicy() {
    }

    public static boolean shouldNudge(final boolean navigationDone, final boolean movementNode,
                                     final int manhattanDistance, final int maxDistance, final boolean targetFits,
                                     final boolean hasSupport) {
        return navigationDone && movementNode && manhattanDistance > 0 && maxDistance > 0 && maxDistance <= 3 && manhattanDistance <= maxDistance
                && targetFits && hasSupport;
    }
}
