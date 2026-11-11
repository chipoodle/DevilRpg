package com.chipoodle.devilrpg.agent;

import net.minecraft.server.level.ServerLevel;

/**
 * Execution boundary. Implementations own mutation and task lifecycle; planners do not.
 * A7-01 defines the contract only and does not replace forestry execution.
 */
public interface TaskExecutor {
    void start(VillagerAgent agent, TaskProposal proposal, ServerLevel level);

    void tick(ServerLevel level);

    void cancel(String reason);

    AgentTaskState state();
}
