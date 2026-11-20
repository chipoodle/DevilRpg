package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

/**
 * Converts a graph-search step into the physical waypoint consumed by NavExecutor.
 *
 * The graph state is not always the mob's standing cell: DROP stores the searched
 * column/state while {@code feet} stores the validated landing height. BREAK likewise
 * keeps its physical stand separate from the post-break graph state.
 */
public final class NavRouteNodePolicy {
    private NavRouteNodePolicy() {
    }

    public static BlockPos routePosition(BlockPos state, NavRoute.Transition transition,
                                         int feet, BlockPos breakStand) {
        if (transition == NavRoute.Transition.BREAK && breakStand != null) {
            return breakStand;
        }
        // BUILD graph state is already the resulting feet cell; its placement cell stays on Node.actionPos.
        if (transition == NavRoute.Transition.DROP) {
            return new BlockPos(state.getX(), feet, state.getZ());
        }
        return state;
    }
}
