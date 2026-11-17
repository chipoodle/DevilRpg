package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

/** Regression for exclusive DROP source-snap ownership and center admission. */
public final class NavSnapOwnershipPolicyTest {
    private static int assertions;

    public static void main(String[] args) {
        BlockPos pending = new BlockPos(577, 110, 514);
        comprobar("pending snap owns movement", NavSnapOwnershipPolicy.snapOwnsMovement(pending));
        comprobar("normal route movement is blocked during snap",
                !NavSnapOwnershipPolicy.allowsRouteMovement(pending));
        comprobar("oscillation abort is blocked during snap",
                !NavSnapOwnershipPolicy.allowsOscillationAbort(pending));
        comprobar("normal route movement is allowed without snap",
                NavSnapOwnershipPolicy.allowsRouteMovement(null));
        comprobar("oscillation abort is allowed without snap",
                NavSnapOwnershipPolicy.allowsOscillationAbort(null));

        comprobar("0.49 from center is not snap completion",
                !NavSnapOwnershipPolicy.snapComplete(false, true, true, true, true));
        comprobar("within 0.20 center tolerance completes",
                NavSnapOwnershipPolicy.snapComplete(true, true, true, true, true));
        comprobar("missing support blocks completion",
                !NavSnapOwnershipPolicy.snapComplete(true, true, true, false, true));
        comprobar("bad real AABB fit blocks completion",
                !NavSnapOwnershipPolicy.snapComplete(true, true, true, true, false));
        comprobar("airborne source blocks completion",
                !NavSnapOwnershipPolicy.snapComplete(true, false, true, true, true));
        comprobar("invalid feet blocks completion",
                !NavSnapOwnershipPolicy.snapComplete(true, true, false, true, true));
        comprobar("same-cell non-centered position stays incomplete",
                !NavSnapOwnershipPolicy.snapComplete(false, true, true, true, true));

        System.out.println("NAV_SNAP_OWNERSHIP_POLICY_TEST: OK (" + assertions + " assertions)");
    }

    private static void comprobar(String name, boolean ok) {
        assertions++;
        if (!ok) throw new AssertionError(name);
    }
}
