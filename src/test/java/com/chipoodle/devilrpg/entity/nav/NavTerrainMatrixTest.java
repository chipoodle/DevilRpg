package com.chipoodle.devilrpg.entity.nav;

/**
 * Matriz de terreno (nivel de política): combina STEP_UP, DROP, BREAK, BUILD, presupuestos y cambios de terreno
 * sobre las políticas puras del planner. Cada fila declara el estado inicial, la política evaluada y el
 * resultado esperado; las filas que necesitan mundo real están marcadas como {@code RUNTIME}.
 */
public final class NavTerrainMatrixTest {
    public static void main(String[] args) {
        // --- STEP_UP / celda de pies -------------------------------------------------------------
        check("STEP_UP: celda de pies valida sin riesgos",
                NavFeetCellPolicy.isValid(true, false, false, false, false, false, true));
        check("STEP_UP: sin soporte no es pisable",
                !NavFeetCellPolicy.isValid(true, false, false, false, false, false, false));
        check("STEP_UP: celda protegida no es pisable",
                !NavFeetCellPolicy.isValid(true, true, false, false, false, false, true));
        check("STEP_UP: chunk descargado no es pisable",
                !NavFeetCellPolicy.isValid(false, false, false, false, false, false, true));
        check("STEP_UP: pies con colision no es pisable",
                !NavFeetCellPolicy.isValid(true, false, true, false, false, false, true));
        check("STEP_UP: cabeza con colision no es pisable",
                !NavFeetCellPolicy.isValid(true, false, false, true, false, false, true));
        check("STEP_UP: fluido/peligro no es pisable",
                !NavFeetCellPolicy.isValid(true, false, false, false, true, false, true));

        // --- DROP ---------------------------------------------------------------------------------
        check("DROP: borde de 1 bloque valido",
                NavDropEdgeStepPolicy.shouldStart(true, true, true, false, 1, 3, true, true, true, true));
        check("DROP: borde de 3 bloques valido",
                NavDropEdgeStepPolicy.shouldStart(true, true, true, false, 3, 3, true, true, true, true));
        check("DROP: 4 bloques fuera del maximo",
                !NavDropEdgeStepPolicy.shouldStart(true, true, true, false, 4, 3, true, true, true, true));
        check("DROP: aterrizaje sin soporte rechazado",
                !NavDropEdgeStepPolicy.shouldStart(true, true, true, false, 1, 3, true, true, false, true));
        check("DROP: aterrizaje sin holgura rechazado",
                !NavDropEdgeStepPolicy.shouldStart(true, true, true, false, 1, 3, true, true, true, false));
        check("DROP: ya en el aterrizaje no arranca",
                !NavDropEdgeStepPolicy.shouldStart(true, true, true, true, 0, 3, true, true, true, true));
        check("DROP: en el aire no arranca",
                !NavDropEdgeStepPolicy.shouldStart(true, true, false, false, 1, 3, true, true, true, true));
        check("DROP: transicion que no es DROP no arranca",
                !NavDropEdgeStepPolicy.shouldStart(false, true, true, false, 1, 3, true, true, true, true));
        check("DROP: despegue sin soporte no arranca",
                !NavDropEdgeStepPolicy.shouldStart(true, true, true, false, 1, 3, false, true, true, true));

        // --- DROP + overshoot (aterrizaje real mas abajo) -----------------------------------------
        check("DROP+overshoot: cayo justo en el aterrizaje",
                NavDropOvershootPolicy.acceptRealLanding(true, true, true, true, 10, 67, 10, 10, 67, 10, 3));
        check("DROP+overshoot: cayo 2 por debajo con caida segura 3",
                NavDropOvershootPolicy.acceptRealLanding(true, true, true, true, 10, 65, 10, 10, 67, 10, 3));
        check("DROP+overshoot: exceso 2 con caida segura 1 rechazado",
                !NavDropOvershootPolicy.acceptRealLanding(true, true, true, true, 10, 65, 10, 10, 67, 10, 1));
        check("DROP+overshoot: aun por encima del aterrizaje rechazado",
                !NavDropOvershootPolicy.acceptRealLanding(true, true, true, true, 10, 70, 10, 10, 67, 10, 3));
        check("DROP+overshoot: en el aire rechazado",
                !NavDropOvershootPolicy.acceptRealLanding(false, true, true, true, 10, 67, 10, 10, 67, 10, 3));
        check("DROP+overshoot: sin pies validos rechazado",
                !NavDropOvershootPolicy.acceptRealLanding(true, false, true, true, 10, 67, 10, 10, 67, 10, 3));
        check("DROP+overshoot: a mas de 1 bloque en horizontal rechazado",
                !NavDropOvershootPolicy.acceptRealLanding(true, true, true, true, 12, 67, 10, 10, 67, 10, 3));

        // --- BREAK + BUILD (ascenso/escalon) ------------------------------------------------------
        check("CLIMB: cara rompible se despeja",
                NavClimbStepPolicy.clearable(true, true, false));
        check("CLIMB: cara protegida no se despeja",
                !NavClimbStepPolicy.clearable(true, true, true));
        check("CLIMB: cara no rompible no se despeja",
                !NavClimbStepPolicy.clearable(true, false, false));
        check("CLIMB: fase CLEAR con cara solida",
                NavClimbStepPolicy.phase(true, true, true, false) == NavClimbStepPolicy.Phase.CLEAR);
        check("CLIMB: fase PLACE con cara/cabeza/techo libres",
                NavClimbStepPolicy.phase(false, false, false, false) == NavClimbStepPolicy.Phase.PLACE);
        check("CLIMB: fase MOVE con escalon colocado",
                NavClimbStepPolicy.phase(false, false, false, true) == NavClimbStepPolicy.Phase.MOVE);
        check("CLIMB: aterrizaje verificado con cuerpo y apoyo",
                NavClimbStepPolicy.landingVerified(true, true, true, true));
        check("CLIMB: aterrizaje sin apoyo no se verifica",
                !NavClimbStepPolicy.landingVerified(true, true, false, true));

        // --- Presupuestos (local/global/tunel) ----------------------------------------------------
        check("TUNEL: primer plan permitido",
                TunnelPlanPolicy.canRequest(0L, 0L, 0, 0));
        check("TUNEL: cooldown bloquea",
                !TunnelPlanPolicy.canRequest(10L, 60L, 0, 0));
        check("TUNEL: tope de planes bloquea",
                !TunnelPlanPolicy.canRequest(1000L, 0L, 4, 0));
        check("TUNEL: tope de pasos bloquea",
                !TunnelPlanPolicy.canRequest(1000L, 0L, 0, 6));
        check("TUNEL: pasos disponibles decrecen",
                TunnelPlanPolicy.stepsAvailable(0) > TunnelPlanPolicy.stepsAvailable(5));

        System.out.println("NAV_TERRAIN_MATRIX_TEST: OK (" + assertions + " assertions, "
                + "escenarios RUNTIME: DROP entre columnas reales, BREAK con linea de vision, "
                + "BUILD con colocacion real, reanudacion de A* y cambios dinamicos de terreno)");
    }

    private static int assertions;

    private static void check(String name, boolean ok) {
        assertions++;
        if (!ok) {
            throw new AssertionError(name);
        }
    }
}
