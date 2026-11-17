package com.chipoodle.devilrpg.entity.nav;

/** Regression for DROP take-off recovery: proximity below/above the planned Y is not take-off occupancy. */
public final class NavDropTakeoffPolicyTest {
    private static int assertions;

    public static void main(String[] args) {
        comprobar("same takeoff Y and adjacent X/Z is accepted",
                NavDropTransitionPolicy.origenFisicamenteAlcanzado(596, 78, 498, 597, 78, 499));
        comprobar("one block below takeoff must recover upward",
                !NavDropTransitionPolicy.origenFisicamenteAlcanzado(596, 77, 498, 597, 78, 499));
        comprobar("one block above takeoff is not the planned takeoff",
                !NavDropTransitionPolicy.origenFisicamenteAlcanzado(596, 79, 498, 597, 78, 499));
        comprobar("horizontal distance beyond adjacency is rejected",
                !NavDropTransitionPolicy.origenFisicamenteAlcanzado(595, 78, 498, 597, 78, 499));
        comprobar("legacy proximity remains broader than physical occupancy",
                NavDropTransitionPolicy.origenCompatible(596, 77, 498, 597, 78, 499)
                        && !NavDropTransitionPolicy.origenFisicamenteAlcanzado(596, 77, 498, 597, 78, 499));

        comprobar("landing one block below takeoff is a valid DROP",
                NavDropTransitionPolicy.descensoFisicoValido(78, 77));
        comprobar("landing at takeoff height is not a DROP",
                !NavDropTransitionPolicy.descensoFisicoValido(78, 78));
        comprobar("landing above takeoff is not a DROP",
                !NavDropTransitionPolicy.descensoFisicoValido(77, 78));
        comprobar("landing below takeoff by safe multi-block fall remains a DROP",
                NavDropTransitionPolicy.descensoFisicoValido(82, 78));

        net.minecraft.core.BlockPos routeStart = new net.minecraft.core.BlockPos(596, 79, 498);
        net.minecraft.core.BlockPos previous = new net.minecraft.core.BlockPos(596, 80, 498);
        comprobar("first route DROP uses the recorded route start as take-off origin",
                routeStart.equals(NavDropTransitionPolicy.origenPlanificado(0, routeStart, null)));
        comprobar("later DROP uses its immediate predecessor, not the route start",
                previous.equals(NavDropTransitionPolicy.origenPlanificado(2, routeStart, previous)));
        comprobar("first route DROP without a recorded start has no invented origin",
                NavDropTransitionPolicy.origenPlanificado(0, null, previous) == null);

        System.out.println("NAV_DROP_TAKEOFF_POLICY_TEST: OK (" + assertions + " assertions)");
    }

    private static void comprobar(String name, boolean ok) {
        assertions++;
        if (!ok) throw new AssertionError(name);
    }
}
