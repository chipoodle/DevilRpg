# A2 — CIERRE FORMAL: ACEPTACIÓN Y ESTADO POSTERIOR

> **VEREDICTO: A2 CLOSED — ACCEPTED.**
> Fecha de cierre: **2026-11-22**. Rama: `feature/autonomous-village`.
> HEAD al escribir este informe: **`dce7d504aa153c15f95bfe196da7232f0f6cb844`**.
> Build aceptada: **`5ad020882a184e8e6e95d1398175fb598b600754`**.
> Este documento es **solo documental**: no modifica código, tests, mundos ni política de aceptación.

---

## 1. Veredicto final

**A2 (Zombie Siege) queda CERRADO y ACEPTADO.**

El criterio de aceptación del proyecto (8/8 asaltantes entrando en la aldea en corridas controladas
idénticas, con C4 integrado PASS, dentro de la ventana oficial de 12000 ticks) **se cumple con margen**:
tres corridas completas consecutivas con **8/8 estricto DENTRO-PARED** y **C4_PASS** cada una.

La corrida `a2n-20261122-055413` alcanzó las 8 entradas estrictas pero quedó **interrumpida** antes del
veredicto C4 (sin `fin.txt`) y **no se contabiliza** como corrida de aceptación.

---

## 2. Criterio de aceptación aplicado

- **Criterio funcional** (`docs/autonomous-village/01-navigation-architecture.md`): *8/8 asaltantes entrando
  en la aldea en dos corridas controladas idénticas*.
- **Métrica estricta**: evento `RAID ESTADO idx=N DENTRO-PARED(ahora)` con **distancia horizontal ≤ 62** del
  ancla de la aldea (el perímetro ≤ 71 es una métrica distinta y NO cuenta como estricta).
- **C4 integrado**: veredicto `C4_PASS` del arnés al final de la fase B.
- **Ventana oficial**: 12000 ticks (`docs/autonomous-village/2026-11-15-PHASE6-IDX4-IDX8-FORENSICS.md`: «Full
  A2 acceptance uses 12000 ticks»). Las corridas se lanzaron con `-FaseA 3000 -FaseB 12000`; **todas las
  entradas estrictas ocurren antes de t=8668**, dentro de la ventana oficial.
- **Semilla**: 12345. **Mundos**: nuevos y desechables bajo `build/a2n/<runId>/game/a2n`.

---

## 3. Corridas aceptadas (tres consecutivas)

| RUN_ID | Veredicto | Estricto | Fase B | Entradas (idx → tick, dist) |
|---|---|---|---|---|
| `a2n-20261122-054142` | **C4_PASS** | **8/8** | 8/8, entraronPerimetroEver=8 | 2→633 (61,90) · 6→690 (61,92) · 5→1886 (62,00) · 1→2172 (61,92) · 7→2479 (61,94) · 3→3013 (61,98) · 8→4571 (61,91) · 4→8541 (61,96) |
| `a2n-20261122-094529` | **C4_PASS** | **8/8** | 8/8, entraronPerimetroEver=8 | 2→633 (61,90) · 6→691 (61,92) · 5→1680 (62,00) · 7→2479 (61,94) · 1→2792 (61,94) · 3→3124 (61,98) · 8→5021 (61,99) · 4→8451 (61,94) |
| `a2n-20261122-100646` | **C4_PASS** | **8/8** | 8/8, entraronPerimetroEver=8 | 6→686 (61,92) · 2→712 (61,90) · 5→1887 (62,00) · 1→2168 (61,92) · 7→2479 (61,94) · 3→3009 (61,98) · 8→4567 (61,91) · 4→8668 (61,96) |

Evidencia complementaria **NO contabilizada**: `a2n-20261122-055413` — estricto 1..8 registrado, sin
`fin.txt`, interrumpida en t≈10500 durante la suspensión de la sesión; no hubo veredicto C4.

### Evidencia literal de `fin.txt`

