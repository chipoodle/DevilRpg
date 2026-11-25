# Checkpoint de continuidad — 24-nov-2026

**Este documento no abre campañas.** Fija el estado de referencia y el protocolo para las próximas sesiones.

## 1. Estado de referencia

| Elemento | Valor |
|---|---|
| Rama | `feature/autonomous-village` |
| HEAD final | `18266502` (docs-only sobre el código probado) |
| SHA de código probado | `1016a7cf` |
| Campaña anterior | **COMPLETE para el alcance autorizado de cierre** |
| C4 | `build/c4n/c4n-20261124-0250` → `C4N_PASS` 3/3, cobertura 11/11 |
| POI | `build/av/poi-final-20261124-0305` → `POI_PASS` 4/4 |
| NAV | `build/av/nav-final-20261124-0255` → `NAV_PASS` 21/21 |
| Tests | `gradlew test` 525/32/39/253/15, política, lint y `git diff --check` verdes |

Las tres campañas runtime se ejecutaron con SHA `1016a7cf`. `HEAD 18266502` solo añade documentación.
No se debe mezclar “HEAD documental” con “SHA probado”.

Commits de cierre: `419dcf3b`, `1016a7cf`, `d277ca90`, `18266502`.
Evidencias: `build/c4n/**`, `build/av/**`; no se borran.

## 2. Limitaciones de deuda técnica

No son defectos abiertos ni autorizan una campaña por sí mismas.

