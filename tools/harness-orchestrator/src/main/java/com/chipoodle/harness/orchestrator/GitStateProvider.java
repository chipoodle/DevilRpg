package com.chipoodle.harness.orchestrator;

import java.nio.file.Path;
import java.util.List;

public interface GitStateProvider {
    record State(String branch, String head, boolean dirty, String error) {}
    State read(Path repository);
    default List<String> changedFiles(Path repository) { return List.of(); }
}
