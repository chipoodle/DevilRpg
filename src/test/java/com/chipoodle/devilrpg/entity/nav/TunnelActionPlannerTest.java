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
        System.out.println("TUNNEL_ACTION_PLANNER_TEST: OK (" + assertions + " assertions)");
    }
    private static void check(String name, boolean ok) {
        assertions++;
        if (!ok) throw new AssertionError(name);
    }
}
