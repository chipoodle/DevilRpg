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

    /**
     * DROP evaluates the horizontal take-off step separately from the vertical fall. The landing
     * remains guarded by fits/support; only the vertical component is excluded from the approach
     * distance so it cannot veto a valid one-cell horizontal move.
     */
    public static boolean shouldNudgeDrop(final boolean navigationDone, final int horizontalDistance,
                                          final int maxDistance, final boolean targetFits,
                                          final boolean hasSupport) {
        return navigationDone && horizontalDistance > 0 && maxDistance > 0 && maxDistance <= 3
                && horizontalDistance <= maxDistance && targetFits && hasSupport;
    }
}
