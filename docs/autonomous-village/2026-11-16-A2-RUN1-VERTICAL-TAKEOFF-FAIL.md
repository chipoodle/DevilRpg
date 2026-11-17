# A2 Run 1 — vertical take-off recovery FAIL (2026-11-16)

## Result

**FAIL.** The vertical recovery route is now produced, but it stalls below the target and later plans
exhaust the 900-expansion budget. IDX4 still does not reach its take-off. A2 Run 1 remains 5/8 strict.

- Tested SHA: `38a4b52ee3e336b82dc8e51df17ae42135822747`
- Run: `a2n-20261116-225217`
- Seed 12345, FaseA 3000, FaseB 6000, budgets 900/8000.

## Static validation

| Command | Exit | Result | Log |
|---|---:|---|---|
| `gradlew test` | 0 | PASS | `build/phase19-vertical-gradle.log` |
| `lint_aldea.py --strict` | 0 | PASS | `build/phase19-vertical-lint.log` |
| `git diff --check` | 0 | PASS | terminal |
| `compileJava -Pa2nDiagnostics=true` | 0 | PASS | `build/phase19-vertical-diagcompile.log` |

```text
NAV_DROP_TAKEOFF_POLICY_TEST: OK (16 assertions)
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
| 3 | t3614 | **t3700** |
| 4 | — | — |
| 5 | t653 | **t890** |
| 6 | t224 | **t650** |
| 7 | — | — |
| 8 | — | — |

## IDX4 — recovery route evidence

UUID `7aa773e1-c77f-4236-ba79-3ab877fb2eb6`.

### Initial origin (resolved)

```text
routeIndex=0
routeStart/plannedFrom = 588,107,520
live                  = 588,107,521 (then 588,106,521)
landing               = 588,104,521
resolved              = 588,107,520
```

### Recovery route accepted

```text
DROP_TAKEOFF_RECOVERY_ROUTE_ACCEPTED
 from=588,106,521 takeoff=588,107,520 suspendedIndex=0
 recoveryRoute=len=1 fin=588,107,520
 ultimos=[STEP_UP@588,107,520]
 summary={inicio=588,106,521 fin=588,107,520 H=12 V=6
 exp=1/900 presupuesto=false evaluados=104 encolados=15
 metaEncolada=true alcanzada=true ruta=1}
```

The auxiliary route contains only `STEP_UP` (allowed); no BREAK/BUILD/BRIDGE/DROP.

### Recovery route stalled

```text
DROP_TAKEOFF_RECOVERY_ROUTE_STALLED
 takeoff=588,107,520 node=588,107,520 transition=STEP_UP
 pos=588,104,521 ticksSinAvance=21
```

The mob did not reach `588,107,520`; it stopped at y104 (3 blocks below).

### Later recovery plans rejected

```text
DROP_TAKEOFF_RECOVERY_PLAN_REJECTED
 from=596,78,498 takeoff=596,79,498
 hitBudget=true route=vacia
 summary={exp=901/900 presupuesto=true
 encolados=1369 metaEncolada=false alcanzada=false
 evaluados=90402 ...}
```

A second rejection:

```text
from=595,80,500 takeoff=595,81,500
exp=901/900 presupuesto=true route=vacia metaEncolada=false
```

### Final state

```text
pos=595,79,501; state=RECOVERING
lastAction=595,79,501:DROP-EN-CURSO (salida=595,81,501 dy=-2 construido)
fallosDeDescenso=26
A2_FALLAR_ENTER tick=3364
motivo={construccion agotada sin completar el descenso}
NAV_FAIL pos=596,76,498 rearmes=0/4
```

## Criteria

- Auxiliary route reaches exact origin: **NO** (stalled at 588,104,521 vs 588,107,520).
- Only WALK/STEP_UP/JUMP_UP: **YES** for the accepted route (STEP_UP only).
- No proximity arrival: **YES** (stall detected; no completion).
- No BREAK/BUILD/BRIDGE/DROP in recovery route: **YES** for accepted route.
- Support/collision/clearance maintained: not violated; route simply not executed.
- Original DROP only after physical origin: **NOT REACHED**.
- Physical landing verification: **N/A**, DROP did not proceed.

The failure is now in execution/replanning of the vertical recovery route, not in origin resolution.

## IDX7

UUID `06ded9b6-7b5e-4a1e-a270-2b945cea4e8c`.

```text
pos=705,65,459; BUILD 703,68,457
local 1/64; global 2526/8000; no progress
NAV_FAIL {presupuesto agotado sin progreso (10/10)}
```

## IDX8

UUID `0cb90c60-32da-4dba-a63a-4e2ffeff9271`.

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

- `build/a2n/a2n-20261116-225217/srv.log`
- `build/a2n/a2n-20261116-225217/nav-trace.log`
- `build/a2n/a2n-20261116-225217/fin.txt`
- `build/a2n/a2n-20261116-225217/estado.txt`
- `build/phase19-vertical-gradle.log`, `-lint.log`, `-diagcompile.log`

Original worlds untouched; local-only artifacts preserved.

## Next action

ChatGPT must make the accepted vertical recovery route physically executable (or nudge/step to the
next `STEP_UP` waypoint) and ensure later recovery plans do not exhaust 901/900 while empty. Preserve
height/support/collision guards. IDX7 and IDX8 remain independent.
