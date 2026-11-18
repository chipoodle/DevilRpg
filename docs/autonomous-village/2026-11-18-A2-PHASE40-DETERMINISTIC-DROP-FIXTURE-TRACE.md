# A2 Phase 40 — deterministic DROP fixture and physical trace (2026-11-18)

## 1. Result

**PARTIALLY COMPLETE.**

- **IDX4-like: TRACE_COMPLETE.** The real entity reached DROP through the real planner/executor. Local
  plan `exp=1/900`, no global plan, no watchdog. The 40-tick trace proves Navigation reaches the edge but
  **MoveControl.hasWanted stays false**, Y never changes, and vertical collision keeps the mob on the
  platform.
- **IDX8-like: FIXTURE_NO_DROP_WITHIN_200_TICKS.** It never produced a plan (`plan=-` for 300 ticks),
  so it yields no DROP evidence. This is a fixture failure, not a DROP result.

- Branch: `feature/autonomous-village`
- HEAD: `ec0274abc3a802536b494afc7a22a8f2a06122f0`
- Production: `6aec35c651810084691f99611c48652fe0a881d7`
- Temporary fixture/probe reverted; production/tests unchanged.

## 2. Fixture

### IDX4-like

```text
spawn:    566.5, 122.0, 533.5
platform: spruce_leaves at y121 (landing column kept open)
landing:  567,119,534
support:  spruce_leaves at 567,118,534
clear:    564..570, 118..123, 531..537 (except support/platform)
```

Run: `a2n-20261118-134234`.

### IDX8-like

```text
spawn:    747.69, 64.0, 534.6
platform: cobblestone at y63, x747..748,z533..535
landing:  746,63,534
support:  ice at 746,62,534
```

Run: `a2n-20261118-134458`.

Preparation only set blocks/spawn/objective. No Path/NavRoute injection, no direct `moveTo`,
`setWantedPosition`, `JumpControl`, velocity injection, goal disable or collision disable.

## 3. Planner budget

IDX4:

```text
tick=33 plan=inicio=566,122,533 fin=567,119,534 H=48 V=6
exp=1/900 presupuesto=false evaluados=104 encolados=8
metaEncolada=true alcanzada=true ruta=1 global=-
```

Local plan only; no global expansion and no watchdog.

IDX8:

```text
plan=- for all 300 ticks
```

No plan was produced; this fixture is invalid for DROP observation.

## 4. IDX4 physical trace

UUID `185ab156-f535-4093-9a22-93f21392a308`, window ticks 35–75, 82 travel rows.

| Field | Observed |
|---|---|
| Navigation | path 2 nodes, next index1, target `567,119,534` from t35–43 |
| Path end | null from t44 |
| MoveControl | `MoveControl`, `hasWanted=false` in all 82 rows |
| Wanted XYZ | stored `567.5,119,534.5`, but inactive |
| Y | always `122.0000` |
| deltaMovement Y | `-0.0784` every sampled row |
| onGround | true |
| verticalCollision | true in all 82 rows |
| horizontalCollision | false |
| fallDistance | 0 |
| XZ movement | `566.50,533.50` → about `567.32,533.83`; no crossing to `567.5,534.5` |
| DROP_DESCENT_STARTED | none (Y never decreases) |

Block states:

```text
566,121,533 spruce_leaves full cube (platform)
567,119,534 air
567,118,534 spruce_leaves full cube (landing support)
```

AABB is `0.60 x 1.95`; vertical collision with the platform at y121 prevents Y decrease.

## 5. Root cause

**Confirmed for IDX4:** `Navigation.moveTo(landing)` builds a 2-node path and reaches its final node at
the platform edge, but PathNavigation does not leave an active MoveControl target (`hasWanted=false`).
The mob continues only a small residual horizontal drift, never crosses the support edge, and vertical
collision holds Y at 122. No alternative DROP actuator runs.

```text
ROOT_CAUSE:
PathNavigation accepts the lower landing but terminates at the edge without activating
MoveControl/step-off; the executor has no remaining movement owner to cross the edge.
CONFIDENCE: HIGH for IDX4 (per-tick trace).
```

This is not a collision-shape exception and not an infinite path: the path ends cleanly. The missing
piece is an actionable horizontal step beyond the support edge.

## 6. IDX8

```text
classification: FIXTURE_NO_DROP_WITHIN_200_TICKS
reached DROP: no
plan: "-" for 300 ticks
trace: absent
```

No causal conclusion about IDX8 DROP is drawn. The fixture setup/objective for IDX8 needs correction
before it can be compared.

## 7. Artifacts

IDX4 (`build/a2n/a2n-20261118-134234/phase40/`):

```text
idx4-tick-trace.csv
idx4-movement-callers.log
idx4-navigation-path.log
idx4-collision-shapes.log
idx4-block-states.log
planner-budget.log
execution-summary.txt
SHA256SUMS.txt
```

IDX8 (`build/a2n/a2n-20261118-134458/phase40/`):

```text
planner-budget.log
execution-summary.txt
SHA256SUMS.txt
```

The original UUID-named CSV is preserved alongside `idx4-tick-trace.csv`.

## 8. Integrity

```text
PRODUCTION_MODIFIED: false (temporary fixture/probe reverted from HEAD)
TESTS_MODIFIED: false
ORIGINAL_WORLDS_MODIFIED: false
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
RUN2_EXECUTED: false
FULL12K_EXECUTED: false
```

## 9. Next action

Phase 41 should use the IDX4 trace to add a bounded edge-step mechanism after the path ends at the
landing edge: an actionable horizontal MoveControl target just beyond the platform lip, held until the
AABB leaves support, with normal gravity/collision and existing predicates. Do not change support/fit,
budgets, tolerances or Phase 29/31/34. Fix the IDX8 fixture separately before inferring a common cause.

A2 remains OPEN.
