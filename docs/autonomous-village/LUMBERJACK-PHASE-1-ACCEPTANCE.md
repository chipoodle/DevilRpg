# Lumberjack Phase 1 — Reload-Safe Task State: Implementation Status

Status: **PHASE 1 ACCEPTED WITHIN THE DEFINED SCOPE (2026-11-12).** A5-01..A5-12 normal-lifecycle runtime PASS; A5-09 malformed variants PASS; Forestry Block 1 A1-A7 COMPLETE; both exceptional resource-refund paths now have deterministic runtime PASS. Abrupt process-crash atomicity remains explicitly OUT OF SCOPE. Do not repeat completed scenarios without a relevant regression. Forestry Block 1 historical Runs 57/58/81 remain accepted; Runs 62/65/27 were re-executed and PASS. The extra `lumber-interrupt` fixture attempt had no result and does not supersede historical Run81 PASS.

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

## Scope boundary / not claimed
- Abrupt process-crash atomicity is explicitly outside Phase 1. The contract
  covers normal save/reload and recoverable operation boundaries, not a forced
  JVM/process termination between arbitrary world and NBT writes.
- The two resource-refund exceptions previously listed here are now covered by
  deterministic runtime fixtures; see **Exceptional resource-refund acceptance**
  below. They are no longer open items.
- Forestry Block 1 A1-A7 is complete; see
  `FORESTRY-BLOCK-1-ACCEPTANCE.md` for Runs 57/58/62/65/27/81 and the full
  acceptance matrix.

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

## Three-status capability table — reconciled 2026-11-12
| Capability | Production | Block 1 historical | Phase 1 reload |
|---|---|---|---|
| Harvest/discovery | implemented | PASS (62/65/57/58) | PASS (A5-02 Run 139) |
| Replanting | implemented | PASS (27/57) | PASS (A5-03 Run 151) |
| Bone Meal growth | implemented | PASS (27) | PASS (A5-04 Run 156); API-false refund not forced |
| Delivery/accounting | implemented | PASS (57/58) | PASS (A5-05 Runs 161/164/167; A5-12 ledger) |
| Two-worker isolation | implemented | PASS (81) | PASS (A5-11 overlap reload) |

Historical Block 1 evidence is recorded in
`FORESTRY-BLOCK-1-ACCEPTANCE.md`: Run 62 terminal obstruction; Run 65
recoverable wall-end route; Run 27 Bone Meal growth; Runs 57/58 inner forest and
delivery; Run 81 two-worker isolation. Runs 62/65/27 were re-executed in the
2026-11-12 continuation and passed. The additional `lumber-interrupt` run was
BLOCKED before world login and does not supersede the accepted historical Run 81.

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

## A6-03 R2 — planting priority PASS (2026-11-10, Run `a6-r2-20261110-193049`)

**Fixture correction (not a production defect).** The historical R2 fixture coupled the
scenario to a harvest-created hole; the grove re-planted saplings and the worker selected
`planton=53,-17` (RECOGER_HARINA), so the controlled tree was never selected. The corrected
fixture controls the grove: all plots are occupied so `VillageGenerator.asegurarArboleda`
cannot re-plant, four plots are freed, and the worker carries 5 logs + 16 saplings. The
production selection path is unchanged.

```text
[A6-R2] SETUP uuid=ba9f9f40-ef0e-4e86-b220-0638ffe5d4a7 logs=5 saplings=16 groveFree=4 warehouse=0 tick=120
[A6-R2] PLANTAR-SELECTED target=49, 97, -38 logs=5 saplings=16 controller=TRAVELLING tick=121
[Village] El lenador: planto un arbol en 49,97,-38 (le quedan 15 semillas)
[A6-R2] PLANTED target=49, 97, -38 saplings=16->15 logs=5 tick=486
[A6-R2] DELIVERED warehouse=5 base=0 delta=5 tick=1148
[A5] FIN PASS detail=R2 plant first then delivery delta=5
```

PASS: **PLANTAR is selected before ENTREGAR**, one sapling is consumed, and the 5-log cargo
is delivered afterwards. No production change.

## A6-03 R5-A — normal threshold PASS (2026-11-10, Run `a6-r5a-20261110-193049`)

Worker `0b7ba9bb-d74a-4193-9297-cef0a9f60e74`, 12 logs, normal warehouse.

```text
[A6-R5A] THRESHOLD-SELECTED target=128, 97, 21 carried=12 controller=DELIVERING tick=121
[LENADOR-DELIVERY] carriedBefore=12 carriedAfter=0 accepted=12 warehouse 0->12
[A6-R5A] DELIVERED warehouse=12 base=0 delta=12 tick=528
```

PASS: the >=12 threshold path delivers exactly once, inventory 12->0, warehouse +12.

## A6-03 R5-D — recovery after cooldown PASS (2026-11-10, Run `a6-r5d-20261110-193049`)

Worker `1a4d8ce9-66a1-420f-8fe2-5159068abf7c`, 12 logs, full warehouse; warehouse freed after
the first failure.

```text
[DELIVERY-DIAG] FAILURE tick=4470 carried=12 attempts=1 cooldownUntil=4510
[A6-R5D] FAILED-CLOSED tick=4470 carried=12 warehouseFreed=true
[DELIVERY-DIAG] BEGIN tick=4558 carried=12 attempts=1 cooldownUntil=4510
[DELIVERY-DIAG] SUCCESS tick=4558 carried=12->0
[A6-R5D] RECOVERED after=88 ticks warehouse=12
```

PASS: no retry before the 40-tick deadline; delivery recovers after the cooldown with all
12 logs.

## A6-03 R4 — 160/200 cooldown cap PASS (2026-11-10, Run `a6-r4cap-20261110-193049`)

Worker `d74cfd05-39f3-4ef0-8c8c-522f93182e95`, 12 logs, permanently full warehouse.

```text
failure tick  cooldownUntil  configured
4470          4510           40
4558          4638           80
4686          4806           120
4854          5014           160
5062          5262           200
[A6-R4] BEGIN n=6 tick=5262 carried=12
[A6-R4] FINAL attempts=6 carried=12 warehouse=0
```

PASS: the two exact caps are observed (160 at attempt 4, 200 at attempt 5), and the next
attempt begins at the deadline, never earlier. This closes the previously unverified
R4 160/200 cap.

## A6-03 R6 — post-harvest with cooldown PASS (2026-11-10, Run `a6-r6-20261110-193049`)

Worker `7ccb1e42-5733-4544-b140-761bbf31620d`, empty inventory, full warehouse, controlled
grove tree at `49,97,-38`.

```text
[A6-R6] HARVESTED tree=49,97,-38 carried=4 tick=4524
[DELIVERY-DIAG] FAILURE tick=5094 carried=4 attempts=1 cooldownUntil=5134
[A6-R6] POST-HARVEST-FAILED carried=4 warehouseFreed=true
[DELIVERY-DIAG] BEGIN tick=5746 carried=4 attempts=1 cooldownUntil=5134
[DELIVERY-DIAG] SUCCESS tick=5746 carried=4->0
[A6-R6] DELIVERED after=652 ticks warehouse=4
```

PASS: the post-harvest delivery fails, the 40-tick cooldown is respected, the worker performs
intermediate planting work, and the later delivery succeeds with all logs.

## A6-03 R7 — save/reload recovery PASS, with timing limitation (2026-11-10)

Save run `a6-r7save-20261110-193049`, worker `6bcdfa6a-b90e-4195-a6d4-9ec68dafd252`:

```text
[DELIVERY-DIAG] FAILURE tick=4530 carried=12 attempts=1 cooldownUntil=4570
[A6-R7] SAVED-AFTER-FAILURE failureTick=4530 carried=12 warehouseFull=true
```

Load run `a6-r7load-20261110-194741` on a copy of that saved world:

```text
[A6-R7] LOAD-START uuid=6bcdfa6a-... carried=12 warehouseFullFreed=true
[DELIVERY-DIAG] FAILURE tick=4649 carried=12 attempts=1 cooldownUntil=4689
[DELIVERY-DIAG] SUCCESS tick=4737 carried=12->0
[A6-R7] LOAD-DELIVERED delayTicks=87 warehouse=12
```

PASS for **delivery recovery across reload**: the restored worker keeps its 12 logs, retries,
and delivers exactly 12 with no loss or duplication after the warehouse is freed. Limitation:
the saved world's clock had already passed the old deadline (4570) when the load started, so
"the transient cooldown resets on reload" is not distinguished from normal expiry; timing of
the reset remains unproven.

## Block 4 — D4/D5 runtime audit (2026-11-10)

### D4 — two lumberjacks, one tree (CONFIRMED)

Final run `a6-d4b-20261110-195518`; workers `02a0f5b6-b386-48b6-b2e4-93e52e6db65a` (A) and
`ca87ea53-e0c6-4be8-891d-acdc6f383a5c` (B); tree `98,98,0`; ENFORCE mode.

```text
[A6-D4] BOTH-TARGET tree=98,98,0 taskA=49935721-... taskB=79238237-... tick=127
[AGENT-AUTH] ACQUIRE A task=49935721... at 4149; B task=79238237... at 4150
[A6-D4] CONTENTION-END treeGone=true carriedA=5 carriedB=0 targetB=98,98,0 tick=256
[LENADOR-DELIVERY] A delivered 5; warehouse 0->5
[A6-D4] RECOVERY B-target=null B-active=false warehouse=5 tick=561
```

Both workers selected the **same tree** with distinct task IDs; each acquired its own
per-agent lease (no cross-worker claim exists). A harvested and delivered 5 logs; B, still
targeting the now-missing tree, later recovered (target cleared, then RECOGER_HARINA/ABONAR
work) and ended inactive. **D4 is confirmed as a missing cross-worker target claim.** No
production change was made.

### D5 — equal-priority goal audit (CONFIRMED)

Run `a6-d5b-20261110-200117`; A `172e7985-f648-4b78-8eeb-fdeb2a966b6b` and B
`b81dbf61-979b-4678-929c-2aca9bfd5827`, both with a test-only competing MOVE/LOOK goal at
priority 0.

```text
[A6-D5] SETUP A=(lumberjack then competing) B=(competing then lumberjack)
[A6-D5] COMPETING-START tag=B-first uuid=b81dbf61... tick=120
[A6-D5] AUDIT tick=160 A-running=VillagerLumberjackGoal B-running=competing A-nav=true B-nav=true
```

Equal-priority goals are **not** preempted by priority: the first-added goal that owns the
MOVE/LOOK flags runs, and the second is blocked. D5 is confirmed as an insertion/flag
arbitration rule, not a priority policy; no explicit profession-priority policy exists yet.
No production change was made.

## D4 post-fix — ephemeral cross-worker tree claim (2026-11-10)

**Status: COMPLETED / ACCEPTED.** D4-A–E PASS; the pre-fix defect evidence above is preserved.

The pre-fix D4 evidence above is preserved: two workers selected `98,98,0` with different
task IDs and independent per-agent leases; A harvested/delivered, B kept the missing target.
**The per-agent navigation lease cannot exclude another worker**: it is scoped to one
`VillagerAgent`, has its own TTL and represents navigation ownership, not work-target
ownership. A cross-worker claim therefore belongs at the Goal's TALAR target-acceptance
boundary, keyed by the resource itself.

### Scope and invariants

- Claim scope: `(dimension id, packed tree BlockPos)` — world-scoped, not per-village or
  per-agent. A tree position is globally unique; the registry is a single server-side
  in-memory map. No NBT and no persistence: claims are task-transient and a reloaded worker
  re-discovers/re-claims.
- At most one live claim per tree; acquisition is `synchronized` and atomic on the server
  thread.
- A worker keeps its own claim across normal Goal/planner ticks while it still targets the
  same tree; the Goal renews it at the start of each tick.
- A failed acquisition does not expose the target, does not authorize navigation and does not
  blacklist the tree; discovery continues.
- Release validates both owner UUID and task ID, so a stale owner cannot erase a successor.
- Release sites: work completion, `stop()`, `abandonarObjetivoInvalido()` (stale/invalid
  target), and `VillagerAgentRegistry` death/leave (`RELEASE-ALL`). Server stop clears all.
- Unrenewed claims expire after `ForestryTreeClaims.CLAIM_TTL_TICKS = 200` ticks; the owner
  logs `LOST` on the next renew and abandons normally. This bounds disappeared-owner
  recovery without arbitrary delays.
- Target identity is the existing `UUID(villager + ":" + objectiveIndex + ":" + tree.asLong())`
  task ID; the claim is **not** the navigation lease.

### Implementation

- New `agent/ForestryTreeClaims.java` (pure Java, synchronized): `acquire`, `renew`,
  `release` (owner+task validated), `releaseAll`, `isAvailable`, `ownerOf`, `clearAll`.
- `VillagerLumberjackGoal`:
  - `aceptarTalar(level, tree)` acquires the claim **before** assigning `target`; denial logs
    `[TREE-CLAIM] event=TARGET-DENIED` and the candidate is not exposed.
  - `canUse()` tree/rest branches and `descubrirTrabajo()` call `aceptarTalar`.
  - `baseDeArbol()` and both hanging-log searches skip trees owned by another worker, so
    discovery naturally chooses another target or idles.
  - `renovarTalarClaim()` at tick start; a lost claim triggers normal invalidation.
  - `releaseTalarClaim()` on completion, `stop()`, abandonment and delivery start.
- `VillagerAgentRegistry`: death/leave release all claims; server stop clears the registry.
- No change to ENTREGAR, navigation lease semantics, wall gates, A8-F03 stale-target guards,
  other professions or NBT.

### Runtime results (fresh A5WALL copies, ENFORCE)

| Scenario | Run | Evidence | Result |
|---|---|---|---|
| D4-A basic contention | `claim-d4a-20261110-201309` | A `40bc5e1c…` claimed `98,98,0` at 4147; B `c1254e6a…` target `null` at 126; A released after harvest and delivered 5; B moved to other work | **PASS** |
| D4-B release/successor | `claim-d4b-20261110-201309` | A `ff86a6b4…` claimed then released at tick 4309 via stuck abandonment; B `726494fe…` claimed at 4316, harvested and delivered 5; A's removed goal never touched B's claim | **PASS** |
| D4-C owner disappearance | `claim-d4c-20261110-201309` | A `84b30361…` claimed; death triggered `RELEASE-ALL reason=death` at 128; B `bcde1078…` claimed at 4156 and delivered 5 | **PASS** |
| D4-D multiple trees | `claim-d4d-20261110-201309` | B `0028aa91…` claimed `98,98,0` and A `a87f2b8a…` claimed `62,98,0` concurrently; both released; warehouse 0→10 | **PASS** |
| D4-E original regression | `claim-d4e-20261110-201309` | B `d75607bb…` claimed `98,98,0` at 4147; A `bb6ac88e…` target `null`; B delivered 5; no duplicate harvest (loser later did ABONAR on the replanted sapling) | **PASS** |

The duplicate-target defect is gone: only the claim owner receives the tree as work; the
other worker does not navigate to it.

### Tests

`ForestryTreeClaimsTest` (registered in `policyTest`) covers atomic acquisition, same-tree
denial, different-tree concurrency, dimension separation, owner/task-validated release, stale
release rejection, TTL expiry and `releaseAll`.

### Remaining limitations

- Claims are ephemeral and intentionally reset on reload; a reloaded worker re-claims.
- TTL recovery is time-based (200 ticks) when no death/leave event fires.
- No priority/fairness policy yet; D5 remains open.
- D4-E's loser later did ABONAR on the replanted sapling, not a duplicate harvest.

