# NEXT SESSION — A2 / C4 Stability

Code checkpoint SHA: `7692a2cc7cbb4420531a7ff1ac0dc379badd10bf`; subsequent handoff commits are documentation-only. Always fetch the live branch HEAD before synchronization.

## Latest checkpoint — 2026-11-16 Phase19 independent exclusion/backtrack fixtures

- Harness run `a2n-20261116-174648` on `8b389672`: `break-runtime` **PASS**. Both targets air, exact 40-tick retry, exact stand occupancy and both strategic missions resumed. This is fixture validation, not A2 siege acceptance.
- Current code adds independent modes `-Da2n.mode=break-edge-excluded` and `-Da2n.mode=nav-backtrack` in `A2NavHarness.java`. They must run in separate disposable worlds with trace enabled.
- Code review found a backtrack selector edge case: it selected the latest valid history cell even when within arrival tolerance, then aborted without trying older cells. ChatGPT added a distance-aware selector overload; executor now skips too-near cells using the existing 2.25 squared arrival tolerance. Added three unit assertions, including the 16-cell history bound.
- Added read-only runtime diagnostics for recovery history/backtrack state and current BREAK fingerprint exclusion.
- Fixture acceptance is strict: BREAK requires two unverified actions on the same edge 40 ticks apart, active unchanged fingerprint exclusion, a different BREAK edge selected, both blocks still solid and cursor not past BREAK. Backtrack requires two no-progress failures, exactly one selected historical cell, history <=16, exact physical return, <=1.5 blocks/tick, strategic mission preserved and resumed.
- Detailed report: `2026-11-16-PHASE19-BREAK-EXCLUSION-BACKTRACK-FIXTURES.md`.
- **PENDING HARNESS**: sync exact HEAD, run tests/lint/diff-check/diagnostics compile, then run both fixture modes independently. ChatGPT has not executed these validations.
- A2 remains OPEN; no full 12k before five consecutive strict 8/8 `DENTRO-PARED` runs. C4 save/reload remains OPEN.

## Latest checkpoint — 2026-11-16 Phase19 BREAK retry observation window

- Harness run `a2n-20261116-173828` on `08cf0e8c`: both targets air, retry exactly 40 ticks, Scenario A resumed. Scenario B remained at X=737 (target X=737) at the 20-tick deadline while state was TRAVERSING and movement commands targeted X=738/739. The original physical-crossing predicate had not yet passed.
- ChatGPT changed **only** `src/main/java/com/chipoodle/devilrpg/debug/A2NavHarness.java` in commit `8f2b9138937b23ff853ec839a0db9f7559d0673e`.
- Fixture now allows a bounded 100 ticks after retry destruction for the mob to physically cross the target column. The original predicate `objective().equals(goal) && blockPosition().getX() > target.getX()` is unchanged; no pass is granted for merely having an objective or movement command.
- Added `BREAK_FIXTURE_RETRY_PROGRESS` samples every 10 ticks with position, objective, state, route node and executor diagnostics. Deadline failure includes final retry diagnostics.
- No production source, A2/C4 original world, cooldown, reach, LOS, collision, planner budget or acceptance threshold changed.
- Detailed report: `2026-11-16-PHASE19-BREAK-RETRY-OBSERVATION-WINDOW.md`.
- **PENDING HARNESS**: run static checks and a fresh disposable `break-runtime` on the exact current branch HEAD. Require both mission-resumption flags and exact 40-tick retry. A2 remains OPEN; no 12k before five consecutive strict 8/8 runs.

## Latest checkpoint — 2026-11-16 Phase19 BREAK retry fixture timing

