# ACTUALIZACIÓN 25-nov-2026 — FAIL-CLOSED EN PRERREQUISITOS DE SKILLS

- DeepSeek validó el SHA `47c2f72e20c54f223dfbd34a0ea193cd94d3de09`: compilación, tests (392), integración de skills (392 assertions), lint y diff-check PASS; no hubo runtime con jugador real.
- Defecto confirmado: `SkillPointSpendPayload.prerequisitesMet` podía considerar desbloqueado un padre cuyo máximo era 0/negativo, porque `parentLevel >= parentMaximum` resultaba verdadero.
- Corregido en `66f5964643128aafb2dd0a3b36719b63b39ed093`: para padres que representan skills, máximo ausente/no positivo o nivel negativo ahora rechaza el prerrequisito antes de evaluar progreso.
- Corregido también el reflejo visual en `SkillProgress` (commit `30020a4e37c0f52e38a4d8cd80c54d469d3957e8`): `isDone()` solo devuelve true con máximo positivo; porcentaje/texto no intentan representar máximos negativos como progreso válido.
- Las raíces visuales sin `SkillEnum` siguen desbloqueando sus primeros nodos, como antes; la validación fail-closed aplica a padres que sí representan una skill.
- **HEAD esperado para Harness:** `30020a4e37c0f52e38a4d8cd80c54d469d3957e8`.
- ChatGPT no ejecutó Gradle ni runtime. Validar primero compilación/tests/lint/diff en ese SHA exacto.
- Añadir pruebas explícitas: padre con máximo null/ausente, 0 y negativo debe rechazar; máximo positivo con padre sin progreso debe rechazar; padre con progreso positivo debe permitir; padre completado con máximo positivo debe permitir; raíces sin skill deben conservar comportamiento. Confirmar también UI `isDone=false` para máximo <=0.
- Repetir después la matriz dinámica 20/30/5, costes/tipos, nueva/existente, compra hasta máximo y rechazo del siguiente nivel, directexecution y save/reload descrita en `2026-11-25-SKILL-DATA-DRIVEN-RULES.md`.
- El reporte anterior no ejecutó jugador real, cliente modificado, consumo de recurso, ni save/reload; siguen PENDIENTES. Servidor dedicado solo llegó a `Done`, sin conexión de jugador. No declarar PASS global hasta cubrir esos escenarios.
- Harness no modifica producción. Preservar mundo/logs existentes; usar mundo de validación separado y documentar SHA, configuración JSON efectiva y evidencia de cada caso.

---

# ACTUALIZACIÓN 25-nov-2026 — FIX DE COMPILACIÓN POST-VALIDACIÓN

- DeepSeek detectó que `PlayerCapabilityForgeEventSubscriber` conservaba un import y un uso de `PlayerPassiveSkillPayload`, clase eliminada en el hardening anterior.
- Corregido en commit `eea975ca152d1068e95ba2e61c2122eed5bdee21`.
- No se restauró el payload: el evento de cambio de dimensión reaplica pasivos directamente en servidor mediante `PlayerSkillCapabilityInterface.triggerPassive(player, skill)`, manteniendo el filtro de pasivos no-minion y la validación existente de niveles/tipo.
- El evento retorna en cliente; la reaplicación de efectos queda server-side. Se conserva el traslado de minions al destino.
- No se ejecutaron pruebas por ChatGPT. La compilación y toda la matriz anterior están pendientes de repetición sobre el nuevo HEAD.
- HEAD esperado para la siguiente validación: `eea975ca152d1068e95ba2e61c2122eed5bdee21`.

---

# ACTUALIZACIÓN 25-nov-2026 — REGLAS DE SKILLS DINÁMICAS DESDE JSON

- Implementación publicada en `361aab948117ed62a5b24dbc6f09d6af4e21ec39` (HEAD de rama al cierre de esta actualización); pendiente de validación Harness/DeepSeek. No declarar PASS hasta recibir reporte sobre este SHA exacto.
- Los getters de máximos, costes y tipos de recurso ahora derivan sus mapas del árbol cargado desde `root_complete.json`, no de copias persistidas en el NBT del jugador. El NBT conserva progreso (niveles comprados); por tanto, cambiar `maxskilllevel` aplica a partidas nuevas y existentes sin borrar progreso.
- Los setters de máximo/coste/tipo se conservan por compatibilidad de interfaz, pero registran advertencia y no alteran reglas: la fuente autoritativa es el JSON.
- Se agregó `directexecution` a la definición Werewolf en `root_complete.json`. La regla declara trigger, recurso, umbral y requisito de transformación.
- `SkillServerPolicy` carga esa regla desde el recurso del mod y la evalúa server-side usando nivel, estado de transformación y maná obtenidos de capabilities del servidor. Si no hay regla, el recurso/trigger no se soporta o los datos no son válidos, rechaza.
- `DirectSkillExecutionPayload` usa exclusivamente `SkillServerPolicy`; el cliente solo propone el enum, nunca el estado ni el umbral. El decoder inválido se convierte en `EMPTY` y se rechaza.
- La clase `DirectSkillExecutionPolicy` queda como fachada de compatibilidad para pruebas/callers; ya no contiene el enum ni el umbral.
- Se mantiene la estructura de árbol/prerrequisitos en el builder común ya usado por la capability. Esta modificación separa la política de acciones directas del código de GUI; no se hizo una migración general de todas las clases SkillElement/GUI.
- Validación pendiente: compile/test/skillCapabilityTest/lint strict/diff check; comprobar nuevo mundo y mundo existente tras cambiar máximos/costes; matriz de reglas directexecution (sin regla, skill errónea, nivel 0, transformado/no transformado, maná >, = y < umbral); cliente modificado; servidor dedicado y save/reload.
- No se ejecutaron pruebas por ChatGPT.

---

# ACTUALIZACIÓN 25-nov-2026 — HARDENING DE SKILLS PUBLICADO, VALIDACIÓN PENDIENTE

- Código de seguridad publicado en `2224c77a359434b23a8929a58e66e4fe60a34930`.
- El árbol completo ya no acepta NBT C2S; compras, loadouts y rotación usan acciones tipadas.
- Pasivos ejecutados dentro de la compra validada; payload independiente retirado.
- Payload directo de transformación exige hombre lobo activo y maná agotado.
- **No hay PASS nuevo:** DeepSeek debe ejecutar build, tests, lint, UI cliente y servidor dedicado sobre el HEAD actual.
- Informe: `2026-11-25-SKILLS-SECURITY-HARDENING.md`.

---

# OBJETIVO CERRADO — SOAK FINAL PASS EN `43afe1e6` (24-nov-2026)

- **Soak final:** `soak-final-head-20261124-2200` → **`SOAK_PASS`**: 72k, **0 muertes**, 11/11,
  0 huérfanos, 0 inactivos permanentes, comida 66, guardado OK. `sin_puesto` máximo 1 transitorio
  (1 muestra, umbral 30).
- **F1–F4, L2 y L9 cerrados**; skills **392**; maná/stamina/auxiliar/minions/árbol/experiencia bajo
  autoridad del servidor; el soak ya estaba aceptado y estable en dos semillas con `bcf98f70`.
- **Límites declarados:** L5/L6 (arnés exacto), L7 (fastutil A/B), L8 (excluido por el usuario), cliente
  real de skills, y la decisión A1/ramas del dueño. No son defectos del código auditado.
- **Informe final:** `2026-11-24-AUTONOMOUS-OBJECTIVE-CLOSURE.md`.

- **Estabilidad multi-mundo CONFIRMADA:** el mismo SHA `bcf98f70` da **`SOAK_PASS`** en la semilla
  `3188…` (`soak-f4-confirm`: 0 muertes, 11/11, 0 inactivos permanentes, comida 108) y en la `7454…`
  (`soak-f4-stability`: 0 muertes, 11/11, 0 inactivos, comida 164).
- **F1/F2/F3/F4 cerrados**; el soak multi-día queda **aceptado y estable en dos semillas**.
- **L2 CERRADA (política):** `limpiarDuplicados` + 5 aserciones nuevas (ownership **59**). Con el
  titular no cargado se limpian las memorias duplicadas cargadas y **NO se libera el ticket** (es del
  ausente). El fixture runtime con titular descargado sigue declarado como limitación.
- **Skills runtime (2 avances):** (1) el humano con 0 de maná ya no se convierte en lobo
  (`WerewolfManaPolicy`: solo revierte `transformado && mana<=0 && tick%20==0`); (2) el payload directo
  ya no ejecuta cualquier skill: `DirectSkillExecutionPolicy` solo permite `TRANSFORM_WEREWOLF` con
  nivel >0. **Skills 384 aserciones** (antes 376). El cliente real (UI/HUD/doble `onKeyInput`) sigue
  pendiente: no hay arnés de cliente.
- **L9 CERRADA:** experiencia S→C; el árbol C2S se valida con `SkillTreeSpendPolicy` (mismo total o
  +1 con punto sin gastar), el servidor consume el punto y reenvía el estado autoritativo; el pasivo
  exige nivel >0. **Skills 392**.
- **Gates:** `test` 1m22s; persistencia 553, ownership 59, skills 392; lint/diff verdes.
- **Siguiente:** L7 (fastutil A/B), L8 (excluido), cliente real de skills y decisión de ramas/A1.
- Informe: `2026-11-24-L9-SKILL-TREE-VALIDATED.md`.

# SOAK ACEPTADO — F1/F2/F3 CERRADOS; PENDIENTES L2/L5–L8/SKILLS RUNTIME (2026-11-24)

- **SHA exacto:** `bfd5113b269ffa317c7e21d2acbf0c867e833a6b`. Run `soak-f3-outside-20261124-2030`:
  **`SOAK_PASS`**, 72.000 ticks, **0 muertes**, población 30, **11/11 desde la primera muestra**,
  0 duplicados / huérfanos / sin puesto / inactivos permanentes, comida 60, guardado OK.
- **F1 (hambre)**, **F2 (caída)** y **F3 (JOB_SITE fuera del muro)** **CERRADOS**.
- La causa de F3 era un **granjero** con el composter en `736,69,537`, **fuera de la muralla**; el
  POI era del oficio y por eso se aceptaba. `bfd5113b` invalida todo `JOB_SITE` fuera del muro y lo
  suelta; el granjero reclama una estación de dentro. `sin_puesto_bajo_max` 148–333 → **0**.
- **Gates:** `test` 4m11s; persistencia 553, ownership 54, skills 376; lint/diff verdes.
- **Siguiente:** repetir el 72k en otra semilla/mundo para estabilidad, y atacar **L2, L5–L8, skills
  runtime** (servidor con jugador, UI cliente, doble `onKeyInput`) y la decisión de ramas/A1.
- Informe: `2026-11-24-SOAK-ACCEPTANCE.md`.

# F1 Y F2 CERRADOS; F3 (PUESTO DE FLETCHER) INTERMITENTE (2026-11-24)

- **Código:** `7e2b4293`. F1 (hambre) con ración al rendirse y a hambre crítica; F2 (caída) con
  `VillageFallPolicy` (miembros del pueblo sin daño por caída **fuera de asedio**).
- **Resultado 72k (`soak-f3-periodic`, `68741d29`):** **0 muertes totales**, población 30, comida 100,
  **11/11 al final**, 0 duplicados, 0 inactivos permanentes, guardado OK. El único fallo es **F3**: 1
  fletcher sin POI durante **241 muestras iniciales** (148–333 según run) y 1 huérfano transitorio; el
  12k de la misma semilla pasa limpio.
- **F3:** el re-registro/reemplazo del POI (`743b81db`/`68741d29`) deja el final en 11/11 y reduce el
  hueco, pero **no lo cierra**. Siguiente: 72k `-Diag` para capturar `SOAK_POI_INVALID` (UUID,
  profesión, `JOB_SITE`, POI, bloque) y decidir si es un adulto nuevo con oficio sin estación o el
  fletcher; luego 11/11 desde la primera muestra.
- **Gates:** `test` 3m11s; persistencia 553, ownership 54, skills 376; lint/diff verdes.
- Informes: `2026-11-24-F2-FALL-PROTECTION.md` y `2026-11-24-F3-JOB-SITE-POI.md`.

# F1 CERRADO (HAMBRE); F2 CAÍDA ABIERTA (2026-11-24)

- **Código:** `fa6a63f4`. Al rendirse `VillagerTavernGoal` (`stuckTicks >= STUCK_LIMIT`), si hay comida
  y el aldeano tiene hambre, se le da una **ración real en el sitio** (`comer(level,false)`): saca 1
  punto de la despensa, marca comida y reduce 25, sujeto al `tieneHambre` de 3 min. Es la promesa que
  ya estaba escrita en el comentario de la clase.
- **Resultado 72k (mismo mundo):** `soak-f1-fallback` → **1 muerte, 0 de hambre**; población 29,
  comida 88, 11/11, 0 duplicados/huérfanos/sin puesto/inactivos, guardado OK, 45 raciones de rendición.
  Antes: 8 muertes (7 hambre). **F1 (hambre) CERRADO.**
- **F2 abierta:** la única baja es una **caída** (`Zacarias, Volviendo al pueblo hit the ground too
  hard`, `729,64,542`). El soak global sigue `SOAK_PASS_CON_BAJAS`, **NO ACEPTADO**; siguiente
  objetivo: daño por caída/terreno en la vuelta al pueblo.
- **Gates:** `test` 1m13s; persistencia 553, ownership 54, skills 376; lint/diff verdes.
- Informe: `2026-11-24-F1-CLOSURE-FALLBACK-RATION.md`.

# F1 ABIERTO — DESPENSA 256 + MESA POR ALDEANO; QUEDA EL ACCESO A LA TABERNA (2026-11-24)

- **Código actual:** `80c0e76e`. Tres correcciones acumuladas: ración diaria que reduce hambre, desatasco
  por tick del goal de taberna, **remesa inicial 64 panes = 256 puntos** (antes 20, la aceptación C1 usaba
  256) y **mesa por aldeano** (antes `usandoDespensa=true` mandaba a los 16 al mismo punto de apoyo).
- **Resultados 72k:** `soak-f1-stable` (mundo nuevo, 20 puntos) → **29 muertes**, comida 0;
  `soak-f1-remesa` (256) → **16 muertes**, comida 160–284 (ya no es hambre de despensa);
  `soak-f1-table` (256 + mesa) → **9 muertes**, población 21, comida 124–284;
  `soak-f1-table-diag` (misma semilla, `-Diag`) → **13 muertes**, comida 56, cobertura final 11/11;
  `soak-f1-waypoint` (oeste+sur) → **10**; **`soak-f1-west` (solo oeste) → 8 muertes**, población 22,
  comida 164, 11/11 final, 0 ausentes; `soak-f1-plaza` (tramo de plaza) → **30 muertes, población 0**,
  comida 292 (regresión, **revertida** en `af711875`). Las bajas siguen `Yendo a la taberna` en
  `709-728,63,541-578` (más una caída).
- **Waypoint de plaza DESCARTADO:** mandó a los aldeanos al oeste `599,63,519`/centro y mató a 30 con
  la despensa llena; el 12k no lo vio. No se reintroduce.
- **Causa raíz capturada:** las mesas (`dark_oak_fence` + `oak_pressure_plate`) dan
  `ruta=6 nodos … alcanza=NO`; la entrada exterior oeste mejora 13→8, pero el **terreno/ruta del
  este/sureste** sigue atascando. **No es hambre de despensa ni destino único: es ruta/terreno.** No se
  tapa con alimentación a distancia.
- **Conclusión:** la comida y la congregación están corregidas; **el acceso/ruta a la taberna sigue
  matando**. F1 **no cerrado**, soak **NO ACEPTADO**. Informes:
  `2026-11-24-F1-FOOD-COLLAPSE-AND-INITIAL-STOCK.md` y
  `2026-11-24-F1-TAVERN-TABLE-DESTINATION.md`.
- **Siguiente:** 72k `-Seed 3188456493687723571 -Diag` (en curso `soak-f1-table-diag-20261124-1545`)
  para capturar nodo/bloque del choke; corregir puerta/tramo. No relajar hambre.
- **Gates:** `test` 4m17s, lint, diff-check verdes; persistencia 553, ownership 54, skills 376.

# F1 PARCIAL — RACIÓN Y DESATASCO DE TABERNA; QUEDA EL CHOKE DE `630,63,530` (2026-11-24)

- **Código probado:** `0e57b569`. Dos correcciones: `repartirRaciones` ahora ordena por más tiempo sin
  comer, reparte solo lo que saca y **reduce hambre 25 por punto** (antes marcaba `ultimaComida` sin
  bajar el hambre); `VillagerTavernGoal.tick` llama a `desatascarSiEstaEncajado` **cada tick**.
- **Resultado:** 72k `soak-f1-final-20261124-1245` → `SOAK_PASS_CON_BAJAS`: **5 → 1 muerte**, población
  15, cobertura 10/11, comida 4–80 (final 20), 0 duplicados/huérfanos/inactivos, guardado OK. La única
  baja, **Zenobia (Granjero)**, murió en `630,63,530` con `VillagerTavernGoal` y
  `nav=[35 nodos, anda=true, llega=false]`: **el mismo choke**. 12k sí dio 0 bajas (food 20→124).
- **F1 NO cerrado:** el hambre y el desatasco están corregidos, pero el **acceso a la taberna** en ese
  punto sigue matando. No se declara el soak aceptado. Informe:
  `2026-11-24-F1-HUNGER-TAVERN-FIX.md`.
- **Siguiente objetivo:** ruta/acceso a la taberna (destino, puerta, tramo, `primerTramoDelPorton`/
  `VillageErrands`) y repetir 72k con **0 muertes por hambre**. No tocar balance para ocultar el choke.
- **Gates:** `test` + `compileJava -Pa2nDiagnostics=true` verdes (2m09s), lint OK, `git diff --check` 0;
  persistencia 553, ownership 54, skills 376.

# SOAK MULTI-DÍA — HERRAMIENTA Y HALLAZGOS; NO ACEPTADO (2026-11-24)

- **Herramienta:** `tools/soak/SoakHarness.java` + `lanzar-soak.ps1` (opt-in `-PsoakHarness=true`,
  excluye `debug/**`). Genera aldea propia, mantenimiento real cada 200 ticks, sprint, muestreo de
  población/cobertura/duplicados/huérfanos/inactividad/comida/leña/hierro y muertes por evento.
- **Fix real encontrado:** el ganadero se asfixiaba en una valla mientras `Recogiendo el corral`
  (`soak-diag-20261124-1150`, Zacarías en 708,55,533). `VillagerAnimalFarmGoal.tick` ahora llama a
  `desatascarSiEstaEncajado` **cada tick** (mismo patrón que leñador/granjero/recolector/clérigo).
  Código probado `552e9b57`; 12k y 72k posteriores no reprodujeron la asfixia. Informe:
  `2026-11-24-ANIMAL-FARM-SUFFOCATION-FIX.md`.
- **Run oficial 72k** `soak-final-20261124-1120` (`552e9b57`): `SOAK_PASS_CON_BAJAS`, 16→11 población,
  **5 muertes por hambre**, 4 ausentes, cobertura 8/11, **0 duplicados, 0 huérfanos, 0 inactivos
  permanentes**, comida 6–77 (final 19), guardado OK, sin excepciones. Detalle:
  `2026-11-24-SOAK-MULTIDAY-CAMPAIGN.md`.
- **NO ACEPTADO:** el contrato de estabilidad (asignaciones) pasa; **la sostenibilidad alimentaria
  falla** (5 muertes en 3 días). Las trazas muestran a los cinco **yendo a la taberna** (4 atascados en
  `630,63,530`, uno dentro de una puerta en `628,66,529`): no es solo cantidad de comida. Causas
  candidatas: ruta/puerta de la taberna, `repartirRaciones` que marca comida pero no reduce el hambre,
  puerta de 1 h del goal frente a +1 de hambre/10 s, y solo 4 camas para 16. **Siguiente objetivo:
  economía/hambre a 16 bocas** y repetir 72k con **0 muertes por hambre**.
- **Límite del arnés:** sin jugador no se ejecuta `VillageManager.manageNearby` (reposición); una baja
  deja la cobertura por debajo de 11/11. C4 cubre 12/12 y 11/11.

# L4 RESUELTO — LA POSICIÓN DEL DUEÑO SE REFRESCA MIENTRAS ESTÁ CARGADO (2026-11-24)

- **Fallo corregido:** `cargarDueniosDePuestos` ignoraba a los dueños cargados, así que `OwnerPos` solo
  se actualizaba al reclamar (o al reconciliar un `JOB_SITE`). Un dueño cargado sin `JOB_SITE` conservaba
  una posición vieja y, al descargarse, la carga podía buscarle donde ya no estaba.
- **Código probado:** `e335d867`. Si el dueño está cargado, vivo y no removido, se llama a
  `claimJobSite(clave, dueño, v.blockPosition())`: mismo titular, `OwnerPos` refrescada, `dirty` solo si
  cambió. Si no está cargado, se mantiene la carga por `OwnerPos`.
- **Pruebas:** persistencia **553** (544+9: refresco, dueño intacto, intento ajeno no pisa, NBT); ownership
  54; skills 376; `test` + `compileJava -Pa2nDiagnostics=true` verdes; lint OK; `git diff --check` 0.
  Informe: `2026-11-24-L4-JOB-SITE-OWNER-POSITION-REFRESH.md`.
- **Límite:** sin corrida runtime de alejamiento/descarga; el refresco ocurre en el latido de aldea (10 s),
  no cada tick.
- **Siguientes:** L2 (limitación de medición, no defecto seguro), soak multi-día, servidor dedicado/UI de
  skills, decisión de ramas/A1.

# L3 RESUELTO — EL LIBRO RECUPERA SU PUESTO AUNQUE OTRO CARGADO LO OCUPE (2026-11-24)

- **Fallo corregido:** `restaurarDueniosDelLibro` se rendía ante `yaDeOtro`: si B (cargado) tenía el
  `JOB_SITE` del POI, A (dueño del libro) no recuperaba su estación hasta que B la soltara, y
  `reconciliarPuestosDuplicados` no actuaba porque solo A/B con el **mismo** JOB cuentan como duplicado.
- **Código probado:** `e9afbdfc`. `VillageJobSiteReconciliationPolicy.Restauracion`/`PlanRestauracion`
  (decisión pura) + `restaurarDueniosDelLibro` limpia las memorias ajenas y **después** devuelve el
  puesto al titular; no suelta tickets (pertenecen al puesto).
- **Pruebas:** `VILLAGE_JOB_SITE_OWNERSHIP_POLICY_TEST` **54** (39+15: restaura, limpia ajenos sin
  duplicados/ordenados, no limpia al titular, 6 negativos, idempotencia); persistencia 544; skills 376;
  `test` + `compileJava -Pa2nDiagnostics=true` verdes; lint OK; `git diff --check` 0. Informe:
  `2026-11-24-L3-JOB-SITE-RESTORE-OWNER.md`.
- **Límite:** sin fixture runtime de dos aldeanos reales; C4/POI no reejecutados. Si el libro estuviera
  obsoleto, manda el libro (I393).
- **Siguientes:** L2/L4, soak multi-día, servidor dedicado/UI de skills, decisión de ramas/A1.

# P1 RESUELTO — SKILLS: ÁRBOL SIN CLIENTE Y PRIMER GATE (2026-11-24)

- **Fallo estructural corregido:** `PlayerSkillCapabilityImplementation` (común) construía
  `ClientSkillBuilderFromJson`, que llamaba a `Minecraft.getInstance().getResourceManager()`. En un
  servidor dedicado con jugador podía lanzar `NoClassDefFoundError`/NPE. Ahora el árbol se lee del
  **classpath** del mod (`/assets/devilrpg/skills/root_complete.json`), sin `Minecraft`; los iconos se
  resuelven contra `BuiltInRegistries.ITEM` (antes `DeferredHolder.get()`, que revienta sin mod cargado).
- **Código probado:** `0a9f8670`. Nuevo `src/skillTest` + tarea `skillCapabilityTest` dentro de `test`:
  **376 aserciones** (33/33 skills con coste/display, 36 nodos, 3 raíces, 6 `PowerEnum`, 3 loadouts,
  mapas NBT completos, partida vieja sin `SOULVINE` que la recibe sin pisar `CHARGE=7`, iconos).
- **Gates:** `test` + `compileJava -Pa2nDiagnostics=true -Pa2nProbeOnly=true` verdes (1m38s); lint
  estricto OK; `git diff --cached --check` 0. Informe:
  `2026-11-24-P1-SKILLS-SERVER-SAFE-TREE-AND-TESTS.md`.
- **Límites:** **no** hay corrida de servidor dedicado con jugador ni UI de cliente reejecutada; el
  resource pack ya no puede sobrescribir el árbol; la doble ejecución cliente/servidor sigue sin revisar.
- **Siguientes:** (P2) L3/L2 del libro de puestos; (P3) soak multi-día; decisión de ramas/A1 del dueño.

# L1 RESUELTO — LIBRO DE PUESTOS AL DESAPARECER EL TITULAR (2026-11-24)

- **Auditoría de pendientes:** `2026-11-24-PENDING-AUDIT.md` (`2b32ba08`). Skills 33/33 implementadas
  pero **0 tests** y con dependencia de cliente en código común; navegación A2/NAV aceptadas; único
  criterio del Master Plan sin ejecutar: **soak multi-día**.
- **Fallo L1 corregido:** el libro `VillageSavedData.JobSiteOwners` no se limpiaba al **morir** o
  **convertirse** el titular, así que la estación quedaba libre en `PoiManager` pero reservada para
  siempre (`claimJobSite` rechazaba al reemplazo).
- **Código probado:** `7db821ea` (docs después). Hook en `VillageAgentRegistry.onDeath` y
  `onConversion`; política pura `puestosDelTitular`; `releaseJobSitesOwnedBy`; log en `VillageManager`.
  **No** se limpia en descarga de chunk ni se fuerza `release` (muerte: vanilla; conversión:
  `soltarTicketsPerdidos`).
- **Pruebas:** `VILLAGE_PERSISTENCE_INTEGRATION_TEST` **544** (525+19), `VILLAGE_JOB_SITE_OWNERSHIP_POLICY_TEST`
  **39** (32+7); `test` + `compileJava -Pa2nDiagnostics=true -Pa2nProbeOnly=true` verdes; lint estricto OK;
  `git diff --check` 0. Informe: `2026-11-24-L1-JOB-SITE-LEDGER-DEATH-CLEANUP.md`.
- **Límite:** el disparador de muerte/conversión está verificado por compilación/revisión, **no** con una
  corrida runtime de un aldeano muriendo; C4/POI no se reejecutaron (no matan aldeanos).
- **Siguientes objetivos independientes:** (P1) skills sin tests + dependencia de cliente; (P2) L3/L2;
  (P3) soak multi-día; decisión de ramas/A1 del dueño.

# CAMPAÑA — CIERRE TÉCNICO 23/24-NOV-2026: C4 11/11, POI REAL, NAV 21/21 (2026-11-24)

- **Checkpoint/protocolo:** `2026-11-24-CONTINUITY-CHECKPOINT.md`.
- **Código probado:** `1016a7cf` (docs-only después). Informe completo:
  `2026-11-24-TECHNICAL-CLOSURE-CAMPAIGN.md`.
- **C4:** `c4n-20261124-0250` → `C4N_PASS` 3/3, `trabajoTicks=2400`, `-ExigirCobertura`; 12/12 profesionales,
  4/4 fundadores, **cobertura 11/11** (NITWIT sin estación), 0 duplicados/huérfanos, guardias/máscara/equipo,
  save/reload. `ownership_demostrado=false`, `cobertura_demostrada=true` (C4 no prueba el ticket).
