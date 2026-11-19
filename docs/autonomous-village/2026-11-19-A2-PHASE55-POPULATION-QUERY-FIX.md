# A2 Phase55 — population census and trace launcher correction (2026-11-19)

## Status

**IMPLEMENTED — STATIC VALIDATION PENDING. No runtime executed or authorized.**

The blocked run `a2n-20261119-114535` demonstrated a mismatch between the fixture's AABB population query (15 villagers / 11 professionals) and the saved disposable world (which contains a live SHEPHERD). The cause inside Minecraft's entity-section/query visibility remains unknown. This change removes that AABB query as the authoritative source for the A2 fixture census and adds diagnostics to explain any discrepancy.

- Prior runtime: `a2n-20261119-114535` — BLOCKED before attacker spawn.
- Prior production SHA: `10719ff82465a0d8997cb2af3af23c9f71292b19`.
- Prior documentation HEAD: `6bb467b4a64b0b53888b40bcc9cc258c2acbd967`.
- New implementation commits:
  - `cdfcd638963a35045ab468250c1519673eb482f7` — capture fixture villagers independently of AABB query.
  - `d11cae8c53ec60f94ac5d6e3b5cf86318ea59e5e` — forward global-search trace property to runServer JVM.
  - `4343753cfb829e1ff5b4585e6ef33dc9f8761ff2` — retain captured villager references through raid audit.
  - `87f6e0875149bf6492b2a2349eca2513dce6d0b3` — require tracked captured villagers in fixture census.
  - `8aa7f1df340f9adf9fefcec21733130712909a6e` and `565d716b9c43845811b499ac53761a2f4512930e` — add and harden a tracked PowerShell wrapper that guarantees both JVM and Gradle trace properties.

## 1. Population census correction

File: `tools/a2n/A2NavHarness.java`.

The raid fixture now listens to NeoForge `EntityJoinLevelEvent` only while `VillageManager.preGenerate` and the fixture maintenance hook execute. It records exact Villager object references by UUID. The event listener only captures the entity reference and basic entity fields; it does not query chunks or mutate the world.

The authoritative fixture roster is built from those captured references after generation/maintenance. A captured villager is eligible only when it:
- intersects the fixture AABB,
- is alive and not removed,
- and is demonstrably tracked: either `isAddedToLevel()` is true or `level.getEntity(UUID)` returns the exact captured object.

The existing `getEntitiesOfClass(Villager.class, AABB)` query remains as a diagnostic comparison, not as the roster source. The harness logs:
- each join event (UUID, profession, position, loadedFromDisk, cancellation state at listener time),
- each captured entity's alive/removed/addedToLevel state,
- every entity captured by join event but omitted by the AABB query,
- UUID lookup presence, entity-section load state (`areEntitiesLoaded`) and chunk-loaded state.

The captured references are also retained for the post-raid worker audit. If `level.getEntity(UUID)` cannot resolve a worker, the harness falls back to the captured reference and still rejects removed/dead entities. Defense and job-site audit enumeration now use the same captured fixture roster rather than a second AABB census.

This prevents a spatial AABB query omission from silently turning a successfully joined, live fixture worker into a missing roster slot. It does **not** change production village population logic or assert that the underlying Minecraft visibility anomaly has been explained.

NeoForge 1.21.1 documents that `EntityJoinLevelEvent` fires when an entity joins a Level through `LevelWriter.addFreshEntity` / `PersistentEntitySectionManager.addNewEntity`; it can fire before the underlying chunk reaches FULL, so the listener intentionally performs no chunk/world access. API reference: https://aldak.netlify.app/javadoc/1.21.1-21.1.x/net/neoforged/neoforge/event/entity/entityjoinlevelevent

## 2. Trace JVM propagation

File: `build.gradle`.

The `runServer` configuration now accepts Gradle project property:

```text
-Pa2nTraceGlobalSearch=true
```

and maps it directly to child-server JVM system property:

```text
devilrpg.a2n.traceGlobalSearch=true
```

The existing `a2nTraceNav` mapping remains available for `devilrpg.a2n.traceNav`. Use the tracked wrapper `tools/a2n/lanzar-phase55-diagnostico.ps1` for a future authorized raid run. It removes stale values for the two trace options, sets both to `true` in `JDK_JAVA_OPTIONS`, sets both `ORG_GRADLE_PROJECT_*` values (which Gradle reads as project properties), delegates to the existing ignored local launcher, then restores the caller's environment. The wrapper sets these JVM options:

```text
-Ddevilrpg.a2n.traceNav=true
-Ddevilrpg.a2n.traceGlobalSearch=true
```

Future authorized invocation:

```powershell
.\tools\a2n\lanzar-phase55-diagnostico.ps1 -Modo raid -LimiteMinutos 18
```

At `ServerStartedEvent`, A2NavHarness logs the effective values from the actual server process:

```text
[A2N] JVM_TRACE traceNav=true traceGlobalSearch=true ...
```

In `raid` mode, if either property is false, the harness terminates as INVALIDO before preparing or modifying the fixture. This avoids spending a runtime authorization on an uninstrumented run.

The generated/local launcher `build/a2n/lanzar-a2n.ps1` remains untouched. The tracked wrapper delegates to it without editing it. The server's `JVM_TRACE` line is the authoritative effective-value check.

## 3. Scope and invariants

- Changed code/configuration files: `tools/a2n/A2NavHarness.java`, `build.gradle`, `tools/a2n/lanzar-phase55-diagnostico.ps1`.
- No production navigation source changed.
- No changes to A2/C4 acceptance thresholds, worker professions, spawn positions, world geometry, chunk forcing, collision, support, protection, OFFSETS, budgets, cooldowns or A*.
- No world, fixture data or local-only artifacts changed.
- No Minecraft process launched; no runtime/test/compile/lint executed by ChatGPT.
- The prior runtime authorization remains consumed. A new runtime requires explicit user authorization.

## 4. Required Harness validation (STATIC ONLY)

On the exact current branch HEAD:

1. Safely sync by fast-forward only; preserve all local/untracked artifacts and worlds.
2. Verify branch and exact HEAD; confirm the only non-documentation changes since `6bb467b4a64b0b53888b40bcc9cc258c2acbd967` are `build.gradle`, `tools/a2n/A2NavHarness.java`, and `tools/a2n/lanzar-phase55-diagnostico.ps1`.
3. Copy canonical `tools/a2n/A2NavHarness.java` to the documented temporary compile location only as the established workflow requires.
4. Run:
   - `gradlew.bat compileJava` with `-Pa2nDiagnostics=true` as required by the launcher workflow.
   - `gradlew.bat test`.
   - `python tools/lint_aldea.py --strict`.
   - `git diff --check`.
5. Confirm the exact `EntityJoinLevelEvent`, `isAddedToLevel`, `areEntitiesLoaded`, and Gradle system-property APIs compile on NeoForge 21.1.249.
6. Do not launch Minecraft, create a world, or execute A2/C4/Full12K. This is static validation only.
7. Report exact SHA, commands, exit codes, test output, worktree status, and preserved untracked files.

After static PASS, stop. The next runtime diagnostic requires a new explicit user authorization. A2 remains OPEN; no conclusion about IDX3/IDX5 or global A* can be drawn from the blocked run.
