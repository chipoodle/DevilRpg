# A2 Run 1 — DROP take-off recovery FAIL (2026-11-16)

## Result

**FAIL.** Static validation passes, but IDX4 remains below the DROP landing; no take-off recovery is
executed. A2 Run 1 reaches 4/8 strict.

- Tested SHA: `6d294c1d8d4c82bc8b706e412ff87d5862b8901e`
- Confirmed commits: `199d905d`, `88182bcc`, `21f31149`, `de0ff4d5`
- Run: `a2n-20261116-201759`
- Seed 12345, FaseA 3000, FaseB 6000, budgets 900/8000.

## Static validation

| Command | Exit | Result | Log |
|---|---:|---|---|
| `gradlew test` | 0 | PASS | `build/phase19-droptakeoff-gradle.log` |
| `lint_aldea.py --strict` | 0 | PASS | `build/phase19-droptakeoff-lint.log` |
| `git diff --check` | 0 | PASS | terminal |
| `compileJava -Pa2nDiagnostics=true` | 0 | PASS | `build/phase19-droptakeoff-diagcompile.log` |

`NAV_DROP_TAKEOFF_POLICY_TEST: OK (5 assertions)` confirmed.

## A2 Run 1

- 8/8 spawned.
- **5/8 perimeter.**
- **4/8 strict** (`DENTRO-PARED`).
- C4 **PASS** `[1,0,0]`, 12/12 resumed.
- Full 12k not run.

| idx | Perimeter | Strict | Result |
|---|---:|---:|---|
| 1 | t387 | **t512** | entered |
| 2 | t242 | — | perimeter only; final `659,71,596`, alive |
| 3 | t871 | **t1064** | entered |
| 4 | — | — | below DROP landing |
| 5 | t454 | **t757** | entered |
| 6 | t224 | **t649** | entered |
| 7 | — | — | BUILD local budget |
| 8 | — | — | no local waypoint |

## IDX4 — required evidence

UUID `3812e691-634b-4e90-a24f-6fe29809f858`.

```text
PLANNED_ORIGIN: not available at execution; routeIndex=0 -> origenPlan=null
LANDING:        597,78,499 (DROP node)
MOB POSITION:   596,77,498 (one block below)
RECOVERY_COMMAND: ACERCARSE-ORIGEN-DROP NOT EMITTED
PHYSICAL_ORIGIN_REACHED: false
DROP_RESULT: rejected, reason={el aterrizaje esta por ENCIMA (dy=1)}
fallosDeDescenso: 24
NAV_FAIL: {replans agotados (8/8)}
```

The route DROP is `idxRuta=0/5`. In `NavExecutor`, `origenPlan` is computed only when
`routeIndex > 0`; at index 0 it is null. Therefore the new physical take-off check is never evaluated
and no `ACERCARSE-ORIGEN-DROP` command is issued. The mob stays at y77 below the y78 landing.

**Exact condition for ChatGPT:** the route starts with a DROP node. The take-off recovery requires a
predecessor (`routeIndex > 0 && routeIndex < route.size`), so the first DROP has no origin and bypasses
`NavDropTransitionPolicy.origenFisicamenteAlcanzado`. The mob cannot recover upward.

This is not the previous `Step.feet` node-height issue; the node and landing are both y78. It is the
missing first-node take-off origin/recovery path.

## IDX7

UUID `988ee86d-6292-4f9a-bcc2-bfdd1f68b2fd`, pos `705,65,459`.

```text
BUILD target 703,68,457; buildable=true; local=1/64
BUDGET exp=901/900; 10/10; no progress
NAV_FAIL {presupuesto agotado sin progreso (10/10)}
```

Unchanged local expansion/BUILD failure.

## IDX8

UUID `ec22260c-f511-44ad-82a6-06776ca3a578`, pos `769,63,497`.

```text
NAV_FAIL {sin objetivo local valido (10/10):
 ruta global sin waypoint de pie VALIDO ... [intento 10/10]}
rearmes=4/4; global exp=8000/8000; rutaFrente=81
```

Unchanged global/local waypoint failure.

## C4

- PASS `[1,0,0]`.
- 12/12 resumed; no casualties/unresolved workers.
- Save/reload OPEN.

## Gate

- Consecutive 8/8 strict regressions: **0**.
- Run 1 FAIL; Run 2 not executed.
- A2 remains OPEN.

## Artifacts

- `build/a2n/a2n-20261116-201759/srv.log`
- `build/a2n/a2n-20261116-201759/nav-trace.log`
- `build/a2n/a2n-20261116-201759/fin.txt`
- `build/a2n/a2n-20261116-201759/estado.txt`
- `build/phase19-droptakeoff-gradle.log`, `-lint.log`, `-diagcompile.log`

Original A2/C4 worlds untouched; local-only artifacts preserved.

## Next action

ChatGPT should make the first DROP node (`routeIndex=0`) carry a recoverable planned origin/take-off,
or otherwise apply the physical take-off recovery without requiring a predecessor. Do not weaken
landing height checks, budgets or acceptance. IDX7 and IDX8 remain separate blockers.
