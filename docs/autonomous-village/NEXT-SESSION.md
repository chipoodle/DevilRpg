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

## Verified baseline

- Branch: `feature/autonomous-village`
- Latest substantive report: `2026-11-16-PHASE10-A2-MIGRATION-REGRESSION.md`. Always verify `git rev-parse HEAD` and `origin/feature/autonomous-village`.
- Remote branch must be fetched and checked before any change.
- Working tree may contain local-only untracked artifacts.

## Completed investigations

- IDX8 elevated-BREAK support-floor selection: fixed by `NavBreakTargetPolicy` and regression test.
- C4 displaced workers beyond 72 blocks: fixed by `VillagerReturnPolicy`/`VillagerReturnGoal`.
- Existing diagnostic worlds remain authoritative; do not recreate.

## Current status

- `NavExecutor` is the single movement authority; legacy planner, `NavFeature` and
  `MovementOwnership` are deleted. All tests pass.
- Migration runs `234037`, `235237`, `000755`: 5/8 perimeter, 3–4/8 strict.
- Phase10 melee-only tactical ownership (`002550`): 6/8 perimeter, 5/8 strict; C4 12/12.
- idx7 (`708,58,468`) and idx8 (`746,64,538`) remain outside; idx5 perimeter-only.
- A2 focused remains **OPEN**, no full 12k. C4 historical 5/5 plus 12/12 in migration runs.
- No legacy planner/fallback; NavExecutor remains sole authority.

## Immediate work

1. Instrument idx7/idx8 strategic objective/route/action and position delta after the melee-only tactical
   change; isolate why they do not approach the wall.
2. Do not restore the legacy planner or weaken collision/budgets/acceptance.
3. C4 save/reload remains OPEN.
4. Only after focused strict entry reaches the documented gate should the full 12,000-tick campaign run.

## Worlds (preserve; do not overwrite)

- `C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-A2-IDX8-Diagnostico`
- `D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-A2-IDX8-Diagnostico`
- `C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-C4-WorkerResume-Diagnostico`
- `D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-C4-WorkerResume-Diagnostico`

Hashes and sizes are in `2026-11-15-PHASE2-FIXES.md`.

## Local-only artifacts

`.a2-backups/`, `src/foodTest/`, `tools/a2n/A2NavHarness.java.partial`, launcher helper scripts, `tools/a5-closure/`. Do not commit indiscriminately.
