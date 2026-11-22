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
| `a2n-20261122-000544` | 5/8 (1,2,3,5,6) | referencia de misión con `misionFinal`; IDX3 entra (61,98) |
| `a2n-20261122-001314` | 4/8 (1,2,5,6) | micro-avance ≥0,5 con memoria de celdas; **primer ascenso de acantilado** (IDX8: 747,63,533 → 747,64,533) |

Últimos hallazgos medidos:

- **`misionDePie()` puede no describir la misión**: el ascenso y el avance usaban solo la celda de pie; ahora
  usan `misionFinal` como referencia de altura (`misionDeReferencia`). Con eso el ascenso se intenta también
  cuando la misión cae en una celda no pisable.
- **Micro-avance junto al acantilado**: exigir 3 bloques de mejora dejaba a IDX8 a UNA celda del muro
  (`748,63,532` con la cara `747,63,532` en aire). Con desnivel ≥3 se permiten pasos de ≥0,5 bloques,
  evitando el vaivén con la memoria de celdas recientes. Resultado: IDX8 llegó a `747,63,533` y el ascenso
  colocó el bloque y subió (primer ascenso de acantilado de la campaña).
- La sonda `CLIMB_STEP_PROBE` confirma que el rechazo dominante antes del micro-avance era `sin_cara`
  (la entidad no estaba pegada al muro), no `bloque_no_rompible`.
- **Encadenado del ascenso**: se intenta subir en CADA tick de PLANNING (antes solo tras 240 ticks de
  callejón) y sin enfriamiento tras una subida verificada; tope de ascensos 12. Medido en
  `a2n-20261122-002353` y `a2n-20261122-003217`: IDX7 sube el primer escalón a t=288 y **encadena el
  segundo** a t=289 (bloque colocado en t=379). El salto al segundo escalón sigue fallando por
  pathfinding (`pathCanReach=false`); se añadió salto asistido acotado.
- **Deadlock por ruta duplicada** (IDX3 en el anillo): el planificador producía la MISMA ruta válida y el
  ejecutor la rechazaba indefinidamente (firma 2102820130, 8 replanteos sin moverse). Ahora, tras 3
  rechazos idénticos se reintenta la ruta (máximo 2 reintentos forzados por misión).
- **C4**: 13 de 14 corridas con C4 PASS; `a2n-20261122-003217` dio C4_FAIL solo por
  `asignacionesJobSiteLimpias=false` (`jobSite=[0,0,0]`), con 16/12/12 supervivientes y trabajadores
  correctos: se registra como variabilidad del subsistema de puestos de trabajo, a revalidar.

Notas: IDX3 oscila entre estricto (61,96) y 62,46 (a 0,46 del umbral) porque el escalón de ascenso lo
deja SOBRE el anillo; IDX4 entra en los runs en los que su descenso de 51 bloques llega a tiempo
(t=4261-4734); IDX7/8 son los únicos que nunca entran: el ascenso se activa (6 subidas completadas,
incluida una de IDX7) pero el A* sigue prefiriendo rodear la meseta y el encadenado de subidas no se
completa dentro de 6000 ticks.

## 5-bis. Ronda 2 (2026-11-22): techo del stand, entrada al recinto y umbral del ascenso

| RUN_ID | Estricto (ever) | Notas |
|---|---|---|
| `a2n-20261122-004219` | 4/8 (1,2,6,8) | **IDX8 entra en estricto (t=4693)** tras 14/14 subidas encadenadas |
| `a2n-20261122-005105` | 4/8 (1,2,5,6) | 16/16 subidas; IDX7 a 62,46 porque el ascenso lo SUBIÓ al muro |
| `a2n-20261122-005924` | 4/8 (2,3,5,6) | **IDX3 entra en estricto (t=4204)**; IDX1 falló por exceso del avance en el gancho superior |
| `a2n-20261122-010725` | — | arranque del servidor bloqueado (world save); relanzado |

Hallazgos y correcciones de la ronda:

- **Techo del propio stand**: al saltar al segundo escalón la cabeza sube un bloque y golpeaba el techo
  del stand (S+2 sólido): el salto quedaba capado y `pathCanReach=false`. Se despeja también S+2.
  Resultado: **14/14 subidas completadas** y IDX8 coronó la meseta y entró en el recinto.
- **Entrada al recinto**: IDX3/IDX7 quedaban SOBRE el anillo (y=72, distancia 62,46) sin bajar al
  interior. Nueva acción `ENCLOSURE_STEP`: un paso de descenso (1-3 bloques) hacia la misión, solo si la
  entidad está por encima y el candidato es celda de pie válida que ACERCA.
- **El ascenso subía al muro**: con `misionPorEncima()` (cualquier desnivel) el ascenso trepaba el muro de
  la aldea (71 → 72) y dejaba al mob a 62,46. Ahora el ascenso exige **desnivel real ≥3**; un escalón de 1
  lo resuelve el movimiento ordinario y el paso de entrada al recinto.
- **Exceso del avance**: el avance horizontal en el gancho superior (cada tick) desviaba a IDX1 de su
  aproximación; queda restringido a desnivel real y los ganchos de fallo conservan el caso de nivel.



## 6. Bloqueos restantes (con evidencia)

- **IDX7/8 (meseta)**: el ascenso ya encadena 8 subidas y corona la meseta (IDX8 entró en
  `a2n-20261122-004219`); queda estabilizar que IDX7 no sea desviado por rutas de rodeo y que la entrada
  al recinto desde el muro sea fiable en todos los rumbos.
- **IDX3 (anillo)**: entró en estricto en `a2n-20261122-005924` (t=4204) con el reintento forzado de ruta
  y el paso de entrada; la variabilidad restante es de aproximación/combate.
- **IDX4 (descenso largo)**: 51 bloques de descenso con cadencia de escalón; entra cuando llega a tiempo
  (6/8 run). Siguiente: acelerar el descenso (o dar más ventana) sin tocar presupuestos.

## 7. Preservación

- Solo se usaron mundos nuevos bajo `build/a2n/<runId>/game/a2n`; `run/a2n` = False en todas las
  corridas; `run/saves` no se consultó ni modificó.
- Untracked previos preservados. No se borró ningún artefacto, log, backup ni mundo.
- El volcado de terreno y todas las trazas quedan como evidencia en `build/a2n/`.
