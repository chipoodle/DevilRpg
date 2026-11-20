# A2 Run1 — D3 single-block BUILD runtime report (2026-11-20)

## 1. Result

**FAIL — 0/8 strict, 2/8 perimeter, no combat deaths, C4 FAIL. D3 BUILD was not exercised.**

The fixture and spawn passed, but no BUILD transition, placement, feet-approach, or physical arrival
occurred. The run therefore cannot validate D3 runtime behavior.

- RUN_ID: `a2n-20261120-140611`
- BRANCH: `feature/autonomous-village`
- INITIAL_HEAD: `3d7e6bf4d44f07b3f91068a493bb9cb1ace7fc58`
- TESTED_HEAD: same
- PRODUCTION_SHA: `c520198c0b89e5da0d6f2e760b22e8d43fcec7d7` (D3 implementation)
- RUNTIME_WORLD: `build/a2n/a2n-20261120-140611/game/a2n`
- SEED: `12345`
- START: 14:06:11; END: 14:14:11 (approx 8 minutes)
- NO SECOND RUN.

## 2. Population / fixture

```text
JVM_TRACE traceNav=true traceGlobalSearch=true
C4 CHUNKS forced=289 loaded=true
C4 POPULATION SOURCES aabb=16 joinEvents=16 capturedOnly=0
villagers=16, professionals=12/12, founders=4/4
C4 FIXTURE ready=true
SPAWN TOTAL intentos=8 ok=8 fallos=0
```

## 3. Per-IDX result

| IDX | Perimeter | Strict | Final state | Position | Notes |
|---:|---|---|---|---|---|
| 1 | yes t509 | no | PLANNING | 702,71,583 | global pending exp5263/8000, frontier556 |
| 2 | no | no | TRAVERSING | 662,72,608 | DROP in progress |
| 3 | no | no | RECOVERING | 594,76,600 | global exp1/8000, front exhausted |
| 4 | no | no | PLANNING | 580,86,520 | global exp1/8000; DROP failures |
| 5 | no | no | RECOVERING | 594,77,466 | global exp1/8000 |
| 6 | yes t280 | no | PLANNING | 658,71,470 | one BREAK verified; global pending exp5218 |
| 7 | no | no | PLANNING | 726,63,475 | global pending exp6890/8000 |
| 8 | no | no | PLANNING | 748,63,534 | global pending exp6542/8000 |

```text
PERIMETER_ENTRANTS: 2/8 (IDX1, IDX6)
STRICT_ENTRANTS: 0/8
DEATHS: 0
ALIVE AT PHASE B: 8/8
```

## 4. D3 BUILD runtime

```text
BUILD-VERIFICADO: 0
BUILD_FEET_APPROACH: 0
BUILD-ACERCAR-PIES: 0
JUMP_UP rejections: 0
```

No BUILD node appeared in any route. The D3 single-block BUILD implementation is **runtime-unmeasured**:
placement, verification, feet arrival and routeIndex advancement could not be observed.

## 5. Navigation telemetry

```text
GLOBAL_SEARCH_START: 81
GLOBAL_SEARCH_COMPLETE: 59
GLOBAL_SEARCH_CANCEL: 15
GLOBAL_SEARCH_RESTART: 3
GLOBAL_FIRST_EXPANSION_END: 81
A2_LOCAL_STAIR_WAIT: 545
```

Failures:

- IDX1/6 reached perimeter but stalled in incremental global search; no route completion.
- IDX3/5/4 hit the old first-expansion trap (`exp=1`, `aristas=56`, `encolados=0`) at their current starts.
- IDX7/8 accumulated large pending searches (exp6890/6542) without completing.
- No local step cap (IDX3/5 `escalones=0`), no CADENCE infinite loop of the prior form, but no route.
- No BUILD, so the D3 BUILD correction is not testable from this run.

## 6. C4

```text
C4 FAIL
guardasQueCombatieron=false
profesionalesReanudados=true
asignacionesJobSiteLimpias=false jobSite=[0,0,0]
trabajadoresVivos=12/12, bajas=0
```

C4 failed because no attacker reached/engaged the guards. It is a consequence of A2 navigation failure,
not a standalone C4 regression.

## 7. Integrity

Protected worlds before/after:

```text
APPDATA A2: BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
run A2:     F4EEAA13C56D92812B7B668EFDAF4AB200973A9977006466E969481C09419B44
APPDATA C4: 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
run C4:     11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

All unchanged. Production/tests untouched. Runtime world is disposable.

Artifacts:

```text
build/a2n/a2n-20261120-140611/srv.log
build/a2n/a2n-20261120-140611/nav-trace.log
build/a2n/a2n-20261120-140611/launcher-status.txt
build/a2n/a2n-20261120-140611/fin.txt
```

## 8. Conclusion

The run is an A2 FAIL. It does not validate D3 BUILD; the immediate blocker remains global search
coverage/trapping (IDX3/4/5/7/8) and insufficient progression for IDX1/6. No runtime retry was made.
A2 remains OPEN; C4 remains unvalidated.

