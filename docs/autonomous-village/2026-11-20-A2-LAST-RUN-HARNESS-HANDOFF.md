# A2 final Run1 — Harness handoff (2026-11-20)

## Authorization and exact scope

The owner explicitly authorized **exactly one additional complete A2 runtime Run1**.

Harness must execute it once, only after the static gate passes. This does not authorize a retry, second run, C4 standalone, Run2, Full12K, or modification of protected worlds.

- Repository: `chipoodle/DevilRpg`
- Branch: `feature/autonomous-village`
- Production implementation commit: `7e78c9360c7960f13da6062f38196e8b1627b93a`
- Final implementation + report base: `3a4767327ef9697650f8ba43dedf90c2dcfeeba4`
- Implementation report: `docs/autonomous-village/2026-11-20-A2-VERTICAL-ONLY-EDGE-IMPLEMENTATION.md`
- The current branch tip may be a documentation-only descendant of `3a4767327ef9697650f8ba43dedf90c2dcfeeba4`; verify it through GitHub and verify the source/test blobs below.
- Prior static R2 PASS on `dbff54480d9e564531b268e3bfe01347a464f32c` does **not** validate the new vertical-edge source change.
- Previous runtime `a2n-20261120-140611`: FAIL 0/8 strict, 2/8 perimeter; no BUILD node was exercised.

## Non-negotiable role split

- **ChatGPT owns production and test source changes directly on GitHub.**
- **Harness owns local synchronization, builds, tests, lint, runtime execution, measurement and evidence reports.**
- Harness must not modify production source or tests. It may run the established harness, collect logs and write validation/report documentation only.
- ChatGPT does not execute local commands, Gradle, tests, Minecraft or runtime. Do not ask the owner to enable a local connector for ChatGPT.
- Preserve local-only work, untracked files, partial files, backups, logs, artifacts and diagnostic worlds. No reset, clean, stash, destructive checkout or overwrite.

## Mandatory preflight

1. Read `CHATGPT-COLLABORATION-POLICY.md`, `NEXT-SESSION.md`, `CHATGPT-HARNESS-WORKFLOW.md`, both continuation indexes, `docs/PENDIENTE.md`, the implementation report and this handoff.
2. Record local branch, local HEAD, remote HEAD, status, untracked files, divergence and recent log. Fetch first; safe fast-forward only. Stop on divergence or overwrite risk.
3. Sync the latest documentation descendant of implementation HEAD `3a4767327ef9697650f8ba43dedf90c2dcfeeba4`. Verify exact source/test blobs:
   - `NavNeighborPolicy.java`: `e0378421c0ea024b34b4f95354e5780db78202d9`
   - `NavPlanner.java`: `274af17a32a7aa435a26457f883438513ce207dc`
   - `NavGoalMetric.java`: `7d5b2c6e91555965caa592b7c328bee056186f0f`
   - `NavNeighborPolicyTest.java`: `0ffd6323dc21a3f9c1dd52f23c71570d6d475625`
   - `build.gradle`: `ab8e949c055676df0f4cd0e4069c9839f80f1382`
