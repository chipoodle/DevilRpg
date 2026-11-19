# A2 Phase 54B-R1 — authorized Run1 validation (2026-11-19)

## 1. Result

**BLOCKED — FIXTURE SUCCEEDED; RUN TERMINATED BY GLOBAL-PLANNER WATCHDOG.**

The new chunk loader worked: 289/289 chunks READY in one tick, village verification passed, and all
8 attackers spawned. The single authorized Run1 was then killed by the server watchdog during the first
global A* planning burst (`NavPlanner/NavObstruction.fits`), before any A2 result.

- RUN_ID: `a2n-20261119-011138`
- BRANCH: `feature/autonomous-village`
- INITIAL_HEAD: `e4035c5522e1019e6527dc5faafc9ff2bca209e9`
- FINAL_HEAD (at report): same
- PRODUCTION_SHA: `656ce7d0f6182fd15e0b1bbb0bd6684c180bd0e6`
- HARNESS: `f437f3723f52a802f086f29eb0b5045e6545c3ec`
- PRODUCTION_MODIFIED: false
- TESTS_MODIFIED: false
- WORLDS_MODIFIED: false

## 2. Trace properties

```text
JAVA_TOOL_OPTIONS=-Ddevilrpg.a2n.traceStair=true -Ddevilrpg.a2n.traceStairCandidates=true
```

Evidence: `STAIR_BUILDER_CANDIDATE` and `STAIR_BUILDER_ANCHOR_REFRESH` events were emitted.

## 3. Chunk loader

```text
01:19:12 C4 CHUNKS TICKETS requested=289 ticketOnly=true timeoutTicks=600
01:19:13 C4 CHUNKS WAIT loaded=289/289 pending=0 elapsed=1 remaining=599
01:19:13 C4 CHUNKS READY loaded=289/289 elapsed=1
01:19:13 C4 CHUNKS loaded=true ticketOnly=true
```

```text
CHUNKS_REQUESTED: 289
CHUNKS_READY: 289
DEADLINE: 600 ticks
READY before deadline: YES (1 tick)
TIMEOUT: NO
```

No blocking `setChunkForced/getChunk` occurred in this preparation.

## 4. Fixture / spawn

```text
01:20:05 C4 PRODUCTION VILLAGE generated=true objective=0 center=661,71,533
         baseY=71 wallColumns=496 wallCells=992 wallPresent=992 wallOk=true
         supportMissing=0 damagedColumns=0
01:20:05 C4 POPULATION villagers=16 guards=4 founders=4/4 professionals=12/12
01:20:05 C4 FIXTURE ready=true
01:20:05 SPAWN TOTAL intentos=8 ok=8 fallos=0
```

All eight UUIDs were spawned and registered. Virgin checks, preGenerate, center, wall and population all
passed.

## 5. Watchdog

```text
01:20:14 ServerHangWatchdog: single tick took 60.04 s
```

Stack:

```text
BlockCollisions.computeNext
NavObstruction.fits:98
NavObstruction.classify:231
NavPlanner.validarArista:115
NavPlanner$1.expand:378
NavGraphSearch.search:108
NavPlanner.planGlobalInternal:496
NavPlanner.planGlobal:331
NavExecutor.rutaGlobalHacia:1470
NavExecutor.elegirObjetivoLocal:1583
NavExecutor.escalonarObjetivoLocal:1932
NavExecutor.tryPlan:3375
NavExecutor.tick:3156
```

This is the same class of global-planner CPU burst documented in Phase39, now in the first plan after
spawn. It is not a fixture/chunk-loader failure. Because the watchdog killed the server, there is no
`fin.txt` and no A2 result.

## 6. IDX5

UUID `0d53b0b8-0d22-4d92-b675-1e45d0597c3b`.

```text
A2_STAIR_CADENCE_ROUTE_PRESERVED = 0
A2_LOCAL_STAIR_WAIT = 0
MOVE_COMMAND = 0
A2_TRYPLAN_ENTER = 1 (tick2)
```

At t2 it entered global planning and was caught by the watchdog before any cadence/route-preserved
event. No causal evidence is available in this run.

```text
IDX5_CADENCE_ROUTE_PRESERVED: NOT OBSERVED
IDX5_COUNTERS_INVARIANT: NOT OBSERVED
IDX5: INSUFFICIENT_EVIDENCE (planning crashed)
```

## 7. IDX3

UUID `29437d58-b949-4fe1-98b6-0da9c943f2f4`.

No stair placement/traversal occurred before the crash; its first planning was also swallowed by the
watchdog.

```text
IDX3_CANDIDATES: partial pre-crash only
IDX3_PLACEMENTS: 0
IDX3_TRAVERSALS: 0
```

## 8. A2 result

```text
A2_STRICT_ENTRANTS: no result (run aborted before phase B)
A2_PERIMETER_CONTACTS: no result
A2_DETAILS: 8 spawned, 0 evaluated completion
C4: not reached in this run
```

No attacker can be classified as entrant/non-entrant from this aborted run.

## 9. Static

```text
gradlew test: PASS
lint_aldea.py --strict: PASS
git diff --check: PASS
```

## 10. Integrity

```text
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

Artifacts:

```text
build/a2n/a2n-20261119-011138/srv.log
build/a2n/a2n-20261119-011138/nav-trace.log
build/a2n/a2n-20261119-011138/launcher-status.txt
build/a2n/a2n-20261119-011138/game/crash-reports/crash-2026-11-19_01.20.14-server.txt
```

## 11. Recommendation

The fixture fix is validated; do not change it. The remaining runtime blocker is the synchronous global
A* burst (448k edge budget) causing the watchdog. That requires a separate approved design/diagnostic
before another Run1. Do not retry the same Run1 without authorization.

A2 remains OPEN.
