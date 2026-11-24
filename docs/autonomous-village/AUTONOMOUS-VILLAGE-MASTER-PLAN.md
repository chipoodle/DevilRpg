# Autonomous Village — Master Plan and Stabilization Roadmap

> **Actualización 24-nov-2026 — CHECKPOINT ESTABLE.** Código probado `1016a7cf`; HEAD de referencia `18266502`
> (este checkpoint de continuidad es un commit docs-only posterior).
> C4 `c4n-20261124-0250` PASS 3/3 con cobertura **11/11**; POI `poi-final-20261124-0305` PASS 4/4 (F1 resuelto,
> F4 sin fantasma, digest vivo==disco); NAV `nav-final-20261124-0255` PASS 21/21. Tests: 525 persistencia, 32
> ownership, 39 BUILD, 253 contrato NAV, 15 autotest POI; `policyTest`, lint y `git diff --check` verdes.
> La campaña de cierre está **COMPLETE para el alcance autorizado**; no se abre otra. Deuda técnica y protocolo:
> `2026-11-24-CONTINUITY-CHECKPOINT.md`. A2/C4 siguen aceptados.
>
> **Actualización 23-nov-2026 — Incremento 3.** A (C4 runtime con invariantes obligatorios) revalidado;
> B (NBT/save-reload real), D (instrumentación del planner + línea base), E (herramientas de diagnóstico) y F
> (documentación) implementados. Informe: `2026-11-23-INCREMENT-3-RUNTIME-PERSISTENCE-INSTRUMENTATION.md`.
> Gates: `test` incluye `villagePersistenceTest` (484 aserciones) y `policyTest` incluye
> `NavPlannerMetricsTest` (68) y `NavSearchBaselineTest` (119). Estado del planner: **sin cambios de algoritmo**;
> la instrumentación está apagada por defecto (`devilrpg.nav.metrics`). A2/C4 siguen aceptados.

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

## Current handoff — 2026-11-12 21:10

- **Lumberjack Phase 1 persistence: ACCEPTED within scope.** A5-01..A5-12
  normal lifecycle and malformed-NBT variants PASS; Forestry Block 1 A1-A7
  COMPLETE; forced sapling-placement rejection and Bone Meal API-false refund
  now have runtime PASS. Abrupt process-crash atomicity is explicitly out of
  scope. Evidence: `LUMBERJACK-PHASE-1-ACCEPTANCE.md`.
- **Food Economy 3.1 and 3.2: COMPLETE/CLOSED.** The 24,000-tick runtime,
  real sleep transaction, both farmer harvest paths, pantry accounting and
  post-reload meal continuation are accepted. Evidence:
  `FOOD-ECONOMY-BLOCK-3.1-ACCEPTANCE.md` and
  `FOOD-ECONOMY-BLOCK-3.2-ACCEPTANCE.md`.
- **C1 — Food chain: ACCEPTED** by Food Economy 3.1/3.2 evidence.
- **C2 — Resource-to-repair accounting: ACCEPTED.** Run
  `c2-repair-accounting-r2-20261112-202200` proves no-material no-change,
  one-item stone-brick repair and no withdrawal for an already-repaired target.
  Evidence: `C2-REPAIR-ACCOUNTING-ACCEPTANCE.md`.
- **C3 — Workforce under pressure: ACCEPTED for forestry.** Historical Run 81
  proves distinct goals/controllers/targets, preemption cleanup and reacquisition.
  No second-profession extension is currently required by the C3 criterion.
  Evidence: `FORESTRY-BLOCK-1-ACCEPTANCE.md`.
- **C5 — Village persistence: ACCEPTED.** Run r3 proves two centers,
  UUIDs/professions, four exact warehouse quantities, active TALAR restoration,
  32.55 blocks of resumed work and zero duplicate Goals. Evidence:
  `C5-VILLAGE-PERSISTENCE-ACCEPTANCE.md`.
