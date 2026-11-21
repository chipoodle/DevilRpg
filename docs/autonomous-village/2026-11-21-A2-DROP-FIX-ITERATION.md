# A2 DROP fix iteration — results and remaining blocker (2026-11-21)

## 1. Result

**PARTIAL — A2 2/8 strict, 2/8 perimeter, C4 PASS.** IDX6’s DROP edge-step stall is fixed. The remaining
six attackers are blocked by terrain enclosures/BREAK post-state and global budget, not by the DROP lip.

## 2. Changes retained

- `NavExecutor`: on DROP edge-step `no_progress`, exclude the exact source→landing edge and the landing
  destination, invalidate route/global and replan. No budget, landing or support predicate changed.
- `NavVerticalSearchPolicy`: each horizontal neighbor probes safe-drop descent, same level and +1 ascent
  only; same-column probes descent only. This is the set of valid single-edge vertical semantics.
- `NAV_VERTICAL_SEARCH_POLICY_TEST`: 5 assertions, registered in `policyTest`.

An experimental one-shot step-down command and an experimental terrain-breach fallback were tested and
removed because they did not move the entity or did not escape the shaft.

## 3. Runtime evidence

Run `a2n-20261121-123432`, seed 12345:

```text
C4_PASS, guards 4/4, workers 12/12, jobSite=[1,0,0]
A2: 2/8 strict, 2/8 perimeter, 2 deaths
IDX1 strict t1425
IDX6 strict t1226
```

The DROP fix worked: IDX6 crossed the previous dead landing `658,70,471` and entered the perimeter/strict
at `656,71,471`, then advanced. Before the fix IDX6 aborted 17 times at `658.506,71,470.700`.

## 4. Remaining blocker

IDX2/3/4/5/7/8 do not cross. IDX3/5 sit in 1×1 stone shafts at `594,74/75,...`; the global search produces
a valid BREAK edge into the adjacent stone, but the post-break graph state is the still-solid target cell.
A* cannot expand through it, so the BREAK edge is a dead end. IDX7/8 exhaust the global budget; IDX4 is
still in a leaf/DROP cycle; IDX2 local planning fails.

This is a graph/post-state defect, not a budget or landing defect. A proper fix must simulate the broken
target as an open feet cell during search (or model BREAK as move-to-beyond with validated support) and then
let the executor break/advance atomically. That changes the graph contract and needs a dedicated design,
not another threshold change.

## 5. State

Production changes are limited to the DROP exclusion and vertical-search policy. Static compile/test pass.
No worlds were modified. A2 remains OPEN; C4 integrated PASS is not standalone validation. Full12K remains
blocked.
