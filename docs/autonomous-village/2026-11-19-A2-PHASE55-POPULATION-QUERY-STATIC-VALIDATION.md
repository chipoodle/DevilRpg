# A2 Phase55 — population query fix static validation (2026-11-19)

## 1. Result

**BLOCKED — the required temporary harness destination already exists and differs from the canonical
source. Static compilation of the new harness was not possible without overwriting/deleting a tracked
file, which is prohibited.**

- RUN_ID: `phase55-population-static-20261119-1215`
- BRANCH: `feature/autonomous-village`
- INITIAL_HEAD: `6bb467b4a64b0b53888b40bcc9cc258c2acbd967`
- TESTED_HEAD: `73f6f3df8ce4982b29f5cdd88651b44c02eb7eb9`
- NAVPLANNER_BLOB_SHA: `5666e42693fe2e90dff1d2ac1004b59b9b62d7cd` (expected, unchanged)
- A2_HARNESS_BLOB_SHA (canonical): `3205305a10a21ded066c03cb746908e94632e08d`
- BUILD_GRADLE_BLOB_SHA: `45d9a43ba940996cdc17ae4757b618bd0e0d492e`
- LAUNCH_WRAPPER_BLOB_SHA: `2c66908dfffef6b22f9b56797dbada554ab2d8ec`

## 2. Blocking conflict

```text
Destination:
src/main/java/com/chipoodle/devilrpg/debug/A2NavHarness.java

Exists: yes
Tracked in HEAD: yes
Destination blob: 28ea2fb8ce970326c1157046dcb59e3d1cc5892d
Canonical blob:   3205305a10a21ded066c03cb746908e94632e08d
Line diff:        1102
```

The destination is the old Phase54B harness, last changed by `f437f372`; the new canonical source lives
only in `tools/a2n/A2NavHarness.java`. The handoff explicitly says: if the destination exists, do not
overwrite it, preserve it and report the blockage. Therefore:

```text
TEMP_SOURCE_CREATED: false
TEMP_SOURCE_REMOVED: false
```

No file was copied, overwritten or deleted.

## 3. Git / synchronization

```text
Local was 32 commits behind; fast-forward only to 73f6f3df.
Production diff 10719ff8..HEAD in src/build.gradle: empty.
NavPlanner.java blob remains 5666e426….
Untracked artifacts preserved:
.a2-backups/, src/foodTest/, tools/a2n/A2NavHarness.java.partial,
tools/a2n/consultar-c4.ps1, tools/a2n/lanzar-c4.ps1, tools/a5-closure/.
```

## 4. Static review performed

### Gradle trace mapping — PASS by source inspection

`build.gradle` maps both project properties to the runServer JVM:

```gradle
if (providers.gradleProperty('a2nTraceNav').isPresent()) {
    systemProperty 'devilrpg.a2n.traceNav', providers.gradleProperty('a2nTraceNav').get()
}
if (providers.gradleProperty('a2nTraceGlobalSearch').isPresent()) {
    systemProperty 'devilrpg.a2n.traceGlobalSearch',
            providers.gradleProperty('a2nTraceGlobalSearch').get()
}
```

```text
TRACE_NAV_GRADLE_MAPPING: present
TRACE_GLOBAL_SEARCH_GRADLE_MAPPING: present
```

### Wrapper — PASS by source inspection

`tools/a2n/lanzar-phase55-diagnostico.ps1`:

- removes previous `-Ddevilrpg.a2n.traceNav=...` and `traceGlobalSearch=...` values;
- appends both required `=true` options to `JDK_JAVA_OPTIONS`;
- sets `ORG_GRADLE_PROJECT_a2nTraceNav=true` and
  `ORG_GRADLE_PROJECT_a2nTraceGlobalSearch=true`;
- delegates to the local launcher unchanged;
- restores the caller environment in `finally`;
- does not itself set a false value.

```text
WRAPPER_REVIEW: PASS (source only)
```

### Startup safety gate — PASS by source inspection

The canonical harness reads both properties at startup and logs:

```text
[A2N] JVM_TRACE traceNav=... traceGlobalSearch=...
```

In `raid` mode it closes `INVALIDO` before fixture preparation if either property is false.

```text
SERVER_STARTUP_GATE_REVIEW: present (source only)
```

### Canonical API references — source present, not compiled

The canonical harness contains references to:

```text
EntityJoinLevelEvent
isAddedToLevel()
areEntitiesLoaded(long)
level.getEntity(UUID)
```

These could not be compiler-verified because the canonical source was not placed at the compile path.

## 5. Commands

```text
COMPILE_JAVA: NOT RUN — blocked by existing destination
GRADLE_TEST: NOT RUN — same blocker
NAV_PLANNER_STATE_TEST: NOT RUN
STRICT_LINT: PASS (exit 0)
GIT_DIFF_CHECK: PASS (exit 0)
```

Running compile/test against the existing old destination would validate the wrong harness and is
therefore not reported as a pass.

## 6. Preservation

```text
RUNTIME_EXECUTED: false
MINECRAFT_LAUNCHED: false
WORLDS_MODIFIED: false
FIXTURES_MODIFIED: false
PRODUCTION_MODIFIED: false
```

```text
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

## 7. Recommendation

ChatGPT must reconcile the tracked destination before static validation:

1. Replace the tracked `src/main/java/.../debug/A2NavHarness.java` with the canonical
   `tools/a2n/A2NavHarness.java` in a production commit, or define a supported build path that compiles
   the canonical source directly.
2. Publish the new HEAD.
3. Re-run the static gate (`compileJava`, `test`, lint, diff-check) without touching untracked artifacts.

No production correction was made by Harness. A2 remains OPEN.
