package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

public final class NavStairAnchorPolicyTest {
    public static void main(String[] args) {
        BlockPos oldAnchor = new BlockPos(607, 93, 601);
        BlockPos higherSupport = new BlockPos(607, 94, 601);
        BlockPos sameLevelMovedSupport = new BlockPos(608, 93, 601);
        BlockPos lowerSupport = new BlockPos(608, 92, 601);

        comprobar("first stair chain seeds from current support",
                NavStairAnchorPolicy.refresh(oldAnchor, higherSupport, false).equals(higherSupport));
        comprobar("traversed anchor is preserved against upward support refresh",
                NavStairAnchorPolicy.refresh(oldAnchor, higherSupport, true).equals(oldAnchor));
        comprobar("same-level horizontal support movement is retained",
                NavStairAnchorPolicy.refresh(oldAnchor, sameLevelMovedSupport, true).equals(sameLevelMovedSupport));
        comprobar("lower real support advances the anchor downward",
                NavStairAnchorPolicy.refresh(oldAnchor, lowerSupport, true).equals(lowerSupport));
        comprobar("null support preserves the existing anchor",
                NavStairAnchorPolicy.refresh(oldAnchor, null, true).equals(oldAnchor));
        comprobar("next stair candidate must be strictly below traversed anchor",
                NavStairAnchorPolicy.isStrictlyBelow(new BlockPos(607, 92, 601), oldAnchor));
        comprobar("same-height candidate is rejected",
                !NavStairAnchorPolicy.isStrictlyBelow(oldAnchor, oldAnchor));
        comprobar("higher candidate is rejected",
                !NavStairAnchorPolicy.isStrictlyBelow(higherSupport, oldAnchor));
        System.out.println("NAV_STAIR_ANCHOR_POLICY_TEST: OK (8 assertions)");
    }

    private static void comprobar(String name, boolean ok) {
        if (!ok) throw new AssertionError(name);
    }
}
