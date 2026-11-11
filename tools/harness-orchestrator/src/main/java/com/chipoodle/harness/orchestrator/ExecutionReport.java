package com.chipoodle.harness.orchestrator;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/** Single report model for JSON and Markdown; map-backed to keep both formats equivalent. */
public final class ExecutionReport {
    public static final String SCHEMA = "devilrpg.report/v2";
    private final LinkedHashMap<String, Object> root;

    private ExecutionReport(LinkedHashMap<String, Object> root) { this.root = root; }
    public Map<String, Object> root() { return root; }
    public String state() { return String.valueOf(root.get("state")); }

    public static ExecutionReport of(TaskContract t, Orchestrator.State state, ProcessAdapter.Result p,
                                     NdjsonParser.Result events, GitStateProvider.State before,
                                     GitStateProvider.State after, List<String> changed,
                                     String reason, boolean reportOk) {
        LinkedHashMap<String, Object> r = new LinkedHashMap<>();
        r.put("schema", SCHEMA);
        r.put("reportId", UUID.randomUUID().toString());
        r.put("taskId", t.taskId());
        r.put("taskSchema", t.schema());
        r.put("createdAt", Instant.now().toString());
        r.put("orchestratorVersion", "0.2.1-rc.2");
        r.put("state", state.name());
        r.put("reason", reason == null ? "UNKNOWN" : reason);
        r.put("reportPersisted", reportOk);

        r.put("task", map("objective", t.objective(), "acceptance", t.acceptance(),
                "repository", t.repository().toString(), "branch", t.branch(),
                "expectedHead", nz(t.expectedHead()), "allowedPaths", t.allowedPaths(),
                "forbiddenPaths", t.forbiddenPaths(), "timeoutSeconds", t.timeoutSeconds(),
                "profile", t.profile()));

        r.put("repository", map("path", t.repository().toString(),
                "before", git(before), "after", git(after), "inspection", before.error() == null ? "OK" : "UNKNOWN"));

        List<Object> auth = new ArrayList<>();
        TaskContract.Authorization a = t.authorization();
        auth.add(auth("commit", a.commit())); auth.add(auth("push", a.push()));
        auth.add(auth("protected_worlds", a.protectedWorlds()));
        auth.add(auth("network", a.network()));
        auth.add(auth("production_code", a.productionCode()));
        auth.add(auth("test_changes", a.testChanges()));
        r.put("authorization", auth);

        r.put("lifecycle", map("validation", "COMPLETED",
                "processStarted", p != null, "pid", "UNKNOWN",
                "startedAt", "UNKNOWN", "endedAt", "UNKNOWN",
                "exitCode", p == null ? null : p.exitCode(),
                "timedOut", p != null && p.timedOut(),
                "elapsedMs", p == null ? null : p.elapsedMillis(),
                "interrupted", "UNKNOWN",
                "terminationAttempted", p != null && p.timedOut(),
                "terminationConfirmed", p != null && p.timedOut() ? "UNKNOWN" : "NOT_APPLICABLE"));

        List<Object> evs = new ArrayList<>();
        int seq = 0;
        if (events != null) {
            for (NdjsonParser.Event e : events.events()) evs.add(map("seq", ++seq, "type", e.type(),
                    "payload", e.raw(), "timestamp", "UNKNOWN", "parse", "OK"));
            for (String m : events.malformed()) evs.add(map("seq", ++seq, "type", "MALFORMED",
                    "payload", m, "timestamp", "UNKNOWN", "parse", "MALFORMED"));
        }
        r.put("events", evs);
        r.put("stderr", p == null ? "" : sanitize(p.stderr()));

        r.put("finalResponse", map("received", events != null && events.hasFinal(),
                "text", events == null || events.finalText() == null ? null : sanitize(events.finalText()),
                "empty", events == null || events.finalText() == null || events.finalText().isEmpty(),
                "semanticsVerified", false, "authoritative", false));

        List<Object> files = new ArrayList<>();
        for (String f : changed) files.add(map("path", f, "category", "UNKNOWN",
                "allowed", "UNKNOWN", "forbidden", "UNKNOWN", "complete", false));
        r.put("filesChanged", files);

        r.put("validations", List.of(map("id", "harness-execution", "executed", p != null,
                "exitCode", p == null ? null : p.exitCode(), "result", p == null ? "NOT_RUN" : state.name(),
                "evidence", "process adapter", "timestamp", "UNKNOWN")));

        List<Object> unresolved = new ArrayList<>();
        unresolved.add("real Harness success/final semantics unverified");
        unresolved.add("credentials not accessed");
        unresolved.add("PID/start/end timestamps unavailable from current adapter");
        unresolved.add("process-tree termination not verified");
        r.put("unresolved", unresolved);

        boolean recovery = state == Orchestrator.State.BLOCKED || state == Orchestrator.State.UNKNOWN
                || state == Orchestrator.State.TIMED_OUT;
        r.put("recovery", map("required", recovery, "lastState", state.name(), "taskId", t.taskId(),
                "pid", "UNKNOWN", "lockStatus", "UNKNOWN",
                "repositoryMayHaveChanged", p != null ? "UNKNOWN" : "NO",
                "instructions", recovery
                        ? "Inspect repository state and lock; do not auto-retry or steal a lock."
                        : "NOT_APPLICABLE"));
        return new ExecutionReport(r);
    }

