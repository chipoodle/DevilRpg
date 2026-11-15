package com.chipoodle.devilrpg.entity.nav;

/** BREAK approach: el primer bloque visible puede ser el objetivo o una obstrucción anterior. */
public final class NavActionApproachPolicyTest {
    public static void main(String[] args) {
        comprobar("delega obstáculo distinto cuando Path no alcanza BREAK",
                NavActionApproachPolicy.debeDelegarPrimerBloque(false, true, true));
        comprobar("delega el propio objetivo BREAK cuando Path no lo puede alcanzar",
                NavActionApproachPolicy.debeDelegarPrimerBloque(false, true, true));
        comprobar("no altera BREAK cuando Path sí es alcanzable",
                !NavActionApproachPolicy.debeDelegarPrimerBloque(true, true, true));
        comprobar("no intenta romper un bloque fuera del alcance",
                !NavActionApproachPolicy.debeDelegarPrimerBloque(false, false, true));
        comprobar("no aplica la regla a BRIDGE",
                !NavActionApproachPolicy.debeDelegarPrimerBloque(false, true, false));
        comprobar("no delega si el raycast no encuentra ningún bloque",
                !NavActionApproachPolicy.debeDelegarPrimerBloque(false, false, true));
        System.out.println("NAV_ACTION_APPROACH_POLICY_TEST: OK (6 aserciones)");
    }

    private static void comprobar(String nombre, boolean ok) {
        if (!ok) {
            throw new AssertionError(nombre);
        }
    }
}
