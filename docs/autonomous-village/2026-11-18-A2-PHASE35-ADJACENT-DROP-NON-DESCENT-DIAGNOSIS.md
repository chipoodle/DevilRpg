# A2 Phase 35 — adjacent DROP non-descent diagnosis (2026-11-18)

## 1. Result

**COMPLETE — ROOT_CAUSE_CONFIRMED for IDX4.** An already-adjacent DROP calls
`mob.getNavigation().stop()`, then `aplicarAcercamientoAdyacente` with the **landing** as both node and
target. The nudge predicate uses Manhattan distance including the vertical drop; for IDX4 that is
`1+3+1 = 5 > maxDistance 3`, so no `MoveControl` command is issued. The mob never leaves the edge and
the DROP retries every tick. IDX8 is an independent BUILD failure; it never reaches DROP.

- Branch: `feature/autonomous-village`
- HEAD: `1d1e1e3e1f5286a6cb2f7df2413d06e6f5159fdc`
- Production: `ee0c4fe445af4cda1272488b4b0ffe712a3372cf`
- Run: `a2n-20261117-210328`
- No production/test change; no new runtime.

## 2. IDX4 evidence

UUID `094ff85e-d7cf-4361-bcfd-a41f65290042`.

```text
tick=3..5411 DROP_VALID nodo=567,119,534
 takeoff=567,122,534 dy=-3 natural
 pos=566,122,533 idxRuta=0/7
```

First cycle:

```text
t3 DROP_APPROACH_WAYPOINT_REACHED
   waypoint=566,122,533 landing=567,119,534
   exact=566.500,122.000,533.500

t3 MOVE_COMMAND reason=DROP node=567,119,534
   requested=567.50,119.00,534.50
   navDone=true navInProgress=false path=-
```

No `NAV_ADJACENT_NUDGE` was emitted for IDX4 in the entire run. No `DROP_DONE`.

At t5411 the same command is still being traced:

```text
attempts=22/40 failures=0
distanceToLanding=2.872
```

CSV positions remain `566.50,122.00,533.50`; onGround stays true, vertical velocity/position does not
change.

## 3. Code path

`NavExecutor` adjacent branch (Phase 34):

```java
mob.getNavigation().stop();
...
aplicarAcercamientoAdyacente(node, aim, 0.9D);
```

`aplicarAcercamientoAdyacente`:

```java
if (node == null || !mob.getNavigation().isDone()) return;
int distance = mob.blockPosition().distManhattan(node.pos());
int maxDistance = node.transition() == NavRoute.Transition.DROP ? 3 : 2;
if (!NavAdjacentApproachPolicy.shouldNudge(true, movementNode, distance, maxDistance,
        fits(node.pos()), hasSupport(node.pos()))) return;
mob.getMoveControl().setWantedPosition(aim.x, aim.y, aim.z, speed);
```

For IDX4:

```text
mob.blockPosition() = 566,122,533
node.pos()          = 567,119,534  (landing, not takeoff)
manhattan           = |1| + |3| + |1| = 5
maxDistance (DROP)  = 3
=> shouldNudge false
=> no MoveControl.setWantedPosition
```

`aim` is also the landing center `(567.5,119,534.5)`, not the take-off
`(567,122,534)`. `Navigation.stop()` has already removed all navigation, so with the nudge rejected
there is no remaining movement owner. The DROP command is traced but no physical movement is requested.

## 4. Where the chain stops

| Step | Result |
|---|---|
| DROP selected | yes |
| take-off computed | `567,122,534` (not current cell) |
| landing validated | yes (`DROP_VALID`, dy=-3) |
| physical command emitted | **no** (trace only; `path=-`, `navDone=true`) |
| horizontal movement | none |
| vertical descent | none |
| landing detected | no |
| cursor advanced | no |
| retry | every tick, attempt count rises, failures stay 0 |

## 5. Hypotheses

| Hypothesis | Result |
|---|---|
| A. No physical descent command | **CONFIRMED** |
| B. Navigation cancels/substitutes the command | Not the primary cause; `stop()` precedes and no replacement is issued |
| C. `stop()` leaves mob stopped without alternative | **CONFIRMED** |
| D. Take-off does not match physical position | **CONFIRMED** as contributing: take-off `567,122,534` is not current `566,122,533` and is never approached |
| E. Geometry requires a prior horizontal move | **CONFIRMED** — one-step move to take-off/edge is never commanded |
| F. Take-off predicate blocks despite `alwaysAdjacent=true` | **CONFIRMED** — nudge predicate uses landing Manhattan 5 > 3 |
| G. State repeats without no-progress detection | **CONFIRMED** — 22 attempts, 0 failures, same position |
| H. Vertical velocity/grounded contradict transition | Grounded remains true, vy0; no descent is requested |
| I. IDX8 reaches DROP and shares cause | **REJECTED** — IDX8 never reaches DROP |

## 6. IDX8 — independent

UUID `11a18d3a-0d45-4980-a3b5-52a0b21f290e`.

```text
intentosDeDescenso=0 fallosDeDescenso=0
lastAction=NONE
final=748,63,534 FAILED
reason={presupuesto agotado sin progreso (10/10) hacia 747,64,534}
```

It executes WALK routes only and fails on BUILD/local budget. It is **not** an adjacent-DROP case.

## 7. IDX3 / IDX5 comparison

- IDX3 `6c6d8329...`: approach needed a waypoint; 24 waypoint commands, 10 adjacent nudges, 14 snaps,
  final `605,96,600`.
- IDX5 `0691cdf2...`: 118 waypoint commands, 4 adjacent nudges, 4 snaps, net +21 blocks, final
  `615,83,465`.
- IDX4: no waypoint (already adjacent), 0 nudges, 0 snaps, 0 displacement.

The difference is exactly the Phase 34 branch: when a waypoint is selected, movement proceeds; when
`alreadyAdjacent=true`, the branch stops navigation and the nudge predicate then rejects the landing
node.

No `DROP_DONE` was observed for idx3/idx5 either in this run; they advance via other route steps, while
IDX4 is trapped because its route starts with the DROP.

## 8. Invariants

```text
PHASE29: preserved (exclusive snap; no contradictory movement).
PHASE31: preserved (reset/first plan/TRAVERSING; 0 resets for IDX4 because no snap is needed).
PHASE34: preserved (supported approach logic unchanged; bug is in adjacent branch nudge target).
```

## 9. Evidence missing

- Exact block state/collision shapes below `567,122,534`/landing; not required for the confirmed
  predicate rejection.
- No Stage B runtime required to establish the cause.

## 10. Recommended next phase

Phase 36 should make the adjacent branch either:

1. command a horizontal step to the **take-off cell** `(567,122,534)` at the current Y, then let the
   normal DROP/fall proceed; or
2. use a nudge distance predicate that separates horizontal adjacency from the vertical drop, while
   still requiring a supported take-off and valid landing.

Do not change Phase 29/31/34, budgets, support/fit, collision, tolerances or 8/8. No implementation in
this phase.

## 11. Integrity

```text
PRODUCTION_MODIFIED_BY_HARNESS: false
TESTS_MODIFIED: false
ORIGINAL_WORLDS_MODIFIED: false
LOCAL_ARTIFACTS_PRESERVED: true
RUNTIME_EXECUTED_IN_PHASE35: false
```

A2 remains OPEN. Run 2 and Full 12K remain prohibited.
