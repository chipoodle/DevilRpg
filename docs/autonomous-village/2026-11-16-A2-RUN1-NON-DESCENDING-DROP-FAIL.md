# A2 Run 1 — non-descending DROP fix FAIL (2026-11-16)

## Result

**FAIL.** Static validation passes, but IDX4 still executes the same invalid DROP and A2 Run 1 remains
5/8 strict.

- Tested SHA: `2214366a9e82bb365e785c99fca61c754a68e0a1`
- Run: `a2n-20261116-211919`
- Seed 12345, FaseA 3000, FaseB 6000, budgets 900/8000.

## Static validation

| Command | Exit | Result | Log |
|---|---:|---|---|
| `gradlew test` | 0 | PASS | `build/phase19-nondescending-gradle.log` |
| `lint_aldea.py --strict` | 0 | PASS | `build/phase19-nondescending-lint.log` |
| `git diff --check` | 0 | PASS | terminal |
| `compileJava -Pa2nDiagnostics=true` | 0 | PASS | `build/phase19-nondescending-diagcompile.log` |

```text
NAV_DROP_TAKEOFF_POLICY_TEST: OK (9 assertions)
NAV_ROUTE_NODE_POLICY_TEST: OK (6 assertions)
```

## A2 Run 1

- 8/8 spawned.
- **5/8 perimeter.**
- **5/8 strict `DENTRO-PARED`.**
- C4 **PASS** `[1,0,0]`, 12/12 resumed.
- Run 2 not executed; 12k not executed.

| idx | Perimeter | Strict |
|---|---:|---:|
| 1 | t387 | **t512** |
| 2 | t242 | **t667** |
| 3 | t871 | **t1066** |
| 4 | — | — |
| 5 | t454 | **t757** |
| 6 | t224 | **t649** |
| 7 | — | — |
| 8 | — | — |

## IDX4

UUID `3a3db29b-ceb9-43b7-97e9-1f9df71f626b`.

```text
pos=596,77,498
ultimaAccionDeRuta=597,78,499:DROP-RECHAZADO
  (el aterrizaje esta por ENCIMA (dy=1))
fallosDeDescenso=24
NAV_FAIL={replans agotados (8/8)}
```

No `DROP_REJECTED_NON_DESCENDING` event occurred for IDX4. No `ACERCARSE-ORIGEN-DROP` command was
emitted for this UUID. The same route/landing and `dy=1` rejection persist.

The route still begins with the DROP at index 0, so the take-off recovery path that requires a
predecessor is not reached. The non-descending planner correction does not provide an executable
alternative for this route.

## IDX7

UUID `0689ad51-4a24-4ae1-81b1-c759924f924c`.

```text
pos=705,65,459; BUILD 703,68,457
BUDGET exp=901/900; no progress; NAV_FAIL
```

Unchanged local 900-expansion/BUILD failure.

## IDX8

UUID `a3530d78-d8ed-4208-9198-ea742420bd66`.

```text
pos=769,63,497
NAV_FAIL {sin objetivo local valido (10/10):
 ruta global sin waypoint de pie VALIDO ... [intento 10/10]}
rearmes=4/4; global exp=8000/8000; rutaFrente=81
```

## C4

- PASS `[1,0,0]`.
- 12/12 resumed; no casualties/unresolved workers.
- Save/reload OPEN.

## Gate

- Consecutive 8/8 strict runs: **0**.
- Run 1 FAIL; no Run 2.
- A2 remains OPEN.

## Artifacts

- `build/a2n/a2n-20261116-211919/srv.log`
- `build/a2n/a2n-20261116-211919/nav-trace.log`
- `build/a2n/a2n-20261116-211919/fin.txt`
- `build/a2n/a2n-20261116-211919/estado.txt`
- `build/phase19-nondescending-gradle.log`, `-lint.log`, `-diagcompile.log`

Original worlds untouched; artifacts preserved.

## Next action

ChatGPT must make the IDX4 first-node DROP route fail at planning time or produce a physically
executable alternative. The runtime route still starts with DROP at `597,78,499`, and the mob at
`596,77,498` receives `dy=1`; no non-descending rejection/recovery event is emitted for IDX4. Do not
weaken the height guard, budgets or entry criteria.
