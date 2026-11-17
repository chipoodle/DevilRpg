package com.chipoodle.devilrpg.entity.nav;

/** Hard expansion-budget gate shared by bounded navigation searches. */
public final class NavExpansionBudgetPolicy {
    private NavExpansionBudgetPolicy() {
    }

    /** True only while another expansion can occur without exceeding the configured hard limit. */
    public static boolean canExpand(int expansions, int maxExpansions) {
        return expansions >= 0 && maxExpansions > 0 && expansions < maxExpansions;
    }
}
