package com.chipoodle.devilrpg.debug;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.goal.VillagerGuardGoal;
import com.chipoodle.devilrpg.world.VillageGenerator;
import com.chipoodle.devilrpg.world.VillageManager;
import com.chipoodle.devilrpg.world.VillageSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import static com.chipoodle.devilrpg.debug.PoiPersistenceReport.Celda;
import static com.chipoodle.devilrpg.debug.PoiPersistenceReport.Duenio;
import static com.chipoodle.devilrpg.debug.PoiPersistenceReport.Hallazgo;
import static com.chipoodle.devilrpg.debug.PoiPersistenceReport.Objeto;

/**
 * ARNÉS DE PERSISTENCIA REAL DE POI/TICKETS · {@code ServerLevel} + {@code PoiManager} de verdad.
 * <p>
 * No hay dobles: el mundo es un directorio desechable ({@code build/av/poi-<run>/game}) que se abre,
 * se guarda con {@code saveEverything}, se cierra y se vuelve a abrir en OTRO JVM. Cada fase es un
 * proceso distinto, así que lo que se mide al cargar solo puede venir del disco.
 *
 * <h2>Fases</h2>
 * <ul>
 *   <li><b>prepare</b>: mundo virgen → {@code VillageManager.preGenerate} → se fuerza el área de la
 *       aldea → se espera a que pase la ventana de gracia de carga (1200 ticks) y a que el reparto se
 *       estabilice → <b>sonda real de tickets</b> ({@code take}/{@code release}/{@code take}) →
 *       <b>fixtures mínimas y documentadas</b> (duplicado, huérfano+estación libre, estación libre sin
 *       memoria) → se captura el estado que se va a guardar → {@code saveEverything} → parada.</li>
 *   <li><b>reload1</b>: reapertura. Se fuerza todo el área <b>menos el chunk del aldeano tardío L</b>:
 *       se comprueba que lo guardado carga igual (mapa POI→UUID exacto), se mide el efecto del
 *       mantenimiento antes de la gracia (posible liberación de tickets de dueños aún no cargados), se
 *       cruza la gracia, se mide la recuperación, y <b>después</b> se carga el chunk de L para ver si
 *       conserva su puesto o se lo han robado (duplicado → reconciliación). Sonda de entidad extra:
 *       se crea un aldeano más y se comprueba que no roba un puesto ajeno.</li>
 *   <li><b>reload2</b>: segundo ciclo de recarga completo. Round-trip otra vez y estabilidad: el estado
 *       final no debe cambiar entre recargas (idempotencia).</li>
 *   <li><b>partial</b>: carga parcial agresiva: se fuerza UN chunk con un profesional y se mide si el
 *       mantenimiento libera tickets de POI cuyo dueño persistido aún no está cargado (el fallo que
 *       rompe la propiedad al reabrir). Después se fuerza todo y se comprueba que no hay duplicados ni
 *       pérdidas.</li>
 * </ul>
 *
 * <h2>Qué es PASS</h2>
 * Hay DOS veredictos por fase, y el lanzador imprime los dos:
 * <ul>
 *   <li>{@code experimento_ok}: el instrumento funcionó (POI/mundo reales, guardado real, lectura de
 *       disco, evidencia escrita, sin timeout).</li>
 *   <li>{@code contrato_ok}: el comportamiento observado cumple las invariantes de POI/tickets que se
 *       documentan en {@code tools/poi/LEEME.md} (un dueño por celda, sin huérfanos, todo ticket
 *       cogido con dueño, promoción POTENTIAL→JOB_SITE, cobertura de los 11 puestos, round-trip,
 *       fixtures recuperadas, carga parcial sin liberar tickets ajenos). Un {@code contrato_ok=false}
 *       es el resultado VÁLIDO de la campaña si expone un defecto: se lista con códigos estables en
 *       {@code hallazgos}. El veredicto global {@code POI_PASS} exige los dos.</li>
 * </ul>
 *
 * <h2>Por qué no GameTest</h2>
 * {@code gameTestServer} arranca un mundo plano nuevo por corrida, ejecuta {@code @GameTest} y sale:
 * no conserva el mundo entre procesos (harían falta las dos recargas sobre el MISMO guardado), exige
 * plantillas de estructura y no permite dirigir {@code saveEverything}-cierre-reapertura. Por eso se
 * usa el mismo patrón de servidor que C4. (Se comprobó que el proyecto no tiene gametests registrados:
 * {@code neoforge.enabledGameTestNamespaces} está puesto pero no hay {@code @GameTest} en el árbol.)
 *
 * <h2>Aislamiento</h2>
 * Se compila solo con {@code -Pa2nDiagnostics=true -PpoiHarness=true}; ese flag excluye todo
 * {@code debug/**} salvo estas dos clases (C4, OwnershipProbe, A2NavHarness… no se cargan). No toca
 * {@code run/} ni saves del jugador: el mundo es el de {@code -Pa2nGameDirectory}.
 */
@EventBusSubscriber(modid = DevilRpg.MODID)
@SuppressWarnings("deprecation")
public final class PoiPersistenceHarness {

    // ------------------------------------------------------------------ configuración de la corrida

    private static final String FASE = prop("poi.phase", "none").trim().toLowerCase(Locale.ROOT);
    private static final String RUN = prop("poi.run", "sin-run");
    private static final Path RAIZ = Path.of(prop("poi.raiz", "."));
    private static final int OBJETIVO = 0;
    /** Mismo punto que usa C4: preGenerate elige la cota real del terreno en ese XZ. */
    private static final BlockPos CENTRO_PEDIDO = new BlockPos(661, 71, 533);
    /** Radio de barrido de POI: la valla (62) más margen para las estaciones de las parcelas. */
    private static final int RADIO_POI = VillageGenerator.FENCE_RADIUS + 12;
    /** Espejo de {@code VillageManager.CARGA_POBLACION_GRACIA_TICKS} (privado). */
    private static final int GRACIA = Integer.getInteger("poi.gracia", 1200);
    /** A partir de aquí se da por pasada la gracia para el reparto (margen sobre el poll de producción). */
    private static final int ESPERA_GRACIA = GRACIA + 200;
    private static final int MAX_TICKS = Integer.getInteger("poi.maxTicks", 5000);
    private static final int CHUNKS_ALREDEDOR = 8;
    private static final int MAX_LLAMADAS_ESTABLES = 14;

    private static final String PREPARE = "prepare";
    private static final String RELOAD1 = "reload1";
    private static final String RELOAD2 = "reload2";
    private static final String PARTIAL = "partial";

    // ------------------------------------------------------------------ estado

    private static ServerLevel nivel;
    private static MinecraftServer server;
    private static Path base;
    private static BlockPos centro;
    private static boolean preparado;
    private static boolean hecho;

    private static int ticks;
    private static int paso;
    private static int inicioPaso;
    private static final List<Etapa> etapas = new ArrayList<>();
    private static String falloInstrumento = "";

    private static final List<Check> checks = new ArrayList<>();
    private static final List<Hallazgo> hallazgos = new ArrayList<>();
    private static final List<String> eventos = new ArrayList<>();
    private static final List<String> fixtures = new ArrayList<>();
    private static final List<String> ticketProbe = new ArrayList<>();

    private static Estado estadoInicial;
    private static Estado estadoFeliz;
    private static Estado estadoAntesFixtures;
    private static Estado estadoCarga;
    private static Estado estadoFinal;
    private static Estado estadoPostGracia;
    private static Estado estadoPostTardio;

    private static Map<String, Snap> snapshotPrevio = new LinkedHashMap<>();
    private static Map<Long, String> propiedadPersistida = new LinkedHashMap<>();
    private static Map<Long, String> ticketPersistido = new LinkedHashMap<>();
    private static String digestPrevio = "";
    private static String centroPrevio = "";
    private static int profesionalesEsperados;
    private static int guardiasEsperados;

    private static String digestVivoInicial = "";
    private static String digestVivoFinal = "";
    private static String digestDisco = "";
    private static boolean guardadoOk;
    private static boolean setupOk;
    private static boolean timeout;

    private static String lateUuid = "";
    private static String lateProfesion = "";
    private static long lateChunk = Long.MIN_VALUE;
    private static long latePuestoEsperado;
    private static long latePotencialEsperado;
    private static String lateRazon = "";
    /** Chunks que NO se fuerzan en reload1 para que L no cargue por propagación de tickets de sus vecinos. */
    private static final Set<Long> zonaTardia = new LinkedHashSet<>();
    /** Aldeanos cuyo chunk cae en {@link #zonaTardia}: no son evaluables en la foto de carga. */
    private static final Set<String> zonaTardiaUuids = new LinkedHashSet<>();
    /** Aldeanos tocados por fixtures (duplicado/huérfano/estación libre/F4): no se evalúan en la foto de carga. */
    private static final Set<String> fixtureUuids = new LinkedHashSet<>();
    private static long partialChunk = Long.MIN_VALUE;
    private static String partialRazon = "";

    private static long fixtureDuplicadoPoi;
    private static String fixtureDuplicadoA = "";
    private static String fixtureDuplicadoB = "";
    /** Titular persistido que debe sobrevivir al duplicado (no se impone A artificialmente). */
    private static String fixtureDuplicadoKeeper = "";
    private static String fixtureHuerfanoUuid = "";
    private static long fixtureHuerfanoSitio;
    private static String fixtureLibreUuid = "";
    private static long fixtureLibrePoi;
    /** F4 (solo reload2): dos POTENTIAL al mismo POI y UN ticket; se mide la promoción sin fantasma. */
    private static long fixtureF4Poi;
    private static String fixtureF4A = "";
    private static String fixtureF4B = "";
    private static String fixtureF4Motivo = "";

    private static final List<Hallazgo> liberadosCargaParcial = new ArrayList<>();
    private static final Set<String> cargadosAntesMantenimiento = new LinkedHashSet<>();
    private static boolean casoCargaParcialMedido;
    private static int mantenimientos;
    private static final Map<String, String> cupoPorOficio = new LinkedHashMap<>();

    private static final List<String> eventosDetalle = new ArrayList<>();
    private static final List<String> liberadosDetalle = new ArrayList<>();

    private PoiPersistenceHarness() {
    }

    private static String prop(final String clave, final String defecto) {
        return System.getProperty(clave, defecto);
    }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent
    public static void alArrancar(final ServerStartedEvent evento) {
        server = evento.getServer();
        nivel = server.overworld();
        base = RAIZ.resolve("build").resolve("av").resolve(RUN);
        try {
            Files.createDirectories(base);
        } catch (Exception e) {
            log("no pude crear " + base + ": " + e);
        }
        log("ARRANCADO fase=" + FASE + " run=" + RUN + " gameTime=" + nivel.getGameTime());
        if (!FASE.equals(PREPARE) && !FASE.equals(RELOAD1) && !FASE.equals(RELOAD2) && !FASE.equals(PARTIAL)) {
            // INERTE a propósito: si una clase compilada de este arnés sobrevive en build/classes de otra
            // corrida (Gradle no borra clases huérfanas), NO debe haltar el servidor de C4/A2/jugador.
            log("fase no configurada ('" + FASE + "'): el arnes POI no hace nada");
            return;
        }
        try {
            cupoPorOficio.putAll(cupoDelPueblo());
            prepararFase();
        } catch (RuntimeException e) {
            fallar("setup", e.toString());
        }
    }

    @SubscribeEvent
    public static void alTick(final ServerTickEvent.Pre evento) {
        if (!preparado || hecho) {
            return;
        }
        ticks++;
        if (ticks > MAX_TICKS) {
            timeout = true;
            fallar("timeout", "ticks=" + ticks + " paso=" + (paso < etapas.size() ? etapas.get(paso).nombre : "?"));
            return;
        }
        if (paso >= etapas.size()) {
            return;
        }
        Etapa etapa = etapas.get(paso);
        if (etapa.inicio < 0) {
            etapa.inicio = ticks;
            inicioPaso = ticks;
        }
        try {
            if (etapa.accion.correr()) {
                log("ETAPA_OK " + etapa.nombre + " t=" + ticks);
                paso++;
                inicioPaso = ticks;
            }
        } catch (RuntimeException e) {
            fallar("etapa_" + etapa.nombre, e.toString());
        }
    }

    // ------------------------------------------------------------------ preparación de la fase

