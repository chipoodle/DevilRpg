# A2 Run 1 — unsupported take-off rework Phase 24 (2026-11-17)

## Result

**FAIL — severe regression.** Only idx1 reaches the perimeter/strict wall. Seven attackers remain
outside while `DROP_TAKEOFF_UNSUPPORTED_WAIT` fires 2,102 times. IDX4 loops a 3-block DROP without
completing it.

- Tested SHA: `37c0aa702b44d94c6d3cc4f6b9fe54ee40e1c2d8`
- Run: `a2n-20261117-142016`
- Seed 12345, FaseA 3000, FaseB 6000, budgets 900/8000.

## Static validation

| Command | Exit | Result | Log |
|---|---:|---|---|
| `gradlew test` | 0 | PASS | `build/phase24-unsupported-gradle.log` |
| `lint_aldea.py --strict` | 0 | PASS | `build/phase24-unsupported-lint.log` |
| `git diff --check` | 0 | PASS | terminal |
| `compileJava -Pa2nDiagnostics=true` | 0 | PASS | `build/phase24-unsupported-diagcompile.log` |

```text
NAV_SOURCE_CELL_POLICY_TEST: OK (10 assertions)
NAV_DROP_TAKEOFF_POLICY_TEST: OK (47 assertions)
NAV_ROUTE_NODE_POLICY_TEST: OK (6 assertions)
NAV_RECOVERY_CONTROLLER_TEST: OK (10 assertions)
NAV_RECOVERY_GUARD_POLICY_TEST: OK (5 aserciones)
```

Policy admission `origenFisicamenteAlcanzado(grounded,feetValid,supported,fits,...)` and
`replanificarDesdeOrigenSoportado(takeoffSupported,...)` are present.
`DROP_TAKEOFF_UNSUPPORTED_WAIT` and `DROP_TAKEOFF_UNSUPPORTED_REPLAN` are present in `NavExecutor`.

## A2 Run 1

- 8/8 spawned.
- **1/8 perimeter.**
- **1/8 strict.**
- C4 **PASS** `[1,0,0]`.
- Run 2 not executed.

| idx | UUID | Perimeter | Strict |
|---|---|---|---:|
| 1 | `ab7f3ed2-7a09-48c5-a554-8fc878200537` | t387 | **t514** |
| 2 | `7d54647f-2748-4cb5-884b-c39acea3eff2` | — | — |
| 3 | `6466acc5-9c51-49a5-98a1-d14a02330c8e` | — | — |
| 4 | `bbadffa7-09c6-49fa-8adf-c1cd118e30f5` | — | — |
| 5 | `b2ef1e3b-777e-4463-a544-8034385278ef` | — | — |
| 6 | `18a01c48-432a-4747-bd90-fb44ce497c8b` | — | — |
| 7 | `a17d0204-7720-407e-9f5d-6042674819be` | — | — |
| 8 | `14dc9f72-62a6-4e83-8a1a-4bcdd59bcc43` | — | — |

`entraronPerimetroEver=1`, `vivos=7`, `muertos=1`.

## Unsupported-takeoff telemetry

```text
DROP_TAKEOFF_UNSUPPORTED_WAIT:   2102
DROP_TAKEOFF_UNSUPPORTED_REPLAN: 2
DROP_TAKEOFF_EXACT_EDGE_DIAG:    0
```

Repeated pattern:

```text
staleTakeoff=661,72,459 liveSource=661,72,459
grounded=true feetValid=false support=false realAabbFits=true
```

The new admission treats the current feet cell as unsupported and waits; the source does not change.
This is now the dominant blocker for the seven non-entrants. It replaces route progress with a
grounded-wait loop.

## IDX4

UUID `bbadffa7-09c6-49fa-8adf-c1cd118e30f5`.

```text
finalPos=567,122,534; alive; enteredPerimeterEver=false
active DROP: idxRuta=0 landing=567,119,534
 takeoff=567,122,534 dy=-3
DROP_START count: 9
DROP_LANDING_PROGRESS count: 600
DROP_DONE: 0
DROP_CURSOR_ADVANCE: 0
```

Repeated landing progress:

```text
tick=5969..5999 routeIndex=0 landing=567,119,534
 liveCell=567,122,534 exact=567.085,122.000,534.085
 onGround=true landingHeightReached=false liveCellSupported=false
 validLanding=false withinTolerance=false distanceSqr=6.595
 delta=0.000,-0.078,0.000
```

IDX4 never leaves the take-off cell: the DROP toward `567,119,534` is started repeatedly but no
descent, `DROP_DONE` or cursor advance occurs. No `DROP_TAKEOFF_UNSUPPORTED_WAIT` or
`REPLAN` event appears for this UUID in this run.

## IDX7 / IDX8

```text
IDX7 a17d0204-7720-407e-9f5d-6042674819be:
 pos=707,63,459; BUILD; global ruta53 exp1664/8000; FAILED
IDX8 14dc9f72-62a6-4e83-8a1a-4bcdd59bcc43:
 pos=746,64,534; TRAVERSING; no perimeter entry
 unsupported wait observed at tick5980/5990
```

## C4

- PASS `[1,0,0]`.
- 12/12 resumed; no casualties/unresolved.
- Save/reload OPEN.

## Gate

- Static PASS.
- A2 Run 1 FAIL 1/8 strict.
- Run 2 not executed.
- Full 12k not executed.
- A2 remains OPEN.

## Artifacts

- `build/a2n/a2n-20261117-142016/srv.log`
- `build/a2n/a2n-20261117-142016/nav-trace.log`
- `build/a2n/a2n-20261117-142016/fin.txt`
- `build/a2n/a2n-20261117-142016/estado.txt`
- `build/phase24-unsupported-gradle.log`
- `build/phase24-unsupported-lint.log`
- `build/phase24-unsupported-diagcompile.log`

Original worlds untouched; artifacts preserved.

## Next action

ChatGPT must fix the unsupported-source wait loop before any further A2 run. The dominant event is
`DROP_TAKEOFF_UNSUPPORTED_WAIT` with `grounded=true, feetValid=false, support=false`; the mob waits
without changing its physical source. This rework regressed perimeter entry from 5/8 to 1/8.

IDX4 separately loops `DROP_START 567,122,534 → 567,119,534` without movement or `DROP_DONE`.
Do not increase budgets or relax support.
