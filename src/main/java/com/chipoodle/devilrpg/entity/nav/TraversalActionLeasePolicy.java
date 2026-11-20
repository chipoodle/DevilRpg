package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

/**
 * Pure authorization policy for the per-entity, per-tick terrain actuator lease.
 *
 * The caller supplies the active route node and the exact action cell derived from it.
 * This policy never infers an action from the strategic/global objective.
 */
public final class TraversalActionLeasePolicy {
    private TraversalActionLeasePolicy() {
    }

    public static boolean canAcquire(
            long now,
            long leaseTick,
            boolean leaseUsed,
            boolean hasActiveNode,
            NavRoute.Transition activeTransition,
            BlockPos activeTarget,
            NavRoute.Transition requestedTransition,
            BlockPos requestedTarget) {
        if (!hasActiveNode || activeTransition == null || activeTarget == null
                || requestedTransition == null || requestedTarget == null) {
            return false;
        }
        if (leaseUsed && leaseTick == now) {
            return false;
        }
        if (!isTerrainAction(activeTransition) || activeTransition != requestedTransition) {
            return false;
        }
        return activeTarget.equals(requestedTarget);
    }

    private static boolean isTerrainAction(NavRoute.Transition transition) {
        return transition == NavRoute.Transition.BREAK
                || transition == NavRoute.Transition.BUILD
                || transition == NavRoute.Transition.BRIDGE;
    }
}
