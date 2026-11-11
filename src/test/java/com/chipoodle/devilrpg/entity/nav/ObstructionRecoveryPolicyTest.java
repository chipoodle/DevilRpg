package com.chipoodle.devilrpg.entity.nav;

public final class ObstructionRecoveryPolicyTest {

    public static void main(String[] args) {
        check(true, ObstructionRecoveryPolicy.shouldAttemptBreach(0, 100L, 0L));
        check(true, ObstructionRecoveryPolicy.shouldAttemptBreach(2, 100L, 300L));
        check(false, ObstructionRecoveryPolicy.shouldAttemptBreach(3, 100L, 300L));
        check(true, ObstructionRecoveryPolicy.shouldAttemptBreach(3, 300L, 300L));
        check(true, ObstructionRecoveryPolicy.shouldAttemptBreach(1, 0L, 0L));
        check(true, ObstructionRecoveryPolicy.puedeRomper(0));
        check(true, ObstructionRecoveryPolicy.puedeRomper(2));
        check(false, ObstructionRecoveryPolicy.puedeRomper(3));
        System.out.println("OBSTRUCTION_RECOVERY_POLICY_TEST: OK (8 aserciones)");
    }

    private static void check(boolean expected, boolean actual) {
        if (expected != actual) {
            throw new AssertionError("esperado=" + expected + " actual=" + actual);
        }
    }
}
