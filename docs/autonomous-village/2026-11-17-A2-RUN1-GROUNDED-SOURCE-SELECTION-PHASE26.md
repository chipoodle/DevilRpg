# A2 Run 1 — grounded source selection Phase 26 (2026-11-17)

## Result

**FAIL.** Source candidates are now found and snaps start, but no snap reaches physical occupancy.
A2 Run 1 reaches 3/8 perimeter and 2/8 strict.

- Tested SHA: `29b82a2c40b205ddb1ac9ddcf83027899a1510ed`
- Run: `a2n-20261117-155419`
- Seed 12345, FaseA 3000, FaseB 6000, budgets 900/8000.

## Static validation

| Command | Exit | Result | Log |
|---|---:|---|---|
| `gradlew test` | 0 | PASS | `build/phase26-source-selection-gradle.log` |
| `lint_aldea.py --strict` | 0 | PASS | `build/phase26-source-selection-lint.log` |
| `git diff --check` | 0 | PASS | terminal |
| `compileJava -Pa2nDiagnostics=true` | 0 | PASS | `build/phase26-source-selection-diagcompile.log` |

```text
NAV_SOURCE_CELL_POLICY_TEST: OK (14 assertions)
NAV_DROP_TAKEOFF_POLICY_TEST: OK (51 assertions)
NAV_ROUTE_NODE_POLICY_TEST: OK (6 assertions)
NAV_RECOVERY_CONTROLLER_TEST: OK (10 assertions)
NAV_RECOVERY_GUARD_POLICY_TEST: OK (5 aserciones)
```

## A2 Run 1

- 8/8 spawned.
- **3/8 perimeter.**
- **2/8 strict.**
- C4 **PASS** `[2,0,0]`.
- Run 2 not executed.

| idx | UUID | Perimeter | Strict |
|---|---|---|---:|
| 1 | `a2b378ac-653c-4ffa-9bec-9fd6c4cef6f6` | t387 | **t514** |
| 2 | `f049c6f2-6087-4bf8-81ed-1573f7283020` | — | — |
| 3 | `b4bbc1bb-6560-4e76-9bba-b6f62ab60de9` | — | — |
| 4 | `f6b5e595-a8b2-4ab0-9952-9085e314b115` | — | — |
| 5 | `ad382bd6-0d6b-4c75-b10d-e589f9be7cc2` | t1829 | — |
| 6 | `2c1826c1-ba05-45e4-9542-914e9293fa75` | t354 | **t650** |
| 7 | `eeadf764-8d58-4a21-a15c-429b11c5aea5` | — | — |
| 8 | `97e29c85-cdbc-403a-b0c6-08355f78818e` | — | — |

IDX5 entered the perimeter at t1829 but did not reach the strict wall.

## Source selection global

```text
DROP_TAKEOFF_SOURCE_CELL_CANDIDATE: 16
DROP_TAKEOFF_SOURCE_SELECTED:        0
DROP_TAKEOFF_SOURCE_SNAP_STARTED:    19
DROP_TAKEOFF_SOURCE_SNAP_REACHED:     0
DROP_TAKEOFF_SOURCE_SNAP_REPLAN:      0
```

Candidates exist, but no `SOURCE_SELECTED`/`SNAP_REACHED` occurs. The snap starts and then stalls or
is retried without a selected-candidate or replan event.

## IDX4

UUID `f6b5e595-a8b2-4ab0-9952-9085e314b115`.

### Source recovery

```text
DROP_TAKEOFF_UNSUPPORTED_SOURCE_RECOVERY: 2
 tick=4120/4130 staleTakeoff=575,112,518 liveSource=575,112,518
 grounded=true feetValid=false support=false realAabbFits=true
 action=resolve_ranked_8_neighbor_aabb_source
```

### Candidates

```text
tick=4118 from=575,112,518 candidate=574,112,518 action=source_snap_then_replan
tick=4204 from=576,111,514 candidate=576,111,515
tick=4250 from=577,110,513 candidate=577,110,514
```

All logged candidates report feet/support/real-AABB fit true. Only one candidate is emitted per event;
there is no eight-neighbor rejection matrix in the log.

### Snap

```text
DROP_TAKEOFF_SOURCE_SNAP_STARTED: 19
last: tick=4270 from=577,110,513 candidate=577,110,514
 exact=577.536,110.000,513.987 target=577.50,110.00,514.50 dy=0
DROP_TAKEOFF_SOURCE_SNAP_REACHED: 0
DROP_TAKEOFF_SOURCE_SNAP_REPLAN: 0
DROP_TAKEOFF_SOURCE_SELECTED: 0
```

The mob approaches the target but never records physical occupancy. No selection reason is logged.

### Origin / DROP

```text
DROP_TAKEOFF_ORIGIN_RESOLVED: 92
 routeIndex=0 routeStart=574,112,518 live=574,112,518
 landing=575,111,517 resolved=574,112,518

DROP_TAKEOFF_LIVE_ORIGIN_VERIFIED: 0
DROP_TAKEOFF_ORIGIN_MISSING_SOURCE_RECOVERY: 0
```

`DROP_START` active at the end:

```text
idxRuta=0 aterrizaje=577,109,513 salida=577,110,513 dy=-1 natural
```

Final:

```text
pos=577,110,514; estado=RECOVERING
DROP_DONE for current landing: NONE
DROP_CURSOR_ADVANCE for current landing: NONE
```

Prior `DROP_DONE`/advance events belong to other cycles (`570,114,532` and `571,112,522`), not to the
current `577,109,513` landing.

### Failures

```text
NAV_FAIL motivo={DROP source-cell recovery stalled before physical occupancy: 571,113,524}
NAV_FAIL motivo={DROP source-cell recovery stalled before physical occupancy: 571,114,532}
A2_FALLAR_ENTER ticks 3560, 4031, 4115
```

The blocker is the snap never reaching physical occupancy; the source is never selected.

## IDX7 / IDX8

```text
IDX7 eeadf764-8d58-4a21-a15c-429b11c5aea5:
 pos=705,65,459; BUILD 703,68,457; local1/64; global2526/8000; FAILED

IDX8 97e29c85-cdbc-403a-b0c6-08355f78818e:
 pos=769,63,497; local invalid; global8000/8000
 NAV_FAIL {sin objetivo local valido (10/10)}
```

## C4

- PASS `[2,0,0]`.
- 12/12 resumed; no casualties/unresolved.
- Save/reload OPEN.

## Gate

- Static PASS.
- A2 Run 1 FAIL 2/8 strict.
- Run 2 not executed.
- Full 12k not executed.
- A2 remains OPEN.

## Artifacts

- `build/a2n/a2n-20261117-155419/srv.log`
- `build/a2n/a2n-20261117-155419/nav-trace.log`
- `build/a2n/a2n-20261117-155419/fin.txt`
- `build/a2n/a2n-20261117-155419/estado.txt`
- `build/phase26-source-selection-gradle.log`
- `build/phase26-source-selection-lint.log`
- `build/phase26-source-selection-diagcompile.log`

Original worlds untouched; artifacts preserved.

## Next action

ChatGPT must make the selected source snap complete physical occupancy and emit `SOURCE_SELECTED` +
`SNAP_REACHED`; currently 19 snaps start and none completes. Also log the eight-neighbor rejection
matrix and selection reason when candidates are ranked. Do not relax support/fit or increase budget.
