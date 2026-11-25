# ESTADO ACTUAL — Handoff de sesión (navegación autónoma A2)

> **ACTUALIZACIÓN 25-nov-2026 — LOGS.** INFO solo para estado/medición; el ruido periódico
> (guardias, portones, huerta, camas, casas, cosecha, árboles, diagnósticos de asedio y minions)
> pasa a DEBUG. WARN/ERROR intactos. Requiere reiniciar.

> **ACTUALIZACIÓN 25-nov-2026 — RENDIMIENTO.** Tres picos de ~2 s: login/generación, oleada de 8
> asediadores en un tick y guardado. No es un bucle. Se eliminó el escaneo/log por tick del leñador
> sin faena y se espació la remesa de harina; el pico de la oleada queda pendiente de repartir.

> **ACTUALIZACIÓN 25-nov-2026 — LEÑADOR.** Ya no reclama un árbol de fuera y se rinde: si el objetivo
> está fuera y él dentro, `pasoDeCamino` devuelve primero el portón. Si no hay harina en
> despensa/almacén, vacía un **compostero lleno** para abonar la arboleda. Además el almacén recibe
> **64 de harina iniciales** (también en aldeas ya generadas). Runtime pendiente.

> **ACTUALIZACIÓN 25-nov-2026 — SPAWN.** El tiempo de amenaza es **por perfil** (`SpawnScaleProfile`;
  3 h en agresivo/vex), no solo global. El `13` es `minHardDistance`, no tiempo. La `AggressiveZombieSpawnRule`
  funciona: en el log spawnearon 3 a las 01:14; la probabilidad era 1–17 % por la curva 67→3000.

> **ACTUALIZACIÓN 25-nov-2026 — ASEDIO/CAMPANA.** `Occupied [0,1]` y `Resolved [0,1]` con `Fallen []`:
> la campana rota no hacía caer la aldea. `VillageSiegeBellPolicy` resuelve **caída inmediata** en
> asedio clásico y horda; `reconciliarAldeasOcupadas` corrige partidas viejas. **25 aserciones**;
> runtime pendiente. La partida actual pasará ambas aldeas a caídas al reiniciar.

> **ACTUALIZACIÓN 25-nov-2026 — CAMPO DE FUERZA.** El guardado tenía `Resolved [0,1]`; el campo
> estaba activo. El agujero era la lista cerrada de `MobSpawnType` en `EntityJoinLevelEvent`.
> `VillageSpawnGuard` cancela todo hostil dentro salvo huevo/`/summon`/`MOB_SUMMONED`; vanilla se
> expulsa al siguiente latido. **Política 22 aserciones; runtime pendiente de noche de prueba.**

> **ACTUALIZACIÓN 25-nov-2026 — SKILLS EN SERVIDOR DEDICADO.** `56454c63`: `SkillDisplayInfo` sin
> `@OnlyIn(CLIENT)`; el servidor dedicado construye la capability y los ejecutores
> (**`SKILL_SERVER_HARNESS OK checks=25`**). Árbol saneado (`Minions`/`AttributeModifier` del
> servidor), `SkillCastPolicy` excluye las 4 activas de minion de la barra del jugador, pasivos
> siguen fuera de la barra. **Skills 416**. La brecha del zombie tiene un candidato sin commit.

> **OBJETIVO CERRADO (24-nov-2026).** Soak final en `43afe1e6`:
> **`SOAK_PASS`** (72k, 0 muertes, 11/11, 0 huérfanos, 0 inactivos permanentes, guardado OK).
> **F1–F4, L2 y L9 cerrados**; skills **392**; soak previamente estable en dos semillas con
> `bcf98f70`. Quedan como **límites declarados** L5/L6 (arnés exacto), L7 (A/B), L8 (excluido por el
> usuario), cliente real de skills y la decisión A1/ramas del dueño. Informe:
> `2026-11-24-AUTONOMOUS-OBJECTIVE-CLOSURE.md`.

> **ACTUALIZACIÓN 24-nov-2026 — SOAK ESTABLE, SKILLS Y L9 CERRADOS.** Soak `bcf98f70`: **`SOAK_PASS`**
> en `3188…` y `7454…`. **F1–F4 cerrados; L2 política cerrada; L9 cerrada:** experiencia S→C y árbol de
> skills validado en servidor (`SkillTreeSpendPolicy`), pasivo con nivel. Maná/stamina/auxiliar/minions
> ya son autoridad del servidor. **Skills 392.** Queda L7, L8 (excluido), cliente real y ramas/A1.
> Informes: `2026-11-24-SOAK-ACCEPTANCE.md`, `2026-11-24-L9-SKILL-TREE-VALIDATED.md`.




