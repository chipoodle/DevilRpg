# Herramientas AV · arnés C4N y diagnóstico (Incremento 3 · Objetivo E)

Esta carpeta (`tools/av/`) es **nueva y versionable**. No sustituye a `tools/c4n/lanzar-c4n.ps1`: lo
**lee** y lo **explica**. Todo lo que escriben estos scripts va a `build/av/` (ignorado por git).

> **REGLA DE ORO:** `build/c4n/**` es **evidencia histórica** y `run/saves/**` es **la partida del jugador**.
> Estos scripts **solo leen** `build/c4n`; **nunca** borran, mueven ni reescriben un run. No se ejecuta
> Minecraft desde aquí. El runtime lo lanza el operador con `tools\c4n\lanzar-c4n.ps1`.

## Contenido

| Fichero | Qué hace |
|---|---|
| `diagnose.ps1` | Diagnóstico de un run (`-Run`) o de todos (`-All`). Escribe `build/av/diagnostico-<run>.json` y `.md`. |
| `collect.ps1` | Tabla/CSV agregada de todos los runs y resumen de fallos. Escribe `build/av/colector-<fecha>.csv` y `colector.csv`. |
| `nav-baseline.ps1` | Lanza la tarea Gradle `navBaselineTest` **si existe** y guarda la salida en `build/av/nav-baseline-<fecha>.txt`. Si no existe, avisa y no falla. |
| `LEEME.md` | Este documento. |

## Qué cambió en el arnés C4 (`tools/c4n/C4StandaloneHarness.java`)

El arnés sigue haciendo lo mismo (3 fases sobre el mismo mundo desechable, censo de 12 profesionales,
instantánea, guardado y parada). **No se ha relajado ningún FAIL existente**; se ha **endurecido** el cierre con
`guardado_ok` (si el guardado falla, la fase es FAIL) y se han añadido campos de diagnóstico. Los cambios son de
instrumentación:

1. **`censoJobSitesAlCargar()` ya se llama** en `reload1`/`reload2`, justo al completar la carga y **antes**
   del mantenimiento inicial. Deja la foto `AL_CARGAR` (JOB_SITE persistido) en el log y en el JSON.
2. **Propiedad de puestos estable entre fases.** Se escribe `jobsite-<fase>.txt` (POI → UUID + profesión +
   guardia/slot/tipo) al cierre de cada fase y se compara con la fase previa. Campos nuevos:
   `job_site_estables`, `job_site_cambiados`, `job_site_perdidos`, `job_site_nuevos`, `job_site_comparacion`,
   `job_site_al_cargar_presentes`, `job_site_al_cargar_cargados`, `job_site_reclamados_en_ventana`,
   `job_site_al_cargar_perdidos`, `job_site_al_cargar_duplicados`, `job_site_al_cargar_huerfanos`,
   `job_site_detalle[]`.
   **Un cambio de dueño NO es FAIL**: los FAIL duros siguen siendo duplicados, huérfanos, pérdidas de aldeano,
   identidad de profesión, goals, reanudados e invariantes del roster.
3. **Métricas de guardias** (`guardias:{...}`): total, fundadores, `fundadores_esperados`,
   `mascara_persistida[4]` (`saved.isGuardiaFundadoraCreada(0, slot)`), `mascara_bits`, `mascara_completa`,
   `con_goal_guardia`, `armadura_completa`, `con_arma`, `con_escudo` y `detalle[]` por guardia. Se lee con
   `VillagerGuardGoal.esGuardia`, `VillageManager.esGuardiaFundadora`, `getItemBySlot` + `ArmorItem` y
   `Items.SHIELD`; no se inventa nada que no se pueda leer con seguridad. **No afecta al veredicto.**
4. **Bloque estructurado** en `phase-<fase>.json` y en `diagnostico-<fase>.json`:
   `esperado`, `observado`, `invariantes` (con `roster_invariantes_ok`, `roster_violaciones` y los flags duros),
   `causa_conocida[]`, `causa_desconocida[]`, `notas[]` y `guardado_ok`. Se conservan `result`, `fase_ok`,
   `roster_invariantes_ok` y los campos planos de siempre (compatibilidad con lanzadores/collector).
5. **`diagnostico-<fase>.json`** se escribe al final de cada fase con el bloque anterior, los artefactos y
   comandos de reproducción. El log deja líneas `DIAG fase=...` y `DIAG causa_*=` para `grep`.
