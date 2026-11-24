# MATRIZ DE PRUEBAS — AUTONOMOUS VILLAGE (actualizada 2026-11-24)

> Esta matriz clasifica **qué se prueba, con qué mecanismo, en qué gate y qué invariante protege**. No sustituye a
> la evidencia runtime: una fila marcada `RUNTIME` solo se considera demostrada cuando existe un run concreto con
> `fin.txt`/JSON/logs. Los runs viven en `build/c4n/**` y no se borran.

## 1. Niveles

| Nivel | Mecanismo | Dónde | Necesita servidor |
|---|---|---|---|
| Unitario puro | Programa `main`, sin Minecraft | `src/test/java/**` (tarea `policyTest`) | No |
| Integración NBT | `Bootstrap.bootStrap()` + `CompoundTag`/`SavedData`/`NbtIo`/entidad real | `src/villageTest/java/**` (tarea `villagePersistenceTest`) | No |
| Integración Skills | `Bootstrap.bootStrap()` + árbol JSON + capability + NBT real | `src/skillTest/java/**` (tarea `skillCapabilityTest`) | No |
| Integración Food | Contenedores/NBT reales | `src/foodTest/java/**` (`foodNbtTest`, `foodIntegrationTest`) | No |
| Runtime C4 | Servidor NeoForge real, mundo desechable, 3 fases | `tools/c4n/C4StandaloneHarness.java` | **Sí** |
| Runtime A2 | Servidor NeoForge real, asedio, mundo desechable | `tools/a2n/A2NavHarness.java` | **Sí** |

## 2. Matriz de pruebas por área

### 2.1 Persistencia de aldea y censo (Incremento 3 · B)

| Prueba | Nivel | Aserciones | Gate | Invariante / qué protege |
|---|---|---:|---|---|
| `VillagePersistenceIntegrationTest` | Integración NBT | **553** | `villagePersistenceTest` → `test` | I388, I389, I400, I403: save/load real, dos ciclos, fichero, parcial, corrupción, identidad, JOB_SITE, fundadores, reconciliación, **limpieza por muerte** (bloque 11) y **refresco de `OwnerPos`** (bloque 12) |
| `VillageRosterInvariantsTest` | Unitario | 12 | `policyTest` | I387: 12 puestos, 4 fundadores, UUID único, cupo, un dueño por puesto |
| `VillageJobSiteOwnershipPolicyTest` | Unitario | **54** | `policyTest` | Un solo dueño por POI; idempotencia; no destruir titular; **puestosDelTitular** (L1/I400); **restauración del titular frente a `yaDeOtro`** (L3/I402) |
| `VillagePopulationLoadPolicyTest` | Unitario | 7 | `policyTest` | Ventana de gracia de carga (no repoblar antes de cargar) |
| `VillageFarmOwnershipTest` | Unitario | 6 | `policyTest` | Una parcela, un dueño; liberación segura |
| `VillageTerrainPolicyTest` | Unitario | 11 | `policyTest` | Terreno/altura de aldea |
| `VillageForestLayoutTest` | Unitario | 20 plazas | `policyTest` | Separación interior/arboleda, radio |
| `WarehouseApproachPolicyTest` | Unitario | 8 | `policyTest` | Acceso al almacén |
| `FoodHungerPolicyTest`, `PantryCreditPolicyTest` | Unitario | — | `policyTest` | Comida/crédito de despensa |
| `FoodStateNbtTest`, `FoodIntegrationTest` | Integración NBT | — | `foodNbtTest`, `foodIntegrationTest` | NBT real de comida y `VillageSavedData` de despensa |
| `SkillCapabilityIntegrationTest` | Integración Skills | **376** | `skillCapabilityTest` → `test` | I401: 33/33 skills con nodo/coste/display; 36 nodos y 3 raíces; mapas NBT completos; partida vieja recibe skills nuevas sin pisar valores; iconos contra el registro real |

### 2.2 Navegación y planner (Incremento 2/3 · C/D)

