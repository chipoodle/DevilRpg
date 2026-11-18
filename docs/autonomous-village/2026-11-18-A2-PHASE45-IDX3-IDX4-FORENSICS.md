# A2 Phase 45 — IDX3 BUILD/step budget + IDX4 near-miss (2026-11-18)

## 1. Result

**COMPLETE.** IDX3 and IDX4 are independent failures.

- **IDX3**: the Phase44 approach chain worked, but the local stair/build cycle repeatedly selected the
  same three objectives (`607,94,601`, `613,88,601`, `608,95,601`), reset after each rearm, and
  exhausted 64 local steps / 10 consecutive failures without horizontal mission progress.
- **IDX4**: it reached `597,79,499` directly above landing `597,78,499`. Landing fit/support are valid
  and horizontal tolerance is satisfied, but `landingHeightReached=false`. Phase41 cannot act because
  its edge-step requires `horizontalDistance > 0`; a vertical-only DROP has distance 0. The mob stays
  grounded directly above the landing.

- Branch: `feature/autonomous-village`
- HEAD: `3012b334e8398d811e795c3f3658eb36b233af85`
- Production: `ed31b815193aa4fcde0b088f1a6d110270ba4a9d`
- Run: `a2n-20261118-145308`
- No production/test/runtime change.

## 2. Evidence paths

```text
build/a2n/a2n-20261118-145308/nav-trace.log
build/a2n/a2n-20261118-145308/srv.log
build/a2n/a2n-20261118-145308/fin.txt
```

UUIDs:

```text
IDX3 ef6bf62b-0ddc-4a87-8218-c4f0cdbb6a32
IDX4 072cdd9b-61d4-48b8-a6ba-bb146cd486b6
```

## 3. IDX3 — local BUILD/step reconstruction

### Counters (kept separate)

| Counter | Final/observed | Source |
|---|---|---|
| `escalonesDeObjetivo` (local accepted) | 64/64 | `A2_LOCAL_STAIR_ENTER`, `A2_FALLAR_ENTER` |
| `fallosDeEscalonDeObjetivo` | 10/10 | `A2_FALLAR_ENTER` |
| physical stairs `colocados` | 24/24 | fallback/final `escalera` |
| physical traversals | **NOT_LOGGED** | no `STAIR_TRAVERSED` event in this run |

Counts for IDX3: 571 `A2_LOCAL_STAIR_ENTER`, 571 `A2_LOCAL_STAIR_WRITE`, 24 fallback decisions/results,
8 `A2_FALLAR_ENTER`.

### Objective cycle

The local objective repeatedly cycles through exactly:

```text
607,94,601 -> 613,88,601 -> 608,95,601
```

Examples:

```text
t3   local 1  objective 608,95,601
t230 local 41 objective 607,94,601
t288 local 42 objective 613,88,601
t321 local 43 objective 608,95,601
t359 local 61 objective 607,94,601
t405 local 62 objective 613,88,601
t450 local 63 objective 608,95,601
t454 FAIL local64/64 fallos10/10
```

After rearm/counter reset the same cycle restarts:

```text
t487 local16 607,94,601
t535 local17 613,88,601
t580 local18 608,95,601
t618 local36 607,94,601 ...
t856 FAIL local64/64
```

The pattern repeats at t880–1633 and beyond. Physical position remains around `607,95,601`; final
`607,95,601`, distance86.71.

### Fallback

```text
t288 A2_STAIR_FALLBACK_DECISION local41/64 anchor=607,93,601
     motivo=escalon verificado tras 1 candidatos (dy=1)
t288 A2_STAIR_FALLBACK_RESULT result=null
```

`result=null` means the fallback did not produce a usable route; the stair was placed/verified but the
executor did not get a traversable continuation. The same repeats at t405, t535, t666, etc.

### First failure

```text
t454 A2_FALLAR_ENTER local=64/64 fallos=10/10
 motivo={sin objetivo local valido (10/10): tope de escalones locales alcanzado (64)
 hacia 616,71,578}
```

The first demonstrated loss of useful progress is the repeated local objective cycle with
`fallback result=null`; the 64/10 counters are the terminal symptom, not the origin. There is no
evidence of horizontal mission progress during these cycles.

```text
ROOT_CAUSE: BUILD/STEP local-objective cycle with fallback null (no traversable continuation)
SYMPTOM: 64 local steps, 10 consecutive failures, FAILED
UNKNOWN: exact builder pipeline events named in the request are not present; placements are only
inferred from escalera.colocados and fallback reason.
```

