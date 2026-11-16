# Phase19 LOS correction — runtime result (2026-11-16)

## Result

**FAIL (behavioral)** — the LOS correction itself works, but the deterministic BREAK fixture times
out before either actuator runs. The fixture's own `fin.txt` labels the timeout `BLOCKED`; the observed
behavior is a runtime navigation failure, not an environmental blocker.

- Tested tree HEAD: `76d3466cfda5f3faa37da8961aa4e3a6e7ca5fba`
- LOS correction source: `9200ac31a350d9013b2abb8f39ce2b421fc9605a`
- Run: `a2n-20261116-163239`
- Classification: **DIAGNOSTIC — NOT ACCEPTANCE**

## Static validation (HEAD 76d3466c)

| Command | Exit | Result | Log |
|---|---:|---|---|
| `gradlew test` | 0 | PASS | `build/phase19-los-gradle.log` |
| `lint_aldea.py --strict` | 0 | PASS | `build/phase19-los-lint.log` |
| `git diff --check` | 0 | PASS | terminal |
| `compileJava -Pa2nDiagnostics=true` | 0 | PASS | `build/phase19-los-diagcompile.log` |

All policy tests executed:

```text
NavBreakContractTest:               OK (5)
NavBreakGraphStateTest:             OK (7)
NavBreakRoutePolicyTest:            OK (11)
NavBreakAttackPositionPolicyTest:  OK (7)
NavBreakActionPolicyTest:           OK (8)
NavBreakExclusionPolicyTest:        OK (6)
NavRecoveryControllerTest:          OK (7)
NavRecoveryGuardPolicyTest:         OK (5)
```

## Planning — LOS corrected

Both production plans succeeded with BREAK nodes:

```text
success: inicio=696,184,568 fin=701,184,568 exp=14/512 metaEncolada=true alcanzada=true
retry:   inicio=736,184,568 fin=741,184,568 exp=14/512 metaEncolada=true alcanzada=true
BREAK_FIXTURE_ROUTE success stand=696,184,567 action=697,184,568
BREAK_FIXTURE_ROUTE retry   stand=736,184,567 action=737,184,568
```

Representative `BREAK_LOS_DIAG`:

```text
reason=ACCEPT stand=696,184,567 target=697,184,568
eye=(696.5,185.6575,567.5) distanceSqr=2.25
legacyTEnter=0.5 legacyAimY=183.3425 legacyRayEnd=(697.5,183.3425,568.5)
rayEnd=(697.5,184.5,568.5)
hitType=BLOCK hitBlock=697,184,568
```

The new ray intersects the glass pane at the requested action position. `BREAK_STAND_CANDIDATE`
also reports `reason=ACCEPTED` for the selected stand. LOS rejection is no longer the cause.

## Runtime failure

Both mobs spawn at the arena origin and never move:

```text
successPos=696,184,568 successState=TRAVERSING successAir=false
retryPos=736,184,568 retryState=TRAVERSING retryAir=false retryCalls=0
```

At ticks 423/515 and then every 100 ticks:

```text
ACTUATOR_COMMAND kind=BREAK_STAND_APPROACH
 target=696,184,567 pos=696,184,568
 pathNull=false pathCanReach=true pathDone=true
 pathNodes=1 pathIndex=1 distSqr=1.2500
```

The same repeats for `736,184,567`. The executor attempts `moveTo` toward the stand, but vanilla
returns a one-node path already marked done at the current position; no physical displacement or
stand occupancy occurs. The BREAK branch requires the stand cell before validating the action, so it
never invokes `recoveryAction`. Consequently:

- `successAir=false`, `retryAir=false`, `retryCalls=0`;
- no `BREAK_EXECUTED_FROM_STAND`;
- no `BREAK_ACTUATOR_RETRY_WAIT`;
- no 40-tick retry;
- no `BREAK_FIXTURE_MISSION_RESUMED`.

Fixture timeout at 1200 ticks; `fin.txt` says `BLOCKED`, but the observed behavior is a failed
stand-approach path, so the substantive classification is **FAIL**.

## Exact defect to return to ChatGPT

File/method: `NavExecutor.ejecutarBreakDesdePosicionDeAtaque(NavRoute.Node, long)`.

Condition: `node.standPos()` is `(696,184,567)` / `(736,184,567)`; the mob is one block south at
`(696,184,568)` / `(736,184,568)`. The method calls `moveTo(standPos)`; PathNavigation produces
`pathNodes=1`, `pathIndex=1`, `pathDone=true`, `pathCanReach=true` and does not move the mob. Because
exact stand occupancy is required, the BREAK action loop never starts.

The planner did **not** reject the edge; `BREAK_LOS_DIAG` accepted it. The failure is the movement
actuator/path for the adjacent stand, not BREAK reach or LOS.

## What was not validated

- Scenario A: NOT EXECUTED
- Scenario B: NOT EXECUTED
- Retry exactly 40 ticks: NOT OBSERVED
- `BREAK_EDGE_EXCLUDED`: NOT OBSERVED
- `NAV_BACKTRACK`: NOT OBSERVED
- Strategic mission resumption: NOT OBSERVED

## A2 / C4

- A2 remains OPEN; this is not a siege run. Last known focused: 5/8 strict, 6/8 perimeter.
- C4 not run; last known focused PASS `[1,0,0]`.
- Save/reload remains OPEN.

## Artifacts

- `build/a2n/a2n-20261116-163239/srv.log`
- `build/a2n/a2n-20261116-163239/nav-trace.log`
- `build/a2n/a2n-20261116-163239/fin.txt` (`BLOCKED` timeout)
- `build/a2n/a2n-20261116-163239/estado.txt`
- `build/phase19-los-gradle.log`, `-lint.log`, `-diagcompile.log`

Original worlds unchanged:
- A2 `BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7`
- C4 `11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF`

## Exact next action

ChatGPT should inspect why `PathNavigation.moveTo(standPos)` yields a one-node path marked done for
the adjacent stand `696,184,567` from `696,184,568` (and `736,184,567` from `736,184,568`). The
LOS/aim correction at `9200ac31` is validated by `BREAK_LOS_DIAG`; it is not the current blocker.
After a correction, Harness reruns `break-runtime` on the exact new SHA. Do not change budgets,
action reach, cooldown or acceptance to force the event.
