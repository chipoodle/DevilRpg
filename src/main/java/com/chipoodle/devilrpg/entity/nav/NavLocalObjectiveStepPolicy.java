package com.chipoodle.devilrpg.entity.nav;

/**
 * Distinguishes a genuinely new local waypoint from a repeated unusable waypoint.
 * Re-selecting the same blocked cell must not consume the bounded local-step budget.
 */
public final class NavLocalObjectiveStepPolicy {
    public enum Decision {
        ACCEPTAR_NUEVO,
        REUTILIZAR,
        RECHAZAR_REPETIDO_INUTILIZABLE
    }

    private NavLocalObjectiveStepPolicy() {
    }

    public static Decision decidir(boolean mismoObjetivo, boolean objetivoActualUtilizable,
                                   boolean dentroDeVentana) {
        if (!mismoObjetivo) {
            return Decision.ACEPTAR_NUEVO;
        }
        return objetivoActualUtilizable && dentroDeVentana
                ? Decision.REUTILIZAR
                : Decision.RECHAZAR_REPETIDO_INUTILIZABLE;
    }
}
