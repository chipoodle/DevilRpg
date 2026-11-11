# Lumberjack Phase 1 — Reload-Safe Task State: Implementation Status

Status: IMPLEMENTED IN SOURCE; RUNTIME ACCEPTANCE NOT RUN.

## Implemented
- Versioned villager NBT compound `DevilRpgForestryTask` (Version=1).
- Persisted fields: Objective, Phase, Controller, Target, Holes (positions),
  SearchTurn, AreaTurn, InsideFirst, DeliverAfterChop, PlannerInside/Outside
  origin/ring/cursor/finished/ready.
- Read in `VillagerLumberjackGoal` constructor before first tick.
- Write after task assignment, at tick end, and on stop.
- `ForestryTaskController.restore` and `ForestryWorkPlanner.restore`.
- Validation: objective, Lumberjack profession, target radius, phase/controller/
  target consistency via `ForestryTaskPolicy`.
- Missing/malformed known version -> remove and SCOUTING.
- Unknown higher version -> leave untouched, transient SCOUTING, never overwrite.
- Harvest revalidates target is still LOGS before mutation.
- Planting/Bone Meal/Delivery use existing world/inventory authoritative checks.

## Deviations from approved design
- Hole species (`Seed`) is not serialized; holes are restored species-less and can
  be planted with any available sapling. Design allowed optional species.
- Immediate revalidation is implemented for harvest target; planting/Bone Meal
  already validate their target state, delivery uses carried stacks.
- Transient navigation/timers intentionally not persisted.

## Automated tests
`ForestryTaskPolicyTest` in `policyTest`:
- phase/controller/target consistency matrix;
- SCOUTING without target;
- planner restore and negative-bound clamping.
Result: `FORESTRY_TASK_POLICY_TEST: OK`.

## Not verified
- All mandatory A5 save/reload scenarios (travelling, harvesting, replanting,
  growing, delivering, stale target, profession/village mismatch, malformed NBT,
  unknown version, overlapping workers, resource accounting).
- Block 1 regressions 62/65/27/57/58/81.
- Actual NBT round-trip in a real world.
- No Phase 1 acceptance is claimed.

## Pre-A5 verification (2026-11-07)

- Persistence frequency: no tick-end save. Writes occur only on task assignment,
  after a completed work action (harvest/plant/bone meal/delivery), and on stop.
  Planner advance during discovery is captured by the assignment save.
- Hole species: `Holes[].Seed` now stores the item registry id and restores the
  `Item` for `Hueco`; species is preserved across reload.
- Unknown higher version: `tareaDesconocida` suppresses all writes; `guardarTarea`
  returns before constructing/writing a compound, so the original is untouched.
- Runtime A5 persistence suite and Block 1 regressions: NOT RUN.

## A5 runtime acceptance attempt (2026-11-07)

Result: **NOT RUN**. No disposable A5 world was launched for Phase 1 reload.

Blocker: the approved Phase 1 A5 scenarios (A5-01..A5-12) do not yet exist as
runnable scenarios in `A5TestController`; the existing forestry scenarios are
single-lifecycle and have no Phase 1 save/reload phase driver. Building and
validating that harness was not completed in this run.

| Scenario | Result |
|---|---|
| A5-01 TRAVELLING | NOT RUN |
| A5-02 HARVESTING | NOT RUN |
| A5-03 REPLANTING | NOT RUN |
| A5-04 GROWING / Bone Meal | NOT RUN |
| A5-05 DELIVERING | NOT RUN |
| A5-06 stale target | NOT RUN |
| A5-07 profession mismatch | NOT RUN |
| A5-08 village/objective mismatch | NOT RUN |
| A5-09 missing/malformed v1 | NOT RUN |
| A5-10 unknown higher version | NOT RUN |
| A5-11 two-worker overlap | NOT RUN |
| A5-12 resource accounting | NOT RUN |
| Block 1 62/65/27/57/58/81 | NOT RUN |

No runtime PASS is claimed. Phase 1 remains NOT ACCEPTED.

## First real save/reload smoke test — PASS (2026-11-07)

Run ID: `stabilization-block1-scan-20261030-134`
World: `build/stabilization-block1-scan-20261030-134/game`, level A5WALL.
UUID: `b89fcee1-c91a-4b1f-a9c4-66471b616288`
Objective: 0.

Stage 1 (`lumber-persist-save`) captured and saved:
```text
Phase=RECOGER_HARINA Controller=TRAVELLING Target=107,97,16
PlannerInside Ring=8 Cursor=31
PlannerOutside Ring=8 Cursor=31
Version=1 Objective=0
```
World saved via `saveEverything(false,true,true)` and client closed normally.

Stage 2 (`lumber-persist-load`) reopened the same world:
```text
uuid=b89fcee1-... goal=true objective=0
phase=RECOGER_HARINA controller=TRAVELLING target=107,97,16
Version=1, PlannerInside Ring=11 Cursor=71
[A5] FIN PASS
```
Same UUID/objective/phase/controller/target; goal reinstalled; navigation
reconstructed (path is not serialized). Planner advanced after reload, so it did
not resume the exact saved cursor, but it retained a valid forward checkpoint and
did not skip the assigned phase/target.

Procedure (two stages):
1. `run-a5.ps1 -Scenario lumber-persist-save` on disposable world; normal close.
2. Re-run with `-Scenario lumber-persist-load` against the same output/game dir.

This proves only the TRAVELLING/RECOGER_HARINA smoke case. A5-02..A5-12 and Block 1
regressions remain NOT RUN. Phase 1 remains NOT ACCEPTED.

## A5 expansion executed 2026-11-07

### A5-01 smoke — PASS (Run 134)
TRAVELLING/RECOGER_HARINA task survived real save/reload; same UUID, objective,
phase, controller, target; goal reinstalled. Planner advanced 8/31 -> 11/71
(legitimate scan after restore; no stale path restored).

