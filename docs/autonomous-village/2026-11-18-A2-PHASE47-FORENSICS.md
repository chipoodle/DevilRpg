# A2 Phase 47 — stair promotion + vertical DROP reproduction (2026-11-18)

## 1. Result

**PARTIALLY COMPLETE.**

- **IDX3:** root cause is now demonstrated. The pending stair waypoint is physically traversed and
  consumed by `confirmarTravesiaEscalonPendiente()` at the start of `elegirObjetivoLocal()`; the fallback
  called later in the same tick sees no pending waypoint and returns `null`. There were **20 verified
  placements and 20 physical traversals**, not zero.
- **IDX4:** no deterministic horizontal-zero fixture exists in the committed Harness, and the geometry
  did not occur in `a2n-20261118-163717`. Runtime physical validation remains `INSUFFICIENT_EVIDENCE`.

- Branch: `feature/autonomous-village`
- HEAD: `d9d544b07bbefde78b363a8f1079fe362d2f6998`
- Production: `e1530ab30591a26237d9ae64c1e9cfefbcbb27f2`
- No runtime in Phase47; no production/test change.

## 2. IDX3 evidence

UUID `9ed655f7-45bc-429f-9e08-9f1ae35bd43d`.

Physical traversal events in `srv.log`:

```text
[16:39:29] ESCALON-TRAVESADO #288 de pie ENCIMA del escalon COLOCADO 607,93,601
           entidad 607,94,601 | colocadoEnTick=229 travesadoEnTick=288
           bloqueVerificado=true | ancla=607,94,601
[16:39:29] ESCALERA-ANCLA #288 avanza a 607,93,601 (colocados=1/24)
[16:39:48] ESCALON-TRAVESADO ... colocadoEnTick=358 travesadoEnTick=405 ... colocados=2/24
[16:40:02] ESCALON-TRAVESADO ... colocadoEnTick=486 travesadoEnTick=535 ... colocados=3/24
```

Total: **20** `ESCALON-TRAVESADO` and **20** `ESCALERA-ANCLA` events.

At t288 the fallback reports null in the same tick:

```text
A2_STAIR_FALLBACK_DECISION tick=288 pos=607,94,601
  anchor=colocados=1/24 ancla=607,93,601
A2_STAIR_FALLBACK_RESULT tick=288 result=null
  stair={colocados=1/24 ancla=607,93,601}
```

### Code path

`elegirObjetivoLocal()`:

```java
az.confirmarTravesiaEscalonPendiente();
BlockPos escalonPendiente = az.objetivoSobreUltimoEscalon();
if (escalonPendiente != null) { ... }
```

`confirmarTravesiaEscalonPendiente()` detects the physical traversal and clears the pending stair. The
later `intentarConstruirEscalonSinWaypoint()` fallback therefore finds no pending `escalonTransitable`
(`objetivoSobreUltimoEscalon()` returns null), returns null, and the local stair counter increments.

The same sequence repeats at t405, t535, etc.: each stair is placed, physically traversed, and consumed
before the fallback can promote it to a waypoint.

```text
CLASSIFICATION: WAYPOINT_RETURNED_BUT_NOT_PLANNED? No.
Exact: pending waypoint is consumed (cleared) before fallback; fallback returns null.
Root cause: traversal confirmation clears pending state in the same tick before fallback promotion.
EVIDENCE: same-tick ESCALON-TRAVESADO #288 + fallback result=null + code order.
MISSING: per-call trace of objetivoSobreUltimoEscalon() before/after confirmation.
```

### Counter reconciliation

```text
STAIR_PIPELINE_ENTER=972
STAIR_PIPELINE_RETURN=484
  difference 488: branches that return without a STAIR_PIPELINE_RETURN event
  (pending waypoint, predicate rejection, direct waypoint return).

STAIR_BUILDER_ENTER=1426
STAIR_BUILDER_RETURN=505
  difference 921: direct construirEscalonHacia() callers (NavExecutor:1580,3371)
  bypass this traced wrapper.

STAIR_PIPELINE_PENDING=20
STAIR_PLACED outcomes=20 (no separate STAIR_PLACED event)
ACCESS_CLEARED outcomes=24 (no separate event)
NO_PROGRESS outcomes=461

escalonesDeObjetivo=64/64
fallosDeEscalonDeObjetivo=10/10
escalera.colocados=20/24
physical placements=20
physical traversals=20 (ESCALON-TRAVESADO)
```

The pending waypoint is not "lost after return": it is consumed by traversal confirmation before the
fallback attempts promotion.

## 3. IDX4 vertical-only

Historical case:

```text
takeoff 597,79,499 -> landing 597,78,499, horizontal0, dy=-1
```

Committed Harness infrastructure has no deterministic DROP fixture. `A2NavHarness` supports raid,
break/nav fixtures and block placement, but no mode that constructs this exact takeoff/landing and lets
the real planner emit the DROP. `a2n-20261118-163717` did not produce equal-X/Z takeoff/landing.

```text
CLASSIFICATION: INSUFFICIENT_EVIDENCE
WHY: no committed deterministic fixture; exact geometry absent from the run.
REQUIRED: a diagnostic mode (or ChatGPT-approved fixture) that:
- creates a supported platform edge with landing one block below and same X/Z,
- spawns the real entity grounded on the takeoff,
- lets the real local planner select the DROP,
- logs DROP_VALID -> EDGE_STEP_SELECTED -> OWNER -> COMMAND -> SUPPORT_CLEARED ->
  DESCENT_STARTED -> LANDING -> CURSOR_ADVANCE.
```

Do not force the case by calling the actuator or injecting a path.

## 4. Contracts / integrity

```text
PRODUCTION_MODIFIED: false
TESTS_MODIFIED: false
WORLDS_MODIFIED: false
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
RUN2_EXECUTED: false
FULL12K_EXECUTED: false
```

Phase41/44/46 and Phase29/31/34/36 unchanged.

## 5. Recommendation

1. Fix the order: after a confirmed traversal, allow the fallback/local selection to use the new
   anchor/waypoint instead of clearing pending before `objetivoSobreUltimoEscalon()`.
2. Add tick and predicate fields to `STAIR_PIPELINE_PENDING` and a `STAIR_WAYPOINT_REJECTED` in the
   `intentarConstruirEscalonSinWaypoint()` branch (currently silent).
3. Build an approved deterministic horizontal-zero DROP fixture before claiming Phase46 physical
   validation.

A2 remains OPEN at 4/8 strict. No Run2/Full12K until five consecutive 8/8 strict.
