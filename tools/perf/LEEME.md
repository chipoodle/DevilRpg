# Rendimiento del planner A* — medición, agregación y comparación

Carpeta del especialista de **rendimiento** de la campaña de cierre técnico de Autonomous Village.
Todo lo de aquí es medición: no cambia rutas, presupuestos, costes ni invariantes.

## Ficheros

| fichero | qué hace |
|---|---|
| `resumir-metricas.ps1` | Agrega índices del arnés de navegación + volcados literales de `NavPlannerMetrics`, y opcionalmente la línea base sintética y el perfil A*. Genera CSV/Markdown sin inventar datos. |
| `medir-nav.ps1` | Lanza `NavSearchPerfTest` (calentamiento + 11 repeticiones + fases + A/B + sobrecarga de métricas) y archiva la salida en `build/av/perf/`. |
| `navperf.init.gradle` | Registra la tarea Gradle `navPerfTest` **sin editar `build.gradle`** (compartido con otros agentes). |
| `fixtures/` | Se genera en `build/av/perf/fixture/` con el MISMO escritor para validar el parser. No es evidencia. |

## 1. Contrato de entrada del arnés de navegación (v1, acordado con `navegacion`)

Carpeta `build/av/nav-<run>/`:

- `nav-escenarios-<ts>.csv` (y copia `nav-escenarios-ultimo.csv`): índice, una fila por entidad.
  Cabecera exacta, delimitador coma, sin comas dentro de valores:

  ```text
  escenario,repeticion,entidad,tipo_busqueda,presupuesto,encontrado,presupuesto_agotado,longitud_ruta,
  expansiones,nanos_metricas,nanos_pared,avances,ticks,aristas,candidatos,encolados,motivo,metrics_csv
  ```

- `metrics-<escenario>-r<rep>.csv`: volcado **literal** de `NavPlannerMetrics.dumpCsv()`
  (bloques `METRICS`, `BUSQUEDA`, `ENTIDAD`), con `reset()` antes de cada escenario.
- `metrics-total.csv` / `metrics-agregado.csv`: cierre de campaña.

Reglas del agregador:

1. `motivo=no_soportado_*` o `metrics_csv=-` con todo a cero ⇒ **NO medido**. Se lista aparte y no
   entra en medias, sumas ni percentiles.
2. Si hay volcado, mandan sus filas `BUSQUEDA`/`ENTIDAD` (detalle exacto por búsqueda/entidad).
   El índice solo aporta `nanos_pared`, `ticks`, `candidatos` y `encolados`.
3. Si el índice y el volcado discrepan en expansiones/nanos (>1% y >5 unidades) se emite
   `AVISO_DISCREPANCIA` con los dos números; no se corrige en silencio.
4. `nanos_pared` es **cota superior** (preparación + ejecución) y nunca se suma a `nanos`.
5. Una fila del índice malformada (menos columnas) hace fallar el script con línea y recuento:
   mejor error que atribuir métricas a la columna equivocada.

Uso:

```powershell
# Solo lo medido por la campaña real
.\tools\perf\resumir-metricas.ps1

# Añadiendo línea base sintética y perfil A* antes/después
.\tools\perf\resumir-metricas.ps1 -IncluirBaseline -IncluirPerf

# Un fixture concreto (se marca como fixture y no se mezcla con evidencia)
.\tools\perf\resumir-metricas.ps1 -InputDir build\av\perf\fixture -IncluirFixture -Tag fixture
```

Salidas: `build/av/perf-resumen-<tag>.csv`, `build/av/perf-resumen-<tag>.md` y copias
`perf-resumen-ultimo.*`.

## 2. Matriz de escenarios de rendimiento

Mismo origen, mismo objetivo, mismo terreno y mismas condiciones dentro de cada escenario para poder
comparar versiones. Los nombres sintéticos son los de `NavSearchPerfTest`; los de runtime son los del
contrato con `navegacion`.

| escenario | geometría / condiciones | qué aísla | presupuesto | referencia sintética (expansiones) |
|---|---|---|---|---|
| `campo_abierto` | plano 32×32, (0,0)→(31,31) | coste base de A* y de la cola | 4096 | 63 |
| `obstaculos` | 18% de celdas bloqueadas deterministas + corredor garantizado | validación de aristas y desvíos | 4096 | 63 (corredor) |
| `muro` | muro recto con un hueco | rodeo mínimo, sin romper | 4096 | 32 |
| `laberinto` | serpentina 32×32 y 64×64 | búsquedas largas, frontera grande | 4096 | 528 / 2080 |
| `drop` (`paso_arriba`, `caida`) | plataforma + caída de 1–3 bloques, segura/insegura | DROP y rechazo de caída insegura | 900–1200 | 300 |
| `break` (`romper`) | muro rompible de 1–2 bloques | coste 12 de BREAK y stand de ataque | 1200 | — |
| `build` (`construir`) | foso/escalón de 1 bloque | coste 16 de BUILD y sucesor | 1200 | — |
| `combinado` (`romper_construir`, `caida_romper_construir`) | muro + foso + caída | interacción de transiciones caras | 1600 | — |
| `inaccesible` | meta rodeada de muro | agotamiento del frente, stale pops | 4096 | 1021 (1948 pops, 927 stale) |
| `presupuesto_insuficiente` | meta lejana con presupuesto 25 | corte duro y reanudación | 25 | 25 |
| `multientidad_N` | N sesiones intercaladas, objetivos disjuntos | coste por entidad y por tick | 4096 | 127 por entidad (N=4/8) |

