package com.chipoodle.devilrpg.entity.nav;

/** Focused pure tests for local action-node acceptance and completion. */
public final class NavObjectivePolicyTest {
    private static int fallos;

    public static void main(String[] args) {
        comprobar("BRIDGE is a valid local action objective",
                NavObjectivePolicy.accionLocalValida(true, false, false));
        comprobar("BREAK is valid while target remains breakable",
                NavObjectivePolicy.accionLocalValida(false, true, true));
        comprobar("BREAK is rejected when target cannot be broken",
                !NavObjectivePolicy.accionLocalValida(false, true, false));
        comprobar("WALK is not an action objective",
                !NavObjectivePolicy.accionLocalValida(false, false, true));
        comprobar("non-action transition is rejected",
                !NavObjectivePolicy.accionLocalValida(false, false, false));

        comprobar("BREAK already resolved when target cell is air",
                NavObjectivePolicy.accionResuelta(true, true));
        comprobar("BREAK remains unresolved while target cell is solid",
                !NavObjectivePolicy.accionResuelta(true, false));
        comprobar("BRIDGE already resolved when target cell is solid",
                NavObjectivePolicy.accionResuelta(false, false));
        comprobar("BRIDGE remains unresolved while target cell is air",
                !NavObjectivePolicy.accionResuelta(false, true));

        if (fallos > 0) {
            System.out.println("NAV_OBJECTIVE_POLICY_TEST: FAIL (" + fallos + " fallos)");
            System.exit(1);
        }
        System.out.println("NAV_OBJECTIVE_POLICY_TEST: OK (todas las aserciones verdes)");
    }

    private static void comprobar(String nombre, boolean ok) {
        if (!ok) {
            fallos++;
            System.out.println("  FALLO: " + nombre);
        } else {
            System.out.println("  ok: " + nombre);
        }
    }
}
