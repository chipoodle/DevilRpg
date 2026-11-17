# Continuación activa — 2026-11-17 (A2 airborne DROP recovery)

## Último checkpoint

- Último Harness Run: `a2n-20261117-001624`, probado SHA `7aed2c683852969c323524bbed7d95965c330790`; static PASS, A2 Run 1 FAIL 4/8 strict y 5/8 perímetro.
- IDX4 evidenció `DROP_TAKEOFF_STEP_UP_ASSIST` con `onGround=false`; el assist se ejecutó con `onGround=false` y el mob cayó a y119; el trace anterior no registró si la ruta auxiliar se aceptó mientras estaba grounded, así que ese estado inicial no está probado. La revisión sí confirmó que faltaba una guarda grounded/source-support; el nuevo trace de aceptación registra ese estado y la corrección impide planificar desde una celda aérea.
- ChatGPT publicó la corrección inicial en `bd638e92dd86214dd8af3a17f1a6726daaea39e8` y añadió telemetría de admisión en `190bb6578906068e33c3a835feb2cacf7bc93085` (checkpoint final de código): exige fuente grounded/feet-valid/support/fits antes de iniciar la ruta auxiliar. Si está cayendo, conserva el DROP pendiente y reintenta desde la celda real una vez aterrizado.
- En STEP_UP de recuperación, se conserva el movimiento horizontal natural durante el salto/caída, pero JumpControl solo se solicita desde una fuente grounded y soportada. Si el mob aterriza y la arista de un bloque ya quedó obsoleta, se descarta la ruta auxiliar de forma fail-closed y se replantea la misión estratégica desde la posición física actual.
- No se han relajado presupuestos, colisión, soporte, clearance, seguridad DROP ni criterios de entrada. No teleport ni impulso directo de velocidad.
- La política agrega cobertura para el origen grounded y la geometría de STEP_UP. Tests pendientes de Harness; ChatGPT no ha ejecutado Gradle/lint/compile/runtime.
- IDX7 e IDX8 siguen independientes y sin cambios. C4 PASS `[1,0,0]`; save/reload OPEN. A2 OPEN; no Run 2 salvo que Run 1 alcance 8/8 estricto; no full 12k hasta cinco runs consecutivos 8/8 estricto.

## Próxima acción Harness

1. Sincronizar por fast-forward seguro y probar el SHA exacto del checkpoint de implementación publicado por ChatGPT.
2. Ejecutar Gradle tests, lint estricto, `git diff --check` y compilación de diagnósticos.
3. Solo si static PASS, ejecutar A2 Run 1. Verificar que la planificación de recuperación no arranca hasta `onGround=true`, `feetValid=true`, `support=true`, `fits=true`; cada STEP_UP assist debe partir grounded y soportado.
4. Capturar waypoint físico exacto, soporte/fit, take-off reached, restauración del cursor DROP y aterrizaje físico final. Run 2 prohibido si Run 1 no es 8/8 estricto.
5. Preservar mundos, artefactos y hashes. Harness no modifica producción.


Code checkpoint SHA: `7692a2cc7cbb4420531a7ff1ac0dc379badd10bf`; subsequent handoff commits are documentation-only. Always fetch the live branch HEAD before synchronization.

## 2026-11-16 — Phase19 BREAK runtime PASS + next independent fixtures

Harness run `a2n-20261116-174648` on `8b389672` returned **PASS (fixture COMPLETO)** for `break-runtime`: both targets air, exact 40-tick retry, both mobs at validated stands and both strategic missions resumed. This is not A2 siege acceptance.

ChatGPT then reviewed the recovery selector and found that the newest prior cell could be too close to the mob; executor rejected it and did not search older history. Published a distance-aware selector overload which skips candidates inside the existing 2.25 squared arrival tolerance. Added unit coverage for selecting an older useful cell. No threshold or history bound changed.

Two independent runtime fixtures are now published in `A2NavHarness`:
- `-Da2n.mode=break-edge-excluded`: two unverified attempts on the same stand->target pair, exactly 40 ticks apart; active same-terrain fingerprint exclusion; planner must select a different BREAK edge while both blocks remain solid and cursor does not pass BREAK.
- `-Da2n.mode=nav-backtrack`: corridor becomes blocked only after physical progress; require two no-progress failures, one historical backtrack, history <=16, exact physical return without teleport, and original strategic mission resumption.

Read-only diagnostics were added for current BREAK fingerprint exclusion and recovery state. The fixtures run in separate new disposable worlds with trace enabled.

Detailed report: `2026-11-16-PHASE19-BREAK-EXCLUSION-BACKTRACK-FIXTURES.md`.

**PENDING HARNESS**: validate the exact branch HEAD with Gradle tests, strict lint, diff-check and diagnostics compile; then execute each mode independently and persist all logs and SHA. ChatGPT has not run these checks. A2 remains OPEN; no full 12k before five consecutive strict 8/8 `DENTRO-PARED` runs. C4 save/reload remains OPEN.

## 2026-11-16 — Phase19 BREAK retry observation window (pending Harness)

Harness run `a2n-20261116-173828` confirmed both targets air, exact 40-tick retry and Scenario A mission resumption. Scenario B reached X=737, the target column, and remained `TRAVERSING` with movement commands toward X=738/739 when the fixture's 20-tick observation deadline expired. Its unchanged physical-crossing predicate had not yet passed.

