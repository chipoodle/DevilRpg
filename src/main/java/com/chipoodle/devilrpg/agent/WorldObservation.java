package com.chipoodle.devilrpg.agent;

import net.minecraft.core.BlockPos;

import java.util.UUID;

/** Read-only snapshot passed to planners. Contains no live entity reference. */
public record WorldObservation(
        UUID agentId,
        String profession,
        String dimension,
        BlockPos position,
        int carriedLogs,
        int saplings,
        long gameTime) {
}
