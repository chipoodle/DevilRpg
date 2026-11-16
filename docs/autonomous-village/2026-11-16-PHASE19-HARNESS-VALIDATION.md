# Phase 19 — Harness validation (2026-11-16)

## Result

**VALIDATION PASS; RUNTIME BREAK BLOCKED.**

- Source/static validation passed on the correct tree.
- No `BREAK_STAND_APPROACH`, `BREAK_EXECUTED_FROM_STAND`, `BREAK_EDGE_EXCLUDED` or `NAV_BACKTRACK`
  event occurred in the focused run.
- A2 remains OPEN.

## Git synchronization

| Item | Value |
|---|---|
| Branch | `feature/autonomous-village` |
| Local before | `14df0115a7567979465210bed5dd338cf7ff0575` |
| Requested remote | `ecdfaff690824607c8e7f0189a356f1fe030b229` |
| Requested code | `f43eb11eab618ed1e19f8b795c1663bc0c619331` |
| Code is ancestor of remote | yes |
| Local/remote before | 0 ahead, 2 behind |
| Sync | `git merge --ff-only origin/feature/autonomous-village` → fast-forward |
| Local after | `ecdfaff690824607c8e7f0189a356f1fe030b229` |
| Remote after | same |
| Working tree | only preserved untracked artifacts |

## Required commands (HEAD `ecdfaff6`)

| Command | Time | Exit | Result | Log |
|---|---|---:|---|---|
| `.\gradlew.bat test` | 2026-11-16 11:22–11:24 | 0 | PASS | `build/phase19-harness-gradle.log` |
| `python tools/lint_aldea.py --strict` | 2026-11-16 11:24 | 0 | PASS | `build/phase19-harness-lint.log` |
| `git diff --check` | 2026-11-16 11:24 | 0 | PASS | terminal |

No process was interrupted; Gradle reported `BUILD SUCCESSFUL`.

## Specific tests

Observed in the Gradle log:

```text
NAV_BREAK_CONTRACT_TEST: OK (5 assertions)
NAV_BREAK_ATTACK_POSITION_POLICY_TEST: OK (7 assertions)
NAV_BREAK_ACTION_POLICY_TEST: OK (8 assertions)
NAV_BREAK_EXCLUSION_POLICY_TEST: OK (6 assertions)
NAV_RECOVERY_CONTROLLER_TEST: OK (7 assertions)
```

`NavBreakAttackPositionPolicyTest` covers the seven required cases: cardinal, diagonal, same cell,
two-cell, vertical mismatch, feet-height mismatch, and predecessor exclusion.

## Source review

Confirmed:

- `NavRoute.Node.pos()` / `standPos()` is the traversable feet cell.
- `Node.actionPos()` is the solid BREAK target.
- `NavPlanner.validarArista` requires a stand found by `NavBreakAttackPositionPolicy`, validates
  single-step reach at the same feet Y, diagonal side clearance, protection, support/fit/LOS via
  `NavBreakActionPolicy.executable`, and excludes failed `(stand,target)` edges.
- `NavExecutor.ejecutarBreakDesdePosicionDeAtaque` now uses `target = node.actionTarget()` and
  navigates only to `node.standPos()`; it does not call `moveTo` on the solid target.
- It revalidates permissions/reach/LOS, invokes the existing recovery action, and verifies the target
  is air before `resolverBreakVerificado` clears route/caches and replans.
- First unverified invocation waits `RECOVERY_COOLDOWN_TICKS` (40); second failure excludes the edge.
- Backtrack uses `NavRecoveryController` (max 16 history entries), two-failure gate, one-shot protection.
- C4 criteria unchanged.

No source discrepancy was found in this review.

## Runtime

Run **`a2n-20261116-112413`** — DIAGNOSTIC, NOT ACCEPTANCE.

- Seed 12345, FaseA 3000, FaseB 6000, budgets 900/8000.
- 8/8 spawned.
- **6/8 perimeter.**
- **5/8 strict `DENTRO-PARED`:** idx1 t513, idx2 t668, idx3 t1065, idx5 t695, idx6 t650.
- idx4 perimeter-only; idx7/idx8 outside.
- C4 **PASS** `[1,0,0]`, 12/12 resumed.

### Missing runtime events

Searched `srv.log` and `nav-trace.log`:

- `BREAK_STAND_APPROACH`: **not observed**
- `BREAK_EXECUTED_FROM_STAND`: **not observed**
- `BREAK_EDGE_EXCLUDED`: **not observed**
- `NAV_BACKTRACK`: **not observed**
- 40-tick retry: **not observed**
- strategic resumption after backtrack: **not observed**

Therefore direct runtime BREAK execution, retry, alternative-edge selection, backtracking and
strategic resumption remain **BLOCKED / unproven**. No PASS is claimed from policy tests.

## Planner integration fixture

No real/mock `CollisionGetter` + `Mob` integration fixture exists. Missing scenarios:
- BREAKABLE with valid cardinal stand,
- diagonal stand with orthogonal clearance,
- stand blocked by collision,
- stand without support,
- protected stand,
- target out of reach,
- LOS obstructed,
- failed-edge exclusion,
- implicit position-jump rejection.

Not implemented in this Harness task.

## A2 / C4

- A2: **OPEN**. Strict gate = five consecutive runs with 8/8 `DENTRO-PARED`; not met. Full 12k not run.
- C4: PASS in focused run; historical contract unchanged. Save/reload OPEN.

## Worlds

Original worlds unchanged:
- A2 `BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7`
- C4 `11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF`

New disposable world/run: `build/a2n/a2n-20261116-112413` (not an original diagnostic world).

## Local artifacts

Preserved:
- `.a2-backups/`
- `src/foodTest/`
- `tools/a2n/A2NavHarness.java.partial`
- launcher helper scripts
- `tools/a5-closure/`

## Next action

Create a deterministic runtime fixture that forces a route BREAK edge (valid stand) and records
`BREAK_STAND_APPROACH` → target air → route advance; then force one actuator false return and observe the
40-tick retry, followed by two no-progress failures to observe `NAV_BACKTRACK` and mission resumption.
Do not change budgets or acceptance to produce the events.


---

## Addendum — ChatGPT source correction after the BLOCKED runtime report

**This addendum does not change the original run result.** Run `a2n-20261116-112413` remains BLOCKED for runtime BREAK; the original measurements and missing-event observations remain valid.

ChatGPT reviewed the actual source and identified that the BREAK route could be unreachable or skipped even when policy checks passed:

1. A* keyed BREAK's successor by `standPos`, so a cheaper WALK edge to the same stand could dominate the BREAK edge despite not clearing the target.
2. `nodoAlcanzado` could advance a BREAK node by proximity before the actuator ran.
3. The actuator's old distance threshold could accept an adjacent predecessor cell as if it occupied the exact attack stand.
4. A global BREAK waypoint's `actionPos` could be lost when local planning created a WALK-only route to the stand.

ChatGPT published corrections and a deterministic runtime fixture in code checkpoint:

`1a7028abc138f5b53d1313155dee23fceb20ed78`

New tests authored and registered (not yet run):
- `NavBreakGraphStateTest` — 7 assertions.
- `NavBreakRoutePolicyTest` — 10 assertions.

New runtime mode:
`A2NavHarness` with `-Da2n.mode=break-runtime`, compiled with `-Pa2nDiagnostics=true` and launched with `-Ddevilrpg.a2n.traceNav=true` in a new disposable world. It exercises a production-actuator success path and a controlled first-failure/second-success path whose retry must be exactly 40 ticks.

The source changes, fixture design, expected trace events, and exact Harness instructions are documented in:
`2026-11-16-PHASE19-BREAK-GRAPH-STATE-FIX.md`.

**Validation state:** no Gradle/lint/runtime command has been run by ChatGPT on `600b78b4`. Harness must fetch the latest branch, verify the exact code checkpoint, run the required commands and execute the new fixture. Edge-exclusion and `NAV_BACKTRACK` still require separate runtime coverage.


### Additional correction — generic recovery target

A subsequent review found one remaining generic recovery path that still passed `actual.pos()` to the BREAK actuator. Since `pos()` is the stand, that path could act on the wrong block. It now passes `actual.actionTarget()` and verifies the actual target state. The graph search also rejects malformed BREAK edges that do not carry both stand and action positions. These changes are included in code checkpoint `1a7028abc138f5b53d1313155dee23fceb20ed78` and are also pending Harness validation.
