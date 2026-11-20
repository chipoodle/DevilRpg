# D3 — single-block ascent and BUILD feet/action implementation (2026-11-20)

## Implementation checkpoint

- Branch: `feature/autonomous-village`
- Source implementation commit: `c520198c0b89e5da0d6f2e760b22e8d43fcec7d7`
- Base source HEAD: `900b20e9682bbc9a86f409f62a7fde09259e045c`
- Related forensic evidence: `2026-11-20-D2-SINGLE-BLOCK-ASCENT-FORENSICS.md`
- Production source and policy tests were changed by ChatGPT on GitHub.
- **No compile, Gradle test, lint, diff-check, or runtime was executed by ChatGPT.**
- Minecraft was not launched; no world or fixture was modified.

## Changes

### 1. Ordinary ascent is capped at +1

- Added `NavVerticalTransitionPolicy`; ordinary delta 0 maps to WALK, +1 to STEP_UP, and +2/+3 return no ordinary transition.
- `NavPlanner.SUBIDA_MAXIMA` now uses `MAX_ORDINARY_ASCENT=1`.
- Removed the +2 JUMP_UP classifier from `NavObstruction.classify`.
- `piesResultantes(JUMP_UP)` is fail-closed; the movement labeler no longer maps +2 to JUMP_UP.
- Removed the private +2 jump actuator methods and their 0.62 vertical impulse from `NavExecutor`.
- A legacy JUMP_UP route node is rejected by both arrival and execution; it cannot be consumed by proximity.
- The enum remains for compatibility, not as an ordinary movement transition.
- DROP behavior and its safe-drop bound remain intact.

### 2. BUILD graph state is now the resulting feet cell

- Added `NavBuildTransitionPolicy`.
- A BUILD edge's successor is `placementCell.above()`, the cell where the mob must stand.
- `NavGraphSearch.Edge/Step.actionPos` carries the exact placement cell; `NavRoute.Node.pos` is the resulting feet cell and `actionPos` is the placement cell below.
- Local and global planner reconstruction preserve that split.
- Each BUILD candidate must pass the existing `esConstruible` checks and the new per-edge vertical envelope: ascent at most +1, descent no greater than the existing safe-drop limit.
- Unsafe DROP-to-BUILD fallback remains possible only when the resulting feet delta is inside that same existing envelope.
- Each horizontal search edge is therefore one +1 build step; a +3 rise must be represented as three independently placeable BUILD edges. An unavailable intermediate placement rejects that path.

### 3. Executor performs one placement and waits for physical traversal

- `activeActionTarget()` returns BUILD's `actionPos`, so the existing exact-target lease authorizes the placement cell, not the feet waypoint.
- BUILD acquires the lease before placement, calls the placement callback with the exact action cell, rereads and verifies the block, and records that action cell in `construidas`.
- A verified placement is not repeated on later ticks. The executor instead moves toward `node.pos`.
- Route index does not advance on placement or proximity. BUILD arrival requires the placement to be in the verified constructed set and still solid, exact feet-cell occupancy, grounded state, support, valid feet, and a fitting real AABB.
- Missing actionPos, occupied unexpected placement cells, lease denial, failed placement, failed verification, or invalid post-build geometry fail closed.
- No BUILD-to-BREAK/BRIDGE conversion was added.

## Files changed

- `src/main/java/com/chipoodle/devilrpg/entity/nav/NavVerticalTransitionPolicy.java` (new)
- `src/main/java/com/chipoodle/devilrpg/entity/nav/NavBuildTransitionPolicy.java` (new)
- `src/main/java/com/chipoodle/devilrpg/entity/nav/NavObstruction.java`
- `src/main/java/com/chipoodle/devilrpg/entity/nav/NavPlanner.java`
- `src/main/java/com/chipoodle/devilrpg/entity/nav/NavRoute.java`
- `src/main/java/com/chipoodle/devilrpg/entity/nav/NavRouteNodePolicy.java`
- `src/main/java/com/chipoodle/devilrpg/entity/nav/NavClimbPolicy.java`
- `src/main/java/com/chipoodle/devilrpg/entity/nav/NavDropTransitionPolicy.java`
- `src/main/java/com/chipoodle/devilrpg/entity/nav/NavExecutor.java`
- `src/test/java/com/chipoodle/devilrpg/entity/nav/NavVerticalBuildPolicyTest.java` (new)
- `src/test/java/com/chipoodle/devilrpg/entity/nav/NavPlannerStateTest.java`
- `src/test/java/com/chipoodle/devilrpg/entity/nav/NavRouteNodePolicyTest.java`
- `src/test/java/com/chipoodle/devilrpg/entity/nav/NavClimbPolicyTest.java`
- `src/test/java/com/chipoodle/devilrpg/entity/nav/NavDropTakeoffPolicyTest.java`
- `src/test/java/com/chipoodle/devilrpg/entity/nav/NavArrivalPolicyTest.java` (test-only follow-up commit)
- `build.gradle` (registers the new policy test)

## Source blob verification references

These blob SHAs were fetched from the documentation-updated branch tip after confirming it is a documentation-only descendant of the source implementation commit:

| File | Blob SHA |
|---|---|
| `NavVerticalTransitionPolicy.java` | `682573c6caf4b6f43d49df0404170f69b0408784` |
| `NavBuildTransitionPolicy.java` | `328d7217f387337b6d4296e2a8a33c1f388476a9` |
| `NavObstruction.java` | `821c393fcc2c8a4f6d2b505bf578a7a3d3366f8e` |
| `NavPlanner.java` | `27f8387d6fe52f0ff18aa46e9bef75f8a22b0f4f` |
| `NavRouteNodePolicy.java` | `f13243d1032e35b4fdf9b27f43a75247e6d337db` |
| `NavExecutor.java` | `4e73cd3b9bf82dfe449fd0731b126eda32eaf819` |
| `NavVerticalBuildPolicyTest.java` | `23fa8566fdc8359291c5cb933f9348a794b79896` |
| `NavPlannerStateTest.java` | `dcd963ccf80b8a4ff11d05a70f8144bfa98aae7c` |
| `NavRouteNodePolicyTest.java` | `8ad85d6fa345ae12d3c18ac9bdae04546358fac7` |
| `NavArrivalPolicyTest.java` | `8dcd33b7bc9e9d0e3dafd0c98ec2f0cba63065ab` |
| `build.gradle` | `352dc1319f0863105f0c2347df2ab857f5f1323c` |

## Policy tests authored — NOT RUN

- `NAV_VERTICAL_BUILD_POLICY_TEST`: 32 assertions, including +0/+1/+2/+3 vertical policy, a three-edge A* BUILD chain, feet/actionPos split, non-buildable intermediate rejection, no repeated placement, and physical-arrival gates.
- `NAV_PLANNER_STATE_TEST`: updated JUMP_UP compatibility expectation and direct BUILD feet-successor checks.
- `NAV_ROUTE_NODE_POLICY_TEST`: 8 assertions, including BUILD feet/action separation.
- `NAV_CLIMB_POLICY_TEST`: JUMP_UP is no longer a standing waypoint.
- `NAV_DROP_TAKEOFF_POLICY_TEST`: legacy JUMP_UP recovery route is rejected.
- Existing policy tests remain registered through `policyTest`; the new test is registered in `build.gradle`.

## Preserved invariants

- Existing placement preconditions in `esConstruible`: protection, `canPlace`, air/headroom and support-face checks.
- Placement callback's own support, headroom, collision/entity-intersection, protection and verification checks.
- Exact per-transition/per-target traversal lease.
- Existing budgets, cooldowns, maximum construction counts, action ranges and A2/C4 criteria.
- No reactivation of removed goals or alternate movement authority.
- No changes to siege fixtures, worlds, Harness scripts or acceptance thresholds.

## Harness handoff — STATIC ONLY

Harness must first follow the collaboration policy and preserve local-only changes, untracked files, backups, logs, partial files, artifacts and both diagnostic worlds. Safe fast-forward only; stop on divergence or overwrite risk. Harness must not modify production.

Sync the current remote branch tip after recording local/remote state. The source implementation is commit `c520198c0b89e5da0d6f2e760b22e8d43fcec7d7`, followed by test-only commit `63276177583dd3f43f93a06700e4b3e52a6e3be6` and documentation commits. Verify every production/test blob in the table above (plus the affected climb/DROP policy files) before running checks on the exact synced tip.

Run the established static gate, with canonical diagnostic source selection:

```powershell
.\gradlew.bat compileJava --info -Pa2nDiagnostics=true -Pa2nPopulationQueryHarness=true -Pa2nTraceNav=true -Pa2nTraceGlobalSearch=true
.\gradlew.bat test --info -Pa2nDiagnostics=true -Pa2nPopulationQueryHarness=true -Pa2nTraceNav=true -Pa2nTraceGlobalSearch=true
python tools\lint_aldea.py --strict
git diff --check
```

Additionally audit:
1. `SUBIDA_MAXIMA=1`; no ordinary producer maps +2/+3 to JUMP_UP.
2. `NavObstruction.classify` no longer emits JUMP_UP; `NavExecutor` has no 0.62 impulse, `iniciarSalto`, or `tickSalto`; legacy JUMP_UP fails closed.
3. BUILD graph successor and route node position are the feet cell; actionPos and activeActionTarget are the exact placement cell.
4. BUILD lease is acquired for actionPos before placement; exact placement is verified; the verified block is not placed twice.
5. Route cursor advances only after exact grounded feet occupancy, support, valid feet and real-AABB fit.
6. `NAV_VERTICAL_BUILD_POLICY_TEST` reports 32 assertions; update counts/results for all affected policy tests.
7. No budgets, cooldowns, protections, support/headroom rules, A2/C4 criteria or fixtures changed.

Record exact tested SHA, commands/exit codes, test counts, source blobs, diff review, and preserved local artifacts. **Stop after static validation. Do not launch Minecraft, runServer, A2, C4, Run2 or Full12K.** Runtime requires a separate explicit authorization.

## Current acceptance status

- A2: **OPEN**. The +3 IDX7 runtime scenario has not been rerun.
- C4: **not revalidated** by this implementation.
- Runtime physical traversal: **NOT RUN**.
- Static validation: **PENDING Harness**.
