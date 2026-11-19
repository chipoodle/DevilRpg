# A2 Phase 55-R2 — static validation after compile fix (2026-11-19)

## 1. Result

**PASS — static only.** Production `11ff43cd338d7a6222a146a2ed642a5546fb2910` compiles, the full test
suite passes, and `NAV_PLANNER_STATE_TEST` executed with **54** assertions (51 source call sites; one
in-loop assertion prints 4 times) ending `OK`.

- BRANCH: `feature/autonomous-village`
- INITIAL_HEAD: `b1a40291a8d92e81b1e61279531167728d8a6106`
- FINAL_HEAD at start: `7f823e6cac852906f7fc887818920b182d9b2fe0`
- TESTED_SHA: `11ff43cd338d7a6222a146a2ed642a5546fb2910`
- PRODUCTION_MODIFIED: false
- TESTS_MODIFIED: false
- WORLDS_MODIFIED: false

## 2. Git / correction diff

```text
Fast-forward only from b1a40291 to 7f823e6c (6 commits).
Production diff 11ff43cd..HEAD (src/build.gradle): empty.
```

`11ff43cd` changes exactly four lines in `NavPlanner.java`:

```java
BlockPos state -> BlockPos routeState
routePosition(state, ...) -> routePosition(routeState, ...)
```

in both route-reconstruction loops. No other production change.

## 3. Static commands

```text
.\gradlew.bat compileJava -Pa2nDiagnostics=true: PASS
.\gradlew.bat test: PASS
python tools\lint_aldea.py --strict: PASS
git diff --check: PASS
```

## 4. NAV_PLANNER_STATE_TEST

Output:

```text
  ok: escalada con hueco y soporte
  ...
  ok: A* incremental inicia pendiente
  ok: A* incremental no publica resultado parcial
  ok: slice incremental respeta una expansion   (x4, loop)
  ok: A* incremental completa dentro del presupuesto
  ok: A* incremental conserva la ruta s?ncrona
  ok: A* incremental reconstruye todos los pasos desde el origen
  ok: A* incremental requiri? varios slices
  ok: A* incremental conserva el presupuesto total duro
NAV_PLANNER_STATE_TEST: OK
```

```text
ASSERTIONS_EXECUTED: 54
```

The source has 51 `comprobar(...)` call sites; the slice-respect assertion is inside a loop and runs four
times, producing 54 `ok:` lines.

## 5. Invariants confirmed by source

```text
SLICE_EXPANSIONS: 4
SLICE_MAX_OPEN_POPS: 8  (advance(4) -> 4*2)
GLOBAL_TOTAL_BUDGET_CHANGED: no
```

- Session retains OPEN/CLOSED/bestG/predecessor maps.
- `result()` throws while pending; partial results are not routes.
- `NavExecutor` retains the handle and stays in planning while pending.
- Mission/invalidation paths call `cancelGlobalSearch`.
- Synchronous callers use `handle == null` and complete.
- No collision/support/protection/cost/heuristic/radio/vertical-window changes in the diff.

## 6. Runtime

None. No Minecraft, A2 Run1, C4, Run2 or Full12K was executed.

## 7. Integrity

```text
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

## 8. Next

The incremental A* is statically validated. Any runtime test requires new explicit authorization.
A2 remains OPEN.
