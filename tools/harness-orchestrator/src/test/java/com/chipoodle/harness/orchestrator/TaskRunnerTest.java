package com.chipoodle.harness.orchestrator;

import java.nio.file.*;
import java.time.Duration;
import java.util.List;

public final class TaskRunnerTest {
    private static int fail;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("dsh-runner-test-");
        Path repo = root.resolve("repo"); Files.createDirectories(repo);
        Path base = root.resolve("integration");
        GitStateProvider git = new GitStateProvider() {
            public State read(Path r) { return new State("feature/autonomous-village", "abc", false, null); }
        };
        ProcessAdapter ok = (c, in, t) -> new ProcessAdapter.Result(0,
                "{\"type\":\"session\",\"sessionId\":\"s\"}\n{\"type\":\"final\",\"text\":\"PONG\"}", "", 5, false);
        TaskRunner runner = new TaskRunner(base, git, ok, "dsh", "test", Duration.ofSeconds(2));

        Path task = write(root, "T1", repo, "{}", true, "abc");
        TaskContract t = runner.validate(task);
        check(t.taskId().equals("T1"), "validate T1");

        TaskRunner.Outcome first = runner.run(task);
        check(first.state() == Orchestrator.State.UNVERIFIED, "run -> UNVERIFIED");
        check(Files.exists(base.resolve("completed/T1.json")), "moved completed");
        check(Files.exists(base.resolve("reports/T1.json")), "report created");
        TaskRunner.Outcome dup = runner.run(task);
        check(dup.state() == Orchestrator.State.BLOCKED, "duplicate blocked");
        check(Files.exists(base.resolve("completed/T1.json")), "duplicate did not alter result");

        Path badBranch = write(root, "T2", repo, "{}", false, "abc");
        check(runner.run(badBranch).state() == Orchestrator.State.BLOCKED, "branch mismatch blocked");

        Path blockedAuth = write(root, "T3", repo, "{\"production_code\":true}", true, "abc");
        check(runner.run(blockedAuth).state() == Orchestrator.State.BLOCKED, "production auth blocked");

        RunLock lock = RunLock.acquire(base.resolve("locks"), "T4", ProcessHandle.current().pid(), "other");
        Path t4 = write(root, "T4", repo, "{}", true, "abc");
        check(runner.run(t4).state() == Orchestrator.State.BLOCKED, "second runner blocked");
        lock.close();

        Path interrupted = base.resolve("running/T5.json");
        Files.writeString(interrupted, "{}");
        Files.writeString(base.resolve("running/T5.meta"), "task=T5\npid=999999\nstarted=x\n");
        check(runner.status("T5").startsWith("running"), "interrupted visible; no retry");
        check(Files.exists(interrupted), "interrupted not auto-moved");

        check(Main.run(new String[]{"help"}) == 0, "help exit 0");
        check(Main.run(new String[]{"validate", task.toString()}) == 0, "validate exit 0");
        check(Main.run(new String[]{"validate", "no-such-file"}) == 2, "invalid validate exit 2");

        // HEAD mismatch
        GitStateProvider wrongHeadGit = (r) -> new GitStateProvider.State("feature/autonomous-village", "def", false, null);
        TaskRunner wrongHead = new TaskRunner(root.resolve("integration2"), wrongHeadGit, ok, "dsh", "t", Duration.ofSeconds(2));
        check(wrongHead.run(write(root, "T8", repo, "{}", true, "abc")).state() == Orchestrator.State.BLOCKED, "HEAD mismatch blocked");

        // dirty baseline
        GitStateProvider dirtyGit = (r) -> new GitStateProvider.State("feature/autonomous-village", "abc", true, null);
        TaskRunner dirty = new TaskRunner(root.resolve("integration3"), dirtyGit, ok, "dsh", "t", Duration.ofSeconds(2));
        check(dirty.run(write(root, "T9", repo, "{}", true, "abc")).state() == Orchestrator.State.BLOCKED, "dirty worktree blocked");

        // Git failure
        GitStateProvider failGit = (r) -> new GitStateProvider.State(null, null, false, "git boom");
        TaskRunner failRunner = new TaskRunner(root.resolve("integration4"), failGit, ok, "dsh", "t", Duration.ofSeconds(2));
        check(failRunner.run(write(root, "T10", repo, "{}", true, "abc")).state() == Orchestrator.State.BLOCKED, "git failure blocked");

