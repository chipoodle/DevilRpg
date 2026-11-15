# A2 — Headless Testing and Instrumentation Manual

Status: maintained operational manual. A2 remains **FAIL / OPEN**; this document does not
declare acceptance.

## A. Environment and prerequisites

- Windows 10/11, PowerShell 7 (`pwsh`) or Windows PowerShell 5.1.
- JDK 21 (`C:\jdk-21` in the current machine).
- Gradle wrapper 8.8 from the repository root.
- Minecraft 1.21.1, NeoForge 21.1.249.
- Branch `feature/autonomous-village`; current baseline HEAD `518fee1`.
- Do not use `run\saves\New World`; all disposable worlds live under `build\a2n\<runId>\game`.
- Required source: `tools/a2n/A2NavHarness.java` (temporary copy in the debug source during a run),
  `build/a2n/lanzar-a2n-run40.ps1` (launcher template), and `build/a2n/lanzar-a2n.ps1`.

## B. Test architecture

```text
lanzar-a2n-run40.ps1
  -> creates build/a2n/a2n-<timestamp>/{game,init.gradle,server.properties}
  -> copies the harness into src/main/java/.../debug/A2NavHarness.java
  -> starts gradlew runServer headlessly with:
       -Da2n.mode=raid -Da2n.faseA=3000 -Da2n.faseB=12000
       -Ddevilrpg.nav.custom=true -Ddevilrpg.nav.budget=900
       -Ddevilrpg.nav.globalBudget=8000
       -Ddevilrpg.a2n.traceNav=true -Dn4.trace=true
  -> harness builds the walled fixture, spawns 8 attackers and samples phase A/B
  -> writes fin.txt and stops the server
  -> launcher waits for fin.txt, verifies save lines, stops only the owned JVM
```

The server is headless. No client GUI, keyboard or mouse input is used. A2 and C4 are separate:
A2 measures attacker approach/entry; C4 measures guards, worker recovery and job-site integrity.

## C. Exact commands

```powershell
cd D:\Documents\DevilRpg-autonomous
git status --short
git rev-parse HEAD
.\gradlew.bat compileJava compileTestJava
python tools\lint_aldea.py --strict
git diff --check
```

Focused navigation tests:

```powershell
java -cp "build/classes/java/main;build/classes/java/test" com.chipoodle.devilrpg.entity.nav.NavTerrainRearmPolicyTest
java -cp "build/classes/java/main;build/classes/java/test" com.chipoodle.devilrpg.entity.nav.NavMissionChainPolicyTest
```

Full headless A2 (only one at a time):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build\a2n\lanzar-a2n-run40.ps1 `
  -Modo raid -Semilla 12345 -LimiteMinutos 90 -Pared 0 -FaseA 3000 -FaseB 12000 `
  -Rampa 0 -Perfil 0 -Inundacion 0 -NavCustom true -Presupuesto 900 -GlobalBudget 8000
```

Run directory: `build\a2n\a2n-<timestamp>`. Logs: `srv.log`, `nav-trace.log`, `fin.txt`,
`launcher-status.txt`, `run40-watch.log`.

Check for an active run:

```powershell
Get-CimInstance Win32_Process -Filter "Name like 'java%'" |
  Where-Object { $_.CommandLine -match 'devlaunch\.Main|serverRunProgramArgs' }
