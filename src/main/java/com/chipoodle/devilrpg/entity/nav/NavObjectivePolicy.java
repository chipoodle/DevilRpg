package com.chipoodle.devilrpg.entity.nav;

/** Pure acceptance and completion policy for local action nodes. */
public final class NavObjectivePolicy {
    private NavObjectivePolicy() {
    }

    /**
     * BRIDGE nodes are actionable objectives. BREAK nodes are actionable only while the entity
     * can still break the requested block; a solid block is not rejected merely for not being a
     * valid standing cell.
     */
    public static boolean accionLocalValida(boolean bridgeTransition,
                                             boolean breakTransition,
                                             boolean canBreakTarget) {
        return bridgeTransition || (breakTransition && canBreakTarget);
    }

    /**
     * A BREAK is already resolved when its target cell is air. A BRIDGE is resolved when its
     * target cell is no longer air. This must be checked before trying to navigate to the node.
     */
    public static boolean accionResuelta(boolean breakTransition, boolean targetIsAir) {
        return breakTransition ? targetIsAir : !targetIsAir;
    }
}
