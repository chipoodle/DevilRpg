package com.chipoodle.devilrpg.world;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapability;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityInterface;
import com.chipoodle.devilrpg.entity.AggressiveZombieEntity;
import com.chipoodle.devilrpg.entity.goal.VillagerRepairGoal;
import com.chipoodle.devilrpg.init.ModEntities;
import com.chipoodle.devilrpg.survival.ObjectiveTargets;
import com.chipoodle.devilrpg.util.MissionRewards;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Gestor del asedio a la primera aldea. La aldea se pre-genera antes de que el jugador llegue; al llegar,
 * tras un pequeño margen para explorar, se lanza una ola de monstruos desde fuera de la valla. Si el
 * jugador la limpia a tiempo -> la aldea se salva (recompensa + avanza el objetivo); si no -> cae (avanza
 * sin recompensa).
 */
public final class VillageManager {

    /** Distancia a la que se pre-genera la aldea antes de llegar el jugador. */
    public static final int PRE_GENERATE_RADIUS = 140;
    /** Radio de llegada al objetivo (se considera "en la aldea"). */
    public static final int ARRIVE_RADIUS = 24;
    /** Radio al que se AVISA al jugador de que hay una aldea cerca (antes de llegar). */
    public static final int NOTICE_RADIUS = 100;
    /** Ticks de margen para explorar la aldea antes del asedio (90 s). */
    private static final int GRACE_TICKS = 90 * 20;
    /** Ticks extra para limpiar la ola tras el asedio (2 min). */
    private static final int SIEGE_TIMEOUT_TICKS = 2 * 60 * 20;
    /** Número base de monstruos agresivos en la ola (escala con el índice del objetivo). */
    private static final int DEFAULT_WAVE = 8;
    /** Incremento máximo de la ola por alejarse (límite: no crece infinitamente). */
    private static final int MAX_WAVE_EXTRA = 20;
    /** Zona mínima/máxima (bloques) a la que spawnea la ola, FUERA de la valla (radio 29). */
    private static final int WAVE_SPAWN_MIN = 32;
    private static final int WAVE_SPAWN_MAX = 40;
    /**
     * Radio del <b>perímetro</b> de la aldea (la valla, {@code VillageGenerator.FENCE_RADIUS}): a partir de
     * aquí se considera que un zombie del asedio <b>no ha entrado</b>.
     */
    private static final int PERIMETER_RADIUS = VillageGenerator.FENCE_RADIUS;

    // --- Salud del asentamiento (Iteración 3, paso 2) ----------------------------------------------

    /** Aldeanos que tiene una aldea sana (los que pone el generador): es el tope de la "salud". */
    public static final int VILLAGERS_FOR_FULL_HEALTH = 4;
    /** Cada cuánto se repone UN aldeano en una aldea debilitada (5 min). */
    private static final int REPOPULATE_INTERVAL_TICKS = 5 * 60 * 20;
    /**
     * Cuánta presión extra acumula la aldea por cada aldeano que le falta. Con 1 aldeano acumula el doble, y
     * vacía dos veces y media: <i>los monstruos huelen la debilidad</i>.
     */
    private static final double PRESSURE_PER_MISSING_VILLAGER = 0.5D;
    /** Cada cuánto se pasa revista a los aldeanos de una aldea (10 s). */
    private static final int VILLAGE_POLL_TICKS = 200;

    // --- Vida del asentamiento (Iteración 3, paso 4) ------------------------------------------------

    /** Comida que produce la granja en cada latido de la aldea. */
    private static final int FARM_YIELD = 8;
    /** Comida que come cada aldeano en cada latido. */
    private static final int FOOD_PER_VILLAGER = 1;
    /** Puntos de comida que da un pan (es lo que vale en vanilla). */
    private static final int FOOD_PER_BREAD = 4;
    /** Despensa máxima de la aldea. */
    private static final int MAX_FOOD = 64;
    /** Comida que cuesta que llegue un aldeano nuevo (nacer o mudarse). */
    private static final int FOOD_TO_GROW = 8;
    /** Tiempo de hambre continua (ticks) antes de que se muera un aldeano: 10 min. */
    private static final long STARVATION_DEATH_TICKS = 10L * 60L * 20L;

    // --- Obrero de la aldea (Iteración 3, A1) ------------------------------------------------------

    /** Marca (en los datos persistentes del aldeano) del que es el <b>obrero</b> de la aldea. */
    public static final String BUILDER_TAG = "DevilRpgBuilder";
    /**
     * Versión del trazado de la aldea. Se sube cuando cambia el diseño y hay que <b>arreglar las ya construidas</b>:
     * <ul>
     *   <li>1: granja con cultivos, acequia y compostador.</li>
     *   <li>2: la parcela se nivela a un solo nivel, porque antes el agua quedaba un bloque por debajo de la
     *       tierra de cultivo y los cultivos se secaban.</li>
     *   <li>3: el plano de la aldea apunta también lo que está <b>a ras de suelo</b> (composteros, suelos de las
     *       casas, base de la torre, caminos), que antes quedaba fuera y el obrero no reponía.</li>
     *   <li>4: el plano es <b>canónico</b>: lo graba el propio generador mientras construye la aldea
     *       ({@code VillageGenerator.generate} devuelve el plano), en vez de fotografiarla leyendo el mundo. Así
     *       el daño previo (o capturar la aldea en mal momento) ya no se confunde con "lo correcto".</li>
     *   <li>5: las construcciones se nivelan a la <b>mediana</b> de su huella (con la más alta quedaban subidas
     *       sobre un zócalo de tierra) y las casas llevan <b>escalón de entrada</b>.</li>
     *   <li>6: las aldeas viejas cambian sus <b>cabañas procedurales por las casas del juego</b>
     *       (`VillageGenerator.actualizarCasas`), con la marca persistida `hasNewHouses` para no rehacer dos veces
     *       una casa ya nueva.</li>
     *   <li>7: cuarta casa (la "grande", con <b>cama extra</b>) y reparación de la <b>tierra pisoteada</b>. La
     *       versión de casas (`CURRENT_HOUSES`) decide si hay que rehacer las tres viejas o solo añadir la cuarta.</li>
     *   <li>8: las construcciones se nivelan al <b>terreno de alrededor</b> (antes, a la mediana de su propia
     *       huella: si el solar venía alto, la casa quedaba sobre un zócalo), la <b>iglesia</b> del juego sustituye
     *       a la torre procedural y la aldea pasa a <b>4 aldeanos</b>.</li>
     *   <li>9: el suelo de las casas va en el bloque de superficie (antes uno más arriba, así que la casa quedaba
     *       <b>un bloque por encima</b> del patio) y los caminos ya <b>no se dibujan sobre los tejados</b>.</li>
     *   <li>10: el nivel de referencia pasa a ser el <b>mínimo del patio inmediato</b> (anillo a 2 bloques) en vez
     *       de la mediana de un anillo a 4: medido en partida, las 6 puertas de la aldea quedaban a 74 con el patio
     *       a 73, o sea <b>un bloque por encima</b>. Con el mínimo, la construcción queda a ras o algo metida en el
     *       lado alto del terreno, nunca por encima.</li>
     *   <li>11: se vuelve a la <b>mediana del patio inmediato</b> (el mínimo dejaba todas las casas un bloque
     *       hundidas) y el desnivel se salva con el <b>escalón de entrada</b>, como pidió el jugador. Además el
     *       plano capturado por <b>escaneo</b> (aldeas migradas) ya incluye la <b>tierra de cultivo y el agua</b> de
     *       la granja: antes las saltaba, así que el obrero no reponía las parcelas pisoteadas.</li>
     * </ul>
     */
    public static final int CURRENT_LAYOUT = 11;

