package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

public final class NavRouteNodePolicyTest {
    public static void main(String[] args) {
        BlockPos dropState = new BlockPos(597, 78, 499);
        BlockPos landing = NavRouteNodePolicy.routePosition(
                dropState, NavRoute.Transition.DROP, 77, null);
        check("DROP route node uses validated landing feet",
                landing.equals(new BlockPos(597, 77, 499)));
        check("DROP preserves searched horizontal column",
                landing.getX() == dropState.getX() && landing.getZ() == dropState.getZ());

        BlockPos walkState = new BlockPos(12, 64, -8);
        check("WALK keeps canonical graph state",
                NavRouteNodePolicy.routePosition(walkState, NavRoute.Transition.WALK, 63, null)
                        .equals(walkState));
        check("STEP_UP keeps canonical successor state",
                NavRouteNodePolicy.routePosition(walkState, NavRoute.Transition.STEP_UP, 65, null)
                        .equals(walkState));

        BlockPos buildFeet = new BlockPos(13, 66, -8);
        BlockPos buildPlacement = buildFeet.below();
        check("BUILD route position is the resulting feet cell",
                NavRouteNodePolicy.routePosition(buildFeet, NavRoute.Transition.BUILD, 66, null)
                        .equals(buildFeet));
        NavRoute.Node buildNode = new NavRoute.Node(buildFeet, NavRoute.Transition.BUILD, buildPlacement);
        check("BUILD action target remains the placement cell below feet",
                buildNode.actionTarget().equals(buildPlacement) && buildNode.standPos().equals(buildFeet));

        BlockPos breakState = new BlockPos(20, 70, 20);
        BlockPos breakStand = new BlockPos(19, 70, 20);
        check("BREAK keeps physical attack stand separate from graph state",
                NavRouteNodePolicy.routePosition(breakState, NavRoute.Transition.BREAK, 70, breakStand)
                        .equals(breakStand));
        check("BREAK without stand retains existing safe fallback",
                NavRouteNodePolicy.routePosition(breakState, NavRoute.Transition.BREAK, 70, null)
                        .equals(breakState));

        System.out.println("NAV_ROUTE_NODE_POLICY_TEST: OK (8 assertions)");
    }

    private static void check(String name, boolean value) {
        if (!value) {
            throw new AssertionError(name);
        }
    }
}
