# A2 TunnelActionPlanner — core and integration contract (2026-11-21)

## Status

Pure planner core implemented. Not yet integrated into `NavExecutor`, so no runtime improvement is claimed.

## Contract

`TunnelActionPlanner.straight` emits a bounded straight tunnel plan. It never moves the mob.

Each `Step` carries:

```text
stand       physical cell from which the actions are performed
feetBlock   block to remove at feet level
headBlock   block to remove at head level
post        resulting feet cell after both removals
```

Validation per step:

- protected cells stop the plan;
- both blocks must pass the injected break predicate;
- the post cell must have support;
- diagonal/zero directions are rejected;
- the number of steps is bounded by the caller.

## Integration plan

`NavExecutor` remains the only movement authority:

1. when no route exists or the front is a dead end, request a tunnel plan;
2. for each step, occupy `stand`, acquire the BREAK lease for `feetBlock`, verify air;
3. acquire the next lease for `headBlock`, verify air;
4. move physically to `post`, verify support/AABB/occupancy;
5. only then consume the step;
6. any failure discards the plan and replans.

The tunnel planner must not issue movement commands, bypass protection, or increase budgets.

## Tests

`TUNNEL_ACTION_PLANNER_TEST`: 9 assertions covering bounds, stand/post semantics, protected cells,
unbreakable cells, missing support and invalid directions.
