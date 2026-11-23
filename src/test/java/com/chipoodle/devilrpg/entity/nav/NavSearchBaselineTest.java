package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A3 · Línea base reproducible del A* REAL ({@link NavGraphSearch.Session}), sin Minecraft de por medio.
 *
 * <p>Usa varios {@link NavGraphSearch.Graph} sintéticos deterministas y, para cada escenario y repetición, mide con
 * {@link NavPlannerMetrics} (begin/advance/end) y con la propia sesión (expansiones, pops, stale pops, reaperturas,
 * frontera, duración, ruta). Imprime una tabla CSV {@code BASELINE,...} legible y afirma:
 * determinismo entre repeticiones y contra la búsqueda síncrona, ruta contigua y válida, coherencia exacta de las
 * métricas y un escenario de 4 entidades con sesiones intercaladas para el coste agregado. No hay umbrales de tiempo:
 * los nanos se imprimen, nunca se comparan.
 *
 * <p>Incluye además la no-regresión de comportamiento ON/OFF sobre el plan LOCAL de {@link NavPlanner} en su rama
 * alcanzable sin mundo ({@code inicio==fin}), comparando ruta/expansiones/motivo/presupuesto con el flag apagado y
 * encendido.
 */
public final class NavSearchBaselineTest {
    private static int comprobaciones;
    private static int fallos;

    private static final int BUDGET = 4096;
    private static final int SLICE = 7;
    private static final int START_FEET = 64;

    public static void main(String[] args) {
        NavPlannerMetrics.setEnabled(true);
        NavPlannerMetrics.reset();

        List<Escenario> escenarios = new ArrayList<>();
        escenarios.add(campoAbierto());
        escenarios.add(muroConHueco());
        escenarios.add(laberinto());
        escenarios.add(metaInalcanzable());
        escenarios.add(presupuestoInsuficiente());

        System.out.println("BASELINE,escenario,repeticion,modo,presupuesto,expansiones,pops,stale_pops,"
                + "reopens,frontera,encontrado,presupuesto_agotado,longitud_ruta,nanos,motivo");

        for (Escenario escenario : escenarios) {
            medirEscenario(escenario);
        }

        medirCosteAgregado4Entidades();
        medirNoRegresionPlanLocal();

        NavPlannerMetrics.Snapshot finalSnapshot = NavPlannerMetrics.snapshot();
        System.out.println(NavPlannerMetrics.cabeceraLinea());
        System.out.println(NavPlannerMetrics.dumpLinea());
        comprobar("el snapshot final conserva las 4 busquedas sinteticas y la LOCAL del arnes",
                finalSnapshot.totales().busquedas() == 5L
                        && finalSnapshot.totales().busquedasCompletadas() == 5L
                        && finalSnapshot.totales().entidades() == 4
                        && finalSnapshot.busquedas().size() == 5);

        NavPlannerMetrics.setEnabled(false);
        if (fallos > 0) {
            System.out.println("NAV_SEARCH_BASELINE_TEST: FAIL (" + fallos + " de " + comprobaciones + ")");
            System.exit(1);
        }
        System.out.println("NAV_SEARCH_BASELINE_TEST: OK (" + comprobaciones + " comprobaciones)");
    }

    // ================================================================================================
    // Escenarios deterministas.
    // ================================================================================================

    private static Escenario campoAbierto() {
        Rejilla rejilla = new Rejilla("campo_abierto_32x32", 32, 32, 0, 0, 31, 31);
        return new Escenario(rejilla, BUDGET);
    }

    private static Escenario muroConHueco() {
        Rejilla rejilla = new Rejilla("muro_con_hueco", 32, 32, 0, 16, 31, 16);
        for (int z = 0; z < 32; z++) {
            if (z != 16) {
                rejilla.muro(16, z);
            }
        }
        return new Escenario(rejilla, BUDGET);
    }

