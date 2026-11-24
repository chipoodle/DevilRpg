# Auditoría de pendientes — 24-nov-2026

**RUN_ID:** `av-audit-20261124-1006`
**Rama:** `feature/autonomous-village`
**INITIAL_HEAD:** `2d687921cd9841c7a8a6afea3cbc792f3ce14aa7`
**Origen:** `origin/feature/autonomous-village`; la rama local está **50 commits por delante**
(`git rev-list --left-right --count origin/feature/autonomous-village...HEAD` → `0 50`).
**Árbol de trabajo inicial:** limpio salvo los untracked preservados
(`.a2-backups/`, `src/foodTest/`, `tools/a2n/*.partial`, `tools/a2n/consultar-c4.ps1`,
`tools/a2n/lanzar-c4.ps1`, `tools/a5-closure/`).
**Baseline ejecutado:** `gradlew compileJava --offline` → `BUILD SUCCESSFUL`;
`gradlew test --offline` → `BUILD SUCCESSFUL` en 1m46s (525 aserciones de persistencia, 32 de ownership,
39 BUILD, 253 contrato NAV, 15 POI; `policyTest`, `navBaselineTest` 119 y `navScenarioContractTest` verdes).

Este documento es una **auditoría**, no una aceptación. Clasifica lo documentado contra el código y la
evidencia de ejecución. No reescribe informes históricos; los cita.

---

## 1 · Método y regla de evidencia

1. Se leyó, en este orden: `CHATGPT-COLLABORATION-POLICY.md`, `NEXT-SESSION.md` (cabecera actual y
   checkpoints), `ESTADO-ACTUAL.md`, `2026-11-24-CONTINUITY-CHECKPOINT.md`,
   `AUTONOMOUS-VILLAGE-MASTER-PLAN.md`, `2026-11-24-TECHNICAL-CLOSURE-CAMPAIGN.md`,
   `2026-11-23-AUTONOMOUS-VILLAGE-CONSOLIDATION-AUDIT.md`, `TEST-MATRIX.md`,
   `docs/PENDIENTE.md`, `docs/CONTINUAR.md`, `docs/aldea-invariantes.md` (I387–I399) y
   `docs/design-roadmap.md`.
2. Cada afirmación se contrastó con el código actual, no con el informe que la originó.
3. Clasificación usada: **IMPLEMENTED** (código + prueba o runtime), **PARTIALLY_IMPLEMENTED**,
   **IMPLEMENTED_BUT_DEFECTIVE**, **NOT_IMPLEMENTED**, **UNVERIFIED** (existe pero no hay evidencia
   suficiente de su comportamiento).
4. La auditoría no modificó producción. La implementación del objetivo elegido se registra en un
   informe aparte y se asocia a su propio SHA.

---

## 2 · Skills (poderes del jugador)

No existe un backlog canónico de poderes pendientes: `design-roadmap.md` §«Estado de visión»
dice expresamente *«Sin backlog canónico de poderes pendientes; no inventar nombres»*. Por tanto la
auditoría mide **lo implementado**, no un listado de deseos.

