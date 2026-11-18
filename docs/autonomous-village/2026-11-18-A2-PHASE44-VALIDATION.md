# A2 Phase 44 validation — non-adjacent DROP ownership (2026-11-18)

## 1. Result

**STATIC PASS; RUNTIME PARTIALLY VALIDATED; A2 3/8 strict.** The new approach owner works: IDX3, IDX4
and IDX5 now transfer Navigation→MoveControl, reach the supported waypoint, hand off to the Phase41
edge-step, descend, land and advance the route cursor. A2 still does not reach 8/8.

- Branch: `feature/autonomous-village`
- Initial HEAD: `fd2bbcd09a1e82708563bae3aa54f9542aa4ada3`
- Production SHA tested: `ed31b815193aa4fcde0b088f1a6d110270ba4a9d`
- Production content diff vs HEAD: empty for `src` and `build.gradle`
- Run: `a2n-20261118-145308`
- Production/tests not modified by Harness.

## 2. Static validation

```text
.\gradlew.bat test: PASS (exit 0)
NAV_DROP_APPROACH_OWNERSHIP_POLICY_TEST: OK (13 assertions)
NAV_DROP_EDGE_STEP_POLICY_TEST: OK (12 assertions)
NAV_ADJACENT_APPROACH_POLICY_TEST: OK (15 assertions)
python tools\lint_aldea.py --strict: PASS (exit 0)
git diff --check: PASS (exit 0)
.\gradlew.bat compileJava -Pa2nDiagnostics=true: PASS (exit 0)
```

Previous navigation tests remained green.

## 3. Runtime result

```text
A2: 3/8 strict, 3/8 perimeter
Strict: idx1 t559, idx5 t730, idx6 t650
C4: PASS [2,0,0]
Run2: not executed
Full12K: not executed
```

## 4. Approach ownership evidence

### IDX3 `ef6bf62b-0ddc-4a87-8218-c4f0cdbb6a32`

```text
t110 DROP_APPROACH_WAYPOINT_COMMAND waypoint=605,96,600 landing=606,95,600
     pathAccepted=true navDone=false exact=604.139,96.000,600.500
t111 DROP_APPROACH_OWNER_SELECTED owner=MOVE_CONTROL
     reason=navigation_done_before_waypoint distance=1.298
t121 DROP_APPROACH_OWNER_REACHED waypoint=605,96,600
     grounded=true support=true fit=true owner=MOVE_CONTROL
t121 DROP_EDGE_STEP_SELECTED takeoff=605,96,600 landing=606,95,600
t135 DROP_EDGE_STEP_SUPPORT_CLEARED
t136 DROP_LANDING_CONFIRMED landing=606,95,600
t136 DROP_CURSOR_ADVANCE previousIndex=11 nextIndex=12
```

No competing Navigation path is logged while the owner is `MOVE_CONTROL`.

### IDX3 totals

```text
DROP_APPROACH_OWNER_SELECTED=1  REACHED=1
DROP_EDGE_STEP_SELECTED=25      SUPPORT_CLEARED=25
DROP_LANDING_CONFIRMED=1        CURSOR_ADVANCE=1
Final 607,95,601 FAILED
```

It progressed physically but later hit `escalones=64/64`, `fallosEscalon=10/10`, build budget. This is
a downstream BUILD/step budget, not an approach-ownership failure.

### IDX4 `072cdd9b-61d4-48b8-a6ba-bb146cd486b6`

```text
OWNER_SELECTED=2 REACHED=4
EDGE_STEP_SELECTED=12 SUPPORT_CLEARED=11
LANDING_CONFIRMED=10 CURSOR_ADVANCE=10
Final 596,79,500 dist72.12 TRAVERSING
```

Repeated owner→edge-step→landing cycles succeeded. It ended just outside 71 (`dist=72.12`) with global
budget exhausted.

### IDX5 `fa989205-4daf-4fe5-823a-f64e28876620`

```text
WAYPOINT_COMMAND=9 OWNER_SELECTED=8 REACHED=9
EDGE_STEP_SELECTED=15 SUPPORT_CLEARED=14
LANDING_CONFIRMED=15 CURSOR_ADVANCE=15
Final 639,71,514 dist29.00 PLANNING
Strict entry t730
```

This is the first run where IDX5 entered strict; the ownership fix changed the outcome.

## 5. Other attackers

```text
IDX1 199ce94a... strict t559
IDX2 7d6e8ac7... no entry
IDX6 b2110733... strict t650
IDX7 f8f620ae... no entry
IDX8 a8c21371... no entry
```

IDX2/IDX7 remain independent classes from Phase42. IDX8 remains a later budget/step issue.

## 6. Regression / invariant audit

```text
PHASE29 snap ownership: preserved; no competing navigation during snap.
PHASE31 post-snap reset: preserved.
PHASE34 supported approach: preserved.
PHASE36 adjacent validation: preserved.
PHASE41 edge-step: preserved and used for every landing above.
No support/fit/collision/predicate/budget/tolerance change.
```

No unsafe column crossing appeared in the approach chain; edge-step support clearing occurs only after
the AABB crosses.

## 7. Failure classification

```text
APPROACH OWNERSHIP: FIXED for IDX3/4/5 (demonstrated).
A2 3/8: remaining failures are downstream/independent:
- IDX3: BUILD/step budget (64/64, 10/10).
- IDX4: global budget; stopped at dist72.12.
- IDX2: source recovery.
- IDX7: BUILD budget.
- IDX8: step/local budget.
```

## 8. Integrity

```text
PRODUCTION_MODIFIED: false
TESTS_MODIFIED: false
ORIGINAL_WORLDS_MODIFIED: false
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
RUN2_EXECUTED: false
FULL12K_EXECUTED: false
```

## 9. Next action

A2 remains OPEN. Continue from `a2n-20261118-145308`: prioritize the near-miss IDX4 (72.12) and the
IDX3 BUILD/step budget, then the independent IDX2/IDX7/IDX8 cases. Do not reopen the approach-owner or
Phase41 contracts. No Run2/Full12K until five consecutive 8/8 strict.
