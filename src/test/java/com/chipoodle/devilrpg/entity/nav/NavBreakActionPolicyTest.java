package com.chipoodle.devilrpg.entity.nav;

public final class NavBreakActionPolicyTest {
    public static void main(String[] args) {
        check("fully executable action accepted", NavBreakActionPolicy.valid(true, true, false, true, true, true));
        check("no body clearance rejected", !NavBreakActionPolicy.valid(false, true, false, true, true, true));
        check("no support rejected", !NavBreakActionPolicy.valid(true, false, false, true, true, true));
        check("protected target rejected", !NavBreakActionPolicy.valid(true, true, true, true, true, true));
        check("non-breakable target rejected", !NavBreakActionPolicy.valid(true, true, false, false, true, true));
        check("out of reach rejected", !NavBreakActionPolicy.valid(true, true, false, true, false, true));
        check("occluded target rejected", !NavBreakActionPolicy.valid(true, true, false, true, true, false));
        check("vertical limit enforced",
                NavBreakReachPolicy.reachable(64, 66, 6.0D, NavBreakActionPolicy.ACTION_REACH_SQR)
                        && !NavBreakReachPolicy.reachable(64, 67, 1.0D, NavBreakActionPolicy.ACTION_REACH_SQR));
        System.out.println("NAV_BREAK_ACTION_POLICY_TEST: OK (8 assertions)");
    }
    private static void check(String name, boolean value) {
        if (!value) throw new AssertionError(name);
    }
}