| Elemento | Clasificación | Evidencia | Riesgo / nota |
|---|---|---|---|
| Catálogo `SkillEnum` ↔ árbol JSON ↔ `capability` | **IMPLEMENTED** | 33 skills reales (34 entradas con `EMPTY`); `root_complete.json` tiene 33 nodos con `capability` y **0** entradas cruzadas sin pareja en ambos sentidos | Un test automático no lo vigilaba |
| Ejecutores por skill | **IMPLEMENTED** | 23 casos en `SingletonSkillExecutorFactory` + 10 skills `ofMinion` aplicadas en `SoulBear`, `SoulWispArcher` y `MinionPassiveAttributes`; 33/33 cubiertas | Sin tests del subsistema |
| Persistencia de puntos, máximos, costes, recursos e iconos | **IMPLEMENTED** | `PlayerSkillCapabilityImplementation` (NBT + caché + `PlayerSkillTreePayload`) | Serialización Java (`BytesUtil`), no codec NBT; sin test de compatibilidad |
| Compatibilidad de partidas viejas con skills nuevas | **IMPLEMENTED** | `addMissingSkills()`/`mergeMissingEntries()` añaden claves ausentes sin pisar valores (`design-roadmap.md` L404) | **UNVERIFIED**: no hay test que lo demuestre |
| UI del árbol, loadouts y HUD | **IMPLEMENTED** | `SkillScreen`, `SkillWidget`, `SkillsIconHudOverlay`, `rotateSkillSet`, `MAX_SKILL_SETS=3` | No runtime-verificado en esta auditoría |
| Seguridad en servidor dedicado | **IMPLEMENTED_BUT_DEFECTIVE (riesgo)** | `PlayerSkillCapabilityImplementation` es común y construye `ClientSkillBuilderFromJson`, que llama a `Minecraft.getInstance().getResourceManager()`; `Minecraft` es clase de cliente | En un servidor dedicado con jugador, la construcción de la capability puede lanzar `NoClassDefFoundError`/NPE. No hay corrida dedicada con jugador que lo confirme; **no se declara confirmado** |
| Doble ejecución cliente/servidor | **UNVERIFIED** | `ClientModKeyInputEventSubscriber.onKeyInput` envía el payload **y** ejecuta `triggerAction` en cliente; el servidor lo ejecuta al recibirlo. Los efectos de mundo están guardados por `!isClientSide`, pero el consumo de maná/stamina del cliente se sincroniza al servidor | Requiere revisión de intención; no hay defecto reproducible documentado |
| Tests automatizados de skills | **NOT_IMPLEMENTED** | 0 archivos en `src/test`, `src/villageTest`, `src/foodTest` que mencionen `skillsystem`/`SkillEnum` | Es la deuda de verificación más clara del subsistema |

**Conclusión:** las skills están **implementadas** (catálogo, ejecutores, persistencia y UI) pero
**no verificadas automáticamente**. La única deuda funcional concreta es la dependencia de cliente en
una clase común; queda como **riesgo arquitectónico** hasta poder probarla en un servidor dedicado con
jugador. No se reabre ningún poder ni se inventan nombres.

---

## 3 · Navegación autónoma

| Elemento | Clasificación | Evidencia | Pendiente real |
|---|---|---|---|
| A2 (asedio, 8/8 estricto, 3 corridas) | **IMPLEMENTED + runtime aceptado** | `2026-11-22-A2-FINAL-ACCEPTANCE-AND-CLOSURE.md`; 3 corridas `a2n-20261122-054142/094529/100646` | No reabrir sin regresión |
| NAV runtime 21 escenarios | **IMPLEMENTED + runtime aceptado** | `nav-final-20261124-0255` → `NAV_PASS` 21/21; `navScenarioContractTest` 253 | — |
| Combinaciones DROP/BREAK/BUILD (§L) | **PARTIALLY_IMPLEMENTED (tests)** | `NavTerrainMatrixTest` 36; matriz declarada; el informe de consolidación §7 la deja como deuda media | Ampliar matriz, no defecto |
| Multientidad | **UNVERIFIED** | 1 entidad por escenario (`nav-final-...`) | L6 |
| A2 no reejecutado en el cierre | **UNVERIFIED (regresión)** | `TECHNICAL-CLOSURE §9.2` | L8 |
| Exención estrecha del muro (`5ad02088`) | **IMPLEMENTED + revisada** | Consolidación §10: solo banda 60,5–65 y `tunelEspera`; presupuesto intacto fuera de la banda | Se conserva; sin defecto demostrado |
| Full12K | **NOT_AN_INDEPENDENT_TASK** | Consolidación §11: no hay definición distinta de las corridas de 12000 ticks | — |
| Rendimiento (fastutil, 30 ms, 8000) | **UNVERIFIED (mejora)** | A/B semántico 750 rutas idénticas; sin A/B runtime | L7; no declarar ganancia |

---

## 4 · Comportamientos de entidades y aldea

