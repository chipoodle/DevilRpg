package com.chipoodle.harness.orchestrator;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public final class OrchestratorTest {
    private static int fail;

    public static void main(String[] args) {
        String repo = Path.of(".").toAbsolutePath().normalize().toString().replace("\\", "\\\\");
        String valid = """
          {"schema":"devilrpg.task/v1","task_id":"T1","repository":"%s",
           "branch":"feature/autonomous-village","expected_head":"abc",
           "allowed_paths":["tools/harness-orchestrator"],
           "forbidden_paths":["src/"],
           "objective":"do nothing","acceptance":["no change"],
           "timeout_seconds":30,"profile":"headless",
           "authorization":{}}
        """.formatted(repo);
        TaskContract t = TaskContract.parse(valid);
        check(t.authorization().commit() == false && t.authorization().push() == false, "auth defaults false");
        check(!t.productionRequested(), "production default false");

        TaskContract commit = TaskContract.parse(valid.replace("\"authorization\":{}", "\"authorization\":{\"commit\":true}"));
        check(commit.authorization().commit(), "explicit commit parsed true");
        Orchestrator blocked = new Orchestrator(fake(0, "{\"type\":\"final\",\"text\":\"x\"}", false), "dsh", Duration.ofSeconds(1));
        check(blocked.execute(commit).state() == Orchestrator.State.BLOCKED, "commit request BLOCKED");
        expectFail(valid.replace("tools/harness-orchestrator", "src/main/java"));
        expectFail(valid.replace("\"schema\":\"devilrpg.task/v1\"", "\"schema\":\"v9\""));

        NdjsonParser.Result ok = NdjsonParser.parse(
                "{\"type\":\"session\",\"sessionId\":\"s\"}\n" +
                "{\"type\":\"final\",\"text\":\"PONG\"}\n");
        check(ok.hasFinal() && "PONG".equals(ok.finalText()), "final parsed");
        NdjsonParser.Result empty = NdjsonParser.parse("{\"type\":\"final\",\"text\":\"\"}");
        check(empty.hasFinal() && "".equals(empty.finalText()), "empty final parsed");
        NdjsonParser.Result missing = NdjsonParser.parse("{\"type\":\"status\",\"phase\":\"turn_start\"}");
        check(!missing.hasFinal(), "missing final detected");
        NdjsonParser.Result bad = NdjsonParser.parse("not-json\n{\"type\":\"final\",\"text\":\"x\"}");
        check(bad.malformed().size() == 1, "malformed line detected");
        NdjsonParser.Result err = NdjsonParser.parse(
                "{\"type\":\"status\",\"phase\":\"turn_end\",\"reason\":{\"kind\":\"error\",\"error\":{\"code\":\"MISSING_CREDENTIAL\"}}}");
        check(err.hasError() && "MISSING_CREDENTIAL".equals(err.errorCode()), "error parsed");

        Orchestrator o = new Orchestrator(fake(0, "{\"type\":\"final\",\"text\":\"PONG\"}", false),
                "dsh", Duration.ofSeconds(1));
        check(o.execute(t).state() == Orchestrator.State.UNVERIFIED, "zero exit is UNVERIFIED");
        Orchestrator badExit = new Orchestrator(fake(1, "", false), "dsh", Duration.ofSeconds(1));
        check(badExit.execute(t).state() == Orchestrator.State.FAILED, "nonzero exit FAILED");
        Orchestrator timeout = new Orchestrator(fake(0, "", true), "dsh", Duration.ofSeconds(1));
        check(timeout.execute(t).state() == Orchestrator.State.TIMED_OUT, "timeout TIMED_OUT");
        Orchestrator emptyFinal = new Orchestrator(fake(0, "{\"type\":\"final\",\"text\":\"\"}", false), "dsh", Duration.ofSeconds(1));
        check(emptyFinal.execute(t).state() == Orchestrator.State.FAILED, "empty final FAILED");
        String report = o.execute(t).reportJson();
        check(report.contains("UNVERIFIED") && report.contains("PONG"), "report generated");

        if (fail > 0) { System.out.println("HARNESS_ORCHESTRATOR_TEST: FAIL (" + fail + ")"); System.exit(1); }
        System.out.println("HARNESS_ORCHESTRATOR_TEST: OK");
    }

    private static ProcessAdapter fake(int exit, String out, boolean timeout) {
        return (command, stdin, timeoutDuration) -> new ProcessAdapter.Result(exit, out, "", 1, timeout);
    }

    private static void expectFail(String json) {
        try { TaskContract.parse(json); fail++; System.out.println("  expected failure: " + json); }
        catch (IllegalArgumentException expected) { }
    }

    private static void check(boolean ok, String name) {
        if (!ok) { fail++; System.out.println("  FAIL: " + name); }
    }
}
