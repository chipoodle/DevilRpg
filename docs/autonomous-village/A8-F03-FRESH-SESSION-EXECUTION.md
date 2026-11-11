# A8-F03 — Fresh Harness Session Execution (C1/C2/C3/Gate)

Status: **PASS (post-fix)**. This file preserves the original fixture-only results: C1 and
C2 failed at baseline (§§3–4) because a removed/non-log target kept its task/lease and was
still authorized. The narrowly scoped production correction and the complete post-fix
regression campaign are recorded in §14; there all five scenarios PASS and every verification
command succeeds.

A8-F03 is closed for the mandatory runtime criteria. No commit, push, branch change or
protected save access was made.

## 1. Repository baseline (revalidated, not assumed)

Initial state at session start:

```text
branch feature/autonomous-village
HEAD   97220be5453b57387002b9bc91935807cc665274
staged 0
unstaged 26
untracked 174
```

Revalidated with `git rev-parse`, `git status --porcelain` and
`git ls-files --others --exclude-standard`. All pre-existing modified/untracked files were
preserved. `src/main/java/com/chipoodle/devilrpg/debug/` is git-ignored, so the fixture
controller edits do not alter the tracked baseline.

Final state after this session:

```text
branch feature/autonomous-village
HEAD   97220be5453b57387002b9bc91935807cc665274
staged 0
unstaged 26
untracked 176   (174 baseline + TalarStepBarrier.java + this report)
test clients 0
```

No commit, push, branch change or destructive Git operation was performed.

## 2. Fixture implementation

### 2.1 Test-only barrier (`TalarStepBarrier`, new file)

`src/main/java/com/chipoodle/devilrpg/agent/TalarStepBarrier.java` is **default off**. It
does nothing unless both properties name the fixture worker:

```text
-Ddevilrpg.test.talarBarrier=true
-Ddevilrpg.test.talarBarrierWorker=<villager UUID>
```

It only holds the Step that the Goal already selected and pauses the Goal's own progression
for that worker. It never acquires/releases a lease, never stops/replaces a path, never
touches a planner cache and never changes the Goal's target or phase.

Deterministic events:

```text
[C1-BARRIER] event=ARMED
[C1-BARRIER] event=STEP-SELECTED ... tick=<T> navAuthorized=false
[C1-BARRIER] event=RELEASED     ... tick=<T> navAuthorized=false
[C1-BARRIER] event=STEP-RESUMED  ... tick=<T> navAuthorized=false->lifecycle
```

### 2.2 Goal hooks (three call sites, production behavior unchanged by default)

`VillagerLumberjackGoal`:

- `pasoDeCamino()`: when a Step is held, return the held destination instead of re-planning;
  otherwise `TalarStepBarrier.observeStep(...)` records the selected Step.
- `tick()`: after `BlockPos paso = pasoDeCamino(level);` and **before**
  `syncNavigationLease(level); navegar(level, paso);`, call
  `TalarStepBarrier.pauseAfterStepSelected(...)`; while paused the Goal returns without
  authorizing navigation.
- No change to target/phase/operations. With the properties absent every call is inert.

This is the exact boundary requested: the Step has been produced by
`VillagerAgent.nextTalarStep()` / `TalarStepPlanner.next()`, and
`navigateTo()->syncForestryNavigation()->ForestryNavigationGateway.moveTo()` has not run yet
for that Step.

### 2.3 Fixtures (in-game debug controller, disposable A5WALL copies)

All fixtures run in a fresh copy of the disposable `A5WALL` world under
`build/<run-id>/game/saves/A5WALL`, launched with
`build/a5-closure/run-a5.ps1`, `-Pa2nDiagnostics=true`, `-Da5.scenario=<fixture>`:

| Fixture | Scenario id | Purpose |
|---|---|---|
| C1 | `c1-step-barrier` | remove target between Step selection and authorization |
| C2 | `c2-invalid-target` | present a non-log candidate to the real planner predicate |
| C3 | `c3-obstruction` | valid log, unreachable standing cell/route |
| Gate valid | `gate-valid` | real village wall, real gate, exterior target |
| Gate negative | `gate-negative` | all four gate mouths unavailable |

Shared setup uses production generation/APIs only: `VillageManager.preGenerate`, real
`VillageGenerator` wall and gates, `VillageGenerator.cotaDeLaPlaza`,
`VillageGenerator.plantonesDeLaArboleda`, `VillageManager.pasoParaCruzarElMuro`,
`VillageManager.esCeldaDePie`, `VillageManager.esDeDentroDelMuro`,
`TalarStepPlanner.standingCell`, the real `VillagerLumberjackGoal`, the real
`VillagerAgent`/gateway/lease, and vanilla `PathNavigation`. Interior targets are removed
through the world (block updates), never by clearing caches, leases or paths.

## 3. C1 — target removed after Step selection, before authorization

Run: `build/a8-f03-c1-20261110-181602` (`result.json`: **FAIL**).
Worker `d48786e1-70e3-4cc8-a6e0-3cc109c0f39e`.
A = `98,98,0`, selected Step = `97,98,0` (standing cell, `standing=true gate=false`).

Timeline (game ticks):

```text
tick 4148  [C1-BARRIER] STEP-SELECTED target=98,98,0 step=97,98,0 navAuthorized=false
tick 4148  [C1] BARRIER-REACHED taskA=7ccf14af-3f2d-3748-b9e3-a58fccaa4e04
           path=0 lease=empty
tick 4148  [C1] TARGET-REMOVED target=98,98,0 removedLogs=5
tick 4148  [C1-BARRIER] RELEASED navAuthorized=false
tick 4150  [AGENT-AUTH] ACQUIRE task=7ccf14af-... (the removed target's task)
tick 4150  [C1-BARRIER] STEP-RESUMED
tick 4150  [NAV-GATE] mode=ENFORCE result=AUTHORIZED command=MOVE task=7ccf14af-...
           target=97,98,0 owner=WORK
tick 4150  [C1] RESUMED targetNow=98,98,0 navActive=true pathBefore=0 pathNow=329564605
           lease=d487.../7ccf14af-.../WORK lastNavTask=7ccf14af-...
tick 4151+ [R3] CACHE-CLEAR target=98,98,0 reason=target-not-log   (repeated)
tick 4152+ [NAV-GATE] AUTHORIZED command=MOVE target=98,98,0       (repeated, renewed lease)
tick 4282  [NAV-GATE] STOP result=CLEANUP_ALLOWED owner=Optional.empty
tick 4540  [C1] B-SELECTED taskB=f2eabeb1-1b2d-3f6d-b3bb-287232c231ec targetB=62,98,14
tick 4542  owner=B, path=1626205751, nav=true
```

Verdict: **FAIL**. At the resumed tick the removed target was still owned and the gateway
authorized a MOVE for its task; the planner's `Step(null)` invalidation only began one tick
later and did not stop the authorized navigation. A subsequent valid TALAR task was accepted
naturally (B), and the old lease was cleaned up only when the Goal later cleared the target.

Acceptance:

| C1 criterion | Result |
|---|---|
| Worker/task/target/Step captured | PASS |
| Barrier reached before authorization | PASS (`navAuthorized=false`) |
| Target removed while paused | PASS (5 logs, tick 4148) |
| Removed target receives no navigation authorization | **FAIL** (`AUTHORIZED` at 4150+) |
| No stale ownership | **FAIL** (lease/task A owned after resume) |
| Cache invalidation observed | PASS (`target-not-log`, 4151+) |
| Subsequent valid TALAR accepted | PASS (task B, 4540-4542) |

## 4. C2 — non-log candidate evaluated by the real planner predicate