### A5-10 unknown higher version — PASS (Run 135)
Stage 1 injected `Version=99` + `FutureMarker="keep-me"` into the real task
compound and saved. Stage 2 reopened the world, let the goal tick to 1000, and
read:
```text
Version:99 FutureMarker:"keep-me" ... nbt unchanged
[A5] FIN PASS
```
The unknown compound was neither overwritten nor removed.

### Not run
A5-02 harvesting, A5-03 replanting, A5-04 growing, A5-05 delivering,
A5-06 stale target, A5-07 profession mismatch, A5-08 village/objective mismatch,
A5-09 malformed data, A5-11 overlap, A5-12 resource accounting.
Block 1 regressions 62/65/27/57/58/81: NOT RUN.

Phase 1 remains NOT ACCEPTED.

## A5-09 malformed data — partial PASS (Run 136)
Stage 1 injected Version=1 missing Phase/Controller/Target into the real task
compound and saved. Stage 2 reopened the world:
```text
uuid=b89fcee1-c91a-4b1f-a9c4-66471b616288
goal=true taskRemoved=true
[A5] FIN PASS
```
Covered: missing required version-1 fields.
Not covered: invalid phase enum, invalid controller enum, invalid target,
invalid planner bounds, invalid hole position, invalid sapling id, and subsequent
valid task assignment after malformed rejection.
No crash and safe SCOUTING fallback observed for the covered case.

## Resource-phase fixtures
A5-02 harvesting, A5-03 replanting, A5-04 growing, A5-05 delivering were NOT
implemented/run. No wood/sapling/Bone Meal accounting was collected.
Phase 1 remains NOT ACCEPTED.

## A5 malformed variants (2026-11-07)
- Missing Phase/Controller/Target: PASS (Run 136).
- Invalid Phase enum `NOT_A_PHASE`: PASS (Run 137); task removed, goal installed.
NOT RUN: invalid controller enum, invalid target, invalid planner bounds,
invalid hole position, invalid sapling registry id, subsequent valid assignment.

## Three-status capability table
| Capability | Production | Block 1 historical | Phase 1 reload |
|---|---|---|---|
| Harvest/discovery | implemented | PASS (62/65/57/58) | NOT RUN |
| Replanting | implemented | PASS (27/57) | NOT RUN |
| Bone Meal growth/refund | implemented | PASS (27); refund source-only | NOT RUN |
| Delivery/accounting | implemented | PASS (57/58) | NOT RUN |
| Two-worker isolation | implemented | PASS (81) | NOT RUN |

Historical Block 1 conditions inspected: Run 62 terminal obstruction; Run 65
recoverable wall-end route; Run 27 Bone Meal growth; Runs 57/58 inner forest and
delivery; Run 81 two-worker isolation.

## A5-02 HARVESTING reload — BLOCKED / NOT RUN

The fixture cannot reach a genuine `ForestryTaskController.State.HARVESTING`.
Source evidence: `VillagerLumberjackGoal` only calls `taskController.assign`,
`travelling`, `delivering`, `scouting`, `complete`. It never calls
`taskController.harvesting()` (nor `replanting`, `growing`, `returning`).

Consequences:
- phase `TALAR` always runs with controller `TRAVELLING` until completion;
- controller `HARVESTING` is unreachable through production behavior;
- injecting `Controller="HARVESTING"` in NBT would be fabricated state and is
  rejected as acceptance evidence.

No A5-02 save/reload run was performed. Acceptance status: NOT RUN / BLOCKED by
missing production controller transition. A minimal future correction would be to
call `taskController.harvesting()` on arrival at the tree and transition through
REPLANTING/GROWING/RETURNING at the matching operations, but that is a production
change and was not made in this test-infrastructure task.

## A5-02 HARVESTING reload — PARTIAL PASS (Run 138)

Correction: production now calls `taskController.harvesting()` when `Fase.TALAR`
actually begins the chopping cycle after arrival, persists at that boundary, and
returns the controller to SCOUTING after the target is consumed.

Stage 1 (`lumber-persist-harvesting-save`) reached a genuine state:
```text
uuid=b89fcee1-c91a-4b1f-a9c4-66471b616288 objective=0
phase=TALAR controller=HARVESTING target=49,97,-31
Version=1 PlannerInside Ring=68 Cursor=207
```
World saved and closed normally.

Stage 2 (`lumber-persist-harvesting-load`) reopened the same world:
```text
uuid=b89fcee1-... goal=true objective=0
phase=TALAR controller=SCOUTING target=- consistent=true
[A5] FIN PASS
```
The worker had completed a real harvest after reload (log: `talo 6 tronco(s)`,
replanted) and safely re-scouted. This satisfies restoration-or-safe-re-scout and
goal resume, but **no inventory/storage ledger was captured**, so “no duplicate
drops” is NOT VERIFIED. A5-02 status: PARTIAL.

## A5-02 HARVESTING reload — PASS (Run 139, resource ledger)

Stage 1 before save:
```text
uuid=b89fcee1-c91a-4b1f-a9c4-66471b616288 objective=0
phase=TALAR controller=HARVESTING target=49,97,-24
invLogs=0 invSap=0 invBone=0 storageLogs=119
```
Stage 2 immediately after reload, before resumed work:
```text
phase=TALAR controller=HARVESTING target=49,97,-24
invLogs=0 invSap=0 invBone=0 storageLogs=119
goal=true consistent=true
```
Reload produced no resource change (wood 119 -> 119, carried 0 -> 0).

