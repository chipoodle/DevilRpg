package com.chipoodle.devilrpg.entity.nav;

public final class AdvanceUnderFirePolicyTest {
    public static void main(String[] args) {
        comprobar("no target: executor advances",
                AdvanceUnderFirePolicy.executorMayAdvance(true, false, false));
        comprobar("guard outside reach: combat briefly owns movement",
                !AdvanceUnderFirePolicy.executorMayAdvance(true, true, false));
        comprobar("guard in melee: executor still advances while attacking",
                AdvanceUnderFirePolicy.executorMayAdvance(true, true, true));
        comprobar("custom navigation off: executor does not advance",
                !AdvanceUnderFirePolicy.executorMayAdvance(false, true, true));
        comprobar("bell objective does not chase distant guard",
                !AdvanceUnderFirePolicy.shouldChase(true, 100.0));
        comprobar("bell objective fights nearby guard",
                AdvanceUnderFirePolicy.shouldChase(true, 9.0));
        comprobar("no bell objective may chase",
                AdvanceUnderFirePolicy.shouldChase(false, 100.0));
        comprobar("no target: march toward village",
                AdvanceUnderFirePolicy.shouldMarchToVillage(true, false, 0.0));
        comprobar("distant guard does not freeze bell-bound attacker",
                AdvanceUnderFirePolicy.shouldMarchToVillage(true, true, 100.0));
        comprobar("near guard owns movement instead of march",
                !AdvanceUnderFirePolicy.shouldMarchToVillage(true, true, 9.0));
        comprobar("without bell objective, pursue even distant target",
                !AdvanceUnderFirePolicy.shouldMarchToVillage(false, true, 100.0));
        System.out.println("ADVANCE_UNDER_FIRE_POLICY_TEST: OK (11 assertions)");
    }

    private static void comprobar(String name, boolean ok) {
        if (!ok) throw new AssertionError(name);
    }
}