Run: `build/a8-f03-c2b-20261110-182314` (`result.json`: **FAIL**).
Worker `0becc3a2-d066-4400-b86b-2174ff57f220`.
Candidate `98,98,0` was a real log selected by the Goal, then the fixture placed `STONE`
there (`wasLog=true nowLog=false`) without touching any private invalidation method.

Timeline:

```text
tick 4148  [C2] TARGET-INVALIDATED taskA=acf093b5-... wasLog=true nowLog=false
tick 4149  [C2] AFTER-INVALIDATION nav=false path=0 lease=empty
tick 4150  [R3] CACHE-CLEAR target=98,98,0 reason=target-not-log
tick 4150  [R9-NAV] task=acf093b5-... result=AUTHORIZED owner=WORK path=0 active=false
tick 4150  [C2] STALE-NAV taskA=acf093b5-... path=141317097 owner=WORK
tick 4152  [R9-NAV] task=acf093b5-... result=AUTHORIZED path=141317097 active=true
tick 5010  [C2] B-SELECTED taskB=e50bcbb2-... target=53,97,-17
tick 5043  owner=B, path=1156953550, nav=true
```

Verdict: **FAIL**. The real planner rejected the candidate (`CACHE-CLEAR ... target-not-log`),
but the Goal fell back to the stale target position and the gateway kept authorizing the
invalid task. A subsequent valid TALAR task was accepted.

Acceptance:

| C2 criterion | Result |
|---|---|
| Real predicate rejects the candidate | PASS (`target-not-log`) |
| Navigation absence for invalid candidate | **FAIL** (`AUTHORIZED` at 4150/4152) |
| Path/lease state captured | PASS (path 141317097, lease task A) |
| Stale ownership cleared | PASS eventually (B owner, path 1156953550) |
| Subsequent valid task accepted | PASS |

## 5. C3 — valid log with unavailable route/standing cell

Run: `build/a8-f03-c3b-20261110-183314` (`result.json`: **PASS**).
Worker `065833d9-1c10-462b-80f8-e11ac8e412cd`.
A = `98,98,0` inside a fully sealed 9x9 stone shell; `standingCell` = `97,98,0` (inside,
unreachable from the worker and outside the 3.5 reach from the shell face).

Timeline:

```text
tick 4148  [C3] TARGET-SELECTED taskA=be0ad565-... target=98,98,0
           standing=97,98,0 path=0 navActive=false
tick 4149  [R9-NAV] taskA result=AUTHORIZED target=97,98,0 path=0 active=false
tick 4151  [R9-NAV] taskA result=AUTHORIZED target=97,98,0 path=546219083 active=true
ticks 4151-4363  path 546219083 is re-issued but the worker never reaches the sealed cell
tick 4367  [R9-NAV] taskA path=0 active=false
tick 4371  [R9-NAV] taskA path=0 active=false
tick 4371  [NAV-GATE] STOP result=AUTHORIZED taskA owner=WORK
tick 4373  [C3] A-ABANDONED taskA=be0ad565-... targetNow=-
           navActive=true pathBefore=0 pathNow=65025135 lease=empty stuckTicks=226
tick 4393  [C3] B-SELECTED taskB=5a5001d1-... target=62,98,14
tick 4395  owner=B, path=623104703, nav=true
```

Verdict: **PASS** for the C3 criteria. Real pathfinding did produce an initial path
(`546219083`), but it never reached the sealed standing cell; the path went inactive
(`path=0 active=false` at 4367-4371) and the Goal abandoned the target after 226 stuck ticks
(`A-ABANDONED`), released the lease (STOP at 4371, `owner=empty` at 4373) and accepted a later
valid TALAR task with a new path (`623104703`). The path id present in the abandonment sample
(`65025135`) is after the authorized STOP and does not belong to a live navigation to the
sealed target; `pathBefore=0` was the selection-time sample, not a claim that no path was ever
created.

## 6. Gate valid — real wall, real gate, real crossing

Run: `build/a8-f03-gate3-20261110-184055` (`result.json`: **PASS**).
Worker `ccfdc340-3ec4-4380-815d-160c5625c671`.