## D5 post-fix — explicit Goal priority and flag policy (2026-11-10)

**Status: COMPLETED / ACCEPTED.** D5-A–E PASS; the pre-fix audit above is preserved.

The pre-fix audit above is preserved. The audit found the real registrations were not all
priority 0, and two registrations contradicted the intended policy:

- **Lumberjack was registered at 0** (the class javadoc said 4): it could outrank flee/guard.
- **Tavern was registered at -1** (comments said 6 or "below professions"): it outranked
  even flee (0).

### Actual GoalSelector semantics (Minecraft 1.21.1 / NeoForge 21.1.249)

`GoalSelector` keeps `availableGoals` in insertion order (`ObjectLinkedOpenHashSet`); it does
**not** sort by priority. Each tick it:

1. stops running goals whose `canContinueToUse()` is false;
2. for each non-running goal in registration order, starts it only when every flag it uses is
   free or the current flag owner `canBeReplacedBy` it;
3. `WrappedGoal.canBeReplacedBy(new)` is `owner.isInterruptable() && new.priority < owner.priority`
   (default `Goal.isInterruptable()` is true);
4. equal priority (`new.priority == owner.priority`) can never replace; the first registered
   eligible goal keeps the flag.

So: lower number = higher priority; a lower number **can preempt** a running higher number;
equal priority is deterministic by insertion order; MOVE and LOOK are independent locks, so a
LOOK-only goal coexists with a MOVE-only goal, but two MOVE goals cannot both run.

### Inventory (post-fix)

| Profession / role | Goal | Priority | Flags | Registration / ordering | Intended conflicts |
|---|---|---:|---|---|---|
| Any non-guard adult | `VillagerFleeGoal` | **0 EMERGENCY** | MOVE | `asegurarElHuir`, once | Must beat all work/needs |
| Any adult | `VillagerTavernGoal` | **1 CRITICAL** | MOVE, LOOK | `asegurarGoalDeLaTaberna`, once | Beats work; yields to flee |
| Any adult | `VillagerGateGoal` | 2 UTILITY | none | `asegurarGoalDePortones`, once | Coexists (no flags) |
| Any adult | `VillagerDoorGoal` | 2 UTILITY | none | `asegurarGoalDePuertas`, once | Coexists (no flags) |
| Flag-gated legacy | `VillageDispatcherGoal` | 2 UTILITY | none | only when `DESPACHADOR_ACTIVO` | Coexists; no work block |
| Guard | `VillagerGuardGoal` | **3 SECURITY** | MOVE, LOOK | `asegurarGoalDeGuardia` | Beats professions; yields to flee/tavern |
| FLETCHER | `VillagerLumberjackGoal` | **4 PROFESSION** | MOVE, LOOK | `asegurarGoalDeLenador` | Equal with other work; no cross-worker claim conflict |
| FARMER | `VillagerFarmGoal` | 4 PROFESSION | MOVE, LOOK | `asegurarGoalDeGranjero` | Equal work |
| SHEPHERD | `VillagerAnimalFarmGoal` | 4 PROFESSION | MOVE, LOOK | `asegurarGoalDeGanadero` | Equal work |
| BUTCHER | `VillagerCookGoal` | 4 PROFESSION | MOVE, LOOK | `asegurarGoalDeCocinero` | Equal work |
| WEAPONSMITH / TOOLSMITH | `VillagerSmithGoal` | 4 PROFESSION | MOVE, LOOK | `asegurarGoalDeHerrero` | Equal work |
| CLERIC | `VillagerClericGoal` | 4 PROFESSION | MOVE, LOOK | constant `PRIORIDAD` | Equal work |
| FISHERMAN | `VillagerFisherGoal` | 4 PROFESSION | MOVE, LOOK | constant `PRIORIDAD` | Equal work |
| Miner role | `VillagerMinerGoal` | 4 PROFESSION | MOVE, LOOK | constant `PRIORIDAD` | Equal work |
| Flag-gated legacy | `VillageNavigationGoal` | 4 PROFESSION | MOVE | only when `NAVEGACION_ACTIVA` | Equal with work; documented legacy caveat |
| NITWIT / builder | `VillagerCollectGoal` | 5 SUPPORT | MOVE, LOOK | `asegurarGoalDeRecolector` | Below work |
| Worker with job | `VillagerRepairGoal` | 5 SUPPORT | MOVE, LOOK | `asegurarGoalDeObrero(..., SUPPORT)` | Below own profession |
| Villager without fixed job | `VillagerRepairGoal` | 3 SECURITY | MOVE, LOOK | `asegurarGoalDeObrero(..., SECURITY)` | Builder primary; same tier as guard |
| Profession with loose materials | `VillagerPickupGoal` | **6 OPPORTUNISTIC** | MOVE, LOOK | constant `PRIORIDAD` | Below all work |

Registration is idempotent per goal class; utilities (gate/door) are added to all adults,
profession goals only to their profession, and guard/pickup are removed/added by role. Equal
tiers are safe because a villager normally has one profession; utilities carry no flags.

### Implementation

- New `VillageGoalPriority` (EMERGENCY 0, CRITICAL 1, UTILITY 2, SECURITY 3, PROFESSION 4,
  SUPPORT 5, OPPORTUNISTIC 6).
- `VillageManager` uses the constants for every villager registration; lumberjack 0 → 4,
  tavern -1 → 1, guard 3, gate/door/dispatcher 2, repair 5/3, collect 5, pickup 6.
- `VillagerClericGoal`, `VillagerFisherGoal`, `VillagerMinerGoal` use `PROFESSION`;
  `VillagerPickupGoal` uses `OPPORTUNISTIC`; `VillagerTavernGoal` uses `CRITICAL`.
- No D4 claim, A8-F03, ENTREGAR, NBT or OFF/ENFORCE changes.

### Runtime D5-A–E (fresh A5WALL copies)

| Scenario | Run | Evidence | Result |
|---|---|---|---|
| D5-A different priorities | `policy-d5a8-20261110-203455` | UUID `6cf8158a…`; priority 5 running first; adding priority 4 at 121 → state `5:idle \| 4:RUN` at 136 | **PASS** |
| D5-B equal priority/MOVE | `policy-d5b-20261110-203455` | UUID `8107cb43…`; two priority-4 lumberjacks; `runningCount=1`, first RUN, second idle at 145 | **PASS** |
| D5-C MOVE vs LOOK | `policy-d5c-20261110-203455` | UUID `a6c57c47…`; `ProbeMoveGoal` (MOVE) and `ProbeLookGoal` (LOOK) both RUN at 140 | **PASS** |
| D5-D dynamic eligibility | `policy-d5d-20261110-203455` | UUID `2362bc61…`; first STOP 136, second START 136; `running=1` at 150 | **PASS** |
| D5-E profession integration | `policy-d5e3-20261110-203455` | UUID `8107150d…`; lumberjack delivered 5; installed `0:flee \| 1:tavern \| 4:lumberjack \| 2:gate \| 2:door \| 5:repair \| 6:pickup`; no-AI zombie trigger → `0:flee RUN`, lumberjack idle at 507 | **PASS** |

D5-A's first fixture attempts failed because the test villager had XP 0 and vanilla
`ResetProfession` erased the FLETCHER profession before the second goal could run; the fixture
now gives a real fletching-table POI and XP 1 (the final PASS above). D5-D used eligibility
toggles, not manual Goal stops.

**D5 status: PASS for the required A–E scenarios.** Remaining limitations: legacy
`VillageNavigationGoal` is at PROFESSION and could equal a profession goal when its flag is
enabled; dispatcher is flag-gated; equal-priority order is insertion order by design.

## A6-03 status (2026-11-10)

