package com.chipoodle.devilrpg.entity.nav;

/**
 * A2 Edit 11(b) · PUENTE de diagnostico entre la navegacion de PRODUCCION y el artefacto de traza del arnes.
 *
 * <p>Reglas de diseno (criticas para no alterar comportamiento):
 * <ul>
 *   <li><b>Apagado por defecto</b>: {@code -Ddevilrpg.a2n.traceNav=true}. Con la bandera apagada,
 *       {@link #on()} y {@link #ev(String, String)} son una unica comparacion de booleano.</li>
 *   <li><b>Sin E/S de ficheros aqui</b>: produccion solo publica lineas a un {@code Consumer} que instala
 *       el arnes. Si no hay consumidor, no pasa nada.</li>
 *   <li><b>Sin objetos de Minecraft en inicializadores estaticos</b>: solo un booleano y una referencia.</li>
 *   <li><b>Nunca propaga excepciones</b>: cualquier fallo del consumidor se traga para no cambiar el juego.</li>
 * </ul>
 *
 * <p>Uso en produccion: {@code if (NavTrace.on()) { NavTrace.ev("PLAN_DONE", "ok=" + ok + " nodos=" + n); }}
 * — el coste cuando esta apagado es una lectura de constante.
 */
public final class NavTrace {

    /** Bandera delgada: misma propiedad que usa el arnes para su traza por tick. */
    private static final boolean ON = Boolean.getBoolean("devilrpg.a2n.traceNav");

    /** Consumidor instalado por el arnes (puede ser null). */
    private static volatile java.util.function.Consumer<String> sink;

    private NavTrace() {
    }

    /** true solo si la propiedad esta encendida. */
    public static boolean on() {
        return ON;
    }

    /** Instala (o retira, con null) el consumidor de eventos. Solo lo llama el arnes. */
    public static void sink(final java.util.function.Consumer<String> consumer) {
        sink = consumer;
    }

    /**
     * Publica un evento estructurado: {@code TIPO campo=valor ...}. No hace nada si esta apagado,
     * si no hay consumidor, o si el consumidor falla.
     */
    public static void ev(final String tipo, final String campos) {
        if (!ON) {
            return;
        }
        final java.util.function.Consumer<String> s = sink;
        if (s == null) {
            return;
        }
        try {
            s.accept(tipo + " " + campos);
        } catch (RuntimeException ignored) {
            // Diagnostico nunca debe romper produccion.
        }
    }
}
