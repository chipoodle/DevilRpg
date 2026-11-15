package com.chipoodle.devilrpg.entity;

public final class AttackerThreatPolicyTest {
    public static void main(String[] args) {
        comprobar("two nearby threats trigger group disengage",
                AttackerThreatPolicy.shouldDisengageFromGroup(9.0F, 10.0F, 2, 5.0D));
        comprobar("one threat does not trigger group rule",
                !AttackerThreatPolicy.shouldDisengageFromGroup(9.0F, 10.0F, 1, 5.0D));
        comprobar("distant group does not trigger",
                !AttackerThreatPolicy.shouldDisengageFromGroup(9.0F, 10.0F, 3, 12.0D));
        comprobar("full health does not trigger",
                !AttackerThreatPolicy.shouldDisengageFromGroup(10.0F, 10.0F, 3, 5.0D));
        comprobar("dead does not trigger",
                !AttackerThreatPolicy.shouldDisengageFromGroup(0.0F, 10.0F, 3, 5.0D));
        System.out.println("ATTACKER_THREAT_POLICY_TEST: OK (5 assertions)");
    }

    private static void comprobar(String name, boolean ok) {
        if (!ok) throw new AssertionError(name);
    }
}
