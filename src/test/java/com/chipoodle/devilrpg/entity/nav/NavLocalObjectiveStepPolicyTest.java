package com.chipoodle.devilrpg.entity.nav;

public final class NavLocalObjectiveStepPolicyTest {
    public static void main(String[] args) {
        comprobar("new objective is a new local step",
                NavLocalObjectiveStepPolicy.decidir(false, false, false)
                        == NavLocalObjectiveStepPolicy.Decision.ACEPTAR_NUEVO);
        comprobar("same usable objective inside window is reused",
                NavLocalObjectiveStepPolicy.decidir(true, true, true)
                        == NavLocalObjectiveStepPolicy.Decision.REUTILIZAR);
        comprobar("same unusable objective is rejected without a new step",
                NavLocalObjectiveStepPolicy.decidir(true, false, true)
                        == NavLocalObjectiveStepPolicy.Decision.RECHAZAR_REPETIDO_INUTILIZABLE);
        comprobar("same usable objective outside window is not reused",
                NavLocalObjectiveStepPolicy.decidir(true, true, false)
                        == NavLocalObjectiveStepPolicy.Decision.RECHAZAR_REPETIDO_INUTILIZABLE);
        comprobar("same unusable objective outside window is rejected",
                NavLocalObjectiveStepPolicy.decidir(true, false, false)
                        == NavLocalObjectiveStepPolicy.Decision.RECHAZAR_REPETIDO_INUTILIZABLE);
        System.out.println("NAV_LOCAL_OBJECTIVE_STEP_POLICY_TEST: OK (5 assertions)");
    }

    private static void comprobar(String name, boolean ok) {
        if (!ok) throw new AssertionError(name);
    }
}