> **ACTUALIZACIÓN 24-nov-2026 — SOAK ACEPTADO EN `bfd5113b`.** `soak-f3-outside` (72k): **`SOAK_PASS`**,
> **0 muertes**, población 30, **11/11 desde la primera muestra**, 0 duplicados/huérfanos/sin
> puesto/inactivos permanentes, comida 60, guardado OK. **F1 (hambre), F2 (caída) y F3 (JOB_SITE fuera
> del muro) cerrados.** La causa de F3 era un granjero con el composter en `736,69,537`, fuera de la
> muralla; `bfd5113b` invalida los `JOB_SITE` fuera del muro. Siguiente: repetir 72k en otra semilla
> para estabilidad y atacar L2/L5–L8/skills runtime. Informe: `2026-11-24-SOAK-ACCEPTANCE.md`.

> **ACTUALIZACIÓN 24-nov-2026 — F1 Y F2 CERRADOS; F3 (PUESTO DE FLETCHER) INTERMITENTE.** Código
> `7e2b4293`. F1 (hambre): ración real al rendirse y a hambre crítica. F2 (caída): `VillageFallPolicy`
> protege a los miembros del pueblo **fuera de asedio**. El 72k `soak-f3-periodic` (`68741d29`) dio
> **0 muertes totales** (0 hambre, 0 caída), población 30, comida 100 y **11/11 al final**; el único
> fallo es **F3**: 1 fletcher sin POI durante 241 muestras iniciales (148–333 según run) y 1 huérfano
> transitorio. El 12k de la misma semilla pasa 11/11. **F1/F2 cerrados; F3 abierta**, soak global **NO
> ACEPTADO** por cobertura inicial. Informe: `2026-11-24-F3-JOB-SITE-POI.md`.

> **ACTUALIZACIÓN 24-nov-2026 — F1 (HAMBRE) CERRADO; F2 (CAÍDA) ABIERTA.** Código `fa6a63f4`. Al
> rendirse el goal de taberna, el aldeano recibe una **ración real en el sitio** (1 punto de despensa,
> -25 hambre, respeta los 3 min). El 72k del mundo que moría da **1 muerte y 0 de hambre** (antes 8,
> 7 de hambre), población 29, comida 88, 11/11, 0 huérfanos/sin puesto/inactivos, guardado OK. La única
> baja es una **caída** en `729,64,542` (“Volviendo al pueblo”): **F2**, siguiente objetivo. El soak
> global sigue `SOAK_PASS_CON_BAJAS` y **NO ACEPTADO**. Informe:
> `2026-11-24-F1-CLOSURE-FALLBACK-RATION.md`.

> **ACTUALIZACIÓN 24-nov-2026 — F1 ABIERTO: COMIDA Y MESA CORREGIDAS, ACCESO A LA TABERNA PENDIENTE.**
> Código `80c0e76e`. Tres correcciones: ración que reduce hambre, desatasco por tick, **remesa inicial 256
> puntos** y **mesa por aldeano** (antes todos al mismo punto de apoyo) y **entrada exterior por la
> puerta oeste**. El 72k del mundo que colapsaba bajó de **29 → 16 → 9 → 8**; el diagnóstico con semilla
> y `-Diag` capturó las mesas inalcanzables (`ruta=6 nodos … alcanza=NO`) y la variante oeste redujo de
> 13 a 8; un waypoint de **plaza** posterior mató a **30** (población 0, comida 292) y fue **revertido**
> (`af711875`). Las bajas siguen en `709-728,63,541-578` con comida 164: el choke es la **ruta/terreno
> del este**, no la despensa ni la puerta. F1 **no cerrado**; soak **NO ACEPTADO**. Informes:
> `2026-11-24-F1-FOOD-COLLAPSE-AND-INITIAL-STOCK.md`,
> `2026-11-24-F1-TAVERN-TABLE-DESTINATION.md`, `2026-11-24-F1-TAVERN-ENTRANCE-WAYPOINT.md` y
> `2026-11-24-F1-PLAZA-WAYPOINT-REGRESSION.md`. Siguiente: ruta/terreno (sin plaza) y POI del fletcher.

