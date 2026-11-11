package com.chipoodle.devilrpg.entity.nav;

/** Pure decisions shared by the planner and executor for climbing and waypoint conversion. */
public final class NavClimbPolicy {
    private NavClimbPolicy() {
    }

    public static boolean escaladaValida(boolean fits, boolean support) {
        return fits && support;
    }

    public static boolean esWaypointDePie(NavRoute.Transition t) {
        // DROP guarda el ATERRIZAJE (celda de pie valida); BUILD guarda la celda donde se COLOCA el bloque.
        return t != NavRoute.Transition.BUILD;
    }
}
