package com.chipoodle.devilrpg.entity.nav;

/**
 * A4.2 · prueba pura de la decision de invalidacion de ruta stale. Es la MISMA funcion que usa
 * {@link NavExecutor}: no hay un mock que sustituya la decision de produccion.
 */
public final class NavRouteInvalidationPolicyTest {

    private static int fallos;

    public static void main(String[] args) {
        final int gracia = 20;
        comprobar("sin ruta no invalida",
                !NavRouteInvalidationPolicy.debeInvalidar(false, true, 100, gracia, NavRoute.Transition.WALK));
        comprobar("navegacion en curso no invalida",
                !NavRouteInvalidationPolicy.debeInvalidar(true, false, 100, gracia, NavRoute.Transition.WALK));
        comprobar("dentro de la gracia no invalida",
                !NavRouteInvalidationPolicy.debeInvalidar(true, true, gracia, gracia, NavRoute.Transition.WALK));
        comprobar("WALK sin avance invalida",
                NavRouteInvalidationPolicy.debeInvalidar(true, true, gracia + 1, gracia, NavRoute.Transition.WALK));
        comprobar("STEP_UP sin avance invalida",
                NavRouteInvalidationPolicy.debeInvalidar(true, true, gracia + 1, gracia, NavRoute.Transition.STEP_UP));
        comprobar("JUMP_UP sin avance invalida",
                NavRouteInvalidationPolicy.debeInvalidar(true, true, gracia + 1, gracia, NavRoute.Transition.JUMP_UP));
        comprobar("DROP sin avance invalida",
                NavRouteInvalidationPolicy.debeInvalidar(true, true, gracia + 1, gracia, NavRoute.Transition.DROP));
        comprobar("BREAK se conserva para su rama",
                !NavRouteInvalidationPolicy.debeInvalidar(true, true, gracia + 1, gracia, NavRoute.Transition.BREAK));
        comprobar("BRIDGE se conserva para su rama",
                !NavRouteInvalidationPolicy.debeInvalidar(true, true, gracia + 1, gracia, NavRoute.Transition.BRIDGE));

        if (fallos > 0) {
            System.out.println("NAV_ROUTE_INVALIDATION_TEST: FAIL (" + fallos + " fallos)");
            System.exit(1);
        }
        System.out.println("NAV_ROUTE_INVALIDATION_TEST: OK (todas las aserciones verdes)");
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
