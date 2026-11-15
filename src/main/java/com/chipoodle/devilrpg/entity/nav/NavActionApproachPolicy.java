package com.chipoodle.devilrpg.entity.nav;

/** Política pura para delegar el primer bloque visible de una acción BREAK inalcanzable. */
public final class NavActionApproachPolicy {
    private NavActionApproachPolicy() {
    }

    /**
     * Si vanilla no puede alcanzar el nodo BREAK, se puede delegar el primer bloque del raycast
     * (incluido el propio objetivo BREAK) siempre que esté al alcance. RecoveryAction y
     * BlockInteractionPolicy siguen siendo los únicos que autorizan la rotura efectiva.
     */
    public static boolean debeDelegarPrimerBloque(boolean pathCanReach, boolean primerBloqueEnAlcance,
            boolean esBreak) {
        return esBreak && !pathCanReach && primerBloqueEnAlcance;
    }
}
