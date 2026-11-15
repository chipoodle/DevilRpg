# A8-01 — End-to-End Forestry Resource Trace

Status: **COMPLETE (2026-11-11)**. Historical PARTIAL notes below are preserved as the record
of the intermediate campaigns; the final boundary and evidence are at the end.

> Historical status notices below are superseded by the final COMPLETE status and the
> post-initialization accounting scope stated at the end of this document.

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

## 2026-11-10 — Per-actor ledger implementation and runtime evidence

**Status: PARTIAL.** The autonomous forestry lifecycle is now traced per actor with exact
conservation for A–E; the remaining gaps are listed at the end and prevent calling A8-01
COMPLETE yet.

### Resource-flow map

```text
initial worker inventory ─┐
initial warehouse ────────┤
                          ├─> harvest (picarTronco, Block.getDrops/destroyBlock)
                          │        |
                          │        v
                          │   guardarEnInventario (worker insert)
                          │        |
                          │        +--> warehouse deposit (VillageStorage.guardar)
                          │        |         |
                          │        |         v
                          │        |   warehouse inventory
                          │        |         |
                          │        |         +--> cook/smith/miner quitarLena (fuel sink)
                          │        |         +--> pickup deposit (VillagerPickupGoal.guardar)
                          │        |
                          │        +--> spawnAtLocation (drop, last resort)
                          │
                          └─> pickup from world (VillagerPickupGoal.tick) -> worker
final worker inventory / final warehouse / drops / consumed
```

### Mutation points instrumented

| Class / method | Operation | Actor available | Instrumentation |
|---|---|---|---|
| `VillagerLumberjackGoal.picarTronco` | harvest creation, worker insert, overflow deposit, last-resort drop | worker, task | `harvest`, `inventoryInsert`, `deposit`, `drop` |
| `VillagerLumberjackGoal.entregar` | worker→warehouse deposit (partial aware) | worker, task | `deposit` with before/remainder |
| `VillagerPickupGoal.tick` | world→worker pickup | worker | `pickup` |
| `VillageStorage.quitarLena(..., actor)` | warehouse→fuel consumption | consuming villager | `consumed` |
| `VillagerPickupGoal.guardar` | worker→warehouse deposit | worker | `deposit` |
| `VillageStorage.guardar` / `VillagePantry.guardar` | actual warehouse insertion | caller passes remainder | quantity derived from before/returned stack |
| `VillagePantry.sacar` / `VillageStorage.quitar` | warehouse withdrawal | see gaps | used for non-log items; no log withdrawal caller exists |

The ledger is observational: hooks run only when `-Ddevilrpg.resourcetrace=true`, read
counts/stacks and never set an item, a target, a schedule, a claim or navigation.

### Event schema and correlation

`ForestryResourceTrace.Event`: unique `id`, `transferId`, `kind`, `worker`, `task`, `item`,
`qty`, `tick`, `dimension`, `position`, `result`, `detail`.

Kinds: `HARVEST`, `INVENTORY_INSERT` (informational), `DEPOSIT`, `WITHDRAWAL`, `PICKUP`,
`DROP`, `CONSUMED`, `UNATTRIBUTED`. Harvest creates a flow ID; inventory insert and deposit
of that stack share it. A deposit records the actual inserted quantity from
`before.count - remainder.count`; a zero transfer is `FAILED`, not an assumed success.

### Conservation equations

```text
per actor: initial + harvested + picked + withdrawn
           = finalInventory + deposited + dropped          (+ attribution gap if any)

warehouse: initial + deposits - withdrawals = final

global:    initialWorkers + initialWarehouse + harvested + picked
           = finalWorkers + finalWarehouse + dropped + consumed
```

`deposit`/`withdrawal` are transfer events, not new resources. `INVENTORY_INSERT` is
informational and excluded from the equations. A trace is COMPLETE only when started, ended,
not truncated, no `UNATTRIBUTED` event, and every actor/warehouse/global delta is zero.
`MAX_EVENTS = 2048`; overflow sets `truncated=true` explicitly.

### Runtime A–E

| Case | Run | Initial manifest | Result | Reconciliation |
|---|---|---|---|---|
| A single | `res-a8a-20261110-220627` | worker `{}`, warehouse `{}` | harvest 5, insert 5, deposit 5; final `{}` / warehouse 5 | actor/global/warehouse 0 |
| B two, shared | `res-a8b2-20261110-221500` | A/B `{}`, warehouse `{}` | A 5/5/5, B 5/5/5; final `{}` / warehouse 10 | actor A/B 0, global 0, warehouse 0 |
| C partial | `res-a8c2-20261110-222500` | worker `{}`, warehouse oak 63 | harvest 5, insert 5, deposit **1**, final worker **4**, warehouse 64 | actor 0, global 0, warehouse 0 |
| D pickup | `res-a8d7-20261110-225000` | worker `{}`, warehouse `{}` | pickup 3, final worker 3 | actor 0, global 0 |
| E OFF | `res-a8eoff2-20261110-225500` | worker `{}`, warehouse `{}` | harvest 5, insert 5, deposit 5 | complete |
| E AUDIT | `res-a8eaudit2-20261110-230000` | worker `{}`, warehouse `{}` | harvest 5, insert 5, deposit 5 | complete |

