# A2 next-defect forensics (2026-11-20)

## 1. Result

**COMPLETE — read-only.** A2 remains OPEN. The highest-confidence unfixed functional defect is the
ascent/BUILD gate (IDX7), which remains in the current production source and was directly observed in
the last runtime.

- BRANCH: `feature/autonomous-village`
- INITIAL_HEAD: `1b2e654205dfc91aa6e50248790e62436e960835`
- INVESTIGATED_HEAD: same
- PRODUCTION_MODIFIED: false
- RUNTIME_EXECUTED: false
- MINECRAFT_LAUNCHED: false
- WORLDS_MODIFIED: false

## 2. A2 acceptance criteria

From `docs/autonomous-village/01-navigation-architecture.md` (lines 6-7, 161-164):

1. **8/8 attackers strictly enter the village (`DENTRO-PARED`)** in each controlled run.
2. The result must repeat in **two consecutive identical controlled runs**.
3. After A2 passes, run the existing **hunting regression** checks.

Perimeter contact is not strict entry. Spawn success is not entry. The harness itself currently logs
`umbralesDeEntrada=NO DEFINIDOS en el repositorio` and does not auto-adjudicate; the canonical 8/8
criterion above remains the acceptance target.

## 3. Evidence status

| Criterion | Evidence | Status |
|---|---|---|
| 8/8 spawn | last run 151818, `SPAWN TOTAL intentos=8 ok=8` | PASS (historical) |
| Fixture/population | 16 villagers, 12 professionals, joins=16 | PASS (historical) |
| 8/8 strict entry | 3/8 (IDX1/2/6) | **FAIL** |
| Perimeter | 3/8 (IDX1/2/6) | secondary only |
| Two consecutive runs | only one run at this checkpoint | **NOT DEMONSTRATED** |
| Hunting regression | no current evidence | **NOT DEMONSTRATED** |
| C4 defense | 151818 C4_PASS 4/4 guards, 12/12 workers | PASS (historical; not revalidated) |
| No watchdog | 151818 no watchdog | PASS (historical) |

The last runtime `a2n-20261119-151818` used production before the P0 local-step fix and the traversal
lease/goal-removal changes. Therefore its 3/8 is **historical**, not a measurement of current HEAD.
Current HEAD is static PASS but runtime-unmeasured.

## 4. Last run failures

```text
IDX1 strict t1018, died t1751
IDX2 strict t747, died t1403
IDX3 FAILED 594,74,600; local step cap 64/64
IDX4 PLANNING 566,122,533; CADENCE_WAIT, 0/28 candidates
IDX5 FAILED 594,75,466; local step cap 64/64
IDX6 strict t728, died t2033
IDX7 FAILED 705,65,459; global route 79, no placement
IDX8 PLANNING 769,63,497; global exp=6737/8000, frontier763
```

No attacker was recorded as strict at phase B end; the three strict entrants had died. The deaths are
combat outcomes, not navigation failures.

## 5. Confirmed defects

### D1 — IDX7 ascent/BUILD gate (priority)

**FACT.** Current `AggressiveZombieEntity.descensoExigeConstruir` (line2414) computes:

```java
int desnivel = miY - objetivoY;
if (desnivel <= CAIDA_SEGURA_MAXIMA) {
    motivoDeNoConstruir = "objetivo ... desnivel " + desnivel + " <= caida segura ...";
    return false;
}
```

For mob Y=65 and target Y=68, `desnivel=-3`, so `-3 <= 3` returns false. `objetivoExigeConstruccion`
(line2325) then returns false, the stair builder is rejected before placement, and a route that needs a
3-block ascent cannot build.

Last-run evidence:

```text
GLOBAL_SEARCH_COMPLETE routeNodes=79 found=true
INC_STEP objective=703,68,457 (dy=+3 from 705,65,459)
decisionEscalera noConstruir=[desnivel -3 <= caida segura 3]
A2_FALLAR tick=5196 motivo={presupuesto agotado sin progreso (10/10)}
```

This is a signed-height/role confusion: the descent gate is applied to an ascent. It is present in
current HEAD; the P0 and lease changes did not address it.

Impact: a valid global BUILD route can never produce a placement for positive dy beyond jump height.
This directly prevents strict entry for that attacker and can affect any route with a >2 ascent.

Minimum test: pure `TerrainBuildNeedPolicyTest` with `mobY=65,targetY=68,maxDrop=3,maxJump=2` must
return `BUILD_REQUIRED` (not “safe descent”), and `mobY=68,targetY=65` must return `DESCEND/NO_BUILD`.
Integration: a route node BUILD at +3 must reach `placement.place(node.pos())` and verify the block.

