# Phase 3 — Collaboration Policy and Stability Status (2026-11-15)

## Git baseline

- Branch `feature/autonomous-village`
- Remote and local were synchronized at `8a7b0b9112ee3757f3eda58158de5f99626d5c4f` at the start of this phase.
- No force-push, reset, clean, stash or revert.

## Permanent policy

Created `docs/autonomous-village/CHATGPT-COLLABORATION-POLICY.md`, and `NEXT-SESSION.md` now mandates reading it first with the other continuation documents.

## Phase-2 code review

### A2 BREAK guard

`NavBreakTargetPolicy.isSupportBelowTarget(hitY, targetY, sameColumn)` only identifies a same-column hit below the target. `NavExecutor` handles that case by returning the target only when the target itself is solid; different-column hits remain ordinary obstructions. Action range, budgets and collision checks are unchanged. Regression: 3 assertions.

### C4 return goal

`VillagerReturnPolicy` starts beyond 72 and continues to 64. `VillagerReturnGoal` uses MOVE, is registered at UTILITY before the equal-priority navigation goal, and flee remains priority 0. Guards are excluded at registration; no job sites are forced. Regression: 4 assertions.

No new defect was found in review.

## Controlled A2/C4 runs

| Run | A2 spawn | A2 entered | Bell | C4 |
|---|---:|---:|---|---|
| `a2n-20261115-145020` | 8/8 | 5/8 | verified | 12/12 PASS |
| `a2n-20261115-150410` | 8/8 | **8/8** | verified at tick 1284, one `SIEGE_COMPLETED` | 12/12 PASS |

`150410`: IDX8 entered and was traversing near `675,71,461` at closure; deaths were normal combat.

### Invalid extra run

`a2n-20261115-153633` was suspended by a host pause (advanced from t3001 to t3300 across ~3 hours). It was terminated and is excluded from evidence.

## Verdict

- A2 focused: **PASS in `150410`**, variable (5/8 in `145020`). Full 12k OPEN.
- C4: **2/2 focused PASS**; not universal stability yet.
- No full 12k campaign run.

## Diagnostic worlds

Unchanged and verified at:
- `DevilRpg-A2-IDX8-Diagnostico` — `BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7`, 15,966,366 bytes.
- `DevilRpg-C4-WorkerResume-Diagnostico` — `11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF`, 16,044,629 bytes.

Both exist in standard and dev saves, no `session.lock`.

## Tests

```text
gradlew test: PASS
NavBreakTargetPolicyTest: OK (3)
VillagerReturnPolicyTest: OK (4)
lint_aldea.py --strict: OK
git diff --check: PASS
```