| # | Limitación | Evidencia actual | Riesgo | Prueba futura posible |
|---|---|---|---|---|
| L1 | Sin limpieza automática del libro al morir el propietario; la estación puede quedar reservada hasta `forgetJobSite`. **RESUELTO 24-nov (`7db821ea`)**: hooks de muerte y conversión + `releaseJobSitesOwnedBy`; ver `2026-11-24-L1-JOB-SITE-LEDGER-DEATH-CLEANUP.md`. | `cargarDueniosDePuestos` no borra vivos; ahora `VillageAgentRegistry.onDeath`/`onConversion` limpian el libro. C4/POI no matan aldeanos. | Estación libre pero reservada para datos históricos anteriores al fix; el runtime de muerte real no se midió. | Test de muerte + recarga: el libro debe limpiarse y la estación reclamarse (semántica cubierta por `VillagePersistenceIntegrationTest` bloque 11; falta runtime de entidad). |
| L2 | Duplicado con keeper no cargado: se limpian memorias cargadas sin liberar ticket; F1 lo declara `NO_MEDIDO`. | `reconciliarPuestosDuplicados`, `poi-final-20261124-0305` F1. | Posible JOB sin ticket si el borrado era el titular. | Fixture F1 con keeper en chunk no cargado y comprobación de ticket/ghost. |
| L3 | `restaurar` con `yaDeOtro` no restaura al dueño del libro si otro aldeano cargado tiene el JOB. **RESUELTO 24-nov (`e9afbdfc`)**: `planificarRestauracion` limpia la memoria ajena y devuelve el puesto al titular; ver `2026-11-24-L3-JOB-SITE-RESTORE-OWNER.md`. | `restaurarDueniosDelLibro` ya no se rinde ante `yaDeOtro`. | El titular puede quedarse sin estación hasta que otro la suelte (datos históricos/ejecución runtime no medida). | Política pura 54 aserciones; falta fixture runtime de dos aldeanos reales. |
| L4 | `OwnerPos` no se refresca al moverse el propietario. **RESUELTO 24-nov (`e335d867`)**: `cargarDueniosDePuestos` refresca al dueño cargado; ver `2026-11-24-L4-JOB-SITE-OWNER-POSITION-REFRESH.md`. | `claimJobSite(..., ownerPos)` se llamaba solo al reclamar; ahora también en el latido de aldea si el dueño está cargado. | Sin pérdida de dueño; el refresco es cada 10 s, no cada tick. | Persistencia 553 aserciones; falta runtime de alejamiento/descarga. |
| L5 | POI no mide horas de chunks descargados ni jugador conectado. | `POI` dura ~2 min/fase, sin jugador. | Comportamiento a largo plazo no demostrado. | Campaña POI con ciclos largos y jugador conectado. |
| L6 | Navegación runtime con una entidad por escenario. | `nav-final-20261124-0255`, 21 escenarios. | No cubre multientidad, laberintos largos, combate, agua ni puertas. | Matriz multientidad y escenarios compuestos. |
| L7 | fastutil sin A/B runtime. | A/B sintético 750 rutas idénticas; campaña real sin baseline anterior. | Ganancia runtime no cuantificada; no se declara. | A/B de 6+ ejecuciones por brazo, mismo mundo y rutas. |
| L8 | A2 no se reejecutó en el cierre. | Excluido expresamente por el usuario; C4/NAV cubren el camino actual. | Regresión A2 no medida. | Campaña A2 acotada solo si el análisis de dependencias la justifica. |
| **F1** | **Hambre a 3 días.** **CERRADO (hambre)** en `fa6a63f4`: ración real al rendirse el goal de taberna; 72k del mismo mundo con **0 muertes por hambre** (antes 8, 7 de hambre), población 29, comida 88, 11/11, 0 huérfanos/sin puesto/inactivos. | `build/soak/soak-f1-fallback-20261124-1755`; `2026-11-24-F1-CLOSURE-FALLBACK-RATION.md`. | El criterio de hambre se cumple; queda la caída F2. | — (cerrado) |
| **F2** | **Caída al volver al pueblo** (`Zacarias`, `hit the ground too hard`, `729,64,542`). **CERRADO** con `VillageFallPolicy` (`ccee1943`): miembros del pueblo sin daño por caída fuera de asedio; ración a hambre crítica (`7e2b4293`). `soak-f2-final`: **0 muertes**. | `build/soak/soak-f2-final-20261124-1825`; `2026-11-24-F2-FALL-PROTECTION.md`. | El criterio de caída/hambre se cumple; queda F3. | — (cerrado) |
| **F3** | **JOB_SITE fuera del muro**: el diagnóstico `-PoiDiag` encontró un **granjero** (`a3dd7940…`) con su composter en `736,69,537`, **fuera de la muralla** (POI del oficio, por eso se aceptaba). **CERRADO** con `bfd5113b`: todo `JOB_SITE` fuera del muro es inválido y se suelta; `soak-f3-outside` da **`SOAK_PASS`**, **0 muertes**, **11/11 desde la primera muestra**, 0 huérfanos/sin puesto. | `build/soak/soak-f3-outside-20261124-2030`; `2026-11-24-SOAK-ACCEPTANCE.md`, `2026-11-24-F3-JOB-SITE-POI.md`. | **Aceptado.** | — (cerrado) |
| **F4** | **Inactivo permanente sin goal**: en la semilla `7454…`, un granjero (`3bce318d…`) quedó **12.000 ticks** parado en `629,63,528` sin faena; el empujón I198 solo cubría a quien ya tenía goal. **CERRADO** con `bcf98f70` (`despertarSiEstaParado`): `soak-f4-stability` da **`SOAK_PASS`**, 0 inactivos permanentes. | `build/soak/soak-f4-stability-20261124-2115`; `2026-11-24-F4-IDLE-RECOVERY.md`. | Estable en dos semillas. | — (cerrado) |
| **L2** | **Duplicado con el titular no cargado.** `limpiarDuplicados` + **59** aserciones de ownership: con el titular ausente se limpian las memorias cargadas y **NO se libera el ticket** (es suyo). | `VillageJobSiteOwnershipPolicyTest` 59; `2026-11-24-L2-DUPLICATE-KEEPER-POLICY.md`. | Política cerrada; falta un fixture runtime con titular descargado (limitación declarada). | — (política) |
| **L5/L6** | **Evidencia POI/navegación multi-entidad.** Soak de **30 aldeanos/72k** en dos semillas (0 muertes, 11/11, 0 inactivos permanentes) y **3 días de POI** sin duplicados/huérfanos. El arnés NAV sigue sin `multientidad_*` y el POI sin horas/jugador. | `2026-11-24-L5-L6-NAV-POI-EVIDENCE.md`. | Parcialmente cerradas; limitación de arnés declarada. | Implementar `multientidad_1/2/4/8` y la ventana horas+jugador si se quiere el contrato exacto. |
| **SK** | **Skills runtime: humano→lobo a 0 maná, payload directo abierto y autoridad de maná/stamina.** (1) 0-maná ya no convierte humano en lobo (`WerewolfManaPolicy`); (2) el payload directo solo ejecuta `TRANSFORM_WEREWOLF` con nivel (`DirectSkillExecutionPolicy`); (3) maná/stamina son **autoridad del servidor** (`playToClient`, SERVERBOUND ignorado, regeneración propia con `PlayerResourceRegenPolicy`). **Skills 387.** | `2026-11-24-SKILLS-RUNTIME-WEREWOLF-MANA.md`, `2026-11-24-SKILLS-DIRECT-PAYLOAD-POLICY.md`, `2026-11-24-MANA-STAMINA-SERVER-AUTHORITY.md`; `SKILL_CAPABILITY_INTEGRATION_TEST` 387. | Defectos corregidos; cliente real (UI/HUD/`onKeyInput`) sigue pendiente. | Ejecutar servidor+cliente real y revisar la doble ejecución de `onKeyInput`. |
| **L9** | **4 payloads de acción siguen aceptando NBT del cliente**: skill tree, experiencia, auxiliar (transformación) y minions. Deben pasar a **acciones validadas** (puntos/nivel/propiedad/cupo), no a `deserializeNBT` del estado completo. | `2026-11-24-MANA-STAMINA-SERVER-AUTHORITY.md` §4. | Abierta; maná/stamina ya cerrados. | Diseñar payloads de acción validados en servidor y probar. |
| **SOAK** | **Aceptación multi-día + estabilidad:** mismo SHA `bcf98f70` en dos semillas (`3188…` y `7454…`), ambas **PASS**: 0 muertes, 11/11, 0 duplicados/huérfanos/sin puesto/inactivos permanentes, guardado OK. | `soak-f4-confirm-20261124-2130`, `soak-f4-stability-20261124-2115`. | **`SOAK_PASS` estable en dos semillas** | Después L2/L5–L8/skills runtime. |

