# A2 Phase 54B — non-blocking fixture chunk load (2026-11-19)

## 1. Result

**IMPLEMENTED (harness only), static PASS, runtime NOT executed.**

The C4/A2 fixture no longer calls blocking `ServerLevel.setChunkForced` for its 289 chunks. It now:

1. requests FORCED tickets through `ServerChunkCache.updateChunkForced(ChunkPos, true)`;
2. polls `ServerLevel.hasChunkAt` once per tick;
3. waits for all 289 chunks with a finite timeout (default 600 ticks);
4. runs the existing virgin/preGenerate/center verification only after READY;
5. spawns attackers only after that verification succeeds;
6. releases tickets and closes `INVALIDO` on timeout/failure.

- Initial HEAD: `759d042feb332006d42c8d3835c3ca53266af633`
- Production SHA: `656ce7d0f6182fd15e0b1bbb0bd6684c180bd0e6` (unchanged)
- No A2/C4 runtime executed.
- Production navigation/entity sources unchanged.

## 2. API evidence (Minecraft 1.21.1 / NeoForge 21.1.249)

`javap` against `build/moddev/artifacts/neoforge-21.1.249.jar`:

```text
ServerLevel.setChunkForced(int,int,boolean)
  -> ForcedChunksSavedData.add
  -> if newly forced: this.getChunk(x,z)        // BLOCKING
  -> getChunkSource().updateChunkForced(...)
```

```text
ServerChunkCache.updateChunkForced(ChunkPos, boolean)   public
  -> DistanceManager.updateChunkForced(pos, add)
     -> new Ticket(TicketType.FORCED, ChunkMap.FORCED_TICKET_LEVEL, pos)
     -> addTicket + tickingTicketsTracker.addTicket
  // no getChunk call
```

Therefore the non-blocking primitive is `ServerChunkCache.updateChunkForced`, followed by polling
`ServerLevel.hasChunkAt`. Ticket removal uses the same method with `false`.

## 3. Implementation

File: `src/main/java/com/chipoodle/devilrpg/debug/A2NavHarness.java`

New state fields:

```text
rChunkTicketsRequested
rChunkLoadFailed
rChunkDeadline
rChunkLastLoaded
rChunkPositions (289 ChunkPos)
rChunkTimeoutTicks = -Da2n.fixtureChunkTimeoutTicks (default 600)
```

State machine in `alTick`:

```text
if !rPreparado:
    prepararCargaChunksRaid()
      false + !failed -> return (wait next tick)
      false + failed  -> INVALIDO close, no spawn
      true            -> rPreparado=true, prepararRaidFixture, verify, spawn
```

`prepararCargaChunksRaid`:

- first call: creates the 17×17 `ChunkPos` list and 289 `updateChunkForced(pos,true)` tickets;
- later ticks: counts `hasChunkAt` for all 289; logs WAIT on change/every20 ticks;
- all loaded: logs READY and returns true;
- deadline exceeded: releases all tickets, increments instrument failure, logs TIMEOUT, ensures no
  attacker spawn.

Existing virgin checks remain:

```text
level.hasChunkAt(center)
!VillageSavedData.isGenerated(0)
no Villager within AABB(center).inflate(100)
VillageManager.preGenerate(level,0,center)
saved.getCentre(0) != null
wall/population verification
```

The previous unconditional `C4 CHUNKS forced=289` log is replaced by `TICKETS/WAIT/READY` events.

## 4. Telemetry

```text
C4 CHUNKS TICKETS requested=289 ticketOnly=true timeoutTicks=600 center=...
C4 CHUNKS WAIT loaded=N/289 pending=M elapsed=E remaining=R
C4 CHUNKS READY loaded=289/289 center=... elapsed=E
C4 CHUNKS loaded=true center=... ticketOnly=true
C4 CHUNKS TIMEOUT loaded=N/289 pending=M timeoutTicks=... ticketsReleased=true
```

The timeout path closes the run with `RAID FIXTURE INVALIDO ... chunk load timeout/failure` and does not
call `prepararRaidAsaltantes`.

## 5. Static validation

```text
.\gradlew.bat compileJava -Pa2nDiagnostics=true: PASS
.\gradlew.bat test: PASS
python tools\lint_aldea.py --strict: PASS
git diff --check: PASS
```

Diff:

```text
A2NavHarness.java: +79/-10
entity/nav/* and AggressiveZombieEntity: no changes vs 759d042f
```

No blocking `setChunkForced`/`getChunk` remains in `prepararRaidFixture`; the remaining `forzar(...)`
uses in the harness belong to other diagnostic fixtures, not the raid preparation path.

## 6. Limitations

- This change is harness-only and has NOT been runtime-tested; a new explicit authorization is required
  for exactly one A2 Run1 on `656ce7d0`.
- `updateChunkForced` tickets are retained for the fixture lifetime; on timeout they are released. If
  the run ends normally, server shutdown clears them.
- The existing village generation/preGenerate remains synchronous after READY; it previously completed
  in seconds and is outside the 289-chunk cold-load problem.

## 7. Integrity

```text
PRODUCTION_MODIFIED: false (navigation/entity production untouched)
TESTS_MODIFIED: false
WORLDS_MODIFIED: false
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

A2 remains OPEN. No Run2/Full12K.
