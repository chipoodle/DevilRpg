package com.chipoodle.devilrpg.entity.nav;

/** Dedicated opt-in trace for the stair-construction pipeline. */
public final class NavStairTrace {
    private static final boolean ON = Boolean.getBoolean("devilrpg.a2n.traceStair");
    private static final boolean CANDIDATES_ON =
            Boolean.getBoolean("devilrpg.a2n.traceStairCandidates");

    private NavStairTrace() {
    }

    public static boolean on() {
        return ON;
    }

    /** High-volume candidate diagnostics are separately opt-in. */
    public static void candidate(final String event, final String detail) {
        if (ON && CANDIDATES_ON) {
            NavTrace.ev(event, detail);
        }
    }

    public static void ev(final String event, final String detail) {
        if (ON) {
            NavTrace.ev(event, detail);
        }
    }
}
