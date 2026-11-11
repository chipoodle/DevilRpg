package com.chipoodle.harness.orchestrator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class RealProcessAdapterTest {
    private static int fail;

    public static void main(String[] args) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        String cp = System.getProperty("java.class.path");
        Path dir = Path.of("build", "integration", "adapter-test-" + System.nanoTime());
        Files.createDirectories(dir);
        RealProcessAdapter a = new RealProcessAdapter();

        ProcessAdapter.Result normal = a.run(cmd(java, cp, "normal"), "", Duration.ofSeconds(5));
        check(normal.exitCode() == 0, "normal exit 0");
        check(normal.stdout().contains("\"final\""), "normal final captured");
        check(normal.stderr().contains("fake-stderr"), "stderr separated");
        check(!normal.timedOut(), "normal not timeout");

        ProcessAdapter.Result echo = a.run(cmd(java, cp, "echo"), "hola ñ\n", Duration.ofSeconds(5));
        check(echo.stdout().contains("hola ñ"), "stdin UTF-8 delivered");

        ProcessAdapter.Result empty = a.run(cmd(java, cp, "empty"), "", Duration.ofSeconds(5));
        check(NdjsonParser.parse(empty.stdout()).hasFinal(), "empty final event captured");
        check("".equals(NdjsonParser.parse(empty.stdout()).finalText()), "empty final text");

        ProcessAdapter.Result malformed = a.run(cmd(java, cp, "malformed"), "", Duration.ofSeconds(5));
        check(NdjsonParser.parse(malformed.stdout()).malformed().size() == 1, "malformed line captured");

        ProcessAdapter.Result unknown = a.run(cmd(java, cp, "unknown"), "", Duration.ofSeconds(5));
        check(NdjsonParser.parse(unknown.stdout()).events().stream().anyMatch(e -> e.type().equals("future_event")), "unknown event captured");

        ProcessAdapter.Result nonzero = a.run(cmd(java, cp, "nonzero"), "", Duration.ofSeconds(5));
        check(nonzero.exitCode() == 7, "nonzero exit captured");

        List<String> argCmd = cmd(java, cp, "args");
        argCmd.add("a b");
        argCmd.add("quote\"x");
        ProcessAdapter.Result arg = a.run(argCmd, "", Duration.ofSeconds(5));
        check(arg.stdout().contains("a b"), "argument with space preserved");
        check(arg.stdout().contains("quote"), "quoted argument captured");

        Path pidFile = dir.resolve("hang.pid");
        List<String> hang = cmd(java, cp, "hang");
        hang.add(pidFile.toString());
        ProcessAdapter.Result timeout = a.run(hang, "", Duration.ofMillis(700));
        check(timeout.timedOut(), "timeout classified");
        Thread.sleep(1200);
        long pid = Long.parseLong(Files.readString(pidFile).trim());
        boolean alive = ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        check(!alive, "timed-out process terminated; no orphan");

        if (fail > 0) { System.out.println("REAL_PROCESS_ADAPTER_TEST: FAIL (" + fail + ")"); System.exit(1); }
        System.out.println("REAL_PROCESS_ADAPTER_TEST: OK");
    }

    private static List<String> cmd(String java, String cp, String scenario) {
        List<String> c = new ArrayList<>(List.of(java, "-cp", cp,
                "com.chipoodle.harness.orchestrator.FakeHarness", scenario));
        return c;
    }
    private static void check(boolean ok, String name) { if (!ok) { fail++; System.out.println("  FAIL: " + name); } }
}
