package com.chipoodle.devilrpg.debug;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Lógica PURA del arnés POI: comparación de tablas, clasificación de anomalías y JSON.
 * <p>
 * No importa ninguna clase de Minecraft: se puede compilar y ejecutar con {@code javac}/{@code java}
 * a secas ({@code main} corre un autotest). El arnés de servidor
 * ({@link PoiPersistenceHarness}) solo recoge datos con {@code ServerLevel}/{@code PoiManager} y delega
 * aquí toda la decisión, de forma que lo que decide PASS/FAIL está probado sin lanzar el juego.
 *
 * <h2>Vocabulario</h2>
 * <ul>
 *   <li><b>Celda</b>: un punto de interés real con su tipo y sus tickets libres ({@code free_tickets}).</li>
 *   <li><b>Dueño</b>: un aldeano y las celdas que tiene en el cerebro ({@code JOB_SITE},
 *       {@code POTENTIAL_JOB_SITE}); {@code 0} = no tiene esa memoria.</li>
 *   <li><b>Huérfano</b>: memoria {@code JOB_SITE} que apunta a una celda que ya no es un POI.</li>
 *   <li><b>Ticket perdido</b>: POI con {@code free_tickets == 0} y ningún aldeano con esa celda en el
 *       cerebro (ni de puesto ni potencial).</li>
 * </ul>
 *
 * <p>Los códigos de hallazgo son estables y se citan en el informe:
 * {@code JOB_SITE_DUPLICADO}, {@code JOB_SITE_HUERFANO}, {@code TICKET_SIN_DUENO},
 * {@code TICKET_LIBRE_CON_DUENO}, {@code POTENTIAL_NO_PROMOVIDO}, {@code COBERTURA_INCOMPLETA},
 * {@code TICKET_LIBERADO_CON_DUENO_NO_CARGADO}, {@code OWNERSHIP_CAMBIADO}, {@code OWNERSHIP_PERDIDO}.
 */
public final class PoiPersistenceReport {

    public static final String JOB_SITE_DUPLICADO = "JOB_SITE_DUPLICADO";
    public static final String JOB_SITE_HUERFANO = "JOB_SITE_HUERFANO";
    public static final String TICKET_SIN_DUENO = "TICKET_SIN_DUENO";
    public static final String TICKET_LIBRE_CON_DUENO = "TICKET_LIBRE_CON_DUENO";
    public static final String POTENTIAL_NO_PROMOVIDO = "POTENTIAL_NO_PROMOVIDO";
    public static final String POTENTIAL_AJENO = "POTENTIAL_AJENO";
    public static final String COBERTURA_INCOMPLETA = "COBERTURA_INCOMPLETA";
    public static final String TICKET_LIBERADO_CON_DUENO_NO_CARGADO = "TICKET_LIBERADO_CON_DUENO_NO_CARGADO";
    public static final String OWNERSHIP_CAMBIADO = "OWNERSHIP_CAMBIADO";
    public static final String OWNERSHIP_PERDIDO = "OWNERSHIP_PERDIDO";
    public static final String OWNERSHIP_NUEVO = "OWNERSHIP_NUEVO";

    private PoiPersistenceReport() {
    }

    // ------------------------------------------------------------------ modelo

    /** Un POI real: celda, tipo, tickets libres y tickets máximos ({@code 0} = desconocido). */
    public record Celda(long pos, String tipo, int libres, int max) {
        public boolean esPuestoDeTrabajo() {
            return tipo != null && !tipo.startsWith("minecraft:home") && max > 0 && !tipo.equals("minecraft:meeting");
        }
    }

    /**
     * Un aldeano y sus memorias de POI.
     *
     * @param uuid      UUID en texto
     * @param profesion profesión (texto del registro, p.ej. {@code minecraft:farmer})
     * @param puesto    {@code JOB_SITE} como {@code BlockPos.asLong}; {@code 0} = sin memoria
     * @param potencial {@code POTENTIAL_JOB_SITE}; {@code 0} = sin memoria
     * @param posicion  posición del aldeano como texto corto ({@code x, y, z})
     * @param cargado   si la entidad estaba cargada al medir
     */
    public record Duenio(String uuid, String profesion, long puesto, long potencial, String posicion,
                         boolean cargado) {
    }