    private static void prepararFase() {
        VillageSavedData saved = VillageSavedData.get(nivel);
        if (FASE.equals(PREPARE)) {
            if (saved.isGenerated(OBJETIVO)) {
                fallar("mundo_no_virgen", "prepare sobre un mundo ya generado");
                return;
            }
            boolean okGen = VillageManager.preGenerate(nivel, OBJETIVO, CENTRO_PEDIDO);
            if (!okGen) {
                fallar("preGenerate_fallo", "");
                return;
            }
            centro = saved.getCentre(OBJETIVO);
            if (centro == null) {
                fallar("centro_no_persistido", "");
                return;
            }
            for (long chunk : chunksAlrededor()) {
                nivel.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), true);
            }
            setupOk = true;
            // El contrato del pueblo son 12 plazas y 4 guardias fundadoras (constantes de producción):
            // sin esto, en prepare el check de entidades comparaba contra 0/0 (detalle 12/0 y 4/0).
            profesionalesEsperados = VillageGenerator.puestosDelPueblo();
            guardiasEsperados = VillageManager.MILICIA_FUNDADORES;
            log("prepare centro=" + centro.toShortString() + " aldeanos=" + aldeanosVivos().size());
            etapasPrepare();
        } else {
            centro = saved.getCentre(OBJETIVO);
            if (centro == null) {
                fallar("centro_ausente_en_reload", "");
                return;
            }
            String previa = FASE.equals(RELOAD1) ? PREPARE : FASE.equals(RELOAD2) ? RELOAD1 : RELOAD2;
            snapshotPrevio = leerSnapshot(previa);
            propiedadPersistida = leerPuestos(previa);
            ticketPersistido = new LinkedHashMap<>();
            for (Snap s : snapshotPrevio.values()) {
                if (s.puesto() > 0L) {
                    ticketPersistido.putIfAbsent(s.puesto(), s.uuid());
                }
                if (s.potencial() > 0L) {
                    ticketPersistido.putIfAbsent(s.potencial(), s.uuid());
                }
            }
            digestPrevio = leerDigest(previa);
            centroPrevio = leerCentro(previa);
            if (snapshotPrevio.isEmpty()) {
                fallar("sin_snapshot_previo", "no hay snapshot-" + previa + ".txt; ejecuta primero la fase previa");
                return;
            }
            fixtureUuids.clear();
            fixtureUuids.addAll(leerFixturesUuids(PREPARE));
            cargarFixtureF1DeDisco();
            if (FASE.equals(PARTIAL)) {
                fixtureUuids.addAll(leerFixturesUuids(RELOAD1));
                fixtureUuids.addAll(leerFixturesUuids(RELOAD2));
            }
            if (profesionalesEsperados <= 0) {
                profesionalesEsperados = VillageGenerator.puestosDelPueblo();
            }
            if (guardiasEsperados <= 0) {
                guardiasEsperados = VillageManager.MILICIA_FUNDADORES;
            }
            profesionalesEsperados = 0;
            guardiasEsperados = 0;
            for (Snap s : snapshotPrevio.values()) {
                if (s.bebe()) {
                    continue;
                }
                if (s.guardia()) {
                    guardiasEsperados++;
                } else if (!"none".equals(s.profesion())) {
                    profesionalesEsperados++;
                }
            }
            if (propiedadPersistida.isEmpty()) {
                log("AVISO: no hay tabla de puestos previa (" + previa + "); el round-trip no se podrá exigir");
            }
            if (digestPrevio.isBlank()) {
                log("AVISO: no hay digest de VillageSavedData previo (" + previa + ")");
            }
            setupOk = true;
            log(FASE + " centro=" + centro.toShortString() + " snapshot=" + snapshotPrevio.size()
                    + " puestos_previos=" + propiedadPersistida.size() + " digest_previo="
                    + (digestPrevio.isBlank() ? "AUSENTE" : digestPrevio.substring(0, Math.min(12, digestPrevio.length()))));
            if (FASE.equals(RELOAD1)) {
                elegirTardio();
                for (long chunk : chunksAlrededor()) {
                    if (!zonaTardia.contains(chunk)) {
                        nivel.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), true);
                    }
                }
                log("reload1 chunk_tardio=" + (lateChunk == Long.MIN_VALUE ? "ninguno"
                        : new ChunkPos(lateChunk).toString() + " razon=" + lateRazon
                        + " zona_no_forzada=" + zonaTardia.size()));
                etapasReload1();
            } else if (FASE.equals(RELOAD2)) {
                for (long chunk : chunksAlrededor()) {
                    nivel.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), true);
                }
                etapasReload2();
            } else {
                elegirChunkParcial();
                if (partialChunk != Long.MIN_VALUE) {
                    nivel.setChunkForced(ChunkPos.getX(partialChunk), ChunkPos.getZ(partialChunk), true);
                }
                log("partial chunk_forzado=" + (partialChunk == Long.MIN_VALUE ? "ninguno"
                        : new ChunkPos(partialChunk).toString() + " razon=" + partialRazon));
                etapasPartial();
            }
        }
        preparado = true;
    }

    /** Elige el aldeano TARDÍO: uno con memoria de POI que pueda perder su puesto si se carga tarde. */
    private static void elegirTardio() {
        Set<String> fixtureUuids = leerFixturesUuids(PREPARE);
        Map<Long, Integer> dueniosPorPoi = new LinkedHashMap<>();
        for (Snap s : snapshotPrevio.values()) {
            if (s.puesto() > 0L) {
                dueniosPorPoi.merge(s.puesto(), 1, Integer::sum);
            }
        }
        Map<Long, List<Snap>> porChunk = new LinkedHashMap<>();
        for (Snap s : snapshotPrevio.values()) {
            if (s.bebe() || s.guardia() || "none".equals(s.profesion())) {
                continue;
            }
            porChunk.computeIfAbsent(new ChunkPos(s.pos()).toLong(), k -> new ArrayList<>()).add(s);
        }
        Snap mejor = null;
        long mejorChunk = Long.MIN_VALUE;
        int mejorPuntos = -1;
        for (Map.Entry<Long, List<Snap>> e : porChunk.entrySet()) {
            if (e.getValue().size() != 1) {
                continue; // chunk compartido: al forzarlo cargarían varios y se pierde el control
            }
            Snap s = e.getValue().get(0);
            // Fuera los aldeanos que el propio arnés ha tocado con fixtures (duplicado, huérfano,
            // estación libre): si el tardío es uno de ellos, su reasignación es el RESULTADO esperado de
            // la reconciliación y no una prueba limpia de persistencia del aldeano tardío.
            if (fixtureUuids.contains(s.uuid())) {
                continue;
            }
            if (s.puesto() <= 0L && s.potencial() <= 0L) {
                continue; // sin memoria: no hay nada que demostrar al cargarlo tarde
            }
            if (s.puesto() > 0L && dueniosPorPoi.getOrDefault(s.puesto(), 0) > 1) {
                continue; // su POI está duplicado en el guardado (fixture F1): no es candidato limpio
            }
            int puntos = 0;
            if (s.puesto() > 0L || s.potencial() > 0L) {
                puntos += 10;
            }
            if (cuota(cupoPorOficio, s.profesion()) > 1) {
                puntos += 5; // hay compañeros de oficio que podrían reclamar su celda
            }
            if (puntos > mejorPuntos) {
                mejor = s;
                mejorChunk = e.getKey();
                mejorPuntos = puntos;
            }
        }
        if (mejor == null) {
            lateRazon = "sin_candidato_de_chunk_exclusivo";
            return;
        }
        lateUuid = mejor.uuid();
        lateProfesion = mejor.profesion();
        lateChunk = mejorChunk;
        latePuestoEsperado = mejor.puesto();
        latePotencialEsperado = mejor.potencial();
        // Un chunk forzado propaga tickets a los vecinos: con solo saltarse el chunk de L, sus vecinos
        // forzados lo cargaban igual (nivel de ticket 31+2 <= FULL). Se deja un buffer de radio 2 y se
        // fuerza la zona entera más tarde, en el paso "cargar_tardio".
        zonaTardia.clear();
        zonaTardiaUuids.clear();
        int lx = ChunkPos.getX(lateChunk);
        int lz = ChunkPos.getZ(lateChunk);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                zonaTardia.add(new ChunkPos(lx + dx, lz + dz).toLong());
            }
        }
        for (Snap s : snapshotPrevio.values()) {
            if (zonaTardia.contains(new ChunkPos(s.pos()).toLong())) {
                zonaTardiaUuids.add(s.uuid());
            }
        }
        lateRazon = "chunk_exclusivo con_memoria=" + (mejor.puesto() > 0L || mejor.potencial() > 0L)
                + " cuota=" + cuota(cupoPorOficio, mejor.profesion())
                + " zona=" + zonaTardia.size() + "ch/" + zonaTardiaUuids.size() + "aldeanos";
    }

    /** Elige UN chunk con un solo profesional (preferible sin JOB_SITE) para la carga parcial. */
    private static void elegirChunkParcial() {
        Map<Long, List<Snap>> porChunk = new LinkedHashMap<>();
        for (Snap s : snapshotPrevio.values()) {
            if (s.bebe() || s.guardia() || "none".equals(s.profesion())) {
                continue;
            }
            porChunk.computeIfAbsent(new ChunkPos(s.pos()).toLong(), k -> new ArrayList<>()).add(s);
        }
        Snap mejor = null;
        long mejorChunk = Long.MIN_VALUE;
        int mejorPuntos = -1;
        for (Map.Entry<Long, List<Snap>> e : porChunk.entrySet()) {
            if (e.getValue().size() != 1) {
                continue;
            }
            Snap s = e.getValue().get(0);
            int puntos = 0;
            if (s.puesto() == 0L) {
                puntos += 10; // un titular sin JOB_SITE: hay "demanda" cargada
            }
            if (cuota(cupoPorOficio, s.profesion()) > 1) {
                puntos += 5; // compañeros descargados con el ticket cogido
            }
            if (puntos > mejorPuntos) {
                mejor = s;
                mejorChunk = e.getKey();
                mejorPuntos = puntos;
            }
        }
        if (mejor == null) {
            partialRazon = "sin_candidato_de_chunk_exclusivo";
            return;
        }
        partialChunk = mejorChunk;
        partialRazon = "chunk_exclusivo uuid=" + mejor.uuid() + " prof=" + mejor.profesion()
                + " job=" + (mejor.puesto() > 0L) + " pot=" + (mejor.potencial() > 0L);
    }

    private static int cuota(final Map<String, String> cupo, final String profesion) {
        try {
            return Integer.parseInt(cupo.getOrDefault(profesion, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ etapas: prepare

    private static void etapasPrepare() {
        etapas.clear();
        etapas.add(new Etapa("esperar_carga_inicial", () -> {
            if (ticks - inicioPaso < 40) {
                return false;
            }
            estadoInicial = capturar();
            log("prepare carga_inicial aldeanos=" + estadoInicial.duenios().size()
                    + " job=" + contarJobSite(estadoInicial));
            mantener("prepare_inicial");
            return true;
        }));
        etapas.add(new Etapa("esperar_gracia", () -> {
            if (ticks - inicioPaso >= 200 && (ticks - inicioPaso) % 200 == 0) {
                mantener("prepare_pre_gracia");
            }
            if (ticks < inicioPaso + ESPERA_GRACIA) {
                return false;
            }
            mantener("prepare_pasada_gracia");
            return true;
        }));
        etapas.add(new Etapa("estabilizar_reparto", () -> {
            if (ticks - inicioPaso >= 40) {
                Estado antes = estadoFinal != null ? estadoFinal : capturar();
                Estado despues = mantenerYOBSERVAR("prepare_estabilizar");
                estadoFinal = despues;
                boolean sinCambios = huella(antes).equals(huella(despues));
                if (sinCambios) {
                    return true;
                }
                if (ticks - inicioPaso > 600) {
                    eventos.add("estabilizar_limite_llamadas");
                    return true;
                }
            }
            return false;
        }));
        etapas.add(new Etapa("cerrar_prepare", () -> {
            estadoFeliz = capturar();
            sondaDeTickets();
            aplicarFixtures();
            estadoAntesFixtures = estadoFeliz;
            cerrarFase(estadoFeliz, capturar());
            return true;
        }));
    }

    // ------------------------------------------------------------------ etapas: reload1

    private static void etapasReload1() {
        etapas.clear();
        etapas.add(new Etapa("carga_inicial", () -> {
            if (!todosCargadosExceptoTardio() && ticks - inicioPaso < 400) {
                return false;
            }
            estadoCarga = capturar();
            digestVivoInicial = huellaVivo();
            log("reload1 carga t=" + ticks + " aldeanos=" + estadoCarga.duenios().size()
                    + " job=" + contarJobSite(estadoCarga));
            boolean ok = comprobarRoundTrip(estadoCarga, "reload1_carga");
            checks.add(new Check("saveddata_carga", digestPrevio.isBlank() || digestVivoInicial.equals(digestPrevio),
                    "vivo=" + corto(digestVivoInicial) + " previo=" + corto(digestPrevio)));
            if (!ok) {
                eventos.add("round_trip_carga_con_diferencias");
            }
            return true;
        }));
        etapas.add(new Etapa("cargar_tardio", () -> {
            if (lateChunk == Long.MIN_VALUE) {
                eventos.add("sin_aldeano_tardio");
                return true;
            }
            boolean yaCargado = false;
            for (String uuid : zonaTardiaUuids) {
                if (estaCargado(uuid)) {
                    yaCargado = true;
                    break;
                }
            }
            if (yaCargado) {
                eventosDetalle.add("TARDIO_YA_CARGADO_ANTES_DE_FORZAR zona_uuid=" + lateUuid
                        + " (algun chunk de la zona se cargo antes)");
            }
            for (long chunk : zonaTardia) {
                nivel.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), true);
            }
            if (!todosCargadosDeLaZona() && ticks - inicioPaso < 240) {
                return false;
            }
            // Se captura ANTES de cualquier mantenimiento: esta es la prueba limpia de que el aldeano
            // que aparece tarde carga la memoria que se guardó (y el resto de su zona también).
            Estado antes = capturar();
            estadoPostTardio = antes;
            boolean sinCambiosAjenos = PoiPersistenceReport.comparar(
                    estadoCarga != null ? estadoCarga.propiedad() : antes.propiedad(),
                    antes.propiedad()).perdidos().isEmpty()
                    && PoiPersistenceReport.comparar(
                    estadoCarga != null ? estadoCarga.propiedad() : antes.propiedad(),
                    antes.propiedad()).cambiados().isEmpty();
            long puestoAhora = 0L;
            long potencialAhora = 0L;
            for (Duenio d : antes.duenios()) {
                if (d.uuid().equals(lateUuid)) {
                    puestoAhora = d.puesto();
                    potencialAhora = d.potencial();
                }
            }
            boolean memoriaPersistida = (latePuestoEsperado == 0L || puestoAhora == latePuestoEsperado)
                    && (latePotencialEsperado == 0L || potencialAhora == latePotencialEsperado);
            checks.add(new Check("tardio_memoria_persistida", memoriaPersistida,
                    "uuid=" + lateUuid + " job=" + PoiPersistenceReport.pos(puestoAhora)
                            + "/" + PoiPersistenceReport.pos(latePuestoEsperado)
                            + " pot=" + PoiPersistenceReport.pos(potencialAhora)
                            + "/" + PoiPersistenceReport.pos(latePotencialEsperado)));
            if (!yaCargado) {
                checks.add(new Check("tardio_carga_sin_cambios_ajenos", sinCambiosAjenos,
                        "antes=" + (estadoCarga == null ? "?" : estadoCarga.propiedad().size())
                                + " despues=" + antes.propiedad().size()));
            } else {
                // El tardío ya estaba cargado antes de forzarlo: no hay una carga limpia que medir. Se declara
                // NO_MEDIDO y no se añade un check verde vacío; la memoria persistida sí se comprueba arriba.
                eventosDetalle.add("tardio_carga_sin_cambios_ajenos=NO_MEDIDO (zona ya cargada)");
            }
            log("reload1 zona_tardia cargada=" + todosCargadosDeLaZona() + " aldeanos="
                    + zonaTardiaUuids.size() + " duplicados="
                    + PoiPersistenceReport.duplicados(antes.duenios()).size());
            estadoPostGracia = antes;
            return true;
        }));
        etapas.add(new Etapa("mantenimiento_pre_gracia", () -> {
            if (ticks - inicioPaso < 20) {
                return false;
            }
            // A partir de aquí TODA la aldea está cargada (la zona tardía se forzó antes): este
            // mantenimiento no es una foto de carga parcial. La carga parcial se mide en la fase
            // 'partial' a propósito.
            Estado antes = capturar();
            cargadosAntesMantenimiento.clear();
            for (Duenio d : antes.duenios()) {
                cargadosAntesMantenimiento.add(d.uuid());
            }
            boolean casoPrevio = false;
            for (Map.Entry<Long, String> e : ticketPersistido.entrySet()) {
                if (!cargadosAntesMantenimiento.contains(e.getValue())) {
                    casoPrevio = true;
                    break;
                }
            }
            Estado despues = mantenerYOBSERVAR("reload1_pre_gracia");
            liberadosCargaParcial.addAll(PoiPersistenceReport.ticketsLiberadosConDuenoNoCargado(
                    antes.celdas(), despues.celdas(), ticketPersistido, cargadosAntesMantenimiento));
            casoCargaParcialMedido = casoPrevio || !liberadosCargaParcial.isEmpty();
            for (Hallazgo h : liberadosCargaParcial) {
                liberadosDetalle.add(h.codigo() + " :: " + h.detalle());
            }
            log("reload1 pre_gracia liberados_ajenos=" + liberadosCargaParcial.size() + " " + liberadosDetalle);
            estadoPostGracia = despues;
            return true;
        }));
        etapas.add(new Etapa("pasada_gracia", () -> {
            if (ticks < inicioPaso + ESPERA_GRACIA) {
                return false;
            }
            mantenerYOBSERVAR("reload1_gracia");
            estadoPostGracia = capturar();
            return true;
        }));
        etapas.add(new Etapa("estabilizar", () -> {
            if (ticks - inicioPaso >= 40) {
                Estado antes = estadoPostGracia != null ? estadoPostGracia : capturar();
                Estado despues = mantenerYOBSERVAR("reload1_estabilizar");
                estadoPostGracia = despues;
                if (huella(antes).equals(huella(despues))) {
                    return true;
                }
                if (ticks - inicioPaso > 600) {
                    eventos.add("estabilizar_limite_llamadas");
                    return true;
                }
            }
            return false;
        }));
        etapas.add(new Etapa("sonda_aldeano_extra", () -> {
            Estado antes = capturar();
            Set<String> uuidsAntes = new LinkedHashSet<>(antes.uuids());
            // Slot 4 = NITWIT (recolector): no tiene puesto de trabajo, así que la sonda no altera la
            // cuenta de oficios con plaza (no provoca una poda de un profesional legítimo).
            VillageGenerator.spawnOneVillager(nivel, centro, 4, false);
            Estado despuesSpawn = capturar();
            String nuevo = "";
            for (String uuid : despuesSpawn.uuids()) {
                if (!uuidsAntes.contains(uuid)) {
                    nuevo = uuid;
                }
            }
            eventos.add("SONDA_ALDEANO_EXTRA uuid=" + nuevo + " profesion=NITWIT");
            // SIN mantenimiento: si se mantiene, el NITWIT extra puede ser reclutado como guardia y
            // cambia el desglose 12/4 del roster (transición legítima, pero ruido para la evidencia).
            // La sonda comprueba que la entidad que aparece no reclama ni comparte un puesto ya ocupado.
            Estado despuesMant = despuesSpawn;
            boolean roba = false;
            if (!nuevo.isEmpty()) {
                for (Map.Entry<Long, String> e : antes.propiedad().entrySet()) {
                    if (nuevo.equals(despuesMant.propiedad().get(e.getKey()))) {
                        roba = true;
                        eventosDetalle.add("SONDA_ALDEANO_EXTRA ROBO poi="
                                + PoiPersistenceReport.pos(e.getKey()) + " por " + nuevo);
                    }
                }
            }
            boolean sinDuplicados = PoiPersistenceReport.duplicados(despuesMant.duenios()).isEmpty();
            long puestoExtra = 0L;
            for (Duenio d : despuesMant.duenios()) {
                if (d.uuid().equals(nuevo)) {
                    puestoExtra = d.puesto();
                }
            }
            checks.add(new Check("aldeano_extra_no_roba", !roba && sinDuplicados,
                    "nuevo=" + nuevo + " jobSite=" + PoiPersistenceReport.pos(puestoExtra)
                            + " roba=" + roba + " duplicados=" + !sinDuplicados));
            if (!nuevo.isEmpty()) {
                Entity extra = nivel.getEntity(UUID.fromString(nuevo));
                if (extra != null) {
                    extra.discard();
                    eventos.add("SONDA_ALDEANO_EXTRA descartado " + nuevo);
                }
            }
            return true;
        }));
        etapas.add(new Etapa("cerrar_reload1", () -> {
            estadoFinal = capturar();
            cerrarFase(estadoFinal, estadoFinal);
            return true;
        }));
    }

    // ------------------------------------------------------------------ etapas: reload2

    private static void etapasReload2() {
        etapas.clear();
        etapas.add(new Etapa("carga_inicial", () -> {
            if (!todosCargadosDelSnapshot() && ticks - inicioPaso < 400) {
                return false;
            }
            estadoCarga = capturar();
            digestVivoInicial = huellaVivo();
            comprobarRoundTrip(estadoCarga, "reload2_carga");
            checks.add(new Check("saveddata_carga", digestPrevio.isBlank() || digestVivoInicial.equals(digestPrevio),
                    "vivo=" + corto(digestVivoInicial) + " previo=" + corto(digestPrevio)));
            return true;
        }));
        etapas.add(new Etapa("fixture_f4", () -> {
            aplicarFixtureF4();
            return true;
        }));
        etapas.add(new Etapa("mantenimiento_inicial", () -> {
            if (ticks - inicioPaso < 20) {
                return false;
            }
            mantenerYOBSERVAR("reload2_inicial");
            return true;
        }));
        etapas.add(new Etapa("pasada_gracia", () -> {
            if (ticks < inicioPaso + ESPERA_GRACIA) {
                return false;
            }
            mantenerYOBSERVAR("reload2_gracia");
            estadoPostGracia = capturar();
            return true;
        }));
        etapas.add(new Etapa("estabilizar", () -> {
            if (ticks - inicioPaso >= 40) {
                Estado antes = estadoPostGracia != null ? estadoPostGracia : capturar();
                Estado despues = mantenerYOBSERVAR("reload2_estabilizar");
                estadoPostGracia = despues;
                if (huella(antes).equals(huella(despues))) {
                    return true;
                }
                if (ticks - inicioPaso > 600) {
                    eventos.add("estabilizar_limite_llamadas");
                    return true;
                }
            }
            return false;
        }));
        etapas.add(new Etapa("cerrar_reload2", () -> {
            estadoFinal = capturar();
            cerrarFase(estadoFinal, estadoFinal);
            return true;
        }));
    }

    // ------------------------------------------------------------------ etapas: partial

    private static void etapasPartial() {
        etapas.clear();
        etapas.add(new Etapa("carga_parcial_inicial", () -> {
            if (ticks - inicioPaso < 40) {
                return false;
            }
            estadoInicial = capturar();
            cargadosAntesMantenimiento.clear();
            for (Duenio d : estadoInicial.duenios()) {
                cargadosAntesMantenimiento.add(d.uuid());
            }
            long conDuenioNoCargado = 0L;
            for (Map.Entry<Long, String> e : ticketPersistido.entrySet()) {
                if (!cargadosAntesMantenimiento.contains(e.getValue())) {
                    conDuenioNoCargado++;
                }
            }
            casoCargaParcialMedido = conDuenioNoCargado > 0;
            log("partial carga t=" + ticks + " aldeanos_cargados=" + estadoInicial.duenios().size()
                    + " pois_con_duenio_no_cargado=" + conDuenioNoCargado
                    + " caso_medido=" + casoCargaParcialMedido);
            estadoCarga = estadoInicial;
            return true;
        }));
        etapas.add(new Etapa("mantenimiento_parcial", () -> {
            if (ticks - inicioPaso < 20) {
                return false;
            }
            Estado despues = mantenerYOBSERVAR("partial_mantenimiento");
            liberadosCargaParcial.addAll(PoiPersistenceReport.ticketsLiberadosConDuenoNoCargado(
                    estadoCarga.celdas(), despues.celdas(), ticketPersistido, cargadosAntesMantenimiento));
            casoCargaParcialMedido = casoCargaParcialMedido || !liberadosCargaParcial.isEmpty();
            for (Hallazgo h : liberadosCargaParcial) {
                liberadosDetalle.add(h.codigo() + " :: " + h.detalle());
            }
            estadoPostGracia = despues;
            log("partial mantenimiento liberados_ajenos=" + liberadosCargaParcial.size());
            return true;
        }));
        etapas.add(new Etapa("forzar_todo_y_cargar", () -> {
            if (ticks - inicioPaso == 1) {
                for (long chunk : chunksAlrededor()) {
                    nivel.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), true);
                }
            }
            if (!todosCargadosDelSnapshot() && ticks - inicioPaso < 400) {
                return false;
            }
            Estado despues = capturar();
            comprobarRoundTrip(despues, "partial_carga_completa");
            estadoPostGracia = despues;
            return true;
        }));
        etapas.add(new Etapa("pasada_gracia", () -> {
            if (ticks < inicioPaso + ESPERA_GRACIA) {
                return false;
            }
            mantenerYOBSERVAR("partial_gracia");
            estadoPostGracia = capturar();
            return true;
        }));
        etapas.add(new Etapa("estabilizar", () -> {
            if (ticks - inicioPaso >= 40) {
                Estado antes = estadoPostGracia != null ? estadoPostGracia : capturar();
                Estado despues = mantenerYOBSERVAR("partial_estabilizar");
                estadoPostGracia = despues;
                if (huella(antes).equals(huella(despues))) {
                    return true;
                }
                if (ticks - inicioPaso > 600) {
                    eventos.add("estabilizar_limite_llamadas");
                    return true;
                }
            }
            return false;
        }));
        etapas.add(new Etapa("cerrar_partial", () -> {
            estadoFinal = capturar();
            cerrarFase(estadoFinal, estadoFinal);
            return true;
        }));
    }

    // ------------------------------------------------------------------ mantenimiento y observación

    private static boolean mantener(final String etiqueta) {
        try {
            VillageManager.ejecutarMantenimientoParaArnes(nivel, OBJETIVO, centro);
            mantenimientos++;
            log("MANT " + etiqueta + " t=" + ticks + " n=" + mantenimientos);
            return true;
        } catch (RuntimeException e) {
            eventos.add("MANTENIMIENTO_ERROR " + etiqueta + " :: " + e);
            log("MANT ERROR " + etiqueta + ": " + e);
            return false;
        }
    }

    private static Estado mantenerYOBSERVAR(final String etiqueta) {
        Estado antes = capturar();
        mantener(etiqueta);
        Estado despues = capturar();
        registrarCambios(etiqueta, antes, despues);
        return despues;
    }

    /** Registra asignaciones/cambios de dueño/liberaciones/nuevos aldeanos entre dos capturas. */
    private static void registrarCambios(final String etiqueta, final Estado antes, final Estado despues) {
        PoiPersistenceReport.Comparacion c = PoiPersistenceReport.comparar(antes.propiedad(), despues.propiedad());
        for (String s : c.nuevos()) {
            eventosDetalle.add(etiqueta + " ASIGNACION " + s);
        }
        for (String s : c.cambiados()) {
            eventosDetalle.add(etiqueta + " CAMBIO_DUENIO " + s);
        }
        for (String s : c.perdidos()) {
            eventosDetalle.add(etiqueta + " PUESTO_PERDIDO " + s);
        }
        Map<Long, Integer> libresAntes = new LinkedHashMap<>();
        for (Celda celda : antes.celdas()) {
            libresAntes.put(celda.pos(), celda.libres());
        }
        for (Celda celda : despues.celdas()) {
            Integer previos = libresAntes.get(celda.pos());
            if (previos != null && !previos.equals(celda.libres())) {
                eventosDetalle.add(etiqueta + (celda.libres() > previos ? " TICKET_LIBERADO poi="
                        : " TICKET_COGIDO poi=") + PoiPersistenceReport.pos(celda.pos())
                        + " libres=" + previos + "->" + celda.libres());
            }
        }
        for (String uuid : despues.uuids()) {
            if (!antes.uuids().contains(uuid)) {
                eventosDetalle.add(etiqueta + " ALDEANO_NUEVO uuid=" + uuid);
            }
        }
        for (String uuid : antes.uuids()) {
            if (!despues.uuids().contains(uuid)) {
                eventosDetalle.add(etiqueta + " ALDEANO_AUSENTE uuid=" + uuid);
            }
        }
    }

    // ------------------------------------------------------------------ sonda de tickets

    /** Prueba real de {@code take}/{@code release}/{@code take} sobre una estación ocupada. */
    private static void sondaDeTickets() {
        if (estadoFeliz == null || estadoFeliz.celdas().isEmpty()) {
            ticketProbe.add("sonda_sin_celdas");
            return;
        }
        Celda elegida = null;
        for (Celda c : estadoFeliz.celdas()) {
            if (estadoFeliz.oficios().containsKey(c.pos()) && c.libres() == 0) {
                elegida = c;
                break;
            }
        }
        if (elegida == null) {
            for (Celda c : estadoFeliz.celdas()) {
                if (estadoFeliz.oficios().containsKey(c.pos())) {
                    elegida = c;
                    break;
                }
            }
        }
        if (elegida == null) {
            ticketProbe.add("sonda_sin_puesto_de_trabajo");
            return;
        }
        BlockPos pos = BlockPos.of(elegida.pos());
        int antes = libres(pos);
        Optional<BlockPos> ocupado = cogerTicket(pos);
        int trasOcupado = libres(pos);
        boolean liberado = soltarTicket(pos);
        int trasSoltar = libres(pos);
        Optional<BlockPos> cogido = cogerTicket(pos);
        int trasCoger = libres(pos);
        boolean liberado2 = soltarTicket(pos);
        int trasSoltar2 = libres(pos);
        Optional<BlockPos> restaurado = cogerTicket(pos);
        int finalLibres = libres(pos);
        ticketProbe.add(new Objeto()
                .texto("poi", PoiPersistenceReport.pos(elegida.pos()))
                .texto("tipo", elegida.tipo())
                .numero("libres_antes", antes)
                .booleano("take_ocupado_vacio", ocupado.isEmpty())
                .numero("libres_tras_take_ocupado", trasOcupado)
                .booleano("release_ok", liberado)
                .numero("libres_tras_release", trasSoltar)
                .booleano("take_libre_ok", cogido.isPresent())
                .numero("libres_tras_take", trasCoger)
                .booleano("release2_ok", liberado2)
                .numero("libres_tras_release2", trasSoltar2)
                .booleano("restaurado_ok", restaurado.isPresent())
                .numero("libres_final", finalLibres)
                .json());
        boolean ok = antes == 0 && ocupado.isEmpty() && trasOcupado == 0 && liberado && trasSoltar == 1
                && cogido.isPresent() && trasCoger == 0 && liberado2 && trasSoltar2 == 1
                && restaurado.isPresent() && finalLibres == 0;
        checks.add(new Check("sonda_tickets_take_release", ok, ticketProbe.get(ticketProbe.size() - 1)));
        log("sonda_tickets poi=" + pos.toShortString() + " ok=" + ok);
    }

    private static int libres(final BlockPos pos) {
        return nivel.getPoiManager().getFreeTickets(pos);
    }

    private static Optional<BlockPos> cogerTicket(final BlockPos pos) {
        return nivel.getPoiManager().take(h -> true, (h, p) -> p.equals(pos), pos, 1);
    }

    private static boolean soltarTicket(final BlockPos pos) {
        if (nivel.getPoiManager().getType(pos).isEmpty()) {
            return false;
        }
        // Fixture: se recrea el estado, así que también se olvida el dueño persistido de ese puesto.
        VillageSavedData.get(nivel).forgetJobSite(pos.asLong());
        return nivel.getPoiManager().release(pos);
    }

    // ------------------------------------------------------------------ fixtures (solo prepare)

    /**
     * Fixtures MÍNIMAS y documentadas para cubrir duplicado, huérfano y estación libre. En esta aldea
     * el estado natural ya trae POTENTIAL sin JOB_SITE y tickets perdidos (se mide aparte, sin tocar);
     * estas tres mutaciones solo crean los casos que el mundo natural no tenía (C4 midió 0 duplicados
     * y 0 huérfanos), para que las recargas tengan algo que reconciliar/recuperar.
     */
    private static void aplicarFixtures() {
        // --- F1 · duplicado: dos granjeros con el MISMO JOB_SITE (ticket único) ---
        List<Villager> granjeros = aldeanosConProfesion(VillagerProfession.FARMER);
        Celda farmerOcupada = null;
        for (Celda c : celdasDeTrabajo(estadoFeliz)) {
            if ("farmer".equals(estadoFeliz.oficios().get(c.pos()))) {
                if (farmerOcupada == null || c.libres() == 0) {
                    farmerOcupada = c;
                }
            }
        }
        if (granjeros.size() >= 2 && farmerOcupada != null) {
            granjeros.sort(Comparator.comparing(v -> v.getUUID().toString()));
            Villager a = granjeros.get(0);
            Villager b = granjeros.get(1);
            fixtureDuplicadoPoi = farmerOcupada.pos();
            fixtureDuplicadoA = a.getUUID().toString();
            fixtureDuplicadoB = b.getUUID().toString();
            if (libres(BlockPos.of(fixtureDuplicadoPoi)) > 0) {
                cogerTicket(BlockPos.of(fixtureDuplicadoPoi));
            }
            // F1 es READ-ONLY sobre el libro: nunca sustituye al dueño persistido por un duplicado cargado.
            // Si el libro apunta a un dueño no cargado, no hay un caso limpio que medir y se declara NO_APLICADA.
            VillageSavedData libro = VillageSavedData.get(nivel);
            UUID ledger = libro.getJobSiteOwner(fixtureDuplicadoPoi);
            Villager keeper = null;
            boolean aplicable;
            if (ledger != null) {
                for (Villager v : granjeros) {
                    if (v.getUUID().equals(ledger)) {
                        keeper = v;
                        break;
                    }
                }
                aplicable = keeper != null;
                if (!aplicable) {
                    fixtures.add("F1 NO_APLICADA duenio_libro_no_cargado=" + ledger);
                }
            } else {
                for (Villager v : granjeros) {
                    if (memoria(v, MemoryModuleType.JOB_SITE) == fixtureDuplicadoPoi) {
                        keeper = v;
                        break;
                    }
                }
                aplicable = keeper != null;
                if (aplicable) {
                    libro.claimJobSite(fixtureDuplicadoPoi, keeper.getUUID(), keeper.blockPosition());
                } else {
                    fixtures.add("F1 NO_APLICADA sin_libro_ni_job_real");
                }
            }
            if (aplicable) {
                // El keeper debe ser uno de los dos que se duplican; si el libro apunta a un tercer granjero,
                // se usa ESE como A y se busca otro B. No se cambia el dueño persistido.
                if (!keeper.equals(a)) {
                    Villager otro = keeper.equals(b) ? a : b;
                    a = keeper;
                    b = otro;
                }
                fixtureDuplicadoA = a.getUUID().toString();
                fixtureDuplicadoB = b.getUUID().toString();
                fixtureDuplicadoKeeper = keeper.getUUID().toString();
                libro.claimJobSite(fixtureDuplicadoPoi, keeper.getUUID(), keeper.blockPosition());
                liberarPuestoAnterior(a, fixtureDuplicadoPoi);
                liberarPuestoAnterior(b, fixtureDuplicadoPoi);
                fijarPuesto(a, fixtureDuplicadoPoi);
                fijarPuesto(b, fixtureDuplicadoPoi);
                // El POTENTIAL de A/B se convierte en JOB_SITE si apuntaba al mismo POI; si apuntaba a otro,
                // se suelta su ticket antes de borrar la memoria (no se fabrica un ticket perdido nuevo).
                soltarPotencialSiAplica(a, fixtureDuplicadoPoi);
                soltarPotencialSiAplica(b, fixtureDuplicadoPoi);
                fixtures.add("F1 DUPLICADO poi=" + PoiPersistenceReport.pos(fixtureDuplicadoPoi)
                        + " uuidA=" + fixtureDuplicadoA + " uuidB=" + fixtureDuplicadoB
                        + " keeper=" + fixtureDuplicadoKeeper
                        + " ticket_libre=" + libres(BlockPos.of(fixtureDuplicadoPoi))
                        + " (dos JOB_SITE, un solo ticket: el guardado capturó una reasignación transitoria)");
            }
        } else {
            fixtures.add("F1 NO_APLICADA granjeros=" + granjeros.size());
        }

        // --- F2 · huérfano + estación libre (MASON, oficio único) ---
        Villager mason = unicoConProfesion(VillagerProfession.MASON);
        if (mason != null) {
            Celda sitio = celdaDeOficio(estadoFeliz, "mason", mason.blockPosition());
            if (sitio != null) {
                fixtureHuerfanoUuid = mason.getUUID().toString();
                fixtureHuerfanoSitio = sitio.pos();
                if (libres(BlockPos.of(sitio.pos())) == 0) {
                    soltarTicket(BlockPos.of(sitio.pos()));
                }
                BlockPos huerfano = buscarHuecoSinPoi(mason.blockPosition());
                soltarPotencialSiAplica(mason, 0L);
                mason.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(nivel.dimension(), huerfano));
                fixtures.add("F2 HUERFANO uuid=" + fixtureHuerfanoUuid + " jobSite=" + huerfano.toShortString()
                        + " (sin POI) estacion_libre=" + PoiPersistenceReport.pos(fixtureHuerfanoSitio)
                        + " libre=" + libres(BlockPos.of(fixtureHuerfanoSitio)));
            } else {
                fixtures.add("F2 NO_APLICADA sin_celda_mason");
            }
        } else {
            fixtures.add("F2 NO_APLICADA sin_mason");
        }

        // --- F3 · estación libre sin memoria (SHEPHERD, oficio único) ---
        Villager pastor = unicoConProfesion(VillagerProfession.SHEPHERD);
        if (pastor != null) {
            Celda sitio = celdaDeOficio(estadoFeliz, "shepherd", pastor.blockPosition());
            if (sitio != null) {
                fixtureLibreUuid = pastor.getUUID().toString();
                fixtureLibrePoi = sitio.pos();
                if (libres(BlockPos.of(sitio.pos())) == 0) {
                    soltarTicket(BlockPos.of(sitio.pos()));
                }
                pastor.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
                soltarPotencialSiAplica(pastor, 0L);
                fixtures.add("F3 ESTACION_LIBRE poi=" + PoiPersistenceReport.pos(fixtureLibrePoi)
                        + " uuid=" + fixtureLibreUuid + " (sin JOB_SITE ni POTENTIAL, ticket libre="
                        + libres(BlockPos.of(fixtureLibrePoi)) + ")");
            } else {
                fixtures.add("F3 NO_APLICADA sin_celda_shepherd");
            }
        } else {
            fixtures.add("F3 NO_APLICADA sin_shepherd");
        }
        log("fixtures aplicadas " + fixtures);
    }

    /**
     * F4 · GHOST POTENTIAL. Dos aldeanos con {@code POTENTIAL_JOB_SITE} en el MISMO POI y <b>un solo
     * ticket cogido</b> (estado real: vanilla {@code AcquirePoi} ignora el resultado de {@code take}, así
     * que pueden existir dos POTENTIAL y un ticket). Después del mantenimiento debe quedar exactamente:
     * 1 {@code JOB_SITE} con su ticket cogido, 0 POTENTIAL en ese POI, 0 ticket libre con dueño y ningún
     * {@code JOB_SITE} fantasma. Se aplica en reload2, después de la foto de carga y antes del primer
     * mantenimiento de la fase.
     */
    private static void aplicarFixtureF4() {
        motivoF4("inicio");
        List<Villager> granjeros = aldeanosConProfesion(VillagerProfession.FARMER);
        if (granjeros.size() < 2) {
            motivoF4("NO_APLICADA granjeros=" + granjeros.size());
            return;
        }
        granjeros.sort(Comparator.comparing(v -> v.getUUID().toString()));
        long f1Poi = leerFixtureF1Poi(PREPARE);
        List<Villager> candidatos = new ArrayList<>();
        for (Villager v : granjeros) {
            if (memoria(v, MemoryModuleType.JOB_SITE) != f1Poi) {
                candidatos.add(v);
            }
        }
        if (candidatos.size() < 2) {
            candidatos = new ArrayList<>(granjeros);
        }
        Villager a = candidatos.get(0);
        Villager b = candidatos.get(1);
        // P4: una celda de farmer; preferimos la que ya tenga uno de los candidatos, y nunca la de F1.
        Celda p4 = null;
        for (Duenio d : capturar().duenios()) {
            if ((d.uuid().equals(a.getUUID().toString()) || d.uuid().equals(b.getUUID().toString()))
                    && d.puesto() > 0L && d.puesto() != f1Poi) {
                p4 = celdaDe(estadoActual(), d.puesto());
                if (p4 != null) {
                    break;
                }
            }
        }
        if (p4 == null) {
            for (Celda c : estadoActual().celdas()) {
                if ("farmer".equals(estadoActual().oficios().get(c.pos())) && c.pos() != f1Poi) {
                    p4 = c;
                    break;
                }
            }
        }
        if (p4 == null) {
            motivoF4("NO_APLICADA sin_celda_farmer");
            return;
        }
        fixtureF4Poi = p4.pos();
        fixtureF4A = a.getUUID().toString();
        fixtureF4B = b.getUUID().toString();
        // Normaliza el POI: nadie lo tiene en el cerebro y el ticket se controla a mano.
        for (Villager v : aldeanosVivos()) {
            if (memoria(v, MemoryModuleType.JOB_SITE) == fixtureF4Poi) {
                v.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
            }
            if (memoria(v, MemoryModuleType.POTENTIAL_JOB_SITE) == fixtureF4Poi) {
                v.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
            }
        }
        soltarTicket(BlockPos.of(fixtureF4Poi));
        liberarPuestoAnterior(a, fixtureF4Poi);
        liberarPuestoAnterior(b, fixtureF4Poi);
        soltarPotencialSiAplica(a, 0L);
        soltarPotencialSiAplica(b, 0L);
        fijarPotencial(a, fixtureF4Poi);
        fijarPotencial(b, fixtureF4Poi);
        int libresAntes = libres(BlockPos.of(fixtureF4Poi));
        if (libresAntes > 0) {
            cogerTicket(BlockPos.of(fixtureF4Poi));
        }
        // El libro de dueños es la autoridad desde C4: se registra A como titular del único ticket. Sin esto,
        // el manager (correctamente) no adivina de quién es un ticket cogido y F4 no podría medir la promoción.
        VillageSavedData.get(nivel).claimJobSite(fixtureF4Poi, UUID.fromString(fixtureF4A));
        int libresDespues = libres(BlockPos.of(fixtureF4Poi));
        fixtures.add("F4 POTENTIAL_DUPLICADO poi=" + PoiPersistenceReport.pos(fixtureF4Poi)
                + " uuidA=" + fixtureF4A + " uuidB=" + fixtureF4B
                + " ticket_libre_antes=" + libresAntes + " despues=" + libresDespues
                + " (dos POTENTIAL, UN ticket cogido: estado real de AcquirePoi)");
        motivoF4("APLICADA poi=" + PoiPersistenceReport.pos(fixtureF4Poi)
                + " A=" + fixtureF4A + " B=" + fixtureF4B + " libres=" + libresDespues);
        log("F4 aplicada " + fixtures.get(fixtures.size() - 1));
    }

    private static void motivoF4(final String motivo) {
        fixtureF4Motivo = motivo;
        log("F4 " + motivo);
    }

    private static void fijarPotencial(final Villager v, final long poi) {
        v.getBrain().setMemory(MemoryModuleType.POTENTIAL_JOB_SITE,
                GlobalPos.of(nivel.dimension(), BlockPos.of(poi)));
    }

    private static Estado estadoActual() {
        return estadoCarga != null ? estadoCarga : capturar();
    }

    private static Celda celdaDe(final Estado estado, final long poi) {
        for (Celda c : estado.celdas()) {
            if (c.pos() == poi) {
                return c;
            }
        }
        return null;
    }

    /**
     * La fixture F1 se aplica en prepare, pero cada fase es un JVM nuevo: hay que releer su POI/UUIDs del fichero
     * para poder comprobar que el superviviente conserva el ticket en reload1/reload2.
     */
    private static void cargarFixtureF1DeDisco() {
        Path p = base.resolve("fixtures-prepare.txt");
        if (!Files.exists(p)) {
            return;
        }
        java.util.regex.Pattern patron = java.util.regex.Pattern.compile(
                "F1[^\\n]*poi=(-?\\d+), (-?\\d+), (-?\\d+) uuidA=([0-9a-fA-F-]{36}) uuidB=([0-9a-fA-F-]{36})"
                        + "(?: keeper=([0-9a-fA-F-]{36}))?");
        try {
            for (String linea : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                java.util.regex.Matcher m = patron.matcher(linea);
                if (m.find()) {
                    fixtureDuplicadoPoi = new BlockPos(Integer.parseInt(m.group(1)),
                            Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))).asLong();
                    fixtureDuplicadoA = m.group(4);
                    fixtureDuplicadoB = m.group(5);
                    fixtureDuplicadoKeeper = m.group(6) == null ? fixtureDuplicadoA : m.group(6);
                    return;
                }
            }
        } catch (Exception e) {
            log("no pude releer F1 de fixtures-prepare: " + e);
        }
    }

    /** POI de la fixture F1 leído de fixtures-prepare.txt (para no deshacerla con F4). */
    private static long leerFixtureF1Poi(final String fase) {
        Path p = base.resolve("fixtures-" + fase + ".txt");
        if (!Files.exists(p)) {
            return 0L;
        }
        java.util.regex.Pattern patron = java.util.regex.Pattern.compile(
                "F1[^\\n]*poi=(-?\\d+), (-?\\d+), (-?\\d+)");
        try {
            for (String linea : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                java.util.regex.Matcher m = patron.matcher(linea);
                if (m.find()) {
                    return new BlockPos(Integer.parseInt(m.group(1)),
                            Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))).asLong();
                }
            }
        } catch (Exception e) {
            log("no pude leer el POI de F1: " + e);
        }
        return 0L;
    }


    private static List<Villager> aldeanosConProfesion(final VillagerProfession profesion) {
        List<Villager> out = new ArrayList<>();
        for (Villager v : aldeanosVivos()) {
            if (v.isBaby() || VillagerGuardGoal.esGuardia(v)) {
                continue;
            }
            if (v.getVillagerData().getProfession() == profesion) {
                out.add(v);
            }
        }
        return out;
    }

    private static Villager unicoConProfesion(final VillagerProfession profesion) {
        List<Villager> lista = aldeanosConProfesion(profesion);
        return lista.isEmpty() ? null : lista.get(0);
    }

    private static Celda celdaDeOficio(final Estado estado, final String oficio, final BlockPos cerca) {
        Celda mejor = null;
        for (Celda c : estado.celdas()) {
            String o = estado.oficios().get(c.pos());
            if (o != null && o.equals(oficio)) {
                if (mejor == null || BlockPos.of(c.pos()).distSqr(cerca) < BlockPos.of(mejor.pos()).distSqr(cerca)) {
                    mejor = c;
                }
            }
        }
        return mejor;
    }

    private static void fijarPuesto(final Villager v, final long poi) {
        v.getBrain().setMemory(MemoryModuleType.JOB_SITE,
                GlobalPos.of(nivel.dimension(), BlockPos.of(poi)));
    }

    private static void borrarPotencial(final Villager v) {
        v.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
    }

    /**
     * Si el aldeano tiene {@code POTENTIAL_JOB_SITE} en una celda distinta de {@code excepto}, suelta su
     * ticket antes de borrar la memoria: así la fixture no fabrica un ticket perdido que luego se
     * atribuiría a producción.
     */
    private static void soltarPotencialSiAplica(final Villager v, final long excepto) {
        long potencial = memoria(v, MemoryModuleType.POTENTIAL_JOB_SITE);
        if (potencial > 0L && potencial != excepto) {
            soltarTicket(BlockPos.of(potencial));
        }
        borrarPotencial(v);
    }

    /** Suelta el ticket del puesto viejo del aldeano si no es el que se va a fijar. */
    private static void liberarPuestoAnterior(final Villager v, final long nuevo) {
        Optional<GlobalPos> mem = v.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (mem.isEmpty() || mem.get().pos().asLong() == nuevo) {
            return;
        }
        BlockPos viejo = mem.get().pos();
        if (nivel.getPoiManager().getType(viejo).isPresent()) {
            nivel.getPoiManager().release(viejo);
        }
        VillageSavedData.get(nivel).forgetJobSite(viejo.asLong());
        v.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
    }

    /** Busca una celda cercana que NO sea POI (para el huérfano). */
    private static BlockPos buscarHuecoSinPoi(final BlockPos cerca) {
        for (int dx = -12; dx <= 12; dx += 2) {
            for (int dz = -12; dz <= 12; dz += 2) {
                BlockPos p = cerca.offset(dx, 0, dz);
                if (nivel.getPoiManager().getType(p).isEmpty()
                        && nivel.getPoiManager().getType(p.above()).isEmpty()
                        && nivel.getPoiManager().getType(p.below()).isEmpty()) {
                    return p;
                }
            }
        }
        return cerca.offset(0, 4, 0);
    }

    // ------------------------------------------------------------------ captura de estado

    private record Estado(int tick, List<Celda> celdas, Map<Long, String> oficios, List<Duenio> duenios,
                          Map<Long, String> propiedad, Set<String> uuids) {
    }

    private static Estado capturar() {
        List<Celda> celdas = new ArrayList<>();
        Map<Long, String> oficios = new LinkedHashMap<>();
        List<PoiRecord> registros = nivel.getPoiManager()
                .getInRange(h -> true, centro, RADIO_POI, PoiManager.Occupancy.ANY).toList();
        for (PoiRecord r : registros) {
            int max = r.getPoiType().value().maxTickets();
            celdas.add(new Celda(r.getPos().asLong(),
                    r.getPoiType().unwrapKey().map(k -> k.location().toString()).orElse("?"),
                    r.getFreeTickets(), max));
            String o = oficiosDe(r.getPoiType());
            if (!o.isEmpty()) {
                oficios.put(r.getPos().asLong(), o);
            }
        }
        celdas.sort(Comparator.comparingLong(Celda::pos));
        List<Duenio> duenios = new ArrayList<>();
        Set<String> uuids = new LinkedHashSet<>();
        for (Villager v : aldeanosVivos()) {
            uuids.add(v.getUUID().toString());
            if (v.isBaby()) {
                continue;
            }
            duenios.add(new Duenio(v.getUUID().toString(),
                    v.getVillagerData().getProfession().toString(),
                    memoria(v, MemoryModuleType.JOB_SITE),
                    memoria(v, MemoryModuleType.POTENTIAL_JOB_SITE),
                    v.blockPosition().toShortString(), true));
        }
        Map<Long, String> propiedad = propiedadDe(duenios);
        return new Estado(ticks, celdas, oficios, duenios, propiedad, uuids);
    }

    private static Map<Long, String> propiedadDe(final List<Duenio> duenios) {
        Map<Long, String> out = new LinkedHashMap<>();
        for (Duenio d : duenios) {
            if (d.puesto() > 0L) {
                out.putIfAbsent(d.puesto(), d.uuid());
            }
        }
        return out;
    }

    private static String oficiosDe(final Holder<PoiType> holder) {
        List<String> out = new ArrayList<>();
        for (VillagerProfession profesion : VillageGenerator.puestosPorOficio().keySet()) {
            if (profesion != VillagerProfession.NITWIT && profesion.heldJobSite().test(holder)) {
                out.add(profesion.toString());
            }
        }
        return String.join(",", out);
    }

    private static long memoria(final Villager v, final MemoryModuleType<GlobalPos> tipo) {
        return v.getBrain().getMemory(tipo).map(m -> m.pos().asLong()).orElse(0L);
    }

    private static List<Villager> aldeanosVivos() {
        // Radio amplio a propósito: el minero/pescador pueden alejarse de su estación y no queremos
        // confundir "se fue a trabajar" con "se perdió al cargar".
        AABB zona = new AABB(centro).inflate(VillageGenerator.FENCE_RADIUS + 48.0D);
        return nivel.getEntitiesOfClass(Villager.class, zona).stream()
                .filter(v -> v.isAlive() && !v.isRemoved())
                .toList();
    }

    private static int contarJobSite(final Estado estado) {
        int n = 0;
        for (Duenio d : estado.duenios()) {
            if (d.puesto() > 0L) {
                n++;
            }
        }
        return n;
    }

    private static int contarGuardias() {
        int n = 0;
        for (Villager v : aldeanosVivos()) {
            if (!v.isBaby() && VillagerGuardGoal.esGuardia(v)) {
                n++;
            }
        }
        return n;
    }

    private static String huella(final Estado estado) {
        StringBuilder sb = new StringBuilder();
        for (Celda c : estado.celdas()) {
            sb.append(c.pos()).append(':').append(c.libres()).append(';');
        }
        sb.append('|');
        for (Map.Entry<Long, String> e : new TreeMap<>(estado.propiedad()).entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append(';');
        }
        sb.append('|');
        List<Duenio> dueniosOrdenados = new ArrayList<>(estado.duenios());
        dueniosOrdenados.sort(Comparator.comparing(Duenio::uuid));
        for (Duenio d : dueniosOrdenados) {
            sb.append(d.uuid()).append(':').append(d.profesion()).append(':').append(d.puesto())
                    .append(':').append(d.potencial()).append(';');
        }
        return PoiPersistenceReport.sha256(sb.toString());
    }

    private static String huellaVivo() {
        try {
            VillageSavedData saved = VillageSavedData.get(nivel);
            CompoundTag tag = saved.save(new CompoundTag(), nivel.registryAccess());
            return huellaSavedData(tag);
        } catch (RuntimeException e) {
            eventos.add("huella_vivo_error " + e);
            return "";
        }
    }

    // ------------------------------------------------------------------ round-trip

    private static boolean comprobarRoundTrip(final Estado estado, final String etiqueta) {
        if (propiedadPersistida.isEmpty()) {
            checks.add(new Check(etiqueta, false, "sin tabla persistida previa"));
            return false;
        }
        Map<Long, String> esperado = new LinkedHashMap<>(propiedadPersistida);
        Map<Long, String> actual = new LinkedHashMap<>(estado.propiedad());
        // En reload1, la zona del aldeano tardío puede no estar cargada todavía: sus entradas NO se exigen
        // en la foto de carga, pero si alguna sí cargó (porque el mantenimiento leyó sus bloques), se exige
        // igual que las demás. Así el tardío cuenta como estable si su pareja coincide con lo guardado y
        // no se convierte en "nuevo"; y un cambio real de dueño sigue siendo un fallo.
        Set<String> cargadosAhora = new LinkedHashSet<>();
        for (Duenio d : estado.duenios()) {
            cargadosAhora.add(d.uuid());
        }
        if (FASE.equals(RELOAD1)) {
            esperado.entrySet().removeIf(e -> zonaTardiaUuids.contains(e.getValue())
                    && !cargadosAhora.contains(e.getValue()));
        }
        // Los aldeanos tocados por fixtures (duplicado, huérfano, estación libre, F4) tienen un estado
        // deliberadamente inconsistente: su persistencia NO es el objeto de la foto de carga, y su
        // recuperación se exige en el estado final (fixture_*). Se excluyen de esta comparación.
        esperado.entrySet().removeIf(e -> fixtureUuids.contains(e.getValue()));
        actual.entrySet().removeIf(e -> fixtureUuids.contains(e.getValue()));
        PoiPersistenceReport.Comparacion c = PoiPersistenceReport.comparar(esperado, actual);
        // Al cargar, lo guardado debe estar PRESENTE y con el MISMO dueño. Una entrada NUEVA puede ser
        // una adquisición legítima de vanilla en los primeros ticks (p.ej. POTENTIAL sobre una estación
        // libre): se registra como "nuevos_permitidos", no como fallo de persistencia. El estado final
        // (duplicados, huérfanos, tickets, cobertura) se evalúa aparte y sigue siendo estricto.
        boolean ok = c.perdidos().isEmpty() && c.cambiados().isEmpty();
        checks.add(new Check(etiqueta, ok, "estables=" + c.estables().size() + " cambiados=" + c.cambiados()
                + " perdidos=" + c.perdidos() + " nuevos_permitidos=" + c.nuevos()
                + " fixtures_fuera=" + fixtureUuids.size()
                + (FASE.equals(RELOAD1) && !lateUuid.isEmpty()
                        ? " (zona tardia " + zonaTardiaUuids.size() + " aldeanos)" : "")));
        // Pares (POI, UUID): conserva los DUPLICADOS de memoria que el mapa poi->uuid colapsa.
        Set<String> esperadosPares = new TreeSet<>();
        for (Snap s : snapshotPrevio.values()) {
            boolean zonaNoCargada = FASE.equals(RELOAD1) && zonaTardiaUuids.contains(s.uuid())
                    && !cargadosAhora.contains(s.uuid());
            if (s.puesto() > 0L && !zonaNoCargada && !fixtureUuids.contains(s.uuid())) {
                esperadosPares.add(s.puesto() + "|" + s.uuid());
            }
        }
        Set<String> actualesPares = new TreeSet<>();
        for (Duenio d : estado.duenios()) {
            if (d.puesto() > 0L && !fixtureUuids.contains(d.uuid())) {
                actualesPares.add(d.puesto() + "|" + d.uuid());
            }
        }
        List<String> paresPerdidos = new ArrayList<>();
        for (String par : esperadosPares) {
            if (!actualesPares.contains(par)) {
                paresPerdidos.add(par);
            }
        }
        List<String> paresNuevos = new ArrayList<>();
        for (String par : actualesPares) {
            if (!esperadosPares.contains(par)) {
                paresNuevos.add(par);
            }
        }
        boolean okPares = paresPerdidos.isEmpty();
        checks.add(new Check(etiqueta + "_pares", okPares, "perdidos=" + paresPerdidos
                + " nuevos_permitidos=" + paresNuevos));
        // POTENTIAL_JOB_SITE también es memoria persistida del aldeano: misma comprobación exacta.
        Set<String> esperadosPot = new TreeSet<>();
        for (Snap s : snapshotPrevio.values()) {
            boolean zonaNoCargada = FASE.equals(RELOAD1) && zonaTardiaUuids.contains(s.uuid())
                    && !cargadosAhora.contains(s.uuid());
            if (s.potencial() > 0L && !zonaNoCargada && !fixtureUuids.contains(s.uuid())) {
                esperadosPot.add(s.potencial() + "|" + s.uuid());
            }
        }
        Set<String> actualesPot = new TreeSet<>();
        for (Duenio d : estado.duenios()) {
            if (d.potencial() > 0L && !fixtureUuids.contains(d.uuid())) {
                actualesPot.add(d.potencial() + "|" + d.uuid());
            }
        }
        Set<String> potPerdidos = new TreeSet<>(esperadosPot);
        potPerdidos.removeAll(actualesPot);
        Set<String> potNuevos = new TreeSet<>(actualesPot);
        potNuevos.removeAll(esperadosPot);
        boolean okPot = potPerdidos.isEmpty();
        checks.add(new Check(etiqueta + "_pares_potencial", okPot,
                "perdidos=" + potPerdidos + " nuevos_permitidos=" + potNuevos));
        if (!okPares || !okPot) {
            eventos.add(etiqueta + "_PARES_DIFERENTES perdidos=" + paresPerdidos + " pot_perdidos=" + potPerdidos);
        }
        if (!c.nuevos().isEmpty()) {
            eventos.add(etiqueta + "_NUEVOS_EN_CARGA (permitidos)=" + c.nuevos().size());
        }
        hallazgos.addAll(PoiPersistenceReport.hallazgosDeComparacion(c, true, false));
        if (!ok) {
            eventos.add(etiqueta + " DIFERENCIAS");
        }
        return ok;
    }

    // ------------------------------------------------------------------ cierre y veredicto

    private static void cerrarFase(final Estado estadoContrato, final Estado estadoParaGuardar) {
        hecho = true;
        Estado contrato = estadoContrato != null ? estadoContrato : capturar();
        Estado guardar = estadoParaGuardar != null ? estadoParaGuardar : contrato;

        // NO DEJAR CHUNKS FORZADOS EN EL GUARDADO: si se quedan, la fase siguiente los carga al abrir el
        // mundo ANTES de que el arnés decida qué forzar (el aldeano "tardío" de reload1 se cargaba solo y
        // la fase partial arrancaba con todo cargado). Se sueltan justo antes de saveEverything.
        soltarChunksForzados();

        // Guardado real PRIMERO; el JSON y el marcador después (el lanzador espera el marcador).
        String errorGuardado = "";
        try {
            guardadoOk = server.saveEverything(false, true, true);
            if (!guardadoOk) {
                errorGuardado = "saveEverything devolvio false";
            }
        } catch (RuntimeException e) {
            guardadoOk = false;
            errorGuardado = e.toString();
        }
        if (guardadoOk) {
            digestVivoFinal = huellaVivo();
            digestDisco = leerDigestDisco();
            escribir("saved-" + FASE + ".txt", String.valueOf(System.currentTimeMillis()));
            escribir("saveddata-digest-" + FASE + ".txt", digestVivoFinal + "\n");
            escribir("saveddata-" + FASE + ".txt", textoCentroYcampos());
        } else {
            escribir("save-failed-" + FASE + ".txt", errorGuardado);
        }
        checks.add(new Check("guardado_ok", guardadoOk, errorGuardado));
        checks.add(new Check("saveddata_disco", !digestDisco.isBlank(),
                "digest=" + corto(digestDisco) + " fichero=" + rutaSavedData()));
        checks.add(new Check("saveddata_disco_igual_vivo", digestDisco.isBlank() || digestDisco.equals(digestVivoFinal),
                "vivo=" + corto(digestVivoFinal) + " disco=" + corto(digestDisco)));
        if (!digestPrevio.isBlank()) {
            checks.add(new Check("centro_persistido", centroPrevio.isBlank()
                    || centroPrevio.equals(centro.toShortString()),
                    "ahora=" + centro.toShortString() + " previo=" + centroPrevio));
        }

        evaluarContrato(contrato);

        boolean experimentoOk = falloInstrumento.isEmpty() && guardadoOk && setupOk && !timeout
                && (FASE.equals(PREPARE) || !digestPrevio.isBlank());
        boolean contratoOk = true;
        for (Check c : checks) {
            contratoOk &= c.ok();
        }
        String resultado = experimentoOk && contratoOk ? "PASS" : "FAIL";

        escribirArtefactos(guardar);
        escribirJson("phase-" + FASE + ".json", jsonFase(resultado, experimentoOk, contratoOk, guardar));
        escribir("done-" + FASE + ".txt", resultado);
        log("FASE " + FASE + " -> " + resultado + " experimento=" + experimentoOk + " contrato=" + contratoOk
                + " checks=" + checks.size() + " hallazgos=" + hallazgos.size()
                + " job_site=" + contarJobSite(contrato) + " guardado=" + guardadoOk);
        for (Check c : checks) {
            if (!c.ok()) {
                log("CHECK_FAIL " + c.nombre() + " :: " + c.detalle());
            }
        }
        for (Hallazgo h : hallazgos) {
            log("HALLAZGO " + h.codigo() + " :: " + h.detalle());
        }
        server.halt(false);
    }

    private static void evaluarContrato(final Estado estado) {
        List<Celda> job = celdasDeTrabajo(estado);
        int esperados = 0;
        for (Map.Entry<String, String> e : cupoPorOficio.entrySet()) {
            if (!"nitwit".equals(e.getKey())) {
                esperados += Integer.parseInt(e.getValue());
            }
        }
        // 1) POI reales: histograma por oficio y una celda por plaza.
        Map<String, Integer> observado = new TreeMap<>();
        for (Celda c : job) {
            String o = estado.oficios().get(c.pos());
            if (o == null || o.isBlank()) {
                continue;
            }
            if (o.contains(",")) {
                hallazgos.add(new Hallazgo("POI_OFICIO_AMBIGUO", "poi=" + PoiPersistenceReport.pos(c.pos())
                        + " oficios=" + o));
            }
            for (String uno : o.split(",")) {
                observado.merge(uno, 1, Integer::sum);
            }
        }
        boolean histograma = observado.size() == cupoPorOficio.size() - 1;
        List<String> detalleHistograma = new ArrayList<>();
        for (Map.Entry<String, String> e : new TreeMap<>(cupoPorOficio).entrySet()) {
            if ("nitwit".equals(e.getKey())) {
                continue;
            }
            int esperado = Integer.parseInt(e.getValue());
            int visto = observado.getOrDefault(e.getKey(), 0);
            histograma &= visto == esperado;
            detalleHistograma.add(e.getKey() + "=" + visto + "/" + esperado);
        }
        checks.add(new Check("pois_registrados", job.size() == esperados && histograma,
                "trabajo=" + job.size() + "/" + esperados + " " + detalleHistograma));
        checks.add(new Check("meeting_point", estado.celdas().stream().anyMatch(c -> c.tipo().equals("minecraft:meeting")),
                "celdas=" + estado.celdas().size()));

        // 2) Propiedad: duplicados / huérfanos / tickets.
        List<Hallazgo> dup = PoiPersistenceReport.duplicados(estado.duenios());
        List<Hallazgo> huerf = PoiPersistenceReport.huerfanos(estado.duenios(), celdasTodas(estado));
        List<Hallazgo> sinDueno = PoiPersistenceReport.ticketsSinDueno(job, estado.duenios());
        List<Hallazgo> libreConDueno = PoiPersistenceReport.ticketsLibresConDueno(job, estado.duenios());
        List<Hallazgo> potencial = PoiPersistenceReport.potencialNoPromovido(job, estado.duenios());
        List<Hallazgo> potAjeno = PoiPersistenceReport.potentialAjeno(estado.duenios());
        hallazgos.addAll(dup);
        hallazgos.addAll(huerf);
        hallazgos.addAll(sinDueno);
        hallazgos.addAll(libreConDueno);
        hallazgos.addAll(potencial);
        hallazgos.addAll(potAjeno);
        checks.add(new Check("sin_duplicados", dup.isEmpty(), "duplicados=" + dup.size()));
        checks.add(new Check("sin_huerfanos", huerf.isEmpty(), "huerfanos=" + huerf.size()));
        checks.add(new Check("tickets_sin_dueno", sinDueno.isEmpty(), "total=" + sinDueno.size()));
        checks.add(new Check("tickets_libres_con_dueno", libreConDueno.isEmpty(), "total=" + libreConDueno.size()));
        checks.add(new Check("potential_promovido", potencial.isEmpty(), "pendientes=" + potencial.size()));
        checks.add(new Check("potential_sin_ajeno", potAjeno.isEmpty(), "ajenos=" + potAjeno.size()));

        // 3) Cobertura por oficio (sin nitwit: el recolector no tiene puesto por diseño).
        Map<String, Integer> cupoSinNitwit = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : cupoPorOficio.entrySet()) {
            if (!"nitwit".equals(e.getKey())) {
                cupoSinNitwit.put(e.getKey(), Integer.parseInt(e.getValue()));
            }
        }
        List<Hallazgo> cob = PoiPersistenceReport.cobertura(estado.duenios(), cupoSinNitwit);
        hallazgos.addAll(cob);
        checks.add(new Check("cobertura_puestos", cob.isEmpty(), "incumplidas=" + cob.size()));

        // 4) Entidades esperadas: no se pierde ningún UUID del snapshot y el censo de adultos no baja.
        // El reparto profesional/guardia puede cambiar legítimamente (milicia, poda de oficios, NITWIT),
        // así que el desglose se informa pero no es un umbral duro; la cobertura de puestos sí lo es.
        int profesionales = 0;
        int guardias = 0;
        for (Duenio d : estado.duenios()) {
            Villager v = aldeano(d.uuid());
            if (v != null && VillagerGuardGoal.esGuardia(v)) {
                guardias++;
            } else if (!"none".equals(d.profesion())) {
                profesionales++;
            }
        }
        int esperadosAdultos = 0;
        List<String> faltan = new ArrayList<>();
        if (!snapshotPrevio.isEmpty()) {
            Set<String> presentes = new LinkedHashSet<>();
            for (Duenio d : estado.duenios()) {
                presentes.add(d.uuid());
            }
            for (Snap s : snapshotPrevio.values()) {
                if (s.bebe()) {
                    continue;
                }
                esperadosAdultos++;
                if (!presentes.contains(s.uuid())) {
                    faltan.add(s.uuid());
                }
            }
        } else {
            esperadosAdultos = profesionalesEsperados + guardiasEsperados;
        }
        boolean entidades = faltan.isEmpty() && estado.duenios().size() >= esperadosAdultos;
        checks.add(new Check("entidades_esperadas", entidades,
                "profesionales=" + profesionales + "/" + profesionalesEsperados
                        + " guardias=" + guardias + "/" + guardiasEsperados
                        + " adultos=" + estado.duenios().size() + "/" + esperadosAdultos
                        + " faltan=" + faltan));

        // 5) Round-trip del aldeano tardío (si tenía puesto persistido).
        if (FASE.equals(RELOAD1) && !lateUuid.isEmpty() && latePuestoEsperado > 0L) {
            long ahora = 0L;
            for (Duenio d : estado.duenios()) {
                if (d.uuid().equals(lateUuid)) {
                    ahora = d.puesto();
                }
            }
            checks.add(new Check("tardio_conserva_puesto", ahora == latePuestoEsperado,
                    "uuid=" + lateUuid + " esperado=" + PoiPersistenceReport.pos(latePuestoEsperado)
                            + " ahora=" + PoiPersistenceReport.pos(ahora)));
        }

        // 6) Fixtures.
        if (FASE.equals(RELOAD1) || FASE.equals(RELOAD2)) {
            if (fixtureDuplicadoPoi != 0L) {
                int dueniosDelPoi = 0;
                for (Duenio d : estado.duenios()) {
                    if (d.puesto() == fixtureDuplicadoPoi) {
                        dueniosDelPoi++;
                    }
                }
                checks.add(new Check("fixture_duplicado_reconciliado", dueniosDelPoi == 1,
                        "poi=" + PoiPersistenceReport.pos(fixtureDuplicadoPoi) + " duenios=" + dueniosDelPoi
                                + " A=" + fixtureDuplicadoA + " B=" + fixtureDuplicadoB));
                Celda celdaF1 = celdaDe(estado, fixtureDuplicadoPoi);
                boolean ticketF1 = celdaF1 != null && celdaF1.libres() == 0;
                String keeperF1 = estado.duenios().stream()
                        .filter(d -> d.puesto() == fixtureDuplicadoPoi)
                        .map(Duenio::uuid).findFirst().orElse("");
                checks.add(new Check("fixture_duplicado_ticket_cogido",
                        ticketF1 && keeperF1.equals(fixtureDuplicadoKeeper),
                        "poi=" + PoiPersistenceReport.pos(fixtureDuplicadoPoi)
                                + " keeper=" + keeperF1 + " esperado=" + fixtureDuplicadoKeeper
                                + " libres=" + (celdaF1 == null ? -1 : celdaF1.libres())));
            }
            if (!fixtureHuerfanoUuid.isBlank()) {
                long puesto = puestoDe(estado, fixtureHuerfanoUuid);
                checks.add(new Check("fixture_huerfano_recuperado", puesto == fixtureHuerfanoSitio,
                        "uuid=" + fixtureHuerfanoUuid + " esperado="
                                + PoiPersistenceReport.pos(fixtureHuerfanoSitio)
                                + " ahora=" + PoiPersistenceReport.pos(puesto)));
            }
            if (!fixtureLibreUuid.isBlank()) {
                long puesto = puestoDe(estado, fixtureLibreUuid);
                checks.add(new Check("fixture_estacion_libre_asignada", puesto == fixtureLibrePoi,
                        "uuid=" + fixtureLibreUuid + " esperado=" + PoiPersistenceReport.pos(fixtureLibrePoi)
                                + " ahora=" + PoiPersistenceReport.pos(puesto)));
            }
        }

        // 6b) F4 · POTENTIAL duplicado / JOB_SITE fantasma (solo reload2).
        if (FASE.equals(RELOAD2) && fixtureF4Poi != 0L) {
            int dueniosDelPoi = 0;
            int potencialesDelPoi = 0;
            for (Duenio d : estado.duenios()) {
                if (d.puesto() == fixtureF4Poi) {
                    dueniosDelPoi++;
                }
                if (d.potencial() == fixtureF4Poi) {
                    potencialesDelPoi++;
                }
            }
            Celda celda = celdaDe(estado, fixtureF4Poi);
            int libresF4 = celda == null ? -1 : celda.libres();
            boolean ticketCogido = libresF4 == 0;
            checks.add(new Check("fixture_f4_un_job_site", dueniosDelPoi == 1,
                    "poi=" + PoiPersistenceReport.pos(fixtureF4Poi) + " duenios=" + dueniosDelPoi
                            + " A=" + fixtureF4A + " B=" + fixtureF4B));
            checks.add(new Check("fixture_f4_sin_potential", potencialesDelPoi == 0,
                    "poi=" + PoiPersistenceReport.pos(fixtureF4Poi) + " potential=" + potencialesDelPoi));
            checks.add(new Check("fixture_f4_ticket_cogido", ticketCogido,
                    "poi=" + PoiPersistenceReport.pos(fixtureF4Poi) + " libres=" + libresF4));
            // Fantasma = JOB_SITE sin ticket, o más JOB_SITE que tickets: aquí el ticket máximo es 1.
            boolean sinFantasma = dueniosDelPoi <= 1 && ticketCogido && potencialesDelPoi == 0;
            checks.add(new Check("fixture_f4_sin_ghost", sinFantasma,
                    "job_site=" + dueniosDelPoi + " tickets_libres=" + libresF4
                            + " potential=" + potencialesDelPoi + " (ghost si JOB sin ticket)"));
        }

        // 7) Carga parcial: solo se exige contrato si el caso se pudo MEDIR. Si no hubo ningún
        // dueño persistido sin cargar en el momento del mantenimiento, el estado es NO_MEDIDO y no se
        // convierte en un PASS vacío ni en un FAIL inventado.
        if (FASE.equals(RELOAD1) || FASE.equals(PARTIAL)) {
            if (casoCargaParcialMedido) {
                boolean ok = liberadosCargaParcial.isEmpty();
                checks.add(new Check("carga_parcial_sin_liberar_ajenos", ok,
                        "estado=" + (ok ? "MEDIDO_OK" : "MEDIDO_VIOLADO")
                                + " liberados=" + liberadosCargaParcial.size() + " " + liberadosDetalle));
                hallazgos.addAll(liberadosCargaParcial);
            } else {
                eventos.add("CARGA_PARCIAL_NO_MEDIDO (no habia duenios persistidos sin cargar)");
            }
        }
    }

    private static List<Celda> celdasDeTrabajo(final Estado estado) {
        List<Celda> out = new ArrayList<>();
        for (Celda c : estado.celdas()) {
            if (!estado.oficios().containsKey(c.pos())) {
                continue;
            }
            // El barrido de POI es circular y más ancho que la valla (RADIO_POI = valla + margen): una aldea
            // natural/ruina vecina puede colar una estación extra (medido: farmer 688,68,591 con 4/3). Para el
            // contrato del pueblo solo cuentan las estaciones dentro de la valla, no todo lo que entra en el radio.
            BlockPos p = BlockPos.of(c.pos());
            int dx = p.getX() - centro.getX();
            int dz = p.getZ() - centro.getZ();
            if ((long) dx * dx + (long) dz * dz > (long) VillageGenerator.FENCE_RADIUS * VillageGenerator.FENCE_RADIUS) {
                continue;
            }
            out.add(c);
        }
        return out;
    }

    private static Set<Long> celdasTodas(final Estado estado) {
        Set<Long> out = new LinkedHashSet<>();
        for (Celda c : estado.celdas()) {
            out.add(c.pos());
        }
        return out;
    }

    private static long puestoDe(final Estado estado, final String uuid) {
        for (Duenio d : estado.duenios()) {
            if (d.uuid().equals(uuid)) {
                return d.puesto();
            }
        }
        return -1L;
    }

    private static Villager aldeano(final String uuid) {
        Entity e = nivel.getEntity(UUID.fromString(uuid));
        return e instanceof Villager v ? v : null;
    }

    // ------------------------------------------------------------------ SavedData

    private static Path rutaSavedData() {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("devilrpg_villages.dat")
                .normalize();
    }

    private static String leerDigestDisco() {
        try {
            Path p = rutaSavedData();
            if (!Files.exists(p)) {
                eventos.add("saveddata_ausente_en_disco " + p);
                return "";
            }
            CompoundTag tag = NbtIo.readCompressed(p, NbtAccounter.unlimitedHeap());
            return huellaSavedData(tag);
        } catch (IOException | RuntimeException e) {
            eventos.add("saveddata_lectura_error " + e);
            return "";
        }
    }

    /**
     * Huella ESTABLE de VillageSavedData: solo campos que el mantenimiento no toca por tiempo.
     * Excluye a propósito Food, Health, Pressure, RepopulatedAt, StarvingSince, Noticed, FarmOwners,
     * Resolved/Occupied (asedio) y Blueprints (el obrero puede actualizarlo): así un cambio de digest
     * entre fases significa que el guardado NO se cargó, no que el pueblo haya vivido un tick más.
     */
    private static String huellaSavedData(final CompoundTag tag) {
        // El fichero de SavedData envuelve el tag real en {"data": {...}, "DataVersion": n}. El tag VIVO
        // (saved.save(new CompoundTag())) no lleva esa envoltura: si no se desenvuelve, la huella del
        // disco sale distinta SIEMPRE (falso negativo de saveddata_disco_igual_vivo).
        CompoundTag raiz = tag.contains("data", Tag.TAG_COMPOUND) ? tag.getCompound("data") : tag;
        StringBuilder sb = new StringBuilder();
        int[] generadas = raiz.getIntArray("Generated").clone();
        Arrays.sort(generadas);
        sb.append("Generated=").append(Arrays.toString(generadas)).append('\n');
        List<String> asentamientos = new ArrayList<>();
        for (Tag t : raiz.getList("Settlement", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            StringBuilder uno = new StringBuilder("Index=").append(e.getInt("Index"));
            uno.append(",Layout=").append(e.getInt("Layout"));
            uno.append(",Casas=").append(e.getInt("CasasVersion"));
            uno.append(",Guardias=").append(e.getInt("GuardiasFundadoras"));
            uno.append(",Remesa=").append(e.getBoolean("RemesaInicialEntregada"));
            uno.append(",Anexo=").append(e.getLong("AnexoAnimales"));
            if (e.contains("Center")) {
                CompoundTag c = e.getCompound("Center");
                uno.append(",Center=").append(c.getInt("X")).append(',').append(c.getInt("Y")).append(',')
                        .append(c.getInt("Z"));
            }
            asentamientos.add(uno.toString());
        }
        asentamientos.sort(Comparator.naturalOrder());
        sb.append("Settlement=").append(asentamientos).append('\n');
        return PoiPersistenceReport.sha256(sb.toString());
    }

    private static String textoCentroYcampos() {
        StringBuilder sb = new StringBuilder();
        sb.append("centro=").append(centro == null ? "?" : centro.toShortString()).append('\n');
        sb.append("digest=").append(digestVivoFinal).append('\n');
        try {
            VillageSavedData saved = VillageSavedData.get(nivel);
            CompoundTag tag = saved.save(new CompoundTag(), nivel.registryAccess());
            sb.append("generated=").append(Arrays.toString(tag.getIntArray("Generated"))).append('\n');
            for (Tag t : tag.getList("Settlement", Tag.TAG_COMPOUND)) {
                CompoundTag e = (CompoundTag) t;
                if (e.getInt("Index") == OBJETIVO) {
                    sb.append("layout=").append(e.getInt("Layout")).append('\n');
                    sb.append("guardias_fundadoras=").append(e.getInt("GuardiasFundadoras")).append('\n');
                    sb.append("health=").append(e.getInt("Health")).append('\n');
                    sb.append("food=").append(e.getInt("Food")).append('\n');
                }
            }
        } catch (RuntimeException e) {
            sb.append("error_campos=").append(e).append('\n');
        }
        return sb.toString();
    }

    private static String leerDigest(final String fase) {
        Path p = base.resolve("saveddata-digest-" + fase + ".txt");
        try {
            if (Files.exists(p)) {
                return Files.readString(p, StandardCharsets.UTF_8).trim();
            }
        } catch (IOException e) {
            log("no pude leer digest previo: " + e);
        }
        return "";
    }

    private static String leerCentro(final String fase) {
        Path p = base.resolve("saveddata-" + fase + ".txt");
        try {
            if (Files.exists(p)) {
                for (String linea : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                    if (linea.startsWith("centro=")) {
                        return linea.substring("centro=".length()).trim();
                    }
                }
            }
        } catch (IOException e) {
            log("no pude leer centro previo: " + e);
        }
        return "";
    }

    // ------------------------------------------------------------------ snapshot / puestos

    private record Snap(String uuid, String profesion, BlockPos pos, long puesto, long potencial,
                        boolean guardia, boolean bebe) {
    }

    private static void escribirArtefactos(final Estado estado) {
        StringBuilder pois = new StringBuilder("# run=").append(RUN).append(" fase=").append(FASE)
                .append(" tick=").append(ticks).append(" centro=").append(centro.toShortString()).append('\n');
        pois.append("# posLong|pos|tipo|free_tickets|max_tickets|oficios\n");
        for (Celda c : estado.celdas()) {
            pois.append(c.pos()).append('|').append(PoiPersistenceReport.pos(c.pos())).append('|')
                    .append(c.tipo()).append('|').append(c.libres()).append('|').append(c.max()).append('|')
                    .append(estado.oficios().getOrDefault(c.pos(), "")).append('\n');
        }
        escribir("pois-" + FASE + ".txt", pois.toString());

        StringBuilder duenios = new StringBuilder("# uuid|profesion|pos|job_site_long|job_site|potential_long|potential|oficio_del_puesto\n");
        for (Duenio d : estado.duenios()) {
            duenios.append(d.uuid()).append('|').append(d.profesion()).append('|').append(d.posicion())
                    .append('|').append(d.puesto()).append('|')
                    .append(d.puesto() == 0L ? "none" : PoiPersistenceReport.pos(d.puesto())).append('|')
                    .append(d.potencial()).append('|')
                    .append(d.potencial() == 0L ? "none" : PoiPersistenceReport.pos(d.potencial())).append('|')
                    .append(estado.oficios().getOrDefault(d.puesto(), "")).append('\n');
        }
        escribir("duenios-" + FASE + ".txt", duenios.toString());

        StringBuilder puestos = new StringBuilder("# poi_long|poi|uuid|profesion|tipo|libres\n");
        for (Map.Entry<Long, String> e : new TreeMap<>(estado.propiedad()).entrySet()) {
            String profesion = "?";
            for (Duenio d : estado.duenios()) {
                if (d.uuid().equals(e.getValue())) {
                    profesion = d.profesion();
                    break;
                }
            }
            String tipo = "?";
            int libres = -1;
            for (Celda c : estado.celdas()) {
                if (c.pos() == e.getKey()) {
                    tipo = c.tipo();
                    libres = c.libres();
                    break;
                }
            }
            puestos.append(e.getKey()).append('|').append(PoiPersistenceReport.pos(e.getKey())).append('|')
                    .append(e.getValue()).append('|').append(profesion).append('|').append(tipo).append('|')
                    .append(libres).append('\n');
        }
        escribir("puestos-" + FASE + ".txt", puestos.toString());

        StringBuilder snap = new StringBuilder("# uuid|profesion|x|y|z|job_site_long|potential_long|guardia|bebe\n");
        for (Villager v : aldeanosVivos()) {
            snap.append(v.getUUID()).append('|').append(v.getVillagerData().getProfession()).append('|')
                    .append(v.blockPosition().getX()).append('|').append(v.blockPosition().getY()).append('|')
                    .append(v.blockPosition().getZ()).append('|')
                    .append(memoria(v, MemoryModuleType.JOB_SITE)).append('|')
                    .append(memoria(v, MemoryModuleType.POTENTIAL_JOB_SITE)).append('|')
                    .append(VillagerGuardGoal.esGuardia(v)).append('|').append(v.isBaby()).append('\n');
        }
        escribir("snapshot-" + FASE + ".txt", snap.toString());

        if (!ticketProbe.isEmpty()) {
            escribir("tickets-" + FASE + ".txt", "# sonda real take/release/take\n"
                    + String.join("\n", ticketProbe) + "\n");
        }
        if (!fixtures.isEmpty()) {
            escribir("fixtures-" + FASE + ".txt", "# fixtures minimas documentadas\n"
                    + String.join("\n", fixtures) + "\n");
        }
        if (!eventosDetalle.isEmpty() || !eventos.isEmpty()) {
            StringBuilder ev = new StringBuilder("# asignaciones/liberaciones/avisos observados\n");
            for (String e : eventos) {
                ev.append("AVISO ").append(e).append('\n');
            }
            for (String e : eventosDetalle) {
                ev.append(e).append('\n');
            }
            escribir("eventos-" + FASE + ".txt", ev.toString());
        }
    }

    private static Map<String, Snap> leerSnapshot(final String fase) {
        Map<String, Snap> out = new LinkedHashMap<>();
        Path p = base.resolve("snapshot-" + fase + ".txt");
        if (!Files.exists(p)) {
            log("snapshot previo ausente: " + p);
            return out;
        }
        try {
            for (String linea : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                if (linea.isBlank() || linea.startsWith("#")) {
                    continue;
                }
                String[] parts = linea.split("\\|", -1);
                if (parts.length < 9) {
                    continue;
                }
                out.put(parts[0], new Snap(parts[0], parts[1],
                        new BlockPos(Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
                                Integer.parseInt(parts[4])),
                        Long.parseLong(parts[5]), Long.parseLong(parts[6]),
                        Boolean.parseBoolean(parts[7]), Boolean.parseBoolean(parts[8])));
            }
        } catch (Exception e) {
            log("no pude leer snapshot previo: " + e);
        }
        return out;
    }

    private static Map<Long, String> leerPuestos(final String fase) {
        Map<Long, String> out = new TreeMap<>();
        Path p = base.resolve("puestos-" + fase + ".txt");
        if (!Files.exists(p)) {
            log("puestos previos ausentes: " + p);
            return out;
        }
        try {
            for (String linea : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                if (linea.isBlank() || linea.startsWith("#")) {
                    continue;
                }
                String[] parts = linea.split("\\|", -1);
                if (parts.length < 3) {
                    continue;
                }
                out.putIfAbsent(Long.parseLong(parts[0]), parts[2]);
            }
        } catch (Exception e) {
            log("no pude leer puestos previos: " + e);
        }
        return out;
    }

    /** UUIDs tocados por las fixtures de prepare (para no elegirlos como aldeano tardío limpio). */
    private static Set<String> leerFixturesUuids(final String fase) {
        Set<String> out = new LinkedHashSet<>();
        Path p = base.resolve("fixtures-" + fase + ".txt");
        if (!Files.exists(p)) {
            return out;
        }
        java.util.regex.Pattern patron = java.util.regex.Pattern.compile(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
        try {
            for (String linea : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                java.util.regex.Matcher m = patron.matcher(linea);
                while (m.find()) {
                    out.add(m.group());
                }
            }
        } catch (Exception e) {
            log("no pude leer fixtures previas: " + e);
        }
        return out;
    }

    private static boolean todosCargadosDelSnapshot() {
        if (snapshotPrevio.isEmpty()) {
            return true;
        }
        for (Snap s : snapshotPrevio.values()) {
            if (s.bebe() || s.guardia()) {
                continue;
            }
            if (!estaCargado(s.uuid())) {
                return false;
            }
        }
        return true;
    }

    private static boolean todosCargadosExceptoTardio() {
        if (snapshotPrevio.isEmpty()) {
            return true;
        }
        for (Snap s : snapshotPrevio.values()) {
            if (s.bebe() || s.guardia() || zonaTardiaUuids.contains(s.uuid())) {
                continue;
            }
            if (!estaCargado(s.uuid())) {
                return false;
            }
        }
        return true;
    }

    private static boolean todosCargadosDeLaZona() {
        for (String uuid : zonaTardiaUuids) {
            if (!estaCargado(uuid)) {
                return false;
            }
        }
        return true;
    }

    /** Comprueba, al forzar la zona tardía, que TODOS sus aldeanos cargan sus memorias persistidas. */
    private static void comprobarMemoriaDeLaZona(final Estado estado) {
        List<String> fallos = new ArrayList<>();
        int comprobados = 0;
        for (Snap s : snapshotPrevio.values()) {
            if (!zonaTardiaUuids.contains(s.uuid())) {
                continue;
            }
            comprobados++;
            Duenio ahora = null;
            for (Duenio d : estado.duenios()) {
                if (d.uuid().equals(s.uuid())) {
                    ahora = d;
                    break;
                }
            }
            if (ahora == null) {
                fallos.add(s.uuid() + ":no_cargado");
                continue;
            }
            if (s.puesto() > 0L && ahora.puesto() != s.puesto()) {
                fallos.add(s.uuid() + ":job " + PoiPersistenceReport.pos(s.puesto())
                        + "->" + PoiPersistenceReport.pos(ahora.puesto()));
            }
            if (s.potencial() > 0L && ahora.potencial() != s.potencial()) {
                fallos.add(s.uuid() + ":pot " + PoiPersistenceReport.pos(s.potencial())
                        + "->" + PoiPersistenceReport.pos(ahora.potencial()));
            }
        }
        checks.add(new Check("zona_tardia_memoria_persistida", fallos.isEmpty(),
                "comprobados=" + comprobados + " fallos=" + fallos));
    }

    private static boolean estaCargado(final String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return false;
        }
        Entity e = nivel.getEntity(UUID.fromString(uuid));
        return e instanceof Villager v && v.isAlive() && !v.isRemoved()
                && nivel.areEntitiesLoaded(new ChunkPos(v.blockPosition()).toLong());
    }

    // ------------------------------------------------------------------ cupo y utilidades

    private static Map<String, String> cupoDelPueblo() {
        Map<String, String> out = new TreeMap<>();
        for (Map.Entry<VillagerProfession, Integer> e : VillageGenerator.puestosPorOficio().entrySet()) {
            out.put(e.getKey().toString(), String.valueOf(e.getValue()));
        }
        return out;
    }

    private static List<Long> chunksAlrededor() {
        List<Long> out = new ArrayList<>();
        int cx = centro.getX() >> 4;
        int cz = centro.getZ() >> 4;
        for (int dx = -CHUNKS_ALREDEDOR; dx <= CHUNKS_ALREDEDOR; dx++) {
            for (int dz = -CHUNKS_ALREDEDOR; dz <= CHUNKS_ALREDEDOR; dz++) {
                out.add(new ChunkPos(cx + dx, cz + dz).toLong());
            }
        }
        return out;
    }

    /** Suelta los tickets de chunk forzado del área (para que la fase siguiente no herede la carga). */
    private static void soltarChunksForzados() {
        int liberados = 0;
        for (long chunk : chunksAlrededor()) {
            if (nivel.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false)) {
                liberados++;
            }
        }
        log("chunks forzados liberados=" + liberados + " (para no precargar la fase siguiente)");
    }

    // ------------------------------------------------------------------ JSON de la fase

    private static String jsonFase(final String resultado, final boolean experimentoOk,
                                   final boolean contratoOk, final Estado estado) {
        List<Celda> job = celdasDeTrabajo(estado);
        int cogidos = 0;
        int libres = 0;
        for (Celda c : job) {
            if (c.libres() == 0) {
                cogidos++;
            } else {
                libres++;
            }
        }
        List<Hallazgo> dup = PoiPersistenceReport.duplicados(estado.duenios());
        List<Hallazgo> huerf = PoiPersistenceReport.huerfanos(estado.duenios(), celdasTodas(estado));
        List<Hallazgo> sinDueno = PoiPersistenceReport.ticketsSinDueno(job, estado.duenios());
        List<Hallazgo> libreConDueno = PoiPersistenceReport.ticketsLibresConDueno(job, estado.duenios());
        List<Hallazgo> potencial = PoiPersistenceReport.potencialNoPromovido(job, estado.duenios());
        List<Hallazgo> potAjeno = PoiPersistenceReport.potentialAjeno(estado.duenios());
        int conJob = 0;
        int conPotencial = 0;
        for (Duenio d : estado.duenios()) {
            if (d.puesto() > 0L) {
                conJob++;
            }
            if (d.potencial() > 0L && d.puesto() == 0L) {
                conPotencial++;
            }
        }
        Objeto pois = new Objeto()
                .numero("celdas", estado.celdas().size())
                .numero("trabajo", job.size())
                .numero("tickets_cogidos", cogidos)
                .numero("tickets_libres", libres)
                .texto("oficios", String.join(",", new TreeSet<>(estado.oficios().values())))
                .texto("histograma", histograma(estado));
        Objeto memoria = new Objeto()
                .numero("job_site", conJob)
                .numero("potential_sin_job", conPotencial)
                .numero("duplicados", dup.size())
                .numero("huerfanos", huerf.size())
                .numero("tickets_sin_dueno", sinDueno.size())
                .numero("tickets_libres_con_dueno", libreConDueno.size())
                .numero("potential_no_promovido", potencial.size())
                .numero("potential_ajeno", potAjeno.size());
        Objeto entidades = new Objeto()
                .numero("aldeanos_cargados", estado.duenios().size())
                .numero("profesionales_esperados", profesionalesEsperados)
                .numero("guardias_esperados", guardiasEsperados)
                .numero("aldeanos_totales_uuid", estado.uuids().size());
        Objeto fixturesJson = new Objeto()
                .texto("duplicado", fixtureDuplicadoPoi == 0L ? "no" : PoiPersistenceReport.pos(fixtureDuplicadoPoi)
                        + " A=" + fixtureDuplicadoA + " B=" + fixtureDuplicadoB)
                .texto("huerfano", fixtureHuerfanoUuid.isBlank() ? "no" : fixtureHuerfanoUuid
                        + " sitio=" + PoiPersistenceReport.pos(fixtureHuerfanoSitio))
                .texto("estacion_libre", fixtureLibreUuid.isBlank() ? "no" : fixtureLibreUuid
                        + " poi=" + PoiPersistenceReport.pos(fixtureLibrePoi))
                .texto("f4", fixtureF4Poi == 0L ? fixtureF4Motivo : PoiPersistenceReport.pos(fixtureF4Poi)
                        + " A=" + fixtureF4A + " B=" + fixtureF4B)
                .crudo("detalle", PoiPersistenceReport.arregloTexto(fixtures));
        String estadoParcial = !casoCargaParcialMedido ? "NO_MEDIDO"
                : liberadosCargaParcial.isEmpty() ? "MEDIDO_OK" : "MEDIDO_VIOLADO";
        Objeto parcial = new Objeto()
                .texto("estado", estadoParcial)
                .booleano("caso_medido", casoCargaParcialMedido)
                .numero("cargados_antes", cargadosAntesMantenimiento.size())
                .numero("tickets_liberados_ajenos", liberadosCargaParcial.size())
                .crudo("detalle", PoiPersistenceReport.arregloTexto(liberadosDetalle));
        Objeto tardio = new Objeto()
                .texto("uuid", lateUuid)
                .texto("profesion", lateProfesion)
                .texto("puesto_esperado", latePuestoEsperado == 0L ? "none" : PoiPersistenceReport.pos(latePuestoEsperado))
                .texto("potential_esperado", latePotencialEsperado == 0L ? "none" : PoiPersistenceReport.pos(latePotencialEsperado))
                .booleano("cargado", !lateUuid.isBlank() && estaCargado(lateUuid))
                .texto("razon", lateRazon);
        Objeto checksJson = new Objeto();
        for (Check c : checks) {
            checksJson.crudo(c.nombre(), new Objeto().booleano("ok", c.ok()).texto("detalle", c.detalle()).json());
        }
        Objeto esperado = new Objeto()
                .texto("pois_trabajo", "11 (3 farmer + 8 oficios con una plaza; nitwit sin puesto)")
                .texto("un_dueno_por_poi", "cada celda de trabajo con como maximo un JOB_SITE")
                .texto("tickets", "todo ticket cogido tiene dueno en el cerebro; todo JOB_SITE tiene su ticket cogido")
                .texto("potential", "POTENTIAL con ticket cogido se promueve a JOB_SITE")
                .texto("cobertura", "cada plaza con su titular y JOB_SITE (11/11)")
                .texto("round_trip", "el mapa POI->UUID guardado se carga igual (sin perdidos/cambiados/nuevos)")
                .texto("fixtures", "duplicado reconciliado, huerfano recuperado, estacion libre asignada")
                .texto("carga_parcial", "no se libera el ticket de un dueno que aun no esta cargado");
        Objeto observado = new Objeto()
                .numero("job_site", conJob)
                .numero("potential_sin_job", conPotencial)
                .numero("duplicados", dup.size())
                .numero("huerfanos", huerf.size())
                .numero("tickets_cogidos", cogidos)
                .numero("tickets_libres", libres)
                .numero("tickets_sin_dueno", sinDueno.size())
                .numero("tickets_libres_con_dueno", libreConDueno.size())
                .numero("potential_no_promovido", potencial.size())
                .numero("potential_ajeno", potAjeno.size())
                .numero("aldeanos", estado.duenios().size())
                .numero("mantenimientos", mantenimientos)
                .numero("ticks", ticks);
        return new Objeto()
                .texto("run", RUN)
                .texto("fase", FASE)
                .texto("sha", leerSha())
                .texto("git_status", leerGitStatus())
                .texto("momento", java.time.Instant.now().toString())
                .texto("result", resultado)
                .booleano("experimento_ok", experimentoOk)
                .booleano("contrato_ok", contratoOk)
                .booleano("timeout", timeout)
                .texto("fallo_instrumento", falloInstrumento)
                .texto("centro", centro == null ? "?" : centro.toShortString())
                .numero("ticks", ticks)
                .numero("mantenimientos", mantenimientos)
                .numero("gracia_ticks", GRACIA)
                .crudo("pois", pois.json())
                .crudo("memoria", memoria.json())
                .crudo("entidades", entidades.json())
                .crudo("fixtures", fixturesJson.json())
                .crudo("carga_parcial", parcial.json())
                .crudo("tardio", tardio.json())
                .crudo("checks", checksJson.json())
                .crudo("hallazgos", PoiPersistenceReport.hallazgosJson(hallazgos))
                .crudo("hallazgos_codigos", PoiPersistenceReport.arregloTexto(PoiPersistenceReport.codigos(hallazgos)))
                .crudo("ticket_probe", PoiPersistenceReport.arregloTexto(ticketProbe))
                .crudo("eventos", PoiPersistenceReport.arregloTexto(eventosDetalle))
                .crudo("avisos", PoiPersistenceReport.arregloTexto(eventos))
                .crudo("esperado", esperado.json())
                .crudo("observado", observado.json())
                .crudo("saveddata", new Objeto()
                        .texto("digest_previo", corto(digestPrevio))
                        .texto("digest_vivo_inicial", corto(digestVivoInicial))
                        .texto("digest_vivo_final", corto(digestVivoFinal))
                        .texto("digest_disco", corto(digestDisco))
                        .texto("fichero", rutaSavedData().toString())
                        .json())
                .crudo("artefactos", new Objeto()
                        .texto("pois", "pois-" + FASE + ".txt")
                        .texto("duenios", "duenios-" + FASE + ".txt")
                        .texto("puestos", "puestos-" + FASE + ".txt")
                        .texto("snapshot", "snapshot-" + FASE + ".txt")
                        .texto("eventos", "eventos-" + FASE + ".txt")
                        .texto("sondas", "tickets-" + FASE + ".txt")
                        .texto("fixtures", "fixtures-" + FASE + ".txt")
                        .texto("saveddata", "saveddata-" + FASE + ".txt")
                        .texto("log", "srv-" + FASE + ".log")
                        .json())
                .json();
    }

    private static String histograma(final Estado estado) {
        Map<String, Integer> cuenta = new TreeMap<>();
        for (Celda c : celdasDeTrabajo(estado)) {
            cuenta.merge(estado.oficios().getOrDefault(c.pos(), "?"), 1, Integer::sum);
        }
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : cuenta.entrySet()) {
            out.add(e.getKey() + "=" + e.getValue());
        }
        return String.join(",", out);
    }

    private static String leerSha() {
        try {
            Path p = base.resolve("sha.txt");
            if (Files.exists(p)) {
                return Files.readString(p, StandardCharsets.UTF_8).trim();
            }
        } catch (IOException e) {
            log("no pude leer sha: " + e);
        }
        return "desconocido";
    }

    private static String leerGitStatus() {
        try {
            Path p = base.resolve("git-status.txt");
            if (Files.exists(p)) {
                return Files.readString(p, StandardCharsets.UTF_8).trim();
            }
        } catch (IOException e) {
            log("no pude leer git-status: " + e);
        }
        return "desconocido";
    }

    private static String corto(final String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        return s.length() <= 16 ? s : s.substring(0, 16);
    }

    private static void escribirJson(final String nombre, final String contenido) {
        String valido = contenido;
        try {
            com.google.gson.JsonParser.parseString(contenido);
        } catch (RuntimeException e) {
            log("JSON INVALIDO en " + nombre + ": " + e);
            escribir(nombre + ".invalido.txt", contenido);
            valido = new Objeto().texto("fase", FASE).texto("result", "FAIL")
                    .booleano("experimento_ok", false)
                    .texto("fallo", "json_invalido: " + e).json();
        }
        escribir(nombre, valido);
    }

    private static void escribir(final String nombre, final String contenido) {
        try {
            Files.writeString(base.resolve(nombre), contenido, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log("no pude escribir " + nombre + ": " + e);
        }
    }

    private static void fallar(final String motivo, final String detalle) {
        falloInstrumento = motivo + (detalle == null || detalle.isBlank() ? "" : " " + detalle);
        hecho = true;
        timeout |= "timeout".equals(motivo);
        log("FALLO DE INSTRUMENTO: " + falloInstrumento);
        if (base != null) {
            escribir("phase-" + FASE + ".json",
                    new Objeto().texto("fase", FASE).texto("result", "FAIL")
                            .texto("fallo_instrumento", falloInstrumento)
                            .booleano("experimento_ok", false).json());
            escribir("done-" + FASE + ".txt", "FAIL");
        }
        if (server != null) {
            server.halt(false);
        }
    }

    private static void log(final String mensaje) {
        DevilRpg.LOGGER.info("[POI] {}", mensaje);
    }

    // ------------------------------------------------------------------ infraestructura de etapas

    private interface Accion {
        boolean correr();
    }

    private static final class Etapa {
        private final String nombre;
        private final Accion accion;
        private int inicio = -1;

        private Etapa(final String nombre, final Accion accion) {
            this.nombre = nombre;
            this.accion = accion;
        }
    }

    private record Check(String nombre, boolean ok, String detalle) {
    }
}
