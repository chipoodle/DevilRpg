package com.chipoodle.devilrpg.entity.nav;

/** Explicit stair-construction outcome; never infer placement from a boolean alone. */
public enum StairBuildResult {
    STAIR_PLACED,
    ACCESS_CLEARED,
    CADENCE_WAIT,
    NO_PROGRESS,
    REJECTED,
    FAILED
}
