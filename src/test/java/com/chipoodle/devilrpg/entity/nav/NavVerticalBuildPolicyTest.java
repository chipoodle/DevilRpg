package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

/** Regression coverage for one-block ascent and sequential route-directed BUILD edges. */
public final class NavVerticalBuildPolicyTest {
    public static void main(String[] args) {
        check("level movement remains WALK", NavVerticalTransitionPolicy.ordinaryTransition(0) == NavRoute.Transition.WALK);
        check("one-block rise remains STEP_UP", NavVerticalTransitionPolicy.ordinaryTransition(1) == NavRoute.Transition.STEP_UP);
        check("two-block rise is not an ordinary transition", NavVerticalTransitionPolicy.ordinaryTransition(2) == null);
        check("three-block rise is not an ordinary transition", NavVerticalTransitionPolicy.ordinaryTransition(3) == null);
        check("ordinary ascent ceiling is exactly one", NavVerticalTransitionPolicy.MAX_ORDINARY_ASCENT == 1);
        check("BUILD rejects a two-block edge", !NavBuildTransitionPolicy.canBuildStep(true, 2, 3));
        check("BUILD rejects a three-block edge", !NavBuildTransitionPolicy.canBuildStep(true, 3, 3));
        check("BUILD rejects a non-placeable intermediate cell", !NavBuildTransitionPolicy.canBuildStep(false, 1, 3));
        check("BUILD retains existing safe-drop bound", !NavBuildTransitionPolicy.canBuildStep(true, -4, 3));

        BlockPos[] placements = {new BlockPos(11, 65, 10), new BlockPos(12, 66, 10), new BlockPos(13, 67, 10)};
        int previousFeetY = 65;
        for (int i = 0; i < placements.length; i++) {
            BlockPos feet = NavBuildTransitionPolicy.successorFeet(placements[i]);
            check("BUILD chain step " + (i + 1) + " rises exactly one block",
                    feet.getY() - previousFeetY == 1
                            && NavBuildTransitionPolicy.canBuildStep(true, feet.getY() - previousFeetY, 3));
            check("BUILD chain step " + (i + 1) + " keeps placement below feet", feet.below().equals(placements[i]));
            previousFeetY = feet.getY();
        }
        check("three BUILD steps represent a +3 rise without a +3 edge", previousFeetY == 68);

        check("placement is attempted before it is verified", NavBuildTransitionPolicy.shouldPlace(false));
        check("verified placement is not repeated", !NavBuildTransitionPolicy.shouldPlace(true));
        check("BUILD arrival requires all physical predicates",
                NavBuildTransitionPolicy.physicallyArrived(true, true, true, true, true));
        check("proximity without exact feet occupancy is rejected",
                !NavBuildTransitionPolicy.physicallyArrived(true, false, true, true, true));
        check("unverified placement is rejected",
                !NavBuildTransitionPolicy.physicallyArrived(false, true, true, true, true));
        check("airborne arrival is rejected",
                !NavBuildTransitionPolicy.physicallyArrived(true, true, false, true, true));
        check("unsupported arrival is rejected",
                !NavBuildTransitionPolicy.physicallyArrived(true, true, true, false, true));
        check("real AABB collision is rejected",
                !NavBuildTransitionPolicy.physicallyArrived(true, true, true, true, false));

        System.out.println("NAV_VERTICAL_BUILD_POLICY_TEST: OK (24 assertions)");
    }

    private static void check(String name, boolean value) {
        if (!value) throw new AssertionError(name);
    }
}