- **C4 / A2 defense and recovery: ACCEPTED (2026-11-12, run8).** Final callback
  variant passed in `c4-full8-20261112-2240`: 4/4 guards engaged, 12/12 living
  professionals resumed, 16 villagers alive, 0 worker casualties/unresolved
  UUIDs, 12/12 profession Goals, JOB_SITE 0 orphaned/duplicates, 15 guard hits,
  6 attacker deaths (5 guard-attributed, 1 iron-golem), 2 removed for recovery. Full evidence and cleanup record:
  `A2-C4-HEADLESS-ACCEPTANCE-20261112.md`.
- **C4 casualty semantics:** no zero-casualty threshold exists in the contract.
  The acceptance predicate now requires every living professional worker to
  resume its Goal, every missing initial worker to have a recorded death event,
  zero unresolved worker UUIDs, 4/4 guard engagement, a surviving village and
  clean JOB_SITE ownership. Casualties are explicit metrics, never silently
  treated as resumed workers.
- **Death instrumentation:** the harness now subscribes to
  `LivingDeathEvent` for the 12 initial worker UUIDs and logs UUID, profession,
  name, position, DamageSource message ID, attacker and direct entity. Final
  metrics separate expected/live/resumed/dead/unresolved workers. Compile
  passed. Final callback variant accepted by run8; see the C4 acceptance record.
- Run4 and run5 recorded 16 guard hits; run3 recorded none. Run5 had five
  attacker deaths and three attackers removed for recovery. The production wall
  remained 496 columns × 2 continuous layers = 992/992; JOB_SITE had 0 orphaned
  and 0 duplicate owners.
- Foundation repair remains PASS in its separate disposable world; manifests
  and hashes are in `A2-FOUNDATION-REPAIR-20261112-ACCEPTANCE.md`.
- **C1, C2, C3, C4 and C5 are accepted.** The C1-C5 acceptance gate is
  closed. Block 6 multi-day soak is the next separate task and has not yet run.
- **Automation/recovery rule:** A2/C4 runs are independent headless processes
  with per-run logs and `fin.txt`. Use
  `tools/a2n/consultar-c4.ps1 -RunId <id> -Wait` to poll every 15 seconds
  without restarting/stopping the run. After dropped remote calls, I retry
  transient failures up to three times with increasing delay. A fully offline
  remote agent/PC cannot be reconnected by the chat. Never relaunch an existing
  RunId. `.partial` remains protected.

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
| Food consumption/shortage | `VillageManager.repartirRaciones`, `pasarHambre` | **Runtime verified / Food Economy 3.1+3.2 COMPLETE** | `FOOD-ECONOMY-BLOCK-3.1-ACCEPTANCE.md`; `FOOD-ECONOMY-BLOCK-3.2-ACCEPTANCE.md` |
| Cook/tavern | Implemented | Unverified integration | source only |
| Miner/smith/cleric/fisher | Implemented | Unverified integration | source only |
| Animal husbandry | Implemented | Unverified integration | source only |
| Repair | Implemented | Unverified integration | source only |
| New construction | **Not found** | Not implemented/verified | source search |
| Storage/pantry | Implemented | Forestry delivery verified | Run 57/58/81 |
| Guard/raid | Implemented | Partial; A2 open | `ESTADO-ACTUAL.md`, unit tests |
| Village navigation | Implemented | Policy test; A2 open | `VillageTaskNavigationPolicyTest` |
| Goal scheduling | Implemented | **Runtime verified A6** | Run 81 |
| Resource trace (A8-01) | Diagnostic ledger + SYSTEM events | **COMPLETE** (A–E, B2, S, G PASS; SYSTEM pre-baseline boundary documented) | `A8-01-RESOURCE-TRACE.md`; runs `res-a8*` |
| Cross-worker same-tree claim (D4) | **Implemented** (ephemeral, world-scoped) | **Runtime verified D4-A–E** | `LUMBERJACK-PHASE-1-ACCEPTANCE.md` D4 post-fix; runs `claim-d4a…e-20261110-201309` |
| Equal-priority profession policy (D5) | **Explicit tiers implemented** | **Runtime verified D5-A–E** | `LUMBERJACK-PHASE-1-ACCEPTANCE.md` D5 post-fix; runs `policy-d5a…e-20261110-203455` |
| Agent-owned forestry navigation (A8-F03) | Complete | **Runtime verified post-fix** | `A8-F03-FRESH-SESSION-EXECUTION.md` §14 |
| Persistence | Implemented | **Lumberjack Phase 1 ACCEPTED; C5 village-level identity/active-job acceptance pending** | `LUMBERJACK-PHASE-1-ACCEPTANCE.md`; next: C5 |
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
- **Status: PASS (2026-11-12).** Run `c2-repair-accounting-r2-20261112-202200`; see `C2-REPAIR-ACCOUNTING-ACCEPTANCE.md`.
- **Note:** new construction is out of scope until a production builder exists.

