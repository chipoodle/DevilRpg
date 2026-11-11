# Food Autonomy — Block 2 (C1) Acceptance

## Status

**OPEN — consumption/shortage/recovery are runtime verified (Run 84); farm/cook production and persistence remain unverified.**

No production Java code was changed. Source inspection corrected the prior
audit assumption that food consumption was absent; it is implemented in
`VillageManager`.

## Scope and intended behavior

C1 covers the existing production chain:

```text
VillagerFarmGoal (produce/harvest)
  -> VillagePantry / VillageStorage (store)
  -> VillagerCookGoal (prepare, where applicable)
  -> VillageManager.repartirRaciones (consume)
  -> VillageManager.pasarHambre (shortage response)
  -> recovery when food returns
```

No new hunger, nutrition, food category, or economy rule is introduced.

## Actual architecture and call chain

| Stage | Production implementation |
|---|---|
| Village life tick | `VillageManager.tickVillageLife(...)` |
| Ration distribution | `VillageManager.repartirRaciones(level, center, aldeanos)` |
| Pantry food value | `VillagePantry.comida(level, center)` |
| Pantry withdrawal | `VillagePantry.sacarComida(container, points, villagers)` |
| Per-villager meal mark | `VillageManager.marcarComida(level, villager)` |
| Hunger effects | `VillageManager.pasarHambre(...)` |
| Last meal | `VillageManager.ultimaComida(...)` |
| Tavern eating | `VillagerTavernGoal` uses the same meal mark |
| Persistence | `VillageSavedData` (`Food`, `StarvingSince`); villager `DevilRpgUltimaComida` in persistent data |

Observed constants:

```text
EAT_INTERVAL_TICKS = 60 * 20
HAMBRE_PACIENTE_TICKS = 3 * EAT_INTERVAL_TICKS
STARVATION_DEATH_TICKS = 10 * 60 * 20
```

`repartirRaciones`:

1. finds the pantry;
2. reads each non-baby villager’s last-meal timestamp;
3. returns 0 until the oldest villager has waited `EAT_INTERVAL_TICKS`;
4. selects all villagers whose interval elapsed;
5. removes food points from the pantry with `sacarComida`;
6. marks each fed villager with `marcarComida`.

`pasarHambre`:

- babies do not consume a ration and their clock is refreshed;
- after `HAMBRE_PACIENTE_TICKS`, applies Weakness and Slowness;
- after `STARVATION_DEATH_TICKS`, kills that villager;
- sets/clears `StarvingSince` and emits recovery/ shortage messages.

### What was already working in source

- Farm production and harvesting code exists (`VillagerFarmGoal`).
- Storage/pantry accounting exists (`VillageStorage`, `VillagePantry`).
- Cook preparation exists (`VillagerCookGoal`).
- Consumption exists (`VillageManager.repartirRaciones`).
- Hunger response exists (`VillageManager.pasarHambre`).
- Food values and village food totals are persisted (`VillageSavedData`).

### What was not demonstrated

No disposable C1 runtime scenario exists yet that proves the full chain with
known quantities. The A5 harness has no food scenario; existing scenarios cover
forestry, mining, reload, and navigation, not farm-to-consumption.

## C1 acceptance matrix

| ID | Criterion | Status | Evidence |
|---|---|---|---|
| C1.1 | Farmer produces food through existing workflow | **BLOCKED (unverified)** | `VillagerFarmGoal` source only; no farm runtime scenario |
| C1.2 | Food reaches storage/pantry with exact accounting | **BLOCKED (unverified)** | `VillageFarmGoal` delivery code; no delta measured |
| C1.3 | Cook prepares ingredients and delivers output | **BLOCKED (unverified)** | `VillagerCookGoal` source only |
| C1.4 | Villager actually consumes food | **PASS — RUNTIME VERIFIED** | Run 84: bread 32->22, meal 4000->5200 |
| C1.5 | Shortage response is coherent | **PASS — RUNTIME VERIFIED** | Run 84: weakness/slow, `StarvingSince=8800` |
| C1.6 | Recovery resumes without duplication | **PASS — RUNTIME VERIFIED** | Run 84: meal 14000, weakness false, `StarvingSince=0` |
| C1.7 | Food/villager state persists across reload | **PARTIAL** | `Food`/`StarvingSince` saved; no C1 reload scenario |