6. **Guardado antes del marcador de fin.** Antes `done-<fase>.txt` se escribía antes de `saveEverything`; el
   lanzador mataba el JVM al verlo y el log se cortaba en `Saving chunks...`. Ahora:
   `saveEverything` → `saved-<fase>.txt` → `phase-<fase>.json`/`diagnostico-<fase>.json` → `done-<fase>.txt`.
   Si el guardado lanza, se escribe `save-failed-<fase>.txt`, `guardado_ok=false`, el resultado pasa a `FAIL`
   y `done-<fase>.txt` se escribe igualmente (el lanzador no espera 20 minutos). Se registra
   `FASE <fase> guardado_ok=true/false`.
7. **JSON validado antes de escribir** (Gson) y **claves duplicadas filtradas** (`sinClavesDuplicadas`): si la
   construcción saliera mal, se guarda `*.json.invalido.txt` y un JSON mínimo válido con `result:FAIL`.

### Formato de evidencia (nuevo)

```
build/c4n/<run>/
  sha.txt                  SHA del commit con el que se lanzó
  fin.txt                  C4N_PASS|C4N_FAIL prepare=... reload1=... reload2=...
  phase-<fase>.json        resultado + campos planos + bloque estructurado
  diagnostico-<fase>.json  bloque estructurado + artefactos + reproducir
  snapshot-<fase>.txt      uuid|profession|pos|goal
  jobsite-<fase>.txt       # poi_long|poi|uuid|profesion|guardia|slot|tipo|tipo_poi
  saved-<fase>.txt         epoch ms del guardado correcto
  save-failed-<fase>.txt   solo si saveEverything lanzó
  srv-<fase>.log[.err]     log del servidor y stderr del lanzador
```

`jobsite-<fase>.txt` se escribe **después** de la ventana de trabajo, no al cargar. La foto de carga se
registra aparte (`AL_CARGAR` y campos `job_site_al_cargar_*`): así no se confunde "memoria vacía al cargar,
se reclama durante la ventana" con "el dueño cambió durante la ventana".

## Los cinco estados que distingue el diagnóstico

| Estado | Cuándo se emite |
|---|---|
| `OK` | `result=PASS` y sin invariantes incumplidas. |
| `INVARIANTE_INCUMPLIDA` | `result=FAIL` y algún flag duro del bloque `invariantes` está en `false` (o `roster_violaciones` no está vacía). |
| `FALLO_CON_CAUSA_CONOCIDA` | `result=FAIL` sin invariante duro, pero con al menos una `causa_conocida` documentada (p. ej. `MEMORIA_JOB_SITE_INCOMPLETA`, `GOALS_NO_INSTALADOS_POR_CARGA_PARCIAL`, `ALDEA_REPUEBLA`, `JOB_SITE_NO_RECLAMADO_ENTRE_FASES`, `GUARDADO_FALLIDO`). |
| `FALLO_CON_CAUSA_DESCONOCIDA` | `result=FAIL` y la única explicación disponible es `causa_desconocida` (p. ej. `DUPLICADO_POR_COLISION_DE_PROPIEDAD`, `CAMBIO_DE_DUENIO_SIN_EVIDENCIA`). |
| `SIN_EVIDENCIA` / `EVIDENCIA_INVALIDA` | No hay `phase-<fase>.json` ni `done-<fase>.txt`, o el JSON no parsea. |

Las causas **conocidas** no cambian el veredicto: explican una anomalía ya tolerada (memorias JOB_SITE que se
reclaman durante la ventana, goals que no se persisten, aldeas que repueblan, guardado que falla). Las
**desconocidas** tampoco convierten nada en FAIL por sí solas: se listan para que el operador sepa qué mirar.
Los FAIL duros se deciden en `okFase` con los mismos números de siempre.

## Uso

```powershell
# Un run concreto (nombre completo o sufijo)
.\tools\av\diagnose.ps1 -Run 024
.\tools\av\diagnose.ps1 -Run c4n-20261123-024

# Todos los runs; sale con 1 si alguno no es C4N_PASS
.\tools\av\diagnose.ps1 -All
.\tools\av\diagnose.ps1 -All -FailOnError

# Tabla + CSV + resumen de fallos
.\tools\av\collect.ps1
.\tools\av\collect.ps1 -Run 024

# Línea base de navegación (si el planner ya añadió la tarea)
.\tools\av\nav-baseline.ps1
```

