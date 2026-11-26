package com.chipoodle.devilrpg.world;

import net.minecraft.world.entity.MobSpawnType;

/**
 * Sello absoluto de spawn: ningún monstruo puede aparecer dentro de una aldea generada y no caída,
 * sin importar si viene de vanilla, un huevo, un comando o una invocación del mod.
 */
public final class VillageSpawnGuard {

    private VillageSpawnGuard() {
    }

    public static boolean debeCancelar(boolean esMonstruo, boolean dentroDeAldeaProtegida,
                                       MobSpawnType tipo) {
        return esMonstruo && dentroDeAldeaProtegida;
    }
}
