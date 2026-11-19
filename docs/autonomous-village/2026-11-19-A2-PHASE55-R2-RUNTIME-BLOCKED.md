# A2 Phase55-R2 — authorized Run1 runtime (BLOCKED — fixture invalid)

## 1. Result

**BLOCKED — FIXTURE INVALID BEFORE SPAWN.** The non-blocking chunk loader worked (289/289 READY in one
tick), but the fixture population verification failed: **14 villagers / 10 professionals**, expected
12 professionals. The harness closed `INVALIDO`; no attacker spawned and no A2 result exists.

- BRANCH: `feature/autonomous-village`
- INITIAL_HEAD: `d96885fdc1c6d711f971b0b318f85d6d91f4c98c`
- RUN_ID: `a2n-20261119-031255`
- PRODUCTION_SHA: `11ff43cd338d7a6222a146a2ed642a5546fb2910`
- PRODUCTION_MODIFIED: false
- TESTS_MODIFIED: false
- WORLDS_MODIFIED: false
- No retry performed.

## 2. Static baseline

```text
compileJava -Pa2nDiagnostics=true: PASS (previous Phase55-R2)
gradlew test: PASS
NAV_PLANNER_STATE_TEST: OK, 54 executed assertions
lint_aldea.py --strict: PASS
git diff --check: PASS
```

Production sources matched `11ff43cd`; diff `11ff43cd..HEAD` in `src`/`build.gradle` was empty.

## 3. Trace properties

```text
-Ddevilrpg.a2n.traceStair=true
-Ddevilrpg.a2n.traceStairCandidates=true
```

Injected via `JAVA_TOOL_OPTIONS`; stair trace code was compiled in and active.

## 4. Chunk loader

```text
03:18:09 C4 CHUNKS TICKETS requested=289 ticketOnly=true timeoutTicks=600
03:18:10 C4 CHUNKS WAIT loaded=289/289 pending=0 elapsed=1 remaining=599
03:18:10 C4 CHUNKS READY loaded=289/289 elapsed=1
03:18:10 C4 CHUNKS loaded=true
```

```text
CHUNKS_REQUESTED=289
CHUNKS_READY=289
DEADLINE=600 ticks
READY before deadline=YES (1 tick)
TIMEOUT=NO
```

## 5. Fixture verification / failure

```text
03:19:08 C4 PRODUCTION VILLAGE generated=true center=661,71,533
         wallOk=true supportMissing=0 damagedColumns=0
03:19:08 C4 POPULATION villagers=14 guards=4 founders=4/4
         professionals=10/12 professionGoals=10/12 trackedWorkerUuids=10
         roster={butcher=1, cleric=1, farmer=3, fisherman=1, fletcher=1,
                 nitwit=1, toolsmith=1, weaponsmith=1}
03:19:08 C4 FIXTURE ready=false
03:19:08 FIN INVALIDO (raid) - RAID FIXTURE INVALIDO - Edit 4 | fallosDeInstrumento=0
```

The fixture's own population gate (`professionals == expectedProfessionals` and profession goals) failed.
Logs show 14 villagers, including 4 `null` profession entries; two expected professionals are missing
from the roster. This is a fixture-preparation failure, not a navigation result.

```text
ATTACKERS_SPAWNED: 0
```

## 6. A2 / C4 / navigation

```text
A2_STRICT_ENTRANTS: no result
A2_PERIMETER_CONTACTS: no result
C4: not reached
A* incremental progression: not reached (no attackers)
IDX5 CADENCE: not reached
IDX3: not reached
WATCHDOG: none
```

No movement/routing evidence can be attributed to this run.

## 7. Interpretation

- Phase54B non-blocking chunk loading is validated again.
- The fixture population gate is not deterministic: this fresh world produced 10 professionals instead
  of 12 despite village generation `generated=true` and wall verification PASS.
- No A2 acceptance measure exists for this run.

## 8. Integrity

```text
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

Artifacts:

```text
build/a2n/a2n-20261119-031255/srv.log
build/a2n/a2n-20261119-031255/nav-trace.log
build/a2n/a2n-20261119-031255/launcher-status.txt
build/a2n/a2n-20261119-031255/fin.txt
```

## 9. Recommendation

Do not retry without a new authorization. The next action is fixture-only diagnosis of the
population gate: why a generated village contains 14 villagers/10 professionals and four `null`
professions instead of the expected 12 professionals, and whether the profession assignment/maintenance
timing is nondeterministic. Do not change navigation or A2 criteria.

A2 remains OPEN.
