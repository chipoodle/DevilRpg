package com.chipoodle.devilrpg.entity.nav;

/** Physical preconditions for bounded STEP_UP execution during DROP take-off recovery. */
public final class NavStepUpRecoveryPolicy {
    private NavStepUpRecoveryPolicy() {
    }

    /** A recovery A* search must start from the mob's actual, supported feet cell, never an airborne blockPosition. */
    public static boolean canStartFrom(boolean grounded, boolean feetValid, boolean supported, boolean fits) {
        return grounded && feetValid && supported && fits;
    }

    /** The active edge must still be a one-block horizontal step from the current physical cell. */
    public static boolean isAdjacentOneBlockStep(int dx, int dy, int dz) {
        int horizontal = Math.abs(dx) + Math.abs(dz);
        return dy == 1 && horizontal > 0 && Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
    }

    /**
     * Allows a natural jump assist only for an adjacent, one-block STEP_UP whose source is grounded
     * and supported, whose lower step is physically obstructed, and whose destination has verified
     * headroom, body clearance and support. It rejects stale airborne origins and unsupported landings.
     */
    public static boolean canAssist(int dx, int dy, int dz, boolean sourceGrounded, boolean sourceSupported,
                                    boolean blockedStep, boolean headClear, boolean destinationFits,
                                    boolean destinationSupported) {
        return isAdjacentOneBlockStep(dx, dy, dz) && sourceGrounded && sourceSupported
                && blockedStep && headClear && destinationFits && destinationSupported;
    }
}