- **JOB_SITE:** causa principal = cerebro generado con `NONE` sin `refreshBrain`; `ValidateNearbyPoi` borraba el
  JOB_SITE cerca de la estación. Además: gracia 1200 > ventana C4 600, `POTENTIAL` vanilla, conteo por profesión,
  guardias contados como dueños. Fix: refreshBrain, reconciliación, libro persistido de dueños
  (`VillageSavedData.JobSiteOwners`) y restauración sin `release`+`take`.
- **F1 (`fixture_duplicado_ticket_cogido`):** el fixture forzaba el libro a A; el dueño real/persistido era B y
  producción lo conservaba correctamente. Se corrigió el arnés (keeper = dueño del libro; NO_MEDIDO si no está
  cargado) y se añadió filtro circular de valla también en producción. `poi-final-20261124-0305` PASS 4/4.
- **POI:** `poi-final-20261124-0305` → `POI_PASS` 4/4 (ServerLevel/PoiManager, digest vivo==disco, tardío,
  F1/F2/F3, **F4 dos POTENTIAL/1 ticket sin fantasma**, parcial `MEDIDO_OK` con 6 aldeanos/0 liberaciones ajenas).
- **NAV:** `nav-final-20261124-0255` → `NAV_PASS` 21/21; 86 búsquedas, 75 completadas, 2.044 expansiones,
  431 ms de planner, 3 presupuestos artificiales, 1 reanudada. Fixes reales: BUILD_FEET_NUDGE y
  `terrainChanged` rearm; escenarios/geometría/preflight/C10 corregidos sin relajar seguridad.
- **Rendimiento:** `build/av/perf-final/`; validación de colisiones domina; fastutil A/B 750 rutas idénticas,
  **sin ganancia runtime declarada** (LOCAL no usa esa estructura).
- **Gates:** `test` (village 525, ownership 32, build 39, nav contract 253, POI 15), `policyTest`,
  `navScenarioContractTest`, `compileJava -Pa2nDiagnostics=true -Pa2nProbeOnly=true`, `lint_aldea --strict`,
  `git diff --check` verdes.
- **Deudas explícitas:** A2 no reejecutado; mundos históricos sin libro pueden bloquear una estación hasta cargar
  al dueño; POI no mide horas/jugador; navegación de una entidad; `reanudados` de C4 es señal débil.

# CAMPAÑA — INCREMENTO 3 COMPLETADO: C4 RUNTIME, NBT REAL, MÉTRICAS Y HERRAMIENTAS (2026-11-23)

- **A · C4 runtime PASS (final, con el arnés enriquecido):** dos campañas consecutivas con mundos nuevos,
  `c4n-20261123-1550` (SHA `69d7e55a`) y `c4n-20261123-1602` (SHA `5c427842`, docs-only sobre el mismo código).
  Ambas: `C4N_PASS prepare=PASS reload1=PASS reload2=PASS`, 3 fases con `roster_invariantes_ok=true`,
  `guardado_ok=true` y `fase_ok=true`; 12/12 profesionales, 4 fundadores (máscara `0b1111`, 4/4 con goal, armadura
  4/4), 0 pérdidas, 0 duplicados, 0 huérfanos; `JOB_SITE` 1→3→3 y 2→3→3, **3/3 estables** en reload2 (0
  cambiados/perdidos; 0 duplicados/huérfanos al cargar). Evidencia previa aceptada: `c4n-20261123-024`
  (SHA `1abab57d`). Informe: `2026-11-23-INCREMENT-3-RUNTIME-PERSISTENCE-INSTRUMENTATION.md`.
- **B · NBT/save-reload DONE:** `VillagePersistenceIntegrationTest` (**484 aserciones**, gate `test`): `CompoundTag`,
  `VillageSavedData.save/load`, `NbtIo` a fichero, `Villager` real + `EntityType.create` + `Brain/JOB_SITE`, dos
  ciclos, carga parcial, corrupción y reconciliación. Defecto real corregido: `VillageSavedData.readBlueprint`
  descarta planos corruptos (antes lanzaba al cargar/recorrer). Limitación: `StubLevel` sin `PoiManager`/`ServerLevel`.
- **D · Instrumentación DONE:** `NavPlannerMetrics` (flag `devilrpg.nav.metrics` OFF por defecto), `NavPlanner`
  instrumentado sin tocar `NavGraphSearch`; `NavPlannerMetricsTest` (68) y `NavSearchBaselineTest` (119, 5 escenarios
  deterministas + 4 entidades + ON/OFF idéntico). `-PnavMetrics=true` disponible; C4 vuelca `metrics-<fase>.txt` si
  se habilita. La navegación real en runtime sigue sin medirse (A2 cerrado).
- **E · Herramientas DONE:** arnés C4 con `AL_CARGAR`, `jobsite-<fase>.txt`, comparación de propiedad, guardias,
  `esperado/observado/invariantes/causa_conocida/causa_desconocida`, `guardado_ok` y `done` tras el guardado;
  `tools/av/{diagnose,collect,nav-baseline}.ps1` + `LEEME.md`. `GuardHarness` evaluado y **no portado**.
- **F · Documentación DONE:** este checkpoint, `TEST-MATRIX.md`, invariantes I387–I391 y el informe del incremento.
- **Gates:** `compileJava`, `compileTestJava`, `test` (incluye `policyTest` 74 clases y `villagePersistenceTest`
  484), `navBaselineTest` (119), `compileJava -Pa2nDiagnostics=true`, `lint_aldea.py --strict`, `git diff --check` —
  todos verdes. Campañas finales `c4n-20261123-1550` y `c4n-20261123-1602` PASS; el `SHA` de código probado es
  `69d7e55a`.
- **Limitaciones declaradas:** `JOB_SITE` solo en 3/12 aldeanos (no se exige cobertura); `reanudados` es señal débil;
  D no mide navegación real; B no prueba `PoiManager`. Campaña **COMPLETA** para los objetivos ejecutables.

# HISTÓRICO — INCREMENTO 3: REVALIDACIÓN C4 CON CENSO OBLIGATORIO (2026-11-23)

- **Objetivo A cumplido (primera campaña):** `c4n-20261123-024` (SHA `1abab57d`) — PREPARE, RELOAD1 y RELOAD2
  **PASS** con `roster_invariantes_ok=true` obligatorio. 12/12 puestos, 4 fundadores, 0 duplicados, 0 pérdidas,
  identidades/profesiones/goals/reanudación correctos. Esta campaña usó el arnés anterior; la campaña final
  `c4n-20261123-1550` valida el arnés enriquecido.

# CAMPAÑA DE EVOLUCIÓN — INCREMENTO 2: MATRIZ DE NAVEGACIÓN Y CENSO OBLIGATORIO (2026-11-23)

- `NavTerrainMatrixTest` (policyTest, ~40 aserciones): STEP_UP, DROP, DROP+overshoot, BREAK+BUILD (fases del escalón), presupuestos del túnel; escenarios RUNTIME documentados.
- El arnés C4 ahora **falla** si `VillageRosterInvariants` no se cumple (antes solo lo registraba).
- Gates verdes. Pendiente: revalidación C4 con el censo obligatorio (runtime), prueba NBT de save/reload, instrumentación de rendimiento y herramientas.

# CAMPAÑA DE EVOLUCIÓN — INCREMENTO 1 VALIDADO; CAMPAÑA ABIERTA (2026-11-23)

- Informe: `docs/autonomous-village/2026-11-23-AUTONOMOUS-VILLAGE-EVOLUTION-CAMPAIGN.md`.
- Entregado: `VillageRosterInvariants` (puro: 12 puestos, 4 fundadores, un dueño por puesto, cupos, UUID únicos) + `VillageRosterInvariantsTest` (11 aserciones) + integración informativa en el arnés C4 (`ROSTER_INVARIANTS` en `phase-*.json`); `VillageJobSiteOwnershipPolicyTest` ampliado a 13 (idempotencia, no destrucción, dos ciclos).
- Gates: compileJava, compileTestJava, test, policyTest, lint, diff-check — verdes.
- A2/C4 intactos: verificador de solo lectura, sin cambios en navegación/persistencia.
- Pendientes ejecutables: matriz de navegación (C), medición de rendimiento (D), GuardHarness/métricas (E), integración NBT de save/reload (B) y revalidación de invariantes en campaña C4. La campaña NO se declara completa.

# A1 — AUDITORÍA FORENSE DE LOS 6 COMMITS DE `migration-neoforge-1.21.1` (2026-11-23)

- Informe: `docs/autonomous-village/2026-11-23-A1-SIX-COMMITS-FORENSIC-AUDIT.md`.
- **Recomendación: NO incorporar ninguno de los seis commits.** A1 (4 guardias de fundación armados, adicionales, sin tocar los 12 puestos) **ya está implementado y validado en AV** (`spawnGuardiasFundadores`, `guardiasFundadoras` persistente, `esGuardiaFundadora`, `equiparDesdeAlmacen`; A2 midió 4/4 fundadores equipados; C4 midió 12/12 puestos con 16 aldeanos).
- Los commits de la otra rama son una implementación paralela anterior con **nombres y persistencia distintos** (`GuardiaDeFundacion`/`Set` vs `GuardiaFundadora`/máscara): portarlos crearía un doble mecanismo de fundación (riesgo C4). Todos conflictúan textualmente con AV (`git merge-tree`).
- A2 y C4 permanecen **ACCEPTED**; no se ha modificado código, tests ni evidencia.

# AUDITORÍA DE CONSOLIDACIÓN — A2 Y C4 ACEPTADOS; SIGUIENTE ETAPA PENDIENTE DE DECISIÓN (2026-11-23)

- **Informe:** `docs/autonomous-village/2026-11-23-AUTONOMOUS-VILLAGE-CONSOLIDATION-AUDIT.md` (auditoría completa: Git, ramas, NeoForge, navegación §K/§L, persistencia, reconciliación, muro, Full12K, contrato de finalización y hoja de ruta).
- **A2:** CLOSED — ACCEPTED (3 corridas, 8/8 estricto, C4 integrado PASS).
- **C4 standalone / Save-ReLoad:** PASS — ACCEPTED (3 campañas consecutivas; 12/12, 0 duplicados, 0 pérdidas).
- **Git:** `feature/autonomous-village` en `d27bd6c7`, **33 commits por delante** de origin, merge-base `fea54ef7`, árbol limpio salvo untracked preservados.
- **NeoForge:** AV ya es NeoForge 1.21.1 (moddev 2.0.146 / 21.1.249). `migration-neoforge-1.21.1` comparte base `c2fb3699` y aporta **6 commits exclusivos de A1** (guardias de fundación) **no portados** a AV. Decisión de integración pendiente del dueño; recomendación: AV autoritativa + port selectivo revisado.
- **Defectos abiertos demostrados:** ninguno. Quedan limitaciones conocidas, deuda técnica (ruta vanilla de POI, cobertura de tests de persistencia, §L) y una decisión de ramas (§K.1).
- **Full12K:** sin definición independiente; equivale a las corridas de 12000 ticks ya aceptadas.
- **Muro (`5ad02088`):** se conserva; cumple banda 60,5–65, cadencia y alcance, sin tocar protección/lease/soporte/AABB/headroom.
- **Siguiente:** el dueño debe decidir (1) estrategia de ramas, (2) si A1 se porta a AV, (3) aprobar el contrato de finalización. No se inicia implementación hasta esa decisión.

# C4 STANDALONE + SAVE/RELOAD — PASS (2026-11-23)

- **Aceptación: 3 campañas completas consecutivas PASS** (`c4n-20261123-021`, `-022`, `-023`), cada una con PREPARE + RELOAD1 + RELOAD2 en PASS, 12/12 presentes, 0 pérdidas, 12/12 profesiones/goals/reanudados, **0 duplicados de job site**, actividad efectiva y guardado/cierre/reapertura verificados.
- SHA de aceptación: `0ab01d0da7807cffa638661bfb3e1b6e0ac60686`; producción: `ff48e257` sobre `2fb687ab`, `41e58087`, `09255bfd`.
- Causa raíz demostrada (015/016/017): al cargar, el reparto veía los POI persistidos como «cogidos SIN dueño» (memorias aún no cargadas) y **liberaba sus tickets**; además la aldea repoblaba antes de cargar y los goals de minero/herrero liberaban tickets ajenos. Correcciones: un-puesto-un-dueño + reconciliación (`09255bfd`), ventana de gracia de repoblado (`41e58087`), comprobación de propiedad a escala de aldea en los goals (`2fb687ab`), reparto aplazado durante la carga (`ff48e257`).
- Instrumentación: censo solo con entidades+chunks cargados (`CARGA_COMPLETA tick=5`), tabla por tick antes/después de mantenimiento; las memorias `JOB_SITE` llegan **vacías** al cargar (el duplicado no es persistido, se crea después). El arnés ahora espera la carga completa antes de cualquier mantenimiento (corrige el escenario de `020`).
- Tests: `VillageJobSiteOwnershipPolicyTest` (9) y `VillagePopulationLoadPolicyTest` (7); gates: compileJava, compileTestJava, test, policyTest, lint, diff-check — todos PASS.
- Informe: `docs/autonomous-village/2026-11-22-C4-STANDALONE-SAVE-RELOAD-ACCEPTANCE.md`.
- A2 sin regresión requerida (cambios de población/puestos, no de navegación).

# C4 STANDALONE / SAVE-RELOAD — EN CURSO (2026-11-23)

- Arnés `tools/c4n/C4StandaloneHarness.java` + lanzador `tools/c4n/lanzar-c4n.ps1` (untracked): fases prepare/reload1/reload2 sobre el MISMO mundo desechable, con censo 12/12, identidades, goals, reanudación, job sites y actividad.
- **Causa raíz del duplicado (demostrada en `c4n-20261123-015` y `-017`)**: al cargar, el reparto veía los POI de los trabajadores persistidos como «cogidos SIN dueño» (sus memorias JOB_SITE aún no estaban cargadas), **liberaba sus tickets** y otro aldeano los reclamaba → dos memorias sobre el mismo POI. En `015` además la aldea repobló antes de cargar (trabajador nuevo + pérdida de uno esperado).
- **Correcciones commiteadas**: `09255bfd` (política un puesto/un dueño + reconciliación), `41e58087` (ventana de gracia de carga: no repoblar), `2fb687ab` (los goals del minero/herrero no liberan tickets ajenos; comprobación a escala de aldea), `ff48e257` (aplazar el reparto completo 1200 ticks desde el arranque).
- **Tests**: `VillageJobSiteOwnershipPolicyTest` (9), `VillagePopulationLoadPolicyTest` (7), registrados en policyTest. Gates compile/compileTest/policyTest/lint/diff-check verdes.
- **Estado de aceptación**: NO conseguido aún. `011`,`012`,`013` dieron 3 campañas PASS (antes de los endurecimientos); `014` PASS; `015`,`017` fallaron por el duplicado (ya corregido en `ff48e257`); `016` falló por el same (fix previo insuficiente); `018` **crasheó por ServerHangWatchdog en `VillageGenerator.isVegetation`** durante la generación del mundo (sitio de aldea hostil, no es el arnés). Pendiente: 3 campañas consecutivas sobre `ff48e257`.
# A2 CERRADO FORMALMENTE — CLOSED / ACCEPTED (2026-11-22)

**Veredicto:** A2 (Zombie Siege) queda **CLOSED — ACCEPTED**. Informe de cierre:
`docs/autonomous-village/2026-11-22-A2-FINAL-ACCEPTANCE-AND-CLOSURE.md`.
Build aceptada: `5ad020882a184e8e6e95d1398175fb598b600754`. HEAD al cerrar: `dce7d504aa153c15f95bfe196da7232f0f6cb844`.

**Criterio:** 8/8 estricto DENTRO-PARED (distancia ≤62) en dos corridas consecutivas con C4 integrado PASS,
ventana oficial 12000 ticks (todas las entradas antes de t=8668), semilla 12345, mundos nuevos.

**Corridas aceptadas (tres consecutivas):**

| RUN_ID | Veredicto | Estricto | Fase B |
|---|---|---|---|
| `a2n-20261122-054142` | C4_PASS | 8/8 | 8/8, entraronPerimetroEver=8 |
| `a2n-20261122-094529` | C4_PASS | 8/8 | 8/8, entraronPerimetroEver=8 |
| `a2n-20261122-100646` | C4_PASS | 8/8 | 8/8, entraronPerimetroEver=8 |

**Corrida NO contabilizada:** `a2n-20261122-055413` (8/8 entradas, sin `fin.txt`, interrumpida antes del C4).

**Pendiente inmediato (documentado, no ejecutado):** revisar la exención estrecha del presupuesto del muro
(`5ad02088`); C4 standalone/save-reload; reconciliar diagnósticos históricos BUILD/BREAK/DROP; definir si
existe una campaña «Full12K» distinta de las corridas de 12000 ticks. Detalle completo en el informe §10.

**Restricciones de este cierre:** sin cambios de código ni tests, sin Gradle, sin runtime, sin tocar mundos.
El historial de fallos y diagnósticos se conserva íntegro.

# CURRENT CHECKPOINT — A2 ACEPTADO: 8/8 estricto en TRES corridas consecutivas + C4 PASS (2026-11-22)

**Criterio cumplido con margen**: 8/8 estricto DENTRO-PARED en tres corridas completas consecutivas con C4
integrado PASS (el criterio pedía dos), semilla 12345, ventana oficial 12000, mundos nuevos y desechables.

```text
a2n-20261122-054142 → C4_PASS · estrictos=1,2,3,4,5,6,7,8 · entranPerimetroEver=8
a2n-20261122-094529 → C4_PASS · estrictos=1,2,3,4,5,6,7,8 · entranPerimetroEver=8
a2n-20261122-100646 → C4_PASS · estrictos=1,2,3,4,5,6,7,8 · entranPerimetroEver=8
```

**Build aceptada**: `5ad02088` (narrow wall exemption + descent break cost), encima de `314224ed`, `d4515543`,
`f574b4af`, `55d9d430`, `1cf7d8ef`, `83e10d86`, `92083380` y `9a4553af`.

**Las dos correcciones finales que cerraron A2**:
1. **Exención estrecha del muro**: romper una celda del anillo no consume el presupuesto de 40 bloques de
   terreno, **solo** cuando el ente ya está en la banda del anillo (60,5-65 del objetivo); la cadencia se
   mantiene y lejos del muro el contrato no cambia. Desbloqueó IDX4 (llegaba con `tunel 0/40`).
2. **Penalización de BREAK en descenso largo** (+120 si la meta está >6 por debajo): el A* prefiere caídas y
   pasos de borde en la bajada de la montaña.

**Reporte completo**: `docs/autonomous-village/2026-11-22-A2-ZOMBIE-SIEGE-RELIABILITY.md` (§0 y §5-bis/§6).
**Preservación**: solo mundos nuevos `build/a2n/<runId>/game/a2n`; `run/a2n`=False; `run/saves` intacto; sin
borrados ni `reset --hard`; untracked previos conservados.

# CURRENT CHECKPOINT — Ronda 12: reserva en descenso probada y revertida; varianza bimodal (2026-11-22)

- **Reserva de presupuesto en descenso largo (≤8 bloques, no romper terreno no-muro): probada y REVERTIDA.**
  `a2n-20261122-044527` (12000) dio **4/8** (IDX2,6,5,1) — el mismo patrón que las otras dos corridas con
  cambios en `romperNodoDeRuta` (040302 exención, 041739 reserva final). HEAD vuelve a `314224ed`.
- **Hallazgo: la varianza es BIMODAL** — la build validada produce **7/8** (034636, 043133) o **4/8**
  (las tres corridas con cambios en el ente). En las 4/8 fallan IDX3/7/8 en la fase tardía. No se puede
  concluir con una sola corrida; hay que medir 2-3 por build.
- **IDX4 no se arregla desde `romperNodoDeRuta`**: tocar ese punto coincide con la atracción mala. La vía
  siguiente es el PLANIFICADOR: penalizar (no denegar) las aristas BREAK cuando el desnivel de descenso es >6,
  para que el A* prefiera caídas/pasos de borde y el presupuesto llegue al muro.
- Estado: **7/8 estricto reproducible + C4 PASS** (ventana oficial); IDX4 a 62,9-63,2 con `tunel 0/40`.

# CURRENT CHECKPOINT — Ronda 11: 7/8 reproducible en ventana oficial; IDX4 a ~1 bloque (2026-11-22)

- Sin cambios netos de código (reserva probada y revertida). HEAD `60606d8f` sobre `314224ed`.
- **`a2n-20261122-043133` (12000, build validada): 7/8 estricto** (IDX2,6,5,1,3,8,7) + **C4 PASS**.
  **IDX4: `600,71,549`, dist 62,91** (entroPerimetroEver=true). Reproduce el 7/8 de `034636`: son dos corridas
  con 7/8 en la ventana oficial.
- **Varianza medida**: la build validada dio 7/8 (034636, 043133) y 4/8 en dos corridas con cambios de prueba
  (040302 con exención, 041739 con reserva). Los 4/8 no son atribuibles con certeza a esos cambios: hay
  variabilidad de orden de tick/combate entre corridas. Medir siempre con varias corridas por build.
- **Reserva de brecha (≤6 bloques, no romper terreno no-muro): NO ayuda a IDX4** porque gasta los 40 bloques en
  la MONTAÑA antes de llegar a la banda del muro. Revertida.
- **Causa raíz final de IDX4**: la ruta de descenso TALADRA la montaña (BREAK_HEAD/BREAK_STAND casi cada bloque)
  y consume el presupuesto; llega al muro con 0/40 y no puede abrir la brecha (dist 62,9-63,2).
- **Siguiente hipótesis (ronda 12)**: denegar el BREAK de ruta en descensos largos (desnivel >6) para forzar la
  bajada por caídas/pasos de borde, preservando el presupuesto; validar con 2-3 corridas 12000 y medir si IDX4
  llega al muro con bloques y cruza a <=62.

# CURRENT CHECKPOINT — Ronda 10: exención del muro probada y REVERTIDA (2026-11-22)

- **Exención de presupuesto para romper el muro objetivo: probada y REVERTIDA.** La corrida
  `a2n-20261122-040302` (12000, con exención) dio solo **4/8 estricto** (IDX6,2,5,1) frente a 7/8 de
  `034636`: al no descontar presupuesto por el muro, los atacantes cambian su comportamiento de rotura y
  IDX3/7/8 se pierden. Commit `314224ed` sigue siendo el estado válido (HEAD actual).
- **Causa raíz vigente de IDX4**: su presupuesto `tunel 0/40` se gasta en el DESCENSO de la montaña (ruta con
  BREAK_HEAD/BREAK_STAND en y=109-77 y en el borde exterior del muro). La brecha final no puede romper.
- **Siguientes (sin exenciones globales)**: (1) que el descenso NO consuma el túnel — preferir DROP/paso de borde
  y negar BREAK de ruta en pendiente cuando la misión está por debajo (preservando la cadencia); (2) validar con
  dos corridas 12000 seguidas.

# CURRENT CHECKPOINT — Ronda 9: 7/8 en ventana oficial; IDX4 sin presupuesto de túnel (2026-11-22)

- Commit `314224ed`. **Corrida `a2n-20261122-034636` (ventana oficial 12000): 7/8 estricto**
  (IDX6 t=691, IDX2 t=719, IDX5 t=1888, IDX1 t=2173, IDX3 t=3014, IDX8 t=4572, IDX7 t=7896) **+ C4 PASS**.
- **Brecha en el anillo**: fuera del recinto y en la banda del muro, el plan de túnel se pide DIRECTAMENTE
  (antes solo tras fallar el A*). **Descenso progresivo desde desnivel ≥3** (antes 10): IDX5 entró en t=1888
  tras atascarse en y=76.
- **IDX4 (único ausente) — causa raíz medida**: llegó a `601,71,553` dist **63,10** (a 0,1 del criterio) pero su
  **presupuesto de rotura está en `tunel 0/40`**: gastó los 40 bloques en el descenso de la montaña, así que la
  brecha no puede romper el muro. Es un problema de ASIGNACIÓN de presupuesto, no de navegación.
- **Siguientes**: (1) preservar presupuesto para la brecha final (el descenso no debe gastar los 40: cortar
  roturas no esenciales de la ruta de bajada, o reservar los últimos bloques para la muralla); (2) validar que
  IDX4 entra con presupuesto; (3) dos corridas 8/8 con C4 en ventana oficial.

# CURRENT CHECKPOINT — Ronda 8: ventana oficial 12000 medida; dos bloqueos concretos (2026-11-22)

- Sin cambios de código. Corrida con la **ventana OFICIAL de aceptación (12000 ticks)**:
  `a2n-20261122-032458` → 5/8 estricto (IDX2,6,1,7,8) + **C4 PASS** en 12000.
- **IDX4 llegó al borde estricto**: final `602,71,556` dist **63,16** (entroPerimetroEver=true) pero no cruzó a
  <=62. Necesita ~1,2 bloques hacia dentro: el avance de banda y la brecha no lo metieron. Es el bloqueo más
  cercano (a 0,16 del criterio).
- **IDX5 se quedó en y=76** (`602,76,469`, dist 86,2): su descenso se estanca tras bajar de 87; el paso de borde
  no encadena los últimos bloques.
- IDX3 esta corrida no entró (final 604,71,580, 74,05); en la corrida 6000 sí entraba (t=2664). La duración de la
  fase B cambia el calendario del arnés: comparar solo corridas con la misma configuración.
- **Siguientes**: (1) brecha determinista en el anillo para dist 62-64 (romper la celda del muro hacia la misión y
  pisar dentro) — desbloquearía IDX4; (2) desbloqueo del descenso de IDX5 en y≈76; (3) dos corridas 8/8 con C4.

# CURRENT CHECKPOINT — Ronda 7: descenso largo desbloqueado (IDX4 122→75), falta tiempo (2026-11-22)

- Commits `f574b4af` y `d4515543`. **Descenso largo de IDX4/IDX5 desbloqueado**: el paso de borde DROP ahora
  arranca desde un despegue válido aunque la navegación siga activa (antes `navigationDone=false` lo impedía y
  el nudge se re-emitía cada tick, 40 intentos y replan eterno). Restringido a misiones POR DEBAJO para no
  romper la aproximación de IDX1/7/8 (la versión sin restringir solo logró 2-3/8).
- Medido: IDX4 **122→75** (antes se quedaba en 109-116), IDX5 87→76, en `a2n-20261122-031519`:
  **6/8 estricto** (IDX1,2,3,6,7,8) + C4 PASS.
- **Las corridas son deterministas** por build: mismo código → mismas posiciones/tiempos. La "varianza" era
  efecto de los cambios entre corridas; ahora se puede iterar A/B con fiabilidad.
- **Bloqueo restante = TIEMPO**: IDX4 (y=75, dist 72.9) e IDX5 (y=76, dist 86.2) siguen bajando al llegar a
  t=6000; a ~117 ticks/bloque el descenso de 51 bloques no deja margen para la entrada al muro. Siguiente:
  acelerar el descenso (pasos de borde encadenados o menores de 3 bloques por caída) sin tocar presupuestos.

# CURRENT CHECKPOINT — Ronda 6: causa raíz del DROP de IDX4 identificada (2026-11-22)

- Sin cambios de código netos (se probó y revirtió un contador de intentos con progreso). HEAD `55d9d430`.
- **Causa raíz medida del bucle de IDX4** (`a2n-20261122-024128` y `-025141`): el nodo DROP `568,115` con dy=-1
  se ejecuta con `salida = (x_aterrizaje, y_mob, z_aterrizaje)` (la celda ENCIMA del aterrizaje). Para dy=-1 esa
  celda no es pisable (su soporte sería el propio aterrizaje, que debe ser aire): el mob queda en 568,116 con
  `DROP_COMMAND`/`DROP_VALID` cada tick, `distanceToLanding` 0,916→0,732 y vuelta atrás cada 40 intentos.
  El contador de progreso (probado) lo convierte en bloqueo permanente, así que se revirtió.
