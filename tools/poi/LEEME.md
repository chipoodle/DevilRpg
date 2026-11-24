# POI · Arnés de persistencia REAL (`ServerLevel` + `PoiManager`)

Prueba de **integración real** (sin dobles) de la persistencia de puntos de interés y tickets de la
aldea autónoma: POIs de verdad en un `ServerLevel`, tickets de verdad (`free_tickets`,
`take`/`release`), `JOB_SITE`/`POTENTIAL_JOB_SITE` de verdad en cerebros de aldeanos, y
guardado/cierre/reapertura reales en **procesos JVM distintos**.

No sustituye a C4: lo **completa** en lo que C4 no medía. El `StubLevel` de
`VillagePersistenceIntegrationTest` (Incremento 3) no tiene chunks ni `PoiManager`; C4 abre un mundo
real pero su ventana de trabajo termina en el tick ~605 y **nunca cruza la gracia de carga**
(`VillageManager.CARGA_POBLACION_GRACIA_TICKS = 1200`), así que no ejerce el reparto ni la
reconciliación.

---

## 1. Qué mide y qué demuestra

| Requisito | Cómo se cubre | Evidencia |
|---|---|---|
| 1 · Registro/consulta real de POIs (tipos y posiciones) | `PoiManager.getInSquare` en el mundo real; histograma por oficio contra las plazas de `VillageGenerator` | `pois-<fase>.txt`, `phase-*.json` → `pois`, `checks.pois_registrados` |
| 2 · Tickets reales (`free_tickets`, `take`/`release`/`acquire`) y propiedad `JOB_SITE` | Sonda `take`→`release`→`take` sobre una estación ocupada (restaura el estado) + lectura de `free_tickets` por celda | `tickets-<fase>.txt`, `phase-*.json` → `ticket_probe`, `memoria` |
| 3 · Guardado y carga real (`saveEverything`, cierre, reapertura) de `VillageSavedData` y entidades | `saveEverything(false,true,true)`; el fichero `world/data/devilrpg_villages.dat` se lee con `NbtIo` en la fase siguiente; las entidades solo pueden venir del disco en otro JVM | `saveddata-<fase>.txt`, `saveddata-digest-*.txt`, `phase-*.json` → `saveddata`, `checks.saveddata_*` |
| 4 · Dos ciclos de recarga sobre el mismo mundo | `reload1` y `reload2` (dos reaperturas completas) + `partial` (tercera) | `fin.txt`, `phase-reload1.json`, `phase-reload2.json` |
| 5 · Carga parcial: esperar y luego asignar sin duplicar/perder | `reload1` (aldeano tardío L retenido) y `partial` (un solo chunk forzado) | `phase-*.json` → `carga_parcial`, `tardio`, `eventos`, `checks.carga_parcial_*` |
| 6 · Entidad que aparece después de los datos de aldea; no roba | Chunk de L forzado **después** de la gracia + sonda de aldeano extra (NITWIT) | `phase-reload1.json` → `tardio`, `checks.tardio_*`, `checks.aldeano_extra_no_roba`, `eventos-*.txt` |
| 7 · Recuperación tras datos incompletos/inconsistentes, sin forzar resultados | Estado natural del mundo (POTENTIAL sin JOB_SITE, tickets perdidos) + fixtures mínimas documentadas (duplicado, huérfano, estación libre) | `fixtures-<fase>.txt`, `phase-*.json` → `fixtures`, `hallazgos`, `checks.fixture_*` |
| 8 · Evidencia JSON/log por fase | `phase-<fase>.json` con POIs, tickets, JOB_SITE, POTENTIAL, UUID/profesión, duplicados/huérfanos, esperado/observado, eventos y códigos | todo `build/av/<run>/` |

**Lo que NO demuestra** (honestidad):
- No mide el comportamiento con jugador conectado ni asedios/hordas: en headless `VillageManager.tick`
  solo acumula presión y tickea asedios ya existentes (`HordeManager` sale si no hay jugador). El único
  mantenimiento de aldea es el que llama este arnés.
