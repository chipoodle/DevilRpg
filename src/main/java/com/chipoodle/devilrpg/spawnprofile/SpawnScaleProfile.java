package com.chipoodle.devilrpg.spawnprofile;

/**
 * <h2>Patrón: entidad con spawnRule + atributos dinámicos según distancia</h2>
 * <p>
 * Un {@code SpawnScaleProfile} agrupa, en un único objeto neutro, toda la configuración que define cómo
 * una entidad pasa de "débil a fuerte" en función de la distancia del jugador a su punto de inicio:
 * los límites de distancia y la base/escala de sus atributos.
 * <p>
 * Es el <b>origen único de verdad</b> de esos valores: lo comparten la entidad (para aplicar el
 * escalado de vida/velocidad/daño) y su {@code SpawnRule} (para calcular la probabilidad de spawn),
 * <b>sin que se acoplen entre sí</b> — ambos dependen solo de este perfil, no el uno del otro.
 * <p>
 * <b>Cómo usarlo como patrón para una entidad nueva</b>:
 * <ol>
 *   <li>Crea un {@code SpawnScaleProfile} con los valores de la entidad (rango de distancia, base y
 *       multiplicador de escala).</li>
 *   <li>Guarda una única instancia en una clase neutra (p. ej. {@code XxxSpawnProfile}) o como
 *       {@code public static final} en la entidad.</li>
 *   <li>En la entidad, usa {@link #scaleFactor(double)} y las bases para ajustar sus atributos, y
 *       {@link #maxDistance()} para acotar la búsqueda del jugador.</li>
 *   <li>En la {@code SpawnRule}, usa {@link #probability(double)} para la probabilidad de spawn (y
 *       {@link #minDistance()}/{@link #maxDistance()} para los mensajes de log).</li>
 * </ol>
 * De este modo la curva de probabilidad y la de escalado quedan centralizadas y son idénticas.
 *
 * @param minDistance       distancia por debajo de la cual la probabilidad es 0 (zona protegida / sin spawn)
 * @param maxDistance       distancia a partir de la cual la probabilidad es 1 (máxima intensidad)
 * @param minHardDistance   a lo que se reduce la zona protegida con la amenaza máxima
 * @param maxScaleMultiplier incremento del factor de escala en la distancia máxima (ej. 1.3 = +30%)
 * @param baseHealth        vida base sobre la que se aplica el factor de escala
 * @param baseSpeed         velocidad base sobre la que se aplica el factor de escala
 * @param baseDamage        daño base sobre el que se aplica el factor de escala
 * @param baseXp            experiencia base que da la entidad (sin escalar)
 * @param maxXpMultiplier   aumento de experiencia a la distancia máxima (0 = sin aumento)
 */
public record SpawnScaleProfile(
        /** Distancia por debajo de la cual la probabilidad es 0 (zona protegida / sin spawn). */
        int minDistance,
        /** Distancia a partir de la cual la probabilidad es 1 (máxima intensidad). */
        int maxDistance,
        /** Valor al que se reduce {@code minDistance} con la amenaza máxima (la zona protegida se encoge). */
        int minHardDistance,
        /** Incremento del factor de escala en la distancia máxima (ej. 1.5 = +50%). */
        double maxScaleMultiplier,
        /** Vida base sobre la que se aplica el factor de escala. */
        double baseHealth,
        /** Velocidad base sobre la que se aplica el factor de escala. */
        double baseSpeed,
        /** Daño base sobre el que se aplica el factor de escala. */
        double baseDamage,
        /** Experiencia base que da la entidad (en la zona protegida, sin escalar). */
        int baseXp,
        /** Multiplicador extra de experiencia a la distancia máxima (0 = sin aumento). */
        double maxXpMultiplier) {

    /**
     * Distancia mínima efectiva según la amenaza (0..1). Con amenaza 0 es {@code minDistance} y con
     * amenaza 1 se reduce a {@code minHardDistance}. Así la zona protegida se encoge con el tiempo.
     */
    public double effectiveMinDistance(double threat) {
        return minDistance + (minHardDistance - minDistance) * threat;
    }

    /**
     * Normaliza la distancia al intervalo [0, 1] teniendo en cuenta el mínimo efectivo (que depende de
     * la amenaza). Devuelve 0 en o por debajo del mínimo y 1 en o por encima de maxDistance.
     */
    public double normalize(double distance, double threat) {
        double min = effectiveMinDistance(threat);
        if (distance <= min) return 0.0;
        if (distance >= maxDistance) return 1.0;
        return (distance - min) / (double) (maxDistance - min);
    }

    /** {@link #normalize(double, double)} con amenaza 0 (mínimo sin encoger). */
    public double normalize(double distance) {
        return normalize(distance, 0.0);
    }

    /**
     * Probabilidad de spawn teniendo en cuenta la amenaza (la zona protegida se encoge con el tiempo).
     * 0 en la zona protegida, 1 a maxDistance, lineal en el tramo intermedio.
     */
    public double probability(double distance, double threat) {
        return normalize(distance, threat);
    }

    /** {@link #probability(double, double)} con amenaza 0. */
    public double probability(double distance) {
        return probability(distance, 0.0);
    }

    /**
     * Factor de escala de atributos teniendo en cuenta la amenaza: 1.0 en la zona protegida (efectiva) y
     * {@code 1.0 + maxScaleMultiplier} en la distancia máxima.
     */
    public double scaleFactor(double distance, double threat) {
        return 1.0 + normalize(distance, threat) * maxScaleMultiplier;
    }

    /** {@link #scaleFactor(double, double)} con amenaza 0. */
    public double scaleFactor(double distance) {
        return scaleFactor(distance, 0.0);
    }

    /**
     * Experiencia que da la entidad según la distancia y la amenaza: {@code baseXp} en la zona protegida y
     * {@code baseXp * (1 + maxXpMultiplier)} a la distancia máxima (0 = sin aumento por distancia).
     */
    public int experienceReward(double distance, double threat) {
        return (int) Math.round(baseXp * (1.0 + normalize(distance, threat) * maxXpMultiplier));
    }

    /** {@link #experienceReward(double, double)} con amenaza 0. */
    public int experienceReward(double distance) {
        return experienceReward(distance, 0.0);
    }
}
