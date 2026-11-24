# Cierre técnico de Autonomous Village — campaña 23/24-nov-2026

**Estado final: COMPLETE para los objetivos ejecutables de esta campaña.**
Rama `feature/autonomous-village`. Código probado: **`1016a7cf`** (el commit de documentación posterior es docs-only).
No se hizo push, merge, checkout de otra rama, `reset --hard` ni `clean`; no se borraron mundos, logs ni evidencia.

Este documento sustituye la narrativa de “auditoría con lista de problemas”: aquí están los defectos encontrados,
las correcciones, las pruebas y la evidencia runtime. Las limitaciones que siguen abiertas se declaran al final y no
se disfrazan de PASS.

---

## 1 · Contrato y resultado

| Área | Resultado | Evidencia principal |
|---|---|---|
| C4 · 12 oficios, 4 fundadores, guardias, JOB_SITE, save/reload | **PASS 3/3 fases** | `build/c4n/c4n-20261124-0250/` |
| Cobertura de las 11 estaciones (NITWIT sin puesto) | **11/11**, 0 duplicados, 0 huérfanos | `phase-prepare/reload1/reload2.json` |
| Persistencia real `ServerLevel` + `PoiManager`, tickets y dos recargas | **POI_PASS 4/4 fases** | `build/av/poi-final-20261124-0305/` |
| Navegación runtime STEP_UP/DROP/BREAK/BUILD/túnel/terreno/A* | **NAV_PASS 21/21** | `build/av/nav-final-20261124-0255/` |
| Rendimiento real con `NavPlannerMetrics` | Medido; sin extrapolar microoptimizaciones | `build/av/perf-final/` |
| Guardias / C4 aceptado | Conservado; C4 final 12/12 y 4/4, máscara `0b1111`, 4/4 equipados | `c4n-20261124-0250` |
| Gates estáticos y tests | Verdes (ver §9) | logs en `build/av/` |

**No se forzó 12/12 puestos.** El contrato es **11/11 estaciones** (hay 11 puestos de trabajo) y el NITWIT queda
documentado sin estación. El contador de profesionales sigue siendo 12/12.

---

## 2 · JOB_SITE: de 3/12 a 11/11, con tabla individual

### 2.1 Tabla de los 12 aldeanos

La columna “resultado” es la del cierre de C4 (`c4n-20261124-0250`, prepare/reload1/reload2). Los 11 puestos son
reclamados por su titular; el NITWIT no tiene ni debe tener estación.

| # | Profesión | Puesto de trabajo | Resultado |
|---:|---|---|---|
| 1 | farmer | composter | JOB_SITE estable, ticket cogido |
| 2 | farmer | composter | JOB_SITE estable, ticket cogido |
| 3 | farmer | composter | JOB_SITE estable, ticket cogido |
| 4 | fisherman | barrel | JOB_SITE estable, ticket cogido |
| 5 | shepherd | loom | JOB_SITE estable, ticket cogido |
| 6 | fletcher | fletching_table | JOB_SITE estable, ticket cogido |
| 7 | cleric | brewing_stand | JOB_SITE estable, ticket cogido |
| 8 | butcher | smoker | JOB_SITE estable, ticket cogido |
| 9 | mason | stonecutter | JOB_SITE estable, ticket cogido |
| 10 | toolsmith | smithing_table | JOB_SITE estable, ticket cogido |
| 11 | weaponsmith | grindstone | JOB_SITE estable, ticket cogido |
| 12 | nitwit | — | **sin estación por diseño**, no cuenta en 11/11 |

### 2.2 Causas reales encontradas (no una sola)

1. **La ventana C4 de 600 ticks era menor que la gracia de carga de 1200 ticks.** El gestor nunca ejecutaba
   `reclamarEstacionesDelPueblo`; los 3 `JOB_SITE` que aparecían eran de `VillagerSmithGoal`/`VillagerMinerGoal`.
   La campaña larga usa 2400 ticks y `-ExigirCobertura`.