> **ACTUALIZACIÓN 24-nov-2026 — F1 PARCIAL.** Código probado `0e57b569`. La ración diaria ya **reduce el
> hambre** (25/punto), reparte solo lo que saca y ordena por más tiempo sin comer; `VillagerTavernGoal`
> se desatasca **cada tick**. El soak 72k bajó de **5 a 1 muerte** (Zenobia, granjero, en `630,63,530`,
> misma ruta a la taberna), población 15, cobertura 10/11, 0 duplicados/huérfanos/inactivos, guardado OK.
> **F1 no cerrado:** queda el choke de acceso a la taberna. Informe:
> `2026-11-24-F1-HUNGER-TAVERN-FIX.md`. Siguiente: ruta/puerta de la taberna y repetir 72k con 0 bajas.

> **ACTUALIZACIÓN 24-nov-2026 — SOAK MULTI-DÍA: HERRAMIENTA + HALLAZGOS; NO ACEPTADO.** Código probado
> `552e9b57`. Nuevo arnés `tools/soak` (72.000 ticks, sprint, aldea desechable). Encontró y corrigió una
> **asfixia real del ganadero** (`VillageAnimalFarmGoal` ahora se desatasca cada tick). El run oficial
> 72k dio **0 duplicados, 0 huérfanos, 0 inactivos permanentes**, pero **5 muertes por hambre** con
> comida en la despensa: la sostenibilidad alimentaria a 16 bocas **falla** y queda como siguiente
> objetivo. No se declara la aldea autosuficiente. Informes:
> `2026-11-24-SOAK-MULTIDAY-CAMPAIGN.md` y `2026-11-24-ANIMAL-FARM-SUFFOCATION-FIX.md`.

> **ACTUALIZACIÓN 24-nov-2026 — L4 CERRADO.** Código probado `e335d867`. Mientras el dueño del libro
> está cargado, `cargarDueniosDePuestos` refresca su `OwnerPos` (mismo titular, sin tocar tickets), para
> que una descarga posterior cargue su chunk correcto. Persistencia **553** (544+9); ownership 54; skills
> 376. Informe: `2026-11-24-L4-JOB-SITE-OWNER-POSITION-REFRESH.md`. Límite: sin runtime de descarga.

> **ACTUALIZACIÓN 24-nov-2026 — L3 CERRADO EN SU PARTE EJECUTABLE.** Código probado `e9afbdfc`.
> `restaurarDueniosDelLibro` ya no se rinde ante `yaDeOtro`: la política pura `planificarRestauracion`
> decide devolver el puesto al titular del libro y limpiar las memorias de los aldeanos cargados que no
> lo son, sin soltar el ticket. Pruebas de ownership **54** (39+15); persistencia 544; skills 376.
> Informe: `2026-11-24-L3-JOB-SITE-RESTORE-OWNER.md`. Límite: sin fixture runtime de dos aldeanos.

> **ACTUALIZACIÓN 24-nov-2026 — P1 SKILLS CERRADO EN SU PARTE EJECUTABLE.** Código probado `0a9f8670`.
> El árbol de skills se lee del classpath (sin `Minecraft`) y los iconos se resuelven contra
> `BuiltInRegistries.ITEM`, así que la capability común ya no depende del cliente. Nuevo gate
> `skillCapabilityTest` dentro de `test`: **376 aserciones** (33/33 skills, 36 nodos, NBT completo,
> compatibilidad de partidas viejas sin pisar valores). Informe:
> `2026-11-24-P1-SKILLS-SERVER-SAFE-TREE-AND-TESTS.md`. Límites: sin servidor dedicado con jugador ni UI
> de cliente reejecutada.

> **ACTUALIZACIÓN 24-nov-2026 — L1 CERRADO.** Código probado `7db821ea`. El libro de dueños de puestos
> (`VillageSavedData.JobSiteOwners`) ahora se limpia al **morir** o **convertirse** el titular
> (`VillageAgentRegistry.onDeath`/`onConversion` → `VillageManager.olvidarPuestosDeTitular`), en vez de
> dejar la estación libre pero reservada para siempre. Pruebas: persistencia **544** aserciones
> (525+19) y ownership **39** (32+7); `test`, lint y diff-check verdes. Informe:
> `2026-11-24-L1-JOB-SITE-LEDGER-DEATH-CLEANUP.md`; auditoría de pendientes:
> `2026-11-24-PENDING-AUDIT.md`. Siguiente objetivo independiente (P1): pruebas de skills y dependencia
> de cliente en el árbol de skills. Límite: el disparador no se midió en runtime con un aldeano real.

