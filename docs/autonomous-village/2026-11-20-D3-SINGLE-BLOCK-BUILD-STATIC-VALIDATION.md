# D3 single-block BUILD — static validation (2026-11-20)

## 1. Result

**PASS.** Compile, full policy suite, lint and diff-check pass. The source audit confirms the +1 ordinary
ascent invariant, removal of the +2 jump actuator, and the BUILD feet/action split.

- BRANCH: `feature/autonomous-village`
- INITIAL_HEAD: `f8705b2c707e61b2d687a4abe506f22d500bcce9`
- IMPLEMENTATION_SHA: `c520198c0b89e5da0d6f2e760b22e8d43fcec7d7`
- TESTED_SHA: `9009bb7b66d9ca1fa3f601a09d369465bc926edc`
- FINAL_HEAD at validation: same
- PRODUCTION_MODIFIED_BY_HARNESS: false
- TESTS_MODIFIED_BY_HARNESS: false
- RUNTIME_EXECUTED: false
- MINECRAFT_LAUNCHED: false
- WORLDS_MODIFIED: false

## 2. Blob verification

All 11 report blobs match at HEAD:

```text
NavVerticalTransitionPolicy.java   682573c6... MATCH
NavBuildTransitionPolicy.java      328d7217... MATCH
NavObstruction.java                821c393f... MATCH
NavPlanner.java                    27f8387d... MATCH
NavRouteNodePolicy.java            f13243d1... MATCH
NavExecutor.java                   4e73cd3b... MATCH
NavVerticalBuildPolicyTest.java    23fa8566... MATCH
NavPlannerStateTest.java           dcd963cc... MATCH
NavRouteNodePolicyTest.java        8ad85d6f... MATCH
NavArrivalPolicyTest.java          8dcd33b7... MATCH
build.gradle                       352dc131... MATCH
```

Affected climb/DROP files present: `NavClimbPolicy` `339738b3`, `NavDropTransitionPolicy` `361fc538`,
tests `8da93ebd`/`3a9df00e`.

Production diff `c520198c..HEAD` in `src`/`build.gradle`: empty.

## 3. Static commands

```text
compileJava --info -Pa2nDiagnostics=true -Pa2nPopulationQueryHarness=true
  -Pa2nTraceNav=true -Pa2nTraceGlobalSearch=true
PASS, exit 0

test --info with same properties
PASS, exit 0

python tools\lint_aldea.py --strict
PASS, exit 0

git diff --check
PASS, exit 0
```

Logs: `build/d3-compile.log`, `build/d3-test.log`, `build/d3-lint.log`.

## 4. Required tests

```text
NAV_VERTICAL_BUILD_POLICY_TEST: OK (32 assertions)
NAV_PLANNER_STATE_TEST: OK (47 assertions; updated BUILD successor coverage)
NAV_ROUTE_NODE_POLICY_TEST: OK (8 assertions)
NAV_CLIMB_POLICY_TEST: OK (all assertions green; JUMP_UP excluded from standing waypoint)
NAV_DROP_TAKEOFF_POLICY_TEST: OK (51 assertions; legacy JUMP_UP rejected)
NAV_ARRIVAL_TEST: OK (all assertions green; legacy JUMP_UP arrival rejected)
```

Named test groups: **57**. No `FALLO:` markers.

The 32-assertion vertical/build suite verifies:

- +0 WALK, +1 STEP_UP, +2/+3 not ordinary;
- `MAX_ORDINARY_ASCENT=1`;
- BUILD rejects +2/+3 single edges and non-placeable intermediates;
- three BUILD edges for a +3 rise, each +1;
- each step's placement action is below its feet state;
- A* rejects a non-buildable intermediate;
- placement-once, exact physical arrival predicates.

## 5. JUMP_UP audit

```text
0.62 occurrences: 0
iniciarSalto/tickSalto occurrences: 0
NavObstruction.classify emits JUMP_UP: no
NavPlanner.SUBIDA_MAXIMA = MAX_ORDINARY_ASCENT (1)
NavPlanner.piesResultantes(JUMP_UP) -> PIES_NO_EJECUTABLE
NavPlanner.etiquetaDeMovimiento(+2/+3) -> null (guarded before use)
NavExecutor case JUMP_UP -> OBSTRUCTED, no action
NavExecutor nodoAlcanzado(JUMP_UP) -> false
```

Legacy enum remains for compatibility only; no ordinary producer/consumer executes it.

## 6. BUILD graph/executor audit

```text
Arista BUILD actionPos = candidate (placement)
BUILD feetHasta = candidate.y + 1
successorFeet = placement.above()
NavRouteNodePolicy BUILD -> graph state (feet)
NavRoute.Node.pos = feet; actionPos = placement
activeActionTarget(BUILD) -> node.actionPos
BUILD case:
  - missing actionPos -> OBSTRUCTED
  - occupied unexpected placement -> OBSTRUCTED
  - lease exact placement before placement.place
  - verify block solid after placement
  - construidas.add(placement)
  - move to node.pos
  - nodoAlcanzado requires placementVerified, exact live feet, grounded, support, fits AABB
```

No BUILD→BREAK/BRIDGE conversion. No repeated placement after verification.

## 7. Safety/invariants

`esConstruible` protection/canPlace/air/headroom/support-face checks remain; placement callback remains;
lease remains exact per entity/tick; budgets/cooldowns unchanged; no removed goals reactivated; A2/C4
criteria and worlds untouched.

## 8. Integrity

```text
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

Preserved untracked: `.a2-backups/`, `src/foodTest/`, `tools/a2n/A2NavHarness.java.partial`,
`tools/a2n/consultar-c4.ps1`, `tools/a2n/lanzar-c4.ps1`, `tools/a5-closure/`.

## 9. Conclusion

D3 is statically PASS. Runtime traversal is UNKNOWN and must be measured only under a new explicit
authorization. A2 remains OPEN; C4 is not revalidated.
