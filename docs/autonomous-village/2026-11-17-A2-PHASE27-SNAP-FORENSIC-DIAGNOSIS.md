# A2 Phase 27 — DROP source snap forensic diagnosis (2026-11-17)

## 1. Inspected tree

- SHA inspected: `05b7e9c04b3619a0d2fea1a9a79899e27f131aac`
- Branch HEAD at inspection: `05b7e9c04b3619a0d2fea1a9a79899e27f131aac`
- Source run: `a2n-20261117-155419` (tested `29b82a2c`)
- IDX4: `f6b5e595-a8b2-4ab0-9952-9085e314b115`

No production, test, world or runtime change was made for this phase.

## 2. Files and methods inspected

- `NavExecutor.java`
  - `iniciarRecuperacionFisicaOrigenDrop()` (line 3676)
  - `resolverFuenteActualYReplanificar()` (line 3893)
  - call sites at 4272, 4317, 4344
  - `traverse()`, `recover()`, `recordCell()` / oscillation guard
- `NavSourceCellPolicy.java`
  - `bestAdjacentCandidate()`
  - `centeredInCell()` (0.20 tolerance)
  - `horizontalOverlapArea()`
- `NavObstruction.java`
  - `fitsActualAabb()`
  - `fitsAtCellCenter()`
  - `hasSupport()`
- `NavPlanner.java`, `NavTrace.java`

## 3. Real snap flow

There are two consumers of the same `fuenteDropPendiente`:

1. `resolverFuenteActualYReplanificar()` — called from the DROP origin branches when the
   **planned take-off is unsupported** or the live origin has no valid source. It starts/continues
   the snap via `MoveControl`, and on center+support+fit emits
   `DROP_TAKEOFF_SOURCE_SNAP_REPLAN`.
2. `iniciarRecuperacionFisicaOrigenDrop()` — called when the planned origin is not physically
   reached. It also consumes `fuenteDropPendiente`, and emits `SOURCE_SNAP_REACHED` on center+support.

Both use the same `fuenteDropPendiente`, `inicioSnapFuenteDrop` and the same
`MoveControl.setWantedPosition(center, currentY, 0.35)`; both call `mob.getNavigation().stop()`.
They are selected by different outer branches, but their pending state is shared.

The snap does **not** create a dedicated `NavState.SNAP`; `state` remains TRAVERSING while the
candidate is being approached.

## 4. Evidence: competing movement

At tick 4250 IDX4 selects `577,110,514` and starts a snap to `577.50,110.00,514.50`.

CSV positions (blockPosition / exact) around the snap:

```text
t4230 (576,110,515) z=515.020
t4235 (576,110,514) z=514.970
t4239 (577,110,514) z=514.790
t4249 (577,110,513) z=513.990
t4259 (577,110,514) z=514.000
t4262 (577,110,513) z=513.970
t4270 (577,110,514) z=514.000
```

The mob never reaches z=514.5; it oscillates on the z=514 boundary.

At the same time, a normal DROP navigation command appears:

```text
L? t4261 MOVE_COMMAND reason=DROP node=577,109,513
 requested=577.50,109.00,513.50 speed=0.9
 pos=577.43,110.00,514.01
```

This is the opposite horizontal target from the snap (`514.5` vs `513.5`). The movement command is
issued by the route traversal, not by the snap. The snap's `MoveControl` and the normal
`Navigation.moveTo` therefore contend for the same entity.

## 5. Evidence: oscillation guard aborts snap

`recordCell()` uses a 24-cell window and flags:

```java
oscillating = occurrences >= 3 && recentCells.size() >= 8;
```

The repeated cells `(577,110,513)` `→` `(577,110,514)` `→` `(577,110,513)` satisfy the A→B→A
pattern. At tick 4270 the cell is again `(577,110,514)`; at 4271 the CSV state changes:

```text
t4270 state=TRAVERSING
t4271 state=RECOVERING
t4272 RECOVER_DECISION ENTER
     pos=577,110,514 state=RECOVERING objective=577,109,511
     nextRecoveryAt=4268 recoveryActions=2/24 routeIdx=1
```

`traverse()` on `oscillating` clears the route and sets `state = NavState.RECOVERING` (line 4068).
The snap is therefore abandoned before the 80-tick timeout and before center tolerance.

No event exists for this transition; the state change is only visible in CSV and the following
`RECOVER_DECISION`.

## 6. Why `SNAP_REACHED`, `SNAP_REPLAN`, `SOURCE_SELECTED` are absent

- `SNAP_REACHED` requires `start.equals(candidate)` **and** dx²+dz² ≤ 0.04 as well as
  support/actual-AABB fit. At the stall z=514.01 vs center514.5: dz≈−0.49, distance²≈0.24 > 0.04.
- `SNAP_REPLAN` additionally requires the same center predicate in `resolverFuente...`; not met.
- `SOURCE_SELECTED` is **not emitted by the current code** at all. Candidate selection is logged as
  `DROP_TAKEOFF_SOURCE_CELL_CANDIDATE`; there is no `DROP_TAKEOFF_SOURCE_SELECTED` call. Its absence
  is an instrumentation gap, not evidence of failed selection.

