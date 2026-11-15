package com.chipoodle.devilrpg.entity.goal;

public final class VillagerFleeSpeedPolicyTest {
    public static void main(String[] args) {
        comprobar("flee speed is 70%", Math.abs(VillagerFleeSpeedPolicy.FLEE_SPEED - 0.945F) < 1e-5F);
        comprobar("flee speed is not original", VillagerFleeSpeedPolicy.FLEE_SPEED < VillagerFleeSpeedPolicy.ORIGINAL_SPEED);
        System.out.println("VILLAGER_FLEE_SPEED_POLICY_TEST: OK (2 assertions)");
    }

    private static void comprobar(String name, boolean ok) {
        if (!ok) throw new AssertionError(name);
    }
}
