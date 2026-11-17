package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/** Regression for grounded DROP source resolution across an AABB/cell boundary. */
public final class NavSourceCellPolicyTest {
    private static int assertions;

    public static void main(String[] args) {
        BlockPos current = new BlockPos(0, 1, 0);
        BlockPos adjacent = new BlockPos(0, 1, 1);
        AABB overlapping = new AABB(0.2D, 1.0D, 0.66D, 0.8D, 2.95D, 1.26D);

        comprobar("AABB overlap selects the unique supported adjacent Z cell",
                adjacent.equals(NavSourceCellPolicy.uniqueAdjacentCandidate(
                        current, overlapping, adjacent::equals)));
        comprobar("integer source remains distinct from adjacent supported candidate",
                !adjacent.equals(current));
        comprobar("z=0.70 footprint only touches z=1 and must not select a candidate",
                NavSourceCellPolicy.uniqueAdjacentCandidate(current,
                        new AABB(0.2D, 1.0D, 0.40D, 0.8D, 2.95D, 1.00D),
                        adjacent::equals) == null);
        comprobar("z=0.95 footprint overlaps adjacent cell",
                adjacent.equals(NavSourceCellPolicy.uniqueAdjacentCandidate(current,
                        new AABB(0.2D, 1.0D, 0.65D, 0.8D, 2.95D, 1.25D),
                        adjacent::equals)));
        comprobar("z=0.96 footprint overlaps adjacent cell",
                adjacent.equals(NavSourceCellPolicy.uniqueAdjacentCandidate(current,
                        overlapping, adjacent::equals)));
        comprobar("unsupported overlapping cells are rejected",
                NavSourceCellPolicy.uniqueAdjacentCandidate(current, overlapping, cell -> false) == null);
        comprobar("candidate with blocked AABB is rejected by physical-valid predicate",
                NavSourceCellPolicy.uniqueAdjacentCandidate(current, overlapping,
                        cell -> adjacent.equals(cell) && false) == null);

        AABB ambiguousFootprint = new AABB(0.65D, 1.0D, 0.65D, 1.25D, 2.95D, 1.25D);
        comprobar("multiple valid adjacent cells are ambiguous and fail closed",
                NavSourceCellPolicy.uniqueAdjacentCandidate(current, ambiguousFootprint,
                        cell -> cell.equals(new BlockPos(1, 1, 0)) || cell.equals(adjacent)) == null);
        comprobar("candidate must be physically centered before source admission",
                NavSourceCellPolicy.centeredInCell(0.5D, 1.5D, adjacent, 0.20D));
        comprobar("boundary overlap is not physical center occupancy",
                !NavSourceCellPolicy.centeredInCell(0.5D, 1.01D, adjacent, 0.20D));

        System.out.println("NAV_SOURCE_CELL_POLICY_TEST: OK (" + assertions + " assertions)");
    }

    private static void comprobar(String name, boolean ok) {
        assertions++;
        if (!ok) throw new AssertionError(name);
    }
}