- **Arreglo propuesto (siguiente)**: en `describirDescenso`, para dy=-1 con aterrizaje contiguo en horizontal, la
  salida debe ser la CELDA DEL MOB (walk-off lateral), no la celda sobre el aterrizaje; validar con las pruebas
  de DROP existentes y con los ataques que ya funcionan (IDX3/5 bajan al interior con dy=-1 y no deben romperse).
- Estado vigente: **7/8 estricto** (`012959`, `020402`) con C4 PASS; IDX4 es el único ausente (se queda en
  y≈116 cerca de 568,116,524).

# CURRENT CHECKPOINT — Ronda 5: cadencia de escalera corregida; IDX4 sigue atascado (2026-11-22)

- Commit `55d9d430`. Mejor resultado vigente: **7/8 estricto** (`012959`, `020402`: IDX1,2,3,5,6,7,8) con C4 PASS.
- **Cadencia de escalera**: el ejecutor retornaba en CADENCE_WAIT sin llamar al constructor, así que
  `escaleraRetraso` no avanzaba nunca (IDX4 con `A2_LOCAL_STAIR_WAIT CADENCE_WAIT counters_unchanged=true`
  miles de ticks). Ahora el camino de espera llama al constructor para consumir la cadencia.
- **Resultados negativos documentados de IDX4** (no repetir a ciegas):
  - Taladro lateral (romper suelo delante y bajar 1): aborta `destino_invalido` en pendientes sin soporte.
  - Taladro vertical (romper el suelo propio y caer): el mob de AABB 0,6 **no cae** al hueco 1×1 ni centrado
    a 0,20 (su caja sigue tocando la celda vecina). Revertido.
  - `a2n-20261122-024128`: IDX4 bajó 122→116 y se quedó ahí; el DROP/escalón no encadena el descenso largo.
- **Siguiente**: analizar a fondo el **pipeline de descenso DROP** (por qué se estanca en y≈109-116) con apoyo
  de la IA local/multi-agente, y atacar la causa (cadencia de planes/rutas por tramo) sin tocar presupuestos.

# CURRENT CHECKPOINT — Ronda 4: 7/8 estricto dos veces más, solo IDX4 (2026-11-22)

- Commit `1cf7d8ef`. `a2n-20261122-020402` y `a2n-20261122-012959`: **7/8 estricto (IDX1,2,3,5,6,7,8), C4 PASS**.
- **Banda del muro determinista**: a <=66 del objetivo el micro-avance manda desde el gancho superior de PLANNING;
  todos los atacantes se pegan al anillo igual (antes dependía de que fallara el plan local → varianza).
- **Caducidad del objetivo de avance** (200 ticks): un objetivo elegido e inalcanzable bloqueaba el re-encolado
  para siempre (guardián "ya en curso").
- **Descenso progresivo** acotado (1-3 bloques, 40 ticks): ayuda en fallos, pero IDX4 sigue bajando solo
  ~10 bloques por corrida (122→109/114) porque el DROP/escalón no cubre el desnivel de la montaña.
- **C4**: 2 de 5 corridas dieron C4_FAIL SOLO por `jobSite=[0,0,0]` (variabilidad del subsistema de puestos).
- **Siguiente**: taladro descendente dedicado para IDX4 (romper suelo y caer 1-2 bloques de forma controlada,
  sin tocar presupuestos globales), revalidar C4 y buscar dos 8/8 consecutivos.

# CURRENT CHECKPOINT — Ronda 3: 7/8 estricto (solo falta IDX4), C4 PASS (2026-11-22)

- Commit `83e10d86`. Mejor run de la campaña: `a2n-20261122-012959` → **7/8 estricto**
  (IDX1,2,3,5,6,7,8), C4 PASS. IDX8 entró en t=5176. Solo falta IDX4.
- **Aproximación al muro**: cerca del anillo (<=66 del objetivo) el avance acepta pasos de 0,5 bloques y el
  túnel se intenta también al agotarse el presupuesto local (no solo con ruta vacía).
- **Timeout del escalón pendiente**: un escalón colocado e inalcanzable bloqueaba al constructor para siempre
  (IDX4: CADENCE_WAIT/PENDING_NOT_TRAVERSED miles de ticks, escalera 7/24 congelada). A los 400 ticks se
  abandona (queda en el mundo) y se reanuda desde la posición real.
- **Varianza**: misma build, `a2n-20261122-013840` dio 4/8; la divergencia por combate/tiempo por corrida es
  ahora el obstáculo principal para 8/8 dos veces. Siguiente: estabilizar IDX4 (descenso de 51 bloques) y
  endurecer el encadenado de todos los atacantes (sin depender de que el A* "tropiece" con la brecha).

# CURRENT CHECKPOINT — Ronda 2: 6/8 estricto con IDX7 entrando, C4 PASS (2026-11-22)

- Commit `722bc5ed` sobre `1e4f8210`. Mejor run de la ronda: `a2n-20261122-011942` → **6/8 estricto**
  (IDX1,2,3,5,6,7), C4 PASS. IDX8 quedó en el recinto a dist 63,7; IDX4 a 72,6 con salud 3,84.
- **Techo del stand**: al saltar al siguiente escalón la cabeza golpeaba el techo del stand (S+2) y el salto
  quedaba capado (`pathCanReach=false`). Se despeja S+2 en la fase CLEAR → **14/14 y 16/16 subidas** completadas.
- **Entrada al recinto** (`ENCLOSURE_STEP`): paso de descenso de 1-3 bloques hacia la misión cuando la entidad
  está SOBRE el anillo (y=72, 62,46). IDX3 entró en `005924` (t=4204) e IDX7 en `011942` (t=3853).
- **Umbral del ascenso**: el ascenso trepaba el muro (71→72) y dejaba al mob a 0,46; ahora exige desnivel ≥3.
  El avance del gancho superior también se limita a desnivel real.
- **Siguientes**: IDX8 (a 63,7 en la meseta) → brecha/paso al interior; IDX4 (descenso largo) → acelerar;
  después, dos corridas consecutivas 8/8 + C4 PASS.

# HISTORICAL — Encadenado del ascenso y deadlock de ruta duplicada (2026-11-22, ronda 1)

- Commit `023f1ab5` sobre `d4d61037`. El ascenso se intenta en CADA tick de PLANNING con desnivel ≥3 y sin
  enfriamiento tras una subida verificada (tope 12). Medido: IDX7 sube el escalón 1 en t=288 y **encadena** el
  segundo en t=289 (bloque colocado en t=379). El salto al segundo escalón falla por pathfinding
  (`pathCanReach=false`); se añadió salto asistido acotado.
- **IDX3 en el anillo**: deadlock medido (firma de ruta 2102820130 rechazada indefinidamente, 8 replanteos sin
  moverse). Ahora, tras 3 rechazos idénticos se reintenta la ruta (máx. 2 forzados por misión).
- Micro-avance en cruz (4 rayos, enfriamiento 20 ticks) para llegar pegado al acantilado.
- Runtime: `a2n-20261122-002353` 3/8 estricto (IDX1,2,6) con C4 PASS; `a2n-20261122-003217` 4/8 (IDX1,2,5,6) con
  C4_FAIL SOLO por `jobSite=[0,0,0]` (16/12/12 correctos; 13/14 corridas con C4 PASS): variabilidad del
  subsistema de puestos, a revalidar.
- **Siguiente**: resolver el salto al escalón 2 del acantilado (probar escalera en zigzag o colocar el escalón
  a distancia 2), estabilizar IDX3 en el anillo, y repetir corridas hasta 8/8 dos veces con C4 PASS.

# HISTORICAL — Fiabilidad del asedio zombi: 6/8 estricto, C4 PASS (2026-11-22)

**Branch:** `feature/autonomous-village`
**Base:** `9a4553af` (túnel subordinado)
**Report:** `docs/autonomous-village/2026-11-22-A2-ZOMBIE-SIEGE-RELIABILITY.md`

- Campaña de corrección completa del asedio zombi: **8 causas raíz corregidas** con evidencia runtime.
- Mejor run: `a2n-20261121-232035` → **6/8 estricto DENTRO-PARED** (IDX1,2,3,4,5,6), C4 PASS. Baseline 3/8.
- Correcciones: celdas de pie por colisión (nieve), tramo local acotado, overshoot DROP + abandono del
  take-off imposible, guardián de progreso real, troncos del muro rompibles para el planificador, cota
  dirigida de subida, ejecución de la ruta global/frente validada, escalón de ASCENSO (minar+colocar+subir),
  guarda de chunks descargados y tope de CPU por plan (el watchdog de 60 s queda cerrado).
- Tests nuevos/ampliados: `NAV_FEET_CELL_POLICY_TEST` 9, `NAV_LOCAL_OBJECTIVE_LEG_POLICY_TEST` 9,
  `NAV_DROP_OVERSHOOT_POLICY_TEST` 10, `NAV_BREAK_FILTER_POLICY_TEST` 7, `NAV_CLIMB_STEP_POLICY_TEST` 19,
  `NAV_LOCAL_OBJECTIVE_PROGRESS_POLICY_TEST` 10, `NAV_GOAL_METRIC_TEST` y lease explícito ampliados.
- Gates: compile/compileTest/policyTest, lint estricto y diff-check en verde. 10 corridas runtime en mundos
  nuevos; C4 PASS en todas las completadas.
- **Bloqueos restantes con evidencia:** IDX7/8 necesitan encadenar 6-8 subidas antes de t=6000 (el ascenso
  funciona: 6 CLIMB DONE; el A* intercala rodeos); IDX3 se queda a 0,46 del umbral en el anillo en
  `RECOVERING`; IDX4 entra solo cuando su descenso de 51 bloques llega a tiempo.
- **Siguiente:** prioridad absoluta del ascenso mientras `misionY - mobY >= 3` con muro adyacente; arreglar
  la recuperación en el anillo de IDX3; acelerar el descenso de IDX4 sin tocar presupuestos. Después,
  8/8 dos veces consecutivas + C4 PASS.

# HISTORICAL — Túnel subordinado integrado, A2 3/8 estricto (2026-11-21)

**Branch:** `feature/autonomous-village`
**Base:** `fea54ef73520b5e28e6426c4696d7f4fa1da8fec`
**Report:** `docs/autonomous-village/2026-11-21-A2-TUNNEL-SUBORDINATE-INTEGRATION.md`

- `TunnelActionPlanner` integrado en `NavExecutor` como **planificador subordinado**: emite pasos validados,
  no mueve ni modifica el mundo. `NavExecutor` sigue siendo la única autoridad (una acción por tick con lease).
- Nuevas piezas puras: `TunnelExecutionPolicy` (cursor/fases, no consume el paso sin ocupación física; LOS de
  la apertura 2-high) y `TunnelPlanPolicy` (topes 3/4/6, cooldowns, cardinales con progreso, prioridad de
  construcción). `TraversalActionLeasePolicy.canAcquireTunnel` añade el lease por bloque exacto.
- Pruebas: `TUNNEL_ACTION_PLANNER_TEST` 20, `TUNNEL_EXECUTION_POLICY_TEST` 31, `TUNNEL_PLAN_POLICY_TEST` 25,
  `TRAVERSAL_ACTION_LEASE_POLICY_TEST` 23. `policyTest` verde; lint estricto y `git diff --check` exit 0.
- Runtime (mundos nuevos, semilla 12345):
  - `a2n-20261121-211306`: 2/8 estricto, C4 PASS. Bug medido: el plan se descartaba en `MOVE_POST` por exigir
    `stand`. Corregido.
  - `a2n-20261121-212053`: 3/8 estricto (IDX1/4/6), 3/8 perímetro, C4 PASS. 3 planes, 9 pasos consumidos con
    soporte+AABB verificados, 0 descartes.
  - `a2n-20261121-213055`: 3/8 estricto (IDX1/4/6), 3/8 perímetro, C4 PASS, 0 activaciones de túnel (prioridad
    de construcción). Demuestra que la pérdida de IDX3 es varianza, no regresión.
- **A2 OPEN:** 3/8 estricto. Bloqueos restantes con evidencia: IDX2 termina FAILED por DROP source-cell recovery
  sin vecino seguro en `660,72,608` (reproducible en los 3 runs); IDX3/5 no completan el descenso por escalón;
  IDX7/8 están 8 bloques por debajo de la misión y se alejan.
- **Siguiente:** (1) recuperación de fuente DROP sin vecino seguro; (2) descenso por escalón en pozos por encima
  de la misión; (3) ascenso/rodeo para misiones por encima. No subir presupuestos globales ni relajar puertas.

# HISTORICAL — A2 breach+move iteration, 3/8 perimeter (2026-11-21)

**Branch:** feature/autonomous-village
**Runtime:** `a2n-20261121-123432`
**Report:** `docs/autonomous-village/2026-11-21-A2-DROP-FIX-ITERATION.md`

- DROP edge-step no-progress now excludes the exact edge and landing, then replans. IDX6 reached strict entry.
- Vertical probes are bounded to safe-drop descent, same level and +1 ascent; same-column descent only.
- A2 result: 2/8 strict (IDX1, IDX6), 2/8 perimeter, 2 deaths; C4 PASS.
- Remaining blocker: BREAK successors remain solid post-break states, so A* cannot tunnel out of 1×1 shafts;
  IDX7/8 exhaust global budget; IDX4 leaf/DROP cycle; IDX2 local planning failure.
- Static compile/test PASS. No further runtime authorized by this checkpoint.

## Next action

1. Implement BREAK post-state simulation (broken target as open feet cell) or a validated move-beyond edge.
2. Re-run static + one controlled A2; do not raise global budget or weaken landing/support.
3. A2 OPEN; C4 standalone pending; Full12K blocked.

# HISTORICAL — BUILD provenance + BREAK diagnostics STATIC PASS

- Static PASS: compile/test/policyTest/lint/diff; 57 named tests, no failures.
- `NAV_BUILD_OBJECTIVE_POLICY_TEST`: 8 assertions; provenance requires exact `feet=actionPos.above()` and
  survives INC_STEP; local gate rechecks it.
- BREAK diagnostics include UUID/caller and use per-Mob `WeakHashMap` 128-event budgets; behavior unchanged.
- Prompt expected HEAD `…c5071f3b` is invalid; actual remote tip `…c6666e7fe` matches all six blobs.
- No runtime. A2 OPEN; C4 not revalidated; D3 BUILD runtime unmeasured.

## Next action

1. ChatGPT reviews static PASS; decide whether to authorize another controlled runtime.
2. Any runtime requires new explicit authorization; previous Run1 authorization is consumed.
3. No Run2/Full12K/C4 standalone without separate authorization.

# HISTORICAL — BUILD provenance fix + BREAK diagnostic isolation

- BUILD: added NavBuildObjectivePolicy. A BUILD waypoint now requires non-null actionPos and exact feetCell == placementCell.above(). NavExecutor retains that actionPos in a dedicated field across INC_STEP and rechecks it before allowing an unsupported local objective. Added LOCAL_OBJECTIVE_SELECTED and BUILD rejection diagnostics.
- BUILD regression: NavBuildObjectivePolicyTest, 8 assertions, registered in policyTest. Authored; NOT RUN by ChatGPT.
- BREAK: diagnostic caps are now 128 per live Mob (WeakHashMap), rather than global. BREAK_STAND_CANDIDATE and BREAK_LOS_DIAG now include UUID and caller. No LOS/reach/candidate/route behavior changed.
- DROP: no behavior patch. IDX6 no_progress is based on horizontal distance while full AABB remains supported; do not weaken support-release or landing. Further deterministic collision fixture needed.
- Death: extraction error corrected. IDX1 died from minecraft:mob_attack; direct/causing UUID cc4b9c97-d8f3-40e3-aee1-cf233feab912. A prior minecraft:fall hit also occurred. Killer name/entity mapping still pending. Telemetry source unchanged.
- No runtime, Gradle, tests, lint, Minecraft, or world operations were performed by ChatGPT. Static validation is pending Harness.

**Harness next:** STATIC ONLY. Safe fast-forward to branch tip; preserve all local/untracked work, partials, backups, logs, artifacts and protected worlds; stop on divergence/overwrite risk. Verify exact blobs. Run diagnostic compileJava, Gradle test + policyTest, strict lint_aldea.py, and git diff --check. Confirm NAV_BUILD_OBJECTIVE_POLICY_TEST (8 assertions) executed. Audit BUILD provenance and BREAK per-entity diagnostic fields/caps. Harness must not modify production/tests.

**No runtime authorization:** no Minecraft, A2 Run2, retry, C4 standalone, or Full12K. Previous one-run authorization is consumed. Full12K remains gated behind five consecutive 8/8 strict A2 runs.

**A2 OPEN.**
---

# HISTORICAL — A2 same-column vertical-edge correction

- Evidencia raíz: el forense del 19-nov demostró que IDX3/IDX5 estaban en pozos 1×1; los ocho vecinos horizontales estaban bloqueados y el grafo no representaba el movimiento vertical en la misma columna `(0,0)`. A* agotaba la primera expansión con 56 candidatos y cero sucesores.
- Corrección implementada: `NavNeighborPolicy` añade una novena dirección `(0,0)` a los ocho vecinos horizontales. Los planificadores local y global usan esa política.
- La nueva dirección solo genera candidatos: todos pasan por `validarArista`. No se relajaron caídas seguras, soporte, colisión, headroom, AABB, protección, permisos BREAK/BUILD, leases, budgets, radios ni criterios.
- Regresión añadida y registrada: `NAV_NEIGHBOR_POLICY_TEST` — 16 assertions. **Authored, NOT RUN.**
- Blobs esperados: NavNeighborPolicy `e0378421c0ea024b34b4f95354e5780db78202d9`; NavPlanner `274af17a32a7aa435a26457f883438513ce207dc`; NavGoalMetric `4f79500a06eadf264cd86115524285cfaf3c8518`; NavNeighborPolicyTest `0ffd6323dc21a3f9c1dd52f23c71570d6d475625`; build.gradle `ab8e949c055676df0f4cd0e4069c9839f80f1382`.
- El dueño autorizó exactamente **una ejecución runtime A2 Run1**. Harness la realiza; ChatGPT no ejecuta comandos locales, Gradle ni Minecraft.
- **Siguiente:** Harness sincroniza de forma segura, preserva worktree/untracked/backups/artefactos/mundos, verifica blobs e hashes protegidos, ejecuta compileJava + test/policyTest + lint estricto + diff-check. Si cualquier gate falla, no lanza Minecraft. Si todos pasan, ejecuta una sola vez el A2 Run1 canónico en mundo nuevo con la instrumentación detallada del handoff.
- Capturar por UUID los 63 candidatos teóricos por primera expansión (9×7, sujetos al radio), causas BREAK stand/LOS, eventos START/PENDING/COMPLETE/CANCEL/RESTART, BUILD/DROP y entrada estricta.
- Sin retry, C4 standalone, Run2 ni Full12K. Harness no modifica producción ni tests.
- D* Lite sigue sin implementarse. El planner actual es A* incremental reanudable. D* Lite no corrige la ausencia de aristas legales; se reconsiderará solo si el runtime demuestra que el coste de replanning es un problema independiente.

**Última validación:** R2 estática PASS sobre el código anterior; la nueva corrección requiere validación estática fresca.
**Estado:** A2 OPEN; C4 no revalidado; BUILD runtime no medido.

---

# HISTORICAL — 2026-11-20 A2 global A* compile correction
**Implementation record:** `docs/autonomous-village/2026-11-20-A2-GLOBAL-ASTAR-CORRECTION-IMPLEMENTATION.md`

## First static gate result

Harness verified all six original implementation/test blobs, then compile failed at `NavGraphSearch.java:176`: mutable local `reopensThisSlice` was captured by the `graph.expand` lambda. `compileJava` and `test` failed with the same compile error; no tests executed. Strict lint and `git diff --check` passed. Harness did not modify production/tests and did not launch Minecraft or runtime.

## ChatGPT correction published

Commit `ab9d4f0c84a56b67d4efb3e4ff2468ac54e2415f` changes the slice counter to a final one-element `int[]` holder. The lambda increments element 0, and `lastReopens` snapshots element 0 after `graph.expand`. This preserves the existing reopen condition, cumulative counter, increment timing and per-slice metric meaning. No navigation behavior, budgets, tests or safety rules changed.

New `NavGraphSearch.java` blob: `906b480afff84780d66b4378b447e7b1e4a5a3f0`.

## Immediate next action — Harness STATIC ONLY

- Safe fast-forward only to the current branch tip; inspect divergence/worktree first and preserve all local/untracked work, backups, partial files, logs, artifacts and protected worlds. Stop on conflict or overwrite risk.
- Verify the six implementation/test blobs; five retain their previous SHAs and `NavGraphSearch.java` must equal `906b480afff84780d66b4378b447e7b1e4a5a3f0`.
- Run the same complete static gate: diagnostic `compileJava`, Gradle `test` (including `policyTest`), strict `lint_aldea.py`, and `git diff --check`.
- Explicitly report whether `NAV_BREAK_ATTACK_POSITION_POLICY_TEST` (8 assertions), `NAV_PLANNER_STATE_TEST`, and every policy test actually executed.
- Audit that only the lambda-capture representation changed; verify stale OPEN, reopen counters, BREAK, DROP, cancellation telemetry and unchanged budgets/invariants.
- Harness must not modify production or tests. Persist a new report and update this checkpoint with exact tested HEAD, blob SHAs, commands, exit codes, test counts and preservation status.
- Stop after STATIC PASS/FAIL. **No Minecraft, runServer, A2, C4, Run2 or Full12K.** No runtime is authorized.

**Current acceptance:** A2 OPEN; C4 not revalidated; D3 BUILD runtime-unmeasured. The prior A2 Run1 authorization is consumed. A static PASS does not authorize runtime.

---

# HISTORICAL — 2026-11-20 A2 global navigation corrections implemented
**Forensic report:** `docs/autonomous-village/2026-11-20-A2-GLOBAL-ASTAR-LOG-FORENSICS.md`
**Run1:** `a2n-20261120-140611` — FAIL, 0/8 strict, 2/8 perimeter.

- BREAK stand candidates now use actual feet Y and include the current supported cell as a zero-movement action stand.
- DROP graph states now use validated landing feet Y in local and global A*.
- A* discards stale OPEN records; pop/stale/closed-skip/reopen metrics are exposed per slice and in search summaries.
- Global cancellation logs include caller class/method/line and world tick.
- Regression tests authored; **STATIC VALIDATION PENDING**. ChatGPT did not run build/tests/lint.
- No runtime or world changes. A2 OPEN; C4 not revalidated; D3 BUILD runtime-unmeasured.

## Immediate next action — Harness STATIC ONLY

Validate exact implementation HEAD `c663f5d06618d12cd8150e238308fe3e9d7652db`:
- Preserve local/untracked work, backups, partial files, logs, artifacts and all protected worlds. Safe fast-forward only; stop on divergence or overwrite risk.
- Verify all source/test blob SHAs in the implementation report.
- Run compileJava, Gradle test/policyTest, strict lint and diff-check.
- Audit BREAK stand generation, DROP state/feet coherence, stale OPEN skipping, pop/reopen metrics and cancellation caller.
- Harness must not modify production or tests. Persist its report and update this checkpoint.
- Stop after STATIC PASS/FAIL. **No Minecraft, runServer, A2, C4, Run2 or Full12K.** Runtime requires separate explicit authorization.

---

# HISTORICAL — 2026-11-20 A2 Run1 D3 runtime FAIL

- Fixture PASS (16/16, 12 professionals, 8/8 spawn); C4 FAIL.
- A2 FAIL: 0/8 strict, 2/8 perimeter (IDX1 t509, IDX6 t280), 0 deaths.
- D3 BUILD not exercised: zero BUILD nodes/placements/feet approaches/JUMP_UP rejects.
- IDX3/4/5 back to exp=1/0 front exhaustion; IDX1/6 global pending; IDX7/8 global pending.
- Protected worlds unchanged; no second run. A2 OPEN; C4 not validated.

---

# HISTORICAL — 2026-11-20 A2 disposable target RESERVED
**Preparation report SHA-256:** `6D7A0B2E7C82FD018EB7DEBD254FF6C9483B281609F21E35DD93A16EF4949B12`

- Preparation started from local `dc8ef0a9f1300ac420f0efc29535f1968c5dbff1`, fast-forwarded to `4b67659b8ba1ef459c4f9b9391868b8da5af28ab`, then committed documentation at `130d5e1c5929c258e147ae431cf1448fba554c00`.
- Established A2 workflow generates a fresh world at runtime from seed/config; APPDATA snapshot is observational reference only, not a clone source.
- Reservation only: `build/a2n/a2n-20261120-135343-prep/` contains `PREP-MANIFEST.txt`; no `game/a2n`, `level.dat`, or region data. The launcher will create a distinct unique `build/a2n/a2n-<timestamp>/game/a2n` when authorized.
- Manifest SHA-256: `DD66E458EFC7BE980DCA896BCA6D6AD639423F270974A8726B77311A3BFCE677`.
- Protected A2 hashes unchanged: APPDATA `BA00A1C4…`; run/saves derivative `F4EEAA13…`. Protected C4 copies both `11D064E2…`.
- Production/tests unchanged; preserved local-only files and artifacts. No runtime or Minecraft launch.
- Prepared command and settings are recorded in the report. Do not execute until separate explicit user authorization.
- A2 remains OPEN; D3 is static PASS only; gameplay traversal UNKNOWN; C4 not revalidated. No Run2 or Full12K.

## Immediate next action

Await explicit user authorization for **one A2 diagnostic Run1 only**, using the documented direct launcher invocation and a newly generated unique runtime world. Before execution, Harness must re-check branch/worktree and protected-world hashes, preserve all artifacts, and report the actual run directory. Do not run C4, Run2, or Full12K under this authorization.

---

# HISTORICAL — 2026-11-20 A2 world integrity BLOCKED

- APPDATA A2 intact at `BA00A1C4…`.
- run/saves A2 changed to `F4EEAA13…`: manual client session by `Dev` on 19 Nov 15:58-16:09,
  time 6104, playerdata/advancements/stats/new regions.
- B copy is a played derivative, not the pristine reference; deletion requires owner decision.
- A2 runtime remains blocked pending owner decision and new authorization.

---

# HISTORICAL — 2026-11-20 D3 static PASS

- All D3 blobs match; production logic unchanged after `c520198c`.
- Compile/test/lint/diff PASS; vertical/build 32, route-node 8, climb/DROP/arrival PASS.
- JUMP_UP +2 producer/actuator removed; BUILD feet/action split and physical arrival confirmed.
- No runtime. A2 OPEN; C4 not revalidated; runtime traversal UNKNOWN.

---

# HISTORICAL — 2026-11-20 D3 single-block ascent implementation
**Prior forensic report:** `docs/autonomous-village/2026-11-20-D2-SINGLE-BLOCK-ASCENT-FORENSICS.md`

- Ordinary ascent is now capped at +1. The planner's `SUBIDA_MAXIMA` uses `MAX_ORDINARY_ASCENT=1`; +2/+3 are not ordinary movement edges.
- `NavObstruction.classify` no longer produces JUMP_UP; the planner's legacy JUMP_UP result is fail-closed. The +2 0.62 actuator was removed from NavExecutor; legacy JUMP_UP nodes cannot advance by proximity or execute.
- BUILD graph successor / `NavRoute.Node.pos` now represent resulting feet cell; `actionPos` carries the exact placement cell below it.
- Planner admits each BUILD edge only when placement preconditions pass and its feet delta is at most +1 (and within the existing safe-drop bound). Multi-block ascent is a chain of individually placeable BUILD edges.
- Executor acquires the exact BUILD action lease, places and verifies actionPos once, then moves to feet pos. Route cursor waits for exact grounded occupancy, support, valid feet and real-AABB fit.
- Existing support/headroom/protection/placement/budget/cooldown/lease rules remain in place. No removed goals re-enabled.
- Added `NAV_VERTICAL_BUILD_POLICY_TEST` (32 assertions) and updated affected planner, route-node, climb and DROP recovery tests; registered in `policyTest`.
- **ChatGPT did not run compile, tests, lint or diff-check.** Static validation is PENDING Harness.
- **No runtime:** Minecraft not launched; no worlds changed. Runtime traversal remains UNKNOWN.
- A2 OPEN; C4 not revalidated.

