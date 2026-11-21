# A2 BUILD provenance + BREAK diagnostics — static validation (2026-11-21)

## 1. Result

**PASS — static only.** Compile, test, policyTest, strict lint and diff-check pass. The BUILD provenance
contract is fail-closed and the BREAK diagnostics are per-entity.

- BRANCH: `feature/autonomous-village`
- INITIAL_HEAD: `87d92e4c03bc9efdc94f7532474dab6fc5071f3b`
- TESTED_HEAD: `7bd26569da11e0a61de0af86124bf6fc6666e7fe`
- FINAL_HEAD at validation: same
- PRODUCTION_MODIFIED_BY_HARNESS: false
- TESTS_MODIFIED_BY_HARNESS: false
- RUNTIME_EXECUTED: false
- MINECRAFT_LAUNCHED: false
- WORLDS_MODIFIED: false

### HEAD discrepancy

The prompt’s `7bd26569da11e0a61de0af86124bf6fc5071f3b` does not exist. The remote tip sharing the
`7bd26569` prefix is `7bd26569da11e0a61de0af86124bf6fc6666e7fe`; all six expected blobs match that commit.
Validation used the actual remote tip.

## 2. Blobs

```text
NavExecutor.java                    4269dc33... MATCH
NavPlanner.java                     212837a2... MATCH
NavBreakActionPolicy.java           1840f268... MATCH
NavBuildObjectivePolicy.java        8af9ac31... MATCH
NavBuildObjectivePolicyTest.java    19cff94f... MATCH
build.gradle                        1fa708fa... MATCH
```

## 3. Gate

```text
compileJava : PASS (exit 0, 3m30s)
test        : PASS (exit 0, 3m25s)
policyTest  : PASS (exit 0, 2m49s)
lint strict : PASS (exit 0)
diff --check: PASS (exit 0)
```

```text
NAV_BUILD_OBJECTIVE_POLICY_TEST: OK (8 assertions)
Named tests: 57
FALLO: markers: 0
```

Logs: `build/prov-compile.log`, `build/prov-test.log`, `build/prov-policytest.log`, `build/prov-lint.log`.

## 4. BUILD provenance audit

- `NavBuildObjectivePolicy.isValid(feet, placement)` requires both non-null and
  `successorFeet(placement).equals(feet)` (`placement.above()`), so missing/misaligned placement is
  rejected.
- `elegirObjetivoLocal` rejects BUILD nodes whose `n.actionPos()` is missing or not directly below the
  feet cell, logging `BUILD_GLOBAL_WAYPOINT_REJECTED`; the node is skipped.
- Selection authorizes construction only when `objetivoConstruible && NavBuildObjectivePolicy.isValid(...)`.
- `objetivoAccionConstruccion` retains the exact placement cell; `INC_STEP` carries it only after
  re-validating `siguiente` against it.
- The local planning gate rechecks `objetivoPermiteConstruccion &&
  NavBuildObjectivePolicy.isValid(objective, objetivoAccionConstruccion)`; otherwise the objective is
  treated as non-usable.
- BREAK metadata remains separate: `objetivoAccionLocal` is BREAK-only; `objetivoAccionConstruccion` is
  BUILD-only.
- BUILD lease, placement, reread verification, support, headroom, AABB and physical arrival rules are
  unchanged.

`NAV_BUILD_OBJECTIVE_POLICY_TEST` covers: exact pair valid, missing placement/feet, placement one below,
one above, lateral, same cell, and an independent valid pair (8 assertions).

## 5. BREAK diagnostics audit

- `NavPlanner.breakStandTraceEvents` is `WeakHashMap<Mob,Integer>`; `traceBreakStandCandidate(mob, ...)`
  emits `uuid` and `caller=NavPlanner#buscarPosicionDeAtaque`.
- `NavBreakActionPolicy.diagnosticEvents` is `WeakHashMap<Mob,Integer>`; `trace(mob, ...)` emits `uuid` and
  `caller=NavBreakActionPolicy#executable`.
- The 128-event limit is read/incremented per Mob key, so each live entity has an independent budget.
- Weak keys do not retain unloaded entities; values are `Integer` and do not back-reference the key.
- No candidate set, reach, support, protection, raycast or execution predicate changed; only diagnostics
  fields and counter scope changed.

## 6. Invariants

```text
navigation budgets/radii/vertical range: unchanged
DROP landing/support/takeoff rules:      unchanged
protected world/wall rules:              unchanged
traversal lease behavior:                unchanged
A2/C4 acceptance criteria:               unchanged
```

## 7. Preservation

Protected worlds unchanged:

```text
A2 APPDATA BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
A2 run     F4EEAA13C56D92812B7B668EFDAF4AB200973A9977006466E969481C09419B44
C4 APPDATA 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
C4 run     11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

Untracked preserved: `.a2-backups/`, `src/foodTest/`, `tools/a2n/A2NavHarness.java.partial`,
`tools/a2n/consultar-c4.ps1`, `tools/a2n/lanzar-c4.ps1`, `tools/a5-closure/`.

Static validation only. A2 remains OPEN; C4 not revalidated; D3 BUILD runtime still unmeasured.
