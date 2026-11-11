# A8-F03-R12 — Integrated Final Acceptance

Status: **PARTIAL / BLOCKED**. A→B navigation ownership and active B path are demonstrated.
B arrival/cleanup, recovery negative controls, and physical wall-gate traversal remain
unverified. No production behavior changed.

## Repository

```text
branch feature/autonomous-village
HEAD 97220be5453b57387002b9bc91935807cc665274
staged 0
unstaged 26
untracked 172
```

No protected save, NBT change, A5/A6 modification, prohibited Git operation, commit/push.

## Scenario A — A→B lifecycle

Historical Run 224:

```text
A: task=b99470cc... target=53,97,-31 path=794850542 lease=WORK active=true
CACHE-CLEAR target=53,97,-31 reason=target-not-log
B: task=5aa527f1... target=40,97,-38
B selection: path=0 active=false lease=empty
next Goal tick: [R9-NAV] task=5aa527f1... AUTHORIZED
 owner=WORK task=5aa527f1... path=426535833 active=true tick=6045
```

PASS: A active/removal, cache invalidation, distinct B identity, B Agent navigation, B
lease, B active path.

NOT CAPTURED: explicit old-A path stop/release reason; B arrival; post-arrival lease
cleanup; subsequent TALAR acceptance.

## Scenario B — Negative controls

NOT RUN.

## Scenario C — Physical gate

BLOCKED. No valid outside-wall/gate fixture was established. No `wallGate=true`, selected
gate, approach, crossing, external arrival, or alternate-route exclusion. No geometry was
changed.

## Scenario D — Gate negative control

NOT RUN.

## Ownership

- ENFORCE agent owns TALAR target/step/navigation commands.
- Legacy Goal executes forestry operations and ENTREGAR.
- No ownership violation demonstrated.

## Verification

```text
compileJava = BUILD SUCCESSFUL
NAVIGATION_LEASE_TEST = OK
NAVIGATION_GATEWAY_POLICY_TEST = OK
NAVIGATION_GATEWAY_HARNESS_TEST = OK
lint_aldea.py --strict = PASS
git diff --check = PASS
```

## Acceptance matrix

| Criterion | Status |
|---|---|
| A active/removal/cache | PASS |
| Old path stop/release reason | NOT CAPTURED |
| B identity/authorization/lease/path | PASS |
| B arrival/Goal completion | NOT RUN |
| B post-arrival cleanup | NOT RUN |
| Recovery controls | NOT RUN |
| Gate selection | BLOCKED/NOT RUN |
| Physical crossing | BLOCKED/NOT RUN |
| External arrival | NOT RUN |
| Gate negative control | NOT RUN |

## Decision

**A8-F03 remains PARTIAL/BLOCKED.** The Agent-owned TALAR navigation contract is demonstrated
through B active path, but the mandatory arrival/cleanup and physical gate criteria are not.
No production defect is proven.

Closure requires a disposable outside-wall gate fixture that preserves existing geometry,
shows `wallGate=true`, and proves the inside-to-outside crossing through the selected gate.
Until then, do not close A8-F03 or expand execution authority.
