package com.chipoodle.devilrpg.debug;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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
        boolean ok = true;
        int presentes = 0;
        int profesionEstable = 0;
        int reanudados = 0;
        int movidos = 0;
        int perdidos = 0;
        int goalsOk = 0;
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
        // C4 · invariantes del censo (12 puestos, 4 fundadores, un dueno por puesto, cupos). Informativo: se
        // registra en la fase y en el log; no invalida una campana ya aceptada, pero deja el diagnostico listo
        // para exigirlo cuando se revalide.
        java.util.List<com.chipoodle.devilrpg.world.VillageRosterInvariants.Miembro> roster = new java.util.ArrayList<>();
        for (var v : aldeanos()) {
            if (!v.isAlive() || v.isRemoved() || v.isBaby()) {
                continue;
            }
            long puesto = v.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE)
                    .map(m -> m.pos().asLong()).orElse(0L);
            boolean fund = com.chipoodle.devilrpg.world.VillageManager.esGuardiaFundadora(v);
            roster.add(new com.chipoodle.devilrpg.world.VillageRosterInvariants.Miembro(
                    v.getUUID(), v.getVillagerData().getProfession().toString(), puesto, fund));
        }
        java.util.Map<String, Integer> cupoRoster = new java.util.LinkedHashMap<>();
        com.chipoodle.devilrpg.world.VillageGenerator.puestosPorOficio()
                .forEach((k, n) -> cupoRoster.put(k.toString(), n));
        var invariantes = com.chipoodle.devilrpg.world.VillageRosterInvariants.comprobar(
                roster, com.chipoodle.devilrpg.world.VillageGenerator.puestosDelPueblo(), cupoRoster, 4);
        log("ROSTER_INVARIANTS ok=" + invariantes.ok() + " violaciones=" + invariantes.violaciones());
        chequeos.add("\"roster_invariantes_ok\":" + invariantes.ok());
        int duplicados = jobSite[2];
        boolean registrados = presentes == esperadoTotal;
        boolean reanudadosOk = reanudados == esperadoTotal;
        boolean identidades = profesionEstable == esperadoTotal;
        boolean sinPerdidas = perdidos == 0;
        boolean sinDuplicados = duplicados == 0;
        boolean jobSiteOk = jobSite[1] == 0 && jobSite[2] == 0;
        boolean actividad = activos.size() + movidos >= Math.max(1, (esperadoTotal * 5) / 6);
        boolean preparadoOk = sinPerdidas && registrados && identidades && sinDuplicados && jobSiteOk;
        boolean okFase = preparadoOk && reanudadosOk && goalsOk == esperadoTotal;

        chequeos.add("\"fase_ok\":" + okFase);
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
        if (!fallo.isEmpty()) {
            chequeos.add("\"fallo\":\"" + escape(fallo) + "\"");
        }

        String veredicto = okFase && fallo.isEmpty() ? "PASS" : "FAIL";
        escribir("phase-" + PHASE + ".json", "{\"fase\":\"" + PHASE + "\",\"result\":\"" + veredicto
                + "\",\"uuid_set\":[" + uuidsJson(profesEsperadas.keySet()) + "],"
                + String.join(",", chequeos) + "}");
        escribir("snapshot-" + PHASE + ".txt", snapshotTexto());
        log("FASE " + PHASE + " -> " + veredicto + " presentes=" + presentes + "/" + esperadoTotal
                + " reanudados=" + reanudados + "/" + esperadoTotal + " jobsite=" + jobSite[0]
                + " huerfanos=" + jobSite[1] + " duplicados=" + duplicados
                + " activos=" + activos.size() + " movidos=" + movidos);
        escribir("done-" + PHASE + ".txt", veredicto);
        try {
            server.saveEverything(false, true, true);
            log("mundo guardado phase=" + PHASE);
            Files.writeString(base.resolve("saved-" + PHASE + ".txt"),
                    String.valueOf(System.currentTimeMillis()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log("save error: " + e);
        }
        server.halt(false);
    }

    /** Censo de JOB_SITE inmediatamente tras la carga, ANTES de cualquier mantenimiento: persistido vs creado. */
    private static void censoJobSitesAlCargar() {
        int cargados = 0;
        for (UUID id : rosterPrev.keySet()) {
            if (nivel.getEntity(id) instanceof net.minecraft.world.entity.npc.Villager v) {
                cargados++;
                log("AL_CARGAR uuid=" + id + " prof=" + v.getVillagerData().getProfession()
                        + " jobSite=" + jobSiteDe(v) + " pos=" + v.blockPosition().toShortString());
            }
        }
        log("AL_CARGAR cargados=" + cargados + "/" + rosterPrev.size());
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
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
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
                    "{\"fase\":\"" + PHASE + "\",\"result\":\"FAIL\",\"fallo\":\"" + escape(fallo) + "\"}");
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
