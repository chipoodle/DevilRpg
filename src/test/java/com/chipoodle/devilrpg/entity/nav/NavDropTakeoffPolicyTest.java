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

        net.minecraft.core.BlockPos stepOne = new net.minecraft.core.BlockPos(596, 80, 498);
        net.minecraft.core.BlockPos stepTwo = new net.minecraft.core.BlockPos(596, 81, 498);
        net.minecraft.core.BlockPos takeoff = new net.minecraft.core.BlockPos(596, 82, 498);
        NavRoute safeClimb = new NavRoute(java.util.List.of(
                new NavRoute.Node(stepOne, NavRoute.Transition.STEP_UP),
                new NavRoute.Node(stepTwo, NavRoute.Transition.STEP_UP),
                new NavRoute.Node(takeoff, NavRoute.Transition.WALK)), takeoff);
        comprobar("vertical take-off recovery accepts a complete walk/step-up route",
                NavDropTransitionPolicy.rutaAscensoOrigenSegura(safeClimb, takeoff));
        NavRoute unsafeDrop = new NavRoute(java.util.List.of(
                new NavRoute.Node(stepOne, NavRoute.Transition.DROP),
                new NavRoute.Node(takeoff, NavRoute.Transition.WALK)), takeoff);
        comprobar("vertical take-off recovery rejects routes containing DROP",
                !NavDropTransitionPolicy.rutaAscensoOrigenSegura(unsafeDrop, takeoff));
        NavRoute unsafeBreak = new NavRoute(java.util.List.of(
                new NavRoute.Node(stepOne, NavRoute.Transition.BREAK),
                new NavRoute.Node(takeoff, NavRoute.Transition.WALK)), takeoff);
        comprobar("vertical take-off recovery rejects destructive routes",
                !NavDropTransitionPolicy.rutaAscensoOrigenSegura(unsafeBreak, takeoff));
        comprobar("vertical take-off recovery rejects partial routes to another destination",
                !NavDropTransitionPolicy.rutaAscensoOrigenSegura(safeClimb, previous));

        comprobar("STEP_UP assist accepts one adjacent supported climb",
                NavStepUpRecoveryPolicy.canAssist(0, 1, -1, true, true, true, true));
        comprobar("STEP_UP assist rejects same-column vertical impulse",
                !NavStepUpRecoveryPolicy.canAssist(0, 1, 0, true, true, true, true));
        comprobar("STEP_UP assist rejects multi-block recovery",
                !NavStepUpRecoveryPolicy.canAssist(0, 3, -1, true, true, true, true));
        comprobar("STEP_UP assist rejects missing obstruction, headroom, fit or support",
                !NavStepUpRecoveryPolicy.canAssist(1, 1, 0, false, true, true, true)
                        && !NavStepUpRecoveryPolicy.canAssist(1, 1, 0, true, false, true, true)
                        && !NavStepUpRecoveryPolicy.canAssist(1, 1, 0, true, true, false, true)
                        && !NavStepUpRecoveryPolicy.canAssist(1, 1, 0, true, true, true, false));

        comprobar("expansion budget permits the first expansion",
                NavExpansionBudgetPolicy.canExpand(0, 900));
        comprobar("expansion budget permits expansion 900",
                NavExpansionBudgetPolicy.canExpand(899, 900));
        comprobar("expansion budget rejects expansion 901",
                !NavExpansionBudgetPolicy.canExpand(900, 900));
        comprobar("expansion budget rejects any expansion after the limit",
                !NavExpansionBudgetPolicy.canExpand(901, 900));
        comprobar("zero expansion budget permits no work",
                !NavExpansionBudgetPolicy.canExpand(0, 0));

        System.out.println("NAV_DROP_TAKEOFF_POLICY_TEST: OK (" + assertions + " assertions)");
    }

    private static void comprobar(String name, boolean ok) {
        assertions++;
        if (!ok) throw new AssertionError(name);
    }
}
