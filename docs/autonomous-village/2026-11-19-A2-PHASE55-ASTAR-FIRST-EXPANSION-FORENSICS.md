# A2 Phase55 — first-expansion A* forensics (run `a2n-20261119-093456`)

## 1. Result

**READ-ONLY COMPLETE — first-expansion failure demonstrated for IDX3 and IDX5.**

Both completed searches with `exp=1`/`frente agotado` expanded the start node once, generated exactly
**56 candidate edges** (8 horizontal offsets × 7 vertical levels), and accepted **0** (`encolados=0`).
They are not “zero candidates generated”; they are “all 56 generated candidates rejected.”

Saved block geometry shows both starts inside a 1×1 vertical shaft with all eight adjacent columns solid
across the vertical window. `NavPlanner.OFFSETS` contains no `(0,0)` vertical-only edge, so a safe descent
straight down the shaft is not representable; with every adjacent column blocked, OPEN becomes empty and
the search terminates at `exp=1`.

- RUN: `a2n-20261119-093456`
- PRODUCTION: `11ff43cd338d7a6222a146a2ed642a5546fb2910`
- No runtime, no world edits, no production edits.

## 2. Literal evidence

IDX3 / entity #261, `09:37:00`:

```text
global exactGoal=false inicio=594, 92, 600 meta=616, 71, 578 radio=112 V=3
exp=1/8000 presupuesto=false aristas=56 encolados=0
frente=594, 92, 600 dFrente3D=35.7 dFrenteXZ=31.1 dFrenteY=20
motivo=el frente alcanzable se agoto dentro del radio 112 rutaFrente=0
```

IDX5 / entity #263, `09:37:01`:

```text
global exactGoal=false inicio=594, 83, 466 meta=616, 71, 488 radio=112 V=3
exp=1/8000 presupuesto=false aristas=56 encolados=0
frente=594, 83, 466 dFrente3D=31.6 dFrenteXZ=31.1 dFrenteY=11
motivo=el frente alcanzable se agoto dentro del radio 112 rutaFrente=0
```

The same pattern repeats for #261 at `594,92/90/88,600` and #263 at `594,83/81,466`.

## 3. Counter semantics

`NavPlanner` graph expansion:

```text
8 OFFSETS: (±1,0), (0,±1), (±1,±1)
dy in [-3, +3]
=> 56 candidates per expanded node
```

- `contadores[0]` (`aristas`) increments for every `validarArista` result.
- `contadores[1]` (`encolados`) increments only when the edge is accepted and emitted.
- `aristas=56` + `encolados=0` therefore proves all 56 offsets were evaluated and all rejected.
- `NavGraphSearch.Session.advance` then sees EOF/empty OPEN, sets `complete=true`, `reason=frente
  alcanzable agotado`, and returns the start as `front`.

## 4. Block geometry (NBT, read-only)

IDX3 around `594,92,600` (final saved world, y92 plane):

```text
x593  stone | x594 air | x595 stone
z599  stone | z600 air | z601 stone
```

A single air column at `594,*,600`; adjacent columns are stone/dirt across the requested vertical window.

IDX5 around `594,83,466`:

```text
y81: x594,z466=air; all adjacent columns stone/dirt
y79-90: only x594,z466 remains air; below is stone at y78
```

This is a 1×1 shaft. The graph cannot move into adjacent columns (solid) and has no vertical-only
neighbor (`(0,0)` is not in `OFFSETS`), so it cannot descend the shaft even though the drop could be
within `maxSafeDrop`.

## 5. Per-attacker table

`Pending` = number of `NAV_GLOBAL_SEARCH_PENDING` events; it is not an exact start count.

