# A2 Phase 49A — stair diagnostic instrumentation (2026-11-18)

## Scope

Instrumentation-only production change. Stair selection, placement, cooldown cadence, movement,
budgets and navigation predicates are unchanged.

- Previous production baseline: `e1530ab30591a26237d9ae64c1e9cfefbcbb27f2`
- Implementation commit: `9d6709f551f394e0ac7be69160c2aa46419d8058`
- Branch: `feature/autonomous-village`
- Harness validation: PENDING on the exact implementation SHA.

## Added diagnostics

- `STAIR_BUILDER_ENTER`: tick, live position, target, pending, anchor, cooldown and count.
- `STAIR_BUILDER_COOLDOWN`: exact delay before/after decrement and CADENCE_WAIT reason.
- `STAIR_BUILDER_ANCHOR_REFRESH`: live support below the mob and previous/new anchor.
- `STAIR_BUILDER_CANDIDATE`: base, anchor, live position/support, candidate, dy, direction and geometric rejection.
- `STAIR_BUILDER_RETURN`: tick, cooldown, anchor, final motivo/rechazo.

Per-candidate tracing is separately gated to avoid high-volume logs:

```text
-Ddevilrpg.a2n.traceStair=true
-Ddevilrpg.a2n.traceStairCandidates=true
```

Both flags are required for per-candidate events. Existing lifecycle tracing still works with only
`traceStair=true`.

## Harness validation request

1. Preserve all local/untracked files and original worlds. Sync without destructive reset.
2. Verify exact HEAD `9d6709f551f394e0ac7be69160c2aa46419d8058`.
3. Run Gradle tests, strict lint, diff-check and diagnostics compile.
4. If static checks pass, execute A2 Run 1 only with both trace flags.
5. Correlate every IDX3 builder entry/return, cooldown decrement, candidate, placement, traversal and anchor refresh.
6. Establish whether cooldown is actually reached after traversal and why the next accepted candidate repeats
   `607,93,601`, using recorded live support/base/candidate coordinates.
7. Harness must not modify production. No Run2 or Full12K. Do not modify original A2/C4 worlds.
8. IDX4 horizontal-zero is separate; do not claim Phase46 runtime validated without a deterministic fixture.

## Acceptance

Identify the first exact point where the next lower stair is not generated. If traces remain incomplete or
multiple branches remain possible, report `INSUFFICIENT_EVIDENCE`; do not infer a fix from stale fields.

A2 remains OPEN at 4/8 strict until a new runtime result proves otherwise.
