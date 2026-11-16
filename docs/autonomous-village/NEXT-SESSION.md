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
25. `docs/autonomous-village/2026-11-16-PHASE19-BREAK-VERIFICATION.md`
26. `docs/autonomous-village/2026-11-16-PHASE19-HARNESS-VALIDATION.md`

## Verified baseline

- Branch: `feature/autonomous-village`
- Current published HEAD: `f43eb11eab618ed1e19f8b795c1663bc0c619331` (follow-up BREAK attack-stand edge fix).
- Latest substantive report: `2026-11-16-PHASE19-HARNESS-VALIDATION.md`. Always verify `git rev-parse HEAD` and `origin/feature/autonomous-village`.
- Remote branch must be fetched and checked before any change.
- Working tree may contain local-only untracked artifacts.

## Completed investigations

- IDX8 elevated-BREAK support-floor selection: fixed by `NavBreakTargetPolicy` and regression test.
- C4 displaced workers beyond 72 blocks: fixed by `VillagerReturnPolicy`/`VillagerReturnGoal`.
- Existing diagnostic worlds remain authoritative; do not recreate.

## Current status

- `NavExecutor` is the single movement authority; legacy planner, `NavFeature` and
  `MovementOwnership` are deleted. Harness reported Gradle tests, strict lint and diff-check PASS
  for the Phase19 baseline at `14df0115`. Follow-up attack-stand edge correction at `f43eb11e`
  has **not yet been compiled or executed by Harness**.
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
- Harness validation at `ecdfaff6`: Gradle tests, strict lint and diff-check PASS. All five BREAK/recovery
  policy tests executed (5/7/8/6/7 assertions).
- Focused `a2n-20261116-112413`: 6/8 perimeter, 5/8 strict, C4 PASS `[1,0,0]`; **no BREAK/backtrack
  runtime events**, so direct execution, retry, alternatives and backtrack remain unproven.
- A2 focused remains **OPEN**; full 12k not run.
- Canonical focused gate: five consecutive **8/8 strict `DENTRO-PARED`** runs.

## Immediate work

1. Build a deterministic runtime fixture that forces a route BREAK edge (valid stand) and records
   `BREAK_STAND_APPROACH` → target air → route advance.
2. Force one actuator false return to observe the 40-tick retry, then two no-progress failures to
   observe `NAV_BACKTRACK` and strategic mission resumption.
3. Add the real/mock `CollisionGetter` + `Mob` planner integration fixture; do not substitute
   policy-only tests.
4. Run focused A2/C4 only after targeted BREAK checks. Do not run 12k until five consecutive 8/8 strict.
5. IDX7 remains BLOCKED; IDX5 boundary UNKNOWN; C4 save/reload OPEN.
   consecutive 8/8 strict `DENTRO-PARED` runs are achieved.
6. IDX7 remains BLOCKED (not reproduced); IDX5 boundary UNKNOWN; C4 contract unchanged.

## Worlds (preserve; do not overwrite)

- `C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-A2-IDX8-Diagnostico`
- `D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-A2-IDX8-Diagnostico`
- `C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-C4-WorkerResume-Diagnostico`
- `D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-C4-WorkerResume-Diagnostico`

Hashes and sizes are in `2026-11-15-PHASE2-FIXES.md`.

## Local-only artifacts

`.a2-backups/`, `src/foodTest/`, `tools/a2n/A2NavHarness.java.partial`, launcher helper scripts, `tools/a5-closure/`. Do not commit indiscriminately.