    /** Anomalía medida, con código estable y detalle legible. */
    public record Hallazgo(String codigo, String detalle) {
    }

    /** Diferencia de propiedad entre dos tablas {@code poi_long -> uuid}. */
    public record Comparacion(List<String> estables, List<String> cambiados, List<String> perdidos,
                              List<String> nuevos) {
        public boolean iguales() {
            return cambiados.isEmpty() && perdidos.isEmpty() && nuevos.isEmpty();
        }
    }

    // ------------------------------------------------------------------ clasificación

    /** POIs con más de un aldeano con la MISMA celda en {@code JOB_SITE}. */
    public static List<Hallazgo> duplicados(final List<Duenio> duenios) {
        Map<Long, List<Duenio>> porPoi = new TreeMap<>();
        for (Duenio d : duenios) {
            if (d.puesto() > 0L) {
                porPoi.computeIfAbsent(d.puesto(), k -> new ArrayList<>()).add(d);
            }
        }
        List<Hallazgo> out = new ArrayList<>();
        for (Map.Entry<Long, List<Duenio>> e : porPoi.entrySet()) {
            if (e.getValue().size() > 1) {
                List<String> uuids = new ArrayList<>();
                for (Duenio d : e.getValue()) {
                    uuids.add(d.uuid() + "(" + d.profesion() + ")");
                }
                out.add(new Hallazgo(JOB_SITE_DUPLICADO, "poi=" + pos(e.getKey()) + " duenios=" + uuids));
            }
        }
        return out;
    }

    /** Memorias {@code JOB_SITE} que apuntan a una celda que ya no es un POI. */
    public static List<Hallazgo> huerfanos(final List<Duenio> duenios, final Set<Long> celdas) {
        List<Hallazgo> out = new ArrayList<>();
        for (Duenio d : duenios) {
            if (d.puesto() > 0L && !celdas.contains(d.puesto())) {
                out.add(new Hallazgo(JOB_SITE_HUERFANO,
                        "poi=" + pos(d.puesto()) + " uuid=" + d.uuid() + " prof=" + d.profesion()));
            }
        }
        return out;
    }

    /**
     * POIs de trabajo con el ticket cogido ({@code libres == 0}) y ningún aldeano con esa celda en el
     * cerebro (ni {@code JOB_SITE} ni {@code POTENTIAL_JOB_SITE}): el ticket perdido de I23.
     */
    public static List<Hallazgo> ticketsSinDueno(final List<Celda> celdas, final List<Duenio> duenios) {
        Set<Long> reclamadas = new LinkedHashSet<>();
        for (Duenio d : duenios) {
            if (d.puesto() > 0L) {
                reclamadas.add(d.puesto());
            }
            if (d.potencial() > 0L) {
                reclamadas.add(d.potencial());
            }
        }
        List<Hallazgo> out = new ArrayList<>();
        for (Celda c : celdas) {
            if (c.esPuestoDeTrabajo() && c.libres() == 0 && !reclamadas.contains(c.pos())) {
                out.add(new Hallazgo(TICKET_SIN_DUENO,
                        "poi=" + pos(c.pos()) + " tipo=" + c.tipo() + " libres=0"));
            }
        }
        return out;
    }

    /** POIs con tickets libres pero un aldeano con {@code JOB_SITE} en esa celda (estado incoherente). */
    public static List<Hallazgo> ticketsLibresConDueno(final List<Celda> celdas, final List<Duenio> duenios) {
        Map<Long, Celda> porPos = new LinkedHashMap<>();
        for (Celda c : celdas) {
            porPos.put(c.pos(), c);
        }
        List<Hallazgo> out = new ArrayList<>();
        Set<Long> yaAvisados = new LinkedHashSet<>();
        for (Duenio d : duenios) {
            if (d.puesto() <= 0L) {
                continue;
            }
            Celda c = porPos.get(d.puesto());
            if (c != null && c.libres() > 0 && yaAvisados.add(d.puesto())) {
                out.add(new Hallazgo(TICKET_LIBRE_CON_DUENO,
                        "poi=" + pos(d.puesto()) + " tipo=" + c.tipo() + " libres=" + c.libres()
                                + " uuid=" + d.uuid()));
            }
        }
        return out;
    }

