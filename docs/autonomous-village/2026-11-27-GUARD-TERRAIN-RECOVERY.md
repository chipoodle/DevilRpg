# Guard terrain recovery — implementation handoff (2026-11-27)

## Git baseline and implementation

- Repository: `chipoodle/DevilRpg`
- Branch: `feature/autonomous-village`
- Base remote HEAD inspected before edits: `9271e6706fe4e70dc61e06f70ebb3f76ea9f4399`
- Implementation HEAD: `13ea721ba8f246a6be2b04664b473138c51e8512`
- Parent of implementation series: `9271e6706fe4e70dc61e06f70ebb3f76ea9f4399`
- The branch advanced only through this implementation series; GitHub compare reports 4 commits ahead, 0 behind.
- No local build machine was reachable. Desktop Commander reported CHRIS-PC offline. **Compilation, tests, lint and Minecraft runtime are NOT RUN.**

## Problem

A guard can become stranded in a water depression below the surrounding terrain. The existing guard goal tracks lack of progress and eventually skips the patrol point, but it has no terrain-building recovery.

## Source changes

1. Added `src/main/java/com/chipoodle/devilrpg/entity/goal/GuardTerrainRecovery.java`.
   - Starts only after at least 60 stuck ticks and only when the Brain still points at the strategic guard destination.
   - Searches four cardinal directions, aligned with the strategic destination.
   - Accepts only a natural dry landing one or two blocks higher, with a real colliding support block and two clear body cells.
   - Builds a short staircase as supported cobblestone columns. Each column is filled from its natural floor upward; no unsupported/floating blocks are placed.
   - Replaces only air/water cells; rejects block entities, lava, solid obstructions and protected village cells.
   - Searches at most four blocks down for real support; caps rise at two blocks, construction at eight blocks, active recovery at 400 ticks, and placement cadence at one block per ten ticks.
   - Places incrementally, verifies each resulting block, then requests the temporary landing through `VillageManager.caminarHacia`.
   - Does not call `Navigation.moveTo` or create a second movement authority.
   - Emits `[GuardTerrain]` start/completion INFO traces and per-block DEBUG traces.
2. Integrated the recovery into `VillagerGuardGoal.tick()`.
   - It runs after combat/equipment/march decisions, so it cannot preempt those priorities.
   - The strategic patrol destination is not overwritten.
   - Normal guard behavior resumes after reaching the landing.

## Known scope and limitations

- This first increment builds a short supported ramp; it does not yet mine dirt. It intentionally fails closed when no dry, naturally supported landing is found within the bounded search.
- It is not yet runtime-accepted. The screenshot scenario must be reproduced in a disposable test world.
- The recovery state is transient in the goal instance. Already placed blocks remain in the world if the entity unloads; interrupted recovery must be checked during runtime validation.
- Do not infer success merely from the presence of the new class or log start event.

## Harness validation required

1. Preserve local-only work and diagnostic worlds. Fetch and fast-forward only; stop on divergence or dirty-tree overwrite risk.
2. Confirm exact tested SHA is `13ea721ba8f246a6be2b04664b473138c51e8512`.
3. Run the repository's full static gate: `clean compileJava compileTestJava test`, strict village lint, and `git diff --check`. Record commands, exit codes and logs.
4. In a disposable NeoForge 1.21.1 world, create a guard at the bottom of a water depression with:
   - floor two blocks below the surrounding bank;
   - a dry supported landing two blocks higher and three blocks forward;
   - the guard assigned a patrol destination beyond that bank.
5. Capture before/after coordinates, all changed block positions/states, Brain WALK_TARGET, route status, and `[GuardTerrain]` logs.
6. Acceptance: after the 60-tick no-progress trigger, the guard builds only supported cobblestone, climbs to the dry landing, resumes the original patrol destination, and does not teleport.
7. Negative controls:
   - A natural reachable route must be used without building.
   - A protected village cell must never be modified.
   - No placement into lava, solid blocks, block entities, or unsupported columns.
   - Combat, equipment trip and active march must retain priority.
   - A missing valid landing must fail closed and back off rather than retry every tick.
8. Record FAIL/BLOCKED outcomes and first failing transition. Harness must not edit production source.

## Status

**IMPLEMENTED — VALIDATION PENDING.** No compile, automated test, strict lint, or runtime PASS is claimed.
