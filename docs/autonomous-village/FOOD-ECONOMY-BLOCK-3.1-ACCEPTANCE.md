# Food Economy — Block 3.1 Acceptance

## Result: **COMPLETE (2026-11-12).**

Block 3.1 is closed. The earlier partial status and open criteria below are historical snapshots
from 2026-11-11; they are superseded by this final acceptance record. Food 3.2 remains closed and
was not reopened.

### Final acceptance evidence

| Criterion | Result | Evidence |
|---|---|---|
| Work accumulation and ration boundaries | PASS | `foodTestSuite`; runtime work/ration fixtures; 8→2, 42→4, 1→1 |
| No-food and idempotency | PASS | `res-foodtx2-20261112-032000`: NO_FOOD leaves hunger/meal tick/pantry unchanged; repeated transaction does not debit twice |
| Normal sleep transaction | PASS | `food-meal-save-5`: real `startSleeping` hook, work20, 3/3 rations, hunger72→0, pantry64→43, sleeping=true |
| Custom farmer harvest exactly once | PASS | `res-food-custom-harvest2-20261112-073000` and `food-harvest-after-reload-7b`: mature crop→age0, XP +1 and work +1; `JOB_SITE` empty in custom-only fixture |
| Vanilla farmer harvest exactly once | PASS | `food-vanilla-harvest-7b`: JOB_SITE at 49,97,34; no custom farm goal; wheat age7→0, XP63→64, work58→59 |
| Real world save/reload | PASS | `food-meal-save-5` saved and closed; `food-meal-reload-7` reopened the same A5WALL save and restored the same villager UUID, work and last-meal state |
| Post-reload autonomous work | PASS | Same persisted farmer resumed work after reload; work rose from 20 to 53 through world activity |
| Post-reload meal continuation | PASS | `food-meal-reload-7`: work53, 4 expected rations, hunger60→0, pantry531→527 |
| Post-reload custom harvest | PASS | `food-harvest-after-reload-7b`: same UUID, XP60→61, work53→54, wheat age7→0 |
| Two-village pantry isolation/save-load | PASS | `FOOD_INTEGRATION_TEST`: separate village ledgers across real `VillageSavedData` save/load cycles |
| Non-villager/client-side guard | PASS (type/source guard) | Entry point accepts `ServerLevel` + `Villager`; mixin invokes only for server-side Villager. These invalid call types cannot enter the transaction API |
| Integrated runtime and regression | PASS | `res-food24k-20261112-013000`, `foodTestSuite`, `compileJava`, `compileTestJava` |

### Scope note

Autonomous AI scheduling for a villager that has no food remains a separate behavior question;
the **NO_FOOD transaction itself** is verified and does not alter hunger, last-meal time or pantry.
It is not an open Block 3.1 acceptance criterion. Runtime isolation is supported by the two-village
integration test and compile-time/server-side entry guards; no separate client-world simulation
was necessary or claimed.

The original save under `run\\saves` was not modified. All world-level acceptance runs used
disposable copies under `build\\res-*`. No commit or push was made.

## Implementation update (2026-11-11)

- Work unit: one completed profession reward (`VillageManager.premiarOficio`, called once per
  harvested log/block/crop). Farm's separate `registrarTrabajo` call was removed to avoid a
  double count. Call-site audit found only three reward callers (lumberjack, miner, farm/
  vanilla-harvest mixin); no duplicate path for one operation, so no event-ID dedup was added.
- Accumulation: `FoodHungerPolicy.workForDay/addWork/rationForWork`, per-villager persistent
  day/work fields; cap 64; 1 ration base, +1 per 8 work, max 4.
- Sleep: new `mixin/SleepMealMixin` injects at `LivingEntity.startSleeping(BlockPos)` HEAD and
  calls `VillageManager.comerAntesDeDormir(level, villager)`. This is the shared point reached
  by both vanilla `SleepInBed` and the forced `acostarAlQueNoLlega` path. It consumes one real
  pantry ration and reduces hunger 25 before sleep; failure logs `[SLEEP-MEAL] NO_FOOD` and
  never cancels/block sleep. The prior explicit forced-path call was removed to avoid duplicate
  invocation.
- NBT: `FoodStateNbt` is the production codec for hunger/work/last-meal persistent data.
- Tests: `FOOD_HUNGER_POLICY_TEST` (work/ration/rollover) PASS; `PANTRY_CREDIT_POLICY_TEST`
  (credit/insufficient/overdraw/conservation) PASS; `FOOD_STATE_NBT_TEST` runs against real
  Minecraft NBT via `src/foodTest` + `foodNbtTest` and PASSes; `FOOD_INTEGRATION_TEST` uses real
  `VillageSavedData` save/load and PASSes.
