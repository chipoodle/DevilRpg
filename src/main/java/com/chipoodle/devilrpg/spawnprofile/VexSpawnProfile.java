package com.chipoodle.devilrpg.spawnprofile;

/**
 * Perfil de spawn del <b>vex</b> (la amenaza aérea de base, que reemplazó al zombie normal): sin zona
 * protegida ({@code minDistance = 0}), spawnea de día y de noche. Sus atributos base están reducidos a un
 * tercio y su {@code maxXpMultiplier} es 450% (la XP crece mucho al alejarse del punto de inicio).
 */
public final class VexSpawnProfile {

    public static final SpawnScaleProfile INSTANCE = new SpawnScaleProfile(
            0,      // minDistance: sin zona protegida (spawnea desde el inicio, cerca del spawn)
            500,    // maxDistance: amenaza local cerca de la base
            0,      // minHardDistance: no hay minimo que encoger
            2.0,    // maxScaleMultiplier: escala mas leve que el agresivo (+200%)
            6.67,   // baseHealth (un tercio de la base del vex)
            0.077,  // baseSpeed (un tercio de la base anterior)
            1.0,    // baseDamage (un tercio de la base anterior)
            5,      // baseXp
            4.5     // maxXpMultiplier: +450% de XP en la distancia máxima
    );

    private VexSpawnProfile() {
    }
}
