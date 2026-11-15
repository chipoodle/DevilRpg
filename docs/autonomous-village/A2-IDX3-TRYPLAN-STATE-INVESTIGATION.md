# A2 idx3 tryPlan / FAILED State Investigation

Status: **UNRESOLVED — A2 FAIL / OPEN.** C4 remains PASS.

## Baseline

- Branch `feature/autonomous-village`, HEAD `518fee1`.
- No reset/clean/stash/revert/commit/push; no GUI; disposable worlds under `build/a2n/`.
- Latest focused run: `a2n-20261114-002306` (FaseA 3000, FaseB 6000, seed 12345).

## Instrumentation added

- `A2_TRYPLAN_ENTER` — `NavExecutor.tryPlan`, state, pos, objective, mission, local/replan/budget counters.
- `A2_REARM_ENTER` — `NavExecutor.intentarRearmeTerminal`, FAILED entry and counters.
- `A2_REARM_REJECTED` — exact rejection: `MAX_REARMES`, `FAILED_DEFINITIVE`, `COOLDOWN`,
  `BUDGET_NO_PROGRESS`, `POLICY_TERMINAL`, `POLICY_WAIT`.
- Existing stair fallback events retained.

## Proven state sequence (idx3 UUID `53543842-…` at final run; `92ff45f3-…` in earlier run)

```text
tick 3993  A2_REARM_ENTER state=FAILED rearmes=4/4
           motivo={sin objetivo local valido (10/10): tope de escalones locales alcanzado (64) hacia 616,71,578}
           objetivo/mision=616,71,578 pos=607,95,601
tick 3993  A2_REARM_REJECTED reason=MAX_REARMES
... repeated every tick ...
```

There is **no** `A2_TRYPLAN_ENTER` for this UUID. `tryPlan` is only called in `PLANNING`;
the entity is stuck in `FAILED` because the four terminal rearmes were consumed. The stair
fallback cannot execute in this state.

## idx3 vs idx4

- idx4 reaches `A2_STAIR_FALLBACK_DECISION/RESULT` and places a verified stair at `589,92,511`.
- idx3 never reaches the fallback because it exhausted all terminal rearmes before the final
  access-clear/retry cycle.
- The access-clear reset was applied, but subsequent attempts again consumed the four rearmes;
  the final state is still `4/4`.

## Root cause (proven)

**Terminal rearm budget exhaustion.** `intentarRearmeTerminal` returns at
`rearmesTerminales >= MAX_TERMINAL_REARMS` before `tryPlan` can run. The missing behavior is not
the fallback gate itself; it is the terminal state's exhausted rearm budget.

## Correction status

Instrumentation only. No speculative fix was applied after this proof. The next correction must
either:
1. make the verified access clear reset the terminal budget (already attempted and still
   exhausted after four fallback attempts), or
2. provide a different bounded transition from FAILED after a genuine terrain change, with
   proof it cannot loop.

That requires one more focused diagnostic of the four rearm attempts' results.

## Validation

- `compileJava` / `compileTestJava`: BUILD SUCCESSFUL.
- `NavTerrainRearmPolicyTest`: OK (9 assertions).
- `python tools/lint_aldea.py --strict`: OK.
- `git diff --check`: PASS.

## Verdicts

- **A2: FAIL / OPEN.** Snapshot t=3900: 4 inside-perimeter-ever, **0 inside wall**, 4 dead.
- **C4: PASS** in preserved evidence; no C4 logic changed.
- Threshold remains `SIN UMBRAL CONTRATADO`; no PASS invented.

## Exact next action

Instrument the four `intentarRearmeTerminal`→`tryPlan` cycles after the access clear, recording
why each fallback attempt returns null. Then fix only that bounded transition. Do not implement
the mobility proposal.

## 2026-11-14 00:40 — Post-rearm local counter overwrite proven

- Run 2n-20261114-003310 (focused FaseB6000). A2 still FAIL: t=3900 3 alive, 5 dead, 5 ever entered, 0 inside.
- idx3 UUID 9d3662ff-…: four terminal rearmes at ticks 275/405/535/665, each followed by A2_TRYPLAN_ENTER ... local=0/64 at 276/406/536/666.
- On the **same tick** as each tryPlan entry, NAV_FAIL reports local=64/64 fallos=10/10 again. The local stair counters are being restored/overwritten inside the planning path before the fallback can run.
- At tick 795 onward: A2_REARM_REJECTED reason=MAX_REARMES repeatedly; tryPlan no longer runs.
- idx4 still succeeds: A2_STAIR_FALLBACK_RESULT result=589,93,511, colocados=1/24, ncla=589,92,511; fallback behavior must be preserved.
- No production fix was applied for the counter overwrite; the exact writer is not yet identified. A2-IDX3-TRYPLAN-STATE-INVESTIGATION.md is the detailed report.
- Next: instrument scalonarObjetivoLocal/allar around the same tick with the counter value before/after and every write; fix the actual overwrite only.

## 2026-11-14 00:55 — Exact 0→64 writer identified; no safe fix applied

- Run 2n-20261114-004219 (focused FaseB6000). idx3 UUID 40f10aeb-….
- Trace shows A2_LOCAL_STAIR_ENTER local=0/64, then A2_LOCAL_STAIR_WRITE op=INC_STEP new=1..64 every ~2 ticks, each selecting 608,95,601 while mission is 616,71,578.
- Writer: NavExecutor.escalonarObjetivoLocal() line 1778 scalonesDeObjetivo++. The candidate is accepted despite not descending and while the mission remains below; the counter therefore counts non-progressing local steps.
- allosDeEscalonDeObjetivo reaches 10 via the repeated failure path once the 64 limit is hit (scalonarObjetivoLocal sets it to MAX at line 1755).
- idx4 (9c0f390…) still reaches fallback and places 589,92,511; a naive “candidate must be closer to mission” guard would also reject that valid step, so no speculative guard was added.
- A2 still FAIL: t=3900 0 inside; C4 preserved PASS. Acceptance threshold still undefined.
- Next exact action: instrument legirObjetivoLocal candidate generation for idx3 and add a progress predicate that distinguishes downward/sideways stair continuation from a same-level no-progress 608,95,601 selection; regression-test both idx3 rejection and idx4 acceptance before changing line 1778.

## 2026-11-14 01:25 — progress predicate added

- `NavLocalObjectiveProgressPolicy` rejects same-level/higher local candidates when the mission requires descent, unless a non-WALK route continuation or pending stair exists.
- Focused run `a2n-20261114-011440`: idx3 descended to 617,86,600 with local 4/64 and explicit rejection reason; idx4 still places 589,92,511.
- Policy test 6/6; compile/lint/diff pass; A2 remains FAIL/OPEN; C4 PASS preserved.