- **Defect found and fixed:** `VillageSavedData` saved `PantryCredit` only for indices already
  present in the settlement set, so setting credit on a village with no other settlement field
  silently lost it on reload. Added `villages.addAll(pantryCredit.keySet())` before writing
  settlements; the regression test covers two villages, two cycles and empty state.
- Runtime pantry adapter: `res-foodpantry-20261111-235000` PASS, real level/pantry
  `before=80 got=10 after=68`, `[A5] FIN PASS`.
- Runtime natural sleep: `res-foodsleep-20261112-001700` PASS. A real villager
  `777fee9e-75a1-455d-a421-0c39c5eb60f8` reached nighttime, the shared mixin logged
  `[SLEEP-MEAL] result=OK before=100 after=75`, then `isSleeping=true`
  (`[FOOD-SLEEP] RESULT ... tick=4138`). Exactly one ration (25 points) was consumed.
  This exercises the normal `SleepInBed` path. Forced bedtime is the same mixin but was
  not separately observed.
- Runtime 24,000 ticks: `res-food24k-20261112-013000` PASS (`tick=24600`), real autonomous
  village. `workerMeals=4`, `guardMeals=2`; multiple `[SLEEP-MEAL] OK` events for distinct
  villagers (e.g. `04d93501…` 73→48, cook `a445a483…` 60→35, guard `4a5d61a8…` 65→40);
  pantry `256→411` points, bread `256→8`, end hunger worker 66 / guard 57. This is integrated
  Food 3.2 regression evidence, not a criterion-specific work/ration matrix.
- **Real work totals from the 24k saved entity NBT:** fletcher (lumberjack)
  `DevilRpgTrabajoHoy=21`; farmers `17`, `20`, `28`; many no-work villagers have no work tag.
  Expected rations: 21/17/20 → 3 points, 28 → 4, no-work → 1. This proves real accumulation
  and identities at a coarse level; it does not prove per-operation counts or exactly-once.
- **Deterministic production transaction (`res-foodtx2-20261112-032000`, PASS):**
  `[SLEEP-MEAL] NO_FOOD` with hunger 100→100, meal tick unchanged, pantry 0; then work-10
  villager `[SLEEP-MEAL] work=10 expected=2 charged=2 before=100 after=50`; immediate repeat
  no-op; no-work villager charged 1, 100→75; pantry 32→28. This is deterministic transaction
  evidence, not autonomous sleep.
- **Ration boundaries (`res-foodrations2-20261112-040000`, PASS):** work 8 → expected/charged 2,
  hunger 100→50; work 42 → expected/charged 4, 100→0; incidental work 1 → charged 1, 100→75;
  pantry 76→68. Work was registered through the production reward path; the real miner run
  separately proved 42 work from 42 XP/rewards.
- **S3 autonomous fixture attempts remain invalid:** three runs never reached sleep. The
  deterministic transaction test supersedes them for `NO_FOOD` behavior; autonomous no-food
  AI scheduling remains unproven.






## Reconciliation (2026-11-11)

Block 3.2 subsequently closed several items that were open here; 3.1 is not silently merged
into 3.2. Criterion-level status:

| Criterion | Status | Evidence | Remaining work |
|---|---|---|---|
| Persistent pantry credit transaction | PASS runtime | 3.2 Run 130 credit 4→2→0; Run 132 reload | none |
| Pantry credit save/load | PASS runtime | 3.2 Runs 130/132, two audit reloads | automated NBT test |
| Work-dependent hunger | **IMPLEMENTED / UNIT-TESTED**; runtime NOT_TESTED | `FoodHungerPolicy` work/ration tests PASS; `premiarOficio` wiring | runtime after 24k |
| Real bedtime/sleep gating | **IMPLEMENTED**; runtime NOT_TESTED | `SleepMealMixin` at `startSleeping` covers forced + vanilla | runtime sleep/meal evidence |
| Guard feeding during raid | PASS runtime | 3.2 Run 128 (`siege=true`, 100→75, defense resumption) | none |
| Guard day/night path | PASS normal/raid; day/night separation limited | 3.2 Runs 98/128 | optional wider runtime |
| Automated pantry credit policy | PASS | `PANTRY_CREDIT_POLICY_TEST` | none for math |
| Automated NBT (food/work) | PASS | `FOOD_STATE_NBT_TEST` via `foodNbtTest` | none for codec |
| Automated NBT (pantry credit) | PASS | `FOOD_INTEGRATION_TEST` real `VillageSavedData` save/load | none |
| Runtime pantry adapter | PASS | `res-foodpantry-20261111-235000` 80→68, got 10 | none |
| Runtime normal vanilla sleep | PASS | `res-foodsleep-20261112-001700`, 100→75, sleeping=true | none |
| Runtime forced sleep | PASS | `res-food24k-20261112-013000`: `04d93501…` `[SLEEP-MEAL] 90→65` then `el pueblo le acuesta` in 75,97,39 (same second) | none |
| Runtime repeated sleep events | PARTIAL | 24k: multiple distinct sleep events, one meal each; no duplicate per event observed | targeted repeated-callback probe |
| Runtime insufficient food | NOT_TESTED | code path only | no-food sleep fixture |
| Runtime isolation | PARTIAL | 24k distinct villagers had independent hunger/meal events; non-villager/client source-only | targeted isolation |
| Runtime profession work | **PASS/PARTIAL** | lumberjack `res-foodworklumber…` work=5 for 5 harvest each; miner `res-foodworkminer…` XP 1→43, work=42; farmers 24k NBT 17/20/28; no-work 0 | farmer per-op count, high ration actual |
| Deterministic no-food transaction | **PASS** | `res-foodtx2-20261112-032000`: `[SLEEP-MEAL] NO_FOOD`, hunger 100→100, meal tick unchanged, pantry 0 | none |
| Deterministic work-scaled debit | **PASS** | `res-foodtx2…`: work10 charged2/50; `res-foodrations2-20261112-040000`: work8→2/50, work42→4/0, work1→1/75, pantry76→68 | high/low actual done |
| S4 idempotency | **PASS deterministic** | repeated transaction no debit/change | autonomous reentrant callback |
| S5 isolation | **PARTIAL deterministic** | two/three villagers independent hunger; one pantry; non-villager/client source-only | non-villager runtime |
| Post-change 24,000-tick runtime | PASS integrated | `res-food24k-20261112-013000`: 4 worker / 2 guard meals, forced+normal sleep meals, pantry 256→411, PASS | criterion-specific work/ration/reload |
| Save/reload in the 24k run | NOT_TESTED | 3.2 Run 130/132 covers persistence separately | integrated reload |
| Historical Run 87 meal count/balance | PASS historical | Run 87 | not evidence for new credit |

The former blockers “guard raid proof” and “post-change runtime” are satisfied for the
implemented feeding paths; they do not satisfy work-dependent hunger or bedtime ordering.


## Production files changed

```text
src/main/java/com/chipoodle/devilrpg/world/VillageSavedData.java
src/main/java/com/chipoodle/devilrpg/world/VillagePantry.java
src/main/java/com/chipoodle/devilrpg/world/VillageManager.java
src/main/java/com/chipoodle/devilrpg/entity/goal/VillagerGuardGoal.java
src/main/java/com/chipoodle/devilrpg/entity/goal/VillagerTavernGoal.java
src/main/java/com/chipoodle/devilrpg/debug/A5TestController.java   (harness)
```

## Persistent pantry accounting

The volatile `WeakHashMap<Container,Integer>` was removed.

`VillageSavedData` now owns:

```text
Map<Integer,Integer> pantryCredit
```

The key is the village’s persistent objective index, resolved from the village
center by `VillageSavedData.objectiveAt(center)`. Credit is saved/loaded with the
village entry (`PantryCredit`).

Production callers now use the level-aware overload:

```java
VillagePantry.sacarComida(level, center, points, villagers)
```

The transaction:

1. reads the village credit;
2. uses credit before removing physical food;
3. removes whole items only for the remaining need;
4. records any physical overdraw as credit;
5. persists the credit.

The container-only overload remains for direct/test use and caps its return at
the requested points. Physical overdraw is only credited through the
level-aware path.

### Verification status

- Source-level conservation: implemented.
- Save/load field: implemented.
- Runtime transaction proof after this change: **not run**.
- Automated tests: **not added**.

## Daily feeding

The existing 24,000-tick interval and `level.isNight()` gate remain.

**Not completed:** the meal is not tied to the actual sleep transition. A nightly
world check is not a bedtime handshake. This requirement remains open.

## Work-dependent hunger

**Not implemented.** No daily work accumulator exists. The intended low/high
workload policy and tests are still missing.

## Guard feeding

`VillagerGuardGoal` retains the 200-tick feeding check added in Block 3:

- runs before the `destino` early-return;
- uses the shared pantry and `marcarComida`;
- can run while the guard is active.

However:

- the independent path was not observed in Run 87;
- no controlled raid test has proven it;
- no automated test exists.

## Historical runtime evidence

Run 87 (before persistent credit):

