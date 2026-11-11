# A8-F03-R1 — Shared TALAR Step Planner and Agent Ownership

Status: **PASS for core extraction/ownership; PARTIAL for invalidation coverage**. The
TALAR standing-cell + wall-gate step calculation is now one shared planner. In ENFORCE the
agent calls it and issues the gateway command; OFF calls the same planner from the Goal.

## Baseline

```text
branch feature/autonomous-village
HEAD 97220be5453b57387002b9bc91935807cc665274
staged 0
unstaged 26
untracked 160
```

## Extracted planner

`agent/TalarStepPlanner.java` now owns:

- `Cache` (`lastTarget`, `standingCell`) scoped per worker/task.
- `standingCell(level, log)` — the former private 5×4×5 search with `REACH` and
  `VillageManager.esCeldaDePie`.
- `next(level, target, center, villager, cache)` — standing cell, then
  `VillageManager.pasoParaCruzarElMuro`, explicit `Step(destination, standingCell, wallGate)`.

`VillagerLumberjackGoal.celdaDePieParaAlcanzar` now delegates to the shared
`standingCell`, so the other call sites (harvest/replant/cleanup) use the same algorithm.
No second copy exists.

ENTREGAR wall-crossing/tiron state (`entregaCruzandoMuro`, `plazaDelPueblo`,
`tironConMemoria`) remains in the legacy Goal. It is not carried into the TALAR planner.

## Ownership

### OFF

`pasoDeCamino` non-ENTREGAR uses `TalarStepPlanner.next(...)` with the Goal's
`talarStepCache`. Target preference and navigation path are unchanged.

### ENFORCE, TALAR

```text
VillagerLumberjackGoal.pasoDeCamino
  -> VillagerAgent.nextTalarStep(...)
       -> TalarStepPlanner.next(...)  [agent cache]
       -> returns Step
  -> Goal consumes destination
  -> navegar -> VillagerAgent.navigateTo -> gateway
```

The Goal does not compute a competing TALAR step in ENFORCE. Other phases still use the
shared planner directly from the Goal; ENTREGAR remains legacy.

## Cache semantics

- Goal cache: OFF and non-TALAR phases.
- Agent cache: ENFORCE TALAR.
- Cache invalidates when the target changes; cleared when the planner returns no destination.
- No global mutable cache; no delivery state in the cache.

## Files changed

- `agent/TalarStepPlanner.java` — extracted shared calculation.
- `agent/VillagerAgent.java` — `nextTalarStep` + agent cache.
- `entity/goal/VillagerLumberjackGoal.java` — standing-cell delegation; agent/planner call.
- `debug/A5TestController.java` — test-only diagnostics (prior).

No controller, work planner, storage, pantry, NBT or operation rewrite.

## Runtime evidence — Run 218 (ENFORCE)

```text
AGENT-TARGET TALAR b89... target=53,97,-17
AGENT-TARGET TALAR 7c0960dd... target=53,97,-17
AGENT-STEP TALAR b89... target=87,97,4 step=86,97,4 standing=true gate=false
AGENT-STEP TALAR b89... target=87,97,4 step=86,97,4
T2-HARVEST TALAR target=53,97,-17
T2-HARVEST POST-HARVEST DELIVERING target=127,97,20 inv=5
T2-HARVEST END warehouse=131 delta=3 tick=2078
[A5] FIN PASS detail=natural harvest delivery
```

The agent selected targets, selected the TALAR standing-cell step (e.g. log 87,4 → step
86,4), and completed the natural harvest/delivery. No competing legacy TALAR step was
observed in ENFORCE.

## Verification

```text
compileJava = BUILD SUCCESSFUL
NAVIGATION_LEASE_TEST = OK
NAVIGATION_GATEWAY_POLICY_TEST = OK
NAVIGATION_GATEWAY_HARNESS_TEST = OK
lint_aldea.py --strict = PASS
git diff --check = PASS
```

No new unit test directly exercises `TalarStepPlanner` because its inputs require a
`ServerLevel`; runtime Run 218 is the evidence.

## Remaining limitations

- Target invalidation/replacement was not demonstrated as an agent-owned transition.
- Wall-gate TALAR step was not exercised in this run (`gate=false`).
- Only TALAR step ownership is transferred; ENTREGAR navigation remains legacy.
- Harvest/replant/grow/deliver execution remains legacy.
- No new A7/A8 equivalence campaign was run.

## Classification

**PASS** for extracting one shared TALAR step planner and making the agent the ENFORCE
caller/owner, with full harvest/delivery runtime completion. **PARTIAL** for invalidation
and wall-gate coverage.
