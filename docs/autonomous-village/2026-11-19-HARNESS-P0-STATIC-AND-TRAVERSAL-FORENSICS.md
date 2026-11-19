# Harness P0 static validation and traversal forensics (2026-11-19)

## 1. Result

**STATIC FAIL.** The P0 implementation is present at `b41e9687`/HEAD `b35c6cb3`, but `compileJava`
fails in the new `NavLocalObjectiveStepPolicy`; therefore tests were not executed.

- BRANCH: `feature/autonomous-village`
- INITIAL_HEAD: `3c36bfb21f1fdce64379dc734bbdf90aae550dde`
- REMOTE/TESTED_HEAD: `b35c6cb3789ee7a267572521ef39ec3394e1750f`
- P0 production commit: `b41e9687a4be74d24a671340ac41b7e3f1817045`
- RUNTIME_EXECUTED: false
- MINECRAFT_LAUNCHED: false
- PRODUCTION_MODIFIED_BY_HARNESS: false

## 2. Git / preservation

Fast-forward only from `3c36bfb2` to `b35c6cb3`. All untracked artifacts preserved:
`.a2-backups/`, `src/foodTest/`, `tools/a2n/A2NavHarness.java.partial`,
`tools/a2n/consultar-c4.ps1`, `tools/a2n/lanzar-c4.ps1`, `tools/a5-closure/`.

## 3. Static commands

### compileJava

```text
.\gradlew.bat compileJava --info -Pa2nDiagnostics=true
  -Pa2nPopulationQueryHarness=true -Pa2nTraceNav=true -Pa2nTraceGlobalSearch=true
exit 1
```

Error:

```text
src/main/java/com/chipoodle/devilrpg/entity/nav/NavLocalObjectiveStepPolicy.java:20:
error: cannot find symbol
            return Decision.ACEPTAR_NUEVO;
                           ^
  symbol:   variable ACEPTAR_NUEVO
  location: class Decision
```

The same failure occurs with `--rerun-tasks` and with direct `javac` on the file. A minimal equivalent
policy (same enum constants, same method) compiles, so the failure is source-specific, not a Gradle or
classpath issue.

### test

```text
.\gradlew.bat test --info ... : exit 1, same compile error
NAV_LOCAL_OBJECTIVE_STEP_POLICY_TEST: NOT RUN
STAIR_BUILD_RESULT_TEST: NOT RUN
NAV_PLANNER_STATE_TEST: NOT RUN
all other tests: NOT RUN
```

Expected assertions from the P0 report (not executed, not claimed as PASS):
`NAV_LOCAL_OBJECTIVE_STEP_POLICY_TEST=5`, `STAIR_BUILD_RESULT_TEST=12`,
`NAV_PLANNER_STATE_TEST=54`.

### lint / diff

```text
python tools\lint_aldea.py --strict: PASS (exit 0)
git diff --check: PASS (exit 0)
```

Logs: `build/p0-compile.log`, `build/p0-compile-rerun.log`, `build/p0-test.log`, `build/p0-lint.log`.

## 4. Traversal research A–I

### A. Active node and transition

`NavExecutor` owns `NavRoute route`, `int routeIndex` and reads
`route.currentNode(routeIndex)` (line4708). Each `NavRoute.Node` is
`(BlockPos pos, Transition transition, BlockPos actionPos)`.

### B. Transition distinction

`NavRoute.Transition` is switched at line4799: `JUMP_UP`, `STEP_UP`, `DROP`, `BUILD` (5267),
`BREAK` (5290), `BRIDGE` (5294); default/WALK is the normal movement path. The transition selects the
actuator, not just the destination.

### C. Blockage/progress detection

- `NavExecutor` tracks `ticksSinAvance` against `mejorDistAlNodo` with
  `AVANCE_MINIMO_DE_NODO=0.25` and declares `OBSTRUCTED` when the active node does not improve.