ChatGPT changed only `A2NavHarness.java` in commit `8f2b9138937b23ff853ec839a0db9f7559d0673e`:
- bounded post-destruction observation increased to 100 ticks;
- original predicate remains objective equals strategic goal AND physical X > target X;
- progress samples every 10 ticks record position, objective, state, route node and executor description;
- deadline remains a hard FAIL and now includes final retry diagnostics.

No production code or gameplay acceptance criterion changed. No original A2/C4 worlds were used.

Detailed report: `2026-11-16-PHASE19-BREAK-RETRY-OBSERVATION-WINDOW.md`.

Next: Harness fetches and validates the exact branch HEAD, runs Gradle tests, strict lint, diff-check and diagnostics compile, then executes a new disposable `break-runtime`. Require both mission-resumption flags, both target-air results and exactly 40 retry ticks. After PASS, prepare independent fixtures for `BREAK_EDGE_EXCLUDED` and `NAV_BACKTRACK`. A2 remains OPEN; no full 12k before five consecutive strict 8/8 `DENTRO-PARED` runs.

## 2026-11-16 — Phase19 BREAK LOS correction (pending Harness)

Previous run `a2n-20261116-160714` was INVALIDO before mob/actuator execution: both production planner routes were empty, with 10/512 expansions and no expected BREAK edge. Static validation had passed, but no BREAK runtime behavior was tested.

ChatGPT reviewed the exact production source and identified a geometric flaw in the old LOS endpoint projection for the fixture's thin glass pane: the computed legacy endpoint falls below target Y. ChatGPT published a correction to raycast toward the target collision-shape center and added opt-in trace evidence for both LOS calculations and attack-stand candidate rejection. The fixture now initializes NavTrace before planning so these events are persisted.

- Source checkpoint: `9200ac31a350d9013b2abb8f39ce2b421fc9605a`.
- Detailed report: `2026-11-16-PHASE19-BREAK-LOS-CORRECTION.md`.
- Validation status: **PENDING HARNESS**. ChatGPT has not executed Gradle or Minecraft runtime locally.

Next Harness action: fetch the latest branch HEAD, preserve local-only artifacts, run tests/lint/diff-check/diagnostics compilation, then execute a new disposable `break-runtime` with `-Ddevilrpg.a2n.traceNav=true`. Capture `BREAK_STAND_CANDIDATE`, `BREAK_LOS_DIAG`, actual clip hits, route summaries and all normal run artifacts. Validate the exact fetched SHA. Harness must not modify production source; return any remaining rejection to ChatGPT.


**Lee primero:** `NEXT-SESSION.md`, `CHATGPT-COLLABORATION-POLICY.md` y `CHATGPT-HARNESS-WORKFLOW.md`.

**Código publicado pendiente de validar:** `6d5dc7b1d1f72a366d4f6b05626147b9e932481c` en `feature/autonomous-village`.

## División de trabajo obligatoria

- ChatGPT modifica el código de producción directamente en GitHub y publica commits.
- Harness sincroniza de forma segura, preserva el trabajo local, ejecuta pruebas/lint/runtime, mide y documenta.
- Harness no implementa correcciones de producción. Devuelve evidencia reproducible a ChatGPT.
- No declarar validado un SHA hasta ejecutar los checks sobre ese SHA exacto.

## Estado más reciente

- Harness reportó PASS de Gradle/lint/diff-check sobre `ecdfaff6`, pero runtime no observó BREAK.
- La revisión posterior identificó que el grafo confundía el estado WALK al stand con el estado posterior a romper el bloque. También encontró que la proximidad podía avanzar un nodo BREAK antes del actuador y que el ejecutor podía aceptar una celda adyacente como stand.
- ChatGPT publicó correcciones en `NavGraphSearch`, `NavPlanner`, `NavExecutor`, añadió `NavBreakRoutePolicy`, dos pruebas nuevas y el modo determinista `A2NavHarness -Da2n.mode=break-runtime`.
- Las pruebas nuevas están registradas pero **no ejecutadas**. El runtime fixture está implementado pero **no ejecutado**.
- Informe detallado: `2026-11-16-PHASE19-BREAK-GRAPH-STATE-FIX.md`.
- A2 sigue OPEN: último focused 5/8 estricto, 6/8 perímetro. No ejecutar 12k hasta cinco corridas consecutivas 8/8 estrictas.
- C4 focused fue PASS en `a2n-20261116-112413`; save/reload sigue OPEN.

## Próxima acción

Harness debe:
1. Fetch del HEAD actual y fast-forward seguro; preservar todos los artefactos locales.
2. Confirmar que el árbol contiene el código `6d5dc7b1d1f72a366d4f6b05626147b9e932481c`.
3. Ejecutar `.\gradlew.bat test`, `python tools/lint_aldea.py --strict` y `git diff --check`.
4. Compilar diagnóstico con `-Pa2nDiagnostics=true`.
5. Ejecutar en un mundo desechable nuevo `-Da2n.mode=break-runtime -Ddevilrpg.a2n.traceNav=true` con run ID único.
6. Persistir logs, trace, `fin.txt`, `estado.txt` y SHA exacto. No modificar código de producción.
7. Después, diseñar una fixture separada para `BREAK_EDGE_EXCLUDED` y `NAV_BACKTRACK`; no asumir que el fixture actual los cubre.

No modificar presupuestos, cooldowns, rangos, protección, colisiones, mundos originales ni criterios de aceptación.

---


## 2026-11-14 10:02 — Perimeter-aware retreat focused: FAIL

