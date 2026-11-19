# A2 Phase 53 — preserve non-stair route during stair cadence wait

## Status

**IMPLEMENTED — PENDING HARNESS VALIDATION.** This phase addresses the IDX5 regression proven by
Phase52. ChatGPT changed production source only; no local build, tests, lint, diagnostics compile,
or Minecraft runtime was executed in this session.

- Branch: `feature/autonomous-village`
- Parent production SHA: `ea21466a0cc7cc2671f5439cd5ff7bbaeecd6d9e`
- Implementation SHA: `656ce7d0f6182fd15e0b1bbb0bd6684c180bd0e6`
- Phase52 evidence: `2026-11-18-A2-PHASE52-STAIR-IDX5-FORENSICS.md`
- Phase52 RUN_ID: `a2n-20261118-225649`

## Evidence addressed

Phase52 proved the first IDX5 divergence at t2: Phase50's `NO_PROGRESS` result allowed the
selected global DROP waypoint to be returned and moved at t3; Phase51's `CADENCE_WAIT` caused
an early `return null`, so the already selected route waypoint was discarded and the run emitted
zero `MOVE_COMMAND`. It also showed that a directly reachable mission waypoint was similarly
discarded by an early return.

## Source change

Only `src/main/java/com/chipoodle/devilrpg/entity/nav/NavExecutor.java` changed.

1. In the directly reachable mission branch, `CADENCE_WAIT` no longer returns null. The mission
   waypoint is returned as before, and a `A2_STAIR_CADENCE_ROUTE_PRESERVED` trace identifies
   `source=direct_mission`.
2. In the selected global-route branch, `CADENCE_WAIT` no longer discards the already selected
   waypoint. The waypoint has already passed global-route selection and the existing physical
   foot/transition validation. A trace identifies `source=validated_global_route` and its
   transition.
3. The builder is still called and its cooldown still delays the next stair attempt. No stair is
   treated as placed, no pending stair is fabricated, and no local step/failure counter is consumed
   by the CADENCE_WAIT path.
4. Fail-closed behavior remains when no usable non-stair waypoint exists: empty global route,
   no valid waypoint, or a waypoint equal to the current position still does not create movement.

## Invariants

- No changes to stair placement predicates, anchor policy, collision, support, headroom, protection,
  real AABB fit, traversal confirmation, planner budgets, retry budgets, or cooldown values.
- No changes to Phase41, Phase44, or Phase46.
- No world edits, teleportation, forced stair placement, or defense changes.
- IDX3's all-solid candidate geometry is a separate unresolved investigation. Monotonic anchor
  runtime acceptance still requires a naturally placed and physically traversed stair.
- A2 remains OPEN. No Run2; no Full12K until five consecutive 8/8 strict `DENTRO-PARED` runs.

## Harness handoff

1. Safely fast-forward to and validate exact implementation SHA
   `656ce7d0f6182fd15e0b1bbb0bd6684c180bd0e6`. Preserve local/untracked work, diagnostic artifacts,
   and worlds. Stop if histories diverge or sync risks overwriting local material.
2. Run the normal Gradle test gate, strict lint, diff check, and diagnostics compile. Record commands,
   exit codes, and exact tested SHA. These checks have **not** been run by ChatGPT.
3. If all static checks pass, run A2 Run1 only using the existing automated/headless launcher.
   Do not open or focus a GUI client; do not require user input; do not leave Minecraft running.
4. Enable both stair trace properties:
   `-Ddevilrpg.a2n.traceStair=true`
   `-Ddevilrpg.a2n.traceStairCandidates=true`
5. For IDX5, verify `A2_STAIR_CADENCE_ROUTE_PRESERVED` appears at the first cooldown tick with
   `source=validated_global_route` (or `direct_mission` if that is the selected branch), followed
   by a valid `MOVE_COMMAND`. Confirm the destination is the previously validated route waypoint,
   not a fabricated stair target. Compare first divergence and movement timeline against Phase52.
6. Confirm `CADENCE_WAIT` still leaves local step/failure counters unchanged and no stair is counted
   as placed. Record whether any natural physical stair placement/traversal occurs; if none, anchor
   traversal remains INSUFFICIENT_EVIDENCE.
7. Run A2 Run1 only. Do not Run2 or Full12K. C4 may be reported only if the existing required
   validation workflow includes it; do not infer it from Phase52.
8. Persist a new dated validation report and update all handoffs. Harness must not edit production.

## Expected interpretation

- Static PASS does not validate runtime movement.
- The key Phase53 runtime assertion is preservation of a valid non-stair route during a stair cooldown.
- If the trace is absent, no MOVE_COMMAND occurs, or the waypoint is invalid, classify the result
  accurately and return the exact first failing trace sequence. Do not adjust budgets or safety rules.
