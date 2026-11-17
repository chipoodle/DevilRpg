# IDX4 — exact bounded A* recovery and suspended-route preservation

Date: 2026-11-17

## Status

**IMPLEMENTED — PENDING HARNESS STATIC AND RUNTIME VALIDATION.**

The Phase 21 Harness run validated the grounded AABB source snap, but the old local recovery search
expanded 900 nodes over a 12-cell horizontal radius and returned an empty route. Source inspection
also found that this rejected temporary search discarded the original route, cursor and plannedFrom.

This change does not claim that IDX4 or A2 now passes. Harness must validate the exact published SHA.

## Phase 21 evidence retained

- Tested SHA: `77b635d49a6eaccc8747fce2f62ad7228aeb940a`
- Run: `a2n-20261117-111418`
- A2: **FAIL 5/8 strict, 5/8 perimeter**.
- Static: PASS; policy tests 10/40/6 executed.
- C4: PASS `[2,0,0]`; save/reload remains OPEN.
- IDX4 source snap reached `596,77,499`, grounded with feet/support/real-AABB valid.
- Snap exact position `596.504,77.000,499.310`; center target `596.500,77.000,499.500`.
  Horizontal radial error is 0.19, inside the implementation's explicit 0.20 tolerance.
  Therefore exact floating-point center equality is not required; cell occupancy plus feet/support/current
  real-AABB checks remain mandatory.
- Recovery search `596,77,499 -> 596,79,498` returned empty at 900/900.
- Original DROP had no verified `DROP_DONE`; no route/cursor/plannedFrom restoration evidence.
- IDX7/IDX8 remained independent failures; Run 2 and 12k were not run.

Historical report:
`2026-11-17-A2-RUN1-AABB-SOURCE-RECOVERY-PHASE21.md`.

## Implementation

### 1. Exact-goal A* for the temporary take-off recovery

`NavPlanner.planExact(...)` reuses the existing validated global movement graph and
`NavGraphSearch` implementation, which reopens improved states and terminates when the goal is
extracted. Unlike strategic global navigation's arrival region, it requires both the state cell and
feet Y to equal the exact take-off.

The recovery search is bounded to:
- Horizontal radius: 4 cells.
- Vertical envelope: 3 feet levels above/below the grounded source.
- Expansion cap: the existing `MAX_EXPANSIONS_PER_PLAN` (900); **not increased**.

For exact recovery only, the graph prunes:
- Any transition other than WALK, STEP_UP, JUMP_UP or DROP.
- BREAK, BUILD and BRIDGE.
- DROP edges whose validated landing is not exactly one block below the source feet.
- States outside the bounded vertical envelope.

The returned route is accepted only when `alcanzoMision=true`, the budget was not hit, the route
destination equals take-off, the final physical waypoint equals take-off, and the recovery transition
policy accepts every edge. Partial/frontier routes are never accepted.

### 2. Preserve the original strategic route on failed recovery planning

Previously, a rejected auxiliary plan cleared:
- `route`
- `routeIndex`
- `plannedFrom`
- global route cache/mission state

That contradicted the documented suspend/restore contract. Now a rejected temporary search leaves
the original route, cursor and `plannedFrom` untouched. It schedules a bounded retry using the
existing replan cooldown and emits `DROP_TAKEOFF_RECOVERY_PLAN_RETRY_WAIT`; it does not execute the
DROP while recovery is pending.

After a valid exact recovery route completes, the executor restores the original route and cursor,
restores the captured `plannedFrom`, and emits
`DROP_TAKEOFF_RECOVERY_ROUTE_RESTORED` with signature/cursor/origin evidence.

### 3. Strict route geometry

`rutaRecuperacionOrigenSegura` now also requires the final route node position to equal the exact
take-off. It validates vertical labels:
- WALK: `dy=0`
- STEP_UP: `dy=+1`
- JUMP_UP: `dy=+2`
- DROP: exactly one block down, at most once

This prevents a partial route with a declared destination from masquerading as exact arrival and
prevents mislabeled vertical edges.

### 4. Source snap tolerance decision

Keep the existing 0.20-block radial center tolerance. Phase 21's observed 0.19 offset is within that
contract, and the mob occupied the candidate cell while grounded with valid feet, support and real
AABB fit. Do not tighten to exact floating-point equality; it would reject physically valid occupancy
without adding a meaningful collision guarantee.

## Changed files

- `NavPlanner.java`: exact-goal bounded A* entry point and exact recovery graph restrictions.
- `NavExecutor.java`: exact A* invocation, retry backoff without destroying the original route,
  explicit route/cursor/`plannedFrom` restoration trace.
- `NavDropTransitionPolicy.java`: exact final waypoint and transition/vertical-delta checks.
- `NavDropTakeoffPolicyTest.java`: three additional assertions; expected total **43**.

The Phase 21 snap implementation and source-cell policy are unchanged. No A2/IDX7/IDX8/C4 acceptance
criteria, production movement budgets, collision/support checks, or world files were changed.

## Harness instructions

Synchronize safely to the current branch HEAD, then run:
1. `.\gradlew.bat test`
2. `python tools/lint_aldea.py --strict`
3. `git diff --check`
4. `.\gradlew.bat compileJava -Pa2nDiagnostics=true`

Expected policy counts:
- `NAV_SOURCE_CELL_POLICY_TEST: OK (10 assertions)`
- `NAV_DROP_TAKEOFF_POLICY_TEST: OK (43 assertions)`
- `NAV_ROUTE_NODE_POLICY_TEST: OK (6 assertions)`

If any static gate fails, do not start Minecraft.

If all static gates pass, run **A2 Run 1 only**, in a disposable world, preserving all original worlds and
local artifacts. For IDX4 capture:
- Source candidate/snap and exact offset; grounded, feet, support and real-AABB evidence.
- Exact planner summary including radius, vertical envelope, expansions, hitBudget, found/reached,
  front and route transitions.
- Whether the exact take-off state was extracted; reject any approximate/frontier route.
- Route acceptance and every physical recovery waypoint.
- Exact take-off occupancy.
- `DROP_TAKEOFF_RECOVERY_ROUTE_RESTORED`: original route signature, cursor, route length and
  `plannedFrom`.
- Original DROP command, `DROP_DONE` for the correct landing, and verified cursor advancement.
- Confirm the original route/cursor/origin remain intact across any rejected search/retry.

No Run 2 unless Run 1 reaches 8/8 strict. No full 12k until five consecutive 8/8 strict runs.
IDX7 and IDX8 remain independent; do not change their policies in this validation. C4 save/reload
remains OPEN.

## Git checkpoint

Production checkpoint: pending final documentation commit on `feature/autonomous-village`.
Tests, lint, diagnostics compilation and Minecraft runtime have **not** been executed by ChatGPT.
