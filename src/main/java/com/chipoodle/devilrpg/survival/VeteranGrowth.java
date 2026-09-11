package com.chipoodle.devilrpg.survival;

/**
 * <h2>Crecimiento del enemigo con el tiempo ("veteranos")</h2>
 * <p>
 * Cierra el último punto de la Iteración 2: hasta ahora el escalado por tiempo ({@link ThreatLevel}) solo se
 * aplicaba <b>al spawnear</b>, así que un enemigo que llevaba media hora vivo seguía siendo el mismo de
 * siempre. Con esto, el enemigo que <b>sobrevive</b> se vuelve más peligroso solo:
 * <ul>
 *   <li>Sube un <b>rango</b> cada {@link #TICKS_PER_RANK} ticks <b>vivo</b> (solo cuenta mientras está
 *       cargado, o sea mientras hay alguien cerca: no crece en el vacío).</li>
 *   <li>Cada <b>baja</b> que hace le adelanta {@link #TICKS_PER_KILL}: los que han matado aldeanos o
 *       esbirros crecen antes.</li>
 *   <li>Cada rango le da más vida, daño, velocidad y <b>tamaño</b> (para que se vea a simple vista), y más
 *       experiencia al morir, para que la pelea larga valga la pena.</li>
 * </ul>
 * El rango y el progreso se guardan en NBT: un veterano que te sobrevive sigue siendo veterano al volver a
 * cargar la partida. Este perfil es el <b>origen único de verdad</b> de esos números (lo comparten el zombie
 * agresivo y el vex helado) y es puramente determinista: server y cliente calculan lo mismo.
 */
public final class VeteranGrowth {

    /** Rango máximo: cuántas veces puede crecer un mismo enemigo. */
    public static final int MAX_RANK = 5;

    /** Ticks vivo y cargado por rango (2 min de juego). */
    public static final int TICKS_PER_RANK = 2 * 60 * 20;

    /** Cuánto adelanta cada baja que hace el enemigo (30 s de "vida" ganada). */
    public static final int TICKS_PER_KILL = 30 * 20;

    // Cuánto gana POR RANGO (acumulativo: rango 5 = +60% vida, +40% daño...).
    /** Vida extra por rango. */
    public static final double HEALTH_PER_RANK = 0.12;
    /** Daño extra por rango. */
    public static final double DAMAGE_PER_RANK = 0.08;
    /** Velocidad extra por rango. */
    public static final double SPEED_PER_RANK = 0.03;
    /** Tamaño extra por rango (se ve más grande; a rango 5 es un 15% más). */
    public static final double SCALE_PER_RANK = 0.03;
    /** Experiencia extra al morir por rango (rango 5 = 2.25x). */
    public static final double XP_PER_RANK = 0.25;

    private VeteranGrowth() {
    }

    /** Rango que le corresponde a un progreso (en ticks) dado. */
    public static int rankFor(int progressTicks) {
        if (progressTicks <= 0) {
            return 0;
        }
        return Math.min(MAX_RANK, progressTicks / TICKS_PER_RANK);
    }

    /** Multiplicador de un atributo para un rango (1.0 con rango 0 = sin cambios). */
    public static double multiplier(int rank, double perRank) {
        return 1.0 + clampRank(rank) * perRank;
    }

    /**
     * Nombre del rango, solo para los mensajes de log (permite seguir la progresión en {@code latest.log}
     * sin tener que adivinar qué significa "rango 3").
     */
    public static String rankName(int rank) {
        return switch (clampRank(rank)) {
            case 0 -> "novato";
            case 1 -> "curtido";
            case 2 -> "veterano";
            case 3 -> "elite";
            case 4 -> "temible";
            default -> "azote";
        };
    }

    private static int clampRank(int rank) {
        return Math.max(0, Math.min(MAX_RANK, rank));
    }
}
