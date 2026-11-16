package com.chipoodle.devilrpg.entity.nav;

public final class NavBreakContractTest {
    public static void main(String[] args) {
        comprobar("adjacent is in action reach", NavBreakContractPolicy.withinReach(1, 0, 0));
        comprobar("two blocks is in action reach", NavBreakContractPolicy.withinReach(2, 0, 0));
        comprobar("three blocks is out of action reach", !NavBreakContractPolicy.withinReach(3, 0, 0));
        comprobar("vertical two is in reach", NavBreakContractPolicy.withinReach(0, 2, 0));
        comprobar("candidate set is bounded", NavBreakContractPolicy.CANDIDATE_OFFSETS == 8);
        System.out.println("NAV_BREAK_CONTRACT_TEST: OK (5 assertions)");
    }

    private static void comprobar(String name, boolean ok) {
        if (!ok) throw new AssertionError(name);
    }
}
