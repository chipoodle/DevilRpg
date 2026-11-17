# A2 Run 1 — Forensic extract (2026-11-16)

## Source

- Run: `a2n-20261116-190555`
- SHA: `2d47d628724a677269e1c3d895759154f56a52c9`
- Mode: read-only extraction; no Minecraft execution, no production changes.

## IDX7 — BUILD without progress

UUID `c30d6124-96cc-4514-9a8d-2e3b110e2c55`; terminal tick 4209.

Last 10 `BUDGET` events all target `703,68,457`, position `705,65,459`:

```text
attempt 1: exp=901/900 progreso=true  wait=80  REINTENTO
attempt 2: exp=901/900 progreso=false wait=160 REINTENTO
attempt 3: exp=901/900 progreso=false wait=320 REINTENTO
attempt 4..9: exp=901/900 progreso=false wait=400 REINTENTO
attempt 10: exp=901/900 progreso=false TERMINAL
```

Earlier `BUILD-FALLIDO` evidence:

```text
lastMove=704,66,458:BUILD-FALLIDO
lastMove=703,68,457:BUILD-FALLIDO
```

Final state/route:

```text
state=PLANNING; objetivo=703,68,457; buildable=true; escalones=1/64
ultimoEscalon={global ALCANZA la mision -> waypoint local 703,68,457
 (transicion=BUILD build=true rutaGlobal=79 exp=2527)}
expGlobal=8749; local=1/64; fallos=0/10; replans=0/8; budget=9/10
NAV_FAIL motivo={presupuesto agotado sin progreso (10/10) hacia 703,68,457}
```

**Answer:** the local planner exhausted its 900-expansion budget on every attempt
(`exp=901/900`, 10/10) with no progress. The BUILD objective was enqueued/buildable
(`buildable=true`, `local=1/64`), but repeated `BUILD-FALLIDO` prevented a usable local route.
There is no evidence of a terrain-change invalidation for IDX7; the terminal cause is local
expansion/no-progress exhaustion after failed BUILD.

`lastPlanSummary` fields such as `metaEncolada`, `alcanzada`, `encolados` for the final local plan
were not persisted in this run. `A2_TRYPLAN_ENTER` only records counters; no full plan summary is
available. Do not infer the missing fields.

## IDX4 — DROP height mismatch

UUID `cd0e8725-3bbc-4672-bd1b-e293628396dc`; terminal tick 5147.

Route state:

```text
DROP node: 597,78,499
mob:       596,77,498
idxRuta=0/5
```

Repeated sequence:

```text
DROP_CANDIDATE nodo=597,78,499 trans=DROP
DROP_VALID     takeoff=597,78,499 dy=0
DROP_COMMAND   distanceToLanding=1.1
DROP_REJECTED  takeoff=597,77,499 dy=1
reason={el aterrizaje esta por ENCIMA (dy=1)}
```

Eventually `fallosDeDescenso=24`, `replans agotados (8/8)`, global budget `8000/8000`.

Code evidence:

- `NavGraphSearch.Step` carries both `state` and `feet`.
- `NavPlanner` reconstructs both local and global route nodes from the **search state**
  (`BlockPos.of(paso.state())` / `cursor`), not from `Step.feet`.
- For DROP, `piesResultantes` computes the real landing height (`cell.y - caidaReal`), but that value
  is not used as the route node position.

```text
STEP_STATE = 597,78,499  (confirmed route node)
STEP_FEET  = 597,77,499  (inferred from drop decision; Step.feet not persisted in log)
DROP_NODE  = 597,78,499
```

**Confirmed code defect:** DROP route nodes use the state height, not the landing/feet height.
The runtime symptom is the mob at y77 receiving “landing above (dy=1)” for a node at y78.
The exact `Step.feet` value is an inference, not a logged field.

## IDX8 — global route without usable standing waypoint

UUID `02a6770d-e458-4f69-a627-9ec9fdaa2be3`; terminal tick 3411.

```text
A2_TRYPLAN_ENTER tick=3411
 pos=769,63,497 objetivo=661,72,533
 local=0/64 fallos=9/10 replans=0/8 budget=0/10

A2_LOCAL_STAIR_WRITE tick=2689..3411
 op=INC_FAIL reason=NO_LOCAL_OBJECTIVE
 (ten increments, reset at 3051, again to 10)

NAV_FAIL t=3411
 motivo={sin objetivo local valido (10/10):
 ruta global sin waypoint de pie VALIDO dentro de la ventana desde 769,63,497
 (habia waypoints pero el mundo cambio) -> se invalida la ruta global [intento 10/10]}
 rearmes=4/4
```

Global summary:

```text
inicio=769,63,497 meta=661,72,533 radio=112 V=3
exp=8000/8000 presupuesto=true
frente=690,45,510 dFrente3D=44.0 dFrenteXZ=37.0 dFrenteY=26
rutaFrente=81
```

The last 20 `A2_LOCAL_STAIR_WRITE` events are all `INC_FAIL` with `NO_LOCAL_OBJECTIVE`, so the local
selector never found a valid standing waypoint. No per-waypoint `A2_GLOBAL-BUDGET-STAIR` events were
present.

Code defect:

- Global route reconstruction creates nodes from `paso.state()`; for DROP the state height is not the
  landing `feet` height.
- The local selector then receives waypoints at the wrong Y for drop steps and rejects them as invalid
  standing cells.

**Confirmed code-level cause:** global route nodes do not preserve `Step.feet` for DROP.
**Not directly confirmed at runtime:** which of the 81 front waypoints were discarded and their exact
per-node transitions; that per-node trace was not persisted.

## Correlation

| idx | UUID | Terminal | Position | Primary evidence |
|---|---|---|---|---|
| 7 | `c30d6124...` | NAV_FAIL t4209 | 705,65,459 | `exp=901/900` ×10, BUILD-FALLIDO |
| 4 | `cd0e8725...` | NAV_FAIL t5147 | 596,77,498 | DROP node y78, rejection dy=1 at y77, 24 failures |
| 8 | `02a6770d...` | NAV_FAIL t3411 | 769,63,497 | 81 global front waypoints, local 0/64 NO_LOCAL_OBJECTIVE |

## Classification

- **IDX7:** confirmed local expansion/no-progress exhaustion after BUILD failure; full plan summary absent.
- **IDX4:** confirmed code-level DROP node height bug (state vs feet); exact feet inferred 77.
- **IDX8:** confirmed code-level route-node height bug; per-waypoint runtime discard sequence not logged.

No production code, worlds or run artifacts were modified. No runtime was executed.
