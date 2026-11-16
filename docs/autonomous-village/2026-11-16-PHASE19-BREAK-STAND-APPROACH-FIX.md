# Phase19 — BREAK adjacent stand approach correction

Date: 2026-11-16

## Evidence and classification

Harness run `a2n-20261116-163239` is a behavioral **FAIL**. Both planner routes contain a valid BREAK edge and the LOS ray hits the intended glass pane. The mobs remain at `696/736,184,568` while the selected physical stands are `696/736,184,567`.

The recorded command repeatedly returns:

```text
pathNull=false pathCanReach=true pathDone=true
pathNodes=1 pathIndex=1 distSqr=1.2500
```

No physical displacement occurs, so exact stand occupancy is never achieved; the actuator is never invoked. This is not a LOS, reach, protection, or planner failure.

## Root cause

`NavExecutor.ejecutarBreakDesdePosicionDeAtaque` issued `PathNavigation.moveTo` toward a horizontally adjacent stand at the same feet Y. In this geometry vanilla returns a one-node path whose next-node index is already at the end. Reissuing the same completed path does not cause movement, even though the mob is not in the stand cell.

The repository already has a bounded adjacent-cell movement fallback, `NavAdjacentApproachPolicy` plus `MoveControl.setWantedPosition`, used for ordinary movement nodes. The BREAK stand branch did not use it.

## Production correction

Commit: `11be611d90c4d65772813550c2a365a524ee5147`

File: `src/main/java/com/chipoodle/devilrpg/entity/nav/NavExecutor.java`

When the mob is not physically in `standRuta`, the executor still issues the normal `moveTo` and traces `BREAK_STAND_APPROACH`. If vanilla navigation is already done, it now requests the existing adjacent nudge only when all existing bounded guards pass:

- navigation is done;
- stand is a non-zero Manhattan distance no greater than 2;
- the mob AABB fits at the stand;
- the stand has support.

The fallback uses `MoveControl.setWantedPosition` toward the validated stand center at the existing speed 1.0. It emits `BREAK_STAND_NUDGE` with origin, stand, distance and reason. Exact physical occupancy remains mandatory before BREAK execution; no proximity shortcut was introduced.

No reach, LOS, protection, collision, retry cooldown, planner budget, recovery budget, or acceptance threshold was changed. The 40-tick actuator retry semantics remain unchanged.

## Validation status

**PENDING HARNESS.** ChatGPT published the source correction and documentation but did not execute Gradle, lint, diagnostics compilation, or Minecraft runtime. The previous Harness PASS static checks apply to SHA `76d3466c`, not this correction.

Harness must fetch and validate the exact branch HEAD containing commit `11be611d90c4d65772813550c2a365a524ee5147`.

Required static checks:

```powershell
.\gradlew.bat test
python tools/lint_aldea.py --strict
git diff --check
.\gradlew.bat compileJava -Pa2nDiagnostics=true
```

Required disposable runtime:

```text
-Da2n.mode=break-runtime
-Ddevilrpg.a2n.traceNav=true
-Da2n.run=<unique-run-id>
```

Capture `BREAK_STAND_APPROACH`, `BREAK_STAND_NUDGE`, physical positions over time, and verify both mobs actually occupy their exact stands. Then validate:

1. both BREAK targets become air;
2. `BREAK_EXECUTED_FROM_STAND` appears for both scenarios;
3. retry scenario invokes its second attempt exactly 40 ticks after the first unverified attempt and records `BREAK_ACTUATOR_RETRY_WAIT`;
4. `BREAK_FIXTURE_MISSION_RESUMED` occurs after verified destruction.

If no nudge is emitted, return exact values for navigationDone, Manhattan distance, fits, support and position. If nudge is emitted but there is no displacement, capture MoveControl wanted position and per-tick coordinates. Do not weaken exact stand occupancy.

## A2 / C4 status

- A2 remains OPEN; the break-runtime fixture is not an A2 siege acceptance run.
- Five consecutive 8/8 strict `DENTRO-PARED` runs remain the gate before any full 12k run.
- C4 save/reload remains OPEN; latest known focused C4 result before this correction was PASS `[1,0,0]`.
- Original A2/C4 worlds and all local Harness artifacts must remain untouched.
- Harness owns synchronization, static/runtime validation, logs and report publication; Harness must not edit production source.
