# Forestry Block 1 — Acceptance Record

## A. Executive summary

**FORESTRY BLOCK 1 — COMPLETE (A1–A7 PASS).**

Autonomous Forestry Block 1 is closed against the runtime evidence listed below.
It covers the lumberjack profession's discovery, navigation, harvesting,
replanting, Bone Meal growth, delivery, stuck recovery, and scheduler lifecycle.

Verified project baseline:

```text
Minecraft 1.21.1
NeoForge 21.1.249
Java 21
Branch: feature/autonomous-village
HEAD: 97220be5453b57387002b9bc91935807cc665274
```

This document records the implementation as audited in the current worktree.
It is not a claim that unrelated village systems are complete.

## B. Scope

In scope:

- `VillagerLumberjackGoal` and its per-goal `ForestryTaskController`.
- `ForestryWorkPlanner` ring/cursor scanning.
- Inner-forest and arboleda task selection.
- Tree classification, harvesting, replanting, Bone Meal growth.
- Delivery into `VillageStorage`.
- Physical no-progress detection and terminal failed-point handling.
- GoalSelector preemption cleanup and two-worker controller isolation.

Out of scope:

- Other professions, combat, raids, village generation balance.
- Long-running world stability beyond the runtime windows used here.
- A global reservation registry; none exists or is required.
- Save/reload resume of an in-flight forestry task; current task state is in-memory only.
- Production gameplay use of the test-only preemption goal.

## C. Architecture

### Classes and responsibilities

| Class | Responsibility |
|---|---|
| `VillagerLumberjackGoal` | Owns the lumberjack task lifecycle, target, phase, navigation, harvest/replant/grow/deliver, and its own `ForestryTaskController`. |
| `ForestryTaskController` | Per-goal assignment state: `SCOUTING` or an active phase; target coordinates; `assign`, `complete`, phase transitions. |
| `ForestryWorkPlanner` | Bounded ring/cursor column generator; keeps its cursor in memory between calls (not across save/reload); explicit `reset(originX, originZ)`. |
| `VillageGenerator` | Tree/arboleda/parcela geometry and tree classification (`esArbolSuelto`, support and standing checks). |
| `VillageManager` | Village membership, failed-point marking, navigation helpers, activity state. |
| `VillageStorage` | Warehouse access, support point, insertion and exact counting. |
| `VillagePantry` | Bone Meal pantry access/counting. |

### Actual call flow

```text
Villager GoalSelector
  -> VillagerLumberjackGoal.canUse / start / tick
  -> descubrirTrabajo
  -> buscarArbolArea(INNER_FOREST | ARBOLEDA | ANY)
  -> buscarArbolEnFrontera(dentro)
  -> ForestryWorkPlanner.nextColumns(256, RADIO_MAXIMO + 2)
  -> candidate filters: domain, inner/arboleda, failed point, tree, standing cell
  -> taskController.assign(x,y,z)
  -> navigation to task / stand point
  -> talar / plantar / abonar / entregar
  -> VillageStorage.guardar(...)
  -> taskController.complete() on terminal cleanup
```

The planner is not a global service. Each goal owns two planner instances
(`forestryPlannerDentro`, `forestryPlannerFuera`) and two readiness flags.
The arboleda/outside planner keeps its existing villager-relative origin.
The inner planner uses the authoritative village `center`.

## D. Architectural invariants

### D.1 Controller ownership

- Each `VillagerLumberjackGoal` owns exactly one `ForestryTaskController`.
- Controllers are never shared between villagers.
- `assign(x,y,z)` sets one target and an active phase.
- `complete()` returns to `SCOUTING` and is idempotent.
- `VillagerLumberjackGoal.stop()` clears only its own `target`, calls
  `taskController.complete()`, stops its navigation, and sets rest ticks.
- Terminal failure and ordinary preemption both release the owning assignment.
- A stopped goal can acquire new work on a later selector cycle.
- No global reservation registry is introduced.

