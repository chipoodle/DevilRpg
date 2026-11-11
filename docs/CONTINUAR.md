




## 2026-11-10 — D5 CERRADO: política explícita de prioridades y flags, D5-A–E PASS
La auditoría desmintió el supuesto "todos los oficios a prioridad 0": huir 0, taberna **-1**,
portones/puertas 2, guardia 3, oficios 4, reparación/recogida 5, recogida por oficio 6; pero
el **leñador estaba a 0** (su javadoc decía 4) y la **taberna a -1** (adelantaba a huir).
Nueva política única `VillageGoalPriority`: EMERGENCY 0 (huir), CRITICAL 1 (taberna/comer),
UTILITY 2 (portones, puertas, despachador), SECURITY 3 (guardia, reparación de constructor),
PROFESSION 4 (todos los oficios), SUPPORT 5 (reparación de obrero, recogida), OPPORTUNISTIC 6
(recogida por oficio). Leñador 4; taberna 1; guardia 3; reparación 5/3; recogida 5; pickup 6.
Semántica real de `GoalSelector` (fuente 1.21.1): orden de inserción, flags MOVE/LOOK
independientes, un número menor **puede** desbancar a uno mayor en ejecución (si es
interrumpible), e igual prioridad **nunca** se reemplaza (gana el primero registrado).
Runtime PASS (copias A5WALL): **D5-A** `policy-d5a8-20261110-203455` (prioridad 4 desbanca a
5; UUID `6cf8158a…`), **D5-B** `policy-d5b-20261110-203455` (dos goals a 4: uno solo MOVE,
primero RUN; UUID `8107cb43…`), **D5-C** `policy-d5c-20261110-203455` (MOVE y LOOK coexisten,
2 RUN; UUID `a6c57c47…`), **D5-D** `policy-d5d-20261110-203455` (stop 136 / start 136 sin
flags atascadas; UUID `2362bc61…`), **D5-E** `policy-d5e3-20261110-203455` (leñador 4
trabaja/entrega 5; huir 0/taberna 1/portón 2/puerta 2/reparación 5/pickup 6 en el estado
instalado; zombi no-AI → `flee RUN`, leñador idle; UUID `8107150d…`). Test
`VILLAGE_GOAL_PRIORITY_TEST` en `policyTest`.
**Estado de cierre (2026-11-10):** **A6-03 CLOSED**; **D4 COMPLETED / ACCEPTED**;
**D5 COMPLETED / ACCEPTED**; **Master Plan Block 4 COMPLETED**. Limitaciones conservadas:
claims D4 efímeros y reclamados tras recarga, TTL 200 ticks para abandono sin release, el
fixture D4-B retira el Goal de A tras el release de producción, `VillageNavigationGoal` sigue
flag-gated en PROFESSION y puede empatar con un oficio, y la taberna no se activó por hambre
(huir sí se demostró). No se describe nada no probado como probado. El checkpoint de
integración es el commit de esta campaña (hash en el informe de cierre).

### Próximo: A8-01 y A7-12 — definiciones exactas y orden

**A8-01 — End-to-End Forestry Resource Trace. Status: PARTIAL.** Objetivo: trazar cada tronco
de la tala hasta el almacén con contabilidad **por actor**. Comportamiento requerido: ledger
por actor de inserciones/extracciones de inventario y depósitos/retiradas de almacén, por
ítem y tick, sin cambiar producción. Criterio de aceptación: reconciliación completa
(inicial + talado − depositado − restante − drops) sin diferencias inexplicadas; hoy no está:
el almacén es compartido y no existe ledger por actor, solo se explicó la dirección del
desfase (T2/b89, Runs 209–212). Archivos: `VillagerLumberjackGoal.picarTronco`,
`VillageStorage.guardar`, `VillagePantry.guardar`, `VillagerPickupGoal`; evidencia
`A8-01-RESOURCE-TRACE.md`. Falta: instrumentación/ledger por actor; no hay defecto de
producción demostrado.

**A7-12 — Controlled OFF/AUDIT Reproducibility and Final Acceptance. Status: PARTIAL /
non-equivalent.** Objetivo: comparar OFF y AUDIT con el mismo fixture y manifiestos
equivalentes. Comportamiento requerido: misma selección de tarea/target y contabilidad de
recursos equivalente. Criterio de aceptación: equivalencia de recursos demostrada con
reconciliación completa y manifiesto inicial capturado; hoy no está: dos pares muestran OFF
+1 tronco, sin causalidad probada y sin manifiesto completo al tick 0. Decisión documentada:
OFF sigue siendo el default, AUDIT solo diagnóstico, **no** transferir autoridad; recomendar
cerrar A7 como validación-inconclusa. Evidencia: `A7-12-FINAL-ACCEPTANCE.md`,
`A8-01-RESOURCE-TRACE.md`.

**Dependencia y secuencia:** A7-12 depende del ledger por actor de A8-01 para eliminar el
confounding del almacén compartido; hacer **A8-01 primero** y después A7-12. No repetir la
comparación a ciegas ni implementar ninguno en esta campaña.

## 2026-11-10 — D4 CORREGIDO: claim efímero entre trabajadores, D4-A–E PASS
El defecto D4 (dos leñadores, un árbol, sin claim) queda corregido con el mecanismo más pequeño:
`agent/ForestryTreeClaims` es un registro **en memoria, sincronizado y con alcance
(dimensión, BlockPos del árbol)**. No es el lease de navegación (ese es por agente y modela
navegación, no la madera): el Goal adquiere el claim **antes de exponer el target**, la
búsqueda (`baseDeArbol`, restos colgando) salta árboles de otro trabajador, el dueño renueva
cada tick, y la liberación valida **dueño+task** (un dueño rancio no borra un claim nuevo).
Se libera al completar, en `stop()`, en la invalidación/abandono, y en muerte/descarga
(`RELEASE-ALL`); sin renovación caduca a los 200 ticks. Sin NBT: efímero; al recargar se
redescubre.
Runtime PASS (copias A5WALL, ENFORCE): **D4-A** `claim-d4a-20261110-201309` (A `40bc5e1c…`
reclama 98,98,0; B `c1254e6a…` target=null; A entrega 5), **D4-B**
`claim-d4b-20261110-201309` (A libera en 4309 por atasco; B `726494fe…` reclama en 4316 y
entrega 5), **D4-C** `claim-d4c-20261110-201309` (muerte de A → RELEASE-ALL tick 128; B
`bcde1078…` reclama 4156 y entrega 5), **D4-D** `claim-d4d-20261110-201309` (B
`0028aa91…`/98,0 y A `a87f2b8a…`/62,0 concurrentes; almacén +10), **D4-E**
`claim-d4e-20261110-201309` (B `d75607bb…` único claim; A `bb6ac88e…` sin target; +5, sin
doble tala).
Tests: `ForestryTreeClaimsTest` en `policyTest` (adquisición atómica, denegación, árboles
distintos, dimensiones, release validado, release rancio, expiración, releaseAll).
**SIGUIENTE: D5** — política explícita de prioridades/flag conflicts (los goals a igual
prioridad siguen resolviéndose por orden de inserción; no se tocó en esta campaña). A8-01 y
A7-12 siguen abiertos. Sin commit/push en esta campaña.
## 2026-11-10 — A6-03 CERRADA: R2/R5-A/R5-D/R4-160/200/R6/R7 PASS; D4 CONFIRMADO y D5 auditado
La campaña runtime de A6-03 queda **CERRADA** con evidencia nueva (copias desechables de A5WALL,
runs `a6-*`). Resultados: **R2 PASS** (primer trabajo PLANTAR, sapling 16→15, luego 5 troncos;
la causa del bloqueo era el fixture: la arboleda replantaba plantones y el trabajador seguía
`planton=53,-17`; se corrigió controlando la arboleda, sin tocar producción); **R5-A PASS**
(umbral 12→almacén +12, tick 528); **R5-D PASS** (fallo tick 4470, cooldown 4510, éxito 4558);
**R4 PASS con topes 160/200** (fallos 4470/4558/4686/4854/5062; cooldownUntil
4510/4638/4806/5014/5262; el intento 6 empieza exactamente en 5262); **R6 PASS** (cosecha 4,
fallo post-cosecha 5094, cooldown 5134, éxito 5746); **R7 PASS de recuperación** (save tras
fallo 4530; load entrega 12 sin pérdida; limitación: el reloj ya había pasado el deadline, no se
distingue el reset transitorio de la expiración normal).
**D4 CONFIRMADO**: dos leñadores (A=02a0f5b6…, B=ca87ea53…) seleccionaron el MISMO árbol
`98,98,0` con tasks distintas (49935721…/79238237…), cada uno con su lease de agente; A taló y
entregó 5, B siguió apuntando al árbol ausente y luego recuperó (target=null, luego
RECOGER_HARINA/ABONAR); no existe claim entre trabajadores. **D5 CONFIRMADO (auditoría)**:
con dos goals a la misma prioridad, gana el primero añadido que posee MOVE/LOOK
(A=lumberjack, B=competing), no hay preempción por prioridad ni doble navegación en el mismo
trabajador. Sin cambios de producción; los fixtures D5/D4 son test-only en el controlador
ignorado.
**SIGUIENTE: Block 4 paso 2** — añadir el claim mínimo entre trabajadores para un mismo árbol
(consistente con las parcelas; nada de registro global) y probarlo con el fixture de dos
leñadores; después, la política explícita de prioridades/flag conflicts (D5). A8-01 y A7-12
siguen abiertos y no se tocan.
## 2026-11-10 — A8-F03 CERRADA Y ACEPTADA: la navegación TALAR no reutiliza un objetivo rancio
A8-F03 (paso de navegación TALAR propiedad del Agent) queda **COMPLETADA / ACEPTADA**.
Evidencia runtime post-fix, toda PASS: C1 (objetivo retirado entre la selección del Step y la
autorización), C2 (candidato no-log), C3 (obstrucción y recuperación), portón válido
(`gate=true` y cruce físico) y portón negativo (sin cruce). Verificación: `compileJava`,
`lint_aldea.py --strict`, `git diff --check`, `a8NavTests` y los tres grupos
`NAVIGATION_LEASE_TEST` / `NAVIGATION_GATEWAY_POLICY_TEST` / `NAVIGATION_GATEWAY_HARNESS_TEST`.
Detalle: `docs/autonomous-village/A8-F03-FRESH-SESSION-EXECUTION.md` (§14 corrección y
regresión; §§3–4 conservan los FAIL históricos, no se reescriben).
Corrección de producción: `TalarStepPlanner.isValidTalarTarget()` es el predicado autoritativo
(`BlockTags.LOGS`); `Step(null)` ya **no** cae al objetivo anterior; el objetivo inválido se
abandona por el ciclo normal (parar navegación, liberar lease, punto fallido, `target=null`,
SCOUTING); `navegar()` valida justo antes de autorizar y `syncNavigationLease()` rechaza un
TALAR inválido. Sin cambios en ENTREGAR, geometría de portones ni NBT.
**SIGUIENTE BLOQUE: A6-03 (runtime restante) y, con él, Block 4 del plan maestro (D4/D5).**
Estado: R1 Run186, R3 Run185, R4 40/80/120 Run182 y R5-B/C Run192 PASS; R2 **BLOCKED**
(la arboleda/plantón ignora el árbol controlado), R5-A/D, R6 y R7 **NOT TESTED**, tope
160/200 de R4 no verificado. D4 (dos leñadores, un mismo árbol) sigue sin prueba runtime.
Ver `docs/autonomous-village/LUMBERJACK-PHASE-1-ACCEPTANCE.md` §A6-03 y
`docs/autonomous-village/AUTONOMOUS-VILLAGE-MASTER-PLAN.md` §H Block 4.
## 2026-11-09 — A6-03 Fix7: actual DELIVERY-DIAG; R5-B/C PASS (Run192)
12 logs, failures 4335/4679/4981, cooldowns 40/80/120 respected. R5-D/R6/R7 not
tested; R2 blocked.
## 2026-11-09 — A6-03 Fix6: R2 still blocked; R5-R7 not tested
Worker ignores controlled tree/loops planton. A6-03 remains BLOCKED.
## 2026-11-09 — A6-03 Fix5: R1/R3/R4 pass; R2 blocked; R5-R7 not tested
Priority fix retained. R2 arboleda regrowth invalidated hole fixture.
## 2026-11-09 — A6-03 Fix4: small-cargo preemption fixed
R3 growing priority PASS, R1 retest PASS, R4 PASS. R2 blocked (hole replanted);
R5-R7 not tested.
## 2026-11-09 — A6-03 R4 bounded retry PASS (Run182)
Real attempts n1,n3,n4,n5; n2 was harina. Cooldowns 40/80/120 respected; 5 logs
retained. R2/R3/R5/R6/R7 not tested.
## 2026-11-09 — A6-03 Fix2: cooldown audit; R4 inconclusive
All paths gated; Run181 5 logs retained, 5 target events, intervals 328/200/440/52
(not all delivery). R2/R3/R7 not tested. A6-03 BLOCKED runtime.
## 2026-11-09 — A6-03 idle delivery implemented (Run180 R1 PASS)
Idle fallback + absolute cooldown gating. 5 logs delivered once, carried0. R2/R3
source; R4/R7 not tested.
## 2026-11-09 — A6-02 Fix3: full-inventory boundary PASS (Run178)
Delivering with logs16/all slots full, pantry16 -> final logs16 invSap0 pantry16.
Withdraw16/return16 same tick; no loss/dup. C/D not applicable.
## 2026-11-09 — A6-02 Fix2: A PASS; B blocked; C/D structural
Run177 did not enter entregar (no delivery log). C/D not applicable by source proof.
World fallback untested.
## 2026-11-09 — A6-02 sapling withdrawal accounting
Uses actual sacadas; returns inventory remainder to pantry; world fallback only if
pantry full. Run176 pantry1->0 inv1 no dup. B/C/D BLOCKED naturally.
## 2026-11-09 — A6-01 exact overflow PASS (Run173)
One tree, 5 logs harvested, 0 inventory, 0 storage, 5 world overflow. Equation exact.
UUID pickup not tested.
## 2026-11-09 — A6-01 harvest overflow fallback
spawnAtLocation for storage remainder; Run171 worldLogs=4 carried0 warehouse0.
Exact full-tree equation NOT VERIFIED (unplanned trees). No schema change.
## 2026-11-09 — A5-05 Fix5: ENTREGAR/SCOUTING/null intentional
No idle phase exists; phase=last work, controller=lifecycle. NBT restoration accepts
it. No code change. Run167 remains evidence.
## 2026-11-09 — A5-05 Fix4: isolated T2 PASS (Run167)
C0=5 C1=0 A=5 W 122->127, no second deposit 200 ticks. T1 Run164 PASS, T3 Run161.
Phase remains ENTREGAR at cleanup; controller SCOUTING/target null.
## 2026-11-09 — A5-05 Fix3: T1 PASS, T2 PARTIAL (Runs164/163)
Fresh FLETCHER canUse delivery PASS (16 logs, DELIVERING). T2 natural harvest +
DELIVERING observed; exactly-one attribution NOT VERIFIED due shared village.
## 2026-11-09 — A5-05 Fix2B: T1/T2 BLOCKED, T3 PASS (prior)
T1 canUse not isolatable (goal installed before logs); T2 no natural harvest in
fixture. T3 Run161 PASS. Priority review recorded; no policy changes.
## 2026-11-09 — A5-05 Fix2A: live delivery discovery + T3 PASS (Run161)
discover picks ENTREGAR at >=12 logs; reload delivered exactly16 once, warehouse
123->139, carried16->0. T1/T2 NOT TESTED.
## 2026-11-08 — A5-05 fix1: DELIVERING made stable (runtime not reached)
start/canContinue/special path updated; schema unchanged. Runs158-160 did not reach
DELIVERING (standard discovery gap, no natural harvest). T1 blocked, T2/T3 NOT TESTED.
## 2026-11-07 — A5-04 PASS + A5-03 regression PASS (Runs156-157)
start() preserves restored controller. GROWING resumed one BoneMeal and exited;
REPLANTING resumed one oak_sapling. A5-04 PASS. A5-05 NOT RUN.
## 2026-11-07 — A5-04 reload FAIL: start() clobbers GROWING
Save ABONAR/GROWING target53,-17 bone16; reload restored NBT but Goal start()
assigned TRAVELLING, no growCrop. A5-04 BLOCKED; production fix not applied.
## 2026-11-07 — A5-04 fix: GROWING persists a tick, then executes (Run154)
Entry tick saves ABONAR/GROWING, no growCrop; next tick consumes once; stage0->1 and
->oak_log. A5-04 reload NOT RUN.
## 2026-11-07 — A5-04 fix: growing() added before growCrop
Source transition verified; Run152 ABONO consumed1 no-growth and exited SCOUTING.
GROWING not observed at tick boundary (same-tick cleanup). A5-04 reload NOT RUN.
## 2026-11-07 — A5-04 BLOCKED: controller GROWING unreachable
No production taskController.growing() call; ABONAR stays TRAVELLING. A5-04 NOT
RUN; no fixture/fabrication. A5-04 not accepted.
## 2026-11-07 — A5-03 REPLANTING save/reload PASS (Run151)
Saved PLANTAR/REPLANTING target36,97,-31 sap8 holes1; reload planted oak_sapling
once, sap7 holes0. A5-03 PASS. A5-04 still NOT RUN.
## 2026-11-07 — A5-03.1 planting placement PASS (Run149)
Hole45,97,-31 air->oak_sapling, sap8->7, holes1->0, controller REPLANTING. Cases
B-D not tested; A5-03 persistence NOT RUN.
## 2026-11-07 — A5-03 fix: live PLANTAR selection reached (Run148)
Shared helper used by canUse+descubrirTrabajo. Real hole53,97,-17 accepted,
PLANTAR selected, sap8->7, hole removed. Direct block-placement log not captured;
A5-03 reload still NOT RUN.
## 2026-11-07 — REPLANTING root cause: decision in canUse, tick uses descubrirTrabajo
Pending-hole/PLANTAR branch lives only in canUse; running goal with target null
calls descubrirTrabajo, which lacks it. primerPendiente never evaluated. A5-03
blocked; no production fix.
## 2026-11-07 — Pending-hole forensics BLOCKED (Runs144-145)
Recorded holes pass air/dirt/arboleda checks but no PLANTAR. Source defect: rejected
holes removed from runtime list without persisting, so NBT Holes is stale. Exact
predicate needs production accessor; STOP. REPLANTING not verified.
## 2026-11-07 — REPLANTING runtime still NOT VERIFIED (Runs141-143)
Pending hole + 9-13 saplings did not trigger PLANTAR; production fell through to
TALAR. No REPLANTING observed. A5-03 not run.
## 2026-11-07 — REPLANTING runtime NOT VERIFIED (Run140)
Worker stayed TALAR/HARVESTING/SCOUTING/ENTREGAR, saps 0->6, never PLANTAR/
REPLANTING in ~8000 ticks. A5-03 not run; no production change.
## 2026-11-07 — REPLANTING controller transition corrected
Transition added at first pending-hole planting swing; immediate post-chop replant
remains HARVESTING sub-step. Policy tests pass. A5-03 reload NOT RUN. Phase1 not
accepted.
## 2026-11-07 — A5-03 BLOCKED: controller REPLANTING unreachable
No production call to taskController.replanting(); PLANTAR stays TRAVELLING.
Fixture not fabricated; A5-03 NOT RUN.
## 2026-11-07 — A5-02 HARVESTING reload PASS (Run139)
Pre-save 119 storage/0 carried; after reload identical; resumed harvest +5 logs
delivered, 0 carried, final 124. No duplication. Saplings 0->3 (leaves), BoneMeal 0.
## 2026-11-07 — A5-02 HARVESTING partial PASS (Run 138)
Added real controller HARVESTING transition on chopping start. Save TALAR/HARVESTING
target49,97,-31; reload safe re-scout after real harvest. Duplicate-drop ledger
not captured. Phase1 not accepted.
## 2026-11-07 — A5-02 BLOCKED: controller HARVESTING unreachable
VillagerLumberjackGoal never calls taskController.harvesting/replanting/growing/
returning; TALAR stays TRAVELLING. Fixture not fabricated; A5-02 NOT RUN.
## 2026-11-07 — Malformed variants: 2 PASS, resource reloads NOT RUN
Run136 missing fields PASS; Run137 invalid phase PASS. Harvest/replant/BoneMeal/
delivery/two-worker reload fixtures and Block1 regressions remain NOT RUN.
Phase1 not accepted.
## 2026-11-07 — A5-09 malformed (missing fields) PASS run136
Task removed, goal remained, no crash. Other malformed variants and A5-02..05
resource fixtures NOT RUN. Phase1 not accepted.
## 2026-11-07 — Forestry A5 expansion partially executed
PASS: smoke run134 (TRAVELLING/RECOGER_HARINA), unknown-version run135
(Version99 marker preserved). A5-02..09/11/12 and Block1 regressions NOT RUN.
Phase1 not accepted.
## 2026-11-07 — First forestry reload smoke test PASS
Run `stabilization-block1-scan-20261030-134`: UUID b89fcee1-, objective0,
RECOGER_HARINA/TRAVELLING target107,97,16 survived real save/reload; goal
reinstalled. Remaining A5-02..12 + Block1 regressions NOT RUN. Phase1 not accepted.
## 2026-11-07 — A5 suite attempted: NOT RUN
Blocked: Phase 1 A5 reload scenarios are not implemented in the harness. Build,
policyTest, lint, and diff pass. Phase 1 remains NOT ACCEPTED.
## 2026-11-07 — Forestry pre-A5 corrections
Event-driven persistence (no per-tick save), hole species serialized, unknown
higher-version writes suppressed. A5 reload suite and Block 1 regressions remain
NOT RUN; Phase 1 still not accepted.
## 2026-11-07 — Lumberjack reload persistence implemented, NOT runtime-accepted
Source + pure tests complete: `DevilRpgForestryTask` v1, controller/planner
restore, validation, unknown-version preservation. A5 reload scenarios and Block 1
regressions still pending. Do not claim Phase 1 accepted.
## 2026-11-07 — Lumberjack reload design
Design only: `docs/autonomous-village/LUMBERJACK-PHASE-1-RELOAD-DESIGN.md`.
Revised design: villager-NBT schema, per-phase recovery, crash-window audit,
separate wood/sapling/Bone Meal conservation, version/identity policy, and
save-boundary acceptance. Implementation NOT started.
## 2026-11-07 — Lumberjack Phase 1 audit
Read-only audit completed: `docs/autonomous-village/LUMBERJACK-PHASE-1-AUDIT.md`.
Block 1 implemented; main gaps are reload persistence of controller/planner,
delivery/inventory edge acceptance, and long-run endurance. No Lumberjack
implementation started.# CONTINUAR — estado vivo

## 2026-11-07 — Food Economy Block 3.2 CERRADO
Completo y verificado en runtime: hambre individual, alimentación normal a 60,
reducción de 25, guardia en paz/asalto, reanudación de defensa, efectos/muerte por
hambruna, crédito de despensa, persistencia tras guardar/cargar y segunda comida
diurna tras recarga.
Evidencia: Runs 105, 109, 110, 120, 128, 130, 132.
Documentos: `docs/autonomous-village/FOOD-ECONOMY-ARCHITECTURE.md` y
`FOOD-ECONOMY-BLOCK-3.2-ACCEPTANCE.md`.
Limitación documentada: no hay test automático de serialización NBT de
`VillageSavedData`.
Siguiente fase: auditoría de fuentes y requisitos del Lumberjack. No se ha
iniciado implementación del Lumberjack.
# CONTINUAR AQUÍ — acuerdos, convenciones y pendientes (leer lo PRIMERO)

Rama de trabajo actual: **`feature/autonomous-village`**. La rama histórica `migration-neoforge-1.21.1` se conserva
como referencia; no se crea una rama nueva sin cerrar los pendientes.
**Última actualización: 10-nov-2026 (D5 cerrado; siguiente A8-01/A7-12).** La entrada más
reciente está al principio del documento; §2.1 y las listas anteriores conservan el historial; no son una segunda cola activa.

---

## 0 · QUE ESCRIBIRLE AL AGENTE EN EL CHAT PARA QUE SIGA

**Frase corta (recomendada):**

> Lee la sección final de `docs/CONTINUAR.md` («RECONCILIACIÓN 22-oct-2026») y sigue la tarea recomendada; mide con el arnés y no des por cerrado nada sin evidencia runtime.

**Variante, si quieres que empiece por algo concreto:**

> Lee la sección final de `docs/CONTINUAR.md` y empieza por A1-CLIENT (armas de los cuatro fundadores visibles en un cliente aislado).

Con eso basta: §2.1 contiene la cola vigente; §2.0 conserva el historial detallado. El documento también contiene las
reglas de trabajo y las herramientas de medida.

---

## 1 · ACUERDOS Y CONVENCIONES (la forma de trabajar de esta casa)

**ESCRIBIR PARA QUE SE ENTIENDA, NO PARA PARECER DEL GREMIO** (8-oct-2026). Lo pidio el jugador, y con razon: yo
escribia *«la traza **canta** los dos numeros»* queriendo decir *«la traza **los escribe en el registro**»*. Jerga mia,
ni inglesa ni tecnica, y si hay que explicarla **esta mal escrita**. Se barrieron **55 apariciones** en 14 ficheros
(docs, herramientas y comentarios del codigo, incluido un metodo que se llamaba `cantarLaMedidaDelDespachador` y ahora
se llama `escribirLaMedidaDelDespachador`). **Regla**: si una palabra necesita traduccion para el jugador, no se usa —
se escribe lo que hace: *escribe*, *dice*, *imprime*, *deja en el registro*, *avisa*. Vale para los documentos, para los
comentarios del codigo y para los mensajes del registro.

**Y LA SEGUNDA PALABRA QUE CAYO FUE «BICHO»** (9-oct-2026, y la cazo el jugador con una pregunta: *«¿que quieres decir
con bichos? ¿te refieres a mobs?»*). Con razon: **no la dice el jugador** (buscado en las frases suyas entre comillas) y
**no estaba en el glosario** — era palabra mia. Se barrieron **361 apariciones en 25 ficheros** y se cambio por
**`monstruo`**, que es lo que el mod ya dice en el texto que ve el jugador (`[Horda] los monstruos marchan contra una
aldea cercana`) y lo que son esas entidades (`net.minecraft.world.entity.monster.Monster`). Incluye los nombres:
`BICHO_DENTRO` → `MONSTRUO_DENTRO`, `BICHO_EN` → `MONSTRUO_EN`, `mantenerBichoDentro` → `mantenerMonstruoDentro`,
`bichosDentro` → `monstruosDentro`, `bichosCerca` → `monstruosCerca`, `cotaBichos` → `cotaMonstruos`. **Aviso para el
futuro**: el termino «mob» se puede usar entre nosotros, pero **en el texto del juego se dice `monstruo`** (o el nombre
del que sea: zombi, esqueleto…).

1. **Medir antes de tocar.** Ningun cambio entra sin su medida. Lo que no mejora la media, **se retira y se dice**.
2. **No suponer nunca.** Cada hipotesis falsa se deja **escrita como falsa** en el acta (hay varias mias, a proposito).
3. **Antes de retirar una idea, comprobar si falla la IDEA o la IMPLEMENTACION** (me paso con I200/I201: la idea era
   buena, la implementacion no).
4. **Nunca encadenar `compilar` y `commitear` sin mirar el resultado**: se commiteo un build roto una vez (se enmendo).
   Compilar y lint van en pasos separados y el commit **solo corre si estan verdes**.
5. **Un goal sin la bandera `MOVE` no puede tocar el camino de otro goal** (es la causa del "baile" de los aldeanos).
6. **NO reescribir toda la logica del aldeano**: se probo y se retiro con medidas (`docs/aldea-cerebro.md` §3), y
   produce **el mismo baile**. Tampoco hay que copiar los goals de vanilla: el conflicto era **entre goals del mod**.
7. **El centro de una aldea es EL DEL OBJETIVO**, nunca el punto medio de lo construido (eso la desplazaba al este y la
   duplicaba). Y **una reparacion NO puede mover una aldea**.
8. **El arnes NUNCA compilado en la partida del jugador**: candado por variable de entorno `DEVILRPG_ARNES=1` + perro
   guardian al arrancar que avisa si `GuardHarness` esta en el classpath.
9. **Nunca dos Gradle ni dos arneses a la vez**; y **no se tocan ficheros Java mientras un arnes mide**.
10. **El juego se puede matar** si tiene el `.jar` bloqueado (permiso permanente del jugador) y se sigue compilando.
11. **Metricas de verdad** (no cuentos de lineas): `PARAR`, `[Rumbo]`, `[Huerta]`, `[Planta]`, `no consigue llegar`,
    `estaba METIDO`, `OLEADA`, `Attributes Scaled`. Ojo: **`PARAR` va limitado a una linea cada 2 s por aldeano**, asi
    que **para el baile fino se lee su numero** (`paradas=N (con faena=M)`, **I246**: en la corrida 182, 91 lineas y
    1790 paradas) ✓;
12. **Documentar SIEMPRE** en `docs/aldea-invariantes.md`: que se ha cambiado, **con clase y numero de linea**, **la
    medida** que lo justifica, y **las hipotesis falsas** que se descartaron. Cada cambio se **compila**, pasa
    `python tools/lint_aldea.py --strict`, se **commitea** con titulo en MAYUSCULAS y **se sube** a GitHub.
13. **Titulos de commit**: prefijo `I###` si es una invariante numerada, o titulo descriptivo; dentro, **las frases
    literales del jugador** entre comillas y **los numeros medidos**.
14. **Idioma y palabras**: el texto que ve el jugador, en **castellano llano**; nada de jerga ("cota" se queda en el
    codigo, en pantalla se dice "nivel del pueblo"); si una palabra no se entiende, se cambia por una clara o en ingles.
15. **Nada de parches que simulen una solucion**: soluciones **de raiz**, y nunca tocar lo que ya funciona (se avisa si
    algo se ha comprobado y **no** se ha tocado, a proposito).
16. **Clase y numero de linea siempre** al explicar un cambio, para poder revisarlo en el IDE.
17. **Entorno**: `$env:GRADLE_USER_HOME="C:\Users\Christian\Documents\DevilRpg\.gradle-home"`; partidas en `run/saves`;
    **registro vivo en `run/logs/latest.log`** (el agente lo lee el solo, no hay que pegarlo); config de servidor en
    `run/config/devilrpg-server.toml` (`logEscaladoDeSpawn` se enciende para medir las velocidades del asedio).
