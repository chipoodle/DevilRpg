package com.chipoodle.devilrpg.entity.goal;

/** Bounded village-return policy for workers displaced beyond profession range. */
public final class VillagerReturnPolicy {
    /** Profession goals currently refuse work beyond FENCE_RADIUS + 10. */
    public static final double RETURN_DISTANCE = 72.0D;
    /** Re-enter profession range with hysteresis so the return goal cannot oscillate at the edge. */
    public static final double STOP_DISTANCE = 64.0D;

    private VillagerReturnPolicy() {
    }

    public static boolean shouldStart(final double distanceSqr) {
        return distanceSqr > RETURN_DISTANCE * RETURN_DISTANCE;
    }

    public static boolean shouldContinue(final double distanceSqr) {
        return distanceSqr > STOP_DISTANCE * STOP_DISTANCE;
    }
}