### C3 — Workforce under pressure

- **Covers:** independent assignments, preemption, cleanup, resumption.
- **Preconditions:** two workers, two distinct tasks, one-shot priority -1 goal.
- **Execution:** Run 81 equivalent plus a second task type if available.
- **PASS evidence:** distinct controllers/targets; A cleaned; B unchanged;
  A reacquires and delivers.
- **Status: PASS for forestry (Run 81).** Distinct goals/controllers/targets,
  A cleanup, B unchanged, and A reacquisition/delivery are accepted in
  `FORESTRY-BLOCK-1-ACCEPTANCE.md`. Extend only if a second profession is shown
  to share the same lifecycle risk.

### C4 — Defense and recovery

- **Covers:** raid -> guard response -> equipment/resource use -> return to work.
- **Preconditions:** completed village, guard with equipment, triggered raid.
- **Execution:** run one bounded raid; observe guard engagement and post-raid work.
- **PASS evidence:** guards engage, village survives or falls deterministically,
  villagers resume professions, no orphaned assignments.
- **FAIL:** guards idle, resources duplicated, or work never resumes.
- **Status: foundation PASS; full defense acceptance remains OPEN.** The
  read-only probe and separately authorized second-world repair are accepted
  within their narrow scope; exact before/after evidence is in
  `A2-FOUNDATION-PROBE-20261112-ACCEPTANCE.md` and
  `A2-FOUNDATION-REPAIR-20261112-ACCEPTANCE.md`.
- The repaired disposable world has 400/400 first-non-air ring columns with
  non-empty runtime collision shapes. Exactly 33 water→stone changes; 367
  columns unchanged; zero unexpected changes or missing chunks.
- **Still pending:** contract a semantic PASS/FAIL verdict, instrument guard
  engagement and post-raid profession/assignment recovery, reconcile the
  launcher/parser, then run one full A2 raid. Read-only findings:
  `A2-EDIT5-READONLY-AUDIT-20261112.md`. Do not modify protected
  `A2NavHarness.java` / `.partial` or launch the raid without separate explicit
  owner approval. Do not use fallback `629`, historical worlds, or `run/saves`.
- **Dependency:** A2 raid navigation; do not claim defense acceptance before it closes.

### C5 — Persistence

- **Covers:** save/reload of identities, professions, storage, assignments.
- **Preconditions:** village with known storage, professions, and at least one
  active job at save time.
- **Execution:** save, reload, compare UUID/profession/storage, then let work
  resume.
- **PASS evidence:** identities and storage match; work resumes without duplicate
  ownership.
- **FAIL:** merged villages, lost storage, duplicated workers, or permanent idle.
- **Status: PASS (2026-11-12).** See `C5-VILLAGE-PERSISTENCE-ACCEPTANCE.md`.
- **Complements:** existing reload scenarios; does not replace them.

## G. Defect backlog

### Verified or documented (not newly reproduced in this audit)

