# CURRENT CHECKPOINT — 2026-11-17 grounded invalid DROP source recovery (PENDING HARNESS)

**Code checkpoint:** `e80f99d5acd4bf987524d4ab5d5a79ad5392fdd6` (the handoff documentation is committed afterward; always sync the live branch HEAD).
**Production code checkpoint:** `e80f99d5acd4bf987524d4ab5d5a79ad5392fdd6`.
**Detailed report:** `2026-11-17-A2-GROUNDED-INVALID-DROP-SOURCE-RECOVERY-FIX.md`.

- Phase 24 tested `37c0aa702b44d94c6d5db506d023ec65d1b8`: A2 FAIL 1/8 perimeter and 1/8 strict; 2,102 unsupported-source WAIT events. The dominant state was grounded with invalid feet/support but a fitting real AABB.
- Root cause: grounded-invalid source bypassed the existing AABB source resolver and fell into the airborne WAIT branch. It now snaps only to a unique cardinal same-Y supported cell overlapped by the real AABB, using horizontal MoveControl, then replans from the physically verified source.
- If no unique source exists or the snap cannot be physically completed within 80 ticks, the executor emits an explicit failure and fails closed; no infinite wait loop.
- A missing `plannedFrom` can no longer bypass DROP origin admission: the live cell is accepted only if grounded, valid, supported and real-AABB fitting; otherwise source recovery/wait occurs.
- `DROP_START` now logs originPlan and physical source predicates for IDX4 forensic diagnosis.
- Policy test expected count is now 51 assertions (47 + 4). Tests authored, **not executed by ChatGPT**.
- **PENDING HARNESS:** sync exact HEAD above; run Gradle tests, strict lint, diff-check and diagnostics compile first. Confirm 51 assertions. If static PASS, A2 Run 1 only.
- Capture source recovery/snap/replan events for the seven blocked entrants and new DROP_START origin fields plus landing/cursor events for IDX4 UUID `bbadffa7-09c6-49fa-8adf-c1cd118e30f5`.
- No Run 2 unless Run 1 is 8/8 strict. No Full 12K until five consecutive 8/8 strict runs. No budget increases or relaxed geometry/support/protection/landing criteria.
- A2 remains OPEN. C4 last PASS [1,0,0], save/reload OPEN. IDX7/IDX8 remain independent. Original worlds/artifacts protected. Harness must not modify production.
- ChatGPT has not run tests, lint, diagnostics compile, or Minecraft runtime.

---

# CURRENT CHECKPOINT — 2026-11-17 IDX4 unsupported DROP take-off guard (PENDING HARNESS)

**This checkpoint supersedes the Phase 23 immediate-work instructions below. Historical results remain unchanged.**

- Phase 23 established that planned take-off `589,96,522` is unsupported; the mob later landed at supported `589,93,522`, while the requested landing `589,95,521` had no `DROP_DONE` or cursor advance.
- Source review found coordinate-only take-off admission: `origenFisicamenteAlcanzado` did not require grounded state, valid feet, support, or actual AABB fit.
- ChatGPT added a runtime admission overload requiring grounded + valid feet + support + actual AABB fit, and uses it in both DROP execution and descent validation.
- If the planned take-off itself is unsupported, the executor now refuses the DROP. It waits while airborne; once the mob is grounded in a valid, supported, fitting live cell, it discards the stale DROP route/cache and replans the strategic route from that actual source.
- New traces: `DROP_TAKEOFF_UNSUPPORTED_WAIT` and `DROP_TAKEOFF_UNSUPPORTED_REPLAN`.
- Added four policy assertions; expected `NAV_DROP_TAKEOFF_POLICY_TEST: OK (47 assertions)`.
- Report: `2026-11-17-IDX4-UNSUPPORTED-TAKEOFF-REPLAN-FIX.md`.
- Implementation commits: policy `59f94193`; test `3ba86c0c`; executor `91aff8df`, `b166fd37`, `c17ec47e`.
- **Current implementation HEAD before handoff docs:** `c17ec47e7277a687fa428841d64ca8c8089e68c1`. Documentation commits may advance it; always fetch live HEAD before sync.
- **PENDING HARNESS:** sync exact live HEAD safely; static tests/lint/diff/diagnostics compile first; if static passes, A2 Run 1 only. Capture both new traces plus DROP_START/LANDING_PROGRESS/DONE/CURSOR_ADVANCE.
- No Run 2 unless 8/8 strict; no 12k until five consecutive 8/8 strict. No budget increase or weakened geometry/support/protection/landing criteria.
- A2 remains OPEN. C4 last observed PASS [1,0,0], save/reload OPEN. IDX7/IDX8 remain independent and untouched. Original worlds and local artifacts remain protected.
- ChatGPT has not executed tests, lint, diagnostics compile, or Minecraft runtime.

---

# CURRENT CHECKPOINT — 2026-11-17 IDX4 Phase 23 targeted forensic instrumentation

**This section supersedes older immediate-work statements below. Historical entries remain unchanged.**

- Phase 22 read-only forensic extraction confirms the exact recovery A* expands `589,95,521`; the candidate `589,95,522` is generated but rejected inside `validarArista`. Exact rejection reason remains UNKNOWN because no candidate-level geometry/policy evidence was recorded.
- The same run separately confirms that DROP landing `589,95,521` receives commands but has no verified physical landing, no matching `DROP_DONE`, and no cursor advance. The earlier `DROP_DONE` for `589,93,522` is a different route cycle and must not be counted.
- ChatGPT added sparse, opt-in-only diagnostics without changing movement semantics:
  - `NavPlanner.java`: `DROP_TAKEOFF_EXACT_EDGE_DIAG` captures the exact one-block-below-goal candidate, edge rejection enum, transition, feet values, protected status, fit/support/headroom and cell snapshot.
  - `NavExecutor.java`: `DROP_LANDING_PROGRESS` records physical landing predicates every 10 ticks; `DROP_CURSOR_ADVANCE` records cursor advancement only after the existing physical arrival check succeeds.
- Commits: planner `81890f7e80f2281481b479968d149437f59e9346`; executor `f43249eabda633569277ee73fa64835273a558e8`.
- Detailed instructions: `2026-11-17-IDX4-PHASE23-FORENSIC-INSTRUMENTATION.md`.
- **PENDING HARNESS**: static validation first on exact live HEAD, then A2 Run 1 only if static passes. No Run 2 unless 8/8 strict; no 12k until five consecutive 8/8 strict.
- A2 remains OPEN. No budget increase or weakened collision/support/headroom/protection/landing conditions. IDX7/IDX8 remain independent. C4 save/reload OPEN.

