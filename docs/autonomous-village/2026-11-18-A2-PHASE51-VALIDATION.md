# A2 Phase 51 validation (2026-11-18)

## 1. Result

**FAIL.** Static PASS and the CADENCE_WAIT wiring is validated, but the monotonic-anchor path was not
exercised at runtime: no stair was placed or traversed, so `STAIR_BUILDER_ANCHOR_REFRESH=0` and no
strictly-lower candidate can be observed. A2 regressed from 3/8 to **2/8 strict**.

- RUN_ID: `a2n-20261118-215530`
- RUN_HEAD: `9df15d87f31b9a7b2b0375c5bb9074eae1b78392`
- PRODUCTION_SHA: `ea21466a0cc7cc2671f5439cd5ff7bbaeecd6d9e`
- DOCUMENTATION_HEAD at start: `9df15d87f31b9a7b2b0375c5bb9074eae1b78392`
- No production/test change by Harness.

## 2. Git / synchronization

```text
Initial HEAD: 53756dcc3b041a8b9c579095070da296fbb7d1fe
Remote:       9df15d87f31b9a7b2b0375c5bb9074eae1b78392 (14 ahead)
Action: fast-forward only
Untracked preserved: .a2-backups/, src/foodTest/, A2NavHarness.java.partial,
                     consultar-c4.ps1, lanzar-c4.ps1, a5-closure/
Production diff after ea21466a..HEAD (src/build.gradle): empty
```

## 3. Static

```text
.\gradlew.bat test: PASS
NAV_STAIR_ANCHOR_POLICY_TEST: OK (8 assertions)  [normal Gradle gate]
STAIR_BUILD_RESULT_TEST: OK (6 assertions)       [normal Gradle gate]
python lint_aldea.py --strict: PASS
git diff --check: PASS
.\gradlew.bat compileJava -Pa2nDiagnostics=true: PASS
```

Both new tests are now registered and executed by the normal gate.

## 4. A2 Run1

```text
A2: 2/8 strict, 2/8 perimeter
STRICT: idx1 t513, idx6 t659
C4: PASS [1,0,0]
RUN2/FULL12K: not executed
```

Per attacker:

```text
idx1 34755a02-9516-4a05-ae9d-025da9cb54a9 strict t513
idx2 afea893e-d17b-438b-973b-44a388d5f994 no entry
idx3 ab713329-f1dc-4a12-83b6-0beee6031cec no entry, final 594,71,600 FAILED
idx4 e61b0b8e-a338-4757-bba2-c3e491c49a54 no entry
idx5 72f57e9d-adc9-4a2d-bd45-5bc981af829f no entry (previous run entered at t730)
idx6 d33bd101-9c8d-4ae8-9499-18e98a35e5ae strict t659
idx7 e2c27683-1e6d-49a6-a009-a02f17603d33 no entry
idx8 bc32f982-45d3-449c-b522-e2d2a483a5ca no entry
```

IDX5 losing its strict entry is a regression from Phase50's 3/8.

## 5. IDX3 stair evidence

UUID `ab713329-f1dc-4a12-83b6-0beee6031cec`.

```text
CONSTRUCCION-ESCALERA=0
ESCALON-TRAVESADO=0
ESCALERA-ANCLA=0
STAIR_BUILDER_ANCHOR_REFRESH=0
STAIR_BUILDER_CANDIDATE=0
```

Final:

```text
pos=594,71,600 estado=FAILED
escalones=0/64 fallos=0/10
escalera.colocados=0/24
motivo=el frente alcanzable se agoto dentro del radio 112 rutaFrente=0
```

No physical stair event exists, so the monotonic-anchor correction and strictly-lower candidate rule
remain `INSUFFICIENT_EVIDENCE` at runtime. The prior `607,93` loop was not reproduced.

## 6. CADENCE_WAIT evidence

This half is validated:

```text
STAIR_BUILDER_COOLDOWN=3595
A2_LOCAL_STAIR_WAIT=3542
```

Example:

```text
t3 COOLDOWN 10->9 reason=CADENCE_WAIT
t3 STAIR_BUILDER_RETURN outcome=CADENCE_WAIT retraso=9
t3 A2_LOCAL_STAIR_WAIT local=0/64 reason=CADENCE_WAIT counters_unchanged=true

t5 COOLDOWN 9->8 ... WAIT local=0/64
t7 COOLDOWN 8->7 ... WAIT local=0/64
```

No `INC_STEP`/`INC_FAIL` accompanies these waits. CADENCE_WAIT is propagated and deferred correctly.

## 7. Classification

```text
STATIC: PASS
CADENCE_WAIT: PASS (runtime demonstrated)
MONOTONIC_ANCHOR: INSUFFICIENT_EVIDENCE (no stair placement/traversal)
A2: FAIL / regression 3/8 -> 2/8
C4: PASS [1,0,0]
```

## 8. Integrity

```text
PRODUCTION_MODIFIED: false
TESTS_MODIFIED: false
WORLDS_MODIFIED: false
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

## 9. Recommendation

Do not declare Phase51 complete. Re-run only after a scenario actually constructs/traverses a stair
(so anchor refresh can be observed), and investigate why CADENCE_WAIT now yields zero stair placements
and loses IDX5. Harness will not modify production.