4. Confirm both local and global expansion loops use the shared `NavNeighborPolicy.offsets()` (eight horizontal directions plus same-column `(0,0))). Confirm every candidate still goes through `validarArista`.
5. Verify protected-world hashes before runtime:
   - APPDATA A2: `BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7`
   - run/saves A2: `F4EEAA13C56D92812B7B668EFDAF4AB200973A9977006466E969481C09419B44`
   - APPDATA C4 and run/saves C4: `11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF`
6. Confirm `build/a2n/a2n-20261120-135343-prep/` remains only a manifest; do not turn it into a world or overwrite it.
7. Read-only inspect prior `build/a2n/a2n-20261120-140611/nav-trace.log` if present. Extract existing `BREAK_STAND_CANDIDATE`, `BREAK_LOS_DIAG`, `GLOBAL_FIRST_EXPANSION_CANDIDATE`, and search lifecycle events. Do not modify that directory.
8. Run the complete static gate on the exact synchronized HEAD:
```powershell
.\gradlew.bat compileJava --info -Pa2nDiagnostics=true -Pa2nPopulationQueryHarness=true -Pa2nTraceNav=true -Pa2nTraceGlobalSearch=true
.\gradlew.bat test --info -Pa2nDiagnostics=true -Pa2nPopulationQueryHarness=true -Pa2nTraceNav=true -Pa2nTraceGlobalSearch=true
python tools\lint_aldea.py --strict
git diff --check
```
9. Confirm `NAV_NEIGHBOR_POLICY_TEST: OK (16 assertions)` and the complete existing policy suite. Audit that budgets, radii, vertical range, support/headroom, collision, protection, safe-drop, landing, placement, BREAK LOS/reach, leases and acceptance criteria are unchanged.
10. If any static command fails, **do not launch Minecraft**. Report exact failure and stop. The one runtime authorization is only consumed by a valid A2 runtime execution.

## Exactly one authorized runtime

If and only if static validation passes and protected-world hashes match, execute exactly once using the established canonical command. It must generate a new unique runtime directory. Do not reuse the prior run or the prep reservation.

```powershell
$env:ORG_GRADLE_PROJECT_a2nDiagnostics='true'
$env:ORG_GRADLE_PROJECT_a2nPopulationQueryHarness='true'
$env:ORG_GRADLE_PROJECT_a2nTraceNav='true'
$env:ORG_GRADLE_PROJECT_a2nTraceGlobalSearch='true'
$env:JDK_JAVA_OPTIONS='-Ddevilrpg.a2n.traceNav=true -Ddevilrpg.a2n.traceGlobalSearch=true -Ddevilrpg.a2n.traceStair=true -Ddevilrpg.a2n.traceStairCandidates=true -Ddevilrpg.a2n.traceNav.verboseDrops=true'

.\build\a2n\lanzar-a2n.ps1 -Modo raid -Semilla 12345 -LimiteMinutos 60 `
  -Pared 0 -FaseA 3000 -FaseB 6000 -Rampa 0 -Perfil 0 -Inundacion 0 `
  -NavCustom true -Presupuesto 900 -GlobalBudget 8000
```

Do not use the wrapper: the disposable-world prep report records its array-splat named-argument defect. Do not launch the client GUI or request user interaction.

## Required evidence from this one run

Record exact run ID, runtime-world path, exact tested SHA and timestamp. Report separately for all eight attackers (UUID ↔ IDX):

1. Fixture/spawn: 16/16 population, 12/12 professionals, 8/8 attacker spawn, chunk readiness.
2. A2: strict `DENTRO-PARED` vs perimeter, tick/position for each entrant, deaths and cause, final state.
3. Search lifecycle: START/PENDING/COMPLETE/CANCEL/RESTART with caller, tick, start, goal, expansions, frontier, pops, stale, closed skips, reopens, enqueued edges and reason.
4. Every first expansion with zero enqueued edges: every candidate's position, kind, rejection, transition, feetFrom/feetResult, fit/support/head clearance/protection/chunk-loaded/block state/action/stand.
5. `GLOBAL_FIRST_EXPANSION_END` candidate/enqueued totals. With nine directions × seven vertical levels, the theoretical candidate count per full first expansion is now 63 (subject to radius filtering).
6. BREAK stand reason counts: ACCEPTED, OUT_OF_ACTION_REACH, NOT_SINGLE_STEP_REACHABLE, DIAGONAL_PINCHED, STAND_PROTECTED, ACTION_NOT_EXECUTABLE.
7. BREAK LOS reason counts and hit details: stand, target, eye, rayEnd, hit block/type, distance. Identify whether the attacker's current feet cell was considered and accepted as its own attack stand.
8. BUILD: node generation, lease, placement attempt/verification, feet approach, grounded/support/AABB checks and route cursor advancement. Explicitly mark zero events NOT EXERCISED.
9. DROP: takeoff, landing, exact feet state, physical descent, and exclusion/replan events.
10. Preserve raw logs and hash them. Recheck all protected-world hashes after runtime. Confirm Harness did not modify production or tests.

## Why this change / what not to conflate

- The 2026-11-19 first-expansion forensic proved IDX3/IDX5 were in 1×1 shafts with all eight adjacent columns solid. The graph omitted same-column `(0,0)`, so a vertical-only safe descent could not be represented.
- The 2026-11-20 Run1 summary showed 40/56 BREAKABLE/NODO_NO_PISABLE and zero accepted edges for IDX3/4/5. Exact BREAK stand/LOS causes were not included in the summary and must be extracted from existing telemetry.
- The new vertical-only direction only adds candidates. It does **not** bypass `validarArista`; unsafe falls and unsupported/blocked landings remain rejected.
- Do not raise budgets, enlarge radii, weaken support/headroom/AABB/collision/protection/LOS/landing predicates, alter walls, teleport mobs, or change acceptance thresholds.
- D* Lite is not implemented. Current production search is resumable A*. The immediate defect was missing graph connectivity; D* Lite cannot invent a legal successor where none exists.

## After the run

- Persist a dated runtime report, raw log hashes, protected-world before/after hashes, exact tested SHA and run ID.
- Update `NEXT-SESSION.md`, `CONTINUAR.md`, `docs/CONTINUAR.md` and `docs/PENDIENTE.md`.
- Stop. **No retry, no Run2, no C4 standalone, no Full12K.**
- Return the evidence to ChatGPT. ChatGPT will implement any final evidence-backed correction and regression tests on GitHub. Harness then validates that commit statically; this does not authorize another runtime.
