package com.chipoodle.devilrpg.entity.nav;

/**
 * A2 FASE 4 · <b>Politica pura de llegada</b>.
 *
 * <p>Separa tres cosas que antes eran una sola: llegar a un objetivo <b>tactico</b> (orilla, escape, waypoint),
 * llegar a la <b>mision</b> con etapas pendientes, y llegar a la <b>mision final</b>. Solo el tercer caso puede
 * declarar {@code ARRIVED}. Al ser pura, se prueba sin servidor y el ejecutor no puede desviarse de la regla.
 */
public final class NavArrivalPolicy {

    public enum Result {
        /** Objetivo tactico alcanzado: se retoma la mision, NUNCA ARRIVED. */
        RETOMAR_MISION,
        /** Etapa de la mision alcanzada y quedan etapas: avanzar a la siguiente. */
        AVANZAR_ETAPA,
        /** Mision final alcanzada sin etapas pendientes: unico caso de ARRIVED. */
        MISION_LLEGADA,
        /** Objetivo que no es la mision (escalon local): recalcular hacia la mision. */
        RECALCULAR
    }

    private NavArrivalPolicy() {
    }

    /**
     * The executor's early objective-proximity shortcut must not consume a pending stair waypoint.
     * Such a waypoint may be considered reached only after physical occupancy of the verified stair.
     */
    public static boolean proximityArrivalAllowed(boolean pendingStair, boolean stairPhysicallyOccupied) {
        return !pendingStair || stairPhysicallyOccupied;
    }

    /**
     * A recovery backtrack is complete only when the mob occupies the exact selected historical cell.
     * Generic distance tolerance is intentionally insufficient: clearing it early loses the one-shot
     * recovery target and can strand the executor in RECOVERING before the strategic mission resumes.
     */
    public static boolean backtrackArrivalAllowed(boolean pendingBacktrack, boolean exactCellOccupied) {
        return !pendingBacktrack || exactCellOccupied;
    }

    /**
     * Route nodes normally use their transition-specific arrival policy. A pending recovery backtrack
     * overrides that result: its route cursor must not consume the selected cell until exact occupancy,
     * otherwise the executor skips the adjacent nudge and leaves a completed route one block short.
     */
    public static boolean backtrackWaypointReached(boolean pendingBacktrack, boolean exactCellOccupied,
                                                    boolean normalWaypointReached) {
        return pendingBacktrack ? exactCellOccupied : normalWaypointReached;
    }

    /**
     * Route-node arrival gate. A pending constructed stair is not reached by proximity:
     * the entity must physically occupy the verified cell above that stair.
     */
    public static boolean waypointReached(boolean jumpUp, boolean drop, boolean pendingStair,
                                          boolean withinTolerance, boolean stairPhysicallyOccupied,
                                          boolean validDropLanding) {
        if (jumpUp) {
            return false;
        }
        if (pendingStair) {
            return stairPhysicallyOccupied;
        }
        if (!withinTolerance) {
            return false;
        }
        return !drop || validDropLanding;
    }

    /**
     * @param tactico          el objetivo activo se fijo con {@code setTacticalObjective}
     * @param objetivoEsMision el objetivo activo coincide con la mision (o su celda de pie)
     * @param quedanEtapas     la cadena de mision tiene etapas pendientes
     */
    public static Result decidir(final boolean tactico, final boolean objetivoEsMision,
                                 final boolean quedanEtapas) {
        if (tactico) {
            return Result.RETOMAR_MISION;
        }
        if (objetivoEsMision) {
            return quedanEtapas ? Result.AVANZAR_ETAPA : Result.MISION_LLEGADA;
        }
        return Result.RECALCULAR;
    }

    /** Solo la mision final declara llegada. Ni un objetivo tactico ni una etapa intermedia lo hacen. */
    public static boolean declaraArrived(final Result resultado) {
        return resultado == Result.MISION_LLEGADA;
    }

    /**
     * A2 FASE 4 · a que objetivo se vuelve tras una llegada. Una llegada tactica (o un escalon local) NO
     * sustituye la mision: se retoma {@code misionFinal}. Es la regla que impide que la orilla se convierta
     * en el destino permanente.
     */
    public static <T> T objetivoTrasLlegada(final Result resultado, final T objetivoActual,
                                            final T misionFinal) {
        if (resultado == Result.RETOMAR_MISION || resultado == Result.RECALCULAR) {
            return misionFinal;
        }
        return objetivoActual;
    }
}
