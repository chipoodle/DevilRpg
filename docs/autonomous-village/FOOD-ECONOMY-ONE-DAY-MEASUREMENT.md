# Food Economy — One-Day Measurement (24,000 ticks)

## Result

**Measured, with one fixture limitation: guard and ordinary worker resolved to the
same villager.** The day is valid for consumption cadence and production totals,
but not for an independent guard measurement.

Run:

```text
ID: stabilization-block1-scan-20261030-85
World: build/stabilization-block1-scan-20261030-85
Scenario: food-day
Ticks: 600 -> 24,600
```

## Initial conditions

```text
[C1-DAY] setup bread=256 points=256
         worker=4a5d61a8-c973-472f-b464-61acda3a1902 mealW=4000
         guard=4a5d61a8-c973-472f-b464-61acda3a1902 mealG=4000
         tick=600
```

- Pantry seeded with 256 bread (1,024 points by bread value), but `comida()`
  reported 256 points because other seeded items/reserves affect the total.
- Worker and guard are the **same UUID**; guard results are therefore invalid as
  an independent measurement.
- No cooked output was seeded.
- Production during the day was entirely villager-driven.

## Consumption results

Tracked villager meal timestamps advanced 12 times:

```text
n=1 tick=1379    n=7 tick=8979
n=2 tick=2866    n=8 tick=10179
n=3 tick=4179    n=9 tick=11379
n=4 tick=5379    n=10 tick=21579
n=5 tick=6579    n=11 tick=22779
n=6 tick=7779    n=12 tick=23979
```

- 12 meals in 24,000 ticks, not 20, because village-life feeding is skipped
  during attack/enemy-inside conditions and some intervals were missed.
- Each event requests 1 food point, with whole-item overdraw.
- Observed meal spacing is usually 1,200–1,500 ticks, confirming the 60-second
  cadence when village life runs.
- The long gap 11,379 → 21,579 shows feeding stops during threat/attack
  conditions.

## Guard results

**Not independently measured.** The selected guard UUID equals the worker UUID.
The runtime cannot support a guard-vs-ordinary comparison from this run.

Source facts remain:

- guards use the same `repartirRaciones` list;
- `tickVillageLife` is skipped while under attack/enemies inside;
- `VillagerTavernGoal` refuses under attack;
- no guard-specific night/raid feeding path exists.

## Farming results

Production log totals during the day:

```text
Farm delivery items counted from logs: 76
Examples:
  "el granjero guardo 8 cosa(s) de su oficio en la despensa" (multiple)
  "El granjero: Guardo 16 en la despensa"
  "El granjero: Trajo 7 del almacen a la despensa"
```

This is an aggregate of the logged farmer delivery lines, not a per-crop
breakdown. The farmer did produce and deliver through the production pantry
path. Exact crop composition was not logged, so a precise point total cannot be
derived.

## Cooking results

```text
Cook batches: 3 + 1 + 1 + 1 + 8 + 2 + 3 = 18 pieces
Logs consumed: 6 (one per batch)
Destination: pantry
```

This is real cooking through `VillagerCookGoal`, not seeded cooked output.
Ingredient inputs are not itemized in the log, so exact raw-to-cooked point
conversion is not reconciled.

## Fishing / animal production

```text
Fishing deliveries: 9 events (cod/salmon)
Animal: chicken and pig sacrifices logged
```

Their item counts and food-point values are not individually summed by the
existing logs.

## Food accounting

```text
Start:  bread=256  pantry points=256
End:    bread=0    pantry points=64
```

- Bread stock fell by 256.
- Pantry points fell by 192 net.
- 76 farm items, 18 cooked pieces, 9 fish events, and animal sacrifices were
  added during the day.
- The village still ended with only 64 points and zero bread.
- The pantry hit 0 points at tick 11,379, then recovered to 30–82 points from
  farm/fish/cook activity, ending at 64.

**Measured daily net balance for this population: negative.** Production was
non-zero but did not prevent the pantry reaching zero mid-day.

## Whole-item over-withdrawal

The audit example is **source-verified but not independently reproduced** in this
run:

```text
10 villagers request 10 points
bread = 4 points each
3 bread removed = 12 points
method returns 10; 2 points are not refunded
```

The day's bread 256→0 with only 192 net point loss is consistent with
whole-item removal, but mixed food types prevent an exact overdraw figure.

## Answers

1. **Ordinary villager:** 12 meal-timestamp advances in 24,000 ticks; at least
   12 requested points, plus item overdraw. Intended once/day is not met.
2. **Guard:** cannot be separated from the worker in this run; source uses the
   same ration path.
3. **Guard at night/raid:** not proven; source skips village life during attacks
   and tavern refuses under attack.
4. **Farmer:** 76 logged delivery items, exact crop mix unknown.
5. **Cook:** 18 cooked pieces from 6 batches/logs, ingredient detail incomplete.
6. **Over-withdrawal:** source-proven; exact day loss not isolated.
7. **Balance:** negative for this test population.
8. **Smallest justified corrections:** daily meal window, exact point
   withdrawal/refund, and a separate guard rule. Do not buff production until
   cadence is corrected.

## Evidence limitations

- Guard and worker are the same entity.
- No per-crop/per-item pantry ledger; only aggregate counts.
- Village population varied and attacks/enemies skipped feeding intervals.
- No isolated 10-villager overdraw fixture.
- No save/reload in this run.
- Production totals are log-derived aggregates, not itemized transactions.

## Next measurement

Rerun a corrected `food-day` with:

- guard explicitly selected **different** from the ordinary worker;
- per-item pantry delta logging;
- a controlled no-attack day;
- exact farm crop counts and cook input/output.

That run is required before implementing the daily feeding policy.