## 3. Protocolo de continuidad

### Regla 1 — Un objetivo principal por campaña

Cada campaña debe declarar: objetivo, alcance, subsistemas/archivos, criterios de aceptación, pruebas, límite de
iteraciones/tiempo, condiciones de parada y entregables. Las tareas auxiliares solo se aceptan si son dependencias
directas. No se añaden objetivos independientes por iniciativa del agente.

### Regla 2 — Autonomía dentro del alcance

El agente puede analizar, diseñar, implementar, corregir, compilar, probar, revisar y repetir sin pedir permiso por
cada cambio reversible que esté dentro del alcance aprobado.

### Regla 3 — Bucles delimitados

`ANALIZAR → IMPLEMENTAR → COMPILAR → PROBAR → DIAGNOSTICAR → CORREGIR → REPETIR`.

Predeterminado: **hasta tres ciclos** de diagnóstico/corrección por defecto. Si no se resuelve, conservar evidencias
y presentar alternativas; no ampliar alcance ni lanzar campañas runtime adicionales fuera de los criterios.
Un bloqueo en una tarea no impide avanzar en otra tarea ya autorizada.

### Regla 4 — Equipos multiagente

Cuando el objetivo lo justifique: arquitectos, programadores, testers y revisores independientes. Usar
`qwen3.5:9b` (y `qwen3.8:27b` si está disponible) como apoyo, verificando sus propuestas. No crear agentes
innecesarios para tareas pequeñas. El coordinador revisa la integración.

### Regla 5 — Revisión independiente

Los cambios importantes los revisa un agente distinto del implementador, buscando regresiones, persistencia,
duplicación de responsabilidades, invariantes, pruebas insuficientes, resultados artificiales y cambios innecesarios.

### Regla 6 — Evidencia por nivel y SHA

Distinguir PASS de compilación, tests, políticas, integración, runtime y aceptación. Cada resultado se asocia al
SHA exacto. No se declara un comportamiento validado si solo compila.

### Regla 7 — Parada

Al completar: revisar criterios, ejecutar pruebas, revisar cambios, commit local si está autorizado, actualizar
checkpoints, entregar informe y detenerse. No iniciar otra campaña automáticamente.

## 4. Protección del proyecto

- No push, merge externo, force push ni cambio de rama sin autorización.
- No `reset --hard`, no `clean`, no borrados masivos.
- No eliminar mundos, logs, respaldos ni evidencias.
- No sobrescribir trabajo ajeno.
- Preservar `.a2-backups/`, `src/foodTest/`, `tools/a2n/`, `tools/a5-closure/` y artefactos diagnósticos.
- A2 y C4 siguen **ACCEPTED**; no reabrirlos por hipótesis.

## 5. Documentos de continuación

