# A8-F03 — Agent-Owned Forestry Navigation Step Selection

> **CERRADA / ACEPTADA el 10-nov-2026.** Este documento conserva como historia el estado
> **BLOCKED** de su fecha. La corrección de producción (predicado autoritativo
> `TalarStepPlanner.isValidTalarTarget()`, sin caída al objetivo rancio, guardas en
> `navegar()`/`syncNavigationLease()`) y la campaña runtime post-fix están en
> [`A8-F03-FRESH-SESSION-EXECUTION.md`](A8-F03-FRESH-SESSION-EXECUTION.md) §14: C1/C2/C3 y
> portón válido/negativo PASS. No reabrir sin una regresión reproducible.

Status: **BLOCKED**. The next-step decision is not a separable selector in the current
source. A safe transfer requires refactoring accepted navigation state and the private
standing-cell search; this milestone does not implement a speculative duplicate.

## Baseline

```text
branch feature/autonomous-village
HEAD 97220be5453b57387002b9bc91935807cc665274
staged 0
unstaged 26
untracked 159
```

No source files were changed for A8-F03. No NBT, operation, Brain or GoalSelector change.

## Actual call chain

```text
VillagerLumberjackGoal.tick / irAlObjetivo
  -> pasoDeCamino(level)
       -> celdaDePieParaAlcanzar(level, target)     [private 5x4x5 standing-cell search]
       -> VillageManager.pasoParaCruzarElMuro(...)
       -> VillageManager.tironConMemoria(...)        [ENTREGAR branch]
  -> navegar(level, paso)
```

`pasoDeCamino` is not a pure choice among supplied destinations. It depends on mutable Goal
state:

- `sitioDelRestoDe`, `sitioDelResto` — cached standing-cell result per target.
- `entregaCruzandoMuro` — one-shot wall-crossing memory.
- `plazaDelPueblo` — cached street cell for delivery.
- `puntoDePaso`, `mejorDistancia`, `stuckTicks` — progress/abandon accounting in `tick`.
- `fase` — TALAR vs ENTREGAR changes the algorithm.
- `center`, `VillageManager` navigation helpers.

`celdaDePieParaAlcanzar` is private and performs the real “where can I stand to reach this
log” search, including `REACH` and `VillageManager.esCeldaDePie`. It is not exposed as a
planner input.

## Why this cannot be moved as-is

A correct agent-owned step decision would need one of:

1. **Duplicate** the standing-cell search and wall/tiron logic in the agent — explicitly
   forbidden and a second navigation implementation.
2. **Move** `celdaDePieParaAlcanzar` and the TALAR branch into a shared helper, then
   refactor `VillagerLumberjackGoal` to consume it. That is a refactor of accepted
   navigation/delivery code, not a one-responsibility transfer.
3. Have the Goal compute the step and the agent “choose” it — observational only, which
   does not satisfy the milestone.

Option 2 is the safe long-term route, but it requires:
- a new shared `TalarStepPlanner`/`ForestryStepPlanner` API;
- moving private standing-cell logic without semantic change;
- preserving ENTREGAR tiron/wall memory and progress/abandon semantics;
- rerunning focused navigation acceptance.

That is larger than A8-F03 and would risk accepted A5/A6 behavior if done casually.

## What remains from A8-F01/A8-F02

- Agent owns navigation command authority in ENFORCE.
- Agent owns the final TALAR inside/outside target choice.
- Agent acquires/renews/releases leases.
- Legacy Goal still computes `pasoDeCamino` and performs operations.

## Runtime

No A8-F03 runtime was run, because the required decision ownership was not implemented.
Run 217 remains the latest evidence: agent target selection and navigation, reaching
POST-HARVEST but not delivery completion.

## Verification

No source changes; compilation/tests/lint/diff remain at the A8-F02 state:

```text
compileJava = BUILD SUCCESSFUL
NAVIGATION_LEASE_TEST = OK
NAVIGATION_GATEWAY_POLICY_TEST = OK
NAVIGATION_GATEWAY_HARNESS_TEST = OK
lint_aldea.py --strict = PASS
git diff --check = PASS
```

## Smallest safe next step

Extract a shared, read-only/step-planning API:

```text
TalarStepPlanner.next(ServerLevel, BlockPos target, BlockPos workerPos,
                      BlockPos center, StepCache cache)
```

Move `celdaDePieParaAlcanzar` and the TALAR branch of `pasoDeCamino` into it without
changing behavior, have both the Goal and agent use it, then transfer ownership of the
TALAR call site. Keep ENTREGAR tiron/wall state legacy until its own increment.

## Classification

**BLOCKED** for A8-F03 as scoped. The precise blocker is the coupling of `pasoDeCamino` to
private standing-cell search and mutable Goal navigation state. No safe one-responsibility
transfer exists without a shared step-planner refactor.
