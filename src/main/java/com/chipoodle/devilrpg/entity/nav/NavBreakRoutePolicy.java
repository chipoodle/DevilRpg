package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** Attaches a validated BREAK action to the final reachable stand waypoint of a local route. */
public final class NavBreakRoutePolicy {
    private NavBreakRoutePolicy() {
    }

    /**
     * Converts the route's final WALK-to-stand node into a BREAK action node. The route destination
     * remains the stand cell; the solid action target is kept separately and never becomes a movement waypoint.
     *
     * @return a new route, or {@code null} when the supplied route does not terminate at the requested stand
     */
    public static NavRoute attachFinalBreak(NavRoute route, BlockPos standPos, BlockPos actionPos) {
        if (route == null || route.isEmpty() || standPos == null || actionPos == null
                || standPos.equals(actionPos)) {
            return null;
        }
        List<NavRoute.Node> nodes = new ArrayList<>(route.nodes());
        int lastIndex = nodes.size() - 1;
        NavRoute.Node last = nodes.get(lastIndex);
        if (!last.standPos().equals(standPos)) {
            return null;
        }
        nodes.set(lastIndex, new NavRoute.Node(standPos.immutable(),
                NavRoute.Transition.BREAK, actionPos.immutable()));
        return new NavRoute(nodes, route.destination());
    }
}