> **ACTUALIZACIÓN 24-nov-2026 — CIERRE TÉCNICO.** Código probado `1016a7cf`. C4 `c4n-20261124-0250` PASS 3/3 con
> cobertura **11/11** (NITWIT sin estación), 12/12 profesionales, 4 fundadores y guardias/equipo; POI
> `poi-final-20261124-0305` PASS 4/4 con `ServerLevel`/`PoiManager`, digest vivo==disco, tardío, F4 sin fantasma y
> parcial medido; NAV `nav-final-20261124-0255` PASS **21/21** con BUILD/terreno dinámico corregidos. JOB_SITE pasó
> de 3/12 a 11/11; causa principal: cerebro generado con `NONE` sin `refreshBrain` (ValidateNearbyPoi borraba el
> puesto). Nuevo libro persistido `VillageSavedData.JobSiteOwners` para no robar tickets de dueños no cargados.
> F1 resuelto: el fixture imponía A, pero el dueño real/persistido era B; el arnés ahora lee el libro y declara
> NO_MEDIDO si el keeper no está cargado. Protocolo/deuda: `2026-11-24-CONTINUITY-CHECKPOINT.md`.
> Informe: `2026-11-24-TECHNICAL-CLOSURE-CAMPAIGN.md`. Deudas: A2 no
> reejecutado, mundos históricos sin libro, POI sin horas/jugador, navegación de una entidad. A2 sigue cerrado por
> su campaña del 22-nov; este cierre no lo reabre ni lo sustituye.

> **ACTUALIZACIÓN 23-nov-2026 (Incremento 3).** A revalidado en runtime con dos campañas consecutivas:
> `c4n-20261123-1550` (SHA `69d7e55a`) y `c4n-20261123-1602` (SHA `5c427842`, docs-only) → `C4N_PASS` en las 6
> fases con `roster_invariantes_ok=true`, `guardado_ok=true`, 12/12, 4 fundadores y 0 duplicados/huérfanos;
> `JOB_SITE` 1→3→3 y 2→3→3, 3/3 estables en la última recarga (campaña previa: `c4n-20261123-024`, SHA
> `1abab57d`). B: `VillagePersistenceIntegrationTest` (484 aserciones, NBT/`NbtIo`/`Villager` reales) en el gate
> `test`; defecto real corregido en `VillageSavedData.readBlueprint`. D: `NavPlannerMetrics` +
> `NavPlannerMetricsTest` (68) + `NavSearchBaselineTest` (119), planner sin cambio de algoritmo. E: arnés C4
> enriquecido y `tools/av/{diagnose,collect,nav-baseline}.ps1` + `LEEME.md`; `GuardHarness` no portado. Informe:
> `2026-11-23-INCREMENT-3-RUNTIME-PERSISTENCE-INSTRUMENTATION.md`.

> **ACTUALIZACIÓN 23-nov-2026 — A2 y C4 CERRADOS Y ACEPTADOS; auditoría de consolidación publicada** (`2026-11-23-AUTONOMOUS-VILLAGE-CONSOLIDATION-AUDIT.md`). La decisión de la siguiente etapa queda en manos del dueño. Se conserva el texto histórico. **ACTUALIZACIÓN 22-nov-2026 — A2 CERRADO Y ACEPTADO.** Este documento es un **traspaso histórico** del
> 16-oct-2026; su texto original se conserva íntegro por trazabilidad. El estado vigente de A2 está en
> `docs/autonomous-village/2026-11-22-A2-FINAL-ACCEPTANCE-AND-CLOSURE.md` (8/8 estricto en tres corridas
> consecutivas de 12000 ticks, C4_PASS). Las afirmaciones de «A2 sigue ABIERTO» que aparecen más abajo
> describen el estado de aquella fecha, no el actual.

> **Documento de traspaso.** Escrito al cerrar **Stage 6B**. Describe el estado **real y verificado** del
> repositorio en ese momento, no el estado deseado. Léelo antes de tocar nada.
>
> Fecha de captura: **16-oct-2026**.
> Rama: `feature/autonomous-village`.
> HEAD: **`97220be5453b57387002b9bc91935807cc665274`**.
>
> **A2 sigue ABIERTO.** No hay aceptación funcional: **ningún bloque colocado, ninguna travesía verificada y
> ningún destino alcanzado** en el fixture del acantilado.
>
> **Reconciliación 22-oct-2026:** R4 S5, L40 y el spawn del golem quedaron aceptados en sus fixtures, pero **no
> cierran A2**. El S5 es el asedio clásico de `VillageManager.start`, no la navegación propia de este documento.
> La próxima tarea viva está en la sección final de `docs/CONTINUAR.md` (**A1-CLIENT**), no aquí.

