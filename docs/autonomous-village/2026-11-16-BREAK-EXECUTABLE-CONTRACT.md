# BREAK Executable Contract — Implementation (2026-11-16)

## Result

**Implemented at source level; focused runtime did not prove a route BREAK event.**

- `NavRoute.Node` now carries a separate `actionPos` (solid block) while `pos`/`standPos()` is the
  traversable execution cell.
- `NavPlanner` rejects a BREAK edge unless it finds a valid adjacent standing position.
- `NavExecutor` navigates only to `node.pos()` (stand), verifies the action target and delegates the
  existing recovery action on `actionPos`.
- No action range, budget, collision, protection, worker or acceptance changes.

## Design

### Route model

`Node(pos, transition, actionPos)`:
- `pos` = traversable standing cell;
- `actionPos` = block acted on (BREAK target), `null` for normal movement;
- old two-argument constructor preserved for compatibility;
- `standPos()` and `actionTarget()` expose the distinction;
- route signature includes `actionPos`.

`NavGraphSearch.Edge/Step` carry `actionPos`; local and global route reconstruction preserve it.

### Planner

For `NavObstruction.Kind.BREAKABLE`:
- the candidate solid cell remains the action target;
- `NavBreakAttackPositionPolicy.candidates` enumerates adjacent/diagonal standing cells, excluding the
  current cell to avoid a zero-progress self-edge;
- each candidate must pass:
  - full mob AABB (`NavObstruction.fits`),
  - real support (`NavObstruction.hasSupport`),
  - protection policy,
  - existing 2.5-block action reach,
  - line of sight (`Level.clip` from stand eye to action target);
- if none passes, the BREAK edge is rejected (`NODO_NO_PISABLE`); no auto-BUILD/BRIDGE fallback is
  introduced;
- the successor state is `standPos`, and `actionPos` is retained in the route metadata.

### Executor

For modern BREAK nodes:
1. navigate to `node.pos()` (stand) only;
2. wait until physically at the stand cell;
3. verify target is breakable, in range, still visible, and authorized;
4. set the existing recovery override to `actionPos` and enter RECOVERING;
5. existing `attemptOneRecovery()` invokes the single `RecoveryAction` and verifies air;
6. only a verified air target advances the route and replans.

Legacy BREAK nodes without `actionPos` retain the old fallback path. `BUILD`, `JUMP_UP`, `STEP_UP`,
`DROP`, `WALK`, `DOOR`, `BRIDGE` behavior is otherwise unchanged.

## Files

- `NavRoute.java`
- `NavPlanner.java`
- `NavGraphSearch.java`
- `NavExecutor.java`
- `NavBreakAttackPositionPolicy.java` (new)
- `NavBreakContractPolicy.java` (new pure policy)
- `NavBreakContractTest.java` (new)
- `build.gradle`

## Tests

```text
.\gradlew.bat test                     PASS
NavBreakContractTest                   OK (5 assertions)
lint_aldea.py --strict                 OK
git diff --check                       PASS
```

The test suite is pure-Java policy coverage because the test source set has no Minecraft classpath.
Full production-planner integration (mock `CollisionGetter`/`Mob`) was not added in this timebox; no
claim is made that every planner geometry path is covered.

## Runtime

Focused run **`a2n-20261116-101718`** (seed 12345, FaseA 3000, FaseB 6000, budgets 900/8000):

- 8/8 spawn.
- 6/8 perimeter.
- **5/8 strict `DENTRO-PARED`:** idx1 513, idx2 668, idx3 1063, idx5 703, idx6 650.
- idx4 perimeter-only; idx7/idx8 outside.
- C4 **PASS** `[1,0,0]`, 12/12 resumed.
- No route `BREAK-DELEGADO` event was present in the captured log; the run does not prove a
  production route BREAK executed through the new path. It does show no aggregate regression and an
  improvement over the immediately preceding 3/8 strict run.

**A2 remains OPEN. Full 12k not run.** IDX8 is not declared solved by this change; IDX7 remains BLOCKED;
IDX5 boundary UNKNOWN.

## Limitations / pending

- Test coverage is policy-level; full planner/executor integration tests require a Minecraft test
  classpath or a controlled harness fixture.
- `NavBreakAttackPositionPolicy` excludes the current cell; a one-cell-wide break with no alternative
  stand is rejected. This is conservative and may reduce valid tunnel breaks.
- Only BREAK was migrated end-to-end. BUILD/JUMP/STEP still use their existing model.
- No runtime proof of a route BREAK action or target-air advancement yet.

## Worlds

Original worlds unchanged; no new world modified. A2/C4 hashes:
- `BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7`
- `11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF`