- No mide el reparto con chunks realmente descargados *durante horas*: el mundo es desechable y las
  fases duran ~1–2 min cada una.
- No prueba `acquire` de la API de vanilla (no existe como método público de `PoiManager`); prueba
  `take`/`release`, que es lo que hace `AcquirePoi` por debajo.
- No es un test de rendimiento.

---

## 2. Por qué no `gameTestServer`

`gameTestServer` arranca un mundo **plano nuevo por corrida**, ejecuta los `@GameTest` y sale: no
conserva el guardado entre procesos (harían falta **dos reaperturas del MISMO mundo**), exige
plantillas de estructura NBT para cada test y no permite dirigir `saveEverything` → cierre → reapertura.
Además el proyecto no tiene gametests registrados (`neoforge.enabledGameTestNamespaces` está puesto,
pero no hay ningún `@GameTest` en el árbol). Por eso se usa el mismo patrón de servidor que C4:
`runServer` con `-Pa2nGameDirectory` y un `@EventBusSubscriber` que conduce las fases.

---

## 3. Ficheros

| Fichero | Papel |
|---|---|
| `tools/poi/PoiPersistenceHarness.java` | Arnés de servidor (paquete `com.chipoodle.devilrpg.debug`). Conduce las 4 fases y escribe la evidencia. |
| `tools/poi/PoiPersistenceReport.java` | Lógica **pura** (sin Minecraft): duplicados, huérfanos, tickets, POTENTIAL, cobertura, comparación de mapas, JSON, SHA-256 y **autotest** (`main`). |
| `tools/poi/lanzar-poi-harness.ps1` | Lanzador: autotest puro, aislamiento, 4×`runServer`, validación de JSON/marcadores, dump independiente, `fin.txt`. |
| `tools/poi/poi-dump.py` | Volcado **independiente** de `world/poi/*.mca` (sin Minecraft): pos, tipo y `free_tickets` persistidos. |
| `build.gradle` (aditivo) | Flag `-PpoiHarness=true` (default **false**) y tarea `poiPersistenceSelftest`; el gate `test` incluye el autotest puro. |

### 3.1 Por qué el flag de Gradle

`-Pa2nDiagnostics=true` compila **todo** `debug/**`, incluida `C4StandaloneHarness`, que con
`c4n.phase=none` hace `fallar("fase_desconocida")` y `server.halt(false)` en `ServerStartedEvent`:
mataría el arnés POI. `-PpoiHarness=true` (que exige `-Pa2nDiagnostics=true`) **excluye todo
`debug/**`** y compila `tools/poi`; es el único entry point de ese build. Sin el flag, el build es
idéntico al de siempre.

> Ojo: Gradle **no borra** clases huérfanas de compilaciones anteriores en
> `build/classes/java/main/com/chipoodle/devilrpg/debug` (comprobado: una `C4StandaloneHarness.class`
> vieja sobrevive a un `compileJava` con exclusiones). Por eso el lanzador borra **solo ese directorio
> de clases de debug** y el estado incremental (`build/tmp/compileJava`) antes de compilar; **no** usa
> `--rerun-tasks` porque eso borra `build/classes/java/main` entero y choca con otros builds en curso.
> Tras compilar verifica que existen `PoiPersistenceHarness.class` **y** `PoiPersistenceReport.class`;
> si falta alguna, aborta (nos pasó: Gradle incremental restauraba solo la clase del fuente cambiado y
> el servidor moría con `NoClassDefFoundError`).

---

## 4. Comandos exactos

