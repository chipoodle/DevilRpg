package com.chipoodle.devilrpg.survival;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.world.VillageManager;
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
        Vec3 spawn = aux.getAnchorPoint();
        if (spawn == null) {
            spawn = aux.getSpawnPoint(); // respaldo: si aún no hay ancla, usar el spawn del jugador
        }
        if (spawn == null) {
            return; // sin ancla/sin spawn -> sin objetivos
        }
        int index = aux.getObjectiveIndex();
        BlockPos target = ObjectiveTargets.targetOf(spawn, index);
        double distSqr = ObjectiveTargets.horizontalDistSqr(player.blockPosition(), target);

        // Pre-generar la aldea cuando el jugador se acerca (antes de llegar, para que no aparezca de golpe).
        if (distSqr <= (double) (VillageManager.PRE_GENERATE_RADIUS * VillageManager.PRE_GENERATE_RADIUS)) {
            VillageManager.preGenerate(player.serverLevel(), index, target);
            // La guarida asociada a este objetivo (foco de enemigos asaltable) tambien se pre-genera.
            LairManager.preGenerate(player.serverLevel(), index, target);
        }

        // Al llegar, se inicia el asedio (con un margen); el avance lo hace VillageManager al resolverse.
        if (distSqr <= (double) (REACH_RADIUS * REACH_RADIUS)) {
            VillageManager.start(player.serverLevel(), player, index, target);
        }
    }
}
