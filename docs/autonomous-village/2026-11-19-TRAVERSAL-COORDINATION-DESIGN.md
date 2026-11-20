# Traversal coordination forensics and design (2026-11-19)

## 1. Status

**DESIGN COMPLETE — no production changes, no runtime.**

This report maps the current navigation/traversal authority, proposes a single-transition contract with a
per-tick actuator lease, and analyzes the atomic 3×3 custom gate separately. It does not implement any
change.

- BRANCH: `feature/autonomous-village`
- INITIAL_HEAD: `2bb7c517048b09788aee3a9a8447656b8a4e4330`
- HEAD ANALYZED: same
- PRODUCTION_MODIFIED: false
- RUNTIME_EXECUTED: false
- MINECRAFT_LAUNCHED: false
- WORLDS_MODIFIED: false
- TESTS_EXECUTED: false

## 2. Current navigation / traversal flow

```text
GoalSelector
 ├─ MoveToVillageCenterGoal (strategic intent only)
 ├─ PursueTargetGoal (target intent only; NavExecutor owns directed movement)
 ├─ BreakBlockGoal (priority 3, passive, no flags)
 └─ TraverseGoal (priority 3, passive, no flags)

NavExecutor
 ├─ route + routeIndex
 ├─ tries next node by transition
 └─ terrainChanged() invalidates route/global cache
```

`NavExecutor` is already the single **movement** authority, but not the single **terrain-action**
authority.

## 3. Findings with evidence

### 3.1 Active transition

- `NavExecutor.route` / `routeIndex` (line170/171).
- Active node: `route.currentNode(routeIndex)` (line4708).
- `NavRoute.Node(pos, transition, actionPos)` (`NavRoute.java` line45).

### 3.2 Exact action target

`node.actionPos()` is carried for BREAK/BUILD/BRIDGE; the executor uses `node.pos()` for BUILD/BRIDGE and
`actionTarget` for BREAK. BREAK stand position remains `node.pos()`; action block is separate.

### 3.3 Blockage/progress

- `NavExecutor` tracks `ticksSinAvance`/`mejorDistAlNodo`, threshold `AVANCE_MINIMO_DE_NODO=0.25`
  (line150), state `OBSTRUCTED` (handled at line3262).
- `BreakBlockGoal` (line3680) uses its own 10-tick movement window and `MOVIMIENTO_QUE_CUENTA=0.25`.
- `TraverseGoal` (line4052) uses a 10-tick window and `DESPLAZAMIENTO_MINIMO_SQR=0.25`.
- No shared blockage state; each goal decides independently that the mob is stuck.

### 3.4 Transition validity

Transitions are validated when the route is built (`NavPlanner.validarArista` and `NavGraphSearch`), and
`terrainChanged()` (line5990) invalidates route/global cache after a world change. There is no runtime
check that a terrain action is the one authorized by the active transition.

### 3.5 Current terrain authority

`BreakBlockGoal.tick` chooses:
- breach ring (`breakBrechaAhead`) when marching without a combat target;
- `breakStepAhead` if target is above;
- `blockingBlockAhead` + `breakBlock` otherwise.

`TraverseGoal.tick` chooses by target Y:
- target >1 below → `cavarHaciaAbajo`;
- target >1 above → `apilarBloqueParaSubir`;
- otherwise randomized `puentearHacia`/`apilarBloqueParaSubir`;
- final fallback `cavarHaciaAbajo`.

Both are passive (`no flags`) and both are registered at priority 3 (lines2601/2606). Priority alone does
not serialize them because neither owns a `MOVE`/`LOOK` flag and there is no shared lease.

### 3.6 Existing coordination candidates

- `RaidTacticalBoard` already provides expiring breach claims (`tryClaim`, `release`, `MAX_CLAIMS=64`,
  `CLAIM_TICKS=100`). Reusable for one gate/breach target, not for the per-tick actuator lease.
- `celdaBloqueadaPorRutaActiva` prevents generic tools from touching the active route cell; it is a guard,
  not an authorization.
- `terrainChanged()` is the correct post-action invalidation hook.

### 3.7 Existing tools, budgets, protection