| Elemento | Clasificación | Evidencia | Pendiente real |
|---|---|---|---|
| C1 comida, C2 reparación, C3 workforce, C4 defensa, C5 persistencia | **IMPLEMENTED + runtime aceptado** | Master Plan §E y actas C1–C5; C4 `c4-full8-20261112-2240` | No reabrir |
| 12 puestos y 4 fundadores; C4 11/11 cobertura | **IMPLEMENTED + runtime aceptado** | `c4n-20261124-0250`; `VillageRosterInvariants` | Cobertura ≠ titularidad (I394) |
| Reanudación de profesiones tras reload | **IMPLEMENTED + runtime aceptado** | C4 3 campañas; `reanudados` es señal débil | — |
| Flujos completos de granjero, cocinero, minero, herrero, clérigo, pescador, ganadería, taberna | **PARTIALLY_VERIFIED** | Master Plan §B/§E: la mayoría solo tiene tests de política/ownership, no aceptación end-to-end | D8/C1-style scenarios |
| Reparación con materiales | **IMPLEMENTED + runtime aceptado** | C2 `c2-repair-accounting-r2` | — |
| Construcción nueva general | **NOT_IMPLEMENTED** | Solo `VillagerRepairGoal`; no hay builder general | Fuera de alcance hasta definir |
| Soak multi-día (criterio 8 de cierre) | **NOT_IMPLEMENTED** | Master Plan §H.6; no hay arnés ni corrida `soak` | **Único criterio de cierre del Master Plan sin ejecutar** |
| Muerte del titular del libro de puestos | **IMPLEMENTED_BUT_DEFECTIVE** | `TECHNICAL-CLOSURE §9.8` + código: `VillageSavedData.jobSiteOwners` no tiene hook de muerte; el ticket lo libera vanilla (`Villager.die → releaseAllPois`) pero la entrada del libro queda y `claimJobSite` la rechaza | **L1: estación libre pero reservada para siempre** |
| Conversión del titular (zombi/bruja) | **IMPLEMENTED_BUT_DEFECTIVE (mismo origen)** | `Mob.convertTo` hace `discard()` sin `releaseAllPois` ni evento de muerte; el libro tampoco se entera | Variante del mismo L1 |
| Duplicado con keeper no cargado (L2) | **PARTIALLY_IMPLEMENTED (limitación)** | `reconciliarPuestosDuplicados` limpia memorias sin liberar ticket; F1 `NO_MEDIDO` | L2 |
| Restauración con `yaDeOtro` (L3) | **PARTIALLY_IMPLEMENTED (limitación)** | `restaurarDueniosDelLibro` línea `if (yaDeOtro) continue;` | L3 |
| `OwnerPos` no refrescada (L4) | **PARTIALLY_IMPLEMENTED (limitación)** | Se refresca en cada `claimJobSite` de la reconciliación para aldeanos cargados con `JOB_SITE`; no para dueños sin `JOB_SITE` o fuera del radio | L4, más estrecha de lo que dice el informe |
| Guardias fundadores / A1 | **IMPLEMENTED + runtime aceptado** | 4/4 equipados; A1 de la otra rama no portado (decisión del dueño) | §K.1 |

---

## 5 · Documentación obsoleta o supersedida

No se borra: se clasifica para que una sesión futura no la lea como cola viva.

| Documento | Estado | Motivo |
|---|---|---|
| `ESTADO-ACTUAL.md` §C/§F («0 colocaciones», «construcción NO funciona») | **SUPERSEDIDO** | Es el traspaso del 16-oct; A2 cerró el 22-nov con BUILD/DROP/BREAK runtime. El banner superior ya lo advierte |
| `docs/PENDIENTE.md` cabecera y `docs/CONTINUAR.md` cabecera | **SUPERSEDIDO / DECISIÓN DEL DUEÑO** | La cola viva es `NEXT-SESSION.md`; la decisión de ramas sigue abierta |
| `NEXT-SESSION.md` cuerpo histórico (rondas A2, fases) | **HISTÓRICO PRESERVADO** | La cabecera del 24-nov es el estado vigente |
| `NavFeature` / `-Ddevilrpg.nav.custom` | **ELIMINADO** | Sin referencias en `src/main`; el arnés ya no lo usa |
| `AggressiveZombiePathPlanner.java` | **AUSENTE** | El segundo planificador legacy ya no está en el árbol |
| `GuardHarness` | **NO PORTADO (decisión)** | I391: mundo/centro/mutaciones incompatibles; la instrumentación vive en C4 |
| `02-CUSTOM-BRAIN-VISION`, `03-TERRAIN-GEOMETRY-VISION`, `04-ENEMY-PROGRESSION-FORTRESS-VISION` | **NOT_IMPLEMENTED (fuera de alcance)** | Documentado como visión; no es deuda de esta etapa |

