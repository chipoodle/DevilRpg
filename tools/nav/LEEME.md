# tools/nav — Campaña RUNTIME de navegación (AV)

Arnés de escenarios funcionales reales para `NavPlanner`/`NavExecutor` de producción. **No es un test unitario**:
spawnea un `AggressiveZombieEntity` real, le fija `navExecutor().setObjective(goal)` y deja que la IA de producción
planifique, ejecute y recupere sobre geometrías de bloques reales en un mundo superflat desechable.

> **Estado de esta entrega:** harness + lanzador + contrato headless **implementados y compilados con `javac`**.
> La ejecución runtime (`runServer`) la lanza el coordinador; este agente no la ejecutó (restricción explícita).
> Por tanto **no hay veredicto runtime PASS/FAIL todavía**; no se declara PASS sin evidencia runtime.

## Ficheros

| Fichero | Papel |
|---|---|
| `tools/nav/NavScenarioHarness.java` | Fuente canónica del arnés (paquete `com.chipoodle.devilrpg.debug`). |
| `tools/nav/lanzar-nav-harness.ps1` | Copia el arnés a `debug/`, prepara `build/av/nav-<run>/game` y lanza `runServer`. |
| `src/test/java/com/chipoodle/devilrpg/entity/nav/NavScenarioContractTest.java` | Contrato headless del catálogo (253 comprobaciones, sin Minecraft). |
| `build/av/informe-navegacion.md` | Informe de la campaña (implementación, evidencias, riesgos, limitaciones). |
| `build/av/nav-fixture/` | Fixture de parser para `tools/perf/resumir-metricas.ps1` (NO es medida real). |

`src/main/java/.../debug/` está en `.gitignore`; el lanzador crea ahí la copia temporal. El build normal excluye
`debug/**`, y **cualquier build de diagnóstico distinto de `-PnavHarness=true` excluye explícitamente
`NavScenarioHarness.java`** para que no compita por `ServerStartedEvent`/`ServerTickEvent` ni detenga el servidor
de otro arnés. Además el arnés es no-op si falta `-PnavHarnessRun`.

## Comando exacto (coordinador)

```powershell
# Campaña completa (21 escenarios, ~21.400 ticks máximos ≈ 18 min + arranque)
.\tools\nav\lanzar-nav-harness.ps1 -RunId runtime-20261123-1900

# Humo corto (2 escenarios, 50% de presupuesto)
.\tools\nav\lanzar-nav-harness.ps1 -RunId smoke-20261123-1900 -Scenarios paso_arriba,caida -Escala 50

# Repetición para el agregador de rendimiento (proceso/servidor nuevo; no repite in-session)
.\tools\nav\lanzar-nav-harness.ps1 -RunId runtime-r2 -Rep 2
```

El lanzador llama internamente:

```
gradlew.bat runServer -PnavHarness=true -PnavHarnessRun=<run> -PnavHarnessRep=<rep>
  -PnavHarnessScenarios=<ids|all> -PnavHarnessEscala=<10..400>
  -Pa2nGameDirectory=build/av/nav-<run>/game -Pa2nTraceNav=true -PnavMetrics=true
  --no-daemon --offline --console=plain
```

No toca `run/`, `run/saves`, `build/c4n` ni persistencia de aldea. El mundo vive en
`build/av/nav-<run>/game` (`level-type=minecraft:flat`, seed fija, dificultad normal, sin spawning).

## Escenarios (21) y criterios

Cada escenario declara geometría, origen, objetivo, acción/transición esperada, presupuesto de ticks, invariantes de
seguridad y criterios PASS/FAIL. El **preflight** ejecuta el planner real sobre el mundo ya construido: si la ruta no
contiene la transición esperada, el escenario es `INVALID` (no PASS). El preflight se desactiva solo en `tunel`
(plan subordinado por pasos, no una ruta estática); `muro` y las combinaciones sí lo exigen.

