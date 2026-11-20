# A2 global A* corrections — static validation R2 (2026-11-20)

## 1. Result

**PASS — static only.** The lambda-capture defect is fixed with a behavior-neutral holder. Compile, the
full policy suite, strict lint and diff-check pass.

- BRANCH: `feature/autonomous-village`
- INITIAL_HEAD: `e4911ba3d7e4a5dd10f329707023f9d7f463144f`
- IMPLEMENTATION_SHA: `c663f5d06618d12cd8150e238308fe3e9d7652db`
- CORRECTION_SHA: `ab9d4f0c84a56b67d4efb3e4ff2468ac54e2415f`
- TESTED_HEAD: `dbff54480d9e564531b268e3bfe01347a464f32c`
- FINAL_HEAD at validation: same
- PRODUCTION_MODIFIED_BY_HARNESS: false
- TESTS_MODIFIED_BY_HARNESS: false
- RUNTIME_EXECUTED: false
- MINECRAFT_LAUNCHED: false
- WORLDS_MODIFIED: false

## 2. Blobs

```text
NavBreakAttackPositionPolicy.java       4e06c662... MATCH
NavGraphSearch.java                     906b480a... MATCH
NavPlanner.java                         b89de3c5... MATCH
NavSuccessorPolicy.java                 c50bf342... MATCH
NavBreakAttackPositionPolicyTest.java   3e5acbf3... MATCH
NavPlannerStateTest.java                9d7c23fd... MATCH
```

## 3. Correction audit

`ab9d4f0c` changes only `NavGraphSearch.java`:

```java
- int reopensThisSlice = 0;
+ final int[] reopensThisSlice = {0};

- reopensThisSlice++;
+ reopensThisSlice[0]++;

- lastReopens = reopensThisSlice;
+ lastReopens = reopensThisSlice[0];
```

Confirmed:

- holder is `final`;
- only element 0 is incremented inside the lambda;
- `lastReopens` reads the holder after `graph.expand`;
- `totalReopens` semantics are unchanged;
- reopens only when `closed.remove(edge.to())` is true;
- no change to expansion order, `bestG` improvement, stale OPEN discard, or budgets.

No additional behavioral change.

## 4. Gate

```text
compileJava --info ... : PASS (exit 0, 42 s)
test --info ...        : PASS (exit 0, 55 s)
lint_aldea --strict    : PASS (exit 0)
git diff --check       : PASS (exit 0)
```

Logs: `build/astarcorr-r2-compile.log`, `build/astarcorr-r2-test.log`, `build/astarcorr-r2-lint.log`.

## 5. Tests

```text
NAV_BREAK_ATTACK_POSITION_POLICY_TEST: OK (8 assertions)
NAV_PLANNER_STATE_TEST: OK (53 assertions)
Named policy tests: 55
No FALLO: markers
```

`NAV_PLANNER_STATE_TEST` includes new coverage:

- DROP canonical successor/estadoSucesor uses real landing height;
- stale OPEN record skipped after a better g-score;
- `totalStalePops==1`, `expansions==3`, `totalPops==4`;
- real closed-state reopen counted (`totalReopens==1`, `expansions==4`);
- existing incremental slice/budget invariants.

## 6. Source audit

### BREAK

- candidates at actual `feetY`, eight neighbors;
- current cell accepted as a zero-movement stand;
- same-Y adjacent reach only; no remote/vertical jumps;
- planner keeps support/AABB/collision/protection/reach/LOS checks;
- `standPos` and `actionPos` remain separate;
- local route attaches BREAK to the final stand, including the current cell;
- no lease bypass.

### DROP

- `sucesorCanonico`/`estadoSucesor` use `feetHasta`/landing cell;
- local/global searches use the canonical successor;
- DROP exclusions/footprints use the real landing;
- no safe-drop/support/takeoff relaxation.

### OPEN / telemetry

- stale entry discarded when `g != bestG[state]`;
- real reopen counted when removed from CLOSED;
- pops/stale/closed/reopens totals and last-slice metrics exposed;
- 8-pop slice and total expansion limits unchanged;
- PENDING/COMPLETE summaries include metrics;
- CANCEL logs caller/method/line/tick and counters.

## 7. Integrity

```text
A2 APPDATA BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
A2 run     F4EEAA13C56D92812B7B668EFDAF4AB200973A9977006466E969481C09419B44
C4 APPDATA 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
C4 run     11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

Unchanged. Untracked preserved: `.a2-backups/`, `src/foodTest/`,
`tools/a2n/A2NavHarness.java.partial`, `tools/a2n/consultar-c4.ps1`,
`tools/a2n/lanzar-c4.ps1`, `tools/a5-closure/`.

## 8. Boundary

This is **static PASS only**. No A2 runtime, world, D3 BUILD traversal, C4 or Full12K was measured.
A2 remains OPEN; C4 remains unvalidated; runtime requires a new explicit authorization.
