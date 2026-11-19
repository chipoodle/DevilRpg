# A2 Phase 54A — fixture chunk-forcing forensics (2026-11-18)

## 1. Result

**COMPLETE.** The Phase53 crash is a synchronous cold-world chunk-generation stall, not a permanent
deadlock.

`ServerLevel.setChunkForced(x,z,true)` adds a forced ticket **and then calls `getChunk(x,z)`**, which
blocks the server thread until the chunk is loaded/generated. `A2NavHarness.prepararRaidFixture` performs
289 such calls (17×17) inside `ServerTickEvent.Pre`, before its `C4 CHUNKS forced=289` log. The 60 s
watchdog fired while the main thread was waiting inside `ServerChunkCache.getChunk`; the server thread
continued afterwards and logged `C4 CHUNKS forced=289`, village generation, then exited.

- RUN_HEAD: `605426b2c846bc3deac0db670af234f48afa4c2b`
- PRODUCTION_SHA: `656ce7d0f6182fd15e0b1bbb0bd6684c180bd0e6`
- RUN_ID: `a2n-20261118-233308`
- No production/test/runtime execution in Phase54A.

## 2. Evidence

Timeline:

```text
23:39:11 DedicatedServer: Done (35.066s), level ready
23:40:11 watchdog: single tick took 60.04 s
23:40:14 watchdog crash report
23:40:19 Server thread: [A2N] C4 CHUNKS forced=289 center=661,71,533 loaded=true
23:40:22 Village Aldea: cleared 24358 blocks
23:40:24 Village Aldea: corner peaks removed, floor gaps filled
```

Stack:

```text
ServerLevel.setChunkForced (ServerLevel.java:1402)
Level.getChunk
ServerChunkCache.getChunk
BlockableEventLoop.managedBlock/waitForTasks
LockSupport.parkNanos
```

The post-watchdog `C4 CHUNKS forced=289` proves the loop eventually completed; the tick was killed first
by the watchdog. The stack is the synchronous wait point, not evidence of a circular lock.

## 3. Semantics / cost

For each of 289 chunks:

```text
setChunkForced -> add FORCED ticket -> getChunk(x,z) synchronously
```

With a cold disposable world nothing is cached; chunk generation includes terrain, structures and
feature placement. Measured cold generation here exceeded 60 s for the batch. The 17×17 loop is
therefore not allowed inside one `ServerTickEvent.Pre`.

`A2FoundationBootstrap` has the same pattern (ring chunks, `setChunkForced` + `getChunk`) but was not
enabled in this run (`no A2FOUND` log).

## 4. Alternatives

### A. Pre-generated disposable world (recommended first)

Prepare a dedicated disposable world offline (all 17×17 chunks around `661,71,533` generated and saved),
copy it under `build/a2n/` as a template, and start the fixture from that copy. Benefits:

- chunk data exists on disk; `getChunk` loads rather than generates;
- no giant feature-generation cost in the first tick;
- original A2/C4 worlds remain untouched.

Risks:

- the template must remain virgin for objective0 (`VillageSavedData.isGenerated=false`, no villagers);
- chunk tickets/poi data must not pre-generate the custom village;
- copying/starting must not overwrite the template;
- still verify `hasChunkAt` and all fixture predicates before spawning.

### B. Incremental preparation

Move preparation out of one `ServerTickEvent.Pre` into explicit states:

```text
PREPARE_TICKETS -> WAIT_CHUNKS -> VERIFY_VIRGIN -> preGenerate -> VERIFY_CENTER -> SPAWN
```

Do **not** simply spread `setChunkForced` calls: each call still blocks until its chunk is generated.
Use a non-blocking ticket/poll design or a pre-generated world. Add a global timeout (e.g. 600 ticks)
and fail explicitly without spawning.

### C. Async API

Use the 1.21.1 chunk ticket/API asynchronously (add ticket, poll `hasChunk`/chunk future each tick);
never call blocking `getChunk` on the server thread. Only use APIs present in the exact mappings.

## 5. Reusable world?

Available local worlds:

```text
run/saves/DevilRpg-A2-IDX8-Diagnostico
run/saves/DevilRpg-A2-Prueba
run/saves/DevilRpg-C4-WorkerResume-Diagnostico
```

These are protected diagnostic worlds and must not be reused as disposable fixtures. No existing
pre-generated disposable template was found. A new template would have to be created outside the
protected worlds.

## 6. Minimum harness change (not implemented)

1. Prepare the world so the 289 chunks are already generated, or
2. Add a non-blocking ticket phase and a state machine that waits for `hasChunkAt`,
3. Spawn attackers only after `C4 CHUNKS`/`preGenerate`/virgin checks all pass,
4. Global timeout with `FIXTURE_INVALID` and no spawn on timeout,
5. Keep both trace properties and all existing fixture checks.

No navigation, budget, predicate, Phase41/44/46 or production change is required for the fixture fix.

## 7. Integrity

```text
PRODUCTION_MODIFIED: false
TESTS_MODIFIED: false
WORLDS_MODIFIED: false
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

## 8. Recommendation

Use a pre-generated disposable world plus a non-blocking chunk-wait phase; verify the fixture is
complete before spawning. Then re-authorize one A2 Run1 on `656ce7d0` to validate CADENCE_WAIT route
preservation and IDX5. Do not retry the current sync-forcing fixture unchanged.
