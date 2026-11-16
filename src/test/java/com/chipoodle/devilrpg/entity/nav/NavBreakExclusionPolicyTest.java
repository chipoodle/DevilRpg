package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

public final class NavBreakExclusionPolicyTest {
    public static void main(String[] args) {
        NavBreakExclusionPolicy policy = new NavBreakExclusionPolicy();
        BlockPos standA = new BlockPos(10, 64, 10);
        BlockPos standB = new BlockPos(11, 64, 10);
        BlockPos target = new BlockPos(10, 64, 11);
        policy.exclude(standA, target, 100L);
        check("same stand-target edge excluded", policy.excluded(standA, target, 100L));
        check("different approach side remains available", !policy.excluded(standB, target, 100L));
        check("changed terrain re-enables failed edge", !policy.excluded(standA, target, 101L));
        check("stale fingerprint is removed", !policy.excluded(standA, target, 100L));
        policy.exclude(standA, target, 100L);
        policy.resolve(standA, target);
        check("verified action clears its exclusion", !policy.excluded(standA, target, 100L));
        for (int i = 0; i < 40; i++) {
            policy.exclude(new BlockPos(i, 64, 0), target, i);
        }
        check("memory remains bounded", policy.size() <= NavBreakExclusionPolicy.MAX_ENTRIES);
        System.out.println("NAV_BREAK_EXCLUSION_POLICY_TEST: OK (6 assertions)");
    }

    private static void check(String name, boolean value) {
        if (!value) throw new AssertionError(name);
    }
}
