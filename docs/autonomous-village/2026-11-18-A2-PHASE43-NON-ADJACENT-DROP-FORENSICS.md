# A2 Phase 43 — non-adjacent DROP approach/landing forensics (2026-11-18)

## 1. Result

**COMPLETE.** IDX3, IDX5 and IDX4 all fail in the non-adjacent DROP approach, after the supported
waypoint/landing is selected but before a physical approach movement is owned. Phase 41 is not involved:
none of these cases is an already-adjacent edge-step.

- Branch: `feature/autonomous-village`
- HEAD: `b624546c8e8174092419bd8d3022845e1b10ae4c`
- Production: `4379dbc250856b292b6cc2e3703b6aaf3bc8022b`
- Run: `a2n-20261118-140906`
- No production/test/world change.

## 2. IDX3 `7e20a00f-1101-48af-9af7-b4cb4790d353`

```text
final pos=604.28,96.00,600.52  state=RECOVERING
last action 606,95,600:DROP-EN-CURSO
22 attempts / 40; replans exhausted 8/8
NAV_FAIL at 603,96,600: replans agotados (8/8)
last tick 5999: RECOVER_DECISION WAIT remaining=340 nextRecoveryAt=6339
```

Last DROP approach:

```text
t3192 MOVE_COMMAND DROP node=606,95,600 requested=606.5,95,600.5
      pos=604.28,96,600.52
      navDone=true path=605,96,600 n=1 next=1 canReach=true nodes=<empty>
      requestedCell=606,95,600 support=grass
t3196 DROP_TAKEOFF_APPROACH_SELECTED takeoff=605,96,600 landing=606,95,600
      horizontal=2
t3196 DROP_APPROACH_WAYPOINT_COMMAND waypoint=605,96,600
      exact=604.277,96.000,600.523
```

Positions t5990–5999 are completely frozen at `604.28,96,600.52`, so the commanded waypoint
`605,96,600` is never reached. The last path shown is a one-node path (`n=1,next=1`) and no movement
owner advances the mob.

```text
ROOT_CAUSE: APPROACH_MOVEMENT_NOT_OWNED
(approach path terminates at a one-node path / no active movement)
SYMPTOM: 8/8 replans exhausted, RECOVERING wait
MISSING_EVIDENCE: path status after the waypoint command at t3196
```

## 3. IDX5 `fd459775-58ce-464d-809d-bb59f7870e97`

```text
final pos=594.50,87.00,466.50 state=RECOVERING
last action 596,86,466:DROP-EN-CURSO
22 attempts; replans exhausted 8/8
NAV_FAIL at 594,87,466: replans agotados (8/8)
last tick 5999: RECOVER_DECISION WAIT remaining=187 nextRecoveryAt=6186
```

Last DROP approach:

```text
t3010 MOVE_COMMAND DROP node=596,86,466 requested=596.5,86,466.5
      pos=594.50,87,466.50
      navDone=true path=595,87,466 n=1 next=1 canReach=true nodes=<empty>
      requestedCell=596,86,466 support=coarse_dirt
t3011 DROP_TAKEOFF_APPROACH_SELECTED takeoff=595,87,466 landing=596,86,466
      horizontal=2
t3011 DROP_APPROACH_WAYPOINT_COMMAND waypoint=595,87,466
      exact=594.500,87.000,466.500
```

Positions t5990–5999 are frozen exactly at `594.50,87,466.50`; the waypoint `595,87,466` is never
reached. Same one-node path/no-owner symptom.

```text
ROOT_CAUSE: APPROACH_MOVEMENT_NOT_OWNED
SYMPTOM: 8/8 replans exhausted, RECOVERING wait
MISSING_EVIDENCE: path result immediately after the t3011 waypoint command
```

## 4. IDX4 `5ea20f89-7f94-47a5-9e55-46432e659ba8`

Phase41 edge-step succeeded at t24 (`567,119,534`). The later failure is a different, non-adjacent
DROP node:

```text
t5887–t5997 DROP_LANDING_PROGRESS
 landing=577,111,522
 liveCell=575,112,524
 exact=575.655,112.000,524.347
 routeIndex=4
 onGround=true
 landingHeightReached=false
 liveCellSupported=true
 validLanding=false
 withinTolerance=false
 distanceSqr=7.065  (~2.66 blocks)
 landingFits=true
 landingSupport=true
 delta=0.000,-0.078,0.000
```

Last DROP movement:

```text
t591 MOVE_COMMAND DROP node=571,113,525 requested=571.5,113,525.5
     pos=571.44,116,527.26
     navDone=true path=571,116,526 n=1 next=1 canReach=true nodes=<empty>
```

The DROP landing itself is valid geometrically (`fits=true`, `support=true`). `validLanding=false` is
decomposed as **`withinTolerance=false`** and `landingHeightReached=false` (Y112 vs landing111), not a
collision or support failure. The last path is a completed one-node path to a different/stale node;
there is no active movement owner to carry the mob from `575,112,524` to `577,111,522`.

```text
ROOT_CAUSE: APPROACH_PATH_TERMINATES_EARLY / APPROACH_MOVEMENT_NOT_OWNED
(landing geometry valid; no owned movement from distanceSqr 7.065)
SYMPTOM: validLanding=false, no terminal NAV_FAIL, TRAVERSING until timeout
MISSING_EVIDENCE: exact moveTo/path result for landing577 after tick591
```

## 5. Cross-case comparison

| Case | Landing valid | Last path | Movement owner | Classification |
|---|---|---|---|---|
| IDX3 | DROP target valid | one-node `n=1,next=1` | none after waypoint | APPROACH_MOVEMENT_NOT_OWNED |
| IDX5 | DROP target valid | one-node `n=1,next=1` | none after waypoint | APPROACH_MOVEMENT_NOT_OWNED |
| IDX4 | `fits/support=true`, outside tolerance | one-node to stale node | none toward 577 | APPROACH_MOVEMENT_NOT_OWNED |

Common defect: **the non-adjacent DROP approach has no continuous movement owner**. The planner accepts
the DROP/waypoint, but the vanilla path returns a one-node/completed path and no MoveControl/edge-step
takes over. Phase41's edge-step only activates when already adjacent, so it correctly does not run here.

Not the cause:

- landing support/fit (IDX4 both true);
- protected/blocked landing (no evidence);
- fall-column blocked (no evidence);
- Phase29/31 snap (no pending snap in these final windows);
- Phase41 edge-step (not applicable).

## 6. Missing evidence

- Path/MoveControl state immediately after each waypoint `moveTo` (logs only show the prior `n=1` path).
- Exact block-state/collision dump for `577,111,522` and the IDX3/5 intermediate cells.
- No new runtime was executed; no extrapolation.

## 7. Recommended next phase

Phase 44 should add a bounded **non-adjacent DROP approach owner**: after the DROP path returns
completed without reaching the waypoint/landing, take MoveControl ownership and advance to the supported
approach cell, then reuse the Phase41 adjacent edge-step. Scope: DROP only; no planner/budget/predicate
changes; no reopening Phase29/31/34/36/41.

## 8. Integrity

```text
PRODUCTION_MODIFIED: false
TESTS_MODIFIED: false
WORLDS_MODIFIED: false
RUN2_EXECUTED: false
FULL12K_EXECUTED: false
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

A2 remains OPEN.
