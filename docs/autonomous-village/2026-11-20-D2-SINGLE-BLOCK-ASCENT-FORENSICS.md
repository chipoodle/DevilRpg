# D2 — single-block ascent and BUILD routing forensics (2026-11-20)

## 1. Result

**COMPLETE — read-only.** Current navigation allows a planned and actively executed **+2 JUMP_UP**.
The BUILD graph also stores the placement cell as the search successor instead of the resulting
standing cell, so the existing representation cannot cleanly express sequential one-block construction.

- BRANCH: `feature/autonomous-village`
- INITIAL_HEAD: `7cc2c8d9a2c3092bc76d69137310373170b9929e`
- INVESTIGATED_HEAD: same
- FINAL_HEAD at validation: same
- PRODUCTION_MODIFIED: false
- TESTS_MODIFIED: false
- STATIC_TESTS_EXECUTED: false
- RUNTIME_EXECUTED: false
- MINECRAFT_LAUNCHED: false
- WORLDS_MODIFIED: false

## 2. Jump call chain (facts)

```text
NavObstruction.classify
  if fits(pos.above(2)) && hasSupport(pos.above(2))
      -> Kind.JUMP_UP                         NavObstruction.java:225-232

NavPlanner.piesResultantes
  case JUMP_UP -> cell.getY() + 2            NavPlanner.java:811-821

NavPlanner.etiquetaDeMovimiento
  delta >= 2 -> JUMP_UP                      NavPlanner.java:1088-1104

NavPlanner.validarArista
  piesHasta - piesDesde > SUBIDA_MAXIMA(2) -> Rechazo.DESNIVEL
                                              NavPlanner.java:216-218
  SUBIDA_MAXIMA = 2                           NavPlanner.java:757

NavPlanner.sucesorCanonico
  escalada (STEP_UP/JUMP_UP) -> feet cell     NavPlanner.java:794-806
  NavSuccessorPolicy uses piesHasta

NavExecutor BUILD/JUMP switch
  case JUMP_UP -> iniciarSalto(node)          NavExecutor.java:4806
  iniciarSalto requires dy == 2               NavExecutor.java:2970-2999
  tickSalto sets delta Y=0.62 and lands at +2  NavExecutor.java:3040-3071
  routeIndex++ only after physical landing     NavExecutor.java:3067-3071
```

`JUMP_UP` is **not merely a planner flag**: it drives an explicit custom jump actuator with a 0.62
vertical impulse and a +2 landing check. Whether the mob reliably reaches that height is runtime
unverified, but the code intends and enforces a +2 transition.

## 3. Single-block invariant — all affected producers/consumers

To enforce “ordinary ascent ≤ +1”:

```text
NavObstruction.classify          remove/deny +2 JUMP_UP classification
NavPlanner.piesResultantes       remove JUMP_UP +2 feet result
NavPlanner.etiquetaDeMovimiento  delta >=2 must not become JUMP_UP
NavPlanner.SUBIDA_MAXIMA         must be 1 for ordinary movement (not relaxed)
NavPlanner.sucesorCanonico       JUMP_UP path no longer needed for +2
NavExecutor.case JUMP_UP          must not execute +2 transitions
NavExecutor.iniciarSalto/tickSalto reject/remove +2 actuator
NavRoute.Transition.JUMP_UP      compatibility only; never produced/executed
NavClimbPolicy.esWaypointDePie   JUMP_UP no longer a normal standing waypoint
NavRouteInvalidationPolicy       JUMP_UP references may remain compatibility-only
NavDropTransitionPolicy         JUMP_UP takeoff handling must be revisited
```

Tests that encode +2 assumptions:

```text
NavPlannerStateTest: JUMP_UP is waypoint; canonical JUMP_UP successor +2
NavArrivalPolicyTest: JUMP_UP waits for controller confirmation
NavDropTakeoffPolicyTest: exact final JUMP_UP takeoff
NavRouteInvalidationPolicyTest: JUMP_UP invalidation
NavClimbPolicyTest: escaladaValida
```

Do not change a constant alone: the producer, executor actuator, route-node policy and tests must move
together.

## 4. BUILD graph semantics

Current flow:

```text
validarArista(candidate buildable)
  kind = BLOCKED (not STEP_UP/JUMP_UP)
  cost<0 && esConstruible
    cost=COST_BUILD
    transition=BUILD
    piesHasta=candidate.y + 1

sucesorCanonico(candidate, kind=BLOCKED, BUILD, piesHasta)
  escalada = (kind == STEP_UP || kind == JUMP_UP) = false
  -> NavSuccessorPolicy returns candidate, NOT candidate.above()

NavGraphSearch.Edge state = candidate (the placement cell)
routePosition(state, BUILD, feet) returns state (placement cell)
NavRoute.Node.pos = placement cell
node feet data = candidate.y+1 (not represented as node.pos)
```

