package com.chipoodle.harness.orchestrator;

import java.time.Instant;

public final class Report {
    private Report() {}

    public static String json(Orchestrator.State state, ProcessAdapter.Result p,
                              NdjsonParser.Result events, String reason) {
        String exit = p == null ? "null" : Integer.toString(p.exitCode());
        String elapsed = p == null ? "null" : Long.toString(p.elapsedMillis());
        String finalText = events == null || events.finalText() == null ? "" : escape(events.finalText());
        int eventCount = events == null ? 0 : events.events().size();
        int malformed = events == null ? 0 : events.malformed().size();
        return "{\"schema\":\"devilrpg.execution-report/v1\",\"state\":\"" + state
                + "\",\"exit_code\":" + exit + ",\"elapsed_ms\":" + elapsed
                + ",\"event_count\":" + eventCount + ",\"malformed_lines\":" + malformed
                + ",\"final_text\":\"" + finalText + "\",\"reason\":\"" + escape(reason)
                + "\",\"generated_at\":\"" + Instant.now() + "\"}";
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
