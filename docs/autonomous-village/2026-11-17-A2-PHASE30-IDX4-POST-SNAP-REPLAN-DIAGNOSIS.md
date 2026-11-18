# A2 Phase 30 — IDX4 post-snap replan exhaustion (2026-11-17)

## 1. Result

**COMPLETE.** The eight replans are not a planner/geometry dead end. IDX4 obtains a route after the
snap, but the **stale oscillation state** aborts the first route and stores its signature; seven
identical replans then match the stored signature and are rejected without traversing. The counter
reaches 8/8.

- Branch: `feature/autonomous-village`
- Initial HEAD: `b1c69d96fa499b4e67c32a8c406bdd55684771c4`
- Production inspected: `192d0c521f86efa39fe9ffe1d47348fcf7d4d5e6`
- Source run: `a2n-20261117-170935`
- IDX4: `b6039940-c68c-4f08-a535-5c967cdb6464`
- No new runtime, no production/test change.

## 2. Git ancestry discrepancy (Phase 29)

Phase 29 report stated `INITIAL_HEAD=cebca95b`, but the production commit's parent is `4afbac50`.
Actual ancestry:

```text
cebca95b (docs history repair)
  -> 4afbac50 (Phase 28 diagnosis docs)
  -> 192d0c52 (Phase 29 production fix)
  -> b1c69d96 (Phase 29 docs validation)
```

The discrepancy is in the Phase 29 **report label**, not in Git history: `cebca95b` was the older
starting point before the Phase 28 documentation commit; `4afbac50` was the real immediate parent.
No history rewrite is needed or performed.

## 3. Timeline and objective

```text
IDX4 objective = 571,116,528
Mission       = 597,71,533
Failure pos   = 571,115,533
Final message = replans agotados (8/8)
```

State transitions show a route is accepted before the loop:

```text
t544 PLANNING (replans 0/8)
t545 TRAVERSING
t559 PLANNING
t560 RECOVERING
t614 PLANNING
t615 TRAVERSING (replans 1/8)
t616 RECOVERING
t774 PLANNING (replans 1/8)
t775 RECOVERING
t1094 PLANNING (replans 2/8)
t1095 RECOVERING
...
t3094 PLANNING (replans 7/8)
t3095 RECOVERING
t3494 PLANNING (replans 8/8)
t3495 FAILED
```

The cycle after the first two is **20 ticks planning + 379 ticks recovery**, exactly the observed
400-tick cadence: accept/reject, cooldown, repeat.

## 4. Eight replans

| # | Plan entry | Start | Goal | Observed outcome | Counter | Rejection evidence |
|---:|---:|---|---|---|---|---|
| 1 | t545 | `571,117,533` | `571,116,528` | route accepted, TRAVERSING; RECOVERING t560 | 0→1 | route executed `JUMP_UP` t553; exact rejection event missing |
| 2 | t615 | `571,115,533` | `571,116,528` | accepted t615; `SNAP_GUARD` abort t616 | 1/8 | `oscillating=true occurrences=3 recentCells=24` |
| 3 | t775 | `571,115,533` | `571,116,528` | no TRAVERSING; RECOVERING t775 | 1→2 | no route event; consistent duplicate signature |
| 4 | t1095 | `571,115,533` | `571,116,528` | no TRAVERSING; RECOVERING t1095 | 2→3 | same |
| 5 | t1495 | `571,115,533` | `571,116,528` | no TRAVERSING; RECOVERING t1495 | 3→4 | same |
| 6 | t1895 | `571,115,533` | `571,116,528` | no TRAVERSING; RECOVERING t1895 | 4→5 | same |
| 7 | t2295 | `571,115,533` | `571,116,528` | no TRAVERSING; RECOVERING t2295 | 5→6 | same |
| 8 | t2695 | `571,115,533` | `571,116,528` | no TRAVERSING; RECOVERING t2695 | 6→7 | same |
| terminal | t3095 | `571,115,533` | `571,116,528` | plan entry at 7/8, then failure at t3495 | 7→8 | `A2_FALLAR_ENTER` t3495 |

Notes:

- `A2_TRYPLAN_ENTER` exists at t615, t775, t1095, t1495, t1895, t2295, t2695, t3095, t3495.
- The counter values visible at those entries are the pre-plan values: 1,1,2,3,4,5,6,7,8.
- Exact `expansions`, route signature, and per-attempt route nodes are **EVIDENCE_MISSING**; no
  `ROUTE_REJECTED`/signature event was emitted.
- t545/t615 both print `pos=571,115/117,533`; the goal never changes.

## 5. Root cause

### First post-snap abort

