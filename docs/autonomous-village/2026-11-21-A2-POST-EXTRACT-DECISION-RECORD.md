# A2 post-extract decision record — BREAK / BUILD / DROP (2026-11-21)

## Baseline and scope

- Branch: `feature/autonomous-village`
- Runtime analyzed: `a2n-20261120-173629`
- Tested source HEAD: `54e3c9a8b11e2b6125a589ee64795db5a98c62fb`
- Harness documentation HEAD: `2b6e2c13d1c454afb0b52752ec7e18757ecf5812`
- Raw extraction report: `2026-11-20-A2-BREAK-BUILD-RUNTIME-FORENSIC-EXTRACT.md`
- Raw report SHA-256: `E7A3FF4AA04EE9653AF1169593D8242458F83DB66E859140189B86B7F35F6DBB`
- No runtime, Gradle, tests, Minecraft launch, world mutation, production modification, or test modification occurred during extraction.

This record corrects the earlier post-run hypothesis where necessary. It does not authorize runtime.

## 1. BREAK — confirmed search-space limitation, no safe behavior patch proven yet

The production policy `NavBreakAttackPositionPolicy.candidates(actionPos, from, feetY)` currently emits exactly eight horizontal neighboring stands, all at `feetY`. `isSingleStepReachable` rejects every stand whose Y differs from the predecessor feet Y.

The run confirms:
- All 128 candidate checks came from IDX1, with 0 accepted.
- 64 were out of action reach, 32 were not one-step reachable, and 32 reached action validation but failed (30 real raycast occlusions, 2 stand-fit precondition failures).
- The recorded ray hit at `729,63,600` is the block above the target `729,62,600`; retaining this LOS rejection is correct.
- IDX3/4/5/7 did not invoke BREAK candidate evaluation at all, despite traversing other Y levels.
- No alternate-height stand was tested, so the evidence does not establish whether one exists or is safe.

**Decision:** do not weaken raycast, reach, support, AABB, protection, or single-step movement. Do not simply add arbitrary vertical stands: a stand at another Y must be represented by a physically executable route transition, including support, fit, headroom, safe descent/ascent and route reconstruction. The current BREAK edge models an action stand plus a post-break successor; changing candidate Y without proving the graph/executor contract could create a route whose movement is not executable.

**Next code task:** design a bounded BREAK approach transition that can first reach a validated alternate-height stand, then perform BREAK from that stand, while preserving the distinct `standPos` and `actionPos` graph metadata. Add pure policy tests for vertical delta bounds and production integration tests for the route/action contract before implementation. If this cannot be expressed without conflating the stand and post-break state, keep BREAK behavior unchanged and instrument the missing caller/context first.

Instrumentation gap: `BREAK_STAND_CANDIDATE` lacks UUID; `BREAK_LOS_DIAG` lacks UUID and caller. Add those fields before any future authorized runtime so first-expansion candidates can be correlated separately from later route/waypoint validations. This is observability only, not a behavior fix.

## 2. BUILD — retract the unsupported actionPos-handoff hypothesis

The raw trace establishes:
- IDX7 global search terminated `found=false`, `hitBudget=true`, at 8,000 expansions, with a partial frontier route of 56 nodes.
- The local target `706,69,459` was written by `A2_LOCAL_STAIR_WRITE op=INC_STEP` from the partial frontier.
- No global BUILD node/actionPos, no local BUILD node, no local route acceptance, and no executor BUILD dispatch were observed.
- Therefore no placement callback was attempted; callback failure is not the cause.

Earlier source review suggested a global BUILD `actionPos` might be discarded during local-objective conversion. The raw extract does **not** prove that such a global BUILD node existed in this run. That hypothesis is withdrawn as a root-cause claim and must not be implemented as a blind handoff patch.

**Decision:** the proven failure is global search budget exhaustion followed by repeated local planning toward a frontier-derived objective that produced no accepted local route, ending at local no-progress 10/10. The trace label `buildable=true transition=BUILD` is not sufficient proof of a real BUILD edge/action node.

**Next code task:** inspect the exact partial-route reconstruction and executor waypoint-selection contracts together. Add a fail-closed invariant: a local objective may be tagged BUILD only when it comes from an actual validated BUILD route node carrying a non-null placement `actionPos` and a valid successor-feet cell. A frontier-derived ordinary waypoint must not be relabeled BUILD merely because the cell is constructible. If the current code already enforces this, add diagnostics/tests that prove it; otherwise implement that invariant and a deterministic regression. Do not increase the global/local budgets or allow an unvalidated partial frontier to masquerade as a BUILD action.

## 3. DROP — distinguish blocked shafts from stalled edge-step execution

IDX4:
- 19 rejected DROP destinations are all blocked fall corridors with spruce leaves (14 at Y=118, 5 at Y=115), at distance 3–5.
- These are not invalid landing/support failures. The final started transition `580,111,520 -> 580,110,520` has no DONE event.
- The bounded exclusion capacity is 32; 19 unique rejections do not prove capacity overflow.

IDX6:
- 17 of 18 `DROP_EDGE_STEP_ABORTED` events are `no_progress`; one is `route_node_changed`.
- Final route guard exhausted 12/12. The final DROP start did not complete.
- No rejected landing event was recorded.

**Decision:** keep blocked leaf shafts excluded; do not force a drop through leaves, relax the landing checks, or enlarge budgets. The current evidence does not identify why IDX6's edge-step made no progress.

**Next code task:** review the edge-step state machine against the trace timestamps and target coordinates. Add deterministic tests for progress accounting, target stability, and reset/abort conditions. Preserve the complete-AABB support-release rule and validated landing. For IDX4, prefer a separately validated alternate route/shaft; do not classify blocked corridors as landing defects.

## 4. IDX1 death telemetry

The run's death cause remains UNKNOWN. Source contains and registers opt-in `A2DamageTelemetry` listeners for `LivingDamageEvent.Post` and `LivingDeathEvent`; the extracted logs contain neither `[A2_DAMAGE]` nor `[A2_DEATH]` evidence for IDX1.

Do not infer a killer from proximity to the threat. Before another runtime, inspect why the registered telemetry did not produce evidence in this run (trace activation, event registration/version semantics, entity removal path, or harness log filtering). Add a focused static regression or instrumentation assertion only after identifying the exact gap.

## 5. Priorities and validation boundary

1. Correct BUILD frontier semantics: actual BUILD route node + non-null actionPos required; no inferred BUILD from a partial frontier.
2. Specify a graph-correct alternate-height BREAK approach; do not weaken LOS or physical movement predicates.
3. Diagnose DROP edge-step no-progress without weakening takeoff/landing safety.
4. Repair/verify damage/death telemetry capture; cause remains UNKNOWN.
5. Preserve the successful same-column vertical-neighbor correction.

No runtime is authorized. The previous single A2 Run1 authorization was consumed. Next Harness action, after any ChatGPT source changes, is STATIC ONLY: safe fast-forward, preserve local/untracked work and worlds, verify exact blobs, compile/test/policyTest/strict lint/diff-check, and report evidence. Harness never modifies production or tests. No Run2, C4 standalone, or Full12K. Full12K remains gated behind five consecutive 8/8 strict A2 runs.

**A2 remains OPEN.**
