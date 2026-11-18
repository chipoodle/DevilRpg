package com.chipoodle.devilrpg.entity.nav;

/** Deterministic tests for bounded non-adjacent DROP approach ownership. */
public final class NavDropApproachOwnershipPolicyTest {
    private static int assertions;

    public static void main(String[] args) {
        comprobar("only DROP can transfer ownership",
                !NavDropApproachOwnershipPolicy.shouldTakeOwnership(false, true, false, 0));
        comprobar("active Navigation path keeps ownership",
                !NavDropApproachOwnershipPolicy.shouldTakeOwnership(true, false, false, 0));
        comprobar("completed path transfers ownership",
                NavDropApproachOwnershipPolicy.shouldTakeOwnership(true, true, false, 0));
        comprobar("physical arrival prevents transfer",
                !NavDropApproachOwnershipPolicy.shouldTakeOwnership(true, true, true, 30));
        comprobar("stalled path transfers ownership",
                NavDropApproachOwnershipPolicy.shouldTakeOwnership(true, false, false, 21));
        comprobar("progress threshold does not transfer",
                !NavDropApproachOwnershipPolicy.shouldTakeOwnership(true, false, false, 20));
        comprobar("timeout remains bounded at limit",
                !NavDropApproachOwnershipPolicy.timedOut(80));
        comprobar("timeout fires beyond limit",
                NavDropApproachOwnershipPolicy.timedOut(81));
        comprobar("no-progress remains bounded at limit",
                !NavDropApproachOwnershipPolicy.noProgress(20));
        comprobar("no-progress fires beyond limit",
                NavDropApproachOwnershipPolicy.noProgress(21));
        comprobar("measurable distance reduction is progress",
                NavDropApproachOwnershipPolicy.madeProgress(1.0D, 1.02D));
        comprobar("sub-threshold distance reduction is not progress",
                !NavDropApproachOwnershipPolicy.madeProgress(1.01D, 1.02D));
        comprobar("non-finite distance is not progress",
                !NavDropApproachOwnershipPolicy.madeProgress(Double.NaN, 2.0D));

        System.out.println("NAV_DROP_APPROACH_OWNERSHIP_POLICY_TEST: OK (" + assertions + " assertions)");
    }

    private static void comprobar(String name, boolean ok) {
        assertions++;
        if (!ok) throw new AssertionError(name);
    }
}