At t615 a route is accepted (`state=TRAVERSING`). At t616:

```text
SNAP_GUARD uuid=b6039940... tick=616
 pending=NONE oscillating=true occurrences=3 recentCells=24
 action=abort_route
```

`traverse()` sees `oscillating==true`, drops the route and sets `RECOVERING`. The oscillation history
was not cleared when the Phase 29 snap completed. `recentCells` still contains the pre-snap
A→B→A sequence around `(571,115/116/117,533)`.

### Signature poisoning

In the oscillation branch, `traverse()` executes:

```java
lastRouteSignature = route.signature();
route = null;
state = RECOVERING;
```

The next plan starts at the same `(571,115,533)` with the same goal `571,116,528`, same policies and
same world. The planner is deterministic, so the route signature repeats. In `tryPlan`:

```java
if (acceptedRoute.signature() == lastRouteSignature) {
    routesRejected++;
    replans++;
    state = RECOVERING;
    return;
}
```

No `TRAVERSING` state is entered for attempts 3–8. This explains the identical 400-tick loop and why
the same counter reaches 8/8.

### Not planner/geometry

A route is accepted at t545 and t615 (`state=TRAVERSING`), including a `JUMP_UP` command t553. The
world therefore contains a valid route toward `571,116,528`; the failure is stale executor state, not
absence of geometry. The final position stays `571,115,533` because no route is ever traversed after
t616.

## 6. Phase 29 relation

- Phase 29 fixed the snap/admission conflict and is not reverted.
- Phase 29 clears `fuenteDropPendiente` on `SNAP_COMPLETE` but does **not** clear `recentCells`,
  `oscillating` or `lastRouteSignature`.
- The stale oscillation/signature is therefore a Phase 29 integration gap, not a failure of the
  exclusive snap itself.

## 7. Geometry

```text
pos        = 571,115,533
goal       = 571,116,528 (5 north, 1 up)
liveCell   = 571,115,533
```

No `feetValid`, support or AABB exception is logged at the failure. The planner returned an accepted
route at least twice, so the local neighborhood is navigable. Exact block shapes around
`571,115,533` are **EVIDENCE_MISSING**; no block-state dump was captured.

## 8. Plan/state analysis

```text
plannedFrom: EVIDENCE_MISSING (no trace field)
route cursor: 0 before first route; routes rejected before assignment on attempts 3+
route signature: not logged; deterministic identical inputs imply same signature
expansions: no BUDGET events in the loop; not a 900-expansion failure
cooldown: 400 ticks between attempts, consistent with recovery cooldown
objective: unchanged
mission: unchanged
```

The counter is consumed by **one real oscillation abort plus repeated equivalent plan rejections**,
not by eight distinct geometric alternatives.

## 9. Minimal condition to solve (not implemented)

The post-snap success path must reset or exclude stale oscillation history for a route planned from
the verified source, and must not store the aborted route as `lastRouteSignature` in a way that
poisons all identical replans. Candidate approaches (for Phase 31, not implemented here):

1. Clear `recentCells`/`oscillating` and reset `lastRouteSignature` when `SNAP_COMPLETE` successfully
   replans from the verified source.
2. Distinguish a route rejection caused by oscillation from a genuinely failed signature, so a
   deterministic identical replan can be attempted once after the world/state resets.
3. Do not change budgets, tolerances, support/fit or the 8/8 limit.

## 10. Evidence gaps

- exact eight route signatures;
- exact planner expansion counts per attempt;
- exact route nodes on attempts 3–8;
- block-state dump around `571,115,533`;
- whether the first t559 failure was also oscillation (no SNAP_GUARD event there).

No new runtime was executed; existing artifacts are sufficient to establish the first abort and the
signature-poisoning loop, but not the exact route content.

## 11. Git/status

```text
PRODUCTION_MODIFIED_BY_HARNESS: false
ORIGINAL_WORLDS_MODIFIED: false
RUNTIME_EXECUTED: false (Phase 30 reuses a2n-20261117-170935)
A2_STATUS: OPEN
```

## 12. Artifacts

- `build/a2n/a2n-20261117-170935/nav-trace.log`
- `build/a2n/a2n-20261117-170935/srv.log`
- `build/a2n/a2n-20261117-170935/fin.txt`
- `build/a2n/a2n-20261117-170935/estado.txt`

## 13. Next action

Phase 31 should address the stale oscillation/signature state after `SNAP_COMPLETE` without changing
the snap contract, budgets or physical predicates. Required evidence for the fix: first post-snap
route must enter TRAVERSING and not be aborted by pre-snap oscillation; replans must not be consumed
by an identical rejected signature; A2 Run 1 only after static PASS.
