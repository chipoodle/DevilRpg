# NEXT SESSION — A2 / C4 Stability

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
- Latest technical report: `2026-11-16-PHASE19-BREAK-LOS-RUNTIME-FAIL.md`. LOS acceptance is proven; stand approach is the current runtime blocker.
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
- Latest `break-runtime` `a2n-20261116-163239` is **FAIL/blocked by stand approach**: plans contain
  BREAK, but both mobs remain at `696/736,184,568` while `moveTo(stand=696/736,184,567)` returns a
  one-node path marked done; `retryCalls=0`, no target air, no mission resumption.
- A2 focused remains **OPEN**; full 12k not run.
- Canonical focused gate: five consecutive **8/8 strict `DENTRO-PARED`** runs.

## Immediate work

1. ChatGPT: inspect why `NavExecutor.ejecutarBreakDesdePosicionDeAtaque` cannot move the mob from
   `696/736,184,568` to its adjacent stand `696/736,184,567`. `moveTo` returns a one-node path marked
   done (`pathNodes=1`, `pathIndex=1`) with no displacement; exact stand occupancy never occurs.
2. Correct the stand-approach/coordinate issue without changing reach, cooldown, budgets or acceptance,
   publish a new SHA, and return it to Harness.
3. Harness: rerun `break-runtime` on the exact new SHA; then validate Scenario A/B and 40-tick retry.
4. After a successful BREAK runtime, add deterministic coverage for failed-edge exclusion and
   two no-progress failures/`NAV_BACKTRACK`.
5. A2 remains OPEN; do not run 12k until five consecutive 8/8 strict `DENTRO-PARED`. C4 save/reload OPEN.

## Worlds (preserve; do not overwrite)

- `C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-A2-IDX8-Diagnostico`
- `D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-A2-IDX8-Diagnostico`
- `C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-C4-WorkerResume-Diagnostico`
- `D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-C4-WorkerResume-Diagnostico`

Hashes and sizes are in `2026-11-15-PHASE2-FIXES.md`.

## Local-only artifacts

`.a2-backups/`, `src/foodTest/`, `tools/a2n/A2NavHarness.java.partial`, launcher helper scripts, `tools/a5-closure/`. Do not commit indiscriminately.
