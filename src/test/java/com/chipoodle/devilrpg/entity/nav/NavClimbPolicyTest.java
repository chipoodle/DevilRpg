package com.chipoodle.devilrpg.entity.nav;

/** Verifica que BUILD sea seleccionable como objetivo local sin fingir que el bloque es una celda de pie. */
public final class NavClimbPolicyTest {

    public static void main(String[] args) {
        comprobar("BUILD no es waypoint de pie", !NavClimbPolicy.esWaypointDePie(NavRoute.Transition.BUILD));
        comprobar("BUILD sí es waypoint seleccionable", NavClimbPolicy.esWaypointSeleccionable(NavRoute.Transition.BUILD));
        comprobar("BUILD exige construcción", NavClimbPolicy.requiereConstruccion(NavRoute.Transition.BUILD));

        comprobar("WALK es waypoint seleccionable", NavClimbPolicy.esWaypointSeleccionable(NavRoute.Transition.WALK));
        comprobar("WALK no exige construcción", !NavClimbPolicy.requiereConstruccion(NavRoute.Transition.WALK));
        comprobar("DROP sigue siendo waypoint de aterrizaje", NavClimbPolicy.esWaypointDePie(NavRoute.Transition.DROP));
        comprobar("DROP no exige construcción", !NavClimbPolicy.requiereConstruccion(NavRoute.Transition.DROP));

        comprobar("BREAK no es waypoint de pie", !NavClimbPolicy.esWaypointDePie(NavRoute.Transition.BREAK));
        comprobar("BREAK sí es waypoint seleccionable de acción", NavClimbPolicy.esWaypointSeleccionable(NavRoute.Transition.BREAK));
        comprobar("BREAK no exige construcción", !NavClimbPolicy.requiereConstruccion(NavRoute.Transition.BREAK));
        comprobar("BRIDGE no es waypoint de pie", !NavClimbPolicy.esWaypointDePie(NavRoute.Transition.BRIDGE));
        comprobar("BRIDGE sí es waypoint seleccionable de acción", NavClimbPolicy.esWaypointSeleccionable(NavRoute.Transition.BRIDGE));
        comprobar("BRIDGE no exige construcción BUILD", !NavClimbPolicy.requiereConstruccion(NavRoute.Transition.BRIDGE));

        System.out.println("NAV_CLIMB_POLICY_TEST: OK (todas las aserciones verdes)");
    }

    private static void comprobar(String nombre, boolean ok) {
        if (!ok) {
            throw new AssertionError(nombre);
        }
    }
}
