# IDX4 — AABB-aware grounded source recovery (2026-11-17)

## Status

**IMPLEMENTATION PUBLISHED — PENDING HARNESS.** This is a focused correction for the confirmed
AABB / integer-cell support mismatch documented in
`2026-11-17-IDX4-GROUNDED-INVALID-FORENSICS.md`.

Baseline forensic commit: `c67e0e8ae59e9ccbe96c440680accce254757477`.

Production implementation checkpoint: `b02affb19eb8e37824682090ba99f793a04aea94`.
The branch may advance with documentation commits; Harness must validate the exact live HEAD.

## Confirmed failure being corrected

At IDX4's stable failure position:

- Exact position: `593.51,87.00,507.96`.
- Integer source cell: `593,87,507`.
- Real AABB: approximately `X=593.21..593.81, Y=87.00..88.95, Z=507.66..508.26`.
- Physical support: cobblestone `593,86,508`.
- Integer-cell support query: `593,86,507` (air).

Vanilla correctly reported grounded because the real AABB overlapped the adjacent cobblestone. Navigation rejected the integer source because its support check only inspected the block directly below `blockPosition()`. The executor then waited without issuing horizontal movement.

## Production changes

### 1. Real-AABB fit checks

`NavObstruction` now exposes:

- `fitsActualAabb(level, mob)`: collision-checks the mob's actual current bounding box.
- `fitsAtCellCenter(level, mob, feetCell)`: translates the actual AABB to the candidate cell center and checks collision there without moving the entity.

These methods are used only for physical DROP recovery source/candidate validation. Existing graph cell geometry and general planner semantics are not globally replaced.

### 2. Unique adjacent source resolution

New `NavSourceCellPolicy`:

- Enumerates only four cardinal neighbors at the same feet Y.
- Requires positive horizontal overlap with the real AABB footprint (boundary-only contact is excluded).
- Requires exactly one candidate to pass the supplied physical-validity predicate.
- Rejects ambiguous, unsupported, blocked, or non-overlapping candidates.
- Provides a center-occupancy predicate; mere overlap or `blockPosition()` changing is not arrival.

The executor candidate predicate requires `esPieValido(candidate)` and real-AABB fit at the candidate center. `esPieValido` continues to require air at feet/head, support below, loaded chunk, and non-protected cell.

### 3. Bounded horizontal physical snap

When the mob is grounded but its current integer source is invalid/unsupported:

- Resolve the unique adjacent supported candidate from `mob.getBoundingBox()`.
- Stop vanilla navigation and command ordinary `MoveControl` toward the candidate center.
- Keep the target Y equal to the current mob Y; no jump, vertical impulse, teleport, block placement, or destruction.
- Keep the candidate pending until the mob is physically in that integer cell, within 0.20 blocks of its center, and current feet/support/real-AABB-fit checks pass.
- A pending snap is bounded to 80 ticks; timeout fails closed with an explicit reason.
- Pending state is cleared on recovery reset/mission change.

New trace events:

- `DROP_TAKEOFF_SOURCE_CELL_INVALID`
- `DROP_TAKEOFF_SOURCE_CELL_CANDIDATE`
- `DROP_TAKEOFF_SOURCE_CELL_REJECTED`
- `DROP_TAKEOFF_SOURCE_SNAP_STARTED`
- `DROP_TAKEOFF_SOURCE_SNAP_REACHED`
- `DROP_TAKEOFF_SOURCE_SNAP_FAILED`

### 4. One-block descent needed to return to the original take-off

The forensic geometry has a second necessary detail: resolved source `(593,87,508)` is one block above the original take-off `(593,86,507)`. The previous auxiliary-route policy rejected every DROP transition, so source resolution alone would still have left the recovery route unable to reach its exact destination.

New `NavDropTransitionPolicy.rutaRecuperacionOrigenSegura(route,start,takeoff)` allows:

- WALK, STEP_UP and JUMP_UP transitions already validated by `NavPlanner`.
- At most one DROP transition, and only when its resulting feet Y is exactly one block below the preceding feet Y.
- No multiple/longer drops, BREAK, BRIDGE, BUILD, or other transition types.
- Exact requested take-off destination only.

The original strict `rutaAscensoOrigenSegura` contract remains unchanged.

