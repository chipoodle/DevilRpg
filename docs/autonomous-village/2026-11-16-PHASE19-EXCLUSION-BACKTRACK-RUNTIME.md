# Phase19 exclusion + backtrack runtime (2026-11-16)

## Result

- `break-edge-excluded`: **COMPLETO**
- `nav-backtrack`: **FAIL** — backtrack selected, but physical return stopped one block short and the
  strategic mission did not resume.

Tested HEAD: `7e7ab1fe191fee0f2ba7bc387ec5a4e7192f5470`.
Both are DIAGNOSTIC fixtures, not A2 acceptance.

## Static validation

| Command | Exit | Result | Log |
|---|---:|---|---|
| `gradlew test` | 0 | PASS | `build/phase19-fixtures-gradle.log` |
| `lint_aldea.py --strict` | 0 | PASS | `build/phase19-fixtures-lint.log` |
| `git diff --check` | 0 | PASS | terminal |
| `compileJava -Pa2nDiagnostics=true` | 0 | PASS | `build/phase19-fixtures-diagcompile.log` |

`NavRecoveryControllerTest: OK (10 assertions)` confirmed.
All other policy tests also passed (contract 5, graph 7, route 11, attack 7, action 8, exclusion 6,
recovery guard 5).

## BREAK_EDGE_EXCLUDED — COMPLETO

Run **`a2n-20261116-180242`** (disposable world).

```text
COMPLETO (break-edge-excluded) - calls=2 retryDeltaTicks=40
retryTimingExact=true excludedPairNotReused=true alternativeSelected=true
rejectedBlockStillSolid=true alternativeBlockStillSolid=true
fingerprintExclusionActive=true exclusionRecorded=true exclusionCount=1
firstPairMatchedPreflight=true secondPairMatched=true
unexpectedActuatorCalls=0 routeIndex=0 breakIndex=0
strategicMissionPreserved=true
```

Events:

```text
BREAK_EDGE_FIXTURE_READY start=816,184,588 goal=823,184,588
  expectedStand=816,184,587 expectedTarget=817,184,588

BREAK_EDGE_FIXTURE_FIRST tick=29
  stand=816,184,587 target=817,184,588 result=UNVERIFIED

BREAK_ACTUATOR_RETRY_WAIT ... retryAt=69 reason=first_unverified_attempt

BREAK_EDGE_FIXTURE_SECOND tick=69 delta=40
  stand=816,184,587 target=817,184,588 samePair=true destroyed=false

BREAK_EDGE_EXCLUDED ... stand=816,184,587 target=817,184,588
  fingerprint=15647821158459134 exclusions=1
  reason={segunda accion no verificada (invoked=false)}

BREAK_EDGE_FIXTURE_ALTERNATIVE_SELECTED
  stand=816,184,586 target=817,184,587
  rejectedStillSolid=true alternativeStillSolid=true exclusionCount=1
```

All required exclusion criteria are satisfied. Route index remained at 0, rejected and alternative
blocks remained solid, original strategic objective preserved, and there were no unexpected actuator
calls.

## NAV_BACKTRACK — FAIL

Run **`a2n-20261116-180555`** (separate disposable world).

```text
FAIL (nav-backtrack) - backtrack fixture timeout ticks=1800
barrierPlaced=true selected=true objective=946,184,608 pos=938,184,608
recovery={history=5 consecutiveFailures=9 backtrackUsed=true
 lastBacktrack=937,184,608 tacticalObjective=- strategicMission=946,184,608}
execution={state=RECOVERING obj=946,184,608 route=- idx=0 replans=7 rec=6}
```

Relevant sequence:

```text
NAV_BACKTRACK_FIXTURE_READY start=936,184,608 barrier=940,184,608 goal=946,184,608

NAV_BACKTRACK_FIXTURE_BARRIER_PLACED tick=53 barrier=940,184,608 mob=939,184,608

NAV_BACKTRACK_SELECTED tick=81 from=939,184,608 target=937,184,608
 history=4 consecutiveFailures=2 reason={planificador sin ruta}
NAV_BACKTRACK_FIXTURE_SELECTED ... historyBounded=true targetDistinct=true
 missionPreserved=true tacticalObjective=937,184,608

SAMPLE tick=120 pos=938,184,608 state=RECOVERING
 recovery tacticalObjective=- lastBacktrack=937,184,608
 mission=946,184,608
```

The mob moved from `939` to `938`, then the tactical objective was cleared while the selected
backtrack target was `937`. Position never reached `937`; no `physicalReturn=true`, no mission
`RESUMED` event, and the fixture timed out in RECOVERING.

### Exact defect for ChatGPT

File/method: `NavExecutor` arrival handling (`travesia`/tick path around the block using
`ARRIVE_DISTANCE_SQR` and `NavArrivalPolicy.decidir`), plus the backtrack selection in
`intentarBacktrackTrasFallo`.

The generic arrival tolerance (`ARRIVE_DISTANCE_SQR`, ~1.5 blocks) considers the mob at `938` arrived
at tactical target `937` (distance 1) and clears `recoveryBacktrackObjective`/`objetivoTactico` before
exact physical occupancy. Consequently:

- `backtrackSelected=true`
- `physicalReturn=false`
- `missionResumed=false`
- `strategicMission` remains `946,184,608`, but executor stays RECOVERING with no route.

The fix must require exact cell occupancy (or an explicit adjacent nudge to the backtrack cell) before
clearing the one-shot backtrack and restoring the mission. Do not change cooldown, budgets or acceptance.

## Artifacts

Exclusion:
- `build/a2n/a2n-20261116-180242/srv.log`, `nav-trace.log`, `fin.txt`, `estado.txt`

Backtrack:
- `build/a2n/a2n-20261116-180555/srv.log`, `nav-trace.log`, `fin.txt`, `estado.txt`

Static logs:
- `build/phase19-fixtures-gradle.log`, `-lint.log`, `-diagcompile.log`

Original A2/C4 worlds were not used or modified. Local-only artifacts were preserved.

## A2 / C4

- A2: OPEN; last known siege 5/8 strict, 6/8 perimeter.
- Full 12k: not run.
- C4: not run; last known PASS `[1,0,0]`.
- Save/reload: OPEN.
