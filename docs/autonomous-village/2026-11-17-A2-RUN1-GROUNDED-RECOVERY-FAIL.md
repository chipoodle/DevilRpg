# A2 Run 1 — grounded take-off recovery FAIL (2026-11-17)

## Result

**FAIL.** Grounded admission works (no invalid airborne acceptance or assist), but IDX4 never gets a
grounded source for recovery. A2 Run 1 is 5/8 strict and C4 is FAIL `[0,0,0]`.

- Tested SHA: `b3064596d457e6e7877607a534f8889a4214045d`
- Production SHA: `190bb6578906068e33c3a835feb2cacf7bc93085`
- Run: `a2n-20261117-020800`
- Seed 12345, FaseA 3000, FaseB 6000, budgets 900/8000.

## Static validation

| Command | Exit | Result | Log |
|---|---:|---|---|
| `gradlew test` | 0 | PASS | `build/phase19-grounded-gradle.log` |
| `lint_aldea.py --strict` | 0 | PASS | `build/phase19-grounded-lint.log` |
| `git diff --check` | 0 | PASS | terminal |
| `compileJava -Pa2nDiagnostics=true` | 0 | PASS | `build/phase19-grounded-diagcompile.log` |

```text
NAV_DROP_TAKEOFF_POLICY_TEST: OK (32 assertions)
NAV_ROUTE_NODE_POLICY_TEST: OK (6 assertions)
```

## A2 Run 1

- 8/8 spawned.
- **5/8 perimeter.**
- **5/8 strict.**
- **C4 FAIL** `[0,0,0]` (12/12 resumed, but assignmentsClean false).
- Run 2 not executed.

| idx | Perimeter | Strict |
|---|---:|---:|
| 1 | t387 | **t513** |
| 2 | t242 | **t668** |
| 3 | t1679 | **t1780** |
| 4 | — | — |
| 5 | t814 | **t1042** |
| 6 | t224 | **t650** |
| 7 | — | — |
| 8 | — | — |

## IDX4

UUID `799074f7-be71-4885-b421-a14210d98bcf`.

### Grounded admission events

```text
DROP_TAKEOFF_RECOVERY_WAIT_GROUNDED
 live=567,121,534 exact=567.47,121.54,534.47
 onGround=false feetValid=false support=false fits=true
 takeoff=566,122,533

DROP_TAKEOFF_RECOVERY_WAIT_GROUNDED
 live=571,115,525 exact=571.51,115.77,525.54
 onGround=false feetValid=false support=false fits=true
 takeoff=571,116,526

DROP_TAKEOFF_RECOVERY_WAIT_GROUNDED
 live=585,105,516 exact=585.51,105.85,516.50
 onGround=false feetValid=true support=true fits=true
 takeoff=585,106,516
```

Only WAIT_GROUNDED occurs for this UUID. There is:

```text
No DROP_TAKEOFF_RECOVERY_ROUTE_ACCEPTED
No DROP_TAKEOFF_STEP_UP_ASSIST
No DROP_TAKEOFF_RECOVERY_REACHED
```

Final:

```text
pos=596,76,498
NAV_FAIL {construccion agotada sin completar el descenso}
rearmes=0/4
fallosDeDescenso=21
```

The guards correctly reject/air-wait: none of the `ROUTE_ACCEPTED` events has `onGround=false`, and no
assist was emitted while airborne. But the mob never finds a grounded, supported source, so recovery
never starts.

### Budget

`901/900` occurrences: **0**. Recovery/plan budgets remain within 900; no forced expansion.

## IDX7

UUID `9c9a47dd-5250-431b-92f4-2cececb62255`.

```text
pos=705,65,459; BUILD 703,68,457
local 1/64; global 2526/8000; no progress
NAV_FAIL {presupuesto agotado sin progreso (10/10)}
```

## IDX8

UUID `f03f9ae6-5ed7-4490-a17c-cfd163b70f5b`.

```text
pos=769,63,497; state=FAILED
NAV_FAIL {sin objetivo local valido (10/10):
 ruta global sin waypoint de pie VALIDO ... [intento 10/10]}
rearmes=4/4; global exp=8000/8000; rutaFrente=81
```

A valid grounded STEP_UP route was accepted (`743,62,534 -> 744,63,534`, onGround/support/fit true)
and `DROP_TAKEOFF_STEP_UP_AIRBORNE` appeared, but the later global/local mission route still failed.

## C4

- **FAIL** `[0,0,0]` (`assignmentsClean=false`).
- 12/12 workers resumed; no casualties/unresolved.
- Save/reload OPEN.

## Gate

- Consecutive 8/8 strict runs: **0**.
- A2 remains OPEN.
- Run 2 not executed; full 12k not executed.

## Artifacts

- `build/a2n/a2n-20261117-020800/srv.log`
- `build/a2n/a2n-20261117-020800/nav-trace.log`
- `build/a2n/a2n-20261117-020800/fin.txt`
- `build/a2n/a2n-20261117-020800/estado.txt`
- `build/phase19-grounded-gradle.log`, `-lint.log`, `-diagcompile.log`

Original worlds untouched; local-only artifacts preserved.

## Next action

ChatGPT must make IDX4 find/reach a grounded supported source cell for take-off recovery. The current
guards are safe but only emit WAIT_GROUNDED; no recovery route starts. C4 `[0,0,0]` is an independent
acceptance failure and must not be hidden by the navigation result.
