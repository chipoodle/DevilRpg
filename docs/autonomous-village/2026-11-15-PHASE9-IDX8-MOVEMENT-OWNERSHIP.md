# Phase 9 — IDX8 Movement Ownership and 9000-Tick Reproduction (2026-11-15)

## Source call chains

### Combat ownership

- `PursueTargetGoal` priority **4**, flags `MOVE|LOOK`.
- `canUse`: live target and `AdvanceUnderFirePolicy.shouldChase(objetivoCampanaActivo(), distance²)`.
- After conquest, `objetivoCampanaActivo()` is false, so distant targets can pass.
- `tick`: `pathPlanner.navigateTo(selectedDestination, 1.2)`; owns movement when active.
- `PursueTargetGoal` is a higher-priority MOVE goal than `MoveToVillageCenterGoal` (priority 7); both cannot own MOVE simultaneously.

### Strategic ownership

- `MoveToVillageCenterGoal` sends `navExecutor.setObjectiveChain(cadenaDeAproximacion(center))`.
- `aiStep`:
  ```text
  combate = pursueTargetGoal.estaActivo()
  combateEnAlcance = combate && distance² <= 4.0
  executorMayMove(true, combate, combateEnAlcance)
  ```
- Distant combat target → `navExecutor.suspendForCombat()`; melee target → executor may tick.
- `NavExecutor` state/planning/BREAK only runs when it owns movement.

## Diagnostic instrumentation

Read-only additions:
- `AggressiveZombieEntity.persiguiendoActivoA2()`
- `AggressiveZombieEntity.pathPlannerDiagA2()`
- `AggressiveZombieEntity.navExecutorSuspendedA2()`
- `NavExecutor.suspendidoDiag()` / `ultimoSuspendidoTickDiag()`
- Harness `-Da2n.probeTargets=true` now logs target UUID/distance, `combateActivo`, `navSuspendido`, `executorMayMove`, legacy planner state (`plan`, waypoint, stalled/retry), strategy route.

No movement, budget, collision, combat or acceptance behavior changed.

## Diagnostic run

- Run ID: **`a2n-20261115-214638`**
- World: `build/a2n/a2n-20261115-214638/game/a2n` (disposable)
- Logs: `build/a2n/a2n-20261115-214638/srv.log`
- Marker: **DIAGNOSTIC — NOT ACCEPTANCE**
- Configuration: seed 12345, FaseA 3000, FaseB 9000, budgets 900/8000, custom nav.
- Result: 8/8 spawned; **8/8 reached perimeter**; 0 alive at 9000; C4 12/12 (diagnostic, not official).
- **IDX8 entered at t=2279 and died at t=2677**; no eastern-outcrop stall.

### Representative IDX8 ownership

```text
t=2600 pos=650,71,575 target=villager dist=2.45 combateActivo=true navSuspendido=false
        planner={plan=retry-delay, waypoint=-, stalled=0, retry=38} executorMayMove=false
t=2620 targetDist=1.95 combateActivo=true navSuspendido=false
        planner={plan=idle, waypoint=-} executorMayMove=true
t=2660 targetDist=0.95 combateActivo=true planner={plan=idle, waypoint=-}
```

The strategic executor can be suspended at >2 blocks when combat owns movement; the legacy planner shows `retry-delay`/`idle` but continues to be driven by `PursueTargetGoal`. This run produced effective movement and entry.

## Stall reproduction result

**Not reproduced.** Phase-7’s stationary interval at `710,59,469` did not recur. This run’s IDX8 engaged a villager at close range, entered, and died.

## Classification

- **Case E — stall not reproduced.** Phase-7 cause remains unproven.
- Case A/B/C/D cannot be selected: no persistent ownership trace at the outcrop was captured.
- The source mechanism (combat MOVE ownership suppresses strategic executor) is confirmed, but not causally tied to the Phase-7 stall.

## Candidate minimal fix

**None implemented.** Do not change goal priority or executor suspension from this evidence. The next diagnostic must catch the actual outcrop stall with the expanded probe (legacy planner + suspension) active for 9000 ticks; this run is one non-reproduction attempt.

## Original worlds

Unchanged:
- A2: 15,966,366 / `BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7`
- C4: 16,044,629 / `11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF`

## Tests

```text
gradlew test: PASS
lint_aldea.py --strict: OK
git diff --check: PASS
```

## Next action

Run another 9000-tick diagnostic with the expanded probe in a **new** disposable world until the eastern-outcrop stall is captured, or conclude it is non-deterministic after a documented number of attempts. Do not implement a navigation fix until the stall trace shows ownership, path state and absence of effective movement.