---

# CURRENT CHECKPOINT — 2026-11-17 IDX4 AABB source recovery

**This section supersedes older "Immediate work" statements below.** Historical entries remain unchanged.

- Forensic root cause confirmed in `2026-11-17-IDX4-GROUNDED-INVALID-FORENSICS.md`: mob AABB spans Z=507.66..508.26 and physically rests on cobblestone (593,86,508), while integer blockPosition=(593,87,507) checks air below at (593,86,507).
- ChatGPT implementation published through production checkpoint `b02affb19eb8e37824682090ba99f793a04aea94`.
- New source resolver selects exactly one cardinal same-Y supported cell overlapped by the actual AABB, validates the actual translated AABB at candidate center, then issues bounded horizontal MoveControl only. It requires physical center occupancy and rechecks feet/support/current-AABB fit before A*.
- Recovery route now permits at most one planner-validated DROP of exactly one block, because resolved source (593,87,508) is one block above original take-off (593,86,507). It rejects longer/multiple drops and all destructive/build/bridge transitions. Temporary route start is saved/restored so the recovery DROP resolves the correct origin.
- Added `NavSourceCellPolicyTest` (10 assertions); `NavDropTakeoffPolicyTest` now expects 43 assertions; `NavRouteNodePolicyTest` remains 6. Harness Phase 21 executed all three: PASS 10/40/6.
- Detailed implementation: `2026-11-17-IDX4-AABB-SOURCE-RECOVERY-FIX.md`.
- Harness static gate at `1e65c024` was BLOCKED only by a missing test import; the historical result is preserved in `2026-11-17-A2-RUN1-AABB-SOURCE-RECOVERY-RESULT.md`.
- ChatGPT added `import net.minecraft.core.BlockPos;` to `NavDropTakeoffPolicyTest.java` (test-only), commit `c75165c2135cccbfcc3eddd40abe09adce6766e8`.
- Confirmed `build.gradle` registers `NavDropTakeoffPolicyTest`, `NavSourceCellPolicyTest`, and `NavRouteNodePolicyTest` in `policyTest`.
- Harness Phase 21 at `77b635d4`: static PASS; source snap reached `596,77,499` with feet/support/real-AABB true; old local recovery A* returned empty at `900/900`; A2 FAIL 5/8 strict, C4 PASS `[2,0,0]`. Historical result: `2026-11-17-A2-RUN1-AABB-SOURCE-RECOVERY-PHASE21.md`.
- Harness Phase 22 at `c17459b7`: static PASS (10/43/6); exact A* bounded and `hitBudget=false`, but stopped at `589,95,521` vs takeoff `589,96,522`; 39 rejections, no route accepted/restored/reached. A2 FAIL 5/8 strict, C4 PASS `[1,0,0]`.
- Harness result: `2026-11-17-A2-RUN1-EXACT-BOUNDED-RECOVERY-PHASE22.md`.
- Harness Phase 23 at `69f066cc`: static PASS (10/43/6); exact-edge diag shows candidate `589,95,522` is air/no-support, classified DROP, and exact goal `589,96,522` has `feetSupport=false`. A2 FAIL 5/8, C4 PASS `[1,0,0]`.
- Harness result: `2026-11-17-A2-RUN1-PHASE23-EDGE-DIAG-RESULT.md`.
- Harness Phase 24 at `37c0aa70`: static PASS (10/47/6); A2 **regressed to 1/8 strict, 1/8 perimeter**. `DROP_TAKEOFF_UNSUPPORTED_WAIT` fired 2102 times with `grounded=true feetValid=false support=false`; only idx1 entered. C4 PASS `[1,0,0]`.
- Harness result: `2026-11-17-A2-RUN1-UNSUPPORTED-TAKEOFF-PHASE24.md`.
- **NEXT: ChatGPT must fix the unsupported-source wait loop before another A2 run; do not increase budget or relax support.**
- ChatGPT implemented exact-goal bounded recovery A* using the existing validated global A* core: horizontal radius 4, vertical envelope 3, unchanged 900 hard cap; no partial/approximate goal acceptance and no BREAK/BUILD/BRIDGE or multi-block DROP.
- Failed temporary planning now preserves the original strategic route, cursor and `plannedFrom`; retries use cooldown. Successful recovery emits `DROP_TAKEOFF_RECOVERY_ROUTE_RESTORED` with route signature/cursor/origin.
- The 0.20-block radial source-center tolerance is retained: Phase 21's 0.19 offset is within tolerance and had grounded/feet/support/real-AABB evidence. Exact floating-point center is not required.
- Detailed fix and Harness protocol: `2026-11-17-IDX4-EXACT-RECOVERY-ASTAR-FIX.md`.
- Expected `NAV_DROP_TAKEOFF_POLICY_TEST`: 43 assertions. **PENDING HARNESS**; ChatGPT has not run tests, lint, diagnostics compile or runtime on this implementation.
- **NEXT: Harness validates exact live HEAD, static gate first, then A2 Run 1 only.**
- Do not run A2 Run 2 unless Run 1 reaches 8/8 strict. Do not run full 12k until five consecutive 8/8 strict runs.
- IDX7/IDX8 untouched. C4 last observed PASS [1,0,0], save/reload OPEN. A2 remains OPEN pending runtime evidence.
- Harness must not modify production source. Original A2/C4 worlds and local artifacts remain protected.

---

# Documentation reconciliation — 2026-11-17

ChatGPT reviewed the shared branch collaboration documents after the Harness report
`a2n-20261117-020800` and found two contradictory legacy documents. The permanent role policy and
the explicit ChatGPT/Harness contract already correctly assigned implementation to ChatGPT and
testing/evidence to Harness, but `GOVERNANCE.md` and `WORKFLOW.md` still described ChatGPT as
read-only and a local-first orchestrator as the workflow.

Those contradictions have now been corrected directly on `feature/autonomous-village`:

- `docs/autonomous-village/GOVERNANCE.md` — commit `c670f2ba2b979b9ebe1ceaa81d1cbd7cd072765e`.
- `docs/autonomous-village/WORKFLOW.md` — commit `9fbc63344acf6e0a575efe1d22b80509cf5b4d5d`.