- Added AttackerRetreatDestinationPolicy.preservesInterior: an attacker already inside may not choose an outside retreat destination; search expanded to 8–32 blocks. Test 12/12.
- Focused run 2n-20261114-095200 completed with fin: C4 PASS, but ntraron=4/8,
ivos=4, muertos=4, dentroParedAhora=0.
- Four deaths all minecraft:mob_attack: ticks 884, 1117, 1709, 1819. Retreat still does not keep entrants alive to the closing snapshot.
- No full acceptance run was launched after this negative focused result. A2 remains FAIL; C4 PASS preserved.
- Next: death timing shows guards kill entrants before/around retreat; a general inside-combat/evasion solution (or authorized survival mechanism) is still required. Do not change threshold/C4.


## 2026-11-14 — Owner-confirmed difficulty design / next verification

The intended existing difficulty mechanic is world-global: distance from the one shared world spawnpoint scales both custom-spawned zombies and siege attackers. Shared world play time increases threat for newly spawned entities; existing entities retain their spawn-time attributes. Multiplayer must not use nearest-player selection or player-specific spawn/bed anchors.

The previous audit reports `nearest player's anchor/spawn`, so the exact production reference remains a verification task, not a proven match. Trace both `AggressiveZombie` spawn paths read-only and verify independence from player positions/player count before code changes.

Future (not implemented): move temporal progression configuration into each creature's `SpawnProfile`. Canonical custom creature names: `AggressiveZombie` and `AggressiveVex` (rename the custom Vex throughout source and strings, with save/registry compatibility review). Then implement profile refactor and rename as separate tested changes. No gameplay/balance/acceptance changes; A2 FAIL, C4 unchanged.

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
un/saves; hashes match; structural verification only.


## 2026-11-16 — Phase19 BREAK + recovery implementation published

- Base remote HEAD: `8f5a74fb9137abbac0a53dc44c981e81d22f9062`; implementation commit recorded in Phase19 report.
- BREAK planning now validates the predecessor's full AABB/support, target protection/breakability, existing reach and a raycast to the intended block.
- BREAK execution acts from the physical stand cell, never calls `moveTo` on the solid block, verifies air, then replans.
- Failed stand->target edges are fingerprint-excluded (max 32) until geometry changes; alternate approach edges remain searchable.
- Recovery history stores at most 16 physically occupied, valid feet cells. First no-progress failure replans; second may backtrack once to a still-valid prior cell as a tactical objective, preserving the strategic mission. Mission progress rearms the mechanism.
- Added pure policy tests; **not run by ChatGPT**. Harness must fetch this commit first, run Gradle/lint/diff-check, then targeted runtime and A2/C4 acceptance checks. No PASS claimed.

## 2026-11-16 — Phase19 Harness validation

- Synced `14df0115` -> `ecdfaff690824607c8e7f0189a356f1fe030b229` by fast-forward; requested code `f43eb11e` is an ancestor.
- Gradle test PASS, strict aldea lint PASS, diff-check PASS; all five BREAK/recovery policy tests executed.
- Focused diagnostic `a2n-20261116-112413`: 6/8 perimeter, 5/8 strict, C4 PASS [1,0,0].
- No BREAK_STAND_APPROACH / BREAK_EXECUTED_FROM_STAND / BREAK_EDGE_EXCLUDED / NAV_BACKTRACK runtime events; direct BREAK behavior remains BLOCKED.
- Report: docs/autonomous-village/2026-11-16-PHASE19-HARNESS-VALIDATION.md. A2 remains OPEN; no 12k.

## 2026-11-16 — Phase19 BREAK graph-state runtime validation

- Synced `289406f6` -> `d440b3e7` (contains implementation `6d5dc7b1`) by fast-forward.
- Static PASS: Gradle, strict lint, diff-check, diagnostics compile. New tests executed: graph-state 7, route-policy 11, attack-position 7.
- `break-runtime` fixture `a2n-20261116-160714` returned **INVALIDO**: both production planner routes empty (`breakTargetFound=false`), `encolados=9`, `exp=10/512`, `rechazadasPorNodoNoPisable=42`. No BREAK/retry/backtrack events.
- Harness did not change code. Candidate LOS/aim defect returned to ChatGPT. A2 remains OPEN; no 12k.
- Report: docs/autonomous-village/2026-11-16-PHASE19-BREAK-RUNTIME-INVALID.md.

## 2026-11-16 — Phase19 LOS correction runtime

- Synced `1e25b777` -> `76d3466c` by fast-forward; LOS source `9200ac31`.
- Static PASS: Gradle, strict lint, diff-check, diagnostics compile.
- `break-runtime` `a2n-20261116-163239`: **FAIL** (fixture timeout labelled BLOCKED). Both plans contain BREAK and LOS hits `697/737,184,568`, but mobs never move from `696/736,184,568` to stand `696/736,184,567`; one-node path marked done, `retryCalls=0`, no target air.
- Harness did not change production code. Report: docs/autonomous-village/2026-11-16-PHASE19-BREAK-LOS-RUNTIME-FAIL.md. A2 remains OPEN; no 12k.


## 2026-11-16 — Phase19 BREAK adjacent stand approach correction

Harness run `a2n-20261116-163239` is a behavioral FAIL: planner routes and LOS are valid, but `moveTo` returns a completed one-node path for the adjacent stand. The mob never physically occupies the stand and the BREAK actuator is never called.