| Prueba | Nivel | Aserciones | Gate | Invariante / qué protege |
|---|---|---:|---|---|
| `NavTerrainMatrixTest` | Unitario (matriz) | 36 | `policyTest` | STEP_UP/DROP/BREAK/BUILD/presupuestos; escenarios runtime documentados |
| `NavVerticalBuildPolicyTest` | Unitario | 39 | `policyTest` | Ascenso +1, BUILD con pies/acción separados |
| `NavBreakGraphStateTest` | Unitario | — | `policyTest` | Estado post-BREAK y arista exacta |
| `NavBreakAttackPositionPolicyTest` | Unitario | 8 | `policyTest` | Posición de ataque BREAK |
| `NavBreakFilterPolicyTest`, `NavBreakRoutePolicyTest`, `NavBreakContractTest` | Unitario | — | `policyTest` | Filtros/contratos de rotura |
| `NavDrop*PolicyTest` (Approach, Ownership, EdgeStep, Overshoot, Column) | Unitario | — | `policyTest` | Caída segura, despegue, aterrizaje, no reutilizar fuente inválida |
| `NavNeighborPolicyTest` | Unitario | 16 | `policyTest` | 8 vecinos + `(0,0)`, sin relajar aristas |
| `NavClimbPolicyTest`, `NavClimbStepPolicyTest` | Unitario | — | `policyTest` | Escalón/escalera y cadencia |
| `TunnelActionPlannerTest`, `TunnelExecutionPolicyTest`, `TunnelPlanPolicyTest` | Unitario | 20/31/25 | `policyTest` | Túnel subordinado: fases, cursor, topes |
| `TraversalActionLeasePolicyTest`, `TraversalActionDispatchPolicyTest` | Unitario | 23/6 | `policyTest` | Un solo lease/acción por tick |
| `NavPlannerMetricsTest` | Unitario | 68 | `policyTest` | I390: métricas exactas, no-op OFF, memoria acotada, null/ids raros |
| `NavSearchBaselineTest` | Unitario (A* real) | 119 | `policyTest` / `navBaselineTest` | I390: determinismo, no-regresión ON/OFF, línea base |
| `NavPlannerStateTest`, `NavRouteNodePolicyTest`, `NavGoalMetricTest` | Unitario | — | `policyTest` | Estado del planner, nodo de ruta, métrica de meta |
| `NavRecovery*`, `ObstructionRecoveryPolicyTest`, `NavSnap*` | Unitario | — | `policyTest` | Recuperación acotada, snap con dueño, no re-entrar |
| `MissionDestinationTest`, `NavArrivalPolicyTest` | Unitario | — | `policyTest` | Destino/meta de misión y llegada exacta |

### 2.3 Asedio, guardias y generación

| Prueba | Nivel | Gate | Invariante / qué protege |
|---|---|---|---|
| `SiegeWaveStateTest`, `WorldSiegePolicyTest`, `RaidTacticalBoardTest` | Unitario | `policyTest` | Estado de oleada/asedio, sin recompensa falsa |
| `Forestry*Test` (TreeClaims, ResourceTrace, TaskPolicy/Controller/Planner) | Unitario | `policyTest` | Silvicultura: claims, traza, controlador, planificador |
| `VillageGoalPriorityTest`, `VillageTaskNavigationPolicyTest` | Unitario | `policyTest` | Prioridad de goals y navegación de tareas |
| `GenerationBudgetTest` | Unitario | `policyTest` | Presupuesto de generación acotado |
| `AdvanceUnderFirePolicyTest` | Unitario | `policyTest` | Avance bajo fuego sin romper la misión |
| `TacticalEscapeBudgetTest`, `NegativeDestinationCacheTest` | Unitario | `policyTest` | Presupuestos/negativos acotados |

### 2.4 Runtime

