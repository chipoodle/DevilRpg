package com.chipoodle.harness.orchestrator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Argument-safe process adapter; no shell, no credential discovery. */
public final class RealProcessAdapter implements ProcessAdapter {
    @Override
    public Result run(List<String> command, String stdin, Duration timeout) throws IOException, InterruptedException {
        long start = System.nanoTime();
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);
        Process p = pb.start();
        Capture out = reader(p.getInputStream());
        Capture err = reader(p.getErrorStream());
        try (var w = p.getOutputStream()) {
            w.write(stdin.getBytes(StandardCharsets.UTF_8));
        }
        boolean finished = p.waitFor(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
        boolean timedOut = !finished;
        if (!finished) {
            p.descendants().forEach(ProcessHandle::destroy);
            p.destroy();
            p.waitFor(5, TimeUnit.SECONDS);
        }
        out.thread.join(2000);
        err.thread.join(2000);
        long elapsed = (System.nanoTime() - start) / 1_000_000L;
        return new Result(p.isAlive() ? -1 : p.exitValue(), out.text(), err.text(), elapsed, timedOut);
    }

    private record Capture(ByteArrayOutputStream bytes, Thread thread) {
        String text() { return bytes.toString(StandardCharsets.UTF_8); }
    }

    private static Capture reader(InputStream in) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        Thread t = new Thread(() -> {
            try { in.transferTo(b); } catch (IOException ignored) { }
        });
        t.setDaemon(true); t.start();
        return new Capture(b, t);
    }
}