- `NEXT-SESSION.md` — handoff y estado inmediato.
- `ESTADO-ACTUAL.md` — estado resumido.
- `AUTONOMOUS-VILLAGE-MASTER-PLAN.md` — hoja de ruta.
- `TEST-MATRIX.md` — qué prueba cada gate.
- `aldea-invariantes.md` — invariantes I1–I399.
- `2026-11-24-TECHNICAL-CLOSURE-CAMPAIGN.md` — informe de cierre y evidencias.
- Este checkpoint — protocolo y deuda técnica.

**Fin del checkpoint. No iniciar desarrollo desde este documento.**

## 6. Actualización 24-nov-2026 (posterior al cierre)

La auditoría `2026-11-24-PENDING-AUDIT.md` (`2b32ba08`) clasificó los pendientes y eligió **L1** como
primer objetivo: el libro de puestos no se limpiaba al morir o convertirse el titular. Se corrigió en
`7db821ea` con hooks en `VillageAgentRegistry` y pruebas headless (persistencia **544**, ownership
**39**); `test`, lint y `git diff --check` verdes. El límite está declarado: **no hay corrida runtime de
un aldeano muriendo**. L2, L3, L4, L5–L8 y el soak multi-día siguen abiertos. Informe:
`2026-11-24-L1-JOB-SITE-LEDGER-DEATH-CLEANUP.md`.

**P1 (skills) cerrado en su parte ejecutable (`0a9f8670`).** El árbol de skills ya no usa `Minecraft`
(lectura por classpath) y los iconos se resuelven contra `BuiltInRegistries.ITEM`; nuevo
`skillCapabilityTest` dentro de `test` (**376** aserciones: 33/33 skills, 36 nodos, NBT, compatibilidad
de partidas viejas). Límites: **sin servidor dedicado con jugador ni UI de cliente reejecutada**; el
resource pack ya no sobrescribe el árbol; la doble ejecución cliente/servidor sigue sin revisar. Informe:
`2026-11-24-P1-SKILLS-SERVER-SAFE-TREE-AND-TESTS.md`.

**L3 corregido a nivel de decisión y cableado (`e9afbdfc`).** `restaurarDueniosDelLibro` ya no se rinde
ante `yaDeOtro`: la política pura `planificarRestauracion` devuelve el puesto al titular del libro y
ordena limpiar las memorias de los aldeanos cargados que no lo son, sin soltar el ticket. Ownership
**54** aserciones (+15). **Límite:** sin fixture runtime de dos aldeanos reales; C4/POI no reejecutados.
Informe: `2026-11-24-L3-JOB-SITE-RESTORE-OWNER.md`.

**L4 corregido (`e335d867`).** `cargarDueniosDePuestos` refresca `OwnerPos` para el dueño cargado, vivo
y no removido (mismo titular, sin tocar tickets); si no está cargado, se conserva la carga por la última
posición. Persistencia **553** aserciones (+9). **Límite:** sin runtime de alejamiento/descarga.
Informe: `2026-11-24-L4-JOB-SITE-OWNER-POSITION-REFRESH.md`.

**Soak multi-día (24-nov, `552e9b57`).** Nuevo arnés `tools/soak` (72k ticks, sprint, mundo desechable,
sin jugador). Encontró una **asfixia real del ganadero** mientras `Recogiendo el corral`; corregida con
`desatascarSiEstaEncajado` en cada tick del `VillageAnimalFarmGoal`. El run oficial 72k dio **0
duplicados, 0 huérfanos, 0 inactivos permanentes**, pero **5 muertes por hambre** (población 16→11,
cobertura 8/11, comida 6–77, inicial 20). **No se acepta el soak**: la sostenibilidad alimentaria a 16
bocas es el objetivo abierto **F1**; el arnés sin jugador no ejecuta `manageNearby` (reposición).
Informes: `2026-11-24-SOAK-MULTIDAY-CAMPAIGN.md` y `2026-11-24-ANIMAL-FARM-SUFFOCATION-FIX.md`.

**F1 parcial (`0e57b569`).** La ración diaria ahora reduce el hambre (25/punto) y
`VillagerTavernGoal` se desatasca cada tick. El 72k baja de **5 a 1 muerte** (Zenobia, granjero, en
`630,63,530`), población 15, cobertura 10/11, 0 duplicados/huérfanos/inactivos, guardado OK. El choke de
acceso a la taberna sigue: F1 **no cerrado**; siguiente objetivo, ruta/puerta de la taberna y 72k con 0
bajas. Informe: `2026-11-24-F1-HUNGER-TAVERN-FIX.md`.

