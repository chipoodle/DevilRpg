# A2 Diagnostic Run1 after Phase55 (2026-11-19)

## 1. Result

**FAIL — A2 3/8 strict, 3/8 perimeter. C4 PASS. Fixture PASS. No watchdog.**

The new join-based population census fixed the fixture blocker. The canonical harness and both trace
properties were effective. Three attackers entered `DENTRO-PARED`; all three later died in combat.
IDX3 and IDX5 still hit the local stair cap; IDX4, IDX7 and IDX8 did not reach the village.

- RUN_ID: `a2n-20261119-151818`
- DATE: 2026-11-19 15:18 America/Mexico_City
- BRANCH: `feature/autonomous-village`
- INITIAL_HEAD: `662502f2826749b148f0565034399e23e2ed3df6`
- PRODUCTION_SHA: `10719ff82465a0d8997cb2af3af23c9f71292b19`
- HARNESS: canonical `tools/a2n/A2NavHarness.java`, blob `914d26da4f9a553f43936812ddc303ed56a24243`
- NAVPLANNER blob: `5666e42693fe2e90dff1d2ac1004b59b9b62d7cd`
- Seed: `12345`
- World: new disposable `build/a2n/a2n-20261119-151818/game/a2n`
- PRODUCTION_MODIFIED: false
- FIXTURES_MODIFIED: false
- Original worlds modified: false

## 2. Effective JVM trace

```text
15:19:23 [A2N] JVM_TRACE traceNav=true traceGlobalSearch=true
         java=21.0.7 vendor=Microsoft
```

Both properties reached the dedicated server JVM.

The tracked wrapper itself mis-bound `-LimiteMinutos` before launching Java; no Java process was started
by that attempt. The same local launcher was then invoked with the wrapper's four `ORG_GRADLE_PROJECT_*`
and both JVM flags set directly. The server's `JVM_TRACE` line is the authoritative proof.

## 3. Fixture / population

```text
C4 CHUNKS forced=289 loaded=true
C4 POPULATION SOURCES aabb=16 joinEvents=16 capturedOnly=0
C4 POPULATION villagers=16 guards=4 founders=4/4
  professionals=12/12 professionGoals=12/12 trackedWorkerUuids=12
  roster includes shepherd=1, mason=1
C4 FIXTURE ready=true
SPAWN TOTAL: intentos=8 ok=8 fallos=0
```

The population-query correction works: AABB and join-event sources agreed on 16/16.

## 4. A2 per attacker

| IDX | UUID | Perimeter | Strict | Entry tick | End | Final position | Result |
|---:|---|---|---|---:|---|---|---|
| 1 | `0a7c81d4…` | yes t684 | **yes** | 1018 | dead t1751 | 666,71,539 | fought inside; died |
| 2 | `d6a7a8f9…` | yes t289 | **yes** | 747 | dead t1403 | 668,71,560 | fought inside; died |
| 3 | `ec679c05…` | no | no | - | FAILED | 594,74,600 | local stair cap 64/64 |
| 4 | `9f5d8cb2…` | no | no | - | PLANNING | 566,122,533 | CADENCE_WAIT, no valid stair candidate |
| 5 | `b0d40cf7…` | no | no | - | FAILED | 594,75,466 | local stair cap 64/64 |
| 6 | `7d1a2b27…` | yes t279 | **yes** | 728 | dead t2033 | 663,72,533 | fought inside; died |
| 7 | `b9bfa8ab…` | no | no | - | FAILED | 705,65,459 | route exists, no stair placement |
| 8 | `4c785a3c…` | no | no | - | PLANNING | 769,63,497 | global search 6737+/8000, frontier 763 |

```text
STRICT_ENTRANTS: 3/8 (ever inside)
PERIMETER_CONTACTS: 3/8
DEATHS: 3 (all three strict entrants)
ALIVE AT PHASE B END: 5
FINAL: 0 alive, 3 dead, 5 disappeared after cleanup
```

No perimeter/strict for IDX3/4/5/7/8.

## 5. Telemetry

```text
GLOBAL_SEARCH_START: 113
GLOBAL_FIRST_EXPANSION_START: 113
GLOBAL_FIRST_EXPANSION_CANDIDATE: 6328
GLOBAL_FIRST_EXPANSION_END: 113
GLOBAL_SEARCH_COMPLETE: 62
GLOBAL_SEARCH_CANCEL: 44
GLOBAL_SEARCH_RESTART: 5
```

First-expansion endings per attacker:

```text
idx1 56 candidates / 24 enqueued
idx2 56 / 21
idx3 56 / 24
idx4 56 / 13
idx5 56 / 24
idx6 56 / 21
idx7 56 / 18
idx8 56 / 10
```

The previous exp1/0 trap is no longer the dominant behavior: all first expansions generated 56 and
enqueued 10-24 candidates.

Restarts recorded `reason=search_parameters_changed`, e.g.:

```text
uuid=ec679c05 oldStart=594,75,600 newStart=594,74,600 oldExp=8
uuid=4c785a3c oldStart=768,63,498 newStart=768,63,497 oldExp=20
```

## 6. Failure analysis

### IDX3 (`ec679c05…`)

Global search progressed (`exp=10`, `aristas=560`, `encolados=132`, `rutaFrente=1`) but local stair
budget exhausted:

```text
escalones=64/64 fallosEscalon=10/10
"tope de escalones locales alcanzado (64)"
```

Final `594,74,600`, `FAILED`. Navigation/step-budget failure, not fixture.

### IDX5 (`b0d40cf7…`)

Same pattern:

```text
exp=10 aristas=560 encolados=132 rutaFrente=1
escalones=64/64 fallosEscalon=10/10
"tope de escalones locales alcanzado (64)"
```

Final `594,75,466`, `FAILED`.

### IDX4 (`9f5d8cb2…`)

At `566,122,533` with a 50-block descent, `CADENCE_WAIT` yielded no valid local objective. All 28 stair
candidates rejected; several `soporte=false` or landing-entry blocked. Ended `PLANNING`, `escalones=0/64`.

### IDX7 (`b9bfa8ab…`)

Global route existed (`ruta=79`, `exp=2531`) and local waypoint selected, but no stair was placed;
`escalones=1/64`, final `FAILED` at `705,65,459`.

### IDX8 (`4c785a3c…`)

Still `PLANNING` at `769,63,497` with global progress `exp=6737/8000`, frontier 763. Incremental search
did not finish before the run ended; no local route.

## 7. Historical comparison

```text
Phase55 previous Run1: 0/8 strict, 1/8 perimeter, 0 deaths, C4 FAIL.
This run:              3/8 strict, 3/8 perimeter, 3 deaths, C4 PASS, fixture PASS.
```

Improvements: fixture census, C4 defense, 3 real entries, no watchdog.
Remaining gaps: IDX3/5 stair cap, IDX4 CADENCE/no candidate, IDX7 no placement, IDX8 unfinished global.

## 8. C4

```text
guardasQueCombatieron=4/4
trabajadoresEsperados=12, vivos=12, reanudados=12
bajasTrabajadores=0
C4_PASS
```

## 9. Integrity

```text
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

Artifacts:

```text
build/a2n/a2n-20261119-151818/srv.log
build/a2n/a2n-20261119-151818/nav-trace.log
build/a2n/a2n-20261119-151818/launcher-status.txt
build/a2n/a2n-20261119-151818/fin.txt
```

## 10. Conclusion

The Phase55 architecture is functional and measurably better, but A2 is not solved. This is a navigation
FAIL, not a fixture or C4 failure. No Run2/Full12K was executed.

A2 remains OPEN.