```powershell
# 1) Autotest puro (sin Minecraft): rápido, sin servidor
.\gradlew.bat poiPersistenceSelftest

# 2) Compilar el arnés aislado (sin lanzar nada)
.\gradlew.bat compileJava -Pa2nDiagnostics=true -PpoiHarness=true

# 3) Campaña completa (4 fases, mismo mundo desechable)
.\tools\poi\lanzar-poi-harness.ps1

# 3b) Run con nombre fijo y más margen
.\tools\poi\lanzar-poi-harness.ps1 -Run poi-20261124-1200 -TimeoutMinutes 25

# 3c) Subconjunto (para depurar; respeta el orden)
.\tools\poi\lanzar-poi-harness.ps1 -Fases prepare,reload1

# 4) Volcado independiente de POIs en disco (sin Minecraft)
python tools/poi/poi-dump.py build/av/<run>/game/world --tipo job --json

# 5) Evidencia
Get-Content build/av/<run>/fin.txt
Get-Content build/av/<run>/phase-prepare.json
Get-Content build/av/<run>/pois-reload1.txt
Get-Content build/av/<run>/eventos-reload1.txt
```

El lanzador **aborta** si hay otro cliente/servidor dev java vivo, si el `-Run` ya tiene evidencia, si
el autotest puro falla, o si una fase no deja JSON/marcador coherentes. No mata procesos ajenos: solo
el árbol del `gradlew` que lanza él.

Coste esperado: ~2 min por fase (generación + cruzar 1200 ticks de gracia + guardado) + arranque de
Gradle; 4 fases ≈ 8–12 min.

---

## 5. Fases (qué hace cada una)

- **prepare** (mundo virgen): `preGenerate` → 17×17 chunks forzados → mantenimiento en t=40, cada 200
  ticks hasta t≥1400, luego cada 40 hasta estabilizar → captura “feliz” → sonda de tickets → fixtures
  F1/F2/F3 → `saveEverything` → `halt`.
- **reload1** (mismo mundo, otro JVM): fuerza todo **menos el chunk de L** (con un buffer de radio 2:
  un chunk forzado propaga tickets a sus vecinos y con saltarse solo el de L no bastaba) → round-trip
  exacto de `JOB_SITE`/`POTENTIAL` y de `VillageSavedData` → **fuerza la zona tardía y comprueba la
  memoria persistida de L antes de cualquier mantenimiento** → mantenimiento antes de gracia → cruza
  gracia y estabiliza → sonda de aldeano extra (sin mantenimiento, para no alterar el roster) → guarda.
- **reload2**: reapertura completa; segundo round-trip y **estabilidad**. Después de la foto de carga se
  aplica **F4** (dos `POTENTIAL` al mismo POI con un solo ticket) y el primer mantenimiento de la fase
  debe dejar exactamente 1 `JOB_SITE` con ticket, 0 `POTENTIAL`, 0 ticket libre con dueño y ningún
  `JOB_SITE` fantasma. Guarda.
- **partial**: reapertura con **un solo chunk**; mide el fallo de carga parcial (¿se liberan tickets de
  dueños que aún no cargaron?); luego fuerza todo, cruza gracia y comprueba que no hay duplicados ni
  pérdidas. El estado se escribe como `MEDIDO_OK`, `MEDIDO_VIOLADO` o `NO_MEDIDO` (si no había ningún
  dueño persistido sin cargar, no se inventa un PASS vacío: no se añade el check).

### 5.1 Fixtures (solo en `prepare`, documentadas en `fixtures-prepare.txt`)

El estado natural del mundo C4 ya trae los casos que el jugador reporta: **11 POIs de trabajo,
11/11 con `free_tickets=0`, 3 `JOB_SITE`, 4 `POTENTIAL_JOB_SITE` y 4 estaciones con ticket cogido y
nadie con esa celda en el cerebro** (butcher `689,63,549`; fisherman `682,63,572`; shepherd
`718,63,534`; farmer `668,63,537`). Eso **no se toca**: se mide tal cual.

Las fixtures crean los dos casos que C4 midió a 0 (duplicado y huérfano) y una estación libre:

- **F1 · duplicado**: dos `farmer` con el mismo `JOB_SITE` (un solo ticket). Es el estado transitorio
  que el guardado puede capturar durante una reasignación (documentado en
  `VillageJobSiteOwnershipPolicy`).
