# A2 Phase55 — incremental global A* implementation

**Status: IMPLEMENTED — PENDING HARNESS STATIC VALIDATION. No runtime authorized or executed.**

## Baseline and ownership

- Branch: `feature/autonomous-village`
- Baseline documentation HEAD: `924c62088e6efce9c8e1fcb39fdb76969f238cc8`
- Production baseline: `656ce7d0f6182fd15e0b1bbb0bd6684c180bd0e6`
- **Implementation SHA:** `9966f807b2fbb13552545cdd181f2f034c7ecbaa`
- Phase54B-R1 runtime: `a2n-20261119-011138`, BLOCKED by 60.04 s global-planner watchdog after 8/8 spawn.
- This implementation changes production navigation only; Harness must validate the published SHA. ChatGPT did not run Gradle, lint, diagnostics compilation, or Minecraft runtime.

## Root cause addressed

The synchronous strategic global search expanded up to the existing hard total expansion budget inside one server tick. Every expanded state validates its bounded neighborhood through `NavPlanner.validarArista` and `NavObstruction`; collision enumeration in `BlockCollisions.computeNext` made the first A* burst exceed the 60 s watchdog.

## Implementation

### 1. Resumable A* core — `NavGraphSearch.Session`

- Added `begin(...)` and `Session.advance(expansionSlice)`.
- OPEN priority queue, best-G map, CLOSED set, predecessor/transition/action/stand/feet maps, best partial front and total expansion count persist across slices.
- Each slice expands at most its requested number of nodes and pops at most twice that number from OPEN, including stale entries. The production slice is 4 expansions / at most 8 OPEN pops per executor per tick. This prevents stale queue entries from bypassing the tick bound.
- The total `maxExpansions` ceiling remains hard and unchanged.
- A partial session cannot expose a route: `result()` throws until completion.
- The existing synchronous `search(...)` API remains as a compatibility wrapper and uses one full-budget slice.

### 2. Global planner session — `NavPlanner`

- Added executor-owned `GlobalSearchHandle`; no static entity/session registry.
- Added `planGlobalIncremental(...)`. The graph adapter and all existing edge validation rules are created once and retained with the A* frontier.
- Strategic global planning advances at **4 expansions per server tick**. The existing total budget (default 8,000; existing property clamp 8,000–32,000) is not increased.
- A changed level, origin, mission, search mode, radius, vertical range or total budget invalidates the retained search and starts a fresh one.
- Synchronous `planGlobal` and exact-goal `planExact` retain their prior synchronous contracts, including repeated bounded queue-pop slices when stale OPEN entries are present.

### 3. Executor integration — `NavExecutor`

- Strategic route requests use the executor-owned handle.
- A pending search returns no route/waypoint. The executor remains PLANNING and retries next tick.
- Pending work does not consume local stair attempts/failure counters, route rejection counts, or emit a movement command from a partial route.
- The pending result is never mistaken for a completed cache hit; each subsequent tick advances the same search.
- Expansion diagnostics count only the delta per slice, not the cumulative count repeatedly.
- Pending trace output is throttled to once per 20 ticks per executor; completed global results retain their existing summary log.
- The local planner, exact DROP-recovery search, collision predicates, protection rules, movement costs, heuristic, radius, vertical range, and global total expansion budget are unchanged.

### 4. Policy test

Extended the already-registered `NavPlannerStateTest` with checks for:
- no partial result exposure;
- per-slice expansion ceiling;
- multi-slice completion;
- route and predecessor reconstruction equivalence with synchronous A*;
- preservation of the hard total expansion budget.

These assertions are authored but **not yet executed**.

## Files changed

- `src/main/java/com/chipoodle/devilrpg/entity/nav/NavGraphSearch.java`
- `src/main/java/com/chipoodle/devilrpg/entity/nav/NavPlanner.java`
- `src/main/java/com/chipoodle/devilrpg/entity/nav/NavExecutor.java`
- `src/test/java/com/chipoodle/devilrpg/entity/nav/NavPlannerStateTest.java`

No fixture, Harness, world, A2/C4 hash, wall, support, collision, protection, landing, or acceptance criterion was changed.

## Required Harness validation

1. Sync and preserve local-only changes/worlds using the permanent workflow. Verify exact production SHA and inspect the diff.
2. Run diagnostics compile, `gradlew test` (must execute `NAV_PLANNER_STATE_TEST`), strict lint, and `git diff --check`.
3. Report the exact assertion count and all command outputs.
4. Do not run Minecraft/A2/C4 runtime yet. This implementation is pending static validation and review.
5. After static PASS, return the report to ChatGPT. A separate runtime authorization is required before any A2 Run1.

## Runtime evidence required after later authorization

- Per-entity `NAV_GLOBAL_SEARCH_PENDING` sequence with tick, cumulative expansions, fixed slice and frontier size.
- Confirm no tick exceeds 16 expansions (and no more than 32 OPEN pops) for each executor search.
- Confirm the same search progresses across ticks rather than restarting; expansion totals are monotonic and counted once.
- Confirm no `MOVE_COMMAND` or local waypoint is emitted while the search is pending.
- On completion, compare route summary and route execution guards; ensure stale/changed terrain is rejected by the existing local physical validation.
- Record total ticks to completion, total expansions, edge validations, and watchdog/tick duration.
- Verify IDX5 cadence preservation and IDX3 stair diagnostics only after the global planner has completed; no claims from an aborted run.

## Open risks / acceptance

- Sixteen expansions is a conservative initial per-tick slice, not a proven performance optimum. Harness must measure it; do not raise it without evidence.
- The world can change between slices. The final global route is still only a strategic waypoint source; the existing local planner and physical movement guards remain responsible for validating each executable local segment. Harness must report any stale-route rejection or repeated invalidation.
- Incremental execution can increase wall-clock planning latency. This is intentional to keep each server tick bounded; report completion latency rather than relaxing the per-tick ceiling.
- A2 remains OPEN. Phase54B fixture remains validated and must not be changed.
- No Run1 retry, Run2, standalone C4, or Full12K is authorized by this implementation approval.
