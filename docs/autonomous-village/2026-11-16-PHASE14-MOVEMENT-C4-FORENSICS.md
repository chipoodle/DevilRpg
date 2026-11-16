# Phase 14 — Movement Correlation and C4 Job-Site Forensics (2026-11-16)

## Git baseline

- Branch `feature/autonomous-village`.
- Initial local/remote HEAD: `9c47a56e0053a63a801d3f2a313655b463713ecf`.
- No divergence. Diagnostic-only probes; no gameplay changes.

## Diagnostic runs

- **`a2n-20261116-035153`** — invalidated: the new probe called `Path.getNextNode()` after the path
  index reached `nodeCount`, causing `IndexOutOfBoundsException`. Instrumentation defect only;
  fixed by bounding the read. Not used for conclusions.
- **`a2n-20261116-035911`** — valid DIAGNOSTIC, NOT ACCEPTANCE.
  - 8/8 spawn, **6/8 perimeter, 4/8 strict**, C4 **PASS** (`jobSite=[1,0,0]`).
  - Strict: idx1 512, idx2 667, idx4 3964, idx6 649.
  - idx3/idx5 perimeter only; idx7/idx8 outside.

## IDX7 — actual correlation

UUID `0f6d2c7d-dc70-4894-b49d-15d089f8fc77` (this run's idx7).

```text
t=5940 pos=707,58,467 exact=707.70,58.00,467.50
velocity=-0.01,-0.01,0.00 onGround=true inWater=true
pathNull=false pathCanReach=true pathDone=false
pathNodes=3 pathIndex=1 pathNext=708,58,467
state=TRAVERSING objective=719,63,468
lastAction=713,58,469:DROP-RECHAZADO
```

Through `t=6000` position and velocity are unchanged; path index remains 1 and next node is one block
away. This is **not** a non-reaching path: `canReach=true`, but there is **zero physical displacement**.
No collision evidence was collected, so collision is **unproven**. The entity is in water, on ground.

## IDX8 — actual correlation

UUID in this run's idx8 (`targetUuid` preserved in probe).

```text
t=5940 pos=746,64,538 exact=746.80,64.00,538.51
velocity=0.00,-0.08,0.00 onGround=true inWater=false
pathNull=false pathCanReach=false pathDone=true
pathNodes=1 pathIndex=1 pathNext=-
state=PLANNING objective=747,68,539
lastAction=745,66,538:BREAK-ACERCARSE (dist=2.82)
```

Position and path state are identical through `t=6000`: a **one-node non-reaching path is done**, the
index is at the end, and the entity never approaches. This confirms the Phase-13 path-completion failure.

## Path lifecycle / counters

- IDX7: 20-tick samples show zero displacement while a reachable path exists; no replacement observed
  in the sampled window; no path index advance beyond 1.
- IDX8: one-node path, index 1/1, `done=true`, no replacement; distance unchanged.
- No evidence of another goal/navigation stop in these windows. No collision demonstration.
- A path index increment is not treated as progress in this analysis; both cases use actual positions.

## IDX5 — cooldown

Still `RECOVERING` at `629,72,467`, path null/done, route recovery node `621,76,468`; the run closes at
6000. The 6164 boundary was not reached; **cooldown-boundary result remains unknown** (as in Phase 13).

## C4 — `[0,0,0]` meaning

Phase-13 `C4 JOB_SITE assignments=0 orphaned=0 duplicateOwners=0` is not a coordinate sentinel.
`auditarAsignacionesJobSite()` iterates villagers and counts non-empty Brain `JOB_SITE` memories.
`[0,0,0]` therefore means **zero workers held a vanilla Brain `JOB_SITE` memory**, not a job site at
coordinates `(0,0,0)`.

Phase-14 run `035911` had `assignments=1` and passed `assignmentsClean`. The phase-13 zero-assignment
case is a real absence of Brain memories under the harness contract, but all 12 custom profession goals
still ran; it is **not proven** that their workstations were missing. The harness treats presence >0 as
cleanliness, which is stricter than “no orphan/duplicate”. No acceptance change made.

## Production changes

**NONE.** The only source change is read-only probe fields (`pathCanReach`, `pathNodes`, `pathIndex`,
`pathNext`, velocity, water/ground) and bounds-safe `getNextNode()`. No navigation decision, target,
budget, collision, recovery, worker or C4 criterion changed.

## Tests

```text
gradlew test: PASS
lint_aldea.py --strict: OK
git diff --check: PASS
```

## A2/C4

- A2 focused: 6/8 perimeter, 4/8 strict; canonical five consecutive 8/8 strict gate not met; **OPEN**;
  full 12k not run.
- C4: phase-14 diagnostic PASS `[1,0,0]`; phase-13 `[0,0,0]` remains a contract failure.
  Historical focused 5/5 preserved; save/reload OPEN.

## Worlds

Unchanged:
- A2 `15,966,366` / `BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7`
- C4 `16,044,629` / `11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF`

## Proven / unresolved

Proven:
- IDX8: non-reaching one-node path completes while position remains fixed.
- IDX7: reachable path exists but actual displacement is zero in water.
- C4 `[0,0,0]` is zero Brain JOB_SITE memories, not coordinate zero.

Unresolved:
- Why PathNavigation produces no movement for IDX7's reachable path.
- Why IDX8's one-node path is generated/accepted as done at an unreachable target.
- Whether IDX5's 6164 recovery attempt succeeds.
- Whether zero Brain JOB_SITE with working custom goals should be accepted by C4.

## Next action

1. Capture collision/shape and `PathNavigation` tick state for IDX7's reachable-but-static path
   (collision must be proven, not inferred from zero motion).
2. Capture the planner input that creates IDX8's one-node, non-reaching path.
3. Do not change production until the specific path-generation/no-motion cause is proven.
