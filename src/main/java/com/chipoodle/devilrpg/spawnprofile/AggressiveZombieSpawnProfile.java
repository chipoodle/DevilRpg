package com.chipoodle.devilrpg.spawnprofile;

/**
 * Perfil de spawn del {@code AggressiveZombieEntity}: el origen único de la configuración de distancia
 * y de la base/escala de atributos. Lo comparten la entidad (para escalar vida/velocidad/daño) y su
 * spawnRule (para calcular la probabilidad de spawn), sin que se acoplen entre sí.
 */
public final class AggressiveZombieSpawnProfile {

    public static final SpawnScaleProfile INSTANCE = new SpawnScaleProfile(
            200,    // minDistance: no spawnea en los primeros 200 bloques (zona protegida)
            1500,   // maxDistance: probabilidad 1 a partir de 1500 bloques
            1.5,    // maxScaleMultiplier: +50% de atributos en la distancia máxima
            20.0,   // baseHealth
            0.15,   // baseSpeed
            2.25    // baseDamage
    );

    private AggressiveZombieSpawnProfile() {
    }
}
