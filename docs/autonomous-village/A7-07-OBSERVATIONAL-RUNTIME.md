# A7-07 — Observational Runtime Fixtures and Forestry Ownership Validation

Status: **PARTIAL**. Active-path stale-stop protection passes. Same-target successive
identity and OFF/AUDIT resource-equivalence are NOT RUN. Natural harvest/delivery ran, but
operation-delta capture is incomplete. Legacy Goal remains executor.

## Baseline

```text
branch feature/autonomous-village
HEAD 97220be5453b57387002b9bc91935807cc665274
staged 0
unstaged 26
untracked 149
```

## Files

- `debug/ForestryTaskBoundaryProbe.java` — read-only, default-disabled observer of legacy
  task IDs/targets/phases; no state mutation.
- `A5TestController` — `agent-active-stale` fixture.
- `A5TestController` — boundary probe scenario wiring.

No NBT or accepted forestry operation changed.

## A — Same-target identity: NOT RUN

Boundary probe observed real distinct assignments and clears in Run 203:

```text
ASSIGN e... task=f1535edc... target=127,97,20 phase=ENTREGAR
CLEAR  b89... oldTask=ff93e5ee... target=45,97,-24
ASSIGN b89... task=32f2db17... target=127,97,20 phase=ENTREGAR
```

Successive task IDs were distinct, but no completion followed by a new assignment at the
exact same target was observed. Repeated snapshots were stable, but this is not the required
same-target A/B proof.

## B — Active navigation protection: PASS (Run 205)

```text
worker=b89fcee1-c91a-4b1f-a9c4-66471b616288
taskA=90340aa2-7c7d-407e-8f41-937be1789c74
taskB=3039edb3-13e9-44a9-ab5a-095da969585f
targetA=115,97,20 targetB=50,97,25
ra=AUTHORIZED aActive=true
rb=AUTHORIZED bActive=true
pathB=1018130059 pathAfter=1018130059
stale=DENIED_WRONG_TASK bActiveAfter=true
owner=taskB stopB=AUTHORIZED stopped=true
[A5] FIN PASS
```

B had an active path before and after A's stale stop; path identity was unchanged, B
retained ownership, and B's valid stop succeeded. No claim beyond observed path identity,
destination and active state.

## C — OFF/AUDIT equivalence: NOT RUN

No two independent worlds with a shared explicit manifest and resource accounting were
generated. No equivalence claim is made.

## D — Natural operations: PARTIAL

`lumber-deliver-harvest` Run 203 completed with `[A5] FIN PASS detail=natural harvest
delivery`; `[LUMBER-TREE]` entries and delivery occurred. The boundary probe observed
TALAR/ENTREGAR phase changes and task IDs. However, before/after block and inventory deltas
were not captured in a dedicated operation manifest. No full TALAR → PLANTAR → ENTREGAR
coverage is claimed.

## E — Isolation

- Boundary probe enabled only by `-Ddevilrpg.taskBoundaryProbe=true`.
- Renewal suppression defaults false.
- OFF remains default.
- No NBT, no static entity reference.
- The probe reads snapshots only; no target/phase/controller mutation.

## Verification

```text
compileJava = BUILD SUCCESSFUL
NAVIGATION_LEASE_TEST = OK
NAVIGATION_GATEWAY_POLICY_TEST = OK
NAVIGATION_GATEWAY_HARNESS_TEST = OK
lint_aldea.py --strict = PASS
git diff --check = PASS
```

## Next

A7-08 must close same-target identity and OFF/AUDIT manifest comparison; no execution
migration yet.
