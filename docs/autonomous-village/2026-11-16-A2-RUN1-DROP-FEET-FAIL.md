# A2 Run 1 — DROP feet correction revalidation (2026-11-16)

## Result

**FAIL.** The DROP-feet correction passes static validation, but A2 Run 1 remains 5/8 strict. The
IDX4 DROP outcome is unchanged.

- Tested SHA: `5604ed03abcbe08b972f3c8b8bf7984e101b12e9`
- Source commits confirmed: `38a15bb7`, `cdf4c9f5`, `b19f413c`
- Run: `a2n-20261116-195651`
- Seed 12345, FaseA 3000, FaseB 6000, budgets 900/8000

## Static validation

| Command | Exit | Result | Log |
|---|---:|---|---|
| `gradlew test` | 0 | PASS | `build/phase19-dropfix-gradle.log` |
| `lint_aldea.py --strict` | 0 | PASS | `build/phase19-dropfix-lint.log` |
| `git diff --check` | 0 | PASS | terminal |
| `compileJava -Pa2nDiagnostics=true` | 0 | PASS | `build/phase19-dropfix-diagcompile.log` |

`NavRouteNodePolicyTest: OK (6 assertions)` confirmed; `NavArrivalPolicyTest` and recovery tests also
passed.

## A2 Run 1

- 8/8 spawned.
- **5/8 perimeter.**
- **5/8 strict `DENTRO-PARED`.**
- C4 **PASS** `[1,0,0]`, 12/12 resumed.
- Full 12k not run.

| idx | Perimeter | Strict | Terminal |
|---|---:|---:|---|
| 1 | t387 | **t513** | entered |
| 2 | t242 | **t668** | entered |
| 3 | t869 | **t1063** | entered |
| 4 | — | — | NAV_FAIL t? `replans agotados (8/8)` |
| 5 | t434 | **t701** | entered |
| 6 | t224 | **t650** | entered |
| 7 | — | — | `exp=901/900` 10/10, BUILD |
| 8 | — | — | no valid local waypoint, global 8000/8000 |

## IDX4 — DROP unchanged

UUID `eb885cfd-5b8a-4fb8-9e44-b109ab0c8cc2`; position `596,77,498`.

```text
DROP node: 597,78,499
takeoff:   597,77,499
dy=1
reason={el aterrizaje esta por ENCIMA (dy=1)}
fallosDeDescenso=24
NAV_FAIL motivo={replans agotados (8/8)}
```

The route node remains `597,78,499` after the “feet” correction. The mob is one block **below** the
landing (y77 vs y78); this is not the earlier “state vs feet node height” symptom. The repeated DROP
is rejected because the mob cannot ascend to its own landing. The exact recovery failure (why the mob
stays below and cannot regain the landing) is **not established** by this trace.

The earlier hypothesis that the node height was wrong is **not confirmed for this instance**: the
landing really is y78; the mob is below it.

## IDX7 — unchanged BUILD budget

UUID `5c2da693-6d8f-4b13-88ae-db1171bb227d`, pos `705,65,459`.

```text
BUDGET intento 9/10 exp=901/900 progreso=false
BUDGET intento 10/10 exp=901/900 progreso=false TERMINAL
NAV_FAIL motivo={presupuesto agotado sin progreso (10/10) hacia 703,68,457}
```

Unchanged: local expansion budget exhausted at 900 on all attempts.

## IDX8 — unchanged local-waypoint failure

UUID `679224c2-997f-4f0a-b3ac-61072f19ac45`, pos `769,63,497`.

```text
NAV_FAIL motivo={sin objetivo local valido (10/10):
 ruta global sin waypoint de pie VALIDO dentro de la ventana desde 769,63,497
 (habia waypoints pero el mundo cambio) [intento 10/10]}
rearmes=4/4
global exp=8000/8000
```

## Gate

- Consecutive 8/8 strict runs: **0**.
- Run 1 is FAIL; no Run 2.
- A2 remains OPEN.
- The DROP-feet correction is statically validated but has **not** improved the runtime gate.

## Artifacts

- `build/a2n/a2n-20261116-195651/srv.log`
- `build/a2n/a2n-20261116-195651/nav-trace.log`
- `build/a2n/a2n-20261116-195651/fin.txt`
- `build/a2n/a2n-20261116-195651/estado.txt`
- `build/phase19-dropfix-gradle.log`, `-lint.log`, `-diagcompile.log`

Original worlds untouched. Local-only artifacts preserved.

## Next action

ChatGPT should treat IDX4 as a separate recovery defect: the mob is below the DROP landing and every
DROP attempt is rejected with `dy=1`; it never regains the landing height. IDX7/IDX8 terminal modes
remain independent. Do not weaken gates, ranges or budgets.
