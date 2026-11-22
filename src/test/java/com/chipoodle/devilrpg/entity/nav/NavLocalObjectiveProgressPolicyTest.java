package com.chipoodle.devilrpg.entity.nav;

public final class NavLocalObjectiveProgressPolicyTest {
    public static void main(String[] args) {
        comprobar("idx3 same-level away is rejected",
                !NavLocalObjectiveProgressPolicy.aceptar(86, 71, 86,
                        617, 600, 616, 578, 618, 600, false, false));
        comprobar("idx4 same-level horizontal progress is accepted",
                NavLocalObjectiveProgressPolicy.aceptar(79, 71, 79,
                        588, 507, 597, 533, 589, 508, false, false));
        comprobar("descending candidate accepted",
                NavLocalObjectiveProgressPolicy.aceptar(100, 71, 96,
                        588, 507, 597, 533, 589, 508, false, false));
        comprobar("route continuation accepted",
                NavLocalObjectiveProgressPolicy.aceptar(95, 71, 95,
                        617, 600, 616, 578, 618, 600, true, false));
        comprobar("pending stair accepted",
                NavLocalObjectiveProgressPolicy.aceptar(95, 71, 95,
                        617, 600, 616, 578, 618, 600, false, true));
        comprobar("same-level when mission not below accepted",
                NavLocalObjectiveProgressPolicy.aceptar(71, 71, 71,
                        0, 0, 10, 10, 11, 11, false, false));
        comprobar("far-away same-level rejected",
                !NavLocalObjectiveProgressPolicy.aceptar(79, 71, 79,
                        588, 507, 597, 533, 580, 500, false, false));
        // A2 · MEDIDO en `a2n-20261121-212053`: IDX7/8 (y=63) con mision ARRIBA (y=72) aceptaban un
        // waypoint que los alejaba 22 bloques porque el guardián vertical se auto-anulaba.
        comprobar("idx7 away candidate rejected when mission is ABOVE",
                !NavLocalObjectiveProgressPolicy.aceptar(63, 72, 63,
                        718, 468, 661, 533, 762, 496, false, false));
        comprobar("idx7 closer candidate accepted when mission is ABOVE",
                NavLocalObjectiveProgressPolicy.aceptar(63, 72, 63,
                        718, 468, 661, 533, 712, 470, false, false));
        comprobar("idx7 ascending candidate accepted only with horizontal progress",
                NavLocalObjectiveProgressPolicy.aceptar(63, 72, 64,
                        718, 468, 661, 533, 712, 470, false, false)
                        && !NavLocalObjectiveProgressPolicy.aceptar(63, 72, 64,
                        718, 468, 661, 533, 700, 450, false, false));
        System.out.println("NAV_LOCAL_OBJECTIVE_PROGRESS_POLICY_TEST: OK (10 assertions)");
    }

    private static void comprobar(String name, boolean ok) {
        if (!ok) throw new AssertionError(name);
    }
}
