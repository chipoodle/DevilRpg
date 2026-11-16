# Phase19 — Independent BREAK_EDGE_EXCLUDED and NAV_BACKTRACK fixtures

Date: 2026-11-16

Current branch HEAD for Harness synchronization: `7692a2cc7cbb4420531a7ff1ac0dc379badd10bf` (before this documentation-only handoff update).

## Baseline accepted by Harness

Harness run `a2n-20261116-174648`, tested on `8b3896725d896e5a136813ae4a4bac6275fd4ed4`, returned **PASS (fixture COMPLETO)**:
- both BREAK targets became air;
- both mobs physically reached validated stands;
- retry actuator called exactly twice, exactly 40 ticks apart;
- both strategic missions resumed, including retry mob crossing into X=738;
- static Gradle tests, strict lint, diff-check and diagnostics compile passed.

This closes only the `break-runtime` fixture. A2 siege remains OPEN.

## Audit finding and bounded correction

Code review found that `NavRecoveryController.chooseBacktrack` returned the newest valid historical cell even when it was within the executor's arrival tolerance. `NavExecutor.intentarBacktrackTrasFallo` then rejected that candidate and aborted without considering older history cells. With ordinary one-block-at-a-time movement history, this could prevent a useful backtrack.

Correction:
- Added `chooseBacktrack(current, minimumDistanceSqr, predicate)`; it skips too-near history cells and continues searching.
- Existing two-argument API delegates to the new overload with zero minimum distance, preserving existing callers.
- Executor now passes its existing `ARRIVE_DISTANCE_SQR` (2.25); no distance threshold was changed.
- Added three unit assertions: skip adjacent history, continue to a farther valid historical cell, and enforce the 16-cell history bound. The recovery controller also exposes read-only state diagnostics.
- Added read-only recovery diagnostics and BREAK fingerprint-exclusion diagnostics for the runtime harness.

Files:
- `NavRecoveryController.java`
- `NavExecutor.java`
- `NavRecoveryControllerTest.java`
- `A2NavHarness.java`

No movement budget, history limit, cooldown, reach, collision, LOS or gameplay acceptance threshold changed.

## Fixture 1 — BREAK_EDGE_EXCLUDED

Run independently with:
`-Da2n.mode=break-edge-excluded -Ddevilrpg.a2n.traceNav=true`

Disposable world only. A sealed five-wide wall contains three breakable glass targets, giving the planner alternative BREAK edges while preventing a walk-around route.

Acceptance requires:
1. Initial production plan contains a BREAK edge.
2. First unverified action is recorded with the exact physical stand and action target.
3. Second action is on the same stand->target pair exactly 40 ticks later.
4. Both actions remain unverified; the original target block stays solid.
5. Production emits `BREAK_EDGE_EXCLUDED` with the edge fingerprint.
6. The current fingerprint remains excluded (`isBreakEdgeExcluded=true`) and the executor reports a nonzero exclusion count.
7. Production planner selects a different BREAK stand->target pair.
8. Both rejected and alternative blocks remain solid; route cursor has not advanced past the alternative BREAK node.
9. Strategic mission remains the original goal; no unexpected actuator calls.

A timeout or a route reusing the rejected pair is FAIL/BLOCKED, never PASS.

## Fixture 2 — NAV_BACKTRACK

Run independently with:
`-Da2n.mode=nav-backtrack -Ddevilrpg.a2n.traceNav=true`

Disposable sealed corridor. The mob starts with a reachable strategic objective. After it physically advances to the corridor threshold, the fixture closes the corridor with bedrock, forcing real no-progress route failures. The fixture itself never teleports or relocates the mob.

Acceptance requires:
1. Two consecutive no-progress failures before selection.
2. Exactly one `NAV_BACKTRACK_SELECTED` event.
3. Selected target is a previously physically occupied, valid feet cell, distinct from current position and farther than arrival tolerance.
4. Recovery history remains at most 16 cells.
5. Tactical backtrack preserves the original strategic mission.
6. Mob physically returns to the selected historical cell (exact block occupancy); no teleport. Per-tick displacement must remain <=1.5 blocks.
7. After reaching the tactical cell, executor restores the original strategic mission objective.
8. Recovery diagnostics identify one backtrack and the preserved strategic mission.

If no valid prior cell is selected, the mob does not physically return, the mission is lost, a second backtrack is selected, or displacement exceeds the bound, report FAIL with samples and executor diagnostics.

## Static validation required

Harness must sync safely to the exact current branch HEAD, preserve local artifacts, and run:

```powershell
.\\gradlew.bat test
python tools/lint_aldea.py --strict
git diff --check
.\\gradlew.bat compileJava -Pa2nDiagnostics=true
```

Then execute each runtime mode in its own new disposable world and unique run ID. Save `srv.log`, `nav-trace.log`, `fin.txt`, `estado.txt`, static logs and tested SHA. Validate the exact SHA, not merely a descendant.

## Status and constraints

**PENDING HARNESS.** ChatGPT has published fixtures and the bounded recovery-selection correction but has not run Gradle, lint, diagnostics compilation or Minecraft runtime.

- `break-runtime`: PASS on the prior accepted SHA.
- `BREAK_EDGE_EXCLUDED`: pending runtime.
- `NAV_BACKTRACK`: pending runtime.
- A2 siege: OPEN; latest known evidence remains 5/8 strict and 6/8 perimeter.
- Do not run full 12k until five consecutive strict 8/8 `DENTRO-PARED` runs.
- C4 save/reload remains OPEN; latest known focused C4 result remains PASS `[1,0,0]`.
- Original A2/C4 worlds must remain untouched.
- Harness validates and documents; ChatGPT implements source changes.
