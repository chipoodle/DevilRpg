# Lumberjack Phase 1 — Reload-Safe Task State Design

Status: DESIGN ONLY. Implementation not started. Review corrections incorporated:
crash-consistency analysis, separate conservation, version policy, identity rules,
field mapping, mutation revalidation, and save-boundary acceptance.
Baseline: Forestry Block 1 accepted; Food Economy Block 3.2 closed.

## 1. Current state architecture (source-verified)

### Goal-instance fields (not persisted)
`VillagerLumberjackGoal`:
- `Fase fase` (TALAR, PLANTAR, ABONAR, RECOGER_HARINA, ENTREGAR)
- `BlockPos target` (current tree/hole/delivery target)
- `List<Hueco> pendientes` (unreplanted holes + species)
- `workTicks`, `restTicks`, `stuckTicks`
- `objetivoProgreso`, `ultimaPosicionProgreso`, `ultimoProgresoTick`
- `turnoDeBusqueda`, `turnoArea`, `buscarInteriorPrimero`
- `entregarTrasTalar`, `deliveryAttempt(s)`, `deliveryCooldown`,
  `entregaCruzandoMuro`
- `ForestryWorkPlanner forestryPlannerDentro/Fuera`
- `forestryPlannerDentroReady`, `forestryPlannerFueraReady`,
  `plannerGeneration`
- `mejorDistancia`, `barridoCooldown`, `nivelAldea`, `harinaEnElAlmacen`

`ForestryTaskController`:
- `State state` (SCOUTING/TRAVELLING/HARVESTING/REPLANTING/GROWING/
  DELIVERING/RETURNING)
- `targetX/targetY/targetZ`

`ForestryWorkPlanner`:
- `originX/originZ`, `ring`, `cursor`, `finished`

### Durable storage already available
- Villager persistent data (entity NBT): ideal owner for per-worker task intent;
  travels with the UUID and is removed with the entity.
- `VillageSavedData`: world/village identity, pantry credit, warnings; not needed
  for per-worker task state.
- No forestry task fields are currently written to either.

## 2. Persistence boundary

Recommendation: **villager persistent data only**, one namespaced compound
`DevilRpgForestryTask`, with a schema version. Do not put per-worker state in
`VillageSavedData`; that would couple task lifetime to village lifetime and make
entity death/removal cleanup harder. Do not use a global reservation registry.

Proposed schema (types are NBT):

```text
DevilRpgForestryTask
  Version: int = 1
  Objective: int                 // village objective index
  Phase: string                  // TALAR|PLANTAR|ABONAR|RECOGER_HARINA|ENTREGAR
  Controller: string             // SCOUTING|TRAVELLING|HARVESTING|REPLANTING|GROWING|DELIVERING|RETURNING
  Target: optional long          // BlockPos.asLong; absent for SCOUTING
  Holes: list of { Pos: long, Seed: optional item registry id }
  PlannerInside: { OriginX:int, OriginZ:int, Ring:int, Cursor:int, Finished:bool, Ready:bool }
  PlannerOutside:{ OriginX:int, OriginZ:int, Ring:int, Cursor:int, Finished:bool, Ready:bool }
  SearchTurn: int
  AreaTurn: int
  InsideFirst: bool
  DeliverAfterChop: bool
```

Do **not** persist: navigation path, `Path`, `mejorDistancia`, `stuckTicks`,
`workTicks`, `restTicks`, `deliveryCooldown`, `deliveryAttempts` beyond a bounded
retry counter, progress anchors, `nivelAldea`, `barridoCooldown`.
These are timing/optimization state; reconstructing them is cheaper and safer.

### 2.1 Field mapping

