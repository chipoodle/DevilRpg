# A2 Phase 50 — monotonic stair anchor and cadence accounting (2026-11-18)

## Status

**IMPLEMENTED — PENDING HARNESS VALIDATION.** This change addresses the two demonstrated IDX3 defects
from Phase49A. ChatGPT modified production and added pure policy tests directly in GitHub. ChatGPT has
not run Gradle, lint, diagnostics compile or Minecraft runtime.

- Previous validated production: `9d6709f551f394e0ac7be69160c2aa46419d8058`
- Phase50 implementation commits:
  - `ae920ddb708041bf8fd17632749f62ab20362122`
  - `f7a919c2ececcf9c745e5e95a8f141ee124ca25e`
- Production SHA to validate: `f7a919c2ececcf9c745e5e95a8f141ee124ca25e`
- Documentation HEAD before this handoff: `b8cf3a98d0999c7a2d0e7223fe07741b0739648d`

## 1. Primary fix — preserve the traversed anchor

New pure policy: `NavStairAnchorPolicy`.

When no stair has yet been traversed, the current support continues to seed the anchor. Once at least
one stair has been placed/traversed, a later support refresh may:

- update horizontally when the support Y is unchanged;
- advance the anchor downward when the real support Y is lower;
- **not** move the anchor upward when the current support is higher.

For the measured case:

```text
previous traversed anchor = 607,93,601
current support           = 607,94,601
new anchor                = 607,93,601 (preserved)
next candidate            = strictly below anchor, e.g. Y <= 92
```

Candidate generation also has an explicit defensive invariant: after the first stair, a candidate
must have Y strictly less than the current traversed anchor. A same-height or higher candidate is
rejected as `NOT_BELOW_TRAVERSED_ANCHOR`.

This does not change support, fit, collision, protection, AABB or landing predicates.

## 2. Secondary fix — CADENCE_WAIT is not local progress

Added `StairBuildResult.CADENCE_WAIT`. The builder returns this outcome only when the call decrements
a positive cooldown by exactly one. A pending stair that has not been traversed remains
`NO_PROGRESS`; candidate exhaustion and other non-progress outcomes remain distinct.

`NavExecutor` now treats `CADENCE_WAIT` as a scheduling wait:

- no local objective step is incremented;
- no local failure is incremented;
- the current tick is deferred by two ticks;
- the next attempt re-enters the normal builder path;
- trace event `A2_LOCAL_STAIR_WAIT` records the unchanged local counter.

The handling covers the direct non-usable-objective path and the fallback call sites inside
`elegirObjetivoLocal()`. It does not increase, reset or otherwise alter any budget.

## 3. Tests added/updated

- `NavStairAnchorPolicyTest`: expected 8 assertions:
  - initial support seed;
  - preserve anchor against upward support;
  - same-level horizontal refresh;
  - downward support refresh;
  - null support preservation;
  - strictly lower candidate accepted;
  - equal-height candidate rejected;
  - higher candidate rejected.
- `StairBuildResultTest`: expected 6 assertions, including `CADENCE_WAIT` distinct from
  `NO_PROGRESS` and `STAIR_PLACED`.

Expected output:

```text
NAV_STAIR_ANCHOR_POLICY_TEST: OK (8 assertions)
STAIR_BUILD_RESULT_TEST: OK (6 assertions)
```

These are expected results only; they have not yet been run by ChatGPT.

## 4. Explicitly unchanged

- Phase41 DROP edge-step actuator.
- Phase44 non-adjacent DROP approach ownership.
- Phase46 horizontal-zero DROP admission.
- Global planner budgets and local maximums.
- Stair maximum of 24 placed blocks, candidate cap and search radius.
- Movement ownership, vanilla gravity/collision and physical traversal confirmation.
- A2/C4 original worlds.

## 5. Harness validation request

On exact production SHA `f7a919c2ececcf9c745e5e95a8f141ee124ca25e`:

1. Preserve local/untracked files and all worlds; sync without destructive reset.
2. Run Gradle tests, strict lint, diff-check and diagnostics compile.
3. Confirm both expected policy-test counts.
4. If all static checks pass, run **A2 Run1 only**.
5. Inspect IDX3:
   - after `ESCALON-TRAVESADO`, no upward `ANCHOR_REFRESH` may replace the traversed anchor;
   - every subsequent accepted candidate must be strictly below the traversed anchor;
   - the placed stair coordinate must descend rather than repeat;
   - `CADENCE_WAIT` must produce `A2_LOCAL_STAIR_WAIT` and must not produce `A2_LOCAL_STAIR_WRITE op=INC_STEP` or `INC_FAIL` for that wait;
   - placement, traversal, support and fit must still be physically verified.
6. Check all eight attackers and ensure no regression in strict/perimeter entry.
7. Verify A2/C4 hashes before/after.
8. No Run2 or Full12K. Do not implement production changes from Harness.

If static checks fail, stop before runtime. If IDX3 does not descend, report exact event sequence and classify the remaining cause; do not relax any predicate or budget.

## 6. Current project status

Latest runtime remains Phase49A run `a2n-20261118-172804`: A2 3/8 strict, C4 PASS `[1,0,0]`.
A2 remains **OPEN**. IDX4 horizontal-zero remains `INSUFFICIENT_EVIDENCE`; no approved deterministic
fixture has been implemented. No Run2/Full12K until five consecutive 8/8 strict runs.
