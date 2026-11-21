# A2 implementation — BUILD provenance and BREAK diagnostic isolation (2026-11-21)

## Baseline
- Branch: feature/autonomous-village
- Harness source-audit report: user-provided attachment, 345 lines.
- Harness documentation HEAD: 2b6e2c13d1c454afb0b52752ec7e18757ecf5812
- Prior runtime: a2n-20261120-173629, FAIL (1/8 strict, 2/8 perimeter).
- No runtime authorization remains.

## Harness audit findings accepted
1. BUILD: escalonarObjetivoLocal() could preserve objetivoPermiteConstruccion=true while clearing objetivoTransicionLocal and objetivoAccionLocal. A BUILD action's exact placement provenance was not carried through the local objective step.
2. BREAK: both candidate and LOS diagnostic counters were global static caps of 128. One entity could consume the cap and suppress later entities. Events lacked UUID and caller.
3. DROP: progress is horizontal distance only. IDX6 stayed at the same horizontal distance while the complete AABB remained supported, so no_progress aborted. The route guard's 12/12 exhaustion follows repeated abort/replan cycles. No safe movement behavior change is proven.
4. Death: the previous extraction missed existing [A2_DAMAGE]/[A2_DEATH] lines because it searched for died/slain and MUERTO. IDX1 died from minecraft:mob_attack with direct/causing UUID cc4b9c97-d8f3-40e3-aee1-cf233feab912; a prior minecraft:fall hit was also recorded. The killer's name/entity mapping is not established.

## Production changes published
### BUILD provenance (fail closed)
New NavBuildObjectivePolicy requires a non-null resulting feet cell, a non-null placement cell, and resulting feet cell equal to placementCell.above().
NavExecutor.elegirObjetivoLocal() rejects a BUILD waypoint with missing/misaligned actionPos. It carries the exact placement cell in a dedicated objetivoAccionConstruccion field, separate from BREAK's objetivoAccionLocal. escalonarObjetivoLocal() preserves this provenance only while the feet/action relation remains valid. The local planning gate rechecks the same invariant before allowing an unsupported objective.
Added LOCAL_OBJECTIVE_SELECTED and BUILD rejection diagnostics with feet, transition, actionPos, route length and found/partial state. The existing BUILD actuator, lease, placement verification, support, AABB, arrival and budgets were not weakened or changed.
Regression: NavBuildObjectivePolicyTest — 8 assertions, registered in policyTest. Authored; not run by ChatGPT.

### BREAK diagnostic isolation (observability only)
- Replaced each global 128-event counter with a per-live-Mob 128-event budget backed by WeakHashMap<Mob,Integer>; weak keys avoid retaining unloaded entities.
- Added uuid and explicit caller to BREAK_STAND_CANDIDATE and BREAK_LOS_DIAG.
- No candidate geometry, action reach, support, protection, raycast, route, or execution behavior changed.
This prevents one entity's events from consuming another entity's diagnostic allowance. It remains a per-entity bounded trace, not an unlimited log.

## Intentionally unchanged
- DROP edge-step behavior: no behavior patch. Keep complete-AABB support release, validated landing, safe takeoff and blocked-shaft exclusions. A deterministic fixture and deeper collision diagnosis are still required before changing the no-progress policy.
- BREAK alternate-height behavior: no behavior patch. A valid alternate-height stand must be reached through ordinary validated movement before the separate BREAK edge; do not merge approach, action stand, and post-break successor into one edge.
- Damage/death listeners: no behavior patch. Telemetry was working; the previous extraction was wrong. Harness should assert presence of [A2_DAMAGE]/[A2_DEATH] when traceNav is enabled and map the attacker UUID using existing entity-spawn records, if available.

## Files changed
Production:
- src/main/java/com/chipoodle/devilrpg/entity/nav/NavExecutor.java
- src/main/java/com/chipoodle/devilrpg/entity/nav/NavPlanner.java
- src/main/java/com/chipoodle/devilrpg/entity/nav/NavBreakActionPolicy.java
- src/main/java/com/chipoodle/devilrpg/entity/nav/NavBuildObjectivePolicy.java (new)
Tests/build:
- src/test/java/com/chipoodle/devilrpg/entity/nav/NavBuildObjectivePolicyTest.java (new, 8 assertions)
- build.gradle (register policy test)
No other production/test files changed in this implementation. Existing navigation budgets, AABB/support checks, protection, BREAK LOS, DROP landing, traversal leases and acceptance criteria remain unchanged.

## Required Harness action — STATIC ONLY
Safe fast-forward to current branch tip. Preserve local/untracked work, partial files, logs, artifacts, backups and all protected worlds. Stop on divergence or overwrite risk.
Verify exact source/test blobs and inspect the diff. Run only diagnostic compileJava; Gradle test and policyTest; strict lint_aldea.py; git diff --check.
Confirm the new NAV_BUILD_OBJECTIVE_POLICY_TEST: OK (8 assertions) actually ran. Audit:
- BUILD provenance remains valid across INC_STEP and cannot be authorized by a stale boolean;
- BUILD nodes require non-null aligned actionPos;
- local planning rechecks the same feet/action relation;
- BREAK logs carry UUID/caller and per-entity caps;
- no changes to BREAK LOS/reach, DROP support/landing, budgets, leases or protected worlds.
Harness must not modify production or tests. Persist exact tested HEAD, blob SHAs, commands, exit codes, test counts and preservation status. Stop after static validation.

No Minecraft, runtime, A2 Run2, retry, C4 standalone, or Full12K. No runtime authorization is granted.

A2 remains OPEN.