## Immediate next action — Harness STATIC ONLY

1. Read the permanent policy, this checkpoint, workflow contract, continuation indexes and D3 implementation report.
2. Inspect local/remote HEAD, divergence, dirty/untracked files; preserve all local-only files, backups, logs, partial files, artifacts and worlds. Safe fast-forward only; stop on any conflict/overwrite risk.
3. Sync the current remote branch tip and verify the D3 report's production/test blob SHAs. Source implementation is `c520198c0b89e5da0d6f2e760b22e8d43fcec7d7`; test-only follow-up `63276177583dd3f43f93a06700e4b3e52a6e3be6` only clarifies the legacy JUMP_UP arrival assertion. The later commits are documentation updates.
4. Run compileJava, Gradle test/policyTest, strict lint and diff-check exactly as specified in the D3 report.
5. Audit +1 ceiling, absence of JUMP_UP producer/actuator, BUILD feet/action split, exact lease target, single placement, physical arrival, non-buildable intermediate rejection and unchanged invariants.
6. Report exact SHA, source blobs, commands, exit codes, assertion counts and preservation status. Harness must not modify production.
7. Stop after static validation. No Minecraft, runServer, A2, C4, Run2 or Full12K. Runtime requires a separate explicit authorization.

---

# HISTORICAL — 2026-11-20 D1 IDX7 ascent forensics

- IDX7 never emitted a BUILD node or placement; it stayed PLANNING and exhausted local budget.
- A single +3 BUILD edge is correctly rejected by `SUBIDA_MAXIMA=2`; no multi-step ascent path exists.
- `descensoExigeConstruir` is descent-only; `apilarBloqueParaSubir` has no live lease-gated caller.
- Required fix: signed ascent policy + intermediate BUILD chain, preserved budgets/protection.
- No runtime. A2 OPEN.

---

# HISTORICAL — 2026-11-20 A2 next-defect forensics

- A2 target is 8/8 strict `DENTRO-PARED` in two consecutive runs + hunting regression.
- Last runtime 151818: 3/8 strict, 3/8 perimeter, C4 PASS, no watchdog; pre-P0/pre-lease.
- Priority unfixed defect: IDX7 ascent/BUILD gate (`descensoExigeConstruir` applies descent logic to
  `dy=+3`).
- IDX3/4/5 P0 fixes and lease changes remain runtime-unmeasured; IDX8 cancellation and atomic gate separate.
- No runtime. A2 OPEN; C4 not revalidated.

---

# HISTORICAL — 2026-11-19 traversal lease fix STATIC PASS
**Validation report:** `docs/autonomous-village/2026-11-19-TRAVERSAL-LEASE-BYPASS-STATIC-VALIDATION.md`

- Exactly one `recoveryAction.run(` remains, inside `invokeTerrainAction` behind the lease.
- Normal BREAK/recovery BREAK/BRIDGE pass through the lease; DROP no longer invokes BREAK.
- Compile/test/lint/diff PASS; lease 13, dispatch 6, NAV planner 54, local 5, stair 12 assertions.
- No runtime. A2 OPEN; C4 not revalidated.

---

# HISTORICAL — 2026-11-19 traversal lease bypass correction (STATIC PENDING)
**Implementation report:** `docs/autonomous-village/2026-11-19-TRAVERSAL-LEASE-BYPASS-FIX.md`
**Continuation indexes:** `docs/autonomous-village/CONTINUAR.md` blob `ded6d68272eb69b52e80ee63582e1da96508f53d`; `docs/CONTINUAR.md` blob `03b52e6e29d8bd001298d162595e43614bfcfa5c`; `docs/PENDIENTE.md` blob `180ddfe367dfb6df3067f39c0a5ccad5fd7613da`.
**Prior FAIL report (preserved):** `docs/autonomous-village/2026-11-19-TRAVERSAL-COORDINATION-STATIC-VALIDATION.md`

- Harness found two genuine bypasses despite static gate PASS: normal BREAK called the callback directly, and DROP opportunistically invoked BREAK.
- Both are corrected. Normal BREAK and recovery BREAK/BRIDGE now use one `invokeTerrainAction` gateway which acquires the exact active-transition lease before dispatch.
- DROP-obstruction BREAK was removed. DROP retains landing/edge exclusions and replans; demolition requires an explicit BREAK route node.
- Only one direct `recoveryAction.run(` remains in NavExecutor, inside the shared gateway (source inspection; Harness must independently verify).
- Added `TraversalActionDispatchPolicy` and `TraversalActionDispatchPolicyTest` (6 assertions), registered in `policyTest`. Existing 13-assertion lease policy test remains.
- No budgets, cooldowns, protections, support/headroom, collision, fixtures or worlds changed.
- **STATIC VALIDATION PENDING.** ChatGPT has not run compile, Gradle tests, lint or diff-check.
- No runtime. Do not launch Minecraft, runServer, wrapper, A2, C4, Run2 or Full12K.
- A2 remains OPEN; C4 not revalidated.

## Immediate next action — Harness STATIC ONLY

1. Read collaboration policy, this checkpoint, workflow contract, continuation indexes, prior validation report and new implementation report.
2. Inspect local/remote HEAD, divergence, dirty/untracked files and preserve all local-only work, backups, logs, partial files, fixtures and worlds. Safe fast-forward only; stop on conflict/overwrite risk.
3. Validate exact source implementation SHA `9f00cb5ecec462df91f9905072988db90bec08b2` (or a later documentation-only descendant after verifying the source blobs).
4. Run compileJava, Gradle test/policyTest, strict lint and `git diff --check`.
5. Confirm lease policy (13 assertions), dispatch policy (6 assertions), all existing policy groups, exactly one direct `recoveryAction.run(` in NavExecutor inside `invokeTerrainAction`, and no DROP BREAK callback.
6. Harness must not modify production. Publish a new dated static-validation report and update continuation indexes with exact tested SHA/evidence.
7. Stop after static validation. Runtime requires a separate explicit authorization.

---

# HISTORICAL — 2026-11-19 traversal coordination validation FAIL (lease bypass)


**Branch:** `feature/autonomous-village`
**Tested HEAD:** `9a26bfbe5fa06475e611a256a26acfb3f9ac4ace`
**Validation report:** `docs/autonomous-village/2026-11-19-TRAVERSAL-COORDINATION-STATIC-VALIDATION.md`

- Static gate PASS: compile/test/lint/diff; new lease test 13 assertions.
- Behavioral FAIL: normal BREAK calls `recoveryAction.run` without `tryAcquireTraversalAction`.
- DROP path also invokes BREAK on drop obstruction, outside the matrix.
- NavExecutor blob handoff stale (actual differs only comments).
- No runtime. A2 OPEN.


 — 2026-11-19 traversal coordination implemented (STATIC PENDING)
**Implementation report:** `docs/autonomous-village/2026-11-19-TRAVERSAL-COORDINATION-IMPLEMENTATION.md`
**Design report:** `docs/autonomous-village/2026-11-19-TRAVERSAL-COORDINATION-DESIGN.md`
**Continuation indexes updated:** `docs/autonomous-village/CONTINUAR.md` commit `01b210c927ebace2b496b9889bf6c8cd5b0c08b2`; `docs/CONTINUAR.md` commit `a02953c7c426286b12a1100aae1c3bf1990506c5`; `docs/PENDIENTE.md` commit `6106802b3f5d5de87cd9d9646243c620aebd26d3`.

- NavExecutor now derives active node/transition/action target from its existing route cursor and owns a per-entity, per-world-tick single-use terrain-action lease.
- Only exact active BREAK/BUILD/BRIDGE targets can acquire the lease. WALK/STEP_UP/JUMP_UP/DROP/DOOR and absent/terminal nodes cannot authorize terrain tools.
- BUILD acquires the lease before placement. BREAK/BRIDGE recovery requires an explicit active route node. Generic recovery based on the global objective no longer modifies terrain.
- BreakBlockGoal and TraverseGoal are no longer registered; their target-Y-driven independent tool decisions cannot run.
- Added `TraversalActionLeasePolicy` and `TraversalActionLeasePolicyTest` (13 assertions), registered in `policyTest`.
- Existing helper protection/budgets remain; no gate changes. Duplicate route-break `terrainChanged()` call removed.
- Source commits: `ff0a0672`, `83ba75be`, `09d2710e`, `7a49c43d`, `9200bfff`, `7aaea193`.
- **STATIC VALIDATION PENDING.** ChatGPT did not run compile/tests/lint/diff. Harness must validate exact implementation SHA and report all results.
- **STATIC ONLY:** no Minecraft, runServer, wrapper, A2, C4, Run2 or Full12K. Runtime was not authorized.
- Existing-gap-first explicit handling remains a follow-up review; atomic custom gate and wave-aware repair are not implemented.
- A2 remains OPEN. This work does not revalidate A2 or C4.

## Immediate next action — Harness STATIC ONLY

1. Read the permanent collaboration policy, this checkpoint, `CHATGPT-HARNESS-WORKFLOW.md`, both continuation indexes, and the implementation report.
2. Inspect local/remote HEAD, divergence, dirty/untracked files and local-only artifacts. Safe fast-forward only; preserve all work, backups, logs, partial files and worlds; stop on conflicts.
3. Validate exact SHA `7aaea19364d958ee0e16f9ea89b3b1be9168e878`.
4. Run documented `compileJava`, `test` (including `policyTest`), strict lint and `git diff --check`.
5. Confirm `TRAVERSAL_ACTION_LEASE_POLICY_TEST` (13 assertions), full existing policy suite, and no active registration of `BreakBlockGoal`/`TraverseGoal`.
6. Harness must not modify production. Persist report and update this checkpoint with exact tested SHA and evidence.
7. Stop after STATIC validation regardless of PASS/FAIL. Do not launch Minecraft or any runtime scenario.
---

# HISTORICAL — 2026-11-19 P0 enum fix STATIC PASS

- Enum spelling fixed (`ACCEPTAR_NUEVO` → `ACEPTAR_NUEVO`), one-character diff.
- compile/test/lint/diff PASS.
- `NAV_LOCAL_OBJECTIVE_STEP_POLICY_TEST`: 5 assertions.
- `STAIR_BUILD_RESULT_TEST`: 12 assertions.
- `NAV_PLANNER_STATE_TEST`: 54 assertions.
- No runtime. A2 OPEN; new runtime authorization required.

---

# HISTORICAL — 2026-11-19 P0 enum spelling corrected; STATIC PENDING
**P0 implementation base:** `b41e9687a4be74d24a671340ac41b7e3f1817045`
**Correction source commit:** `e0dc0d784a77ce062eae586b25453c8c05c0b5ff`
**Correction + test commit (current production HEAD):** `a0251add7dc7661ee2fb7ee6a85a73d0b085acca`

## Harness failure and correction

Harness run `harness-p0-static-20261119-1640` reported STATIC FAIL: the enum declared
`ACCEPTAR_NUEVO` (double C) while implementation and test referenced `ACEPTAR_NUEVO` (single C).
This was a spelling mismatch, not a missing decision or behavior defect.

ChatGPT corrected the enum constant to `ACEPTAR_NUEVO` and kept the implementation return and
five-assertion test aligned. No decision branches, budgets, counters, retry semantics, or gameplay
behavior were changed. GitHub source commit: `e0dc0d784a77ce062eae586b25453c8c05c0b5ff`;
test alignment commit: `a0251add7dc7661ee2fb7ee6a85a73d0b085acca`.

**Validation status:** ChatGPT did not run compile, tests, lint, diff-check, or runtime. The fix is
PENDING Harness STATIC ONLY on the current branch tip. Harness must verify the exact tested SHA,
then run the complete prior static gate (compileJava, test, strict lint, diff-check) and explicitly
report `NAV_LOCAL_OBJECTIVE_STEP_POLICY_TEST` (5 assertions), `STAIR_BUILD_RESULT_TEST`
(12 assertions), `NAV_PLANNER_STATE_TEST` (54 assertions), and all policy tests. No assertions
are presumed executed until Harness reports them.

## Traversal investigation remains read-only

Do not implement the proposed active-transition/one-lease traversal redesign in this checkpoint.
Review its contract first. The existing findings remain: NavExecutor owns route execution; passive
BreakBlockGoal and TraverseGoal independently decide terrain actions; target/center Y is not a valid
substitute for the active route transition. No runtime is authorized. A2 remains OPEN.

**Next action:** Harness STATIC ONLY on the current branch HEAD. Stop after PASS/FAIL. Do not launch
Minecraft or run A2, C4, Run2, or Full12K.

---

# HISTORICAL — 2026-11-19 A2 P0 fixes implemented (STATIC PENDING)
**Implementation report:** `docs/autonomous-village/2026-11-19-A2-P0-LOCAL-OBJECTIVE-STAIR-RETRY-FIX.md`

- IDX3/IDX5: same unusable local objective no longer consumes another `escalonesDeObjetivo` step; it increments the existing bounded failure counter and emits `REJECT_REPEAT_UNUSABLE`.
- IDX4: no-candidate stair search no longer resets the inter-block cooldown. Only a real cooldown decrement is `CADENCE_WAIT`; no candidate is `NO_PROGRESS` and returns to the executor's bounded failure/backoff path.
- Added `NavLocalObjectiveStepPolicy` and `StairBuildResultPolicy`; registered the new local-objective test in `policyTest`.
- Expected tests: `NAV_LOCAL_OBJECTIVE_STEP_POLICY_TEST` 5 assertions; `STAIR_BUILD_RESULT_TEST` 12 assertions.
- No build, tests, lint or runtime have been executed by ChatGPT. Exact commit is pending Harness validation.
- IDX7 ascent/BUILD and IDX8 global-search cancellation remain unimplemented; wrapper fix remains separate.
- No budgets, cooldown durations, collision/support/protection/AABB rules, anchors, traversal checks or acceptance criteria were changed.
- A2 remains OPEN. Runtime authorization is consumed.

## New manual gameplay observation — 2026-11-19

Player manually ran a siege and observed two attackers breach/enter, several attackers ignore the visible breach and dig beneath themselves (some then remain in the holes), and a yellow-named leftover test entity approximately `IDX8` idle at the custom gate.

Source diagnosis and required behavior are recorded in:
`docs/autonomous-village/2026-11-19-MANUAL-SIEGE-OBSERVATION-GATE-AND-BREACH.md`.

Confirmed source findings:
- `TraverseGoal` chooses a live combat target's Y (otherwise village center) and explicitly prefers `cavarHaciaAbajo` when that target is more than one block below. This explains why attackers can dig underfoot instead of using an existing breach; exact runtime trigger per zombie remains unverified.
- `DoubleGateBlock` is a 3×3 EntityBlock assembly. Its hinge cell has a block entity; mob navigation and `breakBlockAt` reject block entities, while the nine-cell cascade exists only in `playerWillDestroy`. Simply allowing the hinge or removing the block-entity guard is insufficient and unsafe.
- Existing `VillageGenerator.asegurarMuro` checks missing custom gate cells and can rebuild the perimeter, but it defers repair while ANY `AggressiveZombieEntity` remains in a broad radius. A stale/out-of-wave `IDX8` near the gate may therefore suppress restoration even after the real siege resolves.

Player's new design requirement supersedes the older gate comment: the siege must be able to destroy the custom gate as one atomic 3×3 breach, and the village must restore it after the real siege is safely resolved. Existing protection of arbitrary block entities and non-wall village structures must remain intact.

**No production code changed for this observation.** The current P0 static-only Harness checkpoint remains first. After it passes, implement/test atomic gate demolition, reachable-breach preference, correction of target-Y-only underfoot digging, and repair gating based on the active siege/wave rather than any nearby aggressive zombie. No runtime is authorized until static PASS and fresh explicit approval.

---

## Immediate work — Harness STATIC ONLY

1. Fast-forward-only sync to the current `feature/autonomous-village` branch tip, which must descend from production implementation commit `b41e9687a4be74d24a671340ac41b7e3f1817045`. Documentation-only handoff commits may follow it. Preserve all local/untracked work, backups, artifacts and worlds. Stop on divergence or overwrite risk.
2. Record the actual branch HEAD tested and verify the seven changed paths against the source blob SHAs in the implementation report.
3. Run:
   - `.\gradlew.bat compileJava --info -Pa2nDiagnostics=true -Pa2nPopulationQueryHarness=true -Pa2nTraceNav=true -Pa2nTraceGlobalSearch=true`
   - `.\gradlew.bat test --info -Pa2nDiagnostics=true -Pa2nPopulationQueryHarness=true -Pa2nTraceNav=true -Pa2nTraceGlobalSearch=true`
   - `python tools\lint_aldea.py --strict`
   - `git diff --check`
4. Confirm the new 5-assertion `NAV_LOCAL_OBJECTIVE_STEP_POLICY_TEST`, the extended 12-assertion `STAIR_BUILD_RESULT_TEST`, and all existing policy tests including `NAV_PLANNER_STATE_TEST` (54 assertions).
5. Record tested SHA, commands, exit codes, logs, source selection, worktree/untracked preservation and any failures. Harness must not modify production.
6. Stop after static validation regardless of PASS/FAIL and return the complete report.

**NO RUNTIME:** do not launch Minecraft, wrapper, runServer, A2, C4, Run2 or Full12K. Any new runtime requires fresh explicit authorization.

---

# HISTORICAL — 2026-11-19 A2 post-Phase55 forensic analysis

**Branch:** `feature/autonomous-village`
**Run analyzed:** `a2n-20261119-151818`
**Report:** `docs/autonomous-village/2026-11-19-A2-POST-PHASE55-FORENSIC-ANALYSIS.md`

- IDX3/5: 381 repeated `INC_STEP` for the same unusable objective; step cap is a counter/idempotence defect.
- IDX4: no-candidate resets 10-tick delay, producing endless CADENCE_WAIT; not the 64 cap.
- IDX7: `dy=+3` ascent rejected by descent-only build gate.
- IDX8: repeated caller cancellation + one 8000-budget exhaustion; exact caller not logged.
- Five restarts caused by one-block start changes; five survivors discarded administratively at Phase B end.
- Wrapper fails because array splatting binds named args positionally.
- No runtime; A2 OPEN.

---

# HISTORICAL — 2026-11-19 A2 Diagnostic Run1 post-Phase55

- JVM_TRACE confirmed both trace properties true.
- Fixture PASS: AABB=16, joins=16, 12 professionals, 8/8 spawn.
- A2 FAIL: 3/8 strict (IDX1/2/6), 3/8 perimeter, all 3 strict entrants died; C4 PASS.
- IDX3/5 hit local stair cap 64/64; IDX4 CADENCE/no candidate; IDX7 no placement; IDX8 still PLANNING.
- No watchdog. Authorization consumed; no Run2/Full12K.

---

# HISTORICAL — 2026-11-19 A2 Phase55 NavFeature removal static PASS

- Canonical harness selected; legacy untouched.
- NavFeature references eliminated (only a comment remains).
- Compile/test/lint/diff PASS; `NAV_PLANNER_STATE_TEST` 54 assertions.
- No runtime executed. A2 OPEN; any diagnostic run needs new explicit authorization.

---

# HISTORICAL — 2026-11-19 A2 Phase55 canonical harness (STATIC PENDING)
**Implementation report:** `docs/autonomous-village/2026-11-19-A2-PHASE55-REMOVE-OBSOLETE-NAVFEATURE-HARNESS-DEPENDENCY.md`
**Implementation report commit:** `20991bfaa05683de8c458035127e7edce7f12c0d`

## Last validation evidence

Harness run `phase55-routing-static-20261119-1400`:
- Gradle routing PASS: javac selected `tools/a2n/A2NavHarness.java`, not the legacy file.
- `compileJava` FAIL: seven missing-symbol errors for `NavFeature`.
- `gradlew test` FAIL at the same compile errors; `NAV_PLANNER_STATE_TEST` NOT RUN.
- Strict lint PASS; `git diff --check` PASS.
- No runtime, world or fixture modification. Harness left production untouched.

## Root cause and correction

The missing `NavFeature` was deliberately deleted in
`docs/autonomous-village/2026-11-15-NAVEXECUTOR-SINGLE-AUTHORITY-MIGRATION.md`, along with the old
`-Ddevilrpg.nav.custom=true` fallback to `AggressiveZombiePathPlanner`. The current architecture has one
movement authority: `AggressiveZombieEntity` constructs `NavExecutor` and ticks it on each server-side
`aiStep`. This is independently verified in current source. Do not recreate `NavFeature` or its old flag.

ChatGPT removed obsolete compile-time references from canonical `tools/a2n/A2NavHarness.java`:
- Raid log now says `navCustomEfectivo=true modo=NavExecutor-single-authority featureFlag=eliminado`.
- Trace header identifies `NavExecutor-single-authority`.
- No production Java, `NavPlanner.java`, gameplay behavior, budgets, policies, fixtures, worlds, A2/C4 criteria
  or Gradle routing changed.
- The legacy harness remains untouched.

Verified source blob after correction:
`914d26da4f9a553f43936812ddc303ed56a24243`.

## Next action — Harness STATIC ONLY

Fast-forward-only sync to the current remote tip containing source commit
`a029e4eb790109df94acb1e07f8cac52f8b88ff8`. Preserve local-only changes, untracked files, backups and worlds;
stop on divergence or overwrite risk.

Verify:
- canonical source blob `914d26da4f9a553f43936812ddc303ed56a24243` and no compile-time `NavFeature` /
  `customNavigation()` references;
- legacy harness blob `28ea2fb8ce970326c1157046dcb59e3d1cc5892d`;
- `NavPlanner.java` blob `5666e42693fe2e90dff1d2ac1004b59b9b62d7cd`;
- Gradle routing and wrapper unchanged.

Run exactly:
- `.\gradlew.bat compileJava --info -Pa2nDiagnostics=true -Pa2nPopulationQueryHarness=true -Pa2nTraceNav=true -Pa2nTraceGlobalSearch=true`
- `.\gradlew.bat test --info -Pa2nDiagnostics=true -Pa2nPopulationQueryHarness=true -Pa2nTraceNav=true -Pa2nTraceGlobalSearch=true`
- `python tools\lint_aldea.py --strict`
- `git diff --check`

Record exact tested SHA, commands, exit codes, test names/assertions (especially NAV_PLANNER_STATE_TEST, 54 assertions),
logs, source selection, worktree and untracked preservation. Verify NeoForge APIs used by the population fix.
Harness must not modify production source.

**STATIC ONLY. Do not launch Minecraft, wrapper, runServer, A2, C4, Run2 or Full12K.** Runtime authorization is
consumed. Stop after static validation regardless of result. A2 remains OPEN.

---

# HISTORICAL — 2026-11-19 A2 Phase55 canonical harness build routing (PENDING)
**Previous Harness result:** `phase55-population-static-20261119-1215` — BLOCKED before compile/test
**Implementation report:** `docs/autonomous-village/2026-11-19-A2-PHASE55-POPULATION-QUERY-BUILD-ROUTING.md` (report blob SHA `7e6b02b3f72a339ee98d27f797aa5d3a88cffb25`)

## Reconciliation

The tracked legacy source `src/main/java/com/chipoodle/devilrpg/debug/A2NavHarness.java` is intentionally
preserved; it contains 12 methods absent from the shorter canonical reconstruction, including BREAK runtime,
BREAK edge-excluded, and NAV backtrack scenarios. It must not be overwritten or deleted.

ChatGPT added an explicit Gradle source selector:

- `-Pa2nDiagnostics=true` enables diagnostic sources.
- `-Pa2nPopulationQueryHarness=true` selects `tools/a2n/A2NavHarness.java`, excludes the legacy
  `debug/A2NavHarness.java` only from this opted-in compilation, and includes `tools/a2n/` as a source root.
- Without the new selector, legacy diagnostic builds retain the existing source.
- Normal builds remain debug-excluded.

The Phase55 PowerShell wrapper now sets/restores both new Gradle selectors plus the two trace properties and
continues to set the actual server JVM trace flags. The ignored local launcher remains untouched.

## Changed files / commits

- `build.gradle` — `15314dd55abf0c83753f3e2356580269ba4c1929`
- `tools/a2n/lanzar-phase55-diagnostico.ps1` — `b399a0e657dd00392a643be2870711b047e14e16`
- `tools/a2n/LEEME.md` — `d291de1cbecb57bc45f3202a1a3a4c0820bc8e1b`,
  `61f3aecd662db713a449db0919f362f723db0c12`
- New implementation report — `8441818102f59c030f9a39ff2e3f6d13527bf95d`

No Java production source, `NavPlanner.java`, worlds, fixtures, navigation behavior, acceptance criteria,
budgets, OFFSETS, collision/support/protection rules, or A2/C4 logic changed. No build/test/lint command was
executed by ChatGPT.

## Next action — Harness STATIC ONLY on exact HEAD

1. Fast-forward-only sync to the current `feature/autonomous-village` tip (which must contain checkpoint base `84ae9ef341261ea8b97b90ee27af927aac769693`); preserve all local/untracked
   work and worlds. Stop on divergence or risk.
2. Verify the legacy tracked file still exists unchanged, canonical source blob remains
   `3205305a10a21ded066c03cb746908e94632e08d`, and `NavPlanner.java` remains
   `5666e42693fe2e90dff1d2ac1004b59b9b62d7cd`.
3. Run:
   - `.\gradlew.bat compileJava -Pa2nDiagnostics=true -Pa2nPopulationQueryHarness=true -Pa2nTraceNav=true -Pa2nTraceGlobalSearch=true`
   - `.\gradlew.bat test -Pa2nDiagnostics=true -Pa2nPopulationQueryHarness=true -Pa2nTraceNav=true -Pa2nTraceGlobalSearch=true`
   - `python tools\lint_aldea.py --strict`
   - `git diff --check`
4. Prove compile selected the canonical source and verify NeoForge 21.1.249 API compatibility. Record exact commands,
   exit codes, logs, tested SHA, worktree and untracked state. Harness must not modify production source.

**STATIC ONLY. Do not launch Minecraft, invoke the wrapper, runServer, A2, C4, Run2, or Full12K.** The previous
runtime authorization is consumed. After static validation, stop regardless of PASS/FAIL and report. A new
runtime requires fresh explicit user authorization.

A2 remains OPEN; no runtime retry is authorized.

---

# HISTORICAL — 2026-11-19 A2 Phase55 population census fix (PENDING)

## Implemented by ChatGPT

- `cdfcd638963a35045ab468250c1519673eb482f7`: A2 fixture captures exact Villager references from NeoForge `EntityJoinLevelEvent` during preGenerate/maintenance. The roster no longer relies on `getEntitiesOfClass(AABB)`; the AABB query remains as a diagnostic comparison.
- `4343753cfb829e1ff5b4585e6ef33dc9f8761ff2`: retained references are used for raid defense/job-site and worker audits; UUID lookup has a captured-reference fallback.
- `87f6e0875149bf6492b2a2349eca2513dce6d0b3`: census requires captured villagers to be alive, not removed, and tracked.
- `a62a0514a9893a016dd4e2d55a6441b468f77ccb` + `a45538bea8b90e31257b9bce0f675bef363aa990`: tracking accepts either `isAddedToLevel()` or exact `level.getEntity(UUID)` identity; per-UUID lookup visibility is logged.
- `d11cae8c53ec60f94ac5d6e3b5cf86318ea59e5e`: `build.gradle` forwards `-Pa2nTraceGlobalSearch=true` to the actual runServer JVM.
- `8aa7f1df340f9adf9fefcec21733130712909a6e` + `565d716b9c43845811b499ac53761a2f4512930e`: tracked PowerShell wrapper sets both `JDK_JAVA_OPTIONS` flags and Gradle `ORG_GRADLE_PROJECT_*` values, then delegates to the untouched local launcher.
- The harness logs effective `traceNav` / `traceGlobalSearch` at server startup and aborts raid mode before fixture setup if either is false.

