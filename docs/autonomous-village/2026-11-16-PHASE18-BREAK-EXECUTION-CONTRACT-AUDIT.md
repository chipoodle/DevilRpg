# Phase 18 — BREAK Execution Contract Audit (2026-11-16)

## Git baseline

- Branch `feature/autonomous-village`.
- Initial local/remote HEAD: `0d0d4dc40b09833c975f1496425dd96bbd59062f`.
- No divergence.
- **No gameplay changes. Documentation only.**

## IDX8 — isolated evaluator status

A truly isolated runtime `walkNodeEvaluator` query was **not executed**. Reason: the existing probe runs
inside the live navigation owner; `PathNavigation.createPath` returns the active path when its target
matches `targetPos`, and no separate evaluator/`PathNavigationRegion` instance is wired into the harness.
Reusing the live evaluator would risk mutating production navigation state.

Source-level result *is* conclusive about the one-node shape:

`PathFinder.findPath` (installed 1.21.1 sources, lines 111–117) reconstructs the best node path even when
no target is reached:

```java
Optional<Path> optional = !set2.isEmpty()
    ? ... reconstructPath(..., true)
    : set.stream().map(target -> reconstructPath(target.getBestNode(), target.getPos(), false))
        .min(comparingDouble(Path::getDistToTarget)...);
```

If the best node is the start node, the result is a **one-node path with `canReach=false`**. That is
exactly IDX8's observed shape. It is a failed-search fallback, not an accepted solution. The exact
reason the search could not improve on the start node (target walkability/support/search limits) is
still not decomposed; **IDX8 geometric cause remains UNKNOWN**.

## Real BREAK flow

1. `NavPlanner.validarArista(from, candidate)`:
   - `NavObstruction.classify` returns `BREAKABLE` for a solid, breakable, unprotected cell.
   - `transitionOf(BREAKABLE)` returns `BREAK`.
   - `piesResultantes(BREAKABLE)` returns `candidate.getY()`.
   - It requires `NavObstruction.hasSupport(candidate)` and rejects unsafe vertical delta.
2. The successor is built by `NavPlanner.estadoSucesor(candidate, BREAK, piesHasta)`.
   For `BREAK` it returns **`candidate` itself**, i.e. the solid block position.
3. `NavRoute.Node` therefore carries the solid BREAK cell as its waypoint.
4. `NavExecutor.traverse` in `case BREAK, BRIDGE`:
   - if the target is already air → advance route index and replan;
   - compute `distNodoSqr`;
   - if `> ALCANCE_DE_ACCION_SQR`, call `PathNavigation.moveTo(node.pos())` on the solid cell;
   - inspect the returned path's `canReach`;
   - raycast `primerBloqueoEnLineaDeVision(node.pos())`;
   - delegate only if `NavActionApproachPolicy.debeDelegarPrimerBloque(!pathCanReach, blockerInReach, true)`;
   - if the blocker is outside `ALCANCE_DE_ACCION_SQR`, it just keeps approaching;
   - if `dist <= ALCANCE_DE_ACCION_SQR`, set `nodoDeRecuperacion = node.pos()` and state `RECOVERING`.
5. Recovery later calls `attemptOneRecovery()` → `recoveryAction.run(towards, node, BREAK)` and verifies
   the block became air. The action is not executed immediately; it waits for recovery scheduling/cooldown.

## Exact contract break

**`NavPlanner` validates that the BREAK target is breakable and supported, but never validates that a
reachable standing cell exists from which the mob can actually attack it.**

- The graph node is the solid target, not an attack position.
- `NavBreakReachPolicy.reachable(feetY, nodeY, distanceSqr, actionReachSqr)` exists but is **not used
  anywhere in production** (grep: zero references).
- There is no search for an adjacent traversable attack cell with full AABB clearance, support, line of
  sight, and action-range distance.
- When PathNavigation cannot reach the solid node, the only fallback is the first raycast blocker; if
  that blocker is >2.5 blocks away, no action is delegated. The executor can only keep issuing the same
  unreachable `moveTo`, then obstruct/fail or exhaust its no-progress budget.
- Even when within range, the break is deferred to recovery rather than executed by the BREAK branch.

