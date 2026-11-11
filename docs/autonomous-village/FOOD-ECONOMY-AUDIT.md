# Food Economy Audit — Intended Behavior vs Current Implementation

## 1. Intended behavior

Design requirements under evaluation:

- ordinary villagers eat about once per Minecraft day;
- hunger depends on work performed;
- villagers eat when hungry, before sleep;
- guards eat too, including at night, independent of ordinary bedtime;
- production (cereals, vegetables, meat, prepared food) sustains the population;
- stock should not drain continuously unless production is genuinely below demand.

No numeric multiplier is assumed. Existing values are reported first.

## 2. Current implementation and call chains

### Automatic village feeding

```text
VillageManager.tickVillageLife(...)
  -> repartirRaciones(level, center, aldeanos)
       -> VillagePantry.despensa(level, center)
       -> VillageManager.ultimaComida(level, villager)
       -> VillagePantry.sacarComida(despensa, bocas.size(), bocas.size())
       -> VillageManager.marcarComida(level, villager)
  -> pasarHambre(level, saved, objectiveIndex, aldeanos)
       -> Weakness / Slowness after HAMBRE_PACIENTE_TICKS
       -> death after STARVATION_DEATH_TICKS
       -> StarvingSince set/cleared
```

`tickVillageLife` runs on the village poll and is skipped while the village is
under attack or enemies are inside.

### Tavern feeding

```text
VillagerTavernGoal
  -> canUse: not resting, pantry >= COMIDA_MINIMA (4 points)
  -> VillagePantry.sacarComida(despensa, 1)
  -> VillageManager.marcarComida(level, villager)
```

Tavern feeding uses the same meal timestamp, so it should not stack with the
automatic ration in the same interval.

## 3. Current feeding interval and quantities

```text
EAT_INTERVAL_TICKS = 1200 ticks = 60 s = 24 intervals per Minecraft day
HAMBRE_PACIENTE_TICKS = 3600 ticks = 3 intervals
STARVATION_DEATH_TICKS = 12000 ticks = 10 intervals
```

`repartirRaciones` requests **one food point per eligible villager**:

```java
sacarComida(despensa, bocas.size(), bocas.size())
```

It then marks up to `bocas.size()` villagers. There is no profession, work, or
guard multiplier. Every non-baby villager is treated the same.

### Critical accounting detail: item granularity

`sacarComida` removes **whole food items** until the requested point total is
covered, then returns `min(removedPoints, requested)`. It does not put back the
surplus.

Example: 10 hungry villagers request 10 points. If bread is selected (4 points
per loaf), three loaves are removed = 12 points; the method returns 10 and marks
10 villagers. **12 points were consumed for a 10-point demand.** The 2-point
overdraw is not refunded or carried forward.

This is a real source-level over-withdrawal risk. It is not merely a projection;
the C1 run’s `bread 32->22` for a multi-villager meal is consistent with
granular withdrawal.

## 4. Ordinary villager behavior

| Property | Current behavior | Intended |
|---|---|---|
| Feeding cadence | Every 1200 ticks (24×/day) | About once/day |
| Portion | 1 point requested; item granularity can overdraw | Work-dependent portion, exact |
| Work dependency | None | Higher for demanding work |
| Meal timing | Village poll; independent of bedtime | Before sleep |
| Who eats | All non-baby villagers | Ordinary + guards with separate rules |
| Duplicate paths | Tavern + automatic share `marcarComida` | No double meal per interval |
| Hunger effects | Weakness/slowness after 3 intervals, death after 10 | Preserve but align to daily routine |

Ordinary villagers are fed **24 times per day**, not once. Even at one point per
meal, that is 24 points/villager/day, before item overdraw.

## 5. Guard behavior

- Guards are included in the same `aldeanos` list and same 1200-tick ration.
- There is no guard-specific feeding code in `repartirRaciones` or `pasarHambre`.
- `tickVillageLife` is skipped during attack/enemies-inside, so automatic rations
  stop during a raid.
- `VillagerTavernGoal.canUse` refuses while the village is under attack and while
  resting, so it is not a reliable guard-during-raid feeding path.
- Guards are not proven to eat at night independently of the ordinary routine.

Current behavior does **not** satisfy the stated guard requirement during raids.

## 6. Food production inventory

| Source | Class/path | Output | Rate | Runtime evidence |
|---|---|---|---|---|
| Grain/vegetables | `VillagerFarmGoal` harvest/replant, delivery to pantry | wheat, carrots, potatoes, beetroot | unknown; `WORK_TICKS=10`, `IDLE_REST_TICKS=80`, 16-item delivery threshold | none for C1 |
| Animal products | `VillagerAnimalFarmGoal` breeding/sacrifice/eggs, storage | raw meat, eggs, leather/wool | unknown; 2 animals/species target | none |
| Fish | `VillagerFisherGoal` -> `VillagePantry.guardar` | cod/salmon | unknown | none |
| Cooking | `VillagerCookGoal` | cooked meat, baked potato, fried egg, bread | max 8 pieces + 2 bread per visit, 1 log per batch | none |
| Initial stock | `VillagePantry.remesaInicial` | seeds, carrot, potato, bone meal, 2 bread | one-time | not measured |

Production rates are **not established**. No daily output per profession is
recorded in the repository. The audit therefore cannot claim a production
shortfall numerically.

## 7. Food-point conversion

```text
bread = 4
cooked meat / baked potato / fried egg = 4
raw meat = 2
vegetable = 2
wheat = 1
```

Reserves scale with population: `PUNTOS_POR_ALDEANO = 8` extra buffer per group.
This is a buffer, not a production rate.

