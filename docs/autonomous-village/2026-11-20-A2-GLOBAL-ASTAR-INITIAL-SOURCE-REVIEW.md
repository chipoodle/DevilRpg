# A2 Run1 — revisión inicial de convergencia A* global (2026-11-20)

## Estado y alcance

- Run revisado: `a2n-20261120-140611`.
- Resultado runtime: FAIL, 0/8 entradas estrictas, 2/8 perímetro.
- HEAD probado: `3d7e6bf4d44f07b3f91068a493bb9cb1ace7fc58`.
- HEAD documental posterior: `306f33d706012ca44c0c85e6a83c8500a370636c`.
- Producción: `c520198c0b89e5da0d6f2e760b22e8d43fcec7d7`.
- D3 BUILD: no medido en runtime; no se generó ningún nodo BUILD.
- Integridad de mundos protegidos: PASS según reporte runtime.
- No se ha autorizado otra ejecución. Esta revisión es exclusivamente de fuentes y artefactos ya existentes.

## Hechos confirmados por el reporte

1. IDX3/4/5 terminan búsquedas globales con `exp=1`, `aristas=56`, `encolados=0` y frente agotado.
2. IDX1/6/7/8 permanecen en búsquedas globales pendientes, respectivamente con aproximadamente 5263/5218/6890/6542 expansiones.
3. Telemetría agregada: START 81, COMPLETE 59, CANCEL 15, RESTART 3; A2_LOCAL_STAIR_WAIT 545.
4. Ningún BUILD fue propuesto ni ejecutado. El resultado no demuestra fallo ni éxito runtime de D3.
5. El resumen no contiene las 56 razones de rechazo por candidato de IDX3/4/5 ni los detalles de cada slice de las búsquedas pendientes. Sin esos eventos no es posible atribuir el frente agotado a terreno, protección, geometría, clasificación o estado de búsqueda.

## Revisión directa del código en HEAD 306f33d

### 1. Primera expansión vacía: el fallo está antes de la cola A*

En `NavPlanner.planGlobalInternal`, el adaptador genera 8 direcciones horizontales por `(2 * verticalRange + 1)` alturas y llama a `validarArista`. Solo las aristas válidas se entregan a `NavGraphSearch.Session`.

Por tanto, `exp=1 / aristas=56 / encolados=0` significa que la expansión inicial examinó 56 candidatos y el adaptador no admitió ninguno. No es evidencia de que A* haya agotado un presupuesto ni de que la cola haya perdido una ruta. La causa concreta está en los campos `kind`, `rejection`, `fits`, `hasSupport`, `headClear`, `protected`, `chunkLoaded` y `feetFits` de `GLOBAL_FIRST_EXPANSION_CANDIDATE`.

**Dato pendiente imprescindible:** extraer del `nav-trace.log` existente los eventos completos de primera expansión para IDX3, IDX4 e IDX5, agrupados por UUID y búsqueda. No volver a ejecutar Minecraft para obtenerlos.

### 2. Estado DROP y altura de pies no están normalizados

En `NavPlanner.sucesorCanonico`, las transiciones STEP_UP y BUILD producen un estado en la celda de pies resultante; DROP conserva `candidate` como estado aunque `piesHasta` sea la altura real del aterrizaje. `NavGraphSearch` usa el estado para `heuristic(state)`, `distance(state)` y para formar la banda Y de candidatos siguientes; el adaptador usa `feet` por separado. `NavRouteNodePolicy.routePosition` corrige la posición física del nodo DROP después de reconstruir la ruta, pero eso no corrige la heurística ni el centro vertical de expansiones posteriores.

Esto es una inconsistencia arquitectónica demostrable y una candidata a defecto de cobertura/convergencia tras una transición DROP. **No explica por sí sola los casos exp=1 de la primera expansión**, porque esos comienzan en el estado inicial, antes de cualquier DROP. Debe tratarse como una línea de investigación separada y validarse con una prueba determinista antes de cambiar producción.

### 3. Cola incremental y re-aperturas

`NavGraphSearch.Session.advance` permite reabrir un estado al encontrar un `g` mejor, pero los registros antiguos del mismo estado permanecen en OPEN. Al extraer, se comprueba `closed`, pero no se descarta explícitamente un registro cuyo `g` ya no coincide con `bestG`. Los duplicados cerrados se saltan y el slice limita también los pops a dos veces las expansiones solicitadas.

Esto puede consumir pops de un slice y reducir el trabajo útil cuando hay muchas re-aperturas. No hay evidencia en el resumen runtime de que sea la causa dominante. Hace falta medir pops, stale pops y expansiones útiles por slice antes de tocarlo.

## Plan de investigación, sin runtime

1. Recuperar de los artefactos ya producidos las trazas completas de primera expansión para IDX3/4/5; contabilizar rechazos por `Rechazo` y `NavObstruction.Kind`, y comparar los 56 candidatos con el snapshot de terreno disponible en el log.
2. Para IDX1/6/7/8, reconstruir por UUID cada START → PENDING → CANCEL/RESTART/COMPLETE, incluyendo `start`, `goal`, `exp`, `frontier`, razón de cancelación, número de slices, cambios de posición y cambios de terreno.
3. Determinar si los 15 CANCEL y 3 RESTART son esperados (movimiento/terreno/misión) o si el propietario invalida búsquedas que deberían conservarse.
4. Añadir pruebas puras para DROP que comprueben que estado de búsqueda, pies, heurística, distancia y sucesores describen la misma celda física; evaluar la migración de DROP a estado canónico de aterrizaje sin alterar exclusiones ni ejecución.
5. Añadir pruebas puras para re-aperturas y stale OPEN entries; medir pops frente a expansiones útiles.
6. Solo después de disponer de evidencia reproducible, implementar la corrección mínima en producción y pedir validación STATIC ONLY a Harness.

## Límites

- No se modificó producción, tests, fixtures ni mundos.
- No se ejecutó build, tests, lint, Minecraft ni Harness runtime durante esta revisión.
- No se autorizan A2 Run2, C4, Full12K ni otro runtime.
- A2 permanece OPEN; C4 no revalidado; D3 BUILD runtime-unmeasured.
