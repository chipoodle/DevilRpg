package com.chipoodle.devilrpg.entity.nav;

public final class NavBreakTargetPolicyTest {
    public static void main(String[] args) {
        comprobar("floor below elevated target is support", NavBreakTargetPolicy.isSupportBelowTarget(64, 65, true));
        comprobar("same-level hit is not support", !NavBreakTargetPolicy.isSupportBelowTarget(65, 65, true));
        comprobar("different column is not support", !NavBreakTargetPolicy.isSupportBelowTarget(64, 65, false));
        System.out.println("NAV_BREAK_TARGET_POLICY_TEST: OK (3 assertions)");
    }

    private static void comprobar(String name, boolean ok) {
        if (!ok) throw new AssertionError(name);
    }
}
