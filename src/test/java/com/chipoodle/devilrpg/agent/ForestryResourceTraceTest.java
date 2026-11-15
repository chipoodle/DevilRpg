package com.chipoodle.devilrpg.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Focused tests for the pure A8-01 ledger: events, transfers and reconciliation. */
public final class ForestryResourceTraceTest {

    private static final String LOG = "minecraft:oak_log";

    public static void main(String[] args) {
        uniqueIdsAndAttribution();
        transferCorrelation();
        partialAndFailed();
        twoWorkersSharedWarehouse();
        consumedFuelSink();
        finalizationGuards();
        systemStockMutations();
        unattributedAndOverflow();
        disabledByDefault();
        System.out.println("FORESTRY_RESOURCE_TRACE_TEST: OK");
    }

    private static void uniqueIdsAndAttribution() {
        UUID a = UUID.randomUUID();
        UUID task = UUID.randomUUID();
        ForestryResourceTrace t = new ForestryResourceTrace("run-1", "fixture-1");
        t.begin(workers(a, 0), warehouse(10));
        t.harvest(a, task, LOG, 5, 1, "minecraft:overworld", 123);
        t.inventoryInsert(a, task, LOG, 5, "flow", 1, "minecraft:overworld", 123);
        t.deposit(a, task, LOG, 5, "flow", 2, "minecraft:overworld", 123);
        ForestryResourceTrace.Report r = t.end(workers(a, 0), warehouse(15), 3);
        check(r.complete(), "balanced single actor");
        check(t.events().size() == 3, "three events");
        check(t.events().stream().map(ForestryResourceTrace.Event::id).distinct().count() == 3,
                "unique event ids");
        ForestryResourceTrace.Event harvest = t.events().get(0);
        check(harvest.worker().equals(a) && harvest.task().equals(task), "worker/task attribution");
    }

    private static void transferCorrelation() {
        UUID a = UUID.randomUUID();
        UUID task = UUID.randomUUID();
        ForestryResourceTrace t = new ForestryResourceTrace("run-2", "f");
        t.begin(workers(a, 0), warehouse(0));
        String flow = t.harvest(a, task, LOG, 4, 1, "d", 1);
        t.inventoryInsert(a, task, LOG, 4, flow, 1, "d", 1);
        t.deposit(a, task, LOG, 4, flow, 2, "d", 1);
        ForestryResourceTrace.Report r = t.end(workers(a, 0), warehouse(4), 3);
        check(r.complete(), "transfer balanced");
        check(t.events().stream().allMatch(e -> flow.equals(e.transferId())),
                "harvest/insert/deposit share transfer id");
    }

    private static void partialAndFailed() {
        UUID a = UUID.randomUUID();
        UUID task = UUID.randomUUID();
        ForestryResourceTrace t = new ForestryResourceTrace("run-3", "f");
        t.begin(workers(a, 0), warehouse(10));
        String flow = t.harvest(a, task, LOG, 5, 1, "d", 1);
        t.inventoryInsert(a, task, LOG, 3, flow, 1, "d", 1);
        t.deposit(a, task, LOG, 2, flow, 2, "d", 1);
        t.drop(a, task, LOG, 2, flow, 3, "d", 1);
        ForestryResourceTrace.Report r = t.end(workers(a, 1), warehouse(12), 4);
        check(r.complete(), "partial + drop balanced");
        check(t.events().stream().anyMatch(e -> e.kind() == ForestryResourceTrace.Kind.DROP
                && e.qty() == 2), "drop recorded");

        ForestryResourceTrace t2 = new ForestryResourceTrace("run-4", "f");
        t2.begin(workers(a, 0), warehouse(10));
        t2.deposit(a, task, LOG, 0, null, 1, "d", 1);
        ForestryResourceTrace.Report r2 = t2.end(workers(a, 0), warehouse(10), 2);
        check(r2.complete(), "failed zero transfer does not unbalance");
        check(t2.events().get(0).result().equals("FAILED"), "failed transfer result");
    }

