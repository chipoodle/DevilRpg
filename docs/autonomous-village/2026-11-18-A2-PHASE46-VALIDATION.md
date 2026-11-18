# A2 Phase 46 validation — vertical-only DROP + IDX3 stair forensics (2026-11-18)

## 1. Result

**STATIC PASS. A2 Run1 4/8 strict; vertical-only case NOT REPRODUCED.**

- `NavDropEdgeStepPolicy` accepts horizontal distance0 and its 16-assertion test PASSes.
- A2 run `a2n-20261118-163717`: 4/8 strict (idx1, idx2, idx5, idx6), 4/8 perimeter, C4 PASS `[1,0,0]`.
- The exact IDX4 geometry (`takeoff 597,79,499 -> landing 597,78,499`, horizontal0) did not occur;
  therefore runtime validation of the vertical-only path is **INSUFFICIENT_EVIDENCE**.
- IDX3 stair trace was activated and captured. It shows the local goal cycle, `ACCESS_CLEARED`/
  `NO_PROGRESS`, 20 `STAIR_PLACED` outcomes and pending stairs, while the fallback still logs
  `result=null`.

- Branch: `feature/autonomous-village`
- HEAD: `e1530ab30591a26237d9ae64c1e9cfefbcbb27f2`
- Production: `e1530ab30591a26237d9ae64c1e9cfefbcbb27f2`
- Run: `a2n-20261118-163717`

## 2. Static

```text
gradlew test: PASS (0)
NAV_DROP_EDGE_STEP_POLICY_TEST: OK (16 assertions)
NAV_DROP_APPROACH_OWNERSHIP_POLICY_TEST: OK (13)
NAV_DROP_TAKEOFF_POLICY_TEST: OK (51)
NAV_ADJACENT_APPROACH_POLICY_TEST: OK (15)
lint_aldea.py --strict: PASS (0)
git diff --check: PASS (0)
compileJava -Pa2nDiagnostics=true: PASS (0)
```

## 3. A2 Run1

```text
A2: 4/8 strict, 4/8 perimeter
STRICT: idx1 t513, idx2 t669, idx5 t730, idx6 t650
PERIMETER: idx1 t387, idx2 t289, idx5 t501, idx6 t279
C4: PASS [1,0,0]
RUN2/FULL12K: not executed
```

IDX2 entered strict for the first time in the analyzed series.

## 4. Vertical-only DROP validation

Required case:

```text
takeoff 597,79,499 -> landing 597,78,499, horizontal=0
```

In `a2n-20261118-163717`, no `DROP_EDGE_STEP_SELECTED` with equal takeoff/landing X/Z occurred.
IDX4 UUID is `362c110e-bd71-4aac-81c2-418b6ca5d65f`; final `591,90,512`, `dist=72.58`,
`RECOVERING`, last action `597,78,499:DROP-EN-CURSO`, global budget `8000/8000`.

No sequence can be reported for:

```text
DROP_EDGE_STEP_SELECTED -> OWNER -> COMMAND -> SUPPORT_CLEARED -> DESCENT_STARTED -> LANDING
```

for a horizontal-zero takeoff in this run. The unit test covers the predicate, but the runtime
geometry did not reproduce.

```text
VERTICAL_ONLY_RUNTIME: INSUFFICIENT_EVIDENCE
REASON: case not present in run a2n-20261118-163717
```

## 5. IDX3 stair reconstruction

UUID `9ed655f7-45bc-429f-9e08-9f1ae35bd43d`; final `608,94,601`, distance86.06, FAILED.

Counts:

```text
STAIR_PIPELINE_ENTER=972
STAIR_BUILDER_ENTER=1426
STAIR_BUILDER_RETURN=505
STAIR_PIPELINE_PENDING=20
STAIR_PIPELINE_RETURN=484
NO_PROGRESS (builder outcomes)=461
ACCESS_CLEARED (builder outcomes)=24
STAIR_PLACED (builder outcomes)=20
A2_LOCAL_STAIR_ENTER=571
A2_STAIR_FALLBACK_RESULT result=null (multiple)
```

### First pipeline

