package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.function.Predicate;

/**
 * Resolves a grounded navigation source from the mob's real AABB footprint when its integer
 * blockPosition cell is unsupported. Only a unique, overlapping cardinal neighbor is eligible.
 */
public final class NavSourceCellPolicy {
    private static final double MIN_OVERLAP = 1.0E-4D;

    private NavSourceCellPolicy() {
    }

    public static BlockPos uniqueAdjacentCandidate(BlockPos current, AABB footprint,
                                                    Predicate<BlockPos> physicallyValid) {
        if (current == null || footprint == null || physicallyValid == null) {
            return null;
        }
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        BlockPos selected = null;
        for (int[] offset : offsets) {
            BlockPos candidate = current.offset(offset[0], 0, offset[1]);
            if (!overlapsHorizontalCell(footprint, candidate) || !physicallyValid.test(candidate)) {
                continue;
            }
            if (selected != null) {
                return null; // ambiguous footprint: fail closed
            }
            selected = candidate;
        }
        return selected;
    }

    public static boolean overlapsHorizontalCell(AABB footprint, BlockPos cell) {
        double overlapX = Math.min(footprint.maxX, cell.getX() + 1.0D)
                - Math.max(footprint.minX, cell.getX());
        double overlapZ = Math.min(footprint.maxZ, cell.getZ() + 1.0D)
                - Math.max(footprint.minZ, cell.getZ());
        return overlapX > MIN_OVERLAP && overlapZ > MIN_OVERLAP;
    }

    public static boolean centeredInCell(double x, double z, BlockPos cell, double tolerance) {
        if (cell == null || tolerance < 0.0D) {
            return false;
        }
        double dx = x - (cell.getX() + 0.5D);
        double dz = z - (cell.getZ() + 0.5D);
        return dx * dx + dz * dz <= tolerance * tolerance;
    }
}
