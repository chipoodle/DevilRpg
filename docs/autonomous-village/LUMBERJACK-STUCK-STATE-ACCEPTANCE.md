# Lumberjack Stuck-State Acceptance

## Result: **PARTIAL — root cause corrected, normal delivery verified, terminal delivery fallback not runtime-exercised.**

## Root cause

`VillagerLumberjackGoal.canContinueToUse()` had a delivery-only stuck branch:

```java
if (fase == Fase.ENTREGAR) {
    deliveryAttempts++;
    deliveryCooldown = Math.min(200, 40 * deliveryAttempts);
    target = null;
    return false;
}
```

This branch:

- never marked the warehouse support point failed;
- retried the same unreachable/full warehouse forever;
- left `fase = ENTREGAR` and the carried logs intact;
- produced the persistent "Llevando la madera" label while the villager stood
  or swayed, because the goal repeatedly stopped/restarted without a terminal
  transition.

The label is set from `ponerActividad(..., "Llevando la madera")` in the
`ENTREGAR` phase.

## Correction

Added a bounded delivery fallback:

```text
MAX_DELIVERY_ATTEMPTS = 6
```

After six failed delivery attempts:

1. drop carried logs as `ItemEntity` at the villager’s position (recoverable
   world items, not deletion);
2. clear `entregarTrasTalar`;
3. reset delivery attempts/cooldown;
4. set phase back to `TALAR`;
5. clear `target`;
6. `taskController.complete()`;
7. stop navigation;
8. log `[LENADOR-DELIVERY] entrega imposible ... carga dejada en el suelo`.

Status label now depends on actual inventory:

```java
case ENTREGAR -> troncosEnMano() > 0 ? "Llevando la madera" : "Volviendo al almacen";
```

## Normal runtime evidence — Run 93

```text
ID: stabilization-block1-scan-20261030-93
World: build/stabilization-block1-scan-20261030-93
Scenario: lumber-xp
Ticks: 5400
```

```text
[LENADOR-DELIVERY] carriedBefore=2 carriedAfter=0 accepted=2
                   warehouseBefore=121 warehouseAfter=123 delta=2
[LENADOR-DELIVERY] carriedBefore=2 carriedAfter=0 accepted=2
                   warehouseBefore=122 warehouseAfter=124 delta=2
[LENADOR-DELIVERY] carriedBefore=2 carriedAfter=0 accepted=2
                   warehouseBefore=122 warehouseAfter=124 delta=2
[LUMBER-XP] RESULT xp=1->9 gain=8 logs=40->34 removed=6
[A5] FIN result=FAIL detail=lumber xpGain=8 removed=6
```

The lumberjack harvested and delivered three times, clearing its carried load
each time and continuing to work. This verifies the normal path is not regressed.

The generic A5 result is FAIL because its XP/removed expectation differs; the
delivery transactions themselves are exact.

## What was not proven

The six-attempt fallback was not triggered in Run 93 because the warehouse was
reachable and had space. A blocked/full warehouse fixture is still required to
prove:

- the sixth failure actually fires;
- logs are dropped as recoverable items;
- the villager transitions to harvesting;
- the status label changes.

## Automated tests

None added. The required tests for delivery retry, invalid destination, load
conservation, and status are still missing.

## Files changed

```text
src/main/java/com/chipoodle/devilrpg/entity/goal/VillagerLumberjackGoal.java
```

## Validation

```text
compileJava = BUILD SUCCESSFUL
python tools/lint_aldea.py --strict = PASS
git diff --check = PASS
```

`compileTestJava` not rerun after the fallback addition.

## Remaining limitations

- No terminal-failure runtime fixture.
- No automated regression test.
- Dropping logs at the villager is a recoverable fallback, not a warehouse
  delivery; it should be followed by collector pickup.
- A5 generic FAIL in Run 93 is unrelated to the delivery path but prevents
  claiming an A5 PASS.

## Next required test

Create a disposable warehouse-full or warehouse-unreachable fixture and verify:

```text
6 failed attempts -> drop logs -> phase TALAR -> new harvest -> no permanent CARRYING
```
