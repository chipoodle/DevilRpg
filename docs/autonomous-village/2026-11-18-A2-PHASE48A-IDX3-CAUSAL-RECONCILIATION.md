# A2 Phase 48A — IDX3 causal reconciliation (2026-11-18)

## 1. Result

**Phase47's causal explanation is corrected.** The fallback returns null because
`construirEscalonResultado()` returns `NO_PROGRESS` **before**
`intentarConstruirEscalonSinWaypoint()` ever calls `objetivoSobreUltimoEscalon()`. It is not a
"pending consumed then queried" bug.

At t288 the builder returns with `pending=null`, stale success `motivo` and `rechazo=-`; that is the
early cooldown/return branch, not a candidate-search failure. The same stair coordinate `607,93,601`
is placed 20 times and physically traversed 20 times, producing a self-loop without descent.

- RUN_HEAD: `d9d544b07bbefde78b363a8f1079fe362d2f6998`
- PRODUCTION_SHA: `e1530ab30591a26237d9ae64c1e9cfefbcbb27f2`
- DOCUMENTATION_HEAD at start: `3fc94c75015b391e8b1f0a95d484acbcd2be8861`
- No production/test/runtime change.

## 2. Decisive evidence at t288

```text
A2_LOCAL_STAIR_ENTER tick=288 local=41/64 ... objetivo=616,71,578
A2_STAIR_FALLBACK_DECISION tick=288 pos=607,94,601 objetivo=613,88,601
  anchor=colocados=1/24 ancla=607,93,601
STAIR_PIPELINE_ENTER pos=607,94,601 mision=616,71,578
STAIR_BUILDER_ENTER pos=607,94,601 target=616,71,578
  pending=null ancla=607,93,601 colocados=1
STAIR_BUILDER_RETURN outcome=NO_PROGRESS pending=null colocados=1
  motivo={escalon verificado tras 1 candidatos (dy=1)} rechazo={-}
STAIR_PIPELINE_RETURN outcome=NO_PROGRESS
A2_STAIR_FALLBACK_RESULT tick=288 result=null
```

`pending=null` at builder entry: the t288 traversal had already advanced the anchor to `607,93,601`
and cleared the pending block. The builder then returned `NO_PROGRESS` without a new placement.

## 3. Control-flow reconstruction

```java
StairBuildResult stairOutcome = az.construirEscalonResultado(mision);
if (stairOutcome != STAIR_PLACED) return null;          // <-- t288 exits here
BlockPos escalonTransitable = az.objetivoSobreUltimoEscalon(); // never reached
```

`construirEscalonResultado()` maps `false` from `construirEscalonDescendente()` to `NO_PROGRESS`.
Inside `construirEscalonDescendente()` the branches that return false without updating
`escaleraMotivo`/`escaleraUltimoRechazo` are:

```java
if (escaleraUltimoColocado != null && !confirmarTravesiaEscalonPendiente()) return false;
if (escaleraRetraso > 0) { escaleraRetraso--; return false; }
if (escaleraColocados >= MAX) { escaleraUltimoRechazo = "presupuesto agotado"; return false; }
```

At t288 `pending=null` rules out the first. `colocados=1 < 24` rules out the third. The successful
placement at tick229 set `escaleraRetraso = 10`; the observed stale `motivo`
(`escalon verificado tras 1 candidatos`) and `rechazo=-` are exactly what the **cooldown branch**
leaves untouched. Therefore the t288 `NO_PROGRESS` is the cooldown/early return branch.

```text
DEMONSTRATED: fallback exits before pending consultation; outcome=NO_PROGRESS.
STRONG EVIDENCE: cooldown branch, because the two diagnostic fields were not updated.
NOT LOGGED: exact escaleraRetraso value at t288 and number of calls since placement.
```

## 4. Physical self-loop

```text
16:39:26 CONSTRUCCION-ESCALERA #288 bloque 607,93,601 verificado (1 de 24)
16:39:29 ESCALON-TRAVESADO #288 ... colocadoEnTick=229 travesadoEnTick=288
16:39:29 ESCALERA-ANCLA avanza a 607,93,601 (colocados=1/24)

16:39:42 CONSTRUCCION-ESCALERA #288 bloque 607,93,601 verificado (2 de 24)
16:39:48 ESCALON-TRAVESADO ... colocadoEnTick=358 travesadoEnTick=405
16:39:48 ESCALERA-ANCLA avanza a 607,93,601 (colocados=2/24)

16:39:56 CONSTRUCCION-ESCALERA #288 bloque 607,93,601 verificado (3 de 24)
16:40:02 ESCALON-TRAVESADO ... colocadoEnTick=486 travesadoEnTick=535
```

