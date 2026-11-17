# A2 Run 1 — exact bounded recovery A* Phase 22 (2026-11-17)

## Result

**FAIL.** Exact bounded recovery no longer overruns 900 and preserves the original route, but it
cannot reach the take-off. IDX4 receives 39 rejections with exact-goal searches that exhaust the
reachable frontier; A2 Run 1 remains 5/8 strict.

- Tested SHA: `c17459b7817fb413a859fbfc3e8a096452f5f650`
- Run: `a2n-20261117-113750`
- Seed 12345, FaseA 3000, FaseB 6000, budgets 900/8000.

## Static validation

| Command | Exit | Result | Log |
|---|---:|---|---|
| `gradlew test` | 0 | PASS | `build/phase22-exact-gradle.log` |
| `lint_aldea.py --strict` | 0 | PASS | `build/phase22-exact-lint.log` |
| `git diff --check` | 0 | PASS | terminal |
| `compileJava -Pa2nDiagnostics=true` | 0 | PASS | `build/phase22-exact-diagcompile.log` |

```text
NAV_SOURCE_CELL_POLICY_TEST: OK (10 assertions)
NAV_DROP_TAKEOFF_POLICY_TEST: OK (43 assertions)
NAV_ROUTE_NODE_POLICY_TEST: OK (6 assertions)
NAV_RECOVERY_CONTROLLER_TEST: OK (10 assertions)
NAV_RECOVERY_GUARD_POLICY_TEST: OK (5 aserciones)
```

## A2 Run 1

- 8/8 spawned.
- **5/8 perimeter.**
- **5/8 strict `DENTRO-PARED`.**
- C4 **PASS** `[1,0,0]`, 12/12 resumed.
- Run 2 not executed.

| idx | UUID | Perimeter | Strict |
|---|---|---|---:|
| 1 | `dfe576cf-48da-46c7-a01b-8fcac98bdd60` | t387 | **t513** |
| 2 | `9047e1b5-fa02-43a3-a9c1-2a3fb41dd5e2` | t242 | **t668** |
| 3 | `4bd2bc10-13ee-4ad1-b896-d2385c0ba7a7` | t1773 | **t1875** |
| 4 | `af70fc14-046d-4864-9cd6-86110ec99f01` | — | — |
| 5 | `00092b40-b31c-4066-a051-3d3ef02a4c8a` | t816 | **t1044** |
| 6 | `6af6ae30-a4a3-4c5d-ba91-2fc1439ea97c` | t224 | **t650** |
| 7 | `b19063f9-9d3b-45b6-b9ec-6e616e7af191` | — | — |
| 8 | `e97efe6b-6ae6-43b9-be38-e659c5ee486f` | — | — |

## IDX4 — exact A* evidence

UUID `af70fc14-046d-4864-9cd6-86110ec99f01`.

This run's geometry did not require source snapping: no `SOURCE_CELL_CANDIDATE` or
`SOURCE_SNAP_REACHED` for this UUID. The mob was already on a valid source.

### planExact result

39 `DROP_TAKEOFF_RECOVERY_PLAN_REJECTED` events. Representative final:

```text
from=589,93,522 takeoff=589,96,522
exactGoal=true radius=4 verticalRange=3
hitBudget=false reached=false
route=len=1 fin=589,95,521 ultimos=[JUMP_UP@589,95,521]
summary={global exactGoal=true inicio=589,93,522 meta=589,96,522
 radio=4 V=3 exp=14/900 presupuesto=false aristas=763 encolados=138
 frente=589,95,521 dFrente3D=0.0 dFrenteXZ=1.0 dFrenteY=0
 motivo=el frente alcanzable se agoto dentro del radio 4 rutaFrente=1}
```

Another rejection:

```text
from=585,105,516 takeoff=585,106,516
exactGoal=true hitBudget=false reached=false route=vacia
exp=56/900 aristas=2870 encolados=915
motivo=el frente alcanzable se agoto dentro del radio 4
```

