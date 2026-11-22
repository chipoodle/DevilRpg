package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

public final class TraversalActionLeasePolicyTest {
    public static void main(String[] args) {
        BlockPos breakTarget = new BlockPos(10, 64, 20);
        BlockPos buildTarget = new BlockPos(11, 64, 20);

        check("active exact BREAK target is authorized", TraversalActionLeasePolicy.canAcquire(
                100L, -1L, false, true, NavRoute.Transition.BREAK, breakTarget,
                NavRoute.Transition.BREAK, breakTarget));
        check("BREAK cannot be substituted with BUILD", !TraversalActionLeasePolicy.canAcquire(
                100L, -1L, false, true, NavRoute.Transition.BREAK, breakTarget,
                NavRoute.Transition.BUILD, breakTarget));
        check("BREAK cannot be substituted with BRIDGE", !TraversalActionLeasePolicy.canAcquire(
                100L, -1L, false, true, NavRoute.Transition.BREAK, breakTarget,
                NavRoute.Transition.BRIDGE, breakTarget));
        check("different action target is denied", !TraversalActionLeasePolicy.canAcquire(
                100L, -1L, false, true, NavRoute.Transition.BREAK, breakTarget,
                NavRoute.Transition.BREAK, buildTarget));
        check("no active node is denied", !TraversalActionLeasePolicy.canAcquire(
                100L, -1L, false, false, NavRoute.Transition.BREAK, breakTarget,
                NavRoute.Transition.BREAK, breakTarget));
        check("missing active transition is denied", !TraversalActionLeasePolicy.canAcquire(
                100L, -1L, false, true, null, breakTarget,
                NavRoute.Transition.BREAK, breakTarget));
        check("missing requested target is denied", !TraversalActionLeasePolicy.canAcquire(
                100L, -1L, false, true, NavRoute.Transition.BREAK, breakTarget,
                NavRoute.Transition.BREAK, null));
        check("WALK cannot authorize a terrain tool", !TraversalActionLeasePolicy.canAcquire(
                100L, -1L, false, true, NavRoute.Transition.WALK, breakTarget,
                NavRoute.Transition.BREAK, breakTarget));
        check("DROP cannot authorize excavation", !TraversalActionLeasePolicy.canAcquire(
                100L, -1L, false, true, NavRoute.Transition.DROP, breakTarget,
                NavRoute.Transition.DROP, breakTarget));
        check("second request in same tick is denied", !TraversalActionLeasePolicy.canAcquire(
                100L, 100L, true, true, NavRoute.Transition.BREAK, breakTarget,
                NavRoute.Transition.BREAK, breakTarget));
        check("lease from previous tick does not block new tick", TraversalActionLeasePolicy.canAcquire(
                101L, 100L, true, true, NavRoute.Transition.BUILD, buildTarget,
                NavRoute.Transition.BUILD, buildTarget));
        check("BUILD requires exact node cell", TraversalActionLeasePolicy.canAcquire(
                101L, 100L, true, true, NavRoute.Transition.BUILD, buildTarget,
                NavRoute.Transition.BUILD, buildTarget));
        check("BRIDGE requires exact node cell", TraversalActionLeasePolicy.canAcquire(
                101L, 100L, true, true, NavRoute.Transition.BRIDGE, buildTarget,
                NavRoute.Transition.BRIDGE, buildTarget));

        // A2 · lease del túnel subordinado: un bloque exacto por tick, sin sustitución.
        BlockPos feet = new BlockPos(10, 64, 20);
        BlockPos head = new BlockPos(10, 65, 20);
        check("tunnel leases its exact feet block", TraversalActionLeasePolicy.canAcquireTunnel(
                200L, -1L, false, true, feet, NavRoute.Transition.BREAK, feet));
        check("tunnel cannot lease the head while feet is authorized",
                !TraversalActionLeasePolicy.canAcquireTunnel(
                        200L, -1L, false, true, feet, NavRoute.Transition.BREAK, head));
        check("tunnel leases its exact head block in the next phase", TraversalActionLeasePolicy
                .canAcquireTunnel(201L, 200L, true, true, head, NavRoute.Transition.BREAK, head));
        check("tunnel second action in the same tick is denied", !TraversalActionLeasePolicy
                .canAcquireTunnel(201L, 201L, true, true, head, NavRoute.Transition.BREAK, head));
        check("tunnel lease from a previous tick does not block the next tick", TraversalActionLeasePolicy
                .canAcquireTunnel(202L, 201L, true, true, feet, NavRoute.Transition.BREAK, feet));
        check("tunnel cannot authorize BUILD", !TraversalActionLeasePolicy.canAcquireTunnel(
                202L, -1L, false, true, feet, NavRoute.Transition.BUILD, feet));
        check("tunnel cannot authorize BRIDGE", !TraversalActionLeasePolicy.canAcquireTunnel(
                202L, -1L, false, true, feet, NavRoute.Transition.BRIDGE, feet));
        check("inactive tunnel authorizes nothing", !TraversalActionLeasePolicy.canAcquireTunnel(
                202L, -1L, false, false, feet, NavRoute.Transition.BREAK, feet));
        check("tunnel without an authorized block authorizes nothing",
                !TraversalActionLeasePolicy.canAcquireTunnel(
                        202L, -1L, false, true, null, NavRoute.Transition.BREAK, feet));
        check("tunnel without a requested block authorizes nothing",
                !TraversalActionLeasePolicy.canAcquireTunnel(
                        202L, -1L, false, true, feet, NavRoute.Transition.BREAK, null));

        System.out.println("TRAVERSAL_ACTION_LEASE_POLICY_TEST: OK (" + assertions + " assertions)");
    }

    private static int assertions;

    private static void check(String name, boolean value) {
        assertions++;
        if (!value) {
            throw new AssertionError(name);
        }
    }
}