| Class.field | Serialized name/type | Allowed | Req | Validation | Missing/invalid |
|---|---|---|---|---|---|
| Goal.objectiveIndex | Objective:int | objective of center | yes | equals `objectiveAt(center)` | discard, SCOUTING |
| Goal.fase | Phase:string | 5 enum names | yes | valid enum; consistent with Controller | discard, SCOUTING |
| Goal.target | Target:long | BlockPos | phase-dependent | tree/hole/delivery target valid; in domain | discard target, SCOUTING |
| Goal.pendientes | Holes:list | <= MAX_PENDIENTES | no | each pos replantable; item id valid | drop invalid entries |
| Goal.buscarInteriorPrimero | InsideFirst:bool | true/false | no | none | default false |
| Goal.entregarTrasTalar | DeliverAfterChop:bool | true/false | no | only true for delivery phases | default false |
| Goal.turnoDeBusqueda | SearchTurn:int | >=0 | no | bounded | default 0 |
| Goal.turnoArea | AreaTurn:int | 0/1 | no | bounded | default 1 |
| Controller.state | Controller:string | 7 enum names | yes | valid; compatible with Phase | derive from Phase, else SCOUTING |
| Controller.targetX/Y/Z | Target:long | BlockPos | when not SCOUTING | same as Goal.target | absent -> SCOUTING |
| PlannerInside.originX/originZ | OriginX/OriginZ:int | world bounds | no | within scan area | reset inside planner |
| PlannerInside.ring | Ring:int | 0..maxRing+1 | no | bounded | reset |
| PlannerInside.cursor | Cursor:int | 0..perimeter-1 | no | bounded | reset |
| PlannerInside.finished | Finished:bool | true/false | no | consistent with ring | reset |
| Goal.forestryPlannerDentroReady | PlannerInside.Ready:bool | true/false | no | origin valid | reset |
| PlannerOutside.* | PlannerOutside.* | same as inside | no | origin within outer scan | reset outside |
| Goal.forestryPlannerFueraReady | PlannerOutside.Ready:bool | true/false | no | origin valid | reset |

Planner readiness is owned by **goal fields**
(`forestryPlannerDentroReady`/`FueraReady`), not by `ForestryWorkPlanner`;
`Finished` is owned by the planner. Persist both only because they cannot be
derived without losing whether the planner was initialized. Do not persist
`plannerGeneration`; it is a transient invalidation counter.

Transient, not serialized: `villager`, `center` (reconstructed), `workTicks`,
`restTicks`, `stuckTicks`, progress anchors, `deliveryAttempts`,
`deliveryCooldown`, `entregaCruzandoMuro`, `mejorDistancia`, `barridoCooldown`,
`nivelAldea`, `harinaEnElAlmacen`, navigation path.

Consistency rule:

```text
TALAR      -> Controller TRAVELLING or HARVESTING
PLANTAR    -> Controller REPLANTING
ABONAR     -> Controller GROWING
RECOGER_HARINA -> Controller TRAVELLING
ENTREGAR   -> Controller DELIVERING or RETURNING
SCOUTING   -> no Target
```

`Objective`, `Goal.target`, and `Controller.target*` must agree. Any mismatch is
treated as malformed and re-scouts; nothing is resumed from contradictory fields.

## 3. Serialization lifecycle

- Write after a durable phase transition (assign, start harvest, replant, begin
  delivery, delivery complete, hole add/remove) and at goal stop.
- Read once when the goal is constructed/reinstalled by
  `VillageManager.asegurarGoalDeLenador`, before the first `canUse`.
- Never trust saved data blindly: validate on load against the current world,
  village center/objective, villager alive/UUID, and block states.
- Remove the compound when the task is terminally completed or the villager is
  no longer a lumberjack.
- Missing data: start SCOUTING.
- Malformed known version: discard invalid task state and start SCOUTING.
- Unknown higher version: do **not** deserialize, modify, or overwrite it; fall
  back safely without interpreting it.
- If the goal tries to save while an unknown-version compound exists, it must
  leave the compound untouched (no overwrite), log once, and operate as transient
  SCOUTING. Normal play may not replace it; only an explicit migration may.

## 4. Phase recovery table

