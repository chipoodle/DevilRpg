package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A3 · <b>Perfil de rendimiento reproducible del núcleo A*</b> ({@link NavGraphSearch.Session}) sin
 * Minecraft de por medio.
 *
 * <p>Qué mide y por qué así:
 * <ul>
 *   <li>Escenarios sintéticos deterministas (campo abierto, obstáculos dispersos, muro con hueco,
 *       laberinto de 32 y de 64, meta inaccesible, presupuesto insuficiente, meta lejana y 4 entidades
 *       intercaladas) con el <b>mismo grafo</b> para la implementación de producción y para el oráculo
 *       congelado {@link NavSearchBoxedBench}.</li>
 *   <li>Calentamiento JIT explícito antes de medir y repeticiones con mediana/mínimo/p95: una sola
 *       pasada en frío (como en la línea base histórica) mezcla compilación JIT con la medida y llegó a
 *       variar 2× entre repeticiones.</li>
 *   <li>Atribución de fases en una pasada aparte: tiempo dentro de {@code Graph.expand} (validación de
 *       aristas) frente al resto (cola de prioridad, mapas de {@code g}/{@code cameFrom}, cajas). La
 *       pasada de fases NO se usa para comparar antes/después; solo para saber qué domina.</li>
 *   <li>Comparación A/B en el MISMO proceso y con el MISMO grafo: producción frente al oráculo
 *       congelado, con hash de ruta, expansiones, pops, stale pops y reaperturas. Si el hash difiere,
 *       la prueba falla: una mejora de tiempo que cambia la ruta no es una mejora, es otro planner.</li>
 * </ul>
 *
 * <p>Salida CSV (una línea por medida) para que {@code tools/perf/resumir-metricas.ps1} y
 * {@code tools/perf/comparar-perf.ps1} la agreguen sin inventar:
 * <pre>
 * PERF,escenario,rep,motor,expansiones,pops,stale_pops,reopens,frontera,avances,nanos_total,ruta_hash
 * PERF_FASE,escenario,motor,nanos_total,nanos_expand,nanos_bookkeeping,expansiones,expand_calls,pct_expand
 * PERF_RESUMEN,escenario,motor,reps,min_ns,mediana_ns,p95_ns,expansiones,nanos_por_expansion,nodos_por_avance,...
 * PERF_AB,escenario,mediana_prod,mediana_boxed,delta_pct,min_prod,min_boxed,delta_min_pct,misma_ruta,ruta_hash
 * </pre>
 *
 * <p>Propiedades: {@code -DnavPerf.reps=N} (por defecto 9), {@code -DnavPerf.warmup=N} (3),
 * {@code -DnavPerf.ab=false} para saltar el oráculo. No usa umbrales de tiempo para aprobar: los
 * números se imprimen; lo que se afirma es determinismo, igualdad de rutas y coherencia de contadores.
 */
public final class NavSearchPerfTest {
    private static final int BUDGET = 4096;
    private static final int SLICE = 7;
    private static final int START_FEET = 64;

    private static int comprobaciones;
    private static int fallos;

