# NavExecutor Single-Authority Migration (2026-11-15)

## Decision

`NavExecutor` is now the only authority for directed zombie movement. The legacy
`AggressiveZombiePathPlanner`, `NavFeature` fallback flag and `MovementOwnership` suspension policy
are removed. Minecraft `PathNavigation` remains the internal actuator used by NavExecutor.

## Before / after

### Before

- `MoveToVillageCenterGoal` used NavExecutor only with `-Ddevilrpg.nav.custom=true`; otherwise
  `AggressiveZombiePathPlanner`.
- `PursueTargetGoal` always used `pathPlanner.navigateTo` and directly owned MOVE.
- `aiStep` suspended NavExecutor whenever combat owned movement.
- Escape water, retreat and patrol had direct `getNavigation().moveTo` fallbacks.
- Two planners could disagree; a distant target could suppress strategic movement.

### After

- `MoveToVillageCenterGoal` sends only the approximation chain to NavExecutor. No planner fallback.
- `PursueTargetGoal` keeps target selection, flank destination and 20-tick attack cadence, but:
  - sends the destination as a tactical objective to NavExecutor;
  - only sends when the destination actually changes;
  - enters a melee hold (`NavExecutor.holdForMelee`) at ≤2 blocks and attacks;
  - releases the hold and tactical objective when the target is lost, resuming the strategic mission.
- `aiStep` always ticks NavExecutor (except client side); no combat suspension.
- Tactical combat ownership is bounded by a 6-block leash; distant targets leave the strategic
  mission with NavExecutor.
- Retreat destinations are sent as tactical objectives; termination clears them.
- Water escape always uses `navegarTacticamente`; the vanilla fallback is removed. Swim/jump impulses
  remain physical locomotion under the escape goal.
- Home patrol and random patrol use NavExecutor (strategic home/objective and tactical wander).
- Direct `getNavigation().moveTo` no longer exists in `AggressiveZombieEntity`; only
  `getNavigation().stop()` and physical impulses remain.

## Deleted / modified

Deleted:
- `AggressiveZombiePathPlanner.java`
- `NavFeature.java`
- `MovementOwnership.java`
- `MovementOwnershipTest.java`

Modified:
- `AggressiveZombieEntity.java` — goals, tactical intents, hold, retreat/water/patrol migration.
- `NavExecutor.java` — `holdForMelee`/`releaseMeleeHold`, suspension diagnostics.
- `NavCombatIntentPolicy.java` (new) + test — melee hold, destination idempotence, tactical leash.
- `A2NavHarness.java` — removed old planner/feature probes; `navSuspendido`/target diagnostics remain.
- `build.gradle` — policy test list updated.

## APIs

- `NavExecutor.holdForMelee()` — stop directed movement while attacking, preserve mission.
- `NavExecutor.releaseMeleeHold()` — resume tactical/strategic planning.
- `NavExecutor.setTacticalObjective(...)` — moving combat destination.
- `NavExecutor.clearTacticalObjective()` — resume strategic mission.
- `NavCombatIntentPolicy.withinTacticalLeash(...)` — 6-block combat ownership bound.
- `NavCombatIntentPolicy.holdInMelee(...)` / `tacticalDestinationChanged(...)`.

## Tests

```text
.\gradlew.bat test                     PASS
NavCombatIntentPolicyTest              OK (6 assertions)
python tools/lint_aldea.py --strict    OK
git diff --check                       PASS
```

Final source audit: no production/test reference to `AggressiveZombiePathPlanner`, `NavFeature` or
`MovementOwnership` remains.

## Runtime migration validation

Configuration: seed 12345, FaseA 3000, FaseB 6000, budgets 900/8000, custom nav.

| Run | Spawn | Perimeter | Strict inside | C4 |
|---|---:|---:|---:|---|
| `a2n-20261115-234037` | 8/8 | 5/8 | 4/8 | 12/12 PASS |
| `a2n-20261115-235237` | 8/8 | 5/8 | 4/8 | 12/12 PASS |
| `a2n-20261116-000755` (6-block tactical leash) | 8/8 | 5/8 | 3/8 | 12/12 PASS |

Non-entrants in the leash run: idx4 `588,86,509`, idx7 `725,66,476`, idx8 `746,64,538`;
idx3/idx5 reached the perimeter but not strict entry. Deaths were legitimate combat.

No bell/conquest failure was observed, but entry is reproducibly below the pre-migration 7/8.
The migration is therefore **implemented and test-clean**, while **A2 acceptance is OPEN/BLOCKED**
on entry behavior. Full 12k was not run.

## Remaining blockers

1. Migrated combat/strategic intent yields only 5/8 perimeter and 3–4/8 strict entry at 6000.
   Next investigation: which intent is active for idx4/7/8 at closure (`combatActivo`, tactical
   objective, NavExecutor state) and whether the 6-block leash or the tactical hold is preventing
   the final approach.
2. C4 save/reload driver remains absent.
3. No full 12k acceptance run.

## Continuation

Update `NEXT-SESSION.md` to point at this report and the runtime evidence. Preserve diagnostic worlds
and local-only artifacts. No force-push.
