# CURRENT CHECKPOINT — 2026-11-19 A2 Phase55 fixture population forensics

La continuación vigente está en `docs/autonomous-village/NEXT-SESSION.md`.

- Run `a2n-20261119-031255`: BLOCKED antes del spawn de atacantes; 289/289 chunks READY en 1 tick.
- Población: 14 entidades = 4 guardias fundadores esperados + 10 profesionales; faltan SHEPHERD (slot 5) y MASON (slot 11).
- Corrección de interpretación: los cuatro aldeanos con profesión NONE son los cuatro fundadores intencionales; no son cuatro trabajadores con profesión perdida.
- Fuente revisada: el generador intenta 12 slots, pero la ruta de spawn no devuelve/expone resultados por slot; aún no se sabe si los dos trabajadores faltaron al crear, murieron, fueron removidos o cambiaron de profesión.
- Forensics: `docs/autonomous-village/2026-11-19-A2-PHASE55-POPULATION-FORENSICS.md`.
- Siguiente: Harness inspecciona logs y NBT existente en modo estrictamente read-only; sin runtime, regeneración ni edición de mundos.
- Autorización Run1 consumida. No retry, Run2, C4 standalone ni Full12K. A2 OPEN; Phase55 runtime no medido.

---

---

# CURRENT CHECKPOINT — 2026-11-18 A2 Phase 53 (PENDING HARNESS)

**Production SHA:** `656ce7d0f6182fd15e0b1bbb0bd6684c180bd0e6`.
**Implementation report:** `docs/autonomous-village/2026-11-18-A2-PHASE53-CADENCE-ROUTE-PRESERVATION.md`.

Phase52 proved the IDX5 regression: `CADENCE_WAIT` suppressed an already validated global waypoint and no movement command followed. Phase53 preserves that non-stair route (and the directly reachable mission fallback) while deferring only stair construction. New trace: `A2_STAIR_CADENCE_ROUTE_PRESERVED`.

**Harness next:** validate the exact SHA with safe fast-forward, preserve local work/worlds, run static checks first, then A2 Run1 only if all pass. Verify trace → valid `MOVE_COMMAND` for IDX5, unchanged cooldown counters, and no false stair placement. No GUI/focus/user intervention. Do not Run2 or Full12K. IDX3 all-solid candidate geometry remains separate; monotonic anchor remains runtime-unvalidated.

A2 remains OPEN at 2/8 strict. Phase41/44/46 and all budgets/safety predicates remain unchanged.

---

# HISTORICAL CHECKPOINT — 2026-11-18 A2 Phase 51 (PENDING HARNESS)

**Production implementation SHA:** `ea21466a0cc7cc2671f5439cd5ff7bbaeecd6d9e`.
**Implementation report:** `docs/autonomous-village/2026-11-18-A2-PHASE51-STAIR-WIRING-FIX.md`.
**Previous validation:** Phase50 `a2n-20261118-212254` — FAIL, 3/8 strict, C4 PASS [1,0,0].

- Phase51 wires `NavStairAnchorPolicy.refresh(previous, supportBelow, hasTraversedStair)` into
  `AggressiveZombieEntity`; post-traversal candidates are defensively required to be strictly below
  the traversed anchor.
- The builder now returns `CADENCE_WAIT` only when it actually decrements the cooldown. All three
  executor call paths preserve that result; `A2_LOCAL_STAIR_WAIT` is emitted and local step/failure
  counters are not consumed. Plan-level waits defer the next attempt by two ticks.
- Other budget-fallback outcomes retain their prior behavior; only `CADENCE_WAIT` updates the
  executor wait state.
- `NavStairAnchorPolicyTest` (8 assertions) and `StairBuildResultTest` (6 assertions) are now
  registered in the normal `policyTest` gate; `gradlew test` depends on that gate.
- **PENDING HARNESS:** validate exact SHA above. Static checks first; if all pass, A2 Run1 only.
  Preserve local/untracked work and worlds; Harness must not modify production.
- Phase41/44/46 and all budgets/safety predicates remain unchanged.
- A2 remains OPEN at 3/8 strict; C4 last PASS [1,0,0]. IDX4 horizontal-zero remains
  INSUFFICIENT_EVIDENCE. No Run2/Full12K until five consecutive 8/8 strict runs.

---

# HISTORICAL CHECKPOINT — 2026-11-18 A2 Phase 46 (PENDING HARNESS)

- Production baseline: `61b39793b5e5fd6f747e9040c888ba32662efb2d`; zero-horizontal-distance DROP edge-step admission and four policy assertions added.
- Report: `docs/autonomous-village/2026-11-18-A2-PHASE46-VERTICAL-ONLY-DROP-FIX.md`.
- Expected `NAV_DROP_EDGE_STEP_POLICY_TEST: OK (16 assertions)`; static/runtime not yet validated.
- A2 OPEN, last result 3/8 strict; C4 last PASS [2,0,0].
- Next: static checks first; then A2 Run1 only, with `-Ddevilrpg.a2n.traceStair=true` to diagnose IDX3 builder
  lifecycle and verify IDX4 support-clear → natural descent → physical landing.
- No budget increases, no weakened predicates, no Run2/Full12K until five consecutive 8/8 strict. Harness does not modify production.
---
# CURRENT CHECKPOINT — 2026-11-17 ranked AABB DROP source selection (PENDING HARNESS)

**Production code checkpoint:** `a795c33d03678e2a12d066645e5dbc927d7f3efb`.
**Detailed report:** `2026-11-17-A2-GROUNDED-SOURCE-SELECTION-PHASE26.md`.

- Phase 25 tested `011373d0b3423b188611867d7f4751ab2ac88c66`: static PASS, A2 Run 1 FAIL 3/8 perimeter and 3/8 strict; C4 PASS `[1,0,0]`. No Run 2/Full 12K.
- Phase 25 emitted 77 `DROP_TAKEOFF_UNSUPPORTED_SOURCE_RECOVERY`, zero source snap/replan; IDX4 UUID `03df487a-ff61-482c-9407-ed5cdd0d1b5d` failed at `567,122,534` with grounded=true, feetValid=false, support=false, realAabbFits=true.
- Root cause in the resolver contract: `uniqueAdjacentCandidate` considered only four cardinal cells at the same Y and returned null when more than one candidate was physically valid. It could also miss a valid diagonal overlap. Thus a set of safe alternatives was treated as a dead-end.
- New `NavSourceCellPolicy.bestAdjacentCandidate` evaluates all eight same-Y neighbors with positive real-AABB horizontal overlap. The caller still requires `esPieValido` (including protection, feet/head clearance and solid support), explicit support, and `fitsAtCellCenter` for the translated real AABB.
- Deterministic ranking: greatest actual horizontal overlap area, then nearest candidate center to the mob's current X/Z, then lexicographic X/Z tie-break. No candidate is accepted on proximity alone. The executor uses this selector in both grounded source recovery paths and retains horizontal MoveControl snap plus physical occupancy verification before replanning.
- Source-cell policy tests expected: 14 assertions (previous 10 + 4 covering multi-candidate deterministic selection, overlap ranking, diagonal-only candidate, and no-safe-candidate rejection). DROP take-off policy remains 51 assertions.
- **PENDING HARNESS:** safely sync the live branch HEAD (resolve it from Git; the handoff documentation will be a descendant of this code checkpoint), preserve local/untracked files and worlds. Run Gradle tests, strict lint, diff-check, and diagnostics compile. Confirm source-cell policy 14 and DROP take-off 51 assertions.
- If static validation passes, execute A2 Run 1 only. Inspect candidate selection and snap/replan for IDX4 and the former blocked attackers. Confirm selected source has support, valid feet, protection clearance and centered real-AABB fit; confirm actual physical snap before route planning.
- If no candidate is selected, capture each of the eight neighbors' overlap, chunk-loaded, protected, feet-valid, support and translated-AABB-fit predicates. Do not widen search vertically, invent a source, or weaken safety predicates without a separate evidence-backed design.
- No Run 2 unless Run 1 is 8/8 strict. No Full 12K until five consecutive 8/8 strict runs. No budget increases or relaxed support, collision, protection, headroom or landing criteria.
- A2 remains OPEN. C4 last PASS [1,0,0], save/reload OPEN. IDX7/IDX8 remain independent. Original worlds and local artifacts protected. Harness must not modify production.
- ChatGPT has not executed Gradle tests, lint, diagnostics compile, or Minecraft runtime.

---

# CURRENT CHECKPOINT — 2026-11-17 grounded invalid DROP source recovery (PENDING HARNESS)

**Code checkpoint:** `e80f99d5acd4bf987524d4ab5d5a79ad5392fdd6` (the handoff documentation is committed afterward; always sync the live branch HEAD).
**Production code checkpoint:** `e80f99d5acd4bf987524d4ab5d5a79ad5392fdd6`.
**Detailed report:** `2026-11-17-A2-GROUNDED-INVALID-DROP-SOURCE-RECOVERY-FIX.md`.

- Phase 24 tested `37c0aa702b44d94c6d5db506d023ec65d1b8`: A2 FAIL 1/8 perimeter and 1/8 strict; 2,102 unsupported-source WAIT events. The dominant state was grounded with invalid feet/support but a fitting real AABB.
- Root cause: grounded-invalid source bypassed the existing AABB source resolver and fell into the airborne WAIT branch. It now snaps only to a unique cardinal same-Y supported cell overlapped by the real AABB, using horizontal MoveControl, then replans from the physically verified source.
- If no unique source exists or the snap cannot be physically completed within 80 ticks, the executor emits an explicit failure and fails closed; no infinite wait loop.
- A missing `plannedFrom` can no longer bypass DROP origin admission: the live cell is accepted only if grounded, valid, supported and real-AABB fitting; otherwise source recovery/wait occurs.
- `DROP_START` now logs originPlan and physical source predicates for IDX4 forensic diagnosis.
- Policy test expected count is now 51 assertions (47 + 4). Tests authored, **not executed by ChatGPT**.
- **PENDING HARNESS:** sync exact HEAD above; run Gradle tests, strict lint, diff-check and diagnostics compile first. Confirm 51 assertions. If static PASS, A2 Run 1 only.
- Capture source recovery/snap/replan events for the seven blocked entrants and new DROP_START origin fields plus landing/cursor events for IDX4 UUID `bbadffa7-09c6-49fa-8adf-c1cd118e30f5`.
- No Run 2 unless Run 1 is 8/8 strict. No Full 12K until five consecutive 8/8 strict runs. No budget increases or relaxed geometry/support/protection/landing criteria.
- A2 remains OPEN. C4 last PASS [1,0,0], save/reload OPEN. IDX7/IDX8 remain independent. Original worlds/artifacts protected. Harness must not modify production.
- ChatGPT has not run tests, lint, diagnostics compile, or Minecraft runtime.

---

# CONTINUAR — IDX4 unsupported DROP take-off guard (2026-11-17)

Implementation checkpoint: `c17ec47e7277a687fa428841d64ca8c8089e68c1`.
Latest handoff: `docs/autonomous-village/NEXT-SESSION.md`.
Latest fix report: `docs/autonomous-village/2026-11-17-IDX4-UNSUPPORTED-TAKEOFF-REPLAN-FIX.md`.

Phase 23 established that planned take-off `589,96,522` is unsupported; the requested landing `589,95,521` never emitted `DROP_DONE` or cursor advance. Source inspection found that runtime take-off admission checked coordinates but not grounded state, valid feet, support, or actual AABB fit.

ChatGPT changed DROP admission to require a grounded, valid, supported, fitting live source and a supported, fitting planned take-off. An unsupported planned take-off is not executed: the mob waits while airborne, then discards the stale DROP route and replans from its actual grounded supported cell. New traces: `DROP_TAKEOFF_UNSUPPORTED_WAIT` and `DROP_TAKEOFF_UNSUPPORTED_REPLAN`.

Four policy assertions were added; expected `NAV_DROP_TAKEOFF_POLICY_TEST` total is 47. **Harness validation is pending; ChatGPT has not run tests, lint, diagnostics compile, or Minecraft runtime.** Static gate first, then A2 Run 1 only if static passes. No Run 2 unless 8/8 strict; no 12k until five consecutive 8/8 strict. A2 remains OPEN; C4 last PASS [1,0,0], save/reload OPEN; IDX7/IDX8 independent. No budgets or safety predicates relaxed.

---

# CURRENT A2 / IDX4 CHECKPOINT — 2026-11-17

The active handoff is `docs/autonomous-village/NEXT-SESSION.md`.

- Forensic root cause: AABB support crosses the Z=508 cell boundary while blockPosition remains in Z=507; see `docs/autonomous-village/2026-11-17-IDX4-GROUNDED-INVALID-FORENSICS.md`.
- ChatGPT production checkpoint: `b02affb19eb8e37824682090ba99f793a04aea94`.
- Implementation: unique supported adjacent source resolution from actual AABB, bounded horizontal MoveControl to cell center, real-AABB fit checks, and a strictly bounded one-block DROP permitted only in the temporary route back to original take-off. No teleport, impulse, or relaxed support.
- Tests added: `NavSourceCellPolicyTest` 10 assertions; `NavDropTakeoffPolicyTest` 40 expected assertions. Pending Harness execution.
- Next: Harness static validation, then A2 Run 1 only. No Run 2 unless 8/8 strict; no 12k until five consecutive 8/8 strict runs.
- A2 OPEN; IDX7/IDX8 untouched; C4 last run PASS [1,0,0], save/reload OPEN.

---

# Continuación vigente — 2026-11-16

La continuación operativa actual de DevilRpg está en `docs/autonomous-village/NEXT-SESSION.md`.
Antes de actuar, leer también:

- `docs/autonomous-village/CHATGPT-COLLABORATION-POLICY.md`
- `docs/autonomous-village/CHATGPT-HARNESS-WORKFLOW.md`
- `docs/autonomous-village/CONTINUAR.md`
- `docs/autonomous-village/2026-11-16-PHASE19-BREAK-GRAPH-STATE-FIX.md`

## Acuerdo operativo

ChatGPT es responsable de modificar código de producción directamente en GitHub y publicar commits. Harness es responsable de sincronizar con seguridad, preservar artefactos locales, ejecutar pruebas/compilaciones/lint/runtime, medir y producir reportes. Harness no corrige código de producción por su cuenta; devuelve evidencia reproducible para que ChatGPT implemente la corrección. Cada validación debe identificar el SHA exacto.

## Checkpoint actual

- Rama: `feature/autonomous-village`.
- Código Phase19 pendiente de validación: `6d5dc7b1d1f72a366d4f6b05626147b9e932481c`.
- Hallazgo: el grafo A* colapsaba el estado BREAK post-acción con el estado WALK al stand; además, BREAK podía avanzar por proximidad sin ejecutar el actuador.
- Corrección publicada: estado post-BREAK y stand separados, nodo BREAK no alcanzado por proximidad, ocupación física exacta del stand, transferencia del action target a la ruta local y reanudación de misión estratégica.
- Fixture determinista agregado: `A2NavHarness` modo `break-runtime`; pruebas nuevas registradas, aún no ejecutadas.
- Siguiente acción: Harness ejecuta Gradle, lint, diff-check y el fixture runtime en un mundo desechable con `-Da2n.mode=break-runtime -Ddevilrpg.a2n.traceNav=true`. Reportar SHA, logs y eventos; no tocar código de producción.
- A2 permanece OPEN en 5/8 estricto; no ejecutar campaña 12k hasta cinco corridas consecutivas 8/8 estrictas. C4 save/reload OPEN.