Consequences:

- The graph state after BUILD is the **solid cell being placed**, not the mob's resulting feet cell.
- The next search expansion uses that solid cell as if it were the current standing state.
- The route node's `pos` is the placement cell; the executor calls `placement.place(node.pos())`.
- `nodoAlcanzado` uses proximity to the placement cell, not physical arrival on top of it.
- A correct sequence such as `feet65 → place at65 → feet66 → place at66 → feet67` is not cleanly
  represented: the successor should be `(x,66,z)` after placing at `(x,65,z)`, with the placement cell
  carried separately as `actionPos`.
- `activeActionTarget()` for BUILD returns `node.pos()`; with a corrected node this should return the
  placement/action cell, while `activeNode().pos()` should be the standing cell.

This is the smallest coherent redesign: make BUILD state/route position the **resulting feet cell** and
carry the placement cell in `NavRoute.Node.actionPos`; update `NavRouteNodePolicy.routePosition`,
`activeActionTarget`, BUILD placement and arrival checks together.

## 5. Safety invariants

Existing checks that must remain:

- `esConstruible`: protection, `canPlace`, air at cell/above/above2, support face.
- `ponerEscalonConSoporteLateral`/placement callback: exact cell, support, headroom, no entity
  intersection, verification.
- `NavExecutor` lease: exact active transition/target, one per entity/tick.
- BUILD placement verification rereads the block and does not advance on failure.
- Budgets/cooldowns unchanged (`tunel=40`, `apilar=8`, waits 10/20/40, max steps).
- No BUILD→BREAK/BRIDGE conversion.

Current BUILD route advancement is weaker than DROP: after placement it can advance on proximity to the
placement cell, before physical standing verification. The proposed feet-cell route position fixes this
because `nodoAlcanzado` will test the actual standing cell.

## 6. Test matrix

| Scenario | Expected |
|---|---|
| delta 0 | WALK |
| delta +1 | STEP_UP |
| delta +2, no construction | REJECT |
| delta +2, valid construction | sequential BUILD + one-block movement |
| delta +3 | sequential BUILD + one-block movement |
| delta +4 | sequential BUILD, subject to budgets |
| descent within safe limit | existing DROP |
| descent beyond safe limit | existing recovery/build |
| BUILD without support | REJECT |
| BUILD without headroom | REJECT |
| BUILD in protected village | REJECT |
| placement rejected | no route advance |
| placement succeeds, arrival fails | no false traversal |
| lease denied | no placement |
| budget exhausted | REJECT |
| +1/+2/+3 intermediate not buildable | REJECT, no partial chain |

Boundary: each BUILD edge must have post-state feet delta ≤1; the full +3 route is three BUILD actions,
each verified.

## 7. Minimal safe production plan (proposal only)

1. Add a signed vertical policy with `MAX_ASCENT=1`; `+2` and above require BUILD.
2. Remove `JUMP_UP` from ordinary producers and reject any surviving +2 jump in `iniciarSalto`.
3. Change BUILD successor state to `candidate.above()` (feet), while `NavRoute.Node.actionPos` holds the
   placement cell. Update `routePosition`, `activeActionTarget`, BUILD case, and arrival checks.
4. Enforce per-BUILD lease, exact placement, support/headroom/protection, verification and budget.
5. Do not re-enable `BreakBlockGoal`/`TraverseGoal` or a strategic fallback.

## 8. Required tests (design only)

```text
VerticalPolicyTest: +0 WALK, +1 STEP_UP, +2/+3 REJECT without construction.
BuildChainTest: +3 produces three one-block BUILD states, not one +3 edge.
BuildGraphStateTest: successor is feet cell; actionPos is placement cell.
ExecutorBuildSeamTest: exact placement, lease, verification, no advance on failure.
SingleBlockInvariantTest: no JUMP_UP producer/consumer for ordinary movement.
Regression: all NavPlanner/NavArrival/NavDrop tests updated to remove +2 assumptions.
```

## 9. Risks

- Removing JUMP_UP affects route invalidation/arrival and takeoff recovery semantics.
- BUILD graph change affects all BUILD routes and any tests using `NavSuccessorPolicy`.
- Must not relax SUBIDA_MAXIMA to hide a producer; the producer/consumer must change.
- Runtime is still required to prove physical jump/landing behavior after change.

## 10. Integrity

```text
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

Artifacts preserved; no runtime. A2 remains OPEN.