2. **`soltarTicketsPerdidos` contaba titulares por profesión, no dueños reales de `JOB_SITE`.** Podía no soltar
   tickets perdidos aunque no hubiera ningún dueño.
3. **`reclamadoPorAlguien` contaba `POTENTIAL_JOB_SITE` como propiedad**, incluidos guardias y el propio solicitante.
4. **Vanilla solo promueve `POTENTIAL` a `JOB_SITE` a menos de 2 bloques.** Los goals propios mantenían a los
   aldeanos lejos y los puestos quedaban muertos con el ticket cogido.
5. **El cerebro de un aldeano generado se construía con `NONE` y no se refrescaba al asignarle profesión.**
   `ValidateNearbyPoi` seguía usando el predicado de `NONE` y **borraba el JOB_SITE** en cuanto el aldeano estaba a
   menos de 16 bloques de la estación. Medido en la traza de prepare: 7 puestos cercanos perdían la memoria 20 ticks
   después de reclamarla; los lejanos sobrevivían. **Ésta era la causa principal del 3/12/4/11 histórico.**
6. **La reconciliación no tenía un libro de dueños.** `PoiManager` solo cuenta tickets y no sabe quién los cogió;
   `AcquirePoi` ignora el resultado de `take`. Sin libro, liberar y volver a coger podía quitarle el ticket a un
   dueño en un chunk descargado; borrar un `POTENTIAL` podía dejar el ticket colgado.

### 2.3 Correcciones

- `VillageGenerator.spawnVillagerAt`: `refreshBrain(level)` tras añadir al mundo, con la profesión ya fijada.
- `VillageManager.reclamarEstacionesDelPueblo`: vuelve a reclamar tras la gracia, asigna XP ≥ 1 y actualiza la
  estampa de reconciliación (pasadas, última pasada, reclamos, tickets, promociones, limpiezas).
- `VillageJobSiteReconciliationPolicy`: acciones puras `PROMOVER`, `LIBERAR_Y_LIMPIAR`, `LIMPIAR`; promoción solo
  con dueño del libro, sin liberar ni volver a coger; fuera de la aldea se libera el ticket solo si no hay dueño
  conocido.
- `VillageSavedData.JobSiteOwners` (**nuevo**, POS → UUID + `OwnerPos`): libro persistido de titularidad. Se escribe
  al reclamar/promover, se limpia al soltar, se guarda en NBT y sobrevive a las recargas. El manager:
  - restaura el `JOB_SITE` del dueño del libro sin tocar el ticket;
  - no libera un ticket si no sabe de quién es;
  - carga el chunk del dueño por su última posición; si el chunk carga y el UUID no existe, olvida la entrada;
  - elige al dueño del libro como superviviente de un duplicado.
- `VillageManager.liberarPuesto` y `moverPuestoDeTrabajo` actualizan el libro.
- C4 endurecido: `reconciliacion_manager_corrio` exige actividad real o cobertura 11/11 (no basta `pasadas>0`),
  `cobertura_demostrada` separado de `ownership_demostrado` (C4 no prueba el titular del ticket).

### 2.4 Evidencia C4 final

`build/c4n/c4n-20261124-0250` — SHA `1016a7cf`, `trabajoTicks=2400`, `exigirCobertura=true`:

```
C4N_PASS prepare=PASS reload1=PASS reload2=PASS
```

Las tres fases: `censo_profesionales_12_12`, `profesionales_presentes_12_12`, `fundadores_4_4`,
`uuid_duplicados_0`, `perdidas_aldeanos_0`, `job_site_duplicados_0`, `job_site_huerfanos_0`,
`mascara_fundadores_0b1111`, `fundadores_con_goal_4_4`, `fundadores_equipados_4_4`, `guardado_ok`,
`roster_invariantes_ok`, `identidad_profesion_12_12`, `goals_profesionales_12_12`, `reanudados_12_12`,
`reconciliacion_manager_corrio`, `cobertura_estaciones_11_11` → **11/11**.
`ownership_demostrado=false` y `cobertura_demostrada=true` (honesto: la titularidad del ticket se mide en POI).
El prepare tuvo 6 reclamaciones y actividad 92; reload1/reload2 no necesitaron reclamar porque la cobertura ya era
11/11, y el criterio lo acepta solo con esa cobertura, no con una pasada vacía.

