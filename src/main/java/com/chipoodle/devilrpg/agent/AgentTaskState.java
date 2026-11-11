package com.chipoodle.devilrpg.agent;

/** Coarse lifecycle state owned by a DevilRpg agent. Not an entity Brain activity. */
public enum AgentTaskState {
    IDLE,
    PLANNING,
    TRAVELLING,
    EXECUTING,
    COMPLETED,
    INTERRUPTED
}