The builder repeatedly places the **same coordinate** `607,93,601`; the mob repeatedly stands on it,
confirming traversal each time. Net descent is zero. This is a stair-construction self-loop, not a
failure to traverse.

## 5. Three states

```text
A pendiente de travesía: block placed, not yet stepped on.
B atravesado: mob occupies block.above(); confirmarTravesia advances anchor and clears pending.
C siguiente no colocado: pending null; builder must create a NEW lower step.
```

At t288 the system is correctly in C (pending null). The defect is that C is answered by a
`NO_PROGRESS`/cooldown return that does not build the next lower step; the next placement later
reverts to the same coordinate.

```text
B→C is handled (pending cleared).
C→next step is broken: cooldown/early return + same-coordinate placement.
```

## 6. Cooldown questions

```text
1. Exact cooldown at each traversal: NOT_LOGGED (STRONG_EVIDENCE branch = >0 at t288).
2. Calls before zero: NOT_LOGGED; builder enters are not ticked.
3. Calls reaching builder: STAIR_BUILDER_ENTER=1426; direct construirEscalonHacia callers bypass 921.
4. Cooldown reload without candidate: yes in code, every failed candidate search sets
   escaleraRetraso=ESCALERA_ENTRE_BLOQUES.
5. NO_PROGRESS counted as local failure: yes, fallback null -> INC_STEP/INC_FAIL at the caller.
6. Cadence: repeated 20-tick-ish cycles, but the exact decrement sequence is not logged.
```

## 7. Reconcile counters

```text
STAIR_PIPELINE_ENTER=972 / RETURN=484: 488 early branches return without trace.
STAIR_BUILDER_ENTER=1426 / RETURN=505: 921 calls via direct construirEscalonHacia.
STAIR_PIPELINE_PENDING=20.
STAIR_PLACED outcomes=20, no event.
ACCESS_CLEARED outcomes=24, no event.
NO_PROGRESS outcomes=461.

escalonesDeObjetivo=64/64
fallosDeEscalonDeObjetivo=10/10
escalera.colocados=20/24
verified placements=20 (CONSTRUCCION-ESCALERA)
verified traversals=20 (ESCALON-TRAVESADO)
```

Placements and traversals are equal, but all 20 placements are the same coordinate `607,93,601`.

## 8. Required instrumentation

ChatGPT must add, without changing behavior:

```text
STAIR_BUILDER_ENTER: tick, escaleraRetraso, escaleraAncla, escaleraUltimoColocado, colocados
STAIR_BUILDER_COOLDOWN: tick, retrasoBefore/After, returnReason
STAIR_BUILDER_CANDIDATE: tick, base, candidate, rejection
STAIR_BUILDER_RETURN: tick + updated motivo/rechazo
```

Also log `escaleraAncla`/`blockPosition().below()` at each candidate generation, to explain why the same
coordinate is selected after traversal.

```text
IDX3_CLASSIFICATION: INSUFFICIENT_EVIDENCE for the exact C→next-step break;
DEMONSTRATED for the control-flow error: fallback exits before pending promotion with NO_PROGRESS.
```

## 9. IDX4 fixture proposal (not implemented)

No production fixture is committed. Proposal for a later authorized diagnostic:

- disposable world, flat platform at `(0,80,0)`;
- supported takeoff `(0,80,0)` and landing `(0,79,0)` with support at `(0,78,0)`;
- real `AggressiveZombieEntity`, grounded at takeoff;
- normal goals and planner; objective set to landing so the real graph emits DROP;
- no path/NavRoute injection, no direct actuator call, no velocity;
- tick trace of AABB/support/MoveControl/deltaMovement;
- headless, no GUI/focus;
- discard world; original A2/C4 untouched.

The key geometric challenge is a platform whose support leaves the AABB partially supported at the
landing column, producing horizontal0 with a valid lower landing.

## 10. Integrity

```text
PRODUCTION_MODIFIED: false
TESTS_MODIFIED: false
WORLDS_MODIFIED: false
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

A2 remains OPEN at 4/8 strict.
