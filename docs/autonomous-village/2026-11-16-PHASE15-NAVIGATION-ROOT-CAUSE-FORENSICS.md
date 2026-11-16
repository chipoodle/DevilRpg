# Phase 15 — Navigation Root-Cause Forensics (2026-11-16)

## Git baseline

- Branch `feature/autonomous-village`.
- Initial local/remote HEAD: `2f7ac40b09c3cb2ce6b248cb7fbf03199487bc98`.
- No divergence. Diagnostic-only probe; no gameplay changes.

## Diagnostic runs

- `a2n-20261116-041520` — **invalid**: NeoForge mod loading stalled for hours after the prior killed
  run; no world tick, no fin. Excluded.
- **`a2n-20261116-092800`** — valid DIAGNOSTIC, NOT ACCEPTANCE.
  - 8/8 spawn, **5/8 perimeter, 4/8 strict**, C4 **PASS** `[2,0,0]`.
  - Strict: idx1 558, idx2 667, idx3 3764, idx6 649; idx4/5/7/8 outside.

## IDX7 — raw precision

UUID `8c194b69-8f7b-4504-a1ee-4e0f16c6879b`.

```text
t=5960/5980/6000
exact9=713.222390965,58.000000000,468.300000012
deltaSincePrev=0.000000000,0.000000000,0.000000000
bbox=712.9224,58.0000,468.0000 -> 713.5224,59.9500,468.6000
solidShapesInBBox=0 firstSolid=-
velocity=0.00,-0.08,0.00 onGround=true inWater=false
pathNull=true pathDone=true state=FAILED
lastAction=715,60,467:BREAK-ACERCARSE (dist=3.31)
```

- Displacement is exactly zero at nine decimals across the sampled interval.
- No solid collision shapes intersect the entity's bounding box in this sample. **Collision is not
  proven** as a cause; it is excluded only for this bbox/sample.
- Unlike Phase-14 IDX7, this run's path is null and the executor is `FAILED`; the Phase-14
  reachable-path-in-water state did not reproduce. The failure is therefore not demonstrably the same
  mechanism.

## IDX8 — one-node path

UUID `912477ff-8576-439c-bb82-9c5c6cf35ab0`.

```text
t=5960/5980/6000
exact9=746.799388735,64.000000000,538.513685849
deltaSincePrev=0.000000000,0.000000000,0.000000000
bbox=746.4994,64.0000,538.2137 -> 747.0994,65.9500,538.8137
solidShapesInBBox=0
pathNull=false pathCanReach=false pathDone=true pathInProgress=false
pathNodes=1 pathIndex=1 pathNext=-
state=PLANNING objective=747,68,539
```

- Exact zero displacement, no solid shapes in bbox.
- A one-node `canReach=false` path is done; no physical approach.
- **The planning transaction that created this one-node path was not captured.** Origin remains
  unknown (invalid target, start/target conversion, search exhaustion, evaluator restriction, etc.).

## IDX5

Still outside; tick-6164 cooldown boundary was not observed. Status remains **UNKNOWN**; no extension
was run.

## C4 contract review

`[0,0,0]` in `C4 JOB_SITE assignments=0` means **zero workers currently hold a vanilla Brain
`JOB_SITE` memory**; it is not coordinate `(0,0,0)` and not a production sentinel.

- The current contract requires `assignments[0] > 0`, `orphaned == 0`, `duplicates == 0`.
- It does **not** verify that a worker's custom profession workstation is valid.
- A worker can execute the custom profession Goal with no Brain `JOB_SITE`; this was observed in
  Phase 13 (`12/12` resumed, `assignments=0`).
- Phase 15 has `assignments=2`, no orphans/duplicates, PASS.

**Recommendation (no change made):** keep the current contract until a separate decision. If custom
Goals are authoritative, the contract should distinguish “no vanilla memory but valid custom station”
from “missing/orphaned/duplicate workstation”; accepting zero automatically would lose duplicate and
orphan detection. Any change requires explicit authorization.

## Proven / unproven

Proven:
- IDX7 exact zero displacement with no solid shapes inside its bbox in the sampled window.
- IDX8 exact zero displacement with a one-node, non-reaching path marked done.
- `[0,0,0]` = zero Brain JOB_SITE memories, not a coordinate.

Unproven:
- Physical collision outside the bbox for IDX7.
- The pathfinder transaction that produced IDX8's one-node path.
- Why the actuator does not move along a reachable path in the Phase-14 water state.
- IDX5 post-cooldown behavior.

## Tests

```text
gradlew test: PASS
lint_aldea.py --strict: OK
git diff --check: PASS
```

## Worlds

Unchanged:
- A2 `BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7`
- C4 `11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF`

## Next action

1. Instrument the pathfinder transaction for IDX8 (requested target, start/target nodes, evaluator,
   accuracy, returned nodes) to explain the one-node path.
2. For IDX7, capture per-tick movement input and `PathNavigation.tick()`/`followThePath` execution when
   a reachable path exists; do not infer collision from static position.
3. C4 contract decision remains separate and unauthorized; do not alter `assignmentsClean`.
