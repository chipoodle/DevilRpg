package com.chipoodle.devilrpg.entity.nav;

/** Pure contract for the one-time post-snap navigation-state reset. */
public final class NavSnapPostResetPolicy {
    private NavSnapPostResetPolicy() {
    }

    /** Only a verified snap completion starts a new navigation phase. */
    public static boolean shouldResetAfterSnap(boolean snapComplete) {
        return snapComplete;
    }

    public static int freshHistorySize() {
        return 1;
    }

    public static boolean freshOscillating() {
        return false;
    }

    public static int freshOccurrences() {
        return 1;
    }

    public static int freshSignature() {
        return 0;
    }

    /** A new phase may accept any route signature; normal duplicate protection resumes afterwards. */
    public static boolean allowsFirstPostSnapSignature(int previousSignature, int candidateSignature) {
        return candidateSignature != previousSignature;
    }

    /** Normal duplicate protection remains active once the new phase has started. */
    public static boolean isDuplicateSignature(int previousSignature, int candidateSignature) {
        return previousSignature == candidateSignature;
    }
}
