package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.function.Predicate;

/**
 * Resolves a grounded navigation source from the mob's real AABB footprint when its integer
 * blockPosition cell is unsupported. Only physically valid overlapping neighboring feet cells
 * are eligible; selection is deterministic.
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

    /**
     * Selects the safest deterministic source among all eight same-height neighboring cells.
     * Multiple valid overlaps are alternatives, not an ambiguity requiring a dead stop: each
     * candidate is independently checked by the caller for protection, feet clearance, support
     * and the real AABB at its centered destination. Prefer the greatest actual footprint overlap,
     * then the nearest cell center, then lexicographic coordinates for a stable final tie-break.
     */
    public static BlockPos bestAdjacentCandidate(BlockPos current, AABB footprint,
                                                 double centerX, double centerZ,
                                                 Predicate<BlockPos> physicallyValid) {
        if (current == null || footprint == null || physicallyValid == null
                || !Double.isFinite(centerX) || !Double.isFinite(centerZ)) {
            return null;
        }
        int[][] offsets = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };
        BlockPos selected = null;
        double selectedOverlap = -1.0D;
        double selectedDistance = Double.POSITIVE_INFINITY;
        for (int[] offset : offsets) {
            BlockPos candidate = current.offset(offset[0], 0, offset[1]);
            if (!overlapsHorizontalCell(footprint, candidate) || !physicallyValid.test(candidate)) {
                continue;
            }
            double overlap = horizontalOverlapArea(footprint, candidate);
            double dx = centerX - (candidate.getX() + 0.5D);
            double dz = centerZ - (candidate.getZ() + 0.5D);
            double distance = dx * dx + dz * dz;
            boolean betterOverlap = overlap > selectedOverlap + 1.0E-9D;
            boolean sameOverlap = Math.abs(overlap - selectedOverlap) <= 1.0E-9D;
            boolean betterDistance = distance < selectedDistance - 1.0E-9D;
            boolean sameDistance = Math.abs(distance - selectedDistance) <= 1.0E-9D;
            boolean stableTieBreak = selected == null || candidate.getX() < selected.getX()
                    || (candidate.getX() == selected.getX() && candidate.getZ() < selected.getZ());
            if (selected == null || betterOverlap
                    || (sameOverlap && betterDistance)
                    || (sameOverlap && sameDistance && stableTieBreak)) {
                selected = candidate;
                selectedOverlap = overlap;
                selectedDistance = distance;
            }
        }
        return selected;
    }

    /** Horizontal intersection area between the real mob footprint and a candidate block column. */
    public static double horizontalOverlapArea(AABB footprint, BlockPos cell) {
        if (footprint == null || cell == null) {
            return 0.0D;
        }
        double overlapX = Math.max(0.0D, Math.min(footprint.maxX, cell.getX() + 1.0D)
                - Math.max(footprint.minX, cell.getX()));
        double overlapZ = Math.max(0.0D, Math.min(footprint.maxZ, cell.getZ() + 1.0D)
                - Math.max(footprint.minZ, cell.getZ()));
        return overlapX * overlapZ;
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
