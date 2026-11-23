package com.chipoodle.devilrpg.entity.nav;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A3 · <b>Instrumentación del planificador A* real</b> (métricas, no comportamiento).
 *
 * <p>Esta clase SOLO mide: no decide rutas, no cambia costes, poda, presupuestos, heurísticas ni criterios de meta.
 * El planificador llama a {@link #begin}, {@link #advance}, {@link #end} y {@link #cancel} en los mismos puntos en los
 * que ya crea, avanza y abandona una búsqueda; con el flag apagado esas llamadas solo comprueban un
 * {@code volatile} y devuelven, sin asignar, sin formatear y sin tocar el reloj.
 *
 * <h2>API de bajo coste</h2>
 * <ul>
 *   <li>Flag de sistema {@code devilrpg.nav.metrics} (por defecto {@code false}) y {@link #setEnabled(boolean)} para
 *       tests/arnés. {@link #reset()} limpia los datos sin cambiar el flag.</li>
 *   <li>{@link #snapshot()} devuelve una copia inmutable de totales, búsquedas cerradas, búsquedas abiertas y
 *       agregados por entidad.</li>
 *   <li>{@link #dumpLinea()} produce UNA línea CSV de totales; {@link #dumpCsv()} el detalle completo;
 *       {@link #dump(PrintStream)} es el único punto que escribe (nunca se imprime solo).</li>
 * </ul>
 *
 * <h2>Seguridad en runtime</h2>
 * <ul>
 *   <li>Ningún método lanza: entradas nulas, ids desconocidos, deltas/naños negativos o cierres duplicados se
 *       ignoran o se acotan. Las sumas son saturadas para no desbordar.</li>
 *   <li>No hay locks ni esperas: estructuras concurrentes, contadores {@link AtomicLong} y desalojo oportunista.
 *       En producción todo ocurre en el hilo del servidor; una lectura concurrente puede ver un estado mezclado,
 *       pero nunca bloquea ni lanza.</li>
 *   <li>Memoria acotada: {@value #MAX_ENTIDADES} entidades, {@value #MAX_ACTIVAS} búsquedas activas y
 *       {@value #MAX_REGISTROS} registros cerrados. Al superar cada tope se desaloja la entrada más antigua
 *       (aproximación LRU por secuencia de uso). Los totales agregados NO se pierden al desalojar.</li>
 *   <li>Los ids son monótonos y no se reutilizan (tampoco tras {@link #reset()}), de modo que un id viejo nunca
 *       puede atribuirse a una búsqueda nueva.</li>
 * </ul>
 *
 * <p><b>Semántica de los datos</b>: {@code nanos} es la suma del trabajo medido de los avances/slices, no el tiempo
 * de pared entre ticks; una búsqueda es {@code reanudada} si recibió más de un {@link #advance}; una
 * {@code replanificación} es un {@link #begin} de la misma entidad mientras tiene otra búsqueda abierta o cuando su
 * última búsqueda terminó cancelada; una cancelación no cuenta como fallo (se contabiliza aparte).
 */
public final class NavPlannerMetrics {

    /** Tipo de búsqueda instrumentada. */
    public enum Tipo {
        /** A* global retenida entre ticks ({@code planGlobalIncremental}). */
        GLOBAL,
        /** Plan local síncrono ({@code NavPlanner.plan}). */
        LOCAL,
        /** Variante de meta exacta ({@code planExact}). */
        GLOBAL_EXACT,
        /** Búsqueda sintética de arnés/test (no viene de {@code NavPlanner}). */
        SINTETICA
    }

    /** Estado final de una búsqueda; {@link #ABIERTA} solo aparece en {@link Snapshot#abiertas()}. */
    public enum Estado {
        ABIERTA, COMPLETADA, FALLIDA, CANCELADA
    }

    /** Id que significa "no hay búsqueda medida" (flag apagado, datos inválidos o búsqueda ya cerrada). */
    public static final long SIN_BUSQUEDA = 0L;

    /** Tope de entidades con agregado propio; al superarlo se desaloja la menos usada. */
    public static final int MAX_ENTIDADES = 512;
    /** Tope de búsquedas activas simultáneas; al superarlo se desaloja la más antigua. */
    public static final int MAX_ACTIVAS = 4096;
    /** Tope de registros cerrados conservados para {@link #snapshot()}/{@link #dumpCsv()}. */
    public static final int MAX_REGISTROS = 512;

    private static final String MOTIVO_DESCONOCIDO = "-";

    private static volatile boolean enabled = Boolean.getBoolean("devilrpg.nav.metrics");

    private static final AtomicLong secuencia = new AtomicLong();
    private static final AtomicLong ids = new AtomicLong();

    private static final Map<UUID, Entidad> porEntidad = new ConcurrentHashMap<>();
    private static final Map<Long, Busqueda> activas = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedDeque<BusquedaSnapshot> registros = new ConcurrentLinkedDeque<>();
    private static final AtomicInteger registrosTam = new AtomicInteger();

    private static final AtomicLong totalBusquedas = new AtomicLong();
    private static final AtomicLong totalCompletadas = new AtomicLong();
    private static final AtomicLong totalFallidas = new AtomicLong();
    private static final AtomicLong totalCanceladas = new AtomicLong();
    private static final AtomicLong totalExpansiones = new AtomicLong();
    private static final AtomicLong totalNanos = new AtomicLong();
    private static final AtomicLong totalPresupuestoAgotado = new AtomicLong();
    private static final AtomicLong totalReplanificaciones = new AtomicLong();
    private static final AtomicLong totalReanudadas = new AtomicLong();

    private NavPlannerMetrics() {
    }

    // ================================================================================================
    // API de control.
    // ================================================================================================

    /** {@code true} solo si la instrumentación está activa. Comprueba un {@code volatile}. */
    public static boolean enabled() {
        return enabled;
    }

    /** Activa o desactiva la instrumentación sin borrar los datos ya recogidos. */
    public static void setEnabled(boolean activo) {
        enabled = activo;
    }

    /**
     * Borra todos los datos. No cambia el flag ni reinicia los ids monótonos. No debe llamarse con búsquedas en
     * vuelo: sus ids dejan de estar registrados y sus avances posteriores serán no-op (nunca lanzan).
     */
    public static void reset() {
        porEntidad.clear();
        activas.clear();
        registros.clear();
        registrosTam.set(0);
        totalBusquedas.set(0L);
        totalCompletadas.set(0L);
        totalFallidas.set(0L);
        totalCanceladas.set(0L);
        totalExpansiones.set(0L);
        totalNanos.set(0L);
        totalPresupuestoAgotado.set(0L);
        totalReplanificaciones.set(0L);
        totalReanudadas.set(0L);
    }

    // ================================================================================================
    // API de medición. Todas las llamadas comprueban el flag antes de asignar o formatear.
    // ================================================================================================

    /**
     * Registra el inicio de una búsqueda.
     *
     * @param tipo             tipo de búsqueda ({@code null} se registra como {@link Tipo#SINTETICA})
     * @param entidad          UUID de la entidad, o {@code null} si no aplica
     * @param tickInicio       tick de juego al iniciar ({@code -1} si se desconoce)
     * @param presupuestoMaximo presupuesto máximo de expansiones (negativo se acota a 0)
     * @return id de la búsqueda, o {@link #SIN_BUSQUEDA} si la instrumentación está apagada
     */
    public static long begin(Tipo tipo, UUID entidad, long tickInicio, int presupuestoMaximo) {
        if (!enabled) {
            return SIN_BUSQUEDA;
        }
        try {
            Tipo tipoSeguro = tipo == null ? Tipo.SINTETICA : tipo;
            int presupuesto = Math.max(0, presupuestoMaximo);
            long id = ids.incrementAndGet();
            Busqueda busqueda = new Busqueda(id, tipoSeguro, entidad, tickInicio, presupuesto,
                    secuencia.incrementAndGet());
            Entidad agregado = entidad(entidad);
            boolean replanificacion = false;
            if (agregado != null) {
                replanificacion = agregado.busquedaAbierta != SIN_BUSQUEDA || agregado.ultimaCancelada;
                agregado.busquedas = sumaInt(agregado.busquedas, 1);
                agregado.busquedaAbierta = id;
                agregado.ultimaCancelada = false;
                agregado.ultimaBusqueda = id;
                agregado.ultimoTipo = tipoSeguro;
                agregado.ultimoTick = tickInicio;
                agregado.ultimoMotivo = null;
                if (replanificacion) {
                    agregado.replanificaciones = sumaInt(agregado.replanificaciones, 1);
                    acumular(totalReplanificaciones, 1L);
                }
            }
            busqueda.replanificacion = replanificacion;
            activas.put(id, busqueda);
            acumular(totalBusquedas, 1L);
            while (activas.size() > MAX_ACTIVAS) {
                if (!desalojarActiva()) {
                    break;
                }
            }
            return id;
        } catch (RuntimeException ex) {
            return SIN_BUSQUEDA;
        }
    }

    /**
     * Registra un avance (slice) de una búsqueda: expansiones nuevas y tiempo medido de ese avance.
     * Más de un avance marca la búsqueda como reanudada. Ids desconocidos/cerrados no hacen nada.
     */
    public static void advance(long id, int expansionesDelta, long nanosDelta) {
        if (!enabled || id <= SIN_BUSQUEDA) {
            return;
        }
        try {
            Busqueda busqueda = activas.get(id);
            if (busqueda == null) {
                return;
            }
            int expansiones = Math.max(0, expansionesDelta);
            long nanos = Math.max(0L, nanosDelta);
            busqueda.expansiones = sumaInt(busqueda.expansiones, expansiones);
            busqueda.nanos = sumaLong(busqueda.nanos, nanos);
            busqueda.avances = sumaInt(busqueda.avances, 1);
            busqueda.ultimoSliceExpansiones = expansiones;
            if (busqueda.avances > 1) {
                if (!busqueda.reanudada) {
                    acumular(totalReanudadas, 1L);
                }
                busqueda.reanudada = true;
            }
            acumular(totalExpansiones, expansiones);
            acumular(totalNanos, nanos);
            Entidad agregado = agregadoDe(busqueda.entidad);
            if (agregado != null) {
                agregado.expansiones = sumaLong(agregado.expansiones, expansiones);
                agregado.nanos = sumaLong(agregado.nanos, nanos);
                agregado.lastTouched = secuencia.incrementAndGet();
            }
        } catch (RuntimeException ignored) {
            // La instrumentación nunca puede tumbar al planificador.
        }
    }

    /**
     * Cierra una búsqueda. {@code exito == false} la cuenta como fallida. Un id ya cerrado/desconocido no hace nada.
     */
    public static void end(long id, boolean exito, boolean presupuestoAgotado, int longitudRuta, String motivo) {
        if (!enabled || id <= SIN_BUSQUEDA) {
            return;
        }
        try {
            Busqueda busqueda = activas.remove(id);
            if (busqueda == null) {
                return;
            }
            busqueda.exito = exito;
            busqueda.presupuestoAgotado = presupuestoAgotado;
            busqueda.longitudRuta = Math.max(0, longitudRuta);
            busqueda.motivo = motivo(motivo);
            busqueda.estado = exito ? Estado.COMPLETADA : Estado.FALLIDA;
            registrar(busqueda);
            if (exito) {
                acumular(totalCompletadas, 1L);
            } else {
                acumular(totalFallidas, 1L);
            }
            if (presupuestoAgotado) {
                acumular(totalPresupuestoAgotado, 1L);
            }
            Entidad agregado = agregadoDe(busqueda.entidad);
            if (agregado != null) {
                if (agregado.busquedaAbierta == id) {
                    agregado.busquedaAbierta = SIN_BUSQUEDA;
                }
                agregado.ultimaBusqueda = id;
                agregado.ultimoTipo = busqueda.tipo;
                agregado.ultimoTick = busqueda.tickInicio;
                agregado.ultimoMotivo = busqueda.motivo;
                agregado.ultimaLongitudRuta = busqueda.longitudRuta;
                agregado.lastTouched = secuencia.incrementAndGet();
                if (!exito) {
                    agregado.fallos = sumaInt(agregado.fallos, 1);
                }
                if (presupuestoAgotado) {
                    agregado.presupuestoAgotado = sumaInt(agregado.presupuestoAgotado, 1);
                }
            }
        } catch (RuntimeException ignored) {
            // La instrumentación nunca puede tumbar al planificador.
        }
    }

    /**
     * Cancela una búsqueda abierta (misión reemplazada, parámetros cambiados, etc.). No cuenta como fallo.
     * Si es la última búsqueda de la entidad, la siguiente {@link #begin} de esa entidad cuenta como replanificación.
     */
    public static void cancel(long id, String motivo) {
        if (!enabled || id <= SIN_BUSQUEDA) {
            return;
        }
        try {
            Busqueda busqueda = activas.remove(id);
            if (busqueda == null) {
                return;
            }
            busqueda.exito = false;
            busqueda.estado = Estado.CANCELADA;
            busqueda.motivo = motivo(motivo);
            registrar(busqueda);
            acumular(totalCanceladas, 1L);
            Entidad agregado = agregadoDe(busqueda.entidad);
            if (agregado != null) {
                agregado.canceladas = sumaInt(agregado.canceladas, 1);
                agregado.ultimaBusqueda = id;
                agregado.ultimoTipo = busqueda.tipo;
                agregado.ultimoTick = busqueda.tickInicio;
                agregado.ultimoMotivo = busqueda.motivo;
                agregado.lastTouched = secuencia.incrementAndGet();
                if (agregado.busquedaAbierta == id) {
                    agregado.busquedaAbierta = SIN_BUSQUEDA;
                    agregado.ultimaCancelada = true;
                }
            }
        } catch (RuntimeException ignored) {
            // La instrumentación nunca puede tumbar al planificador.
        }
    }

    // ================================================================================================
    // Lectura y volcado explícito.
    // ================================================================================================

    /** Copia inmutable del estado actual. */
    public static Snapshot snapshot() {
        List<BusquedaSnapshot> abiertasAhora = new ArrayList<>();
        for (Busqueda busqueda : activas.values()) {
            abiertasAhora.add(busqueda.snapshot());
        }
        abiertasAhora.sort(Comparator.comparingLong(BusquedaSnapshot::id));
        List<BusquedaSnapshot> cerradas = new ArrayList<>(registros);
        List<EntidadSnapshot> entidades = new ArrayList<>();
        for (Entidad agregado : porEntidad.values()) {
            entidades.add(agregado.snapshot());
        }
        entidades.sort(Comparator.comparing(EntidadSnapshot::entidad));
        TotalesSnapshot totales = new TotalesSnapshot(
                totalBusquedas.get(), totalCompletadas.get(), totalFallidas.get(), totalCanceladas.get(),
                totalExpansiones.get(), totalNanos.get(), totalPresupuestoAgotado.get(),
                totalReplanificaciones.get(), totalReanudadas.get(), porEntidad.size());
        return new Snapshot(totales, List.copyOf(cerradas), List.copyOf(abiertasAhora), List.copyOf(entidades));
    }

    /** Cabecera de la línea de totales de {@link #dumpLinea()}. */
    public static String cabeceraLinea() {
        return "METRICS,busquedas,completadas,fallidas,canceladas,expansiones,nanos,"
                + "presupuesto_agotado,replanificaciones,reanudadas,entidades";
    }

    /** UNA línea CSV con los totales agregados. Es el único formato pensado para logs de arnés. */
    public static String dumpLinea() {
        Snapshot instantanea = snapshot();
        TotalesSnapshot totales = instantanea.totales();
        return "METRICS," + totales.busquedas()
                + "," + totales.busquedasCompletadas()
                + "," + totales.busquedasFallidas()
                + "," + totales.canceladas()
                + "," + totales.expansiones()
                + "," + totales.nanos()
                + "," + totales.presupuestoAgotado()
                + "," + totales.replanificaciones()
                + "," + totales.reanudadas()
                + "," + totales.entidades();
    }

    /** CSV completo: totales, búsquedas cerradas, búsquedas abiertas y agregados por entidad. */
    public static String dumpCsv() {
        Snapshot instantanea = snapshot();
        StringBuilder sb = new StringBuilder(1024);
        sb.append(cabeceraLinea()).append('\n').append(dumpLinea()).append('\n');
        sb.append("BUSQUEDA,id,tipo,entidad,tick_inicio,nanos,expansiones,presupuesto,avances,")
                .append("nodos_por_avance,ultimo_slice,reanudada,replanificacion,fallida,")
                .append("presupuesto_agotado,longitud_ruta,estado,motivo\n");
        for (BusquedaSnapshot busqueda : instantanea.busquedas()) {
            sb.append(filaBusqueda(busqueda)).append('\n');
        }
        for (BusquedaSnapshot busqueda : instantanea.abiertas()) {
            sb.append(filaBusqueda(busqueda)).append('\n');
        }
        sb.append("ENTIDAD,entidad,busquedas,expansiones,nanos,fallos,presupuesto_agotado,")
                .append("replanificaciones,canceladas,ultima_busqueda,ultimo_tipo,ultimo_tick,")
                .append("ultimo_motivo,ultima_longitud_ruta\n");
        for (EntidadSnapshot entidad : instantanea.entidades()) {
            sb.append("ENTIDAD,").append(entidad.entidad() == null ? "-" : entidad.entidad())
                    .append(',').append(entidad.busquedas())
                    .append(',').append(entidad.expansiones())
                    .append(',').append(entidad.nanos())
                    .append(',').append(entidad.fallos())
                    .append(',').append(entidad.presupuestoAgotado())
                    .append(',').append(entidad.replanificaciones())
                    .append(',').append(entidad.canceladas())
                    .append(',').append(entidad.ultimaBusqueda())
                    .append(',').append(entidad.ultimoTipo())
                    .append(',').append(entidad.ultimoTick())
                    .append(',').append(csv(entidad.ultimoMotivo()))
                    .append(',').append(entidad.ultimaLongitudRuta())
                    .append('\n');
        }
        return sb.toString();
    }

    /** Volcado explícito por consola (nunca se imprime solo). */
    public static void dump() {
        dump(System.out);
    }

    /** Volcado explícito a un stream concreto. */
    public static void dump(PrintStream salida) {
        if (salida == null) {
            return;
        }
        salida.print(dumpCsv());
    }

    // ================================================================================================
    // Agregados y utilidades internas.
    // ================================================================================================

    private static void registrar(Busqueda busqueda) {
        registros.addLast(busqueda.snapshot());
        registrosTam.incrementAndGet();
        while (registrosTam.get() > MAX_REGISTROS) {
            if (registros.pollFirst() == null) {
                registrosTam.set(0);
                return;
            }
            registrosTam.decrementAndGet();
        }
    }

    private static boolean desalojarActiva() {
        Busqueda victima = null;
        for (Busqueda busqueda : activas.values()) {
            if (victima == null || busqueda.secuencia < victima.secuencia) {
                victima = busqueda;
            }
        }
        return victima != null && activas.remove(victima.id, victima);
    }

    /** Agregado de una entidad; null se trata como "sin entidad" (ConcurrentHashMap no admite claves null). */
    private static Entidad agregadoDe(UUID entidad) {
        return entidad == null ? null : porEntidad.get(entidad);
    }

    private static Entidad entidad(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        Entidad existente = porEntidad.get(uuid);
        long toque = secuencia.incrementAndGet();
        if (existente != null) {
            existente.lastTouched = toque;
            return existente;
        }
        Entidad nueva = new Entidad(uuid, toque);
        Entidad previa = porEntidad.putIfAbsent(uuid, nueva);
        if (previa != null) {
            previa.lastTouched = toque;
            return previa;
        }
        while (porEntidad.size() > MAX_ENTIDADES) {
            if (!desalojarEntidad()) {
                break;
            }
        }
        return nueva;
    }

    private static boolean desalojarEntidad() {
        Entidad victima = null;
        for (Entidad agregado : porEntidad.values()) {
            if (victima == null || agregado.lastTouched < victima.lastTouched) {
                victima = agregado;
            }
        }
        return victima != null && porEntidad.remove(victima.entidad, victima);
    }

    private static String motivo(String motivo) {
        return motivo == null || motivo.isEmpty() ? MOTIVO_DESCONOCIDO : motivo;
    }

    private static String csv(String valor) {
        if (valor == null) {
            return MOTIVO_DESCONOCIDO;
        }
        return valor.replace(',', ';').replace('\n', ' ').replace('\r', ' ');
    }

    private static String filaBusqueda(BusquedaSnapshot busqueda) {
        int nodosPorAvance = busqueda.nodosPorAvance();
        return "BUSQUEDA," + busqueda.id()
                + "," + busqueda.tipo()
                + "," + (busqueda.entidad() == null ? "-" : busqueda.entidad())
                + "," + busqueda.tickInicio()
                + "," + busqueda.nanos()
                + "," + busqueda.expansiones()
                + "," + busqueda.presupuesto()
                + "," + busqueda.avances()
                + "," + nodosPorAvance
                + "," + busqueda.ultimoSliceExpansiones()
                + "," + busqueda.reanudada()
                + "," + busqueda.replanificacion()
                + "," + busqueda.fallida()
                + "," + busqueda.presupuestoAgotado()
                + "," + busqueda.longitudRuta()
                + "," + busqueda.estado()
                + "," + csv(busqueda.motivo());
    }

    private static int sumaInt(int actual, int delta) {
        if (delta <= 0) {
            return actual;
        }
        long suma = (long) actual + (long) delta;
        return suma >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) suma;
    }

    private static long sumaLong(long actual, long delta) {
        if (delta <= 0L) {
            return actual;
        }
        return actual > Long.MAX_VALUE - delta ? Long.MAX_VALUE : actual + delta;
    }

    private static void acumular(AtomicLong destino, long delta) {
        if (delta <= 0L) {
            return;
        }
        while (true) {
            long actual = destino.get();
            long nuevo = actual > Long.MAX_VALUE - delta ? Long.MAX_VALUE : actual + delta;
            if (destino.compareAndSet(actual, nuevo)) {
                return;
            }
        }
    }

    // ================================================================================================
    // Tipos inmutables de lectura.
    // ================================================================================================

    /** Totales agregados de toda la instrumentación. */
    public record TotalesSnapshot(long busquedas, long busquedasCompletadas, long busquedasFallidas,
                                  long canceladas, long expansiones, long nanos, long presupuestoAgotado,
                                  long replanificaciones, long reanudadas, int entidades) {
    }

    /**
     * Fotografía de una búsqueda.
     *
     * <p>{@code avances} es el número de slices/ticks medidos; {@code nodosPorAvance} = expansiones/avances (en la
     * capa incremental equivale a nodos por tick). {@code reanudada} es {@code avances > 1}. {@code fallida} solo es
     * cierta para un cierre sin éxito; una {@link Estado#CANCELADA} no cuenta como fallo.
     */
    public record BusquedaSnapshot(long id, Tipo tipo, UUID entidad, long tickInicio, long nanos,
                                   int expansiones, int presupuesto, int avances, int nodosPorAvance,
                                   int ultimoSliceExpansiones, boolean reanudada, boolean replanificacion,
                                   boolean fallida, boolean presupuestoAgotado, int longitudRuta,
                                   String motivo, Estado estado) {
    }

    /** Agregado acumulado por entidad. */
    public record EntidadSnapshot(UUID entidad, int busquedas, long expansiones, long nanos, int fallos,
                                  int presupuestoAgotado, int replanificaciones, int canceladas,
                                  long ultimaBusqueda, Tipo ultimoTipo, long ultimoTick, String ultimoMotivo,
                                  int ultimaLongitudRuta) {
    }

    /** Copia inmutable: totales, buscadas cerradas (orden de cierre), abiertas (id ascendente) y entidades. */
    public record Snapshot(TotalesSnapshot totales, List<BusquedaSnapshot> busquedas,
                           List<BusquedaSnapshot> abiertas, List<EntidadSnapshot> entidades) {
    }

    // ================================================================================================
    // Estado mutable interno (un único hilo escritor en producción: el del servidor).
    // ================================================================================================

    private static final class Busqueda {
        final long id;
        final Tipo tipo;
        final UUID entidad;
        final long tickInicio;
        final int presupuesto;
        final long secuencia;
        long nanos;
        int expansiones;
        int avances;
        int ultimoSliceExpansiones;
        boolean reanudada;
        boolean replanificacion;
        boolean exito;
        boolean presupuestoAgotado;
        int longitudRuta;
        String motivo = MOTIVO_DESCONOCIDO;
        Estado estado = Estado.ABIERTA;

        Busqueda(long id, Tipo tipo, UUID entidad, long tickInicio, int presupuesto, long secuencia) {
            this.id = id;
            this.tipo = tipo;
            this.entidad = entidad;
            this.tickInicio = tickInicio;
            this.presupuesto = presupuesto;
            this.secuencia = secuencia;
        }

        BusquedaSnapshot snapshot() {
            int nodosPorAvance = avances <= 0 ? 0 : expansiones / avances;
            return new BusquedaSnapshot(id, tipo, entidad, tickInicio, nanos, expansiones, presupuesto,
                    avances, nodosPorAvance, ultimoSliceExpansiones, reanudada, replanificacion,
                    estado == Estado.FALLIDA, presupuestoAgotado, longitudRuta, motivo, estado);
        }
    }

    private static final class Entidad {
        final UUID entidad;
        volatile long lastTouched;
        int busquedas;
        long expansiones;
        long nanos;
        int fallos;
        int presupuestoAgotado;
        int replanificaciones;
        int canceladas;
        long busquedaAbierta = SIN_BUSQUEDA;
        boolean ultimaCancelada;
        long ultimaBusqueda = SIN_BUSQUEDA;
        Tipo ultimoTipo;
        long ultimoTick = -1L;
        String ultimoMotivo = MOTIVO_DESCONOCIDO;
        int ultimaLongitudRuta;

        Entidad(UUID entidad, long lastTouched) {
            this.entidad = entidad;
            this.lastTouched = lastTouched;
        }

        EntidadSnapshot snapshot() {
            return new EntidadSnapshot(entidad, busquedas, expansiones, nanos, fallos,
                    presupuestoAgotado, replanificaciones, canceladas, ultimaBusqueda, ultimoTipo,
                    ultimoTick, ultimoMotivo, ultimaLongitudRuta);
        }
    }
}
