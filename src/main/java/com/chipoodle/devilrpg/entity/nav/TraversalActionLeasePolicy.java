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

    /**
     * A2 · Lease del túnel subordinado. El túnel no tiene nodo de ruta: su autoridad es el cursor del plan, que
     * nombra UN bloque exacto (pies o cabeza del paso activo). Se conservan las dos invariantes del actuador:
     * una sola acción de terreno por entidad y tick, y nada de sustituir el bloque autorizado por otro.
     */
    public static boolean canAcquireTunnel(
            long now,
            long leaseTick,
            boolean leaseUsed,
            boolean tunnelActive,
            BlockPos authorizedTarget,
            NavRoute.Transition requestedTransition,
            BlockPos requestedTarget) {
        if (!tunnelActive || authorizedTarget == null || requestedTarget == null) {
            return false;
        }
        if (requestedTransition != NavRoute.Transition.BREAK) {
            return false;
        }
        if (leaseUsed && leaseTick == now) {
            return false;
        }
        return authorizedTarget.equals(requestedTarget);
    }

    private static boolean isTerrainAction(NavRoute.Transition transition) {
        return transition == NavRoute.Transition.BREAK
                || transition == NavRoute.Transition.BUILD
                || transition == NavRoute.Transition.BRIDGE;
    }
}
