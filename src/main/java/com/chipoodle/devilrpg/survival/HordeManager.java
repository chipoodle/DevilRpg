package com.chipoodle.devilrpg.survival;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.entity.AggressiveZombieEntity;
import com.chipoodle.devilrpg.init.ModEntities;
import com.chipoodle.devilrpg.spawnprofile.AggressiveZombieSpawnProfile;
import com.chipoodle.devilrpg.world.VillageGenerator;
import com.chipoodle.devilrpg.world.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Hordas periódicas: cada cierto tiempo (que se acorta con la amenaza global) se intenta spawnear una
 * partida de enemigos cerca de un jugador.
 * <p>
 * Los zombies de la horda están <b>sometidos al {@link com.chipoodle.devilrpg.spawnprofile.SpawnScaleProfile}</b>:
 * cada uno se spawnea solo si pasa la probabilidad según la distancia del jugador a su punto de inicio.
 * Así:
 * <ul>
 *   <li>Dentro de la zona protegida (&lt; minDistance): probabilidad 0 → no spawnea ninguno.</li>
 *   <li>De minDistance a maxDistance: spawnea una fracción según la curva.</li>
 *   <li>A maxDistance o más: probabilidad 1 → spawnean todos los de la horda.</li>
 * </ul>
 * La horda ataca al jugador que esté <b>más lejos</b> de su spawn (el más "aventurero"); si todos están
 * en la zona protegida, el evento no spawnea nada (el jugador está a salvo cerca de su base).
 */
public final class HordeManager {

    /** Intervalo base entre hordas (ticks) con amenaza 0 (jugador débil al inicio -> hordas raras). */
    private static final int BASE_INTERVAL_TICKS = 20 * 60 * 20;  // 20 minutos
    /** Intervalo mínimo entre hordas (ticks) con amenaza máxima (partida avanzada -> más seguido). */
    private static final int MIN_INTERVAL_TICKS = 3 * 60 * 20;    // 3 minutos
    /** Tamaño base de la horda con amenaza 0. */
    private static final int BASE_HORDE_SIZE = 3;
    /** Enemigos extra que añade la amenaza máxima (tamaño total = BASE_HORDE_SIZE + amenaza*this). */
    private static final int MAX_EXTRA_MEMBERS = 12;

    /**
     * Distancia (desde el CENTRO de la aldea) a la que spawnean las hordas que van a por un asentamiento:
     * justo fuera de la valla ({@link VillageGenerator#FENCE_RADIUS} = 29), nunca dentro.
     */
    private static final int VILLAGE_SPAWN_MIN = VillageGenerator.FENCE_RADIUS + 3;   // 32
    private static final int VILLAGE_SPAWN_MAX = VillageGenerator.FENCE_RADIUS + 19;  // 48

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
        ServerPlayer target = pickPlayer(level, threat);
        if (target == null) {
            return; // nadie está fuera de la zona protegida -> sin horda
        }
        double distance = distanceToSpawn(target);
        if (distance < 0.0) {
            return; // sin spawn point registrado
        }
        double probability = AggressiveZombieSpawnProfile.INSTANCE.probability(distance, threat);

        // Iteración 3: ¿hay alguna aldea DESCUIDADA cerca? Entonces la horda va A POR ELLA (el mundo también
        // juega: la aldea que nadie atiende acumula presión y acaba recibiendo el ataque). Si no hay ninguna,
        // se comporta como siempre y va a por el jugador.
        int currentIndex = objectiveIndexOf(target);
        VillageManager.Settlement settlement = currentIndex < 0
                ? null
                : VillageManager.pickHordeTarget(level, target, currentIndex);

