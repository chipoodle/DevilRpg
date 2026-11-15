# A2/C4 Phase 2 — Root Causes and Corrections (2026-11-15)

## Git synchronization

- Branch `feature/autonomous-village`.
- Remote `origin/feature/autonomous-village` verified at `cc27f5ec24c93033080f9324f525d985ad919d27` before this phase.
- Source/docs from the prior phase were published at:
  - `61a9649` — IDX8/C4 forensic report + NEXT-SESSION.
  - `03e4e38` — A2 source, tests, harness, docs.
  - `cc27f5e` — synchronized checkpoint.
- Historical report preserved its original baseline `518fee13`; this phase records the actual current baseline.

## A2 — IDX8 root cause and correction

Observed trace `a2n-20261115-024825`:

IDX8 delegated BREAK against an elevated local node while the raycast selected the **support block underneath**:

```text
target=746,65,535 blocker=746,64,535 targetHit=false
```

`NavExecutor.primerBloqueoEnLineaDeVision` used a vanilla block clip from the eye to the target center and returned the first collider. For elevated `BREAK` nodes, that collider can be the floor beneath the node. Breaking that floor removes the attacker's footing and produces the observed oscillation instead of opening the route.

### Correction

- New `NavBreakTargetPolicy.isSupportBelowTarget(...)`.
- `primerBloqueoEnLineaDeVision` now ignores a hit that is on the same column but below the node; it returns the node itself when the node is solid, otherwise `null`.
- Regression test `NavBreakTargetPolicyTest` (3 assertions).

This is a general fix for elevated BREAK nodes; no budget increase, no teleport, no wall bypass, no collision weakening.

## C4 — worker recovery root cause and correction

`VillagerFisherGoal.canUse` rejects workers beyond `FENCE_RADIUS + 10` (72 blocks) from center. The three failed workers were at 87–102 blocks and had no return-to-village behavior, so no profession goal could start. The fisherman’s valid barrel site therefore could not help.

### Correction

- New `VillagerReturnPolicy`: start beyond 72 blocks, continue until 64 (hysteresis).
- New `VillagerReturnGoal` (MOVE, priority UTILITY=2): walks a displaced worker back to the village center. Flee (priority 0) still wins under threat; profession goals become eligible again at 64.
- Registered for every non-guard worker next to `VillagerFleeGoal`.
- Regression test `VillagerReturnPolicyTest` (4 assertions).

No job-site forcing, teleport, profession override, or acceptance change.

## Controlled runs

### `a2n-20261115-145020`

- 8/8 spawn; 5/8 entered; IDX8 outside at `702,62,457`.
- C4 **PASS 12/12**, jobSite `[1,0,0]`.
- Confirms worker return fix.

### `a2n-20261115-150410`

- 8/8 spawn.
- **8/8 entered** (`entraronPerimetroEver=8`).
- Bell `DESTRUYO` and `SIEGE_COMPLETED` verified at tick 1284, one event.
- IDX8 entered; final outside at `675,71,461` while traversing.
- Deaths normal combat.
- C4 **PASS 12/12**, jobSite `[2,0,0]`.
- Repeat after `145020`: C4 2/2 PASS in this configuration.

A2 focused siege result: **PASS** (spawn, entry, conquest, legitimate combat). Full 12k acceptance was not run. C4 stability evidence is 2 consecutive focused passes, not a claim of universal stability.

## Worlds

Both existing diagnostic worlds were verified unchanged:

### `DevilRpg-A2-IDX8-Diagnostico`

- Std: `C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-A2-IDX8-Diagnostico`
- Dev: `D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-A2-IDX8-Diagnostico`
- Size `15,966,366`; SHA-256 `BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7`
- No `session.lock`; both copies match.

### `DevilRpg-C4-WorkerResume-Diagnostico`

- Std: `C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-C4-WorkerResume-Diagnostico`
- Dev: `D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-C4-WorkerResume-Diagnostico`
- Size `16,044,629`; SHA-256 `11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF`
- No `session.lock`; both copies match.

They capture the original failures; they were not regenerated or overwritten.

## Tests

```text
gradlew test: PASS
NavBreakTargetPolicyTest: OK (3 assertions)
VillagerReturnPolicyTest: OK (4 assertions)
python tools/lint_aldea.py --strict: OK
git diff --check: PASS
```

## Untracked classifications

- `.a2-backups/` — local forensic backups; keep local, do not commit.
- `src/foodTest/` — local test scratch; not part of this fix.
- `tools/a2n/A2NavHarness.java.partial` — partial scratch; keep local.
- launcher helpers — local tooling; keep local.

## Remaining

- A2: full 12k not run; IDX8 now enters but its convergence is still variable across runs (one run 5/8, one 8/8). The BREAK support fix is validated as a behavior change but not as a universal guarantee.
- C4: two consecutive focused PASSes; longer repeated runs still needed before claiming stability.