---

## A · Misión y dirección arquitectónica

El proyecto desarrolla un sistema de navegación autónoma para los zombis agresivos de DevilRpg. El objetivo
**no** es que pase un fixture concreto:

- Pathfinding 3D consciente del movimiento.
- Restricciones reales de colisión, soporte, holgura, salto y caída segura.
- Rutas conscientes de la construcción, con **colocación real de bloques**.
- **Planes de construcción conectados y multi-paso**.
- **Travesía verificada antes de autorizar** pasos de construcción dependientes.
- **Un único controlador de movimiento autoritativo** mientras la navegación propia está activa.
- Recuperación y replanificación **acotadas** que **preservan la misión original**.

**A\* es el planificador inicial.** *D\* Lite* es un candidato para replanificación dinámica **si las medidas lo
justifican**. No introducir algoritmos sin necesidad demostrada.

**Referencia arquitectónica permanente:** `docs/autonomous-village/01-navigation-architecture.md`
(es un documento **de destino**, no un informe de lo implementado; sus conflictos abiertos están en su §K).
**Léelo antes de proponer cambios arquitectónicos.**

---

## B · Estado real de la implementación

Paquete `src/main/java/com/chipoodle/devilrpg/entity/nav/`:

| Fichero | Líneas | Estado |
|---|---|---|
| `NavState.java` | 30 | **Integrado.** `IDLE, PLANNING, TRAVERSING, OBSTRUCTED, RECOVERING, ARRIVED, FAILED`. |
| `NavRoute.java` | 94 | **Integrado.** `Transition = WALK, STEP_UP, JUMP_UP, DROP, DOOR, BREAK, BRIDGE, BUILD` (con `BUILD` añadido en Stage 4Q). Nodo = celda de bloque; la posición pisable de `BUILD` es su `above()`. |
| `NavObstruction.java` | 176 | **Integrado.** `Kind = PASSABLE, STEP_UP, JUMP_UP, DROP, BREAKABLE, PROTECTED, BLOCKED`; interfaz `Policies` (`maxSafeDrop()=3`, `waterDepthForSafeDrop()`, `isProtected`, `canPlace`); `classify(...)`, `hasSupport(...)`, `fits(...)`, `caidaReal(...)`. |
| `NavPlanner.java` | 268 | **Integrado.** A\* con presupuesto (`devilrpg.nav.budget`, por defecto 900, clamp 50..20000), `COST_BUILD=16.0`, `esConstruible(...)` (privado), invariante de caída segura (Stage 4V) y `rechazadasPorCaida`. |
| `NavExecutor.java` | 772 | **Integrado, pero la construcción NO funciona todavía** (ver §C). Contiene la máquina de construcción (Stage 6A/6B). |
| `NavFeature.java` | 31 | **Integrado.** `-Ddevilrpg.nav.custom=true` o `DEVILRPG_NAV_CUSTOM=1`. **Por defecto APAGADO** (`ENABLED` se resuelve una vez, estático). |

**`AggressiveZombieEntity.java`** (modificado, no commiteado): registra los goals, expone `navExecutor()`,
`navPolicies()` y `navegarConEjecutorPropio(...)`; `aiStep()` da el tick al ejecutor propio **antes** de los goals
cuando el flag está encendido; `setObjective(...)` fija también la misión del plan de construcción.

**Primitiva de colocación:** `setPlacement(...)` enlaza `ponerEscalon(...)` de la entidad con el plan; verifica
aire, dos huecos encima, soporte con colisión y `!protegidoPorLaAldea`.

**Legado que sigue presente:** `AggressiveZombiePathPlanner.java` (planificador antiguo, *untracked*) continúa en
el árbol como **segundo planificador**; su retirada o permanencia sigue **pendiente de decisión** (Stage 4P §B
propone retirarlo cuando quede superseded). La navegación propia **no** está probada en el mundo real: todo lo
medido es en fixtures aislados.

---

## C · Resultado REAL de Stage 6B

**Directorio del run final: `build/a2n/a2n-20261016-130330`** (log en `build/a2n/a2n-20261016-130330/srv.log`).
Comando: modo `stairs`, `-NavCustom true`, presupuesto 900, chunks forzados, fixture Stage 5C.

