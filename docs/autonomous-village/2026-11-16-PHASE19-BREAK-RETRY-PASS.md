# Phase19 BREAK retry — COMPLETO (2026-11-16)

## Result

**COMPLETO.** Both deterministic BREAK scenarios satisfy every required runtime criterion on the
exact tested SHA.

- Tested HEAD: `8b3896725d896e5a136813ae4a4bac6275fd4ed4`
- Production code: `8f2b9138937b23ff853ec839a0db9f7559d0673e`
- Run: `a2n-20261116-174648`
- Classification: **DIAGNOSTIC — BREAK fixture PASS, not A2 acceptance**

## Static validation

| Command | Exit | Result | Log |
|---|---:|---|---|
| `gradlew test` | 0 | PASS | `build/phase19-retryv2-gradle.log` |
| `lint_aldea.py --strict` | 0 | PASS | `build/phase19-retryv2-lint.log` |
| `git diff --check` | 0 | PASS | terminal |
| `compileJava -Pa2nDiagnostics=true` | 0 | PASS | `build/phase19-retryv2-diagcompile.log` |

All policy tests executed: contract 5, graph-state 7, route-policy 11, attack-position 7, action 8,
exclusion 6, recovery-controller 7, recovery-guard 5.

## Runtime result

`fin.txt`:

```text
COMPLETO (break-runtime) - successAir=true retryAir=true retryCalls=2
retryDeltaTicks=40 retryTimingExact=true
successMissionResumed=true retryMissionResumed=true
retryMissionResumeWaitTicks=23
retryPos=738,184,568 retryObjective=741,184,568 retryState=TRAVERSING
```

### Scenario A — production actuator

```text
BREAK_FIXTURE_PLAN success ... breakTargetFound=true
BREAK_FIXTURE_MISSION_RESUMED success pos=698,184,568
  mission=701,184,568 tick=49
```

- Stand physically reached.
- `actionPos=697,184,568` destroyed and verified air.
- Strategic mission resumed at x=698 > target x=697.

### Scenario B — retry

```text
BREAK_FIXTURE_RETRY_SECOND tick=72 delta=40 target=737,184,568
  destroyed=true missionResumeDeadline=172

BREAK_FIXTURE_RETRY_PROGRESS tick=82 ticksAfterBreak=10
  pos=736,184,568 objective=741,184,568 state=TRAVERSING routeNode=738,184,568

BREAK_FIXTURE_RETRY_PROGRESS tick=92 ticksAfterBreak=20
  pos=737,184,568 objective=741,184,568 state=TRAVERSING routeNode=739,184,568

BREAK_FIXTURE_MISSION_RESUMED retry pos=738,184,568
  mission=741,184,568 tick=95 ticksAfterBreak=23
```

- First retry action at tick 32 (unverified) and second at tick 72; interval exactly **40**.
- `retryAir=true`.
- The mob physically crossed `x > 737` at x=738.
- Strategic objective remained `741,184,568`.
- Mission resumed at tick 95, 23 ticks after the break.
- `BREAK_FIXTURE_RETRY_PROGRESS` samples every 10 ticks captured the crossing.

## Criteria

```text
successAir=true
retryAir=true
retryCalls=2
retryDeltaTicks=40
retryTimingExact=true
successMissionResumed=true
retryMissionResumed=true
```

**All satisfied.**

## A2 / C4

- A2: **OPEN**; this fixture is not a siege run. Last known focused: 5/8 strict, 6/8 perimeter.
- Gate of five consecutive 8/8 strict `DENTRO-PARED` runs: not met.
- Full 12k: **not run**.
- C4: not run in this fixture; last known focused PASS `[1,0,0]`.
- C4 save/reload: **OPEN**.

## Artifacts

- `build/a2n/a2n-20261116-174648/srv.log`
- `build/a2n/a2n-20261116-174648/nav-trace.log`
- `build/a2n/a2n-20261116-174648/fin.txt`
- `build/a2n/a2n-20261116-174648/estado.txt`
- `build/phase19-retryv2-gradle.log`
- `build/phase19-retryv2-lint.log`
- `build/phase19-retryv2-diagcompile.log`

Original worlds unchanged by this fixture; no original world was passed to it.

## Independent fixtures still required

These are **not validated** by the success/retry fixture and were not executed here.

### `BREAK_EDGE_EXCLUDED`

Preconditions:
- A valid BREAK route with a reachable stand.
- Actuator returns unverified twice for the same `(stand, actionPos)`.
- Target remains solid; terrain fingerprint unchanged.

Expected evidence:
- first unverified attempt → `BREAK_ACTUATOR_RETRY_WAIT` (40 ticks);
- second unverified attempt → `BREAK_EDGE_EXCLUDED` with the exact stand/target/fingerprint;
- replan chooses a different edge/stand or backs off; no repeated identical action;
- route does not advance while target is solid.

### `NAV_BACKTRACK`

Preconditions:
- Two consecutive no-progress route failures with a valid prior physically occupied cell.
- Backtrack target differs from current and last backtrack; no teleport.

Expected evidence:
- `NAV_BACKTRACK_SELECTED` after the second failure;
- history ≤16 valid cells;
- physical navigation to the prior cell;
- strategic mission preserved and resumed after arrival;
- progress/terrain change rearms the two-failure gate.

## Next action

ChatGPT may now prepare the two independent fixtures above; Harness will execute them only after they
are published on GitHub. A2 remains OPEN; full 12k is not authorized.
