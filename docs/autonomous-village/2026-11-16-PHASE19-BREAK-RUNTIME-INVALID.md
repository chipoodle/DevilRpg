# Phase19 BREAK graph-state runtime validation — INVALIDO (2026-11-16)

## Result

**INVALIDO** — the deterministic `break-runtime` fixture did not reach execution. The production
`NavPlanner` returned empty routes and `breakTargetFound=false` for both fixture mobs, so no
`AggressiveZombieEntity` was started and no BREAK actuator event could occur.

- Tested tree HEAD: `d440b3e70cbb32ccd2e23ed5798ba3bb1b425854`
- Implementation SHA under test: `6d5dc7b1d1f72a366d4f6b05626147b9e932481c` (ancestor)
- Branch: `feature/autonomous-village`
- Run: `a2n-20261116-160714`
- Classification: **DIAGNOSTIC — NOT ACCEPTANCE**

## Git synchronization

- Local before: `289406f6848bfe34e60f46a275319ac2b211fa3f`
- Remote: `d440b3e70cbb32ccd2e23ed5798ba3bb1b425854`
- Divergence before: 0 ahead / 58 behind.
- Only preserved untracked artifacts existed; `git merge --ff-only` succeeded.
- Final tested HEAD: `d440b3e7`.
- No reset/clean/stash/rebase/force-push.

## Static validation (HEAD d440b3e7)

| Command | Exit | Result | Log |
|---|---:|---|---|
| `.\gradlew.bat test` | 0 | PASS | `build/phase19-graph-gradle.log` |
| `python tools/lint_aldea.py --strict` | 0 | PASS | `build/phase19-graph-lint.log` |
| `git diff --check` | 0 | PASS | terminal |
| `.\gradlew.bat compileJava -Pa2nDiagnostics=true` | 0 | PASS | `build/phase19-graph-diagcompile.log` |

Executed policy outputs:

```text
NAV_BREAK_CONTRACT_TEST: OK (5 assertions)
NAV_BREAK_GRAPH_STATE_TEST: OK (7 assertions)
NAV_BREAK_ROUTE_POLICY_TEST: OK (11 assertions)
NAV_BREAK_ATTACK_POSITION_POLICY_TEST: OK (7 assertions)
NAV_BREAK_ACTION_POLICY_TEST: OK (8 assertions)
NAV_BREAK_EXCLUSION_POLICY_TEST: OK (6 assertions)
NAV_RECOVERY_CONTROLLER_TEST: OK (7 assertions)
```

## Runtime evidence

Fresh disposable world: `build/a2n/a2n-20261116-160714/game`.

`fin.txt`:

```text
INVALIDO (break-runtime) - planner did not produce both expected BREAK edges;
success={inicio=696, 184, 568 fin=701, 184, 568 H=12 V=2 exp=10/512 presupuesto=false
evaluados=400 encolados=9 metaEncolada=false alcanzada=false
rechazadasPorCaida=0 rechazadasPorDesnivel=160 rechazadasPorNodoNoPisable=42
PASSABLE=45 STEP_UP=125 JUMP_UP=125 BREAKABLE=39 BLOCKED=66}
retry={inicio=736, 184, 568 fin=741, 184, 568 ... same ...}
```

Key facts:

- Both starts are valid fixture cells: success `696,184,568`; retry `736,184,568`.
- Goals are `701,184,568` and `741,184,568`.
- The target panes are at `697,184,568` and `737,184,568`.
- `BREAKABLE=39` means the classifier saw breakable cells.
- `metaEncolada=false`, `alcanzada=false`, `route=vacia`, `breakTargetFound=false`.
- `encolados=9`, only `exp=10/512`; this is not budget exhaustion.
- `rechazadasPorNodoNoPisable=42` indicates candidate edges were rejected by the executable-stand
  contract rather than accepted as BREAK.

`estado.txt` is empty; `fin.txt` and `srv.log` are preserved. No
`BREAK_FIXTURE_READY`, `BREAK_STAND_APPROACH`, `BREAK_ACTUATOR_RETRY_WAIT`,
`BREAK_EXECUTED_FROM_STAND`, `BREAK_FIXTURE_RETRY_FIRST/SECOND` or
`BREAK_FIXTURE_MISSION_RESUMED` event exists.

## Candidate cause (NOT proven; return to ChatGPT)

The fixture cell adjacency is:

```text
stand:  x0,     y, z
target: x0 + 1, y, z
```

`NavBreakActionPolicy.executable` computes `tEnter` from the eye ray to the target's near face and
maps `aimY` through that parameter. For an adjacent target at the same feet Y, with a mob eye at
~feetY+1.6, that can place the ray end below the target center. The subsequent `level.clip` can then
hit the floor or another face instead of `target`, making LOS false and rejecting every candidate.
This is a **candidate** explanation only; no per-ray values were emitted by the fixture, so it is not
established as the root cause.

The fixture itself is also suspect: `containsBreakTarget` requires `node.pos().distManhattan(target) <= 2`
and the target must be a BLOCK; this check did not produce a route. Do not change production or the
fixture in this Harness task.

## What was not validated

Because the fixture is INVALIDO:

- Scenario A (production actuator): **NOT EXECUTED**
- Scenario B (40-tick retry): **NOT EXECUTED**
- `BREAK_EDGE_EXCLUDED`: **NOT EXECUTED**
- `NAV_BACKTRACK`: **NOT EXECUTED**
- Strategic mission resumption: **NOT EXECUTED**

No claim of BREAK runtime PASS.

## A2 / C4

- A2 remains OPEN; this fixture is not a siege run. Last known focused result remains 5/8 strict,
  6/8 perimeter (`a2n-20261116-112413`).
- C4 was not run in this fixture; last known focused C4 PASS `[1,0,0]`.
- Save/reload remains OPEN.

## Integrity

Original worlds unchanged:

```text
A2 SHA-256 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 SHA-256 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

New disposable world only under `build/a2n/a2n-20261116-160714`.

## Exact next action for ChatGPT

Instrument (in the fixture or a separate diagnostic) the exact values inside
`NavBreakActionPolicy.executable` for `stand=(696,184,568)`, `target=(697,184,568)`:

- `tEnter`, `eye`, `aimY`, `rayEnd`;
- `ClipContext` hit type and hit block position;
- `NavObstruction.fits(stand)`, `hasSupport(stand)`, `policies.canBreak(target)`, and the exact
  rejection branch.

Then correct either the fixture geometry or the LOS/aim computation, publish a new SHA, and Harness
will rerun `break-runtime` on the exact commit. Do not weaken action reach, budgets, protection,
collision or acceptance.
