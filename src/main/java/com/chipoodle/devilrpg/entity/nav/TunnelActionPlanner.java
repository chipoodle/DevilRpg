package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Pure straight-tunnel planner. It never moves the mob: it only emits a bounded sequence of
 * protected/breakability-validated feet+head block pairs with the stand cell for each step.
 */
public final class TunnelActionPlanner {
    private TunnelActionPlanner() { }

    public record Step(BlockPos stand, BlockPos feetBlock, BlockPos headBlock, BlockPos post) { }

    public static List<Step> straight(BlockPos start, int dx, int dz, int maxSteps,
                                      Predicate<BlockPos> canBreak,
                                      Predicate<BlockPos> protectedCell,
                                      Predicate<BlockPos> supported) {
        List<Step> out = new ArrayList<>();
        if (start == null || maxSteps <= 0 || (dx == 0 && dz == 0)) return out;
        int sx = Integer.signum(dx);
        int sz = Integer.signum(dz);
        if (sx != 0 && sz != 0) return out;
        BlockPos cursor = start;
        for (int i = 0; i < maxSteps; i++) {
            BlockPos front = cursor.offset(sx, 0, sz);
            BlockPos head = front.above();
            if (protectedCell.test(front) || protectedCell.test(head)) break;
            if (!canBreak.test(front) || !canBreak.test(head)) break;
            if (!supported.test(front)) break;
            out.add(new Step(cursor.immutable(), front.immutable(), head.immutable(), front.immutable()));
            cursor = front;
        }
        return out;
    }
}