R1 (Run186), R2 (above), R3 (Run185), R4 40/80/120 (Run182) and 160/200 (above), R5-A/B/C/D
(Run192 + above), R6 and R7 (above) are all covered. **A6-03 runtime acceptance is CLOSED**,
with the R7 transient-reset timing limitation and the D4/D5 Block 4 findings recorded above.


## Reconciliación de estado y siguiente prueba — 2026-11-12

La matriz inicial de la primera página es histórica. La evidencia posterior del mismo
documento cierra estos casos, que NO deben repetirse por rutina:

| Caso | Estado vigente | Evidencia |
|---|---|---|
| A5-01 TRAVELLING | PASS | Run 134, mismo UUID/objetivo/fase/controlador tras reload |
| A5-02 HARVESTING | PASS | Run 139, mismo estado y ledger 119 + 5 = 124, sin duplicación |
| A5-03 REPLANTING | PASS | Run 151; Run 157 regresión después del fix GROWING |
| A5-04 GROWING | PASS | Run 156; conserva GROWING tras Goal install, una operación Bone Meal |
| A5-05 DELIVERING | PASS | Run 161 (16 logs) y Run 167 (5 logs exactly-once, +200 ticks sin segundo depósito) |
| A5-10 unknown higher version | PASS | Run 135, Version=99 y FutureMarker preservados |
| A5-09 missing Phase/Controller/Target | PASS | Run 136; task descartada y Goal reinstalado |
| A5-09 invalid Phase enum | PASS | Run 137; task descartada y Goal reinstalado |

A5-06 (target obsoleto antes/después de reload) NO equivale a A8-F03 C1/C2/C3:
aquellos prueban invalidación/guardia de navegación durante runtime, no la recuperación
de una tarea persistida cuyo bloque objetivo cambió mientras el mundo estaba cerrado.
El código de carga restaura un objetivo dentro del radio; la validación de TALAR en
start/tick debe abandonarlo antes de navegar/autorizar. Se necesita una prueba de save,
cambio del bloque objetivo en el mundo guardado/copia, reload y evidencia de
NAV-GUARD/ABANDON + target=null/SCOUTING o selección válida posterior. No inyectar una
fase/controlador imposible ni afirmar que el código fuente sustituye esta prueba.

El arnés A5 actual ya contiene save/load de TRAVELLING, HARVESTING, REPLANTING,
GROWING, DELIVERING, unknown-version y malformed. No contiene escenarios explícitos
dedicados a A5-06 stale-after-reload, A5-07 profesión incorrecta, A5-08 objective/
village mismatch, A5-11 solapamiento post-reload ni A5-12 ledger integral de recursos.
El harness es local/ignorado por Git; no convertirlo en cambio rastreado sin una decisión
explícita sobre su política de versionado.

### A5-06 — siguiente subcaso en ejecución
Diseñar un fixture de dos etapas sobre una copia A5WALL:
1. Guardar una tarea TRAVELLING real con target que sea un árbol LOGS existente.
2. Cerrar normalmente el mundo.
3. En la copia de reload, cambiar ese bloque a aire/otro bloque antes de cargar el Goal.
4. Reabrir; comprobar que el Goal no navega ni autoriza el target viejo, que libera claim/
   lease si aplica y que vuelve a SCOUTING/target nulo o elige un target nuevo válido.
5. Comprobar que no aparecen drops, inventario ni depósito atribuibles al target eliminado.
6. Guardar logs completos y result.json; ejecutar también A8-F03 stale-target regression
   solo si el cambio de producción toca esa protección.

No basta con reemplazar el bloque después de que el Goal ya haya navegado; el cambio debe
existir antes de que se reinstale/ejecute el Goal tras reload. El estado de aceptación
global sigue PARTIAL hasta A5-06/07/08/09 restantes/11/12 y regresiones Block 1.


### Progreso de A5-06 iniciado — 2026-11-12 (runtime aún NO ejecutado)

Se añadieron al A5TestController local/ignorado dos escenarios:
- lumber-stale-save: espera una tarea real TALAR/TRAVELLING cuyo Target siga siendo
  un bloque LOGS; cambia ese bloque a AIR y guarda el mundo con la tarea persistida.
- lumber-stale-load: tras abrir la copia guardada, espera el ciclo del Goal y exige
  Goal de leñador presente, Controller=SCOUTING y ausencia de Target obsoleto.

La alteración del bloque se hace en una tarea real previamente seleccionada por el
Goal, no se inyecta una fase ni un target NBT. Este primer fixture deja el mundo
guardado con entidad apuntando a un bloque que ya no existe; la prueba de reload
verifica la validación al reanudar. La sustitución del bloque ocurre inmediatamente
antes del guardado (no durante un intervalo offline); se registra esta precisión
para no sobredimensionar el alcance de la evidencia.

Validación de compilación forzada después de añadir el fixture:
.\gradlew.bat compileJava --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL (26s; 2 tasks executed).

Todavía NO se ha lanzado lumber-stale-save/load en Minecraft. No existe PASS runtime
de A5-06 todavía. El fixture debe ejecutarse en una copia A5WALL desechable, guardar
result.json y logs de ambas fases, verificar el mismo UUID entre save/reload y
confirmar que no hubo navegación/entrega/drops duplicados por el target eliminado.


### A5-06 intento 1 — BLOCKED por fixture, no es resultado de producto (2026-11-12)

Run: lumber-stale-save-20261112-130000.
Copia: build\res-lumber-stale-20261112-130000\game (clon de la copia A5WALL
stabilization-block1-scan-20261030-134; el guardado original no se usó).
El cliente cargó y A5 PREPARED; el worker persistido fue UUID
b89fcee1-c91a-4b1f-a9c4-66471b616288. Sin embargo, la tarea activa era
RECOGER_HARINA / destino de apoyo, con faltaArboleda=true y planton=53,97,-17;
el almacén/despensa tenían 0 Bone Meal. No se alcanzó TALAR/TRAVELLING y, por tanto,
el fixture no alteró un árbol ni guardó la fase A5-06. No se generó result.json:
el wrapper terminó con result=MISSING/exit 1 tras cerrar únicamente el cliente
desechable (PID 34900). Clasificación: BLOCKED por precondición de fixture; no es
PASS ni FAIL de la recuperación de objetivo obsoleto.

Corrección requerida antes del siguiente intento: preparar una copia base sin tarea
heredada bloqueada en RECOGER_HARINA, o preparar la arboleda/recursos para que el
Goal complete la fase de harina por la ruta real y seleccione un árbol LOGS real.
No asignar manualmente Phase/Controller/Target para forzar el estado. Capturar en
setup el UUID, fase/controlador/target y estado del bloque; si no llega a
TALAR/TRAVELLING, detener y clasificar BLOCKED en vez de esperar 12 minutos.


### A5-06 intento 2/3 — launcher/copia corregidos, Quick Play no abrió el mundo (2026-11-12)

Se detectó un error de aislamiento en el procedimiento: run-a5.ps1 acepta -GameDir,
pero no lo incorpora directamente a la tarea Gradle; el gameDirectory lo define el
init.gradle suministrado. El init.gradle base seguía apuntando a
build/stabilization-block1-scan-20261030-134/game. Por eso la corrida
lumber-stale-save-2 no se acepta como prueba de la copia declarada: los logs mostraron
GAMEDIR en el directorio base. El cliente se cerró; no se produjo result.json.

Se creó una copia independiente:
build/res-lumber-stale3-20261112-135000/game
con su propio init.gradle que establece explícitamente:
D:/Documents/DevilRpg-autonomous/build/res-lumber-stale3-20261112-135000/game
La verificación del log de la corrida siguiente confirmó:
FMLPaths GAMEDIR, MODSDIR y CONFIGDIR apuntando a esa copia. También se confirmó
game/saves/A5WALL/level.dat presente.