```

Stop only the JVM reported by that query (after checking its command line):

```powershell
Stop-Process -Id <pid> -Force
```

Read the result:

```powershell
Get-Content build\a2n\<runId>\fin.txt
Select-String -Path build\a2n\<runId>\srv.log -Pattern 'RAID SNAPSHOT|RAID NAV|VEREDICTO|FIN'
Select-String -Path build\a2n\<runId>\nav-trace.log -Pattern 'A2_STAIR_FALLBACK_(DECISION|RESULT)'
```

## D. Instrumentation catalog

| Event | Source | Activation | Fields | Meaning |
|---|---|---|---|---|
| `A2_STAIR_FALLBACK_DECISION` | `NavExecutor` selected-waypoint path | `-Ddevilrpg.a2n.traceNav=true` (NavTrace) | uuid, tick, pos, objetivo, mision, buildable, local attempts/failures, anchor/stair state | Fallback is about to attempt the local mission stair |
| `A2_STAIR_FALLBACK_RESULT` | `NavExecutor` after `construirEscalonHacia` | same | uuid, tick, result, full stair state | Actual construction result; `null` means no pending stair |
| `CONSTRUCCION-ESCALERA` | `AggressiveZombieEntity` | normal server log | attacker id, block, count, candidates, target | A verified stair block was placed |
| `A2 STAIR-ACCESS-CLEAR` | `AggressiveZombieEntity` | normal server log | attacker id, removed block | A stair access obstruction was removed, no stair pending |

Stair state fields: `colocados`, `ultimo` (pending placed stair), `ancla` (current stair base),
`motivo`, `rechazo`.

## E. Fixture and geometry

- Seed `12345`; village centre `661,71,533`; wall radius 62; 8 attackers at radius 95.
- Phase A until tick 3000; opening snapshot at phase B; latest requested close 12000.
- Disposable world path: `build/a2n/<runId>/game/a2n`.
- Wall is not weakened, attackers are not teleported, and accepted C4 recovery is separate.

## F. Result interpretation

- **Fallback not reached**: no `A2_STAIR_FALLBACK_DECISION` for that attacker; inspect selected
  waypoint (`RAID NAV ... local objetivo=`) and whether `buildable=false`.
- **Fallback reached but no construction**: `FALLBACK_RESULT result=null`; inspect `stair={...}`,
  `ancla`, `rechazo`, and whether the anchor is stale.
- **Stair placed but not traversed**: `colocados` increments while `ultimo=-`; verify physical
  occupation and the next `confirmarTravesia`.
- **Route/mission desynchronization**: local `objetivo` differs from global `mision`; the fallback
  must target the local objective that is below safe-drop, not only the global mission.
- **Budget exhaustion**: `exp=8000/8000`, `presupuesto=true`.
- **No convergence**: `RAID PROX` distance-to-centre trend and route replans over time.

Run40/45/46 evidence: idx3 remains `FAILED` at `607,95,601`, local `64/64`, `10/10`;
idx4 placed a first stair at `589,92,511` after the local-mission fallback fix; idx7/idx8 remain
far from the wall. C4 remained PASS in the focused run (`C4_PASS`, 12/12 workers, 0 casualties).

## G. Acceptance criteria

The current harness prints `VEREDICTO B: SIN UMBRAL CONTRATADO`; **no explicit A2 numeric
threshold is configured**. Do not invent one or declare PASS from `everEntered` alone. The
documented A2 handoff requires attackers to complete the approach, enter the walled enclosure and
remain inside at the phase-B closing snapshot, with survival/arrival criteria met and no
unbounded stalls. C4 is PASS only with the documented guard/worker/job-site evidence. Until the
threshold is explicitly agreed, A2 remains **FAIL/OPEN** regardless of partial entries.

## H. How another AI can continue this investigation

1. Read this manual and `A2-WALLED-RAID-HANDOFF.md`.
2. `git status --short`, `git rev-parse HEAD`; preserve all changes.
3. Inspect `build/a2n/lanzar-a2n-run40.ps1` before changing a run.
4. `.\gradlew.bat compileJava compileTestJava`.
5. Run focused nav tests.
6. Run a focused `-FaseB 6000` headless fixture when iterating; use full 12000 only for acceptance.
7. Read `A2_STAIR_FALLBACK_*` and `RAID NAV` for the failing UUID.
8. Identify the first divergent state; add opt-in trace if absent.
9. Add a policy regression test.
10. Fix the control flow/state, compile, rerun focused.
11. Run full A2 only when the focused failure is resolved.
12. Verify C4 in `fin.txt` and `srv.log`.
13. Append run IDs, findings and next action to the three handoff documents.

## I. Known limitations

- A2 acceptance threshold is currently ambiguous (`SIN UMBRAL CONTRATADO`); this is a contract
  gap, not a test PASS.
- Autonomous no-food sleep and Food 3.1 remain separate concerns; do not conflate them with A2.
- Full A2 takes ~12–22 minutes. Always run one at a time and stop only the owned JVM.
- No GUI evidence is used or required.

## 2026-11-14 00:30 — idx3 terminal state proven: rearmes=4/4, tryPlan never entered

- Focused run 2n-20261114-002306 (FaseB6000): A2 FAIL; t=3900 4 ever entered, 0 inside, 4 dead.
- A2_REARM_ENTER for idx3 shows state=FAILED rearmes=4/4, MAX_REARMES rejection every tick; no A2_TRYPLAN_ENTER for that UUID.
- Root cause is the exhausted terminal rearm budget, not the fallback gate. The access-clear reset did not leave a usable rearm after four failed fallback attempts.
- No speculative production fix applied. New dedicated report: A2-IDX3-TRYPLAN-STATE-INVESTIGATION.md.
- Instrumentation added: A2_TRYPLAN_ENTER, A2_REARM_ENTER, A2_REARM_REJECTED.
- Next: inspect the four rearm→tryPlan cycles after access clear and fix the exact bounded transition. A2 remains FAIL/OPEN; C4 PASS.

## 2026-11-14 01:25 — progress predicate added

- `NavLocalObjectiveProgressPolicy` rejects same-level/higher local candidates when the mission requires descent, unless a non-WALK route continuation or pending stair exists.
- Focused run `a2n-20261114-011440`: idx3 descended to 617,86,600 with local 4/64 and explicit rejection reason; idx4 still places 589,92,511.
- Policy test 6/6; compile/lint/diff pass; A2 remains FAIL/OPEN; C4 PASS preserved.

## 2026-11-14 01:45 — Full accepted-threshold campaign: A2 FAIL, C4 PASS

- Approved contract: A2-ACCEPTANCE-CRITERIA.md (8/8 registered; >=6/8 enter; >=1 alive inside at 12000; no surviving terminal FAILED/zero progress; C4 PASS).
- Full run 2n-20261114-013023 (FaseB12000): spawneados=8/8, entraron=5/8, vivos=3, muertos=5, dentroParedAhora=0. A2 FAIL on criteria 2, 3 and 4.
- Survivors: idx3 alive 617,86,600 (PLANNING, local 4/64), idx4 alive 588,77,507 (PLANNING, stair 589,92,511), idx8 alive 701,62,457 (terminal FAILED).
- C4 PASS: 16 survivors, 12/12 workers alive/resumed, 0 casualties, jobSite [10,0,0], 0 instrumentation failures.
- Campaign report: A2-CAMPAIGN-20261114.md.
- Next: inspect the five deaths and idx8's terminal FAILED first divergence; do not weaken wall/threshold. A2 is not PASS.

## 2026-11-14 02:05 — Death telemetry validated; idx8 upward BREAK blocker

- Telemetry implemented via NeoForge LivingDamageEvent.Post/LivingDeathEvent, registered on NeoForge.EVENT_BUS, opt-in through NavTrace; no behavior changes.
- Focused run 2n-20261114-015833: 16 damage events, 4 deaths. All observed deaths are guard minecraft:mob_attack: idx6 t1353, idx1 t1637, idx5 t2125, idx2 t2321.
- idx8:
eplans agotados (8/8) with BREAK waypoint 701,67,457 ~5 blocks above the mob; no current upward mobility, same waypoint survives every rearm. Navigation-only repair would require the unapproved mobility design or changed route semantics.
- A2 remains FAIL: entrants are killed by guards before the closing snapshot, and idx8 is stranded. Satisfying >=1 alive inside by weakening guards/changing survivability would violate campaign restrictions and needs an explicit design decision.
- Report: A2-DEATH-TELEMETRY-AND-IDX8-BREAK-REPLAN.md.
