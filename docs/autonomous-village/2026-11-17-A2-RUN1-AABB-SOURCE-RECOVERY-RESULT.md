# A2 Run 1 — AABB source recovery result: BLOCKED at static gate (2026-11-17)

## Result

**BLOCKED.** Static validation failed in `compileTestJava`; Minecraft/A2 was not executed.

- Inspected/tested SHA: `1e65c0242fc8058775eb2ab4687ccd3811fa42ad`
- Production checkpoint: `b02affb19eb8e37824682090ba99f793a04aea94`
- Report: this file.

## Static validation

| Order | Command | Exit | Result | Log |
|---:|---|---:|---|---|
| 1 | `.\gradlew.bat test` | **1** | **FAIL** | `build/phase20-source-gradle.log` |
| 2 | `python tools/lint_aldea.py --strict` | 0 | PASS | `build/phase20-source-lint.log` |
| 3 | `git diff --check` | 0 | PASS | terminal |
| 4 | `.\gradlew.bat compileJava -Pa2nDiagnostics=true` | 0 | PASS | `build/phase20-source-diagcompile.log` |

### Exact failure

```text
> Task :compileTestJava FAILED
11 errors

NavDropTakeoffPolicyTest.java:60: error: cannot find symbol
  BlockPos resolvedSource = new BlockPos(593, 87, 508);
NavDropTakeoffPolicyTest.java:61: error: cannot find symbol
  BlockPos originalTakeoff = new BlockPos(593, 86, 507);
NavDropTakeoffPolicyTest.java:69: error: cannot find symbol
NavDropTakeoffPolicyTest.java:68: error: cannot find symbol
NavDropTakeoffPolicyTest.java:72: error: cannot find symbol
NavDropTakeoffPolicyTest.java:76: error: cannot find symbol
NavDropTakeoffPolicyTest.java:74: error: cannot find symbol
NavDropTakeoffPolicyTest.java:75: error: cannot find symbol
NavDropTakeoffPolicyTest.java:79: error: cannot find symbol

symbol: class BlockPos
```

**File:** `src/test/java/com/chipoodle/devilrpg/entity/nav/NavDropTakeoffPolicyTest.java`
**Missing import:** `net.minecraft.core.BlockPos`
**Lines:** 60, 61, 68, 69, 72, 74, 75, 76, 79 (11 errors total)

The test source references `BlockPos` but imports only the package declaration. `NavSourceCellPolicyTest`
already imports `net.minecraft.core.BlockPos`; `NavDropTakeoffPolicyTest` does not.

Because Gradle test compilation aborts, no assertion count can be executed. The expected names were
not observed:

```text
NAV_SOURCE_CELL_POLICY_TEST: NOT EXECUTED
NAV_DROP_TAKEOFF_POLICY_TEST: NOT EXECUTED
NAV_ROUTE_NODE_POLICY_TEST: NOT EXECUTED
```

Source-authored `comprobar(...)` call counts (including method declaration):

```text
NavSourceCellPolicyTest: 11 occurrences -> expected 10 assertions
NavDropTakeoffPolicyTest: 41 occurrences -> expected 40 assertions
NavRouteNodePolicyTest: 6 assertions (unchanged)
```

These are **authored, not run**, and do not count as validation.

## A2 Run 1

```text
A2_RUN1: NOT RUN — static gate failed
```

No Minecraft process was started. No attacker UUIDs were produced for this SHA.

Historical IDX4 UUID `60e65311-464d-4756-a840-b5199b566cf6` is not evidence for this run.

## IDX4 fields

```text
IDX4_UUID: NOT AVAILABLE (runtime prohibited)
SOURCE_INVALID: NOT TESTED
SOURCE_CANDIDATE: NOT TESTED
SOURCE_SNAP_STARTED: NOT TESTED
SOURCE_SNAP_REACHED: NOT TESTED
SOURCE_PHYSICAL_OCCUPANCY: NOT TESTED
SOURCE_FEET_VALID: NOT TESTED
SOURCE_SUPPORT: NOT TESTED
SOURCE_REAL_AABB_FIT: NOT TESTED

RECOVERY_ROUTE_ACCEPTED: NOT TESTED
RECOVERY_ROUTE_TRANSITIONS: NOT TESTED
AUXILIARY_DROP_COUNT: NOT TESTED
AUXILIARY_DROP_PHYSICAL_LANDING: NOT TESTED
EXACT_TAKEOFF_REACHED: NOT TESTED
ORIGINAL_ROUTE_RESTORED: NOT TESTED
ORIGINAL_CURSOR_RESTORED: NOT TESTED
ORIGINAL_PLANNED_FROM_RESTORED: NOT TESTED
ORIGINAL_DROP_EXECUTED: NOT TESTED
ORIGINAL_DROP_PHYSICAL_LANDING: NOT TESTED
```

## IDX7 / IDX8 / C4

```text
IDX7: NOT RUN
IDX8: NOT RUN
C4: NOT RUN
```

No campaign, Run 2 or 12k was executed.

## Diagnosis for ChatGPT

The production main source compiles (`compileJava` PASS), so the blocker is test-source compilation.
Add the missing import:

```java
import net.minecraft.core.BlockPos;
```

to `NavDropTakeoffPolicyTest.java`, then republish a new SHA. Do not change production behavior to
work around a test import.

Check whether `NavSourceCellPolicyTest` and the new expected assertions are actually registered in
`build.gradle`; the failed `:compileTestJava` prevents all test mains from running.

## Integrity

```text
PRODUCTION_MODIFIED_BY_HARNESS: false
ORIGINAL_WORLDS_MODIFIED: false
ORIGINAL_A2_WORLD_MODIFIED: false
ORIGINAL_C4_WORLD_MODIFIED: false
LOCAL_ARTIFACTS_PRESERVED: true
```

Original world hashes:

```text
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

## Artifacts

- `build/phase20-source-gradle.log`
- `build/phase20-source-lint.log`
- `build/phase20-source-diagcompile.log`

No `srv.log`, `nav-trace.log`, `fin.txt` or `estado.txt` were produced because runtime did not start.

## Gate

- Static gate: **FAIL**.
- A2 Run 1: **NOT RUN**.
- Run 2: prohibited.
- Full 12k: prohibited.
- A2 remains **OPEN**.

## Next action

ChatGPT must add the missing `BlockPos` import to `NavDropTakeoffPolicyTest`, verify the test mains
are registered, and publish a new SHA. Harness will rerun static validation first, then A2 Run 1 only.
