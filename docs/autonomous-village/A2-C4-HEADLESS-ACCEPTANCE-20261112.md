# C4 — Headless raid fixture and acceptance

**Checkpoint date:** 2026-11-12  
**Repository:** `D:\Documents\DevilRpg-autonomous`  
**Branch / HEAD at start of implementation:** `feature/autonomous-village` / `518fee13fc15bfb9b92b0479d86b5c609d1947da`  
**No commit or push.** All runs use disposable copies; `run\saves` and historical worlds are untouched.

## 1. Why the original harness could not prove C4

The old raid fixture built a synthetic ring and spawned eight attackers, but it did not generate a real village or establish the real siege state. It therefore could not prove guard duty, professional Goal recovery, or actual ownership of the wave. It also measured three vertical cells for every wall column, while the production fence has two continuous layers and taller stone pillars. The old measurement incorrectly rejected ordinary two-block wall segments.

## 2. Implemented fixture

The canonical harness remains `tools/a2n/A2NavHarness.java`. The Gradle source copy is `src/main/java/com/chipoodle/devilrpg/debug/A2NavHarness.java`; copy canonical to that path before compiling, and remove the temporary source copy after the final run if the local harness workflow expects it. Never edit `.partial`.

### Production village

- Uses the real `VillageManager.preGenerate(level, 0, center)` path.
- Generates the production roster: 12 profession workers, iron golem, and four registered/equipped founder guards.
- Calls the gated `VillageManager.ejecutarMantenimientoParaArnes` to install the actual professional Goals before the raid.
- Wall integrity is measured against the actual production ring. C4 validates the two continuous wall layers per column; 496 columns × 2 = 992 expected cells. The taller stone pillars remain part of production geometry but are not incorrectly required on every log segment.

### Real headless siege state

- Each `AggressiveZombieEntity` receives the production village center, `goToCenterActive=true`, `worldSiegeIndex=0`, and persistence before it is allowed to tick.
- The harness registers all eight attacker UUIDs in a gated `VillageManager.iniciarAsedioParaArnes` test-defense record. This makes `isVillageUnderAttack` true for the real guard Goals and gives breach/kill bookkeeping the actual wave UUIDs.
- The test-defense record is marked `testHarness`; the ordinary player-driven siege timer skips this record, so it does not spawn a second wave or pause because there is no connected player.
- At phase B, the harness removes the remaining attackers, calls `terminarAsedioParaArnes`, then invokes the normal village maintenance path and measures professional Goal recovery.
- All of this runs in a dedicated server with `--nogui`, in a per-run game directory. No Minecraft client is opened and no window/focus is required.

### Guard idle recovery

`VillageManager.empujarSiEstaPlantado` now avoids treating a founder guard with no live enemy assigned as a stuck worker. It may still recover a guard that has a live combat target. This prevents the generic stuck-worker recovery from interrupting legitimate guard idle/patrol behavior.

## 3. Permanent launcher

`tools/a2n/lanzar-c4.ps1` now:
1. Copies the repaired seed world into a new `build/a2n/<runId>/game/a2n` directory.
2. Verifies the copied `level.dat` SHA-256 against the seed.
3. Writes isolated `eula.txt` and `server.properties`.
4. Launches `runServer` with `--nogui`, A2 diagnostics, trace navigation, guard diagnostics, and optional combat trace.
5. Waits for the harness to write `fin.txt`; checks the C4 verdict and four unique guard UUIDs acquiring a live enemy.
6. Fails closed if evidence is missing, fewer than four guards engage, or the verdict is not C4_PASS. Guard hits/kills are reported as supplemental evidence, not required by the current C4 engagement predicate.

Build configuration accepts `a2nGameDirectory`, `a2nMode`, maintenance hook, phase ticks, run ID, nav trace, guard diagnostics, and combat trace. In A2 diagnostic mode only the canonical A2NavHarness is compiled among debug subscribers; other debug entrypoints are excluded.

## 4. Run history

### `c4-smoke2-20261112-2320` — fixture smoke only

- PASS for production fixture: 496 wall columns; corrected two-layer measure; 4/4 founder guards equipped; 12/12 profession workers and Goals installed; eight attackers spawned.
- Short phase window (100 / 200 / 100 ticks); no combat acceptance was intended. Recovery reported 12 workers with running professional Goals and 6 JOB_SITE owners, 0 orphaned, 0 duplicates.
- Not a C4 raid acceptance run.

