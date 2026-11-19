# A2 Phase55 — opt-in global A* first-expansion diagnostics

**Implementation SHA:** `1164004368a784a463293e4d6a9bc6c8fc144474`  
**Parent:** `5af839090be9f16a10d2186bb30ef1d5a646de99`  
**Base checkpoint:** `646a099f53cbaa52e71db0688d0061ef3e657ea9`  
**Branch:** `feature/autonomous-village`  
**Status:** IMPLEMENTED — STATIC VALIDATION PENDING HARNESS

## Scope authorized

Implement only opt-in diagnostic telemetry for the Phase55 first-expansion A* failure. No navigation behavior, graph edges, budgets, cooldowns, collision/support/protection rules, A2/C4 criteria, fixture or world data are changed.

## Source change

Only `src/main/java/com/chipoodle/devilrpg/entity/nav/NavPlanner.java` changed.

New property:

```text
-Ddevilrpg.a2n.traceNav=true
-Ddevilrpg.a2n.traceGlobalSearch=true
```

Both are required: `traceNav` enables the existing Harness sink; `traceGlobalSearch` gates this new telemetry. With either property absent, no new diagnostic events are emitted. Existing `NavTrace` behavior is unchanged.

### Events

- `GLOBAL_SEARCH_START`: UUID, start, goal, exact-goal flag, horizontal radius, vertical range and total expansion budget.
- `GLOBAL_FIRST_EXPANSION_START`: identifies the first expanded state and search envelope.
- `GLOBAL_FIRST_EXPANSION_CANDIDATE`: one event per evaluated candidate in that first expansion, with:
  - candidate position and obstruction kind;
  - exact `Rechazo` enum and `edgeValid`;
  - transition, source feet, resulting feet and cost;
  - body fit, support, one-block head clearance and protection;
  - chunk-loaded status and candidate block-state string;
  - resulting-feet fit/support and BREAK action/stand positions.
- `GLOBAL_FIRST_EXPANSION_END`: candidate and enqueued counts for the first expansion.
- `GLOBAL_SEARCH_COMPLETE`: expansions, remaining frontier, found/budget flags, terminal reason and route-node count.
- `GLOBAL_SEARCH_CANCEL`: UUID, start/goal, progress and `reason=owner_cancelled`.
- `GLOBAL_SEARCH_RESTART`: old/new start and goal, previous expansion count and `reason=search_parameters_changed`.

The per-candidate stream is strictly limited to the first graph expansion of each newly created search. It does not instrument later expansions. The lifecycle events are emitted only when the opt-in flags and existing trace sink are active.

## Invariants preserved

- No vertical-only edge or other graph change.
- No changes to `OFFSETS`, `validarArista` decisions, A* search ordering, per-slice or total expansion budgets.
- No change to collision, support, head-clearance, protection, drop/landing or BREAK predicates.
- No changes to A2/C4 acceptance, fixture setup, cooldowns or runtime state.
- No world reads/writes outside the existing planner calls; no world was loaded or modified by this implementation.

## Validation handoff

**Harness owns all execution.** Synchronize this exact SHA safely, preserve local/untracked work and diagnostic worlds, and run STATIC validation only:

1. diagnostics compile / `compileJava`;
2. `gradlew test`, reporting actual test names and assertion counts;
3. `lint_aldea.py --strict`;
4. `git diff --check`.

Record exact tested SHA, commands, exit codes and outputs. Inspect whether the compiler accepts the `LevelReader.hasChunkAt` call and whether strict lint flags line formatting. Harness must not edit production source.

**No Minecraft launch and no runtime scenario.** The prior one-Run1 authorization was consumed by `a2n-20261119-093456`. Do not retry A2, run C4 standalone, Run2 or Full12K. A2 remains OPEN; no new runtime authorization is granted by this implementation.

## Known diagnostic limitations

- `GLOBAL_SEARCH_CANCEL` reports the generic owner cancellation reason; it does not yet distinguish each caller's semantic cancellation cause.
- The first-expansion candidate event reports `edgeValid` from `validarArista`. Later policy exclusions (for example DROP fingerprint exclusions) can still prevent enqueueing; compare `candidates` and `enqueued` at the expansion end to identify this distinction.
- A final saved NBT remains historical geometry, not a per-tick snapshot. This telemetry captures candidate block state at evaluation time.
- No runtime or static test has been executed by ChatGPT. Implementation is pending Harness validation.
