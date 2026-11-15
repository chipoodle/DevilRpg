package com.chipoodle.devilrpg.entity;

public final class AttackerRetreatDestinationPolicyTest {
    public static void main(String[] args) {
        comprobar("valid supported clear cell",
                AttackerRetreatDestinationPolicy.viable(true, true, true, false, true, 12, 0, 8));
        comprobar("missing support rejected",
                !AttackerRetreatDestinationPolicy.viable(true, true, false, false, true, 12, 0, 8));
        comprobar("collision rejected",
                !AttackerRetreatDestinationPolicy.viable(false, true, true, false, true, 12, 0, 8));
        comprobar("head collision rejected",
                !AttackerRetreatDestinationPolicy.viable(true, false, true, false, true, 12, 0, 8));
        comprobar("hazard rejected",
                !AttackerRetreatDestinationPolicy.viable(true, true, true, true, true, 12, 0, 8));
        comprobar("unreachable rejected",
                !AttackerRetreatDestinationPolicy.viable(true, true, true, false, false, 12, 0, 8));
        comprobar("too close threat rejected",
                !AttackerRetreatDestinationPolicy.viable(true, true, true, false, true, 4, 0, 8));
        comprobar("too deep fall rejected",
                !AttackerRetreatDestinationPolicy.viable(true, true, true, false, true, 12, -4, 8));
        comprobar("reasonable ascent accepted",
                AttackerRetreatDestinationPolicy.viable(true, true, true, false, true, 12, 2, 8));
        comprobar("inside attacker must stay inside",
                AttackerRetreatDestinationPolicy.preservesInterior(true, true));
        comprobar("inside attacker cannot choose outside",
                !AttackerRetreatDestinationPolicy.preservesInterior(true, false));
        comprobar("outside attacker may enter",
                AttackerRetreatDestinationPolicy.preservesInterior(false, true));
        System.out.println("ATTACKER_RETREAT_DESTINATION_POLICY_TEST: OK (12 assertions)");
    }

    private static void comprobar(String name, boolean ok) {
        if (!ok) throw new AssertionError(name);
    }
}
