# A2 IDX4 — guarded STEP_UP execution and exact planner budget

## Status: PENDING HARNESS

This change addresses the two concrete defects from Run 1 `a2n-20261116-225217` on
`38a4b52ee3e336b82dc8e51df17ae42135822747`:

1. An accepted recovery route with a single `STEP_UP` node was handed back to
   `PathNavigation`, which did not physically climb to the validated destination.
2. The local planner incremented the expansion counter before checking the limit, allowing
   `901/900` expansions.

## Production changes

### Guarded natural STEP_UP assist

- Added `NavStepUpRecoveryPolicy.canAssist`.
- During DROP take-off recovery only, the executor uses the assist only when:
  - destination is exactly one block higher;
  - destination is horizontally adjacent (not same-column);
  - the lower step is physically blocked;
  - headroom is clear;
  - destination AABB fits;
  - destination has real support.
- When all predicates pass, the executor stops the ineffective navigation path, directs
  `MoveControl` toward the exact waypoint and requests a natural `JumpControl.jump()` only
  while grounded.
- No direct velocity impulse, teleport, block placement, BREAK, BUILD, BRIDGE or DROP is used.
- Exact physical occupancy, support and fit remain required before advancing the recovery route
  or restoring the suspended DROP cursor.
- Added trace `DROP_TAKEOFF_STEP_UP_ASSIST`.

The assist deliberately rejects same-column and multi-block vertical movement. Those cases must
use a separately planned, validated route; no arbitrary ascent is permitted.

### Hard expansion budget

- Added `NavExpansionBudgetPolicy.canExpand`.
- `NavPlanner.plan` now checks whether an expansion is permitted before incrementing the counter.
- With a budget of 900, exactly 900 expansions are possible; expansion 901 is never performed.
- The budget value is unchanged.

### Regression coverage

Extended `NavDropTakeoffPolicyTest` from 16 to 25 assertions:
- valid adjacent one-block STEP_UP assist;
- same-column and multi-block recovery rejected;
- missing obstruction/headroom/fit/support rejected;
- exact expansion boundary at 900 and rejection of 901;
- zero budget permits no expansion.

## Files changed

- `src/main/java/com/chipoodle/devilrpg/entity/nav/NavExecutor.java`
- `src/main/java/com/chipoodle/devilrpg/entity/nav/NavPlanner.java`
- `src/main/java/com/chipoodle/devilrpg/entity/nav/NavStepUpRecoveryPolicy.java` (new)
- `src/main/java/com/chipoodle/devilrpg/entity/nav/NavExpansionBudgetPolicy.java` (new)
- `src/test/java/com/chipoodle/devilrpg/entity/nav/NavDropTakeoffPolicyTest.java`

## Validation

ChatGPT has not executed Gradle, lint, compilation, or Minecraft runtime. All checks remain
**PENDING HARNESS** on the exact branch HEAD.

Expected static test output:
```text
NAV_DROP_TAKEOFF_POLICY_TEST: OK (25 assertions)
NAV_ROUTE_NODE_POLICY_TEST: OK (6 assertions)
```

Harness must run static checks first and then A2 Run 1 only. Capture:
- `DROP_TAKEOFF_STEP_UP_ASSIST` with start/target and physical movement;
- exact waypoint occupancy, support and fit;
- `DROP_TAKEOFF_RECOVERY_REACHED`;
- restoration of the original DROP route and cursor;
- final DROP validation and physical landing;
- planner expansion count never above 900, including empty/rejected plans.

IDX7 BUILD and IDX8 invalid-local-waypoint remain independent and untouched. No Run 2 unless
Run 1 reaches 8/8 strict. No full 12k until five consecutive 8/8 strict runs. C4 save/reload
remains OPEN. Original A2/C4 worlds must remain untouched; preserve all local artifacts.
