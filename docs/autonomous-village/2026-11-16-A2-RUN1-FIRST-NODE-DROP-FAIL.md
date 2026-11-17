# A2 Run 1 — first-node DROP origin recovery FAIL (2026-11-16)

## Result

**FAIL.** The first-node origin is now resolved and the recovery command is emitted, but the mob
cannot physically ascend to it. A2 Run 1 remains 5/8 strict.

- Tested SHA: `b73ca2de14c676f3f83b6b23bcb9f219205f97a8`
- Run: `a2n-20261116-213836`
- Seed 12345, FaseA 3000, FaseB 6000, budgets 900/8000.

## Static validation

| Command | Exit | Result | Log |
|---|---:|---|---|
| `gradlew test` | 0 | PASS | `build/phase19-firstdrop-gradle.log` |
| `lint_aldea.py --strict` | 0 | PASS | `build/phase19-firstdrop-lint.log` |
| `git diff --check` | 0 | PASS | terminal |
| `compileJava -Pa2nDiagnostics=true` | 0 | PASS | `build/phase19-firstdrop-diagcompile.log` |

```text
NAV_DROP_TAKEOFF_POLICY_TEST: OK (12 assertions)
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
| 1 | t387 | **t512** |
| 2 | t242 | **t667** |
| 3 | t871 | **t1066** |
| 4 | — | — |
| 5 | t451 | **t681** |
| 6 | t224 | **t649** |
| 7 | — | — |
| 8 | — | — |

## IDX4 — origin resolved, vertical recovery blocked

UUID `087a666e-4d82-4795-a237-d9b92592263d`.

```text
routeIndex=0
routeStart/plannedFrom = 589,96,522
live position          = 589,93,522
landing                = 589,95,521
```

Event:

```text
DROP_TAKEOFF_ORIGIN_RESOLVED routeIndex=0
  routeStart=589,96,522 live=589,93,522 landing=589,95,521
  resolved=589,96,522
```

Recovery command was emitted:

```text
ACERCARSE-ORIGEN-DROP -> requested=589.50,96.00,522.50
pos=589.50,93.00,522.53
path=BlockPos{589,93,522}/n=1/next=1/canReach=true
navDone=true; movement delta=0
```

The requested origin is 3 blocks above the live feet. PathNavigation returns a one-node path already
at the current block and the mob does not ascend. No physical origin arrival occurs.

Final:

```text
pos=589,93,522; state=RECOVERING
lastAction=589,95,521:DROP-EN-CURSO (salida=589,96,521 dy=-1 natural)
fallosDeDescenso=18
global exp=8000/8000 presupuesto=true
local objetivo=596,97,519 BUILD; escalones=3/64
```

So Case A is only partially satisfied: origin resolved and command emitted, but **physical origin
reached = false**; the DROP is not executed. The blocker is vertical movement to the take-off
(`y93 -> y96`), not origin resolution.

No `DROP_REJECTED_NON_DESCENDING` applies here; landing y95 < takeoff y96 is descending.

## IDX7

UUID `371ec7c4-364b-4e0c-9bb5-044636b7ad25`.

```text
pos=705,65,459; BUILD 703,68,457
local 1/64; global 2526/8000; no progress
NAV_FAIL {presupuesto agotado sin progreso (10/10)}
```

## IDX8

UUID `435ae971-5cd4-4d81-9ecc-cb0e50318f88`.

```text
pos=769,63,497
NAV_FAIL {sin objetivo local valido (10/10):
 ruta global sin waypoint de pie VALIDO ... [intento 10/10]}
rearmes=4/4; global exp=8000/8000; rutaFrente=81
```

## C4

- PASS `[1,0,0]`.
- 12/12 resumed; no casualties/unresolved workers.
- Save/reload OPEN.

## Gate

- Consecutive 8/8 strict runs: **0**.
- Run 1 FAIL; no Run 2.
- A2 remains OPEN.

## Artifacts

- `build/a2n/a2n-20261116-213836/srv.log`
- `build/a2n/a2n-20261116-213836/nav-trace.log`
- `build/a2n/a2n-20261116-213836/fin.txt`
- `build/a2n/a2n-20261116-213836/estado.txt`
- `build/phase19-firstdrop-gradle.log`, `-lint.log`, `-diagcompile.log`

Original worlds untouched; local-only artifacts preserved.

## Next action

ChatGPT must make the vertical recovery from live y93 to take-off y96 executable (jump/step/build
mechanism or a valid reachable route) while preserving height/support/collision guards. The origin
resolution and command are working; physical ascent is not. IDX7 and IDX8 remain independent.
