# A2 — grounded invalid DROP source recovery (2026-11-17)

## Status

**Implementation published; Harness validation pending.** ChatGPT has not executed Gradle tests, strict lint, diagnostics compilation, or Minecraft runtime.

- Previous run: `a2n-20261117-142016`
- Previous tested SHA: `37c0aa702b44d94c6d5db506d023ec65d1b8`.
- Result: A2 FAIL 1/8 perimeter and 1/8 strict; 2,102 `DROP_TAKEOFF_UNSUPPORTED_WAIT` events.
- Previous dominant state: `grounded=true feetValid=false support=false realAabbFits=true` with stale and live source both `661,72,459`.
- Current implementation branch: `feature/autonomous-village`.
- Current code checkpoint: `e80f99d5acd4bf987524d4ab5d5a79ad5392fdd6`.
- Current branch HEAD (documentation-only descendant): `a4f4b58098bfc78769bfa6999fa7e132d5a29861`.

## Root cause

The executor already had an AABB-based source resolver (`NavSourceCellPolicy.uniqueAdjacentCandidate` and bounded horizontal source snap), but the new unsupported-takeoff admission returned through `DROP_TAKEOFF_UNSUPPORTED_WAIT` before invoking it. A grounded mob with an invalid integer `blockPosition()` therefore waited indefinitely even though the real AABB could overlap a neighboring supported feet cell.

A second admission gap existed when the first DROP node had no recorded `plannedFrom`: the physical-origin guard was conditional on a non-null origin, so a missing origin could bypass the take-off admission. Phase 24's IDX4 report did not include enough `DROP_START` context to prove whether that exact path caused its repeated DROP; new telemetry records it explicitly.

## Production correction

1. Added `NavDropTransitionPolicy.debeRecuperarFuenteActual` to distinguish grounded-invalid source recovery from airborne natural-physics wait.
2. Added `resolverFuenteActualYReplanificar`:
   - Requires the mob to be grounded before source resolution.
   - Selects only one cardinal same-Y cell overlapping the real AABB, using `NavSourceCellPolicy.uniqueAdjacentCandidate`.
   - Requires valid feet/support through `esPieValido`, plus real-AABB fit at the candidate center.
   - Uses horizontal `MoveControl` only (speed 0.35); no jump, teleport, direct velocity, BREAK, BUILD, or BRIDGE.
   - Requires physical candidate occupancy, 0.20 horizontal center tolerance, support and current real-AABB fit.
   - Discards the stale route/cache and replans the strategic mission from the verified source. It does not plan back to the unsupported stale take-off.
   - If no unique safe candidate exists for 80 ticks, or the selected candidate is not physically reached within 80 ticks, emits an explicit unresolved/stalled event and fails closed.
3. Airborne mobs continue to wait for natural physics. A grounded, already-valid supported source replans directly.
4. If `plannedFrom` is missing, the executor may infer the origin only from the current live cell after grounded + valid feet + support + real-AABB fit are all verified. Otherwise it invokes source recovery (grounded) or waits for natural landing (airborne).
5. `DROP_START` now records `originPlan`, `grounded`, `liveFeetValid`, `liveSupport`, and `liveAabbFits` to diagnose IDX4's repeated DROP at command time.

No navigation budgets, search radii, safe-drop limits, collision/support/protection/headroom checks, or landing/arrival criteria were increased or relaxed.

## Tests

Four policy assertions were added. Expected output:

```text
NAV_DROP_TAKEOFF_POLICY_TEST: OK (51 assertions)
```

The assertions are authored but **not executed by ChatGPT**. Harness must confirm the exact count.

## Harness instructions

1. Safely sync exact live branch HEAD `a4f4b58098bfc78769bfa6999fa7e132d5a29861` (the production code checkpoint is `e80f99d5acd4bf987524d4ab5d5a79ad5392fdd6`); preserve local/untracked artifacts and worlds.
2. Static gate first: `gradlew test`, `lint_aldea.py --strict`, `git diff --check`, and `compileJava -Pa2nDiagnostics=true`.
3. Confirm `NAV_DROP_TAKEOFF_POLICY_TEST: OK (51 assertions)`, plus existing source-cell and route-node suites.
4. If static passes, run **A2 Run 1 only** in a disposable world.
5. For the former 661,72,459 blocker, capture `DROP_TAKEOFF_UNSUPPORTED_SOURCE_RECOVERY`, `DROP_TAKEOFF_SOURCE_CELL_INVALID`, `DROP_TAKEOFF_SOURCE_CELL_CANDIDATE`, `DROP_TAKEOFF_SOURCE_SNAP_STARTED`, `DROP_TAKEOFF_SOURCE_SNAP_REPLAN`, and the first route accepted from the new source. Confirm no A* starts before physical support/fit admission.
6. For IDX4 UUID `bbadffa7-09c6-49fa-8adf-c1cd118e30f5`, capture `DROP_TAKEOFF_ORIGIN_RESOLVED`, `DROP_TAKEOFF_LIVE_ORIGIN_VERIFIED` or `DROP_TAKEOFF_ORIGIN_MISSING_SOURCE_RECOVERY`, every `DROP_START` with its new physical-origin fields, `DROP_LANDING_PROGRESS`, `DROP_DONE`, and `DROP_CURSOR_ADVANCE`. Confirm the same landing is not repeatedly started without movement or a safe recovery transition.
7. Report all eight UUIDs, perimeter/strict counts, C4 result, exact tested SHA, and world hashes.

## Gates and preservation

- No A2 Run 2 unless Run 1 is 8/8 strict.
- No Full 12K until five consecutive 8/8 strict runs.
- C4 last observed PASS `[1,0,0]`; save/reload remains OPEN.
- IDX7/IDX8 remain independent; do not silently fold their status into A2.
- A2 remains OPEN until runtime acceptance passes.
- Original A2/C4 worlds and all local-only artifacts remain protected.
- Harness must not modify production source.
