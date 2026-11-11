# A8-F02 — Agent-Owned Forestry Target Selection

Status: **PARTIAL**. Final TALAR target choice is now made by `VillagerAgent` in ENFORCE.
Candidate generation, reachability checks, next path-step selection and forestry operations
remain in `VillagerLumberjackGoal`. ENFORCE Run 217 reached POST-HARVEST delivery but did
not finish delivery within the 6-minute bound.

## Baseline

```text
branch feature/autonomous-village
HEAD 97220be5453b57387002b9bc91935807cc665274
staged 0
unstaged 26
untracked 158
```

## Selected task type

**TALAR**. It is self-contained enough to transfer the final inside/outside target choice:
the legacy Goal still finds candidate trees and validates them; the agent chooses the
target from those candidates.

## Ownership before and after

Before:

```text
buscarArbolArea/buscarArbol -> encontrado = inside != null ? inside : outside
```

After, in ENFORCE:

```text
buscarArbolArea/buscarArbol -> candidates
VillagerAgent.selectTalarTarget -> final target
VillagerLumberjackGoal uses that target
```

OFF still uses the original ternary and never calls the agent selector.

## Files changed

- `agent/TalarTargetSelector.java` — pure inside-first / nearest-outside choice.
- `agent/VillagerAgent.java` — `selectTalarTarget`, selected target state, diagnostic.
- `entity/goal/VillagerLumberjackGoal.java` — ENFORCE consumes agent selection.
- `debug/A5TestController.java` — test-only mode/diagnostic plumbing (prior).
- `docs/.../A8-F02...` — this report.

No controller, planner, storage, pantry, NBT or operation logic changed.

## How candidates reach the agent

The legacy Goal still calls `buscarArbolArea` and `buscarArbol`. It passes at most one
inside and one outside candidate to `VillagerAgent.selectTalarTarget`. The agent applies
`TalarTargetSelector`: inside first, otherwise nearest outside to the worker. The Goal then
assigns that target and invokes its existing navigation/operation path.

This is a real final-choice transfer, not a duplicate tree search.

## Next navigation step

`pasoDeCamino`/`VillageManager.caminarHacia` remain legacy. The agent owns the **navigation
command** (A8-F01) but not the per-step path selection. This is a remaining gap for a later
increment.

## Runtime evidence — Run 217 (ENFORCE)

```text
AGENT-TARGET TALAR uuid=b89... target=87,97,4 inside=[87,97,4] outside=[]
AGENT-TARGET TALAR uuid=08369220... target=53,97,-17 inside=[53,97,-17] outside=[]
T2-HARVEST TALAR target=53,97,-17
AGENT-AUTH ACQUIRE ... task=... (agent lease)
T2-HARVEST POST-HARVEST DELIVERING target=127,97,20 inv=6
```

The agent selected both TALAR targets, acquired leases and issued navigation. T2 harvested
the selected tree and reached delivery. The fixture did not reach `END` within 6 minutes.

## Compatibility

- OFF remains unchanged.
- A8-F01 agent navigation authority remains active in ENFORCE.
- Existing harvesting/replanting/growing/delivery unchanged.
- No vanilla Brain/GoalSelector change.

## Verification

```text
compileJava = BUILD SUCCESSFUL
NAVIGATION_LEASE_TEST = OK
NAVIGATION_GATEWAY_POLICY_TEST = OK
NAVIGATION_GATEWAY_HARNESS_TEST = OK
lint_aldea.py --strict = PASS
git diff --check = PASS
```

## Remaining limitations

- Only final target choice is agent-owned; candidate generation is legacy.
- Next path-step selection remains legacy.
- ENFORCE runtime reached POST-HARVEST delivery but not delivery completion.
- Target invalidation/replacement is handled by existing Goal logic, not demonstrated as an
  agent-owned decision.
- A7-12 resource non-equivalence remains unresolved.

## Classification

**PARTIAL.** The agent now owns a real TALAR target-selection decision and issues commands
under its lease, but not the full target/next-step lifecycle required for PASS.
