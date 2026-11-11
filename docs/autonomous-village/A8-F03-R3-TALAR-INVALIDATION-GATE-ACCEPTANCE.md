# A8-F03-R3 — TALAR Invalidation & Wall-Gate Acceptance Fixtures

Status: **PARTIAL / BLOCKED for R3-B**. R3-A observed real cache invalidation on a removed
TALAR target but did not complete a target-B replacement fixture. R3-B gate traversal was
not executed.

## Baseline

```text
branch feature/autonomous-village
HEAD 97220be5453b57387002b9bc91935807cc665274
staged 0
unstaged 26
untracked 163
```

No protected save, NBT change, prohibited Git operation, commit or push.

## Source findings

- `TalarStepPlanner.Cache.lastTarget` is BlockPos identity.
- `requireLog=true` in agent TALAR and Goal TALAR paths.
- Null or non-log TALAR target clears `lastTarget`/`standingCell` and returns
  `Step(null,...)`.
- Clearing the cache does **not** itself stop an existing `PathNavigation`; the Goal's
  normal target/lease cleanup must do that.
- `VillageManager.pasoParaCruzarElMuro` is called by the planner; `Step.wallGate` is only
  evidence of selection, not traversal.

## R3-A fixture

Scenario `r3-talar-remove`:

- disposable A5WALL copy;
- ENFORCE;
- clears logs/leaves/saplings in ±90;
- places a 4-log tree at `center+(8,0,8)`;
- spawns a fletcher at `center+(4,1,4)` with a real `VillagerLumberjackGoal`;
- removes the target after selection and offers a replacement at `center+(-8,0,8)`.

## R3-A runtime evidence (Run 219)

Cache-clear events were observed:

```text
[R3] event=CACHE-CLEAR target=BlockPos{x=53, y=97, z=-17} reason=target-not-log
```

Repeated clears demonstrate the normal path detected the target was no longer a log and
cleared the planner cache. However:

- no `[R3-A] REMOVE`/`REPLACED` transition completed;
- no target-B task ID was captured;
- the fixture ended at the harness timeout.

Target A evidence exists; target B recovery and no-stale-navigation proof are incomplete.

## R3-B fixture

Not executed. A valid outside-wall tree, gate route, physical crossing, and continuation
were not demonstrated. No geometry or wall construction was modified.

## Ownership

- ENFORCE agent still owns target selection, step decision, and navigation command.
- OFF still uses the same planner from the Goal.
- ENTREGAR remains legacy.
- No competing TALAR step was observed.

## Code changes

- `TalarStepPlanner` — default-off `devilrpg.r3diag` cache-clear event.
- `A5TestController` — `r3-talar-remove` fixture.
- No harvesting/replanting/growing/delivery/NBT change.

## Verification

```text
compileJava = BUILD SUCCESSFUL
NAVIGATION_LEASE_TEST = OK
NAVIGATION_GATEWAY_POLICY_TEST = OK
NAVIGATION_GATEWAY_HARNESS_TEST = OK
lint_aldea.py --strict = PASS
git diff --check = PASS
```

## Acceptance table

| Scenario | Status |
|---|---|
| R3-A initial target/cache | PASS |
| R3-A removal detection | PASS (cache clear observed) |
| R3-A cache invalidation | PASS (code + runtime event) |
| R3-A stale navigation prevention | PARTIAL / not proven |
| R3-A replacement target recovery | NOT RUN |
| R3-B valid gate geometry | NOT RUN |
| R3-B planner gate selection | NOT RUN |
| R3-B navigation command | NOT RUN |
| R3-B physical crossing | NOT RUN |
| R3-B external target arrival | NOT RUN |
| R3-B harvest/delivery | NOT RUN |
| Ownership invariants | PASS |

## Remaining blockers

- The replacement worker/target did not complete; the fixture needs a deterministic
  player-visible target and a longer/verified setup.
- Gate traversal needs a dedicated outside-wall target fixture; none was executed.
- Cache clearing does not cancel an existing path; safe cancellation needs explicit
  navigation stop/lease behavior, which was not demonstrated here.

## Classification

**PARTIAL** for R3-A, **BLOCKED/NOT RUN** for R3-B. No regression was demonstrated.