| Saved phase | Restore | Validate | Resume / restart | Duplicate protection |
|---|---|---|---|---|
| SCOUTING | planners + search turns | origin within allowed radius; planner cursor bounds | resume scanning from cursor; if invalid reset planner | cursor only moves forward; no item transaction |
| TRAVELLING | controller target, phase, target | target is still a valid tree/hole; standing cell exists; same objective | resume travel to validated target; else re-scout | no inventory change during travel |
| HARVESTING | target, phase, remaining work counter reconstructed | tree still present; trunk/leaf state; standing cell | restart the harvest action on the same validated tree; never assume partial block removal | count actual block drops; ledger check before delivery |
| REPLANTING | target hole, species | hole still empty/replantable; sapling available | resume planting only if sapling actually present; else add hole and re-scout | consume sapling only on successful setBlock |
| GROWING | hole/target, bone meal intent | sapling present and immature; bone meal available | resume growth attempt; if grown, clear hole | consume bone meal only when API consumes it; refund on no-growth |
| DELIVERING | delivery intent + target | storage exists/reachable; carried wood count is authoritative | resume delivery to storage; recompute path | insert actual stacks, log accepted count; never double-insert saved "delivered" totals |
| RETURNING | return target/intent | target standable/reachable | resume return; else re-scout | no item transaction |

No saved navigation path is authoritative. Always rebuild from validated intent.

## 5. Validation rules

- `Objective` must equal `objectiveAt(center)`; otherwise discard.
- `Target` must be within the village forestry domain and within max radius.
- Tree target: production tree predicate + standing cell; reject if removed,
  replaced by another block, protected, or outside the domain.
- Hole target: empty/replantable position, same species if recorded, within
  domain; otherwise drop that hole.
- Planner origin must be within the allowed scan area; ring/cursor within bounds;
  `finished` only honored when the origin is unchanged.
- Entity NBT is already scoped to the villager entity UUID; no redundant UUID
  field is added. Validate the living entity, Lumberjack profession, village
  association (center/objective), and task data on the same entity.
- Two workers may restore overlapping targets. Do not add a global arbiter:
  each validates the target; if another worker already removed it, re-scout.
  Never share or transfer a task assignment.
- Immediately before `talar`, `plantar`, `abonar`, and `entregar`, revalidate the
  target block/position and the worker's carried stacks. If the target changed or
  another worker acted, abandon it and re-scout. Saved intent alone never awards
  drops or completes a delivery.

## 6. Normal save/reload vs abrupt crash

### Scope decision

Phase 1 guarantees recovery across **normal Minecraft save, exit, and reload**.
Abrupt process death between individual Java statements is **out of scope** for
this phase; the project has no transaction framework and this design does not add
one. No pending-operation ledgers are required for normal save/reload.

Save points occur between server ticks. All mutations and the corresponding task
state updates in these four operations execute synchronously inside one `tick()`
on the server thread. A normal save therefore cannot observe a state between
mutation and phase update. Exception/crash windows are documented as limitations,
not as Phase 1 requirements.

| Operation | Mutation -> state update | Normal save/reload | Exact recovery rule |
|---|---|---|---|
| Harvest | `picarTronco` (`destroyBlock`, inventory/`VillageStorage.guardar`) -> `talar` clears `target`, sets `entregarTrasTalar` | **SAFE** | If HARVESTING/TALAR target tree is gone, or carried wood/storage changed, clear target and re-scout. Never recreate drops. |
| Planting | `sacarSemilla` -> `plantarSemilla` (`setBlock`) -> hole removal | **SAFE** | If the target is already a sapling, remove the hole and do not plant again. If still empty and a sapling is carried, retry; otherwise keep the hole. |
| Bone Meal | `sacarHarina` -> `growCrop` -> optional refund -> sapling state | **SAFE** | If the block grew, invalidate target and stop. If still a sapling, retry only with actual carried Bone Meal; refund already returns unused items. |
| Delivery | `VillageStorage.guardar` -> inventory `setItem(resto)` -> `entregarTrasTalar=false` | **SAFE** | Carried logs are authoritative. Deliver only the remainder still in inventory; empty inventory means complete. Never add a saved delivered total. |

