package com.chipoodle.devilrpg.entity.nav;

/** La excepción de recuperación solo aplica al bloque exacto delegado como BREAK override. */
public final class NavRecoveryGuardPolicyTest {
    public static void main(String[] args) {
        comprobar("rechaza recuperación genérica sobre celda activa",
                NavRecoveryGuardPolicy.debeRechazar(true, false, false));
        comprobar("permite la acción BREAK/BRIDGE del propio nodo activo",
                !NavRecoveryGuardPolicy.debeRechazar(true, true, false));
        comprobar("permite el primer obstáculo delegado explícitamente como BREAK",
                !NavRecoveryGuardPolicy.debeRechazar(true, false, true));
        comprobar("no permite override sobre una celda distinta",
                NavRecoveryGuardPolicy.debeRechazar(true, false, false));
        comprobar("celda no activa no necesita excepción",
                !NavRecoveryGuardPolicy.debeRechazar(false, false, false));
        System.out.println("NAV_RECOVERY_GUARD_POLICY_TEST: OK (5 aserciones)");
    }

    private static void comprobar(String nombre, boolean ok) {
        if (!ok) {
            throw new AssertionError(nombre);
        }
    }
}
