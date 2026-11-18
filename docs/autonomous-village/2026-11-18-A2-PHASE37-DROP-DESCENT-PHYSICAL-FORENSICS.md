# A2 Phase 37 — DROP descent physical forensics (2026-11-18)

## 1. Result

**PARTIALLY CONFIRMED.** The real command destination is the **landing center**, not the take-off.
IDX4 and IDX8 do produce a Navigation path and horizontal movement, but Y never changes and no
step-off occurs. IDX3/IDX5 do not emit `DROP_DESCENT_COMMAND` at all. The existing logs establish the
command/path failure boundary but do not contain per-tick MoveControl, collision shapes or post-travel
state, so the exact collision-vs-controller cause is unresolved. Stage B is required.

- Branch: `feature/autonomous-village`
- Initial HEAD: `4c002966a61953c235560b8cd7d6fead021f98fd`
- Production: `6aec35c651810084691f99611c48652fe0a881d7`
- Run: `a2n-20261118-042553`
- No production/test/runtime change.

## 2. Command destination discrepancy

Phase 36's prose said “horizontal step to the take-off”, but the real code is:

```java
if (nudge) {
    mob.getNavigation().moveTo(aim.x, aim.y, aim.z, 0.9D);
}
```

`aim` is built from `node.pos()` — the **landing**:

```java
Vec3 aim = new Vec3(node.pos().getX()+0.5, node.pos().getY(), node.pos().getZ()+0.5);
```

`DROP_DESCENT_COMMAND` is emitted immediately after this call and includes
`takeoff=approachWaypoint, landing=node.pos()`. It certifies intent/call, not physical descent.

There is no separate take-off move in the adjacent branch.

## 3. Per-attacker trace

### IDX4 `82c1b366-e2b8-4743-841b-d8a2e63997ec`

```text
t3 MOVE_COMMAND reason=DROP
   requested=567.50,119.00,534.50
   pos=566.50,122.00,533.50
   path=567,119,534 n=2 next=0 canReach=true
   nodes=566,122,533(leaves) | 567,119,534(leaves)
   navDone=false navInProgress=true

t3 DROP_DESCENT_COMMAND
   takeoff=566,122,533 landing=567,119,534 horizontal=2 dy=-3 grounded=true

t32 DROP_DESCENT_STALLED y=122.000 grounded=true
```

CSV positions: x/z move from `566.50,533.50` to about `567.24,533.96`, then stop; Y remains exactly
`122.00`. There is horizontal movement but no vertical movement/step-off.

### IDX8 `44ee1cc8-f320-463c-9ae0-7a22ad2a7f79`

```text
t182 MOVE_COMMAND reason=DROP
     requested=746.50,63.00,534.50
     pos=747.69,64.00,534.60
     path=746,63,534 n=2 next=0 canReach=true
     nodes=747,64,534(cobblestone) | 746,63,534(ice)

t182 DROP_DESCENT_COMMAND
     takeoff=747,64,534 landing=746,63,534 horizontal=1 dy=-1 grounded=true

t221 DROP_DESCENT_STALLED y=64.000 grounded=true
```

It reaches the target column horizontally but remains at y64; no descent.

### IDX3 `73b1294b...`

No `DROP_DESCENT_COMMAND` in this run. It uses `DROP_APPROACH_WAYPOINT_COMMAND` (not adjacent branch)
and its DROP target is `606,95,600`; final `604,96,600`, 22 attempts, 0 descent.

### IDX5 `a1d4f668...`

No `DROP_DESCENT_COMMAND`. Its first DROP move is to `596,86,466` with path n=1/n=2, but it remains
at `594,87,466`; final 22 attempts, 0 descent.

## 4. Path / navigation

| Case | path | nodes | navDone at command | horizontal movement | Y change |
|---|---|---|---|---|---|
| IDX4 | yes | current + landing | false | yes, ~0.8 | none |
| IDX8 | yes | current + landing | false | yes, ~0.8 | none |
| IDX3 | yes (waypoint) | current + target | false | yes to waypoint | none at first |
| IDX5 | yes | current/target | false then true | limited | none |

A path exists. Hypothesis A (“Navigation produces no path”) is **REJECTED** for IDX4/IDX8.

The path's final node is the lower landing; the first node is the current platform. The mob moves
horizontally toward the target but does not cross/leave the support edge; `DROP_DESCENT_STALLED`
records no Y change after 20 ticks.

## 5. Movement ownership

- `Navigation.moveTo(landing)` is called in the adjacent branch.
- `Navigation.stop()` is not called immediately before this call in the Phase 36 code.
- The next tick's DROP branch can call `moveTo` again (same destination).
- No log records MoveControl `wanted`/`hasWanted` or a competing goal.
- `MOVE_COMMAND` is trace-only; it shows `navDone=false, navInProgress=true` at t3 for IDX4.
- At later attempts `navDone=true` (IDX5 t13), but no descent occurs.

