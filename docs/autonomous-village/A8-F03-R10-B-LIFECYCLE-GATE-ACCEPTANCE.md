# A8-F03-R10 — B Task Lifecycle Forensics & Independent Gate Acceptance

Status: **PARTIAL**. B's post-selection lifecycle is now captured: next Goal tick issues
B navigation with a valid agent lease and active path. B arrival and physical gate traversal
remain unverified.

## Baseline

```text
branch feature/autonomous-village
HEAD 97220be5453b57387002b9bc91935807cc665274
staged 0
unstaged 26
untracked 170
```

No protected save, NBT change, prohibited Git op, commit/push.

## B lifecycle — Run 224

A active (historical R9):

```text
taskA=b99470cc... target=53,97,-31 lease=WORK path=794850542 active=true
CACHE-CLEAR target=53,97,-31 reason=target-not-log
```

B selected:

```text
taskB=5aa527f1... target=40,97,-38
path=0 active=false lease=empty
```

Then, on the next Goal tick:

```text
[R9-NAV] task=5aa527f1... target=40,97,-38 result=AUTHORIZED
 owner=WORK task=5aa527f1... path=426535833 active=true tick=6045
```

B's path continued through several ticks. A later natural delivery task
`32f2db17...` acquired its own lease and active path at tick 6111. This proves the empty
lease at selection was an observation race, not a missed acquisition.

## A-side acceptance

| Criterion | Status |
|---|---|
| A active/lease | PASS |
| A removal | PASS |
| Cache invalidation | PASS |
| Old path cleanup | PARTIAL |
| B distinct task | PASS |
| B navigateTo/lease | PASS |
| B active path | PASS |
| B arrival | NOT RUN |
| Lease cleanup after arrival | NOT RUN |

## B arrival

The fixture ended when B navigation became active. No arrival/standing-cell completion or
lease cleanup was observed.

## Negative controls

NOT RUN (removal before nav, invalid target, obstruction, recovery).

## Gate scenario

NOT RUN. No outside-wall fixture, `wallGate=true`, physical crossing or negative control.

## Code changes

- `A5TestController` B-trace stage (test-only).
- `VillagerAgent` default-off `[R9-NAV]` diagnostic.
- No production behavior change.

## Verification

```text
compileJava = BUILD SUCCESSFUL
NAVIGATION_LEASE_TEST = OK
NAVIGATION_GATEWAY_POLICY_TEST = OK
NAVIGATION_GATEWAY_HARNESS_TEST = OK
lint_aldea.py --strict = PASS
git diff --check = PASS
```

## Classification

**PARTIAL.** B navigation ownership is now proven; B arrival/cleanup and all gate criteria
remain open. A8-F03 is not closed.

## Remaining closure

1. B arrival and lease cleanup.
2. Old A path stop/release reason.
3. Negative controls.
4. Real gate selection and physical crossing.
