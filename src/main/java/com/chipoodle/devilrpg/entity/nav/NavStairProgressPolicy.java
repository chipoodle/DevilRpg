package com.chipoodle.devilrpg.entity.nav;

/** Bounded lifecycle rule for a verified descent stair that the agent has already passed. */
public final class NavStairProgressPolicy {
    private NavStairProgressPolicy() {
    }

    /**
     * A pending stair is obsolete only when the mission is below its landing and the mob is already
     * farther below that landing than a safe fall. This is abandonment, never physical traversal.
     */
    public static boolean shouldAbandonPendingStep(final int mobY, final int landingY,
                                                   final int missionY, final int maxSafeDrop) {
        return maxSafeDrop >= 0 && missionY < landingY && mobY < landingY - maxSafeDrop;
    }
}