Preflight (production village and production predicates):

```text
[A8] WORLD-PREPARED center=80,97,0 cota=97
[A8] TREE-PLANTED base=158,98,0 standing=157,98,0 (outside the wall)
[GATE] PREFLIGHT villageReady=true workerInside=true treeOutside=true
       gateStep=143,97,0 gateStandable=true entryStandable=true
       standing=157,98,0 sealed=496 holes=0 bypass=0 ring=496
```

Real ENFORCE planner output:

```text
[AGENT-STEP] TALAR target=158,98,0 step=143,97,0 standing=true gate=true
```

Physical timeline:

```text
tick 4318  [GATE] TARGET-SELECTED taskA=a74f88c7-04e6-3dd5-ac63-ae501bcde53a
tick 4666  [GATE] CROSSED pos=143,97,0 gateStep=143,97,0
           path=490376429 lease=ccfdc340-.../a74f88c7-.../WORK
tick 4734  [GATE] ARRIVED pos=155,98,0 standing=157,98,0
           path=979670655 lease=.../a74f88c7-.../WORK
```

Verdict: **PASS**. The real planner selected the east gate (`wallGate=true`), the worker
physically crossed the wall at the selected gate cell `143,97,0`, continued outside, and
reached the exterior tree platform (`155,98,0`, adjacent to the selected standing cell
`157,98,0` and within reach of the exterior trunk). No teleport, manual navigation, Step
injection or fabricated gate decision was used.

## 7. Gate negative — no eligible gate

Run: `build/a8-f03-gateneg3-20261110-184904` (`result.json`: **PASS**).
All four gate mouths are sealed 3 wide x 3 high with cobblestone, so the production gate
predicate finds no eligible exit cell and no bypass exists.

```text
[GATE] PREFLIGHT villageReady=true workerInside=true treeOutside=true gateStep=-
       gateStandable=false entryStandable=false standing=157,98,0
       sealed=496 holes=0 bypass=0 ring=496
tick 4317  [GATE] TARGET-SELECTED taskA=23c0454b-... target=158,98,0
tick 4859  [GATE] NEG-REFUSED targetA=158,98,0 targetNow=-
           path=179479059 lease=empty inside=true
tick 5051  [GATE] NEG-RESULT taskB=8bcf63dd-... owner=WORK nav=true
           path=1427477240 inside=true crossed=false refused=true
```

Verdict: **PASS**: no eligible gate, no wall crossing, the blocked exterior target was
abandoned with an empty lease, and a subsequent valid inside TALAR task (`62,98,14`) was
accepted.

Intermediate fixture attempt (preserved): `build/a8-f03-gateneg2-20261110-184602` sealed only
the planner's centre outside cell. The worker crossed at `143,97,-1` through the remaining
3-wide gate mouth (`result=FAIL`). That was **fixture geometry**, not a production bypass: the
gate was physically open and the planner had returned `gateStep=-`. The final negative control
seals the whole mouth.

## 8. Acceptance matrix

| Scenario | Result |
|---|---|
| C1 removal before authorization | **FAIL** (authorized at 4150, cleanup only later) |
| C2 invalid target | **FAIL** (planner rejects; navigation still authorized) |
| C3 obstruction | PASS |
| Gate preflight / `wallGate=true` | PASS |
| Physical gate crossing | PASS |
| Gate negative control | PASS |
| A8-F03 overall | **FAIL / OPEN** |

## 9. Verification

Commands and exact exits:

```text
.\gradlew.bat compileJava
  BUILD SUCCESSFUL, exit 0

python tools\lint_aldea.py --strict
  lint aldea: OK (sin patrones peligrosos), exit 0

git diff --check
  exit 0

.\gradlew.bat -I build/a8-f03-navtests.init.gradle a8NavTests --console=plain
  NAVIGATION_LEASE_TEST: OK
  NAVIGATION_GATEWAY_POLICY_TEST: OK
  NAVIGATION_GATEWAY_HARNESS_TEST: OK
  BUILD SUCCESSFUL, exit 0
```

