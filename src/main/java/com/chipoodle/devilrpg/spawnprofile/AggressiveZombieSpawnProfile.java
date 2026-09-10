package com.chipoodle.devilrpg.spawnprofile;

/**
 * Perfil de spawn del {@code AggressiveZombieEntity}: el origen único de la configuración de distancia
 * y de la base/escala de atributos. Lo comparten la entidad (para escalar vida/velocidad/daño) y su
 * spawnRule (para calcular la probabilidad de spawn), sin que se acoplen entre sí.
 */
public final class AggressiveZombieSpawnProfile {

    public static final SpawnScaleProfile INSTANCE = new SpawnScaleProfile(
            67,     // minDistance: zona protegida reducida a un tercio (antes 200)
            3000,   // maxDistance: probabilidad 1 a partir de 3000 bloques
            17,     // minHardDistance: la zona protegida se encoge hasta 17 bloques a máxima amenaza
            3.7,    // maxScaleMultiplier: +350% de atributos en la distancia máxima
            9,   // baseHealth
            0.068,  // baseSpeed
            0.7,    // baseDamage
            20,     // baseXp (experiencia base, como un mob normal)
            4.5     // maxXpMultiplier: +450% de XP en la distancia máxima
    );

    private AggressiveZombieSpawnProfile() {
    }
}
