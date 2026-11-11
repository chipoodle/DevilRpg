# Autonomous Village — Master Plan and Stabilization Roadmap

## A. Project objective

The repository already contains an autonomous village: generated settlements,
profession goals, shared storage/pantry, farm-plot coordination, forestry,
guards/raids, navigation policies, and saved village data. The objective is a
coherent society that keeps working over extended gameplay without player
intervention.

This document is an audit and roadmap, not a redesign. It distinguishes:

- **Runtime verified** — production workflow observed end to end.
- **Automated-test verified** — focused policy/ownership tests pass.
- **Implemented, integration unverified** — source exists, no end-to-end proof.
- **Partial** — only part of the expected workflow exists.
- **Not found** — no production implementation was located.

Forestry Block 1 is closed: see
[FORESTRY-BLOCK-1-ACCEPTANCE.md](FORESTRY-BLOCK-1-ACCEPTANCE.md).

## B. Existing system inventory

### B.1 Village core and generation

| Area | Classes | Status |
|---|---|---|
| Village identity/state | `VillageManager`, `VillageSavedData` | Implemented; partial persistence evidence |
| Generation/boundaries | `VillageGenerator`, `GenerationBudget`, `VillageTerrainPolicy` | Implemented; terrain unit tests |
| Forest layout | `VillageForestLayout` | Implemented; layout test |
| Farm layout/ownership | `VillageGenerator`, `FarmPlotOwners`, `VillageFarmCoordinator` | Implemented; ownership test, no runtime |
| Storage | `VillageStorage`, `VillagePantry`, `WarehouseApproachPolicy` | Implemented; forestry delivery verified |
| Errands | `VillageErrands` | Implemented; not integration verified |

### B.2 Professions

| Profession | Main class | Status |
|---|---|---|
| Lumberjack | `VillagerLumberjackGoal` | **Runtime verified A1–A7** |
| Farmer | `VillagerFarmGoal` | Implemented; ownership test only |
| Cook | `VillagerCookGoal` | Implemented; not integration verified |
| Miner | `VillagerMinerGoal` | Implemented; A5 miner scenarios exist, not a formal acceptance |
| Smith | `VillagerSmithGoal` | Implemented; not integration verified |
| Cleric | `VillagerClericGoal` | Implemented; not integration verified |
| Fisher | `VillagerFisherGoal` | Implemented; not integration verified |
| Animal farm | `VillagerAnimalFarmGoal`, `VuelveAlCorralGoal` | Implemented; not integration verified |
| Repair | `VillagerRepairGoal` | Implemented; not integration verified |
| Tavern | `VillagerTavernGoal` | Implemented; not integration verified |
| Collect/haul | `VillagerCollectGoal`, `VillagerPickupGoal`, `VillageDispatcherGoal` | Implemented; not integration verified |
| Guard | `VillagerGuardGoal` | Implemented; raid integration not accepted |

### B.3 Scheduling and navigation

| Area | Classes | Status |
|---|---|---|
| Per-goal scheduling | `GoalSelector`, profession goals at priority 0 | Implemented; A6 preemption verified |
| Village navigation policy | `VillageNavigationGoal`, `VillageTaskNavigationPolicy` | Implemented; policy test |
| Custom raid navigation | `entity/nav/*`, `AggressiveZombieEntity` | Partial; A2 remains open |
| Stuck/terminal failure | `VillagerLumberjackGoal` physical-progress policy | **Runtime verified (A1)** |
| Cross-worker cleanup | per-goal `ForestryTaskController` | **Runtime verified (A6)** |

### B.4 Defense and persistence

| Area | Classes | Status |
|---|---|---|
| Siege/raid state | `RaidTacticalBoard`, `SiegeWaveState`, `WorldSiegePolicy`, `HordeManager` | Implemented; unit tests, partial fixtures |
| Lairs | `LairManager`, `LairSavedData`, `LairGenerator` | Implemented; not village-integrated |
| Village save data | `VillageSavedData` | Implemented; reload scenarios exist, broad identity/assignment reload unverified |

