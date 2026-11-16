# NEXT SESSION — A2 / C4 Stability

Read first, in order:

1. `docs/autonomous-village/CHATGPT-COLLABORATION-POLICY.md`
2. `docs/autonomous-village/NEXT-SESSION.md` (this file)
3. `docs/autonomous-village/CONTINUAR.md`
4. `docs/CONTINUAR.md`
5. `docs/PENDIENTE.md`
6. `docs/autonomous-village/2026-11-15-PHASE2-FIXES.md`
7. `docs/autonomous-village/2026-11-15-A2-IDX8-C4-FORENSICS.md`
8. `docs/autonomous-village/2026-11-15-PHASE5-A2-VARIABILITY.md`
9. `docs/autonomous-village/2026-11-15-PHASE6-IDX4-IDX8-FORENSICS.md`
10. `docs/autonomous-village/2026-11-15-PHASE7-A2-9000-OBSERVATION.md`
11. `docs/autonomous-village/2026-11-15-PHASE8-IDX8-DECISION-TRACE.md`
12. `docs/autonomous-village/2026-11-15-PHASE9-IDX8-MOVEMENT-OWNERSHIP.md`
13. `docs/autonomous-village/2026-11-15-NAVEXECUTOR-SINGLE-AUTHORITY-MIGRATION.md`
14. `docs/autonomous-village/2026-11-16-PHASE10-A2-MIGRATION-REGRESSION.md`
15. `docs/autonomous-village/2026-11-16-PHASE11-STRATEGIC-NAVIGATION-FORENSICS.md`
16. `docs/autonomous-village/2026-11-16-PHASE12-NAVEXECUTOR-REFUSAL-FORENSICS.md`
17. `docs/autonomous-village/2026-11-16-PHASE13-RECOVERY-ACTUATOR-FORENSICS.md`
18. `docs/autonomous-village/2026-11-16-PHASE14-MOVEMENT-C4-FORENSICS.md`
19. `docs/autonomous-village/2026-11-16-PHASE15-NAVIGATION-ROOT-CAUSE-FORENSICS.md`
20. `docs/autonomous-village/2026-11-16-PHASE16-PATHFINDER-MOVEMENT-FORENSICS.md`
21. `docs/autonomous-village/2026-11-16-PHASE17-ISOLATED-EVALUATOR-IDX7-FORENSICS.md`
22. `docs/autonomous-village/2026-11-16-PHASE18-BREAK-EXECUTION-CONTRACT-AUDIT.md`
23. `docs/autonomous-village/2026-11-16-BREAK-EXECUTABLE-CONTRACT.md`
24. `docs/autonomous-village/2026-11-16-PHASE19-BREAK-RECOVERY-IMPLEMENTATION.md`

## Verified baseline

- Branch: `feature/autonomous-village`
- Latest substantive report: `2026-11-16-PHASE19-BREAK-RECOVERY-IMPLEMENTATION.md`. Always verify `git rev-parse HEAD` and `origin/feature/autonomous-village`.
- Remote branch must be fetched and checked before any change.
- Working tree may contain local-only untracked artifacts.

## Completed investigations

- IDX8 elevated-BREAK support-floor selection: fixed by `NavBreakTargetPolicy` and regression test.
- C4 displaced workers beyond 72 blocks: fixed by `VillagerReturnPolicy`/`VillagerReturnGoal`.
- Existing diagnostic worlds remain authoritative; do not recreate.

## Current status

- `NavExecutor` is the single movement authority; legacy planner, `NavFeature` and
  `MovementOwnership` are deleted. Baseline through Phase18 passed its recorded tests; Phase19
  gameplay changes and newly added policy tests have **not yet been compiled or executed by Harness**.
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
- Focused `a2n-20261116-101718` and merged `a2n-20261116-103331`: 6/8 perimeter, 5/8 strict,
  C4 PASS `[1,0,0]`; no route BREAK event captured, so runtime BREAK proof is still pending.
- A2 focused remains **OPEN**; full 12k not run.
- Canonical focused gate: five consecutive **8/8 strict `DENTRO-PARED`** runs.

## Immediate work

1. Validate the published BREAK execution and bounded recovery implementation in Phase19, including the
   executable stand-position contract from `2026-11-16-BREAK-EXECUTABLE-CONTRACT.md`.
2. Harness must fetch the latest `feature/autonomous-village` commit before touching local files; inspect
   local status and preserve every uncommitted/untracked artifact. Do not reset, clean, stash or overwrite.
3. Run Gradle tests, strict aldea lint and diff-check. Fix compile/test regressions without changing action
   range, existing budgets, collision/protection rules or acceptance.
4. Runtime: focused BREAK from multiple approach sides, failed-edge exclusion and terrain-fingerprint
   invalidation; then bounded backtrack after two no-progress route failures. Verify strategic mission resumes.
5. Run focused A2 and C4 12/12 only after targeted checks. Do not run the 12k campaign until the canonical
   five consecutive 8/8 strict `DENTRO-PARED` gate is satisfied.
6. IDX7 remains BLOCKED (not reproduced); IDX5 boundary UNKNOWN; C4 contract unchanged.

## Worlds (preserve; do not overwrite)

- `C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-A2-IDX8-Diagnostico`
- `D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-A2-IDX8-Diagnostico`
- `C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-C4-WorkerResume-Diagnostico`
- `D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-C4-WorkerResume-Diagnostico`

Hashes and sizes are in `2026-11-15-PHASE2-FIXES.md`.

## Local-only artifacts

`.a2-backups/`, `src/foodTest/`, `tools/a2n/A2NavHarness.java.partial`, launcher helper scripts, `tools/a5-closure/`. Do not commit indiscriminately.
