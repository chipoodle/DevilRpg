# A2 Phase 51 — wire monotonic stair anchor and cadence wait (2026-11-18)

## Status

**IMPLEMENTED — PENDING HARNESS VALIDATION.** This phase wires the two corrections that Phase50
proved were not connected to production. The normal Gradle policy gate now includes both existing
tests. ChatGPT has reviewed the source diff but has **not** run Gradle, lint, diagnostics compilation,
or Minecraft runtime.

- Previous validated/report checkpoint: `53756dcc3b041a8b9c579095070da296fbb7d1fe`
- Production implementation HEAD: `ea21466a0cc7cc2671f5439cd5ff7bbaeecd6d9e`
- Implementation commits:
  - `1df7ecab16c88680056bcfdd314433ac4dbb3475` — wire monotonic anchor policy
  - `76ce8de3231e0d7de15611292201d328528f6021` — propagate cadence waits
  - `0ed873e22946f70374a18a2c12108fff270b6f5b` — register policy tests
  - `ea21466a0cc7cc2671f5439cd5ff7bbaeecd6d9e` — preserve prior non-wait fallback semantics; only CADENCE_WAIT updates the executor wait state

## 1. Monotonic anchor is now wired

In `AggressiveZombieEntity.construirEscalonDescendente`, the direct assignment from
`blockPosition().below()` is replaced with:

- capture the current support below the mob;
- call `NavStairAnchorPolicy.refresh(previousAnchor, currentSupport, hasTraversedStair)`;
- derive `hasTraversedStair` from `escaleraColocados > 0`.

This flag is valid at this point in the builder lifecycle: if a previously placed stair has not
been physically traversed, the method returns at `PENDING_NOT_TRAVERSED` before anchor refresh.
Once that pending stair is cleared, at least one placed stair has therefore been traversed.
The policy preserves the anchor against upward support refresh while retaining same-level horizontal
and downward refresh.

Candidate generation also applies `NavStairAnchorPolicy.isStrictlyBelow(candidate, base)` after
the first placed stair. A candidate at the traversed anchor's Y or above is rejected as
`NOT_BELOW_TRAVERSED_ANCHOR`. Existing geometry, support, collision, protection and fit predicates
remain in force.

## 2. CADENCE_WAIT now comes from the real builder result

`construirEscalonResultado` snapshots `escaleraRetraso` before invoking the builder. It returns
`StairBuildResult.CADENCE_WAIT` only when the builder returns false and the cooldown was decremented
by exactly one. An untraversed pending stair, a rejected candidate, exhausted placement budget, or
other no-progress path remains distinct and is not mislabeled as a cadence wait.

All executor call paths that can invoke the stair builder now preserve CADENCE_WAIT without changing
the previous handling of other boolean-builder outcomes. In particular, the global/local budget
fallbacks set the executor wait state only for CADENCE_WAIT; ACCESS_CLEARED, REJECTED and ordinary
NO_PROGRESS retain their previous fallback behavior:

- the normal no-waypoint pipeline;
- the global-route budget fallback inside `elegirObjetivoLocal`;
- the local-plan budget fallback.

A shared handler emits `A2_LOCAL_STAIR_WAIT` (when navigation tracing is enabled) and records
`counters_unchanged=true`. The normal local-step caller returns before `INC_STEP` or `INC_FAIL`;
the plan-level budget fallback schedules the next attempt at `now + 2` ticks and returns. No planner
budget, local-step limit, stair count, cooldown duration, or retry threshold was increased.

## 3. Normal test gate

`build.gradle` now registers both existing test mains in `policyTest`:

- `NavStairAnchorPolicyTest` — expected 8 assertions.
- `StairBuildResultTest` — expected 6 assertions.

The existing `test` task depends on `policyTest`, so `gradlew test` now executes them through the
normal gate rather than a temporary init task.

Expected output:

```text
NAV_STAIR_ANCHOR_POLICY_TEST: OK (8 assertions)
STAIR_BUILD_RESULT_TEST: OK (6 assertions)
```

These are expected outputs, **not execution results**. Harness must verify them.

## 4. Explicitly unchanged

- Phase41 DROP edge-step actuator.
- Phase44 non-adjacent DROP approach ownership.
- Phase46 horizontal-zero DROP admission.
- All global/local planner budgets, stair placement cap, candidate cap and search radius.
- Collision, support, protection, headroom, AABB-fit and physical traversal predicates.
- C4 acceptance criteria and A2/C4 worlds.
- No Run2 and no Full12K.

## 5. Harness validation request

Validate exact implementation SHA `0ed873e22946f70374a18a2c12108fff270b6f5b`:

1. Preserve local/untracked files, diagnostic artifacts and all worlds; synchronize by safe fast-forward only.
2. Run:
   - `gradlew test`
   - `python lint_aldea.py --strict`
   - `git diff --check`
   - `gradlew compileJava -Pa2nDiagnostics=true`
3. Confirm both policy tests execute through the normal `gradlew test` gate with 8 and 6 assertions.
4. If every static check passes, execute **A2 Run1 only**. Do not run Run2 or Full12K.
5. For IDX3, verify:
   - after physical traversal, an upward support refresh does not increase the anchor Y;
   - horizontal same-Y and real downward support refreshes remain possible;
   - every accepted candidate after the first traversed stair has Y strictly below the traversed anchor;
   - the stair coordinate descends instead of repeating `607,93,601`;
   - cooldown produces `STAIR_BUILDER_RETURN outcome=CADENCE_WAIT` and `A2_LOCAL_STAIR_WAIT`;
   - for every cadence wait, local `INC_STEP` and `INC_FAIL` are absent and the local counter is unchanged;
   - physical placement, support, fit and traversal are still verified.
6. Check all eight attackers and compare protected A2/C4 hashes with the Phase50 report:
   - A2: `BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7`
   - C4: `11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF`
7. If static validation fails, stop before runtime. If IDX3 still repeats or consumes counters during cadence, preserve the exact event sequence and report it; do not weaken predicates or budgets.

## 6. Current project status

A2 remains **OPEN** at the last measured 3/8 strict (Phase50 run
`a2n-20261118-212254`); C4 last PASS `[1,0,0]`. IDX4 horizontal-zero remains
`INSUFFICIENT_EVIDENCE`; its deterministic fixture is not implemented. No Run2 or Full12K until
five consecutive 8/8 strict `DENTRO-PARED` runs. Harness remains the sole owner of execution and
runtime evidence; it must not modify production source.
