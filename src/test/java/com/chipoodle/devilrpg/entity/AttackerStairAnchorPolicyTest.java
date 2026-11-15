package com.chipoodle.devilrpg.entity;

public final class AttackerStairAnchorPolicyTest {
    public static void main(String[] args) {
        comprobar("current support accepted",
                AttackerStairAnchorPolicy.anchorIsCurrent(606, 95, 600, 607, 96, 601));
        comprobar("stale far anchor rejected",
                !AttackerStairAnchorPolicy.anchorIsCurrent(594, 95, 600, 607, 96, 601));
        comprobar("vertical mismatch rejected",
                !AttackerStairAnchorPolicy.anchorIsCurrent(607, 100, 601, 607, 96, 601));
        System.out.println("ATTACKER_STAIR_ANCHOR_POLICY_TEST: OK (3 assertions)");
    }

    private static void comprobar(String name, boolean ok) {
        if (!ok) throw new AssertionError(name);
    }
}
