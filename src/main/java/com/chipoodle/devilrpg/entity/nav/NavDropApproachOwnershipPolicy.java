package com.chipoodle.devilrpg.entity.nav;

/** Bounded ownership transfer for a non-adjacent DROP approach waypoint. */
public final class NavDropApproachOwnershipPolicy {
    public static final int MAX_TICKS = 80;
    public static final int NO_PROGRESS_TICKS = 20;
    public static final double MIN_PROGRESS = 0.01D;

    private NavDropApproachOwnershipPolicy() {
    }

    /**
     * Navigation keeps ownership while its path is active and making progress. A completed path
     * or a bounded no-progress interval transfers this DROP approach to MoveControl.
     */
    public static boolean shouldTakeOwnership(boolean dropTransition, boolean navigationDone,
            boolean physicallyReached, int noProgressTicks) {
        return dropTransition && !physicallyReached
                && (navigationDone || noProgress(noProgressTicks));
    }

    public static boolean timedOut(int ticks) {
        return ticks > MAX_TICKS;
    }

    public static boolean noProgress(int ticks) {
        return ticks > NO_PROGRESS_TICKS;
    }

    public static boolean madeProgress(double distance, double bestDistance) {
        return Double.isFinite(distance) && Double.isFinite(bestDistance)
                && distance < bestDistance - MIN_PROGRESS;
    }
}
