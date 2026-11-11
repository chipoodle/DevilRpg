package com.chipoodle.devilrpg.agent;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Immutable output of a pure planner. It never mutates the world. */
public record TaskProposal(
        UUID taskId,
        String kind,
        BlockPos target,
        int priority,
        Set<String> requiredClaims,
        Map<String, Integer> expectedResources) {

    public TaskProposal {
        if (taskId == null || kind == null || kind.isBlank() || target == null) {
            throw new IllegalArgumentException("taskId, kind and target are required");
        }
        requiredClaims = requiredClaims == null ? Set.of() : Set.copyOf(requiredClaims);
        expectedResources = expectedResources == null ? Map.of() : Map.copyOf(expectedResources);
    }
}
