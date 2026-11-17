package com.chipoodle.devilrpg.entity.nav;

/** Pure contract for a directed DROP edge: planned origin -> landing. */
public final class NavDropTransitionPolicy {
    public static final int MAX_CONTIGUIDAD = 1;

    private NavDropTransitionPolicy() {
    }

    public static boolean aterrizajeContiguo(int origenX, int origenZ, int landingX, int landingZ) {
        return Math.abs(landingX - origenX) <= MAX_CONTIGUIDAD
                && Math.abs(landingZ - origenZ) <= MAX_CONTIGUIDAD;
    }

    public static boolean origenCompatible(int liveX, int liveY, int liveZ,
                                            int origenX, int origenY, int origenZ) {
        return Math.abs(liveX - origenX) <= MAX_CONTIGUIDAD
                && Math.abs(liveY - origenY) <= MAX_CONTIGUIDAD
                && Math.abs(liveZ - origenZ) <= MAX_CONTIGUIDAD;
    }

    /**
     * True only when the mob is physically back on the planned take-off Y and within the
     * horizontal approach tolerance. The old proximity predicate allowed Y +/- 1, which
     * incorrectly treated a mob one block BELOW its take-off as ready to execute a DROP.
     */
    /** A DROP edge is executable only when its validated landing is strictly below take-off. */
    public static boolean descensoFisicoValido(int takeoffFeet, int landingFeet) {
        return landingFeet < takeoffFeet;
    }

    public static boolean origenFisicamenteAlcanzado(int liveX, int liveY, int liveZ,
                                                       int origenX, int origenY, int origenZ) {
        return Math.abs(liveX - origenX) <= MAX_CONTIGUIDAD
                && liveY == origenY
                && Math.abs(liveZ - origenZ) <= MAX_CONTIGUIDAD;
    }

    public static boolean ejecutable(boolean clearance, boolean support, boolean originCompatible,
                                     boolean landingContiguous, int dy, int maxDrop) {
        return clearance && support && originCompatible && landingContiguous
                && dy < 0 && -dy <= maxDrop;
    }
}