### `c4-full1-20261112-2330` — invalid / discarded

- The first long attempt did not assign `worldSiegeIndex` to the synthetic attackers. They could be removed as distant mobs in a no-player server; the run was stopped and not accepted.

### `c4-full2-20261112-2358` — invalid / discarded

- The second attempt registered the test siege and world siege index, but the compiled harness copy still lacked `setVillageCenter` / `setGoToCenterActive`. The attackers did not use the production MoveToVillageCenterGoal. The run was stopped and not accepted.

### `c4-full3-20261112-0015` — C4 PASS (final accepted run)

- Fresh copy of the repaired disposable world; dedicated `runServer --nogui`; production village and test-defense state registered. Seed `level.dat` SHA-256: `4D9516A994A6CFF1D50C9EA6667F2E4CD0CCB7EB96438F26815BDA4FCD94EEEF`.
- Fixture: 496 production wall columns / 992 continuous wall cells; four equipped founder guards; 12 profession workers; eight registered attackers.
- Four unique guard UUIDs acquired a live attacker at ticks 661–662. This proves guard engagement by target acquisition. No successful guard hit was logged (`C4 GUARD HIT=0`) and no guard-attributed kill was logged (`guardKill=0`); do not overstate this as damage/kill evidence.
- Final `fin.txt`: `C4_PASS (raid) - guardasCombatieron=true profesionalesReanudados=true asignacionesJobSiteLimpias=true supervivientes=16 retiradosParaRecuperacion=3 jobSite=[2, 0, 0] fallosDeInstrumento=0`.
- Final audit: 12/12 professional Goals installed; 12/12 workers resumed; 16 villagers alive; 0 orphaned JOB_SITE assignments; 0 duplicate owners. Village outcome `SURVIVED`.
- Five explicit `RAID ESTADO ... MUERTO` events were observed; three remaining attackers were removed during recovery. Later snapshots misclassified some removed entities as disappeared. This attacker accounting defect is informational and is not part of the C4 pass predicate; no claim is made about source-attributed kills.
- Server shut down normally and saved Overworld, Nether and End. No client, GUI, focus changes, `run/saves`, historical world, commit or push.

## 5. Final disposition and remaining diagnostic limits

- Run3 met the explicit C4 contract, but run4 failed the 12/12 worker-recovery predicate. Therefore C4 is not accepted for repeatability yet; see §7. Run5 uses UUID-based worker tracking.
- Guard target acquisition is not the same as a confirmed hit. Run3 logged zero `C4 GUARD HIT` and zero guard-attributed kills; run4 logged 16 hits. A separate future combat-damage criterion would require an attack/damage runtime assertion and must not be retroactively inferred.
- The attacker snapshot counters need a separate correction: some dead/removed attackers are counted as disappeared in later snapshots. The run retained five explicit death events and three recovery removals, but no precise source attribution is claimed.
- The launcher was corrected after the accepted run to tolerate informational native stderr from the JDK/Gradle under Windows PowerShell 5.1. It now checks four unique guard UUIDs, the final `C4_PASS`, and the native Gradle exit code. The corrected script passed PowerShell parser validation; a second full raid is not required for the already accepted C4 predicates.

## 6. Continuation / retry protocol

1. Never relaunch an existing `RunId`. Each run has an isolated directory and immutable input seed copy.
2. If ChatGPT/remote access drops, do not assume the server stopped. Reconnect to CHRIS-PC, inspect `build/a2n/<RunId>/fin.txt`, `launcher.log`, and `game/logs/latest.log`, and read the persisted result before deciding anything.
3. If `fin.txt` exists and begins `C4_PASS`, recover and validate the run; do not repeat it. If no `fin.txt` exists, inspect process state and the tail of `latest.log` before deciding whether the same process is still active or the run failed.
4. For a new attempt, use a new `RunId` and the repaired seed at `build/a2n/arena-repair1-20261112-205206/game/a2n`.
5. Run `python tools\\lint_aldea.py --strict`, `git diff --check`, and the PowerShell parser check. Never touch `tools/a2n/A2NavHarness.java.partial`, `run/saves`, or historical worlds. No commit/push.

## 7. Integrity boundaries

- Protected `tools/a2n/A2NavHarness.java.partial` remains untouched.
- Repaired seed world `build/a2n/arena-repair1-20261112-205206/game/a2n` remains the immutable source; every raid gets a new copy.
- Invalid c4-full1 and c4-full2 worlds remain disposable evidence; neither is reused.
- No GUI/client, no user interaction, no `run\saves`, no historical worlds, no branch switch, no commit/push.


