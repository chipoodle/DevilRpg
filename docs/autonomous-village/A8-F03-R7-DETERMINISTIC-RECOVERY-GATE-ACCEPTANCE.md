# A8-F03-R7 — Deterministic TALAR Recovery & Physical Wall-Gate Acceptance

Status: **PARTIAL**. Fixture A demonstrated active A navigation, target removal, B selection,
distinct task IDs and a changed path. Lease ownership at B was empty, so agent ownership of
B is not proven. Fixture B was not run.

## Baseline

```text
branch feature/autonomous-village
HEAD 97220be5453b57387002b9bc91935807cc665274
staged 0
unstaged 26
untracked 167
```

No protected save, NBT change, prohibited Git operation, commit or push.

## Fixture A — Run 222 (ENFORCE)

Verified worker `b89fcee1-c91a-4b1f-a9c4-66471b616288`; natural TALAR selected after waiting
for the real phase (not a fixed early gate).

```text
R6-A A-SELECTED uuid=b89... target=53,97,-17
 task=0328794e-086e-30f0-80b8-3d00b35a08a5 path=912390442 active=true
 lease=task 0328794e... (WORK, expires 8198) tick=7999

R6-A A-REMOVED target=53,97,-17 tick=8000

R6-A B-SELECTED A=53,97,-17 B=45,97,-38
 taskA=0328794e... taskB=af7eb131-fcdf-31a3-8ca5-1d23d006f812
 pathAfterA=912390442 pathNow=1316776743 active=true
 lease=Optional.empty
 ok=true tick=8058
```

- A was a valid active target with lease.
- A was removed through world state.
- B was selected naturally at a different target.
- Task IDs differ.
- Path changed from 912390442 to 1316776743.
- Lease at B was **empty**, so ENFORCE agent ownership of B navigation is not proven.

No `[R3] CACHE-CLEAR` event appeared in this short window; B selection occurred quickly.

## Fixture A acceptance

| Criterion | Status |
|---|---|
| Active A navigation | PASS |
| A removal | PASS |
| B selected naturally | PASS |
| `taskA.id != taskB.id` | PASS |
| Path changed | PASS |
| Agent lease at B | **FAIL/PARTIAL — empty** |
| Cache invalidation event | NOT OBSERVED |
| Stale A navigation prevented | PARTIAL (path changed; no lease) |
| B arrival | NOT RUN |

## Fixture B — wall gate

**NOT RUN.** No outside-wall target/gate fixture was executed; `wallGate=true` and physical
crossing remain unobserved.

## Ownership

- A navigation was agent-owned (lease WORK).
- B path was active but lease empty; cannot claim agent-owned B navigation.
- OFF and ENTREGAR unchanged.
- Shared planner remains.

## Code changes

- `A5TestController` R6/R7 recovery fixture (test-only).
- No production behavior changed.

## Verification

```text
compileJava = BUILD SUCCESSFUL
NAVIGATION_LEASE_TEST = OK
NAVIGATION_GATEWAY_POLICY_TEST = OK
NAVIGATION_GATEWAY_HARNESS_TEST = OK
lint_aldea.py --strict = PASS
git diff --check = PASS
```

## Remaining limitations

- B lease acquisition/ownership after recovery.
- Cache-clear event was not captured in this window.
- No wall-gate selection or crossing.
- No B arrival.

## Classification

**PARTIAL**. Fixture A recovery is largely demonstrated but agent lease ownership of B is
not. Fixture B is NOT RUN. A8-F03 remains open.

## Git/safety

```text
Branch: feature/autonomous-village
HEAD: 97220be5453b57387002b9bc91935807cc665274
Staged: 0
Unstaged: 26
Untracked: 167
```

No protected save access, NBT change, A5/A6 modification, prohibited Git operation, commit
or push.
