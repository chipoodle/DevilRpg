package com.chipoodle.devilrpg.entity.nav;

/** Physical preconditions for the bounded one-block STEP_UP assist used during DROP take-off recovery. */
public final class NavStepUpRecoveryPolicy {
    private NavStepUpRecoveryPolicy() {
    }

    /**
     * Allows a natural jump assist only for an adjacent, one-block STEP_UP whose lower step is
     * physically obstructed but whose destination has verified headroom, body clearance and support.
     * It deliberately rejects multi-block recovery, same-column impulses, and unsupported landings.
     */
    public static boolean canAssist(int dx, int dy, int dz, boolean blockedStep, boolean headClear,
                                    boolean destinationFits, boolean destinationSupported) {
        int horizontal = Math.abs(dx) + Math.abs(dz);
        return dy == 1 && horizontal > 0 && Math.abs(dx) <= 1 && Math.abs(dz) <= 1
                && blockedStep && headClear && destinationFits && destinationSupported;
    }
}