    private static Escenario laberinto() {
        Rejilla rejilla = new Rejilla("laberinto_serpentina", 32, 32, 0, 0, 0, 31);
        for (int z = 1; z < 32; z += 2) {
            boolean abreDerecha = (z / 2) % 2 == 0;
            for (int x = 0; x < 32; x++) {
                if (abreDerecha ? x != 31 : x != 0) {
                    rejilla.muro(x, z);
                }
            }
        }
        return new Escenario(rejilla, BUDGET);
    }

    private static Escenario metaInalcanzable() {
        Rejilla rejilla = new Rejilla("meta_inalcanzable", 32, 32, 0, 0, 31, 31);
        // La meta existe y es transitable, pero sus dos vecinos interiores son muro: ningún camino puede llegar.
        rejilla.muro(30, 31);
        rejilla.muro(31, 30);
        return new Escenario(rejilla, BUDGET);
    }

    private static Escenario presupuestoInsuficiente() {
        Rejilla rejilla = new Rejilla("presupuesto_insuficiente", 32, 32, 0, 0, 31, 31);
        return new Escenario(rejilla, 25);
    }

    // ================================================================================================
    // Medición por escenario: determinismo, ruta, métricas y ON/OFF.
    // ================================================================================================

    private static void medirEscenario(Escenario escenario) {
        Rejilla rejilla = escenario.rejilla;
        int presupuesto = escenario.presupuesto;
        String nombre = rejilla.nombre;
        UUID entidad = UUID.nameUUIDFromBytes(("baseline-" + nombre).getBytes(java.nio.charset.StandardCharsets.UTF_8));

        NavGraphSearch.Result referencia = NavGraphSearch.search(rejilla.inicio(), START_FEET, rejilla, presupuesto);
        imprimirBaseline(nombre, 0, "sincrono", presupuesto, referencia, 0, 0, 0, 0, 0L);
        comprobar(nombre + ": la busqueda sincrona produce un resultado", referencia != null);

        Medicion primeraConMetricas = null;
        for (int repeticion = 1; repeticion <= 3; repeticion++) {
            long tick = 1000L + repeticion;
            Medicion medicion = medirIncremental(rejilla, presupuesto, SLICE, true, entidad, tick);
            imprimirBaseline(nombre, repeticion, "incremental", presupuesto, medicion.resultado,
                    medicion.pops, medicion.stalePops, medicion.reopens, medicion.frontera, medicion.nanos);
            comprobar(nombre + " rep" + repeticion + ": mismo resultado que la busqueda sincrona",
                    medicion.resultado.equals(referencia));
            comprobar(nombre + " rep" + repeticion + ": ruta contigua y valida",
                    rutaCoherente(rejilla, rejilla.inicio(), medicion.resultado));
            comprobar(nombre + " rep" + repeticion + ": metricas coherentes con la sesion",
                    metricasCoherentes(rejilla, presupuesto, entidad, tick, medicion));
            if (primeraConMetricas == null) {
                primeraConMetricas = medicion;
            } else {
                comprobar(nombre + " rep" + repeticion + ": expansiones deterministas",
                        medicion.resultado.expansions() == primeraConMetricas.resultado.expansions());
                comprobar(nombre + " rep" + repeticion + ": pops deterministas",
                        medicion.pops == primeraConMetricas.pops
                                && medicion.stalePops == primeraConMetricas.stalePops
                                && medicion.reopens == primeraConMetricas.reopens);
                comprobar(nombre + " rep" + repeticion + ": ruta determinista",
                        medicion.resultado.route().equals(primeraConMetricas.resultado.route()));
            }
        }

        // No-regresion ON/OFF: la instrumentacion no puede alterar la busqueda. El flag se APAGA de verdad.
        NavPlannerMetrics.setEnabled(false);
        Medicion sinMetricas = medirIncremental(rejilla, presupuesto, SLICE, false, entidad, 2000L);
        NavPlannerMetrics.setEnabled(true);
        imprimirBaseline(nombre, 1, "sin_metricas", presupuesto, sinMetricas.resultado,
                sinMetricas.pops, sinMetricas.stalePops, sinMetricas.reopens, sinMetricas.frontera, sinMetricas.nanos);
        boolean mismoResultado = primeraConMetricas != null
                && primeraConMetricas.resultado.equals(sinMetricas.resultado);
        comprobar(nombre + ": metricas ON/OFF dan el mismo resultado",
                mismoResultado);
        comprobar(nombre + ": metricas ON/OFF dan las mismas expansiones, pops y reaperturas",
                primeraConMetricas != null
                        && primeraConMetricas.resultado.expansions() == sinMetricas.resultado.expansions()
                        && primeraConMetricas.pops == sinMetricas.pops
                        && primeraConMetricas.stalePops == sinMetricas.stalePops
                        && primeraConMetricas.reopens == sinMetricas.reopens);
        System.out.println("NO_REGRESION," + nombre
                + ",exp_off=" + (sinMetricas.resultado == null ? -1 : sinMetricas.resultado.expansions())
                + ",exp_on=" + (primeraConMetricas == null ? -1 : primeraConMetricas.resultado.expansions())
                + ",ruta_off=" + (sinMetricas.resultado == null ? -1 : sinMetricas.resultado.route().size())
                + ",ruta_on=" + (primeraConMetricas == null ? -1 : primeraConMetricas.resultado.route().size())
                + ",encontrado_off=" + (sinMetricas.resultado != null && sinMetricas.resultado.found())
                + ",encontrado_on=" + (primeraConMetricas != null && primeraConMetricas.resultado.found())
                + ",presupuesto_off=" + (sinMetricas.resultado != null && sinMetricas.resultado.hitBudget())
                + ",presupuesto_on=" + (primeraConMetricas != null && primeraConMetricas.resultado.hitBudget())
                + ",misma_ruta=" + mismoResultado
                + "," + (mismoResultado ? "OK" : "FALLO"));
    }