```text
a2n-20261122-054142: C4_PASS (raid) - guardasCombatieron=true profesionalesReanudados=true asignacionesJobSiteLimpias=true supervivientes=16 trabajadoresEsperados=12 trabajadoresVivos=12 trabajadoresReanudados=12 bajasTrabajadores=0 trabajadoresNoResueltos=0 retiradosParaRecuperacion=0 jobSite=[8, 0, 0] fallosDeInstrumento=0
a2n-20261122-094529: C4_PASS (raid) - guardasCombatieron=true profesionalesReanudados=true asignacionesJobSiteLimpias=true supervivientes=16 trabajadoresEsperados=12 trabajadoresVivos=12 trabajadoresReanudados=12 bajasTrabajadores=0 trabajadoresNoResueltos=0 retiradosParaRecuperacion=0 jobSite=[11, 0, 0] fallosDeInstrumento=0
a2n-20261122-100646: C4_PASS (raid) - guardasCombatieron=true profesionalesReanudados=true asignacionesJobSiteLimpias=true supervivientes=16 trabajadoresEsperados=12 trabajadoresVivos=12 trabajadoresReanudados=12 bajasTrabajadores=0 trabajadoresNoResueltos=0 retiradosParaRecuperacion=0 jobSite=[8, 0, 0] fallosDeInstrumento=0
```

### Evidencia literal de `srv.log` (resumen de fase B y un ejemplo de entrada)

```text
[A2N] RESUMEN FASE B t=12000 spawneados=8/8 fallosSpawn=0 vivos=0 muertos=8 desaparecidos=0 entraronPerimetroEver=8 dentroParedAhora=0
[A2N] RAID ESTADO idx=4 DENTRO-PARED(ahora) t=8668 dist=61.96 pos=599, 71, 524   (a2n-20261122-100646)
```

---

## 4. Correcciones documentadas (por commit verificable)

La campaña agrupó las correcciones en once causas raíz; el detalle verificable por commit es el siguiente
(no se atribuye ningún cambio fuera de su commit):

| # | Causa raíz | Corrección | Commit |
|---|---|---|---|
| 1 | Celdas de pie con decoración sin colisión (nieve) rechazadas por `isAir()` | `NavFeetCellPolicy`: validación por colisión | `9208338057125f661e2f21f1b54a1973d31b2109` |
| 2 | Tramo local de 58 bloques contra 900 expansiones | `NavLocalObjectiveLegPolicy` (≤24 Chebyshev) | idem |
| 3 | DROP aterriza 3 abajo y exige volver al take-off (+4): livelock | `NavDropOvershootPolicy` + abandono al 3.er rechazo | idem |
| 4 | Sin progreso horizontal real cuando la misión está arriba | `NavLocalObjectiveProgressPolicy` | idem |
| 5 | Troncos del muro vetados al planificador | `NavBreakFilterPolicy` (muro rompible) | idem |
| 6 | A* global drenada a las cuevas | `NavGoalMetric.heuristicClimbing` | idem |
| 7 | Ruta global validada no ejecutada | `intentarRutaGlobalValidada` | idem |
| 8 | Sin ascenso posible + watchdog por generación de chunks | `NavClimbStepPolicy` + guarda `FUERA_DE_CARGA` + tope de 30 ms | idem |
| 9 | `misionDePie()` null y avance con umbral de 3 bloques junto al muro | `misionDeReferencia` + micro-avance | `d4d61037cd5a21c9c4070cc288e093c67f7cea25` |
| 10 | Encadenado de subidas cortado, deadlock de ruta duplicada, salto al 2.º escalón | Encadenado + reintento forzado acotado + salto asistido | `023f1ab5429a557a468b4b3e0a8d81b20bb61590` |
| 11 | Techo del stand capando el salto, sin entrada al recinto, ascenso subiendo al muro | Despeje de S+2, `ENCLOSURE_STEP`, ascenso solo con desnivel ≥3 | `722bc5eda734baa4e864191d41200332b6f1e69b` |
| 12 | Cadencia de escalera que nunca avanzaba | Tick del constructor en la espera | `55d9d4301abde8dba64de06af8bb0f5c696c782a` |
| 13 | Aproximación al muro exigiendo 3 bloques, túnel no intentado con presupuesto agotado, escalón pendiente inalcanzable | Micro-avance de banda, túnel en `presupuesto_local`, timeout de 400 ticks | `83e10d86775f7b20825ef222b0c8256c1183c968` |
| 14 | Paso de borde DROP nunca arrancaba con la navegación activa | Arranque desde despegue válido (restringido a descenso) | `f574b4af4599503c61596f3489a33291a3abfa26`, `d4515543cd252e875aeccaccfb023db69932314d` |
| 15 | Brecha no pedida desde la banda + descenso progresivo desde 10 bloques | Brecha directa + umbral 3 bloques | `314224ed0c7748b6419d5f89067f502cc7e58f38` |
| 16 | Varianza de aproximación, objetivo de avance sin caducidad, descenso progresivo | Banda del muro determinista + caducidad 200 ticks + descenso | `1cf7d8ef53df1a7205cbafcf7d782978daf21d2c` |
| 17 | Presupuesto de rotura agotado en la montaña (`tunel 0/40`) | Exención estrecha del muro + penalización de BREAK en descenso | `5ad020882a184e8e6e95d1398175fb598b600754` |

