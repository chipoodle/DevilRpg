package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

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

    /** A DROP edge is executable only when its validated landing is strictly below take-off. */
    public static boolean descensoFisicoValido(int takeoffFeet, int landingFeet) {
        return landingFeet < takeoffFeet;
    }

    /**
     * Resolves the take-off origin for the DROP currently being executed. The first route node
     * has no predecessor in the node list, so its origin is the exact position from which the
     * route was planned. Later DROP nodes must use their immediate predecessor instead.
     */
    public static BlockPos origenPlanificado(int routeIndex, BlockPos routeStart, BlockPos previousNode) {
        if (routeIndex == 0) {
            return routeStart == null ? null : routeStart.immutable();
        }
        return previousNode == null ? null : previousNode.immutable();
    }

    /**
     * A temporary route used to recover a DROP take-off may walk or climb, but must never
     * execute destructive, bridging, building, or falling transitions. The destination must
     * be the exact requested take-off cell; proximity or a partial route is not sufficient.
     */
    public static boolean rutaAscensoOrigenSegura(NavRoute route, BlockPos takeoff) {
        if (route == null || route.isEmpty() || takeoff == null || !takeoff.equals(route.destination())) {
            return false;
        }
        for (NavRoute.Node node : route.nodes()) {
            if (node == null || (node.transition() != NavRoute.Transition.WALK
                    && node.transition() != NavRoute.Transition.STEP_UP
                    && node.transition() != NavRoute.Transition.JUMP_UP)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validates the temporary route from a resolved source cell back to the original DROP take-off.
     * It may contain ordinary walking/climbing and at most one planner-validated, exactly one-block
     * descent. Longer/multiple drops and destructive/building transitions remain forbidden.
     */
    public static boolean rutaRecuperacionOrigenSegura(NavRoute route, BlockPos start, BlockPos takeoff) {
        if (route == null || route.isEmpty() || start == null || takeoff == null
                || !takeoff.equals(route.destination())
                || !takeoff.equals(route.nodes().get(route.nodes().size() - 1).pos())) {
            return false;
        }
        int previousY = start.getY();
        int drops = 0;
        for (NavRoute.Node node : route.nodes()) {
            if (node == null) {
                return false;
            }
            int deltaY = node.pos().getY() - previousY;
            switch (node.transition()) {
                case WALK -> {
                    if (deltaY != 0) return false;
                }
                case STEP_UP -> {
                    if (deltaY != 1) return false;
                }
                case JUMP_UP -> {
                    if (deltaY != 2) return false;
                }
                case DROP -> {
                    if (++drops > 1 || deltaY != -1) return false;
                }
                default -> {
                    return false;
                }
            }
            previousY = node.pos().getY();
        }
        return true;
    }

    /**
     * True only when the mob is physically back on the planned take-off Y and within the
     * horizontal approach tolerance. The old proximity predicate allowed Y +/- 1, which
     * incorrectly treated a mob one block BELOW its take-off as ready to execute a DROP.
     */
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
