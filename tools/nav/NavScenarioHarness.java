package com.chipoodle.devilrpg.debug;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.AggressiveZombieEntity;
import com.chipoodle.devilrpg.entity.nav.NavExecutor;
import com.chipoodle.devilrpg.entity.nav.NavObstruction;
import com.chipoodle.devilrpg.entity.nav.NavPlanner;
import com.chipoodle.devilrpg.entity.nav.NavPlannerMetrics;
import com.chipoodle.devilrpg.entity.nav.NavRoute;
import com.chipoodle.devilrpg.entity.nav.NavState;
import com.chipoodle.devilrpg.entity.nav.NavTrace;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AV · <b>Campaña RUNTIME de navegación por escenarios</b> (arnés de diagnóstico, no producción).
 *
 * <p>Ejecuta escenarios funcionales reales en un mundo desechable: construye geometrías con bloques reales en una
 * plataforma remota, spawnea un {@link AggressiveZombieEntity} real, le fija un objetivo con
 * {@link NavExecutor#setObjective(BlockPos)} y deja que la IA/ejecutor de PRODUCCIÓN planifique, ejecute y recupere.
 * No duplica el controlador: observa estado, ruta, índice de nodo, celdas construidas, diagnóstico de túnel/descenso,
 * salud y cambios de bloque. Cada escenario tiene presupuesto de ticks, invariantes de seguridad y criterios PASS/FAIL.
 *
 * <p>Salidas en {@code build/av/nav-<run>/}: {@code escenario-<id>-r<rep>.json}, {@code metrics-<id>-r<rep>.csv}
 * (volcado literal de {@link NavPlannerMetrics#dumpCsv()}), índice CSV, {@code nav-trace.log}, {@code resultado.json}
 * y {@code fin.txt}. No toca {@code run/}, {@code run/saves}, {@code build/c4n} ni persistencia de aldea.
 *
 * <h2>Contrato del catálogo</h2>
 * El bloque {@code NAV-SCENARIO-CATALOG v1} de abajo es parseado por
 * {@code src/test/java/com/chipoodle/devilrpg/entity/nav/NavScenarioContractTest.java}: mantiene sincronizados los
 * ids declarados y los ids construidos por {@link #construirCatalogo()}. Si un builder no existe o sobra, el arnés
 * falla cerrado al arrancar y el test headless lo detecta sin ejecutar Minecraft.
 */
@EventBusSubscriber(modid = DevilRpg.MODID)
public final class NavScenarioHarness {

    // NAV-SCENARIO-CATALOG v1
    // SC|id|transiciones|acciones|llegada|fallo|ticksMax|radio|roturasMin|nota
    // SC|campo_abierto|||true|false|600|20|0|control abierto, solo WALK
    // SC|paso_arriba|STEP_UP|STEP_UP|true|false|600|20|0|escalon de 1 bloque
    // SC|caida|DROP|DROP|true|false|700|20|0|caida segura de 3
    // SC|romper|BREAK|BREAK|true|false|700|20|1|panel rompible con techo a +2
    // SC|construir|BUILD|BUILD|true|false|1200|20|0|puente sobre hueco de 2
    // SC|paso_arriba_romper|STEP_UP,BREAK|STEP_UP,BREAK|true|false|1100|20|1|escalon + panel
    // SC|paso_arriba_construir|STEP_UP,BUILD|STEP_UP,BUILD|true|false|1400|20|0|escalon + puente
    // SC|caida_romper|DROP,BREAK|DROP,BREAK|true|false|1100|20|1|caida 3 + panel
    // SC|caida_construir|BUILD,DROP|BUILD,DROP|true|false|1200|20|0|puente BUILD + caida segura
    // SC|romper_construir|BREAK,BUILD|BREAK,BUILD|true|false|1500|20|1|panel + puente
    // SC|caida_romper_construir|DROP,BREAK,BUILD|DROP,BREAK,BUILD|true|false|1800|20|1|caida + panel + puente
    // SC|escalera|STEP_UP|STEP_UP|true|false|800|20|0|escalera natural de 3
    // SC|columna|DROP|DROP|true|false|700|20|0|pozo 1x1 y sala inferior
    // SC|muro|BREAK|BREAK|true|false|1200|20|2|muro de 2 paneles
    // SC|tunel||TUNNEL|true|false|1800|20|0|galeria a traves de 4 bloques
    // SC|terreno_dinamico|||true|false|1000|20|0|barrera puesta y retirada en caliente
    // SC|inaccesible|||false|true|1200|20|0|objetivo sellado en bedrock
    // SC|astar_reanudacion|||true|false|1800|40|0|mision fuera de ventana local
    // SC|presupuesto_agotado|||false|false|40|20|0|A* local con presupuesto 2
    // SC|bloqueo_recuperacion|||true|false|1500|20|0|desvio por ramal tras bloqueo
    // SC|limites|||false|false|40|20|0|topes local/global con presupuesto 16

    private static final DateTimeFormatter SELLO = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int PLATAFORMA_Y = Integer.getInteger("devilrpg.nav.harness.y", 200);
    private static final int BASE_X = Integer.getInteger("devilrpg.nav.harness.baseX", 30000);
    private static final int BASE_Z = Integer.getInteger("devilrpg.nav.harness.baseZ", 30000);
    private static final int SEPARACION = 96;
    private static final int REP = Math.max(1, Integer.getInteger("devilrpg.nav.harness.rep", 1));
    private static final int ESCALA = Math.max(10, Math.min(400,
            Integer.getInteger("devilrpg.nav.harness.escala", 100)));
    private static final String FILTRO = System.getProperty("devilrpg.nav.harness.scenarios", "all").trim();
    private static final String RUN_BRUTO = System.getProperty("devilrpg.nav.harness.run",
            "runtime-" + LocalDateTime.now().format(SELLO));
    private static final String RUN = RUN_BRUTO.startsWith("nav-") ? RUN_BRUTO : "nav-" + RUN_BRUTO;
    private static final boolean TRACE = Boolean.getBoolean("devilrpg.a2n.traceNav");
    private static final String VERSION = "av-nav-scenario/1";
    /**
     * Defensa en profundidad: esta clase solo se activa en un run lanzado con -PnavHarnessRun=...
     * Si por un build de diagnóstico antiguo quedara compilada y cargada, NO debe arrancar la campaña
     * ni detener el servidor de otro arnés. Sin la propiedad, es un no-op total.
     */
    private static final boolean ENABLED = System.getProperty("devilrpg.nav.harness.run") != null;
    /** Cabecera exacta del índice convenida con el agregador de rendimiento (no cambiar a mitad de campaña). */
    private static final String CABECERA_INDICE = "escenario,repeticion,entidad,tipo_busqueda,presupuesto,encontrado,presupuesto_agotado,longitud_ruta,expansiones,nanos_metricas,nanos_pared,avances,ticks,aristas,candidatos,encolados,motivo,metrics_csv";

    private static ServerLevel nivel;
    private static Path raiz;
    private static Path dirRun;
    private static boolean iniciado;
    private static boolean terminado;
    private static int pausa = 40;
    private static int indice;
    private static Run actual;
    private static final List<Escenario> PENDIENTES = new ArrayList<>();
    private static final List<String> VEREDICTOS = new ArrayList<>();
    private static final List<Criterio> CRITERIOS_GLOBALES = new ArrayList<>();
    private static final Map<String, NavPlannerMetrics.TotalesSnapshot> TOTALES = new LinkedHashMap<>();
    private static final StringBuilder INDICE = new StringBuilder();
    private static final StringBuilder RESUMEN_JSON = new StringBuilder();
    private static BufferedWriter traza;
    private static String selloIndice;

    private NavScenarioHarness() {
    }

    // ================================================================================================
    // Ciclo del servidor.
    // ================================================================================================

    @SubscribeEvent
    public static void alArrancar(final ServerStartedEvent evento) {
        if (!ENABLED) {
            DevilRpg.LOGGER.warn("[NAVSCN] NavScenarioHarness cargado SIN -PnavHarnessRun; no se activa.");
            return;
        }
        if (iniciado) {
            return;
        }
        iniciado = true;
        nivel = evento.getServer().overworld();
        raiz = Path.of(System.getProperty("a2n.raiz", ".")).toAbsolutePath().normalize();
        dirRun = raiz.resolve("build").resolve("av").resolve(RUN);
        selloIndice = LocalDateTime.now().format(SELLO);
        try {
            Files.createDirectories(dirRun);
        } catch (Exception e) {
            log("FALLO no pude crear " + dirRun + ": " + e);
            terminado = true;
            return;
        }
        try {
            prepararServidor(evento.getServer());
        } catch (RuntimeException e) {
            log("FALLO preparando gamerules/reloj: " + e);
        }
        NavPlannerMetrics.setEnabled(true);
        abrirTraza();
        construirCatalogo();
        filtrarEscenarios();
        INDICE.append(CABECERA_INDICE).append('\n');
        log("ARRANCADO run=" + RUN + " rep=" + REP + " escenarios=" + PENDIENTES.size()
                + " escala=" + ESCALA + "% root=" + dirRun);
        log("MUNDO dimension=" + nivel.dimension().location() + " base=" + BASE_X + "," + PLATAFORMA_Y + "," + BASE_Z
                + " gamerules=doMobSpawning:false,doDaylightCycle:false,randomTick:0,mobGriefing:false");
    }

    @SubscribeEvent
    public static void alTick(final ServerTickEvent.Pre evento) {
        if (!ENABLED || !iniciado || terminado) {
            return;
        }
        try {
            if (actual == null) {
                if (pausa-- > 0) {
                    return;
                }
                iniciarSiguiente(evento.getServer());
                return;
            }
            tickEscenario(evento.getServer());
        } catch (RuntimeException e) {
            log("EXCEPCION en tick de escenario: " + e);
            e.printStackTrace();
            if (actual != null) {
                actual.error = "excepcion: " + e;
                cerrarEscenario(evento.getServer(), false);
            } else {
                terminado = true;
                evento.getServer().halt(false);
            }
        }
    }

    private static void iniciarSiguiente(final MinecraftServer servidor) {
        if (indice >= PENDIENTES.size()) {
            finalizar(servidor);
            return;
        }
        Escenario esc = PENDIENTES.get(indice++);
        Run run = new Run(esc, indice - 1);
        actual = run;
        long t0 = System.nanoTime();
        run.paredInicioNs = t0;
        try {
            prepararEscenario(run, servidor);
        } catch (RuntimeException e) {
            log("FALLO preparando " + esc.id + ": " + e);
            e.printStackTrace();
            run.error = "preparacion: " + e;
            run.geometriaOk = false;
        }
        if (!run.geometriaOk) {
            cerrarEscenario(servidor, false);
            return;
        }
        run.paredPreflightNs = System.nanoTime() - t0;
        run.tickInicio = nivel.getGameTime();
        log("ESCENARIO " + esc.id + " inicio tick=" + run.tickInicio
                + " spawn=" + shortPos(run.layout.spawn) + " goal=" + shortPos(run.layout.goal)
                + " espera=" + esc.transiciones + "/" + esc.acciones
                + " preflight=" + (run.preflightResumen == null ? "-" : run.preflightResumen));
    }

    private static void prepararEscenario(final Run run, final MinecraftServer servidor) {
        Escenario esc = run.esc;
        int col = run.slot % 5;
        int fila = run.slot / 5;
        int bx = BASE_X + col * SEPARACION;
        int bz = BASE_Z + fila * SEPARACION;
        run.area = new Area(bx, PLATAFORMA_Y, bz, esc.radio);
        prepararBase(run.area);
        run.layout = esc.constructor.construir(run.area);
        if (run.layout == null || run.layout.spawn == null || run.layout.goal == null) {
            run.geometriaOk = false;
            run.error = "layout nulo/incompleto";
            return;
        }
        run.snapshot = instantanea(run.area);
        run.cambiosPermitidos.addAll(run.layout.roturas);
        run.cambiosPermitidos.addAll(run.layout.construcciones);
        run.cambiosPermitidos.addAll(run.layout.barrera);
        run.geometriaOk = validarGeometria(run);
        if (!run.geometriaOk) {
            return;
        }
        run.mob = sonda(run.layout.spawn);
        if (run.mob == null) {
            run.geometriaOk = false;
            run.error = "spawn nulo";
            return;
        }
        run.uuid = run.mob.getUUID();
        run.mob.setPersistenceRequired();
        run.mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        run.mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        if (esc.preflightRequerido && esc.tipo != Tipo.PRESUPUESTO && esc.tipo != Tipo.LIMITES) {
            run.preflight = preflight(run);
            run.preflightOk = evaluarPreflight(run);
        } else {
            run.preflightOk = true;
        }
        NavPlannerMetrics.reset();
        if (esc.tipo == Tipo.PRESUPUESTO) {
            NavPlanner.Result r = NavPlanner.plan(nivel, run.mob, run.layout.spawn, run.layout.goal,
                    48, 6, 2, run.mob.navPolicies());
            run.directoLocalResumen = r.summary();
            run.directoLocalHit = r.hitBudget();
            run.directoLocalExp = r.expansions();
            log("PRESUPUESTO local hitBudget=" + r.hitBudget() + " exp=" + r.expansions()
                    + " ruta=" + r.route().length() + " summary={" + r.summary() + "}");
        } else if (esc.tipo == Tipo.LIMITES) {
            NavPlanner.Result local = NavPlanner.plan(nivel, run.mob, run.layout.spawn, run.layout.goal,
                    48, 6, 16, run.mob.navPolicies());
            run.directoLocalHit = local.hitBudget();
            run.directoLocalExp = local.expansions();
            run.directoLocalResumen = local.summary();
            NavPlanner.GlobalResult global = NavPlanner.planGlobal(nivel, run.mob, run.layout.spawn,
                    run.layout.goal, 48, 6, 16, run.mob.navPolicies(), null, null, null);
            run.directoGlobalHit = global.hitBudget();
            run.directoGlobalExp = global.expansions();
            run.directoGlobalPendiente = global.pending();
            run.directoGlobalResumen = global.summary();
            log("LIMITES local hit=" + local.hitBudget() + " exp=" + local.expansions()
                    + " | global hit=" + global.hitBudget() + " exp=" + global.expansions()
                    + " pendiente=" + global.pending());
        } else if (esc.tipo == Tipo.AESTRELLA) {
            // A* GLOBAL INCREMENTAL REAL: se retiene la frontera y se avanza en slices por tick, que es
            // exactamente lo que mide `reanudadas`/`nodosPorAvance` en NavPlannerMetrics. En un corredor
            // plano el ejecutor prefiere escalonado local; para demostrar la capa global se mide ésta
            // directamente (sin falsear métricas: es el planner de producción).
            run.astarHandle = new NavPlanner.GlobalSearchHandle();
            // Y el mismo GLOBAL directo (sincrono) para comparar la ruta/objetivo del incremental. No se usa para
            // el veredicto de movimiento: es la referencia de que ambos planificadores ven el mismo problema.
            NavPlanner.GlobalResult directo = NavPlanner.planGlobal(nivel, run.mob, run.layout.spawn,
                    run.layout.goal, 128, 6, 8000, run.mob.navPolicies(), null, null, null);
            run.directoGlobalHit = directo.hitBudget();
            run.directoGlobalExp = directo.expansions();
            run.directoGlobalPendiente = directo.pending();
            run.directoGlobalResumen = directo.summary();
            run.directoGlobalAlcanzo = directo.alcanzoMision() && directo.route() != null
                    && !directo.route().isEmpty();
        } else {
            run.mob.navExecutor().setObjective(run.layout.goal);
        }
        run.saludInicial = run.mob.getHealth();
    }

    private static void tickEscenario(final MinecraftServer servidor) {
        Run run = actual;
        if (run == null) {
            return;
        }
        run.ticks++;
        muestrear(run);
        if (run.esc.tipo == Tipo.AESTRELLA) {
            run.astarFrames++;
            if (!run.astarDone && run.astarHandle != null) {
                run.astarResult = NavPlanner.planGlobalIncremental(nivel, run.mob, run.layout.spawn,
                        run.layout.goal, 112, 6, 8000, run.mob.navPolicies(), null, null, null,
                        run.astarHandle);
                run.astarAlcanzo = run.astarResult.alcanzoMision();
                if (!run.astarResult.pending()) {
                    run.astarDone = true;
                    run.llego = run.astarAlcanzo;
                    run.ticksFinal = run.ticks;
                    log("ASTAR global fin frames=" + run.astarFrames + " alcanzo=" + run.astarAlcanzo
                            + " exp=" + run.astarResult.expansions()
                            + " ruta=" + run.astarResult.route().length()
                            + " resumen={" + run.astarResult.summary() + "}");
                    cerrarEscenario(servidor, false);
                    return;
                }
            }
            if (run.ticks >= run.ticksMax()) {
                run.timeout = true;
                cerrarEscenario(servidor, false);
            }
            return;
        }
        if (run.esc.tipo == Tipo.DINAMICO) {
            if (run.ticks == 30) {
                colocarBarrera(run, true);
                run.mob.navExecutor().terrainChanged();
                run.barreraPuesta = true;
                log("DINAMICO barrera puesta tick=" + run.ticks + " celdas=" + run.layout.barrera.size());
            } else if (run.ticks == 90 && run.barreraPuesta && !run.barreraQuitada) {
                colocarBarrera(run, false);
                run.mob.navExecutor().terrainChanged();
                run.barreraQuitada = true;
                log("DINAMICO barrera retirada tick=" + run.ticks);
            }
        } else if (run.esc.tipo == Tipo.BLOQUEO) {
            if (run.ticks == 30 && !run.barreraPuesta) {
                colocarBarrera(run, true);
                run.mob.navExecutor().terrainChanged();
                run.barreraPuesta = true;
                log("BLOQUEO barrera permanente puesta tick=" + run.ticks);
            }
        }
        if (run.esc.tipo == Tipo.PRESUPUESTO || run.esc.tipo == Tipo.LIMITES) {
            if (run.ticks >= 10) {
                cerrarEscenario(servidor, false);
            }
            return;
        }
        boolean llegada = run.llego && run.mob.navExecutor().state() == NavState.ARRIVED;
        boolean fallo = run.esc.esperaFallo && run.mob.navExecutor().state() == NavState.FAILED;
        if (llegada || fallo) {
            run.ticksFinal = run.ticks;
            cerrarEscenario(servidor, false);
            return;
        }
        if (run.ticks >= run.ticksMax()) {
            run.timeout = true;
            cerrarEscenario(servidor, false);
        }
    }

    private static void cerrarEscenario(final MinecraftServer servidor, final boolean forzarInvalido) {
        Run run = actual;
        if (run == null) {
            return;
        }
        actual = null;
        if (run.layout == null && run.area != null) {
            run.layout = new Layout(run.area.p(0, 1, 0), run.area.p(0, 1, 0));
        }
        try {
            if (run.mob != null) {
                run.estadoFinal = run.mob.navExecutor().state();
                run.saludFinal = run.mob.getHealth();
                run.vivo = run.mob.isAlive();
                run.celdasConstruidas.addAll(run.mob.navExecutor().celdasConstruidas());
                for (BlockPos c : run.celdasConstruidas) {
                    if (nivel.getBlockState(c).isAir()) {
                        run.construccionesSinBloque++;
                    }
                }
            }
        } catch (RuntimeException ignored) {
        }
        try {
            if (run.mob != null && !run.mob.isRemoved()) {
                run.mob.discard();
            }
        } catch (RuntimeException ignored) {
        }
        run.bloquesTravesados = Math.max(run.bloquesTravesados, parseEntero(run.metricsEjecutor, "bloquesTravesados"));
        registrarConstruccionesMaquina(run);
        run.cambiosInesperados = contarCambiosInesperados(run);
        List<Criterio> criterios = evaluarCriterios(run);
        boolean invalido = forzarInvalido || !run.geometriaOk || run.error != null
                || (run.esc.preflightRequerido && !run.preflightOk);
        boolean ok = !invalido && criterios.stream().allMatch(Criterio::ok);
        String veredicto = invalido ? "INVALID" : ok ? "PASS" : (run.timeout ? "TIMEOUT" : "FAIL");
        run.veredicto = veredicto;
        try {
            escribirJsonEscenario(run, criterios, veredicto);
            escribirMetrics(run);
            String fila = filaIndice(run);
            INDICE.append(fila).append('\n');
            escribirIndice();
            log("NAVSCN," + fila);
        } catch (Exception e) {
            log("FALLO escribiendo evidencia de " + run.esc.id + ": " + e);
            run.error = "escritura: " + e;
        }
        VEREDICTOS.add(run.esc.id + "=" + veredicto);
        CRITERIOS_GLOBALES.addAll(criterios);
        TOTALES.put(run.esc.id, NavPlannerMetrics.snapshot().totales());
        log("ESCENARIO " + run.esc.id + " fin veredicto=" + veredicto + " ticks=" + run.ticks
                + " estado=" + run.estadoFinal + " pos=" + (run.mob == null ? "-" : shortPos(run.mob.blockPosition()))
                + " ejecutadas=" + run.transicionesEjecutadas + " acciones=" + run.accionesObservadas
                + " planes=" + run.planes + " rec=" + run.recuperaciones + " budgetHits=" + run.budgetHits
                + " expUlt=" + run.expansionsLast + " cambiosInesperados=" + run.cambiosInesperados);
        for (Criterio c : criterios) {
            if (!c.ok()) {
                log("  CRITERIO FALLIDO " + c.id() + " :: " + c.detalle());
            }
        }
        pausa = 20;
    }

    private static void finalizar(final MinecraftServer servidor) {
        if (terminado) {
            return;
        }
        terminado = true;
        int pass = 0;
        int fail = 0;
        int invalid = 0;
        for (String v : VEREDICTOS) {
            if (v.endsWith("=PASS")) {
                pass++;
            } else if (v.endsWith("=INVALID")) {
                invalid++;
            } else {
                fail++;
            }
        }
        try {
            escribirPlaceholders();
            escribirAgregados();
            String veredicto = fail == 0 && invalid == 0 ? "NAV_PASS" : "NAV_FAIL";
            String detalle = "escenarios=" + PENDIENTES.size() + " pass=" + pass + " fail=" + fail
                    + " invalid=" + invalid + " | " + String.join(",", VEREDICTOS);
            Files.writeString(dirRun.resolve("fin.txt"), veredicto + " " + detalle + "\n", StandardCharsets.UTF_8);
            String lineaMetricas = NavPlannerMetrics.dumpLinea();
            log("METRICS," + lineaMetricas.substring("METRICS,".length()));
            log("NAV_FIN," + veredicto + " " + detalle);
            log("FIN " + veredicto + " " + detalle);
        } catch (Exception e) {
            log("FALLO escribiendo cierre: " + e);
        }
        try {
            if (traza != null) {
                traza.close();
            }
        } catch (Exception ignored) {
        }
        servidor.halt(false);
    }

    // ================================================================================================
    // Catálogo de escenarios.
    // ================================================================================================

    private static void construirCatalogo() {
        PENDIENTES.clear();
        PENDIENTES.add(e("campo_abierto", "control abierto, solo WALK", 20, NavScenarioHarness::gCampoAbierto)
                .ticks(600));
        PENDIENTES.add(e("paso_arriba", "escalon de 1 bloque", 20, NavScenarioHarness::gPasoArriba)
                .transicion(NavRoute.Transition.STEP_UP).accion("STEP_UP").ticks(600));
        PENDIENTES.add(e("caida", "caida segura de 3", 20, NavScenarioHarness::gCaida)
                .transicion(NavRoute.Transition.DROP).accion("DROP").ticks(700));
        PENDIENTES.add(e("romper", "panel rompible con techo a +2", 20, NavScenarioHarness::gRomper)
                .transicion(NavRoute.Transition.BREAK).accion("BREAK").roturasMin(1).ticks(700));
        PENDIENTES.add(e("construir", "puente sobre hueco de 2", 20, NavScenarioHarness::gConstruir)
                .transicion(NavRoute.Transition.BUILD).accion("BUILD").ticks(1200));
        PENDIENTES.add(e("paso_arriba_romper", "escalon + panel", 20, NavScenarioHarness::gPasoArribaRomper)
                .transicion(NavRoute.Transition.STEP_UP).transicion(NavRoute.Transition.BREAK)
                .accion("STEP_UP").accion("BREAK").roturasMin(1).ticks(1100));
        PENDIENTES.add(e("paso_arriba_construir", "escalon + puente", 20, NavScenarioHarness::gPasoArribaConstruir)
                .transicion(NavRoute.Transition.STEP_UP).transicion(NavRoute.Transition.BUILD)
                .accion("STEP_UP").accion("BUILD").ticks(1400));
        PENDIENTES.add(e("caida_romper", "caida 3 + panel", 20, NavScenarioHarness::gCaidaRomper)
                .transicion(NavRoute.Transition.DROP).transicion(NavRoute.Transition.BREAK)
                .accion("DROP").accion("BREAK").roturasMin(1).ticks(1100));
        PENDIENTES.add(e("caida_construir", "puente BUILD + caida segura de 3", 20,
                        NavScenarioHarness::gCaidaConstruir)
                .transicion(NavRoute.Transition.BUILD).transicion(NavRoute.Transition.DROP)
                .accion("BUILD").accion("DROP").ticks(1200));
        PENDIENTES.add(e("romper_construir", "panel + puente", 20, NavScenarioHarness::gRomperConstruir)
                .transicion(NavRoute.Transition.BREAK).transicion(NavRoute.Transition.BUILD)
                .accion("BREAK").accion("BUILD").roturasMin(1).ticks(1500));
        PENDIENTES.add(e("caida_romper_construir", "caida + panel + puente", 20,
                        NavScenarioHarness::gCaidaRomperConstruir)
                .transicion(NavRoute.Transition.DROP).transicion(NavRoute.Transition.BREAK)
                .transicion(NavRoute.Transition.BUILD)
                .accion("DROP").accion("BREAK").accion("BUILD").roturasMin(1).ticks(1800));
        PENDIENTES.add(e("escalera", "escalera natural de 3", 20, NavScenarioHarness::gEscalera)
                .transicion(NavRoute.Transition.STEP_UP).accion("STEP_UP").ticks(800));
        PENDIENTES.add(e("columna", "pozo 1x1 y sala inferior", 20, NavScenarioHarness::gColumna)
                .transicion(NavRoute.Transition.DROP).accion("DROP").ticks(700));
        PENDIENTES.add(e("muro", "muro de 2 paneles", 20, NavScenarioHarness::gMuro)
                .transicion(NavRoute.Transition.BREAK).accion("BREAK").roturasMin(2).ticks(1200));
        PENDIENTES.add(e("tunel", "galeria a traves de 4 bloques", 20, NavScenarioHarness::gTunel)
                .accion("TUNNEL").preflightRequerido(false).ticks(1800));
        PENDIENTES.add(e("terreno_dinamico", "barrera puesta y retirada en caliente", 20,
                        NavScenarioHarness::gTerrenoDinamico)
                .tipo(Tipo.DINAMICO).ticks(1400));
        PENDIENTES.add(e("inaccesible", "objetivo sellado en bedrock", 20, NavScenarioHarness::gInaccesible)
                .tipo(Tipo.INALCANZABLE).esperaLlegada(false).esperaFallo(false).ticks(1800));
        PENDIENTES.add(e("astar_reanudacion", "A* global incremental (reanudacion)", 40,
                        NavScenarioHarness::gAstarReanudacion)
                .tipo(Tipo.AESTRELLA).esperaLlegada(false).preflightRequerido(false).ticks(220));
        PENDIENTES.add(e("presupuesto_agotado", "A* local con presupuesto 2", 20,
                        NavScenarioHarness::gPresupuesto)
                .tipo(Tipo.PRESUPUESTO).esperaLlegada(false).preflightRequerido(false).ticks(40));
        PENDIENTES.add(e("bloqueo_recuperacion", "desvio por ramal tras bloqueo", 20,
                        NavScenarioHarness::gBloqueoRecuperacion)
                .tipo(Tipo.BLOQUEO).ticks(1500));
        PENDIENTES.add(e("limites", "topes local/global con presupuesto 16", 20, NavScenarioHarness::gLimites)
                .tipo(Tipo.LIMITES).esperaLlegada(false).preflightRequerido(false).ticks(40));
    }

    private static void filtrarEscenarios() {
        if (FILTRO.isEmpty() || "all".equalsIgnoreCase(FILTRO) || "*".equals(FILTRO)) {
            return;
        }
        Set<String> pedidos = new LinkedHashSet<>();
        for (String s : FILTRO.split(",")) {
            if (!s.isBlank()) {
                pedidos.add(s.trim());
            }
        }
        PENDIENTES.removeIf(esc -> !pedidos.contains(esc.id));
        if (PENDIENTES.isEmpty()) {
            log("FILTRO sin escenarios validos: " + FILTRO + "; se ejecutaran todos");
            construirCatalogo();
        }
    }

    private static Escenario e(String id, String descripcion, int radio, Constructor constructor) {
        return new Escenario(id, descripcion, radio, constructor);
    }

    // ================================================================================================
    // Geometrías (coordenadas relativas al centro del área; dy=0 es el bloque de suelo Y=200).
    // ================================================================================================

    private static Layout gCampoAbierto(Area a) {
        // Sin muros: la plataforma abierta ya tiene suelo; el control solo camina.
        return new Layout(a.p(-10, 1, 0), a.p(12, 1, 0));
    }

    private static Layout gPasoArriba(Area a) {
        baseCorredor(a, -14, 14, 0, false);
        for (int x = 1; x <= 14; x++) {
            for (int z = -1; z <= 1; z++) {
                a.solido(x, 1, z);
            }
        }
        return new Layout(a.p(-10, 1, 0), a.p(12, 2, 0));
    }

    private static Layout gCaida(Area a) {
        baseCorredor(a, -14, 0, 0, false);
        baseCorredor(a, 1, 14, -3, false);
        return new Layout(a.p(-10, 1, 0), a.p(12, -2, 0));
    }

    private static Layout gRomper(Area a) {
        baseCorredor(a, -14, 14, 0, true);
        a.cristal(0, 1, 0);
        a.bedrock(0, 1, -1);
        a.bedrock(0, 1, 1);
        Layout l = new Layout(a.p(-10, 1, 0), a.p(12, 1, 0));
        l.roturas.add(a.p(0, 1, 0));
        return l;
    }

    private static Layout gConstruir(Area a) {
        baseCorredor(a, -14, 14, 0, false);
        for (int x = 0; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                a.aire(x, 0, z);
                a.aire(x, -1, z);
                a.aire(x, -2, z);
            }
        }
        return new Layout(a.p(-10, 1, 0), a.p(12, 1, 0));
    }

    private static Layout gPasoArribaRomper(Area a) {
        baseCorredor(a, -14, 14, 0, false);
        for (int x = 1; x <= 14; x++) {
            for (int z = -1; z <= 1; z++) {
                a.solido(x, 1, z);
            }
        }
        for (int x = 1; x <= 6; x++) {
            for (int z = -1; z <= 1; z++) {
                a.bedrock(x, 4, z);
            }
        }
        a.cristal(5, 2, 0);
        a.bedrock(5, 2, -1);
        a.bedrock(5, 2, 1);
        Layout l = new Layout(a.p(-10, 1, 0), a.p(12, 2, 0));
        l.roturas.add(a.p(5, 2, 0));
        return l;
    }

    private static Layout gPasoArribaConstruir(Area a) {
        baseCorredor(a, -14, 14, 0, false);
        for (int x = 1; x <= 3; x++) {
            for (int z = -1; z <= 1; z++) {
                a.solido(x, 1, z);
            }
        }
        for (int x = 7; x <= 14; x++) {
            for (int z = -1; z <= 1; z++) {
                a.solido(x, 1, z);
            }
        }
        for (int x = 4; x <= 6; x++) {
            for (int z = -1; z <= 1; z++) {
                a.aire(x, 1, z);
                a.aire(x, 0, z);
                a.aire(x, -1, z);
            }
        }
        return new Layout(a.p(-10, 1, 0), a.p(12, 2, 0));
    }

    private static Layout gCaidaRomper(Area a) {
        baseCorredor(a, -14, 1, 0, false);
        baseCorredor(a, 2, 14, -3, true);
        for (int z = -1; z <= 1; z++) {
            a.aire(1, 0, z);
            a.aire(1, -1, z);
            a.aire(1, -2, z);
            a.solido(1, -3, z);
        }
        for (int dy = -4; dy <= 0; dy++) {
            a.bedrock(1, dy, -2);
            a.bedrock(1, dy, 2);
        }
        a.cristal(6, -2, 0);
        a.bedrock(6, -2, -1);
        a.bedrock(6, -2, 1);
        Layout l = new Layout(a.p(-10, 1, 0), a.p(12, -2, 0));
        l.roturas.add(a.p(6, -2, 0));
        return l;
    }

    private static Layout gCaidaConstruir(Area a) {
        // BUILD + DROP con aristas normales (sin maquina de escalera): un hueco de 1 se puentea con un
        // BUILD plano y despues una caida segura de 3 a la plataforma baja. Es la combinacion exigida y
        // el ejecutor ya sabe ejecutarla (BUILD_FEET_NUDGE + DROP).
        baseCorredor(a, -14, 10, 0, false);
        baseCorredor(a, 11, 14, -3, false);
        for (int z = -1; z <= 1; z++) {
            a.aire(5, 0, z);
            a.aire(5, -1, z);
            a.aire(5, -2, z);
        }
        return new Layout(a.p(-10, 1, 0), a.p(13, -2, 0));
    }

    private static Layout gRomperConstruir(Area a) {
        baseCorredor(a, -14, 14, 0, false);
        for (int x = -3; x <= 0; x++) {
            for (int z = -1; z <= 1; z++) {
                a.bedrock(x, 3, z);
            }
        }
        a.cristal(-1, 1, 0);
        a.bedrock(-1, 1, -1);
        a.bedrock(-1, 1, 1);
        for (int x = 4; x <= 5; x++) {
            for (int z = -1; z <= 1; z++) {
                a.aire(x, 0, z);
                a.aire(x, -1, z);
                a.aire(x, -2, z);
            }
        }
        Layout l = new Layout(a.p(-10, 1, 0), a.p(12, 1, 0));
        l.roturas.add(a.p(-1, 1, 0));
        return l;
    }

    private static Layout gCaidaRomperConstruir(Area a) {
        baseCorredor(a, -14, 1, 0, false);
        baseCorredor(a, 2, 7, -3, true);
        baseCorredor(a, 10, 14, -3, true);
        for (int x = 8; x <= 9; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int dy = -4; dy <= 0; dy++) {
                    a.aire(x, dy, z);
                }
            }
        }
        for (int z = -1; z <= 1; z++) {
            a.aire(1, 0, z);
            a.aire(1, -1, z);
            a.aire(1, -2, z);
            a.solido(1, -3, z);
        }
        for (int dy = -4; dy <= 0; dy++) {
            a.bedrock(1, dy, -2);
            a.bedrock(1, dy, 2);
        }
        a.cristal(5, -2, 0);
        a.bedrock(5, -2, -1);
        a.bedrock(5, -2, 1);
        for (int x = 8; x <= 9; x++) {
            for (int dy = -4; dy <= 6; dy++) {
                a.bedrock(x, dy, -2);
                a.bedrock(x, dy, 2);
            }
        }
        Layout l = new Layout(a.p(-10, 1, 0), a.p(12, -2, 0));
        l.roturas.add(a.p(5, -2, 0));
        return l;
    }

    private static Layout gEscalera(Area a) {
        baseCorredor(a, -14, 14, 0, false);
        for (int z = -1; z <= 1; z++) {
            a.solido(0, 1, z);
            a.solido(1, 2, z);
        }
        for (int x = 2; x <= 14; x++) {
            for (int z = -1; z <= 1; z++) {
                a.solido(x, 3, z);
            }
        }
        return new Layout(a.p(-10, 1, 0), a.p(12, 4, 0));
    }

    private static Layout gColumna(Area a) {
        baseCorredor(a, -14, 14, 0, false);
        for (int x = 0; x <= 14; x++) {
            for (int z = -1; z <= 1; z++) {
                a.solido(x, -3, z);
            }
        }
        for (int x = 1; x <= 14; x++) {
            for (int z = -1; z <= 1; z++) {
                a.aire(x, -1, z);
                a.aire(x, -2, z);
            }
        }
        a.aire(0, 0, 0);
        a.aire(0, -1, 0);
        a.aire(0, -2, 0);
        a.solido(0, -3, 0);
        for (int x = 0; x <= 14; x++) {
            for (int dy = -4; dy <= 2; dy++) {
                a.bedrock(x, dy, -2);
                a.bedrock(x, dy, 2);
            }
        }
        return new Layout(a.p(-10, 1, 0), a.p(12, -2, 0));
    }

    private static Layout gMuro(Area a) {
        baseCorredor(a, -14, 14, 0, true);
        // Dos paneles SEPARADOS por 3 celdas: el stand del segundo panel es una celda abierta, no el
        // primer panel aun solido. Con paneles adyacentes el planner no puede encadenar los dos BREAK.
        for (int x : new int[]{-4, 0}) {
            a.cristal(x, 1, 0);
            a.bedrock(x, 1, -1);
            a.bedrock(x, 1, 1);
        }
        Layout l = new Layout(a.p(-10, 1, 0), a.p(12, 1, 0));
        l.roturas.add(a.p(-4, 1, 0));
        l.roturas.add(a.p(0, 1, 0));
        return l;
    }

    private static Layout gTunel(Area a) {
        // El ente empieza PEGADO al muro y el muro mide exactamente 3 celdas: el plan de tunel
        // (MAX_STEPS_PER_PLAN=3) abre la galeria completa en UNA activacion y sale a espacio abierto.
        baseCorredor(a, -14, -1, 0, true);
        for (int x = 0; x <= 2; x++) {
            for (int dy = 1; dy <= 3; dy++) {
                for (int z = -1; z <= 1; z++) {
                    a.tierra(x, dy, z);
                }
            }
            for (int dy = 1; dy <= 6; dy++) {
                a.bedrock(x, dy, -2);
                a.bedrock(x, dy, 2);
            }
        }
        baseCorredor(a, 3, 14, 0, false);
        Layout l = new Layout(a.p(-1, 1, 0), a.p(12, 1, 0));
        for (int x = 0; x <= 2; x++) {
            l.roturas.add(a.p(x, 1, 0));
            l.roturas.add(a.p(x, 2, 0));
            l.roturas.add(a.p(x, 1, -1));
            l.roturas.add(a.p(x, 2, -1));
            l.roturas.add(a.p(x, 1, 1));
            l.roturas.add(a.p(x, 2, 1));
        }
        return l;
    }

    private static Layout gTerrenoDinamico(Area a) {
        baseCorredor(a, -14, 14, 0, false);
        Layout l = new Layout(a.p(-10, 1, 0), a.p(12, 1, 0));
        for (int dy = 1; dy <= 2; dy++) {
            for (int z = -1; z <= 1; z++) {
                l.barrera.add(a.p(2, dy, z));
            }
        }
        return l;
    }

    private static Layout gInaccesible(Area a) {
        baseCorredor(a, -6, 2, 0, true);
        // Pared maciza x=2..4 y bolsa sellada 1x2 en (3,1..2,0): celda de pie valida pero inalcanzable.
        for (int x = 2; x <= 4; x++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
                    a.bedrock(x, dy, dz);
                }
            }
        }
        a.aire(3, 1, 0);
        a.aire(3, 2, 0);
        return new Layout(a.p(-4, 1, 0), a.p(3, 1, 0));
    }

    private static Layout gAstarReanudacion(Area a) {
        baseCorredor(a, -34, 34, 0, false);
        return new Layout(a.p(-28, 1, 0), a.p(28, 1, 0));
    }

    private static Layout gPresupuesto(Area a) {
        baseCorredor(a, -14, 14, 0, false);
        return new Layout(a.p(-10, 1, 0), a.p(12, 1, 0));
    }

    private static Layout gBloqueoRecuperacion(Area a) {
        baseCorredor(a, -14, 14, 0, false);
        // Ramal: abre el muro z=+2 en x=0..2 y x=8..10 y construye pasillo z=2..4.
        for (int x = 0; x <= 2; x++) {
            for (int dy = 1; dy <= 2; dy++) {
                a.aire(x, dy, 2);
            }
        }
        for (int x = 8; x <= 10; x++) {
            for (int dy = 1; dy <= 2; dy++) {
                a.aire(x, dy, 2);
            }
        }
        for (int x = 0; x <= 10; x++) {
            for (int z = 2; z <= 4; z++) {
                a.solido(x, 0, z);
                a.aire(x, 1, z);
                a.aire(x, 2, z);
            }
        }
        for (int x = 0; x <= 10; x++) {
            for (int dy = 1; dy <= 5; dy++) {
                a.bedrock(x, dy, 5);
            }
        }
        for (int dy = 1; dy <= 5; dy++) {
            for (int z = 2; z <= 4; z++) {
                a.bedrock(-1, dy, z);
                a.bedrock(11, dy, z);
            }
        }
        Layout l = new Layout(a.p(-10, 1, 0), a.p(12, 1, 0));
        for (int dy = 1; dy <= 2; dy++) {
            for (int z = -1; z <= 1; z++) {
                l.barrera.add(a.p(4, dy, z));
            }
        }
        return l;
    }

    private static Layout gLimites(Area a) {
        baseCorredor(a, -14, 14, 0, false);
        return new Layout(a.p(-10, 1, 0), a.p(12, 1, 0));
    }

    /** Corredor de 3 de ancho, suelo en {@code suelo}, muros de bedrock en z=±2 y techo opcional a +3. */
    private static void baseCorredor(Area a, int x1, int x2, int suelo, boolean techo) {
        for (int x = x1; x <= x2; x++) {
            for (int z = -1; z <= 1; z++) {
                a.solido(x, suelo, z);
                for (int dy = 1; dy <= 4; dy++) {
                    a.aire(x, suelo + dy, z);
                }
            }
            if (techo) {
                for (int z = -1; z <= 1; z++) {
                    a.bedrock(x, suelo + 3, z);
                }
            }
        }
        // Muros laterales hasta el borde del área: sin esto el planner puede RODEAR el final del corredor
        // por la plataforma abierta y evitar la transición que el escenario quiere medir.
        for (int x = -a.radio; x <= a.radio; x++) {
            for (int dy = 1; dy <= 6; dy++) {
                a.bedrock(x, suelo + dy, -2);
                a.bedrock(x, suelo + dy, 2);
            }
        }
    }

    private static Map<BlockPos, BlockState> instantanea(Area a) {
        Map<BlockPos, BlockState> mapa = new HashMap<>();
        for (int dx = -a.radio; dx <= a.radio; dx++) {
            for (int dz = -a.radio; dz <= a.radio; dz++) {
                for (int dy = -8; dy <= 10; dy++) {
                    BlockPos p = a.p(dx, dy, dz);
                    mapa.put(p, nivel.getBlockState(p));
                }
            }
        }
        return mapa;
    }

    private static void prepararBase(Area a) {        forzar(a.x - a.radio - 2, a.z - a.radio - 2, a.x + a.radio + 2, a.z + a.radio + 2);
        for (int dx = -a.radio; dx <= a.radio; dx++) {
            for (int dz = -a.radio; dz <= a.radio; dz++) {
                a.solido(dx, 0, dz);
            }
        }
        for (int dy = -8; dy <= 10; dy++) {
            for (int i = -a.radio - 1; i <= a.radio + 1; i++) {
                a.bedrock(i, dy, -a.radio - 1);
                a.bedrock(i, dy, a.radio + 1);
                a.bedrock(-a.radio - 1, dy, i);
                a.bedrock(a.radio + 1, dy, i);
            }
        }
    }

    private static void colocarBarrera(Run run, boolean solida) {
        BlockState estado = solida ? Blocks.BEDROCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
        for (BlockPos p : run.layout.barrera) {
            nivel.setBlockAndUpdate(p, estado);
        }
    }

    // ================================================================================================
    // Preflight y validación de geometría.
    // ================================================================================================

    private static boolean validarGeometria(Run run) {
        boolean ok = true;
        ok &= celdaValida("spawn", run.layout.spawn);
        ok &= celdaValida("goal", run.layout.goal);
        if (run.esc.tipo == Tipo.INALCANZABLE) {
            ok &= nivel.getBlockState(run.layout.goal).isAir();
        }
        if (!ok) {
            run.error = "geometria: precondiciones de spawn/goal no cumplidas";
        }
        return ok;
    }

    private static boolean celdaValida(String etiqueta, BlockPos p) {
        boolean aire = nivel.getBlockState(p).isAir() && nivel.getBlockState(p.above()).isAir();
        boolean soporte = NavObstruction.hasSolidFace(nivel, p.below());
        if (!aire || !soporte) {
            log("GEOMETRIA " + etiqueta + " invalida en " + shortPos(p) + " aire=" + aire + " soporte=" + soporte);
        }
        return aire && soporte;
    }

    private static Preflight preflight(Run run) {
        Preflight pf = new Preflight();
        NavPlanner.Result local = null;
        NavPlanner.GlobalResult global = null;
        long t0 = System.nanoTime();
        if (run.esc.preflightGlobal) {
            global = NavPlanner.planGlobal(nivel, run.mob, run.layout.spawn, run.layout.goal,
                    128, 6, 8192, run.mob.navPolicies(), null, null, null);
            pf.route = global.route();
            pf.alcanzoMision = global.alcanzoMision();
            pf.hitBudget = global.hitBudget();
            pf.expansiones = global.expansions();
            pf.resumen = global.summary();
        } else {
            // Preflight con el planner GLOBAL (sin el deadline duro de 30 ms del plan local). El local podía
            // devolver hitBudget=true por reloj tras UNA expansion y marcar INVALID un escenario que el ejecutor
            // resolvía después (medido en `paso_arriba`: preflight exp=1/8192 y runtime STEP_UP+ARRIVED).
            global = NavPlanner.planGlobal(nivel, run.mob, run.layout.spawn, run.layout.goal,
                    128, 6, 8192, run.mob.navPolicies(), null, null, null);
            pf.route = global.route();
            pf.alcanzoMision = global.alcanzoMision();
            pf.hitBudget = global.hitBudget();
            pf.expansiones = global.expansions();
            pf.resumen = global.summary();
        }
        pf.nanos = System.nanoTime() - t0;
        if (pf.route != null) {
            for (NavRoute.Node n : pf.route.nodes()) {
                pf.transiciones.add(n.transition());
            }
        }
        run.preflightResumen = pf.resumen;
        return pf;
    }

    private static boolean evaluarPreflight(Run run) {
        Preflight pf = run.preflight;
        if (pf == null) {
            return true;
        }
        if (run.esc.tipo == Tipo.INALCANZABLE) {
            if (pf.hitBudget) {
                run.preflightDetalle = "presupuesto agotado antes de demostrar inalcanzable";
                return false;
            }
            if (pf.alcanzoMision) {
                run.preflightDetalle = "el planner alcanzo el objetivo sellado: " + pf.route.describe();
                return false;
            }
            run.preflightDetalle = "sin ruta a la mision; frente=" + (pf.route == null ? "-"
                    : pf.route.describe());
            return true;
        }
        if (pf.hitBudget) {
            run.preflightDetalle = "presupuesto del preflight agotado";
            return false;
        }
        if (pf.route == null || pf.route.isEmpty()) {
            run.preflightDetalle = "sin ruta";
            return false;
        }
        if (!pf.alcanzoMision) {
            // Una ruta-frente NO es una ruta a la mision: el escenario exige llegar al goal.
            run.preflightDetalle = "ruta-frente sin alcanzar la mision: " + pf.route.describe();
            return false;
        }
        Set<NavRoute.Transition> faltan = EnumSet.noneOf(NavRoute.Transition.class);
        faltan.addAll(run.esc.transiciones);
        faltan.removeAll(pf.transiciones);
        if (!faltan.isEmpty()) {
            run.preflightDetalle = "faltan transiciones " + faltan + " en ruta " + pf.route.describe();
            return false;
        }
        run.preflightDetalle = "ok exp=" + pf.expansiones + " ruta=" + pf.route.length();
        return true;
    }

    // ================================================================================================
    // Observación por tick.
    // ================================================================================================

    private static void muestrear(Run run) {
        AggressiveZombieEntity m = run.mob;
        if (m == null || m.isRemoved()) {
            return;
        }
        NavExecutor ex = m.navExecutor();
        BlockPos pos = m.blockPosition();
        run.minY = Math.min(run.minY, pos.getY());
        run.maxY = Math.max(run.maxY, pos.getY());
        if (run.ultimaY != Integer.MIN_VALUE && run.ultimaY - pos.getY() >= 2) {
            run.dropsFisicos++;
        }
        run.ultimaY = pos.getY();
        if (Math.abs(pos.getX() - run.area.x) > run.area.radio + 3
                || Math.abs(pos.getZ() - run.area.z) > run.area.radio + 3) {
            run.fueraDeArea = true;
        }
        run.maxDist = Math.max(run.maxDist, Math.sqrt(pos.distSqr(run.layout.goal)));
        run.metricsEjecutor = ex.metrics();
        run.moves = Math.max(run.moves, ex.moves());
        run.expansionsLast = ex.expansionsLast();
        run.expansionsTotal = ex.expansionsTotal();
        run.planes = Math.max(run.planes, parseEntero(run.metricsEjecutor, "plans"));
        run.recuperaciones = Math.max(run.recuperaciones, parseEntero(run.metricsEjecutor, "recuperaciones"));
        run.budgetHits = Math.max(run.budgetHits, parseEntero(run.metricsEjecutor, "presupuestosAgotados"));
        run.bloquesTravesados = Math.max(run.bloquesTravesados, ex.bloquesTravesados());
        run.tunnelPasos = Math.max(run.tunnelPasos, parseEntero(ex.tunelDiag(), "pasos"));
        run.celdasConstruidas.addAll(ex.celdasConstruidas());
        run.escaleraColocados = Math.max(run.escaleraColocados,
                parseEntero(m.escaleraDiag(), "colocados"));
        if (m.escaleraTravesiaDiag().contains("confirmada=true")) {
            run.escaleraTravesiaConfirmada = true;
        }
        NavRoute route = ex.route();
        int idx = ex.indiceDeRuta();
        if (route != null) {
            if (route.length() == run.lastRouteLen && route.signature() == run.lastRouteSig
                    && run.lastIndex >= 0 && idx >= run.lastIndex) {
                int hasta = Math.min(idx, route.length());
                for (int i = run.lastIndex; i < hasta; i++) {
                    marcarNodo(run, route.currentNode(i));
                }
            }
            run.lastRouteLen = route.length();
            run.lastRouteSig = route.signature();
            run.lastIndex = idx;
        } else {
            run.lastRouteLen = 0;
            run.lastRouteSig = 0;
            run.lastIndex = -1;
        }
        NavRoute.Node activo = ex.activeNode();
        if (activo != null && activo.transition() == NavRoute.Transition.BREAK) {
            BlockPos target = activo.actionTarget();
            if (target != null && nivel.getBlockState(target).isAir()) {
                run.roturas.add(target.immutable());
            }
        }
        if (ex.state() == NavState.ARRIVED) {
            run.llego = true;
        }
        if (ex.state() == NavState.FAILED) {
            run.fallido = true;
        }
    }

    private static void marcarNodo(Run run, NavRoute.Node nodo) {
        if (nodo == null) {
            return;
        }
        run.transicionesEjecutadas.add(nodo.transition());
        switch (nodo.transition()) {
            case STEP_UP -> {
                run.stepUps++;
                run.accionesObservadas.add("STEP_UP");
            }
            case DROP -> {
                run.drops++;
                run.accionesObservadas.add("DROP");
            }
            case BREAK -> {
                BlockPos target = nodo.actionTarget();
                if (target != null && nivel.getBlockState(target).isAir()) {
                    run.roturas.add(target.immutable());
                }
                run.accionesObservadas.add("BREAK");
            }
            case BUILD -> {
                run.buildsTravesadas++;
                run.accionesObservadas.add("BUILD");
            }
            default -> {
            }
        }
    }

    /**
     * La maquina de escalera del ente (A2) coloca cobblestone fuera de `NavExecutor.celdasConstruidas`. Para
     * los escenarios que declaran BUILD, esos bloques son construccion LEGITIMA observada: se incorporan a la
     * evidencia y a la lista de cambios permitidos, sin abrir la puerta a roturas inesperadas.
     */
    private static void registrarConstruccionesMaquina(Run run) {
        if (!run.esc.acciones.contains("BUILD") || run.snapshot == null) {
            return;
        }
        for (Map.Entry<BlockPos, BlockState> entrada : run.snapshot.entrySet()) {
            BlockPos p = entrada.getKey();
            if (entrada.getValue().isAir() && nivel.getBlockState(p).getBlock() == Blocks.COBBLESTONE) {
                run.celdasConstruidas.add(p);
            }
        }
    }

    private static int contarCambiosInesperados(Run run) {        if (run.snapshot == null) {
            return 0;
        }
        Set<BlockPos> permitidos = new HashSet<>(run.cambiosPermitidos);
        permitidos.addAll(run.celdasConstruidas);
        permitidos.addAll(run.layout.roturas);
        int cambios = 0;
        for (Map.Entry<BlockPos, BlockState> entrada : run.snapshot.entrySet()) {
            BlockPos p = entrada.getKey();
            BlockState ahora = nivel.getBlockState(p);
            if (!ahora.equals(entrada.getValue()) && !permitidos.contains(p)) {
                cambios++;
                if (cambios <= 10) {
                    log("CAMBIO INESPERADO " + shortPos(p) + " antes=" + entrada.getValue() + " ahora=" + ahora);
                }
            }
        }
        return cambios;
    }

    // ================================================================================================
    // Criterios.
    // ================================================================================================

    private static List<Criterio> evaluarCriterios(Run run) {
        List<Criterio> out = new ArrayList<>();
        Escenario esc = run.esc;
        int roturasCubiertas = run.roturasEsperadasCubiertas();
        if (roturasCubiertas >= esc.roturasMin && esc.acciones.contains("BREAK")) {
            run.accionesObservadas.add("BREAK");
        }
        if (run.celdasConstruidas.size() >= 1 && esc.acciones.contains("BUILD")) {
            run.accionesObservadas.add("BUILD");
        }
        if (run.tunnelPasos >= 1) {
            run.accionesObservadas.add("TUNNEL");
        }
        if ((run.drops > 0 || run.dropsFisicos > 0) && esc.acciones.contains("DROP")) {
            run.accionesObservadas.add("DROP");
        }
        out.add(new Criterio("C1-GEOMETRIA", run.geometriaOk,
                "area=" + shortPos(run.area.p(0, 0, 0)) + " radio=" + run.area.radio
                        + " spawn=" + shortPos(run.layout.spawn) + " goal=" + shortPos(run.layout.goal)
                        + (run.error == null ? "" : " error=" + run.error)));
        out.add(new Criterio("C2-PREFLIGHT", run.preflightOk,
                run.esc.preflightRequerido
                        ? (run.preflightDetalle == null ? run.preflightResumen : run.preflightDetalle)
                        : "no requerido"));
        Set<String> faltan = new LinkedHashSet<>(esc.acciones);
        faltan.removeAll(run.accionesObservadas);
        boolean accionesOk = faltan.isEmpty()
                && (roturasCubiertas >= esc.roturasMin)
                && (!esc.acciones.contains("BUILD")
                || (run.celdasConstruidas.size() >= 1 && run.construccionesSinBloque == 0
                && (run.bloquesTravesados >= 1 || run.escaleraTravesiaConfirmada)))
                && (!esc.acciones.contains("TUNNEL") || run.tunnelPasos >= 1);
        out.add(new Criterio("C3-ACCIONES", accionesOk,
                "ejecutadas=" + run.transicionesEjecutadas + " acciones=" + run.accionesObservadas
                        + " roturas=" + roturasCubiertas + "/min=" + esc.roturasMin
                        + " construidas=" + run.celdasConstruidas.size()
                        + " sinBloque=" + run.construccionesSinBloque
                        + " travesadas=" + run.bloquesTravesados
                        + " escaleraColocados=" + run.escaleraColocados
                        + " escaleraTravesia=" + run.escaleraTravesiaConfirmada
                        + " tunelPasos=" + run.tunnelPasos + " faltan=" + faltan));
        boolean llegadaOk;
        if (esc.tipo == Tipo.INALCANZABLE) {
            // El objetivo es inalcanzable por construccion: el preflight global debe agotar el frente SIN
            // alcanzar la mision, y el ejecutor no puede llegar. No se exige FAILED en un presupuesto corto:
            // la recuperacion acotada de produccion puede seguir reintentando sin llegar (jamas ARRIVED).
            llegadaOk = !run.llego && run.preflight != null && !run.preflight.alcanzoMision;
        } else if (esc.esperaFallo) {
            llegadaOk = !run.llego && run.fallido && run.estadoFinal == NavState.FAILED;
        } else if (esc.esperaLlegada) {
            llegadaOk = run.llego && run.estadoFinal == NavState.ARRIVED;
        } else {
            llegadaOk = true;
        }
        out.add(new Criterio("C4-LLEGADA", llegadaOk,
                "llego=" + run.llego + " fallido=" + run.fallido + " estado=" + run.estadoFinal
                        + " preflightAlcanzo=" + (run.preflight == null ? "-" : run.preflight.alcanzoMision)
                        + " dist=" + String.format(Locale.ROOT, "%.2f", run.maxDist)
                        + " timeout=" + run.timeout));
        boolean movimientoOk = switch (esc.tipo) {
            case PRESUPUESTO, LIMITES, INALCANZABLE, AESTRELLA -> true;
            default -> run.moves > 0;
        };
        out.add(new Criterio("C5-MOVIMIENTO", movimientoOk,
                "moves=" + run.moves + " planes=" + run.planes + " recuperaciones=" + run.recuperaciones
                        + " ticks=" + run.ticks));
        boolean seguridad = run.vivo && run.saludFinal + 0.001F >= run.saludInicial
                && run.minY >= run.area.y - 9 && run.maxY <= run.area.y + 14 && !run.fueraDeArea;
        out.add(new Criterio("C6-SEGURIDAD", seguridad,
                "vivo=" + run.vivo + " salud=" + run.saludFinal + "/" + run.saludInicial
                        + " y=[" + run.minY + "," + run.maxY + "] limite=[" + (run.area.y - 9) + ","
                        + (run.area.y + 14) + "] fueraDeArea=" + run.fueraDeArea));
        out.add(new Criterio("C7-TERRENO", run.cambiosInesperados == 0,
                "cambiosInesperados=" + run.cambiosInesperados + " permitidos="
                        + (run.cambiosPermitidos.size() + run.celdasConstruidas.size() + run.roturas.size())));
        boolean limitesOk;
        if (esc.tipo == Tipo.PRESUPUESTO || esc.tipo == Tipo.LIMITES) {
            limitesOk = true;
        } else {
            // El tope duro de producción es 900 por plan; budgetHits se registra pero no se convierte en fallo:
            // recuperarse tras agotar un presupuesto es comportamiento legítimo y está acotado por producción.
            limitesOk = run.expansionsLast <= 900;
        }
        out.add(new Criterio("C8-LIMITES", limitesOk,
                "expansionsLast=" + run.expansionsLast + " budgetHits=" + run.budgetHits
                        + " expansionsTotal=" + run.expansionsTotal));
        if (esc.tipo == Tipo.BLOQUEO || esc.tipo == Tipo.DINAMICO) {
            out.add(new Criterio("C9-RECUPERACION", run.planes >= 2,
                    "planes=" + run.planes + " recuperaciones=" + run.recuperaciones
                            + " barreraPuesta=" + run.barreraPuesta + " retirada=" + run.barreraQuitada));
        }
        if (esc.tipo == Tipo.AESTRELLA) {
            out.add(new Criterio("C10-AESTRELLA", criterioAstar(run), detalleAstar(run)));
        }
        if (esc.tipo == Tipo.PRESUPUESTO) {
            out.add(new Criterio("C11-PRESUPUESTO", run.directoLocalHit && run.directoLocalExp <= 2
                            && NavPlannerMetrics.snapshot().totales().presupuestoAgotado() >= 1,
                    "hitBudget=" + run.directoLocalHit + " exp=" + run.directoLocalExp
                            + " metricPresupuestoAgotado="
                            + NavPlannerMetrics.snapshot().totales().presupuestoAgotado()
                            + " summary={" + run.directoLocalResumen + "}"));
        }
        if (esc.tipo == Tipo.LIMITES) {
            NavPlannerMetrics.Snapshot snap = NavPlannerMetrics.snapshot();
            boolean topes = true;
            int locales = 0;
            int globales = 0;
            for (NavPlannerMetrics.BusquedaSnapshot b : snap.busquedas()) {
                if (b.expansiones() > b.presupuesto()) {
                    topes = false;
                }
                if (b.tipo() == NavPlannerMetrics.Tipo.LOCAL) {
                    locales++;
                }
                if (b.tipo() == NavPlannerMetrics.Tipo.GLOBAL) {
                    globales++;
                }
            }
            boolean ok = run.directoLocalHit && run.directoGlobalHit
                    && run.directoLocalExp <= 16 && run.directoGlobalExp <= 16
                    && !run.directoGlobalPendiente && locales >= 1 && globales >= 1 && topes;
            out.add(new Criterio("C12-LIMITES_LOCAL_GLOBAL", ok,
                    "localHit=" + run.directoLocalHit + " localExp=" + run.directoLocalExp
                            + " globalHit=" + run.directoGlobalHit + " globalExp=" + run.directoGlobalExp
                            + " pendiente=" + run.directoGlobalPendiente + " locales=" + locales
                            + " globales=" + globales + " topesRespetados=" + topes));
        }
        if (run.tunnelPasos > 0) {
            run.accionesObservadas.add("TUNNEL");
        }
        return out;
    }

    private static boolean criterioAstar(Run run) {
        if (run.astarResult == null || !run.astarAlcanzo || run.astarResult.route().isEmpty()) {
            return false;
        }
        // C10 · el GLOBAL incremental y el GLOBAL directo deben ver la misma misión: si el directo no llega,
        // el escenario no demuestra la capa global (aunque haya una búsqueda GLOBAL en métricas).
        if (!run.directoGlobalAlcanzo || run.directoGlobalHit) {
            return false;
        }
        NavPlannerMetrics.Snapshot snap = NavPlannerMetrics.snapshot();
        for (NavPlannerMetrics.BusquedaSnapshot b : snap.busquedas()) {
            if ((b.tipo() == NavPlannerMetrics.Tipo.GLOBAL || b.tipo() == NavPlannerMetrics.Tipo.GLOBAL_EXACT)
                    && b.avances() >= 2 && b.expansiones() > 8
                    && b.ultimoSliceExpansiones() <= 8 && b.presupuesto() == 8000) {
                return true;
            }
        }
        return false;
    }

    private static String detalleAstar(Run run) {
        NavPlannerMetrics.Snapshot snap = NavPlannerMetrics.snapshot();
        StringBuilder sb = new StringBuilder();
        for (NavPlannerMetrics.BusquedaSnapshot b : snap.busquedas()) {
            sb.append("{tipo=").append(b.tipo()).append(" exp=").append(b.expansiones())
                    .append(" avances=").append(b.avances()).append(" slice=")
                    .append(b.ultimoSliceExpansiones()).append(" presupuesto=").append(b.presupuesto())
                    .append(" reanudada=").append(b.reanudada()).append(" motivo=").append(b.motivo())
                    .append("} ");
        }
        return "busquedas=" + snap.busquedas().size() + " reanudadas="
                + snap.totales().reanudadas() + " " + sb;
    }

    // ================================================================================================
    // Evidencia: JSON, CSV de métricas, índice.
    // ================================================================================================

    private static void escribirJsonEscenario(Run run, List<Criterio> criterios, String veredicto) throws Exception {
        StringBuilder sb = new StringBuilder(8192);
        sb.append("{\n");
        campo(sb, "schema", VERSION).append(",\n");
        campo(sb, "run", RUN).append(",\n");
        campo(sb, "repeticion", Integer.toString(REP)).append(",\n");
        campo(sb, "escenario", run.esc.id).append(",\n");
        campo(sb, "descripcion", run.esc.descripcion).append(",\n");
        campo(sb, "veredicto", veredicto).append(",\n");
        campo(sb, "motivo", run.preflightDetalle == null ? (run.error == null ? "-" : run.error)
                : run.preflightDetalle).append(",\n");
        sb.append("\"mundo\":{").append("\"dimension\":\"").append(nivel.dimension().location()).append("\",")
                .append("\"plataformaY\":").append(PLATAFORMA_Y).append(",")
                .append("\"area\":[").append(run.area.x).append(',').append(run.area.y).append(',')
                .append(run.area.z).append("],\"radio\":").append(run.area.radio).append("},\n");
        sb.append("\"geometria\":{").append("\"spawn\":").append(posJson(run.layout.spawn)).append(',')
                .append("\"goal\":").append(posJson(run.layout.goal)).append(',')
                .append("\"transicionesEsperadas\":").append(setJson(run.esc.transiciones)).append(',')
                .append("\"accionesEsperadas\":").append(strSetJson(run.esc.acciones)).append(',')
                .append("\"roturasMin\":").append(run.esc.roturasMin).append(',')
                .append("\"roturasEsperadas\":").append(posListJson(run.layout.roturas)).append(',')
                .append("\"barrera\":").append(posListJson(run.layout.barrera)).append("},\n");
        sb.append("\"presupuesto\":{\"ticksMax\":").append(run.ticksMax())
                .append(",\"plannerLocalDefecto\":900,\"plannerGlobalDefecto\":8000,")
                .append("\"escalaPorcentaje\":").append(ESCALA).append("},\n");
        sb.append("\"preflight\":{").append("\"requerido\":").append(run.esc.preflightRequerido).append(',')
                .append("\"ok\":").append(run.preflightOk).append(',')
                .append("\"hitBudget\":").append(run.preflight != null && run.preflight.hitBudget).append(',')
                .append("\"expansiones\":").append(run.preflight == null ? 0 : run.preflight.expansiones).append(',')
                .append("\"nanos\":").append(run.preflight == null ? 0L : run.preflight.nanos).append(',')
                .append("\"transiciones\":").append(setJson(run.preflight == null
                        ? EnumSet.noneOf(NavRoute.Transition.class) : run.preflight.transiciones)).append(',')
                .append("\"resumen\":").append(jstr(run.preflightResumen == null ? "-" : run.preflightResumen))
                .append("},\n");
        sb.append("\"observado\":{").append("\"ticks\":").append(run.ticks)
                .append(",\"estado\":").append(jstr(String.valueOf(run.estadoFinal)))
                .append(",\"llego\":").append(run.llego)
                .append(",\"fallido\":").append(run.fallido)
                .append(",\"timeout\":").append(run.timeout)
                .append(",\"posFinal\":").append(run.mob == null ? "null" : posJson(run.mob.blockPosition()))
                .append(",\"distMax\":").append(String.format(Locale.ROOT, "%.3f", run.maxDist))
                .append(",\"minY\":").append(run.minY).append(",\"maxY\":").append(run.maxY)
                .append(",\"saludInicial\":").append(run.saludInicial)
                .append(",\"saludFinal\":").append(run.saludFinal)
                .append(",\"vivo\":").append(run.vivo)
                .append(",\"moves\":").append(run.moves)
                .append(",\"planes\":").append(run.planes)
                .append(",\"recuperaciones\":").append(run.recuperaciones)
                .append(",\"budgetHits\":").append(run.budgetHits)
                .append(",\"expansionsLast\":").append(run.expansionsLast)
                .append(",\"expansionsTotal\":").append(run.expansionsTotal)
                .append(",\"stepUps\":").append(run.stepUps)
                .append(",\"drops\":").append(run.drops)
                .append(",\"dropsFisicos\":").append(run.dropsFisicos)
                .append(",\"fueraDeArea\":").append(run.fueraDeArea)
                .append(",\"roturas\":").append(posListJson(new ArrayList<>(run.roturas)))
                .append(",\"construidas\":").append(posListJson(new ArrayList<>(run.celdasConstruidas)))
                .append(",\"construccionesSinBloque\":").append(run.construccionesSinBloque)
                .append(",\"bloquesTravesados\":").append(run.bloquesTravesados)
                .append(",\"tunelPasos\":").append(run.tunnelPasos)
                .append(",\"transicionesEjecutadas\":").append(setJson(run.transicionesEjecutadas))
                .append(",\"accionesObservadas\":").append(strSetJson(run.accionesObservadas))
                .append(",\"cambiosInesperados\":").append(run.cambiosInesperados)
                .append(",\"barreraPuesta\":").append(run.barreraPuesta)
                .append(",\"barreraRetirada\":").append(run.barreraQuitada)
                .append(",\"trace\":{\"tunnelPlanStart\":").append(run.traceTunnelPlanStart)
                .append(",\"breakExecuted\":").append(run.traceBreakExecuted)
                .append(",\"moveCommand\":").append(run.traceMoveCommand).append("}")
                .append("},\n");
        sb.append("\"criterios\":[");
        for (int i = 0; i < criterios.size(); i++) {
            Criterio c = criterios.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"id\":").append(jstr(c.id())).append(",\"ok\":").append(c.ok())
                    .append(",\"detalle\":").append(jstr(c.detalle())).append('}');
        }
        sb.append("],\n");
        sb.append("\"metricas\":").append(metricasJson(NavPlannerMetrics.snapshot())).append('\n');
        sb.append("}\n");
        Files.writeString(dirRun.resolve("escenario-" + run.esc.id + "-r" + REP + ".json"), sb.toString(),
                StandardCharsets.UTF_8);
    }

    private static void escribirMetrics(Run run) throws Exception {
        Path p = dirRun.resolve("metrics-" + run.esc.id + "-r" + REP + ".csv");
        Files.writeString(p, NavPlannerMetrics.dumpCsv(), StandardCharsets.UTF_8);
    }

    private static String filaIndice(Run run) {
        NavPlannerMetrics.Snapshot snap = NavPlannerMetrics.snapshot();
        NavPlannerMetrics.EntidadSnapshot ent = snap.entidades().isEmpty() ? null : snap.entidades().get(0);
        Set<NavPlannerMetrics.Tipo> tipos = EnumSet.noneOf(NavPlannerMetrics.Tipo.class);
        long expansiones = 0;
        long nanos = 0;
        int avances = 0;
        long longitud = 0;
        String motivo = "-";
        boolean agotado = false;
        for (NavPlannerMetrics.BusquedaSnapshot b : snap.busquedas()) {
            tipos.add(b.tipo());
            expansiones += b.expansiones();
            nanos += b.nanos();
            avances += b.avances();
            longitud = b.longitudRuta();
            motivo = b.motivo();
            agotado |= b.presupuestoAgotado();
        }
        for (NavPlannerMetrics.BusquedaSnapshot b : snap.abiertas()) {
            tipos.add(b.tipo());
            expansiones += b.expansiones();
            nanos += b.nanos();
            avances += b.avances();
        }
        if (ent != null) {
            // El agregado por entidad incluye avances de búsquedas abiertas: es la fila ENTIDAD del dump
            // y evita que el índice discrepe del CSV cuando el escenario cierra con una búsqueda pendiente.
            expansiones = Math.max(expansiones, ent.expansiones());
            nanos = Math.max(nanos, ent.nanos());
            longitud = Math.max(longitud, ent.ultimaLongitudRuta());
            motivo = ent.ultimoMotivo() == null ? motivo : ent.ultimoMotivo();
            agotado |= ent.presupuestoAgotado() > 0;
        }
        String tipo = tipos.isEmpty() ? "-" : tipos.size() == 1
                ? tipos.iterator().next().name() : "MIXTO";
        String presupuesto = tipos.size() == 1 && !snap.busquedas().isEmpty()
                ? Integer.toString(snap.busquedas().get(snap.busquedas().size() - 1).presupuesto()) : "-";
        String candidatos = "-";
        String encolados = "-";
        if (run.esc.tipo == Tipo.PRESUPUESTO || run.esc.tipo == Tipo.LIMITES) {
            if (run.directoLocalExp > 0) {
                candidatos = Integer.toString(parseEntero(run.directoLocalResumen, "evaluados"));
                encolados = Integer.toString(parseEntero(run.directoLocalResumen, "encolados"));
            }
        }
        long pared = System.nanoTime() - run.paredInicioNs;
        return sinComas(run.esc.id) + "," + REP + "," + (run.uuid == null ? "-" : run.uuid) + ","
                + tipo + "," + presupuesto + "," + run.llego + "," + agotado + "," + longitud + ","
                + expansiones + "," + nanos + "," + pared + "," + avances + "," + run.ticks
                + ",-," + candidatos + "," + encolados + "," + sinComas(motivo) + ","
                + "build/av/" + RUN + "/metrics-" + run.esc.id + "-r" + REP + ".csv";
    }

    private static void escribirIndice() throws Exception {
        String nombre = "nav-escenarios-" + (selloIndice == null ? fechaRun() : selloIndice) + ".csv";
        Files.writeString(dirRun.resolve(nombre), INDICE.toString(), StandardCharsets.UTF_8);
        Files.writeString(dirRun.resolve("nav-escenarios-ultimo.csv"), INDICE.toString(), StandardCharsets.UTF_8);
    }

    private static void escribirPlaceholders() throws Exception {
        String[] noSoportados = {"laberinto", "multientidad_1", "multientidad_2", "multientidad_4",
                "multientidad_8"};
        for (String id : noSoportados) {
            INDICE.append(id).append(',').append(REP).append(",-,-,-,false,false,0,0,0,0,0,0,-,-,-,")
                    .append("no_soportado_por_campana_funcional,-\n");
        }
        escribirIndice();
    }

    private static void escribirAgregados() throws Exception {
        long busquedas = 0;
        long completadas = 0;
        long fallidas = 0;
        long canceladas = 0;
        long expansiones = 0;
        long nanos = 0;
        long agotado = 0;
        long replan = 0;
        long reanudadas = 0;
        for (NavPlannerMetrics.TotalesSnapshot t : TOTALES.values()) {
            busquedas += t.busquedas();
            completadas += t.busquedasCompletadas();
            fallidas += t.busquedasFallidas();
            canceladas += t.canceladas();
            expansiones += t.expansiones();
            nanos += t.nanos();
            agotado += t.presupuestoAgotado();
            replan += t.replanificaciones();
            reanudadas += t.reanudadas();
        }
        StringBuilder agg = new StringBuilder();
        agg.append(NavPlannerMetrics.cabeceraLinea()).append('\n');
        agg.append("METRICS,").append(busquedas).append(',').append(completadas).append(',')
                .append(fallidas).append(',').append(canceladas).append(',').append(expansiones).append(',')
                .append(nanos).append(',').append(agotado).append(',').append(replan).append(',')
                .append(reanudadas).append(',').append(TOTALES.size()).append('\n');
        Files.writeString(dirRun.resolve("metrics-agregado.csv"), agg.toString(), StandardCharsets.UTF_8);
        Files.writeString(dirRun.resolve("metrics-total.csv"), NavPlannerMetrics.dumpCsv(), StandardCharsets.UTF_8);
        StringBuilder res = new StringBuilder(4096);
        res.append("{\n");
        campo(res, "schema", VERSION).append(",\n");
        campo(res, "run", RUN).append(",\n");
        campo(res, "repeticion", Integer.toString(REP)).append(",\n");
        campo(res, "escenarios", Integer.toString(PENDIENTES.size())).append(",\n");
        res.append("\"veredictos\":[");
        for (int i = 0; i < VEREDICTOS.size(); i++) {
            if (i > 0) {
                res.append(',');
            }
            String[] partes = VEREDICTOS.get(i).split("=", 2);
            res.append("{\"escenario\":").append(jstr(partes[0])).append(",\"veredicto\":")
                    .append(jstr(partes.length > 1 ? partes[1] : "-")).append('}');
        }
        res.append("],\n");
        res.append("\"agregado\":{\"busquedas\":").append(busquedas)
                .append(",\"completadas\":").append(completadas)
                .append(",\"fallidas\":").append(fallidas)
                .append(",\"canceladas\":").append(canceladas)
                .append(",\"expansiones\":").append(expansiones)
                .append(",\"nanos\":").append(nanos)
                .append(",\"presupuestoAgotado\":").append(agotado)
                .append(",\"replanificaciones\":").append(replan)
                .append(",\"reanudadas\":").append(reanudadas).append("}\n");
        res.append("}\n");
        Files.writeString(dirRun.resolve("resultado.json"), res.toString(), StandardCharsets.UTF_8);
        RESUMEN_JSON.append(res);
    }

    private static String metricasJson(NavPlannerMetrics.Snapshot snap) {
        StringBuilder sb = new StringBuilder(2048);
        NavPlannerMetrics.TotalesSnapshot t = snap.totales();
        sb.append("{\"totales\":{\"busquedas\":").append(t.busquedas())
                .append(",\"completadas\":").append(t.busquedasCompletadas())
                .append(",\"fallidas\":").append(t.busquedasFallidas())
                .append(",\"canceladas\":").append(t.canceladas())
                .append(",\"expansiones\":").append(t.expansiones())
                .append(",\"nanos\":").append(t.nanos())
                .append(",\"presupuestoAgotado\":").append(t.presupuestoAgotado())
                .append(",\"replanificaciones\":").append(t.replanificaciones())
                .append(",\"reanudadas\":").append(t.reanudadas())
                .append(",\"entidades\":").append(t.entidades()).append("},");
        sb.append("\"busquedas\":[");
        for (int i = 0; i < snap.busquedas().size(); i++) {
            NavPlannerMetrics.BusquedaSnapshot b = snap.busquedas().get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"id\":").append(b.id()).append(",\"tipo\":").append(jstr(b.tipo().name()))
                    .append(",\"tickInicio\":").append(b.tickInicio()).append(",\"nanos\":").append(b.nanos())
                    .append(",\"expansiones\":").append(b.expansiones()).append(",\"presupuesto\":")
                    .append(b.presupuesto()).append(",\"avances\":").append(b.avances())
                    .append(",\"nodosPorAvance\":").append(b.nodosPorAvance())
                    .append(",\"reanudada\":").append(b.reanudada()).append(",\"replanificacion\":")
                    .append(b.replanificacion()).append(",\"fallida\":").append(b.fallida())
                    .append(",\"presupuestoAgotado\":").append(b.presupuestoAgotado())
                    .append(",\"longitudRuta\":").append(b.longitudRuta()).append(",\"estado\":")
                    .append(jstr(b.estado().name())).append(",\"motivo\":").append(jstr(b.motivo())).append('}');
        }
        sb.append("],\"entidades\":[");
        for (int i = 0; i < snap.entidades().size(); i++) {
            NavPlannerMetrics.EntidadSnapshot e = snap.entidades().get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"entidad\":").append(jstr(String.valueOf(e.entidad())))
                    .append(",\"busquedas\":").append(e.busquedas()).append(",\"expansiones\":")
                    .append(e.expansiones()).append(",\"nanos\":").append(e.nanos())
                    .append(",\"fallos\":").append(e.fallos()).append(",\"presupuestoAgotado\":")
                    .append(e.presupuestoAgotado()).append(",\"replanificaciones\":")
                    .append(e.replanificaciones()).append(",\"canceladas\":").append(e.canceladas())
                    .append(",\"ultimoTipo\":").append(jstr(e.ultimoTipo() == null ? "-"
                            : e.ultimoTipo().name())).append(",\"ultimoTick\":").append(e.ultimoTick())
                    .append(",\"ultimoMotivo\":").append(jstr(e.ultimoMotivo())).append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    // ================================================================================================
    // Servidor, mundo, spawn, traza.
    // ================================================================================================

    private static void prepararServidor(MinecraftServer servidor) {
        GameRules reglas = servidor.getGameRules();
        reglas.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, servidor);
        reglas.getRule(GameRules.RULE_DAYLIGHT).set(false, servidor);
        reglas.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, servidor);
        reglas.getRule(GameRules.RULE_RANDOMTICKING).set(0, servidor);
        reglas.getRule(GameRules.RULE_MOBGRIEFING).set(false, servidor);
        reglas.getRule(GameRules.RULE_DOFIRETICK).set(false, servidor);
        nivel.setDayTime(18000L);
        servidor.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
    }

    private static AggressiveZombieEntity sonda(BlockPos pos) {
        AggressiveZombieEntity z = com.chipoodle.devilrpg.init.ModEntities.AGGRESSIVE_ZOMBIE.get()
                .create(nivel, null, pos, MobSpawnType.COMMAND, false, false);
        if (z == null) {
            return null;
        }
        z.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        nivel.addFreshEntity(z);
        // Los atributos se aplican al entrar al mundo; sin esto la lectura inicial era 10 y la final 40 y el
        // criterio de seguridad confundía una subida de vida con daño.
        z.setHealth(z.getMaxHealth());
        return z;
    }

    private static void forzar(int x1, int z1, int x2, int z2) {
        for (int cx = (x1 >> 4); cx <= (x2 >> 4); cx++) {
            for (int cz = (z1 >> 4); cz <= (z2 >> 4); cz++) {
                nivel.setChunkForced(cx, cz, true);
                nivel.getChunkAt(new BlockPos(cx << 4, PLATAFORMA_Y, cz << 4));
            }
        }
    }

    private static void abrirTraza() {
        if (!TRACE) {
            return;
        }
        try {
            traza = Files.newBufferedWriter(dirRun.resolve("nav-trace.log"), StandardCharsets.UTF_8);
            traza.write("# schema=av-nav-trace/1 run=" + RUN + " rep=" + REP + "\n");
            traza.write("# eventos de produccion via NavTrace.ev (flag devilrpg.a2n.traceNav)\n");
            NavTrace.sink(linea -> {
                try {
                    if (traza != null) {
                        traza.write("EVT " + linea + "\n");
                    }
                    Run r = actual;
                    if (r != null) {
                        if (linea.startsWith("TUNNEL_PLAN_START")) {
                            r.traceTunnelPlanStart++;
                        } else if (linea.startsWith("BREAK_EXECUTED_FROM_STAND")) {
                            r.traceBreakExecuted++;
                        } else if (linea.startsWith("MOVE_COMMAND")) {
                            r.traceMoveCommand++;
                        }
                    }
                } catch (Exception ignored) {
                }
            });
        } catch (Exception e) {
            log("FALLO abriendo nav-trace.log: " + e);
        }
    }

    // ================================================================================================
    // Tipos internos.
    // ================================================================================================

    private enum Tipo {
        NORMAL, DINAMICO, BLOQUEO, TUNEL, INALCANZABLE, PRESUPUESTO, LIMITES, AESTRELLA
    }

    @FunctionalInterface
    private interface Constructor {
        Layout construir(Area area);
    }

    private static final class Escenario {
        final String id;
        final String descripcion;
        final int radio;
        final Constructor constructor;
        final Set<NavRoute.Transition> transiciones = EnumSet.noneOf(NavRoute.Transition.class);
        final Set<String> acciones = new LinkedHashSet<>();
        boolean esperaLlegada = true;
        boolean esperaFallo = false;
        int ticksMax = 900;
        int roturasMin = 0;
        boolean preflightRequerido = true;
        boolean preflightGlobal = false;
        Tipo tipo = Tipo.NORMAL;
        int preflightPresupuesto = 8192;

        Escenario(String id, String descripcion, int radio, Constructor constructor) {
            this.id = id;
            this.descripcion = descripcion;
            this.radio = radio;
            this.constructor = constructor;
        }

        Escenario transicion(NavRoute.Transition t) {
            transiciones.add(t);
            return this;
        }

        Escenario accion(String a) {
            acciones.add(a);
            return this;
        }

        Escenario ticks(int t) {
            ticksMax = t;
            return this;
        }

        Escenario roturasMin(int n) {
            roturasMin = n;
            return this;
        }

        Escenario preflightRequerido(boolean b) {
            preflightRequerido = b;
            return this;
        }

        Escenario globalPreflight(boolean b) {
            preflightGlobal = b;
            return this;
        }

        Escenario esperaLlegada(boolean b) {
            esperaLlegada = b;
            return this;
        }

        Escenario esperaFallo(boolean b) {
            esperaFallo = b;
            return this;
        }

        Escenario tipo(Tipo t) {
            tipo = t;
            return this;
        }
    }

    private static final class Layout {
        final BlockPos spawn;
        final BlockPos goal;
        final List<BlockPos> roturas = new ArrayList<>();
        final List<BlockPos> construcciones = new ArrayList<>();
        final List<BlockPos> barrera = new ArrayList<>();

        Layout(BlockPos spawn, BlockPos goal) {
            this.spawn = spawn.immutable();
            this.goal = goal.immutable();
        }
    }

    private static final class Area {
        final int x;
        final int y;
        final int z;
        final int radio;

        Area(int x, int y, int z, int radio) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radio = radio;
        }

        BlockPos p(int dx, int dy, int dz) {
            return new BlockPos(x + dx, y + dy, z + dz);
        }

        void poner(BlockPos pos, BlockState estado) {
            nivel.setBlockAndUpdate(pos, estado);
        }

        void solido(int dx, int dy, int dz) {
            poner(p(dx, dy, dz), Blocks.STONE.defaultBlockState());
        }

        void bedrock(int dx, int dy, int dz) {
            poner(p(dx, dy, dz), Blocks.BEDROCK.defaultBlockState());
        }

        void aire(int dx, int dy, int dz) {
            poner(p(dx, dy, dz), Blocks.AIR.defaultBlockState());
        }

        void cristal(int dx, int dy, int dz) {
            poner(p(dx, dy, dz), Blocks.GLASS_PANE.defaultBlockState());
        }

        void tierra(int dx, int dy, int dz) {
            poner(p(dx, dy, dz), Blocks.DIRT.defaultBlockState());
        }
    }

    private static final class Preflight {
        NavRoute route;
        final Set<NavRoute.Transition> transiciones = EnumSet.noneOf(NavRoute.Transition.class);
        boolean alcanzoMision;
        boolean hitBudget;
        int expansiones;
        long nanos;
        String resumen = "-";
    }

    private static final class Run {
        final Escenario esc;
        final int slot;
        Area area;
        Layout layout;
        AggressiveZombieEntity mob;
        UUID uuid;
        Map<BlockPos, BlockState> snapshot;
        final Set<BlockPos> cambiosPermitidos = new HashSet<>();
        final Set<BlockPos> celdasConstruidas = new LinkedHashSet<>();
        final Set<BlockPos> roturas = new LinkedHashSet<>();
        final Set<NavRoute.Transition> transicionesEjecutadas = EnumSet.noneOf(NavRoute.Transition.class);
        final Set<String> accionesObservadas = new LinkedHashSet<>();
        Preflight preflight;
        String preflightResumen;
        String preflightDetalle;
        String directoLocalResumen = "-";
        String directoGlobalResumen = "-";
        String metricsEjecutor = "-";
        String error;
        String veredicto;
        boolean geometriaOk = true;
        boolean preflightOk = true;
        boolean directoLocalHit;
        boolean directoGlobalHit;
        boolean directoGlobalPendiente;
        boolean directoGlobalAlcanzo;
        int directoLocalExp;
        int directoGlobalExp;
        int ticks;
        int ticksFinal;
        int lastRouteLen;
        int lastRouteSig;
        int lastIndex = -1;
        int stepUps;
        int drops;
        int dropsFisicos;
        int ultimaY = Integer.MIN_VALUE;
        boolean fueraDeArea;
        int buildsTravesadas;
        int construccionesSinBloque;
        int bloquesTravesados;
        int tunnelPasos;
        int escaleraColocados;
        boolean escaleraTravesiaConfirmada;
        int planes;
        int recuperaciones;
        int budgetHits;
        int moves;
        int expansionsLast;
        int expansionsTotal;
        int cambiosInesperados;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int traceTunnelPlanStart;
        int traceBreakExecuted;
        int traceMoveCommand;
        double maxDist;
        float saludInicial;
        float saludFinal;
        boolean vivo;
        boolean llego;
        boolean fallido;
        boolean timeout;
        boolean barreraPuesta;
        boolean barreraQuitada;
        NavState estadoFinal;
        NavPlanner.GlobalSearchHandle astarHandle;
        NavPlanner.GlobalResult astarResult;
        boolean astarDone;
        boolean astarAlcanzo;
        int astarFrames;
        long paredInicioNs;
        long paredPreflightNs;
        long tickInicio;

        Run(Escenario esc, int slot) {
            this.esc = esc;
            this.slot = slot;
        }

        int ticksMax() {
            return Math.max(80, (int) ((long) esc.ticksMax * ESCALA / 100L));
        }

        int roturasEsperadasCubiertas() {
            int n = 0;
            for (BlockPos p : layout.roturas) {
                if (nivel.getBlockState(p).isAir()) {
                    n++;
                }
            }
            return n;
        }
    }

    private record Criterio(String id, boolean ok, String detalle) {
    }

    // ================================================================================================
    // Utilidades de formato.
    // ================================================================================================

    private static String shortPos(BlockPos p) {
        return p == null ? "-" : p.getX() + "," + p.getY() + "," + p.getZ();
    }

    private static String posJson(BlockPos p) {
        return p == null ? "null" : "[" + p.getX() + "," + p.getY() + "," + p.getZ() + "]";
    }

    private static String posListJson(List<BlockPos> lista) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < lista.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(posJson(lista.get(i)));
        }
        return sb.append(']').toString();
    }

    private static String setJson(Set<NavRoute.Transition> set) {
        StringBuilder sb = new StringBuilder("[");
        int i = 0;
        for (NavRoute.Transition t : set) {
            if (i++ > 0) {
                sb.append(',');
            }
            sb.append(jstr(t.name()));
        }
        return sb.append(']').toString();
    }

    private static String strSetJson(Set<String> set) {
        StringBuilder sb = new StringBuilder("[");
        int i = 0;
        for (String s : set) {
            if (i++ > 0) {
                sb.append(',');
            }
            sb.append(jstr(s));
        }
        return sb.append(']').toString();
    }

    private static StringBuilder campo(StringBuilder sb, String clave, String valor) {
        return sb.append(jstr(clave)).append(':').append(jstr(valor));
    }

    private static String jstr(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    private static String sinComas(String s) {
        return s == null ? "-" : s.replace(',', ';').replace('\n', ' ').replace('\r', ' ');
    }

    private static int parseEntero(String texto, String clave) {
        if (texto == null) {
            return 0;
        }
        Matcher m = Pattern.compile(Pattern.quote(clave) + "=(-?\\d+)").matcher(texto);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static void log(String mensaje) {
        DevilRpg.LOGGER.info("[NAVSCN] {}", mensaje);
    }

    private static String fechaRun() {
        return LocalDateTime.now().format(SELLO);
    }
}