        // Report collision preflight
        Path collisionBase = root.resolve("integration5");
        Files.createDirectories(collisionBase.resolve("reports"));
        Files.writeString(collisionBase.resolve("reports/T11.json"), "{}");
        TaskRunner collision = new TaskRunner(collisionBase, git, ok, "dsh", "t", Duration.ofSeconds(2));
        check(collision.run(write(root, "T11", repo, "{}", true, "abc")).state() == Orchestrator.State.BLOCKED, "report collision blocked");
        check(!Files.exists(collisionBase.resolve("completed/T11.json")), "collision did not execute");

        // Markdown report collision\n        Path mdBase = root.resolve("integration8");\n        Files.createDirectories(mdBase.resolve("reports"));\n        Files.writeString(mdBase.resolve("reports/T15.md"), "keep");\n        TaskRunner mdCollision = new TaskRunner(mdBase, git, ok, "dsh", "t", Duration.ofSeconds(2));\n        check(mdCollision.run(write(root, "T15", repo, "{}", true, "abc")).state() == Orchestrator.State.BLOCKED, "md report collision blocked");\n        check("keep".equals(Files.readString(mdBase.resolve("reports/T15.md"))), "md collision preserved");\n\n        // Lock ownership mismatch and stale diagnostic
        TaskRunner lockRunner = new TaskRunner(root.resolve("integration6"), git, ok, "dsh", "t", Duration.ofSeconds(2));
        RunLock l = RunLock.acquire(lockRunnerBase(root, "integration6"), "T12", 1, "owner");
        Files.writeString(lockRunnerBase(root, "integration6").resolve("runner.lock"), "task=X\ntoken=other\n");
        boolean refused = false; try { l.close(); } catch (java.io.IOException e) { refused = true; }
        check(refused, "lock ownership mismatch refused");
        Files.deleteIfExists(lockRunnerBase(root, "integration6").resolve("runner.lock"));
        Files.writeString(lockRunnerBase(root, "integration6").resolve("runner.lock"), "task=OLD\npid=999999\n");
        boolean stale = false; try { RunLock.acquire(lockRunnerBase(root, "integration6"), "T13", 1, "x"); }
        catch (java.io.IOException e) { stale = e.getMessage().contains("T13") || e.getMessage().contains("task="); }
        check(stale, "stale lock diagnostic/refusal");
        Files.deleteIfExists(lockRunnerBase(root, "integration6").resolve("runner.lock"));

        // Interrupted classification
        Path interruptedBase = root.resolve("integration7");
        TaskRunner rec = new TaskRunner(interruptedBase, git, ok, "dsh", "t", Duration.ofSeconds(2));
        Files.writeString(interruptedBase.resolve("running/T14.json"), "{}");
        Files.writeString(interruptedBase.resolve("running/T14.meta"), "task=T14\npid=999999\nrunner=gone\nstarted=x\n");
        java.util.List<String> recs = rec.classifyInterrupted();
        check(recs.stream().anyMatch(s -> s.contains("T14")), "interrupted classified");
        check(Files.exists(interruptedBase.resolve("running/T14.json")), "interrupted not auto-moved");
        check(Files.exists(interruptedBase.resolve("reports/T14-recovery.json")), "recovery report written");

        // CLI status/report
        check(Main.run(new String[]{"status", "T1"}) == 0, "status exit 0");
        check(Main.run(new String[]{"report", "T1"}) == 0, "report exit 0");
        check(Main.run(new String[]{"status", "unknown-id"}) == 0, "unknown status exit 0");        if (fail > 0) { System.out.println("TASK_RUNNER_TEST: FAIL (" + fail + ")"); System.exit(1); }
        System.out.println("TASK_RUNNER_TEST: OK");
    }

    private static Path lockRunnerBase(Path root, String name) throws Exception {
        return root.resolve(name).resolve("locks");
    }    private static Path write(Path root, String id, Path repo, String auth, boolean goodBranch, String head) throws Exception {
        String branch = goodBranch ? "feature/autonomous-village" : "wrong-branch";
        String json = "{\"schema\":\"devilrpg.task/v1\",\"task_id\":\"" + id + "\",\"repository\":\""
                + repo.toString().replace("\\", "\\\\") + "\",\"branch\":\"" + branch
                + "\",\"expected_head\":\"" + head + "\",\"allowed_paths\":[\"tools/\"],\"forbidden_paths\":[\"src/\"],"
                + "\"objective\":\"PONG\",\"acceptance\":[\"no change\"],\"timeout_seconds\":30,"
                + "\"profile\":\"headless\",\"authorization\":" + auth + "}";
        Path f = root.resolve(id + ".json"); Files.writeString(f, json); return f;
    }
    private static void check(boolean ok, String name) { if (!ok) { fail++; System.out.println("  FAIL: " + name); } }
}
