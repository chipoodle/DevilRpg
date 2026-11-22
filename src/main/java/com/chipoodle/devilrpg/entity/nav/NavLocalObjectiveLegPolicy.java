package com.chipoodle.devilrpg.entity.nav;

/**
 * A2 · <b>Tramo local acotado.</b>
 * <p>
 * El objetivo local se elige sobre la ruta global. Quedarse con el nodo MÁS LEJANO que cabe en la ventana
 * (hasta 48×6, medido: una esquina a 58,8 bloques) entrega al A* local una pierna que su presupuesto de 900
 * expansiones no puede resolver: medido en `a2n-20261121-212053`, IDX3 recibió `639,71,552` (39,44 de distancia)
 * y la búsqueda global al mismo destino había costado 1393 expansiones. El ejecutor gastó 10 intentos de 900
 * sin moverse y terminó FAILED.
 * <p>
 * Este contrato limita la pierna a un alcance de Chebyshev: se conserva el waypoint válido MÁS LEJANO que quepa
 * dentro del tramo (progreso real) y, si ninguno cupiera, el más cercano. No cambia la ventana ni el presupuesto.
 */
public final class NavLocalObjectiveLegPolicy {
    /** Alcance máximo (Chebyshev, bloques) de la pierna local. La mitad de la ventana horizontal (48). */
    public static final int MAX_LEG_CHEBYSHEV = 24;

    private NavLocalObjectiveLegPolicy() {
    }

    public static boolean withinLeg(int dx, int dz, int maxLeg) {
        return Math.max(Math.abs(dx), Math.abs(dz)) <= Math.max(0, maxLeg);
    }

    /**
     * Índice elegido sobre una secuencia ordenada por cercanía (0 = más cercano). Devuelve el índice válido más
     * lejano dentro del tramo; si ninguno cabe, el válido más cercano; {@code -1} si no hay ninguno válido.
     */
    public static int selectIndex(int[] chebyshevFromMob, boolean[] usable, int maxLeg) {
        if (chebyshevFromMob == null || usable == null || chebyshevFromMob.length != usable.length) {
            return -1;
        }
        for (int i = chebyshevFromMob.length - 1; i >= 0; i--) {
            if (usable[i] && withinLeg(chebyshevFromMob[i], 0, maxLeg)) {
                return i;
            }
        }
        for (int i = 0; i < chebyshevFromMob.length; i++) {
            if (usable[i]) {
                return i;
            }
        }
        return -1;
    }
}