Sin embargo, Quick Play no llegó a abrir A5WALL: el log llegó a la carga de recursos
y quedó en el cliente sin entrada de mundo, sin [A5] PREPARED, sin login del jugador
y sin result.json. Primer intento en esa copia produjo error de parseo de options.txt
(NumberFormatException: "key.mouse.middle"); se eliminó options.txt únicamente de
la copia desechable y se reintentó. El segundo intento también terminó en el menú/
sin abrir el mundo. Ambos clientes de prueba se cerraron; no quedó Minecraft de
prueba abierto. Wrapper terminó result=MISSING/exit 1. Clasificación: BLOCKED por
arranque Quick Play; no es PASS ni FAIL de recuperación de target.

No repetir el mismo comando esperando otro resultado. Siguiente corrección del harness
de lanzamiento: hacer que run-a5.ps1 genere/verifique su init.gradle por ejecución
a partir de -GameDir, y reutilizar el mecanismo de arranque directo de mundo ya
validado en los runs A5 previos (no dejar el juego en menú). Antes de la próxima
corrida exigir en client.log: GAMEDIR=<copia>, quickPlaySingleplayer A5WALL,
[A5] PREPARED y login del jugador. Si falta cualquiera, abortar temprano y clasificar
BLOCKED; no esperar el timeout completo. No alterar ni usar run\saves ni otro mundo
de usuario como fallback.


### Traspaso operativo para la siguiente sesión — 2026-11-12 13:00

El usuario confirmó que ya no se trabaja mediante el producto/agente externo Harness.
La siguiente sesión dispone de acceso directo a CHRIS-PC mediante Desktop Commander:
leer/editar/ejecutar directamente en `D:\Documents\DevilRpg-autonomous`; no pedir al
usuario que copie contenido o logs. Esto no elimina por sí mismo los artefactos locales
del proyecto (`A5TestController`, `run-a5.ps1`); son scripts/fixtures del repositorio y
se deben inspeccionar, no tratar como un Harness externo ni asumir que funcionan.

Estado confirmado: rama `feature/autonomous-village`, HEAD `518fee1`. El working tree
contiene cambios preexistentes en producción, pruebas y documentación; preservar todo,
sin reset/clean ni commit/push. `lint_aldea.py --strict` y `git diff --check` pasaron a
las 12:53 del 12-nov. No había cliente A5WALL de prueba abierto. Guardado original intacto.

A5-06 continúa BLOCKED por la ruta de lanzamiento, no por el comportamiento del
leñador. `run-a5.ps1` usa `-Init` para definir el `gameDirectory`; el parámetro
`-GameDir` solo se usa para opciones y no cambia Gradle. Un init propio logró que
FMLPaths apuntara a la copia desechable, pero Quick Play no abrió el mundo: faltaron
`[A5] PREPARED`, login y `result.json`. No repetir el mismo comando.

Próxima sesión: recuperar primero de los logs/historial el mecanismo exacto de apertura
directa de A5WALL que sí funcionó en corridas A5 aceptadas; corregir/verificar el launcher
y agregar preflight para GAMEDIR, PREPARED y login. Abortar temprano si falla cualquiera.
Luego preparar el estado TALAR/TRAVELLING mediante IA real y ejecutar A5-06 save/reload
solo en copia desechable. No fabricar Phase/Controller/Target por NBT, no usar `run/saves`
ni el guardado del jugador. Ver también el traspaso de inicio en `docs/CONTINUAR.md`.


## A5-06 stale target after reload — PASS (2026-11-12)

**Acceptance result: PASS.** Save and reload were performed on separate disposable
copies derived from the accepted A5WALL base. The original player save was not used.

Save stage:
- Run: `lumber-stale-save-final-20261112-134130`.
- Game directory: `build/res-lumber-stale-final-20261112-134130/game`.
- Same worker UUID: `b89fcee1-c91a-4b1f-a9c4-66471b616288`.
- At tick 702 the real Goal had selected `Phase=TALAR`, `Controller=TRAVELLING`,
  target `49,97,-17`, whose block was `minecraft:oak_log`.
- The fixture wrote only the target coordinate marker, saved normally, and finished
  PASS. It did not write Phase/Controller/Target NBT.
- Result: `build/res-lumber-stale-final-20261112-134130/phase-save/result.json`.

Reload stage:
- Run: `lumber-stale-load-refined-20261112-135148`.
- Game directory: `build/res-lumber-stale-reload2-20261112-135148/game`.
- It was cloned from the saved stage. Before opening Minecraft, the copied region chunk
  `r.0.-1.mca` was edited offline: the target log at `49,97,-17` and its support block
  at `49,96,-17` were both changed to air. The region file was backed up as
  `r.0.-1.mca.pre-stale-edit.bak`; the modified palette values were read back and
  verified as air. This prevents the removed tree's former position from being
  reclassified as a valid PLANTAR hole. No entity/task NBT was modified.
- On reload the same UUID was found. At tick 1200:
  `oldBlockAir=true`, `noOldTarget=true`, `currentTarget=-`,
  `currentTargetValid=true`, `navTarget=-`, `navigationAvoidsOld=true`,
  `nearbyLogDrops=0`, `goal=true`, `phase=TALAR`, `controller=SCOUTING`, `ok=true`.
- Result: `build/res-lumber-stale-reload2-20261112-135148/phase-load/result.json`.
  Client and integrated server closed normally; wrapper exit code 0.

**Fixture refinement preserved:** the first reload attempt
(`lumber-stale-load-final-20261112-134727`) replaced only the log with air while
leaving solid grass below. The normal discovery logic correctly recognized that
coordinate as a new planting hole and selected `PLANTAR` at the same position. The
original assertion treated coordinate equality alone as stale and returned FAIL.
That was an ambiguous fixture, not evidence of stale TALAR navigation. The final
fixture removed the support block too; no production code was changed. Do not repeat
the ambiguous variant.

**No production change was required for A5-06.** The saved TALAR task was re-evaluated
against the reloaded world; the invalid target was not retained, no path was created
to it, and no wood was duplicated. This closes A5-06. Next: A5-07 profession mismatch.


## A5-07 profession mismatch after reload — PASS (2026-11-12)

Save run `lumber-profession-save-20261112-135837` (tick 600): worker UUID
`b89fcee1-c91a-4b1f-a9c4-66471b616288` had a real v1 task before the profession change
(`Phase=TALAR`, `Controller=TRAVELLING`, target `87,97,4`). The fixture changed the
profession through `VillagerData` from FLETCHER to FARMER and saved normally. Save result
is PASS in `build/res-lumber-profession-20261112-135837/phase-save/result.json`.

Load run `lumber-profession-load-20261112-140100` (tick 1200): the same UUID restored as
FARMER, with no `DevilRpgForestryTask` compound and no `VillagerLumberjackGoal`; result
PASS. The village manager also logged that the villager left the worker role and returned
to its profession.

**Production correction:** `VillagerLumberjackGoal.clearTaskIfProfessionChanged(Villager)`
removes an installed lumberjack Goal and clears understood v1 task data when the villager
is no longer FLETCHER. Unknown future task versions are preserved. The profession loop in
`VillageManager` invokes this cleanup for non-FLETCHER adult non-guards. CompileJava and
compileTestJava both PASS after this change.

## A5-08 objective mismatch after reload — PASS (2026-11-12)

Save run `lumber-objective-save-20261112-140610` (tick 600): same worker UUID
`b89fcee1-c91a-4b1f-a9c4-66471b616288`, real task target `87,97,4`, actual objective 0;
the negative fixture changed only the persisted task's Objective field to 1 and saved
normally. Save result PASS.

Load run `lumber-objective-load-20261112-140811` (tick 1200): Goal present, task present
with stored Objective 0 matching actual Objective 0, and `ok=true`; result PASS. This
shows the mismatched persisted task was discarded and the installed Goal re-scoped to the
current village objective. No production change was required for A5-08.


## A5 unattended launcher contract — permanent correction (2026-11-12)

