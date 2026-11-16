package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

public final class NavBreakAttackPositionPolicyTest {
    public static void main(String[] args) {
        BlockPos from = new BlockPos(10, 64, 10);
        check("cardinal adjacent stand",
                NavBreakAttackPositionPolicy.isSingleStepReachable(from, new BlockPos(10, 64, 11), 64));
        check("diagonal adjacent stand",
                NavBreakAttackPositionPolicy.isSingleStepReachable(from, new BlockPos(11, 64, 11), 64));
        check("same cell rejected",
                !NavBreakAttackPositionPolicy.isSingleStepReachable(from, from, 64));
        check("two-cell stand rejected",
                !NavBreakAttackPositionPolicy.isSingleStepReachable(from, new BlockPos(12, 64, 10), 64));
        check("vertical mismatch rejected",
                !NavBreakAttackPositionPolicy.isSingleStepReachable(from, new BlockPos(10, 65, 11), 64));
        check("predecessor feet mismatch rejected",
                !NavBreakAttackPositionPolicy.isSingleStepReachable(from, new BlockPos(10, 64, 11), 63));
        check("surrounding candidates exclude predecessor",
                NavBreakAttackPositionPolicy.candidates(new BlockPos(11, 64, 10), from).size() == 7);
        System.out.println("NAV_BREAK_ATTACK_POSITION_POLICY_TEST: OK (7 assertions)");
    }

    private static void check(String name, boolean value) {
        if (!value) throw new AssertionError(name);
    }
}