18. **No hay rama nueva por ahora**: se trabaja en `migration-neoforge-1.21.1` hasta cerrar los pendientes.
19. **LO CERRADO SE MARCA, Y LAS NOTAS VIEJAS NO SE BORRAN** (9-oct-2026, y lo pidio el jugador: *«corrige toda la
    documentacion y anota como cerrado lo que ya esta»*). La documentacion acumula anos y **miente sin querer**: una nota
    que decia «queda abierto: el giro interpolado del porton» hizo que el agente propusiera **hacer lo que ya estaba
    hecho** (`PortonDobleBlockEntity` + `PortonDobleRenderer`): lo cazo el jugador. **Regla**: cuando una nota vieja dice
    que algo esta pendiente, **se comprueba** (en el acta, en el codigo o en los registros) y:
    - si **ya esta hecho/medido**, se le pone DEBAJO la marca
      `> ✅ **CERRADO (comprobado el <fecha>)**: <que lo cerro, con I### y/o `Clase` L###>`;
    - si **sigue abierto**, se le pone `> ⚠️ **SIGUE ABIERTO (comprobado el <fecha>)**: <por que> — esta en
      `docs/CONTINUAR.md` §2.1`;
    - y **el texto viejo NO se borra** (cuenta el camino y las hipotesis falsas, que son parte de la medida).
    **El unico traspaso vivo es este fichero** (`docs/CONTINUAR.md`, su §2.1); `docs/PENDIENTE.md` es el viejo y esta
    marcado como tal; y las entradas del acta son **actas**, no listas de tareas.

---

## 2 · PENDIENTES, EN ORDEN (con clase y numero de linea)

### 2.0 · LO QUE ESTA ABIERTO **HOY** (puesto al dia el 9-oct-2026)

> **HISTÓRICA.** Esta lista de pendientes quedó **superada**: la lista **VIVA** es la **§2.1** (al final del documento). Se conserva como historia de lo que se estaba haciendo el 9-oct-2026.

**TANDA DEL 12-oct-2026 (I353-I355) — LOS DOS REPORTES CON CAPTURA, CERRADOS** ✓:
1. **EL ASEDIO NO ENTRABA** ✓: medido en su registro en vivo — **228** `pica Block`, **todos a y=60-61**, o sea **por DEBAJO**
   del suelo del pueblo (63), 5 escalones y **0 brechas** ✗; y **no** era el bucle de reponer el muro (`muro reconstruido` = **0**
   ✓). La causa: `AggressiveZombieEntity.breakBlockTowards` (**L376**) miraba candidatos **solo con `dy = 0..1`** (su propia
   altura) aunque sabe que el objetivo esta arriba (**L411**). **Arreglo**: mira **tambien hacia arriba**, hasta la altura del
   objetivo (**tope 3**), manteniendo la preferencia por lo bajo (**e2a1cba**). **Su prueba**: en el proximo asedio los
   `pica Block` deben salir **a y=63** y aparecer **brecha/entrada** ✓ (hoy: a 60-61 y ninguna ✗).
2. **LA ALDEA CON PISO DE AGUA (la del lago)** ✓: medido — aldea 1, **cota 63** (lo dice el auditor ✓) con **7192 celdas de agua
   dentro del recinto y 4503 columnas de agua a y=63** ✗. **Arreglo**: `VillageGenerator.ponerPisoDondeHayAgua` pone **bloque en
   la cota y tierra firme debajo** (el piso como el resto del pueblo: el suelo se pisa a la cota y la tierra va una por debajo),
   **respetando el lago de la pesquera** (`esCeldaDelLago`) y sin tocar nada construido; va en el **LATIDO** (en la pasada **NO**
   se ejecutaba ✗, medido: 0 trazas) (**b4eb947**, **1b0a5a3**, **744e475**). **Medido en la copia despues**: `y=62
   grass_block=5951` + piedra/barro/arenisca/arena (**piso solido** ✓) y `y=63 air=10881` + vallas/troncos/cobblestone/trigo
   (**sin agua** ✓), **columnas con agua en la cota: NINGUNA** ✓. El agua que queda (2798) esta **debajo** del suelo: es el lago
   bajo la aldea, por diseno ✓.
**Y DOS ERRORES MIOS DE ESTA TANDA, DICHOS** ✗✓: **(a)** el contador de agua tenia una linea que **escribia la cota por su
cuenta** en vez de medir la altura ✗ (de ahi salio mi falsa conclusion de I347, *«no inunda»* ✗, ya retirada ✓); **(b)** al
escribir el acta **la trunque** (2887 lineas ✗) por olvidar la cola del fichero — restaurada con `git checkout HEAD~1` ✓ y
reescrita ✓, y desde ahora **los scripts de documentos comprueban que el fichero CRECE** o abortan ✓.

**TANDA DEL 11-oct-2026 (I296-I346) — CERRADO Y MEDIDO** ✓: la **granja entera** (0 SIN SEMBRAR · 0 PISOTEADA · 0 atoros ·
0 `pies=water`; con sus dos causas de raíz: el paso de las compuertas encharcado, **I300**, y **dos goals por la misma
compuerta**, **I301**) · los **restos flotantes** (**408 -> 0**, I306/I329/I330, y la leccion: el barrido miraba **dentro
del recinto** y los restos estaban **fuera**) · las **camas** (tejados **4 -> 0** y **`El obrero repuso red_bed` = 0**, con el
arreglo del **PLAN**, I298/I323/I325) · el **muro del asedio** (guardia por el **estado real** `hayAsedio`: **1761 -> 0**
avisos falsos, I338) y el asaltante que **no pica madera** (I297) · el **haz** (solo dentro del recinto de su aldea, I303) ·
el **pantano** (la cota por encima del agua, I304) · el **invocador** atado a su nucleo (candado en `setTarget`, I317) · y el
**hambre** (colchon que escala con la poblacion, I344: `pasa hambre` = 0). **El auditor del proyecto da 0 cosas mal** en las
dos aldeas del jugador (I346). Detalle con **clase, linea y medida**: `docs/aldea-invariantes.md`.

**LO QUE SIGUE ABIERTO DE VERDAD** ✗: los tres de **(a)** — que solo se cierran **jugando** (el cruce del porton del muro,
el asedio EN VIVO con un jugador de verdad, y la barra dibujada en el cliente) ✓ — y, **dentro de un asedio**, la medida del
muro (`muro reconstruido`) ✗, que en el arnes **no se puede ver** porque el asedio queda **EN PAUSA** (`hayAsedio=true` con
`agresivos=0`, I345).

**LO ABIERTO HOY, entero y comprobado contra el acta** (9-oct-2026; lo de mas abajo es el historial, con lo que se fue
cerrando y **como**). Va separado por **quien puede cerrarlo**:

**(a) SOLO SE PUEDEN CERRAR JUGANDO** (el arnes no tiene ventana ni jugador de verdad):
1. **El cruce del aldeano por el porton del muro** (**I222**, acta L6436) — **ACOTADO CON SUS DATOS** ✓/✗ (9-oct-2026,
   **I273**): en su partida el `VillageGateGoal` **corre y mucho** (sale en las lineas `PARAR` 40, 18, 14, 10… veces ✓)
   pero **`[Gate]` sale 0 veces** ✗ → el caso del **porton del MURO** no dispara (los que usa son los del anexo/parcelas ✓);
   y en la MISMA sesion sale **20 veces** `el muro NO esta a la cota` ✗ → **sospecha con fundamento**: con el muro
   desnivelado, el porton del muro puede estar en otra celda que la que tiene apuntada la lista `todosLosPortones` (I4) y
   `VillageGateGoal.esPortonDelMuro` (L514/L523) **no lo reconoce** ✗. **Siguiente medida (sin tocar codigo)**: comparar
   la celda del porton del muro **en la lista** con la que hay **en el mundo** de su aldea. Lo de abajo es el historial:
   cruce; el pueblo abre **0** portones en una corrida normal (**I223**). En el registro tiene que salir
   `[Gate] … aldeano DENTRO · destino FUERA` y despues el aldeano fuera.
2. **El asedio EN VIVO** — **MEDIDO Y CON LA PAUSA VERIFICADA** ✓✓ (9-oct-2026, **I287**): en la copia temporal del arnés se
   encendió `MEDIR_ASEDIO_VIVO` (que saca al jugador de pega de la aldea, L134/L2127) y la traza **salió**: *«Asedio de
   la aldea 3 EN PAUSA: el jugador que la defiende NO esta conectado: el reloj se para y la aldea NO puede caer»* ✓✓ —
   el camino de `VillageManager` L1438-1445, con su motivo y con la regla que protege la partida ✓. **Queda la otra
   variante** (jugador **conectado pero lejos**, con su distancia ✓), que necesita un jugador de verdad → **su prueba
   viva**: >128 bloques y volver (I86/I89). El instrumento está verificado ✓.
   **MEDIDO** ✓✓ (`OLEADA de 8 intentados, 8 colocados (radio 65-73)` + `Aldea 0 salvada`), y
   **LA PAUSA YA TIENE SU TRAZA** ✓✓ (**I283**/**I284**): `VillageManager` **L1438-1445** ya avisa **al cambiar de estado**
   (*«Asedio de la aldea N EN PAUSA: … el reloj se para y la aldea NO puede caer»*), condicionada a `jugadorEnLaAldea`
   (L2182-2186: fuera de `RADIO_ASEDIO_CON_JUGADOR` no avanza el reloj ✓; y con el jugador desconectado,
   `DISTANCIA_SIN_JUGADOR = -1.0`, L2165 ✓). Que no saliera en su sesión significa que **la pausa no llegó a ocurrir** ✓.
   **Lo único que queda es la prueba viva, y es suya**: con asedio en curso, **alejarse >128 bloques y volver** (I86/I89)
   → tienen que verse las dos líneas ✓ (y la aldea **no puede caer** sin él delante ✓). **No hay nada que escribir** ✓.
   **`OLEADA de 8 asediadores intentados, 8 colocados`** ✓ y **`Aldea 0 salvada`** (+1 nivel, y se anuncia el nombre:
   **Valleverde**) ✓✓. **Lo que falta es LA PAUSA** ✗ (alejarse más de 128 bloques y volver): no hay traza de eso en el
   registro ni en el mod, así que necesita instrumento propio.
3. **La barra dibujada en el cliente y el clic en la piedra** — **VEREDICTO MEDIDO** ✓/✗ (9-oct-2026, **I288**): la barra
   es la **de aldea** (`PlayerAuxiliaryCapabilityInterface` L43-55: no enseña dirección hasta que la **piedra de lore** o
   un clérigo se la dan ✓) y **su mitad de DATOS ya la mide el arnés** (registra cada 10 s `aldeasVisitadas`,
   `aldeasReveladas` y **«qué dibujaría la barra»**, `GuardHarness` L98-104 ✓, incluido que quede **oculta** sin nada
   descubierto ✓). Lo que **no** se puede medir aquí es **el dibujo en el cliente** ✗ (el banco no tiene ventana) → es
   prueba de partida: con una aldea **descubierta**, que la barra enseñe nombre y rumbo ✓. El **clic en la piedra** tiene
   su efecto medible por el mismo camino (el descubrimiento ✓); falta el clic de un jugador de verdad.
   nombres), pero lo medido es el **texto** de `VillageBarText`, no el **pixel** en pantalla ni el clic en vivo.
4. ~~**Los 14 faroles de la aldea 0**~~ — **CERRADO POR MEDIDA** ✓ (9-oct-2026, **I250**): la auditoria versionada
   (`tools/audita_aldea.py`) da **0 faroles sin apoyo** en su aldea (y 0 en las otras cinco listas: vallas, cofres
   tapados, puertas incompletas, camas sueltas y portones tapados) -> el retrofit ya corrio. *(Y la herramienta ya no
   lleva el nombre del guardado escrito a mano: busca el mas nuevo de `run/saves`, asi no vuelve a caducar.)*

**(b) CODIGO PENDIENTE, PEQUEÑO Y MEDIBLE YA** (lo abierto, numerado; lo cerrado hoy va al final del apartado):

**REPORTES NUEVOS DEL JUGADOR (9-oct-2026, con pantallazo — se miden antes de tocar)**:
- **A · Camas fuera** — **CERRADO Y MEDIDO EN SU PROPIA ALDEA** ✓✓✓ (9-oct-2026, **I271**-**I286**): en una **copia** de su
  partida (nunca `run\saves` ✓), la corrida **190** deja: **capa 74 (tejado) 4 → 0** ✓, **en la calle (`x=531`) 4 → 0** ✓,
  **capa 68 (dentro) 20 → 20** ✓ y las **blancas de dentro idénticas** ✓ (celda por celda). La traza nombra su aldea
  (`Aldea en 566, 63, 566`) ✓. Queda anotado un hilo **sin prisa** ✗: algo repone camas arriba entre vueltas del latido
  (se ven `4`, `1`, `2`, `2` quitadas ✓) — el barrido gana (0 al final ✓), pero **quién las repone sigue ahí**.
  **(i) LAS DE ENCIMA DE LOS TEJADOS: ARREGLADAS Y MEDIDAS** ✓✓ (9-oct-2026, **I271**: regla de
  **altura y pueblo entero** —toda cama por encima de `nivel + 9`—, en el latido como retrofit idempotente con
  `// lint:ok I9` y **sin subir `CURRENT_LAYOUT`**; medido en la corrida 186: la traza sale ✓, la capa 77 pasa de **4
  mitades a 0** ✓ y las **20 de dentro siguen intactas** ✓; **en su partida se quitan al cargar el mundo** ✓).
  **(ii) LAS DE LA CASA 1 (oeste), en el plano de su pared: PENDIENTES** ✗ (4 mitades rojas en `x=531`, la columna de la
  puerta; dentro hay dos blancas ✓) — es **otra regla** (cama en la pared, no por altura). Lo de abajo es el historial
  de cómo se acotó (I252-I270), **con tres intentos retirados y dos correcciones mías** ✓:
  (i) **dos camas EN EL TEJADO de la barraca** (capa 74 = pueblo+11), restos de un trazado viejo que **la limpieza no
  barre** porque su banda es `pueblo+3 .. pueblo+8` (`VillageGenerator` L1759-1765) → el arreglo es una **limpieza de
  camas sueltas** por encima del tejado (solo camas), y ya tiene su medida (contar antes: 2 ✗ / después: 0 ✓);
  (ii) **dos camas EN LA CALLE, a los lados de una puerta** — **IDENTIFICADO Y MEDIDO** ✓✓ (9-oct-2026, **I256**, con
  las coordenadas del F3 del jugador): la casa está junto a **(531, 65, 563)** y las camas están en **(531, 560)**,
  **(531, 561)**, **(531, 565)** y **(531, 566)** de la **capa 63** — o sea **en el plano de la pared**, la MISMA `x=531`
  que la **puerta (531,563)** ✗, y por eso quedan en la calle (y **Ximena, Leñador, duerme ahí** ✗). La casa **sí tiene
  segunda planta** (capas 66-71, con escaleras), así que **no es** que no quepan: **se colocan una celda hacia fuera** ✗.
  **Acotado** ✓ (9-oct-2026, **I257**/**I259**): la casa es **la «casa 1 (oeste)»** del trazado
  (`VillageGenerator` L121-133, índice 0) y la levanta **`placeVanillaHouse`** (L263-318), que **vacía** la plantilla
  vanilla y la **vuelve a amueblar** — y ahí las dos camas caen **una celda hacia fuera** ✗.   ⚠️ **TRES INTENTOS, TRES RETIRADAS — Y EL PORQUÉ, YA MEDIDO** ✗ (I265/I266/I268/I269): mi barrido **sí corría** (el
  bloque del latido está dentro de `if (% VILLAGE_POLL_TICKS == 0)` ✓), pero **miraba a la barraca**, que está en el
  centro **+(-45,+22)** (`TRAZADO[6]`, `VillageGenerator` L120-129) ✗ — y **las camas están en +(+28,+17)**, el **mismo**
  desplazamiento en su aldea y en la del arnés. O sea: **corrección de fondo** — ese edificio **NO es la barraca** ✗ (lo
  dije mal en I253/I267). **Siguiente paso (sin escribir código): identificar el edificio de (+33,+21)** con un grep de
  `VillageStorage.OFFSET`, `ANEXO_DX`, `PESQUERA`… y luego apuntar ahí el barrido y medir 4 → 0.

  ⚠️ **TRES INTENTOS DE ARREGLO, LOS TRES RETIRADOS CON SU MEDIDA** ✗ (I265/I266/I268): el último, ya con **la excepción
  que el propio lint prevé** para retrofits (`// lint:ok I9`, `tools/lint_aldea.py` L213-216), compilaba y pasaba el
  lint pero **tampoco quitó ninguna cama** (traza ausente y las 4 de la capa 77 intactas). **Antes del cuarto intento
  hay que medir dos cosas**: (1) dónde cae `baseDeBarraca(center)` = `trazado(center, 6)` (¿cubre mi caja esa huella?)
  y (2) si el bloque del latido donde va la llamada (`VillageManager` L2981) **corre de verdad** o está dentro de un
  `if` que casi nunca se cumple.

  ⚠️ **EL PRIMER INTENTO DE ARREGLO SE RETIRÓ** ✗ (9-oct-2026, **I265**/**I266**): se escribió un **barrido de camas sin
  techo** (regla buena, comprobada contra los cuatro casos de abajo) y, al medirlo en el mundo del arnés, **no quitó
  nada** (su traza no sale) y el **salto de `CURRENT_LAYOUT`** que pide el guardián disparó una **reconstrucción gorda**
  (56 → 36 mitades de cama ✗), así que **se retiró todo** ✓. **Siguiente intento, con la lección**: barrer **todos** los
  edificios (no solo las casas y la barraca) y **primero medir de quién son** las camas sueltas.

  **RESUELTO SIN PREGUNTAR** ✓✓ (9-oct-2026, **I264**, *corrigiendo* mi I263): dibujada la capa 63 de la casa, **dentro
  SÍ hay camas** — dos **BLANCAS** en (535,561) y (536,561) ✓ (mi conteo anterior solo miraba las **rojas** ✗) — y las
  **cuatro mitades rojas de la calle están en las celdas de la PARED** (`x=531`) ✗ → son **CAMAS DE MÁS** (duplicados de
  una migración vieja, **la misma familia** que las **2 camas del tejado** de la barraca y que los composteros «de más»
  de I164). **El arreglo, uno solo para los dos medios del reporte A**: **limpieza de camas sueltas** — quitar las que
  estén **fuera del interior** (en la pared o encima del tejado), **solo camas**, sin tocar las de dentro ✓. Medida:
  **antes** 4 mitades fuera ✗ / 2 blancas dentro ✓ → **después** 0 fuera ✓ y las de dentro intactas ✓.
  **dentro** en ese pegado/amueblado (sin tocar la plantilla) y medir: **2 camas fuera → 0** ✓. *(Descartado el
  camino equivocado: la tabla de camas de `VillageGenerator` L9875-9882 es de la **casa grande**, no de la casa 1 —
  ver **I259**.)*
- **C · EL GOLEM QUE SE ASFIXIA EN UNA COLUMNA** ✗ (11-oct-2026, reporte: *«uno de los golems SIEMPRE se spawnea en una de las
  columnas de la aldea y recibe daño por sofocación, en TODAS las aldeas»*): **causa medida** — el golem se convoca en
  `center.offset(4,0,4)` (`VillageGenerator` L7867) y las cuatro columnas del kiosco están **a ±4** → siempre dentro de un
  pilar ✓. **Arreglo PREPARADO entonces y APLICADO el 22-oct-2026** (`i291_golem.py` fue el borrador): celda libre de
  verdad + búsqueda alrededor + aviso si no hay hueco. La corrección final añade la **AABB completa** del golem y la
  exclusión de la huella del kiosco (ver el cierre de abajo).
  > ✅ **CERRADO (comprobado el 22-oct-2026)**: la entrada quedó obsoleta. El sitio de spawn sigue siendo
  > `center.offset(4,0,4)`, pero los postes reales están a **±3** y el arreglo aplicado valida la **caja AABB completa**
  > del golem (1,4 de ancho), no solo pies/cabeza. Evidencia: `golem-fresh-20261022-1920` (semilla 9201, aldea nueva
  > `(80,97,0)`, golem `(83,97,5)`, `collision=false`, `support=true`, vivo 200 ticks). Ver la sección final.
- **D · RESTOS FLOTANTES DEL TERRENO VIEJO AL FUNDAR** ✗ (11-oct-2026, **I292**): la limpieza es **por huella** (solar de
  cada casa, cimiento, reparación) y lo que queda **fuera de esas huellas** se queda flotando ✓. Arreglo: pasada **por
  flotación** en el recinto (quitar lo que no esté en el plano; nunca lo del jugador). Medida: bloques flotando que no son
  del plano → 0.
- **E · EL LEÑADOR DEJA TRONCOS FLOTANDO** ✗ (11-oct-2026, **I293**): *«no corta todos los troncos y quedan troncos
  flotando»*. Dónde: `VillagerLumberjackGoal`. Arreglo: quitar el **resto del árbol** (troncos y hojas que cuelgan), solo
  lo del árbol. Medida: troncos del árbol sin apoyo tras una tala → 0.
- **B · El haz de luz del centro** — **MEDIDO: ESTÁ BIEN PUESTO** ✓✓ (9-oct-2026, **I255**/**I258**). El jugador
  confirmó que era el haz. Comprobado en su aldea: **X/Z** = (566.5, 566.5), que es el medio de la **plataforma** del
  kiosco (9×9), de la **campana** (566,64,566) y del **farol** (566,67,566) ✓; **Y**: el haz arranca en `nivel+6` = **69**
  y el **tejado del kiosco está en 68** → nace **justo encima**, no dentro ✓. **No hay desfase en los datos.**
  Lo único que queda ✗: si lo sigue viendo corrido, es **el dibujo en el cliente** (el margen aleatorio de 0,08 de
  L7684-7685, visto de lejos o con la cámara girada) o que fuera **otra aldea** (la suya es la única del guardado) →
  **un pantallazo del haz** lo cierra; el arnés no tiene ventana. **No se toca nada.**

5. ~~**El compostero que algo vuelve a poner en alto**~~ — **CERRADO: NO OCURRE HOY** ✓ (9-oct-2026, **I250**). Medido
   por dos lados: en el guardado del jugador los **3 composteros estan en su celda oficial** (capa 63; la pelea
   necesitaba justo lo contrario, uno una capa por encima de su objetivo) y en una corrida de 3 minutos salieron **0
   lineas** de la traza del asentado. **La incoherencia del codigo se deja escrita como latente** (el que coloca usa
   `max(nivel, suelo)` y el que asienta usa `nivel`): **no se toca** porque no hay medida que lo pida (el intento se
   midio y se retiro, **I245**), y queda dicho **cuando** importaria: una aldea con el suelo de esa columna por encima
   del nivel del pueblo.
6. ~~**LA FASE 5 DEL PLAN DE LA ALDEA: LOS POLLOS**~~ — **CERRADO: NO ERA UN PENDIENTE** ✓ (9-oct-2026, **I249**): el
    gallinero se resolvio **de otra manera** —tiene un **hueco de un bloque a proposito**, *«los pollos pasan, los
    aldeanos no»* (acta L1275, y la auditoria **salta** ese porton)—, asi que el «pollos fuera del recinto: 0» del plan
    **no puede ser** el criterio. La frase del plan queda corregida, y **si el jugador quiere** el porton que se abre al
    paso y se cierra detras, es una **funcion nueva** (se pide, no se cuela en una pasada de cierre).

**Cerrado hoy en este apartado** (se deja escrito, con su numero de invariante):
- ✅ **El dato de control del arnes que no marca** — **CERRADO Y MEDIDO** (**I244**): ahora cuenta la **columna entera**
  (`columnas del anillo abiertas`) y en la corrida 181 marca **8/4320** mientras taladra, donde antes marcaba **0** ✗.
  Y de paso se arreglaron **dos fallos de la escena** (el asaltante se colaba por una **cueva** por debajo del anillo,
  corridas 179 y 180): el anillo es ahora **macizo hasta 30 bloques por debajo del nivel del pueblo**.
- ✅ **El aviso cosmetico del registro** — **ARREGLADO** (**I244**): se compone segun el trazado de verdad
  (`VillageGenerator.java` L1397-1403) y con `BARRACA_PISO2 = 0` dice «un piso: sala de armas y 8 camas». **Lo que falta
  es verlo**: ese aviso solo se escribe **al construir la barraca**, o sea en una **aldea nueva** (`-MundoNuevo`).
- ➡️ **Las losas del tejado a +11 NO eran un pendiente**: es un limite a proposito y esta en **(c)**, abajo.

**(c) ACEPTADO A PROPOSITO (no se toca, y se dice)**:
7. **Una cama sin acceso no se le da a nadie** (**I43**, acta L994): la celda de espera exige estar a **≤2,0 bloques**,
    asi que una cama **encerrada** (muro o mobiliario delante) se queda sin dueno — y el acta dice por que: *«es
    construccion/mobiliario del pueblo, no del reparto»*. **La medida lo respalda** (misma entrada): en la partida,
    `CAMAS RESUMEN: adultos=11 conCama=11 COMPARTIDAS=0 SIN CAMA=0 DURMIENDO=11` ✓ — **nadie se quedo sin cama**. Decision
    robusta: **no se toca el reparto** (dar una cama inalcanzable o mover al aldeano seria peor: el «baile» costo
    I205-I209). Ver **I248**.
8. **Los tejados quedan fuera del obrero** (**I60**, acta L1470): su banda es **+5/−6** sobre el nivel del pueblo
    (`REPAIR_MAX_UP`/`REPAIR_MAX_DOWN`, `VillageManager.java` L852-853, filtro en L6974), asi que **si un tejado se
    rompe, nadie lo sube a arreglar**. El acta lo llama *«limite conocido (dicho a proposito)»* y da la razon: *«subir a
    un tejado es otra obra, no una reparacion de planta»*. **MEDIDO el 9-oct-2026** con `build/obras_pendientes.py`
    (recorre **todas** las celdas del plano, en su altura): **ALDEA 0 (566,566): OBRAS PENDIENTES: 0** ✓ — las **27
    losas a +11** de septiembre **ya no faltan**, el tejado esta entero. O sea: **no hay nada roto hoy**; lo que hay es
    el limite. *(Estaba en el apartado (b) por error mio: lo puse como «codigo pendiente» sin leer que el acta ya lo
    habia decidido asi. Si el jugador QUIERE que los tejados se reparen, eso es una funcion nueva —que el obrero trabaje
    en alto—, no un arreglo.)*
9. **El piso de arriba desconectado por el apiñamiento de camas** (**I104**, acta L3009): es de la plantilla y de la
    migracion de casas; **no se toco**, y lo que hay es la **red de seguridad** (`bajarDeLasCamas` + el rescate de I103).
10. **El minero coge picos de madera teniendo hierro** (**I141**, acta L4642): la entrada dice que **no se toca ahora**
    («no hay medida que lo pida») y nada posterior lo ha cambiado.

Lo de abajo es el historial de los cinco pendientes que se cerraron en esta ronda; debajo del todo esta el historial
antiguo. Y las tres cosas de **instrumento** con las que empezo la ronda: **la primera ya esta cerrada con numeros**:

1. **La escena del nado** (`MEDIR_AGUA`) — **CERRADA Y MEDIDA** ✓ (9-oct-2026, **I235** en `docs/aldea-invariantes.md`).
   Y salieron **tres** cosas, no una: (a) el **barrido de monstruos del arnes** borraba al asaltante en el primer barrido
   (por eso los 73 volcados de `rapida-49.log` eran la misma celda y la misma velocidad: se midio **un monstruo
   congelado**, no un pozo que lo encerrara ✗); (b) la charca se **juntaba con el mar**, porque en la copia del guardado
   el pueblo esta a la **cota 46 y todo alrededor es agua** (`470,45..62 = water`, comprobado celda a celda): ahora la
   escena se levanta **sobre** el agua y no depende del mundo ✓; y (c) lo gordo: **el goal del nado no arrancaba
   NUNCA** —`ZombieAttackGoal` de vanilla esta en la prioridad **2** con el flag MOVE, igual que `EscapeWaterGoal`, y el
   motor solo deja entrar a un goal si tiene prioridad **estrictamente menor** (`WrappedGoal.canBeReplacedBy`)—, asi que
   el arreglo de I215 era **codigo muerto** mientras el asaltante tuviera objetivo. Arreglado a la **prioridad 1**
   (`AggressiveZombieEntity` **L854**) y medido **antes y despues**: **0 ticks** corriendo en 60 s de atasco → **arranca
   3 veces y saca al monstruo del agua en 2-4 ticks**. La vuelta de orilla a ras ya salia sola: **sale del agua a los 6,0 s**
   (3,4 bloques a 0,57 bloques/s dentro del agua) y anda en tierra a **2,2 bloques/s** ✓.
2. **El puente y la escalera en una ola** — **CERRADA Y MEDIDA** ✓: el **puente** se tiende 8 de 8 y cruzan, y la
   **escalera** primero se midio que **no colocaba nada** (0 escalones en cinco corridas) y **se arreglo** (I238), y
   despues se ha vuelto a medir: **103** lineas `ESCALON` en la corrida 173 (I242) — o sea que las dos herramientas se
   ven en una ola ✓
   (9-oct-2026, **I236** en `docs/aldea-invariantes.md`). Escena nueva (`MEDIR_OLA_CON_FOSO`, en el arnes): **muro entero
   y sin huecos** (anillo r=7, 3 de alto, 68 troncos) con un **foso de 4 de ancho y 7 de hondo delante** y **ocho
   asaltantes fuera**, en **dos vueltas** (objetivo al mismo nivel y 4 bloques arriba). Medido en dos corridas de mundo
   nuevo (153 y 154, `-MundoNuevo`): **el puente lo tienden 8 de 8** ✓ y en la 154 **7 de 8 estan DENTRO del muro a los
   21 s** ✓; el puente **sube un bloque por tablon** (altura maxima 5), o sea que un foso de 4 de ancho deja al monstruo
   **mas alto que el muro: lo pasa por encima sin picarlo** ✓. **La escalera no disparaba NUNCA** (0 `escalon` en cinco
   corridas), **ni dandole su caso** (vuelta 3 de la escena, corrida 158: foso **relleno**, muro **quitado** y el
   objetivo **en el aire**: **0 escalones otra vez**, los ocho asaltantes **debajo del objetivo** y varios **cavando
   hacia abajo**, `266 pica` casi todo `stone` de la plancha). **La causa quedo medida en la linea exacta**:
   `apilarBloqueParaSubir` (**L692**) llamaba **antes** a `breakStepAheadHacia(hacia, true)` y **se salia con `true` en
   cuanto picaba algo**; con `conEscalon = true` esa llamada **pica tambien el suelo de delante**, asi que en cualquier
   suelo rompible (siempre) **cavaba el suelo y no apilaba**: la rama que **coloca** el bloque era **inalcanzable**.
   **ARREGLADO EN I238** (el orden: **colocar primero, picar como ultimo recurso**) y **medido despues** (corrida 159):
   **65 `escalones`** donde antes habia **0**, el puente sigue tendiendose (**13**) y en la vuelta 3 **5 de 8 DENTRO**
   con la escalera en pie ✓. Y DOS HALLAZGOS
   que salieron de la foto: los que entran lo hacen **por debajo** (tunel bajo el muro: 125 piedras
   picadas justo en r=6..7 y los 68 troncos de base **en pie**), y el **romper y el puente se peleaban** (26-36 de los
   bloques picados eran `cobblestone` en r=8..11: **los tablones que ellos mismos acababan de tender**) — **ARREGLADO EN
   I239** (la obra del asedio no se pica: regla del aire debajo, lista **compartida** de tablones y el veto en la unica
   puerta que rompe, `breakBlockAt`) y **medido**: los tablones rotos bajan de **26-36 a 4** y el asedio construye mas
   (puente **16** lineas y escalera **76**, las dos mas que antes) ✓.
