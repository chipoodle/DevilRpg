# Phase 10 — A2 Migration Entry Regression (2026-11-16)

## Objective

Isolate the strict-entry regression after the NavExecutor single-authority migration. No restoration of
the legacy planner, no second movement controller.

## Migration baseline

All runs: seed 12345, FaseA 3000, FaseB 6000, budgets 900/8000.

| Run | Perimeter | Strict entry | C4 |
|---|---:|---:|---|
| `a2n-20261115-234037` | 5/8 | 4/8 | 12/12 |
| `a2n-20261115-235237` | 5/8 | 4/8 | 12/12 |
| `a2n-20261116-000755` (6-block tactical leash) | 5/8 | 3/8 | 12/12 |

Pre-migration focused strict reference: **5, 7, 7, 7, 7**.

## Source findings

- `PursueTargetGoal` now submits tactical objectives to NavExecutor; it no longer owns a path.
- `holdForMelee()` is entered at ≤2 blocks; the strategic objective is preserved.
- `MoveToVillageCenterGoal` has no fallback and sends only the approximation chain.
- `aiStep` always ticks NavExecutor; there is no combat suspension.
- The initial migration retained a **6-block tactical leash**; distant targets still took tactical
  ownership from the strategic mission.
- The 6-block leash did not restore entry.

## Controlled correction

Single variable changed: `NavCombatIntentPolicy.TACTICAL_LEASH_SQR` from `36.0` (6 blocks) to `4.0`
(melee range). Behavior outside melee now leaves NavExecutor on the strategic village mission; combat
remains possible when a guard is actually in melee range.

Regression test updated and passing: `NavCombatIntentPolicyTest` (6 assertions).

## Focused result after correction

Run **`a2n-20261116-002550`**:

- 8/8 spawned.
- **6/8 reached perimeter.**
- **5/8 strict `DENTRO-PARED`**: idx1 512, idx2 667, idx3 3532, idx4 4012, idx6 649.
- idx5 perimeter only; idx7 and idx8 never entered.
- C4 **12/12 PASS**.
- No bell/conquest failure observed.

This is better than the migration baseline (3–4 strict) but still below the 6/8 strict gate.

## Per-attacker classification

| idx | Result |
|---|---|
| 1 | strict entry 512 |
| 2 | strict entry 667 |
| 3 | strict entry 3532 (late) |
| 4 | strict entry 4012 (late) |
| 5 | perimeter only, no strict entry |
| 6 | strict entry 649 |
| 7 | no perimeter; outside `708,58,468` |
| 8 | no perimeter; outside `746,64,538` |

IDX7/IDX8 remain outside with the strategic mission active after the combat leash removal; this is not
proven to be a combat-ownership issue.

## Rejected/remaining hypotheses

- **Combat leash was partially causal:** reducing it improved 3–4 strict to 5 strict, but did not
  restore the gate.
- **Melee hold** was not removed; no evidence it blocks entry.
- **Legacy planner absence** remains the architectural difference, but the specific NavExecutor route
  failure for idx7/idx8 is not yet isolated.
- No wall/guard/collision/budget weakening was applied.

## Tests

```text
gradlew test: PASS
NavCombatIntentPolicyTest: OK (6 assertions)
lint_aldea.py --strict: OK
git diff --check: PASS
```

## A2/C4 status

- A2 focused: **OPEN — 5/8 strict, 6/8 perimeter**; not accepted; no full 12k.
- C4: **12/12 PASS** in the corrected run; historical focused 5/5 preserved; save/reload OPEN.
- Migration remains single-authority; no fallback.

## Next action

Isolate why idx7/idx8 do not advance from `708,58,468` / `746,64,538` with the strategic mission and
melee-only combat ownership. Trace their NavExecutor objective/route/action and position deltas; do not
restore the legacy planner.
