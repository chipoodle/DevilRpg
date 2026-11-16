# Phase 11 — Strategic Navigation Stalls (2026-11-16)

## Git baseline

- Branch `feature/autonomous-village`.
- Initial local/remote HEAD: `af6671047563b8b5c0f0b38e13b464d9e75c910e`.
- No divergence. No production gameplay change in Phase 11; only the read-only target probe was extended
  to idx5/7/8.

## Acceptance-gate discrepancy

Historical/current acceptance documents disagree:

- `01-navigation-architecture.md` states the A2 goal as **8/8 attackers entering in two controlled runs**.
- Phase 4 report mentions a historical **6/8** focused threshold.
- The latest explicit Phase-10 task defines the focused gate as **five consecutive runs with 8/8 strict
  `DENTRO-PARED` entry**.

Resolution: the **latest explicit task gate is canonical for Phase 10/11**: five consecutive focused
runs at 8/8 strict entry; a perimeter arrival is not entry. Historical 6/8 remains recorded, not erased.
A2 is therefore OPEN and the full 12k is not justified.

## Existing run inspected

`a2n-20261116-002550`: 8/8 spawn, 6/8 perimeter, 5/8 strict, C4 12/12.

New diagnostic run **`a2n-20261116-005739`** (distinct disposable world):
- seed 12345, FaseA 3000, FaseB 6000, budgets 900/8000.
- Expanded probe for idx5/7/8.
- Result: 8/8 spawn, 6/8 perimeter, 5/8 strict, C4 12/12.
- **DIAGNOSTIC — NOT ACCEPTANCE.**

Strict entries: idx1 513, idx2 669, idx3 4993, idx4 4315, idx6 650. idx5 perimeter only; idx7/idx8 never
reached perimeter.

## IDX5

Final alive telemetry:

```text
t=6000 pos=632,71,476 dist=63.57
state=RECOVERING objetivo=646,71,513
target=villager dist=76.06 combateActivo=false navSuspendido=false executorMayMove=true
lastAction=632,71,477:DROP-EN-CURSO
```

- Stationary for the final 1000+ ticks.
- No combat ownership, no tactical objective, NavExecutor owns movement.
- Strategic/local objective changed to `646,71,513`; last action is a DROP.
- **Classification: NavExecutor RECOVERING/DROP stall**, not combat timeout.

## IDX7

```text
t=6000 pos=725,66,476 dist=86.08
state=FAILED objetivo=726,68,476
target=iron_golem dist=86.94 combateActivo=false navSuspendido=false executorMayMove=true
lastAction=726,68,477:BREAK-ACERCARSE (dist=2.99)
```

- Terminal `FAILED` before reaching the wall.
- Combat is inactive; executor owns movement.
- It is stuck approaching a BREAK node 2.99 blocks away; no break is executed.
- **Classification: terminal navigation failure at BREAK approach**, not a combat death.

## IDX8

```text
t=6000 pos=746,64,538 dist=85.98
state=PLANNING objetivo=747,68,539
target=villager dist=44.21 combateActivo=false navSuspendido=false executorMayMove=true
lastAction=746,66,539:BREAK-ACERCARSE (dist=2.70)
```

- Stationary in PLANNING for the final interval.
- Combat inactive; executor owns movement.
- Repeated BREAK approach at 2.70 blocks with no action/resolution.
- **Classification: PLANNING/BREAK-approach stall**, distinct from IDX7's terminal FAILED.

## Hypotheses

| Hypothesis | Evidence | Verdict |
|---|---|---|
| Combat target suppresses waypoint | `combateActivo=false`, executorMayMove=true for idx5/7/8 | **Rejected for these stalls** |
| Strategic mission never submitted | objectives are non-null and change | Not supported |
| Planned route not executed | states are RECOVERING/PLANNING/FAILED; last actions DROP/BREAK-ACERCARSE | **Supported** |
| BREAK transition cannot be reached/acted | idx7 2.99 and idx8 2.70 blocks from BREAK node, no resolution | **Leading for idx7/idx8** |
| One common cause | idx5 DROP vs idx7 terminal vs idx8 planning differ | **Rejected** |

## Root cause status

**No single production defect proven.** Three distinct `NavExecutor` terminal/stall modes are observed,
with combat excluded. The evidence is insufficient to change action range or BREAK policy safely.

## Candidate minimal correction (not implemented)

Before changing behavior, add bounded telemetry inside `NavExecutor` for:
- distance to the BREAK node vs `ALCANCE_DE_ACCION`,
- `pathCanReach`, blocker selection and blocker distance,
- route index and last plan result for idx5/7/8.

Only if the blocker is inside the action range but delegation is refused would a code fix be justified.

## Worlds

Original diagnostic worlds unchanged; no new world was created outside the disposable run.
Hashes verified:
- A2 `15,966,366` / `BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7`
- C4 `16,044,629` / `11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF`

## Tests

```text
gradlew test: PASS
NavCombatIntentPolicyTest: OK (6 assertions)
lint_aldea.py --strict: OK
git diff --check: PASS
```

## Next action

Instrument `NavExecutor` BREAK approach for idx7/idx8 (distance/action range/pathCanReach/blocker) and
idx5's DROP recovery. No gameplay change until the refusal point is proven.