3. **Ver la milicia con gente dentro** — **CERRADA Y MEDIDA** ✓ (9-oct-2026, **I237** en `docs/aldea-invariantes.md`).
   Escena nueva (`MEDIR_MILICIA_SOBRANTES`): pone ella misma **12 adultos SIN OFICIO** (el caso «mas adultos que
   puestos») y **3 crias** alrededor de la plaza y deja correr el latido. Medido en un mundo conservado
   (`tanda-rapida.ps1 156 157 -Conservar`): `[Milicia] aldea 0: 4 espadachin(es) y 3 arquero(s) de 12 sobrante(s) de 27
   aldeano(s), milicia hasta 7 | alistados DE VERDAD: 7 (4 espadachin(es), 3 arquero(s))` ✓, las **6 crias nunca se
   alistan** ✓, y los guardias **entrenan** (la marca `entrenado` sube de 0 a **713-777 ticks** y las etiquetas pasan de
   `Yendo a entrenar` a `Patrullando…`) y **se arman** (`arma=minecraft:air` -> `minecraft:bow`, `Cogio 16 flechas del
   almacen`) ✓. **Y LO GORDO, que es una regla del mod y hay que saberla**: el reparto de la milicia vive en
   `tickVillageLife`, que **solo corre con la aldea EN PAZ** (`manageNearby` L1040: `!isUnderAttack`), y una aldea nueva
   **nace con su asedio inicial** — que con un jugador de pega no se resuelve nunca: la corrida 155 dio `guardias=0` los
   tres minutos y ni una linea `[Milicia]`. El instrumento lo arregla dando el asedio **por resuelto** cuando la aldea ya
   esta construida (ver la receta, abajo).
4. **LA MONTAÑA Y EL TUNEL DE FRENTE** — **MEDIDO Y SE CUMPLE** ✓ (9-oct-2026, **I240** en `docs/aldea-invariantes.md`).
   Lo pregunto el jugador: *«lo del tunel se penso para cuando hay una montaña entre el asediador y la villa… en vez de
   rodear (por si es demasiado grande la circunferencia) pueda mejor cavar de frente… si la aldea esta por encima, puede
   hacer una escalera y atravesar abismos o brechas grandes»*. Escena nueva (`MEDIR_MONTANA`): un **anillo de piedra
   CERRADO** a r=70 (imposible de rodear) y el asaltante fuera, a r=80, **sin objetivo** (marchando al centro). Medido:
   **anda hasta la montaña** (r=80 -> r=73 en 9 s), **taladra** (el anillo pasa de 0 a 6 celdas abiertas a los 48 s y a
   **15** a los 72 s) y **la atraviesa** (r=63 a los 72 s, ya en el muro del pueblo; despues muere peleando con la
   milicia). El **puente** (8 de 8, I236) y la **escalera** (65-76 escalones, I238) ya estaban medidos ✓. **Un cambio mio
   que sobraba** (un «paseo a pasos cortos» para acercarse) se midio con su **corrida de control** y **se retiro**: sin el
   el asaltante llega **antes** (72 s y 15 celdas contra 96 s y 12) ✓. Y **la montaña GRUESA aguanta** ✓ (corrida 170:
   anillo de **26 de fondo**: en 156 s avanza de r=92 a r=86 y abre 29 de 37 440 celdas, **sin atravesarla**), que es lo
   que el jugador queria. **Y EL TOPE YA ESTA MEDIDO** ✓ (9-oct-2026, **I241**): las cinco lineas del asedio llevan ahora
   **`asaltante #<id>`** (y el arnes dice cual es el suyo), asi que se pueden contar los bloques de UNO; con el tope de
   verdad el ritmo es **~1 bloque cada 4 s** (38 en 150 s, corrida 171) y **agotar 40 pide 8-10 minutos**, mas de lo que
   dura el banco ✗; con una **corrida de control con el tope en 5** se vio lo que pasa al agotarse: el contador baja
   **5 -> 0** en **20 bloques** y **el asaltante se queda** (anillo clavado en 5 celdas y el monstruo parado en r=91 el
   resto de la corrida) ✓. Y **se recarga con cada centro que se le asigna** (`setGoToCenterActive(true)` **L199** llama a
   `recargarTunel()`): **cada ola vuelve con 40 bloques** — **Y YA ESTA MEDIDO** ✓ (corrida 177, **I243**): el contador de
   la traza (`le quedan N de tunel`) baja `5→1`, y al **volver a asignarle centro** (t=1680) **vuelve a 5** y sigue
   taladrando ✓. Y al medirlo salió **un fallo de la escena**: el anillo se levantaba desde la cota y en un mundo con el
   terreno 13 bloques más alto quedaba **enterrado** (el asaltante lo pasaba andando, r=95→66 en 40 s ✗); **arreglado**
   (cada columna se apoya en su terreno) y **la montaña gruesa vuelta a medir** (corrida 178: r=92→88 en 150 s, 11
   taladros, **sin atravesarla**) ✓.
5. **CAVAR POR DEBAJO OBLIGA A SALIR** — **ARREGLADO Y MEDIDO** ✓ (9-oct-2026, **I242** en `docs/aldea-invariantes.md`).
   La regla del jugador: *«si cavan por debajo pero despues hacen algo para salir a la superficie, obligatorio, esta
   bien; pero si no, mejor que se queden solo cavando cuando sea montaña o algo que los bloquee»*. **No se cumplia
   siempre**: en la corrida 154 el asaltante #0 acabo **8 bloques bajo el suelo y clavado 8 volcados** en un pozo que se
   habia cavado (la escalera no podia colocar porque la celda de delante, a la altura de los pies, era la pared del pozo,
   y el pico le gastaba el presupuesto de escalones). **ARREGLADO**: el escalon se intenta a la altura de los pies y, si
   esa celda esta ocupada, **un bloque mas arriba** (desde el fondo del pozo: un bloque al que saltar). **Medido
   despues** (corridas 173/174/175, 24 casos): fondo maximo **6 bloques** (dentro del foso, que cava 7) y **ninguno bajo
   tierra** ✓, con la escalera subiendo (**103** lineas `ESCALON`). Y queda una **herramienta versionada** para mirarlo
   (`tools/arnes/vuelven_a_la_superficie.py`), porque el dato de control del arnes mezclaba «estar en el foso» con «estar bajo
   tierra».

**Y EL MUNDO DE LA TANDA: LA RECETA YA ESTA MEDIDA (9-oct-2026)** ✓. Lo que salia antes eran **contadores de trabajo a
cero** porque la copia del guardado no tiene aldea donde el arnes la busca (en `470,63,646` habia **mar**: lo dice
`tools\arnes\una_columna.py`, agua de la 45 a la 62), asi que el mod **funda una aldea nueva a la cota 46, bajo el agua**.
**Lo que hay que hacer, y funciona** (medido en I236/I237): **(1)** una corrida con `tanda-rapida.ps1 N -MundoNuevo` (se
genera el mundo y el mod construye la aldea: el cimiento son cientos de miles de bloques y se pasa la corrida
construyendo), y **(2)** las corridas siguientes con **`tanda-rapida.ps1 N+1 N+2 -Conservar`**, que **conservan** ese
mundo ya construido: el pueblo sale **vivo** (medido: **granja 2, ganado 3, pescador 4, herreria 60, cocina 11,
minero 87, guardia 105**) y con **asedio inicial que hay que dar por resuelto** para que corra el latido de la paz
(`tickVillageLife`; lo hace el arnes solo cuando la aldea ya esta generada: ver I237). **Regla de oro intacta:
`run\saves` no se toca** (todo esto va sobre `run\world`).

**Y EL PORTON DOBLE ABATIBLE: CERRADO** ✓ (8-oct-2026, I224–I234 en `docs/aldea-invariantes.md`). Abre y cierra
animado con **entidad de bloque + renderizador propio**, la hoja abate sobre el **canto del marco** sin atravesar el
pilar, es de **un bloque de espesor** (el mismo cerrada y abierta, asi que no hay salto), se pulsa en **toda** la
superficie y el estado abierto deja pasar. Va con **24 modelos** y **192 variantes**, y `HOJA_FUERA` separa la **vista**
de la logica para que el cierre tambien se anime. Tres reglas que costaron caro y quedaron escritas en
`tools/arnes/LEEME.md`: **`run\saves` no se toca nunca**, **con el cliente abierto se mata el proceso y se sigue**, y
**la geometria de un modelo de bloque solo va de −16 a 32** (si se sale, el juego dibuja el damero y miente diciendo
«FileNotFoundException»).
**MEJORAS DEL PORTON, PARA EL FUTURO** (las dejo apuntadas y **fuera del alcance** por decision del jugador, 8-oct-2026):
todavia **no estan definidas** — hay que sentarse a decidir en que merece la pena mejorarlo antes de tocar nada.

---
1. **La regla del muro perimetral** — **CERRADA** (I210 + I211 en `docs/aldea-invariantes.md`), y el fallo de raiz era
   una linea: `AggressiveZombieEntity.protegidoPorLaAldea` (**L539**) terminaba en `return elAsedioYaSeGano()`, o sea que
   **con el asedio inicial sin resolver devolvia FALSE para toda la aldea**. Lo canto la verificacion independiente de la
   cuenta (`tools/arnes/verificar_recinto.py`, 13 casos, sin servidor), no otra corrida. Arreglado: dentro se devuelve
   **`true`** siempre (la regla es de GEOMETRIA: muro perimetral si, dentro no; el campo de fuerza se queda para expulsar
   monstruos y negar spawneo), el veto se aplica **en la puerta** (`breakBlockAt`, **L303**, el unico sitio que destruye un
   bloque), las dos preguntas del recinto usan **la cota de ahora** (`cotaParaElRecinto`, **L470**) y `MURALLA_ANCHO` baja
   **5 → 1 → 0** (**L142**; la verificacion cazo que con 1 la cara de dentro del muro seguia rompible). Medido: la cuenta
   clasifica bien los 13 casos; el asaltante de la aldea **ganada** no toca nada de dentro (**2** bloques, los dos
   **fuera**); y con la regla puesta o puenteada (interruptor `REGLA_DEL_MURO_ACTIVA`, **L287**) el asaltante de fuera
   pica **los mismos 16 bloques, todos r=62..65** (solo el anillo), y el de dentro **0** — porque dentro del pueblo el
   asaltante **anda** hacia su objetivo y no necesita romper nada (medido de r=49 a r=1 sin un solo bloque).
2. **Escalera de bloques, tunel y puente** — **HECHOS Y CORREGIDOS** (I212 + I213). Ya no son «los que no existen»:
   `apilarBloqueParaSubir` (**L688**, el escalon), `cavarHaciaAbajo` (**L777**, el tunel) y `puentearHacia` (**L583**, el
   puente, que ya estaba escrito pero **solo lo llamaba la marcha al centro**, que se apaga en cuanto el asaltante
   tiene objetivo: en un asedio no se usaba nunca). Los usa `TraverseGoal` (**L1495**), pasivo y sin flags como el
   romper, sirviendo a los DOS caminos y **eligiendo por situacion**. **CORREGIDO en I213** (lo que salio mal): el
   puente **solo miraba de frente** (ahora los **cuatro lados**, ordenados por el que mas apunta al objetivo) y el
   escalon **se ponia debajo de sus propios pies** (ahora en la **columna de delante, a la altura de los pies**; el
   fallo lo cazo la ola real, no las escenas). **Medido con `MEDIR_OLA_REAL`** (8 asaltantes a r=60, modo nuevo):
   **6 dentro a los 11 s y 8 a los 37 s**, y de las tres herramientas **solo se usa el tunel (19 cavadas de terreno)**
   — el muro **no lo pica nadie** (`TALADRAR` = 0) porque el anillo **tiene huecos y la ola entra andando**. ABIERTO:
   (a) esa mitad de la regla («el muro perimetral es rompible») **no la ejerce nadie** hoy: o los huecos del anillo se
   cierran, o el asedio no tiene por que abrir brecha; (b) falta ver el **puente y la escalera** en una ola — **CERRADO el 9-oct-2026**: los dos se ven (el **puente** 8 de 8 y la **escalera** 103 lineas `ESCALON`), ver **I236/I238/I242** ✓.
   **CORREGIDO Y MEDIDO en I214**: con el muro de prueba **en r=62** (el radio del muro de verdad), el asaltante
   **ABRE LA BRECHA** (el muro paso de **15 bloques a 10**: pico 5) y **entro** — es la primera medida de esa mitad de
   la regla, y sale **si**. Y entro **andando por el hueco**, sin necesitar la escalera. Ademas, tres fallos de flujo
   mas de los atravesadores, corregidos: `breakStepAheadHacia` **devolvia al picar el hueco y el escalon no se ponia
   nunca** (por eso 0 ESCALON en las corridas 42-46); y dos trampas del instrumento (el muro de prueba puesto **dentro**
   del recinto lo protege la regla -> 15/15 intacto, que es la prueba de que la regla funciona; y el borde de la zanja
   del puente a 3-4 bloques, donde no hay hueco que cubrir).
3. **LOS HUECOS DEL ANILLO DEL MURO** — **RESUELTO** (I218), y **no eran huecos: EL MURO NO EXISTIA**. Medido desde
   dentro del juego (el lector de guardados fallo tres veces; el que funciona es `columna_mina.py`): **ni un tronco ni un
   adoquin en r=62 de la cota-30 a la cota+30**, con la aldea construida. La causa: el muro se levanta en `fence()` con
   la **cota del centro (63)** y el pueblo crecio a **83**, asi que quedo **enterrado veinte bloques**; y `rehacerMuro`
   (la unica ruta que lo reconstruye a la cota buena, L6979) **solo se llama desde la migracion** (VillageManager
   L2364), que **corre una sola vez por aldea**. Arreglado con `VillageGenerator.asegurarMuro` (**L6885**, idempotente),
   llamado desde **`manageNearby` (L1034)** y **NO** desde `tickVillageLife` (que solo corre en aldea EN PAZ: ahi el
   muro no se repararia justo con el asedio dentro, y una aldea vacia no lo levantaria nunca — ver I183). Medido:
   **0 -> 912 de 921 celdas**, los 9 agujeros son **los cuatro portones** (ESTE=3 SUR=2 OESTE=2 NORTE=2), el centro
   **no se movio** (470,63,646) y el asedio **ya pica `cobblestone` y `oak_log`** en vez de solo nieve.
   **DECISION DEL JUGADOR: PORTONES DE VERDAD (hecho en I219)**. Eligio que el asedio tenga que ROMPERLOS: se pone
   **puerta de valla** (`OAK_FENCE_GATE`, 2 de alto) en las 4 entradas cardinales, porque el juego NO deja que un zombi
   abra ni rompa una puerta de valla, y el pueblo SI las cruza con su goal (`VillagerGateGoal`). Las cuatro celdas van
   en el sitio unico de los portones (`VillageGenerator.portonesDelMuro` + `todosLosPortones`, I4). Medido:
   `con muro 921 | PORTONES 9 | AGUJEROS 0 | racha 0` (los 9 = 4 celdas x 2 puertas + 1 dintel), el guardado dice
   `PORTONES 4 celdas: [(408,646),(470,584),(470,708),(532,646)]` y **0 agujeros**, y la ola **ya solo entra rompiendo
   el muro** (`oak_log` y `cobblestone`), **0 puertas rotas**, y **la milicia los mata a los 8** (t=1680).
   **HECHO: PORTON DOBLE ABATIBLE PROPIO, DE 3 DE ANCHO x 3 DE ALTO (I224)**. Nuevo bloque
   `devilrpg:porton_doble` (`DoubleGateBlock`): hojas de 1x3 con `SIDE`/`LAYER`, **12 modelos** y **48 variantes**, que
   **abaten** (cerrada ocupa su celda; abierta de canto en la bisagra) y **no se rompen** (el juego solo deja romper
   `DoorBlock` en difícil), asi que **el asedio tiene que abrir brecha en el MURO**. El hueco son **TRES celdas del
   anillo** cubiertas por **tres bloques** (hoja izquierda, derecha y juntura del medio), que es lo que da el **3 de
   ancho** de verdad. Lo abre el latido (`abrirLosPortonesSegunElPueblo`): **aldeano a menos de 4 bloques -> abren; si
   no, cierran**. Medido: las 4 entradas con el porton (`83/84/85=porton_doble 86=cobblestone`), anillo
   **`921/921 | PORTONES 19 | TAPADOS 0 | AGUJEROS 0`** y **1 reconstruccion**.
   **DOS FALLOS PROPIOS medidos**: con hueco de 3 y solo DOS hojas el muro se reconstruia **3200 veces** (el anillo es
   de UNA celda de grosor: un hueco de 3 deja 3 celdas) -> **una pieza por celda**; y `asegurarMuro` contaba con **su
   propia lista** en vez de mirar el bloque -> mira el bloque. **TEXTURA PROPIA DE ROBLE OSCURO (hecha)**:
   `devilrpg:textures/block/porton_doble.png` (16x16 RGBA: tablones de roble oscuro con flejes de hierro remachados y
   aldaba), generada con `tools/arnes/hacer_textura_del_porton.py` (el PNG se escribe con la libreria estandar: en
   esta maquina no hay PIL). Los 12 modelos apuntan a ella y los tres pisos cogen su tercio, asi que el porton se ve de
   una pieza. Medido: **ni un aviso de modelo ni de textura** en el registro, la textura viaja al recurso del mod, y el
   anillo sigue `921/921 | PORTONES 19 | AGUJEROS 0` con 1 reconstruccion.
3. **El nado del asaltante** — **HECHO en I215** y el **instrumento YA ESTA** (**I235**, 9-oct-2026: el goal arranca 3 veces y saca al monstruo del agua en **2-4 ticks**) ✓. El defecto estaba en la cuenta del propio
   codigo: `MAX_ESCAPE_TICKS = 200` (10 s intentando salir) **+ `retryCooldown = MAX_ESCAPE_TICKS`** en `stop()` (otros
   10 s **sin poder tocar el agua**) = **20 segundos** en los que el asaltante ni avanza ni intenta nada, que es
   exactamente el *"se quedan ahi y avanzan muy lento"* del jugador. Arreglado en `EscapeWaterGoal` (**L1162**): no se
   rinde (`canContinueToUse`, L1233), `stop()` (L1254) ya no pone castigo, el tope es un **recalculo** de orilla
   (`RECALCULAR_CADA_TICKS`, L1185) y el empujon va cada **6** ticks en vez de 15 (L1298). **ABIERTO**: la escena del
   pozo del arnes (`MEDIR_AGUA`) **no mide** (el asaltante queda flotando en el borde, velocidad 0,012, y no sale ni
   con el arreglo ni sin el): hay que quitarle las paredes al pozo antes de dar el nado por medido.
4. **El asaltante de `MOVEMENT_SPEED: 0.552`** — **RESUELTO** (I216). Era un **crio**: el juego le pone al zombi crio un
   modificador de velocidad de **x1,5** (medido con el arnes: adulto `0,23` -> crio `0,345`) y ese multiplicador se
   aplica **despues** de la base, asi que el tope del mod (base `0,23 x 1,6 = 0,368`) se quedaba corto:
   **`0,368 x 1,5 = 0,552`**, exactamente el numero del registro. Arreglado en
   `AggressiveZombieEntity.adjustAttributesBasedOnSpawnDistance` (**L1125-1140**): el tope se aplica al **VALOR FINAL**
   (la base se deriva dividiendo por los modificadores que ya tenga el monstruo). Medido: el crio de la aldea 0 sale a
   **0,552 SIN el arreglo y a 0,230 CON el** (base 0,1533 x 1,5), y la progresion no se toca.
5. **Traza de la milicia** — **HECHA** (I217). `VillageManager.repartirGuardia` (**L3216**) escribe
   `[Milicia] aldea N: X espadachin(es) y Y arquero(s) de S sobrante(s) de A aldeano(s), milicia hasta 7 | alistados DE
   VERDAD: G (…)` **solo cuando el reparto cambia**, y escribe **los dos numeros** (lo que el bucle cree alistar y lo que
   de verdad lleva la marca) para que una discrepancia se vea en vez de esconderse. Medido en la partida de 3 minutos:
   `0 espadachin(es) y 0 arquero(s) de 0 sobrante(s) de 12 aldeano(s) | alistados DE VERDAD: 0` — **y el cero no es un
   fallo**: con los puestos por oficio cubiertos no hay sobrantes y sin sobrantes no hay milicia (es la regla del
   reparto). **Falta ver** una milicia con gente dentro (aldea con crias o mas adultos que puestos) — **CERRADO el 9-oct-2026**: visto y medido en **I237** (4 espadachines y 3 arqueros de 12 sobrantes, y las crias **nunca** se alistan) ✓.

---

## 3 · LO QUE YA ESTA CERRADO Y MEDIDO (no hay que volver a tocarlo)

- **La aldea no se desplaza ni se duplica**: el centro es el del objetivo (`VillageManager.centroDe`). Medido: 0 lineas
  de reconstruccion en las partidas.
- **El asedio funciona de principio a fin**: `OLEADA de 8 asediadores intentados, 8 colocados` y luego `Aldea 0 salvada`
  (arreglado: una oleada vacia no escribe victoria, reintentos, segunda pasada de spawn).
  > ⚠️ **ESTO NO TIENE PRUEBA CITADA, y el acta dice lo contrario** (comprobado el 9-oct-2026): I86/I89 (acta L2262 y
  > L6220-6222) dicen que **el asedio en vivo NO se ha visto** — «0 lineas de `ASEDIO`/`OLEADA`» —, y **ningun registro
  > guardado** de `build/rapida-*.log` tiene la traza `OLEADA` ✗. Se deja la afirmacion porque cuenta la intencion, pero
  > **lo que falta es verlo en la partida**: esta en la **§2.1** (y en §2.0 (a), punto 2, como historia).
- **Los aldeanos no bailan**: I205 (traza + un goal sin MOVE no pisa camino ajeno + el empujon ya no para a quien anda),
  I206 (el embudo `ponerRumbo` no reescribe el destino cada tick: `[Rumbo]` = 94-96 % saltadas), I207 (puertas: se abren
  en vez de sacarlos a empujones), I208 (`parar` idempotente por tick), I209 (una celda de paso no es un encajamiento).
- **Parcelas limpias**: `[Huerta] 0 sin sembrar, 0 pisoteadas` en las partidas nuevas.
- **Velocidad del asaltante anclada**: en la aldea 0 sale a **0.230** (zombi normal de vanilla); la progresion intacta.
- **Milicia completa**: dos roles (`ESPADACHIN`/`ARQUERO`), se **equipa del almacen** (y de la armadura que el pueblo
  fabrique, pieza a pieza), se le **ve el equipo**, escala vida y dano, y la remesa inicial del almacen trae su equipo de
  arranque (cuero, espadas de madera, escudos, arcos, flechas).
- **El arnes fuera de la partida** (`[Arnes]` = 0) y el aviso de compilacion puesto.

---

## 4 · MAPA DE DOCUMENTOS Y HERRAMIENTAS

- `docs/aldea-invariantes.md` — **el acta**: cada cambio con su clase+linea, su medida, y las hipotesis falsas.
- `docs/autonomous-village/01-navigation-architecture.md` — **la arquitectura OBJETIVO** de navegación autónoma,
  modificación del terreno y construcción (autoridad única de movimiento, servicio compartido, grafo 3D consciente
  del movimiento, la construcción como parte de la planificación, semántica de fallo y aceptación por etapas).
  **Es un documento de destino, NO un informe de lo implementado**; A2 sigue ABIERTO y su objetivo sigue siendo
  **8/8 en dos corridas idénticas**. Trae anotados (§K) los conflictos de documentación que debe decidir el dueño.
- `docs/autonomous-village/ESTADO-ACTUAL.md` — **TRASPASO VIGENTE (leer tras este fichero)**: estado real y
  verificado del repositorio al cerrar **Stage 6B** (rama, HEAD `97220be`, inventario de cambios, estado de cada
  componente de `entity/nav/`, resultado real del fixture del acantilado —**0 colocaciones, 0 travesías, destino no
  alcanzado**—, historial de hallazgos, invariantes de seguridad, próximos pasos ordenados y decisiones pendientes
  del dueño). Sustituye a cualquier resumen anterior de sesión.
- `docs/aldea-cerebro.md` — el plan de arquitectura del aldeano y **por que se retiro** la fase que se probo (§3).
- `docs/PENDIENTE.md` — el traspaso **VIEJO** (27-sep a 3-oct): se conserva como **acta** (el porque medido de la sesion de los oficios), **no** como lista de pendientes, y lleva su aviso arriba. **Lo vivo es este fichero.**
- `tools/arnes/tanda-rapida.ps1` (~3 min) y `tanda-larga.ps1` (~20 min) — los bancos de medida (copian el arnes a un
  directorio ignorado y levantan el servidor **sin el jugador**).
- `tools/lint_aldea.py --strict` — el guardian (I8: mutar el mundo en el latido, etc.; las excepciones se justifican con
  `// lint:ok <clave> porque ...`).
- `run/logs/latest.log` — el registro vivo de la partida del jugador (el agente lo lee el solo).
- `run/config/devilrpg-server.toml` — `[logs] logEscaladoDeSpawn` (encendido para medir el asedio).

---

## 5 · ERRORES YA COMETIDOS (para no repetirlos)

1. Se compilo el **arnes de pruebas dentro de la partida del jugador** (le vacio el almacen): candado por entorno + aviso.
2. Se **commiteo un build roto** por encadenar compilar y commitear sin mirar el resultado.
3. Dos hipotesis falsas publicadas como ciertas y luego tumbadas: que a los goals les faltaba la bandera `MOVE` (la
   tienen), y que los `PARAR` eran peleas (eran **llegadas normales**).
4. Se **retiro una idea buena** (no reescribir el camino cada tick) por medirla mal: ahora es I206.
5. Se **leyo mal la leyenda** del cortador de mapas (`C/P/B/W` eran cultivos, no cobble/planks/bricks/water).
6. Se atribuyo a un bug el "794 bloques" (era la distancia al ancla de invocacion, por diseno).
7. El centro de la aldea tomado de la caja de lo construido **desplazo y duplico** la aldea del jugador (mundo perdido).


## §2.1 · ESTADO AL 12-oct-2026 (noche, actualización A1) — RESUMEN Y COLA VIGENTE

**Lo que está hecho y MEDIDO** (todos con `BUILD SUCCESSFUL` y `lint --strict` verde, en `ed937ff`..`cf09b3b`):

- **El mundo no cargaba chunks ni salvaba** → era la **cascada de luz** de mis borrados (`flags 3` / `UPDATE_ALL`): I368, I372,
  I377. **El jugador confirmó que los chunks cargan** ✓.
- **Asedio**: ya no se pasan la marcha **cavando hacia abajo** (I375: 293 → **desaparece**) y pican **a la altura del muro**
  (63-67, antes 51). **Entran 9 de 23** (antes 2). **6,5 s de rodeo** y luego **romper o trepar AL AZAR** (I374, diseño del
  jugador). **Nadie agota los 40** (I356).
- **Compuertas y lago** (I366/I367): medido **−22** bloques en el paso y **+86** celdas de agua devueltas al lago.
- **Camas** (I376/I377): nadie sale **flotando** y sin cascada.
- **Cota del lago** (I373): solo sube con agua **a ras** (≤2); en mundos ya construidos lo elevado no se deshace.
- **Aldea caída** (I378): **no** le llegan más hordas (la contradicción que señaló el jugador).
- **Oso** (I382, I386): los **cuatro** sonidos de monta son del **oso blanco** y el **`HORSE_LAND`** de las caídas ya no suena
  (el daño se mantiene). **El jugador dice que ya funciona** ✓. El **salto**: medido con jugador de verdad →
  **`carga=0.0` siempre** (I384); el siguiente paso es **ENSILLAR** (la carga del caballo solo funciona ensillado).

**LO QUE FALTA** (en este orden):

> El párrafo A1 siguiente conserva las medidas del estado anterior a la corrección; consulta la **Actualización A1** al
> final de este archivo para el estado vigente.

**COLA ACTIVA** (lo que se hace ahora, en este orden): **A1 · ARREGLAR** (medido el 13-oct-2026 en un mundo nuevo headless, `docs/aldea-invariantes.md` §«A1-bis»). **Lo que YA está medido y pasa**: censo de una aldea recién fundada **16 = 12 oficios + 4 fundadores** (0 crías, los 12 oficios intactos con `xp=1`), reparto **2 espadachines (puestos 0 y 2) y 2 arqueros (1 y 3)**, cuatro UUID distintos, y **persistencia y NO duplicación al recargar el mundo en un proceso nuevo**: mismos UUID/roles/puestos, `mascara=1111` + `remesaInicialEntregada=true` leídos de disco, `porPuesto={0=1,1=1,2=1,3=1} sinPuesto=0` y la aldea **no se regenera**. **Los tres fallos que quedan son de la fundación y están en cadena**: **(1)** la remesa del equipo **no se entrega nunca** en una aldea nueva, porque `VillageGenerator.asegurarElPicoDelMinero` (L3671, dentro de `generate`) deja 1 pico y 6 lingotes en el almacén **antes** de que `remesaInicialDelAlmacen` lo mire: su guarda de «almacén vacío» ve el cofre con cosas, devuelve `true` sin entregar y marca `remesaInicialEntregada` para siempre (medido: el almacén de una aldea nueva contiene **solo** `[1 iron_pickaxe, 6 iron_ingot]` y los cuatro fundadores salen `equipado=false`); **(2)** aunque la remesa se entregue, guarda el cuero en **pilas de 4** y `equipar` retira **de uno en uno**, así que solo el primer guardia se equipa entero (medido: puesto 0 completo; puesto 1 arco+16 flechas y **sin armadura**; puesto 2 **nada**; puesto 3 **16 flechas y sin arco**); **(3)** al recargar, los goals **no vuelven** mientras el jugador esté a más de **140** bloques, porque la llamada a `asegurarGoalsDeGuardiasFundadores` (L992-994) está **detrás** del corte por distancia de L979-981 (medido: recién fundados los cuatro `goal(CORRIENDO)`; tras recargar, cuatro `SIN-GOAL` con `distSqr=638458` contra un máximo de `19600`; la llamada directa y el mover al jugador al objetivo los reponen los cuatro). Consecuencia medida en un asedio con 4 asaltantes dentro: **mató 3**, pero **murió el puesto 1** (arquero sin armadura) y el puesto 2 se quedó clavado sin goal; la máscara `1111` **no se corrige** y no repone al muerto. **NO se llama cerrada**: falta el arreglo (entregar después del pico, pilas sueltas de 1, sacar el reponedor de goals de detrás del filtro) y **repetir fases 1/2/3/6** exigiendo cuatro `equipado=true`, cuatro con sus cuatro piezas y su arma/escudo o arco/flechas, `SIN-GOAL` imposible tras recargar y los cuatro vivos tras el asedio. El jugador debe confirmar además el resultado en su partida. Siguen **(A0)** **ENSILLAR EL OSO** para que la **carga del salto** llegue (medido: `carga=0.0` siempre con jugador de verdad; la carga del caballo solo funciona ensillado) — el jugador dice que el oso **ya le funciona**, así que esto es el resto del salto montado; y **(A2)** que los asaltantes **lleguen al centro** (entran 9, al centro 0).

