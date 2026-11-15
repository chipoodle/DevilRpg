package com.chipoodle.devilrpg.entity.nav;

public final class NavMissionChainPolicyTest {
    public static void main(String[] args) {
        comprobar("same raid root retains active approach stage",
                NavMissionChainPolicy.retainActiveApproachChain(true, true, true, false));
        comprobar("new raid root replaces old chain",
                !NavMissionChainPolicy.retainActiveApproachChain(false, true, true, false));
        comprobar("missing objective accepts new chain",
                !NavMissionChainPolicy.retainActiveApproachChain(true, false, true, false));
        comprobar("completed approach accepts next chain",
                !NavMissionChainPolicy.retainActiveApproachChain(true, true, false, false));
        comprobar("entering ring may replace approach with interior-only chain",
                !NavMissionChainPolicy.retainActiveApproachChain(true, true, true, true));
        System.out.println("NAV_MISSION_CHAIN_POLICY_TEST: OK (5 assertions)");
    }

    private static void comprobar(String nombre, boolean ok) {
        if (!ok) throw new AssertionError(nombre);
    }
}
