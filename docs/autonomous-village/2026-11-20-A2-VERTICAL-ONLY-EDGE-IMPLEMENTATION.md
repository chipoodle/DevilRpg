# A2 — same-column vertical edge coverage correction (2026-11-20)

## Status

**IMPLEMENTED on GitHub; static validation pending Harness; runtime not yet executed.** This is the source correction prepared for the owner's single remaining A2 Run1 authorization.

- Repository: `chipoodle/DevilRpg`
- Branch: `feature/autonomous-village`
- Final implementation/documentation HEAD: `3a4767327ef9697650f8ba43dedf90c2dcfeeba4`
- Production source change commit: `7e78c9360c7960f13da6062f38196e8b1627b93a`
- Base documentation/source tip: `aea1372ed0ca60fe5e65af278abc7bc276bf2bbf`
- Prior R2 static PASS applies to the prior source only; it does **not** validate this new change.
- ChatGPT did not execute Gradle, tests, lint, Minecraft or runtime.

## 1. Evidence and root cause

The read-only report `2026-11-19-A2-PHASE55-ASTAR-FIRST-EXPANSION-FORENSICS.md` demonstrated that IDX3 and IDX5 were inside 1×1 vertical shafts:

- The graph expanded 8 horizontal offsets × 7 vertical candidate levels = 56 candidates.
- All 56 were rejected; zero successors were enqueued; A* ended after one expansion.
- The eight adjacent columns were solid throughout the vertical window.
- The graph's direction set omitted the same-column `(0,0)` direction. A vertical-only descent through the shaft therefore could not be represented, even when a landing within the existing safe-drop envelope was physically available.

The 2026-11-20 Run1 repeated the first-expansion failure for IDX3/4/5 (56 candidates, zero enqueued for the reported starts). That run's summary also recorded 40 BREAKABLE/NODO_NO_PISABLE rejections; exact BREAK stand/LOS reasons remain a separate diagnostic question and are required in the final run report.

This is a graph-coverage defect, not an A* expansion-budget problem. D* Lite cannot solve a missing edge in the movement graph.

## 2. Correction

Added `NavNeighborPolicy` with nine candidate directions:

- Four cardinal horizontal directions.
- Four diagonal horizontal directions.
- One same-column direction `(0,0)`, which combines with the existing bounded `dy` loop to generate vertical-only candidate edges.

Both local and global planner loops use the shared `OFFSETS` array, so both can represent a vertical-only movement candidate.

**The new direction does not authorize movement by itself.** Every generated candidate still passes through the existing single `validarArista` path. The existing checks remain authoritative, including:

- actual classification and break/place policy;
- valid resulting feet cell;
- support and headroom;
- collision and real geometry;
- protection;
- safe-drop depth and validated physical landing;
- DROP exclusions;
- BUILD placement feasibility and vertical envelope;
- BREAK action stand, reach and line of sight.

No fall limit, support predicate, protection rule, budget, search radius, cooldown, action range or acceptance criterion was changed. A same-column fall deeper than the existing safe-drop limit remains rejected.

## 3. Heuristic contract

Updated only the `NavGoalMetric` documentation to state the correct lower-bound argument for the expanded graph: each edge's cost is at least its horizontal Euclidean displacement; a vertical-only edge contributes zero horizontal displacement. The existing vertical lower bound remains based on the maximum permitted step-up/safe-drop. The heuristic implementation and costs are unchanged.

## 4. Regression coverage

Added `NavNeighborPolicyTest`, registered in Gradle's `policyTest` task.

It verifies:
- exactly nine directions;
- all eight prior horizontal directions remain;
- the same-column direction is present exactly once;
- directions are unique;
- returned arrays cannot mutate the canonical policy.

Expected output: `NAV_NEIGHBOR_POLICY_TEST: OK (16 assertions)`.

The test is authored but **NOT RUN**. Harness must execute it as part of the complete static gate.

## 5. Files and blobs at final HEAD

| File | Blob SHA |
|---|---|
| `NavNeighborPolicy.java` | `e0378421c0ea024b34b4f95354e5780db78202d9` |
| `NavPlanner.java` | `274af17a32a7aa435a26457f883438513ce207dc` |
| `NavGoalMetric.java` | `7d5b2c6e91555965caa592b7c328bee056186f0f` |
| `NavNeighborPolicyTest.java` | `0ffd6323dc21a3f9c1dd52f23c71570d6d475625` |
| `build.gradle` | `ab8e949c055676df0f4cd0e4069c9839f80f1382` |

## 6. Explicitly unchanged

- A* implementation, OPEN/CLOSED behavior, stale-record handling and budgets.
- Global radius, vertical candidate range, expansion slice and total expansion limit.
- Safe-drop, support, headroom, collision, AABB, protection and placement predicates.
- BREAK action authorization, reach, LOS and traversal lease.
- A2 fixture, spawn, wall geometry and strict 8/8 acceptance.
- C4 logic and acceptance.
- No worlds, local files or Harness scripts were modified by ChatGPT.

## 7. Harness handoff

Follow `docs/autonomous-village/2026-11-20-A2-LAST-RUN-HARNESS-HANDOFF.md`.

1. Safe fast-forward to exact final HEAD `3a4767327ef9697650f8ba43dedf90c2dcfeeba4`; inspect divergence and preserve local-only work.
2. Verify the five blobs above and confirm both `NavPlanner` expansion loops use the shared nine-direction policy.
3. Run `compileJava`, Gradle `test` (including `policyTest`), strict `lint_aldea.py`, and `git diff --check`.
4. Confirm the new policy test executes with 16 assertions and all existing tests still pass.
5. If static checks fail, stop before Minecraft and report. If they pass and protected hashes match, execute **exactly one** A2 Run1 using the canonical command in the handoff, with a unique disposable world.
6. Harness must not modify production or tests. No retry, C4 standalone, Run2 or Full12K.
7. Preserve and hash all logs/worlds; report every first-expansion candidate and BREAK stand/LOS rejection by UUID.

## 8. D* Lite status

D* Lite remains **not implemented**. The current production global search is resumable A*. The architecture deliberately deferred D* Lite until the movement graph and action transitions are correct. The demonstrated failure was that the graph could not express a same-column vertical edge; D* Lite would still see no successor. Revisit incremental replanning only if the corrected graph's runtime telemetry demonstrates that repeated search recomputation is independently limiting progress.

A2 remains OPEN. C4 is not revalidated. Runtime acceptance is still UNKNOWN.