| Tool | Method | Guards / budget |
|---|---|---|
| break block | `breakBlockAt` 939, `breakBlock` 4007 | `protegidoPorLaAldea`, breakable material, `tunelRestante=40`, `tunelEspera=40` |
| wall breach | `breakBrechaAhead` | real wall-ring cell only, same tunnel budget |
| dig down | `cavarHaciaAbajo` 2528 | only -1..-3, `cavarEspera=10`, protection/air |
| stair up | `apilarBloqueParaSubir` 1651 | `apilarRestante=8`, `apilarEspera=10`, headroom/support |
| bridge | `puentearHacia` 1486 | `puenteEspera=20`, tunnel budget, air/abyss checks |

Block entities are categorically rejected by `puedeRomperNodoDeNavegacion`/`breakBlockAt`, which makes the
custom hinge immortal.

## 4. Proposed contract

`NavExecutor` becomes the sole source of traversal intent. Expose read-only, no duplicated mutable state:

```text
activeNode()          -> NavRoute.Node or null
activeTransition()    -> NavRoute.Transition or null
activeActionTarget()  -> exact BlockPos or null
activeBlockReason()   -> enum/reason or null
activeBudget()        -> remaining/wait for that transition
```

The design may use a small `TraversalIntent` value object derived from the active node; it must not store
a second copy of route/objective/budget state.

### 4.1 Fail-closed rules

```text
no active node              -> no terrain action
route null / terminal       -> no terrain action
transition WALK/STEP_UP/JUMP_UP -> no terrain action
transition DROP             -> no dig unless local DROP landing validated
transition BREAK            -> only actionTarget; no BUILD/BRIDGE substitution
transition BUILD            -> only node.pos(); requires support/headroom/protection
transition BRIDGE           -> only node.pos(); requires valid gap/abyss
terrainChanged              -> invalidate lease; action cannot use stale token
protected cell              -> deny all tools
```

### 4.2 Existing gap first

Before a tool is authorized, check whether the active transition has a passable existing gap. A reachable
opening wins over new demolition/construction.

## 5. Per-tick actuator lease

One lease per entity per tick, held by the coordinator, not by a goal:

```text
TraversalLease:
  ownerEntity UUID
  tick
  transition
  actionTarget
  token/generation (route generation + terrain generation)
  used boolean
```

Acquisition:

1. Goal requests a tool for its active transition.
2. Coordinator compares request with `activeTransition`/`activeActionTarget`.
3. If a lease already exists for this entity/tick, deny (return false).
4. If transition/token stale, deny.
5. On successful physical action: mark used, call `terrainChanged()`, release lease at end of tick
   (single-use; next tick needs a new lease).
6. On failure: release without action; surface `NO_PROGRESS`/reason.

The lease is **single-use per tick**, even if both goals tick. Goal priority is not the serialization
mechanism; the lease is.

## 6. Transition/action matrix

| Transition | Allowed action | Forbidden |
|---|---|---|
| WALK | follow/advance | break/dig/build/bridge |
| STEP_UP | validated step-up | dig/build substitution |
| JUMP_UP | validated jump | dig/build substitution |
| DROP | advance; dig only if DROP landing validated | dig because global target lower |
| BREAK | break exactly `actionTarget` | BUILD/BRIDGE substitution |
| BUILD | place exactly `node.pos()` | breaking or bridging |
| BRIDGE | place exactly `node.pos()` | breaking or building elsewhere |
| none/terminal/invalid | none (fail closed) | all tools |

## 7. Pure policy tests to add (design only)

```text
1. only active transition authorizes tool
2. BREAK cannot become BUILD/BRIDGE
3. DROP requires validated landing
4. BUILD requires support/headroom
5. protected cell denies action
6. no lease -> no tool
7. two simultaneous requests -> at most one lease per entity/tick
8. absent/terminal transition -> deny
9. terrainChanged invalidates lease/token
10. existing passable gap wins
11. actionTarget exact match required
12. budgets/waits cannot be bypassed
```

Existing reusable tests: `STAIR_BUILD_RESULT_TEST` (12), `NAV_LOCAL_OBJECTIVE_STEP_POLICY_TEST` (5),
`NAV_BREAK_*`, `NAV_DROP_*`, `STAIR_ANCHOR`. A new pure `TraversalLeasePolicyTest` is required; no test
was modified or executed here.

## 8. Custom 3×3 gate — separate investigation

### 8.1 Assembly

- `DoubleGateBlock` is 3 wide × 3 high = 9 `DoubleGateBlock` cells.
- Base width cells: `VillageGenerator.celdasDelPorton(center, baseY, entrada)` (line7381), three along the
  wall axis.
