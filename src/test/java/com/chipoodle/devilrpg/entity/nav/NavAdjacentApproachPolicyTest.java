package com.chipoodle.devilrpg.entity.nav;

public final class NavAdjacentApproachPolicyTest {
    public static void main(String[] args) {
        comprobar("nudge permitido para celda adyacente validada", NavAdjacentApproachPolicy.shouldNudge(true, true, 1, 2, true, true));
        comprobar("nudge permitido para descenso diagonal validado", NavAdjacentApproachPolicy.shouldNudge(true, true, 2, 2, true, true));
        comprobar("no nudge si navigation sigue activa", !NavAdjacentApproachPolicy.shouldNudge(false, true, 1, 2, true, true));
        comprobar("no nudge para acciones de bloque", !NavAdjacentApproachPolicy.shouldNudge(true, false, 1, 2, true, true));
        comprobar("no nudge si ya esta en la celda", !NavAdjacentApproachPolicy.shouldNudge(true, true, 0, 2, true, true));
        comprobar("no nudge si esta demasiado lejos", !NavAdjacentApproachPolicy.shouldNudge(true, true, 3, 2, true, true));
        comprobar("no nudge si el cuerpo no cabe", !NavAdjacentApproachPolicy.shouldNudge(true, true, 1, 2, false, true));
        comprobar("no nudge sin soporte", !NavAdjacentApproachPolicy.shouldNudge(true, true, 1, 2, true, false));
        comprobar("nudge DROP permitido para diagonal vertical de 3 celdas", NavAdjacentApproachPolicy.shouldNudge(true, true, 3, 3, true, true));
        comprobar("nudge normal no permite distancia 3", !NavAdjacentApproachPolicy.shouldNudge(true, true, 3, 2, true, true));
        comprobar("DROP nudge usa distancia horizontal 2 con caida vertical 3", NavAdjacentApproachPolicy.shouldNudgeDrop(true, 2, 3, true, true));
        comprobar("DROP nudge rechaza horizontal 4", !NavAdjacentApproachPolicy.shouldNudgeDrop(true, 4, 3, true, true));
        comprobar("DROP nudge rechaza landing sin fit", !NavAdjacentApproachPolicy.shouldNudgeDrop(true, 2, 3, false, true));
        comprobar("DROP nudge rechaza landing sin soporte", !NavAdjacentApproachPolicy.shouldNudgeDrop(true, 2, 3, true, false));
        comprobar("DROP nudge rechaza navigation activa", !NavAdjacentApproachPolicy.shouldNudgeDrop(false, 2, 3, true, true));
        System.out.println("NAV_ADJACENT_APPROACH_POLICY_TEST: OK (15 assertions)");
    }
    private static void comprobar(String nombre, boolean ok) { if (!ok) throw new AssertionError(nombre); }
}
