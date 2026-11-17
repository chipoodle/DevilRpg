# Phase19 NAV_BACKTRACK exact arrival — PASS (2026-11-16)

## Result

**PASS.** The backtrack fixture satisfies every required runtime criterion on the exact tested SHA.

- Tested HEAD: `fb93541e5fc216f851f59076942f5ed03947eb92`
- Required test commit: `cc817c6314d2b2055dfbba578c5565e3bff9fedf`
- Run: `a2n-20261116-185434`
- Classification: **DIAGNOSTIC — BREAK/backtrack fixture PASS, not A2 acceptance**

## Static validation

| Command | Exit | Result | Log |
|---|---:|---|---|
| `gradlew test` | 0 | PASS | `build/phase19-backtrack2-gradle.log` |
| `lint_aldea.py --strict` | 0 | PASS | `build/phase19-backtrack2-lint.log` |
| `git diff --check` | 0 | PASS | terminal |
| `compileJava -Pa2nDiagnostics=true` | 0 | PASS | `build/phase19-backtrack2-diagcompile.log` |

`NavArrivalPolicyTest` executed the backtrack assertions:

```text
ok: backtrack adyacente dentro de tolerancia NO se consume
ok: backtrack se consume al ocupar la celda exacta
ok: objetivo no-backtrack conserva llegada generica
ok: nodo de backtrack adyacente NO avanza cursor por tolerancia
ok: nodo de backtrack avanza al ocupar celda exacta
NAV_ARRIVAL_TEST: OK
```

`NavRecoveryControllerTest: OK (10 assertions)` also passed.

## Runtime

`fin.txt`:

```text
COMPLETO (nav-backtrack)
backtrackSelected=true
backtrackSelections=1
backtrackTarget=937,184,608
physicalReturn=true
missionResumed=true
strategicMissionPreserved=true
historySize=6
historyBounded=true
maxStepDistance=0.11418846043238773
noTeleport=true
recovery={history=6 consecutiveFailures=2 backtrackUsed=true
 lastBacktrack=937,184,608 tacticalObjective=-
 strategicMission=946,184,608}
```

Sequence:

```text
NAV_BACKTRACK_FIXTURE_READY start=936,184,608 barrier=940,184,608 goal=946,184,608
BARRIER_PLACED tick=53 mob=939,184,608

NAV_BACKTRACK_SELECTED tick=81 from=939,184,608 target=937,184,608
 history=4 consecutiveFailures=2 reason={planificador sin ruta}

BACKTRACK_ADJACENT_NUDGE tick=94..102
 from=938,184,608 target=937,184,608 manhattan=1
 reason=navigation_done_without_exact_occupancy

BACKTRACK_PROXIMITY_SUPPRESSED
 target=937,184,608 pos=938,184,608 distSqr=0.800

BACKTRACK_NODE_OCCUPANCY_REQUIRED
 target=937,184,608 pos=938,184,608 routeNode=937,184,608 distSqr=0.800

NAV_BACKTRACK_FIXTURE_MISSION_RESUMED tick=103
 pos=937,184,608 backtrackTarget=937,184,608 mission=946,184,608
```

- The mob moved 939 → 938, then used the existing adjacent nudge to step exactly into 937.
- The WALK backtrack node was not consumed by proximity.
- The tactical objective remained until exact occupancy.
- Strategic mission `946,184,608` was then restored.
- History 6 ≤ 16; max position delta 0.114 blocks/tick; no teleport.
- One backtrack selection only.

## Criteria

```text
backtrackSelected=true
backtrackSelections=1
physicalReturn=true
missionResumed=true
strategicMissionPreserved=true
historyBounded=true
noTeleport=true
```

**All satisfied.**

## A2 / C4

- A2: **OPEN**; this is not a siege run. Last known focused: 5/8 strict, 6/8 perimeter.
- Gate of five consecutive 8/8 strict `DENTRO-PARED`: not met.
- Full 12k: not run.
- C4: not run in this fixture; last known focused PASS `[1,0,0]`.
- C4 save/reload: **OPEN**.

## Artifacts

- `build/a2n/a2n-20261116-185434/srv.log`
- `build/a2n/a2n-20261116-185434/nav-trace.log`
- `build/a2n/a2n-20261116-185434/fin.txt`
- `build/a2n/a2n-20261116-185434/estado.txt`
- `build/phase19-backtrack2-gradle.log`
- `build/phase19-backtrack2-lint.log`
- `build/phase19-backtrack2-diagcompile.log`

Original worlds untouched by this fixture. Local-only artifacts preserved.

## Next action

The BREAK success/retry, `BREAK_EDGE_EXCLUDED` and `NAV_BACKTRACK` fixtures are now individually
validated. The next work is the separate A2 focused gate: five consecutive 8/8 strict `DENTRO-PARED`
runs, followed by C4 and (only after that gate) the 12k campaign. A2 remains OPEN.
