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

## Verified baseline

- Branch: `feature/autonomous-village`
- Latest substantive report: `2026-11-15-PHASE5-A2-VARIABILITY.md`. Always verify `git rev-parse HEAD` and `origin/feature/autonomous-village`.
- Remote branch must be fetched and checked before any change.
- Working tree may contain local-only untracked artifacts.

## Completed investigations

- IDX8 elevated-BREAK support-floor selection: fixed by `NavBreakTargetPolicy` and regression test.
- C4 displaced workers beyond 72 blocks: fixed by `VillagerReturnPolicy`/`VillagerReturnGoal`.
- Existing diagnostic worlds remain authoritative; do not recreate.

## Current status

- A2 focused strict enclosure-entry sequence (`DENTRO-PARED`): **5, 7, 7, 7, 7** across `145020, 150410, 184205, 185459, 194632`.
- All five valid runs spawned 8/8 and destroyed the bell with exactly one `SIEGE_COMPLETED`.
- Phase-6 forensics: non-entrant IDX4 is **stalled** at `588,86,509` in 145020/184205/194632 (zero displacement ≥900 ticks), not merely slow. IDX8 is stalled at `700–702,456/457` in 145020/185459; only 150410 shows real progress cut off by the 6000 window (perimeter-only).
- A2 is OPEN; no full 12k run.
- C4 focused: **12/12 on five consecutive valid runs** (same IDs). No save/reload acceptance path.
- Elevated-BREAK and worker-return fixes are published.

## Immediate work

1. Run the separate **9000-tick observation experiment** (not acceptance) described in `2026-11-15-PHASE6-IDX4-IDX8-FORENSICS.md` to distinguish stall from timeout.
2. Preserve the 6000-tick focused results as current evidence; do not silently extend the standard.
3. C4 save/reload remains OPEN; a reload driver is required.
4. Only after the discriminating experiment and a documented focused standard, run full 12,000-tick A2.

## Worlds (preserve; do not overwrite)

- `C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-A2-IDX8-Diagnostico`
- `D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-A2-IDX8-Diagnostico`
- `C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-C4-WorkerResume-Diagnostico`
- `D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-C4-WorkerResume-Diagnostico`

Hashes and sizes are in `2026-11-15-PHASE2-FIXES.md`.

## Local-only artifacts

`.a2-backups/`, `src/foodTest/`, `tools/a2n/A2NavHarness.java.partial`, launcher helper scripts, `tools/a5-closure/`. Do not commit indiscriminately.
