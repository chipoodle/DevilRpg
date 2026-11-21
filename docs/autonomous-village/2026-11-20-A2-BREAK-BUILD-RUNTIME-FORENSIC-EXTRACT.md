# A2 BREAK/BUILD runtime forensic extract (2026-11-20 run)

## 1. Scope

Read-only extraction from `build/a2n/a2n-20261120-173629/`. No Gradle, Minecraft, runtime or tests were
executed. Logs were opened read-only.

- BRANCH: `feature/autonomous-village`
- ANALYZED_HEAD: `54e3c9a8b11e2b6125a589ee64795db5a98c62fb`
- RUN_ID: `a2n-20261120-173629`
- WORLD: `build/a2n/a2n-20261120-173629/game/a2n`

Log hashes before and after analysis are identical (see §9).

## 2. UUID ↔ IDX

```text
IDX1 602a4360-66f7-4ba4-90ac-2ca39a247594
IDX2 7cbf740f-b634-40f8-b4f9-96e12b1c304f
IDX3 0967d6df-1a57-4186-bfbe-22bdb3cd66a3
IDX4 7e3c6aa8-0c70-4af7-9570-e418d41958c9
IDX5 e2ffac40-ed66-4f78-a94e-936c0429a492
IDX6 6452b8c2-cffb-472b-a0d3-35a45dea813a
IDX7 d34840f0-8e58-4931-b402-ad7b2e0cec01
IDX8 3849136c-82bd-4913-9de1-22cb6b4f4a7c
```

## 3. BREAK candidate/LOS correlation

`BREAK_STAND_CANDIDATE` and `BREAK_LOS_DIAG` lines do not carry `uuid`. All 128 candidate lines occur at
trace lines 15–223 and all 128 LOS lines at 24–334, inside IDX1’s opening
`GLOBAL_FIRST_EXPANSION_START start=728,64,600`. No BREAK event exists later or for another UUID.
Therefore **all 128 stand checks belong to IDX1**; IDX2–IDX8 emitted none.

Candidate targets and stands:

- 16 targets: `(727..729, 61..63, 599..601)`.
- Each target has its 8 horizontal neighbors at **stand Y=64** = the mob’s actual feet height.
- The current cell was included: `stand=728,64,600` for target `729,61,600` was evaluated, but rejected
  by action reach (target 3 blocks below).

Candidate predicate counts:

```text
64 OUT_OF_ACTION_REACH       (no LOS call)
32 NOT_SINGLE_STEP_REACHABLE (28 no LOS; 4 have LOS from another validation)
32 ACTION_NOT_EXECUTABLE     (30 matched RAYCAST_REJECT; 2 matched PRECONDITION_REJECT)
```

Exact combined outcomes for the 32 candidates that reached the action predicate:

```text
30 ACTION_NOT_EXECUTABLE + RAYCAST_REJECT
 2 ACTION_NOT_EXECUTABLE + PRECONDITION_REJECT (standFits=false)
```

The remaining 96 LOS lines have no matching first-expansion candidate; they are later route/waypoint
validation calls for nearby targets (725/726, y62/63) and cannot be attributed to a specific caller
because the event has no uuid/caller field. They are not additional first-expansion candidates.

### Per-target exact stand reasons

Stand order: `NW, N, NE, W, E, SW, S, SE` (all at Y=64). `O`=OUT_OF_ACTION_REACH,
`N`=NOT_SINGLE_STEP_REACHABLE, `A`=ACTION_NOT_EXECUTABLE; `/R`=raycast reject, `/P`=precondition
(standFits=false).

| Target | NW | N | NE | W | E | SW | S | SE |
|---|---|---|---|---|---|---|---|---|
| 727,61,599 | O | O | O | O | O | O | O | O |
| 727,61,600 | O | O | O | O | O | O | O | O |
| 727,61,601 | O | O | O | O | O | O | O | O |
| 727,62,600 | N | N | N | A/R | A/P | A/R | A/R | A/R |
| 727,62,601 | N | N | N | A/R | N | A/R | A/R | N |
| 727,63,601 | N | N | N | A/R | N | A/R | A/R | N |
| 728,61,599 | O | O | O | O | O | O | O | O |
| 728,61,601 | O | O | O | O | O | O | O | O |
| 728,62,599 | N | A/R | A/R | N | A/R | N | A/R | A/R |
| 728,62,601 | A/R | A/P | N | A/R | N | A/R | A/R | N |
| 729,61,599 | O | O | O | O | O | O | O | O |
| 729,61,600 | O | O | O | O | O | O | O | O |
| 729,61,601 | O | O | O | O | O | O | O | O |
| 729,62,599 | N | A/R | A/R | N | A/R | N | N | N |
| 729,62,600 | A/R | A/R | A/R | A/R | A/R | N | N | N |
| 729,62,601 | A/R | A/R | N | A/R | N | N | N | N |