No production navigation source, world, fixture geometry, acceptance criteria, budgets, OFFSETS, collision/support/protection rules or A2/C4 logic changed.

## Evidence boundary

The previous run proved that the AABB query omitted a Shepherd that was present alive in the saved disposable-world NBT. It did **not** establish why. The new event-based census uses retained object references and verifies their live tracking state, bypassing the AABB index for fixture roster creation while emitting diagnostics to compare the sources; it does not claim the underlying engine visibility anomaly is explained.

## Next action — Harness STATIC ONLY

Validate the exact current branch HEAD:

- `gradlew.bat compileJava` with the diagnostic source inclusion required by the workflow.
- `gradlew.bat test`.
- `python tools/lint_aldea.py --strict`.
- `git diff --check`.
- Confirm NeoForge 21.1.249 APIs compile: `EntityJoinLevelEvent`, `isAddedToLevel`, `areEntitiesLoaded`.
- Verify Gradle property mapping `-Pa2nTraceGlobalSearch=true` is passed to the server run config.
- Preserve all local/untracked files and worlds. Harness must not modify production source.

**STATIC ONLY. Do not launch Minecraft or run A2/C4/Full12K.** The previous runtime authorization is consumed. If static PASS, stop and return the report; another runtime requires fresh explicit user authorization. A2 remains OPEN.

---

# HISTORICAL — 2026-11-19 A2 Phase55 diagnostics static PASS

- Compile/test/lint/diff PASS; `NAV_PLANNER_STATE_TEST` 54 assertions OK.
- `NavPlanner.java` blob at HEAD == `10719ff8` implementation.
- Telemetry remains opt-in (`traceGlobalSearch` + `traceNav`), default off.
- No runtime executed. A2 OPEN; any runtime diagnostic needs new explicit authorization.

---

# HISTORICAL — 2026-11-19 A2 Phase55 opt-in first-expansion diagnostics (PENDING)
**Implementation report:** `docs/autonomous-village/2026-11-19-A2-PHASE55-GLOBAL-ASTAR-DIAGNOSTICS.md`

- ChatGPT implemented only opt-in diagnostics in `NavPlanner.java`; no navigation behavior or acceptance rule changed.
- Enable with both `-Ddevilrpg.a2n.traceNav=true` and `-Ddevilrpg.a2n.traceGlobalSearch=true`.
- First expansion logs each candidate's obstruction kind/rejection, fit/support/head-clear/protection, chunk-loaded state, block state, feet result and BREAK positions; summary reports candidates/enqueued.
- Lifecycle events: START, COMPLETE, CANCEL and parameter-change RESTART.
- Per-candidate logging is limited to the first graph expansion of each new search. No changes to OFFSETS, A* decisions, budgets, collision/support/protection or A2/C4.
- **Next: Harness STATIC ONLY** on exact SHA above: diagnostics compile/compileJava, `gradlew test`, strict lint, `git diff --check`; report exact SHA and outputs. Harness must not edit production.
- No runtime authorization: the single Phase55 Run1 authorization was consumed by `a2n-20261119-093456`. No A2 retry, Run2, standalone C4 or Full12K.
- A2 remains OPEN. Static validation is pending; ChatGPT did not execute tests.

---

# CURRENT CHECKPOINT — 2026-11-19 A2 Phase55 first-expansion forensics

**Branch:** `feature/autonomous-village`
**Run:** `a2n-20261119-093456` — read-only diagnosis complete
**Production SHA:** `11ff43cd338d7a6222a146a2ed642a5546fb2910`

- IDX3/IDX5 `exp=1` proven first-expansion failure: `aristas=56`, `encolados=0`.
- Both starts are 1×1 shafts; all eight adjacent columns solid. `OFFSETS` lacks a vertical-only `(0,0)`
  edge, so safe descent is not representable and OPEN empties.
- Exact per-candidate rejection enum mix is not logged; opt-in first-expansion telemetry specified.
- No runtime consumed. A2 OPEN; no retry/Run2/Full12K.

---

# HISTORICAL — 2026-11-19 A2 Phase55 runtime FAIL

- Fixture PASS: 289/289 chunks, 16 villagers/12 professionals, 8/8 spawn.
- A2 FAIL: 0/8 strict, 1/8 perimeter (idx6 t271); 8 alive, C4 FAIL because guards never fought.
- Incremental A* avoided watchdog (66 pending slices, 0 watchdog) but key global searches completed
  `exp=1/8000` with front exhausted; most attackers remained PLANNING.
- No CADENCE route-preserved event; no stair placement/traversal.
- One Run1 consumed; no retry. A2 OPEN; no Run2/Full12K.

---

# HISTORICAL — 2026-11-19 A2 Phase55 population NBT forensics

- NBT proof: all 12 workers spawned and survived. SHEPHERD `c690ddc5…` at `708,71,533` and MASON
  `c6ba9a5c…` at `694,71,511` are alive, health20, death0, persistent, inside the verifier AABB.
- The 03:19:08 `getEntitiesOfClass` population snapshot omitted them; exact query-visibility cause is
  unknown (no per-tick entity-section snapshot).
- Fixture spawn/profession logic is not implicated. Next: add read-only query diagnostics; no runtime
  without new authorization.
- A2 OPEN; no Run2/Full12K.

---

# HISTORICAL — 2026-11-19 A2 Phase55 fixture population source forensics
**Harness documentation HEAD at blocked report:** `0181bd4e42dfb8f9f70136c1bca4986a42fdbb4d`
**Source-forensics report:** `docs/autonomous-village/2026-11-19-A2-PHASE55-POPULATION-FORENSICS.md`

## Verified runtime facts

- Chunk loader: 289/289 READY in 1 tick.
- Fixture population gate: 14 villagers, 4 founders/guards, 10 professionals vs 12 expected; roster lacks SHEPHERD and MASON.
- Zero attackers spawned; no A2 result, no A* progression, no IDX5/IDX3 evidence, no watchdog.
- Static baseline remains PASS on production SHA `11ff43cd`: compile, Gradle tests, 54 `NAV_PLANNER_STATE_TEST` assertions, strict lint and diff-check.
- The one authorized A2 Run1 is consumed. No retry, Run2, standalone C4 or Full12K.

## Source-review correction

The four `NONE` professions described in the Harness report are **expected founder guards**, not four missing worker professions: `spawnGuardiasFundadores` creates four founders with null profession, and the gate classifies guards before professionals.

The actual mismatch is 4 founders + 10 professionals rather than 4 founders + 12 professionals. The missing roster entries map to worker slot 5 SHEPHERD (offset +47,0,0) and slot 11 MASON (offset +33,0,-22). This does not yet establish whether either worker failed to spawn, died/was removed, changed profession during maintenance, or fell outside the final query.

Source observability gap: `spawnVillagers` attempts all 12 slots, but `spawnOneVillager` returns void; `spawnVillagerAt` can return null on entity creation/insertion failure without a slot-specific failure log. `huecoLibre` also falls back to its original position if no clear two-block position is found. The report contains only the final population snapshot, not a before/after UUID/slot diff.

## Next action — Harness, read-only only

Inspect existing artifacts for `a2n-20261119-031255` only, following the detailed checklist in `2026-11-19-A2-PHASE55-POPULATION-FORENSICS.md`:

- Extract spawn, warning, death/suffocation/removal, profession-reset and maintenance logs.
- Determine whether SHEPHERD/MASON spawned and whether their entities later died/disappeared.
- If the existing disposable world is available and safe, inspect entity NBT read-only; do not load-and-save or mutate it.
- Return a per-slot table for all 12 worker slots, UUIDs, positions, profession/XP/age/health and terminal evidence; list the four expected founders separately.
- Mark missing evidence UNKNOWN. Do not infer a cause from the final roster alone.

**No Minecraft launch or runtime. No world edits. No production edits by Harness.** ChatGPT will implement any required production correction after the read-only evidence.

A2 remains OPEN. Phase55 runtime behavior remains unmeasured.

---

# HISTORICAL — 2026-11-19 A2 Phase55-R2 static PASS / Run1 authorized


**Branch:** `feature/autonomous-village`
**Validated production SHA:** `11ff43cd338d7a6222a146a2ed642a5546fb2910`
**Static report:** `docs/autonomous-village/2026-11-19-A2-PHASE55-R2-STATIC-VALIDATION.md`
**Runtime authorization:** explicit authorization received from the project owner on 2026-11-19 for exactly ONE A2 Run1.

## Verified state

- Phase55-R2 diagnostics compile, `gradlew test`, strict lint and diff-check: PASS on exact production SHA `11ff43cd`.
- `NAV_PLANNER_STATE_TEST`: OK, 54 assertions actually executed.
- Incremental strategic A*: 4 expansions and at most 8 OPEN pops per `Session.advance` slice; hard total global expansion budget unchanged.
- ChatGPT read the collaboration policy, this checkpoint, Harness workflow, continuation indexes, Phase55 implementation/static report, Phase54B fixture report and Phase54B-R1 blocked runtime report.
- ChatGPT inspected the live branch source. `NavGraphSearch.java`, `NavPlanner.java`, `NavExecutor.java` and `A2NavHarness.java` match their blobs at production SHA `11ff43cd`; no production change is needed before this validation.
- Phase54B non-blocking chunk fixture was previously validated at 289/289 chunks READY in 1 tick, 8/8 attackers spawned, wall/population checks PASS. Do not modify this fixture.
- Previous Phase54B-R1 runtime `a2n-20261119-011138` was BLOCKED by the synchronous global A* watchdog before A2 evaluation; it is not an A2 FAIL.

## Authorized next action — Harness owns execution

Harness must safely sync the current remote branch, preserve all local/untracked work and worlds, and verify the production sources still match `11ff43cd338d7a6222a146a2ed642a5546fb2910`. Then execute exactly ONE standard, automated/headless **A2 Run1** using the existing validated Phase54B fixture and normal A2 Run1 configuration.

Capture:
- Fixture readiness, wall/population verification and all 8 spawn/registration outcomes.
- Per-UUID `NAV_GLOBAL_SEARCH_PENDING` progression: tick, cumulative expansions, `slice=4`, frontier size; confirm monotonic progress across slices and that the search is not restarted.
- Runtime tick duration/watchdog outcome and total planning latency; record route completion, exhaustion, invalidation or timeout.
- No partial route, local waypoint or `MOVE_COMMAND` while that entity's global search is pending; after completion, record route summary and subsequent physical/local validation.
- IDX5 cadence-route-preservation trace, subsequent valid movement and unchanged cooldown/local failure counters, if that path is reached.
- IDX3 candidate/anchor/build lifecycle, actual physical stair placement and traversal, if reached. Do not infer missing events.
- Final A2 strict `DENTRO-PARED` entrants and perimeter contacts separately, per-index entry ticks/terminal reasons, bell/conquest evidence, and all raw log/artifact paths.

The implementation slice is 4 expansions / at most 8 OPEN pops per `Session.advance`; do not raise it. Existing total search budget and all navigation, collision, support, protection, landing, wall and acceptance rules remain unchanged. Pending trace is throttled to 20 ticks; report its actual sampling limits and do not claim unobserved per-tick values.

## Hard stop / exclusions

- Exactly one A2 Run1. Stop after it and publish the report, even if PASS.
- No retry, Run2, standalone C4, Full12K, or any other runtime scenario.
- No production source edits by Harness. Do not modify the Phase54B fixture or acceptance criteria.
- Use the established headless/automated path; no foreground Minecraft GUI or user intervention.
- If synchronization conflicts, local work/worlds are at risk, fixture verification fails, or the run is BLOCKED, stop without retry and document exact evidence.
- Preserve protected worlds and all local-only artifacts. ChatGPT will review the report and decide/implement any next correction.

A2 remains OPEN pending this single runtime result. Static PASS is not gameplay acceptance.

---

# HISTORICAL — 2026-11-19 A2 Phase55 compile correction (VALIDATED IN PHASE55-R2)


**Branch:** `feature/autonomous-village`
**Corrected production SHA:** `11ff43cd338d7a6222a146a2ed642a5546fb2910`
**Correction commit:** `11ff43cd338d7a6222a146a2ed642a5546fb2910`
**Previous failing tested SHA:** `9966f807b2fbb13552545cdd181f2f034c7ecbaa`
**Previous Harness report:** `docs/autonomous-village/2026-11-19-A2-PHASE55-STATIC-VALIDATION.md`
**Implementation report:** `docs/autonomous-village/2026-11-19-A2-PHASE55-INCREMENTAL-GLOBAL-ASTAR.md`

Harness static validation found two Java compile errors in `NavPlanner.advanceGlobalSearch`: local `BlockPos state` declarations redeclared the method parameter `GlobalSearchState state`. ChatGPT renamed exactly those two locals and their immediate `routePosition` references to `routeState`. Commit diff was reviewed: only `NavPlanner.java`, four changed lines (two declarations and two references). No algorithm, budgets, tests, collision, support, protection or acceptance logic changed.

**Historical status: FIX PUBLISHED — STATIC VALIDATION PASS in Phase55-R2.** Harness later confirmed compile, tests, lint and diff-check on the corrected SHA; see the current checkpoint and R2 report.

**Harness next — STATIC ONLY on exact SHA `11ff43cd338d7a6222a146a2ed642a5546fb2910`:**
1. Safely fast-forward and preserve local/untracked work and worlds.
2. Verify exact tested SHA and inspect the one-file correction diff.
3. Run diagnostics compile, `gradlew test`, strict lint, and `git diff --check`.
4. Confirm `NAV_PLANNER_STATE_TEST` executes; report its actual output and assertion count (51 source assertions were authored previously, but were not executed in the failed run).
5. Publish a documentation-only validation report and update handoffs. Harness must not alter production.

No Minecraft, A2 Run1, C4, Run2 or Full12K. This correction does not grant runtime authorization. A2 remains OPEN.

---

---

# HISTORICAL — 2026-11-19 A2 Phase55 incremental global A* (PENDING HARNESS)


**Branch:** `feature/autonomous-village`
**Production implementation SHA:** `9966f807b2fbb13552545cdd181f2f034c7ecbaa`
**Current implementation report:** `docs/autonomous-village/2026-11-19-A2-PHASE55-INCREMENTAL-GLOBAL-ASTAR.md`
**Previous runtime evidence:** `a2n-20261119-011138`, BLOCKED by global-planner watchdog after 8/8 spawn.

Phase55 implements resumable A*:
- `NavGraphSearch.Session` retains OPEN/CLOSED, G scores, predecessor and route metadata across slices.
- Strategic `NavPlanner.planGlobalIncremental` advances at 4 expansions / max 8 OPEN pops per executor per tick.
- Existing hard total global expansion budget is unchanged (default 8,000; existing property clamp 8,000–32,000).
- `NavExecutor` owns the search handle, remains PLANNING while pending, emits no partial route/movement and consumes no local stair failure counters.
- Synchronous `planGlobal` compatibility and exact `planExact` path remain synchronous.
- `NavPlannerStateTest` now includes incremental/synchronous route equivalence, slice ceiling, no partial result and hard total budget assertions.

**Not validated yet:** ChatGPT reviewed source only. No Gradle, diagnostics compile, lint, diff-check or runtime was executed by ChatGPT.

**Harness next — STATIC ONLY:**
1. Sync safely and preserve local/untracked work and worlds.
2. Verify branch HEAD includes implementation SHA above and inspect the four-file diff.
3. Run diagnostics compile, `gradlew test` (confirm `NAV_PLANNER_STATE_TEST` includes the new assertions), `lint_aldea.py --strict`, and `git diff --check`.
4. Report exact tested SHA, test assertion count and outputs. Harness must not modify production.
5. Do not start Minecraft, A2 Run1, C4, Run2 or Full12K. A separate runtime authorization is required after static review.

Runtime review after later authorization must capture per-tick pending slices, monotonic expansion progress, no movement from partial routes, total completion latency, tick duration/watchdog, route validity and IDX5/IDX3 evidence. A2 remains OPEN. Phase54B fixture remains validated and must not be changed.

---

# HISTORICAL — 2026-11-19 A2 Phase 54B-R1 strict timeout


**Production SHA:** `656ce7d0f6182fd15e0b1bbb0bd6684c180bd0e6`.
**Current report:** `2026-11-19-A2-PHASE54B-FIXTURE-IMPLEMENTATION.md` (addendum R1).
**Previous HEAD:** `a868991755eef4f1464a507c31713723a0266f87`.

- Fixture: 289 non-blocking FORCED tickets + `hasChunkAt` polling; **timeout `now >= deadline` is checked
  before READY**, so completion at/after the deadline cannot spawn. Timeout releases tickets and closes
  INVALIDO.
- Static PASS; navigation/entity production unchanged; no runtime executed.
- Need explicit authorization for one A2 Run1 on `656ce7d0`. A2 OPEN; no Run2/Full12K.

---

# HISTORICAL — 2026-11-18 A2 Phase 54A fixture forensics


**Production SHA:** `656ce7d0f6182fd15e0b1bbb0bd6684c180bd0e6`.
**Current report:** `2026-11-18-A2-PHASE54A-FIXTURE-FORENSICS.md`.
**Previous blocked run:** `a2n-20261118-233308`.
**HEAD:** `605426b2c846bc3deac0db670af234f48afa4c2b`.

- Root cause: 289 synchronous `setChunkForced` calls (each adds ticket + blocking `getChunk`) in one
  `ServerTickEvent.Pre`; cold generation exceeded the 60 s watchdog. The loop completed after the
  watchdog (`C4 CHUNKS forced=289` at 23:40:19).
- Recommended: pre-generated disposable world + non-blocking ticket/wait phase + timeout; no spawn
  until fixture verified.
- A2 remains OPEN; no Run2/Full12K until the fixture is fixed and a new Run1 is authorized.

---

# HISTORICAL — 2026-11-18 A2 Phase 53 validation BLOCKED

**Production SHA:** `656ce7d0f6182fd15e0b1bbb0bd6684c180bd0e6`.
**Current report:** `2026-11-18-A2-PHASE53-VALIDATION.md`.
**RUN_ID:** `a2n-20261118-233308` (BLOCKED: fixture chunk-force watchdog).
**HEAD:** `605426b2c846bc3deac0db670af234f48afa4c2b`.

- Static PASS on exact production SHA; both stair tests 8/6.
- A2 Run1 crashed in `A2NavHarness.prepararRaidFixture`/`setChunkForced` before any attacker.
- No IDX5/CADENCE route evidence; `INSUFFICIENT_EVIDENCE`.
- A2 OPEN; no Run2/Full12K. Need fixture stabilization and a new authorized Run1.

---

# HISTORICAL — 2026-11-18 A2 Phase 53 route preservation


**Production SHA:** `656ce7d0f6182fd15e0b1bbb0bd6684c180bd0e6` (parent `ea21466a0cc7cc2671f5439cd5ff7bbaeecd6d9e`).
**Implementation report:** `2026-11-18-A2-PHASE53-CADENCE-ROUTE-PRESERVATION.md`.
**Status:** IMPLEMENTED — PENDING HARNESS VALIDATION.

- Phase52 proved IDX5's regression: `CADENCE_WAIT` returned null after a valid global waypoint had been selected, suppressing the t3 DROP move; direct-mission fallback had the same early-return pattern.
- Phase53 removes those two route-discarding returns. The stair attempt remains deferred, but the directly reachable mission or already validated global waypoint is preserved. New trace: `A2_STAIR_CADENCE_ROUTE_PRESERVED`.
- Only `NavExecutor.java` changed in production. No tests, Gradle, lint, diagnostics compile, or runtime were run by ChatGPT.
- Harness must validate this exact SHA: static gates first; if PASS, A2 Run1 only. Verify preserved trace → valid MOVE_COMMAND for IDX5 and unchanged counters during cooldown.
- IDX3 all-solid candidate geometry remains a separate open issue. Monotonic anchor remains runtime-unvalidated until a physical stair is placed and traversed.
- A2 OPEN. No Run2; no Full12K until five consecutive 8/8 strict `DENTRO-PARED` runs. No Phase41/44/46 changes or budget/safety relaxations.

---

# HISTORICAL — 2026-11-18 A2 Phase 52 forensics

**Production SHA:** `ea21466a0cc7cc2671f5439cd5ff7bbaeecd6d9e`.
**Current report:** `2026-11-18-A2-PHASE52-STAIR-IDX5-FORENSICS.md`.
**RUN_ID:** `a2n-20261118-225649` (2/8 strict, C4 PASS `[1,0,0]`).

- IDX3: 397 builder calls, 380 cooldowns, 2,340 candidates, **all rejected by occupied terrain**
  (`celdaAire=false`); 0 placements/traversals. Not an anchor loop.
- IDX5 regression: at t2 Phase50 returned NO_PROGRESS and moved at t3; Phase51 returns CADENCE_WAIT,
  defers planning, and issues **zero MOVE_COMMAND**. Causal link confirmed.
- Monotonic anchor remains runtime-unvalidated (no physical stair).
- A2 OPEN at 2/8; no Run2/Full12K.

---

# HISTORICAL — 2026-11-18 A2 Phase 51 validation (FAIL)


**Production SHA:** `ea21466a0cc7cc2671f5439cd5ff7bbaeecd6d9e`.
**Current report:** `2026-11-18-A2-PHASE51-VALIDATION.md`.
**RUN_ID:** `a2n-20261118-215530` (2/8 strict, C4 PASS `[1,0,0]`).

- Static PASS; both policy tests run in normal Gradle gate (8/6).
- CADENCE_WAIT validated: 3595 cooldowns, 3542 `A2_LOCAL_STAIR_WAIT`, counters unchanged.
- Monotonic anchor NOT exercised: 0 constructions/traversals/ANCHOR_REFRESH. `INSUFFICIENT_EVIDENCE`.
- A2 regressed 3/8 -> 2/8; IDX5 lost its entry. A2 OPEN.

---

# HISTORICAL — 2026-11-18 A2 Phase 51 (PENDING HARNESS)


**Production implementation SHA:** `ea21466a0cc7cc2671f5439cd5ff7bbaeecd6d9e`.
**Implementation report:** `docs/autonomous-village/2026-11-18-A2-PHASE51-STAIR-WIRING-FIX.md`.
**Previous validation:** Phase50 `a2n-20261118-212254` — FAIL, 3/8 strict, C4 PASS [1,0,0].

- Phase51 wires `NavStairAnchorPolicy.refresh(previous, supportBelow, hasTraversedStair)` into
  `AggressiveZombieEntity`; post-traversal candidates are defensively required to be strictly below
  the traversed anchor.
- The builder now returns `CADENCE_WAIT` only when it actually decrements the cooldown. All three
  executor call paths preserve that result; `A2_LOCAL_STAIR_WAIT` is emitted and local step/failure
  counters are not consumed. Plan-level waits defer the next attempt by two ticks.
- Other budget-fallback outcomes retain their prior behavior; only `CADENCE_WAIT` updates the
  executor wait state.
- `NavStairAnchorPolicyTest` (8 assertions) and `StairBuildResultTest` (6 assertions) are now
  registered in the normal `policyTest` gate; `gradlew test` depends on that gate.
- **PENDING HARNESS:** validate exact SHA above. Static checks first; if all pass, A2 Run1 only.
  Preserve local/untracked work and worlds; Harness must not modify production.
- Phase41/44/46 and all budgets/safety predicates remain unchanged.
- A2 remains OPEN at 3/8 strict; C4 last PASS [1,0,0]. IDX4 horizontal-zero remains
  INSUFFICIENT_EVIDENCE. No Run2/Full12K until five consecutive 8/8 strict runs.

---

# HISTORICAL — 2026-11-18 A2 Phase 50 (PENDING HARNESS)


**Production SHA:** `f7a919c2ececcf9c745e5e95a8f141ee124ca25e`.
**Previous production SHA:** `9d6709f551f394e0ac7be69160c2aa46419d8058`.
**Implementation report:** `2026-11-18-A2-PHASE50-MONOTONIC-STAIR-ANCHOR.md`.

- Phase49A proved IDX3's anchor refresh overwrote traversed `607,93` with higher support `607,94`, recreating the same stair.
- Phase50 preserves the anchor against upward refresh, permits same-level horizontal/downward refresh, and rejects any later stair candidate not strictly below the anchor.
- Added `CADENCE_WAIT`: cooldown returns defer the tick without consuming local step or failure counters.
- Added `NavStairAnchorPolicyTest` (8 assertions); extended `StairBuildResultTest` to 6 assertions.
- **PENDING HARNESS:** static checks first; if they pass, A2 Run1 only on exact SHA above. Harness must not modify production.
- Phase41/44/46 and all budgets remain unchanged. IDX4 horizontal-zero still lacks an approved fixture.
- Latest runtime: `a2n-20261118-172804`, A2 3/8 strict, C4 PASS `[1,0,0]`. A2 OPEN.
- No Run2/Full12K until five consecutive 8/8 strict runs.

---

# HISTORICAL — 2026-11-18 A2 Phase 49A stair instrumentation


**Production implementation:** `9d6709f551f394e0ac7be69160c2aa46419d8058` (instrumentation only).
**Previous behavioral baseline:** `e1530ab30591a26237d9ae64c1e9cfefbcbb27f2`.
**Report:** `2026-11-18-A2-PHASE49A-STAIR-DIAGNOSTIC-INSTRUMENTATION.md`.

- Phase48A corrected Phase47: fallback returns on `NO_PROGRESS` before pending-waypoint lookup.
- Phase49A adds tick/cooldown/anchor/candidate traces only; no behavioral predicates or budgets changed.
- Enable `-Ddevilrpg.a2n.traceStair=true -Ddevilrpg.a2n.traceStairCandidates=true` for A2 diagnostics.
- Exact C→next-step cause remains OPEN pending Harness runtime evidence.
- IDX4 horizontal-zero remains INSUFFICIENT_EVIDENCE; no approved deterministic fixture is committed.
- A2 last measured 4/8 strict; C4 last PASS [1,0,0]. No Run2/Full12K until five consecutive 8/8 strict runs.

---

# HISTORICAL — 2026-11-18 A2 Phase 47 forensics


**Production SHA:** `e1530ab30591a26237d9ae64c1e9cfefbcbb27f2`.
**Current report:** `2026-11-18-A2-PHASE47-FORENSICS.md`.
**Previous validation:** `2026-11-18-A2-PHASE46-VALIDATION.md`.

- IDX3 stair root cause demonstrated: 20 verified placements and 20 `ESCALON-TRAVESADO`; traversal
  confirmation clears the pending stair before fallback promotion, so the same-tick fallback returns null.
- IDX4 horizontal-zero runtime remains INSUFFICIENT_EVIDENCE: no committed deterministic fixture and the
  geometry did not occur in `a2n-20261118-163717`.
- A2 OPEN at 4/8 strict; C4 PASS `[1,0,0]`. No Run2/Full12K until five consecutive 8/8 strict.

---

# HISTORICAL — 2026-11-18 A2 Phase 46 validation


**Production SHA:** `e1530ab30591a26237d9ae64c1e9cfefbcbb27f2`.
**Current report:** `2026-11-18-A2-PHASE46-VALIDATION.md`.
**Implementation report:** `2026-11-18-A2-PHASE46-VERTICAL-ONLY-DROP-FIX.md`.
**Previous forensics:** `2026-11-18-A2-PHASE45-IDX3-IDX4-FORENSICS.md`.

