package com.chipoodle.devilrpg.entity.nav;

public final class NavTerrainRearmPolicyTest {
    public static void main(String[] args) {
        comprobar("rearma si hubo BREAK y luego se agoto el limite", NavTerrainRearmPolicy.shouldRearm(true, true, false));
        comprobar("no rearma si no hubo cambio de terreno", !NavTerrainRearmPolicy.shouldRearm(false, true, false));
        comprobar("no rearma antes de agotar el limite", !NavTerrainRearmPolicy.shouldRearm(true, false, false));
        comprobar("no rearma dos veces en la misma mision", !NavTerrainRearmPolicy.shouldRearm(true, true, true));
        comprobar("no rearma si no hay cambio ni limite", !NavTerrainRearmPolicy.shouldRearm(false, false, false));
        comprobar("reset local con cambio y limite",
                NavTerrainRearmPolicy.shouldResetLocalStairBudget(true, true, 0, 2));
        comprobar("reset local acotado",
                !NavTerrainRearmPolicy.shouldResetLocalStairBudget(true, true, 2, 2));
        comprobar("sin cambio no reset local",
                !NavTerrainRearmPolicy.shouldResetLocalStairBudget(false, true, 0, 2));
        comprobar("sin limite no reset local",
                !NavTerrainRearmPolicy.shouldResetLocalStairBudget(true, false, 0, 2));
        System.out.println("NAV_TERRAIN_REARM_POLICY_TEST: OK (9 assertions)");
    }
    private static void comprobar(String nombre, boolean ok) {
        if (!ok) throw new AssertionError(nombre);
    }
}
