package com.chipoodle.devilrpg.spawnprofile;

/**
 * Perfil de spawn del <b>vex</b> (la amenaza aérea de base, que reemplazó al zombie normal): respeta una
 * zona protegida pequeña ({@code minDistance = 67}) y spawnea de día y de noche. Sus atributos base están
 * reducidos a un tercio y su {@code maxXpMultiplier} es 450% (la XP crece mucho al alejarse del punto de
 * inicio).
 * <p>
 * {@code baseXp} subió de 5 a <b>15</b>: con 5, matar un vex apenas se notaba (uno o dos orbes diminutos) y
 * parecía que no daba experiencia.
 */
public final class VexSpawnProfile {

    public static final SpawnScaleProfile INSTANCE = new SpawnScaleProfile(
            67,      // minDistance: zona protegida pequeña (antes 0): no spawnea pegado a la base
            3000,    // maxDistance: amenaza local cerca de la base
            17,      // minHardDistance: no hay minimo que encoger
            2.5,    // maxScaleMultiplier: escala mas leve que el agresivo (+250%)
            2.5,   // baseHealth (un tercio de la base del vex)
            0.071,  // baseSpeed (un tercio de la base anterior)
            0.34,    // baseDamage (un tercio de la base anterior)
            15,     // baseXp: 5 -> 15 para que matar un vex se note (hasta 15*5.5 = 82 lejos de la base)
            4.5     // maxXpMultiplier: +450% de XP en la distancia máxima
    );

    private VexSpawnProfile() {
    }
}
