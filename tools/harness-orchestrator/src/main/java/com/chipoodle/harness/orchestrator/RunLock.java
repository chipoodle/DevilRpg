package com.chipoodle.harness.orchestrator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;

/** Atomic single-runner lock with ownership token; no stealing. */
public final class RunLock implements AutoCloseable {
    private final Path file;
    private final String token;
    private boolean held;

    private RunLock(Path file, String token) { this.file = file; this.token = token; this.held = true; }

    public static RunLock acquire(Path locksDir, String taskId, long pid, String runnerId) throws IOException {
        Files.createDirectories(locksDir);
        Path file = locksDir.resolve("runner.lock");
        String token = UUID.randomUUID().toString();
        String body = "task=" + taskId + "\npid=" + pid + "\nrunner=" + runnerId
                + "\ntoken=" + token + "\ncreated=" + Instant.now() + "\n";
        try {
            Files.writeString(file, body, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (java.nio.file.FileAlreadyExistsException e) {
            String existing = Files.exists(file) ? Files.readString(file) : "";
            throw new IOException("runner lock already held:\n" + existing, e);
        }
        return new RunLock(file, token);
    }

    public String token() { return token; }

    @Override public void close() throws IOException {
        if (!held) return;
        String body = Files.exists(file) ? Files.readString(file) : "";
        if (!body.contains("token=" + token)) throw new IOException("lock ownership changed; refusing to delete");
        Files.delete(file);
        held = false;
    }
}
