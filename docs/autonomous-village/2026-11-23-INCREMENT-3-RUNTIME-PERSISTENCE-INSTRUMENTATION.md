# AUTONOMOUS VILLAGE — INCREMENTO 3: REVALIDACIÓN RUNTIME, PERSISTENCIA NBT E INSTRUMENTACIÓN

> **Estado:** A revalidado en runtime; B, D y E implementados y verdes; F (este documento) actualizado.
> **SHA de código probado en la campaña final:** `69d7e55a6c848fd6c0542e61a8efe8913431f13c`
> **Campañas finales:** `c4n-20261123-1550` y `c4n-20261123-1602` (dos campañas consecutivas, mundos nuevos)
> **Fecha:** 2026-11-23 · Rama `feature/autonomous-village`
> **Restricciones respetadas:** sin push, sin merge externo, sin cambio de rama, sin `reset --hard`, sin `clean`, sin
> borrar mundos/backups/logs/evidencia, sin incorporar los seis commits descartados y sin debilitar criterios.

---

## 0. Resumen ejecutivo

| Objetivo | Estado | Evidencia principal |
|---|---|---|
| A · C4 runtime con invariantes obligatorios | **PASS** | `c4n-20261123-024` (SHA `1abab57d`) + campañas finales `c4n-20261123-1550` (SHA `69d7e55a`) y `c4n-20261123-1602` (SHA `5c427842`, mismo código) |
| B · NBT/save-reload real | **DONE** | `VillagePersistenceIntegrationTest`: **484 aserciones**, 10 bloques, `NbtIo` real y `Villager` real |
| D · Instrumentación del planner + línea base | **DONE** | `NavPlannerMetrics` (640 líneas), 68 + 119 aserciones, 5 escenarios deterministas |
| E · Herramientas de diagnóstico | **DONE** | arnés C4 enriquecido, `diagnose.ps1`, `collect.ps1`, `nav-baseline.ps1`, evaluación de GuardHarness |
| F · Documentación | **DONE** | este informe, invariantes, matriz de pruebas y checkpoints |

Gates finales (ver §7): `compileJava`, `compileTestJava`, `test` (incluye `policyTest` y
`villagePersistenceTest`), `navBaselineTest`, `compileJava -Pa2nDiagnostics=true`, `lint_aldea.py --strict` y
`git diff --check`.

---

## 1. Objetivo A — Revalidación runtime C4

### 1.1 Campaña aceptada de partida

La campaña `c4n-20261123-024`, ejecutada sobre `1abab57dce53a0e802d7133606ed3ad484008e7d`, pasó las tres fases con
`roster_invariantes_ok=true` **obligatorio** (la fase falla si el verificador devuelve violaciones):

| Fase | Result | Roster | Profesionales | Fundadores | Duplicados | Huérfanos | JOB_SITE | Activos | Reanudados |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| prepare | PASS | true | 12/12 | 4 | 0 | 0 | 2 | 11 | 12/12 |
| reload1 | PASS | true | 12/12 | 4 | 0 | 0 | 3 | 11 | 12/12 |
| reload2 | PASS | true | 12/12 | 4 | 0 | 0 | 3 | 8 | 12/12 |

- UUID idénticos en las tres fases; profesiones idénticas (3 farmers + 9 oficios); 0 pérdidas; 120 muestras por fase.
- 4 guardias fundadores (2 espadachines + 2 arqueros), máscara persistida de 4 bits.
- `devilrpg_villages.dat` actualizado tras el guardado; el mundo se reabrió dos veces sobre el mismo directorio.
- HEAD real al comenzar el incremento: `dda7fb6d` (hijo directo de `1abab57d`), **solo documentación**: no toca
  código ni build y no invalida la evidencia de A.

### 1.2 Qué mide y qué no mide el PASS (honestidad)

- **Mide**: 12 profesionales vivos, 4 fundadores, UUID únicos, profesión estable, cupos, goals instalados,
  reanudación observada, ausencia de pérdidas, un solo dueño por `JOB_SITE` presente y ausencia de huérfanos.
- **No mide**: que los 12 aldeanos tengan `JOB_SITE` al mismo tiempo. En `024` solo 2–3 de 12 tenían memoria de
  puesto; en la campaña final fueron 1→3→3 de 12. El resto llega a `none`. Es una **limitación conocida** del reparto
  (las memorias se reclaman durante la ventana y dependen del oficio y del tick), no un fallo del arnés. El
  verificador no exige cobertura de POI para no convertir una limitación conocida en un falso PASS ni en un FAIL
  artificial; sí exige que no haya dos dueños del mismo POI y que la propiedad de los POI presentes sea estable.
