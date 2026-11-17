# A2 Run 1 — AABB source recovery Phase 21 result (2026-11-17)

## Result

**FAIL.** Source-cell detection and horizontal snap work, but the recovery route to the exact take-off
is empty at `900/900`; the original DROP does not complete a verified landing/cursor advance.
A2 Run 1 remains 5/8 strict.

- Tested SHA: `77b635d49a6eaccc8747fce2f62ad7228aeb940a`
- Production checkpoint: `b02affb19eb8e37824682090ba99f793a04aea94`
- Run: `a2n-20261117-111418`
- Seed 12345, FaseA 3000, FaseB 6000, budgets 900/8000.

## Static validation

| Command | Exit | Result | Log |
|---|---:|---|---|
| `gradlew test` | 0 | PASS | `build/phase21-source-gradle.log` |
| `lint_aldea.py --strict` | 0 | PASS | `build/phase21-source-lint.log` |
| `git diff --check` | 0 | PASS | terminal |
| `compileJava -Pa2nDiagnostics=true` | 0 | PASS | `build/phase21-source-diagcompile.log` |

```text
NAV_SOURCE_CELL_POLICY_TEST: OK (10 assertions)
NAV_DROP_TAKEOFF_POLICY_TEST: OK (40 assertions)
NAV_ROUTE_NODE_POLICY_TEST: OK (6 assertions)
NAV_RECOVERY_CONTROLLER_TEST: OK (10 assertions)
NAV_RECOVERY_GUARD_POLICY_TEST: OK (5 aserciones)
```

The previous missing `BlockPos` import is fixed.

## A2 Run 1

- 8/8 spawned.
- **5/8 perimeter.**
- **5/8 strict `DENTRO-PARED`.**
- C4 **PASS** `[2,0,0]`, 12/12 resumed.
- Run 2 not executed.

| idx | UUID | Perimeter | Strict |
|---|---|---|---:|
| 1 | `30dff545-5559-467b-8159-b1a174af328d` | t387 | **t512** |
| 2 | `a28b33ed-f847-49a8-aee3-ab9bc71d6634` | t242 | **t667** |
| 3 | `3b8c027f-20b7-4178-aaf8-13107df47302` | t1674 | **t1775** |
| 4 | `131e44bf-bc37-433a-b661-cfd9bdfea509` | — | — |
| 5 | `1c14134b-8585-4baa-8598-a81989489257` | t816 | **t1043** |
| 6 | `52103154-13be-47ab-b9bb-bd7a71f8cb3d` | t224 | **t649** |
| 7 | `21c8276a-d2c1-4194-a273-716d6b106748` | — | — |
| 8 | `92d87fb5-422e-464a-a3c8-eedbca5b7979` | — | — |

## IDX4 — source recovery chain

UUID `131e44bf-bc37-433a-b661-cfd9bdfea509`.

### A. Invalid source detected

```text
tick=3309 DROP_TAKEOFF_SOURCE_CELL_CANDIDATE
 from=596,77,498
 candidate=596,77,499
 candidateFeetValid=true candidateSupport=true candidateRealAabbFits=true
```

### B. Horizontal snap

```text
tick=3309..3330 DROP_TAKEOFF_SOURCE_SNAP_STARTED
 from=596,77,498 candidate=596,77,499
 target=596.50,77.00,499.50 dy=0 speed=0.35

tick=3335 DROP_TAKEOFF_SOURCE_SNAP_REACHED
 source=596,77,499 exact=596.504,77.000,499.310
 grounded=true feetValid=true support=true realAabbFits=true
```

Physical cell occupancy: **YES** (`blockPosition=596,77,499`).
Exact center: **NO** — `z=499.310` vs target `499.500` (0.19 short), though the event was accepted.

### C. Recovery A* admission

The source predicates are all true after the snap:

```text
onGround=true
feetValid=true
support=true
realAabbFits=true
```

But no accepted route exists for this UUID:

```text
DROP_TAKEOFF_RECOVERY_ROUTE_ACCEPTED: 0 events
DROP_TAKEOFF_RECOVERY_REACHED:        0 events
```

### D. Recovery route failure

```text
DROP_TAKEOFF_RECOVERY_PLAN_REJECTED
 from=596,77,499 takeoff=596,79,498
 hitBudget=true route=vacia
 summary={inicio=596,77,499 fin=596,79,498 H=12 V=6
 exp=900/900 presupuesto=true
 evaluados=90090 encolados=1366
 metaEncolada=false alcanzada=false ruta=0}
```

