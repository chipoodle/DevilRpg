# A2 global A* log forensics — Run `a2n-20261120-140611`

## 1. Result

**COMPLETE — read-only.** IDX3/4/5 first-expansion searches receive 56 candidates and **zero valid
edges**, so their `exp=1` exhaustion is an input/geometry result, not an A* algorithm failure. IDX1/6/7/8
lose work through restart/cancel and several searches exhaust the 8000 budget.

- BRANCH: `feature/autonomous-village`
- HEAD: `306f33d706012ca44c0c85e6a83c8500a370636c`
- RUN_ID: `a2n-20261120-140611`

## 2. Log hashes

```text
nav-trace.log       EB88E470F873F93527CFF16D3479C414AA6BD807406BA85DF4D5E49B0D9BFD90
srv.log             74DEFBF02ED4F325B34340A1DBCE8457816514C7B960E3FAFCB752403083E41C
launcher-status.txt F3047C4F9F482D15A3E4CFCB47183AA4FC84AECECF386686F5946A48AB489A42
fin.txt             30F71DBF52A433A7A2FA3E26831DD244254E2B9FDC5231DAB09F09E0D813FDCD
```

Files were read from `build/a2n/a2n-20261120-140611/`; none was modified.

## 3. UUID ↔ IDX

```text
IDX1 3473f6e9-e61c-48a0-ab31-3d38bc12b905
IDX2 544c42c3-3407-47b2-886c-2a48e750dd57
IDX3 295cd7f5-0d49-4bd7-b456-9b900471b907
IDX4 897ba54f-3446-44e4-b358-5a479de662ff
IDX5 e386973c-2e84-4851-bcb8-edc3205dd0d7
IDX6 33526cde-8028-4c0b-93f0-ea257800df65
IDX7 be03f694-31d7-458c-becf-f3dec050e81b
IDX8 9ec5a27d-3086-4526-a44a-4e3e7517925a
```

## 4. IDX3/4/5 first expansion

Representative searches (all exp=1 completions use the same 56-candidate shape):

### IDX3 `594,92,600 → 616,71,578`

```text
56 candidates
kind/rejection/edgeValid:
  40 BREAKABLE / NODO_NO_PISABLE / false
   8 STEP_UP  / DESNIVEL          / false
   6 PASSABLE / DESNIVEL          / false
   2 BREAKABLE / DESNIVEL         / false

features:
  42 fits=false hasSupport=true headClear=false feetFits=false feetSupport=true
   8 fits=false hasSupport=true headClear=true  feetFits=true  feetSupport=true
   6 fits=true  hasSupport=true headClear=true  feetFits=true  feetSupport=true
all: protected=false, chunkLoaded=true
```

### IDX5 `594,83,466 → 616,71,488`

```text
56 candidates
  40 BREAKABLE / NODO_NO_PISABLE / false
   9 BREAKABLE / DESNIVEL         / false
   5 STEP_UP  / DESNIVEL          / false
   2 PASSABLE / DESNIVEL          / false
all fits=false except the 2 PASSABLE; all protected=false, chunkLoaded=true
```

### IDX4 `580,88,520 → 597,71,533`

```text
56 candidates
  40 BREAKABLE / NODO_NO_PISABLE / false
  16 BREAKABLE / DESNIVEL         / false
all fits=false, headClear=false, feetFits=false
```

Every candidate has `edgeValid=false`; `GLOBAL_FIRST_EXPANSION_END` reports `enqueued=0`; the search
then completes with `exp=1`, `frontier=0`, `reason=frente_alcanzable_agotado`, `routeNodes=0`.

Exp=1 completions by start:

```text
IDX3: 17 (8 unique starts): 594,78/80/82/84/86/88/90/92,600
IDX4: 24 (11 unique starts): 580,88/90/92/94/96/98/100/102/104/106/108,520
IDX5: 6 (3 unique starts): 594,79/81/83,466
```

The repeated searches restart as the mob falls one block; each new start repeats the same zero-successor
geometry. This is a candidate-generation/stand-position problem, not insufficient expansions.

## 5. Pending searches IDX1/6/7/8

### IDX1

| # | Start → Goal | Pending events | Final |
|---:|---|---:|---|
| 1 | 728,64,600 → 661,73,533 | 1 | CANCEL exp4 frontier17 |
| 2 | 728,64,600 → 706,71,578 | 6 | COMPLETE exp515 frontier181 found |
| 3 | 706,71,579 → 660,72,533 | 102 | COMPLETE exp8000 frontier695 hitBudget |
| 4 | 705,71,580 → 660,72,533 | 1 | RESTART oldExp40 → 704,71,581 |
| 5 | 704,71,581 → 660,72,533 | 0 | CANCEL exp4 frontier11 |
| 6 | 704,71,581 → 660,72,533 | 101 | COMPLETE exp8000 frontier672 hitBudget |
| 7 | 703,71,582 → 660,72,533 | 1 | RESTART oldExp40 → 702,71,583 |
| 8 | 702,71,583 → 660,72,533 | 0 | CANCEL exp4 frontier10 |
| 9 | 702,71,583 → 660,72,533 | 66 | OPEN at end: last exp5192 frontier555 |