`diagnose.ps1` **no rompe** si faltan ficheros o el JSON es inválido: emite la fase con
`SIN_EVIDENCIA`/`EVIDENCIA_INVALIDA`, `json_valido=false` y la lista de `faltantes`. Si el `phase-*.json` es de
una versión anterior sin bloque estructurado, **deriva** `esperado`/`observado`/`invariantes` de los campos
planos y lo marca con `origen=derivado_...`; `roster_invariantes_ok` queda en `null` (= **no medido**, no
`false`).

Salida de `diagnose.ps1` por run:

```
build/av/diagnostico-c4n-20261123-024.json
build/av/diagnostico-c4n-20261123-024.md
```

El `.md` incluye: veredicto, SHA, tabla por fase (esperado / observado / invariante incumplida / causa
conocida / causa desconocida), detalle de guardias, puestos, snapshot, evidencia del log y **cómo reproducir**:

```powershell
.\tools\c4n\lanzar-c4n.ps1 -Run <nuevo>
```

## `collect.ps1`

Emite una fila por **run + fase** con:
`run, fase, result, result_origen, guardado_ok, roster_invariantes_ok, esperados, presentes, duplicados,
huerfanos, fundadores, job_site_presentes, job_site_estables, job_site_cambiados, job_site_perdidos,
job_site_nuevos, goals_instalados, reanudados, activos, movidos, sha, fin, json_valido, json_motivo`.

Escribe el CSV con marca de tiempo y una copia `colector.csv` (el último). El resumen de fallos lista cada
fase no-PASS y, si `diagnose.ps1` ya corrió para ese run, engancha sus códigos de causa conocida/desconocida.

## `nav-baseline.ps1`

Detecta la tarea por dos vías: fuente de test (`src/test/**/*NavSearchBaseline*`) o registro en
`build.gradle(.kts)`. Si no la encuentra, **no ejecuta Gradle** y **no escribe evidencia** (para no dejar un
fichero que parezca una medida real); imprime `TAREA_NO_DISPONIBLE` y sale con 0. Si existe, lanza:

```powershell
.\gradlew.bat navBaselineTest --console=plain
```

y guarda stdout+stderr en `build/av/nav-baseline-<fecha>.txt` (copia `nav-baseline-ultimo.txt`) con cabecera
(fecha, SHA, comando, detección) y pie (`exit_code`, `resultado=PASS/FAIL`). Devuelve el código de Gradle.
No lanzarlo en paralelo con otra compilación (mismo `build/`). A fecha de este incremento, el planner ya añadió
`NavSearchBaselineTest` y la tarea, y el script la ejecutó correctamente (ver
`build/av/nav-baseline-ultimo.txt`).

## Evaluación de `tools/arnes/GuardHarness.java` (no portado)

**Decisión: NO se porta ni se reconstruye sobre C4.** Se deja intacto como copia de referencia histórica.

Evidencia medida en el árbol actual (2026-11-23):

- **Tamaño y forma:** 5.976 líneas / 386.661 bytes. **34 constantes booleanas de escena** (30 `MEDIR_*` +
  `SEMBRAR_AGUA_EMBOTELLADA`, `SEMBRAR_LENA_AL_COCINERO`, `MONSTRUO_DENTRO`, `PLANTAR_AL_LENADOR_EN_LA_PLAZA`) que
  hay que editar a mano en el fuente para elegir qué se mide. **0 escrituras JSON** (`Files.write`/`writeString`):
  solo logs.
- **Supuestos de mundo clavados:** `CENTRO = new BlockPos(470, 63, 646)` e `INDICE = 0`
  (`GuardHarness.java` L36-L37), `ancla()` con `Random` y comentarios que dependen del guardado del jugador
  (`run/world`: 470,46,646 = agua; L3035). No es un mundo desechable: es una **copia del guardado del
  jugador** (`run/world`).
- **Muta el mundo de la copia:** 38 `setBlock`, 22 `addFreshEntity`, 11 `discard`, 3 `setDayTime`,
  2 `getGameRules()`, 2 `spawnOneVillager`, 1 `markSiegeResolved`, además de vaciar el almacén y sembrar
  despensa/leña; fuerza chunks (L1938) y cambia reglas de día/spawn. Eso invalida comparar reload1/reload2
  como hace C4.
- **Doble mecanismo:** llama `VillageManager.manageNearby(level, pega, ancla(), INDICE)` (L413) con un
  `FakePlayer` (3 usos de `FakePlayerFactory`). Es la ruta de tick del **jugador**, no la del arnés, y
  reejecuta mantenimiento/reparto. C4 ya hace su propio mantenimiento (`ejecutarMantenimientoParaArnes`) en un
  mundo aislado; mezclar ambos produciría dos mecanismos de mantenimiento y dos fuentes de verdad.