Históricos preservados: `1550`/`1602` se clasifican `C4N_LEGACY_NO_MEDIDO` (no demuestran ownership/cobertura);
`020` sigue `C4N_FAIL`. No se reescribieron sus JSON.

---

## 3 · Persistencia real `ServerLevel` + `PoiManager` (POI)

Run final: **`build/av/poi-final-20261124-0305`** — SHA `1016a7cf`, 4 fases en JVM distintas sobre el mismo mundo
desechable: `prepare`, `reload1`, `reload2`, `partial`.

```
POI_PASS prepare=PASS reload1=PASS reload2=PASS partial=PASS
```

- 11/11 puestos cubiertos; `JOB_SITE=11`, `POTENTIAL=0`, `duplicados=0`, `huérfanos=0`,
  `tickets_sin_dueño=0`, `tickets_libres_con_dueño=0` en reload1/reload2/partial.
- `VillageSavedData`: **digest vivo == digest en disco** (`4bdc77af…`) en las 4 fases; el `.dat` se leyó
  desenvolviendo la clave `data` de `NbtIo.readCompressed` (defecto del arnés, no de producción).
- Tardío: aldeano no tocado por fixtures, cargado después de la foto y antes del mantenimiento; conserva su puesto
  y no cambia dueños ajenos. `reload1` marca `carga_parcial=NO_MEDIDO` porque el tardío ya se forzó; `partial`
  marca **`MEDIDO_OK` con 6 aldeanos cargados y 0 tickets liberados a dueños no cargados**.
- **F4 (nuevo)**: dos `POTENTIAL_JOB_SITE` en el mismo POI (630,63,567) y **un solo ticket**. Resultado:
  `fixture_f4_un_job_site=true` (1 dueño), `fixture_f4_sin_potential=true` (0), `fixture_f4_ticket_cogido=true`
  (0 libres), `fixture_f4_sin_ghost=true`. No hay `JOB_SITE` fantasma ni ticket colgado.
- F1 (duplicado), F2 (huérfano) y F3 (estación libre) se recuperan; entidades `12/12` profesionales, `4/4`
  guardias, `16/16` adultos del snapshot.
- `PoiPersistenceReport.autotest`: 15 casos, 0 fallos; `poi-dump.py` independiente sobre `world/poi/*.mca`.

### 3.1 Resolución de F1 (`fixture_duplicado_ticket_cogido`)

El fallo NO era una selección incorrecta de producción. El fixture **forzaba artificialmente** el libro a `A`
(`forgetJobSite` + `claimJobSite(A)`) aunque el dueño persistido/real fuera `B`. Al recargar, `A` podía no estar
cargado (o su memoria no estar entre las cargadas), `B` conservaba su `JOB_SITE` y el ticket, y el check exigía `A`
porque el propio fixture lo había impuesto. Evidencia: `fixtures-prepare.txt` de `poi-...-safety4` (A/B) frente a
`snapshot-reload1` (solo B con JOB); el libro al cargar apuntaba a B.

Corrección de arnés, no de producción:

- F1 ya **no reescribe** el libro. Lee el dueño persistido; si está cargado, ese es el keeper; si no, el fixture se
  declara **NO_MEDIDO** (no se sustituye por un duplicado cargado).
- Si el libro aún no existe, usa el farmer que realmente tiene el `JOB_SITE` (no un UUID arbitrario).
- El keeper se persiste en `fixtures-prepare.txt` y se relee en reload1/reload2 (antes el estático se perdía entre
  JVMs y el check no se ejecutaba).
- Si el keeper del libro no está cargado en la fase, `fixture_duplicado_ticket_cogido` se reporta **NO_MEDIDO**,
  nunca PASS vacío.
- Se añadió filtro circular de valla en el arnés **y** en producción (`soltarTicketsPerdidos` y `reclamar`), para
  que una aldea/ruina vecina (medido: farmer 688,68,591, 4/3) no cuele estaciones fuera del anillo.