ChatGPT published `11be611d90c4d65772813550c2a365a524ee5147` in `NavExecutor.ejecutarBreakDesdePosicionDeAtaque`. When navigation is already done while the mob is outside the exact stand, the executor now uses the existing bounded adjacent nudge (`NavAdjacentApproachPolicy` + `MoveControl.setWantedPosition`), only if distance <=2, the AABB fits and the stand has support. Exact stand occupancy is still required. No cooldown, reach, budget, LOS, collision or acceptance criteria changed.

New trace: `BREAK_STAND_NUDGE`. Detailed report: `2026-11-16-PHASE19-BREAK-STAND-APPROACH-FIX.md`.

Status: **PENDING HARNESS**. ChatGPT has not run static validation or runtime on this correction. Harness must fetch exact current branch HEAD, preserve local artifacts/worlds, run tests/lint/diff-check/diagnostics compile and then execute a new disposable `break-runtime`. Validate both targets become air, actuator events, exactly 40 ticks between failed first attempt and retry, and strategic mission resumption. A2 remains OPEN; no 12k until five consecutive strict 8/8 runs. C4 save/reload remains OPEN.

## 2026-11-16 — Phase19 BREAK stand nudge runtime

- Synced `d0f9c7a5` -> `0d8fc521` by fast-forward; production fix `11be611d`.
- Static PASS: Gradle, strict lint, diff-check, diagnostics compile.
- `break-runtime` `a2n-20261116-172549`: nudge emitted, both mobs reach exact stands, both panes air, retry delta exactly 40, success mission resumed.
- Fixture closes at retry-air tick before the retry mob moves (`x=736`, target `x=737`), so `retryMissionResumed=false` is fixture acceptance timing, not a nudge/BREAK failure.
- Report: docs/autonomous-village/2026-11-16-PHASE19-BREAK-STAND-NUDGE-RUNTIME.md. A2 remains OPEN; no 12k.


## 2026-11-16 — Phase19 BREAK retry fixture timing correction

Harness run `a2n-20261116-172549` confirmed the stand nudge, exact stand occupancy, both BREAK target-air results and exactly 40 ticks between the first unverified actuator and retry. Scenario A resumed its strategic mission. The only failure was that the fixture evaluated Scenario B mission progress in the same tick that the retry actuator destroyed the block.

ChatGPT changed only `src/main/java/com/chipoodle/devilrpg/debug/A2NavHarness.java` in commit `4c3f446aeaa893db7ff7bcfc1c5f690f94e37332`: retry mission-progress evaluation now starts only after `now > breakRetrySecondTick`, and the fixture allows up to 20 ticks after destruction for the same objective + physical-progress predicate. If it still does not resume by the deadline, the fixture reports FAIL. Production source is unchanged.

Detailed report: `2026-11-16-PHASE19-BREAK-RETRY-FIXTURE-TIMING.md`.

Status: **PENDING HARNESS**. Fetch and validate exact current HEAD, run Gradle tests, strict lint, diff-check and diagnostics compile, then execute a new disposable `break-runtime`. Require both targets air, exactly 40 ticks, and both mission-resumption flags. Preserve all artifacts and original worlds. A2 remains OPEN; no 12k until five consecutive strict 8/8 runs. C4 save/reload remains OPEN.

## 2026-11-16 — Phase19 BREAK retry runtime

- Synced `81a3b2f1` -> `08cf0e8c` by fast-forward.
- Static PASS: Gradle, strict lint, diff-check, diagnostics compile.
- `break-runtime` `a2n-20261116-173828`: both targets air, retry exactly 40 ticks, success mission resumed; retry `MISSION_RESUMED` false after 20 ticks because mob at x=737/target x=737 but moving toward 738/739.
- Fixture acceptance timing remains; no production change by Harness. Report: docs/autonomous-village/2026-11-16-PHASE19-BREAK-RETRY-RUNTIME-FAIL.md. A2 remains OPEN; no 12k.

## 2026-11-16 — Phase19 BREAK success/retry COMPLETO

- Synced `d7486315` -> `8b389672` by fast-forward; code `8f2b9138`.
- Static PASS: Gradle, strict lint, diff-check, diagnostics compile.
- `break-runtime` `a2n-20261116-174648` COMPLETO: both targets air, retryCalls=2, delta=40, success mission resumed, retry mission resumed at x=738 after 23 ticks.
- Independent `BREAK_EDGE_EXCLUDED` and `NAV_BACKTRACK` fixtures still required; not executed in this run.
- Report: docs/autonomous-village/2026-11-16-PHASE19-BREAK-RETRY-PASS.md. A2 remains OPEN; no 12k.

## 2026-11-16 — Phase19 BREAK exclusion + backtrack runtime

- Synced `3776d463` -> `7e7ab1fe` by fast-forward.
- Static PASS: Gradle, strict lint, diff-check, diagnostics compile; NavRecoveryControllerTest 10 assertions.
- `break-edge-excluded` `a2n-20261116-180242`: **COMPLETO**; one fingerprint exclusion, 40-tick retry, alternative BREAK, both blocks solid, mission preserved.
- `nav-backtrack` `a2n-20261116-180555`: **FAIL**; selected 937 from 939, stopped at 938 because generic arrival tolerance cleared tactical objective before exact return; mission not resumed.
- Report: docs/autonomous-village/2026-11-16-PHASE19-EXCLUSION-BACKTRACK-RUNTIME.md. A2 remains OPEN; no 12k.


