# A2 Phase 28 — DROP snap admission and movement ownership (2026-11-17)

## 1. Inspected state

- Branch: `feature/autonomous-village`
- Inspected SHA: `cebca95b64a4856e6216941631b636af4d1e561b`
- Production SHA: `05b7e9c04b3619a0d2fea1a9a79899e27f131aac` (no production diff)
- Source run: `a2n-20261117-155419`
- IDX4: `f6b5e595-a8b2-4ab0-9952-9085e314b115`
- Temporary runtime used: **NO**
- Original A2/C4 worlds: untouched

No production, test, world or acceptance change was made.

## 2. Files/methods examined

- `NavExecutor.java`
  - `iniciarRecuperacionFisicaOrigenDrop()` (3676)
  - `resolverFuenteActualYReplanificar()` (3893)
  - DROP traversal and admission (4230–4344)
  - `mob.getNavigation().moveTo(..., 0.9D)` at 4472
  - `recordCell()` / `traverse()` oscillation guard
- `NavSourceCellPolicy.java` — `bestAdjacentCandidate`, `centeredInCell`, overlap
- `NavDropTransitionPolicy.java` — `origenFisicamenteAlcanzado`, `origenPlanificado`
- `NavObstruction.java` — `fitsAtCellCenter`, `fitsActualAabb`
- `NavPlanner.java`, `NavTrace.java`

## 3. DROP admission — confirmed premature

At tick 4233 the route's DROP node is `577,109,513`, but:

```text
DROP_START idxRuta=1
 aterrizaje=577,109,513 salida=577,110,513 dy=-1
 originPlan=577,110,514
 grounded=true liveFeetValid=true liveSupport=true liveAabbFits=true
```

The planned origin is the **candidate cell 577,110,514**, not the actual predecessor
`577,110,513`. The admission predicate is:

```java
origenFisicamenteAlcanzado(int liveX, int liveY, int liveZ,
                           int origenX, int origenY, int origenZ) {
    return abs(liveX-origenX) <= 1 && liveY == origenY && abs(liveZ-origenZ) <= 1;
}
```

It compares **integer coordinates with ±1 tolerance**, not the 0.20 center tolerance used by the
snap (`NavSourceCellPolicy.centeredInCell`).

At tick 4261:

```text
live block = 577,110,514
exact      = 577.43,110.00,514.01
candidate center = 577.50,110.00,514.50
centeredInCell(0.20) = false (dz≈0.49)
originPlan = 577,110,514
admission = true (same integer cell, dy=0)
```

DROP is therefore admitted and `DROP_COMMAND` is emitted while the snap has not reached center.
The 0.20 snap tolerance is never consulted by the admission path.

```text
DROP_ADMISSION: PREMATURE — integer-cell equality/±1, not 0.20 center tolerance
```

## 4. Movement ownership — confirmed conflict

While the snap target is `514.50`, the normal DROP traversal issues:

```text
t4261 MOVE_COMMAND reason=DROP node=577,109,513
 requested=577.50,109.00,513.50 speed=0.9
 pos=577.43,110.00,514.01
```

Source: `NavExecutor.java:4472`

```java
mob.getNavigation().moveTo(aim.x, aim.y, aim.z, 0.9D);
```

The snap uses:

```java
mob.getNavigation().stop();
mob.getMoveControl().setWantedPosition(candidate.getX()+0.5, mob.getY(),
                                       candidate.getZ()+0.5, 0.35D);
```

Once the route branch resumes, `Navigation.moveTo` owns the path/MoveControl and pulls toward
`513.50`, opposite to the snap's `514.50`. The two targets fight.

Position sequence:

```text
t4249 z=513.990
t4259 z=514.000
t4262 z=513.970
t4270 z=514.000
```

The mob oscillates around the z=514 boundary and never reaches `514.50`.

```text
MOVEMENT_OWNERSHIP: normal DROP Navigation.moveTo wins after premature admission
```

## 5. Oscillation guard — confirmed abort

`recordCell()` flags A→B→A when `occurrences >= 3 && recentCells.size() >= 8` in a 24-cell window.
Cells `(577,110,513)` and `(577,110,514)` alternate. At:

```text
t4270 state=TRAVERSING
t4271 state=RECOVERING
t4272 RECOVER_DECISION ENTER
     pos=577,110,514 state=RECOVERING objective=577,109,511
     nextRecoveryAt=4268 recoveryActions=2/24 routeIdx=1
```

`traverse()` sees `oscillating`, drops the route and switches to RECOVERING (NavExecutor 4068). The
snap is not given the 80 ticks; `SNAP_REACHED`/`SNAP_REPLAN` are never emitted.

```text
OSCILLATION_GUARD: CONFIRMED ABORT at t4271 before center/snap timeout
```

## 6. Swept AABB / physical collision

Initial AABB at tick 4250:

```text
[577.0855345460943, 110.0, 513.6898689903036]
-> [577.6855345699362, 111.95000004768372, 514.2898690141454]
```

Target center `(577.5,514.5)` would move the AABB to approximately z=514.20–514.80. The candidate
event logged `fitsAtCellCenter=true`; therefore the static destination AABB fits.

