# A7-11 — OFF/AUDIT Equivalence and Final A7 Acceptance

Status: **FAIL / NOT EQUIVALENT** for the tested harvest-delivery scenario. Both modes
executed the same tree and delivery area, but final resource accounting differed.

## Baseline

```text
branch feature/autonomous-village
HEAD 97220be5453b57387002b9bc91935807cc665274
staged 0
unstaged 26
untracked 153
```

## Fixture manifest (both runs)

- Two independent copies of the same disposable A5WALL fixture.
- Scenario: `lumber-deliver-harvest`.
- Worker: fletcher at center+(2,1,2), 4-log oak tree at center+(7,4), village center 80,97,0.
- Storage baseline at install: warehouse logs = 128 in both.
- Test bound: 6000 ticks.
- Mode: OFF for Run 209; AUDIT for Run 210.
- No protected save used.

Known non-equivalence: worker UUID is regenerated per run; world time and entity IDs differ.
No full block/inventory manifest was captured at tick zero.

## Runtime comparison

Both:

```text
TALAR target=53,97,-17
POST-HARVEST target=127,97,20
```

OFF Run 209:

```text
POST inv=5
END warehouse=131 delta=3
A5 FIN PASS tick=1585
```

AUDIT Run 210:

```text
POST inv=4
END warehouse=130 delta=2
A5 FIN PASS tick=1793
```

The target and phase sequence match; the carried/deposited quantities do not.

## Result

**FAIL for equivalence acceptance.** The tested resource accounting differs by one log
(3 vs 2 deposited). It is not established whether this is AUDIT-attributable or
nondeterministic worker/timing variance. Under the task rule, a difference that changes
final balances cannot be normalized away, and equivalence cannot be claimed.

Task-selection and target-selection equivalence were observed for this single pair.
Operation/resource equivalence was not.

## Prior accepted evidence

- Run 208: delivery A storage 167 -> 179, carried 12 -> 0.
- Run 208: natural delivery B with a different task ID.
- Run 205: active-path stale-stop protection.
- Delivery target variability is accepted as support-point selection, not an identity
  failure.

## Non-regression

No controller, planner, storage, pantry, NBT, Goal authority, Brain or GoalSelector change.
Only test harness mode plumbing and disposable fixture changes.

## Remaining

NEED: repeat paired OFF/AUDIT runs with fully verified initial manifests and identify
whether the 1-log difference is deterministic. Until then, A7 overall cannot be PASS and
execution migration is not justified.