Evidencia final: `poi-final-20261124-0305` → `POI_PASS` 4/4; F1 keeper = dueño del libro cargado, ticket 0 libres,
1 `JOB_SITE`, 0 `POTENTIAL`; el check de keeper no cargado queda documentado como NO_MEDIDO.

Lo que **no** demuestra: no hay jugador conectado ni asedios; no mide horas de chunks descargados; `reload1` no mide
carga parcial (se declara NO_MEDIDO); el mundo es desechable y las fases duran ~2 min.

---

## 4 · Navegación runtime

Run final: **`build/av/nav-final-20261124-0255`** — SHA `1016a7cf`.

```
NAV_PASS escenarios=21 pass=21 fail=0 invalid=0 timeout=0
```

Escenarios: campo abierto, STEP_UP, DROP, BREAK, BUILD, paso+romper, paso+construir, caída+romper,
caída+construir, romper+construir, caída+romper+construir, escalera, columna, muro, túnel, terreno dinámico,
inalcanzable, A* global reanudado, presupuesto agotado, recuperación y límites local/global.

### 4.1 Defectos reales de producción corregidos

1. **BUILD plano no llegaba al pie exacto.** La navegación vanilla daba el nodo por alcanzado a ~1.0 y el executor
   exigía ocupación exacta; sin nudge, `construir`, `paso_arriba_construir`, `romper_construir` y
   `caida_romper_construir` se quedaban a una celda. Fix: `NavBuildTransitionPolicy.shouldNudgeFeet` (solo con
   placement verificado, navegación terminada, Manhattan ≤ 2, `fits` y soporte) y `BUILD_FEET_NUDGE` en
   `NavExecutor`; regresión de 6 casos en `NavVerticalBuildPolicyTest` (39 aserciones).
2. **`terrainChanged` no rearmaba la recuperación.** Tras retirar una barrera, el ente esperaba hasta 400 ticks un
   cooldown del terreno viejo. Fix: reset de `nextRecoveryAt`/nodo de recuperación y traza
   `TERRAIN_CHANGED_REARM`. El escenario se hizo determinista (barrera a tick 20/70, ventana 2200) tras una
   intermitencia medida.

### 4.2 Fallos de arnés/geometría corregidos (no se relajó seguridad)

- `muro`: paneles separados 3 celdas para que el segundo stand sea válido.
- `tunel`: muro de 3 celdas, spawn pegado, una activación de 3 pasos.
- `caida_construir`: fixture BUILD+DROP con aristas normales, no la máquina de escalera.
- `inaccesible`: el preflight global devuelve una ruta-frente (`alcanzoMision=false`); el criterio exige
  `preflight` sin alcance, cero llegada y cero cambios, sin relajar el sellado.
- `astar_reanudacion`: mide el GLOBAL incremental real y **también** exige el GLOBAL directo
  (`directoGlobalAlcanzo`, `!hitBudget`), no solo una búsqueda en métricas.
- C10 y C3/C7 usan evidencia física (bloques construidos, sin bloque, travesadas, cambios inesperados), no solo
  contadores.

### 4.3 Métricas reales de esta campaña

| Métrica | Valor |
|---|---:|
| Escenarios / veredicto | 21 / 21 PASS |
| Búsquedas | 86 (75 completadas, 11 fallidas) |
| Entidades | 21 (una por escenario) |
| Expansiones | 2.044 |
| Tiempo del planner | 431.028.900 ns ≈ **431 ms** |
| Presupuestos agotados / reanudadas | 3 (artificiales) / 1 (`astar_reanudacion`) |
| Ticks de campaña | ~5.600 (≈280 s a 20 TPS) |

El detalle por escenario/entidad está en `build/av/perf-final/perf-resumen-final.md` y
`perf-resumen-final.csv`. El informe de análisis previo (`build/av/perf-runtime.md`) usó la campaña `nav-full2`
(416 ms, 90 búsquedas); las conclusiones de cuello de botella se mantienen.

