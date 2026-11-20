# A2 final diagnostic Run1 — Harness handoff (2026-11-20)

## Authorization and scope

The owner explicitly authorized **exactly one additional complete A2 runtime Run1** in this conversation.

This authorization is for Harness to execute the established A2 launcher once, after the static gate passes. It does not authorize a retry, a second run, C4 standalone, Run2, Full12K, or any protected-world modification.

- Target branch: `feature/autonomous-village`
- Remote HEAD at handoff: `1c9ff7422ec3e4dddd28ad094e33d5c3cb6d49ce`
- Static R2 PASS base: `dbff54480d9e564531b268e3bfe01347a464f32c`
- Compare verified: current HEAD is one documentation-only commit ahead of the tested HEAD; no source/test files changed.
- Current production blobs independently checked: `NavPlanner.java=b89de3c5e955f0bf94c939adbaeb76ca98159200`, `NavGraphSearch.java=906b480afff84780d66b4378b447e7b1e4a5a3f0`, `NavExecutor.java=4e73cd3b9bf82dfe449fd0731b126eda32eaf819`, `NavObstruction.java=821c393fcc2c8a4f6d2b505bf578a7a3d3366f8e`, `NavBreakActionPolicy.java=c69014e7ae1b40b5f7c6563e3c04cced74b72179`.
- R2 report: `docs/autonomous-village/2026-11-20-A2-GLOBAL-ASTAR-CORRECTION-STATIC-VALIDATION-R2.md`.
- Previous A2 runtime: `a2n-20261120-140611`, FAIL 0/8 strict, 2/8 perimeter. No BUILD transition occurred.

## Non-negotiable role split

- **ChatGPT owns all production/test source changes on GitHub.**
- **Harness owns local synchronization, static checks, runtime execution, measurement, and evidence reports.**
- Harness must not modify production source or tests. It may only operate the existing harness, collect logs, and write validation/report documentation.
- ChatGPT does not execute local commands, Minecraft, builds, tests, or runtime. Do not ask the owner to enable a local connector for ChatGPT.
- Harness must preserve all local-only work, untracked files, partial files, backups, logs, artifacts and diagnostic worlds. No reset, clean, stash, destructive checkout, or overwrite.

## Mandatory preflight

1. Read `CHATGPT-COLLABORATION-POLICY.md`, `NEXT-SESSION.md`, `CHATGPT-HARNESS-WORKFLOW.md`, both continuation indexes, `docs/PENDIENTE.md`, this handoff, and the referenced reports.
2. Record local branch, local HEAD, remote HEAD, status, untracked files, divergence, and recent log. Fetch first; safe fast-forward only. Stop on divergence or overwrite risk.
3. Confirm remote HEAD is at or descends from `1c9ff7422ec3e4dddd28ad094e33d5c3cb6d49ce`; do not test a different source revision without reporting the discrepancy.
4. Verify protected-world hashes before any runtime:
   - APPDATA A2: `BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7`
   - run/saves A2: `F4EEAA13C56D92812B7B668EFDAF4AB200973A9977006466E969481C09419B44`
   - APPDATA C4 and run/saves C4: `11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF`
5. Confirm the existing reservation `build/a2n/a2n-20261120-135343-prep/` is only a manifest; do not convert it into a world or overwrite it.
6. Read-only inspect the previous runtime trace `build/a2n/a2n-20261120-140611/nav-trace.log` if it exists. Extract existing `BREAK_STAND_CANDIDATE`, `BREAK_LOS_DIAG`, `GLOBAL_FIRST_EXPANSION_CANDIDATE`, and per-UUID search lifecycle events. Do not mutate that run directory.
7. Run the complete static gate on the exact synchronized HEAD:
```powershell
.\gradlew.bat compileJava --info -Pa2nDiagnostics=true -Pa2nPopulationQueryHarness=true -Pa2nTraceNav=true -Pa2nTraceGlobalSearch=true
.\gradlew.bat test --info -Pa2nDiagnostics=true -Pa2nPopulationQueryHarness=true -Pa2nTraceNav=true -Pa2nTraceGlobalSearch=true
python tools\lint_aldea.py --strict
git diff --check
```
8. If any static gate command fails, **do not launch Minecraft**. Report BLOCKED/FAIL with exact output. The one runtime authorization remains unused because no valid A2 run occurred.

