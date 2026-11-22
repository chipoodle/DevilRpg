package com.chipoodle.devilrpg.entity.nav;

/**
 * A2 · <b>Drop que aterriza MÁS ABAJO de lo previsto.</b>
 * <p>
 * Un DROP validado puede overshootear si el pozo es más profundo que el aterrizaje planificado: medido en
 * `a2n-20261121-212053`, IDX5 planificó aterrizar en `597,74,469` y cayó hasta `597,71,470`, una celda de pie
 * VÁLIDA (suelo, soporte, AABB). El ejecutor, en vez de aceptar el aterrizaje real, exigía volver físicamente al
 * take-off `596,75,470` (+4); el grafo solo modela ascensos de +1 por arista, así que la búsqueda exacta moría
 * en 30 expansiones y el ejecutor repetía la misma búsqueda imposible cada 20 ticks (livelock: 160 rechazos).
 * <p>
 * Este contrato admite el aterrizaje REAL cuando es físicamente seguro y el exceso vertical cabe en la caída
 * segura. No relaja soporte, AABB, protección ni caída máxima: solo cambia el desenlace del estancamiento.
 */
public final class NavDropOvershootPolicy {
    private NavDropOvershootPolicy() {
    }

    public static boolean acceptRealLanding(boolean grounded, boolean feetValid, boolean supported, boolean fits,
                                            int liveX, int liveY, int liveZ,
                                            int landingX, int landingY, int landingZ,
                                            int maxSafeDrop) {
        if (!grounded || !feetValid || !supported || !fits) {
            return false;
        }
        if (Math.abs(liveX - landingX) > 1 || Math.abs(liveZ - landingZ) > 1) {
            return false;
        }
        // Debe haber caído hasta el aterrizaje o por debajo, y el exceso no puede superar la caída segura.
        int overshoot = landingY - liveY;
        return overshoot >= 0 && overshoot <= Math.max(0, maxSafeDrop);
    }
}
