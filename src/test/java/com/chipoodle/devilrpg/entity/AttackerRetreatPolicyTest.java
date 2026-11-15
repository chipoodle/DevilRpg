package com.chipoodle.devilrpg.entity;

public final class AttackerRetreatPolicyTest {
    public static void main(String[] args) {
        comprobar("wounded attacker triggers short step-back",
                AttackerRetreatPolicy.shouldRetreat(8.4F, 10.0F, 5.0D, 0, 1000, -1000));
        comprobar("healthy attacker does not retreat",
                !AttackerRetreatPolicy.shouldRetreat(9.5F, 10.0F, 5.0D, 0, 1000, -1000));
        comprobar("zero health is not valid",
                !AttackerRetreatPolicy.shouldRetreat(0.0F, 10.0F, 5.0D, 0, 1000, -1000));
        comprobar("dead activation rejected", !AttackerRetreatPolicy.isValidActivation(0.0F, false));
        comprobar("no threat does not retreat",
                !AttackerRetreatPolicy.shouldRetreat(4.0F, 10.0F, 20.0D, 0, 1000, -1000));
        comprobar("finite retreat budget",
                !AttackerRetreatPolicy.shouldRetreat(4.0F, 10.0F, 5.0D, 2, 1000, -1000));
        comprobar("cooldown prevents oscillation",
                !AttackerRetreatPolicy.shouldRetreat(4.0F, 10.0F, 5.0D, 0, 1000, 900));
        comprobar("no indefinite critical evasion",
                !AttackerRetreatPolicy.shouldRemainEvasive(2.0F, 10.0F, 1.0D));
        comprobar("bounded maneuver duration", AttackerRetreatPolicy.RETREAT_TICKS <= 60);
        comprobar("local destination radius", AttackerRetreatPolicy.MAX_DISTANCE <= 7.0D);
        System.out.println("ATTACKER_RETREAT_POLICY_TEST: OK (10 assertions)");
    }

    private static void comprobar(String name, boolean ok) {
        if (!ok) throw new AssertionError(name);
    }
}
