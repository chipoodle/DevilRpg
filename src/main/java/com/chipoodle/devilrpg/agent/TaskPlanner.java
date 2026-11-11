package com.chipoodle.devilrpg.agent;

import java.util.Optional;

/** Pure task selection. Implementations must not mutate world, inventory or entity state. */
public interface TaskPlanner {
    Optional<TaskProposal> plan(WorldObservation observation);
}