**COLA DIFERIDA** (necesita un dato del jugador antes de tocar código): **(B1)** el **haz** (mirar desde dentro si sale en el kiosco); **(B2)** el **minero** (confirmar si anda o pasar su línea); **(B3)** verificar el **orden de objetivos** con un asedio real.


**Reglas de trabajo que evitarn desastres** (aprendidas a golpes hoy, y están en el historial):

- **Medir antes de tocar**: el registro del jugador (trazas I358/I359) y el inspector `build/inspector_del_asedio.py`
  (`--desde/--hasta` y **dice el tramo que ha leído**).
- **Editar por LÍNEA VERIFICADA**: los scripts buscan la línea, comprueban que es la que creen y **abortan sin tocar nada** si
  no cuadra (pasó **6 veces** hoy). El ancla por texto falló 3 veces: no usarla.
- **No commitear** sin `gradlew compileJava` **BUILD SUCCESSFUL** y `tools/lint_aldea.py --strict` en verde. La excepción
  `lint:ok I9` va **en la misma línea** que el `colocar`.
- **El acta**: anclar en la entrada más nueva **buscándola**, y **comprobar que el fichero crece**; si no crece, abortar.
  `docs/CONTINUAR.md` **no** se toca con cortes: se le **añade** al final.
- **NUNCA** escribir ni borrar `run\saves`. El arnés (`GuardHarness.java`) vive en
  `src/main/java/com/chipoodle/devilrpg/debug/` y va **fuera de git** (`.gitignore`), para que **no se entregue**.
- **El cliente del juego bloquea el jar**: si `compileJava` dice `neoforge-…jar is locked by`, matar el `java.exe` del cliente
  (`-Dfml.modfolders=devilrpg`) y reintentar. El jugador lo autorizó expresamente.

## Actualización A1 — 13-oct-2026 (estado vigente; las medidas previas quedan como historial)

Se aplicaron las tres correcciones solicitadas: `VillageStorage.remesaInicialDelAlmacen` completa faltantes sobre una
copia y solo entrega si cabe toda la dotación; no duplica el pico ni los lingotes que ya dejó el minero; las piezas de
cuero se mantienen en huecos individuales. `VillageManager.manageNearby` repone la meta de fundadores antes del filtro
de 140 bloques cuando el chunk ya está cargado, sin cargar chunks distantes. Los mundos ya generados antes de A1 sin
bits de fundadores no reciben una remesa retroactiva.

`python tools\lint_aldea.py --strict` y `git diff --check` pasaron. `gradlew.bat compileJava` se ejecutó una vez y
salió con código 1 antes de la tarea; el log `daemon-28504.out.log` registra `java.io.IOException: Unable to establish
loopback connection`, con causa inferior `java.net.SocketException: Invalid argument: connect` en
`UnixDomainSockets.connect0` a través de `PipeImpl` y `Selector.open`. `compileJava` no fue alcanzado. Por ese bloqueo
no se lanzó `runServer` ni se obtuvieron medidas nuevas de mundo, guardias, equipo, munición, recarga, metas o asedio.
Los datos del párrafo de medición A1 anterior corresponden a la versión previa al arreglo.

**A1 permanece ABIERTO.** Próximo paso: resolver el fallo de loopback sin alterar esta configuración ni iniciar
procesos en paralelo; después compilar, ejecutar el servidor NeoForge en un mundo nuevo desechable fuera de
`run\saves`, repetir censo/equipo/munición, guardar y reiniciar para comparar UUID y metas con el jugador lejos, y
medir supervivientes y atacantes en un asedio real.

## Ejecución autónoma A1 — 13-oct-2026 (actualiza el estado vigente)

La ejecución anterior de esta actualización quedó como historial. Se verificó el workaround en el proceso de Gradle:
`JDK_JAVA_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\Windows\Temp`; `.gradlew.bat --info compileJava` acabó con
`BUILD SUCCESSFUL`, exit 0 (la tarea estaba `UP-TO-DATE`). Después se forzó compilación completa con
`.gradlew.bat --info compileJava --rerun-tasks`: `Compiling with toolchain 'C:\jdk-21'`, `BUILD SUCCESSFUL`, exit 0.
La compilación forzada inmediatamente posterior a retirar el arnés temporal también acabó en `BUILD SUCCESSFUL` en el
log del daemon. El registro de ese intento no conservó el código de salida del proceso cliente; la ejecución final se
anotará tras los chequeos posteriores a esta actualización.

`python tools\lint_aldea.py --strict` → exit 0 y `git diff --check` → exit 0. Se ejecutaron `runServer` secuenciales
en un mundo desechable con un nombre único bajo `build\a1-runtime-20261013-044500\game`; fase 1 (fundación), fase 2
(recarga y sonda corregida) y fase 3 (combate) cerraron con exit 0. Los resultados medidos, UUID y clases/líneas están
en `docs/aldea-invariantes.md` §«A1 · ejecución autónoma»; los registros reducidos y manifiesto quedan en
`build/a1-runtime-20261013-044500/evidence/`. `run\saves\New World` conserva su fecha previa.

**Pasan** los requisitos pedidos de censo, roles, entrega con pico y lingotes presentes, equipo/munición por guardia,
UUID sin duplicados, y restauración de goals a **800,4 bloques**. El combate también produjo medidas con cuatro
`AggressiveZombieEntity`: 4 muertos y dos `guardKillTags`; sobrevivieron 2 de los 4 fundadores y 10 aldeanos ordinarios.
La sonda inicial de recarga leyó antes de cargar entidades y se repitió una vez corregida; no se usa su censo vacío.

**A1 sigue abierto** por la condición histórica aún no satisfecha de cuatro fundadores vivos tras el combate y por la
confirmación visual pendiente. El asedio medido fue dirigido desde el arnés (cada zombi recibió un fundador como
objetivo a corta distancia); no es prueba de una oleada natural iniciada por el jugador. No se cambió la lógica de
combate porque estas muertes no aíslan todavía un defecto de equipo o de goals. Hipótesis pendiente (no verificada):
la posición cae en el alcance 3–16 de `FireballAttackGoal` (AggressiveZombieEntity L2136-2142), y pudo haber un disparo
inmediato si había línea de visión; el log no cuenta proyectiles ni impactos. Para resolverla, la siguiente medida de
asedio debe quitar el `setTarget` del arnés y registrar el daño recibido o los impactos. Se mantiene el orden: cerrar la
validación de A1 antes de pasar a A0 y A2; B1/B2/B3 siguen diferidos.

### Cierre de compilación y chequeos A1

Después de retirar el arnés temporal, `.\gradlew.bat --info compileJava --rerun-tasks` recompiló con
`C:\jdk-21` y terminó `BUILD SUCCESSFUL`, exit 0; el cierre `.\gradlew.bat --info compileJava` dio exit 0
(`UP-TO-DATE`). `python tools\lint_aldea.py --strict` y `git diff --check` dieron ambos exit 0. Después de este apunte
los tres chequeos se volvieron a ejecutar y confirmaron exit 0.

## A1 — diagnóstico de supervivencia instrumentado (13-oct-2026)

Esta actualización supersede el estado del combate escrito arriba; conserva sus mediciones fallidas como historial.
Con un arnés desechable en `build/a1-combat-diagnosis-20261013-092402`, repetí en mundos únicos el asedio forzado y
un control sin `setTarget`. Al detectar que la generación nueva dejaba a los fundadores en otras posiciones, repetí
ambas variantes en la formación registrada en fase 3 (mismos cuatro puestos y desplazamientos de aparición, semilla
4242, normal, reloj detenido, spawn natural desactivado, 2400 ticks). Los servidores corrieron en secuencia, nunca a
la vez.

- `DevilRpgA1CombatForcedHistorical_20261013-092402`: `runServer -Pa1diagForceTargets=true`, exit 0;
  `founderDeaths=0`, `survivorSlots=0,1,2,3`, 4/4 atacantes muertos, `guardAttributedEnemyDeaths=2`,
  `guardKillTags=2`. Los otros dos atacantes murieron por el gólem de hierro.
- `DevilRpgA1CombatNaturalHistorical_20261013-092402`: `runServer -Pa1diagForceTargets=false`, exit 0;
  `founderDeaths=0`, `survivorSlots=0,1,2,3`, 4/4 atacantes muertos, 3 tags de guardia; el otro zombi murió por un
  aldeano-zombi.
- Las corridas exploratorias previas, con posición generada distinta: forzada, 3 atacantes muertos, 2 tags, un atacante
  vivo y 4/4 fundadores; control natural, 4 atacantes muertos/tags y 4/4 fundadores. Se conservan como corridas
  iniciales, no como sustitutas del par con posiciones históricas.

El log anterior de fase 3 identifica las dos bajas como causadas por un `Aggressive Zombie` en el mensaje de muerte,
pero no guardó fuente de daño. Las nuevas trazas sí distinguen `mob`, `arrow`, `fireball`, fuego, atacante y entidad
directa. En el replay forzado histórico la arquera 1 recibió melee de 0,514 aplicado por golpe; el espadachín 0 bloqueó
golpes de 0,7 con el escudo. También se midió una fireball que impactó a un aldeano ordinario, y la primera pareja
exploratoria registró flechas amigas con 6,02 de daño a espadachines 0 y 2. Ninguna de estas observaciones mata a un
fundador en las corridas nuevas. La modalidad de las dos muertes viejas **sigue sin determinarse**; no se confirma
fireball, conflicto de navegación ni flechas amigas como su causa. El kill-tag discrepancy queda medido por el
atacante final: no suma si mata el gólem o un aldeano-zombi (`CommonForgeInteractionEventSubscriber` L474-480).

No se cambió producción ni se aplicó una mitigación especulativa. Existe un candidato mínimo para el daño de flechas
amigas medido en la formación exploratoria —cancelar daño entre guardias del mismo pueblo, ya que el filtro actual
`esFuegoAmigo` solo trata minions—, pero **no explica las bajas históricas** y no fue necesario para pasar el gate.
La aceptación de supervivencia de cuatro fundadores queda **verde en las dos corridas históricas**. Sigue pendiente la
confirmación visual mencionada en la nota histórica; el motivo exacto de las dos muertes antiguas queda anotado como
no recuperable con los logs previos.

Evidencia, incluidos UUID, vida/armadura inicial, proyectiles, eventos de daño, objetivos, goals/navegación, muertes,
atribución y censo final: `build/a1-combat-diagnosis-20261013-092402/evidence/`. Ver también
`docs/aldea-invariantes.md` §«Diagnóstico instrumentado de supervivencia A1» y la nota operativa al final de
`docs/aldea-estados.md`. El arnés temporal, su clase compilada y las líneas temporales de `build.gradle` se retiran;
no se modificó ni se guardó sobre ningún mundo del jugador. No hay commit ni push. Se mantiene el orden de cola
anterior: A1 antes de A0/A2; B1/B2/B3 siguen diferidos.

Referencias de implementación: `AggressiveZombieEntity.registerGoals` L985-1028 y `FireballAttackGoal` L2134-2202;
`VillagerGuardGoal.tick` L374-403, `buscarEnemigo` L567-620, melee L670-681 y arco L684-739;
`CommonForgeInteractionEventSubscriber.onLivingHurtEvent` L335-349, `esFuegoAmigo` L370-382 y
`onGuardiaMata` L474-480. El daño y el resultado están en `forced-historical-layout-run-console.log` L751, 807,
870, 980, 1021, 1646, 1649 y 2633-2642; el control natural termina en `natural-historical-layout-run-console.log`
L3692-3701.

### Seguimiento — muro, cavidad y render — 13-oct-2026

Se corrigió en producción la clasificación del muro: ahora coincide con las columnas exactas que genera
`VillageGenerator.anilloDelMuro`, valida bloque y altura (`AggressiveZombieEntity.esLaMuralla`) y permite únicamente
los troncos que son parte de ese muro (`breakBlockAt`). La comprobación estática para radio 62 dio 496 columnas
únicas, radios 61,074–62,801 y solo cuatro columnas exactamente en radio 62; los vecinos interiores inmediatos no
pertenecientes al anillo permanecen protegidos. Se conserva el presupuesto de 40; falta medirlo en un asedio después
de la corrección.

La fosa aproximada `(584,627)` está a 3 bloques del eje secundario `(581,627)`. El código del minero puede cavar esa
zona, pero `estaSobreElPozo` solo evita repararla y el caracol esperado no coincide por sí solo con una fosa cuadrada
7×7 desplazada. **Origen no demostrado**; no se tocó el terreno. La ruta cliente usa attachment sincronizada y el
paquete inicial de entidad incluye attachments en NeoForge 21.1.249; no apareció un defecto concreto de sincronización
ni se reprodujo el síntoma visual. No se cambió renderer ni equipo.

Los logs locales consultados solo documentan actividad del pozo primario en `(647,569)` y de su galería alrededor de
X=651 (`run/logs/latest.log` L105, L317-352, L1682-1764); no registran las coordenadas de la fosa/eje secundario en
`latest.log` o `debug.log`. Esa ausencia no atribuye la fosa ni descarta actividad posterior.

Próximo paso Harness: crear un mundo desechable único y comprobar apertura del muro en ambas caras, que bloques
interiores vecinos no se rompen y si el límite de 40 alcanza; inspeccionar el guardado exacto para ubicar las 49 celdas
de la fosa; y comparar marca/attachment cliente con el dibujo de arma en mano. Los resultados de esta nota son
análisis de fuente y simulación geométrica; **no hubo ejecución Minecraft**. A1 sigue abierto. Mantener secuencia de
procesos y no usar mundos del jugador.

Últimos chequeos observados: `.\gradlew.bat --info compileJava` con `C:\jdk-21` y el workaround de socket → exit 0;
`python tools\lint_aldea.py --strict` → exit 0; `git diff --check` → exit 0. No se cambió `build.gradle`, no se
inició Minecraft y no hubo commit ni push.

### A1 — emisión del evento de brecha del muro — 13-oct-2026

El reporte Harness registra **12 bloques de material de muro destruidos en dos corridas y 0 eventos**
`ABRE BRECHA`. Se localizó el fallo de código: `breakBlockAt` destruía primero y luego reevaluaba `esLaMuralla`;
al leer el estado posterior (aire) ya no encontraba el material ni la capa. El reporte con posición, centro y cota
de cada llamada no está anexado a este checkout, así que no atribuyo individualmente los doce casos.

`AggressiveZombieEntity.breakBlockAt` ahora conserva la evaluación del bloque previa a destruir y la comparte con
la regla de protección. Emite el evento solo si `destroyBlock` devuelve verdadero y la evaluación identifica muro
por anillo, altura y material. La nueva traza incluye los datos del servidor. El evento depende de destruir un
bloque, no de entrar: pasar por un portón abierto sin romperlo no cuenta como brecha. `TUNEL_PRESUPUESTO` sigue en
40; no se midió agotamiento. Referencias: `AggressiveZombieEntity` L315-395 y L588-664;
`VillageGenerator.columnasDelAnilloDelMuro` aporta la geometría del anillo.

**Sigue abierto hasta Harness independiente.** En un mundo desechable único, comprobar por bloque la secuencia
`evaluacion muro` → `pica` → exactamente un `ABRE BRECHA`, con posición coincidente, elegibilidad previa y
`destroyBlock=true`. Probar también un bloque interior ordinario protegido, pasar por un portón abierto sin romperlo,
y material fuera del anillo y capa o material inválidos: estos últimos no cuentan como muro o brecha; si están
protegidos por estar dentro del recinto, deben seguir protegidos. Registrar centro y cota reales para cerrar o
descartar la hipótesis de diferencia de escena. La corrección sigue sin validación de juego; A1 y su criterio de
cuatro fundadores vivos permanecen abiertos.

### A1 — el Harness independiente del muro: EJECUTADO (13-oct-2026)

Lo que en la nota anterior quedaba «hasta Harness independiente» **ya se corrió**. Cuatro mundos desechables
(`muroR-a1/-b1/-c1/-d4-142619`, semilla 4242, puerto 25591), arnés temporal borrado; `run\saves` no se tocó.

- **Anillo**: 496 columnas (radios 61,07–62,80); **1472** bloques de muro reconocidos, **0** no reconocidos, **0**
  falsos positivos.
- **Brecha válida** (2 casos): estado previo de muro, `destroyBlock=true`, paso a aire y **exactamente un**
  `ABRE BRECHA` por caso.
- **Candidato inválido** (capa `dy=6` y material tierra): `esMuro=false`, sin destrucción, **sin evento**.
- **Destrucción fallida**: sin evento.
- **Interior**: **0 de 119** bloques destruidos.
- **Dos asaltos reales** (8 asaltantes por corrida): portones cerrados → **37** bloques de muro y **37** eventos;
  portones abiertos → **38** y **38**. Total **77 bloques de muro ↔ 77 eventos**, sin desajustes en ninguno de los
  dos sentidos.
- **Portón abierto sin destruir**: 1 entrada registrada, **0** brechas, 0 bloques destruidos (pasar por la puerta no
  se etiqueta como brecha).
- `TUNEL_PRESUPUESTO=40` no cambió y **no se agotó**; su suficiencia **no** queda demostrada por estas corridas.

Detalle y comandos en `docs/aldea-invariantes.md` §«A1 — el evento de brecha, medido en ataques reales».

### A1 — Exp. 3 del terreno: la depresión nace en la GENERACIÓN (13-oct-2026)

Mundos desechables `run/p3-*` (borrados), semilla **12345**, centro real **`(661,71,533)`**, cota **71**, eje del
pozo 2 en **`(628,71,562)`**. (Cotas comprobadas: 4242 → 63, 777 → 63, 12345 → 71, 2024 → 88.)

- **Línea base solo generación** (mismo tick en que termina `preGenerate`): **11 celdas de aire por debajo de la
  cota**, todas en la columna **x=624** (Δx=−4 del eje), de `y=67` a `y=61`, con el **techo sellado en `y=70`**.
- **No cambia con la actividad**: a 260, 660 y 1260 ticks la medida es **idéntica** (11 celdas, misma distribución).
- **El minero queda descartado**: en el mundo del jugador solo 6 de 136 celdas de aire están a distancia Manhattan ≤1
  del camino del caracol, 13 de 20 pasos sin abrir, y **los 32 pasos de caracol registrados en su sesión están todos
  en el pozo 0** (`647,569`).
- **Lo que NO se reprodujo**: la variante **abierta al cielo** del mundo del jugador. Allí la firma es la misma
  (depresión profunda confinada al anillo, interior a `cota−1`) pero con la superficie abierta: 27 celdas de aire en
  `y=67` y dos ranuras de 1 bloque de ancho en los márgenes (x=577 hasta y=61; x=585 hasta y=63/64).
- **Mecanismo candidato, NO demostrado**: `estaSobreElPozo` (`VillageGenerator` L3622-3631) **no tiene test de
  altura** y protege la columna entera en `MINA_POZO_RADIO=6`; lo consultan el nivelado (L6332-6334) y `sellarSuelo`
  (L6405-6407), así que una depresión natural dentro de ese radio no se rellena hasta `cota−1`. Su hermano
  `esCeldaDeLaMina` (L3598-3612) sí excluye la capa de superficie.

**Decisión explícita: NO se aplica corrección especulativa.** No se tocó `estaSobreElPozo`, `sellarSuelo` ni la
geometría de la mina. Ese mismo guardián existe para que el pozo no se selle (fallo ya medido, comentario L6399-6404),
y estrecharlo sin un defecto demostrado podría reintroducir el «pozo sin ruta». Detalle en
`docs/aldea-invariantes.md` §«A1 — Exp. 3».

### A1 — armas en cliente: PENDIENTE, sin corrección (13-oct-2026)

- **Servidor**: los cuatro fundadores tienen equipo completo y `equipado=true`; uno consta como atacante en una muerte
  de guardián, luego el arma se usó. **Equipo en servidor ≠ equipo dibujado en cliente.**
- **Cliente**: la ruta (attachment sincronizada → `esGuardia` → `GuardVillagerRenderer` con `HumanoidArmorLayer` +
  `ItemInHandLayer`) es coherente en estático; **no apareció un defecto concreto**.
- **No se reprodujo en cliente real** (falló el puente de interfaz) y **no se lanzó `runClient`** porque su directorio
  de juego no está aislado de `run\saves`, y esta tarea prohíbe abrir o modificar el mundo del jugador. Resultado:
  **NOT RUN**, no PASS ni FAIL.
- **No se aplicó ninguna corrección** de renderer, sincronización ni entrega de equipo.

### Pendientes de aldea — leñador y bosque interior (L1–L4, 20-oct-2026)

> **Estado reconciliado el 22-oct-2026:** **L1 cerrado en runtime para el fixture A5** (`l40d-20261022-1845`);
> **L2 parcialmente abierto** (rama interior posterior al depósito probada, prioridad completa pendiente); **L3
> aceptado en runtime** para abono/crecimiento y replante (L11); **L4 implementado** en geometría. No se declara una
> garantía de sostenibilidad indefinida de toda la arboleda. El texto separa lo **observado**, lo **deseado** y lo que
> **aún no está verificado**.

**L1 · El leñador se atasca al cruzar la muralla para ir a por madera — CERRADO EN RUNTIME PARA EL FIXTURE A5 (22-oct-2026).**
Observado: al salir a obtener madera, el leñador empuja contra la muralla en vez de ir al portón y usarlo. I112
documenta el cruce **de vuelta** (llevar madera al almacén) y su arreglo medido; **no demuestra** que la salida a por
madera use el mismo paso. Pendiente: revisar la selección del destino externo y la ruta de salida; comprobar si el
fallo nace en el destino, el planificador, la recuperación o la coordinación; y probar ida por el portón, recogida y
vuelta sin empujar la muralla. No se permite cruzar bloques sólidos ni debilitar colisión/navegación.

> ✅ **CERRADO EN RUNTIME PARA EL FIXTURE ACEPTADO (comprobado el 22-oct-2026)**: `l40d-20261022-1845` (semilla 9601)
> mostró ida al árbol exterior, cruce de vuelta por el portón, depósito y segunda salida. No se generaliza a todos
> los árboles exteriores ni a todos los terrenos; la evidencia es el ciclo del fixture A5.

**L2 · Priorizar árboles dentro de la aldea — ABIERTO (comportamiento existente; aceptación pendiente).**
Prioridad deseada: (1) buscar árboles adecuados dentro del recinto; (2) talar uno interno si existe; (3) mirar fuera
solo si la madera interna es insuficiente o no hay árbol. La inspección de `VillagerLumberjackGoal` muestra una
búsqueda interna antes de la externa y una rama de arboleda/bone meal, pero **no hay una prueba de aceptación
end-to-end** de esta prioridad ni de todos sus casos límite. No se marca como cerrado.

> ⚠️ **PARCIAL (comprobado el 22-oct-2026)**: `l40d-20261022-1845` demostró que, tras el depósito, el selector
> encuentra y ejecuta una tarea **independiente en la arboleda interior** `(-59,63,79)`. Eso valida la rama interior
> después de un ciclo exterior, pero **no** prueba la prioridad completa cuando coexisten árboles internos y externos
> desde el primer tick. L2 sigue abierto como criterio de prioridad, no como tarea de recolección.

**L3 · Reponer el bosque interior con harina de hueso — ACEPTADO en runtime (L11).**
La rama `RECOGER_HARINA`/`ABONAR` usa `BoneMealItem.growCrop`; en `l11-20261021-1400` (semilla 9601) el leñador de
producción aplicó harina a plantones interiores sobre `grass_block`, con luz 15 y aire encima. Las muestras sucesivas
del mismo objetivo pasaron de `oak_sapling[stage=0/1]` a `oak_log[axis=y]`; además hubo replantes de producción tras
las talas en `(-72,63,72)`, `(-59,63,72)` y `(-63,63,72)`. **Caveat:** la instrumentación temporal imprimía el estado
"before" después de la llamada, así que no se atribuye el crecimiento a una única llamada concreta. No se afirma que
todas las plazas crezcan ni que la sostenibilidad sea indefinida.

**L4 · Ampliar el bosque interior — IMPLEMENTADO (geometría; ciclo básico observado).**
El bosque actual está definido en `VillageGenerator` como una caja de **22×26** en la esquina **noroeste**
(`x -46..-25`, `z -40..-15` relativas al centro), con **veinte plazas** (rejilla 5×4). La generación runtime de
`a5r4g` plantó **20 plantones**. L11 observó tala, replante y crecimiento por abono; la ida/vuelta por el portón
sigue siendo L1. No se declara una garantía de sostenibilidad indefinida.

**Referencias existentes:** I92 (prioridad interna y abono), I93 (cadena de la harina de hueso), I108 (la arboleda se
planta una vez), I110 (restos colgando) e I112 (cruce por el portón al llevar madera al almacén). Esas entradas son
actas de lo ya medido; **no sustituyen** la aceptación pendiente L1–L4.

### IMPLEMENT-L11 — abono, crecimiento y replante (21-oct-2026)

- Run `l11-20261021-1400`, semilla `9601`, mundo desechable con jugador y mantenimiento aldeano real.
- El leñador de producción **taló** árboles y **replantó** plantones tras las talas.
- `BoneMealItem.growCrop` se ejecutó en el JVM del servidor integrado; las muestras del mismo objetivo pasaron de
  `oak_sapling` a `oak_log` en `(-76,63,58)`, `(-76,63,65)` y `(-72,63,72)`.
- La traza temporal etiquetaba como "before" un estado leído después de la llamada; por eso **no se atribuye** el
  crecimiento a una llamada individual concreta. Sí se aceptan crecimiento y replante observados.
- `compileJava` 0; `test --rerun-tasks` 0; `VILLAGE_FOREST_LAYOUT_TEST` OK; `lint_aldea --strict` 0;
  `git diff --check` 0.
- **No hubo cambio de comportamiento de producción** para L11; solo fixture debug y diagnósticos temporales retirados.
- No se afirma que todas las plazas crezcan ni que exista una garantía de sostenibilidad indefinida.

### NEXT-02 — próximo hito: L1, salida y vuelta del leñador por el portón

> ✅ **COMPLETADO Y SUSTITUIDO (22-oct-2026)**: L40 (`l40d-20261022-1845`) cumple los seis criterios para el
> fixture A5. No iniciar NEXT-02. La tarea siguiente vigente es la del final de este documento.

**Por qué:** L3 (abono/crecimiento/replante) y L4 (geometría) quedan cubiertos; **L1 sigue abierto** y es el
comportamiento visible que falta del ciclo del leñador. No reabre crecimiento ni replante.

**Comportamiento visible:** el leñador sale a por madera exterior por el portón y vuelve por él al almacén, sin empujar
la muralla de forma sostenida.

**Alcance mínimo:** revisar `VillagerLumberjackGoal` y la selección del destino exterior/ruta de salida; reutilizar el
paso por portón de I112. No romper bloques, no atravesar sólidos, no cambiar geometría ni protección.

**Archivos probables:** `VillagerLumberjackGoal.java` y, si el destino se elige allí, la utilidad de navegación de
aldeanos correspondiente; prueba pura de destino y una corrida con jugador.

**Aceptación:** (1) elige árbol exterior; (2) sale por el portón; (3) recoge; (4) vuelve por el portón; (5) no empuja
la muralla de forma sostenida; (6) no modifica bloques protegidos.

**Validación:** `compileJava`, `test --rerun-tasks`, `lint_aldea --strict`, `git diff --check`; R4 solo si cambia
navegación/terreno compartido.

**Parada:** si el cruce exige un rediseño de navegación compartida, documentar el defecto y parar.

### IMPLEMENT-08/09 — coordinación de brecha entre asaltantes (21-oct-2026) — **PARCIAL**

- **IMPLEMENT-08 — aceptado en implementación y pruebas unitarias.** `RaidTacticalBoard` es efímero, propiedad de
  `VillageDefense`, con reclamos por celda exacta, TTL de 100 ticks, tope de 64 y liberación al romper/terminar.
  `RaidTacticalBoardTest` = **13 aserciones OK**; el reclamo se adquiere justo antes de `breakBlockAt`. La exclusión
  simultánea en producción **no está verificada**.
- **IMPLEMENT-09 — INCONCLUSIVE.** El asedio real `a5r4f-20261021-0900` (semilla 9601) pasó como asedio (4 fundadores,
  8/8, salvada, 200 ticks post-terminal), pero produjo **0 adquisiciones y 0 denegaciones de reclamo**; los asaltantes
  no llegaron al gate de destrucción en condiciones de competir por la misma celda. No es un PASS de coordinación ni
  una regresión de producción.
- **Decisión:** no se siguen cambiando `RaidTacticalBoard`, `VillageManager` ni `AggressiveZombieEntity` para forzar
  ese fixture. La prueba multi-asaltante queda diferida hasta tener un fixture de asedio genuino con dos asaltantes
  sincronizados; no se fabrica `VillageDefense` ni se teletransportan asaltantes.

### NEXT-01 — próximo hito propuesto: **L1, el leñador cruza el portón** (21-oct-2026)

**Por qué L1:** es el siguiente comportamiento visible de sociedad aldeana ya documentado y acotado; no requiere una
nueva capa de IA ni otro banco de pruebas. Hoy el leñador empuja contra la muralla al salir a por madera (L1 abierto).

**Comportamiento visible:** el leñador sale por el portón, recoge madera y vuelve al almacén sin empujar la muralla; si
el portón no es viable, se detiene/replanifica de forma segura en vez de insistir contra el muro.

**Alcance mínimo:** revisar `VillagerLumberjackGoal` y la selección del destino exterior/ruta de salida; reutilizar la
navegación y el paso por portón ya usados en I112. No romper bloques, no atravesar sólidos, no cambiar la geometría de
la aldea ni la política de protección.

**Archivos probables:** `VillagerLumberjackGoal.java`, utilidades de navegación de aldeanos si el destino exterior se
elige allí, y una prueba pura/integración de destino y cruce.

**Aceptación:** una corrida con un leñador real que (1) elige un árbol exterior, (2) cruza por el portón, (3) recoge
madera, (4) vuelve por el portón al almacén, (5) no empuja la muralla de forma sostenida y (6) no modifica bloques
protegidos. Medir además que no se rompe el asedio R4 si el cambio toca navegación compartida.

**Validación estándar:** `compileJava`, `test --rerun-tasks`, `lint_aldea --strict`, `git diff --check`.

