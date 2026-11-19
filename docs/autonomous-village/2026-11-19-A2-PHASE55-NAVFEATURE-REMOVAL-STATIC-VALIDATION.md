# A2 Phase55 — NavFeature removal static validation (2026-11-19)

## 1. Result

**PASS — static only.**

The canonical harness no longer depends on the removed `NavFeature` class. Gradle selects
`tools/a2n/A2NavHarness.java`, the legacy harness remains untouched, compile/tests/lint/diff all pass.

- RUN_ID: `phase55-navfeature-static-20261119-1427`
- BRANCH: `feature/autonomous-village`
- INITIAL_HEAD: `bc5e780037cc991d87afe6c2d03a1df9bc9e458b`
- TESTED_HEAD: `b96f79f2edde53ea1049ef3ae0fd9f9ea9d84fdd`
- FINAL_HEAD at validation: same

## 2. Protected blobs

```text
tools/a2n/A2NavHarness.java                         914d26da4f9a553f43936812ddc303ed56a24243
src/main/java/.../debug/A2NavHarness.java           28ea2fb8ce970326c1157046dcb59e3d1cc5892d
src/main/java/.../entity/nav/NavPlanner.java        5666e42693fe2e90dff1d2ac1004b59b9b62d7cd
build.gradle                                        84023309a18d50dfcb1725c13c5841c49eda87e9
tools/a2n/lanzar-phase55-diagnostico.ps1            a98bc53f99c0871ee16e8784faa7157c972ecc1a
```

All match the expected values. Legacy source unchanged.

## 3. NavFeature removal

```text
Select-String NavFeature in canonical harness: 1 occurrence
```

Only the descriptive comment remains:

```java
// La migracion NavExecutor single-authority elimino NavFeature y su fallback por bandera.
```

No compilable reference to `NavFeature`, `customNavigation()`, `PROPERTY` or `ENV` remains.

## 4. Architecture verification

- `AggressiveZombieEntity` owns a `NavExecutor` field and constructs it at line600.
- `AggressiveZombieEntity.aiStep()` calls `this.navExecutor.tick()` at line2762.
- No `AggressiveZombiePathPlanner` class exists under `entity/nav/`.
- The migration document records `NavFeature` and `AggressiveZombiePathPlanner` as removed.
- Canonical harness records:
  `navCustomEfectivo=true modo=NavExecutor-single-authority featureFlag=eliminado`.
- Trace header records `navCustom=NavExecutor-single-authority`.

## 5. Source selection

`compileJava --info` explicitly reports:

```text
Input property 'stableSources' file ...\src\main\java\...\debug\A2NavHarness.java has been removed.
Input property 'stableSources' file ...\tools\a2n\A2NavHarness.java has been added.
```

Compiled class contains:

```text
public static void capturarAldeanoDeFixture(
        net.neoforged.neoforge.event.entity.EntityJoinLevelEvent);
```

This proves the canonical Phase55 harness was compiled, not the legacy one.

## 6. Commands and results

```text
.\gradlew.bat compileJava --info -Pa2nDiagnostics=true
  -Pa2nPopulationQueryHarness=true -Pa2nTraceNav=true -Pa2nTraceGlobalSearch=true
PASS, exit 0

.\gradlew.bat test --info -Pa2nDiagnostics=true
  -Pa2nPopulationQueryHarness=true -Pa2nTraceNav=true -Pa2nTraceGlobalSearch=true
PASS, exit 0

python tools\lint_aldea.py --strict
PASS, exit 0

git diff --check
PASS, exit 0
```

NeoForge API calls compiled without error: `EntityJoinLevelEvent`, `isAddedToLevel()`,
`areEntitiesLoaded(long)`, `ServerLevel.getEntity(UUID)`.

## 7. Tests

```text
NAV_PLANNER_STATE_TEST: PASS, 54 executed assertions
Named test groups reported: 53
```

The full Gradle suite finished `BUILD SUCCESSFUL`; no `FALLO:` markers.

## 8. Preservation

```text
RUNTIME_EXECUTED: false
MINECRAFT_LAUNCHED: false
WORLDS_MODIFIED: false
FIXTURES_MODIFIED: false
PRODUCTION_MODIFIED: false
LEGACY_HARNESS_MODIFIED: false
TEMP_SOURCE_CREATED: false
```

Untracked preserved: `.a2-backups/`, `src/foodTest/`, `tools/a2n/A2NavHarness.java.partial`,
`tools/a2n/consultar-c4.ps1`, `tools/a2n/lanzar-c4.ps1`, `tools/a5-closure/`.

```text
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

Logs:

```text
build/phase55navfeature-compile.log
build/phase55navfeature-test.log
build/phase55navfeature-lint.log
```

## 9. Conclusion

The `NavFeature` removal and canonical source routing are statically valid. No runtime authorization
exists; the next A2 diagnostic run requires new explicit user authorization. A2 remains OPEN.
