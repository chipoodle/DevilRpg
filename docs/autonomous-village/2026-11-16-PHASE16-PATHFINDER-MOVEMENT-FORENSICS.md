# Phase 16 — Pathfinder Transaction and Movement Pipeline (2026-11-16)

## Git baseline

- Branch `feature/autonomous-village`.
- Initial local/remote HEAD: `e7ac1d80a3dd32f24b2dff604824033eae981337`.
- No divergence. Diagnostic-only probe; no gameplay changes.

## Diagnostic run

**`a2n-20261116-094243`** — DIAGNOSTIC, NOT ACCEPTANCE.
- 8/8 spawn, 5/8 perimeter, **3/8 strict**: idx1 512, idx2 667, idx6 649.
- idx3/idx5 perimeter only; idx4/idx7/idx8 outside.
- C4 **PASS** `[1,0,0]`.

The earlier stuck run `041520` was invalid (no world tick) and is excluded.

## IDX8 — pathfinder transaction captured

UUID `d2c87ca7-f8a9-435a-86c0-053c25e3688b`:

```text
tick=3302 requested=746,65,538 start=747,63,538
bbox=747.2230,63.0000,537.8543 -> 747.8230,64.9500,538.4543
currentNodes=1 currentCanReach=false currentDone=false
evaluator=WalkNodeEvaluator
retryNull=false retryCanReach=false retryNodes=1
retryPath=747,63,538:WALKABLE
```

A second transaction:

```text
tick=3401 requested=745,66,538 start=747,64,538
retryPath=746,64,538:WALKABLE retryNodes=1 retryCanReach=false
```

**Proven:** the pathfinder returns a **single node corresponding to the start/current column**, with
`canReach=false`; it does not produce a node at the requested target. The returned path is not a
solution to the requested destination. The exact reason the target node is excluded (walkability,
support, collision, accuracy) was not decomposed, so the deeper cause remains unproven.

The transaction is not a movement failure: it is a **one-node path generation result**. `isDone=true`
later is a consequence, not proof of arrival.

## IDX7 — required state not reproduced

Phase-16 IDX7 UUID `907df976-448f-403b-9707-02186568d09e` ended `FAILED` with a one-node
`canReach=false` path; exact displacement zero and no solid shapes in bbox.

```text
pos=717.197800328,65.000000000,468.467663083
delta=0.000000000,0.000000000,0.000000000
pathNodes=1 pathCanReach=false pathDone=true state=FAILED
```

The required **path exists + `canReach=true` + not done** state did not reproduce. Per-stage movement
pipeline evidence (PathNavigation.tick/followThePath/move-control consumption) was therefore **NOT
CAPTURED**. No equivalent conclusion is claimed from the FAILED state.

## Collision

No solid collision shapes intersect either sampled bbox (`solidShapesInBBox=0`). This does **not**
exclude collision along a proposed movement; collision remains **NOT PROVEN**.

## C4

Unchanged. `[0,0,0]` remains zero Brain `JOB_SITE` memories, not a sentinel. Phase 16 C4 passed with
`[1,0,0]`. No contract or worker change.

## Proven / unproven

Proven:
- IDX8 pathfinder returns a one-node path at the start/current column with `canReach=false`; no target node.
- IDX7 Phase-16 state is FAILED with exact zero displacement; not equivalent to the Phase-14 reachable-path state.

Unproven:
- Why the target node is rejected by `WalkNodeEvaluator` (support/walkability/accuracy).
- The movement pipeline for the missing IDX7 reachable-path state.
- Any collision along proposed movement.
- IDX5 post-cooldown behavior (UNKNOWN).

## Tests

```text
gradlew test: PASS
lint_aldea.py --strict: OK
git diff --check: PASS
```

## Worlds

Unchanged:
- A2 `BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7`
- C4 `11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF`

## Next action

1. Decompose the pathfinder target rejection for IDX8: target block state, support, walkability and
   node-evaluator result for `746,65,538` / `745,66,538`, using a read-only evaluator query.
2. Reproduce IDX7's reachable-path state specifically; if it cannot be reproduced, preserve BLOCKED status.
3. Do not change action range, budgets, recovery or movement until the target-node rejection is proven.
