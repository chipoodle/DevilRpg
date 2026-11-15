package com.chipoodle.devilrpg.entity.nav;

public final class NavStairVolumeClearancePolicyTest {
    public static void main(String[] args) {
        comprobar("abre primero el bloque de pies", 0, NavStairVolumeClearancePolicy.firstBreakableBlockIndex(new boolean[]{true, true, true}, new boolean[]{true, true, true}, true, true));
        comprobar("si pies esta libre abre cabeza 1", 1, NavStairVolumeClearancePolicy.firstBreakableBlockIndex(new boolean[]{false, true, true}, new boolean[]{false, true, true}, true, true));
        comprobar("si dos huecos estan libres abre cabeza 2", 2, NavStairVolumeClearancePolicy.firstBreakableBlockIndex(new boolean[]{false, false, true}, new boolean[]{false, false, true}, true, true));
        comprobar("no salta un bloque de pies inquebrantable", -1, NavStairVolumeClearancePolicy.firstBreakableBlockIndex(new boolean[]{true, true, true}, new boolean[]{false, true, true}, true, true));
        comprobar("no rompe nada si todo esta libre", -1, NavStairVolumeClearancePolicy.firstBreakableBlockIndex(new boolean[]{false, false, false}, new boolean[]{false, false, false}, true, true));
        comprobar("no excava candidato protegido", -1, NavStairVolumeClearancePolicy.firstBreakableBlockIndex(new boolean[]{true, true, true}, new boolean[]{true, true, true}, false, true));
        comprobar("no excava sin soporte", -1, NavStairVolumeClearancePolicy.firstBreakableBlockIndex(new boolean[]{true, true, true}, new boolean[]{true, true, true}, true, false));
        comprobar("no excava con arreglos incompletos", -1, NavStairVolumeClearancePolicy.firstBreakableBlockIndex(new boolean[]{true}, new boolean[]{true}, true, true));
        System.out.println("NAV_STAIR_VOLUME_CLEARANCE_POLICY_TEST: OK (8 assertions)");
    }
    private static void comprobar(String nombre, int esperado, int actual) {
        if (esperado != actual) throw new AssertionError(nombre + ": esperado=" + esperado + " actual=" + actual);
    }
}