The three navigation tests are the existing pure-Java programs
`NavigationLeaseTest`, `NavigationGatewayPolicyTest`, `NavigationGatewayHarnessTest`; the
temporary init script only registers a runner for them and is a build artifact under
`build/`.

## 10. Production diff (this session)

- **New**: `src/main/java/com/chipoodle/devilrpg/agent/TalarStepBarrier.java` — test-only,
  default-off barrier.
- **Modified**: `src/main/java/com/chipoodle/devilrpg/entity/goal/VillagerLumberjackGoal.java`
  — import + three barrier hooks (`pasoDeCamino` held-step/observe, `tick` pause). No change
  to target selection, phase logic, harvesting, replanting, delivery or ENTREGAR.
- **Fixture-only (git-ignored)**: `src/main/java/com/chipoodle/devilrpg/debug/A5TestController.java`
  — C1/C2/C3/gate fixtures; no production class changed.
- **Report**: `docs/autonomous-village/A8-F03-FRESH-SESSION-EXECUTION.md`.

No change to `TalarStepPlanner`, `ForestryNavigationGateway`, `NavigationLease`,
`VillageManager`, `VillageGenerator`, NBT or save data.

## 11. Exact blocker and minimal fix location (not applied)

Both failures share one production cause, independent of the barrier:

1. `TalarStepPlanner.next(..., requireLog=true)` returns `Step(null,...)` and clears its
   cache when the target block is missing/non-log.
2. `VillagerLumberjackGoal.pasoDeCamino()` then executes
   `return step.destination() != null ? step.destination() : target;` — the stale `target`
   BlockPos is handed to navigation.
3. `syncNavigationLease()` derives the same task id from the snapshot's non-null `target`
   and acquires/renews the lease; `navegar()` calls
   `ForestryNavigationGateway.moveTo()`, which sees `lease.owns(...)` and returns
   `AUTHORIZED`.

Minimal correction (for a subsequent authorized increment): in the TALAR branch, when the
planner returns `Step(null, ...)`, do **not** fall back to `target`; clear the target, stop
navigation and release the lease through the existing Goal cleanup, then let
`descubrirTrabajo()` select the next valid target. Alternatively, re-validate the selected
target at authorization time (before `syncNavigationLease`/`navigateTo`) with the same
`BlockTags.LOGS` predicate. Either change alters production decision-making and was therefore
not applied in this fixture-only session.

## 12. Artifacts

```text
build/a8-f03-c1-20261110-181602/        C1 FAIL (final)
build/a8-f03-c2b-20261110-182314/       C2 FAIL (final)
build/a8-f03-c3b-20261110-183314/       C3 PASS (final)
build/a8-f03-gate3-20261110-184055/     Gate valid PASS (final)
build/a8-f03-gateneg3-20261110-184904/  Gate negative PASS (final)
build/a8-f03-c2-20261110-181948/        superseded C2 (one-tick sampler bug)
build/a8-f03-c3-20261110-181955/        superseded C3 (5x5 shell was within reach)
build/a8-f03-gate-20261110-182724/      superseded gate (grove re-planting diverted)
build/a8-f03-gate2-20261110-183314/     superseded gate (exterior forest blocked)
build/a8-f03-gateneg-20261110-182724/   superseded negative (premature refuse)
build/a8-f03-gateneg2-20261110-184602/  superseded negative (partial gate seal)
```

Each directory keeps `client.log`, `controller.log`, `init.gradle`, `result.json` and
`shutdown.json`. A5/A6 protected saves were not read or modified. The post-fix runs listed in
§14.3 use the same artifact layout under `build/a8-f03-fix-*`.

## 13. Classification (historical baseline, pre-fix)

