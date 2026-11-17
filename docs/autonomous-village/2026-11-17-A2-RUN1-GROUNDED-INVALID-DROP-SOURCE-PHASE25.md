# A2 Run 1 — grounded-invalid DROP source recovery Phase 25 (2026-11-17)

## Result

**FAIL.** Static validation passes, but A2 Run 1 reaches only 3/8 strict. IDX4 cannot resolve a unique
supported AABB-overlapping source cell at `567,122,534`; the new recovery fails closed and replans.

- Tested SHA: `011373d0b3423b188611867d7f4751ab2ac88c66`
- Run: `a2n-20261117-153059`
- Seed 12345, FaseA 3000, FaseB 6000, budgets 900/8000.

## Static validation

| Command | Exit | Result | Log |
|---|---:|---|---|
| `gradlew test` | 0 | PASS | `build/phase25-groundedsource-gradle.log` |
| `lint_aldea.py --strict` | 0 | PASS | `build/phase25-groundedsource-lint.log` |
| `git diff --check` | 0 | PASS | terminal |
| `compileJava -Pa2nDiagnostics=true` | 0 | PASS | `build/phase25-groundedsource-diagcompile.log` |

```text
NAV_DROP_TAKEOFF_POLICY_TEST: OK (51 assertions)
NAV_SOURCE_CELL_POLICY_TEST: OK (10 assertions)
NAV_ROUTE_NODE_POLICY_TEST: OK (6 assertions)
NAV_RECOVERY_CONTROLLER_TEST: OK (10 assertions)
NAV_RECOVERY_GUARD_POLICY_TEST: OK (5 aserciones)
```

## A2 Run 1

- 8/8 spawned.
- **3/8 perimeter.**
- **3/8 strict.**
- C4 **PASS** `[1,0,0]`.
- Run 2 not executed.

| idx | UUID | Perimeter | Strict |
|---|---|---|---:|
| 1 | `208243e4-cd4a-4ec5-8a37-bf6e168d10df` | t387 | **t514** |
| 2 | `b9914621-e875-4e9d-89d9-a9dd95b32361` | — | — |
| 3 | `f101f8da-fd0c-4780-a680-34c225bbd49f` | — | — |
| 4 | `03df487a-ff61-482c-9407-ed5cdd0d1b5d` | — | — |
| 5 | `eb512f59-add5-48ad-820f-adafd72492c5` | t1789 | **t2083** |
| 6 | `33ca7f23-d026-4fd2-91a5-5734ab3fa9c1` | t354 | **t650** |
| 7 | `efbe0b54-62fd-4648-8338-02a03517a5c4` | — | — |
| 8 | `acb5d3f4-dbab-4cdb-a599-ae579974b025` | — | — |

## Global source-recovery telemetry

```text
DROP_TAKEOFF_UNSUPPORTED_SOURCE_RECOVERY: 77
DROP_TAKEOFF_SOURCE_SNAP_REPLAN:           0
```

The new recovery event is emitted, but no snap candidate/replan is recorded globally. IDX2 shows the
repeated stalled pattern at `662,72,608` (`grounded=true feetValid=false support=false`).

## IDX4

UUID `03df487a-ff61-482c-9407-ed5cdd0d1b5d`.

### Origin

```text
DROP_TAKEOFF_ORIGIN_RESOLVED count=1599
routeIndex=0 routeStart=567,122,534 live=567,122,534
landing=568,119,533 resolved=567,122,534
```

The active DROP is a 3-block descent to `567,119,534`.

### New recovery telemetry

```text
DROP_TAKEOFF_LIVE_ORIGIN_VERIFIED: 0
DROP_TAKEOFF_ORIGIN_MISSING_SOURCE_RECOVERY: 0
DROP_TAKEOFF_UNSUPPORTED_SOURCE_RECOVERY: 41
DROP_TAKEOFF_SOURCE_CELL_CANDIDATE: 0
DROP_TAKEOFF_SOURCE_SNAP_STARTED: 0
DROP_TAKEOFF_SOURCE_SNAP_REPLAN: 0
```

```text
tick=1590..1610 DROP_TAKEOFF_UNSUPPORTED_SOURCE_RECOVERY
 staleTakeoff=567,122,534 liveSource=567,122,534
 grounded=true feetValid=false support=false realAabbFits=true
 action=resolve_unique_aabb_neighbor
```

No unique supported AABB-overlapping neighbor was found.

### DROP start fields

```text
DROP_START idxRuta=0 aterrizaje=567,119,534
 salida=567,122,534 dy=-3 construido=false
 originPlan=566,122,533
 grounded=true liveFeetValid=true liveSupport=true liveAabbFits=true
```

The mob validates its physical source, but the resolved origin/landing route does not advance.

### Landing / completion

```text
DROP_LANDING_PROGRESS count=160
tick=1581..1601 landing=568,119,533 liveCell=567,122,534
 exact=567.085,122.000,534.085 onGround=true
 landingHeightReached=false liveCellSupported=false validLanding=false
 withinTolerance=false distanceSqr=8.595
DROP_DONE: 0
DROP_CURSOR_ADVANCE: 0
```

### Failure

```text
A2_FALLAR_ENTER count=5
tick=1444, 1527, 1610
motivo={DROP source-cell recovery found no unique supported AABB-overlapping cell
 at BlockPos{x=567, y=122, z=534}}
NAV_FAIL rearmes=2/4, 3/4, 4/4
finalPos=567,122,534; FAILED
```

IDX4 never descends and never advances the cursor. The blocker is source-cell uniqueness, not budget.

## IDX7 / IDX8

```text
IDX7 efbe0b54-62fd-4648-8338-02a03517a5c4:
 pos=705,65,459; BUILD 703,68,457; local1/64; global2526/8000; FAILED

IDX8 acb5d3f4-dbab-4cdb-a599-ae579974b025:
 pos=769,63,497; local invalid; global8000/8000
 NAV_FAIL {sin objetivo local valido (10/10)}
```

## C4

- PASS `[1,0,0]`.
- 12/12 resumed; no casualties/unresolved.
- Save/reload OPEN.

## Gate

- Static PASS.
- A2 Run 1 FAIL 3/8 strict.
- Run 2 not executed.
- Full 12k not executed.
- A2 remains OPEN.

## Artifacts

- `build/a2n/a2n-20261117-153059/srv.log`
- `build/a2n/a2n-20261117-153059/nav-trace.log`
- `build/a2n/a2n-20261117-153059/fin.txt`
- `build/a2n/a2n-20261117-153059/estado.txt`
- `build/phase25-groundedsource-gradle.log`
- `build/phase25-groundedsource-lint.log`
- `build/phase25-groundedsource-diagcompile.log`

Original worlds untouched; artifacts preserved.

## Next action

ChatGPT must make the AABB-overlap source resolver find a supported neighbor at IDX4's
`567,122,534` or choose a different grounded source. The current resolver emits 41
`UNSUPPORTED_SOURCE_RECOVERY` events and 5 `NAV_FAIL`s with `no unique supported AABB-overlapping
cell`, without candidate/snap. Do not relax support or increase budget.
