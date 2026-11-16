# Phase 19 — BREAK contract verification (2026-11-16)

## Sync

- Branch `feature/autonomous-village`.
- Local HEAD: `b901fcc456cfe77141fd0668c15bbaa129f9746a`.
- Remote HEAD: same.
- Requested `d668594af2c93f640e6f26f4d4e2a949f0338ccc` is an **ancestor** (Phase-19 handoff); there was
  nothing to fast-forward.
- `git merge --ff-only origin/feature/autonomous-village` → **Already up to date**.
- Working tree only has preserved local-only artifacts.

## Checks

```text
.\gradlew.bat test                     PASS
python tools/lint_aldea.py --strict    OK
git diff --check                       PASS
```

Test classpath now includes Minecraft types, so the Phase-19 tests execute:
- `NavBreakActionPolicyTest` — 8 assertions (clearance, support, protection, break permission, reach,
  LOS, vertical reach).
- `NavBreakExclusionPolicyTest` — 6 assertions (stand/target edge key, alternative side, terrain
  fingerprint, bounded memory).
- `NavRecoveryControllerTest` — 7 assertions (first failure replan, second backtrack, historical cell,
  one-backtrack bound, progress rearm, reset).

## Integration error found and fixed

The merged Phase-19 BREAK branch originally used `node.pos()` as the break target. The new route model
uses:

- `node.pos()` / `standPos()` = traversable execution cell;
- `node.actionPos()` = solid target.

That mismatch would have made the executor try to break the stand cell. Fixed in
`NavExecutor.ejecutarBreakDesdePosicionDeAtaque`:

- `target = node.actionTarget()`;
- navigate to `node.standPos()` until physically there;
- validate `NavBreakActionPolicy.executable(..., stand, target, ...)`;
- execute the existing RecoveryAction on `target`;
- verify `target` is air before route/cache invalidation and replan.

The solid target is never passed to `moveTo`; the only `moveTo` in this path is `standRuta`.

## Source verification of requested cases

| Requirement | Evidence |
|---|---|
| BREAK from different attack positions | Planner `NavBreakAttackPositionPolicy` enumerates adjacent/diagonal stands; exclusion is keyed by `(stand,target)`, so a failed side does not block another. |
| No `moveTo` on solid BREAK | `case BREAK -> ejecutarBreakDesdePosicionDeAtaque`; function navigates only `standRuta`; target is only used by `recoveryAction.run`. |
| Second attempt after cooldown | `nextBreakRetryAt = now + RECOVERY_COOLDOWN_TICKS` after first unverified invocation; second failure calls `excluirAristaBreakYReplanificar`. |
| Alternative routes | `NavBreakExclusionPolicy` + planner candidate search; failed edge excluded while terrain fingerprint unchanged. |
| Backtrack after two failures | `NavRecoveryController`: first no-progress → REPLAN, second → BACKTRACK; one-shot backtrack protected from mission refresh. |
| Resume original mission | backtrack completion/mission change clears `recoveryBacktrackObjective`; `objective = misionFinal` on resolution. |
| C4 criteria | Unchanged `assignmentsClean`, 12-worker UUID accounting, resumption and job-site checks. |

This is **source-level verification**, not a runtime trace of each case.

## Runtime

Run **`a2n-20261116-110238`** (seed 12345, FaseA 3000, FaseB 6000, budgets 900/8000):

- 8/8 spawn.
- 6/8 perimeter.
- **5/8 strict `DENTRO-PARED`:** idx1 512, idx2 667, idx3 1066, idx5 699, idx6 649.
- idx4 perimeter-only; idx7/idx8 outside.
- C4 **PASS** `[3,0,0]`, 12/12 resumed.
- No `BREAK_EXECUTED_FROM_STAND`, `BREAK_STAND_APPROACH`, `BREAK_EDGE_EXCLUDED`, or `NAV_BACKTRACK`
  event was present in `nav-trace.log`. **Direct runtime BREAK execution remains unproven.**

## A2/C4

- A2 focused: **OPEN**; canonical five consecutive 8/8 strict gate not met; 12k not run.
- C4: PASS in this focused run; contract unchanged. Save/reload OPEN.
- IDX7 remains BLOCKED; IDX5 boundary UNKNOWN; IDX8 not declared solved.

## Worlds

Unchanged:
- A2 `BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7`
- C4 `11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF`

## Remaining work

1. Produce a runtime trace proving `standPos` navigation, `actionPos` air, route advance and replan.
2. Exercise multiple approach sides and a deliberate first-actuator failure to observe the 40-tick retry.
3. Exercise two no-progress failures to observe backtrack and strategic-mission resumption.
4. Add planner integration tests with a real/mock `CollisionGetter` and `Mob`.


## Follow-up source review — executable attack-stand edge

A review found that the selected attack stand was not constrained to a single legal move from the
predecessor. The old helper could select a farther or vertically different neighbouring cell after
validating the predecessor, while the graph charged only one BREAK edge. Its separate LOS helper
also treated a raycast MISS as visible.

Corrected in the follow-up source commit:

- Candidate attack stands must be at the same feet Y and within one horizontal graph step of the
  predecessor.
- Diagonal stand approaches obey the existing corner-clearance rule.
- The selected stand itself is checked by NavBreakActionPolicy.executable, enforcing production
  reach, protection, break permission, AABB/support and target-first raycast.
- BREAK edge exclusion is keyed to the actual selected (stand,target) pair.
- Added NavBreakAttackPositionPolicyTest for cardinal/diagonal one-step eligibility, same-cell,
  two-cell, vertical mismatch, feet-level mismatch and candidate-set size.

These are source and deterministic policy changes only. Harness must run Gradle tests, strict lint,
diff-check and then targeted runtime. No runtime BREAK or A2 acceptance PASS is claimed.


Follow-up implementation commit: f43eb11eab618ed1e19f8b795c1663bc0c619331.
The follow-up policy test and planner correction are pending Harness execution; the earlier PASS
results in this report apply to the preceding commit only.
