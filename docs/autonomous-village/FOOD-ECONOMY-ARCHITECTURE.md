# Food Economy — Architecture and Invariants (Block 3.2)

Status: CLOSED. Runtime evidence: Runs 105, 109, 110, 120, 128, 130, 132.

## A. Hunger model
- `DevilRpgHambre` (int, villager persistent data), clamped 0..100 by
  `VillageManager.hambre` / `FoodHungerPolicy.clamp`.
- Normal threshold 60 (`HAMBRE_NORMAL`), critical 90 (`HAMBRE_CRITICA`), max 100.
- `tickVillageLife` adds +1 to every non-baby villager per village poll, only in
  peacetime (the caller is behind attack/enemy checks).
- `VillageManager.aumentarHambre` / `reducirHambre` use `FoodHungerPolicy.add/reduce`.
- Successful tavern meal always reduces exactly 25.
- Entity identity is the vanilla villager UUID; hunger is saved in entity NBT.

## B. Starvation lifecycle
- `DevilRpgHambreCriticaDesde` (long, villager persistent data): first game time
  with hunger >= 90; initialized once, cleared when hunger < 90.
- `DevilRpgStarvationActive` (boolean): effects were applied by starvation.
- `pasarHambre` (inside `tickVillageLife`) uses the critical timestamp:
  - effects (Weakness + Slowness) at 3600 critical ticks;
  - death at 12000 critical ticks;
  - below 90: clear timestamp, remove effects, clear active flag.
- `StarvingSince` in `VillageSavedData` is village-level only (warning/display),
  not individual starvation state.
- Run 109: effects at 3600 and recovery; Run 110: death exactly at 12000.

## C. Tavern feeding
- `VillagerTavernGoal`, priority `-1`, flags MOVE + LOOK.
- Installed by `VillageManager.asegurarGoalDeLaTaberna` for every non-baby
  villager during village maintenance; survives reload because the goal is
  re-added by production maintenance.
- `canUse` order: restTicks 40; baby/level; `tieneHambre` AND tavern built;
  attack/sleep (critical guard bypasses); pantry >= 4; pantry support destination.
- `start`: navigate to pantry support point (always; table path no longer gates
  food access).
- `tick`: walk to a standable cell, then on arrival withdraw food, mark meal,
  reduce hunger by 25, play eat sound, log `[C1-MEAL]`.
- `canContinueToUse`: stops on sleep, 160 stuck ticks, or no hunger/waits.
- `stop`: restTicks = 40, stop navigation.
- Meal transaction: `VillagePantry.sacarComida(level, center, 1, 1)` removes one
  logical point; because items are whole, a 4-point item can leave 3 credit.
  Only on `racion > 0` does it call `marcarComida` and reduce hunger by 25.
- `DevilRpgUltimaComida` is the last-meal game time. `tieneHambre` also requires
  `gameTime - ultimaComida >= 3600`, preventing immediate duplicate meals.
- Night: `estaDescansando` rejects ordinary villagers; a guard at hunger >=90
  bypasses attack/sleep. Daytime reload test used legitimate wake-up.

## D. Pantry accounting
- Physical pantry: `VillagePantry.despensa` container; food points are per item
  group (bread etc.). Physical item counts differ from logical points.
- Logical credit: `VillageSavedData.pantryCredit`, keyed by objective index,
  saved as `PantryCredit` per objective.
- `setPantryCredit` clamps and marks saved data dirty.
- `sacarComida(level, center, points, villagers)`:
  1. spend existing credit first;
  2. withdraw physical food for the remainder;
  3. any whole-item overshoot becomes credit;
  4. return the logical points actually satisfied.
- Conservation: physical points = remaining physical + consumed logical +
  residual credit.

## E. Guard feeding and defense
- There is no separate guard pantry withdrawal. Guards feed through the same
  `VillagerTavernGoal`; a guard at critical hunger may bypass attack/sleep.
- Normal and raid guard meals use the shared 3600-tick cooldown.
- `VillagerGuardGoal` combat target is the private `Monster enemigo`, not
  `Mob.getTarget()`. `enemigoActual()` is a read-only diagnostic accessor.
- A5 previously read `getTarget()` and produced false negatives. Run 128 uses
  `enemigoActual()`; raid meal 100->75 and target `2fa83e6d` at 2.44 blocks.
- Guard target selection lives in `buscarEnemigo`; do not replace it with the
  vanilla target slot.

## F. Persistence and lifecycle
- Hunger/critical timestamp/last meal: villager persistent NBT; saved with entity.
- Pantry credit: `VillageSavedData` saved with the level.
- UUID identity is exact after reload; objective index is `objectiveAt(center)`.
- Run 130: hunger 100 persisted, credit 2 persisted and consumed once.
- Run 132: daytime reload, `sleep=false`, second real meal 94->69, one tracked
  meal, cooldown enforced.
- Limitation: no automated NBT serialization test. `VillageSavedData` needs
  Minecraft registry/lifecycle types; runtime remains the evidence.

## G. Historical defects (symptom -> root -> correction -> evidence)
1. Meals only near tick 24000: `tieneHambre` still used rolling timestamp;
   switched to `hambre >= 60` (Run 105).
2. Duplicate guard meals 330 ticks apart: no minimum interval; added 3600-tick
   cooldown in `tieneHambre` (Run 113 defect, Runs 116/128 fixed).
3. Starvation weakness never applied: `HAMBRE_PACIENTE_TICKS` was 3*EAT_INTERVAL
   (72000) while death was 12000; changed to 3600 (Runs 108/109).
4. Pantry overshoot lost: whole-item overdraw became credit; persisted and
   consumed once (Run 130).
5. Guard defense false negative: fixture read `getTarget()`, guard uses
   `enemigo`; added `enemigoActual()` and fixed A5 (Run 128).
6. MEAL2 save timeout: fixture waited for hunger<=45 after a high-hunger meal;
   changed to last-meal timestamp change (Run 132).
7. Night reload no meal: `sleep=true`; fixture set daytime and waited for
   legitimate wake-up (Run 132).
8. Immediate no-meal timeout: persisted game time already exceeded 9000 ticks;
   window raised to 22000 (fixture only).
9. Wrong post-meal assertion: required hunger<60 after regrowth; changed to
   exactly one tracked meal (Run 132).
10. Post-reload second meal: PASS in Run 132 after fixture corrections.

## H. Tests and evidence
- `FoodHungerPolicyTest` (policyTest): 59/60/61 eligibility, 89/90 critical,
  cap100/floor0, 25-point reduction, effects 3599/3600, death 11999/12000,
  single critical timestamp.
- `policyTest` includes navigation/forestry/farm tests; all pass.
- Not automated: Minecraft effect/death, GoalSelector, navigation, NBT save/load.
- Runtime: 105 normal feeding; 109 effects/recovery; 110 death; 120/128 guard
  raid+defense; 130 credit persistence; 132 daytime post-reload second meal.
