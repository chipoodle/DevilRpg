package com.chipoodle.devilrpg.entity.nav;

public final class NavStairProgressPolicyTest {
    public static void main(String[] args) {
        comprobar("abandonar escalon muy por encima", NavStairProgressPolicy.shouldAbandonPendingStep(75, 106, 71, 3));
        comprobar("no abandonar dentro del margen seguro", !NavStairProgressPolicy.shouldAbandonPendingStep(103, 106, 71, 3));
        comprobar("abandona al exceder por un bloque la caida segura", NavStairProgressPolicy.shouldAbandonPendingStep(102, 106, 71, 3));
        comprobar("no abandonar si la mision no esta debajo", !NavStairProgressPolicy.shouldAbandonPendingStep(75, 106, 106, 3));
        comprobar("rechazar margen negativo", !NavStairProgressPolicy.shouldAbandonPendingStep(75, 106, 71, -1));
        System.out.println("NAV_STAIR_PROGRESS_POLICY_TEST: OK (5 assertions)");
    }
    private static void comprobar(String nombre, boolean ok) {
        if (!ok) throw new AssertionError(nombre);
    }
}