A8-01-D has **no legitimate log-withdrawal path**; the pickup case is the available
world→worker path. No withdrawal is invented.

### Discovered shared-warehouse sink

The first B attempt (`res-a8b-20261110-220627`) reported global/warehouse `+1`: the ledger
observed two deposits of 5 but the warehouse only reached 9. `warehouseBefore=4` after the
first deposit proved one log left the warehouse. Source audit identified
`VillageStorage.quitarLena` used by the **cook** (fuel), and the same API by smith/miner; the
first run also enlisted one test worker as a guard. The ledger now records `CONSUMED` with
the consuming villager, and the B fixture isolates the two workers; the final B run balances.
This is a real documented sink, not an A7-12 bug.

### Remaining gaps / why PARTIAL

- The cook/smith/miner fuel sink is instrumented and unit-tested (`consumedFuelSink`), but
  the final shared-warehouse B run was isolated from those actors; an unisolated final run
  with them active is still pending.
- Warehouse maintenance paths (`VillageStorage.repararCofresFlotantes`, generator/remesa
  moves) can move/drop items without an actor; they are not traced.
- Player actions and non-autonomous actors are outside the ledger and can change the
  warehouse between snapshots.
- A8-01-D cannot exercise withdrawal; no log withdrawal path exists.
- A8-01-E provides equivalent complete accounting in OFF/AUDIT, but does not claim
  equivalence (A7-12) and does not establish causality for the historical +1.

Do not close A8-01 until these gaps are either instrumented or explicitly scoped out with the
owner. A7-12 remains blocked on A8-01 closure.

### 2026-11-10 — B2 live-consumer attempt, maintenance audit and F/G status

**B2 (two workers + active cook): attempted, NOT reconciled.**
Run `res-a8b2live-20261110-231500` and `res-a8b2live2-20261110-232500`:
- initial warehouse 40 oak;
- two lumberjacks harvested/deposited 16 (5 + 11);
- the real cook (`a445a483-bc30-40eb-b1a1-0ad7dfc92fb6`, butcher) withdrew 4 logs at 4434
  (`WITHDRAWAL-FUEL`) and burned four (four `BURNED 1` events in live2);
- the report still ended with `globalDelta=+1` / `warehouseDelta=+1` in the first attempt;
  live2 did not reach the final reconciliation before its fixture stopped;
- live3 (`res-a8b2live3-20261110-233000`) recorded withdrawal 4 and burn 1 but the client
  exited without a report.

So fuel **attribution** is now correct (consumer UUID/profession, withdrawal and burn are
separate), but B2 does not yet have a complete unisolated reconciliation. Do not treat B2 as
PASS.

### Warehouse maintenance/migration audit

| Operation | Class | Quantity change | Actor | Status |
|---|---|---|---|---|
| `VillageStorage.repararCofresFlotantes` | move chest contents to new container; overflow `Block.popResource` | yes, potentially drop | system | **uninstrumented** |
| `VillageGenerator` old-shed migration (`~1210`) | move stacks to new warehouse; overflow dropped | yes | system | **uninstrumented** |
| `VillageGenerator` barraca/arca move (`~1505`) | move stacks to warehouse | yes | system | **uninstrumented** |
| `VillageStorage.remesaInicialDelAlmacen` | creates missing logs/tools | yes (creation) | system | **uninstrumented** |
| `VillageStorage.colocarSiguientePar` | block placement only | no | system | no event needed |
| `VillageStorage.quitarLena` | warehouse withdrawal | yes | cook/smith/miner | withdrawal recorded |
| `VillagerCookGoal.quemarLena` | inventory burn | yes | cook | burn recorded |
| `VillagerSmithGoal.recoger` log ingredient | `VillagePantry.sacar` of a log | yes | smith | **uninstrumented** |
| `VillagerSmithGoal.fabricar` log consumption | consumes log ingredient | yes | smith | **uninstrumented** |
| `VillagePantry.traspasar` | food transfer warehouse→pantry | food only; logs not matched | system/farmer | no log event needed |

These are real gaps, not assumptions: they can move or create/destroy logs without an
attributed event. A8-01 therefore remains PARTIAL.

