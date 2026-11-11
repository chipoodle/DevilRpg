package com.chipoodle.devilrpg.world;

/** A2 REMEDIACIÓN 2 · prueba pura del cupo compartido de generación por nivel y tick. */
public final class GenerationBudgetTest {

    private static int fallos;

    public static void main(String[] args) {
        GenerationBudget budget = new GenerationBudget();
        Object overworld = new Object();
        Object nether = new Object();

        comprobar("primer cupo del nivel", budget.tryClaim(overworld, 100L));
        comprobar("segundo cupo del mismo nivel/tick denegado", !budget.tryClaim(overworld, 100L));
        comprobar("otro nivel puede reclamar el mismo tick", budget.tryClaim(nether, 100L));
        comprobar("el mismo nivel puede reclamar otro tick", budget.tryClaim(overworld, 101L));
        comprobar("clave nula denegada", !budget.tryClaim(null, 101L));

        budget.clear();
        comprobar("clear libera el cupo", budget.tryClaim(overworld, 101L));

        if (fallos > 0) {
            System.out.println("GENERATION_BUDGET_TEST: FAIL (" + fallos + " fallos)");
            System.exit(1);
        }
        System.out.println("GENERATION_BUDGET_TEST: OK (todas las aserciones verdes)");
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