| Hito | Resultado |
|---|---|
| Geometría del acantilado validada | ✅ **PASA** (cara de aire, soportes y holguras correctos, desnivel exacto 6) |
| Plan de construcción creado | ✅ sí — `planMision = 130,81,-8`, hook alcanzado (`encima=true`) |
| **Primera colocación legal de bloque** | ❌ **0 colocaciones** (`construidas=0`, `colocadoVerif=0`) |
| Verificación del bloque colocado | ❌ nunca se llegó |
| **Travesía física en un tick posterior** | ❌ **0** (`ESCALON-TRAVESADO: 0`, `travesados=0`) |
| Autorización de pasos dependientes | ❌ nunca se llegó |
| **Llegada al destino original** | ❌ `alcanzoDestino=false` (`yFinal=87`, `destinoY=81`) |
| Caída insegura | ✅ ninguna |
| Oscilación acotada | ⚠️ terminó en `RECOVERING`, `rec=6`, `osc=true` (con la misión intacta) |

**Primera transición fallida:** `buscarEscalonDeDescenso()` **nunca devolvió una celda**, así que
`pasoDeConstruccion()` no colocó nada y `FaseEscalon` **nunca salió de `NOT_STARTED`**. El ejecutor terminó en
`state=RECOVERING` con `rej=2`, `budget=0`, y el zombi parado en `127,87,-8` (borde de la meseta).

**Por qué todavía no se sabe la causa raíz:** no se ejecutó el diagnóstico acotado que habría registrado, para
los primeros candidatos, **qué predicado exacto** los rechaza (`isProtected` / `canPlace` / `!isAir(celda)` /
`!isAir(celda.above())` / `!isAir(celda.above(2))` / sin soporte). **Es la siguiente acción** (§F). No se debe
adivinar: es exactamente el método (log acotado del primer rechazo) que resolvió los bloqueos de Stage 4U, 5B y 6A.

> ⛔ **NO afirmar una causa sin evidencia.** A día de hoy **no está establecido** por qué
> `buscarEscalonDeDescenso()` devuelve `null`. **No** se puede atribuir a la holgura de los candidatos, a la
> protección de bloques, a la política de colocación (`canPlace`) ni a la posición de la entidad **sin el log que
> lo demuestre**. Cualquier informe que afirme una de esas causas sin el registro del predicado que falla es una
> hipótesis no medida y debe rechazarse.

Runs de Stage 6A, para trazabilidad:
`a2n-20261016-121208` (hook detrás del tope de recuperación), `…122549` (**`planMision=null`**),
`…123326` (hook activo, 0 escalones). Run de Stage 5C: `…120009`.

**Detalle de implementación de Stage 6B (en `NavExecutor.java`, compila):**

- Se añadió **`soporteConstruido(BlockPos debajo)`**, que exige **las cuatro condiciones** para aceptar un bloque
  construido como soporte:
  1. el bloque **existe AHORA** en el mundo (no basta con figurar en `construidas`),
  2. su **forma de colisión** cumple el requisito real de soporte,
  3. pertenece al **conjunto verificado** por este plan (`construidas`), y
  4. satisface la **dependencia de travesía** (la entidad lo ha pisado).
- Se distingue **`escalonesTravesadosSet`** de **`construidas`**: colocación **no** es travesía; el conjunto de
  travesados solo se puebla en la transición `TRAVERSED`.
- La búsqueda pasó a una **escalera hacia fuera** (baja avanzando), apoyándose en los escalones **ya travesados**
  o en **terreno natural**, en lugar de exigir terreno natural en toda la cara vertical.
- **Compila, pero la prueba de aceptación funcional FALLÓ** (ver §C).

---

## D · Historial de hallazgos establecidos

1. **Stage 5C** validó una **cara vertical de aire real de 6 bloques** y demostró que la transición `BUILD`
   aislada **no podía generar una arista de construcción** para esa cara. Evidencia: `noAire=2`,
   `sinSoporte=4`, y la única celda `esConstruible=true` era la **superficie ya pisable** de la llanura
   (`121,81,-8`, `PASSABLE`), no una celda que necesitara construirse.
2. **Stage 6A** introdujo la máquina de construcción, la separación misión/sub-objetivo, la verificación de
   colocación y el **gating por travesía**. Resultado: **0 colocaciones**, destino no alcanzado.
3. La búsqueda de construcción de Stage 6A **dependía de soporte natural** y no podía iniciar el descenso
   conectado. (Medido: ninguna celda de una cara vertical tiene terreno justo debajo.)
