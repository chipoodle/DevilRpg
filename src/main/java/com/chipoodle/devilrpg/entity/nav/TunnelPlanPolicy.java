package com.chipoodle.devilrpg.entity.nav;

/**
 * A2 · <b>Presupuesto y dirección del túnel subordinado.</b>
 * <p>
 * El túnel no crea presupuesto nuevo: reutiliza el del actuador ({@code tunelRestante}/{@code tunelEspera}) y añade
 * topes propios, todos duros, más un enfriamiento entre planes. La dirección solo puede ser cardinal y debe acercar
 * de verdad a la misión: un plan que no reduce la distancia horizontal no se autoriza.
 */
public final class TunnelPlanPolicy {
    /** Pasos por activación: cada paso abre 2 bloques y avanza 1 celda. */
    public static final int MAX_STEPS_PER_PLAN = 3;
    /** Planes por misión (tope duro; no se reinicia con reenvíos idempotentes del mismo objetivo). */
    public static final int MAX_PLANS_PER_MISSION = 4;
    /** Pasos verificados por misión, sumando todos los planes. */
    public static final int MAX_STEPS_PER_MISSION = 6;
    /** Enfriamiento entre planes: evita encadenar túneles sin replanificar. */
    public static final int PLAN_COOLDOWN_TICKS = 60;
    /** Espera acotada del actuador de terreno (su cadencia entre bloques es de 40 ticks). */
    public static final int PHASE_WAIT_TICKS = 60;
    /** Espera acotada para que el movimiento físico ocupe {@code post}. */
    public static final int MOVE_WAIT_TICKS = 80;
    /** Progreso horizontal mínimo (bloques) que debe demostrar el plan antes de autorizarse. */
    public static final double MIN_HORIZONTAL_PROGRESS = 0.5D;

    private TunnelPlanPolicy() {
    }

    /** ¿Se puede pedir un plan nuevo ahora mismo? */
    public static boolean canRequest(long now, long nextPlanAt, int plansThisMission, int stepsThisMission) {
        return now >= nextPlanAt
                && plansThisMission < MAX_PLANS_PER_MISSION
                && stepsThisMission < MAX_STEPS_PER_MISSION;
    }

    /** Pasos que aún puede ejecutar esta activación sin agotar el tope por misión. */
    public static int stepsAvailable(int stepsThisMission) {
        int remaining = MAX_STEPS_PER_MISSION - Math.max(0, stepsThisMission);
        return Math.max(0, Math.min(MAX_STEPS_PER_PLAN, remaining));
    }

    /**
     * El escalonado TIENE PRIORIDAD sobre el túnel. Mientras la misión exija descenso y el plan de construcción
     * siga siendo viable, no se pide túnel: esa estrategia ya está validada y el túnel solo debe entrar cuando
     * no queda camino de construcción (agotado o descenso bloqueado).
     */
    public static boolean constructionHasPriority(boolean missionBelowFeet, boolean constructionExhausted,
                                                  boolean descentBlocked) {
        return missionBelowFeet && !constructionExhausted && !descentBlocked;
    }

    /**
     * Las cuatro direcciones cardinales ordenadas por reducción de distancia horizontal hacia la misión
     * (mayor progreso primero) y con desempate determinista. Nunca devuelve diagonales ni la dirección cero.
     */
    public static int[][] directionsToward(int fromX, int fromZ, int targetX, int targetZ) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        double[] progress = new double[dirs.length];
        for (int i = 0; i < dirs.length; i++) {
            progress[i] = horizontalProgress(fromX, fromZ, fromX + dirs[i][0], fromZ + dirs[i][1],
                    targetX, targetZ);
        }
        // Ordenación por inserción: 4 elementos, estable y sin asignar memoria adicional.
        for (int i = 1; i < dirs.length; i++) {
            int[] dir = dirs[i];
            double value = progress[i];
            int j = i - 1;
            while (j >= 0 && progress[j] < value) {
                dirs[j + 1] = dirs[j];
                progress[j + 1] = progress[j];
                j--;
            }
            dirs[j + 1] = dir;
            progress[j + 1] = value;
        }
        return dirs;
    }

    /** Progreso horizontal (bloques) al pasar de {@code from} a {@code post} respecto de la misión. */
    public static double horizontalProgress(int fromX, int fromZ, int postX, int postZ,
                                            int targetX, int targetZ) {
        double before = Math.hypot(targetX - (double) fromX, targetZ - (double) fromZ);
        double after = Math.hypot(targetX - (double) postX, targetZ - (double) postZ);
        return before - after;
    }
}
