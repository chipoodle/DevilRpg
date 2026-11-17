# A2 IDX4 — Do not plan DROP take-off recovery from an airborne cell

## Status

**IMPLEMENTATION PUBLISHED — PENDING HARNESS.** Initial implementation commit: `bd638e92dd86214dd8af3a17f1a6726daaea39e8` (parent `9df030281ff0d132d492255342a1e6a03e3e5543`); final source checkpoint with plan-admission telemetry: `190bb6578906068e33c3a835feb2cacf7bc93085`. This report records the correction prompted by Harness run `a2n-20261117-001624`; it does not claim that the new implementation passes runtime.

## Evidence and diagnosis

Harness tested `7aed2c683852969c323524bbed7d95965c330790` and reported static PASS, A2 Run 1 FAIL (4/8 strict, 5/8 perimeter). For IDX4, the trace showed:

```text
DROP_TAKEOFF_STEP_UP_ASSIST
 from=567,121,534 target=566,122,534
 onGround=false support=true fits=true

DROP_TAKEOFF_RECOVERY_ROUTE_STALLED
 takeoff=566,122,533 node=566,122,534 transition=STEP_UP
 pos=567,119,534 ticksSinAvance=21
```

The trace proves that the STEP_UP assist was executing while `onGround=false`; the prior trace did not record the grounded state at the instant the auxiliary A* route was accepted, so it does **not** prove retrospectively that planning itself began airborne. Source review did establish that recovery planning had no grounded/source-support admission guard, while the STEP_UP executor could keep issuing horizontal movement from an airborne source without initiating a jump. The mob then fell to y119 and the one-block edge became stale. The correction closes both gaps and adds plan-acceptance telemetry so Harness can verify the source state directly.

This is distinct from the already-fixed 900-expansion overflow: the report observed no `901/900`.

## Implementation

- `NavStepUpRecoveryPolicy.canStartFrom` now admits recovery planning only when the mob is grounded, the source feet cell is valid, supported, and fits the entity.
- `iniciarRecuperacionFisicaOrigenDrop` now defers planning while those physical preconditions are false. The suspended original DROP route is not discarded; the executor retries on subsequent ticks from the live cell after landing. `DROP_TAKEOFF_RECOVERY_ROUTE_ACCEPTED` now records exact position, grounded state, valid feet, support and fit at plan admission.
- Recovery STEP_UP preserves horizontal MoveControl during an airborne jump/fall, but JumpControl is only requested by the assist from a grounded, supported source.
- If the mob lands and the active one-block STEP_UP edge is no longer adjacent at the expected elevation, the auxiliary recovery fails closed and the strategic mission is replanned from the live grounded cell. It does not execute a stale edge.
- Added pure-policy coverage for grounded source admission, adjacent one-block geometry, and airborne/unsupported STEP_UP assist rejection.

No teleport, direct velocity impulse, block placement/destruction, budget increase, collision relaxation, support/fit relaxation, or siege acceptance change.

## Files changed

- `src/main/java/com/chipoodle/devilrpg/entity/nav/NavExecutor.java`
- `src/main/java/com/chipoodle/devilrpg/entity/nav/NavStepUpRecoveryPolicy.java`
- `src/test/java/com/chipoodle/devilrpg/entity/nav/NavDropTakeoffPolicyTest.java`
- `docs/autonomous-village/NEXT-SESSION.md`
- `docs/autonomous-village/CONTINUAR.md`
- This report.

## Validation

ChatGPT did not execute Gradle, lint, diff-check, diagnostics compilation, or Minecraft runtime. The new code and tests are **PENDING HARNESS**.

Expected policy output after Harness execution: `NAV_DROP_TAKEOFF_POLICY_TEST: OK (32 assertions)`; route-node policy remains 6 assertions.

## Harness next action

1. Fetch and test the exact published implementation SHA.
2. Run Gradle tests, strict lint, diff-check, and diagnostics compile.
3. If static checks pass, run A2 Run 1 only.
4. Capture:
   - `DROP_TAKEOFF_RECOVERY_WAIT_GROUNDED` while the mob is airborne;
   - recovery plan start only after grounded + valid feet + support + fit;
   - `DROP_TAKEOFF_STEP_UP_ASSIST` only with `onGround=true` and `sourceSupport=true`;
   - physical occupancy/support/fit at every recovery waypoint;
   - `DROP_TAKEOFF_RECOVERY_REACHED`, restored original route cursor, and final physical DROP landing.
5. Do not run A2 Run 2 unless Run 1 is 8/8 strict. Do not run full 12k until five consecutive 8/8 strict runs.

## Unchanged open issues

- A2 remains OPEN.
- IDX7 BUILD/local expansion failure and IDX8 global/local route failure remain independent and untouched.
- C4 focused PASS `[1,0,0]`; save/reload remains OPEN.
- No full 12k campaign.
- Original A2/C4 worlds and local artifacts remain Harness-owned and must be preserved.