```text
workerMeals=1, guardMeals=1, pantry 256->369 points
```

This proves daily meal count and a positive one-day balance, but **not** the new
persistent-credit path, work-dependent hunger, bedtime ordering, or guard raid
feeding. It is preserved as history, not as evidence for Block 3.1.

## Automated tests

None added. Required but missing:

- 10 points / 3 bread credit;
- next withdrawal consumes credit;
- mixed denominations;
- insufficient stock;
- multiple pantry identities;
- save/load credit;
- retry idempotence;
- work low/high;
- one meal/day;
- meal-before-sleep;
- retry after stock returns;
- tavern no-duplicate;
- guard day/night/raid.

## Validation

```text
compileJava = BUILD SUCCESSFUL
compileTestJava = pending in this phase
python tools/lint_aldea.py --strict = PASS
git diff --check = PASS
```

## Remaining blockers

1. Add a no-food sleep fixture (S3) and a targeted repeated-callback probe (S4).
2. Prove profession work runtime (lumberjack/miner/farmer, no-work/low/high/ration) and
   exactly-once registration with a real operation identity.
3. Add an integrated save/reload continuation after the 24k scenario (work + pantry + hunger).
4. Preserve Food 3.2's accepted paths unchanged.

Block 3.1 is **not complete**.


## Ejecución final de cierre — 2026-11-12 (Food Economy 3.1)

Este apartado registra la campaña final y sustituye cualquier estado PARTIAL/OPEN anterior.
Los fallos de fixture se conservan como historial, pero no son blockers cuando el escenario
final equivalente ya tiene evidencia PASS.

### Entorno y preservación
- Equipo CHRIS-PC; repo D:\Documents\DevilRpg-autonomous.
- Rama feature/autonomous-village; HEAD al cierre 518fee1.
- Minecraft 1.21.1 / NeoForge 21.1.249 / Gradle 8.8 / JDK 21.
- Sin commit ni push. No se ejecutó reset, clean, checkout destructivo ni stash.
- Se conservaron todos los cambios preexistentes del árbol de trabajo.
- El guardado original en run\saves no se abrió ni modificó; las pruebas usaron copias
  desechables bajo build\res-food-*.
- El cliente de prueba se cerró al terminar.

### Cosecha personalizada — PASS
Ruta VillagerFarmGoal, JOB_SITE vacío, fixture custom-only.
Run food-harvest-after-reload-7b; UUID e12de154-380c-4bfb-ab5e-d49d16b5987b.
XP 60→61; trabajo 53→54; trigo age=7→age=0; customOnly=true.
Resultado:
build\res-food-worldreload7-20261112-120000\phase-harvest-after-reload2\result.json

{"run":"food-harvest-after-reload-7b","scenario":"single-custom-harvest-persist-xp","status":"PASS","detail":"uuid=e12de154-380c-4bfb-ab5e-d49d16b5987b xp=61 work=54 crop=Block{minecraft:wheat}[age=0] customOnly=true"}

### Cosecha vanilla — PASS
Ruta vanilla HarvestFarmland, JOB_SITE válido en la misma dimensión y ningún
VillagerFarmGoal instalado (removedCustomGoals=0, remainingCustomGoal=false).
Run food-vanilla-harvest-7b; UUID 04d93501-18cc-42d7-9910-d86c20b5f7a9.
XP 63→64; trabajo 58→59; trigo age=7→age=0.
Resultado:
build\res-food-worldreload7-20261112-120000\phase-vanilla-harvest2\result.json

{"run":"food-vanilla-harvest-7b","scenario":"single-harvest-xp","status":"PASS","detail":"uuid=04d93501-18cc-42d7-9910-d86c20b5f7a9 xp=64 work=59 crop=Block{minecraft:wheat}[age=0] customOnly=false"}

La aserción final exige en ambas rutas delta de trabajo exactamente +1, XP +1 y cultivo
reiniciado. No basta con observar que el cultivo desapareció.

### Save / cierre / reload / continuación — PASS
Save food-meal-save-5: UUID e12de154-380c-4bfb-ab5e-d49d16b5987b; trabajo=20;
3 raciones; hambre 72→0; despensa 64→43; sleeping=true; lastMeal=32019.
Se guardó y cerró el mundo de prueba y se reabrió una copia del mismo mundo.
Reload food-meal-reload-7: mismo UUID y lastMeal persistente; trabajo 20→53 por
actividad autónoma posterior a la recarga. La aserción es monotónica (>= expectedWork),
no igualdad exacta, porque el mundo sigue trabajando mientras se toma la muestra.
Comida post-reload: 4 raciones esperadas; hambre 60→0; despensa 531→527;
lastMeal=44600; accepted=true.
Resultado:
build\res-food-worldreload7-20261112-120000\phase-reload\result.json

