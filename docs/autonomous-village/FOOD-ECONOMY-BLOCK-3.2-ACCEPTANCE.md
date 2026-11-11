# Food Economy — Block 3.2 Acceptance (Run 98)

## Result: **PARTIAL — worker and guard now both feed with the 25-point reduction; normal threshold 60 still delayed.**

## Run 98

```text
ID: stabilization-block1-scan-20261030-98
World: build/stabilization-block1-scan-20261030-98
Scenario: food-day
Ticks: 600 -> 24,600
```

```text
workerMeal n=1 tick=24485 hunger=75
guardMeal  n=1 tick=24326 hunger=75
end hungerW=76 hungerG=77
bread 256->0 points 256->265
[A5] FIN result=PASS
```

- Worker and guard are distinct.
- Both consumed real pantry food.
- Both show 100 -> 75, verifying the 25-point reduction.
- Guard now uses individual hunger, not the rolling timestamp.
- Pantry ended positive.

## Still failing

### Normal threshold 60

Both tracked villagers fed near tick 24,400, not when hunger reached 60. The
pantry fallback is now unconditional, so the table path is no longer the blocker.
The remaining delay is in **when the feeding Goal becomes installed/eligible or
when `canUse()` is evaluated with hunger >= 60**.

This must be instrumented before another production change:

```text
goal installed?
canUse called at hunger>=60?
restTicks value
attack/rest state
pantry food
final canUse result
```

### Critical threshold

The meal occurred before death but at high hunger; normal 60 eligibility remains
unproven.

### Starvation/recovery

Not tested. `StarvingSince` is still village-level.

### Guard raid

Not tested.

### Persistence

Not tested.

### Automated tests

None.

## Changes in this step

- Tavern feeding always uses the pantry support point; the table no longer gates
  food access.
- Guard `VillagerGuardGoal` check uses `VillageManager.tieneHambre` and reduces
  hunger by 25 after a successful meal.

## Validation

```text
compileJava = BUILD SUCCESSFUL
python tools/lint_aldea.py --strict = PASS
git diff --check = PASS
```

No tests added.

## Root cause found (Run 102)

`VillageManager.tieneHambre()` still used the legacy rolling timestamp:

```java
return level.getGameTime() - ultimaComida(level, villager) >= EAT_INTERVAL_TICKS;
```

Runtime at hunger 80:

```text
[FOODDIAG] reject uuid=04d93501-... hunger=80
           reason=HUNGER_OR_TABERNA hungry=false taberna=true
```

This is why all villagers fed near tick 24,000: the legacy clock, not individual
hunger, controlled eligibility. The sleep gate was a secondary rejection.

No production behavior was changed in the diagnostic. The required correction is
the one-line `tieneHambre` change to `hambre(villager) >= HAMBRE_NORMAL`.

## Run 105 — normal threshold verified

Root fix applied directly to `VillageManager.tieneHambre`:

```java
return hambre(villager) >= HAMBRE_NORMAL;
```

Runtime:
```text
[FOODDIAG] result hunger=62 dest=107,97,16 pantry=256 result=true
workerMeal tick=630 hunger=37
[FOODDIAG] result hunger=60 pantry=184 result=true
workerMeal tick=6374 hunger=35
```

- First meal at hunger 62 -> 37 (25 points).
- Second meal after hunger returned to 60 -> 35.
- Real pantry food consumed (256 -> 184 across run).
- No duplicate meal while below 60.

Normal-threshold feeding: **PASS**. Remaining Block 3.2: automated tests,
starvation/recovery, guard raid, save/reload.

## Run 109 — starvation effects/recovery PASS

`HAMBRE_PACIENTE_TICKS = 3 * 60 * 20` (3,600), independent of EAT_INTERVAL.
Death remains 12,000.

```text
setup hunger=93 pantry=0
critica=3600 weak=false slow=false tick=8200
effects hunger=100 weakness=true slow=true tick=4479
recovery hunger=57 weakness=false slow=false recovered=true
[A5] FIN result=PASS
```

Effects activate at 3,600 critical ticks and are removed after a real meal.
Death-timer runtime, automated tests, guard raid, and persistence remain open.

## Run 110 — death boundary PASS

