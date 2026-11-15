package com.chipoodle.devilrpg.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Default-off, bounded, per-actor ledger for forestry log resources.
 *
 * <p>Pure Java: the Minecraft-facing counts and hooks live in
 * {@code ForestryResourceTraceMinecraft}. This class owns the event schema, unique IDs, transfer
 * correlation, conservation equations and the INCOMPLETE flags. It never touches inventories.</p>
 *
 * <p>Accounting deliberately counts a transfer once: {@code DEPOSIT}/{@code WITHDRAWAL} are
 * transfer events, while {@code INVENTORY_INSERT} is informational and is not added to the
 * conservation equations. A harvest is a creation event; a drop/pickup is a world sink/source.</p>
 */
public final class ForestryResourceTrace {

    public static final int MAX_EVENTS = 2048;
    public static final String PROPERTY = "devilrpg.resourcetrace";

    public enum Kind { HARVEST, INVENTORY_INSERT, DEPOSIT, WITHDRAWAL, PICKUP, DROP, CONSUMED, SYSTEM_CREATED, SYSTEM_EXTERNAL_IN, SYSTEM_REMOVED, SYSTEM_DROP, SYSTEM_RELOCATED, UNATTRIBUTED }

    public record Event(long id, String transferId, Kind kind, UUID worker, UUID task,
                        String item, int qty, long tick, String dimension, long position,
                        String result, String detail) {
    }

    public record ActorReport(UUID worker, Map<String, Integer> initial,
                              Map<String, Integer> harvested, Map<String, Integer> inserted,
                              Map<String, Integer> deposited, Map<String, Integer> withdrawn,
                              Map<String, Integer> picked, Map<String, Integer> dropped,
                              Map<String, Integer> consumed, Map<String, Integer> finalInventory,
                              Map<String, Integer> delta) {
        public boolean balanced() {
            return delta.values().stream().allMatch(v -> v == 0);
        }
    }

    public record Report(String runId, String fixtureId, boolean complete, boolean started,
                         boolean ended, boolean truncated, boolean unattributed, int eventCount,
                         Map<String, Integer> globalDelta, Map<String, Integer> warehouseDelta,
                         List<ActorReport> actors) {
    }

    private static final class Actor {
        final Map<String, Integer> initial = new LinkedHashMap<>();
        final Map<String, Integer> harvested = new LinkedHashMap<>();
        final Map<String, Integer> inserted = new LinkedHashMap<>();
        final Map<String, Integer> deposited = new LinkedHashMap<>();
        final Map<String, Integer> withdrawn = new LinkedHashMap<>();
        final Map<String, Integer> picked = new LinkedHashMap<>();
        final Map<String, Integer> dropped = new LinkedHashMap<>();
        final Map<String, Integer> consumed = new LinkedHashMap<>();
        final Map<String, Integer> finalInventory = new LinkedHashMap<>();
    }

    private final String runId;
    private final String fixtureId;
    private final Map<UUID, Actor> actors = new LinkedHashMap<>();
    private final Map<String, Integer> initialWarehouse = new LinkedHashMap<>();
    private final Map<String, Integer> finalWarehouse = new LinkedHashMap<>();
    private final Map<String, Integer> warehouseDeposits = new LinkedHashMap<>();
    private final Map<String, Integer> warehouseWithdrawals = new LinkedHashMap<>();
    private final Map<String, Integer> systemCreated = new LinkedHashMap<>();
    private final Map<String, Integer> systemExternalIn = new LinkedHashMap<>();
    private final Map<String, Integer> systemRemoved = new LinkedHashMap<>();
    private final Map<String, Integer> systemDropped = new LinkedHashMap<>();
    private final List<Event> events = new ArrayList<>();
    private long eventSeq;
    private long transferSeq;
    private boolean started;
    private boolean ended;
    private boolean truncated;
    private boolean unattributed;

    public ForestryResourceTrace(String runId, String fixtureId) {
        this.runId = runId;
        this.fixtureId = fixtureId;
    }

    /** Default-off: diagnostics must be explicitly enabled with the system property. */
    public static boolean enabledByDefault() {
        return Boolean.getBoolean(PROPERTY);
    }

    public synchronized void begin(Map<UUID, Map<String, Integer>> workerManifest,
                                   Map<String, Integer> warehouseManifest) {
        if (started) {
            throw new IllegalStateException("trace already started");
        }
        started = true;
        for (Map.Entry<UUID, Map<String, Integer>> e : workerManifest.entrySet()) {
            Actor a = actors.computeIfAbsent(e.getKey(), k -> new Actor());
            putAll(a.initial, e.getValue());
        }
        putAll(initialWarehouse, warehouseManifest);
    }

    public synchronized String harvest(UUID worker, UUID task, String item, int qty, long tick,
                                       String dimension, long position) {
        String flow = newFlow();
        Actor a = actor(worker);
        add(a.harvested, item, qty);
        record(Kind.HARVEST, flow, worker, task, item, qty, tick, dimension, position,
                qty > 0 ? "OK" : "FAILED", "harvest");
        return flow;
    }

