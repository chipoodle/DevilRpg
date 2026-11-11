# A7-12 — Controlled OFF/AUDIT Reproducibility and Final Acceptance

Status: **PARTIAL / non-equivalent**. Two pairs show OFF depositing one more log than
AUDIT, with matching task and target selection. Causality is not proven. No production
change was made.

## Baseline

```text
branch feature/autonomous-village
HEAD 97220be5453b57387002b9bc91935807cc665274
staged 0
unstaged 26
untracked 154
```

## Initial manifests

Both worlds are independent copies of the same disposable A5WALL fixture:

- village center 80,97,0
- tree 87,97,4 (four oak logs)
- storage baseline 128 logs
- worker fletcher at 82,98,2, empty inventory
- scenario `lumber-deliver-harvest`, 6000-tick bound

Known differences: worker UUID and game time are generated per run. No full block/inventory
snapshot was captured at tick zero, so exact manifest equivalence is not proven.

## Pairs

| Pair | Mode | POST carried | Warehouse delta | Final tick |
|---|---|---|---|---|
| 1 | OFF | 5 | +3 | 1585 |
| 1 | AUDIT | 4 | +2 | 1793 |
| 2 | OFF | 6 | +4 | 2037 |
| 2 | AUDIT | 5 | +3 | 2025 |

Both pairs:

```text
TALAR target=53,97,-17
POST-HARVEST delivery target=127,97,20
A5 FIN PASS
```

The direction is consistent: OFF carries/deposits exactly one more log than AUDIT. Task and
target selection are equivalent; final resource accounting is not.

## First divergence

The first observed divergence is at POST-HARVEST carried inventory (OFF +1). It precedes
delivery. Whether this is caused by AUDIT overhead/timing or by run-to-run worker variance
is not established. No gateway rejection occurred; AUDIT only observes and delegates.

## Accounting

The pair-level difference is one log:

```text
OFF: +3/+4 deposited
AUDIT: +2/+3 deposited
```

A complete reconciliation of initial + harvested - deposited - remaining - drops was not
captured. The final warehouse delta alone is insufficient to assign cause.

## Final classification

**PARTIAL.** Equivalence is not demonstrated, but no AUDIT-attributable defect is proven.
The difference is reproducible in direction across two pairs, so it cannot be dismissed as
pure noise without further instrumentation; it also cannot be classified as a defect yet.

## A7 decision

A7 does not pass acceptance. Do not migrate execution authority. Do not schedule another
repetition milestone merely to repeat this comparison.

Engineering recommendation:

1. Keep OFF as production default and AUDIT as a diagnostic-only mode.
2. Do not enable AUDIT in production or use it as an authority layer.
3. Close A7 as **validation-inconclusive**: identity, delivery and active-path safety are
   demonstrated; OFF/AUDIT resource equivalence is not.
4. Before any authority transfer, instrument the exact log pickup/drop accounting rather
   than repeating the end-balance comparison.

## Non-regression

No controller, planner, storage, pantry, NBT, Goal authority, Brain or GoalSelector change.
Only test harness mode plumbing and disposable fixture changes.
