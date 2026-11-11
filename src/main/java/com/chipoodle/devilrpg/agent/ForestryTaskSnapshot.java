package com.chipoodle.devilrpg.agent;

import net.minecraft.core.BlockPos;

import java.util.UUID;

/** Immutable read-only view of the legacy forestry task. Never a persistence source. */
public record ForestryTaskSnapshot(
        UUID villagerId,
        int objectiveIndex,
        String dimension,
        UUID taskId,
        String phase,
        String controllerState,
        BlockPos target,
        int carriedLogs,
        boolean active) {

    public ForestryTaskSnapshot {
        target = target == null ? null : target.immutable();
    }
}
