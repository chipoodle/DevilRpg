package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

/** Regression for supported one-cell DROP approach selection. */
public final class NavDropApproachPolicyTest {
    private static int assertions;

    public static void main(String[] args) {
        BlockPos landing = new BlockPos(10, 60, 10);
        comprobar("adjacent cell is already the approach",
                NavDropApproachPolicy.choose(new BlockPos(9, 63, 10), landing, c -> true)
                        .equals(new BlockPos(9, 63, 10)));
        comprobar("two cells away selects supported intermediate",
                NavDropApproachPolicy.choose(new BlockPos(8, 63, 10), landing, c -> true)
                        .equals(new BlockPos(9, 63, 10)));
        comprobar("diagonal adjacency is allowed",
                NavDropApproachPolicy.choose(new BlockPos(9, 63, 9), landing, c -> true)
                        .equals(new BlockPos(9, 63, 9)));
        comprobar("invalid intermediate is skipped for valid diagonal",
                NavDropApproachPolicy.choose(new BlockPos(8, 63, 10), landing,
                        c -> c.equals(new BlockPos(9, 63, 9)))
                        .equals(new BlockPos(9, 63, 9)));
        comprobar("no safe cell fails closed",
                NavDropApproachPolicy.choose(new BlockPos(8, 63, 10), landing, c -> false) == null);
        comprobar("null current fails closed",
                NavDropApproachPolicy.choose(null, landing, c -> true) == null);
        comprobar("null landing fails closed",
                NavDropApproachPolicy.choose(new BlockPos(8, 63, 10), null, c -> true) == null);
        comprobar("null predicate fails closed",
                NavDropApproachPolicy.choose(new BlockPos(8, 63, 10), landing, null) == null);
        comprobar("approach never selects a farther cell",
                NavDropApproachPolicy.choose(new BlockPos(13, 63, 10), landing, c -> true)
                        .equals(new BlockPos(12, 63, 10)));
        comprobar("same column is already adjacent horizontally",
                NavDropApproachPolicy.choose(new BlockPos(10, 63, 10), landing, c -> true)
                        .equals(new BlockPos(10, 63, 10)));

        System.out.println("NAV_DROP_APPROACH_POLICY_TEST: OK (" + assertions + " assertions)");
    }

    private static void comprobar(String name, boolean ok) {
        assertions++;
        if (!ok) throw new AssertionError(name);
    }
}
