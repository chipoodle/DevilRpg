# Phase 19 — BREAK execution and bounded navigation recovery

## Baseline and scope

- Branch: `feature/autonomous-village`
- Base HEAD: `8f5a74fb9137abbac0a53dc44c981e81d22f9062`
- Scope: BREAK edge executability, failed-edge memory, bounded recovery/backtracking.
- ChatGPT did not access the local PC, PowerShell, Gradle, Minecraft runtime, Harness, or diagnostic worlds.

## BREAK contract

- Shared local/global planner validation requires the predecessor (at its tracked feet Y) to be a valid attack stand: full AABB clearance, support, breakable/unprotected target, existing action/vertical reach, and a collision raycast whose first hit is the intended target.
- A failed stand->target edge is held in a bounded exclusion map (32 entries) while its relevant terrain fingerprint remains unchanged. Changed geometry makes it eligible again.
- Executor handles BREAK before the legacy BRIDGE branch. It never calls `moveTo` on the solid target. It revalidates the physical stand, invokes the existing RecoveryAction on the target, verifies the target is air, and invalidates plans/caches before replanning. Failed actions exclude only that edge so another approach side can be searched.

## Recovery and backtracking

- `NavRecoveryController` retains at most 16 physically occupied positions. The executor records only currently valid, supported, collision-clear feet cells.
- First consecutive no-progress route invalidation: replan from the actual position.
- Second consecutive failure: permit one backtrack to a previously reached cell that is still valid and differs from current/last backtrack.
- The selected cell becomes a tactical objective; the strategic mission remains preserved and arrival handling resumes it.
- Measured progress toward the strategic mission rearms the bounded backtrack.
- Existing expansion, recovery, action-range, collision and acceptance budgets are unchanged.

## Tests added (not executed here)

- `NavBreakActionPolicyTest`: 8 pure assertions for clearance, support, protection, break permission, reach, LOS and vertical reach.
- `NavRecoveryControllerTest`: 7 assertions for retry stages, historical selection, one-backtrack bound, progress rearm and reset.

Harness must execute Gradle tests, strict lint and diff-check, followed by targeted runtime validation. No A2/C4 PASS is claimed. The canonical A2 gate remains five consecutive 8/8 strict `DENTRO-PARED` runs; no 12k campaign before that gate.

## Runtime verification boundary

Planner validates the predecessor at its tracked feet Y; executor validates the actual occupied cell when the BREAK node becomes active. Runtime must verify this handoff especially after DROP/BUILD. If the mob is not at a valid predecessor, preserve the rejection and report exact transition/coordinates; never navigate to the solid target or weaken the contract.


## Follow-up source review

- Removed a chunk-query call from the policy's `CollisionGetter` abstraction; planner callers already classify world cells and the policy must compile against the shared collision interface.
- Protected the one-shot backtrack objective from repeated submissions of the strategic mission. Without this guard, a goal refresh could overwrite the temporary backtrack before it was reached. The guard clears on backtrack arrival, mission change, or objective clear.
- Added a dedicated test for BREAK-edge exclusion and terrain-fingerprint invalidation.


- Reach distance deliberately preserves the pre-existing metric (mob feet position to target center) and the existing 6.25 squared range. The raycast still originates at the eye position. Planning uses the hypothetical stand-cell center; execution uses the mob's exact position.


- The existing BREAK actuator has a 40-tick inter-block cooldown. A false return is therefore retried once after the existing 40-tick recovery cooldown; only a second unverified attempt excludes the unchanged stand->target edge. A different edge resets this transient retry state immediately.


- The same two-consecutive-failure backtrack gate is also fed by a fully exhausted no-route search (not by a hit-budget/inconclusive search), so recovery is not limited to cases where vanilla had already produced a stale route.
