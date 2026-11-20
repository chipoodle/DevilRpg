# A2 global navigation corrections — implementation record (2026-11-20)

## Status

**Implemented in production source; first STATIC gate failed to compile; lambda-capture correction published; fresh STATIC validation pending. Runtime not authorized.**

- Branch: `feature/autonomous-village`
- Forensic baseline: `eb92c817f2cbdbe1bfa708ad92cb4493c51d1d5c`
- Source correction commits:
  - `2c47d5631ef3c70d7dc79663bdbbb92935b444db` — BREAK stand admission, canonical DROP state, stale OPEN handling, counters and cancellation caller.
  - `1c0ba5625e1ce665e8afd783393900a53ae14aca` — evaluate BREAK stands at the actual feet Y, including a target above/below the stand.
  - `c663f5d06618d12cd8150e238308fe3e9d7652db` — clarify canonical feet semantics and correct test assertion count.
  - `ab9d4f0c84a56b67d4efb3e4ff2468ac54e2415f` — fix Java lambda capture for the per-slice reopen metric using a final one-element `int[]` holder; increment remains inside `graph.expand`, and the slice snapshot reads the holder after expansion.
- Harness static attempt on `bfaebd0661a39b3031de3f59efe6fdcef6a3c889` failed at compile: mutable `reopensThisSlice` was captured by the `graph.expand` lambda; no tests ran. Lint and diff-check passed.
- ChatGPT made the minimal compile-only correction in `ab9d4f0c84a56b67d4efb3e4ff2468ac54e2415f`; no build/tests/lint/runtime have been executed by ChatGPT.
- No Minecraft launch or world modification.

## 1. BREAK candidate generation in shafts/slopes

The Run1 forensic evidence showed 40/56 initial candidates rejected as `BREAKABLE/NODO_NO_PISABLE`, with supported terrain but no admitted attack stand. The previous policy explicitly removed the current predecessor from the 8 neighboring stand candidates and required a different one-cell move. It also generated stand candidates at the action block's Y, which rejects a physically adjacent stand when the target block is at another height.

Changes:

- `NavBreakAttackPositionPolicy.candidates(actionPos, from, feetY)` generates the bounded eight neighboring stand cells at the mob's validated feet Y.
- The current cell is retained when it is one of those neighboring stands. `isSingleStepReachable` now admits the already-occupied cell as a zero-movement BREAK approach; it still rejects non-neighbor cells and Y mismatches.
- The planner still validates real support, body clearance, protection, action reach, line of sight, break permission, and existing exclusion policy through the existing world-aware checks.
- The existing action target remains distinct from the physical stand. No arbitrary terrain modification or new movement authority was introduced.

This is a source-level correction aimed at the measured zero-successor geometry. It is **not yet evidence** that all 40 candidates become valid; Harness must run the static gate, and runtime behavior remains unmeasured.

## 2. DROP canonical graph state

Both local and global successor helpers now store the validated landing feet cell as the A* state for DROP:

- `NavPlanner.estadoSucesor`
- `NavPlanner.sucesorCanonico`
- `NavSuccessorPolicy`

The state key Y now equals `piesHasta`, so the next expansion's position, `feet`, heuristic, distance and goal operate on the same physical landing cell. The original candidate/action information remains available where needed for exclusions and route metadata. `NavRouteNodePolicy` retains its defensive DROP feet projection.

No safe-drop limits, takeoff validation, landing support, collision checks or DROP exclusions were relaxed.

## 3. OPEN stale records and search telemetry

`NavGraphSearch.Session.advance` now compares each popped record's `g` against `bestG[state]` and discards stale records before expansion. This prevents an obsolete, higher-cost record from expanding a state after a better route has been queued.

Added counters:

- Total and per-slice pops.
- Total and per-slice stale pops.
- Total and per-slice closed-state skips.
- Total and per-slice real reopens.
- Total and per-slice useful expansions.

The existing per-slice pop ceiling and total expansion budget are unchanged. Pending/completed global search summaries include these metrics.