**Cuellos de botella:** la validación de colisiones domina (≥99 % del tiempo verificable; ~43 candidatos/expansión,
~4–6 µs/candidato en preflight); el bookkeeping del A* es ruido. `inaccesible` repetía 8 búsquedas idénticas
condenadas (~20 % del tiempo); se propone cachear el destino negativo, no se implementó. Los planes LOCAL síncronos
pueden ocupar ~22 ms en un tick (p95 ~15 ms); el GLOBAL está troceado (~1,2 ms/tick).

**Optimización fastutil:** `NavGraphSearch` usa mapas primitivos (`Long2DoubleOpenHashMap` con NaN, etc.), cola
`PriorityQueue` sin cambios; A/B de 750 comprobaciones con rutas/hash idénticos. **No se declara ganancia runtime**:
no hay run anterior al cambio y el 92–93 % del coste es LOCAL, que no usa esa estructura. Techo estimado
0,21–1,34 ms en toda la campaña (≤0,32 % del planner). Para cuantificarlo haría falta un A/B de 6+ ejecuciones por
brazo con el mismo mundo; queda como trabajo futuro, no como claim.

---

## 5 · Guardias, C4 y A2

- C4 final conserva el contrato aceptado: 12 profesionales, 4 fundadores, máscara `0b1111`, 4/4 con goal, 4/4
  equipados, 0 duplicados/huérfanos, `roster_invariantes_ok`, `guardado_ok`.
- No se portó `GuardHarness`: duplicaba responsabilidades y su mundo/centro no son compatibles; la instrumentación
  de guardias vive en C4 (`GUARD_METRICS`, `AL_CARGAR`, `TABLA`).
- **A2 no se reejecutó en esta campaña.** Los cambios de `VillageGenerator.refreshBrain`, `NavExecutor`,
  `NavBuildTransitionPolicy` y `NavGraphSearch` tocan rutas que A2 usaba. C4/POI/NAV cubren sus áreas, pero no
  sustituyen una campaña A2 de 12.000 ticks. Se declara como limitación de regresión, no como PASS de A2.

---

## 6 · Herramientas y evidencia

| Herramienta | Uso | Evidencia |
|---|---|---|
| `tools/c4n/lanzar-c4n.ps1` | C4 3 fases, `-TrabajoTicks`, `-ExigirCobertura` | `build/c4n/c4n-20261124-0250` |
| `tools/av/diagnose.ps1` / `collect.ps1` | estados `C4N_PASS`, `C4N_LEGACY_NO_MEDIDO`, `C4N_FAIL`, `C4N_INCONSISTENTE`; cobertura ≠ ownership | `build/av` |
| `tools/av/Test-Aceptacion.ps1` | 49 comprobaciones estáticas del arnés/aceptación | OK |
| `tools/poi/lanzar-poi-harness.ps1` | 4 fases reales, digest, tardío, F1–F4, parcial | `build/av/poi-final-20261124-0305` |
| `tools/nav/lanzar-nav-harness.ps1` | 21 escenarios, compilación forzada del arnés, preflight global | `build/av/nav-final-20261124-0255` |
| `tools/perf/resumir-metricas.ps1` | agregación sin inventar; `NO_MEDIDO` no cuenta | `build/av/perf-final` |

Todos los flags de arnés (`navHarness`, `poiHarness`, `a2nDiagnostics`) son **opt-in**; el build normal no compila
`debug/**` ni `tools/poi`. El `test` normal incluye `villagePersistenceTest`, `poiPersistenceSelftest`,
`navScenarioContractTest`, `policyTest`, `navBaselineTest` y las políticas de ownership/build.

---

## 7 · Pruebas y gates finales

Ejecutados sobre el código probado (salvo los que se indiquen como documentación posterior):