- `reanudados=12` significa `goal instalado && (activo || movido >0,5 bloques)`; no demuestra trabajo completo. La
  actividad real observada fue 11/11/8 activos en `024` y 11/11/9 en la campaña final; `movidos=12/12/11` en la
  final (reload2 pierde un movimiento pero mantiene goal y reanudación).

### 1.3 Correcciones al arnés motivadas por A/E

1. **Guardado antes del marcador**: `cerrarFase()` escribe ahora `saved-<fase>.txt` después de
   `saveEverything` y **después** escribe `done-<fase>.txt`. Antes el lanzador podía matar el JVM en cuanto veía
   `done` y el log se cortaba en `Saving chunks...`. Un fallo de guardado (excepción o retorno `false` de
   `saveEverything`) produce `save-failed-<fase>.txt`, `guardado_ok=false` y la fase pasa a `FAIL`.
2. **Invariantes obligatorios**: `fase_ok` incluye `VillageRosterInvariants.ok()` desde el incremento 2; el comentario
   que decía “informativo” se corrigió.
3. **Censo de puestos al cargar**: se activa `censoJobSitesAlCargar()` justo al completar la carga y **antes** del
   mantenimiento, y se comparan las tablas `jobsite-<fase>.txt` entre fases (`estables/cambiados/perdidos/nuevos`).
4. **Métricas de guardias**: total, fundadores, máscara persistida por slot, goal de guardia, armadura, arma y escudo.
   Son observadas: no cambian el veredicto.

### 1.4 Campaña final sobre el SHA de código

- **Run:** `c4n-20261123-1550` · **SHA:** `69d7e55a6c848fd6c0542e61a8efe8913431f13c` · **Mundo:**
  `build/c4n/c4n-20261123-1550/game` (desechable).
- **Resultado:** `C4N_PASS prepare=PASS reload1=PASS reload2=PASS` (`fin.txt`), las tres fases con
  `roster_invariantes_ok=true`, `guardado_ok=true` y `fase_ok=true`.
- **Estabilidad de propiedad medida por primera vez con el arnés enriquecido**:
  - `prepare`: 1 `JOB_SITE` (weaponsmith 666,63,487).
  - `reload1`: 3 `JOB_SITE` (el de prepare **estable** + toolsmith y mason reclamados en la ventana; 0 cambiados,
    0 perdidos).
  - `reload2`: los 3 **estables** (3/3), 0 cambiados, 0 perdidos; `job_site_al_cargar_duplicados=0` y
    `job_site_al_cargar_huerfanos=0` en ambas recargas.
- **Guardias**: 4/4 fundadores, máscara persistida `[true,true,true,true]` (`0b1111`), 4/4 con goal, armadura
  completa 4/4, arma 4/4, 2 escudos.
- **Repetibilidad (segunda campaña):** `c4n-20261123-1602` sobre `5c427842` (commit **solo de documentación**
  encima de `69d7e55a`; árbol de código idéntico) volvió a dar `C4N_PASS` en las 3 fases con
  `roster_invariantes_ok=true`, `guardado_ok=true`, 12/12, 4 fundadores, 0 duplicados/huérfanos y `JOB_SITE`
  2→3→3, **3/3 estables** en reload2 (0 cambiados/perdidos; 0 duplicados/huérfanos al cargar). Dos campañas
  consecutivas con mundos nuevos confirman que el PASS no depende de una sola fase ni de un mundo concreto.
- **Conservación:** el mundo, logs, JSON, snapshots y `sha.txt` del run quedan íntegros. No se reutiliza el run id
  (el `prepare` exige mundo virgen; el lanzador aborta si ya hay evidencia). No se borra ningún run histórico.

---

## 2. Objetivo B — Integración NBT/save-reload

### 2.1 Infraestructura y principio

La prueba es un programa `main` headless (`src/villageTest/.../VillagePersistenceIntegrationTest.java`) ejecutado por
la tarea Gradle `villagePersistenceTest`, **enganchada al gate `test`**. Usa el mecanismo real:

- `net.neoforged.fml.loading.LoadingModList.of(...)`, `SharedConstants.tryDetectVersion()`,
  `Bootstrap.bootStrap()` y `RegistryAccess` reales, como `FoodIntegrationTest`.