    public static void main(String[] args) {
        int reps = Math.max(1, Integer.getInteger("navPerf.reps", 9));
        int warmup = Math.max(0, Integer.getInteger("navPerf.warmup", 3));
        boolean ab = !"false".equalsIgnoreCase(System.getProperty("navPerf.ab", "true"));

        List<Escenario> escenarios = escenarios();
        System.out.println("PERF,escenario,rep,motor,expansiones,pops,stale_pops,reopens,frontera,avances,"
                + "nanos_total,ruta_hash");

        // Referencias síncronas: el contrato histórico de NavGraphSearch.search.
        for (Escenario escenario : escenarios) {
            escenario.referencia = NavGraphSearch.search(escenario.rejilla.inicio(), START_FEET,
                    escenario.rejilla, escenario.presupuesto);
            comprobar(escenario.rejilla.nombre + ": la busqueda sincrona produce resultado",
                    escenario.referencia != null);
        }

        // Calentamiento: compila los caminos de producción y del oráculo antes de medir.
        for (int w = 0; w < warmup; w++) {
            for (Escenario escenario : escenarios) {
                correrProduccion(escenario);
                if (ab) {
                    correrBoxed(escenario);
                }
            }
        }

        List<Muestra> produccion = new ArrayList<>();
        List<Muestra> boxed = new ArrayList<>();
        for (Escenario escenario : escenarios) {
            Muestra primeraProd = null;
            Muestra primeraBoxed = null;
            for (int rep = 1; rep <= reps; rep++) {
                Muestra p = correrProduccion(escenario);
                produccion.add(p);
                imprimirPerf(escenario, rep, "produccion", p);
                comprobar(escenario.rejilla.nombre + " rep" + rep
                        + ": produccion da la misma ruta y contadores que la busqueda sincrona",
                        igual(p.resultado, escenario.referencia));
                if (primeraProd == null) {
                    primeraProd = p;
                } else {
                    comprobar(escenario.rejilla.nombre + " rep" + rep
                                    + ": produccion determinista (expansiones/pops/stale/reopens/ruta)",
                            p.resultado.expansions() == primeraProd.resultado.expansions()
                                    && p.pops == primeraProd.pops
                                    && p.stalePops == primeraProd.stalePops
                                    && p.reopens == primeraProd.reopens
                                    && p.hash == primeraProd.hash);
                }
                if (ab) {
                    Muestra b = correrBoxed(escenario);
                    boxed.add(b);
                    imprimirPerf(escenario, rep, "boxed", b);
                    comprobar(escenario.rejilla.nombre + " rep" + rep
                                    + ": produccion y oraculo congelado dan la MISMA ruta",
                            p.hash == b.hash && igual(p.resultado, b.resultado));
                    if (primeraBoxed == null) {
                        primeraBoxed = b;
                    } else {
                        comprobar(escenario.rejilla.nombre + " rep" + rep + ": oraculo determinista",
                                b.resultado.expansions() == primeraBoxed.resultado.expansions()
                                        && b.pops == primeraBoxed.pops
                                        && b.stalePops == primeraBoxed.stalePops
                                        && b.reopens == primeraBoxed.reopens
                                        && b.hash == primeraBoxed.hash);
                    }
                }
            }
        }

        System.out.println("PERF_RESUMEN,escenario,motor,reps,min_ns,mediana_ns,p95_ns,expansiones,"
                + "nanos_por_expansion,pops,stale_pops,reopens,frontera,avances,nodos_por_avance");
        for (Escenario escenario : escenarios) {
            resumir(escenario, "produccion", produccion);
            if (ab) {
                resumir(escenario, "boxed", boxed);
            }
        }

        // Atribución de fases (varias pasadas, mediana, después del calentamiento).
        for (Escenario escenario : escenarios) {
            medirFases(escenario, "produccion");
            if (ab) {
                medirFases(escenario, "boxed");
            }
        }

        System.out.println("PERF_AB,escenario,mediana_prod_ns,mediana_boxed_ns,delta_pct,min_prod_ns,"
                + "min_boxed_ns,delta_min_pct,misma_ruta,ruta_hash");
        if (ab) {
            for (Escenario escenario : escenarios) {
                List<Muestra> prodEsc = filtrar(produccion, escenario);
                List<Muestra> boxedEsc = filtrar(boxed, escenario);
                long medianaProd = mediana(nanos(prodEsc));
                long medianaBoxed = mediana(nanos(boxedEsc));
                long minProd = minimo(nanos(prodEsc));
                long minBoxed = minimo(nanos(boxedEsc));
                boolean mismaRuta = hash(prodEsc) == hash(boxedEsc);
                System.out.println("PERF_AB," + escenario.rejilla.nombre
                        + "," + medianaProd + "," + medianaBoxed
                        + "," + porcentaje(medianaBoxed, medianaProd)
                        + "," + minProd + "," + minBoxed
                        + "," + porcentaje(minBoxed, minProd)
                        + "," + mismaRuta
                        + "," + Long.toHexString(hash(prodEsc)));
                comprobar(escenario.rejilla.nombre + ": A/B misma ruta", mismaRuta);
            }
        }

        for (Escenario escenario : escenarios) {
            if (!escenario.rejilla.nombre.startsWith("multientidad")) {
                System.out.println("PERF_ESCENARIO," + escenario.rejilla.nombre
                        + ",presupuesto=" + escenario.presupuesto
                        + ",inicio=" + escenario.rejilla.inicioX + "/" + escenario.rejilla.inicioZ
                        + ",meta=" + escenario.rejilla.metaX + "/" + escenario.rejilla.metaZ
                        + ",ancho=" + escenario.rejilla.ancho + ",alto=" + escenario.rejilla.alto);
            }
        }

        medirSobrecargaMetricas(escenarios, reps);

        if (fallos > 0) {
            System.out.println("NAV_SEARCH_PERF_TEST: FAIL (" + fallos + " de " + comprobaciones + ")");
            System.exit(1);
        }
        System.out.println("NAV_SEARCH_PERF_TEST: OK (" + comprobaciones + " comprobaciones)");
    }

