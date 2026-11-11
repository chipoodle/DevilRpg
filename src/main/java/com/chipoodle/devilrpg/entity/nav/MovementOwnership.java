package com.chipoodle.devilrpg.entity.nav;

/**
 * A2 REMEDIACIÓN 2 · <b>Propiedad única del movimiento</b>.
 *
 * <p>Cuando la navegación propia está encendida y hay un objetivo de combate vivo, el movimiento lo lleva el
 * goal de combate (planificador heredado). El ejecutor propio se suspende para no emitir un segundo
 * {@code moveTo} ni avanzar waypoints por un desplazamiento que no ordenó. Sin objetivo de combate, el
 * ejecutor vuelve a ser el único dueño.
 */
public final class MovementOwnership {

    private MovementOwnership() {
    }

    /** ¿Puede el ejecutor propio emitir movimiento en este tick? */
    public static boolean executorMayMove(final boolean customNavigation, final boolean validCombatTarget) {
        return customNavigation && !validCombatTarget;
    }
}