Stage 3 after resume:
```text
[Village] talo 5 tronco(s) ... desramo 54 hoja(s)
[LENADOR-DELIVERY] carriedBefore=5 carriedAfter=0 accepted=5
                    warehouseBefore=114 warehouseAfter=119 delta=5
[LUMBER-HARVEST-AFTER] invLogs=0 invSap=3 storageLogs=124
```
Wood ledger: 119 pre + 5 actual accepted = 124 final; 0 carried; no duplication.
Saplings: 0 pre, 3 gained from leaves, 0 consumed; final 3. Bone Meal unchanged 0.
A5-02 status: PASS.

## A5-03 REPLANTING reachability — BLOCKED / NOT RUN

Source audit:
- `tick()` switch has `case PLANTAR -> plantar(level)` but never calls
  `taskController.replanting()`.
- Immediate replant after chopping is `replantar(level, hueco, misma)` inside
  `talar`, also without a controller transition.
- `ForestryTaskController.State.REPLANTING` is only referenced by the persistence
  validator (`ForestryTaskPolicy`), never set by production behavior.
- The goal stays controller `TRAVELLING` while phase `PLANTAR` and while planting.

Therefore a genuine `PLANTAR/REPLANTING` save state is unreachable. Injecting
`Controller=REPLANTING` would be fabricated and is rejected as evidence. No A5-03
fixture was added and no world was launched.

Minimal future correction (not applied): call `taskController.replanting()` at the
start of the real planting operation for a pending hole (before `plantar`), and
decide whether the immediate post-chop `replantar` is a same-tick sub-step of
HARVESTING or a REPLANTING transition. Serialization policy already accepts
`PLANTAR/REPLANTING`.

## REPLANTING controller transition — corrected (2026-11-07)

`VillagerLumberjackGoal.tick()` now transitions at the real pending-hole planting
boundary, after arrival and at the first planting swing:
```java
if (fase == Fase.PLANTAR
        && taskController.state() != ForestryTaskController.State.REPLANTING) {
    taskController.replanting();
    guardarTarea();
}
```
It runs only after the distance/arrival check, not while approaching. Persistence
is event-driven and idempotent.

The immediate post-chop `replantar(...)` remains a HARVESTING sub-operation; it is
not a separate REPLANTING lifecycle. Successful pending-hole planting ends in
SCOUTING with target cleared. Failed planting keeps existing retry semantics and
does not persist an invalid combination.

`ForestryTaskPolicy` already accepts `PLANTAR + REPLANTING + target`; no validation
was weakened.

Tests added: assign->TRAVELLING, harvesting->HARVESTING/idempotent,
replanting->REPLANTING/idempotent, PLANTAR/REPLANTING policy, complete->SCOUTING.

A5-03 reload remains **NOT RUN**; no Minecraft was launched in this correction.

## REPLANTING runtime reachability — NOT VERIFIED (Run 140)

Observed real sequence:
```text
TALAR/TRAVELLING target oak_log
TALAR/HARVESTING (e.g. tick 4205)
TALAR/SCOUTING (tick 4253, invSap 3)
ENTREGAR/SCOUTING (tick 4801)
TALAR/TRAVELLING next tree
...
invSap accumulated 3,4,6
```
The worker never entered `PLANTAR/REPLANTING`. It remained in the
harvest/deliver loop; pending-hole planting requires 8 carried saplings or no
tree available, and neither condition occurred in ~8,000 ticks of observation.
No sapling was planted in the independent PLANTAR path; no block/hole transition
was observed.

Therefore the REPLANTING transition is source-correct but runtime reachability is
NOT VERIFIED. A5-03 reload remains NOT RUN. The fixture was not fabricated and no
production code was changed in this runtime task.

## REPLANTING reachability attempt 2 — NOT VERIFIED (Runs 141–143)

Source decision order: with `semillas >= 8`, production only sets `PLANTAR` if
`primerPendiente(level)` or `huecoDeLaArboleda(level)` returns a valid hole.
Otherwise it falls through to `troncoDentro` and continues TALAR.

Run 141: cleared saplings once; worker reacquired them before a hole was left.
Run 142: held saplings empty; worker still replanted from same-tick leaf drops.
Run 143: filled inventory with dirt stacks so saplings overflowed to storage;
after harvest the NBT showed `holes=1`, `invSap=9..13`, but phase remained
`TALAR` / `TRAVELLING|SCOUTING`. No `PLANTAR/REPLANTING` transition occurred in
~9,000 ticks. The persisted hole count did not prove the hole was valid for
`primerPendiente`; when no valid hole was returned, the existing tree branch won.

No block was planted by the independent PLANTAR path; no inventory sapling was
consumed there. A5-03 remains NOT RUN; no production code changed.

## Pending-hole forensic diagnosis (Runs 144–145)

Run 144 recorded a real hole after harvest:
```text
hole=53,97,-17 dist=31.906 min=65.0
base=air below=grass_block above=air
dirt=true aboveAir=true inArboleda=true
```
This hole satisfies both visible production predicates:
`esHuecoDeTierra` and `seReplantaAqui` (via arboleda).

Run 145 recorded:
```text
hole=53,97,-38 dist=46.615 min=65.0
base=air below=dirt above=air
dirt=true aboveAir=true inArboleda=true
```
After adding 8 real saplings (`invSap=8..10`), the goal remained TALAR for
~3,000 ticks; no PLANTAR/REPLANTING, no planting.

Source finding: `primerPendiente(level)` removes non-accepted holes from the
in-memory `pendientes` list but does NOT call `guardarTarea()`. Therefore the NBT
`Holes` list can remain stale after runtime rejection/removal. The NBT count=1
does not prove the runtime list still contains an accepted hole.

Because `pendientes` and `primerPendiente` are private and no read-only production
accessor exists, the exact first failing predicate at the moment of rejection
cannot be observed without modifying production code, which is prohibited.
Status: BLOCKED at predicate-level forensics; REPLANTING NOT VERIFIED.

