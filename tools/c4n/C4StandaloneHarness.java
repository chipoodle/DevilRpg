package com.chipoodle.devilrpg.debug;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * C4 STANDALONE + SAVE/RELOAD · arnés de servidor (solo lectura del mundo salvo el guardado normal).
 * <p>
 * Tres fases sobre el MISMO mundo desechable, dirigidas por el lanzador:
 * <ul>
 *   <li>{@code prepare}: mundo virgen → {@code VillageManager.preGenerate} + mantenimiento de producción,
 *       censo de los 12 puestos, ventana de trabajo, instantánea, guardado y parada del servidor.</li>
 *   <li>{@code reload1}/{@code reload2}: mismo mundo → espera de carga, censo contra la instantánea previa,
 *       ventana de trabajo (reanudación + actividad), auditoría de job sites, instantánea, guardado y parada.</li>
 * </ul>
 * No hay asedio: demuestra C4 sin depender de A2. No crea ni borra trabajadores; solo observa y guarda.
 */
@EventBusSubscriber(modid = DevilRpg.MODID)
public final class C4StandaloneHarness {

    private static final String PHASE = System.getProperty("c4n.phase", "none").trim().toLowerCase(Locale.ROOT);
    private static final String RUN = System.getProperty("c4n.run", "sin-run");
    private static final Path RAIZ = Path.of(System.getProperty("c4n.raiz", "."));
    private static final BlockPos CENTRO_PEDIDO = new BlockPos(661, 71, 533);
    private static final int OBJETIVO = 0;
    private static final int TRABAJO_TICKS = 600;
    private static final int MAX_ESPERA_CARGA = 1600;
    private static final int MUESTREO_CADA = 5;
    /** A/B: mantenimiento del arnes en la carga (inicial y periodico). Produccion = poll de aldea. */
    private static final boolean MANT_INICIAL = Boolean.parseBoolean(System.getProperty("c4n.mantInicial", "true"));
    private static final boolean MANT_PERIODICO = Boolean.parseBoolean(System.getProperty("c4n.mantPeriodico", "true"));

    private static ServerLevel nivel;
    private static MinecraftServer server;
    private static Path base;
    private static boolean preparado;
    private static boolean hecho;
    private static int ticksFase;
    private static BlockPos centro;

    private static final LinkedHashMap<UUID, String> rosterPrev = new LinkedHashMap<>();
    private static final LinkedHashSet<UUID> esperados = new LinkedHashSet<>();
    private static final LinkedHashMap<UUID, String> profesiones = new LinkedHashMap<>();
    private static final LinkedHashMap<UUID, String> goalsInstalados = new LinkedHashMap<>();
    private static final Set<UUID> activos = new HashSet<>();
    private static final Map<UUID, double[]> posIni = new LinkedHashMap<>();
    private static final Map<UUID, double[]> posFin = new LinkedHashMap<>();
    private static boolean ventanaIniciada;
    private static int muestras;
    private static String fallo = "";
    /** C4 · censo solo cuando TODAS las entidades esperadas estan cargadas (entidad y chunk). */
    private static boolean cargaRegistrada;
    private static boolean mantInicialHecho;
    private static int primerTickTodoCargado = -1;

    // --- E · DIAGNOSTICO: propiedad de puestos (POI -> UUID) y guardias --------------------------------
    /** Máscara persistida de guardias fundadoras: 4 slots fijos (0..3). */
    private static final int SLOTS_FUNDADORES = 4;
    /** Foto de JOB_SITE tomada justo al completar la carga, ANTES del mantenimiento inicial (solo reload). */
    private static final LinkedHashMap<Long, UUID> jobSitesAlCargar = new LinkedHashMap<>();
    private static int jobSitesAlCargarCargados;
    private static String jobSiteComparacion = "sin_fase_previa";
    private static int jobSiteEstables;
    private static int jobSiteCambiados;
    private static int jobSitePerdidos;
    private static int jobSiteNuevos;
    /** -1 = no medido (prepare no carga un mundo previo). */
    private static int jobSiteAlCargarPresentes = -1;
    private static int jobSiteReclamadosEnVentana = -1;
    private static int jobSiteAlCargarPerdidos = -1;
    /** Auditoria de propiedad en el instante de carga (antes de cualquier mantenimiento). */
    private static int jobSiteAlCargarDuplicados = -1;
    private static int jobSiteAlCargarHuerfanos = -1;
    private static final List<String> jobSiteDetalle = new ArrayList<>();

    private C4StandaloneHarness() {
    }

