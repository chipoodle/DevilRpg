package com.chipoodle.devilrpg.agent;

/** Relative priority for a DevilRpg navigation lease. Higher wins. */
public enum NavigationPriority {
    IDLE(10),
    WORK(50),
    EMERGENCY(80),
    SURVIVAL(100);

    private final int rank;

    NavigationPriority(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}