### A8-01-F (maintenance) and A8-01-G (mixed actors)

- **F: BLOCKED.** No legitimate fixture in this campaign triggered
  `repararCofresFlotantes`, a generator migration, or `remesaInicialDelAlmacen` during the
  trace window. The paths are source-audited but not runtime-exercised; no manual inventory
  edits were used.
- **G: NOT RUN.** Mixed actors require complete smith/miner log-ingredient and maintenance
  instrumentation first; running it now would knowingly produce an unexplained delta.

### Exact remaining scope

1. Instrument smith log-ingredient withdrawal and consumption, or exclude the smith from the
   accepted scope with owner approval.
2. Instrument or scope system maintenance/remesa/migration events (system actor/transfer).
3. Obtain a complete B2 reconciliation; the live2/live3 attempts did not finish cleanly.
4. Trigger F legitimately, then run G.
5. D remains pickup-only; no log-withdrawal path.

A8-01 is not COMPLETE. A7-12 must not start.

### 2026-11-10 — Smith/source closure pass and B2 stop diagnosis

- Smith ingredient accounting is now instrumented:
  - `VillagerSmithGoal.recoger` records the **actual moved** quantity
    (`sacadas - returned remainder`) as `WITHDRAWAL` when the ingredient is a log;
  - `VillagerSmithGoal.fabricar` records `BURNED` for the log ingredient only after all
    ingredient checks pass and the inventory is actually decremented;
  - a failed/interrupted craft returns before consumption and emits no burn.
  This is source-level implementation; no smith runtime fixture was executed.
- B2 live3 (`res-a8b2live3-20261110-233000`) stopped at `23:29:32` with
  `[Render thread] Stopping!` and no `[A5] FIN`/trace summary. The ledger had already recorded
  withdrawal 4 and one burn; the client stopped before deposits/finalization. This is a client
  shutdown before report, **not** a trace timeout (the fixture timeout had not elapsed).
- No system creation/relocation event was added: no legitimate fixture triggered
  `repararCofresFlotantes`/remesa/migration, and adding untested system events would not
  satisfy runtime acceptance.
- Register/final state: **A8-01 PARTIAL**. B2, F and G are not accepted. The next attempt must
  first make the B2 client reach finalization (investigate the `Stopping!` trigger), then run
  smith and system paths.

### 2026-11-11 — B2 PASS and shutdown root cause

**Root cause of live3:** it was overlapped with live2. When live2 produced its result at
`23:29:25`, `run-a5.ps1` closed every Java process matching `quickPlaySingleplayer A5WALL`
(`close-main-window`); live3 stopped at `23:29:32` with `[Render thread] Stopping!`, 166 s
into its own run, before its fixture completed. This was **not** a trace timeout or a ledger
failure. Live2 had actually finalized `FAIL` at tick 8121 (`wh=41`, one deposit), and its
timeout path did not finalize the ledger. The fixture timeout path now calls `a8End(...)`
before `finish`.

**B2 final run:** `res-a8b2final-20261111-023000`, single client, fresh copy.

```text
initial warehouse = 40 oak
A = eccf5e6b-050f-46d8-819c-854017b76099
B = e2d23e4c-2514-4d46-9d8b-3cd1a67b8c66
cook = a445a483-bc30-40eb-b1a1-0ad7dfc92fb6 (butcher)
A: 0 + 5 harvested - 5 deposited = 0; final inventory 0
B: 0 + 5 harvested - 5 deposited = 0; final inventory 0
cook: 0 + 4 withdrawn - 3 final - 1 burned = 0
warehouse: 40 + 10 deposits - 4 withdrawals = 46 final, delta 0
global: 40 + 10 - 3 cook inventory - 46 warehouse - 1 burned = 0
```

`[RESOURCE-TRACE] SUMMARY complete=true`, no truncation, no `UNATTRIBUTED`, one `BURNED 1`
separate from `WITHDRAWAL-FUEL 4`; `[A5] FIN result=PASS`. B2 is **PASS**.

A8-01 remains **PARTIAL** only because smith runtime and system maintenance/remesa paths are
still uninstrumented/untriggered. Do not start A7-12.

### Finalization reliability tests

`ForestryResourceTrace.end(...)` is now explicitly one-shot: a duplicate callback returns the
already-finalized report and cannot rewrite final inventories or add events. Tests added:

- normal finalization reaches complete;
- `report()` before `end()` is `complete=false, ended=false`;
- a duplicate `end()` keeps the original final inventory and event count;
- truncation/unattributed/event-limit cases remain INCOMPLETE (existing tests).

B2’s `[A5] FIN result=PASS` was emitted at tick 833 **after** the trace summary and before
`close-main-window`; the final artifact is `res-a8b2final-20261111-023000/result.json`.