| ID | Priority | Finding | Source/evidence | Correction |
|---|---|---|---|---|
| D1 | P1 | A2 raid navigation cannot complete its construction/traversal fixture | `docs/autonomous-village/ESTADO-ACTUAL.md`; `NavExecutor` | Keep A2 separate; fix construction placement/traversal before defense acceptance |
| D2 | CLOSED | Food production/harvest, pantry accounting, sleep transaction and post-reload meal continuation were not previously integrated | `FOOD-ECONOMY-BLOCK-3.1-ACCEPTANCE.md`; `FOOD-ECONOMY-BLOCK-3.2-ACCEPTANCE.md` | No new consumption system; keep accepted Food 3.1/3.2 regressions |
| D3 | P1 | No general construction system; only repair | `VillagerRepairGoal`; source search | Define construction scope separately; do not overload repair |
| D4 | CLOSED | Two lumberjacks could select the same tree; a world-scoped tree claim now serializes ownership | `ForestryTreeClaims`; D4-A–E runtime PASS, `LUMBERJACK-PHASE-1-ACCEPTANCE.md` | Keep owner+task release and TTL cleanup; regress only on claim invariant changes |
| D5 | CLOSED | Explicit goal priority tiers and MOVE/LOOK ownership are implemented and runtime verified | `VillageGoalPriority`; D5-A–E runtime PASS, `LUMBERJACK-PHASE-1-ACCEPTANCE.md` | Preserve explicit tier/flag policy; regress only on scheduling changes |
| D6 | P2 | Farm coordinator is unit-tested but has no runtime multi-farmer proof | `VillageFarmCoordinator`, `VillageFarmOwnershipTest` | C1-style two-farmer plot assignment run |
| D7 | P2 (partially resolved) | Lumberjack active-task NBT/reacquisition is runtime accepted; broader village identity/storage separation still lacks C5 proof | `LUMBERJACK-PHASE-1-ACCEPTANCE.md`; `VillageSavedData` | C5 multi-village reload with known storage and active job; document reacquisition semantics |
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

**Progress 2026-11-10.** D4 was **confirmed**, then the minimal claim was **implemented and
verified**: `ForestryTreeClaims` is an ephemeral, synchronized, `(dimension, tree)`-scoped
registry; the Goal acquires before exposing a TALAR target, renews each tick, validates
release by owner+task, releases on completion/abandonment/stop/death/leave, and expires
unrenewed claims after 200 ticks. Runtime D4-A–E PASS.

D5 is also **implemented and verified**: `VillageGoalPriority` defines explicit tiers
(EMERGENCY 0, CRITICAL 1, UTILITY 2, SECURITY 3, PROFESSION 4, SUPPORT 5, OPPORTUNISTIC 6).
Lumberjack was fixed from 0 to PROFESSION; tavern from -1 to CRITICAL (it used to outrank
flee). Runtime D5-A–E PASS: lower number preempts a running higher number, equal priority is
first-registered-wins, MOVE and LOOK coexist independently, dynamic eligibility stops/starts
normally, and a real lumberjack works/delivers with utility goals coexisting.

**Block 4 status: COMPLETED.** Steps 1–4 are satisfied: D4 proven and corrected, D5 audited
and given an explicit policy, and the two-worker A5 harness (Run 81 equivalent) reused without
a new framework. D4/D5 limitations remain documented (ephemeral claims, 200-tick TTL, D4-B
fixture goal removal, flag-gated navigation at PROFESSION, tavern hunger not triggered).

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

**Block 6 progress — 2026-11-12.** C5 save/reload is **PASS**:
two village centers remained distinct; both worker UUIDs/professions and both
warehouse fingerprints survived; an active TALAR task restored at the normal
village-life poll, ran for 260 ticks, moved 32.55 blocks and retained
duplicateGoals=0/0. See `C5-VILLAGE-PERSISTENCE-ACCEPTANCE.md`.
Block 6 steps 1–2 are complete. C1–C5 are now all accepted, including C4
(run8, `c4-full8-20261112-2240`). Step 3 (bounded multi-day soak) is therefore
unblocked and is the next task; it has not yet been executed. C4 final evidence
is in `A2-C4-HEADLESS-ACCEPTANCE-20261112.md`.

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

A8-01 (resource trace) and A7-12 (OFF/AUDIT equivalence) are **COMPLETE** in their declared
scopes; A7-12 preserves the Campaign 6 non-reproduced AUDIT one-off as a limitation.

**Historical checkpoint (superseded).** Before the 2026-11-10 closures this paragraph listed
A6-03 remaining runtime (R2 blocked, R5-A/D/R6/R7 untested, R4 caps unverified) and Block 4
D4/D5 as next. Those items are now CLOSED/COMPLETED; see “A6-03 runtime closed” and Block 4
below. They are not active work.

