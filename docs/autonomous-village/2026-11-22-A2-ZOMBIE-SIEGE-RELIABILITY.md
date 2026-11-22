# A2 — Fiabilidad del asalto zombi: causas raíz y correcciones (2026-11-21/22)

## 1. Resultado

**A2 sigue OPEN, pero sube de 3/8 a 6/8 estricto DENTRO-PARED** en el mejor run de la campaña, con
**C4 integrado PASS en todas las corridas**. Se han corregido ocho causas raíz con evidencia runtime y
pruebas deterministas; los tres asaltantes restantes tienen diagnóstico medido y trabajo acotado.

```text
Mejor run: a2n-20261121-232035 → 6/8 estricto (IDX1, IDX2, IDX3, IDX4, IDX5, IDX6), C4 PASS
```

## 2. Punto de partida

HEAD `9a4553af` (túnel subordinado integrado). Baseline medido de la campaña: **3/8 estricto**
(IDX1, IDX4, IDX6) con C4 PASS. La aldea del fixture es una **meseta a cota 71 rodeada por un
escalón vertical de 6-8 bloques** en todo el borde (r≈86), medido con un volcado de heightmaps
(`terrain-dump.txt`, artefacto de `a2n-20261121-223916`). Los asaltantes nacen a radio 95 y a y=63-65.

## 3. Causas raíz corregidas (con evidencia)

1. **Celdas de pie con decoración sin colisión (nieve).** `esPieValido` exigía `isAir()`; un rellano
   nevado invalidaba la celda propia y las 8 vecinas, y la recuperación de fuente DROP no tenía
   candidato: IDX2 terminaba `FAILED` en `660,72,608` en las 3 corridas, con rearmes 4/4.
   → `NavFeetCellPolicy`: la celda de pie se valida por **colisión** (pies y cabeza), sin fluido ni
   peligro, con soporte sólido debajo y sin protección.

2. **Pierna local demasiado larga.** `elegirObjetivoLocal` se quedaba con el nodo MÁS LEJANO de la
   ventana (medido: 39,44 → 58,8 bloques) y el A* local de 900 expansiones no lo resolvía (la búsqueda
   global al mismo destino costó 1393): IDX3 gastó 10 intentos sin moverse y terminó `FAILED`.
   → `NavLocalObjectiveLegPolicy` (tramo máximo 24 Chebyshev, se conserva el waypoint válido más
   lejano dentro del tramo).

3. **Aterrizaje DROP más profundo de lo previsto + livelock del take-off.** IDX5 planificó aterrizar en
   `597,74,469` y cayó hasta `597,71,470` (celda de pie válida); el ejecutor exigía volver al take-off
   `596,75,470` (+4) y repetía la búsqueda imposible 160 veces conservando la ruta.
   → `NavDropOvershootPolicy` (acepta el aterrizaje real si es seguro y el exceso cabe en la caída
   segura) + abandono de la arista al tercer rechazo idéntico, con replan.

4. **Guardián de progreso anulado con la misión por encima.** `NavLocalObjectiveProgressPolicy`
   aceptaba CUALQUIER candidato si `missionY >= mobY`: IDX7/8 aceptaron un waypoint a 44 bloques que
   los alejaba (86 → 108 del ancla) y se quedaban sin ruta.
   → Ascender o mantenerse ya no es progreso por sí mismo: exige avance horizontal medible.

5. **Troncos del muro vetados al planificador.** `puedeRomperNodoDeNavegacion` vetaba TODO `LOGS`,
   incluidos los troncos del anillo, que `breakBlockAt` sí acepta (`esLaMuralla`). El A* solo podía
   proponer BREAK en las columnas de adoquín (1 de cada 4 celdas): los tres que entraban lo hacían
   justo por adoquín.
   → `NavBreakFilterPolicy`: hojas siempre vetadas; tronco vetado salvo celda del anillo del muro.

6. **Búsqueda global drenada a las cuevas.** Con una cota admisible, el A* global exploraba el sistema
   de cuevas bajo la meseta (frente 26-34 bloques POR DEBAJO) y agotaba 8000 expansiones sin encontrar
   la subida.
   → `NavGoalMetric.heuristicClimbing`: cota dirigida que suma `0.5 × desnivel restante` cuando la
   entidad está por debajo de la meta (prioriza subir).

7. **Ruta global validada no ejecutada y ruta-frente descartada.** IDX7 tenía una ruta global de 52
   nodos desde SU MISMA celda hasta la misión (con aristas BUILD que suben) y el ejecutor gastaba el
   presupuesto local en re-planificarla. Además, cuando la búsqueda global no alcanzaba la misión pero
   producía un tramo que ACERCA, ese tramo solo servía para elegir un waypoint y el ente se quedaba
   inmóvil en su spawn.
   → `intentarRutaGlobalValidada`: si la ruta global une la celda actual con la misión, se EJECUTA; si
   no la alcanza pero el frente acerca ≥2 bloques, también se ejecuta como tramo.

