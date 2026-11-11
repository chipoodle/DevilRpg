package com.chipoodle.devilrpg.agent;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Translates the legacy forestry snapshot into an agent proposal. It never selects a
 * forest, target or phase and never acquires a lease. The legacy Goal remains the
 * execution owner.
 */
public final class LegacyForestryTaskAdapter implements ForestryTaskAdapter {

    @Override
    public Optional<TaskProposal> propose(VillagerAgent agent, WorldObservation observation) {
        ForestryTaskSnapshot snapshot = agent == null ? null
                : agent.forestrySnapshot().orElse(null);
        if (snapshot == null || !snapshot.active() || snapshot.target() == null
                || snapshot.taskId() == null) {
            return Optional.empty();
        }
        return Optional.of(new TaskProposal(
                snapshot.taskId(),
                "forestry." + snapshot.phase().toLowerCase(),
                snapshot.target(),
                NavigationPriority.WORK.rank(),
                Set.of("forestry:" + snapshot.target().asLong()),
                Map.of("logs", snapshot.carriedLogs())));
    }

    @Override
    public boolean ownsLegacyTask() {
        return true;
    }
}
