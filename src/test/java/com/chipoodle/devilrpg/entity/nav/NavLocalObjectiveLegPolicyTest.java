package com.chipoodle.devilrpg.entity.nav;

/** A2 · tramo local acotado: se conserva el waypoint válido más lejano DENTRO del tramo. */
public final class NavLocalObjectiveLegPolicyTest {
    public static void main(String[] args) {
        check("leg accepts inside", NavLocalObjectiveLegPolicy.withinLeg(24, 0, 24)
                && NavLocalObjectiveLegPolicy.withinLeg(0, -24, 24) && NavLocalObjectiveLegPolicy.withinLeg(13, 17, 24));
        check("leg rejects outside", !NavLocalObjectiveLegPolicy.withinLeg(25, 0, 24)
                && !NavLocalObjectiveLegPolicy.withinLeg(0, -44, 24));
        check("leg is bounded to half the planning window",
                NavLocalObjectiveLegPolicy.MAX_LEG_CHEBYSHEV == 24);
        check("leg rejects negative or zero bounds safely",
                !NavLocalObjectiveLegPolicy.withinLeg(1, 0, 0) && !NavLocalObjectiveLegPolicy.withinLeg(1, 0, -5));

        // Secuencia ordenada por cercanía al mob (0 = más cercano).
        int[] chebyshev = {1, 2, 6, 12, 20, 26, 39, 44, 58};
        boolean[] usable = {false, true, true, false, true, true, true, true, true};
        int chosen = NavLocalObjectiveLegPolicy.selectIndex(chebyshev, usable, 24);
        check("chooses the farthest valid node inside the leg", chosen == 4);
        int[] farOnlyDistances = {40, 41, 42, 43, 44, 45};
        boolean[] onlyFar = {false, false, false, false, false, true};
        check("falls back to the nearest valid node when none fit the leg",
                NavLocalObjectiveLegPolicy.selectIndex(farOnlyDistances, onlyFar, 24) == 5);
        boolean[] none = new boolean[chebyshev.length];
        check("no usable node yields -1", NavLocalObjectiveLegPolicy.selectIndex(chebyshev, none, 24) == -1);
        check("mismatched inputs are rejected",
                NavLocalObjectiveLegPolicy.selectIndex(new int[]{1, 2}, new boolean[]{true}, 24) == -1);
        check("idx3 measured corner leg is outside", !NavLocalObjectiveLegPolicy.withinLeg(39, 44, 24));
        System.out.println("NAV_LOCAL_OBJECTIVE_LEG_POLICY_TEST: OK (" + assertions + " assertions)");
    }

    private static int assertions;

    private static void check(String name, boolean ok) {
        assertions++;
        if (!ok) throw new AssertionError(name);
    }
}
