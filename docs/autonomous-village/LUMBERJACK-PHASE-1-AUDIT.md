# Lumberjack Phase 1 — Source Audit and Requirements

Status: READ-ONLY AUDIT. No implementation.

## 1. Baseline
Forestry Block 1 is accepted (A1–A7). Food Economy Block 3.2 is closed.
Do not reopen either without reproducible regression evidence.

## 2. Existing architecture (confirmed)
- `VillagerLumberjackGoal` owns one `ForestryTaskController` and two
  `ForestryWorkPlanner` instances (inner forest, outer arboleda).
- Goal priority 0, flags MOVE + LOOK, installed by
  `VillageManager.asegurarGoalDeLenador` for `Profession.LUMBERJACK`.
- Phase enum: TALAR, PLANTAR, ABONAR, RECOGER_HARINA, ENTREGAR.
- `descubrirTrabajo` selects trees/holes/seeds; `buscarArbolArea`,
  `buscarArbolEnFrontera`, planner batches.
- Harvest `talar`, replant `plantar`, bone meal `abonar`, fetch `recogerHarina`,
  delivery `ENTREGAR` to `VillageStorage`.
- Per-goal controller isolates assignments; no global reservation registry.
- Stuck detection uses physical progress; failed points persist via VillageManager.
- Two-worker isolation verified in Block 1 Run 81.

## 3. Confirmed capabilities
- Tree discovery inside forest, arboleda and frontier.
- Harvest trunk/leaf handling with per-tree caps.
- Replant from carried/warehouse saplings; remembers holes.
- Bone Meal growth and refund-on-failure in source.
- Delivery and exact accepted-count accounting.
- Terminal/recoverable obstruction handling.
- Two-worker scheduling isolation; idempotent controller completion.

## 4. Gaps / partials
1. No reload persistence for controller phase, target, hole list, or planner
   cursor/readiness. These are goal-instance fields only. A reload restarts
   scouting. Block 1 "persisted planner origin/cursor" means persisted across
   ticks/calls, not across world save/load.
2. No global task reservation. Accepted by design, but preemption/reload can
   allow duplicate scouting; ownership is per goal only.
3. Rejected/partial delivery not forced in runtime (Block 1 limitation).
4. Failed-growth Bone Meal refund not forced in runtime (source only).
5. No axe/tool requirement or durability semantics; chopping is timed.
6. Long-run stability beyond ~5400 ticks not established.
7. No endurance/long-run acceptance for two workers competing with other goals.
8. No acceptance for full inventory during harvest/delivery.
9. No explicit sapling/bone-meal stock sustainability acceptance beyond
   individual runs.

## 5. Dependencies
- `VillageStorage` for delivery/saplings/bone meal.
- `VillagePantry` for Bone Meal only (do not change food economy).
- `VillageManager` for membership, failed points, navigation/activity.
- `VillageGenerator` for tree classification/geometry.
- `VillageForestLayout` for arboleda layout.
- Navigation/obstruction policies; other MOVE goals.

## 6. Phased plan
### Phase 0 — baseline regression (no code)
Re-run Block 1 scenarios 62/65/27/57/81; record invariants. Confirm no regression.

### Phase 1 — reload-safe forestry state
Persist per-goal controller phase/target and planner origin/cursor/readiness in
villager persistent data; restore on goal creation. Files: VillagerLumberjackGoal,
ForestryTaskController, ForestryWorkPlanner, VillageManager. Runtime: save/reload
mid-travel, mid-harvest, mid-delivery. Risk: stale target after world change;
validate target before resuming.

### Phase 2 — delivery/inventory edge acceptance
Force full inventory, rejected/partial insertion, and failed growth refund.
Fix only proven defects in `ENTREGAR`/`VillageStorage`. Acceptance: no item loss,
no phantom wood, exact counts.

### Phase 3 — task ownership under preemption
Add per-goal reservation diagnostics (not a global arbiter) and verify no two
workers claim one tree after preemption/reload. Preserve Block 1 isolation.

### Phase 4 — long-run endurance
Two workers, multiple harvest/replant/delivery cycles, other professions active.
Acceptance: no permanent stuck, no duplicate delivery, stable sapling/bone-meal
loop.

## 7. Acceptance criteria
- Reload resumes or safely re-scouts without duplicate assignment.
- No wood/sapling/bone-meal duplication or loss at edges.
- Existing A1–A7 invariants remain.
- `compileJava`, `compileTestJava`, `policyTest`, strict lint, diff check.
- Runtime evidence for every new behavior; source-only is not PASS.

## 8. Documentation correction
Block 1 wording "persisted planner origin and cursor" is accurate only for
in-memory ticks/calls, not world save/reload. This audit records the distinction.

## 9. Out of scope
Food economy, guard/raid, other professions, broad AI redesign, global movement
arbiter, village generation balance.
