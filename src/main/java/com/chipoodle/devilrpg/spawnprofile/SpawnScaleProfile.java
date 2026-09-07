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
 * @param maxScaleMultiplier incremento del factor de escala en la distancia máxima (ej. 1.3 = +30%)
 * @param baseHealth        vida base sobre la que se aplica el factor de escala
 * @param baseSpeed         velocidad base sobre la que se aplica el factor de escala
 * @param baseDamage        daño base sobre el que se aplica el factor de escala
 */
public record SpawnScaleProfile(
        /** Distancia por debajo de la cual la probabilidad es 0 (zona protegida / sin spawn). */
        int minDistance,
        /** Distancia a partir de la cual la probabilidad es 1 (máxima intensidad). */
        int maxDistance,
        /** Incremento del factor de escala en la distancia máxima (ej. 1.3 = +30%). */
        double maxScaleMultiplier,
        /** Vida base sobre la que se aplica el factor de escala. */
        double baseHealth,
        /** Velocidad base sobre la que se aplica el factor de escala. */
        double baseSpeed,
        /** Daño base sobre el que se aplica el factor de escala. */
        double baseDamage) {

    /**
     * Normaliza la distancia al intervalo [0, 1] dentro del rango [minDistance, maxDistance].
     * Devuelve 0 en o por debajo de minDistance y 1 en o por encima de maxDistance.
     */
    public double normalize(double distance) {
        if (distance <= minDistance) return 0.0;
        if (distance >= maxDistance) return 1.0;
        return (distance - minDistance) / (double) (maxDistance - minDistance);
    }

    /**
     * Probabilidad de spawn: 0 por debajo de minDistance, 1 por encima de maxDistance y lineal en el
     * tramo intermedio. Idéntica a la curva usada por la entidad cuando ajusta sus atributos.
     */
    public double probability(double distance) {
        return normalize(distance);
    }

    /**
     * Factor de escala de atributos a una distancia dada: 1.0 en la zona protegida y
     * {@code 1.0 + maxScaleMultiplier} en la distancia máxima.
     */
    public double scaleFactor(double distance) {
        return 1.0 + normalize(distance) * maxScaleMultiplier;
    }
}
