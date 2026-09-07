package com.chipoodle.devilrpg.spawnprofile;

/**
 * Perfil de escalado por distancia para entidades que se generan con una {@code SpawnRule} propia y
 * cuyos atributos crecen de "débil a fuerte" según la distancia del jugador a su punto de inicio.
 * <p>
 * Es el origen único de verdad de los límites de distancia (y de la base/escala de atributos), compartido
 * por la entidad (para aplicar el escalado) y por su spawnRule (para calcular la probabilidad de spawn)
 * <b>sin que se acoplen entre sí</b>: ambos dependen solo de este perfil neutro.
 * <p>
 * Como patrón para futuras entidades: crea un {@code SpawnScaleProfile} para cada entidad que quieras
 * que se spawnee con una spawnRule y con atributos dinámicos, y haz que tanto la entidad como su
 * spawnRule lo lean desde la misma instancia.
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
