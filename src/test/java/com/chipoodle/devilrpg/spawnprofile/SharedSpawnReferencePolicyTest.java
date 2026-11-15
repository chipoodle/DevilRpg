package com.chipoodle.devilrpg.spawnprofile;

public final class SharedSpawnReferencePolicyTest {
    public static void main(String[] args) {
        comprobar("shared spawn is the reference",
                SharedSpawnReferencePolicy.distanceSqr(3, 64, 4, 0, 64, 0) == 25.0D);
        comprobar("distant position uses shared origin",
                SharedSpawnReferencePolicy.distanceSqr(100, 64, 0, 0, 64, 0) == 10000.0D);
        comprobar("same point is zero", SharedSpawnReferencePolicy.distanceSqr(0, 64, 0, 0, 64, 0) == 0.0D);
        comprobar("vertical distance counts",
                SharedSpawnReferencePolicy.distanceSqr(0, 70, 0, 0, 64, 0) == 36.0D);
        comprobar("player-like different spawn does not participate",
                SharedSpawnReferencePolicy.distanceSqr(10, 64, 10, 0, 64, 0) == 200.0D);
        System.out.println("SHARED_SPAWN_REFERENCE_POLICY_TEST: OK (5 assertions)");
    }

    private static void comprobar(String name, boolean ok) {
        if (!ok) throw new AssertionError(name);
    }
}
