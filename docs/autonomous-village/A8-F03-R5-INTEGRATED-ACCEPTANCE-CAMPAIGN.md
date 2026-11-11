# A8-F03-R5 — Integrated TALAR Acceptance Campaign

Overall status: **PARTIAL / BLOCKED**. This report consolidates executed evidence; it does
not claim scenarios that were not run. No production changes were made.

## 1. Repository baseline

```text
branch feature/autonomous-village
HEAD 97220be5453b57387002b9bc91935807cc665274
staged 0
unstaged 26
untracked 165
```

Protected saves: not accessed. NBT: unchanged. A5/A6 worlds: not modified. No prohibited Git
operation, commit or push.

## 2. Architecture and dependency map

- `TalarStepPlanner`: cache, standing cell, wall gate, invalidation.
- `VillagerAgent`: TALAR target selection, `nextTalarStep`, lease, gateway command.
- `VillagerLumberjackGoal`: target discovery, OFF planner call, ENFORCE agent call,
  forestry operations, ENTREGAR legacy.
- `ForestryNavigationGateway` / `NavigationLease`: command authorization and ownership.
- `VillageManager`: standing-cell/wall/gate and navigation helpers.

## 3. Fixture preflight

Known working ENFORCE worker: `b89fcee1-c91a-4b1f-a9c4-66471b616288` in disposable A5WALL
copies. Run 218 proved natural harvest/delivery. R3 custom worker never discovered its
target; that is a fixture-orchestration failure, not a planner defect.

Gate fixture: not built. No outside-wall target/gate geometry was validated.

## 4. Known-worker validation

| Check | Result |
|---|---|
| b89 exists in disposable world | PASS (Runs 209–218) |
| ENFORCE agent/lease | PASS (Run 218) |
| TALAR target selection | PASS (Run 218) |
| Planner step | PASS (Run 218: log 87,4 → step 86,4) |
| Harvest/delivery | PASS (Run 218) |
| Custom R3 worker target discovery | BLOCKED (fixture) |

## 5. Scenario matrix

### Group A — Target lifecycle

| ID | Status | Evidence |
|---|---|---|
| A01 initial valid target | PASS | Run 218 |
| A02 unchanged cache reuse | PASS (source/Run 218 repeated steps) | Run 218 |
| A03 target position changes | PASS | Run 218 53,-17 → 87,4 |
| A04 target null | PASS (source) | `TalarStepPlanner` clear |
| A05 target block removed | PASS (cache clear) | Run 219 `[R3] CACHE-CLEAR` |
| A06 same-position replacement | NOT RUN | no fixture |
| A07 different-position replacement | PASS | Run 218 |
| A08 target non-log | PASS (cache clear) | Run 219 |
| A09 B after A removal | NOT RUN | R3/R4 fixture failed |
| A10 two replacements | NOT RUN | no fixture |

### Group B — Planner/cache

B01–B05: PASS by source plus Run 218/219. B06–B10: NOT RUN (no focused fixture). No
cross-worker cache contamination was observed in Run 218 (b89 and T2 independent).

### Group C — Navigation/lease

| ID | Status | Evidence |
|---|---|---|
| C01 start | PASS | Run 218 AGENT-AUTH/NAV-GATE |
| C02 arrival | PASS | Run 218 harvest/delivery |
| C03 remove while stationary | NOT RUN | |
| C04 remove while moving | NOT RUN | |
| C05 replace while moving | NOT RUN | |
| C06 lease expiry | PASS (general) | A7-05 Run 201 |
| C07 other owner | PASS (general) | A7-07 Run 205 |
| C08 blocked obstacle | NOT RUN | |
| C09 no destination | PARTIAL | R3 cache clear; command stop unproven |
| C10 replacement command | NOT RUN | |
| C11 repeated invalidation | NOT RUN | |
| C12 Goal stop/restart | NOT RUN | |

### Group D — Wall/gate

D01–D12: **NOT RUN**. No gate geometry fixture, no `wallGate=true`, no physical crossing.

### Group E — End-to-end

E01 internal harvest/delivery: **PASS** (Run 218).
E02–E06: **NOT RUN**.

### Group F — OFF/ENFORCE

- Same shared planner: PASS source.
- OFF uses Goal cache; ENFORCE agent cache: PASS source.
- No duplicate TALAR algorithm: PASS source.
- ENTREGAR legacy: PASS source.
- Runtime equivalence: NOT RUN (A7-12 inconclusive).

### Group G — Regression/invariants

- Harvest/replant/grow/storage/pantry/delivery: unchanged source; Run 218 PASS.
- NBT: unchanged.
- Brain/GoalSelector: unchanged.
- Cache per worker: PASS Run 218 observation.
- Ownership invariants: PASS.

## 6. Target lifecycle results

Demonstrated: A01, A02/A03/A07, A04/A05/A08.
Not demonstrated: A06, A09, A10, replacement task identity.

## 7. Planner/cache results

Standing-cell + wall-gate extraction works for internal targets. `wallGate=false` only.
Cache clear on null/non-log target is implemented. No cache-clear → active PathNavigation
stop proof.

## 8. Navigation/lease results

Run 218: agent acquired lease, issued commands, completed harvest/delivery. C03–C05,
C08–C12 remain unverified. General lease expiry/stale protection is covered by A7-05/A7-07,
not by a TALAR-specific removal fixture.

## 9. Wall-gate results

**BLOCKED**: no valid fixture and no physical crossing. No geometry was changed.

## 10. End-to-end forestry

E01 PASS (Run 218). External/gate sequences NOT RUN.

## 11. OFF/ENFORCE compatibility

Source ownership is correct. No new runtime OFF/ENFORCE pair was executed; A7-12 remains
inconclusive.

## 12. Regression/invariant results

PASS for source invariants and Run 218 natural harvest/delivery. No regression observed.

## 13. Defect classifications

- R3 custom worker target discovery: **FIXTURE_ORCHESTRATION**.
- Gate absence: **FIXTURE_GEOMETRY / NOT RUN**.
- Cache clear without PathNavigation stop: **INSUFFICIENT_EVIDENCE**, not proven defect.
- No production defect demonstrated.

## 14. Code changes

None for R5. R3 diagnostic and fixture remain.

## 15. Verification

```text
compileJava = BUILD SUCCESSFUL
NAVIGATION_LEASE_TEST = OK
NAVIGATION_GATEWAY_POLICY_TEST = OK
NAVIGATION_GATEWAY_HARNESS_TEST = OK
lint_aldea.py --strict = PASS
git diff --check = PASS
```

## 16. Remaining limitations

- No deterministic A→B recovery fixture.
- No active-navigation removal/lease-release proof.
- No wall-gate selection/crossing.
- No full OFF/ENFORCE equivalence.
- R3 custom worker still unvalidated.

## 17. Git and safety

```text
Branch: feature/autonomous-village
HEAD: 97220be5453b57387002b9bc91935807cc665274
Staged: 0
Unstaged: 26
Untracked: 165
```

No protected saves, NBT changes, A5/A6 modifications, prohibited Git operations, commits or
pushes.

## 18. Overall acceptance

**PARTIAL / BLOCKED for mandatory gate and recovery scenarios.**

A8-F03 core (shared planner + agent TALAR ownership + internal E01) is PASS. It cannot be
classified PASS overall without:
1. deterministic A→B task recovery,
2. active navigation cleanup/lease release after removal,
3. valid gate selection and physical crossing.

The campaign must not be reported as fully executed; many cells are NOT RUN.
