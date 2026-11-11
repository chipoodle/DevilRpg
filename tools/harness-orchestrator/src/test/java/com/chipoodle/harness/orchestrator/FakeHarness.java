package com.chipoodle.harness.orchestrator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Deterministic test-only executable; never invokes Harness/Gradle/Minecraft/Git. */
public final class FakeHarness {
    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8));
        String scenario = args.length > 0 ? args[0] : "normal";
        switch (scenario) {
            case "normal" -> {
                System.out.println("{\"type\":\"session\",\"sessionId\":\"fake\"}");
                System.out.println("{\"type\":\"status\",\"phase\":\"turn_start\",\"turn\":1}");
                System.out.println("{\"type\":\"final\",\"text\":\"PONG\"}");
                System.err.println("fake-stderr");
            }
            case "echo" -> {
                String in = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
                System.out.println("{\"type\":\"session\",\"sessionId\":\"fake\"}");
                System.out.println("{\"type\":\"final\",\"text\":\"" + esc(in.trim()) + "\"}");
            }
            case "empty" -> System.out.println("{\"type\":\"final\",\"text\":\"\"}");
            case "malformed" -> {
                System.out.println("not-json");
                System.out.println("{\"type\":\"final\",\"text\":\"X\"}");
            }
            case "unknown" -> {
                System.out.println("{\"type\":\"future_event\",\"x\":1}");
                System.out.println("{\"type\":\"final\",\"text\":\"X\"}");
            }
            case "nonzero" -> { System.out.println("{\"type\":\"final\",\"text\":\"X\"}"); System.exit(7); }
            case "args" -> System.out.println("{\"type\":\"final\",\"text\":\"" + esc(String.join("|", args)) + "\"}");
            case "hang" -> {
                if (args.length > 1) Files.writeString(Path.of(args[1]), Long.toString(ProcessHandle.current().pid()));
                Thread.sleep(30_000);
            }
            default -> { System.err.println("unknown scenario"); System.exit(9); }
        }
        System.out.flush();
    }
    private static String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"); }
}