The shared runner `tools/a5-closure/run-a5.ps1` now enforces unattended operation for
all future A5 scenarios. This is a local repository runner; do not depend on the external
Harness product or ask the user to operate Minecraft.

- Preflight requires the disposable `<GameDir>/saves/<Level>/level.dat` and an init.gradle
  that points Gradle's `runClient.gameDirectory` at that exact absolute GameDir and passes
  `--quickPlaySingleplayer <Level>`. A mismatch aborts before Minecraft launches.
- The runner creates/updates `options.txt` even when absent:
  `pauseOnLostFocus:false`, `onboardAccessibility:true`,
  `skipMultiplayerWarning:true`, `tutorialStep:none`. Accessibility onboarding is marked
  completed; Quick Play enters the requested world directly.
- Once the Java client window exists, the runner hides only that HWND with Win32
  `ShowWindowAsync(SW_HIDE)`. This is non-activating. The runner must never call
  `SetForegroundWindow`, send ESC/keyboard input, simulate mouse clicks, or otherwise
  steal the user's foreground focus. The integrated server continues ticking with
  `pauseOnLostFocus:false`.
- The runner verifies that the requested world actually logged in (`joined the game`).
  If no login occurs within 300 seconds, it writes a BLOCKED result and aborts; a title
  screen or onboarding prompt is not accepted as a running test.
- On completion, it consumes the scenario result, closes/terminates only the disposable
  test client, verifies zero remaining matching clients, and writes `shutdown.json` with
  `joinedRequestedWorld`, `clientWindowHiddenWithoutActivation`,
  `focusStealActions=0`, result, shutdown method and cleanup status.

**Runtime proof of the new contract:**
- Run: `unattended-runner-smoke-20261112-142210`; scenario:
  `lumber-profession-load`; disposable GameDir:
  `build/res-a5-unattended-smoke-20261112-142210/game`.
- `options.txt` read-back confirmed the four values above. Log confirmed FMLPaths GAMEDIR
  was the disposable GameDir, Quick Play arguments included `A5WALL`, and the player
  logged in at 14:24:02. The test completed at tick 1200 with PASS and the same UUID
  `b89fcee1-c91a-4b1f-a9c4-66471b616288`.
- During the run the Minecraft HWND was hidden (MainWindowHandle=0); the foreground
  application remained WhatsApp, not Minecraft. No `Saving and pausing game` marker was
  emitted. `shutdown.json`: `joinedRequestedWorld=true`,
  `clientWindowHiddenWithoutActivation=true`, `focusStealActions=0`,
  `remainingTestClients=0`, `clean=true`, launcher exit 0. Cleanup required the runner's
  forced test-client termination after the result; no user action was needed.
- Runner self-test: PASS (root/nested result discovery; malformed/missing results rejected).

This contract supersedes the earlier manual focus/ESC workaround and the old
`onboardAccessibility:false` setting. Never reintroduce focus-stealing helpers.


### Follow-up: exact client-process ownership fixed and cleanup re-proven (2026-11-12 14:30)

The first smoke run `unattended-runner-smoke-20261112-142210` exposed a cleanup-accounting
bug in the runner: its broad `quickPlaySingleplayer` matcher could select a Gradle-side
Java process rather than the actual Minecraft client. Although its shutdown report said
zero remaining, a separate `net.neoforged.devlaunch.Main` child remained; it was identified
as PID 40892 and manually terminated. Therefore the first run proves hidden-window/no-focus
operation and scenario PASS, but its cleanup assertion is superseded and must not be cited
as clean evidence.

Correction in `run-a5.ps1`: all hide/close/kill/remaining-process queries now use the exact
client identity `net.neoforged.devlaunch.Main` plus the requested
`--quickPlaySingleplayer <Level>` arguments. Gradle daemon/launcher JVMs are not mistaken
for the game client.

**Second runtime verification:**
- Run: `unattended-runner-smoke2-20261112-142720`; scenario:
  `lumber-profession-load`; result PASS at tick 1200, same UUID
  `b89fcee1-c91a-4b1f-a9c4-66471b616288`.
- The client logged into A5WALL, ran with hidden HWND and Chrome as foreground application;
  no `Saving and pausing game` marker and no focus-steal actions.
- Correctly identified client PID 676; shutdown method `forced-test-client-kill`;
  `remainingTestClients=0`, `clean=true`, launcher exit 0. Independent post-run process query
  also returned `REMAINING_TEST_CLIENTS=0`.
- Runner self-test and regex matcher self-test both PASS. This second run supersedes the
  first run for full unattended lifecycle/cleanup acceptance.


## A5-09 malformed controller and out-of-range target — PASS (2026-11-12)

**Invalid controller enum:**
- Save run: `lumber-controller-save-20261112-143500`; load run:
  `lumber-controller-load-20261112-143500`.
- Disposable GameDir: `build/res-lumber-controller-20261112-143500/game`.
- Save stage changed only the real task's Controller string to
  `INVALID_CONTROLLER_STATE`, saved the world, and returned PASS at tick 600.
- Reload stage restored the same UUID
  `b89fcee1-c91a-4b1f-a9c4-66471b616288`. At tick 600:
  `goal=true taskSafe=true ok=true`; the invalid enum was not retained and the
  lumberjack Goal remained installed in a safe fallback state. Result PASS.
- Both stage shutdown reports: joined requested world, hidden window, zero focus actions,
  zero remaining test clients, clean=true.

**Out-of-range target (invalid target position):**
- Save run: `lumber-range-save-20261112-143700`; load run:
  `lumber-range-load-20261112-143700`.
- Disposable GameDir: `build/res-lumber-range-20261112-143700/game`.
- Save stage changed the real task Target to `1080,97,1000` (1000 blocks from the
  village center), saved, and returned PASS at tick 600.
- Reload stage restored the same UUID. At tick 600:
  `goal=true farTargetRetained=false taskPresent=true ok=true`; the invalid target
  was discarded, the Goal was installed, and the task compound remained available for
  safe continuation. Result PASS.
- Both stage shutdown reports: joined requested world, hidden window, zero focus actions,
  zero remaining test clients, clean=true.

These two cases close the invalid-controller and out-of-range-target variants of A5-09.
Still open: invalid planner bounds, malformed hole position, invalid sapling registry id,
and valid task reassignment after malformed rejection. No production code change was
required for these two negative fixtures.


## A5-09 remaining malformed payload variants — PASS (2026-11-12)

### Invalid planner bounds + invalid hole position + invalid sapling registry ID

- Save: `lumber-invalid-payload-save-20261112-154000`; reload:
  `lumber-invalid-payload-load-20261112-154000`.
- Disposable GameDir: `build/res-lumber-invalid-payload-20261112-154000/game`.
- Same worker UUID: `b89fcee1-c91a-4b1f-a9c4-66471b616288`.
- Save stage persisted real task NBT with PlannerInside Ring/Cursor at
  `Integer.MAX_VALUE`, PlannerOutside Cursor=-1, one valid oak hole, one hole with
  invalid registry ID `devilrpg:missing_sapling`, and one hole at
  `1080,97,1000` outside the forestry domain. Save result PASS at tick 600.
- On reload, production logged both invalid planners reset. The malformed hole position
  and unknown sapling ID were dropped; exactly one valid oak hole remained. Both planner
  origins/bounds were sanitized and the lumberjack Goal remained installed:
  `oneValidHole=true insideSafe=true outsideSafe=true ok=true`. Reload PASS at tick 600.
- Save and reload shutdown reports both: joined requested world, window hidden without
  activation, `focusStealActions=0`, `remainingTestClients=0`, `clean=true`.

### Valid reassignment after malformed controller rejection

- Save: `lumber-reassign-save-20261112-154000`; reload:
  `lumber-reassign-load-20261112-154000`.
