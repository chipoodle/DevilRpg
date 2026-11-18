# A2 Phase 46 — vertical-only DROP edge-step coverage (2026-11-18)

## Result

**IMPLEMENTED — PENDING HARNESS VALIDATION.**

Production change is narrow: existing Phase41 DROP edge-step admission now accepts zero horizontal block
distance as well as its existing 1..3 range. This covers a grounded mob in the cell directly above a lower
landing at the same X/Z.

The existing actuator is unchanged:
- MoveControl targets the already validated landing center.
- It keeps current Y while any column under the complete entity AABB remains supported.
- It releases to landing Y only after fullyOffSupport confirms every AABB support column is clear.
- Vanilla gravity and collision remain authoritative.
- Existing timeout (60 ticks), no-progress (20 ticks), landing validation and physical cursor-advance
  requirements remain unchanged.

No support, fit, AABB, collision, height, landing tolerance, planner budget or route geometry predicate
was relaxed. No teleport, velocity injection or jump impulse was added.

## Evidence and rationale

Phase45 run a2n-20261118-145308, IDX4 UUID 072cdd9b-61d4-48b8-a6ba-bb146cd486b6:
- takeoff 597,79,499; landing 597,78,499; horizontal block distance 0.
- landing fit/support true; withinTolerance=true; landingHeightReached=false.

Phase41 admission rejected distance zero despite the remaining physical checks passing. New admission lets
the existing bounded actuator try the center-directed edge-step. It does not declare descent or landing
successful merely because distance is zero. If the AABB cannot clear support, no-progress/timeout aborts.

## Changes

1. NavDropEdgeStepPolicy.shouldStart: horizontal distance lower bound changed from >0 to >=0. All other
   admission requirements and upper bounds are retained.
2. NavDropEdgeStepPolicyTest: four assertions added for valid zero-distance DROP, negative distance rejection,
   grounded requirement, and landing support/fit requirements.

Expected output: NAV_DROP_EDGE_STEP_POLICY_TEST: OK (16 assertions).

## IDX3 remains unresolved

Phase45 IDX3 UUID ef6bf62b-0ddc-4a87-8218-c4f0cdbb6a32. Repeating local objectives and fallback result=null
remain unresolved. Phase45 logs lacked STAIR_PIPELINE_*, STAIR_BUILDER_* and physical traversal events;
no constructor root cause is asserted. Do not change local-step limits, build budgets or stair geometry.

For next A2 Run1 enable the existing opt-in diagnostic -Ddevilrpg.a2n.traceStair=true and capture:
- STAIR_PIPELINE_ENTER/RETURN/PENDING; STAIR_BUILDER_ENTER/GATE_FALSE/RETURN.
- typed outcome and pending stair coordinate; verified placement and actual physical traversal.
- local objective counter writes and rearm resets.
If the Harness launcher cannot pass the property, report the limitation.

## Phase45 report caveats to reconcile

- IDX4 mission is written as 661,71,533 in the distance section but 661,72,533 in the comparison table.
- fallback result=null does not itself prove physical stair placement or traversal. Keep escalera.colocados,
  per-placement verification and traversal counts as separate evidence.

## Validation status

ChatGPT changed GitHub source and test files only; it did not run Gradle, lint, compile or Minecraft runtime.

Harness must verify exact HEAD and preserve local/untracked files/worlds; run Gradle tests, strict lint,
diff-check and diagnostics compile first; confirm the new 16-assertion test and all prior navigation tests.
If static passes, run A2 Run1 only with stair tracing enabled. Verify zero-distance selection, per-tick AABB
support checks, support-clear before natural descent, and physical landing/cursor advance only after grounded
landing validation. Diagnose IDX3 builder lifecycle; Harness must not modify production. C4 focused regression
may be run if workflow requires it. No Run2/Full12K until five consecutive 8/8 strict runs.

A2 remains OPEN. Phase44 approach owner and Phase41 actuator lifecycle are frozen except for this narrowly
scoped zero-distance admission extension; Phase29/31/34/36 remain unchanged.