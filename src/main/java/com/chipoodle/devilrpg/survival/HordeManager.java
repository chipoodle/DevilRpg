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

    private HordeManager() {
    }

    /** Se invoca en el tick del servidor (por nivel). Programa y dispara hordas según la amenaza. */
    public static void tick(ServerLevel level) {
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

    private static void spawnHorde(ServerLevel level, double threat) {
        // El más "aventurero" (el que está más lejos de su ancla) manda para la horda que va A POR ÉL.
        ServerPlayer target = pickPlayer(level, threat);
        // PERO LA HORDA QUE VA A POR UNA ALDEA NO NECESITA QUE NADIE ESTÉ DE AVENTURA (I98): va contra el pueblo,
        // que acumula abandono, no contra el jugador. Antes, con todos los jugadores dentro de la zona protegida
        // (`pickPlayer` = null) el mundo no mandaba NADA, ni siquiera a la aldea que llevaba horas sin socorro:
        // por eso "no llegaba ningún raid al pueblo". Para elegir la aldea basta con un jugador cualquiera (de él
        // salen el ancla y el índice de objetivo).
        ServerPlayer paraLaAldea = target != null ? target : pickAnyPlayer(level);
        if (paraLaAldea == null) {
            return; // sin jugadores conectados no hay a quién contarle una horda
        }
        int currentIndex = objectiveIndexOf(paraLaAldea);
        VillageManager.Settlement settlement = currentIndex < 0
                ? null
                : VillageManager.pickHordeTarget(level, paraLaAldea, currentIndex);
        if (settlement == null && target == null) {
            return; // ni aldea a la que ir, ni jugador fuera de la zona protegida: no hay horda
        }
        double distance = target != null ? distanceToSpawn(target) : -1.0;
        // A UNA ALDEA LE LLEGA LA HORDA ENTERA (lo que escala con el tiempo es su TAMAÑO, ver `plannedCount`): si
        // se le aplicara la probabilidad por distancia al jugador, un jugador dentro de su zona protegida dejaría
        // la horda en cero y el pueblo no recibiría nada — que es justo lo que se está arreglando. Al jugador, en
        // cambio, se le sigue midiendo con la curva de siempre (cerca de su base, nada).
        double probability = settlement != null ? 1.0
                : AggressiveZombieSpawnProfile.INSTANCE.probability(distance, threat);

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
            // Hacia una aldea se spawnea justo FUERA de la valla (65–81 bloques del centro, ver L52-57); si va a por el
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
        // UNA SOLA ETIQUETA PARA LAS HORDAS DEL MUNDO (I98): antes eran `[Horda]` (a la aldea) y `[Horde]` (al
        // jugador), dos nombres casi iguales para dos cosas distintas, y buscar "qué ha pasado con las hordas" en
        // el log era una trampa. Ahora `[Horda]` y se dice a por quién va.
        if (settlement != null) {
            DevilRpg.LOGGER.info("[Horda] {} de {} enemigos van a por la aldea {} (centro {})",
                    spawned, plannedCount, settlement.objectiveIndex(), settlement.center());
            VillageManager.startWorldSiege(level, settlement, wave);
        } else {
            DevilRpg.LOGGER.info("[Horda] {} de {} enemigos van a por el jugador {} (a {} bloques de su ancla,"
                            + " probabilidad {})", spawned, plannedCount, target.getGameProfile().getName(),
                    Math.round(distance), String.format("%.2f", probability));
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