4. **Stage 6B** implementó que un bloque **construido previamente** sirva como soporte **solo si** el bloque
   existe ahora en el mundo, su colisión cumple el requisito, lo colocó y verificó **este plan**, y la entidad
   lo ha **PISADO** (`escalonesTravesadosSet`, distinto de `construidas`). También reorientó la búsqueda a una
   **escalera que baja avanzando**. **La implementación está en el árbol y compila, pero sigue produciendo 0
   colocaciones** — es decir, **el contrato de cadena de soporte NO está demostrado en ejecución**.
5. **Criterio de aceptación vigente:** construcción física, **travesía física por la misma entidad** y
   **llegada al destino original**. **No** basta con generar una ruta, ni con colocar un bloque, ni con un
   waypoint.

**Hallazgos anteriores que siguen siendo ciertos:**

- Se **descartó** la arbitración del selector de goals como causa de los atascos (`puedeEntrar=true` siempre).
- **Budgets** no son la restricción: a 900 y a 4000 expansiones el centro protegido sigue `metaEncolada=false`.
- El centro de la aldea es **`PROTECTED`** e inocupable; el objetivo se movió a un **punto de aproximación**
  derivado de la superficie a `RADIO_DE_APROXIMACION = 64` (fuera de `FENCE_RADIUS=62`).
- **Se consiguió una colocación verificada** una vez (`a2n-20261016-023618`) pero **con 0 travesías**, y era un
  artefacto local (`dy=1`) **fuera de cualquier ruta**: no cuenta como aceptación.
- Los fixtures deben **forzar los chunks**; a 256 bloques del spawn las escrituras `setBlock` **no se aplican**
  (medido: la columna leía el terreno natural). Ese fue el bloqueo real de Stage 4X/4Y.
- Referencia de raid con muro: **5/8** estable (`…131506`, hash `4081CAF70F16F813`); sin muro **8/8**
  (`…113448`); los rezagados son siempre **z0/z4/z7**.

---

## E · Invariantes de seguridad y flujo de trabajo (PROMINENTE)

- **`D:\Documents\DevilRpg` (worktree principal) NO debe tocarse.** Verificado: 0 cambios.
- **Nunca** acceder ni modificar `run/saves/New World`.
- **Hash protegido del save:** `D9A58A246C23FDA92BFE2E6CA6770F7BDB1A1FD8C886DB819E485B4E0BD27140`.
- **Nunca** reset, clean, stash ni sobrescribir trabajo sin commitear.
- **Nunca** commit ni push sin autorización explícita.
- **No** ejecutar Gradle y el harness del juego a la vez.
- **Confirmar que no queda ningún JVM del juego activo antes de Gradle.**
- **No** ejecutar los fixtures de caza y de raid amurallado a la vez que el desarrollo de navegación.
- Los artefactos temporales del harness van en **`build/a2n/` (ignorado)** o en la infra de test existente.
- **No dejar marcadores de diagnóstico temporales en código de producción.**
- La navegación propia se mantiene **por defecto APAGADA**.
- **No** revivir la fase retirada de arquitectura de aldeanos ni cambiar IA de aldeanos no relacionada.
- El script de ejecución rápida (`build/a2n/lanzar-a2n.ps1`) crea un `gameDirectory` propio por run; **no usarlo
   sin comprobar que no apunta al worktree principal**.

---

## F · Próximos pasos exactos (ordenados)

**La aceptación de construcción NO ha pasado, así que la ruta es la de fallo:**

1. **Preservar el run que falla** (`build/a2n/a2n-20261016-130330`) e identificar la primera transición fallida
   (ya acotada: `buscarEscalonDeDescenso()` devuelve `null`).
2. **Diagnóstico acotado, SOLO en el harness ignorado de `build/a2n/`, y SIN cambiar comportamiento de
   producción.** Registrar los primeros ~8 candidatos de `buscarEscalonDeDescenso()` con sus coordenadas y **el
   predicado exacto que falla**, entre:
   - `isProtected`
   - `canPlace`
   - celda destino es aire
   - primera y segunda celda de holgura son aire (`celda.above()`, `celda.above(2)`)
   - validez del soporte, **distinguiendo terreno natural** de **soporte construido verificado y travesado**
   - alcanzabilidad u otras restricciones de candidato que **realmente existan** en la implementación

   Informar **cuántos candidatos se examinaron** y **por qué se rechazó cada uno**. **Sin spam por tick.**
   **No duplicar un predicado privado de producción y presentarlo como autoritativo** sin etiquetarlo
   claramente como **replicado**. Esto es lo que faltó al cerrar Stage 6B.
