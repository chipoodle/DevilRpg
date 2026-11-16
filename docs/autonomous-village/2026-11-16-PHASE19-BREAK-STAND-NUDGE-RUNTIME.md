# Phase19 BREAK stand approach — runtime result (2026-11-16)

## Result

**FAIL (fixture acceptance timing), not a nudge/BREAK actuator failure.**

- Tested HEAD: `0d8fc52119e3e3b968ee935a6559c88019047741`
- Production correction: `11be611d90c4d65772813550c2a365a524ee5147`
- Run: `a2n-20261116-172549`
- Classification: **DIAGNOSTIC — NOT ACCEPTANCE**

## Git sync

- Local before: `d0f9c7a5895c3fb2a69cc7148839f60efa181a0a`
- Remote: `0d8fc52119e3e3b968ee935a6559c88019047741`
- Divergence: 0 ahead / 4 behind.
- Fast-forward succeeded; only local-only untracked artifacts existed.
- Confirmed `BREAK_STAND_NUDGE` and `NavAdjacentApproachPolicy.shouldNudge`.

## Static validation (HEAD 0d8fc521)

| Command | Exit | Result | Log |
|---|---:|---|---|
| `gradlew test` | 0 | PASS | `build/phase19-nudge-gradle.log` |
| `lint_aldea.py --strict` | 0 | PASS | `build/phase19-nudge-lint.log` |
| `git diff --check` | 0 | PASS | terminal |
| `compileJava -Pa2nDiagnostics=true` | 0 | PASS | `build/phase19-nudge-diagcompile.log` |

All policy tests executed, including graph-state (7), route-policy (11), attack-position (7),
action (8), exclusion (6), recovery controller (7), recovery guard (5).

## Runtime — nudge and BREAK

`fin.txt`:

```text
FAIL (break-runtime) - successAir=true retryAir=true retryCalls=2
retryDeltaTicks=40 retryTimingExact=true
successMissionResumed=true retryMissionResumed=false
```

### Scenario A — success

- Planner route contains BREAK: stand `696,184,567`, action `697,184,568`.
- `BREAK_STAND_NUDGE` emitted at tick 20–24 from `696,184,568` to stand `696,184,567`,
  `manhattan=1`, `reason=navigation_done_without_physical_occupancy`.
- `BREAK_EXECUTED_FROM_STAND uuid=8cd8... stand=696,184,567 target=697,184,568 invoked=true verified=true`.
- `successAir=true`.
- `BREAK_FIXTURE_MISSION_RESUMED success pos=698,184,568 mission=701,184,568 tick=48`.

**Scenario A: PASS.**

### Scenario B — retry

- Planner route contains BREAK: stand `736,184,567`, action `737,184,568`.
- `BREAK_STAND_NUDGE` emitted at ticks 22–29 from `736,184,568` to stand `736,184,567`.
- `BREAK_FIXTURE_RETRY_FIRST tick=30 target=737,184,568 result=UNVERIFIED`.
- `BREAK_ACTUATOR_RETRY_WAIT ... retryAt=70 reason=first_unverified_attempt`.
- `BREAK_FIXTURE_RETRY_SECOND tick=70 delta=40 target=737,184,568 destroyed=true`.
- `BREAK_EXECUTED_FROM_STAND uuid=fe26... stand=736,184,567 target=737,184,568 invoked=true verified=true`.
- `retryAir=true`, retry delta **40 exactly**.

**Scenario B actuator/retry timing: PASS.** The fixture's `retryMissionResumed` is false.

## Why `retryMissionResumed=false`

The fixture closes at tick 70, the exact tick the retry target becomes air. Its acceptance predicate is:

```text
retryMissionResumed = objective().equals(goal) && blockPosition().getX() > target.getX()
```

At tick 70 the retry mob is still at `736,184,567`; the target is `737,184,568`, so it has not yet
moved to `x > 737`. The success mob had two extra ticks and resumed at x=698. This is a **fixture
acceptance-timing defect**, not a production BREAK defect: the target air and 40-tick retry are proven,
but mission resumption is evaluated before the retry mob gets a movement tick.

No modification was made to production or fixture source.

## Artifacts

- `build/a2n/a2n-20261116-172549/srv.log`
- `build/a2n/a2n-20261116-172549/nav-trace.log`
- `build/a2n/a2n-20261116-172549/fin.txt`
- `build/a2n/a2n-20261116-172549/estado.txt`
- `build/phase19-nudge-gradle.log`, `-lint.log`, `-diagcompile.log`

Original worlds unchanged:
- A2 `BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7`
- C4 `11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF`

## A2 / C4

- A2: not run; remains OPEN at 5/8 strict / 6/8 perimeter from prior siege evidence.
- C4: not run; last known PASS `[1,0,0]`.
- Save/reload: OPEN.

## Exact handoff

The production nudge/BREAK correction is validated for:

1. physical stand occupancy;
2. target air;
3. success mission resumption;
4. retry exactly 40 ticks after first unverified action.

Only the retry mission-resumption predicate needs fixture timing: wait at least one movement tick after
`retryAir` before evaluating `x > target.getX()`, or use the same event-based mission-resumption check
as the success mob. This is not a production code correction.
