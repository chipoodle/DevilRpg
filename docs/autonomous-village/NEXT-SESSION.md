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
- Phase-4 substantive commit: `2026-11-15-PHASE4-STABILITY.md` accompanies this checkpoint. Always verify `git rev-parse HEAD` and `origin/feature/autonomous-village`.
- Remote branch must be fetched and checked before any change.
- Working tree may contain local-only untracked artifacts; read the final report of the latest session.

## Completed investigations

- IDX8 elevated-BREAK support-floor selection: fixed by `NavBreakTargetPolicy` and regression test.
- C4 displaced workers beyond 72 blocks: fixed by `VillagerReturnPolicy`/`VillagerReturnGoal`; 2/2 focused passes.
- Existing diagnostic worlds remain authoritative; do not recreate.

## Current status

- A2 focused valid entry sequence: 5, 8, 7, 7; all valid runs destroyed the bell and emitted one conquest event. 3/4 runs reached ≥7/8; one 5/8. Full 12k OPEN.
- C4 focused: 12/12 on four consecutive valid runs (145020, 150410, 184205, 185459). Save/reload support not established.
- Elevated-BREAK support-floor fix and worker return goal are published.

## Immediate work

1. Run at least two additional valid focused A2 repetitions and two C4 repetitions (including save/reload if supported), same seed/configuration.
2. Record every run, including failures; do not count suspended/interrupted runs.
3. Verify C4 12/12, fisherman restart, and no worker stuck in return goal.
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