    @SubscribeEvent
    public static void alArrancar(final ServerStartedEvent evento) {
        server = evento.getServer();
        nivel = server.overworld();
        base = RAIZ.resolve("build").resolve("c4n").resolve(RUN);
        try {
            Files.createDirectories(base);
        } catch (Exception e) {
            log("no pude crear " + base + ": " + e);
        }
        log("ARRANCADO fase=" + PHASE + " run=" + RUN);
        if (!("prepare".equals(PHASE) || "reload1".equals(PHASE) || "reload2".equals(PHASE))) {
            fallar("fase_desconocida", "fase='" + PHASE + "'");
            return;
        }
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                nivel.setChunkForced((CENTRO_PEDIDO.getX() >> 4) + dx, (CENTRO_PEDIDO.getZ() >> 4) + dz, true);
            }
        }
        var saved = com.chipoodle.devilrpg.world.VillageSavedData.get(nivel);
        boolean generada = saved.isGenerated(OBJETIVO);
        if ("prepare".equals(PHASE)) {
            if (generada) {
                fallar("mundo_no_virgen", "prepare sobre un mundo ya generado");
                return;
            }
            boolean okGen = com.chipoodle.devilrpg.world.VillageManager.preGenerate(nivel, OBJETIVO, CENTRO_PEDIDO);
            if (!okGen) {
                fallar("preGenerate_fallo", "");
                return;
            }
            centro = saved.getCentre(OBJETIVO);
            if (centro == null) {
                fallar("centro_no_persistido", "");
                return;
            }
            try {
                com.chipoodle.devilrpg.world.VillageManager.ejecutarMantenimientoParaArnes(nivel, OBJETIVO, centro);
            } catch (RuntimeException ex) {
                fallar("mantenimiento_prepare", ex.toString());
                return;
            }
            log("prepare centro=" + centro.toShortString() + " poblacion=" + aldeanos().size());
            preparado = true;
        } else {
            centro = saved.getCentre(OBJETIVO);
            if (centro == null) {
                fallar("centro_ausente_en_reload", "");
                return;
            }
            leerRosterPrevio();
            log("reload centro=" + centro.toShortString() + " rosterPrevio=" + rosterPrev.size());
            preparado = true;
        }
    }

    @SubscribeEvent
    public static void alTick(final ServerTickEvent.Pre evento) {
        if (!preparado || hecho || nivel == null) {
            return;
        }
        ticksFase++;
        if (ticksFase % MUESTREO_CADA == 0) {
            muestrearActividad();
        }
        if (ticksFase % 5 == 0) {
            int[] js = auditarJobSite();
            if (js[2] > 0) {
                log("DUPLICADO_TICK tick=" + ticksFase + " presentes=" + js[0] + " duplicados=" + js[2]);
                if (ticksFase <= 200 || ticksFase % 25 == 0) {
                    volcarTabla("DUPLICADO");
                }
            }
        }
        if ("prepare".equals(PHASE)) {
            tickPrepare();
        } else {
            tickReload();
        }
    }

    // ------------------------------------------------------------------ prepare

    private static void tickPrepare() {
        if (ticksFase < 40) {
            return;
        }
        if (!ventanaIniciada) {
            censo(esperados, profesiones, goalsInstalados);
            int esperadosCount = com.chipoodle.devilrpg.world.VillageGenerator.puestosDelPueblo();
            if (esperados.size() < esperadosCount) {
                if (ticksFase > MAX_ESPERA_CARGA) {
                    fallar("censo_prepare_incompleto", "profesionales=" + esperados.size() + "/" + esperadosCount);
                }
                return;
            }
            iniciarVentana();
        }
        if (ticksFase >= 40 + TRABAJO_TICKS) {
            cerrarFase();
        }
    }

    // ------------------------------------------------------------------ reload

    private static void tickReload() {
        if (rosterPrev.isEmpty()) {
            fallar("roster_previo_ausente", "");
            return;
        }
        if (!cargaRegistrada) {
            for (UUID id : rosterPrev.keySet()) {
                var e = nivel.getEntity(id);
                if (!(e instanceof net.minecraft.world.entity.npc.Villager v) || !v.isAlive() || v.isRemoved()
                        || !nivel.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(v.blockPosition()).toLong())) {
                    if (ticksFase >= MAX_ESPERA_CARGA) {
                        fallar("carga_no_completa", "esperados=" + rosterPrev.size() + " tick=" + ticksFase);
                    }
                    return;
                }
            }
            cargaRegistrada = true;
            primerTickTodoCargado = ticksFase;
            log("CARGA_COMPLETA tick=" + ticksFase + " esperados=" + rosterPrev.size());
            // E · la foto PERSISTIDA de los puestos, tomada justo al completar la carga y ANTES del mantenimiento:
            // es la unica forma de separar "memoria vacia al cargar" de "puesto reclamado durante la ventana".
            censoJobSitesAlCargar();
            volcarTabla("ANTES_DE_MANTENIMIENTO");
            if (MANT_INICIAL && !mantInicialHecho) {
                mantInicialHecho = true;
                try {
                    com.chipoodle.devilrpg.world.VillageManager.ejecutarMantenimientoParaArnes(nivel, OBJETIVO, centro);
                } catch (RuntimeException ex) {
                    log("mantenimiento inicial aviso: " + ex);
                }
                log("MANT_INICIAL tick=" + ticksFase);
                volcarTabla("TRAS_MANTENIMIENTO");
            }
            return;
        }
        if (!ventanaIniciada) {
            censo(esperados, profesiones, goalsInstalados);
            int instalados = 0;
            for (UUID id : esperados) {
                if ("installed".equals(goalsInstalados.get(id))) {
                    instalados++;
                }
            }
            if (instalados < esperados.size() && ticksFase < MAX_ESPERA_CARGA) {
                if (MANT_PERIODICO && ticksFase % 200 == 0) {
                    try {
                        com.chipoodle.devilrpg.world.VillageManager.ejecutarMantenimientoParaArnes(
                                nivel, OBJETIVO, centro);
                        log("mantenimiento reload t=" + ticksFase + " goals=" + instalados + "/" + esperados.size());
                        volcarTabla("TRAS_MANTENIMIENTO_PERIODICO");
                    } catch (RuntimeException ex) {
                        log("mantenimiento reload aviso: " + ex);
                    }
                }
                return;
            }
            iniciarVentana();
        }
        if (ticksFase >= primerTickTodoCargado + TRABAJO_TICKS) {
            cerrarFase();
        }
    }

    /** C4 · tabla temporal completa: quien comparte que POI, con profesion, posicion y goal. */
    private static void volcarTabla(String etiqueta) {
        Map<Long, java.util.List<UUID>> porPoi = new LinkedHashMap<>();
        for (UUID id : rosterPrev.keySet()) {
            if (!(nivel.getEntity(id) instanceof net.minecraft.world.entity.npc.Villager v) || !v.isAlive()) {
                log("TABLA " + etiqueta + " tick=" + ticksFase + " uuid=" + id + " NO_CARGADO");
                continue;
            }
            String js = jobSiteDe(v);
            log("TABLA " + etiqueta + " tick=" + ticksFase + " uuid=" + id
                    + " prof=" + v.getVillagerData().getProfession()
                    + " pos=" + v.blockPosition().toShortString() + " jobSite=" + js
                    + " goal=" + (tieneGoalProfesional(v) ? "installed" : "MISSING"));
            var mem = v.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
            if (mem.isPresent()) {
                porPoi.computeIfAbsent(mem.get().pos().asLong(), k -> new java.util.ArrayList<>()).add(id);
            }
        }
        for (var e : porPoi.entrySet()) {
            if (e.getValue().size() > 1) {
                log("TABLA " + etiqueta + " tick=" + ticksFase + " POI=" + BlockPos.of(e.getKey()).toShortString()
                        + " duenios=" + e.getValue());
            }
        }
    }
    // ------------------------------------------------------------------ census & activity

    private static List<net.minecraft.world.entity.npc.Villager> aldeanos() {
        var zona = new net.minecraft.world.phys.AABB(centro)
                .inflate(com.chipoodle.devilrpg.world.VillageGenerator.FENCE_RADIUS + 20.0D);
        return nivel.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, zona);
    }

    private static void censo(final Set<UUID> esperadosOut, final Map<UUID, String> profOut,
                              final Map<UUID, String> goalOut) {
        esperadosOut.clear();
        profOut.clear();
        goalOut.clear();
        for (var v : aldeanos()) {
            if (!v.isAlive() || v.isRemoved() || v.isBaby()) {
                continue;
            }
            if (com.chipoodle.devilrpg.entity.goal.VillagerGuardGoal.esGuardia(v)) {
                continue;
            }
            if (v.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.NONE) {
                continue;
            }
            esperadosOut.add(v.getUUID());
            profOut.put(v.getUUID(), v.getVillagerData().getProfession().toString());
            goalOut.put(v.getUUID(), tieneGoalProfesional(v) ? "installed" : "missing");
        }
        log("censo profesionales=" + esperadosOut.size() + "/" + com.chipoodle.devilrpg.world.VillageGenerator.puestosDelPueblo()
                + " roster=" + profOut);
    }

    private static void iniciarVentana() {
        ventanaIniciada = true;
        muestras = 0;
        posIni.clear();
        for (UUID id : esperados) {
            if (nivel.getEntity(id) instanceof net.minecraft.world.entity.npc.Villager v) {
                posIni.put(id, new double[]{v.getX(), v.getY(), v.getZ()});
            }
        }
        log("ventana de trabajo iniciada t=" + ticksFase + " esperados=" + esperados.size());
    }

    private static void muestrearActividad() {
        if (!ventanaIniciada || esperados.isEmpty()) {
            return;
        }
        muestras++;
        for (UUID id : esperados) {
            if (!(nivel.getEntity(id) instanceof net.minecraft.world.entity.npc.Villager v)
                    || !v.isAlive() || v.isRemoved()) {
                continue;
            }
            String esperado = goalProfesionalEsperado(v);
            if (esperado.isEmpty()) {
                continue;
            }
            for (var wrapped : v.goalSelector.getAvailableGoals()) {
                if (wrapped.isRunning() && wrapped.getGoal().getClass().getSimpleName().equals(esperado)) {
                    activos.add(id);
                    break;
                }
            }
            posFin.put(id, new double[]{v.getX(), v.getY(), v.getZ()});
        }
    }

    // ------------------------------------------------------------------ close & report

    private static void cerrarFase() {
        hecho = true;
        List<String> chequeos = new ArrayList<>();
        int presentes = 0;
        int profesionEstable = 0;
        int reanudados = 0;
        int movidos = 0;
        int perdidos = 0;
        int goalsOk = 0;
        int fundadoresObservados = 0;
        int esperadoTotal = "prepare".equals(PHASE)
                ? com.chipoodle.devilrpg.world.VillageGenerator.puestosDelPueblo() : rosterPrev.size();

        Map<UUID, String> profesEsperadas = new LinkedHashMap<>();
        if ("prepare".equals(PHASE)) {
            profesEsperadas.putAll(profesiones);
        } else {
            for (var e : rosterPrev.entrySet()) {
                String[] parts = e.getValue().split("\\|", -1);
                profesEsperadas.put(e.getKey(), parts.length > 0 ? parts[0] : "?");
            }
        }

        for (UUID id : profesEsperadas.keySet()) {
            var entidad = nivel.getEntity(id);
            if (!(entidad instanceof net.minecraft.world.entity.npc.Villager v) || !v.isAlive() || v.isRemoved()) {
                perdidos++;
                continue;
            }
            presentes++;
            String prof = v.getVillagerData().getProfession().toString();
            boolean goalOk = tieneGoalProfesional(v);
            if (goalOk) {
                goalsOk++;
            }
            if (prof.equals(profesEsperadas.get(id))) {
                profesionEstable++;
            }
            boolean activo = activos.contains(id);
            double[] a = posIni.get(id);
            double[] b = posFin.get(id);
            boolean movido = a != null && b != null
                    && (Math.abs(a[0] - b[0]) + Math.abs(a[2] - b[2])) > 0.5D;
            if (movido) {
                movidos++;
            }
            if (goalOk && (activo || movido)) {
                reanudados++;
            }
            log("WORKER uuid=" + id + " prof=" + prof + " profEsperada=" + profesEsperadas.get(id)
                    + " goal=" + (goalOk ? "installed" : "MISSING") + " activo=" + activo
                    + " movido=" + movido + " pos=" + v.blockPosition().toShortString()
                    + " jobSite=" + jobSiteDe(v));
        }

        int[] jobSite = auditarJobSite();
        // C4 · invariantes del censo (12 puestos, 4 fundadores, un dueno por puesto, cupos). Se registra en la fase
        // y en el log; la red de seguridad ya se exige en `okFase` (invariantes.ok()).
        java.util.List<com.chipoodle.devilrpg.world.VillageRosterInvariants.Miembro> roster = new java.util.ArrayList<>();
        for (var v : aldeanos()) {
            if (!v.isAlive() || v.isRemoved() || v.isBaby()) {
                continue;
            }
            long puesto = v.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE)
                    .map(m -> m.pos().asLong()).orElse(0L);
            boolean fund = com.chipoodle.devilrpg.world.VillageManager.esGuardiaFundadora(v);
            if (fund) {
                fundadoresObservados++;
            }
            roster.add(new com.chipoodle.devilrpg.world.VillageRosterInvariants.Miembro(
                    v.getUUID(), v.getVillagerData().getProfession().toString(), puesto, fund));
        }
        java.util.Map<String, Integer> cupoRoster = new java.util.LinkedHashMap<>();
        com.chipoodle.devilrpg.world.VillageGenerator.puestosPorOficio()
                .forEach((k, n) -> cupoRoster.put(k.toString(), n));
        var invariantes = com.chipoodle.devilrpg.world.VillageRosterInvariants.comprobar(
                roster, com.chipoodle.devilrpg.world.VillageGenerator.puestosDelPueblo(), cupoRoster,
                com.chipoodle.devilrpg.world.VillageManager.MILICIA_FUNDADORES);
        log("ROSTER_INVARIANTS ok=" + invariantes.ok() + " violaciones=" + invariantes.violaciones());
        chequeos.add("\"roster_invariantes_ok\":" + invariantes.ok());

        // E · propiedad de puestos entre fases + foto AL_CARGAR, y metricas de guardias (solo observacion).
        compararYVolcarJobSites();
        String guardias = guardiasJson();

        int duplicados = jobSite[2];
        boolean registrados = presentes == esperadoTotal;
        boolean reanudadosOk = reanudados == esperadoTotal;
        boolean identidades = profesionEstable == esperadoTotal;
        boolean sinPerdidas = perdidos == 0;
        boolean sinDuplicados = duplicados == 0;
        boolean jobSiteOk = jobSite[1] == 0 && jobSite[2] == 0;
        boolean preparadoOk = sinPerdidas && registrados && identidades && sinDuplicados && jobSiteOk;
        boolean okFase = preparadoOk && reanudadosOk && goalsOk == esperadoTotal && invariantes.ok();

        List<Causa> conocidas = causasConocidas(esperadoTotal, jobSite[0], goalsOk, jobSitePerdidos,
                jobSiteNuevos, esperados.size(), invariantes.violaciones());
        List<Causa> desconocidas = causasDesconocidas(duplicados, jobSiteCambiados, invariantes.violaciones(),
                jobSiteAlCargarPerdidos, perdidos);

        // E · GUARDADO PRIMERO, MARCADOR DESPUES: el lanzador mata el JVM en cuanto ve done-<fase>.txt, asi que
        // escribir el marcador antes de saveEverything cortaba el log en "Saving chunks..." y dejaba el mundo a medias.
        boolean guardadoOk = false;
        String errorGuardado = "";
        try {
            // E · el retorno de saveEverything NO se ignora: si el servidor no pudo guardar, la fase no es PASS.
            guardadoOk = server.saveEverything(false, true, true);
            if (!guardadoOk) {
                throw new IllegalStateException("saveEverything devolvio false");
            }
            Files.writeString(base.resolve("saved-" + PHASE + ".txt"),
                    String.valueOf(System.currentTimeMillis()), StandardCharsets.UTF_8);
            log("mundo guardado phase=" + PHASE);
        } catch (Exception e) {
            guardadoOk = false;
            errorGuardado = e.toString();
            log("save error: " + e);
            escribir("save-failed-" + PHASE + ".txt", errorGuardado);
        }
        log("FASE " + PHASE + " guardado_ok=" + guardadoOk);
        if (!guardadoOk) {
            fallo = "save_fallido " + errorGuardado;
            conocidas.add(new Causa("GUARDADO_FALLIDO", "saveEverything lanzo: " + errorGuardado));
        }
        boolean faseOkFinal = okFase && guardadoOk && fallo.isEmpty();
        String veredicto = faseOkFinal ? "PASS" : "FAIL";

        String esperadoJson = esperadoJson();
        StringBuilder obs = new StringBuilder("{\"profesionales\":").append(esperados.size())
                .append(",\"fundadores\":").append(fundadoresObservados)
                .append(",\"uuid\":").append(profesEsperadas.size())
                .append(",\"goals_instalados\":").append(goalsOk)
                .append(",\"presentes\":").append(presentes)
                .append(",\"perdidos\":").append(perdidos)
                .append(",\"profesion_estable\":").append(profesionEstable)
                .append(",\"reanudados\":").append(reanudados)
                .append(",\"activos\":").append(activos.size())
                .append(",\"movidos\":").append(movidos)
                .append(",\"muestras\":").append(muestras)
                .append(",\"job_site_presentes\":").append(jobSite[0])
                .append(",\"job_site_huerfanos\":").append(jobSite[1])
                .append(",\"job_site_duplicados\":").append(duplicados)
                .append(",\"job_site_estables\":").append(jobSiteEstables)
                .append(",\"job_site_cambiados\":").append(jobSiteCambiados)
                .append(",\"job_site_perdidos\":").append(jobSitePerdidos)
                .append(",\"job_site_nuevos\":").append(jobSiteNuevos)
                .append(",\"job_site_comparacion\":\"").append(escape(jobSiteComparacion)).append('"')
                .append(",\"job_site_al_cargar_presentes\":")
                .append(numOrNull(jobSiteAlCargarPresentes))
                .append(",\"job_site_al_cargar_cargados\":")
                .append(numOrNull("prepare".equals(PHASE) ? -1 : jobSitesAlCargarCargados))
                .append(",\"job_site_reclamados_en_ventana\":").append(numOrNull(jobSiteReclamadosEnVentana))
                .append(",\"job_site_al_cargar_perdidos\":").append(numOrNull(jobSiteAlCargarPerdidos))
                .append(",\"job_site_al_cargar_duplicados\":").append(numOrNull(jobSiteAlCargarDuplicados))
                .append(",\"job_site_al_cargar_huerfanos\":").append(numOrNull(jobSiteAlCargarHuerfanos))
                .append(",\"guardado_ok\":").append(guardadoOk)
                .append('}');
        String observadoJson = obs.toString();
        StringBuilder inv = new StringBuilder("{\"roster_invariantes_ok\":").append(invariantes.ok())
                .append(",\"roster_violaciones\":").append(stringsJson(invariantes.violaciones()))
                .append(",\"job_site_ok\":").append(jobSiteOk)
                .append(",\"censo_completo\":").append(registrados)
                .append(",\"sin_duplicados\":").append(sinDuplicados)
                .append(",\"sin_huerfanos\":").append(jobSite[1] == 0)
                .append(",\"sin_perdidas\":").append(sinPerdidas)
                .append(",\"identidades\":").append(identidades)
                .append(",\"goals\":").append(goalsOk == esperadoTotal)
                .append(",\"reanudados\":").append(reanudadosOk)
                .append(",\"fase_ok\":").append(faseOkFinal)
                .append('}');
        String invariantesJson = inv.toString();
        String conocidasJson = causasJson(conocidas);
        String desconocidasJson = causasJson(desconocidas);

        chequeos.add("\"fase_ok\":" + faseOkFinal);
        chequeos.add("\"guardado_ok\":" + guardadoOk);
        chequeos.add("\"esperados\":" + esperadoTotal);
        chequeos.add("\"presentes\":" + presentes);
        chequeos.add("\"perdidos\":" + perdidos);
        chequeos.add("\"profesion_estable\":" + profesionEstable);
        chequeos.add("\"goals_instalados\":" + goalsOk);
        chequeos.add("\"reanudados\":" + reanudados);
        chequeos.add("\"activos\":" + activos.size());
        chequeos.add("\"movidos\":" + movidos);
        chequeos.add("\"job_site_presentes\":" + jobSite[0]);
        chequeos.add("\"job_site_huerfanos\":" + jobSite[1]);
        chequeos.add("\"job_site_duplicados\":" + duplicados);
        chequeos.add("\"muestras\":" + muestras);
        chequeos.add("\"job_site_estables\":" + jobSiteEstables);
        chequeos.add("\"job_site_cambiados\":" + jobSiteCambiados);
        chequeos.add("\"job_site_perdidos\":" + jobSitePerdidos);
        chequeos.add("\"job_site_nuevos\":" + jobSiteNuevos);
        chequeos.add("\"job_site_comparacion\":\"" + escape(jobSiteComparacion) + "\"");
        chequeos.add("\"job_site_al_cargar_presentes\":" + numOrNull(jobSiteAlCargarPresentes));
        chequeos.add("\"job_site_al_cargar_cargados\":"
                + numOrNull("prepare".equals(PHASE) ? -1 : jobSitesAlCargarCargados));
        chequeos.add("\"job_site_reclamados_en_ventana\":" + numOrNull(jobSiteReclamadosEnVentana));
        chequeos.add("\"job_site_al_cargar_perdidos\":" + numOrNull(jobSiteAlCargarPerdidos));
        chequeos.add("\"job_site_al_cargar_duplicados\":" + numOrNull(jobSiteAlCargarDuplicados));
        chequeos.add("\"job_site_al_cargar_huerfanos\":" + numOrNull(jobSiteAlCargarHuerfanos));
        chequeos.add("\"job_site_detalle\":" + stringsJson(jobSiteDetalle));
        chequeos.add("\"guardias\":" + guardias);
        chequeos.add("\"esperado\":" + esperadoJson);
        chequeos.add("\"observado\":" + observadoJson);
        chequeos.add("\"invariantes\":" + invariantesJson);
        chequeos.add("\"causa_conocida\":" + conocidasJson);
        chequeos.add("\"causa_desconocida\":" + desconocidasJson);
        chequeos.add("\"notas\":" + stringsJson(List.of(
                "job_site_presentes y job_site_estables/cambiados/perdidos/nuevos son OBSERVADOS: no hay umbral duro "
                        + "porque las memorias JOB_SITE se reclaman durante la ventana (limitacion conocida).",
                "Los FAIL duros siguen siendo los de siempre: duplicados de JOB_SITE, huerfanos, perdidas de aldeano, "
                        + "identidad de profesion, goals, reanudados e invariantes del roster.")));
        if (!fallo.isEmpty()) {
            chequeos.add("\"fallo\":\"" + escape(fallo) + "\"");
        }
        chequeos = sinClavesDuplicadas(chequeos);

        escribirJson("phase-" + PHASE + ".json", "{\"fase\":\"" + PHASE + "\",\"result\":\"" + veredicto
                + "\",\"uuid_set\":[" + uuidsJson(profesEsperadas.keySet()) + "],"
                + String.join(",", chequeos) + "}");
        escribir("snapshot-" + PHASE + ".txt", snapshotTexto());
        escribirDiagnostico(veredicto, esperadoJson, observadoJson, invariantesJson, conocidasJson,
                desconocidasJson, guardias);
        log("DIAG fase=" + PHASE + " result=" + veredicto + " guardado_ok=" + guardadoOk
                + " roster_invariantes_ok=" + invariantes.ok()
                + " job_site_ok=" + jobSiteOk + " causas_conocidas=" + conocidas.size()
                + " causas_desconocidas=" + desconocidas.size() + " job_site_estables=" + jobSiteEstables
                + " cambiados=" + jobSiteCambiados + " perdidos=" + jobSitePerdidos + " nuevos=" + jobSiteNuevos);
        for (Causa c : conocidas) {
            log("DIAG causa_conocida=" + c.codigo() + " :: " + c.detalle());
        }
        for (Causa c : desconocidas) {
            log("DIAG causa_desconocida=" + c.codigo() + " :: " + c.detalle());
        }
        log("FASE " + PHASE + " -> " + veredicto + " guardado_ok=" + guardadoOk
                + " presentes=" + presentes + "/" + esperadoTotal
                + " reanudados=" + reanudados + "/" + esperadoTotal + " jobsite=" + jobSite[0]
                + " huerfanos=" + jobSite[1] + " duplicados=" + duplicados
                + " activos=" + activos.size() + " movidos=" + movidos);
        // El marcador de fin va DESPUES del guardado y de saved-<fase>.txt: es la senal que espera el lanzador.
        // D · volcado runtime de metricas del planner (vacio si el run no navega, pero demuestra el cableado).
        if (com.chipoodle.devilrpg.entity.nav.NavPlannerMetrics.enabled()) {
            escribir("metrics-" + PHASE + ".txt", com.chipoodle.devilrpg.entity.nav.NavPlannerMetrics.dumpCsv());
            log("METRICS " + com.chipoodle.devilrpg.entity.nav.NavPlannerMetrics.dumpLinea());
        }
        escribir("done-" + PHASE + ".txt", veredicto);
        server.halt(false);
    }

    /** Censo de JOB_SITE inmediatamente tras la carga, ANTES de cualquier mantenimiento: persistido vs creado. */
    private static void censoJobSitesAlCargar() {
        jobSitesAlCargar.clear();
        Map<Long, UUID> owners = new LinkedHashMap<>();
        int cargados = 0;
        int duplicados = 0;
        int huerfanos = 0;
        for (UUID id : rosterPrev.keySet()) {
            if (!(nivel.getEntity(id) instanceof net.minecraft.world.entity.npc.Villager v)
                    || !v.isAlive() || v.isRemoved()) {
                log("AL_CARGAR uuid=" + id + " NO_CARGADO");
                continue;
            }
            cargados++;
            var memory = v.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
            if (memory.isPresent()) {
                BlockPos poi = memory.get().pos();
                if (nivel.getPoiManager().getType(poi).isEmpty()) {
                    huerfanos++;
                }
                UUID previo = owners.putIfAbsent(poi.asLong(), v.getUUID());
                if (previo != null) {
                    duplicados++;
                    log("AL_CARGAR JOB_SITE_DUPLICADO poi=" + poi.toShortString()
                            + " uuidA=" + previo + " uuidB=" + v.getUUID()
                            + " profA=" + profesionDe(previo) + " profB=" + v.getVillagerData().getProfession());
                } else {
                    jobSitesAlCargar.put(poi.asLong(), v.getUUID());
                }
            }
            log("AL_CARGAR uuid=" + id + " prof=" + v.getVillagerData().getProfession()
                    + " jobSite=" + jobSiteDe(v) + " persistido=" + memory.isPresent()
                    + " pos=" + v.blockPosition().toShortString());
        }
        jobSitesAlCargarCargados = cargados;
        jobSiteAlCargarDuplicados = duplicados;
        jobSiteAlCargarHuerfanos = huerfanos;
        log("AL_CARGAR cargados=" + cargados + "/" + rosterPrev.size()
                + " con_jobSite=" + jobSitesAlCargar.size() + " duplicados=" + duplicados
                + " huerfanos=" + huerfanos);
    }

    // ------------------------------------------------------------------ E · propiedad de puestos y guardias

    /**
     * E · compara la propiedad de puestos con la fase previa y escribe {@code jobsite-<fase>.txt}.
     * <p>
     * Solo OBSERVA: un cambio de dueño o un puesto perdido no invalida la fase (los FAIL duros siguen siendo
     * duplicados y huérfanos). La foto {@code AL_CARGAR} permite separar "memoria vacía al cargar" de "puesto
     * reclamado/abandonado durante la ventana".
     */
    private static void compararYVolcarJobSites() {
        Map<Long, Puesto> actual = puestosActuales();
        if (!jobSitesAlCargar.isEmpty() || "reload1".equals(PHASE) || "reload2".equals(PHASE)) {
            jobSiteAlCargarPresentes = jobSitesAlCargar.size();
            int reclamados = 0;
            int noLlegaron = 0;
            for (Long poi : actual.keySet()) {
                if (!jobSitesAlCargar.containsKey(poi)) {
                    reclamados++;
                }
            }
            for (Long poi : jobSitesAlCargar.keySet()) {
                if (!actual.containsKey(poi)) {
                    noLlegaron++;
                }
            }
            jobSiteReclamadosEnVentana = reclamados;
            jobSiteAlCargarPerdidos = noLlegaron;
            jobSiteDetalle.add("AL_CARGAR presentes=" + jobSiteAlCargarPresentes
                    + " reclamados_en_ventana=" + reclamados + " no_llegaron_al_cierre=" + noLlegaron);
        }

        String previa = "reload1".equals(PHASE) ? "prepare" : "reload2".equals(PHASE) ? "reload1" : null;
        if (previa == null) {
            jobSiteComparacion = "sin_fase_previa";
        } else {
            Map<Long, UUID> antes = leerJobSites(previa);
            if (antes == null) {
                jobSiteComparacion = "fase_previa_sin_tabla:" + previa;
            } else {
                jobSiteComparacion = "comparado_con_" + previa;
                for (var e : antes.entrySet()) {
                    Puesto ahora = actual.get(e.getKey());
                    if (ahora == null) {
                        jobSitePerdidos++;
                        jobSiteDetalle.add("PERDIDO poi=" + BlockPos.of(e.getKey()).toShortString()
                                + " uuid=" + e.getValue());
                    } else if (!ahora.uuid().equals(e.getValue())) {
                        jobSiteCambiados++;
                        jobSiteDetalle.add("CAMBIADO poi=" + ahora.poi() + " antes=" + e.getValue()
                                + " ahora=" + ahora.uuid() + " profAhora=" + ahora.profesion()
                                + " tipoPoi=" + ahora.tipoPoi());
                    } else {
                        jobSiteEstables++;
                    }
                }
                for (var e : actual.entrySet()) {
                    if (!antes.containsKey(e.getKey())) {
                        jobSiteNuevos++;
                        jobSiteDetalle.add("NUEVO poi=" + e.getValue().poi() + " uuid=" + e.getValue().uuid()
                                + " prof=" + e.getValue().profesion());
                    }
                }
            }
        }
        escribir("jobsite-" + PHASE + ".txt", textoJobSites(actual));
    }

    /** Foto de puestos al cierre: POI -> quién lo ocupa. Incluye guardias por si alguno conserva memoria. */
    private static LinkedHashMap<Long, Puesto> puestosActuales() {
        LinkedHashMap<Long, Puesto> out = new LinkedHashMap<>();
        List<net.minecraft.world.entity.npc.Villager> lista = new ArrayList<>(aldeanos());
        lista.sort(java.util.Comparator.comparingLong(
                (net.minecraft.world.entity.npc.Villager v) -> v.blockPosition().asLong()));
        for (var v : lista) {
            if (!v.isAlive() || v.isRemoved() || v.isBaby()) {
                continue;
            }
            var memory = v.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
            if (memory.isEmpty()) {
                continue;
            }
            BlockPos pos = memory.get().pos();
            boolean guardia = com.chipoodle.devilrpg.entity.goal.VillagerGuardGoal.esGuardia(v);
            int slot = v.getPersistentData().getInt(com.chipoodle.devilrpg.world.VillageManager.GUARD_INDEX_TAG);
            int tipo = v.getPersistentData().getInt(com.chipoodle.devilrpg.world.VillageManager.GUARD_TYPE_TAG);
            Puesto puesto = new Puesto(v.getUUID(), v.getVillagerData().getProfession().toString(), guardia,
                    slot, tipo, pos.toShortString(),
                    String.valueOf(nivel.getPoiManager().getType(pos)));
            Puesto previo = out.putIfAbsent(pos.asLong(), puesto);
            if (previo != null) {
                jobSiteDetalle.add("DUPLICADO poi=" + pos.toShortString() + " uuidA=" + previo.uuid()
                        + " uuidB=" + v.getUUID());
            }
        }
        return out;
    }

    private static String textoJobSites(Map<Long, Puesto> puestos) {
        StringBuilder sb = new StringBuilder();
        sb.append("# run=").append(RUN).append(" fase=").append(PHASE)
                .append(" tick=").append(ticksFase).append(" puestos=").append(puestos.size()).append('\n');
        sb.append("# poi_long|poi|uuid|profesion|guardia|slot|tipo|tipo_poi\n");
        for (var e : puestos.entrySet()) {
            Puesto p = e.getValue();
            sb.append(e.getKey()).append('|').append(p.poi()).append('|').append(p.uuid()).append('|')
                    .append(p.profesion()).append('|').append(p.guardia()).append('|').append(p.slot())
                    .append('|').append(p.tipo()).append('|').append(p.tipoPoi()).append('\n');
        }
        return sb.toString();
    }

    private static Map<Long, UUID> leerJobSites(String fase) {
        Path p = base.resolve("jobsite-" + fase + ".txt");
        if (!Files.exists(p)) {
            log("tabla de puestos previa ausente: " + p);
            return null;
        }
        Map<Long, UUID> out = new LinkedHashMap<>();
        try {
            for (String linea : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                if (linea.isBlank() || linea.startsWith("#")) {
                    continue;
                }
                String[] parts = linea.split("\\|", -1);
                if (parts.length < 3) {
                    continue;
                }
                out.putIfAbsent(Long.parseLong(parts[0]), UUID.fromString(parts[2]));
            }
        } catch (Exception e) {
            log("no pude leer la tabla de puestos previa: " + e);
            return null;
        }
        return out;
    }

    /**
     * Metricas de guardias: cuántos hay, cuántos fundadores, la máscara persistida por slot, si llevan goal de
     * guardia y qué equipo tienen. Solo se mide lo que se puede leer con seguridad; nada de esto altera la fase.
     */
    private static String guardiasJson() {
        boolean[] mascara = new boolean[SLOTS_FUNDADORES];
        int mascaraBits = 0;
        try {
            var saved = com.chipoodle.devilrpg.world.VillageSavedData.get(nivel);
            for (int slot = 0; slot < SLOTS_FUNDADORES; slot++) {
                mascara[slot] = saved.isGuardiaFundadoraCreada(OBJETIVO, slot);
                if (mascara[slot]) {
                    mascaraBits |= 1 << slot;
                }
            }
        } catch (RuntimeException ex) {
            log("guardias: no pude leer la mascara persistida: " + ex);
        }
        int total = 0;
        int fundadores = 0;
        int conGoal = 0;
        int armaduraCompleta = 0;
        int conArma = 0;
        int conEscudo = 0;
        List<String> detalle = new ArrayList<>();
        for (var v : aldeanos()) {
            if (!v.isAlive() || v.isRemoved() || v.isBaby()) {
                continue;
            }
            if (!com.chipoodle.devilrpg.entity.goal.VillagerGuardGoal.esGuardia(v)) {
                continue;
            }
            total++;
            boolean fund = com.chipoodle.devilrpg.world.VillageManager.esGuardiaFundadora(v);
            if (fund) {
                fundadores++;
            }
            boolean goal = tieneGoalDeGuardia(v);
            if (goal) {
                conGoal++;
            }
            int piezas = piezasDeArmadura(v);
            if (piezas == 4) {
                armaduraCompleta++;
            }
            boolean arma = !v.getMainHandItem().isEmpty();
            if (arma) {
                conArma++;
            }
            boolean escudo = v.getOffhandItem().is(Items.SHIELD);
            if (escudo) {
                conEscudo++;
            }
            int slot = v.getPersistentData().getInt(com.chipoodle.devilrpg.world.VillageManager.GUARD_INDEX_TAG);
            int tipo = v.getPersistentData().getInt(com.chipoodle.devilrpg.world.VillageManager.GUARD_TYPE_TAG);
            String mano = arma
                    ? String.valueOf(BuiltInRegistries.ITEM.getKey(v.getMainHandItem().getItem())) : "none";
            detalle.add("uuid=" + v.getUUID() + " slot=" + slot + " tipo=" + tipo + " fundador=" + fund
                    + " goal=" + goal + " armadura=" + piezas + "/4 mano=" + mano
                    + " escudo=" + escudo);
        }
        StringBuilder sb = new StringBuilder("{\"total\":").append(total)
                .append(",\"fundadores\":").append(fundadores)
                .append(",\"fundadores_esperados\":")
                .append(com.chipoodle.devilrpg.world.VillageManager.MILICIA_FUNDADORES)
                .append(",\"mascara_persistida\":[");
        for (int i = 0; i < SLOTS_FUNDADORES; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(mascara[i]);
        }
        sb.append(']').append(",\"mascara_bits\":").append(mascaraBits)
                .append(",\"mascara_completa\":").append(mascaraBits == (1 << SLOTS_FUNDADORES) - 1)
                .append(",\"con_goal_guardia\":").append(conGoal)
                .append(",\"armadura_completa\":").append(armaduraCompleta)
                .append(",\"con_arma\":").append(conArma)
                .append(",\"con_escudo\":").append(conEscudo)
                .append(",\"detalle\":").append(stringsJson(detalle))
                .append('}');
        return sb.toString();
    }

    private static int piezasDeArmadura(net.minecraft.world.entity.npc.Villager v) {
        int n = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            var stack = v.getItemBySlot(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof ArmorItem armadura
                    && armadura.getEquipmentSlot() == slot) {
                n++;
            }
        }
        return n;
    }

    private static boolean tieneGoalDeGuardia(net.minecraft.world.entity.npc.Villager v) {
        for (var wrapped : v.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof com.chipoodle.devilrpg.entity.goal.VillagerGuardGoal) {
                return true;
            }
        }
        return false;
    }

    private static String esperadoJson() {
        int puestos = com.chipoodle.devilrpg.world.VillageGenerator.puestosDelPueblo();
        int fundadores = com.chipoodle.devilrpg.world.VillageManager.MILICIA_FUNDADORES;
        return "{\"profesionales\":" + puestos + ",\"fundadores\":" + fundadores
                + ",\"uuid\":" + puestos + ",\"goals\":" + puestos
                + ",\"duplicados\":0,\"huerfanos\":0,\"perdidas_aldeanos\":0"
                + ",\"reanudados\":" + puestos + "}";
    }

    /** Anomalías con causa CONOCIDA y documentada: no cambian el veredicto, solo lo explican. */
    private static List<Causa> causasConocidas(int esperadoTotal, int jobSitePresentes, int goalsOk,
                                               int jobSitePerdidos, int jobSiteNuevos, int profesionales,
                                               List<String> violaciones) {
        List<Causa> out = new ArrayList<>();
        int puestos = com.chipoodle.devilrpg.world.VillageGenerator.puestosDelPueblo();
        int declarados = profesionalesDeclarados(violaciones, profesionales);
        if (jobSitePresentes < esperadoTotal) {
            out.add(new Causa("MEMORIA_JOB_SITE_INCOMPLETA",
                    "job_site_presentes=" + jobSitePresentes + "/" + esperadoTotal
                            + ": las memorias JOB_SITE pueden venir vacias al cargar y se reclaman durante la "
                            + "ventana (limitacion conocida); por eso no hay umbral duro de cobertura"));
        }
        if (goalsOk < esperadoTotal) {
            out.add(new Causa("GOALS_NO_INSTALADOS_POR_CARGA_PARCIAL",
                    "goals=" + goalsOk + "/" + esperadoTotal
                            + ": los goals no se persisten y el mantenimiento los reinstala durante la ventana"));
        }
        if (declarados > puestos) {
            out.add(new Causa("ALDEA_REPUEBLA",
                    "profesionales=" + declarados + "/" + puestos
                            + ": hay gente de mas en la aldea; el censo/roster la cuenta y el roster la acota"));
        }
        if ("reload1".equals(PHASE) || "reload2".equals(PHASE)) {
            if (jobSitePerdidos > 0) {
                out.add(new Causa("JOB_SITE_NO_RECLAMADO_ENTRE_FASES",
                        "job_site_perdidos=" + jobSitePerdidos + " respecto de la fase previa: compatible con la "
                                + "memoria vacia al cargar; se reclama (o no) durante la ventana"));
            }
            if (jobSiteNuevos > 0) {
                out.add(new Causa("JOB_SITE_RECLAMADO_EN_VENTANA",
                        "job_site_nuevos=" + jobSiteNuevos
                                + " respecto de la fase previa: reclamacion durante la ventana"));
            }
        }
        if (jobSiteAlCargarDuplicados > 0) {
            out.add(new Causa("JOB_SITE_DUPLICADO_AL_CARGAR_RECONCILIADO",
                    "job_site_al_cargar_duplicados=" + jobSiteAlCargarDuplicados
                            + ": el guardado traia dos duenios para un POI; la reconciliacion de carga debe dejar uno "
                            + "(si al cierre sigue habiendo duplicados, la fase falla)"));
        }
        if (jobSiteAlCargarHuerfanos > 0) {
            out.add(new Causa("JOB_SITE_HUERFANO_AL_CARGAR",
                    "job_site_al_cargar_huerfanos=" + jobSiteAlCargarHuerfanos
                            + ": la memoria persistida apunta a un POI que ya no existe; el reparto debe liberarla"));
        }
        return out;
    }

    /** Cuenta de profesionales que declara una violación {@code profesionales:N/M}; -1 si no aplica. */
    private static int numeroProfesionales(String violacion) {
        if (violacion == null || !violacion.startsWith("profesionales:")) {
            return -1;
        }
        String resto = violacion.substring("profesionales:".length());
        int barra = resto.indexOf('/');
        try {
            return Integer.parseInt(barra >= 0 ? resto.substring(0, barra) : resto);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int profesionalesDeclarados(List<String> violaciones, int porCenso) {
        int n = porCenso;
        for (String v : violaciones) {
            int m = numeroProfesionales(v);
            if (m >= 0) {
                n = Math.max(n, m);
            }
        }
        return n;
    }

    /**
     * Anomalías SIN causa medida. No se convierten en FAIL: se listan para que el operador sepa qué mirar. Los FAIL
     * duros ya los decide {@code okFase} con los mismos números.
     */
    private static List<Causa> causasDesconocidas(int duplicados, int jobSiteCambiados, List<String> violaciones,
                                                  int jobSiteAlCargarPerdidos, int perdidos) {
        List<Causa> out = new ArrayList<>();
        if (duplicados > 0) {
            out.add(new Causa("DUPLICADO_POR_COLISION_DE_PROPIEDAD",
                    "job_site_duplicados=" + duplicados
                            + ": dos aldeanos comparten POI y esta fase no guarda la secuencia que lo produjo"));
        }
        if (jobSiteCambiados > 0) {
            out.add(new Causa("CAMBIO_DE_DUENIO_SIN_EVIDENCIA",
                    "job_site_cambiados=" + jobSiteCambiados
                            + ": el POI existe en ambas fases con otro UUID; puede ser reasignacion legitima, "
                            + "se registra sin FAIL"));
        }
        if (jobSiteAlCargarPerdidos > 0) {
            out.add(new Causa("JOB_SITE_AL_CARGAR_DESAPARECIDO",
                    "job_site_al_cargar_perdidos=" + jobSiteAlCargarPerdidos
                            + ": el puesto estaba en la foto AL_CARGAR y ya no esta al cierre"));
        }
        if (perdidos > 0) {
            out.add(new Causa("ALDEANO_PERDIDO_SIN_CAUSA_MEDIDA",
                    "perdidos=" + perdidos + ": aldeano(s) esperados que no estan al cierre"));
        }
        List<String> noClasificadas = new ArrayList<>();
        int puestos = com.chipoodle.devilrpg.world.VillageGenerator.puestosDelPueblo();
        for (String v : violaciones) {
            if (v.startsWith("profesionales:")) {
                int n = numeroProfesionales(v);
                // > puestos lo explica ALDEA_REPUEBLA; < puestos con pérdidas lo explica ALDEANO_PERDIDO.
                if (n > puestos || (n < puestos && perdidos > 0)) {
                    continue;
                }
            }
            noClasificadas.add(v);
        }
        if (!noClasificadas.isEmpty()) {
            out.add(new Causa("INVARIANTE_DE_ROSTER_INCUMPLIDA", String.join("; ", noClasificadas)));
        }
        return out;
    }

    private static String causasJson(List<Causa> causas) {
        StringBuilder sb = new StringBuilder("[");
        for (Causa c : causas) {
            if (sb.length() > 1) {
                sb.append(',');
            }
            sb.append("{\"codigo\":\"").append(escape(c.codigo()))
                    .append("\",\"detalle\":\"").append(escape(c.detalle())).append("\"}");
        }
        return sb.append(']').toString();
    }

    private static String stringsJson(List<String> valores) {
        StringBuilder sb = new StringBuilder("[");
        for (String valor : valores) {
            if (sb.length() > 1) {
                sb.append(',');
            }
            sb.append('"').append(escape(valor)).append('"');
        }
        return sb.append(']').toString();
    }

    private static String numOrNull(int valor) {
        return valor < 0 ? "null" : String.valueOf(valor);
    }

    /**
     * Deja fuera los fragmentos con una clave JSON repetida (el ultimo ganaria y el JSON seguiria siendo valido, pero
     * seria ambiguo). Se registra en el log para no ocultar el fallo de construccion.
     */
    private static List<String> sinClavesDuplicadas(List<String> fragmentos) {
        Set<String> vistas = new LinkedHashSet<>();
        List<String> salida = new ArrayList<>();
        for (String fragmento : fragmentos) {
            String clave = claveDeFragmento(fragmento);
            if (clave != null && !vistas.add(clave)) {
                log("CLAVE JSON DUPLICADA (se descarta el fragmento): " + clave);
                continue;
            }
            salida.add(fragmento);
        }
        return salida;
    }

    private static String claveDeFragmento(String fragmento) {
        if (fragmento == null || !fragmento.startsWith("\"")) {
            return null;
        }
        int fin = fragmento.indexOf('"', 1);
        if (fin < 0 || fin + 1 >= fragmento.length() || fragmento.charAt(fin + 1) != ':') {
            return null;
        }
        return fragmento.substring(1, fin);
    }

    /** Escribe JSON validando antes con Gson; si la construccion saliera mal, deja el intento y un FAIL legible. */
    private static void escribirJson(String nombre, String contenido) {
        String valido = contenido;
        try {
            com.google.gson.JsonParser.parseString(contenido);
        } catch (RuntimeException ex) {
            log("JSON INVALIDO en " + nombre + ": " + ex);
            escribir(nombre + ".invalido.txt", contenido);
            valido = "{\"fase\":\"" + PHASE + "\",\"result\":\"FAIL\",\"fase_ok\":false,"
                    + "\"roster_invariantes_ok\":null,\"fallo\":\"json_invalido:"
                    + escape(ex.toString()) + "\"}";
        }
        escribir(nombre, valido);
    }

    /** Volcado final legible por máquina: bloque estructurado + comandos de reproducción. */
    private static void escribirDiagnostico(String veredicto, String esperadoJson, String observadoJson,
                                            String invariantesJson, String conocidasJson, String desconocidasJson,
                                            String guardiasJson) {
        StringBuilder sb = new StringBuilder("{\"run\":\"").append(escape(RUN)).append('"')
                .append(",\"fase\":\"").append(PHASE).append('"')
                .append(",\"sha\":\"").append(escape(leerSha())).append('"')
                .append(",\"momento\":\"").append(escape(java.time.Instant.now().toString())).append('"')
                .append(",\"result\":\"").append(veredicto).append('"')
                .append(",\"esperado\":").append(esperadoJson)
                .append(",\"observado\":").append(observadoJson)
                .append(",\"invariantes\":").append(invariantesJson)
                .append(",\"causa_conocida\":").append(conocidasJson)
                .append(",\"causa_desconocida\":").append(desconocidasJson)
                .append(",\"guardias\":").append(guardiasJson)
                .append(",\"job_site_detalle\":").append(stringsJson(jobSiteDetalle))
                .append(",\"reproducir\":{\"comando\":\".\\\\tools\\\\c4n\\\\lanzar-c4n.ps1 -Run <nuevo>\",")
                .append("\"diagnostico\":\".\\\\tools\\\\av\\\\diagnose.ps1 -Run <run>\",")
                .append("\"colector\":\".\\\\tools\\\\av\\\\collect.ps1\",")
                .append("\"nota\":\"ejecutar desde la raiz del repo; el runtime del servidor lo lanza el operador\"},")
                .append("\"artefactos\":{\"phase\":\"phase-").append(PHASE).append(".json\",")
                .append("\"snapshot\":\"snapshot-").append(PHASE).append(".txt\",")
                .append("\"jobsite\":\"jobsite-").append(PHASE).append(".txt\",")
                .append("\"log\":\"srv-").append(PHASE).append(".log\"}}");
        escribirJson("diagnostico-" + PHASE + ".json", sb.toString());
    }

    private static String leerSha() {
        try {
            Path p = base.resolve("sha.txt");
            if (Files.exists(p)) {
                return Files.readString(p, StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            log("no pude leer sha.txt: " + e);
        }
        return "desconocido";
    }

    private record Puesto(UUID uuid, String profesion, boolean guardia, int slot, int tipo, String poi,
                          String tipoPoi) {
    }

    private record Causa(String codigo, String detalle) {
    }

    private static int[] auditarJobSite() {
        Map<Long, UUID> owners = new LinkedHashMap<>();
        int presentes = 0;
        int huerfanas = 0;
        int duplicadas = 0;
        for (var v : aldeanos()) {
            if (!v.isAlive() || v.isRemoved() || v.isBaby()) {
                continue;
            }
            var memory = v.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
            if (memory.isEmpty()) {
                continue;
            }
            presentes++;
            BlockPos pos = memory.get().pos();
            if (nivel.getPoiManager().getType(pos).isEmpty()) {
                huerfanas++;
            }
            UUID previo = owners.putIfAbsent(pos.asLong(), v.getUUID());
            if (previo != null) {
                duplicadas++;
                log("JOB_SITE_DUPLICADO poi=" + pos.toShortString()
                        + " tipo=" + nivel.getPoiManager().getType(pos)
                        + " uuidA=" + previo + " profA=" + profesionDe(previo)
                        + " uuidB=" + v.getUUID() + " profB=" + v.getVillagerData().getProfession()
                        + " tick=" + ticksFase);
            }
        }
        return new int[]{presentes, huerfanas, duplicadas};
    }

    private static String jobSiteDe(net.minecraft.world.entity.npc.Villager v) {
        return v.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE)
                .map(m -> m.pos().toShortString() + "@" + nivel.getPoiManager().getType(m.pos()))
                .orElse("none");
    }

    private static String profesionDe(UUID id) {
        return nivel.getEntity(id) instanceof net.minecraft.world.entity.npc.Villager v
                ? v.getVillagerData().getProfession().toString() : "?";
    }

    private static String snapshotTexto() {
        StringBuilder sb = new StringBuilder("# uuid|profession|pos|goal\n");
        for (UUID id : esperados) {
            String prof = profesiones.getOrDefault(id, "?");
            String pos = "?";
            if (nivel.getEntity(id) instanceof net.minecraft.world.entity.npc.Villager v) {
                pos = v.blockPosition().toShortString();
            }
            sb.append(id).append('|').append(prof).append('|').append(pos).append('|')
                    .append(goalsInstalados.getOrDefault(id, "?")).append('\n');
        }
        return sb.toString();
    }

    private static void leerRosterPrevio() {
        String previa = "reload1".equals(PHASE) ? "prepare" : "reload1";
        Path p = base.resolve("snapshot-" + previa + ".txt");
        if (!Files.exists(p)) {
            log("snapshot previo ausente: " + p);
            return;
        }
        try {
            for (String linea : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                if (linea.isBlank() || linea.startsWith("#")) {
                    continue;
                }
                String[] parts = linea.split("\\|", -1);
                if (parts.length < 4) {
                    continue;
                }
                rosterPrev.put(UUID.fromString(parts[0]), parts[1] + "|" + parts[2] + "|" + parts[3]);
            }
        } catch (Exception e) {
            log("no pude leer snapshot previo: " + e);
        }
    }

    private static boolean tieneGoalProfesional(net.minecraft.world.entity.npc.Villager v) {
        String esperado = goalProfesionalEsperado(v);
        if (esperado.isEmpty()) {
            return false;
        }
        for (var wrapped : v.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal().getClass().getSimpleName().equals(esperado)) {
                return true;
            }
        }
        return false;
    }

    private static String goalProfesionalEsperado(net.minecraft.world.entity.npc.Villager v) {
        var p = v.getVillagerData().getProfession();
        if (p == net.minecraft.world.entity.npc.VillagerProfession.FARMER) return "VillagerFarmGoal";
        if (p == net.minecraft.world.entity.npc.VillagerProfession.WEAPONSMITH
                || p == net.minecraft.world.entity.npc.VillagerProfession.TOOLSMITH) return "VillagerSmithGoal";
        if (p == net.minecraft.world.entity.npc.VillagerProfession.MASON) return "VillagerMinerGoal";
        if (p == net.minecraft.world.entity.npc.VillagerProfession.NITWIT) return "VillagerCollectGoal";
        if (p == net.minecraft.world.entity.npc.VillagerProfession.FLETCHER) return "VillagerLumberjackGoal";
        if (p == net.minecraft.world.entity.npc.VillagerProfession.SHEPHERD) return "VillagerAnimalFarmGoal";
        if (p == net.minecraft.world.entity.npc.VillagerProfession.BUTCHER) return "VillagerCookGoal";
        if (p == net.minecraft.world.entity.npc.VillagerProfession.FISHERMAN) return "VillagerFisherGoal";
        if (p == net.minecraft.world.entity.npc.VillagerProfession.CLERIC) return "VillagerClericGoal";
        return "";
    }

    private static String uuidsJson(Iterable<UUID> ids) {
        StringBuilder sb = new StringBuilder();
        for (UUID id : ids) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append('"').append(id).append('"');
        }
        return sb.toString();
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static void escribir(String nombre, String contenido) {
        try {
            Files.writeString(base.resolve(nombre), contenido, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log("no pude escribir " + nombre + ": " + e);
        }
    }

    private static void fallar(String motivo, String detalle) {
        fallo = motivo + (detalle.isBlank() ? "" : " " + detalle);
        log("FALLO DE INSTRUMENTO: " + fallo);
        hecho = true;
        if (base != null) {
            escribir("phase-" + PHASE + ".json",
                    "{\"fase\":\"" + PHASE + "\",\"result\":\"FAIL\",\"fase_ok\":false,"
                            + "\"roster_invariantes_ok\":null,\"fallo\":\"" + escape(fallo) + "\"}");
            escribir("diagnostico-" + PHASE + ".json",
                    "{\"run\":\"" + escape(RUN) + "\",\"fase\":\"" + PHASE + "\",\"sha\":\""
                            + escape(leerSha()) + "\",\"momento\":\""
                            + escape(java.time.Instant.now().toString()) + "\",\"result\":\"FAIL\""
                            + ",\"fallo\":\"" + escape(fallo) + "\""
                            + ",\"esperado\":{},\"observado\":{}"
                            + ",\"invariantes\":{\"medido\":false,\"fase_ok\":false}"
                            + ",\"causa_conocida\":" + causasJson(List.of(new Causa("FALLO_DE_INSTRUMENTO", fallo)))
                            + ",\"causa_desconocida\":[]"
                            + ",\"reproducir\":{\"comando\":\".\\\\tools\\\\c4n\\\\lanzar-c4n.ps1 -Run <nuevo>\"}}");
            escribir("done-" + PHASE + ".txt", "FAIL");
        }
        if (server != null) {
            server.halt(false);
        }
    }

    private static void log(String mensaje) {
        DevilRpg.LOGGER.info("[C4N] {}", mensaje);
    }
}
