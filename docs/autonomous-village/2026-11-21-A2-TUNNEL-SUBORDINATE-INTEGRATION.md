# A2 — Túnel subordinado: integración en `NavExecutor` (2026-11-21)

## 1. Resultado

**A2 sigue OPEN: 3/8 estricto, 3/8 perímetro, C4 integrado PASS.** La integración del túnel
subordinado está implementada, probada y medida; no degrada el resultado y no sustituye ninguna
puerta de seguridad. Los cinco asaltantes restantes fallan por causas **ajenas al túnel**
(DROP source-cell recovery, escalonado/descenso y deambulación), documentadas con evidencia.

HEAD de partida: `fea54ef73520b5e28e6426c4696d7f4fa1da8fec`.

## 2. Decisión de arquitectura aplicada (opción 1 subordinada)

```text
TunnelActionPlanner = planificador PURO de acciones de terreno
NavExecutor         = ÚNICA autoridad de movimiento y de acción
```

- `TunnelActionPlanner` no llama a `moveTo`, ni a `MoveControl`, ni teletransporta, ni modifica el mundo.
- `NavExecutor` pide el plan, y ejecuta **una acción por tick** con el mismo lease de terreno que la ruta.
- Cualquier fallo descarta el plan COMPLETO, invalida ruta/global y replanifica. No hay acción improvisada.

## 3. Implementación

### 3.1 Piezas nuevas (puras, probadas)

- `TunnelExecutionPolicy`: cursor inmutable del plan (`planSize`, `stepIndex`, `Phase`).
  - `afterBreak(cursor, airVerified)`: un break no verificado devuelve el MISMO cursor (no avanza).
  - `afterMove(cursor, occupiedVerified)`: solo la ocupación física verificada consume el paso.
  - `breakTarget`/`stepSibling`/`standCell`/`postCell`: única fuente de verdad de qué bloque puede
    romperse en cada fase.
  - `lineOfSightAccepted(firstHit, requested, sibling)`: la línea de visión admite el bloque pedido o el
    OTRO bloque de la misma apertura de 2 de alto; un tercer bloque o un rayo sin impacto rechaza.
- `TunnelPlanPolicy`: topes duros y dirección.
  - `MAX_STEPS_PER_PLAN = 3`, `MAX_PLANS_PER_MISSION = 4`, `MAX_STEPS_PER_MISSION = 6`.
  - `PLAN_COOLDOWN_TICKS = 60`, `PHASE_WAIT_TICKS = 60` (la cadencia del actuador es 40), `MOVE_WAIT_TICKS = 80`.
  - `directionsToward(...)`: solo cardinales, ordenadas por progreso horizontal real.
  - `constructionHasPriority(...)`: el escalonado manda mientras sea viable.
- `TraversalActionLeasePolicy.canAcquireTunnel(...)`: lease por bloque exacto, una acción de terreno por
  entidad y tick, compartiendo el mismo par tick/usado que el lease de ruta.

### 3.2 `NavExecutor`

- Estado: `tunnelPlan`, `tunnelCursor`, `tunnelPhaseDeadline`, `tunnelNextPlanAt`, `tunnelPlansThisMission`,
  `tunnelStepsThisMission`, `tunnelLastDiscard` y el detector de callejón sostenido.
- Flujo por paso:
  1. ocupa exactamente `stand` (solo se exige en fases BREAK);
  2. lease BREAK para `feetBlock` → rompe por el MISMO `recoveryAction` (`romperNodoDeRuta` →
     `breakBlockAt`) → verifica Aire;
  3. lease BREAK para `headBlock` → rompe → verifica Aire;
  4. emite el ÚNICO movimiento a `post` y espera ocupación exacta, soporte y AABB real;
  5. solo entonces consume el paso.
- Disparadores (todos acotados): ruta local vacía (`sin_ruta_local`), escalonado agotado
  (`sin_objetivo_local`) y callejón sostenido 240 ticks en la misma celda sin avance global ni de
  escalonado (`callejon_sostenido`). El túnel NO compite con el escalonado viable.
- Diagnóstico: `tunelDiag()` se publica dentro de `rutaAccionDiag()`, así que aparece en los snapshots del
  arnés sin tocar el arnés.

### 3.3 Invariantes preservadas

- `NavExecutor` sigue siendo la única autoridad de movimiento.
- BREAK pasa por `puedeRomperNodoDeNavegacion` / `breakBlockAt` y las mismas puertas de protección.
- No se relajan soporte, AABB, headroom, alcance, raycast, lease, presupuestos ni criterios.
- No se entra en la aldea rompiendo bloques protegidos (la celda protegida detiene el plan).
- El túnel consume el MISMO presupuesto de túnel del ente (`tunelRestante`/`tunelEspera`); no crea uno nuevo.

## 4. Pruebas deterministas (registradas en `policyTest`)

```text
TUNNEL_ACTION_PLANNER_TEST        20 aserciones (ampliado desde 9)
TUNNEL_EXECUTION_POLICY_TEST      31 aserciones (nuevo)
TUNNEL_PLAN_POLICY_TEST           25 aserciones (nuevo)
TRAVERSAL_ACTION_LEASE_POLICY_TEST 23 aserciones (ampliado con lease de túnel)
TRAVERSAL_ACTION_DISPATCH_POLICY_TEST 6 aserciones (sin cambios)
```

