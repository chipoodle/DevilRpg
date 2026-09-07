package com.chipoodle.devilrpg.survival;

import net.minecraft.world.level.Level;

/**
 * Nivel de amenaza global del mundo, que aumenta con el tiempo de juego transcurrido.
 * <p>
 * Es la componente "tiempo" de la escalación del mod: a mayor amenaza, más fuertes y más frecuentes
 * serán los enemigos y las hordas. (La componente "distancia" la maneja {@code SpawnScaleProfile}.)
 * <p>
 * Es puramente derivado del reloj del mundo, por lo que server y cliente calculan el mismo valor sin
 * necesidad de sincronizarlo.
 */
public final class ThreatLevel {

    /** Cuánto puede subir la dificultad por amenaza a su máximo (ej. 0.8 = +80%). */
    public static final double MAX_EXTRA_DIFFICULTY = 0.8;

    /** Ticks de juego hasta la amenaza máxima (3 h reales de juego). 20 ticks = 1 s. */
    private static final double FULL_THREAT_TICKS = 3.0 * 60 * 60 * 20;

    private ThreatLevel() {
    }

    /** Amenaza actual en [0, 1] según el tiempo de mundo transcurrido desde el inicio del mundo. */
    public static double current(Level level) {
        if (level == null) {
            return 0.0;
        }
        double frac = level.getGameTime() / FULL_THREAT_TICKS;
        return Math.max(0.0, Math.min(1.0, frac));
    }

    /**
     * Multiplicador global de dificultad (1.0 con amenaza 0 <=> 1.0 + MAX_EXTRA_DIFFICULTY con amenaza 1).
     * Se puede multiplicar por el {@code scaleFactor} de distancia para combinar distancia + tiempo.
     */
    public static double multiplier(Level level) {
        return 1.0 + current(level) * MAX_EXTRA_DIFFICULTY;
    }
}
