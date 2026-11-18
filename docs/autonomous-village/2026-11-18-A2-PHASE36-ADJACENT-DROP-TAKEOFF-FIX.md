# A2 Phase 36 — adjacent DROP take-off execution (2026-11-18)

## 1. Result

**FAIL / PARTIALLY VALIDATED.** The adjacent branch now emits a horizontal DROP command using
horizontal distance instead of Manhattan-with-fall. But the mob still does not physically descend:
`DROP_DESCENT_COMMAND` is followed by `DROP_DESCENT_STALLED`, never `DROP_DESCENT_STARTED`.
A2 remains 2/8 strict.

- Branch: `feature/autonomous-village`
- Initial HEAD: `1f2fa3863ea0750b4725982deed3145ae4287ec2`
- Production before: `ee0c4fe445af4cda1272488b4b0ffe712a3372cf`
- Production after: `6aec35c651810084691f99611c48652fe0a881d7`
- Runtime run: `a2n-20261118-042553`
- No original-world modification.

## 2. Implementation

`NavAdjacentApproachPolicy.shouldNudgeDrop(navigationDone, horizontalDistance, maxDistance,
targetFits, hasSupport)` evaluates only X/Z displacement, leaving the landing's support/fit checks
unchanged. The DROP branch uses it when `alreadyAdjacent`.

`NavExecutor` adjacent branch now issues `Navigation.moveTo` to the validated landing center and adds:

- `DROP_TAKEOFF_APPROACH_SELECTED`
- `DROP_TAKEOFF_APPROACH_COMMAND`
- `DROP_TAKEOFF_APPROACH_REACHED`
- `DROP_TAKEOFF_APPROACH_REJECTED`
- `DROP_DESCENT_COMMAND`
- `DROP_DESCENT_STARTED` (requires physical Y decrease)
- `DROP_DESCENT_STALLED` (20 ticks without Y decrease)
- `DROP_LANDING_CONFIRMED`

No budget, support, fit, collision, tolerance or Phase 29/31/34 semantics changed.

## 3. Static validation

```text
gradlew test: PASS
NAV_ADJACENT_APPROACH_POLICY_TEST: OK (15 assertions)
NAV_DROP_APPROACH_POLICY_TEST: OK (10 assertions)
NAV_DROP_TAKEOFF_POLICY_TEST: OK (51 assertions)
NAV_SOURCE_CELL_POLICY_TEST: OK (14 assertions)
NAV_SNAP_OWNERSHIP_POLICY_TEST: OK (12 assertions)
NAV_SNAP_POST_RESET_POLICY_TEST: OK (12 assertions)
NAV_ROUTE_NODE_POLICY_TEST: OK (6 assertions)
lint_aldea.py --strict: PASS
git diff --check: PASS
compileJava -Pa2nDiagnostics=true: PASS
```

## 4. Runtime

Run `a2n-20261118-042553`.

```text
A2: 2/8 strict, 2/8 perimeter
Strict: idx1 t593, idx6 t990
C4: PASS [2,0,0]
Run 2: not executed
Full 12K: not executed
```

### IDX4

UUID `82c1b366-e2b8-4743-841b-d8a2e63997ec`.

```text
DROP_DESCENT_COMMAND: 54
DROP_DESCENT_STARTED:  0
DROP_DESCENT_STALLED:  5
DROP_LANDING_CONFIRMED: 0
final pos=567,122,530 RECOVERING
intentosDeDescenso=22 fallosDeDescenso=3
```

Each command is followed by no Y change and `grounded=true`.

### IDX8

UUID `44ee1cc8-f320-463c-9ae0-7a22ad2a7f79`.

```text
DROP_DESCENT_COMMAND: 824
DROP_DESCENT_STARTED:  0
DROP_DESCENT_STALLED:  136
DROP_LANDING_CONFIRMED: 0
final pos=747,64,534 TRAVERSING
```

It does reach DROP and emits commands, but never descends. It is no longer purely independent BUILD
failure in this run; it is also blocked at the same physical descent step.

### IDX3 / IDX5

```text
IDX3 73b1294b...: 22 attempts, 0 descent, final604,96,600 RECOVERING
IDX5 a1d4f668...: 22 attempts, 0 descent, final594,87,466 RECOVERING
```

Neither reaches `DROP_DESCENT_STARTED`; both retry from the edge.

## 5. Interpretation

The command-emission defect from Phase 35 is fixed: the adjacent branch no longer stops before
requesting movement, and horizontal distance no longer includes the fall.

The remaining blocker is physical: `Navigation.moveTo` to the landing does not make the mob leave
the supporting edge, and `MoveControl`/navigation reports no vertical progress. This is not the old
Manhattan predicate rejection. The executor retries the command every tick (or in bursts) without
`DROP_DESCENT_STARTED`; `DROP_DESCENT_STALLED` now records it.

Why the entity does not step off/fall is not established by the current trace. No block-state or
collision-shape dump exists for the edge. Possible causes (unconfirmed): path target below the current
support is not treated as traversable by vanilla navigation; collision at the edge; or MoveControl
keeps the body on the support block.

## 6. Invariants

```text
PHASE29: preserved.
PHASE31: preserved.
PHASE34: preserved; supported approach selection unchanged.
Snap churn: not reintroduced (0 resets for IDX4 in this run).
```

## 7. Integrity

```text
PRODUCTION_MODIFIED_BY_HARNESS: true (authorized Phase 36 change)
TESTS_MODIFIED: true
ORIGINAL_WORLDS_MODIFIED: false
GUI_OR_FOCUS_INTERVENTION: none
A2 SHA-256 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 SHA-256 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

## 8. Next action

Phase 37 must diagnose why the validated adjacent `Navigation.moveTo(landing)` produces no physical
vertical movement/step-off, despite `DROP_DESCENT_COMMAND` and `grounded=true`. Required evidence:
per-tick MoveControl target/result, navigation path nodes, actual AABB/edge collision, and block states
around the take-off/landing. Do not change budgets or physical predicates. A2 remains OPEN; Run 2 and
12K remain prohibited.
