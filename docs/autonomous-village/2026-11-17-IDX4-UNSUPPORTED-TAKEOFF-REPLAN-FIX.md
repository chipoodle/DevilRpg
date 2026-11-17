# IDX4 — Unsupported DROP take-off admission fix (2026-11-17)

## Status

**Implementation published; Harness validation pending.** No Gradle test, strict lint, diagnostics compile, or Minecraft runtime was executed by ChatGPT.

- Previous forensic run: `a2n-20261117-122621`
- Previous tested SHA: `69f066cc63c01925d60b037d2a1317c5224d3b6b`
- Previous result: A2 FAIL 5/8 strict, 5/8 perimeter; C4 PASS `[1,0,0]`.
- Implementation branch: `feature/autonomous-village`
- Implementation checkpoint: `c17ec47e7277a687fa428841d64ca8c8089e68c1`

## Root cause from Phase 23 + source inspection

The Phase 23 evidence showed:

- Planned DROP origin/take-off: `589,96,522`, with `feetSupport=false`.
- The one-block-below candidate `589,95,522` also had no support and was classified as DROP, not STEP_UP.
- The mob was airborne at the planned origin, then landed at supported cell `589,93,522`.
- The requested landing `589,95,521` never emitted `DROP_DONE` or `DROP_CURSOR_ADVANCE`; it physically landed elsewhere.

Source inspection found a missing runtime invariant:

- `NavDropTransitionPolicy.origenFisicamenteAlcanzado(...)` checked coordinates/Y only. It did not require grounded state, a valid feet cell, support, or real AABB fit.
- The DROP actuator could therefore treat coordinate proximity as take-off readiness even when the planned origin had no support.
- `describirDescenso` used the same coordinate-only predicate for `cercaOrigen`.

## Production correction

1. Added a runtime overload of `origenFisicamenteAlcanzado` requiring all four physical predicates: grounded, valid feet, support, and real AABB fit, in addition to the existing coordinate condition.
2. Added `replanificarDesdeOrigenSoportado`: an unsupported planned take-off is abandoned only after the mob is grounded in a valid, supported cell where its real AABB fits.
3. In the DROP executor:
   - Validate the planned take-off cell itself (`esPieValido`, support, and center AABB fit).
   - Never execute a DROP from an unsupported planned origin.
   - While airborne, stop navigation and let normal physics reach a landing; do not issue a jump, teleport, velocity impulse, build, break, or bridge.
   - Once grounded on a valid supported source, discard the stale DROP route and its global cache, then replan the strategic route from the actual source.
   - If no grounded supported source exists yet, wait fail-closed and emit `DROP_TAKEOFF_UNSUPPORTED_WAIT`.
4. `describirDescenso` now uses the grounded/supported runtime origin predicate as well.

No budget, search radius, safe-drop limit, collision, headroom, protection, landing support, or arrival tolerance was changed. The original landing is not declared reached by proximity.

## Tests

`NavDropTakeoffPolicyTest` adds four assertions covering:
- Supported grounded origin admission.
- Rejection of airborne, invalid, unsupported, and non-fitting origins.
- Replanning from a grounded valid supported source when the planned take-off is unsupported.
- No replan from airborne/invalid/unsupported/non-fitting sources, or when the take-off is already supported.

Expected updated test output: `NAV_DROP_TAKEOFF_POLICY_TEST: OK (47 assertions)`.

The existing test registration is unchanged. These are authored assertions, **not yet executed**.

## Changed files and commits

- `NavDropTransitionPolicy.java` — `59f9419329051ffb3385101fea667a4582655640`
- `NavDropTakeoffPolicyTest.java` — `3ba86c0c29a30dcc70d3e828bf4109d3bba8ddee`
- `NavExecutor.java` — `91aff8df92ae2ecf709ec999d56d65201bc78bac`, `b166fd3741887f7542f4223e1715f009426ed633`, `c17ec47e7277a687fa428841d64ca8c8089e68c1`

## Harness instructions

1. Sync safely to exact branch HEAD `c17ec47e7277a687fa428841d64ca8c8089e68c1`; preserve all local/untracked artifacts.
2. Run static gate first:
   - `.\gradlew.bat test`
   - `lint_aldea.py --strict`
   - `git diff --check`
   - `.\gradlew.bat compileJava -Pa2nDiagnostics=true`
3. Confirm the updated policy test reports 47 assertions.
4. If static passes, execute **A2 Run 1 only** in the disposable world.
5. For IDX4, capture:
   - `DROP_TAKEOFF_ORIGIN_RESOLVED`
   - `DROP_TAKEOFF_UNSUPPORTED_WAIT`
   - `DROP_TAKEOFF_UNSUPPORTED_REPLAN`
   - subsequent route acceptance/start and the actual first transition
   - `DROP_START`, `DROP_LANDING_PROGRESS`, `DROP_DONE`, and `DROP_CURSOR_ADVANCE`
6. Confirm no `DROP_START` is emitted from an unsupported or airborne origin. Confirm any replacement route starts at a grounded, supported, fitting live cell and its selected DROP landing is physically reached.
7. Report all eight entrants, strict/perimeter counts, UUIDs, and exact tested SHA.

## Gates and preservation

- No Run 2 unless Run 1 is 8/8 strict.
- No Full 12K until five consecutive 8/8 strict runs.
- C4 remains last observed PASS `[1,0,0]`; save/reload remains OPEN.
- IDX7 and IDX8 remain independent and untouched.
- A2 remains OPEN until runtime acceptance passes.
- Original A2/C4 worlds and all local-only artifacts remain protected.
- Harness must not modify production source.