- `VillageSavedData.save(CompoundTag, HolderLookup.Provider)` y `VillageSavedData.load(CompoundTag, ...)` reales,
  **no** una copia de campos en memoria.
- `NbtIo.writeCompressed` / `NbtIo.readCompressed` sobre un fichero real en `build/av/village-persistence/`.
- `EntityType.VILLAGER.create(level)`, `Villager.saveWithoutId`, `EntityType.create(tag, level)` y el codec real del
  `Brain` (`MemoryModuleType.JOB_SITE`, `GlobalPos`) para identidad y propiedad.
- Un `StubLevel` mínimo (sin chunks, sin servidor, sin `PoiManager`) solo como **infraestructura de `Level`**; la
  lógica de persistencia y los codecs son los de producción. La limitación está documentada en el javadoc y en §2.5.

### 2.2 Cobertura (484 aserciones)

| Bloque | Qué prueba |
|---|---|
| 1 · NBT vacío | Todos los defaults (`HEALTH_UNKNOWN`, centro/plano/parcela nulos, máscara libre) y save/load/save estable. |
| 2 · Estado completo | `Generated`, `Resolved`, `Occupied`, `Fallen`, `Noticed`, presión con `Since` + multiplicador, `FarmOwners` con UUID, `Settlement` completo (centro, máscara 0b1111, salud, repoblación, hambre, layout, casas, anexo, remesa, comida, crédito) y planos con `BlockState` reales (escalera `facing=east`, trigo `age=3`, coordenadas negativas). |
| 3 · Dos ciclos | Comparación campo a campo y `CompoundTag` normalizado; UUID exacto (`toString`), no solo igualdad lógica; la normalización ordena **entradas** por `Index`/`Key`, nunca celdas ni paleta. |
| 4 · Fichero real | `writeCompressed`/`readCompressed`; truncado y basura no cargan datos a medias; el fallback vacío no inventa estado. |
| 5 · Fundadores | Slots 0..3 individuales, parcial `0b1010`, slots 4/-1 ignorados, dos ciclos, bit sucio `0b10000` no marca nada y se conserva al marcar. |
| 6 · Carga parcial | Falta cada sección top-level por turno: sin excepción, defaults de la sección y resto conservado. |
| 7 · Corrupto/inconsistente | UUID basura descartado, `FarmOwners` duplicado (gana el UUID menor en ambos órdenes), plano desalineado / índice fuera / paleta vacía / `Name` inválido, bloque desconocido → AIR, propiedad inválida → valor por defecto, `Settlement` duplicado (escalares: última entrada; remesa monotónica; orden invertido), claves desconocidas ignoradas, tipos equivocados, `Fallen` implica `Resolved`. |
| 8 · Reconciliación | Claim/release idempotente y persistente; `VillagePopulationLoadPolicy` en la ventana de gracia; `VillageJobSiteOwnershipPolicy` sin duplicados y con POIs distintos. |
| 9 · Identidad real | `Villager` real con UUID, `VillagerData(PLAINS, FARMER, 3)` y `JOB_SITE` `GlobalPos(OVERWORLD, 12,64,-18)`; save → `EntityType.create`; UUID/profesión/nivel/posición exactos; segundo ciclo `tag.equals(segundo)`; fichero comprimido; dos aldeanos con el mismo POI → la política deja uno, se borra la memoria del sobrante y tras guardar/cargar no reaparece. |
| 10 · Censo persistido | 12 profesionales + 4 fundadores desde los NBT persistidos tras dos ciclos, sin violaciones; detecta UUID duplicado, puesto duplicado y cupo excedido. |

### 2.3 Defecto real encontrado y corrección mínima

**Antes del fix** (`build/av/village-persistence-before-fix.log`): **471 aserciones, 7 fallos**, de los cuales **5 son
defectos reales de producción**. Después del fix y de añadir el caso de índice negativo: **484 aserciones, 0 fallos**
(el test también creció; 471→484 no mide solo el fix de producción).

```text
FALLO: corrupto plano desalineado: recorrerlo no lanza IOOBE
       (java.lang.ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1)
FALLO: corrupto plano desalineado: se descarta para recapturarlo
FALLO: corrupto plano indice fuera: stateAt no lanza
FALLO: corrupto plano indice fuera: se descarta
FALLO: corrupto palette Name invalido: load no lanza
       (ResourceLocationException: Non [a-z0-9/._-] character in path of location: minecraft:NOT A VALID ID!!!)
```

