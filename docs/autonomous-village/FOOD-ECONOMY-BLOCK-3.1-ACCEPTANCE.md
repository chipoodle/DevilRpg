# Food Economy — Block 3.1 Acceptance

## Result: **PARTIAL — persistent pantry credit implemented; work-dependent hunger, true bedtime gating, guard raid proof, automated tests, and post-change runtime remain open.**

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

1. Add automated pantry credit tests and run them.
2. Add save/reload test for `PantryCredit`.
3. Implement a per-villager daily work accumulator and work-to-hunger policy.
4. Gate the ordinary meal on the real sleep transition.
5. Prove the guard-specific path during a raid.
6. Rerun the 24,000-tick scenario after these changes.

Block 3.1 is **not complete**.