### D.2 Navigation and physical progress

Constants and state:

```text
STUCK_LIMIT = 160 ticks
progress baseline: objetivoProgreso + ultimaPosicionProgreso + ultimoProgresoTick
meaningful movement: villager block position distance² > 1.0
```

Rules:

- Target change resets the baseline to the current position and current game time.
- A physical move greater than one block (squared distance > 1.0) resets the
  deadline and records the new position/time.
- Otherwise `stuckTicks = gameTime - ultimoProgresoTick`.
- Path-step changes, new `Path` objects, navigation activity, and `isDone()`
  do **not** reset the deadline.
- `desatascarSiEstaEncajado` may extract an embedded villager but does not reset
  the deadline.
- At `stuckTicks >= STUCK_LIMIT`:
  1. capture the assigned target;
  2. `VillageManager.marcarPuntoFallido(villager, target)`;
  3. return false from `canContinueToUse`;
  4. scheduler calls `stop()`;
  5. `taskController.complete()` releases the assignment;
  6. navigation stops;
  7. subsequent valid work can be selected.
- Failed points are excluded from later candidate selection.

### D.3 Forestry planner

- Inner origin: authoritative village `center`, not the lumberjack position.
- Outer/arboleda origin: villager position, preserving existing behavior.
- Batch budget: 256 columns per `nextColumns` call (unchanged).
- Maximum ring: `VillageGenerator.FENCE_RADIUS + 40`, plus the existing +2
  planner margin.
- Cursor and ring persist across ordinary batches and deliveries **within the
  lifetime of that goal instance**. They are in-memory fields, not entity NBT or
  world saved data, so they do not survive a world save/reload.
- Ordinary delivery does **not** reset either planner.
- `reiniciarBarrido()` is an intentional rescan:
  - increments `plannerGeneration`;
  - resets the inner planner to `center` when it is initialized;
  - clears the outer readiness flag;
  - leaves unrelated villagers' planners untouched.
- Inner candidates exclude arboleda columns.
- `ANY` alternates inner-first and arboleda-first without permanent priority.
- Failed points, invalid trees, and invalid standing cells are filtered before
  assignment.
- No nearest-tree rewrite or budget expansion is part of Block 1.

### D.3.1 Persistence boundary (source-verified)

There are three distinct storage mechanisms:

| Mechanism | What it stores | Does it survive save/reload? |
|---|---|---|
| Goal-instance fields | phase, `ForestryTaskController`, target, holes, planners, readiness, stuck/cooldown | **No** |
| Villager persistent data (entity NBT) | `DevilRpgHambre`, critical timestamp, last meal, village center tag, failed-point bookkeeping | Yes, with the entity |
| World saved data | village identity, pantry credit, siege/starvation warnings | Yes, with the level |

Current Forestry task state is **goal-instance state**:

- `ForestryTaskController`: state enum and target coordinates.
- `ForestryWorkPlanner`: origin, ring, cursor.
- `forestryPlannerDentroReady` / `forestryPlannerFueraReady`.
- remembered replant holes and pending work.
- current phase and delivery state.

None of these fields is written to entity NBT or `VillageSavedData` by the
current implementation. A reload therefore creates a fresh goal that starts in
`SCOUTING`; it does not resume the previous task. Block 1 runtime PASS results
remain valid for their tested lifetimes, but they do not include reload resume.

Reload-safe task state is a proposed, unimplemented phase. Its future acceptance
must cover save/reload while travelling, harvesting, and delivering; validation
of a restored target against the current world; safe re-scouting when invalid;
and no duplicate or lost wood, saplings, or Bone Meal.

### D.4 Harvest and growth

- Tree validation uses the production `VillageGenerator` predicates and a
  standing/approach cell check.
- Harvest removes the trunk column and attached canopy according to the
  existing `talar`/`rematarElArbol` logic.
