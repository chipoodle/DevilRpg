# C5 — Village identity, storage and active-job persistence

**Status: PASS / ACCEPTED (2026-11-12).** This is a disposable-world
save/reload acceptance for two independently identified villages. It complements
Lumberjack Phase 1; it does not replace the A5 task-state matrix or existing
multi-village reload scenarios.

## Fixture and exact saved state

Run root:
`build/c5-village-persistence-r3-20261112-201500`

GameDir:
`build/c5-village-persistence-r3-20261112-201500/game`

Only the disposable copy of `A5WALL` was used. No original save or
`run/saves` was touched.

| Field | Village 0 | Village 1 |
|---|---|---|
| Center | `80,97,0` | `430,106,0` |
| Worker UUID | `8b3681f6-dcb5-4116-90ee-f0a88ec6c863` | `8ad55df9-135e-4152-b919-ce6c1ea68854` |
| Profession | Fletcher (level 2) | Farmer (level 3) |
| Oak Logs in warehouse | 13 | 29 |
| Spruce Logs in warehouse | 5 | 7 |

Village 0's fletcher had an active persisted forestry task at save time:
- Phase: `TALAR`
- Controller: `TRAVELLING`
- Target: `98,98,0` (valid log tree)
- GoalSelector wrapper: `isRunning=true`

## Save run — PASS

Run: `c5-village-persistence-save-r3-20261112-201500`

- Scenario: `c5-save`
- Tick: 612
- `result.json`: PASS — saved two village identities, professions, exact
  storage fingerprints and active TALAR task.
- `shutdown.json`: resultSeen=true, result=PASS, malformed=false,
  joinedRequestedWorld=true, clientWindowHiddenWithoutActivation=true,
  focusStealActions=0, remainingTestClients=0, launcherTreeRemaining=0,
  clean=true.
- Runner process exited 0. The Gradle launcher itself was force-terminated
  after server shutdown (launcherExitCode=1); acceptance is based on the
  validated result/shutdown pair and clean process sweep.

## Reload run — PASS

Run: `c5-village-persistence-load-r3-20261112-201500`

- Scenario: `c5-load`
- Tick: 447; the persisted village-life poll restored the lumberjack Goal at
  world gameTime 12800.
- Both centers loaded at their exact saved positions; they did not merge.
- Both UUIDs were found in their respective village areas and both professions
  matched the snapshot.
- All four warehouse fingerprints matched exactly before work resumed:
  A oak 13/spruce 5; B oak 29/spruce 7.
- Worker A's task NBT remained Version 1 / TALAR / TRAVELLING with the same
  target. The normal village-life poll reinstalled exactly one
  `VillagerLumberjackGoal`; target `98,98,0`; duplicate profession goals
  `0/0`.
- After 260 ticks of resumed work, worker A moved **32.5532 blocks**;
  duplicate goals remained `0/0`.
- `result.json`: PASS — village identities/professions/storage preserved;
  active lumberjack resumed.
- `shutdown.json`: resultSeen=true, result=PASS, malformed=false,
  joinedRequestedWorld=true, clientWindowHiddenWithoutActivation=true,
  focusStealActions=0, remainingTestClients=0, launcherTreeRemaining=0,
  clean=true.
- Runner process exited 0. Gradle launcherExitCode=1 reflects the deliberate
  post-server client-tree termination, not the gameplay result.

## Fixture corrections recorded

Two earlier reload attempts found worker A but not worker B. The saved centers,
professions, storage and active task were intact; the fixture queried B before
its forced chunk had completed loading. The final fixture now records exact
worker positions, calls `getChunkAt` for both worker chunks after applying
forced-chunk tickets, waits five ticks, and only then queries UUIDs. The r3
run proves the corrected sequence. This was a harness-ordering issue, not a
production persistence defect.

## Acceptance

C5 passes its defined contract: village centers/identities remain distinct;
worker UUIDs and professions persist; warehouse quantities remain exact;
an active task survives save/reload; the normal village-life poll restores one
Goal; and work resumes without duplicate ownership.

Block 6 steps 1 and 2 are complete. The bounded multi-day soak remains gated
on C1–C5; do not start it until the remaining C2/C3/C4 acceptance dependencies
are reconciled. Current next actionable item is C2 repair/resource accounting;
A2/C4 remains separately blocked by the documented owner decision.