## 2026-11-16 — Phase19 backtrack exact-arrival correction (PENDING HARNESS)

- Previous run `a2n-20261116-180555`: backtrack selected 937 from 939 but generic arrival tolerance cleared it at 938; no physical return and no mission resumption.
- ChatGPT published exact-cell arrival gate; source checkpoint `889072d235a44d0efc2de7ad955333b39b1d8b03`. Ordinary tactical arrival behavior and all budgets/cooldowns unchanged. Added pure policy regression assertions and `BACKTRACK_PROXIMITY_SUPPRESSED` trace.
- Static and runtime validation are pending Harness. Validate exact live HEAD with Gradle tests, strict lint, diff-check, diagnostics compile, then disposable `nav-backtrack` only.
- Required: one selection, physical occupancy of the selected cell, mission resumed, strategic mission preserved, history ≤16, no teleport.
- `BREAK_EDGE_EXCLUDED` remains PASS. A2 OPEN; no full 12k until five consecutive strict 8/8; C4 save/reload OPEN.

## 2026-11-16 — Phase19 NAV_BACKTRACK exact arrival runtime

- Synced `9a5d075d` -> `e2fe0873` by fast-forward.
- Static PASS: Gradle, strict lint, diff-check, diagnostics compile; three new NavArrivalPolicy backtrack assertions and NavRecoveryControllerTest 10 passed.
- `nav-backtrack` `a2n-20261116-182234`: **FAIL**. BACKTRACK_PROXIMITY_SUPPRESSED works at distSqr=1.935, but mob stays at 938 with completed route to 937; no physical step/mission resume.
- Missing adjacent nudge/exact-step for recoveryBacktrackObjective. Report: docs/autonomous-village/2026-11-16-PHASE19-BACKTRACK-EXACT-ARRIVAL-FAIL.md. A2 OPEN; no 12k.


## 2026-11-16 — Phase19 backtrack route-cursor + adjacent nudge correction (PENDING HARNESS)

- Previous run `a2n-20261116-182234`: exact objective arrival gate worked, but WALK route node `937` was still consumed by generic waypoint tolerance at `938`; cursor reached idx=2 and no nudge ran.
- ChatGPT published checkpoint `cc817c6314d2b2055dfbba578c5565e3bff9fedf`: exact-cell gate now also applies inside `nodoAlcanzado()`, preserving the active WALK node until physical occupancy. Existing adjacent nudge helper then runs when navigation is done, guarded by Manhattan distance, AABB fit and support. Added two diagnostic trace events and pure policy assertions.
- Static/runtime checks are pending Harness. Validate exact live HEAD and run only disposable `nav-backtrack` after static checks. Require exact physical occupancy, one backtrack selection, mission resumed, history <=16, no teleport.
- A2 OPEN; no full 12k before five consecutive strict 8/8; C4 save/reload OPEN.

## 2026-11-16 — Phase19 NAV_BACKTRACK exact arrival PASS

- Synced `58cb27aa` -> `fb93541e` by fast-forward; test commit `cc817c63`.
- Static PASS: Gradle, strict lint, diff-check, diagnostics compile; NavArrivalPolicy cursor-occupancy assertions and NavRecoveryControllerTest 10 passed.
- `nav-backtrack` `a2n-20261116-185434`: **COMPLETO**. Selected 937, adjacent nudge from 938, physical return, mission resumed tick103, history6, maxStep0.114, one backtrack, no teleport.
- BREAK success/retry, BREAK_EDGE_EXCLUDED and NAV_BACKTRACK fixtures individually validated. A2 remains OPEN; no 12k.
- Report: docs/autonomous-village/2026-11-16-PHASE19-NAV-BACKTRACK-EXACT-ARRIVAL-RESULT.md.

## 2026-11-16 — A2 strict siege gate run 1

- Baseline SHA `2d47d628` (already current). Run `a2n-20261116-190555`: 8/8 spawn, 5/8 perimeter, 5/8 strict, C4 PASS [2,0,0].
- Strict entrants: idx1 558, idx2 667, idx3 1062, idx5 702, idx6 649. Non-entrants idx4/7/8, all alive.
- Terminal failures: idx8 no valid local waypoint/budget (t3411), idx7 BUILD no-progress budget (t4209), idx4 replans 8/8 + DROP rejections (t5147).
- Gate 0/5 consecutive; full 12k not run. Report: docs/autonomous-village/2026-11-16-A2-STRICT-GATE-RUN1-FAIL.md.

## 2026-11-16 — A2 Run 1 forensic extract

- Read-only extraction from `a2n-20261116-190555`; no runtime, no production changes.
- IDX4/IDX8 shared code defect confirmed: route nodes use Step.state instead of Step.feet; DROP node y78 while mob at y77 gives dy=1 rejection.
- IDX7: exp=901/900 on 10/10 attempts, BUILD-FALLIDO, final full plan summary not persisted.
- IDX8: 81 global front waypoints, 0/64 valid local, A2_LOCAL_STAIR_WRITE INC_FAIL NO_LOCAL_OBJECTIVE.
- Report: docs/autonomous-village/2026-11-16-A2-RUN1-FORENSIC-EXTRACT.md. A2 remains OPEN; no 12k.


## 2026-11-16 — A2 DROP route-node feet correction (PENDING HARNESS)