## 7. Casualty semantics and run6 validation — C4 OPEN

### `c4-full4-20261112-0045` — C4 FAIL

- Four distinct guards acquired live targets and delivered 16 successful guard-hit events; six attackers died.
- Village outcome: `SURVIVED`, 16 villagers alive. JOB_SITE: 0 orphaned, 0 duplicate owners. All 12 profession Goals remained installed.
- The old sampler counted 11/12 workers resumed, without recording the missing UUID. Run5 resolved this by capturing and sampling all 12 initial worker UUIDs.

### `c4-full5-20261112-0155` — C4 FAIL under the strict 12/12 initial-worker predicate

- Four guards engaged and delivered 16 hits; five attackers died and three were removed for recovery.
- Village outcome: `SURVIVED`, 15 villagers alive. Eleven professional workers survived, all 11 had professional Goals installed and all 11 resumed; JOB_SITE had 3 assignments, 0 orphaned, 0 duplicate owners.
- Missing worker UUID: `f62b9fae-2361-42b0-b0f3-6058f265c789`. Initial village log maps this UUID to the only weaponsmith; Minecraft death log records Quintin (Herrero de armas) was slain by an Aggressive Zombie at (710.64, 71, 496.37). This is a real casualty, not a tracking false negative.
- C4 contract has no zero-casualty requirement. It requires all surviving workers to resume, all missing initial UUIDs to be accounted for by death events, no unresolved UUIDs, 4/4 guard engagement, a surviving village and clean JOB_SITE ownership.

### Harness correction compiled for run6

- Subscribe to `LivingDeathEvent` for expected worker UUIDs; persist UUID, profession, name, position, DamageSource message ID, attacker and direct entity.
- At final audit, partition expected workers into alive/resumed, confirmed casualties and unresolved. Require all living workers to resume and all initial UUIDs to be accounted for; do not count a dead worker as an unresumed survivor.
- Compile: `compileJava -Pa2nDiagnostics=true --no-daemon --offline --rerun-tasks` — BUILD SUCCESSFUL.
- Canonical harness SHA-256: `0B699503DCBE99D084AE4A073FE77650B72484B02BA97DED0829C889BFCA9358`.

### `c4-full6-20261112-0210` — IN PROGRESS

Fresh disposable copy of the repaired seed; dedicated headless server; phaseA=3000, phaseB=6600, recoveryTicks=1200. The local monitor `tools/a2n/consultar-c4.ps1 -RunId c4-full6-20261112-0210 -Wait` is active. Wait for `fin.txt`; do not relaunch the same RunId.

**C4 remains OPEN** until run6 returns a final `C4_PASS` with 0 unresolved workers, all living professionals resumed, and casualty details recorded. Block 6 multi-day soak remains gated.


## 8. Final cleric callback validation — 2026-11-12 22:40

### `c4-full7-20261112-0230` — C4_PASS (first cleric-cache variant)

The dedicated headless run completed with:
`C4_PASS (raid) - guardasCombatieron=true profesionalesReanudados=true asignacionesJobSiteLimpias=true supervivientes=16 trabajadoresEsperados=12 trabajadoresVivos=12 trabajadoresReanudados=12 bajasTrabajadores=0 trabajadoresNoResueltos=0 retiradosParaRecuperacion=3 jobSite=[1, 0, 0] fallosDeInstrumento=0`.

This validates all C4 runtime predicates for that compiled variant: 4/4 guards engaged, 12/12 living professionals resumed, 16 villagers survived, no casualties/unresolved workers, and no instrumentation failures. Run7 was started before the final direct-registration callback edit, so it does not by itself validate that last source variant.

### Final cleric callback variant

The canonical source now additionally exposes `VillagerClericGoal.registrarSoporteAsignado(BlockPos)`; station allocation registers the brewing stand directly before the next vanilla Brain tick can erase JOB_SITE, and the Goal can capture its station during the post-heal rest window. `compileJava` passed after these edits (the latest check reported BUILD SUCCESSFUL; compile task up-to-date).

### `c4-full8-20261112-2240` — IN PROGRESS

