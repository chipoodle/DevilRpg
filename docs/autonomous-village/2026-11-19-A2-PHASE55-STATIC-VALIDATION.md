# A2 Phase 55 — incremental global A* static validation (2026-11-19)

## 1. Result

**FAIL — production does not compile at the tested SHA.**

`9966f807b2fbb13552545cdd181f2f034c7ecbaa` introduces local variables named `state` in
`NavPlanner.advanceGlobalSearch(...)`, but that method already has a parameter named `state`. Compilation
fails before any test or runtime validation.

```text
NavPlanner.java:617: error: variable state is already defined in method
  advanceGlobalSearch(Mob,GlobalSearchState,GlobalSearchHandle)
NavPlanner.java:628: error: variable state is already defined in method
  advanceGlobalSearch(Mob,GlobalSearchState,GlobalSearchHandle)
```

- BRANCH: `feature/autonomous-village`
- INITIAL_HEAD: `924c62088e6efce9c8e1fcb39fdb76969f238cc8`
- FINAL_HEAD at start: `1679315289534bd885013fefb8c15c34f0bf82f9`
- TESTED_SHA: `9966f807b2fbb13552545cdd181f2f034c7ecbaa`
- PRODUCTION_MODIFIED: false
- TESTS_MODIFIED: false
- WORLDS_MODIFIED: false

## 2. Git / synchronization

```text
Local was 45 commits behind origin; fast-forward only.
Production diff 9966f807..16793152 (src/build.gradle): empty.
Untracked local artifacts preserved.
```

## 3. Static commands

```text
.\gradlew.bat compileJava -Pa2nDiagnostics=true: FAIL (exit 1)
.\gradlew.bat test: FAIL (exit 1, same compile errors)
python tools\lint_aldea.py --strict: PASS
git diff --check: PASS
```

`NAV_PLANNER_STATE_TEST` could **not** run because compilation failed. The test source contains **51**
`comprobar(...)` assertions and would print `NAV_PLANNER_STATE_TEST: OK` without an assertion count.

## 4. Code inspection (pre-compile evidence)

Verified by source inspection:

- `NavGraphSearch.Session` retains `open`, `closed`, `bestG`, predecessors, feet/action/stand maps,
  `front`, `bestDist`, `found`, `expansions`, `complete`, `hitBudget`.
- `advance(expansionSlice)` bounds expansions by the slice and OPEN pops by `expansionSlice * 2`.
- `NavPlanner.GLOBAL_EXPANSIONS_PER_TICK = 4`; retained handle passes `advance(4)` => max 4 expansions,
  max 8 OPEN pops per tick.
- Total `maxExpansions` is still supplied by the caller (`MAX_EXPANSIONES_GLOBALES`); no budget increase
  is present in the slice design.
- `result()` throws if the session is not complete, so partial results cannot become routes.
- `NavExecutor` retains the handle, remains in planning while `global.pending()`, and has a
  `globalSearchPending` guard that avoids consuming local stair/fail counters.
- `cancelGlobalSearch` clears the handle; mission/terrain invalidation call sites exist.
- Synchronous callers use `handle == null` and loop to completion, preserving their API.
- No changes to collision/support/protection/cost/heuristic/radio/vertical window were observed in the
  reviewed diff.

The compile error is nevertheless a hard blocker for all behavioral claims.

## 5. Error context

`NavPlanner.advanceGlobalSearch` receives `GlobalSearchState state` and then, in both route-building
loops, declares:

```java
BlockPos state = BlockPos.of(paso.state());
```

This shadows/redeclares the parameter in the same scope, which Java rejects. The same local name occurs
at lines 617 and 628.

## 6. Runtime

No runtime executed. Correctly stopped after static failure.

## 7. Integrity

```text
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

## 8. Recommendation

ChatGPT must rename the local `BlockPos state` variables (e.g. `routeState`) in both loops, rebuild, and
publish a new exact SHA. Then re-run this static gate before any runtime authorization.

A2 remains OPEN.
