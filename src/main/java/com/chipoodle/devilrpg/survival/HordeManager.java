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
 *   <li>De minDistance a maxSpawnDistance: spawnea una fracción según la curva.</li>
 *   <li>A maxSpawnDistance o más: probabilidad 1 → spawnean todos los de la horda.</li>
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
     * justo fuera de la valla ({@link VillageGenerator#FENCE_RADIUS} = 62), nunca dentro. Con los valores de
     * verdad (<b>65 y 81</b>: el comentario decía 32-48 porque se quedó con el `FENCE_RADIUS` viejo, que era 29).
     */
    private static final int VILLAGE_SPAWN_MIN = VillageGenerator.FENCE_RADIUS + 3;   // 65
    private static final int VILLAGE_SPAWN_MAX = VillageGenerator.FENCE_RADIUS + 19;  // 81

    /**
     * El <b>turno</b> del último roll que se disparó (nivel → {@code gameTime / intervalo}). Es lo único que se
     * guarda en memoria, y <b>no es una cuenta</b>: al cargar se apunta el turno en el que se está y se dispara
     * cuando cambia, así que el reloj sigue siendo el del mundo (ver {@link #tick}).
     */
    private static final Map<ServerLevel, Long> ULTIMO_TURNO = new HashMap<>();

    /** Oleada en construcción: se inserta como máximo un mob por tick del servidor. */
    private static final Map<ServerLevel, PendingHorde> PENDING_HORDES = new HashMap<>();

    private static final class PendingHorde {
        private final ServerPlayer playerTarget;
        private final VillageManager.Settlement settlement;
        private final double probability;
        private final int plannedCount;
        private final Vec3 base;
        private final Random random = new Random();
        private final List<UUID> wave = new ArrayList<>();
        private int nextIndex;
        private int spawned;

        private PendingHorde(ServerPlayer playerTarget, VillageManager.Settlement settlement,
                             double probability, int plannedCount, Vec3 base) {
            this.playerTarget = playerTarget;
            this.settlement = settlement;
            this.probability = probability;
            this.plannedCount = plannedCount;
            this.base = base;
        }
    }

    private HordeManager() {
    }

    /** Se invoca en el tick del servidor (por nivel). Programa y dispara hordas según la amenaza. */
    public static void tick(ServerLevel level) {
        // Una sola creación/inserción por tick: evita que toda la oleada inicialice navegación y goals
        // en el mismo frame del servidor.
        tickPendingHorde(level);
        if (PENDING_HORDES.containsKey(level)) {
            return;
        }
        double threat = ThreatLevel.current(level);
        int interval = (int) (MIN_INTERVAL_TICKS + (BASE_INTERVAL_TICKS - MIN_INTERVAL_TICKS) * (1.0 - threat));
        if (interval <= 0) {
            return;
        }
        // EL RELOJ ES EL DEL MUNDO, no un contador de "ticks desde que se abrió el juego" (I98). Con el contador en
        // memoria, CERRAR EL JUEGO ponía la cuenta a cero: el roll solo llegaba en sesiones más largas que el
        // intervalo (13-20 min) y, como además la aldea necesitaba un SEGUNDO roll para tener presión, en la
        // práctica no llegaba ninguna horda a ningún pueblo (medido en la partida del jugador: UNA sola línea de
        // horda en dos días de logs y `Pressure` vacío en el guardado).
        // Ahora el turno es `gameTime / intervalo`, o sea del reloj del mundo: sobrevive al cierre y no depende de
        // cuánto lleve abierto el juego. Se dispara cuando CAMBIA el turno (no con un módulo exacto: el intervalo
        // se mueve con la amenaza y un módulo exacto se puede quedar sin dar nunca). Lo que se pierde es el turno
        // que caiga con el mundo cerrado: no se acumulan turnos atrasados.
        long turno = level.getGameTime() / interval;
        if (ULTIMO_TURNO.getOrDefault(level, turno) == turno) {
            return; // todavía no ha cambiado el turno (la primera vez solo se apunta de dónde venimos)
        }
        ULTIMO_TURNO.put(level, turno);
        spawnHorde(level, threat);
    }

    /**
     * Libera el estado transitorio de una dimensión al descargarla. Una horda incompleta no tiene
     * libro mayor: sus entidades ya insertadas vuelven a ser zombis normales antes de olvidar la ola.
     */
    public static void onLevelUnload(ServerLevel level) {
        if (level == null) {
            return;
        }
        ULTIMO_TURNO.remove(level);
        PendingHorde pending = PENDING_HORDES.remove(level);
        if (pending == null) {
            return;
        }
        for (UUID uuid : pending.wave) {
            net.minecraft.world.entity.Entity entity = level.getEntity(uuid);
            if (entity instanceof AggressiveZombieEntity zombie) {
                zombie.setGoToCenterActive(false);
                zombie.setWorldSiegeIndex(-1);
                zombie.setVillageCenter(null);
                zombie.setGlowingTag(false);
            }
            VillageManager.olvidarPosicionDeAsediador(uuid);
        }
    }

    private static void spawnHorde(ServerLevel level, double threat) {
        ServerPlayer target = pickPlayer(level, threat);
        ServerPlayer paraLaAldea = target != null ? target : pickAnyPlayer(level);
        if (paraLaAldea == null) {
            return;
        }
        int currentIndex = objectiveIndexOf(paraLaAldea);
        VillageManager.Settlement settlement = currentIndex < 0
                ? null
                : VillageManager.pickHordeTarget(level, paraLaAldea, currentIndex);
        if (settlement == null && target == null) {
            return;
        }
        double distance = target != null ? distanceToSpawn(target) : -1.0D;
        double probability = settlement != null ? 1.0D
                : AggressiveZombieSpawnProfile.INSTANCE.probability(distance, threat);
        int plannedCount = BASE_HORDE_SIZE + (int) Math.round(threat * MAX_EXTRA_MEMBERS);
        Vec3 base = settlement != null ? Vec3.atCenterOf(settlement.center()) : target.position();
        PENDING_HORDES.put(level, new PendingHorde(target, settlement, probability, plannedCount, base));
        DevilRpg.LOGGER.info("[Horda] preparando oleada escalonada: {} enemigos previstos{}",
                plannedCount, settlement != null ? " contra la aldea " + settlement.objectiveIndex() : "");
    }

    /**
     * Avanza una oleada pendiente. Cada tick consume un índice y crea/inserta como máximo una entidad;
     * las búsquedas de IA/navegación iniciales quedan distribuidas en ticks consecutivos.
     */
    private static void tickPendingHorde(ServerLevel level) {
        PendingHorde pending = PENDING_HORDES.get(level);
        if (pending == null) {
            return;
        }
        if (pending.nextIndex < pending.plannedCount) {
            pending.nextIndex++;
            if (pending.random.nextDouble() < pending.probability) {
                double angle = pending.random.nextDouble() * Math.PI * 2.0D;
                double dist = pending.settlement != null
                        ? VILLAGE_SPAWN_MIN + pending.random.nextDouble() * (VILLAGE_SPAWN_MAX - VILLAGE_SPAWN_MIN)
                        : 20.0D + pending.random.nextDouble() * 24.0D;
                int x = (int) Math.floor(pending.base.x + Math.cos(angle) * dist);
                int z = (int) Math.floor(pending.base.z + Math.sin(angle) * dist);
                int y = pending.settlement != null ? VillageGenerator.spawnY(level, x, z)
                        : (int) Math.floor(pending.base.y);
                BlockPos pos = new BlockPos(x, y, z);
                AggressiveZombieEntity zombie = ModEntities.AGGRESSIVE_ZOMBIE.get()
                        .create(level, null, pos, MobSpawnType.MOB_SUMMONED, true, true);
                if (zombie != null) {
                    zombie.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
                    if (pending.settlement != null) {
                        zombie.setVillageCenter(pending.settlement.center());
                        zombie.setGoToCenterActive(true);
                    }
                    if (level.addFreshEntity(zombie)) {
                        com.chipoodle.devilrpg.spawner.AggressiveSpawnAudit.success("horde_wave", level, zombie, pos);
                        if (pending.settlement != null) {
                            // El UUID queda pendiente, pero NO es asediador oficial hasta que
                            // VillageManager registre el WorldSiege. Así no se persiste una marca
                            // de campaña sin libro mayor si el servidor se interrumpe a mitad de ola.
                            pending.wave.add(zombie.getUUID());
                        }
                        pending.spawned++;
                    } else {
                        zombie.discard();
                    }
                }
            }
        }
        if (pending.nextIndex < pending.plannedCount) {
            return;
        }
        PENDING_HORDES.remove(level);
        if (pending.settlement != null) {
            DevilRpg.LOGGER.info("[Horda] {} de {} enemigos van a por la aldea {} (centro {}); spawn escalonado",
                    pending.spawned, pending.plannedCount, pending.settlement.objectiveIndex(),
                    pending.settlement.center());
            VillageManager.startWorldSiege(level, pending.settlement, pending.wave);
        } else if (pending.playerTarget != null) {
            DevilRpg.LOGGER.info("[Horda] {} de {} enemigos van a por el jugador {} (a {} bloques de su ancla, probabilidad {}); spawn escalonado",
                    pending.spawned, pending.plannedCount, pending.playerTarget.getGameProfile().getName(),
                    Math.round(distanceToSpawn(pending.playerTarget)), String.format("%.2f", pending.probability));
        }
    }

    /** Índice del objetivo de progresión actual del jugador (o -1 si no hay datos). */
    private static int objectiveIndexOf(ServerPlayer player) {
        PlayerAuxiliaryCapabilityInterface aux = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
        return aux == null ? -1 : aux.getObjectiveIndex();
    }

    /**
     * Cualquier jugador conectado (para la horda que va <b>a por una aldea</b>: de él salen el ancla y el índice de
     * objetivo, pero <b>no</b> se le exige estar fuera de la zona protegida, porque esa horda no va a por él).
     */
    private static ServerPlayer pickAnyPlayer(ServerLevel level) {
        List<? extends ServerPlayer> players = level.players();
        return players.isEmpty() ? null : players.get(0);
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