**F1 abierto: despensa 256 y mesa por aldeano (`552ecfc2`/`80c0e76e`).** El segundo mundo se hundió con
20 puntos (29 muertes, comida 0). La remesa de **64 panes = 256 puntos** eliminó el colapso alimentario
(comida 160–284), pero seguían muriendo **16** `Yendo a la taberna`; la causa era que
`usandoDespensa=true` mandaba a todos al mismo punto de apoyo. Con **mesa por aldeano** bajaron a **9**
(población 21, comida 124–284); el diagnóstico con semilla y `-Diag` dio **13** y capturó la causa:
las mesas (`dark_oak_fence`+`oak_pressure_plate`) dan `ruta=6 nodos … alcanza=NO` y el aldeano muere en
la puerta abierta `703,63,553` con comida 56. **Es geometría/acceso de la taberna**, no hambre. F1 **no
cerrado**; siguiente: corregir ruta/mesa/puerta y 72k con 0 muertes. Informes:
`2026-11-24-F1-FOOD-COLLAPSE-AND-INITIAL-STOCK.md` y `2026-11-24-F1-TAVERN-TABLE-DESTINATION.md`.

**F1 abierto: entrada a la taberna (waypoint oeste) (`39b5bf53`/`2ebf8ef4`).** Las mesas no tenían
casilla de pie alcanzable (`alcanza=NO`); se añadió una entrada exterior por la puerta oeste. El waypoint
oeste+sur movió las muertes al sur (`701,581`, 10 bajas); solo oeste bajó a **8** (población 22, comida
164, 11/11 final) pero las bajas siguen en `709-728,63,541-578`. **El choke es la ruta/terreno del este**,
no la puerta ni la despensa. F1 **no cerrado**; siguiente: ruta/terreno (plaza/road waypoints o
navegación). Informe: `2026-11-24-F1-TAVERN-ENTRANCE-WAYPOINT.md`.

**Waypoint de plaza: regresión revertida (`f375446b` → `af711875`).** El 12k con plaza dio 0 muertes,
pero el 72k mató a **30** (población 0, comida 268–292) en `599,63,519` y centro-oeste; el tramo de plaza
mandó a los aldeanos al choke del oeste. Se revirtió. El mejor código vigente es la entrada oeste
(`2ebf8ef4`, 8 muertes). El 12k con plaza también destapó un **fletcher** (`c7230316…`) sin POI válido
en `610,63,508` (`sin_puesto=1`, 49 muestras) con población 30: defecto de **job site/POI**, no de
hambre. Informe: `2026-11-24-F1-PLAZA-WAYPOINT-REGRESSION.md`.

**F1 cerrado (hambre) con la ración al rendirse (`fa6a63f4`).** `VillagerTavernGoal`, al alcanzar
`stuckTicks >= STUCK_LIMIT`, da una **ración real en el sitio** (`comer(level,false)`: 1 punto de
despensa, -25 hambre, mismo `tieneHambre` de 3 min) si hay comida. `soak-f1-fallback` (72k, misma
semilla): **0 muertes por hambre** (antes 8, 7 de hambre), población 29, comida 88, 11/11, 0
huérfanos/sin puesto/inactivos, 45 raciones de rendición, guardado OK. La única baja es la **caída F2**
(`729,64,542`). Informe: `2026-11-24-F1-CLOSURE-FALLBACK-RATION.md`.

**F2 cerrado: caída protegida y ración a hambre crítica (`ccee1943`/`7e2b4293`).** `VillageFallPolicy`
cancela el daño por caída a los miembros del pueblo **fuera de asedio**; la ración a `CRITICAL` cubre
a los que la ruta reportaba como “avanzando”. `soak-f2-final` (72k, misma semilla): **0 muertes
totales** (0 hambre, 0 caída), población 30, comida 72; el único fallo es **F3** (1 fletcher sin POI,
333 muestras). El 12k de la misma semilla pasa 11/11. Informe:
`2026-11-24-F2-FALL-PROTECTION.md`.

**F3 abierta: POI del fletcher (`743b81db`/`68741d29`).** El 72k `soak-f3-periodic` tiene **0
muertes** (0 hambre/0 caída), población 30, comida 100 y **11/11 al final**; el POI de la mesa de
flechas (610,63,508) falta durante **241 muestras iniciales** (148–333 según run) y hay 1 huérfano
transitorio. El 12k de la misma semilla pasa limpio. El re-registro/reemplazo periódico deja el final
en 11/11 y reduce el hueco, pero no lo cierra: siguiente, 72k `-Diag` para `SOAK_POI_INVALID` y
corregir el `JOB_SITE`/censo. Informe: `2026-11-24-F3-JOB-SITE-POI.md`.

