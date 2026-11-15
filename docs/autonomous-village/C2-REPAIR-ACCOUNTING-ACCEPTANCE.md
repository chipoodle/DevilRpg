# C2 — Resource-to-repair accounting

**Status: PASS / ACCEPTED (2026-11-12).** The repair goal now requires a
matching warehouse material, withdraws exactly one unit at the mutation
boundary, verifies the resulting block state, and refunds the unit if the
world rejects placement.

## Production behavior

Changed `src/main/java/com/chipoodle/devilrpg/entity/goal/VillagerRepairGoal.java`:

- `canUse()` checks that the warehouse contains at least one matching repair
  item before reserving/starting a repair target. A target with no available
  material is skipped for the current search cycle.
- At completion of the work interval, the goal resolves the blueprint block's
  item form and calls `VillageStorage.quitar(..., 1)` exactly once.
- Farmland maps to `Items.DIRT` as its repair substrate.
- Blocks whose `asItem()` is `Items.AIR` have no supported material mapping and
  are not repaired for free.
- The goal verifies both the `setBlock` result and the resulting block state.
  If placement is rejected, the removed item is returned to the warehouse;
  if it cannot fit, the remainder is dropped at the worker.
- An already repaired/invalid target is released before any material withdrawal.

New construction remains out of scope until a production builder exists.

## Runtime acceptance

Run: `c2-repair-accounting-r2-20261112-202200`

Scenario: `c2-repair-accounting`; disposable `A5WALL` copy only.

Controlled blueprint target:
- Position: `77,97,-3`
- Expected state: `minecraft:stone_bricks`
- Required material: `minecraft:stone_bricks`
- Repair worker UUID: `83496b07-d1ce-4cb4-9212-a6db13d8cb9f`

The controller drove the production goal's `tick()` with this known target to
isolate the resource transaction:

| Stage | Starting material | Block result | Ending material | Result |
|---|---:|---|---:|---|
| No material | 0 | Remained air/damaged | 0 | PASS |
| Funded repair | 1 | Restored to stone bricks | 0 | PASS |
| Already-repaired target | 1 | Remained stone bricks | 1 | PASS |

Controller result detail:
`noMaterialSafe=true repaired=true afterRepair=0 invalidSafe=true afterInvalid=1`.

- `result.json`: PASS, tick 600.
- `shutdown.json`: resultSeen=true, result=PASS, malformed=false,
  joinedRequestedWorld=true, clientWindowHiddenWithoutActivation=true,
  focusStealActions=0, remainingTestClients=0, launcherTreeRemaining=0,
  clean=true.
- Runner completed with clean process sweep. Launcher exit code 1 is the
  runner's post-server client-tree termination, not a gameplay failure.

## Scope and fixture notes

The runtime fixture intentionally assigned a controlled blueprint target and
called the production goal's `tick()` method directly. It proves the
no-material, exactly-once funded repair, and already-repaired/no-withdrawal
branches. It does not claim a separate end-to-end acceptance of natural repair
target discovery or the forced setBlock-rejection/refund branch. The
`canUse()` material precheck is compile-validated and source-reviewed; its
optional direct fixture assertion was discarded because `canUse()` returned
before populating its skipped-target set, so that harness assertion did not
establish the intended precondition. That discarded fixture result is not a
production FAIL and is not used as acceptance evidence.

C2 is accepted for the defined resource-to-repair accounting contract. Do not
repeat this scenario unless its material mapping, warehouse transaction, or
block-placement invariants change.

## Excluded fixture-only attempts (2026-11-12)

After the accepted r2 run, two optional fixture iterations were executed:

| Run | Result | Detail |
|---|---|---|
| `c2-repair-accounting-r3-20261112-203000` | Excluded fixture FAIL | Optional assertion expected `canUse()` to populate the skipped-target set; observed `blocked=true, skipped=false`. |
| `c2-repair-accounting-r4-20261112-203100` | Excluded fixture FAIL | Same assertion and same result; no additional production evidence. |

Both runs entered the requested disposable world, kept the client window hidden
without activation, recorded `focusStealActions=0`, and ended with
`remainingTestClients=0`, `launcherTreeRemaining=0`, `clean=true`.
The failed assertion concerned an internal fixture observation: `canUse()`
returned before target selection, so the fixture could not infer a skipped
target from that set. It did not show a material withdrawal or a free repair.
The accepted r2 test directly exercised the production transaction boundary and
proved the contract required by C2. The current fixture no longer includes the
invalid optional assertion. Do not repeat r3/r4 or treat them as product FAILs.

