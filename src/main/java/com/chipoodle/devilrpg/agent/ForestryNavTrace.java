package com.chipoodle.devilrpg.agent;

import com.chipoodle.devilrpg.DevilRpg;
import java.util.UUID;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default-off, passive navigation/lease observation at the shared Goal layer.
 * Logging only; it never calls navigation, leases, claims or task code.
 */
public final class ForestryNavTrace {

    public static final String PROPERTY = "devilrpg.navtrace";
    private static final AtomicLong SEQ = new AtomicLong();

    private ForestryNavTrace() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    public static void event(String type, UUID actor, UUID task, String mode,
                             String destination, long tick, String detail) {
        if (!enabled()) return;
        DevilRpg.LOGGER.info("[NAV-TRACE] seq={} event={} actor={} task={} mode={} dest={} tick={} {}",
                SEQ.incrementAndGet(), type, actor, task, mode,
                destination == null ? "-" : destination, tick, detail == null ? "" : detail);
    }
}