- Replanting uses an available sapling and records the base.
- Bone Meal is withdrawn from storage/pantry only through the existing
  transaction path.
- Success is state-based: the block state must change after application.
- If the API did not consume Bone Meal, the stack is refunded; if it consumed
  it, no refund is issued.
- Successful growth updates the interaction state and permits continued work.
- Runtime evidence for growth and conservation is Run 27. Forced failed-growth
  refund is source-level only and is not claimed as runtime-tested.

### D.5 Delivery and conservation

- The goal carries harvested logs in its inventory.
- Delivery inserts the carried stack through `VillageStorage`.
- The implementation logs and relies on the actual accepted count, not an
  assumed full-stack insertion.
- Runtime evidence shows exact local conservation:
  `carriedBefore=2`, `carriedAfter=0`, `accepted=2`, warehouse local `+2`.
- Partial/rejected insertion behavior exists in source but was not forced in a
  dedicated runtime scenario; no broader conservation guarantee is claimed.

### D.6 Scheduler interruption

Verified by Run 81:

- Production lumberjack goal priority: `0`.
- Test-only competing goal priority: `-1`.
- Both use `Goal.Flag.MOVE`.
- The competing goal is a validation mechanism, not a production feature.
- It is one-shot: `NOT_TRIGGERED -> ACTIVE -> FINISHED`; `canUse()` is false
  after `FINISHED`.
- The real `GoalSelector` starts the competing goal, stops the lumberjack goal,
  and the lumberjack's `stop()` clears only its own target/controller.
- Worker B's controller identity and target remain unchanged at the
  preemption instant.
- When the competing goal ends, Worker A resumes normal scheduling, acquires
  new work, harvests, and delivers.

## E. Acceptance matrix

| ID | Acceptance criterion | Runtime evidence | Result |
|---|---|---|---|
| A1 | Terminal and recoverable obstruction | Runs 62 and 65 | PASS |
| A2 | Bone Meal transaction conservation | Run 27 | PASS |
| A3 | Inner-forest discovery and harvesting | Runs 57/58 | PASS |
| A4 | Exact warehouse delivery | Runs 57/58 and earlier delivery evidence | PASS |
| A5 | Growth and planner rescan | Run 27 | PASS |
| A6 | Scheduler interruption and two-worker isolation | Run 81 | PASS |
| A7 | Subsequent forestry task acquisition | Runs 57/58 and Run 81 | PASS |

## F. Runtime evidence

### Run 62 — terminal obstruction and subsequent work

```text
[A5-STUCK] assigned tree=70, 97, -3 enclosed tick=3640 owner=b89fcee1-...
[A5-STUCK] failedPoint tick=4182 nextTarget=null
[LENADOR-DELIVERY] carriedBefore=2 carriedAfter=0 accepted=2
                   warehouseBefore=130 warehouseAfter=132 delta=2
[A5] FIN result=PASS detail=lumber xpGain=8 removed=8 ... tick=5400
```

### Run 65 — recoverable wall-end route

```text
[Village] nueva tarea BlockPos{x=70, y=97, z=-3} desde BlockPos{x=126, y=97, z=15}
[A5-OBSTACLE] scenario=lumber-recover assigned tree=70,-3 enclosed tick=3672
[Village] talo 2 tronco(s) ... y desramo 42 hoja(s)
[LENADOR-DELIVERY] carriedBefore=2 carriedAfter=0 accepted=2
                   warehouseBefore=131 warehouseAfter=133 delta=2
```

The wall was placed east of the tree with both ends open; no failed-point event
was recorded for the tree.

### Run 27 — Bone Meal growth