### B.5 Explicitly not found

- **New-construction builder.** `VillagerRepairGoal` repairs; no general
  resource-consuming construction goal was located.
- **Global reservation registry.** None exists. Farm plots have a coordinator;
  forestry/resources use per-goal ownership only.

These are scope facts, not proof that every related behavior is broken.

## C. Architecture and dependencies

```text
VillageGenerator / VillageSavedData
  -> village center, plots, forest layout, boundaries
  -> VillageManager (membership, rest, threats, failed points)
  -> profession GoalSelectors
       -> Farm / Lumber / Cook / Miner / Smith / Guard / ...
       -> VillageNavigationGoal / shared navigation policies
       -> VillageStorage / VillagePantry (shared containers)
       -> profession-specific controllers/owners
```

Key dependency chains:

- **Food:** farm plot -> harvest -> storage/pantry -> cook ->
  `VillageManager.repartirRaciones` -> `pasarHambre` -> shortage/recovery.
- **Materials:** forestry/mining -> storage -> crafting/repair/construction.
- **Defense:** threat -> guard goal -> equipment/consumables -> post-raid return
  to work.
- **Ownership:** farm coordinator guards plots; forestry controller guards only
  one goal's assignment; there is no cross-worker claim for trees.
- **Persistence:** `VillageSavedData` restores village-level state; goal-local
  targets/controllers are transient and are expected to be reacquired.

## D. Shared invariants

1. **One owner per assignment.** A goal may release only its own controller.
2. **Idempotent cleanup.** Repeated `complete()`/`stop()` cannot corrupt state.
3. **Single village center.** Spatial systems use the authoritative center, not
   a transient villager position.
4. **Bounded scanning.** Planner batches stay at 256 columns; no radius/budget
   expansion to hide origin defects.
5. **Physical progress.** Navigation progress means movement, not path object
   recreation.
6. **Fail before cleanup.** Terminal failure records the failed point before the
   target/assignment is cleared.
7. **Exact storage accounting.** Delivery uses accepted counts and preserves
   carried/warehouse conservation.
8. **No duplicate resource claims.** Multiple workers must not reserve the same
   unique job/resource unless the architecture explicitly allows sharing.
9. **Persistence identity.** Saved village state must not merge villages or
   reassign identities across reload.
10. **Bounded retries.** No worker may retry an unchanged obstruction forever.

## E. Acceptance matrix

| Subsystem | Implementation | Verification status | Evidence |
|---|---|---|---|
| Forestry A1–A7 | Complete | **Runtime verified** | Runs 62, 65, 27, 57/58, 81 |
| Forestry controller | Complete | Automated + runtime | `FORESTRY_TASK_CONTROLLER_TEST`, Run 81 |
| Forestry planner | Complete | Automated + runtime | `ForestryWorkPlannerTest`, Runs 57/58 |
| Farm plot ownership | Complete | Automated only | `VillageFarmOwnershipTest` |
| Farm workflow | Complete | **Unverified integration** | no end-to-end farm run |
| Food consumption/shortage | `VillageManager.repartirRaciones`, `pasarHambre` | Runtime verified, but 24 meals/day and item overdraw fail intended daily design | `FOOD-ECONOMY-AUDIT.md`; Run 84 |
| Cook/tavern | Implemented | Unverified integration | source only |
| Miner/smith/cleric/fisher | Implemented | Unverified integration | source only |
| Animal husbandry | Implemented | Unverified integration | source only |
| Repair | Implemented | Unverified integration | source only |
| New construction | **Not found** | Not implemented/verified | source search |
| Storage/pantry | Implemented | Forestry delivery verified | Run 57/58/81 |
| Guard/raid | Implemented | Partial; A2 open | `ESTADO-ACTUAL.md`, unit tests |
| Village navigation | Implemented | Policy test; A2 open | `VillageTaskNavigationPolicyTest` |
| Goal scheduling | Implemented | **Runtime verified A6** | Run 81 |
| Agent-owned forestry navigation (A8-F03) | Complete | **Runtime verified post-fix** | `A8-F03-FRESH-SESSION-EXECUTION.md` §14 |
| Persistence | Implemented | Reload scenarios partial | `xp-reload`, `multi-village-reload` scenarios |
| Village generation/terrain | Implemented | Automated | `VillageTerrainPolicyTest`, layout test |

