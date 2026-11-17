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

        // A pending stair is reached only after physical occupancy, never by the generic 1.5-block tolerance.
        comprobar("proximidad generica NO consume escalon pendiente sin pisarlo",
                !NavArrivalPolicy.proximityArrivalAllowed(true, false));
        comprobar("proximidad generica permite escalon pendiente solo despues de pisarlo",
                NavArrivalPolicy.proximityArrivalAllowed(true, true));
        comprobar("proximidad generica conserva objetivos normales",
                NavArrivalPolicy.proximityArrivalAllowed(false, false));

        // A Phase19 backtrack is complete only at the exact selected historical block cell.
        comprobar("backtrack adyacente dentro de tolerancia NO se consume",
                !NavArrivalPolicy.backtrackArrivalAllowed(true, false));
        comprobar("backtrack se consume al ocupar la celda exacta",
                NavArrivalPolicy.backtrackArrivalAllowed(true, true));
        comprobar("objetivo no-backtrack conserva llegada generica",
                NavArrivalPolicy.backtrackArrivalAllowed(false, false));
        comprobar("nodo de backtrack adyacente NO avanza cursor por tolerancia",
                !NavArrivalPolicy.backtrackWaypointReached(true, false, true));
        comprobar("nodo de backtrack avanza al ocupar celda exacta",
                NavArrivalPolicy.backtrackWaypointReached(true, true, false));
        comprobar("nodo normal conserva resultado de llegada existente",
                NavArrivalPolicy.backtrackWaypointReached(false, false, true));
        comprobar("escalon pendiente adyacente NO se considera alcanzado",
                !NavArrivalPolicy.waypointReached(false, false, true, true, false, false));
        comprobar("escalon pendiente fuera de tolerancia SI se reconoce si esta ocupado",
                NavArrivalPolicy.waypointReached(false, false, true, false, true, false));
        comprobar("escalon pendiente ocupado se alcanza",
                NavArrivalPolicy.waypointReached(false, false, true, true, true, false));
        comprobar("waypoint WALK normal conserva tolerancia",
                NavArrivalPolicy.waypointReached(false, false, false, true, false, false));
        comprobar("waypoint WALK fuera de tolerancia no se alcanza",
                !NavArrivalPolicy.waypointReached(false, false, false, false, false, false));
        comprobar("DROP cercano sin aterrizaje fisico no se alcanza",
                !NavArrivalPolicy.waypointReached(false, true, false, true, false, false));
        comprobar("DROP con aterrizaje valido se alcanza",
                NavArrivalPolicy.waypointReached(false, true, false, true, false, true));
        comprobar("JUMP_UP sigue esperando confirmacion del controlador",
                !NavArrivalPolicy.waypointReached(true, false, false, true, true, true));

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