Este bloque es el checkpoint vigente. El resto del archivo conserva los registros históricos y no sustituye los documentos activos indicados arriba.

---

## 2026-11-12 22:43 — C4 ACCEPTED; A2 GLOBAL / BLOCK 6 SOAK OPEN; C1–C5 ACCEPTED

**C4 ACCEPTED:** la variante final del clérigo pasó en `c4-full8-20261112-2240`. Resultado final: 4/4 guardias combatieron; 12/12 trabajadores vivos reanudaron; 16 aldeanos vivos; 0 bajas/no resueltos; JOB_SITE limpio; 15 impactos, 6 atacantes muertos (5 atribuidos a guardias) y 2 retirados para recuperación. El detalle completo está al final de este archivo y en `A2-C4-HEADLESS-ACCEPTANCE-20261112.md`. Run4 tuvo 4/4 guardias, 16 impactos, 16 aldeanos vivos y 11/12 trabajadores contados; todos los 12 Goals seguían instalados. Run5 con UUID tracking registró 4/4 guardias, 16 impactos, 5 atacantes muertos, 3 retirados, 15 aldeanos vivos, 11 profesionales supervivientes, 11 Goals y 11 reanudaciones; JOB_SITE 0 huérfanos/duplicados. El UUID faltante `f62b9fae-2361-42b0-b0f3-6058f265c789` era el único weaponsmith y el log de Minecraft registra que Quintin (Herrero de armas) fue asesinado por un Aggressive Zombie. No era un falso negativo de AABB: fue una baja real.

**Corrección del contrato:** C4 no especifica cero bajas; exige que los trabajadores supervivientes reanuden sus profesiones, que las bajas se registren y que no haya entidades esperadas sin resolver. La aceptación ahora exige: todos los UUID iniciales están presentes vivos o tienen evento de muerte registrado; cada trabajador vivo reanuda su Goal profesional; Goals instalados = trabajadores vivos; 4/4 guardias con engagement; JOB_SITE sin huérfanos/duplicados; la aldea sobrevive. No se ocultan bajas ni se cuenta un muerto como trabajador que no reanudó.

El harness añade `LivingDeathEvent` para capturar UUID, profesión, nombre, posición, DamageSource, atacante y entidad directa; al cierre distingue trabajadores vivos/reanudados, bajas y UUID no resueltos. `compileJava -Pa2nDiagnostics=true --no-daemon --offline --rerun-tasks` dio BUILD SUCCESSFUL. SHA-256 canónico: `0B699503DCBE99D084AE4A073FE77650B72484B02BA97DED0829C889BFCA9358`.

**Run6 (`c4-full6-20261112-0210`) terminó C4_FAIL:** 4/4 guardias, 16 aldeanos vivos, 12/12 profesionales y Goals instalados, 11/12 reanudados, 0 bajas, 0 UUID no resueltos y JOB_SITE limpio. El faltante fue el clérigo (`bbd837c3-4253-4737-acc1-164eedb6af01`): `runningGoals=[]`, `jobSite=none`, vivo en 619,71,490. El log confirma que había salido a sanar a un guardia. Vanilla invalida `JOB_SITE` por distancia y el Goal perdía el soporte al terminar la curación.

**Corrección funcional:** `VillagerClericGoal` conserva un `GlobalPos` válido del soporte de pociones aunque Brain borre `JOB_SITE`; el repartidor de estaciones registra el soporte directamente en el Goal al asignarlo, antes del siguiente tick vanilla, y la captura también ocurre durante el descanso corto posterior a curar. `compileJava` PASS.

**Runs finales:** `c4-full7-20261112-0230` y `c4-full8-20261112-2240` terminaron C4_PASS en copias desechables independientes. Run8 valida el callback final del clérigo; no relanzar ni repetir esos RunIds.

**Desconexiones:** procesos largos corren en el PC y persisten logs/veredicto. `consultar-c4.ps1 -RunId <id> -Wait` sondea cada 15 s sin relanzar ni detener. Las llamadas remotas transitorias se reintentan hasta 3 veces con espera creciente; si el agente/PC está realmente offline no se puede garantizar reconexión automática. No relanzar RunIds existentes.

El launcher captura stdout/stderr de Gradle vía `cmd.exe` y valida `fin.txt`, `C4_PASS`, cuatro UUID de guardia y exit code. C1–C5 están ACCEPTED. Block 6 multi-day soak está desbloqueado como siguiente tarea, aún no ejecutada. A2 global sigue OPEN: C4 PASS no equivale al umbral A2 de 8/8 en tres semillas distintas. Seed reparado intacto; no GUI/foco/interacción; no `run/saves` ni mundos históricos. No commit/push; `.partial` protegido.

---

## 2026-11-12 21:10 — C1/C2/C3/C5 ACCEPTED; C4 FOUNDATION PASS / A2 CONTRACT OPEN

C2 resource-to-repair accounting is **PASS**. Production change in
`VillagerRepairGoal.java`:
- Resolve the required material from the planned block's item form; farmland
  uses dirt. No-item-form blocks are not repaired for free.
- `canUse()` checks warehouse count before reserving a repair target.
- At completion, withdraw exactly one matching item with
  `VillageStorage.quitar`; place and verify the block; refund to storage (or
  drop the remainder at the worker) if placement is rejected.
- An invalid/already-repaired target is released before material withdrawal.

Accepted runtime: `c2-repair-accounting-r2-20261112-202200`, PASS tick600.
Target `77,97,-3`, `minecraft:stone_bricks`, worker
`83496b07-d1ce-4cb4-9212-a6db13d8cb9f`. No material: block unchanged/count0;
one item: block restored/count 1→0; already repaired: item remains/count1.
Shutdown clean=true, hidden window, focusStealActions=0, remaining clients=0,
launcherTreeRemaining=0. Evidence:
`docs/autonomous-village/C2-REPAIR-ACCOUNTING-ACCEPTANCE.md`.

Two later optional fixture iterations,
`c2-repair-accounting-r3-20261112-203000` and
`c2-repair-accounting-r4-20261112-203100`, failed only an assertion that
expected `canUse()` to populate the skipped-target set. Observed
`blocked=true, skipped=false`: `canUse()` returned before target selection.
Both had clean shutdown, hidden client, focusStealActions=0, and zero residual
clients/launcher descendants. This is a harness assertion failure, not a
product FAIL; excluded from acceptance. The current C2 fixture no longer has
that assertion. Exact run IDs and disposition are in
`C2-REPAIR-ACCOUNTING-ACCEPTANCE.md`; accepted r2 transaction evidence remains
valid.

C3 remains accepted by historical Run81; C5 passed in r3; C1 Food Economy
3.1/3.2 is closed. Therefore C1, C2, C3 and C5 are accepted.

**C4 foundation repair: PASS; full defense acceptance remains OPEN.** Run
`arena-repair1-20261112-205206` created a second disposable seed-12345 world.
Its read-only pre-scan matched the prior 33-coordinate manifest exactly. The
repair replaced exactly 33 water blocks with stone at Y=62. Post-repair runtime
verification: 400/400 columns NON_EMPTY, 0 EMPTY, 367 unchanged, 0 unexpected
changes, 0 missing chunks. Protected harness hashes unchanged; no A2 raid.
Full before/after manifests, coordinate diff, logs and hashes:
`A2-FOUNDATION-REPAIR-20261112-ACCEPTANCE.md`.

**Next gate: explicit approval to edit the canonical A2 harness.** The
read-only audit is recorded in `A2-EDIT5-READONLY-AUDIT-20261112.md`: phase
A/B metrics and wall integrity code already exist, but the verdict remains
`SIN UMBRAL CONTRATADO` / `INSTRUMENTADO`; the launcher only counts markers;
direct guard engagement and profession/assignment recovery are not instrumented.
Do not apply the older `_raid_e5_block.txt`. No harness edit or full A2 raid is
authorized yet; request approval separately. Keep `.partial` untouched. Do not
use fallback `629`, historical worlds, or `run/saves`. C4 remains OPEN/BLOCKED
and Block 6 multi-day soak remains gated.

---

## 2026-11-12 20:20 — C5 VILLAGE PERSISTENCE PASS; C2 NEXT

C5 ya está **ACCEPTED**. Runs:
- Save `c5-village-persistence-save-r3-20261112-201500`: PASS tick612,
  dos centros, dos UUID/profesiones, almacenes exactos A=13 oak/5 spruce,
  B=29 oak/7 spruce; GoalSelector lumberjack running=true con TALAR/
  TRAVELLING target 98,98,0 al guardar.
- Reload `c5-village-persistence-load-r3-20261112-201500`: PASS tick447.
  Ambos centros/UUID/profesiones/almacenes coinciden; NBT TALAR se restauró;
  el poll normal de vida de aldea reinstaló exactamente un
  `VillagerLumberjackGoal`, duplicados 0/0; después de 260 ticks el trabajador
  se desplazó 32.5532 bloques. Ambos pares result/shutdown PASS, ventana oculta,
  foco robado 0, clientes residuales 0, launcherTreeRemaining 0, clean=true.
- Intentos anteriores C5 load r1/r2 no encontraban B porque el fixture consultaba
  antes de completar carga del chunk forzado. El r3 guarda las posiciones exactas,
  llama `getChunkAt`, espera 5 ticks y luego busca UUID. No era defecto de producción.

Documento fuente: `docs/autonomous-village/C5-VILLAGE-PERSISTENCE-ACCEPTANCE.md`.
Block 6 pasos 1–2 completos. Soak multi-día sigue gated hasta C1–C5 aceptados.

**Nota histórica (supersedida por la sección 20:40):** C2 se ejecutó y quedó
PASS en `c2-repair-accounting-r2-20261112-202200`. La única dependencia C-level
restante es C4/A2, bloqueada hasta que el owner apruebe el fixture `661,71,533`.
No tocar A2 ni usar el fallback `629` sin aprobación.

---

## 2026-11-12 19:50 — LUMBERJACK PHASE 1 ACEPTADA; REFUNDS EXCEPCIONALES PASS

Se cerró Phase 1 dentro del alcance definido. No repetir A5-01..A5-12 ni Block 1
sin un cambio relevante de invariantes. Rama `feature/autonomous-village`;
HEAD a verificar al reanudar; no commit/push. Repo
`D:\Documents\DevilRpg-autonomous`.

Cambios de producción:
- `VillagerLumberjackGoal.plantarSemilla` devuelve boolean y confirma la
  colocación comprobando el estado real del bloque. `plantar` y `replantar`
  devuelven la semilla retirada si el mundo rechaza la mutación.
- Bone Meal: si no hay crecimiento, se devuelve la cantidad original incluso
  cuando la API mutó su argumento antes de devolver false. Si el argumento
  queda vacío, se reconstruye `new ItemStack(Items.BONE_MEAL, consumidas)`;
  no intentar mutar `ItemStack.EMPTY`.
- Los dos failure-injection hooks son false por defecto y se resetean en
  `finally`; solo los activa el controlador de pruebas en mundos desechables.

Evidencia runtime final (cada run con `result.json` PASS + `shutdown.json`
PASS, mundo realmente abierto, ventana oculta sin activación, foco robado 0,
clientes residuales 0, launcherTreeRemaining 0, clean=true):
- `phase1-final-sapling-reject-20261112-194500` / `lumber-sapling-reject`:
  forzó rechazo en `plantar` y `replantar`; dos celdas siguieron air, inventario
  conservó exactamente 1 oak sapling, `replantar` devolvió false.
- `phase1-final-bonemeal-reject-r2-20261112-194700` /
  `lumber-bonemeal-reject`: API=false después de simular consumo de argumento;
  log `consumidas=1 devueltas=1 inventario=1`, sapling sin cambio.
- La primera variante de Bone Meal detectó el bug de `ItemStack.EMPTY.grow`
  (inventario 1->0); se corrigió y el reintento r2 dio PASS. No ocultar este
  primer FAIL histórico: es la evidencia que motivó la corrección.
- El runner ahora captura la carrera de proceso que sale entre snapshot y
  `taskkill /T /F`, realiza segunda búsqueda y solo acepta cleanup con cero
  procesos. `-SelfTest` PASS, incluido árbol padre/hijo.

Validación final después del fix:
`compileJava compileTestJava policyTest --rerun-tasks` — BUILD SUCCESSFUL;
`run-a5.ps1 -SelfTest` PASS; `lint_aldea.py --strict` OK;
`git diff --check` OK. A5-01..A5-12 normal lifecycle PASS, A5-09 malformed
PASS, Forestry Block 1 A1-A7 COMPLETE, ambos refunds excepcionales PASS.
C5 save/load también PASS (ver sección 20:20).

**Disposición:** Lumberjack Phase 1 ACCEPTED dentro del alcance. Atomicidad ante
caída abrupta de JVM/proceso y crash-window journals permanecen OUT OF SCOPE.
Siguiente: C2 — resource-to-repair accounting. Probar un bloque dañado y un
reparador, confirmar retirada exacta de un material almacenado y cambio real del
bloque; los intentos inválidos/sin material no deben retirar nada. A2/C4 sigue
bloqueado por la decisión del owner sobre el fixture de raid. No reabrir Phase 1
ni C5 sin regresión relevante. Guardados originales intactos; no usar `run/saves`.

---

## 2026-11-12 19:12 — BLOCK 1 REGRESSIONES PARCIALES (RUNS AUTOMÁTICOS)

Se retomó en CHRIS-PC sin repetir A5-01..A5-12 ni A5-09/11/12. Repo
`D:\Documents\DevilRpg-autonomous`, rama `feature/autonomous-village`,
HEAD `518fee13fc15bfb9b92b0479d86b5c609d1947da`; no commit/push.

Resultados nuevos, todos en copias desechables y mediante
`tools/a5-closure/run-a5.ps1`:

- **Run 62 / terminal obstruction — PASS:** `res-block1-regression-stuck5-20261112-184000`,
  scenario `lumber-stuck`. Muro cerrado instalado al asignar árbol; punto fallido
  detectado tick 803; nueva tarea/árbol asignado tick 823; XP 5->13, 6 troncos
  retirados, `terminal=true nextWork=true`. Result PASS, joined=true,
  ventana oculta sin activación, focusStealActions=0, remainingTestClients=0,
  clean=true. Los intentos stuck1..4 no sustituyen esta evidencia final.
- **Run 65 / recoverable wall-end route — PASS:** `res-block1-regression-recover-20261112-185000`,
  scenario `lumber-recover`. Ruta llegó al extremo del muro tick 618 sin
  registrar punto fallido; XP 6->18, 10 troncos retirados, recovered=true.
  Result PASS y shutdown clean; ventana oculta, cero acciones de foco robado.
- **Run 27 / Bone Meal growth — PASS:** `res-block1-regression-growth-20261112-185000`,
  scenario `lumber-xp`. XP 1->7, 6 troncos retirados, arboleda 40->36,
  Bone Meal almacén 32->0; sapling avanzó a LOG y `growthVerified=true`.
  Result PASS y shutdown clean; ventana oculta, cero acciones de foco robado.
- **Two-worker preemption/scheduler — SIN RESULTADO (fixture):**
  `res-block1-regression-scheduler-20261112-185000`, scenario
  `lumber-interrupt`. Primer intento `phase`: BLOCKED con grace antiguo de
  180 s. Reintento `phase-retry300`: sí entró al mundo, ventana oculta,
  `focusStealActions=0`, pero no escribió `result.json`; B ejecutó
  `VillagerGuardGoal`, no se observaron las aserciones de dos leñadores ni
  preempción y el cliente terminó con exit code 1. Shutdown limpio y cero
  clientes residuales. No usarlo como FAIL de producción ni como evidencia
  nueva sobre scheduler; Run81 histórico sigue PASS.

