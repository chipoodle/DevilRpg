# A2 Phase 33 — snap churn geometry diagnosis (2026-11-18)

## 1. Result

**COMPLETE — ROOT_CAUSE_CONFIRMED for the common churn mechanism.**

All four looped attackers follow the same pattern:

1. A verified snap completes in a supported cell.
2. The next route begins with a DROP node.
3. The executor calls `Navigation.moveTo` directly to the **landing center**, bypassing the
   supported intermediate WALK/STEP node in the accepted path.
4. The mob's center crosses an X (or Z) block boundary into an adjacent **unsupported** column.
5. The source predicate becomes `feetValid=false/support=false`; a new source snap pulls it back.
6. The route restarts and repeats at the next step.

This is a **self-induced executor/planner trajectory disagreement**, not a new terrain change and not a
Phase 29/31 regression.

- Branch: `feature/autonomous-village`
- Initial/final HEAD: `03017bba55c5d90cfa6ffb1b1e278b84d76ffcf7`
- Production: `ec6aa339cf4abd1691092591c1379792ffdc1575`
- Run: `a2n-20261117-190320`
- No new runtime; no production/test change.

## 2. Code path

`NavExecutor` DROP case:

```java
Vec3 aim = new Vec3(node.pos().getX() + 0.5D, node.pos().getY(),
                    node.pos().getZ() + 0.5D);       // line 4255
...
mob.getNavigation().moveTo(aim.x, aim.y, aim.z, 0.9D); // line 4605
traceActuatorCommand("DROP_APPROACH", ...);
trazarComandoMovimiento(node, "DROP", aim.x, aim.y, aim.z, 0.9D);
```

`aim` is the **landing cell center**, not the next accepted path node. The accepted path may contain a
supported intermediate cell before that landing. `Navigation.moveTo` heads straight for the landing,
and vanilla movement cuts the corner through the unsupported adjacent column.

The executor then evaluates source validity from `mob.blockPosition()`; the center has crossed the
boundary, so `hasSupport`/`esPieValido` fail and Phase 29 starts a new snap.

## 3. Per-attacker first cycle

### IDX3 `ded4119b-1f8d-4fc7-a97f-36274a0a1e7c`

```text
t110 MOVE_COMMAND reason=DROP node=606,95,600
     requested=606.50,95.00,600.50
     pos=604.14,96.00,600.50
     path nodes=604,96,600(support=grass) -> 605,96,600(grass) -> 606,95,600(grass)

t130 DROP_TAKEOFF_SOURCE_CELL_INVALID
     current=606,96,600 exact=606.015,96.000,600.500
     feetValid=false support=false candidate=605,96,600
```

The path includes the supported intermediate `605,96,600`, but the command targets the landing
`606.5`. The center reaches x=606.015 while still at y96, where the column is unsupported.

### IDX4 `c5ec81bb-0790-4c60-9d48-512621d95977`

```text
t64 MOVE_COMMAND reason=DROP node=568,119,532
    requested=568.50,119.00,532.50
    pos=567.37,122.00,533.64
    path nodes=567,122,533(leaves) -> 567,122,532(leaves) -> 568,119,532(leaves)

t84 DROP_TAKEOFF_SOURCE_CELL_CANDIDATE
    from=568,122,532 candidate=567,122,532
    candidate feet/support/fit=true

t84+ source invalid path; SNAP_COMPLETE t120 candidate=567,122,532
```

The supported intermediate is `567,122,532`; direct target `568.5` pulls the center across x=568
into the unsupported `568,122,532` column.

### IDX5 `05e3bcae-217f-4332-a67e-7e606c8af6a7`

```text
t3 MOVE_COMMAND reason=DROP node=596,86,466
   requested=596.50,86.00,466.50
   pos=594.50,87.00,466.50
   path nodes=594,87,466(coarse_dirt) -> 595,87,466(coarse_dirt) -> 596,86,466(coarse_dirt)

t530 DROP_TAKEOFF_SOURCE_CELL_INVALID
    current=596,87,466 exact=596.041,87.000,466.454
    feetValid=false support=false candidate=595,87,466
```

The mob crosses x=596 while still at y87; the supported intermediate is `595,87,466`.

### IDX8 `23c25c6d-85ac-42a2-a664-53b462ea1b5a`

```text
t182 MOVE_COMMAND reason=DROP node=746,63,534
     requested=746.50,63.00,534.50
     pos=747.69,64.00,534.60
     path nodes=747,64,534(cobblestone) -> 746,63,534(ice)

t390 DROP_TAKEOFF_SOURCE_CELL_INVALID
     current=746,64,534 exact=746.956,64.000,534.547
     feetValid=false support=false candidate=747,64,534
```

