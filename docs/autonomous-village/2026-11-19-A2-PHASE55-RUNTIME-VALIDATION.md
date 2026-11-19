# A2 Phase55 — authorized Run1 runtime (2026-11-19)

## 1. Result

**FAIL — no watchdog, but navigation did not produce entries; C4 FAILED.**

The Phase54B fixture passed and the incremental global A* avoided the previous 60 s watchdog. But A2
finished with **0/8 strict, 1/8 perimeter**, no combat deaths, and C4 `FAIL` because guards never
engaged (`guardasQueCombatieron=false`, `profesionalesReanudados=false`).

- RUN_ID: `a2n-20261119-093456`
- BRANCH: `feature/autonomous-village`
- INITIAL_HEAD: `b23f0b56fce94fed5de62c17988455a7d10dd4b1`
- PRODUCTION_SHA: `11ff43cd338d7a6222a146a2ed642a5546fb2910`
- HARNESS: `f437f372...` (Phase54B fixture)
- PRODUCTION_MODIFIED: false
- TESTS_MODIFIED: false
- WORLDS_MODIFIED: false
- No retry.

## 2. Fixture

```text
09:36:36 C4 CHUNKS TICKETS requested=289 ticketOnly=true timeoutTicks=600
09:36:36 C4 CHUNKS READY loaded=289/289 elapsed=1
09:36:53 C4 POPULATION villagers=16 guards=4 founders=4/4
         professionals=12/12 professionGoals=12/12
         roster includes mason=1, shepherd=1
09:36:53 C4 FIXTURE ready=true
```

The NBT-forensic fix/verification allowed both sheepherd and mason to count this time. Fixture PASS.

## 3. Incremental A* / watchdog

```text
watchdog events: 0
NAV_GLOBAL_SEARCH_PENDING: 66
RUTA GLOBAL completed: 10
```

Representative pending slices:

```text
tick=2  exp=4/8000 slice=4 frontera=16
tick=22 exp=8/8000 slice=4 frontera=23
```

The incremental A* did not block a tick. However global searches often completed with `exp=1/8000`
and `frente alcanzable agotado`, giving no route:

```text
idx3 #261 inicio=594,92,600 exp=1/8000 frente=594,92,600 rutaFrente=0
idx5 #263 inicio=594,83,466 exp=1/8000 frente=594,83,466 rutaFrente=0
```

Other attackers remained PLANNING or TRAVERSING with no progress toward the village.

## 4. A2 outcomes

```text
STRICT: 0/8
PERIMETER: 1/8 (idx6 t271, final 661,71,464 dist68.63)
DEATHS: 0
ALIVE AT END: 8/8
```

Per attacker:

```text
idx1 0a7c81d4... final 727,65,600 dist95.12 TRAVERSING
idx2 d6a7a8f9... final 660,72,608 dist75.09 TRAVERSING
idx3 2cae8dcf... final 594,88,600 dist94.75 PLANNING
idx4 527641b9... final 566,122,533 dist94.50 PLANNING
idx5 a6cecab5... final 594,79,466 dist94.05 PLANNING
idx6 7d1a2b27... final 661,71,464 dist68.63 TRAVERSING; perimeter t271
idx7 299ba43f... final 728,63,466 dist94.75 PLANNING
idx8 ec2c1ddb... final 756,63,533 dist95.50 PLANNING
```

No attacker reached `DENTRO-PARED`.

## 5. C4

```text
C4 DEFENSE guardasQueCombatieron=0/4
trabajadoresEsperados=12 trabajadoresVivos=12 trabajadoresReanudados=0
villageVillagersAlive=16 professionGoals=12/12
FIN: C4_FAIL (raid) - guardasCombatieron=false profesionalesReanudados=false
```

The C4 failure is a consequence of the attackers never reaching/engaging the village, not a standalone
C4 regression.

## 6. IDX5

UUID `a6cecab5-550f-445e-af7d-3a236de68929`.

```text
A2_STAIR_CADENCE_ROUTE_PRESERVED: 0
A2_LOCAL_STAIR_WAIT: 430
STAIR_BUILDER_CANDIDATE: 2220
CONSTRUCCION-ESCALERA: 0
ESCALON-TRAVESADO: 0
final 594,79,466 PLANNING
```

CADENCE_WAIT is active but never produced a preserved route event; the attacker remained in planning.
No causal validation of the Phase53 route-preservation behavior is possible from this run.

## 7. IDX3

UUID `2cae8dcf-4357-4c90-be9c-3ca9e530875f`, final `594,88,600` PLANNING. No candidate/place/traversal
success; global front exhausted at exp=1/8000. No stair placement evidence.

## 8. Integrity

```text
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

Artifacts:

```text
build/a2n/a2n-20261119-093456/srv.log
build/a2n/a2n-20261119-093456/nav-trace.log
build/a2n/a2n-20261119-093456/launcher-status.txt
build/a2n/a2n-20261119-093456/fin.txt
```

## 9. Recommendation

The chunk-loader fix is validated. The incremental A* removed the watchdog but did not restore useful
routing: global searches terminate with `exp=1`/front exhausted for key attackers, and most remain in
PLANNING. This is a navigation/A* result, not a fixture failure; it needs a new design/diagnosis before
another Run1. No retry, no Run2/Full12K.

A2 remains OPEN.
