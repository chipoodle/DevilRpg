package com.chipoodle.devilrpg.survival;

import com.chipoodle.devilrpg.config.ConfigHolder;
import net.minecraft.world.level.Level;

/**
 * Nivel de amenaza global del mundo, que aumenta con el tiempo de juego transcurrido.
 * <p>
 * Es la componente "tiempo" de la escalación del mod: a mayor amenaza, más fuertes y más frecuentes
 * serán los enemigos y las hordas. (La componente "distancia" la maneja {@code SpawnScaleProfile}.)
 * <p>
 * <b>Se configura en {@code devilrpg-server.toml}</b>, sección {@code [threat]}:
 * <ul>
 *   <li>{@code threatMaxExtraDifficulty} (0.8 = +80%): cuánto sube la dificultad a amenaza máxima.</li>
 *   <li>{@code threatFullHours} (3): horas <b>jugadas</b> (con el mundo cargado) hasta el máximo.</li>
 * </ul>
 * Antes eran constantes en esta clase y había que recompilar para cambiarlas.
 * <p>
 * Es puramente derivado del reloj del mundo, por lo que server y cliente calculan el mismo valor sin
 * necesidad de sincronizarlo.
 */
public final class ThreatLevel {

    /** Valor por defecto de {@code threatMaxExtraDifficulty} (0.8 = +80%). */
    public static final double DEFAULT_MAX_EXTRA_DIFFICULTY = 0.8;

    /** Valor por defecto de {@code threatFullHours} (3 h jugadas). */
    public static final double DEFAULT_FULL_THREAT_HOURS = 3.0;

    /** Ticks en una hora de juego (20 ticks/s × 60 s × 60 min). */
    private static final double TICKS_PER_HOUR = 20.0 * 60.0 * 60.0;

    private ThreatLevel() {
    }

    /**
     * Cuánto sube la dificultad a amenaza máxima (0.8 = +80%), leído de la config del servidor.
     * <p>
     * Los mobs lo usan al calcular sus atributos (antes era la constante
     * {@code ThreatLevel.MAX_EXTRA_DIFFICULTY}, que ya no existe: ahora es configurable sin recompilar).
     */
    public static double maxExtraDifficulty() {
        return ConfigHolder.getThreatMaxExtraDifficulty();
    }

    /** Ticks jugados hasta la amenaza máxima, según la config ({@code threatFullHours}). */
    public static double fullThreatTicks() {
        return Math.max(1.0D, ConfigHolder.getThreatFullHours() * TICKS_PER_HOUR);
    }

    /** Amenaza actual en [0, 1] según el tiempo de mundo transcurrido desde el inicio del mundo. */
    public static double current(Level level) {
        if (level == null) {
            return 0.0;
        }
        double frac = level.getGameTime() / fullThreatTicks();
        return Math.max(0.0, Math.min(1.0, frac));
    }

    /**
     * Multiplicador global de dificultad (1.0 con amenaza 0 &lt;=&gt; 1.0 + maxExtraDifficulty con amenaza 1).
     * Se puede multiplicar por el {@code scaleFactor} de distancia para combinar distancia + tiempo.
     */
    public static double multiplier(Level level) {
        return 1.0 + current(level) * maxExtraDifficulty();
    }
}