| Item | Status |
|---|---|
| Fixture synchronization barrier (C1) | Implemented, default-off, exercised |
| C1 removed-target authorization | **FAIL** |
| C2 invalid-target navigation absence | **FAIL** |
| C3 obstruction cleanup | **PASS** |
| Gate preflight/selection/crossing | **PASS** |
| Gate negative control | **PASS** |
| A8-F03 at baseline | **FAIL — remained open** |

The current post-fix classification is in §14.

## 14. Production fix and post-fix regression (current session)

Status: **PASS**. C1 and C2 now pass at runtime, C3 and both gate scenarios remain passing,
and all verification commands succeed.

### 14.1 Confirmed root cause

Traced end to end before editing:

1. `TalarStepPlanner.next(..., requireLog=true)` uses the authoritative predicate
   `state.is(BlockTags.LOGS)`. For a missing/non-log target it clears the cache and returns
   `Step(null, ..., ...)` (`Step.destination() == null`).
2. `VillagerLumberjackGoal.pasoDeCamino()` retained the stale target through
   `return step.destination() != null ? step.destination() : target;` — the exact field that
   was reused.
3. `syncNavigationLease()` builds the task id from the snapshot's non-null `target` and
   acquires/renews; then `navegar()` calls
   `VillagerAgent.navigateTo()->syncForestryNavigation()->ForestryNavigationGateway.moveTo()`,
   which sees `lease.owns(...)` and returns `AUTHORIZED` (and `PathNavigation` moves toward
   the stale Step).

Callers of the authorization path are `VillagerLumberjackGoal.tick()` (tick-start sync plus
the post-Step sync/navegar pair) and `irAlObjetivo()` (target selection). The barrier only
held the Step across the pause; it did not create the defect.

### 14.2 Correction (files and behavior)

- `agent/TalarStepPlanner.java` — added the single authoritative predicate
  `isValidTalarTarget(ServerLevel, BlockPos)` (`BlockTags.LOGS`) and reused it in `next()` for
  both the `requireLog` rejection and the standing-cell cache. No duplicate predicate in the
  Goal.
- `entity/goal/VillagerLumberjackGoal.java`:
  - **Removed the stale fallback**: in the TALAR branch, `Step.destination() == null` now
    returns `null` (logged as `[NAV-GUARD] event=NO-STEP ... reason=planner-invalid`). The
    non-TALAR fallback is unchanged.
  - `tick()` and `irAlObjetivo()` handle a null Step through
    `abandonarObjetivoInvalido()`: stop navigation, release the lease, mark the failed point,
    clear `target`, return the controller to `SCOUTING`, set the normal rest period and
    persist the legacy task. This is the existing invalidation lifecycle, not a new one.
  - `syncNavigationLease()` refuses to acquire/renew a TALAR lease when
    `isValidTalarTarget(...)` is false, and stops/releases first.
  - `navegar()` has the final narrow guard immediately before
    `agent.navigateTo`/`ForestryNavigationGateway.moveTo`: an invalid TALAR target logs
    `[NAV-GUARD] event=DENIED reason=target-not-log` and **no MOVE is issued**; navigation is
    stopped through `pararNavegacion`.
- `debug/A5TestController.java` (git-ignored fixture code) — C1/C2 classifiers updated to
  assert the corrected behavior (`staleLease=false`, `staleNav=false`, target cleared,
  successor accepted). The test-only `TalarStepBarrier` is preserved default-off.
- Report: this file.

No change to target selection, Step planning, wall/gate geometry, navigation ownership,
ENTREGAR (legacy), NBT formats or persistence semantics.

### 14.3 Post-fix results

