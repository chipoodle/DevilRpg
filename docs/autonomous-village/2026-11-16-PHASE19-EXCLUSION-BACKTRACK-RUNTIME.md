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


## ChatGPT correction — exact physical backtrack arrival (PENDING HARNESS)

Source checkpoint: `889072d235a44d0efc2de7ad955333b39b1d8b03` (the preceding two commits in this correction sequence are `29747f62dd4a134602307152c1ec3799cacc8e75` and `224695203cf0eecffb1d72c30cff41c3b3ea5efe`).

Root cause confirmed in the production arrival shortcut: `recoveryBacktrackObjective` was cleared in the same generic `ARRIVE_DISTANCE_SQR` branch used for ordinary tactical objectives. At position `938,184,608`, target `937,184,608`, the one-block separation passed that tolerance and discarded the one-shot backtrack before physical cell occupancy.

Correction published by ChatGPT:

- Added pure policy `NavArrivalPolicy.backtrackArrivalAllowed(pendingBacktrack, exactCellOccupied)`; a pending backtrack cannot be consumed by distance tolerance.
- In `NavExecutor`, a backtrack is recognized only while `recoveryBacktrackObjective.equals(objective)`; its completion requires `mob.blockPosition().equals(recoveryBacktrackObjective)`.
- The generic arrival branch now requires both the existing stair gate and the new backtrack gate. Ordinary tactical objectives, stairs, cooldowns, budgets, recovery limits and acceptance thresholds are unchanged.
- Added three pure assertions: adjacent-but-within-tolerance is rejected; exact-cell occupancy is accepted; non-backtrack objectives retain generic arrival.
- Added bounded `BACKTRACK_PROXIMITY_SUPPRESSED` trace (at most every 20 ticks) while the mob is within generic tolerance but not in the exact selected cell.

No Gradle, lint, diff-check, diagnostics compile or runtime validation was run by ChatGPT. This correction is **PENDING HARNESS**, not PASS.

### Required Harness validation

1. Fetch live `feature/autonomous-village` HEAD; verify it contains source checkpoint `889072d2` and preserve all local-only artifacts. No reset/clean/stash/rebase/force-push.
2. Run `.\\gradlew.bat test`, `python tools/lint_aldea.py --strict`, `git diff --check`, and `.\\gradlew.bat compileJava -Pa2nDiagnostics=true`.
3. Execute only the disposable `nav-backtrack` fixture with `-Da2n.mode=nav-backtrack -Ddevilrpg.a2n.traceNav=true`.
4. Require `backtrackSelected=true`, `backtrackSelections=1`, `physicalReturn=true`, `missionResumed=true`, `strategicMissionPreserved=true`, `historyBounded=true`, and `noTeleport=true`. Trace must show the proximity suppression at `938` (or another adjacent cell) and exact physical occupancy at `937,184,608` before the tactical objective is cleared.
5. Do not rerun or modify the already passing `BREAK_EDGE_EXCLUDED` fixture unless the new changes cause a regression. Do not run full 12k. A2 remains OPEN until five consecutive strict 8/8 `DENTRO-PARED` runs; C4 save/reload remains OPEN.
