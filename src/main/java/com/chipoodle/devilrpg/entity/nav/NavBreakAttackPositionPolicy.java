package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure candidate ordering for a BREAK execution cell. Geometry, support, protection and line of
 * sight are validated by the planner with the real mob/world; this class only defines the bounded
 * search set and the existing action reach.
 */
public final class NavBreakAttackPositionPolicy {
    /** Same reach as NavExecutor's action range (2.5 blocks). */
    public static final double ACTION_REACH_SQR = NavBreakContractPolicy.ACTION_REACH_SQR;

    private static final int[][] OFFSETS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private NavBreakAttackPositionPolicy() {
    }

    public static List<BlockPos> candidates(final BlockPos actionPos, final BlockPos from) {
        List<BlockPos> out = new ArrayList<>(OFFSETS.length);
        for (int[] off : OFFSETS) {
            BlockPos p = actionPos.offset(off[0], 0, off[1]);
            if (!p.equals(from)) {
                out.add(p);
            }
        }
        out.sort(Comparator
                .comparingDouble((BlockPos p) -> from == null ? 0.0D : p.distSqr(from))
                .thenComparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));
        return out;
    }

    public static boolean withinActionReach(final BlockPos standPos, final BlockPos actionPos) {
        return standPos.distSqr(actionPos) <= ACTION_REACH_SQR;
    }

    /** The attack stand must be reached by one already-supported local graph move. */
    public static boolean isSingleStepReachable(final BlockPos from, final BlockPos standPos,
                                                final int feetY) {
        if (from == null || standPos == null || from.getY() != feetY || standPos.getY() != feetY) {
            return false;
        }
        int dx = Math.abs(standPos.getX() - from.getX());
        int dz = Math.abs(standPos.getZ() - from.getZ());
        return Math.max(dx, dz) == 1;
    }

    /** Pure coordinate form used by deterministic tests without a Minecraft classpath. */
    public static boolean withinActionReach(final int dx, final int dy, final int dz) {
        return NavBreakContractPolicy.withinReach(dx, dy, dz);
    }

    public static int candidateOffsetCount() {
        return NavBreakContractPolicy.CANDIDATE_OFFSETS;
    }
}
