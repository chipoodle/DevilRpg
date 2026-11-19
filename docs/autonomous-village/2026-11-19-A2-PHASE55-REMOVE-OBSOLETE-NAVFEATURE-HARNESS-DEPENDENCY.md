# A2 Phase55 — remove obsolete NavFeature harness dependency (2026-11-19)

## Status

**IMPLEMENTED — STATIC VALIDATION REQUIRED. ChatGPT did not run compile, tests, lint, or runtime.**

Harness run `phase55-routing-static-20261119-1400` proved that the Gradle source routing works: javac selected
`tools/a2n/A2NavHarness.java`, not the tracked legacy source. Compilation then failed at seven references to
`com.chipoodle.devilrpg.entity.nav.NavFeature`. The class is absent from the current production source tree.

## 1. Root cause confirmed from migration record

The missing type is not an accidentally omitted current production class. The authoritative migration record
`docs/autonomous-village/2026-11-15-NAVEXECUTOR-SINGLE-AUTHORITY-MIGRATION.md` explicitly records:

- `NavFeature` was deleted.
- The old `-Ddevilrpg.nav.custom=true` feature switch and fallback to `AggressiveZombiePathPlanner` were removed.
- `NavExecutor` became the sole directed-movement authority.
- `AggressiveZombieEntity` constructs a `NavExecutor` in its constructor and ticks it on every server-side
  `aiStep`.
- Vanilla `PathNavigation` remains only as the internal actuator used by `NavExecutor`.

Current source independently confirms the migration record:
- `AggressiveZombieEntity.java`: constructor creates `new NavExecutor(this, this.navPolicies)`.
- `AggressiveZombieEntity.java`: server-side `aiStep()` calls `this.navExecutor.tick()`.
- `NavFeature.java` does not exist in the current source tree.

Therefore recreating `NavFeature` or restoring its old property/environment toggle would reintroduce an obsolete
abstraction and misrepresent the current architecture. The Phase55 harness only needs to report which movement
architecture is in effect; it must not control or gate production navigation.

## 2. Focused harness correction

File: `tools/a2n/A2NavHarness.java`

Removed all compile-time references to:
- `NavFeature.PROPERTY`
- `NavFeature.ENV`
- `NavFeature.customNavigation()`

The raid setup now logs:

```text
NAVCUSTOM navCustomEfectivo=true modo=NavExecutor-single-authority featureFlag=eliminado
```

This is a description of the current source architecture, not a runtime feature toggle. The trace header now
records:

```text
navCustom=NavExecutor-single-authority
```

No new production class was created. No production Java source was changed. No behavior, goal, navigation
policy, budget, collision/support/protection rule, A2/C4 criterion, fixture, or world was modified.

## 3. Git

- Parent checkpoint: `bc5e780037cc991d87afe6c2d03a1df9bc9e458b`
- Source implementation commit: `a029e4eb790109df94acb1e07f8cac52f8b88ff8`
- Source file blob after correction: `914d26da4f9a553f43936812ddc303ed56a24243`
- Source file before correction: `3205305a10a21ded066c03cb746908e94632e08d`
- `NavPlanner.java` remains blob `5666e42693fe2e90dff1d2ac1004b59b9b62d7cd`.
- Legacy `src/main/java/com/chipoodle/devilrpg/debug/A2NavHarness.java` remains untouched at blob
  `28ea2fb8ce970326c1157046dcb59e3d1cc5892d`.
- Gradle source routing and Phase55 wrapper remain unchanged.

## 4. Required next action — Harness STATIC ONLY

Synchronize fast-forward-only to the exact HEAD in `docs/autonomous-village/NEXT-SESSION.md`. Preserve all
local-only changes, untracked files, backups and worlds. Stop on divergence or any overwrite risk.

Run the same static gate with the canonical source selector:

```powershell
.\gradlew.bat compileJava --info -Pa2nDiagnostics=true -Pa2nPopulationQueryHarness=true -Pa2nTraceNav=true -Pa2nTraceGlobalSearch=true
.\gradlew.bat test --info -Pa2nDiagnostics=true -Pa2nPopulationQueryHarness=true -Pa2nTraceNav=true -Pa2nTraceGlobalSearch=true
python tools\lint_aldea.py --strict
git diff --check
```

Verify that:
- javac compiles `tools/a2n/A2NavHarness.java`;
- no `NavFeature` symbol reference remains in that canonical source;
- NeoForge API calls from the population fix compile;
- the 54-assertion `NAV_PLANNER_STATE_TEST` and any other registered policy tests actually execute;
- legacy source and `NavPlanner.java` blobs remain unchanged;
- no production source is modified by Harness.

Record exact tested SHA, commands, exit codes, test/assertion results, logs, worktree state and untracked preservation.

**STATIC ONLY.** Do not run Minecraft, wrapper, `runServer`, A2, C4, Run2, or Full12K. The prior runtime
authorization is consumed. Stop after static validation even if it passes; a new runtime requires fresh,
explicit user authorization.

A2 remains OPEN.
