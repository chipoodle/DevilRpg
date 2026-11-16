# Phase 17 — Isolated Evaluator and IDX7 Forensics (2026-11-16)

## Git baseline

- Branch `feature/autonomous-village`.
- Initial local/remote HEAD: `d2a4e0084cef31c202348b4a77d38e38bca03a0b`.
- No divergence. Diagnostic-only changes; no gameplay changes.

## Source audit — `PathNavigation.createPath`

Installed sources: `neoforge-21.1.249-sources.jar`.

`PathNavigation.createPath(BlockPos, int)` calls:

```java
createPath(ImmutableSet.of(pos), 8, false, accuracy)
```

That protected method contains (lines 138–139):

```java
if (this.path != null && !this.path.isDone() && targets.contains(this.targetPos)) {
    return this.path;   // cached active path, no pathfinder search
}
```

Therefore the Phase-16 `tracePathTransaction()` retry was **not an independent query**. When the requested
target equals the active `targetPos`, `createPath` returns the current one-node path verbatim. The
Phase-16 retry result (`retryPath=747,63,538`, `canReach=false`) is exactly the active path, not a new
`PathFinder` result. **Observer/selection effect confirmed.** The diagnostic has been corrected to log
`PATH_TRANSACTION_CACHED_RETURN` and skip the cached query.

This does not invalidate the observable fact that the **production path was one node and
non-reaching**; it invalidates only the claim that an independent search produced it.

## IDX8 — geometry

The production path transaction at tick 3302:

```text
requested=746,65,538 start=747,63,538
currentNodes=1 currentCanReach=false currentDone=false
```

was created by the production `moveTo`. Its target conversion and the target block geometry were **not
captured** in that run. No independent evaluator query has been performed because the documented
`createPath` overload returns the active path. Target-node rejection remains **UNKNOWN / BLOCKED**.

No claim is made that `746,65,538` is intrinsically non-walkable, unsupported, or dimension-blocked.

## IDX7 — reproduction

The Phase-16 run did not satisfy the required gate. IDX7 ended `FAILED` with a one-node non-reaching
path and zero displacement. The specific required state:

```text
path != null && path.canReach() && !isDone()
state != FAILED && state != RECOVERING
```

was never observed. **BLOCKED — REQUIRED IDX7 STATE NOT REPRODUCED.** No per-stage movement pipeline
claim is made. Phase-14's reachable-path/no-movement state remains unreproduced and unexplained.

## Collision

No solid shapes intersected the sampled bboxes in Phase 15/16. Collision along proposed movement is
**NOT PROVEN**.

## IDX5

No extension. Tick-6164 cooldown boundary remains **UNKNOWN**.

## C4

No changes. Phase-16 diagnostic passed `[1,0,0]`; `[0,0,0]` remains zero Brain `JOB_SITE` memories.
Contract redesign requires separate authorization. Save/reload remains OPEN.

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

## Proven / unproven

Proven:
- `createPath(BlockPos,int)` can return the active path when its target matches `targetPos`; the
  Phase-16 retry was therefore cached, not independent.
- Production IDX8 path was one-node/non-reaching; production IDX7 Phase-16 was FAILED, not the required
  reachable-path state.

Unproven:
- Exact target-node rejection reason for IDX8.
- IDX7 reachable-path movement mechanism.
- Collision along proposed movement.
- IDX5 post-cooldown behavior.

## Next action

Use a genuinely isolated evaluator/pathfinder (or a read-only `WalkNodeEvaluator` setup with a separate
`PathNavigationRegion`), or capture the original production `moveTo` path before installing it. Do not
reuse `createPath` while an active matching path exists.
