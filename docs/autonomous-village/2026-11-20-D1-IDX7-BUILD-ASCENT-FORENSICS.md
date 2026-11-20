# D1 IDX7 — BUILD +3 ascent forensics (2026-11-20)

## 1. Result

**COMPLETE — read-only.** IDX7 never reached a BUILD transition or the placement callback. The
failure is not a lease rejection or an incorrect instance of an existing BUILD path: there is **no
executable ascending-construction path** for the active executor, and a single +3 BUILD edge is
correctly rejected by the planner's `SUBIDA_MAXIMA=2` movement limit.

- BRANCH: `feature/autonomous-village`
- INITIAL_HEAD: `24210f1b0a652211dddbbcb8785fe24007ee41d7`
- INVESTIGATED_SOURCE_HEAD: same
- PRODUCTION_MODIFIED: false
- RUNTIME_EXECUTED: false
- MINECRAFT_LAUNCHED: false
- WORLDS_MODIFIED: false

## 2. Files/methods inspected

```text
AggressiveZombieEntity.java
  descensoExigeConstruir             2414
  objetivoExigeConstruccion          2325
  construirEscalonResultado          2358
  construirEscalonDescendente        1913
  apilarBloqueParaSubir              1650
  ponerEscalon / ponerEscalonConSoporteLateral
  recoverNavigation                  1243 (no callers)
  setRecoveryAction                  600
  setPlacement                       618

NavPlanner.java
  validarArista                      140
  esConstruible                      1073
  piesResultantes                    811
  etiquetaDeMovimiento               1088
  SUBIDA_MAXIMA                      111

NavExecutor.java
  BUILD case                         5267
  activeNode/activeActionTarget      5948-5970
  tryAcquireTraversalAction          5977
  tryPlan budget branch              3515-3550
  invokeTerrainAction                5855
```

## 3. Preserved runtime evidence

IDX7 UUID `b9bfa8ab-4674-40f0-8923-2eb0c05bf57d`.

```text
15:24:00 NAVEGACION FAILED en 705,65,459:
  presupuesto agotado sin progreso (10/10) hacia 703,68,457

final RAID NAV:
  pos=705,65,459
  estado=FAILED
  local={objetivo=703,68,457 buildable=true escalones=1/64 fallosEscalon=0/10
         ultimoEscalon={global ALCANZA la mision -> waypoint local 703,68,457
         (transicion=BUILD build=true rutaGlobal=79 exp=2531)}}
  decisionEscalera noConstruir=[objetivo 703,68,457 a desnivel -3 <= caida segura 3]
```

Trace around the failure:

```text
A2_TRYPLAN_ENTER tick=5167..5196 state=PLANNING
  objetivo=703,68,457 mision=661,72,533 local=1/64 budget=9/10
```

There is **no** `VERTICAL_TRANSITION`, `BUILD`, `PLACEMENT`, `BUILD-VERIFICADO` or `MOVE_COMMAND`
event for a BUILD node. The mob never left PLANNING.

## 4. Reconstruction

```text
mob feet:             705,65,459
global/local target:  703,68,457
delta:                 +3  (target above)
max jump (SUBIDA_MAXIMA): 2
max safe drop:        3 (configured)
```

A single BUILD edge to the target would have:

```text
candidate cell = 703,67,457? (cell that supports target feet)
BUILD feetHasta = candidate.y + 1
if candidate.y = 67 -> feetHasta=68, delta=+3
```

`validarArista` assigns BUILD only after `esConstruible`, then applies:

```java
if (piesHasta == PIES_NO_EJECUTABLE
        || piesHasta - piesDesde > SUBIDA_MAXIMA
        || piesDesde - piesHasta > maxSafeDrop) {
    return new Arista(..., Rechazo.DESNIVEL);
}
```

+3 > 2 → rejected. This is correct for a **single physical edge**: the mob cannot jump/climb 3 at once.
The defect is that no chain of intermediate BUILD cells is generated. The local objective is the final
+3 cell, and there is no executor-owned ascent policy to build +1/+2 first.

`descensoExigeConstruir` is descent-only; for `miY-objetivoY=-3` it says no construction. The
`decisionEscalera` line is a secondary diagnostic, not the executor's BUILD dispatch. The executor never
had an active BUILD node to lease.

## 5. H1–H6

