# A2 Phase 50 validation — monotonic anchor + CADENCE_WAIT (2026-11-18)

## 1. Result

**FAIL / NOT VALIDATED.** Static tests pass, but runtime shows both production corrections are not wired:

1. `STAIR_BUILDER_ANCHOR_REFRESH` still assigns `escaleraAncla = blockPosition().below()` directly. At
   t337 it moves `607,93 -> 607,94`; `NavStairAnchorPolicy.refresh()` is never called.
2. The cooldown branch still returns `false`; `construirEscalonResultado()` maps it to `NO_PROGRESS`,
   not `CADENCE_WAIT`. `A2_LOCAL_STAIR_WAIT` is absent (0 events), and local steps still increment.

- RUN_ID: `a2n-20261118-212254`
- RUN_HEAD: `31a01091a6baf69918528ee57ac6a87f2c7e68d7`
- PRODUCTION_SHA: `f7a919c2ececcf9c745e5e95a8f141ee124ca25e`
- Previous production: `9d6709f551f394e0ac7be69160c2aa46419d8058`
- A2: 3/8 strict (idx1 t513, idx5 t730, idx6 t650), C4 PASS `[1,0,0]`
- No production/test change by Harness.

## 2. Static

```text
gradlew test: PASS
lint_aldea.py --strict: PASS
git diff --check: PASS
compileJava -Pa2nDiagnostics=true: PASS

NAV_STAIR_ANCHOR_POLICY_TEST: OK (8 assertions)  [not registered in build.gradle]
STAIR_BUILD_RESULT_TEST: OK (6 assertions)       [not registered in build.gradle]
NAV_ADJACENT 15, NAV_DROP_APPROACH_OWNERSHIP 13, NAV_DROP_EDGE_STEP 16: PASS
```

Because the two new tests are not in `build.gradle`, `gradlew test` did not execute them. They were run
directly through a temporary Gradle init task (no repo change) and both PASSed. Registration must be
fixed before they count as part of the normal gate.

## 3. Anchor failure

Code path still present:

```java
BlockPos anchorBefore = escaleraAncla;
escaleraAncla = blockPosition().below();
NavStairTrace.candidate("STAIR_BUILDER_ANCHOR_REFRESH", ...);
```

`NavStairAnchorPolicy.refresh()` is imported but never called.

Runtime IDX3 UUID `dd66a599-fdf5-4fdd-9522-72b95ca8dea8`:

```text
t288 ESCALON-TRAVESADO 607,93 desde607,94
t337 STAIR_BUILDER_ANCHOR_REFRESH
     supportBelow=607,94
     anchorBefore=607,93 -> anchorAfter=607,94   <-- still moves upward
```

The accepted candidate remains the same stair coordinate:

```text
t... candidate=607,93 dy=1 ACCEPTED_GEOMETRY
CONSTRUCCION-ESCALERA 607,93,601 repeated
```

The policy test alone is not sufficient; production does not use it.

## 4. CADENCE_WAIT failure

Runtime:

```text
STAIR_BUILDER_COOLDOWN=216
A2_LOCAL_STAIR_WAIT=0
```

Builder still does:

```java
if (escaleraRetraso > 0) {
    escaleraRetraso--;
    NavStairTrace.ev("STAIR_BUILDER_COOLDOWN", ...);
    return false;
}
```

and the caller maps any `false` to:

```java
ultimoResultadoEscalon = StairBuildResult.NO_PROGRESS;
```

No branch sets `StairBuildResult.CADENCE_WAIT`. Therefore:

```text
STAIR_BUILDER_RETURN outcome=NO_PROGRESS (not CADENCE_WAIT)
A2_LOCAL_STAIR_WAIT absent
A2_LOCAL_STAIR_WRITE op=INC_STEP still occurs on cooldown returns
```

Example:

```text
t3 COOLDOWN 10->9 outcome=NO_PROGRESS
t3 A2_LOCAL_STAIR_WRITE new=...
t149 COOLDOWN 8->7 outcome=NO_PROGRESS
```

## 5. A2 result

```text
A2: 3/8 strict, 3/8 perimeter
STRICT: idx1 t513, idx5 t730, idx6 t650
C4: PASS [1,0,0]
RUN2/FULL12K: not executed
```

No 8/8; A2 OPEN.

## 6. Integrity

```text
PRODUCTION_MODIFIED: false
TESTS_MODIFIED: false
WORLDS_MODIFIED: false
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F639ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

## 7. Required production work

1. Replace the direct assignment with `NavStairAnchorPolicy.refresh(anchorBefore,
   blockPosition().below(), escaleraTravesiaConfirmada/hasTraversed)` and use its result.
2. Return `StairBuildResult.CADENCE_WAIT` from `construirEscalonResultado()` when the cooldown branch
   was taken; do not map it to NO_PROGRESS.
3. Emit `A2_LOCAL_STAIR_WAIT` and skip `INC_STEP`/`INC_FAIL` for that outcome.
4. Register both new tests in `build.gradle`.

No budget/physical predicate changes are justified by this validation.

## 8. Recommendation

Phase51 should correct the wiring and revalidate on exact SHA. A2 remains OPEN at 3/8 strict.
