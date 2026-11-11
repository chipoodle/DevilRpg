# A7-04 — Agent Navigation Runtime Acceptance

Status: **PARTIAL**. Actual gateway-core harness implemented and passing. Live expiry,
live cancellation, same-target generation, OFF/AUDIT comparison, and full lifecycle are
NOT RUN. Legacy Goal remains the executor.

## Baseline

```text
branch feature/autonomous-village
HEAD 97220be5453b57387002b9bc91935807cc665274
staged 0
unstaged 26
untracked 147
```

## Gateway harness

`NavigationDelegate` is a no-world functional test seam. Production wrappers delegate to
`VillageManager.caminarHacia` / `PathNavigation.stop`; `moveToCore` and `stopCore` are the
actual gateway decision/delegation paths.

`NavigationGatewayHarnessTest` asserts result and delegate call count:

- authorized move: AUTHORIZED, 1 call.
- missing lease ENFORCE: DENIED_EXPIRED, 0 calls.
- wrong task: DENIED_WRONG_TASK, 0 calls; original lease retained.
- expired lease: DENIED_EXPIRED, 0 calls.
- wrong agent: DENIED_WRONG_TASK, 0 calls.
- stale stop after preemption: DENIED_WRONG_TASK, 0 calls; new owner still stops.
- OFF: OFF_DELEGATED, 1 call.
- AUDIT: UNAUTHORIZED_AUDITED, 1 call.
- ENFORCE unattributed cleanup: CLEANUP_ALLOWED, 1 call (no newer owner to protect).

Result: `NAVIGATION_GATEWAY_HARNESS_TEST: OK`.

## Cancellation

`VillagerAgent` tracks the last delegated task. Stop policy:

- exact current owner may stop;
- a newer owner blocks stale stop;
- a completed/invalidated task may clean up only its own attribution;
- unattributed cleanup is allowed;
- unknown attribution is denied in ENFORCE.

No live cancellation run was performed. Path attribution is logical (last gateway task),
not a reading of `PathNavigation`'s internal path owner.

## Task generation

`VillagerLumberjackGoal` has transient `agentTaskId`/`agentTaskTarget`, regenerated on a
new/changed target and cleared on null. No NBT field. Same-target successive execution was
NOT RUN.

## Runtime evidence

- Run 194 AUDIT: snapshot/adapter/lease authorized, `RECOGER_HARINA`, target 107,97,16.
- Run 195 ENFORCE: authorized movement.
- Run 196 ENFORCE: wrong task and null-agent denials; authorized movement continued.
- Run 193: 16 villagers, 16 agents, 0 missing.

These runs predate the final gateway-core extraction; they demonstrate the production
integration, not the new delegate-count harness.

## NOT RUN

- Live lease expiry with renewal suppression.
- C1–C4 live cancellation / agent invalidation.
- Same-target successive task identity.
- OFF-vs-AUDIT deterministic comparison.
- Full TALAR → PLANTAR → ABONAR/GROWING → ENTREGAR observation.
- ENFORCE expired-lease live command.

## Non-regression

No forestry operation, controller, planner, storage, pantry, NBT, or village geometry
changed. No protected world accessed. A5/A6 campaigns not restarted. OFF remains default.

## Next

A7-05 should add disposable-world fixtures for live expiry/cancellation and a deterministic
OFF/AUDIT twin run before any execution migration.
