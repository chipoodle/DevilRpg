# A2 recovery breach + move iteration (2026-11-21)

## Result

**PARTIAL IMPROVEMENT.** IDX3 escaped its 1×1 shaft and reached the perimeter; A2 moved from 2/8 to
3/8 perimeter, with 2/8 strict (IDX1, IDX6) unchanged. C4 PASS. No protected world was touched.

- RUN_ID: `a2n-20261121-134659`
- SOURCE: working tree after `d81b75b1` plus this iteration
- A2: 2/8 strict, 3/8 perimeter, 3 deaths
- C4: PASS integrated

## Change

When the global route contains at most one front node and the mission is not reached:

1. `AggressiveZombieEntity.abrirPasajeHacia(mission)` breaks at most two blocks
   (feet + head) in the dominant cardinal direction toward the mission.
2. Every block passes `protegidoPorLaAldea`, collision, `canBreakBlock`, and `breakBlockAt`.
3. If the opened cell is air at feet/head and supported, the executor enters a bounded
   `breachMoveTarget` phase (max 50 ticks) and drives MoveControl into that cell.
4. If exact occupancy does not occur, the phase ends and normal planning resumes.
5. The breach is only attempted from a front1/not-found global route; no budget, lease,
   protection, landing or BUILD predicate was changed.

## Evidence

IDX3 lifecycle in the run:

```text
t103  breach 595,92,600 -> move completed
t166  breach 595,92,599 -> moved
t229  breach 596,92,599 -> moved
...
t2850 ENTRO-PERIMETRO pos=611,71,583
final 616,71,578 dist=64.15
```

IDX5 also escaped its shaft (`596,76,469`) but did not reach the perimeter in this run.

IDX1/IDX6 still entered strict at t1426/t1225. C4 PASS.

## Remaining

- IDX3 reached only the perimeter (dist 64.15, strict threshold is inside 62).
- IDX5 escaped but remained outside.
- IDX2/4/7/8 still fail.
- **No runtime retry is authorized by this report.**

This validates the breach+move direction but is not the 8/8 acceptance.