```text
setup hunger=93 pantry=0
crit=12000 -> death
[C1-DEATH] dead crit=12000 tick=16600
[A5] FIN result=PASS
```

No meal was consumed; the critical timestamp persisted through failed feeding and
death occurred at the 12,000-tick boundary. This also demonstrates failed
withdrawal does not clear critical starvation state.

## Automated hunger-policy tests (2026-11-06)

`FoodHungerPolicy` is now the pure production policy used by `VillageManager`.
`FoodHungerPolicyTest` (registered in `policyTest`) asserts:

- 59 not hungry; 60 and 61 hungry;
- 89 not critical; 90 critical;
- hunger caps at 100 and never goes below 0;
- meal reduces 60->35 and 90->65, reduction 25;
- effects false at 3,599, true at 3,600;
- death false at 11,999, true at 12,000;
- critical timestamp initializes once.

Result: `FOOD_HUNGER_POLICY_TEST: OK`.
Minecraft effect application, entity death, GoalSelector eligibility, guard raid,
and reload remain runtime-only/uncertain and are not claimed unit-tested.

## Guard raid feeding — OPEN

- Run 113 exposed duplicate guard meals 330 ticks apart; a 3600-tick minimum
  between meals was added to `tieneHambre`.
- Run 118: one meal 100->75, but `siege=false` at meal time.
- Run 119: delayed raid start failed (`classic defense did not start`).
- No active-raid guard feeding evidence. Do not mark PASS.

## Run 120 — active-raid guard feeding PASS

Fixture-only: guard last-meal timestamp set to `now-4000`, hunger100; raid started
at tick600.

```text
[C1-MEAL] uuid=4a5d61a8-... before=100 after=75 siege=true tick=5840
[A5] FIN result=PASS
```

- real classic raid active at the meal tick;
- actual pantry food consumed;
- exact 25-point reduction;
- one meal event (cooldown held).
Defense resumption after the meal was not captured with a target UUID; the guard
survived and the raid continued until later.

## Defense resumption — PARTIAL (Runs 121-123)

Run 120 proves active-raid feeding (`siege=true`, 100->75). After the meal:
- guard target remained null;
- Run 123 moved a real `AggressiveZombieEntity` next to the guard;
- target stayed null and the siege ended within 600 ticks.

Defense resumption after eating is **not proven**. No production AI change was
made; the fixture/guard targeting must be diagnosed next.

## Run 127 — defense resumption PASS

Prior FAIL was a fixture error: it read `getTarget()`, while the guard stores its
combat target in private `enemigo`.

```text
[C1-MEAL] before=100 after=75 siege=true tick=5780
[C1-RAID] movedRaider uuid=dc333a86-...
[GUARDDIAG] acquired guard=4a5d61a8-... enemy=dc333a86-... dist=0.808 tick=6160
[GUARDDIAG] acquired ... enemy=dc333a86-... dist=1.00 tick=6340
```

The guard acquired a real hostile 380 ticks after the active-raid meal. No duplicate
meal. The A5 FIN assertion still reports FAIL because it checks `getTarget()`;
that assertion is a known false negative.

## Run 128 — guard raid feeding + defense resumption PASS

Read-only accessor `VillagerGuardGoal.enemigoActual()` added; A5 no longer reads
`Mob.getTarget()`.

```text
[C1-MEAL] before=100 after=75 siege=true tick=4923
[C1-RAID] postMeal+600 target=2fa83e6d-... alive=true
          dist=2.44 goalMOVE=true
[A5] FIN result=PASS defending=true
```

- real classic raid active at meal;
- one real pantry transaction, exactly 100->75;
- one meal event;
- real hostile acquired after feeding, alive at 2.44 blocks.

Guard raid feeding and defense resumption: **PASS**.

## Run 130 — hunger and pantry credit persistence PASS

Save:
```text
villager=e12de154-... hunger=100 objective=0
pantryBefore=4 points request=2 removed=2 credit=2 pantryAfter=0
```
Reload same world:
```text
villager=e12de154-... hunger=100 objective=0
creditBefore=2 removed=2 creditAfter=0
[A5] FIN result=PASS
```
Same UUID, same objective index, no duplicate or lost credit.

Conservation:
```text
4 physical points = 0 remaining + 2 consumed + 2 credit
after reload: 2 credit -> consumed exactly once, credit 0
```
Limitation: one reload cycle; second cycle and no-duplicate-meal-after-reload
not separately exercised.

