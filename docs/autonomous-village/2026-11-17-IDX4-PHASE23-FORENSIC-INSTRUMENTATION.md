# IDX4 Phase 23 — targeted forensic instrumentation (2026-11-17)

## Purpose

The Phase 22 runtime and the subsequent read-only extraction established two separate unknowns:

1. The exact bounded recovery A* reaches/expands `589,95,521`, but the cardinal edge toward the take-off `589,96,522` is rejected during edge validation. The previous run did not preserve the rejection reason or the candidate's geometry/policy snapshot.
2. The original DROP toward `589,95,521` receives movement commands but does not physically reach that landing. The trace lacks periodic physical landing predicates and an explicit cursor-advance event.

This phase adds **diagnostics only**. It does not alter movement acceptance, route costs, budgets, collision checks, protection, DROP completion criteria, or recovery behavior.

## Source changes

### `NavPlanner.java`

Inside exact-goal planning only, the graph adapter now emits `DROP_TAKEOFF_EXACT_EDGE_DIAG` for the candidate directly below the exact take-off when its predecessor is a cardinal neighbor at the same feet Y.

The event records:

- source, candidate and exact goal;
- `NavObstruction.Kind`;
- accepted/rejected result and exact `Rechazo`;
- transition, feet-from/feet-to and cost;
- protected status;
- candidate fit, support and one-block head clearance;
- resulting feet-cell fit/support;
- the existing read-only `NavObstruction.snapshot`.

The filter is deliberately sparse and is inactive unless `NavTrace` is enabled. It does not emit a line for every A* edge.

### `NavExecutor.java`

For active DROP route nodes, `DROP_LANDING_PROGRESS` is emitted at most once per 10 ticks and records:

- target landing and live block cell;
- exact entity coordinates and delta movement;
- on-ground state;
- landing-height predicate;
- support under the live cell;
- combined valid-landing predicate;
- within-tolerance and distance squared;
- target-cell fit/support.

When a DROP node is actually reached and the route cursor increments, `DROP_CURSOR_ADVANCE` records the landing, previous index, next index and route length. The existing `DROP_DONE` remains tied to physical arrival and is not reinterpreted.

## Commits

- Planner diagnostic: `81890f7e80f2281481b479968d149437f59e9346`
- Executor diagnostic: `f43249eabda633569277ee73fa64835273a558e8`

These commits contain telemetry only. No runtime, Gradle test, lint or diagnostics compile has been executed by ChatGPT.

## Harness validation protocol

1. Sync exact live branch HEAD and verify no local changes are overwritten.
2. Run static checks first:
   - `.\gradlew.bat test`
   - strict `lint_aldea.py`
   - `git diff --check`
   - `compileJava -Pa2nDiagnostics=true`
3. Confirm diagnostics compile and existing policy test counts remain unchanged.
4. If static validation passes, execute **A2 Run 1 only** in the authorized disposable world/harness flow. No original worlds.
5. Capture all `DROP_TAKEOFF_EXACT_EDGE_DIAG` records for IDX4, correlating `from=589,95,521`, `candidate=589,95,522`, `goal=589,96,522`.
6. Capture ordered `DROP_LANDING_PROGRESS`, `DROP_DONE` and `DROP_CURSOR_ADVANCE` for landing `589,95,521`. Do not count events for landing `589,93,522`.
7. Do not run Run 2 unless Run 1 reaches 8/8 strict. Do not run the 12k campaign until five consecutive strict 8/8 runs.

## Acceptance / safety invariants

- A2 remains OPEN until the existing strict criteria pass.
- No budget increase.
- No weakening of collision, support, headroom, protection, route, or physical landing checks.
- No teleport, direct velocity/position manipulation, or destructive shortcut.
- IDX7 and IDX8 remain independent issues.
- C4 save/reload remains OPEN.
- Harness must not edit production source or original worlds.
