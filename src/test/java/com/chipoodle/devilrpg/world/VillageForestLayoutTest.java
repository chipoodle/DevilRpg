package com.chipoodle.devilrpg.world;

public final class VillageForestLayoutTest {

    public static void main(String[] args) {
        if (VillageForestLayout.slots() != 20) {
            throw new AssertionError("plazas=" + VillageForestLayout.slots());
        }
        java.util.Set<Long> unique = new java.util.HashSet<>();
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int x : VillageForestLayout.xs()) {
            for (int z : VillageForestLayout.zs()) {
                if (!unique.add(((long) x << 32) ^ (z & 0xffffffffL))) {
                    throw new AssertionError("plaza duplicada " + x + "," + z);
                }
                if ((long) x * x + (long) z * z > 62L * 62L) {
                    throw new AssertionError("plaza fuera del muro: " + x + "," + z);
                }
                if (x < VillageForestLayout.X0 || x > VillageForestLayout.X1
                        || z < VillageForestLayout.Z0 || z > VillageForestLayout.Z1) {
                    throw new AssertionError("plaza fuera de la caja: " + x + "," + z);
                }
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }
        }
        if (minZ < -40 || maxZ > -15) {
            throw new AssertionError("rango z=" + minZ + ".." + maxZ);
        }
        System.out.println("VILLAGE_FOREST_LAYOUT_TEST: OK (20 plazas, radio<=62, z=" + minZ + ".." + maxZ + ")");
    }
}
