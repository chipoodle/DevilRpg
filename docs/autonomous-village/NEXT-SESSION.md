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

- A2 focused strict enclosure-entry sequence (`DENTRO-PARED`): **5, 7, 7, 7, 7**.
- Phase-7 observation `a2n-20261115-203001` (9000 ticks, OBSERVATION — NOT ACCEPTANCE):
  - IDX4: frozen 5000–6000 at `588,86,509`; resumed after 6000, reached `603,71,507` (dist 63.35), then stopped just outside strict entry.
  - IDX8: moved until ~6000, then only 3 unique positions over 3000 ticks at `710,59,469` (dist 80.49), targeting a villager, state `PLANNING`.
- C4 at 9000 observation: 12/12; official focused C4 remains 5/5.
- No full 12k; no production code changed.

## Immediate work

1. Instrument the **IDX8 villager-target/`PLANNING`** interaction at `710,59,469` to prove whether combat targeting suppresses waypoint execution; do not change navigation yet.
2. Treat IDX4 as a 6000-window timeout/delayed route, not a permanent stall; do not extend the official focused standard silently.
3. C4 save/reload remains OPEN (no reload acceptance path).
4. Only after an evidence-backed IDX8 correction and a documented focused standard, run full 12,000-tick A2.

## Worlds (preserve; do not overwrite)

- `C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-A2-IDX8-Diagnostico`
- `D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-A2-IDX8-Diagnostico`
- `C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-C4-WorkerResume-Diagnostico`
- `D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-C4-WorkerResume-Diagnostico`

Hashes and sizes are in `2026-11-15-PHASE2-FIXES.md`.

## Local-only artifacts

`.a2-backups/`, `src/foodTest/`, `tools/a2n/A2NavHarness.java.partial`, launcher helper scripts, `tools/a5-closure/`. Do not commit indiscriminately.
