# A2 Phase 44 — bounded non-adjacent DROP approach owner (2026-11-18)

## 1. Implementation status

**IMPLEMENTED — PENDING HARNESS VALIDATION.** This phase adds a DROP-only movement owner for the supported approach waypoint selected by the existing `NavDropApproachPolicy`. It addresses Phase43's missing continuous movement ownership without changing planner budgets, route geometry, safety predicates, or the adjacent Phase41 edge-step.

- Repository: `chipoodle/DevilRpg`
- Branch: `feature/autonomous-village`
- Implementation parent: `793816686e4373d4b581264468f2ee92ce431156`
- Production implementation commit: `b4f53966f7336d997ace224660d1a5cb892e2786`
- Lifecycle hardening commit: `5b71f063798fc0fd50be4dd2b4d3d5c58041262a`
- Final movement-release commit: `ed31b815193aa4fcde0b088f1a6d110270ba4a9d`
- Phase43 forensic report: `2026-11-18-A2-PHASE43-NON-ADJACENT-DROP-FORENSICS.md`
- Current A2 status: **OPEN**
- No tests, lint, compile, or Minecraft runtime have been executed by ChatGPT.

## 2. Behavioral contract

1. The existing `NavDropApproachPolicy.choose` continues to select the supported, same-height approach cell. Its validation remains `esPieValido`, solid support, and `fitsAtCellCenter`.
2. The executor first submits the existing `Navigation.moveTo` command and records the selected waypoint and landing.
3. While Navigation remains active and makes measurable progress, it retains movement ownership.
4. If Navigation completes before the waypoint is physically reached, or makes no measurable progress for more than 20 ticks, the DROP approach transfers to `MoveControl`.
5. The MoveControl owner is DROP-only, targets the center of the already validated waypoint at the mob's current Y, and never injects velocity, teleports, jumps, breaks/builds, or bypasses vanilla collision.
6. While MoveControl owns the approach, every tick revalidates the waypoint's feet validity, support and centered real-AABB fit. The current source must remain grounded, feet-valid, supported under the real AABB and free of real-AABB collision.
7. The waypoint is considered reached only when `blockPosition()` equals it, the mob is grounded, and the waypoint safety predicates still pass. Proximity alone never advances the route.
8. Once reached, the temporary owner is released and any pending Navigation path is stopped. The same traversal pass re-enters the existing adjacent DROP branch, where Phase41's unchanged edge-step can take ownership and initiate the natural fall.
9. The approach owner fails closed after more than 80 ticks or more than 20 ticks without progress. It releases movement, sets `OBSTRUCTED`, and leaves the route's physical landing invariant intact.
10. Ownership is cleared on route-node change, landing confirmation, route null and post-snap reset.

The 80/20 values are local actuator bounds for this new owner; no global/local planner expansion budgets, retry budgets, DROP safety limits, or arrival tolerances were changed.

## 3. Code changes

- `NavDropApproachOwnershipPolicy.java`: pure policy for DROP-only takeover, timeout, no-progress and measurable progress.
- `NavDropApproachOwnershipPolicyTest.java`: 13 deterministic assertions covering transition scope, active path retention, completed-path takeover, physical-arrival exclusion, stall takeover, timeout/no-progress boundaries and progress threshold.
- `NavExecutor.java`: pending/owned approach state, Navigation-to-MoveControl handoff, per-tick safety checks, physical arrival verification, bounded failure and cleanup.
- `build.gradle`: registers the new policy test in `policyTest` / `gradlew test`.

New trace events:
- `DROP_APPROACH_OWNER_SELECTED`
- `DROP_APPROACH_OWNER_PROGRESS`
- `DROP_APPROACH_OWNER_REACHED`
- `DROP_APPROACH_OWNER_ABORTED`
- `DROP_APPROACH_OWNER_CLEARED`

`DROP_APPROACH_WAYPOINT_COMMAND` now also records `pathAccepted` and `navDone` immediately after the command.

## 4. Preserved invariants

- Phase29 exclusive snap ownership and Phase31 post-snap reset are unchanged.
- Phase34 supported approach selection is unchanged.
- Phase36 adjacent approach behavior is unchanged.
- Phase41 `NavDropEdgeStepPolicy`, edge-step thresholds, support clearing, natural gravity and collision behavior are unchanged.
- No planner budgets, route expansion limits, collision/support/protection/headroom/fit predicates, landing tolerance, combat, guards, C4, spawn or acceptance criteria changed.
- No original worlds or local Harness artifacts were accessed or modified by this implementation.
- A2 remains OPEN. Run2 and Full12K remain prohibited until the existing five-consecutive 8/8 strict gate is met.

## 5. Validation status

**Authored, not executed by ChatGPT:**
- `NAV_DROP_APPROACH_OWNERSHIP_POLICY_TEST` — 13 assertions.
- Registered in the existing `policyTest` task and therefore `gradlew test`.

**PENDING HARNESS on exact production SHA `ed31b815193aa4fcde0b088f1a6d110270ba4a9d`:**

1. Inspect local branch, HEAD, remote HEAD, divergence, worktree and untracked files. Preserve local-only files, scripts, logs, backups and worlds. Fast-forward only if safe.
2. Run static validation first: `gradlew test`, strict lint (`python lint_aldea.py --strict`), `git diff --check`, and diagnostics compile (`gradlew compileJava -Pa2nDiagnostics=true`). Record exact commands, exit codes and tested SHA.
3. Confirm `NAV_DROP_APPROACH_OWNERSHIP_POLICY_TEST: OK (13 assertions)` and all previously required navigation policy tests.
4. Only if every static gate passes, execute **A2 Run1 only** in the disposable Harness world. Do not run Run2 or Full12K.
5. Capture per attacker, especially IDX3 UUID `7e20a00f-1101-48af-9af7-b4cb4790d353`, IDX5 UUID `fd459775-58ce-464d-809d-bb59f7870e97`, and IDX4 UUID `5ea20f89-7f94-47a5-9e55-46432e659ba8`:
   - waypoint command with `pathAccepted` and immediate `navDone`;
   - owner selection reason (completed Navigation path vs. no progress);
   - position/distance/progress while MoveControl owns movement;
   - exact grounded physical waypoint arrival;
   - subsequent Phase41 edge-step selection, support-cleared, natural descent, landing confirmation and cursor advance;
   - any owner abort reason and whether the mob remains safely supported.
6. Confirm no competing Navigation movement command while MoveControl owns the approach; no unsupported intermediate crossing; no predicate/budget changes; no GUI/focus intervention.
7. Verify protected A2/C4 world hashes remain:
   - A2 `BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7`
   - C4 `11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF`
8. Write a new dated validation report and update all three continuation handoffs. Harness must not modify production source.

If a static check fails, do not launch A2. If runtime does not reach 8/8 strict, A2 remains OPEN and the next work must be based on captured evidence; do not relax any invariant.

## 6. Integrity

```text
PRODUCTION_MODIFIED_BY_CHATGPT: true (authorized Phase44 implementation)
TESTS_ADDED_BY_CHATGPT: true (not executed here)
HARNESS_VALIDATION: PENDING
A2_RUNTIME: NOT RUN BY CHATGPT
RUN2_EXECUTED: false
FULL12K_EXECUTED: false
ORIGINAL_WORLDS_MODIFIED: false
A2_STATUS: OPEN
```
