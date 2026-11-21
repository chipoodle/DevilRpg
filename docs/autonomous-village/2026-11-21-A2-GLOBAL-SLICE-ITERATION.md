# A2 global-slice iteration — 3/8 strict (2026-11-21)

## Result

**PARTIAL IMPROVEMENT.** Increasing the resumable global-search rate from 4 to 8 expansions/tick
(same 8000 total budget, same predicates) added **IDX4** as a strict entrant while keeping IDX1/IDX6.

- RUN_ID: `a2n-20261121-140816`
- A2: **3/8 strict** (IDX1, IDX4, IDX6), 3/8 perimeter, 3 deaths
- C4: PASS integrated
- No protected world touched

## Evidence

```text
IDX6 strict t634   pos 658,71,471
IDX1 strict t1066  pos 699,71,581
IDX4 strict t4326  pos 599,71,535
```

IDX3 regressed to `597,75,598` with this faster search rate; IDX5/7/8 did not enter. The total global
budget was not increased; only the per-tick resume rate changed.

## Change

`NavPlanner.GLOBAL_EXPANSIONS_PER_TICK` 4 -> 8. The A* frontier still terminates at the same 8000 total
expansions; validation, protection, support, leases and route acceptance are unchanged.

## Remaining

- IDX3 route choice regressed under the faster rate.
- IDX5 escaped its shaft but remains outside.
- IDX7/IDX8 still do not converge to a viable mission route.
- IDX2 remains a local-planning failure.
- Two consecutive 8/8 runs are not yet achieved.
