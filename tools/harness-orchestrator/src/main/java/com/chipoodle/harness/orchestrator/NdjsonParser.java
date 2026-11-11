package com.chipoodle.harness.orchestrator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Parses verified NDJSON envelope types without assuming success semantics. */
public final class NdjsonParser {
    public record Event(String type, String raw, Map<String, Object> data) {}
    public record Result(List<Event> events, List<String> malformed, boolean hasFinal,
                         String finalText, boolean hasError, String errorCode, String errorMessage) {}

    public static Result parse(String stdout) {
        List<Event> events = new ArrayList<>();
        List<String> malformed = new ArrayList<>();
        boolean hasFinal = false; String finalText = null;
        boolean hasError = false; String code = null, message = null;
        for (String line : stdout.split("\\R")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            try {
                Map<String, Object> m = Json.obj(t);
                Object type = m.get("type");
                if (!(type instanceof String s)) { malformed.add(t); continue; }
                events.add(new Event(s, t, m));
                if ("final".equals(s)) {
                    hasFinal = true;
                    Object text = m.get("text");
                    finalText = text instanceof String x ? x : "";
                }
                if ("status".equals(s) && m.get("reason") instanceof Map<?, ?> r
                        && "error".equals(r.get("kind"))) {
                    hasError = true;
                    if (r.get("error") instanceof Map<?, ?> e) {
                        code = e.get("code") instanceof String c ? c : null;
                        message = e.get("message") instanceof String x ? x : null;
                    }
                }
            } catch (RuntimeException e) {
                malformed.add(t);
            }
        }
        return new Result(List.copyOf(events), List.copyOf(malformed), hasFinal, finalText,
                hasError, code, message);
    }
}