## Pending-hole root cause — PLANTAR branch is in the wrong lifecycle method

Source verification:

- `canUse()` (around line 338–445) contains the pending-hole decision:
  `primerPendiente` -> `huecoDeLaArboleda` -> `buscarClaro` -> `Fase.PLANTAR`.
- `tick()` (line 570) calls `descubrirTrabajo(level)` whenever `target == null`.
- `descubrirTrabajo()` (line 448) contains NO pending-hole and NO `Fase.PLANTAR`
  branch. It only handles arboleda Bone Meal, then trees (TALAR).
- `canContinueToUse()` returns true with `target == null`, so the running goal does
  not stop/restart and `canUse()` is not called again.

Therefore, after a harvest sets `target=null`, the live goal always enters
`descubrirTrabajo` and can only choose TALAR/ENTREGAR, never PLANTAR. This is why
Runs 143–147 never entered REPLANTING even with a valid hole and 8–14 saplings.
`primerPendiente` was not evaluated at all in the live path; the runtime NBT hole
stayed stale. `HOLEDIAG` produced zero lines because its code is in `canUse`,
which was not invoked during the continuous run.

Classification: BLOCKED — structural production lifecycle defect, not a hole
predicate failure. No production change made in this diagnostic.

## A5-03 fix — shared planting selection (Run 148)

Root cause fixed: the pending-hole/PLANTAR decision was extracted to
`seleccionarHuecoParaPlantar(...)` and is now called from both `canUse()` and the
live `descubrirTrabajo()` path. Order preserved: threshold -> `primerPendiente` ->
`huecoDeLaArboleda` -> `buscarClaro`+cooldown -> PLANTAR; otherwise TALAR.

Run 148 (`stabilization-block1-scan-20261030-148`) real evidence:
```text
[REPLANT2] holes=1 addedSap=8 invSap=8 tick=2803
[HOLEDIAG] primerPendiente entry size=1 list=[53,97,-17]
[HOLEDIAG] candidate ... at=air above=air below=grass_block
           hole=true dist=31.9 arboleda=true replanta=true
[HOLEDIAG] candidate accepted pos=53,97,-17
[REPLANT2] wait phase=PLANTAR controller=SCOUTING holes=0 invSap=7 tick=3000
```
So the live path evaluated and accepted the real pending hole, selected PLANTAR,
consumed one sapling (8->7), and removed the hole. Direct block-placement log was
not captured (the fixture only armed END on REPLANTING; selection was
PLANTAR/SCOUTING and quickly left), so actual setBlock success is PARTIAL, not
fully verified. No save/reload acceptance was run.

## A5-03.1 actual planting placement — PASS (Run 149)

Execution path: `tick` -> `case PLANTAR -> plantar(level)` ->
`plantarSemilla` -> `level.setBlock(pos, sapling, UPDATE_ALL)`.
The hole is removed after successful placement.

Run 149 real evidence:
```text
[REPLANT2] holes=1 addedSap=8 invSap=8
[HOLEDIAG] candidate pos=45,97,-31 ... hole=true arboleda=true replanta=true accepted
[REPLANT2] BEGIN hole=45,97,-31 controller=REPLANTING invSap=8 before=air
[Village] El lenador: planto un arbol en 45,97,-31 (le quedan 7 semillas)
[REPLANT2] END hole=45,97,-31 blockId=minecraft:oak_sapling
           sapBefore=8 sapAfter=7 holesNow=0
[A5] FIN PASS
```
Verified: selected real hole, reached planting execution, target air ->
`minecraft:oak_sapling`, sapling consumed 8->7, hole removed 1->0, final block
present after operation. A5-03 persistence itself remains NOT RUN.

Selection cases: A PASS runtime. B/C/D NOT TESTED — helper is private and requires
ServerLevel/village geometry; no safe pure-test seam without exposing production
state. No planting rules changed.

## A5-03 REPLANTING save/reload — PASS (Run 151)

Stage 1 (`lumber-replant-save`) saved at the genuine pending planting state:
```text
uuid=b89fcee1-c91a-4b1f-a9c4-66471b616288 objective=0
phase=PLANTAR controller=REPLANTING target=36,97,-31
invSap=8 holes=1 block=air
NBT Version=1 Holes=[36,97,-31] Target=36,97,-31
```
World saved and closed normally.

Stage 2 (`lumber-replant-load`) reopened same world:
```text
tick=50  same UUID/objective, phase=PLANTAR controller=REPLANTING
         target=36,97,-31 invSap=8 holes=1 block=air
         goal not yet installed (false)
tick=256 [Village] El lenador: planto un arbol en 36,97,-31 (7 semillas)
         blockId=minecraft:oak_sapling invSap=7 holes=0
         phase=PLANTAR controller=SCOUTING ok=true
```
Verified: same entity/objective/target, hole preserved, sapling count conserved
across reload, goal resumed after installation, exactly one planting, target air ->
`minecraft:oak_sapling`, one sapling consumed, hole removed. No duplicate task or
resource creation. A5-03 PASS.

## A5-04 GROWING / Bone Meal — BLOCKED / NOT RUN

Source verification:
- `ForestryTaskPolicy` accepts `ABONAR + GROWING + target`.
- `VillagerLumberjackGoal` has `case ABONAR -> "Yendo a la arboleda"` and
  `case ABONAR -> abonar(level)`, but no `taskController.growing()` call.
- The only controller transitions in production are `assign`, `travelling`,
  `harvesting`, `replanting`, `delivering`, `scouting`, `complete`.
- Therefore controller `GROWING` is unreachable through normal production.
- The live goal would run `Fase.ABONAR` with controller `TRAVELLING` while
  travelling and while applying Bone Meal.

