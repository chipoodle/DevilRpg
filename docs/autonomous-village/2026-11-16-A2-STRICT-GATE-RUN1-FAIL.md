# A2 Strict Siege Gate — Run 1 FAIL (2026-11-16)

## Result

```text
RESULT: FAIL
SHA: 2d47d628724a677269e1c3d895759154f56a52c9
A2_GATE: FAIL (run 1)
CONSECUTIVE_8_OF_8: 0
RUN_IDS: a2n-20261116-190555
FIRST_FAILURE: idx8 terminal NAV_FAIL tick=3411; idx4/7/8 never strictly inside
ROOT_CAUSE: three distinct terminal navigation failures, not one shared cause
NEXT_ACTION: ChatGPT to address the reproduced terminal modes; no production change by Harness
```

- Seed 12345, FaseA 3000, FaseB 6000, budgets 900/8000, custom navigation.
- 8/8 spawned.
- **5/8 perimeter.**
- **5/8 strict `DENTRO-PARED`.**
- C4 **PASS** `[2,0,0]`.
- Full 12k not run.

## Per-attacker result

| idx | Perimeter | Strict entry | Terminal |
|---|---:|---:|---|
| 1 | t411 | **t558** | entered |
| 2 | t242 | **t667** | entered |
| 3 | t869 | **t1062** | entered |
| 4 | — | — | NAV_FAIL t5147, `replans agotados (8/8)` |
| 5 | t434 | **t702** | entered |
| 6 | t224 | **t649** | entered |
| 7 | — | — | NAV_FAIL t4209, `presupuesto agotado sin progreso (10/10)` |
| 8 | — | — | NAV_FAIL t3411, `sin objetivo local valido (10/10)` |

Survivors at closure: idx4 `596,77,498`; idx7 `705,65,459`; idx8 `769,63,497`.

## Terminal evidence

### IDX8 — first terminal failure

```text
A2_FALLAR_ENTER tick=3411 local=0/64 fallos=10/10
motivo={sin objetivo local valido (10/10):
 ruta global sin waypoint de pie VALIDO dentro de la ventana desde 769, 63, 497
 (habia waypoints pero el mundo cambio) -> se invalida la ruta global para recalcularla [intento 10/10]}
NAV_FAIL ... rearmes=4/4
```

Global: `exp=8000/8000 presupuesto=true`, `frente=690,45,510`, `rutaFrente=81`.
Local objective `661,72,533`; `escalones=0/64`, 10/10 local failures.

### IDX7

```text
A2_FALLAR_ENTER tick=4209 local=1/64 fallos=0/10
motivo={presupuesto agotado sin progreso (10/10) hacia 703, 68, 457}
NAV_FAIL ... rearmes=0/4
```

Local objective `703,68,457`, transition BUILD, `expGlobal=8749`, route 79.
Position `705,65,459`.

### IDX4

```text
A2_FALLAR_ENTER tick=5147 local=1/64 fallos=0/10
motivo={replans agotados (8/8)}
NAV_FAIL ... rearmes=1/4
```

Position `596,77,498`; `RECOVERING`, `fallosDeDescenso=24`, last DROP rejected because the landing is
above (`dy=1`); global budget `exp=8000/8000 presupuesto=true`; local objective `595,82,503`
(STEP_UP).

## Static validation

```text
gradlew test: PASS
lint_aldea.py --strict: OK
git diff --check: PASS
```

Executed on `2d47d628`. No production changes.

## C4

- PASS `[2,0,0]`.
- 12/12 resumed, no casualties/unresolved workers.
- Save/reload remains OPEN.

## Gate

- Run 1: FAIL (5/8 strict).
- Consecutive 8/8 runs: **0**.
- No further runs executed because the instruction requires stopping at first failure.
- Full 12k blocked.

## Diagnosis

The failure is not a single regression. Three independent terminal modes remain:

1. **IDX8**: global route reaches its expansion budget and local selection finds no valid standing
   waypoint after world change; four terminal rearm attempts exhausted.
2. **IDX7**: stable route to BUILD objective but no progress budget exhausted; no terminal rearm used.
3. **IDX4**: repeated DROP rejections (`dy=1`, landing above) plus global budget exhaustion, then
   replan budget 8/8 exhausted.

All three attackers remained alive; this is navigation failure, not combat death.

## Artifacts

- `build/a2n/a2n-20261116-190555/srv.log`
- `build/a2n/a2n-20261116-190555/nav-trace.log`
- `build/a2n/a2n-20261116-190555/fin.txt`
- `build/a2n/a2n-20261116-190555/estado.txt`
- `build/phase19-retryv2-gradle.log` (static evidence from current tree)

Original A2/C4 worlds were not used or modified.

## Next action

ChatGPT should address the three reproduced terminal modes separately, without weakening entry
criteria, action ranges, budgets, collision or acceptance. After a new SHA, Harness reruns A2 Run 1;
only a 8/8 strict result permits Run 2 and the five-run gate.