Causa: `VillageSavedData.load` aceptaba un `Blueprint` sin validar `Palette`/`Pos`/`State`. Un guardado corrupto podía
(a) lanzar `ResourceLocationException` durante la carga del mundo, o (b) dejar un plano que reventaba con
`ArrayIndexOutOfBoundsException` al recorrerlo el obrero de `VillageManager` (que itera por índice).

**Corrección** (solo `VillageSavedData.readBlueprint`, ~37 líneas): lee la paleta, valida que
`positions.length == states.length` y que cada índice cae dentro de la paleta; si algo no es consistente, **descarta
el plano** (`hasBlueprint=false`) para que el gestor lo vuelva a capturar. Un plano válido se carga exactamente igual;
el formato NBT no cambia. **Después del fix: 484 aserciones, 0 fallos.**

Los otros 2 fallos eran expectativas del test, no producción: un fichero truncado lanza `ReportedNbtException`
(RuntimeException), no `IOException`; y al quitar `Fallen` del NBT, `Resolved` conserva el 4 ya guardado.

### 2.4 Clasificación de las pruebas

| Tipo | Pruebas | Necesita servidor |
|---|---|---|
| Unitarias/puras | `VillageRosterInvariantsTest`, `VillageJobSiteOwnershipPolicyTest`, `VillagePopulationLoadPolicyTest` (dentro de la integración), políticas de navegación | No |
| Integración NBT (headless, Minecraft real) | `VillagePersistenceIntegrationTest` (484) | No |
| Integración de build | `villagePersistenceTest`, `foodNbtTest`, `foodIntegrationTest` | No |
| Runtime | Campaña C4 (prepare/reload1/reload2), A2 (cerrado) | **Sí** |

### 2.5 Limitaciones abiertas de B

- No se prueba `VillageSavedData.get(ServerLevel)`/`DataStorage` real ni `PoiManager.acquire/release`; el
  `StubLevel` no tiene chunks. Eso queda cubierto por la campaña C4 runtime, que sí abre y guarda un mundo real.
- El aldeano `LIBRARIAN` no se puede restaurar en el stub porque `VillageTrades` exige el registro de datapack
  `minecraft:enchantment`; se usa `NITWIT` como segunda profesión sin trades. `FARMER` sí se prueba completo.
- Una lista NBT mixta (Compound + Int) no es representable en 1.21.1 (`ListTag.add` lanza); no se simula una
  corrupción imposible.

---

## 3. Objetivo D — Instrumentación del planner

### 3.1 Diseño

- **Nuevo** `NavPlannerMetrics` (puro, sin Minecraft): flag `devilrpg.nav.metrics` (default **false**),
  `setEnabled`, `reset`, `begin/advance/end/cancel`, `snapshot`, `dumpLinea`, `dumpCsv`, `dump`.
- Mide: id, tipo (`GLOBAL`, `GLOBAL_EXACT`, `LOCAL`, `SINTETICA`), UUID, tick, nanos, expansiones, presupuesto,
  avances/slices, nodos por avance, último slice, reanudada, replanificación, fallo, presupuesto agotado, longitud de
  ruta, motivo, estado; agregados por entidad y totales.
- **No invasiva**: `NavGraphSearch` **no se tocó** (0 líneas de diff). En `NavPlanner` solo se añaden llamadas de
  medida y un campo `final long metricsId`; el cuerpo de `plan(...)` se movió **verbatim** a `planImpl(...)`, con un
  wrapper que mide. Con el flag apagado, `begin` devuelve `SIN_BUSQUEDA` y `advance/end/cancel` solo comprueban un
  `volatile`; no se formatea ni se asigna.
- **Memoria acotada**: 512 entidades, 4096 búsquedas activas, 512 registros cerrados, con desalojo oportunista y
  totales globales que no se pierden. Sin locks (un solo hilo escritor en producción), sumas saturadas, sin
  excepciones.
- **Controlable en runtime**: `-PnavMetrics=true` en `runServer` añade `-Ddevilrpg.nav.metrics`; cualquier arnés puede
  llamar `NavPlannerMetrics.dump()` al cerrar. Por defecto **apagado**.

### 3.2 Puntos instrumentados

`NavPlanner` global/incremental (creación, cada `advance`, cierre, cancelación y reinicio por parámetros) y
`NavPlanner.plan(...)` local (wrapper con duración y expansiones). El delta de expansiones se toma de
`Session.expansions()` antes/después del bloque real; el tiempo excluye el trabajo de post-proceso.