A genuine `ABONAR/GROWING` task cannot be created without modifying production,
injecting NBT (fabrication), or changing the schema. A5-04 is therefore NOT RUN.
No fixture was added and no world was launched.

Minimal future correction (not applied): call `taskController.growing()` at the
real Bone Meal application boundary after arrival, persist there, and reset to
SCOUTING after the target is consumed/grown. This is production behavior and was
not changed in this acceptance task.

## A5-04 lifecycle fix — GROWING transition added (source VERIFIED, runtime PARTIAL)

Added in `abonar()`, after target validation and after obtaining Bone Meal, immediately
before `BoneMealItem.growCrop`:
```java
if (taskController.state() != ForestryTaskController.State.GROWING) {
    taskController.growing();
    guardarTarea();
}
```
`growing()` already existed and preserves the assigned target. Phase remains ABONAR.
`tick()` already clears target and returns to SCOUTING after the work action, so the
controller does not remain in GROWING.

Run 152 real ABONAR evidence:
```text
ABONAR target=49,97,-24 controller=TRAVELLING boneMeal=1
[LENADOR-ABONO] ... SIN_CRECIMIENTO antes=oak_sapling[stage=0]
                despues=oak_sapling[stage=0] api=true consumidas=1 devueltas=0
after ABONAR target=- controller=SCOUTING boneMeal=0
```
Bone Meal operation ran and exited normally, but GROWING was not observed at an A5
tick boundary: transition and synchronous growCrop plus target cleanup occur in
the same server tick. Therefore the transition is source-level VERIFIED but not
runtime-observed/persisted as `Controller:"GROWING"`. A5-04 save/reload is NOT RUN
and needs a controlled boundary/pause before growCrop.

## A5-04 lifecycle: GROWING is now a tick-boundary state (Run 154)

Implementation:
- `abonar()` returns boolean. On first ABONAR arrival it validates target and
  `harinaEnMano()`, calls `taskController.growing()`, saves, sets
  `workTicks=WORK_TICKS-1`, and returns false WITHOUT calling growCrop.
- `tick()` skips target/scouting cleanup when `faenaCompletada=false`, so
  `Phase=ABONAR, Controller=GROWING, Target` survives the tick.
- `canContinueToUse()` does not overwrite GROWING with `travelling()`.
- Next tick ABONAR/GROWING consumes one Bone Meal, calls growCrop once, returns
  true, and the existing cleanup clears target/scouting and saves.

Run 154 evidence:
```text
tick=1114 [GROW-SMOKE] ENTER GROWING target=53,97,-17
          boneMeal=16 before=oak_sapling[stage=0]
          (no ABONO log in that tick)
next tick [LENADOR-ABONO] target=53,97,-17 stage0->stage1 consumidas=1
later     target 53,97,-17 stage1->oak_log consumidas=1
final     boneMeal=0, phase=TALAR controller=HARVESTING
[A5] FIN PASS
```
Verified: GROWING persisted at tick boundary; no growCrop in entry tick; exactly one
operation next tick; both stage growth and log growth observed; Bone Meal accounting
matches (one per attempt); controller exits GROWING normally. A5-04 save/reload is
still NOT RUN.

## A5-04 GROWING save/reload — **FAIL / BLOCKED** (Run 155)

Pre-save (genuine state):
```text
[GROW-SAVE] uuid=b89fcee1-... obj=0 target=53,97,-17
 phase=ABONAR controller=GROWING boneMeal=16
 block=oak_sapling[stage=1]
 NBT Version=1 Target=53,97,-17 Controller="GROWING"
(no LENADOR-ABONO before save)
```

After normal reload, NBT stayed `ABONAR/GROWING/53,97,-17`, boneMeal=16,
block=`oak_sapling[stage=1]` from tick 1 through tick 220. At tick ~233 the
controller changed to `TRAVELLING` with no Bone Meal consumption and no block
change. No `growCrop` ran.

Root cause (source): `VillagerLumberjackGoal.start()` does:
```java
if (target != null) {
    taskController.assign(target.getX(), target.getY(), target.getZ());
}
```
`assign()` sets `TRAVELLING` unconditionally, clobbering the restored `GROWING`.
The task survived NBT restore but not Goal installation, so ABONAR/GROWING is
validated by `cargarTarea` and then overwritten before execution.

First failed invariant: controller state is not preserved through Goal installation.
A5-04 is NOT PASS. Fix requires production change in `start()` (e.g. preserve
GROWING/REPLANTING instead of assign); not applied in this acceptance task.

## A5-04 fix 2 — Restored controller preserved through Goal install (PASS)

`start()` now calls `assign()` only when `!taskController.hasAssignment()`; otherwise
it preserves GROWING/REPLANTING/DELIVERING/RETURNING and target from NBT.

Run 156 GROWING save/reload:
```text
save tick=1139 phase=ABONAR controller=GROWING target=53,97,-17
          boneMeal=16 block=oak_sapling[stage=1]
reload tick=40..80 same GROWING/target/bone/block
tick=89 [LENADOR-ABONO] target=53,97,-17 SIN_CRECIMIENTO
        api=true consumidas=1 devueltas=0
END phase=ABONAR controller=SCOUTING target=- boneMeal=15
```
Verified: GROWING survived install, one Bone Meal consumed, block unchanged for this
attempt, controller exited SCOUTING, no duplicate. A5-04 PASS.

Run 157 A5-03 regression:
```text
save PLANTAR/REPLANTING target=53,97,-24 sap=8 holes=1
reload tick=60 same REPLANTING/target/sap/holes block=air
tick=290 planto 53,97,-24; block=oak_sapling sap=7 holes=0
```
A5-03 remains PASS.

## A5-05 fix 1 — stable DELIVERING lifecycle (source; runtime NOT TESTED)

Corrections:
- `start()`: for `Fase.ENTREGAR` with `TRAVELLING`, set `delivering()` and save;
  restored DELIVERING is preserved by `hasAssignment()`.