The corrected documents now explicitly state:
- `feature/autonomous-village` is the shared communication channel and durable source of truth.
- ChatGPT directly investigates, edits production source, reviews diffs, commits and publishes on that branch.
- Harness synchronizes the branch locally, runs all requested static/runtime tests, records metrics/logs and publishes dated reports/documentation to the same branch.
- ChatGPT reviews every Harness report and Harness-authored documentation change, reconciles errors/contradictions and updates the current handoff.
- Harness does not independently implement production source fixes.
- The user's pasted Harness report is a handoff; ChatGPT must also inspect the repository report/source.
- Local Harness worktree is the execution environment, not a competing source-of-truth branch.

No production source was changed in this documentation reconciliation. No tests or Minecraft runtime
were run by ChatGPT. The latest empirical status remains the Harness result for tested SHA
`b3064596d457e6e7877607a534f8889a4214045d`: A2 FAIL (5/8 strict, 5/8 perimeter), C4 FAIL
`[0,0,0]`; IDX4 grounded recovery still has no accepted route; IDX7/IDX8 remain independent.
Run 2 and Full 12K remain prohibited by the acceptance gates.

**Next implementation:** inspect the live source for IDX4's airborne landing-anchor stabilization
(change documented as `2026-11-17-A2-IDX4-LANDING-ANCHOR-FIX.md`), verify its safety/geometry and
instrumentation, then make a focused correction to obtain a genuinely grounded supported recovery
source. Investigate C4 `assignmentsClean=false` separately. Harness validates the exact new SHA.

---

# NEXT SESSION — A2 / C4 Stability

## Latest checkpoint — 2026-11-17 airborne DROP take-off recovery correction (PENDING HARNESS)

- Harness Run 1 `a2n-20261117-001624` tested `7aed2c683852969c323524bbed7d95965c330790` and returned static PASS but A2 **FAIL 4/8 strict, 5/8 perimeter**. IDX4 emitted `DROP_TAKEOFF_STEP_UP_ASSIST` while `onGround=false`, then fell to y119 and stalled below the STEP_UP waypoint. The old trace does not record whether the auxiliary A* plan was accepted while grounded, so that plan-time state is not retrospectively proven. Source review did establish that no grounded/source-support admission guard existed; the new code adds one and records plan-admission state.
- Initial implementation commit: `bd638e92dd86214dd8af3a17f1a6726daaea39e8`; final source checkpoint including plan-admission telemetry: `190bb6578906068e33c3a835feb2cacf7bc93085`. Recovery planning now requires a grounded, valid, supported, fitting source cell. While falling, the original DROP route remains pending and the executor waits/retries rather than accepting a route from a transient cell.
- STEP_UP recovery now keeps horizontal MoveControl while airborne but does not request a jump until grounded. If it lands at an elevation that makes the active one-block edge stale, the auxiliary recovery is abandoned fail-closed and the strategic mission is replanned from the live cell. No teleport, direct velocity impulse, destructive action, budget increase, or weakened support/fit/landing predicate.
- Policy tests extended to cover grounded source admission, adjacent one-block geometry, and airborne/unsupported rejection. Tests are authored, **not run by ChatGPT**.
- Harness must run static checks first on exact live HEAD, then A2 Run 1 only. Capture `DROP_TAKEOFF_RECOVERY_WAIT_GROUNDED`, recovery plan start coordinates, `DROP_TAKEOFF_STEP_UP_ASSIST` only with `onGround=true` and `sourceSupport=true`, exact waypoint occupancy, take-off reached, restored DROP cursor and physical landing. If Run 1 is not 8/8 strict, do not run Run 2.
- IDX7 BUILD/900-expansion and IDX8 global/local route failures remain independent. C4 `[1,0,0]` remains PASS; save/reload OPEN. A2 OPEN; no full 12k until five consecutive strict 8/8 runs.


## Latest checkpoint — 2026-11-16 DROP route-node feet correction (PENDING HARNESS)

- Forensic source: Run 1 `a2n-20261116-190555`, SHA `2d47d628724a677269e1c3d895759154f56a52c9`.
- IDX4/IDX8 confirmed route reconstruction defect: DROP route nodes used graph-state Y instead of validated landing `Step.feet`.
- ChatGPT added `NavRouteNodePolicy`; global complete routes, global partial/frontier routes and local routes now map DROP nodes to `(state.x, feet, state.z)`.
- BREAK still uses its physical stand as `Node.pos` and preserves the solid target in `Node.actionPos`. Other transitions retain canonical graph state.
- Added `NavRouteNodePolicyTest` (6 assertions) and registered it in `policyTest`.
- Code commits: `866d8169`, `38a15bb7`, `cdf4c9f5`, `b19f413c`. Detailed report: `2026-11-16-A2-DROP-ROUTE-FEET-FIX.md`.
- **PENDING HARNESS**: no Gradle, lint, diff-check, diagnostics compile or runtime has been executed by ChatGPT. Harness must validate exact live HEAD, then rerun A2 Run 1 only.
- IDX7 BUILD-FALLIDO/local 900-expansion exhaustion is intentionally unchanged pending measurement of this DROP fix. Do not increase budgets or weaken BUILD/collision/entry criteria.
- A2 remains OPEN; Run 1 must pass 8/8 before Run 2; five consecutive strict 8/8 before full 12k. C4 save/reload remains OPEN.


Code checkpoint SHA: `7692a2cc7cbb4420531a7ff1ac0dc379badd10bf`; subsequent handoff commits are documentation-only. Always fetch the live branch HEAD before synchronization.

## Latest checkpoint — 2026-11-16 Phase19 independent exclusion/backtrack fixtures

- Harness run `a2n-20261116-174648` on `8b389672`: `break-runtime` **PASS**. Both targets air, exact 40-tick retry, exact stand occupancy and both strategic missions resumed. This is fixture validation, not A2 siege acceptance.
- Current code adds independent modes `-Da2n.mode=break-edge-excluded` and `-Da2n.mode=nav-backtrack` in `A2NavHarness.java`. They must run in separate disposable worlds with trace enabled.
- Code review found a backtrack selector edge case: it selected the latest valid history cell even when within arrival tolerance, then aborted without trying older cells. ChatGPT added a distance-aware selector overload; executor now skips too-near cells using the existing 2.25 squared arrival tolerance. Added three unit assertions, including the 16-cell history bound.
- Added read-only runtime diagnostics for recovery history/backtrack state and current BREAK fingerprint exclusion.
- Fixture acceptance is strict: BREAK requires two unverified actions on the same edge 40 ticks apart, active unchanged fingerprint exclusion, a different BREAK edge selected, both blocks still solid and cursor not past BREAK. Backtrack requires two no-progress failures, exactly one selected historical cell, history <=16, exact physical return, <=1.5 blocks/tick, strategic mission preserved and resumed.
- Detailed report: `2026-11-16-PHASE19-BREAK-EXCLUSION-BACKTRACK-FIXTURES.md`.
- **PENDING HARNESS**: sync exact HEAD, run tests/lint/diff-check/diagnostics compile, then run both fixture modes independently. ChatGPT has not executed these validations.
- A2 remains OPEN; no full 12k before five consecutive strict 8/8 `DENTRO-PARED` runs. C4 save/reload remains OPEN.