Exact coordinate examples for target `(729,62,600)`:

```text
NW 728,64,599  A/R   N 728,64,600  A/R   NE 728,64,601  A/R
W  729,64,599  A/R   E 729,64,601  A/R
SW 730,64,599  N     S 730,64,600  N     SE 730,64,601  N
```

Targets at Y=61 are 3 blocks below the stand; every stand exceeds `ACTION_REACH`. Targets at Y=62/63
are within reach for some stands, but either the stand is not one local step away (`N`) or the real
raycast is blocked. Example:

```text
stand=728,64,600 target=729,62,600
rayEnd=(729.5,62.5,600.5) hitType=BLOCK hitBlock=729,63,600
```

The block above the target is the first hit, so the target center is genuinely occluded. No accepted
stand exists for these targets at Y=64.

## 4. Did IDX3/4/5/7 try BREAK at other heights?

No. Their NAV foot-Y ranges:

```text
IDX3: 76..88
IDX4: 86..122
IDX5: 77..79
IDX7: 63 only
```

IDX3/4/5 did move through other heights, and IDX4 reached Y=119 next to the Y=118 leaf layer; IDX7
never left Y=63. But there are **zero BREAK_STAND_CANDIDATE / BREAK_LOS_DIAG events for their UUIDs**.
Therefore no alternate-height BREAK attempt occurred. The absence is not proof that an alternate stand
would fail LOS; it is proof that the candidate evaluator was not invoked for them.

## 5. IDX7 — BUILD sequence

```text
tick 1     A2_TRYPLAN_ENTER pos=728,63,466 goal=661,71,533
tick 2     A2_TRYPLAN_ENTER goal=706,71,488
tick 2022  GLOBAL_SEARCH_COMPLETE start=728,63,466 goal=706,71,488
           exp=8000 frontier=924 found=false hitBudget=true routeNodes=56
tick 2022  A2_LOCAL_STAIR_WRITE op=INC_STEP new=1 objetivo=706,69,459
tick 2022  BUDGET intento=1/10 pos=728,63,466 objetivo=706,69,459 progreso=true
tick 2022..4982 A2_TRYPLAN_ENTER local=1/64, same objective
tick 4982  BUDGET intento=10/10 progreso=false desenlace=TERMINAL
tick 4982  A2_FALLAR_ENTER motivo={presupuesto agotado sin progreso (10/10) hacia 706,69,459}
tick 4982  NAV_FAIL pos=728,63,466
```

Important: `GLOBAL_SEARCH_COMPLETE found=false`, route front = 56 nodes. The chosen local objective is
the **front cell** `706,69,459`, produced by `A2_LOCAL_STAIR_WRITE op=INC_STEP new=1`. It is not proof of
a global BUILD edge; there is no global BUILD node/actionPos in the trace. The final diagnostic labels it
`buildable=true transition=BUILD build=true rutaGlobal=56`, but that label was computed when converting the
front waypoint into a local objective.

Action placement evidence:

```text
actionPos occurrences: 0
BUILD-VERIFICADO: 0
BUILD_FEET_APPROACH: 0
BUILD-ACERCAR-PIES: 0
BUILD-POSTSTATE: 0
```

## 6. Was there a BUILD node in the local route?

**No.** There is no local route acceptance event, no BUILD `actionPos`, and no executor BUILD dispatch.
The transition was lost before the local route: the global search was a partial/frontier route
(`found=false`), and `A2_LOCAL_STAIR_WRITE` only wrote a local objective. The local planner repeatedly
planned toward `706,69,459` without accepting a route. Terminal failure is local budget no-progress
(10/10), not a placement callback failure.

## 7. IDX4 — DROP cycles

Six unique starts, five completed, one final start without `DROP_DONE`:

```text
567,122,534 -> 567,119,534   DONE
567,119,524 -> 567,116,524   DONE
571,116,525 -> 571,113,525   DONE
572,113,525 -> 572,112,525   DONE
576,112,522 -> 576,111,522   DONE
580,111,520 -> 580,110,520   START only, no DONE
```