| id | geometría | esperado | ticks |
|---|---|---|---:|
| campo_abierto | plataforma abierta, spawn −10 → goal +12 | WALK, llegada | 600 |
| paso_arriba | escalón de 1 bloque | STEP_UP, llegada | 600 |
| caida | borde con caída de 3 | DROP, llegada | 700 |
| romper | panel de cristal con techo a +2 | BREAK, panel aire, llegada | 700 |
| construir | hueco de 2 con muros laterales | BUILD verificado, travesía, llegada | 1200 |
| paso_arriba_romper | escalón + panel | STEP_UP+BREAK, llegada | 1100 |
| paso_arriba_construir | escalón + hueco | STEP_UP+BUILD, llegada | 1400 |
| caida_romper | caída 3 + panel inferior | DROP+BREAK, llegada | 1100 |
| caida_construir | hueco de 1 (BUILD) + caída segura de 3 | BUILD + DROP, llegada | 1200 |
| romper_construir | panel + hueco | BREAK+BUILD, llegada | 1500 |
| caida_romper_construir | caída + panel + hueco | DROP+BREAK+BUILD, llegada | 1800 |
| escalera | 3 escalones naturales | STEP_UP ×3, llegada | 800 |
| columna | pozo 1×1 a sala inferior | DROP, llegada | 700 |
| muro | 2 paneles consecutivos con techo | BREAK ×2, ambos aire, llegada | 1200 |
| tunel | muro de tierra 3×3 (una activación de túnel) | TUNNEL (`tunelDiag.pasos>=1`), llegada | 1800 |
| terreno_dinamico | barrera puesta tick 30 / retirada tick 90 | replanificación (≥2 planes), llegada | 1000 |
| inaccesible | bolsa 1×2 sellada en bedrock | sin llegada; preflight global no alcanza la misión | 1800 |
| astar_reanudacion | A* global incremental real (corredor de 56) | GLOBAL avances≥2, exp>8, slice≤8, reanudada | 220 |
| presupuesto_agotado | `plan(..., maxExpansions=2)` directo | hitBudget, exp≤2, métrica agotado≥1 | 40 |
| bloqueo_recuperacion | barrera permanente + ramal en U | ≥2 planes, llegada por ramal | 1500 |
| limites | `plan` y `planGlobal` directos con presupuesto 16 | hitBudget en ambas, exp≤16 | 40 |

Criterios comunes: preflight, acciones observadas, llegada/fallo, movimiento real, salud intacta, Y y XZ dentro del
área, **cero cambios de bloque fuera de los permitidos** (snapshot del área + celdas construidas + objetivos de
rotura + barreras del arnés), `expansionsLast<=900`, y extras A*/recuperación/túnel/presupuesto. Para BUILD se exige
colocación verificada en mundo y **travesía física** (`bloquesTravesados>=1`). Para BREAK, el bloque objetivo debe
estar en aire. Para TUNNEL, `tunelDiag().pasos>=1`.

## Evidencia que produce

`build/av/nav-<run>/`:

- `escenario-<id>-r<rep>.json`: geometría, preflight, observado, criterios, métricas y veredicto.
- `metrics-<id>-r<rep>.csv`: volcado **literal** de `NavPlannerMetrics.dumpCsv()` tras `reset()` por escenario.
- `nav-escenarios-<timestamp>.csv` / `nav-escenarios-ultimo.csv`: índice acordado con rendimiento.
- `metrics-agregado.csv` (suma por escenario), `metrics-total.csv` (dumpCsv del cierre).
- `nav-trace.log`: eventos de producción vía `NavTrace` (si `-Pa2nTraceNav=true`).
- `resultado.json`, `fin.txt` (`NAV_PASS`/`NAV_FAIL`), `launcher.log`, `launch.cmd`, `sha.txt`,
  `launcher-manifest.txt`.

La línea `NAVSCN,<columnas>` se emite a stdout por escenario; `METRICS,<dumpLinea>` y `NAV_FIN,<veredicto>` al cierre.
`nanos_pared` incluye preparación+spawn+preflight: es **cota superior**, no coste puro del planner (el JSON guarda
`preflight.nanos` para restarlo).

## Verificación headless ya realizada

