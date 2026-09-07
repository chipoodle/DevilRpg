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
            50,     // minHardDistance: la zona protegida se encoge hasta 50 bloques a máxima amenaza
            1.75,    // maxScaleMultiplier: +75% de atributos en la distancia máxima
            10.0,   // baseHealth
            0.15,   // baseSpeed
            1.2     // baseDamage
    );

    private AggressiveZombieSpawnProfile() {
    }
}