- Phase46 static PASS (`NAV_DROP_EDGE_STEP_POLICY_TEST` 16 assertions). A2 `a2n-20261118-163717` = 4/8
  strict (idx1, idx2, idx5, idx6), C4 PASS `[1,0,0]`; A2 OPEN.
- The exact IDX4 vertical-only geometry did not recur, so its runtime path remains INSUFFICIENT_EVIDENCE.
- IDX3 trace: `ACCESS_CLEARED`/`NO_PROGRESS`, 20 `STAIR_PLACED` outcomes and pending stairs, but fallback
  `result=null`; exact promotion to route not logged.
- No Run2/Full12K until five consecutive 8/8 strict.

---

# HISTORICAL — 2026-11-18 A2 Phase 46 vertical-only DROP fix


**Production baseline:** `61b39793b5e5fd6f747e9040c888ba32662efb2d`.
**Report:** `2026-11-18-A2-PHASE46-VERTICAL-ONLY-DROP-FIX.md`.
**Previous forensics:** `2026-11-18-A2-PHASE45-IDX3-IDX4-FORENSICS.md`.

- Phase46 production/test change is implemented but not validated; run static checks first.
- Expected `NAV_DROP_EDGE_STEP_POLICY_TEST: OK (16 assertions)`.
- A2 remains OPEN at 3/8 strict (`a2n-20261118-145308`); C4 last PASS `[2,0,0]`.
- Phase45 IDX4: vertical-only DROP, distance 0. Phase46 permits the existing bounded MoveControl actuator
  to attempt support clearance; landing still requires natural descent and physical confirmation.
- Phase45 IDX3: local BUILD/STEP cycle remains unresolved. Enable `-Ddevilrpg.a2n.traceStair=true`
  on next A2 Run1 to capture the missing builder lifecycle; do not change budgets.
- Phase44 approach ownership and Phase41 actuator lifecycle remain frozen; only zero-distance admission
  is extended. Phase29/31/34/36 unchanged.
- No Run2 or Full12K until five consecutive 8/8 strict runs. Harness must not modify production.

---

# HISTORICAL — 2026-11-18 A2 Phase 45 IDX3/IDX4 forensics

**Production SHA:** `ed31b815193aa4fcde0b088f1a6d110270ba4a9d`.
**Current report:** `2026-11-18-A2-PHASE45-IDX3-IDX4-FORENSICS.md`.
**Previous validation:** `2026-11-18-A2-PHASE44-VALIDATION.md`.

- Phase44/41 contracts remain frozen and validated.
- Phase45: IDX3 is a local BUILD/step objective cycle with fallback `null` (64/64, 10/10); IDX4 is a
  vertical-only DROP at `597,79,499` → `597,78,499`, outside Phase41's horizontal-distance>0 activation.
- A2 OPEN at 3/8 strict (`a2n-20261118-145308`); C4 PASS `[2,0,0]`.
- No Run2/Full12K until five consecutive 8/8 strict.

---

# HISTORICAL — 2026-11-18 A2 Phase 44 non-adjacent DROP approach owner


**Production implementation SHA:** `ed31b815193aa4fcde0b088f1a6d110270ba4a9d`.
**Implementation parent:** `793816686e4373d4b581264468f2ee92ce431156`.
**Current report:** `2026-11-18-A2-PHASE44-VALIDATION.md`.
**Implementation report:** `2026-11-18-A2-PHASE44-NON-ADJACENT-DROP-APPROACH-OWNER.md`.
**Previous forensic report:** `2026-11-18-A2-PHASE43-NON-ADJACENT-DROP-FORENSICS.md`.

- Phase43 proved the non-adjacent DROP approach can lose movement ownership after Navigation returns a completed one-node path.
- Phase44 adds a bounded DROP-only Navigation → MoveControl approach owner. Navigation retains control while progressing; completed path or >20 no-progress ticks transfers to MoveControl.
- MoveControl advances only toward the already validated supported waypoint. Waypoint safety is rechecked each tick; arrival requires exact block occupancy, grounded state and valid support/AABB fit.
- Owner bounds: >80 total ticks or >20 no-progress ticks aborts safely to OBSTRUCTED. No planner budgets, safety predicates, landing tolerances or Phase29/31/34/36/41 behavior changed.
- New policy test: `NAV_DROP_APPROACH_OWNERSHIP_POLICY_TEST` (13 assertions), registered in `gradlew test`; **not run by ChatGPT**.
- **HARNESS VALIDATION COMPLETE** on `ed31b815`: static PASS; A2 Run1 `a2n-20261118-145308` = 3/8 strict (idx1 t559, idx5 t730, idx6 t650), C4 PASS `[2,0,0]`. Ownership chain Navigation→MoveControl→Phase41 landing validated for IDX3/4/5. A2 remains OPEN.
- A2 remains **OPEN**. Five consecutive 8/8 strict runs remain the gate before Run2/Full12K.
- Production commits: `b4f53966f7336d997ace224660d1a5cb892e2786` (initial owner); `5b71f063798fc0fd50be4dd2b4d3d5c58041262a` (lifecycle hardening); `ed31b815193aa4fcde0b088f1a6d110270ba4a9d` (Navigation release on every owner clear).
- Harness remains execution/evidence owner; it must not modify production.

---

# HISTORICAL — 2026-11-18 A2 Phase 43 non-adjacent DROP approach/landing

**Production code checkpoint:** `4379dbc250856b292b6cc2e3703b6aaf3bc8022b`.
**Forensic source HEAD:** `b624546c8e8174092419bd8d3022845e1b10ae4c`.
**Report:** `2026-11-18-A2-PHASE43-NON-ADJACENT-DROP-FORENSICS.md`.

- IDX3/5 froze short of the supported waypoint with a one-node completed path and no movement owner; each exhausted 8/8 replans.
- IDX4's later non-adjacent landing at `577,111,522` had fit/support true but remained outside physical landing tolerance, with no movement owner toward the landing.
- Phase41 edge-step was not involved and no regression was demonstrated. Missing immediate post-`moveTo` path result remains a forensic limitation.
- Phase44 is the implementation response; see current checkpoint above. A2 remains OPEN.

---

# HISTORICAL — 2026-11-18 A2 Phase 42 remaining non-entrants


**Production code checkpoint:** `4379dbc250856b292b6cc2e3703b6aaf3bc8022b`.
**Branch HEAD:** `747826227a9a6bee9c29cf65cc34f99cb55f3003`.
**Current report:** `2026-11-18-A2-PHASE42-REMAINING-NON-ENTRANTS-FORENSIC.md`.
**Previous edge-step fix:** `2026-11-18-A2-PHASE41-DROP-EDGE-STEP-FIX.md`.

- Phase 42 analyzed `a2n-20261118-140906` (A2 2/8 strict, C4 PASS `[2,0,0]`). All six non-entrants have
  independent causes: IDX2 source recovery, IDX3/5 DROP-approach replans, IDX4 later non-adjacent DROP
  landing, IDX7 BUILD budget, IDX8 step/local budget.
- Phase 41 edge-step is not a regression: IDX4 landed at t24 and IDX8 at t279 before their later blockers.
- Next diagnostic priority: non-adjacent DROP landing/approach (IDX3/5 and later IDX4); do not reopen the
  adjacent edge-step.
- A2 remains OPEN; no Run 2; no 12K before five consecutive 8/8 strict.

---

# HISTORICAL — 2026-11-17 ranked AABB DROP source selection (Phase 26)


**Production code checkpoint:** `a795c33d03678e2a12d066645e5dbc927d7f3efb`.
**Detailed report:** `2026-11-17-A2-GROUNDED-SOURCE-SELECTION-PHASE26.md`.

- Phase 25 tested `011373d0b3423b188611867d7f4751ab2ac88c66`: static PASS, A2 Run 1 FAIL 3/8 perimeter and 3/8 strict; C4 PASS `[1,0,0]`. No Run 2/Full 12K.
- Phase 25 emitted 77 `DROP_TAKEOFF_UNSUPPORTED_SOURCE_RECOVERY`, zero source snap/replan; IDX4 UUID `03df487a-ff61-482c-9407-ed5cdd0d1b5d` failed at `567,122,534` with grounded=true, feetValid=false, support=false, realAabbFits=true.
- Root cause in the resolver contract: `uniqueAdjacentCandidate` considered only four cardinal cells at the same Y and returned null when more than one candidate was physically valid. It could also miss a valid diagonal overlap. Thus a set of safe alternatives was treated as a dead-end.
- New `NavSourceCellPolicy.bestAdjacentCandidate` evaluates all eight same-Y neighbors with positive real-AABB horizontal overlap. The caller still requires `esPieValido` (including protection, feet/head clearance and solid support), explicit support, and `fitsAtCellCenter` for the translated real AABB.
- Deterministic ranking: greatest actual horizontal overlap area, then nearest candidate center to the mob's current X/Z, then lexicographic X/Z tie-break. No candidate is accepted on proximity alone. The executor uses this selector in both grounded source recovery paths and retains horizontal MoveControl snap plus physical occupancy verification before replanning.
- Source-cell policy tests expected: 14 assertions (previous 10 + 4 covering multi-candidate deterministic selection, overlap ranking, diagonal-only candidate, and no-safe-candidate rejection). DROP take-off policy remains 51 assertions.
- **PENDING HARNESS:** safely sync the live branch HEAD (resolve it from Git; the handoff documentation will be a descendant of this code checkpoint), preserve local/untracked files and worlds. Run Gradle tests, strict lint, diff-check, and diagnostics compile. Confirm source-cell policy 14 and DROP take-off 51 assertions.
- If static validation passes, execute A2 Run 1 only. Inspect candidate selection and snap/replan for IDX4 and the former blocked attackers. Confirm selected source has support, valid feet, protection clearance and centered real-AABB fit; confirm actual physical snap before route planning.
- If no candidate is selected, capture each of the eight neighbors' overlap, chunk-loaded, protected, feet-valid, support and translated-AABB-fit predicates. Do not widen search vertically, invent a source, or weaken safety predicates without a separate evidence-backed design.
- No Run 2 unless Run 1 is 8/8 strict. No Full 12K until five consecutive 8/8 strict runs. No budget increases or relaxed support, collision, protection, headroom or landing criteria.
- A2 remains OPEN. C4 last PASS [1,0,0], save/reload OPEN. IDX7/IDX8 remain independent. Original worlds and local artifacts protected. Harness must not modify production.
- ChatGPT has not executed Gradle tests, lint, diagnostics compile, or Minecraft runtime.

---

# CURRENT CHECKPOINT — 2026-11-17 grounded invalid DROP source recovery (PENDING HARNESS)

**Code checkpoint:** `e80f99d5acd4bf987524d4ab5d5a79ad5392fdd6` (the handoff documentation is committed afterward; always sync the live branch HEAD).
**Production code checkpoint:** `e80f99d5acd4bf987524d4ab5d5a79ad5392fdd6`.
**Detailed report:** `2026-11-17-A2-GROUNDED-INVALID-DROP-SOURCE-RECOVERY-FIX.md`.

- Phase 24 tested `37c0aa702b44d94c6d5db506d023ec65d1b8`: A2 FAIL 1/8 perimeter and 1/8 strict; 2,102 unsupported-source WAIT events. The dominant state was grounded with invalid feet/support but a fitting real AABB.
- Root cause: grounded-invalid source bypassed the existing AABB source resolver and fell into the airborne WAIT branch. It now snaps only to a unique cardinal same-Y supported cell overlapped by the real AABB, using horizontal MoveControl, then replans from the physically verified source.
- If no unique source exists or the snap cannot be physically completed within 80 ticks, the executor emits an explicit failure and fails closed; no infinite wait loop.
- A missing `plannedFrom` can no longer bypass DROP origin admission: the live cell is accepted only if grounded, valid, supported and real-AABB fitting; otherwise source recovery/wait occurs.
- `DROP_START` now logs originPlan and physical source predicates for IDX4 forensic diagnosis.
- Policy test expected count is now 51 assertions (47 + 4). Tests authored, **not executed by ChatGPT**.
- **PENDING HARNESS:** sync exact HEAD above; run Gradle tests, strict lint, diff-check and diagnostics compile first. Confirm 51 assertions. If static PASS, A2 Run 1 only.
- Capture source recovery/snap/replan events for the seven blocked entrants and new DROP_START origin fields plus landing/cursor events for IDX4 UUID `bbadffa7-09c6-49fa-8adf-c1cd118e30f5`.
- No Run 2 unless Run 1 is 8/8 strict. No Full 12K until five consecutive 8/8 strict runs. No budget increases or relaxed geometry/support/protection/landing criteria.
- A2 remains OPEN. C4 last PASS [1,0,0], save/reload OPEN. IDX7/IDX8 remain independent. Original worlds/artifacts protected. Harness must not modify production.
- ChatGPT has not run tests, lint, diagnostics compile, or Minecraft runtime.

---

# CURRENT CHECKPOINT — 2026-11-17 IDX4 unsupported DROP take-off guard (PENDING HARNESS)

**This checkpoint supersedes the Phase 23 immediate-work instructions below. Historical results remain unchanged.**

- Phase 23 established that planned take-off `589,96,522` is unsupported; the mob later landed at supported `589,93,522`, while the requested landing `589,95,521` had no `DROP_DONE` or cursor advance.
- Source review found coordinate-only take-off admission: `origenFisicamenteAlcanzado` did not require grounded state, valid feet, support, or actual AABB fit.
- ChatGPT added a runtime admission overload requiring grounded + valid feet + support + actual AABB fit, and uses it in both DROP execution and descent validation.
- If the planned take-off itself is unsupported, the executor now refuses the DROP. It waits while airborne; once the mob is grounded in a valid, supported, fitting live cell, it discards the stale DROP route/cache and replans the strategic route from that actual source.
- New traces: `DROP_TAKEOFF_UNSUPPORTED_WAIT` and `DROP_TAKEOFF_UNSUPPORTED_REPLAN`.
- Added four policy assertions; expected `NAV_DROP_TAKEOFF_POLICY_TEST: OK (47 assertions)`.
- Report: `2026-11-17-IDX4-UNSUPPORTED-TAKEOFF-REPLAN-FIX.md`.
- Implementation commits: policy `59f94193`; test `3ba86c0c`; executor `91aff8df`, `b166fd37`, `c17ec47e`.
- **Current implementation HEAD before handoff docs:** `c17ec47e7277a687fa428841d64ca8c8089e68c1`. Documentation commits may advance it; always fetch live HEAD before sync.
- **PENDING HARNESS:** sync exact live HEAD safely; static tests/lint/diff/diagnostics compile first; if static passes, A2 Run 1 only. Capture both new traces plus DROP_START/LANDING_PROGRESS/DONE/CURSOR_ADVANCE.
- No Run 2 unless 8/8 strict; no 12k until five consecutive 8/8 strict. No budget increase or weakened geometry/support/protection/landing criteria.
- A2 remains OPEN. C4 last observed PASS [1,0,0], save/reload OPEN. IDX7/IDX8 remain independent and untouched. Original worlds and local artifacts remain protected.
- ChatGPT has not executed tests, lint, diagnostics compile, or Minecraft runtime.

---

# CURRENT CHECKPOINT — 2026-11-17 IDX4 Phase 23 targeted forensic instrumentation

**This section supersedes older immediate-work statements below. Historical entries remain unchanged.**

- Phase 22 read-only forensic extraction confirms the exact recovery A* expands `589,95,521`; the candidate `589,95,522` is generated but rejected inside `validarArista`. Exact rejection reason remains UNKNOWN because no candidate-level geometry/policy evidence was recorded.
- The same run separately confirms that DROP landing `589,95,521` receives commands but has no verified physical landing, no matching `DROP_DONE`, and no cursor advance. The earlier `DROP_DONE` for `589,93,522` is a different route cycle and must not be counted.
- ChatGPT added sparse, opt-in-only diagnostics without changing movement semantics:
  - `NavPlanner.java`: `DROP_TAKEOFF_EXACT_EDGE_DIAG` captures the exact one-block-below-goal candidate, edge rejection enum, transition, feet values, protected status, fit/support/headroom and cell snapshot.
  - `NavExecutor.java`: `DROP_LANDING_PROGRESS` records physical landing predicates every 10 ticks; `DROP_CURSOR_ADVANCE` records cursor advancement only after the existing physical arrival check succeeds.
- Commits: planner `81890f7e80f2281481b479968d149437f59e9346`; executor `f43249eabda633569277ee73fa64835273a558e8`.
- Detailed instructions: `2026-11-17-IDX4-PHASE23-FORENSIC-INSTRUMENTATION.md`.
- **PENDING HARNESS**: static validation first on exact live HEAD, then A2 Run 1 only if static passes. No Run 2 unless 8/8 strict; no 12k until five consecutive 8/8 strict.
- A2 remains OPEN. No budget increase or weakened collision/support/headroom/protection/landing conditions. IDX7/IDX8 remain independent. C4 save/reload OPEN.

---

# CURRENT CHECKPOINT — 2026-11-17 IDX4 AABB source recovery

**This section supersedes older "Immediate work" statements below.** Historical entries remain unchanged.

- Forensic root cause confirmed in `2026-11-17-IDX4-GROUNDED-INVALID-FORENSICS.md`: mob AABB spans Z=507.66..508.26 and physically rests on cobblestone (593,86,508), while integer blockPosition=(593,87,507) checks air below at (593,86,507).
- ChatGPT implementation published through production checkpoint `b02affb19eb8e37824682090ba99f793a04aea94`.
- New source resolver selects exactly one cardinal same-Y supported cell overlapped by the actual AABB, validates the actual translated AABB at candidate center, then issues bounded horizontal MoveControl only. It requires physical center occupancy and rechecks feet/support/current-AABB fit before A*.
- Recovery route now permits at most one planner-validated DROP of exactly one block, because resolved source (593,87,508) is one block above original take-off (593,86,507). It rejects longer/multiple drops and all destructive/build/bridge transitions. Temporary route start is saved/restored so the recovery DROP resolves the correct origin.
- Added `NavSourceCellPolicyTest` (10 assertions); `NavDropTakeoffPolicyTest` now expects 43 assertions; `NavRouteNodePolicyTest` remains 6. Harness Phase 21 executed all three: PASS 10/40/6.
- Detailed implementation: `2026-11-17-IDX4-AABB-SOURCE-RECOVERY-FIX.md`.
- Harness static gate at `1e65c024` was BLOCKED only by a missing test import; the historical result is preserved in `2026-11-17-A2-RUN1-AABB-SOURCE-RECOVERY-RESULT.md`.
- ChatGPT added `import net.minecraft.core.BlockPos;` to `NavDropTakeoffPolicyTest.java` (test-only), commit `c75165c2135cccbfcc3eddd40abe09adce6766e8`.
- Confirmed `build.gradle` registers `NavDropTakeoffPolicyTest`, `NavSourceCellPolicyTest`, and `NavRouteNodePolicyTest` in `policyTest`.
- Harness Phase 21 at `77b635d4`: static PASS; source snap reached `596,77,499` with feet/support/real-AABB true; old local recovery A* returned empty at `900/900`; A2 FAIL 5/8 strict, C4 PASS `[2,0,0]`. Historical result: `2026-11-17-A2-RUN1-AABB-SOURCE-RECOVERY-PHASE21.md`.
- Harness Phase 22 at `c17459b7`: static PASS (10/43/6); exact A* bounded and `hitBudget=false`, but stopped at `589,95,521` vs takeoff `589,96,522`; 39 rejections, no route accepted/restored/reached. A2 FAIL 5/8 strict, C4 PASS `[1,0,0]`.
- Harness result: `2026-11-17-A2-RUN1-EXACT-BOUNDED-RECOVERY-PHASE22.md`.
- Harness Phase 23 at `69f066cc`: static PASS (10/43/6); exact-edge diag shows candidate `589,95,522` is air/no-support, classified DROP, and exact goal `589,96,522` has `feetSupport=false`. A2 FAIL 5/8, C4 PASS `[1,0,0]`.
- Harness result: `2026-11-17-A2-RUN1-PHASE23-EDGE-DIAG-RESULT.md`.
- Harness Phase 24 at `37c0aa70`: static PASS (10/47/6); A2 **regressed to 1/8 strict, 1/8 perimeter**. `DROP_TAKEOFF_UNSUPPORTED_WAIT` fired 2102 times with `grounded=true feetValid=false support=false`; only idx1 entered. C4 PASS `[1,0,0]`.
- Harness result: `2026-11-17-A2-RUN1-UNSUPPORTED-TAKEOFF-PHASE24.md`.
- Harness Phase 25 at `011373d0`: static PASS (10/51/6); A2 FAIL 3/8 strict, 3/8 perimeter, C4 PASS `[1,0,0]`. IDX4 has 41 `UNSUPPORTED_SOURCE_RECOVERY`, no candidate/snap, 5 NAV_FAILs (`no unique supported AABB-overlapping cell`); no DROP_DONE/cursor.
- Harness result: `2026-11-17-A2-RUN1-GROUNDED-INVALID-DROP-SOURCE-PHASE25.md`.
- Harness Phase 26 at `29b82a2c`: static PASS (14/51/6); A2 FAIL 2/8 strict, 3/8 perimeter, C4 PASS `[2,0,0]`. Candidates exist (16) but `SOURCE_SELECTED=0`, `SNAP_REACHED=0`; IDX4 has 19 snaps started and never completes occupancy.
- Harness result: `2026-11-17-A2-RUN1-GROUNDED-SOURCE-SELECTION-PHASE26.md`.
- Phase 27 forensic result at `05b7e9c0`: `ROOT_CAUSE_PARTIALLY_CONFIRMED`. The snap targets z=514.5 but a normal DROP `MOVE_COMMAND` at t4261 targets z=513.5; blockPosition oscillates 513↔514; `recordCell` flags oscillation and `traverse` switches to RECOVERING at t4271 before center tolerance. `SOURCE_SELECTED` is not implemented; full eight-neighbor matrix is missing.
- Report: `2026-11-17-A2-PHASE27-SNAP-FORENSIC-DIAGNOSIS.md`.
- Harness Phase 28 at `cebca95b` (production `05b7e9c0`): root cause **PARTIALLY_CONFIRMED**. `originPlan` is candidate `577,110,514`; `origenFisicamenteAlcanzado` admits same integer cell at z=514.01 despite 0.49 to center. Normal DROP navigation then targets z=513.50 vs snap 514.50; cells oscillate 513↔514 and guard switches to RECOVERING at t4271.
- Report: `2026-11-17-A2-PHASE28-DROP-SNAP-OWNERSHIP-DIAGNOSIS.md`.
- Harness Phase 29 implemented `NavSnapOwnershipPolicy` (production `192d0c52`): pending snap blocks route movement, DROP admission cannot continue until `SNAP_COMPLETE` with 0.20 center + grounded/feet/support/real-AABB. Added `SNAP_MOVE_OWNER`, `SNAP_ADMISSION`, `SNAP_SWEPT_AABB`, `SNAP_GUARD`, `SNAP_COMPLETE`, `SOURCE_SELECTED`.
- Static PASS: `NAV_SNAP_OWNERSHIP_POLICY_TEST: OK (12)`, DROP 51, source 14. Runtime `a2n-20261117-170935`: A2 2/8 strict, C4 PASS `[1,0,0]`. IDX4 completes snap at t211 with collision=false; later fails `replans agotados (8/8)`.
- Report: `2026-11-17-A2-PHASE29-DROP-SNAP-EXCLUSIVE-OWNERSHIP-FIX.md`.
- Phase 30 read-only diagnosis: ROOT_CAUSE confirmed for post-snap exhaustion. First post-snap route accepted at t615, stale oscillation aborts at t616 and stores `lastRouteSignature`; seven deterministic identical replans are rejected by signature, reaching 8/8 at t3495.
- Report: `2026-11-17-A2-PHASE30-IDX4-POST-SNAP-REPLAN-DIAGNOSIS.md`.
- Harness Phase 31 production `ec6aa339`: one-time `SNAP_POST_RESET` clears `recentCells`/`oscillating`/occurrences and invalidates `lastRouteSignature`; first post-snap plan accepted and TRAVERSING; later guards/duplicate protection preserved. Static 12-assertion post-reset test PASS; runtime `a2n-20261117-190320` A2 2/8 strict, C4 PASS `[1,0,0]`.
- Report: `2026-11-17-A2-PHASE31-POST-SNAP-STATE-RESET-FIX.md`.
- Phase 32 forensic: all 8 accounted. idx1/idx6 entered strict (t593/t650) then died in combat; idx2 terminal no-safe-source recovery; idx7 BUILD budget exhaustion; idx3/4/5/8 timeout with 135–141 verified snap resets each (~43 ticks/block), no Phase29/31 guard/duplicate failure.
- Report: `2026-11-18-A2-PHASE32-NON-ENTRANT-FORENSIC-DIAGNOSIS.md`.
- Phase 33 forensic: ROOT_CAUSE_CONFIRMED common mechanism. DROP_APPROACH calls `Navigation.moveTo` to landing center, bypassing supported intermediate node; center crosses into unsupported adjacent column, source snap pulls back, repeats every ~43 ticks. idx3/4/5/8 confirmed with first cycle positions.
- Report: `2026-11-18-A2-PHASE33-SNAP-CHURN-GEOMETRY-DIAGNOSIS.md`.
- Harness Phase 34 production `ee0c4fe4`: `NavDropApproachPolicy` selects supported adjacent waypoint before landing; DROP no longer direct-targets across unsupported column. Static 10-assertion PASS; runtime `a2n-20261117-210328` A2 2/8 strict, C4 PASS `[1,0,0]`. Churn resets: idx3 139→14, idx5 141→4, idx4/idx8 0.
- Report: `2026-11-18-A2-PHASE34-DROP-APPROACH-SUPPORTED-PATH-FIX.md`.
- Phase 35 read-only: IDX4 adjacent DROP confirmed. `Navigation.stop()` then `aplicarAcercamientoAdyacente(landing)` rejects because Manhattan 1+3+1=5 > DROP max 3; no MoveControl, no descent, 22 attempts at same position. IDX8 never reaches DROP; independent BUILD failure.
- Report: `2026-11-18-A2-PHASE35-ADJACENT-DROP-NON-DESCENT-DIAGNOSIS.md`.
- Harness Phase 36 production `6aec35c6`: adjacent branch now uses horizontal-distance DROP nudge and Navigation.moveTo; telemetry proves commands are emitted but `DROP_DESCENT_STARTED` remains 0 and `DROP_DESCENT_STALLED` occurs (IDX4 54 cmds/5 stalled; IDX8 824/136). A2 2/8 strict, C4 PASS `[2,0,0]`.
- Report: `2026-11-18-A2-PHASE36-ADJACENT-DROP-TAKEOFF-FIX.md`.
- Phase 37 forensic: real `Navigation.moveTo` destination is landing center (not takeoff). IDX4/IDX8 have real paths and horizontal movement but no Y change; IDX3/5 emit no descent command. Remaining collision-vs-controller cause NOT_LOGGED; Stage B required.
- Report: `2026-11-18-A2-PHASE37-DROP-DESCENT-PHYSICAL-FORENSICS.md`.
- Phase 38 Stage B attempted (`a2n-20261118-110342`) with temporary before/after-travel probes; run hit server watchdog in `NavObstruction.fits`/`NavPlanner` before any DROP. No tick trace produced; instrumentation fully reverted; production `6aec35c6` intact.
- Report: `2026-11-18-A2-PHASE38-DROP-DESCENT-PHYSICAL-EXECUTION-TRACE.md` (BLOCKED).
- Phase 39 static: watchdog explained as synchronous global A* cost — 8,000 expansions × 56 offsets = 448,000 edges, multiple bounded collision queries per edge, no tick yield; log says 60.00s (crash text mislabels ms as s). Probe not causal. Deterministic DROP fixture designed (IDX4/IDX8 geometry), not executed.
- Report: `2026-11-18-A2-PHASE39-PLANNER-WATCHDOG-AND-DROP-FIXTURE-DIAGNOSIS.md`.
- Phase 40 fixture: IDX4-like TRACE_COMPLETE (`a2n-20261118-134234`), local plan exp=1/900, no global. 82 travel rows: path 2 nodes then null, MoveControl.hasWanted=false all rows, Y constant122, verticalCollision=true. IDX8-like FIXTURE_NO_DROP (`a2n-20261118-134458`): plan=- for300 ticks, no trace.
- Report: `2026-11-18-A2-PHASE40-DETERMINISTIC-DROP-FIXTURE-TRACE.md`.
- Phase 41 production `4379dbc2`: bounded DROP edge-step transfers ownership to MoveControl after path end. Real A2 run `a2n-20261118-140906`: IDX4 descends t17-24 and lands at 567,119,534; IDX8 descends t275-279. A2 still 2/8 strict, C4 PASS [2,0,0].
- Report: `2026-11-18-A2-PHASE41-DROP-EDGE-STEP-FIX.md`.
- **NEXT: diagnose remaining A2 non-entrants; edge-step blocker closed.**
- ChatGPT implemented exact-goal bounded recovery A* using the existing validated global A* core: horizontal radius 4, vertical envelope 3, unchanged 900 hard cap; no partial/approximate goal acceptance and no BREAK/BUILD/BRIDGE or multi-block DROP.
- Failed temporary planning now preserves the original strategic route, cursor and `plannedFrom`; retries use cooldown. Successful recovery emits `DROP_TAKEOFF_RECOVERY_ROUTE_RESTORED` with route signature/cursor/origin.
- The 0.20-block radial source-center tolerance is retained: Phase 21's 0.19 offset is within tolerance and had grounded/feet/support/real-AABB evidence. Exact floating-point center is not required.
- Detailed fix and Harness protocol: `2026-11-17-IDX4-EXACT-RECOVERY-ASTAR-FIX.md`.
- Expected `NAV_DROP_TAKEOFF_POLICY_TEST`: 43 assertions. **PENDING HARNESS**; ChatGPT has not run tests, lint, diagnostics compile or runtime on this implementation.
- **NEXT: Harness validates exact live HEAD, static gate first, then A2 Run 1 only.**
- Do not run A2 Run 2 unless Run 1 reaches 8/8 strict. Do not run full 12k until five consecutive 8/8 strict runs.
- IDX7/IDX8 untouched. C4 last observed PASS [1,0,0], save/reload OPEN. A2 remains OPEN pending runtime evidence.
- Harness must not modify production source. Original A2/C4 worlds and local artifacts remain protected.

