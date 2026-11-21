# A2 high-altitude prefilter iteration — 4/8 perimeter (2026-11-21)

## Result

**PARTIAL IMPROVEMENT.** Rejecting foliage-blocked drop corridors during planning, only for high
descents (`feetY >= 100`), recovered IDX4's wasted descent cycles while preserving IDX3's successful
route under slice 7.

- RUN_ID: `a2n-20261121-150753`
- A2: **3/8 strict** (IDX1, IDX3, IDX6), **4/8 perimeter** (adds IDX4)
- C4: PASS integrated
- IDX4 final `599,71,524`, dist `62.15` (strict threshold inside 62)

## Change

1. Slice restored to 7 expansions/tick.
2. In `NavPlanner.validarArista`, for DROP edges with `piesDesde >= 100`, reject the edge if its fall
   corridor contains `LEAVES` or `LOGS`. This prevents 19 IDX4 execution-time rejects at Y118/Y115.
3. The prefilter is inactive for lower routes, preserving IDX3's graph topology.

## Evidence

```text
IDX1 strict t1031  pos 699,71,581
IDX3 strict t3980  pos 608,71,565
IDX6 strict t634   pos 658,71,471
IDX4 perimeter t5266 pos 590,71,522; final 599,71,524 dist=62.15
```

Slice 8 with the same prefilter loses IDX3 and keeps IDX4; slice 7 + prefilter keeps both at
perimeter/strict respectively.

## Remaining

- IDX4 needs ~0.15 blocks more; it is the closest miss.
- IDX5 escaped its shaft but remains outside.
- IDX2/7/8 still fail.
- Two consecutive 8/8 remain unachieved.