C1 is **not PASS** until at least C1.1–C1.6 are observed end to end in one
bounded disposable world.

## Food transaction model

```text
produced/harvested (farm)
  -> delivered (pantry/storage exact deltas)
  -> ingredients withdrawn (cook)
  -> prepared output delivered (cook)
  -> food points available (VillagePantry.comida)
  -> points withdrawn per fed villager (sacarComida)
  -> meal timestamp advanced (marcarComida)
  -> remaining stock (pantry recount)
```

Food values are existing constants: bread/cooked meat 4, raw meat/vegetable 2,
wheat 1. No conversion is invented here.

## Required runtime scenario (next task)

Create one disposable `food-autonomy` A5 scenario with known initial pantry and
warehouse contents:

1. one farmer, one cook, known plots and stock;
2. record pre-run pantry/warehouse counts and villager UUIDs/professions;
3. let the farmer produce and deliver a known crop;
4. verify pantry delta;
5. let the cook prepare one batch and verify ingredient/output delta;
6. advance past `EAT_INTERVAL_TICKS` and verify pantry food points decrease and
   at least one `DevilRpgUltimaComida` timestamp advances;
7. empty the pantry, advance past `HAMBRE_PACIENTE_TICKS`, verify Weakness or
   Slowness on a villager and the shortage log;
8. restore food and verify recovery (`vuelve a tener comida`), with no extra
   withdrawal.

PASS requires in-game log evidence and exact count reconciliation. Starting a
goal or producing a log line is not sufficient.

## Root cause / correction

No defect was demonstrated. The prior audit's “consumption not found” statement
was incorrect and has been corrected in the master plan. There is no production
change to justify yet.

## Validation

```text
python tools/lint_aldea.py --strict = PASS
git diff --check = PASS
```

No Minecraft run was performed for C1 in this phase; no production build was
changed.

## Known limitations

- Farm, cook, and consumption integration is not runtime verified.
- Shortage death/recovery was not exercised.
- Food persistence across reload was not exercised.
- Concurrent pantry withdrawal was not tested.
- The existing A5 harness has no food scenario; adding one is the next bounded
  task, not a new framework.

## Regression policy

- Farm ownership: `VillageFarmOwnershipTest`.
- Storage/delivery: exact pantry/warehouse delta scenario.
- Consumption/shortage: C1 food scenario above.
- Persistence: C1 save/reload variant.
- Forestry must not be rereun for food changes unless shared storage code changes.

## Documentation updates

- `AUTONOMOUS-VILLAGE-MASTER-PLAN.md`: corrected the “consumption not found”
  finding and updated the C1 status.
- `docs/CONTINUAR.md` / `docs/PENDIENTE.md`: point to this acceptance record and
  the next runtime scenario.

## Runtime evidence — Run 84

World: `build/stabilization-block1-scan-20261030-84`

```text
[C1-FOOD] setup bread=32 points=140 uuid=4a5d61a8-... meal=4000 tick=600
[C1-FOOD] consume bread 32->22 pointsNow=108 meal 4000->5200
          consumed=true tick=1900
[C1-FOOD] shortage points=0 weakness=true slow=true
          starvingSince=8800 tick=8900
[C1-FOOD] recovery points=68 meal=14000 weakness=false
          starvingSince=0 recovered=true tick=10200
[A5] FIN result=PASS detail=C1 consumption/shortage/recovery verified
```

- C1.4 PASS: a real ration interval withdrew 10 bread (40 points) across the
  village and advanced the tracked villager's meal timestamp.
- C1.5 PASS: with the pantry held empty through 3700 ticks since the last meal,
  the villager received Weakness and Slowness; `StarvingSince=8800`.
- C1.6 PASS: restoring bread produced a new meal (`14000`), cleared weakness and
  `StarvingSince`, with pantry points remaining.
- No duplicate withdrawal was observed in the tracked interval.

Farm/cook production (C1.1–C1.3) was not exercised by this scenario and remains
BLOCKED. Persistence (C1.7) remains unverified.