`MOVE_CONTROL_OVERRIDDEN` is **NOT LOGGED**; cannot be confirmed or rejected.

## 6. Physics/AABB

- Spawn AABB size: `0.60 x 1.95` (CSV).
- IDX4 moves horizontally to `x≈567.24,z≈533.96`, then stalls at the z=534 boundary.
- IDX8 moves to `x≈746.96`, at the x=746 boundary, Y remains64.
- `fits`/`support` for the landing were true at the DROP command.
- No collision-shape dump, block-state dump, or `deltaMovement` before/after `travel` exists.
- Exact BlockStates of the edge columns are not logged (only path support IDs for the landing).

`AABB`/`COLLISION_SHAPES`/`DELTA_MOVEMENT`: **NOT_LOGGED**.

## 7. Hypotheses

| Hypothesis | Result |
|---|---|
| A. Navigation does not produce a path | **REJECTED** for IDX4/IDX8 (path exists) |
| B. Path first waypoint does not allow leaving platform | **PARTIALLY_CONFIRMED** — horizontal movement stops at boundary |
| C. MoveControl target overwritten | **INSUFFICIENT_EVIDENCE** |
| D. `Navigation.stop()` cancels path | **REJECTED** for Phase 36 adjacent branch (no stop before moveTo) |
| E. AABB collides with edge block shape | **INSUFFICIENT_EVIDENCE** — plausible, no shape log |
| F. Support/clearance prevents horizontal exit | **INSUFFICIENT_EVIDENCE** — landing fit true, edge not dumped |
| G. Route considered done immediately without actuator | **PARTIALLY_CONFIRMED** — IDX5 navDone=true, IDX4 path progresses then stalls |
| H. Horizontal speed insufficient to cross edge | **PARTIALLY_CONFIRMED** — stops ~0.2 before target boundary |
| I. Same command repeats without progress | **CONFIRMED** — attempts/stalls repeat |
| J. Goal/other subsystem resets motion | **INSUFFICIENT_EVIDENCE** — no per-tick owner log |

## 8. IDX3/IDX5 distinction

They are **not** adjacent-descent cases in this run: no `DROP_DESCENT_COMMAND`, and they still use
`DROP_APPROACH_WAYPOINT_COMMAND`. Their 22 attempts are DROP retries from a waypoint path, not the
adjacent branch. Do not merge them with IDX4/IDX8.

## 9. Invariants

```text
PHASE29: preserved.
PHASE31: preserved.
PHASE34: preserved.
PHASE36: preserved (horizontal-distance predicate and command telemetry).
```

No production/test change in Phase 37.

## 10. Missing evidence / Stage B

Needed to distinguish collision from controller completion:

- per-tick `deltaMovement` before/after `travel`;
- MoveControl `hasWanted`, wanted XYZ, speed, operation;
- live Navigation path nodes/next index at each tick;
- actual AABB min/max and `horizontalCollision`/`verticalCollision`;
- block states and collision shapes at the take-off/landing edge.

No existing artifact contains these. **Stage B is required** and must not modify gameplay.

### Stage B proposal (not executed)

Observe IDX4 (`82c1b366...`) and IDX8 (`44ee1cc8...`) in a disposable world for the first DROP cycle.

Record for 40 ticks:
1. `mob.getX/Y/Z`, AABB, deltaMovement, onGround, horizontal/vertical collision, fallDistance.
2. Navigation `isDone`, `isInProgress`, path node count/next index.
3. MoveControl wanted XYZ/speed/operation before and after `travel`.
4. BlockState + collision shape at current, take-off, landing and the edge cell.
5. `DROP_DESCENT_COMMAND/STALLED` and any `moveTo/stop/setWantedPosition` caller.

Read-only instrumentation only; no movement/predicate changes. Use a new disposable world; original A2/C4
hashes must remain unchanged; headless, no GUI/focus/user intervention. Stop after 40 ticks or first
descent/landing. No Run 2 or 12K.

## 11. Integrity

```text
PRODUCTION_MODIFIED_BY_HARNESS: false
TESTS_MODIFIED: false
ORIGINAL_WORLDS_MODIFIED: false
LOCAL_ARTIFACTS_PRESERVED: true
```

## 12. Next action

Authorize a narrowly scoped Stage B diagnostic for IDX4/IDX8 first DROP cycle. Do not implement a
production change until collision/ownership is distinguished. A2 remains OPEN.

## 13. Artifacts

- `build/a2n/a2n-20261118-042553/nav-trace.log`
- `build/a2n/a2n-20261118-042553/srv.log`
- Phases 33–36 reports.