## Latest checkpoint — 2026-11-16 Phase19 BREAK retry observation window

- Harness run `a2n-20261116-173828` on `08cf0e8c`: both targets air, retry exactly 40 ticks, Scenario A resumed. Scenario B remained at X=737 (target X=737) at the 20-tick deadline while state was TRAVERSING and movement commands targeted X=738/739. The original physical-crossing predicate had not yet passed.
- ChatGPT changed **only** `src/main/java/com/chipoodle/devilrpg/debug/A2NavHarness.java` in commit `8f2b9138937b23ff853ec839a0db9f7559d0673e`.
- Fixture now allows a bounded 100 ticks after retry destruction for the mob to physically cross the target column. The original predicate `objective().equals(goal) && blockPosition().getX() > target.getX()` is unchanged; no pass is granted for merely having an objective or movement command.
- Added `BREAK_FIXTURE_RETRY_PROGRESS` samples every 10 ticks with position, objective, state, route node and executor diagnostics. Deadline failure includes final retry diagnostics.
- No production source, A2/C4 original world, cooldown, reach, LOS, collision, planner budget or acceptance threshold changed.
- Detailed report: `2026-11-16-PHASE19-BREAK-RETRY-OBSERVATION-WINDOW.md`.
- **PENDING HARNESS**: run static checks and a fresh disposable `break-runtime` on the exact current branch HEAD. Require both mission-resumption flags and exact 40-tick retry. A2 remains OPEN; no 12k before five consecutive strict 8/8 runs.

## Latest checkpoint — 2026-11-16 Phase19 BREAK retry fixture timing

- Harness run `a2n-20261116-172549` proves the production stand nudge, exact stand occupancy, both target-air results and exactly 40-tick retry. Scenario A mission resumption also passes.
- The only failure was fixture timing: it evaluated retry mission progress in the same tick that the second actuator destroyed the block, before the retry mob received a subsequent entity-movement tick.
- ChatGPT changed **only** `src/main/java/com/chipoodle/devilrpg/debug/A2NavHarness.java` in commit `4c3f446aeaa893db7ff7bcfc1c5f690f94e37332`. It defers the retry mission-progress predicate until `now > breakRetrySecondTick` and allows up to 20 ticks after destruction for the same objective + physical-progress predicate. If still false at the deadline, the fixture reports FAIL; it does not silently pass.
- Production `NavExecutor` and its nudge/BREAK behavior are unchanged.
- Detailed handoff: `docs/autonomous-village/2026-11-16-PHASE19-BREAK-RETRY-FIXTURE-TIMING.md`.
- **PENDING HARNESS**: validate the exact current branch HEAD with Gradle tests, strict lint, diff-check, diagnostics compile, then rerun disposable `break-runtime`. Require both mission-resumption flags, exact 40-tick retry and both targets air. No A2/C4 world use.


## Latest checkpoint — 2026-11-16 Phase19 BREAK stand approach correction

- Harness run `a2n-20261116-163239` is a behavioral FAIL: both BREAK plans and LOS hits are valid, but `moveTo` returns a completed one-node path for the adjacent stand and the mobs do not move.
- ChatGPT published commit `11be611d90c4d65772813550c2a365a524ee5147` in `NavExecutor.ejecutarBreakDesdePosicionDeAtaque`: when vanilla navigation is done before exact stand occupancy, it uses the existing bounded `NavAdjacentApproachPolicy` + `MoveControl.setWantedPosition` fallback, guarded by Manhattan distance, AABB fit and support.
- Exact physical occupancy remains mandatory before the actuator. No reach, LOS, protection, collision, cooldown, planner/recovery budget or acceptance threshold changed.
- New trace event: `BREAK_STAND_NUDGE`; existing `BREAK_STAND_APPROACH` remains.
- Detailed handoff: `docs/autonomous-village/2026-11-16-PHASE19-BREAK-STAND-APPROACH-FIX.md`.
- **PENDING HARNESS**: ChatGPT has not run Gradle/lint/diagnostics compile or Minecraft runtime on this SHA. Harness must validate exact current branch HEAD, then rerun disposable `break-runtime` and capture physical positions, both target-air checks, 40-tick retry and mission resumption.


## Latest checkpoint — 2026-11-16 Phase19 BREAK LOS correction

- Previous Harness run `a2n-20261116-160714` was **INVALIDO**: static checks passed, but both planner routes were empty and no mob/actuator started. This is not a BREAK actuator FAIL.
- Source correction is published through `9200ac31a350d9013b2abb8f39ce2b421fc9605a`: BREAK LOS now clips toward the target collision-shape center instead of the old projected endpoint below a thin pane. Planning-time `BREAK_LOS_DIAG` and bounded `BREAK_STAND_CANDIDATE` events were added; fixture trace initialization now occurs before planning.
- Detailed handoff: `docs/autonomous-village/2026-11-16-PHASE19-BREAK-LOS-CORRECTION.md`.
- This correction is **NOT YET VALIDATED** by Harness. Tests, diagnostics compilation and runtime must be executed on the exact current branch HEAD after fetching.
- Harness must preserve local artifacts, fast-forward safely, run Gradle tests, strict lint, diff-check and diagnostics compile, then execute a new disposable `break-runtime` world with `-Ddevilrpg.a2n.traceNav=true`.
- Required evidence: `BREAK_STAND_CANDIDATE`, `BREAK_LOS_DIAG` (legacy and new ray endpoints plus actual clip hit), route summaries, `nav-trace.log`, server logs, `fin.txt`, `estado.txt`, exact SHA.
- Harness must not change production source. If the new ray still fails, return the exact rejection branch and clip hit to ChatGPT.
- A2 remains OPEN (last known 5/8 strict, 6/8 perimeter); no 12k until five consecutive 8/8 strict runs. C4 save/reload remains OPEN.


Read first, in order:

1. `docs/autonomous-village/CHATGPT-COLLABORATION-POLICY.md`
2. `docs/autonomous-village/NEXT-SESSION.md` (this file)
3. `docs/autonomous-village/CHATGPT-HARNESS-WORKFLOW.md` (permanent division of responsibilities and handoff protocol)
4. `docs/autonomous-village/CONTINUAR.md`
5. `docs/CONTINUAR.md`
6. `docs/PENDIENTE.md`
7. `docs/autonomous-village/2026-11-15-PHASE2-FIXES.md`
8. `docs/autonomous-village/2026-11-15-A2-IDX8-C4-FORENSICS.md`
9. `docs/autonomous-village/2026-11-15-PHASE5-A2-VARIABILITY.md`
10. `docs/autonomous-village/2026-11-15-PHASE6-IDX4-IDX8-FORENSICS.md`
11. `docs/autonomous-village/2026-11-15-PHASE7-A2-9000-OBSERVATION.md`
12. `docs/autonomous-village/2026-11-15-PHASE8-IDX8-DECISION-TRACE.md`
13. `docs/autonomous-village/2026-11-15-PHASE9-IDX8-MOVEMENT-OWNERSHIP.md`
14. `docs/autonomous-village/2026-11-15-NAVEXECUTOR-SINGLE-AUTHORITY-MIGRATION.md`
15. `docs/autonomous-village/2026-11-16-PHASE10-A2-MIGRATION-REGRESSION.md`
16. `docs/autonomous-village/2026-11-16-PHASE11-STRATEGIC-NAVIGATION-FORENSICS.md`
17. `docs/autonomous-village/2026-11-16-PHASE12-NAVEXECUTOR-REFUSAL-FORENSICS.md`
18. `docs/autonomous-village/2026-11-16-PHASE13-RECOVERY-ACTUATOR-FORENSICS.md`
19. `docs/autonomous-village/2026-11-16-PHASE14-MOVEMENT-C4-FORENSICS.md`
20. `docs/autonomous-village/2026-11-16-PHASE15-NAVIGATION-ROOT-CAUSE-FORENSICS.md`
21. `docs/autonomous-village/2026-11-16-PHASE16-PATHFINDER-MOVEMENT-FORENSICS.md`
22. `docs/autonomous-village/2026-11-16-PHASE17-ISOLATED-EVALUATOR-IDX7-FORENSICS.md`
23. `docs/autonomous-village/2026-11-16-PHASE18-BREAK-EXECUTION-CONTRACT-AUDIT.md`
24. `docs/autonomous-village/2026-11-16-BREAK-EXECUTABLE-CONTRACT.md`
25. `docs/autonomous-village/2026-11-16-PHASE19-BREAK-RECOVERY-IMPLEMENTATION.md`
26. `docs/autonomous-village/2026-11-16-PHASE19-BREAK-VERIFICATION.md`
27. `docs/autonomous-village/2026-11-16-PHASE19-HARNESS-VALIDATION.md`
28. `docs/autonomous-village/2026-11-16-PHASE19-BREAK-GRAPH-STATE-FIX.md`
29. `docs/autonomous-village/2026-11-16-PHASE19-BREAK-RUNTIME-INVALID.md`
30. `docs/autonomous-village/2026-11-16-PHASE19-BREAK-LOS-RUNTIME-FAIL.md`
31. `docs/autonomous-village/2026-11-16-PHASE19-BREAK-STAND-NUDGE-RUNTIME.md`
32. `docs/autonomous-village/2026-11-16-PHASE19-BREAK-RETRY-RUNTIME-FAIL.md`
33. `docs/autonomous-village/2026-11-16-PHASE19-BREAK-RETRY-PASS.md`
34. `docs/autonomous-village/2026-11-16-PHASE19-EXCLUSION-BACKTRACK-RUNTIME.md`
35. `docs/autonomous-village/2026-11-16-PHASE19-BACKTRACK-EXACT-ARRIVAL-FAIL.md`
36. `docs/autonomous-village/2026-11-16-PHASE19-NAV-BACKTRACK-EXACT-ARRIVAL-RESULT.md`
37. `docs/autonomous-village/2026-11-16-A2-STRICT-GATE-RUN1-FAIL.md`
38. `docs/autonomous-village/2026-11-16-A2-RUN1-FORENSIC-EXTRACT.md`
39. `docs/autonomous-village/2026-11-16-A2-DROP-ROUTE-FEET-FIX.md`
40. `docs/autonomous-village/2026-11-16-A2-RUN1-DROP-FEET-FAIL.md`
41. `docs/autonomous-village/2026-11-17-A2-IDX4-DROP-TAKEOFF-RECOVERY-FIX.md`
42. `docs/autonomous-village/2026-11-17-A2-IDX4-NON-DESCENDING-DROP-FIX.md`
42. `docs/autonomous-village/2026-11-16-A2-RUN1-DROP-TAKEOFF-FAIL.md`
43. `docs/autonomous-village/2026-11-16-A2-RUN1-NON-DESCENDING-DROP-FAIL.md`
44. `docs/autonomous-village/2026-11-16-A2-RUN1-FIRST-NODE-DROP-FAIL.md`
45. `docs/autonomous-village/2026-11-16-A2-RUN1-VERTICAL-TAKEOFF-FAIL.md`
46. `docs/autonomous-village/2026-11-17-A2-RUN1-STEP-UP-ASSIST-FAIL.md`
47. `docs/autonomous-village/2026-11-17-A2-RUN1-GROUNDED-RECOVERY-FAIL.md`
48. `docs/autonomous-village/2026-11-17-A2-RUN1-LANDING-ANCHOR-FAIL.md`
49. `docs/autonomous-village/2026-11-17-IDX4-GROUNDED-INVALID-FORENSICS.md`
50. `docs/autonomous-village/2026-11-17-A2-RUN1-AABB-SOURCE-RECOVERY-RESULT.md`
51. `docs/autonomous-village/2026-11-17-A2-RUN1-AABB-SOURCE-RECOVERY-PHASE21.md`
52. `docs/autonomous-village/2026-11-17-A2-RUN1-EXACT-BOUNDED-RECOVERY-PHASE22.md`
53. `docs/autonomous-village/2026-11-17-A2-RUN1-PHASE23-EDGE-DIAG-RESULT.md`
54. `docs/autonomous-village/2026-11-17-A2-RUN1-UNSUPPORTED-TAKEOFF-PHASE24.md`

## Permanent collaboration workflow