- Disposable GameDir: `build/res-lumber-reassign-20261112-154000/game`.
- Same worker UUID: `b89fcee1-c91a-4b1f-a9c4-66471b616288`.
- Save stage wrote `Controller=INVALID_CONTROLLER_STATE` to a real task and saved
  normally. Reload discarded the malformed controller; the same worker received a new
  valid task, with Goal installed, `Phase=ENTREGAR`, `Controller=DELIVERING`, target
  `127,97,20`, version 1. Reload PASS at tick 201.
- Both stage shutdown reports: joined requested world, hidden window without activation,
  `focusStealActions=0`, `remainingTestClients=0`, `clean=true`.

**A5-09 is now fully PASS** for the required negative cases and recovery/reassignment.
No production code changes were needed for these variants; runtime evidence confirms the
existing validation/sanitization behavior. Next: A5-11 overlap after reload, A5-12 resource
accounting, then Block 1 regression decision/runs.


## A5-11 two-worker overlap after reload — PASS (2026-11-12)

**Production defect found and corrected:** `ForestryTreeClaims` is intentionally ephemeral,
so all claims disappear when the integrated server stops. Before this correction,
`VillagerLumberjackGoal.cargarTarea()` restored a persisted TALAR target but did not
reacquire its claim. Two workers with the same saved tree target could therefore resume
without an owner reservation.

**Production change:** during v1 task restoration, a TALAR target is revalidated with
`TalarStepPlanner.isValidTalarTarget` and reacquired through the existing
`ForestryTreeClaims`. A contested/invalid target is cleared to SCOUTING and persisted
immediately. Added diagnostics: `RESTORE-ACQUIRED` and `RESTORE-DENIED`. No persistent
claim NBT or new global registry was introduced; the existing transient claim map remains
the authority.

**Runtime fixture:**
- Save: `lumber-overlap-save-20261112-160000`; reload:
  `lumber-overlap-load-20261112-160000`.
- Disposable GameDir: `build/res-lumber-overlap-20261112-160000/game`.
- Two distinct FLETCHER worker UUIDs persisted with the same valid tree target
  `98,98,0`: A `130d96cd-bf73-47d5-9013-18d759cf56cf`; B
  `18a41d25-324c-45fb-8020-8c370ee0f375`.
- Reload emitted `RESTORE-ACQUIRED` for A and `RESTORE-DENIED` for B. At tick 80:
  A retained target `98,98,0`; B target was null and its task controller was SCOUTING;
  claim owner matched A's UUID/task; tree remained LOGS; `ok=true`. Result PASS.
- Both save/reload runner shutdown reports: `joinedRequestedWorld=true`, hidden window
  without activation, `focusStealActions=0`, `remainingTestClients=0`, `clean=true`.
- `compileJava compileTestJava policyTest` PASS; focused
  `FORESTRY_TREE_CLAIMS_TEST: OK` and `FORESTRY_TASK_POLICY_TEST: OK`.

This closes A5-11 and establishes that post-reload overlapping work is serialized by the
existing transient claim system before navigation/harvest can resume. Next: A5-12 resource
accounting, then Block 1 regressions/acceptance decision.


## A5-12 resource accounting — PASS for normal lifecycle operations (2026-11-12)

The acceptance is a composite of the real save/reload resource cases A5-02..A5-05,
the dedicated A5 delivery conservation fixture, and the complete A8-01 per-actor
wood ledger. These are independent runs; quantities are not summed across runs.

### Wood — harvest, delivery, shared-warehouse consumers

- **A5-02 Run 139:** worker inventory 0 logs before harvest; warehouse 119. The
  worker harvested 5 actual logs, delivered all 5, and ended with 0 carried / 124
  warehouse logs. Conservation: `119 + 5 = 124`; no duplicate. Leaves yielded 3
  saplings (0 -> 3); Bone Meal stayed 0.
- **A5-05 Run 167:** isolated worker carried 5, warehouse 122; after delivery carried
  0 and warehouse 127 (`+5`). An additional 200-tick observation kept warehouse at
  127: no second deposit.
- **A5-12 dedicated save/reload:** final repeat run
  `lumber-resource-deliver-save2-20261112-162000` / `lumber-resource-deliver-load2-20261112-162000`.
  Disposable GameDir: `build/res-lumber-resource-delivery2-20261112-162000/game`.
  Same worker UUID `b89fcee1-c91a-4b1f-a9c4-66471b616288`; save stage persisted
  `Phase=ENTREGAR`, `Controller=DELIVERING`, 16 carried logs, warehouse 131.
  Reload delivered exactly 16: carried `16 -> 0`, warehouse `131 -> 147`, accepted
  `16`, warehouse delta `16`, `balanced=true`, `[A5] FIN PASS` at tick 881.
  The earlier repeat `...161500` produced the same balanced result.
- **A8-01-G mixed actor ledger:** initial warehouse 64 oak logs; lumberjack harvested
  and deposited 10; smith withdrew 2 (1 remained in inventory, 1 burned); cook
  withdrew/burned 4. Final warehouse 68. Each actor delta 0, warehouse delta 0,
  global delta 0; 30 events, `complete=true`, `truncated=false`,
  `unattributed=false`, `[A5] FIN PASS`.

### Saplings — acquisition and planting

- **A5-02 Run 139:** 3 saplings gained from actual leaf drops; none consumed in that
  harvest segment (0 -> 3).
- **A5-03 Run 151:** same villager/target across normal save/reload. Inventory
  `8 -> 7`, one air hole became `minecraft:oak_sapling`, hole count `1 -> 0`.
  Exactly one sapling consumed for exactly one successful placement.
- **A6-02 Run 176:** one oak sapling withdrawn from pantry and inserted into worker
  inventory: pantry `1 -> 0`, worker `0 -> 1`; no duplicate.

### Bone Meal — withdrawal/consumption

- **A5-04 Run 156:** restored `GROWING` task survived Goal installation. Bone Meal
  inventory `16 -> 15`; `BoneMealItem.growCrop` reported `api=true`, consumed `1`,
  returned `0`; controller exited to SCOUTING and did not repeat the operation.
  The sapling remained stage 1 during this particular attempt, so the item was
  consumed by the vanilla API without a growth transition; accounting still balances.

### Acceptance boundary and remaining limitations

**A5-12 PASS** for the exercised normal save/reload lifecycle: actual wood creation,
worker inventory, delivery, warehouse consumption, sapling acquisition/planting and
Bone Meal API consumption reconcile without duplication or unexplained loss. Wood has
a complete event ledger; sapling and Bone Meal cases use before/after inventory,
warehouse and block-state snapshots rather than the log-only `ForestryResourceTrace`.

Not claimed: abrupt process-crash atomicity (explicitly out of Phase 1 scope); a forced
runtime case where sapling placement is rejected after item withdrawal; or a forced
`growCrop` API-false Bone Meal refund. Those exceptional branches remain separately
unverified and must not be inferred from the normal-path PASS.

Both stages of the A5-12 save/reload run report `joinedRequestedWorld=true`,
`clientWindowHiddenWithoutActivation=true`, `focusStealActions=0`,
`remainingTestClients=0`, `clean=true`. The second run's load client PID was 40208;
no Minecraft test process remained after shutdown.

**Next acceptance work:** Block 1 regression decision/runs 62/65/27/57/58/81. A5-12
normal lifecycle resource accounting is no longer pending.

## Block 1 — regresiones históricas reejecutadas (2026-11-12)

Las siguientes pruebas se ejecutaron en copias desechables de `A5WALL` con
`tools/a5-closure/run-a5.ps1`. No se modificó el guardado original.

