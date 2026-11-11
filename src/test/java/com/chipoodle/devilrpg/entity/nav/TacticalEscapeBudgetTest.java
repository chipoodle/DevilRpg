package com.chipoodle.devilrpg.entity.nav;

/** A2 REMEDIACIÓN 2 · prueba pura del presupuesto finito de escapes de agua. */
public final class TacticalEscapeBudgetTest {

    private static int fallos;

    public static void main(String[] args) {
        TacticalEscapeBudget budget = new TacticalEscapeBudget(4);
        comprobar("primer intento", budget.intentar() && budget.intentos() == 1);
        comprobar("segundo intento", budget.intentar() && budget.intentos() == 2);
        comprobar("tercer intento", budget.intentar() && budget.intentos() == 3);
        comprobar("cuarto intento", budget.intentar() && budget.intentos() == 4);
        comprobar("quinto intento agotado", !budget.intentar());
        comprobar("agotado", budget.agotado());
        comprobar("no crece por seguir intentando", budget.intentos() == 4);

        budget.reiniciar();
        comprobar("reinicio de episodio", !budget.agotado() && budget.intentos() == 0 && budget.intentar());

        TacticalEscapeBudget minimo = new TacticalEscapeBudget(0);
        comprobar("tope mínimo al menos 1", minimo.maxIntentos() == 1 && minimo.intentar() && !minimo.intentar());

        if (fallos > 0) {
            System.out.println("TACTICAL_ESCAPE_TEST: FAIL (" + fallos + " fallos)");
            System.exit(1);
        }
        System.out.println("TACTICAL_ESCAPE_TEST: OK (todas las aserciones verdes)");
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
