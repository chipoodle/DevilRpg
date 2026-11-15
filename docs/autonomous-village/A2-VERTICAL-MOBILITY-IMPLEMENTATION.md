# A2 Vertical Mobility Implementation

Status: authorization recorded; implementation **not completed**. A2 remains FAIL.

## Authorization

On 2026-11-14 the user authorized the WALK/JUMP/BREAK/BUILD proposal, including vertical mobility
for idx8's unreachable upward BREAK waypoint. Scope excludes arbitrary village destruction,
wall weakening, threshold changes and C4 changes.

## Implemented so far

- Authorization recorded in `AGGRESSIVE-ZOMBIE-MOBILITY-DESIGN-PROPOSAL.md`.
- Existing WALK/BREAK/BUILD navigation retained; `NavLocalObjectiveProgressPolicy` preserved.
- No new JUMP or upward BUILD capability was completed in this stage.

## idx8 blocker

Run13 idx8: `replans agotados (8/8)` at BREAK waypoint `701,67,457`, mob around `701,62,457`.
The waypoint is ~5 blocks above and outside current action reach; the same waypoint survives every
bounded rearm. A route-selection/jump/build upward transition is required. No hardcoded idx8
special case was added.

## Required next implementation

1. Capability interface for vertical reachability (destination above, headroom, support, landing).
2. JUMP transition for reachable one-block/step-up cases.
3. Bounded BUILD ascent when no jump landing exists; validate material/support/collision, verify
   placement and retain mission/route identity.
4. Reject an unreachable upward BREAK waypoint before exhausting replans; select an alternative
   local waypoint without losing the mission.
5. Tests: valid/invalid jump, build validation, duplicate prevention, mission retention, idx8
   recovery, idx3/idx4 non-regression.

## Current verdict

A2 FAIL. Full acceptance was not run after the partial combat equipment change because focused
survival still failed. C4 remains preserved PASS.

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
