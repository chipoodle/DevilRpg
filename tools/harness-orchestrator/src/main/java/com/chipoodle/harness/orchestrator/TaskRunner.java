package com.chipoodle.harness.orchestrator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class TaskRunner {
    public record Outcome(Orchestrator.State state, String taskId, Path report, String message) {}

    private final Path base;
    private final GitStateProvider git;
    private final ProcessAdapter adapter;
    private final String executable;
    private final String runnerId;
    private final Duration timeout;

    public TaskRunner(Path base, GitStateProvider git, ProcessAdapter adapter, String executable,
                      String runnerId, Duration timeout) throws IOException {
        this.base = base.toAbsolutePath().normalize();
        this.git = git; this.adapter = adapter; this.executable = executable;
        this.runnerId = runnerId; this.timeout = timeout;
        for (String d : List.of("inbox","running","completed","failed","blocked","reports","locks"))
            Files.createDirectories(this.base.resolve(d));
    }

    public TaskContract validate(Path taskFile) throws IOException {
        return TaskContract.parse(Files.readString(taskFile));
    }

    public Outcome run(Path taskFile) throws IOException {
        TaskContract task;
        try { task = validate(taskFile); }
        catch (RuntimeException e) { return blockedInvalid(taskFile, e.getMessage()); }

        if (find(task.taskId()) != null) {
            return new Outcome(Orchestrator.State.BLOCKED, task.taskId(), null,
                    "duplicate task id already present");
        }
        if (Files.exists(base.resolve("reports").resolve(task.taskId() + ".json"))
                || Files.exists(base.resolve("reports").resolve(task.taskId() + ".md"))) {
            return new Outcome(Orchestrator.State.BLOCKED, task.taskId(), null, "report already exists");
        }
        Path inbox = base.resolve("inbox").resolve(task.taskId() + ".json");
        if (Files.exists(inbox)) return new Outcome(Orchestrator.State.BLOCKED, task.taskId(), null, "duplicate inbox");
        Files.copy(taskFile, inbox, StandardCopyOption.COPY_ATTRIBUTES);

        GitStateProvider.State gs = git.read(task.repository());
        String gate = gate(task, gs);
        if (gate != null) {
            Path report = report(task, null, null, Orchestrator.State.BLOCKED, gate, gs, gs);
            Files.move(inbox, base.resolve("blocked").resolve(task.taskId() + ".json"), StandardCopyOption.ATOMIC_MOVE);
            return new Outcome(Orchestrator.State.BLOCKED, task.taskId(), report, gate);
        }

        RunLock lock;
        try { lock = RunLock.acquire(base.resolve("locks"), task.taskId(), ProcessHandle.current().pid(), runnerId); }
        catch (IOException e) { return new Outcome(Orchestrator.State.BLOCKED, task.taskId(), null, e.getMessage()); }

        Path running = base.resolve("running").resolve(task.taskId() + ".json");
        try {
            Files.move(inbox, running, StandardCopyOption.ATOMIC_MOVE);
            Files.writeString(base.resolve("running").resolve(task.taskId() + ".meta"),
                    "task=" + task.taskId() + "\npid=" + ProcessHandle.current().pid() + "\nrunner=" + runnerId
                            + "\nstarted=" + Instant.now() + "\n", StandardCharsets.UTF_8);
            Orchestrator.Outcome out = new Orchestrator(adapter, executable, timeout).execute(task);
            Orchestrator.State s = out.state();
            GitStateProvider.State after = git.read(task.repository());
            Path report = report(task, out.process(), out.events(), s, out.reason(), gs, after);
            Path dest = base.resolve(dirFor(s)).resolve(task.taskId() + ".json");
            Files.move(running, dest, StandardCopyOption.ATOMIC_MOVE);
            Files.deleteIfExists(base.resolve("running").resolve(task.taskId() + ".meta"));
            return new Outcome(s, task.taskId(), report, out.reason());
        } catch (Exception e) {
            Files.move(running, base.resolve("failed").resolve(task.taskId() + ".json"), StandardCopyOption.ATOMIC_MOVE);
            Path report = report(task, null, null, Orchestrator.State.FAILED, e.getMessage(), gs, git.read(task.repository()));
            return new Outcome(Orchestrator.State.FAILED, task.taskId(), report, e.getMessage());
        } finally {
            try { lock.close(); } catch (IOException ignored) { }
        }
    }

    /** Classifies orphaned running tasks without moving or retrying them. */
    public java.util.List<String> classifyInterrupted() throws IOException {
        java.util.List<String> out = new java.util.ArrayList<>();
        Path runningDir = base.resolve("running");
        try (var stream = Files.list(runningDir)) {
            for (Path f : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
                String name = f.getFileName().toString();
                String id = name.substring(0, name.length() - 5);
                Path meta = runningDir.resolve(id + ".meta");
                String body = Files.exists(meta) ? Files.readString(meta) : "";
                long pid = -1;
                for (String line : body.split("\\R")) if (line.startsWith("pid=")) {
                    try { pid = Long.parseLong(line.substring(4).trim()); } catch (NumberFormatException ignored) { }
                }
                boolean alive = pid > 0 && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
                String state = alive ? "UNKNOWN" : "BLOCKED";
                Path report = base.resolve("reports").resolve(id + "-recovery.json");
                if (!Files.exists(report)) {
                    Files.writeString(report, "{\"task_id\":\"" + id + "\",\"state\":\"" + state
                            + "\",\"pid\":" + pid + "\",\"process_alive\":" + alive
                            + ",\"manual_recovery\":\"verify PID/ownership, then move running/" + id
                            + ".json manually; do not retry automatically\"}\n", StandardCharsets.UTF_8);
                }
                out.add(state + " " + id);
            }
        }
        return out;
    }
    public String status(String id) throws IOException {
        for (String d : List.of("running","completed","failed","blocked","inbox")) {
            Path f = base.resolve(d).resolve(id + ".json");
            if (Files.exists(f)) return d + " " + id;
        }
        return "unknown " + id;
    }

    public String report(String id, String format) throws IOException {
        Path json = base.resolve("reports").resolve(id + ".json");
        Path md = base.resolve("reports").resolve(id + ".md");
        return "markdown".equals(format) && Files.exists(md) ? Files.readString(md)
                : Files.exists(json) ? Files.readString(json) : "no report for " + id;
    }

    private Path find(String id) {
        for (String d : List.of("inbox","running","completed","failed","blocked")) {
            Path f = base.resolve(d).resolve(id + ".json");
            if (Files.exists(f)) return f;
        }
        return null;
    }

    private static String gate(TaskContract t, GitStateProvider.State gs) {
        if (gs.error() != null) return "git inspection failed: " + gs.error();
        if (gs.dirty()) return "worktree is dirty; explicit dirty baseline is not supported";
        if (!t.branch().equals(gs.branch())) return "branch mismatch: expected " + t.branch() + " got " + gs.branch();
        if (t.expectedHead() != null && !t.expectedHead().isBlank()
                && !t.expectedHead().equals(gs.head())) return "HEAD mismatch";
        return null;
    }

    private static String dirFor(Orchestrator.State s) {
        return switch (s) {
            case FAILED, TIMED_OUT -> "failed";
            case BLOCKED, UNKNOWN -> "blocked";
            case UNVERIFIED, SUCCEEDED -> "completed";
            default -> "failed";
        };
    }

    private Outcome blockedInvalid(Path taskFile, String message) throws IOException {
        String id = "invalid-" + System.nanoTime();
        Path blocked = base.resolve("blocked").resolve(id + ".json");
        Files.copy(taskFile, blocked);
        Path report = base.resolve("reports").resolve(id + ".json");
        Files.writeString(report, "{\"task_id\":\"" + id + "\",\"state\":\"BLOCKED\",\"reason\":\""
                + escape(message) + "\"}\n", StandardCharsets.UTF_8);
        return new Outcome(Orchestrator.State.BLOCKED, id, report, message);
    }

    private Path report(TaskContract t, ProcessAdapter.Result p, NdjsonParser.Result events,
                        Orchestrator.State s, String reason, GitStateProvider.State before,
                        GitStateProvider.State after) throws IOException {
        Path json = base.resolve("reports").resolve(t.taskId() + ".json");
        Path md = base.resolve("reports").resolve(t.taskId() + ".md");
        if (Files.exists(json) || Files.exists(md)) throw new IOException("report already exists for " + t.taskId());
        java.util.List<String> changed = after.error() == null ? git.changedFiles(t.repository()) : java.util.List.of();
        ExecutionReport report = ExecutionReport.of(t, s, p, events, before, after, changed, reason, false);
        java.util.List<String> errors = report.validate();
        if (!errors.isEmpty()) throw new IOException("report incomplete: " + errors);
        Files.writeString(json, report.toJson(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        Files.writeString(md, report.toMarkdown(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        return json;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
