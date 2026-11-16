# Phase19 — BREAK retry mission-resumption observation window

Date: 2026-11-16

## Harness evidence prompting this change

Run: `a2n-20261116-173828`, tested on `08cf0e8c`.

The report established:
- both BREAK targets became air;
- exact retry interval: 40 ticks;
- Scenario A mission resumption passed;
- Scenario B mob reached `737,184,568`, equal to target X=737, while navigation remained `TRAVERSING`;
- movement commands targeted X=738/739 and `pathCanReach=true`;
- the previous 20-tick observation deadline expired before the mob crossed into X>737.

This is not evidence that the retry mission was cancelled: the objective remained the strategic goal and navigation was actively traversing. The fixture's 20-tick observation window was insufficient for the unchanged physical-crossing assertion.

## Fixture-only correction

Commit: `8f2b9138937b23ff853ec839a0db9f7559d0673e`

Only file changed:
`src/main/java/com/chipoodle/devilrpg/debug/A2NavHarness.java`

Changes:
1. Increased the bounded retry mission-resumption observation window from 20 to **100 ticks** after the second actuator destroys the block.
2. Preserved the exact acceptance predicate:
   `objective().equals(goal) && blockPosition().getX() > target.getX()`.
3. Added `BREAK_FIXTURE_RETRY_PROGRESS` diagnostic samples every 10 ticks after destruction, recording physical position, objective, navigation state, current route node and `NavExecutor.describe()`.
4. Added final FAIL diagnostics for retry position, objective, navigation state and executor description.
5. The fixture still fails if the mob does not physically cross the target column before the 100-tick deadline. No automatic pass is possible merely because the objective remains assigned or a movement command exists.

The 40-tick retry requirement, exact stand occupancy, LOS, BREAK verification and production behavior are unchanged. No production source was modified. No A2/C4 original world was touched.

## Validation status

**PENDING HARNESS.** ChatGPT published this fixture-only correction and did not run Gradle, lint, diagnostics compilation or Minecraft runtime.

Harness must validate the exact branch HEAD and run:
```powershell
.\\gradlew.bat test
python tools/lint_aldea.py --strict
git diff --check
.\\gradlew.bat compileJava -Pa2nDiagnostics=true
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
- Scenario B `BREAK_FIXTURE_MISSION_RESUMED retry` after destruction with `ticksAfterBreak >= 1`.
- If not resumed, retain periodic progress samples and final executor diagnostics; timeout at 100 ticks must remain FAIL.

## Next independent coverage

After BREAK runtime passes, design and run separate fixtures for:
1. `BREAK_EDGE_EXCLUDED` (second failed action).
2. `NAV_BACKTRACK` (two no-progress failures).

Do not conflate these with the current BREAK runtime fixture.

## A2 / C4 gates

- A2 remains OPEN; this fixture is not a siege run.
- Do not run full 12k until five consecutive strict 8/8 `DENTRO-PARED` runs.
- C4 save/reload remains OPEN; latest known focused result remains PASS `[1,0,0]`.
- Preserve original worlds and all local Harness artifacts.
- Harness owns synchronization, validation, runtime evidence and reports. Harness must not edit production source.