    /** Informational leg; not part of the conservation equation. */
    public synchronized void inventoryInsert(UUID worker, UUID task, String item, int qty,
                                             String flow, long tick, String dimension, long position) {
        Actor a = actor(worker);
        add(a.inserted, item, qty);
        record(Kind.INVENTORY_INSERT, flow, worker, task, item, qty, tick, dimension, position,
                "INFO", "worker-inventory-insert");
    }

    public synchronized String deposit(UUID worker, UUID task, String item, int qty, String flow,
                                       long tick, String dimension, long position) {
        String transfer = flow == null ? newFlow() : flow;
        Actor a = actor(worker);
        add(a.deposited, item, qty);
        add(warehouseDeposits, item, qty);
        record(Kind.DEPOSIT, transfer, worker, task, item, qty, tick, dimension, position,
                qty > 0 ? "OK" : "FAILED", "worker->warehouse");
        return transfer;
    }

    public synchronized String withdrawal(UUID worker, UUID task, String item, int qty, String flow,
                                          long tick, String dimension, long position) {
        String transfer = flow == null ? newFlow() : flow;
        Actor a = actor(worker);
        add(a.withdrawn, item, qty);
        add(warehouseWithdrawals, item, qty);
        record(Kind.WITHDRAWAL, transfer, worker, task, item, qty, tick, dimension, position,
                qty > 0 ? "OK" : "FAILED", "warehouse->worker");
        return transfer;
    }

    public synchronized void pickup(UUID worker, UUID task, String item, int qty, String flow,
                                    long tick, String dimension, long position) {
        Actor a = actor(worker);
        add(a.picked, item, qty);
        record(Kind.PICKUP, flow == null ? newFlow() : flow, worker, task, item, qty, tick,
                dimension, position, "OK", "world->worker");
    }

    public synchronized void drop(UUID worker, UUID task, String item, int qty, String flow,
                                  long tick, String dimension, long position) {
        Actor a = actor(worker);
        add(a.dropped, item, qty);
        record(Kind.DROP, flow == null ? newFlow() : flow, worker, task, item, qty, tick,
                dimension, position, "OK", "worker->world");
    }

    /** Warehouse log withdrawn as fuel: a transfer, not yet a sink. */
    public synchronized void consumed(UUID worker, UUID task, String item, int qty, String flow,
                                      long tick, String dimension, long position) {
        Actor a = actor(worker);
        add(a.withdrawn, item, qty);
        add(warehouseWithdrawals, item, qty);
        record(Kind.WITHDRAWAL, flow == null ? newFlow() : flow, worker, task, item, qty, tick,
                dimension, position, qty > 0 ? "OK" : "FAILED", "warehouse->fuel-withdrawal");
    }

    /** A log actually burned from the worker's inventory: global sink, no warehouse leg. */
    public synchronized void burned(UUID worker, UUID task, String item, int qty, String flow,
                                    long tick, String dimension, long position) {
        Actor a = actor(worker);
        add(a.consumed, item, qty);
        record(Kind.CONSUMED, flow == null ? newFlow() : flow, worker, task, item, qty, tick,
                dimension, position, qty > 0 ? "OK" : "FAILED", "worker-inventory->fuel-sink");
    }

    /** System-created stock: new logs inside the boundary. */
    public synchronized void systemCreated(String item, int qty, long tick, String detail) {
        add(systemCreated, item, qty);
        record(Kind.SYSTEM_CREATED, newFlow(), null, null, item, qty, tick, "system", 0L,
                qty > 0 ? "OK" : "FAILED", detail);
    }

    /** Logs entering the warehouse from a source outside the baseline boundary. */
    public synchronized void systemExternalIn(String item, int qty, long tick, String detail) {
        add(systemExternalIn, item, qty);
        record(Kind.SYSTEM_EXTERNAL_IN, newFlow(), null, null, item, qty, tick, "system", 0L,
                qty > 0 ? "OK" : "FAILED", detail);
    }

    /** Logs removed from the warehouse without becoming a tracked world drop. */
    public synchronized void systemRemoved(String item, int qty, long tick, String detail) {
        add(systemRemoved, item, qty);
        record(Kind.SYSTEM_REMOVED, newFlow(), null, null, item, qty, tick, "system", 0L,
                qty > 0 ? "OK" : "FAILED", detail);
    }

    /** Logs leaving warehouse stock as world ItemEntities. */
    public synchronized void systemDrop(String item, int qty, long tick, String detail) {
        add(systemDropped, item, qty);
        record(Kind.SYSTEM_DROP, newFlow(), null, null, item, qty, tick, "system", 0L,
                qty > 0 ? "OK" : "FAILED", detail);
    }

