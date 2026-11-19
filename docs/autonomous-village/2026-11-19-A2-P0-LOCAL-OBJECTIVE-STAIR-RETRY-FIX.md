# A2 P0 — local objective idempotence and stair retry fix (2026-11-19)

## Status

**IMPLEMENTED — PENDING HARNESS STATIC VALIDATION.**

- Branch: `feature/autonomous-village`
- Forensic baseline: `3c36bfb21f1fdce64379dc734bbdf90aae550dde`
- Production implementation HEAD: `b41e9687a4be74d24a671340ac41b7e3f1817045`
- Forensic source SHA: `10719ff82465a0d8997cb2af3af23c9f71292b19`
- Related report: `2026-11-19-A2-POST-PHASE55-FORENSIC-ANALYSIS.md`
- Runtime: **NOT EXECUTED**
- Tests/build/lint: **NOT EXECUTED BY CHATGPT**
- A2 remains **OPEN**.

## 1. IDX3 / IDX5 — repeated unusable local objective

### Evidence

The forensic run recorded 381 repeated `INC_STEP` writes for one unusable objective on each attacker. The existing code incremented `escalonesDeObjetivo` whenever the selected candidate did not pass the usable-waypoint idempotence guard, even when the candidate was exactly the same cell as the previous objective. The objective-progress policy accepted that candidate repeatedly, so the local-step counter reached 64 without 64 distinct objective transitions.

### Implementation

Added pure policy:

`src/main/java/com/chipoodle/devilrpg/entity/nav/NavLocalObjectiveStepPolicy.java`

Its decision is explicit:

- `ACEPTAR_NUEVO`: candidate identity differs; it may consume one local-step transition after the existing progress policy accepts it.
- `REUTILIZAR`: same objective remains usable and inside the local window; preserve the existing route/transition without consuming a step.
- `RECHAZAR_REPETIDO_INUTILIZABLE`: same objective is still unusable or outside the local window; do not consume a step, increment the existing bounded failure counter, and return to the existing bounded retry/failure path.

`NavExecutor.escalonarObjetivoLocal` now uses this policy. The failure counter is no longer reset before the repeated-objective check; it resets only when a genuinely new candidate is selected. A new `REJECT_REPEAT_UNUSABLE` trace records the objective and confirms that the local-step counter was not consumed.

The existing mission-change, verified-progress and verified-terrain-change reset paths remain responsible for resetting their scoped counters.

### Regression test

Added `NavLocalObjectiveStepPolicyTest` — expected **5 assertions**. It covers new objectives, reuse of a usable objective, repeated unusable objectives, and window rejection.

## 2. IDX4 — no-candidate retry must not become CADENCE_WAIT

### Evidence

When all bounded stair candidates failed, `construirEscalonDescendente` reset `escaleraRetraso` to the same 10-tick inter-block delay used after a verified placement/access clear. Subsequent cooldown decrements were correctly classified as `CADENCE_WAIT`, but the no-candidate path restarted the delay forever.

### Implementation

In `AggressiveZombieEntity.construirEscalonDescendente`, the no-valid-candidate path no longer resets `escaleraRetraso`.

The inter-block cooldown remains attached to verified stair placement and verified access clearing. A no-candidate attempt now remains `NO_PROGRESS`; the existing `NavExecutor` bounded objective-failure counter and `nextBudgetRetryAt` backoff govern subsequent retries and terminal failure.

Extracted the outcome classification into:

`src/main/java/com/chipoodle/devilrpg/entity/nav/StairBuildResultPolicy.java`

`AggressiveZombieEntity.construirEscalonResultado` delegates to this pure policy. `CADENCE_WAIT` is produced only when a failed builder call actually decrements a positive cooldown by exactly one. An unchanged cooldown/no-candidate result remains `NO_PROGRESS`.

### Regression test

Extended `StairBuildResultTest` from 6 to **12 expected assertions**, covering no-candidate classification, real cooldown decrement, unchanged cooldown, verified placement, verified access clear, and fail-closed classification.

## 3. Invariants preserved

No changes were made to:

- `NavExecutor` single-authority architecture or the removal of `NavFeature`.
- `MAX_ESCALONES_DE_OBJETIVO` (64) or `MAX_FALLOS_DE_ESCALON_DE_OBJETIVO` (10).
- Local/global A* expansion budgets (900 / 8000).
- Stair placement cap, candidate cap, search radius, or cooldown duration.
- Stair anchor monotonicity, physical traversal confirmation, or pending-stair ownership.
- Collision, support, headroom, protection, real-AABB fit, landing or movement predicates.
- A2/C4 acceptance criteria, fixture geometry, worlds, or harness behavior.
- IDX7 ascent/BUILD behavior, IDX8 global cancellation/session lifecycle, or the PowerShell wrapper.

