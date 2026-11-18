# A2 Phase 39 — planner watchdog and deterministic DROP fixture (2026-11-18)

## 1. Result

**COMPLETE.** The Phase 38 watchdog is a real single-tick CPU stall in the synchronous global A*
expansion, not a 700-day clock jump and not a probe frame. The log reports **60.00 seconds**; the crash
report's `60000128.00 seconds` is the millisecond value formatted as seconds. The planner performs
8,000 expansions × 56 candidate offsets = 448,000 edge validations in one `tryPlan()` call, with
multiple bounded `BlockCollisions` queries per edge. No per-tick yielding exists.

A deterministic DROP fixture is viable: a small prepared platform/landing that forces the normal local
DROP edge, with the real entity, Navigation, MoveControl, goals and executor. Global planning is avoided
because the objective is adjacent and reachable, not because a path is injected.

- Branch: `feature/autonomous-village`
- HEAD: `d3299d7cc6455c4c028a751737d3623f7a67aa0a`
- Production: `6aec35c651810084691f99611c48652fe0a881d7`
- No production/test/runtime changes in this phase.

## 2. Crash reconstruction

```text
11:07:37 Server Watchdog: A single server tick took 60.00 seconds
stack:
  BlockCollisions.computeNext
  NavObstruction.fits:98
  NavObstruction.classify:207
  NavPlanner.validarArista:115
  NavPlanner.planGlobalInternal:496
  NavExecutor.rutaGlobalHacia:1456
  ...
  NavExecutor.tryPlan:3302
```

Immediately after the watchdog, the server thread continued and logged:

```text
global exactGoal=false inicio=756,63,533 meta=661,73,533 radio=112 V=3
exp=8000/8000 presupuesto=true aristas=448000 encolados=181486
```

That is the full expansion budget in one plan. The watchdog stack is the point reached when the
watchdog sampled the thread, not proof of an infinite loop inside `BlockCollisions`.

## 3. AABB / collision audit

`NavObstruction` uses fixed synthetic boxes:

```java
cellBox  = [x+0.15, y,       z+0.15] -> [x+0.85, y+1.0, z+0.85]  // 0.7 x 1.0
upperBox = [x+0.15, y+1.0,   z+0.15] -> [x+0.85, y+2.0, z+0.85]  // 0.7 x 1.0
```

`fits()` calls `getBlockCollisions(mob, cellBox)` and `getBlockCollisions(mob, upperBox)` and only
tests `iterator().hasNext()`. The boxes are bounded, finite and independent of the entity AABB. No
NaN/overflow or entity-sized unbounded region is present in `fits`.

`classify()` calls `fits`, `hasSupport`/`blockedAtFeet`, `headClearOneUp`, and additional `fits` for
STEP_UP/JUMP_UP. A single edge therefore performs several collision/shape queries, not one.

`BlockCollisions.computeNext` iterates a bounded cursor over those 1-block-high boxes; no loop whose
bound depends on the mob AABB exists in the mod code. The observable failure is aggregate cost, not a
single pathological AABB.

## 4. Complexity

```text
maxExpansions = 8000
offsets       = 8 horizontal x 7 vertical = 56
edges         = 8000 x 56 = 448,000  (matches log)
```

Each edge:

- `classify()` -> at least 2 `getBlockCollisions`;
- `hasSupport` -> blockState + collision shape;
- `blockedAtFeet` / `headClearOneUp` -> another collision query for blocked cells;
- `fits` for STEP_UP/JUMP_UP -> more collision queries;
- `piesResultantes` / `esConstruible` add shape checks.

So ~0.9–2 million collision/shape queries are possible before the single `tryPlan()` returns. The search
is synchronous: `NavGraphSearch.search()` does not yield between expansions. At ~67 µs/edge this already
reaches 30 seconds; chunk/leaf collision costs can push it past 60 seconds.

**Confirmed:** a single global plan can consume the full 8,000-expansion budget in one tick.
**Not confirmed:** a defect inside `BlockCollisions` itself; no evidence of a non-terminating cursor.

The Phase 38 probe is not causal: its stack contains no probe frame, it only wrote during
`DROP_DESCENT_COMMAND`/`travel`, and no probe artifact was created. The planner stalled before any DROP.

