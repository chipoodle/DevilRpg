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

## Verified baseline

- Branch: `feature/autonomous-village`
- Latest substantive report: `2026-11-16-PHASE16-PATHFINDER-MOVEMENT-FORENSICS.md`. Always verify `git rev-parse HEAD` and `origin/feature/autonomous-village`.
- Remote branch must be fetched and checked before any change.
- Working tree may contain local-only untracked artifacts.

## Completed investigations

- IDX8 elevated-BREAK support-floor selection: fixed by `NavBreakTargetPolicy` and regression test.
- C4 displaced workers beyond 72 blocks: fixed by `VillagerReturnPolicy`/`VillagerReturnGoal`.
- Existing diagnostic worlds remain authoritative; do not recreate.

## Current status

- `NavExecutor` is the single movement authority; legacy planner, `NavFeature` and
  `MovementOwnership` are deleted. All tests pass.
- Phase10 melee-only tactical ownership (`002550`): 6/8 perimeter, 5/8 strict; C4 12/12.
- Phase11 diagnostic `005739`: same 6/8 perimeter, 5/8 strict. Combat inactive for idx5/7/8.
- Phase12 diagnostic `022656`: 5/8 perimeter, 4/8 strict; C4 12/12.
- Phase13 diagnostic `024118`: 6/8 perimeter, 4/8 strict; C4 **FAIL** (`jobSite=[0,0,0]`, 12/12 resumed).
- Phase14 diagnostic `035911`: 6/8 perimeter, 4/8 strict; C4 **PASS** `[1,0,0]`.
- Phase15 diagnostic `092800`: 5/8 perimeter, 4/8 strict; C4 **PASS** `[2,0,0]`.
- Phase16 diagnostic `094243`: 5/8 perimeter, 3/8 strict; C4 **PASS** `[1,0,0]`.
  - IDX8 transaction: requested `746,65,538` from start `747,63,538`; pathfinder returned one node at
    `747,63,538:WALKABLE` with `canReach=false` — no target node.
  - IDX7 reachable-path state did not reproduce; ended FAILED with a one-node non-reaching path.
- A2 focused remains **OPEN**; full 12k not run.
- Canonical focused gate: five consecutive **8/8 strict `DENTRO-PARED`** runs.

## Immediate work

1. Decompose IDX8 target-node rejection: block state, support, walkability and evaluator result at
   `746,65,538` / `745,66,538` with a read-only query.
2. Reproduce the specific IDX7 reachable-path state; otherwise preserve BLOCKED.
3. C4 contract decision (zero Brain JOB_SITE vs custom workstation) still requires separate authorization.
4. IDX5 6164 boundary remains UNKNOWN.

## Worlds (preserve; do not overwrite)

- `C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-A2-IDX8-Diagnostico`
- `D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-A2-IDX8-Diagnostico`
- `C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-C4-WorkerResume-Diagnostico`
- `D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-C4-WorkerResume-Diagnostico`

Hashes and sizes are in `2026-11-15-PHASE2-FIXES.md`.

## Local-only artifacts

`.a2-backups/`, `src/foodTest/`, `tools/a2n/A2NavHarness.java.partial`, launcher helper scripts, `tools/a5-closure/`. Do not commit indiscriminately.
