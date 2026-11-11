package com.chipoodle.devilrpg.entity.nav;

/** A5.32/IMPLEMENT-01 · bounded breach-vs-detour fallback. */
public final class ObstructionRecoveryPolicy {

    private ObstructionRecoveryPolicy() {
    }

    /**
     * A breach may be attempted while under the per-target failure cap, or after the veto expires.
     * The caller still has to pass terrain/protection checks.
     */
    public static boolean shouldAttemptBreach(final int failures, final long now, final long avoidUntil) {
        return failures < 3 || now >= avoidUntil;
    }

    /** At most three successful removals per obstruction-recovery attempt. */
    public static boolean puedeRomper(final int roturasHechas) {
        return roturasHechas < 3;
    }
}