A new isolated copy of the immutable repaired seed was launched with the final callback variant:
- dedicated `runServer --nogui`, no Minecraft client/UI;
- phaseA=3000, phaseB=6600, recoveryTicks=1200;
- port 25579;
- output: `build/a2n/c4-full8-20261112-2240`;
- seed SHA-256: `4D9516A994A6CFF1D50C9EA6667F2E4CD0CCB7EB96438F26815BDA4FCD94EEEF`;
- monitor PID 4668: `consultar-c4.ps1 -RunId c4-full8-20261112-2240 -Wait -PollSeconds 15 -TimeoutMinutes 20`.

Do not relaunch this RunId. After disconnect, inspect `fin.txt`, `launcher.log`, `game/logs/latest.log`, and the monitor/process state before taking action. C4 is not closed until run8 returns its final verdict. Keep `.partial`, historical worlds, and `run/saves` untouched. No commit/push.


## 8. Continuación tras interrupción remota — run7 PASS / run8 en curso

### Recuperación de sesión

La conexión de ChatGPT/Desktop Commander es independiente del proceso local de Gradle/Minecraft. Si se corta la sesión remota, el servidor dedicado y los procesos locales ya iniciados continúan en CHRIS-PC; no se debe asumir que se detuvieron ni iniciar otra copia a ciegas.

- Consultar dispositivo: Desktop Commander `list_devices`; dispositivo autorizado CHRIS-PC.
- Consultar un run: `powershell -NoProfile -ExecutionPolicy Bypass -File tools\\a2n\\consultar-c4.ps1 -RunId <RunId>`.
- Esperar un run sin relanzarlo: agregar `-Wait -PollSeconds 15 -TimeoutMinutes 30`.
- El watcher no detiene ni reinicia Minecraft. Lee `fin.txt`, proceso JVM y logs. Un run sin `fin.txt` requiere inspección antes de decidir otro intento.
- La conexión del asistente no puede ser reconectada automáticamente desde el modelo; la recuperación se hace al recibir el siguiente mensaje, consultando el mismo RunId. El trabajo local no depende de mantener abierta la conversación.
- Nunca reusar RunId ni mundo de una corrida fallida: cada intento nuevo copia el seed reparado a un directorio nuevo.

### `c4-full7-20261112-0230` — PASS repetible

- `fin.txt`: `C4_PASS`; 16 aldeanos supervivientes; 12/12 trabajadores iniciales vivos y reanudados; 0 bajas de trabajadores; 0 UUID sin resolver; 12/12 Goals profesionales; JOB_SITE: 1 asignación, 0 huérfanas, 0 propietarios duplicados; 4/4 guardias con adquisición de enemigo vivo; 0 fallos de instrumento.
- Log independiente: 4 UUID únicos en `C4 GUARD ENGAGED`, 14 impactos `C4 GUARD HIT`, 4 bajas atribuidas a guardias (`guardKill=true`), 5 muertes de atacantes con salud 0 confirmada. Tres atacantes restantes se retiraron explícitamente durante la fase de recuperación.
- Fixture de producción: 496 columnas del muro, 992/992 celdas continuas presentes; 4/4 guardias fundadores equipados; 12/12 trabajadores profesionales con Goal instalado; 8/8 atacantes registrados.
- Fases: A hasta tick 3000; B hasta tick 6600; recuperación hasta tick 7800. Servidor dedicado `--nogui`, guardado normal de dimensiones.
- Resultado de launcher: `BUILD SUCCESSFUL in 7m 44s`. Sin GUI, foco, jugador conectado, `run\\saves`, mundo histórico, commit o push.

### `c4-full8-20261112-2240` — EN CURSO

- Nuevo directorio y mundo desechable; seed `arena-repair1-20261112-205206/game/a2n`; SHA-256 de `level.dat` origen y copia: `4D9516A994A6CFF1D50C9EA6667F2E4CD0CCB7EB96438F26815BDA4FCD94EEEF`.
- Puerto aislado 25579; fases A=3000, B=6600, recuperación=1200; nav trace, guard diagnostics y combat trace habilitados.
- El servidor ya inició y el fixture fue generado: 496 columnas / 992 celdas; 4/4 guardias fundadores equipados; 12/12 trabajadores y Goals profesionales; 8 atacantes registrados en la defensa real de prueba.
- No existe aún `fin.txt` al escribir esta actualización. El watcher local `consultar-c4.ps1 -RunId c4-full8-20261112-2240 -Wait -PollSeconds 15 -TimeoutMinutes 30` está activo en PID 29108. El proceso Gradle/Minecraft continúa localmente; no lanzar otro run ni matar el proceso.
- Siguiente acción: recuperar el watcher/proceso y leer `fin.txt`; si termina, verificar los 12 UUID iniciales (vivos/reanudados o bajas confirmadas), 0 no resueltos, 4/4 guardias, JOB_SITE limpio y guardado normal. Si la conexión vuelve a cortarse, repetir solo la consulta del mismo RunId.

