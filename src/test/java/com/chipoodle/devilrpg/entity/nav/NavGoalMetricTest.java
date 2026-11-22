package com.chipoodle.devilrpg.entity.nav;

/** Pruebas puras de la metrica global 3D y del frente parcial de A2. */
public final class NavGoalMetricTest {

    private static int fallos;

    public static void main(String[] args) {
        double radioSqr = 2.25D;

        double dentro = NavGoalMetric.heuristic(1, 70, 1, 0, 71, 0, radioSqr, 1.0D, 2, 3);
        comprobarCerca("objetivo dentro de tolerancia 3D", 0.0D, dentro);

        double subir = NavGoalMetric.heuristic(0, 38, 0, 0, 71, 0, radioSqr, 1.0D, 2, 3);
        comprobarCerca("cota vertical al subir usa maximo de 2", 16.0D, subir);

        double bajar = NavGoalMetric.heuristic(0, 96, 0, 0, 71, 0, radioSqr, 1.0D, 2, 3);
        comprobarCerca("cota vertical al bajar usa maximo seguro de 3", 8.0D, bajar);

        double horizontal = NavGoalMetric.heuristic(5, 71, 0, 0, 71, 0, radioSqr, 1.0D, 2, 3);
        comprobarCerca("cota horizontal descuenta radio de llegada", 3.5D, horizontal);

        double bajoMeta = NavGoalMetric.frontDistanceSquared(0, 38, 0, 0, 71, 0, radioSqr, 1.0D);
        double fueraXZAlturaCorrecta = NavGoalMetric.frontDistanceSquared(5, 71, 0, 0, 71, 0,
                radioSqr, 1.0D);
        comprobar("frente parcial prefiere altura correcta frente a XZ perfecto 33 bloques abajo",
                fueraXZAlturaCorrecta < bajoMeta);

        double bajoObjetivo = NavGoalMetric.frontDistanceSquared(725, 42, 533, 725, 71, 533,
                radioSqr, 1.0D);
        double cercaEn3D = NavGoalMetric.frontDistanceSquared(730, 70, 533, 725, 71, 533,
                radioSqr, 1.0D);
        comprobar("frente parcial evita nodo justo debajo del objetivo", cercaEn3D < bajoObjetivo);

        // A2 · cota dirigida para misiones por ENCIMA: suma el término vertical solo cuando y < goalY.
        double admisibleBajo = NavGoalMetric.heuristic(0, 63, 0, 0, 71, 0, radioSqr, 1.0D, 2, 3);
        double dirigidaBajo = NavGoalMetric.heuristicClimbing(0, 63, 0, 0, 71, 0, radioSqr, 1.0D, 2, 3);
        comprobar("cota dirigida prioriza subir (y < meta)", dirigidaBajo > admisibleBajo);
        comprobarCerca("cota dirigida = admisible + 0.5 * desnivel restante",
                admisibleBajo + 0.5D * 7.0D, dirigidaBajo);
        comprobar("a la altura de la meta la cota dirigida es la admisible",
                NavGoalMetric.heuristicClimbing(0, 71, 0, 0, 71, 0, radioSqr, 1.0D, 2, 3)
                        == NavGoalMetric.heuristic(0, 71, 0, 0, 71, 0, radioSqr, 1.0D, 2, 3));
        comprobar("por encima de la meta la cota dirigida es la admisible",
                NavGoalMetric.heuristicClimbing(0, 96, 0, 0, 71, 0, radioSqr, 1.0D, 2, 3)
                        == NavGoalMetric.heuristic(0, 96, 0, 0, 71, 0, radioSqr, 1.0D, 2, 3));
        comprobar("peso de subida acotado", NavGoalMetric.CLIMB_WEIGHT > 0.0D
                && NavGoalMetric.CLIMB_WEIGHT <= 1.0D);

        if (fallos > 0) {
            System.out.println("NAV_GOAL_METRIC_TEST: FAIL (" + fallos + " fallos)");
            System.exit(1);
        }
        System.out.println("NAV_GOAL_METRIC_TEST: OK (todas las aserciones verdes)");
    }

    private static void comprobarCerca(String nombre, double esperado, double actual) {
        comprobar(nombre, Math.abs(esperado - actual) < 0.0001D);
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