## 8. Daily consumption model

Demand per villager at current cadence:

```text
24 meals/day × 1 point = 24 points/day
```

Actual withdrawal is higher when item granularity rounds up.

| Village | Villagers | Points/day at 24×1 | Bread-equivalent/day |
|---|---:|---:|---:|
| Small | 10 | 240 | 60 |
| Medium | 20 | 480 | 120 |
| Large | 40 | 960 | 240 |

If the intended design is one 4-point meal/day, demand would be 40/80/160
points/day—**6× lower** than the current one-point 24-meal model, and much lower
than actual item-rounded withdrawal.

These are demand projections, not measured production.

## 9. Production model

No verified per-day rates exist. Conditional model:

```text
net/day = farm output + animal output + fish + cooked conversion - demand
```

Cook conversion does not create food points from nothing except by changing raw
(2) to cooked (4) for supported recipes; it consumes logs and ingredients.
Bread converts 3 wheat (3 points) to 4 points, a +1 point gain per loaf.

Without measured harvest/animal/fish rates, net daily stock cannot be calculated.
The missing measurement is the primary evidence gap.

## 10. Small / medium / large projections

All projections below are **conditional on current 24-meal cadence** and ignore
item overdraw, which makes them optimistic:

| Village | Daily demand (points) | 7-day reserve needed | 30-day reserve needed |
|---|---:|---:|---:|
| 10 villagers | 240 | 1,680 | 7,200 |
| 20 villagers | 480 | 3,360 | 14,400 |
| 40 villagers | 960 | 6,720 | 28,800 |

A pantry holding a few hundred points cannot sustain these populations for more
than a day or two at the current cadence.

## 11. Identified imbalances and evidence

### Verified source-level imbalances

1. **Feeding frequency is 24×/day.** `EAT_INTERVAL_TICKS=1200` is used directly;
   no daily window or bedtime gate exists.
2. **No work-dependent hunger.** Every non-baby villager requests the same
   1 point.
3. **Item-granular overdraw.** Whole items are removed; surplus points above the
   requested total are not refunded.
4. **No guard-specific feeding.** Guards use the same ration list and the village
   life tick is skipped during attacks.
5. **Tavern and automatic feeding share a timestamp but are not proven free of
   double-path races.** Source uses `marcarComida`, but no runtime concurrency
   test exists.

### Unverified / not measurable yet

- Actual farm output per day.
- Animal and fish output per day.
- Cook throughput and log consumption per day.
- Whether production alone can sustain current demand.
- Whether ordinary villagers actually go to the tavern before sleep.

## 12. Current vs intended behavior

| Requirement | Current | Verdict |
|---|---|---|
| Once per day | 24 times/day | **FAIL** |
| Work-dependent hunger | None | **FAIL** |
| Eat before sleep | Village poll, no bedtime gate | **FAIL** |
| Guard night/raid feeding | Same as ordinary; skipped in attack | **FAIL** |
| Individual routine | Per-villager timestamp, but village-wide trigger | **PARTIAL** |
| Food production | Implemented, rates unknown | **UNVERIFIED** |
| Stock sustainability | 240–960 points/day demand | **AT RISK** |

## 13. Minimal proposed correction architecture

Do not implement until the design numbers are approved. Reuse existing state;
do not add a second hunger system.

1. Keep `DevilRpgUltimaComida` as the per-villager meal anchor.
2. Replace the 1200-tick feeding cadence with a **daily meal window** (one meal
   per villager per 24,000 ticks), gated before sleep.
3. Add a small per-villager **work accumulator** (e.g., harvest/cook/guard
   actions) that raises the day’s ration within an approved range. Reuse existing
   goal activity; no new inventory.
4. Keep `VillagePantry.sacarComida` as the single withdrawal path, but make
   withdrawal exact: either allow partial-point accounting or refund/carry the
   item surplus. Do not remove 12 points for a 10-point demand.
5. Add a guard rule: guards may satisfy their daily meal outside the bedtime
   window, including during a raid, using the same pantry path.
6. Keep `marcarComida` as the idempotence point shared by tavern and automatic
   feeding.
7. Preserve `StarvingSince`, weakness/slowness, and death thresholds, but
   recalibrate them to the new daily cadence only after approval.

Open design decisions requiring approval:

- exact daily points per ordinary villager;
- work multiplier range and which actions count;
- guard portion and raid feeding cooldown;
- whether a missed meal carries hunger debt or only applies effects.

## 14. Smallest runtime scenarios needed

One combined 24,000-tick disposable scenario, not many isolated ones:

1. **Daily routine:** one ordinary worker, one guard, known pantry.
2. Verify exactly one automatic meal per ordinary villager per day, before
   sleep, and no second meal in the same day.
3. Verify the guard can eat at night/outside bedtime.
4. Measure one full farm harvest -> pantry delta and one cook batch -> output.
5. Hold food empty for three days and verify hunger effects and recovery.
6. Optional reload at day boundary for persistence.

PASS requires exact pantry deltas and meal timestamps. A goal starting is not
evidence.

## 15. Answer: why does the pantry empty?

**Both problems coexist, but over-consumption is the proven dominant structural
cause.**

- Demand is 24 meals/day/villager, versus an intended roughly 1/day.
- Item-granular withdrawal can remove more points than requested.
- Guards have no separate rule and may not eat during raids.
- Production exists but its daily rate is unmeasured, so a production shortfall
  cannot yet be quantified.

The next evidence needed is a measured 24,000-tick production/consumption run.
Until then, do not “fix” production by adding food; first correct the cadence and
exact withdrawal, then measure whether production sustains the reduced demand.