**Condición de parada:** si el leñador no puede cruzar el portón sin una corrección de navegación compartida, parar y
documentar el defecto concreto; no abrir L2–L4 ni el bosque interior en el mismo pase.

### A5 — Cierre del asedio: matriz de aceptación (20-oct-2026) — **PENDING**

> **El asedio sigue PENDIENTE.** Una observación de producción correcta **no** demuestra que un defecto concreto esté
> arreglado. Esta matriz separa PASS, FAIL e INCONCLUSIVE; no se rellena ningún hueco con una suposición.

| Escenario | Estado | Evidencia / siguiente acción |
|---|---|---|
| **N1** — fallo original de guarda en A3 | **INCONCLUSIVE** | A3 `a2n-20261019-212236` terminó en `guarda de ruta: replanteos agotados (6/6)` con `BREAK@675,71,639`; no se reconstruyó la geometría exacta. A4.1 reprodujo guarda 6/6 con un `JUMP_UP@0,103,8` (`a4lab-a-20261019-224904`) y A4.2 conservó el BREAK en `a4lab-a-20261019-235036`. A5 copió el mundo A3 (`build/a5-closure/n1-a3copy-20261020-1030`) y arrancó en `676,71,639` con objetivo `649,76,616`: el recorder nuevo capturó `GUARD_STATE` (recuperación no rechazada, `rechaza=false`), la ruta tomó `STEP_UP` y terminó `ARRIVED`; `build/a4lab/n1-a3copy-20261020-1030/result.json` = **INCONCLUSIVE**, `n1BreakSeen=true`. Falta el estado causal exacto del BREAK original. |
| **N2** — terreno dinámico / ruta stale | **PASS (3/3)** | `a4lab-c-20261019-234850`, `a4lab-c-20261020-002141`, `a4lab-c-20261020-002300`: muro real de 3 bloques en z=12, `RUTA INVALIDADA` y `ARRIVED` en tick 203 en las tres. Trazas en `build/a4lab/<run>/nav-trace.log`; `result.json` PASS. |
| **N3** — DROP: candidato, validación, ejecución, aterrizaje | **PASS (5/5)** | D1 `a4lab-d-20261020-001813`, D2 `a4lab-e-20261020-001939`, D4 `a4lab-d4-20261020-101816`, D5 `a4lab-d5-20261020-005549`. D3 `a4lab-d3-20261020-160056` (semilla 9408): `DROP_UNSAFE candidate=0,101,9 caida=6 maxSafeDrop=3 constructible=false`; `rechazadasPorCaida=94`; luego ruta segura y llegada. **`DROP_CONTIG`: NOT MEASURED.** |
| **N4** — propiedad del movimiento y goals competidores | **PARCIAL: N4.1/N4.3 PASS; N4.2 INCONCLUSIVE** | N4.1 `n4-41-20261020-1745` (9501): `PursueTargetGoal` → `MoveToVillageCenterGoal`, `6→16`, 2 transiciones. N4.3 `n4-43b-20261020-1805` (9504): centro + muro, `0,101,6→1,101,21`, 4 transiciones. N4.2 `n4-42g-20261020-2125` (9510): entidad persistente en piscina 3x3; `EscapeWaterGoal` corre, pero `nav=IDLE`, `obj=null`, `waterExit=false`, 23 alternancias con `FloatGoal`; sin reanudar misión. Sigue INCONCLUSIVE. A5.26: los runs N4 no activaron `devilrpg.nav.custom`; `nav=IDLE` puede ser fallback vanilla. El rerun con custom nav (9511) retiró la entidad en tick 1. A5.27: con `-Ddevilrpg.nav.custom=true`, `EscapeWaterGoal` pidió orilla `-1,102,8`, `navegarTacticamente` devolvió true y `NavExecutor` quedó PLANNING con ese objetivo; salió del agua una vez, pero terminó de nuevo en agua (`-11,61,11`) y no reanudó misión. Sigue INCONCLUSIVE. A5.28: tras salir en `-1,103,7`, la entidad derivó a `-3,101,9`, cruzó el borde del fixture en `x=-4` y cayó (`-4,56,8`); clasificación D (salió del fixture y cayó), no rechazo de ruta. Sigue INCONCLUSIVE. A5.29: arena ampliado; Case A `n4-42m` (9516) sale del agua y queda estable en `-6,101,12` (onGround, dry); Case B (misión) no se ejecutó por limitación del fixture. A5.30: misión de centro/objetivo mueve al ente y reinicia el stuck antes de `EscapeWaterGoal`; no se puede establecer una misión genuina sin perder la precondición de agua. N4.2 sigue INCONCLUSIVE. A5.32: se añadió `WATER_DWELL_TICKS=60` independiente del movimiento horizontal, con reinicio solo por avance real a la misión; Stage 1 salto existente y Stage 2 rotura acotada a 3 bloques vía `breakBlockAt`; Stage 3 slab no implementado. Case A `n4-42n` (9517) sigue saliendo y quedando seco. N4.2 sigue INCONCLUSIVE por misión. Artefactos en `build/a5-closure/a5.29-...`, `a5.30-...` y `a5.32-20261020-2355/`. |
| **S1** — defensa estándar | **PASS** | `prod-20261019-2130`: 4 guardias (2 espadachines, 2 arqueros) equipados, oleada 8/8, brecha real, entradas, `Aldea 0 salvada` y recompensa. `a43-prod-20261019-2355` repitió la oleada 8/8 y la salvación. |
| **S2** — obstrucción durante un asedio | **PASS** | `a5s2e-20261020-1515` (semilla 9305): guardia fundador `5592838c...`; su ruta real tenía próximo nodo `58,64,-32`; se insertó piedra (aire→piedra) en tick 1842 y un tick después la ruta pasó a `58,64,-31`. `targetVerified=true`, `navigationResponseObserved=true`; terminal salvada en tick 3114; 200 ticks post-terminal; cierre automático. Artefactos en `build/a5-closure/a5s2e-20261020-1515/` y `build/a5-closure/a5.12-20261020-1525/`. |
| **S3** — derrota / caída de la aldea | **PASS (automatizado)** | `a5s3-20261020-1210` (semilla 9105): controlador sin chat; 4 fundadores equipados, oleada 8/8 en tick 1841, `La aldea 0 ha CAIDO` en tick 4242, **1** mensaje de derrota, **0** mensajes de salvada, **0** recompensas XP, ventana post-terminal de 200 ticks sin reinicio. Artefactos en `build/a5-closure/a5s3-20261020-1210/` y `build/a5-closure/a5.8-20261020-1225/`. |
| **S4** — estabilidad post-terminal | **PASS** | En `a43-prod-20261019-2355`, tras `Aldea 0 salvada`, `asedio ... NO arranca` = **0**, `[A2N]` = **0**, `[A4LAB]` = **0** durante la ventana posterior. `postterm-20261019-2140` ya había dado 0 avisos. |
| **S5** — repetición con semillas independientes | **PASS (3/3)** | `a5s5a-20261020-1355` (9401), `a5s5b-20261020-1405` (9402), `a5s5c-20261020-1425` (9403): cada uno con 4 fundadores equipados, oleada 8/8, brechas reales, terminal salvada, 200 ticks post-terminal, cierre automático y 0 JVMs de cliente. Artefactos en cada directorio y en `build/a5-closure/a5.11-20261020-1455/`. |

**A5.9 — arranque y cierre automáticos:** el smoke `a5smoke-shutdown2-20261020-1255` pasó sin interacción humana:
`options.txt` aislado con `pauseOnLostFocus:false`, `onboardAccessibility:false`, `skipMultiplayerWarning:true`;
resultado escrito, cliente cerrado por `CloseMainWindow`, `remainingTestClients=0`. Evidencia en
`build/a5-closure/a5.9-fix-20261020-1300/A5.9-FIX-report.md`.

**Bloqueos concretos para cerrar:** N1 necesita reconstruir la geometría/estado causal del `BREAK` original;
`DROP_CONTIG` necesita una candidata real con columnas vivo/planificada no contiguas; N4 necesita un escenario runtime
de propiedad. **A2 sigue ABIERTO. `DROP_CONTIG` sigue NO MEDIDO.**

### Cola vigente

**A2 · ORIGEN DEL OBJETIVO ENTRE IGUALES: ENCONTRADO, ARREGLADO Y — CLAVE — NO ERA LA CAUSA DEL ATASCO
(15-oct-2026).**
**[MEDIDO] El origen es la VENGANZA heredada, no un selector de búsqueda.** Se instrumentó el arnés para registrar
cada CAMBIO de objetivo con identidad y pila de llamadas. En `build\a2n\a2n-20261015-142513` los cuatro casos de
misma especie ocurren **todos en `t=2620`**, justo tras el cambio de fase A→B, y **varios asaltantes a la vez
apuntan al MISMO agresivo** (`#209` desde z0, z2 y z7; `#210` desde z1). Ese patrón —coincidencia temporal exacta y
objetivo compartido— es el de una **reyerta**, no el de una búsqueda de objetivo. La pila solo muestra el punto de
lectura del arnés, así que la asignación ocurrió en el tick de IA, lo que encaja con `HurtByTargetGoal` (heredado de
`Zombie` vía `super.registerGoals()`, L1056), que **no filtra por clase**.
**[ARREGLADO] Se sustituye por una venganza que excluye a los agresivos** (`AggressiveZombieEntity.java` L1059-1080:
se recogen las `HurtByTargetGoal` heredadas y se quitan, y se añade
`new HurtByTargetGoal(this, AggressiveZombieEntity.class).setAlertOthers()`), conservando la venganza contra
jugadores, aldeanos, golems y animales. **Verificado en dos corridas: `mismaEspecie` pasa de 4 a 0.**

**[INCIDENTE PROPIO, corregido] La primera versión del arreglo tenía un bug real.** Quitaba los goals **dentro** del
bucle sobre `targetSelector.getAvailableGoals()`, que es la colección viva: eso provocó
`NullPointerException: Cannot invoke "WrappedGoal.getGoal()" because "wrapped" is null` y **abortó la corrida** con
`RUN INVALIDO` (`a2n-20261015-144525`, 1/8). Corregido recogiendo primero y quitando después; confirmado con
`INVALIDO=0` en la corrida siguiente. Dejó además un JVM de juego huérfano que hizo fallar el lanzamiento siguiente
(se mató y se repitió).

**[MEDIDO — LO IMPORTANTE] El arreglo de objetivos NO mejora el asedio.** Con `mismaEspecie=0` y sin corridas
inválidas: `a2n-20261015-143956` → **5/8** (rezagados z0 85.67, z4 68.79, z7 84.37);
`a2n-20261015-150233` → **4/8** (rezagados z0 85.18, z3 67.60, z4 67.20, z7 84.89). Es decir: **z0 y z7 siguen sin
entrar aunque ya no se apunten entre ellos**, y sus distancias son las mismas de siempre (≈85). Por tanto la
persecución entre iguales era un **defecto real pero no la causa** del estancamiento, y **el problema de z0/z7 es de
navegación**: se quedan clavados a ~85 bloques del centro, lejos del muro, sin llegar nunca a la fase de brecha.
El arreglo se conserva porque elimina una reyerta que no existe en el comportamiento vanilla del zombi (los zombis
no se atacan entre sí) y porque `mismaEspecie=0` está verificado; **pero no debe contarse como progreso de asedio.**

**A2 · ETAPA 2 — SUBSISTEMA DE NAVEGACIÓN PROPIA (15-oct-2026). Estado: componentes escritos y COMPILANDO, sin
integrar ni medir todavía.**
Se crea el paquete `com.chipoodle.devilrpg.entity.nav` con cinco piezas de responsabilidad separada (no un
monolito). **Compila** (`compileJava` OK) pero **NO está cableado a `AggressiveZombieEntity`**: la implementación
actual (`AggressiveZombiePathPlanner` + goals) sigue siendo la que corre, tal y como pedía el plan de migración.

| Fichero | Responsabilidad |
|---|---|
| `nav/NavState.java` | Máquina de estados explícita: `IDLE, PLANNING, TRAVERSING, OBSTRUCTED, RECOVERING, ARRIVED, FAILED`, con presupuesto por estado. |
| `nav/NavRoute.java` | Ruta propia: nodos ordenados con **transición explícita** (`WALK, STEP_UP, JUMP_UP, DROP, DOOR, BREAK, BRIDGE`) y `signature()` para poder **rechazar de verdad una ruta repetida**. |
| `nav/NavObstruction.java` | Clasificación `PASSABLE/STEP_UP/JUMP_UP/DROP/BREAKABLE/PROTECTED/BLOCKED`, con `Policies` **inyectables** para no duplicar la protección de la aldea. |
| `nav/NavPlanner.java` | A* acotado sobre la rejilla con coste por transición (romper = 12, puentear = 14) y **presupuesto duro** de expansiones. |
| `nav/NavExecutor.java` | **Único** dueño de `moveTo`: recibe objetivos (`setObjective`), replanifica solo si el objetivo se desplaza >3 bloques, detecta oscilación A→B→A, lleva presupuestos de reintento y recuperación, y expone `metrics()`. |

**Decisiones atadas a fallos medidos:** el coste alto de romper evita que el túnel sea la opción preferida (el
baseline picaba 199 bloques); el presupuesto duro de expansiones ataca el cuelgue de 60 s del watchdog que provocó
una versión anterior (9 anillos × 6× nodos en un tick); la `signature()` de ruta ataca el fallo concreto de **z3**
(`plan=local-escape`, `waypoint=602,103,572`, `rejected=4`, `destinationStalled` 18→28→38: el mismo punto rechazado
reintentado); y el objetivo-dispersión ataca el requisito de objetivo móvil sin replanificar cada tick.

**Lo que FALTA (bloqueantes, en orden):**
1. **Cablear** `NavExecutor` en `AggressiveZombieEntity` con una `Policies` real y una `RecoveryAction` real, detrás
   de un flag para poder comparar con la implementación actual.
2. **Impedir que los goals de vanilla cancelen el movimiento** (el motivo de que exista un ejecutor único): hay que
   retirar de `MOVE` a `PatrolHomeGoal`/`FireballAttackGoal` o hacer que envíen objetivos en vez de navegar.
3. **Arreglar z0/z7**, que **no es del planificador**: llevan a otro `aggressive_zombie` como objetivo, y eso deja
   `MoveToVillageCenterGoal.canUse()` en falso (exige `getTarget() == null`) mientras `PursueTargetGoal` no corre,
   con `MOVE=-` y `plan=idle`. **No se ha identificado todavía QUÉ selector admite a un `AggressiveZombieEntity`**:
   los selectores configurados son Player, `ITamableEntity` (LivingEntity), Animal, Villager y IronGolem, y
   `HurtByTargetGoal` (heredado de `Zombie` vía `super.registerGoals()`, L1056) solo debería reaccionar a un golpe
   recibido. **Hipótesis sin confirmar**, por eso no se ha tocado.
4. **Pruebas**: no se han añadido aún las pruebas de transiciones/objetivos imposibles, ni se ha medido con el nuevo
   sistema. **No hay ninguna medida nueva de asedio ni de caza con esta implementación.**

**A2 · DECISIÓN FINAL DE ASEDIO — LOS TRES REZAGADOS SON DOS DEFECTOS DISTINTOS (15-oct-2026).**
**[MEDIDO] Tercera corrida consecutiva en 5/8** (`build\a2n\a2n-20261015-135451`, semilla 12345, muro, 0 fallos de
instrumento, 0 cuelgues, 5 muertes): `entraronPerimetro=5`; entran z1 (3.14), z2 (1.89), z4 (17.03), z5 (2.72),
z6 (2.70). **Rezagados: z0 (84.93), z3 (69.43), z7 (84.95)** — los mismos que en el baseline, pero **mucho más
lejos**. Con esto son **tres de tres** corridas en 5/8 con el mismo trío: 5/8 es el valor estable, no una racha.

**[MEDIDO] z0 y z7 no son un fallo del planificador: se quedan SIN NINGÚN GOAL DE MOVIMIENTO porque se apuntan
entre ellos.** Muestras finales, idénticas durante miles de ticks: `pos=692,62,447` (z0) y `pos=739,64,499` (z7),
`nav=quieto`, `goals=P3:TraverseGoal` (a veces `P1:FloatGoal` o `P8:RandomLookAroundGoal`), **`pursueRunning=false`
y `MOVE=-`**, `planner={plan=idle, waypoint=-, stalled=0, rejected=0, retry=0}`, y
**`objetivo=aggressive_zombie@640, 71, 490`**. Es decir: llevan a otro **zombi agresivo** como objetivo; como
`MoveToVillageCenterGoal.canUse()` exige `getTarget() == null`, la marcha a la aldea queda **bloqueada**, y
`PursueTargetGoal` no los mueve. El `plan=idle` es **consecuencia**, no causa: nadie pide navegación.
**[MEDIDO] z3 SÍ es un fallo del planificador (y es el único de los tres).**
`plan=local-escape`, `waypoint=602,103,572`, `rejected=4`, `destinationStalled` subiendo 18 → 28 → 38, y la
posición apenas se mueve (605 → 603 → 601 en `x`): vuelve a planificar **al mismo punto ya rechazado** una y otra
vez sin salir del encierro. Esto es exactamente el fallo que la reescritura autorizada debe resolver.
**[DECISIÓN] La reescritura de navegación queda JUSTIFICADA por z3** (fallo de planificación repetible, 3 de 3
corridas, mismo punto rechazado). Pero **z0/z7 tienen un arreglo pequeño, fuera del planificador**, que vale la
pena antes o junto con la reescritura: excluir a `AggressiveZombieEntity` como objetivo de la caza entre iguales
(la lista de objetivos los alcanza), o permitir la marcha a la aldea cuando el objetivo no es perseguible. **Riesgo
a vigilar:** cualquier cambio de prioridades puede regresar la caza, que ya pasa sus criterios en dos corridas
idénticas; por eso debe probarse contra caza **y** asedio.

**A2 · DIAGNÓSTICO DEL ARBITRAJE Y DE LA LÍNEA DE VISTA (15-oct-2026, sesión de verificación independiente).**
Todo lo de abajo está **medido** salvo donde se dice explícitamente que es hipótesis.

**[MEDIDO] El 0/8 de asedio NO se reprodujo.** Dos corridas controladas del mismo escenario (semilla 12345, muro,
`a2n-20261015-104341` y `a2n-20261015-105323`) dieron **5/8 entradas**, idéntico al baseline conservado
(`a2n-20261014-215503`, 5/8), y con **más** actividad de brecha que el baseline (15 `ABRE BRECHA` frente a 14).
El 0/8 anterior (`a2n-20261014-231256`) queda como **valor atípico no reproducido**, no como regresión.
**Queda abierto el objetivo de 8/8.**

**[DECISIÓN — 15-oct-2026] NO se reescribe la navegación todavía.** Justificación medida: la navegación actual
**no es la que falla**. (1) Las dos corridas de caza pasan persecución, recuperación y reubicación; (2) el atasco de
la caza resultó ser **línea de vista del test**, no planificación; (3) el «objetivo válido sin goal activo» resultó
ser **`getTarget()` null**, no un bloqueo del selector (`puedeEntrar=true` siempre); (4) el fallo de ataque resultó
ser **el `FakePlayer` como objetivo de daño**. De los cinco fallos investigados, **ninguno era del planificador**.
Además el asedio **no se ha re-medido desde que se agotó este hilo**: la última medida es 5/8 (dos veces), idéntica
al baseline, y **no hay evidencia medida que justifique un planificador nuevo**. Lo que queda (8/8) exige primero
una medida fresca del asedio con el diagnóstico de arbitraje ya instalado en el arnés; **si tras eso sigue sin
llegar a 8/8 con el planificador actual, la reescritura queda autorizada y justificada**. No se ha empezado ninguna
reescritura y no se ha tocado el planner.

**[MEDIDO] Se DESMINTIÓ la hipótesis de que el arbitraje de goals impide la persecución.** Se añadió al arnés un
diagnóstico de solo lectura que hace la MISMA pregunta que el planificador del juego
(`WrappedGoal.canBeReplacedBy`). Resultado en **todas** las transiciones de atasco:
`Pursue=P4 canUse=false running=false puedeEntrar=true`. Es decir: **no hay conflicto de flags MOVE/LOOK**; el
selector no está rechazando el goal. `PursueTargetGoal.canUse()` devuelve falso simplemente porque **`getTarget()`
es null**: el zombi **no ha adquirido objetivo**. La lectura anterior de «objetivo válido sin goal activo» era un
objetivo transitorio que la línea de vista revocaba de inmediato, no un bloqueo del planificador.

**[MEDIDO] La causa de la intermitencia de la caza estaba en la GEOMETRÍA DEL TEST, no en producción.**
`NearestAttackableTargetGoal` (condiciones por defecto) **exige `Sensing.hasLineOfSight`**, que cae a un raycaste
real. El ayudante del arnés `prepararDestinoAccesibleDespuesDeMover` limpiaba el pasillo **solo a la altura de los
pies**, así que tras reubicar al jugador de pega el raycaste quedaba tapado a la altura de los ojos:
`vistaCruda=false vePegaSensing=false target=null` en **629 transiciones seguidas** (`a2n-20261015-115326`).
Añadiendo el despeje por encima de la cabeza (`despejarCorredor`), la adquisición natural ocurre a los ~4 ticks de
acercar al jugador. **No se tocó producción**: producción está haciendo lo correcto (no se adquiere lo que no se ve),
y debilitar la línea de vista para que pase un test es justo lo prohibido.

**[MEDIDO] Persecución continua, recuperación y reubicación: SÍ** (`a2n-20261015-115958`):
`naturalTargetAcquired=true`, `targetMoved=true`, `targetRetained=true`, `advancedAfterMove=true`,
`wallBlocksOpened=4`, y `Pursue=P4 canUse=true running=true` de forma continua hasta **`distPega=1.57`** con
`target=player#53 alive=true LOS=true`.

**[RESUELTO — 2 corridas idénticas] El golpe y el daño SÍ funcionan; el que no vale como objetivo es el
`FakePlayer`.** Instrumentando el estado privado de `PursueTargetGoal` (`attackTicks`, `inAttackRange`, por
reflexión) y llamando al MISMO `doHurtTarget`, se midió (`a2n-20261015-131854`, semilla del arnés):
`enRango=true`, `attackTicks=11`, `target=player`, `puedeAtacar=true`, `invulnerable=false` y
**`doHurtTarget->false` en 6 llamadas forzadas con 0 de daño y la salud clavada en 20.0**. Es decir: la rama de
ataque **sí se ejecuta** y **falla dentro de `doHurtTarget` cuando el objetivo es un `FakePlayer`**. **No es un
defecto de producción ni un fallo del observador.**
Para validarlo se añadió al arnés un **objetivo real** (un aldeano) al lado del punto de aparición, y el camino de
daño quedó demostrado en **dos corridas idénticas**:
`a2n-20261015-132637` → `danoAplicado=10.80`, salud del aldeano `20.0 → 9.20`, `aldeanoGolpes=1`;
`a2n-20261015-133257` → `aldeanoGolpes=3`, salud `20.0 → 3.70` (y una llamada forzada con daño,
`danoDeAtaqueForzado=1`). La diferencia entre `0` y `1` en las llamadas forzadas es el **tiempo de recarga del
ataque**: una llamada con la recarga a medias hace daño reducido (0.7 frente a 7.80), así que la cifra suelta no
indica fallo.
**Limitación que hay que dejar escrita:** `FakePlayer` sirve como evidencia de **IA de persecución** (adquisición,
movimiento, recuperación, reubicación) pero **NO** para validar daño, login real, red ni ciclo de vida del asedio.
La verificación de daño necesita un objetivo real, como el aldeano de estas corridas.

**[MEDIDO] Caza: los cinco criterios pasan en DOS corridas idénticas** (`a2n-20261015-132637` y
`a2n-20261015-133257`): adquisición natural por el flujo real de `AggressiveZombieSpawnRule`, movimiento hacia el
objetivo, recuperación de ruta bloqueada (`routeFailed=true`), progreso tras reubicar al jugador
(`targetRetained=true`, `advancedAfterMove=true`) y ataque con daño verificado. `movedFromSpawn=true` en ambas.

**[MEDIDO] No se hizo ningún cambio de producción en esta sesión.** Lo único que se tocó fue
`PlayerCapabilityForgeEventSubscriber.java`: se **normalizaron los finales de línea** (646 CRLF → 646 LF) para que
`git diff --check` dejara de fallar; con `git diff --ignore-cr-at-eol` el cambio semántico sigue siendo **exactamente
las 5 líneas** que ya había (`+5`, sin lógica nueva). `compileJava`, `lint_aldea.py --strict` y `git diff --check`
pasan.

**A2 · TAREA ACTIVA — NAVEGACIÓN AGRESIVA (15-oct-2026).** El planner actual volvió a **5/8** entradas en el
escenario difícil (8/8 vivos; 12 brechas; sin muertes, cuelgues ni fallos de instrumento). La caza sin aldea también
se midió con el flujo de `AggressiveZombieSpawnRule`: adquirió al jugador naturalmente, recuperó una ruta fallida,
continuó después de que el jugador se moviera y dio un golpe real. **A2 sigue abierto**: el objetivo final es 8/8 y
quedan tres asaltantes fuera. Los resultados completos están en **«A2 — seguimiento de rutas y caza (15-oct-2026)»**
más abajo.

**A2 · PRIMER INTENTO DE NAVEGACIÓN COMPARTIDA (14-oct-2026; reemplazado por el seguimiento de abajo).**
Cambio **de producción** en `AggressiveZombieEntity`. **Qué se cambió:** (1) se **extrae** el mecanismo a la entidad y
lo comparten los dos destinos: `navegarHacia(destino, …)` (navegación) y `desatascarseHacia(x,y,z)` (recuperación:
puente, túnel, escalón, peldaño, salto y empujón directo con guarda de precipicio); (2) **el destino lo elige cada
goal por su cuenta** — el centro de la aldea en `MoveToVillageCenterGoal`, y la **presa recalculada** en el nuevo
`PursueTargetGoal` —, que es lo que separa «mecánica» de «selección de destino»; (3) **el orden de la navegación se
corrige**: primero la vía normal de vanilla (`moveTo(x,y,z)`), y los **puntos intermedios solo como respaldo** con
**la misma `Path` validada**. La hipótesis propuesta entonces (no aislada causalmente) era que sondear con
`createPath(Set)` y navegar con `moveTo(x,y,z)` son dos búsquedas distintas y que la segunda no encontraba la ruta.
La corrida midió que el resultado **empeoró de 5/8 a 1/8**, pero no probó ese mecanismo por separado. Restaurado el orden, la
aldea **vuelve a 5/8**; (4) **`PursueTargetGoal`** sustituye a `MeleeAttackGoal` (misma prioridad 5, registrado
antes): persigue la presa **recalculando el destino**, hace el cuerpo a cuerpo él mismo y **usa la recuperación
compartida**. Hacía falta porque un zombi creado por `AggressiveZombieSpawnRule` **no tiene centro de aldea**
(`setVillageCenter` no se llama y `MoveToVillageCenterGoal.canUse` exige centro), así que su persecución dependía
solo del `MeleeAttackGoal` de vanilla: **sin detección de atasco y sin recuperación**.
**Medido** (`build\a2n\a2n-20261014-215503`, semilla 12345, muro, 0 fallos de instrumento, **sin cuelgues**):
`entraronPerimetro=5`, `dMin<=3=5`, `dMin<=8=5`; z1 **0,10**, z5 **0,05**, z6 **0,39**, z3 **2,01**; los tres
rezagados (z0/z4/z7) siguen con `alcanza=0` por 44 sondas y quedan a 57-65 bloques.
**Defecto propio, cazado y corregido en esta misma iteración:** la versión anterior probaba **9 anillos por tick**
con el multiplicador de nodos a 6 y **colgó el servidor** (`ServerHangWatchdog: a single server tick took 60.00
seconds`); acotado a 2 anillos, multiplicador 1 y enfriamiento, ya no se cuelga (0 avisos del watchdog).
**Lo que NO está hecho y hay que decir claro: la prueba de caza SIN asedio no se ha ejecutado.** El código del
`PursueTargetGoal` está compilado y registrado, pero **no hay medida** de que un zombi creado por
`AggressiveZombieSpawnRule` persiga a un jugador y se recupere; el requisito de probar los dos escenarios por
separado **queda pendiente**. El escenario de asedio está medido; el de caza, **no**.
Ese código y esos resultados son historia del intento anterior; no son una medida del planner actual. El objetivo de
8/8 y la prueba de caza siguen pendientes.

**A2 · PLANNER DE RUTAS Y PERSECUCIÓN (14-oct-2026) — ESTADO DE ESA FECHA; ver seguimiento del 15-oct.**

> El estado «runtime pendiente» que sigue conserva fielmente lo que se sabía el 14-oct; las pruebas posteriores y el
> arreglo de los puntos locales quedan registrados en el seguimiento de abajo.

**Causa que muestran los datos:** la navegación directa devolvió `canReach=false` en 44 sondas para cada uno de los
tres rezagados observados; elevar las recuperaciones de 5 a 21 no cambió el 5/8. **Lo que permite concluir el código,
pero sigue siendo inferencia:** el respaldo anterior solo probaba ocho puntos cardinales fijos, a 54 y 70 bloques del
destino; no mantenía memoria efectiva de destinos fallidos ni construía una ruta por puntos locales. Si ninguno de
esos ocho puntos era alcanzable, el goal volvía a la misma recuperación orientada hacia el destino final. No se midió
en juego cuál de los ocho puntos falló para cada rezagado, así que no atribuyo el resultado a un punto concreto.
Para la caza aparece además una discrepancia directa en el código: la regla coloca zombis a hasta **86** bloques, pero
el `FOLLOW_RANGE` anterior era **64**. Un zombi que nacía más lejos no podía adquirir al jugador con el selector
vanilla hasta que este entrara en rango; el valor ahora es **96**. La prueba real de adquisición sigue pendiente.

**Diseño actual:** `AggressiveZombiePathPlanner` es una capa compartida y acotada. Los goals eligen el destino; el
planner crea primero una `Path` directa y sigue esa misma instancia. Si no alcanza el destino, puede recorrer el tramo
parcial que el buscador sí encontró; después prueba hasta 32 candidatos de superficie en cuatro anillos y ocho
rumbos, y solo navega una ruta que el pathfinder haya validado. El fallback consulta columnas de chunks cargados y
conserva como máximo 32 puntos recientes durante 200 ticks, incluso cuando el destino se mueve; reintenta una
búsqueda fallida cada 40 ticks y abandona un tramo sin progreso tras 160 ticks. Como máximo hace dos búsquedas por
intento. La recuperación física permanece separada: un intento por
llamada, con presupuesto existente de túnel/escalera/puente y enfriamiento creciente cuando no cambia el terreno.
`TraverseGoal` ahora exige dos ventanas de 40 ticks sin desplazamiento antes de intervenir; `BreakBlockGoal` aplica
enfriamiento creciente cuando no puede romper y pausa tras 24 acciones exitosas sin dos bloques de avance o cambio
de objetivo.

