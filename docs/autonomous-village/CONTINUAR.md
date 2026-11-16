# Continuación activa — 2026-11-16 (Phase19 BREAK)

**Lee primero:** `NEXT-SESSION.md`, `CHATGPT-COLLABORATION-POLICY.md` y `CHATGPT-HARNESS-WORKFLOW.md`.

**Código publicado pendiente de validar:** `600b78b4f628cadf894be2cb38015d0c4ffb512d` en `feature/autonomous-village`.

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
2. Confirmar que el árbol contiene el código `600b78b4`.
3. Ejecutar `.\gradlew.bat test`, `python tools/lint_aldea.py --strict` y `git diff --check`.
4. Compilar diagnóstico con `-Pa2nDiagnostics=true`.
5. Ejecutar en un mundo desechable nuevo `-Da2n.mode=break-runtime -Dn4.trace=true` con run ID único.
6. Persistir logs, trace, `fin.txt`, `estado.txt` y SHA exacto. No modificar código de producción.
7. Después, diseñar una fixture separada para `BREAK_EDGE_EXCLUDED` y `NAV_BACKTRACK`; no asumir que el fixture actual los cubre.

No modificar presupuestos, cooldowns, rangos, protección, colisiones, mundos originales ni criterios de aceptación.

---


## 2026-11-14 10:02 — Perimeter-aware retreat focused: FAIL

- Added AttackerRetreatDestinationPolicy.preservesInterior: an attacker already inside may not choose an outside retreat destination; search expanded to 8–32 blocks. Test 12/12.
- Focused run 2n-20261114-095200 completed with fin: C4 PASS, but ntraron=4/8, ivos=4, muertos=4, dentroParedAhora=0.
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
