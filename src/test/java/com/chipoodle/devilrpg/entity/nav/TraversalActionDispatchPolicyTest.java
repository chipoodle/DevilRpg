package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

import java.util.concurrent.atomic.AtomicInteger;

public final class TraversalActionDispatchPolicyTest {
    public static void main(String[] args) {
        BlockPos breakTarget = new BlockPos(10, 64, 20);
        BlockPos otherTarget = new BlockPos(11, 64, 20);
        AtomicInteger calls = new AtomicInteger();

        boolean noLease = TraversalActionDispatchPolicy.dispatch(false, () -> {
            calls.incrementAndGet();
            return true;
        });
        check("BREAK callback is not invoked without a lease", !noLease && calls.get() == 0);

        boolean exactBreakLease = TraversalActionLeasePolicy.canAcquire(
                100L, -1L, false, true, NavRoute.Transition.BREAK, breakTarget,
                NavRoute.Transition.BREAK, breakTarget);
        boolean normalBreak = TraversalActionDispatchPolicy.dispatch(exactBreakLease, () -> {
            calls.incrementAndGet();
            return true;
        });
        check("normal BREAK dispatches exactly once after exact lease", normalBreak && calls.get() == 1);

        calls.set(0);
        boolean dropCannotAuthorizeBreak = TraversalActionLeasePolicy.canAcquire(
                101L, -1L, false, true, NavRoute.Transition.DROP, breakTarget,
                NavRoute.Transition.BREAK, breakTarget);
        boolean dropBreak = TraversalActionDispatchPolicy.dispatch(dropCannotAuthorizeBreak, () -> {
            calls.incrementAndGet();
            return true;
        });
        check("DROP obstruction cannot dispatch BREAK", !dropBreak && calls.get() == 0);

        boolean dropCannotAuthorizeAnyTerrainTool = TraversalActionLeasePolicy.canAcquire(
                101L, -1L, false, true, NavRoute.Transition.DROP, breakTarget,
                NavRoute.Transition.DROP, breakTarget);
        check("DROP itself cannot acquire a terrain-tool lease", !dropCannotAuthorizeAnyTerrainTool);

        boolean wrongTargetLease = TraversalActionLeasePolicy.canAcquire(
                102L, -1L, false, true, NavRoute.Transition.BREAK, breakTarget,
                NavRoute.Transition.BREAK, otherTarget);
        boolean wrongTargetDispatch = TraversalActionDispatchPolicy.dispatch(wrongTargetLease, () -> {
            calls.incrementAndGet();
            return true;
        });
        check("BREAK callback is not invoked for a different target", !wrongTargetDispatch && calls.get() == 0);

        boolean failedLease = TraversalActionLeasePolicy.canAcquire(
                103L, -1L, false, true, NavRoute.Transition.BREAK, breakTarget,
                NavRoute.Transition.BREAK, breakTarget);
        boolean failedAction = TraversalActionDispatchPolicy.dispatch(failedLease, () -> {
            calls.incrementAndGet();
            return false;
        });
        boolean secondSameTickLease = TraversalActionLeasePolicy.canAcquire(
                103L, 103L, true, true, NavRoute.Transition.BREAK, breakTarget,
                NavRoute.Transition.BREAK, breakTarget);
        boolean secondDispatch = TraversalActionDispatchPolicy.dispatch(secondSameTickLease, () -> {
            calls.incrementAndGet();
            return true;
        });
        check("failed action still consumes lease and denies a second same-tick attempt",
                !failedAction && !secondDispatch && calls.get() == 1 && !secondSameTickLease);

        System.out.println("TRAVERSAL_ACTION_DISPATCH_POLICY_TEST: OK (6 assertions)");
    }

    private static void check(String name, boolean value) {
        if (!value) {
            throw new AssertionError(name);
        }
    }
}
