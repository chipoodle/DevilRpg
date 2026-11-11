# A8-F03-R4 — Deterministic Target Recovery, Lease Release & Wall-Gate

Status: **BLOCKED / PARTIAL**. R4-A/B/C were not completed. The R3 timeout is traced to
fixture orchestration, not a demonstrated planner defect. No production changes were made.

## Baseline

```text
branch feature/autonomous-village
HEAD 97220be5453b57387002b9bc91935807cc665274
staged 0
unstaged 26
untracked 164
```

## R3 timeout root cause

Run 219 fixture `r3-talar-remove`:

- cleared logs/leaves/saplings in ±90;
- placed A at center+(8,0,8);
- spawned a fletcher with a real goal;
- waited for `r3Goal.snapshot()` to report TALAR at A.

No `[R3-A] REMOVE` occurred. The only cache-clear evidence was:

```text
[R3] event=CACHE-CLEAR target=BlockPos{x=53, y=97, z=-17} reason=target-not-log
```

That target belongs to the existing village lumberjack b89, not the custom fixture worker.
The custom worker never produced the expected TALAR assignment within the bound. The
fixture’s target-selection orchestration is the blocker; no evidence shows the shared
planner rejected A.

## R4-A (target replacement)

NOT COMPLETED. No task A/task B pair for the fixture worker, no distinct IDs, no B step.

## R4-B (navigation/lease release)

NOT COMPLETED. Cache clearing does not itself stop PathNavigation; the existing Goal
cleanup/lease release path was not demonstrated under target removal.

## R4-C (gate geometry/crossing)

NOT COMPLETED. No outside-wall tree/gate fixture; `wallGate=true` and physical crossing
remain unobserved. No geometry changed.

## Ownership

- ENFORCE still routes TALAR target/step/navigation through agent.
- OFF uses shared planner with Goal cache.
- ENTREGAR legacy.
- No competing TALAR step observed.

## Code changes

None for R4. R3’s `TalarStepPlanner` cache-clear diagnostic and `r3-talar-remove` fixture
remain.

## Verification

```text
compileJava = BUILD SUCCESSFUL
NAVIGATION_LEASE_TEST = OK
NAVIGATION_GATEWAY_POLICY_TEST = OK
NAVIGATION_GATEWAY_HARNESS_TEST = OK
lint_aldea.py --strict = PASS
git diff --check = PASS
```

## Blockers

1. Fixture worker did not select the custom TALAR target; need to use a known worker or a
   verified target-discovery precondition.
2. Active navigation/lease release after removal is unproven.
3. No valid outside-wall gate fixture has been built/executed.

## Classification

**BLOCKED** for R4. No production behavior was changed to force a pass. The ownership
invariants remain intact; target recovery, lease release, and gate crossing are not
demonstrated.
