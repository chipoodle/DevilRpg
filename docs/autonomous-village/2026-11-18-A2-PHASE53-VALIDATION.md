# A2 Phase 53 validation — CADENCE_WAIT route preservation (2026-11-18)

## 1. Result

**BLOCKED at runtime.** Static validation PASSed on exact production `656ce7d0`, but the single
authorized A2 Run1 crashed during `A2NavHarness.prepararRaidFixture` while forcing chunks
(`setChunkForced` waiting on chunk generation). No attacker spawned, no `MOVE_COMMAND`, no stair event
and no IDX5 evidence could be produced. Per the one-Run1 limit, no retry was made.

- BRANCH: `feature/autonomous-village`
- LOCAL/REMOTE HEAD: `605426b2c846bc3deac0db670af234f48afa4c2b`
- TESTED PRODUCTION SHA: `656ce7d0f6182fd15e0b1bbb0bd6684c180bd0e6`
- RUN_ID: `a2n-20261118-233308`
- No production/test change.

## 2. Sync / static

```text
Initial local: def43b16
Remote: 605426b2 (6 commits ahead)
Action: safe fast-forward only
Untracked preserved
Production diff 656ce7d0..HEAD (src/build.gradle): empty
```

```text
.\gradlew.bat test: PASS
NAV_STAIR_ANCHOR_POLICY_TEST: OK (8 assertions)
STAIR_BUILD_RESULT_TEST: OK (6 assertions)
NAV_DROP_APPROACH 10, OWNERSHIP 13, EDGE_STEP 16: PASS
lint_aldea.py --strict: PASS
git diff --check: PASS
compileJava -Pa2nDiagnostics=true: PASS
```

## 3. Runtime failure

```text
RUN: a2n-20261118-233308
launcher: GRADLE_EXITED_BEFORE_FIN
server log: single server tick took 60.04 seconds
```

Stack:

```text
ServerHangWatchdog
LockSupport.parkNanos
BlockableEventLoop.waitForTasks/managedBlock
ServerChunkCache.getChunk
Level.getChunk
ServerLevel.setChunkForced
A2NavHarness.prepararRaidFixture (A2NavHarness.java:3074)
A2NavHarness.alTick (:137)
```

This is a chunk-generation/world-load deadlock during A2 fixture preparation, before any attacker
tick. It is not evidence about CADENCE_WAIT or IDX5.

Artifacts:

```text
build/a2n/a2n-20261118-233308/srv.log
build/a2n/a2n-20261118-233308/launcher-status.txt
build/a2n/a2n-20261118-233308/game/crash-reports/crash-2026-11-18_23.40.14-server.txt
```

## 4. Required IDX5 checks

```text
A2_STAIR_CADENCE_ROUTE_PRESERVED: NOT OBSERVED (run never reached attackers)
source=validated_global_route/direct_mission: NOT OBSERVED
waypoint preserved: NOT OBSERVED
MOVE_COMMAND after cadence: NOT OBSERVED
IDX5 UUID 426df5d6...: NOT PRESENT
local counters: NOT OBSERVED
STAIR_PLACED due to wait: NOT OBSERVED (none)
```

```text
IDX5_RUNTIME_VALIDATION: INSUFFICIENT_EVIDENCE
```

## 5. IDX3 / anchor

No attacker spawned, therefore:

```text
constructor calls: 0
placements: 0
traversals: 0
anchor refreshes: 0
MONOTONIC_ANCHOR_RUNTIME: INSUFFICIENT_EVIDENCE
```

## 6. A2 / C4

```text
A2: BLOCKED — no valid run (no fin.txt)
C4: not reached in this run
```

No strict/perimeter count can be claimed.

## 7. Integrity

```text
PRODUCTION_MODIFIED: false
TESTS_MODIFIED: false
WORLDS_MODIFIED: false
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

## 8. Recommendation

Do not count this as a Phase53 behavioral result. Stabilize the A2 fixture chunk forcing (or use a
pre-generated disposable world) and re-authorize exactly one A2 Run1 on `656ce7d0` to test the
CADENCE_WAIT route-preservation event and IDX5. Harness must not modify production or rerun without
authorization.
