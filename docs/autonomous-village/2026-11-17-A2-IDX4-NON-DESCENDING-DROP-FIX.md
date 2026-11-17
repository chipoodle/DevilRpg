# A2 IDX4 — reject non-descending DROP edge (2026-11-17)

## Runtime evidence

Harness run `a2n-20261116-201759`, tested SHA `6d294c1d8d4c82bc8b706e412ff87d5862b8901e`: static PASS, A2 FAIL at 4/8 strict and 5/8 perimeter. IDX4 started with DROP at routeIndex=0; no predecessor node existed, so `origenPlan=null` and `ACERCARSE-ORIGEN-DROP` was not issued. Mob position was `596,77,498`, landing `597,78,499`, and every attempt was rejected with `dy=1`.

## Source-level diagnosis

`NavExecutor` plans the first local route from `mob.blockPosition()` (`NavPlanner.plan(..., mob.blockPosition(), ...)`). `NavPlanner` initializes the start feet to `start.getY()` and validates every edge using `piesDesde` and `piesHasta`.

`NavPlanner.validarArista()` previously allowed a candidate classified as `DROP` whenever its vertical delta stayed within the generic climb/drop limits. It did not require the validated landing feet to be strictly below the source feet. Thus an edge could be labelled DROP even though the landing was above the actual take-off. For IDX4, the observed source feet are Y=77 and landing feet Y=78: this is not a recoverable DROP edge. Using `plannedFrom` as an origin would only reproduce the same Y=77 source and would not solve the impossible transition.

## Correction

- Added `NavDropTransitionPolicy.descensoFisicoValido(takeoffFeet, landingFeet)`, requiring `landingFeet < takeoffFeet`.
- `NavPlanner.validarArista()` now rejects a DROP transition before it enters A* when the validated landing is level with or above the source feet. It records the optional bounded `DROP_REJECTED_NON_DESCENDING` trace and classifies the edge as `DESNIVEL`.
- Other transition types and BUILD fallback remain unchanged. Safe-drop limits, collision checks, budgets and siege acceptance criteria are unchanged.
- Extended `NavDropTakeoffPolicyTest` from five to nine assertions: strict descent, equal-height rejection, above-takeoff rejection and valid multi-block descent.

## Commits

- `a4a4fd29` — strict physical descent policy.
- `cd2024d0` — reject non-descending DROP edges in planner.
- `865f9229` — four regression assertions.

## Validation status

**PENDING HARNESS.** ChatGPT did not execute Gradle, lint, diff-check, diagnostics compile or Minecraft runtime. The prior static PASS applies only to SHA `6d294c1d...` and does not validate this correction.

## Required Harness validation

1. Fetch the live `feature/autonomous-village` HEAD; safe fast-forward only and preserve all artifacts/worlds.
2. Run Gradle tests, strict lint, `git diff --check`, and diagnostics compile.
3. Confirm `NAV_DROP_TAKEOFF_POLICY_TEST: OK (9 assertions)`.
4. If static validation passes, execute A2 Run 1 only using the unchanged strict configuration.
5. For IDX4, capture the planner source feet, candidate, landing feet, `DROP_REJECTED_NON_DESCENDING` event (if verbose tracing is enabled), chosen alternative route and final outcome. Verify no impossible DROP enters the accepted route.
6. IDX7 BUILD/900-expansion and IDX8 invalid-local-waypoint remain independent; do not modify them in this run.
7. No Run 2 unless Run 1 reaches 8/8 strict. No full 12k until five consecutive 8/8 strict runs.

A2 remains OPEN. C4 save/reload remains OPEN. Original A2/C4 worlds must remain untouched.