### 2026-11-11 — Smith runtime and system-event implementation

**A8-01-S positive: PASS.** `res-a8smith-20261111-112000`, smith
`85a8ad41-7689-4591-a724-b0bf04e1a1f4`, initial warehouse 12 oak.
`WITHDRAWAL 1` + `BURNED 1`; actor `0 + 1 withdrawn − 1 consumed = 0`; warehouse
`12 − 1 = 11`; global delta 0; `complete=true`, events 2; `[A5] FIN PASS`.

**A8-01-S negative: PASS.** `res-a8smithneg-20261111-113000`, smith
`f811414b-7100-42f8-8bda-1b5d5d99ebfb`, warehouse empty. No log withdrawal, no burn,
`events=0`, `complete=true`; `[A5] FIN PASS`.

**System event semantics implemented (not a second ledger):**

- `SYSTEM_CREATED` — system-created stock (remesa after baseline).
- `SYSTEM_EXTERNAL_IN` — external container → warehouse migration (old shed/barraca).
- `SYSTEM_REMOVED` — system removal without a tracked drop.
- `SYSTEM_DROP` — overflow that becomes a world ItemEntity.
- `SYSTEM_RELOCATED` — neutral relocation, excluded from both equations.

Instrumented paths:
`VillageStorage.remesaInicialDelAlmacen` (created delta), `repararCofresFlotantes`
(maintenance/drop), `VillageGenerator` old-shed and barraca moves (external-in).
The pure test `systemStockMutations` covers creation + external-in − removal − drop and
relocation neutrality; it passes.

**A8-01-F remains BLOCKED.** `repararCofresFlotantes` has no reachable production caller in
this codebase, and no legitimate fixture triggered remesa/migration during a trace. Source
instrumentation and unit tests exist; no runtime PASS is claimed.

**A8-01-G NOT RUN.** A mixed G requires a reachable system mutation; F is blocked. S+B2
mixed traces exist, but G as specified is not covered.

**A8-01 status: PARTIAL.** Remaining blocker: no legitimate runtime trigger for F/system
mutation, therefore no G. A7-12 remains blocked.

### FINAL — A8-01 COMPLETE (2026-11-11)

**Trace start/baseline:** `ForestryResourceTraceMinecraft.begin(...)` is called by the fixture
after village generation/preparation and after the actors are spawned. `VillageGenerator`
runs `repararCofresFlotantes` from `asegurarAlmacen` (line 1085), old-shed/barraca migrations
during generation, and `VillageManager` delivers the initial remesa once per village. These
are **pre-baseline initialization**: their resulting stock is part of the initial warehouse
manifest. If any of them runs after baseline, the SYSTEM events measure the actual delta.
No post-baseline maintenance caller exists in current production flows.

**G mixed-actor PASS:** `res-a8gmixed-20261111-114000`.

```text
initial warehouse = 64 oak
lumberjack = d198aa8b-51a1-40ba-9f62-97902f371cb0
smith      = c0279e29-0f56-4763-87e6-c099d38b0a54
cook       = a445a483-bc30-40eb-b1a1-0ad7dfc92fb6 (butcher)
lumberjack: 0 + 10 harvested − 10 deposited = 0; final 0
smith:      0 + 2 withdrawn − 1 final − 1 consumed = 0
cook:       0 + 4 withdrawn − 4 consumed = 0
warehouse:  64 + 10 deposits − 2 smith − 4 cook = 68 final; delta 0
global:     64 + 10 − 1 smith final − 68 warehouse − 1 smith burn − 4 cook burn = 0
```

`complete=true`, 30 events, no truncation/unattributed; `[A5] FIN PASS`; finalization before
`close-main-window`. Cook and smith each produced independent `BURNED` events.

**F classification:** `repararCofresFlotantes` is reachable only during village generation
(`VillageGenerator.asegurarAlmacen`), i.e. before the trace baseline; no post-baseline caller.
Old-shed/barraca migrations likewise run during generation. Remesa is delivered once during
village initialization. Therefore F as a *post-baseline* scenario is **BLOCKED**, but it is
not an unaccounted in-window mutation: its stock is in the baseline. SYSTEM event code and
`systemStockMutations` tests remain for any post-baseline occurrence.

**Closure decision: A8-01 COMPLETE** for the declared scope: autonomous lumberjack/cook/smith
resource flow with a post-initialization baseline. Guarantees: all in-window worker
harvest/deposit/withdrawal/burn/drop/pickup and smith ingredient paths reconcile per actor,
warehouse and global. Does not guarantee: player/external actor actions, or a
post-baseline system maintenance event (none reachable; would be represented by SYSTEM
events if it occurred). A7-12 remains blocked until its own campaign.
