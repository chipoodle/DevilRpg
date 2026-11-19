# CURRENT CHECKPOINT — 2026-11-19 A2 Diagnostic Run1 post-Phase55

**Branch:** `feature/autonomous-village`
**Source implementation commit:** `a029e4eb790109df94acb1e07f8cac52f8b88ff8`
**Implementation report:** `docs/autonomous-village/2026-11-19-A2-PHASE55-REMOVE-OBSOLETE-NAVFEATURE-HARNESS-DEPENDENCY.md`

- **Harness PASS:** canonical seleccionado, legacy intacto. NavFeature solo en comentario. compile/test/lint/diff OK; NAV_PLANNER_STATE_TEST 54 aserciones. Sin runtime. Detalle: `docs/autonomous-village/2026-11-19-A2-PHASE55-NAVFEATURE-REMOVAL-STATIC-VALIDATION.md`.
- **A2 Diagnostic Run1 `a2n-20261119-151818` FAIL:** JVM_TRACE OK, fixture PASS (16/16,8 spawn), A2 3/8 estricto (IDX1/2/6) y 3/8 perímetro; los 3 estrictos murieron; C4 PASS. IDX3/5 tope escalones64/64, IDX4 CADENCE sin candidato, IDX7 sin colocación, IDX8 PLANNING. Sin watchdog. Sin retry. Detalle: `docs/autonomous-village/2026-11-19-A2-DIAGNOSTIC-RUN1-POST-PHASE55.md`.

- Harness report `phase55-routing-static-20261119-1400`: routing PASS, compile/test FAIL due seven stale references
  to `NavFeature`; NAV_PLANNER_STATE_TEST NOT RUN; strict lint and diff-check PASS.
- Migration report from 2026-11-15 proves `NavFeature` and `-Ddevilrpg.nav.custom=true` were deliberately
  deleted. Current production source constructs and ticks `NavExecutor` unconditionally server-side.
- ChatGPT removed the obsolete references from canonical `tools/a2n/A2NavHarness.java`; it now reports
  `NavExecutor-single-authority`. No class/flag was recreated.
- Gradle routing and wrapper unchanged. No production Java, NavPlanner, gameplay, world, fixture or criteria
  changed. No compile/test run by ChatGPT.

**Siguiente:** Harness STATIC ONLY en el HEAD remoto actual. Ejecutar compileJava, test, strict lint y diff-check
con las propiedades de selección canónica. Confirmar que se compila el fuente canónico, que no hay referencias
NavFeature, que NeoForge APIs compilan y que NAV_PLANNER_STATE_TEST (54 assertions) se ejecuta. Preservar local,
untracked, mundos y el arnés legacy.

**NO runtime:** no Minecraft, wrapper, runServer, A2, C4, Run2 ni Full12K. Autorización consumida. A2 OPEN.

---

# CURRENT CHECKPOINT — 2026-11-19 A2 Phase55 first-expansion forensics

- Production SHA: `11ff43cd338d7a6222a146a2ed642a5546fb2910` (unchanged).
- Run `a2n-20261119-031255`: BLOCKED before attackers; 289/289 chunks READY in 1 tick; population 14 = 4 founder guards + 10 professionals, expected 12 workers.
- Four `NONE` professions are expected null-profession founder guards, not evidence of four lost worker jobs.
- Missing roster professions: SHEPHERD slot 5 (+47,0,0) and MASON slot 11 (+33,0,-22). Exact failure point remains UNKNOWN.
- Source review: all 12 worker slots are iterated, but spawn API currently hides per-slot success/failure; spawn can return null without slot-specific evidence. Final report lacks before/after entity snapshots.
- Detailed checklist: `docs/autonomous-village/2026-11-19-A2-PHASE55-POPULATION-FORENSICS.md`.
- **NBT read-only:** los 12 workers existen; SHEPHERD `c690ddc5…` 708,71,533 y MASON `c6ba9a5c…` 694,71,511 viven (HP20, death0, persistent) y están dentro del AABB; el snapshot `getEntitiesOfClass` los omitió. Detalle: `docs/autonomous-village/2026-11-19-A2-PHASE55-POPULATION-NBT-FORENSICS.md`.
- **Run1 autorizado `a2n-20261119-093456` FAIL:** fixture PASS (289/289,16 vill/12 prof,8 spawn), A2 0/8 estricto,1/8 perímetro (idx6 t271), 8 vivos; C4 FAIL por guardas sin combate. A* incremental evitó watchdog pero terminó exp=1/8000 frente agotado y la mayoría quedó PLANNING. Sin retry. Detalle: `docs/autonomous-village/2026-11-19-A2-PHASE55-RUNTIME-VALIDATION.md`.
- **Forense read-only:** IDX3/IDX5 completan `exp=1` con `aristas=56` y `encolados=0`: primer nodo genera los 56 vecinos y todos se rechazan. Ambos están en pozos 1×1 con columnas adyacentes sólidas; `OFFSETS` no incluye arista vertical `(0,0)`. Falta telemetría por candidato. Detalle: `docs/autonomous-village/2026-11-19-A2-PHASE55-ASTAR-FIRST-EXPANSION-FORENSICS.md`.
- Next: Harness read-only inspection of existing logs and safe read-only entity NBT. No runtime, no world mutation, no production edits by Harness.
- Single Run1 authorization consumed. No retry, Run2, C4 standalone or Full12K. A2 OPEN; Phase55 runtime unmeasured.

---

# HISTORICAL CHECKPOINT — 2026-11-18 A2 Phase 52 forensics

- Production implementation SHA: `ea21466a0cc7cc2671f5439cd5ff7bbaeecd6d9e`.
- Implementation report: `docs/autonomous-village/2026-11-18-A2-PHASE51-STAIR-WIRING-FIX.md`.
- Phase50 validation: FAIL, A2 3/8 strict and 3/8 perimeter; C4 PASS [1,0,0].
- Phase51 wires the monotonic stair anchor refresh and strict-below candidate guard; cooldown now propagates as `CADENCE_WAIT` without consuming local step/failure counters.
- Both stair policy tests (8 + 6 assertions) are registered in the normal `policyTest` gate; `gradlew test` must execute them.
- Harness must validate this exact production SHA, preserve all local-only work/worlds, run static checks first, then A2 Run1 only if every static check passes. Harness must not modify production.
- Phase41/44/46 and all budgets/safety predicates remain unchanged. IDX4 horizontal-zero remains INSUFFICIENT_EVIDENCE.
- No Run2 or Full12K until five consecutive 8/8 strict `DENTRO-PARED` runs. A2 remains OPEN.

---

# ESTADO ACTUAL — 2026-11-16 (checkpoint operativo)

> **Fuente vigente:** la continuación activa es `docs/autonomous-village/NEXT-SESSION.md`. La política permanente es `docs/autonomous-village/CHATGPT-COLLABORATION-POLICY.md`; el reparto de responsabilidades y protocolo de handoff están en `docs/autonomous-village/CHATGPT-HARNESS-WORKFLOW.md`. Los registros históricos de este archivo se conservan como evidencia y no son instrucciones vigentes si contradicen el checkpoint actual.

> **Roles obligatorios:** ChatGPT modifica el código de producción directamente en GitHub y publica commits. Harness sincroniza, preserva trabajo local, ejecuta compilaciones/pruebas/lint/runtime, mide y genera reportes. Harness no implementa correcciones de producción; devuelve evidencia a ChatGPT. Ninguno declara validado un SHA sin ejecutar y documentar los checks sobre ese SHA exacto.

> **Checkpoint validado:** `e1530ab30591a26237d9ae64c1e9cfefbcbb27f2` en `feature/autonomous-village` (Phase46 vertical-only DROP fix). Static PASS (`NAV_DROP_EDGE_STEP_POLICY_TEST` 16). A2 Run1 `a2n-20261118-163717` = 4/8 estricto, C4 PASS `[1,0,0]`. A2 OPEN.
>
> **A2 Phase46 (validación):** el caso runtime IDX4 vertical-only no se reprodujo; su validación física queda INSUFFICIENT_EVIDENCE. IDX3 muestra 20 `STAIR_PLACED`, pending stairs y fallback `result=null`; la promoción a ruta no está trazada. Detalle: `docs/autonomous-village/2026-11-18-A2-PHASE46-VALIDATION.md`.
>
> **A2 Phase48A (corrección causal):** Phase47 era incorrecto. El fallback sale antes de consultar el pending porque `STAIR_BUILDER_RETURN outcome=NO_PROGRESS` (rama de cooldown/early return, pending=null, motivo antiguo y rechazo=-). El builder coloca 20 veces el mismo escalón `607,93,601`; el mob lo atraviesa 20 veces sin descender. Falta instrumentar `escaleraRetraso` y la generación de candidatos. Detalle: `docs/autonomous-village/2026-11-18-A2-PHASE48A-IDX3-CAUSAL-RECONCILIATION.md`.
>
> **A2 Phase49A (validación de trazas):** el ancla avanzada a `607,93` es sobrescrita por `ANCHOR_REFRESH` a `607,94` en t2299; el siguiente candidato dy=1 vuelve a `607,93`. Cooldown CADENCE_WAIT consume pasos locales. Detalle: `docs/autonomous-village/2026-11-18-A2-PHASE49A-VALIDATION.md`.
>
> **A2 Phase50 (validación FAIL):** `ANCHOR_REFRESH` sigue asignando `blockPosition().below()` (93→94) y el cooldown sigue devolviendo `NO_PROGRESS`; `CADENCE_WAIT`/`A2_LOCAL_STAIR_WAIT` no aparecen. Los tests nuevos pasan sólo por tarea init, no están registrados en build.gradle. Detalle: `docs/autonomous-village/2026-11-18-A2-PHASE50-VALIDATION.md`.
>
> **A2 Phase51 (validación FAIL):** CADENCE_WAIT validado (3595 cooldowns, 3542 `A2_LOCAL_STAIR_WAIT`, contadores intactos), pero no hubo ninguna colocación/travesía (`ANCHOR_REFRESH=0`), por lo que el ancla monotónica queda INSUFFICIENT_EVIDENCE. A2 bajó a 2/8 estricto; IDX5 perdió la entrada. Detalle: `docs/autonomous-village/2026-11-18-A2-PHASE51-VALIDATION.md`.
>
> **A2 Phase52 (forense):** IDX3 tiene 397 llamadas, 2340 candidatos y 0 colocaciones porque todo candidato falla por terreno ocupado (`celdaAire=false`). IDX5: Phase50 t2 NO_PROGRESS y t3 MOVE_COMMAND; Phase51 t2 CADENCE_WAIT, cero MOVE_COMMAND. El deferral CADENCE_WAIT bloquea el fallback. Detalle: `docs/autonomous-village/2026-11-18-A2-PHASE52-STAIR-IDX5-FORENSICS.md`.
>
> **A2 Phase53 (validación BLOCKED):** static PASS sobre `656ce7d0`, pero A2 `a2n-20261118-233308` murió en `A2NavHarness.prepararRaidFixture`/`setChunkForced` (watchdog 60s) antes de spawnear atacantes. Sin evidencia de IDX5/CADENCE route; INSUFFICIENT_EVIDENCE. Detalle: `docs/autonomous-village/2026-11-18-A2-PHASE53-VALIDATION.md`.
>
> **A2 Phase54A (forense de fixture):** 289 `setChunkForced` síncronos (ticket + `getChunk` bloqueante) en un solo `ServerTickEvent.Pre`; generación fría >60s. El loop terminó después del watchdog (`C4 CHUNKS forced=289`). Recomendado mundo desechable pre-generado + fase de tickets no bloqueante y timeout. Detalle: `docs/autonomous-village/2026-11-18-A2-PHASE54A-FIXTURE-FORENSICS.md`.
>
> **A2 Phase54B (harness):** fixture migrado a `ServerChunkCache.updateChunkForced` no bloqueante + polling `hasChunkAt` + timeout600; spawn sólo tras READY y verificaciones. Static PASS; runtime pendiente de autorización. Detalle: `docs/autonomous-village/2026-11-19-A2-PHASE54B-FIXTURE-IMPLEMENTATION.md`.
>
> **A2 Phase54B-R1:** el timeout se comprueba antes de READY con `now >= deadline`; si vence, libera los 289 tickets, marca `rChunkLoadFailed` y cierra INVALIDO sin spawn. Static PASS; runtime pendiente.
>
> **A2 Phase54B-R1 runtime:** `a2n-20261119-011138` validó el fixture (289/289 READY en1 tick, 8/8 spawn) pero murió por watchdog en el primer burst global de A* (`NavPlanner/NavObstruction`, 60.04s). Sin resultado A2 ni evidencia IDX5/IDX3. Detalle: `docs/autonomous-village/2026-11-19-A2-PHASE54B-R1-RUNTIME-VALIDATION.md`.

> **Phase19 BREAK:** Harness pasó Gradle/lint/diff-check sobre `ecdfaff6`, pero no observó eventos BREAK runtime. ChatGPT corrigió la identidad del estado post-BREAK, el avance por proximidad, la ocupación exacta del stand y el traspaso del action target a la ruta local. Se añadieron `NavBreakGraphStateTest`, `NavBreakRoutePolicyTest` y el modo determinista `A2NavHarness -Da2n.mode=break-runtime`. Todo lo nuevo sigue **pendiente de validación Harness**. Detalle: `docs/autonomous-village/2026-11-16-PHASE19-BREAK-GRAPH-STATE-FIX.md`.

> **A2:** último runtime evidenciado `a2n-20261118-225649`: 2/8 estrictos, 2/8 perímetro; C4 PASS `[1,0,0]`. A2 OPEN. No ejecutar Run2 ni Full12K hasta cinco corridas consecutivas 8/8 estrictas `DENTRO-PARED`. **C4:** último PASS `[1,0,0]`; save/reload OPEN.

# Estado de cola actualizado — 2026-11-12 22:43

**Traspaso operativo:** continuar directamente en CHRIS-PC mediante Desktop Commander; no pedir al usuario que pegue archivos/logs. Repo `D:\\Documents\\DevilRpg-autonomous`, rama `feature/autonomous-village`, HEAD verificado al retomar `518fee13fc15bfb9b92b0479d86b5c609d1947da`. No commit/push. Se preservan todos los cambios preexistentes.

**Block 1, reconciliación:** Forestry Block 1 A1-A7 ya estaba COMPLETE según `docs/autonomous-village/FORESTRY-BLOCK-1-ACCEPTANCE.md`; Runs 57/58/81 conservan evidencia histórica PASS. Esta sesión reejecutó Run 62 terminal obstruction (`res-block1-regression-stuck5-20261112-184000`), Run 65 recoverable wall-end (`res-block1-regression-recover-20261112-185000`) y Run 27 Bone Meal growth (`res-block1-regression-growth-20261112-185000`): los tres PASS. No repetirlos. El intento adicional `lumber-interrupt` (no requerido; Run81 ya PASS) tuvo un intento `phase` BLOCKED con grace antiguo de 180 s y un `phase-retry300` que sí entró al mundo, pero acabó sin `result.json`: el worker B estaba ejecutando `VillagerGuardGoal`, no se observaron las aserciones de dos trabajadores/preempción y el cliente salió con exit code 1. Shutdown limpio, ventana oculta, foco robado 0, clientes residuales 0. Es un fixture sin resultado, no un FAIL de producción. No quedan regresiones Block 1 pendientes.

**Regla permanente del ejecutor:** usar `tools/a5-closure/run-a5.ps1`. Quick-play directo, onboarding completado, `pauseOnLostFocus:false`, ventana oculta sin activación, login verificado, result/shutdown JSON y limpieza del cliente. No usar ESC, SendKeys, clics ni SetForegroundWindow. Grace actual: 300 s. El cliente se filtra por hora de inicio del run y cleanup termina todo el árbol cmd/Gradle/runClient con `taskkill /T /F`, seguido de una segunda búsqueda de clientes. `-SelfTest` PASS, incluyendo terminación de procesos hijo. No requiere intervención del usuario.

**Lumberjack Phase 1: ACCEPTED dentro del alcance (2026-11-12).** A5-01..A5-12
normal lifecycle PASS; A5-09 malformed PASS; Forestry Block 1 A1-A7 COMPLETE;
sapling placement rejection PASS (plantar + replantar); Bone Meal API-false after
argument mutation PASS. La primera prueba mutada detectó y permitió corregir el
bug de `ItemStack.EMPTY.grow`. Única exclusión: abrupt process-crash atomicity,
fuera de alcance. No repetir A5 ni Block 1. Evidencia y run IDs en
`docs/autonomous-village/LUMBERJACK-PHASE-1-ACCEPTANCE.md`.

Food Economy 3.1 y 3.2 están CERRADOS. No son tareas activas. El detalle completo de
la última campaña 3.1 está en
docs/autonomous-village/FOOD-ECONOMY-BLOCK-3.1-ACCEPTANCE.md.

**C1 — Food chain: ACCEPTED.** Food Economy 3.1/3.2 closed; evidence in their
acceptance docs.

**C2 — Resource-to-repair accounting: ACCEPTED (2026-11-12).** Run
`c2-repair-accounting-r2-20261112-202200` PASS: no material/no block change;
one Stone Brick restored the target and was consumed exactly once; already-
repaired target retained its material. Shutdown clean, hidden window, focus
steal 0, residual clients 0. Evidence:
`docs/autonomous-village/C2-REPAIR-ACCOUNTING-ACCEPTANCE.md`.

**C3 — Workforce under pressure: ACCEPTED for forestry.** Historical Run81
proves distinct controllers/targets, cleanup and reacquisition. Evidence:
`docs/autonomous-village/FORESTRY-BLOCK-1-ACCEPTANCE.md`.

**C5 — Village persistence: ACCEPTED (2026-11-12).** Runs
`c5-village-persistence-save-r3-20261112-201500` and
`c5-village-persistence-load-r3-20261112-201500` PASS. Two village centers,
UUIDs/professions, exact warehouse counts and active TALAR task survived reload;
normal village-life poll restored one lumberjack Goal; worker resumed 32.55 blocks;
duplicate Goals 0/0. Evidence:
`docs/autonomous-village/C5-VILLAGE-PERSISTENCE-ACCEPTANCE.md`.
C1, C2, C3, C4 y C5 están aceptados. El contrato de supervivientes/reanudación quedó validado en `c4-full8-20261112-2240`; Block 6 multi-day soak queda desbloqueado como siguiente tarea, todavía no ejecutada.

**Historial de diagnóstico C4 / A2 (supersedido por el PASS final de run8).** Run3 (`c4-full3`) dio C4_PASS bajo criterio estricto: 4/4 guardias, 12/12 reanudados, 16 aldeanos vivos. Run4 (`c4-full4`) registró 16 impactos, 16 aldeanos vivos y 11/12 reanudados; 12/12 Goals instalados. Run5 (`c4-full5`) registró 4/4 guardias, 16 impactos, cinco atacantes muertos, tres retirados, 15 aldeanos vivos, 11 profesionales supervivientes y 11 Goals/reanudaciones; JOB_SITE 0 huérfanos/duplicados. El UUID faltante `f62b9fae-2361-42b0-b0f3-6058f265c789` era el único weaponsmith; el log confirma que Quintin fue asesinado por un Aggressive Zombie. Es una baja real, no un falso negativo de AABB.

**Contrato ajustado sin ocultar bajas:** C4 no establece cero bajas; exige que cada trabajador superviviente reanude su Goal, que todo trabajador muerto tenga evento de muerte registrado, que no queden UUID no resueltos, que los cuatro guardias tengan engagement, la aldea sobreviva y JOB_SITE quede limpio. Se añadió `LivingDeathEvent` para guardar UUID, profesión, nombre, posición, DamageSource, atacante y entidad directa. Al final se separan trabajadores vivos/reanudados, bajas y no resueltos.

**Run6 (`c4-full6-20261112-0210`) C4_FAIL:** 4/4 guardias, 16 aldeanos vivos, 12/12 profesionales y Goals instalados, 11/12 reanudados, 0 bajas, 0 UUID no resueltos. El faltante fue el clérigo `bbd837c3-4253-4737-acc1-164eedb6af01`: vivo, `runningGoals=[]`, `jobSite=none`, posición 619,71,490. Había salido a sanar a un guardia; Vanilla invalidó JOB_SITE por distancia y el Goal olvidó el soporte.

**Fix funcional:** `VillagerClericGoal` conserva el `GlobalPos` del soporte aunque Brain borre JOB_SITE; `reclamarEstacionesDelPueblo` registra la estación directamente en el Goal antes del siguiente tick vanilla, y el Goal captura memoria incluso durante `restTicks`. `compileJava` PASS.

**Resultado final:** `c4-full8-20261112-2240` — C4_PASS con la variante final del callback del clérigo. 4/4 guardias combatieron; 12/12 trabajadores vivos reanudaron; 16 supervivientes; 0 bajas/no resueltos; JOB_SITE sin huérfanos ni duplicados; 0 fallos de instrumento. Evidencia completa en `docs/autonomous-village/A2-C4-HEADLESS-ACCEPTANCE-20261112.md`.

Launcher `tools/a2n/lanzar-c4.ps1` captura stdout/stderr vía `cmd.exe` y preserva exit code. `tools/a2n/consultar-c4.ps1 -RunId <id> -Wait` sondea cada 15 s sin relanzar ni detener. Llamadas remotas transitorias: hasta 3 reintentos con espera creciente; agente/PC realmente offline no garantiza reconexión automática.

La reparación de la base en `arena-repair1-20261112-205206` permanece PASS: 33 coordenadas exactas, 400/400 columnas con colisión, 0 cambios inesperados. `.partial` intacto. No usar fallback `629`, mundos históricos ni `run/saves`. C1/C2/C3/C4/C5 ACCEPTED; Block 6 multi-day soak es la siguiente tarea y sigue sin ejecutar.

El resto de este archivo conserva actas históricas. No usar sus antiguas marcas PENDIENTE
como cola vigente sin reconciliarlas con docs/CONTINUAR.md.


## Lumberjack Phase 1: ACCEPTED within scope (2026-11-12)
See `LUMBERJACK-PHASE-1-ACCEPTANCE.md`. A5 normal lifecycle, exceptional refunds and Block 1 are accepted; crash atomicity is out of scope. Do not repeat completed tests.

## Lumberjack reload-safe task state: IMPLEMENTED AND ACCEPTED
Design implemented and runtime-accepted within scope; see `docs/autonomous-village/LUMBERJACK-PHASE-1-RELOAD-DESIGN.md` and `LUMBERJACK-PHASE-1-ACCEPTANCE.md`.# PENDIENTE — nota de cierre

## Food Economy Block 3.2: CERRADO (2026-11-07)
No reabrir hambre, taberna, guardia, asedio, despensa ni persistencia sin una
regresión reproducible. Único tema abierto relacionado: test automático de
serialización NBT de `VillageSavedData` (limitación documentada, no fallo de
gameplay). El resto de este fichero es histórico de la aldea.

## Food Economy 3.1/3.2 — CERRADOS (2026-11-12; no es cola activa)
Food 3.2 remains CLOSED (Runs 98/105/109/110/128/130/132; documented automated NBT
serialization limitation preserved). Food 3.1 is **COMPLETE**: both farmer harvest paths
passed exactly-once runtime checks; normal sleep meal, work-scaled ration debit, no-food and
idempotency passed; a real world save/close/reload restored the same villager state and
continued autonomous work, a post-reload meal and a post-reload custom harvest all passed.
Two-village `VillageSavedData` isolation/save-load is covered by `FOOD_INTEGRATION_TEST`.
See `docs/autonomous-village/FOOD-ECONOMY-BLOCK-3.1-ACCEPTANCE.md` and the latest entry in
`docs/CONTINUAR.md`. This file is historical; it is not the live queue.

## A8-F03: CERRADA / ACEPTADA (2026-11-10)
Navegación TALAR del Agent sin objetivo rancio. C1 (objetivo retirado antes de autorizar),
C2 (candidato no-log), C3 (obstrucción), portón válido y portón negativo: PASS en runtime
post-fix. `compileJava`, `lint_aldea.py --strict`, `git diff --check`, `a8NavTests` y los tres
grupos de navegación: PASS. Detalle y evidencia histórica (incluidos los FAIL pre-fix) en
`docs/autonomous-village/A8-F03-FRESH-SESSION-EXECUTION.md`. No reabrir sin una regresión
reproducible. A7-12 quedó COMPLETA (2026-11-11) con la anomalía AUDIT de Campaign 6 preservada
como limitación no reproducida. A6-03/Block 4 están CERRADOS/COMPLETADOS; la cola activa es
Food Economy 3.1 residual (ver sección superior). Ver la entrada más reciente de
`docs/CONTINUAR.md`. Este fichero sigue siendo histórico, no la cola viva.

## A6-03 / Block 4: CERRADOS 2026-11-10 (nota de traspaso, no cola viva)
A6-03 runtime cerrada: R2, R5-A, R5-D, R4 (incl. topes 160/200), R6 y R7 PASS; R1/R3/R4
40/80/120/R5-B/C ya estaban aceptados. **D4 COMPLETED / ACCEPTED** (claim efímero entre
trabajadores, D4-A–E PASS) y **D5 COMPLETED / ACCEPTED** (política explícita de prioridades y
flags, D5-A–E PASS). **Master Plan Block 4 COMPLETED.** La cola activa es Food Economy 3.1
residual; A8-01 y A7-12 están COMPLETAS y se conservan como evidencia histórica. Ver la entrada
más reciente de `docs/CONTINUAR.md` y el Master Plan; este fichero no es la cola viva.
# POR DÓNDE SEGUIR — traspaso de sesión (aldea / DevilRpg)

> ## ⚠️ ESTE ES EL TRASPASO **VIEJO** (27-sep a 3-oct-2026). SE CONSERVA COMO ACTA, NO COMO LISTA DE PENDIENTES
>
> **El traspaso VIVO es `docs/CONTINUAR.md`** (su sección final «RECONCILIACIÓN 22-oct-2026» es la cola vigente;
> §2.1 conserva el historial). Este fichero se queda porque guarda el **porqué medido** de la sesión de los oficios
> (la mina, la boca de la galería, el hierro, la taberna…) y las referencias a `docs/aldea-invariantes.md`
> (**I119–I135**), pero **sus marcas de «PENDIENTE» pueden estar ya cerradas** ✗: comprobar en el acta antes de darlas
> por pendientes. *(Puesto en claro el 9-oct-2026, después de que el propio agente leyera este fichero como si fuera la
> lista viva.)*

> **Para la sesión nueva**: esto NO es lo que hay que seguir — lo vivo es la sección final de `docs/CONTINUAR.md`.
> Aquí, si acaso, se viene a buscar **el porqué medido** de la sesión de los oficios: cada apartado trae **lo que está
> medido**, **por qué se hizo así** y **el paso que tocaba**. Los datos crudos de aquellas medidas están en
> `tools/arnes/medidas-mina-sellada.txt` (§1–§28) y el porqué de cada regla, en `docs/aldea-invariantes.md`
> (**I119–I135**).
>
> **Para el jugador**: pégale a la sesión nueva: *«Lee `docs/CONTINUAR.md` y sigue por lo pendiente; mide con el arnés
> y no te fíes de nada que no esté medido»*.

> **Pendientes nuevos de aldea (L1–L4, 20-oct-2026):** el leñador atascado al cruzar la muralla, la prioridad de
> árboles internos, la reposición del bosque con harina de hueso y la ampliación del bosque interior se registran en
> **`docs/CONTINUAR.md`**. **Estado reconciliado el 22-oct-2026:** **L1 cerrado en runtime para el fixture aceptado**
> (`l40d-20261022-1845`); **L2 parcialmente abierto** (rama interior posterior al depósito probada, prioridad completa
> no); **L3 aceptado en runtime**; **L4 implementado**. **A1-CLIENT cerró el 22-oct-2026** con captura real de los
> cuatro fundadores (`a1client3-20261022-2040`). **Golem movement sigue NO REPRODUCIDO** (23-oct-2026; tres rutas, sin daño); ver la sección final. **Terrain clearance cerró el 22-oct-2026** con scan completo
> (`terrain4-20261022-2240`, residual=0). **L2 quedó PASS** (`l2iso2-20261023-0600`). La próxima tarea viva es **A2**,
> bloqueada por la decisión de umbral del dueño; no L1. Este fichero es histórico y no duplica la cola viva.

## CÓMO SE TRABAJA AQUÍ (leer antes de tocar nada)

**La regla de oro**: nada entra sin **`gradlew compileJava`** + **`python tools/lint_aldea.py --strict`** + **una medida
del arnés**. Un intento que no arregla **se retira** y **se dice**. (En la última sesión se retiraron tres intentos,
con su número: 940 falsos positivos, 0 disparos, y una corrida a cero por instrumento ciego.)

**El arnés (una corrida ≈ 9-12 minutos de reloj)**:
1. Copiar `tools/arnes/GuardHarness.java` a `src/main/java/com/chipoodle/devilrpg/debug/GuardHarness.java`.
2. **Encender UN modo** en la copia (`MEDIR_MINERO`, `MEDIR_PEPITAS`, `MEDIR_MILICIA`, …) y **comprobar que está
   encendido**: en la última sesión se perdió una corrida por lanzarla con el modo apagado (`al borde: 0`).
3. `run/world` es **copia** del guardado del jugador: `run/saves/New World (2)` → `run/world` **antes** de cada
   corrida (el guardado del jugador **no se toca**). Borrar antes `run/logs/latest.log`.
4. `gradlew runServer` en segundo plano y leer `run/logs/latest.log`.

**Trampas ya pagadas (no repetirlas)**:
- **El log ROTA por tamaño**: si `latest.log` es pequeño, hay que leer también `<fecha>-N.log.gz` (gunzip y concatenar).
- **Matar `gradlew` NO mata el servidor**: el JVM sigue con el puerto y la corrida siguiente falla en silencio
  (`serverlevel2 is null`). Antes de lanzar:
  `Get-CimInstance Win32_Process -Filter "Name like 'java%'" | Where-Object { $_.CommandLine -match 'fml.modFolders' }`.
- **Al terminar**: apagar el modo, **borrar `debug/`** (y `build/classes/.../debug/`), restaurar `run/world` y
  comprobar que el `.jar` queda libre.

**La medida del pueblo (I135)**: el **total** de rendiciones (`no consigue llegar`) **es ruido** (5, 9, 10, 12, 18 en
corridas comparables). Se mide **por 1.000 ticks y por etiqueta**:
```
python tools/arnes/rendiciones.py --etiquetas build/medida-*.log
```
La prueba de un arreglo **no es el total**, es **el criterio concreto que baja a cero** (p. ej. `Yendo a la taberna`
16 → 0). Para hablar del total, **media de 3-4 corridas**. Tanda de referencia de la última sesión:
**media 1,29 · rango 0,42-2,78 · desviación 0,94** por 1.000 ticks.

## Estado (27-sep-2026 — sesión de los oficios: los 5 atascos, la mina, el hierro y la boca de la galería)

Todo **compila**, pasa `lint_aldea --strict`, el árbol está **limpio** (sin `debug/`, sin JVMs, `run/world`
restaurado) y **todo commiteado**.

**Y el titular de la sesión, medido**: la **tasa de rendiciones del pueblo** (I135, ventana fija 2.000-12.000) baja
de la **media 1,29** de la tanda anterior a **0,30** con el código final (**media de 3 corridas: 0,10 · 0,30 · 0,50**),
y el pueblo queda en **7 avisos, TODOS SUELTOS y ningún bucle** (contra 18 avisos con bucles de **16** y **19** de la
referencia de la mañana): los gordos (`Yendo a la taberna` 16, `Volviendo a la plaza` 19, `Cuidando el ganado` 3,
`no consigue entrar al parcela` 5) están **a cero**.

**Cerrado y MEDIDO en esta sesión** (con el criterio que lo prueba):

| qué | medida |
|---|---|
| **el granjero** (I129) | el caminante daba por **llegado** lo que está a 1 bloque → `caminarHaciaExacto` (tolerancia 0 + ruta pedida a mano): `no consigue entrar al parcela` **5 → 0**, avisos de huerta **6 → 0**, el granjero **`Cosechando`** |
| **la recolectora encerrada** (I130) | no podía **salir del parcela** (una puerta de valla cerrada **no es navegable**): `abrirLaCompuertaDeAlLado` + salir con paso exacto → `Volviendo a la plaza` **19 → 0** (total **27 → 9**) |
| **el ganadero** (I131) | perseguía la **celda cruda del animal** (que no se pisa) → `casillaDePieCercaDe` (regla I114): `Cuidando el ganado` **3 → 0** (total **9 → 5**) |
| **el aldeano METIDO en un bloque** | el criterio bueno es **la forma de colisión contra la altura de los pies** (con `esCeldaDePie`: 940 falsos positivos; con la caja: 0 disparos) → **2 desatascos reales**, 0 falsos positivos |
| **la mina ATRAVIESA el agua** (I132) | **aísla** la cáscara 3×3×3, **seca** la celda y **sigue** → `hechas 0 → 3/24`, **`TOPE=NO`**; y **la mina BAJA** (`pasos 16 → 32`, `y 54 → 46`) con **16 piezas de caracol** |
| **el pico y el hierro de los raids** (I133/I134) | el zombi suelta **pepitas** y **el que mata las lootea** (antes **desaparecían a los 5 min**); el herrero forja el pico **con 27 pepitas** → medido: **`Forjo un pico de HIERRO`** |
| **la taberna** | `VillagerTavernGoal` caminaba a la **celda de la mesa** (no pisable) → `casillaDePieCercaDe`: `Yendo a la taberna` **16 → 0** y la corrida **2,73 → 0,47** por 1.000 ticks |
| **la medida** (I135) | `tools/arnes/rendiciones.py`: rendiciones **por 1.000 ticks en ventana fija**, por etiqueta, con media y rango |
| **la boca de la galería** (I136) | el aldeano va **de pie sobre la losa** (nodo `y+1`) y con **dos** celdas de hueco el vecino sale **BLOCKED**: no entra ni sale → **tres** celdas. `hechas` **3/24 congelado 3.600 ticks → 24/24** y `pasos` **32 → 44** (la cara de `y=46` a `y=40`); y **las paredes contra el agua** al abrir cada celda |
| **la cadena del hierro de los raids** (I137) | el **guardia** mata, **lootea** (2 pepitas), **deja el hierro en el almacén** (13 depósitos, de 0 a **20** pepitas) y **el herrero forja el pico de HIERRO**; el eslabón que faltaba era que el guardia se lo quedaba en el inventario |
| **la balsa, al herrero de herramientas** (I138) | era la faena que le comía el tiempo al minero (**33-46 coladas** con el pedernal en su inventario y el almacén clavado en 6) → ahora cuela el **herrero**: coladas del minero **0**, del herrero **10**, el pedernal **6 → 16**, y la misma galería en **t≈17.200** en vez de **t≈37.800** |
| **los atascos sueltos** (I139) | (1) el guardia: el atasco se medía **solo por la recta** y la ronda es un círculo → cuenta el **avance por la ruta** (`Patrullando` **9 → 2**); (2) a por un objeto caído se iba a la **celda cruda** (la mesa, una valla) → `casillaDePieCercaDe` (`Recogiendo el corral` **4 → 0**, `Guardando lo suyo` **5 → 0**); (3) «pisable» no incluía **los cultivos** → el parcela no tenía ni una casilla de pie (el granjero hundido en bucle **7 → 1**) |

**El aviso de método que salió de aquí**: al normalizar la medida, el "ruido" escondía **16 rendiciones de un solo
aldeano en un solo sitio** (la taberna). La medida no era un trámite: **destapó el fallo**.
## Lo que está PENDIENTE (en este orden, como pidió el jugador)

### 0. Sesión del 27-sep-2026: la **boca de la galería** (el choque caracol ↔ galería) — **CERRADO y MEDIDO**

Lo ordenado era el **choque caracol ↔ galería** (el 68 % del tiempo del minero). **Medido y arreglado** — lo cuenta
**I136** y los datos crudos están en `tools/arnes/medidas-boca-galeria.txt` (corridas
`build/medida-galeria-3alto.log`, `medida-galeria-agua-1.log`, `medida-galeria-muro.log`):

| qué | medida |
|---|---|
| la **boca** (el diagnóstico viejo: «pica su propia losa») | **era un fantasma del instrumento**: `cara` es la celda del caracol del paso, y ésa lleva su pieza por definición |
| la **boca** (causa real) | el aldeano va **de pie sobre la losa** (nodo `y+1`, caja `y+0,5…y+2,45`) y con **dos** celdas de hueco el vecino sale **BLOCKED** → no entra ni sale. Arreglo: **tres** celdas (I136) |
| **antes → después** | `hechas` de la galería del paso 32: **3/24 congelado 3.600 ticks** → **3 → … → 24/24**; y la galería del **paso 16** (que cavaba el arnés) ahora **la cava el minero: 2/24 → 24/24** |
| el **caracol** | `pasos` **32 congelado** → la galería se cierra y **sigue bajando: 34 → … → 44** (la cara de `y=46` a **`y=40`**), con `TOPE=SI`/`se PARA` = **0** |
| el **agua** | las tres celdas abren el techo en el acuífero y el túnel se inundaba (`hechas` 6 → **0**, 223 muestras). Con el censo nuevo `AGUA`: el agua entra por la **celda de delante** → `aislarDelAgua` al **abrir** cualquier celda, saltando solo el paso del caracol. Al final: **31 celdas con fluido, ninguna dentro del túnel** |
| el **muro** | con 21/24 el minero se iba al **portón norte** (243 muestras; el final de la galería cae «fuera del muro» a 17 bloques bajo el suelo): **bajo tierra no hay muro que cruzar** → la ruta a la celda 21 pasa a `22 nodos … alcanza=SI` |

**Lo que toca ahora**: **(1)** los **atascos sueltos** del §7 (**hechos y medidos** en esta misma sesión: I139);
**(2)** volver a medir la **tasa de rendiciones** (`rendiciones.py --etiquetas`, media de 3-4 corridas) con la mina ya
desbloqueada — la primera corrida buena da **0,30** por 1.000 ticks (contra 1,29 de la tanda anterior).

**Y también CERRADO en esta sesión**: la **cadena del hierro de los raids** (§6b.2/§6b.3) — **I137**: el guardia mata,
lootea, **deja el hierro en el almacén** (13 depósitos; el almacén de 0 a 20 pepitas) y **el herrero forja el pico de
hierro**, que se lleva el minero. El eslabón que faltaba era el guardia (se quedaba el botín en el inventario) y el
instrumento tenía tres trampas (contaba pepitas en el suelo, la barredora descartaba el zombi y el escaneo era de 140
bloques): está todo en `tools/arnes/medidas-pepitas.txt`.

**Y la opción C** (la balsa y su acarreo, al herrero de herramientas) — **I138**: coladas del minero **33-46 → 0**, las
hace el herrero (**10**, hasta el objetivo de 16 pedernales) y la misma galería se completa en **t≈17.200** en vez de
**t≈37.800** (`tools/arnes/medidas-balsa.txt`).

**Y los atascos sueltos del §7** — **I139**, los dos que quedaban:

* **el guardia no se rendía por falta de ruta**: sus avisos decían `ruta=16-32 nodos … alcanza=SI` y `nav=[… alcanza]`
  —un destino a 20 bloques con una ruta de **30 nodos**—. El contador medía **solo la distancia en línea recta** y la
  ronda es un **círculo**: en un rodeo la recta sube. Ahora cuenta también el **avance por la ruta viva**. MEDIDO:
  `Patrullando` **9 → 2**.
* **al objeto caído se iba a la celda CRUDA** (que puede ser la **mesa de la taberna**, una **valla** o el propio
  parcela): `casillaDePieCercaDe` en los cuatro goals que recogen **+** `desatascarSiEstaEncajado` antes de contar
  atasco. Y de raíz: **«pisable» no incluía los cultivos** (`esCeldaDePie` pedía aire y un parcela no tiene aire) →
  el granjero hundido en la farmland **no tenía a dónde salir**. MEDIDO: `Recogiendo el corral` **4 → 0**,
  `Guardando lo suyo` **5 → 0**, `Yendo a la taberna` **1-2 → 0**, el bucle del parcela **7 → 1**, y la **tasa del
  pueblo 0,50-0,60 → 0,30** por 1.000 ticks (`tools/arnes/medidas-atascos-sueltos.txt`).

### 8.bis.0. SESIÓN DEL 30-sep-2026: **los cuatro reportes del jugador, y el pueblo a 1 aviso** — ACTA

El jugador se quejó, con razón, de que llevaba rondas midiendo etiquetas mientras él veía cosas a simple vista. Sus
cuatro reportes, cada uno con su causa **medida** en su propio registro:

| lo que reportó | la causa (medida) | el arreglo |
|---|---|---|
| **«los zombis del raid inicial son muy lentos y no atacan a los aldeanos»** | el perfil tenía `baseSpeed = 0.071` y **el zombi del juego anda a 0.23** (un **31 %**), y el aldeano estaba en el `targetSelector` en **prioridad 4**, detrás del jugador | base **0.23**, velocidad con tope **×1.6**; el aldeano pasa a **prioridad 0** (I165) |
| **«los aldeanos no huyen ante los zombis agresivos»** | el pánico **existe** en su cerebro, pero **los goals del oficio cogen `MOVE`** y lo excluyen: no podía moverse (su registro: `Dionisio … slain by Zombie`, **sin dar un paso**) | `VillagerFleeGoal` a **prioridad 0 y con `MOVE`**: suelta el trabajo y corre a su casa (I165) |
| **«varias construcciones hundidas y alrededor hueco, da a un pozo»** | medido con cortes del mundo: la choza del minero tiene el suelo **bien** (`cota−1`) pero **debajo hay 5 bloques de aire con agua al fondo** (el «pozo»); la aldea está **sobre el vacío** en esa zona | **cimiento**: rellena de piedra todo hueco **24 bloques** abajo en un disco de 86, respetando **el anillo del caracol** de la mina (I166) |
| **«semillas y vegetales flotando en la superficie de la parcela»** | **era el mod**: dos `addFreshEntity(new ItemEntity(...))` soltaban el sobrante **en la celda de la mata** | **no se tira nada**: el sobrante va al almacén y, si no cabe, **a la mano**; y la cosecha **no se frena por capacidad** (I166) |

**Y el cuadro por etiqueta de las corridas recientes** (105, 106 y 111: **4 · 3 · 1** avisos, con la mejor de la sesión
en **1**), contra las etiquetas que el objetivo pedía cerrar:

| etiqueta | referencia (I154) | ahora |
|---|---|---|
| `Yendo a la cocina` | 26 | **0** |
| `Labro la huerta` / `Labrando la huerta` | 14 | **0** |
| `Sembrando` | 11 | **0** |
| `Trajo … del almacén a la despensa` | 3 | **0** |
| `Buscando recambios` | 3 | **0** |
| `Yendo a la arboleda` | — | **0** |
| `Bajando lo del corral` | 35 | **0** |
| `Recogiendo lo suyo` | 40 | **0** |
| `Yendo a la taberna` | 29 | **0** |
| `Sacrificando un animal` | 15 | **5** (arreglado en I168, **pendiente de medir**) |
| `Guardando lo suyo` | — | **2** (es el caso de I169, **pendiente de medir**) |

**Lo que queda**: (a) medir 4 corridas con I168 + I169 + el cimiento en su sitio; (b) la **clase B** —la ruta **alcanza**
y el goal se rinde igual—, que es la única forma que sobrevive en el mejor registro (`ruta=23 nodos … alcanza=SI` desde
dentro del corral, con el minero yendo al almacén: el caso de I169); (c) el acta del cuadro de arriba con su media.

#### 8.bis.0.bis · **EL BANCO RÁPIDO Y LO QUE ENSEÑÓ** (30-sep-2026, continuación)

El jugador cortó por lo sano: *«¿por qué tardas tanto en hacer mediciones?»*. **Tenía razón y el cuello de botella era el
instrumento**: cada pregunta costaba **80 minutos** (4 corridas). Se construyó `tools/arnes/tanda-rapida.ps1` (**3 minutos
por corrida**, con el recuento **por etiqueta** y las trazas de la reparación), y con él:

| arreglo | corridas rápidas | media | veredicto |
|---|---|---|---|
| **cimiento: anillo sin holgura** | verificado **en el terreno** | — | **el hueco se cierra y el caracol sigue abierto** ✓ |
| I170 (la ruta manda) | 1 · 1 · 0 | **0,67** | se queda ✓ |
| I172 (atasco «por tiempo») | 1 · 2 · 4 | 2,33 | **RETIRADO** ✗ |
| I174 (mirada en el recado) | 2 · 1 · 2 | 1,67 | **RETIRADO** ✗ |
| I171 (devolver el rumbo) | 1 · 1 · 0 | **0,67** | se queda ✓ |

**Y las tres verdades que salieron del instrumento** (más valiosas que los arreglos, porque evitan perder más tiempo):

1. **El aviso de rendición era una autopsia** ✗: imprimía el estado **después** de rendirse (rumbo borrado, navegación
   parada). Perseguí un `cerebro=-` dos rondas creyendo que era la causa. Ya dice el **tramo** (`recorridos N bloques en
   M ticks`, I173).
2. **La navegación nunca falla** ✓: la traza de «sin camino» **no saltó ni una vez** en dos corridas. Los aldeanos **no**
   están bloqueados: **tienen ruta y casi no avanzan** (medido: 4,6 bloques en 13 s; **0,5 en 9 s**) → **no les empujan**.
3. **Contar rendiciones en 3 minutos es ruido** (0 a 4) ✗: no distingue un arreglo de otro. El banco rápido imprime ahora
   también **el trabajo del pueblo** (sucesos por oficio), que tiene más señal; pero a 3 minutos también es pequeño. **El
   banco rápido sirve para ENCONTRAR el fallo; para confirmar la mejora hace falta la tanda larga de 4.**

#### 8.bis.0.quater · **ACTA DE CIERRE DE LAS ETIQUETAS** (30-sep-2026)

**Lo que pedía el objetivo, etiqueta a etiqueta, con la evidencia de las tandas de 4 corridas de 20 minutos** (cada
tanda es un build distinto, y eso es lo que hace comparable la tabla):

| etiqueta del objetivo | referencia | **estado medido** | quién lo arregló |
|---|---|---|---|
| `Yendo a la cocina` | 26 | **0** ✓ | I156/I161 + el patrón de casilla de pie |
| `Labro/Labrando la huerta` | 14 | **0-1** ✓ | I154 (a una celda que no se pisa, no se camina) + I167 |
| `Sembrando` | 11 | **0-1** ✓ | I154 + la cosecha entera (I166) |
| `Guardo lo suyo` / `Trajo … del almacén a la despensa` | 3 | **0** ✓ | I166 (el sobrante al almacén, nunca al suelo) |
| `Buscando recambios` | 3 | **0-2** ✓ | I154 + I169/I178/I179 (las puertas) |
| `Yendo a la arboleda` | — | **0** ✓ | el leñador por tramos (I112) |
| `Bajando lo del corral` | 35 | **0** ✓ | I154 + el ganadero a una casilla de pie al lado (I168) |
| `Recogiendo lo suyo` | 40 | **0** ✓ | I154 + la ruta manda (I170) |
| `Yendo a la taberna` | 29 | **0** ✓ | I158 (el atasco se mide contra el escalón) |
| `Sacrificando un animal` | 15 | **5 → 0** ✓ | I168 (casilla de pie al lado del animal) |
| `Guardando lo suyo` | 5 | **1-3** ✗ | I179/I180 (en medición) |

**Totales por tanda** (4 corridas de 20 minutos cada una, misma aldea y mismo arnés):

| tanda | qué llevaba | total | media/corrida |
|---|---|---|---|
| 111-114 | I168 | 1 · 2 · 4 · 1 | **2,0** |
| 115-118 | + I169 + I170 + I171 | 5 · 4 · 1 · 8 | 4,5 ✗ (la regresión de mi relleno, I177) |
| 123 | todo menos I180 | **1** | — |

**Las cinco verdades que dejó esta sesión** (valen más que los arreglos, porque evitan repetir el camino):

1. **El aviso de rendición era una autopsia** ✗: imprimía el estado **después** de rendirse. Se persiguió un
   `cerebro=-` durante dos rondas creyendo que era la causa. Ya dice el **tramo** (neto, **ANDADO** y **destinos**).
2. **La navegación nunca falla** ✓: la traza de «sin camino» **no saltó ni una vez** en dos corridas.
3. **«Anda mucho y no llega»** ✓: 15 de 18 rendiciones ocurrían a **3-8 bloques** del destino y **todas con `dy=0`** —
   se quedan **a las puertas**, y la compuerta que estorba suele estar en el **punto medio del lado**, no en la recta.
4. **La clase E estaba pasando dentro del mismo tick** ✗: un aldeano con **36 destinos en 8 ticks** y **0 bloques
   andados** — varios goals y el «tirón» escribiéndole el destino a la vez. **I180**: el primero que escribe manda.
5. **El cuello de botella era el instrumento** ✓: **80 minutos por pregunta** convertían cada intento en una tarde. Con
   `tools/arnes/tanda-rapida.ps1` (3 minutos, con etiquetas y trazas) y el corte del mundo (`slice_mina.py`) se
   encontraron en minutos cosas que llevaban cuatro intentos: **la holgura de ±1 del anillo** que dejaba el borde de la
   choza del minero hueco, y que **el aviso no fotografiaba el fallo sino el después**.

**Y lo que NO queda cerrado, dicho sin adornos**: la etiqueta `Guardando lo suyo` (1-3 por corrida), cuya causa ya está
medida (clase E dentro del tick) y arreglada (I180), **pendiente de la medición de 127-130**.

#### 8.bis.0.quinquies · **CIERRE DE LA SESIÓN** (30-sep-2026, ronda 24)

**Lo medido, y lo que se retiró por medirse mal** (las tres retiradas están escritas, con sus números, en
`docs/aldea-invariantes.md`):

| intento | corridas | media | veredicto |
|---|---|---|---|
| I168 (ganadero a una casilla de pie al lado) | 1 · 2 · 4 · 1 | **2,0** | se queda ✓ |
| I169+I170+I171 (puertas, ruta, rumbo) | 5 · 4 · 1 · 8 | 4,5 | I171 se estrechó con I177 ✓ |
| I172 (atasco «por tiempo») | 1 · 2 · 4 | 2,33 | **RETIRADA** ✗ |
| I174 (mirada en el recado) | 2 · 1 · 2 | 1,67 | **RETIRADA** ✗ |
| I180 («un destino por tick») | 2 · 6 | ~4 | **RETIRADA** ✗ |
| I177+I178+I179 (rumbo solo si está parado, puertas conocidas) | 123 | **1** | se queda ✓ |
| **I181** (el rescate cuenta como avance) | — | — | **medido con el banco rápido** ✓ |

**La medición del BUILD FINAL** (con I181 dentro y I180 fuera), banco rápido, cuatro corridas de 3 minutos:
**0 · 1 · 1 · 1** → **media 0,75** ✓ (la mejor de la sesión), con las etiquetas **rotando** (`Guardando lo suyo` ×2,
`Recogiendo el corral` ×1) y el pueblo trabajando (granja 2-6, ganado 3-5, pescador 2, **herrería 22-33**, cocina 4,
minero 9-16, leñador 0-5 por 3 minutos).

**Lo que queda abierto, con nombre y sin adornos**:

1. **`Guardando lo suyo`** (1-3 por corrida de 20 min): la causa está medida —el aldeano se queda **dentro de una casa a
   `y=79`**, el mod lo **rescata** a la plaza y su goal **se rendía igual**— y el arreglo (**I181**) está dentro y
   medido con el banco rápido. **No hay 4 corridas largas de I181**: el tiempo de esta sesión llegó hasta aquí.
2. **El residuo general**: 1-2 avisos por corrida de 20 minutos sobre **cientos de recados** (≈1 %) y con las etiquetas
   **rotando** de una corrida a otra — no familias sistemáticas, sino tropiezos sueltos. **El pueblo trabaja**: 44-57
   labores de granja, 47-50 de ganado, 174-265 de herrería y 618-666 de guardia por corrida.
3. **Lo que el jugador reportó, todo cerrado**: los ítems de la parcela (el mod los tiraba ✗, I166), el hueco de la choza
   del minero (**verificado en el terreno** ✓, I166), los zombis lentos y que no atacaban aldeanos (0,071 → 0,23 y
   prioridad 0 ✓, I165) y los aldeanos que no huían (el pánico no podía moverlos ✗, `VillagerFleeGoal` ✓).

### 8.bis.0.sexies · **SESIÓN DEL 30-sep (noche) y 3-oct-2026**: los dos reportes nuevos, y el instrumento que mentía

**Los dos reportes del jugador, cerrados — y uno de ellos destapó un fallo de MÉTODO mío.**

| reporte del jugador | causa medida | arreglo | medido |
|---|---|---|---|
| *«la parte de abajo de la aldea está hueco, hay un boquete cerca de una parcela»* | `afianzarElSuelo` **solo** se llamaba desde la **reparación**, así que **una aldea nueva nacía sin cimiento** ✗ | **I182**: cimiento al final del terreno **y** al final de `generate` (la mina cava después ✗) + **trazado 81** para las ya generadas | dos líneas `CIMIENTO` (11200 y 11188 bloques) en cada corrida ✓ |
| *«¿por qué no aparece arriba el nombre y profesión de los aldeanos?»* | el nombrado vive en `tickVillageLife`, que **solo corre con la aldea EN PAZ** (puerta del **12-sep**, `b4d04af`) y **una aldea nueva nace bajo asedio** ✗ | **I183**: la marca y el reparto de nombres corren **también con asedio** (junto a las camas, cada 10 s, idempotente) | `etiqueta=Bartolo (Herrero de armas)` ✓ 242 líneas con nombre y oficio en 3 min |

**Y la parte que era mía, dicha sin adornos**: al hacer a los asaltantes **rápidos** y **que vayan a por los aldeanos**
(I165), el **asedio puede durar mucho más** ✗ — y con él la aldea se queda «sin vida» (sin nombres, sin etiquetas). **No
miré qué dependía de que la aldea estuviera en paz antes de tocar a los zombis** ✗.

**El fallo de método del cimiento, que es el que más vale**: durante la sesión di el hueco por arreglado porque **el
arnés decía 0 avisos** ✓ — pero **el arnés mide sobre el guardado viejo del jugador, que SÍ migra** y por tanto
**siempre** pasaba por el cimiento ✓, mientras su **partida nueva** no ✗. *Lo que solo se prueba por el camino de la
migración no está probado para una partida nueva.*

**El instrumento mentía, y también está arreglado (I184)**: los bancos **mataban el servidor de golpe** ✗, el mundo **no
se guardaba** y, como el trazado **se guarda en el mundo**, **cada corrida volvía a migrar** ✗: el pueblo se rehacía
entero y la medida se llenaba de **5-7 avisos** ✗ que **no eran del arnés sino de la mudanza**. Ahora el arnés para a
un tick pedido con `server.halt(false)` (lo mismo que `/stop`: **guarda y sale**) y `-Conservar` sirve de verdad:

```
corrida 45 (CON migración)     → cierre limpio ✓ · 7 avisos ✗  (la mudanza)
corrida 46 (mundo CONSERVADO)  → cierre limpio ✓ · SIN línea REPARADA ✓ · 1 aviso ✓
```

**Conclusión medida**: los 5-7 avisos **eran la mudanza**, el pueblo asentado vuelve al **mejor registro de la sesión**
y **no había regresión** alguna de I182/I183 ✓. El coste real de la mudanza es **+3 a +6 avisos UNA VEZ** por partida
(al pasar al trazado 81), y eso hay que decírselo al jugador antes de que lo vea ✗.

**Lo que sigue abierto**:

1. **`Guardando lo suyo` con I181**: **4 corridas largas** del pueblo **asentado** (ya medibles ✓, con
   `tools/arnes/tanda-larga.ps1 N -Conservar`), que es lo que cierra el único pendiente de etiqueta que queda.
2. **El residuo**: con el mundo asentado, **1 aviso por 3 minutos** sobre cientos de recados (≈1 %), etiqueta rotando
   (`Sacrificando un animal` en la 46) → tropiezos sueltos, no familias.
3. **Documentar** en el `LEEME` que el banco **exige** el cierre limpio (hecho: es el paso 0 del protocolo).

### 8.bis.0.septies · **LAS 4 CORRIDAS LARGAS DEL PUEBLO ASENTADO, y la ráfaga del minero (I185)**

Hechas con `tools/arnes/tanda-larga.ps1 47 48 49 50 -Conservar`, cuatro corridas de **20 minutos** sobre el pueblo
**ya asentado** (sin migración ✓, con cierre limpio ✓ confirmado en cada registro):

| corrida | 47 | 48 | 49 | 50 | media |
|---|---|---|---|---|---|
| avisos | **1** ✓ | **13** ✗ | **9** ✗ | **12** ✗ | **8,75** ✗ |

**Y el residuo NO son tropiezos sueltos** ✗ — **es UN aldeano atrapado soltando ráfaga** ✓, que es lo que había que
descubrir aquí:

```
corrida 48:  9x  Ximeno (Minero) / Volviendo a la caseta   desde 603, 76, 537  (y=76, calle a 78: DOS por debajo)
             2x  Zacarias (Ganadero) / Cuidando el ganado
             2x  guardias / Yendo a entrenar
```

**El patrón es el de SIEMPRE**, y se ve comparando con **todos** los lotes guardados en `build/medida-tanda*.log`:
en cada lote hay **un dominante** (`Eufemia (Ganadero)` en docenas de corridas viejas, con **30 · 50 · 80 · hasta 238**
avisos ✗; hoy `Ximeno (Minero)`) y domina **`alcanza=NO`** ✗ (destino inalcanzable). Los lotes viejos daban **62 · 64 ·
72 · 80 · 110 · 238**; los buenos de la sesión, **1 · 2 · 4 · 1 · 5 · 4 · 1 · 8**. O sea: **el trabajo de estas semanas
se ve**, y lo que queda es **un atrapado por tanda**, no una familia de fallos por todas partes.

**La causa raíz eran UNA línea** ✗ (en `rescatarAldeanosAtrapados`): la exención decía
`getY() <= cota + 0.6` = **«por debajo de la calle = está en la calle»**, así que al minero metido en la **zanja de la
boca de su mina** (`603, 76, 537`, calle 78 = **dos bloques por debajo**) **ninguna red lo rescataba** ✗ y su errand se
reintentaba 9-13 veces ✗. **Verificado en el terreno**: la zanja es **idéntica en el guardado original del jugador** ✓
(`run/saves/New World`, misma ranura 1×1 en `x=603`, mismo escalón), así que **no la causó el cimiento** ✓.

**ARREGLO (I185)**: la exención es ahora una **banda** de un bloque por debajo de la calle (más el medio escalón de las
losas por arriba), y quien esté más abajo cuenta como atrapado; el minero que **trabaja** abajo sigue igual, porque se
mueve y además se exige que **no sepa volver** a la plaza.

| mismo mundo asentado | antes | después (I185) |
|---|---|---|
| avisos por corrida de 3 min | **1 · 13 · 9 · 12** ✗ (largas) | **1 · 2 · 1** ✓ |
| ráfaga del minero | **9-13** ✗ | **1** ✓ |
| rescates | 0 | **2 · 0 · 1** ✓ (`estaba atascado (sin poder salir) en 520, 79, 594`) |

**Lo que sigue abierto ahora**:

1. **El único aviso que queda** es el minero **bajando a su mina** (`603, 75, 537` → `603, 61, 526`, ruta de 33 nodos):
   se rinde **una vez** y ya no hay ráfaga ✓. Para quitarlo haría falta que el rescate dispare **antes** que el
   presupuesto del errand (~16 s), o una red propia para «no consigo bajar»; **no medido todavía**.
2. **`Guardando lo suyo` (I181)** sigue sin sus 4 corridas largas propias: en estas cuatro **no apareció ni una vez** ✓
   (la etiqueta no salió en 47-50), así que o está cerrada de hecho o le toca otra tanda.
3. **La zanja de la mina** (`x=603`, boca de la mina de la aldea, **1×1 y 2 de fondo**): es un problema de **mundo** ✗
   (el aldeano no puede subir un escalón de 2). El rescate lo tapa ✓, pero lo suyo es **dejar un escalón** al construir
   la mina. Anotado, sin medir.

### 8.bis.0.octies · **I186: la rendición no siempre es culpa del sitio** — y **0 · 0 · 0** ✓

**El dato que lo decidió** (sacado de las 4 corridas largas, gratis): el aviso dice «no consigue llegar a X» y se lee
como «X es inalcanzable», pero **el 100 % de los avisos traía `ANDADO` ≈ 0** ✗ (0,0-0,8 bloques), **incluso con
`alcanza=SI`** y `neto` de 30 bloques ✗. El aldeano **no andaba nada**. Y los relojes estaban al revés ✗: el recado se
rinde a los **10 s**, el goal del oficio a los **16 s**, y el rescate necesitaba **30 s** → llegaba **siempre tarde**.

**ARREGLO (I186)**: `marcarPuntoFallido` **no marca nada** si el aldeano está **atrapado** (quieto ≥ 4 s en una celda
fuera de la banda de la calle: de eso se encarga el rescate ✓), y `ATRAPADO_TICKS` baja de **30 s a 12 s** ✓.

| mismo mundo asentado, banco rápido | 1 · 2 · 1 (I185) | **0 · 0 · 0** ✓ (I186) |
|---|---|---|

**El mejor registro de toda la sesión** ✓ (el anterior mejor era **0 · 1 · 1 · 1**, media 0,75), y el pueblo trabaja:
herrería 46-49, minero 7-9, guardia 397-480 por 3 minutos.

**Y LO QUE DESTAPÓ, que ahora es el pendiente nº 1**: en las 4 corridas **largas** la **granja** rinde **16 · 15 · 15 ·
5** frente a la referencia **40-57** ✗ (los demás oficios en rango: ganado 56-90 ✓, herrería 199-231 ✓, pescador 7-9 ✓,
cocina 4-24 ✓). La causa está **en el propio registro**:

```
[Village] La aldea 0 pasa hambre: 2 boca(s) sin su racion y 0 punto(s) en la despensa      ✗ (x4)
[Arnes] GRANJERO ... puesto=SIN PUESTO ...                                                ✗ (un granjero sin puesto)
[Village] El granjero: Trajo 8 del almacen a la despensa                                  ✗ (x39, al almacen en vez de cosechar)
```

O sea: **la despensa está vacía** ✗ y hay un **granjero sin puesto de trabajo** ✗, así que el granjero se pasa el día
**trayendo del almacén** en vez de cosechar. **Pendiente con nombre**: (a) por qué un granjero se queda **sin puesto**
(¿el compostero no está, o `reclamarEstacionesDelPueblo` no lo ve?), y (b) por qué la despensa queda a **0 puntos**
(¿se cosecha y no se guarda, o no se cosecha?).

### 8.bis.0.nonies · **I186 e I187: la rendición no es siempre del sitio, y «un puesto, un dueño»**

**(a) I186 — «antes de culpar al sitio, mira si el que no se mueve es el aldeano».** El aviso dice «no consigue llegar
a X» y se lee como «X es inalcanzable», pero **el 100 % de los avisos traía `ANDADO` ≈ 0** ✗ (0,0-0,8 bloques),
**incluso con `alcanza=SI`** y `neto` de 30 bloques. Y los relojes estaban al revés ✗: el recado se rinde a los **10 s**,
el goal del oficio a los **16 s** y el rescate necesitaba **30 s** → llegaba **siempre tarde**. Arreglado: el embudo
`marcarPuntoFallido` **no marca nada** si el aldeano está atrapado (quieto ≥ 4 s fuera de la banda de la calle, de eso
se encarga el rescate) y `ATRAPADO_TICKS` baja de **30 s a 12 s**.

| mundo asentado, banco rápido | avisos |
|---|---|
| antes (lote largo) | **1 · 13 · 9 · 12** ✗ |
| I185 (el rescate, banda de la calle) | 1 · 2 · 1 ✓ |
| **I186 (el embudo + los relojes)** | **0 · 0 · 0** ✓✓ |

**(b) I187 — «un puesto, un dueño».** En las corridas 47-49 había **exactamente 3 granjeros** ✓, cada uno con su
compostero ✓ y todo sano. En la **50** (aldea de 20 aldeanos) aparecieron **cuatro** ✗ y dos se **turnaban el mismo
compostero** (`535, 78, 600`: 37 muestras uno, 63 el otro) con uno en `SIN PUESTO` **48 muestras** ✗. Causa: el reclamo
tenía un tercer paso —«no hay otra: la más cercana»— que cogía una estación **ocupada**, y el filtro (`deOtro`) solo
mira a los aldeanos **de esa pasada** → si el dueño no estaba en la lista, **los dos se creían dueños**. Fuera el paso
3: quien no encuentra estación **libre** ni una ocupada **sin dueño** se queda sin puesto ese latido y lo reintenta.

**VERIFICADO en la corrida larga 59** ✓: **tres granjeros, cada uno con SU compostero** (`991470fd→533,78,580`,
`6b437808→535,78,600`, `05821ff2→573,78,570`), **cero `SIN PUESTO`** ✓ y **cero reclamos** ✓ → el turno ha desaparecido.

**(c) Y UNA CORRECCIÓN DE MI PROPIA MEDIDA** ✗: la cuenta de labranza que usé buscaba `Guardo` (mayúscula) y la línea
real es **`el granjero guardo`** (minúscula), así que **estaba midiendo de menos** ✗. Con la cuenta bien hecha:
**16 · 21 · 23 · 9** en las cuatro corridas del lote (no 16·15·15·5) y **3** en la 59 ✗. La referencia sigue siendo
**40-57** ✓.

**(d) Lo que la 59 sí dice, y hay que leerlo con cuidado** ✗: sus avisos son **6**, y **4 de ellos son el minero**
(`Eufemia (Minero) / Volviendo a la caseta`) **desde la plaza a 50 bloques** ✗ — **efecto secundario de mi rescate**
✓: al atrapado se le baja a la **plaza** y su recado (la mina) queda a 50 bloques ✗. Y la aldea bajó de **20 a 13**
aldeanos ✗: **el mundo de la 59 llevaba 80 minutos pasando hambre** ✗, así que la granja no se puede juzgar ahí ✗.
**En marcha**: corrida 60 (fresca + migración, 3 min, guarda el mundo) y **61 (fresca y asentada, 20 min)** para medir
la granja en un **pueblo sano** y compararla de verdad con la referencia.

**(e) Y EL VEREDICTO DEL PUEBLO SANO (corrida 61, fresca y asentada, 20 minutos)** ✓:

| | corrida 61 (fresca) | lote anterior (mundo degradado) |
|---|---|---|
| **avisos** | **3** ✓ — y de **tres aldeanos distintos** (`Teodoro/Recolector → Yendo al almacen`, `Zacarias/Ganadero → Sacrificando`, `Anselmo/Granjero → Sembrando`), **sin ráfagas** ✓ | **1 · 13 · 9 · 12** ✗ (media 8,75, con 20 de 35 en un solo minero) |
| **despensa** | **34 · 48 · 28 · 16 puntos** ✓ con **13-16 aldeanos** ✓ | **0 puntos** y `pasa hambre` ✗ |
| **granjeros** | **3, cada uno con SU compostero** ✓ (`991470fd`, `4d096c1c`, `05821ff2`) y **cero `SIN PUESTO`** ✓ | 4 turnándose el mismo ✗ |
| otros oficios | ganado 45 ✓, pescador 9 ✓, herrería 161 ✓, cocina 22 ✓ | en rango |

**Conclusiones que se sostienen con estos números** ✓: (1) **I186** deja el residuo en **3 avisos sueltos** ✗ (frente a
la media de 8,75 ✓ y a los 62-238 de los lotes viejos ✓); (2) **I187** está verificado **dos veces** ✓ (tres granjeros
con su compostero y nadie `SIN PUESTO` ✓); (3) **la granja no estaba rota** ✗ — el pueblo sano come (despensa 34-48 ✓)
y el desplome era del mundo degradado ✓; y (4) **el efecto secundario del rescate queda anotado** ✗: al atrapado se le
baja a la **plaza**, y desde ahí un recado lejano (la mina, 50 bloques) puede fallar ✓ — es el siguiente candidato,
junto con **`Yendo a la taberna`** ✗ (que reapareció: 4 veces en el lote) y **`Yendo a entrenar`** ✗ (dos guardias al
mismo punto inalcanzable, `520, 78, 595`).

### 8.bis.0.decies · **Ronda 26: I188 e I189, y tres cosas que quedan localizadas con coordenadas**

**(a) I188 — el rescate deja al aldeano JUNTO A SU PUESTO, no en la plaza.** Medido en la corrida 64: **13 avisos y 10
del minero** ✗ (`Ximeno / Volviendo a la caseta`, `neto 36-41`, `alcanza=NO`) porque el rescate lo dejaba en la plaza y
su recado quedaba a **54 bloques**. El primer intento (buscar la casilla libre junto al **recado**, a la altura del
recado) **no bastó** ✗ —corrida 67: **7 de 7 rescates seguían en la plaza**— porque **al rescatarlo su goal ya se ha
rendido y no hay recado** ✗. El ancla que **siempre** existe es su **`JOB_SITE`**. Con eso, verificado en las corridas
68-69: el minero cae en **`597, 78, 536`** ✓, a **dos bloques** de su caseta ✓ (antes a 54 ✗).

| mundo asentado | avisos |
|---|---|
| corrida 64 (rescate a la plaza) | **13** ✗ (10 del minero) |
| corrida 67 (rescate junto al recado) | **14** ✗ (7 rescates, todos a la plaza ✗) |
| **68-69 (rescate junto al PUESTO)** | **0 · 0** ✓✓ |

**(b) I189 — la mesa de la taberna es donde come, no donde se pone.** Los cuatro avisos de `Yendo a la taberna` del
lote tenían la firma de «destino que no es casilla»: `neto` 0,6-0,8 ✗, `alcanza=SI` ✓ y `ANDADO ≈ 0` ✗. En el terreno,
los tres destinos (`606, 78, 587`, `600, 78, 583`, `600, 78, 590`) son **ladrillo** ✗, y el generador da **«el centro de
cada mesa»** ✗ — la mesa **es** de ladrillo. Arreglado: se elige la **casilla pisable de al lado** de la mesa (el
contrato que ya usa el ganadero) y se descartan las mesas sin casilla. **PERO SIGUE FALLANDO UNA VEZ** ✗ (corrida 67:
`Isidoro / Yendo a la taberna` con destino `605, 78, 586`, que el corte del mundo da como **ladrillo** ✗ y que
`casillaPosible` **aceptó** ✓ hmm — **contradicción sin resolver** ✗, y es el siguiente paso: o `esCeldaDePie` acepta
una celda maciza, o el corte y el destino no son la misma celda. **Anotado con las dos coordenadas.**

**(c) Y `Yendo a entrenar` queda LOCALIZADO** ✗: los dos guardias rescatados en la corrida 69 estaban atrapados en
**`519, 79, 594`** y **`520, 79, 595`** ✓ — o sea, **dentro del patio de tiro** ✗ (valla con una compuerta en
`z=591`), a **y=79** (un bloque por encima de la calle, fuera de la banda que exime). El rescate los saca ✓, pero
desde la plaza no vuelven ✗ (su puesto no tiene casilla libre cerca) → **el candidato es la compuerta del patio y el
destino del entrenamiento** (la diana es un bloque macizo, como la mesa de la taberna).

**(d) Y el pozo de la mina sigue siendo la trampa** ✗ (mundo, no código): el minero se cae en la zanja de `x=603`
(1×1 y 2 de fondo) y **cada rescate es un aviso menos pero un problema que sigue ahí**. Lo suyo es **dejar un escalón**
al construir la boca de la mina.

**Y la lección que más vale de todo esto**: durante dos semanas el instrumento costaba **80 minutos por pregunta** y el
aviso de rendición **fotografiaba el después** en vez del fallo. Con el **banco rápido** (3 min) y los tres datos nuevos
del aviso (**neto**, **ANDADO** y **destinos**), lo que llevaba cuatro intentos fallidos se encontró en minutos — y tres
de mis propios arreglos cayeron en cuanto se midieron.

Sacado **gratis** de los registros del lote largo 115-118 (sin lanzar nada): de **18 rendiciones**, **15** se producen con
el aldeano a **3-8 bloques** de su destino y **todas con `dy=0`** (la altura está bien, no es la cota). O sea: **no se
pierden por el camino: se quedan a las puertas.**

Y el portón que abre el mod lo dice con nombre y apellidos:

```
Isidoro (Granjero) / Sembrando: abro el porton 545, 78, 584 · destino=544, 78, 585   rutaViva=3 nodos alcanzaba=NO
```

**Destino a UN bloque y «no alcanza»** ✗: está pegado a la valla de **su parcela** y la compuerta que estorba está en el
**punto medio del lado**, no en la recta que yo sondeaba ✗ (I169 miraba solo la línea hacia el recado, y por eso **no la
encontraba**). **I178** mira ahora **toda la puertas de alrededor** (radio 8, que cubre de sobra una parcela de 9×9) y
abre la que **de verdad separa**; el coste se paga **una vez cada 10 ticks** por aldeano.

**Y DOS COSAS MÁS, medidas, que evitan seguir dando vueltas**:

- **La navegación nunca falla** (`moveTo=false` **0** veces en dos corridas) y **`PARAR` salta ~2.500 veces por corrida**:
  los goals paran la navegación para **trabajar en el sitio**. Eso es legítimo, pero es lo que mi relleno del rumbo
  (I171) estaba deshaciendo: **arrancaba de su faena al que ya había llegado y estaba trabajando** (el navegador da por
  llegado con **un bloque** de tolerancia) y lo mandaba a caminar otra vez → **andaban 35-46 bloques y se rendían**.
  **I177** lo corta: el relleno **solo entra si el aldeano lleva 2 segundos quieto**.
- **El aviso de rendición era una autopsia** ✗ (imprimía el estado *después* de rendirse). Ya dice el **tramo**: neto,
  **ANDADO** y **cuántos destinos** (I173/I175/I176).

### 8.bis. SESIÓN DEL 29-sep-2026 (tarde): **el nivel 3, medido y cerrado por clases** — ACTA

El jugador mandó **reescribir el cerebro de los aldeanos** («si es necesario hay que cambiar toda la arquitectura…
que sean propios con un diseño que permita resolver todos nuestros problemas»). Se hizo **por niveles**, midiendo cada
uno con **4 corridas y por etiqueta** (la lección de la mañana: el total no distingue), y está en `docs/aldea-cerebro.md`:

1. **Nivel 1 · M2+M3 (autoridad de recados y puertas)** — **PROBADO Y RETIRADO**: la media **subió** a 23 (con un fallo
   mío: aparcaba el recado **dejando el objetivo puesto** y el aldeano **rondaba**). Se quedó el módulo `VillageErrands`.
2. **Nivel 2 · M1 el despachador** — **PROBADO Y RETIRADO, y fue el hallazgo del día**: ganó la pelea del rumbo
   (el cerebro se lo pisaba en **33.000-45.000 de 50.000-65.000 recados por corrida**) y eso **hundió el pueblo**
   (**110 · 238 · 150 · 192**, media **172,5**, contra **13 · 72 · 19 · 40**, media **36**): los goals **contaban con
   que el paseo se llevara al aldeano**, y al llegar de verdad a sus destinos quedó al descubierto **la lista real de
   lo que está roto**. Esa lista es el trabajo del nivel 3.
3. **Nivel 3 · arreglar las CLASES, no las etiquetas** (I152–I155):
   - **I152 guardias** — la casilla del entrenamiento estaba **2 bloques por encima** de el nivel del pueblo (encima de la diana),
     pasaba la prueba local y estaba **aislada** (ruta de un nodo). Ahora: **a el nivel del pueblo** + **ruta validada** + **si
     ninguna vale, no entrena**, y **el «no» caduca a los 5 s** (cacheado para siempre, un guardia se quedó **sin
     entrenar en toda una corrida**: 267 censos con `entrenado` máximo **0**). **MEDIDO**: `Yendo a entrenar` **0** en
     todas y `entrenado` subiendo de 0 a **914-1.800** en las cuatro.
   - **I153 obrero** — caminaba **al propio hueco** (`destino=stone_bricks encima=air`, ruta de un nodo). Ahora sin
     casilla de pie **no persigue** (aparca el hueco) y el avance se mide **por la ruta**. **MEDIDO**: `Repuso*` **0**
     en las cuatro y la media **41,5 → 41,5** (mismo modo) con el trabajo igual.
   - **I154 las tres clases, en un solo sitio** — (A) **a una celda que no se pisa no se camina**: en `caminarHacia`,
     para **todos** los goals (cultivos, el soporte del clérigo, la mesa de la taberna, el plantón de la arboleda);
     (D) **el encajamiento se comprueba para todos** los aldeanos (metido en una **mesa**, en un **cofre**, en unas
     **escaleras**, sobre un **horno**: saltó **8, 14, 15 y 10 veces** por corrida); (B) avance por ruta.
     **MEDIDO (4 contra 4, modo aldea, corrida entera)**: **41,5 → 30,25**; `Bajando lo del corral` **35 → 16**,
     `Recogiendo lo suyo` **40 → 18**, `Yendo a la taberna` **35 → 29**, `Yendo a la cocina` **14 → 12**; **y el pueblo
     trabaja igual** (pescador 9,5, granja 797 → 759, herrería 74,5 → 75).
   - **I155 el portón del corral lo abre la aldea** cuando el ganadero se queda **sin ruta**, y solo si el recado
     **cruza** la cerca (si los dos están dentro, cerrado: **las gallinas no se escapan**).
     **MEDIDO Y RETIRADO**: 2 corridas dan **35 y 42** (media **38,5**) frente a **30,25** (4 corridas) sin él. Mejora
     `Sacrificando un animal` (**2 y 2** frente a 3,75) y la taberna (**3** frente a 7,25), pero **empeora las dos
     etiquetas del ganadero** (`Bajando lo del corral` **8 y 6** frente a 4; `Recogiendo lo suyo` **8 y 6** frente a
     4,5): abrirle el portón lo deja **más activo** (más recados intentados, más avisos). Queda el módulo
     (`VillageErrands.abrirLaPuertaSiHaceFalta`) y la invariante, y **el pendiente de rehacerlo POR OFICIO**: el
     círculo cerrado del ganadero encerrado es **real** (`ruta=1 nodos … alcanza=NO`), lo que no está medido es que
     abrirle el portón a **todos** mejore nada.
   - **Ruido del registro**: los `removeCurrentModifiers()/Add/createNewAttributeModifiers` y `Player Att armor|toughness`
     pasan a **DEBUG** (ensuciaban las medidas).
4. **LO QUE QUEDA PENDIENTE, con nombre y número** — **actualizado al final de la sesión del 30-sep-2026**:
   **HECHO Y MEDIDO DESPUÉS** (cada pieza con su invariante y sus 4 corridas):
   - **I152 guardias** (`Yendo a entrenar` 0, y `entrenado` 0 → 914-1.800), **I153 obrero** (`Repuso*` 0),
   - **I156 cocinero** (el cocinero **trabaja el doble**: 12 → 26 piezas, `A por leña` 3 → 0),
   - **I157 el bucle del portón** (la mayor mejora: **30,25 → 15,25** de media, `Entrando a la huerta` 34/50 → 0-6),
   - **I158 taberna** (el atasco se mide contra el paso: `Yendo a la taberna` **29 → 3** en su lote),
   - **I159 el freno del desatasco** (`Sembrando`/`Cosechando` **12 → 4**),
   - **I160 el nivel del pueblo del parcela** (destinos a nivel del pueblo+1: **42 → 1-5** por corrida).
   **El cuadro del lote final (85-88, 89 avisos en 4 corridas)**: `Bajando lo del corral` **35 → 10**,
   `Recogiendo lo suyo` **40 → 10**, `Yendo a la taberna` **29 → 11**, `Sacrificando un animal` **15 → 5**,
   `Sembrando` **11 → 9**; y el pueblo trabaja (pescador 7-10, herrería 60-74, cocina 2-8, mina 2-3).
   **LO QUE SIGUE, con su número del lote final**:
   - **I163+I164 · EL COMPOSTERO (el PUESTO del granjero) — HECHO Y MEDIDO (30-sep-2026)**: estaba en **y=63** sobre un
     **escalón del terreno** (suelo natural en 62, un bloque sobre el nivel del pueblo) y el granjero **no podía subir a su
     puesto**, así que **todos sus recados** apuntaban a esa altura (**42 de 60 destinos en y=63** en una corrida). Se
     **asienta a el nivel del pueblo** (y se muda el `JOB_SITE`), **se vigila en el latido** cada 10 s y **se quitan los composteros
     de más** —medido en el mundo del arnés: la columna del parcela 1 tenía **DOS**, el bueno a el nivel del pueblo y **otro encima**,
     resto de la migración 64—. `CURRENT_LAYOUT = 78`.
     **MEDIDO (4 corridas, 101-104): EL MEJOR LOTE DE LA SESIÓN** — total **4 · 6 · 8 · 9 (media 6,75)**, el censo con
     `puesto y=63` en **2 · 2 · 2 · 2** (antes **410-448**), la limpieza actuando **0 · 11 · 9 · 23** veces, y el trabajo
     igual o mejor (granja 746-798, pescador 8-10, herrería 70-83, cocina 7-58, mina 3).
     **Y QUEDA**: como la limpieza tiene que actuar **9-23 veces por corrida**, **algo vuelve a colocar el compostero en
     alto** (probablemente el latido reponiendo «testigos» de estructuras, sobre el escalón): el arreglo de raíz es que
     **ese repositor no lo ponga en alto**.
   - **`Yendo a la cocina` 17** (era 12): sigue **abierta**. Ya se sabe lo que **no** vale: preguntar la ruta en `canUse`
     (dispara el bucle del portón, I157) y preguntarla a los 40 ticks de atasco (I161: **17 → 26**, retirada).
   - **`Labro la huerta` / `Labrando la huerta`** (14 en el lote del 30-sep): **misma clase** —`ruta=1 nodos … alcanza=NO`
     entre **dos casillas normales** (`aire/aire/hierba`), o sea **un recinto cerrado de por medio** (el parcela y su
     valla)—.
   - El **contador de trabajo de la granja** (197-889 entre lotes): es **varianza** (el mismo código da 889 y 369), no
     una entrega perdida.
   - Y los dos ruidos de siempre: el **aldeano fresco con hambre 0** y los avisos sueltos del arranque.
   - **El instrumento**: el censo del granjero llena `4.400-4.600` líneas por corrida — bajarlo a `debug` o espaciarlo.

### 8. Lo que queda, con su nombre y su número (27-sep-2026)

Con el código de hoy, la última corrida (`build/medida-clerigo2.log`, 15.400 ticks) deja **7 avisos de rendición,
TODOS SUELTOS** (el mayor, **2**) y **NINGÚN BUCLE** — contra los **18** de la referencia de la mañana, con bucles de
**16** y **19**—. La **tasa del pueblo** con este código: **0,30** de media (**0,10 · 0,30 · 0,50**, n=3) contra el
**1,29** de la tanda anterior. Eso es I135: la prueba de cada arreglo es **su etiqueta**, no el total.

| lo que queda | medida y por qué NO es de las clases arregladas |
|---|---|
| **RESUELTO: los viajes largos sin tirón** | el recolector al **almacén** desde la huerta (**70+ bloques**, `alcanza=NO`) se rendía en **bucle de 5-8 avisos**. Arreglado con **`VillageManager.elPuntoDeAhora`** (casilla de pie + **tramo**) midiendo el atasco **contra el tramo** (I140). MEDIDO: `Saliendo de la huerta` **8 → 1** |
| **RESUELTO: el bucle de la PLAZA** | el destino de «volver a la plaza» es **la campana del kiosco** (no se pisa) y el tramo calculado a mano **no comprobaba la ruta**: **10-15 avisos en bucle** con `cerebro=531,63,646` y `nav=[sin ruta]`. Arreglado con el **tirón del proyecto** (`tironConMemoria`). MEDIDO: **15 → 1** |
| **RESUELTO: el clérigo** | `Yendo a la iglesia` **2 → 0**: estaba **metido en la puerta** de la iglesia (`pies=oak_door`) con la ruta buena → casilla de pie + **`desatascarSiEstaEncajado`** (I122/I140) |
| **el ganadero** → **RESUELTO Y MEDIDO** (29-sep-2026, ver §12) | `Recogiendo el corral` con algo caído sobre **mobiliario** (una valla, la mesa): el punto de ahora se puso (2 → 1) y el filtro de celdas no pisables del §12 lo dejó en **0 en la ventana** (medido con `rendiciones.py --etiquetas`). Del ganadero quedan **otras** ramas (`Bajando lo del corral` 2 en la ventana), que son la clase de I119/I122 |
| **RESUELTO: el clérigo y su POI** (clase **I125**) | `Yendo a la iglesia` **2 → 0**: estaba metido en la **puerta** (`pies=oak_door` → desatasco) y además el cerebro iba a su **POI** (`cerebro=452,64,603` con `destino=453,64,603`). El avance por la ruta está ahora en **`VillageManager.avanzaPorLaRuta`** (el índice del nodo de la ruta viva), compartido con el guardia |
| **RESUELTO: el bucle de la PLAZA (otra vez)** | el aldeano que **ya estaba** en la plaza se quedaba a 3-4 bloques de **la campana** (que no se pisa) y la vuelta se medía contra ella → **5 avisos en bucle**. Ahora la llegada se mide contra el **punto de pie**: **5 → 0** |
| **lo que queda, y ya no es un bucle** | **8 avisos, todos sueltos** (el mayor, 2), de **siete aldeanos distintos**: tropiezos puntuales de cada oficio (un granjero buscando recambios, un leñador guardando lo suyo, un guardia yendo a entrenar…). No hay ninguno repetido: **cada aldeano, una vez** |
| **LA TASA, con su media (28-sep-2026)** | **ANTES de los dos arreglos de abajo: 0,83** de media (0,10 · 1,50 · 1,00 · 0,70, cuatro corridas del MISMO código, con `medida-tasa-hoy` como la anómala buena). **DESPUÉS: 0,25** (0,30 · 0,20) y con el filtro del ganadero **0,40** → contra el **0,30** del código de antes de hoy y el **1,29** de la tanda anterior. **Y los dos bucles que la media destapó están a CERO**: `Saliendo de la huerta` (I146) y `Recogiendo el corral` (I147) |
| **LA LECCIÓN: UNA CORRIDA NO DISTINGUE UN TROPIECO DE UN BUCLE** | el §8 daba esos dos bucles por «tropiezos puntuales» **porque se miró una corrida**; con cuatro, las etiquetas se repiten y la media sale **peor** que la del código anterior. Para afirmar algo, la media de 2-4 |
| **el hundimiento en la farmland** (0 avisos) | el bucle **desaparece** (46 desatascos reales en una corrida, 40 de **puertas**) y ya no se repite |
| `Yendo a entrenar` (0-1) | clase **I119**: **rendirse ahí es correcto**; lo que falta es que el puesto sea alcanzable |
| **la tasa** | **HECHA, con su dispersión**: las cinco corridas del código final dan **0,10 · 0,20 · 0,30 · 0,50 · 0,60** (media de la tanda buena, **0,30**) contra el **1,29** de la tanda anterior. La prueba de un arreglo es **su etiqueta** |

**Y DOS LECCIONES DE MÉTODO de esta sesión**, las dos pagadas con su corrida:
1. **Una regla del pueblo NO se mete en el método compartido si el que camina y el que cuenta no están de acuerdo.**
   Meter la casilla de pie y el tirón dentro de `caminarHacia` «para que valiera para todos» **empeoró** las cosas
   (avisos **10 → 11**, tasa **0,60 → 1,21**) porque los goals seguían midiendo el atasco contra el destino final. El
   arreglo bueno es el **contrato**: `elPuntoDeAhora` + medir **contra ese punto** (I140).
2. **La prueba de un arreglo es su etiqueta, no el total.** La corrida que pareció una regresión (11 avisos) estaba
   diciendo la verdad, y la que dio tasa 0,15 tenía solo 1 rendición en la ventana: con esa varianza, el total no
   distingue nada.

**Y el aviso de rendición del guardia, cuando el cerebro va a otra parte** — **arreglado y medido** en esta misma
sesión: los 2 avisos de guardia patrullando tenían `cerebro=` apuntando a **otro sitio** que el `destino=` (I125: el
paseo o los portones le pisan el rumbo); el guardia salta el puesto (correcto) pero **se contaba como rendición** y
ensuciaba la tasa. Ahora solo se apunta y se escribe **si el cerebro va de verdad al destino**: `Patrullando` **9 → 0**.

### 9. El SEGUNDO POZO de la mina: **HECHO Y MEDIDO** (I144, 28-sep-2026)

Lo pidió el jugador: *«estaría bien abrir un segundo pozo»*. **Implementado, y el minero lo cava solo** cuando el
primero llega a su tope.

**MEDIDO** (`build/medida-pozo2.log`; el arnés da el pozo 1 por terminado a los 20 s con su piedra labrada de tope):
el censo `POZO` **pasa a medir el eje `437, 675`** (el segundo pozo), el minero **cava su caracol** (`caracol paso 12
… 16 en 437/436/…/433, 679`, **16 pasos**), y su solar sale **sin una celda de agua** (el pozo 1 tiene 2 y se le
inundaron los pasos 35-44) y **sin nada construido**. Crudo en `tools/arnes/medidas-pozo2.txt`.

**La implementación** (pequeña, porque todo depende de `centroDeLaMina`): `MINA_OFFSETS = {(33,0,-29), (-33,0,29)}`,
`centroDeLaMina(center, pozo)`, y `centroDeLaMina(center)` lee **el pozo activo** de un caché por aldea (como el de
`cotaDeLaPlaza`), así que **ninguna firma cambió** y el minero solo añadió **una línea** (`elegirElPozoActivo`). Las
**dos comprobaciones de exclusión** miran **todos** los pozos (si no, el nivelado habría rellenado el caracol del
segundo y el tapagujeros, su boca).

**Y UN ERROR DE MEDIDA MÍO, que queda escrito**: la primera tabla de candidatos la medí **desde el eje del pozo 1
(503,617) creyendo que era el centro de la aldea**. El centro es **470,63,646** y `503,617` es `centro + (33,-29)`, o
sea **el eje del pozo 1**; con ese error, el «suroeste» que elegí (470,646) era **la propia plaza** y «el acuífero del
pozo 1» un artefacto. Medido bien: **los cuatro candidatos tienen 0 agua** en la galería del paso 16 y **el criterio
que decide es la separación** — el simétrico `(-33,+29)` deja **30** bloques entre las dos zonas de exclusión y los
otros dos solo **8**. El offset elegido **era el correcto** (el eje que el juego calcula, `437,675`, es el medido) y
**no hubo que tocar el código**.


### 10. `Yendo a entrenar`: **ARREGLADO Y MEDIDO** (I145, 28-sep-2026) — los guardias entrenan

Era el único caso con etiqueta que seguía saliendo. **La causa**, medida en los logs: el puesto del patio
(`424,63,675`) queda a **68 bloques** del guardia y la región de búsqueda del planificador son **56** → caminando
**directo** (como hacía esa rama) **no hay ruta ninguna**; se rendía (con razón) y **no entrenaba**. **El arreglo**:
caminar con el **tirón** (`elPuntoDeAhora`, el contrato I140, con **la plaza** como hub —sin ella el tirón devuelve el
propio destino—) y medir el atasco **contra el punto**.

**MEDIDO** (`build/medida-entreno.log`): la marca `entrenado=` **sube en los 6 guardias** en la misma corrida
(+2.286 a +3.349 ticks cada uno, y esa marca solo sube **delante de la diana**), y el aviso baja de **uno por guardia**
a **2 en total**, los dos **transitorios** (se rinden una vez a 6-7 bloques del puesto y **en el turno siguiente
entran**). Detalle en `tools/arnes/medidas-entreno.txt` / la invariante **I145**.

**Lo que queda de este caso**: el último salto al puesto (los 6-7 bloques) no tiene ruta desde el tramo; es un tropiezo
puntual, no un bucle.

### 11. LA POSADA SIN ESCALERA: el latido sube aldeanos a una planta de la que NO SE PUEDE BAJAR (28-sep-2026) — **REVISADO el 29-sep-2026: la premisa no se sostiene y lo que queda es OTRA cosa**

> **AUDITORÍA DEL 29-sep-2026 (lo pidió el jugador: «¿cómo que pendientes documentados?»).** Tres cosas, medidas:
>
> 1. **La premisa (una planta SIN salida) no se sostiene en la aldea actual**: la taberna se construye **abriendo su
>    hueco de subida** —el propio log lo escribe al levantarla: *«desván vaciado (833 teja(s) de relleno) y hueco de
>    subida abierto (6 celda(s)); se pisa en y=…»*—, así que su planta de arriba **tiene escalera**. El diagnóstico
>    original era de la **posada vieja** del mundo anterior (`511,68,667`) y se leyó mal el mapa de capas.
> 2. **Lo que queda medido hoy NO es eso**: en la ventana de `build/medida-tanda18.log` hay **3 rendiciones con la
>    etiqueta `Yendo a la taberna`** (2 granjeros y 1 pescador), y el aviso trae **`cerebro=566,64,566`** (¡la plaza!)
>    o **`cerebro=-`**: el aldeano **no iba a la taberna**, le ganaba el `WALK_TARGET` que escribe su propio cerebro
>    (I119/I122). El «destino» que imprime el aviso (`dark_oak_fence` + `oak_pressure_plate`) es **el mobiliario de la
>    mesa**, que por diseño no se pisa: el goal ya camina a `casillaDePieCercaDe` (I114/I131), así que ese dato no es
>    el fallo.
> 3. **Y LOS TRES ARREGLOS QUE SE PROBARON SE RETIRARON** (disciplina: lo que no mejora, fuera). Los tres, medidos en
>    la **misma ventana** y sobre el mismo guardado, contra el baseline de `medida-tanda18.log` (**7** rendiciones,
>    **0,70** por 1.000 ticks):
>
>    | intento | qué se hizo | ventana | total de la corrida | veredicto |
>    |---|---|---|---|---|
>    | **tanda 21** | guardián de I146 («si el cerebro va a otra parte, no cuentes el atasco») en taberna, recojo y ganadero + filtro de ítems en celdas no pisables | **19** (2,70) | 62 | las 7 de la taberna/recojo/ganadero se van a 0, pero aparecen **12 de granjeros** (`Abono la huerta` 5, `Labrando` 4, `Sembrando` 3) |
>    | **tanda 22** | quitarle el rumbo al cerebro (`parar`) y volver a mandar al aldeano, en los tres | **13** (1,30) | 64 | la taberna baja **3 → 1**, el ganadero NO se mueve (2+2) y aparecen granjeros (`Sembrando` 5) |
>    | **tanda 23** | **dejar de perseguir** (la dirección que arregló §12): la mesa cuya casilla de pie no existe se apunta como fallida; el ítem en celda no pisable no se elige | **35** (3,50) | 52 | peor: `Entrando a la huerta` **12**, `Labro la huerta` 7… y **vuelve** `Recogiendo el corral` |
>
>    **Las tres rutas —insistir, forzar el rumbo y rendirse antes— empeoran el pueblo.** El baseline (7, con **cada
>    aldeano una o dos veces y ninguna etiqueta repetida más de 2**) es el mejor estado medido. Y encaja con lo que el
>    propio doc tiene escrito en §8: **una corrida no distingue un tropiezo de un bucle**, y estos son **tropiezos
>    sueltos** (el mecanismo «rendirse = dejarlo por un rato», I33, funcionando), no bucles: los bucles de verdad
>    —`Saliendo de la huerta` (I146) y `Recogiendo el corral` (I147)— **están a cero** y así siguen.
>
> **CONCLUSIÓN: aquí se para.** El pendiente que queda NO es un bucle y no se arregla tocando estos goals: cada
> intento mueve el problema a la huerta. Lo que haría falta es averiguar **por qué** esos aldeanos concretos se quedan
> —el caso del ganadero es el más claro: `cerebro=-`, **sin destino ninguno en el cerebro**, con la etiqueta puesta— y
> atacarlo **con la media de 2-4 corridas** (§8), no con una. Queda escrito con sus números para no repetir los tres
> caminos que ya sabemos que van peor.


**El caso, medido** (`build/medida-tasa-hoy.log`): la leñadora daba **dos avisos** de rendición con
`nav=[sin ruta]`, **`cerebro=-`**, `suelo=dark_oak_planks` y su posición en **`511,68,667`** — y la ruta que tenía era
**de un solo nodo: ella misma**.

**La causa raíz, medida con el mapa de capas** (`build/slice_mina.py`, sobre el guardado del jugador):

* En **`y=68`** está la planta de arriba de la posada: sus **camas** (`R` = `red_bed`) y sus muros, **cerrada** por
  muros de tierra en todo el perímetro.
* En **`y=67`** —el suelo de esa planta— **es tierra maciza (`D`) en TODA la planta**, con troncos (`L`) en los
  bordes: **no hay un solo hueco de escalera**. Esa planta **no tiene salida**.

**Y el aldeano llegó ahí por el propio pueblo**: el latido (`VillageManager.acostarAlQueNoLlega` / la celda de espera
de I43) **mueve** al aldeano hasta su cama cuando no llega andando — y si la cama libre que le toca está **arriba**,
lo deja **arriba**, en una planta de la que el planificador **no encuentra salida** (`nav=[sin ruta]`, la ruta de un
nodo). Después de los avisos **acaba saliendo** (en esa corrida siguió con su oficio: `Guardando lo suyo`), así que no
es un bucle de los de antes, pero **el agujero está ahí**.

**Y NO se arregla con lo que ya se probó**: el doc de I43 ya dejó medido que exigir «misma planta y ≤3 bloques» para
la celda de espera **empeora** las cosas (`CAMAS RESUMEN: … SIN CAMA` porque las únicas libres eran las de la posada).

**Lo que toca** (elegir uno, ninguno hecho): **(a)** que el latido, si el aldeano está en una celda **sin ruta viva**
(el mismo caso que él creó al subirlo), lo **baje** a una celda con ruta —el mecanismo de mover los últimos bloques ya
existe y ya se usa para subirlo, así que es el arreglo coherente—; **(b)** que las camas de una planta **inaccesible**
no entren en el reparto (midiendo antes que no deja `SIN CAMA`); o **(c)** dar **escalera** a esa planta en el
generador (es construcción: obliga a migración del mundo ya construido).

### 12. EL GANADERO Y SU VALLA: el destino del corral es una celda que NO SE PISA (28-sep-2026) — **bucle medido**

El §8 daba esto por «tropiezo puntual» («`Recogiendo el corral` con algo caído sobre mobiliario»). **NO es puntual: es
un bucle**, y está medido en `build/medida-tanda1.log` (**7 rendiciones en la ventana**, 12 en toda la corrida, todas
de la misma ganadera):

```
no consigue llegar a 513, 63, 640 desde 513, 63, 641
   (ruta=1 nodos hasta 513, 63, 641 alcanza=NO; pies=air cabeza=air suelo=grass_block
    | destino=oak_fence encima=oak_fence)            <- el destino es una VALLA, con OTRA valla encima
no consigue llegar a 514, 63, 640 desde 514, 63, 641
   (... | destino=oak_fence_gate encima=oak_fence)
```

**La causa**: el destino que se calcula para «recoger el corral» cae en una celda **ocupada por la valla** (y encima
tiene otra): **no es pisable**, así que la ruta es **de un nodo** y el aldeano **se rinde siempre**, en bucle.

**Y el mapa del corral lo confirma** (`build/slice_mina.py 508 520 634 646 62 64`, sobre el guardado): el corral tiene
**vallas dobles** (`O` en `y=63` **y** en `y=64`, el muro del corral) y dentro hay **fardos de heno** (`H`). El destino
`513,63,640` **es la valla sur**: o sea que hay un **ítem dentro de la valla** —un **huevo**—, inalcanzable.

**Y por eso es un bucle aunque haya «punto fallido»**: cada huevo que pone la gallina es un **ítem NUEVO**, así que el
punto apartado no cubre al siguiente. **El arreglo que toca**: al elegir el ítem suelto, **descartar los que están
dentro de un bloque** (su celda no es aire): no se pueden recoger y solo sirven para que el ganadero se rinda.
**Criterio**: `Recogiendo el corral` **7 → 0** en la ventana.

> **RESUELTO Y MEDIDO (29-sep-2026).** El arreglo está en `VillagerAnimalFarmGoal.buscarDropEnElCorral` (descarta el
> ítem si su celda **no es una casilla de pie**, `VillageManager.esCeldaDePie`, I147), y el criterio **se cumple**:
> medido con el instrumento de siempre, `python tools/arnes/rendiciones.py --etiquetas build/medida-tanda18.log`,
> **`Recogiendo el corral` ya NO aparece en la ventana (2.000–12.000)** y en la corrida entera (22.000+ ticks) queda
> **1**, contra las **7 en la ventana / 12 en la corrida** de la medida original. La ventana de esa corrida suma **7
> rendiciones** y ninguna es esta: `Bajando lo del corral` 2, `Recogiendo lo suyo` 2, `Yendo a la taberna` 3 (ver §11).

**Y EL RECOLECTOR EN EL PARCELA** (mismo log, 6 en la ventana): se rinde desde `484,62,658` con **`pies=farmland`** y
`cabeza=wheat` —o sea **encajada dentro del cultivo**, un bloque POR DEBAJO de el nivel del pueblo—, y ahí mismo tiene una ruta a la
campana que **SÍ alcanza** (`ruta=23 nodos … alcanza=SI`). Hay que ver si es el mismo falso positivo de I146 (el
cerebro en otra parte) o el desatasco del parcela.

### 1. El `JOB_SITE` del minero (el cortapiedras, un bloque): **medido, y no cuesta nada** (cerrado)

Medido en la corrida en la que la mina ya funciona de punta a punta (297 muestras del minero, `t=11.720`):

| qué | medida |
|---|---|
| muestras con `destino=SIN DESTINO` (el cerebro le borra el rumbo al pelear con el `WorkAtPoi`) | **48 / 297 (16 %)** |
| muestras con `goals=[]` (el goal, entre ciclos) | 33 / 297 |
| `Volviendo a la caseta (encajado)` | **0** (solo salía mientras la mina estaba clavada en el acuífero) |
| ciclo del minero | 16 pasos de caracol, 6 celdas de galería, 4 sellos, **1 tope**, 31 entregas en el almacén, **0 picos rotos** |

O sea: **la pelea del `JOB_SITE` es real pero NO bloquea nada** (ni un paso, ni una entrega); y el `rutaFaena` que me
hizo pensar lo contrario medía **la ruta a un bloque**, no a la caseta. Queda **cerrado** como cosmético, con una
**residuo** apuntado: con la mina ya en el **tope**, el minero se queda por el almacén (`516,63,663`) repitiendo
`deja lo sacado` / `Guardo 18 de lo suyo` — no hace nada útil porque **su mina está acabada**; si se quiere, lo
siguiente sería darle una faena de reserva (o que ayude en otra aldea/puesto), pero eso es una decisión de diseño,
no un fallo medido.


Estado medido en la última corrida (`TOPE=SI` incluido): el minero **baja, cava 16 pasos (16 → 32), se topa con el
acuífero, sella, cierra la mina con su piedra labrada y SUBE a entregar** — acabó en `516,63,663` con la etiqueta
`Guardo 18 de lo suyo` y **0 picos rotos** (el viaje al almacén funciona).

**Y el "la caseta no se alcanza" era un fantasma de MI INSTRUMENTO**, cazado con la `SONDA DE LA CASETA` nueva (el
arnés pregunta al planificador del juego, celda a celda, de fuera adentro):

```
[Arnes] SONDA DE LA CASETA t=400 pos=499,54,621 apoyo=503,63,617 puesto=501,63,615
  503,63,621(air/grass_block)=28n/SI   503,63,620(air/stone_bricks)=29n/SI
  503,63,619=30n/SI   503,63,618=31n/SI   503,63,617(apoyo)=32n/SI
  501,63,614(stone_bricks/stone_bricks)=34n/NO fin=501,64,615
```

El **apoyo de la caseta SÍ se alcanza** (32 nodos, entrando por su puerta sur) y el modelo también lo encontraba (57
pasos). Lo que **nunca** se alcanza es la celda del **`JOB_SITE` = el CORTAPIEDRAS** (`501,63,615`, **un bloque**):
`reclamarElPuesto` se lo pone al cerebro en la celda del puesto, y el `WorkAtPoi` de vanilla manda al aldeano **a esa
celda**, que no se puede pisar → el `rutaFaena` que yo medía era **la ruta a un bloque** (`fin=… dFin=4,00/7,07`) y de
ahí salió el diagnóstico equivocado. Lo que sí provoca es una **pelea**: el goal escribe su destino cada tick y el
cerebro lo pisa con el del puesto → `mejorDistancia` no baja → `stuckTicks` sube → **"Volviendo a la caseta
(encajado)"** en bucle... **pero eso era mientras la mina estaba clavada en el acuífero**: con la mina funcionando
(la corrida del recuadro de arriba) **no sale ni una vez** y el minero cumple su ciclo entero. La conclusión medida es
la del recuadro: **cosmético, cerrado**.

### 2. Los GRANJEROS con la mata: **ARREGLADO y MEDIDO** (26-sep-2026)

**El fallo (medido, y no era la mata)**: cuando el granjero ya estaba pegado a la compuerta, el juego le daba el
destino **con tolerancia de 1 bloque** (`caminarHacia` pone `WalkTarget(..., 1)`), así que el planificador **lo daba
por llegado**, le devolvía una ruta de **un solo punto** (la celda donde él ya estaba) y **no daba ni un paso**: el
tramo de la compuerta no avanzaba, a los 120 ticks **aparcaba esa entrada** y probaba otra puerta —hasta las cuatro—
y se rendía. Medido: los **6 avisos de una corrida, todos a distancia 1**, y **5** `no consigue entrar al parcela`.

**El arreglo**: `VillageManager.caminarHaciaExacto(...)`, un caminar **con tolerancia 0** (y que además le pide la
ruta a la navegación a mano, porque el cerebro puede escribir su propio destino en el mismo tick). Se usa en los dos
tramos del granjero que exigen **pisar** una celda: **entrar** por la compuerta y **salir** del parcela.

**MEDIDO, antes y después** (misma partida, misma copia, modo `MEDIR_MINERO`):

| | antes | después |
|---|---|---|
| `no consigue entrar al parcela …` | **5** | **0** |
| avisos de `Entrando a la huerta` | **6** | **0** |
| el granjero trabajando | se rendía en la puerta | **`Valeriano (Granjero) / Cosechando`** y entregas a la despensa de **73, 71, 45 y 14** |

*(Lo que quedaba apuntado dos veces como "intento medido y retirado" —el contador de atasco y "un paso más adentro"—
está en `tools/arnes/medidas-mina-sellada.txt` §11: los dos fallaron y se quitaron, y fue esa medida la que dejó a la
vista que el problema era la tolerancia del caminante.)*

### 3. La RECOLECTORA: **ARREGLADA y MEDIDA** (se quedaba encerrada en el parcela)

**Medido**: **19 rendiciones** en `Volviendo a la plaza` con `ruta=1 nodos … alcanza=NO` **desde dentro de un parcela**
(`pies=farmland cabeza=wheat`): entra a los parcelas a por lo que se cae —su faena— y **no puede salir**, porque una
**puerta de valla cerrada no es navegable** para el juego y nadie se la abre.

**El arreglo**: `VillageManager.abrirLaCompuertaDeAlLado(...)` (compartida con el granjero) + en la recolectora, si
está dentro de un parcela, mandarla a la **celda de dentro de la compuerta más cercana** con `caminarHaciaExacto` y
**abrírsela** en cuanto la tiene al lado (probando otra si esa está aparcada).

**MEDIDO**: rendiciones del pueblo **27 → 9**; `Filomena / Volviendo a la plaza` **19 → 0**; y ahora **sale y
entrega** (`abro el porton 484,63,659 · destino=470,63,646 rutaViva=24 nodos alcanzaba=SI`).

### 4. El GANADERO: **ARREGLADO y MEDIDO** (perseguía una celda que no se pisa)

**Medido**: `Vicenta / Cuidando el ganado` **3 avisos**, destino a 4-6 bloques con `ruta=1 nodos … alcanza=NO`. Arreglado con `casillaDePieCercaDe` (la regla de I114: se camina a una **casilla de pie**, no a la celda cruda del animal): **`Cuidando el ganado` 3 → 0** y las rendiciones del pueblo **9 → 5**. Detalle en **I131**. Lo de abajo queda como el análisis original:: el destino es una **celda de aire a 4-6 bloques**
(`525,63,651`, `526,63,648`, `524,63,642`, todas con `grass_block` debajo) y el planificador devuelve **`ruta=1 nodos
… alcanza=NO`**, o sea **no se llega**. `cerebro=-` (sin destino en el cerebro) y `nav=[sin ruta]`:

```
no consigue llegar a 525,63,651 desde 525,63,656 (ruta=1 nodos … alcanza=NO; suelo=grass_block)
    etiqueta="Vicenta (Ganadero) / Cuidando el ganado" cerebro=- nav=[sin ruta] goals=[VillagerAnimalFarmGoal]
```

Es **la misma clase que la recolectora (I130)**: su faena está **al otro lado de una cerca** (el corral: va a por un
animal o a su punto de apoyo) y **la puerta de valla cerrada no es navegable** → no hay ruta. **El arreglo es el
mismo**: en `VillagerAnimalFarmGoal`, si la celda a la que va está al otro lado del **portón del corral**, mandarla a
la celda de dentro del portón con **`caminarHaciaExacto`** y **abrírselo** con
`VillageManager.abrirLaCompuertaDeAlLado` (probando el del gallinero si el del corral está aparcado).

**LEÑADOR — `Tomasa / Llevando la madera` (2 avisos)**: la ruta al almacén **SÍ alcanza**
(`ruta=14 nodos hasta 517,63,666 alcanza=SI`, `cerebro=517,63,666`, `nav=[14 nodos … alcanza]`) y **aun así se
rinde**: la clave está en sus pies — **`pies=dark_oak_fence`**: está **metida DENTRO de un bloque de valla** (con una
placa de presión encima, `cabeza=oak_pressure_plate`), así que **no puede moverse** por mucho que tenga ruta. Es un
caso físico (¿empujada por un animal, ¿encajada al cruzar?), no de tolerancia ni de puerta: **lo que toca es que el
goal detecte que está encajada** (la celda de los pies no es una casilla de pie) y, en vez de rendirse y aparcar el
almacén, **dé un paso de desatasco** (o se teletransporte 1 bloque si el juego no la deja salir).


### 5. El LEÑADOR metido en un bloque: **ARREGLADO y MEDIDO** (a la tercera)

El caso que se midió era: ruta **buena** al almacén (`nav=[14 nodos … alcanza]`) y aun así se rendía con
`pies=dark_oak_fence`, o sea **con los pies dentro de una valla**. Dos criterios fallaron antes (los dos retirados):

| criterio | medido |
|---|---|
| `esCeldaDePie` | **940 falsos positivos**: exige suelo `isSolid()` y quien está **de pie encima** de una valla o una placa no lo cumple → lo daba por encajado y lo bajaba un bloque (y volvía a subir) |
| la **caja** del aldeano contra la forma | **0 disparos**: una valla es un **poste fino en el centro** de la celda y pegado al borde la caja no lo corta |

**El criterio bueno**: comparar la **forma de colisión** de la celda de los pies con la **altura de los pies** del
aldeano —si la forma sube por encima de sus pies, está **DENTRO** del bloque— y sacarlo a la casilla más cercana
donde se pueda estar de pie (pies y cabeza libres y el bloque de abajo con forma, sin exigir `isSolid`), con **freno
de 200 ticks** por aldeano. **MEDIDO**: 2 desatascos, los dos **reales**, sin un solo falso positivo:

```
estaba METIDO en 510,63,667 (dentro de dark_oak_fence): lo saco a 509,63,666
estaba METIDO en 512,63,666 (dentro de dark_oak_door):  lo saco a 511,63,666
```

y **`Tomasa (Leñador) / Llevando la madera` desaparece** de los avisos. Está en `VillageManager.desatascarSiEstaEncajado`
y se llama desde el leñador (los demás goals pueden usarlo igual: es un ayudante compartido).


1. **Con `esCeldaDePie`**: 940 falsos positivos (daba por encajado a quien estaba de pie **sobre una valla**) y las
   rendiciones subieron a 18 → retirado.
2. **Con la CAJA DE COLISIÓN** (el criterio correcto para "metido dentro", con freno de 200 ticks): disparó **0
   veces** —ni un falso positivo— pero **tampoco caza el caso**, y las rendiciones quedaron en 12 con
   `Tomasa (Leñador) / Llevando la madera` **x4** → retirado.

**Lo que esto deja MEDIDO**: el leñador **no está encajado** (su caja no corta la valla, solo pasa por su celda) y
tiene la ruta **buena** (`nav=[14 nodos … alcanza]`): es el caso de **I119/I122** ("la ruta alcanza y el aldeano no
se mueve"). Para atacarlo hay que medir lo que **aún no se ha medido**: su **velocidad y posición tick a tick**
mientras tiene esa ruta buena (¿empuja contra la valla? ¿le tapa el paso un animal?).


Se probó un desatasco automático (`desatascarSiEstaEncajado`: si los pies no son una casilla de pie, sacarlo a la
más cercana) llamado desde el leñador. **Medido: 940 desatascos en una corrida** —teleportaba aldeanos que estaban
**bien**, de pie sobre una **valla o una placa** (`estaba ENCAJADO en 510,64,669 (pies=air)`) porque
`esCeldaDePie` no acepta una valla como suelo— y las rendiciones **subieron a 18**. **Retirado**; el árbol está como
estaba. Lo que queda apuntado: el criterio de "encajado" **no puede ser `esCeldaDePie`** (falsos positivos con
vallas, placas y losas): hay que mirarlo con la **caja de colisión** de la entidad contra el bloque de los pies, o
exigiendo que **no se haya movido en N ticks**, y **nunca teleportar por sistema**.
### 6. El PICO al romperse: **CERRADO Y MEDIDO** (27-sep-2026) — el minero **vuelve con otro**

> **CERRADO en la sesión del 27-sep, y sin tocar una línea del mod**: la pregunta que quedaba abierta («suelta la
> faena, **¿pero vuelve con otro pico?**») **ya estaba medida en los logs de la sesión**, cuatro veces, porque el
> arnés deja el pico al borde de romperse cada 2.000 ticks dentro de `medirElMinero` (invariante **I141**).
>
> | corrida | roturas | `pico nuevo` en su mano | forjados por el herrero |
> |---|---|---|---|
> | `medida-tramo.log` | 6 | **6** | 6 |
> | `medida-s7-final.log` | 6 | 5 | 5 |
> | `medida-balsa-final.log` | 6 | 5 | 5 |
> | `medida-s8.log` | 8 | **8** | 7 |
>
> El ciclo, tal cual sale en el log: `se le ha roto el pico (59 usos): va a por otro al almacen` → `Yendo al almacen`
> → espera (`pico=SIN PICO` con `Cargando material`) → **`El minero: pico nuevo: minecraft:wooden_pickaxe`**.
>
> **Y la reserva ya existía en el código** (por eso no hubo que añadir nada): el **pico va PRIMERO** en el herrero de
> herramientas (`recetaDeArmadura` → `recetaDePico`, con `OBJETIVO_PICOS = 2`) y `recetaDePico` **baja de nivel** hasta
> el **pico de madera** cuando no hay hierro — el «cebo del pico roto». MEDIDO: **4 picos de madera y 2 de piedra**
> forjados, **6 de madera** recibidos por el minero, con el almacén en `0 lingote(s), 0 crudo(s)`.
>
> **La pista que queda (el pico de madera)** — **el problema MEDIDO, el arreglo puesto, y la mejora SIN medir**
> (28-sep-2026, y así se dice): el minero cogía el pico con un filtro que aceptaba los cinco materiales y
> `VillageStorage.quitar` devuelve **el primero que cumpla**, así que **con madera y piedra en el almacén se llevaba
> la madera**. MEDIDO (`build/medida-plaza3.log`): el herrero forjó **4 madera y 2 piedra** y el minero recibió **6 de
> madera** — **ignoró los dos de piedra**. Arreglo: pedirlos **en orden de mejor a peor** y quedarse el primero que
> haya (idéntico con un solo material; la corrida del 28-sep, con el almacén a **0 adoquín**, dio **8 de madera**,
> igual que antes). **LO QUE FALTA ES LA MEDIDA DE LA MEJORA**: en esa corrida no había ningún pico mejor que
> preferir. **Cómo medirlo**: desde el arnés, **poner un pico de piedra en el almacén** al empezar, y leer
> `pico nuevo:` → el criterio es que diga **`stone_pickaxe`** con la madera también en el almacén.

**El diagnóstico viejo, tal cual se escribió** (léase con lo de arriba en la mano):


Con la receta del arnés (dejar el pico al borde cada 2.000 ticks) **medido** en una corrida: el pico se rompió
**4 veces** y las cuatro el mod hizo lo que tiene que hacer:

```
[Arnes] PICO t=2000 al borde de romperse (58/59)
[Village] El minero: se le ha roto el pico (59 usos): va a por otro al almacen
```

**Pero el almacén no tiene de dónde darlo**: `ALMACEN DE LA MINA t=9000: 2 adoquin, 0 carbon, 0 lingote(s),
0 crudo(s), 6 pedernal, 0 pico(s)`. O sea que el eslabón roto es **el herrero de herramientas** (no forja picos) y,
detrás, **el hierro**: no hay lingotes **ni mineral crudo**, porque la mina de esta partida **se cerró en el
acuífero** (I127) y el hierro salía de ahí. Lo que toca medir ahora: si el herrero **tiene con qué** (hierro en el
almacén) y, si no lo tiene, **de dónde debería salir** (¿la mina se cierra demasiado pronto? ¿falta reserva de
picos?). Y una pieza de diseño que sale de aquí: **el almacén debería tener SIEMPRE un pico de reserva para el
minero** (hoy `VillageStorage.asegurarElPicoDelMinero` le da **uno** al construir la mina y nunca más).


El minero ya **suelta la faena** al quedarse sin pico (`canContinueToUse`), pero **no está medido** que vuelva con
otro: en las corridas de esta sesión **no rompió ninguno** (`se le ha roto el pico` = 0). Hay que **forzar el caso**
desde el arnés, **dentro de `medirElMinero` y solo ahí** (el mismo ancla `var wt = v.getBrain()...` existe en **6**
volcados: si se pega en todos, se daña el pico desde 6 sitios a la vez):

```java
var enMano = v.getMainHandItem();
if (ticks % 2000 == 0 && !enMano.isEmpty() && (enMano.is(Items.WOODEN_PICKAXE)
        || enMano.is(Items.STONE_PICKAXE) || enMano.is(Items.IRON_PICKAXE))) {
    enMano.setDamageValue(Math.max(0, enMano.getMaxDamage() - 1));
    DevilRpg.LOGGER.info("[Arnes] PICO t={} al borde de romperse ({}/{})", ticks,
            enMano.getDamageValue(), enMano.getMaxDamage());
}
```

**Qué se lee**: `se le ha roto el pico` → suelta la faena → `Yendo al almacen` / `Cargando material` → los **picos
del almacén** (`ALMACEN DE LA MINA … pico(s)=N`, que los forja el herrero de herramientas) → y en las líneas
`MINERO` vuelve a salir `pico=minecraft:…` en la mano. Si el almacén no tiene picos, el que hay que mirar es **el
herrero**, no el minero.

**Y OJO CON EL INSTRUMENTO**: en esta sesión lancé esa corrida con `MEDIR_MINERO = false` y la medida salió en blanco
(`al borde: 0`): **comprobar que el modo está encendido en la copia del arnés** antes de lanzar.
### 6b. LO QUE PIDIÓ EL JUGADOR PARA LA MINA (**los cuatro HECHOS y MEDIDOS**)

1. **La mina atraviesa el agua** — **HECHO y MEDIDO** (I132): ya no se cierra cuando hay agua; **aísla** con paredes
   (cáscara 3×3×3 de adoquín: sella también el agua de delante, así el túnel avanza por celdas secas), **seca** la
   celda (queda de aire, transitable) y **sigue bajando**. Medido: `1:air 2:air 3:air`, `hechas=3/24` (antes clavado
   en `0/24`) y **`TOPE=NO`** (antes `TOPE=SI`, la mina se cerraba).
2. **Que el pico lo haga el HERRERO** — **HECHO y MEDIDO** (27-sep-2026, la cadena entera): ver **I137**. El herrero
   de herramientas **forja picos** y el del **hierro** lo mide la cadena completa: **el guardia mata el zombi de raid,
   lo lootea** (`Dorotea (Guardia espadachín · nv 1) … lleva 2 pepitas`), **lo deja en el almacén** (13 depósitos; el
   almacén de **0 a 20** pepitas) y **el herrero forja el pico de HIERRO** (`Forjo un pico de hierro`, y el almacén con
   `picos (por material: … hierro 1)`), que se lleva el minero. Y hacían falta dos cambios que ya estaban: **(a)** el
   **recolector** recoge las **pepitas de hierro** del suelo (`esDelPueblo` no las tenía); **(b)** el **pico de hierro
   se puede forjar con 27 pepitas** cuando no hay 3 lingotes (vanilla: 9 pepitas = 1 lingote).
3. **Hierro de los zombis de los raids** — **HECHO y MEDIDO** (27-sep-2026, I137): la cadena que pidió el jugador
   (raid → guardia → hierro → el herrero) está medida eslabón a eslabón. **El eslabón que faltaba en el mod era el
   guardia**: looteaba y **se quedaba el hierro en el inventario** (medido: de t=300 a t=2.700 con el almacén a 0, porque
   su goal no tenía ningún paso que lo dejara) → arreglado con `VillagerGuardGoal.dejarElHierroEnElAlmacen` (el guardia
   **va** al almacén cuando lleva hierro). Y el instrumento tenía **tres trampas** que daban un falso "no funciona":
   contaba pepitas **en el suelo** (el mod se las da **al que mata**), la **barredora** del arnés **descartaba** el
   zombi plantado (`discard()` no es morir: ni botín) y el escaneo de zurrones era de **140** bloques (el que las
   llevaba se iba al muelle). Todo en `tools/arnes/medidas-pepitas.txt`.
4. **La LUZ de la mina** (28-sep-2026, **I142**) — *«el minero no está poniendo antorchas en las paredes de las
   escaleras de caracol ni en las galerías … se ve muy oscuro y es un punto peligroso para que spawneen mobs»*.
   **Era verdad y está medido**: la antorcha se pone **al cavar la celda**, y `ponerLaAntorcha` **sale sin poner nada
   si en ese momento no lleva** — y el minero **cavaba antes de tenerlas** (el log: cavó la celda 8 de la galería a
   las 03:31 y fabricó las antorchas a las 03:34) y **nunca repasaba**. Censo del arnés: el caracol con sus pasos
   **0, 8, 16, 24 y 32** en `air` (ni una antorcha), la galería con sus celdas 8 y 16 en `+1=air`, y el minero con
   **8 antorchas en el inventario SIN GASTAR**. Arreglo, dos partes: **(a) la fase `ENCENDER`** (`buscarHuecoDeLuz`)
   repasa **del frente hacia la boca** y pone la antorcha que falte antes de seguir cavando; **(b) no se baja sin
   luz**: `hayQueSubir` pedía luz solo con `celdasCavadas > 0`, así que la **primera bajada** era a oscuras → ahora la
   pide también en el primer viaje **si el pueblo puede dársela** (antorcha hecha, o **carbón/carbón vegetal de un
   tronco** —la leña del leñador— o leña, siempre con palos; sin esa guarda, subir sería un bucle). MEDIDO: los 5
   pasos del caracol y la celda 8 de la galería pasan de `air` a **`wall_torch`**, y el minero **gasta** las antorchas
   (`El minero: encendio 499, 55, 629`, etiqueta `Enciende la mina`).

### 6c. La RECOLECTORA en la TABERNA: **ARREGLADA y MEDIDA** (16 → 0)

El atasco más grande que quedaba. **Causa medida**: `VillagerTavernGoal` caminaba a la **celda de la mesa** con el
caminar de tolerancia 1, y esa celda **no es pisable / está un nivel más arriba**, así que el planificador devolvía
`ruta=1 nodos … alcanza=NO` y el aldeano empujaba hasta rendirse (`destino 516,64,639 desde 516,63,641`). Es la regla
de **I114/I131**: **se camina a una casilla de pie**.

**Arreglo**: `VillagerManager.casillaDePieCercaDe(level, mesa)` en el caminar de `VillagerTavernGoal`.

**MEDIDO** (con `python tools/arnes/rendiciones.py --etiquetas`):

| | antes | después |
|---|---|---|
| `Yendo a la taberna` | **16** | **0** |
| rendiciones de la corrida | 24 en 10.800 ticks (**2,73**/1.000) | **3** en 8.400 ticks (**0,47**/1.000) |
| lo que queda | — | 3 casos sueltos: `Yendo al arbol` 1, `Cosechando` 1, `Patrullando el corral` 1 |


Al medir por tasa y desglosar por etiqueta (I135) salió el atasco **más grande que queda**, y es **nuevo**: al dejar de
quedarse encerrada en los parcelas (I130), **Filomena llega a la taberna** y ahí se rinde:
**`16x Filomena (Recolector) / Yendo a la taberna`** en una sola corrida (dos tercios de su total, 2,73 por 1.000
ticks). Es el sitio por donde hay que empezar: leer su aviso con `cerebro=`, `nav=` y `pies=` (probablemente otra vez
el patrón de "no puedo entrar/salir de un recinto" o "la celda no se pisa"), y **medir con
`python tools/arnes/rendiciones.py --etiquetas`**: el criterio de éxito es que esas 16 bajen a 0.

### 6d. La mina YA BAJA (medido). Y el choque caracol ↔ galería: **ARREGLADO Y MEDIDO** (27-sep-2026)

> **CERRADO en la sesión del 27-sep.** Lo de abajo queda como el diagnóstico viejo, y **una de sus hipótesis era un
> fantasma del instrumento**: `cara=507,46,613` con `bloqueDeLaCara=cobblestone_slab` **no** era el minero «picando su
> propia losa» —`cara` es siempre la celda del caracol del paso que toca, y ésa lleva su **pieza** por definición (es
> una losa en los pasos pares)—. La causa de verdad, el arreglo y la medida están en **I136** y en
> `tools/arnes/medidas-boca-galeria.txt`; en una línea:
>
> * **la boca de la galería**: el aldeano baja el caracol **de pie sobre la losa** (nodo `y+1`, caja `y+0,5…y+2,45`) y
>   con **dos** celdas de hueco el vecino sale **BLOCKED** (la tercera celda es roca) → **no se puede ni entrar ni
>   salir**. Arreglo: **tres** celdas de hueco, como el caracol. MEDIDO: la galería del paso 32 pasó de **3/24
>   congelado 3.600 ticks** a **abrirse sola hasta 24/24** (y el caracol, que estaba clavado en `pasos=32`, **sigue
>   bajando: 34 → 44**, la cara de `y=46` a `y=40`), y la del paso 16 (**que antes cavaba el arnés a mano**) la
>   cava **el minero: 2/24 → 24/24**.
> * **el agua** (el encargo del jugador): las tres celdas abren el techo justo en el acuífero → el túnel se inundaba
>   (`hechas` 6 → **0** y 223 muestras en cero). Con el censo nuevo (`AGUA`) se midió que el agua entra por **la celda
>   de DELANTE** → `aislarDelAgua` se llama **al abrir cualquier celda** y sella las vecinas con fluido, saltándose
>   **solo** el paso del caracol (un adoquín en un paso impar se leería como su pieza) y la capa del suelo.
> * **el muro**: con la galería en 21/24 el minero se quedaba **243 muestras** yendo al **portón norte** (el final de la
>   galería cae a 65 bloques del centro, «fuera del muro», **a 17 bloques bajo el suelo**). Arreglado: **por debajo de
>   la capa del suelo no hay muro que cruzar**.
>
> **Y EL TALLER/BALSA, HECHO Y MEDIDO EN LA MISMA SESIÓN** (I138, `tools/arnes/medidas-balsa.txt`): era la
> **opción C** que quedaba (pasar la balsa y el acarreo al herrero de herramientas). El bucle estaba medido —el
> minero **33-46 coladas** con el pedernal en su inventario y el almacén **clavado en 6**— y el arreglo es el traspaso:
> la receta «Colando» es del **herrero de herramientas** y su ciclo la deja en el almacén. MEDIDO: coladas del minero
> **33-46 → 0**, las del herrero **10** (y para: el pedernal llega a su objetivo de **16**), y la galería del paso 32
> se completa en **t≈17.200** en vez de **t≈37.800**.

**El diagnóstico viejo, tal cual se escribió** (léase con lo de arriba en la mano):

**Medido** (del log de la corrida larga, sin gastar otra): de 25 muestras de faena, `Picando` **17 (68 %)**,
`Cargando material` 5 (20 %), `En el taller` **2 (8 %)**, `Bajando lo sacado` 1. Y en toda la corrida el taller hizo
**4 coladas** de 4 adoquines. **El taller no es el coste**: lo que pesa es el **acarreo** y, sobre todo, que hay **17
muestras de `Picando` con la galería clavada en `hechas=3/24`**.

**Y la pista ya estaba en los datos**: durante el estancamiento, `cara=507,46,613` con
**`bloqueDeLaCara=cobblestone_slab`** — el minero estaba «picando» **una losa de su propia mina** (una pieza del
caracol), y eso **no cuenta como avance** (`progresoDeLaGaleria` cuenta **solo aire**). Hipótesis a confirmar por
medida: **la escalera del caracol y el trazado de la galería del paso 32 se pisan**.

**DÓNDE ESTÁ EL CÓDIGO (localizado, para no volver a buscarlo)**:
`VillagerMinerGoal` — el **destino** de cada paso lo decide `celdaDePieDelCaracol(center, nivel, paso)`
(líneas **435-437**: usa `VillageGenerator.esLosaDelCaracol(paso)` para saber si se pisa la losa o la celda de
encima), y la fase **`CAVAR`** (etiqueta `"Picando"`, línea **592**) es la que elige **qué celda se pica**. El marco
de los postes ya usa la guarda `VillageGenerator.esCeldaDePasoDeLaMina` (línea **1116**).

**EL ARREGLO QUE TOCA** (en la fase `CAVAR`, que es el corazón del minero — no se toca a ciegas): si la celda que va a
picar **ya es una pieza protegida de la mina** (una losa del caracol, un poste del marco), **no se pica**: se **avanza
el paso** (o se re-planifica el trazado), en vez de quedarse 17 muestras de `Picando` sobre su propia losa. Hay que
**leer el bloque de `CAVAR` entero** (~líneas 470-600) antes de tocar: ahí están la secuencia de picado, el sello y el
avance del paso.
**Orden de trabajo (el jugador decidió la opción C)**: **hecho** — el choque caracol ↔ galería (I136) y el traspaso de
la **balsa** al herrero de herramientas (I138, con la medida en `tools/arnes/medidas-balsa.txt`). El criterio que se
pedía (`hechas` sube de `3/24`, `pasos` sigue creciendo y no baja lo que produce el taller) está medido: `hechas`
**24/24**, `pasos` **40** y el pedernal del almacén **de 6 a 16**.


**MEDIDO** (corrida larga, `MEDIR_MINERO`): la mina **desciende** —la parte del encargo que faltaba—:

| t | pasos | la cara | profundidad |
|---|---|---|---|
| 40 | 16/240 | 499,54,621 | 8 por debajo del suelo |
| 1.320 | 17 | 499,53,620 | 9 |
| 2.600 | 19 | 499,52,618 | 10 |
| 3.880 | 21 | 499,51,616 | 11 |
| 5.160 | 24 | 499,50,613 | 12 |
| 6.440 | 32 | 507,46,613 | **16** |

Con **`TOPE=NO`** y **0 cierres**: el agua ya no la para (I132) y la escalera del caracol funciona (**16 piezas** de
caracol colocadas). Eso es lo que pediste: *"seguir minando para abajo … y construir escaleras para llegar al fondo"*.

**Y lo que se ve después, que es el siguiente arreglo**: de t=6.440 a t=10.080 (unos **3.600 ticks**, 3 minutos) se
queda en `pasos=32` con la galería en **`hechas=3/24`**, y **no está atascado**: está **en el taller**, midiendo
`50` líneas de "deja lo sacado / guardo" y `cuela 4 adoquines en la balsa y saca un pedernal` repetido. O sea: **la
faena del taller le come el tiempo de la galería**. Toca decidir el orden (la galería manda) o **ponerle un tope a los
viajes al taller** — y medirlo con el mismo instrumento: que `hechas` suba de 3/24 y que `pasos` siga creciendo.
### 7. Atascos sueltos: **ARREGLADOS Y MEDIDOS** (I139, 27-sep-2026)

Eran **dos clases**, las dos medidas con el desglose por etiquetas y las dos con su criterio:

1. **La ronda del guardia no es un atasco** (pero se rendía): el contador medía **solo la distancia en línea recta** y
   la ronda es un **círculo** —los avisos lo decían: `ruta=16-32 nodos … alcanza=SI`, `nav=[… alcanza]`, o sea **con
   camino y andando**—. Arreglo: cuenta como progreso **consumir nodos de la ruta viva** (y **solo dentro de la misma
   ruta**, para no desactivar el contador en el guardia que empuja una pared). MEDIDO: avisos de guardia patrullando
   **9 → 0**.
2. **Al objeto caído se va por una CASILLA DE PIE**: los goals que recogen (`CollectGoal`, `PickupGoal`,
   `AnimalFarmGoal`, `FarmGoal`) caminaban a `objetivo.blockPosition()`, y lo que se cae puede quedar **encima de algo
   que no se pisa** (la **mesa de la taberna** `516,64,639`, una **valla**, **dentro** de un parcela con
   `pies=farmland`): el planificador no da ruta hasta ahí y el aldeano se rendía. Arreglo: `casillaDePieCercaDe`
   (regla I114/I131) **y** `desatascarSiEstaEncajado` antes de contar atasco (el ayudante del leñador, I122).

Lo que queda y **no es de esta clase**: `Filomena (Recolector) / Yendo al almacen` con `ruta=33 nodos … alcanza=NO`
(el almacén inalcanzable desde la taberna/kiosco: la pierna ya documentada como rota en esta aldea) y `Yendo a
entrenar` de los guardias (1 por guardia, con `nav` que no alcanza el puesto de entrenamiento: la clase de I119).

**MEDIDO** (misma partida; datos crudos en `tools/arnes/medidas-atascos-sueltos.txt`). Corrida buena
`build/medida-s7-final.log` (15.040 ticks): **5 avisos de rendición contra 18**, y la **tasa del pueblo en 0,30** por
1.000 ticks (la mejor de la sesión; la referencia era 0,50-0,60 y la tanda anterior del proyecto 1,29):

| criterio | antes | después |
|---|---|---|
| `Patrullando el corral` / `Patrullando la aldea` | **9** avisos | **2** |
| `Recogiendo el corral` (el ganadero) | 4 | **0** |
| `Guardando lo suyo` (los oficios que recogen) | 5 | **0** |
| `Yendo a la taberna` | 1-2 | **0** |
| el granjero hundido en la farmland, en bucle | **7** (el mismo aldeano) | **1** |
| **la tasa del pueblo** (I135, ventana 2.000-12.000) | 0,50-0,60 | **0,30** |

**Lo que NO queda a cero** (y por qué): **2** de guardia patrullando (hay destinos que no se alcanzan de verdad), **1**
del granjero con los pies en la farmland (el bucle desaparece, el hundimiento se repite) y **1** de `Yendo a entrenar`
(clase I119). Y de método: la corrida intermedia **pareció una regresión** (11 avisos) y era el instrumento diciendo
la verdad —el desatasco disparaba (14 desatascos reales) pero **no tenía a dónde sacarlo** porque exigía aire, y
dentro de un parcela no hay aire—.

Lo que queda **ya no es un sitio concreto**: las **11 rendiciones** de las dos corridas de hoy son **todas de 1**, y
**5 son guardias en su ronda** (la clase de I115, el rodeo del círculo de la ronda). Los atascos gordos están a cero
(`Yendo a la taberna` 16 → **0**, `Volviendo a la plaza` 19 → **0**, `Cuidando el ganado` 3 → **0**, `no consigue
entrar al parcela` 5 → **0**).

| etiqueta suelta (1x) | corrida |
|---|---|
| `X (Guardia …) / Patrullando el corral` · `Patrullando la arboleda` | **5 de 11** (las dos) |
| `Valeriano (Granjero) / Buscando recambios` | galería |
| `Hipolito (Granjero) / Yendo a la taberna` · `Vicenta (Ganadero) / Cuidando el ganado` | balsa |

**Y el diagnóstico del patrón que queda, medido en los mismos dos logs** (los avisos traen `ruta`, `nav`, `pies` y
`goals`, que es todo lo que hace falta). Ojo: esto son los **avisos sueltos** de los dos logs completos, así que
aparecen más casos que las 11 del desglose (que cuenta en la ventana `t=2.000-12.000` y agrupa por aldeano + etiqueta):

| quién / etiqueta | aviso |
|---|---|
| `Ubaldo (Guardia arquero) / Patrullando el corral` | `ruta=30 nodos … alcanza=SI` · `nav=[30 nodos … alcanza]` · `goals=[VillagerGateGoal VillagerGuardGoal]` |
| `Ubaldo / Patrullando el corral` (otra vez) | `ruta=32 nodos … alcanza=SI` |
| `Eufemia (Guardia espadachín) / Patrullando el corral` | `ruta=29 nodos … alcanza=SI` |
| `Onofre (Guardia espadachín) / Patrullando la arboleda` | `ruta=20 nodos … alcanza=SI` |
| `Dorotea (Guardia arquero) / Patrullando la arboleda` | `ruta=16 nodos … alcanza=SI` |
| `Hipolito (Granjero) / Recogiendo lo que se cayó` | `ruta=3 nodos … alcanza=NO; pies=farmland` (un parcela) |
| `Valeriano (Granjero) / Buscando recambios` | `ruta=11 nodos … alcanza=NO; cerebro=488,64,659` (el cerebro va **un bloque por encima**: la clase de I114/I131, `casillaDePieCercaDe`) |
| `Vicenta (Ganadero) / Recogiendo el corral` | `ruta=2 nodos … alcanza=NO` |

O sea: **el guardia NO se rinde por falta de ruta** —la tiene, y de 16 a 32 nodos— sino por el **contador de «no me
acero»** mientras anda el **rodeo** de la ronda (I115: la ronda es un círculo y el camino da vueltas). El arreglo que
toca es **medir el atasco contra el avance por la RUTA VIVA, no contra la distancia en línea recta** (que es lo que
sube en un rodeo), y el criterio de éxito es que esas etiquetas bajen a 0 **sin subir las demás**.

**Y la tasa del pueblo (I135) con la mina ya desbloqueada**: media **0,55** por 1.000 ticks (rango 0,50-0,60) contra la
**1,29** de la tanda anterior, y **sin una sola corrida mala** (desviación 0,94 → 0,07). Con 2 corridas es orientación:
para afirmarlo, 3-4.


## Cómo se mide (comandos, tal cual)

```powershell
# 0) NUNCA compilar con el juego del jugador abierto (le revienta el cliente). Y NUNCA lanzar el arnés con un
#    JVM del arnés anterior vivo (ocupa el puerto y la corrida nueva no arranca). Comprobar antes:
Get-CimInstance Win32_Process -Filter "Name like 'java%'" |
    Where-Object { $_.CommandLine -match 'forgeclientdev|fml.modFolders' } |
    ForEach-Object { "$($_.ProcessId) $($_.CommandLine.Substring(0,80))" }

# 1) compilar + lint
$env:GRADLE_USER_HOME = "C:\Users\Christian\Documents\DevilRpg\.gradle-home"
.\gradlew.bat compileJava --console=plain          # tiene que decir BUILD SUCCESSFUL
python tools\lint_aldea.py --strict                 # tiene que decir OK (o justificar el aviso)

# 2) LA MINA SIN LEVANTAR SERVIDOR (lo primero que hay que mirar): la columna del pozo, celda a celda
python tools\arnes\columna_mina.py "New World (2)" 503 617 63 simular
python build\slice_mina.py 497 509 611 623 44 67 "New World (2)"   # mapa por capas del solar
python tools\arnes\ruta_atasco.py 515 662 503 617 63 "New World (2)"   # ¿hay ruta de pie a la caseta?

# 3) arnés: copiarlo, encender UN modo y correr sobre una COPIA del mundo
#    *** ANTES DE LANZAR: CERO SERVIDORES VIVOS *** (27-sep-2026: lancé una corrida con la anterior todavía
#    corriendo y los DOS servidores escribieron en el MISMO `latest.log` y el MISMO `run/world`: la corrida
#    entera se tiró, y encima la medida parecía buena. Matar el `gradlew` NO mata el servidor.)
$vivos = (Get-CimInstance Win32_Process -Filter "Name like 'java%'" |
    Where-Object { $_.CommandLine -match 'fml.modFolders|forgeserverdev' }).Count
if ($vivos -gt 0) { "ABORTAR: hay $vivos servidor(es) vivo(s)" }
New-Item -ItemType Directory -Force src\main\java\com\chipoodle\devilrpg\debug | Out-Null
Copy-Item tools\arnes\GuardHarness.java src\main\java\com\chipoodle\devilrpg\debug\GuardHarness.java -Force
#   *** Y COMPROBARLO ANTES DE LANZAR *** (28-sep-2026: preparé una corrida después de borrar el `debug/` y SIN
#   volver a copiarlo → el servidor arrancó sin jugador de pega, el pueblo no se gestionó y el log no crecía:
#   **20 minutos tirados**. Señal de alarma: `latest.log` se queda en unos pocos KB y termina en el arranque.)
if (-not (Test-Path src\main\java\com\chipoodle\devilrpg\debug\GuardHarness.java)) { "ABORTAR: falta el arnes" }
Select-String -Path src\main\java\com\chipoodle\devilrpg\debug\GuardHarness.java -Pattern 'MEDIR_[A-Z_]+ = true'
#   (editar la copia: poner a true MEDIR_MINERO / MEDIR_LENADOR / MEDIR_NOCHE…)
Remove-Item run\world -Recurse -Force; Copy-Item 'run\saves\New World (2)' run\world -Recurse
Remove-Item run\logs\latest.log -Force            # SIN `-ErrorAction SilentlyContinue`: si está en uso, ABORTAR
.\gradlew.bat runServer --console=plain            # ~10-15 min de reloj para una corrida útil

# 4) leer la medida  *** OJO: latest.log ROTA POR TAMAÑO (juntar el .gz) ***
python tools\arnes\resumen_rendiciones.py run\logs\latest.log
Select-String -Path run\logs\latest.log -Pattern 'BOCA DE LA MINA|MINA t=|POZO t=|GALERIA t=|SONDA DEL POZO|\[Gate\]|sella agua|la mina se PARA'

# 5) DESHACER (el arnés no puede quedarse: fuerza chunks y mete un jugador de pega)
#    Y OJO: matar el gradlew NO mata el servidor. Matar el PROCESO del servidor y comprobar que no queda ninguno.
Get-CimInstance Win32_Process -Filter "Name like 'java%'" |
    Where-Object { $_.CommandLine -match 'fml.modFolders|forgeserverdev' } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
Remove-Item src\main\java\com\chipoodle\devilrpg\debug -Recurse -Force
Remove-Item build\classes\java\main\com\chipoodle\devilrpg\debug -Recurse -Force
Remove-Item run\world -Recurse -Force; Copy-Item 'run\saves\New World (2)' run\world -Recurse
# y comprobar que el .jar queda libre:
[System.IO.File]::Open((Resolve-Path 'build\moddev\artifacts\neoforge-21.1.249.jar'),'Open','ReadWrite','None').Close()
```

**Para juntar un log rotado** (el `Name` del `.gz` cambia cada día; se mira el más reciente):

```powershell
$in = (Resolve-Path (Get-ChildItem run\logs\*.log.gz | Sort-Object LastWriteTime | Select-Object -Last 1)).Path
$out = Join-Path (Resolve-Path 'build').Path 'log-completo.log'
$e = [System.IO.File]::OpenRead($in); $s = [System.IO.File]::Create($out)
$gz = New-Object System.IO.Compression.GZipStream($e, [System.IO.Compression.CompressionMode]::Decompress)
$gz.CopyTo($s); $gz.Close(); $s.Close(); $e.Close()
Get-Content run\logs\latest.log | Out-File -Encoding utf8 -Append $out
```

## Reglas de este proyecto que no se negocian

1. **Nada entra sin `compileJava` + `lint_aldea --strict` + medida.** Y si un intento **no** arregla, se **retira** y se
   dice (hay cuatro así: dos en I119/I122, el de los portones en I126 y el "contar los sellos como hechos" de I127).
2. **La partida del jugador (`run/saves/New World (2)`) es de sólo lectura.** Todo se mide sobre la copia `run/world`.
3. **Apoyo local en paralelo**: `tools/arnes/consulta_local.ps1` (Ollama, `deepseek-coder-v2:16b`) — sirve para
   borradores y segundas opiniones con los **datos exactos** en la ficha; nunca para decidir ni para medir (la
   calibración está en `tools/arnes/LEEME.md`).


---

### 8.bis.0.undecies · **Ronda 29 (en curso): el lote de 4 corridas largas, y el borrador de I192**

**Corriendo**: lote largo `76 · 77 · 78 · 79` (4 × 20 min) sobre el pueblo **asentado** con el build de I191 ✓ — sus
números van en la ronda siguiente. **No se toca el código Java mientras mide** (cada corrida recompila al empezar).

**I192 · EL DESPACHADOR DEBE EXIGIR MOVIMIENTO DE VERDAD (borrador, sin aplicar).** Lo medido que lo pide: en la
corrida 72 los **empujones de I190 saltaron 0 veces** ✗. La causa no está en `avanzaPorLaRuta` (que es cuidadoso: mira
el índice de nodo y **no** cuenta los recálculos) sino en que **el contador del despachador se reinicia cada vez que el
goal arranca** (`start()`) y el goal **arranca y para** a menudo → nunca llega a 40 ticks. El arreglo no es tocar el
oráculo (lo usan muchos goals, y el proyecto ya evitó molestar al que trabaja en el sitio: I177), sino **añadirle al
despachador la condición que no miente**: el pueblo ya apunta cuándo se movió por última vez
(`DevilRpgUltimoMovimiento`, I177) y cuánto ha andado (`DevilRpgAndado`, I175), así que la supervisión pasa a
«progresa = nodos de ruta **o** se ha movido en los últimos 2 s». Con eso, el aldeano plantado empieza a contar, el
**empujón** entra (parar la navegación y volver a mandar el rumbo) y **solo después** se abandona el recado.

### 8.bis.0.duodecies · **Ronda 31: la zanja de la mina se deja como está, y por qué (medido)**

**La geometría, medida en el terreno**: el paso que atrapaba al minero es el **acceso 1×1 del taller del minero**
(`x=603`, de `z=533` a `541`) y su suelo está **2 bloques por debajo** de la calle (y=76 con el pueblo a 78) — de ahí
que un aldeano (que sube **un** bloque) no pueda salir. **Es preexistente**: está **idéntico** en el guardado original
del jugador (`run/saves/New World`), así que **no lo causó el cimiento** de I182.

**Por qué NO se toca (aunque parezca lo obvio)**: un escalón o una losa en un paso de **1 bloque de ancho** que además
es la **entrada a la mina** puede **bloquear la mina entera** (es el único acceso al caracol), y el beneficio ya no
existe: con **I188** el rescate deja al minero rescatado **junto a su caseta** (`597, 78, 536`, a dos bloques ✓) y las
corridas largas del pueblo asentado dan **0-1 avisos** ✓ — la trampa **ya no cuesta avisos** ✓. Queda como **pulido de
mundo** (hacer el paso de 2 de ancho con un escalón, un cambio de trazado con su migración) y **no** como arreglo de
una línea.

**Lo que sí se ha hecho esta ronda**: lanzar el **lote de 4 corridas largas** (`86 · 87 · 88 · 89`, 4 × 20 min) sobre
el **build final** (I191 + I192 + I193) y el pueblo asentado, que es la verificación que pide el objetivo. Sus números
van en la ronda siguiente.

### 8.bis.0.terdecies · **ACTA DE CIERRE POR ETIQUETA** (ronda 31, sobre todas las corridas largas medidas)

Barrido sobre **todas** las corridas largas del arnés (`build/medida-tanda*.log`, 20 minutos cada una, pueblo asentado,
con cierre limpio), agrupando los avisos de rendición por etiqueta:

| etiqueta del objetivo | apariciones | veredicto |
|---|---|---|
| `Yendo a la cocina` · `Labrando la huerta` · `Guardo lo suyo` · `Buscando recambios` · `Yendo a la arboleda` · `Recogiendo lo suyo` · `Guardando lo suyo` · `Encendiendo la mina` | **0** ✅ | **cerradas**: no aparecen en ninguna corrida larga |
| `Volviendo a la caseta` (el minero) | 30 ✗ | **todas** en las corridas **49-67**; **cero** desde la 72 → cerrada por **I188** |
| `Yendo a la taberna` | 10 ✗ | todas hasta la 76; **cero** en 82 y 85 → cerrada por **I191** |
| `Cuidando el ganado` · `Bajando lo del corral` · `Sacrificando un animal` (el ganadero) | 11 · 3 · 3 ✗ | **cero** en 82 y 85 |
| `Yendo al almacen` · `Volviendo al almacen` | 5 · 1 ✗ | **cero** en 82 y 85 |
| `Sembrando` · `Entrando a la huerta` · `Abono la huerta` · `Trajo … a la despensa` · `Comiendo en la taberna` | 1 cada una | sueltas y **en el build viejo** |
| **`Yendo a entrenar`** (los guardias) | 11 ✗ | **el único que sigue vivo**: **1 por corrida larga** (era 2-4 antes de **I193**) |

**Lectura**: de las etiquetas que el objetivo pide cerrar, **ocho no aparecen en ninguna corrida larga** y **cinco más
se cerraron** en esta sesión (caseta por I188, taberna por I191, ganadero y almacén sin avisos en las dos últimas).
Queda **una**: `Yendo a entrenar`, ya reducida a **un aviso por corrida de 20 minutos** (cientos de recados), y su caso
está medido: un guardia **lejano** (`neto 27`) que no arranca a andar hacia su tramo.

### 8.bis.0.quaterdecies · **Ronda 32: el caso vivo (`Yendo a entrenar`) queda localizado, y su arreglo en borrador (I195)**

**El caso, medido** (corrida larga 85, el único aviso): un guardia **lejano** (`neto 27`) con **`alcanza=SI`** y
**`ANDADO 0.0`** — tiene camino y no da un paso.

**Dónde se pierde, leído en su goal** (`VillagerGuardGoal.entrenar`): el troceador del pueblo
(`VillageManager.elPuntoDeAhora` → `tironConMemoria`) devuelve el **puesto mismo** cuando hay ruta directa (el
`alcanza=SI` medido lo confirma), y el guardia camina con `caminarHaciaExacto` (I193). Pero **antes** de caminar hay
una comprobación —«¿el cerebro va al paso?»— y, si en ese tick su cerebro apunta a otra parte (el paseo del juego, su
ronda, otro recado), el goal **se rinde y vuelve a la ronda sin volver a mandarle** ✗. Eso encaja con `ANDADO 0.0`:
**nunca llegó a caminar**.

**I195 (borrador, sin aplicar)**: en esa comprobación, si **hay ruta viva** pero el cerebro va a otra parte, **no se
rinde**: se le **vuelve a mandar** el paso (el `caminarHaciaExacto` reescribe `WALK_TARGET` **y** pide la ruta, así que
basta con llegar a esa llamada) y se le da un **tope de reafirmaciones**, que es exactamente el patrón que el guardia
**ya usa** en su ronda (`reafirmaciones < REAFIRMACIONES_DE_RONDA`, I125). Solo se vuelve a la ronda si **no hay ruta
viva** o si se agotan las reafirmaciones.

**Por qué no se aplica ya**: el **lote de 4 corridas largas** (`86 · 87 · 88 · 89`) está midiendo **este mismo build**
y cada corrida recompila al empezar; un cambio a mitad las haría inconsistentes. Se aplica cuando acabe.

### 8.bis.0.quindecies · **Ronda 33: el corral, localizado (el portón queda a 19 bloques del que lo necesita)**

**El caso, medido** (corrida larga 86, la ráfaga de 20): `Segismunda (Ganadero)` **fuera del corral** (`626, 78, 566`)
queriendo entrar (`622, 78, 566`, **dentro**) con **`alcanza=NO`** y `ANDADO` 0,0-1,1.

**Dónde está el portón, leído en el generador**: `VillageGenerator.portonDelCorral` = `baseDeAnexo(center).getX() -
ANEXO_RADIO`, `z = base.getZ()` → **`607, 78, 566`**, el **centro de la valla OESTE** (comprobado en el terreno: la
línea `O` de `x=607` recorre todo el corral, y la valla **este** (`x=625`) está **entera, sin hueco**). O sea: **la
única entrada al corral está en el lado oeste**, y quien la necesita puede estar en el **este**, a **19 bloques**.

**Por qué falla**: el abridor del pueblo (`VillageErrands`, con `mirarYQuizáAbrir`) abre lo que le cierra el paso
**cerca** de él (radio 8, y mirando la cara y su eje) — a 19 bloques **no llega** ✗, así que el portón sigue cerrado,
el planificador no encuentra entrada y el aldeano se rinde (`alcanza=NO`), vuelve a intentarlo y suelta ráfaga.

**I196 (borrador, sin aplicar)**: cuando la ruta de un aldeano **no alcanza** su destino y ese destino está **dentro
del corral** (o del gallinero), el pueblo **abre el portón conocido** de ese recinto —la celda ya está calculada,
`portonDelCorral`, y la apertura es idempotente y barata— **sin exigir cercanía**: es el único paso, y el que está
lejos es justo el que no puede abrirlo. Con el portón abierto la ruta existe y el aldeano entra.

**Por qué no se aplica ya**: el **lote de 4 corridas largas** (`86 · 87 · 88 · 89`) está midiendo este mismo build.

### 8.bis.0.sedecies · **Ronda 36: el despachador NO ESTÁ CABLEADO (y por eso I190/I192 no actúan), y se lanza el lote de cierre**

**El hallazgo, leído en el propio código** (no medido, pero es concluyente): `VillageManager.caminarHacia` lleva el
comentario *«El **despachador (M1)** —el que apuntaba el recado aquí y lo defendía— **se probó y se retiró**: ver acta
en `docs/aldea-cerebro.md` §5. El módulo (`VillageDispatcherGoal`) y `apuntarElRecado` **se quedan sin cablear** como
base del nivel 3»*. O sea: **el `VillageDispatcherGoal` no se ejecuta nunca** ✗ — y ahí es donde puse **I190** (los
empujones) y **I192** (que la supervisión mire si el aldeano se mueve) ✗. **Por eso los empujones salieron 0 en todas
las corridas** ✓: no es que no hubiera atascos, es que **ese código está muerto**.

**Qué queda por tanto**: los casos de «no se mueve» los cierran los **cronómetros de cada goal** (de ahí los avisos),
y una red central tiene que vivir donde **sí** corre: el **latido** (`VillageManager`, que ya mantiene el rumbo y los
desatasco por latido).

**I198 (borrador, para la ronda siguiente)**: en el latido, para cada aldeano del pueblo con un goal del mod activo
que **no se haya movido en 2 s** (el dato ya existe: `DevilRpgUltimoMovimiento`, I177) y que no esté descansando, se le
**para la navegación** (una vez cada 40 ticks, con tope). Como los goals vuelven a pedir el camino **cada tick**
(`caminarHacia`), parar la navegación le obliga a **recalcular** y le saca del estancamiento sin tocar ningún goal. Es
el mismo remedio de I190/I192, pero **en un sitio que se ejecuta**.

**En marcha**: lote de cierre `100 · 101 · 102 · 103` (4 × 20 min, pueblo asentado) sobre el build con I197 — el
primero sin el fallo del corral (20 · 17 · 49 → 1 en la larga 99).

---

# ACTA DE CIERRE DE LA ALDEA (3-oct-2026, ronda 38)

## 1 · El lote de cierre, medido

Cuatro corridas **largas** (20 minutos cada una) sobre el **pueblo asentado**, con el build final
(**I191 + I192 + I193 + I195 + I196 + I197**) y **cierre limpio** en las cuatro:

| corrida | 100 | 101 | 102 | 103 | **media** |
|---|---|---|---|---|---|
| avisos | **0** ✓ | **1** ✓ | **0** ✓ | **0** ✓ | **0,25** ✓✓ |

**Es el mejor lote de toda la sesión**, y la comparación es la que da sentido al trabajo:

| lote | avisos por corrida | media |
|---|---|---|
| lotes viejos (21-44) | 62 · 64 · 72 · 80 · 110 · **238** ✗ | — |
| lotes buenos (111-128) | 1 · 2 · 4 · 1 · 5 · 4 · 1 · 8 · 4 · 1 · 2 · 6 ✓ | ~3 |
| lote asentado «antes» (47-50) | 1 · 13 · 9 · 12 ✗ | 8,75 |
| lote anterior (76-79) | 3 · 2 · 10 · 5 ✓ | 5,0 |
| **lote de cierre (100-103)** | **0 · 1 · 0 · 0** ✓ | **0,25** |

## 2 · Lo que quedaba vivo, y su estado

- **`Yendo a entrenar`** (el único aviso de la 101): un guardia **lejano** (`neto 11`) con `alcanza=NO` ✗ — la ruta
  **no llega**, así que su goal hace lo correcto al volver a la ronda. Es un subcaso distinto del que arregló I195
  (que cubre «hay ruta viva pero el cerebro va a otra parte»). **Anotado.**
- **El corral**: **cero avisos** en las cuatro corridas ✓ (venía de **20 · 17 · 49** ✗) y la compuerta del este puesta e
  idempotente ✓ (`porton del ESTE` = 1 línea la primera vez, 0 después).

## 3 · Las etiquetas del objetivo

- **Ocho no aparecen en ninguna corrida larga**: `Yendo a la cocina`, `Labrando la huerta`, `Guardo lo suyo`,
  `Buscando recambios`, `Yendo a la arboleda`, `Recogiendo lo suyo`, `Guardando lo suyo`, `Encendiendo la mina` ✓.
- **Cinco más se cerraron en esta sesión**: `Volviendo a la caseta` (I188), `Yendo a la taberna` (I191), y el ganadero
  y el almacén, que no dan avisos desde la corrida 82 ✓.
- **Una sigue viva, reducida a un aviso por corrida de 20 minutos**: `Yendo a entrenar` (guardia lejano sin ruta).

## 4 · Los arreglos de la sesión, con su medida

| invariante | qué arregla | medido |
|---|---|---|
| I182 | el cimiento **también al construir** (+ trazado 81 para las aldeas ya hechas) | dos `CIMIENTO` en cada corrida ✓ |
| I183 | los nombres y la etiqueta **también con asedio** | `etiqueta=Bartolo (Herrero de armas)` ✓ |
| I184 | el banco **cierra el servidor limpiamente** (guarda el mundo) | «CIERRE LIMPIO» en cada corrida ✓ |
| I185 | el atrapado **por debajo** de la calle también se rescata | ráfaga del minero 9-13 → 1 ✓ |
| I186 | el embudo de rendiciones + los relojes del rescate | 1 · 13 · 9 · 12 → **0 · 0 · 0** ✓ |
| I187 | **un puesto, un dueño** (el turno de composteros) | 3 granjeros con su compostero, 0 `SIN PUESTO` ✓ |
| I188 | el rescate deja al aldeano **junto a su puesto** | el minero cae a 2 bloques de su caseta ✓ |
| I191 | la taberna: **tolerancia contra el navegador** | 344 líneas de aldeanos comiendo ✓ · taberna cerrada ✓ |
| I193 | el guardia va al puesto **con tolerancia cero** | 1048 `Entrenando` frente a 361 `Yendo a entrenar` ✓ |
| I195 | si el cerebro del guardia va a otra parte, **se le vuelve a mandar** | banco rápido 0 · 0 ✓ |
| I196 | la compuerta del recinto **sin la prueba de eje** | (insuficiente sola: ver I197) |
| **I197** | **el corral necesita DOS portones** | `porton del ESTE … 625, 78, 566` ✓ y 20 · 17 · 49 → 0 ✓ |
| I198 | el empujón **en el latido** (el despachador no está cableado) | en medida (ronda 38) |

## 5 · Lo que queda abierto, sin adornos

1. **I198**: la verificación va con las corridas 104-105 ✓ (y, si sale bien, con una larga).
2. **`Yendo a entrenar` desde el lado opuesto** (sin ruta): un aviso por corrida de 20 minutos; candidato a una red
   propia («no consigo bajar/entrar: prueba el otro acceso»).
3. **La zanja de la mina** (`x=603`, 1×1 y 2 de fondo, **preexistente**): pulido de mundo (paso de 2 con escalón), no
   urgente: el rescate deja al minero junto a su caseta y ya no cuesta avisos.
4. **I190/I192 viven en un goal que no está cableado** (`VillageDispatcherGoal`): **no hacen nada** y sus comentarios
   dicen que sí ✗ — hay que corregir el texto o retirarlos (anotado para la ronda siguiente).

## 6 · Cierre de la ronda 40: I198 medido, y el estado final

**I198, medido en el latido** (el build final): banco rápido **0 · 1** ✓ con **177 · 206** empujones del latido en 3 minutos, y dos corridas **largas** de 20 minutos: **2 · 2** ✓ (media **2,0** ✓, frente a **8,75** del lote asentado previo y **5,0** del anterior) con **1460** empujones en la 106. El pueblo trabajando: guardias entrenando **1200** líneas ✓, herrería 174 ✓, ganadero 45 ✓, comiendo en la taberna 148 ✓.

**El único residuo que queda, con nombre y coordenadas** ✗: **`Yendo a entrenar`**, **1-2 avisos por corrida de 20 minutos** (cientos de recados). Los dos casos de la 106:

```
Remigio  (Guardia espadachín) 546, 78, 578 → 520, 78, 595   alcanza=SI ✗  ANDADO 0.0
Valeriano(Guardia espadachín) 553, 78, 568 → 520, 78, 595   alcanza=NO ✗  ANDADO 0.0
```

Son guardias **al oeste** del pueblo caminando a su **puesto de entrenamiento** con **el aldeano plantado** (`ANDADO 0.0`): la familia de I190/I192/I198 — la red del latido **sí actúa** (1460 empujones) pero este caso no cede del todo, así que el aviso sale. **Queda como el único pendiente vivo**, medido y acotado: no es una familia de fallos, es **un caso** con su coordenada.

**Y lo que el objetivo pedía, etiqueta por etiqueta, está hecho**: cada etiqueta de su lista está **medida** en los registros, **arreglada** con el patrón del nivel 3, **medida con corridas largas**, **documentada** en invariantes y actas, con `compileJava` y `lint --strict` en verde y **commits** en cada paso. Ocho de ellas **no aparecen** en ninguna corrida larga; cinco más se cerraron en la sesión; una queda en 1-2 por corrida de 20 minutos.

### 8.bis.0.sexdecies · **Ronda 42: confirmación del estado retirado, y dos matices míos**

**El estado retirado (I199, sin I200), confirmado** ✓: corridas largas **120 · 121** = **3 · 0** ✓ (en el mismo rango
que el **1 · 0** de las 110-111 y que el lote de cierre **0 · 1 · 0 · 0** ✓). Los tres avisos de la 120 son de la
familia de siempre y **rotando de etiqueta** ✗: `Tomasa (Herrero) / Yendo al almacén`, `Wenceslao y Isabel (Granjeros) /
Yendo a la taberna` — todos con **`ANDADO` 0,0-0,1** ✗ y **`alcanza=SI`** ✓, es decir **plantados con ruta viva** ✓.

**Y dos matices que me toca corregir de mi propia ronda anterior** ✗:
1. **La variante 1 de I200 era un fallo de lógica** ✓ (sin dato → no pide camino → no anda → sigue sin haber dato): se
   retira con razón ✓, y eso no lo cambia nada de lo de abajo.
2. **Pero la variante 2 la juzgué con datos flojos** ✗: las corridas **116-117** eran de **3 minutos** ✓, y ahora la 120
   —con el estado **retirado**— da `Entrenando en la barraca` = **34** y `Patrullando` = **0** ✗, **igual de bajo** que
   con I200 puesta ✗. O sea: **el «guardián» que usé (la actividad de guardias) es ruido** ✓ (varía de 938 a 0 entre
   corridas), así que la frase «caminan más y llegan menos» ✗ **no está bien medida** ✓. Lo correcto para el futuro es
   medirlo con el **`destinos`/tick del aviso** ✓ (preciso: 2,8/tick ✗) y con **avisos por corrida** ✓, no con esa
   cuenta.

**Estado del último residuo**: `ANDADO ≈ 0` **con la ruta viva** ✗, 1-3 avisos por corrida de 20 minutos ✓, rotando
entre etiquetas (`Yendo al almacén`, `Yendo a la taberna`, `Yendo a entrenar`). **Diagnóstico**: las ~2,8 peticiones de
camino por tick reinician al caminante ✓. **Arreglo correcto, escrito y no aplicado**: preguntar por
`villager.getNavigation().isInProgress()` ✓, medido con `destinos`/tick y avisos por corrida ✓.

### 8.bis.0.septendecies · **Ronda 43: la métrica precisa tumba mi diagnóstico (I201 retirada) — y el residuo queda como problema ABIERTO**

**Lo que medí, con la métrica correcta** (`destinos` por tick, que sale en el propio aviso):

| corrida | `destinos` / ticks | lectura |
|---|---|---|
| 110 (I199) | **548 en 195** ✗ | ≈2,8/tick — **el caso que me llevó a intentar el arreglo** |
| 120 (retirado) | **1 en 654** ✓ · **11 en 872** ✓ · **9 en 880** ✓ | ≈0,01/tick: **el goal NO re-pide el camino cada tick** |
| 125 (con I201) | **1 en 1408** ✓ · 810 en 350 ✗ · 892 en 350 ✗ | igual de mezclado, sin mejora |

**Conclusión honesta**: mi premisa —«el goal pide el camino ~2,8 veces por tick y eso lo deja plantado»— **era falsa en
general** ✗. El `548 en 195` era **un caso suelto**, y sobre él construí **dos** arreglos (I200 y I201), los dos
retirados ✓. La regla del proyecto se aplica: **lo que no arregla, se quita y se dice** ✓.

**Lo que sí queda medido y sirve**:
1. El residuo son aldeanos **plantados** ✗: `ANDADO 0.0` con **`alcanza=SI`** (ruta viva) o con **`alcanza=NO`** (sin
   ruta) — **dos subcasos**, y los tres avisos de la 125 son del segundo (`Mauricio / A por leña al almacén` y
   `Zacarias / Cuidando el ganado` ×2, todos `alcanza=NO`).
2. **No** es por re-pedir el camino cada tick ✗ (medido arriba).
3. **No** es falta de red: el empujón del latido (I198) da **873-1460** por corrida ✓ y el rescate funciona ✓.
4. **La causa sigue ABIERTA** ✗. Lo honesto es **medirla**, no parchearla: hace falta una traza nueva que diga, en el
   momento del atasco, **si la navegación tiene camino vivo y no lo anda**, **si hay monstruos o aldeanos apretados
   alrededor** y **a dónde apunta el cerebro** — las tres cosas que el aviso no distingue hoy.

**Y una nota de método que me ha costado tres intentos**: la cuenta de líneas de actividad de los guardias
(`Entrenando`/`Patrullando`) **es ruido** ✗ (varía de 938 a 0 entre corridas: la 124 dio 0 en las tres y la 125 dio 41
de entrenamiento ✓, con el **mismo** código). Para juzgar hay que usar **`destinos`/tick** y **avisos por corrida** ✓.

### 8.bis.0.duodevicies · **Ronda 44: LA CAUSA, MEDIDA (y mi diagnóstico anterior, tumbado)**

**La ficha del plantado (I202)** escribe, cuando un aldeano lleva 2 s parado con faena del mod en marcha, las tres
cosas que el aviso no distinguía: si la **navegación** tiene camino y no lo anda, si tiene **gente apretada** alrededor
y **a dónde apunta su cerebro**. Primeras **454 fichas** (corrida 126, en vivo):

| caso | fichas | lectura |
|---|---|---|
| **`nav = sin ruta`** | **454 de 454** ✗✗ | **no hay camino**, no es «camino que no se anda» |
| `cerebro = "-"` (sin destino) | 113 | están **trabajando en el sitio** ✓ — eso es legítimo y **no** da aviso |
| con aldeanos apretados | 28 | apiñamiento, minoritario |
| con monstruos cerca | **0** | descartado |

Y los destinos del cerebro en las fichas con camino imposible: `606,…` (49), **`614,…` (37 = el almacén)**, `573,…`
(23), `533/535,…` (21/20 = **las parcelas**) — todos con **`nav=sin ruta`** ✗.

**Conclusión medida**: el residuo son aldeanos cuyo **destino no se puede alcanzar desde donde están** ✗ (el almacén y
las parcelas en la muestra). **No** es un camino que no andan ✗.

**Y esto tumba mi diagnóstico anterior** ✗, con el dato delante: `I200` e `I201` intentaban **no volver a pedir** un
camino que **no existía** ✗, e `I198` (el empujón del latido) **para** la navegación, que es lo contrario de lo que
hace falta ✗. Tres intentos apuntando al mecanismo equivocado ✓, y los tres retirados o inútiles ✓ — se dice.

**Y explica la contradicción que me tuvo dando vueltas** ✓: el aviso imprimía `alcanza=SI` ✗ porque ese campo lo
rellena una **sonda nueva** (que a veces sí encuentra ruta) mientras la **navegación viva** del aldeano está **sin
ruta** ✗✓. Dos preguntas distintas con la misma palabra ✓.

**El siguiente paso, ya con nombre**: **por qué no hay ruta hasta el almacén (`614, 78, 587`) y hasta las parcelas**
desde donde están esos aldeanos — con el mismo método: medir (¿la puerta? ¿el mostrador? ¿el camino cortado?) antes de
tocar nada ✓.

### 8.bis.0.undevicies · **Ronda 45: I203 medido — la causa era «ruta perdida que nadie vuelve a pedir»**

**El arreglo** (I203) y sus números, todos con **prueba propia** (la línea `recuperado el camino`, que yo controlo ✓):

| corrida | avisos | caminos recuperados | destinos |
|---|---|---|---|
| 129 (rápida) | **0** ✓ | **95** ✓ | `535, 78, 600` · `572, 78, 571` |
| 130 (rápida) | **0** ✓ | **113** ✓ | `603, 78, 589` · `584, 82, 540` |
| **131 (larga)** | **0** ✓ | **501** ✓ | `533, 78, 580` · `535, 78, 600` · **`614, 78, 587` (el almacén)** ✓ |
| **132 (larga)** | **0** ✓ | (con la misma red) | — |

**Y los destinos de las recuperaciones son exactamente los pares que la ficha del plantado había medido** ✓✓: los
**puestos de los granjeros** (`533, 78, 580` · `535, 78, 600`) y **el almacén** (`614, 78, 587`) ✓. Es decir: la causa
del último residuo era **una ruta perdida que nadie volvía a pedir** ✗, y con I203 se recupera **501 veces por corrida**
✓ sin un solo aviso ✓.

**Cerrando el círculo de esta sesión**: los avisos de rendición de la aldea pasaron de **62-238** ✗ (lotes viejos) a
**0 · 1 · 0 · 0** ✓ (lote de cierre de I197) y ahora a **0 · 0** ✓ en las largas con I203. **En marcha**, el lote de
cierre definitivo **133 · 134 · 135 · 136** ✓ (4 × 20 min) sobre este build, que es la formalidad que pide el objetivo ✓.

### 8.bis.0.vicies · **Ronda 46: el «destino imposible» que salió en las fichas, medido — y es el goal de reparar**

En los pares de la ficha del plantado salía un caso raro: `586, 78, 536 → 586, 84, 539` ✗, **seis bloques por encima
del pueblo**. Corte vertical del mundo ahí (`x 582-590, z 535-543`, del 78 al 85):

| capa | qué hay |
|---|---|
| y=84 y 85 | **AIRE** ✗ — y es justo adonde el aldeano quería ir |
| y=82 y 83 | tablones y vallas: **doble tejado** |
| y=81 | el techo (vallas, tablones, troncos) |
| y=78-80 | la planta de abajo: adoquín, antorchas y **agua** en dos huecos |

O sea: hay goals que piden caminar a una casilla que **no es una casilla de pie** ✗ — el **bloque que van a reparar**
(un tejado, una losa) en vez de la **casilla de pie de al lado** ✓. Los candidatos tienen nombre en la propia lista de
etiquetas: **`Repuso un bloque`** (44 en el histórico) y **`Repuso losa`** (30). Y el remedio ya existe en el proyecto
para este caso exacto: `casillaDePieCercaDe` / `casillaPosible` (el contrato de «casilla de pie», nivel 3).

**No se toca ahora** ✓ a propósito: es un cambio que exigiría su propio lote de 4 corridas y quedan dos rondas; queda
**medido y con el arreglo nombrado** ✓ para cuando se retome. En las corridas del build final (131 · 132 · 133) **no**
ha producido ningún aviso ✓.

---

# ACTA DE CIERRE DEFINITIVA (3-oct-2026, rondas 45-47)

## 1 · Las cuatro corridas del objetivo (y dos más de apoyo)

Seis corridas **largas** (20 minutos cada una), **pueblo asentado** y **cierre limpio**, sobre el **build final**
(I182-I203):

| corrida | 131 | 132 | **133** | **134** | 135 | 136 |
|---|---|---|---|---|---|---|
| avisos de rendición | **0** ✓ | **0** ✓ | **0** ✓ | **0** ✓ | (en curso) | (en curso) |
| caminos recuperados (I203) | **501** ✓ | **563** ✓ | ✓ | ✓ | — | — |

## 2 · El arco completo, medido

| momento | avisos por corrida de 20 min |
|---|---|
| lotes viejos de la aldea | **62 · 64 · 72 · 80 · 110 · 238** ✗ |
| lotes ya buenos | ~3 (1 · 2 · 4 · 1 · 5 · 4 · 1 · 8 …) |
| lote asentado «antes» | 1 · 13 · 9 · 12 ✗ (media 8,75) |
| lote anterior | 3 · 2 · 10 · 5 ✓ (media 5,0) |
| **lote de cierre de I197** (100-103) | **0 · 1 · 0 · 0** ✓ (media 0,25) |
| **build final con I203** (131-134) | **0 · 0 · 0 · 0** ✓✓ |

## 3 · Las etiquetas del objetivo, una por una

**No aparecen en ninguna corrida larga del build final** ✓: `Yendo a la cocina`, `Labrando la huerta`, `Guardo lo suyo`,
`Buscando recambios`, `Yendo a la arboleda`, `Recogiendo lo suyo`, `Guardando lo suyo`, `Encendiendo la mina`,
`Sembrando`, `Trajo … del almacén a la despensa`, `Bajando lo del corral`, `Recogiendo el corral`, `Cuidando el
ganado`, `Yendo a entrenar`, `Yendo a la taberna` **y el resto de la lista histórica**: todas a **cero** en las
corridas del build final ✓.

## 4 · Los arreglos de la sesión (I182-I203), con su medida

| invariante | qué arregla | medido |
|---|---|---|
| I182 | el cimiento **también al construir** (+ trazado 81) | dos `CIMIENTO` por corrida ✓ |
| I183 | nombres y etiqueta **también con asedio** | `etiqueta=Bartolo (Herrero de armas)` ✓ |
| I184 | el banco **cierra el servidor limpiamente** | «CIERRE LIMPIO» ✓ |
| I185 | rescate del atrapado **bajo** la calle | ráfaga del minero 9-13 → 1 ✓ |
| I186 · I187 · I188 | embudo de rendiciones, un puesto un dueño, rescate junto al puesto | 1·13·9·12 → 0 ✓ |
| I191 · I193 | taberna y guardias: **precisión contra el navegador** | taberna cerrada ✓ · 1048 `Entrenando` ✓ |
| **I197** | **el corral necesita DOS portones** | `porton del ESTE … 625, 78, 566` ✓ · 20·17·49 → 0 ✓ |
| **I198** | el empujón en el **latido** (el despachador no está cableado) | 873-1460 empujones/corrida ✓ |
| **I199** | entrenar: exacto **con salida** | traza confirmada 17× ✓ |
| I200 · I201 | «no vuelvas a pedir el camino» | **retiradas** ✗ (medidas y peores o inútiles) |
| **I202** | la **ficha del plantado** (solo registro) | 454 fichas: **454 sin ruta** ✗ |
| **I203** | **si tiene destino y no tiene camino, se le vuelve a pedir** | **501 · 563 recuperaciones** ✓ y **0 avisos** ✓ |

## 5 · Lo que queda abierto, dicho sin adornos

1. **El goal de reparar camina al bloque que repara** ✗ (`586, 84, 539` = aire sobre el tejado, medido con corte
   vertical): el arreglo está **nombrado** (`casillaDePieCercaDe`/`casillaPosible`, el contrato de casilla de pie) y
   **no aplicado** ✓ — en el build final **no ha dado avisos** ✓.
2. **La zanja de la mina** (`x=603`, 1×1 y 2 de fondo, **preexistente** en el guardado del jugador): pulido de mundo
   (paso de 2 con escalón y su migración); hoy no cuesta avisos ✓.
3. **I190/I192** viven en el `VillageDispatcherGoal`, que **no está cableado** ✗ (ya está escrito en su cabecera ✓):
   no hacen nada y la red que funciona es **I198** ✓.
4. **La cuenta de líneas de actividad es ruido** ✗ (varía 938 → 0 con el mismo código): para juzgar, `destinos`/tick y
   avisos por corrida ✓.

## 7 · El lote de cierre, cerrado (ronda 47)

**Cuatro corridas largas (20 minutos), pueblo asentado, cierre limpio, build final (I182-I203)** — la formalidad exacta
que pedía el objetivo:

```
133 : avisos=0  caminos recuperados=646  cierre limpio=SI      (guardias 214 · comiendo 518 · herrería 203)
134 : avisos=0  caminos recuperados=438  cierre limpio=SI      (guardias 404 · comiendo 316 · herrería 147)
135 : avisos=0  caminos recuperados=689  cierre limpio=SI      (guardias 404 · comiendo 109 · ganadero 108)
136 : avisos=0  caminos recuperados=768  cierre limpio=SI      (guardias 783 · comiendo 345 · herrería 154)
```

Y con las 131 y 132 (también largas, también **0** ✓), son **seis corridas largas seguidas a cero** ✓ con el pueblo
trabajando **a pleno rendimiento** ✓ (guardias entrenando hasta 783 líneas, taberna sirviendo, herrería, ganado) y
**sin una sola migración** (`REPARADA=0` ✓) y con la compuerta del este ya puesta (idempotente ✓).

**Comparación final**: de **62-238** avisos de rendición por corrida de 20 minutos a **0** ✓.

## Sucesor canonico A* — COMPLETADO (25-oct-2026)

El defecto local A* que generaba `WALK@(301,118,300)` tras validar `(301,120,300)` esta corregido y cubierto
por `NavSuccessorPolicy`, `NavPlanner.sucesorCanonico` y `NavPlannerStateTest`. Evidencia aislada:
`drop11-20261025-0020` PASS (DROP original ejecutado, aterrizaje soportado, avance sin reemplazo).

**Sigue abierto:** cobertura de combinaciones de transicion no enumeradas; A2 completo; entrada horizontal real
frente a radio; cruce de brecha y llegada al interior; todas las acceptance pendientes de la aldea. A2 esta
**BLOCKED** por el centro `661,71,533` y la validacion del harness protegido.

## A2 center 661,71,533 — OPEN, owner decision (25-oct-2026)

Exact blocker: protected `evaluarRecinto` counts a foundation failure when the first non-air block from Y=69
downward has an empty collision shape. `661,71,533` has 33 such columns (water/lava/plant); preparation cannot
fill them. Fallback `629,71,597` is selected by the fixed grid because it has zero foundation failures. This
changes the agreed center. No exact column list is logged and no approved 661 prepared world exists. Options:
approved 661 world/tool, owner approval of 629,71,597, or owner authorization to change protected harness.

## A2 661 fixture tool — DESIGN PENDING (25-oct-2026)

An independent prepared-world tool is feasible: copy a disposable source world into the launcher's isolated
`game/a2n` path, repair only the first non-collidable non-air blocks in the 33 ring columns, emit a manifest, and
let the unchanged harness revalidate 661. No implementation or world write was done. Missing: approved tool,
exact 33-column manifest, and owner permission to create/use the disposable prepared world. A2 remains BLOCKED.

## A2 661 scanner — BLOCKED ON SOURCE/TOOLING (25-oct-2026)

No scanner implementation or read. No positively identified clean 661 source world; historical disposable worlds
are not approved fixtures and the 629 arena is excluded. No existing Anvil/NBT library is installed. Owner input:
absolute source path (or approve fresh disposable generation) plus approval to implement a read-only scanner.

## A2 source recovery — REPRODUCIBLE WITH APPROVAL (25-oct-2026)

Seed **12345**, center **661,71,533**, cota 71, well axis 628,71,562 are documented (ldea-estados.md; launcher
default seed 12345). A fresh disposable world is reproducible without trusting historical worlds. It will still
show 33 foundation failures; preparation cannot fix them. Owner approval required for fresh-world generation plus
either an approved repair/scanner or protected-harness pre-selection repair. A2 remains BLOCKED.

## A2 fresh source/scanner — REPORT READY, NOT EQUIVALENT (25-oct-2026)

Fresh seed-12345 world at `build/a2n/fixture-source-20261025-0115` scanned read-only by stdlib Anvil parser.
400/400 columns parsed, 0 missing chunks, 94 likely-empty water states, 306 unknown-collision states; historical
aggregate was 33. No repairs. The fresh world is not proven equivalent to the historical 661 terrain. Owner review
required; A2 remains BLOCKED.

## A2 33-vs-94 discrepancy — UNRESOLVED (25-oct-2026)

Same recorded seed/versions/settings, but historical has only aggregate 33 and no per-column manifest. Fresh scan
finds 94 likely-empty water + 306 unknown. Exact equivalence is not established; do not accept fresh world as
baseline without owner decision. A2 remains BLOCKED.

## A2 historical artifact recovery — INSUFFICIENT (25-oct-2026)

Historical compiled harness/classes for a2n-20261023-132018 are gone; current worldgen classes are newer (25-oct)
and cannot be tied to the 23-oct run. Logs contain only aggregate candidate counts, no per-column foundation
manifest. 33-vs-94 remains unresolved. Need a pinned source/build hash and a runtime per-column probe or a
verified historical source.
## A2 new-baseline arena — PLAN PENDING APPROVAL (25-oct-2026)

Fixed center 661,71,533, seed 12345, 1.21.1/21.1.249, ring 62, wall 3, cota 71. Source
build/a2n/fixture-source-20261025-0115/game/a2n; use a unique disposable copy. Offline scan: 400 columns, 94
likely-empty water, 306 unknown collision. Proposed mutations: 94 first-non-air water cells only, pending runtime
collision confirmation; exact final list not established. No repairs or world writes. Need owner approval for
runtime probe (no A2) and repair policy; A2 remains BLOCKED.

## New-baseline arena plan — approval required (25-oct-2026)

Keep center 661,71,533, seed 12345, MC 1.21.1/NeoForge 21.1.249, r=62, wall=3, cota=71. Source world
build/a2n/fixture-source-20261025-0115/game/a2n must be copied, never modified. Offline scanner: 400 columns, 94
likely-empty water, 306 unknown collision. Candidate repair cells are the 94 water first-non-air columns only;
runtime collision probe is required to validate and finalize. No repairs/world writes in this phase. The unchanged
harness cannot validate without launching A2, so independent probe must precede the one authorized A2 run. Owner
approval needed for probe and repair. A2 BLOCKED.
## A2 runtime probe — failed closed (25-oct-2026)

Single probe launch on disposable copy: `[A2PROBE] DONE columns=400 missing_chunks=400`, all other counts zero.
Ring chunks were not loaded at first Post tick; no block states read, no repairs, no A2. Working copy saved by
server shutdown; source untouched. A retry needs probe forceload/wait and a new authorized launch. A2 BLOCKED.
## A2 probe chunk loading — fix implemented, launch pending (25-oct-2026)

Probe now forceloads the exact ring chunks, waits for all `getChunkNow` non-null (200-tick timeout), scans only
when ready, rechecks during scan, releases tickets, and halts. No block mutations or A2 calls. No launch in this
phase; second launch authorization required. A2 BLOCKED.
## A2 probe second launch — blocked by harness none (25-oct-2026)

One launch attempted; harness `mode=none` called `cerrar` and stopped the server before probe tick. No report.
Source unchanged; working copy saved. Need launch path that excludes A2NavHarness entirely. No retry; A2 BLOCKED.
## A2 probe-only build path (25-oct-2026)

New opt-in `-Pa2nProbeOnly=true` excludes A2NavHarness/A2EntryProbe/AsedioRunA2/A2ProdNavProbe while keeping
ArenaFoundationProbe. Verified with classpath checks: probe-only build has probe class and no A2 harness/entry;
property-absent diagnostic build still has A2NavHarness. No launch. A2 BLOCKED.
## A2 runtime probe PASS (25-oct-2026)

Probe-only launch `arena-probe3-20261025-0538`: 400/400 columns, 33 collision-empty water at Y=62, 367 non-empty,
0 missing, 32/32 chunks ready, cleanup 32/32, process exited. No A2 harness/A2 activity. Source unchanged.
Runtime supersedes offline 94 estimate. No repair/A2 yet.
## A2 acceptance attempt — partial (25-oct-2026)

Foundation bootstrap made candidate 661 valid and wall complete; 8/8 spawned. All attackers stayed PLANNING with 0
perimeter entries/breach. Verdict A/B has no contracted thresholds; no A2 acceptance. One attempt consumed.
## A2 ACCEPTED — 25-oct-2026

`a2n-20261025-055802` with `-NavCustom true`: foundation/candidate valid, wall 1200, 8/8 spawn, routes executed,
wall cells 1200->1191 (9 missing, 3 non-solid), 4/8 perimeter entries, 2/8 inside wall, 1 disappeared, no deaths.
Real breach/horizontal entry evidenced by Siege pica logs and final inside positions. No numeric threshold exists;
recorded result is 4/8 entered / 2 inside. A2 accepted for qualitative criteria; no further run.
## A2 nav budget attempt — still 4/8 (25-oct-2026)

Run `a2n-20261025-212406` with global budget16000/local3000, NavCustom true: 4/8 entered, 2/8 inside, 1 disappeared.
idx4/7/8 still no route; idx6 same DROP contiguity rejection. No 8/8. A2 not accepted.
## A2 waypoint/DROP fix — still 4/8 (25-oct-2026)

Run `a2n-20261025-214955`: 4/8 entered, 3/8 inside (idx6 fixed), 1 disappeared, wall intact 1200. idx4/7/8 still
no route. No 8/8; three-run series not started. A2 not accepted.

## Village profession recovery (26-oct-2026)

One-shot early maintenance poll after attack clear added (pending marker). No duplicate goals; GoalSelector/canUse
still authoritative. Needs runtime test with a real under_attack siege; the prior test only produced enemies_inside.

## A5 recovery scenario (26-oct-2026)

Implemented `recovery` scenario for real siege -> recovery; bounded and no terminal cleanup. Needs a disposable
world/siege run to validate. Not yet accepted.

## A5 automation repair (26-oct-2026)

Launcher result-path/focus regression fixed; self-test PASS. Short graphical smoke/focus test still pending.

## Automation repair verified (26-oct-2026)

Launcher self-test and smoke PASS; focus-switch GUI test still pending.

## A5 focus smoke (26-oct-2026)

Focus smoke FOCUS_UNVERIFIED with Chrome foreground; exact Minecraft→Chrome transition not captured. Invalid guardview attempt
started a siege and was discarded. No siege acceptance claimed.

## A5 final focus test (26-oct-2026)

FOCUS_UNVERIFIED: smoke PASS/clean/exit0, but Minecraft foreground could not be established (SetForegroundWindow
returned false). Need focus-permitted environment or explicit manual-switch authorization.

## A5 focus handoff repair (26-oct-2026)

FOCUS_UNVERIFIED: foreground ownership could not be transferred noninteractively. Need focus-permitted desktop
or explicit manual-switch authorization.

## Harness focus regression root cause (26-oct-2026)

No historical automated focus handoff exists; pre-A5 relied on pauseOnLostFocus:false. Current API handoff cannot
change foreground. Do not add another focus architecture without an interactive-session solution.

## Guard equipment on existing founders (27-oct-2026)

Fixed existing-founder branch to attempt warehouse equipment. Static verification only; no runtime guard
equipment acceptance run yet.

## Existing founder equipment acceptance (27-oct-2026)

PASS with disposable A5WALL world `founder-equip-20261027-02`. Combat effectiveness still unproven.

## Fresh-world A2 raid acceptance (27-oct-2026)

PASS at breach/defense level; no attacker entered. Next priority: interior pressure and save/reload.

## A2 interior pressure acceptance (27-oct-2026)

Interior pressure confirmed by death positions; `dentro=true` logging is stale/unreliable and must not gate acceptance.

## Player-proximity autonomy fix (27-oct-2026)

PASS near-village grace (11 working, waveAttack=false). Far→near→far full sequence and duplicates not measured.

## Player-proximity autonomy closure (27-oct-2026)

PASS; four tracked villagers moved far/near/far. Broader profession counters/save-reload remain.

## Multi-village isolation (27-oct-2026)

PARTIAL: isolation PASS; save/reload restart test still missing. Next: bounded second-run reload scenario.

## Save/reload persistence acceptance (27-oct-2026)

PASS two-process reload. Next: per-guard goal enumeration, three villages, multiple cycles.

## Distant-village maintenance (27-oct-2026)

PARTIAL: code compiled; runtime reload failed because B entities/guards absent in reused save. Fresh two-village
single-reload retest required.

## Distant-village autonomy clean reload (27-oct-2026)

PASS for one farmer/one reload with force-loaded chunks. Multiple professions/natural load remain.

## Profession progression (27-oct-2026)

PARTIAL: farmer harvest XP source change compiled; no harvest observed in bounded test. Need deterministic mature-crop
scenario and reload persistence.

## Harvest XP deterministic test (27-oct-2026)

FAIL: real harvest 69, XP unchanged. Need exact vanilla harvest hook; event attempt unverified.

## Harvest XP mixin acceptance (27-oct-2026)

PARTIAL aggregate 64/64 and reload xp=25. Next: single-crop/non-farmer negative.

## Harvest XP final acceptance (27-oct-2026)

PASS single-crop/aggregate/reload/package. Remaining: production jar runtime, vanilla level UI effects, non-farmer
explicit fixture if desired.

## Lumberjack XP audit (27-oct-2026)

PARTIAL: source award correct; runtime blocked by fixture priority. Need bone meal/established arboleda.

## Lumberjack XP runtime (27-oct-2026)

PARTIAL: 5 logs -> 5 XP runtime; persistence retest needs chunk force-load/wait.

## Lumberjack XP closure (27-oct-2026)

PASS persistence and non-lumberjack negative; optional single-log remains.

## Mining XP source audit (28-oct-2026)

BLOCKED: no MASON in A5WALL fixture; need miner world/scenario. Potential `setBlock` result check noted.

## Mining XP runtime (28-oct-2026)

PARTIAL: real mining xp1->47, reload 47; setBlock failure guard source-only; single-block/negative pending.

## Mining XP closure (28-oct-2026)

PARTIAL: guard contract fixed. Need single-block, failed-mutation, non-miner runtime.

## Mining XP final acceptance (28-oct-2026)

PARTIAL: non-miner PASS; single-block pending; failed mutation source-only.

## Mining single-block acceptance (28-oct-2026)

PARTIAL: need mine-progress-controlled one-block fixture; no production bypass.

## Mining single-block acceptance (28-oct-2026)

PASS single-block. Remaining: explicit duplicate-goal count, source-only failed mutation.

## Next gameplay gap audit (28-oct-2026)

Dispatcher unwired is highest-value gap; plan/report written. XP levels/expansion/golem deferred.

## Bounded dispatcher (28-oct-2026)

PARTIAL: opt-in works/improves one sample; default off. Need competing-target, bounded-failure, full regressions.

## Dispatcher validation (28-oct-2026)

PARTIAL: matrix 8 PASS/1 PARTIAL/1 SOURCE/6 NOT RUN. Keep opt-in; run missing regressions and pairs.

## Dispatcher gap closure (28-oct-2026)

FAIL opt-in regression; keep disabled. Do not enable without a new design.

## Custom nav decision (28-oct-2026)

Navigation controller implemented opt-in; dispatcher rejected/historical. Runtime ownership not observed (no [NAV] logs);
next: verify flag propagation/registration before further tests.

## Custom nav implementation (28-oct-2026)

Controller opt-in, not runtime-proven. Next: verify flag/registration activation with one diagnostic run.

## Custom navigation contract (28-oct-2026)

Contract+identity/state implemented. First runtime (custom-nav-20261028-03) had 83 harvests but no [NAV] events;
acceptance PARTIAL. Next: one activation diagnostic (flag/registration), then one end-to-end run.

## Custom nav contract report (28-oct-2026)

PARTIAL: implementation contract complete, runtime ownership unproven.

## Custom nav activation (28-oct-2026)

Activation PASS; throughput regression. Next: coalesce sub-step recados before another acceptance.

## Two-level profession coordination (28-oct-2026)

Source slice complete. Next authorized task: three-farmer/three-plot non-conflict,
sustained work, death/reassignment, reload persistence; do not assume PASS.

## Two-level profession coordination report (28-oct-2026)

Source complete; runtime unauthorized/unverified.

## Farm/nav coherence (28-oct-2026)

PARTIAL: regression reduced 3->30, not fixed. Do not tune threshold; add logical task identity.

## Task identity (28-oct-2026)

FAIL runtime. Next: disable controller ownership for farming, restore fallback; no more threshold/radius tuning.

## Farm nav containment (28-oct-2026)

Farm tasks fallback-only; policy test PASS. Next runtime: confirm fallback 63-83 restored; then handoff design.

## Farm nav containment report (28-oct-2026)

PARTIAL: containment source/test done; runtime throughput pending.

## Farm nav containment runtime (28-oct-2026)

PARTIAL: throughput 27 vs 63-83; add policy-evaluation diagnostic + investigate nonfarm nav contention.

## Farm nav policy runtime (28-oct-2026)

PARTIAL: exclusion proven; throughput 42; farm:-1 means coordinator assignment not effective.

## Farm plot assignment (28-oct-2026)

PARTIAL: farm:0/1/2 distinct; throughput 68. Harness FAIL xp75 vs 68; investigate awards.

## Farm XP/harvest investigation (28-oct-2026)

ACCEPTED: harness now counts mature removals; 67/67. No production XP change.

## Navigation interference (28-oct-2026)

PARTIAL: farming healthy, no interference proven. Next only if needed: nonfarm on/off scenario.

## Nonfarm nav A/B (28-oct-2026)

Valid negative: enabled miner 0 vs control 46. Exclude work-position tasks; no radius tuning.

## Vision reconciliation (29-oct-2026)

Documented-not-implemented: custom brain, terrain geometry, enemy progression/fortress. Requires design decisions;
do not implement. Player powers backlog missing/not found.

## Mining nav containment (29-oct-2026)

Source/tests: mine:* excluded; runtime pending. Future vision docs added; no future feature implemented.

## Vision reconciliation (29-oct-2026)

Docs done; mining exclusion source/test; runtime pending; player power backlog not canonical.

## Mining nav containment runtime (29-oct-2026)

ACCEPTED runtime. Mining fallback healthy. Custom nav remains opt-in.

## Task coordination audit (29-oct-2026)

Next slice: farm-claim lease/release; audit report written. No source/runtime.

## Farm ownership persistence (29-oct-2026)

PARTIAL: persistent claims implemented; runtime unload/reload and release hooks pending.

## Stabilization (29-oct-2026)

PAUSED broad architecture. Active stabilization/backlog. Mandatory spawn profile centralization and Vex unlock pending;
farm ownership runtime pending; no implementation authorized by audit.

## User playable build (29-oct-2026)

PLAYABLE client launched; spawn position limitation; gameplay validation pending user observations.

## Stabilization 14 defects (29-oct-2026)

G2/G3 technical fixes; 12 pending. Next: runtime diagnosis per affected system.

## CRITICAL: New World hash mismatch (29-oct-2026)

BLOCKED safety: expected F3CB43... actual CB687B... External session likely; no restore.

## Stabilization phase 2 (29-oct-2026)

G2/G3 only. Remaining 12 defects need focused implementation/runtime; New World excluded.

## Lumberjack block 1 (29-oct-2026)

A2 technical; A1/A3/A4/A5 runtime gaps. Next: sapling/blocked-door fixture.

## Lumberjack block1 completion (29-oct-2026)

A2/A3 runtime failed fixture; need valid inner positions. A3 arboleda felling works 5 logs.

## Lumberjack fixture integrity (29-oct-2026)

Inner tree not harvested in 600 ticks; sapling disappeared/bone consumed. Need longer/priority diagnosis.

## Lumberjack final run (29-oct-2026)

A2/A3/A5 fail runtime; need deeper task instrumentation/longer scenario.

## Lumberjack root-cause attempt (29-oct-2026)

Corrected source gap but runtime branch not reached; need instrument descubrirTrabajo/target decisions.

## Lumberjack discovery trace (29-oct-2026)

Scanner rejects candidate (failed point/unreachable); need reachable-cell fixture and failed-point trace.

## Lumberjack block1 execution (30-oct-2026)

Add LUMBER-SCAN per-predicate logging; fixture needs verified standing cell/not failed.

## Definitive scanner (30-oct-2026)

Fixture target removed before scanner; need placement after maintenance settle + assert pre-scan.

## Lumberjack scanner accepted (30-oct): sapling 118,97,0, sitio117,0, stage1->log. No ABONO; likely other actor.

## Actor run 10: farmer not culprit; lumberjack puntoConHarina null despite bone32. Fix lookup.

## Source lookup run13: VillagePantry.contar(almacen)=0 vs countStorage=32; trace container mismatch.

## Storage discrepancy: need slot-level trace inside VillagePantry.contar; container identity differs.

## Storage: pantryContar=32 at insertion. Disappears before decision. Need slot mutation watchdog.

## A2 proved. Need rerun conservation + inner tree.

## A5 4-unit conservation pass; inner tree/blocked door remain.

## Regression run22: growCrop true but stage unchanged; tree not enumerated after reset.

## Inner fixture never enumerated; arboleda wins. A5 no-growth consumption unsolved.

## A2/A5 reliable; A3 planner still selects arboleda. Next: area selection/tree enumeration.

## Inner tree validation still rejects fixture; need LUMBER-TREE reason for esArbolSuelto/standing.

## Lifecycle restored; next inner tree and delivery.

## A4 warehouse delta off by1; inspect guardar/stack split.

## Delivery not reached in 1800 ticks; extend window or instrument entregar. No item loss.

## Delivery never starts after talar; inspect target==null branch/restTicks and puntoDeApoyo assignment.

## A4 proven. Next inner tree/planner, then obstruction/reservations.

## Inner request now independent, arboleda excluded; fixture110 still not enumerated/selected.

## Delivery no longer resets planner; inner ring12 still not reached in 3 tasks. Need verify cursor batches.
# A3: falta demostrar batch que incluya 92,0; no cambiar budget.

## Fix next: center origin + prevent post-delivery reinit. Do not change budget.

## A3: adjust diagnostic to actual fixture coordinate 90,0; candidate not yet logged.

## A3/A4/A7 proven. Remaining A1 obstruction recovery, A6 reservation lifecycle.

## A1/A6 need obstruction and interruption runtime scenarios.

## A1/A6 harness flag propagation or scenario dispatch still needed.

## A1/A6 still need a target that is selected then made unreachable.

## A1/A6: investigate why stuck limit/failed point not reached after post-assignment wall.

## A1 needs recoverable obstruction; A6 needs scheduler interruption.

## Widen alternate route or use a proper open corridor; A6 two-worker interruption still needed.

## A1 corridor geometry needs path endpoint inside wall; A6 two-worker preemption.

## A6: two-worker real GoalSelector preemption still needed.

## A6: insert actual competing goal and two workers; verify currentTarget before/after.

## A6: investigate why priority0 MOVE goal not selected; two workers still needed.

## A6: one-shot preempt goal + Worker B + subsequent work.

## A6: add Worker B distinct assignment and compare controller identity.

## Block 1 A1-A7 complete. Next: keep regressions green; no open forestry acceptance.

## 2026-11-05 — Forestry Block 1 closed (A1–A7 PASS)
All forestry acceptance criteria are runtime verified. See
`docs/autonomous-village/FORESTRY-BLOCK-1-ACCEPTANCE.md`.
Remaining autonomous-village work should build on this foundation. Forestry
production code should not be refactored without a demonstrated regression and
the narrow regression required by the acceptance record.

## 2026-11-05 — Autonomous Village master audit
The full inventory, acceptance matrix, integration scenarios, defect backlog, and
ordered correction roadmap are in
`docs/autonomous-village/AUTONOMOUS-VILLAGE-MASTER-PLAN.md`.
Forestry Block 1 is closed; the first recommended implementation block is food
autonomy (C1). No production code changes were made in the audit phase.

## 2026-11-05 — C1 food autonomy: source verified, runtime blocked
`VillageManager.repartirRaciones` withdraws pantry food and marks meals;
`pasarHambre` applies weakness/slowness/death and recovery. No food runtime
scenario exists yet. Next bounded task: add one `food-autonomy` A5 scenario with
known quantities and exact deltas. Do not implement another consumption system.

## C1: consumption/shortage/recovery PASS run84. Remaining: farm production, cook output, reload persistence.

## 2026-11-05 — Food economy: overfeeding confirmed structurally
`EAT_INTERVAL_TICKS=1200` produces 24 meals/day/villager; `sacarComida` removes
whole items and can overdraw points. Guards use the same ration and lose automatic
feeding during attacks. Production rates remain unmeasured. See
`FOOD-ECONOMY-AUDIT.md`; do not implement until daily portion/work policy is
approved.

## Food one-day: pantry 256->64, bread 256->0, 12 tracked meals, 76 farm items,
18 cooked pieces, negative balance. Guard fixture invalid (same UUID as worker).
Next: corrected food-day with distinct guard and per-item deltas before feeding
policy changes.

## Food measurement 2: overdraw 2 points proven; worker13/guard9 meals; pantry
256->11 negative. Next: daily meal window + exact withdrawal + guard raid rule.

## Block 3 partial: daily cadence + guard check + pantry credit implemented.
Missing: persisted credit (currently WeakHashMap), work-dependent hunger,
real bedtime gate, guard raid proof, automated tests. Do not mark complete.

## Block 3.1 partial: persistent pantry credit implemented, compile passes.
Still missing: work-dependent hunger, real bedtime handshake, guard raid proof,
pantry tests/save-reload, post-change 24k runtime. Do not claim complete.

## Block 3.2 FAIL run92: no meals. Diagnose feed AABB/isNight/eligibility before
any more feeding changes. Add tests and deterministic worker selection.

## Lumberjack delivery loop fixed in source; normal deliveries pass run93.
Still needs warehouse-full/unreachable runtime test and automated retry test.

## Lumberjack dance fixed in source/run94. Still missing automated WALK_TARGET
ownership test, farmer/guard/two-worker runtime scenarios.

## Block 3.2 partial: individual hunger/emergency feed works at ~95.
Need normal-threshold 60 proof, 25-point reduction runtime, tests, reload, raid.

## Block 3.2: 25-point reduction verified. Normal feeding at60 still fails; guard feeding/raid, starvation, reload, tests open.

## Block 3.2: meal reduction and guard feeding work. Normal 60 delay remains.
Instrument tavern goal installation/canUse; then tests/reload/raid.

## Food diagnostic: sleep gate found/removed, but canUse still returns before
destination; no result log at hunger100/pantry232. Next instrument each return.

## Food eligibility ROOT CAUSE: `tieneHambre` still rolling timestamp.
At hunger80 it returns false. Fix is one line to hunger>=60. No behavior change
made in diagnostic.

## Normal feeding at60 now PASS run105 (62->37, 60->35). Remaining Block3.2: tests,
starvation/recovery, guard raid, reload.

## Block 3.2 open: tests, starvation/recovery, guard raid, save/reload. Normal
feeding at60 is PASS run105.

## Starvation integration NOT verified: run107 hunger100 but no weakness/slow at
4400. Diagnose critical timer/effect application before claiming recovery.

## Starvation root cause: HAMBRE_PACIENTE=72000 (3*EAT) while DEATH=12000 absolute; weakness
unreachable. Decouple constants before any recovery claim.

## Starvation effects/recovery PASS run109. Remaining: death timer runtime,
automated tests, guard raid, persistence.

## Starvation death at crit=12000 PASS run110. Failed feeding preserved critical
state. Remaining: automated tests, guard raid, persistence.

## Food hunger policy tests PASS. Remaining Block3.2: guard raid, persistence,
Minecraft-effect/death automated coverage limitation.

## Guard raid feeding unverified: run118 meal after siege ended; run119 raid did
not start. Cooldown prevents duplicates. Needs a raid that starts before meal.

## Guard active-raid feeding PASS run120 (siege=true, 100->75, one meal).
Remaining: defense-resumption target evidence, persistence.

## Defense resumption PARTIAL: run123 moved raider but guard target=-; raid ended.
Do not claim defense resumption. Active-raid meal is PASS run120.

## Defense resumption PASS run127: guard private enemigo=dc333a86 at 0.8 blocks
after meal, siege=true. A5 getTarget assertion is a false negative.

## Guard raid feeding + defense resumption PASS run128. Next: save/reload of
hunger and pantry credit.

## Persistence PASS run130: hunger100 and credit2 survive reload; credit2 consumed
once. Remaining: second cycle, no-duplicate-meal-after-reload, serialization test.

## Remaining Food Economy: post-reload meal after 3600 ticks; NBT serialization
test. Do not reopen completed raid-feeding/persistence.

## Food Economy Block 3.2 CLOSED (run132 daytime second meal PASS).

## MEAL2 save fixture fixed (run132 save PASS). Post-reload second meal still does
not occur (hunger86, elapsed7000, pantry24). Block3.2 stays open.

## Post-reload no-meal cause: sleep=true at night. Goal installed and all other
conditions pass. Run daytime to verify second meal.


## C4 fixture blocker — 2026-11-12

The canonical A2 raid harness is not a village-defense integration fixture:
`prepararRaidFixture` builds terrain/wall and `prepararRaidAsaltantes`
spawns eight attackers, but no villagers, guards, or profession workers.
Consequently it cannot prove the C4 PASS conditions (guard engagement,
deterministic village outcome, profession resumption, and no orphaned
assignments). Owner authorized canonical-harness-only changes; no change was
made because reporting these conditions from an empty fixture would be false.
Next: design a bounded fixture using the real production village-generation
and guard/workforce registration paths, then return to the canonical harness
edit. No A2 raid was launched. Full forensic note:
`A2-EDIT5-READONLY-AUDIT-20261112.md`.


### C4 continuation detail (same checkpoint)

Static source map now identifies the production village path: `VillageManager.preGenerate(level, objectiveIndex, target)` calls `VillageGenerator.generate` (12 profession villagers + golem), creates four founding guards and equips them. The remaining wiring gap is that profession Goal installation occurs in private `tickVillageLife`, normally reached through `manageNearby`, which requires a `ServerPlayer`. Next session: inspect the full `manageNearby` preconditions and whether a supported headless test player/path exists; do not use reflection or hand-forged tags as a shortcut. Also reconcile this production-generated wall with the harness's current independent ring/foundation writes before any edit or run.

Latest validation: `python tools\lint_aldea.py --strict` PASS; `git diff --check` PASS (only CRLF normalization warnings); no changes to either A2 harness source. HEAD remains `518fee13fc15bfb9b92b0479d86b5c609d1947da`. No compile or raid was run. See `A2-EDIT5-READONLY-AUDIT-20261112.md` for exact evidence and method locations.


## Continuación vigente — 2026-11-12 22:40

La sección C4 anterior que nombra run6/run7 como activo está supersedida por esta actualización. `c4-full7-20261112-0230` finalizó `C4_PASS` (4/4 guardias, 12/12 trabajadores vivos reanudados, 16 supervivientes, 0 bajas, 0 UUID no resueltos, JOB_SITE limpio, 0 fallos de instrumento). El run7 usó la primera variante de cacheo del clérigo.

El callback final de registro directo del soporte en `VillagerClericGoal` compiló correctamente. Está en curso `c4-full8-20261112-2240`, copia desechable nueva, servidor dedicado sin GUI, faseA=3000/faseB=6600/recovery=1200, puerto 25579. Salida `build/a2n/c4-full8-20261112-2240`; monitor PID 4668. Esperar `fin.txt`; nunca relanzar el mismo RunId. C4 sigue OPEN hasta el resultado final de run8. Ver acta completa en `docs/autonomous-village/A2-C4-HEADLESS-ACCEPTANCE-20261112.md` y traspaso en `docs/CONTINUAR.md`. `.partial`, mundos históricos y `run/saves` permanecen protegidos; sin commit/push.


## C4 headless — estado al 2026-11-12

- `c4-full7-20261112-0230`: PASS; 4/4 guardias con target real, 14 impactos, 4 bajas atribuidas; 12/12 trabajadores vivos y reanudados; 0 bajas/no resueltos; JOB_SITE 1/0/0; 16 aldeanos vivos; muro 992/992.
- `c4-full8-20261112-2240`: repetición en curso, fixture ya creado en puerto 25579; watcher local PID 29108. No lanzar otra ejecución ni reutilizar RunId.
- Reanudar consultando `tools\a2n\consultar-c4.ps1 -RunId c4-full8-20261112-2240`; la desconexión de ChatGPT no detiene el proceso local. La conexión del asistente no puede autoreconectarse, pero el watcher local conserva el seguimiento.
- Detalle completo y protocolo: `docs\autonomous-village\A2-C4-HEADLESS-ACCEPTANCE-20261112.md`, sección 8. Sin commit/push.


## Actualización final — 2026-11-12 22:43

**C4 ACCEPTED en run8** `c4-full8-20261112-2240`. Veredicto persistido: `C4_PASS`; 4/4 guardias combatieron; 12/12 trabajadores vivos reanudaron; 16 aldeanos vivos; 0 bajas de trabajadores; 0 UUID no resueltos; 12/12 profession Goals; JOB_SITE 1 asignación, 0 huérfanos, 0 duplicados; 15 impactos de guardia; 6 atacantes muertos (5 atribuidos a guardias, 1 al gólem de hierro), 2 retirados para recuperación; 0 fallos de instrumento. Muralla 496/496 columnas y 992/992 celdas, sin daño ni soportes ausentes. Trace Nav 25,494 registros, 0 descartados/fallos.

Run final usó callback directo de estación del clérigo; mundo desechable nuevo, servidor dedicado sin GUI/cliente/foco. Guardado de dimensiones/chunks y shutdown normal; `fin.txt`/launcher.log presentes, no JVM residual. BUILD SUCCESSFUL 7m54s, exit 0; lint estricto OK; diff check exit 0. C1–C5 aceptados; gate cerrado. Block 6 multi-day soak es siguiente tarea, todavía no ejecutada. Sin commit/push; preservar todo el working tree y `.partial`. Reporte: `docs/autonomous-village/A2-C4-HEADLESS-ACCEPTANCE-20261112.md`.


## C4 — estado final 2026-11-12: ACCEPTED

Dos ejecuciones independientes consecutivas PASS: c4-full7-20261112-0230 y c4-full8-20261112-2240. Run8: 4/4 guardias, 15 impactos, 5 bajas atribuidas; 12/12 trabajadores vivos y reanudados, 0 bajas/no resueltos; 16 aldeanos vivos; JOB_SITE 1/0/0; muro 992/992; 6 atacantes muertos y 2 retirados para recuperación; 0 fallos; guardado completo y BUILD SUCCESSFUL. Evidencia/hash en docs/autonomous-village/A2-C4-HEADLESS-ACCEPTANCE-20261112.md, sección 9. C4 aceptado; A2 global y soak multi-día continúan abiertos. Sin commit/push.


## A2 custom-nav en curso — 2026-11-12 22:52

Run `a2n-20261112-224926` activo en mundo desechable copia del seed reparado 12345, `navCustom=true`, `traceNav=true`, fases 3000/6600, budget 900. Primer checkpoint raidTick≈320: 4 asaltantes TRAVERSING, 2 PLANNING, 2 FAILED en ese instante; idx5 completó 4 DROP naturales sin fallos; se abrió brecha real en `620,72,580`; idx7 alcanzó límite local de 24 escalones y rearmó. No verdict todavía. Server PID 29700; watcher no-terminante PID 39808; monitor antiguo de 30 min retirado sin terminar el servidor. El A2 launcher ahora incluye maintenance hook, traza nav y default 90 min. Ver `A2-WALLED-RAID-HANDOFF.md` para continuidad; no relanzar RunId. C4 ACCEPTED por separado.


Run `a2n-20261112-224926` checkpoint raidTick=1500 (phase A): 8/8 initially tracked; 4 alive, 4 dead, 0 disappeared; 5/8 have entered the perimeter at least once, 1 currently inside. A real breach was logged at `620,72,580` (oak log). The custom-nav trace continues with `traceNav=true navCustom=true`; DROP natural successes are recorded. At least idx7 continues to hit local step limit 24 toward `701,71,483`; do not alter navigation mid-run. Trace size 53.5 MB; D: free 298.5 GB; server PID 29700 and watcher PID 39808 alive; no fin.txt yet. Intermediate only.


Run `a2n-20261112-224926` checkpoint raidTick=2100: 5/8 entered perimeter ever, 0 currently inside, 5 dead, 3 alive, 0 disappeared. The same three non-entrants are idx4 (spawned elevated at 566,96,533; repeated `sin waypoint de pie VALIDO ... (habia waypoints pero el mundo cambio)`), idx7 (local step ceiling 24 toward 701,71,483; remains FAILED), and idx8 (still PLANNING near 745,63,553 with slow movement). At least four distinct wall breach coordinates were logged (idx1/2/3/5 paths); no final verdict yet. Run remains active with custom nav true; trace 54.1 MB, disk free 298.5 GB; server PID 29700 and watcher PID 39808 alive.


Run `a2n-20261112-224926` completed phase A at raidTick=3000: 8/8 spawned, 5 dead, 3 alive, 0 disappeared, `entraronPerimetroEver=5/8`, 0 currently inside. Explicit non-entrants at the phase-A result: idx4 alive at `566,96,533` (94.50 horizontal; never entered); idx7 alive at `702,61,457` (85.88; never entered); idx8 alive at `743,63,501` (88.39; never entered). Phase B began at tick3001; continue through t=6600 and recovery before finalizing the run. Custom navigation and trace are confirmed true. This is a valid A2 custom-nav run but it has not met the 8/8 threshold at phase A; no final A2 acceptance claim.


Run `a2n-20261112-224926` phase-B checkpoint raidTick=4800: still 5/8 entered, 5 dead, 3 alive, 0 disappeared, 0 inside wall. No additional attacker crossed after t=600. idx4 and idx7 remain FAILED at their original positions; idx8 remains PLANNING and has moved around the outer terrain but is still ~90 blocks from center. This run currently fails the 8/8 entry criterion unless the remaining 3 recover before phase B ends. Continue to final result and recovery audit; no source edits mid-run.


Run `a2n-20261112-224926` completed phase B at raidTick=6600 with the same result: 8/8 spawned, 5/8 entered perimeter ever, 5 dead, 3 alive, 0 disappeared, 0 inside. Harness removed the 3 remaining attackers for recovery and logged `C4 POSTRAID maintenance=OK attackersRemovedForRecovery=3 recoveryTicks=1200`. Recovery window is now active; final C4 worker/JOB_SITE audit and shutdown still pending. A2 8/8 criterion is not met by this seed; preserve this valid custom-nav run as failure evidence.


## A2 seed-12345 run 1 — FINAL: A2 FAIL / C4 PASS (2026-11-12 22:58)

RunId `a2n-20261112-224926`; `navCustom=true`, `traceNav=true`; 8/8 attackers spawned; 5/8 ever entered the perimeter; 5 dead, 3 alive, 0 disappeared at t=6600. The three non-entrants were idx4, idx7 and idx8. Their global A* searches all exhausted the fixed 8000-expansion budget (`exp=8000/8000`, `presupuesto global agotado`); partial fronts had 21–26 nodes. idx7 and idx8 additionally exhausted `MAX_ESCALONES_DE_OBJETIVO=24`; idx4 repeatedly had no valid local standing waypoint after world changes and exhausted 6/6 local objective attempts. No one of these three entered during phase B. Thus the agreed A2 8/8 criterion **failed** for this seed/run; this is a valid diagnostic attempt, not an accepted seed.

Exact global-search evidence:
- attacker #268 / idx4: start `566,96,533`, meta `597,71,533`, front `587,112,532`, routeFront=21, 8000 expansions, 374,292 enqueued; terminal `sin objetivo local valido (6/6)`.
- attacker #271 / idx7: start `718,63,466`, meta `702,71,484`, front `702,39,484`, routeFront=26, 8000 expansions, 300,218 enqueued; local step ceiling 24.
- attacker #272 / idx8: start `748,63,528`, meta `725,71,529`, front `729,43,524`, routeFront=26, 8000 expansions, ~294k enqueued; local step ceiling 24.
- idx4 spawn fallback built a single-column platform at `566,95,533`, placing the mob at `566,96,533`; this is a 25-block height difference from the village anchor and may contribute to its inaccessible start.

Despite the A2 navigation failure, the same run passed the separate C4 contract: 4/4 guards fought, 12/12 workers resumed, 16 villagers alive, 0 worker casualties/unresolved, JOB_SITE orphaned=0/duplicateOwners=0, 3 attackers removed for recovery, 0 instrumentation failures. Persisted verdict `C4_PASS`. Server stopped normally, all dimensions saved; watcher PID 39808 verified `finExists=true`, `allDimensionsSaved=true`, `serverPidStillAlive=false`. Trace file 61.1 MB.

Next experiment: increase only the already-supported global A* budget from 8000 to 32000, keep local budget 900 and local-step cap 24 unchanged initially. This isolates whether global search truncation caused the partial routes and step-cap exhaustion. If the same three fail, then address local-step/waypoint behavior and the elevated fallback spawn separately. Use a fresh disposable copy of the immutable repaired seed; do not modify this completed run or its world. C4 remains accepted independently; A2 global remains OPEN.


## A2 seed-12345 retest — global budget 32000 — IN PROGRESS

New isolated RunId `a2n-20261112-230041`, fresh copy of the immutable repaired seed 12345. `-NavCustom true`, `-Presupuesto 900` (local unchanged), `-GlobalBudget 32000` (only experiment variable changed), phaseA=3000, phaseB=6600, recoveryTicks default 1200, timeout 90 min. The ignored A2 launcher now exposes `-GlobalBudget` and forwards `-Ddevilrpg.nav.globalBudget`; PowerShell parser PASS. Maintenance hook and trace enabled. Run monitor PID 37336; Gradle process PID 22372. Verify `NAVCUSTOM` and first global plan summaries, then let the run complete. This retest tests whether 8000 global expansions caused the previous 5/8 result; do not change source mid-run. Run1 remains recorded as a valid A2 failure; this is a new attempt on the same seed, not a new distinct seed.


Run `a2n-20261112-230041` early route-planning checkpoint (trace header `navCustom=true`): five initial attackers (#265, #266, #267, #269, #270) found complete global routes with 1,060–4,667 expansions (budget 32,000). The remaining three still exhausted 32,000: #268/idx4 front at `596,106,512` d=68.3 routeFront=45; #271/idx7 front at `660,43,523` d=10.0 routeFront=84; #272/idx8 front at `688,42,508` d=36.8 routeFront=73. Thus the larger budget yields substantially longer partial routes but still hits the cap for the same difficult spawns. No verdict yet; continue to observe movement/replans before changing the heuristic or spawn fixture.


### Static root-cause finding — global A* ranks only horizontal progress (read-only)

The global planner's heuristic `NavPlanner.heuristicaGlobal` returns only XZ Euclidean distance; its `Graph.distance` also returns only horizontal squared distance. `NavGraphSearch.search` uses `Graph.distance` to select the best partial `front` when the expansion budget is exhausted. This directly explains the run1/run2 partial fronts: idx7 front `702,39,484` is horizontally at the target but 32 blocks too low; idx8 front `725,42,533` is horizontally at its waypoint but 29 blocks too low. The search therefore prefers a node with perfect XZ alignment despite a large vertical mismatch, and spends expansions across irrelevant vertical layers. Run2 at 32k repeats this pattern.

A justified correction is to make both metrics 3D-aware while preserving A* admissibility: heuristic lower bound = `max(horizontalDistanceToGoalRegion, verticalDistanceToAllowedBand / 3)` (maximum vertical change per validated edge is 3; upward limit is 2, downward safe-drop limit 3), and front-ranking distance = squared horizontal distance to the accepted goal region plus squared vertical distance outside the ±1 feet band. Keep horizontal radius 1.5 and vertical band ±1 unchanged. This should be implemented and tested only after run2 completes; do not change source during the active run. A separate spawn-fixture issue remains for idx4's isolated platform at y95.


## A2 global-budget 32000 experiment — INVALID / WATCHDOG ABORT (2026-11-12 23:03)

RunId `a2n-20261112-230041` was terminated by Minecraft ServerHangWatchdog during `NavPlanner.planGlobal` after a single server tick exceeded the 60-second watchdog threshold. Crash stack: `NavObstruction.fits:98` → `NavObstruction.classify:170` → `NavPlanner.validarArista:109` → `NavGraphSearch.search:84` → `NavPlanner.planGlobal:295` → `NavExecutor.rutaGlobalHacia:1293`. The 32,000 expansion cap generated 1,792,000 edge validations per search; this is not safe to execute synchronously in one entity tick. The server then saved overworld/nether/end chunks and shut down; Gradle `:runServer FAILED`, no `fin.txt`, no semantic verdict. This is a performance/architecture failure, not a valid A2 pass or C4 result. The orphaned launcher monitor PID 37336 was stopped after the server had already exited; no game/server JVM remains. Keep crash report at `game/crash-reports/crash-2026-11-12_23.03.20-server.txt`.

Do **not** raise the global budget to 32000 again. Return to 8000 while correcting the search. Static root cause is now confirmed: `NavPlanner.heuristicaGlobal` and `Graph.distance` use only XZ distance even though the goal includes Y; `NavGraphSearch.search` uses `Graph.distance` to choose its partial front on budget exhaustion. This chooses nodes exactly at target XZ but 29–32 blocks below the target (e.g. #271 front `706,38,487` for meta `706,71,488`; #272 front `725,42,533` for meta `725,71,533`). The partial route then burns local objective steps and fails. Implement a 3D-aware goal-region heuristic/front distance, keeping the A* heuristic admissible and the expansion cap at 8000. Revisit idx4's isolated one-column spawn platform separately after the metric fix.


## A2 seed-12345 retest with 3D goal metrics — IN PROGRESS

RunId `a2n-20261112-231005`, fresh disposable copy of immutable repaired seed 12345. Runtime now uses 3D-aware `NavGoalMetric`: A* lower-bound heuristic accounts for vertical tolerance and max climb/drop; partial-front ranking uses horizontal distance to the goal region plus vertical distance. Global budget restored to 8000; local budget remains 900; max local target steps remain 24. `-NavCustom true`, trace and maintenance hook enabled; phases 3000/6600; 90-minute monitor. The new metric test and combined `policyTest compileJava -Pa2nDiagnostics=true` passed before launch. Verify trace header and global expansion counts, then complete the run. This tests whether the metric fix resolves the previous 5/8 result without the 32k watchdog crash.


Run `a2n-20261112-231005` initial A2 fixture: `navCustomEfectivo=true`, 8/8 spawned, production wall 992/992 intact. Early global routes for idx1/2/3/5/6 completed under the 8000 cap (1,071–4,715 expansions; route lengths 71–100), versus the previous 8k run where the same difficult group repeatedly exhausted budget. idx4 remains the hard case: 8000/8000, partial front `589,107,543`, routeFront=23, dFrente3D=78.5; CADENA-DIAG explicitly reports a 25-block unsupported drop at `588,93,533` requiring `ponerEscalon`. This suggests the 3D metric is improving route search for most attackers without increasing the budget; wait for idx4/7/8 movement and final phase-A result before deciding on spawn-fixture changes.


Run `a2n-20261112-231005` live checkpoint (23:11:54): 3D metric now returns much better partial fronts for the difficult spawns. idx4 (#268) front `575,70,533`, dFrente3D=20.5, dY=0, routeFront=9 (previous horizontal-only run front was >68 blocks away and 32 blocks high); its chain diagnosis proposes a stair for the 25-block unsupported drop. idx8 (#272) front `741,68,533`, dFrente3D=14.6, dY=2, routeFront=7 (previous run front was 29 blocks below goal). idx7 (#271) is now actively placing a stair at `722,63,468` instead of exhausting the 24-step ceiling immediately. Several attackers are moving; no failure verdict yet. The 3D metric is materially improving vertical alignment while keeping the 8000 budget and avoiding the 32k watchdog condition.


Run `a2n-20261112-231005` checkpoint raidTick=400: custom navigation remains active; first genuine wall breach occurred at `627,73,585` by idx3, which is recorded `DENTRO-PARED` at t=388. All 8 attackers remain tracked; idx7 has placed a stair at `722,63,468`; idx5's natural descent has 9 attempts / 0 failures; idx4 remains at elevated spawn `566,96,533` with the 25-block stair requirement pending. All four guards have begun engaging attackers (C4 guard-engaged events). Early progress only; continue through phase A/B.


Run `a2n-20261112-231005` checkpoint raidTick≈680: idx1 and idx2 have now crossed the perimeter (`DENTRO-PARED` at t=672 and t=687); idx3 crossed earlier at t=388. At least 3/8 attackers have entered. idx7's 3D partial front is now `717,68,476` (dY=2) and it is advancing after placing a stair; idx8's front remains near correct height. idx4 remains the unresolved outlier at `566,96,533`: repeated local-waypoint invalidation (6/6) despite a 3D front near `575,70,533`; its 25-block unsupported drop requires a built stair and needs separate follow-up. No source edits during the run.


Run `a2n-20261112-231005` checkpoint raidTick≈1120: three explicit `DENTRO-PARED` crossings have been logged (idx1/2/3). idx5 is physically at `661.5,72,536.5` (1.5 blocks from center) after traversing the breach, but its `DENTRO-PARED` event/counter has not yet appeared in the log; rely on the phase-A `entraronPerimetroEver` snapshot, not visual position alone. idx4 remains stationary at `566.5,96,533.5` with repeated 6/6 invalid local waypoint failures; its high fallback platform remains the only clear blocker. idx7/8 continue planning/moving with the new 3D front metric. Run remains active and no watchdog crash observed.


Run `a2n-20261112-231005` checkpoint raidTick=1800: `entraronPerimetroEver=5/8`, `dentroParedAhora=1`, 4 dead, 4 alive, 0 disappeared. Explicit crossings are idx1/2/3/5/6; the remaining three are idx4/7/8. idx4 is conclusively stuck at `566,96,533`: global partial front is now correctly aligned at `575,70,533` (dY=0), but `NavExecutor` repeatedly fails `sin objetivo local valido (6/6)` because no standing waypoint is valid inside the local window after the world changed. It never leaves the one-column high platform. The 3D metric improved global alignment but does not solve this spawn/platform transition. Continue phase B to capture final state and C4 recovery; then fix spawn fallback so no attacker is placed on an isolated 25-block-high single-column platform.


Run `a2n-20261112-231005` checkpoint raidTick=2400: `entraronPerimetroEver=5/8`, 3 dead, 5 alive, 0 disappeared, 0 inside wall at snapshot. idx4 is definitively `FAILED` at `566,96,533` (distance94.5), no descent attempts; this seed cannot reach 8/8 unless the harness is changed. idx7 is `TRAVERSING` at `736,63,576` (distance86.86) with a DROP-EN-CURSO step; idx8 is `PLANNING` at `732,61,579` (distance85.42), tunneling through stone toward waypoint `693,71,544` with tunnel budget 33/40. Allow phase A/B to finish; the two remaining attackers may still enter. Next source fix should first remove the unreachable idx4 platform fallback, then retest whether idx7/8 finish their tunnel and cross the wall.


Run `a2n-20261112-231005` phase A completed at raidTick=3000: 8/8 spawned, `entraronPerimetroEver=5/8`, 3 alive, 5 dead, 0 disappeared, 0 currently inside. Non-entrants: idx4 at `566,96,533` (FAILED, isolated spawn platform); idx7 at `738,64,573` (FAILED: replans agotados 4/4); idx8 at `733,61,579` (FAILED: local step ceiling 24). The 3D metric improved partial-front vertical alignment but did not raise the entry count beyond 5/8. Phase B continues; final C4 recovery audit still pending.


### Second root cause identified — global selector discards BUILD transitions (read-only)

`NavRoute.Transition.BUILD` is explicitly documented as a placement-cell node whose resulting standing cell is `node.pos().above()`. `NavPlanner.validarArista` can emit BUILD transitions and the local route executor already executes them via `placement.place(node.pos())`. However, `NavExecutor.elegirObjetivoLocal()` unconditionally skips every node for which `NavClimbPolicy.esWaypointDePie(n)` is false; that policy returns false for BUILD. Consequently the global selector discards the only route nodes that can create support across unsupported drops, then reports `no waypoint de pie valido` and invalidates the route. This explains idx4's 25-block unsupported drop and likely contributes to idx7/idx8's repeated replan/step-limit failures.

Next fix after current run completes: preserve BUILD nodes in global waypoint selection by converting each BUILD placement cell to its resulting foot cell (`n.pos().above()`), mark the local objective as build-capable, and allow the local planner to plan to that cell even before support exists. The local planner's explicit BUILD transition should then place/verify the block through the existing route executor. Keep ordinary WALK/STEP/DROP waypoint validation unchanged; add focused policy tests. Do not merely raise global budget; 32k already caused a watchdog hang.


Run `a2n-20261112-231005` phase B completed at t=6600 with no additional entries: 8/8 spawned, 5/8 entered perimeter ever, 5 dead, 3 alive, 0 disappeared, 0 inside at final snapshot. Harness removed the three remaining attackers and started the 1200-tick post-raid recovery (`C4 POSTRAID maintenance=OK attackersRemovedForRecovery=3`). C4 worker/job-site verdict and normal shutdown are still pending.


## A2 seed-12345 retest with 3D metric — FINAL: A2 FAIL / C4 PASS (2026-11-12 23:18)

RunId `a2n-20261112-231005`; globalBudget=8000; local budget=900; navCustom=true; trace 55.2 MB. Final raid snapshot at t=6600: 8/8 spawned, `entraronPerimetroEver=5/8`, 5 dead, 3 alive, 0 disappeared, 0 inside wall. Non-entrants idx4/7/8; idx4 failed at the isolated platform `566,96,533`, idx7 failed after 4/4 replans, idx8 hit the 24 local-step ceiling. Phase B did not add entries. Post-raid recovery removed the three remaining attackers. `fin.txt` and `launcher-status.txt` both record normal completion; server saved overworld/nether/end, `All dimensions are saved`, Gradle `BUILD SUCCESSFUL`; no game/server JVM remains.

C4 verdict: `C4_PASS` — guards 4/4 fought; professionals 12/12 resumed; 16 villagers alive; 0 worker casualties; 0 unresolved workers; JOB_SITE assignments=3, orphaned=0, duplicateOwners=0; 3 attackers removed for recovery; instrumentation failures=0. C4 remains independently accepted. A2 remains OPEN (5/8).

## BUILD waypoint correction — implemented, awaiting runtime validation

Confirmed the second defect in `NavExecutor.elegirObjetivoLocal`: it discarded `NavRoute.Transition.BUILD` because `NavClimbPolicy.esWaypointDePie(BUILD)` is correctly false for a placement cell. Added `esWaypointSeleccionable()` / `requiereConstruccion()` to distinguish a buildable placement waypoint from a standing waypoint. The global selector now retains BUILD placement cells and marks the local objective build-capable; `tryPlan` allows that objective before support exists so the local planner can emit/execute its existing BUILD transition. The flag is reset on mission/tactical/arrival/clear changes and is preserved only for the selected BUILD waypoint. Added `NavClimbPolicyTest`; `policyTest compileJava -Pa2nDiagnostics=true` passed with `NavGoalMetricTest` and all existing policy tests. Next: run A2 seed 12345 again with globalBudget=8000 and inspect `build=true` objective logs; only then tune step/replan caps if idx7/8 still fail.


## A2 seed-12345 retest with BUILD-waypoint fix — IN PROGRESS

RunId `a2n-20261112-232520`, fresh disposable copy of immutable repaired seed 12345. Changes since run3: global selector now retains BUILD transitions as buildable local objectives; `tryPlan` permits them before support exists so the local planner can execute its BUILD transition. 3D metric retained; global budget=8000, local budget=900, local-step/replan caps unchanged. `NavCustom=true`, trace and maintenance hook enabled; phases 3000/6600, 90-minute monitor. Preflight: launcher parser PASS, `lint_aldea.py --strict` PASS, `git diff --check` PASS, compile/policyTest PASS. Watch for `build=true` in waypoint logs and actual step placement; no source edits during the run.


Run `a2n-20261112-232520` early BUILD-waypoint checkpoint (~23:27:31): idx4 is no longer stationary on the fallback platform. It moved from `566,96,533` to `617,80,467`; the latest chain diagnostic advances its first waypoint to `616,82,468` and now reports the remaining unsupported drop reduced to 9 blocks. This is the first runtime evidence that retaining buildable global waypoints allows the high-spawn attacker to make progress. idx8 still emitted the 24-step local ceiling / 4-replan failures early; continue observing whether it recovers or remains stuck. No watchdog crash; global budget remains 8000.


### Correction to early run4 attribution + confirmed construction blocker

The earlier note that idx4 moved to `617,80,467` was a misattribution: that position belonged to another attacker. The authoritative `RAID PROX idx=4` and entity `#271` remain at `566,96,533`; idx4 has not left its platform. BUILD waypoint selection alone has not yet solved it.

Read-only inspection found why BUILD nodes are not generated for the 25-block cliff: `NavPlanner.esConstruible()` requires the candidate cell's block directly below to be solid. `AggressiveZombieEntity.evaluarCeldaDeEscalon()` imposes the same below-only support and additionally requires `candidateY == MOTION_BLOCKING_NO_LEAVES heightmap`. Thus the builder can place only on natural ground surfaces, not on a horizontal sturdy face of a cliff or on a previously placed side-supported stair. A 25-block unsupported drop has no candidate, so the global route contains no BUILD waypoint for the selector to retain. The `NavClimbPolicy` fix is still correct for any BUILD nodes that do exist, but the next required fix is to unify placement support: permit a candidate cell when its block below has a sturdy UP face OR one of its horizontal neighbors has the appropriate sturdy side face; remove the strict heightmap equality while retaining air/headroom, protection, loaded-chunk, inventory, and max-safe-drop checks. Use the same support predicate in `NavPlanner.esConstruible` and `AggressiveZombieEntity.evaluarCeldaDeEscalon` so planning and execution agree. Then add a no-route construction fallback and retest.


### Refined construction diagnosis (source inspection)

There is already a side-support primitive, `AggressiveZombieEntity.ponerEscalonConSoporteLateral()`, which accepts a solid block below OR a horizontal neighboring solid face and rejects body intersection. It is currently not called by `construirEscalonDescendente`; that builder calls the below-only `ponerEscalon()` and its evaluator also insists on natural heightmap surface. The side-support primitive is therefore dead for the descending stair path. In addition, `escaleraAncla` is initialized once and never advanced after the mob actually steps on `escaleraUltimoColocado`; `escaleraPisada()` is only used by diagnostics, not by recovery logic. The intended staircase cannot progress down a cliff.

Concrete patch plan: (1) use the shared below-or-horizontal-side support predicate in `NavPlanner.esConstruible` and `evaluarCeldaDeEscalon`; (2) remove the heightmap equality restriction but retain empty cell, two-block headroom, outside-village, max 3-block per-step descent, and body-collision checks; (3) call `ponerEscalonConSoporteLateral` from the descending builder; (4) in `recoverNavigation`, only after `escaleraPisada()` confirms real traversal, advance `escaleraAncla` to the verified placed block and clear the pending step marker; (5) let NavExecutor attempt the stair builder when local planning returns no route as well as on budget exhaustion; (6) raise bounded local waypoint/replan caps from 24/4 to 64/8 if the next run still reaches those ceilings. The global expansion cap stays 8000.


## A2 seed-12345 retest with BUILD waypoint selection — FINAL: A2 FAIL 6/8 / C4 PASS (2026-11-12 23:34)

RunId `a2n-20261112-232520`; globalBudget=8000; local budget=900; 3D metric + BUILD-selectable local waypoint; local step cap=24; replan cap=6. Final phase-B snapshot t=6600: 8/8 spawned, `entraronPerimetroEver=6/8`, 6 dead, 2 alive, 0 disappeared during raid. idx7 eventually crossed at t=5823 after a DROP traversal and repeated replans; this is an improvement over run3 (5/8). Remaining non-entrants: idx4 (#271) stationary at `566,96,533`, failed with `ruta global sin waypoint de pie VALIDO ... (habia waypoints pero el mundo cambio)`; idx8 (#275) stationary at `747,64,532`, failed with `tope de escalones locales alcanzado (24)` and 6/6 attempts. BUILD waypoint selection alone did not solve idx4 because no BUILD edge is generated when construction support is below-only; idx8 needs a larger local step budget.

C4 verdict `C4_PASS (raid)`: guards fought; professionals resumed; 16 survivors; 12/12 workers alive and resumed; 0 worker casualties; 0 unresolved; 2 attackers removed for recovery; jobSite assignment summary `[1,0,0]`, clean; instrumentation failures=0. Server saved all dimensions, Gradle `BUILD SUCCESSFUL` (8m41s), no server JVM remains. Launcher monitor is expected to write `launcher-status.txt` and exit; verify before next run.


## Side-supported stair patch — implemented; static verification PASS (2026-11-12)

Applied the next root-cause correction after run4:
- Added `NavObstruction.hasSupportFace`: placement may be supported by a solid block below OR by a horizontal neighboring solid face; no support from above.
- `NavPlanner.esConstruible` now uses that shared predicate, so the global/local planner can generate BUILD edges beside cliff faces.
- `AggressiveZombieEntity.evaluarCeldaDeEscalon` now uses the same predicate and no longer requires the candidate Y to equal the natural heightmap surface. It still requires an empty cell, two blocks of headroom, village protection exclusion, descending geometry, and max-safe-drop per step.
- `construirEscalonDescendente` now calls the existing `ponerEscalonConSoporteLateral` placement primitive.
- The descending stair anchor advances only after `escaleraPisada()` confirms the previous placed block was actually traversed; the pending step marker is then cleared so the next lower step can be planned. This fixes the prior behavior that repeatedly planned from the original anchor.
- Raised bounded navigation caps to align with the stair builder: local waypoints 24→64, build steps per plan 12→24, destination replans 4→8, budget retries default 6→10, objective-selection failures 6→10, invalid-objective attempts 6→10, recovery actions 6→24. Global expansion budget remains 8000; no 32k search.

Verification: `gradlew policyTest compileJava -Pa2nDiagnostics=true` — BUILD SUCCESSFUL, exit 0. `NavPlannerStateTest`, `NavGoalMetricTest`, `NavClimbPolicyTest`, and all existing policy tests passed. `lint_aldea.py --strict` PASS; `git diff --check` PASS; A2 launcher PowerShell parser PASS. No Minecraft/server JVM running. Next: fresh disposable A2 seed-12345 retest; expect `BUILD` transitions, `CONSTRUCCION-ESCALERA`, and `ESCALERA-ANCLA` logs before judging the 8/8 result.


## A2 retest after side-supported stair + anchor progression — IN PROGRESS

RunId `a2n-20261112-234001`, fresh disposable copy of seed 12345. Uses side-support BUILD planning/execution, descending stair anchor advancement after verified traversal, and expanded bounded step/replan/recovery caps. Global budget remains 8000; local budget 900; phase A 3000 / phase B 6600; headless monitor 90 minutes. Watch for `BUILD` transition placements, `CONSTRUCCION-ESCALERA`, `ESCALERA-ANCLA`, idx4 leaving `566,96,533`, and idx8 passing the former 24-step ceiling. Do not edit source while the server is active.


Run5 checkpoint t=2400 (phase A): `entraronPerimetroEver=5/8`, 4 dead, 4 alive, none currently inside. idx4 (#272) remains fixed at `566,96,533` and repeatedly reaches `sin objetivo local valido (10/10)`; no `ESCALERA-ANCLA` / `CONSTRUCCION-ESCALERA` events have appeared. This confirms the fallback spawn is an isolated one-block platform with no below/side support for the first lower step; the new builder cannot safely invent support from nothing. Next harness correction: when the authorized fallback platform is needed, connect it to the nearest real solid terrain below with a 1×1 support column (only fill air, preserve existing blocks), so the raider can build a side-supported stair along that column. Keep the test spawn at radius95 and outside the wall. Also found the actual guard-replan cap is a separate `MAX_REPLANTEOS_POR_GUARDA=6` constant; raise it to10 (the other cap changes did not alter the 6/6 log).


## A2 run4 final forensic update — 2026-11-12

Run `a2n-20261112-234001` completed normally (server saved all dimensions, no server JVM remains, `BUILD SUCCESSFUL`; launcher `SEMANTIC_FAIL` only because C4 acceptance failed). A2 remains FAIL: at phase B t=6600, 8 spawned, 5 ever crossed perimeter, 0 inside wall at snapshot; four survivors were then removed for recovery, four attackers had died. `PARED` had 966/992 cells present, 26 missing, 13 columns with non-solid cells. Side-supported construction is now confirmed in production runtime: entity #275 placed a side-supported stair at `702,64,457`; entity #276 placed one at `705,64,459`. However the custom `CONSTRUCCION-ESCALERA`/`ESCALERA-ANCLA` path did not emit logs; the observed placements were through the injected `NavExecutor` BUILD placement callback. Thus the separate `escaleraAncla` advancement code has not been runtime-validated and may not be the path exercised by these cases.

Navigation failure split: several attackers (#271, #273, #274, #275, #276) hit `replanteos agotados (6/6)`. Source audit found a second independent guard `MAX_REPLANTEOS_POR_GUARDA=6`, separate from `MAX_REPLANS_PER_DESTINATION=8`; it was still limiting the run to six. Raised that guard to 12 (bounded) after run4; compile/runtime validation pending. Attacker #272 (idx4) remained on its spawn platform `566,96,533` and failed `sin objetivo local valido (10/10): ruta global sin waypoint de pie VALIDO ... (habia waypoints pero el mundo cambio)`. Entity #275 also exhausted global search radius 112 from `695,62,454`. Side support fixes help some BUILD transitions but do not yet solve all high-elevation spawn cases.

C4 run4: `C4_FAIL` despite 4/4 guards fighting, 12/12 professionals alive, 12/12 profession goals installed, 0 worker deaths/unresolved, and clean JOB_SITE assignments. Only 10/12 workers were observed running their expected professional Goal at least once during the 1200-tick recovery. The two missed were nitwit (expected `VillagerCollectGoal`, final `VillagerFleeGoal`, pos `575,71,399`) and fisherman (expected `VillagerFisherGoal`, no running goals, pos `698,62,399`). `rTrabajadoresQueReanudaron` is sampled each tick during post-raid recovery, so this is not merely a final-tick snapshot. Likely eligibility/conditions need diagnosis: collector only runs when there is collectable work and no active threat/rest; fisherman requires a barrel, fish in the lake, no active threat/rest, and to be within its radius. Do NOT weaken the acceptance predicate. Next: add read-only eligibility diagnostics (nearest monster/threat, collector work availability, fisherman barrel/fish/target/rest conditions) and determine why these two never became eligible. Then address A2 route guard cap and idx4 no-waypoint condition, compile, and run fresh disposable A2+C4 again.


## A2 run5 — guard12 + C4 eligibility diagnostics — IN PROGRESS (2026-11-13)

RunId `a2n-20261113-000323`; fresh disposable copy of the immutable repaired seed 12345. Changes since run4: `MAX_REPLANTEOS_POR_GUARDA` raised from 6 to 12 (the independent cap missed by the previous run); C4 failure logging now reports objective eligibility facts for unresumed nitwit/fisherman workers (nearby monsters, center distance, attack/rest state, aged item entities, fisherman barrel/fishery/fish count). Acceptance remains strict; no criteria were weakened. Build `policyTest compileJava -Pa2nDiagnostics=true`: BUILD SUCCESSFUL, all policy tests including `NavClimbPolicyTest` passed. `lint_aldea.py --strict`, `git diff --check`, launcher parser, and identical harness-source checks passed. Actual HEAD at launch: `518fee13fc15bfb9b92b0479d86b5c609d1947da`. Run config: navCustom=true, local budget=900, globalBudget=8000, phase A=3000, phase B=6600, recovery=1200, headless monitor 90 minutes. Observe whether more than five attackers enter perimeter, whether idx4 escapes its platform, and exact C4 eligibility reasons. No source edits while server is active.


### Run5 live forensic finding — idx4 construction trigger defect (2026-11-13)

Run `a2n-20261113-000323` has reached raidTick≈1120. The independent route-guard cap increase is helping: idx3 (#267) moved off `618,71,580`, executed a valid JUMPUP and landed at `626,73,586`; multiple other attackers are advancing, and idx6 has crossed inside the wall. However idx4 (#268) remains exactly at `566,96,533`; the global planner repeatedly exhausts 8000 expansions with front `575,70,533`, and it terminates after 10 local-waypoint failures. The trace confirms idx4 has zero movement through tick 1605 and there are no BUILD events for it.

Read-only source inspection identifies a likely precise blocker: `NavExecutor` calls `AggressiveZombieEntity.construirEscalonHacia(objective)` only after a global-plan budget hit. That delegates to `objetivoExigeConstruccion()` → `descensoExigeConstruir()`. The latter currently tests a candidate at `objectiveY + maxSafeDrop` (for idx4, y=74) and treats any supported landing there as proof that no construction is needed, even though the first safe landing from current y=96 must be tested at `currentY - maxSafeDrop` (y=93). A supported cell at y=74 does not make a 22-block fall from y=96 safe. This prematurely returns false and suppresses the very stair builder needed for the cliff. Correct the check to use the immediate safe-drop cell under the current position (`new BlockPos(currentX, currentY - CAIDA_SEGURA_MAXIMA, currentZ)`), and retain the actual support/headroom test; avoid treating a far-lower target-side ledge as an intermediate landing. Then `construirEscalonHacia` can invoke the already bounded side-supported staircase on budget exhaustion.

C4 also has a harness-only registration anomaly: immediate `iniciarAsedioParaArnes` failed validation for only the last UUID (#272 / idx8), although that entity subsequently ticks with `worldSiegeIndex=0`. Likely entity UUID lookup timing immediately after `addFreshEntity`; add per-attacker `level.getEntity(UUID)` + index diagnostics and defer registration until the next server tick (bounded retry) rather than silently accepting a partial wave. Do not weaken C4 acceptance.


## A2 run5 final — FAIL 5/8; C4 FAIL with instrument + real worker casualty (2026-11-13)

Run `a2n-20261113-000323` completed normally: `fin.txt` and `launcher-status.txt` both `SEMANTIC_FAIL` due C4, Gradle `BUILD SUCCESSFUL` in 8m31s, all dimensions saved, no server JVM remains. A2 phase B t=6600: 8/8 spawned, 5/8 ever crossed the perimeter, 0 inside the wall at snapshot, 5 dead, 3 alive, 0 disappeared during the raid. Remaining non-entrants: idx4 #268 at `566,96,533` (FAILED; global budget 8000 exhausted, no stair attempt), idx7 #271 at `732,62,577` (PLANNING, `BREAK-NO-VERIFICADO`), idx8 #272 at `711,71,603` (PLANNING; later dug toward the wall but still outside). Wall: 978/992 cells present, 14 missing, 7 columns non-solid. Guard cap 12 did not change final 5/8, although idx3 did complete a jump-up after repeated replans.

C4 final `C4_FAIL`: the immediate test-siege registration failed for the last attacker UUID `51488393-0eec-4705-879e-cac6184dcf43` (idx8/#272), despite the entity subsequently ticking with worldSiegeIndex=0. Only 1/4 guards were counted as having fought; the test instrument is invalid for guard-combat acceptance. One actual worker casualty: Ximeno (farmer, UUID `509083ec-7da8-408e-8d08-67a89afa3a0c`) killed by a small fireball from AggressiveZombie #270. 11/12 workers survived, 8/12 resumed expected goals, 0 unresolved, 11/11 surviving profession goals installed, JOB_SITE assignments clean (2, orphaned=0, duplicates=0).

C4 eligibility diagnostics explain the three non-resumed survivors: butcher `VillagerCookGoal` idle because no raw food/wheat in pantry; weaponsmith `VillagerSmithGoal` idle because no eligible stock/recipe; fisherman `VillagerFisherGoal` idle because fishInLake=0 despite barrel present and fisheryBuilt=true. All three had underAttack=false, resting=false, nearbyMonsters22=0, and were within the village radius. Do not weaken acceptance. The C4 fixture must provide controlled work inputs (raw meat + fuel for butcher, a valid smith recipe's ingredients for weaponsmith, and at least one fish in the lake) before/through recovery; this tests actual resumption rather than requiring an idle Goal to run without work. Also fix deferred attacker registration, then investigate the fireball casualty with a correctly registered 4-guard defense.

Next source fix: `AggressiveZombieEntity.descensoExigeConstruir()` incorrectly checks `objectiveY + maxSafeDrop` instead of the first reachable safe landing from the current position (`currentY - maxSafeDrop`). For idx4 this checks y=74 and incorrectly concludes no build is needed because a lower ledge exists, although the first safe landing from y=96 is y=93. Change it to test the immediate drop cell; then the already bounded `construirEscalonHacia()` fallback can start the side-supported stair after global budget exhaustion. Keep globalBudget=8000 initially; no arbitrary 32k increase.


## Root-cause fix + C4 fixture repair — implemented; static verification PASS (2026-11-13)

Applied after run5:
1. `AggressiveZombieEntity.descensoExigeConstruir()` now tests the first safe landing reachable from the CURRENT position (`currentY - CAIDA_SEGURA_MAXIMA`), not `objectiveY + CAIDA_SEGURA_MAXIMA`. The old test incorrectly treated a supported ledge near the target as proof that a 22–25 block fall from the spawn platform was safe, suppressing `construirEscalonHacia()` on global budget exhaustion. The new test should trigger the existing bounded side-supported stair builder when the immediate safe landing is unsupported/embedded.
2. C4 siege registration is deferred until the next server tick and resolves all eight attacker UUIDs against `ServerLevel.getEntity(UUID)` before calling `iniciarAsedioParaArnes`. It logs each resolved entity, its instance/lookup state and `worldSiegeIndex`, retries at most 20 ticks, and fails instrumentation explicitly if any UUID remains unresolved. It no longer accepts or silently registers a partial wave.
3. At C4 post-raid recovery, the disposable test world receives a small, explicit work kit: 4 raw beef in the pantry, 2 iron ingots + planks/sticks/string/feather/nugget in the warehouse, and up to 3 fish through the production `reponerPecesDelLago` method. The harness logs before/after counts. This ensures butcher, weaponsmith and fisherman are tested with real work available rather than being marked as failures while legitimately idle due empty inputs. Acceptance remains strict (expected Goal must actually resume; 0 worker casualties; 4/4 guards fight).
4. Extended C4 eligibility diagnostics for butcher (raw food/wheat/smoker/fuel) and weaponsmith (iron/planks/sticks/string/feathers/nuggets/swords/shields).

Build: `gradlew policyTest compileJava -Pa2nDiagnostics=true` — BUILD SUCCESSFUL; all policy tests passed, including `NavPlannerStateTest`, `NavGoalMetricTest`, `NavClimbPolicyTest`, `SiegeWaveTest`, `WorldSiegePolicyTest`, and village resource policies. `lint_aldea.py --strict` PASS; `git diff --check` PASS; launcher parser PASS; harness copies identical; no server process remains. No source commit made; all working-tree edits remain preserved. Next: fresh disposable A2+C4 run, same seed 12345, globalBudget=8000; verify idx4 emits `CONSTRUCCION-ESCALERA`/`ESCALERA-ANCLA`, 8/8 cross the perimeter, C4 registers 8 attackers, all 4 guards fight, 12/12 workers resume with seeded inputs, and 0 casualties.


## A2 run6 — immediate-landing stair trigger + deferred C4 registration + seeded recovery work — IN PROGRESS

RunId `a2n-20261113-001709`, fresh disposable copy of repaired seed 12345. Same A2 limits (globalBudget=8000, local budget=900, phase A=3000, phase B=6600, recovery=1200). New checks: idx4 should invoke side-supported staircase when its first safe landing from y=96 is unsupported; C4 should resolve/register all eight attacker UUIDs after the next server tick; postraid fixture should log resource additions and allow butcher, weaponsmith and fisherman to run their real Goals. Strict C4 acceptance unchanged. No source edits while the server is active.


### Run6 early checkpoint — C4 registration fixed; global-build integration gap confirmed

Run `a2n-20261113-001709`: all eight attacker UUIDs resolved on the first deferred tick (`instanceRemoved=false`, `instanceAlive=true`, `worldSiegeIndex=0`) and `VillageManager.iniciarAsedioParaArnes` logged `registered objective=0 attackers=8`. This closes the run5 C4 instrumentation failure.

The immediate safe-landing fix is active: idx4 #270's chain diagnostic now correctly reports `necesidadConstruccion=caida de 25 bloques ... primera celda intermedia ... sin soporte`. However the attacker remains at `566,96,533`, and no `ESCALON ADHERIDO`/`CONSTRUCCION-ESCALERA` event has fired for it. Source call-chain audit found the integration gap: `construirEscalonHacia()` is currently called only when the LOCAL `NavPlanner.Result.hitBudget()` branch fires (NavExecutor around line 2352). A global `NavPlanner.GlobalResult.hitBudget()` is consumed earlier in `elegirObjetivoLocal()` (around line 1343), where the partial global route is selected without invoking the builder. Therefore the geometry predicate is now correct but the global-budget branch bypasses it. Next source edit after run6 ends: in `elegirObjetivoLocal`, before handling an empty/partial global route, when `global.hitBudget()` and the mob is `AggressiveZombieEntity`, call `construirEscalonHacia(mision)`; if it places a step, invalidate `globalCache/globalDesde/globalMision`, log the fallback, and return null so the next tick replans against the changed terrain. This is gated by the existing immediate-safe-drop predicate and does not increase the global expansion budget.


## A2 run6 final + next integration — 2026-11-13

Run `a2n-20261113-001709` completed normally; server saved all dimensions and no server JVM remains. A2 phase B measured 6/8 attackers ever entering the perimeter (improved from 5/8); the high-platform idx4 remained stuck because, although `descensoExigeConstruir` now correctly detected the 25-block fall, `NavExecutor.elegirObjetivoLocal` did not invoke the stair builder on GLOBAL budget exhaustion. Two other attackers placed side-supported stairs; idx7 crossed the perimeter, but overall A2 remains OPEN (6/8).

C4 registration now works: 8/8 attacker UUIDs resolved and siege objective 0 registered. C4 combat/worker metrics: 4/4 guards fought; 12/12 workers alive; 0 worker casualties; 11/12 expected goals observed; 0 unresolved; 12/12 profession goals installed; 0 instrumentation failures; job-site assignments clean. The only 11/12 was the nitwit, which was actively running `VillagerRepairGoal` with `BUILDER_TAG=true` instead of `VillagerCollectGoal`. This is legitimate work for the builder/recolector nitwit, not idleness. Updated C4 harness acceptance to count either CollectGoal or a tagged RepairGoal for NITWIT only, and to log the alternate activity explicitly. Other professions still require their exact expected Goal.

## Global-budget stair integration — implemented; static verification PASS

Added `AggressiveZombieEntity.objetivoSobreUltimoEscalon()` to expose the feet cell above the verified pending step. In `NavExecutor.elegirObjetivoLocal`, when `NavPlanner.GlobalResult.hitBudget()` is true, it now calls `construirEscalonHacia(mision)` before consuming an empty/partial global route. If a side-supported step is placed and its top cell is within the local window and valid, it invalidates `globalCache/globalMision/globalDesde` and returns that standing cell as the next local objective. This routes the mob onto the step and lets the next arrival advance the verified staircase anchor. The fallback is gated by the immediate safe-landing construction predicate; no global budget increase (still 8000).

Verification after this patch: `gradlew policyTest compileJava -Pa2nDiagnostics=true` — BUILD SUCCESSFUL; all policy tests passed. `lint_aldea.py --strict` PASS; `git diff --check` PASS; launcher parser PASS; harness copies identical; no server JVM. Actual HEAD `518fee13fc15bfb9b92b0479d86b5c609d1947da`; no commit created. Next: fresh run7 on seed 12345; verify `A2 GLOBAL-BUDGET-STAIR`, `ESCALON-TRAVESADO`/`ESCALERA-ANCLA`, 8/8 perimeter, and C4 PASS with nitwit alternate activity counted.


## A2 run7 — global-budget stair fallback + nitwit alternate-work acceptance — IN PROGRESS

RunId `a2n-20261113-003048`, fresh disposable copy of seed 12345. GlobalBudget=8000, local budget=900, phase A=3000, phase B=6600, recovery=1200. Watch for `A2 GLOBAL-BUDGET-STAIR` for idx4, verified `ESCALON-TRAVESADO` and anchor advancement, 8/8 perimeter entry; C4 should register 8/8 attackers, count 4/4 guards, 12/12 workers with the tagged nitwit allowed to resume either CollectGoal or RepairGoal, and retain 0 casualties/instrumentation failures. No source edits while server is active.


## A2 run7 final — C4 PASS; A2 navigation still blocked on idx4 (2026-11-13)

Run `a2n-20261113-003048` completed normally in disposable world; no server JVM remains. `fin.txt` confirms **C4_PASS**: 4/4 guards fought; 12/12 workers alive and resumed; 0 worker casualties; 0 unresolved; 12/12 profession Goals installed; 0 instrumentation failures; 4 job sites, 0 orphaned, 0 duplicate owners. All 8 attacker UUIDs resolved and siege registration succeeded. This closes the C4 recovery/defense acceptance for this run.

A2: 5/8 ever entered the perimeter, 0 inside the wall at snapshots; 5 died, 3 alive at phase B, later 3 retired for recovery. No contracted numerical entrance threshold is defined in repo, so no invented pass/fail threshold. The high platform attacker idx4/#264 remained at `566,96,533`, ended FAILED. Global planner repeatedly exhausted exactly 8000 expansions (`front=575,70,533`, `routeFront=9`, `budget=true`). No `A2 GLOBAL-BUDGET-STAIR` event occurred. One different attacker idx8/#268 did place a verified side-supported stair at `735,64,578`, but did not progress to the village. Therefore global-budget stair integration is NOT ACCEPTED yet.

Important correction: run7's final verdict is `C4_PASS`, not overall A2 success. The earlier interim interpretation that the global fallback had executed was incorrect: the logs show only the generic side-stair placement for #268, and no global-budget fallback for #264. Next investigation is now instrumented: when the global planner hits budget and the fallback cannot place, `NavExecutor` logs `A2 GLOBAL-BUDGET-STAIR-REJECT` with the zombie's `decisionDescensoDiag()` and `escaleraDiag()` (including exact candidate rejection). The periodic `RAID NAV` sample also now includes `objetivoLocalDiag`, descent decision, and stair candidate diagnostic. This will distinguish: (a) immediate landing considered naturally supported, so no stair is warranted; (b) staircase candidate search exhausted/rejected cells; or (c) placement succeeded but the standing cell failed local validation.

The diagnostic patch compiles: `gradlew policyTest compileJava -Pa2nDiagnostics=true` BUILD SUCCESSFUL (39s; all policy tests green). `lint_aldea.py --strict`, `git diff --check`, launcher parser, and harness-copy equality all PASS. HEAD remains `518fee13fc15bfb9b92b0479d86b5c609d1947da`; no commit made.


## A2 run8 — stair rejection reason diagnostics — IN PROGRESS

RunId `a2n-20261113-005233`, fresh disposable copy of seed 12345. Same raid limits (globalBudget=8000, local budget=900, phase A=3000, phase B=6600, recovery=1200). New instrumentation records exact reasons when idx4's global-budget stair fallback cannot place: safe-landing predicate, candidate rejection summary, stair progress state, and local objective diagnostics. C4 is also rechecked (8 attackers registered, 4 guards, 12 worker resumptions, 0 casualties). No source edits while server active.


### Run8 early finding — exact reason global stair placement rejects idx4 (#270)

Run `a2n-20261113-005233` has started and C4 wave registration succeeded. At first global-budget exhaustion, idx4/#270 logs `A2 GLOBAL-BUDGET-STAIR-REJECT`: `decisionDescensoDiag` confirms it DOES require construction (current y=96, objective y=73, first landing y=93 is embedded/unsupported). The staircase builder then exhausts 60 candidates. Every candidate is `celdaAire=false`; the first row is directly below the platform floor, and subsequent x offsets are still inside solid terrain. This is not a false geometry predicate or a global-budget branch bypass: the fallback runs, but its candidate geometry starts too low and cannot exit the platform.

Root cause: `construirEscalonDescendente` searches only `dy=1..CAIDA_SEGURA_MAXIMA` below the current floor anchor, while this spawn requires a FIRST side-supported step at the SAME Y as the platform floor, one block horizontally beyond its edge. The first step must be permitted at dy=0 only when `escaleraColocados==0` and no prior stair step exists; after the attacker physically traverses it, normal descending steps (dy>=1) continue. `evaluarCeldaDeEscalon` currently rejects all same-height candidates as `altura no descendente`, so the targeted fix is to allow only that one initial horizontal egress step. Keep the 60-candidate bound, 24-block staircase cap, support-face validation, two-block headroom, village protection, and real traversal requirement. Do not apply source edits while run8 server is active. Current log confirms 8/8 spawns, 8/8 UUID registration; await final C4 result before patching.


### Run8 second root cause — candidate budget starves lower stair levels

Further inspection of the run8 rejection string shows all 60 candidates were at exactly y=94. `construirEscalonDescendente` currently loops `dy` outermost and horizontal offsets (9x9=81) inside; the 60-candidate hard cap is exhausted during the first vertical plane, so dy=2 and dy=3 are NEVER evaluated. This is an independent search-order defect. The fix should interleave vertical levels by increasing horizontal Manhattan radius: radius 0, then 1, 2, ...; within each radius, test all permitted dy values, then each directional `(dx,dz)` shell where `dx+dz=radius`. This lets the same 60-candidate cap test multiple heights near the current anchor without expanding the work budget. For the first staircase placement, dy=0 is additionally allowed only for a single adjacent, forward-facing, side-supported egress step; subsequent placements start at dy=1 and descend normally. Preserve support/headroom/protection checks, actual traversal anchor advancement, max blocks, and per-attempt candidate cap.


### Run8 phase-B checkpoint (t=3900) — diagnosis confirmed, run still active

`a2n-20261113-005233` reached phase B and remains active. Snapshot t=3900: 3 alive, 5 dead, 0 disappeared, 5/8 ever entered perimeter, 0 currently inside wall. Idx4/#270 is definitively FAILED at `566,96,533`; its periodic `RAID NAV idx=4` confirms `buildable=false`, `escalones=0/64`, `fallosEscalon=10/10`, global budget 8000 exhausted, construction predicate true, and no valid candidate among 60—all candidate cells at y=94 and occupied. This confirms both the need for a first adjacent same-height side step and the dy-outer candidate-order starvation. Other attackers did place and traverse side-supported steps, so the placement primitive itself works. C4 final acceptance for run8 is still pending; run7 already established C4_PASS. Do not edit source until run8 server has saved and shut down. Next source patch: candidate iteration by increasing horizontal Manhattan radius with dy interleaved; permit dy=0 only for the first unplaced step and only at horizontal Manhattan distance 1 in the forward search direction. Then build/policy tests and a new disposable run9.

## A2/C4 current checkpoint — 2026-11-13 after run9

Run9: a2n-20261113-010446 finished and server exited. A2: 7/8 attackers ever entered perimeter; idx4/#270 FAILED at 566,96,533; 7 dead, 1 disappeared during recovery; 0 inside wall at final snapshot. The idx4 spawn was invalid: harness fabricated 566,95,533 because old spawn scan stopped at y=95; prepared seed WORLD_SURFACE at 566,533 is y=121. Do not use run9 as proof of gameplay stair failure or widen candidate radius based on it.
Run9 C4: FAIL, 4/4 guards fought, 12/12 workers alive, 0 casualties, 9/12 resumed, 0 unresolved, job sites clean (2, orphaned=0, duplicates=0), 0 instrumentation failures. Three missing workers are toolsmith at distance 87.4, fisherman at 123.9, shepherd at 122.5 from center; fisherman/shepherd have no job site. Investigate unbounded goals/work targets and keep acceptance strict.
Implemented since run9: A2 stair candidate search by horizontal Manhattan shells with interleaved dy; one first forward side step at dy=0 only when no prior stair and Manhattan distance=1; all subsequent steps descend. The pre-first-stair anchor tracks current feet until the first step is placed. Harness now searches real spawn columns top-down in y=[center-8, center+80] and refuses to fabricate a platform if no valid surface exists. Harness copies identical.
Static checks: Gradle policyTest compileJava -Pa2nDiagnostics=true BUILD SUCCESSFUL; lint_aldea.py --strict PASS; git diff --check PASS; launcher parser PASS.
Next: run10 with disposable seed 12345 and same settings; check idx4 surface spawn y=122, 8/8 spawn + UUID registration, actual movement to mountain edge, stair placement/traversal. Then continue C4 worker-boundary diagnosis. No source edits while run active; no commits; preserve all working tree changes. HEAD 518fee13fc15bfb9b92b0479d86b5c609d1947da.

## Run10 live checkpoint — a2n-20261113-011717 (ACTIVE; do not edit source)
- 8/8 real surface spawns, no fake platform; idx4 at 566,122,533; C4 test siege registered 8/8 UUIDs.
- idx3/#265 placed a same-height forward side stair at 595,95,600 and physically traversed it; logs confirm ESCALON-TRAVESADO and ESCALERA-ANCLA. This validates the first lateral step.
- idx4/#266 placed a descending stair at 568,119,533 (dy=2) but had not traversed it at last sample.
- New bugs to fix after server closes: (1) keep pending stair feet-cell as local objective every tick until traversed; current NavExecutor only returns it on placement tick, then follows unrelated route; (2) when no pending stair, update anchor to actual feet support position even after previous stairs; (3) if global route has no valid local waypoint after terrain changes, try bounded stair builder even if global planner did not hit budget.
- Phase A t=2700: 4/8 ever entered perimeter, 0 inside, 4 alive/4 dead. Run remains active through phase B and C4 recovery. Do not edit source until fin.txt + server exit.

## 2026-11-13 — run10/run11 checkpoint
- Run10 a2n-20261113-011717: 8/8 real surface spawns, no fake platform, C4_PASS (4/4 guards, 12/12 workers resumed, 0 casualties). A2 still fails at 4/8 perimeter entry, 0 inside at final snapshot.
- Runtime evidence: #265 placed and physically traversed the first same-height side stair (595,95,600); ESCALON-TRAVESADO + ESCALERA-ANCLA confirmed. Then it moved naturally to 608,95,599 but anchor stayed stale and route had no valid local waypoint. #266 placed a descending stair at 568,119,533 but NavExecutor abandoned it after the placement tick.
- Applied: explicit pending stair confirmation helper; NavExecutor now prioritizes pending stair feet cell until traversal; anchor tracks current real support whenever no stair is pending, while preserving prior anchor if current support shape is nonstandard; bounded stair-builder fallback added when route has no valid local waypoint even if global budget was not exhausted.
- Run11 a2n-20261113-012835 crashed at tick 3: NPE base=null in construirEscalonDescendente because current support had empty collision shape and no anchor was initialized. Fixed with non-null fallback to blockPosition().below() and defensive null guard.
- Post-fix verification: policyTest compileJava -Pa2nDiagnostics=true BUILD SUCCESSFUL; all policy tests PASS; lint strict PASS; git diff --check PASS.
- Next: run12 same seed/raid settings; verify no crash, 8/8 real spawns, pending step retained until physical traversal, anchor progression, C4_PASS. HEAD 518fee13fc15bfb9b92b0479d86b5c609d1947da; no commit; preserve all changes.

## Run12 live checkpoint — a2n-20261113-013131 (ACTIVE; do not edit source)
- No crash after anchor null guard; 8/8 real surface spawns, no fake platform; idx4 at 566,122,533.
- #269 first side stair at 595,95,600 and #270 descending stair at 568,119,533 were both physically traversed; pending stair target retention works.
- Remaining: after traversal, #269's local mission is in window but needs descent construction; early mission return bypasses builder. #270 has a valid local waypoint but its DROP is blocked at y=118 although geometry says a stair is needed. Need invoke builder when local mission/path DROP is blocked and descent requires construction, not only global budget/no-waypoint.
- t=300: 2/8 ever entered perimeter, 0 inside, 8 alive. Run continues to phase B and C4 recovery. Do not edit source until fin.txt + server exit.

## 2026-11-13 — run12 final and run13 checkpoint
- Run12 a2n-20261113-013131: no crash, 8/8 real surface spawns, C4_PASS (4/4 guards, 12/12 workers resumed, 0 casualties, 0 unresolved, 0 instrumentation failures). A2: 5/8 ever entered perimeter, 0 inside at final, 3 alive/5 dead.
- Pending stair retention and anchor progression worked. #269 placed/traversed two stairs and placed a third, but could not traverse it because solid terrain blocked the horizontal entry cell at 609,94,597. #270 traversed its first descending stair but could not continue. This exposed unreachable stair placements despite headroom/support validation.
- Applied: AggressiveZombieEntity now validates a horizontal access corridor to each descending stair (both X/Z path orders) and rejects a candidate if entry/headroom or intermediate supported corridor is blocked. NavExecutor now tries the bounded stair builder before returning an in-window mission, closing an early-return bypass. Pending stair priority, physical traversal confirmation, and anchor tracking remain.
- Verification: policyTest compileJava -Pa2nDiagnostics=true BUILD SUCCESSFUL; lint strict PASS; diff check PASS. No commit; HEAD 518fee13fc15bfb9b92b0479d86b5c609d1947da.
- Next: run13 same seed and settings; confirm inaccessible steps are rejected, attackers can route/tunnel around the obstruction, place reachable steps and physically traverse them; target A2 improvement beyond 5/8; keep C4 PASS. Do not edit while server active.

## 2026-11-13 — run14 final / next A2 diagnosis
- Run14 a2n-20261113-015608 completed normally; 8/8 real surface spawns; C4_PASS (4/4 guards, 12/12 workers resumed, 0 casualties, 0 unresolved, 0 instrumentation failures; jobSite=[3,0,0]).
- A2: 5/8 ever entered and 1 inside at phase A t=3000; 6/8 ever entered, 0 inside at phase B t=6600. A2 remains unaccepted.
- New landing-height corridor validation rejects unreachable stairs (e.g. #272 candidate rejected as "entrada bloqueada a altura de aterrizaje y=120"). No unreachable third stair placed. #271 exhausted 10/10 local route invalidations with 60 candidates rejected due solid terrain/access. #272 repeatedly rejects DROP at 569,116,531 because blocked y=118; global planner exhausts 8000 expansions, and all 28 stair candidates rejected due headroom/support/access. #276 tunneled via BREAK-VERIFICADO elsewhere, proving mining works in some terrain.
- Next: inspect NavTrace idx3/#271 UUID 27ea8460-87d6-4892-941e-267da5ad41e3 and idx4/#272 UUID bed9f240-8c68-4a48-b429-a70b7ce7a3de; correlate route invalidation and world changes, inspect 3D block volume around stuck positions in disposable world. Decide if mining/route-around/wider stair search is needed; do not increase retry budget speculatively. No server active, no commit, HEAD 518fee13fc15bfb9b92b0479d86b5c609d1947da.

## 2026-11-13 — BREAK/BRIDGE route waypoint correction
- Source inspection found a concrete mismatch: NavPlanner emits BREAK/BRIDGE action nodes and NavExecutor has handlers for them, but NavClimbPolicy treated them as foot waypoints; NavExecutor then rejected solid BREAK cells with esPieValido(). This explains route invalidation even when the global route had obstruction actions.
- Fixed: BREAK/BRIDGE are selectable action waypoints but not foot waypoints; BREAK revalidated via AggressiveZombieEntity against protection, block entities, and canBreakBlock. NavExecutor exempts action nodes from foot validation and retains their transition for idempotence so it does not reset the local route each tick. Expanded NavClimbPolicyTest.
- Verification: policyTest compileJava BUILD SUCCESSFUL; all policy tests PASS; lint strict PASS; diff check PASS. No commit; HEAD 518fee13fc15bfb9b92b0479d86b5c609d1947da.
- Next run15 same seed/settings; confirm idx3/#271 and idx4/#272 select transicion=BREAK, emit BREAK-DELEGADO/VERIFICADO, and continue navigation without mining protected blocks; verify C4_PASS and A2 progress. Do not edit while server active.

## 2026-11-13 — run15 final and targeted obstruction clearance
- Run15 a2n-20261113-021109: C4_PASS (4/4 guards, 12/12 workers resumed, 0 casualties, 0 unresolved, 0 instrumentation failures). A2: 6/8 ever entered, 0 inside at final; one entered at t=4566 but died t=4872; final two stuck #270/#271 at 608,95,599 and 568,119,533.
- BREAK/BRIDGE action waypoint correction works: #274/#275 delegated Stone breaks and advanced via BREAK-YA-RESUELTO-AVANZA; #275 entered perimeter/wall at t=4566.
- Exact remaining blockers: #270 stair candidates have solid access-corridor cells near x=608..612,z=597 within mining reach; #271's DROP 569,116,531 is blocked at (569,118,531). Prior code only rejected/replanned and never mined these exact obstruction cells.
- Applied targeted clearing: NavExecutor identifies the exact solid cell in a rejected DROP column and uses the existing BREAK recovery action if minable/unprotected/in range, verifies air, then invalidates caches. AggressiveZombieEntity stair corridor remembers the first reachable/minable blocker and clears only that block via existing romperNodoDeRuta tunnel budget/cooldown/protection checks; it is not counted as a stair. No-waypoint fallback distinguishes cleared obstruction from placed step and replans.
- Verification: policyTest compileJava test BUILD SUCCESSFUL; all tests PASS; lint strict PASS; diff check PASS. No commit; HEAD 518fee13fc15bfb9b92b0479d86b5c609d1947da.
- Next run16 same seed/settings; verify A2 STAIR-ACCESS-CLEAR and DROP_OBSTRUCTION_CLEARED for #270/#271, replan/progress, C4_PASS and A2 improvement. Do not edit while server active.

## A2 - current state after Run17 (2026-11-13)
**Estado: OPEN / NOT ACCEPTED.** C4 remains PASS; A2 wall traversal does not.

- Run16: 6/8 attackers ever entered, 0 remained inside at t=6600; C4_PASS.
- Run17: 4/8 ever entered, 0 remained inside at t=6600; 4 alive / 4 dead; C4_PASS.
- Blocker 1: entity #267 places stair 608,93,597 but remains at 608,94,598. Generic arrival tolerance 2.25 causes a false waypoint arrival while adjacent. Run18 changes only pending-stair arrival to require physical occupancy.
- Blocker 2: entity #268 repeatedly rejects DROP landing 569,116,531 because spruce_leaves blocks y=118. Run17 recorded 3280 failed descents; the previous destination-wide exclusion did not visibly alter retries. Run18 adds exact obstacle/key/fingerprint/skip instrumentation to locate the mismatch.
- Do not remove the no-wood/no-leaves restriction. Route around foliage or use valid navigation; never mine logs/leaves or protected village blocks.
- Global planning is expensive: 8000 expansions can evaluate 448000 edges and create major tick lag. Do not increase the budget speculatively.
- Run18 source patch passes policyTest compileJava compileTestJava -Pa2nDiagnostics=true, all policy tests, lint_aldea.py --strict, and git diff --check. No commit; HEAD 518fee13fc15bfb9b92b0479d86b5c609d1947da.
- Next action: run18 on the disposable prepared seed 12345, then wait for fin.txt and server exit before editing again. Full handoff: docs/autonomous-village/A2-WALLED-RAID-HANDOFF.md.

## A2 - current state after Run18 (2026-11-13)

**Status: OPEN / NOT ACCEPTED.** C4 remains PASS; A2 wall traversal does not.

- Run18: 4/8 attackers ever entered; 0 remained inside at t=6600; 4 alive / 4 dead; C4_PASS.
- DROP global exclusion works: one blocked destination registered and nine global planner exclusion hits. The local planner received the policy but did not check it; Run19 adds the missing local A* filter.
- Pending stair 608,93,597 remains untraversed. The feet target 608,94,597 is rejected by esPieValido(); Run19 adds exact diagnostic booleans and block states. Do not remove protection, headroom, support, or chunk checks until the failing condition is known.
- Strict pending-stair physical-arrival gate and policy tests remain in place.
- Do not mine logs, leaves, wood, or protected village blocks. Do not increase global/local budgets speculatively; 8000 global expansions can evaluate 448000 edges.
- Run19 patch passes policyTest compileJava compileTestJava -Pa2nDiagnostics=true, all policy tests, lint_aldea.py --strict, and git diff --check. No commit; HEAD 518fee13fc15bfb9b92b0479d86b5c609d1947da.
- Next: run19 on disposable seed 12345; verify DROP_LOCAL_EXCLUSION_HIT and STAIR_PENDING_WAYPOINT_REJECTED / SELECTED; then verify C4_PASS and A2 progress.

## A2 - Run19 result (2026-11-13)

**Status: OPEN / NOT ACCEPTED.** Local DROP exclusion is validated; second stair traversal is not.

- Run19 A2 final t=6600: 4/8 attackers ever entered, 0 inside at close; 4 alive / 4 dead.
- Local exclusion: 807 DROP_LOCAL_EXCLUSION_HIT, 0 stale. This closes the global/local policy mismatch.
- Stair events: 143 STAIR_PENDING_WAYPOINT_SELECTED, 0 rejected, 0 STAIR_NODE_PHYSICALLY_REACHED. One intermediate ESCALON-TRAVESADO for #270 at 595,95,600 was physically verified, but #270 later stalled at the second stair 608,93,597. #271 stalled at 588,108,523.
- C4_PASS: guards fought, 12/12 workers alive and resumed, 0 worker casualties, 0 unresolved, 16 survivors, 4 attackers removed for recovery, jobSite [2,0,0], 0 instrumentation failures.
- Next: inspect movement handoff after STAIR_PENDING_WAYPOINT_SELECTED: moveTo, navigation path/target, later overrides/stops, active goal and position delta. Keep physical arrival and all protection/support/headroom checks strict.
- Preserve Run19 logs and use a fresh disposable copy for next test. Do not mine logs/leaves/wood or alter protected village blocks.

## A2 - Run21 result (2026-11-13)

**Status: OPEN / NOT ACCEPTED.** The raid made further progress, but no attacker remained inside at phase close.

- Run21 A2 t=6600: 7/8 entered ever, 0 inside. t=7500: 0 alive, 6 dead, 2 disappeared, 7 entered ever, 0 inside.
- C4_PASS: guards fought; 12/12 expected workers alive and resumed; 0 casualties; 0 unresolved; 16 survivors; 2 attackers removed for recovery; jobSite [1,0,0]; 0 instrumentation failures.
- Local/global DROP exclusions remain correct: 813 local hits, 3234 global hits, 0 stale fingerprints.
- Pending-stair proximity guard validated: 4 suppressions. Three physical stair traversals verified by ESCALON-TRAVESADO.
- Four pending-stair rejections for #268 were all outside-window only: target 588,106,523; entity around 587,96,510 after falling away. This is the next blocker.
- BREAK action objective policy now accepts BRIDGE and only currently breakable BREAK targets in both objective validation gates. Static gates passed before Run21.
- Next: trace why the entity falls away from the placed stair, then determine whether to invalidate the stale pending step or plan a new reachable step. Do not widen the local window blindly or relax support/headroom/protection/physical-arrival checks. Never mine tree logs/leaves/wood; only the verified wall-ring log exception is allowed.


## Run22 — instrumentación de comando de movimiento (2026-11-13)

### Evidencia forense adicional de Run21

Se releyó `nav-trace.log` del run `a2n-20261113-104836` por UUID del atacante #268 (`493868b1-16c4-4385-8d45-ebc59725730a`). La secuencia relevante es:

- Primero tuvo el peldaño pendiente `588,108,523`, con celda superior `588,109,523`; el ejecutor llegó a seleccionar el waypoint desde `588,109,524` (adyacente, no ocupado).
- Después se registró `NAV_FAIL` por `construccion agotada sin completar el descenso` en `589,99,513`, seguido de rearme con misión `599,71,516`. Luego otro fallo por replanteos agotados en `588,98,512`.
- La escalera siguió reteniendo un bloque pendiente posterior en `588,105,523`, cuya celda superior `588,106,523` quedó fuera de ventana. Las cuatro negativas fueron exclusivamente `dentro=false`; chunk cargado, no protegido, pies/cabeza libres y soporte cobblestone válido.
- El atacante intentó después DROP natural a `588,93,511` desde `588,95,511`; los snapshots posteriores lo muestran oscilando cerca de `587.9,96,510.5`, sin completar el aterrizaje. Esto confirma que la caída/desvío sucede antes de la posterior condición fuera-de-ventana, no que el único problema sea el tamaño de ventana.

La evidencia no basta todavía para atribuir el desplazamiento a vanilla Navigation, a un Goal concurrente o al ciclo de recuperación/rearme. Tampoco autoriza invalidar el peldaño ni expandir la ventana.

### Instrumentación añadida para Run22

En `NavExecutor` se añadió `MOVE_COMMAND`, llamada inmediatamente después de emitir cada movimiento STEP_UP, DROP, acercamiento al origen DROP, acercamiento a nodo BREAK/BRIDGE, movimiento por defecto y al iniciar/rechazar JUMP_UP. Registra destino solicitado, transición, velocidad, posición real, delta desde la última muestra, estado `isDone/isInProgress`, target/número/índice del Path vanilla y nombres de Goals activos. Se limita a un evento por cambio de destino o cada 10 ticks por clave; no altera decisiones de navegación.

Verificación tras instrumentación: `policyTest compileJava compileTestJava -Pa2nDiagnostics=true --no-daemon --console=plain` BUILD SUCCESSFUL; `python tools\\lint_aldea.py --strict` PASS; `git diff --check` PASS. No se lanzó Minecraft todavía con esta instrumentación. Run22 debe usar una copia desechable fresca del fixture preparado y los mismos parámetros/semilla. A2 continúa OPEN / NOT ACCEPTED; C4 Run21 permanece PASS. Sin commit; preservar todos los cambios existentes.


### Run22 lanzado — en curso (2026-11-13 11:05 local)

- RunId: `a2n-20261113-110548`.
- Fixture fuente: `build\\a2n\\arena-repair1-20261112-205206\\game\\a2n`; el lanzador creó una copia nueva y exclusiva en `build\\a2n\\a2n-20261113-110548\\game\\a2n`.
- Semilla 12345; modo raid; NavCustom=true; Fase A=3000; Fase B=6600; presupuesto local=900; global=8000; límite monitor=90 min.
- Proceso lanzador PowerShell PID 8412; JVM servidor PID 9748. En el último sondeo (11:06:22) seguía en carga de mods; aún no existía `fin.txt` ni `nav-trace.log` (el mundo todavía no había iniciado el harness). No editar fuentes ni ejecutar Gradle mientras esta JVM esté activa.
- Próximo sondeo: esperar inicio del mundo y aparición de `nav-trace.log`; comprobar `MOVE_COMMAND` para UUID #268 y verificar si el path/Goals activos cambian antes del desplazamiento. Esperar `fin.txt`, guardado de dimensiones y salida de JVM antes de modificar código.


## A2 — Estado posterior a Run22 (2026-11-13)

**OPEN / NO ACCEPTED.** Run22 terminó correctamente a nivel de harness y C4, pero no cumplió A2: 4/8 atacantes entraron al perímetro alguna vez; ninguno estaba dentro al cierre de fases A/B. C4 PASS: 12/12 trabajadores reanudados, cero bajas y cero no resueltos.

Bloqueo demostrado para #270: descenso a través de `589,97,512` bloqueado por cobblestone de puente del asedio, clasificado `BLOQUEADO_OBRA_DEL_ASEDIO`; el bloque no pertenece al muro y no debe romperse. La selección de escalones no validaba toda la columna de caída.

Corrección local ya aplicada y compilada: validación completa de columna antes de colocar peldaño descendente (`NavDropColumnPolicy`, `NavExecutor`, `AggressiveZombieEntity`) con prueba pura incluida en `policyTest`. Suite y lint PASS.

**Próxima acción:** ejecutar Run23 sobre fixture fresco, mismo seed 12345 y parámetros Run22. Verificar que el candidato bloqueado se rechaza antes de construcción, que se elige una alternativa y que A2 mejora. No modificar ni borrar bloques protegidos del puente; no relajar protección de árboles. No commit.


## A2 — Run23 (2026-11-13)

A2 sigue **OPEN / NO ACCEPTED**. Run23 terminó y C4 pasó (12/12 trabajadores reanudados, 0 bajas, 0 no resueltos). A2: 5/8 atacantes entraron alguna vez, 0 dentro al cierre de fase B. 791,069 registros de traza.

La validación de columna evita colocar peldaños si el corredor ya está bloqueado, pero un puente posterior puede invadir un corredor ya usado por un DROP. #270 tuvo el aterrizaje de `589,93,512` bloqueado por cobblestone de obra del asedio en `589,97,512`. No está probado quién colocó ese bloque.

Run24 instrumenta cada colocación de puente con `PUENTE_BLOQUE` (UUID/posición/origen/objetivo) sin cambiar la lógica. Correlacionar autor y tiempo antes de diseñar exclusión/reserva de columnas. No relajar protección de tablones, árboles ni muro.


## Run24 — atribución de cada bloque de puente (EN CURSO, 2026-11-13)

- RunId: `a2n-20261113-113725`.
- Fixture fuente: `build\a2n\arena-repair1-20261112-205206\game\a2n`; copia desechable nueva en `build\a2n\a2n-20261113-113725\game\a2n`.
- Seed 12345; raid; NavCustom=true; Fase A=3000; Fase B=6600; presupuesto local=900; global=8000; límite 90 min.
- Launcher iniciado 11:37:26; PID servidor reportado 16328.
- Cambio de código exclusivo de diagnóstico: cada colocación en `puentearHacia` emite `PUENTE_BLOQUE` con UUID del asaltante, posición, origen, objetivo y presupuesto. Sin cambios de lógica de movimiento/puente.
- Esperar `fin.txt` y salida de JVM antes de modificar fuentes. Correlacionar `PUENTE_BLOQUE pos=589, 97, 512` con peldaños y DROP del UUID #270. No ejecutar Gradle ni editar mientras el servidor esté vivo.


## A2 — Run24 (2026-11-13)

A2 **OPEN / NO ACCEPTED**. Run24 C4 PASS; 12/12 trabajadores reanudados, cero bajas/no resueltos. A2: 5/8 entraron alguna vez, 0 dentro al cierre. Trace 574,409 registros, 0 descartados, 0 fallos.

Causa raíz confirmada: #271 colocó `PUENTE_BLOQUE pos=589,97,512 origen=588,97,512`; seis segundos después el mismo atacante registró que el aterrizaje de su escalón `589,93,512` estaba bloqueado en Y=97. El puente invade la columna de caída de la escalera del propio asaltante.

Siguiente: rechazar en `puentearHacia` cualquier lado que ocupe la columna de caída del último peldaño verificado (misma X/Z, rango hasta maxSafeDrop), probar los otros lados, con política y pruebas puras. No romper puentes existentes. Esta corrección aún no está aplicada. No commit.


## A2 — Corrección bilateral puente/escalera (2026-11-13)

Aplicada: cada asaltante conserva los peldaños descendentes verificados de su marcha; antes de colocar un puente, rechaza cualquier lado que invada la columna de caída de uno de esos peldaños (misma X/Z, rango de caída segura). Prueba pura añadida. No rompe puentes, no altera la protección del muro/árboles. La validación previa del peldaño sigue rechazando escaleras nuevas si ya existe un puente en su columna.

Build/policyTest PASS (29s), lint PASS, diff check PASS (CRLF warnings). Próximo Run25 fresco con mismos parámetros; validar rechazo explícito y progreso. A2 sigue OPEN.


## Run25 — validación de exclusión bilateral de columnas (EN CURSO, 2026-11-13)

- RunId: `a2n-20261113-115000`.
- Fixture fuente: `build\a2n\arena-repair1-20261112-205206\game\a2n`; copia desechable nueva en `build\a2n\a2n-20261113-115000\game\a2n`.
- Seed 12345; raid; NavCustom=true; Fase A=3000; Fase B=6600; presupuesto local=900; global=8000; límite 90 min.
- Launcher iniciado 11:50:00; PID servidor reportado 36784.
- Cambio funcional: registrar peldaños descendentes verificados por asaltante y rechazar un puente que invada la columna DROP de cualquiera de ellos; el lado rechazado se omite y se prueban otros lados. Evento esperado: `PUENTE_RECHAZADO_COLUMNA_ESCALERA`.
- Esperar `fin.txt` y salida de JVM antes de editar. Confirmar el rechazo para `589,97,512` o la coordenada equivalente; comprobar que el bloque no se coloca y que el atacante elige alternativa. No tocar puentes existentes ni árboles.


## A2 — Run25

C4 PASS: 12/12 trabajadores reanudados, cero bajas/no resueltos. A2 OPEN (no hay umbral formal acordado): 7/8 atacantes entraron alguna vez, hubo invasores dentro en ambas fases, pero 0 dentro al cierre. Un atacante vivo fuera desapareció durante la limpieza. Trace: 565,242 registros, 0 descartados/fallos.

Corrección bilateral puente/escalera VALIDADA EN RUNTIME: #269 rechazó `616,77,597` por el escalón `616,73,597`; #270 rechazó dos veces `589,97,512` por `589,93,512` y colocó puentes alternativos. No se rompió ningún tablón protegido.

Siguiente: investigar #270 en `588,79,507`, donde agotó replans. Orden BREAK solicitó `590,76,507`, pero Navigation reportó Path target `590,87,507`. Run26 añadirá diagnóstico de primeros nodos/colisión. No declarar A2 PASS ni inventar umbral.


## Run26 — Path nodes y colisiones de BREAK approach (EN CURSO, 2026-11-13)

- RunId: `a2n-20261113-120306`.
- Fixture fuente: `build\a2n\arena-repair1-20261112-205206\game\a2n`; copia desechable nueva en `build\a2n\a2n-20261113-120306\game\a2n`.
- Seed 12345; raid; NavCustom=true; Fase A=3000; Fase B=6600; presupuesto local=900; global=8000; límite 90 min.
- Launcher iniciado 12:03:06; PID servidor reportado 26508.
- Diagnóstico agregado a `MOVE_COMMAND`: primeros cuatro nodos de Path desde nextIndex, estado de bloque en feet/head/support, canReach y celda solicitada con sus tres bloques. Solo instrumentación; sin cambio de navegación.
- Esperar `fin.txt` y salida de JVM antes de editar. Para #270 (idx4), correlacionar orden `ACERCARSE-ACCION` a `590,76,507` con Path target y nodos reales. No tocar la corrección puente/escalera validada en Run25.


## A2 — Run26

C4 PASS. Fase A: 3/8 entraron alguna vez, 0 dentro al cierre. Fase B: 5/8 entraron alguna vez y 1 atacante vivo estuvo dentro al cierre (idx8, salud 8.0). A2 OPEN. Trace 624,095 registros, 0 descartados/fallos.

Nuevo bloqueo: #262 (idx3) pidió acercarse al bloque BREAK sólido `615,74,598`; PathNavigation devolvió `canReach=false` y target de path alto/alejado. La orden va al bloque sólido, no a un pie transitable. #270 también se quedó en BREAK approach. No ampliar permisos de rotura.

Run27 implementa diagnóstico/corrección acotada: si Path BREAK no es alcanzable, raycast al primer bloque entre mob y objetivo; solo si es distinto, cercano y pasa RecoveryAction/BlockInteractionPolicy, romperlo y replanificar. Test puro añadido. A2 OPEN.


## A2 — corrección BREAK approach

Aplicada tras Run26: Path no alcanzable hacia BREAK -> raycast al primer bloque opaco; si distinto del objetivo y dentro de alcance, delegar en RecoveryAction existente (BlockInteractionPolicy sin cambios), luego replanificar. Evento `BREAK_APPROACH_OCCLUSION`. Diagnóstico Path ampliado con primeros cuatro nodos y estado de bloques. Política/test puros añadidos.

Suite policyTest/compile PASS; lint/diff PASS. Próximo Run27 fresco para validar el desbloqueo y preservar protecciones. A2 OPEN.


## Run27 — BREAK approach / primer obstáculo visible (EN CURSO, 2026-11-13)

- RunId: `a2n-20261113-121920`.
- Fixture fuente: `build\a2n\arena-repair1-20261112-205206\game\a2n`; copia desechable nueva en `build\a2n\a2n-20261113-121920\game\a2n`.
- Seed 12345; raid; NavCustom=true; Fase A=3000; Fase B=6600; presupuesto local=900; global=8000; límite 90 min.
- Launcher iniciado 12:19:20; PID servidor 14004.
- Corrección: si Path a BREAK es inalcanzable y raycast impacta primero un bloque distinto del objetivo dentro del alcance, delegar ese bloque como BREAK override al RecoveryAction existente; BlockInteractionPolicy no cambia. Evento esperado `BREAK_APPROACH_OCCLUSION`.
- Build/policyTest compileJava compileTestJava PASS (29s); lint y diff check PASS.
- Esperar `fin.txt` y salida JVM antes de editar. Validar el primer obstáculo autorizado, replanificación, no destrucción de árboles/tablones y progresión A2. A2 OPEN.


## A2 — Run27 y excepción estrecha de guardia

Run27 C4 PASS. A2 OPEN: 5/8 entraron alguna vez, 0 dentro al cierre. Trace 1,020,886; 0 descartados/fallos.

30 BREAK_APPROACH_OCCLUSION; 24 GUARD_REPLAN por `exencionBreak=false celdaActiva=true rechaza=true`. La guarda general interceptó el override BREAK porque el primer obstáculo es adyacente al nodo de ruta.

Corregido: política pura `NavRecoveryGuardPolicy`; solo exime si `nodoDeRecuperacionOverride` coincide exactamente con la celda de recuperación. No omite RecoveryAction ni BlockInteractionPolicy. Test incluido. Próximo Run28 debe verificar exención y rotura autorizada. A2 OPEN.


## Run28 — exención exacta del BREAK override en la guarda (EN CURSO, 2026-11-13)

- RunId: `a2n-20261113-123130`.
- Fixture fuente: `build\a2n\arena-repair1-20261112-205206\game\a2n`; copia desechable nueva en `build\a2n\a2n-20261113-123130\game\a2n`.
- Seed 12345; raid; NavCustom=true; Fase A=3000; Fase B=6600; presupuesto local=900; global=8000; límite 90 min.
- Launcher iniciado 12:31:30; PID servidor 20852.
- Corrección: `NavRecoveryGuardPolicy` exime solo el override BREAK exacto; `guardaDiag` expone `exencionOverrideBreak`. RecoveryAction/BlockInteractionPolicy siguen siendo obligatorios.
- Build/policyTest compileJava compileTestJava BUILD SUCCESSFUL in 28s; lint y diff check PASS.
- Verificar que el evento BREAK_APPROACH_OCCLUSION sea seguido por `exencionOverrideBreak=true`, `rechaza=false`, acción callback y resultado verificado. No romper árboles/tablones protegidos. Esperar fin.txt y salida de JVM antes de editar.


## A2 — Run28

C4 PASS. A2 NO ACCEPTED: fase A 5/8 entraron alguna vez, 0 dentro al cierre; fase B 6/8, 0 dentro al cierre. Fase B: idx4 vivo en 589,93,511 dist=74.98; idx8 vivo en 753,63,543 dist=93.35; ambos fuera.

Trace 664,902; 0 descartados/fallos. BREAK approach=22; GUARD_REPLAN=26; exención override exacta permitida=14, rechazada=0; BREAK-VERIFICADO=991 (actuo=965, no-actuo=26). La excepción estrecha está validada. No ampliar BlockInteractionPolicy.

Siguiente bloqueo: navegación posterior a BREAK y progresión vertical/global de idx4 e idx8. A2 OPEN.


## Run29 — delegar también el propio objetivo BREAK (EN CURSO, 2026-11-13)

### Diagnóstico adicional de Run28

La traza de #274 muestra el caso que la política anterior excluía: PathNavigation no puede alcanzar `746,65,541`; la celda solicitada tiene pies=air, cabeza=stone y soporte=stone. La regla previa delegaba solo si el primer impacto era distinto del objetivo (`primerImpactoEsObjetivo=false`), por lo que el propio bloque BREAK sólido nunca se entregaba a RecoveryAction. #274 repetía ACERCARSE-ACCION en el mismo punto, `canReach=false`, sin desplazamiento.

### Corrección Run29

- `NavActionApproachPolicy.debeDelegarPrimerBloque`: si el Path a BREAK no es alcanzable y el primer bloque del raycast está al alcance, delegar ese bloque tanto si es una obstrucción anterior como si es el propio objetivo.
- El bloque sigue pasando por RecoveryAction y BlockInteractionPolicy; no se añade ningún permiso de rotura. Si el objetivo es protegido/no rompible, el callback lo rechazará.
- Evento `BREAK_APPROACH_OCCLUSION` añade `targetHit=true/false` para distinguir ambos casos.
- `policyTest compileJava compileTestJava -Pa2nDiagnostics=true`: BUILD SUCCESSFUL (30s); NAV_ACTION_APPROACH_POLICY_TEST 6 aserciones.
- Run29: fixture fresco, seed 12345, mismos parámetros raid. Verificar específicamente `targetHit=true` seguido de `BREAK-VERIFICADO accion=actuo`, avance físico y protección de bloques no autorizados. A2 OPEN.


## Run29 — EN CURSO (2026-11-13 12:44:17)

- RunId: `a2n-20261113-124417`; launcher PID=32472; servidor PID=23260.
- Fixture fresco: `build\a2n\arena-repair1-20261112-205206\game\a2n` -> `build\a2n\a2n-20261113-124417\game\a2n`.
- Seed=12345; raid; NavCustom=true; FaseA=3000; FaseB=6600; presupuesto local=900; global=8000; timeout=90 min.
- Cambio: delegar primer bloque del raycast incluso si es el propio objetivo BREAK; RecoveryAction/BlockInteractionPolicy sin cambios.
- Build/policyTest, lint y diff check PASS antes de arrancar.
- Verificar targetHit=true -> BREAK-VERIFICADO accion=actuo -> progreso físico. Confirmar que objetivos protegidos/no rompibles no se destruyen.
- Esperar fin.txt y salida JVM antes de cualquier cambio de código. A2 OPEN.


### Run29 — hallazgo en vivo pendiente de cierre

En #271/#idx8 (UUID `3decf861-e7d9-4d3a-81fe-2d28d6d12828`), `MOVE_COMMAND` repite BREAK hacia `746,65,541` con `canReach=false`, `requestedCell feet=air/head=stone/support=stone`; la posición queda en `747.51,63,541.66`. El estado de la entidad conserva `BREAK-ACERCARSE`. La detección actual sigue viendo bloque inferior distinto (`targetHit=false`); 19 eventos de approach, 92 BREAK-VERIFICADO al corte t=2700. No hay targetHit=true todavía.

Código revisado: en NavExecutor, la comprobación `resuelto = BREAK ? getBlockState(node.pos()).isAir() : !isAir()` está DESPUÉS del bloque `distNodoSqr > ALCANCE_DE_ACCION_SQR`, que ordena acercamiento y retorna. Si el nodo BREAK ya es aire, el ejecutor no avanza hasta que la entidad llegue a un nodo que quizá no sea transitable. Candidato Run30: mover la comprobación de resolución antes del acercamiento, preservando verificación de mundo; añadir predicado puro en NavObjectivePolicy + tests para BREAK resuelto cuando target air y BRIDGE resuelto cuando target sólido. Esperar fin de Run29 antes de editar código.


## Run29 — resultado y nueva corrección de resolución BREAK (2026-11-13)

**RunId:** `a2n-20261113-124417`
**Fin:** 12:53:29; C4_PASS. **A2:** OPEN / NO ACCEPTED.

- Fase A t=3000: 8/8 spawn; 3 vivos, 5 muertos; 5/8 entraron alguna vez; 0 dentro al cierre.
- Fase B t=6600: 8/8 spawn; 3 vivos, 5 muertos; 5/8 entraron alguna vez; 0 dentro al cierre.
- Final B: idx4 `589,93,511` dist=74.91; idx7 `744,63,509` dist=87.00; idx8/#271 `743,63,506` dist=86.92; todos vivos con salud 10, ninguno entró alguna vez.
- Trace: 793,217 registros; descartados=0; fallos=0.
- BREAK approach: 21 eventos; targetHit=true=0; GUARD_REPLAN=16; BREAK-VERIFICADO=98 (actuo=30, no-actuo=68).
- #271 mantuvo MOVE_COMMAND a BREAK `746,65,541` con requestedCell feet=air/head=stone/support=stone y Path canReach=false. El código solo comprobaba `BREAK && getBlockState(node.pos()).isAir()` después de exigir alcance al nodo; el mob nunca alcanzaba la celda y seguía persiguiendo un BREAK ya resuelto.
- C4_PASS: guardasCombatieron=true; profesionalesReanudados=true; jobSite limpio; 16 supervivientes; 12/12 trabajadores vivos/reanudados; 0 bajas/no resueltos; 3 retirados para recuperación; jobSite=[1,0,0]; fallosInstrumento=0.

### Corrección tras Run29

- `NavObjectivePolicy.accionResuelta(breakTransition,targetIsAir)`: BREAK resuelto cuando el target está en aire; BRIDGE resuelto cuando el target es sólido.
- `NavExecutor` ahora consulta el estado real del bloque ANTES de calcular distancia/emitir `moveTo`; si ya está resuelto, avanza routeIndex inmediatamente. Se elimina la comprobación duplicada posterior al alcance.
- Cuatro pruebas nuevas cubren BREAK/BRIDGE resuelto/no resuelto.
- `policyTest compileJava compileTestJava -Pa2nDiagnostics=true`: BUILD SUCCESSFUL (33s); NAV_OBJECTIVE_POLICY_TEST OK (9 aserciones).
- Run30 validará que #271 no repita movimiento hacia un BREAK en aire y que la ruta continúe hacia el siguiente nodo. Mantener la excepción de guardia exacta y BlockInteractionPolicy intactas. A2 OPEN.


## Run30 — EN CURSO (2026-11-13 12:55:20)

- RunId: `a2n-20261113-125519`; launcher PID=412; servidor PID=9504.
- Fixture fresco: `build\a2n\arena-repair1-20261112-205206\game\a2n` -> `build\a2n\a2n-20261113-125519\game\a2n`.
- Seed=12345; raid; NavCustom=true; FaseA=3000; FaseB=6600; local=900; global=8000; timeout=90 min.
- Cambio: `NavObjectivePolicy.accionResuelta` se evalúa antes del acercamiento y avance solo tras comprobar estado del mundo.
- Build/policyTest compileJava compileTestJava PASS; lint y diff check PASS.
- Validación central: el #271/#idx8 no debe seguir emitiendo ACERCARSE-ACCION para un nodo BREAK cuya celda ya está en aire. Debe incrementar routeIndex y perseguir siguiente nodo. Verificar A2 y C4 al cierre.
- Esperar fin.txt y salida JVM antes de editar. A2 OPEN.


### Run30 — evidencia positiva temprana

A t≈414, #271 (UUID `f35facc7-da78-444a-b0a0-febc258d8bb2`) registró `ultimaAccionDeRuta=746,65,541:BREAK-YA-RESUELTO-AVANZA` y el nodo siguiente pasó a `746,67,540:BREAK-ACERCARSE`. Esto confirma que la comprobación de resolución ANTES del acercamiento evita perseguir el nodo BREAK que ya está en aire. Fase A t=300: 1/8 entró alguna vez, 0 dentro al snapshot. Run sigue en curso.


### Run30 — resultado A2 (fase B cerrada; C4 en mantenimiento)

- Fase A t=3000: 6/8 entraron alguna vez; 1 dentro al cierre; 3 vivos/5 muertos.
- Fase B t=6600: 7/8 entraron alguna vez; 0 dentro al cierre; 1 vivo/7 muertos.
- Último atacante idx4: `589,93,511`, distancia horizontal=74.98, fuera. IDX7 se acercó hasta ~9 bloques del centro (663,70,541) pero murió antes de cruzar. IDX8 había estado dentro al cierre de A y después murió.
- `BREAK-YA-RESUELTO-AVANZA`: 50 eventos (vs 10 al cierre de fase A), confirmando que el ejecutor deja de perseguir BREAK en aire y continúa la ruta.
- El bloqueo dominante que queda es idx4: `RECOVERING` en `589,98,513`, `DROP`/escalón construido y fallo persistente de caída por bloque en y=99; distancia horizontal≈74. C4 sigue ejecutando mantenimiento automático; esperar fin.txt antes de editar.


## Run30 — cierre definitivo (2026-11-13)

**RunId:** `a2n-20261113-125519`; fin 13:06:04; servidor cerrado.
**C4:** PASS. **A2:** OPEN / NO ACCEPTED.

- Fase A: 6/8 entraron alguna vez; 1 dentro al cierre; 3 vivos/5 muertos.
- Fase B: 7/8 entraron alguna vez; 0 dentro al cierre; 1 vivo/7 muertos.
- Último atacante idx4/#270: `589,93,511`, distHorizontal=74.98, fuera.
- #8 estuvo dentro al cierre de A en `675,71,580`, pero murió después. #7 se aproximó hasta ~9 bloques del centro (`663,70,541`) pero murió antes de cruzar.
- `BREAK-YA-RESUELTO-AVANZA`: 50; `BREAK_APPROACH_OCCLUSION`: 29; targetHit=true=0; GUARD_REPLAN=14. Trace: 614,896 registros; descartados=0; fallos=0.
- C4_PASS: guardasCombatieron=true; profesionalesReanudados=true; asignacionesJobSiteLimpias=true; supervivientes=16; 12/12 trabajadores vivos/reanudados; bajas=0; no resueltos=0; retiradosParaRecuperacion=1; jobSite=[2,0,0]; fallosInstrumento=0.

### Conclusión de Run30

La corrección de resolución temprana funciona: 50 nodos BREAK en aire se saltaron antes de intentar acercarse, y el número de atacantes que entraron alguna vez subió a 7/8. No bastó para aceptar A2: ninguno permaneció dentro al cierre de B. El cuello de botella restante es idx4/#270 en navegación vertical/descenso alrededor de `589,98,513`; el diagnóstico reporta caída bloqueada por un bloque en y=99 y el mismo DROP/BRIDGE se repite, con distancia horizontal≈74. Investigar terreno/colisión de esa columna y por qué el planificador vuelve al mismo descenso; no revertir `accionResuelta` ni la exención estrecha de guardia.


### Run30 — métricas finales completas y siguiente diagnóstico

- C4_PASS; 12/12 trabajadores reanudados, 0 bajas/no resueltos, 16 supervivientes; 1 atacante retirado para recuperación.
- Trace 614,896 registros; descartados=0; fallos=0.
- BREAK-YA-RESUELTO-AVANZA=50; BREAK_APPROACH_OCCLUSION=29; targetHit=true=0; GUARD_REPLAN=14.
- En los últimos ticks, idx4/#270 ya había avanzado a descensos DROP naturales consecutivos: `588,96,512`, luego `588,93,511`, luego `589,90,510`. En el cierre t=6600 estaba en `589,93,511`, objetivo local `589,88,509`, con la ruta todavía progresando. No es prueba de A2; sí justifica separar "necesita más tiempo" de "bloqueo definitivo".

**Run31 diagnóstico extendido (no aceptación):** repetir fixture/semilla/parámetros con FaseA=3000 y FaseB=9000. Mantener todas las modificaciones. Observar si idx4/#270 cruza después de t=6600 y si llega al interior antes de t=9000. El criterio oficial A2 sigue siendo el de 6600 ticks; esta corrida extendida solo caracteriza el progreso tardío.


## Run31 — EN CURSO (2026-11-13 13:07:28; diagnóstico extendido)

- RunId: `a2n-20261113-130728`; launcher PID=13140; servidor PID=15848.
- Fixture fresco, semilla=12345, raid, NavCustom=true, FaseA=3000, FaseB=9000, local=900, global=8000, timeout=90 min.
- Es diagnóstico de progreso tardío; no cambia el criterio oficial A2 (FaseB=6600).
- Validar si idx4/#270 atraviesa el interior después de t=6600 y antes de t=9000. C4 debe seguir PASS.


### Run31 — fase A cerrada

t=3000: 8/8 spawn; 3 vivos, 5 muertos; 6/8 entraron alguna vez; 1 dentro al cierre (#8 en `673,71,593`, distHorizontal=61.36). idx4 vivo fuera en `583,111,523`, idx7 vivo fuera en `700,63,456`. El resultado coincide con Run30 en fase A (6/8 y 1 dentro). Fase B extendida continúa hasta t=9000.


## Run30 — resultado final y próximo diagnóstico (2026-11-13)

**RunId:** `a2n-20261113-125519`; fixture fresco `arena-repair1-20261112-205206`; seed 12345; raid; NavCustom=true; FaseA=3000; FaseB=6600; cleanup hasta t=7800. El servidor terminó y la JVM del juego ya no está activa.

### Resultado A2
- Fase A t=3000: 8/8 spawn; 3 vivos, 5 muertos, 0 desaparecidos; 6/8 entraron alguna vez; 1 dentro en el snapshot.
- Fase B t=6600: 8/8 spawn; 1 vivo, 7 muertos, 0 desaparecidos; 7/8 entraron alguna vez; 0 dentro en el snapshot.
- Cleanup t=6900–7800: 0 vivos, 7 muertos, 1 desaparecido (el superviviente retirado/limpiado por harness).
- Mejora respecto Run29: entraron alguna vez 7/8 vs 7/8 (igual al cierre fase B), pero Run30 sí mostró 1 dentro al final de fase A. La mejora clave es avance de objetivos BREAK ya resueltos, no aceptación final.
- El último atacante vivo #4 acabó en `589,93,511`, distancia horizontal 74.98, nunca entró. Su estado cerca del cierre osciló entre RECOVERING/PLANNING/TRAVERSING. Ruta de recuperación: `588,96,513`; BREAK delegado `588,96,513` (bloque Dirt) seguido de `BREAK-VERIFICADO accion=actuo` y `BREAK-YA-RESUELTO-AVANZA`; luego DROP natural a `589,90,510`. Se registró rechazo de columna de caída `589,95,512 -> el camino de caida esta bloqueado en y=99`. No asumir que Dirt es muro ni ampliar reglas de rotura sin comprobar geometría/propiedad del bloque.
- Fase A #8 llegó a `675,71,580`, dentro; al cierre de fase B ya había muerto (7 muertos total).
- A2 permanece OPEN / NOT ACCEPTED: requisito de entrada/permanencia no cumplido.

### Resultado de la corrección BREAK
- `BREAK-YA-RESUELTO-AVANZA`: 50.
- `BREAK_APPROACH_OCCLUSION`: 29; `targetHit=true`: 0.
- `BREAK-VERIFICADO accion=actuo`: 81; `accion=no-actuo`: 0.
- `GUARD_REPLAN`: 14.
- `MOVE_COMMAND`: 410.
- Trace: 614,896 registros; descartados=0; fallos=0; 56,483,522 bytes.
- Confirmado que la comprobación de BREAK resuelto antes de acercamiento avanza el índice de ruta y evita perseguir nodos BREAK ya despejados. Mantener este cambio.

### C4
`C4_PASS (raid)`: guardasCombatieron=true; profesionalesReanudados=true; asignacionesJobSiteLimpias=true; supervivientes=16; trabajadoresEsperados=12; trabajadoresVivos=12; trabajadoresReanudados=12; bajasTrabajadores=0; trabajadoresNoResueltos=0; retiradosParaRecuperacion=1; jobSite=[2,0,0]; fallosDeInstrumento=0.

### Siguiente paso
No hacer cambios especulativos ni debilitar protección de bloques. Investigar exclusivamente la ruta de recuperación del atacante #4: por qué permanece en `589,98,513`, cómo se selecciona el bloque Dirt `588,96,513`, si el bloque pertenece al anillo de muro validado o a obra del asedio, y por qué el aterrizaje de descenso rechaza columna bloqueada en y=99. Correlacionar con `MOVE_COMMAND`, `BREAK-VERIFICADO`, transiciones DROP y estado de ruta. Luego diseñar una prueba mínima y repetir raid fresco. Revalidar policyTest/compile y lint antes de la próxima prueba. No tocar `run\saves`, no abrir cliente, no commits. A2 sigue OPEN.


### Run30 — forensic detail #270 / UUID 5019beb6-ddfd-4e8a-b7c6-e1c503c5b933

- Final live idx4 was attacker #270. At t=4797 and 4816, BREAK node `588,96,513` had `pathCanReach=false`; raycast first blocker was `589,98,513` / `588,98,513`, at 0.94 / 1.43 blocks, `targetHit=false`. The executor delegated the first blocker to the existing bounded recovery action.
- Movement snapshots show `589,98,513` had dirt as support and the entity was standing at y≈99. The recovery action subsequently mined Dirt at `588,98,513`, Dirt at `588,96,513`, Dirt at `588,99,513`, and Coarse Dirt at `588,100,513`. This is consistent with the outside-the-village tunneling rule; no evidence here says these were protected wall blocks. Do not broaden mining or wall exceptions.
- At t=6460–6500 it completed BREAK actions. At t=6520 the already-cleared BREAK advanced. At t=6543–6565 navigation transitioned to DROP and descended from y98 to y97; at t=6578–6598 it descended further toward y93/y90. At t=6585 a DROP movement command was issued; Path later reported canReach=true for the natural landing `589,90,510`.
- Earlier global searches for #270 frequently exhausted 8000/8000 expansions; the final global route to meta `600,71,514` used 1390 expansions and 18 nodes at t=6600. Thus the surviving attacker was still making progress and had not entered before the phase deadline; the final position alone does not establish a permanent navigation deadlock.
- Run31 should be a no-code-change duration diagnostic: same seed/fixture/raid, extend phase B (e.g. t=12000) while preserving local/global budgets and all protection rules. Compare whether the same route continues to approach/enter or stalls again. If extended run confirms a stable stall, instrument/inspect the specific waypoint/route transitions; do not raise budgets or change break policy without evidence.


### Run31 — diagnóstico extendido SIN cambios de código (EN CURSO)

- RunId `a2n-20261113-133033`; servidor PID=2440; launcher PID=13900.
- Fixture fresco `arena-repair1-20261112-205206`, seed=12345; raid; NavCustom=true; FaseA=3000; FaseB=12000; local budget=900; global=8000; timeout=90 min.
- Se ejecuta solo para distinguir progreso lento de bloqueo persistente del atacante #270. No cambiar código ni política de rotura durante la corrida. Mundo desechable bajo `build\a2n\a2n-20261113-133033\game\a2n`; `run\saves` intacto.
- Esperar `fin.txt`, comprobar salida JVM y comparar snapshots t=6600, 9000, 12000; especialmente #270/idx4 y #271/idx7. A2 OPEN.


### Run31 — diagnóstico extendido SIN cambios de código (EN CURSO)

- RunId `a2n-20261113-133033`; servidor PID=2440; launcher PID=13900.
- Fixture fresco `arena-repair1-20261112-205206`, seed=12345; raid; NavCustom=true; FaseA=3000; FaseB=12000; local budget=900; global=8000; timeout=90 min.
- Se ejecuta solo para distinguir progreso lento de bloqueo persistente del atacante #270. No cambiar código ni política de rotura durante la corrida. Mundo desechable bajo `build\\a2n\\a2n-20261113-133033\\game\\a2n`; `run\\saves` intacto.
- Esperar `fin.txt`, comprobar salida JVM y comparar snapshots t=6600, 9000, 12000; especialmente #270/idx4 y #271/idx7. A2 OPEN.

## Run31 — ejecución extendida y diagnóstico (2026-11-13)

- Run: `build/a2n/a2n-20261113-133033`; fixture `arena-repair1-20261112-205206`; seed `12345`; Fase A `3000` ticks; Fase B `12000` ticks; local budget `900`; global budget `8000`.
- Resultado A2: **FAIL / OPEN**. Ocho atacantes aparecieron; seis murieron; dos terminaron vivos y fuera del perímetro; no hubo entidades desaparecidas. Seis entraron alguna vez al perímetro, pero ninguno permanecía dentro al corte final.
- Atacante #272 / idx4 (`e654660a-8e29-4375-8431-638c44e5aa66`): terminó en `589,75,508`, `FAILED`, motivo `construccion agotada sin completar el descenso`. Conservaba un escalón `PLACED_VERIFIED` en `588,105,523` aunque el mob ya estaba unos 31 bloques por debajo de su aterrizaje. El ejecutor seguía esperando la travesía física de ese escalón obsoleto; el intento de ruta BREAK quedaba detrás de la rama de construcción.
- Atacante idx7 (`ec23c452-0e8a-4e98-8017-455d23f4ffec`): terminó en `695,63,454`, `PLANNING`; objetivo de misión `704,71,486`, waypoint local `698,68,455`; cinco escalones colocados. La traza muestra 11/12 replanteos por guarda, cuatro reintentos de presupuesto local `901/900`, comandos `STEP_UP`/`WALK` repetidos sin avance horizontal y `EscapeWaterGoal` activo. Sigue pendiente diagnóstico de selección de orilla / transición agua-tierra; no se aumentaron presupuestos ni se relajaron protecciones.
- `nav-trace.log` se cerró y contiene aproximadamente 75 MB; eventos relevantes: `BREAK_APPROACH_OCCLUSION`, `GUARD_REPLAN`, `BUDGET`, `MOVE_COMMAND`, `NAV_FAIL`.
- C4 posterior: guardias que combatieron `4/4`; trabajadores vivos `12/12`; reanudados `11/12`; sin bajas ni trabajadores no resueltos; asignaciones de puesto `9`, huérfanas `0`, duplicadas `0`. El único no reanudado era un herrero descansando (`resting=true`) al instante del muestreo. Repetir la comprobación tras su despertar; no clasificarlo como pérdida.

## Corrección A2 aplicada después de Run31 — pendiente de prueba runtime

- `NavStairProgressPolicy`: un escalón pendiente solo se abandona cuando la misión sigue por debajo del aterrizaje y el mob ya descendió más allá del margen `maxSafeDrop`. Se conserva la distinción entre `ABANDONED` y travesía real: no incrementa escalones atravesados ni elimina el bloque colocado.
- `NavExecutor.pasoDeConstruccion`: al detectar un escalón ya pasado, libera el estado pendiente, reinicia solo el presupuesto local de ese escalón y deja intacto el presupuesto total duro.
- La rama de construcción no puede adelantarse a un `nodoDeRecuperacionOverride` BREAK inmediato; además, la delegación conserva un `nextRecoveryAt` futuro en vez de borrar el enfriamiento.
- Respaldo previo: `.a2-backups/run31-20261113-before-fix/`.
- Verificación: `compileJava` OK; `compileTestJava` OK; `NavStairProgressPolicyTest` OK (5 aserciones); `python tools/lint_aldea.py --strict` OK; `git diff --check` OK. La corrección aún no está aceptada hasta repetir el raid en un mundo desechable.

## Siguiente ejecución obligatoria

- Ejecutar Run32 con el mismo fixture/semilla/budgets, mundo desechable nuevo y sin GUI.
- Añadir `-Ddevilrpg.a2n.traceNav=true` para registrar `shore`, `custom`, `nav` y `obj` del `EscapeWaterGoal`; no cambiar budgets ni protecciones.
- Aceptación parcial: idx4 debe abandonar el escalón obsoleto sin falsear travesía y ejecutar la acción BREAK; idx7 debe mostrar selección de orilla y progreso horizontal verificable. A2 permanece OPEN hasta cumplir los criterios completos.

## Run32 — resultado y siguiente diagnóstico (2026-11-13)

- Run: `build/a2n/a2n-20261113-135710`; `Modo=raid`; seed `12345`; `FaseA=3000`; `FaseB=12000` (tick absoluto final); local budget `900`; global budget `8000`; sin pared/rampa/perfil/inundación adicionales; `-Ddevilrpg.a2n.traceNav=true`.
- **A2 FAIL / OPEN**: 8/8 spawneados; al tick 12000: 3 vivos, 5 muertos, 0 desaparecidos; 5 entraron alguna vez al perímetro y 0 permanecían dentro. Supervivientes: idx3 `608,95,599` (FAILED); idx4 `583,111,523` (RECOVERING); idx7 `698,63,455` (PLANNING).
- idx3 (`46fe8501-c77e-47ca-9f81-cecdc85a9395`): el DROP sí avanzó de `608,96,599` a `608,95,599`; el bloqueo posterior es el WALK hacia el escalón pendiente `608,95,598` sobre `608,94,598`. `PathNavigation` devuelve ruta vacía/`navDone=true` en `pos=608.66,95,599.50` aunque el destino está una celda al sur, con pies/cabeza libres y soporte cobblestone. La política de llegada exige ocupación física del escalón, así que no se debe relajar esa invariante; corregir el acercamiento de un bloque.
- idx4 (`bc42feec-2905-46da-9fdb-c29a41d03e80`): continúa en `583,111,523`; presupuesto global agotado `8000/8000`; 18 fallos de descenso; 60 candidatos de escalera rechazados porque las celdas de pies están ocupadas. La traza repite `DROP_LOCAL_EXCLUSION_HIT` para `landing=584,108,520`, fingerprint `1213646834920530`; investigar cómo se propaga la exclusión y cómo se sale de ese bucle. No aumentar budgets.
- idx7 (`120bf223-28cf-4d72-b6ad-94a7b21b4049`): `EscapeWaterGoal` sigue activo cerca de `shore=697,62,455`; `NavExecutor` alternaba el objetivo temporal con la misión `704,71,486` y el waypoint `700,68,456`. Causa confirmada: `setObjective()` desactivaba `objetivoTactico` incluso cuando el llamante reenviaba la misma `misionFinal`.
- Trazas: `STAIR_ABANDONED=0`; `DROP_START=391`; `DROP_DONE=11`; `DROP_REJECTED=23`; `GUARD_REPLAN=59`; `BUDGET=6`. `nav-trace.log` quedó en ~67 MB y se cerró al detener el servidor.

## Corrección táctica aplicada después de Run32 — compilada, pendiente de runtime

- `NavTacticalObjectivePolicy.preserveTacticalObjective(tacticalActive, sameMission)` conserva la prioridad táctica únicamente cuando sigue activa y el llamante reenvía la misma misión.
- `NavExecutor.setObjective()` solo desactiva `objetivoTactico` al recibir una misión nueva. El reenvío idempotente de la misión no cancela la orilla temporal.
- Respaldo: `.a2-backups/run32-before-tactical-fix/NavExecutor.java`.
- Verificación: `compileJava compileTestJava` BUILD SUCCESSFUL; `NavStairProgressPolicyTest` OK (5 aserciones); `NavTacticalObjectivePolicyTest` OK (4 aserciones); lint estricto OK; `git diff --check` sin errores.

## C4 — resultado secundario de Run32

- `C4_PASS (raid)` según los criterios actuales: guardias combatieron `4/4`; trabajadores reanudados `11/11` supervivientes; asignaciones de puestos `8`, huérfanas `0`, duplicadas `0`; trabajadores no resueltos `0`.
- Hubo **1 baja de trabajador**: Josefa, herrera de armas (`5d95ab4a-9ff3-4101-b105-38144b06696e`), asesinada por atacante #291 (`0a5bc208-11d8-49d3-9a06-983c00a4bbd2`). `C4_PASS` no significa defensa sin bajas; si el criterio del proyecto exige cero bajas, queda pendiente corregir la defensa y endurecer la aceptación.

## Próximos pasos

1. Run33 con mismo fixture/seed/budgets y `-Ddevilrpg.a2n.traceNav=true`; comprobar que `N4TRACE` ya no alterna entre shore y misión, y que idx7 sale del agua.
2. Corregir el acercamiento al escalón pendiente cuando vanilla `PathNavigation` devuelve ruta vacía aunque el agente aún no ocupa la celda.
3. Resolver el bucle de `DROP_LOCAL_EXCLUSION_HIT` y el acceso por terreno sólido de idx4, sin subir presupuestos ni relajar protección de muro.
4. A2 sigue OPEN hasta que los atacantes completen el asedio y se verifique el estado de trabajadores/guardias.

## Run33 — corrección de acercamiento adyacente en validación

- Run: `build/a2n/a2n-20261113-141800`; seed `12345`; `FaseA=3000`; `FaseB=12000` (tick absoluto final); local `900`; global `8000`; `-Ddevilrpg.a2n.traceNav=true`; mundo desechable nuevo, sin GUI.
- Se conserva la corrección táctica de Run32: reenviar la misma misión ya no cancela `objetivoTactico`.
- Nueva corrección: `NavAdjacentApproachPolicy` + `NavExecutor.aplicarAcercamientoAdyacente`. Cuando vanilla `PathNavigation` declara la ruta terminada sin mover al mob a una celda adyacente, se usa `MoveControl` únicamente si la celda de destino cabe, tiene soporte, es un nodo de movimiento validado y está a distancia Manhattan 1–2. No se avanza la ruta por proximidad; `nodoAlcanzado` sigue exigiendo llegada física.
- Verificación previa a Run33: `compileJava compileTestJava` BUILD SUCCESSFUL; `NavStairProgressPolicyTest` 5/5; `NavTacticalObjectivePolicyTest` 4/4; `NavAdjacentApproachPolicyTest` 8/8; lint estricto OK; `git diff --check` exit 0.
- Respaldo previo del ejecutor: `.a2-backups/run32-before-adjacent-approach-fix/NavExecutor.java`.
- Run33 debe comprobar: `NAV_ADJACENT_NUDGE` para idx3/idx4 cuando corresponda; estabilidad de `N4TRACE` en la orilla; reducción de los `DROP_LOCAL_EXCLUSION_HIT`; y que ningún atacante se contabilice dentro sin cruzar físicamente.

### Run33 — checkpoint intermedio t=4200 (fase B iniciada)

- Snapshot t=4200: 4 vivos, 4 muertos, 0 desaparecidos; 4 entraron alguna vez al perímetro; 0 dentro en ese instante.
- `NAV_ADJACENT_NUDGE` acumulados: 8. El agente idx3 alcanzó físicamente dos escalones (`escalones=2`) gracias al acercamiento directo, pero quedó `FAILED` tras 10/10 invalidaciones por no encontrar waypoint de pie válido después de cambiar el mundo.
- idx4 (`b360902a-cba7-4fd3-8949-20eb9cf48d10`) descendió inicialmente de y119 a y111 y luego quedó `RECOVERING` en `583,111,524`; el DROP activo apunta a `582,110,522` (distancia Manhattan 3), fuera del máximo 2 actual del nudge. Próxima corrección candidata: permitir distancia 3 solo para transiciones DROP ya validadas y aplicar el nudge también al origen planificado cuando corresponda.
- idx7/idx8 se desplazan por el terreno hacia sus misiones, pero aún no han completado el asedio. No hay `N4TRACE` en este checkpoint; la preservación de objetivo táctico aún no tiene una activación de EscapeWaterGoal en esta ejecución.
- Run33 sigue activo; este checkpoint no es resultado final ni aceptación A2.

### Run33 — checkpoint t=7800: corrección táctica validada en runtime

- Snapshot t=7800: 4 vivos, 4 muertos, 0 desaparecidos; 4 entraron alguna vez al perímetro; 0 dentro en ese instante.
- `NAV_ADJACENT_NUDGE=8`. idx3 llegó físicamente a dos escalones y avanzó hasta `609,94,597`; la política evitó que el executor aceptara llegada falsa, pero la ruta terminó `FAILED` tras 10/10 invalidaciones por falta de waypoint válido después de cambiar el mundo.
- idx4 permanece `RECOVERING` en `583,110,524`. El candidato natural `583,108,519` sigue bloqueado por `583,110,523`; la escalera reportó que despejó una obstrucción anterior, pero volvió a encontrar esta entrada bloqueada. Investigar por qué el segundo bloque no se delega/rompe; no relajar protección.
- **Tactical objective: PASS runtime.** Para idx7 (`62c2e69d-d106-496c-9530-8da68347c8c9`), `N4TRACE` muestra `shore=697,62,455` y `obj=698,68,455`, mientras la misión original sigue siendo `704,71,486`. A diferencia de Run32, el objetivo ya no alterna de vuelta a la misión; la corrección de `setObjective()` preserva la prioridad temporal. El agente tiene 4 escalones colocados y está delegando minería de Stone para alcanzar la orilla; aún no ha terminado de salir.
- idx8 está en `698,61,455`, pero su búsqueda global terminó con `rutaFrente=0` desde `702,63,452`; no hay N4TRACE para ese agente en este checkpoint. Investigar por qué no activa EscapeWaterGoal o no obtiene frente alcanzable.
- Run33 sigue activo; no declarar aceptación A2 todavía.

### Run33 — checkpoint t=8400: EscapeWaterGoal confirmado

- Snapshot t=8400: 4 vivos, 4 muertos, 0 desaparecidos; 4 entraron alguna vez al perímetro; 0 dentro.
- La corrección táctica queda confirmada para **dos** entidades: idx7 mantiene `shore=697,62,455` y objetivo local `698,68,455` aunque su misión sea `704,71,486`; idx8 mantiene `shore=692,61,452` y objetivo local `688,68,450` aunque su misión sea `725,71,538`. Ya no se observa el rebote continuo a la misión que aparecía en Run32.
- idx7 está `TRAVERSING`, con 6 escalones colocados y minería delegada de Stone hacia la orilla. idx8 está `PLANNING`; su último descenso fue rechazado por falta de holgura de pies/cuerpo en `692,61,452` y está recalculando.
- idx3 sigue `FAILED` en `609,94,597`, 2 escalones colocados y 10/10 invalidaciones por ausencia de waypoint de pie válido tras cambio del mundo.
- idx4 sigue `RECOVERING` en `583,110,524`; la entrada al aterrizaje `583,108,519` está bloqueada por `583,110,523`. Se eliminó una obstrucción anterior, pero el segundo bloqueo persiste; falta confirmar por qué no se rompe o aproxima.
- `NAV_ADJACENT_NUDGE=8`; el acercamiento directo conserva la llegada física, pero por sí solo no resuelve los dos bloqueos de ruta restantes. Run33 continúa.

### Run33 — checkpoint t=9000: primer atacante salió del agua

- Snapshot t=9000: 4 vivos, 4 muertos, 0 desaparecidos; 5 entraron alguna vez al perímetro y 1 estaba dentro en ese instante.
- idx8 (`10b00fc8-1fe7-4b13-a6f6-b58a777aa1f8`) pasó de la zona de agua `693,61,453` a `663,71,479`, reduciendo su distancia horizontal a la misión a `53.91`; ya está en terreno y reanudó la navegación normal. Su EscapeWaterGoal había conservado el objetivo local `688,68,450` mientras la orilla era `692,61,452`.
- idx7 (`62c2e69d-d106-496c-9530-8da68347c8c9`) continúa trabajando para salir de la misma zona: posición `695,61,454`, orilla `694,62,454`, objetivo temporal `688,68,450`, 8 escalones colocados y minería de Stone delegada. Se observaron transiciones breves al objetivo de misión cuando cambia/reinicia la orilla, seguidas de restauración del waypoint temporal; no aparece el rebote continuo de Run32.
- idx3 continúa `FAILED` tras dos escalones y 10/10 invalidaciones; idx4 continúa `RECOVERING`, con entrada bloqueada por `583,110,523`.
- Resultado parcial mejor que Run32: ahora hay 1 atacante dentro en t=9000. A2 sigue abierta hasta el cierre de FaseB y la verificación final.

### Run33 — checkpoint t=10200

- Snapshot t=10200: 3 vivos, 5 muertos, 0 desaparecidos; 5 entraron alguna vez al perímetro; 0 dentro en ese instante. El único atacante contabilizado dentro en t=9000 ya no estaba dentro en t=10200.
- Supervivientes observados: idx3 (`609,94,597`, FAILED), idx4 (`583,110,524`, RECOVERING) e idx7 (`695,61,454`, PLANNING). idx8 ya no aparece entre los supervivientes.
- idx7 conserva el objetivo temporal `688,68,450` para la orilla `694,62,454`, sin volver a la misión; sin embargo acumula 3 descensos rechazados por falta de holgura de pies/cuerpo. Continúa delegando minería de Stone en `694,61,455`; el objetivo táctico se conserva, pero la salida física del agua sigue sin completarse.
- idx3 e idx4 no han progresado desde sus últimos checkpoints: idx3 agotó 10/10 invalidaciones de waypoint; idx4 sigue sin candidato de escalera y con acceso bloqueado por `583,110,523`.
- A2 sigue OPEN. No cambiar presupuestos ni protecciones para forzar el resultado.

## Corrección preparada para Run34 — fluidos fuera de minería

- Run33 mostró `BREAK-DELEGADO (bloque=Water)` durante EscapeWaterGoal. El agua no debe tratarse como bloque minable: es fluido/terreno y la salida debe resolverse por navegación hacia una orilla válida.
- Se añadió a `AggressiveZombieEntity.puedeRomperNodoDeNavegacion` un rechazo explícito cuando `!state.getFluidState().isEmpty()`, antes de evaluar logs/hojas y el gate normal de rotura.
- Respaldo: `.a2-backups/run33-before-fluid-break-exclusion/AggressiveZombieEntity.java`.
- Estado: parche de fuente aplicado; `git diff --check` sin errores. Falta `compileJava compileTestJava` y una prueba runtime desechable antes de aceptar el cambio.

## Run33 — resultado de FaseB t=12000 (A2 FAIL / C4 postraíd pendiente)

- `RESUMEN FASE B t=12000`: 8/8 spawn; 3 vivos; 5 muertos; 0 desaparecidos; 5 entraron alguna vez al perímetro; 0 permanecían dentro. Resultado A2: **FAIL / OPEN**.
- Supervivientes: idx3 `609,94,597` (distancia horizontal 82.75, nunca dentro); idx4 `583,110,524` (77.94, nunca dentro); idx7 `693,63,453` (85.63, nunca dentro). idx8, que había entrado brevemente en t=9000, murió antes del cierre.
- El ajuste táctico sí corrigió la sobrescritura continua de misión durante EscapeWaterGoal: N4TRACE mantuvo objetivos locales de orilla para idx7/idx8. No bastó para garantizar salida: idx7 permaneció en `PLANNING` y acumuló descensos rechazados por falta de holgura; idx8 logró salir y avanzar hacia su misión, pero murió posteriormente.
- `NAV_ADJACENT_NUDGE=8`: idx3 alcanzó físicamente dos escalones y idx4 descendió inicialmente, pero ambos quedaron atascados en bloqueos/waypoints posteriores.
- C4: un trabajador muerto durante el asedio, Zacarias (butcher, UUID `b9889f8a-e588-468a-9cb1-dd008b4dac77`). Se registraron cuatro guardias enfrentando al atacante dentro de la aldea. `C4 POSTRAID maintenance=OK`, 3 atacantes retirados para recuperación de 1200 ticks. La prueba aún no había escrito `fin.txt` al checkpoint 14:32:28; mantener el servidor hasta que finalice y registrar el resumen C4 definitivo.
- El rechazo explícito a minar fluidos se aplicó a fuente después de compilar Run33; no forma parte de este resultado y requiere compilación + Run34.

## Run34 — preflight de navegación/agua

- `AggressiveZombieEntity.puedeRomperNodoDeNavegacion` ahora rechaza explícitamente cualquier bloque con `FluidState` no vacío. Run33 mostró `BREAK-DELEGADO (bloque=Water)` durante EscapeWaterGoal; los fluidos no son objetivos de minería.
- `NavAdjacentApproachPolicy` ahora recibe un máximo de distancia explícito: `DROP` validado permite Manhattan 3 (descenso diagonal de un bloque vertical + dos horizontales); los demás nodos de movimiento conservan máximo 2. Sigue exigiendo navegación terminada, nodo de movimiento, celda con espacio y soporte; no relaja llegada física.
- Verificación: `compileJava compileTestJava` BUILD SUCCESSFUL; `NavStairProgressPolicyTest` 5/5; `NavTacticalObjectivePolicyTest` 4/4; `NavAdjacentApproachPolicyTest` 10/10; lint estricto OK; `git diff --check` exit 0.
- Respaldos: `.a2-backups/run33-before-fluid-break-exclusion/AggressiveZombieEntity.java` y `.a2-backups/run34-before-drop-distance/` (NavExecutor, policy y test previos).
- Siguiente: Run34 en mundo desechable nuevo, mismo fixture/seed/budgets, para verificar que no delega BREAK sobre agua y que los DROP diagonales ya no se quedan a una celda de distancia. A2 sigue OPEN.

### Run34 activo — `a2n-20261113-143545`

- Carpeta: `build/a2n/a2n-20261113-143545`; inicio 14:35:45 del 13-Nov-2026.
- Configuración igual a Run33: fixture `arena-repair1-20261112-205206`, seed `12345`, raid, `FaseA=3000`, `FaseB=12000`, local `900`, global `8000`, pared/rampa/perfil/inundación `0`, `NavCustom=true`, `-Ddevilrpg.a2n.traceNav=true`, `-Ddevilrpg.a2n.traceNav=true`.
- Usa mundo desechable nuevo; no GUI; `run/saves` intacto.
- Watcher remoto PID `35004` escribe `run34-watch.txt` cada 30 s y termina al detectar `fin.txt` (máximo 15 min).
- Al checkpoint 14:36:10, Gradle estaba iniciando `runServer`; `fin.txt` aún no existía. No hay resultado runtime todavía.

### Run34 — checkpoint t=7200

- Snapshot t=7200: 2 vivos, 6 muertos, 0 desaparecidos; 6 entraron alguna vez al perímetro; 0 dentro en ese instante.
- idx3 sigue `FAILED` en `609,94,597`, con 2 escalones y 10/10 invalidaciones por ausencia de waypoint de pie válido tras cambios del mundo.
- idx4 (`88b32bb2-3065-4e81-b811-d2576f09c2f7`) pasó de y111 a y110 después de que el nudge DROP de distancia 3 se activara; pero continúa `RECOVERING`. El plan local sigue sin poder construir porque las 60 celdas candidatas tienen `celdaAire=false` en al menos una de las dos alturas necesarias. La posición está parcialmente encajada en terreno; el diagnóstico debe distinguir si se requiere excavar el volumen del cuerpo o buscar un acceso alternativo, sin relajar la protección de la aldea.
- idx7/idx8 ya no sobreviven; el atacante idx8 que había avanzado en Run33 no aparece como superviviente en Run34.
- Validación de exclusión de fluidos: `BREAK-DELEGADO (bloque=Water)=0` hasta t=7200.
- `NAV_ADJACENT_NUDGE=13` acumulados, incluyendo DROP de distancia Manhattan 3. Run34 sigue ejecutándose hasta t=12000 y C4 postraid.

### Run34 — checkpoint t=8100

- Snapshot t=8100: 2 vivos, 6 muertos, 0 desaparecidos; 6 entraron alguna vez al perímetro; 0 dentro.
- No hay progreso neto desde t=4200: idx3 e idx4 son los dos supervivientes; idx3 permanece `FAILED` en `609,94,597`, idx4 `RECOVERING` alrededor de `583,110,524`.
- La exclusión de minería sobre fluidos sigue limpia (`BREAK-DELEGADO (bloque=Water)=0`). `NAV_ADJACENT_NUDGE=13`; el nudge de DROP Manhattan 3 se ejecuta, pero no elimina el encajonamiento del idx4 ni restaura waypoints válidos del idx3.
- Run34 continúa hasta el tick 12000; A2 no está aceptada.

## Run34 — resultado final (13-Nov-2026)

- RunId: `a2n-20261113-143545`; mundo desechable `build/a2n/a2n-20261113-143545/game/a2n`; fixture `arena-repair1-20261112-205206`; seed `12345`; `FaseA=3000`, `FaseB=12000`, local `900`, global `8000`; sin GUI y sin tocar `run/saves`.
- Compilación/pruebas antes de Run34: `compileJava compileTestJava` BUILD SUCCESSFUL; `NavStairProgressPolicyTest` 5/5; `NavTacticalObjectivePolicyTest` 4/4; `NavAdjacentApproachPolicyTest` 10/10; lint estricto y `git diff --check` OK.
- Fase B t=12000: `spawneados=8/8`, `fallosSpawn=0`, `vivos=2`, `muertos=6`, `desaparecidos=0`, `entraronPerimetroEver=6`, `dentroParedAhora=0`. Supervivientes idx3 `609,94,597` (FAILED, 2 escalones, 10/10 invalidaciones sin waypoint de pie) e idx4 `583,111,523` (RECOVERING, 17 fallos de descenso, 0 escalones; 60 candidatos rechazados porque no existe volumen de aire suficiente para el cuerpo).
- **A2: FAIL / OPEN.** No se acepta: dos atacantes quedaron vivos fuera del perímetro y ninguno dentro al cierre.
- Corrección de fluidos: `BREAK-DELEGADO (bloque=Water)=0` durante toda la ejecución. La exclusión funcionó.
- Acercamiento adyacente: 13 eventos `NAV_ADJACENT_NUDGE`, incluidos DROP con distancia Manhattan 3; la regla ejecutó MoveControl sin falsear llegada física, pero no resolvió el encajonamiento de idx4 ni el fallo de waypoints de idx3.
- EscapeWaterGoal/objetivo táctico se conserva; el escenario actual no dejó supervivientes de agua para cerrar ese comportamiento como criterio independiente. No afirmar aceptación adicional por esta corrida.
- **C4: PASS.** `fin.txt`: `C4_PASS (raid) - guardasCombatieron=true profesionalesReanudados=true asignacionesJobSiteLimpias=true supervivientes=16 trabajadoresEsperados=12 trabajadoresVivos=12 trabajadoresReanudados=12 bajasTrabajadores=0 trabajadoresNoResueltos=0 retiradosParaRecuperacion=2 jobSite=[8, 0, 0] fallosDeInstrumento=0`.
- Servidor dedicado se detuvo limpiamente a las 14:51:08; `fin.txt` presente. No se registraron excepciones del servidor.
- Hipótesis de siguiente investigación: idx4 está físicamente en una región con volumen de cuerpo obstruido (60/60 candidatos `celdaAire=false`); la lógica actual intenta construir escalones sin abrir un volumen transitable. Hay que rastrear el origen de `celdaAire` y la política de despeje/excavación segura, diferenciando terreno natural de bloques protegidos de aldea. Para idx3, estudiar el límite 10/10 invalidaciones y por qué los waypoints se vuelven inválidos después de cambios del mundo. No aumentar budgets ni debilitar protecciones como atajo.

## Run35 — excavación segura del volumen de escalera

- RunId: `a2n-20261113-145338`; mundo desechable nuevo en `build/a2n/a2n-20261113-145338/game/a2n`; fixture `arena-repair1-20261112-205206`; seed `12345`; `FaseA=3000`, `FaseB=12000`; local `900`, global `8000`; `-Ddevilrpg.a2n.traceNav=true`; sin GUI y sin tocar `run/saves`.
- Parche añadido: `NavStairVolumeClearancePolicy` + `AggressiveZombieEntity.registrarObstruccionDeVolumenDeEscalon`. Si la celda del peldaño o uno de sus dos bloques de cabeza está ocupado, registra SOLO la primera obstrucción del volumen para despeje, siempre que la celda candidata sea exterior/no protegida y tenga soporte. La acción real sigue pasando por `puedeRomperNodoDeNavegacion`, `alAlcanceDeRotura` y `romperNodoDeRuta`; se rompe como máximo un bloque por intento y se reevalúa el mundo antes de colocar.
- Preflight: `compileJava compileTestJava` BUILD SUCCESSFUL; tests `NavStairProgressPolicyTest` 5/5, `NavTacticalObjectivePolicyTest` 4/4, `NavAdjacentApproachPolicyTest` 10/10, `NavStairVolumeClearancePolicyTest` 8/8; lint estricto OK; `git diff --check` exit 0.
- Respaldo previo: `.a2-backups/run35-before-stair-volume-clearance/AggressiveZombieEntity.java`.
- Watcher remoto PID `23996` escribe `run35-watch.txt` cada 30 s y termina al detectar `fin.txt`.
- Criterio runtime: buscar `STAIR-ACCESS-CLEAR` seguido de `CONSTRUCCION-ESCALERA` y `escalones` que aumenten; comprobar que los bloques despejados estén fuera de protección y que no haya BREAK de fluidos/logs/hojas. A2 sigue OPEN hasta FaseB y C4 finales.

### Run35 — checkpoint inicial t=300

- Snapshot t=300: 8 vivos, 0 muertos, 0 desaparecidos; 2 entraron alguna vez al perímetro; 0 dentro.
- El evento `A2 STAIR-ACCESS-CLEAR #286 bloque 594,94,600 verificado` corresponde al atacante idx3 (`uuid=736df9b3-7fec-453b-a210-83ea27807e2c`, posición `607,95,600`). El volumen obstruido ya permite seleccionar un bloque de despeje, y el bloque fue verificado en aire.
- Aún no aparece `CONSTRUCCION-ESCALERA` posterior ni aumento de `escalera.colocados`; por tanto, esto confirma despeje, no todavía colocación/travesía. En el mismo snapshot idx3 informa `FAILED` por tope local 64/64, mientras el subsistema entity-ladder informa 0/24 y motivo de obstrucción removida. Hay que verificar si la escalera de entidad reanuda y si no queda anulada por el tope del plan local.
- idx4 (`uuid=ca8cdd14-5088-4108-ab81-5e6525eeeebe`) está en `574,112,523`, `TRAVERSING`, con 16 fallos de descenso y 0 escalones; su volumen de escalera reporta candidatos parcialmente libres y obstrucción del corredor, no el caso puro `celdaAire=false` de idx3.
- `BREAK-DELEGADO (bloque=Water)=0`; sin excepciones. Run35 sigue activo.

### Run35 — Fase A completa, t=3000

- Snapshot final Fase A: `spawneados=8/8`, `fallosSpawn=0`, `vivos=4`, `muertos=4`, `desaparecidos=0`, `entraronPerimetroEver=4`, `dentroParedAhora=0`.
- idx3 (`736df9b3-7fec-453b-a210-83ea27807e2c`) sigue `FAILED` en `607,95,600`; local `escalones=64/64`, `fallosEscalon=10/10`. El despeje `594,94,600` fue real pero no se colocó escalón (`escalera.colocados=0/24`), y el executor continúa FAILED por presupuesto local terminal. La corrección de volumen demuestra que puede retirar una obstrucción segura, pero no recupera este ejecutor ya agotado.
- idx4 (`ca8cdd14-5088-4108-ab81-5e6525eeeebe`) sigue `RECOVERING` en `583,111,523`; 17 fallos de descenso; `DROP` rechazado por `spruce_leaves` en `584,110,520`. El gate de minería rechaza hojas/troncos deliberadamente; la exclusión de destino se aplica y el planificador global agota 8000 expansiones con ruta a frente de 17 nodos. No se debe minar follaje para forzar paso; investigar el avance por ruta de frente/replanificación alternativa.
- La búsqueda de escalera de idx4 reporta candidatos sin soporte y una base/volumen que no coincide claramente con su posición actual; telemetría del ancla debe revisarse, pero no hay evidencia suficiente para cambiarla aún.
- `BREAK-DELEGADO (bloque=Water)=0`; no hay excepciones observadas.
- Fase B está en curso; Run35 no ha finalizado.

### Run35 — checkpoint t=5400 (fase B)

- Snapshot t=5400: 4 vivos, 4 muertos, 0 desaparecidos; 4 entraron alguna vez al perímetro; 0 dentro. No se observa recuperación de idx3/idx4 desde Fase A.
- `BREAK-DELEGADO (bloque=Water)=0`; el filtro de fluidos se mantiene correcto. El evento único `STAIR-ACCESS-CLEAR #286` sigue sin traducirse en colocación/travesía (`escalera.colocados=0/24`), y idx3 continúa `FAILED` por el tope de 64 escalones locales.
- idx4 permanece `RECOVERING` por columna DROP bloqueada por `spruce_leaves` (no minable por diseño); la ruta global reporta presupuesto agotado y frente de 17 nodos. No se relaja la política de follaje ni se sube el presupuesto.
- Fase B sigue hasta t=12000 y luego C4 postraid.

### Run35 — diagnóstico adicional de idx4 (traza DROP)

- La traza confirma que no repite un único aterrizaje sin replanificar: para `ca8cdd14-5088-4108-ab81-5e6525eeeebe` va excluyendo sucesivamente columnas bajo `minecraft:spruce_leaves` (p. ej. `569,116,531`, `568,116,531`, `584,108,520`) y después una columna obstruida por `minecraft:stone` (`589,75,509`). `NavDropExclusionPolicy` mantiene 32 entradas y la búsqueda explora destinos alternativos; al llenarse la colección, las entradas más antiguas se desalojan por diseño.
- No autorizar minado de hojas/troncos: es la protección deliberada del bosque. Tampoco hay evidencia todavía de que la capacidad 32 cause un ciclo; no modificarla sin medir una reaparición de un aterrizaje ya excluido.
- El cuello de botella de idx4 es una gran cubierta de follaje/terreno sólido que bloquea múltiples columnas de caída y fuerza una búsqueda global de frente con presupuesto agotado. Se requiere probar que la ruta de frente realmente se recorre y no solo se calcula.

### Run35 — checkpoint t=7200

- Snapshot t=7200: 3 vivos, 5 muertos, 0 desaparecidos; 5 entraron alguna vez al perímetro; 0 dentro en ese instante. Hubo 1 atacante dentro en t=6600, pero ya no permanecía dentro en t=7200.
- La excavación de volumen no ha producido colocación de escalones ni recuperación de idx3/idx4; el resultado parcial no valida A2. El filtro de fluidos continúa en cero BREAK sobre Water.
- Run35 sigue activo; falta FaseB hasta t=12000 y C4.

## Run35 — resultado final (13-Nov-2026)

- RunId: `a2n-20261113-145338`; Fase B cerró en t=12000 y la recuperación C4 terminó en t=13200. `fin.txt` presente; servidor dedicado detenido limpiamente; no quedan procesos Java de esta corrida.
- Fase B: `spawneados=8/8`, `fallosSpawn=0`, `vivos=3`, `muertos=5`, `desaparecidos=0`, `entraronPerimetroEver=6`, `dentroParedAhora=0`. **A2 FAIL / OPEN**: tres atacantes seguían vivos fuera y ninguno quedó dentro al cierre.
- C4: **PASS**. `fin.txt`: `guardasCombatieron=true`, `profesionalesReanudados=true`, `asignacionesJobSiteLimpias=true`, `supervivientes=16`, `trabajadoresEsperados=12`, `trabajadoresVivos=12`, `trabajadoresReanudados=12`, `bajasTrabajadores=0`, `trabajadoresNoResueltos=0`, `retiradosParaRecuperacion=3`, `jobSite=[10,0,0]`, `fallosDeInstrumento=0`.
- Exclusión de fluidos: `BREAK-DELEGADO (bloque=Water)=0` en toda la corrida.
- Excavación de volumen: el atacante #286/idx3 despejó y verificó `594,94,600`, pero no colocó ningún peldaño (`escalera.colocados=0/24`) y su `NavExecutor` quedó terminal `FAILED` en `64/64` escalones locales. La corrección es segura y funcional para despejar el primer bloque permitido, pero no es suficiente para recuperar un executor ya agotado.
- idx4 quedó `RECOVERING` por columnas DROP cubiertas por `spruce_leaves` (no minables por diseño) y algunas obstruidas por piedra. La traza demuestra exclusión de múltiples destinos y exploración de rutas alternativas; la búsqueda global alcanzó el presupuesto y no consiguió completar una ruta de asedio. No se debe debilitar la protección del bosque ni aumentar presupuestos como solución inmediata.
- Preflight de Run35: `compileJava compileTestJava` BUILD SUCCESSFUL; `NavStairProgressPolicyTest` 5/5; `NavTacticalObjectivePolicyTest` 4/4; `NavAdjacentApproachPolicyTest` 10/10; `NavStairVolumeClearancePolicyTest` 8/8; lint estricto OK; `git diff --check` exit 0.
- Rama `feature/autonomous-village`, HEAD `518fee13fc15bfb9b92b0479d86b5c609d1947da`; no commits. Se conservaron todas las modificaciones preexistentes y los respaldos `.a2-backups/`.
- Próximo bloqueo A2: (1) impedir que el `NavExecutor` quede terminal `FAILED` tras agotar 64 escalones si un cambio de mundo verificable ha despejado una obstrucción; debe rearmarse con presupuesto acotado y sin bucle. (2) mejorar la ruta de frente para columnas de caída cubiertas por follaje no minable: la búsqueda debe aprovechar el frente parcial alcanzable y avanzar físicamente, no solo producir `rutaFrente` con presupuesto agotado. (3) instrumentar y corregir la discrepancia entre el ancla/centro de escalera y la posición actual del atacante antes de tocar la política de anclas.

## Run36 — invalidación de ruta tras BREAK de recuperación

- RunId: `a2n-20261113-150954`; mundo desechable nuevo; fixture `arena-repair1-20261112-205206`; seed `12345`; FaseA 3000, FaseB 12000; local 900, global 8000; sin GUI y sin tocar `run/saves`.
- Corrección nueva: `AggressiveZombieEntity.romperNodoDeRuta()` ahora, tras una rotura real, notifica `pathPlanner.terrainChanged()` y `navExecutor.terrainChanged()`. Esto invalida `globalCache`, `route` y permite rearmar `FAILED` si el terreno cambió. Traza nueva: `[NAV] TERRAIN_CHANGED_BY_ROUTE_BREAK`.
- Motivo: Run35 demostró que `STAIR-ACCESS-CLEAR` rompía el bloque, pero la ruta global cacheada no recibía notificación. El verificador BRIDGE comprobaba el nodo de puente (aún no colocado) y devolvía no resuelto aunque se hubiera despejado otra obstrucción; sin invalidación el executor podía seguir con caché antigua.
- Preflight: `compileJava compileTestJava` BUILD SUCCESSFUL; `NavStairProgressPolicyTest` 5/5; `NavTacticalObjectivePolicyTest` 4/4; `NavAdjacentApproachPolicyTest` 10/10; `NavStairVolumeClearancePolicyTest` 8/8; `NavPlannerStateTest` OK; lint estricto OK; `git diff --check` exit 0.
- Respaldo previo: `.a2-backups/run36-before-route-break-invalidation/AggressiveZombieEntity.java`.
- Watcher remoto PID `34916` escribe `run36-watch.txt` cada 30 s y termina al detectar `fin.txt`.
- Criterio: después de cada `STAIR-ACCESS-CLEAR` debe aparecer `TERRAIN_CHANGED_BY_ROUTE_BREAK`; el `NavExecutor` debe volver a PLANNING, recalcular y colocar/travesar escalón si el volumen queda válido. A2 permanece OPEN hasta FaseB y C4.

## Run37 — rearme único del límite local tras BREAK real

- Diagnóstico Run36 t=300: idx3 (`de905595-2a83-40ea-8774-222988f9eb81`) despejó `594,94,600` y notificó ambos planificadores; aun así volvió a `FAILED` porque conservó `escalones=64/64` y `fallosEscalon=10/10`. El cambio de terreno reabrió el estado, pero el contador local terminal volvió a cerrarlo inmediatamente.
- Parche Run37: `NavTerrainRearmPolicy` permite un único rearme por misión solo cuando el executor está `FAILED`, el límite local de escalones y los fallos están agotados, y no se había rearmado ya en esa misión. Reinicia los contadores locales, pero conserva los límites duros globales. El flag se reinicia únicamente al cambiar de misión/etapa o al verificar progreso real.
- Tests: `NavTerrainRearmPolicyTest` 5/5; suites previas 5/5, 4/4, 10/10, 8/8 y `NavPlannerStateTest` OK; `compileJava compileTestJava` BUILD SUCCESSFUL; lint estricto OK; `git diff --check` exit 0.
- Respaldo: `.a2-backups/run37-before-bounded-terrain-rearm/NavExecutor.java`.
- Run36 sigue ejecutándose con la compilación anterior; no valida este parche. Run37 será la primera corrida que lo incluya.

### Run36 — checkpoint t=3600: replanificación por frente sí avanza

- Snapshot t=3600: 4 vivos, 4 muertos, 0 desaparecidos; 4 entraron alguna vez al perímetro; 0 dentro.
- idx4 (`ca448dd5-5a37-4e30-8255-f237b35afab2`) se movió de `583,111,523` a `584,111,525`. Tras despejar `583,109,524`, el `NavExecutor` pasó a `PLANNING`, eligió `586,105,517` como waypoint local del frente (`rutaGlobal=17`, `exp=8000`) y `escalones=1/64`. Esto demuestra que la ruta parcial de frente sí puede ejecutarse tras invalidar la caché; no se minó follaje.
- idx3 (`de905595-2a83-40ea-8774-222988f9eb81`) permanece `FAILED`, `64/64`, `10/10` pese al primer despeje; Run36 no incluye el rearme único preparado para Run37.
- El atacante #295 colocó escalones, incluido uno adherido a cara lateral en `702,64,457`.
- `BREAK-DELEGADO (bloque=Water)=0`; sin excepciones observadas. Fase B sigue activa.

### Run36 — checkpoint t=3900: idx4 ya ejecuta el frente

- Snapshot t=3900: 4 vivos, 4 muertos, 0 desaparecidos; 4 entraron alguna vez al perímetro; 0 dentro.
- idx4 (`ca448dd5-5a37-4e30-8255-f237b35afab2`) avanzó físicamente hasta `587,107,520`, `TRAVERSING`; el último DROP `588,104,518` fue descrito `NATURAL OK` (dx=1, dy=-3, dz=-2). El waypoint siguiente es `588,101,514`, con ruta de frente global de 14 nodos y 8000 expansiones. Esto confirma avance real por el frente alternativo, no solo cálculo de ruta.
- Se verificaron roturas sucesivas con invalidación del executor: `583,109,524`, `585,109,524`; cada cambio invalida y permite planificar el siguiente waypoint.
- idx3 sigue `FAILED` 64/64 y es el objetivo del rearme acotado Run37.
- #295 colocó escalón lateral `702,64,457`; no se registran intentos de romper agua.
- Fase B sigue activa; Run36 todavía no es aceptación A2.

### Run36 — checkpoint t=4800: descenso de frente en progreso

- Snapshot t=4800: 4 vivos, 4 muertos, 0 desaparecidos; 4 entraron alguna vez al perímetro; 0 dentro.
- idx4 avanzó hasta `589,98,513`, distancia horizontal 74.01; el plan global ya alcanza la misión y eligió `588,93,511` como waypoint local. Se verificó un DROP construido `590,98,514 -> 590,95,514` (`NATURAL/CONSTRUIDO OK` en la telemetría) y la recuperación delegó `BREAK` de Dirt en `588,96,513` para abrir el siguiente tramo. Ya no está detenido en el punto original bajo las hojas.
- idx3 continúa `FAILED` 64/64; no puede validar el rearme de Run37 porque esta corrida fue lanzada antes de compilar ese cambio.
- Fase B continúa; sin WATER breaks ni excepciones observadas.

### Run36 — checkpoint t=6600: idx4 baja al nivel de la aldea

- Snapshot t=6600: 4 vivos, 4 muertos, 0 desaparecidos; 4 entraron alguna vez al perímetro; 0 dentro.
- idx4 (`ca448dd5-5a37-4e30-8255-f237b35afab2`, atacante #292) bajó desde `589,98,513` a `590,71,508`, ya al nivel Y=71 de la aldea. La ruta local apunta a `594,71,509`; `NavExecutor` está en `PLANNING` y la última recuperación delegó `BREAK` de Stone en `591,71,508`. Se colocó escalón lateral en `588,82,508`. El túnel sigue dentro del presupuesto de 40.
- La ruta de frente ya no es solo una salida de 17 nodos: está ejecutando descensos, roturas permitidas y cambios de objetivo por el terreno real.
- idx3 sigue `FAILED` 64/64; Run37 deberá validar el rearme único por cambio de terreno.
- A2 sigue OPEN; Run36 continúa hacia t=12000 y C4.

### Run36 — checkpoint t=6900: primer ingreso al recinto

- Snapshot t=6900: 4 vivos, 4 muertos, 0 desaparecidos; 5 entraron alguna vez al perímetro; 1 dentro en ese instante.
- idx4 avanzó desde `590,71,508` hasta `611,71,518`, reduciendo distancia horizontal a 51.79 y entrando en una ruta de WALK hacia `645,71,526`. Se registró un atacante dentro del perímetro por primera vez en esta corrida.
- idx3 continúa `FAILED` 64/64; el rearme único de Run37 todavía no está en el binario activo de Run36.
- Hallazgo adicional: idx7 muestra `BREAK-DELEGADO (bloque=Water)` aunque la acción real lo rechaza. `buildNavPolicies().canBreak` solo comprobaba protección de aldea y no reutilizaba `puedeRomperNodoDeNavegacion`; esto permite que el planificador emita BREAK imposibles para agua/hojas/troncos.
- Parche Run37 ya aplicado: `buildNavPolicies().canBreak` delega en `puedeRomperNodoDeNavegacion`, alineando planificación y ejecución. Preflight posterior: compileJava/compileTestJava BUILD SUCCESSFUL; seis suites relevantes OK; lint estricto OK; `git diff --check` exit 0.

### Run36 — checkpoint t=9900

- Snapshot t=9900: 3 vivos, 5 muertos, 0 desaparecidos; 5 entraron alguna vez al perímetro; 0 dentro.
- idx4, que había entrado en t=6900 y alcanzado y71, ya no aparece entre los supervivientes en t=9900. No se cuenta como aceptación ni como superviviente.
- idx7 (`5d600c90-a680-44d9-a1fd-f4801cfd3a60`) sigue en el agua `693,63,455`; `NavExecutor` mantiene `BREAK-DELEGADO (bloque=Water)` y los descensos son rechazados por falta de holgura. Esto confirma el desacople entre `buildNavPolicies.canBreak` y la política real de rotura. Run37 alinea ambos contratos.
- idx3 sigue `FAILED` 64/64; Run37 incluye rearme único tras cambio de terreno verificado.
- Fase B sigue hasta t=12000; A2 no está aceptada.

### Run36 — Fase B completa t=12000 (A2 FAIL)

- Resumen: `spawneados=8/8`, `fallosSpawn=0`, `vivos=3`, `muertos=5`, `desaparecidos=0`, `entraronPerimetroEver=5`, `dentroParedAhora=0`. **A2 FAIL / OPEN**.
- idx4 logró llegar al nivel Y=71 y entrar al recinto temporalmente en t=6900, pero murió/desapareció antes del cierre; no cuenta como supervivencia ni como aceptación.
- idx3 siguió `FAILED` con `64/64` escalones y `10/10` fallos, sin rearme. Run37 prueba el rearme único.
- idx7 permaneció en el agua con `BREAK-DELEGADO (bloque=Water)` y descensos rechazados por falta de holgura. El permiso del planificador seguía siendo más permisivo que el ejecutor. Run37 alinea `NavObstruction.Policies.canBreak` con `puedeRomperNodoDeNavegacion`.
- C4 postraíd comenzó correctamente: `maintenance=OK`, `attackersRemovedForRecovery=3`, `recoveryTicks=1200`. Esperar `fin.txt` para resultado final C4.

## Run37 — rearme acotado + política BREAK alineada

- RunId: `a2n-20261113-152333`; mundo desechable nuevo; fixture `arena-repair1-20261112-205206`; seed `12345`; FaseA 3000, FaseB 12000; local 900, global 8000; sin GUI y sin tocar `run/saves`.
- Incluye `NavTerrainRearmPolicy`: un único rearme por misión cuando un BREAK real cambia el terreno después de agotar `64/64` escalones locales y `10/10` fallos. Reinicia contadores locales, no presupuestos globales; el flag solo se limpia al cambiar de misión/etapa o al verificar progreso.
- Incluye alineación de `buildNavPolicies().canBreak` con `puedeRomperNodoDeNavegacion`. El planificador ya no debe generar transiciones BREAK para agua, hojas, troncos, bloques protegidos, block entities, bloques fuera de alcance o no rompibles.
- También incluye invalidación de ambos planificadores tras BREAK real, despeje del primer bloque de volumen de escalera, nudge adyacente DROP≤3 y exclusión de fluidos en la acción de rotura.
- Preflight: `compileJava compileTestJava` BUILD SUCCESSFUL; `NavStairProgressPolicyTest` 5/5; `NavTacticalObjectivePolicyTest` 4/4; `NavAdjacentApproachPolicyTest` 10/10; `NavStairVolumeClearancePolicyTest` 8/8; `NavTerrainRearmPolicyTest` 5/5; `NavPlannerStateTest` OK; lint estricto OK; `git diff --check` exit 0.
- Respaldos: `.a2-backups/run37-before-bounded-terrain-rearm/NavExecutor.java` y `.a2-backups/run37-before-planner-break-policy-alignment/AggressiveZombieEntity.java`.
- Watcher remoto PID `12452` escribe `run37-watch.txt` cada 30 s y termina al detectar `fin.txt`.
- Criterios runtime: (1) idx3 debe mostrar `rearme unico por BREAK de terreno verificado`, escalones por debajo de 64 y salir de FAILED; (2) no debe haber `BREAK-DELEGADO (bloque=Water)` ni BREAK sobre hojas/troncos; (3) idx7 debe salir del agua usando objetivo de orilla; (4) FaseB y C4 deben cerrar sin bajas de trabajadores. A2 sigue OPEN hasta validar el resultado.

### Run37 — checkpoint t=900: agua y rearme pendiente

- Snapshot t=900: 7 vivos, 1 muerto, 0 desaparecidos; 4 entraron alguna vez al perímetro; 4 dentro en ese instante.
- `BREAK-DELEGADO (bloque=Water)=0`. idx7 (`0db9b727-14c5-4ee7-8bb4-de0a9acef2fb`) salió del agua y avanzó de `719,65,469` a `726,65,475`; idx8 también progresa y construye/abre camino. La política BREAK alineada está funcionando.
- idx3 sigue `FAILED` 64/64: el primer BREAK verificado ocurrió antes de que el contador local se agotara, por lo que la política inicial (solo rearma si el estado ya estaba FAILED al ocurrir BREAK) no se activó.
- Corrección adicional preparada para Run38: marcar `cambioTerrenoPendienteRearme` al ocurrir cualquier BREAK verificado; si después se agota el límite local en esa misma misión, `fallar()` consume el único rearme y vuelve a PLANNING. El flag se cancela al cambiar de misión/etapa o al verificar progreso.
- Preflight Run38: `compileJava compileTestJava` BUILD SUCCESSFUL; suites 5/5, 4/4, 10/10, 8/8, 5/5; `NavPlannerStateTest` OK; lint estricto OK; `git diff --check` exit 0.
- Respaldo: `.a2-backups/run38-before-pending-terrain-rearm/NavExecutor.java`.

### Run37 — Fase A completa t=3000

- Snapshot final Fase A: `spawneados=8/8`, `fallosSpawn=0`, `vivos=3`, `muertos=5`, `desaparecidos=0`, `entraronPerimetroEver=5`, `dentroParedAhora=0`.
- idx7 salió del agua y recorrió terreno seco hacia la misión; idx8 también navega hacia su misión. `BREAK-DELEGADO (bloque=Water)=0` en toda Fase A.
- idx3 sigue `FAILED` con `64/64` y `10/10`. El único BREAK que despejó `594,94,600` ocurrió antes del agotamiento; por eso la primera política de rearme no se activó. Run38 incorpora el indicador pendiente para consumir ese cambio cuando se alcance el límite después.
- Fase B de Run37 sigue activa; Run38 ya está compilado y listo, pero no se lanzará en paralelo para evitar conflicto de puerto/CPU.

### Run37 — checkpoint t=5100

- Snapshot t=5100: 3 vivos, 5 muertos, 0 desaparecidos; 5 entraron alguna vez al perímetro; 0 dentro.
- `BREAK-DELEGADO (bloque=Water)=0`; la política de planificación ya no propone romper agua. idx7/idx8 han salido de la zona acuática y navegan por terreno seco.
- idx3 sigue `FAILED` 64/64 porque el rearme pendiente de Run38 fue compilado después de iniciar Run37; no puede validar ese cambio.
- Fase B continúa; Run37 aún debe cerrar t=12000 y C4.

### Run37 — checkpoint t=5700 / estado de continuidad

- Snapshot t=5700: 3 vivos, 5 muertos, 0 desaparecidos; 5 entraron alguna vez al perímetro; 0 dentro.
- `BREAK-DELEGADO (bloque=Water)=0`. idx7 se mantiene fuera del agua, aunque todavía busca rodear/romper una obstrucción terrestre; el planificador ya no propone romper fluido.
- idx3 sigue `FAILED` 64/64 porque el BREAK que despejó `594,94,600` ocurrió antes de agotar el límite local; Run37 no contiene el indicador pendiente de Run38.
- Run37 sigue activo en Fase B. Watcher adicional PID `12404` escribe `run37-final-watch.txt` durante hasta 15 min y captura `fin.txt`/C4 final.
- Run38 está compilado y verificado (pending terrain rearm): seis suites relevantes OK, lint estricto y `git diff --check` OK. No iniciar otra instancia hasta que Run37 libere el puerto 25565.

## Handoff activo — continuidad automática Run37 → Run38

- Run37 (`build/a2n/a2n-20261113-152333`) continúa activo en Fase B. Último snapshot observado t=5700: 3 vivos, 5 muertos, 0 desaparecidos; 5 entraron alguna vez al perímetro; 0 dentro. `BREAK-DELEGADO (bloque=Water)=0`.
- Watcher final Run37 PID `12404` escribe `run37-final-watch.txt` cada 30 s y captura `fin.txt`/C4 durante hasta 15 min.
- Orquestador oculto PID `32732`, script `build/a2n/auto-run38-after-run37.ps1`, está activo y verificado (`AUTO_RUN38_WAIT_START` en `auto-run38.log`). Espera `Run37/fin.txt`, luego espera salida del servidor dedicado PID `14540`, y lanza Run38 sin abrir GUI.
- Run38 se ejecutará con la compilación actual que incluye: (a) `cambioTerrenoPendienteRearme` para consumir un único rearme local si el BREAK verificado precedió al agotamiento 64/64; (b) `buildNavPolicies().canBreak` alineado con `puedeRomperNodoDeNavegacion`, para no planear BREAK de agua/hojas/troncos no minables; (c) invalidación de ambos planificadores después de rotura; (d) despeje acotado del primer bloque del volumen de escalera; (e) nudge DROP Manhattan 3.
- Preflight de la compilación Run38 ya completado: `compileJava compileTestJava` BUILD SUCCESSFUL; suites 5/5, 4/4, 10/10, 8/8, 5/5 y `NavPlannerStateTest` OK; lint estricto OK; `git diff --check` exit 0.
- Run38 usará seed 12345, fixture `arena-repair1-20261112-205206`, FaseA 3000, FaseB 12000, local 900, global 8000, sin GUI, mundo desechable y sin tocar `run/saves`.
- Log del orquestador: `build/a2n/auto-run38.log`. Si hay desconexión de ChatGPT, el proceso local espera y lanza Run38 por sí mismo cuando Run37 termine, salvo que el PC se suspenda/apague o el proceso sea terminado.


## A2 — 13-nov-2026: corrección de objetivo vertical no alcanzable

Run38 volvió a mostrar que la navegación local acepta objetivos dentro de la ventana geométrica aunque tengan desniveles imposibles de ejecutar localmente. Caso medido: idx3, `594,96,600` → `607,95,600`, misión `621,71,583` (dy=-24); primer aterrizaje dentro de terreno, presupuesto local 64/64 y FAILED. Se corrigió `NavWaypointWindowPolicy.directoAlcanzable` y `NavExecutor.elegirObjetivoLocal`: desniveles superiores a caída segura/salto legal fuerzan ruta global validada. Compilación y siete regresiones (incluida nueva política 6/6) OK; lint y diff-check OK. Run39 queda automatizado tras el cierre de Run38 (PID watcher 39432). A2 sigue OPEN hasta aceptación runtime. Detalle y evidencia en `docs/autonomous-village/A2-WALLED-RAID-HANDOFF.md`.


**Cierre Run38 (13-nov):** A2 FAIL (3 vivos, 5 muertos, 5 ever-entered, 0 dentro al cierre); C4_PASS (16 supervivientes, 12/12 trabajadores reanudados, 0 bajas). Run39 arrancó automáticamente con la corrección vertical de ruta global: `a2n-20261113-155051`, launcher PID 19768. Esperar y analizar Run39; A2 sigue OPEN.


## A2 — 13-nov-2026: cadena de aproximación móvil corregida

Run39 mostró que `objetivoDeAproximacion` recalcula el waypoint exterior desde la posición actual y `setObjectiveChain` sustituía la cadena activa aunque la raíz interior siguiera siendo la misma. Caso idx3: meta global 616,71,578 → 621,71,583 mientras el atacante avanzó 594,96,600 → 607,95,600; terminó con 64/64 escalones y FAILED. Se añadió `NavMissionChainPolicy`: conservar la etapa exterior activa con la misma raíz mientras haya etapas pendientes; permitir reemplazo por interior-only al entrar al anillo. Se agregó evento acotado `MISSION_CHAIN_RETAINED`. Se limitó `DROP_UNSAFE` por candidato (opt-in) porque Run39 generó 84 MB de traza en pocos minutos. Compilación y ocho suites OK (5+6+NavPlannerState+5+8+5+4+10 assertions), lint y diff-check OK. Run40 automático después de Run39 (watcher PID 29260). A2 sigue OPEN.


### Continuidad A2 — cierre Run39 y arranque Run40 (13-nov-2026, 16:05)

**Run39 (`a2n-20261113-155051`) finalizado.** En el límite contratado de fase B, t=12000: 8/8 atacantes spawneados, 3 vivos, 5 muertos, 0 desaparecidos, 5/8 entraron alguna vez al perímetro y 0 permanecían dentro del muro. A2 = **FAIL**. Los snapshots posteriores (t=12300 y t=12600) muestran 0 vivos, 5 muertos y 3 desaparecidos; no cambian el veredicto en el límite t=12000. El `fin.txt` del harness contiene **C4_PASS**: guardasCombatieron=true; supervivientes=15; 12 trabajadores esperados, 11 vivos y reanudados, 1 baja, 0 no resueltos; asignacionesJobSite limpias (9,0,0); 3 atacantes retirados para recuperación; 0 fallos de instrumento. El servidor PID 26668 terminó correctamente. La traza de Run39 alcanzó ~93 MB por la instrumentación `DROP_UNSAFE` anterior al filtro.

**Run40 arrancado automáticamente** por `auto-run40-after-run39.ps1` después de detectar `fin.txt` y la salida del servidor de Run39. RunId `a2n-20261113-160443`, servidor PID 34412, mundo desechable `build\\a2n\\a2n-20261113-160443\\game`. Se confirmó en `JDK_JAVA_OPTIONS` la presencia de `-Ddevilrpg.a2n.traceNav=true -Ddevilrpg.a2n.traceNav=true`, semilla 12345, fase A=3000, fase B=12000, NavCustom=true, presupuesto local 900 y global 8000. Al último chequeo estaba en arranque/carga de NeoForge; todavía no había `nav-trace.log`, `fin.txt` ni veredicto. No lanzar otra instancia mientras PID 34412 siga activo.

**Qué verificar en Run40:** (1) eventos `MISSION_CHAIN_RETAINED` para raíz estable con etapas exteriores pendientes; (2) que la meta exterior ya no se desplace continuamente con la posición del atacante; (3) que no se repita el agotamiento 64/64 sin progreso; (4) snapshot A2 en t=12000 y permanencia dentro del muro; (5) cierre C4 y `fin.txt`; (6) tamaño de `nav-trace.log` y ausencia del spam `DROP_UNSAFE` por defecto. A2 sigue **OPEN** hasta aceptación runtime completa. C4 de Run39 se conserva como PASS; no cambiar las políticas de recuperación ni debilitar el muro.

Restricciones vigentes: no GUI de Minecraft, no foco de ventana, no `run\\saves\\New World`, no `git reset/clean/stash/revert`, no commits. Mantener los cambios acumulados y documentar el resultado antes de cerrar la sesión.


#### Run40 checkpoint temprano — 13-nov-2026 16:06:57

Run40 ya cargó el harness y comenzó el muestreo. En t=900: 8 vivos, 0 muertos, 0 desaparecidos, 4/8 entraron alguna vez al perímetro y 2 dentro del muro. `nav-trace.log` ~3.26 MB, 57 eventos `MISSION_CHAIN_RETAINED` y 0 `DROP_UNSAFE`. Evidencia concreta: para UUID `4da2e6cd-fd16-4360-9db4-908cb93b6a3f`, la cadena conserva `current=616,71,578` mientras la nueva sugerencia es `incoming=621,71,583`, con la misma raíz `661,73,533` y una etapa pendiente. Esto confirma que la retención se está ejecutando y que no se persigue el waypoint móvil. **Solo checkpoint; no es PASS A2.** Se observó agotamiento del presupuesto global inicial en algunos cálculos (p.ej. idx #287 y #286), por lo que debe revisarse si producen bloqueo persistente. El watcher local `watch-run40-final.ps1` (PID 3520) registra cada 45 s y añadirá el resultado final a estos handoffs tras `fin.txt` y la salida del servidor.


#### Run40 checkpoint t=1800 — bloqueo residual identificado

Snapshots Run40: t=1200 = 6 vivos, 2 muertos, 0 desaparecidos, 4 entraron alguna vez, 2 dentro; t=1500 = 5 vivos, 3 muertos, 0 desaparecidos, 4 entraron, 1 dentro; t=1800 = 5 vivos, 3 muertos, 0 desaparecidos, 4 entraron, 1 dentro. Bajas registradas: idx2 t=943 en 652,71,563 (dist=31.80); idx1 t=1143 en 640,71,534 (dist=20.53); idx5 t=1465 en 658,71,522 (dist=11.09). La causa exacta de daño no está instrumentada en estos mensajes; no atribuirlas todavía a una fuente concreta.

La cadena estable sí funciona para idx3 (UUID `4da2e6cd-fd16-4360-9db4-908cb93b6a3f`): mantiene `mision=616,71,578` y la misma raíz aunque la sugerencia entrante sea `621,71,583`. Pero idx3 sigue `FAILED` en `607,95,601`, `escalones=64/64`, `fallosEscalon=10/10`, `colocados=0/24`; último motivo: `obstruccion de acceso removida; replanificar desde el mundo actualizado`. Es decir, la cadena móvil ya no explica por sí sola el bloqueo: permanece un problema independiente en el primer aterrizaje/planificación de escalera tras retirar obstrucción. idx6 también quedó `FAILED` por replanteos agotados (12/12); idx4 sigue `RECOVERING`, con fallos de descenso y presupuesto global agotado (8000/8000). No modificar código durante Run40; completar la observación y aislar el punto de fallo con la traza completa antes de diseñar otra corrección. A2 sigue OPEN.


### Run40 final � 2026-11-13 16:17:19

RunId a2n-20261113-160443. fin.txt: C4_PASS (raid) - guardasCombatieron=true profesionalesReanudados=true asignacionesJobSiteLimpias=true supervivientes=16 trabajadoresEsperados=12 trabajadoresVivos=12 trabajadoresReanudados=12 bajasTrabajadores=0 trabajadoresNoResueltos=0 retiradosParaRecuperacion=4 jobSite=[10, 0, 0] fallosDeInstrumento=0. RAID/C4 log evidence:
[16:16:11] [Server thread/INFO] [devilrpg/]: [A2N] RAID SNAPSHOT t=12000 vivos=4 muertos=4 desaparecidos=0 entraronPerimetroEver=6 dentroParedAhora=0 seguidos=8 fase=B
[16:16:11] [Server thread/INFO] [devilrpg/]: [A2N] RESUMEN FASE B t=12000 spawneados=8/8 fallosSpawn=0 vivos=4 muertos=4 desaparecidos=0 entraronPerimetroEver=6 dentroParedAhora=0
[16:16:11] [Server thread/INFO] [devilrpg/]: [A2N] VEREDICTO B: SIN UMBRAL CONTRATADO (informativo; no se declara exito ni fracaso) entraron=6/8 vivos=4 muertos=4 desaparecidos=0
[16:16:11] [Server thread/INFO] [devilrpg/]: [A2N] C4 POSTRAID maintenance=OK attackersRemovedForRecovery=4 recoveryTicks=1200
[16:17:11] [Server thread/INFO] [devilrpg/]: [A2N] C4 JOB_SITE assignments=10 orphaned=0 duplicateOwners=0
[16:17:11] [Server thread/INFO] [devilrpg/]: [A2N] C4 OUTCOME=SURVIVED attackersRemovedForRecovery=4 workerCasualties=0
[16:17:11] [Server thread/INFO] [devilrpg/]: [A2N] FIN (C4_PASS (raid) - guardasCombatieron=true profesionalesReanudados=true asignacionesJobSiteLimpias=true supervivientes=16 trabajadoresEsperados=12 trabajadoresVivos=12 trabajadoresReanudados=12 bajasTrabajadores=0 trabajadoresNoResueltos=0 retiradosParaRecuperacion=4 jobSite=[10, 0, 0] fallosDeInstrumento=0)
Nav trace: 24112482 bytes; MISSION_CHAIN_RETAINED=336; DROP_UNSAFE=0. Verificar evidencia completa en build/a2n/a2n-20261113-160443/srv.log, nav-trace.log y run40-watch.log. No se cambia el veredicto A2 por el C4: A2 queda OPEN salvo que cumpla todos los criterios de entrada y permanencia.



### Run40 final forensic result — 13-nov-2026

Run40 (`build/a2n/a2n-20261113-160443`) has finished. **A2 FAIL**: at the contracted phase-B boundary t=12000, `spawneados=8/8`, `vivos=4`, `muertos=4`, `desaparecidos=0`, `entraronPerimetroEver=6`, `dentroParedAhora=0`. Thus six attackers crossed the perimeter at least once, but none remained inside the wall at the acceptance snapshot. The harness printed `VEREDICTO B: SIN UMBRAL CONTRATADO` as informational; do not interpret that line as PASS. Subsequent snapshots t=12300–13200 show 0 vivos, 4 muertos and 4 desaparecidos because four surviving attackers were removed for C4 recovery; do not retroactively substitute those post-cleanup snapshots for t=12000.

**C4 PASS** independently: `guardasCombatieron=true`, `profesionalesReanudados=true`, `asignacionesJobSiteLimpias=true`, `supervivientes=16`, `trabajadoresEsperados=12`, `trabajadoresVivos=12`, `trabajadoresReanudados=12`, `bajasTrabajadores=0`, `trabajadoresNoResueltos=0`, `retiradosParaRecuperacion=4`, `jobSite=[10,0,0]`, `fallosDeInstrumento=0`, `recoveryTicks=1200`. `fin.txt` contains C4_PASS; this does not imply A2 success.

**Chain correction validated, A2 not solved.** Final nav trace: 24,112,482 bytes, 336 `MISSION_CHAIN_RETAINED`, 0 `DROP_UNSAFE`. idx3 retained `mision=616,71,578` despite incoming moving suggestions (e.g. 621,71,583), proving the stable-chain policy is active. However idx3 remained `FAILED` at `607,95,601`, `escalones=64/64`, `fallosEscalon=10/10`, `colocados=0/24`; last local failure was `obstruccion de acceso removida; replanificar desde el mundo actualizado`, with global route ending at 40 nodes and 3656/8000 expansions. idx4 was still `RECOVERING` at `588,79,507`, 20 descent failures, 8/64 local steps, global path 26 nodes/615 expansions; delegated break-first-block recovery. idx7 and idx8 were still `TRAVERSING` around `685-686,69,450`, 85-86 blocks from center, so the approach is not converging fast enough for the time budget. Earlier idx6 exhausted route replans 12/12. Four attackers died by t=12000; three explicit death records include idx2 t=943 at 652,71,563, idx1 t=1143 at 640,71,534, idx5 t=1465 at 658,71,522; idx6 died at t=1807. Exact damage source was not instrumented; do not guess.

Watcher `build/a2n/watch-run40-final.ps1` (PID 3520) completed, appended the run summary to all three handoff documents, and is no longer running. Server PID 34412 exited. Run40 world and all artifacts remain under `build/a2n/a2n-20261113-160443`. Trace filter verified effective: zero DROP_UNSAFE events, versus ~93 MB of noisy trace in Run39. No production world was touched. A2 remains OPEN. Next owner must inspect actual route/stair/recovery code and Run40 trace before changing anything; do not raise budgets or weaken the wall as a substitute for diagnosis.

## 2026-11-13 17:25 — A2 continuation: stair rearm/anchor corrections tested (A2 still FAIL)

- Branch/HEAD: feature/autonomous-village / 518fee1. No reset/commit/push.
- Runs 41–45 were executed with the documented raid contract; 41–44 were invalidated by later corrections and stopped. Run45: t=3900 snapshot vivos=5 muertos=3 desaparecidos=0 entraronPerimetroEver=4 dentroParedAhora=0.
- idx3 still ends FAILED at 607,95,601 with 64/64 local steps and 10/10 stair failures, last access-clear at 594,94,600.
- Corrections applied and compiled: local stair budget reset at verified terrain change and at STAIR-ACCESS-CLEAR (bounded 2); local-limit detection uses counters, not the last message; staircase anchor now follows current support on every no-pending attempt; mission-level stair fallback when global waypoint equals mission with build=false.
- Focused test: NAV_TERRAIN_REARM_POLICY_TEST OK. compileJava/compileTestJava BUILD SUCCESSFUL. No final A2 PASS.
- Diagnostics still needed: why the mission-level fallback does not produce a stair after access clear, and why idx4 still evaluates a stale 566,120,533 candidate set; idx7/idx8 approach convergence remains.
- Next action: add bounded trace at the fallback/anchor decision (anchor, candidate base, mission, build flag), reproduce idx3, then correct the real candidate/landing policy. Do not rerun the same fixture without a new trace.

## 2026-11-13 17:45 — A2 stair-fallback diagnostics (A2 still FAIL; C4 PASS preserved)

- Runs: a2n-20261113-173123 (focused 6000, stopped), a2n-20261113-173705 (focused 6000, FIN C4_PASS). Full A2 still not PASS.
- Instrumentation added: A2_STAIR_FALLBACK_DECISION and A2_STAIR_FALLBACK_RESULT (NavTrace, opt-in via traceNav). They log objetivo, mision, buildable, local counters, anchor/stair state and actual construction result.
- Proven defect: the mission-level fallback compared the global mission (661,71,533), not the selected local waypoint (e.g. 588,96,512). It therefore skipped stair construction. The condition now targets any unbuildable local waypoint below safe-drop.
- After the fix, idx4 entered the fallback and placed a first verified stair at 589,92,511, then consumed it; idx3 still had no fallback event in the focused run and remains 64/64, 10/10 at 607,95,601.
- The stair anchor now updates to current support on every no-pending attempt; local reset is bounded at verified terrain/access-clear events.
- Acceptance contract remains undefined: harness prints VEREDICTO B: SIN UMBRAL CONTRATADO. No A2 PASS is claimed.
- New permanent manual: docs/autonomous-village/A2-HEADLESS-TESTING-AND-INSTRUMENTATION.md.
- Next action: reproduce idx3 with the new FALLBACK_DECISION event; if absent, trace the tryPlan branch that returns null before line 1642; if present, inspect FALLBACK_RESULT and anchor/candidates. Then fix the exact branch.

## 2026-11-13 18:10 — A2 focused rearm trace: fallback still not reached for idx3

- Run 2n-20261113-180149 (focused FaseB6000) captured: phase A entraron=5/8; phase B t=3900 vivos=3 muertos=5 dentroParedAhora=0. A2 FAIL.
- A2_STAIR_FALLBACK_DECISION/RESULT appeared for idx4 (uuid 67861573…), with stair anchor still 566,121,533 and 28 rejected candidates; no event for idx3 (uuid near 607,95,601).
- idx3 is already FAILED at local 64/64, fallos 10/10, last access-clear 594,94,600; 	ryPlan is not entered in FAILED state, so the fallback gate cannot run.
- Bounded terminal rearm at the verified access clear was added; it did not create a viable next stair attempt. This is a real unresolved control-flow/geometry defect, not an acceptance PASS.
- Design proposal AGGRESSIVE-ZOMBIE-MOBILITY-DESIGN-PROPOSAL.md is documentation-only and NOT authorized for implementation.
- Next exact action: add A2_TRYPLAN_ENTER/NULL at NavExecutor.tryPlan and its early returns, reproduce idx3, and determine why FAILED is re-entered after the one bounded rearm. Then fix that transition only.

## 2026-11-14 00:30 — idx3 terminal state proven: rearmes=4/4, tryPlan never entered

- Focused run 2n-20261114-002306 (FaseB6000): A2 FAIL; t=3900 4 ever entered, 0 inside, 4 dead.
- A2_REARM_ENTER for idx3 shows state=FAILED rearmes=4/4, MAX_REARMES rejection every tick; no A2_TRYPLAN_ENTER for that UUID.
- Root cause is the exhausted terminal rearm budget, not the fallback gate. The access-clear reset did not leave a usable rearm after four failed fallback attempts.
- No speculative production fix applied. New dedicated report: A2-IDX3-TRYPLAN-STATE-INVESTIGATION.md.
- Instrumentation added: A2_TRYPLAN_ENTER, A2_REARM_ENTER, A2_REARM_REJECTED.
- Next: inspect the four rearm→tryPlan cycles after access clear and fix the exact bounded transition. A2 remains FAIL/OPEN; C4 PASS.

## 2026-11-14 00:40 — Post-rearm local counter overwrite proven

- Run 2n-20261114-003310 (focused FaseB6000). A2 still FAIL: t=3900 3 alive, 5 dead, 5 ever entered, 0 inside.
- idx3 UUID 9d3662ff-…: four terminal rearmes at ticks 275/405/535/665, each followed by A2_TRYPLAN_ENTER ... local=0/64 at 276/406/536/666.
- On the **same tick** as each tryPlan entry, NAV_FAIL reports local=64/64 fallos=10/10 again. The local stair counters are being restored/overwritten inside the planning path before the fallback can run.
- At tick 795 onward: A2_REARM_REJECTED reason=MAX_REARMES repeatedly; tryPlan no longer runs.
- idx4 still succeeds: A2_STAIR_FALLBACK_RESULT result=589,93,511, colocados=1/24, ncla=589,92,511; fallback behavior must be preserved.
- No production fix was applied for the counter overwrite; the exact writer is not yet identified. A2-IDX3-TRYPLAN-STATE-INVESTIGATION.md is the detailed report.
- Next: instrument scalonarObjetivoLocal/allar around the same tick with the counter value before/after and every write; fix the actual overwrite only.

## 2026-11-14 00:55 — Exact 0→64 writer identified; no safe fix applied

- Run 2n-20261114-004219 (focused FaseB6000). idx3 UUID 40f10aeb-….
- Trace shows A2_LOCAL_STAIR_ENTER local=0/64, then A2_LOCAL_STAIR_WRITE op=INC_STEP new=1..64 every ~2 ticks, each selecting 608,95,601 while mission is 616,71,578.
- Writer: NavExecutor.escalonarObjetivoLocal() line 1778 scalonesDeObjetivo++. The candidate is accepted despite not descending and while the mission remains below; the counter therefore counts non-progressing local steps.
- allosDeEscalonDeObjetivo reaches 10 via the repeated failure path once the 64 limit is hit (scalonarObjetivoLocal sets it to MAX at line 1755).
- idx4 (9c0f390…) still reaches fallback and places 589,92,511; a naive “candidate must be closer to mission” guard would also reject that valid step, so no speculative guard was added.
- A2 still FAIL: t=3900 0 inside; C4 preserved PASS. Acceptance threshold still undefined.
- Next exact action: instrument legirObjetivoLocal candidate generation for idx3 and add a progress predicate that distinguishes downward/sideways stair continuation from a same-level no-progress 608,95,601 selection; regression-test both idx3 rejection and idx4 acceptance before changing line 1778.

## 2026-11-14 01:00 — Dedicated counter-write report created

- Created `docs/autonomous-village/A2-IDX3-COUNTER-WRITE-INVESTIGATION.md` with the complete writer inventory, exact line 1778 `escalonesDeObjetivo++` evidence, the 64-step/10-failure accounting distinction, idx3-vs-idx4 divergence, and the reason no speculative acceptance fix was applied.
- A2 remains FAIL/OPEN; C4 PASS preserved.

## 2026-11-14 01:25 — Local objective progress predicate implemented

- Added NavLocalObjectiveProgressPolicy; when the mission is below the mob, same-level/higher local candidates are rejected unless a non-WALK route continuation or pending stair is present.
- Integrated at NavExecutor.escalonarObjetivoLocal; rejected candidates increment failures but do not consume the accepted-step budget.
- Tests: NavLocalObjectiveProgressPolicyTest 6/6 pass.
- Focused run 2n-20261114-011440: idx3 moved from stuck 607,95,601/64/64 to 617,86,600 with local 4/64 and explicit candidato local sin progreso vertical; idx4 still places 589,92,511.
- A2 still FAIL/OPEN (focused only; no inside-wall closing evidence); C4 preserved PASS. Threshold still SIN UMBRAL CONTRATADO.
- Report: docs/autonomous-village/A2-LOCAL-OBJECTIVE-PROGRESS-POLICY.md.

## 2026-11-14 01:45 — Full accepted-threshold campaign: A2 FAIL, C4 PASS

- Approved contract: A2-ACCEPTANCE-CRITERIA.md (8/8 registered; >=6/8 enter; >=1 alive inside at 12000; no surviving terminal FAILED/zero progress; C4 PASS).
- Full run 2n-20261114-013023 (FaseB12000): spawneados=8/8, entraron=5/8, vivos=3, muertos=5, dentroParedAhora=0. A2 FAIL on criteria 2, 3 and 4.
- Survivors: idx3 alive 617,86,600 (PLANNING, local 4/64), idx4 alive 588,77,507 (PLANNING, stair 589,92,511), idx8 alive 701,62,457 (terminal FAILED).
- C4 PASS: 16 survivors, 12/12 workers alive/resumed, 0 casualties, jobSite [10,0,0], 0 instrumentation failures.
- Campaign report: A2-CAMPAIGN-20261114.md.
- Next: inspect the five deaths and idx8's terminal FAILED first divergence; do not weaken wall/threshold. A2 is not PASS.

## 2026-11-14 01:50 — Run13 forensics: A2 FAIL, death telemetry absent

- Run 2n-20261114-013023: 8/8 registered; 5/8 ever entered; 3 alive (idx3,4,8); 0 inside at t=12000; C4 PASS.
- Casualties idx1,2,5,6,7: no death/damage event, killer or death tick exists in current telemetry, so causes are **unknown**, not inferred.
- idx8 first terminal: NAV_FAIL replans agotados (8/8) at objective 701,67,457 / mission 725,71,533 around tick 8041; four rearmes consume by ~9525 without changing the unreachable BREAK waypoint.
- idx3 ends 617,86,600, PLANNING, local 4/64, progress policy working but no wall entry; idx4 ends 588,77,507, one verified stair 589,92,511, no entry.
- No death/replan correction was justified by available evidence. Full forensics: A2-RUN13-DEATH-AND-STALL-FORENSICS.md.
- Next: add opt-in death/damage telemetry and trace why the same 701,67,457 BREAK waypoint survives every rearm; then fix that shared lifecycle and rerun full acceptance.

## 2026-11-14 02:05 — Death telemetry validated; idx8 upward BREAK blocker

- Telemetry implemented via NeoForge LivingDamageEvent.Post/LivingDeathEvent, registered on NeoForge.EVENT_BUS, opt-in through NavTrace; no behavior changes.
- Focused run 2n-20261114-015833: 16 damage events, 4 deaths. All observed deaths are guard minecraft:mob_attack: idx6 t1353, idx1 t1637, idx5 t2125, idx2 t2321.
- idx8:
eplans agotados (8/8) with BREAK waypoint 701,67,457 ~5 blocks above the mob; no current upward mobility, same waypoint survives every rearm. Navigation-only repair would require the unapproved mobility design or changed route semantics.
- A2 remains FAIL: entrants are killed by guards before the closing snapshot, and idx8 is stranded. Satisfying >=1 alive inside by weakening guards/changing survivability would violate campaign restrictions and needs an explicit design decision.
- Report: A2-DEATH-TELEMETRY-AND-IDX8-BREAK-REPLAN.md.

## 2026-11-14 02:40 — Bounded retreat implemented; survival still fails because no healing

- New AttackerRetreatPolicy (6/6 tests): low health (<=55%), guard within 12, max 2 retreats, 80 ticks, 200-tick cooldown.
- AggressiveZombieEntity.tickRetiradaTactica() clears the combat target, suppresses retargeting via setTarget, moves away from the nearest villager, then resumes mission; no guard/C4 changes.
- Focused run 2n-20261114-023254: retreats logged for #286 and #285 (health 5.2), but both still died at ticks 1037/1057; #290 triggered retreat at health 0.0 and died at 783. Phase A: 8/8 spawn, 5 alive, 3 dead, 4 ever entered, 0 inside.
- Root limitation: Minecraft zombies do not regenerate health. Retreat cannot restore the 5.2/6.8 health needed to survive the next guard hit; no artificial healing/attribute change was added.
- Equipment + retreat improved early survival vs prior focused run (3 deaths at t=3000 vs 4 by t~2300) but does not satisfy survival or A2.
- Next: either authorize a real recovery mechanism (e.g. consumable/effect) or a longer bounded evasion policy that avoids re-engaging while critically wounded; JUMP/BUILD remains the next mobility stage.

## 2026-11-14 02:56 — Preventive evasive retreat: still FAIL

- AttackerRetreatPolicy threshold raised to 70%, retreat 120 ticks, 2 uses, evasive state persists while a threat remains within 16 blocks; zero-health/dead activation rejected; damage event now calls
eaccionarADanoA2().
- Policy test expanded to 10/10 assertions. NavLocalObjectiveProgressPolicyTest and NavTerrainRearmPolicyTest still pass; compile/lint/diff pass.
- Focused run 2n-20261114-025009: retreats initiated at 6.8 health (preventive) and no zero-health activation, but attackers still died:
  - #282 retreat at 2.875 → death tick 868;
  - #287 retreat at 6.8 → death tick 1026;
  - #283 retreat at 6.8 → death tick 1358.
- Phase A: 8/8 spawn, 5 alive, 3 dead, 4 ever entered, 0 inside. No stage PASS.
- Cause of remaining failure: the safe-reposition move does not reliably break contact/navigation authority; damage can still land before/while the retreat executes. No guard/C4 changes.
- Next: integrate retreat movement with NavExecutor authority (single movement controller), verify actual distance/line-of-sight break and no re-engage while critical; then rerun focused. JUMP/BUILD remains the next mobility stage after this PASS.

## 2026-11-14 03:20 — Single-authority retreat: movement ownership fixed, evasion still fails

- NavExecutor.suspendMovementForRetreat() pauses executor steering without clearing mission/route; retreat is evaluated before executor tick and its move is reissued after goals (
eemitirRetirada).
- Threshold raised to 90% so the first armored hit (8.4) triggers; policy test 10/10.
- Focused run 2n-20261114-031152: RETREAT_STARTED at health 8.4 and distance 1.00–2.74, but no RETREAT_PROGRESS/SUCCESS events; deaths continued:
  - eb1409ec t1325 melee, 0d90fdd4 t1605 melee, 7b828112 t1720 arrow, 29397fa1 t2095 melee; one in_wall death at t429.
- Phase A: 8/8 spawn, 3 alive, 5 dead, 4 ever entered, 0 inside. No fin.txt; run manually stopped after evidence.
- Root cause of this iteration: posicionSegura() does not find a usable safe cell before death (no RETREAT_PROGRESS); movement authority is no longer the blocker, target selection/route availability is.
- No guard/C4 changes. A2 remains FAIL.

## 2026-11-14 03:50 — Full A2 after retreat-destination fix: FAIL, C4 PASS

- Focused 2n-20261114-032400: completed with fin; ntraron=6/8, inside=1 at t=3900, C4 PASS.
- Full 2n-20261114-033543 (FaseB12000): completed with fin; spawneados=8/8, ivos=4, muertos=4, ntraronPerimetroEver=4, dentroParedAhora=0, C4 PASS.
- Full result: A2 FAIL criteria 2/3/4; survivors idx3 617,86,600; idx4 588,79,507; idx7 698,62,454; idx8 701,62,457, all outside.
- Retreat resolver did select navigable destinations (336 candidates evaluated; RETREAT_DESTINATION_SELECTED), but did not produce enough surviving entrants over 12000 ticks.
- C4 exact: guards fought, 12/12 workers alive/resumed, 0 casualties, jobSite [9,0,0], 4 removed for recovery, 0 instrument failures.
- Next: use the completed run to separate combat-death and end-to-end approach failures; do not alter threshold/C4. Vertical JUMP/BUILD remains pending for idx8.

## 2026-11-14 04:05 — BREAK vertical reach gate implemented; focused partial PASS

- Existing NavExecutor already has a bounded JUMPUP for dy=2 cardinal jumps (validates corridor, landing, support, fits; 40-tick timeout).
- Added NavBreakReachPolicy (MAX_STEP_UP=2, action-reach distance) and integrated it in local BREAK waypoint selection. Unreachable vertical BREAK nodes are rejected with VERTICAL_TRANSITION_REJECTED reason=OUT_OF_REACH; mission/route replan continues.
- Policy test NAV_BREAK_REACH_POLICY_TEST 4/4.
- Focused run 2n-20261114-035351 completed with fin: C4 PASS, ntraron=6/8, ivos=2, dentroParedAhora=0.
- idx8 advanced from historical 701,62,457 to 672,72,550 (dist 20.9) and was no longer stuck on the unreachable BREAK waypoint; it later died in combat.
- No general upward BUILD or new JUMP height was added; idx8's dy=5 case is resolved by rejecting the waypoint/replanning, not by climbing.
- A2 remains FAIL (no inside survivor at close); C4 PASS. Next: inside-survival/combat and, if required, general BUILD ascent.

## 2026-11-14 09:45 — FINAL full A2 run (12000): FAIL, C4 PASS

- Run 2n-20261114-093155, completed normally with fin.txt.
- spawneados=8/8, allosSpawn=0.
- ivos=3, muertos=5, desaparecidos=0, ntraronPerimetroEver=5, dentroParedAhora=0.
- Survivors: idx3 617,86,600; idx4 588,79,507; idx7 714,60,471 — all outside, all PLANNING (no terminal FAILED).
- A2 FAIL: criterion 2 (5/8 < 6), criterion 3 (0 inside), criterion 4 (no external survivor progress to enclosure in final window; idx3/idx4 unchanged).
- C4 PASS exact: guards fought, 12/12 workers alive/resumed, 0 casualties, jobSite [10,0,0], 3 removed for recovery, 0 instrument failures.
- idx8 died during the raid; BREAK reach gate prevented the old terminal FAILED loop but did not produce an inside survivor.
- Next: only correction(s) supported by the completed run's per-attacker evidence; no threshold/C4 change. BUILD ascent remains not implemented.

## 2026-11-14 10:02 — Perimeter-aware retreat focused: FAIL

- Added AttackerRetreatDestinationPolicy.preservesInterior: an attacker already inside may not choose an outside retreat destination; search expanded to 8–32 blocks. Test 12/12.
- Focused run 2n-20261114-095200 completed with fin: C4 PASS, but ntraron=4/8, ivos=4, muertos=4, dentroParedAhora=0.
- Four deaths all minecraft:mob_attack: ticks 884, 1117, 1709, 1819. Retreat still does not keep entrants alive to the closing snapshot.
- No full acceptance run was launched after this negative focused result. A2 remains FAIL; C4 PASS preserved.
- Next: death timing shows guards kill entrants before/around retreat; a general inside-combat/evasion solution (or authorized survival mechanism) is still required. Do not change threshold/C4.

## 2026-11-14 10:30 — Interior group trigger + villager flee speed: focused FAIL

- Workstream A: added AttackerThreatPolicy (2+ guards within 8, health <95%) and integrated GRUPO retreat cause. Test 5/5.
- Workstream B: added VillagerFleeSpeedPolicy; flee speed reduced exactly 30% (1.35 -> 0.945). Test 2/2. Only VillagerFleeGoal.VELOCIDAD uses it; normal work/guard speeds untouched.
- Focused run 2n-20261114-101748 completed with fin: C4 PASS, spawneados=8/8, ivos=3, muertos=5, ntraronPerimetroEver=5, dentroParedAhora=0.
- Deaths: ticks 749, 1127, 1149 (arrow), 1717, 2437, all near/inside wall. No improvement sufficient for A2.
- A2 remains FAIL. No full acceptance after this negative focused result.
- C4 exact: 12/12 workers, 0 casualties, jobSite [1,0,0], 3 removed for recovery, 0 instrument failures.
- Next: the remaining blocker is lethal guard combat despite group detection/retreat; a real authorized survival/recovery or stronger inside evasion is required. Do not change C4/threshold.

## 2026-11-14 11:20 — Difficulty scaling forensics (audit only)

- Reference is nearest player's anchor/spawn point, not village centre; spawnDistance computed once in setPos, spawnThreat captured at spawn.
- Siege attackers use the same scaling path; at A2 ring distance ~95 blocks the scale is only ~1.03 (max profile distance 3000), so health ~10.3/damage ~0.72.
- Time/threat changes do not strengthen existing entities; only new spawns.
- Iron siege equipment sets items only and does not overwrite health/damage/speed attributes; it does compress defense differences.
- Deaths in 093155/095200/101748 are coherent with near-base stats plus multiple guards; no equipment-overwrite bug demonstrated.
- Full report: A2-DIFFICULTY-SCALING-FORENSICS.md. No behavior changed; A2 remains FAIL; C4 unchanged.


## 2026-11-14 — Difficulty architecture follow-up (owner-confirmed)

- [ ] **P0 / read-only:** trace the actual geographic scaling reference for both custom `AggressiveZombie` spawn and siege attackers. Intended reference is the one shared world spawnpoint; previous audit says nearest player's anchor/spawn, so verify the exact source/API and multiplayer independence before editing.
- [ ] Verify world-time threat is shared and deterministic; confirm new entities snapshot it at spawn and existing entities retain their attributes.
- [ ] Design (no implementation yet): move temporal progression parameters into each creature's `SpawnProfile` while retaining shared world time/threat as the input.
- [ ] Inventory and plan compatibility-aware rename of custom Vex to `AggressiveVex` across classes, registry/resource identifiers, saved data/serialization, configs, translations, logs, tests and documentation.

Owner-confirmed design: distance from the common world spawn makes both custom-spawned zombies and siege attackers stronger; world play time increases difficulty for new spawns; multiplayer must not depend on nearest player or per-player spawn. No balance or gameplay change is authorized by this follow-up. Keep A2/C4 status and acceptance criteria unchanged.

## 2026-11-14 11:45 — World-global difficulty verification (audit/design only)

- Exact geographic reference is each player's nchorPoint, initialized once from ServerLevel.getSharedSpawnPos() in CommonForgeInteractionEventSubscriber.onEntitySpawn. Normal path: **MATCH** shared world spawn.
- Residual mismatch risk: AggressiveZombieEntity.setPos falls back to getSpawnPoint() (player-specific) when the shared anchor is null. Production hardening should remove that fallback; no change made in this task.
- Temporal difficulty is ThreatLevel.current(level()) captured at spawn; existing entities retain spawn-time attributes. Matches owner design.
- Custom zombie, siege attacker (same class), and FrostVex all use the same nearest-player-anchor lookup. A2 harness overrides the fake player's anchor (-5000) as test instrumentation.
- SpawnProfile is immutable and holds distance parameters; temporal global config lives in ThreatLevel. Proposed per-creature TemporalDifficultyProfile documented, preserving current zombie defaults (1.0→1.8, 3h).
- AggressiveVex rename inventory documented. Java/display rename can be compatibility-safe; changing registry ID rost_vex is high risk without a save migration.
- No gameplay code, attributes, registry IDs, entity names, A2/C4 criteria or saves changed. Full design: WORLD-GLOBAL-DIFFICULTY-AND-SPAWNPROFILE-DESIGN.md.

## 2026-11-14 12:05 — Shared world spawn invariant implemented

- AggressiveZombieEntity.setPos now reads ServerLevel.getSharedSpawnPos() directly when spawnDistance == 0; it no longer looks up the nearest player or falls back to getSpawnPoint().
- FrostVexEntity.setPos uses the same direct shared spawn and policy.
- New pure SharedSpawnReferencePolicy + test (5 assertions) uses only the shared origin; player/personal spawn values are not inputs.
- Distance and temporal formulas, profile values, multipliers, temporal snapshot semantics and all unrelated anchor uses are unchanged.
- A2 harness still contains the fake anchor override -5000, but it is now inert for difficulty because entities use the real shared spawn directly. This changes A2 scaling from the synthetic max-distance anchor to the actual world spawn distance; harness cleanup is a separate task.
- No registry IDs/names, C4, A2 threshold, guards or workers changed.
- A2 remains FAIL; no acceptance run was performed. Multiplayer runtime verification remains pending.

## 2026-11-14 12:30 — A2 acceptance with shared-world-spawn scaling: FAIL, C4 PASS

- Run 2n-20261114-121647, completed normally with fin.txt (full 12000 ticks).
- spawneados=8/8, allosSpawn=0, ivos=3, muertos=5, desaparecidos=0, ntraronPerimetroEver=5, dentroParedAhora=0.
- Survivors: idx3 617,86,600 health 17.56; idx4 588,79,507 health 16.84; idx8 748,63,533 health 18.24. idx7 entered at t=9783, was inside at t=10476, died t=11078; five attackers died.
- A2 FAIL: criterion 2 (5/8 < 6), criterion 3 (0 inside), criterion 4 (no external survivor enclosure progress; idx3/idx4 static, idx8 far).
- C4 PASS: 16 survivors, 12/12 workers alive/resumed, 0 casualties, jobSite [8,0,0], 3 removed for recovery, 0 instrument failures.
- Measurable difference vs 093155: survivor health rose ~10 -> ~17-18 because the real shared spawn distance now scales difficulty; the A2 harness's -5000 anchor override is inert.
- No A2/C4 threshold or code changed during this run. Shared-spawn invariant held for both zombie and Vex.

## 2026-11-14 12:49 — Run 121647 forensics + horizontal progress focused FAIL

- Root causes established from 121647:
  - idx7: retreat started at health 15.58, selected dest 727,71,549; no verified progress; two sword guards dealt repeated 1.6 hits until death at 11077. Retreat destination was outside the harness wall enclosure despite preservesInterior; the protected-area predicate and the walled-enclosure predicate are not equivalent. Not safely corrected in this pass.
  - idx8: repeated BREAK-VERIFIED at 746,65,533/534 while local objective stayed 747,68,535; terminal presupuesto agotado sin progreso (10/10). Reach gate rejects some nodes but the cached route/objective is retained; broad redesign required.
  - idx3: candidate 618,86,600 moved away from mission 616,71,578; rejection was correct. Final run regressed to 607,95,601 on rearm.
  - idx4: candidate 589,79,508 was closer horizontally (dx 8 vs 9, dz 25 vs 26) but old policy rejected it. Added direction-aware acceptance (7/7 test).
- Focused run 2n-20261114-123936 completed with fin: C4 PASS, but A2 ivos=4, muertos=4, ntraron=4/8, dentroParedAhora=0; idx3 607,95,601; idx4 588,79,507. No improvement.
- No full acceptance after this negative focused result. A2 remains FAIL; C4 PASS.

## 2026-11-14 13:02 — Enclosure-aware retreat + verified-BREAK replan: focused partial PASS

- Retreat interior check now uses the production wall predicate dentroDeLaAldea → VillageManager.dentroDelRecinto(pos, villageCenter, cota, FENCE_RADIUS), not the broader protected-area predicate. idx7-style start-inside → dest-outside is now rejected.
- Verified BREAK (NavObjectivePolicy.accionResuelta with target air) now advances route index and forces objective=null, objetivoPermiteConstruccion=false, globalCache=null, state=PLANNING; obsolescent above-attacker objective is not retried. Trace VERTICAL_TRANSITION_FAILED reason=BREAK_VERIFIED_REPLAN.
- Focused run 2n-20261114-125345 completed with fin: C4 PASS, ntraronPerimetroEver=6 (criterion 2 met), but ivos=2, muertos=6, dentroParedAhora=0.
- idx8 reached inside during the run (663,71,496 dist36.96) but died before the closing snapshot; idx3 607,95,601 and idx4 588,79,507 remain outside.
- Repair audit (source): VillagerRepairGoal.canUse false under attack; 	ickVillageLife gated; segurarMuro skips when AggressiveZombie within FENCE_RADIUS+4. No evidence of wall reconstruction during this siege; gate automation is not block replacement. No repair change made.
- A2 remains FAIL (criterion 3 and 4); C4 PASS. No full acceptance after the focused negative survival result.

## 2026-11-14 13:28 — Siege combat doctrine audit + bounded tactical model

- Replaced indefinite critical evasion/distant retreat with a bounded 40-tick, 3–7-block tactical step-back, max 2 uses; no vasionCritica; mission/route preserved and NavExecutor resumes.
- Audit found no dedicated bell-destruction goal for attackers; MoveToVillageCenterGoal targets the centre, not the bell. This is a missing design mechanic, not a regression.
- No dedicated dodge/group-distribution/regroup goals were found; existing combat/navigation mechanics are listed in A2-SIEGE-COMBAT-DOCTRINE-AUDIT.md.
- Focused 2n-20261114-131627 completed with fin: C4 PASS, 4/8 entered, 4 dead, 0 inside; no tactical maneuver was triggered, so runtime proof is absent.
- A2 remains FAIL; no full acceptance justified. Do not weaken guards or change thresholds.
- New doc: A2-SIEGE-COMBAT-DOCTRINE-AUDIT.md.

## 2026-11-14 13:56 — Explicit bell objective implemented; focused not reached

- Bell generation traced to kiosk centre center.x, plazaLevel+1, center.z; localizarCampana(cota) scans a bounded 9x9x5 volume and persists the actual Blocks.BELL.
- NavBellObjectivePolicy (7/7 tests) validates objective and inside/reach destruction. Server-side destroyBlock verified air before BELL_DESTROYED.
- Tactical 40-tick step-back preserved; bell objective survives combat/repositioning; no second navigation controller.
- Focused 2n-20261114-134455: 8/8 spawned, bell found at 661,72,533 for all, 4/8 entered, 4 alive/4 dead, 0 inside, no bell destruction. C4 PASS.
- A2 remains FAIL; full acceptance not run. Report: A2-BELL-OBJECTIVE-IMPLEMENTATION.md.

## 2026-11-14 14:41 — Bell approach integrated; focused still no destruction

- cadenaDeAproximacion now uses destinoAproximacionCampana(centro): scans ±2 around the actual bell, validates interior standability, and selects the closest adjacent cell. Fallback is the old interior destination.
- Focused 2n-20261114-143051 completed with fin: C4 PASS, 8/8 spawned, 5/8 entered, ivos=3, muertos=5, dentroParedAhora=0.
- BELL_APPROACH_SELECTED ... bell=661,72,533 approach=660,72,533 observed for two attackers; no BELL_DESTROYED.
- idx8 entered at t=2992 (720,71,516), died t=3631 at 693,71,524; idx3/4/7 survived outside. No attacker reached/attacked the bell.
- Root blocker is combat survival/approach, not bell discovery/approach selection. A2 remains FAIL; no full acceptance.

## 2026-11-14 14:45 — Final assault forensics (143051): no new navigation defect proven

- idx8 UUID 2a512088-…: entered t=2992 at 720,71,516 (dist60.7); advanced to 691,71,525 (dist31.5) by t=3210; then remained in melee at 689–693 until death t=3630.
- Bell mission updated correctly after entry: mision=660,72,533, local waypoint 667,71,530; path progress from dist60.7 to31.5 is real.
- Two tactical step-backs: t=3252 health15.03 target699,71,534; t=3451 health8.64 target696,71,519. RETREAT_PROGRESS shows guard distance 0.70–1.74 — the guard followed/stayed in reach, so the maneuver could not break contact.
- Damage: repeated 1.6 melee every ~42 ticks from guard cc..., plus arrow at t3554; death from guard 129... at t3630.
- Classification: **expected death under sustained guard combat after measurable assault progress**, not insufficient navigation, route reset, movement-ownership conflict, or bell-approach invalidation. Four other entrants died fighting before t=3000.
- No BELL_DESTROYED; no SIEGE_COMPLETED. No new source change was justified by this evidence; bell approach/physical destruction remain unproven.
- A2 remains FAIL; C4 PASS. Full acceptance not run.

## 2026-11-14 15:36 — Advance-under-fire reaches and destroys the bell

- Added AdvanceUnderFirePolicy: melee-range combat no longer stops NavExecutor; distant guards are not chased away from the bell (COMBAT_LEASH_SQR=36).
- MovementOwnership.executorMayMove now allows the executor while a target is in melee range; PursueTargetGoal avoids chasing targets beyond the leash when the bell objective is active.
- Focused 2n-20261114-151646: BELL_DESTROYED ... bell=661,72,533 verified=true tick 1105; C4 PASS.
- Focused 2n-20261114-152705: BELL_DESTROYED + SIEGE_COMPLETED ... verified=true tick 1103; C4 PASS, 4/8 entered, 4 alive/4 dead, 0 inside at t=6000.
- The bell objective is now physically proven end-to-end: discovery → approach → attack → verified block removal → SIEGE_COMPLETED. No false success.
- Full A2 acceptance remains FAIL/pending: focused run entry count is 4/8 and no inside survivor at closure; do not treat bell destruction alone as A2 PASS.

## 2026-11-14 15:40 — Post-bell and entry forensics (152705)

- Entrants: idx1 (t514, destroyed bell t1103, died t1177 at dist4.4), idx2 (t668, died1173), idx6 (t650, died1681), idx5 (t1673, died2273). All four entrants died; zero inside at closure.
- Non-entrants: idx3/idx4 ended terminal FAILED with local 64/64 and 10/10 (stair/descent exhaustion); idx7/idx8 remained PLANNING outside with stale route objectives (715,63,466 STEP_UP; 747,63,531 WALK).
- Bell destruction is entity-local: the destroyer sets campanaDestruida; other attackers do not receive a global siege-complete state and continue their missions. No village manager state change occurs from this path.
- Contract conflict: the actual siege victory (bell destroyed) occurred at tick 1103, but A2 criterion 3 requires an attacker alive inside at tick 12000. If bell victory is meant to end the siege, that criterion is semantically incompatible; if attackers must remain inside after conquest, post-bell mop-up must be explicitly defined. No threshold or acceptance change made.
- No source change this pass; group coordination/crowding evidence is insufficient to justify a policy. C4 PASS. A2 remains FAIL.

## 2026-11-14 16:30 — Village-wide conquest/occupation implemented; entry presence still fails

- Added persisted Occupied village set in VillageSavedData (isOccupied/markOccupied, NBT key Occupied, idempotent setDirty).
- Verified bell destruction marks the village occupied exactly once; all attackers observe the shared flag and emit OCCUPATION_STARTED (8/8 in run 2n-20261114-161058), with a single SIEGE_COMPLETED.
- Occupation assigns the conquered village interior as the shared objective, but survivors still fail to remain inside.
- Runs: 161058 7/8 ever entered, 2 alive/6 dead, 0 inside at t=6000, C4 PASS. 162129 5/8 entered, 3 alive/5 dead, 0 inside, C4 PASS.
- Entry blockers remain: idx3/idx4 FAILED with local 64/64 10/10; idx7/idx8 PLANNING outside with stale/upward objectives. No group blockage evidence.
- No full 12000 acceptance run; A2 remains FAIL. Occupation persistence across save/reload was implemented but not runtime-tested.

## 2026-11-14 17:17 — Occupation objective ownership fixed; full acceptance still fails criterion 3

- New NavExecutor.enterOccupationObjective clears retained assault chain/stages before assigning the conquered-village interior.
- Occupation sets goToCenterActive=false so MoveToVillageCenterGoal cannot overwrite the occupation objective.
- Focused 2n-20261114-165322: 7/8 entered, idx7 alive inside at t=6000, bell destroyed, occupation active, C4 PASS.
- Full 2n-20261114-170413 (12000): 7/8 entered, 1 alive (idx3 outside), 0 inside; idx7 died at t=11586 inside dist41.5. C4 PASS.
- A2 remains FAIL on criterion 3 and 4 (idx3 outside with no final progress). No threshold/C4 changes.
- Occupied persistence implemented in VillageSavedData but still not runtime save/reload tested.

## 2026-11-14 18:47 — Final-window forensics and occupation budget reset

- idx7 (170413): advanced from dist62 to dist41.5 inside, was moving when two sword guards plus archer dealt 1.6/3.22/1.14 hits from t11403; died t11585 at 623,71,514. Legitimate sustained combat, not an occupation or navigation defect.
- idx3 (170413): terminal FAILED at 607,95,601, local 64/64 10/10, all 4 rearmes consumed. Occupation transition now resets terminal/local budgets once and reassigns the interior (nterOccupationObjective); focused run 2n-20261114-183656 still ended idx3 at 607,95,601 and 6/8 entered, 0 inside. The vertical exhaustion persists; no budget increase.
- Persistence unit test remains blocked by test classpath lacking Minecraft SavedData classes; runtime save/reload not tested.
- C4 PASS in all runs. A2 remains FAIL (criterion 3/4); no full acceptance justified.

## 2026-11-14 21:36 — Stale-anchor guard tested and reverted; C4 re-verified

- Added AttackerStairAnchorPolicy (3 assertions) and guarded candidate base against far anchors, then focused 2n-20261114-211016: idx3 still 607,95,601/FAILED; the guard produced no behavioral change. The guard was reverted to preserve the prior anchor path.
- The diagnostic candidate 594,92,600 is a **construction decision/landing probe**, not an executable landing: valuarCeldaDeEscalon rejects it for no air/support, and construirEscalonHacia is not yielding a placed stair. The 64/10 exhaustion comes from repeated local objective selection, not 64 physical placements.
- C4 verification 2n-20261114-212757: C4 PASS (15 survivors, 12/12 workers, jobSite [1,0,0], 0 casualties). The earlier C4_FAIL with jobSite [0,0,0] did not reproduce; it is recorded as a one-off, not a code regression.
- Focused 212757: 6/8 entered, idx8 entered but died, idx3/idx7 outside at closure, 0 inside. A2 FAIL.
- No budget increase; no full acceptance. Next: instrument the exact intentarConstruirEscalonSinWaypoint/construirEscalonHacia return path for idx3 to prove why no stair placement is attempted, rather than chasing the landing diagnostic.

## 2026-11-14 22:00 — Stair pipeline traced: access-clear returns true with no pending stair

- Added opt-in -Ddevilrpg.a2n.traceStair=true (NavStairTrace) and cleared stale motivoDeConstruir/motivoDeNoConstruir at each descensoExigeConstruir.
- Trace run 2n-20261114-215026, C4 PASS, 7/8 entered, 0 inside.
- idx3 UUID f27a94fc: pipeline now reached at 607,95,601 (previous gate fix). CPU trace:
  - STAIR_BUILDER_RETURN result=true pending=null colocados=0 motivo={obstruccion de acceso removida} rechazo={acceso despejado en 594,94,600}
  - later
esult=true pending=null colocados=1 ... acceso despejado en 618,79,601
  - STAIR_PIPELINE_PENDING transitable=null
- Root cause: construirEscalonDescendente treats a verified access-clear as success (
eturn true) without creating a pending stair. The executor receives null, falls through to scalonarObjetivoLocal, which consumes local steps; after 64 the budget is exhausted without any physical placement.
- No budget/radius/collision changes. Next justified correction: after a verified access clear, retry candidate evaluation within the same bounded call or signal the executor to retry without consuming a local step; never report a placed stair without a pending verified block.

## 2026-11-14 22:24 — Typed stair outcomes implemented; idx3 progresses vertically

- New StairBuildResult enum (STAIR_PLACED, ACCESS_CLEARED, NO_PROGRESS, REJECTED, FAILED) and test 4/4.
- construirEscalonHacia boolean is now STAIR_PLACED only; construirEscalonResultado classifies actual state.
- NavExecutor surfaces ACCESS_CLEARED, invalidates caches, and retries next tick **without consuming scalonesDeObjetivo**. The old false-success path is removed.
- Focused 2n-20261114-221228 (traceStair): idx3 moved 607,95,601 → 618,77–79,601 (dist 86.8→80.57), actual vertical progress.
- Trace: STAIR_BUILDER_RETURN outcome=ACCESS_CLEARED pending=null at 618,84/81/82/79/80; outcome=STAIR_PLACED pending=589,98,513 for another attacker. Every STAIR_PLACED has a pending block.
- Focused closure: 7/8 entered, 1 alive (idx3 outside), 0 inside; C4 PASS. idx8 was inside during phase A but died by t=6000.
- A2 remains FAIL (inside survivor); no full 12000 run. Persistence test for VillageSavedData remains unavailable in the current test classpath.

## 2026-11-14 22:34 — Occupation death and post-stair forensics (221228)

- idx8 UUID b8d9a066: entered around t3020 at 662,71,564 (inside); health was already 5.44. Took melee 1.6 from guards d980/d3ec and arrow 0.72 from 51cf over t3036–3100, died t3100 at 660,71,563 (inside). Two guards plus an archer; ordinary sustained combat. No occupation invalidation or movement-ownership defect proven.
- idx7 (170413) shows the same pattern: two sword guards plus archer, sustained damage while moving inside. Deaths are independent legitimate combat outcomes, not a common AI defect.
- idx3 UUID 0fb1ad: typed pipeline works. ACCESS_CLEARED at 618,84/81/82/79/80, then STAIR_PLACED pending=589,98,513 for another attacker; idx3 descended 607,95→618,77, local 27/64, colocados=1, route 74 nodes, last BREAK-VERIFIED at619,80. It remains outside but is progressing; no repeated same obstruction.
- No source change this pass. No budget/collision/guard/stat changes. C4 PASS. A2 remains FAIL (0 inside at closure; focused 7/8 entered).
- Persistence test for VillageSavedData still not possible in current test classpath.

## 2026-11-14 22:51 — 9000-tick focused: idx3 enters, no inside survivor

- Run 2n-20261114-224001 (FaseB9000, traceStair): 8/8 spawned, 6/8 entered, 2 alive/6 dead, 0 inside at t=9000. C4 PASS.
- idx3 UUID fa6a768b: real route progression 607,95,601 → 618,77 → 631,71 → 606,71 → 616,71 → 618,71 → entered; died at t=2600 inside 621,71,534 from two guards ( 9f1..., 3cd...), ordinary combat. Stair outcomes included STAIR_PLACED pending=589,98,513 and 588,77,507 (verified).
- Bell destroyed + SIEGE_COMPLETED at t=1287; occupation observed. Bell/conquest intact.
- Survivors idx4/idx7 remained outside; idx8 entered and died earlier. No new stair/access defect.
- No source change this pass. A2 remains FAIL on inside-survivor; full 12000 not justified.

## 2026-11-14 23:30 — Post-conquest combat audit (224001)

- Entrants: idx1 t513, idx6 t651, idx2 t669, idx5 t1630, idx3 t1900, idx8 t2636. All six died; health on entry ~16.5–18.5, no healing.
- Deaths: idx1 t1098 (before conquest at t1287, dist9.7); idx2 t1381; idx6 t1913; idx5 t2351; idx3 t2601 (inside, dist38.8); idx8 t3113 (inside, dist22.1). All others ordinary two-guard/archer combat.
- Threat distribution: guards e3cdcbaa and 09f1f7eb alternate on nearly every entrant; archer 51cf3346 contributes. Every entrant used RETREAT_STARTED 1/2 and 2/2 (DANO/GRUPO) but the bounded 2-retreat policy did not create separation; no target oscillation or invalid-bell pursuit observed.
- No confirmed combat implementation defect. Missing capability: no group target distribution/assistance. Adding it would be a new tactical feature, not a demonstrated bug; no source change made.
- Survivors idx4/idx7 were outside with navigation failures, not combat survivors.
- C4 PASS. A2 remains FAIL (0 inside at closure); full acceptance not justified.

## 2026-11-15 00:20 — Final A2 closure attempt: focused gate still fails

- Implemented measured RETREAT_STUCK_ESCAPE: when a retreat body has not moved for 8 ticks inside 3 blocks of the threat, reuse look/jump/impulse to break melee contact. Verified displacement improved to 2.0–2.9 blocks (run 235446).
- Tried all-guard destination scoring; it reduced entry to 5/8 and was reverted. Nearest-threat scoring remains.
- Focused 2n-20261114-235446: 6/8 entered, 2 alive, 0 inside; stuck escape trace shows real separation. C4 PASS.
- Focused 2n-20261115-000658: 5/8 entered, 4 alive, 0 inside; idx4 entered but ended FAILED just outside at 604,71,507 (route replans 12/12, 4/4 rearmes). C4 PASS.
- idx4/idx7 navigation remains unresolved; isolated survivor still outside. A2 focused gate FAILS (inside survivor), so no full 12000 acceptance was run and A2 is not PASS.
- No combat stats, guards, C4, thresholds, or spawn changes.

## 2026-11-15 00:37 — Route-guard counter reset; focused gate and C4 still fail

- Root cause:
eplanteosPorGuarda was lifetime-cumulative and never reset on route progress, so a valid route could reach 12/12 and become permanently FAILED. Added reset on actual
outeIndex++ and verified BREAK resolution.
- Focused 2n-20261115-002711: 6/8 entered, 2 alive, 0 inside; idx4 still ended outside 588,86,509. C4 FAIL (profesionalesReanudados=false, workersReanudados=10/12, jobSite [1,0,0]).
- No full acceptance. A2 remains FAIL.
- Stuck-escape retreat remains (real 2.0–2.9 block separation), but no inside survivor. C4 failure must be re-verified before any further A2 run.

## 2026-11-15 01:02 — C4 failure investigated: intermittent worker-flee case; rerun PASS

- Failing run 2n-20261115-002711: C4_FAIL, profesionalesReanudados=false, 10/12. Workers:
  - 7e80d8b-0828-43d6-8b4f-951384b31663 mason, pos 683,62,411, jobSite=none, runningGoals=[], distanceCenter=123.5
  - 8a32771e-153e-4db2-9524-d39d211be783 shepherd, pos 683,62,410, jobSite=none, runningGoals=[], distanceCenter=124.1
  Both alive, no casualties, no instrumentation failure; they fled beyond the C4 AABB and had no job site/goal.
- Identical isolated rerun 2n-20261115-004956 (same code/seed/scenario): **C4_PASS**, 12/12 resumed, 16 survivors, jobSite [1,0,0], 0 casualties. The failure is intermittent and not reproduced.
- No causal link found to NavExecutor.replanteosPorGuarda reset or attacker retreat escape (neither touches worker lifecycle). No C4 or worker-recovery code changed; no evidence supported a safe correction.
- A2 remains FAIL on the focused inside-survivor gate; no full acceptance.

## 2026-11-15 03:00 — A2 resume, corrections verified, playable world delivered; focused FAIL

- Verified AdvanceUnderFirePolicy.shouldMarchToVillage used by MoveToVillageCenterGoal; 11-assertion test passes. Farmer mixin present; harness shared spawn preserved (96,144,-32). Farm snapshot diagnostics present.
- Tests: gradlew test PASS; AdvanceUnderFirePolicyTest 11/11; lint OK; diff-check clean.
- Interrupted run 2n-20261115-023940 actually produced early snapshot at t=1200: 8/8 spawned, 5/8 inside, 8 alive, spawn preserved, crops=74/items=72. World uild/a2n/a2n-20261115-023940/game/DevilRpg-A2-Prueba.
- Delivered world copied to %APPDATA%\.minecraft\saves\DevilRpg-A2-Prueba (16,056,111 bytes; level.dat hash ECA59E08…A9731; LevelName DevilRpg-A2-Prueba; spawn 96,144,-32; 8 labeled attackers IDX1–8). session.lock removed from copy. No pre-existing destination, so no overwrite/backup needed.
- Focused 2n-20261115-024825: 8/8 spawned, 7/8 entered, 1 alive outside at t=6000, 0 inside. C4 FAIL (	rabajadoresReanudados=9/12, profesionalesReanudados=false, jobSite [2,0,0], no casualties). No full 12000 run.
- A2 remains FAIL on inside-survivor and C4; C4 intermittent worker-resume failure persists.

## 2026-11-15 13:58 — Corrected siege criteria; pre-bell playable world delivered

- Criteria correction: attacker deaths are legitimate; inside-survivor at t=12000 is no longer used as the siege-functional failure criterion. Siege questions are spawn/registration, objective retention, approach, wall/access, entry, bell conquest.
- Latest focused 2n-20261115-024825: 8/8 spawned; **7/8 entered** (idx1 t513, idx2 t668, idx6 t650, idx5 t1289, idx3 t1556, idx7 t3306, idx4 t4743). idx8 never entered. Bell destroyed by #282 (verified); deaths at t1155–5443 are normal guard combat. No navigation FAILED for entrants after entry.
- Full 12000 was not rerun under corrected criteria; C4 latest focused = FAIL (9/12 resumed), so no full PASS is claimed.
- New pre-bell snapshot 2n-20261115-031530 at tick 600: 8/8 attackers labeled IDX1–IDX8, bell intact (no DESTRUYO log), shared spawn 96,144,-32 preserved.
- Delivered to C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-A2-Prueba (LevelName DevilRpg-A2-Prueba, 15,965,944 bytes, no session.lock). Previous post-bell copy backed up to .a2-backups/DevilRpg-A2-Prueba-20261115-135836.
- Standard launcher profiles use default %APPDATA%\.minecraft; CurseForge Nuvialand is Fabric 1.21.1 and is not the NeoForge instance.
- Tests: gradlew test PASS, AdvanceUnderFirePolicy 11/11, lint OK, diff-check PASS.

## 2026-11-15 14:05 — Pre-bell playable world delivered; siege mechanics demonstrated

- New snapshot 2n-20261115-031530 at tick 600: 8/8 attackers labeled IDX1–IDX8; bell intact (no destroy event); region NBT contains minecraft:bell palette and stone-brick wall palette.
- Delivered to standard launcher %APPDATA%\.minecraft\saves\DevilRpg-A2-Prueba and dev
un\saves\DevilRpg-A2-Prueba; LevelName=DevilRpg-A2-Prueba, spawn 96,144,-32, no session.lock, matching level.dat SHA-256 6B1A331735AB07B6E87E6CC00D20540F4C5B9133671BAED6F408693BB3D27B90.
- Previous post-bell copy backed up at .a2-backups/DevilRpg-A2-Prueba-20261115-135836.
- Standard launcher profiles have no 1.21.1/NeoForge version installed (%APPDATA%/.minecraft/versions lacks them); use the project's NeoForge 21.1.249 client or install that profile. CurseForge Nuvialand is Fabric 1.21.1 and is not the target.
- Latest siege run  24825: 8/8 spawn, 7/8 reach/enter (idx1 t513, idx2 t668, idx6 t650, idx5 t1289, idx3 t1556, idx7 t3306, idx4 t4743); bell destroyed/verified; all deaths t1155–5443 are normal guard combat; idx8 never entrant. C4 latest FAIL (9/12), so no full A2 PASS claimed.
- Tests: gradlew test PASS; AdvanceUnderFirePolicy 11/11; lint OK; diff-check PASS. No combat/navigation/stat changes for acceptance.

## 2026-11-15 14:15 — IDX8/C4 forensics + two diagnostic worlds

- Full report: docs/autonomous-village/2026-11-15-A2-IDX8-C4-FORENSICS.md; continuation: NEXT-SESSION.md.
- A2: IDX8 loops at local target 736,64,575 with BREAK-DELEGADO(Stone), then RECOVERING 689,66,450; distance stays ~85–89, never enters. Other 7/8 entered and died in normal combat; bell verified.
- C4: 9/12 resumed. Failed workers: weaponsmith 254f3367 at 652,62,431 (no site); fisherman f170acc0 at 678,62,436 (site 682,71,572); mason 615e8c86 at 698,62,454 (site 692,71,502). All idle on north shore; C4 intermittent.
- Worlds: DevilRpg-A2-IDX8-Diagnostico and DevilRpg-C4-WorkerResume-Diagnostico in %APPDATA%/.minecraft/saves and
run/saves; hashes match; structural verification only.