- **F2 · huérfano + estación libre**: el `mason` tiene `JOB_SITE` en una celda **sin POI** y su
  estación queda libre (ticket devuelto). El aldeano debe perder la memoria huérfana y recuperar su
  estación.
- **F3 · estación libre sin memoria**: el `shepherd` sin `JOB_SITE` ni `POTENTIAL`, y su estación
  libre. Debe recibirla **después** de la gracia (espera), sin robar la de nadie.
- **F4 · POTENTIAL duplicado / `JOB_SITE` fantasma** (en `reload2`, después de la foto de carga):
  dos granjeros con `POTENTIAL_JOB_SITE` en el **mismo** POI y **un solo ticket cogido**. Vanilla
  `AcquirePoi` ignora el resultado de `take`, así que dos POTENTIAL + un ticket es un estado real. El
  primer mantenimiento debe dejar exactamente **1 `JOB_SITE` con ticket cogido, 0 POTENTIAL, 0 ticket
  libre con dueño, 0 ticket sin dueño** y ninguno fantasma. Si aparece un `JOB_SITE` sin ticket,
  `fixture_f4_sin_ghost` falla. Evidencia: `fixtures-reload2.txt` y `phase-reload2.json`.

No se fuerza el resultado: se observa. Si el pueblo no recupera, se reporta.

---

## 6. Criterios PASS/FAIL

Cada fase escribe `phase-<fase>.json` con **dos** veredictos y `checks` detallados:

- `experimento_ok`: el instrumento midió (setup, `preGenerate`/centro, guardado real, lectura del
  `.dat` del disco, JSON válido, sin timeout, y evidencia previa presente).
- `contrato_ok`: **todos** los `checks` de esa fase. Los invariantes son:

| Check | Contrato |
|---|---|
| `pois_registrados` | 11 celdas de trabajo y el histograma por oficio = plazas de `VillageGenerator` (3 farmer + 8 oficios; NITWIT sin puesto) |
| `meeting_point` | la campana (`minecraft:meeting`) existe en el radio |
| `sin_duplicados` | ninguna celda con 2+ `JOB_SITE` |
| `sin_huerfanos` | ninguna memoria `JOB_SITE` que apunte a una celda que ya no es POI |
| `tickets_sin_dueno` | todo POI de trabajo con `free_tickets=0` tiene a alguien con esa celda en el cerebro |
| `tickets_libres_con_dueno` | ningún `JOB_SITE` sobre una celda con ticket libre |
| `potential_promovido` | ningún `POTENTIAL` con ticket cogido y sin dueño esperando promoción |
| `potential_sin_ajeno` | ningún `POTENTIAL` apuntando a una celda que ya es `JOB_SITE` de otro |
| `cobertura_puestos` | cada oficio con sus plazas cubiertas y con `JOB_SITE` (11/11) |
| `entidades_esperadas` | todos los profesionales/guardias del snapshot cargados (admite “gente de sobra” extra) |
| `sonda_tickets_take_release` | `take` sobre ocupada vacío; `release`→1; `take`→presente; restaurado |
| `<fase>_carga`, `<fase>_carga_pares`, `<fase>_carga_pares_potencial` | el mapa y los pares (POI, UUID) guardados cargan exactamente igual (el tardío L se excluye hasta que se fuerza) |
| `saveddata_carga` / `saveddata_disco_igual_vivo` | la huella estable de `VillageSavedData` del disco coincide con la del guardado anterior; el `.dat` es legible |
| `centro_persistido` | el centro de la aldea no cambia entre fases |
| `guardado_ok` | `saveEverything(false,true,true)` devolvió `true` |
| `fixture_duplicado_reconciliado` | la celda duplicada acaba con **un** dueño |
| `fixture_huerfano_recuperado` | el mason pierde la memoria sin POI y recupera su estación |
| `fixture_estacion_libre_asignada` | el shepherd recibe su estación tras la gracia |
| `tardio_memoria_persistida` | L carga con el `JOB_SITE`/`POTENTIAL` exactos que se guardaron |
| `tardio_carga_sin_cambios_ajenos` | al cargar L no cambia el dueño de ninguna otra celda |
| `tardio_conserva_puesto` | si L tenía `JOB_SITE`, lo conserva (no se lo roban) |
| `aldeano_extra_no_roba` | el aldeano extra no se queda con una celda que ya tenía dueño; sin duplicados |
| `fixture_f4_un_job_site` | F4: exactamente 1 `JOB_SITE` en el POI de dos POTENTIAL |
| `fixture_f4_sin_potential` | F4: 0 `POTENTIAL` en ese POI tras la promoción |
| `fixture_f4_ticket_cogido` | F4: `free_tickets == 0` (el ticket del ganador está confirmado) |
| `fixture_f4_sin_ghost` | F4: no hay `JOB_SITE` sin ticket ni más dueños que tickets |
| `carga_parcial_sin_liberar_ajenos` | solo si el caso se MIDIÓ: no se libera el ticket de un dueño no cargado (`MEDIDO_OK`/`MEDIDO_VIOLADO`; si no, `NO_MEDIDO`) |

