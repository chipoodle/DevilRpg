package com.chipoodle.devilrpg.agent;

import java.util.Optional;

/**
 * Boundary for the future forestry migration. The current implementation is intentionally
 * an interface only: A7-01 must not switch or rewrite VillagerLumberjackGoal.
 */
public interface ForestryTaskAdapter {
    Optional<TaskProposal> propose(VillagerAgent agent, WorldObservation observation);

    boolean ownsLegacyTask();
}
