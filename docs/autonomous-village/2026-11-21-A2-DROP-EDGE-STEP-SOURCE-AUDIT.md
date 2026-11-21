# A2 DROP edge-step source audit — 2026-11-21

## Scope

Source-only review after the BUILD provenance / BREAK diagnostics static gate passed. No production or test changes were made by ChatGPT. No Gradle, tests, Minecraft, runtime, or world operations were executed.

- Branch: `feature/autonomous-village`
- Source checkpoint: `fd5940ba579ae3089e072e2870811461e4073c89`
- `NavExecutor.java` blob: `4269dc33377f8f1f6033053ec06aae3da77e069b`
- `NavDropEdgeStepPolicy.java` blob: `aa726f2350192c7bf2bd5242b15c007cdf9e49d9`
- `NavDropEdgeStepPolicyTest.java` blob: `18a0610d93a8e7cb4db11d17fb082592cae0c567`
- Runtime evidence: `a2n-20261120-173629`, extracted in `2026-11-20-A2-BREAK-BUILD-RUNTIME-FORENSIC-EXTRACT.md`.

## Confirmed source behavior

1. `activateDropEdgeStep(takeoff, landing)` stores both cells, resets tick/no-progress/best-distance counters, stops vanilla navigation, and immediately issues a horizontal MoveControl command to the landing center.
2. Each `tickDropEdgeStep` first checks the complete entity AABB footprint with `fullyOffSupport`. If every support column is cleared (or the entity has descended below takeoff Y), it switches to the descending command and releases the edge-step owner.
3. While support remains, progress is measured only as horizontal distance to the landing center. A reduction greater than 0.01 resets the no-progress counter; otherwise the counter increments.
4. `NavDropEdgeStepPolicy.noProgress` aborts only when the counter is greater than 20, so the 21st consecutive non-improving tick aborts the edge-step and sets the executor to `OBSTRUCTED`.
5. `DROP_EDGE_STEP_PROGRESS` is emitted every five ticks and includes position, horizontal distance, grounded state and AABB. `DROP_EDGE_STEP_COMMAND` includes takeoff, landing, target and block position. `DROP_EDGE_STEP_ABORTED` currently records only UUID, tick, reason and block position; it omits the last/best distance, no-progress count, Y/velocity, grounded state and AABB.

## Interpretation boundary

The forensic report's 17 `no_progress` aborts mean the measured distance-to-landing-center failed to improve by more than 0.01 for 21 consecutive active ticks while the full AABB still had support (otherwise the state machine would have switched to descent first). This does **not** prove the entity was stationary: it could have moved without getting closer to the landing center, oscillated, been collision-blocked, or had a target/geometry mismatch.

The existing policy test checks only the fixed threshold (20 accepted, 21 abort). It does not test distance-progress accounting, reset semantics, target stability, or the relationship between support release and progress. No safe behavioral correction is established by source alone.

## Required read-only evidence before changing behavior

For each of the 18 IDX6 `DROP_EDGE_STEP_ABORTED` events in the existing run:

- Correlate by UUID and tick with the immediately preceding `DROP_EDGE_STEP_SELECTED`, every `DROP_EDGE_STEP_COMMAND`, and all `DROP_EDGE_STEP_PROGRESS` records.
- Recover the exact takeoff/landing pair, position samples, distance sequence, grounded state, AABB and any support-cleared/descent event.
- Determine whether each attempt repeats the same edge, changes route node, moves but not toward the center, remains at a stable position, or reaches a new route before abort.
- Correlate the 12/12 route-guard exhaustion to the exact sequence of DROP aborts and replans; do not treat the aggregate 17/18 count as one continuous attempt.
- Map the runtime UUID to IDX6 from that same run's spawn/registration records. The prior extract's IDX6 UUID is `6452b8c2-cffb-472b-a0d3-35a45dea813a`; verify it against the exact run logs before using it.
- Separately map killer UUID `cc4b9c97-d8f3-40e3-aee1-cf233feab912` to its entity/role using spawn records from the exact run in which `minecraft:mob_attack` was recorded. Do not assume that UUID belongs to IDX1 or to the 2026-11-20 run.

If the existing logs do not contain enough per-tick samples, document the missing fields. A future instrumentation-only change may add them, but it must not change movement, thresholds, route guards, support/AABB checks or landing rules.

## Validation and authorization

- This is a source audit only; no production/test modifications.
- No runtime is authorized. The previous A2 runtime authorization remains consumed.
- No A2 Run2, retry, C4 standalone, or Full12K.
- A2 remains OPEN. Last measured runtime remains 1/8 strict and 2/8 perimeter; C4 PASS was integrated only and not standalone.
- Next Harness task: read-only analysis of existing artifacts plus static integrity verification only if needed. Harness must not edit production or tests. Preserve all untracked artifacts, logs, backups and protected worlds.
