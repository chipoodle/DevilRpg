# A2 Phase 41 — DROP edge-step actuator fix (2026-11-18)

## 1. Result

**FIX VALIDATED; A2 remains 2/8 strict.**

The bounded DROP edge-step now transfers movement ownership to the real `MoveControl` after the
approach path ends at the platform edge. In the real A2 run:

- IDX4-like attacker descends physically from t17, lands at `567,119,534` at t24, and advances the cursor.
- IDX8-like attacker descends from t275 and reaches `746,63,534` at t279.
- No teleport, velocity injection, predicate relaxation or budget change.

A2 Run 1 remains 2/8 strict (idx1, idx6), so A2 remains OPEN.

- Branch: `feature/autonomous-village`
- Initial HEAD: `154a7ba5446531dd9323972efaf7542b4eb35fcd`
- Production before: `6aec35c651810084691f99611c48652fe0a881d7`
- Production after: `4379dbc250856b292b6cc2e3703b6aaf3bc8022b`
- A2 run: `a2n-20261118-140906`

## 2. Root cause (Phase 40)

PathNavigation created a 2-node path to the lower landing and ended at the edge, but
`MoveControl.hasWanted=false` in all 82 traced rows. With no horizontal owner, the AABB stayed over the
platform and `verticalCollision=true` held Y constant; no descent began.

## 3. Design / ownership

`NavDropEdgeStepPolicy`:

- activates only for `Transition.DROP`;
- requires Navigation done, grounded, not at landing, horizontal distance 1..3, supported/fitting
  takeoff and landing;
- computes full-AABB support columns (`fullyOffSupport`);
- timeout 60 ticks, no-progress 20 ticks;
- never applies to WALK/STEP_UP/JUMP_UP/BUILD/BREAK/BRIDGE.

`NavExecutor`:

1. On DROP and path done, stops Navigation.
2. Sets `DROP_EDGE_STEP_OWNER` to `MOVE_CONTROL`.
3. Commands `MoveControl.setWantedPosition(landing.x+0.5, currentY, landing.z+0.5, 0.9)` while any AABB
   column is supported.
4. When all AABB columns are off support, switches the target to landing Y and lets vanilla gravity and
   collision finish.
5. Clears ownership on landing/cursor advance, route change, timeout, no progress, route null and
   post-snap reset.
6. Phase29 snap guard still precedes traversal; no competing owner.

## 4. Files modified

```text
src/main/java/com/chipoodle/devilrpg/entity/nav/NavExecutor.java
src/main/java/com/chipoodle/devilrpg/entity/nav/NavDropEdgeStepPolicy.java
src/test/java/com/chipoodle/devilrpg/entity/nav/NavDropEdgeStepPolicyTest.java
build.gradle
```

## 5. Static

```text
NAV_DROP_EDGE_STEP_POLICY_TEST: OK (12 assertions)
NAV_ADJACENT_APPROACH_POLICY_TEST: OK (15 assertions)
NAV_DROP_APPROACH_POLICY_TEST: OK (10 assertions)
NAV_SNAP_OWNERSHIP / POST_RESET / SOURCE / ROUTE: PASS (existing)
gradlew test: PASS
lint_aldea.py --strict: PASS
git diff --check: PASS
compileJava -Pa2nDiagnostics=true: PASS
```

No budget, tolerance, support/fit, AABB or collision predicate changed.

## 6. Runtime evidence

### IDX4 `5ea20f89-7f94-47a5-9e55-46432e659ba8`

```text
t3  DROP_EDGE_STEP_SELECTED takeoff=566,122,533 landing=567,119,534
t17 DROP_EDGE_STEP_SUPPORT_CLEARED pos=567,122,534 support=false
t17 y=121.92
t20 y=121.23
t23 y=119.89
t24 DROP_LANDING_CONFIRMED landing=567,119,534 pos=567,119,534
t24 DROP_CURSOR_ADVANCE previousIndex=0 nextIndex=1
```

### IDX8 `b0cb68cd-f8e9-457b-a2e6-bbaa62215ec6`

```text
t182 DROP_EDGE_STEP_SELECTED takeoff=747,64,534 landing=746,63,534
t275 DROP_EDGE_STEP_SUPPORT_CLEARED
t275 y=63.92
t277 y=63.54
t279 y=63.00 at landing 746,63,534
```

No `DROP_EDGE_STEP_STALLED`; 3 aborts were route-change only.

### A2

```text
A2: 2/8 strict, 2/8 perimeter
idx1 t2233, idx6 t990
C4: PASS [2,0,0]
RUN2: not executed
FULL12K: not executed
```

## 7. IDX8 fixture limitation

The Phase40 IDX8-like fixture still produced no plan (`plan=-`); its repair was not completed in this
phase. Production IDX8 was validated independently in the real A2 run above. No fixture result is used
as evidence for the fix.

## 8. Integrity

```text
PRODUCTION_MODIFIED: true (authorized edge-step fix)
TESTS_MODIFIED: true
ORIGINAL_WORLDS_MODIFIED: false
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

## 9. Next action

Continue A2 diagnosis for the remaining non-entrants; the edge-step blocker is closed. A2 remains OPEN;
no Run 2 or 12K until five consecutive 8/8 strict runs.