| Scenario | Run | Worker | Result |
|---|---|---|---|
| C1 removed target before authorization | `a8-f03-fix-c1-20261110-185729` | `f1fa8e34-8aaa-423c-97c7-1b4cc3d1f369` | **PASS** |
| C2 non-log candidate | `a8-f03-fix-c2-20261110-185729` | `e9af3689-4864-4e59-842a-daa6ac16c38b` | **PASS** |
| C3 obstruction | `a8-f03-fix-c3-20261110-185729` | `1d7cf6cd-eeab-4961-8f06-1a1c48823b3a` | **PASS** |
| Gate valid | `a8-f03-fix-gate-20261110-185729` | `28436412-f6c5-4548-8d84-01f9ba61cf2e` | **PASS** |
| Gate negative | `a8-f03-fix-gateneg-20261110-185729` | `25b83cd0-046d-4288-83b2-b24d2ce7557b` | **PASS** |

### 14.4 C1 post-fix timeline (PASS)

`taskA=09433278-08b7-310d-93ee-16769a7168cf`, target `98,98,0`, selected Step `97,98,0`
(`standing=true gate=false`).

```text
tick 4148  [C1-BARRIER] STEP-SELECTED navAuthorized=false
tick 4148  [C1] TARGET-REMOVED removedLogs=5; RELEASED
tick 4150  [C1-BARRIER] STEP-RESUMED tick=4150
tick 4150  [NAV-GUARD] event=DENIED reason=target-not-log target=98,98,0
           objetivo=97,98,0 mode=ENFORCE
tick 4150  [C1] RESUMED navActive=false pathBefore=0 pathNow=0
           lease=empty lastNavTask=null
tick 4152  [R3] CACHE-CLEAR target=98,98,0 reason=target-not-log
tick 4152  [NAV-GUARD] event=NO-STEP reason=planner-invalid
tick 4152  [NAV-GUARD] event=ABANDON target=98,98,0 lease=released
tick 4152  [C1] TARGET-CLEARED targetNow=- lease=empty nav=false path=0
tick 4154  [C1] B-SELECTED taskB=3d5217d8-04ec-3d6e-85ba-8d284896b8c9
           targetB=62,98,14
tick 4156  [AGENT-AUTH] ACQUIRE taskB; [NAV-GATE] MOVE AUTHORIZED target=61,98,14
           path=1177709379
```

No `[NAV-GATE] MOVE` and no `[R9-NAV]` entry references `taskA` after the removal tick; the
first authorized MOVE belongs to `taskB`. Acceptance: no stale authorization, no continuing
path, normal cleanup, valid successor selected and authorized — **PASS**.

### 14.5 C2 post-fix timeline (PASS)

`taskA=39208c33-0815-37bb-a273-573e772121a5`, candidate `98,98,0` replaced by stone at tick
4147 (`wasLog=true nowLog=false`).

```text
tick 4147  [C2] TARGET-INVALIDATED
tick 4148  [C2] AFTER-INVALIDATION nav=false path=0 lease=empty lastNavTask=null
tick 4149  [R3] CACHE-CLEAR target=98,98,0 reason=target-not-log
tick 4149  [NAV-GUARD] event=NO-STEP reason=planner-invalid
tick 4149  [NAV-GUARD] event=ABANDON lease=released
tick 4149  [C2] TARGET-CLEARED targetNow=- nav=false path=0 lease=empty
tick 4151  [C2] B-SELECTED taskB=14846616-d1da-3afa-a9af-bfa3c0056857 targetB=62,98,14
tick 4153  [NAV-GATE] MOVE AUTHORIZED taskB target=61,98,14 path=1167142109
```

No authorization or MOVE references the invalid candidate after 4147 — **PASS**.

### 14.6 C3 post-fix timeline (PASS)

`taskA=7d919733-2fd6-3b39-87bb-e9e1b67f722d`, target `98,98,0`, standing cell `97,98,0`
inside the sealed 9×9 shell.

```text
tick 4149+ [NAV-GATE] MOVE AUTHORIZED taskA target=97,98,0 (valid log; real pathfinding)
ticks 4365-4371 [R9-NAV] taskA path=0 active=false
tick 4371  [NAV-GATE] STOP result=AUTHORIZED taskA owner=WORK
tick 4373  [C3] A-ABANDONED targetNow=- stuckTicks=226 lease=empty
tick 4393  [C3] B-SELECTED taskB=baf9ac6c-7373-3ea3-918d-1a66420315c7 targetB=62,98,14
tick 4395  [NAV-GATE] MOVE AUTHORIZED taskB target=61,98,14 path=1731885939
```

