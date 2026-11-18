# A2 Phase 38 — DROP descent physical execution trace (BLOCKED)

## 1. Result

**BLOCKED.** Stage B was authorized and attempted with temporary read-only probes. The diagnostic run
crashed in `NavObstruction.fits` / `NavPlanner` before any `DROP_DESCENT_COMMAND`; no IDX4/IDX8 tick
trace could be produced. Per the Phase 38 rule, the run was not repeated for a favorable cycle.

- Branch: `feature/autonomous-village`
- HEAD: `cd5890c2cab4f394b32690af2b7e8f1d8f3e23c0`
- Production: `6aec35c651810084691f99611c48652fe0a881d7` (restored exactly)
- Run: `a2n-20261118-110342`
- Artifacts: `build/a2n/a2n-20261118-110342/phase38/`

## 2. Temporary instrumentation

Added only for the run and fully reverted:

- `DropDescentProbe` (new, temporary): before/after `travel`, position/AABB/deltaMovement/onGround/
  collisions/fallDistance, Navigation path node count/next index/target, MoveControl class/hasWanted/
  wanted XYZ/speed, block states and collision shapes.
- `AggressiveZombieEntity.travel` override calling the probe before/after `super.travel`.
- `NavExecutor` call to start the probe at the adjacent `DROP_DESCENT_COMMAND`.

No movement target, speed, predicate, budget, tolerance or ownership was changed.

After the run all instrumentation was removed and the two touched production files were restored from
HEAD. `git diff --check` is clean.

## 3. Crash evidence

```text
java.lang.Error: ServerHangWatchdog detected that a single server tick took
60000128.00 seconds (should be max 0.05)

at BlockCollisions.computeNext
at NavObstruction.fits(NavObstruction.java:98)
at NavObstruction.classify(NavObstruction.java:207)
at NavPlanner.validarArista(NavPlanner.java:115)
at NavPlanner.planGlobalInternal(NavPlanner.java:496)
at NavExecutor.rutaGlobalHacia(NavExecutor.java:1456)
at NavExecutor.elegirObjetivoLocal(NavExecutor.java:1560)
at NavExecutor.escalonarObjetivoLocal(NavExecutor.java:1867)
at NavExecutor.tryPlan(NavExecutor.java:3302)
```

The stack contains no probe frame. No `phase38` CSV was created, so the probe never started; the crash
occurred in global planning before the first DROP. The exact reason for the planner stall is outside
this phase.

## 4. Artifacts

```text
build/a2n/a2n-20261118-110342/phase38/crash-server.txt
build/a2n/a2n-20261118-110342/phase38/launcher-status.txt
build/a2n/a2n-20261118-110342/phase38/execution-summary.txt
build/a2n/a2n-20261118-110342/phase38/SHA256SUMS.txt
```

```text
103ACBDFA93443C6798604EDC9FA9B934B82AD2771D98F8310D5938E25405A04  crash-server.txt
CE8D82EFC2D7AA67B5CB7760E14054E5AA6BD1D1CD2E13D7C7DDC29029AB202B  execution-summary.txt
EA81C9B463FA1CB493A053F7FDDBD938E7EB8E498485AAC38EC74FAAF6068FB7  launcher-status.txt
```

`idx4-tick-trace.csv`, `idx8-tick-trace.csv`, `movement-callers.log`,
`navigation-path.log`, `collision-shapes.log` and `block-states.log` are absent because the probe
never activated.

## 5. Integrity

```text
PRODUCTION_MODIFIED: false (temporary probe reverted)
TESTS_MODIFIED: false
ORIGINAL_WORLDS_MODIFIED: false
GUI_OR_FOCUS_INTERVENTION: none
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

## 6. Classification

```text
PHASE38_STATUS: BLOCKED
IDX4_TRACE: ABSENT
IDX8_TRACE: ABSENT
ROOT_CAUSE_PHYSICAL: UNRESOLVED
```

Stage B did not reach the DROP window; no new evidence distinguishes collision from MoveControl/
Navigation completion.

## 7. Next action

Do not run another A2 solely for this probe. First stabilize or bypass the global-planner
`BlockCollisions` watchdog hang for the diagnostic scenario (without changing navigation behavior), or
use a deterministic fixture that reaches the adjacent DROP without global planning. Then re-authorize
Stage B for IDX4/IDX8 first DROP cycle. A2 remains OPEN; no Run 2 or 12K.