- `canContinueToUse()`: exempts `DELIVERING` from the `travelling()` overwrite.
- post-harvest delivery path: `guardarTarea()` immediately after `delivering()`.

Source invariants now:
```text
ENTREGAR + DELIVERING + target -> start preserves, canContinue preserves
new ENTREGAR selection -> assign then delivering, saved
post-harvest path -> delivering + save in same tick
delivery -> entregar() unchanged; cleanup -> SCOUTING + save
```

Runtime attempts:
- Run 158/159: supplied 16 logs; standard delivery was not selected because live
  `descubrirTrabajo` has no ENTREGAR branch. No DELIVERING.
- Run 160: natural harvest mode; worker looped in discovery for 8 min without
  harvesting or delivering. No DELIVERING.
T1 standard delivery is blocked by the live-discovery gap; T2/T3 NOT TESTED.
No NBT schema change. Resource accounting code unchanged.

## A5-05 Fix 2A — live delivery discovery (T3 PASS)

Added a live discovery branch: a worker carrying `>= LLEVAR_TRONCOS` selects
`ENTREGAR` before arboleda/planting/tree work via `iniciarEntrega()`.
`tick()` also sets `delivering()` after post-discovery assignment when
`fase == ENTREGAR`, so the controller is coherent without `start()`.

Run 161 save:
```text
carried=16 warehouse=127 phase=ENTREGAR controller=DELIVERING
target=123,97,21 NBT Version=1
```
Reload tick=42: same target/controller/carried/warehouse.
Resumed:
```text
[LENADOR-DELIVERY] carriedBefore=16 carriedAfter=0 accepted=16
 warehouseBefore=123 warehouseAfter=139 delta=16
```
Exactly one delivery. T3 PASS (save/reload/resume/resource accounting).
T1 canUse-only path NOT TESTED (fixture used live discovery). T2 post-harvest
NOT TESTED. No NBT change; entregar() unchanged.

## A5-05 Fix 2B — delivery entry-path runtime validation (partial)

T1 initial canUse delivery: BLOCKED. The A5 fixture cannot insert 12 logs before the
lumberjack Goal is installed; by the first controllable tick the Goal is already
running, so canUse is not re-invoked. Source path remains: ENTREGAR -> assign ->
start() -> delivering().
T2 natural post-harvest: BLOCKED. Run 160 looped in discovery for 8 minutes with no
harvest; Run 161 later harvested only after the delivery test had completed, so it
is not a clean post-harvest fixture.
T3 active discovery save/reload: PASS (Run 161, recorded previously).

State priority review (source):
- logs <12: live discovery has no below-threshold delivery branch; may continue
  other work. This is a known reachability gap.
- pending REPLANTING / GROWING: target non-null, discovery not called; preserved.
- full logs: discovery now selects ENTREGAR before arboleda/planting/tree work.
- storage partial: entregar keeps remainder, returns false; cleanup scouting;
  retry later, no duplication.
- storage unavailable: guardar returns stack, no loss; delivery cannot complete.
No policy changes made.

## A5-05 Fix 3 — isolated Goal fixtures

T1 canUse (Run 164): PASS.
Fresh FLETCHER spawned, 16 logs inserted before adding the production Goal.
`canUse` selected `ENTREGAR`, `start` established `DELIVERING`, target=127,97,20.
```text
[T1-CANUSE] SELECTED controller=DELIVERING inv=16 warehouse=128
[LENADOR-DELIVERY] carriedBefore=16 carriedAfter=0 accepted=16
 warehouseBefore=127 warehouseAfter=143 delta=16
[T1-CANUSE] END inv=0 warehouse=143 delta=15
```
The log delta is 15 because one warehouse log was consumed concurrently; the
delivery event itself reports +16 and inventory 16->0.

T2 natural harvest (Run 163): PARTIAL.
Fresh FLETCHER + planted oak tree; natural TALAR at tick1194, harvest 5 logs,
post-harvest `ENTREGAR/DELIVERING` at tick1244. Total warehouse +9 across two
delivery events (4 and 5), but the log lines do not include UUIDs and the existing
village lumberjack was also active, so the exactly-one deposit for the fresh worker
is NOT VERIFIED. Natural harvest and DELIVERING transition are VERIFIED.

## A5-05 Fix 4 — isolated post-harvest exactly-once (Run 167) PASS

Fixture: killed other FLETCHERs, kept village systems alive, cleared logs/leaves in
radius 50, planted one 5-log oak, spawned one fresh FLETCHER with production Goal.

```text
uuid=4234f4a2-3526-4200-91c6-f9c3471f3f1e
[T4-ISO] DELIVERING target=128,97,21 controller=DELIVERING C0=5 W0=122
[Village] El lenador: talo 5 tronco(s)
[LENADOR-DELIVERY] carriedBefore=5 carriedAfter=0 accepted=5
 warehouseBefore=122 warehouseAfter=127 delta=5
[T4-ISO] DELIVERED C1=0 A=5 W1=127 delta=5
 phase=ENTREGAR controller=SCOUTING target=null
[T4-ISO] OBSERVE +200 ticks: C200=0, warehouse unchanged 127
[A5] FIN PASS
```
Exactly one attributable delivery, no second deposit. Phase remains ENTREGAR at
cleanup (target null, controller SCOUTING); the next discovery moves to
PLANTAR/TALAR. T2 PASS on exactly-once; phase-label SCOUTING caveat recorded.

## A5-05 Fix 5 — ENTREGAR/SCOUTING/null is intentional idle state

