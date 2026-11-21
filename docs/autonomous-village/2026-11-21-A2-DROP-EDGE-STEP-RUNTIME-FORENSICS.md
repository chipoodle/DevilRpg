# A2 DROP edge-step runtime forensics — IDX6 (2026-11-21)

## 1. Result

**COMPLETE — read-only.** IDX6’s 18 edge-step aborts are **horizontal-stagnation aborts**, not invalid
landing or support rejection. The repeated 26-tick cadence and frozen coordinates explain the route-guard
exhaustion.

- RUN_ID: `a2n-20261120-173629`
- SOURCE_HEAD: `54e3c9a8b11e2b6125a589ee64795db5a98c62fb`
- IDX6 UUID: `6452b8c2-cffb-472b-a0d3-35a45dea813a`
- No runtime, Gradle, tests or world mutation.

## 2. DROP lifecycle

```text
DROP_START 661,72,459 -> 661,71,459   DONE t253, cursor 0->1, routeLength11
DROP_START 658,71,471 -> 658,70,471   started, no DONE (final)
```

`DROP_EDGE_STEP_ABORTED` total: 18

```text
17 no_progress
 1 route_node_changed (t198)
```

Abort ticks:

```text
198 route_node_changed
918 944 970 996 1022 1048 1074 1100 1126 1152 1178 1204 1230 1257 1317 1377 1437
```

The 17 `no_progress` aborts recur every ~26 ticks (20-tick threshold plus retry cadence). The initial
`route_node_changed` at t198 is separate and occurred during the first DROP approach.

## 3. Frozen coordinates

From `DROP_EDGE_STEP_PROGRESS` around the final DROP:

```text
t892 pos=658.741,71,470.700 horizontal=0.836 grounded=true
t897 pos=658.625,71,470.700 horizontal=0.810 grounded=true
t902 pos=658.559,71,470.700 horizontal=0.802 grounded=true
t907 pos=658.530,71,470.700 horizontal=0.801 grounded=true
t912 pos=658.523,71,470.700 horizontal=0.800 grounded=true
t917 pos=658.518,71,470.700 horizontal=0.800 grounded=true
t918 ABORTED no_progress pos=658,71,470
```

`AABB maxZ = 471.0`; the entity is pressed exactly at the block boundary of the landing column. It remains
`grounded=true`, so `DROP_EDGE_STEP_SUPPORT_CLEARED` never fires. Each retry restarts at the same place and
aborts again.

## 4. Source explanation

`activateDropEdgeStep` resets:

```text
dropEdgeStepTicks=0
dropEdgeStepNoProgress=0
dropEdgeStepBestDistance=MAX_VALUE
```

`tickDropEdgeStep`:

```text
offSupport = fullyOffSupport(full AABB) || mobY < takeoffY - 0.05
if offSupport -> descent command; deactivate
distance = horizontal distance to landing center
if distance < bestDistance - 0.01 -> reset noProgress
else -> noProgress++
if noProgress > 20 -> abort no_progress
if ticks > 60 -> abort timeout
```

The progress metric is horizontal distance only. The support-release gate requires the full AABB to have
no support **or** the entity to drop below takeoff. When the entity is blocked at the lip, neither occurs
and horizontal distance stays 0.800, so the policy aborts at 21 ticks.

The exact collision/controller reason the AABB stops at `maxZ=471.0` is **not proven** by the trace.
Confirmed: no horizontal progress, no vertical descent, support never released, repeated commands.

## 5. Route-guard relationship

Route guard exhausted `12/12` at t1232. The 17 no-progress aborts are earlier/concurrent and each returns
`OBSTRUCTED`; the executor replans/re-enters the same DROP. The guard exhaustion is therefore a
**consequence** of repeated edge-step failure, not an independent root cause. Aborts continue after t1232
during terminal/rearm attempts (t1257, 1317, 1377, 1437).

## 6. Confirmed vs hypothesis

Confirmed:

- 18 aborts: 17 `no_progress`, 1 `route_node_changed`.
- The final DROP never completed.
- Horizontal position freezes at 0.800 and `grounded=true`.
- No support-release event and no landing rejection.
- No invalid takeoff/landing was logged for this final DROP.
- Route guard exhaustion follows the repeated no-progress aborts.

Hypothesis:

- The entity is colliding with the landing-column lip, so MoveControl cannot advance into
  `z=471.5`; the exact collision block is not in the event.
- The current command targets `mob.getY()` while horizontally approaching a lower landing, so the
  controller may not step down until support is released.

## 7. Required deterministic tests

```text
- constant horizontal distance + grounded + supported -> noProgress reaches 21 and aborts
- horizontal improvement >0.01 resets noProgress
- full AABB off-support before 20 ticks clears to descent
- vertical drop below takeoff clears to descent
- route-node change resets the edge-step counters
- one-block adjacent supported landing is not forced through a blocked lip
- terminal abort does not report an invalid landing
```

Do not force drops through leaves or relax landing/support checks. A2 remains OPEN; no runtime authorized.
