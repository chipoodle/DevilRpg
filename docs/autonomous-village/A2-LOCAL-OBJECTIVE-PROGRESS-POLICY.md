# A2 Local Objective Progress Policy

Status: implemented, focused-tested. A2 remains **FAIL / OPEN**; C4 PASS preserved.

## Existing transition semantics

- `NavRoute.Transition` distinguishes WALK, BREAK, BUILD, DROP and related route actions.
- `escalonarObjetivoLocal()` selects a local standable objective from `elegirObjetivoLocal()`.
- `escalonesDeObjetivo` counts accepted local planning steps, not physical stair placements.
- `fallosDeEscalonDeObjetivo` counts failed local selections; at the 64-step limit the code
  forces it to 10.

## idx3 evidence

Mission `616,71,578`; position `607,95,601`; mission requires a 24-block descent. The selector
returned the same-level `608,95,601`; `escalonarObjetivoLocal()` accepted it and incremented the
step counter until 64/64. This consumed the budget without descent.

## idx4 evidence

idx4 selected a descending continuation, reached the stair fallback, and placed a verified stair
at `589,92,511` (`colocados=1/24`, `ancla=589,92,511`).

## Policy

`NavLocalObjectiveProgressPolicy.aceptar(mobY, missionY, candidateY, routeContinuation,
pendingStair)`:

- pending placed stair: accept;
- route continuation with a non-WALK transition: accept as a valid intermediate;
- mission at or above the mob: accept (no descent required);
- mission below the mob: accept only if `candidateY < mobY`.

Rejected candidates do not consume the accepted-step budget; they increment the failure counter
and leave the objective unchanged.

## Invariants

- No new movement capability.
- No distance-only rule; horizontal route intermediates remain valid when the existing route
  marks a non-WALK continuation.
- No pending stair is abandoned.
- Finite 64/10 and terminal rearm budgets remain.
- Mission-chain retention and C4 behavior are untouched.

## Regression tests

`NavLocalObjectiveProgressPolicyTest` (6 assertions, passing):

- idx3 same-level rejection;
- idx4 descending acceptance;
- proven horizontal continuation;
- pending stair;
- mission-not-below same level;
- ascent while mission below.

## Runtime evidence

Run `a2n-20261114-011440` (focused FaseB6000):

- idx3 UUID `c274e2c4-…`: pos `617,86,600`, local `4/64`, failure `1/10`,
  `ultimoEscalon={candidato local sin progreso vertical: pos=617,86,600 ... candidato=618,86,600 dy=0}`.
- idx3 no longer repeats the same-level candidate to 64/64; it descends to y=86.
- idx4 UUID `f67c61dd-…`: fallback still places `589,92,511` and consumes it.
- A2 verdict remains FAIL/OPEN (inside-wall closing count incomplete); no PASS claimed.

## Limitations

- Focused run stopped at the phase-B snapshot; full A2 was not run.
- The policy only handles the proven mission-below case. Ascent/horizontal cases outside the
  route-continuation evidence must be verified in a full run before any acceptance claim.
- Acceptance threshold remains `SIN UMBRAL CONTRATADO`.

## Next action

Run the full agreed A2 fixture only after documenting/approving its threshold; verify idx3 keeps
progressing, idx4 still reaches the fallback, and C4 remains PASS.