```text
H1 signed-height predicate bug: CONFIRMED
   descensoExigeConstruir treats -3 as "<= safe drop" and returns false; it cannot authorize ascent
   construction. It is the diagnostic/recovery gate, but not the only reason no BUILD node exists.

H2 planner rejects BUILD +3: CONFIRMED (the generic guard rejects a single +3 edge)
   This is physically correct for one edge, but there is no multi-step ascent path.

H3 incorrect feet calculation: REJECTED
   piesHasta = candidate.y+1 is consistent; the guard, not a wrong feet value, rejects.

H4 executor does not dispatch BUILD: CONFIRMED
   Trace has no BUILD node/placement; mob stays PLANNING and exhausts local budget.

H5 lease or placement guard: REJECTED for this trace
   No active BUILD node; lease/placement never reached.

H6 secondary fallback interference: CONFIRMED as secondary
   recoverNavigation/descending builder and decisionEscalera report no-build but are not the primary
   missing executor path; apilarBloqueParaSubir has no live caller except the unregistered TraverseGoal
   and the now-uncalled recoverNavigation.
```

## 6. Primary root cause

**There is no route-driven ascending construction path.**

- The planner can emit a BUILD edge only for a single candidate with feet rise ≤2.
- A +3 target cannot be one edge; it needs a BUILD chain (+1, then +2).
- The executor's BUILD case only places a node the planner emits; it does not synthesize an intermediate
  stair.
- The previous ascending helper `apilarBloqueParaSubir` is only referenced by the removed TraverseGoal
  and the unreferenced `recoverNavigation`; it is not callable through the lease-gated executor.
- The descent-only gate additionally prevents the old recovery path from considering the ascent.

## 7. Minimum safe fix (proposal only)

1. Add a pure signed vertical policy:

```text
delta = targetFeetY - mobFeetY
delta > maxJump  -> ASCENT_BUILD_REQUIRED (multi-step)
delta < -maxDrop -> DESCENT_BUILD_REQUIRED (only if no supported landing)
otherwise        -> NO_BUILD
```

2. Make local objective selection choose a reachable intermediate BUILD cell (+1 or +2) when the final
target is higher, rather than the final +3 cell. The planner should emit a chain of BUILD nodes.

3. Route the resulting BUILD node through `placement.place(node.pos())` with the existing lease,
   support/headroom/protection and verification. Do not re-enable the old goals or strategic fallback.

4. Do not allow a single BUILD edge to bypass `SUBIDA_MAXIMA`; build intermediate cells instead.

5. Preserve all current budgets, cooldowns, protection, support, collision, A2/C4 criteria.

## 8. Tests to design (not execute)

### Pure vertical policy

| mobY | targetY | maxDrop | maxJump | expected |
|---:|---:|---:|---:|---|
| 65 | 68 | 3 | 2 | ASCENT_BUILD_REQUIRED |
| 68 | 65 | 3 | 2 | DESCEND |
| 65 | 67 | 3 | 2 | NO_BUILD |
| 65 | 66 | 3 | 2 | NO_BUILD |
| 65 | 69 | 3 | 2 | ASCENT_BUILD_REQUIRED |

### Planner

- +3 target must produce intermediate BUILD nodes, not a rejected single edge.
- Each BUILD node's `piesHasta` delta must be ≤2.
- Non-buildable intermediate cells must fail closed.

### Executor

- Active BUILD node +1 reaches `placement.place(exactNodePos)` once.
- Lease is acquired for that node and consumed on failure.
- Placement verification rereads the world and only then advances.
- BUILD cannot become BREAK/BRIDGE.
- No active node → no placement.

## 9. Invariants

Do not change: `SUBIDA_MAXIMA`, maxSafeDrop, budgets, cooldowns, lease semantics, support/headroom,
protection, collision, A2/C4 criteria, fixtures, worlds.

## 10. Integrity

```text
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

Artifacts:

```text
build/a2n/a2n-20261119-151818/srv.log
  B6B8106DB50BD14CEDDAEF59B14B4F3912F7FB3C4DA3443181C7E487E5999431
build/a2n/a2n-20261119-151818/nav-trace.log
  8195D6E04D7B1B7ABFFDBE9EAD1ADA93067B458A87772738E0C8B7386A7C435C
```

A2 remains OPEN. No runtime.
