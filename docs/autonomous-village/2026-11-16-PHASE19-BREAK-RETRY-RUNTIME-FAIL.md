# Phase19 BREAK retry runtime — FAIL (2026-11-16)

## Result

**FAIL.** Both blocks become air and the retry interval is exactly 40 ticks, but the retry mob does
not satisfy the fixture's mission-resumption predicate within the required 20 ticks.

- Tested HEAD: `08cf0e8c8c8c82112f127e3eb534a0e196a3fed9`
- Run: `a2n-20261116-173828`
- Classification: **DIAGNOSTIC — NOT ACCEPTANCE**

## Static validation

| Command | Exit | Result | Log |
|---|---:|---|---|
| `gradlew test` | 0 | PASS | `build/phase19-retryfix-gradle.log` |
| `lint_aldea.py --strict` | 0 | PASS | `build/phase19-retryfix-lint.log` |
| `git diff --check` | 0 | PASS | terminal |
| `compileJava -Pa2nDiagnostics=true` | 0 | PASS | `build/phase19-retryfix-diagcompile.log` |

All policy tests executed, including graph-state 7, route-policy 11, attack-position 7, action 8,
exclusion 6, recovery controller 7, recovery guard 5.

## `fin.txt`

```text
FAIL (break-runtime) - successAir=true retryAir=true retryCalls=2
retryDeltaTicks=40 retryTimingExact=true
successMissionResumed=true retryMissionResumed=false
retryMissionResumeWaitTicks=20
```

## Scenario A — success

```text
BREAK_FIXTURE_MISSION_RESUMED success pos=698,184,568
mission=701,184,568 tick=46
```

- Stand reached, target `697,184,568` in air, `BREAK_EXECUTED_FROM_STAND verified=true`.
- Mission resumed at x=698 > target x=697.

**Scenario A: PASS.**

## Scenario B — retry

```text
BREAK_FIXTURE_RETRY_FIRST tick=26 target=737,184,568 result=UNVERIFIED
BREAK_FIXTURE_RETRY_SECOND tick=66 delta=40 target=737,184,568 destroyed=true
missionResumeDeadline=86
BREAK_FIXTURE_SAMPLE tick=80 retryPos=737,184,568 retryState=TRAVERSING retryAir=true retryCalls=2
```

- Nudge reaches stand `736,184,567`.
- Second action occurs at tick 66, exactly 40 ticks after first.
- `retryAir=true`.
- At tick 80, mob is at `737,184,568`; target is `737,184,568`.
- The predicate is:
  ```text
  objective == goal && blockPosition().getX() > target.getX()
  ```
  `737 > 737` is false. The mob is moving toward `738/739` per its path
  (`MOVE_COMMAND ... node=738/739, pathCanReach=true`), but has not crossed x=738 within the
  fixture's 20-tick deadline.

At deadline tick 86 the predicate remains false (`retryMissionResumed=false`).

## Diagnosis

This is **fixture acceptance timing**, not a stand-nudge or BREAK actuator failure:

- stand occupancy: PASS;
- target air: PASS;
- 40-tick retry: PASS;
- success mission resumption: PASS;
- retry mission resumption: FAIL only because the position predicate is checked while the mob is
  still at the target's x and before it crosses to x=738.

The fixture requires `blockPosition().getX() > target.getX()` and closes at 20 ticks after the break;
the retry mob has only just begun moving. The production behavior is visibly progressing
(`MOVE_COMMAND` toward 738/739, `canReach=true`).

No production code was modified. No criteria were weakened.

## A2 / C4

- A2: OPEN; this is not a siege run. Last known focused: 5/8 strict, 6/8 perimeter.
- Full 12k: not run.
- C4: not run; last known focused PASS `[1,0,0]`.
- C4 save/reload: OPEN.

## Artifacts

- `build/a2n/a2n-20261116-173828/srv.log`
- `build/a2n/a2n-20261116-173828/nav-trace.log`
- `build/a2n/a2n-20261116-173828/fin.txt`
- `build/a2n/a2n-20261116-173828/estado.txt`
- `build/phase19-retryfix-gradle.log`, `-lint.log`, `-diagcompile.log`

Original worlds unchanged:
- A2 `BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7`
- C4 `11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF`

## Next action

ChatGPT should adjust only the fixture's retry mission-resumption acceptance: either allow the mob to
cross the target column before checking `x > target.x`, or use the same objective/mission event used by
the success mob. Then publish a new SHA; Harness will rerun the exact `break-runtime` and require both
`BREAK_FIXTURE_MISSION_RESUMED success` and `... retry`.

Do not change production navigation, stand nudge, cooldown or budgets.