Cobertura real de la campaña de `navegacion` (funcional, no de rendimiento): `campo_abierto`, `drop`,
`break`, `build`, `muro`, `inaccesible`, `presupuesto_insuficiente`; `combinado` como
`romper_construir`/`caida_romper_construir`; `obstaculos` como `bloqueo_recuperacion` +
`terreno_dinamico`. **No** hay `laberinto` ni `multientidad_2/4/8`; el agregador las marca como
`FALTA` si no aparecen, en vez de rellenarlas con cero.

## 3. Metodología de la medición sintética (núcleo A*)

`NavSearchPerfTest` (en `src/test`, sin Minecraft):

- calentamiento JIT explícito (`-DnavPerf.warmup`, por defecto 4),
- 11 repeticiones medidas (`-DnavPerf.reps`), mediana, mínimo y p95,
- A/B en el **mismo proceso y mismo grafo** contra `NavSearchBoxedBench`, copia congelada de
  `NavGraphSearch.Session` con `HashMap`/`HashSet` (la implementación de `HEAD ae28c19a`),
- hash FNV-1a de la ruta completa (estado, pies, transición, `actionPos`, `standPos`) + expansiones,
  pops, stale pops y reaperturas: si algo cambia, la prueba falla,
- atribución de fases (`Graph.expand` vs bookkeeping) con 5 repeticiones y mediana,
- sobrecarga de `NavPlannerMetrics` con ON/OFF (informativa; ver limitaciones).

```powershell
.\tools\perf\medir-nav.ps1                    # 11 reps, calentamiento 4
.\tools\perf\medir-nav.ps1 -Reps 21 -Warmup 6
```

`medir-nav.ps1` usa Gradle (y por tanto el `build/` compartido): **no lanzarlo mientras otro agente
está compilando**, igual que `tools/av/nav-baseline.ps1`. La tarea `navPerfTest` se registra sin tocar
`build.gradle` (verificado con `gradlew -I tools/perf/navperf.init.gradle tasks --all`).

No hay umbrales de tiempo que "aprueben": los números se imprimen; lo que se afirma es
determinismo, igualdad de rutas y coherencia de contadores.

## 4. Línea base existente (`build/av/nav-baseline-ultimo.txt`, 2026-11-23)

Lectura (los `nanos` de esa línea base se tomaron en frío, 1 repetición por modo; sirven de orden de
magnitud, no de benchmark):

- `campo_abierto`: 63 expansiones, 9 slices, 0,76–1,29 ms incremental.
- `muro_con_hueco`: 32 expansiones, 0,15–0,30 ms.
- `laberinto_serpentina`: 528 expansiones, 2,06–4,67 ms.
- `meta_inalcanzable`: 1021 expansiones pero **1948 pops, 927 stale (47,6% de los pops)** y
  0 reaperturas; 5,86–11,98 ms.
- `presupuesto_insuficiente`: 25 expansiones, 0,05–0,07 ms.
- Agregado 4 entidades: 252 expansiones, 0,88–2,03 ms, 4 reanudadas.
- Todas las repeticiones `sin_metricas` salen más rápidas que las de métricas ON, hasta 3×. La
  prueba solo comparaba ruta/expansiones, no tiempo; esa diferencia es **orden/JIT/GC**, no una
  medida válida de la sobrecarga de instrumentación (ver limitaciones).

Conclusiones de la auditoría de la instrumentación:

- `NavPlannerMetrics` mide bien lo que dice medir: `begin/advance/end/cancel` en los mismos puntos
  que crea/avanza/abandona; cancelación ≠ fallo; `nanos` = suma de slices, no tiempo de pared.
- Huecos reales: no hay `tick_fin`/duración en ticks de la búsqueda global retenida; no hay
  contadores de candidatos/aristas/encolados en el volcado (el planner los calcula, pero solo en
  `summary()`); `nodos_por_avance` se etiqueta como nodos/tick aunque en la ruta síncrona legacy el
  `advance` interno hace varios slices y cuenta como uno; las búsquedas abandonadas solo se ven como
  `ABIERTA` en `snapshot()`.