`Fase` is only `{TALAR, PLANTAR, ABONAR, RECOGER_HARINA, ENTREGAR}`; there is no
`SCOUTING` phase. `Phase` is the last work category; `Controller` is the lifecycle.
After delivery `tick()` clears target and sets controller SCOUTING, then saves.
`consistente()` accepts any phase when controller is SCOUTING, provided target is
null, so NBT reload restores this idle combination without error. Next discovery
sets a new phase. No production correction applied; adding a SCOUTING phase would
change schema/semantics and is out of scope.
Run 167 evidence: delivered 5, phase=ENTREGAR controller=SCOUTING target=null;
+200 ticks C=0, warehouse unchanged; then new PLANTAR/TRAVELLING.

## A6-01 — harvested-log overflow fallback

`picarTronco` now keeps the remainder returned by `VillageStorage.guardar` and calls
`villager.spawnAtLocation(sobra)` when both inventory and warehouse reject it.
No NBT/schema/delivery changes.

Run 171 (full inventory + dirt-filled full warehouse):
```text
invFull=true warehouseLogs=0 baseline=0
[OVERFLOW] HARVESTED worldLogs=4 carried=0 warehouse=0
[A5] FIN PASS
```
This proves the fallback materialized overflow items instead of silently discarding
them. Exact harvested==overflow reconciliation is NOT VERIFIED: the fixture worker
harvested unplanned trees (Run 172 worldLogs=12, target tree still 5 logs), so the
full-tree equation could not be isolated. Run 171 observed 4 overflow logs with zero
inventory/storage acceptance.

## A6-01 exact overflow accounting — PASS (Run 173)

Isolated fixture: one FLETCHER, all other villagers removed, logs/leaves/saplings
cleared in ±160 radius, one 5-log oak at 87,97..101,4; inventory filled with dirt;
VillageStorage filled with dirt (0 oak-log capacity).
```text
[OVERFLOW] installed uuid=212bc1cb-... invFull=true warehouseFull=0 tree=87,97,4
[Village] El lenador: talo 5 tronco(s)
[OVERFLOW] FINAL harvested=5 remaining=0 worldLogs=5
 carried=0 warehouseLogs=0
[A5] FIN PASS
```
Exact equation: 5 removed = 0 inventory + 0 storage + 5 world overflow.
Per-ItemEntity UUID/stack attribution was not captured; aggregate count is exact.
Pickup verification NOT TESTED.

## A6-02 — sapling withdrawal accounting (Run 176)

`entregar()` now uses the actual count returned by `VillagePantry.sacar`, inserts
exactly that many, returns inventory remainder to the pantry, and only then falls
back to `spawnAtLocation`. This also fixes the prior over-insertion bug (it used to
insert 16 even when fewer were withdrawn).

Run 176 natural delivery fixture, pantry stocked with exactly one oak sapling:
```text
[SAPLING] installed pantry=1 tick=100
[SAPLING] ACQUIRED inv=1 pantry=0 pantryBefore=1 tick=492
[A5] FIN PASS
```
Accounting: withdrawn1 = inventoryAccepted1 + pantryReturned0 + worldOverflow0.
No duplication. Normal capacity PASS.
Full-inventory / partial-inventory / full-pantry tests are NOT naturally reachable:
after log delivery the worker has a freed log slot, so sapling insertion succeeds.
B/C/D remain BLOCKED; NBT unchanged.

## A6-02 Fix 2 — acquisition boundary verification

Test A remains PASS (Run 176): pantry1 -> inv1, pantry0, no duplication.
Test B (full inventory) attempted Run 177: 16 logs in all slots, pantry sapling16,
warehouse full of dirt. After 300 ticks logs=16, invSap=0, pantry=16, but no
`[LENADOR-DELIVERY]` log occurred, so the acquisition block was never entered.
Inconclusive; NOT a PASS.
Test C (partial sapling capacity) is structurally inapplicable: with `semillasEnMano()==0`,
either an empty slot accepts all 16 or no empty slot accepts 0; partial capacity
would require an existing sapling stack with room, which sets semillasEnMano()>0
and skips withdrawal.
Test D (pantry rejection) is structurally unreachable in normal synchronous
execution: `sacar` frees exactly the slots/count later returned, and
`VillagePantry.guardar` runs on the same server tick with no interleaving; returned
remainder <= withdrawn, so the freed capacity is sufficient. World fallback is
defensive only.

## A6-02 Fix 3 — full inventory boundary PASS (Run 178)

Run 177 was inconclusive because it ended at tick 400, before the worker reached
storage. Run 178 extended to 1600 ticks and observed repeated delivery attempts:
```text
[SAP-BOUND] tick=1200 phase=ENTREGAR controller=DELIVERING target=128,97,16
 logs=16 invSap=0 pantry=16
[LENADOR-DELIVERY] carriedBefore=16 carriedAfter=16 accepted=0
 warehouseBefore=0 warehouseAfter=0 delta=0
[SAP-BOUND] END logs=16 invSap=0 pantry=16
[A5] FIN PASS
```
The full-inventory acquisition boundary is production-reachable. With pantry holding
16 oak saplings, `sacar` removes 16, inventory accepts 0, and the same-tick pantry
return restores 16. Final accounting: withdrawn16 = inventory0 + pantry16 + world0.
The operation is same-tick so intermediate withdrawal is not observable in logs;
this is a runtime-fixture limitation, not a code path gap. World fallback remains
structurally unreachable in the normal synchronous path.

## A6-03 — idle delivery fallback with bounded retry

Implemented Policy B:
- `descubrirTrabajo` end (and no-work `canUse` end) delivers positive carried logs
  via `iniciarEntrega`.
- Idle fallback runs only after tree/planting/growing/seed work fails.
- Threshold (>=12) and post-harvest paths are gated by an absolute transient
  `deliveryCooldownUntil`; no countdown double-decrement.
- Failure keeps inventory remainder, clears target/SCOUTING, sets
  `deliveryCooldownUntil = gameTime + min(200, 40*attempts)`.