## 4. IDX4 — near-miss reconstruction

### Mission and distance formula

```text
anchor/mission: 661,71,533
final exact: 596.75,79.00,500.25
horizontal distance = sqrt((596.75-661)^2 + (500.25-533)^2) = 72.115 -> 72.12
NOT 3D distance (3D would be 72.56).
```

The strict threshold is horizontal distance `<=62`; perimeter is `<=71`. Final 72.12 is outside both,
so it is a near-miss only relative to the 71 perimeter, not an entry.

### Final DROP

```text
t3476 DROP_VALID nodo=597,78,499 takeoff=597,79,499 dy=-1
      attempts=1/40 failures=22 pos=596,79,500
t3476 DROP_EDGE_STEP_SELECTED takeoff=596,79,500 landing=597,78,499
t3486 EDGE_STEP_PROGRESS pos=596.750,79.000,500.211 horizontal=1.033
t3491 EDGE_STEP_ABORTED reason=route_node_changed pos=597,79,499
t5985/5995 DROP_LANDING_PROGRESS
      routeIndex=0 landing=597,78,499 liveCell=596,79,500
      exact=596.749,79.000,500.250
      landingHeightReached=false
      liveCellSupported=true
      validLanding=false
      withinTolerance=true
      distanceSqr=1.378
      landingFits=true
      landingSupport=true
      delta=0.000,-0.078,0.000
```

At t3491 the mob is at `597,79,499`, directly above landing `597,78,499` (horizontal delta0). Phase41's
edge-step requires a positive horizontal distance; with dx=dz=0 it cannot activate. Y remains79 because
the mob is grounded on the takeoff block. `withinTolerance=true` and landing fit/support true; the only
false component is `landingHeightReached` (vertical-only DROP).

```text
ROOT_CAUSE: vertical-only DROP (horizontal distance 0) is outside Phase41 edge-step activation
SYMPTOM: landingHeightReached=false; validLanding=false; frozen at Y79 above landing Y78
UNKNOWN: exact reason for the t3491 route_node_changed abort; the subsequent vertical-only geometry
is sufficient to explain non-descent.
```

### Budget

Final local/global diagnostics:

```text
expGlobal=56000
ultimaGlobal exp=8000/8000 presupuesto=true aristas=448000
motivo=presupuesto global agotado
```

IDX4 consumed global-plan budget on the long route, but the final near-miss is not "budget prevented
the last step": at t3476 it had a valid DROP and reached the takeoff cell. The last step failed because
the resulting DROP is vertical-only and Phase41 does not cover distance0. Budget exhaustion explains the
earlier lack of alternative global plan, not the final geometric block.

## 5. IDX3 vs IDX4

| Dimension | IDX3 | IDX4 |
|---|---|---|
| Mission | `616,71,578` then `661,72,533` | `661,72,533` |
| Start/final | `594,96,600` → `607,95,601` | long route →`596.75,79,500.25` |
| Final horizontal dist | 86.71 | 72.12 |
| Local steps | 64/64 | 1/64 at last plan |
| Consecutive failures | 10/10 | 22 DROP failures at last node |
| Physical stairs | 24/24 `colocados` | 1/24 |
| Global expansions | 3768/8000 final, earlier 8000 | 56000 total, final 8000/8000 |
| Budget | local 64/10 | global budget exhausted |
| Last transition | BUILD/STEP local cycle | vertical-only DROP |
| First demonstrated block | fallback `null` / repeated local goals | Phase41 requires horizontal >0 |

**Independent defects.** IDX3 is a local BUILD/step objective cycle; IDX4 is a vertical-only DROP
coverage gap in Phase41. They share only the DROP/STEP terminal symptom.

## 6. Contracts / integrity

```text
Phase44/41/29/31/34/36: not modified.
Production/tests/config: not modified.
Worlds: not modified.
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
No new runtime; no Run2/Full12K.
```

## 7. Recommendation

1. **IDX4:** Phase41 (or a separate DROP-only policy) must support a vertical-only drop with horizontal
   distance0 after exact takeoff occupancy, without relaxing height/landing checks. This is the smallest
   evidence-backed gap.
2. **IDX3:** instrument/repair the local stair fallback lifecycle: why `result=null` after a verified
   stair and why the same three objectives repeat/reset. Do not raise the 64/10 limits.
3. Do not change budgets or predicates until the vertical-only case and the local cycle are separately
   confirmed.