Veredicto global (`fin.txt`):

- `POI_PASS` — las 4 fases con `experimento_ok=true` y `contrato_ok=true`.
- `POI_HALLAZGOS` — el arnés midió, pero algún contrato falla: **es un resultado válido** y lista los
  códigos (`COBERTURA_INCOMPLETA`, `TICKET_SIN_DUENO`, `POTENTIAL_NO_PROMOVIDO`,
  `TICKET_LIBERADO_CON_DUENO_NO_CARGADO`, `JOB_SITE_DUPLICADO`, `JOB_SITE_HUERFANO`, …). Cada
  hallazgo lleva `poi`, `uuid`, `profesión`, tipo y tick en `detalle`.
- `POI_FALLO` — no se pudo medir (timeout, guardado fallido, JSON/marcador incoherente, sin evidencia
  previa, autotest puro en rojo).

> Con el `VillageManager` de `HEAD ae28c19a` (sin reconciliación de POTENTIAL) lo esperable es
> `POI_HALLAZGOS`: cobertura < 11, `POTENTIAL_NO_PROMOVIDO`, `TICKET_SIN_DUENO` y, en `partial`,
> `TICKET_LIBERADO_CON_DUENO_NO_CARGADO`. Con la reconciliación de `aldea` (working tree) lo esperable
> es que bajen a 0 y el run tienda a `POI_PASS`; si no, el arnés lo dirá con números.

---

## 7. Evidencia que deja cada fase (`build/av/<run>/`)

```
sha.txt · git-status.txt · selftest.txt · fin.txt
srv-<fase>.log / .err
phase-<fase>.json          # veredicto, checks, hallazgos, esperado/observado, eventos, digests
pois-<fase>.txt            # posLong|pos|tipo|free_tickets|max|oficios
duenios-<fase>.txt         # uuid|profesion|pos|job_site|potential
puestos-<fase>.txt         # poiLong|pos|uuid|profesion|tipo|free_tickets
snapshot-<fase>.txt        # uuid|profesion|x|y|z|job|potential|guardia|bebe  (lo lee la fase siguiente)
eventos-<fase>.txt         # ASIGNACION / CAMBIO_DUENIO / PUESTO_PERDIDO / TICKET_LIBERADO / ALDEANO_NUEVO
tickets-<fase>.txt         # sonda take/release/take (solo prepare)
fixtures-<fase>.txt        # fixtures aplicadas (prepare: F1/F2/F3; reload2: F4)
saveddata-<fase>.txt       # centro, digest, generated/layout/guardias/health/food
saveddata-digest-<fase>.txt
poi-dump.json              # volcado independiente de world/poi/*.mca (post-run)
```

