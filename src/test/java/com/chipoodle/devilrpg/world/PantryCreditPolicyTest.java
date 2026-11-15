package com.chipoodle.devilrpg.world;

public final class PantryCreditPolicyTest {
    private static int fallos;

    public static void main(String[] args) {
        PantryCreditPolicy.Result single = PantryCreditPolicy.apply(3, 1, 0);
        check(single.creditUsed() == 1 && single.granted() == 1 && single.remainingCredit() == 2,
                "credito simple");
        PantryCreditPolicy.Result insufficient = PantryCreditPolicy.apply(1, 4, 2);
        check(insufficient.creditUsed() == 1 && insufficient.physicalNeeded() == 3
                        && insufficient.granted() == 3 && insufficient.remainingCredit() == 0,
                "insuficiente conserva lo fisico");
        PantryCreditPolicy.Result overdraw = PantryCreditPolicy.apply(1, 2, 4);
        check(overdraw.granted() == 2 && overdraw.remainingCredit() == 3, "sobrante a credito");
        PantryCreditPolicy.Result failed = PantryCreditPolicy.apply(2, 2, 0);
        check(failed.granted() == 2 && failed.remainingCredit() == 0, "credito cubre sin fisico");
        PantryCreditPolicy.Result zero = PantryCreditPolicy.apply(0, 0, 0);
        check(zero.granted() == 0 && zero.remainingCredit() == 0, "vacio");
        PantryCreditPolicy.Result retry = PantryCreditPolicy.apply(0, 1, 0);
        check(retry.granted() == 0 && retry.remainingCredit() == 0, "reintento sin stock");
        PantryCreditPolicy.Result conserve = PantryCreditPolicy.apply(5, 2, 0);
        check(conserve.creditUsed() + conserve.remainingCredit() == 5, "conservacion credito");
        if (fallos > 0) { System.out.println("PANTRY_CREDIT_POLICY_TEST: FAIL (" + fallos + ")"); System.exit(1); }
        System.out.println("PANTRY_CREDIT_POLICY_TEST: OK");
    }

    private static void check(boolean ok, String name) {
        if (!ok) { fallos++; System.out.println("  FALLO: " + name); }
    }
}
