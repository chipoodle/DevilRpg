# A2 Death Telemetry and idx8 BREAK Replan

Status: death telemetry implemented and validated; idx8 lifecycle diagnosed but **not repaired**.
A2 remains FAIL.

## Damage/death telemetry

NeoForge APIs used (verified against NeoForge 21.1.249):

- `net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post`
  (`getEntity`, `getSource`, `getNewDamage`, `getOriginalDamage`).
- `net.neoforged.neoforge.event.entity.living.LivingDeathEvent`
  (`getEntity`, `getSource`).
- Registered on `NeoForge.EVENT_BUS` in `DevilRpg` constructor.
- Filter: `AggressiveZombieEntity`; enabled only when `NavTrace.on()` is true.
- No damage, health, knockback, AI or death behavior changes.

Events: `[A2_DAMAGE]` and `[A2_DEATH]`, including UUID, entityId, tick, position, amount,
original amount, health, source type, direct entity and causing entity.

## Focused validation

Run `a2n-20261114-015833`, FaseB6000. Telemetry captured 16 damage events and 4 deaths:

| idx | UUID | Death tick | Position | Cause | Direct/causing |
|---|---|---|---|---|---|
| 6 | `c83a320c-…` | 1353 | 612,71,545 | `minecraft:mob_attack` | `95066df1-…` |
| 1 | `a85dd0ba-…` | 1637 | 620,71,576 | `minecraft:mob_attack` | `95066df1-…` |
| 5 | `666b38ad-…` | 2125 | 636,71,545 | `minecraft:mob_attack` | `06086dfc-…` |
| 2 | `6a0225a6-…` | 2321 | 637,71,541 | `minecraft:mob_attack` (arrow contributed at 2283) | `06086dfc-…` |

All observed deaths are guard melee at village level, not falls, suffocation or navigation. The
fifth casualty did not occur in this 6000-tick run.

**Conclusion:** the five Run13 deaths are combat casualties (guard attacks), with exact source
available in the new telemetry. This is a combat/survival issue, not a navigation-death issue.

## idx8 BREAK lifecycle

UUID `5c8e7765-1afb-48d5-9e47-4d8d55bf246f` (Run13).

First failure: `NAV_FAIL replans agotados (8/8)` at `701,63,457`, objective `701,67,457`,
mission `725,71,533`, around tick 8041. Four rearmes are consumed by ~9525 while the same BREAK
waypoint is selected. Final `701,62,457`, `FAILED`, route 79, last action
`700,65,458:BREAK-ACERCARSE (dist=3.15)`.

Cause: the BREAK waypoint is ~5 blocks above the mob and outside its action reach; the current
architecture has no upward jump/build capability for this case. The rearm resets counters but the
same unreachable waypoint survives, so the finite budget exhausts again.

**No narrow navigation-only repair is available without adding a general upward mobility
capability (the unapproved mobility proposal) or changing route selection semantics.** That
requires explicit design/authorization; no speculative fix was added.

## Implications for approved acceptance

Criteria 2/3 remain blocked by two independent issues:

- entrants are killed by guards before the closing snapshot;
- idx8/other actors can be stranded on unreachable upward BREAK waypoints.

The approved threshold requires ≥1 alive inside. Satisfying it by weakening guards, changing
attacker survivability or altering combat would violate the campaign restrictions. A design
decision is required before changing combat/survival behavior.

## Validation

```text
compileJava                              BUILD SUCCESSFUL
NAV_LOCAL_OBJECTIVE_PROGRESS_POLICY_TEST  OK (6 assertions)
NAV_TERRAIN_REARM_POLICY_TEST             OK (9 assertions)
python tools/lint_aldea.py --strict       OK
git diff --check                          PASS
```

Focused run stopped after telemetry proof; no C4 regression observed, prior C4 PASS preserved.

## Next action

Decide whether A2 acceptance may include a combat/survival correction (attacker durability or
guard interaction) or whether the contract must require entrants to arrive late enough to survive.
For navigation, the idx8 upward BREAK case needs the authorized mobility design; do not implement
it implicitly.