This explains the observed Phase-12/16 sequence: `BLOCKER_OUT_OF_REACH`, repeated approach, terminal
`presupuesto agotado sin progreso`, and one-node non-reaching paths. It does **not** claim that IDX8's
specific target is intrinsically non-walkable; it explains why an unvalidated plan can be non-executable.

## Answers to required questions

1. **Graph positions:** it represents the **block to break**, not an attack position.
2. **Edge guarantee:** none. Only target support/breakability is checked; source action feasibility is not.
3. **`moveTo` to a solid block:** vanilla can return a best-node path, including a one-node
   `canReach=false` fallback, rather than a usable path.
4. **First blocker >2.5 blocks:** no delegation; the executor keeps approaching until obstruction/budget.
5. **Adjacent valid cell search:** **no**.
6. **Alternative attack positions:** **no**; only the first raycast blocker.
7. **Replan after break:** yes, once the block is verified air, route index advances, caches clear and
   state returns to PLANNING.
8. **Protection:** yes at the door (`puedeRomperNodoDeNavegacion` and `BlockInteractionPolicy`), but the
   planner's BREAK edge is gated by `policies.canBreak`, not by an attack-position check.
9. **Same issue in BUILD/JUMP/STEP:** yes in kind: BUILD validates placement and resulting landing, and
   step/jump validate landing, but none validate that the **physical transition is executable from the
   predecessor state**. BREAK is the clearest because the node itself is solid.

## Design evaluation (not implemented)

### Option A — Action-aware route nodes (recommended)

`NavRoute.Node` gains optional action metadata: `actionPos`, `standPos`, preconditions, cost. For BREAK,
the edge ends at a validated adjacent standing cell; the break target is an action target. The planner
searches candidate stand cells around the block, validates full AABB, support, LOS/raycast, protection,
and action range, and chooses the cheapest.

- Pros: preserves planner correctness; executor only navigates standable cells; retries/alternatives
  are explicit; fixes BREAK and gives a model for BUILD/JUMP/STEP.
- Cons: larger graph expansion, more planner cost, changes NavRoute serialization/diagnostics; needs
  careful cache invalidation.
- Files: `NavRoute`, `NavPlanner`, `NavExecutor`, maybe `NavGraphSearch`, new `NavActionPositionPolicy`.
- Tests: planner edge tests for no-stand-cell, LOS, range, protection; executor integration tests;
  scenario A2.

### Option B — Executor-side attack-position search

Keep the plan as-is; when `BREAK` is unreachable, run a bounded local search for an adjacent attack
cell, then navigate there and delegate the break.

- Pros: smaller planner change; can fix existing routes; immediate.
- Cons: planner still promises non-executable edges; runtime cost during failure; duplicated geometry
  rules; harder deterministic tests; may mask planner defects.
- Files: `NavExecutor`, new `NavBreakAttackPositionPolicy`, `NavBreakReachPolicy` (currently unused).
- Tests: search validity/range/LOS/protection; no regression of budgets.

### Option C — Hybrid

Planner annotates a stand position when cheaply available; executor performs bounded fallback search
otherwise. Best long-term behavior but highest complexity.

**Recommendation:** Option A, with Option B only as a temporary guarded fallback. Do not change action
range or budgets.

## Complexity / risk

- Option A: medium-high (~2–4 days), moderate risk in planner performance and route compatibility.
- Option B: medium (~1–2 days), higher risk of duplicated geometry and runtime cost.
- Both require deterministic regression tests and a focused A2 run; no 12k until the focused gate.

## Tests required

- Unit: attack cell selection (clearance/support/LOS/range/protection), no-cell rejection, cost.
- Planner: BREAK edge cannot exist without an executable stand position.
- Executor: navigates to stand cell, breaks target, verifies air, advances route, replans.
- Integration: IDX7/IDX8 scenario, focused A2, C4 12/12.

## What was not done

- No isolated runtime `WalkNodeEvaluator` query (no safe separate evaluator wiring; live evaluator reuse
  would risk observer effects).
- No gameplay change, no action-range/budget/acceptance change.
- No A2 full run; no IDX7 reproduction campaign.

## Status

- IDX8: one-node path shape explained as a failed-search fallback; target geometry **UNKNOWN**.
- BREAK contract: **confirmed broken** at the planner/executor boundary.
- IDX7: remains BLOCKED (required reachable-path state not reproduced).
- A2 OPEN; C4 unchanged; IDX5 6164 boundary UNKNOWN.
