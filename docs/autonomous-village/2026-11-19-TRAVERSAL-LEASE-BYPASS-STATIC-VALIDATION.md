# Traversal lease-bypass fix — static validation (2026-11-19)

## 1. Result

**PASS.** Both prior bypasses are closed. Compile, full tests, lint and diff-check pass. No runtime was
executed.

- BRANCH: `feature/autonomous-village`
- IMPLEMENTATION_SHA: `9f00cb5ecec462df91f9905072988db90bec08b2`
- TESTED_SHA: `ab75af8fc27302fa3c5c157b8d72562d9e3e2cd5`
- FINAL_HEAD at validation: same
- RUNTIME_EXECUTED: false
- MINECRAFT_LAUNCHED: false
- WORLDS_MODIFIED: false
- PRODUCTION_MODIFIED_BY_HARNESS: false

## 2. Blob verification

```text
NavExecutor.java                         908fcaa323c5f6f08cd186c55650cab71c1d28ce  MATCH
TraversalActionDispatchPolicy.java       6309324398d3d21b54f9a8e34ab24540d3e590a8  MATCH
TraversalActionDispatchPolicyTest.java   1f62e839c0492e8b21640b37d0488c0fad018532  MATCH
build.gradle                             899208c0b5ba7ad3e50f166fb2dc26c3b9bc1a67  MATCH
```

Production diff `9f00cb5e..HEAD` in `src`/`build.gradle`: empty.

## 3. Callback audit

`recoveryAction.run(` occurrences in `NavExecutor.java`: **exactly one**.

```text
line5861: leaseAcquired, () -> recoveryAction.run(towards, target, transition));
```

It is inside `invokeTerrainAction(...)`, after:

```java
boolean leaseAcquired = tryAcquireTraversalAction(transition, target);
return TraversalActionDispatchPolicy.dispatch(
        leaseAcquired, () -> recoveryAction.run(towards, target, transition));
```

No other direct callback invocation exists.

## 4. BREAK / recovery / DROP

### Normal BREAK

`ejecutarBreakDesdePosicionDeAtaque` now calls:

```text
invokeTerrainAction(NavRoute.Transition.BREAK, target, objective)
```

The lease requires active BREAK, exact `actionTarget`, and no same-tick consumption.

### Recovery BREAK/BRIDGE

`attemptOneRecovery` selects only the active node's BREAK/BRIDGE target and calls
`invokeTerrainAction(transicion, nodo, towards)`. No generic strategic-objective fallback remains.

### DROP

The rejected-DROP path now:

- does not invoke `recoveryAction`;
- does not call BREAK;
- does not emit `DROP_OBSTRUCTION_CLEARED`;
- records the take-off and destination exclusion;
- clears route/global cache and returns to PLANNING for a bounded replan.

### Goals

`BreakBlockGoal`/`TraverseGoal` classes remain but are not registered in `GoalSelector`; no
`new BreakBlockGoal` or `new TraverseGoal` registration exists.

## 5. Static commands

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

## 6. Tests and assertions

```text
TRAVERSAL_ACTION_LEASE_POLICY_TEST: OK (13 assertions)
TRAVERSAL_ACTION_DISPATCH_POLICY_TEST: OK (6 assertions)
NAV_PLANNER_STATE_TEST: OK (54 assertions)
NAV_LOCAL_OBJECTIVE_STEP_POLICY_TEST: OK (5 assertions)
STAIR_BUILD_RESULT_TEST: OK (12 assertions)
```

Named test groups: **56**. No `FALLO:` markers. Logs:

```text
build/leasefix-compile.log
build/leasefix-test.log
build/leasefix-lint.log
```

## 7. Preservation

```text
.a2-backups/
src/foodTest/
tools/a2n/A2NavHarness.java.partial
tools/a2n/consultar-c4.ps1
tools/a2n/lanzar-c4.ps1
tools/a5-closure/
```

Untouched. Protected A2/C4 worlds unchanged.

```text
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

## 8. Conclusion

The lease is now the single gateway for terrain callbacks. BREAK and BRIDGE recovery use it; DROP cannot
invoke a terrain tool; failed actions consume the lease. The implementation is statically valid.

A2 remains OPEN. C4 is not revalidated. Runtime requires new explicit authorization.