    /**
     * Aldeanos con {@code POTENTIAL_JOB_SITE} en un POI cuyo ticket está cogido, sin {@code JOB_SITE} y
     * <b>sin otro dueño</b>: la promoción que el jugador espera (encontró el puesto y el ticket ya es suyo).
     * Si otro aldeano tiene ese POI como {@code JOB_SITE}, el POTENTIAL es {@link #potentialAjeno}.
     */
    public static List<Hallazgo> potencialNoPromovido(final List<Celda> celdas, final List<Duenio> duenios) {
        Map<Long, Celda> porPos = new LinkedHashMap<>();
        for (Celda c : celdas) {
            porPos.put(c.pos(), c);
        }
        Map<Long, String> duenioDelPoi = new LinkedHashMap<>();
        for (Duenio d : duenios) {
            if (d.puesto() > 0L) {
                duenioDelPoi.putIfAbsent(d.puesto(), d.uuid());
            }
        }
        List<Hallazgo> out = new ArrayList<>();
        for (Duenio d : duenios) {
            if (d.puesto() > 0L || d.potencial() <= 0L) {
                continue;
            }
            Celda c = porPos.get(d.potencial());
            if (c == null) {
                continue;
            }
            String otro = duenioDelPoi.get(d.potencial());
            if (otro != null && !otro.equals(d.uuid())) {
                continue; // no es promoción pendiente: la celda ya es de otro (ver potentialAjeno)
            }
            // Solo es "promoción pendiente" si el ticket de esa celda ya está cogido: si está libre, el
            // reparto normal puede dársela con `take` y no hay nada que promocionar.
            if (c.libres() == 0) {
                out.add(new Hallazgo(POTENTIAL_NO_PROMOVIDO,
                        "poi=" + pos(d.potencial()) + " tipo=" + c.tipo() + " uuid=" + d.uuid()
                                + " prof=" + d.profesion()));
            }
        }
        return out;
    }

    /** {@code POTENTIAL_JOB_SITE} que apunta a una celda que ya tiene {@code JOB_SITE} de OTRO aldeano. */
    public static List<Hallazgo> potentialAjeno(final List<Duenio> duenios) {
        Map<Long, String> duenioDelPoi = new LinkedHashMap<>();
        for (Duenio d : duenios) {
            if (d.puesto() > 0L) {
                duenioDelPoi.putIfAbsent(d.puesto(), d.uuid());
            }
        }
        List<Hallazgo> out = new ArrayList<>();
        for (Duenio d : duenios) {
            if (d.potencial() <= 0L) {
                continue;
            }
            String otro = duenioDelPoi.get(d.potencial());
            if (otro != null && !otro.equals(d.uuid())) {
                out.add(new Hallazgo(POTENTIAL_AJENO,
                        "poi=" + pos(d.potencial()) + " uuid=" + d.uuid() + " prof=" + d.profesion()
                                + " duenio=" + otro));
            }
        }
        return out;
    }

