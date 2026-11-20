package com.chipoodle.devilrpg.entity.nav;

/** Pure canonical-state rule: transitions with a different physical feet height use the validated feet cell. */
public final class NavSuccessorPolicy {
    private NavSuccessorPolicy() {
    }

    public static int[] sucesor(int x, int y, int z, boolean usarPiesValidados, int piesHasta) {
        return usarPiesValidados ? new int[]{x, piesHasta, z} : new int[]{x, y, z};
    }
}