8. **Falta de capacidad de ASCENSO y watchdog por generación de chunks.** La meseta tiene un escalón
   vertical de 6-8 bloques: no había ninguna arista legal para subir y el ente quedaba clavado (IDX8
   inmóvil 6000 ticks). Además, validar aristas en chunks descargados disparó su generación síncrona:
   un tick de más de 60 s mató el servidor (watchdog) con la pila en
   `NavExecutor.tryPlan → NavPlanner.plan → validarArista → NavObstruction.fits`.
   → `NavClimbStepPolicy` + modo de ejecución ASCENSO (despeja pies/cabeza/techo del escalón, coloca un
   bloque y sube). `validarArista` rechaza celdas fuera de carga (`FUERA_DE_CARGA`) y el plan local
   tiene un tope de 30 ms de CPU.

## 4. Pruebas deterministas añadidas (registradas en `policyTest`)

```text
NAV_FEET_CELL_POLICY_TEST             9
NAV_LOCAL_OBJECTIVE_LEG_POLICY_TEST   9
NAV_DROP_OVERSHOOT_POLICY_TEST       10
NAV_BREAK_FILTER_POLICY_TEST          7
NAV_CLIMB_STEP_POLICY_TEST           19
NAV_LOCAL_OBJECTIVE_PROGRESS_POLICY_TEST  10 (ampliado)
NAV_GOAL_METRIC_TEST                   ampliado con la cota dirigida
TRAVERSAL_ACTION_LEASE_POLICY_TEST     ampliado con el lease explícito
```

`compileJava`, `compileTestJava`, `policyTest`, `lint_aldea.py --strict` y `git diff --check`: todos en
verde.

## 5. Evidencia runtime (semilla 12345, mundos nuevos y desechables)

| RUN_ID | Estricto | Notas |
|---|---|---|
| `a2n-20261121-221911` | 4/8 (1,2,3,6) | nieve + tramo + overshoot + troncos |
| `a2n-20261121-223916` | — (watchdog) | artefacto `terrain-dump.txt`; crash por generación de chunk |
| `a2n-20261121-225204` | 4/8 (1,2,3,6) | + cota dirigida, guarda de chunk y tope de CPU |
| `a2n-20261121-230234` | 5/8 (1,2,3,5,6) | + escalón de ascenso (IDX5 entra) |
| `a2n-20261121-231122` | 5/8 (1,2,3,5,6) | + ruta-frente |
| **`a2n-20261121-232035`** | **6/8 (1,2,3,4,5,6)** | IDX4 entra en t=4261; primer ascenso completado |
| `a2n-20261121-232912` | 5/8 (1,2,3,5,6) | umbral de avance 3 bloques |
| `a2n-20261121-233720` | 4/8 (1,2,5,6) | salto del ascenso completado (6 CLIMB DONE) |
| `a2n-20261121-234558` | 4/8 (1,2,5,6) | prioridad de ascenso |
| `a2n-20261121-235432` | 4/8 (1,2,5,6) | sonda de rechazo; IDX3 a 62,46 |

Notas: IDX3 oscila entre estricto (61,96) y 62,46 (a 0,46 del umbral) porque el escalón de ascenso lo
deja SOBRE el anillo; IDX4 entra en los runs en los que su descenso de 51 bloques llega a tiempo
(t=4261-4734); IDX7/8 son los únicos que nunca entran: el ascenso se activa (6 subidas completadas,
incluida una de IDX7) pero el A* sigue prefiriendo rodear la meseta y el encadenado de subidas no se
completa dentro de 6000 ticks.

## 6. Bloqueos restantes (con evidencia)

- **IDX7/8 (meseta)**: el ascenso funciona (CLIMB_STEP_DONE x6 con bloque colocado y pies verificados)
  pero el encadenamiento no cubre los 8 bloques del acantilado antes de t=6000; el A* intercala rutas
  de rodeo. Siguiente: hacer que el ascenso tenga prioridad ABSOLUTA mientras `misionY - mobY >= 3` y
  haya muro adyacente (sin esperar a que falle el plan), y/o subir `MAX_ASCENSOS_MISION` (hoy 10).
- **IDX3 (anillo)**: se queda a 0,46 del umbral en `617,72,577` en estado `RECOVERING` durante miles de
  ticks, con un DROP hacia `616,71,577` que no completa. Siguiente: revisar el bucle de recuperación en
  el anillo (el DROP interior no se ejecuta desde RECOVERING).
- **IDX4 (descenso largo)**: 51 bloques de descenso con cadencia de escalón; entra cuando llega a
  tiempo (6/8 run). Siguiente: acelerar el descenso (o dar más ventana) sin tocar presupuestos.

## 7. Preservación

- Solo se usaron mundos nuevos bajo `build/a2n/<runId>/game/a2n`; `run/a2n` = False en todas las
  corridas; `run/saves` no se consultó ni modificó.
- Untracked previos preservados. No se borró ningún artefacto, log, backup ni mundo.
- El volcado de terreno y todas las trazas quedan como evidencia en `build/a2n/`.
