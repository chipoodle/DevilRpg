# A2 Attacker Combat/Survival Implementation

Status: partial. A2 remains FAIL.

## Authorization

User authorized attacker-only combat/survival improvements on 2026-11-14. Guards, guard
attributes/AI/equipment, C4 recovery and workers remain out of scope.

## Evidence

Run13 and focused telemetry: attackers had 10 health and took ~3.9 damage per guard melee hit,
dying in three hits. The existing `equiparAlAzar()` gave iron equipment to only 35% of zombies.
Focused `a2n-20261114-021400` showed 3.68 damage (partial armor), still three-hit deaths.

## Implemented

- `AggressiveZombieEntity.equiparAsaltanteDeAsedio()`: deterministic iron sword, helmet,
  chestplate, leggings, boots for `worldSiegeIndex >= 0` attackers.
- Existing random loadout remains for non-siege zombies.
- No guard source/attribute/AI change.

## Focused result

Run `a2n-20261114-021954`: guard melee damage reduced from ~3.9 to **1.6** (full iron armor).
Attackers still died from accumulated hits and one fall:
`db32...` t1747 melee, `803e...` t1790 `minecraft:fall`, `b24f...` t2118 melee.
No acceptance PASS.

This proves equipment alone is insufficient. A bounded combat disengagement/retreat policy is
required so attackers can break contact, recover, and resume the mission. That policy is not yet
implemented; no invulnerability/health multiplier or guard weakening was used.

## Validation

```text
compileJava / compileTestJava  BUILD SUCCESSFUL
lint --strict                  OK
git diff --check               PASS
```

## Next

Implement bounded tactical retreat/re-engagement in the attacker brain/goals, with tests for
bounded retries, threat recognition, mission resumption and unchanged guard behavior.

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