The worker never reached the sealed target; taskA navigation became inactive and the lease
was empty after abandonment; the later valid target was authorized normally — **PASS**.

### 14.7 Gate valid post-fix timeline (PASS)

```text
[GATE] PREFLIGHT gateStep=143,97,0 gateStandable=true entryStandable=true
       standing=157,98,0 sealed=496 holes=0 bypass=0
[AGENT-STEP] TALAR target=158,98,0 step=143,97,0 standing=true gate=true
tick 4318  TARGET-SELECTED taskA=ba8e8cce-a94f-3456-9be7-c2024fee3f87 target=158,98,0
tick 4670  [GATE] CROSSED pos=143,97,0 gateStep=143,97,0 path=1187411454 lease=WORK
tick 4737  [GATE] ARRIVED pos=155,98,0 standing=157,98,0 path=1187411454 lease=WORK
```

Real preflight passed, the ENFORCE planner selected the intended gate (`gate=true`), the
worker physically crossed at the selected gate cell and reached the exterior tree platform
(adjacent to the selected standing cell `157,98,0`, within reach of the trunk). No teleport or
position manipulation — **PASS**.

### 14.8 Gate negative post-fix timeline (PASS)

```text
[GATE] PREFLIGHT gateStep=- gateStandable=false entryStandable=false
       standing=157,98,0 sealed=496 holes=0 bypass=0
tick 4318  [GATE] TARGET-SELECTED taskA=f46858ae-abd6-37ab-9405-78a020c080f7 target=158,98,0
tick 4824  [GATE] NEG-REFUSED targetNow=- lease=empty inside=true
tick 5016  [GATE] NEG-RESULT taskB=e4d77da9-7ffb-3134-b99b-71c5f5ed9de7 owner=WORK
           nav=true path=2001325078 inside=true crossed=false
```

All four 3×3 gate mouths sealed; no gate Step, no crossing, exterior target abandoned with an
empty lease, and a valid interior target subsequently authorized — **PASS**.

### 14.9 Verification (post-fix)

```text
.\gradlew.bat compileJava
  BUILD SUCCESSFUL, exit 0

python tools\lint_aldea.py --strict
  lint aldea: OK (sin patrones peligrosos), exit 0

git diff --check
  exit 0

.\gradlew.bat -I build/a8-f03-navtests.init.gradle a8NavTests --console=plain
  NAVIGATION_LEASE_TEST: OK
  NAVIGATION_GATEWAY_POLICY_TEST: OK
  NAVIGATION_GATEWAY_HARNESS_TEST: OK
  BUILD SUCCESSFUL, exit 0
```

### 14.10 Final repository state

```text
branch feature/autonomous-village
HEAD   97220be5453b57387002b9bc91935807cc665274
staged 0
unstaged 26
untracked 176
test clients 0
```

The tracked count is unchanged because `TalarStepPlanner.java` and
`VillagerLumberjackGoal.java` were already among the pre-existing 26 modified files; the
fixture controller is git-ignored. No protected save was read or modified.

### 14.11 Post-fix acceptance matrix

| Criterion | Result |
|---|---|
| C1 removed target receives no authorization | **PASS** |
| C1 no continuing path / normal cleanup | **PASS** |
| C1 valid successor selected/authorized | **PASS** |
| C2 rejected candidate never authorized | **PASS** |
| C2 normal recovery to a valid log | **PASS** |
| C3 obstruction regression | **PASS** |
| Gate valid regression | **PASS** |
| Gate negative regression | **PASS** |
| Build / lint / diff / navigation tests | **PASS** |
| A8-F03 | **PASS** |
