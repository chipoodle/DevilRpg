# A7-02 — Forestry Observation + Navigation Gateway Pilot

Status: snapshot, adapter, lease-aware gateway, and AUDIT/ENFORCE pilot implemented.
Legacy `VillagerLumberjackGoal` remains the production execution owner. Default mode is OFF;
no NBT schema changed.

## Snapshot

`ForestryTaskSnapshot` is immutable and read-only:

```text
villagerId, objectiveIndex, dimension, taskId, phase, controllerState,
target (immutable), carriedLogs, active
```

Produced by `VillagerLumberjackGoal.snapshot()` from the live phase, controller, target and
inventory. It never assigns, saves, mutates or navigates. Task identity is deterministic
from villager UUID + objective + target; stable while the target is unchanged and changes
when the target changes. No task ID is written to NBT.

`VillagerAgentRegistry` observes the lumberjack snapshot each server tick and stores it on
the transient agent.

## Adapter

`LegacyForestryTaskAdapter` translates an active snapshot to `TaskProposal`:

```text
forestry.<phase> @ target, priority WORK,
claim forestry:<targetLong>, expected logs = carriedLogs
```

It returns empty without an active target and never acquires a lease or executes.

## Gateway

`ForestryNavigationGateway` wraps the two forestry navigation commands:

- `tick` path-following move;
- `irAlObjetivo` initial move;
- exploration/cleanup stop;
- `stop()` cleanup.

Modes via `-Ddevilrpg.agent.navigationMode=OFF|AUDIT|ENFORCE` (default OFF):

- OFF: delegate immediately, no lease.
- AUDIT: check lease; log unauthorized; still delegate.
- ENFORCE: require exact agent/task lease; otherwise reject.

It delegates to `VillageManager.caminarHacia` / `PathNavigation.stop`; it does not plan paths.

Lease lifecycle in `VillagerLumberjackGoal`:

- acquire/renew in `syncNavigationLease` when target is non-null and mode is not OFF;
- replace an old task lease only after explicit release;
- release on cleanup, stop, target loss;
- expiry is handled by the lease.

Remaining bypasses: direct navigation in other professions, `VillageManager` navigation
outside forestry, `Villager` vanilla goals, and Brain `WALK_TARGET`. They are not classified
as unauthorized forestry commands.

## Runtime evidence

Run 194 — AUDIT:

```text
[AGENT-FORESTRY] uuid=b89fcee1... active=true task=948d3e03...
 phase=RECOGER_HARINA controller=TRAVELLING target=107,97,16
 proposal=forestry.recoger_harina@107,97,16
 owner=b89fcee1.../948d3e03...
 mode=AUDIT
[NAV-GATE] mode=AUDIT result=AUTHORIZED source=forestry command=MOVE ...
[A5] FIN PASS
```

Run 195 — ENFORCE:

```text
[NAV-GATE] mode=ENFORCE result=AUTHORIZED source=forestry command=MOVE ...
 task=948d3e03... target=107,97,16
[AGENT-FORESTRY] ... mode=ENFORCE
[A5] FIN PASS
```

Agent lifecycle smoke (A7-01 Run 193): 16 villagers, 16 agents, 0 missing.

## Verification

- `compileJava`: BUILD SUCCESSFUL.
- `NAVIGATION_LEASE_TEST`: OK.
- `lint_aldea.py --strict`: PASS.
- `git diff --check`: PASS.

Not tested: ENFORCE denial runtime, OFF-vs-AUDIT behavioral comparison, Brain interception,
other professions, save/reload of agent state.