| IDX | UUID | Global start → objective | Pending | Completed summaries | Final state |
|---:|---|---|---:|---|---|
| 1 | `0a7c81d4…` | 728,64,600 → 706,71,578 | 14 | 1: exp450, aristas25144, encolados10381, ruta36 | 727,65,600 TRAVERSING |
| 2 | `d6a7a8f9…` | 661,73,628 → 661,73,533 | 1 | 0 | 660,72,608 TRAVERSING |
| 3 | `2cae8dcf…` | 594,94,600 → 616,71,578 | 2 | 5 × exp1/aristas56/encolados0 | 594,88,600 PLANNING |
| 4 | `527641b9…` | 566,122,533 → 597,71,533 | 15 | 0 | 566,122,533 PLANNING |
| 5 | `a6cecab5…` | 594,87,466 → 616,71,488 | 3 | 4 × exp1/aristas56/encolados0 | 594,79,466 PLANNING |
| 6 | `7d1a2b27…` | 661,72,438 → 661,73,533 | 1 | 0 | 661,71,464 TRAVERSING |
| 7 | `299ba43f…` | 728,63,466 → 706,71,488 | 15 | 0 | 728,63,466 PLANNING |
| 8 | `ec2c1ddb…` | 756,63,533 → 725,71,533 | 15 | 0 | 756,63,533 PLANNING |

IDX1 is the only attacker with a completed usable global route (36 nodes) but still only reached
`727,65,600` by the end.

## 6. First failed transition (demonstrable)

```text
expand(594,92,600)
  -> 56 candidate edges evaluated
  -> 0 accepted/emitted
  -> OPEN empty
  -> complete=true, front=594,92,600, routeFrente=0
```

Same for `594,83,466` (IDX5).

## 7. Demonstrated / discarded / unknown

**Demonstrated**

- 56 candidates generated: not a “zero candidate” bug.
- All 56 rejected: `encolados=0`.
- No accepted edge was later discarded: none were emitted.
- The start cells are in 1×1 shafts; adjacent columns are solid.
- Repeated exp1 summaries at the same start prove the search was recreated (exp reset), not a single
  session that accumulated expansions.
- `OFFSETS` has no vertical-only `(0,0)` edge, so the only possible descent direction is absent.

**Discarded**

- Budget exhaustion: `presupuesto=false`, `exp=1/8000`.
- Pending partial result used as route: `rutaFrente=0`, no route.
- World-boundary/radius clipping: starts are far inside radio112.
- Chunk unavailable: fixture had 289/289 chunks READY.

**Unknown**

- Exact per-candidate rejection enum mix: `CLASIFICACION`, `NODO_NO_PISABLE`, `DESNIVEL`,
  `CAIDA_INSEGURA`, etc. Only aggregate `56/0` is logged.
- Exact block/collision state at the instant of each search: the NBT is the final save, after later
  mining. It matches the shaft geometry but is not a per-tick snapshot.
- Why the executor recreated the search immediately after an exp1 completion instead of keeping the
  completed negative result cached; no explicit `GLOBAL_SEARCH_START/CANCEL/RESTART` event exists.

## 8. Missing telemetry (not implemented)

Minimum useful addition, opt-in and bounded to the first expansion of each global search:

```text
GLOBAL_FIRST_EXPANSION uuid=... start=... goal=...
  candidates=56 accepted=0
  per candidate: pos, kind, rejection, fits, hasSupport, headClear,
                 protected, chunkLoaded, blockState, feetResult
```

Plus lifecycle events:

```text
GLOBAL_SEARCH_START uuid start goal
GLOBAL_SEARCH_COMPLETE uuid start goal exp frontier reason
GLOBAL_SEARCH_CANCEL/RESTART uuid oldStart newStart reason
```

Do not log all expansions at normal volume.

## 9. Recommendation

1. Confirm the exact rejection causes with the opt-in first-expansion telemetry above.
2. Review whether the global graph should admit a vertical-only descent edge for a 1×1 safe shaft. This is
   a production navigation change and must be separately authorized; do not implement it here.
3. Until then, do not attribute the A2 failure to the fixture or to budget; it is a first-expansion graph
   coverage/trapping failure.

A2 remains OPEN. No runtime authorization consumed by this forensic.
