package com.chipoodle.devilrpg.entity.nav;

/**
 * A2 · veto de madera: hojas siempre, troncos de árbol siempre, troncos del anillo del muro NO
 * (breakBlockAt los acepta y son la brecha del asedio).
 */
public final class NavBreakFilterPolicyTest {
    public static void main(String[] args) {
        check("tree log is vetoed", NavBreakFilterPolicy.vetoWood(false, true, false));
        check("leaves are vetoed", NavBreakFilterPolicy.vetoWood(true, false, false));
        check("tree log with leaves is vetoed", NavBreakFilterPolicy.vetoWood(true, true, false));
        check("wall ring log is NOT vetoed", !NavBreakFilterPolicy.vetoWood(false, true, true));
        check("non-wood is never vetoed", !NavBreakFilterPolicy.vetoWood(false, false, false));
        check("leaves are vetoed even inside the wall ring",
                NavBreakFilterPolicy.vetoWood(true, false, true));
        check("stone of the wall ring is not wood-vetoed",
                !NavBreakFilterPolicy.vetoWood(false, false, true));
        System.out.println("NAV_BREAK_FILTER_POLICY_TEST: OK (" + assertions + " assertions)");
    }

    private static int assertions;

    private static void check(String name, boolean ok) {
        assertions++;
        if (!ok) throw new AssertionError(name);
    }
}
