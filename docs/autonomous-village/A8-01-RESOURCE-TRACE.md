# A8-01 — End-to-End Forestry Resource Trace

Status: **PARTIAL**. The one-log A7-12 discrepancy is explained in direction by a shared
warehouse and a second lumberjack, but item-level per-actor accounting is not complete.

## 1. Baseline

```text
branch feature/autonomous-village
HEAD 97220be5453b57387002b9bc91935807cc665274
staged 0
unstaged 26
untracked 155
```

No production behavior changed in A8-01.

## 2. Existing code paths

### Harvest

`VillagerLumberjackGoal.picarTronco` (`~878-900`):

1. `Block.getDrops(tronco, level, p, null)`.
2. `level.destroyBlock(p, false)`.
3. `guardarEnInventario(drop)` inserts into the lumberjack inventory.
4. If inventory remainder exists, `VillageStorage.guardar(level, center, resto)`.
5. Only if storage also rejects the remainder does `villager.spawnAtLocation(sobra)` create
   an `ItemEntity`.

Therefore harvested logs normally go **directly to the lumberjack inventory or directly
to the warehouse**, not to the ground.

### Warehouse

`VillageStorage.guardar` (`~498`) delegates to the shared warehouse container via
`VillagePantry.guardar`. Every lumberjack in the village uses the same `VillageStorage.almacen`,
so warehouse deltas are aggregate, not per-worker.

### Pickup

`VillagerPickupGoal` can target `ItemEntity` (`~410`), but no `ItemEntity` for the tracked
logs was observed in Runs 209–212. The only log `spawnAtLocation` path is the
inventory-and-storage-full fallback, which was not hit.

## 3. Instrumentation

No new instrumentation was added. Existing `[T2-HARVEST]`, `[TASK-BOUNDARY]`, and
`[LUMBER-TREE]` logs provide actor, target and warehouse totals.

## 4. Chronological evidence

Runs 209/210 and 211/212 used copies with:

```text
storage baseline = 128 logs
fixture tree at 87,97,4
T2 worker at 82,98,2
```

Observed:

```text
T2 TALAR target=53,97,-17       (village tree, not the fixture tree)
b89 ASSIGN target=87,97,4       (fixture tree, village lumberjack)
T2 POST-HARVEST target=127,97,20
T2 END warehouse delta=+3 / +2  (pair 1)
T2 END warehouse delta=+4 / +3  (pair 2)
```

The fixture tree at 87,4 was assigned to **b89fcee1...**, the existing village lumberjack.
T2 harvested a different village tree at 53,-17. Both deposit into the same warehouse.

## 5. Which villager moved the logs

- T2 harvested and carried its tree's logs.
- b89 harvested the fixture tree at 87,4.
- Both used the shared warehouse.
- No third-party `ItemEntity` pickup was observed.
- Warehouse withdrawals/churn by other actors are possible and are not itemized by the
  existing logs.

## 6. Reconciliation

Pair 1:

```text
T2 carried at POST = 5 (OFF) / 4 (AUDIT)
T2 warehouse delta = +3 / +2
```

T2 was later at carried 0, so its own deposit was 5/4, while the shared warehouse gained
only 3/2. The difference was not created by T2's missing log; it reflects shared
warehouse activity (b89 deposit/withdraw and/or other village consumption).

Pair 2:

```text
T2 carried at POST = 6 / 5
T2 warehouse delta = +4 / +3
```

Same pattern: T2 contributed more than the aggregate delta.

## 7. Explanation of the A7-12 one-log discrepancy

**Established direction:** the measured warehouse delta is not a per-worker total. A second
lumberjack (b89) was active on the same fixture tree and shared warehouse. The OFF/AUDIT
difference is therefore at least confounded by a different actor's activity and by warehouse
withdrawals.

**Not established:** the exact individual transfer that accounts for the final one-log
difference. No item-level pickup/deposit ledger existed, and no `ItemEntity` was observed.
This is a shared-accounting explanation, not a proven AUDIT defect.

## 8. Classification

**PARTIAL.**

- The trace works and identifies the competing villager and shared warehouse.
- A specific per-actor item movement remains unresolved.
- No concrete production defect is demonstrated.
- No ItemEntity pickup of harvested logs was observed.

## 9. Remaining uncertainty

To fully reconcile, the next trace would need a per-actor ledger of b89 and T2 inventory
insert/extract and warehouse deposit/withdraw, keyed by item and tick. A8-01 intentionally
does not add that production instrumentation or start another campaign.

No execution authority transfer is justified. The A7-12 non-equivalence remains
**not attributable to AUDIT** and must not be treated as a PASS or a proven defect.