## 5. Deterministic fixture design

### Geometry (IDX4-like)

```text
spawn:   566.5, 122.0, 533.5   (feet 566,122,533)
platform: 3x3 spruce_leaves at y121 under/around spawn
landing: 567,119,534
support: spruce_leaves at 567,118,534
clear:   567,119..122,534 and 566..568,119..122,534 (air)
neighbors: open air; no walls above y122
```

### Geometry (IDX8-like)

```text
spawn:   747.69, 64.0, 534.6
platform: cobblestone at y63 around 747,534
landing: 746,63,534
support: ice at 746,62,534
clear:   746,63..64,534
```

The fixture runs one attacker at a time, with the real `AggressiveZombieEntity` and its normal AI goals.
Before the observation window it prepares the blocks and spawns/positions the mob. It then sets the
production objective to the landing so the normal planner chooses a local DROP edge.

It does **not**:

- inject a `NavRoute` or a Navigation `Path`;
- call `setWantedPosition`/`JumpControl`;
- replace MoveControl/Navigation;
- disable collision or goals;
- teleport during the 40-tick window;
- change predicates, budgets or tolerances.

The only prepared state is terrain + initial spawn/objective, before the window. If the local planner
cannot reach the DROP without a global plan, the fixture is considered invalid rather than forcing it.

### Why it avoids the watchdog

The objective is one cell below and one cell adjacent; local candidate validation finds a DROP edge
almost immediately. The global planner does not need the full 8,000-expansion envelope. The fixture
must log the plan budget/expansions and abort if `exp` approaches the hard budget.

## 6. Fixture equivalence and risks

Equivalent: same entity class/dimensions, same `NavExecutor`, same DROP transition, same
`Navigation.moveTo`, same `MoveControl`, same `travel`, same collision code, same support/fit predicates,
same AI goals (kept enabled).

Different: simplified terrain, no nearby village/raiders, potentially different chunk cache warmth and
neighbor block states. These can change planning cost but not the DROP executor path being diagnosed.
The fixture must copy the exact BlockStates from the real edge where known; unknown states are a stated
limitation.

## 7. Validation plan (not executed)

1. New disposable world; original A2/C4 untouched.
2. Build platform, spawn one real attacker, set objective.
3. Run headless until first `DROP_DESCENT_COMMAND`.
4. Assert no watchdog and `exp < 8000`.
5. Start the 40-tick probe (Phase 38 instrumentation) at that command.
6. Verify `DROP_DESCENT_COMMAND`/`STARTED`/`STALLED` and per-tick travel/MoveControl/path/AABB.
7. Stop at 40 ticks, first physical descent, or landing.
8. Repeat for IDX8 geometry.
9. If no command within 200 ticks, mark fixture invalid; do not teleport or force a path.
10. Preserve logs; no production commit; instrumentation reverted.

This validation requires a new authorized runtime and was **not** executed in Phase 39.

## 8. Recommendation for Phase 40

Implement the fixture as a separate diagnostic mode (e.g. `A2DropFixture`) using the real entity and
normal goals, with the two geometries above. Do not alter the planner, budgets, DROP predicates or
Navigation. Instrument only observation. If the fixture reaches the first DROP without global-plan
watchdog, resume Stage B for 40 ticks per attacker.

## 9. Files inspected

- `NavObstruction.java` (`fits:98`, `classify:207`, `cellBox:63`, `upperBox:81`)
- `NavPlanner.java` (`validarArista:115`, `planGlobalInternal:496`, expansion loop ~370)
- `NavGraphSearch.java` (`search:108`)
- `NavExecutor.java` (`rutaGlobalHacia:1456`, `tryPlan:3302`)
- `A2NavHarness.java` deterministic fixture pattern
- Phase 38 crash report and server log

## 10. Integrity

```text
PRODUCTION_MODIFIED: false
TESTS_MODIFIED: false
ORIGINAL_WORLDS_MODIFIED: false
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

## 11. Status

A2 remains OPEN. The watchdog is explained as an unbounded-per-tick global A* cost; the exact threshold
is environment-dependent. The DROP physical cause is still unresolved and requires the fixture + Stage B.
No planner limit/predicate change is recommended in this phase.
