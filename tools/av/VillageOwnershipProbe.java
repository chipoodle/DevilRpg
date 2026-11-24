package com.chipoodle.devilrpg.debug;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.world.VillageGenerator;
import com.chipoodle.devilrpg.world.VillageManager;
import com.chipoodle.devilrpg.world.VillageSavedData;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * C4 · <b>SONDA DE PROPIEDAD DE PUESTOS (solo diagnóstico)</b>.
 * <p>
 * Se ejecuta sobre una <b>copia desechable</b> de un mundo C4 (nunca el mundo original), carga el
 * {@link ServerLevel} real, espera a que los aldeanos y los POI estén cargados, llama al <b>mantenimiento de
 * producción</b> ({@link VillageManager#ejecutarMantenimientoParaArnes}) con la cadencia real del pueblo
 * ({@code VILLAGE_POLL_TICKS} = 200) y vuelca por tick y al cierre:
 * <ul>
 *   <li>los POI de trabajo por tipo, con posición y {@code free_tickets};</li>
 *   <li>cada aldeano con UUID, profesión, {@code JOB_SITE}, {@code POTENTIAL_JOB_SITE} y si el tipo coincide;</li>
 *   <li>la tabla de los 11 puestos con estación (NITWIT no tiene: es legítimo) y su estado
 *       {@code JOB_SITE}/{@code POTENTIAL}/{@code TICKET_PERDIDO}/{@code LIBRE};</li>
 *   <li>la estampa read-only de reconciliación de {@link VillageManager#estampaDeReconciliacion()}.</li>
 * </ul>
 * Es ribete de diagnóstico: <b>no forma parte del mod</b> y solo se compila con {@code -Pa2nDiagnostics=true}.
 * No guarda el mundo; el lanzador trabaja sobre {@code build/av/probe-game} y el servidor se detiene solo al
 * terminar. Escribe JSON+texto en {@code build/av/ownership-probe-<run>/}.
 * <p>
 * Propiedades de JVM:
 * <ul>
 *   <li>{@code probe.run} (nombre de la carpeta), {@code probe.ticks} (2400 por defecto),</li>
 *   <li>{@code probe.cada} (200), {@code probe.raiz} (raíz del repo), {@code probe.objetivo} (0),</li>
 *   <li>{@code probe.mantenimiento} ({@code true}), {@code probe.esperaMax} (600 ticks de espera de carga).</li>
 * </ul>
 */
@EventBusSubscriber(modid = DevilRpg.MODID)
public final class VillageOwnershipProbe {

    private static final int OBJETIVO = Integer.getInteger("probe.objetivo", 0);
    private static final int TICKS = Integer.getInteger("probe.ticks", 2400);
    private static final int CADA = Math.max(20, Integer.getInteger("probe.cada", 200));
    private static final int ESPERA_MAX = Math.max(60, Integer.getInteger("probe.esperaMax", 600));
    private static final boolean MANTENIMIENTO = Boolean.parseBoolean(System.getProperty("probe.mantenimiento", "true"));
    private static final String RUN = System.getProperty("probe.run", "probe");
    private static final Path RAIZ = Path.of(System.getProperty("probe.raiz", "."));
    private static final Path BASE = RAIZ.resolve("build").resolve("av").resolve("ownership-probe-" + RUN);

    /** Los 11 oficios con estación, en el orden que usa el informe (NITWIT no está: no tiene estación). */
    private static final String[] OFICIOS_CON_ESTACION = {
            "farmer", "farmer", "farmer", "cleric", "toolsmith", "weaponsmith",
            "fletcher", "mason", "shepherd", "butcher", "fisherman"
    };

    private static ServerLevel nivel;
    private static MinecraftServer server;
    private static BlockPos centro;
    private static int ticks;
    private static boolean hecho;
    private static boolean cargaLista;
    private static int tickCarga = -1;
    private static int ultimoTamano = -1;
    private static int muestrasEstables;
    private static int mantenimientos;

    private VillageOwnershipProbe() {
    }

    @SubscribeEvent
    public static void alArrancar(final ServerStartedEvent evento) {
        server = evento.getServer();
        nivel = server.overworld();
        try {
            Files.createDirectories(BASE);
        } catch (Exception e) {
            log("no pude crear " + BASE + ": " + e);
        }
        VillageSavedData saved = VillageSavedData.get(nivel);
        centro = saved.getCentre(OBJETIVO);
        if (centro == null) {
            centro = VillageManager.centroDe(nivel, OBJETIVO);
        }
        if (centro == null) {
            fallar("SIN_CENTRO", "no hay centro persistido ni generado para el objetivo " + OBJETIVO);
            return;
        }
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                nivel.setChunkForced((centro.getX() >> 4) + dx, (centro.getZ() >> 4) + dz, true);
            }
        }
        log("ARRANCADO run=" + RUN + " objetivo=" + OBJETIVO + " centro=" + centro.toShortString()
                + " ticks=" + TICKS + " cada=" + CADA + " mantenimiento=" + MANTENIMIENTO);
        volcar("inicio");
    }

    @SubscribeEvent
    public static void alTick(final ServerTickEvent.Pre evento) {
        if (hecho || nivel == null) {
            return;
        }
        ticks++;
        if (!cargaLista) {
            comprobarCarga();
            if (!cargaLista) {
                return;
            }
        }
        if (ticks % CADA == 0) {
            if (MANTENIMIENTO) {
                try {
                    VillageManager.ejecutarMantenimientoParaArnes(nivel, OBJETIVO, centro);
                    mantenimientos++;
                } catch (RuntimeException e) {
                    log("mantenimiento t=" + ticks + " aviso: " + e);
                }
            }
            volcar("tick-" + ticks);
        }
        if (ticks >= TICKS) {
            hecho = true;
            JsonObject finalJson = volcar("final");
            escribir("fin.txt", "run=" + RUN + "\nticks=" + ticks + "\ncentro=" + centro.toShortString()
                    + "\ncarga_en_tick=" + tickCarga + "\nmantenimientos=" + mantenimientos + "\n"
                    + "resumen=" + resumenLinea(finalJson) + "\n");
            log("FIN ticks=" + ticks + " " + resumenLinea(finalJson));
            server.halt(false);
        }
    }

    // ------------------------------------------------------------------ carga

    private static void comprobarCarga() {
        if (ticks % 20 != 0) {
            return;
        }
        List<Villager> aldeanos = aldeanos();
        int estaciones = contarEstaciones();
        boolean entidadesCargadas = true;
        for (Villager v : aldeanos) {
            if (!nivel.areEntitiesLoaded(new ChunkPos(v.blockPosition()).toLong())) {
                entidadesCargadas = false;
                break;
            }
        }
        if (aldeanos.size() > 0 && aldeanos.size() == ultimoTamano && entidadesCargadas && estaciones >= 1) {
            muestrasEstables++;
        } else {
            muestrasEstables = 0;
            ultimoTamano = aldeanos.size();
        }
        if (muestrasEstables >= 3) {
            cargaLista = true;
            tickCarga = ticks;
            log("CARGA_LISTA tick=" + ticks + " aldeanos=" + aldeanos.size() + " estaciones=" + estaciones);
            volcar("cargada");
        } else if (ticks >= ESPERA_MAX) {
            cargaLista = true;
            tickCarga = ticks;
            log("CARGA_TIMEOUT tick=" + ticks + " aldeanos=" + aldeanos.size() + " estaciones=" + estaciones
                    + " (sigo para no colgar la sonda)");
            volcar("carga-timeout");
        }
    }

    private static int contarEstaciones() {
        int n = 0;
        for (Map.Entry<VillagerProfession, Integer> entrada : VillageGenerator.puestosPorOficio().entrySet()) {
            if (entrada.getKey() == VillagerProfession.NITWIT) {
                continue;
            }
            n += nivel.getPoiManager().getInSquare(entrada.getKey().heldJobSite(), centro,
                    VillageGenerator.FENCE_RADIUS, PoiManager.Occupancy.ANY).toList().size();
        }
        return n;
    }

    // ------------------------------------------------------------------ volcado

    private static JsonObject volcar(final String etiqueta) {
        JsonObject raiz = new JsonObject();
        raiz.addProperty("run", RUN);
        raiz.addProperty("etiqueta", etiqueta);
        raiz.addProperty("tick", ticks);
        raiz.addProperty("gameTime", nivel.getGameTime());
        raiz.addProperty("centro", centro.toShortString());
        raiz.addProperty("cargaLista", cargaLista);
        raiz.addProperty("tickCarga", tickCarga);
        raiz.addProperty("mantenimientos", mantenimientos);
        raiz.add("estampa", estampa());

        List<Villager> aldeanos = aldeanos();
        aldeanos.sort(Comparator.comparing(v -> v.getUUID().toString()));
        Map<Long, List<Villager>> dueniosJob = new HashMap<>();
        Map<Long, List<Villager>> dueniosPot = new HashMap<>();
        for (Villager v : aldeanos) {
            v.getBrain().getMemory(MemoryModuleType.JOB_SITE).ifPresent(sitio ->
                    dueniosJob.computeIfAbsent(sitio.pos().asLong(), k -> new ArrayList<>()).add(v));
            v.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).ifPresent(sitio ->
                    dueniosPot.computeIfAbsent(sitio.pos().asLong(), k -> new ArrayList<>()).add(v));
        }

        JsonArray aldeanosJson = new JsonArray();
        for (Villager v : aldeanos) {
            aldeanosJson.add(aldeanoJson(v));
        }
        raiz.add("aldeanos", aldeanosJson);

        List<JsonObject> pois = poisJson(dueniosJob, dueniosPot);
        JsonArray poisArr = new JsonArray();
        for (JsonObject p : pois) {
            poisArr.add(p);
        }
        raiz.add("pois", poisArr);

        JsonObject puestos = tablaDePuestos(pois);
        raiz.add("puestos", puestos);

        JsonObject resumen = resumen(aldeanos, pois, puestos);
        raiz.add("resumen", resumen);

        try {
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(raiz);
            escribir(etiqueta + ".json", json);
            escribir("ultimo.json", json);
            escribir(etiqueta + ".txt", texto(raiz));
            escribir("resumen.txt", resumenLinea(raiz) + "\n", true);
        } catch (Exception e) {
            log("no pude escribir el volcado " + etiqueta + ": " + e);
        }
        log("VOLCADO " + etiqueta + " " + resumenLinea(raiz));
        return raiz;
    }

    private static JsonObject aldeanoJson(final Villager v) {
        JsonObject o = new JsonObject();
        o.addProperty("uuid", v.getUUID().toString());
        o.addProperty("nombre", v.getPersistentData().getString("DevilRpgNombre"));
        o.addProperty("profesion", v.getVillagerData().getProfession().toString());
        o.addProperty("pos", v.blockPosition().toShortString());
        o.addProperty("guardia", com.chipoodle.devilrpg.entity.goal.VillagerGuardGoal.esGuardia(v));
        o.addProperty("bebe", v.isBaby());
        o.addProperty("distanciaCentro", distancia(centro, v.blockPosition()));
        añadirMemoria(o, "jobSite", v.getBrain().getMemory(MemoryModuleType.JOB_SITE), v);
        añadirMemoria(o, "potential", v.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE), v);
        o.addProperty("memorias", v.getBrain().getMemories().keySet().toString());
        return o;
    }

    private static void añadirMemoria(JsonObject o, String clave, Optional<GlobalPos> memoria, Villager v) {
        if (memoria.isEmpty()) {
            return;
        }
        BlockPos pos = memoria.get().pos();
        JsonObject m = new JsonObject();
        m.addProperty("pos", pos.toShortString());
        m.addProperty("tipo", tipoDe(pos));
        m.addProperty("freeTickets", freeTickets(pos));
        m.addProperty("tipoCoincide", tipoCoincide(v.getVillagerData().getProfession(), pos));
        m.addProperty("dentroAldea", VillageManager.esDeDentroDelMuro(centro, pos));
        m.addProperty("distanciaCentro", distancia(centro, pos));
        o.add(clave, m);
    }

    private static List<JsonObject> poisJson(final Map<Long, List<Villager>> dueniosJob,
                                             final Map<Long, List<Villager>> dueniosPot) {
        Map<Long, JsonObject> porPos = new LinkedHashMap<>();
        List<VillagerProfession> oficios = new ArrayList<>(VillageGenerator.puestosPorOficio().keySet());
        oficios.sort(Comparator.comparing(VillagerProfession::toString));
        for (VillagerProfession oficio : oficios) {
            if (oficio == VillagerProfession.NITWIT) {
                continue;
            }
            List<BlockPos> encontrados = nivel.getPoiManager().getInSquare(oficio.heldJobSite(), centro,
                    VillageGenerator.FENCE_RADIUS, PoiManager.Occupancy.ANY).toList().stream()
                    .map(registro -> registro.getPos()).toList();
            encontrados.sort(Comparator.comparingInt((BlockPos p) -> p.getX()).thenComparingInt(BlockPos::getZ));
            for (BlockPos pos : encontrados) {
                if (porPos.containsKey(pos.asLong())) {
                    continue;
                }
                JsonObject p = new JsonObject();
                p.addProperty("pos", pos.toShortString());
                p.addProperty("tipo", tipoDe(pos));
                p.addProperty("oficioConsultado", oficio.toString());
                p.addProperty("freeTickets", freeTickets(pos));
                p.addProperty("distanciaCentro", distancia(centro, pos));
                p.addProperty("dentroAldea", VillageManager.esDeDentroDelMuro(centro, pos));
                List<Villager> jobs = dueniosJob.getOrDefault(pos.asLong(), List.of());
                List<Villager> pots = dueniosPot.getOrDefault(pos.asLong(), List.of());
                p.addProperty("dueniosJobSite", jobs.size());
                p.addProperty("dueniosPotential", pots.size());
                p.addProperty("jobSiteUuids", uuids(jobs));
                p.addProperty("potentialUuids", uuids(pots));
                boolean jobValido = jobs.stream().anyMatch(v -> tipoCoincide(
                        v.getVillagerData().getProfession(), pos));
                p.addProperty("jobSiteValido", jobValido);
                p.addProperty("estado", estado(pos, jobs, pots));
                porPos.put(pos.asLong(), p);
            }
        }
        List<JsonObject> out = new ArrayList<>(porPos.values());
        out.sort(Comparator.comparing((JsonObject o) -> o.get("tipo").getAsString())
                .thenComparing(o -> o.get("pos").getAsString()));
        return out;
    }

    private static JsonObject tablaDePuestos(final List<JsonObject> pois) {
        JsonObject tabla = new JsonObject();
        JsonArray filas = new JsonArray();
        Map<String, List<JsonObject>> porTipo = new LinkedHashMap<>();
        for (JsonObject p : pois) {
            porTipo.computeIfAbsent(p.get("tipo").getAsString(), k -> new ArrayList<>()).add(p);
        }
        int indice = 0;
        for (String tipo : OFICIOS_CON_ESTACION) {
            String clave = "minecraft:" + tipo;
            List<JsonObject> candidatos = new ArrayList<>(porTipo.getOrDefault(clave, List.of()));
            candidatos.sort(Comparator.comparing(o -> o.get("pos").getAsString()));
            JsonObject origen = null;
            if (!candidatos.isEmpty()) {
                origen = candidatos.remove(0);
            }
            JsonObject fila = new JsonObject();
            fila.addProperty("indice", indice++);
            fila.addProperty("tipo", tipo);
            if (origen == null) {
                fila.addProperty("falta", true);
                fila.addProperty("estado", "FALTA");
            } else {
                for (String campo : List.of("pos", "freeTickets", "distanciaCentro", "dentroAldea",
                        "dueniosJobSite", "dueniosPotential", "jobSiteUuids", "potentialUuids",
                        "jobSiteValido", "estado")) {
                    fila.add(campo, origen.get(campo));
                }
            }
            filas.add(fila);
        }
        tabla.addProperty("esperados", OFICIOS_CON_ESTACION.length);
        tabla.add("filas", filas);
        int conJobValido = 0;
        Set<String> oficiosSinJob = new LinkedHashSet<>();
        for (var fila : filas) {
            JsonObject f = fila.getAsJsonObject();
            if (!f.has("falta") && f.get("jobSiteValido").getAsBoolean()) {
                conJobValido++;
            } else {
                oficiosSinJob.add(f.get("tipo").getAsString());
            }
        }
        tabla.addProperty("conJobSiteValido", conJobValido);
        JsonArray sinJob = new JsonArray();
        oficiosSinJob.forEach(sinJob::add);
        tabla.add("oficiosSinJob", sinJob);
        return tabla;
    }

    private static JsonObject resumen(final List<Villager> aldeanos, final List<JsonObject> pois,
                                      final JsonObject tabla) {
        JsonObject o = new JsonObject();
        int profesionales = 0;
        int guardias = 0;
        int nitwits = 0;
        int nitwitConJob = 0;
        int nitwitConPotential = 0;
        for (Villager v : aldeanos) {
            if (v.getVillagerData().getProfession() == VillagerProfession.NONE) {
                if (com.chipoodle.devilrpg.entity.goal.VillagerGuardGoal.esGuardia(v)) {
                    guardias++;
                }
            } else if (v.getVillagerData().getProfession() == VillagerProfession.NITWIT) {
                nitwits++;
                if (v.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE)) {
                    nitwitConJob++;
                }
                if (v.getBrain().hasMemoryValue(MemoryModuleType.POTENTIAL_JOB_SITE)) {
                    nitwitConPotential++;
                }
            } else {
                profesionales++;
            }
        }
        int estaciones = pois.size();
        int conJob = 0;
        int conPotential = 0;
        int perdidos = 0;
        int libres = 0;
        int conJobValido = 0;
        for (JsonObject p : pois) {
            String estado = p.get("estado").getAsString();
            switch (estado) {
                case "JOB_SITE" -> conJob++;
                case "POTENTIAL" -> conPotential++;
                case "TICKET_PERDIDO" -> perdidos++;
                case "LIBRE" -> libres++;
                default -> {
                }
            }
            if (p.get("jobSiteValido").getAsBoolean()) {
                conJobValido++;
            }
        }
        int conJobValidoTabla = tabla.get("conJobSiteValido").getAsInt();
        o.addProperty("aldeanos", aldeanos.size());
        o.addProperty("profesionales", profesionales);
        o.addProperty("guardias", guardias);
        o.addProperty("nitwits", nitwits);
        o.addProperty("estaciones", estaciones);
        o.addProperty("estacionesConJobSite", conJob);
        o.addProperty("estacionesConPotential", conPotential);
        o.addProperty("estacionesConJobSiteValido", conJobValido);
        o.addProperty("estacionesConJobSiteValidoTabla", conJobValidoTabla);
        o.addProperty("ticketsPerdidos", perdidos);
        o.addProperty("ticketsLibres", libres);
        o.addProperty("nitwitConJobSite", nitwitConJob);
        o.addProperty("nitwitConPotential", nitwitConPotential);
        o.addProperty("puestosEsperados", OFICIOS_CON_ESTACION.length);
        o.addProperty("onceDeOnce", conJobValidoTabla >= OFICIOS_CON_ESTACION.length
                && nitwitConJob == 0 && nitwitConPotential == 0);
        o.add("oficiosSinJob", tabla.get("oficiosSinJob"));
        return o;
    }

    private static JsonObject estampa() {
        VillageManager.EstampaReconciliacion e = VillageManager.estampaDeReconciliacion();
        JsonObject o = new JsonObject();
        o.addProperty("reclamaciones", e.reclamaciones());
        o.addProperty("ultimaReclamacionTick", e.ultimaReclamacionTick());
        o.addProperty("ticketsPerdidosLiberados", e.ticketsPerdidosLiberados());
        o.addProperty("promocionesDePotencial", e.promocionesDePotencial());
        o.addProperty("potencialesLiberados", e.potencialesLiberados());
        o.addProperty("potencialesLimpiados", e.potencialesLimpiados());
        o.addProperty("puestosInvalidosLimpiados", e.puestosInvalidosLimpiados());
        return o;
    }

    private static String resumenLinea(final JsonObject raiz) {
        JsonObject r = raiz.getAsJsonObject("resumen");
        JsonObject e = raiz.getAsJsonObject("estampa");
        return "job=" + r.get("estacionesConJobSite") + " pot=" + r.get("estacionesConPotential")
                + " perdidos=" + r.get("ticketsPerdidos") + " libres=" + r.get("ticketsLibres")
                + " validos=" + r.get("estacionesConJobSiteValidoTabla") + "/" + r.get("puestosEsperados")
                + " nitwitJob=" + r.get("nitwitConJobSite")
                + " onceDeOnce=" + r.get("onceDeOnce")
                + " reclamaciones=" + e.get("reclamaciones")
                + " promociones=" + e.get("promocionesDePotencial")
                + " liberados=" + e.get("ticketsPerdidosLiberados")
                + " limpiados=" + e.get("puestosInvalidosLimpiados");
    }

    private static String texto(final JsonObject raiz) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(raiz.get("etiqueta").getAsString())
                .append(" tick=").append(raiz.get("tick").getAsInt())
                .append(" gameTime=").append(raiz.get("gameTime").getAsLong())
                .append(" centro=").append(raiz.get("centro").getAsString())
                .append(" carga=").append(raiz.get("cargaLista").getAsBoolean())
                .append(" mantenimientos=").append(raiz.get("mantenimientos").getAsInt()).append('\n');
        sb.append("RESUMEN ").append(resumenLinea(raiz)).append('\n');
        for (var a : raiz.getAsJsonArray("aldeanos")) {
            JsonObject v = a.getAsJsonObject();
            sb.append("V ").append(v.get("uuid").getAsString())
                    .append(" ").append(v.get("profesion").getAsString())
                    .append(" pos=").append(v.get("pos").getAsString())
                    .append(" guardia=").append(v.get("guardia").getAsBoolean());
            if (v.has("jobSite")) {
                JsonObject m = v.getAsJsonObject("jobSite");
                sb.append(" JOB=").append(m.get("pos").getAsString())
                        .append("@").append(m.get("tipo").getAsString())
                        .append(" free=").append(m.get("freeTickets").getAsInt())
                        .append(" ok=").append(m.get("tipoCoincide").getAsBoolean());
            } else {
                sb.append(" JOB=none");
            }
            if (v.has("potential")) {
                JsonObject m = v.getAsJsonObject("potential");
                sb.append(" POT=").append(m.get("pos").getAsString())
                        .append("@").append(m.get("tipo").getAsString())
                        .append(" free=").append(m.get("freeTickets").getAsInt())
                        .append(" ok=").append(m.get("tipoCoincide").getAsBoolean());
            } else {
                sb.append(" POT=none");
            }
            sb.append('\n');
        }
        for (var p : raiz.getAsJsonArray("pois")) {
            JsonObject o = p.getAsJsonObject();
            sb.append("POI ").append(o.get("tipo").getAsString())
                    .append(" ").append(o.get("pos").getAsString())
                    .append(" free=").append(o.get("freeTickets").getAsInt())
                    .append(" estado=").append(o.get("estado").getAsString())
                    .append(" job=").append(o.get("jobSiteUuids").getAsString())
                    .append(" pot=").append(o.get("potentialUuids").getAsString()).append('\n');
        }
        for (var f : raiz.getAsJsonObject("puestos").getAsJsonArray("filas")) {
            JsonObject o = f.getAsJsonObject();
            sb.append("PUESTO ").append(o.get("indice").getAsInt())
                    .append(" ").append(o.get("tipo").getAsString());
            if (o.has("falta")) {
                sb.append(" FALTA");
            } else {
                sb.append(" ").append(o.get("pos").getAsString())
                        .append(" free=").append(o.get("freeTickets").getAsInt())
                        .append(" estado=").append(o.get("estado").getAsString())
                        .append(" valido=").append(o.get("jobSiteValido").getAsBoolean())
                        .append(" job=").append(o.get("jobSiteUuids").getAsString())
                        .append(" pot=").append(o.get("potentialUuids").getAsString());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ utilidades

    private static List<Villager> aldeanos() {
        return new ArrayList<>(nivel.getEntitiesOfClass(Villager.class,
                new AABB(centro).inflate(VillageGenerator.FENCE_RADIUS + 24.0D)));
    }

    private static String tipoDe(final BlockPos pos) {
        return nivel.getPoiManager().getType(pos)
                .flatMap(tipo -> tipo.unwrapKey().map(clave -> clave.location().toString()))
                .orElse("?");
    }

    private static int freeTickets(final BlockPos pos) {
        return nivel.getPoiManager().getType(pos).isPresent()
                ? nivel.getPoiManager().getFreeTickets(pos) : -1;
    }

    private static boolean tipoCoincide(final VillagerProfession profesion, final BlockPos pos) {
        Optional<Holder<PoiType>> tipo = nivel.getPoiManager().getType(pos);
        return tipo.isPresent() && profesion.heldJobSite().test(tipo.get());
    }

    private static String estado(final BlockPos pos, final List<Villager> jobs, final List<Villager> pots) {
        if (!jobs.isEmpty()) {
            return "JOB_SITE";
        }
        if (!pots.isEmpty()) {
            return "POTENTIAL";
        }
        int free = freeTickets(pos);
        return free == 0 ? "TICKET_PERDIDO" : "LIBRE";
    }

    private static String uuids(final List<Villager> lista) {
        List<String> out = new ArrayList<>();
        for (Villager v : lista) {
            out.add(v.getUUID() + "/" + v.getVillagerData().getProfession());
        }
        return String.join(",", out);
    }

    private static double distancia(final BlockPos centro, final BlockPos pos) {
        double dx = pos.getX() + 0.5D - centro.getX();
        double dz = pos.getZ() + 0.5D - centro.getZ();
        return Math.round(Math.sqrt(dx * dx + dz * dz) * 10.0D) / 10.0D;
    }

    private static void escribir(final String nombre, final String contenido) {
        escribir(nombre, contenido, false);
    }

    private static void escribir(final String nombre, final String contenido, final boolean append) {
        try {
            Path destino = BASE.resolve(nombre);
            if (append && Files.exists(destino)) {
                Files.writeString(destino, contenido, StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.APPEND);
            } else {
                Files.writeString(destino, contenido, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log("no pude escribir " + nombre + ": " + e);
        }
    }

    private static void fallar(final String codigo, final String detalle) {
        log("FALLO " + codigo + " :: " + detalle);
        escribir("fin.txt", "FALLO " + codigo + "\n" + detalle + "\n");
        if (server != null) {
            server.halt(false);
        }
    }

    private static void log(final String mensaje) {
        DevilRpg.LOGGER.info("[OWNPROBE] {}", mensaje);
    }
}
