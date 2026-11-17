# A2 IDX4 — physical DROP take-off recovery correction (2026-11-17)

## Diagnosis

The latest runtime trace (a2n-20261116-195651) reports the mob at 596,77,498, the DROP landing at
597,78,499, and dy=1. Source inspection of NavExecutor found why the executor did not recover
to the planned take-off cell:

- NavDropTransitionPolicy.origenCompatible(...) accepted a live mob position within ±1 Y of the
  planned origin.
- The DROP executor used that permissive proximity result to decide whether it was already close
  enough to execute the transition.
- A mob one block below the planned take-off was therefore treated as compatible; describirDescenso
  then correctly rejected the landing as above the mob (dy=1), invalidated the route and retried.
- The executor never issued the existing move command back to the planned take-off Y because the
  proximity check had already passed.

This is a concrete code-level explanation for the repeated dy=1 symptom. It does not establish that
the navigation engine will successfully climb back to the origin; that requires Harness runtime evidence.

## Correction

- Added NavDropTransitionPolicy.origenFisicamenteAlcanzado(...): horizontal adjacency remains bounded
  by one block, but Y must match the planned take-off Y exactly.
- Applied this stricter predicate both when deciding whether to move back to the planned origin and
  when validating planned-origin compatibility inside describirDescenso.
- If the mob is at y77 while the planned take-off is y78, the executor now commands movement to the
  planned origin instead of attempting a DROP from below.
- Added NavDropTakeoffPolicyTest with five assertions, including the exact failing geometry.
- Registered the test in policyTest.
- No budgets, collision rules, safe-drop limits, or acceptance criteria were changed.

## Commits

- 199d905d — physical take-off predicate.
- 88182bcc — executor recovery to planned take-off.
- 21f31149 — regression test.
- de0ff4d5 — test registration.

## Validation status

**PENDING HARNESS.** ChatGPT did not execute Gradle, lint, diff-check, diagnostics compile, or Minecraft
runtime. Do not call this fix validated until Harness tests the exact current branch SHA.

## Required Harness validation

1. Fetch the live feature/autonomous-village HEAD and safe fast-forward only; preserve all local artifacts.
2. Run Gradle tests, strict lint, git diff --check, and diagnostics compile.
3. Confirm NAV_DROP_TAKEOFF_POLICY_TEST: OK (5 assertions).
4. If static checks pass, run A2 Run 1 only with the existing strict configuration.
5. Capture IDX4 trace showing:
   - planned DROP origin and landing;
   - mob position while below the origin;
   - ACERCARSE-ORIGEN-DROP command being issued while Y differs;
   - physical arrival at the exact planned take-off Y;
   - subsequent DROP decision and outcome.
6. If the mob cannot reach the planned origin, preserve the navigation/path trace and classify the
   result FAIL/BLOCKED; do not bypass the origin-height guard.
7. IDX7 BUILD/900-expansion and IDX8 invalid-local-waypoint remain independent; do not modify them in this validation.
8. No Run 2 unless Run 1 reaches 8/8 strict. No full 12k until five consecutive 8/8 strict runs.

A2 remains OPEN. C4 save/reload remains OPEN. Original A2/C4 worlds must remain untouched.