- Harness run `a2n-20261116-172549` proves the production stand nudge, exact stand occupancy, both target-air results and exactly 40-tick retry. Scenario A mission resumption also passes.
- The only failure was fixture timing: it evaluated retry mission progress in the same tick that the second actuator destroyed the block, before the retry mob received a subsequent entity-movement tick.
- ChatGPT changed **only** `src/main/java/com/chipoodle/devilrpg/debug/A2NavHarness.java` in commit `4c3f446aeaa893db7ff7bcfc1c5f690f94e37332`. It defers the retry mission-progress predicate until `now > breakRetrySecondTick` and allows up to 20 ticks after destruction for the same objective + physical-progress predicate. If still false at the deadline, the fixture reports FAIL; it does not silently pass.
- Production `NavExecutor` and its nudge/BREAK behavior are unchanged.
- Detailed handoff: `docs/autonomous-village/2026-11-16-PHASE19-BREAK-RETRY-FIXTURE-TIMING.md`.
- **PENDING HARNESS**: validate the exact current branch HEAD with Gradle tests, strict lint, diff-check, diagnostics compile, then rerun disposable `break-runtime`. Require both mission-resumption flags, exact 40-tick retry and both targets air. No A2/C4 world use.


## Latest checkpoint — 2026-11-16 Phase19 BREAK stand approach correction

- Harness run `a2n-20261116-163239` is a behavioral FAIL: both BREAK plans and LOS hits are valid, but `moveTo` returns a completed one-node path for the adjacent stand and the mobs do not move.
- ChatGPT published commit `11be611d90c4d65772813550c2a365a524ee5147` in `NavExecutor.ejecutarBreakDesdePosicionDeAtaque`: when vanilla navigation is done before exact stand occupancy, it uses the existing bounded `NavAdjacentApproachPolicy` + `MoveControl.setWantedPosition` fallback, guarded by Manhattan distance, AABB fit and support.
- Exact physical occupancy remains mandatory before the actuator. No reach, LOS, protection, collision, cooldown, planner/recovery budget or acceptance threshold changed.
- New trace event: `BREAK_STAND_NUDGE`; existing `BREAK_STAND_APPROACH` remains.
- Detailed handoff: `docs/autonomous-village/2026-11-16-PHASE19-BREAK-STAND-APPROACH-FIX.md`.
- **PENDING HARNESS**: ChatGPT has not run Gradle/lint/diagnostics compile or Minecraft runtime on this SHA. Harness must validate exact current branch HEAD, then rerun disposable `break-runtime` and capture physical positions, both target-air checks, 40-tick retry and mission resumption.


## Latest checkpoint — 2026-11-16 Phase19 BREAK LOS correction

- Previous Harness run `a2n-20261116-160714` was **INVALIDO**: static checks passed, but both planner routes were empty and no mob/actuator started. This is not a BREAK actuator FAIL.
- Source correction is published through `9200ac31a350d9013b2abb8f39ce2b421fc9605a`: BREAK LOS now clips toward the target collision-shape center instead of the old projected endpoint below a thin pane. Planning-time `BREAK_LOS_DIAG` and bounded `BREAK_STAND_CANDIDATE` events were added; fixture trace initialization now occurs before planning.
- Detailed handoff: `docs/autonomous-village/2026-11-16-PHASE19-BREAK-LOS-CORRECTION.md`.
- This correction is **NOT YET VALIDATED** by Harness. Tests, diagnostics compilation and runtime must be executed on the exact current branch HEAD after fetching.
- Harness must preserve local artifacts, fast-forward safely, run Gradle tests, strict lint, diff-check and diagnostics compile, then execute a new disposable `break-runtime` world with `-Ddevilrpg.a2n.traceNav=true`.
- Required evidence: `BREAK_STAND_CANDIDATE`, `BREAK_LOS_DIAG` (legacy and new ray endpoints plus actual clip hit), route summaries, `nav-trace.log`, server logs, `fin.txt`, `estado.txt`, exact SHA.
- Harness must not change production source. If the new ray still fails, return the exact rejection branch and clip hit to ChatGPT.
- A2 remains OPEN (last known 5/8 strict, 6/8 perimeter); no 12k until five consecutive 8/8 strict runs. C4 save/reload remains OPEN.