- Read `docs/autonomous-village/CHATGPT-HARNESS-WORKFLOW.md` and follow it in every session.
- ChatGPT owns production source implementation and publishes changes directly to GitHub on this branch.
- Harness owns local synchronization, preservation, builds/tests/lints, automated runtime measurement, logs and validation reports. Harness must not independently implement production source fixes.
- Harness returns evidence and reproducible failures; ChatGPT reviews and makes source corrections; Harness validates the exact new SHA.
- Neither agent may claim work performed by the other or mark a commit validated without evidence for that exact SHA.
- This is a permanent workflow rule, not a one-session instruction. It must survive new conversations and agent handoffs.

## Verified baseline

- Branch: `feature/autonomous-village`
- Code implementation awaiting validation: `6d5dc7b1d1f72a366d4f6b05626147b9e932481c` (BREAK graph-state, executor and deterministic fixture correction). Documentation commits may advance branch HEAD after this code checkpoint; always fetch and verify actual local/remote HEAD before acting.
- Latest technical report: `2026-11-17-A2-RUN1-UNSUPPORTED-TAKEOFF-PHASE24.md`. Regression: 1/8 strict; 2102 UNSUPPORTED_WAIT with support=false; IDX4 DROP loops without DONE.
- Remote branch must be fetched and checked before any change.
- Working tree may contain local-only untracked artifacts.

## Completed investigations

- IDX8 elevated-BREAK support-floor selection: fixed by `NavBreakTargetPolicy` and regression test.
- C4 displaced workers beyond 72 blocks: fixed by `VillagerReturnPolicy`/`VillagerReturnGoal`.
- Existing diagnostic worlds remain authoritative; do not recreate.

## Current status

- `NavExecutor` is the single movement authority; legacy planner, `NavFeature` and
  `MovementOwnership` are deleted. Harness passed Gradle tests, strict lint and diff-check on the
  prior tree at `ecdfaff6`; those results do **not** validate the later code checkpoint `6d5dc7b1d1f72a366d4f6b05626147b9e932481c`.
  The new graph-state/route/executor changes and two new tests are **not yet compiled or executed by Harness**.
- Phase10 melee-only tactical ownership (`002550`): 6/8 perimeter, 5/8 strict; C4 12/12.
- Phase11 diagnostic `005739`: same 6/8 perimeter, 5/8 strict. Combat inactive for idx5/7/8.
- Phase12 diagnostic `022656`: 5/8 perimeter, 4/8 strict; C4 12/12.
- Phase13 diagnostic `024118`: 6/8 perimeter, 4/8 strict; C4 **FAIL** (`jobSite=[0,0,0]`, 12/12 resumed).
- Phase14 diagnostic `035911`: 6/8 perimeter, 4/8 strict; C4 **PASS** `[1,0,0]`.
- Phase15 diagnostic `092800`: 5/8 perimeter, 4/8 strict; C4 **PASS** `[2,0,0]`.
- Phase16 diagnostic `094243`: 5/8 perimeter, 3/8 strict; C4 **PASS** `[1,0,0]`.
- Phase17: `createPath` cached-return flaw confirmed; Phase16 retry not independent.
- Phase18 audit: BREAK route node was the solid block; no attack-position search.
- BREAK contract implementation: `Node.pos`=stand, `Node.actionPos`=solid target; planner validates
  adjacent stand AABB/support/LOS/reach/protection; executor navigates to stand, delegates action,
  requires verified air before advancing.
- Focused `a2n-20261116-101718`, merged `103331`, post-integration `110238`: 6/8 perimeter,
  5/8 strict, C4 PASS `[1,0,0]`/`[3,0,0]`.
- Phase19 verification: BREAK executor integration fixed to use `actionTarget()`. Follow-up review
  constrains planner-selected attack stands to one legal move from the predecessor and delegates
  reach/LOS/protection checks to `NavBreakActionPolicy.executable`.
- Harness validation at `d440b3e7` (implementation `6d5dc7b1`): Gradle tests, strict lint, diff-check and
  diagnostic compile PASS. New tests executed: graph-state 7, route-policy 11, attack-position 7.
- `break-runtime` `a2n-20261116-160714` was INVALIDO (empty plans); LOS correction `9200ac31` at
  `76d3466c` fixed planning: `BREAK_LOS_DIAG` accepts both panes with `hitBlock` equal to `actionPos`.
- `break-runtime` `a2n-20261116-174648` on HEAD `8b389672`: **COMPLETO**. Both panes air, retry
  calls=2, delta=40, success/retry missions resumed (retry at x=738, ticksAfterBreak=23).
- `break-edge-excluded` `a2n-20261116-180242`: **COMPLETO**. Two unverified actions, delta=40,
  one `BREAK_EDGE_EXCLUDED` (fingerprint, exclusions=1), alternative BREAK selected, both blocks
  solid, route index unchanged, strategic mission preserved, 0 unexpected calls.
- `nav-backtrack` `a2n-20261116-185434` on HEAD `fb93541e`: **COMPLETO**. Selection target
  `937,184,608`, adjacent nudge from 938, exact physical return, mission resumed at tick 103,
  history 6, max step 0.114, one backtrack, no teleport.
- A2 gate Run 1 `a2n-20261116-190555` on `2d47d628`: **FAIL**. 8/8 spawn, 5/8 perimeter, 5/8 strict
  (idx1 t558, idx2 t667, idx3 t1062, idx5 t702, idx6 t649); idx4/7/8 outside and alive.
  - idx8 NAV_FAIL t3411: no valid local waypoint after world change; global budget 8000/8000; 4/4 rearms.
  - idx7 NAV_FAIL t4209: no-progress budget 10/10 toward `703,68,457` BUILD.
  - idx4 NAV_FAIL t5147: replans 8/8, 24 DROP rejections (landing above), global budget exhausted.
- Consecutive 8/8 runs: **0**. C4 in Run 1: PASS `[2,0,0]`.
- Forensic extract confirms the IDX4/IDX8 shared code defect: route nodes use `Step.state`, not
  `Step.feet`, so DROP nodes carry the search-state Y instead of the landing Y. IDX4 runtime shows
  node y78 vs mob y77 (`dy=1` rejection); IDX8 has 81 front waypoints but 0/64 valid local.
- IDX7 BUDGET shows `exp=901/900` for 10/10 attempts with `BUILD-FALLIDO`; no final full plan summary
  was persisted.
- DROP-feet fix at `5604ed03`: static PASS (NavRouteNodePolicyTest 6). A2 Run 1 `a2n-20261116-195651`
  still **FAIL** at 5/8 strict. IDX4 remains below its landing `597,78,499` at `596,77,498`
  (24 DROP rejections, dy=1, replans 8/8); the earlier state-vs-feet node hypothesis is not the cause
  of this runtime instance. IDX7 `exp=901/900` 10/10; IDX8 no local waypoint/global budget.