- Forensic source: `a2n-20261116-190555`, SHA `2d47d628724a677269e1c3d895759154f56a52c9`.
- Confirmed IDX4/IDX8 defect: local and global route reconstruction used graph state Y for DROP instead of validated feet/landing Y.
- ChatGPT added `NavRouteNodePolicy` and applied it to complete global routes, partial/frontier global routes and local A* reconstruction. DROP now uses `(state.x, Step.feet, state.z)`.
- BREAK retains stand position in `Node.pos` and solid target in `Node.actionPos`; other transitions retain canonical state.
- Added `NavRouteNodePolicyTest` with 6 assertions and registered it in `policyTest`.
- Commits: `866d8169` (policy), `38a15bb7` (production reconstruction), `cdf4c9f5` (test), `b19f413c` (test registration).
- Report: `docs/autonomous-village/2026-11-16-A2-DROP-ROUTE-FEET-FIX.md`.
- ChatGPT did not execute tests, lint, diff-check, diagnostics compile or runtime. **PENDING HARNESS** on exact live HEAD.
- IDX7 BUILD failure/local 900-expansion exhaustion is intentionally deferred until the DROP correction is measured. No budget, collision, safe-drop or acceptance criteria were weakened.
- Next: Harness validates static checks, then reruns A2 Run 1 only. Run 2 is forbidden unless Run 1 reaches 8/8 strict. Five consecutive 8/8 strict runs remain the gate before full 12k.
- A2 OPEN; C4 save/reload OPEN; original worlds and previous run artifacts remain protected.

## 2026-11-16 — A2 Run 1 after DROP-feet correction

- Synced `bd1e543f` -> `5604ed03` by fast-forward; confirmed commits 38a15bb7/cdf4c9f5/b19f413c.
- Static PASS including NavRouteNodePolicyTest 6 assertions, NavArrivalPolicy, lint and diagnostics compile.
- A2 Run 1 `a2n-20261116-195651`: FAIL 5/8 strict, 5/8 perimeter, C4 PASS [1,0,0].
- IDX4 unchanged: mob at 596,77,498 below landing 597,78,499; dy=1 DROP rejections, replans 8/8. IDX7 exp=901/900 10/10; IDX8 no local waypoint/global budget.
- Report: docs/autonomous-village/2026-11-16-A2-RUN1-DROP-FEET-FAIL.md. A2 OPEN; no 12k.


## 2026-11-17 — A2 IDX4 physical DROP take-off recovery correction (PENDING HARNESS)

- Runtime evidence `a2n-20261116-195651` showed IDX4 at `596,77,498` with landing `597,78,499` (dy=1).
- Source diagnosis: `origenCompatible` accepted +/-1 Y, so the executor treated a mob one block below its planned take-off as already compatible. The subsequent DROP validation rejected the landing as above the mob, and route invalidation/replanning repeated without issuing the move back to the take-off.
- Added `origenFisicamenteAlcanzado`: horizontal tolerance remains <=1, but Y must equal the planned take-off Y. Both executor decision points now use this stricter physical-occupancy predicate.
- Added `NavDropTakeoffPolicyTest` (5 assertions) and registered it in `policyTest`.
- Commits: `199d905d` policy, `88182bcc` executor, `21f31149` test, `de0ff4d5` registration.
- Report: `docs/autonomous-village/2026-11-17-A2-IDX4-DROP-TAKEOFF-RECOVERY-FIX.md`.
- ChatGPT did not execute tests, lint, diff-check, diagnostics compile or runtime. **PENDING HARNESS** on exact live HEAD.
- Harness: static checks, then A2 Run 1 only. Capture command to return to planned take-off Y, exact Y occupancy, and the next DROP outcome. Do not bypass the height guard.
- IDX7 BUILD/900-expansion and IDX8 invalid-local-waypoint remain separate and untouched. No Run 2 unless Run 1 reaches 8/8 strict; no full 12k until five consecutive 8/8 strict runs. A2 OPEN; C4 save/reload OPEN.

## 2026-11-16 — A2 Run 1 DROP take-off recovery

- Synced `c03a61e1` -> `6d294c1d` by fast-forward; commits 199d905d/88182bcc/21f31149/de0ff4d5.
- Static PASS incl NAV_DROP_TAKEOFF_POLICY_TEST 5 assertions, lint and diagnostics compile.
- A2 Run 1 `a2n-20261116-201759`: FAIL 4/8 strict, 5/8 perimeter, C4 PASS [1,0,0].
- IDX4 DROP is route index0: origenPlan null, no ACERCARSE-ORIGEN-DROP, mob stays y77 below y78; dy=1 repeated. IDX7/IDX8 unchanged.
- Report: docs/autonomous-village/2026-11-16-A2-RUN1-DROP-TAKEOFF-FAIL.md. A2 OPEN; no 12k.

## 2026-11-17 — A2 IDX4 reject non-descending DROP edge (PENDING HARNESS)