- Success clears cooldown and resets attempts.
- No NBT schema change; cooldown is transient (reload resets it).

Run 180 idle R1:
```text
[IDLE] supplied logs=5 tick=161
[IDLE] SELECTED target=128,97,21 controller=DELIVERING carried=5 tick=163
[LENADOR-DELIVERY] carriedBefore=5 carriedAfter=0 accepted=5
 warehouseBefore=123 warehouseAfter=128 delta=5
[IDLE] END carried=0 controller=SCOUTING phase=ENTREGAR tick=499
[A5] FIN PASS
```
Exactly one delivery, 5 accepted, inventory 5->0. Net warehouse delta 0 over the
run because the village consumed 5 logs concurrently; the delivery event itself is +5.
R2/R3 priority source-verified; R4 full-storage/cooldown runtime NOT TESTED; R5/R6
prior evidence; R7 NOT TESTED.

## A6-03 Fix 2 — cooldown audit and R4 attempt

Cooldown source audit: all three initiation sites (`canUse` idle, discovery
threshold, post-harvest) check `enCooldown(level)`; failure/stuck call
`iniciarCooldown` from `gameTime`, success clears it; no independent decrement.
Run 181 full-storage fixture: 5 logs retained, warehouse 0, five target BEGIN
events at ticks 162,490,690,1130,1182 (intervals 328,200,440,52). Targets varied
(128,21 / 107,16 / 53,-17), so not all intervals are delivery-retry spacing; some
are other work targets. No tight loop over 1200 ticks, but exact 40/80/120 spacing
was not measured. R4 remains NOT VERIFIED.
R2/R3 priority interactions NOT TESTED. R1 Run180 PASS. R5/R6 prior evidence.
R7 NOT TESTED.

## A6-03 R4 — bounded retry PASS (Run 182)

Phase/controller logging separated real delivery attempts from other targets.
Real failures/retries:
```text
attempt1 begin162 fail484 (5 logs, accepted0)
attempt2 begin486 = RECOGER_HARINA (not delivery)
attempt3 begin664 delivery, fail782
attempt4 begin862 delivery, fail910
attempt5 begin1030 delivery, fail1078
```
Configured cooldowns after failures: 40 (484->524), 80 (782->862), 120
(910->1030). No delivery attempt began before its cooldown. Other work/travel
explains 484->664. Logs retained 5 throughout; warehouse accepted 0.
R4 PASS. R2/R3/R5-cooldown/R6-cooldown/R7 remain NOT TESTED.

## A6-03 Fix 4 — priority defect and remaining runtime

CONCRETE DEFECT FOUND: `canUse()` had `(tronco == null && troncos > 0)` delivery, which
preempted a valid ABONAR task. Minimal fix: removed that small-cargo preemption;
threshold >=12 and post-harvest paths unchanged; idle fallback at end of discovery
handles small cargo after useful work.

Run 184/185 R3 growing priority: ABONAR selected first at tick101, logs5 remained,
bone8->3, then useful harvest/delivery; no delivery before ABONAR.
Run 186 R1 idle delivery retest after fix: 5 logs delivered once, carried0.
Run 182 R4 cooldown: 40/80/120 respected.
R2 planting priority: BLOCKED — fixture harvest replanted its hole, so no valid
planting task survived; no PASS claimed.
R5/R6/R7 not tested.

## A6-03 Fix 5 — final runtime attempt

R1 retest after priority fix: PASS (Run186, 5 logs delivered once).
R3 growing priority: PASS (Run185, ABONAR first, bone8->3, later delivery).
R4 cooldown: PASS (Run182, 40/80/120; logs retained).
R2 planting priority: BLOCKED. Run187/188 controlled tree never became the worker's
target; village arboleda regenerated after the fixture clear, worker harvested an
arboleda tree and replanted it, so no pending hole survived to inject a sapling.
No PLANTAR selection observed; no PASS claimed.
R5/R6/R7 not tested. R4 160/200 cap not tested.

## A6-03 Fix 6 — closure attempt

R2 planting priority remains BLOCKED. Run 189 delayed fixture setup until tick500
(after arboleda generation), cleared the area, placed a dirt-based 5-log tree at
87,97,4 and spawned the worker. The worker ignored that tree and repeatedly logged
`planton=53,97,-17` without Bone Meal; no harvest, no pending hole, no PLANTAR.
Runs 187/188 failed similarly due arboleda regeneration/alternate targets.
R5 threshold-with-cooldown, R6 post-harvest-with-cooldown and R7 save/reload remain
NOT TESTED. R4 160/200 cap NOT VERIFIED.
Accepted evidence unchanged: R1 Run186 PASS, R3 Run185 PASS, R4 40/80/120 Run182.

## A6-03 Fix 7 — actual delivery diagnostics; R5-B/C PASS

Added property-gated `[DELIVERY-DIAG] BEGIN/SUCCESS/FAILURE` at the actual
`entregar()` call in `tick`, disabled unless `-Ddevilrpg.deliverydiag=true`.
Run 192, 12 logs, full warehouse:
```text
BEGIN tick=4335 carried=12 attempts=0 cooldown=0
FAILURE tick=4335 attempts=1 cooldownUntil=4375
BEGIN tick=4679 carried=12 attempts=1 cooldownUntil=4375
FAILURE tick=4679 attempts=2 cooldownUntil=4759
BEGIN tick=4981 carried=12 attempts=2 cooldownUntil=4759
FAILURE tick=4981 attempts=3 cooldownUntil=5101
```
No delivery began before its deadline; all 12 logs retained. R5-B/C PASS.
R5-A normal threshold prior T1/T3; R5-D recovery not tested.
R6/R7 not tested. R2 remains BLOCKED (arboleda/planton selection).