When the auxiliary route is installed, the executor saves the original route's `plannedFrom` and sets `plannedFrom=start` for the temporary route. This is essential if its first node is the allowed one-block DROP: the DROP actuator must resolve the candidate source as its origin, not the suspended strategic route's original start. The saved value is restored when the auxiliary route ends or is abandoned.

Recovery waypoint and final take-off occupancy now use the real current AABB fit predicate in addition to exact feet occupancy and support.

## Tests added

- `NavSourceCellPolicyTest`: 10 assertions covering unique Z-overlap candidate, z=0.70 boundary-only contact rejection, z=0.95/0.96 overlap, unsupported/blocked candidate rejection, ambiguous candidates, and center occupancy.
- `NavDropTakeoffPolicyTest`: 4 additional assertions covering one exact one-block recovery DROP, rejection of longer/multiple drops, and rejection of destructive transitions.
- `build.gradle`: registered `NavSourceCellPolicyTest` in `policyTest`.

Expected test outputs:

```text
NAV_SOURCE_CELL_POLICY_TEST: OK (10 assertions)
NAV_DROP_TAKEOFF_POLICY_TEST: OK (40 assertions)
NAV_ROUTE_NODE_POLICY_TEST: OK (6 assertions)
```

These are expected counts, **not executed results**. ChatGPT has not run Gradle, lint, diagnostics compilation, or Minecraft runtime.

## Commits

- `42c1f82797e4c6c20e492635f854ee71323fd8df` — real-AABB fit helpers.
- `467167cb61f75a7223112b663a9a835be279cd2c` — grounded source resolver and bounded snap.
- `e2876b5567e4159e1a9fc0affa559d18098e57e0` — unique adjacent AABB source policy.
- `f914b6c67c17dc0224431725c271d4d915dcb049` — source policy regression.
- `b153ab7b5dd4724f1098f847635f5862bd88b6b6` — policy test registration.
- `8dfd53fcf3109566fc8bd5cb43260e024a99569b` — bounded one-block recovery descent policy.
- `330c998700130af8b6292c5fb9b393ff529c01cd` — preserve/restore temporary route origin.
- `46b2aee58dcb1af3a71b65a6261bcbc463fa7751` — recovery descent tests.
- `549b110e7c3b459e11fc9b4a8d9de61d6b5eae66` — real-AABB fit at recovery waypoints/final take-off.
- `dec080512efff2388c58427e4d0927243bf53c79` — candidate rejection test clarification.
- `3e7cb3814936a639ea035a7fffc617b1c7239dd7` — destructive transition test correction.
- `b02affb19eb8e37824682090ba99f793a04aea94` — clear pending snap on recovery reset.

## Required Harness validation

1. Safely synchronize exact `feature/autonomous-village` HEAD and preserve local-only artifacts/worlds.
2. Run:
   - `.\gradlew.bat test`
   - `python tools/lint_aldea.py --strict`
   - `git diff --check`
   - `compileJava -Pa2nDiagnostics=true`
3. Confirm expected policy outputs above.
4. If static validation passes, execute A2 Run 1 only (seed 12345, existing accepted phase/budget settings, disposable world).
5. Capture the entire IDX4 sequence:
   - invalid source and exact AABB;
   - unique candidate and its support block;
   - target AABB fit;
   - every snap command and position delta;
   - physical center arrival;
   - source feet/support/current-AABB fit all true;
   - recovery route and its transition list;
   - if one DROP is present, exact source/landing Y, validated fall depth and physical landing;
   - exact take-off occupancy;
   - suspended route/cursor and `plannedFrom` restoration;
   - original DROP command and physical landing.
6. Assert no teleport, setPos, velocity impulse, jump during source snap, block changes, or weakened support/fit/landing predicates.
7. If A2 Run 1 is not 8/8 strict, do not run Run 2. Do not run full 12k; that still requires five consecutive strict 8/8 runs.

## Scope and gates

- IDX7 BUILD/900-expansion and IDX8 local-waypoint failures are untouched.
- C4 is independent; last run PASS `[1,0,0]`, save/reload remains OPEN.
- A2 remains OPEN pending Harness evidence.
- Original A2/C4 worlds must remain read-only.
- Harness must not modify production source.