There is **no swept-AABB trace, no list of intersected blocks, and no per-tick collision result**.
The trace cannot distinguish "collision at z≈514.0" from "navigation target z=513.50 pulls back".
However, the competing `MOVE_COMMAND` target `513.50` is direct positive evidence of controller
competition and is sufficient to explain why the snap does not progress.

```text
SWEPT_AABB: EVIDENCE_MISSING
PHYSICAL_COLLISION: NOT DEMONSTRATED; competing navigation confirmed
```

## 7. Eight-neighbor matrix

Source cell `(577,110,513)`; AABB as above. Only same-Y neighbors. Horizontal overlap area is exact
from the logged AABB. Predicate columns for non-overlapping cells are not evaluated by the policy
(early `continue`), so they are marked N/E.

| Dir | Cell | Overlap | Dist² to center | Feet | Support | Fit center | Selected | Reason |
|---|---|---:|---:|---|---|---|---|---|
| N | `577,110,512` | 0.0000 | ~2.230 | N/E | N/E | N/E | no | no horizontal overlap |
| NE | `578,110,512` | 0.0000 | ~2.690 | N/E | N/E | N/E | no | no horizontal overlap |
| E | `578,110,513` | 0.0000 | ~0.637 | N/E | N/E | N/E | no | no horizontal overlap |
| SE | `578,110,514` | 0.0000 | ~0.647 | N/E | N/E | N/E | no | no horizontal overlap |
| S | `577,110,514` | **0.1739** | **0.2731** | true | true | true | **YES** | selected: only overlap |
| SW | `576,110,514` | 0.0000 | ~1.990 | N/E | N/E | N/E | no | no horizontal overlap |
| W | `576,110,513` | 0.0000 | ~2.181 | N/E | N/E | N/E | no | no horizontal overlap |
| NW | `576,110,512` | 0.0000 | ~3.730 | N/E | N/E | N/E | no | no horizontal overlap |

The selected candidate is `(577,110,514)`, with overlap `0.6 × 0.2899 ≈ 0.1739` and center distance²
`0.114² + 0.510² = 0.2731`. There is no ambiguity; the other seven have zero overlap.

The full matrix for rejected cells cannot be completed beyond overlap because the runtime does not
log their feet/support/fit predicates (policy returns early on `!overlapsHorizontalCell`).

## 8. Hypotheses

| Hypothesis | Status |
|---|---|
| A. Normal navigation steals ownership | **CONFIRMED** by `MOVE_COMMAND` target 513.5 vs snap 514.5 |
| B. Geometry/physical collision blocks final move | **NOT DEMONSTRATED**; center fit true, no swept trace |
| C. Admission continues before center | **CONFIRMED** by integer origin `577,110,514` and `origenFisicamenteAlcanzado` ±1 |

## 9. Classification

**ROOT_CAUSE_PARTIALLY_CONFIRMED.**

Confirmed sequence:

1. Candidate `577,110,514` selected.
2. Snap starts with target `514.50`.
3. `originPlan` becomes the candidate integer cell `577,110,514`.
4. DROP admission uses integer ±1, so at `z=514.01` it returns true despite 0.49 to center.
5. Normal DROP navigation commands `z=513.50`, opposite to the snap.
6. Cell alternation 513↔514 triggers oscillation; state becomes RECOVERING at t4271.
7. Snap never reaches center; no `SNAP_REACHED`/`SNAP_REPLAN`.

Unresolved:

- exact swept collision at the final 0.49;
- per-tick winning `setWantedPosition` ownership (Navigation vs MoveControl).

## 10. Evidence needed to close the remaining gap

Read-only instrumentation:

```text
SNAP_MOVE_TARGET   tick, target, caller
SNAP_CONTROLLER    tick, navigation path target, MoveControl wanted target
SNAP_SWEPT_COLLISION tick, before/after AABB, intersected blocks/shapes
SNAP_CENTER_CHECK  tick, dx, dz, d2, tolerance
SNAP_GUARD_ABORT   tick, oscillating, cells, occurrences, window
SOURCE_SELECTED    tick, selected, overlap, distance, full neighbor predicates
```

## 11. Phase 28 recommendation

1. Require the same exact center/occupancy predicate for DROP admission that the snap requires;
   integer-cell ±1 is not sufficient after a source snap.
2. Keep one movement owner while `fuenteDropPendiente` is active; suppress normal DROP
   `Navigation.moveTo` until snap completion or explicit failure.
3. Prevent the oscillation guard from consuming a boundary-jitter snap.
4. Add the telemetry above before changing movement geometry.

No production change was implemented.

## 12. Evidence and integrity

- `build/a2n/a2n-20261117-155419/nav-trace.log`
- `build/a2n/a2n-20261117-155419/srv.log`
- Phase 27 report: `2026-11-17-A2-PHASE27-SNAP-FORENSIC-DIAGNOSIS.md`

```text
PRODUCTION_MODIFIED_BY_HARNESS: false
ORIGINAL_WORLDS_MODIFIED: false
LOCAL_ARTIFACTS_PRESERVED: true
```
