# Phase19 — BREAK LOS geometry correction and diagnostic handoff

**Date:** 2026-11-16  
**Branch:** `feature/autonomous-village`  
**Implementation checkpoint:** `6d5dc7b1d1f72a366d4f6b05626147b9e932481c`  
**Current source HEAD:** `9200ac31a350d9013b2abb8f39ce2b421fc9605a`  
**Status:** IMPLEMENTED / STATIC AND RUNTIME VALIDATION PENDING HARNESS

## 1. Previous Harness result

Harness run `a2n-20261116-160714` returned **INVALIDO**, not a production actuator FAIL:

- Static Gradle tests, strict lint, diff-check and diagnostics compilation passed on `d440b3e7`.
- Both planner routes were empty; `breakTargetFound=false`.
- Planner evaluated 400 edges, enqueued 9, expanded 10/512; this was not budget exhaustion.
- The classifier identified 39 BREAKABLE cells, while 42 candidate edges were rejected as `NODO_NO_PISABLE`.
- No mob or actuator started. No BREAK runtime behavior was validated.
- Full evidence remains in `2026-11-16-PHASE19-BREAK-RUNTIME-INVALID.md`; that historical report is preserved.

## 2. Source-level geometric finding

For the fixture's diagonal attack stand:

- stand: `(697,184,569)` relative to the first arena origin `(696,184,568)`
- target: `(697,184,568)`
- hypothetical eye: approximately `(697.5,185.62,569.5)`
- target center: `(697.5,184.5,568.5)`

The former near-voxel-face projection computes `tEnter=0.5` and `aimY=183.38`, below the target block's lower Y=184. The resulting ray endpoint was below the block. That projection assumed a full-cube near face and is unsuitable for the narrow central collision shape of a glass pane. The exact old clip hit was not recorded in the previous run, so the actual collision result remains to be measured.

## 3. Implementation published

ChatGPT modified production source directly on this branch:

- `NavBreakActionPolicy.executable` now raycasts from the eye to the target's interior center, allowing the clip to intersect the actual collision shape instead of projecting the endpoint below a thin pane.
- The former `tEnter`, `legacyAimY` and legacy endpoint are retained as diagnostic values only.
- Opt-in `NavTrace` event `BREAK_LOS_DIAG` records the rejection stage, stand, target, eye, distance, legacy geometry, new ray endpoint and actual clip hit type/block.
- `NavPlanner.buscarPosicionDeAtaque` now emits bounded `BREAK_STAND_CANDIDATE` events for candidate rejection reasons and accepted stands.
- The fixture now initializes its NavTrace sink before calling the planner, so planning-time LOS diagnostics are persisted.

Source commits in order:

- `1b8f6849e9247ced42899ea312c45f1bc8c5e058` — center-target ray and LOS diagnostics.
- `6dcc2eea5699fc376fd1c4d34392d7febc9bc6db` — initialize fixture trace before planning.
- `e1fcc3ecbd0f3394a68fe36b5cb41b442b714e06` — trace attack-stand candidate rejection.
- `9200ac31a350d9013b2abb8f39ce2b421fc9605a` — make legacy diagnostic endpoint safe for non-finite values.

No action reach, protection, cooldown, movement budget, collision rule, acceptance threshold or original world was changed.

## 4. Required Harness validation

Harness must fetch the latest branch HEAD and validate that exact SHA. Preserve local-only artifacts and use safe fast-forward only. Do not edit production source.

Run:

```powershell
.\gradlew.bat test
python tools/lint_aldea.py --strict
git diff --check
.\gradlew.bat compileJava -Pa2nDiagnostics=true
```

Run a new disposable world with:

```text
-Da2n.mode=break-runtime
-Ddevilrpg.a2n.traceNav=true
-Da2n.run=<unique-run-id>
```

Preserve `nav-trace.log`, complete server logs, `fin.txt`, `estado.txt`, route summaries and exact tested SHA.

Required diagnostic evidence:

- `BREAK_STAND_CANDIDATE` reasons for each attempted stand.
- `BREAK_LOS_DIAG` values including `legacyTEnter`, `legacyAimY`, `legacyRayEnd`, `rayEnd`, `hitType`, and `hitBlock`.
- Whether the two expected panes each produce a BREAK route node.
- If the planner still rejects the edge, identify the exact rejection stage. Do not infer the cause from aggregate `NODO_NO_PISABLE` counts.

If both routes are valid, continue the existing success and retry scenarios without changing their contract. Confirm both blocks become air, retry delta is exactly 40 ticks, and both strategic missions resume.

Classification:
- `COMPLETO`: all existing fixture acceptance conditions are met.
- `FAIL`: runtime executed and a behavioral acceptance condition failed.
- `BLOCKED`: environment or execution prevented valid completion.
- `INVALIDO`: fixture preconditions or expected planner edges are still invalid.

Do not mark the LOS correction as validated until Harness tests the exact new SHA.

## 5. Project status

- Phase19 graph-state source checkpoint remains implemented but runtime acceptance is pending.
- BREAK production actuator and retry are not yet validated.
- `BREAK_EDGE_EXCLUDED` and `NAV_BACKTRACK` still require separate runtime fixtures.
- A2 remains OPEN; last focused result is 5/8 strict and 6/8 perimeter.
- Do not run the 12k campaign until five consecutive 8/8 strict `DENTRO-PARED` runs.
- C4 save/reload remains OPEN.

## 6. Ownership

**Harness:** synchronize, execute, measure and document the exact source SHA. Do not implement production fixes.

**ChatGPT:** review Harness evidence and make any required production correction directly on GitHub in `feature/autonomous-village`.

## Harness validation result — 2026-11-16

- Static checks PASS on `76d3466c` (LOS source `9200ac31`): Gradle, strict lint, diff-check, diagnostics compile.
- `BREAK_LOS_DIAG` accepts both panes; `hitType=BLOCK`, `hitBlock=697/737,184,568`; both plans contain BREAK.
- `break-runtime` `a2n-20261116-163239`: **FAIL** — mobs stay at `696/736,184,568`; `moveTo` to `696/736,184,567` returns a one-node path marked done, no displacement, `retryCalls=0`; fixture timeout.
- LOS correction validated; stand approach is the current blocker. Report: `2026-11-16-PHASE19-BREAK-LOS-RUNTIME-FAIL.md`.