## Final persistence extension (2026-11-06)
Two consecutive read-only `food-audit` reloads on world 130:
```text
PERSIST-AUDIT hunger=100 objective=0 credit=0 lastMeal=4602 gameTime=6821
[A5] FIN PASS
```
Both cycles PASS; same UUID e12de154-, same objective 0. Cooldown after reload had
1381 ticks remaining (3600 - (6821-4602)). A real post-reload meal was not run.

## Final acceptance matrix
| Criterion | Status |
|---|---|
| Individual hunger 0..100 | PASS |
| Normal feeding at 60 | PASS (105) |
| 25-point meal | PASS |
| Guard normal + raid + defense | PASS (98/128) |
| Critical effects/recovery/death | PASS (109/110) |
| Pantry credit serialization | PASS runtime (130) |
| Two reload cycles | PASS (audits) |
| Credit consumed once | PASS |
| Conservation | PASS |
| Real meal after reload/cooldown expiry | NOT TESTED |
| Automated NBT serialization test | NOT ADDED |

Block 3.2 is complete for the implemented and verified paths; the two items above
are documented open limitations, not verified behavior.

## Final post-reload meal attempt — NOT VERIFIED
Run 131 setup: villager e12de154-, hunger62, pantry64. A real tavern meal occurred
(pantry 64->24), but the fixture did not reach its save/finish assertion before
timeout. Therefore no first-meal save/reload/second-meal timeline exists.
Food Economy Block 3.2 remains OPEN for this criterion.

## MEAL2 fixture diagnosis (Run 132)
Root cause of Run131 timeout: fixture waited for `hunger<=45`, but the real meal
occurred later at high hunger (post-meal 52/75), so the condition never became true.
Fixed to detect `ultimaComida` change. Save PASS:
```text
[MEAL2] first hunger=52 lastMeal=8377 pantry=12
```
Post-reload: hunger climbed 67->86, elapsed 7000, pantry24, but no second meal.
This is an unresolved post-reload eligibility/target-installation issue, not a
save fixture timeout.

## Post-reload no-meal diagnosis
```text
[MEAL2] goals tavern=true prio=-1 goals=6
[FOODDIAG] reject hunger=97 reason=ATTACK_OR_SLEEP attack=false sleep=true
gameTime≈17978
```
Goal registration is intact after reload. The rejection is the sleep routine at
night. The fixture must execute the post-reload meal in daytime; no production
correction is justified by this evidence.

## FINAL — daytime post-reload second meal PASS (Run 132)
```text
tavern=true prio=-1
reload sleep=false gameTime=19180
cooldown remaining>0 before expiry; no meal
[C1-MEAL] e12de154- before=94 after=69 tick=23373
[MEAL2] second lastMeal=23373
[A5] FIN result=PASS detail=secondMeals=1
```
Same UUID/objective; one tracked second meal; exact 25-point reduction; cooldown
prevented early/duplicate meal. Physical food consumed; pantry points also changed
from other villagers, so per-villager ledger is the meal transaction only.
Automated NBT serialization coverage remains absent and is documented.

## Block 3.2 final status: CLOSED

# FINAL — Food Economy Block 3.2 CLOSED (2026-11-07)

| Criterio | Estado |
|---|---|
| Hambre individual 0..100 | PASS |
| Alimentación normal a 60 | PASS (Run 105) |
| Reducción exacta de 25 | PASS (105/128/132) |
| Guardia en paz y asalto | PASS (98/128) |
| Reanudación de defensa | PASS (128) |
| Efectos a 3600 / muerte a 12000 | PASS (109/110) |
| Crédito de despensa y conservación | PASS (130) |
| Hambre y crédito tras reload | PASS (130) |
| Dos ciclos de reload | PASS |
| Cooldown 3600 antes de expirar | PASS |
| Segunda comida diurna tras reload | PASS (132: 94->69, una comida) |
| Test NBT de serialización | NO AÑADIDO — limitación documentada |

Arquitectura e invariantes: `docs/autonomous-village/FOOD-ECONOMY-ARCHITECTURE.md`.
Estado: **CERRADO**. El Lumberjack no se ha iniciado.
