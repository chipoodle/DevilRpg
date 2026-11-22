package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

import java.util.List;

public final class TunnelActionPlannerTest {
    private static int assertions;
    public static void main(String[] args) {
        BlockPos start = new BlockPos(10, 64, 10);
        List<TunnelActionPlanner.Step> plan = TunnelActionPlanner.straight(start, 1, 0, 3,
                p -> true, p -> false, p -> true);
        check("bounded three-step tunnel", plan.size() == 3);
        check("first stand is origin", plan.get(0).stand().equals(start));
        check("steps advance one block", plan.get(1).stand().equals(start.east())
                && plan.get(2).stand().equals(start.east(2)));
        check("feet and head pair", plan.get(0).feetBlock().equals(start.east())
                && plan.get(0).headBlock().equals(start.east().above()));
        check("post is the opened feet cell", plan.get(0).post().equals(start.east()));
        check("protected stops the tunnel", TunnelActionPlanner.straight(start, 1, 0, 3,
                p -> true, p -> p.equals(start.east()), p -> true).isEmpty());
        check("unbreakable stops the tunnel", TunnelActionPlanner.straight(start, 1, 0, 3,
                p -> p.getX() < 12, p -> false, p -> true).size() == 1);
        check("diagonal and zero directions are rejected", TunnelActionPlanner.straight(
                start, 1, 1, 3, p -> true, p -> false, p -> true).isEmpty()
                && TunnelActionPlanner.straight(start, 0, 0, 3, p -> true, p -> false, p -> true).isEmpty());
        check("unsupported front stops the tunnel", TunnelActionPlanner.straight(start, 1, 0, 3,
                p -> true, p -> false, p -> false).isEmpty());

        // A2 · límite de pasos y contrato 2-high completo, paso a paso.
        check("one-step bound is honoured", TunnelActionPlanner.straight(start, 1, 0, 1,
                p -> true, p -> false, p -> true).size() == 1);
        check("large bound is honoured without truncation", TunnelActionPlanner.straight(start, 1, 0, 64,
                p -> true, p -> false, p -> true).size() == 64);
        check("every step keeps a 2-high feet/head pair immediately ahead of its stand",
                everyStepIsTwoHigh(plan));
        check("every post equals its opened feet cell and keeps support",
                plan.stream().allMatch(s -> s.post().equals(s.feetBlock())));
        check("western cardinal direction advances west", TunnelActionPlanner.straight(start, -1, 0, 2,
                p -> true, p -> false, p -> true).get(1).stand().equals(start.west()));
        check("northern cardinal direction advances north", TunnelActionPlanner.straight(start, 0, -1, 2,
                p -> true, p -> false, p -> true).get(1).stand().equals(start.north()));
        check("southern cardinal direction advances south", TunnelActionPlanner.straight(start, 0, 1, 2,
                p -> true, p -> false, p -> true).get(1).stand().equals(start.south()));
        check("protected HEAD stops the plan", TunnelActionPlanner.straight(start, 1, 0, 3,
                p -> true, p -> p.equals(start.east(2).above()), p -> true).size() == 1);
        check("unsupported later front truncates the plan", TunnelActionPlanner.straight(start, 1, 0, 3,
                p -> true, p -> false, p -> p.getX() < 12).size() == 1);
        check("invalid max steps is rejected", TunnelActionPlanner.straight(start, 1, 0, 0,
                p -> true, p -> false, p -> true).isEmpty());
        check("null start is rejected", TunnelActionPlanner.straight(null, 1, 0, 3,
                p -> true, p -> false, p -> true).isEmpty());

        System.out.println("TUNNEL_ACTION_PLANNER_TEST: OK (" + assertions + " assertions)");
    }

    private static boolean everyStepIsTwoHigh(List<TunnelActionPlanner.Step> plan) {
        for (int i = 0; i < plan.size(); i++) {
            TunnelActionPlanner.Step s = plan.get(i);
            BlockPos expectedFront = s.stand().east();
            if (!s.feetBlock().equals(expectedFront) || !s.headBlock().equals(expectedFront.above())) {
                return false;
            }
            if (i > 0 && !s.stand().equals(plan.get(i - 1).post())) {
                return false;
            }
        }
        return true;
    }

    private static void check(String name, boolean ok) {
        assertions++;
        if (!ok) throw new AssertionError(name);
    }
}
