package com.chipoodle.devilrpg.entity.goal;

/** Focused test for the explicit villager Goal priority policy. */
public final class VillageGoalPriorityTest {

    public static void main(String[] args) {
        check(VillageGoalPriority.EMERGENCY < VillageGoalPriority.CRITICAL, "flee above critical need");
        check(VillageGoalPriority.CRITICAL < VillageGoalPriority.UTILITY, "critical above utilities");
        check(VillageGoalPriority.UTILITY < VillageGoalPriority.SECURITY, "utilities above security");
        check(VillageGoalPriority.SECURITY < VillageGoalPriority.PROFESSION, "security above work");
        check(VillageGoalPriority.PROFESSION < VillageGoalPriority.SUPPORT, "work above support");
        check(VillageGoalPriority.SUPPORT < VillageGoalPriority.OPPORTUNISTIC, "support above pickup");
        check(VillageGoalPriority.EMERGENCY == 0, "emergency is the highest priority");
        check(VillageGoalPriority.PROFESSION == 4, "profession tier is four");
        check(VillageGoalPriority.CRITICAL == 1, "eating is one tier, not negative");
        System.out.println("VILLAGE_GOAL_PRIORITY_TEST: OK");
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError("FAILED: " + what);
        }
    }
}
