package com.chipoodle.devilrpg.entity.nav;

/** Regla pura del sucesor canonico: una escalada usa la celda de pie validada. */
public final class NavSuccessorPolicy {
    private NavSuccessorPolicy() {
    }

    public static int[] sucesor(int x, int y, int z, boolean escalada, int piesHasta) {
        return escalada ? new int[]{x, piesHasta, z} : new int[]{x, y, z};
    }
}
