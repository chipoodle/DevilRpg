# A2 IDX4 — executable vertical DROP take-off recovery

## Status

**PENDING HARNESS.** The production change is published, but ChatGPT did not execute Gradle,
lint, diagnostics compilation, or Minecraft runtime. Do not treat this as a PASS.

## Harness evidence

Run `a2n-20261116-213836`, tested SHA
`b73ca2de14c676f3f83b6b23bcb9f219205f97a8`:

- Static checks PASS; DROP take-off policy 12 assertions; route-node policy 6 assertions.
- A2 Run 1 FAIL: 5/8 strict, 5/8 perimeter. Run 2 and full 12k were not run.
- IDX4 origin resolution and recovery command were correct:
  - planned take-off `589,96,522`
  - live position `589,93,522`
  - landing `589,95,521`
  - `DROP_TAKEOFF_ORIGIN_RESOLVED` and `ACERCARSE-ORIGEN-DROP` emitted.
- Vanilla PathNavigation returned a one-node path at the current cell
  (`589,93,522`), `navDone=true`, movement delta zero. Physical origin was not reached.
- IDX7 and IDX8 remain independent failures. C4 focalized PASS `[1,0,0]`; save/reload OPEN.

## Root cause

The executor sent a same-column target three blocks above the mob directly to vanilla
`PathNavigation.moveTo`. Vanilla returned an already-complete one-node path at the current cell,
so the executor had no executable vertical route. The take-off origin itself was correct.

## Production correction

The DROP branch no longer delegates vertical take-off recovery directly to vanilla navigation.

When the mob is not physically at the planned take-off:

1. Run the existing `NavPlanner.plan` over the validated navigation graph, from the mob's current
   physical cell to the exact take-off cell.
2. Keep the existing local expansion budget (900 by default), use a bounded 12-block horizontal
   recovery radius, and preserve the existing vertical range and terrain policies.
3. Accept only a complete route whose destination is exactly the take-off and whose transitions
   are exclusively `WALK`, `STEP_UP`, or `JUMP_UP`. Recovery routes containing `DROP`, `BREAK`,
   `BRIDGE`, or `BUILD` are rejected; this recovery does not destroy, place, bridge, or fall.
4. Temporarily suspend the original DROP route and traverse the validated recovery route. Each
   recovery waypoint requires exact physical cell occupancy plus real support and collision fit.
   Existing `JUMP_UP` execution is reused; `STEP_UP` additionally uses the existing bounded
   adjacent MoveControl nudge when vanilla navigation completes without movement.
5. Restore the suspended DROP route only after the take-off is physically reached at the exact Y,
   within the existing horizontal tolerance, with support and clearance verified.
6. If planning fails, the route is incomplete, or the recovery route stalls, fail closed: discard
   the suspended route and replan the original mission from the mob's actual position. No direct
   vertical impulse, teleport, or relaxed landing guard is introduced.

New bounded traces:

- `DROP_TAKEOFF_RECOVERY_ROUTE_ACCEPTED`
- `DROP_TAKEOFF_RECOVERY_PLAN_REJECTED`
- `DROP_TAKEOFF_RECOVERY_REACHED`
- `DROP_TAKEOFF_RECOVERY_INCOMPLETE`
- `DROP_TAKEOFF_RECOVERY_ROUTE_STALLED`

## Regression coverage

`NavDropTransitionPolicy.rutaAscensoOrigenSegura` accepts only a complete route to the exact
take-off composed of WALK/STEP_UP/JUMP_UP transitions.

`NavDropTakeoffPolicyTest` now has **16 assertions**, including:
- valid complete walk/step-up recovery route accepted;
- routes containing DROP rejected;
- destructive BREAK routes rejected;
- partial routes with a different destination rejected.

The test remains registered in `build.gradle`; no build registration change was needed.

## Commits

- `df3b9e46598e31a900a30bb75b72bbce6aaad27d` — safe recovery route policy.
- `ebeda4de04c799ec14e61c2e220934aaddf5f3e7` — recovery route policy assertions.
- `2cc3aa50ba32b8421c2c054a54245fbf42606495` — execute validated route and suspend/restore DROP route.
- `d79189aa6b5c642c66ebc2c0c2506bb7b741375c` — fail closed on stalled/oscillating recovery route.
- `d7056617172913ab07ecad74ceed10f620120151` — clarify policy contract.
- `800329f65938041e6ba7b5ef74afa531e1f2c4ba` — clarify executor origin semantics.

## Required Harness validation

1. Safe fast-forward sync to exact current `feature/autonomous-village` HEAD. Preserve local artifacts/worlds.
2. Static checks, in order:
   ```powershell
   .\gradlew.bat test
   python tools/lint_aldea.py --strict
   git diff --check
   .\gradlew.bat compileJava -Pa2nDiagnostics=true
   ```
3. Confirm:
   ```text
   NAV_DROP_TAKEOFF_POLICY_TEST: OK (16 assertions)
   NAV_ROUTE_NODE_POLICY_TEST: OK (6 assertions)
   ```
4. Only after static PASS, execute A2 Run 1. Do not run Run 2 unless Run 1 reaches 8/8 strict.
5. For IDX4 capture:
   - original DROP route and suspended route index;
   - recovery planner start/goal, expansions, budget status, summary and full route;
   - confirmation every recovery transition is WALK/STEP_UP/JUMP_UP;
   - each waypoint's physical arrival, support and fit;
   - `JUMPUP START/LANDED/ABORT` if a jump is selected;
   - `DROP_TAKEOFF_RECOVERY_REACHED` and restored original route cursor;
   - final DROP validation and physical landing.
6. If recovery is rejected/stalled, capture the exact reason and verify normal replanning starts from
   the live position. Do not claim ascent success from a command or path alone.
7. Confirm no changes to safe-drop height, support, collision, protection, or strict entry criteria.
8. IDX7 BUILD/900-expansion and IDX8 invalid-local-waypoint remain independent and untouched.
9. No full 12k until five consecutive 8/8 strict `DENTRO-PARED` runs.

## Current gate

- A2: OPEN; latest run 5/8 strict.
- IDX4: vertical recovery implementation pending Harness.
- IDX7 / IDX8: OPEN, independent.
- C4 focalized: previous PASS `[1,0,0]`, 12/12 resumed; save/reload OPEN.
- Full 12k: NOT RUN.
- Original A2/C4 worlds and previous run artifacts must remain intact.
