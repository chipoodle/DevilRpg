package com.chipoodle.devilrpg.entity.nav;

/** Política pura de la guarda: solo una acción BREAK explícitamente delegada puede salir de la celda activa. */
public final class NavRecoveryGuardPolicy {
    private NavRecoveryGuardPolicy() {
    }

    public static boolean debeRechazar(boolean celdaActiva, boolean exencionNodoActivo,
            boolean exencionOverrideBreak) {
        return celdaActiva && !exencionNodoActivo && !exencionOverrideBreak;
    }
}
