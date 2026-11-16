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

- Latest substantive report: `2026-11-15-PHASE8-IDX8-DECISION-TRACE.md`.
- A2 focused strict enclosure-entry sequence remains **5, 7, 7, 7, 7** (145020, 150410, 184205, 185459, 194632).
- Phase-7 observation `a2n-20261115-203001` (9000, OBSERVATION — NOT ACCEPTANCE): IDX4 resumed after 6000 but stopped at `603,71,507`; IDX8 stalled at `710,59,469`.
- Phase-8 diagnostic `a2n-20261115-205715` did **not** reproduce the outcrop stall; IDX8 entered at 2347 and died in combat. Source ownership mechanism confirmed, cause of the stall not proven.
- C4 official focused: **5/5 PASS**; no save/reload acceptance path.
- A2 full 12k OPEN; no production navigation change.

## Immediate work

1. Run a **9000-tick observation with `-Da2n.probeTargets=true`** in a new disposable world to catch the eastern-outcrop stall with `combateActivo`, `executorMayMove`, target UUID/distance and route state active.
2. Do not change navigation behavior until that trace proves the cause.
3. C4 save/reload remains OPEN (requires a reload driver).
4. Only after a demonstrated cause and focused standard, run full 12,000-tick A2.

## Worlds (preserve; do not overwrite)

- `C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-A2-IDX8-Diagnostico`
- `D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-A2-IDX8-Diagnostico`
- `C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-C4-WorkerResume-Diagnostico`
- `D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-C4-WorkerResume-Diagnostico`

Hashes and sizes are in `2026-11-15-PHASE2-FIXES.md`.

## Local-only artifacts

`.a2-backups/`, `src/foodTest/`, `tools/a2n/A2NavHarness.java.partial`, launcher helper scripts, `tools/a5-closure/`. Do not commit indiscriminately.