Important design boundary: the existing descending stair builder remains descent-only. The global route's first-class `NavRoute.Transition.BUILD` and its verified executor path remain distinct. This change does not convert the descending anchor/builder into an ascent builder.

## 4. Files changed

- `src/main/java/com/chipoodle/devilrpg/entity/nav/NavExecutor.java`
- `src/main/java/com/chipoodle/devilrpg/entity/AggressiveZombieEntity.java`
- `src/main/java/com/chipoodle/devilrpg/entity/nav/NavLocalObjectiveStepPolicy.java` (new)
- `src/main/java/com/chipoodle/devilrpg/entity/nav/StairBuildResultPolicy.java` (new)
- `src/test/java/com/chipoodle/devilrpg/entity/nav/NavLocalObjectiveStepPolicyTest.java` (new)
- `src/test/java/com/chipoodle/devilrpg/entity/nav/StairBuildResultTest.java`
- `build.gradle` (register new policy test in the existing `policyTest` gate)

## 5. Implementation commits

All commits are on `feature/autonomous-village`, descended from the forensic baseline:

- `055864c1eef4a44778c6f0b92f9780cdab24d38c` — bound repeated unusable local objectives.
- `a309dd4c5f24404aaa52fbe2db62f0058bb766c1` — stop resetting stair cadence after no candidate.
- `558bbcceb389b6376e9c2080f766850f894fd71d` — add local objective idempotence policy.
- `7506bd1835dabe94bcb55ea32066578815c07076` — add local objective policy regression test.
- `180111f42d943e1e207d0dafbc1cbecbc871aae2` — isolate stair result classification.
- `c50d707628fe7c74ad21f20e45a5cdc53b3b97e4` — wire explicit stair result policy.
- `c2ce90119a0d804c1d86a1dcce9e998fc2c7ed41` — extend stair result tests.
- `b762e832f5c15f834fa3c18ba17d8b108718141e` — register local objective policy test.
- `b41e9687a4be74d24a671340ac41b7e3f1817045` — guard nullable current objective in idempotence check.

The compare from forensic baseline to implementation HEAD is fast-forward: 9 commits, 7 files changed, 132 insertions and 46 deletions.

## 6. Harness — next task

Validate **exact implementation HEAD**:

`b41e9687a4be74d24a671340ac41b7e3f1817045`

1. Fast-forward-only sync to this commit. Preserve all local/untracked work, backups, diagnostic artifacts and worlds. Stop on divergence or overwrite risk.
2. Verify branch and exact HEAD; verify all seven changed paths and the two new source/test files.
3. Run static checks only:
   - `.\gradlew.bat compileJava --info -Pa2nDiagnostics=true -Pa2nPopulationQueryHarness=true -Pa2nTraceNav=true -Pa2nTraceGlobalSearch=true`
   - `.\gradlew.bat test --info -Pa2nDiagnostics=true -Pa2nPopulationQueryHarness=true -Pa2nTraceNav=true -Pa2nTraceGlobalSearch=true`
   - `python tools\lint_aldea.py --strict`
   - `git diff --check`
4. Confirm `NAV_LOCAL_OBJECTIVE_STEP_POLICY_TEST: OK (5 assertions)`.
5. Confirm `STAIR_BUILD_RESULT_TEST: OK (12 assertions)`.
6. Confirm all existing policy tests, especially `NAV_PLANNER_STATE_TEST` (54 assertions), still run through the normal test gate.
7. Record exact tested SHA, commands, exit codes, logs, test names/assertions, source selection, worktree state and untracked preservation.
8. Confirm no production edits were made by Harness.

**STATIC ONLY.** Do not launch Minecraft, the Phase55 wrapper, runServer, A2, C4, Run2 or Full12K. The previous runtime authorization is consumed. Stop after static validation regardless of PASS/FAIL and return the complete report.

## 7. Remaining work after static validation

- P1 IDX7: inspect the exact route transition and implement the ascent/BUILD handoff without weakening the descending stair-anchor contract.
- P1 IDX8: instrument every global-search cancellation caller/reason/tick before changing cancellation or restart semantics. Do not reuse a frontier from a moved start without proving route-root correctness.
- P2 wrapper: fix named-argument forwarding independently after the navigation changes.
- No runtime until production changes pass static validation and a new explicit runtime authorization is obtained.
