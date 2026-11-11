# Food Economy — Block 3 Acceptance

## Result: **PARTIAL — daily cadence and value credit implemented; pre-sleep timing, work-dependent hunger, guard raid feeding, persistence of credit, and automated tests remain open.**

Production changes were made; this block is not fully closed.

## Production defects addressed

1. **Over-withdrawal:** `VillagePantry.sacarComida` removed 12 points for a
   10-point request.
2. **24 meals/day:** `EAT_INTERVAL_TICKS` was 1,200 ticks.
3. **Guard gap:** guards had no feeding path independent of the village-life tick.

## Implemented changes

### Ordinary daily ration

```java
EAT_INTERVAL_TICKS = 20 * 60 * 20; // 24,000 ticks
```

`repartirRaciones` now returns 0 unless `level.isNight()`, making the ordinary
ration a nightly opportunity rather than a 60-second poll.

This is a nightly gate, not yet a true per-villager bedtime handshake.

### Guard feeding

`VillagerGuardGoal.tick()` now checks every 200 ticks:

```text
if ultimaComida >= 24,000 ticks:
    withdraw 1 point from the pantry
    marcarComida
```

This runs before the guard's `destino` early-return, so it can run while the
guard is active during a raid. It uses the same pantry and meal timestamp.

### Pantry value credit

`VillagePantry` keeps a per-container residual credit:

```text
request 10, 3 bread (12 points)
-> remove 3 bread
-> consume 10 points
-> retain 2-point credit
-> next request consumes the credit instead of removing more food
```

The credit is held in a `WeakHashMap<Container,Integer>`. It conserves value
within a server session but **is not persisted**. This does not yet meet the
save/reload requirement.

## Run 87 — runtime evidence

```text
ID: stabilization-block1-scan-20261030-87
World: build/stabilization-block1-scan-20261030-87
Scenario: food-day
Ticks: 600 -> 24,600
```

Distinct roles:

```text
worker = e774aa05-1520-4670-a19a-e2b0eb603d57
guard  = 4a5d61a8-c973-472f-b464-61acda3a1902
farmer = 777fee9e-75a1-455d-a421-0c39c5eb60f8
cook   = a445a483-bc30-40eb-b1a1-0ad7dfc92fb6
```

Observed:

```text
[C1-DAY] workerMeal n=1 tick=24179 pointsLeft=274 bread=10
[C1-DAY] guardMeal  n=1 tick=24179
[C1-DAY] snapshotEnd bread=9 wheat=7 carrot=42 potato=13 beet=42 cooked=19
[C1-DAY] end bread 256->9 points 256->369 workerMeals=1 guardMeals=1
[A5] FIN result=PASS
```

## Acceptance matrix

| Criterion | Result | Evidence |
|---|---|---|
| One ordinary meal per day | **PASS (count)** | workerMeals=1 |
| Meal before sleep | **NOT PROVEN** | meal at tick 24,179, not a recorded sleep transition |
| Work-dependent hunger | **NOT IMPLEMENTED** | no work accumulator |
| Guard distinct identity | **PASS** | different UUID |
| Guard day/night feeding | **PARTIAL** | 1 meal, but same tick as ordinary meal; attribution ambiguous |
| Guard during raid | **NOT PROVEN** | no raid in run 87 |
| No duplicate meal | **SOURCE-VERIFIED** | shared `marcarComida` |
| Exact pantry conservation | **SOURCE-VERIFIED** | credit retained; second-call assertion not logged |
| Pantry credit persistence | **FAIL** | `WeakHashMap`, not saved |
| Automated tests | **NOT ADDED** | none |
| Daily balance | **POSITIVE in run 87** | 256 -> 369 |

## Work-dependent hunger

Not implemented. There is no per-villager daily work accumulator in the
correction. The intended two-workload policy remains an open design/implementation
item.

## Why pre-sleep timing is not proven

The only worker meal was logged at tick 24,179. That is near the start of the
next Minecraft day, not a demonstrated pre-sleep transition. The nightly gate
allows a meal, but it does not prove the villager ate before going to bed.

## Guard attribution

The single guard meal occurred at the same tick as the ordinary worker meal.
This is consistent with the ordinary nightly ration reaching the guard, not with
the independent guard path. No `[Village] Guardia ... comio en su puesto` line
was emitted. Therefore the guard-specific path was not independently exercised.

## Balance

Run 87 ended with **369 points** (bread 9, wheat 7, carrot 42, potato 13,
beetroot 42, cooked 19) versus 256 at start. This is a positive one-day balance
for this seed/population, but it does not establish sustainable production
rates. Production remains variable and was not measured per profession.

## Known limitations

- No save/reload; credit is session-only.
- No automated tests for the credit or daily feeding.
- No work-dependency.
- No proven pre-sleep meal transition.
- No raid-time guard feeding test.
- No per-item transaction ledger.
- Production sustainability not established.

## Next required work

1. Add automated pantry tests: 10/3 bread credit, repeated calls, mixed
   denominations, insufficient stock.
2. Persist pantry credit in `VillageSavedData` (or an equivalent existing saved
   state), not a static map.
3. Add a per-villager daily work accumulator and two workload tests.
4. Gate the ordinary meal on the actual bedtime/sleep transition, not only
   `isNight()`.
5. Run a guard-only raid feeding scenario and assert the guard-specific log.
6. Rerun the 24,000-tick scenario with a recorded sleep transition.

Until then, Block 3 is **PARTIAL**, not complete.
