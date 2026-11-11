# A8-F03-R2 — Target Invalidation & Wall-Gate Runtime Acceptance

Status: **PARTIAL**. Target replacement is demonstrated from Run 218; null/removed target
invalidation is implemented and verified by source/compile, but not exercised in a new
runtime. Wall-gate traversal remains NOT RUN because no focused gate fixture was executed.

## Baseline

```text
branch feature/autonomous-village
HEAD 97220be5453b57387002b9bc91935807cc665274
staged 0
unstaged 26
untracked 162
```

No protected save, NBT change, prohibited Git operation, commit or push.

## Target identity and replacement

- Target identity is `BlockPos` equality in `TalarStepPlanner.Cache.lastTarget`.
- Agent/Goal task identity is separate (snapshot task ID).
- Cache is per worker: Goal cache for OFF/non-TALAR, agent cache for ENFORCE TALAR.
- A changed target recomputes `standingCell` and cannot reuse the old step.

Run 218 natural replacement evidence:

```text
AGENT-STEP TALAR b89... target=53,97,-17 step=52,97,-17
...
AGENT-STEP TALAR b89... target=87,97,4 step=86,97,4
```

The step changed with the target; no A-step was reused for B.

## Cache invalidation

`TalarStepPlanner.next(...)` now:

- clears the cache when target is null;
- clears the cache when `requireLog` is true and the target block is no longer a log;
- recomputes when the target changes;
- returns `Step(null, ...)` on invalidation.

Agent TALAR calls `requireLog=true`; Goal TALAR calls the same. Non-TALAR phases use the
same planner with `requireLog=false`, preserving existing behavior.

No runtime target-removal fixture was run. The behavior is source/compile verified, not
runtime accepted.

## Wall-gate

`TalarStepPlanner.next` still calls `VillageManager.pasoParaCruzarElMuro` and returns
`Step.wallGate`. `gate=false` remains the only observed runtime case (Run 218).
A focused outside-wall TALAR fixture was not executed; wall-gate traversal is **NOT RUN**.

## OFF vs ENFORCE

- OFF: Goal calls `TalarStepPlanner.next(..., goal cache)`.
- ENFORCE: Goal calls `VillagerAgent.nextTalarStep(...)`, agent uses its cache and the
  gateway.
- Same planner, no duplicate algorithm.
- ENTREGAR unchanged.

## Files changed

- `agent/TalarStepPlanner.java` — target null/removed cache invalidation.
- `agent/VillagerAgent.java` — TALAR sets `requireLog=true`.
- `entity/goal/VillagerLumberjackGoal.java` — TALAR requireLog; shared planner call.
- `docs/.../A8-F03-R2...` — this report.

No harvest/replant/grow/delivery logic changed.

## Verification

```text
compileJava = BUILD SUCCESSFUL
NAVIGATION_LEASE_TEST = OK
NAVIGATION_GATEWAY_POLICY_TEST = OK
NAVIGATION_GATEWAY_HARNESS_TEST = OK
lint_aldea.py --strict = PASS
git diff --check = PASS
```

## Runtime evidence

Run 218 (ENFORCE):

```text
AGENT-TARGET TALAR b89... target=53,97,-17
AGENT-STEP b89... target=53,97,-17 step=52,97,-17
AGENT-TARGET TALAR b89... target=87,97,4
AGENT-STEP b89... target=87,97,4 step=86,97,4
T2-HARVEST END warehouse=131 delta=3
A5 FIN PASS
```

## Classification

| Scenario | Status |
|---|---|
| R2-A target replacement | PASS (Run 218) |
| R2-B target invalidation | PARTIAL (code/compile only) |
| R2-C wall gate | NOT RUN |
| R2-D compatibility | PASS (same planner; ENTREGAR legacy) |
| Overall | **PARTIAL** |

## Remaining limitations

- No runtime removed-target/invalidation fixture.
- No wall-gate traversal fixture; `gate=true` unobserved.
- Only TALAR step ownership; ENTREGAR legacy.
- Harvest/delivery execution remains legacy.