Contexto previo (base de la campaña): `9a4553af5d329b8c58b337872c7f7442094a3a9d`
(`feat(nav): execute tunnel action plans through NavExecutor lease`).

---

## 5. Las dos correcciones finales (`5ad02088`)

### 5.1 Exención espacialmente acotada del presupuesto del muro

- **Qué se hizo**: romper una celda del **anillo del muro** no descuenta el presupuesto de 40 bloques de
  terreno, **solo** cuando el ente está en la banda del anillo (**60,5–65** del objetivo) y **manteniendo la
  cadencia** (`tunelEspera`). Lejos del muro el contrato de rotura no cambia.
- **Por qué fue necesaria**: IDX4 nace a y≈122 en la montaña y gasta los 40 bloques en el descenso; llegaba al
  muro con `tunel 0/40` y el actuador rechazaba la brecha (`actuador_sin_presupuesto_o_cadencia`), quedándose
  a **62,9–63,2** (0,1–0,9 del criterio ≤62). Sin brecha no hay entrada.
- **Bloqueo que resolvió**: la imposibilidad de abrir el muro con presupuesto cero. Medido: IDX4 entra en
  t=8541 / 8451 / 8668 en las tres corridas aceptadas.
- **Invariantes intactas**: presupuesto total sin cambios (40); cadencia del actuador sin cambios; protección
  del interior (`protegidaParaNavegacion`) sin cambios; lease, soporte, AABB y headroom sin cambios; el muro
  ya era rompible por diseño (`NavBreakFilterPolicy`).
- **Revisión futura recomendada**: es una **excepción de cómputo**, no una ampliación de presupuesto. Un
  revisor podría preferir la alternativa «reservar N bloques para la muralla» (probada y descartada en su
  momento por coincidir con la variante de resultados 4/8). Se documenta como decisión de diseño a revisar,
  **sin invalidar la aceptación** (el comportamiento observado es el de tres corridas completas).

### 5.2 Penalización de BREAK en descensos largos

- **Qué se hizo**: `NavPlanner` suma **+120** al coste de una arista `BREAKABLE` cuando `from.y - goalY > 6`
  (`COST_BREAK_DESCENSO`), de modo que el A* prefiere caídas y pasos de borde al taladrar la ladera.
- **Por qué fue necesaria**: el descenso de IDX4 consumía el presupuesto de terreno minando casi cada bloque.
- **Bloqueo que resolvió**: ayuda a que el descenso use la geometría natural; el presupuesto sigue gastándose
  en la montaña (la exención del muro es la que desbloquea la brecha), por lo que esta penalización es una
  mejora de preferencia, no la condición de aceptación.
- **Invariantes intactas**: coste de BREAK base (12) sin cambios fuera de descensos largos; presupuesto y
  cadencias sin cambios.

### 5.3 Presupuestos e invariantes globales

No se aumentó ningún presupuesto (túnel 40, escalera 24, ascensos 12, planes locales, expansiones A*) ni se
relajaron protección, lease, soporte, AABB, headroom o caída segura. Las únicas excepciones son las
documentadas en §5.1 y §5.2.

---

## 6. Commits y hashes completos

