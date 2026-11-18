package com.chipoodle.devilrpg.entity.nav;

/** Regression for post-snap navigation-state reset and signature separation. */
public final class NavSnapPostResetPolicyTest {
    private static int assertions;

    public static void main(String[] args) {
        comprobar("verified completion resets", NavSnapPostResetPolicy.shouldResetAfterSnap(true));
        comprobar("non-completion does not reset", !NavSnapPostResetPolicy.shouldResetAfterSnap(false));
        comprobar("history seeded with one cell", NavSnapPostResetPolicy.freshHistorySize() == 1);
        comprobar("oscillating cleared", !NavSnapPostResetPolicy.freshOscillating());
        comprobar("occurrences reset", NavSnapPostResetPolicy.freshOccurrences() == 1);
        comprobar("signature invalidated", NavSnapPostResetPolicy.freshSignature() == 0);
        comprobar("new phase accepts changed signature",
                NavSnapPostResetPolicy.allowsFirstPostSnapSignature(0, 12345));
        comprobar("duplicate still detected",
                NavSnapPostResetPolicy.isDuplicateSignature(12345, 12345));
        comprobar("different signature not duplicate",
                !NavSnapPostResetPolicy.isDuplicateSignature(12345, 54321));
        comprobar("zero signature cannot equal a real nonzero signature",
                NavSnapPostResetPolicy.allowsFirstPostSnapSignature(0, 77));
        comprobar("duplicate protection remains exact",
                NavSnapPostResetPolicy.isDuplicateSignature(-7, -7));
        comprobar("different negative signatures allowed",
                !NavSnapPostResetPolicy.isDuplicateSignature(-7, -8));

        System.out.println("NAV_SNAP_POST_RESET_POLICY_TEST: OK (" + assertions + " assertions)");
    }

    private static void comprobar(String name, boolean ok) {
        assertions++;
        if (!ok) throw new AssertionError(name);
    }
}
