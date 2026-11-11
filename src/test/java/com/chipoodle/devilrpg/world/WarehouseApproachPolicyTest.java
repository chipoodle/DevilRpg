package com.chipoodle.devilrpg.world;

import java.util.List;
import java.util.Optional;

public final class WarehouseApproachPolicyTest {

    private WarehouseApproachPolicyTest() {
    }

    private static WarehouseApproachPolicy.Candidate c(int x, int z, boolean standable, boolean inRange,
                                                       boolean reachable, double d) {
        return new WarehouseApproachPolicy.Candidate(x, 63, z, standable, inRange, reachable, d);
    }

    public static void main(String[] args) {
        WarehouseApproachPolicy.Candidate good = c(10, 10, true, true, true, 25);
        WarehouseApproachPolicy.Candidate nearButUnreachable = c(1, 1, true, true, false, 1);
        WarehouseApproachPolicy.Candidate outOfRange = c(2, 2, true, false, true, 2);
        WarehouseApproachPolicy.Candidate obstructed = c(3, 3, false, true, true, 3);

        check(WarehouseApproachPolicy.select(List.of(good)).orElseThrow().equals(good), "valid candidate selected");
        check(WarehouseApproachPolicy.select(List.of(outOfRange)).isEmpty(), "out-of-range rejected");
        check(WarehouseApproachPolicy.select(List.of(obstructed)).isEmpty(), "obstructed rejected");
        check(WarehouseApproachPolicy.select(List.of(nearButUnreachable)).isEmpty(), "unreachable rejected");
        check(WarehouseApproachPolicy.select(List.of(good, nearButUnreachable, outOfRange, obstructed))
                .orElseThrow().equals(good), "invalid candidate never displaces valid one");

        WarehouseApproachPolicy.Candidate farther = c(20, 20, true, true, true, 100);
        WarehouseApproachPolicy.Candidate nearer = c(15, 15, true, true, true, 50);
        check(WarehouseApproachPolicy.select(List.of(farther, nearer)).orElseThrow().equals(nearer),
                "nearest eligible wins regardless of list order");

        WarehouseApproachPolicy.Candidate tieA = c(7, 9, true, true, true, 10);
        WarehouseApproachPolicy.Candidate tieB = c(8, 9, true, true, true, 10);
        check(WarehouseApproachPolicy.select(List.of(tieB, tieA)).orElseThrow().equals(tieA),
                "ties rank deterministically by coordinates");

        check(WarehouseApproachPolicy.select(List.of()).isEmpty(), "no candidate is explicit empty");
        System.out.println("WAREHOUSE_APPROACH_POLICY_TEST: OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
