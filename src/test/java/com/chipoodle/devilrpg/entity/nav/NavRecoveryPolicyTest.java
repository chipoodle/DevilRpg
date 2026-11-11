package com.chipoodle.devilrpg.entity.nav;

/**
 * A2 FASES 2/3 · prueba enfocada (sin servidor) de la politica de recuperacion acotada.
 * Ejercita las mismas decisiones que usa {@link NavExecutor}: reintento por presupuesto, fallo terminal,
 * rearme y espera. Se ejecuta con {@code javac}+{@code java}.
 */
public final class NavRecoveryPolicyTest {

    private static int fallos;

    public static void main(String[] args) {
        final int max = 6;
        final int maxRearmes = 4;

        // 1. Presupuesto agotado repetido SIN progreso: los primeros son reintentos; el tope es terminal.
        for (int i = 1; i < max; i++) {
            NavRecoveryPolicy.Decision d = NavRecoveryPolicy.trasPresupuesto(i, max, false);
            comprobar("sin progreso " + i + "/" + max + " reintenta", d.reintentar() && !d.terminal());
        }
        NavRecoveryPolicy.Decision tope = NavRecoveryPolicy.trasPresupuesto(max, max, false);
        comprobar("sin progreso en el tope es terminal", tope.terminal() && !tope.reintentar());
        comprobar("el motivo terminal nombra el presupuesto", tope.motivo().contains("presupuesto"));

        // 2. Presupuesto agotado seguido de replanificacion con progreso: NO es terminal.
        NavRecoveryPolicy.Decision conProgreso = NavRecoveryPolicy.trasPresupuesto(max, max, true);
        comprobar("con progreso sigue siendo inconcluyente", conProgreso.reintentar() && !conProgreso.terminal());
        comprobar("con progreso no se declara fallo", !conProgreso.terminal());

        // 3. Movimiento del objetivo: una mision nueva reinicia el contador de rearmes y permite rearmar.
        NavRecoveryPolicy.Decision misionNueva = NavRecoveryPolicy.trasFallo(0, maxRearmes, 100L, 0L, true);
        comprobar("mision nueva (rearmes=0) permite rearmar", misionNueva.rearmar() && !misionNueva.terminal());

        // 4. Limite de recuperacion alcanzado: terminal y sin rearme.
        NavRecoveryPolicy.Decision limite = NavRecoveryPolicy.trasFallo(maxRearmes, maxRearmes, 1000L, 0L, true);
        comprobar("limite de rearmes es terminal", limite.terminal() && !limite.rearmar());
        comprobar("el motivo terminal nombra los rearmes", limite.motivo().contains("rearmes"));

        // 5. Enfriamiento: antes del proximo rearme se espera, no se rearma ni se falla.
        NavRecoveryPolicy.Decision espera = NavRecoveryPolicy.trasFallo(1, maxRearmes, 100L, 160L, true);
        comprobar("antes del enfriamiento se espera", espera.esperar() && !espera.rearmar() && !espera.terminal());

        // 6. Destino permanentemente invalido: no se rearma nunca.
        NavRecoveryPolicy.Decision invalido = NavRecoveryPolicy.trasFallo(0, maxRearmes, 100L, 0L, false);
        comprobar("destino invalido no se rearma", invalido.terminal() && !invalido.rearmar());
        comprobar("el motivo terminal nombra el destino",
                invalido.motivo().contains("destino") || invalido.motivo().contains("celda"));

        // 7. (e) Objetivo local no utilizable: sin progreso, el numero de intentos es terminal; no hay
        //    PLANNING infinito. Con progreso medible, se reintenta.
        final int maxInvalidos = 6;
        for (int i = 1; i < maxInvalidos; i++) {
            NavRecoveryPolicy.Decision d = NavRecoveryPolicy.trasObjetivoInvalido(i, maxInvalidos, false);
            comprobar("objetivo invalido " + i + "/" + maxInvalidos + " reintenta",
                    d.reintentar() && !d.terminal());
        }
        NavRecoveryPolicy.Decision invalidoTope =
                NavRecoveryPolicy.trasObjetivoInvalido(maxInvalidos, maxInvalidos, false);
        comprobar("objetivo invalido en el tope es terminal",
                invalidoTope.terminal() && !invalidoTope.reintentar());
        NavRecoveryPolicy.Decision invalidoProgreso =
                NavRecoveryPolicy.trasObjetivoInvalido(maxInvalidos, maxInvalidos, true);
        comprobar("objetivo invalido con progreso no es terminal",
                invalidoProgreso.reintentar() && !invalidoProgreso.terminal());

        if (fallos > 0) {
            System.out.println("NAV_POLICY_TEST: FAIL (" + fallos + " fallos)");
            System.exit(1);
        }
        System.out.println("NAV_POLICY_TEST: OK (todas las aserciones verdes)");
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
