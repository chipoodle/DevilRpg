package com.chipoodle.devilrpg.entity.nav;

/** A2 REMEDIACIÓN 2 · prueba de regresión: el motivo terminal no crece al repetirse. */
public final class NavTerminalDiagnosticsTest {

    private static int fallos;

    public static void main(String[] args) {
        String base = "presupuesto agotado sin progreso (6/6)";
        String una = NavTerminalDiagnostics.appendOnce(base, " (no rearmable sin progreso)");
        String dos = NavTerminalDiagnostics.appendOnce(una, " (no rearmable sin progreso)");
        String tres = NavTerminalDiagnostics.appendOnce(dos, " (no rearmable sin progreso)");
        comprobar("el sufijo se añade una vez", una.endsWith("(no rearmable sin progreso)"));
        comprobar("la segunda llamada es estable", una.equals(dos));
        comprobar("la tercera llamada es estable", dos.equals(tres));
        comprobar("el tamaño no crece", una.length() == dos.length() && dos.length() == tres.length());

        comprobar("keepFirst conserva el primero", "primero".equals(
                NavTerminalDiagnostics.keepFirst("primero", "segundo")));
        comprobar("keepFirst usa el nuevo si no habia", "segundo".equals(
                NavTerminalDiagnostics.keepFirst("-", "segundo")));

        if (fallos > 0) {
            System.out.println("NAV_TERMINAL_DIAG_TEST: FAIL (" + fallos + " fallos)");
            System.exit(1);
        }
        System.out.println("NAV_TERMINAL_DIAG_TEST: OK (todas las aserciones verdes)");
    }

    private static void comprobar(String nombre, boolean ok) {
        if (!ok) {
            fallos++;
            System.out.println("  FALLO: " + nombre);
        } else {
            System.out.println("  ok: " + nombre);
        }
    }
}