## Exactly one authorized runtime

If and only if static validation passes and the protected-world checks match, execute exactly once using the canonical prepared command below. It creates a fresh unique runtime directory; do not reuse the prior run or reserved prep directory.

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

Do not run the wrapper: the disposable-world prep report records its known array-splat named-argument defect. Do not launch the client GUI or request user interaction.

## Required evidence from this one run

Report exact run ID and runtime-world path. Capture all eight attackers separately (UUID ↔ IDX):

1. Spawn/fixture: 16/16 population, 12/12 professionals, 8/8 attacker spawn, chunk readiness.
2. A2 result: perimeter vs strict `DENTRO-PARED`, exact tick/position for each entrant, deaths and cause, final state.
3. Global search: every START/PENDING/COMPLETE/CANCEL/RESTART with caller, tick, start, goal, expansions, frontier, pops, stale, closed skips, reopens, enqueued edges and reason.
4. For every first expansion with zero enqueued edges, include every candidate's position, kind, rejection, transition, feetFrom/feetResult, fit/support/head clearance/protection/chunk-loaded/block state/action/stand.
5. Aggregate `BREAK_STAND_CANDIDATE` reasons (ACCEPTED, OUT_OF_ACTION_REACH, NOT_SINGLE_STEP_REACHABLE, DIAGONAL_PINCHED, STAND_PROTECTED, ACTION_NOT_EXECUTABLE).
6. Aggregate `BREAK_LOS_DIAG` reasons and exact ray hit block/target, stand, target, eye, ray end, and distance. Identify whether the attacker's actual feet cell is accepted as its own BREAK stand.
7. BUILD: route-node production, placement attempts, lease, placement verification, feet approach, grounded/support/AABB verification and route cursor advancement. Zero events must be explicitly reported as NOT EXERCISED.
8. DROP: takeoff, landing, exact feet state, and any exclusion/replan.
9. Preserve all raw logs and hash them. Verify all four protected-world hashes again after the run. Report production/test worktree untouched by Harness.

## Diagnostic questions — no speculative fixes in Harness

The existing evidence has two concrete leads that must be separated, not conflated:

- The 2026-11-19 first-expansion forensic proved IDX3/IDX5 were in 1×1 shafts, all eight adjacent columns were solid, and the graph's horizontal `OFFSETS` had no same-column `(0,0)` edge. That explains why a vertical shaft descent was not representable, but it does **not** authorize unsafe falls.
- The 2026-11-20 Run1 produced 40/56 `BREAKABLE/NODO_NO_PISABLE` candidates and zero accepted edges for IDX3/4/5. The exact BREAK stand/LOS rejection cause was not included in the prior summary. Existing `BREAK_STAND_CANDIDATE` and `BREAK_LOS_DIAG` telemetry must be counted and inspected.

Do not raise budgets, enlarge radii, weaken support/headroom/AABB/collision/protection/LOS/landing predicates, alter walls, teleport mobs, or change acceptance thresholds. Do not modify production/tests.

## After the run

- Persist a dated runtime report, raw log hashes, protected-world before/after hashes, exact tested SHA and run ID.
- Update `NEXT-SESSION.md`, `CONTINUAR.md`, `docs/CONTINUAR.md`, and `docs/PENDIENTE.md`.
- Stop. **No retry, no Run2, no C4 standalone, no Full12K.**
- Return the evidence to ChatGPT. ChatGPT will implement the final evidence-backed source correction on GitHub and add/update regression tests. Harness then performs static validation of that new commit; this does not authorize another runtime.

## D* Lite decision

D* Lite is **not implemented** in the current branch. The production global planner is resumable A* (`NavGraphSearch.Session`). D* Lite was deliberately deferred in the architecture document. The current first-expansion evidence is about missing/invalid graph edges; D* Lite cannot create a legal successor where the graph emits none. Do not substitute D* Lite for fixing edge generation. Revisit D* Lite only after the action-aware graph is correct and runtime evidence shows repeated replanning cost is a separate bottleneck.