    // ================================================================================================
    // Escenarios. Misma geometría y presupuesto para producción y oráculo.
    // ================================================================================================

    private static List<Escenario> escenarios() {
        List<Escenario> lista = new ArrayList<>();
        lista.add(new Escenario(new Rejilla("campo_abierto_32", 32, 32, 0, 0, 31, 31), BUDGET));
        lista.add(obstaculosDispersos());
        lista.add(muroConHueco());
        lista.add(laberinto("laberinto_serpentina_32", 32));
        lista.add(laberinto("laberinto_serpentina_64", 64));
        lista.add(metaInalcanzable());
        lista.add(new Escenario(new Rejilla("presupuesto_insuficiente_25", 32, 32, 0, 0, 31, 31), 25));
        lista.add(new Escenario(new Rejilla("meta_lejana_128", 128, 128, 0, 0, 127, 127), BUDGET));
        lista.add(multientidad(4, 64));
        lista.add(multientidad(8, 64));
        return lista;
    }

    private static Escenario obstaculosDispersos() {
        Rejilla rejilla = new Rejilla("obstaculos_dispersos_32", 32, 32, 0, 0, 31, 31);
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                if ((x == 0 && z == 0) || (x == 31 && z == 31)) {
                    continue;
                }
                long h = (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
                if (((h >>> 33) % 100L) < 18L) {
                    rejilla.muro(x, z);
                }
            }
        }
        // Corredor garantizado: fila z=0 y columna x=31. Así el escenario mide búsqueda, no "sin ruta".
        for (int x = 0; x < 32; x++) {
            rejilla.despejar(x, 0);
        }
        for (int z = 0; z < 32; z++) {
            rejilla.despejar(31, z);
        }
        return new Escenario(rejilla, BUDGET);
    }

    private static Escenario muroConHueco() {
        Rejilla rejilla = new Rejilla("muro_con_hueco_32", 32, 32, 0, 0, 31, 31);
        for (int z = 0; z < 32; z++) {
            if (z != 16) {
                rejilla.muro(16, z);
            }
        }
        return new Escenario(rejilla, BUDGET);
    }

    private static Escenario laberinto(String nombre, int lado) {
        Rejilla rejilla = new Rejilla(nombre, lado, lado, 0, 0, 0, lado - 1);
        for (int z = 1; z < lado; z += 2) {
            boolean abreDerecha = (z / 2) % 2 == 0;
            for (int x = 0; x < lado; x++) {
                if (abreDerecha ? x != lado - 1 : x != 0) {
                    rejilla.muro(x, z);
                }
            }
        }
        return new Escenario(rejilla, BUDGET);
    }

    private static Escenario metaInalcanzable() {
        Rejilla rejilla = new Rejilla("meta_inalcanzable_32", 32, 32, 0, 0, 31, 31);
        rejilla.muro(30, 31);
        rejilla.muro(31, 30);
        return new Escenario(rejilla, BUDGET);
    }

    private static Escenario multientidad(int entidades, int lado) {
        Rejilla rejilla = new Rejilla("multientidad_" + entidades, lado, lado, 0, 0, lado - 1, lado - 1);
        return new Escenario(rejilla, BUDGET, entidades);
    }

    // ================================================================================================
    // Medición de producción y del oráculo congelado.
    // ================================================================================================

    private static Muestra correrProduccion(Escenario escenario) {
        long t0 = System.nanoTime();
        NavGraphSearch.Session sesion = NavGraphSearch.begin(escenario.rejilla.inicio(), START_FEET,
                escenario.rejilla, escenario.presupuesto);
        long nanos;
        int llamadas;
        NavGraphSearch.Result resultado;
        if (escenario.entidades > 1) {
            Muestra multi = correrMultientidadProduccion(escenario);
            return multi;
        } else {
            boolean fin;
            llamadas = 0;
            do {
                fin = sesion.advance(SLICE);
                llamadas++;
            } while (!fin);
            resultado = sesion.result();
            nanos = System.nanoTime() - t0;
        }
        return new Muestra(escenario, resultado, sesion.totalPops(), sesion.totalStalePops(),
                sesion.totalReopens(), sesion.frontierSize(), nanos, llamadas, hash(resultado));
    }

    /** 4/8 sesiones intercaladas: una rebanada por entidad y ronda, como el bucle real por tick. */
    private static Muestra correrMultientidadProduccion(Escenario escenario) {
        long t0 = System.nanoTime();
        int n = escenario.entidades;
        NavGraphSearch.Session[] sesiones = new NavGraphSearch.Session[n];
        boolean[] terminadas = new boolean[n];
        int pops = 0;
        int stale = 0;
        int reopens = 0;
        int frontera = 0;
        int llamadas = 0;
        for (int i = 0; i < n; i++) {
            sesiones[i] = NavGraphSearch.begin(escenario.rejilla.inicio(), START_FEET,
                    escenario.rejilla, escenario.presupuesto);
        }
        int pendientes = n;
        while (pendientes > 0) {
            for (int i = 0; i < n; i++) {
                if (terminadas[i]) {
                    continue;
                }
                boolean fin = sesiones[i].advance(5);
                llamadas++;
                if (fin) {
                    terminadas[i] = true;
                    pendientes--;
                }
            }
        }
        for (int i = 0; i < n; i++) {
            pops += sesiones[i].totalPops();
            stale += sesiones[i].totalStalePops();
            reopens += sesiones[i].totalReopens();
            frontera += sesiones[i].frontierSize();
        }
        NavGraphSearch.Result resultado = sesiones[0].result();
        long nanos = System.nanoTime() - t0;
        for (int i = 1; i < n; i++) {
            comprobar(escenario.rejilla.nombre + ": sesion " + i + " con la misma ruta",
                    sesiones[i].result().equals(resultado));
        }
        return new Muestra(escenario, resultado, pops, stale, reopens, frontera, nanos, llamadas,
                hash(resultado));
    }

    private static Muestra correrBoxed(Escenario escenario) {
        long t0 = System.nanoTime();
        if (escenario.entidades > 1) {
            int n = escenario.entidades;
            NavSearchBoxedBench.Sesion[] sesiones = new NavSearchBoxedBench.Sesion[n];
            boolean[] terminadas = new boolean[n];
            int pops = 0;
            int stale = 0;
            int reopens = 0;
            int frontera = 0;
            int llamadas = 0;
            for (int i = 0; i < n; i++) {
                sesiones[i] = new NavSearchBoxedBench.Sesion(escenario.rejilla.inicio(), START_FEET,
                        escenario.rejilla, escenario.presupuesto);
            }
            int pendientes = n;
            while (pendientes > 0) {
                for (int i = 0; i < n; i++) {
                    if (terminadas[i]) {
                        continue;
                    }
                    boolean fin = sesiones[i].advance(5);
                    llamadas++;
                    if (fin) {
                        terminadas[i] = true;
                        pendientes--;
                    }
                }
            }
            for (int i = 0; i < n; i++) {
                pops += sesiones[i].totalPops();
                stale += sesiones[i].totalStalePops();
                reopens += sesiones[i].totalReopens();
                frontera += sesiones[i].frontierSize();
            }
            NavGraphSearch.Result resultado = sesiones[0].result();
            long nanos = System.nanoTime() - t0;
            return new Muestra(escenario, resultado, pops, stale, reopens, frontera, nanos, llamadas,
                hash(resultado));
        }
        NavSearchBoxedBench.Sesion sesion = new NavSearchBoxedBench.Sesion(escenario.rejilla.inicio(),
                START_FEET, escenario.rejilla, escenario.presupuesto);
        boolean fin;
        int llamadas = 0;
        do {
            fin = sesion.advance(SLICE);
            llamadas++;
        } while (!fin);
        NavGraphSearch.Result resultado = sesion.result();
        long nanos = System.nanoTime() - t0;
        return new Muestra(escenario, resultado, sesion.totalPops(), sesion.totalStalePops(),
                sesion.totalReopens(), sesion.frontierSize(), nanos, llamadas, hash(resultado));
    }

    // ================================================================================================
    // Fases: cuánto se va dentro de Graph.expand (validación de aristas) y cuánto en la búsqueda.
    // ================================================================================================

    // ================================================================================================
    // Fases: cuánto se va dentro de Graph.expand (validación de aristas) y cuánto en la búsqueda.
    // ================================================================================================

    private static Fase medirFaseUna(Escenario escenario, String motor) {
        CronoGraph crono = new CronoGraph(escenario.rejilla);
        long t0 = System.nanoTime();
        NavGraphSearch.Result resultado;
        int expansiones;
        int n = escenario.entidades;
        if ("boxed".equals(motor)) {
            NavSearchBoxedBench.Sesion[] sesiones = new NavSearchBoxedBench.Sesion[n];
            boolean[] terminadas = new boolean[n];
            for (int i = 0; i < n; i++) {
                sesiones[i] = new NavSearchBoxedBench.Sesion(escenario.rejilla.inicio(), START_FEET,
                        crono, escenario.presupuesto);
            }
            int pendientes = n;
            while (pendientes > 0) {
                for (int i = 0; i < n; i++) {
                    if (!terminadas[i] && sesiones[i].advance(n > 1 ? 5 : SLICE)) {
                        terminadas[i] = true;
                        pendientes--;
                    }
                }
            }
            expansiones = 0;
            for (int i = 0; i < n; i++) {
                expansiones += sesiones[i].expansions();
            }
            resultado = sesiones[0].result();
        } else if (n > 1) {
            NavGraphSearch.Session[] sesiones = new NavGraphSearch.Session[n];
            boolean[] terminadas = new boolean[n];
            for (int i = 0; i < n; i++) {
                sesiones[i] = NavGraphSearch.begin(escenario.rejilla.inicio(), START_FEET, crono,
                        escenario.presupuesto);
            }
            int pendientes = n;
            while (pendientes > 0) {
                for (int i = 0; i < n; i++) {
                    if (!terminadas[i] && sesiones[i].advance(5)) {
                        terminadas[i] = true;
                        pendientes--;
                    }
                }
            }
            expansiones = 0;
            for (int i = 0; i < n; i++) {
                expansiones += sesiones[i].expansions();
            }
            resultado = sesiones[0].result();
        } else {
            NavGraphSearch.Session sesion = NavGraphSearch.begin(escenario.rejilla.inicio(), START_FEET,
                    crono, escenario.presupuesto);
            boolean fin;
            do {
                fin = sesion.advance(SLICE);
            } while (!fin);
            expansiones = sesion.expansions();
            resultado = sesion.result();
        }
        long nanos = System.nanoTime() - t0;
        return new Fase(nanos, crono.expandNanos, expansiones, crono.expandCalls);
    }

    /**
     * Fases con repeticiones y mediana: una sola pasada en frío mezcla JIT y GC y llega a variar 10×.
     * La atribución es indicativa, no un umbral de aprobación.
     */
    private static void medirFases(Escenario escenario, String motor) {
        int repsFase = Math.max(1, Integer.getInteger("navPerf.faseReps", 5));
        long[] totales = new long[repsFase];
        long[] expands = new long[repsFase];
        int expansiones = 0;
        int calls = 0;
        for (int r = 0; r < repsFase; r++) {
            Fase fase = medirFaseUna(escenario, motor);
            totales[r] = fase.nanos;
            expands[r] = fase.expandNanos;
            expansiones = fase.expansiones;
            calls = fase.expandCalls;
        }
        long nanos = mediana(totales);
        long expandNanos = mediana(expands);
        long bookkeeping = Math.max(0L, nanos - expandNanos);
        int pct = nanos <= 0L ? 0 : (int) Math.round(100.0D * expandNanos / nanos);
        System.out.println("PERF_FASE," + escenario.rejilla.nombre + "," + motor
                + "," + repsFase + "," + nanos + "," + expandNanos + "," + bookkeeping
                + "," + expansiones + "," + calls + "," + pct);
    }

    private record Fase(long nanos, long expandNanos, int expansiones, int expandCalls) {
    }

    private static final class CronoGraph implements NavGraphSearch.Graph {
        private final NavGraphSearch.Graph base;
        long expandNanos;
        int expandCalls;

        CronoGraph(NavGraphSearch.Graph base) {
            this.base = base;
        }

        @Override
        public void expand(long state, int feet, Consumer<NavGraphSearch.Edge> out) {
            long t0 = System.nanoTime();
            try {
                base.expand(state, feet, out);
            } finally {
                expandNanos += System.nanoTime() - t0;
                expandCalls++;
            }
        }

        @Override
        public boolean goal(long state, int feet) {
            return base.goal(state, feet);
        }

        @Override
        public double heuristic(long state) {
            return base.heuristic(state);
        }

        @Override
        public double distance(long state) {
            return base.distance(state);
        }
    }

    // ================================================================================================
    // Sobrecarga de NavPlannerMetrics: lo que cuesta medir, medido.
    // ================================================================================================

    /**
     * La línea base histórica comparaba tiempo con métricas ON/OFF en UNA pasada y sin calentar: las
     * repeticiones ON salían hasta 3× más lentas, pero eso mezcla JIT/orden con instrumentación. Aquí
     * se calienta, se alternan 7 repeticiones y se da la mediana. Se pasa {@code nanos=0} a
     * {@code advance} a propósito: se mide el coste de CONTABILIZAR (begin/advance/end + mapas), no el
     * de leer el reloj, que solo existe cuando el flag está encendido.
     */
    private static void medirSobrecargaMetricas(List<Escenario> escenarios, int reps) {
        int repsMetricas = Math.max(5, Math.min(15, reps));
        UUID entidad = UUID.nameUUIDFromBytes("perf-metricas".getBytes(StandardCharsets.UTF_8));
        System.out.println("PERF_METRICAS,escenario,mediana_sin_ns,mediana_con_ns,delta_pct,"
                + "min_sin_ns,min_con_ns,expansiones,misma_ruta,ruta_hash");
        try {
            NavPlannerMetrics.setEnabled(false);
            for (int w = 0; w < 4; w++) {
                for (Escenario escenario : escenarios) {
                    correrProduccion(escenario);
                    correrConMetricas(escenario, entidad);
                }
            }
            for (Escenario escenario : escenarios) {
                long[] sin = new long[repsMetricas];
                long[] con = new long[repsMetricas];
                Muestra referencia = null;
                boolean mismaRuta = true;
                for (int r = 0; r < repsMetricas; r++) {
                    sin[r] = correrProduccion(escenario).nanos;
                    Muestra conMetricas = correrConMetricas(escenario, entidad);
                    con[r] = conMetricas.nanos;
                    if (referencia == null) {
                        referencia = conMetricas;
                    } else {
                        mismaRuta &= referencia.hash == conMetricas.hash
                                && referencia.resultado.equals(conMetricas.resultado);
                    }
                }
                long medianaSin = mediana(sin);
                long medianaCon = mediana(con);
                System.out.println("PERF_METRICAS," + escenario.rejilla.nombre
                        + "," + medianaSin + "," + medianaCon
                        + "," + porcentaje(medianaSin, medianaCon)
                        + "," + minimo(sin) + "," + minimo(con)
                        + "," + (referencia == null ? 0 : referencia.resultado.expansions() * (long) escenario.entidades)
                        + "," + mismaRuta
                        + "," + Long.toHexString(referencia == null ? 0L : referencia.hash));
                comprobar(escenario.rejilla.nombre + ": metricas ON/OFF misma ruta",
                        mismaRuta && referencia != null && igual(referencia.resultado, escenario.referencia));
            }
        } finally {
            NavPlannerMetrics.setEnabled(false);
            NavPlannerMetrics.reset();
        }
    }

    private static Muestra correrConMetricas(Escenario escenario, UUID entidad) {
        NavPlannerMetrics.setEnabled(true);
        NavPlannerMetrics.reset();
        long t0 = System.nanoTime();
        int n = escenario.entidades;
        NavGraphSearch.Session[] sesiones = new NavGraphSearch.Session[n];
        long[] ids = new long[n];
        boolean[] terminadas = new boolean[n];
        for (int i = 0; i < n; i++) {
            sesiones[i] = NavGraphSearch.begin(escenario.rejilla.inicio(), START_FEET,
                    escenario.rejilla, escenario.presupuesto);
            ids[i] = NavPlannerMetrics.begin(NavPlannerMetrics.Tipo.SINTETICA, entidad, 9000L + i,
                    escenario.presupuesto);
        }
        int pops = 0;
        int stale = 0;
        int reopens = 0;
        int frontera = 0;
        int llamadas = 0;
        int pendientes = n;
        while (pendientes > 0) {
            for (int i = 0; i < n; i++) {
                if (terminadas[i]) {
                    continue;
                }
                int antes = sesiones[i].expansions();
                boolean fin = sesiones[i].advance(n > 1 ? 5 : SLICE);
                llamadas++;
                NavPlannerMetrics.advance(ids[i], sesiones[i].expansions() - antes, 0L);
                if (fin) {
                    NavGraphSearch.Result resultado = sesiones[i].result();
                    NavPlannerMetrics.end(ids[i], resultado.found(), resultado.hitBudget(),
                            resultado.route().size(), resultado.reason());
                    terminadas[i] = true;
                    pendientes--;
                }
            }
        }
        for (int i = 0; i < n; i++) {
            pops += sesiones[i].totalPops();
            stale += sesiones[i].totalStalePops();
            reopens += sesiones[i].totalReopens();
            frontera += sesiones[i].frontierSize();
        }
        NavGraphSearch.Result resultado = sesiones[0].result();
        long nanos = System.nanoTime() - t0;
        return new Muestra(escenario, resultado, pops, stale, reopens, frontera, nanos, llamadas,
                hash(resultado));
    }

    // ================================================================================================
    // Agregados e impresión.
    // ================================================================================================

    private static void imprimirPerf(Escenario escenario, int rep, String motor, Muestra m) {
        System.out.println("PERF," + escenario.rejilla.nombre + "," + rep + "," + motor
                + "," + (m.resultado.expansions() * (long) escenario.entidades)
                + "," + m.pops
                + "," + m.stalePops
                + "," + m.reopens
                + "," + m.frontera
                + "," + m.llamadas
                + "," + m.nanos
                + "," + Long.toHexString(m.hash));
    }

    private static void resumir(Escenario escenario, String motor, List<Muestra> todas) {
        List<Muestra> propias = filtrar(todas, escenario);
        long[] valores = nanos(propias);
        long min = minimo(valores);
        long mediana = mediana(valores);
        long p95 = percentil(valores, 95);
        Muestra ultima = propias.get(propias.size() - 1);
        long expansiones = ultima.resultado.expansions() * (long) escenario.entidades;
        long porExpansion = expansiones == 0L ? 0L : mediana / expansiones;
        double nodosPorAvance = ultima.llamadas == 0 ? 0.0D : (double) expansiones / ultima.llamadas;
        System.out.println("PERF_RESUMEN," + escenario.rejilla.nombre + "," + motor
                + "," + propias.size()
                + "," + min
                + "," + mediana
                + "," + p95
                + "," + expansiones
                + "," + porExpansion
                + "," + ultima.pops
                + "," + ultima.stalePops
                + "," + ultima.reopens
                + "," + ultima.frontera
                + "," + ultima.llamadas
                + "," + String.format(java.util.Locale.ROOT, "%.3f", nodosPorAvance));
    }

    private static List<Muestra> filtrar(List<Muestra> todas, Escenario escenario) {
        List<Muestra> propias = new ArrayList<>();
        for (Muestra m : todas) {
            if (m.escenario == escenario) {
                propias.add(m);
            }
        }
        if (propias.isEmpty()) {
            throw new IllegalStateException("sin muestras para " + escenario.rejilla.nombre);
        }
        return propias;
    }

    private static long[] nanos(List<Muestra> muestras) {
        long[] valores = new long[muestras.size()];
        for (int i = 0; i < valores.length; i++) {
            valores[i] = muestras.get(i).nanos;
        }
        return valores;
    }

    private static long minimo(long[] valores) {
        long min = Long.MAX_VALUE;
        for (long v : valores) {
            min = Math.min(min, v);
        }
        return min == Long.MAX_VALUE ? 0L : min;
    }

    private static long mediana(long[] valores) {
        if (valores.length == 0) {
            return 0L;
        }
        long[] copia = Arrays.copyOf(valores, valores.length);
        Arrays.sort(copia);
        int medio = copia.length / 2;
        return (copia.length & 1) == 1 ? copia[medio] : (copia[medio - 1] + copia[medio]) / 2L;
    }

    private static long percentil(long[] valores, int p) {
        if (valores.length == 0) {
            return 0L;
        }
        long[] copia = Arrays.copyOf(valores, valores.length);
        Arrays.sort(copia);
        int indice = (int) Math.ceil(p / 100.0D * copia.length) - 1;
        indice = Math.max(0, Math.min(copia.length - 1, indice));
        return copia[indice];
    }

    /** Delta relativo: positivo = producción más rápida que el oráculo. */
    private static String porcentaje(long referencia, long nuevo) {
        if (referencia <= 0L) {
            return "-";
        }
        return String.format(java.util.Locale.ROOT, "%.2f", 100.0D * (referencia - nuevo) / referencia);
    }

    private static long hash(List<Muestra> muestras) {
        long h = 1L;
        for (Muestra m : muestras) {
            h = h * 31L + m.hash;
        }
        return h;
    }

    private static boolean igual(NavGraphSearch.Result a, NavGraphSearch.Result b) {
        return a != null && a.equals(b);
    }

    /** FNV-1a sobre la ruta completa: estado, pies, transición y posiciones de acción/soporte. */
    private static long hash(NavGraphSearch.Result resultado) {
        long h = 0xcbf29ce484222325L;
        for (NavGraphSearch.Step paso : resultado.route()) {
            h = mezclar(h, paso.state());
            h = mezclar(h, paso.feet());
            h = mezclar(h, paso.transition().ordinal());
            h = mezclar(h, paso.actionPos() == null ? Long.MIN_VALUE : paso.actionPos().asLong());
            h = mezclar(h, paso.standPos() == null ? Long.MIN_VALUE : paso.standPos().asLong());
        }
        h = mezclar(h, resultado.front());
        h = mezclar(h, resultado.found() ? 1L : 0L);
        return h;
    }

    private static long mezclar(long h, long valor) {
        return (h ^ valor) * 0x100000001b3L;
    }

    private static void comprobar(String nombre, boolean ok) {
        comprobaciones++;
        if (!ok) {
            fallos++;
            System.out.println("  FALLO: " + nombre);
        }
    }

    private static final class Escenario {
        final Rejilla rejilla;
        final int presupuesto;
        final int entidades;
        NavGraphSearch.Result referencia;

        Escenario(Rejilla rejilla, int presupuesto) {
            this(rejilla, presupuesto, 1);
        }

        Escenario(Rejilla rejilla, int presupuesto, int entidades) {
            this.rejilla = rejilla;
            this.presupuesto = presupuesto;
            this.entidades = entidades;
        }
    }

    private record Muestra(Escenario escenario, NavGraphSearch.Result resultado, int pops, int stalePops,
                           int reopens, int frontera, long nanos, int llamadas, long hash) {
    }

    // ================================================================================================
    // Rejilla sintética determinista (misma semántica que la línea base histórica).
    // ================================================================================================

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

        void despejar(int x, int z) {
            muro[x][z] = false;
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
}