So the search is bounded and does not consume 900, but it cannot produce a route that reaches the
exact take-off. The best found route ends one Y block short (`589,95,521` vs `589,96,522`).

### Restoration / take-off

```text
DROP_TAKEOFF_RECOVERY_ROUTE_ACCEPTED: 0
DROP_TAKEOFF_RECOVERY_ROUTE_RESTORED: 0
DROP_TAKEOFF_RECOVERY_REACHED:          0
```

No route was accepted for IDX4, so no signature/cursor/route-length restoration event exists.

### Original DROP

`DROP_START` for the active route is:

```text
idxRuta=0 aterrizaje=589,95,521 salida=589,96,521 dy=-1 construido=false
```

Final position `589,93,522` does not match that active landing. The latest `DROP_DONE` for the UUID
is `idxRuta=0 aterrizaje=589,93,522`, from an earlier route cycle.

```text
Current active DROP landing: 589,95,521
Latest DROP_DONE:             589,93,522
Final position:               589,93,522
```

Therefore the current original DROP is not verified complete; the cursor evidence is from a prior
cycle. No `DROP_TAKEOFF_RECOVERY_ROUTE_RESTORED` exists.

Final:

```text
RAID RESULTADO idx=4 finalPos=589,93,522
A2_FALLAR_ENTER tick=3159
motivo={construccion agotada sin completar el descenso}
NAV_FAIL pos=587,81,533 rearmes=0/4
```

## Required Phase 22 fields

```text
IDX4_UUID: af70fc14-046d-4864-9cd6-86110ec99f01

planExact:
  reached=false
  hitBudget=false
  expansions=14/900 (also 56/900)
  route=len=1 JUMP_UP@589,95,521 (or empty)
  fin=589,95,521 vs takeoff589,96,522

Take-off físico alcanzado: NO
DROP_TAKEOFF_RECOVERY_ROUTE_RESTORED: 0 events
  signature: N/A
  cursor: N/A
  route length: N/A
  plannedFrom: N/A

DROP_START: idx0 landing589,95,521 from589,96,521
DROP_DONE for current landing: NONE
Latest DROP_DONE: idx0 landing589,93,522 (prior cycle)
Cursor advance after DROP_DONE: only prior-cycle evidence
Original route preserved during rejected searches: YES (no restoration event; route still suspended)
```

## IDX7 / IDX8

```text
IDX7 b19063f9-9d3b-45b6-b9ec-6e616e7af191:
 pos=705,65,459; BUILD 703,68,457; local1/64; global2526/8000
 NAV_FAIL {presupuesto agotado sin progreso (10/10)}

IDX8 e97efe6b-6ae6-43b9-be38-e659c5ee486f:
 pos=769,63,497; local invalid; global8000/8000
 NAV_FAIL {sin objetivo local valido (10/10)}
 rearmes=1/4 observed at that point
```

## C4

- PASS `[1,0,0]`.
- 12/12 resumed; no casualties/unresolved.
- Save/reload OPEN.

## Gate

- Static gate: PASS.
- A2 Run 1: FAIL 5/8 strict.
- Run 2: not executed.
- Full 12k: not executed.
- A2 remains OPEN.

## Artifacts

- `build/a2n/a2n-20261117-113750/srv.log`
- `build/a2n/a2n-20261117-113750/nav-trace.log`
- `build/a2n/a2n-20261117-113750/fin.txt`
- `build/a2n/a2n-20261117-113750/estado.txt`
- `build/phase22-exact-gradle.log`
- `build/phase22-exact-lint.log`
- `build/phase22-exact-diagcompile.log`

Original worlds untouched; local-only artifacts preserved.

## Next action

ChatGPT must make the exact bounded A* reach the exact take-off from `589,93,522` to
`589,96,522`. The current reachable frontier stops at `589,95,521` (`JUMP_UP`), one Y block short;
`hitBudget=false`, so this is a search/transition limitation, not budget exhaustion. Do not increase
the budget. Preserve the original route during rejected searches (already observed).

IDX7/IDX8 remain independent; C4 PASS in this run.