### Integridad al checkpoint

- Rama: `feature/autonomous-village`; HEAD: `518fee13fc15bfb9b92b0479d86b5c609d1947da`.
- Arnés canónico: SHA-256 comienza `0B699503DCBE99D0...` (hash completo verificar al reanudar); copia Gradle debe corresponder byte a byte antes de compilar.
- `tools/a2n/A2NavHarness.java.partial` permanece protegido e intacto; hash medido comienza `2C3EBA9ED6E936B5...`.
- No commit, push, reset, stash ni clean. Preservar todos los cambios y archivos no seguidos existentes.


### Run8 live checkpoint — raid engagement verified

At server tick approximately 1183, the final-callback run had recorded:
- production village valid: wallColumns=496, wallCells=992, wallPresent=992, wallOk=true, supportMissing=0, damagedColumns=0;
- 16 villagers; 4/4 guards equipped; 12/12 professionals and profession Goals;
- 8 attackers registered for the siege;
- 4 engagement events from 4 distinct guard UUIDs;
- 10 guard-hit events and 4 attacker-death events;
- 0 worker-death events and 0 workers-not-resumed at this checkpoint.

The server remains active and `fin.txt` has not yet been written. This is an intermediate observation only, not the final C4 verdict.


### Run8 phase-A checkpoint — tick 2100

Raid snapshots show the siege progressing: at t=300, 8/8 attackers alive; t=600, 5 had crossed the perimeter at least once; t=900, 5 were inside the wall; t=1200, 2 dead; t=1500 and t=1800, 5 dead and 3 alive; t=2100, 3 attackers remain, 5 dead, 0 disappeared. No worker death or worker-not-resumed event had been observed at the preceding tick-1183 checkpoint. Run remains active; final worker/guard/JOB_SITE audit is pending.


Run8 phase-A completed through t=3000: 5 attackers killed, 3 remain alive, 0 disappeared; no `C4 WORKER DEATH` and no `C4 WORKER NOT RESUMED` events recorded through this checkpoint. Phase B and recovery remain in progress; no final verdict yet.


Run8 phase-B checkpoint at t=5100: 3 attackers remain alive, 5 killed, 0 disappeared; the siege remains in phase B. No worker-death or worker-not-resumed markers have appeared through this checkpoint. The remaining attackers are still in/around their tunnel path; allow the configured phase-B and recovery windows to complete.


Run8 current combat counters at t≈5283: 4/4 unique guards engaged, 12 guard-hit events, 5 attacker-death events, 0 worker deaths, 0 workers-not-resumed; no `fin.txt` yet. This confirms sustained combat without a worker casualty so far, but is still an intermediate state.


Run8 phase-B checkpoint t=6000: 3 attackers alive, 5 killed, 0 disappeared; the perimeter entry metric reached 6/8 ever and 1 currently inside. Worker death/not-resumed counters remain 0 in the most recent aggregate check. The run is approaching the phase-B boundary; final recovery audit pending.


Run8 reached the end of configured phase B at t=6600: 6 attackers dead, 2 alive, 0 disappeared; 0 currently inside the wall. Recovery window (1200 ticks) and final audit remain pending. No worker-death/not-resumed events have been observed in the current run so far.


Run8 checkpoint t=7200: all attackers are gone from the live raid sample (0 alive, 6 confirmed dead, 2 disappeared/removed, 0 currently inside). The final defense and JOB_SITE audit has not yet been emitted; recovery phase is still running. Worker deaths/not-resumed remain 0 in prior aggregate check.


Run8 t=7500: all attackers remain accounted for as 6 confirmed deaths + 2 removed/disappeared; no live attackers remain, and the two no longer have active presence in the raid sample. Villager farm/miner Goals continue ticking in the village log. The run is still in phase B; final recovery and C4 audits have not yet fired.


## 9. FINAL RESULT — `c4-full8-20261112-2240` — C4 PASS

**This section supersedes the older OPEN / IN PROGRESS notes above.** The final callback variant was validated by a fresh dedicated headless run on an isolated copy of the repaired seed.