    /** Sesión REAL avanzada a slices, con métricas opcionales alrededor de cada avance. */
    private static Medicion medirIncremental(Rejilla rejilla, int presupuesto, int slice, boolean conMetricas,
                                             UUID entidad, long tick) {
        NavGraphSearch.Session sesion = NavGraphSearch.begin(rejilla.inicio(), START_FEET, rejilla, presupuesto);
        long id = conMetricas
                ? NavPlannerMetrics.begin(NavPlannerMetrics.Tipo.SINTETICA, entidad, tick, presupuesto)
                : NavPlannerMetrics.SIN_BUSQUEDA;
        int avances = 0;
        long nanos = 0L;
        while (true) {
            int expansionesAntes = sesion.expansions();
            long t0 = System.nanoTime();
            boolean terminada = sesion.advance(slice);
            long delta = System.nanoTime() - t0;
            nanos += delta;
            avances++;
            if (conMetricas) {
                NavPlannerMetrics.advance(id, sesion.expansions() - expansionesAntes, delta);
            }
            if (terminada) {
                break;
            }
        }
        NavGraphSearch.Result resultado = sesion.result();
        if (conMetricas) {
            NavPlannerMetrics.end(id, resultado.found(), resultado.hitBudget(),
                    resultado.route().size(), resultado.reason());
        }
        return new Medicion(resultado, sesion.totalPops(), sesion.totalStalePops(), sesion.totalReopens(),
                sesion.frontierSize(), nanos, avances, id, conMetricas);
    }