---

# Documentation reconciliation — 2026-11-17

ChatGPT reviewed the shared branch collaboration documents after the Harness report
`a2n-20261117-020800` and found two contradictory legacy documents. The permanent role policy and
the explicit ChatGPT/Harness contract already correctly assigned implementation to ChatGPT and
testing/evidence to Harness, but `GOVERNANCE.md` and `WORKFLOW.md` still described ChatGPT as
read-only and a local-first orchestrator as the workflow.

Those contradictions have now been corrected directly on `feature/autonomous-village`:

- `docs/autonomous-village/GOVERNANCE.md` — commit `c670f2ba2b979b9ebe1ceaa81d1cbd7cd072765e`.
- `docs/autonomous-village/WORKFLOW.md` — commit `9fbc63344acf6e0a575efe1d22b80509cf5b4d5d`.

The corrected documents now explicitly state:
- `feature/autonomous-village` is the shared communication channel and durable source of truth.
- ChatGPT directly investigates, edits production source, reviews diffs, commits and publishes on that branch.
- Harness synchronizes the branch locally, runs all requested static/runtime tests, records metrics/logs and publishes dated reports/documentation to the same branch.
- ChatGPT reviews every Harness report and Harness-authored documentation change, reconciles errors/contradictions and updates the current handoff.
- Harness does not independently implement production source fixes.
- The user's pasted Harness report is a handoff; ChatGPT must also inspect the repository report/source.
- Local Harness worktree is the execution environment, not a competing source-of-truth branch.

No production source was changed in this documentation reconciliation. No tests or Minecraft runtime
were run by ChatGPT. The latest empirical status remains the Harness result for tested SHA
`b3064596d457e6e7877607a534f8889a4214045d`: A2 FAIL (5/8 strict, 5/8 perimeter), C4 FAIL
`[0,0,0]`; IDX4 grounded recovery still has no accepted route; IDX7/IDX8 remain independent.
Run 2 and Full 12K remain prohibited by the acceptance gates.

**Next implementation:** inspect the live source for IDX4's airborne landing-anchor stabilization
(change documented as `2026-11-17-A2-IDX4-LANDING-ANCHOR-FIX.md`), verify its safety/geometry and
instrumentation, then make a focused correction to obtain a genuinely grounded supported recovery
source. Investigate C4 `assignmentsClean=false` separately. Harness validates the exact new SHA.

---

# NEXT SESSION — A2 / C4 Stability

## Latest checkpoint — 2026-11-17 airborne DROP take-off recovery correction (PENDING HARNESS)

- Harness Run 1 `a2n-20261117-001624` tested `7aed2c683852969c323524bbed7d95965c330790` and returned static PASS but A2 **FAIL 4/8 strict, 5/8 perimeter**. IDX4 emitted `DROP_TAKEOFF_STEP_UP_ASSIST` while `onGround=false`, then fell to y119 and stalled below the STEP_UP waypoint. The old trace does not record whether the auxiliary A* plan was accepted while grounded, so that plan-time state is not retrospectively proven. Source review did establish that no grounded/source-support admission guard existed; the new code adds one and records plan-admission state.
- Initial implementation commit: `bd638e92dd86214dd8af3a17f1a6726daaea39e8`; final source checkpoint including plan-admission telemetry: `190bb6578906068e33c3a835feb2cacf7bc93085`. Recovery planning now requires a grounded, valid, supported, fitting source cell. While falling, the original DROP route remains pending and the executor waits/retries rather than accepting a route from a transient cell.
- STEP_UP recovery now keeps horizontal MoveControl while airborne but does not request a jump until grounded. If it lands at an elevation that makes the active one-block edge stale, the auxiliary recovery is abandoned fail-closed and the strategic mission is replanned from the live cell. No teleport, direct velocity impulse, destructive action, budget increase, or weakened support/fit/landing predicate.
- Policy tests extended to cover grounded source admission, adjacent one-block geometry, and airborne/unsupported rejection. Tests are authored, **not run by ChatGPT**.
- Harness must run static checks first on exact live HEAD, then A2 Run 1 only. Capture `DROP_TAKEOFF_RECOVERY_WAIT_GROUNDED`, recovery plan start coordinates, `DROP_TAKEOFF_STEP_UP_ASSIST` only with `onGround=true` and `sourceSupport=true`, exact waypoint occupancy, take-off reached, restored DROP cursor and physical landing. If Run 1 is not 8/8 strict, do not run Run 2.
- IDX7 BUILD/900-expansion and IDX8 global/local route failures remain independent. C4 `[1,0,0]` remains PASS; save/reload OPEN. A2 OPEN; no full 12k until five consecutive strict 8/8 runs.


## Latest checkpoint — 2026-11-16 DROP route-node feet correction (PENDING HARNESS)

- Forensic source: Run 1 `a2n-20261116-190555`, SHA `2d47d628724a677269e1c3d895759154f56a52c9`.
- IDX4/IDX8 confirmed route reconstruction defect: DROP route nodes used graph-state Y instead of validated landing `Step.feet`.
- ChatGPT added `NavRouteNodePolicy`; global complete routes, global partial/frontier routes and local routes now map DROP nodes to `(state.x, feet, state.z)`.
- BREAK still uses its physical stand as `Node.pos` and preserves the solid target in `Node.actionPos`. Other transitions retain canonical graph state.
- Added `NavRouteNodePolicyTest` (6 assertions) and registered it in `policyTest`.
- Code commits: `866d8169`, `38a15bb7`, `cdf4c9f5`, `b19f413c`. Detailed report: `2026-11-16-A2-DROP-ROUTE-FEET-FIX.md`.
- **PENDING HARNESS**: no Gradle, lint, diff-check, diagnostics compile or runtime has been executed by ChatGPT. Harness must validate exact live HEAD, then rerun A2 Run 1 only.
- IDX7 BUILD-FALLIDO/local 900-expansion exhaustion is intentionally unchanged pending measurement of this DROP fix. Do not increase budgets or weaken BUILD/collision/entry criteria.
- A2 remains OPEN; Run 1 must pass 8/8 before Run 2; five consecutive strict 8/8 before full 12k. C4 save/reload remains OPEN.


Code checkpoint SHA: `7692a2cc7cbb4420531a7ff1ac0dc379badd10bf`; subsequent handoff commits are documentation-only. Always fetch the live branch HEAD before synchronization.

## Latest checkpoint — 2026-11-16 Phase19 independent exclusion/backtrack fixtures

- Harness run `a2n-20261116-174648` on `8b389672`: `break-runtime` **PASS**. Both targets air, exact 40-tick retry, exact stand occupancy and both strategic missions resumed. This is fixture validation, not A2 siege acceptance.
- Current code adds independent modes `-Da2n.mode=break-edge-excluded` and `-Da2n.mode=nav-backtrack` in `A2NavHarness.java`. They must run in separate disposable worlds with trace enabled.
- Code review found a backtrack selector edge case: it selected the latest valid history cell even when within arrival tolerance, then aborted without trying older cells. ChatGPT added a distance-aware selector overload; executor now skips too-near cells using the existing 2.25 squared arrival tolerance. Added three unit assertions, including the 16-cell history bound.
- Added read-only runtime diagnostics for recovery history/backtrack state and current BREAK fingerprint exclusion.
- Fixture acceptance is strict: BREAK requires two unverified actions on the same edge 40 ticks apart, active unchanged fingerprint exclusion, a different BREAK edge selected, both blocks still solid and cursor not past BREAK. Backtrack requires two no-progress failures, exactly one selected historical cell, history <=16, exact physical return, <=1.5 blocks/tick, strategic mission preserved and resumed.
- Detailed report: `2026-11-16-PHASE19-BREAK-EXCLUSION-BACKTRACK-FIXTURES.md`.
- **PENDING HARNESS**: sync exact HEAD, run tests/lint/diff-check/diagnostics compile, then run both fixture modes independently. ChatGPT has not executed these validations.
- A2 remains OPEN; no full 12k before five consecutive strict 8/8 `DENTRO-PARED` runs. C4 save/reload remains OPEN.

## Latest checkpoint — 2026-11-16 Phase19 BREAK retry observation window

- Harness run `a2n-20261116-173828` on `08cf0e8c`: both targets air, retry exactly 40 ticks, Scenario A resumed. Scenario B remained at X=737 (target X=737) at the 20-tick deadline while state was TRAVERSING and movement commands targeted X=738/739. The original physical-crossing predicate had not yet passed.
- ChatGPT changed **only** `src/main/java/com/chipoodle/devilrpg/debug/A2NavHarness.java` in commit `8f2b9138937b23ff853ec839a0db9f7559d0673e`.
- Fixture now allows a bounded 100 ticks after retry destruction for the mob to physically cross the target column. The original predicate `objective().equals(goal) && blockPosition().getX() > target.getX()` is unchanged; no pass is granted for merely having an objective or movement command.
- Added `BREAK_FIXTURE_RETRY_PROGRESS` samples every 10 ticks with position, objective, state, route node and executor diagnostics. Deadline failure includes final retry diagnostics.
- No production source, A2/C4 original world, cooldown, reach, LOS, collision, planner budget or acceptance threshold changed.
- Detailed report: `2026-11-16-PHASE19-BREAK-RETRY-OBSERVATION-WINDOW.md`.
- **PENDING HARNESS**: run static checks and a fresh disposable `break-runtime` on the exact current branch HEAD. Require both mission-resumption flags and exact 40-tick retry. A2 remains OPEN; no 12k before five consecutive strict 8/8 runs.

## Latest checkpoint — 2026-11-16 Phase19 BREAK retry fixture timing

- Harness run `a2n-20261116-172549` proves the production stand nudge, exact stand occupancy, both target-air results and exactly 40-tick retry. Scenario A mission resumption also passes.
- The only failure was fixture timing: it evaluated retry mission progress in the same tick that the second actuator destroyed the block, before the retry mob received a subsequent entity-movement tick.
- ChatGPT changed **only** `src/main/java/com/chipoodle/devilrpg/debug/A2NavHarness.java` in commit `4c3f446aeaa893db7ff7bcfc1c5f690f94e37332`. It defers the retry mission-progress predicate until `now > breakRetrySecondTick` and allows up to 20 ticks after destruction for the same objective + physical-progress predicate. If still false at the deadline, the fixture reports FAIL; it does not silently pass.
- Production `NavExecutor` and its nudge/BREAK behavior are unchanged.
- Detailed handoff: `docs/autonomous-village/2026-11-16-PHASE19-BREAK-RETRY-FIXTURE-TIMING.md`.
- **PENDING HARNESS**: validate the exact current branch HEAD with Gradle tests, strict lint, diff-check, diagnostics compile, then rerun disposable `break-runtime`. Require both mission-resumption flags, exact 40-tick retry and both targets air. No A2/C4 world use.


## Latest checkpoint — 2026-11-16 Phase19 BREAK stand approach correction

- Harness run `a2n-20261116-163239` is a behavioral FAIL: both BREAK plans and LOS hits are valid, but `moveTo` returns a completed one-node path for the adjacent stand and the mobs do not move.
- ChatGPT published commit `11be611d90c4d65772813550c2a365a524ee5147` in `NavExecutor.ejecutarBreakDesdePosicionDeAtaque`: when vanilla navigation is done before exact stand occupancy, it uses the existing bounded `NavAdjacentApproachPolicy` + `MoveControl.setWantedPosition` fallback, guarded by Manhattan distance, AABB fit and support.
- Exact physical occupancy remains mandatory before the actuator. No reach, LOS, protection, collision, cooldown, planner/recovery budget or acceptance threshold changed.
- New trace event: `BREAK_STAND_NUDGE`; existing `BREAK_STAND_APPROACH` remains.
- Detailed handoff: `docs/autonomous-village/2026-11-16-PHASE19-BREAK-STAND-APPROACH-FIX.md`.
- **PENDING HARNESS**: ChatGPT has not run Gradle/lint/diagnostics compile or Minecraft runtime on this SHA. Harness must validate exact current branch HEAD, then rerun disposable `break-runtime` and capture physical positions, both target-air checks, 40-tick retry and mission resumption.


## Latest checkpoint — 2026-11-16 Phase19 BREAK LOS correction

- Previous Harness run `a2n-20261116-160714` was **INVALIDO**: static checks passed, but both planner routes were empty and no mob/actuator started. This is not a BREAK actuator FAIL.
- Source correction is published through `9200ac31a350d9013b2abb8f39ce2b421fc9605a`: BREAK LOS now clips toward the target collision-shape center instead of the old projected endpoint below a thin pane. Planning-time `BREAK_LOS_DIAG` and bounded `BREAK_STAND_CANDIDATE` events were added; fixture trace initialization now occurs before planning.
- Detailed handoff: `docs/autonomous-village/2026-11-16-PHASE19-BREAK-LOS-CORRECTION.md`.
- This correction is **NOT YET VALIDATED** by Harness. Tests, diagnostics compilation and runtime must be executed on the exact current branch HEAD after fetching.
- Harness must preserve local artifacts, fast-forward safely, run Gradle tests, strict lint, diff-check and diagnostics compile, then execute a new disposable `break-runtime` world with `-Ddevilrpg.a2n.traceNav=true`.
- Required evidence: `BREAK_STAND_CANDIDATE`, `BREAK_LOS_DIAG` (legacy and new ray endpoints plus actual clip hit), route summaries, `nav-trace.log`, server logs, `fin.txt`, `estado.txt`, exact SHA.
- Harness must not change production source. If the new ray still fails, return the exact rejection branch and clip hit to ChatGPT.
- A2 remains OPEN (last known 5/8 strict, 6/8 perimeter); no 12k until five consecutive 8/8 strict runs. C4 save/reload remains OPEN.


Read first, in order:

1. `docs/autonomous-village/CHATGPT-COLLABORATION-POLICY.md`
2. `docs/autonomous-village/NEXT-SESSION.md` (this file)
3. `docs/autonomous-village/CHATGPT-HARNESS-WORKFLOW.md` (permanent division of responsibilities and handoff protocol)
4. `docs/autonomous-village/CONTINUAR.md`
5. `docs/CONTINUAR.md`
6. `docs/PENDIENTE.md`
7. `docs/autonomous-village/2026-11-15-PHASE2-FIXES.md`
8. `docs/autonomous-village/2026-11-15-A2-IDX8-C4-FORENSICS.md`
9. `docs/autonomous-village/2026-11-15-PHASE5-A2-VARIABILITY.md`
10. `docs/autonomous-village/2026-11-15-PHASE6-IDX4-IDX8-FORENSICS.md`
11. `docs/autonomous-village/2026-11-15-PHASE7-A2-9000-OBSERVATION.md`
12. `docs/autonomous-village/2026-11-15-PHASE8-IDX8-DECISION-TRACE.md`
13. `docs/autonomous-village/2026-11-15-PHASE9-IDX8-MOVEMENT-OWNERSHIP.md`
14. `docs/autonomous-village/2026-11-15-NAVEXECUTOR-SINGLE-AUTHORITY-MIGRATION.md`
15. `docs/autonomous-village/2026-11-16-PHASE10-A2-MIGRATION-REGRESSION.md`
16. `docs/autonomous-village/2026-11-16-PHASE11-STRATEGIC-NAVIGATION-FORENSICS.md`
17. `docs/autonomous-village/2026-11-16-PHASE12-NAVEXECUTOR-REFUSAL-FORENSICS.md`
18. `docs/autonomous-village/2026-11-16-PHASE13-RECOVERY-ACTUATOR-FORENSICS.md`
19. `docs/autonomous-village/2026-11-16-PHASE14-MOVEMENT-C4-FORENSICS.md`
20. `docs/autonomous-village/2026-11-16-PHASE15-NAVIGATION-ROOT-CAUSE-FORENSICS.md`
21. `docs/autonomous-village/2026-11-16-PHASE16-PATHFINDER-MOVEMENT-FORENSICS.md`
22. `docs/autonomous-village/2026-11-16-PHASE17-ISOLATED-EVALUATOR-IDX7-FORENSICS.md`
23. `docs/autonomous-village/2026-11-16-PHASE18-BREAK-EXECUTION-CONTRACT-AUDIT.md`
24. `docs/autonomous-village/2026-11-16-BREAK-EXECUTABLE-CONTRACT.md`
25. `docs/autonomous-village/2026-11-16-PHASE19-BREAK-RECOVERY-IMPLEMENTATION.md`
26. `docs/autonomous-village/2026-11-16-PHASE19-BREAK-VERIFICATION.md`
27. `docs/autonomous-village/2026-11-16-PHASE19-HARNESS-VALIDATION.md`
28. `docs/autonomous-village/2026-11-16-PHASE19-BREAK-GRAPH-STATE-FIX.md`
29. `docs/autonomous-village/2026-11-16-PHASE19-BREAK-RUNTIME-INVALID.md`
30. `docs/autonomous-village/2026-11-16-PHASE19-BREAK-LOS-RUNTIME-FAIL.md`
31. `docs/autonomous-village/2026-11-16-PHASE19-BREAK-STAND-NUDGE-RUNTIME.md`
32. `docs/autonomous-village/2026-11-16-PHASE19-BREAK-RETRY-RUNTIME-FAIL.md`
33. `docs/autonomous-village/2026-11-16-PHASE19-BREAK-RETRY-PASS.md`
34. `docs/autonomous-village/2026-11-16-PHASE19-EXCLUSION-BACKTRACK-RUNTIME.md`
35. `docs/autonomous-village/2026-11-16-PHASE19-BACKTRACK-EXACT-ARRIVAL-FAIL.md`
36. `docs/autonomous-village/2026-11-16-PHASE19-NAV-BACKTRACK-EXACT-ARRIVAL-RESULT.md`
37. `docs/autonomous-village/2026-11-16-A2-STRICT-GATE-RUN1-FAIL.md`
38. `docs/autonomous-village/2026-11-16-A2-RUN1-FORENSIC-EXTRACT.md`
39. `docs/autonomous-village/2026-11-16-A2-DROP-ROUTE-FEET-FIX.md`
40. `docs/autonomous-village/2026-11-16-A2-RUN1-DROP-FEET-FAIL.md`
41. `docs/autonomous-village/2026-11-17-A2-IDX4-DROP-TAKEOFF-RECOVERY-FIX.md`
42. `docs/autonomous-village/2026-11-17-A2-IDX4-NON-DESCENDING-DROP-FIX.md`
42. `docs/autonomous-village/2026-11-16-A2-RUN1-DROP-TAKEOFF-FAIL.md`
43. `docs/autonomous-village/2026-11-16-A2-RUN1-NON-DESCENDING-DROP-FAIL.md`
44. `docs/autonomous-village/2026-11-16-A2-RUN1-FIRST-NODE-DROP-FAIL.md`
45. `docs/autonomous-village/2026-11-16-A2-RUN1-VERTICAL-TAKEOFF-FAIL.md`
46. `docs/autonomous-village/2026-11-17-A2-RUN1-STEP-UP-ASSIST-FAIL.md`
47. `docs/autonomous-village/2026-11-17-A2-RUN1-GROUNDED-RECOVERY-FAIL.md`
48. `docs/autonomous-village/2026-11-17-A2-RUN1-LANDING-ANCHOR-FAIL.md`
49. `docs/autonomous-village/2026-11-17-IDX4-GROUNDED-INVALID-FORENSICS.md`
50. `docs/autonomous-village/2026-11-17-A2-RUN1-AABB-SOURCE-RECOVERY-RESULT.md`
51. `docs/autonomous-village/2026-11-17-A2-RUN1-AABB-SOURCE-RECOVERY-PHASE21.md`
52. `docs/autonomous-village/2026-11-17-A2-RUN1-EXACT-BOUNDED-RECOVERY-PHASE22.md`
53. `docs/autonomous-village/2026-11-17-A2-RUN1-PHASE23-EDGE-DIAG-RESULT.md`
54. `docs/autonomous-village/2026-11-17-A2-RUN1-UNSUPPORTED-TAKEOFF-PHASE24.md`
55. `docs/autonomous-village/2026-11-17-A2-RUN1-GROUNDED-INVALID-DROP-SOURCE-PHASE25.md`
56. `docs/autonomous-village/2026-11-17-A2-RUN1-GROUNDED-SOURCE-SELECTION-PHASE26.md`
57. `docs/autonomous-village/2026-11-17-A2-PHASE27-SNAP-FORENSIC-DIAGNOSIS.md`
58. `docs/autonomous-village/2026-11-17-A2-PHASE28-DROP-SNAP-OWNERSHIP-DIAGNOSIS.md`
59. `docs/autonomous-village/2026-11-17-A2-PHASE29-DROP-SNAP-EXCLUSIVE-OWNERSHIP-FIX.md`
60. `docs/autonomous-village/2026-11-17-A2-PHASE30-IDX4-POST-SNAP-REPLAN-DIAGNOSIS.md`
61. `docs/autonomous-village/2026-11-17-A2-PHASE31-POST-SNAP-STATE-RESET-FIX.md`
62. `docs/autonomous-village/2026-11-18-A2-PHASE32-NON-ENTRANT-FORENSIC-DIAGNOSIS.md`
63. `docs/autonomous-village/2026-11-18-A2-PHASE33-SNAP-CHURN-GEOMETRY-DIAGNOSIS.md`
64. `docs/autonomous-village/2026-11-18-A2-PHASE34-DROP-APPROACH-SUPPORTED-PATH-FIX.md`
65. `docs/autonomous-village/2026-11-18-A2-PHASE35-ADJACENT-DROP-NON-DESCENT-DIAGNOSIS.md`
66. `docs/autonomous-village/2026-11-18-A2-PHASE36-ADJACENT-DROP-TAKEOFF-FIX.md`
67. `docs/autonomous-village/2026-11-18-A2-PHASE37-DROP-DESCENT-PHYSICAL-FORENSICS.md`
68. `docs/autonomous-village/2026-11-18-A2-PHASE38-DROP-DESCENT-PHYSICAL-EXECUTION-TRACE.md`
69. `docs/autonomous-village/2026-11-18-A2-PHASE39-PLANNER-WATCHDOG-AND-DROP-FIXTURE-DIAGNOSIS.md`
70. `docs/autonomous-village/2026-11-18-A2-PHASE40-DETERMINISTIC-DROP-FIXTURE-TRACE.md`
71. `docs/autonomous-village/2026-11-18-A2-PHASE41-DROP-EDGE-STEP-FIX.md`
72. `docs/autonomous-village/2026-11-18-A2-PHASE42-REMAINING-NON-ENTRANTS-FORENSIC.md`
73. `docs/autonomous-village/2026-11-18-A2-PHASE43-NON-ADJACENT-DROP-FORENSICS.md`
74. `docs/autonomous-village/2026-11-18-A2-PHASE44-VALIDATION.md`
75. `docs/autonomous-village/2026-11-18-A2-PHASE45-IDX3-IDX4-FORENSICS.md`
76. `docs/autonomous-village/2026-11-18-A2-PHASE46-VALIDATION.md`
77. `docs/autonomous-village/2026-11-18-A2-PHASE47-FORENSICS.md`
78. `docs/autonomous-village/2026-11-18-A2-PHASE48A-IDX3-CAUSAL-RECONCILIATION.md`
79. `docs/autonomous-village/2026-11-18-A2-PHASE49A-STAIR-DIAGNOSTIC-INSTRUMENTATION.md`
80. `docs/autonomous-village/2026-11-18-A2-PHASE50-VALIDATION.md`
81. `docs/autonomous-village/2026-11-18-A2-PHASE51-VALIDATION.md`
82. `docs/autonomous-village/2026-11-18-A2-PHASE52-STAIR-IDX5-FORENSICS.md`
83. `docs/autonomous-village/2026-11-18-A2-PHASE53-VALIDATION.md`
84. `docs/autonomous-village/2026-11-18-A2-PHASE54A-FIXTURE-FORENSICS.md`
85. `docs/autonomous-village/2026-11-19-A2-PHASE54B-FIXTURE-IMPLEMENTATION.md`
86. `docs/autonomous-village/2026-11-19-A2-PHASE54B-R1-RUNTIME-VALIDATION.md`
87. `docs/autonomous-village/2026-11-19-A2-PHASE55-STATIC-VALIDATION.md`
88. `docs/autonomous-village/2026-11-19-A2-PHASE55-R2-STATIC-VALIDATION.md`
89. `docs/autonomous-village/2026-11-19-A2-PHASE55-R2-RUNTIME-BLOCKED.md`
90. `docs/autonomous-village/2026-11-19-A2-PHASE55-POPULATION-NBT-FORENSICS.md`
91. `docs/autonomous-village/2026-11-19-A2-PHASE55-RUNTIME-VALIDATION.md`
92. `docs/autonomous-village/2026-11-19-A2-PHASE55-ASTAR-FIRST-EXPANSION-FORENSICS.md`
93. `docs/autonomous-village/2026-11-19-A2-PHASE55-GLOBAL-ASTAR-DIAGNOSTICS-STATIC-VALIDATION.md`
94. `docs/autonomous-village/2026-11-19-A2-PHASE55-GLOBAL-ASTAR-DIAGNOSTIC-RUNTIME.md`
95. `docs/autonomous-village/2026-11-19-A2-PHASE55-POPULATION-QUERY-STATIC-VALIDATION.md`
96. `docs/autonomous-village/2026-11-19-A2-PHASE55-POPULATION-QUERY-BUILD-ROUTING-STATIC-VALIDATION.md`
97. `docs/autonomous-village/2026-11-19-A2-PHASE55-NAVFEATURE-REMOVAL-STATIC-VALIDATION.md`
98. `docs/autonomous-village/2026-11-19-A2-DIAGNOSTIC-RUN1-POST-PHASE55.md`
99. `docs/autonomous-village/2026-11-19-A2-POST-PHASE55-FORENSIC-ANALYSIS.md`
100. `docs/autonomous-village/2026-11-19-HARNESS-P0-STATIC-AND-TRAVERSAL-FORENSICS.md`
101. `docs/autonomous-village/2026-11-19-P0-ENUM-FIX-STATIC-VALIDATION.md`
102. `docs/autonomous-village/2026-11-19-TRAVERSAL-COORDINATION-DESIGN.md`
103. `docs/autonomous-village/2026-11-19-TRAVERSAL-COORDINATION-STATIC-VALIDATION.md`
104. `docs/autonomous-village/2026-11-19-TRAVERSAL-LEASE-BYPASS-STATIC-VALIDATION.md`
105. `docs/autonomous-village/2026-11-20-A2-NEXT-DEFECT-FORENSICS.md`
106. `docs/autonomous-village/2026-11-20-D1-IDX7-BUILD-ASCENT-FORENSICS.md`
107. `docs/autonomous-village/2026-11-20-D2-SINGLE-BLOCK-ASCENT-FORENSICS.md`
108. `docs/autonomous-village/2026-11-20-D3-SINGLE-BLOCK-BUILD-STATIC-VALIDATION.md`
109. `docs/autonomous-village/2026-11-20-A2-WORLD-INTEGRITY-FORENSICS.md`
110. `docs/autonomous-village/2026-11-20-A2-DISPOSABLE-WORLD-PREP.md`
111. `docs/autonomous-village/2026-11-20-A2-RUN1-D3-RUNTIME-REPORT.md`
112. `docs/autonomous-village/2026-11-20-A2-GLOBAL-ASTAR-LOG-FORENSICS.md`
113. `docs/autonomous-village/2026-11-20-A2-GLOBAL-ASTAR-CORRECTION-STATIC-VALIDATION.md`
114. `docs/autonomous-village/2026-11-20-A2-GLOBAL-ASTAR-CORRECTION-STATIC-VALIDATION-R2.md`
115. `docs/autonomous-village/2026-11-20-A2-VERTICAL-ONLY-FINAL-RUN-REPORT.md`
116. `docs/autonomous-village/2026-11-20-A2-BREAK-BUILD-RUNTIME-FORENSIC-EXTRACT.md`
117. `docs/autonomous-village/2026-11-21-A2-BUILD-PROVENANCE-BREAK-DIAGNOSTICS-STATIC-VALIDATION.md`
118. `docs/autonomous-village/2026-11-21-A2-DROP-FIX-ITERATION.md`
119. `docs/autonomous-village/2026-11-21-A2-RECOVERY-BREACH-MOVE-ITERATION.md`
80. `docs/autonomous-village/2026-11-18-A2-PHASE49A-VALIDATION.md`
81. `docs/autonomous-village/2026-11-18-A2-PHASE50-MONOTONIC-STAIR-ANCHOR.md`

