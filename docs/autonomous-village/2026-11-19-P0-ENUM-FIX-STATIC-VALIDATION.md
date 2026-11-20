# P0 enum spelling — static validation (2026-11-19)

## 1. Result

**STATIC PASS.** The only production change is the enum constant rename
`ACCEPTAR_NUEVO` → `ACEPTAR_NUEVO`. Compile, full tests, lint and diff-check pass.

- BRANCH: `feature/autonomous-village`
- INITIAL_HEAD: `bdf6575c2d8c4f6640a97cada15194f35b7440d5`
- TESTED_HEAD: `ab470cb700218fdc9d92018dedded87dec5f7948`
- FINAL_HEAD at validation: same
- RUNTIME_EXECUTED: false
- MINECRAFT_LAUNCHED: false
- WORLDS_MODIFIED: false
- PRODUCTION_MODIFIED_BY_HARNESS: false

## 2. Blobs

```text
NavLocalObjectiveStepPolicy.java
a000c986fb27ec249b6936febe3c748970b200c4

NavLocalObjectiveStepPolicyTest.java
eafb796c3b50a0bbb809cb31509327babff78513
```

The enum now declares `ACEPTAR_NUEVO` and the implementation/test use exactly that identifier. Diff is
one changed character; no logic, counter, budget or navigation behavior changed.

## 3. Commands

```text
.\gradlew.bat compileJava --info -Pa2nDiagnostics=true
  -Pa2nPopulationQueryHarness=true -Pa2nTraceNav=true -Pa2nTraceGlobalSearch=true
PASS, exit 0

.\gradlew.bat test --info -Pa2nDiagnostics=true
  -Pa2nPopulationQueryHarness=true -Pa2nTraceNav=true -Pa2nTraceGlobalSearch=true
PASS, exit 0

python tools\lint_aldea.py --strict
PASS, exit 0

git diff --check
PASS, exit 0
```

Logs:

```text
build/p0fix-compile.log
build/p0fix-test.log
build/p0fix-lint.log
```

## 4. Required tests

```text
NAV_LOCAL_OBJECTIVE_STEP_POLICY_TEST: OK (5 assertions)
STAIR_BUILD_RESULT_TEST: OK (12 assertions)
NAV_PLANNER_STATE_TEST: OK (54 assertions)
```

No `FALLO:` markers. The suite reports 54 named test groups.

## 5. All named tests executed

```text
FORESTRY_TREE_CLAIMS_TEST
FORESTRY_RESOURCE_TRACE_TEST
VILLAGE_GOAL_PRIORITY_TEST
PHASE1_TEST
ADVANCE_UNDER_FIRE_POLICY_TEST
NAV_POLICY_TEST
NAV_ARRIVAL_TEST
NAV_ROUTE_NODE_POLICY_TEST
NAV_DROP_TAKEOFF_POLICY_TEST
NAV_SOURCE_CELL_POLICY_TEST
NAV_SNAP_OWNERSHIP_POLICY_TEST
NAV_SNAP_POST_RESET_POLICY_TEST
NAV_ADJACENT_APPROACH_POLICY_TEST
NAV_DROP_APPROACH_POLICY_TEST
NAV_DROP_APPROACH_OWNERSHIP_POLICY_TEST
NAV_DROP_EDGE_STEP_POLICY_TEST
NAV_STAIR_ANCHOR_POLICY_TEST
STAIR_BUILD_RESULT_TEST
NAV_OBJECTIVE_POLICY_TEST
NAV_LOCAL_OBJECTIVE_STEP_POLICY_TEST
NAV_COMBAT_INTENT_POLICY_TEST
NAV_BREAK_CONTRACT_TEST
NAV_BREAK_GRAPH_STATE_TEST
NAV_BREAK_ROUTE_POLICY_TEST
NAV_BREAK_ATTACK_POSITION_POLICY_TEST
NAV_BREAK_ACTION_POLICY_TEST
NAV_BREAK_EXCLUSION_POLICY_TEST
NAV_RECOVERY_CONTROLLER_TEST
TACTICAL_ESCAPE_TEST
OBSTRUCTION_RECOVERY_POLICY_TEST
BLOCK_INTERACTION_POLICY_TEST
NEGATIVE_CACHE_TEST
NAV_TERMINAL_DIAG_TEST
NAV_ROUTE_INVALIDATION_TEST
NAV_PLANNER_STATE_TEST
NAV_GOAL_METRIC_TEST
NAV_CLIMB_POLICY_TEST
NAV_DROP_COLUMN_POLICY_TEST
NAV_ACTION_APPROACH_POLICY_TEST
NAV_RECOVERY_GUARD_POLICY_TEST
SIEGE_WAVE_TEST
FOOD_HUNGER_POLICY_TEST
PANTRY_CREDIT_POLICY_TEST
FORESTRY_TASK_POLICY_TEST
WORLD_SIEGE_POLICY_TEST
VILLAGE_TERRAIN_POLICY_TEST
RAID_TACTICAL_BOARD_TEST
FORESTRY_WORK_PLANNER_TEST
FORESTRY_TASK_CONTROLLER_TEST
VillageTaskNavigationPolicyTest
VillageFarmOwnershipTest
WAREHOUSE_APPROACH_POLICY_TEST
VILLAGE_FOREST_LAYOUT_TEST
GENERATION_BUDGET_TEST
```

## 6. Worktree / preservation

Production files clean. Untracked artifacts preserved:

```text
.a2-backups/
src/foodTest/
tools/a2n/A2NavHarness.java.partial
tools/a2n/consultar-c4.ps1
tools/a2n/lanzar-c4.ps1
tools/a5-closure/
```

## 7. Integrity

```text
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

## 8. Conclusion

The enum spelling defect is fixed with no behavioral change. P0 static gate is PASS. No runtime was
authorized or executed. A2 remains OPEN.
