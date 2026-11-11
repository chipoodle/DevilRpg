package com.chipoodle.devilrpg.entity.nav;

/**
 * A2 FASES 2/3 · <b>Politica pura de recuperacion acotada</b>.
 *
 * <p>Vive fuera de {@link NavExecutor} y no toca Minecraft para poder probarse con un fixture puro. El
 * ejecutor le pregunta SIEMPRE lo mismo: ¿toca reintentar, esperar, rearmar o declarar fallo terminal?
 * Asi no hay dos verdades sobre los topes y los tests ejercitan la misma decision que el juego.
 *
 * <p>Reglas:
 * <ul>
 *   <li>Presupuesto agotado CON progreso sigue siendo inconcluyente (se reintenta).</li>
 *   <li>Presupuesto agotado SIN progreso, repetido hasta el tope, es fallo terminal con motivo.</li>
 *   <li>Un {@code FAILED} se rearma como maximo N veces, con enfriamiento; un destino invalido permanente
 *       no se rearma nunca.</li>
 * </ul>
 */
public final class NavRecoveryPolicy {

    /** Que hacer en el siguiente tick. {@code rearm}/{@code terminal} son excluyentes con {@code wait}. */
    public record Decision(boolean reintentar, boolean rearmar, boolean terminal, String motivo) {
        public boolean esperar() {
            return !reintentar && !rearmar && !terminal;
        }
    }

    private NavRecoveryPolicy() {
    }

    /**
     * Decision tras un {@code hitBudget}.
     *
     * @param reintentos          reintentos acumulados SIN progreso (ya contado el actual)
     * @param maxReintentos       tope configurable (>=1)
     * @param huboProgreso        el ente se acerco de verdad al objetivo desde el ultimo reinicio
     */
    public static Decision trasPresupuesto(final int reintentos, final int maxReintentos,
                                           final boolean huboProgreso) {
        if (huboProgreso) {
            return new Decision(true, false, false, "presupuesto agotado con progreso: inconcluyente");
        }
        if (reintentos >= Math.max(1, maxReintentos)) {
            return new Decision(false, false, true,
                    "presupuesto agotado sin progreso (" + reintentos + "/" + Math.max(1, maxReintentos) + ")");
        }
        return new Decision(true, false, false, "presupuesto agotado: reintento " + reintentos + "/"
                + Math.max(1, maxReintentos));
    }

    /**
     * Decision cuando el objetivo local sigue sin ser utilizable (ni en ventana ni celda de pie).
     * Es el punto donde antes el ejecutor salia de {@code tryPlan} en CADA tick y quedaba en PLANNING para
     * siempre. Sin progreso, el numero de intentos es un tope terminal; con progreso medible, se reintenta.
     */
    public static Decision trasObjetivoInvalido(final int intentos, final int maxIntentos,
                                                final boolean huboProgreso) {
        if (huboProgreso) {
            return new Decision(true, false, false, "objetivo local invalido pero con progreso: se recalcula");
        }
        if (intentos >= Math.max(1, maxIntentos)) {
            return new Decision(false, false, true,
                    "objetivo local no utilizable sin progreso (" + intentos + "/"
                            + Math.max(1, maxIntentos) + ")");
        }
        return new Decision(true, false, false, "objetivo local no utilizable: intento " + intentos + "/"
                + Math.max(1, maxIntentos));
    }

    /**
     * Decision para un estado {@code FAILED}.
     *
     * @param rearmes         rearmes ya consumidos por esta mision
     * @param maxRearmes      tope (>=0)
     * @param ahora           tick actual
     * @param proximoRearme   tick a partir del cual se permite rearmar
     * @param destinoPosible  existe una celda de pie para la mision o el objetivo
     */
    public static Decision trasFallo(final int rearmes, final int maxRearmes, final long ahora,
                                     final long proximoRearme, final boolean destinoPosible) {
        if (!destinoPosible) {
            return new Decision(false, false, true, "destino invalido permanente: sin celda de pie");
        }
        if (rearmes >= Math.max(0, maxRearmes)) {
            return new Decision(false, false, true,
                    "rearmes agotados (" + rearmes + "/" + Math.max(0, maxRearmes) + ")");
        }
        if (ahora < proximoRearme) {
            return new Decision(false, false, false, "espera de rearme hasta " + proximoRearme);
        }
        return new Decision(false, true, false, "rearme " + (rearmes + 1) + "/" + Math.max(0, maxRearmes));
    }
}
