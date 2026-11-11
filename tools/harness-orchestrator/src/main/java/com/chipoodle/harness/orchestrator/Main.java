package com.chipoodle.harness.orchestrator;

import java.nio.file.Path;
import java.time.Duration;

public final class Main {
    public static void main(String[] args) throws Exception {
        int code = run(args);
        if (code != 0) System.exit(code);
    }

    public static int run(String[] args) throws Exception {
        if (args.length == 0 || "help".equals(args[0])) { usage(); return 0; }
        Path base = Path.of("build", "integration");
        TaskRunner runner = new TaskRunner(base, new ProcessGitStateProvider(), new RealProcessAdapter(),
                "dsh", "local-runner-" + ProcessHandle.current().pid(), Duration.ofSeconds(3600));
        switch (args[0]) {
            case "validate" -> {
                if (args.length != 2) { usage(); return 2; }
                try { TaskContract t = runner.validate(Path.of(args[1])); System.out.println("VALID " + t.taskId()); return 0; }
                catch (Exception e) { System.err.println("INVALID " + e.getMessage()); return 2; }
            }
            case "run" -> {
                if (args.length != 2) { usage(); return 2; }
                TaskRunner.Outcome o = runner.run(Path.of(args[1]));
                System.out.println(o.state() + " " + o.taskId() + " " + (o.report() == null ? "" : o.report()));
                return switch (o.state()) { case UNVERIFIED -> 3; case BLOCKED -> 4; case FAILED, TIMED_OUT -> 1; default -> 0; };
            }
            case "status" -> {
                System.out.println(args.length > 1 ? runner.status(args[1]) : "usage: status <task-id>");
                return 0;
            }
            case "report" -> {
                if (args.length < 2) { usage(); return 2; }
                String fmt = args.length > 3 && "--format".equals(args[2]) ? args[3] : "json";
                System.out.print(runner.report(args[1], fmt));
                return 0;
            }
            default -> { usage(); return 2; }
        }
    }

    private static void usage() {
        System.out.println("""
            Usage:
              dsh-runner help
              dsh-runner validate <task-file>
              dsh-runner run <task-file>
              dsh-runner status <task-id>
              dsh-runner report <task-id> [--format json|markdown]
            Exit: 0 ok, 1 failed/timed-out, 2 usage/invalid, 3 UNVERIFIED, 4 BLOCKED.
            """);
    }
}
