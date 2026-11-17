# A2 Run 1 — landing anchor FAIL (2026-11-17)

## Result

**FAIL.** The landing-anchor code is present and static tests pass, but IDX4 waits on an invalid,
unsupported feet cell (`landingAnchor=NONE`), so no recovery route is admitted. A2 Run 1 remains
5/8 strict.

- Tested SHA: `f32c32a494cb989c97637b05e38ef6216d37c755`
- Production source: `559ba6e3` (landing-anchor fix)
- Run: `a2n-20261117-101833`
- Seed 12345, FaseA 3000, FaseB 6000, budgets 900/8000.

## Static validation

| Command | Exit | Result | Log |
|---|---:|---|---|
| `gradlew test` | 0 | PASS | `build/phase19-anchor-gradle.log` |
| `lint_aldea.py --strict` | 0 | PASS | `build/phase19-anchor-lint.log` |
| `git diff --check` | 0 | PASS | terminal |
| `compileJava -Pa2nDiagnostics=true` | 0 | PASS | `build/phase19-anchor-diagcompile.log` |

```text
NAV_DROP_TAKEOFF_POLICY_TEST: OK (36 assertions)
NAV_ROUTE_NODE_POLICY_TEST: OK (6 assertions)
```

## A2 Run 1

- 8/8 spawned.
- **5/8 perimeter.**
- **5/8 strict `DENTRO-PARED`.**
- C4 **PASS** `[1,0,0]`, 12/12 resumed.
- Run 2 not executed.

| idx | Perimeter | Strict |
|---|---:|---:|
| 1 | t387 | **t513** |
| 2 | t242 | **t668** |
| 3 | t1772 | **t1869** |
| 4 | — | — |
| 5 | t816 | **t1044** |
| 6 | t224 | **t650** |
| 7 | — | — |
| 8 | — | — |

## IDX4

UUID `60e65311-464d-4756-a840-b5199b566cf6`.

### WAIT_GROUNDED sample

```text
live=593,87,507 exact=593.51,87.00,507.96
velocity=0.000,-0.078,0.000
onGround=true
feetValid=false
support=false
fits=true
landingAnchor=NONE
takeoff=593,86,507
```

Repeated without change. The mob is grounded and horizontally stable (vx/vz = 0), but the current
feet cell is invalid/unsupported, so `shouldStabilizeLanding` does not apply an anchor.

No events for this UUID:

```text
DROP_TAKEOFF_RECOVERY_ROUTE_ACCEPTED: NOT OBSERVED
DROP_TAKEOFF_STEP_UP_ASSIST:          NOT OBSERVED
DROP_TAKEOFF_RECOVERY_REACHED:        NOT OBSERVED
```

Final:

```text
pos=595,79,501
A2_FALLAR_ENTER tick=5239
motivo={replans agotados (8/8)}
rearmes=1/4
```

The anchor logic is safe but inapplicable here: it requires a valid/validly supported feet cell, and
the current cell has `feetValid=false support=false`. The mob never obtains a legal recovery source.

## IDX7

UUID `228034bd-97ff-4478-8f74-0a1052686a1f`.

```text
pos=705,65,459; BUILD 703,68,457
local 1/64; global 2527/8000; no progress
NAV_FAIL {presupuesto agotado sin progreso (10/10)}
```

## IDX8

UUID `12039dc5-e701-4bbd-bdbb-e5dcce0f1053`.

```text
pos=769,63,497; state=FAILED
NAV_FAIL {sin objetivo local valido (10/10):
 ruta global sin waypoint de pie VALIDO ... [intento 10/10]}
rearmes=4/4; global exp=8000/8000; rutaFrente=81
```

## C4

- PASS `[1,0,0]`.
- 12/12 resumed; no casualties/unresolved.
- Save/reload OPEN.

## Gate

- Consecutive 8/8 strict runs: **0**.
- A2 remains OPEN.
- Full 12k not executed.

## Artifacts

- `build/a2n/a2n-20261117-101833/srv.log`
- `build/a2n/a2n-20261117-101833/nav-trace.log`
- `build/a2n/a2n-20261117-101833/fin.txt`
- `build/a2n/a2n-20261117-101833/estado.txt`
- `build/phase19-anchor-gradle.log`, `-lint.log`, `-diagcompile.log`

Original worlds untouched; local-only artifacts preserved.

## Next action

ChatGPT must make IDX4 obtain a grounded, supported, valid feet cell for landing-anchor/recovery. A
horizontal anchor alone cannot help when `feetValid=false support=false`; the executor needs a safe
way to reach a valid supported source without teleport or arbitrary impulse. C4 is PASS in this run;
IDX7/IDX8 remain independent.