- Harness run `a2n-20261116-201759` on `6d294c1d`: 4/8 strict, 5/8 perimeter. IDX4 first route node was DROP at routeIndex=0, so no predecessor/origenPlan existed; mob `596,77,498`, landing `597,78,499`, dy=1.
- Source diagnosis: local planning starts from `mob.blockPosition()` and initializes `piesDesde=start.getY()`. `validarArista()` allowed a DROP whose `piesHasta` was equal to or above `piesDesde` as long as the generic vertical bound passed. This is an impossible DROP edge, not a missing recovery origin. `plannedFrom` would reproduce the same Y=77 source and cannot make the Y=78 landing a descent.
- Added `NavDropTransitionPolicy.descensoFisicoValido(takeoffFeet, landingFeet)` and reject `DROP` edges unless landing feet are strictly below source feet, before A* accepts the edge. Added optional `DROP_REJECTED_NON_DESCENDING` trace.
- Extended `NavDropTakeoffPolicyTest` from 5 to 9 assertions. Commits: `a4a4fd29` policy, `cd2024d0` planner, `865f9229` test.
- Report: `docs/autonomous-village/2026-11-17-A2-IDX4-NON-DESCENDING-DROP-FIX.md`.
- ChatGPT did not execute tests, lint, diff-check, diagnostics compile or runtime. **PENDING HARNESS** on exact live HEAD.
- Harness static validation, then A2 Run 1 only. Capture source feet, candidate, landing feet, rejected invalid DROP and alternative route. IDX7/IDX8 remain independent and untouched. No Run 2 unless 8/8 strict; no full 12k before five consecutive 8/8 strict runs. A2 OPEN; C4 save/reload OPEN.
## 2026-11-16 — A2 Run 1 non-descending DROP fix

- Synced `868f3f63` -> `2214366a` by fast-forward.
- Static PASS incl NAV_DROP_TAKEOFF_POLICY_TEST 9, NAV_ROUTE_NODE 6, lint and diagnostics compile.
- A2 Run 1 `a2n-20261116-211919`: FAIL 5/8 strict, 5/8 perimeter, C4 PASS [1,0,0].
- IDX4 unchanged: DROP 597,78,499 from 596,77,498, dy=1, 24 failures; no non-descending rejection/recovery event for its UUID. IDX7/IDX8 unchanged.
- Report: docs/autonomous-village/2026-11-16-A2-RUN1-NON-DESCENDING-DROP-FAIL.md. A2 OPEN; no 12k.


## 2026-11-17 — A2 IDX4 first-node DROP origin fix (PENDING HARNESS)

- Harness on `2214366a`: static PASS (DROP policy 9, route-node policy 6), A2 Run 1
  `a2n-20261116-211919`: FAIL 5/8 strict, 5/8 perimeter. IDX4 remains at
  `596,77,498` with DROP landing `597,78,499`, dy=+1, routeIndex=0.
- Source inspection: accepted route installation records `plannedFrom = mob.blockPosition()`.
  DROP executor resolved `origenPlan` only from the previous route node when routeIndex > 0,
  leaving the first DROP without an origin. For index 0, the recorded route start is the exact
  predecessor; for later nodes, the immediate previous node remains authoritative.
- Added `NavDropTransitionPolicy.origenPlanificado(routeIndex, routeStart, previousNode)`.
  Updated `NavExecutor` to use it and emit `DROP_TAKEOFF_ORIGIN_RESOLVED` for index 0.
  Existing exact-Y physical-origin guard and all landing/safety checks remain unchanged.
- Added three pure assertions; `NAV_DROP_TAKEOFF_POLICY_TEST` expected count is now 12.
  Existing test registration is unchanged.
- Commits: `b763b112` policy, `2518d703` tests, `183b7667` executor,
  `bc38d43a` report.
- Report: `docs/autonomous-village/2026-11-17-A2-IDX4-FIRST-NODE-DROP-ORIGIN-FIX.md`.
- No tests, lint, compile, or runtime were executed by ChatGPT. **PENDING HARNESS** on exact HEAD.
- Harness must capture routeStart/plannedFrom, live position, landing, resolved origin, recovery command,
  physical arrival at origin, and final DROP result. Static PASS first; A2 Run 1 only.
- IDX7/IDX8 remain independent and untouched. No Run 2 unless 8/8 strict; no full 12k until
  five consecutive 8/8 strict runs. A2 OPEN; C4 save/reload OPEN.

## 2026-11-16 — A2 Run 1 first-node DROP origin recovery

- Synced `31923d0f` -> `b73ca2de` by fast-forward.
- Static PASS incl NAV_DROP_TAKEOFF_POLICY_TEST 12, NAV_ROUTE_NODE 6, lint and diagnostics compile.
- A2 Run 1 `a2n-20261116-213836`: FAIL 5/8 strict, 5/8 perimeter, C4 PASS [1,0,0].
- IDX4 origin resolves 589,96,522 from live589,93,522; ACERCARSE-ORIGEN-DROP emitted but one-node path, no ascent, physical origin not reached. IDX7/IDX8 unchanged.
- Report: docs/autonomous-village/2026-11-16-A2-RUN1-FIRST-NODE-DROP-FAIL.md. A2 OPEN; no 12k.


## 2026-11-17 — A2 IDX4 executable vertical take-off recovery (PENDING HARNESS)

- Harness run `a2n-20261116-213836` on `b73ca2de`: static PASS (DROP policy 12, route-node 6);
  A2 Run 1 FAIL 5/8 strict, 5/8 perimeter. IDX4 resolved take-off `589,96,522` from live
  `589,93,522`, landing `589,95,521`, but vanilla PathNavigation returned a one-node path at
  the current cell and movement delta 0. Physical origin was not reached.
- Root cause: same-column vertical `moveTo` was not executable through vanilla PathNavigation.
- Added bounded recovery via the existing `NavPlanner.plan` from live position to exact take-off.
  The temporary route must be complete and may contain only WALK/STEP_UP/JUMP_UP. DROP, BREAK,
  BRIDGE, and BUILD are rejected. No direct vertical impulse, teleport, block placement, or
  destruction is used.