| Escenario | Herramienta | Invariante / criterio | Evidencia aceptada |
|---|---|---|---|
| C4 standalone save/reload | `tools/c4n/lanzar-c4n.ps1` | I387 + 12/12, 4 fundadores, 0 duplicados/huérfanos, identidad, goals, reanudados, `guardado_ok`, invariantes del roster | `build/c4n/c4n-20261124-0250` (**PASS 3/3, cobertura 11/11**) |
| C4 puestos/guardias | mismo arnés | `jobsite-<fase>.txt`, `AL_CARGAR`, máscara de fundadores, equipo | JSON/log del run |
| POI real `ServerLevel`/`PoiManager` | `tools/poi/lanzar-poi-harness.ps1` | 4 fases, tickets, digest vivo==disco, tardío, F1–F4, parcial medido | `build/av/poi-final-20261124-0305` (**POI_PASS 4/4**) |
| Matriz navegación runtime | `tools/nav/lanzar-nav-harness.ps1` | 21 escenarios STEP_UP/DROP/BREAK/BUILD/túnel/terreno/A*/límites | `build/av/nav-final-20261124-0255` (**NAV_PASS 21/21**) |
| Soak multi-día | `tools/soak/lanzar-soak.ps1 -PsoakHarness=true` | 72.000 ticks (3 días de juego), sin jugador; duplicados/huérfanos/inactividad/comida/muertes | `build/soak/soak-f1-table-diag-20261124-1545` (`80c0e76e`): **`SOAK_FAIL`**, comida 56–?, **13 muertes yendo a la taberna**, mesas inalcanzables (`alcanza=NO`) |
| Rendimiento real | `tools/perf/resumir-metricas.ps1` | Agregación literal, `NO_MEDIDO` fuera de medias | `build/av/perf-final/perf-resumen-final.md` |

## 3. Comandos de los gates

```powershell
.\gradlew.bat compileJava compileTestJava --console=plain
.\gradlew.bat test --console=plain                 # policyTest + villagePersistenceTest + POI selftest + nav contract
.\gradlew.bat policyTest --console=plain            # 80 clases registradas
.\gradlew.bat villagePersistenceTest --console=plain
.\gradlew.bat skillCapabilityTest --console=plain
.\gradlew.bat navBaselineTest --console=plain
.\gradlew.bat poiPersistenceSelftest --console=plain
.\gradlew.bat navScenarioContractTest --console=plain
.\gradlew.bat compileJava -Pa2nDiagnostics=true -Pa2nProbeOnly=true --console=plain
python tools\lint_aldea.py --strict
git diff --check
```

Herramientas de evidencia:

```powershell
.\tools\av\diagnose.ps1 -Run c4n-20261124-0250 -ExigirCobertura -FailOnError
.\tools\av\diagnose.ps1 -All
.\tools\av\collect.ps1
.\tools\av\nav-baseline.ps1
```

## 4. Qué NO está cubierto por tests automáticos

- `ServerLevel`/`PoiManager` real en B (lo cubre POI runtime; C4 también abre mundo real).
- Cobertura de `JOB_SITE` 12/12 (el contrato es **11/11 estaciones**; NITWIT no tiene puesto).
- Multientidad/laberintos largos/agua/puertas en navegación; A2 no se reejecutó en esta campaña.
- `GuardHarness` legado (no portado; ver `tools/av/LEEME.md`).
- **Muerte/conversión real de un aldeano** (L1): la semántica del libro está cubierta por el bloque 11 de
  `VillagePersistenceIntegrationTest`, pero el disparador `VillageAgentRegistry.onDeath/onConversion` no se
  ha medido en runtime con una entidad muriendo. C4/POI no matan aldeanos.
- **Soak sin jugador**: `VillageManager.manageNearby` (reposición de aldeanos) no se ejecuta; una baja
  deja la cobertura por debajo de 11/11. El soak **no** demuestra reposición; C4 cubre 12/12/11/11.
- **Soak y hambre**: el run 72k `soak-final-20261124-1120` registró 5 muertes por hambre con comida en
  la despensa (F1 abierto). El arnés reporta `bajas` y `ausentes_fin`; no se declara PASS de
  sostenibilidad alimentaria.
- **Skills**: cubiertas headless por `SkillCapabilityIntegrationTest` (376); **no** se ha ejecutado un
  servidor dedicado con jugador ni la UI de cliente (`SkillScreen`/HUD). La doble ejecución
  cliente/servidor de `onKeyInput` sigue sin revisar. Ver `2026-11-24-P1-SKILLS-SERVER-SAFE-TREE-AND-TESTS.md`.