| Hash completo | Título |
|---|---|
| `9a4553af5d329b8c58b337872c7f7442094a3a9d` | feat(nav): execute tunnel action plans through NavExecutor lease |
| `9208338057125f661e2f21f1b54a1973d31b2109` | fix(nav): siege zombie reliability - 8 root causes, A2 3/8 -> 6/8 strict |
| `d4d61037cd5a21c9c4070cc288e093c67f7cea25` | fix(nav): mission reference and micro-advance for the siege ascent |
| `023f1ab5429a557a468b4b3e0a8d81b20bb61590` | fix(nav): chain climbs, break duplicate-route deadlock, jump-assisted step |
| `722bc5eda734baa4e864191d41200332b6f1e69b` | fix(nav): clear stand ceiling for climbs, enclosure entry step, ascent only for real elevation |
| `55d9d4301abde8dba64de06af8bb0f5c696c782a` | fix(nav): tick the stair cadence from the executor wait path |
| `83e10d86775f7b20825ef222b0c8256c1183c968` | fix(nav,a2): 7/8 strict - wall approach advance, tunnel on budget, stair pending timeout |
| `f574b4af4599503c61596f3489a33291a3abfa26` | fix(nav): start the DROP edge step from a valid takeoff even with an active path |
| `d4515543cd252e875aeccaccfb023db69932314d` | fix(nav): narrow the edge-step relaxation to descent missions |
| `314224ed0c7748b6419d5f89067f502cc7e58f38` | fix(nav): ring breach from the wall band and descent step from 3 blocks |
| `1cf7d8ef53df1a7205cbafcf7d782978daf21d2c` | fix(nav): deterministic wall-band advance, advance-objective expiry, progressive descent action |
| `5ad020882a184e8e6e95d1398175fb598b600754` | fix(nav): preserve the wall breach - narrow wall exemption plus descent break cost |
| `dce7d504aa153c15f95bfe196da7232f0f6cb844` | docs(a2): ACCEPTED - 8/8 strict in three consecutive runs with C4 PASS |

Relación con el estado final del código: `dce7d504` es el HEAD al escribir este informe; el árbol de trabajo
solo contiene untracked previos preservados (`.a2-backups/`, `src/foodTest/`, `tools/a2n/A2NavHarness.java.partial`,
`tools/a2n/consultar-c4.ps1`, `tools/a2n/lanzar-c4.ps1`, `tools/a5-closure/`). La build aceptada es `5ad02088`.

Commits documentales de la campaña: `4ceba8bd`, `5e878e94`, `cebe5cbb`, `dc8e6a4f`, `60606d8f`, `5eee7339`,
`c15d30bb`.

---

## 7. Gates finales

Última ejecución registrada (en `dce7d504`, antes de este cierre documental):

```text
compileJava      BUILD SUCCESSFUL
compileTestJava  BUILD SUCCESSFUL
policyTest       BUILD SUCCESSFUL
lint_aldea.py --strict   OK
git diff --check         0
```

En esta fase de cierre **no se han vuelto a ejecutar Gradle, tests ni runtime** (restricción del encargo).
`git diff --check` se ha verificado en modo lectura.

---

## 8. Invariantes preservadas

- `NavExecutor` continúa siendo la **única autoridad de movimiento**; una acción de terreno por entidad y
  tick mediante `TraversalActionLeasePolicy`.
- BREAK sigue pasando por `puedeRomperNodoDeNavegacion`/`breakBlockAt`; el interior de la aldea sigue
  protegido.
- Sin teleport. Sin cambios en protección, lease, soporte, AABB, headroom ni caída segura.
- Sin aumento de presupuestos (túnel 40, escalera 24, ascensos 12, planes y expansiones sin cambios).
- Mundos protegidos intactos: `run/a2n`=False, `run/saves` sin consultar ni modificar, solo mundos nuevos en
  `build/a2n/<runId>/game/a2n`.
- Untracked previos conservados; sin `reset --hard`/`clean`/borrados.

---

## 9. Decisiones de diseño que quedan documentadas

1. **Exención estrecha del muro** (§5.1): aceptada para la campaña; **revisable** en el futuro.
2. **Penalización de BREAK en descenso largo** (§5.2): preferencia de planificación, sin cambio de contrato.
3. **Ventana oficial 12000**: las corridas aceptadas usan `-FaseB 12000`; todas las entradas estrictas
   ocurren antes de t=8668.
