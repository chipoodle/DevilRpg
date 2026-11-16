# Phase 13 — Generic Recovery and Actuator Forensics (2026-11-16)

## Git baseline

- Branch `feature/autonomous-village`.
- Initial local/remote HEAD: `2db10278eae7c79dd7239df460487e4e172e481b`.
- No divergence. Diagnostic-only instrumentation; no gameplay decisions changed.

## Diagnostic run

- Run ID: **`a2n-20261116-024118`**
- Disposable world: `build/a2n/a2n-20261116-024118/game/a2n`
- Logs: `build/a2n/a2n-20261116-024118/srv.log`, `nav-trace.log`
- Classification: **DIAGNOSTIC — NOT ACCEPTANCE**
- Result: 8/8 spawn, **6/8 perimeter, 4/8 strict**, C4 **FAIL** (jobSite=[0,0,0], though 12/12 workers resumed).
- Strict: idx1 513, idx2 668, idx4 4013, idx6 650. idx3 perimeter-only; idx5/7/8 outside.

## IDX5 — generic recovery

UUID `2d5f3fdb-3a61-4cd3-91a7-1ac100b3d75e`.

```text
tick=5999 pos=629,72,467 state=RECOVERING
objective=622,77,469 recoveryNode=621,76,468 override=null
routeIdx=1/8 recoveryActions=15/24
nextRecoveryAt=6164 now=5999
RECOVER_DECISION=WAIT remaining=165
```

- The recovery gate is **intentional cooldown**, not a rejected guard.
- It alternates `ENTER` → `WAIT` each tick; no route/cache invalidation at closure.
- The next attempt is scheduled for tick 6164, after the 6000 closure.
- **Result: no incorrect recovery decision proven.** The attacker is waiting out a deliberate exponential backoff and the diagnostic window ends first.
- Confidence: high for the cooldown; unknown whether the 6164 attempt would succeed.

## IDX7 — actuator

UUID `0f6d2c7d-dc70-4894-b49d-15d089f8fc77`.

```text
tick=2390 BREAK_APPROACH target=726,68,477 pos=725,66,475
pathNull=false pathCanReach=false pathDone=false pathNodes=1 pathIndex=0 distSqr=9.8737

tick=2394 BREAK_APPROACH target=726,68,477 pos=725,66,476
pathNull=false pathCanReach=false pathDone=true pathNodes=1 pathIndex=1 distSqr=9.0849
```

- The command path has **one node and cannot reach**.
- Path index advances 0→1; navigation reports done; the entity moves only one block.
- Distance to target remains ~3.01 blocks (>2.5 action radius).
- Later terminal:
  ```text
  tick=5359 A2_FALLAR_ENTER motivo={presupuesto agotado sin progreso (10/10) hacia 726,68,476}
  NAV_FAIL pos=725,66,476
  ```
- **First proven actuator failure:** PathNavigation completes a non-reaching one-node path while the entity remains outside action range; repeated approach fails to close the gap.

## IDX8 — actuator

UUID `a74922f9-59e5-4405-a3f9-814a0ceb18a6`.

```text
tick=4994 BREAK_APPROACH target=746,68,539 pos=743,67,538
pathCanReach=false pathNodes=1 pathIndex=1 distSqr=11.2145

tick=5083 BREAK_APPROACH pos=744,66,540 pathCanReach=false pathNodes=3 pathIndex=0 distSqr=12.3591
tick=5084 ... pathIndex=1 distSqr=12.0578

tick=5061 DROP_APPROACH target=744,66,539 pos=743,67,538
pathCanReach=true pathNodes=2 pathIndex=0 distSqr=1.6944
tick=5062 ... pathIndex=1 distSqr=1.7516
```

- Non-reaching BREAK paths advance their index while position changes only slightly/away.
- A DROP path is reachable and close, but the entity **does not displace** across commands.
- It enters `RECOVERING` with `nextRecoveryAt=6230`, `recoveryActions=1/24`, routeIdx=1.
- **First proven actuator failure:** path index/done progresses without corresponding physical displacement; target never enters action range.

## Hypotheses

| Hypothesis | Verdict |
|---|---|
| Incorrect BREAK delegation/action range | **Rejected**; blockers were out of range and policy refused correctly |
| IDX5 guard rejects recovery | **Rejected**; it is a deliberate WAIT/cooldown |
| PathNavigation produces a non-reaching path treated as progress | **Supported** for IDX7/IDX8 |
| Entity receives path but does not physically move | **Supported** for IDX8 DROP/BREAK |
| Combat ownership causes stall | **Rejected** in Phase 11/12 evidence |
| One shared cause | **Rejected**; IDX5 is cooldown, IDX7/IDX8 actuator path failure |

## Production changes

**NONE.** Only read-only `RECOVER_DECISION` and `ACTUATOR_COMMAND` traces were added.
No action range, budget, collision, BREAK/recovery policy, movement authority or acceptance changes.

## Candidate correction (not implemented)

Before changing production, add per-tick path/position correlation after command issuance to
distinguish:
- path completed while distance remains above range,
- path index advances with no movement,
- entity movement blocked by collision.

A candidate minimal fix would be in the NavExecutor path-completion/progress predicate, not in the
action range. It must not treat a non-reaching/zero-displacement path as progress.

## Tests

```text
gradlew test: PASS
lint_aldea.py --strict: OK
git diff --check: PASS
```

## A2/C4

- A2 focused: 6/8 perimeter, 4/8 strict; gate of five consecutive 8/8 strict not met; **OPEN**; full 12k not run.
- C4 diagnostic: **FAIL** (profesionalesReanudados=true, 12/12 resumed, but jobSite=[0,0,0] fails ssignmentsClean). Historical 5/5 preserved; save/reload OPEN.

## Worlds

Unchanged:
- A2 `15,966,366` / `BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7`
- C4 `16,044,629` / `11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF`

## Next action

Add bounded post-command movement sampling (every 20 ticks) for IDX7/IDX8: position delta, path index,
`isDone`, distance; prove whether the actuator produces zero displacement while the path advances.
No production change until that correlation is captured.