`GLOBAL_SEARCH_CANCEL` now includes world tick, search counters and the exact calling class/method/line from the Java stack. This resolves the previous generic `owner_cancelled` caller gap without changing cancellation semantics.

## 4. Regression coverage authored

- `NavBreakAttackPositionPolicyTest`: verifies current-cell zero-movement stand admission, inclusion of the predecessor, and head-height target stand candidates at actual feet Y. Expected output: 8 assertions.
- `NavPlannerStateTest`: verifies DROP successor state at landing Y, a multi-edge search whose DROP state/feet remain coherent through the next expansion, stale OPEN record skipping, pop/expansion counters, and closed-state reopens.
- Existing BUILD, BREAK action, support, protection, route execution, leases and A2/C4 acceptance contracts are not intentionally changed.

Tests are authored but **NOT RUN** until Harness reports exact results.

## 5. Explicitly unchanged

- No search budgets, slice sizes, radii, safe-drop limits, cooldowns or retry limits changed.
- No protection, collision, support, headroom, AABB, placement or break permission relaxed.
- No A2 fixture, world, spawn, acceptance criterion or C4 logic changed.
- No second A2, C4 or Full12K run.
- D3 BUILD runtime remains unmeasured because Run1 emitted no BUILD nodes.

## 6. Required next step — Harness STATIC ONLY

Harness must sync safely to the current branch tip and run the repository's complete static gate:

1. Verify branch/HEAD, divergence, dirty/untracked state and preserve all local-only files, backups, partial files, logs, artifacts and worlds. Safe fast-forward only; stop on conflict/overwrite risk.
2. Verify the source/test blob SHAs listed below.
3. Run `compileJava`, Gradle `test` including `policyTest`, strict lint and `git diff --check`.
4. Verify the new BREAK and DROP assertions, stale/reopen metrics, existing policy groups, and unchanged safety invariants.
5. Confirm `GLOBAL_SEARCH_CANCEL` includes caller and tick; pending/complete summaries include pop/stale/reopen metrics.
6. Harness must not modify production or tests. Persist the static report and update `NEXT-SESSION.md`.
7. Stop after STATIC PASS/FAIL. No Minecraft, runServer, A2, C4, Run2 or Full12K.

### Expected source/test blobs at implementation HEAD

| File | Blob SHA |
|---|---|
| `NavBreakAttackPositionPolicy.java` | `4e06c662da8ec4e457815d6be464e6e26bc2be01` |
| `NavGraphSearch.java` | `906b480afff84780d66b4378b447e7b1e4a5a3f0` (after lambda-capture correction) |
| `NavPlanner.java` | `b89de3c5e955f0bf94c939adbaeb76ca98159200` |
| `NavSuccessorPolicy.java` | `c50bf342b96f26158b6946a31ac3fc1be93cadbc` |
| `NavBreakAttackPositionPolicyTest.java` | `3e5acbf3eec3c371477e1f70a0f525642c7fd633` |
| `NavPlannerStateTest.java` | `9d7c23fdc56e86783e255ac68efed14efa670a9c` |

A2 remains OPEN. C4 is not revalidated. No runtime authorization is granted by this implementation record.

## 7. Compile blocker correction — 2026-11-20

Harness report `2026-11-20-A2-GLOBAL-ASTAR-CORRECTION-STATIC-VALIDATION.md` established the exact Java error: `reopensThisSlice` was not effectively final inside `graph.expand`. The correction changes only the local per-slice counter to `final int[] reopensThisSlice = {0}`, increments index `0` in the lambda, and copies index `0` into `lastReopens` after expansion. The cumulative `totalReopens`, condition for a real reopen, timing of increments, and per-slice metric semantics are unchanged. No budget, route, or navigation behavior was changed. New `NavGraphSearch.java` blob: `906b480afff84780d66b4378b447e7b1e4a5a3f0`.

**Required:** Harness must run a fresh STATIC ONLY gate against the new branch tip, verify all source/test blobs (including the new NavGraphSearch blob), then persist a new validation result. No runtime authorization.