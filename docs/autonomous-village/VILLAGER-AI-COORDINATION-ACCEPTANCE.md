# Villager AI Coordination — Acceptance

## Result: **Lumberjack oscillation root cause corrected and runtime-verified; broader profession tests remain open.**

## Actual architecture before the correction

- Lumberjack movement is issued through `VillageManager.caminarHacia(...)`, which
  writes Brain `WALK_TARGET` and `LOOK_TARGET`; vanilla `MoveToTargetSink` then
  calls `Navigation.moveTo`.
- `caminarHacia` already had a same-destination guard: if the Brain target and
  live path are unchanged, it does not rewrite the route.
- `VillagerLumberjackGoal.tick()` erased `WALK_TARGET` every tick whenever
  `taskController.ownsNavigation()` was true.
- That erase defeated the same-destination guard: every tick had no target, so
  `caminarHacia` rewrote it, vanilla recalculated the path, and the walker
  restarted. Visually this is the standing “dance”/sway.

The task controller was authoritative for the forestry assignment, but not for
Brain movement memory. The two movement writers were:

1. `VillagerLumberjackGoal` via `caminarHacia` (Brain target);
2. the same goal erasing that target every tick.

## Root cause

`VillagerLumberjackGoal`:

```java
if (taskController.ownsNavigation() || target == null) {
    villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
}
```

The `ownsNavigation()` half was wrong for a goal that walks through the Brain.
It forced a new destination/path on every tick.

## Correction

The erase now happens only when there is no target:

```java
if (target == null) {
    villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
}
```

While a task is active, `caminarHacia` keeps the same destination and live path,
and vanilla continues the existing route instead of restarting it.

## Runtime evidence — Run 94

```text
ID: stabilization-block1-scan-20261030-94
World: build/stabilization-block1-scan-20261030-94
Scenario: lumber-xp
Ticks: 5400
```

```text
[Rumbo] peticiones=12642 · ya iban andando (no se les toca)=6272
[Village] nueva tarea 53,-17 desde 107,19
[LENADOR-DELIVERY] carriedBefore=2 carriedAfter=0 accepted=2
                   warehouseBefore=122 warehouseAfter=124 delta=2
[Village] nueva tarea 53,-24
[LENADOR-DELIVERY] ... warehouseBefore=124 warehouseAfter=126 delta=2
[Village] nueva tarea 49,-17
[LUMBER-XP] RESULT xp=1->7 gain=6 logs=40->34 removed=6
[A5] FIN result=PASS detail=lumber xpGain=6 removed=6
```

The lumberjack:

- selected a tree;
- navigated;
- harvested;
- delivered exactly 2 logs;
- selected another tree;
- repeated three deliveries;
- did not remain in a carrying loop.

The `[Rumbo]` skip count (6,272 of 12,642 requests) is direct evidence that the
same route is now reused instead of being rebuilt every tick.

## What was not proven

- Farmer navigation under the same instrumentation.
- Guard/emergency preemption and resumption.
- Two-worker simultaneous movement.
- Other professions' `Navigation.stop()` paths.
- Automated ownership tests.

The fix is specific to the lumberjack's Brain-target erase. It does not claim a
complete movement arbiter for every profession.

## Production files changed

```text
src/main/java/com/chipoodle/devilrpg/entity/goal/VillagerLumberjackGoal.java
```

## Tests added

None. The required movement-ownership test for this exact regression is still
missing.

## Validation

```text
compileJava = BUILD SUCCESSFUL
compileTestJava = BUILD SUCCESSFUL
python tools/lint_aldea.py --strict = PASS
git diff --check = PASS
```

## Remaining limitations

- No automated test asserts that an active Brain target is not erased.
- No farmer/guard runtime scenario.
- `VillageNavigationGoal.stop()` still calls `Navigation.stop()`
  unconditionally; GoalSelector ordering makes this safe for its own task, but no
  cross-task test exists.
- `ForestryTaskController` remains per-goal, not a village-wide arbiter.

## Conclusion

The observed lumberjack “carrying wood, standing and swaying” behavior is
explained by the per-tick `WALK_TARGET` erase and is corrected. The runtime
acceptance passes for harvest, delivery, and resume. Broader multi-profession
coordination remains future work.