    /**
     * Cobertura de puestos por oficio: para cada oficio del pueblo, cuántos de sus titulares tienen
     * {@code JOB_SITE}. {@code cupo} es el número de plazas por oficio (de {@code VillageGenerator}).
     */
    public static List<Hallazgo> cobertura(final List<Duenio> duenios, final Map<String, Integer> cupo) {
        Map<String, Integer> titulares = new TreeMap<>();
        Map<String, Integer> conPuesto = new TreeMap<>();
        for (Duenio d : duenios) {
            if (d.profesion() == null || !cupo.containsKey(d.profesion())) {
                continue;
            }
            titulares.merge(d.profesion(), 1, Integer::sum);
            if (d.puesto() > 0L) {
                conPuesto.merge(d.profesion(), 1, Integer::sum);
            }
        }
        List<Hallazgo> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : new TreeMap<>(cupo).entrySet()) {
            int esperados = e.getValue();
            int conSite = conPuesto.getOrDefault(e.getKey(), 0);
            if (conSite < esperados) {
                out.add(new Hallazgo(COBERTURA_INCOMPLETA,
                        e.getKey() + " con_JOB_SITE=" + conSite + "/" + esperados
                                + " titulares_cargados=" + titulares.getOrDefault(e.getKey(), 0)));
            }
        }
        return out;
    }

    /**
     * Compara los tickets libres antes/después de un mantenimiento y marca los POIs que pasaron de
     * {@code libres == 0} a {@code libres > 0} cuando su dueño persistido NO estaba cargado antes de la
     * llamada: liberar el ticket de un aldeano que aún no ha cargado es el fallo de carga parcial
     * (el dueño real aparece después con la misma memoria y otro puede haber reclamado la celda).
     *
     * @param antes            celdas antes del mantenimiento
     * @param despues          celdas después
     * @param duenioPersistido {@code poi_long -> uuid} que se guardó en la fase anterior
     * @param cargadosAntes    UUID cargados ANTES del mantenimiento
     */
    public static List<Hallazgo> ticketsLiberadosConDuenoNoCargado(
            final List<Celda> antes, final List<Celda> despues,
            final Map<Long, String> duenioPersistido, final Set<String> cargadosAntes) {
        Map<Long, Integer> libresAntes = new LinkedHashMap<>();
        for (Celda c : antes) {
            libresAntes.put(c.pos(), c.libres());
        }
        List<Hallazgo> out = new ArrayList<>();
        for (Celda c : despues) {
            Integer previos = libresAntes.get(c.pos());
            if (previos == null || previos != 0 || c.libres() <= 0) {
                continue;
            }
            String duenio = duenioPersistido.get(c.pos());
            if (duenio == null) {
                continue; // no había dueño persistido: liberar un ticket huérfano es legítimo
            }
            if (!cargadosAntes.contains(duenio)) {
                out.add(new Hallazgo(TICKET_LIBERADO_CON_DUENO_NO_CARGADO,
                        "poi=" + pos(c.pos()) + " tipo=" + c.tipo() + " duenio_persistido=" + duenio
                                + " cargado_antes=false"));
            }
        }
        return out;
    }

    /** Diferencia entre dos mapas de propiedad ({@code poi_long -> uuid}), en orden estable. */
    public static Comparacion comparar(final Map<Long, String> antes, final Map<Long, String> despues) {
        List<String> estables = new ArrayList<>();
        List<String> cambiados = new ArrayList<>();
        List<String> perdidos = new ArrayList<>();
        List<String> nuevos = new ArrayList<>();
        for (Map.Entry<Long, String> e : new TreeMap<>(antes).entrySet()) {
            String ahora = despues.get(e.getKey());
            if (ahora == null) {
                perdidos.add("poi=" + pos(e.getKey()) + " uuid=" + e.getValue());
            } else if (!ahora.equals(e.getValue())) {
                cambiados.add("poi=" + pos(e.getKey()) + " antes=" + e.getValue() + " ahora=" + ahora);
            } else {
                estables.add("poi=" + pos(e.getKey()) + " uuid=" + e.getValue());
            }
        }
        for (Long poi : new TreeSet<>(despues.keySet())) {
            if (!antes.containsKey(poi)) {
                nuevos.add("poi=" + pos(poi) + " uuid=" + despues.get(poi));
            }
        }
        return new Comparacion(estables, cambiados, perdidos, nuevos);
    }

    /** Convierte hallazgos de comparación a la lista de códigos esperada por el JSON. */
    public static List<Hallazgo> hallazgosDeComparacion(final Comparacion c, final boolean esRoundTrip) {
        return hallazgosDeComparacion(c, esRoundTrip, true);
    }

    /**
     * Igual que {@link #hallazgosDeComparacion(Comparacion, boolean)} pero permite decidir si las
     * entradas NUEVAS se reportan. En la foto "al cargar" una entrada nueva puede ser una adquisición
     * legítima de vanilla en los primeros ticks (p.ej. un POTENTIAL sobre una estación libre); eso no
     * es un fallo de persistencia y se mide aparte en el estado final.
     */
    public static List<Hallazgo> hallazgosDeComparacion(final Comparacion c, final boolean esRoundTrip,
                                                       final boolean incluirNuevos) {
        List<Hallazgo> out = new ArrayList<>();
        if (!c.perdidos().isEmpty()) {
            out.add(new Hallazgo(esRoundTrip ? "OWNERSHIP_NO_PERSISTIDO" : OWNERSHIP_PERDIDO,
                    String.join("; ", c.perdidos())));
        }
        if (!c.cambiados().isEmpty()) {
            out.add(new Hallazgo(esRoundTrip ? "OWNERSHIP_CAMBIADO_EN_CARGA" : OWNERSHIP_CAMBIADO,
                    String.join("; ", c.cambiados())));
        }
        if (incluirNuevos && !c.nuevos().isEmpty()) {
            out.add(new Hallazgo(esRoundTrip ? "OWNERSHIP_EXTRA_EN_CARGA" : OWNERSHIP_NUEVO,
                    String.join("; ", c.nuevos())));
        }
        return out;
    }

    // ------------------------------------------------------------------ formato

    /** {@code BlockPos.asLong} -> "x, y, z" legible (sin depender de Minecraft). */
    public static String pos(final long packed) {
        int x = (int) (packed >> 38);
        int y = (int) (packed << 52 >> 52);
        int z = (int) (packed << 26 >> 38);
        return x + ", " + y + ", " + z;
    }

    // ------------------------------------------------------------------ JSON

    /** Constructor JSON mínimo (sin dependencias): objetos con campos en orden de inserción y arrays. */
    public static final class Objeto {
        private final LinkedHashMap<String, String> campos = new LinkedHashMap<>();

        public Objeto texto(final String clave, final String valor) {
            campos.put(clave, valor == null ? "null" : comillas(valor));
            return this;
        }

        public Objeto numero(final String clave, final long valor) {
            campos.put(clave, Long.toString(valor));
            return this;
        }

        public Objeto decimal(final String clave, final double valor) {
            campos.put(clave, Double.toString(valor));
            return this;
        }

        public Objeto booleano(final String clave, final boolean valor) {
            campos.put(clave, Boolean.toString(valor));
            return this;
        }

        /** Inserta un valor YA renderizado (objeto/array/null). */
        public Objeto crudo(final String clave, final String json) {
            campos.put(clave, json);
            return this;
        }

        public String json() {
            StringBuilder sb = new StringBuilder("{");
            boolean primero = true;
            for (Map.Entry<String, String> e : campos.entrySet()) {
                if (!primero) {
                    sb.append(',');
                }
                primero = false;
                sb.append(comillas(e.getKey())).append(':').append(e.getValue());
            }
            return sb.append('}').toString();
        }
    }

    /** Array JSON a partir de valores ya renderizados. */
    public static String arreglo(final Collection<String> valores) {
        StringBuilder sb = new StringBuilder("[");
        boolean primero = true;
        for (String v : valores) {
            if (!primero) {
                sb.append(',');
            }
            primero = false;
            sb.append(v);
        }
        return sb.append(']').toString();
    }

    /** Array JSON de textos. */
    public static String arregloTexto(final Collection<String> valores) {
        List<String> out = new ArrayList<>();
        for (String v : valores) {
            out.add(comillas(v));
        }
        return arreglo(out);
    }

    /** Array JSON de hallazgos ({@code {codigo, detalle}}). */
    public static String hallazgosJson(final List<Hallazgo> hallazgos) {
        List<String> out = new ArrayList<>();
        for (Hallazgo h : hallazgos) {
            out.add(new Objeto().texto("codigo", h.codigo()).texto("detalle", h.detalle()).json());
        }
        return arreglo(out);
    }

    /** Códigos de una lista de hallazgos, sin repetir y en orden estable. */
    public static List<String> codigos(final List<Hallazgo> hallazgos) {
        Set<String> out = new TreeSet<>();
        for (Hallazgo h : hallazgos) {
            out.add(h.codigo());
        }
        return new ArrayList<>(out);
    }

    public static String comillas(final String s) {
        StringBuilder sb = new StringBuilder("\"");
        if (s != null) {
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
                            sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    // ------------------------------------------------------------------ huella

    /** SHA-256 en hexadecimal de un texto UTF-8 ({@code ""} si el algoritmo no está). */
    public static String sha256(final String texto) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(texto.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "sin_sha256";
        }
    }

    // ------------------------------------------------------------------ autotest

    /**
     * Autotest de la lógica. No necesita Minecraft ni el mundo: se ejecuta con {@code java} a secas.
     * Devuelve el número de fallos (0 = PASS).
     */
    public static int autotest() {
        List<String> fallos = new ArrayList<>();

        List<Duenio> duenios = new ArrayList<>();
        duenios.add(new Duenio("u1", "minecraft:farmer", posLong(10, 64, 10), 0L, "0, 64, 0", true));
        duenios.add(new Duenio("u2", "minecraft:farmer", posLong(10, 64, 10), 0L, "1, 64, 0", true));
        duenios.add(new Duenio("u3", "minecraft:cleric", posLong(20, 64, 20), posLong(21, 64, 20), "2, 64, 0", true));
        duenios.add(new Duenio("u4", "minecraft:mason", posLong(99, 64, 99), 0L, "3, 64, 0", true));

        List<Hallazgo> dup = duplicados(duenios);
        esperar(fallos, "duplicados", dup.size() == 1 && JOB_SITE_DUPLICADO.equals(dup.get(0).codigo()));

        Set<Long> celdas = new LinkedHashSet<>();
        celdas.add(posLong(10, 64, 10));
        celdas.add(posLong(20, 64, 20));
        celdas.add(posLong(21, 64, 20));
        List<Hallazgo> huerf = huerfanos(duenios, celdas);
        esperar(fallos, "huerfanos", huerf.size() == 1 && JOB_SITE_HUERFANO.equals(huerf.get(0).codigo())
                && huerf.get(0).detalle().contains("u4"));

        List<Celda> pois = new ArrayList<>();
        pois.add(new Celda(posLong(10, 64, 10), "minecraft:farmer", 0, 1));
        pois.add(new Celda(posLong(20, 64, 20), "minecraft:cleric", 0, 1));
        pois.add(new Celda(posLong(21, 64, 20), "minecraft:farmer", 0, 1));
        pois.add(new Celda(posLong(30, 64, 30), "minecraft:shepherd", 0, 1));
        pois.add(new Celda(posLong(40, 64, 40), "minecraft:butcher", 1, 1));
        List<Hallazgo> sinDueno = ticketsSinDueno(pois, duenios);
        // 30,64,30 (shepherd) no lo tiene nadie; 40,64,40 está libre y sin dueño (legítimo).
        esperar(fallos, "tickets_sin_dueno", sinDueno.size() == 1
                && sinDueno.get(0).detalle().contains("30, 64, 30"));

        List<Celda> libres = new ArrayList<>();
        libres.add(new Celda(posLong(10, 64, 10), "minecraft:farmer", 1, 1));
        List<Hallazgo> libreConDueno = ticketsLibresConDueno(libres, duenios);
        esperar(fallos, "ticket_libre_con_dueno", libreConDueno.size() == 1
                && TICKET_LIBRE_CON_DUENO.equals(libreConDueno.get(0).codigo()));

        List<Celda> pot = new ArrayList<>();
        pot.add(new Celda(posLong(21, 64, 20), "minecraft:farmer", 0, 1));
        List<Duenio> soloPotencial = new ArrayList<>();
        soloPotencial.add(new Duenio("u5", "minecraft:farmer", 0L, posLong(21, 64, 20), "4, 64, 0", true));
        List<Hallazgo> noPromovido = potencialNoPromovido(pot, soloPotencial);
        esperar(fallos, "potential_no_promovido", noPromovido.size() == 1
                && POTENTIAL_NO_PROMOVIDO.equals(noPromovido.get(0).codigo())
                && noPromovido.get(0).detalle().contains("u5"));

        List<Duenio> ajenos = new ArrayList<>();
        ajenos.add(new Duenio("uA", "minecraft:farmer", posLong(21, 64, 20), 0L, "0, 64, 0", true));
        ajenos.add(new Duenio("uB", "minecraft:farmer", 0L, posLong(21, 64, 20), "1, 64, 0", true));
        List<Hallazgo> potAjeno = potentialAjeno(ajenos);
        esperar(fallos, "potential_ajeno", potAjeno.size() == 1
                && POTENTIAL_AJENO.equals(potAjeno.get(0).codigo())
                && potAjeno.get(0).detalle().contains("uB"));
        esperar(fallos, "potential_ajeno_no_es_promocion", potencialNoPromovido(pot, ajenos).isEmpty());

        Map<String, Integer> cupo = new LinkedHashMap<>();
        cupo.put("minecraft:farmer", 2);
        cupo.put("minecraft:cleric", 1);
        cupo.put("minecraft:mason", 1);
        List<Duenio> dueniosCobertura = new ArrayList<>();
        dueniosCobertura.add(new Duenio("c1", "minecraft:farmer", posLong(10, 64, 10), 0L, "0, 64, 0", true));
        dueniosCobertura.add(new Duenio("c2", "minecraft:farmer", 0L, 0L, "1, 64, 0", true));
        List<Hallazgo> cob = cobertura(dueniosCobertura, cupo);
        boolean cubreFarmer = false;
        boolean cubreMason = false;
        for (Hallazgo h : cob) {
            cubreFarmer |= h.detalle().contains("minecraft:farmer con_JOB_SITE=1/2");
            cubreMason |= h.detalle().contains("minecraft:mason con_JOB_SITE=0/1");
        }
        esperar(fallos, "cobertura", cob.size() == 3 && cubreFarmer && cubreMason);

        Map<Long, String> antes = new LinkedHashMap<>();
        antes.put(1L, "u1");
        antes.put(2L, "u2");
        Map<Long, String> despues = new LinkedHashMap<>();
        despues.put(1L, "u1");
        despues.put(3L, "u3");
        Comparacion comp = comparar(antes, despues);
        esperar(fallos, "comparar", comp.estables().size() == 1 && comp.perdidos().size() == 1
                && comp.nuevos().size() == 1 && comp.cambiados().isEmpty());
        Map<Long, String> despues2 = new LinkedHashMap<>();
        despues2.put(1L, "u1");
        despues2.put(2L, "u9");
        Comparacion comp2 = comparar(antes, despues2);
        esperar(fallos, "comparar_cambiado", comp2.cambiados().size() == 1);

        List<Celda> antesTickets = new ArrayList<>();
        antesTickets.add(new Celda(posLong(50, 64, 50), "minecraft:butcher", 0, 1));
        List<Celda> despuesTickets = new ArrayList<>();
        despuesTickets.add(new Celda(posLong(50, 64, 50), "minecraft:butcher", 1, 1));
        Map<Long, String> duenio = new LinkedHashMap<>();
        duenio.put(posLong(50, 64, 50), "uNoCargado");
        Set<String> cargados = new LinkedHashSet<>();
        cargados.add("u1");
        List<Hallazgo> liberados = ticketsLiberadosConDuenoNoCargado(
                antesTickets, despuesTickets, duenio, cargados);
        esperar(fallos, "ticket_liberado_carga_parcial", liberados.size() == 1
                && TICKET_LIBERADO_CON_DUENO_NO_CARGADO.equals(liberados.get(0).codigo()));
        cargados.add("uNoCargado");
        esperar(fallos, "ticket_liberado_duenio_cargado",
                ticketsLiberadosConDuenoNoCargado(antesTickets, despuesTickets, duenio, cargados).isEmpty());

        String esperadoJson = "{\"a\":\"x\\\"y\\\\z\\n\",\"b\":2,\"c\":true}";
        String json = new Objeto().texto("a", "x\"y\\z\n").numero("b", 2).booleano("c", true).json();
        esperar(fallos, "json_escape", esperadoJson.equals(json));

        esperar(fallos, "sha256", "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
                .equals(sha256("abc")));

        esperar(fallos, "pos", "10, 64, 10".equals(pos(posLong(10, 64, 10))));

        System.out.println("PoiPersistenceReport.autotest: " + (fallos.isEmpty() ? "PASS" : "FAIL")
                + " casos=15 fallos=" + fallos.size());
        for (String f : fallos) {
            System.out.println("  FALLO: " + f);
        }
        System.out.println("  codigos=" + codigos(dupConTodos(dup, huerf, sinDueno, libreConDueno, noPromovido,
                potAjeno, cob, liberados)));
        return fallos.size();
    }

    @SafeVarargs
    private static List<Hallazgo> dupConTodos(final List<Hallazgo>... listas) {
        List<Hallazgo> out = new ArrayList<>();
        for (List<Hallazgo> l : listas) {
            out.addAll(l);
        }
        return out;
    }

    private static void esperar(final List<String> fallos, final String nombre, final boolean condicion) {
        if (!condicion) {
            fallos.add(nombre);
        }
    }

    private static long posLong(final int x, final int y, final int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public static void main(final String[] args) {
        int fallos = autotest();
        if (fallos != 0) {
            System.exit(1);
        }
    }
}