4. **Umbrales de las acciones nuevas**: ascenso solo con desnivel ≥3; descenso progresivo desde ≥3; banda del
   muro 60,5–65; timeout de escalón pendiente 400 ticks; reintentos forzados de ruta 2 por misión.
5. **Bimodalidad observada**: la misma build puede dar 7/8 o 4/8 según el orden de tick/combate; la aceptación
   se apoya en tres corridas completas consecutivas, no en una sola.

---

## 10. Inventario de pendientes posteriores a A2 (priorizado)

### A. Obligatorios para la siguiente etapa
1. **Revisar la exención del presupuesto del muro** (§5.1). Decisión del dueño; no bloquea la aceptación.
2. **C4 standalone / save-reload**: no consta validación independiente posterior a la aceptación. El C4
   **integrado** pasó en las tres corridas; el standalone (guardar/recargar sin asedio) sigue **pendiente**.
3. **Reconciliar los diagnósticos históricos de BUILD/BREAK/DROP** con las correcciones de esta campaña:
   los informes previos a A2 listan límites y combinaciones no probadas (`01-navigation-architecture.md` §L);
   hay que marcar cuáles quedaron cubiertos y cuáles siguen abiertos.
4. **Full 12k**: las corridas aceptadas **son** de 12000 ticks de fase B. Si el proyecto distingue una
   «campaña Full12K» separada (p. ej., con validaciones adicionales), debe confirmarse su definición; no se
   ejecutó nada adicional en este cierre.

### B. Validaciones no ejecutadas (requieren autorización)
- C4 standalone (guardar/recargar).
- Cualquier campaña «Full12K» distinta de las corridas aceptadas.
- Regresión de caza y otros escenarios fuera del fixture A2 (no consta ejecución en esta campaña).

### C. Decisiones de diseño a revisar
- La exención del muro (§5.1).
- La política de rama: `01-navigation-architecture.md` §K.1 señala que `docs/CONTINUAR.md` pide mantener
  `feature/autonomous-village` sincronizada con `migration-neoforge-1.21.1` y **no se ha sincronizado**. Sigue
  siendo una decisión del dueño.
- Los otros conflictos de §K (ámbito aldeano vs zombi; `docs/aldea-cerebro.md`; numeración de documentos).

### D. Mejoras opcionales
- Reducir la bimodalidad (7/8 vs 4/8) estabilizando el orden de tick/combate.
- Reducir el consumo de presupuesto del descenso (la penalización no lo eliminó).
- Rendimiento: los planes locales tienen tope de 30 ms; el A* global usa 8000 expansiones.

### E. Problemas históricos resueltos — NO reabrir como pendientes
- Las 17 correcciones de §4 (incluidas las 8 causas raíz de `92083380`).
- El watchdog por generación síncrona de chunks (guarda `FUERA_DE_CARGA` + tope de CPU).
- El deadlock de ruta duplicada de IDX3, el livelock de take-off de IDX5, la nieve en las celdas de pie, los
  troncos del muro vetados al planificador: todos con corrección y evidencia runtime.
- El resultado 3/8, 5/8 o 6/8 de las campañas previas queda **superado** por las tres corridas 8/8.

---

## 11. Limitaciones y discrepancias registradas

- **`a2n-20261122-055413`** (8/8 estricto, sin `fin.txt`) no se contabiliza: interrumpida antes del veredicto C4.
- **Gates no re-ejecutados** en esta fase documental por restricción expresa; se registra la última ejecución.
- **Full12K**: no hay una definición separada verificable en los documentos para una campaña distinta de las
  corridas de 12000 ticks; se registra como punto a confirmar, no como validación pendiente inventada.
- **Sincronización de rama** con `migration-neoforge-1.21.1`: no realizada; conflicto documentado en §K.1.
- Los checkpoints `docs/CONTINUAR.md`, `docs/PENDIENTE.md`, `docs/autonomous-village/NEXT-SESSION.md` y
  `docs/autonomous-village/CONTINUAR.md` se actualizan a **A2 CLOSED — ACCEPTED** conservando todo el historial.
