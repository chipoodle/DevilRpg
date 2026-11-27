# Guard return — diagnostic instrumentation after Scenario A failure

Date: 2026-11-27

## Harness result reviewed

- Tested production SHA: `39229f5dc45c2234bb65559251d33aeb42735c67`.
- Static gate: PASS (compile, tests, policy tests, strict lint, diff-check).
- Scenario A: FAIL. Ximeno received the persistent return state and street destination; the goal was running and WALK_TARGET was present, but position remained approximately `637.5,62.5,577.9` in water for 600 ticks. No cobblestone was observed and the return tag was not cleared.
- Scenarios B–F and priority/negative controls: NOT RUN.
- Original-world manifest: 48 files, before/after identical, world diff 0.
- Harness did not modify production.
- Root cause correctly remains INCONCLUSIVE: the report did not capture the actual WALK_TARGET coordinates, the result of `elCerebroVaA`, or the natural route query.

## Source review — important distinction

In the pre-diagnostic source, recovery activation had several gates:

1. `stuckTicks >= 60`.
2. No active enemy, equipment trip, or march.
3. `VillageManager.elCerebroVaA(villager, destino)` must be true; that helper compares the WALK_TARGET block position to the strategic destination with exact equality.
4. At exactly 60 stuck ticks, `hayRutaQueAlcanza(villager, destino)` may reset the counter if a natural path is reachable.
5. Only then does `GuardTerrainRecovery.tick(..., true)` attempt planning.

Crucially, when `GuardTerrainRecovery.plan(...)` returned false, the old code only scheduled a retry for 100 ticks and returned false. It emitted no log. Therefore, absence of `[GuardTerrain]` did **not** prove that recovery was never invoked; it could also mean that every plan attempt was silently rejected. Do not assume either explanation without the new traces.

## Diagnostic implementation

Commit: `968d78ffcaac4e345868583cd21f3a59e35e2703`.

Files changed:

- `VillagerGuardGoal.java`: `[GuardReturnDiag]` records the exact Brain target, exact-match result, natural route result at the 60-tick check, and current Navigation path shape/state. When the recovery gate is closed because the Brain target differs, it records the mismatch at bounded intervals.
- `GuardTerrainRecovery.java`: logs accepted candidate plans (origin, target, exact block list and landing) and logs aggregate rejection reasons (invalid landing, unsupported column, invalid placement, over-budget candidate) when no plan exists.

This is instrumentation only. It does not alter movement, thresholds, plan selection, block placement, retry cadence, budgets, protections, priorities, A2, or C4. No test or runtime result exists yet for this commit.

## Next validation

Run the exact handoff in `2026-11-27-GUARD-RETURN-DIAGNOSTIC-HANDOFF.md`. Static gates first; if they pass, repeat only Scenario A in a fresh disposable world. The next report must include all `[GuardReturnDiag]` and `[GuardTerrain]` records for Ximeno, including the first threshold event and each plan attempt/rejection. Keep the original-world hash unchanged.

Do not expand to B–F until A demonstrates physical movement or the new evidence identifies a concrete code defect that ChatGPT corrects.
