# Phase 12 — NavExecutor Refusal and DROP Recovery Forensics (2026-11-16)

## Git baseline

- Branch `feature/autonomous-village`.
- Initial local/remote HEAD: `7f7f476a21caa0241214fa8f9bbf5d5be055bfca`.
- No divergence. No production movement change; diagnostic decision traces added.

## Diagnostic run

- Run ID: **`a2n-20261116-022656`**
- Disposable world: `build/a2n/a2n-20261116-022656/game/a2n`
- Raw logs: `build/a2n/a2n-20261116-022656/srv.log`, `nav-trace.log`
- Classification: **DIAGNOSTIC — NOT ACCEPTANCE**
- Result: 8/8 spawn, 5/8 perimeter, 4/8 strict, C4 12/12.
- Strict entrants: idx1 512, idx2 4114, idx3 3862, idx6 659.

## BREAK decisions

`ALCANCE_DE_ACCION_SQR = 6.25` (action radius = 2.5 blocks).

### IDX7 (`42b2ce16-c37f-42fc-861d-15c7727f64d1`)

```text
tick=764 target=719,67,470 transition=BREAK
distSqr=10.0055 actionRangeSqr=6.25
pathNull=false pathCanReach=false pathDone=false
blocker=719,67,470 stone blockerDistSqr=10.0055 blockerInReach=false
policyDelegate=false recoveryAction=true idxRuta=1/3 state=TRAVERSING
```

- First refusal: **`BLOCKER_OUT_OF_REACH`** — the blocker coincides with the target and is ~3.16 blocks away, outside the 2.5 action radius.
- It never gets closer; later:
  ```text
  A2_FALLAR_ENTER ... motivo={presupuesto agotado sin progreso (10/10) hacia 693,71,514}
  NAV_FAIL ... pos=721,65,470
  ```
- **Classification:** correct policy refusal while stranded; terminal failure is secondary to failing to approach the BREAK node. No incorrect action-range decision proven.

### IDX8 (`d37b4756-2cef-495a-93d2-36818ccf8aea`)

```text
tick=3292 target=746,65,538 transition=BREAK
distSqr=9.1985 actionRangeSqr=6.25
pathNull=false pathCanReach=false pathDone=false
blocker=746,65,538 stone blockerDistSqr=9.1985 blockerInReach=false
policyDelegate=false recoveryAction=true idxRuta=3/6 state=TRAVERSING
```

- First refusal: **`BLOCKER_OUT_OF_REACH`**, ~3.03 blocks away.
- A later attempt at target `745,66,538` had `pathCanReach=false`, blocker distance 10.13², also out of reach.
- **Classification:** correct policy refusal; the unresolved defect is failure to approach the node, not an incorrect delegation decision.

## IDX5 DROP/recovery

Final state:

```text
pos=629,72,467 state=RECOVERING
nodoRecuperacion=621,76,468
lastAction=621,73,468:BREAK-ACERCARSE (dist=2.77)
intentosDescenso=0 fallosDescenso=0
```

No `DROP_DECISION` or `BREAK_DECISION` event exists for idx5's UUID
(`e725f69b-6832-4fad-ac9c-b8578f560778`). It is **not** in the BREAK/DROP branch at closure.
The current trace proves a generic `RECOVERING` stall, but not which `recover()` gate refuses or
delays the action. **Insufficient evidence for an incorrect production decision.**

## Hypotheses

| Hypothesis | Result |
|---|---|
| Action-range policy wrongly rejects an in-range blocker | **Rejected** by exact distances (3.03/3.16 > 2.5) |
| Combat suppresses movement | **Rejected** (`combateActivo=false` in Phase 11; no combat in these branches) |
| BREAK target is already resolved | **Rejected** (stone remains; no resolution event) |
| IDX7/IDX8 approach is stalled before action range | **Supported** |
| One shared cause for IDX5/7/8 | **Rejected** |
| IDX5 generic recovery gate is the cause | **Unresolved; requires `recover()` instrumentation** |

## Production changes

**None.** Only read-only `BREAK_DECISION` / `DROP_DECISION` traces were added. No movement,
budget, collision, BREAK policy or acceptance change.

## Existing worlds

Unchanged and verified:
- A2 `15,966,366` / `BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7`
- C4 `16,044,629` / `11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF`

## Tests

```text
gradlew test: PASS
lint_aldea.py --strict: OK
git diff --check: PASS
```

## Acceptance

The canonical focused gate remains **five consecutive 8/8 strict `DENTRO-PARED` runs**.
This diagnostic is not acceptance. A2 remains OPEN; full 12k not run. C4 diagnostic run 12/12.

## Next action

1. Instrument `recover()` entry/refusal for idx5: `nextRecoveryAt`, `recoveryActions`,
   `rechazoPorGuardaDeRuta`, `attemptOneRecovery`, route/cache invalidation.
2. Instrument the pre-delegation approach for idx7/idx8: why `moveTo(node)` does not reduce
   `distNodoSqr` below 6.25 over hundreds of ticks (path null/canReach, actuator movement).
3. Do not change action range or budgets until an incorrect in-range refusal is demonstrated.