    private static boolean metricasCoherentes(Rejilla rejilla, int presupuesto, UUID entidad, long tick,
                                             Medicion medicion) {
        if (!medicion.conMetricas) {
            return true;
        }
        NavPlannerMetrics.BusquedaSnapshot registro = buscarBusqueda(medicion.metricId);
        if (registro == null) {
            return false;
        }
        NavGraphSearch.Result resultado = medicion.resultado;
        return registro.tipo() == NavPlannerMetrics.Tipo.SINTETICA
                && entidad.equals(registro.entidad())
                && registro.tickInicio() == tick
                && registro.presupuesto() == presupuesto
                && registro.expansiones() == resultado.expansions()
                && registro.avances() == medicion.avances
                && registro.reanudada() == (medicion.avances > 1)
                && registro.nodosPorAvance() == (medicion.avances <= 0
                        ? 0 : resultado.expansions() / medicion.avances)
                && registro.longitudRuta() == resultado.route().size()
                && registro.fallida() == !resultado.found()
                && registro.presupuestoAgotado() == resultado.hitBudget()
                && registro.motivo().equals(resultado.reason())
                && registro.estado() == (resultado.found()
                        ? NavPlannerMetrics.Estado.COMPLETADA : NavPlannerMetrics.Estado.FALLIDA);
    }

    private static boolean rutaCoherente(Rejilla rejilla, long inicio, NavGraphSearch.Result resultado) {
        List<NavGraphSearch.Step> ruta = resultado.route();
        long cursor = inicio;
        for (NavGraphSearch.Step paso : ruta) {
            int x1 = xDe(cursor);
            int z1 = zDe(cursor);
            int x2 = xDe(paso.state());
            int z2 = zDe(paso.state());
            if (!rejilla.transitable(x2, z2)) {
                return false;
            }
            if (Math.abs(x1 - x2) + Math.abs(z1 - z2) != 1) {
                return false;
            }
            cursor = paso.state();
        }
        if (resultado.found()) {
            return cursor == rejilla.meta();
        }
        return ruta.isEmpty() ? resultado.front() == inicio : cursor == resultado.front();
    }

    // ================================================================================================
    // Coste agregado con 4 entidades (sesiones intercaladas, un slice por entidad y ronda).
    // ================================================================================================

