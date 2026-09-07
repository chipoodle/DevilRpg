package com.chipoodle.devilrpg.spawnprofile;

/**
 * Perfil de spawn del {@code AggressiveZombieEntity}: el origen único de la configuración de distancia
 * y de la base/escala de atributos. Lo comparten la entidad (para escalar vida/velocidad/daño) y su
 * spawnRule (para calcular la probabilidad de spawn), sin que se acoplen entre sí.
 */
public final class AggressiveZombieSpawnProfile {

    public static final SpawnScaleProfile INSTANCE = new SpawnScaleProfile(
            200,    // minDistance: no spawnea en los primeros 200 bloques (zona protegida)
            3000,   // maxDistance: probabilidad 1 a partir de 3000 bloques
            50,     // minHardDistance: la zona protegida se encoge hasta 50 bloques a máxima amenaza
            3.5,    // maxScaleMultiplier: +350% de atributos en la distancia máxima
            20.0,   // baseHealth (valor de un zombie normal)
            0.23,   // baseSpeed (valor de un zombie normal)
            3.0,    // baseDamage (valor de un zombie normal)
            20,     // baseXp (experiencia base, como un mob normal)
            4.0     // maxXpMultiplier: +400% de XP en la distancia máxima
    );

    private AggressiveZombieSpawnProfile() {
    }
}
