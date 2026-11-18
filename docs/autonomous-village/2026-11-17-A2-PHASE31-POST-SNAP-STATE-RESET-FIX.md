# A2 Phase 31 — post-snap navigation state reset fix (2026-11-17)

## 1. Result

**FIX VALIDATED.** The pre-snap oscillation history and aborted-route signature are reset exactly
once when a verified source snap completes. The first post-snap plan is accepted and enters
TRAVERSING. Genuine later oscillation and duplicate-signature protection remain active.

- Branch: `feature/autonomous-village`
- Initial HEAD: `1e1530841389ed14cf30a2d4c4b59b3ed88d7823`
- Production before: `192d0c521f86efa39fe9ffe1d47348fcf7d4d5e6`
- Production after: `ec6aa339cf4abd1691092591c1379792ffdc1575`
- Runtime run: `a2n-20261117-190320` (new disposable world)

## 2. Production change

`NavExecutor.resetPostSnapNavigationState(validatedSource, now)`:

```text
recentCells.clear(); add(validatedSource)
oscillating = false
recentCellOccurrences = 1
lastRouteSignature = 0
postSnapFirstPlanPending = true
```

Called from both successful snap-release paths:

1. `resolverFuenteActualYReplanificar()` — after `SNAP_COMPLETE`, before `state=PLANNING`.
2. `iniciarRecuperacionFisicaOrigenDrop()` — after pending candidate clear/`SNAP_COMPLETE`.

New pure policy `NavSnapPostResetPolicy` supplies the fresh history/occurrence/signature values and
keeps duplicate comparison explicit.

Added telemetry:

- `SNAP_POST_RESET` — source, cells/oscillating/occurrences/signature before/after, reason.
- `POST_SNAP_FIRST_PLAN` — start, goal, plannedFrom, signature, decision.
- `POST_SNAP_ROUTE_START` — signature, cursor, start, state=TRAVERSING.
- `POST_SNAP_GUARD` — guard context with `postSnapWindow`.
- `REPLAN_SIGNATURE_DECISION` — candidate/previous signature and counter before/after.

## 3. Invariants

Unchanged:

- 8/8 replan limit;
- A* and recovery budgets;
- support, feet, fit, real AABB, collision;
- 0.20 snap center tolerance;
- Phase 29 exclusive snap ownership;
- no teleport/force/partial routes;
- no BREAK/BUILD/BRIDGE;
- strategic objective/mission.

## 4. Static validation

```text
gradlew test: PASS
NAV_SNAP_POST_RESET_POLICY_TEST: OK (12 assertions)
NAV_SNAP_OWNERSHIP_POLICY_TEST: OK (12 assertions)
NAV_DROP_TAKEOFF_POLICY_TEST: OK (51 assertions)
NAV_SOURCE_CELL_POLICY_TEST: OK (14 assertions)
lint_aldea.py --strict: PASS
git diff --check: PASS
compileJava -Pa2nDiagnostics=true: PASS
```

Test file: `NavSnapPostResetPolicyTest` (12 assertions), registered in `build.gradle`.

## 5. Runtime

Run `a2n-20261117-190320`, A2 Run 1 only.

```text
A2: 2/8 strict, 2/8 perimeter
Strict: idx1 t593, idx6 t650
C4: PASS [1,0,0]
Run 2: not executed
Full 12K: not executed
```

Telemetry totals:

```text
SNAP_POST_RESET:           580
POST_SNAP_FIRST_PLAN:      579
POST_SNAP_ROUTE_START:     579
POST_SNAP_GUARD:             5
REPLAN_SIGNATURE_DECISION:   1
```

IDX4 `c5ec81bb-0790-4c60-9d48-512621d95977`:

```text
t5939 SNAP_POST_RESET source=566,123,526
      recentCellsBefore=3 after=1
      oscillatingBefore=false after=false
      occurrencesBefore=2 after=1
      signatureBefore=0 after=0

t5940 POST_SNAP_FIRST_PLAN
      start=566,123,526 goal=569,119,525
      signature=-223992152 decision=ACCEPT state=PLANNING

t5941 POST_SNAP_ROUTE_START
      signature=-223992152 routeIndex=0
      start=566,123,526 state=TRAVERSING
```

A second completion follows the same sequence at t5982–5984. No `POST_SNAP_GUARD` appears for IDX4
after the reset.

The five `POST_SNAP_GUARD` events belong to genuinely later oscillation in other attackers
(`ff5c31e0`, `7611598e`), and the one `REPLAN_SIGNATURE_DECISION` is a normal duplicate rejection after
that later oscillation. This confirms the reset does not disable the guards.

## 6. Phase 30 scenario

The exact coordinates `571,115,533 → 571,116,528` did not recur in this disposable run; IDX4 took
another path. The equivalent contract was validated:

1. reset fires once per verified completion;
2. first plan accepted;
3. first route enters TRAVERSING;
4. no pre-snap guard abort;
5. later guards still active;
6. duplicate protection still active;
7. 8/8 limit unchanged.

## 7. Integrity

```text
PRODUCTION_MODIFIED_BY_HARNESS: true (authorized Phase 31 fix)
ORIGINAL_WORLDS_MODIFIED: false
LOCAL_ARTIFACTS_PRESERVED: true
TEMPORARY_WORLD_USED: YES — a2n-20261117-190320 disposable world
```

```text
A2 SHA-256 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 SHA-256 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

## 8. Commits

```text
ec6aa339 fix(nav): reset stale oscillation state after verified snap
```

Documentation commit follows.

## 9. Limitations / next action

- Exact Phase 30 geometry was not reproduced.
- A2 remains 2/8 strict; the post-snap state defect is fixed, but other independent failures remain.
- Phase 32 should diagnose the current A2 non-entrants without weakening the Phase 29/31 contracts.
- Run 2 and 12K remain prohibited.