`PursueTargetGoal` es el único goal de movimiento de combate (prioridad 4) y usa el mismo planner para el objetivo
activo; el flanqueo de manada se calcula dentro de ese goal. La marcha a la aldea conserva prioridad menor (7) y no
arranca con un objetivo de combate. `AggressiveZombieSpawnRule` no requiere aldea: el selector de objetivos detecta
jugadores y `PursueTargetGoal` funciona sin `villageCenter`. También se retiran los goals heredados de zombie que
podían competir por MOVE: `ZombieAttackGoal` (prioridad 2), `MoveThroughVillageGoal` (6) y
`WaterAvoidingRandomStrollGoal` (7). Esa competencia se identificó en el código; Harness debe comprobar su efecto
real durante el asedio.

> **Corrección de línea:** el texto del 14-oct dice que `TraverseGoal` espera dos ventanas de 40 ticks. El código
> actual usa `VENTANA_DE_MOVIMIENTO=10` y `VENTANAS_PARADO_ANTES_DE_ACTUAR=1` (`AggressiveZombieEntity.java:1888-1891`);
> esa cifra anterior no describe la versión medida.

**Verificación de esta entrega:** no se ejecutó Minecraft ni Harness; no hay resultado de runtime que permita afirmar
8/8 o persecución de jugador confirmada. Compilación: en PowerShell se fijaron
`$env:GRADLE_USER_HOME = 'C:\Users\Christian\Documents\DevilRpg\.gradle-home'` y
`$env:JDK_JAVA_OPTIONS = '-Djdk.net.unixdomain.tmpdir=C:\Windows\Temp'`, luego
`.\gradlew.bat --info compileJava` → `BUILD SUCCESSFUL in 29s`, exit 0, con JDK 21.0.7. Lint:
`python tools\lint_aldea.py --strict` → `lint aldea: OK (sin patrones peligrosos)`, exit 0. Diff-check:
`git diff --check` → exit 0, sin salida. Harness debe repetir el escenario difícil de asedio y exigir
**8/8 dentro del perímetro**, y crear zombis mediante
`AggressiveZombieSpawnRule` sin aldea para comprobar adquisición/persecución de jugador y ataque. En ambos debe vigilar
que no haya bucles de puntos, acciones repetidas sin cambios, excavación ilimitada, conflicto de navegación o cuelgue
del servidor. La limitación conocida es que terrenos con rutas físicamente cerradas o chunks sin cargar todavía
pueden no ofrecer un punto alcanzable; en ese caso el planificador se mantiene acotado y el resultado de juego sigue
siendo una pregunta para Harness.

### A2 — seguimiento de rutas y caza (15-oct-2026)

**Causa apoyada por código y medidas (la relación causal exacta sigue siendo inferencia):** con la versión que daba
0/8 (`build\a2n\a2n-20261015-015450`), el plan observado era `local-escape`; el planner elegía antes cualquier ruta
local alcanzable que el tramo parcial hacia el destino. Un `MOVING` de esa ruta no significaba que el monstruo se
acercara al centro. `MoveToVillageCenterGoal` solo llama a `recoverNavigation` al recibir `STALLED` o tras 80 ticks
de `NO_ROUTE`; por eso una secuencia de destinos locales alcanzables podía mantener la marcha y dejar sin turno la
brecha física. En las muestras de la ola, `BreakBlockGoal` y `TraverseGoal` son pasivos (sin `MOVE`), prioridad 3,
y `MoveToVillageCenterGoal` (prioridad 7) posee el movimiento; con presa, `PursueTargetGoal` (prioridad 4)
se convierte en la meta de movimiento y la meta de aldea deja de poder usarse. Esto descarta que la prueba respalde la hipótesis de dos metas `MOVE`
compitiendo; no es una ablación, así que el acoplamiento entre el orden del planner y la bajada de brechas es una
inferencia fuerte, no una prueba aislada.

**Corrección y arquitectura actual:** `AggressiveZombiePathPlanner` intenta primero una ruta directa; después busca
una ruta de superficie alcanzable cuyo punto intermedio reduzca al menos 3 bloques la distancia al destino. Si no
hay tal punto, sigue el tramo parcial de la ruta directa/intermedia antes de ofrecer un escape local. Cuando un tramo
parcial termina, devuelve `STALLED` y el goal puede ceder a la recuperación física. El escape local permite como
máximo 4 bloques temporales de desvío, dentro de un límite total de 16 desde el mejor avance. Rechaza hasta 32
destinos recientes durante 200 ticks, espera 40 ticks entre búsquedas fallidas y deja de seguir rutas sin avance
tras 160 ticks. Cada planificación llama al pathfinder como máximo tres veces; todas las rutas de movimiento son
instancias `Path` producidas por `createPath`. La recuperación actúa aparte y hace como máximo una acción por
llamada; la excavación sin avance conserva su tope de 24 acciones.

**Medida de asedio final** (`build\a2n\a2n-20261015-022203`, comando `powershell -ExecutionPolicy Bypass -File
build/a2n/lanzar-a2n.ps1 -Modo nav -Semilla 12345 -Pared 1 -FaseA 3000 -FaseB 3060 -LimiteMinutos 7`):
**5/8** entraron al perímetro, 8/8 sobrevivieron; 6 celdas del anillo quedaron abiertas. Hubo **12** `ABRE BRECHA`,
92 acciones de picado, 7 comienzos de taladro, 1 puente, 12 escalones y 2 acciones de cava hacia abajo. Los tres
que no entraron fueron z0 (mínimo 84,44 bloques del centro), z3 (69,43) y z7 (84,28); z3 quedó en una meseta alta
(posición final 596,103,577). El log registró 0 muertes, 0 fallos de instrumento, 0 watchdog hangs y el servidor
cerró en 3060 ticks. El criterio interno del arnés de **6/8** no pasa; el mínimo pedido para esta primera corrección
(recuperar la línea base de 5/8) sí. El objetivo **8/8 no está logrado**. Esta corrida mide la ola de navegación,
no el reloj completo del asedio: el arnés no tiene un `ServerPlayer` real para iniciar ese ciclo.
El log sí tuvo dos avisos `Can't keep up` durante la generación previa (304 y 238 ticks detrás); no hubo watchdog.
Después del marcador `FIN`, el arnés mató el PID aislado: por eso el `runServer` hijo registra salida `-1` aunque el
arnés cerró la medición en forma normal.

En la muestra t=2980, z0 llevaba `P3:TraverseGoal` + `P7:MoveToVillageCenterGoal`, `plan=local-escape`, 23 puntos
rechazados y 67 ticks sin acercarse al destino; z3 conservaba esas metas, `plan=partial-direct`, 4 puntos rechazados
y 36 ticks; z7 tenía `plan=local-escape`, 13 puntos rechazados y 44 ticks. Los tres seguían fuera mientras el
servidor los movía. El informe final cuenta 4 decisiones `BLOQUEADO_OBRA_DEL_ASEDIO`: las trazas muestran adoquines
protegidos de la obra a `y=101-103`, no celdas del anillo; no atribuyo a esa protección el atasco completo. z3
en esa muestra estaba en `(596,102,585)` sobre terreno alto.

Variantes preservadas como evidencia: con el sobre de desvío pero escapes locales sin prioridad corregida,
`a2n-20261015-015450` dio 0/8, 0 brechas y 24 escalones; al hacer que el tramo parcial precediera al escape local,
`a2n-20261015-020144` recuperó 5/8 y 14 brechas; exigir que todos los escapes locales reduzcan distancia dio
4/8 en `a2n-20261015-021151`. Por eso la implementación actual conserva un desvío lateral pequeño, pero limitado.

**Medida de caza sin aldea** (`build\a2n\a2n-20261015-022649`, comando
`powershell -ExecutionPolicy Bypass -File build/a2n/lanzar-a2n.ps1 -Modo hunt -LimiteMinutos 5`): el arnés creó
al monstruo mediante el flujo de spawn, sin centro ni asedio. Resultado: `spawned=true`, `noVillage=true`,
`naturalTargetAcquired=true`, `movedFromSpawn=true`, `routeFailed=true`, `targetMoved=true`, `targetRetained=true`,
`advancedAfterMove=true`, `attackRelocation=true`, `attacked=true`, `hitEvents=1`, distancia mínima 1,27 y salud
del `FakePlayer` 15,09886 tras daño real de 4,90114. La adquisición no fue asignada manualmente. El `FakePlayer`
sirve para medir selector, persecución y daño en servidor; no demuestra inicio de sesión en `PlayerList`, paquetes
ni sincronización con un cliente conectado. No hubo fallos de instrumento ni muertes.

**Qué queda para Harness:** repetir el mismo escenario difícil con el código actual; exigir finalmente 8/8; vigilar
las tres rutas rezagadas (dos al este y una sobre terreno alto), las celdas abiertas, las acciones de brecha y
escalón, las listas de puntos rechazados, las excavaciones, muertes y watchdog. La caza ya tiene resultado positivo
con `FakePlayer`, pero queda fuera de esa demostración el cliente conectado. A2 permanece abierto hasta medir 8/8.

**Chequeos de implementación:** `compileJava` → `BUILD SUCCESSFUL`; `python tools/lint_aldea.py --strict` →
`lint aldea: OK (sin patrones peligrosos)`; `git diff --check` → exit 0 (ajuste temporal de Git
`core.whitespace=cr-at-eol` para conservar el archivo Java CRLF). Sin commit ni push.

**A2 · SIN AVANCE + RODEO DE RECUPERACIÓN (intento anterior, ya sustituido).** Cambio
**de producción** en `AggressiveZombieEntity.MoveToVillageCenterGoal`, con evidencia en
`build\a2n\a2n-20261014-131506` (antes) y `build\a2n\a2n-20261014-152136` (después). **Qué se cambió:** (1) la
detección de «no avanza» medía el **temblor de un tick** (`distanceToSqr(ultimaPos) > 0.25` reiniciaba `evalTicks`
y salía con `return`), así que con un monstruo agitándose en la misma celda el intento de abrirse paso **no llegaba
a dispararse jamás**; ahora se exige **acercarse de verdad** (`IMPROVEMENT_THRESHOLD` contra una referencia con
ventana de paciencia de 7 s). (2) Tras **3** intentos seguidos sin avance **ni ruta viva** (`canReach`), el
asaltante hace un **rodeo acotado** de 10 s hacia un punto lateral que tenga ruta, y **mientras rodea no pica**.
**Medido:** la detección **funciona** — pasó de **5** recuperaciones (corrida `…145202`) a **21**
(`…152136`), alcanzando a 7 de los 8 asaltantes. **Pero la convergencia NO
mejoró**: `entraronPerimetro=5`, `dMin<=3=5`, `dMin<=8=5`, **idéntico** al baseline sin el cambio
(`…131506`), y los tres rezagados siguen con **`alcanza=0` de 72-77 sondas**. La actividad de picado apenas cambia
(`pica Block{` 199→166 pero `CAVA hacia abajo` 29→38). **Interpretación previa (inferencia, no prueba del terreno):**
en esas posiciones el buscador dio `canReach=false` en todas las sondas; de ahí se planteó la hipótesis de que un
rodeo basado solo en movimiento podía no bastar y que se necesitaba modificar el terreno o elegir un destino
alcanzable. Esa hipótesis no se probó. **NO se
hace commit**: falta la mejora de comportamiento que exige el encargo. **Lo que NO se probó:** ninguna intervención
sobre el presupuesto del buscador, y el instrumento de alcanzabilidad (flood-fill) **no es de fiar** (falló su propia
validación en `…141858`), así que la alcanzabilidad del terreno sigue **sin medir**. Se conserva como historia: lo
sustituye el navegador por puntos intermedios de arriba.

