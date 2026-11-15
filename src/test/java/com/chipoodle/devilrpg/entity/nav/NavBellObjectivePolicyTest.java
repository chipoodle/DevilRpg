package com.chipoodle.devilrpg.entity.nav;

public final class NavBellObjectivePolicyTest {
    public static void main(String[] args) {
        comprobar("bell block is valid objective",
                NavBellObjectivePolicy.objectiveValid(true, false));
        comprobar("destroyed bell is not valid",
                !NavBellObjectivePolicy.objectiveValid(true, true));
        comprobar("replaced block is not a bell",
                !NavBellObjectivePolicy.objectiveValid(false, false));
        comprobar("inside reach can destroy",
                NavBellObjectivePolicy.canDestroy(true, true, 4.0, 25.0));
        comprobar("outside cannot destroy",
                !NavBellObjectivePolicy.canDestroy(true, false, 4.0, 25.0));
        comprobar("out of reach cannot destroy",
                !NavBellObjectivePolicy.canDestroy(true, true, 100.0, 25.0));
        comprobar("dead cannot destroy",
                !NavBellObjectivePolicy.canDestroy(false, true, 1.0, 25.0));
        System.out.println("NAV_BELL_OBJECTIVE_POLICY_TEST: OK (7 assertions)");
    }

    private static void comprobar(String name, boolean ok) {
        if (!ok) throw new AssertionError(name);
    }
}
