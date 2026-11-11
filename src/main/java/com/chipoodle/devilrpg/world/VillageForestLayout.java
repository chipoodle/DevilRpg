package com.chipoodle.devilrpg.world;

/** Pure geometry of the interior village forest; no Minecraft classes. */
public final class VillageForestLayout {

    public static final int X0 = -46;
    public static final int X1 = -25;
    public static final int Z0 = -40;
    public static final int Z1 = -15;
    public static final int COLUMNS = 5;
    public static final int ROWS = 4;

    private VillageForestLayout() {
    }

    public static int slots() {
        return COLUMNS * ROWS;
    }

    public static int[] xs() {
        return repartir(X0 + 2, X1 - 2, COLUMNS);
    }

    public static int[] zs() {
        return repartir(Z0 + 2, Z1 - 2, ROWS);
    }

    private static int[] repartir(int desde, int hasta, int cuantos) {
        int[] valores = new int[cuantos];
        for (int i = 0; i < cuantos; i++) {
            valores[i] = desde + (int) Math.round((hasta - desde) * (i / (double) (cuantos - 1)));
        }
        return valores;
    }
}
