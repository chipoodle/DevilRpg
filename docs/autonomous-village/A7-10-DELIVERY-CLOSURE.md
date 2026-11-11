# A7-10 — Delivery Completion, Same-Target Reuse and OFF/AUDIT Closure

Status: **PARTIAL / BLOCKED for exact same-target delivery reuse**. Delivery A now succeeds
with a verified storage delta. Delivery B is naturally assigned at a different support
point because `VillageStorage.puntoDeApoyo` is position-dependent. OFF/AUDIT comparison
remains NOT RUN.

## Baseline

```text
branch feature/autonomous-village
HEAD 97220be5453b57387002b9bc91935807cc665274
staged 0
unstaged 26
untracked 152
```

## Run 207 diagnosis

Run 207 spawned the worker ~47 blocks from storage. The delivery target cleared with
`carried=12`, matching the `canContinueToUse` delivery failure/abandon path, not a
successful deposit. `CLEAR` alone was not completion evidence.

## Corrected fixture

`agent-delivery-reuse` now spawns beside the storage support area and records actual
storage contents before/after.

Run 208:

```text
worker=08134965-7ee3-4934-a1ea-1ac71095502a
A task=41f01f56-955d-3f06-bcc7-2929945e1446
target=127,97,20
A clear: carried=0 storageBefore=167 storageAfter=179
REFILL logs=12
B task=26995d91-8c5e-32ed-b705-1282bc8dae9c
target=123,97,21
sameTarget=false
```

A succeeded: 12 logs deposited, carried 0, storage +12. B was naturally assigned, but at
a different target. `puntoDeApoyo` picks the nearest free standing position within range,
so the target can change after A moves the worker. Exact X reuse is therefore **BLOCKED**
by current selection semantics, not by identity generation.

## Identity

A and B IDs are distinct, but `targetA != targetB`, so this does not satisfy the
same-target acceptance. No ID reuse or intra-task instability was observed.

## OFF/AUDIT

NOT RUN. No twin manifest worlds or resource comparison executed.

## Accounting

A local deposit reconciles: initial carried 12 + produced 0 - deposited 12 - remaining 0 -
drops 0 = 0. Storage 167 -> 179. This is a verified delivery operation, not a full harvest
cycle manifest.

## Non-regression

No accepted operation, controller, planner, storage, pantry, NBT or Goal authority changed.
Test-only fixture changes only.

## Next

A7-11 should either accept target-position variability and define identity equivalence by
storage access area, or add an explicit stable delivery anchor without changing accepted
deposit semantics. OFF/AUDIT must still be executed.