- Take-off fix at `6d294c1d`: static PASS (NAV_DROP_TAKEOFF_POLICY_TEST 5). A2 Run 1
  `a2n-20261116-201759`: **4/8 strict**, 5/8 perimeter. IDX4 route starts with DROP at index0, so
  `origenPlan` is null and `ACERCARSE-ORIGEN-DROP` is never emitted; mob remains at y77 below y78.
- Follow-up source diagnosis: local planner starts from `mob.blockPosition()` and initializes
  `piesDesde=start.getY()`; planner accepted a DROP whose landing feet were above the source feet
  because only the generic vertical bound was enforced. Using `plannedFrom` cannot repair this
  impossible edge. Fix at pending SHA: require `landingFeet < takeoffFeet` for every DROP edge;
  reject non-descending DROP before A* accepts it. New test grows to 9 assertions.
- IDX2 this run reached perimeter but not strict; idx7/idx8 unchanged independent failures.
- Non-descending fix at `2214366a`: static PASS (NAV_DROP_TAKEOFF 9, NAV_ROUTE_NODE 6). A2 Run 1
  `a2n-20261116-211919`: **FAIL**, 5/8 strict, 5/8 perimeter. IDX4 still `597,78,499` vs mob
  `596,77,498`, `dy=1`, 24 failures; no `DROP_REJECTED_NON_DESCENDING` or recovery command for its UUID.
  IDX7/IDX8 unchanged; C4 PASS `[1,0,0]`.
- First-node origin diagnosis: route acceptance stores `plannedFrom = mob.blockPosition()`; the
  DROP executor previously resolved an origin only from a previous node when `routeIndex > 0`.
  Thus a first-node DROP had no recoverable origin. Absence of `DROP_REJECTED_NON_DESCENDING` was
  inconclusive because that trace is opt-in.
- First-node origin fix at pending HEAD: `NavDropTransitionPolicy.origenPlanificado` uses recorded
  routeStart for index 0 and immediate predecessor for later indices; `NavExecutor` now issues the
  existing exact-Y `ACERCARSE-ORIGEN-DROP` recovery when required. Added `DROP_TAKEOFF_ORIGIN_RESOLVED`
  trace and 3 assertions (DROP policy total 12). Commits `b763b112`, `2518d703`, `183b7667`.
- First-node origin fix at `b73ca2de`: static PASS (NAV_DROP_TAKEOFF 12). A2 Run 1
  `a2n-20261116-213836`: **FAIL**, 5/8 strict, 5/8 perimeter, C4 PASS `[1,0,0]`.
  IDX4 resolves origin `589,96,522` from live `589,93,522` and emits `ACERCARSE-ORIGEN-DROP`, but
  PathNavigation returns a one-node path and the mob does not ascend; physical origin not reached.
- IDX7 BUILD/900-expansion and IDX8 local-waypoint failures unchanged.
- Vertical recovery fix at `38a4b52e`: static PASS (NAV_DROP_TAKEOFF 16). A2 Run 1
  `a2n-20261116-225217`: **FAIL**, 5/8 strict, 5/8 perimeter, C4 PASS `[1,0,0]`.
  IDX4 accepted a len1 STEP_UP recovery route but stalled at `588,104,521` vs takeoff `588,107,520`;
  later plans returned empty with `exp=901/900`; final construction exhausted.
- STEP_UP assist at `7aed2c68`: static PASS (NAV_DROP_TAKEOFF 25). A2 Run 1
  `a2n-20261117-001624`: **FAIL**, 4/8 strict, 5/8 perimeter, C4 PASS `[1,0,0]`.
  IDX4 emitted `DROP_TAKEOFF_STEP_UP_ASSIST` (`567,121 -> 566,122`, support/fit true) but stalled at
  `567,119`, never reached the waypoint. No `901/900` entries observed.
- Grounded fix at `b3064596`: static PASS (NAV_DROP_TAKEOFF 32). A2 Run 1
  `a2n-20261117-020800`: **FAIL**, 5/8 strict, 5/8 perimeter, **C4 FAIL [0,0,0]**.
  IDX4 emits only `DROP_TAKEOFF_RECOVERY_WAIT_GROUNDED` (onGround=false); no accepted route, no assist,
  no recovery reached; final `construccion agotada sin completar el descenso`. No `901/900` entries.
- Landing anchor at `f32c32a4` (production559ba6e3): static PASS (NAV_DROP_TAKEOFF 36).
  A2 Run 1 `a2n-20261117-101833`: **FAIL**, 5/8 strict, 5/8 perimeter, C4 PASS `[1,0,0]`.
  IDX4 is grounded at `593.51,87,507.96` but `feetValid=false support=false fits=true`,
  `landingAnchor=NONE`; no route accepted/assist/reached; final replans 8/8.
- IDX4 forensic root cause (run `a2n-20261117-101833`): exact `593.51,87,507.96`, AABB
  z≈507.66–508.26, supported by cobblestone `(593,86,508)`, but `blockPosition`/support check uses
  `(593,87,507)` below `(593,86,507)` air. `onGround=true`, `feetValid=false`, `support=false`,
  `fits=true` (narrow cellBox). Valid shortest candidate is `(593,87,508)` via horizontal WALK.
- No production change and no new A2 run in the forensic session. A2 OPEN; full 12k not run.
- Canonical focused gate: five consecutive **8/8 strict `DENTRO-PARED`** runs.

## Immediate work

1. ChatGPT: fix the `UNSUPPORTED_WAIT` loop (`grounded=true feetValid=false support=false`) before
   another A2 run. It caused 2,102 waits and reduced perimeter entry to 1/8.
2. IDX4 loops DROP_START `567,122,534 → 567,119,534` with no descent/DONE/cursor advance.
3. Do not increase budget or relax support/protection/landing. C4 PASS `[1,0,0]`.
4. No Run 2 unless 8/8 strict; no 12k until five consecutive 8/8 strict runs.

## Worlds (preserve; do not overwrite)

- `C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-A2-IDX8-Diagnostico`
- `D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-A2-IDX8-Diagnostico`
- `C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-C4-WorkerResume-Diagnostico`
- `D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-C4-WorkerResume-Diagnostico`

Hashes and sizes are in `2026-11-15-PHASE2-FIXES.md`.

## Local-only artifacts

`.a2-backups/`, `src/foodTest/`, `tools/a2n/A2NavHarness.java.partial`, launcher helper scripts, `tools/a5-closure/`. Do not commit indiscriminately.