All starts logged `grounded=true liveFeetValid=true liveSupport=true liveAabbFits=true`.

19 unique `DROP_REJECTED`, all **path blocked**, not landing/support failure:

```text
14 at Y=118:
569,116,531 568,116,531 570,116,530 570,116,531 571,116,529 571,116,530
571,116,528 571,116,527 571,116,526 570,116,526 570,116,525 569,116,525
568,116,525 567,116,525

5 at Y=115:
571,113,526 571,113,527 571,113,528 570,113,527 570,113,526
```

Every `DROP_DESTINATION_EXCLUDED` obstacle is `minecraft:spruce_leaves` at distance 3–5:
14 at Y=118 and 5 at Y=115. Exclusion fingerprints include `729977819684257`,
`729388615285487`, `729130719809857`, `729976879491037`, `729130660177837`,
`1195122615316962`, `729396826789343`. Consequently the 19 “fails” are **blocked drop shafts**, not
invalid landings.

## 8. IDX6 — DROP cycles

Three unique starts:

```text
661,72,459 -> 661,71,459   DONE t253, cursor 0->1, routeLength11
658,71,471 -> 658,70,471   START, no DONE (final)
661,72,459 -> 661,71,459   duplicate start, no second DONE
```

`DROP_EDGE_STEP_ABORTED` = 18 unique: **17 `no_progress`**, 1 `route_node_changed` (t198).
No `DROP_REJECTED` for IDX6.

After the 1-block drop, terminal `NAV_FAIL` was `guarda de ruta: replanteos agotados (12/12)` at
`658,71,470`, with rearmes 0/4..4/4. The final DROP to `658,70,471` never completed.

## 9. IDX1 final activity / death

Last captured health/retreat:

```text
t1839 RETREAT_STARTED health=15.293243 threat=670,71,553
t1858 RETREAT_PROGRESS health=13.693243 threat=670,71,552
t1878 RETREAT_PROGRESS health=13.693243 threat=668,71,551
```

Last movement activity:

```text
t1904 DROP_LANDING_PROGRESS landing=668,71,548 live=668,74,549 airborne
t1914 DROP_LANDING_PROGRESS live=670,71,549 validLanding=true
t1915 DROP_TAKEOFF_UNSUPPORTED_REPLAN stale=668,74,549 -> live=670,71,550
t1916 A2_TRYPLAN_ENTER pos=670,71,550
t1920 MOVE_COMMAND node=668,71,548 WALK pos=670.68,71.00,550.21
t1930 MOVE_COMMAND node=668,71,548 WALK pos=670.42,71.00,550.05
```

Harness marked `MUERTO t=1922 pos=670,71,550`. There is **no damage/death callback, killer, damage
source, or `died/slain` line** in `srv.log` or `nav-trace.log`. Cause: **UNKNOWN**. Do not infer the
cause from proximity to the threat entity.

## 10. Hashes / preservation

```text
srv.log             00E94969BFA8A95036958053413D3A446E029482EF80FECFF8DB12DB47A36AE3
nav-trace.log       3786603A631A2B7725590E15EE861CA4742CAED233C72DB44AA61387D1E55181
fin.txt             A1140A5D482020F6F7F0A3E20EADCED3C574FE074516A28B814218D23B99AB85
launcher-status.txt A5F504E33C59E95960332B3C5A9ECF4EE88402554C8E2D05ACE8217B4903251C
```

Identical before and after analysis. No log was edited/rotated. No world, production or test was modified.

## 11. Confirmed vs unknown

Confirmed:

- all BREAK checks belong to IDX1; 0 accepted stands;
- 64 out-of-range, 32 not-single-step, 32 action failures in first expansion;
- IDX3/4/5/7 emitted no BREAK candidates;
- IDX7 had no global BUILD node/actionPos; local objective came from the frontier;
- IDX4’s 19 rejects are blocked shafts (spruce leaves), not bad landings;
- IDX6’s DROP failed after 17 no-progress edge steps and route guard exhaustion;
- IDX1 death cause not instrumented.

Unknown:

- caller/context for the 96 LOS lines outside the candidate set;
- whether a valid alternate-height stand exists for the BREAK targets (not evaluated);
- IDX1 damage source.

No runtime is authorized. A2 remains OPEN.