Normal-reload classifications: **all four SAFE**.

Abrupt-crash limitation: an exception between `destroyBlock` and drop storage can
lose drops; between sapling removal and `setBlock` can lose a sapling; between
Bone Meal removal and `growCrop`/refund can lose Bone Meal; between storage insert
and inventory update can duplicate wood. These are **out of Phase 1 scope** and
remain unresolved if crash recovery is ever approved.

## 7. Separate conservation rules

### Wood
Authoritative: world block state, carried log stacks, `VillageStorage` contents.
- Only `picarTronco` block destruction creates drops.
- Only actual `VillageStorage.guardar` increases stored wood.
- `entregar` must insert only stacks currently in inventory.
- Saved intent never adds wood; reload reconcile uses physical counts.
```text
carried_before + actual_drops = carried_after + accepted_storage_delta
```

### Saplings
Authoritative: carried stacks, storage stacks, planted block state.
- `sacarSemilla` removes one; `plantarSemilla` places one.
- A hole is removed only after a successful placement.
- Failed placement must keep/refund the sapling; current removal-before-place
  window is unresolved.
```text
saplings_before = saplings_after + successful_plantings + pending_removed
```

### Bone Meal
Authoritative: carried/storage count, sapling growth state.
- `sacarHarina` removes; `growCrop` may consume; failed growth refunds.
- Refund path exists in `abonar`; forced runtime evidence is still missing.
```text
bone_before = bone_after + consumed_by_api + lost_or_pending
```

Holes are bookkeeping, not an item ledger; remove only after successful replant
or explicit invalidation.

## 8. Failure recovery

- Malformed known version -> discard and SCOUTING.
- Unknown higher version -> leave compound untouched, transient SCOUTING, no overwrite.
- Villager dead/removed -> data dies with entity; no world orphan.
- Village changed / objective mismatch -> delete, SCOUTING.
- Target invalid -> clear target, SCOUTING; retain only valid holes.
- Planner invalid -> reset that planner, keep the other if valid.
- Storage missing -> RETURNING/scout, never drop wood.
- Repeated delivery failure -> existing bounded attempts then drop recoverable
  items as today; do not invent new teleport/storage behavior.

## 8. Migration/versioning

- `Version=1` initial. Future fields are optional and defaulted.
- Never reinterpret old fields under a new meaning without a version bump.
- On upgrade: read known fields, validate, write back current version.
- Unknown higher version: ignore and re-scout rather than corrupt state.

## 9. Test plan

Automated (pure):
- NBT schema round-trip helper (if extracted without Minecraft runtime).
- Planner cursor/ring bounds and reset.
- Validation predicates for tree/hole/objective.
- Conservation accounting helper.

Runtime (A5, mandatory):
1. Save/reload while TRAVELLING.
2. Save/reload while HARVESTING.
3. Save/reload while REPLANTING/GROWING.
4. Save/reload while DELIVERING.
5. Target tree removed/changed before reload.
6. Worker removed / UUID mismatch.
7. Malformed/missing/unknown-version data.
8. Two workers with overlapping targets after reload.
9. Wood/sapling/Bone Meal conservation across each.
10. Block 1 A1–A7 regression runs 62/65/27/57/81.
11. Save immediately **before** each resource mutation: harvest destroy, sapling
    placement, Bone Meal application, storage insertion.
12. Save immediately **after** the mutation but **before** phase/hole/target
    advancement.
13. Save immediately **after** phase/hole/target advancement.
14. Repeat boundaries 11–13 for harvest, planting, Bone Meal, and delivery.
15. Profession changed or village association/objective mismatch after reload.

