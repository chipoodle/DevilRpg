package com.chipoodle.devilrpg.entity.nav;

import java.util.function.BooleanSupplier;

/**
 * Final dispatch gate shared by every NavExecutor terrain-action path.
 * The callback is never evaluated unless the active-transition lease was acquired.
 */
public final class TraversalActionDispatchPolicy {
    private TraversalActionDispatchPolicy() {
    }

    public static boolean dispatch(boolean leaseAcquired, BooleanSupplier action) {
        return leaseAcquired && action != null && action.getAsBoolean();
    }
}
