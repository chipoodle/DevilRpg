package com.chipoodle.devilrpg.entity.nav;

/**
 * A2 FASE 4 · prueba enfocada (sin servidor) de la semantica de llegada:
 * una llegada tactica NUNCA declara ARRIVED, una etapa intermedia avanza, y solo la mision final (sin etapas)
 * declara llegada. Se ejecuta con {@code javac}+{@code java}.
 */
public final class NavArrivalPolicyTest {

    private static int fallos;

    public static void main(String[] args) {
        // (a) Tactical arrival resumes the mission, never ARRIVED.
        NavArrivalPolicy.Result tactico = NavArrivalPolicy.decidir(true, true, false);
        comprobar("llegada tactica => RETOMAR_MISION", tactico == NavArrivalPolicy.Result.RETOMAR_MISION);
        comprobar("llegada tactica NO declara ARRIVED", !NavArrivalPolicy.declaraArrived(tactico));
        // (a) y tras la llegada tactica se RETOMA LA MISION, no la orilla.
        comprobar("tras llegada tactica el objetivo vuelve a la mision",
                "MISION".equals(NavArrivalPolicy.objetivoTrasLlegada(tactico, "ORILLA", "MISION")));

        // Tactical arrival even when the mission still has stages.
        NavArrivalPolicy.Result tacticoConEtapas = NavArrivalPolicy.decidir(true, true, true);
        comprobar("tactica con etapas tampoco declara ARRIVED",
                tacticoConEtapas == NavArrivalPolicy.Result.RETOMAR_MISION
                        && !NavArrivalPolicy.declaraArrived(tacticoConEtapas));

        // (d) Genuine mission arrival: mission, no stages left -> ARRIVED.
        NavArrivalPolicy.Result mision = NavArrivalPolicy.decidir(false, true, false);
        comprobar("mision final => MISION_LLEGADA", mision == NavArrivalPolicy.Result.MISION_LLEGADA);
        comprobar("mision final SI declara ARRIVED", NavArrivalPolicy.declaraArrived(mision));

        // Mission with pending stages: advance, not ARRIVED.
        NavArrivalPolicy.Result etapa = NavArrivalPolicy.decidir(false, true, true);
        comprobar("mision con etapas => AVANZAR_ETAPA", etapa == NavArrivalPolicy.Result.AVANZAR_ETAPA);
        comprobar("etapa intermedia NO declara ARRIVED", !NavArrivalPolicy.declaraArrived(etapa));

        // Intermediate local objective: recalculate, not ARRIVED.
        NavArrivalPolicy.Result escalon = NavArrivalPolicy.decidir(false, false, false);
        comprobar("objetivo no-mision => RECALCULAR", escalon == NavArrivalPolicy.Result.RECALCULAR);
        comprobar("escalon local NO declara ARRIVED", !NavArrivalPolicy.declaraArrived(escalon));

        if (fallos > 0) {
            System.out.println("NAV_ARRIVAL_TEST: FAIL (" + fallos + " fallos)");
            System.exit(1);
        }
        System.out.println("NAV_ARRIVAL_TEST: OK (todas las aserciones verdes)");
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
