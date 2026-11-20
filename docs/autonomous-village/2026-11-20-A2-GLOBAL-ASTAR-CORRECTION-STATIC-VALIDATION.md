# A2 global A* corrections — static validation (2026-11-20)

## 1. Result

**FAIL — compile error.** The implementation blobs match the report, but `NavGraphSearch.java` does not
compile: a mutable local is captured by the `graph.expand` lambda. Tests never execute.

- BRANCH: `feature/autonomous-village`
- INITIAL_HEAD: `eb92c817f2cbdbe1bfa708ad92cb4493c51d1d5c`
- IMPLEMENTATION_SHA: `c663f5d06618d12cd8150e238308fe3e9d7652db`
- TESTED_HEAD: `bfaebd0661a39b3031de3f59efe6fdcef6a3c889`
- FINAL_HEAD at validation: same
- PRODUCTION_MODIFIED_BY_HARNESS: false
- TESTS_MODIFIED_BY_HARNESS: false
- RUNTIME_EXECUTED: false
- MINECRAFT_LAUNCHED: false
- WORLDS_MODIFIED: false

## 2. Blobs

All six report blobs match HEAD:

```text
NavBreakAttackPositionPolicy.java       4e06c662... MATCH
NavGraphSearch.java                     0b306ea9... MATCH
NavPlanner.java                         b89de3c5... MATCH
NavSuccessorPolicy.java                 c50bf342... MATCH
NavBreakAttackPositionPolicyTest.java   3e5acbf3... MATCH
NavPlannerStateTest.java                9d7c23fd... MATCH
```

Production diff `c663f5d0..HEAD`: empty.

## 3. First failure

```text
src/main/java/com/chipoodle/devilrpg/entity/nav/NavGraphSearch.java:176:
error: local variables referenced from a lambda expression must be final or effectively final
                        reopensThisSlice++;
```

`reopensThisSlice` is a mutable slice counter declared in `advance` and incremented inside the
`graph.expand(...)` lambda. Java forbids capturing it. This is a compile-time blocker, not a behavior
failure.

## 4. Static commands

```text
compileJava --info ... : FAIL (exit 1)
test --info ...        : FAIL (exit 1, same compile error)
lint_aldea.py --strict : PASS (exit 0)
git diff --check       : PASS (exit 0)
```

```text
NAV_BREAK_ATTACK_POSITION_POLICY_TEST: NOT RUN
NAV_PLANNER_STATE_TEST: NOT RUN
All policy tests: NOT RUN
```

Logs: `build/astarcorr-compile.log`, `build/astarcorr-test.log`, `build/astarcorr-lint.log`.

## 5. Source audit (not executable evidence)

The intended changes are present and coherent at source level, but cannot be validated at runtime or by
tests until compilation is fixed.

### BREAK

- `candidates(actionPos, from, feetY)` builds the eight neighbors at the actual feet Y, sorted by distance.
- `isSingleStepReachable` accepts the current cell (`dx=dz=0`) and adjacent single steps at the same feet Y.
- `NavPlanner` calls `candidates(actionPos, from, piesDesde)`, so the stand height is the mob's feet level.
- `standPos` and `actionPos` remain separate in `Arista`/`NavRoute.Node`.
- Lease path remains `invokeTerrainAction`; no direct BREAK bypass found.

### DROP

- `estadoSucesor` and `sucesorCanonico` use the feet/landing cell for DROP.
- Local/global edge construction both use `sucesorCanonico`.
- DROP exclusions/huellas use the canonical landing.
- No safe-drop/support relaxation found.

### OPEN / metrics / cancel

- `advance` discards stale OPEN entries by comparing entry `g` with `bestG[state]`.
- Metrics exist: `totalPops`, `totalStalePops`, `totalClosedSkips`, `totalReopens`, last-slice counters.
- `GLOBAL_SEARCH_CANCEL` logs caller/method/line/tick and counters.
- `GLOBAL_SEARCH_PENDING`/`COMPLETE` summaries include the counters.
- The 8-pop-per-slice and total expansion limits appear unchanged.

These are source observations, not PASS: the code does not compile.

## 6. Required correction

ChatGPT must fix the lambda capture without changing the intended metrics. Options: use a mutable holder
(e.g. `int[] reopensThisSlice`), aggregate reopens after `graph.expand`, or move the counter to a field
with per-slice reset. Then publish a new HEAD and re-run the exact gate.

## 7. Preservation

Untracked preserved: `.a2-backups/`, `src/foodTest/`, `tools/a2n/A2NavHarness.java.partial`,
`tools/a2n/consultar-c4.ps1`, `tools/a2n/lanzar-c4.ps1`, `tools/a5-closure/`.

Protected A2/C4 worlds unchanged:

```text
A2 APPDATA BA00A1C4...
A2 run     F4EEAA13...
C4 both    11D064E2...
```

A2 remains OPEN. No runtime.
