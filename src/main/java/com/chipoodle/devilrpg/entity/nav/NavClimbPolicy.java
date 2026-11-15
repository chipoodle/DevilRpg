package com.chipoodle.devilrpg.entity.nav;

/** Pure decisions shared by the planner and executor for climbing and waypoint conversion. */
public final class NavClimbPolicy {
    private NavClimbPolicy() {
    }

    public static boolean escaladaValida(boolean fits, boolean support) {
        return fits && support;
    }

    public static boolean esWaypointDePie(NavRoute.Transition t) {
        // DROP guarda el ATERRIZAJE (celda de pie valida); BUILD, BREAK y BRIDGE son nodos de accion,
        // no celdas donde el mob deba poder permanecer de pie.
        return t != null && t != NavRoute.Transition.BUILD
                && t != NavRoute.Transition.BREAK && t != NavRoute.Transition.BRIDGE;
    }

    /** BUILD/BREAK/BRIDGE tambien son objetivos locales: son celdas de accion, no celdas de pie. */
    public static boolean esWaypointSeleccionable(NavRoute.Transition t) {
        return t != null && (esWaypointDePie(t) || requiereConstruccion(t)
                || t == NavRoute.Transition.BREAK || t == NavRoute.Transition.BRIDGE);
    }

    public static boolean requiereConstruccion(NavRoute.Transition t) {
        return t == NavRoute.Transition.BUILD;
    }
}