---

## 6 · Deuda técnica y riesgos (verificada en código)

| # | Deuda | Estado en código | Clasificación |
|---|---|---|---|
| L1 | Sin limpieza del libro al morir el titular | Confirmada: no hay hook; `claimJobSite` rechaza al nuevo titular | **DEFECTO DEMOSTRADO** → objetivo de esta sesión |
| L2 | Duplicado con keeper no cargado | `reconciliarPuestosDuplicados` limpia sin liberar ticket | Limitación |
| L3 | `restaurar` con `yaDeOtro` | `continue` explícito | Limitación |
| L4 | `OwnerPos` no refrescada | Parcial: se refresca en `reconciliarPuestosPotenciales` para JOB_SITE cargados | Limitación estrecha |
| L5 | POI sin horas/jugador | Arnés POI ~2 min/fase | Limitación de evidencia |
| L6 | Navegación de una entidad | 21 escenarios | Limitación de evidencia |
| L7 | fastutil sin A/B runtime | A/B semántico 750 rutas | No declarar ganancia |
| L8 | A2 no reejecutado | Cierre técnico §9.2 | Deuda de regresión |
| — | Soak multi-día | Sin arnés | **Criterio de cierre pendiente** |
| — | Política de ramas / A1 | 50 commits por delante; `migration-neoforge-1.21.1` con 6 commits A1 | **Decisión del dueño** |
| — | Skills sin tests y con dependencia de cliente | 33/33 implementadas; `Minecraft` en clase común | **Deuda de verificación + riesgo** |
| — | Documentación obsoleta | Banners presentes; cuerpo histórico | Bajo |

---

## 7 · Priorización

| Prioridad | Objetivo | Motivo |
|---|---|---|
| **P0** | **L1: limpiar el libro de puestos al morir/convertirse el titular** | Defecto demostrado, acotado, con test headless posible y efecto real en autonomía (estación reservada para siempre) |
| P1 | Skills: quitar la dependencia de cliente del árbol y añadir el primer test del subsistema | Riesgo de servidor dedicado + deuda de verificación, alineado con el foco de la sesión |
| P2 | L3 y L2 | Limitaciones del mismo libro; requieren fixtures de reconciliación |
| P3 | Soak multi-día | Criterio de cierre del Master Plan; requiere arnés runtime nuevo |
| P4 | L6/L8, §L, rendimiento | Deuda de evidencia, no bloqueante |
| Decisión | Ramas / A1 / contrato de cierre | Solo el dueño |

**Objetivo seleccionado:** **P0 (L1)**. Criterios de aceptación:

1. El libro elimina **solo** las entradas cuyo titular es el aldeano desaparecido, incluidas sus
   `OwnerPos`, y sigue siendo idempotente.
2. Un aldeano nuevo puede reclamar la estación después de la limpieza (`claimJobSite` devuelve `true`).
3. La limpieza se dispara en **muerte** y en **conversión** (zombi/bruja), no en descarga de chunk.
4. El NBT guardado no resucita las entradas limpiadas; el libro de otros titulares no se toca.
5. Gates: `compileJava`, `test` (incluye `policyTest` y `villagePersistenceTest`), lint estricto y
   `git diff --check` verdes.

---

## 8 · Inventario de pruebas (baseline)

| Gate | Resultado baseline |
|---|---|
| `gradlew compileJava --offline` | BUILD SUCCESSFUL |
| `gradlew test --offline` | BUILD SUCCESSFUL (1m46s); `VILLAGE_PERSISTENCE_INTEGRATION_TEST: OK (525)`; `VILLAGE_JOB_SITE_OWNERSHIP_POLICY_TEST: OK (32)`; `NAV_VERTICAL_BUILD_POLICY_TEST: OK (39)`; `NAV_SCENARIO_CONTRACT_TEST: OK (253)`; POI 15; `policyTest` 80 clases; `NAV_SEARCH_BASELINE_TEST: OK (119)` |
| `lint_aldea.py --strict` | Pendiente en esta fase de auditoría; se ejecuta en la fase de implementación |
| `git diff --check` | Pendiente en esta fase de auditoría |