Final persisted verdict (`fin.txt`):
`C4_PASS (raid) - guardasCombatieron=true profesionalesReanudados=true asignacionesJobSiteLimpias=true supervivientes=16 trabajadoresEsperados=12 trabajadoresVivos=12 trabajadoresReanudados=12 bajasTrabajadores=0 trabajadoresNoResueltos=0 retiradosParaRecuperacion=2 jobSite=[1, 0, 0] fallosDeInstrumento=0`.

Final server audit:
- `C4 DEFENSE guardasQueCombatieron=4/4`; 12 expected workers, 12 alive, 12 resumed; 0 worker casualties; 0 unresolved; 16 villagers alive; 12/12 profession Goals.
- `C4 JOB_SITE assignments=1 orphaned=0 duplicateOwners=0`.
- `C4 OUTCOME=SURVIVED attackersRemovedForRecovery=2 workerCasualties=0`.
- Combat counters (final log audit): 4 distinct guards engaged, 15 guard-hit events, 6 confirmed attacker deaths (5 attributed to guards, 1 to the iron golem), 2 attackers removed for recovery; no attackers remained live at the final raid snapshot.
- Production village geometry: 496/496 wall columns, 992/992 wall cells present, `wallOk=true`, `supportMissing=0`, `damagedColumns=0`.
- Population at fixture: 16 villagers, 4/4 founders/guards equipped, 12/12 professional roles and profession Goals.
- Navigation trace closed with 25,494 records, 0 discarded, 0 failures.
- Instrumentation failures: 0.

Execution/cleanup:
- Dedicated `runServer --nogui`, no client or GUI launched, no user interaction or focus stealing.
- `fin.txt` and `launcher.log` persisted under `build/a2n/c4-full8-20261112-2240`.
- Server saved all dimensions/chunks and stopped normally; launcher reports no JVM with the RunId; no Java server process remained.
- Gradle: `BUILD SUCCESSFUL in 7m 54s`, 7 tasks (3 executed, 4 up-to-date); process exit code 0.
- `python tools/lint_aldea.py --strict`: OK. `git diff --check`: exit 0 (only Git CRLF/LF advisory warnings).
- Branch `feature/autonomous-village`, HEAD `518fee13fc15bfb9b92b0479d86b5c609d1947da`; no commit/push.
- `tools/a2n/A2NavHarness.java` SHA-256: `0B699503DCBE99D084AE4A073FE77650B72484B02BA97DED0829C889BFCA9358`.
- Protected `tools/a2n/A2NavHarness.java.partial` SHA-256: `2C3EBA9ED6E936B595E26650B796A624838B5CB4FF6CFD7B1804BE7A13552127`; unchanged.

**C4 is ACCEPTED.** Together with C1, C2, C3 and C5 already accepted, the C1–C5 acceptance gate is now closed. Block 6 multi-day soak is no longer blocked by C4; it remains a separate next task and has not been run as part of this acceptance.


## 9. Repetición final — c4-full8-20261112-2240 PASS; C4 aceptado

**Esta sección supersede el estado provisional de §5 y §8.** La repetición run8 terminó correctamente y confirma el PASS independiente de run7.

- fin.txt: C4_PASS (raid) - guardasCombatieron=true profesionalesReanudados=true asignacionesJobSiteLimpias=true supervivientes=16 trabajadoresEsperados=12 trabajadoresVivos=12 trabajadoresReanudados=12 bajasTrabajadores=0 trabajadoresNoResueltos=0 retiradosParaRecuperacion=2 jobSite=[1, 0, 0] fallosDeInstrumento=0.
- Fixture real: 496 columnas de muro, 992/992 celdas continuas presentes; 4/4 guardias fundadores con Goals y equipo; 12/12 trabajadores profesionales con Goals; ocho atacantes registrados en el asedio de prueba.
- Combate medido: cuatro UUID de guardias distintos adquirieron enemigo vivo; 15 impactos de guardia y cinco bajas atribuidas a guardias (guardKill=true). Seis muertes de atacantes confirmadas con salud 0; los dos atacantes restantes fueron retirados explícitamente por el arnés para la fase de recuperación.
- Recuperación: 12/12 UUID de trabajadores iniciales vivos y reanudados; 0 bajas de trabajadores; 0 UUID no resueltos; 16 aldeanos vivos; JOB_SITE 1 asignación, 0 huérfanas, 0 propietarios duplicados; Goals profesionales 12/12.
- Navegación: artefacto nav-trace.log cerrado por el arnés; sin fallos de instrumentación (fin.txt reporta 0). Servidor dedicado, sin cliente ni jugador conectado.
- Fases A=3000, B=6600, recuperación=1200 (fin de muestreo t=7800). Salida: BUILD SUCCESSFUL in 7m 54s; Overworld, Nether y End guardados completamente; apagado normal.
- Repetibilidad: run7 y run8 son dos ejecuciones independientes PASS consecutivas con 12/12 trabajadores vivos/reanudados y 0 bajas/no resueltos. C4 queda **ACEPTADO**. A2 completo y el soak multi-día permanecen abiertos/gated por sus propios criterios; este PASS no los declara aceptados.

