# IDX4 grounded-invalid forensics (2026-11-17)

## 1. Scope and tested baseline

- Inspected SHA: `f0be2dacd05c40f840696bc543cefc9dd27dafc4`
- Branch: `feature/autonomous-village`
- Production source for the observed run: `559ba6e3` (landing anchor)
- Source run: `a2n-20261117-101833`
- Run SHA: `f32c32a494cb989c97637b05e38ef6216d37c755`
- IDX4 UUID: `60e65311-464d-4756-a840-b5199b566cf6`

No A2 campaign was executed for this diagnostic. No production code was changed.

## 2. Root cause

**AABB / integer-cell mismatch on the Z boundary.**

At the stable failure position:

```text
exact position = 593.51, 87.00, 507.96
blockPosition  = 593, 87, 507
mob size       = 0.60 x 1.95
```

The actual body footprint is approximately:

```text
X: 593.21 .. 593.81
Y: 87.00 .. 88.95
Z: 507.66 .. 508.26
```

The mob is vertically supported by the **adjacent** cobblestone at `(593,86,508)`, whose top is
`y=87`. Its AABB overlaps that block in Z by about `0.26` blocks. Vanilla physics therefore reports
`onGround=true`.

However, every navigation validator uses the integer cell from `mob.blockPosition()`:

```text
feet cell    = 593,87,507
support cell = 593,86,507  (air)
```

`NavObstruction.hasSupport(level, 593,87,507)` checks `(593,86,507)` only. That block is air, so it
returns `false`. `esPieValido(593,87,507)` also checks `(593,86,507)` and returns `false`.

This is the exact discrepancy:

```text
onGround=true            -> vanilla AABB rests on (593,86,508)
feetValid=false          -> support below integer cell (593,86,507) is air
support=false            -> same integer-cell check
fits=true                -> NavObstruction uses a narrow synthetic cellBox, not the real AABB
```

It is **not** a Y offset: feet Y is exactly `87.00`. It is a Z-boundary resolution problem.

### Why `fits=true`

`NavObstruction.cellBox(pos)` is `x+0.15..x+0.85`, `z+0.15..z+0.85`. For `z=507` this covers
`507.15..507.85`, which does not include the supporting block at z=508. The real AABB extends to
`508.26`. Therefore the fit test does not see the support block and `fits=true` is not evidence that
the current cell is valid.

## 3. Geometry reconstruction

### Nearby blocks established by the A2 trace

At tick 5489, the accepted STEP_UP path reported:

```text
nodes=
593,86,507 {feet=air, head=air, support=cobblestone}
593,87,508 {feet=air, head=air, support=cobblestone}
```

The support shown is the block below each node:

```text
(593,85,507) = cobblestone   -> supports takeoff (593,86,507)
(593,86,508) = cobblestone   -> supports cell (593,87,508)
(593,86,507) = air           -> no support for current integer feet cell
```

Stair placement telemetry:

```text
tick 5292 A2_LOCAL_STAIR_WRITE op=INC_STEP new=1 objective=593,87,508
tick 5509 A2_LOCAL_STAIR_WRITE op=INC_STEP new=2 objective=594,85,507
```

### ASCII geometry (X=593 slice)

```text
Y
89 |  air    air
88 |  air    air
87 |  MOB feet at z507.96; AABB z507.66..508.26
   |   [feet cell 507]   [top of step at 508]
86 |  air    COBBLESTONE (593,86,508)
85 |  COBBLESTONE (593,85,507)
   +------------------------ z
       z=507        z=508
```

The mob straddles the Z boundary. Its integer feet cell is 507; its actual support is in 508.

## 4. Tick reconstruction

| Tick | Observation |
|---:|---|
| 5292 | stair objective `593,87,508` written |
| 5469–5489 | STEP_UP movement toward `593,87,508`; path contains `593,86,507` and `593,87,508` |
| 5509 | first WAIT at `live=593,87,507`, exact z=507.70, `onGround=false`, support=false |
| 5519 | exact z=507.95, **onGround=true**, support=false, feetValid=false, fits=true, anchor=NONE |
| 5519–5989 | repeated unchanged WAIT, 49 events at this cell |
| final result | `RAID RESULTADO idx=4 finalPos=593,87,507`, alive, outside perimeter |

At z=507.70 the AABB max Z is exactly 508.00 (only touching the step). At z=507.95 it is 508.25
(overlapping the step top), which coincides with `onGround` changing to true. This is direct evidence
that the support comes from the z=508 block.

The earlier `A2_FALLAR_ENTER t=5239` was followed by recovery/rearm activity that built the stair and
returned to this edge; the final run state is the 593,87,507 wait. The report records both, but the
forensic root cause is the later stable state.

## 5. Exact code path