**Pruebas existentes por área:** persistencia de aldea (525 aserciones, incluye NBT real y
`Villager` real); ownership de puestos (32); navegación (≈80 clases de política + contrato + baseline);
POI (15); aldea/terreno/comida/siege. **Skills: 0.**

---

## 9 · Conclusión de la auditoría

- **No hay defectos abiertos** en A2/C4/NAV según sus actas; las limitaciones están declaradas.
- **Sí hay un defecto funcional demostrado** en el libro de puestos: L1 (muerte/conversión del titular).
- El **único criterio del Master Plan sin ejecutar** es el soak multi-día.
- Las **skills están completas** (33/33) pero sin tests y con una dependencia de cliente en código común.
- La documentación histórica está preservada con banners; la cola viva sigue siendo `NEXT-SESSION.md`.
- Se procede con **L1**; P1 (skills) queda como siguiente objetivo independiente.

---

## 10 · Actualización post-auditoría (misma fecha)

- **L1 RESUELTO** en `7db821ea`: hooks de muerte/conversión, `releaseJobSitesOwnedBy`, política pura y
  pruebas (persistencia **544**, ownership **39**). Informe:
  `2026-11-24-L1-JOB-SITE-LEDGER-DEATH-CLEANUP.md`. Límite: sin runtime de aldeano muriendo.
- **P1 (skills) RESUELTO en su parte ejecutable** en `0a9f8670`: el árbol se lee del classpath (sin
  `Minecraft`), los iconos van contra `BuiltInRegistries.ITEM` y `skillCapabilityTest` aporta **376**
  aserciones dentro de `test`. Informe:
  `2026-11-24-P1-SKILLS-SERVER-SAFE-TREE-AND-TESTS.md`. Límites: sin servidor dedicado con jugador ni UI
  de cliente; doble ejecución cliente/servidor sin revisar.
- **Siguen abiertos:** L2, L4, L5–L8, soak multi-día, §L/rendimiento, decisión de ramas/A1.
- **L3 RESUELTO** en `e9afbdfc`: `planificarRestauracion` (pura) + limpieza de memorias ajenas y
  restauración del titular; ownership **54** (39+15). Informe:
  `2026-11-24-L3-JOB-SITE-RESTORE-OWNER.md`. Límite: sin runtime de dos aldeanos reales.
- **L4 RESUELTO** en `e335d867`: `OwnerPos` se refresca para el dueño cargado; persistencia **553**
  (544+9). Informe: `2026-11-24-L4-JOB-SITE-OWNER-POSITION-REFRESH.md`. Límite: sin runtime de descarga.
- **Soak multi-día IMPLEMENTADO y ejecutado (72.000 ticks)**, pero **NO ACEPTADO**: el arnés encontró y
  se corrigió una asfixia real del ganadero; el run oficial dio 0 duplicados/huérfanos/inactivos pero
  **5 muertes por hambre** en 16 bocas. Nuevo objetivo **F1** (sostenibilidad alimentaria). Informes:
  `2026-11-24-SOAK-MULTIDAY-CAMPAIGN.md` y `2026-11-24-ANIMAL-FARM-SUFFOCATION-FIX.md`.
- **F1 PARCIAL** en `0e57b569`: la ración diaria reduce hambre y el goal de taberna se desatasca; el 72k
  baja a **1 muerte** (`630,63,530`), pero el acceso a la taberna sigue. Informe:
  `2026-11-24-F1-HUNGER-TAVERN-FIX.md`. El soak sigue **NO ACEPTADO**.
- **Siguen abiertos:** L2, L5–L8, **F1 (ruta a la taberna)**, §L/rendimiento, decisión de ramas/A1.
- Las clasificaciones de §2 se mantienen como **foto de la auditoría**; esta sección registra qué se
  corrigió después, sin reescribir los hallazgos.
