package com.chipoodle.devilrpg.agent;

import com.chipoodle.devilrpg.world.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.npc.Villager;

/**
 * Shared TALAR next-step calculation. Candidate/target selection and ENTREGAR tiron state
 * remain outside this class. The Goal and the agent call this same implementation.
 */
public final class TalarStepPlanner {

    private static final double REACH = 3.5D;

    private TalarStepPlanner() {
    }

    /** Mutable per-worker cache; explicitly owned by the Goal (OFF) or agent (ENFORCE). */
    public static final class Cache {
        private BlockPos lastTarget;
        private BlockPos standingCell;

        public void clear() {
            lastTarget = null;
            standingCell = null;
        }
    }

    public record Step(BlockPos destination, boolean standingCell, boolean wallGate) {
    }

    public static Step next(ServerLevel level, BlockPos target, BlockPos center,
                            Villager villager, Cache cache) {
        return next(level, target, center, villager, cache, false);
    }

    /** Authoritative production predicate for a TALAR target: the block must still be a log. */
    public static boolean isValidTalarTarget(ServerLevel level, BlockPos target) {
        return target != null && level.getBlockState(target).is(BlockTags.LOGS);
    }

    public static Step next(ServerLevel level, BlockPos target, BlockPos center,
                            Villager villager, Cache cache, boolean requireLog) {
        if (target == null) {
            cache.clear();
            r3("CACHE-CLEAR", null, "null-target");
            return new Step(null, false, false);
        }
        if (requireLog && !isValidTalarTarget(level, target)) {
            cache.clear();
            r3("CACHE-CLEAR", target, "target-not-log");
            return new Step(null, false, false);
        }
        if (!target.equals(cache.lastTarget)) {
            cache.lastTarget = target.immutable();
            cache.standingCell = isValidTalarTarget(level, target)
                    ? standingCell(level, target) : null;
        }
        BlockPos step = cache.standingCell != null ? cache.standingCell : target;
        BlockPos gate = VillageManager.pasoParaCruzarElMuro(level, center, villager, step);
        return new Step(gate != null ? gate : step, cache.standingCell != null, gate != null);
    }

    private static void r3(String event, BlockPos target, String reason) {
        if (Boolean.getBoolean("devilrpg.r3diag")) {
            com.chipoodle.devilrpg.DevilRpg.LOGGER.info("[R3] event={} target={} reason={} tick={}",
                    event, target, reason, System.nanoTime());
        }
    }

    /** The standing cell from which the log is within reach, or null. */
    public static BlockPos standingCell(ServerLevel level, BlockPos log) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -2; dy <= 1; dy++) {
                    BlockPos p = log.offset(dx, dy, dz);
                    if (!VillageManager.esCeldaDePie(level, p)) continue;
                    double d = Math.sqrt(p.distSqr(log));
                    if (d <= REACH - 0.5D && d < bestDistance) {
                        bestDistance = d;
                        best = p.immutable();
                    }
                }
            }
        }
        return best;
    }
}