### 5.1 Feet validity

`NavExecutor.esPieValido(BlockPos)` (`src/main/java/com/chipoodle/devilrpg/entity/nav/NavExecutor.java:1323`):

```java
if (c == null || !mob.level().hasChunkAt(c)) return false;
if (policies.isProtected(c)) return false;
if (!mob.level().getBlockState(c).isAir()
        || !mob.level().getBlockState(c.above()).isAir()) return false;
return NavObstruction.hasSolidFace(mob.level(), c.below());
```

Input is `mob.blockPosition()` (`NavExecutor.java:3638`), i.e. integer `(593,87,507)`. It checks
`(593,86,507)`, not the actual AABB footprint.

### 5.2 Support

`NavObstruction.hasSupport(BlockGetter, BlockPos)` (`NavObstruction.java:112`):

```java
public static boolean hasSupport(BlockGetter level, BlockPos pos) {
    return hasSolidFace(level, pos.below());
}
```

Again, only `(593,86,507)`.

### 5.3 Fit

`NavObstruction.fits(...)` (`NavObstruction.java:97`) uses `cellBox(pos)` and `upperBox(pos)`.
`cellBox` (`NavObstruction.java:63`) is fixed to `0.15..0.85` in X/Z, smaller than the real 0.60-wide
AABB. It cannot detect a support/collision block that the real AABB overlaps outside that narrow box.

### 5.4 Admission and anchor

`NavStepUpRecoveryPolicy.canStartFrom` (`NavStepUpRecoveryPolicy.java:9`):

```java
return grounded && feetValid && supported && fits;
```

`shouldStabilizeLanding` (`NavStepUpRecoveryPolicy.java:22`):

```java
return !grounded && feetValid && supported && fits;
```

At the failure point: `grounded=true`, `feetValid=false`, `supported=false`, `fits=true`.

- `canStartFrom` = false -> no recovery A*.
- `shouldStabilizeLanding` = false for two independent reasons: mob is grounded, and support is false.
- The executor waits and retries; no movement command is issued.

This is the observed loop. It is not a planner budget failure at this point.

## 6. Candidate recovery matrix

Only cells supported by trace evidence are marked valid; others are UNKNOWN.

| Candidate feet cell | Feet/head | Support block | Fit | Collision | Physical transition | Result |
|---|---|---|---|---|---|---|
| `593,87,507` (current) | air/air | `593,86,507` = air | true (narrow box) | none in narrow box | none | **INVALID**: no integer-cell support |
| `593,87,508` | air/air | `593,86,508` = cobblestone | true (path accepted) | none | WALK +Z ≈0.54 to center | **VALID shortest candidate** |
| `593,86,507` (takeoff) | air/air | `593,85,507` = cobblestone | true (path accepted) | none | after valid source, move to takeoff | valid destination, not source |
| `594,85,507` (landing) | not probed | not probed | unknown | unknown | DROP landing | UNKNOWN |
| `592,87,507` | not probed | not probed | unknown | unknown | WALK | UNKNOWN |

### Physically reachable candidate

`(593,87,508)` is already partially occupied: the AABB crosses into z=508 and rests on its top.
A horizontal `WALK` to the cell center (`z=508.5`) requires about `+0.54` blocks, no jump and no
vertical impulse. Once centered, `blockPosition()` becomes `(593,87,508)`, support at `(593,86,508)`
is the same cobblestone, and `feetValid/support/fits` should all be true.

This is the shortest safe recovery observed:

```text
CURRENT (593.51,87,507.96) --WALK +Z--> (593.50,87,508.50)
```

No teleport, block break/place, jump, or velocity impulse is required.

## 7. Decision chain and cycle

1. DROP route node `597,78,499`? No: for this cycle, the resolved takeoff is `593,86,507` and landing
   `594,85,507`; origin resolves correctly.
2. Original DROP route is suspended at index 0.
3. The executor builds/uses the STEP_UP stair toward `593,87,508`.
4. It reaches the edge at z=507.96, but `mob.blockPosition()` remains z=507.
5. `onGround=true` from the z=508 cobblestone.
6. `esPieValido`/`hasSupport` check z=507 and return false.
7. `fits` uses the narrow cellBox and returns true.
8. `canStartFrom` rejects: support false.
9. `shouldStabilizeLanding` rejects: grounded true and support false.
10. No route, no jump, no movement; wait repeats.
11. Replans exhaust without ever producing a valid source.

This is a **self-locking source-cell cycle**: the mob is physically supported, but the support lies in
an adjacent integer cell not represented by its `blockPosition()`.

## 8. Historical hypotheses