## 7. Destination fit vs movement

`fitsAtCellCenter(577,110,514)` was logged true in the candidate event. The static destination AABB
therefore fits; the failure is not a simple "destination blocked" fit failure. The movement stops
about 0.49 short of center while a competing route command pulls toward z=513.5.

The exact physical collision vs. controller ownership split cannot be proven from existing logs:
there is no swept-AABB/block-collision trace and no per-tick record of which controller issued the
winning `setWantedPosition`.

## 8. Eight-neighbor matrix

The existing logs do **not** contain the full eight-neighbor matrix. For IDX4 only the selected
candidate and one rejection are logged:

```text
t4250 candidate=577,110,514
 feetValid=true support=true realAabbFits=true

t4250 rejected=576,111,515
 reason=feet_support_or_real_aabb_fit_changed

t4204 rejected=574,112,518
 reason=feet_support_or_real_aabb_fit_changed
```

`DROP_TAKEOFF_SOURCE_CELL_INVALID` includes the AABB but only the selected candidate, not the eight
ranked neighbors. Therefore the requested N/NE/E/SE/S/SW/W/NW matrix is **EVIDENCE_MISSING**. It must
be added as read-only telemetry in Phase 28 if ChatGPT needs the complete ranking.

Known AABB:

```text
AABB[577.0855345460943,110.0,513.6898689903036]
 -> [577.6855345699362,111.95000004768372,514.2898690141454]
```

Current cell `(577,110,513)` overlaps x fully (0.6) and z only partly; candidate z=514 overlaps the
AABB's forward edge. This explains why the candidate is selected but not why movement stops 0.49
short.

## 9. Comparative pattern

Global Phase 26:

```text
SOURCE_CELL_CANDIDATE: 16
SOURCE_SELECTED:        0 (event not implemented)
SNAP_STARTED:          19
SNAP_REACHED:           0
SNAP_REPLAN:            0
```

Every snap that starts fails to reach center. IDX4 is the only case with a detailed candidate sequence
in this run. The common failure signature is: candidate found → MoveControl snap started → no
`SNAP_REACHED` → state changes to RECOVERING via guard/oscillation or a route command resumes.

## 10. Root cause classification

**ROOT_CAUSE_PARTIALLY_CONFIRMED.**

Confirmed:

- The snap is initiated by `resolverFuenteActualYReplanificar` (and the fallback
  `iniciarRecuperacionFisicaOrigenDrop`).
- The snap target is the candidate center at z=514.5.
- A normal DROP `MOVE_COMMAND` at tick 4261 targets z=513.5, opposite to the snap.
- The mob oscillates between block cells 513 and 514 at the z boundary.
- The oscillation guard changes state to RECOVERING at tick 4271 and aborts the snap before the
  center tolerance.
- `SOURCE_SELECTED` is not implemented, not merely missing at runtime.

Unresolved:

- Whether the last 0.49 block is blocked by a physical collision or solely by the competing
  navigation controller. Existing logs do not include swept collision or controller ownership.

## 11. Evidence needed to distinguish the remaining hypotheses

Add read-only telemetry (no behavior change):

```text
SNAP_MOVE_TARGET      tick, source, target, controller=MoveControl
SNAP_POSITION_DELTA   tick, before/after exact XYZ, delta movement
SNAP_CONTROLLER_OWNER tick, last setWantedPosition caller, navigation path target
SNAP_SWEPT_COLLISION  tick, swept AABB min/max, intersecting block positions/shapes
SNAP_CENTER_CHECK     tick, dx, dz, distanceSqr, tolerance, result
SNAP_GUARD_ABORT      tick, reason=oscillation, cells, occurrences, window size
SOURCE_SELECTED       tick, selected cell, overlap, distance, rank, rejected neighbors
```

The critical missing pieces are the swept collision result and the per-tick winning controller target.
Without them, Phase 28 cannot distinguish physical blockage from navigation ownership conflict.

## 12. Recommendation for Phase 28

1. Make the snap state exclusive: while `fuenteDropPendiente != null`, suppress normal DROP
   `Navigation.moveTo` and do not let `traverse`/oscillation consume the entity.
2. Add an explicit `NavState.SNAP` (or a guarded flag) so the oscillation guard does not treat
   sub-cell boundary jitter as route oscillation.
3. Emit `SOURCE_SELECTED` when `bestAdjacentCandidate` returns a winner, with rank/overlap/distance
   and the eight neighbors' predicate results.
4. Add swept-AABB collision telemetry before changing movement.
5. Do not relax support/fit or increase the 900 budget.

## 13. Files modified

None in production or tests. Only this report and the handoff documents are modified.

```text
PRODUCTION_MODIFIED_BY_HARNESS: false
ORIGINAL_WORLDS_MODIFIED: false
LOCAL_ARTIFACTS_PRESERVED: true
```
