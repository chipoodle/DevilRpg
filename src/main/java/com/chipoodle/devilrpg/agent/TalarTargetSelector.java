package com.chipoodle.devilrpg.agent;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Optional;

/**
 * Pure TALAR candidate choice. Candidate generation, reachability checks and forestry
 * operations remain in the legacy Goal; this class owns only the final selection rule.
 */
public final class TalarTargetSelector {

    private TalarTargetSelector() {
    }

    /** Prefers an inside candidate; otherwise the nearest outside candidate to the worker. */
    public static Optional<BlockPos> select(List<BlockPos> inside, List<BlockPos> outside,
                                            BlockPos workerPosition) {
        if (inside != null && !inside.isEmpty()) {
            return Optional.of(inside.get(0).immutable());
        }
        if (outside == null || outside.isEmpty() || workerPosition == null) {
            return Optional.empty();
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : outside) {
            double d = candidate.distSqr(workerPosition);
            if (d < bestDistance) {
                bestDistance = d;
                best = candidate;
            }
        }
        return Optional.ofNullable(best).map(BlockPos::immutable);
    }
}
