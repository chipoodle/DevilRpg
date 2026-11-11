# A7-05 — Live Runtime Fixtures for Ownership and Forestry Compatibility

Status: **PARTIAL**. Live lease expiry and live cancellation fixtures pass. Same-target
task generation, OFF/AUDIT comparison, and full lifecycle remain NOT RUN. Legacy Goal is
still the executor.

## Fixtures added

- `VillagerLumberjackGoal.suppressRenewalForTest(boolean)` — disabled by default; suppresses
  only lease renewal in a disposable test.
- `A5TestController` scenario `agent-live-expiry`.
- `A5TestController` scenario `agent-live-cancel`.

No forestry operation, controller, planner, storage, pantry, or NBT changed.

## Live lease expiry — PASS (Run 201)

Disposable A5WALL copy; worker `b89fcee1-c91a-4b1f-a9c4-66471b616288`.

```text
task=948d3e03-2f72-3a72-8185-d81656e09b70
TTL=200
suppressed at game time 4021
lease expired at 4221
command at 4223
result=DENIED_EXPIRED
pathBefore=0 pathAfter=0 active=false ownerAfter=empty
[A5] FIN PASS
```

The gateway rejected the former task and did not acquire a replacement lease. No path
replacement was observed. Controller was not explicitly reset by the fixture.

## Live cancellation — PASS (Run 202)

Disposable worker; actual gateway + `PathNavigation`.

```text
B2 move=AUTHORIZED path=0 active=false
B2 stale=DENIED_WRONG_TASK protects=true
B2 ownerStop=AUTHORIZED active=false
B1 stop=AUTHORIZED active=false
B3 cleanup=CLEANUP_ALLOWED active=false owner=empty
B4 invalidate stop=CLEANUP_ALLOWED active=false owner=empty
[A5] FIN PASS
```

B1 normal completion, B2 newer-owner protection, B3 expiry cleanup, B4 invalidation all
followed the configured policy. The worker's path was not active in this fixture, so these
runs prove logical attribution and gateway decisions, not active-path replacement.

## Logical vs actual path ownership

`lastNavigationTaskId` is logical command attribution. The fixture records
`PathNavigation.getPath()` identity and `isInProgress()`. Because the dummy worker often had
no active path, actual path ownership is **not proven** for B1/B2; only unchanged path
identity and gateway decisions are shown.

## Task identity — NOT RUN

No live same-target A/B fixture was executed. Transient `agentTaskId` generation exists,
but `taskA.id != taskB.id` for a reused target has not been demonstrated at runtime.

## OFF/AUDIT comparison — NOT RUN

No independent OFF and AUDIT disposable twins were generated. No resource/accounting
comparison is claimed.

## Forestry lifecycle — PARTIAL

Run 194–201 observed `RECOGER_HARINA` / `TRAVELLING` with target 107,97,16 and matching
lease. TALAR, PLANTAR, ABONAR/GROWING, and ENTREGAR operations were **NOT RUN** with
operation-level evidence.

## ENFORCE integration — PASS

Run 196: wrong task and no-lease movement denied; Run 201: expired lease denied; Run 202:
stale stop denied while newer owner retained ownership. No controller reset observed.

## Non-regression

- No accepted forestry operation rewritten.
- No NBT schema change.
- No protected world accessed; disposable copies only.
- No A5/A6 campaign restarted.
- Legacy Goal remains production executor.
- OFF remains default; renewal suppression is test-only and default false.

## Verification

```text
compileJava = BUILD SUCCESSFUL
NAVIGATION_LEASE_TEST = OK
NAVIGATION_GATEWAY_POLICY_TEST = OK
NAVIGATION_GATEWAY_HARNESS_TEST = OK
lint_aldea.py --strict = PASS
git diff --check = PASS
```

## Next

A7-06 should build deterministic OFF/AUDIT twins and a live same-target A/B identity
fixture before any execution migration.
