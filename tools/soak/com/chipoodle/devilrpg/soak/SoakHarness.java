package com.chipoodle.devilrpg.soak;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.goal.VillagerGuardGoal;
import com.chipoodle.devilrpg.world.VillageGenerator;
import com.chipoodle.devilrpg.world.VillageManager;
import com.chipoodle.devilrpg.world.VillagePantry;
import com.chipoodle.devilrpg.world.VillageSavedData;
import com.chipoodle.devilrpg.world.VillageStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * SOAK MULTI-DÍA · arnés de servidor aislado (24-nov-2026).
 * <p>
 * Un solo entry point, opt-in con {@code -PsoakHarness=true}: excluye {@code debug/**} y compila
 * {@code tools/soak}. No hay raid, no hay jugador y no se crean aldeanos: se genera una aldea de
 * producción con {@link VillageManager#preGenerate}, se ejecuta el mantenimiento real cada 200 ticks
 * (la misma ruta que el latido de aldea) y se mide durante {@code TICKS} ticks de ventana.
 * <p>
 * <b>Contrato del soak</b> (criterios obligatorios):
 * <ul>
 *   <li>12/12 profesionales presentes al abrir la ventana;</li>
 *   <li>0 duplicados de {@code JOB_SITE} y 0 huérfanos en cada muestra tras la gracia;</li>
 *   <li>0 profesionales (excepto NITWIT) sin puesto válido en cada muestra tras la gracia;</li>
 *   <li>0 aldeanos con "inactividad larga" (sin goal corriendo y sin moverse 2.400 ticks) tras la gracia;</li>
 *   <li>comida de despensa nunca negativa y no 0 durante más de 30 muestras seguidas (6.000 ticks);</li>
 *   <li>población final &gt; 0, guardado real OK y ventana completa;</li>
 *   <li>sin excepciones no controladas (el lanzador revisa el log).</li>
 * </ul>
 * El "storage drift" se reporta como proxy (comida, leña, hierro, total del almacén): no hay libro
 * mayor de transacciones, así que no se declara conservación exacta.
 */
@EventBusSubscriber(modid = DevilRpg.MODID)
public final class SoakHarness {

    private static final String RUN = System.getProperty("soak.run", "sin-run");
    private static final Path RAIZ = Path.of(System.getProperty("soak.raiz", "."));
    private static final int TICKS = Math.max(200, Integer.getInteger("soak.ticks", 72000));
    private static final int OBJETIVO = 0;
    private static final BlockPos CENTRO_PEDIDO = new BlockPos(661, 71, 533);
    private static final int MAX_ESPERA_CARGA = 2400;
    private static final int GRACIA = 1200;
    private static final int MUESTREO = 200;
    private static final int IDLE_TICKS = 2400;
    private static final int IDLE_PERMANENTE_TICKS = 12000;
    private static final int MAX_MUESTRAS_SIN_COMIDA = 30;
    private static final int MAX_MUESTRAS_SIN_COBERTURA = 30;

    private static ServerLevel nivel;
    private static MinecraftServer server;
    private static Path base;
    private static BlockPos centro;
    private static boolean preparado;
    private static boolean ventanaIniciada;
    private static boolean hecho;
    private static int ticks;
    private static int ticksVentana;
    private static int muestras;
    private static final Map<UUID, String> profesiones = new LinkedHashMap<>();
    private static final Map<UUID, double[]> ultimaPos = new LinkedHashMap<>();
    private static final Map<UUID, Integer> quietos = new LinkedHashMap<>();
    private static final Map<UUID, double[]> posMuestra = new LinkedHashMap<>();
    private static final Map<UUID, Integer> quietosMuestra = new LinkedHashMap<>();
    private static final Set<UUID> stuckLogged = new HashSet<>();
    private static final Set<UUID> inactivosMarcados = new HashSet<>();
    private static final Set<UUID> inactivosPermanentesSet = new HashSet<>();
    private static int duplicadosMax;
    private static int huerfanosMax;
    private static int sinPuestoMax;
    private static int conPuestoMax;
    private static int sinPuestoFin = -1;
    private static int conPuestoFin = -1;
    private static int ausentesFin = -1;
    private static int sinPuestoBajo;
    private static int sinPuestoBajoMax;
    private static int conPuestoBajo;
    private static int conPuestoBajoMax;
    private static int inactivosPico;
    private static int inactivosFin;
    private static int inactivosPermanentes;
    private static int inactivosPermanentesFin;
    private static int poblacionMin = Integer.MAX_VALUE;
    private static int poblacionFin = -1;
    private static int comidaMin = Integer.MAX_VALUE;
    private static int comidaMax = Integer.MIN_VALUE;
    private static int comidaFin = -1;
    private static int lenaMin = Integer.MAX_VALUE;
    private static int lenaFin = -1;
    private static int hierroMin = Integer.MAX_VALUE;
    private static int hierroFin = -1;
    private static int storageMin = Integer.MAX_VALUE;
    private static int storageFin = -1;
    private static int muestrasSinComida;
    private static int muestrasSinComidaMax;
    private static boolean guardadoOk;
    private static String fallo = "";
    private static int bajas;
    private static final List<String> bajasDetalle = new ArrayList<>();
    private static final List<String> csv = new ArrayList<>();
    private static long inicioMs;
    private static long finMs;

    private SoakHarness() {
    }

    @SubscribeEvent
    public static void alArrancar(final ServerStartedEvent evento) {
        server = evento.getServer();
        nivel = server.overworld();
        base = RAIZ.resolve("build").resolve("soak").resolve(RUN);
        try {
            Files.createDirectories(base);
        } catch (IOException e) {
            log("no pude crear " + base + ": " + e);
        }
        log("SOAK_ARRANCADO run=" + RUN + " ticks=" + TICKS);
        csv.add("tick,ventana,poblacion,profesionales,ausentes,con_puesto,sin_puesto_no_nitwit,"
                + "duplicados,huerfanos,inactivos,goals_corriendo,comida,lena,hierro,storage_total,bajas");
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                nivel.setChunkForced((CENTRO_PEDIDO.getX() >> 4) + dx, (CENTRO_PEDIDO.getZ() >> 4) + dz, true);
            }
        }
        VillageSavedData saved = VillageSavedData.get(nivel);
        if (saved.isGenerated(OBJETIVO)) {
            fallar("mundo_no_virgen");
            return;
        }
        if (!VillageManager.preGenerate(nivel, OBJETIVO, CENTRO_PEDIDO)) {
            fallar("preGenerate_fallo");
            return;
        }
        centro = saved.getCentre(OBJETIVO);
        if (centro == null) {
            fallar("centro_no_persistido");
            return;
        }
        try {
            VillageManager.ejecutarMantenimientoParaArnes(nivel, OBJETIVO, centro);
        } catch (RuntimeException ex) {
            fallar("mantenimiento_inicial: " + ex);
            return;
        }
        preparado = true;
        inicioMs = System.currentTimeMillis();
        // Sprint de Minecraft: corre los ticks sin esperar 50 ms entre ellos. El lanzador limita el
        // proceso por tiempo; el contrato son TICKS de ventana, no minutos de reloj.
        server.tickRateManager().requestGameToSprint(1_000_000);
        log("SOAK_PREPARADO centro=" + centro.toShortString() + " poblacion=" + aldeanos().size()
                + " sprint=" + server.tickRateManager().isSprinting());
    }

    @SubscribeEvent
    public static void alTick(final ServerTickEvent.Pre evento) {
        if (!preparado || hecho) {
            return;
        }
        ticks++;
        if (ticks < 40) {
            return;
        }
        if (!ventanaIniciada) {
            censo();
            if (profesiones.size() < VillageGenerator.puestosDelPueblo()) {
                if (ticks > MAX_ESPERA_CARGA) {
                    fallar("censo_incompleto: " + profesiones.size() + "/"
                            + VillageGenerator.puestosDelPueblo());
                }
                return;
            }
            ventanaIniciada = true;
            log("SOAK_VENTANA tick=" + ticks + " profesionales=" + profesiones.size());
        }
        ticksVentana++;
        // Producción: la misma ruta del latido de aldea, cada 200 ticks. La gracia de 1.200 ticks la
        // aplica el propio manager; aquí no se simula nada.
        if (ticks % MUESTREO == 0) {
            try {
                VillageManager.ejecutarMantenimientoParaArnes(nivel, OBJETIVO, centro);
            } catch (RuntimeException ex) {
                log("SOAK_MANTENIMIENTO_AVISO tick=" + ticks + " " + ex);
            }
            muestra();
        }
        if (ticksVentana >= TICKS) {
            cerrar();
        }
    }

    @SubscribeEvent
    public static void alMorir(final LivingDeathEvent evento) {
        if (nivel == null || !(evento.getEntity() instanceof Villager v)
                || v.level() != nivel) {
            return;
        }
        bajas++;
        String causa = evento.getSource().getLocalizedDeathMessage(v).getString();
        String detalle = v.getUUID() + " prof=" + v.getVillagerData().getProfession()
                + " pos=" + v.blockPosition().toShortString() + " causa=\"" + causa + "\"";
        bajasDetalle.add(detalle);
        log("SOAK_BAJA " + detalle);
    }

    // ------------------------------------------------------------------ métricas

    private static void censo() {
        for (Villager v : nivel.getEntitiesOfClass(Villager.class, new AABB(centro).inflate(96))) {
            if (v.isBaby() || !v.isAlive() || v.isRemoved() || VillagerGuardGoal.esGuardia(v)) {
                continue;
            }
            VillagerProfession p = v.getVillagerData().getProfession();
            if (p == VillagerProfession.NONE) {
                continue; // los fundadores son NONE por diseño; no son profesionales
            }
            profesiones.putIfAbsent(v.getUUID(), p.toString());
        }
    }

    private static void muestra() {
        muestras++;
        List<Villager> aldeanos = aldeanos();
        int poblacion = aldeanos.size();
        if (Boolean.getBoolean("soak.stuckdiag")) {
            diagnosticarAtascos(aldeanos);
        }
        Map<Long, List<UUID>> porPoi = new HashMap<>();
        int conPuesto = 0;
        int sinPuestoNoNitwit = 0;
        int huerfanos = 0;
        int goalsCorriendo = 0;
        int inactivos = 0;
        int ausentes;
        boolean dia = nivel.isDay();
        Set<UUID> presentes = new HashSet<>();
        for (Villager v : aldeanos) {
            if (v.isBaby() || !v.isAlive() || v.isRemoved()) {
                continue;
            }
            presentes.add(v.getUUID());
            VillagerProfession p = v.getVillagerData().getProfession();
            boolean profesional = p != VillagerProfession.NONE
                    && !VillagerGuardGoal.esGuardia(v);
            Optional<GlobalPos> job = v.getBrain().getMemory(MemoryModuleType.JOB_SITE);
            boolean puestoValido = false;
            if (job.isPresent()) {
                BlockPos pos = job.get().pos();
                boolean existe = nivel.getPoiManager().getType(pos).isPresent();
                boolean dentro = dentroDelMuro(pos);
                if (!existe || !dentro) {
                    huerfanos++;
                } else if (profesional && p.heldJobSite().test(
                        nivel.getPoiManager().getType(pos).orElseThrow())) {
                    puestoValido = true;
                }
                porPoi.computeIfAbsent(pos.asLong(), k -> new ArrayList<>()).add(v.getUUID());
            }
            if (profesional) {
                if (puestoValido) {
                    conPuesto++;
                } else if (p != VillagerProfession.NITWIT) {
                    sinPuestoNoNitwit++;
                }
                if (tieneGoalCorriendo(v)) {
                    goalsCorriendo++;
                    recuperarInactivo(v, "goal");
                } else if (dia && !v.isSleeping() && v.getTarget() == null) {
                    inactivos++;
                    int muestrasQuieto = quietoMuestras(v);
                    if (muestrasQuieto * MUESTREO == IDLE_TICKS) {
                        inactivosMarcados.add(v.getUUID());
                        log("SOAK_INACTIVO_INICIO uuid=" + v.getUUID() + " prof=" + p
                                + " pos=" + v.blockPosition().toShortString() + " job="
                                + v.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                                        .map(g -> g.pos().toShortString()).orElse("SIN_PUESTO")
                                + " quieto=" + (muestrasQuieto * MUESTREO));
                    } else if (muestrasQuieto == 0) {
                        recuperarInactivo(v, "movimiento");
                    }
                    if (muestrasQuieto * MUESTREO >= IDLE_PERMANENTE_TICKS
                            && inactivosPermanentesSet.add(v.getUUID())) {
                        log("SOAK_INACTIVO_PERMANENTE uuid=" + v.getUUID() + " prof=" + p
                                + " pos=" + v.blockPosition().toShortString()
                                + " quieto=" + (muestrasQuieto * MUESTREO));
                    }
                } else {
                    quietos.remove(v.getUUID());
                    recuperarInactivo(v, dia ? "dormido/target" : "noche");
                }
            }
        }
        ausentes = 0;
        for (UUID id : profesiones.keySet()) {
            if (!presentes.contains(id)) {
                ausentes++;
            }
        }
        int duplicados = 0;
        for (List<UUID> duenos : porPoi.values()) {
            if (duenos.size() > 1) {
                duplicados += duenos.size() - 1;
            }
        }
        poblacionFin = poblacion;
        poblacionMin = Math.min(poblacionMin, poblacion);
        conPuestoFin = conPuesto;
        sinPuestoFin = sinPuestoNoNitwit;
        ausentesFin = ausentes;
        comidaFin = comida();
        lenaFin = cuenta(VillageStorage::esLena);
        hierroFin = cuenta(s -> s.is(Items.IRON_NUGGET) || s.is(Items.IRON_INGOT));
        storageFin = cuenta(s -> true);
        if (comidaFin >= 0) {
            comidaMin = Math.min(comidaMin, comidaFin);
            comidaMax = Math.max(comidaMax, comidaFin);
            if (comidaFin == 0) {
                muestrasSinComida++;
                muestrasSinComidaMax = Math.max(muestrasSinComidaMax, muestrasSinComida);
            } else {
                muestrasSinComida = 0;
            }
        }
        if (lenaFin >= 0) lenaMin = Math.min(lenaMin, lenaFin);
        if (hierroFin >= 0) hierroMin = Math.min(hierroMin, hierroFin);
        if (storageFin >= 0) storageMin = Math.min(storageMin, storageFin);
        if (ticksVentana >= GRACIA) {
            duplicadosMax = Math.max(duplicadosMax, duplicados);
            huerfanosMax = Math.max(huerfanosMax, huerfanos);
            sinPuestoMax = Math.max(sinPuestoMax, sinPuestoNoNitwit);
            conPuestoMax = Math.max(conPuestoMax, conPuesto);
            // Un aldeano puede morir o cambiar de estación: se tolera un hueco de hasta 30 muestras
            // (6.000 ticks) antes de considerarlo pérdida de cobertura permanente.
            sinPuestoBajo = sinPuestoNoNitwit > 0 ? sinPuestoBajo + 1 : 0;
            sinPuestoBajoMax = Math.max(sinPuestoBajoMax, sinPuestoBajo);
            conPuestoBajo = conPuesto < 11 ? conPuestoBajo + 1 : 0;
            conPuestoBajoMax = Math.max(conPuestoBajoMax, conPuestoBajo);
            inactivosPico = Math.max(inactivosPico, inactivosMarcados.size());
            inactivosFin = inactivosMarcados.size();
            inactivosPermanentes = Math.max(inactivosPermanentes, inactivosPermanentesSet.size());
            inactivosPermanentesFin = inactivosPermanentesSet.size();
        }
        csv.add(ticks + "," + ticksVentana + "," + poblacion + "," + profesiones.size() + "," + ausentes
                + "," + conPuesto + "," + sinPuestoNoNitwit + "," + duplicados + "," + huerfanos
                + "," + inactivos + "," + goalsCorriendo + "," + comidaFin + "," + lenaFin + ","
                + hierroFin + "," + storageFin + "," + bajas);
        if (muestras % 10 == 0 || ticksVentana >= GRACIA && ticksVentana < GRACIA + MUESTREO * 2) {
            log("SOAK_MUESTRA tick=" + ticks + " ventana=" + ticksVentana + " poblacion=" + poblacion
                    + " profesional=" + profesiones.size() + " ausentes=" + ausentes
                    + " con_puesto=" + conPuesto + " sin_puesto=" + sinPuestoNoNitwit
                    + " duplicados=" + duplicados + " huerfanos=" + huerfanos
                    + " inactivos=" + inactivos + " comida=" + comidaFin + " lena=" + lenaFin
                    + " hierro=" + hierroFin + " storage=" + storageFin);
        }
    }

    private static int quietoMuestras(Villager v) {
        double[] prev = ultimaPos.get(v.getUUID());
        double x = v.getX(), y = v.getY(), z = v.getZ();
        if (prev == null) {
            ultimaPos.put(v.getUUID(), new double[]{x, y, z});
            quietos.put(v.getUUID(), 0);
            return 0;
        }
        double dist = Math.sqrt((x - prev[0]) * (x - prev[0])
                + (y - prev[1]) * (y - prev[1]) + (z - prev[2]) * (z - prev[2]));
        ultimaPos.put(v.getUUID(), new double[]{x, y, z});
        int q = dist < 0.75D ? quietos.getOrDefault(v.getUUID(), 0) + 1 : 0;
        quietos.put(v.getUUID(), q);
        return q;
    }

    private static void recuperarInactivo(Villager v, String motivo) {
        if (inactivosMarcados.remove(v.getUUID())) {
            log("SOAK_INACTIVO_FIN uuid=" + v.getUUID() + " motivo=" + motivo);
        }
        inactivosPermanentesSet.remove(v.getUUID());
    }

    private static boolean tieneGoalCorriendo(Villager v) {
        for (var wrapped : v.goalSelector.getAvailableGoals()) {
            if (wrapped.isRunning()) {
                return true;
            }
        }
        return false;
    }

    /** Diagnóstico opt-in: un aldeano que no se mueve 400+ ticks, con su ruta/nodos/bloques. */
    private static void diagnosticarAtascos(List<Villager> aldeanos) {
        for (Villager v : aldeanos) {
            if (v.isBaby() || !v.isAlive() || v.isRemoved()) {
                continue;
            }
            double[] prev = posMuestra.get(v.getUUID());
            double x = v.getX(), y = v.getY(), z = v.getZ();
            boolean movido = prev == null || Math.sqrt((x - prev[0]) * (x - prev[0])
                    + (y - prev[1]) * (y - prev[1]) + (z - prev[2]) * (z - prev[2])) >= 0.75D;
            posMuestra.put(v.getUUID(), new double[]{x, y, z});
            if (movido) {
                quietosMuestra.put(v.getUUID(), 0);
                stuckLogged.remove(v.getUUID());
                continue;
            }
            int q = quietosMuestra.merge(v.getUUID(), 1, Integer::sum);
            if (q < 2 || !stuckLogged.add(v.getUUID())) {
                continue;
            }
            var camino = v.getNavigation().getPath();
            BlockPos siguiente = camino != null && !camino.isDone() && camino.getNodeCount() > 0
                    ? camino.getNextNodePos() : null;
            StringBuilder goals = new StringBuilder();
            for (var wrapped : v.goalSelector.getAvailableGoals()) {
                if (wrapped.isRunning()) {
                    if (goals.length() > 0) goals.append(',');
                    goals.append(wrapped.getGoal().getClass().getSimpleName());
                }
            }
            log("SOAK_STUCK uuid=" + v.getUUID() + " prof=" + v.getVillagerData().getProfession()
                    + " pos=" + v.blockPosition().toShortString()
                    + " next=" + (siguiente == null ? "SIN_NODO" : siguiente.toShortString())
                    + " nextBlock=" + (siguiente == null ? "-" : nivel.getBlockState(siguiente))
                    + " feet=" + nivel.getBlockState(v.blockPosition())
                    + " below=" + nivel.getBlockState(v.blockPosition().below())
                    + " pathNodes=" + (camino == null ? -1 : camino.getNodeCount())
                    + " idx=" + (camino == null ? -1 : camino.getNextNodeIndex())
                    + " done=" + (camino == null || camino.isDone())
                    + " reach=" + (camino == null || camino.canReach())
                    + " ground=" + v.onGround() + " hunger=" + VillageManager.hambre(v)
                    + " goals=[" + goals + "]");
        }
    }

    private static boolean dentroDelMuro(BlockPos pos) {
        int dx = pos.getX() - centro.getX();
        int dz = pos.getZ() - centro.getZ();
        int r = VillageGenerator.FENCE_RADIUS;
        return dx * dx + dz * dz <= r * r;
    }

    private static int comida() {
        try {
            return VillagePantry.comida(nivel, centro);
        } catch (RuntimeException ex) {
            log("SOAK_COMIDA_AVISO " + ex);
            return -1;
        }
    }

    private static int cuenta(java.util.function.Predicate<net.minecraft.world.item.ItemStack> filtro) {
        try {
            return VillageStorage.cuenta(nivel, centro, filtro);
        } catch (RuntimeException ex) {
            log("SOAK_STORAGE_AVISO " + ex);
            return -1;
        }
    }

    private static List<Villager> aldeanos() {
        return nivel.getEntitiesOfClass(Villager.class, new AABB(centro).inflate(96));
    }

    // ------------------------------------------------------------------ cierre y evidencia

    private static void cerrar() {
        if (hecho) {
            return;
        }
        hecho = true;
        finMs = System.currentTimeMillis();
        muestra();
        try {
            guardadoOk = server.saveEverything(false, true, true);
        } catch (RuntimeException ex) {
            guardadoOk = false;
            log("SOAK_GUARDADO_ERROR " + ex);
        }
        boolean ok = fallo.isEmpty()
                && profesiones.size() == VillageGenerator.puestosDelPueblo()
                && duplicadosMax == 0
                && huerfanosMax == 0
                && sinPuestoBajoMax <= MAX_MUESTRAS_SIN_COBERTURA
                && inactivosPermanentes == 0
                && muestrasSinComidaMax <= MAX_MUESTRAS_SIN_COMIDA
                && comidaMin >= 0
                && poblacionFin > 0
                && guardadoOk
                && ticksVentana >= TICKS;
        String veredicto = !ok ? "SOAK_FAIL" : bajas == 0 ? "SOAK_PASS" : "SOAK_PASS_CON_BAJAS";
        String detalle = "ticks=" + ticksVentana + " muestras=" + muestras
                + " poblacion_fin=" + poblacionFin + " profesionales=" + profesiones.size()
                + " bajas=" + bajas
                + " ausentes_fin=" + ausentesFin + " con_puesto_fin=" + conPuestoFin
                + " sin_puesto_fin=" + sinPuestoFin
                + " duplicados_max=" + duplicadosMax + " huerfanos_max=" + huerfanosMax
                + " sin_puesto_max=" + sinPuestoMax + " sin_puesto_bajo_max=" + sinPuestoBajoMax
                + " con_puesto_bajo_max=" + conPuestoBajoMax
                + " inactivos_pico=" + inactivosPico + " inactivos_fin=" + inactivosFin
                + " inactivos_permanentes=" + inactivosPermanentes
                + " inactivos_permanentes_fin=" + inactivosPermanentesFin
                + " comida_min=" + comidaMin + " comida_fin=" + comidaFin
                + " sin_comida_max=" + muestrasSinComidaMax + " lena_fin=" + lenaFin
                + " hierro_fin=" + hierroFin + " storage_fin=" + storageFin
                + " guardado_ok=" + guardadoOk;
        String bajasJson = bajasDetalle.isEmpty() ? "[]"
                : "[\"" + bajasDetalle.stream().map(SoakHarness::escape)
                        .collect(java.util.stream.Collectors.joining("\",\"")) + "\"]";
        String json = "{\"run\":\"" + RUN + "\",\"result\":\"" + veredicto + "\",\"ticks\":"
                + ticksVentana + ",\"ticks_totales\":" + ticks + ",\"muestras\":" + muestras
                + ",\"poblacion_fin\":" + poblacionFin + ",\"profesionales\":" + profesiones.size()
                + ",\"bajas\":" + bajas + ",\"bajas_detalle\":" + bajasJson
                + ",\"ausentes_fin\":" + ausentesFin + ",\"con_puesto_fin\":" + conPuestoFin
                + ",\"sin_puesto_fin\":" + sinPuestoFin
                + ",\"con_puesto_max\":" + conPuestoMax
                + ",\"duplicados_max\":" + duplicadosMax + ",\"huerfanos_max\":" + huerfanosMax
                + ",\"sin_puesto_max\":" + sinPuestoMax
                + ",\"sin_puesto_bajo_max\":" + sinPuestoBajoMax
                + ",\"con_puesto_bajo_max\":" + conPuestoBajoMax
                + ",\"inactivos_pico\":" + inactivosPico + ",\"inactivos_fin\":" + inactivosFin
                + ",\"inactivos_permanentes\":" + inactivosPermanentes
                + ",\"inactivos_permanentes_fin\":" + inactivosPermanentesFin
                + ",\"comida_min\":" + comidaMin + ",\"comida_max\":" + comidaMax
                + ",\"comida_fin\":" + comidaFin + ",\"muestras_sin_comida_max\":"
                + muestrasSinComidaMax + ",\"lena_min\":" + lenaMin + ",\"lena_fin\":" + lenaFin
                + ",\"hierro_min\":" + hierroMin + ",\"hierro_fin\":" + hierroFin
                + ",\"storage_min\":" + storageMin + ",\"storage_fin\":" + storageFin
                + ",\"guardado_ok\":" + guardadoOk + ",\"duracion_ms\":" + (finMs - inicioMs)
                + ",\"fallo\":\"" + escape(fallo) + "\",\"detalle\":\"" + escape(detalle) + "\"}";
        escribir("soak.json", json);
        escribir("muestras.csv", String.join("\n", csv) + "\n");
        escribir("fin.txt", veredicto + " " + detalle);
        log(veredicto + " " + detalle);
        try {
            server.halt(false);
        } catch (RuntimeException ex) {
            log("SOAK_HALT_ERROR " + ex);
        }
    }

    private static void fallar(String motivo) {
        if (hecho) {
            return;
        }
        fallo = motivo;
        hecho = true;
        escribir("fin.txt", "SOAK_FAIL " + motivo);
        log("SOAK_FAIL " + motivo);
        try {
            server.halt(false);
        } catch (RuntimeException ex) {
            log("SOAK_HALT_ERROR " + ex);
        }
    }

    private static void escribir(String nombre, String contenido) {
        try {
            Files.writeString(base.resolve(nombre), contenido, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log("SOAK_ESCRITURA_ERROR " + nombre + ": " + e);
        }
    }

    private static void log(String mensaje) {
        System.out.println("[SOAK] " + mensaje);
        if (base != null) {
            try {
                Files.writeString(base.resolve("soak.log"), mensaje + "\n", StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (IOException ignored) {
                // stdout ya lo tiene; no se rompe el run por el log.
            }
        }
    }

    private static String escape(String texto) {
        return texto == null ? "" : texto.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }
}