- **Semántica de guardia desactualizada:** su helper `esGuardia()` (L790-L796) devuelve true si el aldeano
  **tiene el goal** `VillagerGuardGoal`; la arquitectura actual usa la marca persistente
  `VillagerGuardGoal.esGuardia` (marca + copia sincronizada). Un guardia sin goal reinstalado se vería como
  "no guardia".
- **Sin invariantes de roster:** no conoce `VillageRosterInvariants` ni el censo de 12/4; mide escenas
  sueltas, no el contrato de la aldea.

**Lo único rescatable** era la *idea* de verificar el equipo de la guardia (`getItemBySlot`, `ArmorItem`,
`Items.SHIELD`). Se ha integrado **dentro** de C4 como lectura barata y read-only (`guardias.armadura_completa`,
`con_arma`, `con_escudo`, `detalle[]`), no como segundo arnés ni utilidad duplicada. No se copió código de
GuardHarness.

Si en el futuro hiciera falta una escena concreta de GuardHarness (p. ej. una ola o un portón), la vía es un
**escenario pequeño y aislado** que reutilice el mundo desechable y el contrato de C4, no resucitar el banco
de 5.976 líneas.

## Apoyo local (Qwen)

Se usó el modelo local una vez para revisar el diseño y la evaluación de GuardHarness:

```
.\tools\arnes\consulta_local.ps1 -Ficha build\av\ficha-qwen-herramientas.txt -Modelo qwen3.5:9b
```

- Ficha: `build/av/ficha-qwen-herramientas.txt`
- Respuesta y verificación contra el código real: `build/av/qwen-herramientas.md`
- Resultado: **78,8 s / 3.502 tokens**. Sus afirmaciones se contrastaron; las que no coinciden con el árbol
  actual están marcadas en ese fichero. Qwen propone, el código y las mediciones deciden.

## Validaciones ejecutadas (sin lanzar Minecraft)

```powershell
# Sintaxis de los cuatro scripts
[System.Management.Automation.Language.Parser]::ParseFile(...)   # los 4: SINTAXIS_OK

# Compilación del arnés contra las clases reales de NeoForge 1.21.1
javac -proc:none -encoding UTF-8 -d build\av\javac-out `
  -cp "build\classes\java\main;build\moddev\artifacts\neoforge-21.1.249-merged.jar;<jars de build/moddev/serverLegacyClasspath.txt>" `
  tools\c4n\C4StandaloneHarness.java                              # exit 0

# Diagnóstico y colector sobre runs reales (solo lectura)
.\tools\av\diagnose.ps1 -Run 024
.\tools\av\diagnose.ps1 -Run 020
.\tools\av\collect.ps1
```

## Riesgos y pendientes

- **No se ha ejecutado el runtime** (lo lanza el operador). La compilación valida APIs, pero no el
  comportamiento en juego: el primer run nuevo debe revisar `AL_CARGAR`, `jobsite-*.txt`, `guardias` y
  `diagnostico-*.json`.
- **`job_site_presentes` bajo (2-3/12 en runs históricos)** es esperado por la limitación conocida de
  memorias JOB_SITE; por eso no hay umbral duro de cobertura. Si se quiere exigir cobertura, primero hay que
  demostrar que el reclamado es determinista.
- **La comparación entre fases depende de `jobsite-<fase>.txt`**: en runs anteriores a este cambio no existe
  y `diagnose.ps1` lo marca como `fase_previa_sin_tabla`/faltante (no inventa estabilidad).
- **`guardado_ok`**: un fallo de guardado invalida la fase y escribe `save-failed-<fase>.txt`; el operador debe
  mirar ese marcador antes de culpar a la aldea.
- **Cierre del lanzador**: ya no mata cualquier java con el patrón dev; aborta si hay un cliente/servidor ajeno
  y solo cierra el árbol del `gradlew` que lanzó (`taskkill /PID <srv> /T /F` tras 60 s sin salir). No se debe
  lanzar en paralelo con otro agente que compile.
- **`init-c4n.gradle`**: el lanzador ya no lo genera (era código muerto, nunca se pasaba `--init-script`). El
  aislamiento real sigue siendo `-Pa2nGameDirectory=build/c4n/<run>/game`. Los `init-c4n.gradle` de runs
  antiguos son evidencia histórica y no se tocan.
