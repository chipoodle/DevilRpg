# A8-F03-R9 — Final Integrated TALAR Acceptance

Status: **PARTIAL / BLOCKED**. Run 223 captured A with a valid lease/path and B selection
with a distinct ID, but B had no active path or lease at the observation. Gate traversal was
not run. No production defect fix was applied.

## Baseline

```text
branch feature/autonomous-village
HEAD 97220be5453b57387002b9bc91935807cc665274
staged 0
unstaged 26
untracked 169
```

## Call chain and diagnosis

`nextTalarStep` computes a Step only. `navigateTo` -> `syncForestryNavigation` acquires the
lease. R7's empty B lease was an observation race; R9 waited for A's active state but still
sampled B immediately at selection.

## Run 223 evidence

A active:

```text
[R9-NAV] uuid=b89... task=b99470cc... target=53,97,-31
 result=AUTHORIZED owner=WORK(path=794850542) active=true tick=7934
[R3] CACHE-CLEAR target=53,97,-31 reason=target-not-log
```

B selected:

```text
[R6-A] B-SELECTED A=53,97,-31 B=40,97,-38
 taskA=b99470cc... taskB=5aa527f1...
 pathAfterA=827999372 pathNow=0 active=false
 lease=Optional.empty ok=false tick=8582
```

No `[R9-NAV]` for taskB was captured in the window. Therefore:

- A was agent-owned and active.
- A removal/cache invalidation was observed.
- B selected naturally with a distinct ID/target.
- B had no active path or lease at the sample.
- B navigateTo/arrival remain unproven.

## Track A/B

| Criterion | Status |
|---|---|
| A active + lease | PASS |
| A removal | PASS |
| Cache invalidation | PASS |
| Old path cleanup/release | PARTIAL |
| B distinct task | PASS |
| B Step/command | NOT CAPTURED |
| B lease after navigateTo | NOT CAPTURED |
| B arrival | NOT RUN |

## Track C gate

NOT RUN. No outside-wall fixture, no `wallGate=true`, no physical crossing, no negative
control.

## Code changes

- `VillagerAgent` default-off `[R9-NAV]` diagnostic at the actual gateway boundary.
- `A5TestController` R9 diagnostic property.
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

**PARTIAL / BLOCKED.** A lifecycle is largely demonstrated; B command/lease/arrival and all
gate criteria remain unverified. A8-F03 remains open.

## Remaining closure conditions

1. Wait for B `[R9-NAV]` after selection and capture its lease/path.
2. Capture old path stop and lease release reason.
3. Prove B arrival and cleanup.
4. Execute physical gate fixture and negative control.