    /** Internal relocation: diagnostic only, neutral in both conservation equations. */
    public synchronized void systemRelocated(String item, int qty, long tick, String detail) {
        record(Kind.SYSTEM_RELOCATED, newFlow(), null, null, item, qty, tick, "system", 0L,
                "INFO", detail);
    }

    public synchronized void unattributed(Kind kind, String item, int qty, long tick,
                                          String dimension, long position, String detail) {
        unattributed = true;
        record(Kind.UNATTRIBUTED, newFlow(), null, null, item, qty, tick, dimension, position,
                "INCOMPLETE", detail);
    }

    public synchronized Report end(Map<UUID, Map<String, Integer>> finalWorkerManifest,
                                   Map<String, Integer> warehouseManifest, long tick) {
        if (ended) {
            return report(tick); // one-shot: a duplicate callback cannot rewrite finals
        }
        ended = true;
        for (Map.Entry<UUID, Map<String, Integer>> e : finalWorkerManifest.entrySet()) {
            Actor a = actors.computeIfAbsent(e.getKey(), k -> new Actor());
            putAll(a.finalInventory, e.getValue());
        }
        putAll(finalWarehouse, warehouseManifest);
        return report(tick);
    }

    public synchronized Report report(long tick) {
        List<ActorReport> reports = new ArrayList<>();
        Map<String, Integer> global = new LinkedHashMap<>();
        putAll(global, initialWarehouse);
        putAll(global, systemCreated);
        putAll(global, systemExternalIn);
        for (Map.Entry<UUID, Actor> e : actors.entrySet()) {
            Actor a = e.getValue();
            addAll(global, a.initial);
            addAll(global, a.harvested);
            addAll(global, a.picked);
            Map<String, Integer> delta = new LinkedHashMap<>();
            addAll(delta, a.initial);
            addAll(delta, a.harvested);
            addAll(delta, a.picked);
            addAll(delta, a.withdrawn);
            subAll(delta, a.finalInventory);
            subAll(delta, a.deposited);
            subAll(delta, a.dropped);
            subAll(delta, a.consumed);
            reports.add(new ActorReport(e.getKey(), copy(a.initial), copy(a.harvested),
                    copy(a.inserted), copy(a.deposited), copy(a.withdrawn), copy(a.picked),
                    copy(a.dropped), copy(a.consumed), copy(a.finalInventory), delta));
        }
        for (Actor a : actors.values()) {
            subAll(global, a.finalInventory);
            subAll(global, a.dropped);
            subAll(global, a.consumed);
        }
        subAll(global, finalWarehouse);
        subAll(global, systemRemoved);
        subAll(global, systemDropped);
        Map<String, Integer> whDelta = new LinkedHashMap<>();
        addAll(whDelta, initialWarehouse);
        addAll(whDelta, warehouseDeposits);
        addAll(whDelta, systemCreated);
        addAll(whDelta, systemExternalIn);
        subAll(whDelta, warehouseWithdrawals);
        subAll(whDelta, systemRemoved);
        subAll(whDelta, systemDropped);
        subAll(whDelta, finalWarehouse);

        boolean complete = started && ended && !truncated && !unattributed
                && zero(global) && zero(whDelta)
                && reports.stream().allMatch(ActorReport::balanced);
        return new Report(runId, fixtureId, complete, started, ended, truncated, unattributed,
                events.size(), global, whDelta, reports);
    }

    public synchronized List<Event> events() {
        return List.copyOf(events);
    }

    public synchronized boolean truncated() {
        return truncated;
    }

    private void record(Kind kind, String transferId, UUID worker, UUID task, String item, int qty,
                        long tick, String dimension, long position, String result, String detail) {
        if (events.size() >= MAX_EVENTS) {
            truncated = true; // explicit, never silent
            return;
        }
        events.add(new Event(++eventSeq, transferId, kind, worker, task, item, qty, tick,
                dimension, position, result, detail));
    }

    private Actor actor(UUID worker) {
        return actors.computeIfAbsent(worker, k -> new Actor());
    }

    private String newFlow() {
        return runId + ":t" + (++transferSeq);
    }

    private static void add(Map<String, Integer> map, String item, int qty) {
        if (qty == 0) return;
        map.merge(item, qty, Integer::sum);
    }

    private static void putAll(Map<String, Integer> target, Map<String, Integer> source) {
        if (source == null) return;
        source.forEach((k, v) -> target.merge(k, v, Integer::sum));
    }

    private static void addAll(Map<String, Integer> target, Map<String, Integer> source) {
        putAll(target, source);
    }

    private static void subAll(Map<String, Integer> target, Map<String, Integer> source) {
        if (source == null) return;
        source.forEach((k, v) -> target.merge(k, -v, Integer::sum));
    }

    private static boolean zero(Map<String, Integer> map) {
        return map.values().stream().allMatch(v -> v == 0);
    }

    private static Map<String, Integer> copy(Map<String, Integer> map) {
        return new LinkedHashMap<>(map);
    }
}
