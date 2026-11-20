package com.chipoodle.devilrpg.entity.nav;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class NavNeighborPolicyTest {
    public static void main(String[] args) {
        int[][] offsets = NavNeighborPolicy.offsets();
        check("eight horizontal neighbours plus one same-column direction",
                offsets.length == 9 && NavNeighborPolicy.offsetCount() == 9);

        Set<String> actual = new HashSet<>();
        int verticalOnly = 0;
        int cardinal = 0;
        int diagonal = 0;
        for (int[] offset : offsets) {
            check("every offset is a two-coordinate direction", offset.length == 2);
            actual.add(offset[0] + "," + offset[1]);
            if (offset[0] == 0 && offset[1] == 0) verticalOnly++;
            else if (Math.abs(offset[0]) + Math.abs(offset[1]) == 1) cardinal++;
            else if (Math.abs(offset[0]) == 1 && Math.abs(offset[1]) == 1) diagonal++;
        }

        check("same-column vertical probe is present", actual.contains("0,0"));
        check("all four cardinal directions remain", cardinal == 4
                && actual.contains("1,0") && actual.contains("-1,0")
                && actual.contains("0,1") && actual.contains("0,-1"));
        check("all four diagonals remain", diagonal == 4
                && actual.contains("1,1") && actual.contains("1,-1")
                && actual.contains("-1,1") && actual.contains("-1,-1"));
        check("there is exactly one vertical-only direction", verticalOnly == 1);
        check("directions are unique", actual.size() == offsets.length);

        offsets[0][0] = 99;
        check("returned offsets cannot mutate the canonical policy",
                Arrays.stream(NavNeighborPolicy.offsets()).noneMatch(o -> o[0] == 99));

        System.out.println("NAV_NEIGHBOR_POLICY_TEST: OK (16 assertions)");
    }

    private static void check(String name, boolean value) {
        if (!value) throw new AssertionError(name);
    }
}
