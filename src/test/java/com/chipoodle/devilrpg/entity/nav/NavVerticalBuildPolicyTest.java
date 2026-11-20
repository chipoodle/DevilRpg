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

        NavGraphSearch.Result searchedChain = searchBuildChain(true);
        check("A* finds the three-edge BUILD chain", searchedChain.found() && searchedChain.route().size() == 3);
        for (int i = 0; i < searchedChain.route().size(); i++) {
            NavGraphSearch.Step step = searchedChain.route().get(i);
            BlockPos feet = BlockPos.of(step.state());
            check("A* BUILD edge " + (i + 1) + " retains the placement action below its feet state",
                    step.transition() == NavRoute.Transition.BUILD && step.actionPos() != null
                            && feet.equals(step.actionPos().above()));
            check("A* BUILD edge " + (i + 1) + " rises exactly one",
                    step.feet() - (65 + i) == 1 && feet.getY() == 66 + i);
        }
        check("A* rejects a chain with a non-buildable intermediate", !searchBuildChain(false).found());

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

        System.out.println("NAV_VERTICAL_BUILD_POLICY_TEST: OK (32 assertions)");
    }

    private static NavGraphSearch.Result searchBuildChain(boolean secondStepPlaceable) {
        BlockPos start = new BlockPos(10, 65, 10);
        BlockPos goal = new BlockPos(13, 68, 10);
        NavGraphSearch.Graph graph = new NavGraphSearch.Graph() {
            @Override
            public void expand(long state, int feet, java.util.function.Consumer<NavGraphSearch.Edge> out) {
                BlockPos from = BlockPos.of(state);
                if (from.getX() >= goal.getX()) return;
                BlockPos placement = new BlockPos(from.getX() + 1, feet, from.getZ());
                boolean placeable = from.getX() != 11 || secondStepPlaceable;
                if (!NavBuildTransitionPolicy.canBuildStep(placeable, 1, 3)) return;
                BlockPos successorFeet = NavBuildTransitionPolicy.successorFeet(placement);
                out.accept(new NavGraphSearch.Edge(successorFeet.asLong(), 1.0D,
                        NavRoute.Transition.BUILD, successorFeet.getY(), placement, null));
            }
            @Override public boolean goal(long state, int feet) {
                return BlockPos.of(state).equals(goal) && feet == goal.getY();
            }
            @Override public double heuristic(long state) {
                return goal.getX() - BlockPos.of(state).getX();
            }
            @Override public double distance(long state) {
                return heuristic(state);
            }
        };
        return NavGraphSearch.search(start.asLong(), start.getY(), graph, 16);
    }

    private static void check(String name, boolean value) {
        if (!value) throw new AssertionError(name);
    }
}
