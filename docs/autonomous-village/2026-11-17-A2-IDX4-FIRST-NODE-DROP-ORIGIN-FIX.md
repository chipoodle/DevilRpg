# A2 IDX4 — first-node DROP take-off origin fix (PENDING HARNESS)

## Status

**PENDING HARNESS.** ChatGPT modified production source and pure regression coverage on
`feature/autonomous-village`. No Gradle, lint, diagnostics compile, or Minecraft runtime was
executed by ChatGPT.

## Evidence and root cause

Harness tested `2214366a9e82bb365e785c99fca61c754a68e0a1`:

- Static validation PASS; `NAV_DROP_TAKEOFF_POLICY_TEST` 9 assertions.
- A2 Run 1 `a2n-20261116-211919`: FAIL, 5/8 strict, 5/8 perimeter.
- IDX4 mob `596,77,498`, route DROP landing `597,78,499`, runtime dy=+1.
- `routeIndex=0`; no predecessor existed, so executor left `origenPlan=null`.
- The report did not observe `DROP_REJECTED_NON_DESCENDING`; that event was opt-in behind
  `devilrpg.a2n.traceNav.verboseDrops`, so absence of the event alone did not prove the guard was bypassed.

Source inspection confirmed `plannedFrom` is recorded as the exact mob position when an accepted route
is installed. It is the valid predecessor for the first route node only. For later DROP nodes, the
immediate previous route node remains the correct origin. The executor previously ignored
`plannedFrom` at route index 0 and only resolved origins from a previous node when index > 0.

## Production correction

- Added `NavDropTransitionPolicy.origenPlanificado(routeIndex, routeStart, previousNode)`.
  - index 0 resolves to the recorded route start;
  - later indices resolve only to their immediate predecessor;
  - missing origin remains null; no synthetic coordinates are invented.
- Updated `NavExecutor` DROP execution to resolve the origin through this policy.
- Added bounded `DROP_TAKEOFF_ORIGIN_RESOLVED` trace for route index 0, including route start,
  live position, landing, and resolved origin.
- Existing `origenFisicamenteAlcanzado` remains strict: exact take-off Y and horizontal adjacency.
  If the mob is below/away from the planned origin, the existing `ACERCARSE-ORIGEN-DROP` movement
  is issued. Once at origin, all existing landing, support, clearance, contiguity, and safe-fall
  validations still apply. If the route start itself is the live y77 position, the landing y78
  remains rejected and the existing exclusion/replan path handles it; the guard is not weakened.
- Extended `NavDropTakeoffPolicyTest` from 9 to 12 assertions:
  first-node route-start resolution, later-node predecessor resolution, and missing-origin behavior.
- Existing test registration in `build.gradle` is unchanged.

## Commits

- `b763b11245f0fdf1864c49cfd0100e451f40abd8` — resolve route take-off origin policy.
- `2518d70396d6c500efb241f522c6f53eef6a022c` — add three policy assertions.
- `183b76675761a69f2ca2b501dc02311cba412572` — use first-node route origin in executor.

## Required Harness validation

1. Fast-forward sync and confirm exact current branch HEAD.
2. Run `.gradlew.bat test`, `python tools/lint_aldea.py --strict`,
   `git diff --check`, and `.gradlew.bat compileJava -Pa2nDiagnostics=true`.
3. Confirm `NAV_DROP_TAKEOFF_POLICY_TEST: OK (12 assertions)` and
   `NAV_ROUTE_NODE_POLICY_TEST: OK (6 assertions)`.
4. Only after static PASS, run A2 Run 1.
5. For IDX4 capture `plannedFrom`, routeIndex, route node, live position, resolved origin,
   `DROP_TAKEOFF_ORIGIN_RESOLVED`, `ACERCARSE-ORIGEN-DROP`, physical arrival at take-off,
   final DROP validation, and whether an alternative route is selected.
6. Confirm no height, support, collision, safe-fall, expansion-budget, or strict-entry criteria changed.
7. IDX7 BUILD/900-expansion and IDX8 invalid-local-waypoint remain independent; do not modify them.
8. No Run 2 unless Run 1 reaches 8/8 strict. No full 12k until five consecutive 8/8 strict runs.

## Current project status

- A2: OPEN; last Run 1 5/8 strict.
- IDX4: fix pending Harness.
- IDX7 / IDX8: OPEN, unchanged.
- C4 focalized: prior PASS [1,0,0], 12/12 resumed; save/reload OPEN.
- Full 12k: NOT RUN.
- Original A2/C4 worlds and all prior artifacts must remain intact.