Read first, in order:

1. `docs/autonomous-village/CHATGPT-COLLABORATION-POLICY.md`
2. `docs/autonomous-village/NEXT-SESSION.md` (this file)
3. `docs/autonomous-village/CHATGPT-HARNESS-WORKFLOW.md` (permanent division of responsibilities and handoff protocol)
4. `docs/autonomous-village/CONTINUAR.md`
5. `docs/CONTINUAR.md`
6. `docs/PENDIENTE.md`
7. `docs/autonomous-village/2026-11-15-PHASE2-FIXES.md`
8. `docs/autonomous-village/2026-11-15-A2-IDX8-C4-FORENSICS.md`
9. `docs/autonomous-village/2026-11-15-PHASE5-A2-VARIABILITY.md`
10. `docs/autonomous-village/2026-11-15-PHASE6-IDX4-IDX8-FORENSICS.md`
11. `docs/autonomous-village/2026-11-15-PHASE7-A2-9000-OBSERVATION.md`
12. `docs/autonomous-village/2026-11-15-PHASE8-IDX8-DECISION-TRACE.md`
13. `docs/autonomous-village/2026-11-15-PHASE9-IDX8-MOVEMENT-OWNERSHIP.md`
14. `docs/autonomous-village/2026-11-15-NAVEXECUTOR-SINGLE-AUTHORITY-MIGRATION.md`
15. `docs/autonomous-village/2026-11-16-PHASE10-A2-MIGRATION-REGRESSION.md`
16. `docs/autonomous-village/2026-11-16-PHASE11-STRATEGIC-NAVIGATION-FORENSICS.md`
17. `docs/autonomous-village/2026-11-16-PHASE12-NAVEXECUTOR-REFUSAL-FORENSICS.md`
18. `docs/autonomous-village/2026-11-16-PHASE13-RECOVERY-ACTUATOR-FORENSICS.md`
19. `docs/autonomous-village/2026-11-16-PHASE14-MOVEMENT-C4-FORENSICS.md`
20. `docs/autonomous-village/2026-11-16-PHASE15-NAVIGATION-ROOT-CAUSE-FORENSICS.md`
21. `docs/autonomous-village/2026-11-16-PHASE16-PATHFINDER-MOVEMENT-FORENSICS.md`
22. `docs/autonomous-village/2026-11-16-PHASE17-ISOLATED-EVALUATOR-IDX7-FORENSICS.md`
23. `docs/autonomous-village/2026-11-16-PHASE18-BREAK-EXECUTION-CONTRACT-AUDIT.md`
24. `docs/autonomous-village/2026-11-16-BREAK-EXECUTABLE-CONTRACT.md`
25. `docs/autonomous-village/2026-11-16-PHASE19-BREAK-RECOVERY-IMPLEMENTATION.md`
26. `docs/autonomous-village/2026-11-16-PHASE19-BREAK-VERIFICATION.md`
27. `docs/autonomous-village/2026-11-16-PHASE19-HARNESS-VALIDATION.md`
28. `docs/autonomous-village/2026-11-16-PHASE19-BREAK-GRAPH-STATE-FIX.md`
29. `docs/autonomous-village/2026-11-16-PHASE19-BREAK-RUNTIME-INVALID.md`
30. `docs/autonomous-village/2026-11-16-PHASE19-BREAK-LOS-RUNTIME-FAIL.md`
31. `docs/autonomous-village/2026-11-16-PHASE19-BREAK-STAND-NUDGE-RUNTIME.md`
32. `docs/autonomous-village/2026-11-16-PHASE19-BREAK-RETRY-RUNTIME-FAIL.md`
33. `docs/autonomous-village/2026-11-16-PHASE19-BREAK-RETRY-PASS.md`
34. `docs/autonomous-village/2026-11-16-PHASE19-EXCLUSION-BACKTRACK-RUNTIME.md`
35. `docs/autonomous-village/2026-11-16-PHASE19-BACKTRACK-EXACT-ARRIVAL-FAIL.md`