Reconciliación: Forestry Block 1 A1-A7 ya estaba COMPLETE según
`docs/autonomous-village/FORESTRY-BLOCK-1-ACCEPTANCE.md`; Runs 57/58/81 conservan
su evidencia histórica PASS. Esta sesión reejecutó equivalentes de Runs 62/65/27
y los tres dieron PASS. No quedan regresiones Block 1 pendientes.

Prueba extra `lumber-interrupt` (no requerida: Run81 ya PASS) tuvo dos intentos:
`phase` quedó BLOCKED por el grace antiguo de 180 s; `phase-retry300` sí entró
al mundo con ventana oculta y cero foco robado, pero no produjo `result.json`.
El worker B quedó bajo `VillagerGuardGoal`, no apareció la aserción
`[A5-WORKERS] distinctGoals/distinctTargets` ni `[A5-PREEMPT]`, y el cliente
terminó con exit code 1. Clasificar como fixture sin resultado, no como FAIL
del scheduler de producción. No repetir hasta aislar el fixture.

El runner fue endurecido: startup grace actual 300 s, filtrado de clientes por
hora de inicio del run y terminación de todo el árbol cmd/Gradle/runClient con
`taskkill /T`; `-SelfTest` verifica que no queden procesos hijos. Siguiente
paso: decisión de cierre de Phase 1 con los límites excepcionales documentados
(placement rechazado, Bone Meal API-false refund, crash atomicity); no repetir
A5 ni Block 1 sin cambio relevante. No usar `run/saves`, no tocar el guardado
original.

## 2026-11-12 17:25 — A5-12 RESOURCE ACCOUNTING CERRADO PARA CICLO NORMAL

A5-12 PASS (ciclo normal): se documentó ledger compuesto en `LUMBERJACK-PHASE-1-ACCEPTANCE.md`. Run dedicado `lumber-resource-deliver-save2/load2-20261112-162000`: mismo UUID `b89fcee1-c91a-4b1f-a9c4-66471b616288`, 16 logs carried `16->0`, warehouse `131->147`, accepted/delta `16`, `balanced=true`, tick 881. Ambas etapas entraron al mundo, ventana oculta, `focusStealActions=0`, cero clientes residuales, clean=true. A8-01-G ya aporta ledger completo: warehouse 64 +10 harvested/deposited -2 smith -4 cook =68; actor/warehouse/global delta 0, complete=true. Saplings: A5-02 0->3 leaf drops; A5-03 Run151 8->7 y bloque oak_sapling/hole1->0; A6-02 Run176 pantry1->0/worker0->1. Bone Meal: A5-04 Run156 16->15, API consumió1, no repitió. Caveats explícitos: no se forzó runtime de setBlock rechazado ni `growCrop` API-false refund; no se reclama crash atomicity.

**Siguiente:** Block 1 regressions/decision: runs 62/65/27/57/58/81. A5-01..A5-12 normal lifecycle y A5-09 están PASS; A5-11 reacquire claim tras reload ya está PASS.

## 2026-11-12 16:00 — A5-11 OVERLAP POST-RELOAD CERRADO + RECLAMACIÓN RECUPERADA

A5-09 quedó completamente PASS: planner bounds Ring/Cursor corruptos, hole fuera de dominio, sapling registry ID desconocido y reasignación válida tras rechazo, además de invalid controller y out-of-range target. Runs `lumber-invalid-payload-*` y `lumber-reassign-*` documentados en el acta A5.

A5-11 PASS: dos trabajadores persistieron el mismo árbol `98,98,0`. En reload, `ForestryTreeClaims` asignó el claim al worker `130d96cd-bf73-47d5-9013-18d759cf56cf`; el segundo `18a41d25-324c-45fb-8020-8c370ee0f375` recibió `RESTORE-DENIED`, quedó en SCOUTING sin Target. El árbol seguía LOGS. Run `lumber-overlap-save/load-20261112-160000`. Ambos shutdown clean, sin foco robado ni clientes residuales.

**Fix de producción:** `VillagerLumberjackGoal.cargarTarea()` ahora revalida y readquiere el claim transitorio de todo TALAR restaurado antes de permitir navegación/tala. Si el claim está ocupado o el target ya no es válido, limpia target, pasa a SCOUTING y persiste. Sin claim NBT nuevo ni registro global adicional.

`compileJava compileTestJava policyTest` PASS; `FORESTRY_TREE_CLAIMS_TEST` y `FORESTRY_TASK_POLICY_TEST` PASS. Siguiente: A5-12 resource accounting; después regresiones Block 1 y decisión de aceptación.

## 2026-11-12 14:42 — A5-09 MALFORMED CONTINÚA CERRANDO + EJECUTOR REVALIDADO

**A5-09 invalid controller enum: PASS.** Save `lumber-controller-save-20261112-143500`; reload `lumber-controller-load-20261112-143500`. El NBT real guardó `Controller=INVALID_CONTROLLER_STATE`; reload del mismo UUID `b89fcee1-c91a-4b1f-a9c4-66471b616288`: `goal=true taskSafe=true ok=true`, tick 600.

**A5-09 invalid target/out-of-range: PASS.** Save `lumber-range-save-20261112-143700`; reload `lumber-range-load-20261112-143700`. El target corrupto `1080,97,1000` no se conservó tras reload: `goal=true farTargetRetained=false taskPresent=true ok=true`, tick 600.

Los cuatro procesos de estas dos parejas terminaron con `joinedRequestedWorld=true`, ventana oculta, `focusStealActions=0`, `remainingTestClients=0`, `clean=true`. No se robó el foco. Evidencia en el acta A5.

A5-09 quedó completamente PASS en runtime: invalid controller enum, out-of-range target, planner Ring/Cursor corruptos, hole fuera de dominio, sapling registry ID desconocido y reasignación válida tras rechazo. Evidencia en `docs/autonomous-village/LUMBERJACK-PHASE-1-ACCEPTANCE.md`; runs `build/res-lumber-invalid-payload-20261112-154000` y `build/res-lumber-reassign-20261112-154000`. Siguiente: A5-11 overlap entre dos trabajadores después de reload; después A5-12 resource accounting y decisión/regresiones Block 1.

## 2026-11-12 14:35 — EJECUTOR A5 CORREGIDO Y REVALIDADO (sin foco ni intervención)

**Corrección de cleanup tras la primera prueba:** el primer smoke run encontró que el matcher amplio podía confundir un Java de Gradle con el cliente real; dejó un proceso `net.neoforged.devlaunch.Main` huérfano (PID 40892), que se terminó manualmente. No usar su `clean=true` como evidencia. `run-a5.ps1` ahora identifica exclusivamente `net.neoforged.devlaunch.Main` junto con `--quickPlaySingleplayer <Level>` para ocultar, cerrar, matar y verificar el cliente.

**Prueba final del contrato unattended:** `unattended-runner-smoke2-20261112-142720`, `lumber-profession-load`, PASS tick 1200; UUID `b89fcee1-c91a-4b1f-a9c4-66471b616288`. `shutdown.json`: cliente PID 676, `joinedRequestedWorld=true`, `clientWindowHiddenWithoutActivation=true`, `focusStealActions=0`, `remainingTestClients=0`, `clean=true`; una consulta independiente posterior confirmó cero clientes. Chrome siguió foreground; no hubo pausa por pérdida de foco. Runner SelfTest y matcher SelfTest PASS. Evidencia completa en el acta de aceptación.

**Regla permanente:** para todas las pruebas futuras usar `tools/a5-closure/run-a5.ps1`. No usar ESC, SendKeys, clics, `SetForegroundWindow` ni ninguna maniobra que active Minecraft. El runner marca `onboardAccessibility:true`, `pauseOnLostFocus:false`, `skipMultiplayerWarning:true`, `tutorialStep:none`; valida GameDir/Level y `--quickPlaySingleplayer`; oculta la ventana con `ShowWindowAsync(SW_HIDE)` sin activarla; exige login real; escribe result/shutdown JSON y verifica que el cliente de prueba desaparezca. Si no entra al mundo en 300 s, BLOCKED y aborta. No requiere intervención del usuario.

## 2026-11-12 14:30 — CORRECCIÓN PERMANENTE DEL EJECUTOR A5 (sin foco ni intervención)

**Regla obligatoria para todas las pruebas futuras:** usar `tools/a5-closure/run-a5.ps1` actualizado. No usar ESC, SendKeys, clics, `SetForegroundWindow` ni ninguna maniobra que active Minecraft. El runner marca `onboardAccessibility:true`, `pauseOnLostFocus:false`, `skipMultiplayerWarning:true`, `tutorialStep:none`; valida que init.gradle apunte al GameDir desechable correcto y use `--quickPlaySingleplayer A5WALL`; oculta la ventana Java con `ShowWindowAsync(SW_HIDE)` sin activarla; espera el login real y el result.json; y verifica shutdown/ausencia de clientes. Si no entra al mundo en 300 s, BLOCKED y aborta. No requiere intervención del usuario.

**Prueba runtime del runner:** `unattended-runner-smoke-20261112-142210`, escenario `lumber-profession-load`, PASS tick 1200; UUID `b89fcee1-c91a-4b1f-a9c4-66471b616288`. El log confirmó Quick Play/login; ventana oculta; WhatsApp siguió como foreground; no hubo `Saving and pausing game`; `shutdown.json` confirmó `joinedRequestedWorld=true`, `clientWindowHiddenWithoutActivation=true`, `focusStealActions=0`, `remainingTestClients=0`, `clean=true`. SelfTest del runner PASS. La limpieza del cliente necesitó terminación forzada tras escribir el resultado; no hubo acción del usuario. Evidencia en `LUMBERJACK-PHASE-1-ACCEPTANCE.md`, sección “A5 unattended launcher contract”.

## 2026-11-12 13:00 — TRASPASO DE SESIÓN / acceso directo a CHRIS-PC

**Forma de trabajo vigente:** esta sesión ya tiene acceso directo al equipo Windows
`CHRIS-PC` mediante Desktop Commander. No usar el producto/agente externo Harness ni
pedir al usuario que copie archivos, logs o resultados al chat. Leer, editar, ejecutar
y verificar directamente en `D:\\Documents\\DevilRpg-autonomous`. El usuario confirmó
expresamente que este es el flujo actual. Los archivos/script locales del repositorio
(`A5TestController`, `run-a5.ps1`, etc.) son artefactos del proyecto; no confundirlos
con el antiguo Harness externo. No asumir que el script está correcto: inspeccionarlo.

**Equipo/repositorio verificados:**
- PC: `CHRIS-PC` online; Windows; PowerShell; JDK de proyecto `C:\\jdk-21`.
- Repo: `D:\\Documents\\DevilRpg-autonomous`.
- Rama: `feature/autonomous-village`; HEAD: `518fee1`.
- Guardado original del jugador: no se usó ni modificó.
- No hay cliente Minecraft A5WALL desechable abierto (verificación 2026-11-12 12:53).
- No commit ni push. Preservar todos los cambios preexistentes y no limpiar archivos no rastreados.

**Tarea activa:** Lumberjack Phase 1, aceptación runtime del estado persistente de tarea
(`DevilRpgForestryTask v1`). La fuente de verdad es
`docs/autonomous-village/LUMBERJACK-PHASE-1-ACCEPTANCE.md`. A5-01/02/03/04/05/10 y
A5-09 missing-fields/invalid-phase ya tienen PASS: no repetirlos sin regresión.
A5-06, A5-07 y A5-08 quedaron COMPLETE/PASS el 2026-11-12; ver evidencia de save/reload en el acta de aceptación.

**Historial del bloqueo técnico de A5-06 (SUPERADO el 12-nov):**
- El primer intento no alcanzó TALAR/TRAVELLING: aldeano heredado en RECOGER_HARINA,
  arboleda incompleta y sin Bone Meal.
- Después se identificó que `run-a5.ps1 -GameDir` no configura Gradle
  `gameDirectory`; lo determina el `-Init` suministrado.
- Con init.gradle propio, FMLPaths GAMEDIR/MODSDIR/CONFIGDIR sí apuntaron a la copia
  `build/res-lumber-stale3-20261112-135000/game`, y existía
  `saves/A5WALL/level.dat`. Aun así Quick Play dejó el cliente en menú: faltaron
  `[A5] PREPARED`, login y `result.json`. El primer intento además tuvo options.txt
  malformado; se retiró solo de la copia desechable y se reintentó. Ambos clientes
  quedaron cerrados. No clasificar como PASS/FAIL de producto.

**A5-07 y A5-08 COMPLETE/PASS. Siguiente paso obligatorio: A5-09 — malformed variants restantes.**
- A5-07 PASS: FLETCHER→FARMER; el mismo UUID recargó sin tarea persistente ni Goal de leñador.
- A5-08 PASS: Objective guardado incorrecto descartado; Goal reinstalado y tarea re-scoped al objetivo actual.
- Launcher validado: clonar `stabilization-block1-scan-20261030-218`, init.gradle apuntando al GameDir de la copia, `saves/A5WALL/level.dat` presente y options.txt mínimo con `pauseOnLostFocus:false`.
- El parámetro `-GameDir` de run-a5.ps1 no define Gradle `gameDirectory`; el `-Init` lo hace. Verificar GAMEDIR y login en client.log.
- Continuar solo en copias `build/res-*`; nunca usar `run/saves` ni el guardado original. Conservar result.json/shutdown.json/logs.

**Validaciones al cierre:** `python tools\\lint_aldea.py --strict`, `git diff --check`;
compilar con `.\gradlew.bat compileJava` solo cuando el cambio lo requiera. No
hacer commit/push sin autorización. Revisar `git status --short` antes y después y
no descartar ninguno de los cambios listados.

## 2026-11-12 — Food Economy 3.1 COMPLETE; siguiente bloque Lumberjack Phase 1

Food Economy Block 3.1 queda COMPLETE/ACCEPTED. El acta incluye la ejecución final:
UUID/run IDs, resultados JSON, pruebas custom y vanilla, save/close/reload, ajustes del
A5TestController ignorado, límites exactos de lo probado, intentos de fixture sustituidos,
validación y regla anti-repetición. Ver
docs/autonomous-village/FOOD-ECONOMY-BLOCK-3.1-ACCEPTANCE.md, sección
“Ejecución final de cierre — 2026-11-12”.

No repetir la campaña Food 3.1, NO_FOOD, raciones, cosechas, sueño, 24k, SavedData ni
save/reload aceptados salvo regresión reproducible o cambio de producción que afecte esos
caminos. No reabrir Food 3.2, A6-03, A7-12, A8-01, A8-F03 ni Block 4.

SIGUIENTE BLOQUE ACTIVO: Lumberjack Phase 1 — aceptación runtime del estado de tarea
persistente (DevilRpgForestryTask v1). La implementación está hecha, pero el acta
LUMBERJACK-PHASE-1-ACCEPTANCE.md mantiene aceptación NOT ACCEPTED. Ejecutar una campaña
integrada A5 save/reload para fases, validaciones y regresiones Block 1. No volver a
investigar ni implementar lo ya aceptado: inventariar escenarios A5 existentes/faltantes,
completar los que falten sin fabricar estados imposibles, ejecutar en copias desechables,
corregir fallos reales y repetir hasta matriz final. Guardado original intacto.

