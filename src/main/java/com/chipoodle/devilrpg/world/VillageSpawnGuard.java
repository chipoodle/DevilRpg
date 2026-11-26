package com.chipoodle.devilrpg.world;

import net.minecraft.world.entity.MobSpawnType;

/**
 * Sello absoluto de spawn: ningún monstruo puede aparecer dentro de una aldea generada y no caída,
 * sin importar si viene de vanilla, un huevo, un comando o una invocación del mod.
 */
public final class VillageSpawnGuard {

    private VillageSpawnGuard() {
    }

    /** El sello existe desde que se genera la aldea y solo desaparece al marcarla como caída. */
    public static boolean estaProtegida(boolean generada, boolean caida) {
        return generada && !caida;
    }

    public static boolean debeCancelar(boolean esMonstruo, boolean dentroDeAldeaProtegida,
                                       MobSpawnType tipo) {
        return esMonstruo && dentroDeAldeaProtegida;
    }
}
