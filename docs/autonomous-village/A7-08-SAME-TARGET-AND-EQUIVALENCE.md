# A7-08 — Same-Target Identity and OFF/AUDIT Runtime Equivalence

Status: **PARTIAL**. Active-path protection remains PASS from A7-07. Same-target identity
and OFF/AUDIT equivalence are NOT RUN. Natural delivery fixtures pass but lack full
resource manifests.

## Source lifecycle

- `VillagerLumberjackGoal.guardarTarea` clears `agentTaskId`/`agentTaskTarget` on target
  null and creates a new UUID when target is new/changed (`~229-235`).
- `descubrirTrabajo` selects work and `iniciarEntrega` selects the fixed storage access
  point (`~460`, `~519`).
- Natural completion clears `target` and saves (`~793`, `guardarTarea`).
- Failed targets are suppressed by `VillageManager.marcarPuntoFallido` (`~643`).
- `VillageStorage` has a fixed `OFFSET` access point (`VillageStorage.java:39`, `~74`), so
  a later delivery can legitimately reuse the same coordinates.

Identity is created per active target, not per goal instance. Target reuse is therefore
possible and should produce a new ID after a clear.

## Runtime evidence

Run 203 `lumber-deliver-harvest`: PASS, natural harvest delivery.

```text
TASK-BOUNDARY ASSIGN ... target=127,97,20 phase=ENTREGAR
TASK-BOUNDARY ASSIGN ... target=45,97,-24 ...
```

Run 206 `lumber-idle-delivery`: PASS, `idle delivery accepted5`.

```text
TASK-BOUNDARY ASSIGN uuid=6e8fba6c... task=dabfe62c...
 target=128,97,21 phase=ENTREGAR controller=DELIVERING
```

No natural completion followed by a new assignment at the exact same target was observed.
Therefore same-target A/B identity is **NOT RUN**. No transition was forced.

## Active-path protection

Run 205 remains valid:

```text
taskA=90340aa2... taskB=3039edb3...
targetA=115,97,20 targetB=50,97,25
pathB=1018130059 pathAfter=1018130059
stale=DENIED_WRONG_TASK activeAfter=true owner=taskB
stopB=AUTHORIZED stopped=true
```

## OFF/AUDIT comparison

NOT RUN. No two independent worlds with a shared manifest and resource capture were
generated.

## Operation evidence

- TALAR: PARTIAL — natural harvest logs and `LUMBER-TREE`; no before/after manifest.
- PLANTAR: NOT RUN.
- ABONAR/GROWING: NOT RUN.
- ENTREGAR: PARTIAL — Run 206 `accepted5`; no storage/pantry delta manifest.

## Isolation

Boundary probe default-off and read-only; renewal suppression default-off; OFF default;
no NBT change; no static entity reference.

## Verification

compileJava PASS; lease/policy/harness tests PASS; lint PASS; diff-check PASS.

## Next

A7-09 must produce a natural same-target delivery cycle and a two-world manifest before
any authority transfer.