Reconciliación vigente: A5-01, A5-02, A5-03 (Run 151/157), A5-04 (Run 156),
A5-05 (Runs 161/167), A5-06 (save `lumber-stale-save-final-20261112-134130` / load
`lumber-stale-load-refined-20261112-135148`), A5-10 (Run 135), A5-09 missing-fields
(Run 136) e invalid-phase (Run 137) tienen PASS y no deben repetirse sin regresión.
Siguen abiertos A5-07 profesión incorrecta, A5-08 aldea/objetivo incorrecto, variantes
malformed restantes, A5-11 solapamiento tras reload, A5-12 ledger integral y la decisión
final sobre regresiones Block 1 62/65/27/57/58/81.

A5-06 COMPLETE: save real TALAR/TRAVELLING a tick 702; UUID
`b89fcee1-c91a-4b1f-a9c4-66471b616288`; target `49,97,-17` era oak_log. En la copia de
reload se quitó el log y su soporte antes de abrir; tras 1200 ticks el mismo UUID quedó
SCOUTING, sin target ni navegación al objetivo viejo, cero drops y Goal presente. La
primera variante con solo el log en aire lo reinterpretó legítimamente como hueco PLANTAR;
fixture refinado, no bug de producción. Sin cambios de producción. Evidencia y paths en
`LUMBERJACK-PHASE-1-ACCEPTANCE.md`.

Launcher conocido como válido: clonar `stabilization-block1-scan-20261030-218`, crear
init.gradle apuntando al GameDir de la copia, mantener `saves/A5WALL/level.dat` y
options.txt mínimo válido (`pauseOnLostFocus:false`). `-GameDir` en run-a5.ps1 no define
Gradle gameDirectory; lo hace `-Init`. En el log verificar GAMEDIR y login. Detalle en
`LUMBERJACK-PHASE-1-ACCEPTANCE.md`.

Restricciones: no usar NBT inyectado como evidencia de transición real; las fases deben
alcanzarse por producción. Guardados normales; crash abrupto fuera del alcance aprobado.
Antes de cerrar: compileJava, compileTestJava, policyTest/targets, lint_aldea.py --strict,
git diff --check. Sin commit/push sin autorización expresa.






## 2026-11-12 — Food Economy 3.1 COMPLETE / ACCEPTED

A7-12 COMPLETE and A8-01 COMPLETE; A6-03 CLOSED and Block 4/D4-D5 COMPLETED. Food 3.2
remains CLOSED. **Food 3.1 is now COMPLETE**: deterministic no-food/idempotency/ration
boundaries PASS; lumberjack/miner work accounting PASS; both farmer harvest routes pass
operation-level exactly-once checks; normal sleep meal PASS; same-world save/close/reload
restores the villager's UUID, work and last-meal state; post-reload autonomous work, meal
transaction and custom harvest PASS. The vanilla `HarvestFarmland` route also passed with
no custom farm goal installed (XP63→64, work58→59, wheat age7→0). Two-village pantry
save/load isolation is covered by `FOOD_INTEGRATION_TEST`; server/Villager type guards
exclude client and non-villager entry. Full evidence is in
`docs/autonomous-village/FOOD-ECONOMY-BLOCK-3.1-ACCEPTANCE.md`.

Do not reopen Food 3.1, Food 3.2, A6-03 or Block 4 without a reproducible regression.
No commit or push was made; the original player save remains untouched.

## 2026-11-11 — A8-01 COMPLETA: ledger por actor, B2/S/G PASS; A7-12 COMPLETE
Implementado un ledger **default-off** (`-Ddevilrpg.resourcetrace=true`) con
`ForestryResourceTrace` (puro) + `ForestryResourceTraceMinecraft` (adaptador). Instrumenta
tala→inventario→almacén (`picarTronco`, `entregar`), recogida (`VillagerPickupGoal`) y el
consumo de leña del cocinero/herrero/minero (`VillageStorage.quitarLena(..., actor)`). Eventos
con ID único, `transferId`, worker/task/item/tick/dim/pos/resultado; `DEPOSIT` usa la cantidad
real (`before - remainder`), no asume todo-o-nada; `INVENTORY_INSERT` es informativo y no se
cuenta dos veces; límite 2048 eventos con `truncated=true` explícito. Ecuaciones por actor,
almacén y global; completo solo con deltas cero y sin `UNATTRIBUTED`.
Runtime PASS (copias A5WALL): **A** `res-a8a-20261110-220627` (5/5/5, final 0/5), **B**
`res-a8b2-20261110-221500` (A 5/5/5, B 5/5/5, almacén 10), **C**
`res-a8c2-20261110-222500` (parcial 5→insert5→deposit1→final4, almacén 63→64), **D**
`res-a8d7-20261110-225000` (pickup3→final3; **no existe ruta de retirada de troncos**), **E
OFF** `res-a8eoff2-20261110-225500` y **E AUDIT** `res-a8eaudit2-20261110-230000` (cada uno
5/5/5, completo). El primer B (`res-a8b-...`) detectó el +1 real: el **cocinero quemó un
tronco** del almacén (`warehouseBefore=4` tras depositar 5); se instrumentó `CONSUMED` y el B
final se aisló de otros consumidores.
**Estado intermedio (superado):** faltaba runtime B sin aislamiento con cocinero/herrero
activos, y no estaban atribuidos los movimientos de mantenimiento del almacén
(`repararCofresFlotantes`, remesa/migración) ni actores no autónomos. **B2** se intentó
(`res-a8b2live-20261110-231500`, `res-a8b2live2-...`, `res-a8b2live3-...`): el cocinero
`a445a483…` retiró 4 troncos y quemó 4 (o 1 en live3), atribuidos como `WITHDRAWAL-FUEL` +
`BURNED`, pero no se obtuvo reconciliación final completa (live3 salió sin informe; live1 +1).
**F: BLOCKED** (no se pudo disparar mantenimiento legítimamente); **G: NOT RUN** (requiere
instrumentar antes las retiradas de troncos del herrero y el mantenimiento). El herrero ya
registra `WITHDRAWAL` (movidas reales) y `BURNED` (solo tras consumir), pero no se ejecutó
runtime con herrero. **B2 live3** paró en `23:29:32` con `[Render thread] Stopping!` porque
**se solapó con live2**: al terminar live2, `run-a5.ps1` cerró todos los clientes `A5WALL`;
no fue un fallo del ledger. **B2 final** `res-a8b2final-20261111-023000`: PASS completo
(A 5/5/5, B 5/5/5, cook 4 retirados/1 quemado/3 finales, almacén 40+10-4=46, global 0). El
timeout de B2 ahora llama a `a8End` antes de `finish`. **S+** `res-a8smith-20261111-112000`:
PASS (1 retirado, 1 quemado, almacén 12→11, global 0). **S-** `res-a8smithneg-20261111-113000`:
PASS (sin retirada ni quema, events=0, completo). Eventos SYSTEM implementados y testeados
(`SYSTEM_CREATED/EXTERNAL_IN/REMOVED/DROP/RELOCATED`); remesa/reparar/migraciones
instrumentadas. **F queda BLOCKED como escenario post-baseline**: `repararCofresFlotantes`
solo se alcanza desde `VillageGenerator.asegurarAlmacen` durante la generación, antes del
baseline; remesa y migraciones también son inicialización. **G PASS**:
`res-a8gmixed-20261111-114000` (lumberjack 10/10, smith 2 retirados/1 consumido/1 final, cook
4 retirados/4 quemados, almacén 64+10−2−4=68, global 0). **A8-01 COMPLETA** para el scope
declarado; A7-12 no empieza.
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
integración es `b086193` (`feat(autonomous-village): coordinate forestry claims and goal
priorities`), publicado en `origin/feature/autonomous-village`.

### Próximo: A8-01 y A7-12 — definiciones exactas y orden

**A8-01 — End-to-End Forestry Resource Trace. Status: COMPLETE** (scope: autonomous
lumberjack/cook/smith flow, baseline after village initialization). Evidencia aceptada: A–E,
B2 `res-a8b2final-20261111-023000`, S+ `res-a8smith-20261111-112000`, S−
`res-a8smithneg-20261111-113000`, G `res-a8gmixed-20261111-114000`; A8-01 document.

**A7-12 — Controlled OFF/AUDIT Reproducibility and Final Acceptance. Status: COMPLETE**
(2026-11-11), con la anomalía AUDIT de Campaign 6 preservada como limitación no reproducida.
Campaign 9 abandono real: OFF `res-a7abandon3-off-20261111-224500` y AUDIT
`res-a7abandon3-audit-20261111-225000`; retirada ambiental del árbol A tras adquirir claim,
release + `NAV-GUARD ABANDON`, recuperación en árbol B, 5/5 y almacén+5 en ambos; AUDIT leases
`adde8dcc…`/`e6e8c150…` RELEASED. A8-01 sigue COMPLETE. OFF default; AUDIT diagnóstico; sin
transferencia de autoridad.

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
**Historical checkpoint (superseded 2026-11-10):** this paragraph listed A6-03 runtime and
Block 4/D4-D5 as next. A6-03 CLOSED (R2/R5-A/R5-D/R4-160/200/R6/R7 PASS) and D4/D5/Block 4
COMPLETED; they are not active. The active next block is Food Economy 3.1 residual.
Ver `LUMBERJACK-PHASE-1-ACCEPTANCE.md` §A6-03 y el Master Plan para la evidencia histórica.
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

Restored `tools/a5-closure/run-a5.ps1`: absolute a5.output, recursive validated result discovery, PASS-only
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


## 2026-11-12 22:40 — C4 RUN7 PASS; FINAL CALLBACK RUN8 ACTIVE

Run7 `c4-full7-20261112-0230` returned C4_PASS: 4/4 guards engaged; 12/12 living professionals resumed; 16 villagers survived; 0 casualties; 0 unresolved workers; JOB_SITE clean; 0 instrumentation failures. Run7 used the first cleric-cache variant and predates the final direct station-registration callback, so it does not validate the final callback variant.

The final `VillagerClericGoal.registrarSoporteAsignado(BlockPos)` callback and post-heal rest-window capture compiled successfully. Run8 `c4-full8-20261112-2240` is now running on a fresh disposable copy of the immutable repaired seed, dedicated server `runServer --nogui`, phaseA=3000, phaseB=6600, recoveryTicks=1200, port 25579. Output directory: `build/a2n/c4-full8-20261112-2240`. Seed SHA-256: `4D9516A994A6CFF1D50C9EA6667F2E4CD0CCB7EB96438F26815BDA4FCD94EEEF`. Monitor PID 4668 uses `consultar-c4.ps1 -RunId c4-full8-20261112-2240 -Wait -PollSeconds 15 -TimeoutMinutes 20`.

On reconnect, inspect run8 `fin.txt`, `launcher.log`, `game/logs/latest.log` and process/monitor state. Never relaunch the same RunId. Keep `tools/a2n/A2NavHarness.java.partial`, historical worlds and `run/saves` untouched. No commit/push. C4 remains OPEN pending run8.


## DevilRpg — Continuación C4 headless (2026-11-12)



## Continuación C4 — 2026-11-12 (run7 PASS; run8 en curso)

- `c4-full7-20261112-0230` terminó `C4_PASS`: 4/4 guardias adquirieron enemigo, 14 impactos y 4 bajas atribuidas a guardias; 12/12 trabajadores vivos y reanudados; 0 bajas y 0 UUID sin resolver; 16 aldeanos vivos; JOB_SITE 1/0/0; muro 992/992; 0 fallos de instrumento. Cinco muertes de atacantes confirmadas y tres retiradas para recuperación.
- `c4-full8-20261112-2240` es la repetición actualmente en curso en puerto 25579, mundo desechable independiente y seed reparado verificado por SHA-256. Fixture ya listo: 496 columnas, 992 celdas, 4 guardias equipados, 12 trabajadores/Goals, 8 atacantes registrados.
- Watcher local activo PID 29108: `tools\\a2n\\consultar-c4.ps1 -RunId c4-full8-20261112-2240 -Wait -PollSeconds 15 -TimeoutMinutes 30`. No relanza ni detiene el servidor.
- Si se corta la conexión de ChatGPT/Desktop Commander, los procesos locales continúan. Al reanudar, consultar dispositivo y el mismo RunId; leer `fin.txt`, `launcher.log` y `game\\logs\\latest.log`. No iniciar otra corrida mientras exista JVM asociada o falte inspeccionar el resultado. La conexión del asistente no se puede autoreconectar desde el modelo.
- C4 ya tiene un run PASS repetible; mantener la aceptación final pendiente de la repetición run8. Sin commit/push.


**Run8 live checkpoint (tick ~1183):** production wall 496/496 columns and 992/992 wall cells valid; 16 villagers; 4/4 guards equipped; 12/12 professions/Goals; 8 attackers registered; 4 distinct guard UUIDs engaged; 10 guard-hit events; 4 attacker deaths; 0 worker deaths and 0 workers-not-resumed so far. Server active, no `fin.txt` yet. Intermediate only; do not call C4 accepted until final verdict. Full detail in `A2-C4-HEADLESS-ACCEPTANCE-20261112.md`.


Run8 phase-A checkpoint at tick 2100: 5/8 attackers dead, 3 alive, 0 disappeared; at t=900 five had entered inside the wall. No worker casualties/unresolved workers had been observed at the earlier tick-1183 checkpoint. Full phase B and final recovery audit remain pending.


Run8 phase A reached t=3000: 5 attackers killed, 3 alive, 0 disappeared; no worker death or worker-not-resumed events. Phase B/recovery still pending; final verdict not yet written.


Run8 phase B checkpoint t=5100: 3 attackers alive, 5 killed, 0 disappeared; no worker-death or worker-not-resumed events. Continue through configured phase-B and recovery windows.


Run8 current counters at t≈5283: 4/4 distinct guards engaged, 12 guard hits, 5 attacker deaths, 0 worker deaths, 0 workers-not-resumed. No `fin.txt` yet; intermediate only.


Run8 t=6000: 3 attackers alive, 5 killed, 0 disappeared; perimeter entry ever=6/8 and currently inside=1. Worker-death/not-resumed counters remain 0; phase-B boundary and recovery audit pending.


Run8 phase B reached t=6600: 6 attackers dead, 2 alive, 0 disappeared, 0 inside wall. Recovery window of 1200 ticks and final audit pending; no worker-death/not-resumed events observed so far.


Run8 t=7200: 0 attackers alive, 6 confirmed dead, 2 disappeared/removed, 0 inside wall. Final defense/JOB_SITE audit still pending; recovery phase remains active.


Run8 t=7500: 6 confirmed attacker deaths + 2 removed/disappeared; no active attackers. Village farm/miner Goals continue ticking. Still phase B; final recovery and C4 audits pending.


## 2026-11-12 22:43 — C4 ACCEPTED; C1–C5 GATE CLOSED

Final callback validation `c4-full8-20261112-2240` returned persisted `C4_PASS`. Metrics: 4/4 guards engaged; 12/12 living professionals resumed; 16 villagers survived; 0 worker casualties; 0 unresolved UUIDs; profession Goals 12/12; JOB_SITE 1 assignment, 0 orphaned, 0 duplicate owners; 2 attackers removed for recovery; 15 guard-hit events; 6 confirmed attacker deaths (5 attributed to guards, 1 to iron golem); 0 instrumentation failures. Production wall: 496/496 columns and 992/992 cells present, wallOk=true, supportMissing=0, damagedColumns=0. Navigation trace: 25,494 records, 0 discarded, 0 failures.