    /**
     * Versión de las <b>casas</b> que debe tener una aldea: 0 = cabañas procedurales (partidas viejas),
     * 1 = las tres casas del juego, 2 = las cuatro (la última es la "grande", con cama extra), 3 = además la
     * <b>iglesia</b> en el sitio de la vieja torre, 4 = casas con el <b>suelo a ras</b> del patio (antes iban un
     * bloque altas), 5 = con el nivel de referencia corregido (mínimo del patio), 6 = con la <b>mediana del
     * patio inmediato</b> y escalón para el desnivel (el mínimo las dejaba hundidas). Se sube cuando cambia el
     * número, el tipo o la <b>altura</b> de las construcciones, y la migración solo hace lo que falte (rehacer una
     * casa borra lo que tenga dentro).
     */
    public static final int CURRENT_HOUSES = 6;
    /** Radio alrededor del obrero en el que se buscan huecos que reponer. */
    private static final double REPAIR_SEARCH_RADIUS = 40.0D;
    /** Cuánto puede estar el hueco por encima / por debajo del obrero para que intente alcanzarlo. */
    private static final int REPAIR_MAX_UP = 5;
    private static final int REPAIR_MAX_DOWN = 6;
    /** Cuántos obreros puede tener una aldea a la vez (se reparten los huecos). */
    private static final int MAX_BUILDERS = 3;
    /** Si un obrero se queda atascado con un hueco, la reserva caduca y otro puede cogerlo. */
    private static final long CLAIM_TIMEOUT_TICKS = 60L * 20L;
    /**
     * Reservas de huecos: posición comprimida → (obrero que lo tiene, tick en que lo cogió). Es lo que evita que
     * varios obreros vayan al MISMO agujero cuando hay más de uno trabajando.
     */
    private static final Map<ServerLevel, Map<Long, Reclamo>> CLAIMS = new HashMap<>();

    private record Reclamo(UUID builder, long tick) {
    }
    /** Etiqueta de los datos persistentes del aldeano con su fecha de nacimiento. */
    private static final String BORN_TAG = "DevilRpgVillagerBorn";
    /** A partir de esta edad (2 días de juego) el aldeano es viejo y va más lento. */
    private static final long VILLAGER_OLD_AGE_TICKS = 2L * 24000L;
    /** Al llegar aquí (3 días de juego) el aldeano muere de viejo y deja el relevo a los jóvenes. */
    private static final long VILLAGER_LIFESPAN_TICKS = 3L * 24000L;

    private static final Map<ServerLevel, List<VillageDefense>> DEFENSES = new HashMap<>();

    private VillageManager() {
    }