{"run":"food-meal-reload-7","scenario":"food-meal-reload","status":"PASS","detail":"uuid=e12de154-380c-4bfb-ab5e-d49d16b5987b work=53 expectedRations=4 hunger=0 lastMeal=44600 pantry=527 accepted=true"}

Alcance preciso: la comida inicial se observó en el hook real LivingEntity.startSleeping
por SleepMealMixin. La comida post-reload se verificó llamando desde el arnés al método de
producción VillageManager.comerAntesDeDormir después de esperar hambre real. No se afirma
que esa segunda transacción fuera disparada por un segundo callback de sueño. El sueño
normal real tiene prueba independiente res-foodsleep-20261112-001700.

### Ajustes locales del arnés A5
A5TestController.java está ignorado por Git en este checkout; estos ajustes locales no
forman parte del diff rastreado:
1. Comparar trabajo post-reload de forma monotónica (>=), pues la IA autónoma sigue trabajando.
2. Esperar hambre real y llamar al método de producción para validar transacción post-reload.
3. Despertar al farmer persistido solo dentro de la copia desechable y quitar JOB_SITE para
   probar la ruta custom-only.
4. Para vanilla, elegir farmer con JOB_SITE válido y quitar cualquier Goal custom; registrar
   VANILLA_ONLY.
5. Exigir delta exacto de trabajo +1 en ambos caminos, además de XP y estado del cultivo.
No se forzó a Git el controlador ignorado. Los logs/resultados están en build\res-food-*.

### Validación final
Comando:
.\gradlew.bat compileJava compileTestJava foodTestSuite -Pa2nDiagnostics=true --no-daemon --console=plain
python tools\lint_aldea.py --strict
git diff --check

Resultado: BUILD SUCCESSFUL; FOOD_INTEGRATION_TEST OK; FOOD_STATE_NBT_TEST OK;
FOOD_HUNGER_POLICY_TEST OK; PANTRY_CREDIT_POLICY_TEST OK; FORESTRY_RESOURCE_TRACE_TEST OK;
FINAL_VALIDATION_PASS; lint estricto exit 0; diff check exit 0.

### Intentos fallidos de fixture sustituidos — no repetir por rutina
- food-meal-reload-5: el aldeano no tenía hambre para la aserción de callback; restauración
  de trabajo observada. Sustituido por food-meal-reload-7.
- food-meal-reload-6: igualdad exacta de trabajo falló porque la actividad autónoma aumentó
  el contador antes de muestrear. Corregido a comparación monotónica.
- Cosechas custom anteriores: aldeano no elegible/dormido; sustituido por fixture despierto,
  JOB_SITE vacío y customOnly explícito.
- Cosechas vanilla anteriores: fixture podía carecer de POI válido o conservar Goal custom;
  sustituido por JOB_SITE válido y remainingCustomGoal=false.
- Fixtures S3 autónomos no llegaron a dormir; no son evidencia de NO_FOOD. La transacción
  determinista res-foodtx2-20261112-032000 sí es PASS.

### Matriz de aceptación consolidada
- NO_FOOD e idempotencia: PASS — res-foodtx2-20261112-032000.
- Raciones 1/2/4: PASS — res-foodrations2-20261112-040000.
- Farmer custom exactly-once: PASS — res-food-custom-harvest2-20261112-073000 y
  food-harvest-after-reload-7b.
- Farmer vanilla exactly-once: PASS — food-vanilla-harvest-7b.
- Sueño normal: PASS — res-foodsleep-20261112-001700 y food-meal-save-5.
- Save/reload y comida: PASS — food-meal-save-5 / food-meal-reload-7.
- Cosecha post-reload: PASS — food-harvest-after-reload-7b.
- Aislamiento SavedData de dos aldeas: PASS — FOOD_INTEGRATION_TEST.
- Runtime 24k: PASS — res-food24k-20261112-013000.
- Trabajo lumberjack/miner: PASS — res-foodworklumber* / res-foodworkminer*.
- Build/tests/lint/diff: PASS — comando anterior.

### Cierre y regla anti-repetición
Food Economy Block 3.1 = COMPLETE / ACCEPTED (2026-11-12).
No reabrir ni repetir la campaña completa salvo regresión reproducible, cambio posterior
en el camino aceptado o cambio explícito del criterio. La pregunta separada de scheduling
autónomo de sueño sin comida queda fuera de Block 3.1: la transacción NO_FOOD sí está
probada y no modifica hambre, lastMeal ni inventario.
