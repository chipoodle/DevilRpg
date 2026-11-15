package com.chipoodle.devilrpg.entity.nav;

public final class NavBreakReachPolicyTest {
    public static void main(String[] args) {
        comprobar("reachable break", NavBreakReachPolicy.reachable(62, 64, 4.0, 25.0));
        comprobar("five-block vertical break rejected",
                !NavBreakReachPolicy.reachable(62, 67, 4.0, 25.0));
        comprobar("far break rejected", !NavBreakReachPolicy.reachable(62, 63, 100.0, 25.0));
        comprobar("same-level close accepted", NavBreakReachPolicy.reachable(71, 71, 1.0, 25.0));
        System.out.println("NAV_BREAK_REACH_POLICY_TEST: OK (4 assertions)");
    }

    private static void comprobar(String name, boolean ok) {
        if (!ok) throw new AssertionError(name);
    }
}
