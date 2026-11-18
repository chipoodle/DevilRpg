package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

import java.util.function.Predicate;

/** Chooses a supported one-cell DROP approach before the landing is targeted. */
public final class NavDropApproachPolicy {
    private NavDropApproachPolicy() {
    }

    /**
     * Returns the current cell when it is already horizontally adjacent to the landing (and the
     * caller validated it). Otherwise returns a valid same-Y neighbor that strictly reduces the
     * horizontal distance to the landing. Null means no safe approach cell exists.
     */
    public static BlockPos choose(BlockPos current, BlockPos landing, Predicate<BlockPos> safe) {
        if (current == null || landing == null || safe == null) {
            return null;
        }
        int currentDistance = horizontalDistanceSqr(current, landing);
        if (currentDistance <= 2) {
            // Chebyshev-adjacent in X/Z: 1x1, 1x0 or 0x1.
            int dx = Math.abs(current.getX() - landing.getX());
            int dz = Math.abs(current.getZ() - landing.getZ());
            if (dx <= 1 && dz <= 1) {
                return current.immutable();
            }
        }
        int[][] offsets = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };
        BlockPos best = null;
        int bestDistance = currentDistance;
        for (int[] offset : offsets) {
            BlockPos candidate = current.offset(offset[0], 0, offset[1]);
            int distance = horizontalDistanceSqr(candidate, landing);
            if (distance >= bestDistance || !safe.test(candidate)) {
                continue;
            }
            boolean better = distance < bestDistance;
            boolean tie = distance == bestDistance;
            boolean lex = best == null || candidate.getX() < best.getX()
                    || (candidate.getX() == best.getX() && candidate.getZ() < best.getZ());
            if (better || (tie && lex)) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static int horizontalDistanceSqr(BlockPos a, BlockPos b) {
        int dx = a.getX() - b.getX();
        int dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}