Run used final direct cleric station-registration callback, a fresh disposable copy of immutable repaired seed, dedicated `runServer --nogui`; no client/GUI/focus interaction. Server saved all dimensions/chunks and stopped normally; `fin.txt` and `launcher.log` persisted. Launcher: no JVM remaining for RunId. Gradle BUILD SUCCESSFUL (7m54s), exit 0. `lint_aldea.py --strict` OK; `git diff --check` exit 0 (line-ending advisory warnings only).

C4 is ACCEPTED. C1/C2/C3/C4/C5 are now accepted; the C1–C5 gate is closed. Block 6 multi-day soak is the next gated task, not yet executed. Repo `D:\\Documents\\DevilRpg-autonomous`, branch `feature/autonomous-village`, HEAD `518fee13fc15bfb9b92b0479d86b5c609d1947da`. No commit/push; all pre-existing working-tree changes preserved. Protected `A2NavHarness.java.partial` hash unchanged: `2C3EBA9ED6E936B595E26650B796A624838B5CB4FF6CFD7B1804BE7A13552127`. Full run report: `docs/autonomous-village/A2-C4-HEADLESS-ACCEPTANCE-20261112.md`.


## C4 — cierre con aceptación repetible (2026-11-12)

- **C4 ACCEPTED** tras dos ejecuciones independientes consecutivas PASS: c4-full7-20261112-0230 y c4-full8-20261112-2240.
- Run8: 4/4 guardias, 15 impactos, 5 bajas atribuidas; 6 muertes de atacantes y 2 retirados para recuperación; 12/12 trabajadores vivos/reanudados; 0 bajas y 0 UUID sin resolver; 16 aldeanos vivos; JOB_SITE 1/0/0; muro 992/992; 0 fallos; apagado y guardado normales; BUILD SUCCESSFUL 7m54s.
- Evidencia y SHA-256 completos en docs/autonomous-village/A2-C4-HEADLESS-ACCEPTANCE-20261112.md, sección 9. Logs bajo build/a2n/c4-full8-20261112-2240/.
- El watcher consultar-c4.ps1 terminó al encontrar fin.txt; no hay JVM del run8 activa. Si vuelve a cortarse la sesión remota, el proceso local continúa y se recupera consultando el mismo RunId; el modelo no puede reconectar por sí solo una conversación caída.
- C4 cerrado; A2 global y soak multi-día siguen abiertos según sus propios gates. No commit/push; preservar todo el worktree.


## 2026-11-12 22:47 — A2 custom-navigation attempt 1 ACTIVE

RunId `a2n-20261112-224753`, first of the three distinct-seed A2 attempts. Existing A2 launcher invoked with `-Modo raid -NavCustom true -MundoPreparado build/a2n/arena-repair1-20261112-205206/game/a2n -LimiteMinutos 30 -FaseA 3000 -FaseB 6600 -Presupuesto 900`. It copies the immutable repaired seed to `build/a2n/a2n-20261112-224753/game/a2n`. Trace flag added to ignored `build/a2n/lanzar-a2n.ps1` as `-Ddevilrpg.a2n.traceNav=true`; PowerShell parser PASS. Monitor PID 19196; server JVM PID 17180. On reconnect inspect same run's `fin.txt`, `srv.log`, `srv.log.err`, `nav-trace.log`; verify trace header `navCustom=true`. Do not relaunch. A2 contract remains 8/8 in three distinct seeds; this is attempt 1 only. C4 separately ACCEPTED. Preserve `.partial`, no historical worlds/`run/saves`, no commit/push.


### A2 launcher preflight correction — 2026-11-12 22:49

Run `a2n-20261112-224753` closed as `INVALIDO (raid) - RAID FIXTURE INVALIDO` before any attacker spawn. The log identified `C4 MAINTENANCE ERROR: IllegalStateException: A2 maintenance hook disabled`; the A2 launcher had omitted Gradle property `-Pa2nMaintenanceHook=true`, which the C4 launcher already supplies. This was a launcher/configuration preflight failure, not a navigation or production AI failure and is **not counted** among the three A2 seed attempts. Added `-Pa2nMaintenanceHook=true` to the ignored A2 launcher. PowerShell parser PASS. Do not relaunch the same RunId.

The corrected new RunId `a2n-20261112-224926` is active (seed 12345, prepared-world copy, `-NavCustom true`, trace enabled). Launcher monitor PID 24192; server JVM PID 29700. Wait for its trace header and final `fin.txt` before deciding whether this is valid attempt 1.


### Run a2n-20261112-224926 — live checkpoint (raidTick≈320)

Verified trace header `traceNav=true navCustom=true`; `NAVCUSTOM` log confirms `devilrpg.nav.custom=true`. At raidTick≈320, 8 attackers remain tracked. Trace shows idx1/2/5/6 in TRAVERSING, idx3/4 PLANNING, idx7/8 FAILED at this snapshot; idx5 has four natural DROP attempts with zero failures. A real wall breach was logged at `620,72,580` (oak log). idx7's local planner reported `tope de escalones locales alcanzado (24)` toward `701,71,483`, followed by terminal rearm attempts and a placed step. This is intermediate evidence only; do not declare pass/fail until the full raid and final result.

The initial A2 launcher limit was 30 minutes, potentially too short for custom-nav ticks. The script's default was raised to 90 minutes. To preserve this live run, its original 30-minute PowerShell monitor PID 24192 was stopped without terminating the Gradle/server descendants; server PID 29700 remains alive. A separate non-terminating watcher PID 39808 now waits up to 120 minutes for `fin.txt` and then verifies save/shutdown. On reconnect, inspect the same RunId and watcher-status.txt; do not relaunch.


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

## 2026-11-13 — checkpoint after A2 run9
- Run8 ended C4_PASS (4/4 guards, 12/12 workers resumed, 0 casualties); A2 only 5/8 perimeter entry.
- Run9 ended A2 7/8 perimeter entry, but idx4 was invalidly spawned inside a mountain: harness created a one-column platform at 566,95,533 although the real surface is y=121 (spawn y=122). The stair search correctly rejected all surrounding solid cells. This is a harness fixture defect, not grounds to expand stair search blindly.
- Run9 C4_FAIL: 4/4 guards fought, 12/12 workers alive, 0 casualties, but only 9/12 resumed. Missing: toolsmith at 632,62,450 (87.4 from center), fisherman at 607,72,421 (123.9), shepherd at 607,72,422 (122.5). Investigate wandering beyond village radius / missing job sites; do not weaken acceptance.
- Applied: stair candidate search now interleaves vertical levels by horizontal Manhattan shell; permits only one forward, adjacent, same-height first side step; tracks the pre-first-stair anchor with the mob's real position until a stair is placed. Keeps candidate cap 60, max stairs 24, support/headroom/protection and physical traversal checks.
- Applied to A2 harness: surface search top-down from centerY-8 to centerY+80; removed artificial one-column spawn fallback. No surface means explicit spawn failure. Harness source copies are identical.
- Verification: policyTest compileJava -Pa2nDiagnostics=true BUILD SUCCESSFUL; lint strict PASS; diff check PASS; launcher parser PASS; harness copies identical.
- Next run: run10 on disposable seed 12345, same phase/budget settings. Confirm idx4 spawns at surface y=122, no artificial platform, and whether it reaches a cliff edge / builds and traverses stairs. C4 still needs repair for 3 non-resumed workers. HEAD 518fee13fc15bfb9b92b0479d86b5c609d1947da, no commit, preserve all modifications.

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

## 2026-11-13 - A2 checkpoint after Run17
- Branch feature/autonomous-village; HEAD 518fee13fc15bfb9b92b0479d86b5c609d1947da; no commit. Preserve all existing working-tree modifications.
- Run16 a2n-20261113-022432: A2 6/8 ever entered, 0 inside at final; C4_PASS.
- Run17 a2n-20261113-095622: A2 4/8 ever entered, 0 inside at final; C4_PASS. No server JVM remains.
- Two concrete navigation defects remain: (1) pending stair target can be marked reached by the 2.25 distance-squared tolerance while the zombie is still in the adjacent block; (2) DROP landing 569,116,531 repeats against the leaf obstruction at 569,118,531, despite the destination-wide exclusion patch.
- Run18 patch is applied and verified: strict physical occupancy for pending stair nodes; exclusion diagnostics DROP_DESTINATION_EXCLUDED, DROP_DESTINATION_EXCLUSION_HIT, DROP_DESTINATION_EXCLUSION_STALE, DROP_REJECTION_NO_COLUMN_BLOCK.
- Verification: policyTest + compileJava + compileTestJava with -Pa2nDiagnostics=true BUILD SUCCESSFUL; all policy tests PASS; lint strict PASS; diff check PASS; runtime bytecode verified.
- Next: run18 on disposable seed 12345 with the same phase/budget settings. Do not edit source while the server is active; wait for fin.txt and JVM exit. See docs/autonomous-village/A2-WALLED-RAID-HANDOFF.md for exact acceptance observations.

## 2026-11-13 - A2 checkpoint after Run18 / Run19 patch

- Branch feature/autonomous-village; HEAD 518fee13fc15bfb9b92b0479d86b5c609d1947da; no commit.
- Run18 a2n-20261113-101103: A2 4/8 ever entered, 0 inside at final; C4_PASS; no server JVM remains.
- Global destination exclusion is proven: 1 exclusion registered, 9 global planner hits, 0 stale fingerprints, 0 missing-column detections.
- Remaining DROP defect: local A* received the exclusion policy but did not apply it. Run19 patch adds the same filter to local expansion and DROP_LOCAL_EXCLUSION_HIT / DROP_LOCAL_EXCLUSION_STALE events.
- Remaining stair defect: pending step 608,93,597 is not accepted as waypoint because esPieValido() returns false for target 608,94,597. Run19 patch logs the exact failing predicate and block states; do not weaken safety checks speculatively.
- Run19 static gates: policyTest + compileJava + compileTestJava BUILD SUCCESSFUL; all policy tests PASS; lint strict PASS; diff check PASS; runtime bytecode verified.
- Next: Run19 on disposable seed 12345 with same phase/budget settings. Wait for fin.txt and JVM exit before further edits. Full handoff: docs/autonomous-village/A2-WALLED-RAID-HANDOFF.md.

## 2026-11-13 - Run19 completed / next focus is stair movement handoff

- RunId a2n-20261113-102344 completed normally; fin.txt exists; server JVM exited.
- A2 remains OPEN / NOT ACCEPTED: t=6600, 4/8 attackers entered ever, 0 inside at close. #270 and #271 stalled on pending stairs.
- Local DROP exclusion works: 807 DROP_LOCAL_EXCLUSION_HIT, 0 stale.
- Pending stair: 143 waypoint selections, 0 rejected, 0 STAIR_NODE_PHYSICALLY_REACHED. One earlier physical stair event for #270 at 595,95,600 is real but only an intermediate step; #270 later stalled at 608,93,597. #271 stalled at 588,108,523.
- C4_PASS: 12/12 workers alive/resumed, 0 worker casualties, 0 unresolved, 16 survivors, 4 attackers removed for recovery, jobSite [2,0,0], 0 instrumentation failures.
- Next investigation: selected pending waypoint is not translated into successful movement. Trace exact moveTo handoff, path acceptance, later overrides/stops, active goal, and actual position delta. Do not loosen protection/headroom/support/arrival checks.
- Full details and Run20 plan: docs/autonomous-village/A2-WALLED-RAID-HANDOFF.md.

## 2026-11-13 - Run20/Run21 checkpoint

- Run20 validated the pending-stair proximity guard at runtime; A2 t=6600 was 5/8 entered ever, 0 inside; C4_PASS.
- Run21: A2 t=6600 7/8 entered ever, 0 inside; t=7500 0 alive, 6 dead, 2 disappeared, 7 entered ever, 0 inside. A2 remains NOT ACCEPTED.
- Run21 C4_PASS: 12/12 workers alive/resumed, 0 casualties, 0 unresolved, 16 survivors, 2 attackers removed for recovery, jobSite [1,0,0], 0 instrumentation failures.
- Run21 trace: 550919 records, 0 dropped, 0 failures. DROP_LOCAL_EXCLUSION_HIT=813, stale=0; global exclusion hit=3234, stale=0. Proximity suppressions=4.
- Three legacy stair traversals were physically verified. Four pending stair rejections for #268 were solely because target 588,106,523 was outside local window after the entity fell to about 587,96,510.
- BREAK action objective policy added; policyTest, compileJava, compileTestJava, lint strict, and diff check passed before Run21. It prevented valid BREAK nodes from being rejected merely because they are not standing cells.
- Next: diagnose fall/step-anchor desynchronization and whether to invalidate or replan an unreachable pending stair. Keep physical arrival and safety checks strict. Full details: docs/autonomous-village/A2-WALLED-RAID-HANDOFF.md.


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


## Continuación A2 — Run22 y corrección precolocación

- Run22 `a2n-20261113-110548` terminó 11:14:50; C4 PASS (12/12 trabajadores reanudados, 0 bajas/no resueltos); A2 FAIL/OPEN (4/8 atacantes entraron alguna vez, 0 dentro al cierre).
- Hallazgo: #270 sí recibió `MOVE_COMMAND`; el bloqueo probado fue una columna de caída ocupada en `589,97,512` por cobblestone de puente del asedio. El bloque era obra protegida (`BLOQUEADO_OBRA_DEL_ASEDIO`), no muro y no debía picarse.
- La búsqueda de escalones comprobaba solo dos huecos superiores, no toda la columna de caída. Corregido: `NavDropColumnPolicy` + validación previa en `AggressiveZombieEntity.evaluarCeldaDeEscalon`, reutilizando los predicados de `NavExecutor`.
- Suite `policyTest compileJava compileTestJava -Pa2nDiagnostics=true`: BUILD SUCCESSFUL. `lint_aldea.py --strict` y `git diff --check`: PASS.
- Siguiente: Run23 fresco, mismos fixture/semilla/parámetros. Verificar rechazo antes de colocar el candidato con columna bloqueada y progreso real. No aceptar A2 aún.


## Run23 — resultado y siguiente paso

- Run23 `a2n-20261113-112455` terminó 11:34:10. C4 PASS: 12/12 trabajadores reanudados, cero bajas/no resueltos. A2 FAIL/OPEN: 5/8 atacantes entraron alguna vez; 0 dentro al cierre. Trace: 791,069 registros, 0 fallos de instrumento según fin.
- Validación nueva sí rechaza candidatos de peldaño cuya columna ya está bloqueada (Y=97/99 y Y=76/77). No impide que un puente posterior invada una columna que estaba libre al colocar el peldaño.
- Caso #270: escalón `589,93,512` colocado 11:31:45; a 11:31:50 aterrizaje bloqueado por cobblestone `589,97,512`, clasificado como obra del asedio. No está demostrada la identidad del constructor.
- Run24 añade solo log `PUENTE_BLOQUE` por cada cobblestone colocado por `puentearHacia` (UUID, pos, origen, objetivo). Próximo paso: correlacionar autor de `589,97,512` con la ruta DROP. No hacer aún un cambio de lógica.


## Run24 — atribución de cada bloque de puente (EN CURSO, 2026-11-13)