- `advanceGlobalSearch` usa `slice = 7` fijo mientras el resumen dice `slice = GLOBAL_EXPANSIONS_PER_TICK`
  (8): discrepancia de diagnóstico, sin efecto en comportamiento.
- La instrumentación no cambia rutas ni contadores (los tests ON/OFF lo verifican); su coste no se
  puede aislar de forma fiable con la línea base original.

## 5. Optimización aplicada: colecciones primitivas en `NavGraphSearch.Session`

Cambio aprobado por el coordinador: sustituir `HashMap<Long,Double>`, `HashMap<Long,Long>`,
`HashMap<Long,BlockPos>`, `HashMap<Long,Integer>`, `HashSet<Long>` por
`Long2DoubleOpenHashMap`, `Long2LongOpenHashMap`, `Long2ObjectOpenHashMap`, `Long2IntOpenHashMap` y
`LongOpenHashSet` de fastutil (ya presente en NeoForge/Minecraft 1.21.1). **No** se cambia el
algoritmo, el comparador de la cola (`f` y luego `h`), el presupuesto, las reaperturas ni el orden de
expansión. `bestG` usa `defaultReturnValue(NaN)` para resolver `containsKey`+`get` en una sola
consulta, sin cambiar el significado de "sin entrada".

Resultado sintético (A/B en el MISMO proceso, mismo grafo, 4 ejecuciones; mediana de las medianas de
producción frente al oráculo congelado con `HashMap`/`HashSet`):

| escenario | expansiones | boxed (oráculo) ns | producción (fastutil) ns | Δ mediana |
|---|---:|---:|---:|---:|
| presupuesto_insuficiente_25 | 25 | 83.400 | 61.425 | +26,4% (tamaño no fiable) |
| campo_abierto_32 | 63 | 233.050 | 224.175 | +3,8% (no concluyente) |
| muro_con_hueco_32 | 63 | 205.225 | 165.850 | +19,2% |
| obstaculos_dispersos_32 | 63 | 212.650 | 191.300 | +10,0% |
| meta_lejana_128 | 255 | 1.184.275 | 498.000 | **+58,0%** |
| multientidad_4 | 508 | 1.343.350 | 723.275 | **+46,2%** |
| laberinto_serpentina_32 | 528 | 1.973.550 | 467.775 | **+76,3%** |
| multientidad_8 | 1016 | 3.766.300 | 1.446.525 | **+61,6%** |
| meta_inalcanzable_32 | 1021 | 7.715.200 | 2.355.725 | **+69,5%** |
| laberinto_serpentina_64 | 2080 | 17.954.600 | 1.640.075 | **+90,9%** |

El delta es la reducción de tiempo de producción respecto al oráculo: 100·(boxed−producción)/boxed.
Las búsquedas de 25-63 expansiones están en el ruido (±10-30% entre ejecuciones); las de ≥255
expansiones son consistentes en las 4 ejecuciones. Además, una medición previa con los dos motores
**alternados** (`build/av/perf/antes3.txt`) dio +72,3% / +84,1% / +69,3% en laberinto 32 / 64 /
inalcanzable, corroborando que el orden de medición no explica el grueso. Detalle en
`build/av/perf/ab-final-*.txt`, `PERF_AB` de cada ejecución y `comparacion-antes-despues.csv`.

**No es una ganancia de campaña todavía.** El coste real de mundo incluye `validarArista` (consultas
de colisión por arista), que en el arnés sintético es trivial. La mejora de runtime debe medirse con
el arnés de `navegacion` (`metrics-*.csv`) antes de declararla. No se han tocado `NavPlanner`,
`NavExecutor` ni C4.

## 6. Limitaciones conocidas

- El sintético no modela colisiones de Minecraft: sobreestima el peso del bookkeeping y, por tanto,
  la mejora porcentual respecto al runtime real.
- La máquina es compartida con otros agentes: las medianas de búsquedas <100 expansiones varían
  ±30% entre procesos. Para esos tamaños el resultado es "sin cambio medible".
- `PERF_METRICAS` (ON/OFF) es informativo: el JIT de la propia instrumentación y el orden de las
  pasadas pesan más que el coste real; no se usa como conclusión.
- `NavPlannerMetrics` conserva 512 búsquedas cerradas: un volcado tras >512 cierres pierde detalle
  (los totales no).
- El arnés de navegación es funcional: `laberinto` y `multientidad_2/4/8` pueden faltar; el agregador
  lo declara en cobertura, no lo inventa.
- `nanos_pared` incluye preparación de mundo; para coste puro de ejecución hay que restar
  `preflight_nanos` del JSON del escenario.
- El smoke end-to-end de `medir-nav.ps1` coincidió con una compilación de otro agente y Gradle no pudo
  borrar `build/classes/java/main`; la tarea se registra bien y el binario se ejecutó por `javac`
  directo. Es un conflicto de `build/` compartido, no un fallo del perfil.