### 3.3 Línea base reproducible (A* real, grafos sintéticos)

Escenarios deterministas sobre `NavGraphSearch.Session` real; 3 repeticiones incrementales + búsqueda síncrona +
_ON/OFF_. Los nanos se imprimen, **no se comparan** (JIT/GC); las aserciones son de determinismo y coherencia.

| Escenario | Presupuesto | Expansiones | Pops (stale) | Ruta | Resultado | Nanos (rango 3 reps) |
|---|---:|---:|---:|---:|---|---|
| campo_abierto_32x32 | 4096 | 63 | 63 (0) | 62 | found | 0,75–1,72 ms |
| muro_con_hueco | 4096 | 32 | 32 (0) | 31 | found | 0,15–0,40 ms |
| laberinto_serpentina | 4096 | 528 | 528 (0) | 527 | found | 2,31–4,70 ms |
| meta_inalcanzable | 4096 | 1021 | 1948 (927) | 60 | no found · “frente alcanzable agotado” | 10,6–15,5 ms |
| presupuesto_insuficiente | 25 | 25 | 26 (0) | 24 | hitBudget · “presupuesto global agotado” | 0,086–0,161 ms |

Agregado 4 entidades intercaladas: 4 búsquedas, **252 expansiones**, 4 reanudadas, 13 rondas; cada entidad 63
expansiones y 13 avances. No-regresión ON/OFF: `misma_ruta=true`, mismas expansiones, mismo `hitBudget` y mismo
`reason` en los 6 escenarios. Evidencia: `build/av/nav-baseline-ultimo.txt` y `build/av/nav-baseline-*.txt`.

### 3.4 Qué se puede concluir y qué no

- **Sí**: el A* real es determinista en estos escenarios; la instrumentación no altera el resultado; el coste medido
  es coherente con las expansiones; hay una base objetiva para comparar versiones.
- **No**: no se ha medido el planner sobre terreno de Minecraft real ni con aldeanos reales en runtime; eso requiere
  un arnés de navegación (A2) con `-PnavMetrics=true`. No se ha cambiado el algoritmo (no hay evidencia que lo
  justifique); A* incremental se conserva.

---

## 4. Objetivo E — Herramientas de diagnóstico

### 4.1 Arnés C4 enriquecido

`tools/c4n/C4StandaloneHarness.java` conserva las 3 fases y los FAIL duros, y añade:

- `censoJobSitesAlCargar()` en reload antes del mantenimiento (foto `AL_CARGAR`).
- `jobsite-<fase>.txt` (POI → UUID + profesión + guardia/slot/tipo) y comparación con la fase previa:
  `job_site_estables`, `job_site_cambiados`, `job_site_perdidos`, `job_site_nuevos`,
  `job_site_al_cargar_{presentes,cargados,perdidos}`, `job_site_reclamados_en_ventana`.
- `guardias`: total, fundadores, esperados, `mascara_persistida[4]`, `mascara_bits`, `mascara_completa`,
  `con_goal_guardia`, `armadura_completa`, `con_arma`, `con_escudo`, detalle por guardia.
- Bloque estructurado `esperado`, `observado`, `invariantes`, `causa_conocida[]`, `causa_desconocida[]`, `notas[]`,
  `guardado_ok` en `phase-<fase>.json`, y `diagnostico-<fase>.json` con artefactos y comandos de reproducción.
- JSON **validado** antes de escribir (Gson); claves duplicadas filtradas; si la construcción falla, se guarda
  `*.json.invalido.txt` y un JSON mínimo `result:FAIL`.
- **Guardado primero, marcador después**: `saveEverything` → `saved-<fase>.txt` → JSON → `done-<fase>.txt`; un fallo
  de guardado marca la fase `FAIL` y deja `save-failed-<fase>.txt`.

### 4.2 Herramientas nuevas (`tools/av/`)

| Herramienta | Qué hace |
|---|---|
| `diagnose.ps1` | `-Run`/`-All`; lee `fin.txt`, `sha.txt`, `phase-*.json`, `snapshot-*`, `jobsite-*`, logs; escribe `build/av/diagnostico-<run>.{json,md}` con tabla por fase y los 5 estados. Tolerante a ficheros ausentes/JSON inválido. |
| `collect.ps1` | Tabla/CSV agregada de todos los runs (run+fase) y resumen de fallos; engancha las causas de `diagnose` si existen. |
| `nav-baseline.ps1` | Detecta y lanza `navBaselineTest`; guarda la salida en `build/av/nav-baseline-<fecha>.txt`; si no existe la tarea, avisa y no inventa evidencia. |
| `LEEME.md` | Uso, formato, taxonomía, evaluación de GuardHarness y reglas de preservación. |

