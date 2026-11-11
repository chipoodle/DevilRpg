package com.chipoodle.harness.orchestrator;

import java.time.Duration;
import java.util.List;

/** Explicit lifecycle; SUCCEEDED requires verified final-response semantics. */
public final class Orchestrator {
    public enum State { QUEUED, VALIDATING, RUNNING, SUCCEEDED, FAILED, BLOCKED, TIMED_OUT, UNVERIFIED, UNKNOWN }

    public record Outcome(State state, ProcessAdapter.Result process, NdjsonParser.Result events,
                          String reportJson, String reason) {}

    private final ProcessAdapter adapter;
    private final String executable;
    private final Duration timeout;

    public Orchestrator(ProcessAdapter adapter, String executable, Duration timeout) {
        this.adapter = adapter;
        this.executable = executable;
        this.timeout = timeout;
    }

    public Outcome execute(TaskContract task) {
        if (task.productionRequested() || task.testsRequested() || task.authorization().commit()
                || task.authorization().push()) {
            return new Outcome(State.BLOCKED, null, null, null,
                    "authorization requested beyond skeleton policy; no process started");
        }
        ProcessAdapter.Result p;
        try {
            p = adapter.run(List.of(executable, "--profile", "headless", "--json"), task.objective(), timeout);
        } catch (Exception e) {
            return new Outcome(State.FAILED, null, null, null, "process launch failed: " + e.getMessage());
        }
        NdjsonParser.Result ev = NdjsonParser.parse(p.stdout());
        if (p.timedOut()) return outcome(State.TIMED_OUT, p, ev, "timeout");
        if (p.exitCode() != 0) return outcome(State.FAILED, p, ev, "exit=" + p.exitCode());
        if (!ev.malformed().isEmpty()) return outcome(State.FAILED, p, ev, "malformed NDJSON");
        if (ev.hasError()) return outcome(State.FAILED, p, ev, "harness error " + ev.errorCode());
        if (!ev.hasFinal()) return outcome(State.FAILED, p, ev, "missing final event");
        if (ev.finalText() == null || ev.finalText().isBlank())
            return outcome(State.FAILED, p, ev, "empty final text");
        return outcome(State.UNVERIFIED, p, ev, "zero exit and final text; success semantics unverified");
    }

    private Outcome outcome(State s, ProcessAdapter.Result p, NdjsonParser.Result ev, String reason) {
        return new Outcome(s, p, ev, Report.json(s, p, ev, reason), reason);
    }
}
