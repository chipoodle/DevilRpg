# Phase19 — BREAK retry mission-resumption fixture timing

Date: 2026-11-16

## Previous runtime evidence

Harness run `a2n-20261116-172549`, tested on `0d8fc521`, reported:

```text
successAir=true retryAir=true retryCalls=2
retryDeltaTicks=40 retryTimingExact=true
successMissionResumed=true retryMissionResumed=false
```

The production behavior already passed:
- both mobs reached the exact validated stand;
- both BREAK targets became air;
- Scenario A emitted mission resumption;
- Scenario B's first unverified action was retried exactly 40 ticks later;
- Scenario B emitted the verified BREAK execution event.

The only failure was the fixture's immediate same-tick mission-progress assertion for Scenario B.

## Root cause

`A2NavHarness.tickBreakRuntime` runs from `ServerTickEvent.Pre`. The retry actuator destroys the block at tick 70, and the fixture then evaluated:

```java
objective().equals(goal) && blockPosition().getX() > target.getX()
```

in that same tick. The mob was still physically at its stand `736,184,567`; it had not received a subsequent entity-movement tick after the target became air. The success mob had already received movement ticks, which is why its equivalent predicate passed.

This is fixture acceptance timing, not evidence of a production mission-resumption failure.

## Fixture-only correction

Commit: `4c3f446aeaa893db7ff7bcfc1c5f690f94e37332`

Only file changed:
`src/main/java/com/chipoodle/devilrpg/debug/A2NavHarness.java`

Changes:
1. Added `breakRetryMissionResumeDeadlineTick`.
2. When the second controlled actuator destroys the target, the fixture sets a deadline 20 ticks later.
3. Retry mission progress is not evaluated until `now > breakRetrySecondTick`, ensuring at least one subsequent server/entity tick has elapsed after the destruction tick.
4. The fixture retains the same mission-resumption predicate: objective equals the strategic goal and the mob's physical X coordinate has advanced beyond the target.
5. Completion waits for retry mission resumption or the 20-tick deadline. If the predicate remains false at the deadline, the fixture reports **FAIL** with the elapsed wait; it cannot silently pass.

The exact 40-tick actuator retry measurement is unchanged. No production source was modified. No reach, LOS, collision, cooldown, planner budget, recovery budget or acceptance criterion was changed.

## Validation status

**PENDING HARNESS.** ChatGPT published the fixture correction and documentation but did not execute Gradle, lint, diagnostics compilation or Minecraft runtime.

Harness must fetch and validate the exact current branch HEAD. Run:

```powershell
.\gradlew.bat test
python tools/lint_aldea.py --strict
git diff --check
.\gradlew.bat compileJava -Pa2nDiagnostics=true
```

Then execute a new disposable `break-runtime` world with a unique run ID and trace enabled.

Required final evidence:
- `successAir=true`
- `retryAir=true`
- `retryCalls=2`
- `retryDeltaTicks=40`
- `retryTimingExact=true`
- `successMissionResumed=true`
- `retryMissionResumed=true`
- Scenario B `BREAK_FIXTURE_MISSION_RESUMED retry` occurs after the retry destruction tick, with `ticksAfterBreak >= 1).

If mission resumption does not occur within 20 ticks, report FAIL with positions, objective, navigation state and per-tick samples. Do not weaken the predicate or alter production.

## A2 / C4

- A2 remains OPEN; this is not an A2 siege run.
- Do not run full 12k until five consecutive strict 8/8 `DENTRO-PARED` runs.
- C4 save/reload remains OPEN; latest known focused result before this fixture correction was PASS `[1,0,0]`.
- Preserve original worlds and all local Harness artifacts.
- Harness owns synchronization, validation, runtime evidence and reports. Harness must not edit production source.