## F. Cross-system acceptance scenarios

Keep the suite small. Each scenario must prove production outcomes, not startup.

### C1 — Food autonomy

- **Covers:** farm -> storage/pantry -> cook -> consumption -> shortage.
- **Preconditions:** one farmer, one cook, valid plots, known pantry stock.
- **Execution:** harvest a known crop; verify storage delta; let consumption run;
  then remove food and verify the shortage path.
- **PASS evidence:** crop count increases, pantry count decreases by consumption,
  villager behavior changes under shortage without duplicate withdrawals.
- **FAIL:** food teleports, counts diverge, no consumption occurs, or shortage
  causes a loop.
- **Replaces/complements:** farm ownership unit test; no new food system.

### C2 — Resource-to-repair/construction lifecycle

- **Covers:** forestry/mining -> storage -> repair/construction -> accounting.
- **Preconditions:** damaged structure, known stored materials, one repair worker.
- **Execution:** damage a known structure; allow repair; verify material withdrawal
  and block-state change.
- **PASS evidence:** structure repaired, stored material decreases exactly once.
- **FAIL:** repair without materials, duplicated withdrawal, or no block change.
- **Note:** new construction is out of scope until a production builder exists.

### C3 — Workforce under pressure

- **Covers:** independent assignments, preemption, cleanup, resumption.
- **Preconditions:** two workers, two distinct tasks, one-shot priority -1 goal.
- **Execution:** Run 81 equivalent plus a second task type if available.
- **PASS evidence:** distinct controllers/targets; A cleaned; B unchanged;
  A reacquires and delivers.
- **Status:** already proven for forestry; extend only when a second profession
  shares the same lifecycle risk.

### C4 — Defense and recovery

- **Covers:** raid -> guard response -> equipment/resource use -> return to work.
- **Preconditions:** completed village, guard with equipment, triggered raid.
- **Execution:** run one bounded raid; observe guard engagement and post-raid work.
- **PASS evidence:** guards engage, village survives or falls deterministically,
  villagers resume professions, no orphaned assignments.
- **FAIL:** guards idle, resources duplicated, or work never resumes.
- **Dependency:** A2 raid navigation is still open; do not claim defense
  acceptance before it closes.

### C5 — Persistence

- **Covers:** save/reload of identities, professions, storage, assignments.
- **Preconditions:** village with known storage, professions, and at least one
  active job at save time.
- **Execution:** save, reload, compare UUID/profession/storage, then let work
  resume.
- **PASS evidence:** identities and storage match; work resumes without duplicate
  ownership.
- **FAIL:** merged villages, lost storage, duplicated workers, or permanent idle.
- **Complements:** existing reload scenarios; does not replace them.

## G. Defect backlog

### Verified or documented (not newly reproduced in this audit)

| ID | Priority | Finding | Source/evidence | Correction |
|---|---|---|---|---|
| D1 | P1 | A2 raid navigation cannot complete its construction/traversal fixture | `docs/autonomous-village/ESTADO-ACTUAL.md`; `NavExecutor` | Keep A2 separate; fix construction placement/traversal before defense acceptance |
| D2 | P1 | C1 food chain exists in source but has no end-to-end runtime scenario | `VillageManager.repartirRaciones`; `FOOD-AUTONOMY-BLOCK-2-ACCEPTANCE.md` | Build one bounded food-autonomy scenario; do not add another consumption system |
| D3 | P1 | No general construction system; only repair | `VillagerRepairGoal`; source search | Define construction scope separately; do not overload repair |
| D4 | P1 (suspected) | Forestry has no cross-worker target claim; two goals can choose the same tree | `VillagerLumberjackGoal.descubrirTrabajo`; per-goal controller | Add a minimal village-level claim or deterministic task partitioning; prove with two-worker same-tree fixture |
| D5 | P2 (suspected) | All profession goals register at priority 0; real preemption among equal priorities is not proven | goal registrations; Run 81 used test priority -1 | Audit flag conflicts; define explicit priority policy; add a real competing-goal test |
| D6 | P2 | Farm coordinator is unit-tested but has no runtime multi-farmer proof | `VillageFarmCoordinator`, `VillageFarmOwnershipTest` | C1-style two-farmer plot assignment run |
| D7 | P2 (suspected) | Goal-local assignments are transient; reload behavior is not proven for active jobs | `ForestryTaskController`, `VillageSavedData` | C5 reload with active job; document reacquisition semantics |
| D8 | P3 | Many profession workflows have no end-to-end acceptance | inventory above | Add only the C1/C2/C4 scenarios that cover multiple professions |