## Permanent collaboration workflow

- Read `docs/autonomous-village/CHATGPT-HARNESS-WORKFLOW.md` and follow it in every session.
- ChatGPT owns production source implementation and publishes changes directly to GitHub on this branch.
- Harness owns local synchronization, preservation, builds/tests/lints, automated runtime measurement, logs and validation reports. Harness must not independently implement production source fixes.
- Harness returns evidence and reproducible failures; ChatGPT reviews and makes source corrections; Harness validates the exact new SHA.
- Neither agent may claim work performed by the other or mark a commit validated without evidence for that exact SHA.
- This is a permanent workflow rule, not a one-session instruction. It must survive new conversations and agent handoffs.

## Verified baseline

- Branch: `feature/autonomous-village`
- Code implementation awaiting validation: `6d5dc7b1d1f72a366d4f6b05626147b9e932481c` (BREAK graph-state, executor and deterministic fixture correction). Documentation commits may advance branch HEAD after this code checkpoint; always fetch and verify actual local/remote HEAD before acting.
- Latest technical report: `2026-11-16-PHASE19-BACKTRACK-EXACT-ARRIVAL-FAIL.md`. Exact-arrival guard works; adjacent physical backtrack step is still missing.
- Remote branch must be fetched and checked before any change.
- Working tree may contain local-only untracked artifacts.

## Completed investigations

- IDX8 elevated-BREAK support-floor selection: fixed by `NavBreakTargetPolicy` and regression test.
- C4 displaced workers beyond 72 blocks: fixed by `VillagerReturnPolicy`/`VillagerReturnGoal`.
- Existing diagnostic worlds remain authoritative; do not recreate.

## Current status

- `NavExecutor` is the single movement authority; legacy planner, `NavFeature` and
  `MovementOwnership` are deleted. Harness passed Gradle tests, strict lint and diff-check on the
  prior tree at `ecdfaff6`; those results do **not** validate the later code checkpoint `6d5dc7b1d1f72a366d4f6b05626147b9e932481c`.
  The new graph-state/route/executor changes and two new tests are **not yet compiled or executed by Harness**.
- Phase10 melee-only tactical ownership (`002550`): 6/8 perimeter, 5/8 strict; C4 12/12.
- Phase11 diagnostic `005739`: same 6/8 perimeter, 5/8 strict. Combat inactive for idx5/7/8.
- Phase12 diagnostic `022656`: 5/8 perimeter, 4/8 strict; C4 12/12.
- Phase13 diagnostic `024118`: 6/8 perimeter, 4/8 strict; C4 **FAIL** (`jobSite=[0,0,0]`, 12/12 resumed).
- Phase14 diagnostic `035911`: 6/8 perimeter, 4/8 strict; C4 **PASS** `[1,0,0]`.
- Phase15 diagnostic `092800`: 5/8 perimeter, 4/8 strict; C4 **PASS** `[2,0,0]`.
- Phase16 diagnostic `094243`: 5/8 perimeter, 3/8 strict; C4 **PASS** `[1,0,0]`.
- Phase17: `createPath` cached-return flaw confirmed; Phase16 retry not independent.
- Phase18 audit: BREAK route node was the solid block; no attack-position search.
- BREAK contract implementation: `Node.pos`=stand, `Node.actionPos`=solid target; planner validates
  adjacent stand AABB/support/LOS/reach/protection; executor navigates to stand, delegates action,
  requires verified air before advancing.
- Focused `a2n-20261116-101718`, merged `103331`, post-integration `110238`: 6/8 perimeter,
  5/8 strict, C4 PASS `[1,0,0]`/`[3,0,0]`.
- Phase19 verification: BREAK executor integration fixed to use `actionTarget()`. Follow-up review
  constrains planner-selected attack stands to one legal move from the predecessor and delegates
  reach/LOS/protection checks to `NavBreakActionPolicy.executable`.
