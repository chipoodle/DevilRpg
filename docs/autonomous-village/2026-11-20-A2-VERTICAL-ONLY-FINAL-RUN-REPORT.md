# A2 vertical-only final Run1 — runtime report (2026-11-20)

## 1. Result

**FAIL — A2 1/8 strict, 2/8 perimeter, one death; C4 PASS. BUILD was not exercised at runtime.**

- RUN_ID: `a2n-20261120-173629`
- BRANCH: `feature/autonomous-village`
- INITIAL_HEAD: `1c9ff7422ec3e4dddd28ad094e33d5c3cb6d49ce`
- TESTED_HEAD: `54e3c9a8b11e2b6125a589ee64795db5a98c62fb`
- PRODUCTION_SHA: `7e78c9360c7960f13da6062f38196e8b1627b93a`
- RUNTIME_WORLD: `build/a2n/a2n-20261120-173629/game/a2n`
- SEED: `12345`
- START/END: 17:36:29–17:43:53 (approx 7m24s)
- Second run: none.

## 2. Static gate (before runtime)

```text
compileJava: PASS (exit 0, 21 s)
test:        PASS (exit 0, 50 s)
lint strict: PASS (exit 0)
diff --check:PASS (exit 0)
```

```text
NAV_NEIGHBOR_POLICY_TEST: OK (16 assertions)
NAV_PLANNER_STATE_TEST: OK (53 assertions)
Named policy tests: 56
FALLO: markers: 0
```

All five vertical-only blobs matched the handoff.

## 3. Fixture / C4

```text
JVM_TRACE traceNav=true traceGlobalSearch=true
C4 CHUNKS forced=289 loaded=true
C4 FIXTURE ready=true
SPAWN TOTAL intentos=8 ok=8 fallos=0
Population 16/16; professionals 12/12
C4_PASS: guards 4/4, workers 12/12, jobSite=[1,0,0], no worker losses, no instrument failures
```

## 4. A2 result

```text
PERIMETER_ENTRANTS: 2/8 (IDX1 t512, IDX6 t280)
STRICT_ENTRANTS:    1/8 (IDX1 t1426, pos 699,71,581)
DEATHS:             1 (IDX1 t1922, pos 670,71,550)
```

| IDX | Final | Position | Result |
|---:|---|---|---|
| 1 | dead t1922 | 670,71,550 | perimeter then strict; died inside |
| 2 | FAILED | 660,72,608 | no perimeter |
| 3 | RECOVERING | 594,76,600 | no perimeter |
| 4 | PLANNING | 580,86,520 | no perimeter; DROP churn |
| 5 | RECOVERING | 594,77,466 | no perimeter |
| 6 | FAILED | 658,71,470 | perimeter only; DROP at t~? |
| 7 | FAILED | 728,63,466 | no perimeter; BUILD waypoint selected |
| 8 | PLANNING | 748,63,532 | no perimeter |

Death cause was **not present** in `srv.log` (no `died/slain` line); only the `MUERTO` state is available.

## 5. First expansion / vertical-only effect

```text
GLOBAL_FIRST_EXPANSION_CANDIDATE: 4851
GLOBAL_FIRST_EXPANSION_END:        77
zero-enqueued first expansions:    0
```

Every first expansion enqueued successors. The previous 56-candidate/zero-edge pattern is gone. IDX3/5 now
progress to `exp=5` with a `rutaFrente=1` before front exhaustion; IDX4 remains in DROP churn.

## 6. BREAK stand / LOS

```text
BREAK_STAND_CANDIDATE: 128
  OUT_OF_ACTION_REACH:       64
  ACTION_NOT_EXECUTABLE:     32
  NOT_SINGLE_STEP_REACHABLE: 32
  ACCEPTED:                   0

BREAK_LOS_DIAG: 128
  RAYCAST_REJECT:      118
  PRECONDITION_REJECT:  10
  accepted:               0
```

No BREAK stand was accepted. Example raycast rejection hits the block above the target:

```text
stand=728,64,600 target=729,62,600
rayEnd=(729.5,62.5,600.5) hitBlock=729,63,600 hitType=BLOCK
```

The current feet cell was in the candidate set but did not survive the reach/LOS filters. BREAK execution
was therefore not validated.

## 7. BUILD / DROP

```text
BUILD-VERIFICADO: 0
BUILD_FEET_APPROACH: 0
BUILD-ACERCAR-PIES: 0
```

**BUILD: NOT EXERCISED.** No placement, verification or feet arrival.

IDX7 did generate a local BUILD waypoint:

```text
objetivo=706,69,459 buildable=true escalones=1/64
transicion=BUILD build=true rutaGlobal=56 exp=8000
```

but the entity was already `FAILED` with the global budget exhausted; no `actionPos`/placement occurred.

DROP remained active but unsuccessful overall: `DROP-EN-CURSO` events present, 127 landing lines, IDX6
ended FAILED after a one-block drop route, IDX4 shows 19 descent failures.

## 8. Search lifecycle highlights

- IDX1 completed a 48-node route (`exp=530`) and entered strictly; it died later.
- IDX6 reached perimeter, completed a 62-node route (`exp=338`), then ended FAILED after DROP.
- IDX3/5: `exp=5`, front exhausted with `rutaFrente=1`; repeated ACCESS_CLEARED/CADENCE.
- IDX7: `exp=8000`, `aristas=504000`, `encolados=132639`, failed at budget with BUILD frontier.
- IDX8: pending `exp=6438/8000`, frontier942, stalePops2529, reopens1.

## 9. Integrity

```text
A2 APPDATA BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
A2 run     F4EEAA13C56D92812B7B668EFDAF4AB200973A9977006466E969481C09419B44
C4 APPDATA 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
C4 run     11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

Unchanged after runtime.

Log hashes:

```text
srv.log             00E94969BFA8A95036958053413D3A446E029482EF80FECFF8DB12DB47A36AE3
nav-trace.log       3786603A631A2B7725590E15EE861CA4742CAED233C72DB44AA61387D1E55181
fin.txt             A1140A5D482020F6F7F0A3E20EADCED3C574FE074516A28B814218D23B99AB85
launcher-status.txt A5F504E33C59E95960332B3C5A9ECF4EE88402554C8E2D05ACE8217B4903251C
```

## 10. Conclusion

Vertical-only connectivity fixed the zero-successor symptom: all 77 first expansions enqueued edges.
C4 now PASSes and IDX1 achieved strict entry. A2 still fails the 8/8 criterion: only IDX1 entered strictly,
it died, and 6/8 never reached the village. BUILD remains runtime-unmeasured.

A2 OPEN; C4 PASS observed but not a standalone revalidation; no retry/Run2/Full12K.
