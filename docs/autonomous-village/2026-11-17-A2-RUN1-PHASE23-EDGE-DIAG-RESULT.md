# A2 Run 1 — Phase 23 exact-edge diagnostic result (2026-11-17)

## Result

**FAIL.** The exact-edge diagnostic now explains the A* shortfall: the candidate
`589,95,522` is classified `DROP`, not `STEP_UP`, because it has no support. The exact goal
`589,96,522` also reports `feetSupport=false`. A2 Run 1 remains 5/8 strict.

- Tested SHA: `69f066cc63c01925d60b037d2a1317c5224d3b6b`
- Run: `a2n-20261117-122621`
- Seed 12345, FaseA 3000, FaseB 6000, budgets 900/8000.

## Static validation

| Command | Exit | Result | Log |
|---|---:|---|---|
| `gradlew test` | 0 | PASS | `build/phase23-edge-gradle.log` |
| `lint_aldea.py --strict` | 0 | PASS | `build/phase23-edge-lint.log` |
| `git diff --check` | 0 | PASS | terminal |
| `compileJava -Pa2nDiagnostics=true` | 0 | PASS | `build/phase23-edge-diagcompile.log` |

```text
NAV_SOURCE_CELL_POLICY_TEST: OK (10 assertions)
NAV_DROP_TAKEOFF_POLICY_TEST: OK (43 assertions)
NAV_ROUTE_NODE_POLICY_TEST: OK (6 assertions)
```

## A2 Run 1

- 8/8 spawned.
- **5/8 perimeter.**
- **5/8 strict.**
- C4 **PASS** `[1,0,0]`, 12/12 resumed.
- Run 2 not executed.

| idx | UUID | Perimeter | Strict |
|---|---|---|---:|
| 1 | `46af050c-da90-4615-860e-168c61d584c1` | t387 | **t512** |
| 2 | `4b40851f-7393-40f6-bbd5-cfe1c310e572` | t242 | **t667** |
| 3 | `3c791d5e-b1b0-44c7-9a08-ba272b1e24ee` | t1772 | **t1869** |
| 4 | `7460873e-0b48-47f3-95d9-46ba8e475f75` | — | — |
| 5 | `b68ca1fb-bd68-4e44-a4ab-ad3be3662e4f` | t816 | **t1043** |
| 6 | `15a28c9c-27f5-451c-980a-657b76e26e91` | t224 | **t649** |
| 7 | `060a7603-61a5-4383-8533-c1081900eabe` | — | — |
| 8 | `d84c0e65-17d3-48cc-a5ac-1e2279496862` | — | — |

## IDX4 — current landing only (`589,95,521`)

UUID `7460873e-0b48-47f3-95d9-46ba8e475f75`.

### `DROP_TAKEOFF_EXACT_EDGE_DIAG`

```text
from=589,95,521 candidate=589,95,522 goal=589,96,522
kind=DROP accepted=false rejection=DESNIVEL transition=DROP
feetFrom=95 feetTo=94 cost=1.8 protected=false
candidateFits=true candidateSupport=false candidateHeadClear=true
feetFits=true feetSupport=false
snapshot={celda=589,95,522 kind=DROP fits=true soporte=false cabezaLibre1=true
 vecinos: 590,95,522=BLOCKED/f1/s0 588,95,522=DROP/f1/s0
 589,95,523=DROP/f1/s0 589,95,521=PASSABLE/f1/s1 ...}
```

This is not a `STEP_UP` edge. The candidate cell at y95 is air with no support, so `classify` produces
`DROP` to y94. The exact goal `589,96,522` reports `feetSupport=false`; the target take-off itself is
not a supported feet cell.

### `DROP_LANDING_PROGRESS` for `589,95,521`

```text
tick=3348 routeIndex=0 landing=589,95,521
 liveCell=589,96,522 exact=589.500,96.311,522.897
 onGround=false landingHeightReached=false liveCellSupported=false
 validLanding=false withinTolerance=false distanceSqr=2.610
 landingFits=true landingSupport=true

tick=3358 routeIndex=0 landing=589,95,521
 liveCell=589,93,522 exact=589.500,93.000,522.684
 onGround=true landingHeightReached=true liveCellSupported=true
 validLanding=true withinTolerance=false distanceSqr=7.652

tick=3368 and 3378: same live cell 589,93,522, distanceSqr=7.514
```

### `DROP_DONE` / cursor for current landing

```text
DROP_START current: L46280 / L46292 / L46302
 idxRuta=0 aterrizaje=589,95,521 salida=589,96,521 dy=-1
DROP_DONE 589,95,521: NONE
DROP_CURSOR_ADVANCE 589,95,521: NONE
```

Do not mix with the previous cycle:

```text
landing=589,93,522:
 DROP_DONE L46232, tick~3340
 DROP_CURSOR_ADVANCE L46233 previousIndex=0 nextIndex=1 routeLength=10
```

That `DROP_DONE` and cursor advance belong to `589,93,522`, not to the current `589,95,521` cycle.

## IDX7 / IDX8

```text
IDX7 060a7603-61a5-4383-8533-c1081900eabe:
 pos=705,65,459; BUILD 703,68,457; local1/64; global2527/8000
 NAV_FAIL {presupuesto agotado sin progreso (10/10)}

IDX8 d84c0e65-17d3-48cc-a5ac-1e2279496862:
 pos=769,63,497; local invalid; global8000/8000
 NAV_FAIL {sin objetivo local valido (10/10)}
```

## C4

- PASS `[1,0,0]`.
- 12/12 resumed; no casualties/unresolved workers.
- Save/reload OPEN.

## Gate

- Static gate: PASS.
- A2 Run 1: FAIL 5/8 strict.
- Run 2: not executed.
- Full 12k: not executed.
- A2 remains OPEN.

## Artifacts

- `build/a2n/a2n-20261117-122621/srv.log`
- `build/a2n/a2n-20261117-122621/nav-trace.log`
- `build/a2n/a2n-20261117-122621/fin.txt`
- `build/a2n/a2n-20261117-122621/estado.txt`
- `build/phase23-edge-gradle.log`
- `build/phase23-edge-lint.log`
- `build/phase23-edge-diagcompile.log`

Original worlds untouched; local-only artifacts preserved.

## Next action

ChatGPT must make the recovery goal a supported feet cell. The current exact goal
`589,96,522` has `feetSupport=false`, and the route's one-block STEP_UP premise
(`589,95,522` solid) is false: that cell is air with no support and classifies as DROP.
Do not reinterpret this as a budget failure; `hitBudget=false`. Do not relax support.
