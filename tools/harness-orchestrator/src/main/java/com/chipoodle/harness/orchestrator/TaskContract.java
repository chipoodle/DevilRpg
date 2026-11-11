package com.chipoodle.harness.orchestrator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Validated devilrpg.task/v1 contract. All authorization flags default to false. */
public record TaskContract(
        String schema, String taskId, Path repository, String branch, String expectedHead,
        List<String> allowedPaths, List<String> forbiddenPaths, String objective,
        List<String> acceptance, int timeoutSeconds, String profile, Authorization authorization) {

    public record Authorization(boolean commit, boolean push, boolean protectedWorlds,
                                boolean network, boolean productionCode, boolean testChanges) {}

    public static TaskContract parse(String json) {
        Map<String, Object> m = Json.obj(json);
        String schema = str(m, "schema", "");
        if (!"devilrpg.task/v1".equals(schema)) throw new IllegalArgumentException("unknown schema: " + schema);
        String id = str(m, "task_id", "");
        if (id.isBlank()) throw new IllegalArgumentException("task_id required");
        String repo = str(m, "repository", "");
        if (repo.isBlank()) throw new IllegalArgumentException("repository required");
        Path repository;
        try { repository = Path.of(repo).toAbsolutePath().normalize(); }
        catch (RuntimeException e) { throw new IllegalArgumentException("invalid repository path"); }
        if (!repository.toFile().isDirectory()) throw new IllegalArgumentException("repository is not a directory: " + repository);
        String branch = str(m, "branch", "");
        if (branch.isBlank()) throw new IllegalArgumentException("branch required");
        String head = str(m, "expected_head", "");
        List<String> allowed = strings(m, "allowed_paths");
        List<String> forbidden = strings(m, "forbidden_paths");
        if (allowed.isEmpty()) throw new IllegalArgumentException("allowed_paths required");
        for (String p : allowed) path(p, "allowed");
        for (String p : forbidden) path(p, "forbidden");
        for (String a : allowed) for (String f : forbidden) if (overlap(a, f)) throw new IllegalArgumentException("allowed/forbidden path conflict: " + a + " vs " + f);
        String objective = str(m, "objective", "");
        if (objective.isBlank()) throw new IllegalArgumentException("objective required");
        List<String> acceptance = strings(m, "acceptance");
        if (acceptance.isEmpty()) throw new IllegalArgumentException("acceptance required");
        long timeout = num(m, "timeout_seconds", 1800);
        if (timeout < 1 || timeout > 86_400) throw new IllegalArgumentException("timeout_seconds out of range");
        String profile = str(m, "profile", "headless");
        if (!profile.equals("headless")) throw new IllegalArgumentException("only the headless profile is supported by this skeleton");
        Map<String, Object> a = m.get("authorization") instanceof Map<?, ?> mm ? cast(mm) : Map.of();
        Authorization auth = new Authorization(
                bool(a, "commit"), bool(a, "push"), bool(a, "protected_worlds"),
                bool(a, "network"), bool(a, "production_code"), bool(a, "test_changes"));
        if (auth.protectedWorlds()) throw new IllegalArgumentException("protected_worlds authorization is not supported");
        if (auth.network()) throw new IllegalArgumentException("network authorization is not supported by this skeleton");
        return new TaskContract(schema, id, repository, branch, head, List.copyOf(allowed), List.copyOf(forbidden),
                objective, List.copyOf(acceptance), (int) timeout, profile, auth);
    }

    public boolean productionRequested() { return authorization.productionCode(); }
    public boolean testsRequested() { return authorization.testChanges(); }

    private static Map<String, Object> cast(Map<?, ?> m) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        m.forEach((k, v) -> out.put(String.valueOf(k), v));
        return out;
    }
    private static String str(Map<String, Object> m, String k, String d) {
        Object v = m.get(k); return v instanceof String s ? s : d;
    }
    private static long num(Map<String, Object> m, String k, long d) {
        Object v = m.get(k); return v instanceof Number n ? n.longValue() : d;
    }
    private static boolean bool(Map<String, Object> m, String k) {
        Object v = m.get(k); return v instanceof Boolean b ? b : false;
    }
    private static List<String> strings(Map<String, Object> m, String k) {
        List<String> out = new ArrayList<>();
        if (m.get(k) instanceof List<?> l) for (Object o : l) if (o instanceof String s) out.add(s);
        return out;
    }
    private static void path(String p, String kind) {
        if (p.isBlank() || p.contains("..")) throw new IllegalArgumentException("invalid " + kind + " path: " + p);
        if (p.startsWith("run/saves/New World") || p.startsWith("run\\saves\\New World")
                || p.startsWith("run/saves/A2-Observation") || p.startsWith("run\\saves\\A2-Observation"))
            throw new IllegalArgumentException("protected world path forbidden: " + p);
    }
    private static boolean overlap(String a, String b) {
        String x = a.replace('\\', '/').replaceAll("/+$", ""), y = b.replace('\\', '/').replaceAll("/+$", "");
        return x.equals(y) || x.startsWith(y + "/") || y.startsWith(x + "/");
    }
}