- The original DROP route/cursor is suspended and restored only after physical take-off occupancy
  at exact Y with support and clearance. Recovery waypoints require exact cell occupancy, support,
  and fit. STEP_UP uses existing bounded adjacent nudge; JUMP_UP reuses existing jump actuator.
- If planning fails, route stalls, or route is incomplete, recovery fails closed and the original
  mission is replanned from the live position. Existing budgets and safety predicates unchanged.
- Added `NavDropTransitionPolicy.rutaAscensoOrigenSegura` and four assertions;
  `NAV_DROP_TAKEOFF_POLICY_TEST` expected total: 16. Existing test registration unchanged.
- Commits: `df3b9e46` policy, `ebeda4de` tests, `2cc3aa50` executor,
  `d79189aa` stalled/oscillating cleanup, `d7056617` policy comments,
  `800329f6` executor comments, report `75b4a827`.
- Report: `docs/autonomous-village/2026-11-17-A2-IDX4-VERTICAL-TAKEOFF-RECOVERY-FIX.md`.
- ChatGPT did not run tests, lint, compile, or Minecraft. **PENDING HARNESS** on exact current HEAD.
- Harness: static checks first; then A2 Run 1 only. Capture complete auxiliary route, transitions,
  physical occupancy/support at each waypoint, jump outcome if used, take-off arrival, restoration
  of the original DROP cursor, and final physical landing.
- IDX7/IDX8 remain independent and untouched. No Run 2 unless 8/8 strict; no full 12k until five
  consecutive 8/8 strict runs. A2 OPEN; C4 save/reload OPEN.

## 2026-11-16 — A2 Run 1 vertical take-off recovery

- Synced `b6b398a8` -> `38a4b52e` by fast-forward.
- Static PASS incl NAV_DROP_TAKEOFF_POLICY_TEST 16, NAV_ROUTE_NODE 6, lint and diagnostics compile.
- A2 Run 1 `a2n-20261116-225217`: FAIL 5/8 strict, 5/8 perimeter, C4 PASS [1,0,0].
- IDX4 accepted STEP_UP-only recovery route but stalled at 588,104,521 vs takeoff588,107,520; later plans empty at 901/900; construction exhausted. IDX7/IDX8 unchanged.
- Report: docs/autonomous-village/2026-11-16-A2-RUN1-VERTICAL-TAKEOFF-FAIL.md. A2 OPEN; no 12k.


## 2026-11-17 — A2 IDX4 guarded STEP_UP execution + expansion budget fix (PENDING HARNESS)

- Previous Run 1 `a2n-20261116-225217` on `38a4b52e`: FAIL 5/8 strict. IDX4's accepted one-node STEP_UP recovery route stalled below take-off; subsequent recovery searches reported 901/900.
- Added `NavStepUpRecoveryPolicy`: natural JumpControl + MoveControl assist only for an adjacent one-block climb with blocked lower step, clear headroom, destination fit and support. Same-column and multi-block ascent are rejected. Exact physical occupancy/support/fit remain mandatory.
- Added `NavExpansionBudgetPolicy`; planner checks before increment, so the hard 900 limit cannot execute expansion 901. No budget increase.
- Extended `NavDropTakeoffPolicyTest` to 25 assertions. Expected `NAV_ROUTE_NODE_POLICY_TEST` remains 6.
- Production code checkpoint before handoff documentation: `491b7d0170501e9d86433a52d2703395a4029b06`.
- Report: `docs/autonomous-village/2026-11-17-A2-IDX4-STEP-UP-EXECUTION-FIX.md`.
- ChatGPT has not run Gradle/lint/compile/runtime. **PENDING HARNESS** on exact live HEAD. Static first, then A2 Run 1 only. IDX7/IDX8 unchanged; no Run 2 unless 8/8 strict; no full 12k until five consecutive 8/8 strict runs. C4 save/reload OPEN.

## 2026-11-17 — A2 Run 1 STEP_UP assist

- Synced `ed17b493` -> `7aed2c68` by fast-forward; production491b7d01.
- Static PASS incl NAV_DROP_TAKEOFF_POLICY_TEST 25, NAV_ROUTE_NODE 6, lint and diagnostics compile.
- A2 Run 1 `a2n-20261117-001624`: FAIL 4/8 strict, 5/8 perimeter, C4 PASS [1,0,0].
- IDX4 assist emitted (567,121 -> 566,122, support/fit true) but mob stalled at567,119; no REACHED, no 901/900. IDX7/IDX8 unchanged.
- Report: docs/autonomous-village/2026-11-17-A2-RUN1-STEP-UP-ASSIST-FAIL.md. A2 OPEN; no 12k.

## 2026-11-17 — A2 Run 1 grounded take-off recovery

- Synced `9df03028` -> `b3064596` by fast-forward; production190bb657.
- Static PASS incl NAV_DROP_TAKEOFF_POLICY_TEST 32, NAV_ROUTE_NODE 6, lint and diagnostics compile.
- A2 Run 1 `a2n-20261117-020800`: FAIL 5/8 strict, 5/8 perimeter, C4 **FAIL** [0,0,0].
- IDX4 only WAIT_GROUNDED (onGround=false); no accepted route/assist/REACHED; final construction exhausted. No 901/900. IDX7/IDX8 unchanged.
- Report: docs/autonomous-village/2026-11-17-A2-RUN1-GROUNDED-RECOVERY-FAIL.md. A2 OPEN; no 12k.