3. **Leer el código y la evidencia antes de cambiar nada.** Corregir **el defecto de implementación más pequeño
   demostrado**, dentro de la arquitectura de construcción ya aprobada (no reabrir el diseño).
4. **Repetir el test de aceptación aislado** del acantilado (modo `stairs`, `-NavCustom true`, presupuesto 900,
   chunks forzados, fixture Stage 5C).
5. **No ejecutar la caza ni el raid amurallado** hasta que pasen los criterios del acantilado.

Si la aceptación llegara a pasar: revisar riesgos de regresión → validar compilación/lint/diff → **caza** →
**raid amurallado (8/8 en dos corridas idénticas)** → evaluar huecos arquitectónicos restantes antes de declarar
A2 cerrado.

**En cualquier caso, A2 permanece ABIERTO.**

---

## G · Decisiones del dueño y preguntas abiertas

Solo lo que **realmente** requiere al dueño. **No** se pide volver a aprobar el diseño de construcción conectada
multi-paso: **ya fue autorizado** (Stage 6A) y está implementado.

1. **Política de ramas (conflicto ya documentado en la arquitectura, §K).** `CONTINUAR.md` dice que
   `feature/autonomous-village` **debe mantenerse sincronizada** con `migration-neoforge-1.21.1`. **No** se debe
   fusionar ni sincronizar hasta que el dueño resuelva ese conflicto.
2. **Retirada de `AggressiveZombiePathPlanner.java`** (segundo planificador aún en el árbol): ¿se retira cuando el
   nuevo quede demostrado, o se conserva? (Stage 4P §B propone retirarlo.)
3. **`PatrolHomeGoal` — rama de deambulación**: la vuelta a casa ya está convertida a sumisión de objetivo, pero
   el `moveTo` de deambulación **sigue sin pasar por el ejecutor propio**. ¿Se convierte también (autoridad de
   movimiento única estricta) o se deja?
4. **Alcance de la reescritura aldeano-vs-zombi** y esquema de numeración/README de `docs/autonomous-village/`
   (conflictos §K del documento de arquitectura): pendientes de decisión.

---

## H · Integridad verificada al escribir este documento

| Comprobación | Resultado |
|---|---|
| JVMs (juego y totales) | **0** — ninguno activo |
| `git rev-parse HEAD` | `97220be5453b57387002b9bc91935807cc665274` |
| Rama | `feature/autonomous-village` |
| Área de stage (`git diff --cached`) | **vacía** — nada preparado para commit |
| Worktree principal `D:\Documents\DevilRpg` | **0 cambios** |
| Hash del save protegido | `D9A58A246C23FDA92BFE2E6CA6770F7BDB1A1FD8C886DB819E485B4E0BD27140` ✅ |
| Run de Stage 6B `build/a2n/a2n-20261016-130330` | **existe** (`srv.log` presente) |
| `compileJava` | ✅ BUILD SUCCESSFUL (Stage 6B) |
| `lint_aldea.py --strict` | ✅ OK, exit 0 (Stage 6B) |
| `git diff --check` | ✅ exit 0 |
| Commit / push | **ninguno** (HEAD sin cambios) |

**Ficheros modificados sin commitear (pre-existentes, intactos):**

```
 M docs/CONTINUAR.md
 M docs/aldea-invariantes.md
 M src/main/java/com/chipoodle/devilrpg/entity/AggressiveZombieEntity.java
 M src/main/java/com/chipoodle/devilrpg/eventsubscriber/common/PlayerCapabilityForgeEventSubscriber.java
 M src/main/java/com/chipoodle/devilrpg/util/EventUtils.java
?? docs/autonomous-village/
?? src/main/java/com/chipoodle/devilrpg/entity/AggressiveZombiePathPlanner.java
?? src/main/java/com/chipoodle/devilrpg/entity/nav/
```

> Nota: `PlayerCapabilityForgeEventSubscriber.java` muestra un diff grande **por normalización de finales de
> línea (CRLF→LF)**, no por cambio semántico (+5 líneas reales: guarda de `FakePlayer`). No revertirlo.

## I · Estado tras el fix de sucesor canonico (25-oct-2026)

Rama `feature/autonomous-village`, HEAD `97220be5453b57387002b9bc91935807cc665274`, staged 0. El fix de identidad A* esta verificado por
`policyTest` y por el DROP aislado `drop11-20261025-0020`. A2 completo no se ejecuto: sigue bloqueado por el
centro del harness protegido. Ver `CONTINUAR.md` para el handoff y los comandos.