### Hashes de evidencia run8

| Artefacto | SHA-256 |
|---|---|
| fin.txt | 904280984D65A5971F61977DEE0B1B407244AB4661A3479CF3ECED2AECC25259 |
| launcher.log | AD6C9500D8CB4AA0900DFEA5DA6FF8654EB343B7D2A21678D765EFB76A53A81D |
| game/logs/latest.log | 828D412FA3FB52C659B56CD2530A39DFA4FC2F2E489328F19A6E5BF73332DBBC |
| nav-trace.log | 8F46A616D54BCB28AF352CED597052A817F6F6C302248171D691C8D0DAEF63FC |
| seed/copied level.dat | 4D9516A994A6CFF1D50C9EA6667F2E4CD0CCB7EB96438F26815BDA4FCD94EEEF |

### Verificación de cierre

- Rama feature/autonomous-village; HEAD 518fee13fc15bfb9b92b0479d86b5c609d1947da; cero staged; sin commit ni push.
- Arnés canónico tools/a2n/A2NavHarness.java: 0B699503DCBE99D084AE4A073FE77650B72484B02BA97DED0829C889BFCA9358.
- Protegido tools/a2n/A2NavHarness.java.partial intacto: 2C3EBA9ED6E936B595E26650B796A624838B5CB4FF6CFD7B1804BE7A13552127.
- VillageManager.java: 634F85E789473A9133E68E90F7F3D5EC9C81B799C54BD3D897A0C6BA2F10A13F; build.gradle: 74A1647F5F8691A5CEC553AC5642E002C266E6E0B21AAB9F647555AA8C3941DA.
- python tools\\lint_aldea.py --strict: OK. git diff --check: exit 0 (avisos CRLF de Git solamente). compileJava: BUILD SUCCESSFUL antes del run8; el runServer completo también BUILD SUCCESSFUL.
- Sin JVM de Minecraft/Gradle del run8 activa al cierre; watcher terminó al encontrar fin.txt. Sin run\\saves, mundos históricos, GUI ni cambios de foco.


Trace detail for run8: nav-trace summary reports 25,494 records, 0 discarded, 0 failures. Each of the eight attackers has zero sampling gaps; first sample tick 1 and last sample tick recorded individually in the trace footer. Canonical harness and Gradle debug source copy match byte-for-byte at SHA-256 0B699503DCBE99D084AE4A073FE77650B72484B02BA97DED0829C889BFCA9358. Both run7 and run8 fin.txt independently contain C4_PASS. No Java process remained after run8 shutdown.

## 10. Boundary with A2 custom-navigation acceptance

A post-run read-only audit of `nav-trace.log` found the header `traceNav=true navCustom=false`. Run8 therefore validates C4 production defense/recovery and records navigation telemetry, but it is **not** an A2 custom-navigation acceptance run. The A2 feature is controlled by `-Ddevilrpg.nav.custom=true` or `DEVILRPG_NAV_CUSTOM=1`. The C4 launcher does not enable it; the existing A2 launcher `build/a2n/lanzar-a2n.ps1` exposes `-NavCustom` and sets the JVM property.

The run8 A2 telemetry had `entraronPerimetroEver=6/8`; idx4, idx7 and idx8 remained alive outside the perimeter and in PLANNING with no movement. This does not affect C4 acceptance, whose contract concerns guards, village survival, professional recovery and JOB_SITE ownership. It does mean A2 global remains open. A future A2 attempt must explicitly run with `-NavCustom true` and verify `navCustom=true` in the trace header; the agreed 8/8-in-three-distinct-seeds contract remains unchanged.
