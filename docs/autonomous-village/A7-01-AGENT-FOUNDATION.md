# A7-01 — Agent Foundation and Navigation Ownership

Status: foundation IMPLEMENTED and TESTED in isolation. Observation-only; vanilla Brain and
all existing forestry execution remain active.

## Components

Package `com.chipoodle.devilrpg.agent`:

- `VillagerAgent` — one transient agent per villager UUID; profession, dimension,
  task identity/state, navigation lease. No entity reference, no NBT.
- `VillagerAgentRegistry` — entity join/leave/death/server-stop lifecycle; one agent
  per UUID, removed on leave/death, cleared on shutdown.
- `TaskPlanner` — pure `plan(WorldObservation)`.
- `TaskProposal` — immutable proposal with target, priority, claims, expected resources.
- `TaskExecutor` — execution contract only; not wired to forestry.
- `WorldObservation` — read-only snapshot.
- `ForestryTaskAdapter` — migration boundary only; no forestry replacement.
- `NavigationLease` — per-agent ownership, TTL, explicit priority preemption, renew,
  release, stale-owner rejection, diagnostics.

## Attachment

`EntityJoinLevelEvent` → `computeIfAbsent(UUID)`.
`EntityLeaveLevelEvent` / `LivingDeathEvent` → remove + invalidate.
`ServerStoppingEvent` → invalidate all + clear.
No entity references are retained; the server resolves each UUID per tick.

Persistence decision: transient in A7-01. No new NBT, no migration, no path persistence.
Legacy `DevilRpgForestryTask` untouched.

## Navigation ownership

The lease is a DevilRpg-side intent/ownership tracker. It does **not** intercept vanilla
`PathNavigation`, `WALK_TARGET`, `GoalSelector`, or Brain writes. Diagnostics are enabled
with `-Ddevilrpg.agentdiag=true` and emit transition-only `[AGENT]` / `[AGENT-LEASE]`
events. The missing interception point is documented for A7-02.

## Verification

- `compileJava`: BUILD SUCCESSFUL.
- `NavigationLeaseTest`: OK (acquire, renew, wrong agent/task release, preemption,
  expiry, invalidate).
- Agent smoke Run 193: 16 villagers, 16 agents, 0 missing.
- A5/A6 execution untouched; no NBT or controller changes.
