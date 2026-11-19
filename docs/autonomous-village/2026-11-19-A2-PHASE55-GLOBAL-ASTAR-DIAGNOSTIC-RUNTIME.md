# A2 Phase55 — global A* diagnostic runtime (2026-11-19)

## 1. Result

**BLOCKED — fixture failed before spawn; diagnostic telemetry could not be exercised.**

The single authorized diagnostic run stopped at `INVALIDO` because the population gate saw
15 villagers / 11 professionals (expected 12). No attacker spawned, so no global A* search ran and no
`GLOBAL_*` event could be emitted. The run was not retried.

- RUN_ID: `a2n-20261119-114535`
- DATE: 2026-11-19 11:45 America/Mexico_City
- BRANCH: `feature/autonomous-village`
- INITIAL_HEAD: `c889b641dcb300fcce78a0a4ec31e1f33f220ef1`
- EXECUTED_PRODUCTION_SHA: `10719ff82465a0d8997cb2af3af23c9f71292b19`
- NAVPLANNER_BLOB_SHA: `5666e42693fe2e90dff1d2ac1004b59b9b62d7cd`
- World: new disposable `a2n` world
- PRODUCTION_MODIFIED: false
- WORLDS_MODIFIED (original): false
- FIXTURES_MODIFIED: false

## 2. Git / production verification

```text
Local HEAD == origin == c889b641 (no sync needed).
NavPlanner blob == target 5666e42693fe2e90dff1d2ac1004b59b9b62d7cd.
Production diff 10719ff8..HEAD in src/build.gradle: empty.
Worktree production: clean; untracked local artifacts preserved.
```

## 3. JVM trace properties

Requested:

```text
-Ddevilrpg.a2n.traceNav=true
-Ddevilrpg.a2n.traceGlobalSearch=true
```

Observed:

```text
TRACE_NAV_EFFECTIVE: unverified
TRACE_GLOBAL_SEARCH_EFFECTIVE: unverified
```

Reasons:

- The launcher sets `JDK_JAVA_OPTIONS` with `-Ddevilrpg.a2n.traceNav=true` but **does not include**
  `-Ddevilrpg.a2n.traceGlobalSearch=true`.
- `traceGlobalSearch` was supplied only through `JAVA_TOOL_OPTIONS`; the server log has no
  `Picked up JAVA_TOOL_OPTIONS` line, so effective propagation to the server JVM cannot be confirmed.
- No `nav-trace.log` was created and no `GLOBAL_*` event appears in `srv.log`. Because no attacker
  spawned, zero events are expected even if enabled; this is not positive proof either way.

This alone is a §4/§9 stop condition for interpreting a diagnostic run.

## 4. Fixture and population

```text
11:47:13 C4 CHUNKS READY loaded=289/289 elapsed=1
11:47:31 C4 PRODUCTION VILLAGE generated=true wallOk=true
11:47:31 C4 POPULATION villagers=15 guards=4 founders=4/4
         professionals=11/12 professionGoals=11/12 trackedWorkerUuids=11
         roster={butcher=1, cleric=1, farmer=3, fisherman=1, fletcher=1,
                 mason=1, nitwit=1, toolsmith=1, weaponsmith=1}
11:47:31 C4 FIXTURE ready=false
11:47:31 FIN INVALIDO (raid) - RAID FIXTURE INVALIDO - Edit 4 | fallosDeInstrumento=0
```

Missing roster entry: SHEPHERD. The saved world NBT still contains it alive:

```text
f5ec6f5f-2367-474f-95ff-a154cb61d234
minecraft:shepherd 708.5,71.0,533.5
Health=20, DeathTime=0
```

Mason is present and counted. This reproduces the earlier NBT finding: the worker exists, but the
population query omits it. Exact cause remains unknown.

## 5. A2 / diagnostics

```text
ATTACKERS_SPAWNED: 0
GLOBAL_SEARCH_START_COUNT: 0
GLOBAL_FIRST_EXPANSION_COUNT: 0
GLOBAL_FIRST_EXPANSION_CANDIDATE_COUNT: 0
GLOBAL_FIRST_EXPANSION_END_COUNT: 0
GLOBAL_SEARCH_COMPLETE_COUNT: 0
GLOBAL_SEARCH_CANCEL_COUNT: 0
GLOBAL_SEARCH_RESTART_COUNT: 0
A2_RESULT: no result
WATCHDOG: none
TIMEOUT: none
```

No IDX3/IDX5/IDX4/IDX7/IDX8 evidence exists from this run.

## 6. Demonstrations and unknowns

**Demonstrated**

- Chunk loader PASS, 289/289 in one tick.
- Village/wall generation PASS.
- Population query saw 15/11 and omitted SHEPHERD, while NBT contains the live shepherd.
- No attacker spawned and no A2 result was produced.

**Unknown / blocked**

- Whether `traceGlobalSearch` reached the server JVM.
- Any first-expansion candidate detail, search lifecycle, or cancellation/restart reason.
- Root causes for IDX4/IDX7/IDX8.

## 7. Preservation

Logs:

```text
build/a2n/a2n-20261119-114535/srv.log
build/a2n/a2n-20261119-114535/launcher-status.txt
build/a2n/a2n-20261119-114535/fin.txt
build/a2n/a2n-20261119-114535/game/a2n/level.dat
build/a2n/a2n-20261119-114535/game/a2n/entities/r.1.1.mca
build/a2n/a2n-20261119-114535/game/a2n/entities/r.1.0.mca
```

```text
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

## 8. Next

1. Resolve the population query omission (fixture/query visibility), not worker spawn/profession.
2. Ensure the launcher passes **both** properties directly in `JDK_JAVA_OPTIONS` and verify them in
   the server JVM before another authorization.
3. Any new runtime run requires new explicit user authorization; the single diagnostic authorization
   was consumed.

A2 remains OPEN.