## Permanent collaboration workflow

- Read `docs/autonomous-village/CHATGPT-HARNESS-WORKFLOW.md` and follow it in every session.
- ChatGPT owns production source implementation and publishes changes directly to GitHub on this branch.
- Harness owns local synchronization, preservation, builds/tests/lints, automated runtime measurement, logs and validation reports. Harness must not independently implement production source fixes.
- Harness returns evidence and reproducible failures; ChatGPT reviews and makes source corrections; Harness validates the exact new SHA.
- Neither agent may claim work performed by the other or mark a commit validated without evidence for that exact SHA.
- This is a permanent workflow rule, not a one-session instruction. It must survive new conversations and agent handoffs.

## Verified baseline

- Branch: `feature/autonomous-village`
- Code implementation awaiting validation: `6d5dc7b1d1f72a366d4f6b05626147b9e932481c` (BREAK graph-state, executor and deterministic fixture correction). Documentation commits may advance branch HEAD after this code checkpoint; always fetch and verify actual local/remote HEAD before acting.
- Latest technical report: `2026-11-18-A2-PHASE44-VALIDATION.md`. Approach ownership validated; A2 3/8 strict; remaining IDX3 BUILD, IDX4 near-miss72.12, IDX2/7/8 independent.
- Remote branch must be fetched and checked before any change.
- Working tree may contain local-only untracked artifacts.

## Completed investigations

- IDX8 elevated-BREAK support-floor selection: fixed by `NavBreakTargetPolicy` and regression test.
- C4 displaced workers beyond 72 blocks: fixed by `VillagerReturnPolicy`/`VillagerReturnGoal`.
- Existing diagnostic worlds remain authoritative; do not recreate.

## Current status

- `NavExecutor` is the single movement authority; legacy planner, `NavFeature` and
  `MovementOwnership` are deleted. Harness passed Gradle tests, strict lint and diff-check on the
  prior tree at `ecdfaff6`; those results do **not** validate the later code checkpoint `6d5dc7b1d1f72a366d4f6b05626147b9e932481c`.
  The new graph-state/route/executor changes and two new tests are **not yet compiled or executed by Harness**.
- Phase10 melee-only tactical ownership (`002550`): 6/8 perimeter, 5/8 strict; C4 12/12.
- Phase11 diagnostic `005739`: same 6/8 perimeter, 5/8 strict. Combat inactive for idx5/7/8.
- Phase12 diagnostic `022656`: 5/8 perimeter, 4/8 strict; C4 12/12.
- Phase13 diagnostic `024118`: 6/8 perimeter, 4/8 strict; C4 **FAIL** (`jobSite=[0,0,0]`, 12/12 resumed).
- Phase14 diagnostic `035911`: 6/8 perimeter, 4/8 strict; C4 **PASS** `[1,0,0]`.
- Phase15 diagnostic `092800`: 5/8 perimeter, 4/8 strict; C4 **PASS** `[2,0,0]`.
- Phase16 diagnostic `094243`: 5/8 perimeter, 3/8 strict; C4 **PASS** `[1,0,0]`.
- Phase17: `createPath` cached-return flaw confirmed; Phase16 retry not independent.
- Phase18 audit: BREAK route node was the solid block; no attack-position search.
- BREAK contract implementation: `Node.pos`=stand, `Node.actionPos`=solid target; planner validates
  adjacent stand AABB/support/LOS/reach/protection; executor navigates to stand, delegates action,
  requires verified air before advancing.
- Focused `a2n-20261116-101718`, merged `103331`, post-integration `110238`: 6/8 perimeter,
  5/8 strict, C4 PASS `[1,0,0]`/`[3,0,0]`.
- Phase19 verification: BREAK executor integration fixed to use `actionTarget()`. Follow-up review
  constrains planner-selected attack stands to one legal move from the predecessor and delegates
  reach/LOS/protection checks to `NavBreakActionPolicy.executable`.
- Harness validation at `d440b3e7` (implementation `6d5dc7b1`): Gradle tests, strict lint, diff-check and
  diagnostic compile PASS. New tests executed: graph-state 7, route-policy 11, attack-position 7.
- `break-runtime` `a2n-20261116-160714` was INVALIDO (empty plans); LOS correction `9200ac31` at
  `76d3466c` fixed planning: `BREAK_LOS_DIAG` accepts both panes with `hitBlock` equal to `actionPos`.
- `break-runtime` `a2n-20261116-174648` on HEAD `8b389672`: **COMPLETO**. Both panes air, retry
  calls=2, delta=40, success/retry missions resumed (retry at x=738, ticksAfterBreak=23).
- `break-edge-excluded` `a2n-20261116-180242`: **COMPLETO**. Two unverified actions, delta=40,
  one `BREAK_EDGE_EXCLUDED` (fingerprint, exclusions=1), alternative BREAK selected, both blocks
  solid, route index unchanged, strategic mission preserved, 0 unexpected calls.
- `nav-backtrack` `a2n-20261116-185434` on HEAD `fb93541e`: **COMPLETO**. Selection target
  `937,184,608`, adjacent nudge from 938, exact physical return, mission resumed at tick 103,
  history 6, max step 0.114, one backtrack, no teleport.
- A2 gate Run 1 `a2n-20261116-190555` on `2d47d628`: **FAIL**. 8/8 spawn, 5/8 perimeter, 5/8 strict
  (idx1 t558, idx2 t667, idx3 t1062, idx5 t702, idx6 t649); idx4/7/8 outside and alive.
  - idx8 NAV_FAIL t3411: no valid local waypoint after world change; global budget 8000/8000; 4/4 rearms.
  - idx7 NAV_FAIL t4209: no-progress budget 10/10 toward `703,68,457` BUILD.
  - idx4 NAV_FAIL t5147: replans 8/8, 24 DROP rejections (landing above), global budget exhausted.
- Consecutive 8/8 runs: **0**. C4 in Run 1: PASS `[2,0,0]`.
- Forensic extract confirms the IDX4/IDX8 shared code defect: route nodes use `Step.state`, not
  `Step.feet`, so DROP nodes carry the search-state Y instead of the landing Y. IDX4 runtime shows
  node y78 vs mob y77 (`dy=1` rejection); IDX8 has 81 front waypoints but 0/64 valid local.
- IDX7 BUDGET shows `exp=901/900` for 10/10 attempts with `BUILD-FALLIDO`; no final full plan summary
  was persisted.
- DROP-feet fix at `5604ed03`: static PASS (NavRouteNodePolicyTest 6). A2 Run 1 `a2n-20261116-195651`
  still **FAIL** at 5/8 strict. IDX4 remains below its landing `597,78,499` at `596,77,498`
  (24 DROP rejections, dy=1, replans 8/8); the earlier state-vs-feet node hypothesis is not the cause
  of this runtime instance. IDX7 `exp=901/900` 10/10; IDX8 no local waypoint/global budget.
- Take-off fix at `6d294c1d`: static PASS (NAV_DROP_TAKEOFF_POLICY_TEST 5). A2 Run 1
  `a2n-20261116-201759`: **4/8 strict**, 5/8 perimeter. IDX4 route starts with DROP at index0, so
  `origenPlan` is null and `ACERCARSE-ORIGEN-DROP` is never emitted; mob remains at y77 below y78.
- Follow-up source diagnosis: local planner starts from `mob.blockPosition()` and initializes
  `piesDesde=start.getY()`; planner accepted a DROP whose landing feet were above the source feet
  because only the generic vertical bound was enforced. Using `plannedFrom` cannot repair this
  impossible edge. Fix at pending SHA: require `landingFeet < takeoffFeet` for every DROP edge;
  reject non-descending DROP before A* accepts it. New test grows to 9 assertions.
- IDX2 this run reached perimeter but not strict; idx7/idx8 unchanged independent failures.
- Non-descending fix at `2214366a`: static PASS (NAV_DROP_TAKEOFF 9, NAV_ROUTE_NODE 6). A2 Run 1
  `a2n-20261116-211919`: **FAIL**, 5/8 strict, 5/8 perimeter. IDX4 still `597,78,499` vs mob
  `596,77,498`, `dy=1`, 24 failures; no `DROP_REJECTED_NON_DESCENDING` or recovery command for its UUID.
  IDX7/IDX8 unchanged; C4 PASS `[1,0,0]`.
- First-node origin diagnosis: route acceptance stores `plannedFrom = mob.blockPosition()`; the
  DROP executor previously resolved an origin only from a previous node when `routeIndex > 0`.
  Thus a first-node DROP had no recoverable origin. Absence of `DROP_REJECTED_NON_DESCENDING` was
  inconclusive because that trace is opt-in.
- First-node origin fix at pending HEAD: `NavDropTransitionPolicy.origenPlanificado` uses recorded
  routeStart for index 0 and immediate predecessor for later indices; `NavExecutor` now issues the
  existing exact-Y `ACERCARSE-ORIGEN-DROP` recovery when required. Added `DROP_TAKEOFF_ORIGIN_RESOLVED`
  trace and 3 assertions (DROP policy total 12). Commits `b763b112`, `2518d703`, `183b7667`.
- First-node origin fix at `b73ca2de`: static PASS (NAV_DROP_TAKEOFF 12). A2 Run 1
  `a2n-20261116-213836`: **FAIL**, 5/8 strict, 5/8 perimeter, C4 PASS `[1,0,0]`.
  IDX4 resolves origin `589,96,522` from live `589,93,522` and emits `ACERCARSE-ORIGEN-DROP`, but
  PathNavigation returns a one-node path and the mob does not ascend; physical origin not reached.
- IDX7 BUILD/900-expansion and IDX8 local-waypoint failures unchanged.
- Vertical recovery fix at `38a4b52e`: static PASS (NAV_DROP_TAKEOFF 16). A2 Run 1
  `a2n-20261116-225217`: **FAIL**, 5/8 strict, 5/8 perimeter, C4 PASS `[1,0,0]`.
  IDX4 accepted a len1 STEP_UP recovery route but stalled at `588,104,521` vs takeoff `588,107,520`;
  later plans returned empty with `exp=901/900`; final construction exhausted.
- STEP_UP assist at `7aed2c68`: static PASS (NAV_DROP_TAKEOFF 25). A2 Run 1
  `a2n-20261117-001624`: **FAIL**, 4/8 strict, 5/8 perimeter, C4 PASS `[1,0,0]`.
  IDX4 emitted `DROP_TAKEOFF_STEP_UP_ASSIST` (`567,121 -> 566,122`, support/fit true) but stalled at
  `567,119`, never reached the waypoint. No `901/900` entries observed.
- Grounded fix at `b3064596`: static PASS (NAV_DROP_TAKEOFF 32). A2 Run 1
  `a2n-20261117-020800`: **FAIL**, 5/8 strict, 5/8 perimeter, **C4 FAIL [0,0,0]**.
  IDX4 emits only `DROP_TAKEOFF_RECOVERY_WAIT_GROUNDED` (onGround=false); no accepted route, no assist,
  no recovery reached; final `construccion agotada sin completar el descenso`. No `901/900` entries.
- Landing anchor at `f32c32a4` (production559ba6e3): static PASS (NAV_DROP_TAKEOFF 36).
  A2 Run 1 `a2n-20261117-101833`: **FAIL**, 5/8 strict, 5/8 perimeter, C4 PASS `[1,0,0]`.
  IDX4 is grounded at `593.51,87,507.96` but `feetValid=false support=false fits=true`,
  `landingAnchor=NONE`; no route accepted/assist/reached; final replans 8/8.
- IDX4 forensic root cause (run `a2n-20261117-101833`): exact `593.51,87,507.96`, AABB
  z≈507.66–508.26, supported by cobblestone `(593,86,508)`, but `blockPosition`/support check uses
  `(593,87,507)` below `(593,86,507)` air. `onGround=true`, `feetValid=false`, `support=false`,
  `fits=true` (narrow cellBox). Valid shortest candidate is `(593,87,508)` via horizontal WALK.
- No production change and no new A2 run in the forensic session. A2 OPEN; full 12k not run.
- Canonical focused gate: five consecutive **8/8 strict `DENTRO-PARED`** runs.

## Immediate work

1. ChatGPT to fix the `reopensThisSlice` lambda capture without changing the metric semantics.
2. Re-run compileJava/test/lint/diff on the new HEAD.
3. No runtime/Run2/Full12K until static PASS and new authorization. A2 OPEN; C4 not revalidated.

## Worlds (preserve; do not overwrite)

- `C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-A2-IDX8-Diagnostico`
- `D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-A2-IDX8-Diagnostico`
- `C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-C4-WorkerResume-Diagnostico`
- `D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-C4-WorkerResume-Diagnostico`

Hashes and sizes are in `2026-11-15-PHASE2-FIXES.md`.

## Local-only artifacts

`.a2-backups/`, `src/foodTest/`, `tools/a2n/A2NavHarness.java.partial`, launcher helper scripts, `tools/a5-closure/`. Do not commit indiscriminately.


## 2026-11-16 — Phase19 backtrack exact-arrival correction (PENDING HARNESS)

- Harness evidence: `BREAK_EDGE_EXCLUDED` PASS; `NAV_BACKTRACK` FAIL because generic `ARRIVE_DISTANCE_SQR` cleared the one-shot tactical target at 938 before exact cell 937.
- ChatGPT correction source checkpoint: `889072d235a44d0efc2de7ad955333b39b1d8b03`. Added exact-cell gate for pending recovery backtrack in `NavExecutor`, pure policy and three regression assertions in `NavArrivalPolicyTest`, plus bounded suppression trace.
- No tests/compile/lint/runtime executed by ChatGPT. **PENDING HARNESS** on exact live HEAD.
- Run static validation and only disposable `nav-backtrack`; require physical return to exact historical cell, one selection, mission resumed, strategic mission preserved, bounded history, no teleport.
- `BREAK_EDGE_EXCLUDED` remains validated; A2 OPEN; no full 12k until five consecutive strict 8/8; C4 save/reload OPEN.


## 2026-11-16 — Phase19 backtrack route-cursor + nudge correction (PENDING HARNESS)

- Harness run `a2n-20261116-182234` passed static validation and exact objective-arrival guard, but runtime still failed: the generic WALK node arrival gate consumed `937` at adjacent `938`, advancing route cursor to idx=2 before the nudge helper could execute.
- ChatGPT correction checkpoint: `cc817c6314d2b2055dfbba578c5565e3bff9fedf`. `nodoAlcanzado()` now requires exact occupancy for the active backtrack route node; this keeps the WALK node active and lets the existing bounded `aplicarAcercamientoAdyacente()` + `NavAdjacentApproachPolicy` + `MoveControl.setWantedPosition` execute when vanilla navigation is done. Added `BACKTRACK_NODE_OCCUPANCY_REQUIRED`, `BACKTRACK_ADJACENT_NUDGE`, and three pure assertions.
- No static or runtime validation performed by ChatGPT. **PENDING HARNESS** on exact live HEAD.
- Run Gradle tests, strict lint, diff-check, diagnostics compile, then a new disposable `nav-backtrack` only. Require exact physical return before clearing tactical target, mission resumed, one selection, history <=16 and no teleport.
- A2 OPEN; no full 12k before five consecutive strict 8/8; C4 save/reload OPEN.


## 2026-11-17 — IDX4 first-node DROP origin fix

- Commits: `b763b11245f0fdf1864c49cfd0100e451f40abd8`,
  `2518d70396d6c500efb241f522c6f53eef6a022c`,
  `183b76675761a69f2ca2b501dc02311cba412572`,
  docs `bc38d43a2085fef4cd91fad90afb91c95adacda2`.
- Harness validation pending on exact current HEAD. Expected DROP policy test: 12 assertions.
- Report: `2026-11-17-A2-IDX4-FIRST-NODE-DROP-ORIGIN-FIX.md`.


## 2026-11-17 — IDX4 executable vertical take-off recovery

- Harness run `a2n-20261116-213836` established that origin resolution worked but vanilla
  same-column vertical `moveTo` returned a one-node path at live y93 instead of ascending to y96.
- Published bounded auxiliary navigation via existing `NavPlanner.plan`. Only complete routes
  to the exact take-off using WALK/STEP_UP/JUMP_UP are accepted; no DROP/BREAK/BRIDGE/BUILD.
- Main DROP route/cursor is suspended and restored only after exact-Y physical take-off with
  support and fit. Recovery waypoints require exact occupancy; JUMP_UP uses the existing actuator.
  Planning failure, incomplete route, or stall fails closed and replans from the live position.
- No safety guards or budgets were relaxed. Test expected: `NAV_DROP_TAKEOFF_POLICY_TEST` 16
  assertions; `NAV_ROUTE_NODE_POLICY_TEST` 6.
- Commits: `df3b9e46`, `ebeda4de`, `2cc3aa50`, `d79189aa`, `d7056617`, `800329f6`.
- Report: `2026-11-17-A2-IDX4-VERTICAL-TAKEOFF-RECOVERY-FIX.md`.
- PENDING HARNESS; ChatGPT did not execute static or runtime checks.


## Latest checkpoint — 2026-11-17 A2 IDX4 STEP_UP execution correction (PENDING HARNESS)

- Run `a2n-20261116-225217` on `38a4b52e`: static PASS; A2 Run 1 FAIL 5/8 strict. IDX4 accepted one STEP_UP but did not climb; later plans reported 901/900.
- ChatGPT added a guarded natural STEP_UP assist for DROP recovery: only adjacent one-block climbs with blocked lower step, headroom, destination fit and real support; uses MoveControl + JumpControl, never direct velocity or teleport. Exact physical occupancy remains required.
- Corrected NavPlanner's expansion guard to check before incrementing; 900 is now the maximum, never 901. Budget itself unchanged.
- Added pure policies and extended `NavDropTakeoffPolicyTest` to 25 assertions; expected route-node test remains 6.
- Production code checkpoint before handoff documentation: `491b7d0170501e9d86433a52d2703395a4029b06`.
- Report: `docs/autonomous-village/2026-11-17-A2-IDX4-STEP-UP-EXECUTION-FIX.md`.
- PENDING HARNESS on exact current HEAD. Static checks first, then A2 Run 1 only. IDX7/IDX8 untouched. No Run 2 unless 8/8 strict; no 12k until five consecutive 8/8 strict runs. C4 save/reload OPEN.


## 2026-11-17 — IDX4 bounded airborne landing anchor (PENDING HARNESS)

- Harness on `b3064596` / production `190bb657`: static PASS, A2 Run 1 FAIL 5/8 strict.
- IDX4 emitted only WAIT_GROUNDED; one sample had feetValid=true, support=true, fits=true, but onGround=false.
- Published bounded horizontal landing guidance through MoveControl, only over the current valid/supported/fitting feet cell. No A* or jump is allowed until a later tick reports onGround=true.
- Added four pure assertions; expected DROP policy test total 36. WAIT trace includes velocity and landingAnchor.
- Report: `2026-11-17-A2-IDX4-LANDING-ANCHOR-FIX.md`. PENDING HARNESS. A2 Run 1 only after static PASS; no Run 2 unless 8/8 strict; no 12k until five consecutive 8/8 strict.
- C4 `assignmentsClean=false` remains independent and unchanged; IDX7/IDX8 untouched.


## 2026-11-19 — Revisión del orden de decisión de traversal (diagnóstico; sin cambios de producción)

El usuario propuso evaluar el orden: avanzar → romper si bloqueado → excavar si la ruta necesita bajar → escalar si necesita subir. Se revisó el código actual en la rama `feature/autonomous-village`:

- `AggressiveZombieEntity.java`, blob `00fb9c6c6131de715ebb4afe66d6d0585e8910dc`.
- `NavExecutor.java`, blob `2055abca5ad2b1dc90e168394406d3be9b058668`.

Hallazgos confirmados:

1. `NavExecutor` es la autoridad única de navegación normal y ejecuta la ruta validada.
2. Hay dos goals pasivos e independientes para modificar terreno cuando el zombie se atasca: `BreakBlockGoal` y `TraverseGoal`. No reclaman MOVE/LOOK; por ello sus decisiones no forman una única secuencia coordinada.
3. `BreakBlockGoal.tick()` usa su propio detector de falta de movimiento. En marcha sin target intenta `breakBrechaAhead` limitado al anillo real; con target rompe obstáculo o escalón según altura del target.
4. `TraverseGoal.tick()` también detecta falta de movimiento. Actualmente:
   - si target/centro está más de 1 bloque abajo, intenta `cavarHaciaAbajo` primero;
   - si está más de 1 bloque arriba, intenta `apilarBloqueParaSubir` primero;
   - si no tuvo éxito, prueba `puentearHacia` y `apilarBloqueParaSubir` en orden aleatorio;
   - si todavía no hizo nada, vuelve a intentar `cavarHaciaAbajo`, sin exigir que la ruta haya seleccionado una transición DROP válida.
5. `TraverseGoal.objetivoDeLaMarcha()` toma el target de combate vivo o, si no existe, el centro de aldea. Es un destino estratégico/táctico, no necesariamente el siguiente nodo de la ruta. Por eso su coordenada Y global no es evidencia suficiente para excavar/escalar localmente.
6. El comentario histórico en el código documenta que la selección por altura ya provocó excavación dominante y que los intentos con orden fijo también dieron resultados incorrectos. No se debe resolver con un simple intercambio ciego de llamadas.

Dirección de diseño para la corrección (pendiente de implementación):

- Mantener `NavExecutor` como fuente de verdad: inspeccionar la transición/nodo activo y el motivo de no progreso antes de alterar terreno.
- Si la ruta tiene siguiente paso WALK/JUMP/STEP_UP/DROP y es físicamente válido, dejar avanzar; no ejecutar herramientas de terreno por una diferencia de Y del destino final.
- Si el paso está bloqueado, preferir una brecha ya abierta y transitable; si el obstáculo de la ruta es rompible y está dentro del contrato de asedio, romperlo antes de crear túneles/hoyos alternativos.
- Construir escalón solo ante una transición de subida local que la ruta/geométrica valide y con soporte/headroom.
- Excavar solo ante una transición DROP real y validada, nunca como fallback genérico por target inferior ni como último intento incondicional.
- Coordinar o retirar la duplicación de decisiones entre `BreakBlockGoal` y `TraverseGoal`; evitar que dos detectores de atasco emitan acciones incompatibles sobre el mismo zombie.
- Añadir políticas puras y tests que cubran prioridad WALK/avance, BREAK de obstáculo, DROP validado, STEP_UP/BUILD validado, target inferior sin DROP (no cavar), y ausencia de transición de ruta (no acción vertical).
- Preservar presupuestos, protección del recinto, restricciones de block entities salvo la futura excepción atómica específica del portón, y todas las invariantes A2/C4.

Estado: diagnóstico source-level solamente. No se modificó producción, no se ejecutó build/test/lint/runtime. Este análisis no sustituye el checkpoint inmediato de Harness STATIC ONLY para P0 indicado arriba. No autoriza runtime. Tras cerrar ese checkpoint, implementar el rediseño coordinado y someterlo a STATIC ONLY antes de solicitar autorización de runtime.


## 2026-11-21 — BUILD/BREAK static PASS + DROP source audit

- Harness static validation PASS on exact production HEAD `7bd26569da11e0a61de0af86124bf6fc6666e7fe`; documentation HEAD advanced to `fd5940ba579ae3089e072e2870811461e4073c89`. Compile, test, policyTest, strict lint and diff-check PASS; 57 named tests; no runtime/world/production/test modifications. Report: `2026-11-21-A2-BUILD-PROVENANCE-BREAK-DIAGNOSTICS-STATIC-VALIDATION.md`.
- ChatGPT source-only DROP audit: `NavExecutor.tickDropEdgeStep` aborts after 21 consecutive ticks without >0.01 reduction in horizontal distance to landing center, provided complete AABB support has not cleared. This does not prove the mob was stationary. Abort telemetry omits distance sequence, no-progress count, velocity, grounded and AABB. No behavior fix is justified yet.
- Report: `2026-11-21-A2-DROP-EDGE-STEP-SOURCE-AUDIT.md` (commit `b9e3f31f9ab99f53c77af988e84af116f42ebb1f`).
- Next: Harness read-only correlate all 18 IDX6 aborts with selected edge, commands, progress, route-node changes and route-guard events in existing logs; verify IDX6 UUID against that run's spawn records. Separately map killer UUID `cc4b9c97-d8f3-40e3-aee1-cf233feab912` against spawn records from the exact run where `minecraft:mob_attack` was captured. No runtime, no production/tests edits.
- Runtime authorization remains consumed. No Run2/retry, C4 standalone or Full12K. A2 OPEN; last runtime 1/8 strict, 2/8 perimeter. C4 integrated PASS only.
