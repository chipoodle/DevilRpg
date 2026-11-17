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

        System.out.println("NAV_DROP_TAKEOFF_POLICY_TEST: OK (" + assertions + " assertions)");
    }

    private static void comprobar(String name, boolean ok) {
        assertions++;
        if (!ok) throw new AssertionError(name);
    }
}
