package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

public final class NavBreakGraphStateTest {
    public static void main(String[] args) {
        BlockPos start = new BlockPos(0, 64, 0);
        BlockPos stand = new BlockPos(1, 64, 1);
        BlockPos target = new BlockPos(1, 64, 0);
        BlockPos goal = new BlockPos(2, 64, 0);

        NavGraphSearch.Result result = NavGraphSearch.search(start.asLong(), 64,
                new NavGraphSearch.Graph() {
                    @Override
                    public void expand(long state, int feet,
                                       java.util.function.Consumer<NavGraphSearch.Edge> out) {
                        BlockPos current = BlockPos.of(state);
                        if (current.equals(start)) {
                            // A cheaper WALK can reach the same physical stand, but it does not
                            // clear the obstruction and therefore is not the same graph state.
                            out.accept(new NavGraphSearch.Edge(stand.asLong(), 1.0D,
                                    NavRoute.Transition.WALK, 64));
                            out.accept(new NavGraphSearch.Edge(target.asLong(), 12.0D,
                                    NavRoute.Transition.BREAK, 64, target, stand));
                        } else if (current.equals(target)) {
                            out.accept(new NavGraphSearch.Edge(goal.asLong(), 1.0D,
                                    NavRoute.Transition.WALK, 64));
                        }
                    }

                    @Override
                    public boolean goal(long state, int feet) {
                        return state == goal.asLong();
                    }

                    @Override
                    public double heuristic(long state) {
                        return Math.abs(BlockPos.of(state).getX() - goal.getX());
                    }

                    @Override
                    public double distance(long state) {
                        return heuristic(state);
                    }
                }, 32);

        check("goal found despite cheaper WALK-to-stand state", result.found());
        check("route contains post-break target state", result.route().size() == 2
                && result.route().get(0).state() == target.asLong());
        NavGraphSearch.Step breakStep = result.route().get(0);
        check("BREAK transition preserved", breakStep.transition() == NavRoute.Transition.BREAK);
        check("action target preserved", target.equals(breakStep.actionPos()));
        check("physical stand preserved separately", stand.equals(breakStep.standPos()));
        check("next edge starts after cleared target", result.route().get(1).state() == goal.asLong());
        System.out.println("NAV_BREAK_GRAPH_STATE_TEST: OK (6 assertions)");
    }

    private static void check(String name, boolean value) {
        if (!value) throw new AssertionError(name);
    }
}