### 4.3 Los cinco estados

| Estado | Criterio operativo |
|---|---|
| `OK` | `result=PASS` y sin invariantes incumplidas. |
| `INVARIANTE_INCUMPLIDA` | `FAIL` con un flag duro en `false` o `roster_violaciones` no vacía. |
| `FALLO_CON_CAUSA_CONOCIDA` | `FAIL` sin invariante duro y con causa documentada (`MEMORIA_JOB_SITE_INCOMPLETA`, `GOALS_NO_INSTALADOS_POR_CARGA_PARCIAL`, `ALDEA_REPUEBLA`, `JOB_SITE_NO_RECLAMADO_ENTRE_FASES`, `GUARDADO_FALLIDO`, `FALLO_DE_INSTRUMENTO`). |
| `FALLO_CON_CAUSA_DESCONOCIDA` | `FAIL` con `DUPLICADO_POR_COLISION_DE_PROPIEDAD`, `CAMBIO_DE_DUENIO_SIN_EVIDENCIA`, `JOB_SITE_AL_CARGAR_DESAPARECIDO`, `ALDEANO_PERDIDO_SIN_CAUSA_MEDIDA`, `INVARIANTE_DE_ROSTER_INCUMPLIDA`. |
| `SIN_EVIDENCIA` / `EVIDENCIA_INVALIDA` | No hay fase o el JSON no parsea; se marcan los ficheros que faltan. |

Aplicación real: `diagnose.ps1 -Run 020` clasifica `reload2` como `INVARIANTE_INCUMPLIDA` por
`job_site_duplicados=1` y lista la causa desconocida `SIN_CLASIFICACION_EN_EL_JSON_ORIGINAL` (formato anterior, sin
bloque de causas). `-All` procesó 23 runs sin romper con los runs históricos incompletos.

### 4.4 GuardHarness: evaluación y decisión

`tools/arnes/GuardHarness.java` (5.976 líneas / 386.661 bytes) **no se porta**. Evidencia:

- Centro e índice **hardcodeados** (`470,63,646`, índice 0), mundo del jugador copiado a `run/world`, jugador falso y
  `manageNearby`; muta el mundo (38 `setBlock`, 22 `addFreshEntity`, 11 `discard`, 3 `setDayTime`, etc.) y **no
  escribe JSON** (solo logs). 34 constantes booleanas de escena que hay que editar a mano.
- Duplicaría el mantenimiento actual (`ejecutarMantenimientoParaArnes` vs `manageNearby`), rompería la comparación
  reload1/reload2 por mutar el mundo y usaría una semántica de guardia distinta de la marca persistente actual.
- Lo rescatable —leer equipo y goal de guardia— ya está integrado en C4 (`guardiasJson`, `piezasDeArmadura`), sin un
  segundo arnés ni código A1 duplicado.

### 4.5 Lanzador seguro

`lanzar-c4n.ps1` ahora:

- Resuelve la raíz desde `$PSScriptRoot` (no desde el cwd).
- **Preflight**: aborta si hay un cliente/servidor dev ajeno (`devlaunch.Main|forgeserverdev|fml.modFolders`), en vez
  de matarlo.
- Espera a que el `gradlew` de **este** lanzamiento termine (hasta 60 s); solo entonces `taskkill /PID <srv> /T /F`
  sobre su árbol. No mata java del sistema a ciegas.
- Solo acepta un `done-<fase>.txt` con `LastWriteTime >= inicio de la fase` (no confunde marcadores viejos).
- Deja de generar `init-c4n.gradle` (era código muerto; el aislamiento real es
  `-Pa2nGameDirectory=build/c4n/<run>/game`). Los `init-c4n.gradle` históricos no se tocan.

---

## 5. Invariantes y qué prueba las protege

