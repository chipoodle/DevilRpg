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
        System.out.println("NAV_LOCAL_OBJECTIVE_PROGRESS_POLICY_TEST: OK (7 assertions)");
    }

    private static void comprobar(String name, boolean ok) {
        if (!ok) throw new AssertionError(name);
    }
}