**SOAK ACEPTADO en `bfd5113b`.** `soak-f3-outside-20261124-2030` (72k) da **`SOAK_PASS`**: **0
muertes**, población 30, **11/11 desde la primera muestra**, 0 duplicados/huérfanos/sin puesto/
inactivos permanentes, comida 60, guardado OK. La causa de F3 era un **granjero con el composter en
`736,69,537`, fuera de la muralla**; el POI era del oficio y `puestoDeTrabajoInvalido` solo miraba el
tipo dentro del muro. Ahora todo `JOB_SITE` fuera del muro es inválido y se suelta; el granjero
reclama una estación de dentro. Informe: `2026-11-24-SOAK-ACCEPTANCE.md`.

**F4 cerrada y soak estable en dos semillas (`bcf98f70`).** En la semilla `7454…` un granjero quedó
12.000 ticks parado sin goal; `despertarSiEstaParado` (30 s sin moverse, oficio del pueblo, sin goal)
le pide camino a su puesto. `soak-f4-stability` (72k): **`SOAK_PASS`**, 0 muertes, 11/11, 0 inactivos
permanentes, comida 164. La semilla `3188…` ya era PASS en `bfd5113b`; `soak-f4-confirm` comprueba el
mismo SHA en esa semilla y también da **`SOAK_PASS`** (0 muertes, 11/11, 0 inactivos, comida 108).
Informe: `2026-11-24-F4-IDLE-RECOVERY.md`. **Soak estable en dos semillas con `bcf98f70`.**

**L2 cerrada (política).** `limpiarDuplicados` (ownership **59** aserciones) fija que, con el titular
no cargado, se limpian las memorias duplicadas cargadas y **NO se libera el ticket** (pertenece al
ausente). `reconciliarPuestosDuplicados` usa la decisión y lo registra. Falta un fixture runtime con
titular descargado; limitación declarada. Informe: `2026-11-24-L2-DUPLICATE-KEEPER-POLICY.md`.

**Skills runtime: bug del hombre lobo a 0 maná (`161bc4f8`).** El cliente mandaba
`DirectSkillExecutionPayload(TRANSFORM_WEREWOLF)` cada 10 ticks con maná 0 **sin mirar si estaba
transformado**; `SkillShapeshiftWerewolf` alterna y el payload directo no consume recursos, así que un
humano podía quedar convertido. `WerewolfManaPolicy` exige `transformado && mana<=0 && tick%20==0`;
skills **380** aserciones. Cliente real (UI/HUD/`onKeyInput`) sigue pendiente. Informe:
`2026-11-24-SKILLS-RUNTIME-WEREWOLF-MANA.md`.

**Skills runtime: el payload directo ejecutaba cualquier skill (`a06c906d`).**
`DirectSkillExecutionPayload.handle` ejecutaba el `SkillEnum` que mandara el cliente sin nivel, coste,
cooldown ni precondiciones. `DirectSkillExecutionPolicy` solo permite `TRANSFORM_WEREWOLF` con nivel
>0; el handler lee la capability y rechaza el resto. Skills **384** aserciones. Informe:
`2026-11-24-SKILLS-DIRECT-PAYLOAD-POLICY.md`.

**Recursos: maná/stamina pasan a autoridad del servidor (`f231923a`).** El cliente enviaba su NBT en
cada `setMana`/regeneración; ahora solo el servidor manda S→C (`playToClient`), la rama SERVERBOUND se
ignora y `PlayerCapabilityForgeEventSubscriber` regenera en servidor con `PlayerResourceRegenPolicy`.
Skills **387**. **L9 abierta:** los otros 4 payloads (skill tree, experiencia, auxiliar, minions)
siguen aceptando NBT del cliente; deben ser acciones validadas. Informe:
`2026-11-24-MANA-STAMINA-SERVER-AUTHORITY.md`.

**L5/L6 parcialmente cerradas con el soak.** 30 aldeanos navegando 72k en dos semillas (0 muertes,
11/11, 0 inactivos permanentes) y 3 días de POI sin duplicados/huérfanos: evidencia runtime fuerte de
multi-entidad y propiedad. El arnés NAV sigue marcando `multientidad_1/2/4/8` como no soportados y el
POI no mide horas/jugador; la limitación se declara. Informe:
`2026-11-24-L5-L6-NAV-POI-EVIDENCE.md`.
