# A2 Phase 34 — DROP_APPROACH supported-path fix (2026-11-18)

## 1. Result

**COMPLETE for the Phase 33 fix.** The executor now advances through a supported adjacent approach
cell before targeting the landing. Snap churn falls sharply and the direct landing corner-cut is
removed. A2 remains **OPEN** at 2/8 strict because adjacent DROPs now stall without descending and
other independent failures remain.

- Branch: `feature/autonomous-village`
- Initial HEAD: `452418db131a58159436d37dc0bd727454d74116`
- Production before: `ec6aa339cf4abd1691092591c1379792ffdc1575`
- Production after: `ee0c4fe445af4cda1272488b4b0ffe712a3372cf`
- Runtime run: `a2n-20261117-210328` (new disposable world)

## 2. Root cause (Phase 33)

`NavExecutor` used `Navigation.moveTo` directly to the landing center, bypassing supported intermediate
path cells. The mob cut the corner into an unsupported adjacent column, then Phase 29 snapped it back.

## 3. Implementation

New pure policy `NavDropApproachPolicy.choose(current, landing, safe)`:

- if current is horizontally adjacent (Chebyshev ≤1), current is the approach cell;
- otherwise choose a safe same-Y neighbor that strictly reduces horizontal distance;
- deterministic distance/lexicographic tie-break;
- null if no safe approach exists.

`NavExecutor` DROP branch now:

- selects the approach waypoint;
- if already adjacent: stops navigation and lets the normal DROP nudge/action proceed;
- otherwise commands `moveTo` to the **supported waypoint center**, not the landing;
- aborts safely (`OBSTRUCTED`) if no safe waypoint exists;
- adds telemetry:
  - `DROP_APPROACH_WAYPOINT_SELECTED`
  - `DROP_APPROACH_WAYPOINT_COMMAND`
  - `DROP_APPROACH_WAYPOINT_REACHED`
  - `DROP_APPROACH_PATH_ABORT`

No support/fit/collision/AABB/budget/speed/tolerance change.

## 4. Static validation

```text
gradlew test: PASS
NAV_DROP_APPROACH_POLICY_TEST: OK (10 assertions)
NAV_SNAP_POST_RESET_POLICY_TEST: OK (12 assertions)
NAV_SNAP_OWNERSHIP_POLICY_TEST: OK (12 assertions)
NAV_DROP_TAKEOFF_POLICY_TEST: OK (51 assertions)
NAV_SOURCE_CELL_POLICY_TEST: OK (14 assertions)
lint_aldea.py --strict: PASS
git diff --check: PASS
compileJava -Pa2nDiagnostics=true: PASS
```

## 5. Runtime

Run `a2n-20261117-210328`, A2 Run 1 only.

```text
A2: 2/8 strict, 2/8 perimeter
Strict: idx1 t593, idx6 t649
C4: PASS [1,0,0]
Run 2: not executed
Full 12K: not executed
```

### Churn comparison

| idx | resets before | resets after | waypoint commands | final |
|---|---:|---:|---:|---|
| 3 | 139 | 14 | 24 | `605,96,600` RECOVERING |
| 4 | 135 | 0 | 0 (always adjacent) | `566,122,533` RECOVERING |
| 5 | 141 | 4 | 118 | `615,83,465` RECOVERING |
| 8 | 136 | 0 | 0 (always adjacent) | `748,63,534` FAILED |

The common corner-cut is removed where intermediate waypoints are required (idx3/idx5). idx5 also
gained substantial displacement (spawn `594,87,466` → `615,83,465`).

### New blocker

For idx4/idx8 the approach is always already adjacent, so the executor correctly issues no direct
landing path. However, the adjacent DROP nudge does not produce a descent:

```text
idx4: final 566,122,533 RECOVERING
 lastAction=567,119,534:DROP-EN-CURSO (salida=567,122,534 dy=-3 natural)
 intentosDeDescenso=22 fallosDeDescenso=0
```

The mob remains on the supported edge and retries the DROP without physically dropping. This is a
separate landing/descent blocker, not the Phase 33 corner-cut.

## 6. IDX3/4/5/8 traces

```text
IDX3 6c6d8329-7649-41b1-823b-9e2298d31994:
  approach waypoints emitted, 14 resets, final 605,96,600 RECOVERING.
IDX4 094ff85e-d7cf-4361-bcfd-a41f65290042:
  alwaysAdjacent=true from 566,122,533 to landing567,119,534; no descent; 22 attempts.
IDX5 0691cdf2-7e70-4cd5-924b-528055f42fc0:
  118 waypoint commands, 4 resets, final615,83,465; net +21 blocks.
IDX8 11a18d3a-0d45-4980-a3b5-52a0b21f290e:
  alwaysAdjacent=true; no descent; final748,63,534 FAILED on BUILD budget.
```

No `DROP_APPROACH_PATH_ABORT` occurred.

## 7. Phase 29/31

```text
SNAP ownership exclusive: preserved.
Post-snap reset: preserved.
No contradictory movement while snap pending.
No Phase 29/31 regression observed.
```

The reduced reset counts demonstrate the approach fix; remaining resets are new physical/DROP
failures, not the old corner-cut loop.

## 8. Signature sentinel

Unchanged and not fixed in this phase: `lastRouteSignature=0` sentinel remains a separate robustness
observation; zero-signature collision did not occur.

## 9. Integrity

```text
PRODUCTION_MODIFIED_BY_HARNESS: true (authorized Phase 34 fix)
TESTS_MODIFIED: true (new policy test + build registration)
ORIGINAL_WORLDS_MODIFIED: false
GUI_OR_FOCUS_INTERVENTION: none
TEMPORARY_WORLD: a2n-20261117-210328 disposable world
```

```text
A2 SHA-256 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 SHA-256 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

## 10. Commits

```text
ee0c4fe4 fix(nav): approach drop landing through supported cell
```

Documentation commit follows.

## 11. Next action

Phase 35 should diagnose why an already-adjacent DROP does not trigger a physical descent
(idx4/idx8), without reopening Phase 29/31 or the supported-approach contract. Do not change budgets or
physical predicates. A2 remains OPEN; Run 2 and 12K remain prohibited.
