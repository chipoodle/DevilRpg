# A2 Phase55 — canonical harness build routing (2026-11-19)

## Status

**IMPLEMENTED — STATIC VALIDATION REQUIRED. No compile, tests, lint, or runtime were executed by ChatGPT.**

The prior Harness batch `phase55-population-static-20261119-1215` was correctly BLOCKED because the historical
source path `src/main/java/com/chipoodle/devilrpg/debug/A2NavHarness.java` already exists, is tracked, and
contains a distinct legacy harness. Its blob was
`28ea2fb8ce970326c1157046dcb59e3d1cc5892d`; the canonical Phase55 source at
`tools/a2n/A2NavHarness.java` was `3205305a10a21ded066c03cb746908e94632e08d`.

Replacing the legacy file would discard twelve methods found only in that source, including BREAK runtime,
BREAK edge-excluded, and NAV backtrack scenarios. Therefore neither source is overwritten or deleted.

## 1. Build routing correction

`build.gradle` now recognizes:

- `-Pa2nDiagnostics=true`: explicitly enables the debug source set, as before.
- `-Pa2nPopulationQueryHarness=true`: requires diagnostics mode, excludes the tracked legacy
  `debug/A2NavHarness.java` only from this opt-in compilation, and adds `tools/a2n/` as a source root.
  The canonical class keeps its existing package and class name, so the launcher uses the Phase55 implementation
  without copying files into the production source tree.
- When `a2nPopulationQueryHarness` is absent/false, diagnostic builds retain the legacy source selection.
- Normal builds still exclude the debug source tree.

The legacy source remains tracked and unchanged. `NavPlanner.java`, gameplay/navigation production Java,
worlds, fixtures, acceptance criteria, and navigation budgets are unchanged.

## 2. Launcher correction

`tools/a2n/lanzar-phase55-diagnostico.ps1` now sets these Gradle project environment properties for its child
launcher:

```text
ORG_GRADLE_PROJECT_a2nDiagnostics=true
ORG_GRADLE_PROJECT_a2nPopulationQueryHarness=true
ORG_GRADLE_PROJECT_a2nTraceNav=true
ORG_GRADLE_PROJECT_a2nTraceGlobalSearch=true
```

It continues to set both JVM trace flags in `JDK_JAVA_OPTIONS`, delegates to the existing ignored local
launcher without editing it, and restores all four project variables plus `JDK_JAVA_OPTIONS` in `finally`.
This makes the Phase55 runtime wrapper select the same canonical source that the static compile must validate.

## 3. Required Harness validation — STATIC ONLY

Synchronize fast-forward-only to the implementation HEAD recorded in `NEXT-SESSION.md`. Preserve all local
changes, untracked files, backups and worlds. Confirm:

- The legacy source blob is still `28ea2fb8ce970326c1157046dcb59e3d1cc5892d` (or report the actual
  unchanged blob if the repository baseline differs).
- The canonical source is still `3205305a10a21ded066c03cb746908e94632e08d`.
- `NavPlanner.java` blob is unchanged from `5666e42693fe2e90dff1d2ac1004b59b9b62d7cd`.
- No copy, overwrite, deletion, or temporary source file is needed.

Run exactly:

```powershell
.\gradlew.bat compileJava -Pa2nDiagnostics=true -Pa2nPopulationQueryHarness=true -Pa2nTraceNav=true -Pa2nTraceGlobalSearch=true
.\gradlew.bat test -Pa2nDiagnostics=true -Pa2nPopulationQueryHarness=true -Pa2nTraceNav=true -Pa2nTraceGlobalSearch=true
python tools\lint_aldea.py --strict
git diff --check
```

Record each command, exit code, exact tested SHA, and the compile output proving the canonical Phase55 source
was selected. Confirm that NeoForge 21.1.249 resolves `EntityJoinLevelEvent`,
`isAddedToLevel()`, `areEntitiesLoaded(long)`, and `ServerLevel.getEntity(UUID)`.

**Do not run Minecraft, the wrapper, runServer, A2, C4, Run2, or Full12K.** The previous runtime authorization
was consumed. Even if all static checks pass, stop and return the report; a new runtime requires fresh,
explicit user authorization.

A2 remains OPEN. This implementation is pending exact-SHA static validation.