### Unverified suspicions (do not treat as confirmed defects)

- Shared storage insertion may not be atomic under concurrent workers.
- Cook/tavern consumption may withdraw food without a matching need.
- Guard equipment use and post-raid resumption may leave stale goals.
- Animal husbandry may not coordinate feed/space with storage.
- Persistence may not preserve per-villager profession assignments consistently.

Each requires a bounded scenario before a production change.

## H. Correction roadmap

### Block 2 — Food autonomy baseline

1. Establish C1 with current farm/cook/pantry code.
2. Determine whether consumption is genuinely absent or lives outside the
   inspected packages.
3. Implement the smallest consumption/need loop only if C1 proves absence.
4. Add shortage behavior and exact pantry accounting.
5. Regression: farm ownership, storage counting, cook workflow.

### Block 3 — Materials and repair accounting

1. Establish C2 with `VillagerRepairGoal`.
2. Verify material withdrawal and block-state change.
3. Fix only demonstrated double-withdraw or no-material repair.
4. Defer new construction until its scope is explicitly defined.

### Block 4 — Workforce claims and scheduling

1. Prove or disprove D4 with two lumberjacks and one tree.
2. If confirmed, add the smallest claim mechanism consistent with farm plots;
   do not introduce a global registry for everything.
3. Audit priority/flag conflicts (D5).
4. Reuse the Run 81 two-worker harness; do not build a new framework.

### Block 5 — Defense and recovery

1. Keep A2 as its own acceptance track.
2. Once A2 closes, run C4 with real guards and a bounded raid.
3. Verify equipment/resource use and return to professions.
4. Do not claim defense from unit tests alone.

### Block 6 — Persistence and long-run stability

1. Run C5 with an active job and known storage.
2. Verify identities, professions, storage, and reacquisition.
3. Add a bounded multi-day soak only after C1–C5 pass.
4. Record any divergence as a defect with exact tick/state evidence.

## I. Regression policy

| Change area | Minimum regression |
|---|---|
| Controller/cleanup | `FORESTRY_TASK_CONTROLLER_TEST` |
| Village navigation/stuck | `VillageTaskNavigationPolicyTest`; A1 terminal/recoverable equivalents |
| Farm ownership | `VillageFarmOwnershipTest` |
| Planner/origin/area separation | forestry inner/arboleda runtime scenarios |
| Storage/delivery | exact delivery/conservation scenario |
| Scheduling/priorities | two-worker Run 81 equivalent |
| Food/pantry | C1 food scenario |
| Repair/resources | C2 repair scenario |
| Persistence | C5 reload scenario |

Documentation-only changes require `lint_aldea --strict` and `git diff --check`;
no Minecraft run.

## J. Completion criteria

The Autonomous Village is stable when:

1. Every implemented profession has at least one end-to-end production workflow
   with observable output and conservation.
2. Food production, storage, consumption, and shortage behavior form a closed
   loop.
3. Shared resources cannot be double-claimed or duplicated by concurrent workers.
4. Repairs/construction consume stored materials exactly once.
5. Guards respond to a real raid, use equipment correctly, and return to work.
6. Save/reload preserves village identity, storage, professions, and safe task
   reacquisition.