### IDX6

| # | Start → Goal | Pending | Final |
|---:|---|---:|---|
| 1 | 661,72,438 → 661,73,533 | 1 | CANCEL exp4 frontier14 |
| 2 | 661,71,468 → 660,72,533 | 104 | COMPLETE exp8000 frontier926 |
| 3 | 660,71,468 → 660,72,533 | 104 | COMPLETE exp8000 frontier949 |
| 4 | 659,71,469 → 660,72,533 | 1 | RESTART oldExp36 → 658,71,470 |
| 5 | 658,71,470 → 660,72,533 | 7 | COMPLETE exp564 frontier313 found |
| 6 | 658,71,470 → 660,72,533 | 67 | OPEN at end: last exp5153 frontier749 |

### IDX7

| # | Start → Goal | Pending | Final |
|---:|---|---:|---|
| 1 | 728,63,466 → 661,73,533 | 1 | CANCEL exp4 frontier16 |
| 2 | 728,63,466 → 706,71,488 | 100 | COMPLETE exp8000 frontier619 |
| 3 | 726,63,475 → 706,71,488 | 101 | COMPLETE exp8000 frontier635 |
| 4 | 726,63,475 → 706,71,488 | 87 | OPEN at end: last exp6824 frontier598 |

### IDX8

| # | Start → Goal | Pending | Final |
|---:|---|---|---|
| 1 | 756,63,533 → 661,73,533 | 1 | CANCEL exp4 frontier14 |
| 2 | 756,63,533 → 725,71,533 | 103 | COMPLETE exp8000 frontier563 |
| 3 | 748,63,534 → 725,71,533 | 103 | COMPLETE exp8000 frontier552 |
| 4 | 748,63,534 → 725,71,533 | 84 | OPEN at end: last exp6490 frontier550 |

Repeated CANCEL/RESTART correspond to one-block start changes and to owner invalidation; telemetry only
says `owner_cancelled`, not the caller. Searches that do complete often exhaust 8000.

## 6. DROP state audit

Code facts:

```text
NavPlanner.sucesorCanonico: DROP is not escalada -> state = candidate
NavPlanner.estadoSucesor: DROP -> candidate
NavRouteNodePolicy.routePosition: DROP -> (state.x, feet, state.z)
heuristic/distance: receive state (candidate), not feet
Graph.expand next: state candidate, feet landing
```

Therefore a DROP can use a candidate/air cell as the A* state while its physical landing is `feet`. The
heuristic and successor Y can be wrong relative to the physical feet. Trace first-expansion DROP examples
show this mismatch, e.g.:

```text
from=728,64,600 candidate=729,65,600 kind=DROP
feetFrom=64 feetResult=65 fits=true hasSupport=false
rejection=DESNIVEL edgeValid=false
```

Here the candidate is above the source feet and lacks support, so it is correctly rejected; no **valid**
DROP was present in the first expansions to measure a landed-route example. The code-level mismatch is
confirmed; its runtime impact is not quantifiable from this trace.

## 7. OPEN / slice audit

`NavGraphSearch.Session.advance`:

```text
maxPopsThisSlice = expansionSlice * 2 (8 for slice4)
poll -> if closed contains -> continue
        if expansions >= max -> budget
        closed.add; expansions++
        neighbors with better g -> closed.remove, reinsert
```

Stale OPEN entries for a re-opened state remain. They can consume the 8-pop budget (closed skip or stale
re-expansion) before useful nodes. The trace does not log per-slice pops or stale entries, so this is a
**code-level possibility, not measured**. Required future metrics: pops/slice, stale pops, reopens,
useful expansions/slice.

## 8. Confirmed / hypotheses / limitations

**Confirmed**

- IDX3/4/5 `exp=1` searches receive 56 candidates and 0 valid edges.
- First-expansion rejection is dominated by `BREAKABLE/NODO_NO_PISABLE` (40/56) and `DESNIVEL`.
- IDX3/4/5 restart as the mob descends; each new start repeats the geometry.
- IDX1/6/7/8 repeatedly cancel/restart; several 8000-budget exhaustions.
- DROP graph state and physical feet can differ in code.

**Hypothesis**

- The primary remaining blocker is lack of a valid stand/attack position and BUILD/step geometry at the
  current shaft/slope, not A* budget.
- OPEN stale pops may reduce progress but are unmeasured.

**Unknown**

- Exact owner cancellation callers (generic reason only).
- Per-slice pop/stale counts.
- Impact of DROP state/feet mismatch on route quality (no valid DROP example in first expansions).

## 9. Recommendation

1. Instrument `GLOBAL_SEARCH_CANCEL` with caller/tick and `Session.advance` with pops/stale/reopens.
2. Fix candidate generation at the shaft/slope first; do not raise budgets.
3. Separate DROP A* state (feet cell) from action/takeoff state, or make heuristic/goal use feet.
4. Add pure tests for stale OPEN reopens and DROP state/heuristic consistency.
5. Re-run only after static PASS and new authorization.

A2 remains OPEN. No runtime executed.