- RunId: `a2n-20261113-113725`.
- Fixture fuente: `build\a2n\arena-repair1-20261112-205206\game\a2n`; copia desechable nueva en `build\a2n\a2n-20261113-113725\game\a2n`.
- Seed 12345; raid; NavCustom=true; Fase A=3000; Fase B=6600; presupuesto local=900; global=8000; límite 90 min.
- Launcher iniciado 11:37:26; PID servidor reportado 16328.
- Cambio de código exclusivo de diagnóstico: cada colocación en `puentearHacia` emite `PUENTE_BLOQUE` con UUID del asaltante, posición, origen, objetivo y presupuesto. Sin cambios de lógica de movimiento/puente.
- Esperar `fin.txt` y salida de JVM antes de modificar fuentes. Correlacionar `PUENTE_BLOQUE pos=589, 97, 512` con peldaños y DROP del UUID #270. No ejecutar Gradle ni editar mientras el servidor esté vivo.


## Run24 — cierre forense

- Run24 `a2n-20261113-113725` terminó 11:46:45; C4 PASS (12/12 trabajadores reanudados, 0 bajas/no resueltos); A2 FAIL/OPEN (5/8 entraron alguna vez, 0 dentro al cierre). Trace: 574,409 registros, 0 descartados, 0 fallos.
- Causa raíz exacta: #271 colocó puente `589,97,512` desde `588,97,512` a las 11:44:19; a las 11:44:25 el mismo atacante reportó DROP de escalón `589,93,512` bloqueado precisamente en Y=97. El constructor de puentes invade su propia columna de caída.
- Próximo cambio: política pura para rechazar puente en misma X/Z y rango vertical de la columna de caída del último escalón verificado; intentar otro lado. Mantener intacta protección de puentes y árboles. Añadir pruebas. No se ha aplicado todavía; no commit.


## Corrección puente/escalera posterior a Run24

Aplicada y compilada: se registran los peldaños descendentes construidos por cada asaltante; `puentearHacia` rechaza un tablón que ocupe la columna DROP de cualquiera de esos peldaños y prueba el siguiente lado. La política pura cubre mismo X/Z y rango vertical de caída segura. No rompe ni borra tablones existentes. La validación previa de peldaño sigue activa para la secuencia inversa.

Suite `policyTest compileJava compileTestJava -Pa2nDiagnostics=true`: BUILD SUCCESSFUL in 29s. Lint y diff check PASS (advertencias CRLF). Próximo: Run25 fresco; buscar `PUENTE_RECHAZADO_COLUMNA_ESCALERA` y verificar descenso/avance. A2 OPEN.


## Run25 — validación de exclusión bilateral de columnas (EN CURSO, 2026-11-13)

- RunId: `a2n-20261113-115000`.
- Fixture fuente: `build\a2n\arena-repair1-20261112-205206\game\a2n`; copia desechable nueva en `build\a2n\a2n-20261113-115000\game\a2n`.
- Seed 12345; raid; NavCustom=true; Fase A=3000; Fase B=6600; presupuesto local=900; global=8000; límite 90 min.
- Launcher iniciado 11:50:00; PID servidor reportado 36784.
- Cambio funcional: registrar peldaños descendentes verificados por asaltante y rechazar un puente que invada la columna DROP de cualquiera de ellos; el lado rechazado se omite y se prueban otros lados. Evento esperado: `PUENTE_RECHAZADO_COLUMNA_ESCALERA`.
- Esperar `fin.txt` y salida de JVM antes de editar. Confirmar el rechazo para `589,97,512` o la coordenada equivalente; comprobar que el bloque no se coloca y que el atacante elige alternativa. No tocar puentes existentes ni árboles.


## Run25 — resultado y siguiente bloqueo

Run25 `a2n-20261113-115000` terminó 11:59:01. C4 PASS (12/12 reanudados, 0 bajas/no resueltos); A2 permanece OPEN (sin umbral formal acordado): 7/8 atacantes entraron alguna vez, hubo presencia dentro durante A y B, pero 0 dentro al cierre. Trace 565,242 registros, 0 descartados/fallos.

La corrección bilateral puente/escalera pasó en runtime: #269 rechazó puente `616,77,597` por escalón `616,73,597`; #270 rechazó dos veces `589,97,512` por escalón `589,93,512` y colocó alternativas, descendiendo hasta Y=79.

Siguiente diagnóstico: #270 agotó replans en `588,79,507`. `MOVE_COMMAND` pidió BREAK approach `590,76,507`, pero Navigation reportó Path target `590,87,507` (2 nodos). Run26 instrumentará los primeros nodos y su colisión para distinguir ruta válida, obsoleta o no transitable. No tocar puente/escalera sin nueva evidencia.


## Run26 — Path nodes y colisiones de BREAK approach (EN CURSO, 2026-11-13)

- RunId: `a2n-20261113-120306`.
- Fixture fuente: `build\a2n\arena-repair1-20261112-205206\game\a2n`; copia desechable nueva en `build\a2n\a2n-20261113-120306\game\a2n`.
- Seed 12345; raid; NavCustom=true; Fase A=3000; Fase B=6600; presupuesto local=900; global=8000; límite 90 min.
- Launcher iniciado 12:03:06; PID servidor reportado 26508.
- Diagnóstico agregado a `MOVE_COMMAND`: primeros cuatro nodos de Path desde nextIndex, estado de bloque en feet/head/support, canReach y celda solicitada con sus tres bloques. Solo instrumentación; sin cambio de navegación.
- Esperar `fin.txt` y salida de JVM antes de editar. Para #270 (idx4), correlacionar orden `ACERCARSE-ACCION` a `590,76,507` con Path target y nodos reales. No tocar la corrección puente/escalera validada en Run25.


## Run26 — resultado y próximo bloqueo

Run26 `a2n-20261113-120306` terminó 12:12:24. C4 PASS (12/12 reanudados, 0 bajas/no resueltos). Fase A: 3/8 entraron alguna vez, 0 dentro al cierre. Fase B: 5/8 entraron alguna vez y 1 atacante vivo permaneció dentro al cierre (idx8, salud 8); A2 sigue OPEN. Trace 624,095 registros, 0 descartados/fallos.

El nuevo Path diagnosticó BREAK approach imposible: #262 (idx3) solicita moverse al bloque sólido `615,74,598`, pero PathNavigation devuelve `canReach=false` y un Path target alejado/alto (p. ej. `611,94,598` al solicitar `611,82,598`). El mob queda en `615,77,597`. No cambiar permisos de rotura.

Run27: si Path a BREAK no es alcanzable, raycast al primer bloque en línea; si es distinto del objetivo y está al alcance, delegarlo como BREAK en la RecoveryAction existente, que conserva BlockInteractionPolicy. Verificar rotura, invalidar ruta local y replanificar. Política pura `NavActionApproachPolicy` y test añadidos. A2 OPEN.


## Corrección BREAK approach tras Run26

Aplicada: si un Path a un nodo BREAK devuelve `canReach=false`, raycast desde los ojos del mob al objetivo. Si el primer bloque impactado es distinto del objetivo y está al alcance, se delega como BREAK override al RecoveryAction existente; se conserva BlockInteractionPolicy y después se replanifica. Evento `BREAK_APPROACH_OCCLUSION`. Path trace ahora incluye primeros cuatro nodos y estados feet/head/support. Nueva política/test puros.

Build/policyTest PASS; lint y diff check PASS. Run27 fresco con mismos parámetros validará rotura autorizada del primer obstáculo, replan y protección de árboles/tablones. A2 OPEN.


## Run27 — BREAK approach / primer obstáculo visible (EN CURSO, 2026-11-13)

- RunId: `a2n-20261113-121920`.
- Fixture fuente: `build\a2n\arena-repair1-20261112-205206\game\a2n`; copia desechable nueva en `build\a2n\a2n-20261113-121920\game\a2n`.
- Seed 12345; raid; NavCustom=true; Fase A=3000; Fase B=6600; presupuesto local=900; global=8000; límite 90 min.
- Launcher iniciado 12:19:20; PID servidor 14004.
- Corrección: si Path a BREAK es inalcanzable y raycast impacta primero un bloque distinto del objetivo dentro del alcance, delegar ese bloque como BREAK override al RecoveryAction existente; BlockInteractionPolicy no cambia. Evento esperado `BREAK_APPROACH_OCCLUSION`.
- Build/policyTest compileJava compileTestJava PASS (29s); lint y diff check PASS.
- Esperar `fin.txt` y salida JVM antes de editar. Validar el primer obstáculo autorizado, replanificación, no destrucción de árboles/tablones y progresión A2. A2 OPEN.


## Run27 — guardia intercepta BREAK approach

Run27 terminó 12:28:21. C4 PASS (12/12 reanudados, 0 bajas/no resueltos). A2 OPEN: 5/8 entraron alguna vez, 0 dentro al cierre de A y B; 3 vivos fuera. Trace 1,020,886 registros, 0 descartados/fallos.

El raycast generó 30 `BREAK_APPROACH_OCCLUSION`, pero 24 fueron interceptados por `rechazoPorGuardaDeRuta`: `exencionBreak=false celdaActiva=true rechaza=true`. La celda de obstrucción es adyacente al nodo BREAK activo, y la guarda la considera parte de la ruta. El callback no recibe la acción.

Corregido: nueva `NavRecoveryGuardPolicy`; exención solo si `nodoDeRecuperacionOverride` coincide exactamente con la celda de recuperación. La acción sigue pasando por RecoveryAction/BlockInteractionPolicy. Test añadido. Run28 validará `exencionOverrideBreak=true`, `rechaza=false` y rotura autorizada. A2 OPEN.


## Run28 — exención exacta del BREAK override en la guarda (EN CURSO, 2026-11-13)

- RunId: `a2n-20261113-123130`.
- Fixture fuente: `build\a2n\arena-repair1-20261112-205206\game\a2n`; copia desechable nueva en `build\a2n\a2n-20261113-123130\game\a2n`.
- Seed 12345; raid; NavCustom=true; Fase A=3000; Fase B=6600; presupuesto local=900; global=8000; límite 90 min.
- Launcher iniciado 12:31:30; PID servidor 20852.
- Corrección: `NavRecoveryGuardPolicy` exime solo el override BREAK exacto; `guardaDiag` expone `exencionOverrideBreak`. RecoveryAction/BlockInteractionPolicy siguen siendo obligatorios.
- Build/policyTest compileJava compileTestJava BUILD SUCCESSFUL in 28s; lint y diff check PASS.
- Verificar que el evento BREAK_APPROACH_OCCLUSION sea seguido por `exencionOverrideBreak=true`, `rechaza=false`, acción callback y resultado verificado. No romper árboles/tablones protegidos. Esperar fin.txt y salida de JVM antes de editar.


## Run28 — resultado y siguiente bloqueo

Run28 `a2n-20261113-123130` terminó 12:41:15. C4 PASS: 16 supervivientes; 12/12 trabajadores vivos/reanudados; 0 bajas/no resueltos; 2 retirados para recuperación; 0 fallos de instrumento. Trace 664,902 registros, 0 descartados/fallos.

A2 OPEN: fase A 5/8 entraron alguna vez, 0 dentro al cierre; fase B 6/8 entraron alguna vez, 0 dentro al cierre. Fase B cerró con idx4 vivo en 589,93,511 (distancia 74.98) e idx8 vivo en 753,63,543 (distancia 93.35), ambos fuera. Después, el harness completó mantenimiento.

BREAK approach: 22 detecciones; 14 estados de guarda con `exencionOverrideBreak=true/rechaza=false`, 0 rechazos de override; 991 BREAK-VERIFICADO (965 actuo, 26 no-actuo). La excepción exacta está validada y debe conservarse. No ampliar permisos ni modificar BlockInteractionPolicy.

Siguiente: investigar navegación posterior a BREAK y progreso vertical/global de idx4 e idx8; no tocar la excepción validada. A2 OPEN.


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
- Next: instrument scalonarObjetivoLocal/
allar around the same tick with the counter value before/after and every write; fix the actual overwrite only.

## 2026-11-14 00:55 — Exact 0→64 writer identified; no safe fix applied

- Run 2n-20261114-004219 (focused FaseB6000). idx3 UUID 40f10aeb-….
- Trace shows A2_LOCAL_STAIR_ENTER local=0/64, then A2_LOCAL_STAIR_WRITE op=INC_STEP new=1..64 every ~2 ticks, each selecting 608,95,601 while mission is 616,71,578.
- Writer: NavExecutor.escalonarObjetivoLocal() line 1778 scalonesDeObjetivo++. The candidate is accepted despite not descending and while the mission remains below; the counter therefore counts non-progressing local steps.
-
allosDeEscalonDeObjetivo reaches 10 via the repeated failure path once the 64 limit is hit (scalonarObjetivoLocal sets it to MAX at line 1755).
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
- Full 2n-20261114-033543 (FaseB12000): completed with fin; spawneados=8/8,
ivos=4, muertos=4, ntraronPerimetroEver=4, dentroParedAhora=0, C4 PASS.
- Full result: A2 FAIL criteria 2/3/4; survivors idx3 617,86,600; idx4 588,79,507; idx7 698,62,454; idx8 701,62,457, all outside.
- Retreat resolver did select navigable destinations (336 candidates evaluated; RETREAT_DESTINATION_SELECTED), but did not produce enough surviving entrants over 12000 ticks.
- C4 exact: guards fought, 12/12 workers alive/resumed, 0 casualties, jobSite [9,0,0], 4 removed for recovery, 0 instrument failures.
- Next: use the completed run to separate combat-death and end-to-end approach failures; do not alter threshold/C4. Vertical JUMP/BUILD remains pending for idx8.

## 2026-11-14 04:05 — BREAK vertical reach gate implemented; focused partial PASS

- Existing NavExecutor already has a bounded JUMPUP for dy=2 cardinal jumps (validates corridor, landing, support, fits; 40-tick timeout).
- Added NavBreakReachPolicy (MAX_STEP_UP=2, action-reach distance) and integrated it in local BREAK waypoint selection. Unreachable vertical BREAK nodes are rejected with VERTICAL_TRANSITION_REJECTED reason=OUT_OF_REACH; mission/route replan continues.
- Policy test NAV_BREAK_REACH_POLICY_TEST 4/4.
- Focused run 2n-20261114-035351 completed with fin: C4 PASS, ntraron=6/8,
ivos=2, dentroParedAhora=0.
- idx8 advanced from historical 701,62,457 to 672,72,550 (dist 20.9) and was no longer stuck on the unreachable BREAK waypoint; it later died in combat.
- No general upward BUILD or new JUMP height was added; idx8's dy=5 case is resolved by rejecting the waypoint/replanning, not by climbing.
- A2 remains FAIL (no inside survivor at close); C4 PASS. Next: inside-survival/combat and, if required, general BUILD ascent.

## 2026-11-14 09:45 — FINAL full A2 run (12000): FAIL, C4 PASS

- Run 2n-20261114-093155, completed normally with fin.txt.
- spawneados=8/8,
allosSpawn=0.
-
ivos=3, muertos=5, desaparecidos=0, ntraronPerimetroEver=5, dentroParedAhora=0.
- Survivors: idx3 617,86,600; idx4 588,79,507; idx7 714,60,471 — all outside, all PLANNING (no terminal FAILED).
- A2 FAIL: criterion 2 (5/8 < 6), criterion 3 (0 inside), criterion 4 (no external survivor progress to enclosure in final window; idx3/idx4 unchanged).
- C4 PASS exact: guards fought, 12/12 workers alive/resumed, 0 casualties, jobSite [10,0,0], 3 removed for recovery, 0 instrument failures.
- idx8 died during the raid; BREAK reach gate prevented the old terminal FAILED loop but did not produce an inside survivor.
- Next: only correction(s) supported by the completed run's per-attacker evidence; no threshold/C4 change. BUILD ascent remains not implemented.

