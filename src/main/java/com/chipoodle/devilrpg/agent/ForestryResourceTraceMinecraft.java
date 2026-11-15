package com.chipoodle.devilrpg.agent;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.world.VillageStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Minecraft-facing, default-off bridge for {@link ForestryResourceTrace}. Counts only; it never
 * mutates an inventory, a stack, a target or a schedule. When the property is absent every hook
 * returns immediately.
 */
public final class ForestryResourceTraceMinecraft {

    private static ForestryResourceTrace ledger;
    private static ForestryResourceTrace.Report lastReport;

    private ForestryResourceTraceMinecraft() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(ForestryResourceTrace.PROPERTY);
    }

    public static void begin(ServerLevel level, BlockPos center, List<Villager> workers,
                             String fixtureId) {
        if (!enabled()) {
            return;
        }
        String runId = System.getProperty("devilrpg.resourcetrace.run",
                System.getProperty("a5.run", "run"));
        ledger = new ForestryResourceTrace(runId, fixtureId);
        ledger.begin(countWorkers(workers), countWarehouse(level, center));
        lastReport = null;
        DevilRpg.LOGGER.info("[RESOURCE-TRACE] event=RUN-BEGIN run={} fixture={} workers={} "
                        + "warehouse={} tick={}",
                runId, fixtureId, workers.size(), countWarehouse(level, center),
                level.getGameTime());
    }

    public static ForestryResourceTrace.Report end(ServerLevel level, BlockPos center,
                                                   List<Villager> workers) {
        if (!enabled() || ledger == null) {
            return null;
        }
        ForestryResourceTrace.Report report = ledger.end(countWorkers(workers),
                countWarehouse(level, center), level.getGameTime());
        lastReport = report;
        logReport(report);
        return report;
    }

    public static ForestryResourceTrace.Report lastReport() {
        return lastReport;
    }

    public static boolean isComplete() {
        return lastReport != null && lastReport.complete();
    }

    public static boolean hasConsumed() {
        return ledger != null && ledger.events().stream()
                .anyMatch(e -> e.kind() == ForestryResourceTrace.Kind.CONSUMED && e.qty() > 0);
    }

    public static boolean hasConsumed(UUID worker) {
        return ledger != null && ledger.events().stream()
                .anyMatch(e -> e.kind() == ForestryResourceTrace.Kind.CONSUMED
                        && e.qty() > 0 && worker.equals(e.worker()));
    }

    public static Map<String, Integer> logCounts(Container container) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (container == null) return counts;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (!s.isEmpty() && s.is(ItemTags.LOGS)) {
                counts.merge(item(s), s.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    /** System creation (e.g. initial remittance) after the trace baseline. */
    public static void systemCreatedDelta(Map<String, Integer> before, Container after,
                                          String detail) {
        if (!enabled() || ledger == null) return;
        applyDelta(before, logCounts(after), Map.of(), detail, true);
    }

    /** External-to-warehouse migration: positive delta is an external source, not creation. */
    public static void systemExternalDelta(Map<String, Integer> before, Container after,
                                           String detail) {
        if (!enabled() || ledger == null) return;
        applyDelta(before, logCounts(after), Map.of(), detail, false);
    }

    /** Maintenance: relocation may drop overflow; negative delta is a removal/sink. */
    public static void systemMaintenanceDelta(Map<String, Integer> before, Container after,
                                              Map<String, Integer> dropped, String detail) {
        if (!enabled() || ledger == null) return;
        applyDelta(before, logCounts(after), dropped, detail, false);
    }

    private static void applyDelta(Map<String, Integer> before, Map<String, Integer> after,
                                   Map<String, Integer> dropped, String detail,
                                   boolean positiveIsCreation) {
        Map<String, Integer> items = new LinkedHashMap<>(before);
        after.forEach((k, v) -> items.putIfAbsent(k, 0));
        dropped.forEach((k, v) -> items.putIfAbsent(k, 0));
        for (String item : items.keySet()) {
            int b = before.getOrDefault(item, 0);
            int a = after.getOrDefault(item, 0);
            int d = dropped.getOrDefault(item, 0);
            int created = Math.max(0, a - b);
            int removed = Math.max(0, b - a - d);
            if (created > 0) {
                if (positiveIsCreation) ledger.systemCreated(item, created, System.nanoTime(),
                        detail);
                else ledger.systemExternalIn(item, created, System.nanoTime(), detail);
            }
            if (d > 0) ledger.systemDrop(item, d, System.nanoTime(), detail);
            if (removed > 0) ledger.systemRemoved(item, removed, System.nanoTime(), detail);
        }
    }

    public static String harvest(Villager worker, UUID task, ServerLevel level, BlockPos tree,
                                 ItemStack drop) {
        if (!enabled() || ledger == null || drop.isEmpty() || !drop.is(ItemTags.LOGS)) {
            return null;
        }
        String flow = ledger.harvest(worker.getUUID(), task, item(drop), drop.getCount(),
                level.getGameTime(), dimension(level), tree.asLong());
        logEvent("HARVEST", worker, task, drop, tree, flow);
        return flow;
    }

    public static void inventoryInsert(Villager worker, UUID task, ServerLevel level,
                                       ItemStack before, ItemStack remainder, String flow) {
        if (!enabled() || ledger == null || before.isEmpty() || !before.is(ItemTags.LOGS)) {
            return;
        }
        int moved = before.getCount() - remainder.getCount();
        ledger.inventoryInsert(worker.getUUID(), task, item(before), moved, flow,
                level.getGameTime(), dimension(level), worker.blockPosition().asLong());
    }

    public static String deposit(Villager worker, UUID task, ServerLevel level, BlockPos center,
                                 ItemStack before, ItemStack remainder, String flow) {
        if (!enabled() || ledger == null || before.isEmpty() || !before.is(ItemTags.LOGS)) {
            return null;
        }
        int moved = before.getCount() - remainder.getCount();
        String transfer = ledger.deposit(worker.getUUID(), task, item(before), moved, flow,
                level.getGameTime(), dimension(level), center.asLong());
        logEvent(moved > 0 ? "DEPOSIT" : "DEPOSIT-FAILED", worker, task, before, center, transfer);
        return transfer;
    }

    public static String withdrawal(Villager worker, UUID task, ServerLevel level, BlockPos center,
                                    ItemStack stack, int qty, String flow) {
        if (!enabled() || ledger == null || stack.isEmpty() || !stack.is(ItemTags.LOGS)) {
            return null;
        }
        return ledger.withdrawal(worker.getUUID(), task, item(stack), qty, flow,
                level.getGameTime(), dimension(level), center.asLong());
    }

    public static void pickup(Villager worker, ServerLevel level, ItemStack stack, int qty) {
        if (!enabled() || ledger == null || stack.isEmpty() || !stack.is(ItemTags.LOGS)) {
            return;
        }
        ledger.pickup(worker.getUUID(), null, item(stack), qty, null, level.getGameTime(),
                dimension(level), worker.blockPosition().asLong());
        logEvent("PICKUP", worker, null, stack, worker.blockPosition(), null);
    }

    public static void drop(Villager worker, UUID task, ServerLevel level, ItemStack stack,
                            String flow) {
        if (!enabled() || ledger == null || stack.isEmpty() || !stack.is(ItemTags.LOGS)) {
            return;
        }
        ledger.drop(worker.getUUID(), task, item(stack), stack.getCount(), flow,
                level.getGameTime(), dimension(level), worker.blockPosition().asLong());
        logEvent("DROP", worker, task, stack, worker.blockPosition(), flow);
    }

    /** A warehouse log burned as fuel: warehouse withdrawal plus global sink. */
    public static void consumed(Villager worker, ServerLevel level, BlockPos center,
                                ItemStack stack, int qty) {
        if (!enabled() || ledger == null || stack.isEmpty() || !stack.is(ItemTags.LOGS)) {
            return;
        }
        ledger.consumed(worker.getUUID(), null, item(stack), qty, null, level.getGameTime(),
                dimension(level), center.asLong());
        logEvent("WITHDRAWAL-FUEL", worker, null, stack, center, null);
    }

    /** One log actually burned from the cook's inventory. */
    public static void burned(Villager worker, ItemStack stack, int qty) {
        if (!enabled() || ledger == null || stack.isEmpty()) {
            return;
        }
        ledger.burned(worker.getUUID(), null, item(stack), qty, null,
                worker.level().getGameTime(), dimension((ServerLevel) worker.level()),
                worker.blockPosition().asLong());
        DevilRpg.LOGGER.info("[RESOURCE-TRACE] event=BURNED worker={} item={} qty={} tick={}",
                worker.getUUID(), item(stack), qty, worker.level().getGameTime());
    }

    private static Map<UUID, Map<String, Integer>> countWorkers(List<Villager> workers) {
        Map<UUID, Map<String, Integer>> out = new LinkedHashMap<>();
        for (Villager v : workers) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
                ItemStack s = v.getInventory().getItem(i);
                if (!s.isEmpty() && s.is(ItemTags.LOGS)) {
                    counts.merge(item(s), s.getCount(), Integer::sum);
                }
            }
            out.put(v.getUUID(), counts);
        }
        return out;
    }

    private static Map<String, Integer> countWarehouse(ServerLevel level, BlockPos center) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Container c = VillageStorage.almacen(level, center);
        if (c == null) return counts;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty() && s.is(ItemTags.LOGS)) {
                counts.merge(item(s), s.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    private static String item(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static String dimension(ServerLevel level) {
        return level.dimension().location().toString();
    }

    private static void logEvent(String type, Villager worker, UUID task, ItemStack stack,
                                 BlockPos pos, String flow) {
        DevilRpg.LOGGER.info("[RESOURCE-TRACE] event={} worker={} task={} item={} qty={} pos={} "
                        + "flow={} tick={}",
                type, worker.getUUID(), task, item(stack), stack.getCount(),
                pos == null ? "-" : pos.toShortString(), flow, worker.level().getGameTime());
    }

    private static void logReport(ForestryResourceTrace.Report report) {
        DevilRpg.LOGGER.info("[RESOURCE-TRACE] SUMMARY run={} fixture={} complete={} started={} "
                        + "ended={} truncated={} unattributed={} events={}",
                report.runId(), report.fixtureId(), report.complete(), report.started(),
                report.ended(), report.truncated(), report.unattributed(), report.eventCount());
        for (ForestryResourceTrace.ActorReport actor : report.actors()) {
            DevilRpg.LOGGER.info("[RESOURCE-TRACE] ACTOR worker={} initial={} harvested={} "
                            + "inserted={} deposited={} withdrawn={} picked={} dropped={} final={} delta={}",
                    actor.worker(), actor.initial(), actor.harvested(), actor.inserted(),
                    actor.deposited(), actor.withdrawn(), actor.picked(), actor.dropped(),
                    actor.finalInventory(), actor.delta());
        }
        DevilRpg.LOGGER.info("[RESOURCE-TRACE] WAREHOUSE delta={}", report.warehouseDelta());
        DevilRpg.LOGGER.info("[RESOURCE-TRACE] GLOBAL delta={}", report.globalDelta());
    }
}
