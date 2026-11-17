# Phase19 NAV_BACKTRACK exact arrival — FAIL (2026-11-16)

## Result

**FAIL.** The new exact-arrival guard correctly suppresses generic proximity consumption, but the mob
still never steps from `938,184,608` into the selected historical cell `937,184,608`.

- Tested HEAD: `e2fe0873e956ee4607207042c1fad3d79299942e`
- Source checkpoint: `889072d235a44d0efc2de7ad955333b39b1d8b03`
- Run: `a2n-20261116-182234`
- Classification: **DIAGNOSTIC — NOT ACCEPTANCE**

## Static validation

| Command | Exit | Result | Log |
|---|---:|---|---|
| `gradlew test` | 0 | PASS | `build/phase19-backtrackfix-gradle.log` |
| `lint_aldea.py --strict` | 0 | PASS | `build/phase19-backtrackfix-lint.log` |
| `git diff --check` | 0 | PASS | terminal |
| `compileJava -Pa2nDiagnostics=true` | 0 | PASS | `build/phase19-backtrackfix-diagcompile.log` |

The three new `NavArrivalPolicyTest` backtrack assertions executed and passed:

```text
ok: backtrack adyacente dentro de tolerancia NO se consume
ok: backtrack se consume al ocupar la celda exacta
ok: objetivo no-backtrack conserva llegada generica
NAV_ARRIVAL_TEST: OK
```

`NavRecoveryControllerTest: OK (10 assertions)` also passed.

## Runtime

`fin.txt`:

```text
FAIL (nav-backtrack) - backtrack fixture timeout ticks=1800
barrierPlaced=true selected=true objective=937,184,608 pos=938,184,608
recovery={history=5 consecutiveFailures=2 backtrackUsed=true
 lastBacktrack=937,184,608 tacticalObjective=937,184,608
 strategicMission=946,184,608}
execution={state=TRAVERSING obj=937,184,608
 route=len=2 fin=937,184,608 ultimos=[WALK@938,184,608 WALK@937,184,608]
 idx=2 replans=0 rec=0}
```

Sequence:

```text
NAV_BACKTRACK_FIXTURE_BARRIER_PLACED tick=79 mob=939,184,608
NAV_BACKTRACK_FIXTURE_SELECTED tick=107 current=939,184,608
 target=937,184,608
 history=4 consecutiveFailures=2
 historyBounded=true targetDistinct=true missionPreserved=true
```

The mob moved from 939 to 938 and stopped. The route to 937 completed (`idx=2`,
`ultimos=[WALK@938, WALK@937]`), but the physical cell remained `938`.

`BACKTRACK_PROXIMITY_SUPPRESSED` fires repeatedly:

```text
BACKTRACK_PROXIMITY_SUPPRESSED
 target=937,184,608 pos=938,184,608 distSqr=1.935
```

Thus the exact-arrival guard works: it prevents early clearing. It does **not** provide a movement
mechanism for the final adjacent block.

There is no `NAV_ADJACENT_NUDGE`/`BREAK_STAND_NUDGE`-equivalent for the backtrack target.

## Exact remaining defect for ChatGPT

File: `src/main/java/com/chipoodle/devilrpg/entity/nav/NavExecutor.java`.

- Backtrack selection (`intentarBacktrackTrasFallo`) sets `recoveryBacktrackObjective=937` and the
  tactical objective.
- `BACKTRACK_PROXIMITY_SUPPRESSED` correctly keeps the objective at distance 1.935.
- The local route ends with `WALK@938 -> WALK@937`, but no adjacent nudge is emitted when
  `navigation.isDone()` and `blockPosition` is one cell from `recoveryBacktrackObjective`.
- The generic `NavAdjacentApproachPolicy` nudge is used for BREAK stands and normal adjacent nodes, not
  for the backtrack objective.

Required correction: apply the existing adjacent-approach nudge (or equivalent exact-step movement)
to `recoveryBacktrackObjective` when navigation is done but the mob is not physically in that cell.
Only after exact occupancy may the tactical objective be cleared and the mission restored.

## Criteria status

```text
backtrackSelected=true
backtrackSelections=1
physicalReturn=false
missionResumed=false
strategicMissionPreserved=true
historyBounded=true
noTeleport=true (no teleport observed; movement stalled)
```

## A2 / C4

- A2: OPEN; last known focused 5/8 strict, 6/8 perimeter.
- Full 12k: not run.
- C4: not run; last known PASS `[1,0,0]`.
- Save/reload: OPEN.

## Artifacts

- `build/a2n/a2n-20261116-182234/srv.log`
- `build/a2n/a2n-20261116-182234/nav-trace.log`
- `build/a2n/a2n-20261116-182234/fin.txt`
- `build/a2n/a2n-20261116-182234/estado.txt`
- `build/phase19-backtrackfix-gradle.log`, `-lint.log`, `-diagcompile.log`

Original worlds untouched. Local-only artifacts preserved.

## Next action

ChatGPT should add the adjacent nudge/exact-step for `recoveryBacktrackObjective` and publish a new SHA.
Harness will rerun `nav-backtrack` and require:

```text
backtrackSelected=true
backtrackSelections=1
physicalReturn=true
missionResumed=true
strategicMissionPreserved=true
historyBounded=true
noTeleport=true
```
