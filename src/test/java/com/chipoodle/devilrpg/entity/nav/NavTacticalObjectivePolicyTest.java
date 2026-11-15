package com.chipoodle.devilrpg.entity.nav;

public final class NavTacticalObjectivePolicyTest {
    public static void main(String[] args) {
        comprobar("conserva orilla mientras se reenvia la misma mision",
                NavTacticalObjectivePolicy.preserveTacticalObjective(true, true));
        comprobar("una mision nueva cancela la prioridad tactica",
                !NavTacticalObjectivePolicy.preserveTacticalObjective(true, false));
        comprobar("sin objetivo tactico no inventa prioridad",
                !NavTacticalObjectivePolicy.preserveTacticalObjective(false, true));
        comprobar("sin tactico ni mision repetida no preserva",
                !NavTacticalObjectivePolicy.preserveTacticalObjective(false, false));
        System.out.println("NAV_TACTICAL_OBJECTIVE_POLICY_TEST: OK (4 assertions)");
    }
    private static void comprobar(String nombre, boolean ok) {
        if (!ok) throw new AssertionError(nombre);
    }
}
