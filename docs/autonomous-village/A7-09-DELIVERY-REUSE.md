# A7-09 — Natural Delivery Identity Reuse and OFF/AUDIT Accounting

Status: **PARTIAL / NOT RUN** for the two required runtime objectives. No execution
migration is justified.

## Baseline

```text
branch feature/autonomous-village
HEAD 97220be5453b57387002b9bc91935807cc665274
staged 0
unstaged 26
untracked 151
```

## A — Same-target delivery identity

Run 207 fixture `agent-delivery-reuse`; disposable worker
`09c3d3fa-2956-449f-a4aa-e85233e6d240`.

```text
SPAWN logs=12
A task=d9ff8a7f-0866-3f7f-b804-104021093b17
 target=BlockPos{x=127, y=97, z=20} phase=ENTREGAR
CLEAR A task=d9ff8a7f... tick=4107 logs=12
```

The first delivery assignment was natural. The task cleared, but no natural delivery B
was assigned at the same target within the bounded runtime. The worker still carried 12
logs, so the refill step was not the missing precondition.

Result: **NOT RUN**. No B identity exists; distinct IDs across different targets are not
claimed as proof.

## B — OFF/AUDIT comparison

**NOT RUN.** No two independent manifest-verified worlds were executed.

## C — Operation manifests

- TALAR: PARTIAL (Run 203 natural harvest).
- PLANTAR: NOT RUN.
- ABONAR/GROWING: NOT RUN.
- ENTREGAR: PARTIAL (Run 206 `accepted5`; Run 207 cleared without confirmed deposit).

No reconciled `initial + produced - deposited - remaining - dropped = 0` manifest exists.

## D — Isolation

Boundary probe default-off, read-only. Renewal suppression default-off. OFF default. No NBT
change. No static entity references.

## Verification

compileJava PASS; lease/policy/harness tests PASS; lint PASS; diff-check PASS.

## Next

A7-10 must create a bounded second-delivery precondition and two-world accounting. Legacy
Goal remains executor.
