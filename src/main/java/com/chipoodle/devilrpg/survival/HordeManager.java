package com.chipoodle.devilrpg.survival;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.AggressiveZombieEntity;
import com.chipoodle.devilrpg.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Hordas periódicas: cada cierto tiempo (que se acorta con la amenaza global) aparece una partida de
 * enemigos cerca de un jugador, con tamaño y frecuencia crecientes según {@link ThreatLevel}.
 * <p>
 * Es un "evento" independiente del {@code CustomSpawner}; se registra en el tick del servidor junto a
 * las otros sistemas de supervivencia.
 */
public final class HordeManager {

    /** Intervalo base entre hordas (ticks) con amenaza 0 (jugador débil al inicio -> hordas raras). */
    private static final int BASE_INTERVAL_TICKS = 20 * 60 * 20;  // 20 minutos
    /** Intervalo con amenaza máxima: las hordas son más grandes/fuertes pero menos frecuentes. */
    private static final int MIN_INTERVAL_TICKS = 50 * 60 * 20;    // 50 minutos
    /** Máximo de enemigos extra que añade la amenaza máxima. */
    private static final int MAX_EXTRA_MEMBERS = 6;

    private static final Map<ServerLevel, Integer> TICKS = new HashMap<>();

    private HordeManager() {
    }

    /** Se invoca en el tick del servidor (por nivel). Programa y dispara hordas según la amenaza. */
    public static void tick(ServerLevel level) {
        double threat = ThreatLevel.current(level);
        int interval = (int) (MIN_INTERVAL_TICKS + (BASE_INTERVAL_TICKS - MIN_INTERVAL_TICKS) * (1.0 - threat));
        int elapsed = TICKS.getOrDefault(level, 0) + 1;
        if (elapsed >= interval) {
            TICKS.put(level, 0);
            spawnHorde(level, threat);
        } else {
            TICKS.put(level, elapsed);
        }
    }

    private static void spawnHorde(ServerLevel level, double threat) {
        ServerPlayer target = pickPlayer(level);
        if (target == null) {
            return;
        }
        Random random = new Random();
        int count = 1 + (int) Math.round(threat * MAX_EXTRA_MEMBERS);
        Vec3 base = target.position();
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double dist = 20 + random.nextDouble() * 24;
            BlockPos pos = new BlockPos(
                    (int) Math.floor(base.x + Math.cos(angle) * dist),
                    (int) Math.floor(base.y),
                    (int) Math.floor(base.z + Math.sin(angle) * dist));

            AggressiveZombieEntity zombie = ModEntities.AGGRESSIVE_ZOMBIE.get()
                    .create(level, null, pos, MobSpawnType.MOB_SUMMONED, true, true);
            if (zombie != null) {
                zombie.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
                level.addFreshEntity(zombie);
            }
        }
        DevilRpg.LOGGER.info("[Horde] {} enemigos cerca de {} (amenaza {})", count, target.getGameProfile().getName(), String.format("%.2f", threat));
    }

    private static ServerPlayer pickPlayer(ServerLevel level) {
        List<? extends ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return null;
        }
        return players.get(new Random().nextInt(players.size()));
    }
}