Cubren: plan 2-high con stand/feet/head/post, celda protegida, bloque no rompible, falta de soporte,
dirección inválida, límite de pasos, lease por bloque y una acción por tick, fallo de break (no avanza
post), fallo de movimiento (no consume paso), plan completo verificado y no repetición de un paso ya
verificado.

## 5. Validación estática

```text
compileJava                    BUILD SUCCESSFUL
compileTestJava                BUILD SUCCESSFUL
policyTest                     BUILD SUCCESSFUL (todas las suites)
python tools/lint_aldea.py --strict   exit 0 (sin patrones peligrosos)
git diff --check               exit 0
```

## 6. Evidencia runtime (mundos nuevos y desechables, semilla 12345)

| RUN_ID | Túnel | A2 estricto | Perímetro | C4 |
|---|---|---|---|---|
| `a2n-20261121-211306` | 5 planes IDX3/4/7, todos descartados por bug de fase | 2/8 (IDX1,6) | 2/8 | PASS |
| `a2n-20261121-212053` | 3 planes IDX3/5, 9 pasos consumidos, 0 descartes | 3/8 (IDX1,4,6) | 3/8 | PASS |
| `a2n-20261121-213055` | 0 activaciones (prioridad de construcción) | 3/8 (IDX1,4,6) | 3/8 | PASS |

### 6.1 Defecto encontrado y corregido (run 1)

`a2n-20261121-211306` midió el bug exacto: el plan se descartaba justo al completar el movimiento.

```text
TUNNEL_PLAN_DISCARD ... motivo={stand_no_ocupado stand=596, 77, 598 pos=597, 77, 598}
TUNNEL_PLAN_DISCARD ... motivo={stand_no_ocupado stand=718, 63, 468 pos=717, 63, 468}
```

Causa: la comprobación de ocupación de `stand` se aplicaba también en `MOVE_POST`; en esa fase la
entidad debe precisamente ABANDONAR el stand. Corrección: exigir `stand` solo en fases BREAK y añadir
una guarda de deriva (manhattan > 3 → descartar). El run 2 lo valida:

```text
TUNNEL_STEP_CONSUMED ... tick=889  paso=0/3 stand=596, 77, 598 post=597, 77, 598 pasosMision=1/6 soporte=true aabb=true
TUNNEL_STEP_CONSUMED ... tick=972  paso=1/3 ...
TUNNEL_STEP_CONSUMED ... tick=1052 paso=2/3 ...
TUNNEL_PLAN_COMPLETE ... tick=1052 pasos=3 fin=599, 77, 598 pasosMision=3/6
```

### 6.2 Defecto encontrado y corregido (run 2 → run 3)

En el run 2 el túnel se adelantó al escalonado en IDX3 (mob 6 bloques por encima de su misión). El
run 3, con la prioridad de construcción, tuvo **0 activaciones** y el mismo 3/8. Además IDX3 tampoco
alcanzó el estricto sin túnel (597,75,598), lo que demuestra que la pérdida de IDX3 respecto del
mejor run histórico es **varianza entre corridas**, no una regresión del túnel.

## 7. Bloqueos restantes (evidencia, fuera del alcance del túnel)

- **IDX2** termina `FAILED` en `660,72,608` con motivo terminal
  `DROP source-cell recovery found no safe supported AABB-overlapping neighbor` (rearmes 4/4). No hay
  celda adyacente de pie con soporte y AABB real; un túnel horizontal no puede ayudar (el frente no es
  un bloque sólido de 2 de alto con soporte).
- **IDX3/IDX5** quedan alrededor de `597,75,598` / `596,75,469`, por encima de su misión, con el
  escalonado en `CADENCE_WAIT`/ciclos DROP; su ruta de descenso no se completa dentro de 6000 ticks.
- **IDX7/IDX8** están 8 bloques POR DEBAJO de la misión (`y=63` vs `71`) y acaban alejándose
  (`769,63,497`): necesitan ascenso/rodear, no un túnel cardinal horizontal.
- El túnel es, por contrato, horizontal y cardinal: no sustituye al escalonado ni a la ruta global.

## 8. Estado y siguiente paso

- Integración subordinada: **implementada y validada** (estática + 3 corridas runtime).
- A2: **3/8 estricto DENTRO-PARED**; no se declara PASS. C4 standalone no revalidado. Full12K bloqueado.
- Siguiente, por orden de evidencia: (1) recuperación de fuente DROP sin vecino seguro (IDX2, reproducible
  en los 3 runs); (2) completar el descenso por escalón en pozos por encima de la misión (IDX3/5);
  (3) ascenso/rodeo para misiones por encima (IDX7/8). El túnel queda como último recurso acotado.

## 9. Preservación

- Mundos: solo `build/a2n/<runId>/game/a2n` (nuevos y desechables). `run/a2n` = False; `run/saves` no se
  consultó ni modificó. Mundos protegidos intactos.
- Untracked previos preservados (`.a2-backups/`, `src/foodTest/`, `tools/a2n/*.partial`, `tools/a2n/*.ps1`,
  `tools/a5-closure/`). No se borró ningún artefacto, log ni backup.