    private static void medirCosteAgregado4Entidades() {
        NavPlannerMetrics.reset();
        Rejilla rejilla = new Rejilla("agregado_4_entidades", 32, 32, 0, 0, 31, 31);
        int entidades = 4;
        NavGraphSearch.Session[] sesiones = new NavGraphSearch.Session[entidades];
        long[] ids = new long[entidades];
        UUID[] uuids = new UUID[entidades];
        boolean[] terminadas = new boolean[entidades];
        int[] avances = new int[entidades];
        long[] nanos = new long[entidades];

        for (int i = 0; i < entidades; i++) {
            uuids[i] = new UUID(0xA11CEL, i + 1L);
            sesiones[i] = NavGraphSearch.begin(rejilla.inicio(), START_FEET, rejilla, BUDGET);
            ids[i] = NavPlannerMetrics.begin(NavPlannerMetrics.Tipo.SINTETICA, uuids[i], 5000L + i, BUDGET);
        }

        int rondas = 0;
        int pendientes = entidades;
        while (pendientes > 0 && rondas < 10_000) {
            for (int i = 0; i < entidades; i++) {
                if (terminadas[i]) {
                    continue;
                }
                int expansionesAntes = sesiones[i].expansions();
                long t0 = System.nanoTime();
                boolean terminada = sesiones[i].advance(5);
                long delta = System.nanoTime() - t0;
                nanos[i] += delta;
                avances[i]++;
                NavPlannerMetrics.advance(ids[i], sesiones[i].expansions() - expansionesAntes, delta);
                if (terminada) {
                    NavGraphSearch.Result resultado = sesiones[i].result();
                    NavPlannerMetrics.end(ids[i], resultado.found(), resultado.hitBudget(),
                            resultado.route().size(), resultado.reason());
                    terminadas[i] = true;
                    pendientes--;
                }
            }
            rondas++;
        }

        NavPlannerMetrics.Snapshot snapshot = NavPlannerMetrics.snapshot();
        comprobar("agregado: las 4 sesiones terminan", pendientes == 0);
        comprobar("agregado: 4 busquedas contadas", snapshot.totales().busquedas() == entidades);
        comprobar("agregado: 4 entidades distintas", snapshot.totales().entidades() == entidades
                && snapshot.entidades().size() == entidades);
        comprobar("agregado: 4 busquedas cerradas", snapshot.busquedas().size() == entidades
                && snapshot.abiertas().isEmpty());

        long expansionesTotales = 0L;
        long expansionesPorEntidad = 0L;
        long nanosPorEntidad = 0L;
        int reanudadasEsperadas = 0;
        for (int i = 0; i < entidades; i++) {
            expansionesTotales += sesiones[i].expansions();
            NavPlannerMetrics.EntidadSnapshot agregado = buscarEntidad(snapshot.entidades(), uuids[i]);
            NavPlannerMetrics.BusquedaSnapshot registro = buscarBusqueda(ids[i]);
            comprobar("agregado: entidad " + i + " con 1 busqueda y sin fallos",
                    agregado != null && agregado.busquedas() == 1 && agregado.fallos() == 0);
            comprobar("agregado: entidad " + i + " con expansiones exactas",
                    agregado != null && agregado.expansiones() == sesiones[i].expansions());
            comprobar("agregado: entidad " + i + " con la ultima busqueda correcta",
                    agregado != null && agregado.ultimaBusqueda() == ids[i]
                            && agregado.ultimoTipo() == NavPlannerMetrics.Tipo.SINTETICA);
            comprobar("agregado: registro " + i + " con avances y reanudada coherentes",
                    registro != null && registro.avances() == avances[i]
                            && registro.reanudada() == (avances[i] > 1)
                            && registro.expansiones() == sesiones[i].expansions()
                            && registro.longitudRuta() == sesiones[i].result().route().size());
            expansionesPorEntidad += agregado == null ? 0L : agregado.expansiones();
            nanosPorEntidad += agregado == null ? 0L : agregado.nanos();
            if (avances[i] > 1) {
                reanudadasEsperadas++;
            }
            System.out.println("BASELINE_ENTIDAD," + uuids[i] + ",busquedas=1"
                    + ",expansiones=" + sesiones[i].expansions()
                    + ",avances=" + avances[i]
                    + ",nanos=" + nanos[i]
                    + ",encontrado=" + sesiones[i].result().found());
        }
        comprobar("agregado: la suma por entidad coincide con el total de expansiones",
                expansionesPorEntidad == snapshot.totales().expansiones()
                        && snapshot.totales().expansiones() == expansionesTotales);
        comprobar("agregado: la suma por entidad coincide con el total de nanos",
                nanosPorEntidad == snapshot.totales().nanos());
        comprobar("agregado: reanudadas contadas por busqueda",
                snapshot.totales().reanudadas() == reanudadasEsperadas);
        System.out.println("BASELINE_AGREGADO,entidades=" + entidades
                + ",busquedas=" + snapshot.totales().busquedas()
                + ",expansiones=" + snapshot.totales().expansiones()
                + ",nanos=" + snapshot.totales().nanos()
                + ",fallos=" + snapshot.totales().busquedasFallidas()
                + ",presupuesto_agotado=" + snapshot.totales().presupuestoAgotado()
                + ",reanudadas=" + snapshot.totales().reanudadas()
                + ",rondas=" + rondas);
    }

    // ================================================================================================
    // No-regresión del plan LOCAL de NavPlanner (rama alcanzable sin mundo: inicio == fin).
    // ================================================================================================