| Invariante | Prueba / evidencia |
|---|---|
| 12 profesionales, 4 fundadores, UUID únicos, un dueño por `JOB_SITE`, cupos, sin nulos | `VillageRosterInvariants` + `VillageRosterInvariantsTest` (12) + C4 (obligatorio) |
| Máscara de fundación de 4 bits persistente e idempotente | `VillagePersistenceIntegrationTest` bloque 5 (individual, parcial, sucio, dos ciclos) |
| Identidad de aldeano (UUID/profesión/nivel) y `JOB_SITE` sobreviven save/load real | `VillagePersistenceIntegrationTest` bloque 9 (`Villager` + `EntityType.create` + `NbtIo`) |
| `VillageSavedData` completo sobrevive dos ciclos, incluso a fichero | bloques 2–4 |
| Carga parcial/corrupta no revienta ni inventa estado | bloques 6–7; corrección `readBlueprint` |
| Reconciliación de duplicados de puesto y ventana de gracia | bloques 8; `VillageJobSiteOwnershipPolicyTest`, `VillagePopulationLoadPolicyTest` |
| El planner no cambia de comportamiento al instrumentar | `NavSearchBaselineTest` NO_REGRESION (6 escenarios ON/OFF) + `NavGraphSearch` sin diff |
| El diagnóstico distingue esperado/observado/invariante/causa | `diagnose.ps1` + `LEEME.md` + run 020 |
| El arnés no da PASS si el guardado falla | `guardado_ok` en `fase_ok`; `save-failed-<fase>.txt` |

---

## 6. Campaña final

```
Run:        c4n-20261123-1550
SHA:        69d7e55a6c848fd6c0542e61a8efe8913431f13c
Comando:    .\tools\c4n\lanzar-c4n.ps1 -Run c4n-20261123-1550 -TimeoutMinutes 20
fin.txt:    C4N_PASS prepare=PASS reload1=PASS reload2=PASS
Mundo:      build/c4n/c4n-20261123-1550/game (desechable; se conserva)
Guardado:   saved-{prepare,reload1,reload2}.txt + phase-*.json + done-*.txt (guardado_ok=true en las 3)
```

| Fase | result | guardado_ok | roster | 12/12 | 4 fundadores | 0 pérdidas | 0 dup | JOB_SITE | estables/cambiados/perdidos/nuevos | guardias |
|---|---|---|---|---|---|---|---|---|---|---|
| prepare | PASS | true | true | 12/12 | 4 | 0 | 0 | 1 | 0/0/0/0 (sin fase previa) | 4/4, mask 0b1111, armadura 4/4, arma 4/4, 2 escudos |
| reload1 | PASS | true | true | 12/12 | 4 | 0 | 0 | 3 | 1/0/0/2 | 4/4, mask 0b1111, armadura 4/4, arma 4/4, 2 escudos |
| reload2 | PASS | true | true | 12/12 | 4 | 0 | 0 | 3 | 3/0/0/0 | 4/4, mask 0b1111, armadura 4/4, arma 4/4, 2 escudos |

Datos de carga (`AL_CARGAR`, antes de mantenimiento): reload1 `cargados=12/12`, `job_site_al_cargar_presentes=1`,
duplicados/huérfanos 0; reload2 `cargados=12/12`, `job_site_al_cargar_presentes=3`, duplicados/huérfanos 0.
`activos` 11/11/9, `movidos` 12/12/11, `reanudados` 12/12/12 en las tres fases.

Diagnóstico: `.\tools\av\diagnose.ps1 -Run c4n-20261123-1550 -FailOnError` → `C4N_PASS`, 3×`OK`, exit 0.
Colector: `.\tools\av\collect.ps1 -Run 1550` → 3 filas PASS, 0/3 fases no-PASS.

### 6.1 Segunda campaña (repetibilidad)

```
Run:        c4n-20261123-1602
SHA:        5c4278422c057117d48d2303bc94b56d24cb6dad  (docs-only sobre 69d7e55a; código idéntico)
fin.txt:    C4N_PASS prepare=PASS reload1=PASS reload2=PASS
```

| Fase | result | guardado_ok | roster | 12/12 | 4 fundadores | JOB_SITE | estables/cambiados/perdidos/nuevos | carga: presentes/duplicados |
|---|---|---|---|---|---|---|---|---|
| prepare | PASS | true | true | 12/12 | 4 | 2 | 0/0/0/0 | — |
| reload1 | PASS | true | true | 12/12 | 4 | 3 | 2/0/0/1 | 2/0 |
| reload2 | PASS | true | true | 12/12 | 4 | 3 | 3/0/0/0 | 3/0 |

`guardias` 4/4, máscara `0b1111`, armadura/arma 4/4, 2 escudos en las tres fases. Dos campañas consecutivas
(`1550` y `1602`) con mundos nuevos: **PASS en las seis fases**, sin duplicados, sin pérdidas y con propiedad
estable (3/3) en la última recarga.

---

## 7. Gates y comandos

