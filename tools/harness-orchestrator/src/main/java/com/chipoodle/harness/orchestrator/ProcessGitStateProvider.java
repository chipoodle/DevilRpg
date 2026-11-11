package com.chipoodle.harness.orchestrator;

import java.nio.file.Path;
import java.util.List;

/** Argument-safe read-only Git inspection; no mutation. */
public final class ProcessGitStateProvider implements GitStateProvider {
    @Override public State read(Path repo) {
        try {
            String branch = git(repo, "rev-parse", "--abbrev-ref", "HEAD").trim();
            String head = git(repo, "rev-parse", "HEAD").trim();
            boolean dirty = !git(repo, "status", "--porcelain").isBlank();
            return new State(branch, head, dirty, null);
        } catch (Exception e) { return new State(null, null, false, e.getMessage()); }
    }
    @Override public List<String> changedFiles(Path repo) {
        try { return git(repo, "status", "--porcelain").lines().filter(s -> !s.isBlank()).toList(); }
        catch (Exception e) { return List.of(); }
    }
    private static String git(Path repo, String... args) throws Exception {
        List<String> c = new java.util.ArrayList<>(List.of("git", "-C", repo.toString()));
        c.addAll(List.of(args));
        Process p = new ProcessBuilder(c).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (p.waitFor() != 0) throw new IllegalStateException("git failed: " + out.trim());
        return out;
    }
}
