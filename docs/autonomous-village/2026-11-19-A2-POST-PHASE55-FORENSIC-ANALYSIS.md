# A2 post-Phase55 forensic analysis (2026-11-19)

## 1. Executive summary

Read-only analysis of run `a2n-20261119-151818` against production blobs at HEAD
`662502f2`. Five distinct failure mechanisms were identified; IDX3 and IDX5 share the same root cause.

| IDX | Confirmed mechanism | Class |
|---:|---|---|
| 3 | `escalonesDeObjetivo` increments 381× for the same invalid objective `595,74,599` | ROOT_CAUSE_CONFIRMED |
| 4 | no-candidate retry resets 10-tick `escaleraRetraso`; cooldown calls become CADENCE_WAIT forever | ROOT_CAUSE_CONFIRMED |
| 5 | same as IDX3 with objective `595,75,467` | ROOT_CAUSE_CONFIRMED |
| 7 | global BUILD target is 3 blocks **above** the mob; `descensoExigeConstruir` only considers descents (`miY - objetivoY <= 3` → no build) | ROOT_CAUSE_CONFIRMED |
| 8 | caller cancels/restarts global searches repeatedly; one search hits 8000, final search still pending at run end | ROOT_CAUSE_PROBABLE |

No Minecraft was launched. No production code was modified.

- RUN_ID: `a2n-20261119-151818`
- INITIAL_HEAD: `662502f2826749b148f0565034399e23e2ed3df6`
- Production SHA: `10719ff82465a0d8997cb2af3af23c9f71292b19`
- Canonical harness blob: `914d26da4f9a553f43936812ddc303ed56a24243`
- NavPlanner blob: `5666e42693fe2e90dff1d2ac1004b59b9b62d7cd`

## 2. Evidence files

```text
build/a2n/a2n-20261119-151818/srv.log
build/a2n/a2n-20261119-151818/nav-trace.log
build/a2n/a2n-20261119-151818/launcher-status.txt
build/a2n/a2n-20261119-151818/fin.txt
```

Raw logs were read only; no file was rewritten.

## 3. IDX3 — step-cap root cause

UUID `ec679c05-1a0a-49a9-a0c9-bbe317d7c2aa`.

`A2_LOCAL_STAIR_WRITE op=INC_STEP` count: **384**, but only **2 distinct objectives**:

```text
(595,76,599)  x3   ticks 1284,1325,1340
(595,74,599)  x381 ticks 1345..2140
```

At tick 2142:

```text
op=SET_FALLOS_MAX new=10 reason=LOCAL_LIMIT_AT_ENTRY
```

Final:

```text
estado=FAILED escalones=64/64 fallosEscalon=10/10
"tope de escalones locales alcanzado (64) hacia 661,72,533"
```

Mechanism (code `NavExecutor.escalonarObjetivoLocal`):

1. `siguiente = elegirObjetivoLocal()` returns `595,74,599` each tick.
2. `fallosDeEscalonDeObjetivo = 0`.
3. Idempotence guard is false because the objective is neither `esPieValido`, nor
   `objetivoPermiteConstruccion`, nor `esAccionLocalValida`. The objective is a DROP-like cell that
   `NavLocalObjectiveProgressPolicy.aceptar` accepts once but never becomes a usable foot cell.
4. `escalonesDeObjetivo++` runs every tick.
5. The same candidate is re-selected; the counter reaches 64 without 64 distinct steps or blocks.
6. At the cap, `fallosDeEscalonDeObjetivo` is forced to 10 and the executor fails.

`escalonesDeObjetivo` therefore does **not** represent placed stair blocks; it counts accepted local
objective transitions. Repeated acceptance of the same unusable objective is the defect.

## 4. IDX5 — same root cause

UUID `b0d40cf7-184e-4549-8aee-370f4ddf2ee2`.

```text
384 INC_STEP events, 2 distinct objectives:
(595,77,467) x3
(595,75,467) x381
last increment tick 2178; SET_FALLOS_MAX tick 2180
```

Final `FAILED`, `escalones=64/64`, `fallosEscalon=10/10`. This is the same counter/idempotence defect as
IDX3, with a different invalid objective. Not a separate root cause.

## 5. IDX4 — CADENCE_WAIT loop

UUID `9f5d8cb2-7e9b-4d17-8bc6-a5f13cb6faf9`.

```text
A2_LOCAL_STAIR_WAIT: 3143 events, ticks 1..5999
local=0/64 counters_unchanged=true
final escalones=0/64, PLANNING at 566,122,533
final stair rejection: "ningun candidato valido de 28"
```

Mechanism:

1. `AggressiveZombieEntity.construirEscalonDescendente` searches candidates; if none is valid it sets
   `escaleraRetraso = ESCALERA_ENTRE_BLOQUES` (10) and returns false.
2. On subsequent calls, `escaleraRetraso > 0` decrements by one and returns false.
3. `construirEscalonResultado` maps that decrement to `CADENCE_WAIT`.
4. `NavExecutor` logs `A2_LOCAL_STAIR_WAIT` and returns null; the local plan retries; the same 28 candidates
   fail again, resetting the delay to 10.
5. This repeats forever. The local stair counters remain 0 because no candidate is ever placed; this is
   **not** the 64-step cap.

The 28 candidates fail on `celdaAire`, `hueco1/hueco2`, `soporte=false`, or blocked landing entry.
The `CADENCE_WAIT` is real, but it is a symptom of the retry policy, not a legitimate temporary wait.

## 6. IDX7 — ascent ignored by build gate

UUID `b9bfa8ab-4674-40f0-8923-2eb0c05bf57d`.

Global result:

```text
GLOBAL_SEARCH_COMPLETE start=707,63,459 goal=661,72,533
exp=2531 frontier=767 found=true reason=encontrado routeNodes=79
```

Local target selected at tick 2204:

```text
INC_STEP new=1 objective=703,68,457
```

The mob was at `705,65,459`: target is `dy=+3`. The final diagnostic says:

```text
decisionEscalera construir=[-]
noConstruir=[objetivo 703,68,457 a desnivel -3 <= caida segura 3 (miY=65)]
```

`descensoExigeConstruir` computes `desnivel = miY - objetivoY = -3`; `-3 <= 3` returns false, so
`objetivoExigeConstruccion` is false and `construirEscalonResultado` returns `REJECTED`. The builder is
never allowed to place a stair for a 3-block **rise**. The local planner then hits its budget and fails:

```text
A2_FALLAR_ENTER tick=5196 local=1/64
motivo={presupuesto agotado sin progreso (10/10) hacia 703,68,457}
```

Root: the build gate models only descents; it has no equivalent for an ascent greater than the jump
limit. This is a confirmed global-to-local transition defect.

## 7. IDX8 — global search lifecycle

UUID `4c785a3c-7ccc-49ea-812a-02b01171fe28`.

Lifecycle from `nav-trace.log`:

```text
START goal=661,73 -> CANCEL exp=4
START goal=725,71 -> CANCEL exp=72,79,80,80,4534
START goal=661,72 -> CANCEL exp=1831,80,80,79
START goal=661,72 -> COMPLETE exp=8000 found=false hitBudget=true frontier=851 routeNodes=102
RESTART 768,63,498 -> 768,63,497 oldExp=20
RESTART 768,63,497 -> 769,63,497 oldExp=12
START 769,63,497 ... run ends pending exp=6737 frontier=763
```

The search is repeatedly cancelled by its owner before it can converge; one search does reach the hard
8000 budget with `found=false`. The final search starts at a new position and cannot finish before tick
6000. The telemetry reason is generic `owner_cancelled`; the exact caller is not logged. Goal changes
explain some cancellations; repeated cancellations of the same goal at exp72/79/80 are not explained by
goal change and are **INSUFFICIENT_EVIDENCE** at caller level.

This is not a frontier-explosion-only defect: the search is also losing work through cancellation.

## 8. Five restarts

| UUID | IDX | oldStart → newStart | oldGoal/newGoal | oldExp |
|---|---:|---|---|---:|
| `c6c6f9f1…` | 2 | 660,71,597 → 659,71,596 | 660,72,533 (same) | 40 |
| `cefeb16f…` | 6 | 660,71,469 → 659,71,470 | 660,72,533 (same) | 40 |
| `ec679c05…` | 3 | 594,75,600 → 594,74,600 | 661,72,533 (same) | 8 |
| `4c785a3c…` | 8 | 768,63,498 → 768,63,497 | 661,72,533 (same) | 20 |
| `4c785a3c…` | 8 | 768,63,497 → 769,63,497 | 661,72,533 (same) | 12 |

All restarts are caused by a one-block start-position change with an unchanged goal.
`NavExecutor.rutaGlobalHacia` sets `continuing` only when `globalSearchStart.equals(yo)`; any movement
invalidates the retained session and restarts it. This loses 8-40 expansions per restart and affects
IDX3 and IDX8; IDX2/6 also lose work but still reached strict entry before dying.

Tick is not part of `GLOBAL_SEARCH_RESTART` telemetry, so exact restart ticks cannot be reported.

## 9. Entity lifecycle

Phase B end at tick 6000:

```text
vivos=5 muertos=3 desaparecidos=0
RAID RESULTADO idx=3,4,5,7,8 vivo=true
C4 POSTRAID maintenance=OK attackersRemovedForRecovery=5
```

Harness code (`A2NavHarness` around line183) then executes:

```java
for (AggressiveZombieEntity atacante : rAsaltantes) {
    if (atacante != null && !atacante.isRemoved()) {
        atacante.discard();
    }
}
```

At tick 6300 the five are counted as disappeared. They were **not** killed by combat; they were removed
administratively for recovery. The three strict entrants (IDX1/2/6) were already dead at ticks 1751,
1403 and 2033.

## 10. Wrapper failure

The wrapper’s parameter block is correct:

```powershell
param([Parameter(ValueFromRemainingArguments=$true)][string[]]$LauncherArgs)
```

But it invokes the local launcher with array splatting:

```powershell
& $launcher @LauncherArgs
```

Array splatting passes elements positionally, not as named parameter tokens. Reproduced offline with the
same parameter shape: `-Modo raid -Semilla 12345 -LimiteMinutos 60` becomes positional
`Semilla=-Modo`, `Modo=raid`, `LimiteMinutos=-Semilla`, causing the exact error.

The direct invocation worked because PowerShell parsed the named parameters itself. Fix: convert the
`-Name value` pairs to a hashtable and splat that, or call the launcher with explicit named arguments.
This is independent of navigation.

## 11. Confirmed / probable / insufficient

**ROOT_CAUSE_CONFIRMED**

- IDX3/IDX5: repeated `INC_STEP` for the same unusable local objective; 381 repeats each.
- IDX4: no-candidate retry resets 10-tick delay and produces an endless CADENCE_WAIT loop.
- IDX7: ascent (`dy=+3`) rejected by a descent-only build gate.
- Cleanup: five live attackers administratively discarded at Phase B end.
- Wrapper: array splatting mis-binds named arguments.

**ROOT_CAUSE_PROBABLE**

- IDX8: repeated caller cancellation plus one 8000-budget exhaustion prevents convergence; exact
  cancellation callers are not logged.
- Restarts: exact start-equality invalidates useful sessions on one-block movement.

**INSUFFICIENT_EVIDENCE**

- Exact caller/reason for each `GLOBAL_SEARCH_CANCEL` (generic `owner_cancelled`).
- Exact tick of each restart (not logged).
- Whether a different ascent policy alone would let IDX7 enter; local plan budget also exhausted.

## 12. Recommended fixes (priority)

**P0**

- IDX3/5: do not increment `escalonesDeObjetivo` when the selected objective is unchanged and still not
  usable after a failed progress check. Either mark the attempt failed (bounded) or hold the counter.
- IDX4: do not reset `escaleraRetraso` to 10 after “no candidate” without incrementing a bounded attempt
  counter; make no-candidate return terminal/backoff, not CADENCE_WAIT.

**P1**

- IDX7: extend the build gate to ascents greater than jump limit, or reject BUILD waypoints that the
  local target policy cannot execute.
- IDX8: log cancellation caller/tick, then avoid cancelling a useful search on local transient failures;
  consider a restart policy based on distance threshold rather than exact position equality.

**P2**

- Restarts: reuse/retarget session when the start moves one block, or budget the lost work.
- Wrapper: replace array splatting with hashtable splatting.
- Add telemetry: cancel reason/tick, restart tick, step-counter operation with objective identity.

## 13. Evidence thresholds for closure

- IDX3/5 resolved when a run shows no repeated `INC_STEP` for the same objective and local counter changes
  only on distinct usable objectives.
- IDX4 resolved when no-candidate attempts terminate/backoff with a bounded non-CADENCE outcome.
- IDX7 resolved when a `dy>2` global waypoint produces a placement attempt or an explicit rejection that
  is not “safe descent”.
- IDX8 resolved when a search reaches a completed route or terminates hitBudget with a logged caller and
  no repeated same-goal cancellation at low expansion counts.
- Wrapper resolved when the tracked wrapper launches with `-LimiteMinutos` and logs `JVM_TRACE=true,true`.

## 14. Likely production files

```text
src/main/java/com/chipoodle/devilrpg/entity/nav/NavExecutor.java
src/main/java/com/chipoodle/devilrpg/entity/AggressiveZombieEntity.java
src/main/java/com/chipoodle/devilrpg/entity/nav/NavPlanner.java (telemetry/cancel reasons only)
tools/a2n/lanzar-phase55-diagnostico.ps1
```

No production file was modified by this analysis.

## 15. Integrity

```text
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

A2 remains OPEN.