```text
[LUMBER-FIXTURE] sap=118,97,0 state=oak_sapling[stage=0] ... bone=32
[LENADOR-ABONO] target=118,97,0 antes=oak_sapling[stage=0]
                despues=oak_sapling[stage=1] consumidas=1 restante=3
[LENADOR-ABONO] target=118,97,0 antes=oak_sapling[stage=1]
                despues=oak_log[axis=y] consumidas=1 restante=2
[LUMBER-XP] RESULT bone=32->0
[A5] FIN result=PASS detail=lumber xpGain=2 removed=2 ... tick=1800
```

### Run 57 — inner-forest discovery and delivery

```text
[Village] nueva tarea BlockPos{x=70, y=97, z=-3} desde BlockPos{x=126, y=97, z=16}
[Village] talo 2 tronco(s) ... y desramo 42 hoja(s)
[LENADOR-DELIVERY] carriedBefore=2 carriedAfter=0 accepted=2
                   warehouseBefore=126 warehouseAfter=128 delta=2
```

### Run 81 — two-worker scheduler isolation

```text
[A5-WORKERS] A=b89fcee1-... B=b433cefc-... setup=ACTIVE profB=fletcher
             posB=102,98,10 noAiB=false
[A5-WORKERS] goalA=1933457801 goalB=172570596
             ctrlA=1664555375 ctrlB=638864851
[A5-WORKERS] distinctGoals=true distinctControllers=true
             bothAssigned=true distinctTargets=true
             targetA=107,97,16 targetB=127,97,20
[A5-PREEMPT] start tick=601 target=null
[A5-PREEMPT] stop tick=681 target=null B=127,97,20 bUnchanged=true
[LENADOR-DELIVERY] accepted=2 warehouse delta=2   (repeated after resumption)
[A5] FIN result=PASS
     detail=A6 two-worker preemption/isolation/reacquisition verified
```

## G. Build and validation

Latest confirmed results for the closure:

```text
compileJava = BUILD SUCCESSFUL
compileTestJava = BUILD SUCCESSFUL
FORESTRY_TASK_CONTROLLER_TEST = OK
VillageFarmOwnershipTest = OK
VillageTaskNavigationPolicyTest = OK
policyTest = BUILD SUCCESSFUL
lint_aldea --strict = PASS
git diff --check = PASS
```

## H. Known limitations

- Failed-growth refund is implemented but was not forced in a dedicated runtime
  test.
- Rejected or partially accepted delivery was not forced in a dedicated runtime
  test.
- Long-term simulation stability beyond the recorded 5400-tick scenarios was
  not established.
- The preemption goal is test-only; production gameplay has no such goal.
- No global task reservation exists; ownership is per goal by design.
- Unrelated village systems are outside this acceptance record.

## I. Completion declaration

**FORESTRY BLOCK 1 — COMPLETE (A1–A7 PASS).**

Future changes must preserve the invariants in this document. Any change that
touches forestry navigation, planning, delivery, or goal scheduling must rerun
the relevant regression listed in the continuation policy, not necessarily the
entire A1–A7 campaign.

## J. Regression policy

Do not rerun the whole A1–A7 campaign for unrelated changes. Use the narrowest
relevant regression:

| Change area | Required regression |
|---|---|
| Controller ownership / `stop()` / `complete()` | `FORESTRY_TASK_CONTROLLER_TEST` |
| Navigation, stuck detection, obstruction handling | `VillageTaskNavigationPolicyTest` plus terminal and recoverable obstruction scenarios (Run 62/Run 65 equivalents) |
| Farm/forestry ownership interactions | `VillageFarmOwnershipTest` |
| Planner origin, cursor, area separation, scan budget | inner-forest and arboleda runtime scenarios; **within one goal instance, not across reload** |
| Delivery, storage, warehouse accounting | exact delivery/conservation scenario |
| Goal scheduling, priorities, preemption | two-worker scheduler isolation scenario equivalent to Run 81 |
| Bone Meal transaction or growth | Bone Meal growth/conservation scenario equivalent to Run 27 |

For documentation-only changes, run `python tools/lint_aldea.py --strict` and
`git diff --check`; no Minecraft run is required.