**Historical checkpoint — superseded 2026-11-12.** The Food Economy 3.1/3.2
residuals were subsequently closed; see their acceptance files. Lumberjack Phase 1
is accepted within scope; C5 village persistence and C2 repair/resource accounting
are now PASS; C3 is accepted by historical Run81. The next required item is
**C4 defense/recovery via A2**, blocked until the owner approves fixture 661,71,533.
Do not treat this 2026-11-10 food checkpoint as current.

## 2026-11-10 — A6-03 runtime closed; D4 confirmed; D5 audited

A6-03 is **CLOSED** for its runtime scenarios. New post-fix runs (fresh A5WALL copies):
R2 planting priority PASS (`a6-r2-20261110-193049`: PLANTAR first, sapling 16->15, then 5
logs delivered; blocker was fixture-only arboleda re-planting); R5-A normal threshold PASS
(`a6-r5a-20261110-193049`: 12 logs, warehouse +12); R5-D recovery PASS
(`a6-r5d-20261110-193049`: failure 4470, cooldown 4510, success 4558); R4 cap PASS
(`a6-r4cap-20261110-193049`: cooldowns 40/80/120/**160**/**200**, attempt 6 at 5262); R6
post-harvest cooldown PASS (`a6-r6-20261110-193049`: failure 5094, cooldown 5134, success
5746); R7 save/reload recovery PASS with the timing limitation that the old deadline had
already expired at load (`a6-r7save...`/`a6-r7load...`). R1/R3/R4 40/80/120/R5-B/C remain
accepted from Runs 186/185/182/192.

**D4 confirmed** (`a6-d4b-20261110-195518`): two lumberjacks selected the same tree
`98,98,0` with distinct task IDs and independent per-agent leases; A harvested/delivered 5,
B still targeted the missing tree and later recovered. No cross-worker claim exists. Block 4
step 1 is complete; **historical checkpoint: step 2 was then next, but the minimal claim was
subsequently implemented and D4-A–E PASS; this is superseded by “Block 4 status: COMPLETED.”**

**D5 audited** (`a6-d5b-20261110-200117`): equal-priority goals resolve by insertion order and
MOVE/LOOK flag ownership, not priority preemption (A ran lumberjack, B ran the first-added
competing goal). **Historical checkpoint: this audit preceded the explicit
`VillageGoalPriority` policy; D5-A–E subsequently PASS and Block 4 is COMPLETED.**

A8-01 and A7-12 are COMPLETE (A7-12 with the Campaign 6 non-reproduced AUDIT one-off
documented as a limitation). No production
behavior changed in A6-03/Block 4; the new fixtures are test-only in the git-ignored debug
controller.

## 2026-11-10 — D4 cross-worker claim implemented and verified

The confirmed D4 defect is corrected by an **ephemeral, world-scoped** claim keyed by
`(dimension, tree BlockPos)`. The per-agent navigation lease was not the right layer: it is
scoped to one agent and models navigation ownership, not the tree as work. The Goal now
acquires the claim before exposing a TALAR target, discovery skips trees owned by another
worker, and release is validated by owner+task with a 200-tick TTL and death/leave cleanup.

Runtime PASS: D4-A basic contention, D4-B release/successor, D4-C owner death, D4-D two-tree
concurrency, D4-E one-tree regression (`claim-d4a…e-20261110-201309`). The original
duplicate-target behavior is gone. `ForestryTreeClaimsTest` covers atomic acquisition,
denial, concurrency, validated release, stale release and expiry. D5 (priority/flag policy)
remains open; no priority change was made.

## 2026-11-10 — D5 priority policy implemented and verified

The audit disproved the "all profession goals at priority 0" premise: flee was 0, tavern
**-1**, gate/door 2, guard 3, professions mostly 4, repair/collect 5, pickup 6 — but lumberjack
was accidentally registered at **0** and tavern at **-1** (above flee). The explicit policy is
now centralised in `VillageGoalPriority`: EMERGENCY 0, CRITICAL 1, UTILITY 2, SECURITY 3,
PROFESSION 4, SUPPORT 5, OPPORTUNISTIC 6. Lumberjack is 4; tavern is 1; guard 3; gate/door and
the flag-gated dispatcher 2; repair 5 for workers / 3 for builders; collect 5; pickup 6.

