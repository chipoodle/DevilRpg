package com.chipoodle.devilrpg.world;

/** A2 REMEDIACIÓN 2 · prueba pura de la política de cierre del asedio del mundo. */
public final class WorldSiegePolicyTest {

    private static int fallos;

    public static void main(String[] args) {
        // Confirmada: resiste, incluso en el límite del plazo.
        comprobar("confirmada resiste", WorldSiegePolicy.decidir(true, false, false, 0L)
                == WorldSiegePolicy.Outcome.RESISTED_CONFIRMED);
        comprobar("confirmada resiste en el plazo", WorldSiegePolicy.decidir(true, true, false,
                WorldSiegePolicy.TIMEOUT_TICKS) == WorldSiegePolicy.Outcome.RESISTED_CONFIRMED);

        // Activa: antes del plazo no se toca; al plazo se cierra SIN victoria.
        comprobar("activa antes del plazo no resuelve", WorldSiegePolicy.decidir(false, true, false, 1L)
                == WorldSiegePolicy.Outcome.NONE);
        comprobar("activa en el plazo cierra sin victoria", WorldSiegePolicy.decidir(false, true, false,
                WorldSiegePolicy.TIMEOUT_TICKS) == WorldSiegePolicy.Outcome.TIMEOUT_ACTIVE);
        comprobar("activa en el plazo no paga recompensa", WorldSiegePolicy.decidir(false, true, false,
                WorldSiegePolicy.TIMEOUT_TICKS) != WorldSiegePolicy.Outcome.RESISTED_CONFIRMED);

        // Desconocida: al plazo se cierra sin victoria.
        comprobar("desconocida en el plazo cierra sin victoria", WorldSiegePolicy.decidir(false, false, false,
                WorldSiegePolicy.TIMEOUT_TICKS) == WorldSiegePolicy.Outcome.TIMEOUT_UNCONFIRMED);

        // Aldea caída manda sobre el plazo.
        comprobar("aldea caída => FALLEN", WorldSiegePolicy.decidir(false, true, true, 1L)
                == WorldSiegePolicy.Outcome.FALLEN);
        comprobar("aldea caída en el plazo sigue FALLEN", WorldSiegePolicy.decidir(false, true, true,
                WorldSiegePolicy.TIMEOUT_TICKS) == WorldSiegePolicy.Outcome.FALLEN);

        // Límite determinista.
        comprobar("justo antes del plazo no resuelve", WorldSiegePolicy.decidir(false, false, false,
                WorldSiegePolicy.TIMEOUT_TICKS - 1L) == WorldSiegePolicy.Outcome.NONE);

        if (fallos > 0) {
            System.out.println("WORLD_SIEGE_POLICY_TEST: FAIL (" + fallos + " fallos)");
            System.exit(1);
        }
        System.out.println("WORLD_SIEGE_POLICY_TEST: OK (todas las aserciones verdes)");
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
