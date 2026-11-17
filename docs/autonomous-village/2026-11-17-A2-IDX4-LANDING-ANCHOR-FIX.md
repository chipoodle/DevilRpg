# A2 IDX4 — bounded airborne landing anchor (2026-11-17)

## Status

**IMPLEMENTATION PUBLISHED — PENDING HARNESS.** This change addresses the observed state where IDX4 repeatedly waits while airborne, including one sample with `feetValid=true`, `support=true`, and `fits=true`, but `onGround=false`.

## Change

- Added `NavStepUpRecoveryPolicy.shouldStabilizeLanding`: true only while airborne and only when the current feet cell is valid, supported, and fitting.
- While the DROP recovery planner is waiting, the executor now stops vanilla navigation and uses ordinary `MoveControl` to hold the mob horizontally over the center of that already-validated cell, at its current Y.
- This is not a recovery route, jump, velocity impulse, teleport, or terrain action. Gravity remains responsible for landing.
- A* admission remains unchanged: grounded + valid feet + support + fit are all required. The next tick can plan only after `mob.onGround()` is true.
- WAIT telemetry now includes velocity and whether a landing anchor was applied.
- Added four pure-policy assertions. Expected `NAV_DROP_TAKEOFF_POLICY_TEST: OK (36 assertions)`; route-node test remains 6.

## Safety

No budget, collision, support, fit, safe-drop, or acceptance threshold was relaxed. No BREAK/BUILD/BRIDGE/DROP action is allowed by this landing-anchor behavior. The original suspended DROP route is retained.

## Validation

ChatGPT did not run Gradle, lint, diagnostics compilation, or Minecraft runtime. **PENDING HARNESS** on exact branch HEAD.

## Required Harness run

1. Sync the exact branch HEAD and preserve local-only artifacts/worlds.
2. Run Gradle tests, strict lint, diff-check, and diagnostics compile.
3. If static checks pass, execute A2 Run 1 only.
4. For IDX4, capture WAIT events with velocity, feet/support/fit and landingAnchor; whether horizontal position remains over the anchor; first tick where `onGround=true`; accepted recovery route with all four admission predicates true; physical occupancy/support/fit for every waypoint; exact take-off arrival, original DROP cursor restoration, and physical landing.
5. If no landing occurs, preserve the full tick trace and classify FAIL/BLOCKED. Do not bypass the grounded admission gate.
6. Do not run Run 2 unless Run 1 reaches 8/8 strict. Do not run full 12k until five consecutive 8/8 strict runs.

## Independent unresolved work

- C4 `assignmentsClean=false` remains an independent FAIL requiring its own diagnosis; this commit does not alter C4 or its acceptance criteria.
- IDX7 BUILD/local expansion and IDX8 local waypoint failures remain untouched.
- A2 remains OPEN; C4 save/reload remains OPEN.
- Original A2/C4 worlds and local Harness artifacts must remain untouched.