    private static void medirNoRegresionPlanLocal() {
        BlockPos posicion = new BlockPos(10, 64, 10);
        NavPlannerMetrics.setEnabled(false);
        NavPlanner.Result apagado = NavPlanner.plan(null, null, posicion, posicion,
                8, 4, 64, null, null, null, null);

        NavPlannerMetrics.setEnabled(true);
        NavPlannerMetrics.Snapshot antes = NavPlannerMetrics.snapshot();
        NavPlanner.Result encendido = NavPlanner.plan(null, null, posicion, posicion,
                8, 4, 64, null, null, null, null);
        NavPlannerMetrics.Snapshot despues = NavPlannerMetrics.snapshot();

        boolean mismaRuta = apagado.route().nodes().equals(encendido.route().nodes())
                && apagado.route().destination().equals(encendido.route().destination());
        boolean mismoResumen = apagado.summary().equals(encendido.summary());
        comprobar("plan local: ON/OFF misma ruta", mismaRuta);
        comprobar("plan local: ON/OFF mismas expansiones y presupuesto",
                apagado.expansions() == encendido.expansions() && apagado.hitBudget() == encendido.hitBudget());
        comprobar("plan local: ON/OFF mismo resumen", mismoResumen);
        comprobar("plan local: el agregado previo de 4 entidades sigue intacto antes de medir",
                antes.totales().busquedas() == 4L && antes.totales().entidades() == 4
                        && antes.busquedas().size() == 4);

        NavPlannerMetrics.BusquedaSnapshot registro = despues.busquedas().isEmpty()
                ? null : despues.busquedas().get(despues.busquedas().size() - 1);
        comprobar("plan local: la instrumentacion registra la busqueda LOCAL sin tocar las anteriores",
                despues.totales().busquedas() == antes.totales().busquedas() + 1
                        && despues.totales().busquedasCompletadas() == antes.totales().busquedasCompletadas() + 1
                        && despues.totales().entidades() == antes.totales().entidades()
                        && despues.busquedas().size() == antes.busquedas().size() + 1
                        && registro != null && registro.tipo() == NavPlannerMetrics.Tipo.LOCAL
                        && registro.entidad() == null && registro.tickInicio() == -1L
                        && registro.expansiones() == encendido.expansions()
                        && registro.presupuesto() == 64
                        && registro.longitudRuta() == encendido.route().nodes().size()
                        && registro.estado() == NavPlannerMetrics.Estado.COMPLETADA
                        && "meta alcanzada".equals(registro.motivo()));

        System.out.println("NO_REGRESION,plan_local_inicio_fin"
                + ",exp_off=" + apagado.expansions()
                + ",exp_on=" + encendido.expansions()
                + ",ruta_off=" + apagado.route().nodes().size()
                + ",ruta_on=" + encendido.route().nodes().size()
                + ",encontrado_off=true"
                + ",encontrado_on=true"
                + ",presupuesto_off=" + apagado.hitBudget()
                + ",presupuesto_on=" + encendido.hitBudget()
                + ",misma_ruta=" + mismaRuta
                + "," + (mismaRuta && mismoResumen ? "OK" : "FALLO"));
    }

    // ================================================================================================
    // Rejilla sintética determinista.
    // ================================================================================================

    private record Escenario(Rejilla rejilla, int presupuesto) {
    }

    private record Medicion(NavGraphSearch.Result resultado, int pops, int stalePops, int reopens, int frontera,
                            long nanos, int avances, long metricId, boolean conMetricas) {
    }

    /** Grafo de rejilla 4-direccional: coste 1 por paso, heurística Manhattan (admisible y consistente). */
    private static final class Rejilla implements NavGraphSearch.Graph {
        final String nombre;
        final int ancho;
        final int alto;
        final int inicioX;
        final int inicioZ;
        final int metaX;
        final int metaZ;
        final boolean[][] muro;

        Rejilla(String nombre, int ancho, int alto, int inicioX, int inicioZ, int metaX, int metaZ) {
            this.nombre = nombre;
            this.ancho = ancho;
            this.alto = alto;
            this.inicioX = inicioX;
            this.inicioZ = inicioZ;
            this.metaX = metaX;
            this.metaZ = metaZ;
            this.muro = new boolean[ancho][alto];
        }

