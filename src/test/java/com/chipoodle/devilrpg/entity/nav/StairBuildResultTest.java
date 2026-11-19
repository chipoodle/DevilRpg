package com.chipoodle.devilrpg.entity.nav;

public final class StairBuildResultTest {
    public static void main(String[] args) {
        comprobar("placed is not access clear", StairBuildResult.STAIR_PLACED != StairBuildResult.ACCESS_CLEARED);
        comprobar("access clear is distinct from no progress", StairBuildResult.ACCESS_CLEARED != StairBuildResult.NO_PROGRESS);
        comprobar("cadence wait is distinct from no progress", StairBuildResult.CADENCE_WAIT != StairBuildResult.NO_PROGRESS);
        comprobar("cadence wait is not a stair placement", StairBuildResult.CADENCE_WAIT != StairBuildResult.STAIR_PLACED);
        comprobar("rejected distinct", StairBuildResult.REJECTED != StairBuildResult.FAILED);
        comprobar("only placed is a stair", StairBuildResult.STAIR_PLACED == StairBuildResult.valueOf("STAIR_PLACED"));
        comprobar("no candidate without cooldown is NO_PROGRESS",
                StairBuildResultPolicy.clasificar(false, false, false, 0, 0)
                        == StairBuildResult.NO_PROGRESS);
        comprobar("only a real cooldown decrement is CADENCE_WAIT",
                StairBuildResultPolicy.clasificar(false, false, false, 10, 9)
                        == StairBuildResult.CADENCE_WAIT);
        comprobar("unchanged cooldown is not CADENCE_WAIT",
                StairBuildResultPolicy.clasificar(false, false, false, 10, 10)
                        == StairBuildResult.NO_PROGRESS);
        comprobar("verified pending stair is STAIR_PLACED",
                StairBuildResultPolicy.clasificar(true, true, false, 0, 0)
                        == StairBuildResult.STAIR_PLACED);
        comprobar("verified access clear is distinct",
                StairBuildResultPolicy.clasificar(true, false, true, 0, 0)
                        == StairBuildResult.ACCESS_CLEARED);
        comprobar("unclassified successful builder result fails closed",
                StairBuildResultPolicy.clasificar(true, false, false, 0, 0)
                        == StairBuildResult.FAILED);
        System.out.println("STAIR_BUILD_RESULT_TEST: OK (12 assertions)");
    }

    private static void comprobar(String name, boolean ok) {
        if (!ok) throw new AssertionError(name);
    }
}