7. No worker can stall forever on an unchanged obstruction.
8. A bounded multi-day soak shows no orphaned assignments, storage drift, or
   permanent idle loops.

Until then, the village is **partially verified**, not complete.

## 2026-11-05 — Food economy audit correction

Runtime C1 proved the mechanics, but the economy audit found **24 rations/day**
(`EAT_INTERVAL_TICKS=1200`) and whole-item overdraw in `VillagePantry.sacarComida`.
This is a design/implementation mismatch, not a missing consumption system.
See `FOOD-ECONOMY-AUDIT.md`. Do not add food production to compensate before
correcting cadence and exact withdrawal.

## 2026-11-05 — One-day food measurement

Run 85 measured 24,000 ticks: pantry 256->64 points, bread 256->0, 12 tracked
meal events, 76 farm delivery items, 18 cooked pieces, negative net balance.
Guard data is invalid because the guard resolved to the worker UUID. See
`FOOD-ECONOMY-ONE-DAY-MEASUREMENT.md`; repeat with distinct guard before policy
changes.

## 2026-11-05 — Food measurement 2

Run 86 verified distinct worker/guard/farmer/cook identities. Whole-item
overdraw reproduced exactly: 10 requested, 3 bread removed (12 points), 10
returned, **2 points lost**. Worker 13 meals, guard 9 before the attack gap,
pantry 256->11. Production: 63 farm items, 13 cooked pieces, 10 fish events.
Negative daily balance. See `FOOD-ECONOMY-MEASUREMENT-2.md`.

## 2026-11-05 — Food Block 3 partial

Daily 24,000-tick cadence, night-gated ordinary rations, a guard feeding check,
and a per-container pantry value credit are implemented. Run 87 shows 1 worker
meal and 1 guard meal in 24,000 ticks and a 256->369 pantry. Still missing:
persisted credit, work-dependent hunger, proven pre-sleep timing, guard raid
attribution, and automated tests. See `FOOD-ECONOMY-BLOCK-3-ACCEPTANCE.md`.

## 2026-11-05 — Food Block 3.1 partial
Persistent pantry credit (`VillageSavedData.PantryCredit`) replaces the volatile
WeakHashMap; production withdrawals use the level-aware overload. Work-dependent
hunger, real bedtime gating, guard raid proof, automated tests, and post-change
runtime remain open. See `FOOD-ECONOMY-BLOCK-3.1-ACCEPTANCE.md`.

## 2026-11-10 — A8-F03 closed (agent-owned TALAR navigation)

A8-F03 is **COMPLETED / ACCEPTED**. Post-fix runtime: C1 (target removed before navigation
authorization), C2 (non-log candidate), C3 (obstruction/recovery), valid gate (ENFORCE
`gate=true` plus physical crossing) and gate negative (no crossing) all PASS. Build, strict
lint, diff check, `a8NavTests`, `NAVIGATION_LEASE_TEST`, `NAVIGATION_GATEWAY_POLICY_TEST`
and `NAVIGATION_GATEWAY_HARNESS_TEST` PASS. See
`A8-F03-FRESH-SESSION-EXECUTION.md` §14; §§3–4 preserve the historical pre-fix failures.

Production correction: `TalarStepPlanner.isValidTalarTarget()` is the authoritative
`BlockTags.LOGS` predicate; `Step(null)` no longer falls back to the previous target; invalid
targets are abandoned through the existing lifecycle; `navegar()` validates immediately
before authorization and `syncNavigationLease()` refuses invalid TALAR targets. ENTREGAR
ownership, gate geometry and NBT behavior are unchanged.

A8-01 (resource trace) and A7-12 (OFF/AUDIT equivalence) remain open/partial and are **not**
closed by A8-F03. Next: **A6-03 remaining runtime** (R2 blocked; R5-A/D, R6, R7 not tested;
R4 160/200 cap not verified) and **§H Block 4** (D4 cross-worker claim, D5 priority audit).
See `LUMBERJACK-PHASE-1-ACCEPTANCE.md` §A6-03 and the newest entry of `docs/CONTINUAR.md`.