        void muro(int x, int z) {
            muro[x][z] = true;
        }

        boolean transitable(int x, int z) {
            return x >= 0 && x < ancho && z >= 0 && z < alto && !muro[x][z];
        }

        long inicio() {
            return clave(inicioX, inicioZ);
        }

        long meta() {
            return clave(metaX, metaZ);
        }

        @Override
        public void expand(long state, int feet, Consumer<NavGraphSearch.Edge> out) {
            int x = xDe(state);
            int z = zDe(state);
            if (transitable(x + 1, z)) {
                out.accept(new NavGraphSearch.Edge(clave(x + 1, z), 1.0D, NavRoute.Transition.WALK, feet));
            }
            if (transitable(x - 1, z)) {
                out.accept(new NavGraphSearch.Edge(clave(x - 1, z), 1.0D, NavRoute.Transition.WALK, feet));
            }
            if (transitable(x, z + 1)) {
                out.accept(new NavGraphSearch.Edge(clave(x, z + 1), 1.0D, NavRoute.Transition.WALK, feet));
            }
            if (transitable(x, z - 1)) {
                out.accept(new NavGraphSearch.Edge(clave(x, z - 1), 1.0D, NavRoute.Transition.WALK, feet));
            }
        }

        @Override
        public boolean goal(long state, int feet) {
            return state == meta();
        }

        @Override
        public double heuristic(long state) {
            return Math.abs(xDe(state) - metaX) + Math.abs(zDe(state) - metaZ);
        }

        @Override
        public double distance(long state) {
            return heuristic(state);
        }
    }

    private static long clave(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int xDe(long state) {
        return (int) (state >> 32);
    }

    private static int zDe(long state) {
        return (int) state;
    }

    // ================================================================================================
    // Utilidades.
    // ================================================================================================

    private static NavPlannerMetrics.BusquedaSnapshot buscarBusqueda(long id) {
        NavPlannerMetrics.Snapshot snapshot = NavPlannerMetrics.snapshot();
        for (NavPlannerMetrics.BusquedaSnapshot busqueda : snapshot.busquedas()) {
            if (busqueda.id() == id) {
                return busqueda;
            }
        }
        for (NavPlannerMetrics.BusquedaSnapshot busqueda : snapshot.abiertas()) {
            if (busqueda.id() == id) {
                return busqueda;
            }
        }
        return null;
    }

    private static NavPlannerMetrics.EntidadSnapshot buscarEntidad(List<NavPlannerMetrics.EntidadSnapshot> lista,
                                                                    UUID uuid) {
        for (NavPlannerMetrics.EntidadSnapshot entidad : lista) {
            if (uuid.equals(entidad.entidad())) {
                return entidad;
            }
        }
        return null;
    }

    private static void imprimirBaseline(String escenario, int repeticion, String modo, int presupuesto,
                                         NavGraphSearch.Result resultado, int pops, int stalePops, int reopens,
                                         int frontera, long nanos) {
        if (resultado == null) {
            System.out.println("BASELINE," + escenario + "," + repeticion + "," + modo + "," + presupuesto
                    + ",0,0,0,0,0,false,false,0," + nanos + ",-");
            return;
        }
        System.out.println("BASELINE," + escenario + "," + repeticion + "," + modo + "," + presupuesto
                + "," + resultado.expansions()
                + "," + pops
                + "," + stalePops
                + "," + reopens
                + "," + frontera
                + "," + resultado.found()
                + "," + resultado.hitBudget()
                + "," + resultado.route().size()
                + "," + nanos
                + "," + resultado.reason().replace(',', ';').replace('\n', ' '));
    }

    private static void comprobar(String nombre, boolean ok) {
        comprobaciones++;
        if (!ok) {
            fallos++;
            System.out.println("  FALLO: " + nombre);
        }
    }
}
