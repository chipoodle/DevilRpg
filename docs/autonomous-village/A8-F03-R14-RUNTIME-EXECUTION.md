# A8-F03-R14 — Runtime Execution

Status: **PARTIAL**. B arrival/completion and lease cleanup are now demonstrated. Recovery
negative controls and physical wall-gate traversal remain NOT RUN.

## Baseline

```text
branch feature/autonomous-village
HEAD 97220be5453b57387002b9bc91935807cc665274
staged 0
unstaged 26
untracked 173
```

No protected save, NBT change, prohibited Git op, commit/push.

## Run 225 — B completion

A:

```text
target=40,97,-17 task=9283e7b1... path=67967476 active=true lease=WORK tick=6507
A removed tick=6508
```

B:

```text
B=127,97,20 taskB=32f2db17...
selection path=0 active=false lease=empty tick=6581
B-NAV-ACTIVE path=1628967190 owner=WORK taskB active=true tick=6583
```

B completion:

```text
B-DONE target=null path=0 active=false
lease=Optional.empty
phase=ENTREGAR controller=SCOUTING
tick=7177
[A5] FIN PASS detail=R6-A B arrival/completion
```

This proves:

- B active navigation under its own task/lease;
- path completion/inactivity;
- lease cleanup to empty;
- target cleared;
- controller returned to SCOUTING;
- no stale A/B path remained at the sample.

A's old path was replaced by B's path; explicit old-A stop/release reason is still missing.

## Acceptance

| Criterion | Status |
|---|---|
| A removal/cache | PASS (prior) |
| Old A path stop/release reason | PARTIAL |
| B distinct identity/nav/lease | PASS |
| B arrival/completion | PASS |
| B lease cleanup | PASS |
| No stale A/B path at sample | PASS |
| Subsequent TALAR acceptance | NOT RUN |
| Recovery controls | NOT RUN |
| Gate selection/crossing | NOT RUN |
| Gate negative control | NOT RUN |

## Recovery controls

NOT RUN.

## Gate

NOT RUN. No outside-wall fixture, `wallGate=true`, physical crossing or negative control.

## Production changes

None. Fixture-only B completion stage.

## Verification

```text
compileJava = BUILD SUCCESSFUL
NAVIGATION_LEASE_TEST = OK
NAVIGATION_GATEWAY_POLICY_TEST = OK
NAVIGATION_GATEWAY_HARNESS_TEST = OK
lint_aldea.py --strict = PASS
git diff --check = PASS
```

## Remaining closure

1. Explicit old-A path stop/release reason.
2. Subsequent TALAR acceptance.
3. Recovery negative controls.
4. Real gate selection and physical crossing.
5. Gate negative control.

## Classification

**PARTIAL.** A8-F03 B completion is now closed; gate and recovery controls remain. No
production defect demonstrated.
