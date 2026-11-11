package com.chipoodle.devilrpg.entity.nav;

/**
 * A4.2 · Politica pura de invalidacion de una ruta local que ya no progresa.
 *
 * <p>Una ruta con un nodo activo no-BREAK/BRIDGE que lleva mas de la gracia sin avance y con la
 * navegacion de vanilla terminada es <b>stale</b>: el ejecutor no debe seguir enviando el mismo nodo
 * ni gastar la guarda de recuperacion contra el objetivo lejano. Se invalida la ruta local y el
 * planificador existente decide desde la posicion actual. BREAK/BRIDGE se conservan: son acciones
 * legitimas de la ruta y las gestiona su rama de recuperacion.
 */
public final class NavRouteInvalidationPolicy {

    private NavRouteInvalidationPolicy() {
    }

    public static boolean debeInvalidar(final boolean hayRuta, final boolean navegacionTerminada,
                                        final int ticksSinAvance, final int graciaTicks,
                                        final NavRoute.Transition transicion) {
        if (!hayRuta || !navegacionTerminada || ticksSinAvance <= graciaTicks) {
            return false;
        }
        return transicion != NavRoute.Transition.BREAK && transicion != NavRoute.Transition.BRIDGE;
    }
}