| Regresión | Run | Resultado | Evidencia |
|---|---|---|---|
| Run 62 — obstrucción terminal | `block1-regression-stuck5-20261112-184000` / `lumber-stuck` | **PASS** | `[A5-OBSTACLE]` instaló el muro alrededor del árbol `53,97,-24`; `[A5-STUCK]` registró `failedPoint tick=803`; limpió la tarea bloqueada y asignó nuevo árbol `45,97,-24` en tick 823. XP 5→13, 6 troncos retirados, `wall=true terminal=true nextWork=true`. |
| Run 65 — ruta recuperable por extremo del muro | `block1-regression-recover-20261112-185000` / `lumber-recover` | **PASS** | Árbol `53,97,-17` amurallado; `[A5-RECOVERED]` confirmó llegada al extremo en tick 618, posición `53,97,-13`. XP 6→18, 10 troncos retirados, `recovered=true`. No se marcó terminal. |
| Run 27 — crecimiento con Bone Meal | `block1-regression-growth-20261112-185000` / `lumber-xp` | **PASS** | Almacén Bone Meal 32→0; `[LENADOR-ABONO]` observó crecimiento de plantones a `oak_log`; `growthVerified=true`. XP 1→7, 6 troncos retirados, arboleda 40→36. |

### Contrato unattended y cleanup

Los tres runs PASS anteriores verificaron `joinedRequestedWorld=true`,
`clientWindowHiddenWithoutActivation=true`, `focusStealActions=0`,
`remainingTestClients=0` y `clean=true`. El runner terminó usando
`forced-test-client-kill` tras el cierre del servidor; por eso el proceso
Gradle reportó exit code -1 aunque el controlador ya había escrito un
`result.json` válido con PASS y el runner confirmó limpieza. No confundir ese
código del launcher con fallo de la aserción de gameplay.

### Prueba adicional de dos trabajadores / preempción (no requerida)

Run `block1-regression-scheduler-20261112-185000`, scenario
`lumber-interrupt`: **sin resultado aceptable; fixture incompleto**. El primer
intento `phase` terminó BLOCKED con el grace antiguo de 180 s. El reintento
`phase-retry300` sí entró a `A5WALL` y quedó oculto sin activar foco
(`joinedRequestedWorld=true`, `clientWindowHiddenWithoutActivation=true`,
`focusStealActions=0`), pero no escribió `result.json`; `shutdown.json`
registró `result=MISSING`, `launcherExitCode=1`, `remainingTestClients=0`,
`clean=true`, elapsed 415.9 s.

El fixture creó worker B como fletcher, pero los logs muestran a B bajo
`VillagerGuardGoal`; no aparecieron `[A5-WORKERS] distinctGoals=true`,
`distinctTargets=true` ni `[A5-PREEMPT]`. El cliente terminó con exit code 1
antes de la aserción. Clasificar como fixture/ejecución sin resultado, no como
FAIL del scheduler de producción. Run81 histórico sigue PASS y esta prueba
extra no lo reemplaza. No repetir hasta aislar el worker B de la asignación de
guardia y controlar el estado de asedio.

### Reconciliación final de Block 1 y siguiente paso

Forestry Block 1 A1-A7 ya estaba declarado COMPLETE en
`FORESTRY-BLOCK-1-ACCEPTANCE.md`, con Runs 57/58/62/65/27/81 aceptados. La
continuación del 2026-11-12 reejecutó Run 62, Run 65 y Run 27 equivalents y los
tres dieron PASS. Runs 57/58 (bosque interior/entrega) y Run 81 (aislamiento de
dos trabajadores) conservan su evidencia histórica PASS; no requieren repetición.

La prueba adicional `lumber-interrupt` no produjo resultado aceptable:
`phase` BLOCKED con grace antiguo y `phase-retry300` entró al mundo, pero el
fixture B quedó bajo `VillagerGuardGoal` y el cliente terminó sin
`result.json`. No es un FAIL del scheduler de producción y no invalida Run 81.
No hay regresiones Block 1 pendientes en este momento.

A5-01..A5-12 tienen PASS para los caminos normales de persistencia/reanudación.
Phase 1 permanece PARTIAL solo por las excepciones explícitamente no reclamadas:
sapling placement rechazado/refund, Bone Meal API-false refund y crash atomicity
(fuera de alcance). Siguiente paso: revisar el criterio de cierre con estas
limitaciones explícitas; no volver a ejecutar A5 ni Block 1 sin un cambio que
afecte sus invariantes.

## Exceptional resource-refund acceptance — PASS (2026-11-12)

Both exceptional resource paths have deterministic runtime evidence. The
fixtures use separate disposable copies of `A5WALL`, quick-play unattended,
and reset the failure-injection flags in `finally`. No original save was used.

### Sapling placement rejection — PASS

Run `phase1-final-sapling-reject-20261112-194500`, scenario
`lumber-sapling-reject`, tick 100.

- Forced `plantarSemilla` placement rejection after sapling withdrawal.
- Exercised both `plantar` and immediate `replantar` failure paths.
- Both target positions remained air; one oak sapling was returned to the
  villager inventory exactly once.
- `replantar` returned boolean `false`, preserving the caller's retry/hole
  handling contract.
- `result.json`: PASS; detail confirms inventory=1 and bothWorldPositionsAir=true.
- `shutdown.json`: resultSeen=true, result=PASS, malformed=false,
  joinedRequestedWorld=true, clientWindowHiddenWithoutActivation=true,
  focusStealActions=0, remainingTestClients=0, launcherTreeRemaining=0,
  clean=true. Runner process exit code 0. The Gradle launcher itself was
  force-terminated after world shutdown (launcherExitCode=1); this is not the
  gameplay result and is accepted only with the valid result/shutdown pair.

### Bone Meal API-false after argument mutation — PASS

Run `phase1-final-bonemeal-reject-r2-20261112-194700`, scenario
`lumber-bonemeal-reject`, tick 100.

- Injected an API-false result after simulating consumption of the argument
  stack (Bone Meal stack count 1 -> 0 before the false result).
- The first implementation exposed a real defect: growing `ItemStack.EMPTY`
  did not reconstruct the item, and the first run failed with inventory 0.
- Corrected the refund path: when the API argument is empty but the operation
  did not grow the sapling, construct a new `ItemStack(Items.BONE_MEAL,
  consumidas)`; otherwise copy the remainder and restore the consumed count.
- Final rerun: log `api=false consumidas=1 devueltas=1 inventario=1`;
  sapling block state unchanged; `[A5] FIN result=PASS`.
- `result.json`: PASS, inventoryBoneMeal=1, saplingUnchanged=true.
- `shutdown.json`: resultSeen=true, result=PASS, malformed=false,
  joinedRequestedWorld=true, clientWindowHiddenWithoutActivation=true,
  focusStealActions=0, remainingTestClients=0, launcherTreeRemaining=0,
  clean=true. Runner process exit code 0. Gradle launcher was force-terminated
  after server shutdown (launcherExitCode=1), with clean=true.

### Production and harness changes

- `plantarSemilla` now returns success only when the requested sapling block
  is actually present after `setBlock`; a rejected placement refunds the
  withdrawn sapling. Both initial planting and immediate replanting check it.
- Bone Meal failure refunds the original amount even if the API mutated the
  passed stack before returning false or no growth occurred. If the passed
  stack became empty, a fresh stack is reconstructed rather than mutating
  `ItemStack.EMPTY`.
- Failure-injection controls default to false and are reset after each fixture.
- `run-a5.ps1` cleanup now tolerates a child process exiting between the
  process snapshot and `taskkill /T /F`; it still performs a second process
  sweep. `-SelfTest` verifies launcher-tree termination and passed after the
  change.
- Final build validation after the resource fix:
  `compileJava compileTestJava policyTest --rerun-tasks` — BUILD SUCCESSFUL;
  all policy fixtures reported OK.

### Phase 1 disposition

A5-01..A5-12 normal lifecycle and malformed-NBT cases are PASS; Block 1 A1-A7
is COMPLETE; the sapling rejection and Bone Meal API-false refund exceptions
are now PASS. **Phase 1 is accepted within its documented scope.** Abrupt
process-crash atomicity remains excluded by design and is not claimed.