```powershell
# (manual, sin Gradle: evita el lock de otro runServer)
javac -encoding UTF-8 -proc:none -cp <classpath> -d build/av/nav-check-out tools/nav/NavScenarioHarness.java
javac -encoding UTF-8 -proc:none -cp ... -d build/av/nav-check-out src/test/java/.../NavScenarioContractTest.java
java -cp "build/av/nav-check-out;<classpath>" com.chipoodle.devilrpg.entity.nav.NavScenarioContractTest
# -> NAV_SCENARIO_CONTRACT_TEST: OK (253 comprobaciones)
```

El contrato comprueba: 21 ids exactos y sin duplicados, builders = catálogo, transiciones/acciones/presupuestos/radios
declarados, lanzador y flags Gradle, aislamiento del arnés, instrumentación (`enabled/reset/dumpCsv`), cabecera del
índice, `NAVSCN`/`METRICS`/`NAV_FIN`, y las políticas puras que sostienen las geometrías (STEP_UP +1, BUILD +1/−3,
DROP −3, rechazo −4/+2).

Comando Gradle (cuando no haya otro build/runServer activo):

```powershell
.\gradlew.bat navScenarioContractTest --offline --console=plain
```

## Correcciones tras el primer runtime (run `nav-full-20261123-1920`)

- **Producción `NavExecutor`**: BUILD plano se quedaba a 1 celda del pie (`pathDone=true`, `bloquesTravesados=0`).
  Se añadió `NavBuildTransitionPolicy.shouldNudgeFeet` y un nudge acotado `BUILD_FEET_NUDGE` reutilizando
  `NavAdjacentApproachPolicy` (regresión en `NavVerticalBuildPolicyTest`). `construir` y las combinaciones pasan.
- **Producción `NavExecutor.terrainChanged()`**: resetea `nextRecoveryAt`/nodo de recuperación; sin esto, retirar la
  barrera de `terreno_dinamico` dejaba una espera de hasta 400 ticks del terreno viejo. El escenario pasa.
- **Arnés**: el preflight global ahora exige `alcanzoMision` (una ruta-frente no es una ruta); `muro` separa los dos
  paneles 3 celdas (el stand del segundo es abierto); `tunel` usa muro de 3 celdas (una activación de 3 pasos);
  `caida_construir` es BUILD+DROP con aristas normales (sin la máquina A2); `inaccesible` no exige FAILED en un
  presupuesto corto: exige que el preflight NO alcance la misión y que el ejecutor no llegue; `astar_reanudacion`
  mide `planGlobalIncremental` retenido (el corredor plano hace que el ejecutor prefiera escalonado local).

Resultado de los runs de corrección: `fix1` 8/10 PASS, `fix2` 2/2 PASS (los 10 escenarios que fallaban, PASS) y
**campaña completa `full2-20261123-1946` = NAV_PASS 21/21** (0 FAIL, 0 INVALID, 0 TIMEOUT).

## Riesgos conocidos

1. **`tunel`**: el muro de 3 celdas se abre en UNA activación (3 pasos); un muro más grueso necesitaría otra
   activación/cooldown y el plan subordinado podría no repetirse.
2. **`muro`**: el preflight exige la ruta a la misión con dos BREAK; los paneles deben seguir separados por celdas
   abiertas para que el stand del segundo sea válido.
3. **`inaccesible`**: el ejecutor puede permanecer en RECOVERING sin llegar (recuperación acotada); el criterio no
   exige FAILED, sino preflight sin alcance + cero llegada + cero cambios inesperados.
4. **Geometría**: cualquier ruta alternativa más barata que la esperada se detecta como fallo de criterio; el
   preflight (donde está activo) la marca `INVALID` antes de ejecutar.

## Limitaciones de la campaña

- No demuestra multientidad, laberintos largos, combate, asedio, puertas ni agua; no sustituye A2/C4.
- No mide rendimiento de producción: mide acciones/transiciones y vuelca métricas del planner; `nanos_pared` es cota
  superior.
- Una sola entidad por escenario; escenarios en áreas separadas del mismo mundo, ejecutados en serie.
- `astar_reanudacion` mide la capa global directamente; en corredores planos el ejecutor usa escalonado local. La
  integración ejecutor→global se cubre en `caida_construir`/A2, no en ese escenario.