Actual `GoalSelector` semantics were read from the 1.21.1 sources and verified at runtime:
insertion order, flag locks, lower number may preempt a running higher number, equal priority
cannot replace (first registered wins), MOVE and LOOK are independent. Runtime PASS: D5-A
lower-number preemption, D5-B equal-priority one MOVE owner, D5-C MOVE+LOOK coexistence,
D5-D stop/start on eligibility change, D5-E real lumberjack work/delivery with flee 0,
tavern 1 and flagless utility goals coexisting, plus a zombie-triggered flee preemption
(`policy-d5a8…d…e3-20261110-203455`). No D4 claim,
A8-F03, ENTREGAR or NBT change. Legacy `VillageNavigationGoal` remains flag-gated at
PROFESSION; documented as a limitation.

## 2026-11-10 — A8-01 per-actor resource ledger (historical checkpoint — superseded)

A default-off, bounded ledger now traces the autonomous forestry lifecycle per worker:
harvest, inventory insert, warehouse deposit/drop, pickup, and fuel consumption. Transfer
quantities are actual (`before - remainder`); `INVENTORY_INSERT` is informational; actor,
warehouse and global equations are checked; truncation or unattributed events force
INCOMPLETE. Runtime A–E: single, two-worker shared, partial deposit, pickup, OFF and AUDIT all
complete (`res-a8*`). The first two-worker run exposed a real +1 sink: the cook burned one
warehouse log as fuel; `VillageStorage.quitarLena` now records `CONSUMED` with the consuming
villager. **Historical checkpoint (superseded 2026-11-11):** this paragraph predates the final
A8-01 closure. Current status is A8-01 **COMPLETE** (post-initialization autonomous scope;
system events and G/S evidence accepted); A7-12 is **COMPLETE** with the Campaign 6
non-reproduced limitation.

## 2026-11-11 — Next block: Food Economy 3.1/3.2 residual

Food 3.2 is CLOSED (Runs 98/105/109/110/128/130/132): normal 60 feeding, 25-point meal,
active-raid guard feeding + defense resumption, critical effects/death, pantry credit
persistence and daytime post-reload second meal all PASS. One documented limitation remains:
no automated NBT serialization test.

Food 3.1 is PARTIAL. Residual acceptance criteria:

1. Work-dependent hunger policy/accumulator — **implemented and unit-tested**, runtime pending.
2. Meal gated on the real sleep transition (bedtime handshake) — **forced-sleep path done**;
   normal vanilla `SleepInBed` remains unintegrated.
3. Automated pantry-credit tests (credit consumption, mixed denominations, insufficient
   stock, identities, save/load, retry idempotence) — **not added**.
4. Automated NBT serialization test for hunger/pantry credit (also the 3.2 limitation).
5. Post-change runtime after 1–2: a 24,000-tick food run proving the new policy, bedtime
   ordering and conservation.

Dependencies: current `VillageManager`/`FoodHungerPolicy`/`VillagePantry`/`VillageSavedData`
and `FoodHungerPolicyTest`; no A2, C5 or architecture work. Runtime needs disposable A5WALL
copies, one client at a time, full resource/pantry evidence, save/reload where relevant.
Do not mark 3.1 COMPLETE until all five criteria have evidence; 3.2 remains CLOSED.


## Actualización de aceptación — C4 headless (2026-11-12)

C4 — fixture de aldea de producción, asedio headless, combate de guardias y recuperación profesional — queda **ACEPTADO** tras dos ejecuciones independientes consecutivas PASS: c4-full7-20261112-0230 y c4-full8-20261112-2240. Run8 verificó muro 992/992; 4/4 guardias con objetivo, 15 impactos y 5 bajas atribuidas; 12/12 trabajadores vivos/reanudados; 0 bajas y 0 UUID no resueltos; 16 aldeanos vivos; JOB_SITE 1 asignación, 0 huérfanas y 0 duplicados; 6 atacantes muertos y 2 retirados para recuperación; 0 fallos de instrumentación y guardado normal. Evidencia completa y hashes: docs/autonomous-village/A2-C4-HEADLESS-ACCEPTANCE-20261112.md, sección 9. Esto no cierra A2 global ni autoriza saltar el soak multi-día; sus gates siguen abiertos. Sin commit/push.
