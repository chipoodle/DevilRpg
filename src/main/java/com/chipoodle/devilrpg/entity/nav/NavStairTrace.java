package com.chipoodle.devilrpg.entity.nav;

/** Dedicated opt-in trace for the stair-construction pipeline. */
public final class NavStairTrace {
    private static final boolean ON = Boolean.getBoolean("devilrpg.a2n.traceStair");

    private NavStairTrace() {
    }

    public static boolean on() {
        return ON;
    }

    public static void ev(final String event, final String detail) {
        if (ON) {
            NavTrace.ev(event, detail);
        }
    }
}