```text
.\gradlew.bat compileJava compileTestJava --console=plain     BUILD SUCCESSFUL
.\gradlew.bat test --console=plain                            BUILD SUCCESSFUL
  - policyTest                                               74 clases registradas
  - villagePersistenceTest                                   VILLAGE_PERSISTENCE_INTEGRATION_TEST: OK (484)
.\gradlew.bat navBaselineTest --console=plain                 NAV_SEARCH_BASELINE_TEST: OK (119)
.\gradlew.bat compileJava -Pa2nDiagnostics=true               BUILD SUCCESSFUL
python tools\lint_aldea.py --strict                           lint aldea: OK
git diff --check                                              0
```

Pruebas nuevas registradas: `NavPlannerMetricsTest` (68), `NavSearchBaselineTest` (119) en `policyTest`;
`villagePersistenceTest` en el gate `test`.

---

## 8. Defectos, correcciones y limitaciones

### 8.1 Defectos corregidos

1. **Guardado antes de `done`** (arnés): carrera real de la campaña A; ahora el marcador va después del guardado y
   un fallo de guardado es `FAIL`.
2. **Plano NBT corrupto** (`VillageSavedData.readBlueprint`): crash en carga/recorrido con `Pos`/`State`
   desalineados, índice fuera de paleta o `Name` inválido; se descarta el plano y se recaptura.
3. **Lanzador mataba java a ciegas**: ahora preflight + cierre del árbol del propio `gradlew`.
4. **`init-c4n.gradle` muerto**: eliminado de la generación; el aislamiento real es `-Pa2nGameDirectory`.
5. **Comentario “informativo”** del roster corregido: los invariantes son obligatorios desde el incremento 2.
6. **`guardado_ok` no ignoraba el retorno**: ahora usa el `boolean` de `saveEverything`; si devuelve `false` o lanza,
   la fase es `FAIL` y queda `save-failed-<fase>.txt`.
7. **Diagnóstico de carga**: se añadió auditoría de `JOB_SITE` **en el instante de carga** (duplicados/huérfanos
   antes de mantenimiento) y se clasifican como causa conocida si se reconcilian; la campaña final midió 0/0.
8. **Lanzador**: rechaza reutilizar un `-Run` con evidencia previa, exige que JSON y `done` sean posteriores al
   inicio de fase y que ambos digan `PASS`; un `done` vacío ya no rompe el script.

### 8.2 Limitaciones abiertas (no son PASS)

- `JOB_SITE` presente en 1→3→3 de 12 aldeanos en la campaña final (2–3 en `024`); la cobertura total no se exige.
  La campaña final reporta **estabilidad** de los POI presentes (3/3 estables en reload2, 0 cambiados/perdidos),
  no cobertura.
- `reanudados` es una señal débil (`goal` + activo/movido); no demuestra trabajo completo (`movidos=11/12` en
  reload2).
- B no prueba `PoiManager`/`ServerLevel`; eso queda en C4 runtime.
- D no mide navegación real en runtime; la línea base es del A* real sobre grafos sintéticos. El arnés C4 vuelca
  `metrics-<fase>.txt` si se habilita `-PnavMetrics=true`, pero C4 no navega; medir en A2 sigue pendiente.
- `GuardHarness` no se porta; queda como referencia histórica.

---

## 9. Preservación

- No push, no merge externo, no cambio de rama, no `reset --hard`, no `clean`.
- `run/saves` intacto; los runs de `build/c4n` se conservan (incluidos los fallidos `015/017/020`).
- No se modificó la evidencia histórica de A2/C4; los runs antiguos no tienen los campos nuevos y `diagnose` los
  deriva de campos planos (`origen=derivado_...`, `roster_invariantes_ok=null` = no medido).
- Los seis commits descartados de `migration-neoforge-1.21.1` no se incorporaron.
- El árbol de trabajo conserva los untracked previos (`src/foodTest`, `tools/a2n/*`, `.a2-backups`, `.a5-closure`).

---

## 10. Próximos pasos

1. Repetir una segunda campaña C4 con **mundo nuevo** si se quiere separar “pasa una vez” de “pasa siempre”
   (recomendado; no bloquea el cierre actual).
2. Medir el planner en runtime con `-PnavMetrics=true` en un arnés de navegación (A2) y comparar con la línea base.
3. Si se toca la cobertura de `JOB_SITE`, hacerlo con una prueba de runtime que demuestre que no rompe la ventana de
   carga ni la reconciliación.
