package com.chipoodle.harness.orchestrator;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

public interface ProcessAdapter {
    record Result(int exitCode, String stdout, String stderr, long elapsedMillis, boolean timedOut) {}
    Result run(List<String> command, String stdin, Duration timeout) throws IOException, InterruptedException;
}
