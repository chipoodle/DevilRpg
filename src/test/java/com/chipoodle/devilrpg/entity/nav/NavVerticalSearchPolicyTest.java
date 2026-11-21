package com.chipoodle.devilrpg.entity.nav;

import java.util.Arrays;

public final class NavVerticalSearchPolicyTest {
    private static int assertions;
    public static void main(String[] args) {
        check("horizontal probes descend safely and rise one",
                Arrays.equals(NavVerticalSearchPolicy.levels(false, 3, 3),
                        new int[]{-3, -2, -1, 0, 1}));
        check("same-column probes descend only",
                Arrays.equals(NavVerticalSearchPolicy.levels(true, 3, 3),
                        new int[]{-1, -2, -3}));
        check("safe-drop limit caps descent",
                Arrays.equals(NavVerticalSearchPolicy.levels(false, 6, 1),
                        new int[]{-1, 0, 1}));
        check("zero vertical range keeps only the same level",
                Arrays.equals(NavVerticalSearchPolicy.levels(false, 0, 3),
                        new int[]{0}));
        check("zero safe drop removes descent",
                Arrays.equals(NavVerticalSearchPolicy.levels(false, 3, 0),
                        new int[]{0, 1}));
        System.out.println("NAV_VERTICAL_SEARCH_POLICY_TEST: OK (" + assertions + " assertions)");
    }
    private static void check(String name, boolean ok) {
        assertions++;
        if (!ok) throw new AssertionError(name);
    }
}
