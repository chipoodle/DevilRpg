# A2 Bell Objective Implementation

Status: bell objective implemented; focused runtime did **not** reach/destroy the bell. A2 FAIL; C4 PASS.

## A. Sources inspected

- `VillageGenerator` bell generation (kiosk centre, `center.x, plazaLevel+1, center.z`; attachment FLOOR)
- `AggressiveZombieEntity` mission chain, `MoveToVillageCenterGoal`, `NavExecutor`
- `VillageManager.dentroDelRecinto`, `VillageGenerator.FENCE_RADIUS`
- existing `BreakBlockGoal`, `romperNodoDeRuta`, `NavBreakReachPolicy`
- campaign/doctrine docs and runs `121647`, `123936`, `125345`, `131627`

## B. Bell discovery

`VillageGenerator` places the bell at the kiosk centre one block above the plaza level:

```text
new BlockPos(center.x, nivel + 1, center.z), Blocks.BELL, FLOOR attachment
```

`AggressiveZombieEntity.localizarCampana(cota)` scans a bounded 9×9×5 volume around
`villageCenter` from `cota` to `cota+4` and stores the first actual `Blocks.BELL`.
It never assumes the bell remains valid: if the block is no longer a bell, the objective is
marked lost and no success is reported.

## C. Objective lifecycle

- `campanaObjetivo` persists for the attacker's lifetime/siege.
- `campanaDestruida` is set only after a successful server-side destroy and a verified air state.
- `BELL_OBJECTIVE_FOUND` is emitted once when located.
- `BELL_OBJECTIVE_LOST` is emitted if the block is replaced/removed by another actor.
- `BELL_DESTROYED` is emitted only after `destroyBlock` returns true and the block is air.
- Tactical step-back and combat target changes do not clear `campanaObjetivo`.
- No second navigation controller was added; the existing mission chain still reaches the kiosk area.

## D. Destruction

When the attacker is alive, physically inside the wall enclosure
(`dentroDeLaAldea(blockPosition, cota)`), and within 5 blocks of the bell
(`distanceSqr <= 25`), the entity calls:

```java
level().destroyBlock(campanaObjetivo, false)
```

and verifies `getBlockState(campanaObjetivo).isAir()`. Only then is the siege objective considered
completed. No client-only effect, no radius destruction, no guard/attribute changes.

**Reason for a dedicated check:** ordinary `romperNodoDeRuta` is gated by village protection and
route transitions; the bell is the siege victory block, so its destruction is an explicit
objective rather than a generic obstacle break. It does not bypass the attack/entry requirement.

## E. Tactical interaction

The previous doctrine correction is preserved: 40-tick, 3–7-block tactical step-back, max 2 uses,
no indefinite evasion. The bell objective is independent of that state and survives it.

## F. Tests

```text
NAV_BELL_OBJECTIVE_POLICY_TEST: OK (7 assertions)
ATTACKER_RETREAT_POLICY_TEST: OK (10 assertions)
ATTACKER_RETREAT_DESTINATION_POLICY_TEST: OK (12 assertions)
compileJava / compileTestJava: BUILD SUCCESSFUL
lint --strict: OK
git diff --check: PASS
```

Covers valid/lost bell, outside reach, out-of-range, dead actor, destroyed state.

## G. Focused runtime

Run `a2n-20261114-134455` (FaseB6000), completed with `fin.txt`:

- 8/8 spawned.
- Bell located: `661,72,533` (`BELL_OBJECTIVE_FOUND`) for every attacker.
- `entraronPerimetroEver=4`, `vivos=4`, `muertos=4`, `dentroParedAhora=0`.
- **No `BELL_DESTROYED` and no `DESTRUYO la campana`.**
- C4 PASS.

Conclusion: discovery and lifecycle work; destruction was not reached because no attacker
survived/reached the kiosk. This is a navigation/survival blocker, not a false success.

## H. C4

`fin.txt`: C4_PASS, 16 survivors, 12/12 workers alive/resumed, 0 casualties, jobSite [1,0,0],
4 removed for recovery, 0 instrumentation failures. No C4 logic changed.

## I. Preservation

Branch `feature/autonomous-village`, HEAD `518fee13…`. No reset/clean/stash/revert/commit/push.
No GUI/`run/saves/New World`. Historical artifacts preserved.

## J. Remaining blockers

1. No attacker reached the bell; 4/8 entered and none remained inside at t=6000.
2. Mission navigation still completes at the kiosk area but survival/guard combat prevents entry.
3. No runtime proof of physical bell destruction.

## K. Next action

Use the focused logs to determine why the four entrants die before/at the kiosk. Do not run full
12000 acceptance until one attacker reaches and destroys the bell in a focused run. The bell
objective itself is implemented and verified only at policy/state level.

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
