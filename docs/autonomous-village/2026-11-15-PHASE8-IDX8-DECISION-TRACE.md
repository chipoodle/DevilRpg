# Phase 8 — IDX8 Target / PLANNING / Waypoint Decision Trace (2026-11-15)

## Source call chain

1. **Target acquisition** — `AggressiveZombieEntity` receives a `LivingEntity` target (telemetry: `target=entity.minecraft.villager`).
2. **Combat goal** — `PursueTargetGoal` (priority **4**, flags MOVE+LOOK) `canUse` calls `AdvanceUnderFirePolicy.shouldChase(objetivoCampanaActivo(), distance²)`. After the bell is destroyed, `objetivoCampanaActivo()` is false, so a live target can pass `shouldChase` at any distance.
3. **Combat movement** — `PursueTargetGoal.tick` selects a destination and calls `zombie.pathPlanner.navigateTo(...)` (legacy planner), not `NavExecutor`.
4. **Village goal** — `MoveToVillageCenterGoal` (priority **7**) sends `navExecutor.setObjectiveChain(cadenaDeAproximacion(center))`.
5. **Ownership** — in `AggressiveZombieEntity.aiStep`:
   - `combate = pursueTargetGoal.estaActivo()`
   - `combateEnAlcance = combate && distance² <= MELEE_RANGE_SQR (4.0)`
   - `MovementOwnership.executorMayMove(true, combate, combateEnAlcance)`.
   - Distant combat target → `navExecutor.suspendForCombat()`; melee target → executor may move.
6. **NavExecutor** — `PLANNING`, path/waypoint validation, `BREAK` delegation/verification.

This confirms that a distant live target can legitimately suspend the strategic executor and let the combat planner own movement.

## Diagnostic instrumentation

- Read-only accessor `AggressiveZombieEntity.persiguiendoActivoA2()`.
- Harness probe `-Da2n.probeTargets=true` logs IDX4/IDX8 every 20 ticks:
  - tick, position, distance, state, objective, target type/UUID/distance,
  - `combateActivo`, `executorMayMove`, route action.
- No production behavior changed.

## Diagnostic run

- Run ID: `a2n-20261115-205715`
- Disposable world: `build/a2n/a2n-20261115-205715/game/a2n`
- Logs: `build/a2n/a2n-20261115-205715/srv.log`
- Marked **DIAGNOSTIC — NOT ACCEPTANCE**
- Result: 8/8 spawn, 7/8 perimeter, C4 12/12. **IDX8 entered enclosure at t=2347 and died in combat at t=2886.**

### IDX8 observed ownership

```text
t=2800 target=villager targetDist=1.43 combateActivo=true  executorMayMove=true
t=2820 target=villager targetDist=1.34 combateActivo=true  executorMayMove=true
t=2840 target=-        combateActivo=false executorMayMove=true
t=2880 target=villager targetDist=3.28 combateActivo=true  executorMayMove=false
```

The mechanism is real: at 3.28 blocks the executor is suspended. But the run did not exhibit the Phase-7 stall; IDX8 entered the village and died normally. Therefore this run cannot attribute the Phase-7 stall to combat ownership.

## Hypothesis evaluation

| Hypothesis | Evidence | Verdict |
|---|---|---|
| A. Combat target suppresses waypoint execution | Source path exists; `executorMayMove=false` observed at 3.28; but no eastern-outcrop stall in this run | **Supported mechanism, not proven cause** |
| B. PLANNING never produces executable path | Route had `BREAK-VERIFICADO`; no repeated path-failure telemetry at the outcrop | Not proven |
| C. Waypoint exists but movement suppressed by another controller | Combat goal owns MOVE by design; executor suspend observed only near target | **Competing, not isolated** |
| D. BREAK verification/obstruction blocks route | Route showed verified BREAK; no repeated failed verification in diagnostic run | Not proven |
| E. Other cause | Phase-7 stall not reproduced; run variance / geometry / target timing remain | **Possible** |

## Conclusion

**Multiple competing causes remain / insufficient evidence.** The source-level ownership mechanism is confirmed and is the leading explanation for a distant-target interaction, but the controlled run did not reproduce the Phase-7 eastern-outcrop stall. Do not implement a navigation fix from this evidence.

Smallest additional diagnostic: run a **9000-tick observation with the same ownership probe**, so the probe is active when the eastern-outcrop stall actually occurs; correlate `combateActivo`, `executorMayMove`, target distance, and waypoint/BREAK state at `710,59,469`. Use a new disposable world, not the original diagnostics.

## Original worlds

Verified unchanged:
- A2 `15,966,366` / `BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7`.
- C4 `16,044,629` / `11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF`.

## Tests

```text
gradlew test: PASS
lint_aldea.py --strict: OK
git diff --check: PASS
```

**This diagnostic run is not acceptance.**
