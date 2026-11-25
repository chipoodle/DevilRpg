package com.chipoodle.devilrpg.world;

import net.minecraft.world.entity.MobSpawnType;

/**
 * <b>El sello corta el spawn de hostiles dentro de una aldea protegida.</b>
 * <p>
 * La lista anterior era cerrada (`NATURAL`, `EVENT`, `REINFORCEMENT`, `SPAWNER`, ...). Cualquier tipo
 * nuevo o no listado se colaba dentro. La regla robusta es la inversa: dentro de una aldea protegida
 * se cancela <b>todo</b> hostil salvo lo que el jugador o el mod crean a propósito:
 * {@code SPAWN_EGG}, {@code COMMAND} y {@code MOB_SUMMONED}. Un tipo {@code null} se deja pasar porque
 * es el camino de {@code addFreshEntity} de mecánicas/mods y no se puede clasificar; el guardián de
 * entrada ya lo trata aparte.
 */
public final class VillageSpawnGuard {

    private VillageSpawnGuard() {
    }

    public static boolean debeCancelar(boolean esMonstruo, boolean dentroDeAldeaProtegida,
                                       MobSpawnType tipo) {
        if (!esMonstruo || !dentroDeAldeaProtegida || tipo == null) {
            return false;
        }
        return switch (tipo) {
            case SPAWN_EGG, COMMAND, MOB_SUMMONED -> false;
            default -> true;
        };
    }
}
