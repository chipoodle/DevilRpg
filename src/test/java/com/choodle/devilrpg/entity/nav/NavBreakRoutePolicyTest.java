package com.choodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

import java.util.List;

public final class NavBreakRoutePolicyTest {
    public static void main(String[] args) {
        BlockPos start = new BlockPos(0, 64, 0);
        BlockPos stand = new BlockPos(1, 64, 1);
        BlockPos target = new BlockPos(1, 64, 0);
        BlockPos goal = new BlockPos(2, 64, 0);
        NavRoute original = new NavRoute(List.of(
                new NavRoute.Node(start, NavRoute.Transition.WALK),
                new NavRoute.Node(stand, NavRoute.Transition.WALK)), stand);

        NavRoute converted = NavBreakRoutePolicy.attachFinalBreak(original, stand, target);
        check("route converted", converted != null);
        check("prefix node preserved", converted.nodes().get(0).equals(original.nodes().get(0)));
        check("final transition is BREAK", converted.nodes().get(1).transition() == NavRoute.Transition.BREAK);
        check("stand remains movement position", converted.nodes().get(1).standPos().equals(stand));
        check("solid target remains separate", converted.nodes().get(1).actionTarget().equals(target));
        check("destination remains stand", converted.destination().equals(stand));
        check("empty route rejected", NavBreakRoutePolicy.attachFinalBreak(
                new NavRoute(List.of(), stand), stand, target) == null);
        check("wrong final stand rejected", NavBreakRoutePolicy.attachFinalBreak(original, goal, target) == null);
        check("same-cell action rejected", NavBreakRoutePolicy.attachFinalBreak(original, stand, stand) == null);
        check("null action rejected", NavBreakRoutePolicy.attachFinalBreak(original, stand, null) == null);
        System.out.println("NAV_BREAK_ROUTE_POLICY_TEST: OK (10 assertions)");
    }

    private static void check(String name, boolean value) {
        if (!value) throw new AssertionError(name);
    }
}