        Random random = new Random();
        int plannedCount = BASE_HORDE_SIZE + (int) Math.round(threat * MAX_EXTRA_MEMBERS);
        int spawned = 0;
        Vec3 base = settlement != null ? Vec3.atCenterOf(settlement.center()) : target.position();
        List<UUID> wave = new ArrayList<>();
        for (int i = 0; i < plannedCount; i++) {
            // Cada zombie de la horda pasa por la probabilidad del SpawnScaleProfile (distancia + amenaza).
            if (random.nextDouble() >= probability) {
                continue;
            }
            double angle = random.nextDouble() * Math.PI * 2.0D;
            // Hacia una aldea se spawnea justo FUERA de la valla (32–48 bloques del centro); si va a por el
            // jugador, alrededor suyo como siempre.
            double dist = settlement != null
                    ? VILLAGE_SPAWN_MIN + random.nextDouble() * (VILLAGE_SPAWN_MAX - VILLAGE_SPAWN_MIN)
                    : 20 + random.nextDouble() * 24;
            int x = (int) Math.floor(base.x + Math.cos(angle) * dist);
            int z = (int) Math.floor(base.z + Math.sin(angle) * dist);
            int y = settlement != null ? VillageGenerator.spawnY(level, x, z) : (int) Math.floor(base.y);
            BlockPos pos = new BlockPos(x, y, z);
            AggressiveZombieEntity zombie = ModEntities.AGGRESSIVE_ZOMBIE.get()
                    .create(level, null, pos, MobSpawnType.MOB_SUMMONED, true, true);
            if (zombie != null) {
                zombie.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
                if (settlement != null) {
                    // Marchan a la aldea con los goals que ya existían (MoveToVillageCenterGoal).
                    zombie.setVillageCenter(settlement.center());
                    zombie.setGoToCenterActive(true);
                    // Y quedan marcados con la aldea a la que van: quien les pegue queda apuntado como
                    // defensor y cobra la recompensa si la horda es rechazada.
                    zombie.setWorldSiegeIndex(settlement.objectiveIndex());
                    wave.add(zombie.getUUID());
                }
                level.addFreshEntity(zombie);
                spawned++;
            }
        }
        if (settlement != null) {
            DevilRpg.LOGGER.info("[Horda] {} de {} enemigos hacia la aldea {} (centro {})",
                    spawned, plannedCount, settlement.objectiveIndex(), settlement.center());
            VillageManager.startWorldSiege(level, settlement, wave);
        } else {
            DevilRpg.LOGGER.info("[Horde] {} spawneados de {} cerca de {} (distancia {} prob {})",
                    spawned, plannedCount, target.getGameProfile().getName(),
                    Math.round(distance), String.format("%.2f", probability));
        }
    }

    /** Índice del objetivo de progresión actual del jugador (o -1 si no hay datos). */
    private static int objectiveIndexOf(ServerPlayer player) {
        PlayerAuxiliaryCapabilityInterface aux = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
        return aux == null ? -1 : aux.getObjectiveIndex();
    }

    /**
     * Elige al jugador <b>más lejos</b> de su punto de inicio (el más "aventurero"). Si todos los
     * jugadores están dentro de la zona protegida (efectiva, que se encoge con la amenaza), devuelve
     * {@code null} (sin horda).
     */
    private static ServerPlayer pickPlayer(ServerLevel level, double threat) {
        List<? extends ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return null;
        }
        ServerPlayer best = null;
        double bestDistance = -1.0;
        for (ServerPlayer p : players) {
            double d = distanceToSpawn(p);
            if (d >= bestDistance) {
                bestDistance = d;
                best = p;
            }
        }
        double min = AggressiveZombieSpawnProfile.INSTANCE.effectiveMinDistance(threat);
        return bestDistance > min ? best : null;
    }

    /** Distancia horizontal del jugador a su punto de inicio (ancla; o el spawn si no hay ancla). */
    private static double distanceToSpawn(ServerPlayer player) {
        PlayerAuxiliaryCapabilityInterface aux = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
        if (aux == null) {
            return -1.0;
        }
        Vec3 spawn = aux.getAnchorPoint();
        if (spawn == null) {
            spawn = aux.getSpawnPoint();
        }
        if (spawn == null) {
            return -1.0;
        }
        double dx = player.getX() - spawn.x;
        double dz = player.getZ() - spawn.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
