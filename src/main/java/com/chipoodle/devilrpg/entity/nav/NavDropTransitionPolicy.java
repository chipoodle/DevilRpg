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

    public static boolean ejecutable(boolean clearance, boolean support, boolean originCompatible,
                                     boolean landingContiguous, int dy, int maxDrop) {
        return clearance && support && originCompatible && landingContiguous
                && dy < 0 && -dy <= maxDrop;
    }
}