La evidencia identifica el código medido: `sha.txt` (HEAD) y `git-status.txt` (working tree con
cambios de otros agentes, p.ej. la reconciliación de `aldea`). **Un run no es comparable si el
`git-status.txt` difiere.**

---

## 8. Estado medido de partida (solo lectura, mundo C4 `build/c4n/c4n-20261123-1550`)

`python tools/poi/poi-dump.py .../game/world --tipo job` + lectura de `entities/*.mca`:

- 11 POI de trabajo: `farmer` ×3 (`628,63,547` / `630,63,567` / `668,63,537`), `weaponsmith`
  `666,63,487`, `toolsmith` `665,63,487`, `cleric` `644,64,490`, `shepherd` `718,63,534`, `butcher`
  `689,63,549`, `fisherman` `682,63,572`, `fletcher` `610,63,508`, `mason` `692,63,502`.
- **11/11 con `free_tickets=0`**.
- `JOB_SITE` = 3 (mason, toolsmith, weaponsmith). `POTENTIAL_JOB_SITE` = 4 (2 farmer, fletcher, y un
  `none`/guardia). **4 estaciones con ticket cogido y nadie con la celda en el cerebro**: butcher,
  fisherman, shepherd y farmer `668,63,537` (corrección de `aldea`; inicialmente se habían contado 3).
- C4 cierra en el tick ~605 < 1200: **nunca cruzó la gracia**. Ese es el hueco que cubre este arnés.

---

## 9. Limitaciones conocidas y notas de coordinación

- **Sin jugador no hay mantenimiento de producción**: `VillageManager.tick(level)` solo acumula
  presión y tickea asedios existentes; `HordeManager` no lanza hordas sin jugador. Todo el
  mantenimiento es explícito del arnés. Esto hace el experimento determinista, pero no prueba el
  camino `manageNearby` con jugador.
- **Carga parcial**: si el mantenimiento de la fase `partial` carga todos los chunks antes de mirar
  (por las lecturas de bloques de `tickVillageLife`), el caso puede salir “no medido”
  (`caso_medido=false`) y el check pasa vacío; se registra así, no se disimula. `reload1` cubre el
  caso controlado reteniendo un chunk concreto.
- **El chunk del tardío L** puede cargarse antes de forzarlo si alguna lectura de bloques del
  mantenimiento lo toca; el arnés lo detecta y escribe `TARDIO_YA_CARGADO_ANTES_DE_FORZAR`.
- **Reloj**: la gracia se mide desde la primera llamada a `cargaDeAldeaAsentada` (nuestra primera
  llamada al mantenimiento), no desde el arranque; por eso se espera `GRACIA + 200` ticks desde el
  inicio de la fase y luego se estabiliza.
- **`VillageManager` no se toca**: el arnés mide comportamiento. Si `aldea` cambia la reconciliación,
  el arnés lo mide igual (los checks son de estado observable, no de API interna). No se llama a
  `estampaDeReconciliacion()` para no depender de una API que no está en HEAD.
- **No ejecutar en paralelo** con el cliente/servidor del jugador ni con otra campaña C4/A2: el
  lanzador aborta si detecta procesos dev ajenos.
- **Evidencia histórica intacta**: el run escribe solo en `build/av/<run>/`; no toca `run/`, saves,
  `build/c4n` ni `build/av` de otras corridas.

---

## 10. Autotest puro

`PoiPersistenceReport.autotest()` (15 casos) se ejecuta con `gradlew poiPersistenceSelftest` y también
con `javac`/`java` a secas. Cubre: duplicados, huérfanos, tickets sin dueño, ticket libre con dueño,
POTENTIAL pendiente vs POTENTIAL ajeno, cobertura por oficio, comparación estable/cambiada/perdida/
nueva, liberación con dueño no cargado, escapado JSON y SHA-256. **No necesita Minecraft**: es el
control de calidad de la lógica que decide PASS/FAIL.
