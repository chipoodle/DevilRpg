# A2 Phase55 — canonical harness routing static validation (2026-11-19)

## 1. Result

**FAIL — routing works and selects `tools/a2n/A2NavHarness.java`, but the canonical source does not
compile: it references missing class `com.chipoodle.devilrpg.entity.nav.NavFeature`.**

- RUN_ID: `phase55-routing-static-20261119-1400`
- BRANCH: `feature/autonomous-village`
- INITIAL_HEAD: `62cb5be88fcb5df9aa3c3e3bac8e6899f35d8f52`
- TESTED_HEAD: `c2e44f78c09c2a4819860a75e5db171140e54b52`
- FINAL_HEAD at validation: same
- NAVPLANNER_BLOB_SHA: `5666e42693fe2e90dff1d2ac1004b59b9b62d7cd`
- LEGACY_HARNESS_BLOB_SHA: `28ea2fb8ce970326c1157046dcb59e3d1cc5892d`
- CANONICAL_HARNESS_BLOB_SHA: `3205305a10a21ded066c03cb746908e94632e08d`
- BUILD_GRADLE_BLOB_SHA: `84023309a18d50dfcb1725c13c5841c49eda87e9`
- WRAPPER_BLOB_SHA: `a98bc53f99c0871ee16e8784faa7157c972ecc1a`

## 2. Git / preservation

```text
Local was 14 commits behind; fast-forward only.
Legacy source unchanged.
Canonical source unchanged.
NavPlanner unchanged.
No temporary source created or deleted.
Untracked artifacts preserved.
```

## 3. Routing review — PASS

`build.gradle`:

- requires `a2nDiagnostics` when `a2nPopulationQueryHarness` is true;
- excludes legacy `com/chipoodle/devilrpg/debug/A2NavHarness.java` only in this opt-in build;
- adds `tools/a2n` as a source root;
- canonical package is `com.chipoodle.devilrpg.debug`;
- normal builds still exclude `debug/**`;
- non-population diagnostic builds keep legacy behavior.

Wrapper defines and restores:

```text
ORG_GRADLE_PROJECT_a2nDiagnostics
ORG_GRADLE_PROJECT_a2nPopulationQueryHarness
ORG_GRADLE_PROJECT_a2nTraceNav
ORG_GRADLE_PROJECT_a2nTraceGlobalSearch
JDK_JAVA_OPTIONS
```

## 4. Effective source selection — CONFIRMED

The compile error paths are the proof:

```text
D:\Documents\DevilRpg-autonomous\tools\a2n\A2NavHarness.java:1323 ...
D:\Documents\DevilRpg-autonomous\tools\a2n\A2NavHarness.java:1737 ...
```

The compiler selected `tools/a2n/A2NavHarness.java`, not the legacy `src/main/.../debug` file.

## 5. Compile failure

```text
.\gradlew.bat compileJava --info -Pa2nDiagnostics=true
  -Pa2nPopulationQueryHarness=true -Pa2nTraceNav=true -Pa2nTraceGlobalSearch=true
exit 1
```

Seven errors, all the same missing class:

```text
tools/a2n/A2NavHarness.java:1323: cannot find symbol: class NavFeature
tools/a2n/A2NavHarness.java:1324: cannot find symbol: class NavFeature
tools/a2n/A2NavHarness.java:1325: cannot find symbol: class NavFeature
tools/a2n/A2NavHarness.java:1326: cannot find symbol: class NavFeature
tools/a2n/A2NavHarness.java:1327: cannot find symbol: class NavFeature
tools/a2n/A2NavHarness.java:1328: cannot find symbol: class NavFeature
tools/a2n/A2NavHarness.java:1737: cannot find symbol: class NavFeature

symbol:   class NavFeature
location: package com.chipoodle.devilrpg.entity.nav
```

Offending use:

```java
com.chipoodle.devilrpg.entity.nav.NavFeature.PROPERTY
com.chipoodle.devilrpg.entity.nav.NavFeature.ENV
com.chipoodle.devilrpg.entity.nav.NavFeature.customNavigation()
```

`NavFeature.java` does not exist under `src/main/java/com/chipoodle/devilrpg/entity/nav/`.

No compiler error was reported for `EntityJoinLevelEvent`, `isAddedToLevel()`,
`areEntitiesLoaded(long)` or `ServerLevel.getEntity(UUID)`, but the build cannot reach a green state.

## 6. Tests / lint / diff

```text
GRADLE_TEST: FAIL (exit 1, same seven compile errors)
NAV_PLANNER_STATE_TEST: NOT RUN
ASSERTIONS: N/A
STRICT_LINT: PASS (exit 0)
GIT_DIFF_CHECK: PASS (exit 0)
```

## 7. Integrity

```text
RUNTIME_EXECUTED: false
MINECRAFT_LAUNCHED: false
WORLDS_MODIFIED: false
FIXTURES_MODIFIED: false
PRODUCTION_MODIFIED: false
TEMP_SOURCE_CREATED: false
TEMP_SOURCE_REMOVED: false
```

```text
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

## 8. Recommendation

ChatGPT must resolve the `NavFeature` dependency in the canonical harness. Options: publish the missing
class, or replace those calls with the real production navigation-feature API already present. Then
publish a new HEAD and re-run this exact static gate.

The build routing itself is correct and does not need to be reverted. A2 remains OPEN.