**A1 · sigue ABIERTO por un solo criterio.** Falta la verificación no ejecutada: **armas visibles en un cliente real**,
que exige una corrida de cliente con **directorio de juego aislado** (no `run\`) para no tocar `run\saves`. Todo lo
demás del muro y de la fundación consta medido; el terreno queda documentado sin corrección. El jugador debe confirmar
además el resultado en su partida. **Esta limitación no cambia en esta actualización.**

**A0 · CERRADA (13-oct-2026) — el dueño confirma que el oso ya no da problemas.** El oso queda **fuera de la cola
activa**. Historia y arreglos previos, intactos: **I382** (los cuatro sonidos de monta pasan a ser del oso blanco),
**I386** (`HORSE_LAND` de las caídas reimplementado sin sonido de caballo, con el daño mantenido), **I384** (la carga
del salto medida con jugador de verdad: **`carga=0.0` siempre**, `isJumping=false`, `dy=0.0`) y **I385 RETIRADO**
(«salto al pulsar», revertido: la carga sí sirve y lo correcto es hacer que **llegue**). El propietario confirma que
el conjunto **ya no presenta problemas**; **no hay en esta acta una medida nueva e independiente** de la corrección de
A0 — la confirmación es del dueño, no una verificación repetida aquí. Detalle y enlaces en
`docs/aldea-invariantes.md` §«A0 · CORREGIDA Y CERRADA POR CONFIRMACIÓN DEL DUEÑO».

**COLA DIFERIDA** (necesita un dato del jugador antes de tocar código): **(B1)** el haz; **(B2)** el minero;
**(B3)** verificar el orden de objetivos con un asedio real.

Chequeos registrados para la fase anterior A1 (no describen el estado A2 actual): `.\gradlew.bat compileJava` →
exit 0; `python tools\lint_aldea.py --strict` → exit 0 con `lint aldea: OK (sin patrones peligrosos)`;
`git diff --check` → exit 0; 9 archivos modificados, sin artefactos de harness ni mundos de prueba. No hubo commit ni push.

> **Nota de orden**: las notas históricas de `## §2.1` (L563) y de las secciones de diagnóstico A1 (L631, L680) siguen
> diciendo «A1 antes de A0 y A2». **Se conservan tal cual** como registro de lo que se decidió entonces.
> **Actualizado el 22-oct-2026:** A0 cerrada; A2 sigue abierto pero bloqueado por decisión del dueño sobre el
> fixture; la tarea siguiente es **A1-CLIENT**, definida en la sección final «RECONCILIACIÓN 22-oct-2026».

## A2 · ASEDIO AMURALLADO — CONTINUACIÓN FOCALIZADA

El trabajo de restauración del **asedio amurallado completo** (modo `raid` del arnés) tiene su propio registro de continuación:

- **[`docs/autonomous-village/A2-WALLED-RAID-HANDOFF.md`](autonomous-village/A2-WALLED-RAID-HANDOFF.md)**

Ese documento contiene: el objetivo del escenario (semilla `12345`, centro `661,71,533`, 8 asaltantes a radio 95, perímetro 71, muro de 3 de alto a radio 62, fases 3000/6600), la cronología de las cinco ediciones, los hashes verificados, el estado sin ambigüedad (**A2 ABIERTO**, asedio **sin medir**) y los invariantes de seguridad. Consúltese antes de retomar el asedio.

## RECONCILIACIÓN 22-oct-2026 — R4, L40 y golem

Esta sección es la **cola viva** desde esta fecha. No borra el historial anterior; lo marca cuando queda superado.

### Estado aceptado

| Área | Resultado | Evidencia runtime | Alcance |
|---|---|---|---|
| **L40 · leñador** | **PASS** | `l40d-20261022-1845` (semilla 9601); también `l40b-20261022-1350` | Dos ciclos independientes: tala, replante, cruce de vuelta, depósito; segunda tarea interior en `(-59,63,79)`. No incluye crecimiento/abono completo. |
| **R4 · asedio** | **PASS (escenario S5)** | `r4golem-20261022-1900` (semilla 9101) | `VillageManager.start`, 8/8 asaltantes vivos, brechas, guardias combatiendo, `Aldea 0 salvada`, terminal tick 4237, 200 ticks post-terminal. No cubre todas las configuraciones ni la partida real del jugador. |
| **Golem · spawn** | **PASS** | `golem-fresh-20261022-1920` (semilla 9201, aldea nueva) | Aldea generada por `VillageManager.preGenerate` en mundo sin `devilrpg_villages.dat`; centro `(80,97,0)`, kiosco `x=77..83, z=-3..3`, golem `(83,97,5)`, AABB libre, apoyo sólido, vivo 200 ticks. No es cobertura multi-semilla exhaustiva. |

### Correcciones y diagnósticos que quedan como cerrados

- **Guardia/NPE**: `VillagerGuardGoal.sePuedeEstar` ahora rechaza `null`; era un fallo real de arranque del asedio.
- **Asedio sin raiders**: no era defecto de producción. El mundo desechable estaba en **Peaceful** y `Mob.checkDespawn()` eliminaba a los hostiles. El fixture S5 fija **Normal**; la oleada 8/8 permanece viva.
- **Golem**: el candidato `center.offset(4,0,4)` tenía la celda de pies libre, pero la caja de 1,4 bloques solapaba un poste del kiosco. `VillageGenerator.celdaLibreParaElGolem` ahora exige queda fuera de la huella 7×7, valida la **AABB completa** con `level.noCollision(...)`, exige apoyo sólido y no cae al spawn inseguro si no hay candidato.

### Estado documental

- `docs/PENDIENTE.md` sigue siendo **histórico**; su aviso de L1–L4 abiertos quedó superado por L40 (L1 runtime y L2 parcial).
- `docs/aldea-invariantes.md` conserva las actas; la entrada **I291** se completa al final con la corrección AABB.
- `docs/autonomous-village/ESTADO-ACTUAL.md` describe **A2**, que sigue abierto y no queda afectado por R4/L40/golem.
- `docs/autonomous-village/A2-WALLED-RAID-HANDOFF.md` conserva A2 abierto: el S5 de R4 **no** es la aceptación de navegación propia A2.

### A1-CLIENT — PASS (22-oct-2026)

Run `a1client3-20261022-2040`, mundo desechable A5SMOKE, semilla 9101, cliente gráfico aislado. Se celebraron los
cuatro fundadores en fila y se capturó la ventana real. Evidencia visual:
`build/a5-closure/a1client3-20261022-2040/profile.png` y `profile_zoom.png`.

| Puesto | UUID | Servidor | Cliente visible |
|---|---|---|---|
| 0 espadachín | `b9d89ce2-65cd-4814-bc39-c90b5a38ab08` | espada de madera + escudo + 4 piezas de cuero | armadura de cuero, escudo y espada |
| 1 arquero | `22802a3c-4a1e-43b9-b3c8-7fbe14a392f5` | arco + 4 piezas de cuero | armadura de cuero y arco (visible en perfil; la vista frontal lo ocultaba) |
| 2 espadachín | `7b0b24ce-2e72-4652-9621-9b5bfa5f0d31` | espada de madera + escudo + 4 piezas de cuero | armadura de cuero, escudo y espada |
| 3 arquero | `6838dc4b-6953-4971-b3ed-3db99739b6fb` | arco + 4 piezas de cuero | armadura de cuero y arco |

**Resultado:** los cuatro fundadores muestran el equipo esperado. No se cambió producción. La vista frontal inicial
ocultaba el arco del puesto 1; la vista de perfil lo confirmó, así que no hubo defecto de renderer ni de sincronización.

**Nota de fixture:** `guardview` vive solo en `A5TestController` (debug) y coloca a los guardias en fila para la
captura; no altera el comportamiento de producción.

### L2 — prioridad interior/exterior completa

> ✅ **COMPLETADO (23-oct-2026)**: `l2iso2-20261023-0600` demostró la selección interior con el selector real.
> La tarea siguiente es A2, bloqueada por la decisión de umbral del dueño.

**Problema y evidencia.** L40 (`l40d-20261022-1845`) probó que, tras el depósito, el leñador elige una tarea interior
en `(-59,63,79)` y la ejecuta. No prueba la prioridad cuando hay árboles internos y externos disponibles desde el
primer tick ni todos los casos límite. L2 sigue parcialmente abierto.

**Por qué no A2.** A2 es la línea estratégica, pero sigue bloqueado por una decisión del dueño sobre el fixture del
cañón y no tiene umbral de aceptación acordado. L2 es acotado, tiene criterio claro y no reabre R4/L40/golem.

**Alcance.** Un fixture con al menos un árbol interno válido y uno externo; observar selección inicial, consumo del
interno y salto al externo solo cuando corresponda. Revisar `VillagerLumberjackGoal.buscarArbol`/`descubrirTrabajo`
sin cambiar la navegación compartida ni la geometría de la arboleda.

**No-goals.** No tocar A2, crecimiento/abono, R4, golem ni el almacén.

**Criterio de aceptación.** (1) con árbol interno y externo, elige el interno; (2) tras agotarlo, elige el externo;
(3) no elige un externo mientras haya interno válido; (4) registra la transición con coordenadas; (5) el ciclo
tala-replanta-deposita sigue funcionando.

**Validación.** `compileJava`, `test --rerun-tasks`, `lint_aldea --strict`, `git diff --check`; runtime L40 y, si se
toca navegación compartida, R4 S5.

### Tareas que siguen abiertas pero no son la siguiente

- **A2**: navegación propia y asedio amurallado; bloqueado por decisión de fixture/umbral.
- **L2**: prioridad completa interior/exterior; L40 solo prueba la rama interior posterior al depósito.
- **Crecimiento/abono completo**: L11 observó crecimiento, pero no se afirma sostenibilidad ni todas las plazas.
- **B1/B2/B3**: siguen diferidas por falta de dato del jugador.

## TERRAIN CLEARANCE — PASS (22-oct-2026)

**Causa:** `VillageGenerator.nivelar` recortaba de abajo hacia arriba y **cortaba en el primer bloque que no era
terreno natural**. Un hueco de aire bajo un resto flotante, o una estructura apoyada en la cota, detenía el recorrido
antes de llegar al resto de arriba. El resultado eran parches de tierra suspendidos sobre zonas abiertas o sobre
tejados.

**Corrección:** el recorte recorre ahora **de arriba hacia abajo** desde `g-1` (`sueloNatural`) hasta `baseY`:
- el aire se salta (`continue`);
- el terreno natural se retira aunque haya huecos debajo;
- al encontrar una estructura o un árbol (`!esTerrenoRecortable`) se detiene para no perforar lo construido.

Así se limpia lo que flota **por encima** de una estructura sin tocar la estructura, y se cubren todas las columnas
del footprint, no solo las huellas de edificios.

**Footprint y límites:** `LEVEL_RADIUS = FENCE_RADIUS + 2 = 64` (129×129 columnas). El barrido de `despejarVolumen`
usa una ventana superior de 60 bloques; el scan de aceptación cubrió `y = cota+2 .. cota+60`. El talud exterior
(`SLOPE_WIDTH=10`, hasta radio 74) conserva su política; el marcador de control se puso a radio 75.

**Evidencia:** `terrain4-20261022-2240`, A5SMOKE2 nueva, semilla 9201, centro `(80,97,0)`, cota 97. Se generó la
aldea por `VillageManager.preGenerate`, se inyectaron 4 parches de tierra en el interior a `y=117` y uno fuera en
`x=155`; se ejecutó el `levelTerrain` de producción. Scan completo 129×129:
`residual=0`, `seededGone=true`, `outsideKept=true`, `y=99..157`.

**Límite:** el reto se inyectó **después** de la generación fresca y se volvió a ejecutar el pase de nivelación de
producción; no se sembró terreno antes del primer `generate`. Es una validación del pase de limpieza, no una matriz
de todos los relieves posibles.

**Regresiones:** golem fresh `golem-postterrain-20261022-2300` PASS (`(83,97,5)`, 200 ticks); L40
`l40-postterrain-20261022-2310` PASS (dos ciclos); R4 S5 `r4-postterrain-20261022-2320` PASS (8/8, salvada tick 4241,
200 ticks). L2 quedó PASS el 23-oct-2026; la siguiente tarea viva es **A2**, bloqueada por la decisión de umbral del dueño.

## GOLEM MOVEMENT — NOT REPRODUCED / PARTIAL (22-oct-2026)

**Estado anterior conservado:** el spawn inicial sigue PASS (`golem-postterrain-20261022-2300`, `(83,97,5)`,
collision-free, support, 200 ticks).

**Defecto reportado:** el golem se asfixia al pasar junto a un pilar del kiosco. No se reprodujo en las pruebas
controladas de movimiento:
- `golemnav1-20261022-2340`: objetivo al centro; se detuvo en `(81,68,2)`, salud 100, sin intersección.
- `golemnav2-20261022-2350`: objetivo al este del kiosco; rodeó por `(84,67,2)` y `(85,67,-5)`, salud 100, sin
  intersección.
- `golemlower1-20261022-2320`: golem creado a `y=76` y `bajarElGolemAlSuelo` no lo bajó (permaneció a `y=73`), así
  que no ejerció el helper de bajada; PASS de movimiento pero **no válido para ese helper**.

**Hallazgo estático:** `VillageManager.casillaAlSueloDeLaPlaza` (usado al bajar un golem vanilla desde un tejado)
comprobaba pies/cabeza y empezaba en `kioscoRadio()+1`; con 1,4 de ancho podía solapar un poste de ±3. Se añadió la
comprobación de la AABB completa con `level.noCollision(...)`.

**Límite:** no hay evidencia runtime de que este helper se ejecute en el caso reportado; el defecto de asfixia en
movimiento sigue **NO REPRODUCIDO** y no se declara cerrado. La prueba de bajada debe corregirse para que el golem
esté realmente por encima de la cota y `bajarElGolemAlSuelo` lo mueva.

## P0 GOLEM DAMAGE — NOT REPRODUCED (23-oct-2026)

**Listener:** `LivingDamageEvent.Post` debug-only, filtered to the tracked golem UUID; active before movement. No
`[GOLEMDAMAGE]` callback was emitted in any run.

**Three routes, A5SMOKE seed 9101, 1,000+ ticks each:**
- `golemroute-east-20261023-0200`: spawn `(83,67,5)` → `(87,67,-5)`, health 100, no pillar intersection.
- `golemroute-center-20261023-0200`: spawn `(83,67,5)` → `(80,66,6)`, passed `(81,68,2)`, health 100, no intersection.
- `golemroute-west-20261023-0200`: spawn `(83,67,5)` → `(78,67,-5)`, passed `(79,68,0)`/`(79,68,-2)`, health 100, no
  intersection.

**Conclusion:** no actual damage, no `minecraft:in_wall`, no pillar AABB overlap. The reported suffocation remains
**OPEN / NOT REPRODUCED**; no production fix is justified. The prior lowering fixture remains invalid
(`y=76` before and after).

**Limitation:** all three used the same A5SMOKE seed/world; a different terrain/village layout could exercise a
different path.

## L2 CANUSE — selector observed; fixture blocked (23-oct-2026)

Run `l2trace-20261023-0440`, A5SMOKE seed 9101. Trace inserted in
`VillagerLumberjackGoal.canUse` (source line 187), compiled, and verified in the class bytecode.

```text
[L2] GOALS noAi=false alive=true goals=1
[L2] GOAL priority=0 class=VillagerLumberjackGoal running=false flags=[MOVE, LOOK]
[L2-CANUSE] n=1 uuid=e082f1fb-... prof=fletcher attack=true sleep=false dist=57.0
[L2-CANUSE] n=2 uuid=e082f1fb-... prof=none     attack=true ...
```

- The real selector **does invoke** `canUse()`; the prior missing-line inference was invalid.
- First rejection: `VillageManager.isVillageUnderAttack(...) == true`.
- The controlled fletcher also changes from profession `fletcher` to `none` immediately after registration.
- No `start()`, no tree selection, no navigation.

**L2 remains NOT ACCEPTED.** The next fixture must use a production-initialized fletcher in a village that is not
under attack and keep its profession stable; no production selection-policy change is justified.

## L2 — PASS (23-oct-2026)

Run `l2iso2-20261023-0600`, A5SMOKE2 seed 9201, player objective index `-1` set through the existing
`PlayerAuxiliaryCapabilityInterface` (empty `0..index` loop in `manageNearby`, so no arrival defense).

```text
[L2] OBJECTIVE_ISOLATION index=-1 target=(141,115,141) dist=153.6
[L2] GOALS noAi=false alive=true goals=1 prof=fletcher
[L2-CANUSE] n=1 uuid=db378b34-... prof=fletcher attack=false
[L2] START fase=TALAR target=BlockPos{x=36, y=98, z=-17}
[Village] El lenador: talo 5 tronco(s) ...
```

- UUID stable: `db378b34-4f4b-47a8-8205-fdf8e5b92894`
- Attack false throughout the decision.
- Candidate roots both valid: interior `(36,98,-38)`, exterior `(145,98,0)`.
- Selected target `(36,98,-17)` is another **interior** grove tree (the fixture has all 20 grove plots planted),
  so the named `(36,-38)` tree was not the one selected.
- The real selector, not a manual target, made the decision.

**L2 PASS** for the interior-over-exterior policy in this fixture. Limitation: the exact named interior tree was not
selected because the grove contains multiple valid interior candidates.

## GOLEM A5SMOKE2 controlled target — INCONCLUSIVE (23-oct-2026)

A5SMOKE2 (seed 9201, center `(80,97,0)`, kiosk pillars `(77|83,98..101,-3|3)`) was designated as a controlled
reproduction target, not the recovered original graphical incident.

Three production-spawn golem routes, damage listener active, 1,041 ticks each:

- center: `(83,97,5)` → `(81,98,2)`, health 100, no damage, no pillar AABB intersection.
- east: `(83,97,5)` → `(84,97,-2)`, health 100, no damage, no intersection.
- west: `(83,97,5)` → `(77,97,-6)`, health 100, no damage, no intersection.

Result: **DIAGNOSTIC INCONCLUSIVE**. The controlled target did not reproduce suffocation; the original player route
and the exact failure conditions remain unknown. No production fix was made.

## NEXT — A2 walled-raid acceptance needs an owner decision (23-oct-2026)

**Selected task:** A2 custom-navigation walled-raid acceptance.

**Why:** A1-CLIENT, L1, L2, terrain, R4 S5 and L40 are accepted or have accepted baselines. A2 is the only
remaining strategic line in the documented queue. The golem suffocation investigation is deferred, not fixed.

**Blocker:** `docs/autonomous-village/A2-WALLED-RAID-HANDOFF.md` records that A2 has **no agreed acceptance
threshold** and the fixture still needs an owner decision. The terrain option 2 is implemented, but the acceptance
criterion is not.

**Decision needed before implementation:**

1. Numeric acceptance threshold for the raid (for example 6/8, 7/8 or 8/8 entrants), and whether one run or
   multiple seeds are required.
2. Whether the current prepared-terrain fixture at `661,71,533` is accepted, or the wall must be redefined as a
   stepped barrier.
3. Whether the "Edit 5" instrumented run is the required acceptance vehicle.

No A2 production or harness code was changed in this pass. Until the owner chooses these, A2 remains **BLOCKED —
OWNER DECISION REQUIRED**.

### Owner decisions applied — A2 acceptance attempt blocked (23-oct-2026)

Owner decisions recorded: threshold **8/8 in three distinct-seed runs**; fixture **prepared terrain at
`661,71,533`**; vehicle **Edit 5 instrumented run**.

First attempted seed `12345` (`run=a2n-20261023-120319`, `-NavCustom true`, `pared=1`, phases 3000/6600) did not
reach the harness:
- `srv.log` ends at world-generation `Preparing spawn area: 51%`;
- no `[A2N]`, `CANDIDATO`, `SPAWN`, `PARED`, `FASE`, `RESUMEN` or `VEREDICTO` runtime lines;
- launcher killed the isolated server at the 25-minute limit;
- `srv.log.err` only reports Gradle `runServer` exit -1.

The second seed was cancelled before launch because the first did not produce a valid acceptance run. No raider
counts exist; no A2 PASS is claimed.

**Status:** **BLOCKED — TECHNICAL PREREQUISITE MISSING** (fixture/harness never initialized in the bounded run).
Next step: diagnose the world-generation stall or use a pre-generated disposable A2 fixture; do not change the
navigation/AI/threshold.

### A2 blocker root cause — harness excluded by build (23-oct-2026)

The failed run `a2n-20261023-120319` did **not** stall in world generation. Its log confirms:

```text
[12:04:45] Preparing spawn area: 51%
[12:04:45] Time elapsed: 4285 ms
[12:04:45] Done (8.314s)! For help, type "help"
```

The arena then saved periodically (`Gathered mod list ...` every 5 min) until the 25-minute launcher limit. No
`[A2N]` marker ever appeared.

**Cause:** `build.gradle` excludes `com/chipoodle/devilrpg/debug/**` unless
`-Pa2nDiagnostics=true`; `build/a2n/lanzar-a2n.ps1` invokes `runServer` without that property. The copied
`A2NavHarness.java` was therefore not compiled into the runtime classpath and its `@EventBusSubscriber` never loaded.

**Smallest next action:** add `-Pa2nDiagnostics=true` to the launcher's `runServer` invocation, then run one short
preflight expecting `[A2N] ARRANCADO modo=raid`. Do not start the 3-seed campaign until that marker and the
`CANDIDATO/FIXTURE` lines appear. This is a launcher/test-configuration correction, not production navigation.

### A2 launcher preflight — harness loads, fixture center deviates (23-oct-2026)

Launcher fix: added `-Pa2nDiagnostics=true` to `build/a2n/lanzar-a2n.ps1` runServer argument list.

Preflight `a2n-20261023-132018`, seed 12345, 5 minutes:
- `[A2N] ARRANCADO modo=raid` at 13:22:07.
- `FIXTURE RAID listo=true`, wall 400/400 columns, 1200 cells, ring complete.
- `SPAWN TOTAL: intentos=8 ok=8 fallos=0`.
- No entrants by phase A (`entraronPerimetroEver=0`); preflight stopped at phase B.

**Geometry deviation:** candidate `661,71,533` was invalid (`fallosAltura=248`, `fallosContinuidad=219`,
`fallosFundacion=33`). The harness selected candidate 14 at **`629,71,597`**, built the wall there, and spawned 8/8
around that center. This is not the owner-agreed `(661,71,533)` geometry.

No three-seed acceptance campaign was started. Owner decision needed: accept `629,71,597` as the prepared fixture
center, or require the 661 terrain preparation to be corrected so a 661-centered candidate validates.

### A2 center restoration — blocker confirmed (23-oct-2026)

The candidate loop evaluates the **virgin** ring first (`A2NavHarness` ~1812-1832). It only requires
`sinSuelo==0 && fallosFundacion==0`; height/continuity are deferred to preparation. Candidate
`661,71,533` has `fallosFundacion=33`, so it is rejected at selection time.

The terrain-preparation block (solid reference at `Y=70`, clear `Y=71..103`) runs **after** a center is already
chosen (~1852 onward). Therefore it cannot repair the 661 foundation, and the next candidate with zero foundation
failures (`629,71,597`) wins.

**Smallest correction:** apply the same bounded preparation at `661,71,533` before candidate evaluation, or force
that center and validate it. Both are changes inside the protected `A2NavHarness.java`, which this task forbids.
No pre-generated fixture for the 661 ring exists in the current artifacts.

**Status:** BLOCKED — TECHNICAL PREREQUISITE MISSING. No harness or launcher change was made in this pass.

## A2 successor fix — local A* canonical state (25-oct-2026)

**Observed state:** branch `feature/autonomous-village`, HEAD `97220be5453b57387002b9bc91935807cc665274`, staged 0. The worktree still contains
pre-existing uncommitted work, untracked `NavExecutor.java`, `.a2-backups/`, and diagnostic edits. No commit,
staging, reset, clean, or push was performed.

**Root cause (CONFIRMED):** local A* validated the effective standing cell but keyed `bestG`, `piesDe`,
`cameFrom`, `transitionTo`, the open queue, goal comparison, and route reconstruction by the raw candidate.
For `candidate=(301,118,300)`, `classify` returned `JUMP_UP` and `piesHasta=120`; zero delta relabeled the
edge `WALK`, so the route contained the invalid `WALK@(301,118,300)` instead of the validated
`WALK@(301,120,300)`.

**Fix:** `NavSuccessorPolicy` defines the pure canonical rule; `NavPlanner.sucesorCanonico` applies it using
the classified kind. Local A* now uses that successor for every graph-keyed structure; global successor handling
uses the same helper. No validation, support, protected-cell, safe-drop, or movement rule was weakened.

**Evidence:** focused `policyTest` / `NAV_PLANNER_STATE_TEST` = OK, including zero-delta JUMP_UP, normal
JUMP_UP, STEP_UP, WALK, DROP, BREAK, BUILD, and canonical-key collision. Isolated DROP fixture
`drop11-20261025-0020` = PASS: first node `WALK@301,120` with fits/support true; original DROP at
`305,117` executed; landed feet Y=117; route advanced past DROP; no route replacement. This is an isolated
movement result, not A2 acceptance.

**Next gate:** full A2 remains BLOCKED. The protected harness still selects a non-agreed center because
`661,71,533` fails foundation validation before preparation; fixing that requires changing protected
`A2NavHarness.java`. Do not run full A2 or alter the harness until the owner approves a center/prerequisite.

**Commands:** `.\gradlew.bat compileJava -Pa2nDiagnostics=true -Pa4nLab=true`;
`.\gradlew.bat policyTest`; `python tools\lint_aldea.py --strict`; `git diff --check`.

**Protected hashes:** `A2NavHarness.java =
11980C98668EF5D13E4CF3A276C9782392A9187F51B3EF63D7FC88141FA4C940`; `.partial =
2C3EBA9ED6E936B595E26650B796A624838B5CB4FF6CFD7B1804BE7A13552127`.

## A2 center foundation diagnosis (25-oct-2026)

The protected harness rejects `661,71,533` before preparation in `evaluarRecinto`: for every ring column it
scans from Y=69 downward for the first non-air block; if that block has an empty collision shape (water/lava/plant),
`fallosFundacion++`. Candidate 661 reports `sinSuelo=0` and `fallosFundacion=33`; the selection predicate is
`sinSuelo==0 && fallosFundacion==0`. Altura/continuidad are deliberately deferred because preparation at
Y=70/71..103 can fix them. Preparation can fill only AIR, so it cannot remove a first non-air fluid/plant.

The fixed candidate grid then selects the first valid candidate `629,71,597` (offset -32,+64). Exact 33 column
coordinates and block types are not emitted by the protected harness; no per-column evidence exists.

Safe resolution status: **UNRESOLVED / OWNER DECISION REQUIRED.** No protected-harness change, alternate approved
fixture, or approved terrain generator for the 661 ring is available. Options: (A) produce an approved prepared
world for 661 (requires a generator/tool and owner approval); (B) approve 629,71,597 as the test center (changes the
owner-agreed geometry); (C) authorize a protected-harness pre-selection preparation/evaluation change. Do not
silently skip foundation validation. Full A2 remains blocked until one option is approved.

## 661-centered A2 fixture tool — DESIGN ONLY (25-oct-2026)

`-MundoPreparado` copies a source world containing `level.dat` to `<run>/game/a2n` and sets
`level-name=a2n`. The protected harness still evaluates the copied terrain with `evaluarRecinto`; it does not
identity-check the source or repair it. A prepared world plus manifest is therefore feasible in principle without
harness changes.

Proposed independent tool (not implemented): operate only on a copied disposable world, never `run\saves`.
Fixed center `661,71,533`, ring r=62, scan Y=69 downward per column. Report every first non-air block with empty
collision (water/lava/plant), then replace only those exact cells with a solid block; preserve all other cells,
biomes, structures, and the wall footprint. Emit a before/after manifest: coordinates, old state, new state, changed
count, source/destination hashes. Abort unless source identity, center, ring count, and expected failure count
match the approved manifest. After repair, the unchanged harness must accept candidate idx=1 (661) with
`sinSuelo=0` and `fallosFundacion=0`. Discard the copied world after the run.

Status: DESIGN PENDING / OWNER APPROVAL REQUIRED. No tool, world write, or Minecraft launch was performed. Exact 33
columns/types are not logged by existing artifacts; a report-only scan is the first implementation step. Full A2
remains BLOCKED.

## A2 661 scanner Phase 1 — BLOCKED INPUT (25-oct-2026)

Scanner was not implemented or run. Phase 1 requires an explicitly selected source world; none is positively
identified. The many `build/a2n/*/game/a2n` worlds are historical runs, not an approved clean 661 fixture; the
629,71,597 arena is explicitly excluded. The environment also has no existing Anvil/NBT library
(`nbtlib`/`anvil`/`amulet` absent), so read-only chunk inspection would require new approved tooling.

No file, world, or report was created. Exact 33 coords/types remain unknown. Owner must provide an absolute clean
source-world path or approve generation of a disposable 661 source and a read-only scanner implementation.

## A2 source recovery feasibility (25-oct-2026)

Evidence: `build/a2n/lanzar-a2n.ps1` defaults `='12345'`; `docs/aldea-estados.md` records the measured
fixture world as seed **12345**, center **(661,71,533)**, cota **71**, well axis **(628,71,562)**. The protected
harness hardcodes center `661,71,533`, ring radius 62, wall height 3, and deterministic preparation at Y=70 plus
clear 71..103.

Conclusion: **SOURCE REPRODUCIBLE WITH OWNER APPROVAL.** A fresh disposable world can be generated from seed 12345;
no historical world needs to be trusted or parsed. However, the fresh world will still expose the same 33
non-collidable foundation columns because preparation runs after candidate selection and cannot fill them. A
read-only scanner/writer or an approved harness pre-selection repair is still required.

Scanner feasibility: a Python-stdlib Anvil/NBT reader is technically possible (region header + zlib + NBT + palette),
but exact Minecraft collision semantics for all modded blocks are not available offline. A report-only scanner can
report block states; exact predicate equivalence needs a validated collision map or a Java test harness. No scanner
or world was created. Owner must approve fresh-world generation and the repair/scanning approach.

## A2 fresh seed-12345 source + read-only framework scan (25-oct-2026)

Fresh disposable world: `build/a2n/fixture-source-20261025-0115/game/a2n`. Created by a plain `runServer` with no
`-Pa2nDiagnostics`; no `[A2N]` marker, no harness, no A2. Seed 12345, center 661,71,533, RCON-forceloaded 110
chunks, saved and stopped. `level.dat` SHA-256 `af0301d75023fda9d693204bc6682698e276271693d83af0a73458e1fb833710`.

Stdlib scanner `scan_foundation.py` read all 400 ring columns, Y=69..5, zero missing chunks. First non-air states:
94 `minecraft:water` (LIKELY_EMPTY), 306 UNKNOWN-collision states (stone/gravel/clay/granite/andesite/leaves...).
This is **not** the historical aggregate 33 and cannot be claimed equivalent without runtime collision validation.
No blocks repaired. Report: `build/a2n/fixture-source-20261025-0115/report/foundation-manifest.{json,txt}`.

Status: SCANNER REPORT READY — OWNER REVIEW REQUIRED; A2 remains BLOCKED. Source terrain differs from the
historical 661 fixture (94 likely-empty vs 33), so this world is not yet an approved replacement source.

## A2 33-vs-94 discrepancy audit (25-oct-2026)

Historical run `a2n-20261023-132018` and fresh source `fixture-source-20261025-0115` use the same recorded
Minecraft 1.21.1, NeoForge 21.1.249, seed 12345, `level-type=minecraft:normal`, generator settings `{}`, center
661,71,533, radius 62. Historical count: 33 foundation failures. Fresh stdlib scan: 400/400 columns, 94
first-non-air water states (LIKELY_EMPTY), 306 UNKNOWN-collision states. Historical per-column manifest is absent;
the scanner cannot reproduce runtime `getCollisionShape` for all states. The discrepancy is **UNRESOLVED**; do
not treat the fresh world as equivalent. Possible contributors not proven: different worldgen code/compiled state
between runs, partial chunk generation at historical candidate evaluation, or scanner collision approximation.
Owner must choose recovery of a verified historical source, a new baseline (explicit approval), or remain blocked.

## A2 historical artifact recovery (25-oct-2026)

`a2n-20261023-132018` retains only logs/config/world; the compiled `A2NavHarness.class` used by the run is
gone. Current `build/classes` has no debug harness; `VillageGenerator.class` and `VillageManager.class` are
dated 25-oct, so they are not the 23-oct binaries. Gradle's `previous-compilation-data.bin` is current, not a
historical content-addressable record. The log contains candidate aggregates only; no per-column `FUNDACION`
manifest exists. Therefore the 33-vs-94 cause cannot be attributed to compiled worldgen vs source state from
artifacts. Historical code is **not recoverable**; per-column evidence **unavailable**. Next experiment must
regenerate with a pinned source/build hash or run a runtime per-column probe, subject to owner approval.

## A2 new-baseline arena plan (25-oct-2026)

Historical source recovery is closed for baseline purposes. New baseline remains fixed at center **661,71,533**,
seed **12345**, Minecraft **1.21.1**, NeoForge **21.1.249**, ring radius **62**, wall height **3**, cota **71**.

Source: `build/a2n/fixture-source-20261025-0115/game/a2n` (never overwrite). Working copy: unique
`build/a2n/arena-new-<run>/game/a2n` via non-destructive copy. No historical world is used.

Planning findings: scanner parsed 400/400 ring columns, 94 `LIKELY_EMPTY` water, 306 UNKNOWN collision. Proposed
mutations are only the 94 first-non-air water cells (same Y, replace with solid support); this is a **candidate
subset**, not final: runtime `getCollisionShape` must confirm every column and detect any empty-collision state
outside water. No block repair has been applied.

Runtime probe requirement (not authorized yet): disposable working copy + a debug-only A5/standalone probe using
the real server classes, scanning the exact ring and printing every column's first non-air block, collision
empty/non-empty, and a proposed mutation. It must not start A2. Then a separate repair pass (also not authorized)
applies only manifest rows, writes before/after manifest, and re-probes idempotently. The unchanged harness cannot
validate arena readiness without launching its A2 preparation; independent probe is required first. A2 remains
BLOCKED. Owner must authorize the runtime probe and repair policy.
## A2 runtime probe — BLOCKED by chunk timing (25-oct-2026)

Probe class `ArenaFoundationProbe` ran once in disposable copy
`build/a2n/arena-probe-20261025-0130/game/a2n` under plain runServer with `-Da2n.foundationProbe=true`.
`[A2N] ARRANCADO modo=none` only; no fixture/candidate/A2. The first server tick saw all 400 ring chunks
unloaded (`getChunkNow` null), so the probe failed closed: empty=0, non_empty=0, no_non_air=0, missing_chunks=400.
No block states were read or changed. The server then saved the working copy on halt; source remained untouched.

Cause: probe scanned at the first ServerTickEvent.Post before chunk loading/forceload. Fix requires a bounded
forceload/wait phase inside the probe, or a launcher/command that loads the ring chunks before scanning. This
would need a second Minecraft launch, which the phase limit forbids. No retry was made.
Report: `build/a2n/arena-probe-20261025-0130/report/runtime-foundation.json`.

Status: **BLOCKED — SAFE RUNTIME PROBE NOT POSSIBLE** within this one-launch phase. A2 remains BLOCKED.
## A2 runtime probe chunk-loading fix (25-oct-2026)

`ArenaFoundationProbe` now derives the exact unique chunk set for the 400-column ring, calls
`ServerLevel.setChunkForced(x,z,true)` for each chunk, waits across `ServerTickEvent.Post` until every
`getChunkNow` is non-null (timeout 200 ticks), and only then scans. It rechecks chunk availability during the scan,
writes JSON/text outside the world, releases all forced tickets, and calls `server.halt(false)`. It has no block
set/break calls and no A2 candidate/arena/raid calls. If a required chunk is missing, `setChunkForced` can generate
it in the disposable working copy; the source world is never opened. The previous launch consumed the one-run
authorization; a second launch requires owner approval.

Status: PROBE FIX IMPLEMENTED — SECOND LAUNCH AUTHORIZATION REQUIRED. A2 remains BLOCKED.
## A2 runtime probe second launch — blocked by harness mode none (25-oct-2026)

Launch `arena-probe2-20261025-0525` started once. Probe logged LOADING (32 chunks), but `A2NavHarness` with
`a2n.mode=none` executed its terminal `cerrar("SIN EJECUTAR", ...)` path immediately and shut the server down
before any probe tick. No `runtime-foundation.json/txt` was produced. Source fixture hash unchanged
`af0301...3710`; working-copy hash changed only from server save. No A2 raid/candidate/arena happened, but the
harness none path is itself a launch-method blocker.

Required next method: run the probe in a build/launch that excludes `A2NavHarness` entirely (separate diagnostic
source set/task), not merely `a2n.mode=none`. One launch consumed; no retry. A2 remains BLOCKED.
## A2 probe-only launch path (25-oct-2026)

`build.gradle` now supports `-Pa2nProbeOnly=true` together with `-Pa2nDiagnostics=true`. This excludes
`A2NavHarness.java`, `A2EntryProbe.java`, `AsedioRunA2.java`, and `A2ProdNavProbe.java` from `main`, while keeping
`ArenaFoundationProbe` compiled. Verified classpath: probe present; both A2 harness and entry probe absent.
Without the property, normal diagnostic build still includes `A2NavHarness` (verified). Normal non-diagnostic builds
are unchanged because debug is still excluded unless `a2nDiagnostics=true`.

Intended probe command (not executed):
`.\gradlew.bat runServer --console=plain -Pa2nDiagnostics=true -Pa2nProbeOnly=true --init-script <isolated-init>`
with only `-Da2n.foundationProbe=true -Da2n.probe.out=<absolute report path> -Djdk.net.unixdomain.tmpdir=C:\Windows\Temp`.
No `a2n.mode`. A2 remains BLOCKED; a new owner launch authorization is required.
## A2 isolated runtime probe — PASS (25-oct-2026)

Run `arena-probe3-20261025-0538` used `-Pa2nDiagnostics=true -Pa2nProbeOnly=true`; `A2NavHarness.class` and
A2 entry classes were absent. Probe loaded 32 chunks in 32 ticks, measured all 400 ring columns: **33
collision-empty** (all `minecraft:water` at Y=62), **367 collision-nonempty**, 0 no-non-air, 0 missing. Cleanup
reported `released=32/32`; server stopped and process exited. Source fixture hash unchanged
`af0301...3710`; working-copy hash changed from save. No A2/raid/candidate/arena/spawn/repair.

This runtime result supersedes the offline 94-water estimate; it does not by itself authorize repairs or A2.
Report: `build/a2n/arena-probe3-20261025-0538/report/runtime-foundation.{json,txt}`.
## A2 acceptance attempt — PARTIAL, navigation blocker (25-oct-2026)

New pre-selection bootstrap `A2FoundationBootstrap` repaired the observed water columns with STONE (same material as
harness `solido`), gated to `a2n.mode=raid` plus `DEVILRPG_FOUNDATION_REPAIR=1`. Run
`a2n-20261025-054523` used a fresh copy of the verified source.

Foundation/fixture evidence: `CANDIDATO idx=1 centro=661,71,533 ... fallosFundacion=0 valido=true`;
`CENTRO ELEGIDO idx=1`; `PARED total ... presentes=1200 ... anilloCompleto=true`; `SPAWN TOTAL intentos=8 ok=8`.
Acceptance blocker: every sampled attacker remained `estado=PLANNING`, `entraronPerimetroEver=0`,
`dentroParedAhora=0`; `VEREDICTO A/B: SIN UMBRAL CONTRATADO`; no wall contact, breach, or horizontal entry.
`navCustomEfectivo=false`. The one authorized attempt is consumed; no retry or repair.

Status: A2 PARTIALLY PASSED — SPECIFIC BLOCKER (navigation never leaves PLANNING). A2 remains unaccepted.
## A2 ACCEPTED — custom navigation run (25-oct-2026)

Run `a2n-20261025-055802` used the actual A2 harness, fresh source copy, foundation bootstrap, and existing
`-NavCustom true`. `NAVCUSTOM`/FIN report `navCustomEfectivo=true`. Foundation and fixture:
`CANDIDATO idx=1 fallosFundacion=0 valido=true`, `CENTRO ELEGIDO idx=1`, wall 1200 cells, `SPAWN TOTAL 8/8`.

Navigation evidence: planners left PLANNING, produced routes, and executed TRAVERSING/RECOVERING transitions.
Wall contact/breach: `[Siege] ... pica Block{minecraft:stone} ...` at ring coordinates; final `PARED total
presentes=1191 faltantes=9 columnasNoSolidas=3` (1200->1191). Horizontal entry: `entraronPerimetroEver=4`,
`dentroParedAhora=2`; final idx1 `697,63,566 dist=49.87` and idx3 `647,89,546 dist=19.55`, both inside radius 62.
One attacker disappeared (`DESAPARECIO idx=2`), no deaths; `fallosDeInstrumento=0`.

Result is a real breach and horizontal entry, not a radial-only or vertical-bypass artifact. No numeric threshold
exists in the repo; measured result is 4/8 perimeter entries and 2/8 inside. This supersedes the earlier partial
status. A2 ACCEPTED for the qualitative walled-raid criteria; no further acceptance run.
## A2 navigation correction — acceptance still fails (25-oct-2026)

Added opt-in global-search budget in `NavExecutor` (`devilrpg.nav.globalBudget` / `DEVILRPG_NAV_GLOBAL_BUDGET`,
default 8000, max 32000). Acceptance run `a2n-20261025-212406` used 16000 global and 3000 local, NavCustom=true,
foundation bootstrap, 8/8 spawn. Result remained **4/8 entered, 2/8 inside**, 1 disappeared. idx4, idx7, idx8 still
failed no-route planning (global budget exhausted); idx7/8 ended farther out. idx6 still failed the same DROP
contiguity (`vivo dz=2`, `plan dz=1`, `cercaOrigen=false`) and never crossed. Budget increase did not fix the
demo; no further run. A2 remains unaccepted.
## A2 waypoint/DROP corrections — partial result (25-oct-2026)

Changes in `NavExecutor`: keep scanning global route nodes after an out-of-window node; before evaluating a DROP,
move to its planned origin when the live position is not origin-compatible. Run `a2n-20261025-214955` used
NavCustom=true, foundation bootstrap, default global/local budgets. Result: 4/8 entered, **3/8 inside** (idx6 now
inside at `654,74,482`, dist51.06; wall remained 1200/1200), 1 disappeared. idx4/7/8 still no-route:
idx4 no valid local waypoint, idx7/8 `presupuesto agotado sin progreso (6/6)`. 8/8 not met; no three-run series.
Focused `NAV_PLANNER_STATE_TEST: OK`; no executor-level DROP regression test seam added. A2 NOT ACCEPTED.

## Village profession recovery (26-oct-2026)

Added one-shot maintenance recovery in `VillageManager`: when a poll is skipped for `under_attack` or
`enemies_inside`, the objective is marked pending; on the next tick after both clear, normal `tickVillageLife`
runs once before the next 200-tick poll. It reuses the idempotent `asegurarGoal...` methods and does not force
goals, reset cooldowns, or clear routes. Architecture/invariants: `docs/autonomous-village/PROFESSION-RECOVERY.md`.
Compilation passes; runtime/under-attack validation is still pending.

## A5 recovery scenario (26-oct-2026)

Added `-Da5.scenario=recovery` in `A5TestController`: baseline two ordinary profession villagers, start a real
siege via `VillageManager.start`, require actual under_attack, wait for natural clear, then require same-UUID
profession execution and no duplicate profession goals. Bounded 400 ticks start, 4000 clear, 800 resumption.
No terminal cleanup. Documented in PROFESSION-RECOVERY.md. Compile passes; runtime acceptance not yet run.

## A5 automation repair (26-oct-2026)

Restored `build/a5-closure/run-a5.ps1`: absolute a5.output, recursive validated result discovery, PASS-only
exit 0, malformed/missing/non-PASS nonzero, headless options (pauseOnLostFocus/onboardAccessibility/skip warning/
tutorial), and `-SelfTest`. Self-test PASS. Focus independence is configured but not graphically re-verified.

## Automation repair verified (26-oct-2026)

`run-a5.ps1` now resolves absolute output, recursively discovers and validates results, accepts only PASS as
launcher success, enforces headless options, and terminates all matching test clients. Self-test PASS; short
`smoke` run PASS with `self-exit clean=True exit=0`. Graphical focus-switch independence was not tested.

## A5 focus smoke (26-oct-2026)

`focus-smoke-20261026-02` (scenario `smoke`, non-siege) FOCUS_UNVERIFIED with Chrome foreground before/after; client log
preserved; shutdown clean; exit 0. Exact Minecraft→Chrome transition not captured (Chrome was already foreground).

## A5 final focus test (26-oct-2026)

`focus-smoke-20261026-03` PASS/clean/exit0 but FOCUS_UNVERIFIED: Windows rejected programmatic foreground
transfer to Minecraft; no Minecraft→Chrome transition observed.

## A5 focus handoff repair (26-oct-2026)

FOCUS_UNVERIFIED: AttachThreadInput + SetForegroundWindow returned success but foreground remained Chrome; no
Minecraft->Chrome transition. Smoke-only; no siege; no source/launcher change retained.

## Harness focus regression root cause (26-oct-2026)

Historical A5 automation used pauseOnLostFocus:false + CloseMainWindow; it never automated foreground transfer.
Current SetForegroundWindow/AttachThreadInput failed due Windows foreground lock. No historical handoff to restore;
smoke with Chrome foreground passes. Automatic Minecraft->Chrome remains unverified.

## Guard equipment on existing founders (27-oct-2026)

`VillageGenerator.spawnGuardiasFundadores` now calls
`VillagerGuardGoal.equiparDesdeAlmacen` for an **already-loaded** founding
guard, matching the new-spawn path. This closes the case where a village loaded
with unarmed founders and a stocked warehouse: the old branch repaired marks/goal
and returned without ever attempting equipment. It does not create weapons when
the warehouse is empty.

## Existing founder equipment acceptance (27-oct-2026)

Runtime PASS: same founder UUID, existing-founder branch executed, wooden sword + shield + leather armor equipped,
warehouse stock 173->167, guardGoals=1, samePuesto=1, clean shutdown/exit0. Debug scenario `founder-equip` added.

## Fresh-world A2 raid acceptance (27-oct-2026)

PASS: A5WALL S5 real wave 8/8, custom navigation, 13 wall breach records, 8 raiders killed, guards active, no
villagers removed, village saved, clean shutdown. No attacker entered (`dentro=true` 0); interior pressure remains
unproven.

## A2 interior pressure acceptance (27-oct-2026)

INTERIOR_PRESSURE_CONFIRMED: fresh S5 run, 8/8 wave, breach, all 8 killed at interior positions x=60-69/z=-3..-11
inside radius62. `dentro` flag reported false at inside positions; treat flag as stale. No code changes.

## Player-proximity autonomy fix (27-oct-2026)

PASS near-village grace: defense registered but wave not spawned; 11 profession goals running at tick287 with
`waveAttack=false`. `isVillageUnderAttack` now requires waveSpawned; maintenance gate uses it. Real wave still
suppresses work. Scenario `profession-near`.

## Player-proximity autonomy closure (27-oct-2026)

PASS far->near->far: 4/4 tracked villagers moved in all phases, near goals active, waveAttack=false, duplicates=0.
Wave suppression/recovery reused prior accepted evidence. Scenario `profession-far-near`.

## Multi-village isolation (27-oct-2026)

PASS: A at 80,97,0 stayed attack-false and its fletcher moved 86 blocks while B at 430,106,0 had a real 10-raider
wave. Save/reload not validated (PARTIAL).

## Save/reload persistence acceptance (27-oct-2026)

PASS: second process on `multi-village-20261027-02` save retained centers 80/430, tracked UUIDs/professions,
4+4 founder guards, zero duplicates; fletcher moved 4.57 blocks after reload. Defenses transient by design.

## Distant-village maintenance (27-oct-2026)

Implemented 200-tick maintenance for loaded peaceful villages beyond player proximity; duplicate near tick avoided.
Compile/static pass. Runtime reload validation PARTIAL/FAIL: B guards/UUID missing in reused save; retest fresh.

## Distant-village autonomy clean reload (27-oct-2026)

PASS: fresh two-village baseline (B farmer moved 10.51, playerDist350), separate process reload retained UUID/profession,
4 guards, attackB=false, post-reload movement 1.65, duplicates 0. Production distant maintenance path exercised.

## Profession progression (27-oct-2026)

PARTIAL: no XP path existed; added `premiarOficio` and farmer harvest award (xp>=1 guard). Bounded runs saw no
completed harvest, farmers stayed xp=1, so runtime unverified. Next: seed mature crops and test XP/persistence.

## Harvest XP deterministic test (27-oct-2026)

FAIL: 69 mature crops harvested, XP gain 0. Vanilla harvest path bypasses custom `segada`; event hook did not
award. Source incomplete; next: hook actual vanilla harvest or own harvesting.

## Harvest XP mixin acceptance (27-oct-2026)

PARTIAL (aggregate): vanilla HarvestFarmland destroyBlock redirect, 64 harvests -> 64 XP; reloaded farmer xp=25; single-crop/non-farmer not separate.
Single-crop/non-farmer not separate. Mixin registered in neoforge.mods.toml.

## Harvest XP final acceptance (27-oct-2026)

PASS: single mature wheat 1->2 XP, age0, no second award; aggregate 64/64; reload xp25; mixin packaged in jar.
Non-farmer code-path-proven; jar runtime launch not performed.

## Lumberjack XP audit (27-oct-2026)

PARTIAL: award added after successful LOG mutation in picarTronco; runtime fixture failed because fletcher chose
RECOGER_HARINA (arboleda not established). No production defect proven; needs established-arboleda fixture.

## Lumberjack XP runtime (27-oct-2026)

PARTIAL: established arboleda reached TALAR; production log 5 logs harvested, XP 1->6 (5 awards). Reload failed
because entity chunk unloaded. Farming unchanged.

## Lumberjack XP closure (27-oct-2026)

PASS: 5 logs -> 5 XP; separate-process reload same UUID/FLETCHER/xp6; farmer negative xp1->1; build/lint/diff pass.
No single-log isolated test; source per-log.

## Mining XP source audit (28-oct-2026)

BLOCKED runtime: per-block XP added in `picarYRecoger` after natural-block removal; A5WALL has no MASON. No
production runtime acceptance. Farming/lumber unchanged.

## Mining XP runtime (28-oct-2026)

PARTIAL: spawned MASON + warehouse pick, real miner goal xp1->47; reload xp47 same UUID. setBlock guard added
after runtime; single-block/negative not run.

## Mining XP closure (28-oct-2026)

PARTIAL: corrected failed setBlock guard to return false (retry, no XP); prior runtime xp1->47 and reload xp47.
Single-block/failed/non-miner still unverified.

## Mining XP final acceptance (28-oct-2026)

PARTIAL: non-miner runtime PASS (xp1->1, minerGoals0); prior 46/46 mining and reload 47; failed mutation
SOURCE-ONLY; isolated single-block not verified.

## Mining single-block acceptance (28-oct-2026)

PARTIAL: non-miner runtime PASS; aggregate 46/46 and reload 47; isolated single block blocked by goal geometry
(progress/next-step progression). Failed mutation source-only.

## Mining single-block acceptance (28-oct-2026)

PASS: deterministic step-1 stone target (117,95,-32) -> cobblestone mine piece; XP 1->2 exactly, 120 ticks no
repeat; MASON same UUID. Failed mutation source-only; aggregate/non-miner prior.

## Next gameplay gap audit (28-oct-2026)

Chosen gap: VillageDispatcherGoal/apuntarElRecado are unwired dead code; documented 19% recado abandonment.
Plan: bounded no-MOVE dispatcher, 3 pushes/budget, deterministic competing-WALK_TARGET fixture. No implementation yet.

## Bounded dispatcher (28-oct-2026)

PARTIAL: opt-in -Ddevilrpg.dispatcher=true wired recado ownership/idempotent registration; enabled run 76 harvests
vs control 69, pushes capped 1-3, duplicates0; miner single-block regression PASS. Missing explicit competing target,
bounded-failure, farming/lumber/proximity regressions. Default off.

## Dispatcher validation (28-oct-2026)

PARTIAL: competing recado retained/moved, pushes<=3; no work progress, no new pairs, farming/lumber/raid regressions NOT RUN.
Default off.

## Dispatcher gap closure (28-oct-2026)

FAIL opt-in: two new pairs enabled 52 vs control71 and enabled58 vs control68; default off/fallback intact.
Competing partial, mining PASS, farming/lumber/proximity/blocked NOT RUN.

## Custom nav decision (28-oct-2026)

Dispatcher opt-in rejected: pairs 71>52 and 68>58. New direction: VillageNavigationGoal owns MOVE only during a live
recado, bounded replans (3) and RECADO_PRESUPUESTO, frees on arrival; opt-in -Ddevilrpg.navigation=true. First runtime
showed 83 harvests but no [NAV] ownership events; acceptance not claimed.

## Custom nav implementation (28-oct-2026)

Implemented opt-in VillageNavigationGoal + docs; targeted run 83 harvests but no [NAV] ownership events, so runtime
acceptance not claimed. Stopped per policy. Dispatcher rejected/historical.

## Custom navigation contract (28-oct-2026)

Added stable recado ID + state machine (UNOWNED/OWNED/MOVING/RECOVERING/ARRIVED/RELEASED), cleanup and bounded recovery.
Runtime still lacks [NAV] ownership evidence; not accepted. Dispatcher remains rejected/default-off.

## Custom nav contract report (28-oct-2026)

PARTIAL: contract/state/id/cleanup implemented; runtime still no [NAV] ownership. No retry cycle; next activation
diagnostic then one end-to-end.

## Custom nav activation (28-oct-2026)

PARTIAL: fixed missing caminarHacia wiring and missing recado ID; controller START/ARRIVE proven. But 20 harvests
vs 63-83 fallback, recado churn. Keep opt-in; next coalesce recados/ownership.

## Two-level profession coordination (28-oct-2026)

Added VillageFarmCoordinator per-village assignment + VillagerFarmGoal consult;
canonical docs. SOURCE-ONLY; no runtime (forbidden this task). Pending three-farmer
allocation/reassignment/reload acceptance.

## Two-level profession coordination report (28-oct-2026)

SOURCE-ONLY: VillageFarmCoordinator + VillagerFarmGoal integration; no runtime. Three-farmer allocation/reassignment/
reload pending.

## Farm/nav coherence (28-oct-2026)

PARTIAL: stable recado ID and >8-block controller threshold; harvests 20->3->30 but fallback 63-83. Coordinator
source-only; 3-farmer/reassignment/reload unverified. Keep opt-in; next task identity.

## Task identity (28-oct-2026)

FAIL: farm task identity now stable (`farm:*`) but controller churn/ABANDON-REPLANS produced 0 harvests final. Stop
tuning; disable nav ownership for farming and redesign handoff/test.

## Farm nav containment (28-oct-2026)

PARTIAL: farm:* excluded from custom nav via VillageTaskNavigationPolicy + focused test; fallback preserved.
No runtime in this task; throughput/runtime pending.

## Farm nav containment report (28-oct-2026)

PARTIAL: farm:* excluded from custom nav, fallback preserved, policy test PASS; no runtime. Next containment runtime.

## Farm nav containment runtime (28-oct-2026)

PARTIAL: 0 farm NAV events, fallback 27 harvests/27 XP; below 63-83 baseline. No policy-rejection event. Containment
not accepted; no retry.

## Farm nav policy runtime (28-oct-2026)

PARTIAL: direct farm:-1 -> fallback logs; 0 farm NAV; 42 harvests (below 63-83); coordinator tag absent.
Next: fix why plot tag is not assigned. Farming nav remains disabled.

## Farm plot assignment (28-oct-2026)

PARTIAL: fixed absent-tag default 0 -> no write; farmers now farm:0/1/2; 68 harvests healthy but XP75 mismatch
harness FAIL. Next investigate XP/harvest discrepancy.

## Farm XP/harvest investigation (28-oct-2026)

ACCEPTED metric correction: net mature loss undercounted due growth; per-tick removals 67=XP67. No game XP defect.
Farm nav containment separate/unresolved.

## Navigation interference (28-oct-2026)

PARTIAL/no defect proven: latest 67/67; 0 farm NAV, 3 policy rejections. Nonfarm interference unverified; no
source change/runtime.

## Nonfarm nav A/B (28-oct-2026)

ACCEPTED experiment: control miner +46 XP, enabled 0. Custom nav prevents work-position arrival; speculative fixes
reverted. Keep opt-in/off; exclude mining tasks until handoff contract.

## Vision reconciliation (29-oct-2026)

Added canonical vision docs for custom brain, irregular terrain, enemy progression/fortress. Player powers: no
canonical pending list; existing names preserved. No production code changed. No next authorized task selected yet.

## Mining nav containment (29-oct-2026)

Policy extended: mine:* -> PROFESSION_FALLBACK; policy test PASS. No runtime in this step. Documentation vision audit added.

## Vision reconciliation (29-oct-2026)

PARTIAL: vision docs added for brain/terrain/enemy fortress; no future feature implemented. Mining work-position tasks
now excluded from custom nav; policy test PASS, runtime pending.

## Mining nav containment runtime (29-oct-2026)

ACCEPTED: mine:* -> PROFESSION_FALLBACK, 0 mining NAV, miner XP 1->47, clean shutdown. Label wording fixed after;
behavior unchanged.

## Task coordination audit (29-oct-2026)

PARTIAL/design: farm plot claims lack lease/release; unloaded-owner conflict blind spot; recado release unused.
Recommended next slice: farm-claim lease. No production changes/runtime.

## Farm ownership persistence (29-oct-2026)

PARTIAL: SavedData authoritative owner map + pure tests; persistence save/load and release hooks runtime-unverified. No
timeout. Staged 0.

## Stabilization phase (29-oct-2026)

La arquitectura amplia queda **PAUSADA**: cerebro propio, task lifecycle universal,
terreno adaptativo, fortaleza enemiga y rediseño amplio de profesiones. Prioridad:
baseline jugable y validación de gameplay.

Backlog canónico: `docs/autonomous-village/GAMEPLAY-OBSERVATION-BACKLOG.md`.
Auditorías: `DEVILRPG-STABILIZATION-BASELINE`, `DEVILRPG-PATHFINDING-COVERAGE`,
`DEVILRPG-SPAWN-PROGRESSION-AUDIT`, `DEVILRPG-PLAYER-POWERS-INVENTORY` (2026-10-29).

Requisitos obligatorios registrados: Vex no elegible al inicio/Aggressive sí;
SpawnProfile autoritativo para minHardDistance (con activación) y escalado temporal;
farm ownership runtime/release pendiente.

## User playable build (29-oct-2026)

PLAYABLE: isolated A5WALL client running; mod loaded; player at 270,140,330; `/tp 80 100 0` to observe village.
Navigation/hostile custom nav off; no auto-shutdown.

## Stabilization 14 defects (29-oct-2026)

PARTIAL: G2 riding speed level1 0.7x and G3 repeated tame hearts fixed/compiled. Other 12 pending; New World read-only.
No integrated runtime.

## CRITICAL: New World hash mismatch (29-oct-2026)

Expected F3CB43...; actual CB687B... level.dat timestamp 20:47 predates task. Not written by task; baseline
not checked first. Stop; do not restore. A2-Observation/harness unchanged.

## Stabilization phase 2 (29-oct-2026)

PARTIAL: G2/G3 technical fixes preserved; 12 pending, B3/C1 partial. No integrated runtime. New World untouched.

## Lumberjack block 1 (29-oct-2026)

PARTIAL: inner sapling fallback added; tree felling runtime 5 logs/+5 XP. Fixture counter limitation, not game fail.
A1/A2 runtime fixtures missing; A4/A5 pending.

## Lumberjack block1 completion (29-oct-2026)

PARTIAL: inner sapling stayed stage0, bone 32->0 unattributed; inner tree fixture hit stone_bricks. A1/A4/A6
unverified. No further run.

## Lumberjack fixture integrity (29-oct-2026)

Fixture verified: sap 98,97,0; tree 89,97,16; bone32. Final sap=air, tree still log, bone0, XP+5.
A2/A3 partial/fail runtime; A4/A6/A7 unverified. No more runs.

## Lumberjack final run (29-oct-2026)

No abono events; bone was only moved warehouse->inventory. Sapling air unexplained; inner tree untouched.
A2/A3/A5 fail runtime; A7 partial. No more runs.

## Lumberjack root-cause attempt (29-oct-2026)

Sapling selection added to descubrirTrabajo/unthrottled; still no LENADOR-ABONO runtime. Sapling air unattributed;
inner tree untouched; bone moved not consumed. A2/A3/A5 fail runtime.

## Lumberjack discovery trace (29-oct-2026)

descubrirTrabajo reached, planton=null; no abono, inner tree untouched, bone only transferred. A2/A3 fail.

## Lumberjack block1 execution (30-oct-2026)

FAIL. Scanner null; added standing-cell guard but no per-predicate trace. A2/A3/A5 fail. See EXECUTION.

## Definitive scanner execution (30-oct-2026)

SCAN ENTER proven; sapling gone 8s after placement at both 98,0 and 118,0. Not a predicate reject.
Inner tree untouched. A2/A3 fail. Need post-maintenance fixture.

## New iteration 30-oct: scanner accepts sapling after tick-200 fixture; sapling grew without ABONO attribution.
A2 candidate PASS but fertilization actor unknown; A3/A5 still fail. Fixture PASS is arboleda-only.

## Actor run 10 (30-oct): farmer grows only crops; lumberjack accepted sapling but puntoConHarina null. No ABONO.
A2/A5 fail. Need support-point resolution trace.

## Source lookup run13: both sources 0 despite fixture 32; no withdrawal. Refund fix compiled but unexercised.
A2/A5/A3 fail.

## Storage discrepancy run13: fixture count 32, production count 0; no withdrawal. Container identity unresolved.
A2/A3/A5 fail.

## Storage identity disproved: production reads32 at insertion, 0 later, no withdrawal. Consumer unidentified.
A2/A5 fail.

## A2 PASS runtime: lumberjack withdrew4, fertilized sapling stage0->1->log. A5 loss path fixed, not rerun.
A3/A4/A6/A7 fail.

## A5 transaction PASS run20: 4 withdrawn,2 consumed,2 left. Warehouse32 competition unresolved. A3/A4/A1/A6/A7 fail.

## A3 run22: sapling consumed8 no growth; tree117,10 not enumerated. A2 unstable. Need planner reset/verification.

## Run23: sapling grew after5; inner tree117,10 still not selected; task53,-17 is arboleda. A3/A4 fail.

## Run27 A2/A5 PASS: 4 withdrawn=2 consumed+2 left, growth state-based. A3/A4 fail; tree117 not selected.

## Area contract implemented; inner candidate still null at89,18. Arboleda 53,-17 works/delivers. A3 fail.

## Phase5 run33 lifecycle restored (L2-CANUSE, arboleda 5 logs). Root cause run32: all-villager discard removed goal context.
A3/A4 remain.

## Phase6 run34: delivery guardo2, warehouse +1, second task starts. A4 conservation fails by1.

## Phase7 run36: 5 logs remain carried, no delivery; warehouse -1 from other villagers. No loss. A4 needs longer/isolated delivery.

## Phase8 run37: 5 logs carried, delivery never starts even 3000 ticks. A4 blocked at trigger/navigation.

## A4 PASS run38: delivery carried2->0 accepted2 warehouse+2, twice; second task works.
A3/A1/A6/A7 remain.

## Phase11 inner decoupled, arboleda excluded; still no inner fixture selection. A3 fail.

## Phase12 found/removed per-delivery planner reset; center origin + inner arboleda exclusion. Run48 still no tree92.
# Phase13 — planificador sin reset por entrega, origen centro, exclusión arboleda; tree92 aún no evaluado.

## Phase14 numbers: inner origin26,-24 then126,16; batches reach ring11; fixture92 needs ring38 => never generated.

## Phase15 center origin + explicit reset proven; delivery no reset. A3 fixture90 not selected.

## Phase16 run57: A3 PASS inner tree70,-3 harvested2 delivered2; A7 PASS both areas.

## Phase17 stop clears ForestryTaskController; A3/A4/A7 preserve. A1/A6 runtime still unverified.

## Phase19: walled-tree env flag not propagated; run58 normal A3 regression. A1/A6 unverified.

## Phase20 dispatch fixed; wall created but tree70,-3 not selected. A1/A6 fail.

## Phase21: post-assignment wall at tick3694, tree70,-3; no failedPoint/next task by tick5400. A1/A6 fail.

## Phase22: physical progress policy; walled task failed tick4182, next task delivered. Terminal A1 pass; recoverable/interruption still open.

## Phase recover run63: one-block gap not navigable; walled tree failed. A1 recoverable/A6 fail.

## Run64 3-block corridor still ruta1 NO; A1 recoverable fail, A6 unexecuted.

## A1 PASS run65 (wall end route); terminal run62. A6 remains.

## A6 run66 did not register preemption goal; only normal forestry. A1/A2-A5/A7 unchanged.

## A6 run68: preempt goal registered but never started; A6 fail.

## A6: priority -1 preemption works (start602 target null), but test goal loops and no Worker B.

## A6 one-shot preemption+resume proven; Worker B isolation missing.

## A6 PASS run81: two fletchers, distinct ctrl/targets, preempt A, B unchanged, A delivered. Block A1-A7 complete.

## FINAL CLOSURE — Forestry Block 1 complete (A1–A7 PASS)
- Acceptance record: `docs/autonomous-village/FORESTRY-BLOCK-1-ACCEPTANCE.md`
- Runtime references: A1 runs 62/65; A2/A5 run 27; A3/A4 runs 57/58; A6 run 81; A7 runs 57/58/81.
- Two-worker scheduler isolation reference: Run 81.
- Forestry is now the stable foundation for remaining autonomous-village integration work.
- No forestry production refactoring is planned without a demonstrated regression.

## AUTONOMOUS VILLAGE MASTER PLAN
- Repository-wide audit and stabilization roadmap:
  `docs/autonomous-village/AUTONOMOUS-VILLAGE-MASTER-PLAN.md`
- Forestry Block 1 remains complete (A1–A7 PASS).
- Next implementation block is food autonomy (C1), after establishing the current
  farm -> storage/pantry -> consumption baseline.
- The master plan distinguishes runtime verified, automated-test verified,
  implemented/unverified, partial, and not-found systems.

## 2026-11-05 — Block 2 C1 source audit
Food consumption is **implemented** in `VillageManager.repartirRaciones` /
`pasarHambre`; the prior “not found” assumption was corrected. No production
change was made. C1 remains BLOCKED pending one end-to-end `food-autonomy` runtime
scenario. See `docs/autonomous-village/FOOD-AUTONOMY-BLOCK-2-ACCEPTANCE.md`.

## 2026-11-05 — C1 consumption runtime verified
Run 84: bread 32->22, meal 4000->5200; shortage weakness/slow at 3700 ticks
(`StarvingSince=8800`); recovery meal 14000, weakness false, `StarvingSince=0`.
C1.4-C1.6 PASS. C1.1-C1.3 farm/cook and C1.7 persistence remain blocked.

## 2026-11-05 — Food economy audit
Full audit: `docs/autonomous-village/FOOD-ECONOMY-AUDIT.md`.
Key findings: rations run every 1200 ticks (24×/day), one point requested per
villager, whole-item withdrawal can overdraw, and guards have no separate feeding
policy (village life is skipped during attacks). Production rates are unmeasured.
Recommended first correction: daily meal window + exact withdrawal + guard rule,
after design approval. Do not add food production to mask demand.

## 2026-11-05 — One-day food measurement
Run 85 (`food-day`, 24,000 ticks): pantry 256->64 points, bread 256->0; tracked
villager advanced 12 meal timestamps; farmer delivered 76 logged items, cook
produced 18 pieces from 6 batches, fisher 9 events. Net food balance negative.
Guard measurement invalid because the selected guard UUID equals the worker.
Details: `docs/autonomous-village/FOOD-ECONOMY-ONE-DAY-MEASUREMENT.md`.

## 2026-11-05 — Food measurement 2 (Run 86)
Distinct worker/guard/farmer/cook verified. Worker 13 meals, guard 9 meals (stopped
during attack). Over-withdrawal reproduced: requested10 -> returned10, bread 3->0,
12 points removed, 2 points lost. Pantry 256->11; farm 63 items, cook 13 pieces,
10 fish events. Negative daily balance. Details:
`docs/autonomous-village/FOOD-ECONOMY-MEASUREMENT-2.md`.

## 2026-11-05 — Food Block 3 partial implementation
`VillagePantry` now retains a per-container value credit; `EAT_INTERVAL_TICKS`
is daily (24,000) and ordinary rations are night-gated; guards have a separate
200-tick feeding check in `VillageGuardGoal`. Run 87: workerMeals=1, guardMeals=1,
pantry 256->369. Pre-sleep timing, work-dependent hunger, guard raid attribution,
credit persistence, and automated tests remain open. See
`FOOD-ECONOMY-BLOCK-3-ACCEPTANCE.md`.

## 2026-11-05 — Food Block 3.1 partial
Pantry credit moved from WeakHashMap to persistent `VillageSavedData.PantryCredit`
keyed by village objective index; production callers use the level-aware
`sacarComida(level, center, ...)`. Work-dependent hunger and true bedtime gating
remain unimplemented; guard raid path and automated tests remain unproven.
See `FOOD-ECONOMY-BLOCK-3.1-ACCEPTANCE.md`.

## 2026-11-05 — Food Block 3.2 failed runtime
Run 92: workerMeals=0 guardMeals=0, pantry 256->282. Daily feeding did not occur.
Persistent credit/work accumulator are source-only, tests missing, bedtime still
isNight gate. Do not claim completion. See BLOCK-3.2-ACCEPTANCE.md.

## 2026-11-05 — Lumberjack carrying-wood loop correction
`VillagerLumberjackGoal` delivery stuck branch now abandons after 6 attempts,
drops logs as recoverable ItemEntities, resets phase and resumes work. Status label
now checks actual logs. Run 93 verified 3 normal harvest/delivery cycles; fallback
itself not triggered. See `LUMBERJACK-STUCK-STATE-ACCEPTANCE.md`.

## 2026-11-05 — Villager movement ownership fix
Root cause: `VillagerLumberjackGoal` erased Brain `WALK_TARGET` every tick while
its task owned navigation, forcing a new path each tick (the standing sway).
Erase now happens only when target is null. Run 94 PASS: 3 harvest/delivery
cycles, `[Rumbo]` skipped 6272 redundant route writes. See
`VILLAGER-AI-COORDINATION-ACCEPTANCE.md`.

## 2026-11-05 — Block 3.2 partial: individual hunger + emergency feeding
Run 96: hunger 95/96, worker+guard meals, pantry 256->261. Meal at critical
threshold, not normal 60; 25-point reduction fix unverified; tests/reload/raid
missing. See FOOD-ECONOMY-BLOCK-3.2-ACCEPTANCE.md.

## 2026-11-05 — Block 3.2 run97: meal reduction 100->75 verified; normal 60 still late, guard0.

## 2026-11-05 — Block 3.2 run98: worker+guard meals, 25-point reduction verified.
Normal threshold 60 still delayed; no tests/reload/raid. Feeding goal
installation/canUse timing is the next diagnostic.

## 2026-11-06 — Food eligibility diagnostic (Runs 99, 101)
Instrumented tavern install/canUse behind `-Ddevilrpg.fooddiag=true`.
Run 99 showed all villagers `sleep=true` at hunger 60-91, causing rejection.
Removed sleep gate. Run 101 then showed `hunger=100 restTicks=0 attack=false
sleep=false pantry=232 resultPending=true` with **zero `[FOODDIAG] result` lines**,
so canUse still returns before destination selection. Root cause not isolated.
No production behavior accepted. See Block 3.2 acceptance.

## 2026-11-06 — Food eligibility root cause found
Run 102 logged every tavern rejection. At hunger 80:
```text
[FOODDIAG] reject uuid=04d93501-... tick=9310 hunger=80
           reason=HUNGER_OR_TABERNA hungry=false taberna=true
```
Source `VillageManager.tieneHambre` (line ~8491) still returns:
```java
level.getGameTime() - ultimaComida(level, villager) >= EAT_INTERVAL_TICKS
```
It was never changed to `hambre(villager) >= HAMBRE_NORMAL`. Therefore the
tavern/guard gates ignore individual hunger until the legacy 24,000-tick clock
expires. The sleep gate was a secondary rejection, not the primary cause.
No production behavior changed in this diagnostic. Next correction: one-line
change to `tieneHambre`.

## 2026-11-06 — Normal feeding at 60 verified (Run 105)
`tieneHambre` now uses `hambre(villager) >= HAMBRE_NORMAL`; the previous edits
had not applied. Run 105:
```text
FOODDIAG result hunger=62 dest=107,97,16 pantry=256 result=true
workerMeal tick=630 hunger=37
FOODDIAG result hunger=60 pantry=184 result=true
workerMeal tick=6374 hunger=35
```
Real pantry meals, 25-point reduction, second meal only after hunger returned to
60. Block 3.2 normal feeding is now PASS. Remaining: automated tests, starvation
recovery, guard raid, save/reload.

## 2026-11-06 — Block 3.2 final acceptance not complete
Normal feeding at 60 and the 25-point meal are runtime verified (Run 105).
Remaining BLOCKED/UNTESTED: automated tests, starvation/recovery, guard raid
feeding, real save/reload of hunger and pantry credit. No production changes in
this step; do not mark Block 3.2 complete.

## 2026-11-06 — Starvation integration source + Run 107
`pasarHambre` now starts a per-villager critical timer when `DevilRpgHambre >= 90`
and preserves HAMBRE_PACIENTE_TICKS/STARVATION_DEATH_TICKS. Run 107 held pantry
empty:
```text
setup hunger=93 pantry=0
effects hunger=100 weakness=false slow=false tick=4400
```
No weakness/slowness at 3800 ticks after setup. Starvation integration remains
UNVERIFIED/FAILING; no tests added. Recovery showed hunger 58 but the subject ate,
so recovery is not evidence of the new critical-starvation path.

## 2026-11-06 — Starvation trace root cause (Run 108)
`pasarHambre` runs every 200 ticks; critical timestamp set at tick4600 and
`critica` reaches 3600. Weakness remains false because the thresholds are derived
from the now-daily EAT_INTERVAL:
```text
EAT_INTERVAL_TICKS = 24000
HAMBRE_PACIENTE_TICKS = 3 * EAT_INTERVAL = 72000
STARVATION_DEATH_TICKS = 12,000 (absolute)
```
So HAMBRE_PACIENTE (72,000) is above the death threshold (12,000): weakness is
unreachable. Root cause: the patient threshold was coupled to the daily meal
interval while death remained absolute. No production change in diagnostic; next fix is
decoupling constants.

## 2026-11-06 — Starvation effects/recovery PASS (Run 109)
`HAMBRE_PACIENTE_TICKS` decoupled to 3*60*20=3600; death remains 12000.
Run 109:
```text
setup hunger=93 pantry=0
critica=3600 weak=false -> next update weak=true slow=true
recovery hunger=57 weakness=false slow=false
[A5] FIN PASS
```
Effects activate at the 3600 critical threshold and clear after a real meal.
Death timer (12000), automated tests, guard raid, reload remain open.

## 2026-11-06 — Starvation death boundary PASS (Run 110)
Run 110 held pantry empty and critical hunger:
```text
C1-DEATH setup hunger=93 tick=600
Village ... ha muerto de hambre (10 min en hambre critica)
C1-DEATH dead crit=12000 tick=16600
A5 FIN PASS
```
No meal occurred; critical timestamp persisted to 12,000 exactly. Failed feeding
did not clear starvation. Automated tests still missing.

## 2026-11-06 — Food hunger automated policy tests
Extracted `FoodHungerPolicy` and wired `VillageManager` hunger/feeding/starvation
to it. Added `FoodHungerPolicyTest` to policyTest: 59/60/61 eligibility, 89/90
critical, cap100, floor0, 25 reduction, 3599/3600 effects, 11999/12000 death,
single critical timestamp. All pass. Runtime evidence preserved from Runs 105/109/110.

## 2026-11-06 — Guard raid feeding NOT verified
Run 118: guard meal 100->75 but `siege=false` at the meal. Run 119 delayed the
raid to tick6000; classic defense did not start (`INCONCLUSIVE`). No active-raid
guard feeding evidence exists. A 3600-tick meal cooldown now prevents the
duplicate 95->70->47 sequence from Run 113. Guard raid remains OPEN.

## 2026-11-06 — Guard active-raid feeding PASS (Run 120)
Fixture seeds guard `DevilRpgUltimaComida = now-4000` (test-only), hunger100; raid
starts at tick600. Result:
```text
C1-MEAL uuid=4a5d61a8-... before=100 after=75 siege=true tick=5840
A5 FIN PASS
```
Real active raid at meal tick, exact 25 reduction, one meal. Raid later ended;
guard alive. Defense resumption target not captured.

## 2026-11-06 — Defense resumption still PARTIAL (Runs 121-123)
Run120 proved active-raid meal (siege=true, 100->75). Post-meal target capture:
- Run121/122: target=-, goalMOVE=true.
- Run123: moved real AggressiveZombie to guard; target still -; siege ended within
  600 ticks. No target acquired after meal. Defense resumption NOT proven.
No production AI change made. Remaining: capture a hostile target after meal.

## 2026-11-06 — Guard defense resumption PASS (Run 127)
Root cause of prior FAIL: A5 checked vanilla `getTarget()`, but VillagerGuardGoal
stores the enemy in private `enemigo`. Instrumented scan/acquired log:
- meal 100->75, siege=true at tick5780;
- real raider dc333a86 moved next to guard;
- at tick6160..6380 guard acquired `enemy=dc333a86` at 0.8-1.2 blocks;
- raid remained active; no duplicate meal.
Defense resumption PASS. A5 FIN still says FAIL because it reads getTarget();
documented false negative in fixture assertion.

## 2026-11-06 — A5 guard raid feeding + defense resumption PASS (Run 128)
Added read-only `VillagerGuardGoal.enemigoActual()`; A5 now asserts the real
combat target. Result:
```text
C1-MEAL before=100 after=75 siege=true tick=4923
postMeal+600 target=2fa83e6d-... alive=true dist=2.44 goalMOVE=true
A5 FIN PASS defending=true
```
Single meal, exact 25 reduction, real hostile acquired after feeding. Raid-feeding
subsystem CLOSED. Next: hunger/pantry save/reload.

## 2026-11-06 — Hunger + pantry credit survive reload (Run 130)
Save: villager e12de154... hunger=100 objective=0 pantry 4 points, request2
removed2, credit2, pantry0. Reload same world: hunger=100, creditBefore=2,
credit-only withdrawal removed2, creditAfter=0. Same UUID/objective. Ledger:
4 physical = 0 remaining + 2 consumed + 2 credit; after reload 2 credit consumed.
No duplicate/lost credit observed one cycle. Automated serialization test absent.

## 2026-11-06 — Food Economy Block 3.2 closure state
Persistence: run130 PASS (hunger100, credit2->0) plus two `food-audit` reloads
PASS (hunger100, credit0, lastMeal4602). Architecture documented in
`docs/autonomous-village/FOOD-ECONOMY-ARCHITECTURE.md`.
Open limitations: real post-reload meal/cooldown expiry not exercised; no NBT
serialization automated test. Next task: Lumberjack implementation only after
owner accepts these limitations.

## 2026-11-06 — Post-reload second meal NOT verified
Run 131 (food-meal-save) reached a real tavern meal for e12de154- after setup
hunger62, pantry64->24, but the scenario did not reach its save/finish assertion
before timeout. No reload/second-meal evidence. Do NOT close Block 3.2.

## 2026-11-06 — MEAL2 timeout root cause fixed (Run 132)
Run131 fixture checked `hunger<=45` after a meal that actually occurred at high
hunger, so it never saved. Replaced with `ultimaComida != fmLastMeal`.
Run132 saved PASS: hunger52 lastMeal8377 pantry12.
Reload phase still never produced a second meal despite hunger86, cooldown0,
pantry24 — separate unresolved post-reload eligibility issue.

## 2026-11-06 — Post-reload no-meal root cause: sleep=true
Run132 reload: tavern goal installed (prio -1, 6 goals), hunger97, pantry88,
cooldown0, but FOODDIAG rejects `reason=ATTACK_OR_SLEEP attack=false sleep=true`
at gameTime ~17978 (night). No production defect; fixture must run the post-reload
meal during daytime or wake the villager legitimately.

## 2026-11-06 — Block 3.2 CLOSED: daytime post-reload second meal PASS
Run132 final: tavern goal installed prio -1, sleep=false after daylight, second real
meal e12de154- 94->69 at gameTime23373, lastMeal23373, exactly one tracked meal;
cooldown logs showed remaining>0 before expiry and no early meal. A5 FIN PASS.
NBT serialization automated test remains absent (documented limitation).
