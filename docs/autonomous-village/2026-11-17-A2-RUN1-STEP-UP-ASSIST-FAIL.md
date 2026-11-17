# A2 Run 1 — STEP_UP recovery assist FAIL (2026-11-17)

## Result

**FAIL.** The STEP_UP assist is emitted and expansion budgets stay within 900, but IDX4 still stalls
below its take-off. A2 Run 1 reaches 4/8 strict.

- Tested SHA: `7aed2c683852969c323524bbed7d95965c330790`
- Production source: `491b7d0170501e9d86433a52d2703395a4029b06`
- Run: `a2n-20261117-001624`
- Seed 12345, FaseA 3000, FaseB 6000, budgets 900/8000.

## Static validation

| Command | Exit | Result | Log |
|---|---:|---|---|
| `gradlew test` | 0 | PASS | `build/phase19-stepup-gradle.log` |
| `lint_aldea.py --strict` | 0 | PASS | `build/phase19-stepup-lint.log` |
| `git diff --check` | 0 | PASS | terminal |
| `compileJava -Pa2nDiagnostics=true` | 0 | PASS | `build/phase19-stepup-diagcompile.log` |

```text
NAV_DROP_TAKEOFF_POLICY_TEST: OK (25 assertions)
NAV_ROUTE_NODE_POLICY_TEST: OK (6 assertions)
```

## A2 Run 1

- 8/8 spawned.
- **5/8 perimeter.**
- **4/8 strict.**
- C4 **PASS** `[1,0,0]`, 12/12 resumed.
- Run 2 not executed.

| idx | Perimeter | Strict |
|---|---:|---:|
| 1 | t387 | **t513** |
| 2 | t242 | **t668** |
| 3 | t1529 | **t1615** |
| 4 | — | — |
| 5 | t615 | **t852** |
| 6 | t224 | — |
| 7 | — | — |
| 8 | — | — |

## IDX4

UUID `384b713d-214d-4577-a1c7-73dc7bdf6700`.

### Assist event

```text
DROP_TAKEOFF_STEP_UP_ASSIST
 from=567,121,534 target=566,122,534
 onGround=false support=true fits=true
```

The allowed assist was invoked for an adjacent one-block step with support/fit.

### Physical result

```text
DROP_TAKEOFF_RECOVERY_ROUTE_STALLED
 takeoff=566,122,533 node=566,122,534 transition=STEP_UP
 pos=567,119,534 ticksSinAvance=21
```

The mob remained at y119, three blocks below the target y122. It did not occupy the waypoint.

Other stalls on the recovery path:

```text
takeoff=568,119,525 node=576,112,526 transition=WALK
 pos=576,112,526 ticksSinAvance=21

takeoff=588,107,520 node=588,107,520 transition=STEP_UP
 pos=588,104,521 ticksSinAvance=21
```

Final:

```text
pos=595,79,501; state=RECOVERING
lastAction=597,78,499:DROP-EN-CURSO (salida=597,79,499 dy=-1 natural)
fallosDeDescenso=26
global exp=8000/8000 presupuesto=true
```

No `DROP_TAKEOFF_RECOVERY_REACHED` for IDX4. The DROP original did not proceed.

### Budget

No `901/900` records were observed in this run. All recovery summaries stayed at `<=900`; the
budget correction is effective. The remaining failure is physical execution of the STEP_UP assist
and subsequent waypoints, not budget overrun.

## IDX7

UUID `894e1308-526d-4caf-be14-d2f68e4de4bd`.

```text
pos=705,65,459; BUILD 703,68,457
local 1/64; global 2527/8000; no progress
NAV_FAIL {presupuesto agotado sin progreso (10/10)}
```

## IDX8

UUID `c39353a5-2ccb-4cb7-8615-8d3c640d9297`.

Interesting: IDX8 reached a STEP_UP recovery for takeoff `747,64,534`
(`DROP_TAKEOFF_RECOVERY_REACHED`), but still terminated:

```text
pos=769,63,497; state=FAILED
NAV_FAIL {sin objetivo local valido (10/10):
 ruta global sin waypoint de pie VALIDO ... [intento 10/10]}
rearmes=4/4; global exp=8000/8000; rutaFrente=81
```

Its recovery reached a local take-off, but the global/local mission route still failed.

## C4

- PASS `[1,0,0]`.
- 12/12 resumed; no casualties/unresolved workers.
- Save/reload OPEN.

## Gate

- Consecutive 8/8 strict runs: **0**.
- Run 1 FAIL; no Run 2.
- A2 remains OPEN.

## Artifacts

- `build/a2n/a2n-20261117-001624/srv.log`
- `build/a2n/a2n-20261117-001624/nav-trace.log`
- `build/a2n/a2n-20261117-001624/fin.txt`
- `build/a2n/a2n-20261117-001624/estado.txt`
- `build/phase19-stepup-gradle.log`, `-lint.log`, `-diagcompile.log`

Original worlds untouched; local-only artifacts preserved.

## Next action

ChatGPT must make the STEP_UP assist physically move the mob from `567,119` to the `566,122` waypoint
(and execute subsequent WALK/STEP_UP waypoints) without teleport or arbitrary vertical impulse. The
assist policy/budget now behave; execution remains stalled. IDX7 and IDX8 remain independent.
