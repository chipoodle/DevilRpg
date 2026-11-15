package com.chipoodle.devilrpg.entity.nav;

import java.util.OptionalInt;
import java.util.function.IntPredicate;

/**
 * Valida la columna vertical que debe quedar libre entre una celda de aterrizaje y los pies actuales.
 * Pura para poder probar la misma regla que aplica el ejecutor y que debe respetar el constructor de escalones.
 */
public final class NavDropColumnPolicy {
    private NavDropColumnPolicy() {
    }

    /**
     * Devuelve la primera Y bloqueada, desde encima del aterrizaje hasta los pies actuales (inclusive).
     * Si el mob ya está a la altura del aterrizaje o por debajo, no hay columna de caída que comprobar.
     */
    public static OptionalInt primerBloqueoY(int landingY, int currentFeetY, IntPredicate bloqueadaEnY) {
        if (bloqueadaEnY == null) {
            throw new IllegalArgumentException("bloqueadaEnY no puede ser null");
        }
        for (int y = landingY + 1; y <= currentFeetY; y++) {
            if (bloqueadaEnY.test(y)) {
                return OptionalInt.of(y);
            }
        }
        return OptionalInt.empty();
    }

    /**
     * ¿Un bloque de puente propuesto ocuparía la columna vertical de caída de un escalón ya construido?
     * El landing del escalón está en stairY+1; los bloques desde landingY+1 hasta maxSafeDrop
     * pueden impedir que el mob vuelva a usar esa bajada.
     */
    public static boolean puenteInvadeColumnaDeEscalon(int stairX, int stairY, int stairZ,
            int bridgeX, int bridgeY, int bridgeZ, int maxSafeDrop) {
        int landingY = stairY + 1;
        return maxSafeDrop > 0
                && stairX == bridgeX
                && stairZ == bridgeZ
                && bridgeY > landingY
                && bridgeY <= landingY + maxSafeDrop;
    }
}
