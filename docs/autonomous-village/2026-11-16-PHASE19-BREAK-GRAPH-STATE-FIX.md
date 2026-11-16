# Phase19 — BREAK graph-state correction and deterministic runtime fixture

**Date:** 2026-11-16  
**Implementation HEAD:** `6d5dc7b1d1f72a366d4f6b05626147b9e932481c`  
**Branch:** `feature/autonomous-village`  
**Status:** IMPLEMENTED / NOT YET VALIDATED BY HARNESS

## 1. Why the previous runtime run had no BREAK events

Harness run `a2n-20261116-112413` passed the static checks on `ecdfaff6`, but emitted no BREAK or backtrack runtime events. Code review found several contract breaks that explain why policy tests could pass while the runtime action was unreachable or skipped.

### 1.1 Search state collapsed BREAK into WALK

The previous local/global search used the attack `standPos` as the successor state for a BREAK edge. The same stand cell was also reachable through an ordinary, cheaper WALK edge. Since A* keyed `bestG` by position, the WALK state dominated the BREAK edge even though it did not remove the solid target.

**Correction:** the graph successor for BREAK is now the post-action cell (the solid `actionPos` that becomes traversable after removal). The physical `standPos` travels separately as route metadata. `NavGraphSearch.Edge/Step` now preserve both positions. Local reconstruction uses a separate `standTo` map; global reconstruction uses `Step.standPos()`.

This keeps two non-equivalent states distinct:
- WALK to the attack stand, with the obstruction still present.
- BREAK the target from the stand, yielding the formerly solid target cell as the post-action graph state.

### 1.2 BREAK could be advanced by proximity before its actuator ran

`NavExecutor.nodoAlcanzado` used generic waypoint proximity for BREAK nodes. A mob within the arrival radius of the stand could cause the route index to advance before `ejecutarBreakDesdePosicionDeAtaque` was called.

**Correction:** BREAK nodes are never advanced by proximity. The executor must process the action node; if the target is already air, the BREAK handler detects that and resolves/replans.

### 1.3 Adjacent-cell proximity was mistaken for stand occupancy

The BREAK executor previously accepted squared distance `<= 1.0` as sufficient to act. A mob in the predecessor cell can be exactly one block from a stand and satisfy that threshold.

**Correction:** the actuator now requires `mob.blockPosition().equals(standPos)` before checking the real stand-to-target action preconditions and invoking the recovery action.

### 1.4 Global BREAK waypoint action was lost during local planning

A global BREAK node stores a traversable attack stand and a separate solid action target. The local planner could plan a WALK route to the stand and lose the action metadata.

**Correction:**
- `NavExecutor` retains `objetivoAccionLocal` separately from the local movement objective.
- The local objective validator checks the actual action target from the selected stand.
- `NavBreakRoutePolicy.attachFinalBreak` converts the final local stand waypoint into a BREAK action node while retaining the stand as the movement position and the target as `actionPos`.
- The route signature is checked after the action has been attached, so the executable action target participates in route identity.

### 1.5 Mission continuation after BREAK

A verified or excluded BREAK edge must not leave the executor planning indefinitely toward a temporary attack stand.

**Correction:** verified BREAK and excluded-edge replanning restore `objective` to `misionFinal`, clear the temporary local action metadata and invalidate global/local route caches. The strategic mission remains authoritative.

## 2. Code changes

- `NavGraphSearch.java`: separate post-break search state from attack stand in Edge/Step.
- `NavPlanner.java`: local and global graph states use the post-action target; route reconstruction restores the physical stand position.
- `NavExecutor.java`:
  - carries global BREAK action target into local execution,
  - attaches the final BREAK node to the local route,
  - does not advance BREAK by proximity,
  - requires exact physical occupancy of the stand,
  - resumes the strategic mission after verification/exclusion,
  - uses `actionTarget()` in the generic recovery path rather than accidentally treating the stand as the block to break,
  - carries post-break state correctly when scanning global route predecessors.
- `NavBreakRoutePolicy.java`: attaches a BREAK action to a route ending at a validated stand.
- `A2NavHarness.java`: adds `-Da2n.mode=break-runtime`, a deterministic disposable-world fixture with two real `AggressiveZombieEntity` instances and production `NavPlanner`/`NavExecutor`.
- `build.gradle`: registers two new policy tests.

## 3. New automated tests (authored, NOT YET RUN)

- `NavBreakGraphStateTest` — 7 assertions. Models a cheaper WALK path to the same stand that is a dead end, while a BREAK edge to the distinct post-break target state reaches the goal. Verifies the BREAK transition, action target and stand are all preserved.
- `NavBreakRoutePolicyTest` — 11 assertions. Verifies that the final stand waypoint becomes a BREAK node, the action target stays separate, route prefix/destination are preserved, and invalid inputs (including a route whose destination differs from the stand) are rejected.

