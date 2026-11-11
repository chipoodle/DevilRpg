package com.chipoodle.devilrpg.entity.nav;

/**
 * A2 REMEDIACIÓN 2 · <b>Diagnóstico terminal estable</b>.
 *
 * <p>Un fallo definitivo no puede añadir texto en cada tick: el motivo debe quedar fijado una sola vez.
 * Esta clase pura concentra la regla para poder probarla sin servidor.
 */
public final class NavTerminalDiagnostics {

    private NavTerminalDiagnostics() {
    }

    /** Añade el sufijo solo si no estaba ya; devuelve el mismo texto si ya se añadió. */
    public static String appendOnce(final String actual, final String sufijo) {
        if (sufijo == null || sufijo.isEmpty()) {
            return actual == null ? "" : actual;
        }
        String base = actual == null ? "" : actual;
        return base.contains(sufijo) ? base : base + sufijo;
    }

    /** Fija el motivo terminal una sola vez: si ya había uno, lo conserva. */
    public static String keepFirst(final String actual, final String nuevo) {
        if (actual != null && !actual.isEmpty() && !"-".equals(actual)) {
            return actual;
        }
        return nuevo == null ? "-" : nuevo;
    }
}
