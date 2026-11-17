# A2 — DROP route-node reconstruction fix (2026-11-16)

## Source evidence

Forensic source: `a2n-20261116-190555`, tested SHA
`2d47d628724a677269e1c3d895759154f56a52c9`.

Confirmed failures:

- IDX4: route node `DROP@597,78,499` while the actual landing feet were inferred as
  `597,77,499`; repeated executor rejection reported landing above (`dy=1`).
- IDX8: global partial route contained 81 front waypoints, but the local selector found
  `0/64` valid standing objectives. Code inspection confirmed global route reconstruction
  used `Step.state()` rather than `Step.feet()` for DROP nodes.
- IDX7 is separate: local planner hit `901/900` expansions in 10/10 attempts after
  `BUILD-FALLIDO`. No change to IDX7's budget or BUILD behavior is included in this patch.

Full evidence: `2026-11-16-A2-RUN1-FORENSIC-EXTRACT.md`.

## Production correction

Added `NavRouteNodePolicy.routePosition(state, transition, feet, breakStand)`:

- DROP: retain state X/Z and use validated `feet` for route-node Y.
- BREAK: retain the physical stand position; the solid action target remains in
  `NavRoute.Node.actionPos`.
- WALK, STEP_UP, JUMP_UP, BUILD and other transitions retain their canonical graph state.

Applied the policy to all three reconstruction paths in `NavPlanner`:

1. Global route when the mission is found.
2. Global partial/frontier route when the global search exhausts its budget.
3. Local A* route reconstruction, using the `piesDe` value recorded for each state.

This preserves transition labels, predecessor/state keys, BREAK action metadata, route budgets,
collision checks and drop safety limits. It does not widen safe-drop limits or relax validation.

## Regression test

Added `NavRouteNodePolicyTest` and registered it in `policyTest`:

- DROP maps `(597,78,499)` plus feet=77 to `(597,77,499)`.
- DROP retains X/Z.
- WALK and STEP_UP retain canonical state.
- BREAK retains physical stand separately from graph state.
- BREAK with missing stand retains the prior fallback behavior.

## Git commits

- `866d81692cc8d0d821662276dbe9ebc2cf57b0fc` — route-node policy.
- `38a15bb75c0eb80d82c001f12a90f4c9a36c1fc0` — apply policy to global/local reconstruction.
- `cdf4c9f53e88734aa4b348e7b48a8bbc5ea6b729` — regression test.
- `b19f413c5551135c1eef19aeec03638592fa4625` — register test.

## Validation status

**PENDING HARNESS.** ChatGPT did not execute Gradle, lint, diff-check, diagnostics compilation or Minecraft runtime.
Harness must fetch the exact current branch HEAD and run static validation before a new A2 Run 1.

IDX7 is intentionally deferred until the DROP correction's effect is measured. Do not change local
expansion budgets, BUILD eligibility, collision, safe-drop limits or strict-entry criteria in this patch.

A2 remains **OPEN**. Run 1 must reach 8/8 strict before Run 2; five consecutive 8/8 strict runs
are required before full 12k. Full 12k was not run. C4 save/reload remains OPEN. Original A2/C4
worlds and prior run artifacts must remain untouched.
