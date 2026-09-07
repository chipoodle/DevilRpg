package com.chipoodle.devilrpg.spawnprofile;

/**
 * Perfil del {@code NormalZombieEntity}: la amenaza nocturna de base. A diferencia del agresivo, este
 * perfil tiene {@code minDistance = 0}, es decir <b>sin zona protegida</b>: el zombie normal presiona al
 * jugador desde el inicio, incluso cerca del spawn. Como la entidad es "normal" (se quema al sol), la
 * presión se manifiesta sobre todo de noche.
 */
public final class NormalZombieSpawnProfile {

    public static final SpawnScaleProfile INSTANCE = new SpawnScaleProfile(
            0,      // minDistance: SIN zona protegida (spawnea desde el inicio, cerca del spawn)
            500,    // maxDistance: amenaza local cerca de la base
            0,      // minHardDistance: no hay minimo que encoger
            2.0,    // maxScaleMultiplier: escala mas leve que el agresivo (+200%)
            20.0,   // baseHealth (zombie normal)
            0.23,   // baseSpeed
            3.0,    // baseDamage
            5,      // baseXp
            2.0     // maxXpMultiplier
    );

    private NormalZombieSpawnProfile() {
    }
}
