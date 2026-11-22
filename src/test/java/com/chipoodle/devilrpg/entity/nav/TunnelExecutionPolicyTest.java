package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * A2 · contrato de ejecución del túnel: una acción por fase, el paso solo se consume con ocupación física
 * verificada y un fallo nunca salta al paso siguiente.
 */
public final class TunnelExecutionPolicyTest {
    public static void main(String[] args) {
        BlockPos start = new BlockPos(0, 64, 0);
        List<TunnelActionPlanner.Step> plan = TunnelActionPlanner.straight(start, 1, 0, 2,
                p -> true, p -> false, p -> true);
        BlockPos feet0 = plan.get(0).feetBlock();
        BlockPos head0 = plan.get(0).headBlock();
        BlockPos feet1 = plan.get(1).feetBlock();
        BlockPos head1 = plan.get(1).headBlock();

        TunnelExecutionPolicy.Cursor cursor = TunnelExecutionPolicy.start(plan.size());
        check("start is step 0 at feet", cursor.stepIndex() == 0
                && cursor.phase() == TunnelExecutionPolicy.Phase.BREAK_FEET && !cursor.complete());
        check("start requires no move", !cursor.complete() && cursor.breakPhase());
        check("feet block is the authorized target", TunnelExecutionPolicy.breakTarget(cursor, plan).equals(feet0));
        check("head block is the sibling of the same opening",
                TunnelExecutionPolicy.stepSibling(cursor, plan).equals(head0));
        check("stand is the step stand", TunnelExecutionPolicy.standCell(cursor, plan).equals(start));
        check("post is the step post", TunnelExecutionPolicy.postCell(cursor, plan).equals(plan.get(0).post()));

        // Fallo de break: NO avanza de fase y NO se consume el paso.
        TunnelExecutionPolicy.Cursor feetFailed = TunnelExecutionPolicy.afterBreak(cursor, false);
        check("failed feet break keeps the same phase", feetFailed == cursor);
        check("failed feet break does not authorize the head", TunnelExecutionPolicy
                .breakTarget(feetFailed, plan).equals(feet0));
        check("failed feet break does not consume the step",
                feetFailed.stepIndex() == 0 && TunnelExecutionPolicy.postCell(feetFailed, plan)
                        .equals(plan.get(0).post()));

        TunnelExecutionPolicy.Cursor head = TunnelExecutionPolicy.afterBreak(cursor, true);
        check("verified feet break advances to the head at the same step",
                head.phase() == TunnelExecutionPolicy.Phase.BREAK_HEAD && head.stepIndex() == 0);
        check("head block is now the authorized target",
                TunnelExecutionPolicy.breakTarget(head, plan).equals(head0));
        check("feet block is the sibling while breaking the head",
                TunnelExecutionPolicy.stepSibling(head, plan).equals(feet0));
        check("failed head break keeps the head phase",
                TunnelExecutionPolicy.afterBreak(head, false) == head);
        check("failed head break still does not consume the step", head.stepIndex() == 0);

        TunnelExecutionPolicy.Cursor move = TunnelExecutionPolicy.afterBreak(head, true);
        check("verified head break requires the move",
                move.phase() == TunnelExecutionPolicy.Phase.MOVE_POST && move.stepIndex() == 0);
        check("no break target is authorized during the move",
                TunnelExecutionPolicy.breakTarget(move, plan) == null);
        check("no sibling is authorized during the move",
                TunnelExecutionPolicy.stepSibling(move, plan) == null);

        // Fallo de movimiento: NO consume el paso.
        check("failed move does not consume the step", TunnelExecutionPolicy.afterMove(move, false) == move);

        TunnelExecutionPolicy.Cursor consumed = TunnelExecutionPolicy.afterMove(move, true);
        check("verified move consumes the step and starts the next one",
                consumed.stepIndex() == 1 && consumed.phase() == TunnelExecutionPolicy.Phase.BREAK_FEET
                        && !consumed.complete());
        check("a consumed step is never repeated", TunnelExecutionPolicy
                .breakTarget(consumed, plan).equals(feet1));
        check("re-applying the verified move to the OLD cursor does not double-consume",
                TunnelExecutionPolicy.afterMove(move, true).stepIndex() == 1);

        TunnelExecutionPolicy.Cursor secondHead = TunnelExecutionPolicy.afterBreak(consumed, true);
        TunnelExecutionPolicy.Cursor secondMove = TunnelExecutionPolicy.afterBreak(secondHead, true);
        check("second step uses its own head block",
                TunnelExecutionPolicy.breakTarget(secondHead, plan).equals(head1));
        TunnelExecutionPolicy.Cursor done = TunnelExecutionPolicy.afterMove(secondMove, true);
        check("complete plan is complete only after every verified move",
                done.stepIndex() == 2 && done.complete());
        check("complete plan authorizes no action",
                TunnelExecutionPolicy.breakTarget(done, plan) == null
                        && TunnelExecutionPolicy.standCell(done, plan) == null
                        && TunnelExecutionPolicy.postCell(done, plan) == null);
        check("complete cursor is stable", TunnelExecutionPolicy.afterMove(done, true) == done
                && TunnelExecutionPolicy.afterBreak(done, true) == done);
        check("empty plan is complete", TunnelExecutionPolicy.start(0).complete());

        // Línea de visión del túnel: el objetivo o el otro bloque de la MISMA apertura; nada más.
        check("line of sight accepts the requested block",
                TunnelExecutionPolicy.lineOfSightAccepted(feet0, feet0, head0));
        check("line of sight accepts the sibling of the same opening",
                TunnelExecutionPolicy.lineOfSightAccepted(head0, feet0, head0));
        check("line of sight rejects a third block",
                !TunnelExecutionPolicy.lineOfSightAccepted(new BlockPos(5, 64, 5), feet0, head0));
        check("line of sight rejects a missing ray hit",
                !TunnelExecutionPolicy.lineOfSightAccepted(null, feet0, head0));
        check("line of sight rejects a null request",
                !TunnelExecutionPolicy.lineOfSightAccepted(feet0, null, head0));

        System.out.println("TUNNEL_EXECUTION_POLICY_TEST: OK (" + assertions + " assertions)");
    }

    private static int assertions;

    private static void check(String name, boolean ok) {
        assertions++;
        if (!ok) throw new AssertionError(name);
    }
}