## 2026-11-14 10:30 — Interior group trigger + villager flee speed: focused FAIL

- Workstream A: added AttackerThreatPolicy (2+ guards within 8, health <95%) and integrated GRUPO retreat cause. Test 5/5.
- Workstream B: added VillagerFleeSpeedPolicy; flee speed reduced exactly 30% (1.35 -> 0.945). Test 2/2. Only VillagerFleeGoal.VELOCIDAD uses it; normal work/guard speeds untouched.
- Focused run 2n-20261114-101748 completed with fin: C4 PASS, spawneados=8/8,
ivos=3, muertos=5, ntraronPerimetroEver=5, dentroParedAhora=0.
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


## 2026-11-14 — Next: verify world-global difficulty reference (owner design clarification)

The owner confirmed the existing intended mechanic: geographic difficulty uses distance from the **single shared world spawnpoint** for all players; both custom-spawned zombies and siege attackers scale with that distance. Temporal difficulty uses shared world play time, evaluated at spawn: new entities receive current threat and existing entities retain their spawn-time attributes. Multiplayer must not use nearest-player selection, individual bed/spawn anchors, current player position, or the player that triggered the spawn as the difficulty reference.

**Known discrepancy:** the previous source audit describes `nearest player's anchor/spawn`. Do not equate this with the common world spawn. First perform a read-only trace of the actual API/value used by both `AggressiveZombie` spawn paths and verify independence from player count/positions. No code changes until proven.

Future design (not yet implementation): temporal progression parameters move into each creature's `SpawnProfile`, with world time/threat remaining a shared global input. Current custom creatures: `AggressiveZombie` and custom Vex, to be consistently renamed `AggressiveVex`. Rename planning must include classes, registry/resource IDs, serialization/save compatibility, configs, translations, logs, tests and docs. Preserve old-world compatibility; do not blindly rename persisted IDs.

Order: verify global spawn reference and multiplayer behavior; verify time snapshot; define per-creature SpawnProfile temporal contract; inventory Vex naming/compatibility; then implement profile refactor and rename separately with tests. No gameplay changes authorized by this documentation entry. A2 remains FAIL; C4 unchanged.

## 2026-11-14 11:45 — World-global difficulty verification (audit/design only)

- Exact geographic reference is each player's nchorPoint, initialized once from ServerLevel.getSharedSpawnPos() in CommonForgeInteractionEventSubscriber.onEntitySpawn. Normal path: **MATCH** shared world spawn.
- Residual mismatch risk: AggressiveZombieEntity.setPos falls back to getSpawnPoint() (player-specific) when the shared anchor is null. Production hardening should remove that fallback; no change made in this task.
- Temporal difficulty is ThreatLevel.current(level()) captured at spawn; existing entities retain spawn-time attributes. Matches owner design.
- Custom zombie, siege attacker (same class), and FrostVex all use the same nearest-player-anchor lookup. A2 harness overrides the fake player's anchor (-5000) as test instrumentation.
- SpawnProfile is immutable and holds distance parameters; temporal global config lives in ThreatLevel. Proposed per-creature TemporalDifficultyProfile documented, preserving current zombie defaults (1.0→1.8, 3h).
- AggressiveVex rename inventory documented. Java/display rename can be compatibility-safe; changing registry ID
rost_vex is high risk without a save migration.
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
- spawneados=8/8,
allosSpawn=0,
ivos=3, muertos=5, desaparecidos=0, ntraronPerimetroEver=5, dentroParedAhora=0.
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
- Focused run 2n-20261114-123936 completed with fin: C4 PASS, but A2
ivos=4, muertos=4, ntraron=4/8, dentroParedAhora=0; idx3 607,95,601; idx4 588,79,507. No improvement.
- No full acceptance after this negative focused result. A2 remains FAIL; C4 PASS.

## 2026-11-14 13:02 — Enclosure-aware retreat + verified-BREAK replan: focused partial PASS

- Retreat interior check now uses the production wall predicate dentroDeLaAldea → VillageManager.dentroDelRecinto(pos, villageCenter, cota, FENCE_RADIUS), not the broader protected-area predicate. idx7-style start-inside → dest-outside is now rejected.
- Verified BREAK (NavObjectivePolicy.accionResuelta with target air) now advances route index and forces objective=null, objetivoPermiteConstruccion=false, globalCache=null, state=PLANNING; obsolescent above-attacker objective is not retried. Trace VERTICAL_TRANSITION_FAILED reason=BREAK_VERIFIED_REPLAN.
- Focused run 2n-20261114-125345 completed with fin: C4 PASS, ntraronPerimetroEver=6 (criterion 2 met), but
ivos=2, muertos=6, dentroParedAhora=0.
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
- Focused 2n-20261114-143051 completed with fin: C4 PASS, 8/8 spawned, 5/8 entered,
ivos=3, muertos=5, dentroParedAhora=0.
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


## 2026-11-17 — IDX4 AABB source recovery test import correction

- Harness report `2026-11-17-A2-RUN1-AABB-SOURCE-RECOVERY-RESULT.md`: tested SHA `1e65c0242fc8058775eb2ab4687ccd3811fa42ad` BLOCKED at `compileTestJava` by missing `BlockPos` import; no tests or A2 runtime executed.
- ChatGPT added `import net.minecraft.core.BlockPos;` in test-only commit `c75165c2135cccbfcc3eddd40abe09adce6766e8`.
- Confirmed all three policy test mains are registered in `build.gradle`: NavDropTakeoffPolicyTest, NavSourceCellPolicyTest, NavRouteNodePolicyTest.
- Harness must validate exact updated branch HEAD with static checks before any A2 Run 1. A2 remains OPEN; do not run Run 2 or 12k outside existing acceptance gates.


## 2026-11-17 — IDX4 exact bounded recovery A* (PENDING HARNESS)

- Phase 21 established that source snap succeeds but the old local recovery search returned empty at 900/900; see `2026-11-17-A2-RUN1-AABB-SOURCE-RECOVERY-PHASE21.md`.
- New `NavPlanner.planExact` uses the existing validated global A* core, requires exact state + feet Y at take-off, bounds horizontal radius to 4 and vertical envelope to 3, and keeps the existing 900 expansion cap.
- Exact recovery graph rejects BREAK/BUILD/BRIDGE and multi-block DROP edges. Recovery route policy requires the final waypoint to equal take-off and checks WALK=0, STEP_UP=+1, JUMP_UP=+2, DROP=-1 (at most one).
- Failed auxiliary planning no longer destroys the original route/cursor/`plannedFrom`; retry is cooldown-bounded. Successful restoration emits `DROP_TAKEOFF_RECOVERY_ROUTE_RESTORED`.
- Source snap retains the 0.20 radial tolerance; Phase 21's 0.19 offset is within it and all physical predicates passed.
- Fix report: `2026-11-17-IDX4-EXACT-RECOVERY-ASTAR-FIX.md`. Expected DROP policy test total: 43.
- **PENDING HARNESS** on exact current HEAD. Static checks first, then A2 Run 1 only. A2 OPEN; no Run 2 unless 8/8 strict; no 12k until five consecutive 8/8 strict. IDX7/IDX8 unchanged; C4 save/reload OPEN.

## 2026-11-17 — A2 Phase 27 DROP source snap forensic

- Inspected SHA 05b7e9c0 / run a2n-20261117-155419; read-only, no production changes.
- PARTIALLY_CONFIRMED: MoveControl snap target z=514.5 competes with normal DROP MOVE_COMMAND z=513.5 at t4261; blockPosition oscillates 513<->514; oscillation guard switches state to RECOVERING at t4271 before center tolerance.
- SOURCE_SELECTED is not implemented; SOURCE_SNAP_REACHED/REPLAN absent. Eight-neighbor matrix not logged.
- Report: docs/autonomous-village/2026-11-17-A2-PHASE27-SNAP-FORENSIC-DIAGNOSIS.md. A2 OPEN; no Run 2/12k.

## 2026-11-17 — A2 Phase 28 snap ownership/admission

- Inspected `cebca95b` (production `05b7e9c0`); read-only, no production/runtime changes.
- CONFIRMED admission: `originPlan=577,110,514`; `origenFisicamenteAlcanzado` uses integer ±1, admits at z=514.01 with 0.49 to center.
- CONFIRMED ownership: normal DROP `Navigation.moveTo` targets z=513.50 while snap targets 514.50; blockPosition oscillates 513<->514; guard sets RECOVERING at t4271.
- PHYSICAL_COLLISION not demonstrated; swept-AABB/controller telemetry missing. Eight-neighbor matrix: only S (577,110,514) overlaps (0.1739); other seven zero overlap.
- Report: docs/autonomous-village/2026-11-17-A2-PHASE28-DROP-SNAP-OWNERSHIP-DIAGNOSIS.md. A2 OPEN; no Run 2/12k.

## 2026-11-17 — A2 Phase 29 snap exclusive ownership/admission fix

- Production `192d0c52`: `NavSnapOwnershipPolicy`; pending snap blocks route movement and oscillation abort; completion uses 0.20 center + grounded/feet/support/real-AABB.
- Static PASS: NAV_SNAP_OWNERSHIP 12, NAV_DROP_TAKEOFF 51, NAV_SOURCE_CELL 14, lint/diff/compile.
- Runtime `a2n-20261117-170935`: A2 2/8 strict, 2/8 perimeter, C4 PASS [1,0,0]; snap works (SNAP_MOVE_OWNER, SNAP_COMPLETE t211, no contradictory MOVE_COMMAND, collision=false).
- IDX4 later fails independent `replans agotados (8/8)` at571,115,533. Report: docs/autonomous-village/2026-11-17-A2-PHASE29-DROP-SNAP-EXCLUSIVE-OWNERSHIP-FIX.md. A2 OPEN; no 12k.

## 2026-11-17 — A2 Phase 30 IDX4 post-snap replan diagnosis

- Read-only at `b1c69d96` (production `192d0c52`); no runtime/production changes.
- Confirmed stale oscillation after snap: t615 route accepted, t616 SNAP_GUARD aborts with recentCells24, stores lastRouteSignature; seven identical replans rejected, 8/8 at t3495.
- Route exists (TRAVERSING and JUMP_UP t553), so not a geometry/planner dead end.
- Git discrepancy: Phase29 report labeled initial HEAD cebca95b; actual parent was 4afbac50. No rewrite.
- Report: docs/autonomous-village/2026-11-17-A2-PHASE30-IDX4-POST-SNAP-REPLAN-DIAGNOSIS.md. A2 OPEN.

## 2026-11-17 — A2 Phase 31 post-snap state reset fix

- Production `ec6aa339`: one-time reset on verified snap completion clears recentCells/oscillating/occurrences and lastRouteSignature; first plan/route start telemetry added.
- Static PASS: NAV_SNAP_POST_RESET 12, NAV_SNAP_OWNERSHIP 12, DROP 51, source 14, lint/diff/compile.
- Runtime `a2n-20261117-190320`: A2 2/8 strict, 2/8 perimeter, C4 PASS [1,0,0]. 580 reset events; first plan accepted and TRAVERSING; later guards/duplicate protection active.
- Report: docs/autonomous-village/2026-11-17-A2-PHASE31-POST-SNAP-STATE-RESET-FIX.md. A2 OPEN; no 12k.

## 2026-11-18 — A2 Phase 32 non-entrant forensic

- Read-only at `8d4c9b4f`; production `ec6aa339`; no runtime/production changes.
- All 8 accounted: idx1/idx6 strict then combat death; idx2 no safe source neighbor; idx7 BUILD budget; idx3/4/5/8 timeout with 135-141 snap resets (~43 ticks/block).
- Strict=62 radius current `rDentroParedAhora`; perimeter=71 cumulative; first strict events counted by gate.
- Phase29/31 healthy for looped attackers; signature sentinel 0 not used in duplicate comparison, robustness only.
- Report: docs/autonomous-village/2026-11-18-A2-PHASE32-NON-ENTRANT-FORENSIC-DIAGNOSIS.md. A2 OPEN.

## 2026-11-18 — A2 Phase 33 snap churn geometry

- Read-only at `03017bba`; production `ec6aa339`; no runtime/production changes.
- ROOT_CAUSE_CONFIRMED common: DROP_APPROACH moveTo landing center bypasses supported intermediate path node; center crosses unsupported adjacent column, source snap pulls back, repeats every ~43 ticks.
- idx3/4/5/8 first cycles documented with exact positions and DROP targets. Phase29/31 healthy.
- Report: docs/autonomous-village/2026-11-18-A2-PHASE33-SNAP-CHURN-GEOMETRY-DIAGNOSIS.md. A2 OPEN.

## 2026-11-18 — A2 Phase 34 DROP approach supported-path fix

- Production `ee0c4fe4`: NavDropApproachPolicy selects supported adjacent waypoint before landing; direct landing target no longer skips intermediate column.
- Static PASS: NAV_DROP_APPROACH 10, DROP 51, source 14, snap ownership/reset 12/12.
- Runtime `a2n-20261117-210328`: A2 2/8 strict, 2/8 perimeter, C4 PASS [1,0,0]; churn idx3 139->14, idx5 141->4, idx4/idx8 0.
- New blocker: already-adjacent DROP does not descend (idx4 22 attempts, idx8 BUILD FAILED). Report: docs/autonomous-village/2026-11-18-A2-PHASE34-DROP-APPROACH-SUPPORTED-PATH-FIX.md. A2 OPEN.

## 2026-11-18 — A2 Phase 35 adjacent DROP non-descent

- Read-only at `1d1e1e3e`; production `ee0c4fe4`; no production/test/runtime changes.
- IDX4 confirmed: Navigation.stop then aplicarAcercamientoAdyacente(landing) rejected by Manhattan 1+3+1=5 > max3; no MoveControl, no descent, 22 retries same position.
- IDX8 independent: never reached DROP (intentosDescenso0), failed on BUILD budget.
- Report: docs/autonomous-village/2026-11-18-A2-PHASE35-ADJACENT-DROP-NON-DESCENT-DIAGNOSIS.md. A2 OPEN.

## 2026-11-18 — A2 Phase 36 adjacent DROP take-off execution

- Production `6aec35c6`: horizontal-distance DROP nudge, Navigation.moveTo command, descent/landing telemetry.
- Static PASS incl NAV_ADJACENT 15, NAV_DROP_APPROACH 10, DROP 51, source14, snap12/12.
- Runtime `a2n-20261118-042553`: A2 2/8 strict, 2/8 perimeter, C4 PASS [2,0,0]. IDX4 54 commands/5 stalled/0 started; IDX8 824/136/0; no landing confirmed.
- Report: docs/autonomous-village/2026-11-18-A2-PHASE36-ADJACENT-DROP-TAKEOFF-FIX.md. A2 OPEN.

## 2026-11-18 — A2 Phase 37 DROP descent physical forensics

- Read-only at `4c002966`; production `6aec35c6`; no production/test/runtime changes.
- Real Navigation.moveTo destination is landing center, not takeoff. IDX4/IDX8 have paths n=2 and horizontal movement, but Y unchanged; IDX3/5 emit no descent command.
- Collision vs MoveControl/nav completion remains NOT_LOGGED; Stage B proposed only.
- Report: docs/autonomous-village/2026-11-18-A2-PHASE37-DROP-DESCENT-PHYSICAL-FORENSICS.md. A2 OPEN.

