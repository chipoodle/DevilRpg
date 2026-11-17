# A2 Phase 29 — DROP snap exclusive ownership and admission fix (2026-11-17)

## 1. Result

**COMPLETE for the Phase 29 correction.** The snap is now the sole movement owner while
`fuenteDropPendiente` is set, route navigation is blocked until snap completion, and completion uses
the 0.20 center predicate. A2 remains **OPEN** (runtime 2/8 strict), but the Phase 28 snap/admission
defect is corrected and deterministically covered.

- Branch: `feature/autonomous-village`
- Initial HEAD: `cebca95b64a4856e6216941631b636af4d1e561b`
- Production SHA: `192d0c521f86efa39fe9ffe1d47348fcf7d4d5e6`
- Prior production inspected: `05b7e9c04b3619a0d2fea1a9a79899e27f131aac`
- Runtime run: `a2n-20261117-170935` (disposable world)
- Original A2/C4 worlds: untouched

## 2. Production changes

### New policy

`src/main/java/com/chipoodle/devilrpg/entity/nav/NavSnapOwnershipPolicy.java`

```java
snapOwnsMovement(pending)          -> pending != null
allowsRouteMovement(pending)       -> pending == null
allowsOscillationAbort(pending)    -> pending == null
snapComplete(centered, grounded, feetValid, supported, fits)
```

### `NavExecutor`

- `traverse()` now checks `snapOwnsMovement(fuenteDropPendiente)` **before** route/oscillation logic.
  While pending:
  - `mob.getNavigation().stop()`;
  - only `resolverFuenteActualYReplanificar(now)` runs;
  - returns without DROP/route movement.
- Oscillation abort is additionally gated by `allowsOscillationAbort(pending)`.
- Both snap completion paths use `NavSnapOwnershipPolicy.snapComplete(...)`; the 0.20 center check is
  unchanged.
- Added telemetry:
  - `SNAP_MOVE_OWNER` — pending snap owns movement, route blocked.
  - `SNAP_ADMISSION` — exact position, candidate, dx/dz, centered, decision.
  - `SNAP_SWEPT_AABB` — before/target/swept AABB and collision result.
  - `SNAP_GUARD` — oscillation abort context (including occurrences/window).
  - `SNAP_COMPLETE` — candidate, exact position, distance², physical checks.
  - `DROP_TAKEOFF_SOURCE_SELECTED` — selected candidate and strategy.

No budget, speed, support, feet, fit, AABB, collision, tolerance or acceptance criterion changed.

## 3. Tests

`src/test/java/com/chipoodle/devilrpg/entity/nav/NavSnapOwnershipPolicyTest.java` — 12 assertions:

1. pending snap owns movement;
2. route movement blocked during snap;
3. oscillation abort blocked during snap;
4. route movement allowed with no snap;
5. oscillation abort allowed with no snap;
6. 0.49 from center does not complete;
7. inside 0.20 completes;
8. no support blocks completion;
9. no real-AABB fit blocks completion;
10. airborne blocks completion;
11. invalid feet blocks completion;
12. same integer cell but non-centered does not complete.

Registered in `build.gradle` `policyTest`.

Static results:

```text
gradlew test: PASS
NAV_DROP_TAKEOFF_POLICY_TEST: OK (51 assertions)
NAV_SOURCE_CELL_POLICY_TEST: OK (14 assertions)
NAV_SNAP_OWNERSHIP_POLICY_TEST: OK (12 assertions)
lint_aldea.py --strict: PASS
git diff --check: PASS
compileJava -Pa2nDiagnostics=true: PASS
```

## 4. Runtime evidence

Run `a2n-20261117-170935`, disposable A2 world.

```text
A2: 2/8 strict, 2/8 perimeter
Strict: idx1 t1820, idx6 t649
C4: PASS [1,0,0]
Run 2: not executed
Full 12K: not executed
```

IDX4 UUID `b6039940-c68c-4f08-a535-5c967cdb6464`.

Exclusive ownership:

```text
SNAP_MOVE_OWNER count (all attackers): 137
SNAP_COMPLETE count:                  24
SNAP_SWEPT_AABB count:                129
SOURCE_SELECTED count:                33
```

For IDX4:

```text
t190 SNAP_MOVE_OWNER pending=567,122,531 owner=SNAP routeBlocked=true
t200 SNAP_MOVE_OWNER ...
t210 SNAP_MOVE_OWNER ...
t211 SNAP_COMPLETE candidate=567,122,531
     exact=567.691,122.000,531.499 centered=true
```

No `MOVE_COMMAND` was emitted for IDX4 between t190 and t215, i.e. while the snap was pending.

Swept AABB samples for IDX4:

```text
t190 before=AABB[567.8351,122.0,531.3491] -> [568.1351,122.9750,531.6491]
     target=AABB[567.3500,122.0,531.3500] -> [567.6500,122.9750,531.6500]
     swept=... collision=false dx=0.485 dz=-0.001
t210 ... collision=false dx=0.205 dz=-0.001
```

Global swept result: `collision=false` 113, `collision=true` 16. IDX4's completed snap was collision
free.

After the snap, IDX4 later fails independently with `replans agotados (8/8)` at `571,115,533`; this is
not a snap/admission failure and is outside Phase 29's fix scope.

## 5. Phase 28 reproduction limitation

The exact Phase 28 coordinates `(577,110,513)->(577,110,514)` did not recur in this disposable A2 run;
the RNG/world geometry produced a different IDX4 path. The same contract was exercised by:

- `SNAP_MOVE_OWNER` blocking route commands;
- `SNAP_COMPLETE` only at `centered=true`;
- deterministic 0.49/non-centered unit test;
- swept-AABB telemetry.

The exact Phase 28 geometric scenario is therefore covered deterministically but not reproduced
byte-for-byte at runtime.

## 6. Invariants preserved

```text
budgets unchanged (900/8000)
speed unchanged (0.35 snap, 0.9 DROP)
support/feet/AABB/fit unchanged
radial tolerance unchanged (0.20)
collision/protection unchanged
no teleport/setPos/artificial impulse
no BREAK/BUILD/BRIDGE added
no acceptance criteria changed
```

## 7. Files and commits

Production files:

- `src/main/java/com/chipoodle/devilrpg/entity/nav/NavExecutor.java`
- `src/main/java/com/chipoodle/devilrpg/entity/nav/NavSnapOwnershipPolicy.java`

Test/build files:

- `src/test/java/com/chipoodle/devilrpg/entity/nav/NavSnapOwnershipPolicyTest.java`
- `build.gradle`

Commits:

```text
192d0c52 fix(nav): make drop source snap exclusive and center-gated
```

Documentation commit follows in `NEXT-SESSION.md`/`CONTINUAR.md`.

## 8. Integrity

```text
PRODUCTION_MODIFIED_BY_HARNESS: true (authorized Phase 29 minimal fix)
ORIGINAL_WORLDS_MODIFIED: false
LOCAL_ARTIFACTS_PRESERVED: true
TEMPORARY_WORLD_USED: YES — disposable A2 run world
```

A2 world `BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7`
C4 world `11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF`

## 9. Next action

ChatGPT should review this Phase 29 runtime. The snap/admission correction is complete, but A2 remains
2/8 strict and IDX4 now fails later at `replans agotados (8/8)`. Phase 30 should diagnose that
independent post-snap failure without reopening the exclusive-snap contract.