```text
pos=594,96,600 mission=661,71,533 causa={objetivo no utilizable}
STAIR_PIPELINE_RETURN outcome=ACCESS_CLEARED
STAIR_BUILDER_RETURN outcome=ACCESS_CLEARED pending=null colocados=0
motivo={obstruccion de acceso removida; replanificar desde el mundo actualizado}
```

The first `ACCESS_CLEARED` does **not** place a stair; it requests a replan.

### Pending stairs

```text
STAIR_PIPELINE_PENDING transitable=607,94,601
 stair={colocados=1/24 ultimo=607,93,601 ancla=607,94,601
        motivo=escalon verificado tras 1 candidatos (dy=1)}
```

Pending exists after placement. Builder outcomes include 20 `STAIR_PLACED`, but no separate
`STAIR_PLACED` event and no `STAIR_TRAVERSED` event exists in these logs. The fallback still reports
`result=null`; the exact conversion from pending stair to a usable fallback route is not logged.

### Local cycle / reset

```text
t3   local 1
t230 local41; t288 local42; t321 local43
t359 local61; t405 local62; t450 local63
t454 A2_FALLAR_ENTER local64/64 fallos10/10
t454 NAV_TERRAIN_REARM
...
t487 local16; t535 local17; t580 local18 ...
t856 FAIL local64/64 again
```

The same objectives recur:

```text
607,94,601 -> 613,88,601 -> 608,95,601
```

`NAV_TERRAIN_REARM` resets the local counters; the subsequent cycle is geometrically the same and
reaches the same limits. Vertical progress (Y96→95/94) and small horizontal progress (X594→607/608) do
not produce mission progress; the target remains far and `ACCESS_CLEARED` repeatedly replans.

### Answers

1. **Fallback null:** pending stairs exist and 20 `STAIR_PLACED` outcomes occur, but no fallback route
   is produced; the exact promotion code is not traced (`INSUFFICIENT_EVIDENCE` for why).
2. **Builder outcome:** mixed `ACCESS_CLEARED` (24), `NO_PROGRESS` (461), `STAIR_PLACED` (20).
3. **Pending after placement:** yes, `STAIR_PIPELINE_PENDING` with `colocados` incrementing.
4. **Counter reset:** `NAV_TERRAIN_REARM` at t454 and subsequent rearms; local returns to 0/16.
5. **Same objectives:** the local goal generator returns the same three cells after each rearm.
6. **Progress:** vertical/limited horizontal movement exists, but no measurable mission progress.
7. **Rearm:** restarts the same state/cycle, not a new usable route.

## 6. Phase45 errata

1. **IDX4 mission vs anchor:** the strict-distance anchor is `661,71,533` (`RAID SPAWN ancla`), while
   the production mission/objective is `661,72,533`. Phase45's “mission 661,71,533” in the distance
   section conflated anchor and mission. `dist=72.12` is horizontal distance to the anchor `661,533`;
   the 3D distance is 72.56.
2. **`escalones físicos 24/24`:** this is the aggregated `escalera.colocados` counter, not proof of 24
   verified placements or 24 traversals. In the Phase46 trace, builder outcomes report 20
   `STAIR_PLACED`; no `STAIR_TRAVERSED` event exists. These are distinct concepts:
   `escalonesDeObjetivo` (64), `fallosDeEscalonDeObjetivo` (10), `escalera.colocados` (20/24), verified
   placements (20 outcomes, no separate event), traversals (NOT_LOGGED).

## 7. Integrity

```text
PRODUCTION_MODIFIED: false
TESTS_MODIFIED: false
WORLDS_MODIFIED: false
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
RUN2_EXECUTED: false
FULL12K_EXECUTED: false
```

## 8. Recommendation

- Re-run a vertical-only fixture/scenario that deterministically produces horizontal0 to validate the
  runtime path; do not claim it from unit tests alone.
- Repair/diagnose why `STAIR_PLACED`/pending does not become a fallback route (`result=null`) before
  touching the 64/10 limits.
- Keep Phase44/41/29/31/34/36 frozen. A2 remains OPEN.