- `sellarLasCeldasDelPorton` (line7420) places all nine (3 widths × 3 layers).
- Four generated entrances: `portonesDelMuro` (line9133).
- Hinge: only `SIDE==LEFT`, `!JUNTURA`, `LAYER==LOW` owns `PortonDobleBlockEntity`
  (`PortonDobleBlockEntity.esLaBisagra`, line70). The other eight have no block entity.
- `playerWillDestroy` (line333) removes all nine for a player, but mob destruction does not invoke it.

### 8.2 Atomic siege breach design

1. Identify gate by generated entrance + base `cota`, validate all nine cells are `DoubleGateBlock` and
   share `FACING`.
2. Claim the gate as one target via `RaidTacticalBoard` (key = hinge/gate id, not individual cell), so
   attackers cannot split the nine cells.
3. Authorize only the gate's hinge/assembly as a breach; preserve the global block-entity veto for every
   other block entity.
4. Remove all nine cells in one server-side operation with `destroyBlock(pos,false)` or an equivalent
   no-drop path; capture/validate hinge entity removal; never leave partial structure.
5. Verify all nine are absent and the passage is traversable before emitting breach success.
6. Call `navExecutor.terrainChanged()`/village wall invalidation after the atomic removal.
7. One breach event per gate claim; release claim on completion/failure.

### 8.3 Restoration

`VillageGenerator.asegurarMuro` (line7039) already:
- counts wall/gate cells;
- detects missing gates (`portonesMal`);
- calls `rehacerMuro` when incomplete;
- **returns without repair if any `AggressiveZombieEntity` is within `FENCE_RADIUS+4`** (line7137).

Risk: a stale/out-of-wave `IDX8` entity can suppress repair indefinitely. Required:
- repair only when the real siege/wave is resolved and no current wave member is operating at the wall;
- distinguish active wave membership from any aggressive zombie in a broad AABB;
- keep repair idempotent and do not repair during an active breach;
- verify all four gates restored, no partial cells, no orphan block entities.

Exact wave-membership API must be confirmed before implementation; `SiegeWaveState` exists but is a pure
evaluator over entity states, not a per-entity membership registry.

## 9. Runtime procedure for a future authorized test (not executed)

Use the automated headless path already documented:

```powershell
$env:ORG_GRADLE_PROJECT_a2nDiagnostics='true'
$env:ORG_GRADLE_PROJECT_a2nPopulationQueryHarness='true'
$env:ORG_GRADLE_PROJECT_a2nTraceNav='true'
$env:ORG_GRADLE_PROJECT_a2nTraceGlobalSearch='true'
$env:JDK_JAVA_OPTIONS='-Ddevilrpg.a2n.traceNav=true -Ddevilrpg.a2n.traceGlobalSearch=true'
.\build\a2n\lanzar-a2n.ps1 -Modo raid -Semilla 12345 -LimiteMinutos 60 ...
```

Evidence: `srv.log`, `nav-trace.log`, `launcher-status.txt`, `fin.txt`; disposable world under
`build/a2n/<run>/game`; protected worlds untouched. The server runs headless via Gradle `runServer`; no
client window/focus is used. The tracked wrapper currently mis-binds named args via array splatting
(`& $launcher @LauncherArgs`), so use the direct env invocation above or fix the wrapper first. A future
run requires explicit user authorization; this task authorizes none.

## 10. Risks / decisions requiring approval

- Adding a lease changes traversal behavior; must not bypass existing budgets/protection.
- Gate atomic destruction touches a block entity; requires explicit production approval and focused
  policy tests.
- Wave-membership API for repair gating must be confirmed; do not use “any zombie nearby”.
- The existing random bridge/stair order must be removed only through the transition contract, not by a
  local reorder.

## 11. Recommended order

1. Implement `TraversalIntent` + single-use lease + fail-closed policy tests.
2. Make `BreakBlockGoal`/`TraverseGoal` request actions instead of choosing tools.
3. Atomic gate breach with one claim and full nine-cell verification.
4. Repair gating based on resolved wave/active members.
5. Static gate, then new explicit runtime authorization.

## 12. Evidence boundary

No code was changed, no tests were run, no world or Minecraft process was touched. The design is based on
source inspection at HEAD `2bb7c517` and prior forensic reports.

A2 remains OPEN.
