package com.chipoodle.devilrpg.entity.nav;

/**
 * Candidate directions for the navigation graph.
 *
 * The eight horizontal neighbours are retained and a same-column direction is added so
 * the planner can represent validated vertical movement (especially safe descent through
 * a one-block shaft). This only creates candidates: NavPlanner.validarArista remains the
 * single authority for support, collision, headroom, protection, landing and safe-drop rules.
 */
public final class NavNeighborPolicy {
    private static final int[][] OFFSETS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
            {0, 0}
    };

    private NavNeighborPolicy() {
    }

    /** Returns an isolated copy: callers cannot mutate the policy's canonical directions. */
    public static int[][] offsets() {
        int[][] copy = new int[OFFSETS.length][];
        for (int i = 0; i < OFFSETS.length; i++) {
            copy[i] = OFFSETS[i].clone();
        }
        return copy;
    }

    public static int offsetCount() {
        return OFFSETS.length;
    }
}