| Comando | Resultado |
|---|---|
| `.\gradlew.bat test` | **BUILD SUCCESSFUL**; `VILLAGE_PERSISTENCE_INTEGRATION_TEST: OK (525)`; `VILLAGE_JOB_SITE_OWNERSHIP_POLICY_TEST: OK (32)`; `NAV_VERTICAL_BUILD_POLICY_TEST: OK (39)`; `NAV_SCENARIO_CONTRACT_TEST: OK (253)`; `PoiPersistenceReport.autotest: PASS 15`; `NAV_PLANNER_METRICS_TEST 68`; `NAV_SEARCH_BASELINE_TEST 119` |
| `.\gradlew.bat compileJava -Pa2nDiagnostics=true -Pa2nProbeOnly=true` | **BUILD SUCCESSFUL** |
| `python tools\lint_aldea.py --strict` | `lint aldea: OK` |
| `git diff --check` | 0 errores (solo warnings CRLF) |
| `tools/av/Test-Aceptacion.ps1` | `TEST_ACEPTACION: OK` |
| `tools/av/diagnose.ps1 -Run c4n-20261124-0250 -ExigirCobertura -FailOnError` | `C4N_PASS` |

---

## 8 · Revisión independiente

El revisor independiente auditó el working tree y encontró un **bloqueante real**: la promoción
`POTENTIAL→JOB_SITE` confiaba en `free_tickets==0` para saber quién tenía el ticket, y podía crear un `JOB_SITE`
fantasma o liberar el ticket de un dueño no cargado. Se corrigió con el libro persistido (§2.3/§3), carga del chunk
del dueño, restauración sin `release`+`take` y F4. La revisión delta confirmó que:
- F4 (dos POTENTIAL/1 ticket) queda sin fantasma;
- I1 (reconciliación efectiva), I2 (cobertura ≠ ownership), I7 (GLOBAL directo y C10), I3/I4 (POI parcial y
  estabilidad) están corregidos;
- el `refreshBrain`, `NavExecutor`, `NavGraphSearch` no presentan hallazgos nuevos.

La revisión final del delta del libro se pidió al mismo revisor; cualquier observación posterior se trata como
trabajo abierto, no como PASS silencioso.

---

## 9 · Limitaciones declaradas (honestidad)

1. **C4 no prueba titularidad del ticket.** Mide cobertura y deja `ownership_demostrado=false`; la titularidad se
   demuestra en POI. No se usa “ownership” para justificar el PASS de C4.
2. **A2 no se reejecutó.** Es la mayor deuda de regresión de esta campaña.
3. **El libro de dueños es nuevo.** Los mundos viejos sin libro se protegen (no se libera un ticket sin dueño
   conocido) y el chunk del dueño se carga cuando hay `OwnerPos`; si un dueño no cargado no tiene posición
   persistida, la estación puede quedar bloqueada hasta que se cargue. No se declara “no robo” absoluto para datos
   históricos sin libro.
4. **POI no mide horas de chunks descargados ni jugador conectado.** `reload1` declara carga parcial `NO_MEDIDO`;
   `partial` sí mide 6 aldeanos cargados y 0 liberaciones ajenas.
5. **La navegación es una entidad por escenario.** No cubre multientidad, laberintos largos, combate, asedio,
   puertas ni agua.
6. **La mejora fastutil no tiene A/B runtime.** Semántica idéntica (750 rutas), ganancia no cuantificada.
7. **`reanudados` de C4** es una señal de goal activo/movimiento; no demuestra que el goal faltara al cargar. El
   `astar_reanudacion` de NAV sí mide reanudación real del GLOBAL incremental.

---

## 10 · Veredicto

**COMPLETE** para el contrato ejecutable de esta campaña:

- C4 `C4N_PASS` 3/3, 11/11 estaciones, 12/12 profesionales, 4/4 fundadores, guardias/equipo, save/reload.
- POI `POI_PASS` 4/4, `ServerLevel`/`PoiManager`, tickets, digest, tardío, F1–F4, parcial.
- NAV `NAV_PASS` 21/21, con BUILD y terreno dinámico corregidos y A* global reanudado.
- Rendimiento real medido, cuellos de botella identificados, sin claims de mejora no demostrados.
- Gates verdes y revisión independiente con bloqueante corregido.

No se declara COMPLETE para A2 (no reejecutado) ni para datos históricos sin libro de dueños; esas son las deudas
explícitas de la §9.
