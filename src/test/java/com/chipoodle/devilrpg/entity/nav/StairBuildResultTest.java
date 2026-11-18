package com.chipoodle.devilrpg.entity.nav;

public final class StairBuildResultTest {
    public static void main(String[] args) {
        comprobar("placed is not access clear", StairBuildResult.STAIR_PLACED != StairBuildResult.ACCESS_CLEARED);
        comprobar("access clear is distinct from no progress", StairBuildResult.ACCESS_CLEARED != StairBuildResult.NO_PROGRESS);
        comprobar("cadence wait is distinct from no progress", StairBuildResult.CADENCE_WAIT != StairBuildResult.NO_PROGRESS);
        comprobar("cadence wait is not a stair placement", StairBuildResult.CADENCE_WAIT != StairBuildResult.STAIR_PLACED);
        comprobar("rejected distinct", StairBuildResult.REJECTED != StairBuildResult.FAILED);
        comprobar("only placed is a stair", StairBuildResult.STAIR_PLACED == StairBuildResult.valueOf("STAIR_PLACED"));
        System.out.println("STAIR_BUILD_RESULT_TEST: OK (6 assertions)");
    }

    private static void comprobar(String name, boolean ok) {
        if (!ok) throw new AssertionError(name);
    }
}
