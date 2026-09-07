package com.chipoodle.devilrpg.survival;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Gestiona el <b>objetivo de progresión</b>: una serie de metas cada vez más lejos del punto de inicio
 * del jugador, para obligarlo a seguir avanzando y a no establecerse.
 * <p>
 * El índice del objetivo actual se guarda en la capability auxiliar (se sincroniza al cliente). Cada tick
 * se comprueba si el jugador alcanzó el objetivo; si lo logró, se avanza al siguiente (más lejos). La
 * posición del objetivo se calcula de forma determinista con {@link ObjectiveTargets}.
 */
public final class ObjectiveManager {

    /** Distancia horizontal mínima (bloques) a la que se considera alcanzado el objetivo. */
    private static final int REACH_RADIUS = ObjectiveTargets.REACH_RADIUS;

    private ObjectiveManager() {
    }

    /** Se invoca en el tick del jugador (servidor). Avanza el objetivo si fue alcanzado. */
    public static void tick(ServerPlayer player) {
        PlayerAuxiliaryCapabilityInterface aux = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
        if (aux == null) {
            return;
        }
        Vec3 spawn = aux.getSpawnPoint();
        if (spawn == null) {
            return; // sin spawn registrado -> sin objetivos
        }
        int index = aux.getObjectiveIndex();
        BlockPos target = ObjectiveTargets.targetOf(spawn, index);

        if (ObjectiveTargets.horizontalDistSqr(player.blockPosition(), target) <= (double) (REACH_RADIUS * REACH_RADIUS)) {
            aux.setObjectiveIndex(index + 1, player);
            DevilRpg.LOGGER.info("[Objective] Jugador {} alcanzó el objetivo {} -> siguiente {}", player.getGameProfile().getName(), index, index + 1);
        }
    }
}