## 2026-11-18 — A2 Phase 38 Stage B attempt (BLOCKED)

- Read-only at `cd5890c2`; temporary travel/probe instrumentation added, run `a2n-20261118-110342`, then fully reverted.
- Run crashed in NavObstruction.fits/NavPlanner watchdog before DROP; no idx4/idx8 tick traces, no phase38 CSV.
- Original worlds intact. Production unchanged at `6aec35c6`. Report: docs/autonomous-village/2026-11-18-A2-PHASE38-DROP-DESCENT-PHYSICAL-EXECUTION-TRACE.md. A2 OPEN.

## 2026-11-18 — A2 Phase 39 planner watchdog and DROP fixture

- Read-only at `d3299d7c`; production `6aec35c6`; no runtime/production changes.
- Watchdog explained: synchronous global A* 8000 expansions x 56 offsets = 448000 edges, multiple bounded collision queries per edge, no tick yield; log says 60.00s (crash text ms-as-s). Probe not causal.
- Deterministic IDX4/IDX8 DROP fixture designed; validation not executed.
- Report: docs/autonomous-village/2026-11-18-A2-PHASE39-PLANNER-WATCHDOG-AND-DROP-FIXTURE-DIAGNOSIS.md. A2 OPEN.

## 2026-11-18 — A2 Phase 40 deterministic DROP fixture

- Temporary fixture/probe run `a2n-20261118-134234` (IDX4-like) and `a2n-20261118-134458` (IDX8-like); all instrumentation reverted, production unchanged.
- IDX4 TRACE_COMPLETE: local plan exp=1/900, no global; path 2 nodes then null; MoveControl.hasWanted=false all 82 rows; Y=122; verticalCollision=true; no step-off.
- IDX8 FIXTURE_NO_DROP: no plan in300 ticks, no trace.
- Report: docs/autonomous-village/2026-11-18-A2-PHASE40-DETERMINISTIC-DROP-FIXTURE-TRACE.md. A2 OPEN.

## 2026-11-18 — A2 Phase 41 DROP edge-step fix

- Production `4379dbc2`: bounded DropEdgeStep claims MoveControl after path end, steps AABB off support, then natural fall. Tests PASS incl NAV_DROP_EDGE_STEP 12.
- A2 `a2n-20261118-140906`: IDX4 descends t17-24 lands567,119,534 cursor advances; IDX8 descends t275-279 lands746,63,534. A2 2/8 strict, C4 PASS [2,0,0].
- Report: docs/autonomous-village/2026-11-18-A2-PHASE41-DROP-EDGE-STEP-FIX.md. A2 OPEN.

## 2026-11-18 — A2 Phase 42 remaining non-entrants

- Read-only at `74782622`; production `4379dbc2`; run `a2n-20261118-140906`; A2 2/8, C4 PASS [2,0,0].
- Six independent causes: IDX2 source recovery; IDX3/5 DROP-approach 8/8 replans; IDX4 later non-adjacent DROP landing; IDX7 BUILD budget; IDX8 step/local budget.
- Phase41 edge-step not a regression (IDX4 t24, IDX8 t279). Report: docs/autonomous-village/2026-11-18-A2-PHASE42-REMAINING-NON-ENTRANTS-FORENSIC.md. A2 OPEN.

## 2026-11-18 — A2 Phase 43 non-adjacent DROP forensics

- Read-only at `b624546c`; production `4379dbc2`; run `a2n-20261118-140906`.
- IDX3/5 frozen at waypoint (one-node completed path, no owner), 8/8 replans. IDX4 later landing577,111,522 fits/support true but withinTolerance=false, no owner from distanceSqr7.065.
- Phase41 edge-step not involved; no regression. Report: docs/autonomous-village/2026-11-18-A2-PHASE43-NON-ADJACENT-DROP-FORENSICS.md. A2 OPEN.

## 2026-11-18 — A2 Phase44 non-adjacent DROP approach owner (PENDING HARNESS)

- ChatGPT production implementation: `ed31b815193aa4fcde0b088f1a6d110270ba4a9d`; parent `793816686e4373d4b581264468f2ee92ce431156`.
- New bounded DROP-only owner transfers from Navigation to MoveControl when path ends before physical waypoint arrival or stalls >20 ticks; timeout >80, no-progress >20; safety revalidated, exact grounded occupancy required.
- Phase41 edge-step unchanged and receives control in the same traversal pass after the supported waypoint is physically occupied; clearing the temporary owner stops any pending Navigation path before the edge-step starts.
- New `NAV_DROP_APPROACH_OWNERSHIP_POLICY_TEST`: 13 assertions, registered but **not run by ChatGPT**.
- **PENDING HARNESS:** static tests/lint/diff/diagnostics compile on exact SHA, then A2 Run1 only if static passes. Capture owner/path/landing traces for IDX3/5/4. No Run2/Full12K; A2 OPEN.
- Report: `docs/autonomous-village/2026-11-18-A2-PHASE44-NON-ADJACENT-DROP-APPROACH-OWNER.md`.

## 2026-11-18 — A2 Phase 44 validation

- Synced HEAD `fd2bbcd0`; production `ed31b815`; static PASS incl NAV_DROP_APPROACH_OWNERSHIP 13.
- A2 `a2n-20261118-145308`: 3/8 strict (idx1 t559, idx5 t730, idx6 t650), C4 PASS [2,0,0].
- IDX3/4/5 ownership chain Navigation->MoveControl->Phase41->landing validated. Remaining: IDX3 BUILD64/64, IDX4 dist72.12 global budget, IDX2/7/8 independent.
- Report: docs/autonomous-village/2026-11-18-A2-PHASE44-VALIDATION.md. A2 OPEN.

## 2026-11-18 — A2 Phase 45 IDX3/IDX4 forensics

- Read-only run `a2n-20261118-145308`; production unchanged.
- IDX3: local objective cycle 607,94,601/613,88,601/608,95,601; fallback result=null; 64/64,10/10; physical stairs24/24.
- IDX4: vertical-only DROP at597,79,499 ->597,78,499; horizontal dist0 outside Phase41; landing fits/support true, landingHeightReached=false; final horizontal dist72.12.
- Report: docs/autonomous-village/2026-11-18-A2-PHASE45-IDX3-IDX4-FORENSICS.md. A2 OPEN 3/8.

## 2026-11-18 — A2 Phase 46 validation

- Production `e1530ab3`; static PASS incl NAV_DROP_EDGE_STEP16, approach owner13.
- A2 `a2n-20261118-163717`: 4/8 strict (idx1,2,5,6), C4 PASS [1,0,0].
- Vertical-only IDX4 runtime not reproduced -> INSUFFICIENT_EVIDENCE. IDX3 trace: ACCESS_CLEARED/NO_PROGRESS, 20 STAIR_PLACED, pending stairs, fallback null.
- Phase45 errata: anchor661,71,533 vs mission661,72,533; escalera.colocados 20/24 != traversals. Report: docs/autonomous-village/2026-11-18-A2-PHASE46-VALIDATION.md. A2 OPEN.

## 2026-11-18 — A2 Phase 47 forensics

- Read-only at `d9d544b0`; production `e1530ab3`.
- IDX3: 20 colocaciones y 20 ESCALON-TRAVESADO. La confirmacion de travesia consume el pending antes de fallback; mismo tick result=null.
- IDX4 horizontal-zero: INSUFFICIENT_EVIDENCE; no hay fixture determinista comprometido y el caso no ocurrio en run163717.
- Report: docs/autonomous-village/2026-11-18-A2-PHASE47-FORENSICS.md. A2 OPEN 4/8.

## 2026-11-18 — A2 Phase 48A IDX3 reconciliation

- Read-only at `d9d544b0`; production `e1530ab3`.
- Phase47 corrected: fallback exits with STAIR_BUILDER_RETURN NO_PROGRESS before pending; same stair607,93,601 placed/traversed20 times.
- Need instrumentation for escaleraRetraso and candidate generation; IDX4 fixture still unimplemented.
- Report: docs/autonomous-village/2026-11-18-A2-PHASE48A-IDX3-CAUSAL-RECONCILIATION.md. A2 OPEN.

## 2026-11-18 — A2 Phase 49A validation

- Run `a2n-20261118-172804`; production `9d6709f5`; A2 3/8 strict, C4 PASS [1,0,0].
- IDX3: ANCHOR_REFRESH t2299 moves anchor607,93 ->607,94; candidate dy1 repeats607,93. CADENCE_WAIT increments local steps.
- Report: docs/autonomous-village/2026-11-18-A2-PHASE49A-VALIDATION.md. A2 OPEN.

## 2026-11-18 — A2 Phase 50 validation FAIL

- Run `a2n-20261118-212254`; production `f7a919c2`; A2 3/8 strict, C4 PASS [1,0,0].
- ANCHOR_REFRESH still 607,93->607,94; policy not wired. Cooldown still NO_PROGRESS; CADENCE_WAIT/A2_LOCAL_STAIR_WAIT absent.
- Tests 8/6 PASS only via init task; not registered in build.gradle.
- Report: docs/autonomous-village/2026-11-18-A2-PHASE50-VALIDATION.md. A2 OPEN.

## 2026-11-18 — A2 Phase 51 validation FAIL

- Run `a2n-20261118-215530`; production `ea21466a`; A2 2/8 strict, C4 PASS [1,0,0].
- Static PASS; tests 8/6 now in normal gate. CADENCE_WAIT validated (3595/3542, counters unchanged).
- Anchor unexercised: 0 constructions/traversals/ANCHOR_REFRESH; A2 regressed from3/8, IDX5 lost entry.
- Report: docs/autonomous-village/2026-11-18-A2-PHASE51-VALIDATION.md. A2 OPEN.

## 2026-11-18 — A2 Phase 52 stair/IDX5 forensics

- Run `a2n-20261118-225649`; production `ea21466a`; A2 2/8 strict, C4 PASS [1,0,0].
- IDX3: 397 builder calls, 380 cooldowns, 2340 candidates all occupied terrain; 0 placements/traversals.
- IDX5: Phase50 t2 NO_PROGRESS->t3 MOVE; Phase51 t2 CADENCE_WAIT->zero MOVE_COMMAND. Causal link confirmed.
- Report: docs/autonomous-village/2026-11-18-A2-PHASE52-STAIR-IDX5-FORENSICS.md. A2 OPEN.

## 2026-11-18 — A2 Phase 53 validation BLOCKED

- Static PASS on production `656ce7d0`; tests 8/6.
- Run `a2n-20261118-233308` crashed in A2NavHarness.prepararRaidFixture/setChunkForced watchdog 60s before spawn. No IDX5/cadence route evidence.
- Report: docs/autonomous-village/2026-11-18-A2-PHASE53-VALIDATION.md. A2 OPEN.

## 2026-11-18 — A2 Phase 54A fixture forensics

- Read-only; run a2n-20261118-233308. 289 setChunkForced synchronous in one tick; cold generation >60s watchdog. Loop completed after watchdog.
- Recommended pre-generated disposable world + non-blocking ticket/wait + timeout before spawn.
- Report: docs/autonomous-village/2026-11-18-A2-PHASE54A-FIXTURE-FORENSICS.md. A2 OPEN.

## 2026-11-19 — A2 Phase 54B fixture implementation

- Harness-only: updateChunkForced tickets + hasChunkAt polling + timeout600; spawn only after READY/verifications.
- Static PASS; navigation/entity production unchanged; no runtime executed.
- Report: docs/autonomous-village/2026-11-19-A2-PHASE54B-FIXTURE-IMPLEMENTATION.md. A2 OPEN.

## 2026-11-19 — A2 Phase 54B-R1 strict timeout

- Fixture loader now checks `now >= rChunkDeadline` before READY; late completion becomes TIMEOUT, releases tickets, no spawn.
- Static PASS; navigation/entity setup untouched; no runtime executed.
- Report addendum: docs/autonomous-village/2026-11-19-A2-PHASE54B-FIXTURE-IMPLEMENTATION.md. A2 OPEN.

## 2026-11-19 — A2 Phase 54B-R1 runtime BLOCKED

- Run a2n-20261119-011138: fixture 289/289 READY 1 tick, 8/8 spawn, village/wall/population PASS.
- Watchdog at first global A* burst (NavPlanner/NavObstruction, 60.04s); no A2 result, IDX5/IDX3 insufficient.
- Report: docs/autonomous-village/2026-11-19-A2-PHASE54B-R1-RUNTIME-VALIDATION.md. A2 OPEN.

## 2026-11-19 — A2 Phase55 static validation FAIL

- `9966f807` compileJava FAIL: NavPlanner.java:617/628 redeclara `BlockPos state` sobre parámetro `GlobalSearchState state`.
- NAV_PLANNER_STATE_TEST (51 assertions) no ejecutado; lint/diff PASS; no runtime.
- Report: docs/autonomous-village/2026-11-19-A2-PHASE55-STATIC-VALIDATION.md. A2 OPEN.

## 2026-11-19 — A2 Phase55-R2 static PASS

- Production `11ff43cd`; compile/test/lint/diff PASS.
- NAV_PLANNER_STATE_TEST OK with 54 executed assertions; slice4, pops8, budget unchanged.
- No runtime. Report: docs/autonomous-village/2026-11-19-A2-PHASE55-R2-STATIC-VALIDATION.md. A2 OPEN.

## 2026-11-19 — A2 Phase55-R2 runtime BLOCKED (fixture population)

- Run a2n-20261119-031255: chunk loader 289/289 READY 1 tick; fixture population 14/10 professionals vs 12; 0 spawn.
- No A2/A*/IDX5/IDX3 evidence; no retry. Production `11ff43cd`.
- Report: docs/autonomous-village/2026-11-19-A2-PHASE55-R2-RUNTIME-BLOCKED.md. A2 OPEN.

## 2026-11-19 — A2 Phase55 population NBT forensics

- Read-only NBT: all 12 workers exist; SHEPHERD c690ddc5 at708,71,533 and MASON c6ba9a5c at694,71,511 alive HP20 death0 persistent.
- Both inside verifier AABB; getEntitiesOfClass snapshot omitted them. Exact query visibility unknown.
- Report: docs/autonomous-village/2026-11-19-A2-PHASE55-POPULATION-NBT-FORENSICS.md. No runtime. A2 OPEN.

## 2026-11-19 — A2 Phase55 runtime FAIL

- Run a2n-20261119-093456: fixture PASS (289/289,16 villagers/12 professionals,8 spawn).
- A2 0/8 strict,1/8 perimeter (idx6 t271),8 alive; C4 FAIL guards no combat.
- Incremental A* no watchdog (66 pending) but key searches exp=1/8000 front exhausted; most PLANNING. No cadence preserved/stairs.
- Report: docs/autonomous-village/2026-11-19-A2-PHASE55-RUNTIME-VALIDATION.md. A2 OPEN.

## 2026-11-19 — A2 Phase55 first-expansion A* forensics

- IDX3/IDX5 exp1: first node generates 56 candidates, 0 accepted; OPEN empty.
- Starts are 1x1 shafts, adjacent columns solid; OFFSETS lacks vertical-only (0,0) edge.
- Exact rejection enum missing; opt-in telemetry specified. Report: docs/autonomous-village/2026-11-19-A2-PHASE55-ASTAR-FIRST-EXPANSION-FORENSICS.md.
- No runtime. A2 OPEN.