## 2026-11-16 — Phase19 backtrack exact-arrival correction (PENDING HARNESS)

- Harness evidence: `BREAK_EDGE_EXCLUDED` PASS; `NAV_BACKTRACK` FAIL because generic `ARRIVE_DISTANCE_SQR` cleared the one-shot tactical target at 938 before exact cell 937.
- ChatGPT correction source checkpoint: `889072d235a44d0efc2de7ad955333b39b1d8b03`. Added exact-cell gate for pending recovery backtrack in `NavExecutor`, pure policy and three regression assertions in `NavArrivalPolicyTest`, plus bounded suppression trace.
- No tests/compile/lint/runtime executed by ChatGPT. **PENDING HARNESS** on exact live HEAD.
- Run static validation and only disposable `nav-backtrack`; require physical return to exact historical cell, one selection, mission resumed, strategic mission preserved, bounded history, no teleport.
- `BREAK_EDGE_EXCLUDED` remains validated; A2 OPEN; no full 12k until five consecutive strict 8/8; C4 save/reload OPEN.


## 2026-11-16 — Phase19 backtrack route-cursor + nudge correction (PENDING HARNESS)

- Harness run `a2n-20261116-182234` passed static validation and exact objective-arrival guard, but runtime still failed: the generic WALK node arrival gate consumed `937` at adjacent `938`, advancing route cursor to idx=2 before the nudge helper could execute.
- ChatGPT correction checkpoint: `cc817c6314d2b2055dfbba578c5565e3bff9fedf`. `nodoAlcanzado()` now requires exact occupancy for the active backtrack route node; this keeps the WALK node active and lets the existing bounded `aplicarAcercamientoAdyacente()` + `NavAdjacentApproachPolicy` + `MoveControl.setWantedPosition` execute when vanilla navigation is done. Added `BACKTRACK_NODE_OCCUPANCY_REQUIRED`, `BACKTRACK_ADJACENT_NUDGE`, and three pure assertions.
- No static or runtime validation performed by ChatGPT. **PENDING HARNESS** on exact live HEAD.
- Run Gradle tests, strict lint, diff-check, diagnostics compile, then a new disposable `nav-backtrack` only. Require exact physical return before clearing tactical target, mission resumed, one selection, history <=16 and no teleport.
- A2 OPEN; no full 12k before five consecutive strict 8/8; C4 save/reload OPEN.


## 2026-11-17 — IDX4 first-node DROP origin fix

- Commits: `b763b11245f0fdf1864c49cfd0100e451f40abd8`,
  `2518d70396d6c500efb241f522c6f53eef6a022c`,
  `183b76675761a69f2ca2b501dc02311cba412572`,
  docs `bc38d43a2085fef4cd91fad90afb91c95adacda2`.
- Harness validation pending on exact current HEAD. Expected DROP policy test: 12 assertions.
- Report: `2026-11-17-A2-IDX4-FIRST-NODE-DROP-ORIGIN-FIX.md`.


## 2026-11-17 — IDX4 executable vertical take-off recovery

- Harness run `a2n-20261116-213836` established that origin resolution worked but vanilla
  same-column vertical `moveTo` returned a one-node path at live y93 instead of ascending to y96.
- Published bounded auxiliary navigation via existing `NavPlanner.plan`. Only complete routes
  to the exact take-off using WALK/STEP_UP/JUMP_UP are accepted; no DROP/BREAK/BRIDGE/BUILD.
- Main DROP route/cursor is suspended and restored only after exact-Y physical take-off with
  support and fit. Recovery waypoints require exact occupancy; JUMP_UP uses the existing actuator.
  Planning failure, incomplete route, or stall fails closed and replans from the live position.
- No safety guards or budgets were relaxed. Test expected: `NAV_DROP_TAKEOFF_POLICY_TEST` 16
  assertions; `NAV_ROUTE_NODE_POLICY_TEST` 6.
- Commits: `df3b9e46`, `ebeda4de`, `2cc3aa50`, `d79189aa`, `d7056617`, `800329f6`.
- Report: `2026-11-17-A2-IDX4-VERTICAL-TAKEOFF-RECOVERY-FIX.md`.
- PENDING HARNESS; ChatGPT did not execute static or runtime checks.


## Latest checkpoint — 2026-11-17 A2 IDX4 STEP_UP execution correction (PENDING HARNESS)

- Run `a2n-20261116-225217` on `38a4b52e`: static PASS; A2 Run 1 FAIL 5/8 strict. IDX4 accepted one STEP_UP but did not climb; later plans reported 901/900.
- ChatGPT added a guarded natural STEP_UP assist for DROP recovery: only adjacent one-block climbs with blocked lower step, headroom, destination fit and real support; uses MoveControl + JumpControl, never direct velocity or teleport. Exact physical occupancy remains required.
- Corrected NavPlanner's expansion guard to check before incrementing; 900 is now the maximum, never 901. Budget itself unchanged.
- Added pure policies and extended `NavDropTakeoffPolicyTest` to 25 assertions; expected route-node test remains 6.
- Production code checkpoint before handoff documentation: `491b7d0170501e9d86433a52d2703395a4029b06`.
- Report: `docs/autonomous-village/2026-11-17-A2-IDX4-STEP-UP-EXECUTION-FIX.md`.
- PENDING HARNESS on exact current HEAD. Static checks first, then A2 Run 1 only. IDX7/IDX8 untouched. No Run 2 unless 8/8 strict; no 12k until five consecutive 8/8 strict runs. C4 save/reload OPEN.


## 2026-11-17 — IDX4 bounded airborne landing anchor (PENDING HARNESS)

- Harness on `b3064596` / production `190bb657`: static PASS, A2 Run 1 FAIL 5/8 strict.
- IDX4 emitted only WAIT_GROUNDED; one sample had feetValid=true, support=true, fits=true, but onGround=false.
- Published bounded horizontal landing guidance through MoveControl, only over the current valid/supported/fitting feet cell. No A* or jump is allowed until a later tick reports onGround=true.
- Added four pure assertions; expected DROP policy test total 36. WAIT trace includes velocity and landingAnchor.
- Report: `2026-11-17-A2-IDX4-LANDING-ANCHOR-FIX.md`. PENDING HARNESS. A2 Run 1 only after static PASS; no Run 2 unless 8/8 strict; no 12k until five consecutive 8/8 strict.
- C4 `assignmentsClean=false` remains independent and unchanged; IDX7/IDX8 untouched.