- Harness validation at `d440b3e7` (implementation `6d5dc7b1`): Gradle tests, strict lint, diff-check and
  diagnostic compile PASS. New tests executed: graph-state 7, route-policy 11, attack-position 7.
- `break-runtime` `a2n-20261116-160714` was INVALIDO (empty plans); LOS correction `9200ac31` at
  `76d3466c` fixed planning: `BREAK_LOS_DIAG` accepts both panes with `hitBlock` equal to `actionPos`.
- `break-runtime` `a2n-20261116-174648` on HEAD `8b389672`: **COMPLETO**. Both panes air, retry
  calls=2, delta=40, success/retry missions resumed (retry at x=738, ticksAfterBreak=23).
- `break-edge-excluded` `a2n-20261116-180242`: **COMPLETO**. Two unverified actions, delta=40,
  one `BREAK_EDGE_EXCLUDED` (fingerprint, exclusions=1), alternative BREAK selected, both blocks
  solid, route index unchanged, strategic mission preserved, 0 unexpected calls.
- `nav-backtrack` `a2n-20261116-182234` on HEAD `e2fe0873`: **FAIL**. `BACKTRACK_PROXIMITY_SUPPRESSED`
  works at `distSqr=1.935`, but the mob stays at `938` with a completed 2-node WALK route to `937`;
  no physical step, no mission resume. Adjacent nudge is missing for `recoveryBacktrackObjective`.
- A2 focused remains **OPEN**; full 12k not run.
- Canonical focused gate: five consecutive **8/8 strict `DENTRO-PARED`** runs.

## Immediate work

1. ChatGPT: add the adjacent physical nudge/exact-step for `recoveryBacktrackObjective` when navigation
   is done and the mob is one cell away (`938 -> 937`). The suppression guard already prevents early
   clearing; do not change cooldown/budgets/acceptance.
2. Rerun `nav-backtrack` on the exact new SHA; require physicalReturn+missionResumed.
3. BREAK_EDGE_EXCLUDED is validated; keep its fixture unchanged.
4. A2 remains OPEN; do not run 12k until five consecutive 8/8 strict `DENTRO-PARED`. C4 save/reload OPEN.

## Worlds (preserve; do not overwrite)

- `C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-A2-IDX8-Diagnostico`
- `D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-A2-IDX8-Diagnostico`
- `C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-C4-WorkerResume-Diagnostico`
- `D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-C4-WorkerResume-Diagnostico`

Hashes and sizes are in `2026-11-15-PHASE2-FIXES.md`.

## Local-only artifacts

`.a2-backups/`, `src/foodTest/`, `tools/a2n/A2NavHarness.java.partial`, launcher helper scripts, `tools/a5-closure/`. Do not commit indiscriminately.


## 2026-11-16 — Phase19 backtrack exact-arrival correction (PENDING HARNESS)

- Harness evidence: `BREAK_EDGE_EXCLUDED` PASS; `NAV_BACKTRACK` FAIL because generic `ARRIVE_DISTANCE_SQR` cleared the one-shot tactical target at 938 before exact cell 937.
- ChatGPT correction source checkpoint: `889072d235a44d0efc2de7ad955333b39b1d8b03`. Added exact-cell gate for pending recovery backtrack in `NavExecutor`, pure policy and three regression assertions in `NavArrivalPolicyTest`, plus bounded suppression trace.
- No tests/compile/lint/runtime executed by ChatGPT. **PENDING HARNESS** on exact live HEAD.
- Run static validation and only disposable `nav-backtrack`; require physical return to exact historical cell, one selection, mission resumed, strategic mission preserved, bounded history, no teleport.
- `BREAK_EDGE_EXCLUDED` remains validated; A2 OPEN; no full 12k until five consecutive strict 8/8; C4 save/reload OPEN.