These tests are registered in `policyTest`. The graph-state test also rejects a malformed BREAK edge that lacks a stand position. ChatGPT has not run Gradle, lint or runtime; no PASS is claimed for this implementation SHA.

## 4. Deterministic runtime fixture

Run mode: `break-runtime` in `A2NavHarness`. Build diagnostics with `-Pa2nDiagnostics=true`; launch with `-Da2n.mode=break-runtime -Ddevilrpg.a2n.traceNav=true` and a unique `a2n.run` in a **new disposable world**.

The fixture:
1. Builds two separate sealed, supported, low-clearance corridors. Bedrock walls prevent bypass mining; a glass pane is the only breakable obstruction. The ceiling at feetY+2 rules out STEP_UP/JUMP_UP while preserving the mob's 1.95-block clearance after the pane is removed.
2. Spawns two real `AggressiveZombieEntity` instances.
3. Calls the production `NavPlanner.plan` for each and refuses to start runtime acceptance unless each route contains a BREAK node targeting its expected pane.
4. Runs the first mob with the production recovery actuator.
5. Runs the second mob with a controlled test actuator: first call returns false without changing the world; the second call destroys the exact action target. Production navigation, stand validation, 40-tick retry scheduling, world verification and mission resumption remain active.
6. Persists route/position/state samples and terminates with `COMPLETO` only if both targets become air, the retry is exactly 40 ticks after the first attempt, and both mobs resume/make progress toward their original strategic goals. Timeout is `BLOCKED`; failed preconditions are `INVALIDO`.

Expected trace evidence:
- `BREAK_STAND_APPROACH`
- `BREAK_ACTUATOR_RETRY_WAIT` for the retry mob
- `BREAK_EXECUTED_FROM_STAND` for both mobs
- `BREAK_FIXTURE_RETRY_FIRST`, `BREAK_FIXTURE_RETRY_SECOND delta=40`
- `BREAK_FIXTURE_MISSION_RESUMED` for both mobs

The fixture calls `traceInit()` before starting the mobs; `-Ddevilrpg.a2n.traceNav=true` installs the production `NavTrace` sink and persists actuator events in `nav-trace.log`. The unrelated `n4.trace` property is not used.

The fixture does **not yet** exercise second-action failure/edge exclusion or `NAV_BACKTRACK`. Those remain separate runtime tasks and must not be marked PASS from this fixture.

## 5. Harness validation instructions

On the local repository:

1. Fetch the current `origin/feature/autonomous-village` HEAD and preserve all local-only artifacts. No reset/clean/stash/force-push.
2. Confirm exact HEAD `6d5dc7b1d1f72a366d4f6b05626147b9e932481c`.
3. Run:
   ```powershell
   .\gradlew.bat test
   python tools/lint_aldea.py --strict
   git diff --check
   ```
4. Build the diagnostic variant using the existing A2 diagnostics build path (`-Pa2nDiagnostics=true`).
5. Run the `break-runtime` fixture in a new disposable world, with trace enabled and a unique run ID. Do not use or modify the original A2/C4 diagnostic worlds.
6. Preserve complete logs and `build/a2n/<run>/fin.txt` / `estado.txt`. Report the exact tested SHA and all event timestamps.
7. If planner preconditions fail, classify as INVALIDO and return the plan summary/route plus world geometry. Do not silently weaken the fixture or production policies.
8. After this fixture, add separate deterministic runtime coverage for failed-edge exclusion and two no-progress failures/backtrack. Do not claim these from the success/retry fixture.

## 6. Project acceptance status (unchanged)

- A2 latest focused siege evidence remains `a2n-20261116-112413`: 5/8 strict, 6/8 perimeter. Gate OPEN.
- Canonical A2 gate remains five consecutive 8/8 strict `DENTRO-PARED` runs.
- Do not run the 12k campaign before that gate.
- C4 focused run in `a2n-20261116-112413` was PASS `[1,0,0]`, 12/12 resumed. Save/reload remains OPEN.
- Original A2/C4 world hashes remain the previously documented values; this implementation has not touched worlds.
- Static/runtime validation of this new implementation is pending Harness.

## 7. Next owner/action

**Harness:** fetch and validate `6d5dc7b1d1f72a366d4f6b05626147b9e932481c`, then execute the deterministic `break-runtime` fixture.

**ChatGPT:** review the resulting report and logs; correct source directly on GitHub if Harness demonstrates a defect. Harness must not independently change production source.
