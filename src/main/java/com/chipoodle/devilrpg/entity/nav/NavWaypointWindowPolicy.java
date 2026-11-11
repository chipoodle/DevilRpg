package com.chipoodle.devilrpg.entity.nav;

/** Pure window rule for converting a global route node into a local objective. */
public final class NavWaypointWindowPolicy {
    private NavWaypointWindowPolicy() {
    }

    public static boolean aceptable(int dx, int dz, int dy, boolean drop, int maxHorizontal, int maxVertical,
                                    int maxDrop) {
        if (Math.abs(dx) > maxHorizontal || Math.abs(dz) > maxHorizontal) {
            return false;
        }
        if (!drop) {
            return Math.abs(dy) <= maxVertical;
        }
        // Un DROP puede bajar mas que la ventana vertical del planificador, pero nunca mas que la caida segura.
        return dy < 0 && -dy <= maxDrop;
    }
}
