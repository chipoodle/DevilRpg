package com.chipoodle.devilrpg.spawnprofile;

/** Geographic difficulty reference: always the shared world spawn, never a player-specific spawn. */
public final class SharedSpawnReferencePolicy {
    private SharedSpawnReferencePolicy() {
    }

    public static double distanceSqr(final double entityX, final double entityY, final double entityZ,
                                     final double spawnX, final double spawnY, final double spawnZ) {
        double dx = entityX - spawnX;
        double dy = entityY - spawnY;
        double dz = entityZ - spawnZ;
        return dx * dx + dy * dy + dz * dz;
    }
}
