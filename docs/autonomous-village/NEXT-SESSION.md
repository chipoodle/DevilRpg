# NEXT SESSION — A2 / C4 Stability

Read first, in order:

1. `docs/autonomous-village/CHATGPT-COLLABORATION-POLICY.md`
2. `docs/autonomous-village/NEXT-SESSION.md` (this file)
3. `docs/autonomous-village/CONTINUAR.md`
4. `docs/CONTINUAR.md`
5. `docs/PENDIENTE.md`
6. `docs/autonomous-village/2026-11-15-PHASE2-FIXES.md`
7. `docs/autonomous-village/2026-11-15-A2-IDX8-C4-FORENSICS.md`

## Verified baseline

- Branch: `feature/autonomous-village`
- Phase-3 substantive commit: `46aee02b4c35d1f775b3e586feefddd70ce25dbc` (collaboration policy + stability report). The final synchronization checkpoint is the commit containing this file; always verify `git rev-parse HEAD` and `origin/feature/autonomous-village`.
- Remote branch must be fetched and checked before any change.
- Working tree may contain local-only untracked artifacts; read the final report of the latest session.

## Completed investigations

- IDX8 elevated-BREAK support-floor selection: fixed by `NavBreakTargetPolicy` and regression test.
- C4 displaced workers beyond 72 blocks: fixed by `VillagerReturnPolicy`/`VillagerReturnGoal`; 2/2 focused passes.
- Existing diagnostic worlds remain authoritative; do not recreate.

## Current status

- A2 focused: variable entry counts (5/8 and 8/8); bell conquest verified. Full 12k OPEN.
- C4 focused: 12/12 on the two Phase-2 runs; stability still being measured.
- Phase-3 repeated validation is the next task; do not rerun the obsolete original failures as if unfixed.

## Immediate work

1. Run repeated focused A2 and C4 scenarios with identical seed/config.
2. Record every run, including failures: spawn, entry, IDX8, bell/conquest, C4 12/12, job sites.
3. Verify no `VillagerReturnGoal` remains stuck and that fishing still resumes.
4. Only after a documented reproducible focused standard, run full 12,000-tick A2.
5. Do not increase budgets, force job sites/survival, or weaken criteria.

## Worlds (preserve; do not overwrite)

- `C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-A2-IDX8-Diagnostico`
- `D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-A2-IDX8-Diagnostico`
- `C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-C4-WorkerResume-Diagnostico`
- `D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-C4-WorkerResume-Diagnostico`

Hashes and sizes are in `2026-11-15-PHASE2-FIXES.md`.

## Local-only artifacts

`.a2-backups/`, `src/foodTest/`, `tools/a2n/A2NavHarness.java.partial`, launcher helper scripts. Do not commit indiscriminately.