Another rejection:

```text
from=585,105,516 takeoff=585,106,516
hitBudget=true route=vacia exp=900/900
```

So the snap did its job; the next blocker is the recovery A* being unable to produce a route from
the valid snapped source to the take-off, exhausting exactly 900 expansions with an empty result.

### E. Original route / DROP

The original DROP was commanded from a constructed take-off:

```text
tick=3474..3475 DROP_DECISION
 nodo=596,77,499 takeoff=596,79,499 dy=-2
 constructed=true idxRuta=0/2
DROP_START idxRuta=0 aterrizaje=596,77,499
 salida=596,79,499 dy=-2 construido=true
```

Final position is the landing cell:

```text
RAID RESULTADO idx=4 finalPos=596,77,499
```

But no `DROP_DONE` exists for `596,77,499`; the last observed DROP_DONE is `588,104,521`.
Therefore:

```text
ORIGINAL_DROP_EXECUTED: YES (DROP_START/COMMAND)
ORIGINAL_DROP_PHYSICAL_LANDING: POSITION OBSERVED, but no DROP_DONE/verified arrival
ORIGINAL_CURSOR_RESTORED: NO EVIDENCE
ORIGINAL_PLANNED_FROM_RESTORED: NO EVIDENCE
```

## IDX4 required fields

```text
IDX4_UUID: 131e44bf-bc37-433a-b661-cfd9bdfea509
SOURCE_INVALID: YES
SOURCE_CANDIDATE: 596,77,499
SOURCE_SNAP_STARTED: YES (t3309..3330)
SOURCE_SNAP_REACHED: YES (t3335)
SOURCE_PHYSICAL_OCCUPANCY: YES (cell); exact center NO (499.310 vs 499.500)
SOURCE_FEET_VALID: YES
SOURCE_SUPPORT: YES
SOURCE_REAL_AABB_FIT: YES

RECOVERY_ROUTE_ACCEPTED: NO
RECOVERY_ROUTE_TRANSITIONS: NONE
AUXILIARY_DROP_COUNT: 0
AUXILIARY_DROP_PHYSICAL_LANDING: N/A
EXACT_TAKEOFF_REACHED: NO
ORIGINAL_ROUTE_RESTORED: UNKNOWN/NO EVIDENCE
ORIGINAL_CURSOR_RESTORED: NO EVIDENCE
ORIGINAL_PLANNED_FROM_RESTORED: NO EVIDENCE
ORIGINAL_DROP_EXECUTED: YES (command/start)
ORIGINAL_DROP_PHYSICAL_LANDING: POSITION OBSERVED; NO DROP_DONE
```

## IDX7 / IDX8

```text
IDX7 21c8276a-d2c1-4194-a273-716d6b106748:
 pos=705,65,459; BUILD 703,68,457; local 1/64; global 2526/8000
 NAV_FAIL {presupuesto agotado sin progreso (10/10)}

IDX8 92d87fb5-422e-464a-a3c8-eedbca5b7979:
 pos=769,63,497; no valid local waypoint; global 8000/8000
 rearmes=4/4; rutaFrente=81
```

## C4

- PASS `[2,0,0]`.
- 12/12 resumed; no casualties/unresolved workers.
- Save/reload OPEN.

## Gate

- Static gate: PASS.
- A2 Run 1: FAIL 5/8 strict.
- Run 2: not executed.
- Full 12k: not executed.
- A2 remains OPEN.

## Artifacts

- `build/a2n/a2n-20261117-111418/srv.log`
- `build/a2n/a2n-20261117-111418/nav-trace.log`
- `build/a2n/a2n-20261117-111418/fin.txt`
- `build/a2n/a2n-20261117-111418/estado.txt`
- `build/phase21-source-gradle.log`
- `build/phase21-source-lint.log`
- `build/phase21-source-diagcompile.log`

Original worlds untouched; local-only artifacts preserved.

## Next action

ChatGPT must make the recovery A* find a route from the valid snapped source
`(596,77,499)` to the take-off / original DROP entry. The current search returns empty at `900/900`.
Also tighten the snap completion to exact center (`z=499.5`) rather than accepting `499.31`, if the
contract requires center occupancy. IDX7/IDX8 remain independent; C4 PASS in this run.