    /**
     * Avisa al jugador de que hay una aldea cerca (una única vez por jugador y objetivo): mensaje en
     * pantalla y sonido de campana lejana. Se dispara al entrar en {@link #NOTICE_RADIUS} bloques.
     * El aviso se guarda, para no repetirlo en cada partida.
     */
    public static void noticeIfNear(ServerLevel level, ServerPlayer player, int objectiveIndex, BlockPos target) {
        VillageSavedData saved = VillageSavedData.get(level);
        if (saved.isNoticed(objectiveIndex, player.getUUID())) {
            return;
        }
        saved.markNoticed(objectiveIndex, player.getUUID());
        player.displayClientMessage(Component.literal(
                "Divisas una aldea a lo lejos... la campana llama, y algo se agita en la oscuridad."), false);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BELL_BLOCK, SoundSource.AMBIENT, 1.0F, 1.0F);
    }

    /**
     * Pre-genera la aldea (cabañas + aldeanos + valla) en el punto del objetivo, si aún no existe.
     * <p>
     * La marca de "ya generada" es <b>persistente</b>: antes vivía en memoria y, al reiniciar la partida, la
     * aldea se volvía a generar <b>encima</b> de la que ya había (nivelaba el terreno, despejaba vegetación y
     * reconstruía cabañas y valla, cargándose lo que hubieras construido cerca).
     */
    public static void preGenerate(ServerLevel level, int objectiveIndex, BlockPos target) {
        VillageSavedData saved = VillageSavedData.get(level);
        if (saved.isGenerated(objectiveIndex)) {
            return;
        }
        // `generate` devuelve el PLANO CANÓNICO de la aldea (lo que el generador colocó mientras construía), así
        // que se guarda aquí mismo, con la aldea recién hecha: el obrero sabrá qué reponer sin depender de en qué
        // estado se encontrara la aldea después.
        VillageSavedData.Blueprint plano = VillageGenerator.generate(level, target);
        saved.markGenerated(objectiveIndex);
        if (plano != null) {
            saved.setBlueprint(objectiveIndex, plano);
            saved.setLayout(objectiveIndex, CURRENT_LAYOUT);
            // Esta aldea nace ya con las casas del juego (las cuatro): que la migración no las vuelva a construir.
            saved.setCasasVersion(objectiveIndex, CURRENT_HOUSES);
            DevilRpg.LOGGER.info("[Village] Aldea {} pre-generada en {} (plano de {} bloques)",
                    objectiveIndex, target, plano.size());
        } else {
            DevilRpg.LOGGER.info("[Village] Aldea {} pre-generada en {}", objectiveIndex, target);
        }
    }

    /**
     * Gestiona también las aldeas de objetivos <b>ya superados</b> que el jugador tenga cerca, no solo la del
     * objetivo actual.
     * <p>
     * Antes, en cuanto avanzabas de objetivo la aldea anterior dejaba de gestionarse: si volvías a ella (o
     * pasabas cerca), no se pre-generaba, no avisaba y no se podía asediar — se quedaba congelada, y con el
     * tiempo eso dejaba aldeas "muertas" por el mundo. Ahora, cualquier aldea a menos de
     * {@link #PRE_GENERATE_RADIUS} bloques del jugador se pre-genera, avisa y puede asediarse; los tres pasos
     * son idempotentes (pre-generado, avisado y resuelto se guardan en {@link VillageSavedData}), así que
     * llamarlo cada tick no repite nada.
     */
    public static void manageNearby(ServerLevel level, ServerPlayer player, Vec3 anchor, int currentIndex) {
        VillageSavedData saved = VillageSavedData.get(level);
        BlockPos playerPos = player.blockPosition();
        for (int i = 0; i <= currentIndex; i++) {
            BlockPos target = ObjectiveTargets.targetOf(anchor, i);
            double distSqr = ObjectiveTargets.horizontalDistSqr(playerPos, target);
            if (distSqr > (double) PRE_GENERATE_RADIUS * PRE_GENERATE_RADIUS) {
                continue;
            }
            if (!saved.isGenerated(i)) {
                preGenerate(level, i, target);
            }
            noticeIfNear(level, player, i, target);
            if (distSqr <= (double) ARRIVE_RADIUS * ARRIVE_RADIUS) {
                start(level, player, i, target);
            }
            // SALUD DE LA ALDEA (Iteración 3): se cuenta a los aldeanos vivos y la aldea se recupera DE A POCO.
            // Antes solo se repoblaba la aldea VACÍA, y de golpe (3 aldeanos + golem): ahora una aldea
            // debilitada repone un aldeano cada REPOPULATE_INTERVAL_TICKS, y una vacía se rehace entera de una
            // vez para que no quede muerta si el jugador llega justo después de una masacre. Va AQUÍ y no en
            // start() porque start() sale antes si el asedio de ese objetivo ya se resolvió (aldea "salvada"),
            // y entonces la aldea se quedaba vacía para siempre: es el caso que reportó el jugador.
            // No se toca si la aldea ya cayó (isFallen: la derrota es definitiva) ni si hay asedio en curso
            // (si no, repoblaríamos mientras los monstruos la están matando).
            if (level.getGameTime() % VILLAGE_POLL_TICKS == 0L && !saved.isFallen(i) && !isUnderAttack(level, i)) {
                int vivos = observeVillagers(level, saved, i, target);
                if (vivos > 0) {
                    tickVillageLife(level, saved, i, target, vivos);
                }
                if (vivos == 0) {
                    VillageGenerator.spawnVillagers(level, target);
                    saved.markRepopulated(i, level.getGameTime());
                    DevilRpg.LOGGER.info("[Village] Aldea {} estaba vacia: aldeanos y golem repuestos", i);
                } else if (vivos > 0 && vivos < VILLAGERS_FOR_FULL_HEALTH
                        && level.getGameTime() - saved.getRepopulatedAt(i) >= REPOPULATE_INTERVAL_TICKS
                        && saved.getFood(i) >= FOOD_TO_GROW) {
                    // Crecer cuesta comida: una aldea hambrienta no se recupera hasta que la granja produzca.
                    // El que llega nace CRÍA (crece sola, mecánica vanilla): así se ve el relevo generacional.
                    VillageGenerator.spawnOneVillager(level, target, vivos, true);
                    saved.setFood(i, saved.getFood(i) - FOOD_TO_GROW);
                    saved.markRepopulated(i, level.getGameTime());
                    DevilRpg.LOGGER.info("[Village] Aldea {} se recupera: aldeano {}/{} (comida {})",
                            i, vivos + 1, VILLAGERS_FOR_FULL_HEALTH, saved.getFood(i));
                }
            }
        }
    }

    /** Inicia el asedio al llegar el jugador a la aldea (con un margen antes de la ola). */
    public static void start(ServerLevel level, ServerPlayer player, int objectiveIndex, BlockPos target) {
        // No re-lanzar si ya hay un asedio activo para este jugador/objetivo.
        for (VillageDefense d : DEFENSES.getOrDefault(level, List.of())) {
            if (d.playerUUID.equals(player.getUUID()) && d.objectiveIndex == objectiveIndex) {
                return;
            }
        }
        // Ni si el mundo ya la está atacando por su cuenta (horda dirigida a esta aldea): no se apilan dos
        // oleadas sobre la misma aldea.
        if (isUnderWorldSiege(level, objectiveIndex)) {
            return;
        }
        // Ni si el asedio de este objetivo ya se resolvió (salvada o caída): se guarda, así que al reiniciar
        // la partida no se puede repetir la recompensa ni volver a asediar la misma aldea.
        VillageSavedData saved = VillageSavedData.get(level);
        if (saved.isSiegeResolved(objectiveIndex)) {
            return;
        }
        if (!saved.isGenerated(objectiveIndex)) {
            preGenerate(level, objectiveIndex, target);
        }
        DEFENSES.computeIfAbsent(level, l -> new ArrayList<>())
                .add(new VillageDefense(objectiveIndex, player.getUUID(), target));
        player.displayClientMessage(Component.literal("Llegaste a la aldea... los monstruos se acercan."), false);
    }

    /** Se llama en el tick del servidor: gestiona el margen, la ola y la resolución del asedio. */
    public static void tick(ServerLevel level) {
        // Hordas que el MUNDO manda contra una aldea (Iteración 3): se resuelven aparte de los asedios que
        // arranca el jugador al llegar.
        tickWorldSieges(level);

        List<VillageDefense> list = DEFENSES.get(level);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (int i = list.size() - 1; i >= 0; i--) {
            VillageDefense d = list.get(i);
            d.tickTicks++;

            // Tras el margen de exploración, lanza la ola desde FUERA de la valla.
            if (!d.waveSpawned && d.tickTicks >= GRACE_TICKS) {
                spawnWave(level, d);
                d.waveSpawned = true;
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(d.playerUUID);
                if (p != null) {
                    p.displayClientMessage(Component.literal("¡Defiende la aldea de los monstruos!"), false);
                }
            }

            if (d.waveSpawned) {
                boolean waveCleared = isWaveCleared(level, d.wave);
                boolean timeout = d.tickTicks > GRACE_TICKS + SIEGE_TIMEOUT_TICKS;
                if (waveCleared || timeout) {
                    // Al acabarse el tiempo, si queda algún zombie FUERA del perímetro (sin pasar los muros) se
                    // considera que el asedio FRACASÓ y la aldea se salva. Sin esta regla, unos pocos zombies
                    // escondidos que nunca llegan al centro (y por tanto no se pueden matar) hacían caer la
                    // aldea sin que el jugador pudiera evitarlo: si no llegan, no asedian, no pueden ganar.
                    boolean siegeFailed = timeout && !waveCleared && !allZombiesInsidePerimeter(level, d);
                    ServerPlayer player = level.getServer().getPlayerList().getPlayer(d.playerUUID);
                    if (player != null) {
                        PlayerAuxiliaryCapabilityInterface aux = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
                        // El objetivo solo avanza si es el objetivo ACTUAL: desde la Iteración 3 también se
                        // pueden asediar aldeas de objetivos ya superados (siguen vivas y gestionadas), y
                        // avanzar el índice con uno viejo haría RETROCEDER al jugador.
                        boolean isCurrentObjective = aux != null && aux.getObjectiveIndex() == d.objectiveIndex;
                        if (waveCleared) {
                            if (d.wave.isEmpty()) {
                                grantReward(player, d.objectiveIndex);
                                player.displayClientMessage(Component.literal(isCurrentObjective
                                        ? "¡Has salvado la aldea! El objetivo avanza."
                                        : "¡Has salvado la aldea!"), false);
                            } else {
                                // La ola se dio por limpia porque los atacantes dejaron de estar cargados (el
                                // jugador se alejó y se descargaron los chunks), no porque los matara: la aldea
                                // se salva igual, pero NO hay recompensa. Misma regla que en las hordas del mundo.
                                DevilRpg.LOGGER.info("[Village] Aldea {} salvada sin limpiar la horda ({} atacantes "
                                                + "sin confirmar): sin recompensa", d.objectiveIndex, d.wave.size());
                                player.displayClientMessage(Component.literal(isCurrentObjective
                                        ? "Los monstruos se dispersaron: la aldea está a salvo. El objetivo avanza."
                                        : "Los monstruos se dispersaron: la aldea está a salvo."), false);
                            }
                        } else if (siegeFailed) {
                            grantReward(player, d.objectiveIndex);
                            player.displayClientMessage(Component.literal(isCurrentObjective
                                    ? "Los monstruos no lograron entrar: ¡la aldea está a salvo! El objetivo avanza."
                                    : "Los monstruos no lograron entrar: ¡la aldea está a salvo!"), false);
                        } else {
                            // La aldea ha caído de verdad (los monstruos entraron y sobrevivieron al tiempo):
                            // se marca caída (definitiva) y queda en ruinas. Antes solo se avisaba por chat y la
                            // aldea seguía "viva", así que el gestor la repoblaba más tarde como si nada.
                            fallVillage(level, VillageSavedData.get(level), d.objectiveIndex, d.center);
                            player.displayClientMessage(Component.literal(isCurrentObjective
                                    ? "La aldea cayó... El objetivo avanza."
                                    : "La aldea cayó..."), false);
                        }
                        if (isCurrentObjective) {
                            aux.setObjectiveIndex(d.objectiveIndex + 1, player);
                        }
                    }
                    // Los que queden vivos dejan de asediar (no convergen al centro): los que no llegaron a
                    // entrar se quedan por el mundo como zombies agresivos normales, con el escalado por
                    // distancia que ya traen de su spawn.
                    disableGoToCenter(level, d.wave);
                    // Se guarda que este asedio ya se resolvió: no se relanza al reiniciar la partida.
                    VillageSavedData.get(level).markSiegeResolved(d.objectiveIndex);
                    list.remove(i);
                }
            }
        }
    }

    private static void spawnWave(ServerLevel level, VillageDefense d) {
        Random random = new Random();
        // La ola crece al alejarse del ancla, pero con un LÍMITE: no se extiende infinitamente.
        int count = DEFAULT_WAVE + Math.min(d.objectiveIndex * 2, MAX_WAVE_EXTRA);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            // FUERA de la valla (radio 29): spawnea entre 32 y 40 bloques del centro.
            int dist = WAVE_SPAWN_MIN + random.nextInt(WAVE_SPAWN_MAX - WAVE_SPAWN_MIN);
            int x = (int) Math.round(d.center.getX() + Math.cos(angle) * dist);
            int z = (int) Math.round(d.center.getZ() + Math.sin(angle) * dist);
            int y = VillageGenerator.spawnY(level, x, z);
            AggressiveZombieEntity zombie = ModEntities.AGGRESSIVE_ZOMBIE.get()
                    .create(level, null, new BlockPos(x, y, z), MobSpawnType.MOB_SUMMONED, true, true);
            if (zombie != null) {
                zombie.moveTo(x + 0.5D, y, z + 0.5D, 0.0F, 0.0F);
                zombie.setVillageCenter(d.center); // para que converja hacia la aldea si no ataca
                // Marcado con la aldea: al morir se descuenta de la ola, y así la recompensa solo se paga si
                // de verdad se limpió la horda (no si se dispersó al descargarse los chunks).
                zombie.setWorldSiegeIndex(d.objectiveIndex);
                level.addFreshEntity(zombie);
                d.wave.add(zombie.getUUID());
            }
        }
    }

    private static boolean isWaveCleared(ServerLevel level, List<UUID> wave) {
        for (UUID uuid : wave) {
            net.minecraft.world.entity.Entity e = level.getEntity(uuid);
            if (e != null && e.isAlive()) {
                return false;
            }
        }
        return true;
    }

    /**
     * ¿Están <b>todos</b> los zombies vivos de la ola dentro del perímetro de la aldea (pasados los muros)?
     * Se usa al agotarse el tiempo: si alguno se quedó fuera, el asedio fracasó y la aldea se salva.
     */
    private static boolean allZombiesInsidePerimeter(ServerLevel level, VillageDefense d) {
        double perimeterSqr = (double) PERIMETER_RADIUS * PERIMETER_RADIUS;
        for (UUID uuid : d.wave) {
            net.minecraft.world.entity.Entity e = level.getEntity(uuid);
            if (e == null || !e.isAlive()) {
                continue;
            }
            if (e.distanceToSqr(d.center.getX() + 0.5D, d.center.getY() + 0.5D, d.center.getZ() + 0.5D) > perimeterSqr) {
                return false; // éste no llegó a entrar
            }
        }
        return true;
    }

    /** Desactiva el goal de converger al centro en los zombies vivos de la ola (cuando la aldea cayó). */
    private static void disableGoToCenter(ServerLevel level, List<UUID> wave) {
        for (UUID uuid : wave) {
            net.minecraft.world.entity.Entity e = level.getEntity(uuid);
            if (e instanceof AggressiveZombieEntity zombie) {
                zombie.setGoToCenterActive(false);
            }
        }
    }

    /**
     * Niveles de experiencia vanilla que paga salvar una aldea. La recompensa va <b>en experiencia</b>: subir
     * un nivel dispara el flujo de siempre del mod ({@code PlayerXpEvent.LevelChange} →
     * {@code setCurrentLevel}, que da <b>1 punto de habilidad por nivel</b>), así que el punto llega con la
     * experiencia de verdad en vez de regalarse suelto. Antes se regalaban 3 puntos directos con
     * {@code addUnspentPoints}, que no subían nada la experiencia.
     */
    private static final int REWARD_EXPERIENCE_LEVELS = 1;

    /**
     * Recompensa por salvar la aldea: materiales, un libro y <b>1 nivel de experiencia</b> (que trae su punto
     * de habilidad por el camino normal). No se llama si la aldea cae.
     */
    private static void grantReward(ServerPlayer player, int objectiveIndex) {
        player.addItem(new ItemStack(Items.IRON_INGOT, 8));
        player.addItem(new ItemStack(Items.LEATHER, 6));
        player.addItem(new ItemStack(Items.WRITTEN_BOOK)); // receta (por ahora un libro genérico)

        int puntosGanados = MissionRewards.giveExperienceLevels(player, REWARD_EXPERIENCE_LEVELS);
        String premio = MissionRewards.describe(REWARD_EXPERIENCE_LEVELS, puntosGanados);
        player.displayClientMessage(Component.literal("La aldea te lo agradece: " + premio + "."), false);
        PlayerExperienceCapabilityInterface expCap =
                IGenericCapability.getUnwrappedPlayerCapability(player, PlayerExperienceCapability.INSTANCE);
        DevilRpg.LOGGER.info("[Village] Aldea {} salvada: {} (quedan {} puntos)",
                objectiveIndex, premio, expCap != null ? expCap.getUnspentPoints() : -1);
    }

    // --- Iteración 3: el mundo también juega (hordas que van a por una aldea) -------------------------

    /** Aldea objetivo de una horda: su índice de objetivo y el centro (la posición del objetivo). */
    public record Settlement(int objectiveIndex, BlockPos center) {
    }

    /** Radio (respecto al jugador) en el que se buscan aldeas a las que mandar una horda. */
    private static final double HORDE_TARGET_RADIUS = 220.0D;
    /** Presión (ticks de abandono) a partir de la cual una aldea empieza a ser objetivo de las hordas. */
    private static final int PRESSURE_MIN_TICKS = 8 * 60 * 20;   // 8 min de juego
    /** Radio alrededor del centro donde se cuentan los aldeanos para decidir si la aldea ha caído. */
    private static final double FALLEN_CHECK_RADIUS = 48.0D;
    /** Radio al que se avisa a los jugadores de lo que pasa en una aldea. */
    private static final double SIEGE_WARN_RADIUS = 160.0D;
    /**
     * Niveles de experiencia que paga rechazar una horda del mundo (la que el mundo manda a por una aldea sin
     * que el jugador la provoque). Igual que salvar la aldea en el asedio clásico: 1 nivel, y su punto de
     * habilidad llega por el camino normal. Se paga <b>solo a quien participó</b> (le pegó a algún enemigo de
     * esa horda), así que si la aldea se defiende sola no cobra nadie.
     */
    private static final int WORLD_SIEGE_REWARD_EXPERIENCE_LEVELS = 1;
    /** Asedios dirigidos por el mundo (en curso). Como {@link #DEFENSES}, no se persisten. */
    private static final Map<ServerLevel, List<WorldSiege>> WORLD_SIEGES = new HashMap<>();

    /**
     * Elige la aldea que debe atacar una horda: la <b>más descuidada</b> (mayor presión) de las que están a
     * menos de {@link #HORDE_TARGET_RADIUS} del jugador, ya generadas, que no hayan caído y que no estén ya
     * bajo ataque. Devuelve {@code null} si no hay ninguna (entonces la horda va a por el jugador, como antes).
     * <p>
     * La presión se acumula aquí mismo: son ticks de juego que la aldea lleva sin que nadie la atienda, así
     * que avanza aunque el chunk esté descargado.
     */
    public static Settlement pickHordeTarget(ServerLevel level, ServerPlayer player, int currentIndex) {
        Vec3 anchor = anchorOf(player);
        if (anchor == null) {
            return null;
        }
        VillageSavedData saved = VillageSavedData.get(level);
        long gameTime = level.getGameTime();
        BlockPos playerPos = player.blockPosition();
        Settlement best = null;
        int bestPressure = 0;
        for (int i = 0; i <= currentIndex; i++) {
            if (saved.isFallen(i) || !saved.isGenerated(i) || isUnderAttack(level, i)) {
                continue;
            }
            BlockPos target = ObjectiveTargets.targetOf(anchor, i);
            if (ObjectiveTargets.horizontalDistSqr(playerPos, target) > HORDE_TARGET_RADIUS * HORDE_TARGET_RADIUS) {
                continue;
            }
            int pressure = saved.accruePressure(i, gameTime, pressureMultiplier(saved.getHealth(i)));
            if (pressure < PRESSURE_MIN_TICKS || pressure <= bestPressure) {
                continue;
            }
            bestPressure = pressure;
            best = new Settlement(i, target);
        }
        if (best != null) {
            DevilRpg.LOGGER.info("[Village] Horda dirigida a la aldea {} (presión {} min sin atención)",
                    best.objectiveIndex(), Math.round(bestPressure / 1200.0D));
        }
        return best;
    }

    /**
     * Registra una horda del mundo que va a por una aldea. La resuelve {@link #tickWorldSieges}: si los
     * enemigos caen, la aldea resiste (y su presión vuelve a cero); si se queda sin aldeanos, la aldea cae.
     */
    public static void startWorldSiege(ServerLevel level, Settlement settlement, List<UUID> wave) {
        if (wave.isEmpty()) {
            return;
        }
        WORLD_SIEGES.computeIfAbsent(level, l -> new ArrayList<>())
                .add(new WorldSiege(settlement.objectiveIndex(), settlement.center(), wave));
        DevilRpg.LOGGER.info("[Village] La aldea {} está siendo atacada: {} enemigos marchan a por ella",
                settlement.objectiveIndex(), wave.size());
        announceNearby(level, settlement.center(),
                "Los tambores suenan: los monstruos marchan contra una aldea cercana.");
    }

    /** Resuelve los asedios del mundo: aldea resiste (se reinicia su presión) o cae (sin aldeanos). */
    private static void tickWorldSieges(ServerLevel level) {
        List<WorldSiege> list = WORLD_SIEGES.get(level);
        if (list == null || list.isEmpty()) {
            return;
        }
        VillageSavedData saved = VillageSavedData.get(level);
        for (int i = list.size() - 1; i >= 0; i--) {
            WorldSiege siege = list.get(i);
            if (isWaveCleared(level, siege.wave)) {
                saved.resetPressure(siege.objectiveIndex);
                DevilRpg.LOGGER.info("[Village] La aldea {} resistió el ataque (presión reiniciada)",
                        siege.objectiveIndex);
                announceNearby(level, siege.center, "La aldea ha resistido: los monstruos han sido rechazados.");
                // Recompensa para quien la defendió (ver registerDefender).
                rewardWorldSiegeDefenders(level, siege);
                list.remove(i);
                continue;
            }
            int vivos = observeVillagers(level, saved, siege.objectiveIndex, siege.center);
            if (vivos == 0) {
                fallVillage(level, saved, siege.objectiveIndex, siege.center);
                announceNearby(level, siege.center, "La aldea ha caído: no queda nadie con vida entre sus muros.");
                // El objetivo avanza para quien lo tuviera pendiente: esa aldea ya no se puede salvar. Se
                // marca como resuelta para que no se lance además el asedio clásico al llegar.
                for (ServerPlayer p : level.players()) {
                    PlayerAuxiliaryCapabilityInterface aux = IGenericCapability.getUnwrappedPlayerCapability(p, PlayerAuxiliaryCapability.INSTANCE);
                    if (aux != null && aux.getObjectiveIndex() == siege.objectiveIndex) {
                        p.displayClientMessage(Component.literal(
                                "La aldea del objetivo ha caído... El objetivo avanza."), false);
                        aux.setObjectiveIndex(siege.objectiveIndex + 1, p);
                    }
                }
                disableGoToCenter(level, siege.wave);
                list.remove(i);
            }
        }
    }

    /** ¿Esa aldea está siendo atacada ahora mismo (por el jugador o por el mundo)? */
    private static boolean isUnderAttack(ServerLevel level, int objectiveIndex) {
        return isUnderWorldSiege(level, objectiveIndex) || isUnderPlayerSiege(level, objectiveIndex);
    }

    /**
     * Apunta a un <b>defensor</b> de la aldea: lo llama {@code AggressiveZombieEntity.hurt} cada vez que
     * alguien le pega a un enemigo de una horda del mundo. Vale el jugador y también sus <b>minions</b> (el
     * mérito es del dueño), que es como pelea medio mod. Si esa aldea no tiene horda en curso, no hace nada.
     */
    public static void registerDefender(ServerLevel level, int objectiveIndex, @Nullable Entity attacker) {
        Player player = playerBehind(attacker);
        if (player == null) {
            return;
        }
        for (WorldSiege siege : WORLD_SIEGES.getOrDefault(level, List.of())) {
            if (siege.objectiveIndex == objectiveIndex) {
                siege.defenders.add(player.getUUID());
                return;
            }
        }
    }

    /** El jugador detrás de un atacante: él mismo, o el dueño si el que pega es un minion suyo. */
    @Nullable
    private static Player playerBehind(@Nullable Entity attacker) {
        if (attacker instanceof Player player) {
            return player;
        }
        if (attacker instanceof OwnableEntity ownable && ownable.getOwner() instanceof Player owner) {
            return owner;
        }
        return null;
    }

    /**
     * Un atacante de una horda ha muerto: se quita de la lista de <b>atacantes vivos</b> de su asedio (tanto de
     * los asedios del mundo como del asedio clásico del jugador). Eso permite distinguir "los mataron a todos"
     * de "se descargaron los chunks al alejarse el jugador", y que la recompensa solo se cobre en el primer caso.
     */
    public static void onSiegeAttackerKilled(ServerLevel level, UUID attacker) {
        for (WorldSiege siege : WORLD_SIEGES.getOrDefault(level, List.of())) {
            siege.wave.remove(attacker);
        }
        for (VillageDefense defense : DEFENSES.getOrDefault(level, List.of())) {
            defense.wave.remove(attacker);
        }
    }

    /**
     * Paga a quienes defendieron la aldea de una horda del mundo: <b>1 nivel de experiencia</b> (su punto de
     * habilidad llega por el camino normal) y unos lingotes que el pueblo comparte. Si nadie intervino, la
     * aldea se defendió sola y no se paga nada.
     */
    private static void rewardWorldSiegeDefenders(ServerLevel level, WorldSiege siege) {
        if (!siege.wave.isEmpty()) {
            // El asedio se da por resuelto porque los atacantes han dejado de estar cargados (el jugador se
            // alejó y se descargaron los chunks), no porque los mataran: no se paga nada.
            DevilRpg.LOGGER.info("[Village] Aldea {} resistió sin que nadie limpiara la horda ({} atacantes "
                            + "sin confirmar): sin recompensa", siege.objectiveIndex, siege.wave.size());
            return;
        }
        if (siege.defenders.isEmpty()) {
            DevilRpg.LOGGER.info("[Village] Aldea {} resistió sin ayuda de nadie: sin recompensa",
                    siege.objectiveIndex);
            return;
        }
        for (UUID uuid : siege.defenders) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
            if (player == null) {
                continue; // se desconectó antes de que acabara
            }
            int puntosGanados = MissionRewards.giveExperienceLevels(player, WORLD_SIEGE_REWARD_EXPERIENCE_LEVELS);
            String premio = MissionRewards.describe(WORLD_SIEGE_REWARD_EXPERIENCE_LEVELS, puntosGanados);
            player.displayClientMessage(Component.literal(
                    "Rechazaste la horda que iba a por la aldea: " + premio + " y el pueblo te da hierro."), false);
            player.addItem(new ItemStack(Items.IRON_INGOT, 4));
            DevilRpg.LOGGER.info("[Village] Aldea {} resistió: {} para {}", siege.objectiveIndex, premio,
                    player.getGameProfile().getName());
        }
    }

    private static boolean isUnderWorldSiege(ServerLevel level, int objectiveIndex) {
        for (WorldSiege siege : WORLD_SIEGES.getOrDefault(level, List.of())) {
            if (siege.objectiveIndex == objectiveIndex) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUnderPlayerSiege(ServerLevel level, int objectiveIndex) {
        for (VillageDefense defense : DEFENSES.getOrDefault(level, List.of())) {
            if (defense.objectiveIndex == objectiveIndex) {
                return true;
            }
        }
        return false;
    }

    /** Aldeanos vivos alrededor del centro de una aldea. */
    private static int countVillagers(ServerLevel level, BlockPos center) {
        return level.getEntitiesOfClass(Villager.class, new AABB(center).inflate(FALLEN_CHECK_RADIUS)).size();
    }

    /**
     * Cuenta los aldeanos vivos de una aldea y apunta su <b>salud</b>. Si el chunk no está cargado devuelve
     * {@link VillageSavedData#HEALTH_UNKNOWN} y no toca nada: contar entidades descargadas daría 0 y la aldea
     * parecería muerta (se marcaría caída sin motivo y se repoblaría a lo tonto).
     */
    private static int observeVillagers(ServerLevel level, VillageSavedData saved, int objectiveIndex, BlockPos center) {
        if (!level.isLoaded(center)) {
            return VillageSavedData.HEALTH_UNKNOWN;
        }
        int vivos = countVillagers(level, center);
        saved.setHealth(objectiveIndex, vivos);
        return vivos;
    }

    /** Una aldea con pocos aldeanos acumula presión más rápido: los monstruos van a por las débiles. */
    private static double pressureMultiplier(int health) {
        if (health == VillageSavedData.HEALTH_UNKNOWN || health >= VILLAGERS_FOR_FULL_HEALTH) {
            return 1.0D;
        }
        return 1.0D + (VILLAGERS_FOR_FULL_HEALTH - Math.max(0, health)) * PRESSURE_PER_MISSING_VILLAGER;
    }

    /**
     * Un latido de la vida de la aldea (cada {@link #VILLAGE_POLL_TICKS}, solo en aldeas en paz y con aldeanos):
     * <ul>
     *   <li><b>Cultivan y comen</b>: la granja produce comida y cada aldeano consume la suya. Sin despensa la
     *       aldea pasa hambre y no crece (ver {@link #FOOD_TO_GROW}).</li>
     *   <li><b>Reparan</b>: una aldea sana vuelve a levantar lo que se cayó en el último ataque.</li>
     *   <li><b>Envejecen</b>: ver {@link #ageVillagers}.</li>
     * </ul>
     */
    private static void tickVillageLife(ServerLevel level, VillageSavedData saved, int objectiveIndex, BlockPos center, int vivos) {
        int antes = saved.getFood(objectiveIndex);
        int comida = Math.min(MAX_FOOD, antes + FARM_YIELD) - vivos * FOOD_PER_VILLAGER;
        saved.setFood(objectiveIndex, comida);

        List<Villager> aldeanos = level.getEntitiesOfClass(Villager.class, new AABB(center).inflate(FALLEN_CHECK_RADIUS));

        // HAMBRE: si la despensa está vacía, los aldeanos se quedan débiles y, si se alarga, muere alguno.
        if (comida <= 0) {
            if (saved.getStarvingSince(objectiveIndex) == 0L) {
                saved.setStarvingSince(objectiveIndex, level.getGameTime());
                DevilRpg.LOGGER.info("[Village] La aldea {} se quedo sin comida: pasa hambre", objectiveIndex);
                announceNearby(level, center, "La aldea pasa hambre: sus granjas no dan abasto.");
            }
            starveVillagers(level, saved, objectiveIndex, aldeanos);
        } else if (saved.getStarvingSince(objectiveIndex) != 0L) {
            saved.setStarvingSince(objectiveIndex, 0L);
            DevilRpg.LOGGER.info("[Village] La aldea {} vuelve a tener comida", objectiveIndex);
        }

        // COMER DE VERDAD: se les reparte pan (lo recogen ellos) para que puedan criar como en vanilla.
        feedVillagers(level, saved, objectiveIndex, aldeanos);

        // OBRERO: se asegura de que exista el PLANO de la aldea y de que haya un aldeano que repare. El trabajo
        // lo hace su goal, andando y bloque a bloque (ver VillagerRepairGoal): aquí solo se prepara.
        prepareRepairs(level, saved, objectiveIndex, center, aldeanos);

        ageVillagers(level, aldeanos);
    }

    /**
     * Deja la aldea lista para repararse <b>sola y de verdad</b>: captura el <b>plano</b> la primera vez (qué
     * bloque debería haber en cada sitio) y nombra un <b>obrero</b> si no lo hay. Al de partidas viejas se le
     * pone antes la granja, para que el plano la incluya.
     */
    private static void prepareRepairs(ServerLevel level, VillageSavedData saved, int objectiveIndex, BlockPos center, List<Villager> aldeanos) {
        // Cambios de TRAZADO que hay que aplicar también a las aldeas ya construidas:
        //  1-2: granja (cultivos, acequia, compostero) y su nivelado a un solo nivel.
        //  3:   el plano apunta también lo que está a ras de suelo.
        //  4:   el plano pasa a ser canónico (lo graba el generador).
        //  5:   nivelado por mediana + escalón de entrada en las casas.
        //  6:   las cabañas procedurales se sustituyen por CASAS DEL JUEGO (con la marca `hasNewHouses`, para no
        //       reconstruir las que ya son nuevas: rehacer una casa borra lo que haya dentro).
        if (saved.getLayout(objectiveIndex) < CURRENT_LAYOUT) {
            int casas = saved.getCasasVersion(objectiveIndex);
            if (casas < CURRENT_HOUSES) {
                // Se rehacen TODAS las construcciones con el nivelado nuevo (al terreno de alrededor, no a su
                // propia huella) y se añaden las que falten (cuarta casa e iglesia). OJO: rehacer una casa borra lo
                // que tenga dentro; queda en el log con un WARN.
                VillageGenerator.actualizarCasas(level, center);
                VillageGenerator.actualizarTemplo(level, center);
                saved.setCasasVersion(objectiveIndex, CURRENT_HOUSES);
            }
            VillageGenerator.farm(level, center);
            // El plano se tira: hay que volver a capturarlo, ya con las casas nuevas y con las reglas actuales.
            saved.clearBlueprint(objectiveIndex);
            saved.setLayout(objectiveIndex, CURRENT_LAYOUT);
            DevilRpg.LOGGER.info("[Village] Aldea {}: trazado actualizado a la version {} (casas, granja y plano)",
                    objectiveIndex, CURRENT_LAYOUT);
        }
        if (!saved.hasBlueprint(objectiveIndex)) {
            VillageSavedData.Blueprint plano = VillageGenerator.captureBlueprint(level, center);
            saved.setBlueprint(objectiveIndex, plano);
            DevilRpg.LOGGER.info("[Village] Aldea {}: plano guardado ({} bloques)", objectiveIndex, plano.size());
        }
        // OBREROS: puede haber VARIOS (hasta MAX_BUILDERS) repartiéndose el trabajo. Los goals no se guardan con
        // la partida, así que se les repone cada vez que se les ve; y si faltan obreros, se nombran aldeanos
        // adultos, dejando al granjero para la huerta siempre que haya alguien más.
        int marcados = 0;
        for (Villager villager : aldeanos) {
            if (villager.getPersistentData().getBoolean(BUILDER_TAG)) {
                asegurarGoalDeObrero(villager, center, objectiveIndex);
                marcados++;
            }
        }
        int adultos = 0;
        for (Villager villager : aldeanos) {
            if (!villager.isBaby()) {
                adultos++;
            }
        }
        // Se reserva al menos un aldeano para lo suyo (huerta, comercio...) si hay gente de sobra.
        int deseados = Math.max(1, Math.min(MAX_BUILDERS, adultos - 1));
        for (Villager villager : aldeanos) {
            if (marcados >= deseados) {
                return;
            }
            if (villager.isBaby() || villager.getPersistentData().getBoolean(BUILDER_TAG)
                    || villager.getVillagerData().getProfession() == VillagerProfession.FARMER) {
                continue;
            }
            marcarObrero(villager, center, objectiveIndex);
            marcados++;
        }
        // Si no había más que granjeros, se tira de ellos (mejor una huerta más lenta que una aldea en ruinas).
        for (Villager villager : aldeanos) {
            if (marcados >= deseados) {
                return;
            }
            if (villager.isBaby() || villager.getPersistentData().getBoolean(BUILDER_TAG)) {
                continue;
            }
            marcarObrero(villager, center, objectiveIndex);
            marcados++;
        }
    }

    /** Marca a un aldeano como obrero y le pone el goal de reparación. */
    private static void marcarObrero(Villager villager, BlockPos center, int objectiveIndex) {
        villager.getPersistentData().putBoolean(BUILDER_TAG, true);
        asegurarGoalDeObrero(villager, center, objectiveIndex);
        DevilRpg.LOGGER.info("[Village] Aldea {}: {} es obrero de la aldea", objectiveIndex, villager.getUUID());
    }

    private static void asegurarGoalDeObrero(Villager villager, BlockPos center, int objectiveIndex) {
        for (WrappedGoal wrapped : villager.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof VillagerRepairGoal) {
                return;
            }
        }
        villager.goalSelector.addGoal(3, new VillagerRepairGoal(villager, center, objectiveIndex));
    }

    /** ¿Esa aldea está siendo atacada ahora mismo? (el obrero no trabaja en plena refriega). */
    public static boolean isVillageUnderAttack(ServerLevel level, int objectiveIndex) {
        return isUnderAttack(level, objectiveIndex);
    }

    /**
     * El hueco del plano que hay que reponer más cercano al obrero, o {@code null} si no hay nada roto a su
     * alcance. {@code excluir} trae las posiciones comprimidas que ese obrero ya descartó por inalcanzables, y
     * se saltan los huecos que otro obrero tenga reservados.
     */
    @Nullable
    public static BlockPos findRepairTarget(ServerLevel level, int objectiveIndex, BlockPos from, Set<Long> excluir, UUID builder) {
        VillageSavedData.Blueprint plano = VillageSavedData.get(level).getBlueprint(objectiveIndex);
        if (plano == null) {
            return null;
        }
        BlockPos mejor = null;
        double mejorDist = REPAIR_SEARCH_RADIUS * REPAIR_SEARCH_RADIUS;
        for (int i = 0; i < plano.size(); i++) {
            BlockPos pos = plano.posAt(i);
            long comprimida = pos.asLong();
            if (excluir.contains(comprimida) || reclamadoPorOtro(level, comprimida, builder)) {
                continue;
            }
            int dy = pos.getY() - from.getY();
            if (dy > REPAIR_MAX_UP || dy < -REPAIR_MAX_DOWN) {
                continue;
            }
            if (!necesitaReparacion(level.getBlockState(pos), plano.stateAt(i))) {
                continue;
            }
            double dist = pos.distSqr(from);
            if (dist < mejorDist) {
                mejorDist = dist;
                mejor = pos;
            }
        }
        return mejor;
    }

    /**
     * ¿Hay que reponer algo en esa posición? Se repone si está en <b>aire</b> (lo típico: lo rompió un asedio) o si
     * el bloque que hay es el resultado de un destrozo concreto: la <b>tierra de cultivo se convierte en tierra</b>
     * cuando alguien salta encima, así que si el plano dice tierra de cultivo (o la acequia) y ahora hay tierra o
     * hierba, se vuelve a poner. Cualquier otra cosa (lo que haya puesto el jugador) no se toca.
     */
    private static boolean necesitaReparacion(BlockState actual, BlockState esperado) {
        if (actual.isAir()) {
            return true;
        }
        if (actual.equals(esperado)) {
            return false;
        }
        boolean eraHuerta = esperado.is(Blocks.FARMLAND) || esperado.is(Blocks.WATER);
        boolean pisoteada = actual.is(Blocks.DIRT) || actual.is(Blocks.GRASS_BLOCK) || actual.is(Blocks.COARSE_DIRT)
                || actual.is(Blocks.PODZOL) || actual.is(Blocks.ROOTED_DIRT);
        return eraHuerta && pisoteada;
    }

    /** Lo mismo, mirando el plano: ¿ese hueco hay que reponerlo? (lo consulta el obrero en cada tick). */
    public static boolean necesitaReparacion(ServerLevel level, int objectiveIndex, BlockPos pos) {
        BlockState esperado = blueprintState(level, objectiveIndex, pos);
        return esperado != null && necesitaReparacion(level.getBlockState(pos), esperado);
    }

    /**
     * Reserva un hueco para un obrero. Devuelve {@code false} si otro lo tiene cogido todavía (así, con varios
     * obreros, cada uno va a un sitio distinto en vez de amontonarse en el mismo agujero).
     */
    public static boolean reclamarHueco(ServerLevel level, BlockPos pos, UUID builder) {
        Map<Long, Reclamo> mapa = CLAIMS.computeIfAbsent(level, l -> new HashMap<>());
        long ahora = level.getGameTime();
        if (reclamadoPorOtro(level, pos.asLong(), builder)) {
            return false;
        }
        if (mapa.size() > 512) {
            mapa.values().removeIf(reclamo -> ahora - reclamo.tick() >= CLAIM_TIMEOUT_TICKS);
        }
        mapa.put(pos.asLong(), new Reclamo(builder, ahora));
        return true;
    }

    /** Suelta la reserva de un hueco (al colocarlo, al abandonarlo o al parar el goal). */
    public static void liberarHueco(ServerLevel level, BlockPos pos) {
        Map<Long, Reclamo> mapa = CLAIMS.get(level);
        if (mapa != null) {
            mapa.remove(pos.asLong());
        }
    }

    private static boolean reclamadoPorOtro(ServerLevel level, long posComprimida, UUID builder) {
        Map<Long, Reclamo> mapa = CLAIMS.get(level);
        if (mapa == null) {
            return false;
        }
        Reclamo reclamo = mapa.get(posComprimida);
        return reclamo != null && !reclamo.builder().equals(builder)
                && level.getGameTime() - reclamo.tick() < CLAIM_TIMEOUT_TICKS;
    }

    /** El bloque que debería haber en esa posición según el plano de la aldea ({@code null} si no está en él). */
    @Nullable
    public static BlockState blueprintState(ServerLevel level, int objectiveIndex, BlockPos pos) {
        VillageSavedData.Blueprint plano = VillageSavedData.get(level).getBlueprint(objectiveIndex);
        if (plano == null) {
            return null;
        }
        long comprimida = pos.asLong();
        for (int i = 0; i < plano.size(); i++) {
            if (plano.positions()[i] == comprimida) {
                return plano.stateAt(i);
            }
        }
        return null;
    }

    /**
     * Reparte <b>pan de verdad</b>: a un aldeano que todavía no puede criar (en vanilla hacen falta 12 puntos de
     * comida en total) se le deja un pan en el suelo, que <b>recoge él mismo</b>. Así se ve la comida, se la
     * llevan andando y con eso nacen crías. Cada pan cuesta {@link #FOOD_PER_BREAD} de la despensa y solo se
     * reparte uno por latido, para que la aldea no se quede sin reservas. A los viejos no se les da: ya no crían.
     */
    private static void feedVillagers(ServerLevel level, VillageSavedData saved, int objectiveIndex, List<Villager> aldeanos) {
        if (saved.getFood(objectiveIndex) < FOOD_PER_BREAD) {
            return; // despensa vacía: no hay pan que repartir
        }
        for (Villager villager : aldeanos) {
            if (villager.isBaby() || villager.canBreed()) {
                continue; // las crías no comen de la despensa y el que ya puede criar no lo necesita
            }
            if (ageOf(level, villager) >= VILLAGER_OLD_AGE_TICKS) {
                continue; // viejo: ya no cría, no se le da pan
            }
            ItemEntity pan = new ItemEntity(level, villager.getX(), villager.getY() + 0.4D, villager.getZ(),
                    new ItemStack(Items.BREAD));
            pan.setDefaultPickUpDelay();
            level.addFreshEntity(pan);
            saved.setFood(objectiveIndex, saved.getFood(objectiveIndex) - FOOD_PER_BREAD);
            return; // uno por latido
        }
    }

    /**
     * Consecuencias del hambre: mientras la aldea no tenga comida, sus aldeanos van con <b>Debilidad</b> y
     * <b>Lentitud</b>, y si el hambre dura más de {@link #STARVATION_DEATH_TICKS} (10 min) <b>muere uno</b>.
     */
    private static void starveVillagers(ServerLevel level, VillageSavedData saved, int objectiveIndex, List<Villager> aldeanos) {
        for (Villager villager : aldeanos) {
            if (!villager.hasEffect(MobEffects.WEAKNESS)) {
                villager.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 60, 0, false, false));
            }
            if (!villager.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                villager.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 60, 0, false, false));
            }
        }
        long desde = saved.getStarvingSince(objectiveIndex);
        if (desde != 0L && level.getGameTime() - desde >= STARVATION_DEATH_TICKS && !aldeanos.isEmpty()) {
            Villager victima = aldeanos.get(level.random.nextInt(aldeanos.size()));
            DevilRpg.LOGGER.info("[Village] Un aldeano de la aldea {} ha muerto de hambre", objectiveIndex);
            victima.hurt(level.damageSources().generic(), Float.MAX_VALUE);
            // El contador se reinicia: el siguiente no cae hasta dentro de otros 10 min de hambre.
            saved.setStarvingSince(objectiveIndex, level.getGameTime());
        }
    }

    /**
     * <b>Edad</b> de un aldeano en ticks de juego: la primera vez que se le ve se le apunta la fecha de
     * nacimiento en sus datos persistentes (viaja con él en el guardado). {@code 0} si acaba de conocerse.
     */
    private static long ageOf(ServerLevel level, Villager villager) {
        CompoundTag datos = villager.getPersistentData();
        if (!datos.contains(BORN_TAG)) {
            datos.putLong(BORN_TAG, level.getGameTime());
            return 0L;
        }
        return level.getGameTime() - datos.getLong(BORN_TAG);
    }

    /**
     * <b>Envejecimiento visible</b>: a partir de {@link #VILLAGER_OLD_AGE_TICKS} (2 días de juego) el aldeano es
     * viejo: va más lento y más débil (y ya no se le reparte pan, así que no cría). Al llegar a
     * {@link #VILLAGER_LIFESPAN_TICKS} (3 días) muere de viejo con la animación y el sonido de muerte normales,
     * no con un borrado seco.
     */
    private static void ageVillagers(ServerLevel level, List<Villager> aldeanos) {
        for (Villager villager : aldeanos) {
            long edad = ageOf(level, villager);
            if (edad >= VILLAGER_LIFESPAN_TICKS) {
                DevilRpg.LOGGER.info("[Village] Un aldeano murio de viejo a los {} dias de juego",
                        edad / 24000L);
                villager.hurt(level.damageSources().generic(), Float.MAX_VALUE);
            } else if (edad >= VILLAGER_OLD_AGE_TICKS) {
                if (!villager.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                    villager.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 60, 0, false, false));
                }
                if (!villager.hasEffect(MobEffects.WEAKNESS)) {
                    villager.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 60, 0, false, false));
                }
            }
        }
    }

    /**
     * La aldea cae: se queda sin aldeanos y eso es <b>definitivo</b> (no vuelve a ser objetivo de hordas ni se
     * repuebla). Se deja en <b>ruinas</b> ({@link VillageGenerator#ruin}) para que el jugador vea lo que pasó
     * cuando vuelva. Idempotente: si ya estaba caída no hace nada.
     */
    private static void fallVillage(ServerLevel level, VillageSavedData saved, int objectiveIndex, BlockPos center) {
        if (saved.isFallen(objectiveIndex)) {
            return;
        }
        saved.markFallen(objectiveIndex);
        VillageGenerator.ruin(level, center, objectiveIndex);
        DevilRpg.LOGGER.info("[Village] La aldea {} ha CAÍDO y queda en ruinas", objectiveIndex);
    }

    /** Manda un mensaje a los jugadores que estén cerca de la aldea. */
    private static void announceNearby(ServerLevel level, BlockPos center, String message) {
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().distSqr(center) <= SIEGE_WARN_RADIUS * SIEGE_WARN_RADIUS) {
                p.displayClientMessage(Component.literal(message), false);
            }
        }
    }

    /** Punto de inicio del jugador (ancla; o su spawn si aún no hay ancla). */
    private static Vec3 anchorOf(Player player) {
        PlayerAuxiliaryCapabilityInterface aux = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
        if (aux == null) {
            return null;
        }
        Vec3 anchor = aux.getAnchorPoint();
        return anchor != null ? anchor : aux.getSpawnPoint();
    }

    /** Datos de un asedio dirigido por el mundo (horda que va a por una aldea por su cuenta). */
    private static final class WorldSiege {
        final int objectiveIndex;
        final BlockPos center;
        final List<UUID> wave;
        /**
         * En los asedios del mundo esta lista guarda solo los atacantes <b>vivos</b>: cada muerte se descuenta
         * ({@link #onWorldSiegeAttackerKilled}), así que quedar vacía significa "los mataron a todos". Eso es lo
         * que separa una defensa de verdad de un asedio que se resolvió porque el jugador se alejó.
         */
        final Set<UUID> defenders = new HashSet<>();

        WorldSiege(int objectiveIndex, BlockPos center, List<UUID> wave) {
            this.objectiveIndex = objectiveIndex;
            this.center = center;
            this.wave = wave;
        }
    }

    /** Datos de un asedio en curso. */
    private static final class VillageDefense {
        final int objectiveIndex;
        final UUID playerUUID;
        final BlockPos center;
        final List<UUID> wave = new ArrayList<>();
        long tickTicks;
        boolean waveSpawned;

        VillageDefense(int objectiveIndex, UUID playerUUID, BlockPos center) {
            this.objectiveIndex = objectiveIndex;
            this.playerUUID = playerUUID;
            this.center = center;
        }
    }
}