    private static void twoWorkersSharedWarehouse() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        ForestryResourceTrace t = new ForestryResourceTrace("run-5", "f");
        Map<UUID, Map<String, Integer>> initial = workers(a, 0);
        initial.putAll(workers(b, 0));
        t.begin(initial, warehouse(10));
        String fa = t.harvest(a, UUID.randomUUID(), LOG, 5, 1, "d", 1);
        t.deposit(a, null, LOG, 5, fa, 2, "d", 1);
        String fb = t.harvest(b, UUID.randomUUID(), LOG, 3, 3, "d", 2);
        t.deposit(b, null, LOG, 3, fb, 4, "d", 2);
        Map<UUID, Map<String, Integer>> fin = workers(a, 0);
        fin.putAll(workers(b, 0));
        ForestryResourceTrace.Report r = t.end(fin, warehouse(18), 5);
        check(r.complete(), "two workers complete");
        check(r.actors().size() == 2, "two actor reports");
        check(r.actors().stream().allMatch(ForestryResourceTrace.ActorReport::balanced),
                "per-actor balanced");
        check(zero(r.globalDelta()), "global balanced");
        check(zero(r.warehouseDelta()), "warehouse balanced");
        check(t.events().stream().filter(e -> e.kind() == ForestryResourceTrace.Kind.DEPOSIT)
                .anyMatch(e -> a.equals(e.worker())), "deposit attributed to A");
        check(t.events().stream().filter(e -> e.kind() == ForestryResourceTrace.Kind.DEPOSIT)
                .anyMatch(e -> b.equals(e.worker())), "deposit attributed to B");
    }

    private static void consumedFuelSink() {
        UUID cook = UUID.randomUUID();
        ForestryResourceTrace t = new ForestryResourceTrace("run-5b", "f");
        t.begin(workers(cook, 0), warehouse(35));
        t.consumed(cook, null, LOG, 4, null, 1, "d", 1); // warehouse -> cook inventory
        t.burned(cook, null, LOG, 1, null, 2, "d", 1);   // one log burned
        ForestryResourceTrace.Report r = t.end(workers(cook, 3), warehouse(31), 3);
        check(r.complete(), "fuel withdrawal + burn balanced");
        check(zero(r.globalDelta()), "global includes burn sink");
        check(zero(r.warehouseDelta()), "warehouse includes withdrawal");
        check(t.events().stream().anyMatch(e -> e.kind() == ForestryResourceTrace.Kind.CONSUMED),
                "burn event recorded");
    }

    private static void finalizationGuards() {
        UUID a = UUID.randomUUID();
        ForestryResourceTrace t = new ForestryResourceTrace("run-5c", "f");
        t.begin(workers(a, 0), warehouse(0));
        t.harvest(a, null, LOG, 2, 1, "d", 1);
        ForestryResourceTrace.Report notEnded = t.report(2);
        check(!notEnded.complete() && !notEnded.ended(), "no end() is incomplete");

        ForestryResourceTrace.Report first = t.end(workers(a, 2), warehouse(0), 3);
        check(first.complete(), "normal finalization");
        ForestryResourceTrace.Report second = t.end(workers(a, 0), warehouse(2), 4);
        check(second.complete(), "duplicate finalization remains complete");
        check(second.actors().get(0).finalInventory().getOrDefault(LOG, 0) == 2,
                "duplicate end cannot rewrite final inventory");
        check(second.eventCount() == first.eventCount(), "duplicate end adds no events");
    }

    private static void systemStockMutations() {
        ForestryResourceTrace t = new ForestryResourceTrace("run-5d", "f");
        t.begin(new LinkedHashMap<>(), warehouse(10));
        t.systemCreated(LOG, 5, 1, "remesa");
        t.systemExternalIn(LOG, 2, 2, "old-shed");
        t.systemRemoved(LOG, 3, 3, "removal");
        t.systemDrop(LOG, 1, 4, "overflow");
        t.systemRelocated(LOG, 9, 5, "internal");
        ForestryResourceTrace.Report r = t.end(new LinkedHashMap<>(), warehouse(13), 6);
        check(r.complete(), "system stock balanced");
        check(zero(r.globalDelta()), "global system delta");
        check(zero(r.warehouseDelta()), "warehouse system delta");
        check(t.events().stream().anyMatch(e -> e.kind() == ForestryResourceTrace.Kind.SYSTEM_CREATED),
                "system creation event");
        check(t.events().stream().anyMatch(e -> e.kind() == ForestryResourceTrace.Kind.SYSTEM_RELOCATED),
                "system relocation event");
    }

    private static void unattributedAndOverflow() {
        ForestryResourceTrace t = new ForestryResourceTrace("run-6", "f");
        t.begin(workers(UUID.randomUUID(), 0), warehouse(0));
        t.unattributed(ForestryResourceTrace.Kind.DEPOSIT, LOG, 1, 1, "d", 1, "unknown actor");
        ForestryResourceTrace.Report r = t.end(workers(UUID.randomUUID(), 0), warehouse(0), 2);
        check(!r.complete() && r.unattributed(), "unattributed forces INCOMPLETE");

        UUID a = UUID.randomUUID();
        ForestryResourceTrace t2 = new ForestryResourceTrace("run-7", "f");
        t2.begin(workers(a, 0), warehouse(0));
        for (int i = 0; i < ForestryResourceTrace.MAX_EVENTS + 1; i++) {
            t2.harvest(a, null, LOG, 1, i, "d", i);
        }
        check(t2.truncated(), "event limit sets truncation");
        ForestryResourceTrace.Report r2 = t2.end(workers(a, 0), warehouse(0), 99);
        check(!r2.complete() && r2.truncated(), "truncation forces INCOMPLETE");
    }

    private static void disabledByDefault() {
        String old = System.getProperty(ForestryResourceTrace.PROPERTY);
        System.clearProperty(ForestryResourceTrace.PROPERTY);
        check(!ForestryResourceTrace.enabledByDefault(), "diagnostics default-off");
        if (old != null) System.setProperty(ForestryResourceTrace.PROPERTY, old);
    }

    private static Map<UUID, Map<String, Integer>> workers(UUID worker, int logs) {
        Map<UUID, Map<String, Integer>> out = new LinkedHashMap<>();
        Map<String, Integer> inv = new LinkedHashMap<>();
        if (logs > 0) inv.put(LOG, logs);
        out.put(worker, inv);
        return out;
    }

    private static Map<String, Integer> warehouse(int logs) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (logs > 0) out.put(LOG, logs);
        return out;
    }

    private static boolean zero(Map<String, Integer> map) {
        return map.values().stream().allMatch(v -> v == 0);
    }

    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError("FAILED: " + what);
    }
}