| Prior change | Why it did not resolve IDX4 |
|---|---|
| DROP route-node feet correction | Node/feet height was not this failure; current feet Y is exact |
| Physical take-off recovery | Origin resolves; source admission fails before A* |
| Non-descending DROP | Landing `594,85,507` is below takeoff `593,86,507`; not the issue |
| First-node origin | Origin is `593,86,507`; not missing |
| Vertical take-off recovery | No vertical mismatch; the issue is horizontal cell resolution |
| STEP_UP assist | Assist can run only from a supported source; source is rejected |
| Grounded wait | `onGround=true` but integer-cell support false; waiting cannot change z |
| Landing anchor | Requires airborne + valid support; here grounded + support false; correctly inapplicable |

No previous hypothesis is reused. The remaining untested area is support resolution from the real
AABB footprint rather than the integer `blockPosition()` cell.

## 9. Proposed focused correction for ChatGPT

Do not relax support or accept the current invalid cell for A*. Instead:

1. Add a source-resolution step before `canStartFrom`:
   - Compute the actual AABB footprint (`mob.getBoundingBox()` shrunk slightly to avoid boundary
     contact).
   - If `hasSupport(blockPosition())` is false but exactly one adjacent same-Y cell overlapped by
     the footprint has support and fits, return that cell as a **candidate source**.
2. Move horizontally to the center of that candidate with ordinary `MoveControl` (no jump, no
   vertical velocity, no teleport). Re-evaluate after physical arrival.
3. Only admit A* from the new valid source. Keep `canStartFrom`/`shouldStabilizeLanding` semantics.
4. Replace the synthetic `cellBox` fit check with the real AABB for source validation (at minimum, do
   not use `fits=true` to override a false support result).
5. Add telemetry:
   - `DROP_TAKEOFF_SOURCE_CELL_INVALID`
   - `DROP_TAKEOFF_SOURCE_CELL_CANDIDATE`
   - `DROP_TAKEOFF_SOURCE_SNAP_STARTED`
   - `DROP_TAKEOFF_SOURCE_SNAP_REACHED`

This preserves height, support, collision, clearance and fall-safety guards.

## 10. Required unit tests

1. AABB at z=0.96 with support in `(0,0,1)` and air in `(0,0,0)`:
   - `hasSupport(blockPosition())` false;
   - candidate resolver returns `(0,1,1)`;
   - candidate fits and is supported.
2. z=0.70 (touch only): no candidate selected; onGround false.
3. z=0.95/0.96: candidate selected; source move is horizontal only (dy=0).
4. No support in any overlapped adjacent cell: reject; no false positive.
5. Candidate with support but blocked AABB: reject.
6. `canStartFrom(false source)` stays false; `canStartFrom(resolved candidate)` true.
7. Ensure `fits` uses the real AABB width; a block only under the overhang must not be invisible to
   the check.

## 11. Recommended isolated runtime fixture

Flat platform at y=0, one cobblestone at `(0,0,1)`, air at `(0,0,0)`. Place the mob at
`(0.5,1.0,0.96)` with an air block below `(0,0,0)`. Required sequence:

```text
WAIT/SOURCE_INVALID -> horizontal WALK to z=1.5 -> SOURCE_VALID
-> recovery A* admitted -> exact takeoff arrival -> original DROP resumed
```

No jumps, no block changes, no teleport; assert AABB and integer cell transition.

## 12. Acceptance criteria

- `onGround=true` and source support are reconciled without weakening support.
- The resolver selects exactly the adjacent supported cell, never an unsupported one.
- The mob physically occupies the candidate center (`floor(x/z)` equals the candidate cell).
- A* starts only after source support/feet/fit are all true.
- Original DROP cursor and landing are restored and physically executed.
- If no candidate exists, fail closed and preserve the original route.

## 13. Evidence / artifacts

- `build/a2n/a2n-20261117-101833/nav-trace.log`
- `build/a2n/a2n-20261117-101833/srv.log`
- `build/a2n/a2n-20261117-101833/fin.txt`
- `build/a2n/a2n-20261117-101833/estado.txt`
- Prior reports:
  - `2026-11-17-A2-RUN1-LANDING-ANCHOR-FAIL.md`
  - `2026-11-17-A2-IDX4-LANDING-ANCHOR-FIX.md`
  - `2026-11-17-A2-RUN1-GROUNDED-RECOVERY-FAIL.md`
  - `2026-11-17-A2-RUN1-STEP-UP-ASSIST-FAIL.md`
  - `2026-11-16-A2-RUN1-VERTICAL-TAKEOFF-FAIL.md`

## 14. Classification

**ROOT CAUSE CONFIRMED for the observed source-cell failure.** The remaining question is not whether
the support exists, but how ChatGPT chooses to resolve the actual AABB footprint into a navigation
source cell. The current code cannot do so, so the recovery cannot start.

A2 remains OPEN; no Run 2 or 12k was executed.
