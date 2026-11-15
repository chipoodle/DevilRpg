# A2 IDX8 + C4 Worker Resume — Forensic Report 2026-11-15

## Baseline

- Branch `feature/autonomous-village`, HEAD `518fee13fc15bfb9b92b0479d86b5c609d1947da`.
- Dirty tree preserved. No reset/clean/stash/revert/commit/push.
- A2 run: `a2n-20261115-024825`.
- C4 run: same run, final `fin.txt` `C4_FAIL`, 9/12 workers resumed, `jobSite=[2,0,0]`.
- A2 pre-bell snapshot: `a2n-20261115-031530`, tick 600.
- Forensic checkpoint: `.a2-backups/forensic-checkpoint-20261115-1413/`.

## A2 IDX8

UUID `0fa7101a-5d7d-45cd-906a-b90ae8c1001d`.

Runtime timeline (20 s samples):

```text
pos 747,63,534 dist 86.90 tgt local 736,64,575 BREAK-DELEGADO(Stone)
pos 740,63,495 dist 87.67 same target
pos 735,63,488 dist 86.98 same target
pos 747,63,524 dist 86.88 same target
pos 745,63,509 dist 87.78 same target
pos 726,63,472 dist 89.06 same target
pos 705,62,457 dist 87.80 same target
pos 701,64,457 dist 85.41 state RECOVERING tgt 689,66,450 JUMP_UP
```

Root cause classification: **local route/waypoint non-convergence**, not idle and not terminal FAILED. IDX8 keeps a global route toward `661,72,533` but repeatedly picks local targets at `736,64,575` / `689,66,450`, both outside the village, with `BREAK-DELEGADO (Stone)`/`JUMP_UP` never resolving into an entry. It moves, but oscillates in a ~20-block region and distance remains ~85–89 blocks. It never crosses the enclosure.

No correction was applied: evidence identifies a route/local-target instability at the east/north boundary, but no single safe root fix was proven without a targeted diagnostic run. Do not classify this as combat death.

Other seven: spawned, approached, entered, and died in guard combat (see run log). Bell conquest verified.

## C4

Latest failure `a2n-20261115-024825`: 9/12 resumed.

Failed workers:

| UUID | Profession | Pos | Job site | State |
|---|---|---|---|---|
| `254f3367-795c-4c7b-afdb-75fb0a7115f2` | weaponsmith | 652,62,431 | none | no running goal |
| `f170acc0-a486-4c5f-8f0e-6e6833fa1c71` | fisherman | 678,62,436 | 682,71,572 (barrel present) | no running goal |
| `615e8c86-2d7b-4f3a-bdc4-c8756d370c6d` | mason | 698,62,454 | 692,71,502 | only `VillagerPickupGoal` |

All are alive, ~87–102 blocks north of center, idle, with no hostile/rest state. So the failure is not a missing job site alone: the fisherman has a valid site and still does not start `VillagerFisherGoal`. The common factor is physical displacement outside the village and failure to recover/return; job-site/no-site is a secondary symptom.

C4 is intermittent: identical earlier run `a2n-20261115-004956` passed 12/12. No C4 code change was made because no deterministic goal-installation/memory-order defect was proven.

## Diagnostic worlds

### DevilRpg-A2-IDX8-Diagnostico

- Source: `build/a2n/a2n-20261115-031530/game/a2n` (tick 600, pre-bell).
- Index8 at `747,63,536`, label `A2 IDX8 PLANNING tgt=747,68,535`.
- All 8 labels IDX1–IDX8 present; bell and wall intact; shared spawn 96,144,-32.
- Std: `C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-A2-IDX8-Diagnostico`
- Dev: `D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-A2-IDX8-Diagnostico`
- Size 15,966,366; level.dat SHA-256 `BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7`.

### DevilRpg-C4-WorkerResume-Diagnostico

- Source: final world from `a2n-20261115-024825` (C4 failure state).
- All three failed worker UUIDs verified in `entities/r.1.0.mca` at the listed coordinates.
- Spawn set to `661,80,590` for inspection.
- Std: `C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-C4-WorkerResume-Diagnostico`
- Dev: `D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-C4-WorkerResume-Diagnostico`
- Size 16,044,629; level.dat SHA-256 `11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF`.

Both copies match by size and level.dat hash. `session.lock` removed. Structural verification only; no GUI launch.

## Tests

```text
gradlew test: PASS
python tools/lint_aldea.py --strict: OK
git diff --check: PASS
AdvanceUnderFirePolicyTest: 11 assertions OK
```

## Remaining blockers

1. IDX8 local route target non-convergence at east/north boundary.
2. C4 worker recovery intermittency (9/12), including a worker with a valid job site.
3. Full A2 12k under corrected criteria and stable C4 are not claimed.