- `nodoAlcanzado` requires physical proximity; DROP requires physical landing plus support.
- `BreakBlockGoal` uses its own 10-tick movement detector (`MOVIMIENTO_QUE_CUENTA=0.25`).
- `TraverseGoal` uses a 10-tick window and `DESPLAZAMIENTO_MINIMO_SQR=0.25`.
There is no shared blockage state between the executor and the two goals.

### D. Knowing whether the next segment is rise/fall

The executor knows exactly: the active node transition and its Y. The goals do not consult it.
`BreakBlockGoal` chooses from `getTarget()`/`villageCenter`; `TraverseGoal.objetivoDeLaMarcha` chooses
the target/center and decides from `hacia.getY()` versus the mob Y. They can therefore act on a global
objective height rather than the active route transition.

### E. Goal ↔ executor communication

Goals call public zombie helpers (`breakBlockAt`, `cavarHaciaAbajo`, `apilarBloqueParaSubir`,
`puentearHacia`) and then `navExecutor.terrainChanged()`. They do not ask the executor for the active
node, transition, action target or blockage reason. `NavExecutor` can only invalidate/replan after the
world changes.

### F. Mutual exclusion

Both goals are added at priority 3:

```java
this.goalSelector.addGoal(3, new BreakBlockGoal(this));
...
this.goalSelector.addGoal(3, new TraverseGoal(this));
```

Neither sets `Goal.Flag.MOVE`/`LOOK`; both are passive. With no flags there is no mutual-exclusion flag
separating them, and no shared actuator lease. Both can evaluate/act in the same interval. This is a real
coordination risk.

### G. Existing gap before creating one

`BreakBlockGoal` uses `blockingBlockAhead`/`breakBrechaAhead`; `puentearHacia` checks the target cell is
air and has empty space, so it will not bridge into a solid cell. But neither goal first asks “does the
active transition already have a valid existing gap?” The decision is based on target height/center and
local probes, not the validated route.

### H. Current tools and budgets

| Tool | Method | Budget/guard |
|---|---|---|
| break block | `breakBlockAt` (939), `breakBlock` (4007) | `protegidoPorLaAldea`, breakable material, `tunelRestante=40`, `tunelEspera=40` |
| breach ring | `breakBrechaAhead` | wall-ring check + same tunnel budget |
| descend/dig | `cavarHaciaAbajo` (2528) | height delta -1..-3, `cavarEspera=10`, protected/air checks |
| stair up | `apilarBloqueParaSubir` (1651) | `apilarRestante=8`, `apilarEspera=10`, headroom/support checks |
| bridge | `puentearHacia` (1486) | `puenteEspera=20`, tunnel budget, empty space/abyss check |

### I. Minimum architecture change

Make `NavExecutor` the sole source of truth for the next action. Expose read-only:

```text
activeTransition()
activeActionTarget()
activeBlockedReason()
activeBudgetState()
```

Then a single actuator coordinator (one lease per tick) maps:

```text
WALK/STEP_UP/JUMP_UP -> follow only
DROP -> follow; dig only if transition DROP and landing validated
BREAK  -> break exactly actionTarget, protected checks
BUILD  -> place exactly node cell
BRIDGE -> bridge exactly node cell
```

Goals must not choose a tool from the global center/target height. They may request an action only for
the active transition, and only one may own the actuator lease. Existing gap detection comes first.

## 5. Recommended pure tests

```text
TraversalIntentPolicyTest
- only active transition authorizes a tool
- BREAK cannot become BUILD/BRIDGE
- DROP requires validated landing
- BUILD requires support/headroom
- protected cell denies every tool
- no lease means no action
- existing passable gap wins over construction
```

## 6. Limitations

- No test or runtime could validate P0 because compilation fails.
- The exact cause of the `NavLocalObjectiveStepPolicy` compiler resolution failure is not determined; a
  minimal equivalent compiles. ChatGPT should re-create/rename the enum or publish a corrected file.
- Traversal findings are source-only, not runtime-measured.

## 7. Status

`STATIC FAIL`. A2 remains OPEN. No runtime, no Run2, no Full12K, no production edits.
