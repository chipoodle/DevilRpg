# Traversal coordination — static validation (2026-11-19)

## 1. Result

**FAIL (behavioral audit), although compile/tests/lint/diff PASS.**

The implementation commit `7aaea193` is present and the full static suite is green. However, the
**normal BREAK path bypasses `tryAcquireTraversalAction()`**, and a DROP fallback still invokes BREAK on
the drop obstruction. The lease is therefore not the only gateway for terrain modification.

- BRANCH: `feature/autonomous-village`
- INITIAL_HEAD: `e4692eeb0df6e9f1e83e25a0c6991004360771c8`
- IMPLEMENTATION_HEAD: `7aaea19364d958ee0e16f9ea89b3b1be9168e878`
- TESTED_HEAD: `9a26bfbe5fa06475e611a256a26acfb3f9ac4ace`
- FINAL_HEAD at validation: same
- PRODUCTION_MODIFIED_BY_HARNESS: false
- RUNTIME_EXECUTED: false
- MINECRAFT_LAUNCHED: false
- WORLDS_MODIFIED: false

## 2. Blob verification

```text
TraversalActionLeasePolicy.java      6c748e7c896831b17f7142ad9e81b692b42c6ada  MATCH
AggressiveZombieEntity.java          c7ff8d65522605f0e7478e90a9bf7a68570c95cc  MATCH
TraversalActionLeasePolicyTest.java  ee65b408d8d9f04bbf13a0d00ee2850111ce529d  MATCH
build.gradle                         d75d65cd197b29112a5d4915e6e70614777d99c5  MATCH
NavExecutor.java                     expected b808fe59...; actual 2ba70851...
```

`b808fe591d0d2dcdf3b703bfdd4ca5d4e21ab181` is the parent commit `ff0a0672` version;
`7aaea193` changes only three comment lines, yielding `2ba70851ca1f119657cfeceb6f29ed36cb5310bb`. The
production logic is identical; the handoff blob is stale.

## 3. Static results

```text
compileJava --info ... : PASS (exit 0)
test --info ...        : PASS (exit 0)
lint_aldea.py --strict : PASS (exit 0)
git diff --check       : PASS (exit 0)
```

```text
TRAVERSAL_ACTION_LEASE_POLICY_TEST: OK (13 assertions)
NAV_PLANNER_STATE_TEST: OK (54 assertions)
NAV_LOCAL_OBJECTIVE_STEP_POLICY_TEST: OK (5 assertions)
STAIR_BUILD_RESULT_TEST: OK (12 assertions)
Named test groups: 55
No FALLO: markers
```

Logs: `build/traversal-compile.log`, `build/traversal-test.log`, `build/traversal-lint.log`.

## 4. Confirmed implementation points

- `activeNode()` derives from `route.currentNode(routeIndex)` and returns null outside
  TRAVERSING/RECOVERING.
- `activeTransition()` derives from that node.
- `activeActionTarget()` returns `actionTarget` for BREAK, `node.pos()` for BUILD/BRIDGE, null otherwise.
- `tryAcquireTraversalAction()` rejects missing transition/target, non-terrain transitions, mismatched
  transition/target, and a same-tick second lease. It marks the lease used even if the tool later fails.
- `terrainChanged()` invalidates route/global state; subsequent `activeNode()` is null until a new route.
- `BreakBlockGoal` and `TraverseGoal` classes remain but are **not registered** in `GoalSelector`
  (lines2598-2602); no `new BreakBlockGoal`/`new TraverseGoal` registration remains.
- Recovery callback fails closed when node/transition is null.
- Budgets, protection, support/headroom/collision checks are unchanged.

## 5. First failure — BREAK bypasses the lease

`NavExecutor.ejecutarBreakDesdePosicionDeAtaque` (line2306) ends with:

```java
boolean invoked = recoveryAction != null
        && recoveryAction.run(objective, target, NavRoute.Transition.BREAK);
```

There is no preceding `tryAcquireTraversalAction(BREAK, target)`. The callback in
`AggressiveZombieEntity` only checks node/transition non-null and calls `romperNodoDeRuta`; it does not
acquire the lease. Therefore a BREAK terrain action can execute without the per-tick lease.

This violates:

- “`tryAcquireTraversalAction()` impide una segunda acción durante el mismo tick” for all tools.
- “El lease se consume incluso si la herramienta falla” for BREAK.
- The single-authority contract for BREAK.

The existing 13-assertion policy test validates the pure policy, not this integration path.

## 6. Second defect — DROP invokes BREAK

At `DROP` validation failure, `NavExecutor` calls:

```java
boolean accion = recoveryAction.run(node.pos(), obstaculoCaida,
        NavRoute.Transition.BREAK);
```

(lines5061-5062). This is a terrain modification during an active **DROP** transition, outside the
matrix (“DROP: no excavación sustitutiva”; BREAK only for active BREAK). It also bypasses the lease.

This may be an intentional legacy “clear the drop path” behavior, but it is not authorized by the new
contract and must be reconciled by ChatGPT.

## 7. Preserved behavior / no relaxation

- No budget/cooldown constants changed.
- No protection/support/headroom/collision predicates changed.
- No A2/C4 criteria, fixture, or world changed.
- Goals no longer independently choose tools; the remaining defect is inside `NavExecutor` itself.

## 8. Required correction (ChatGPT)

1. Acquire `tryAcquireTraversalAction(BREAK, target)` before the `recoveryAction.run` at line2363; if
   denied, do not invoke.
2. Either acquire a lease and explicitly authorize the DROP-obstruction BREAK at line5061, or remove that
   fallback from the new contract. Do not bypass the lease silently.
3. Add integration-level tests (not only the pure policy) proving:
   - a BREAK action without a lease cannot call `recoveryAction`;
   - a second terrain action in the same tick is denied;
   - DROP cannot invoke BREAK unless the contract explicitly allows it.
4. Re-run static gate; runtime remains unauthorized.

## 9. Integrity

```text
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

Preserved untracked: `.a2-backups/`, `src/foodTest/`, `tools/a2n/A2NavHarness.java.partial`,
`tools/a2n/consultar-c4.ps1`, `tools/a2n/lanzar-c4.ps1`, `tools/a5-closure/`.

A2 remains OPEN.