The mob crosses x=746 while still at y64, before the planned descent; the supported cell is
`747,64,534`.

## 4. Transition table

| Field | At SNAP_COMPLETE | After route start | At next invalidation |
|---|---|---|---|
| IDX4 position | `567.367,122.000,533.639` | `567,122,533` cursor0 | `568.050,122.000,531.577` |
| Candidate | `567,122,533` | — | `567,122,531` |
| Support | true | true at source | **false** at live cell |
| Feet | valid | valid | **false** |
| Real AABB fit | true | true | `true` (no collision) |
| Grounded | true | true | true |
| Center distance | ≤0.20 | 0 | outside candidate |
| Invalidation | — | DROP target landed in adjacent column | source snap |

The AABB fit remains `true`; the failure is **support under the crossed integer cell**, not collision.
This is why sweeping shows `collision=false` and why the snap can always pull back.

## 5. Cycle timing

| idx | resets | first | last | typical gap | net displacement |
|---|---:|---:|---:|---:|---:|
| 3 | 139 | t163 | t5999 | ~42–43 | ~11 |
| 4 | 135 | t62 | t5982 | ~43–58 | ~6 |
| 5 | 141 | t56 | t5977 | ~42–43 | ~1 |
| 8 | 136 | t254 | t5974 | ~42–44 | ~9 |

Each cycle is roughly 0.35-speed recentring over a partial block plus route start/replan overhead.
The ~43-tick period is the **snap recenter + next DROP approach** cycle, not a fixed timeout.

## 6. Comparison / common mechanism

All four:

- use a DROP first node whose landing is 2–3 cells from the current position;
- have accepted path intermediates that are supported;
- issue `Navigation.moveTo` directly to the landing center;
- cross into an unsupported adjacent X column at the same Y before the planned WALK/descent;
- recover with a source snap to the previous supported X cell;
- repeat at the next step.

Different terrain (leaves, grass, coarse dirt, ice) but identical executor behavior. This is one common
mechanism, not four independent terrain defects.

## 7. Phase 29/31 audit

```text
SNAP_MOVE_OWNER / SNAP_COMPLETE: each cycle
SNAP_POST_RESET: each completion
POST_SNAP_FIRST_PLAN: ACCEPT
POST_SNAP_ROUTE_START: TRAVERSING
POST_SNAP_GUARD: 0 for idx3/4/5/8
REPLAN_SIGNATURE_DECISION: 0 for idx3/4/5/8
```

No contradictory movement was observed while a snap was pending. Phase 29 exclusivity and Phase 31
reset are working; the churn is caused by the **next DROP approach**, not by stale state.

## 8. Signature sentinel

```text
lastRouteSignature = 0 still serves as post-snap sentinel.
The actual duplicate check is `acceptedRoute.signature() == lastRouteSignature`.
NavSnapPostResetPolicy.allowsFirstPostSnapSignature(...) is not used in that comparison.
A real zero signature could theoretically collide; no zero signature was observed.
Separate robustness observation only.
```

## 9. Classification

```text
IDX3: ROOT_CAUSE_CONFIRMED — DROP direct-target corner cut into unsupported x=606 at y96
IDX4: ROOT_CAUSE_CONFIRMED — direct target into unsupported x=568 at y122
IDX5: ROOT_CAUSE_CONFIRMED — direct target into unsupported x=596 at y87
IDX8: ROOT_CAUSE_CONFIRMED — direct target into unsupported x=746 at y64
```

Exact block identifier of the **unsupported landed column** is not logged; only support=false and the
accepted path's support blocks are available. The mechanism is nevertheless directly proven by the
movement target, first post-snap position, boundary crossing and next snap candidate.

## 10. Missing evidence

- Exact `BlockState` of each unsupported crossed cell (only `support=false` logged).
- Full collision shape list for the crossed columns.
- Whether vanilla movement cut the corner because of diagonal MoveControl versus path node skip; the
  command target bypassing intermediate nodes is confirmed.

## 11. Recommended next phase

A minimal correction would make DROP approach advance through the accepted path's supported
intermediate cell(s) before aiming at the landing, or refuse a direct landing target when the straight
segment crosses an unsupported column. Do not relax support/fit and do not change Phase 29/31.
No implementation in this phase.

## 12. Integrity

```text
PRODUCTION_MODIFIED_BY_HARNESS: false
TESTS_MODIFIED: false
ORIGINAL_WORLDS_MODIFIED: false
LOCAL_ARTIFACTS_PRESERVED: true
RUNTIME_EXECUTED_IN_PHASE33: false
```

A2 remains OPEN. Run 2 and Full 12K remain prohibited.
