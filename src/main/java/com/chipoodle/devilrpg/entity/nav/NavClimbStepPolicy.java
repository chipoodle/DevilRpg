package com.chipoodle.devilrpg.entity.nav;

/**
 * A2 · <b>ESCALÓN DE ASCENSO.</b> Contrato puro para subir UN bloque cuando el terreno no ofrece un escalón
 * natural: se despejan las celdas necesarias de la cara (pies, cabeza y techo del escalón) y se coloca un bloque
 * en la celda de pies, sobre el que la entidad sube.
 * <p>
 * Es la capacidad que el jugador pidió explícitamente («si cavan por debajo pero después hacen algo para salir a
 * la superficie, obligatorio»). Sin ella, una aldea en una meseta con un escalón vertical de 6-8 bloques (medido
 * en el volcado de terreno de `a2n-20261121-223916`) es inalcanzable para los asaltantes que nacen abajo.
 * <p>
 * No decide dirección ni toca el mundo: solo dice qué fase toca y qué es válido en cada una.
 */
public final class NavClimbStepPolicy {
    private NavClimbStepPolicy() {
    }

    public enum Phase {
        /** Quedan celdas sólidas por despejar (se rompen una por tick). */
        CLEAR,
        /** La cara está libre: toca colocar el bloque del escalón. */
        PLACE,
        /** El bloque está colocado: toca subir físicamente a él. */
        MOVE
    }

    /** Cada celda de la cara debe estar ya libre, o ser rompible y no estar protegida. */
    public static boolean clearable(boolean solid, boolean breakable, boolean protectedCell) {
        return !solid || (breakable && !protectedCell);
    }

    /** Fase que toca según el estado REAL del mundo (idempotente, sin memoria). */
    public static Phase phase(boolean faceSolid, boolean headSolid, boolean topSolid, boolean stepPlaced) {
        if (faceSolid || headSolid || topSolid) {
            return Phase.CLEAR;
        }
        return stepPlaced ? Phase.MOVE : Phase.PLACE;
    }

    /** El bloque del escalón solo se coloca si la cara quedó libre, hay soporte y no invade a la entidad. */
    public static boolean placeable(boolean faceAir, boolean headAir, boolean topAir, boolean support,
                                    boolean insideMobAabb, boolean protectedCell) {
        return faceAir && headAir && topAir && support && !insideMobAabb && !protectedCell;
    }

    /** El ascenso se consume solo con ocupación física verificada de la celda superior. */
    public static boolean landingVerified(boolean occupied, boolean grounded, boolean supported, boolean fits) {
        return occupied && grounded && supported && fits;
    }
}
