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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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

    /** Oleada en construcción; cada miembro conserva su sorteo y candidato durante la carga del chunk. */
    private static final Map<ServerLevel, PendingHorde> PENDING_HORDES = new HashMap<>();

    /** FULL basta para insertar una entidad; no reservamos innecesariamente un radio ENTITY_TICKING. */
    private static final TicketType<ChunkTicketKey> HORDE_CHUNK_TICKET = TicketType.create(
            "devilrpg_horde",
            Comparator.comparing(ChunkTicketKey::campaignId).thenComparingLong(ChunkTicketKey::chunkPos),
            0
    );
    private static final int MAX_CHUNK_LOAD_ATTEMPTS = 2;
    private static final long CHUNK_RETRY_DELAY_TICKS = 20L;
    private static final long CHUNK_REQUEST_TIMEOUT_TICKS = 20L * 30L;

    private record ChunkTicketKey(UUID campaignId, long chunkPos) {
    }

    private static final class HordeMember {
        private final int index;
        private final boolean eligible;
        private final int x;
        private final int z;
        private int attempts;
        private long retryAtTick;
        private ChunkRequest request;

        private HordeMember(int index, boolean eligible, int x, int z) {
            this.index = index;
            this.eligible = eligible;
            this.x = x;
            this.z = z;
        }
    }

    private static final class ChunkRequest {
        private final ChunkPos chunkPos;
        private final ChunkTicketKey ticketKey;
        private final long startedAtTick;
        private volatile boolean terminal;
        private boolean ticketReleased;

        private ChunkRequest(ChunkPos chunkPos, ChunkTicketKey ticketKey, long startedAtTick) {
            this.chunkPos = chunkPos;
            this.ticketKey = ticketKey;
            this.startedAtTick = startedAtTick;
        }
    }

    private static final class PendingHorde {
        private final UUID campaignId = UUID.randomUUID();
        private final ServerPlayer playerTarget;
        private final VillageManager.Settlement settlement;
        private final double probability;
        private final int plannedCount;
        private final Vec3 base;
        private final Random random = new Random();
        private final List<UUID> wave = new ArrayList<>();
        private final Set<UUID> defenders = new HashSet<>();
        private int nextIndex;
        private int spawned;
        private HordeMember currentMember;
        private boolean cancelled;

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
        pending.cancelled = true;
        if (pending.currentMember != null && pending.currentMember.request != null) {
            releaseChunkTicket(level, pending.currentMember.request);
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

    /**
     * Registra a un defensor que intervino mientras se está ensamblando una horda del mundo.
     * Devuelve false si no existe una PendingHorde para ese objetivo.
     */
    public static boolean registerPendingDefender(ServerLevel level, int objectiveIndex, UUID defender) {
        PendingHorde pending = PENDING_HORDES.get(level);
        if (pending == null || pending.settlement == null
                || pending.settlement.objectiveIndex() != objectiveIndex || defender == null) {
            return false;
        }
        pending.defenders.add(defender);
        return true;
    }

    /** Una baja confirmada durante PendingHorde debe quitar el UUID antes de crear el libro mayor. */
    public static void onPendingAttackerKilled(ServerLevel level, UUID attacker) {
        PendingHorde pending = PENDING_HORDES.get(level);
        if (pending != null && attacker != null) {
            pending.wave.remove(attacker);
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
     * Avanza la oleada sin pedir chunks de forma bloqueante. Las solicitudes se inician desde el common pool;
     * toda mutación de nivel, ticket, entidad y PendingHorde vuelve al hilo del servidor.
     */
    private static void tickPendingHorde(ServerLevel level) {
        PendingHorde pending = PENDING_HORDES.get(level);
        if (pending == null || pending.cancelled) {
            return;
        }

        HordeMember member = pending.currentMember;
        if (member == null) {
            if (pending.nextIndex >= pending.plannedCount) {
                finishPendingHorde(level, pending);
                return;
            }
            int index = pending.nextIndex++;
            boolean eligible = pending.random.nextDouble() < pending.probability;
            int x = 0;
            int z = 0;
            if (eligible) {
                double angle = pending.random.nextDouble() * Math.PI * 2.0D;
                double distance = pending.settlement != null
                        ? VILLAGE_SPAWN_MIN + pending.random.nextDouble() * (VILLAGE_SPAWN_MAX - VILLAGE_SPAWN_MIN)
                        : 20.0D + pending.random.nextDouble() * 24.0D;
                x = (int) Math.floor(pending.base.x + Math.cos(angle) * distance);
                z = (int) Math.floor(pending.base.z + Math.sin(angle) * distance);
            }
            member = new HordeMember(index, eligible, x, z);
            pending.currentMember = member;
            if (!eligible) {
                pending.currentMember = null;
                if (pending.nextIndex >= pending.plannedCount) {
                    finishPendingHorde(level, pending);
                }
                return;
            }
        }

        if (member.request != null) {
            ChunkRequest request = member.request;
            if (level.getGameTime() - request.startedAtTick >= CHUNK_REQUEST_TIMEOUT_TICKS) {
                releaseChunkTicket(level, request);
                member.request = null;
                scheduleMemberRetry(level, pending, member, "timeout esperando chunk");
            }
            return;
        }
        if (level.getGameTime() < member.retryAtTick) {
            return;
        }

        BlockPos column = new BlockPos(member.x, level.getMinBuildHeight(), member.z);
        if (level.hasChunkAt(column)) {
            spawnPendingMember(level, pending, member);
            return;
        }
        requestMemberChunk(level, pending, member);
    }

    private static void requestMemberChunk(ServerLevel level, PendingHorde pending, HordeMember member) {
        ChunkPos chunkPos = new ChunkPos(member.x >> 4, member.z >> 4);
        ChunkTicketKey key = new ChunkTicketKey(pending.campaignId, chunkPos.toLong());
        ChunkRequest request = new ChunkRequest(chunkPos, key, level.getGameTime());
        member.attempts++;
        member.request = request;

        // Ticket mutations belong to the server thread. Keep the FULL chunk resident until insertion/cancellation.
        level.getChunkSource().addRegionTicket(HORDE_CHUNK_TICKET, chunkPos, 0, key);
        MinecraftServer server = level.getServer();

        CompletableFuture.runAsync(() -> {
            CompletableFuture<?> chunkFuture;
            synchronized (request) {
                // Unload may win while this task is waiting in the worker queue.
                if (request.terminal) {
                    return;
                }
                chunkFuture = level.getChunkSource().getChunkFuture(
                        chunkPos.x, chunkPos.z, ChunkStatus.FULL, true);
            }
            chunkFuture.whenComplete((result, error) -> {
                boolean loaded = error == null
                        && result instanceof net.minecraft.server.level.ChunkResult<?> chunkResult
                        && chunkResult.isSuccess();
                dispatchChunkCompletion(server, level, pending, member, request, loaded, error);
            });
        }).exceptionally(error -> {
            dispatchChunkCompletion(server, level, pending, member, request, false, error);
            return null;
        });
    }

    private static void dispatchChunkCompletion(MinecraftServer server, ServerLevel level, PendingHorde pending,
                                                HordeMember member, ChunkRequest request,
                                                boolean loaded, Throwable error) {
        try {
            server.execute(() -> completeChunkRequest(level, pending, member, request, loaded, error));
        } catch (RuntimeException rejected) {
            // During shutdown onLevelUnload invalidates the request and removes its ticket.
            // Never attempt ticket or level access from this worker callback.
            DevilRpg.LOGGER.debug("[Horda] callback de chunk rechazado durante cierre: {}", rejected.toString());
        }
    }

    private static void completeChunkRequest(ServerLevel level, PendingHorde pending, HordeMember member,
                                             ChunkRequest request, boolean loaded, Throwable error) {
        if (request.terminal || pending.cancelled || PENDING_HORDES.get(level) != pending
                || pending.currentMember != member || member.request != request) {
            return;
        }
        if (loaded && level.hasChunkAt(new BlockPos(member.x, level.getMinBuildHeight(), member.z))) {
            spawnPendingMember(level, pending, member);
            return;
        }

        releaseChunkTicket(level, request);
        member.request = null;
        String reason = error == null ? "el future no entregó un chunk FULL" : error.toString();
        scheduleMemberRetry(level, pending, member, reason);
    }

    private static void scheduleMemberRetry(ServerLevel level, PendingHorde pending,
                                            HordeMember member, String reason) {
        if (pending.cancelled || PENDING_HORDES.get(level) != pending || pending.currentMember != member) {
            return;
        }
        if (member.attempts < MAX_CHUNK_LOAD_ATTEMPTS) {
            member.retryAtTick = level.getGameTime() + CHUNK_RETRY_DELAY_TICKS;
            DevilRpg.LOGGER.warn("[Horda] miembro {} campaña {} reintentará candidato ({}, {}) intento {}/{}: {}",
                    member.index, pending.campaignId, member.x, member.z, member.attempts + 1,
                    MAX_CHUNK_LOAD_ATTEMPTS, reason);
            return;
        }
        DevilRpg.LOGGER.error("[Horda] miembro {} campaña {} no pudo cargar candidato ({}, {}) tras {} intentos: {}",
                member.index, pending.campaignId, member.x, member.z, member.attempts, reason);
        pending.currentMember = null;
        if (pending.nextIndex >= pending.plannedCount) {
            finishPendingHorde(level, pending);
        }
    }

    private static void spawnPendingMember(ServerLevel level, PendingHorde pending, HordeMember member) {
        if (pending.cancelled || PENDING_HORDES.get(level) != pending || pending.currentMember != member) {
            return;
        }

        AggressiveZombieEntity zombie;
        BlockPos pos;
        try {
            // A direct spawn into an already-loaded chunk has no async request to account for.
            if (member.request == null) {
                member.attempts++;
            }
            int y = pending.settlement != null
                    ? VillageGenerator.spawnY(level, member.x, member.z)
                    : (int) Math.floor(pending.base.y);
            pos = new BlockPos(member.x, y, member.z);
            zombie = ModEntities.AGGRESSIVE_ZOMBIE.get()
                    .create(level, null, pos, MobSpawnType.MOB_SUMMONED, true, true);
            if (zombie == null) {
                failCurrentMember(level, pending, member, "la fábrica de entidad devolvió null");
                return;
            }
            zombie.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
            if (pending.settlement != null) {
                zombie.setVillageCenter(pending.settlement.center());
                zombie.setGoToCenterActive(true);
            }
            if (!level.addFreshEntity(zombie)) {
                zombie.discard();
                failCurrentMember(level, pending, member, "addFreshEntity rechazó la entidad");
                return;
            }
        } catch (RuntimeException failure) {
            failCurrentMember(level, pending, member, "excepción durante spawn: " + failure);
            return;
        }

        // addFreshEntity ya confirmó la inserción: primero cerramos el miembro y contabilizamos
        // el UUID. Ninguna operación auxiliar posterior puede dejarlo pendiente ni provocar otro spawn.
        UUID insertedId = zombie.getUUID();
        pending.spawned++;
        if (pending.settlement != null) {
            pending.wave.add(insertedId);
        }

        ChunkRequest completedRequest = member.request;
        member.request = null;
        pending.currentMember = null;

        // La liberación del ticket tampoco debe reabrir el miembro confirmado si falla el unload interno.
        if (completedRequest != null) {
            try {
                releaseChunkTicket(level, completedRequest);
            } catch (RuntimeException failure) {
                DevilRpg.LOGGER.error("[Horda] no se pudo liberar ticket tras insertar miembro {} de campaña {}: {}",
                        member.index, pending.campaignId, failure.toString());
            }
        }

        // Auditoría y registro de asedio son efectos secundarios: se aíslan individualmente.
        // El libro mayor en memoria ya refleja el spawn confirmado, aunque alguno falle.
        try {
            com.chipoodle.devilrpg.spawner.AggressiveSpawnAudit.success("horde_wave", level, zombie, pos);
        } catch (RuntimeException failure) {
            DevilRpg.LOGGER.error("[Horda] falló la auditoría del miembro ya insertado {} campaña {}: {}",
                    member.index, pending.campaignId, failure.toString());
        }
        if (pending.settlement != null) {
            try {
                zombie.setWorldSiegeIndex(pending.settlement.objectiveIndex());
            } catch (RuntimeException failure) {
                DevilRpg.LOGGER.error("[Horda] no se pudo asignar índice de asedio al miembro insertado {} campaña {}: {}",
                        member.index, pending.campaignId, failure.toString());
            }
            try {
                VillageManager.registrarPosicionDeAsediador(insertedId, pos);
            } catch (RuntimeException failure) {
                DevilRpg.LOGGER.error("[Horda] no se pudo registrar posición del miembro insertado {} campaña {}: {}",
                        member.index, pending.campaignId, failure.toString());
            }
        }

        if (pending.nextIndex >= pending.plannedCount) {
            finishPendingHorde(level, pending);
        }
    }

    private static void failCurrentMember(ServerLevel level, PendingHorde pending,
                                          HordeMember member, String reason) {
        if (member.request != null) {
            releaseChunkTicket(level, member.request);
            member.request = null;
        }
        scheduleMemberRetry(level, pending, member, reason);
    }

    private static void releaseChunkTicket(ServerLevel level, ChunkRequest request) {
        synchronized (request) {
            if (request.ticketReleased) {
                return;
            }
            request.terminal = true;
            request.ticketReleased = true;
        }
        level.getChunkSource().removeRegionTicket(
                HORDE_CHUNK_TICKET, request.chunkPos, 0, request.ticketKey);
    }

    private static void finishPendingHorde(ServerLevel level, PendingHorde pending) {
        if (pending.cancelled || PENDING_HORDES.get(level) != pending || pending.currentMember != null
                || pending.nextIndex < pending.plannedCount) {
            return;
        }
        PENDING_HORDES.remove(level);
        if (pending.settlement != null) {
            DevilRpg.LOGGER.info("[Horda] {} de {} enemigos van a por la aldea {} (centro {}); spawn escalonado",
                    pending.spawned, pending.plannedCount, pending.settlement.objectiveIndex(),
                    pending.settlement.center());
            VillageManager.startWorldSiege(level, pending.settlement, pending.wave, pending.defenders);
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