Automated tests may cover schema/predicates only; Minecraft entity/GoalSelector/
world save behavior must be runtime evidence.

## 10. Acceptance criteria

- Reload during any phase either resumes a validated task or safely re-scouts.
- No old navigation path is treated as authoritative.
- No duplicate/lost wood, saplings, or Bone Meal.
- Overlapping workers resolve by validation/re-scout, no global arbiter.
- Malformed data never crashes or blocks the goal.
- Existing Block 1 invariants and scenarios remain green.
- `compileJava`, `compileTestJava`, `policyTest`, strict lint, diff check.

## 11. Risks / rejected alternatives

- Persisting full navigation paths: rejected; stale and unsafe.
- `VillageSavedData` for per-worker tasks: rejected; wrong lifetime/cleanup.
- Global reservation registry: rejected; outside Block 1 design and unnecessary.
- Trusting saved target without validation: rejected; world changes.
- Persisting every transient timer: rejected; reconstruction is safer.
- Main risk: duplicate harvest/delivery after reload if saved "progress" is
  trusted. Mitigation: actual inventory/block state is authoritative.

## 12. Source classes/methods affected (future)

- `VillagerLumberjackGoal`: add `saveTaskState`/`loadTaskState`, call on stop and
  goal installation; phase transition hooks.
- `ForestryTaskController`: add state/target export/import + validation.
- `ForestryWorkPlanner`: add origin/ring/cursor/finished/ready export/import.
- `VillageManager.asegurarGoalDeLenador`: load before first tick.
- `VillageSavedData`: no schema change expected.
- `VillageStorage`/`VillagePantry`: unchanged transaction semantics.

## 13. Storage/delivery evidence

- `VillageStorage.guardar(level, center, stack)` returns the **remainder**; it never
  reports success. `VillagePantry.guardar` fills matching stacks, then empty slots,
  and returns whatever did not fit.
- Complete insertion -> remainder empty; partial -> remainder partially filled;
  rejected -> whole input returned.
- `entregar` writes the returned remainder back with `setItem(i, resto)`, so
  carried inventory is authoritative after a partial delivery.
- A normal save after the tick persists storage and carried remainder together;
  a second delivery inserts only the remainder. This is SAFE for normal reload.
- Planting a sapling already present: `esHuecoDeTierra` is false, so the hole is
  removed and no second placement occurs; the world block is authoritative.
- Bone Meal: if the sapling grew, block state invalidates the target; if not, the
  refund path returns unused Bone Meal to inventory/storage. Safe in normal reload.

## 14. Final implementation checklist (not started)

1. [ ] Add versioned `DevilRpgForestryTask` read/write helpers.
2. [ ] Persist only the fields in §2.1; skip transient navigation/timers.
3. [ ] Restore in `VillageManager.asegurarGoalDeLenador` before first tick.
4. [ ] Validate objective/profession/village/target/holes/planner bounds.
5. [ ] Enforce the phase/controller/target consistency table.
6. [ ] Revalidate target immediately before each resource mutation.
7. [ ] Use actual blocks/inventory/storage as authoritative; no saved totals.
8. [ ] Unknown higher version: never overwrite; transient SCOUTING.
9. [ ] Add A5 boundaries before mutation, after mutation, after phase advance.
10. [ ] Re-run Block 1 Runs 62/65/27/57/81.
11. [ ] Add pure tests for schema/predicates where Minecraft-free.
12. [ ] Validate compileJava, compileTestJava, policyTest, lint, diff.

Crash-window journals are explicitly **not** on this checklist.

## 15. Implementation status (source, not accepted)

Implemented: `DevilRpgForestryTask` Version 1, goal/controller/planner
export/restore, constructor load, assignment/tick/stop save, objective/profession
validation, phase/controller/target consistency (`ForestryTaskPolicy`), unknown
version preservation, harvest target revalidation.
Deviation: holes persist positions but not seed species.
No A5 save/reload scenario has been run. Phase 1 is NOT accepted.
