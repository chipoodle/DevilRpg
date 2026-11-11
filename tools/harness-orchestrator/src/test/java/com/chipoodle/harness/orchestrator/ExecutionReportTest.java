package com.chipoodle.harness.orchestrator;

import java.nio.file.*;
import java.util.List;
import java.util.Map;

public final class ExecutionReportTest {
    private static int fail;
    public static void main(String[] args) throws Exception {
        Path repo = Files.createTempDirectory("report-repo");
        String json = "{\"schema\":\"devilrpg.task/v1\",\"task_id\":\"R1\",\"repository\":\""
                + repo.toString().replace("\\", "\\\\")
                + "\",\"branch\":\"b\",\"expected_head\":\"h\",\"allowed_paths\":[\"x\"],"
                + "\"forbidden_paths\":[\"y\"],\"objective\":\"test ñ\",\"acceptance\":[\"ok\"],"
                + "\"timeout_seconds\":10,\"profile\":\"headless\",\"authorization\":{}}";
        TaskContract t = TaskContract.parse(json);
        GitStateProvider.State before = new GitStateProvider.State("b", "h", false, null);
        GitStateProvider.State after = new GitStateProvider.State("b", "h2", true, null);
        ProcessAdapter.Result p = new ProcessAdapter.Result(0,
                "{\"type\":\"session\",\"sessionId\":\"s\"}\n"
                + "{\"type\":\"status\",\"phase\":\"turn_start\"}\n"
                + "{\"type\":\"future\",\"x\":1}\n"
                + "not-json\n"
                + "{\"type\":\"final\",\"text\":\"PONG ñ\"}\n", "err ñ", 12, false);
        NdjsonParser.Result ev = NdjsonParser.parse(p.stdout());
        ExecutionReport r = ExecutionReport.of(t, Orchestrator.State.UNVERIFIED, p, ev, before, after,
                List.of("M foo"), "zero exit", true);
        check(r.validate().isEmpty(), "complete report valid: " + r.validate());
        Map<String,Object> root = Json.obj(r.toJson());
        check("devilrpg.report/v2".equals(root.get("schema")), "schema v2");
        check(((List<?>) root.get("events")).size() == 5, "event order/count incl malformed");
        check(r.toMarkdown().contains("\"schema\":\"devilrpg.report/v2\""), "markdown includes JSON");
        check(r.toJson().contains("PONG ñ"), "UTF-8 preserved");
        check(Json.obj(r.toJson()).containsKey("authorization"), "authorization section");

        ExecutionReport blocked = ExecutionReport.of(t, Orchestrator.State.BLOCKED, null, null, before, before,
                List.of(), "branch", false);
        check(blocked.validate().isEmpty(), "blocked report valid");
        check(blocked.toJson().contains("\"required\":true"), "recovery required");

        ExecutionReport bad = ExecutionReport.of(t, Orchestrator.State.SUCCEEDED, p, ev, before, after,
                List.of(), "bad", true);
        check(bad.validate().contains("SUCCEEDED not allowed"), "SUCCEEDED rejected");

        GitStateProvider.State unknown = new GitStateProvider.State(null, null, false, "git fail");
        ExecutionReport u = ExecutionReport.of(t, Orchestrator.State.UNVERIFIED, p, ev, unknown, unknown,
                List.of(), "unknown", true);
        check(u.toJson().contains("\"branch\":\"UNKNOWN\""), "unknown git not invented");

        if (fail > 0) { System.out.println("EXECUTION_REPORT_TEST: FAIL (" + fail + ")"); System.exit(1); }
        System.out.println("EXECUTION_REPORT_TEST: OK");
    }
    private static void check(boolean ok, String name) { if (!ok) { fail++; System.out.println("  FAIL: " + name); } }
}