    public List<String> validate() {
        List<String> e = new ArrayList<>();
        for (String k : List.of("schema","taskId","taskSchema","createdAt","orchestratorVersion","task",
                "repository","authorization","lifecycle","events","finalResponse","filesChanged",
                "validations","unresolved","recovery","state")) if (!root.containsKey(k)) e.add("missing " + k);
        if (!SCHEMA.equals(root.get("schema"))) e.add("bad schema");
        if ("SUCCEEDED".equals(root.get("state"))) e.add("SUCCEEDED not allowed");
        @SuppressWarnings("unchecked") Map<String,Object> life = (Map<String,Object>) root.get("lifecycle");
        boolean started = Boolean.TRUE.equals(life.get("processStarted"));
        @SuppressWarnings("unchecked") List<Object> events = (List<Object>) root.get("events");
        if (started && events.isEmpty()) e.add("process started without events");
        if (started && root.get("finalResponse") instanceof Map<?,?> f && !Boolean.TRUE.equals(f.get("received")))
            e.add("process started without final event");
        if ("BLOCKED".equals(root.get("state")) || "UNKNOWN".equals(root.get("state")) || "TIMED_OUT".equals(root.get("state"))) {
            if (root.get("recovery") instanceof Map<?,?> r && !Boolean.TRUE.equals(r.get("required")))
                e.add("recovery missing");
        }
        return e;
    }

    public String toJson() { StringBuilder b = new StringBuilder(); json(root, b); return b.append('\n').toString(); }

    public String toMarkdown() {
        StringBuilder b = new StringBuilder("# Task ").append(root.get("taskId")).append("\n\n");
        b.append("State: **").append(root.get("state")).append("**\n\n");
        b.append("Reason: ").append(root.get("reason")).append("\n\n");
        b.append("```json\n").append(toJson()).append("```\n");
        return b.toString();
    }

    private static Map<String,Object> git(GitStateProvider.State g) {
        if (g == null || g.error() != null) return map("branch","UNKNOWN","head","UNKNOWN",
                "staged","UNKNOWN","unstaged","UNKNOWN","untracked","UNKNOWN","status","UNKNOWN");
        return map("branch", nz(g.branch()), "head", nz(g.head()),
                "staged","UNKNOWN","unstaged", g.dirty() ? "UNKNOWN" : "0",
                "untracked","UNKNOWN","status", g.dirty() ? "DIRTY" : "CLEAN");
    }
    private static Map<String,Object> auth(String k, boolean requested) {
        return map("flag",k,"requested",requested,"effective",false,"decision","DENIED","reason","default-deny");
    }
    private static String nz(String s) { return s == null || s.isBlank() ? "UNKNOWN" : s; }
    private static String sanitize(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)(api[_-]?key|token|password)\\s*[=:]\\s*\\S+", "$1=<redacted>");
    }
    private static Map<String,Object> map(Object... kv) {
        LinkedHashMap<String,Object> m = new LinkedHashMap<>();
        for (int i=0;i<kv.length;i+=2) m.put(String.valueOf(kv[i]), kv[i+1]);
        return m;
    }
    private static void json(Object o, StringBuilder b) {
        if (o == null) { b.append("null"); return; }
        if (o instanceof String s) { b.append('"').append(s.replace("\\","\\\\").replace("\"","\\\"")
                .replace("\n","\\n").replace("\r","\\r")).append('"'); return; }
        if (o instanceof Number || o instanceof Boolean) { b.append(o); return; }
        if (o instanceof Map<?,?> m) {
            b.append('{'); boolean first=true;
            for (var e : m.entrySet()) { if(!first)b.append(','); first=false;
                json(String.valueOf(e.getKey()), b); b.append(':'); json(e.getValue(), b); }
            b.append('}'); return;
        }
        if (o instanceof Iterable<?> it) {
            b.append('['); boolean first=true;
            for (Object x : it) { if(!first)b.append(','); first=false; json(x,b); }
            b.append(']'); return;
        }
        json(String.valueOf(o), b);
    }
}