### D2 — IDX8 global-search cancellation/convergence

**HYPOTHESIS (source-supported).** `GLOBAL_SEARCH_CANCEL` has only generic `owner_cancelled`; the exact
caller is not logged. One search reached 8000 without a goal and the final search was still pending.
Repeatedly cancelling the same goal at exp 72/79/80 loses work. This is a real observed behavior but the
specific caller/condition is not proven.

Minimum test: instrument cancel caller + reason (or a pure session lifetime policy test with
cancel-on-local-transient vs retain). Do not change budgets.

### D3 — Atomic custom 3×3 gate

**FACT (source).** `DoubleGateBlock` has nine cells; only the hinge has a block entity;
`playerWillDestroy` cascades for players, but the mob path destroys one cell at most. `breakBlockAt`
rejects block entities. The gate is not a coherent siege target. This is separate from A2's general
village entry and must not be mixed with the traversal lease fix.

Minimum test: gate assembly validation (9 cells + facing + hinge), one claim per gate, atomic no-drop
removal, all-absent verification, protection of other block entities.

### D4 — Terrain tools now depend entirely on route transitions

**HYPOTHESIS / regression risk.** With `BreakBlockGoal`/`TraverseGoal` unregistered, every terrain action
must come from active BREAK/BUILD/BRIDGE. This is the intended contract, but no runtime has validated
that the planner emits those transitions for every wall/breach scenario. The lease fix is statically
correct, not runtime-proven.

Minimum test: a route containing BREAK/BUILD/BRIDGE must execute exactly one action per tick; a route
with no such node must make no terrain change. A controlled runtime is required before claiming A2
progress.

## 6. Ruled out / not priority

- Population query blocker: fixed; last run shows 16/16 and 12 professionals.
- Chunk loader watchdog: fixed; last run 289/289 in one tick, no watchdog.
- IDX3/IDX5 repeated step increments: P0 policy implemented (static PASS), but **runtime-unmeasured**;
  not safe to call resolved.
- IDX4 CADENCE loop: P0 no-candidate retry fix implemented (static PASS), runtime-unmeasured.
- Lease bypass: fixed and static PASS; not the next defect.
- Combat deaths of IDX1/2/6: expected combat outcomes, not navigation defects.

## 7. Priority decision

**PRIORITY_NEXT_DEFECT: D1 — ascent/BUILD gate.**

Reasons: it is a confirmed source condition, it directly caused a concrete last-run failure (IDX7),
it is independent of unmeasured P0/lease behavior, and it blocks the 8/8 criterion. IDX8 is next but
requires better lifecycle evidence; the custom gate is a separate functional requirement.

## 8. Files / methods for ChatGPT

```text
src/main/java/com/chipoodle/devilrpg/entity/AggressiveZombieEntity.java
  descensoExigeConstruir (line2414)
  objetivoExigeConstruccion (line2325)
  construirEscalonResultado (line2358)
  construirEscalonDescendente (line1913)

src/main/java/com/chipoodle/devilrpg/entity/nav/NavExecutor.java
  BUILD case (line5267)
  activeActionTarget / tryAcquireTraversalAction (5950-5990)
  terrainChanged (5990+)
```

Recommended pure policy: signed vertical relation → `ASCEND_BUILD`, `DESCEND`, `FLAT`; ascent beyond
`NavClimbPolicy` jump limit requires BUILD, descent beyond safe drop requires BUILD only with no supported
landing.

## 9. Evidence artifacts

```text
build/a2n/a2n-20261119-151818/srv.log
  SHA256 B6B8106DB50BD14CEDDAEF59B14B4F3912F7FB3C4DA3443181C7E487E5999431
build/a2n/a2n-20261119-151818/nav-trace.log
  SHA256 8195D6E04D7B1B7ABFFDBE9EAD1ADA93067B458A87772738E0C8B7386A7C435C
```

No newer A2 runtime exists. The last run's launcher status is `C4_PASS`; C4 is historical only.

## 10. Integrity

```text
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

Preserved untracked: `.a2-backups/`, `src/foodTest/`, `tools/a2n/A2NavHarness.java.partial`,
`tools/a2n/consultar-c4.ps1`, `tools/a2n/lanzar-c4.ps1`, `tools/a5-closure/`.

A2 remains OPEN. C4 not revalidated.
