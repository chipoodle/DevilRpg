# A7-03 — Gateway Safety, Cancellation, and Lifecycle Evidence

Status: gateway policy/denial enforcement and cancellation attribution implemented. Forestry
execution remains in the legacy Goal; OFF is default. No NBT change.

## Gateway policy

`NavigationGatewayPolicy` is the pure decision core used by movement commands:

- OFF: delegate, no lease.
- AUDIT: delegate, log unauthorized.
- ENFORCE: require exact agent/task lease, else deny.
- Cancellation: an owner may stop its own movement; a current owner for another task
  always wins over stale cleanup; an unowned task may clean up only movement attributed
  to its own last command; unattributed cleanup is allowed; unknown attribution is logged
  and denied in ENFORCE.

`VillagerAgent` now records `lastNavigationTaskId` on delegated movement and clears it on
its own stop. This is transient and not persisted.

## Task generation

`VillagerLumberjackGoal` keeps a transient `agentTaskId`/`agentTaskTarget`, regenerated
when a target becomes active or changes and cleared when target becomes null. Snapshot task
identity uses it. No NBT field was added. Reload starts a new transient generation, while
the legacy task phase/target remains the source of truth.

## Evidence

Unit tests:

- `NAVIGATION_LEASE_TEST`: OK.
- `NAVIGATION_GATEWAY_POLICY_TEST`: OK (A1–A8 decisions, cancellation attribution).
- `compileJava`: BUILD SUCCESSFUL.
- `lint_aldea.py --strict`: PASS.
- `git diff --check`: PASS.

Runtime:

- Run 194 AUDIT: authorized snapshot/adapter/lease, `RECOGER_HARINA` target 107,97,16.
- Run 195 ENFORCE: authorized movement.
- Run 196 ENFORCE: wrong task → `DENIED_WRONG_TASK`; null agent → `DENIED_NO_LEASE`;
  authorized task still moved.
- Run 193 agent smoke: 16 villagers / 16 agents.

Not tested: OFF-vs-AUDIT behavioral comparator, runtime cancellation of a completed task,
lease expiry runtime, full TALAR→PLANTAR→ABONAR→ENTREGAR lifecycle, save/reload of agent
state. Vanilla Brain remains an independent writer and is not intercepted.
