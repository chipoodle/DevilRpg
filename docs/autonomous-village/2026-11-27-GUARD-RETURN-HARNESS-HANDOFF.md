# Harness handoff — stranded guard return/reintegration (27-nov-2026)

## Exact target

- Repository: `chipoodle/DevilRpg`
- Branch: `feature/autonomous-village`
- Production implementation commit: `9632c9df61438fc39f1a32fda83a8affbc691be0` (includes the last source change: restore the returning guard goal after reload).
- The final documentation commit will be a descendant; sync the current branch tip and record the exact tested SHA. Do not test an earlier base.
- Minecraft 1.21.1 / NeoForge 21.1.249.
- ChatGPT did not run Gradle, unit tests, lint, dedicated server, client or runtime.

## Copy-ready Harness prompt

```text
TASK: Validate stranded guard return and militia reintegration. Harness is verification-only; MUST NOT modify production source, tests, build scripts, or documentation.

REPOSITORY
- chipoodle/DevilRpg
- branch feature/autonomous-village
- expected implementation commit 9632c9df61438fc39f1a32fda83a8affbc691be0
- Sync the current remote branch tip (documentation-only descendants may exist). Record exact tested SHA and prove it contains the implementation commit.

SAFE SYNC / WORKTREE
1. Inspect branch, HEAD, status, upstream, local/remote divergence and untracked files before any operation.
2. Preserve every local edit, untracked file, diagnostic world and log.
3. Never reset, clean, stash, force-push, checkout over edits, or use git add .
4. If worktree is dirty or diverged, stop and report BLOCKED with evidence; do not overwrite.
5. Fast-forward only when safe. Record before/after SHA and diff.
6. Never modify the original player world. Create a NEW disposable copy for every runtime campaign; do not reuse Run010/Run011 world copies.

STATIC GATES (exact tested SHA)
- Run ./gradlew clean compileJava compileTestJava test (or repository's canonical wrapper commands if task aliases differ; report exact commands).
- Run the dedicated village/guard policy test suites and all project-required test suites.
- Run strict lint and git diff --check.
- Inspect source and test reports for:
  a) GUARD_RETURNING_TAG is persisted in Villager PersistentData.
  b) Returning guards are excluded from dynamic militia candidates and cleanup/desalistar.
  c) The existing guard goal is re-ensured for returning guards during repartition after load.
  d) No second movement goal was added; VillagerGuardGoal remains the sole guard MOVE/LOOK owner at SECURITY priority.
  e) No Navigation.moveTo, teleport, forced position, or bypass of caminarHacia was introduced.
  f) No changes to A2/C4 acceptance criteria, thresholds, budgets, or protections.
- If static gates fail, do not proceed to production runtime; report FAIL with first root cause and complete logs.

RUNTIME — FRESH WORLD COPY ONLY
Prepare a fresh copy from a known baseline. Preserve source world hashes. Instrument guard UUID, PersistentData, goal-selector registration/running state, Brain WALK_TARGET, block position, feet/head fluid, support block, current destination, path reachability, and all [GuardReturn]/[GuardTerrain] logs.

Scenario A — reproduce the stranded guard
- Use the known Ximeno guard UUID 5b42b9cf-8e30-4c9f-ad16-2ad31491a2e5 if available; otherwise create an equivalent dynamic guard in a water depression.
- Start at/near Run011 coordinates: feet 637,62,577; village center 566,64,566. Record actual test coordinates; do not force these coordinates if the world differs.
- Verify no safe patrol post exists (puntoDeGuardia null), current feet/head/support safety, and the pre-fix condition would have left canUse=false / WALK_TARGET empty.
- Verify return state is set and destination is VillageManager.casillaDeLaCalle, not a fictitious patrol post or center coordinate.
- Verify goal remains the existing guard goal; no competing rescue goal claims MOVE.
- Verify ordinary rest schedule does not suppress rescue.

Scenario B — natural path first, terrain recovery only when needed
- If a natural route to the street cell is reachable, guard must use it without block placement. Also create a second case where a patrol post exists but is unreachable: after 60 ticks without progress and with Brain still targeting that post, the guard must abandon that post and switch to the street cell before any terrain construction. If the patrol route is reachable, it must not switch merely because straight-line distance temporarily increases.
- If no natural route reaches and the Brain continues to target the street cell, verify stuck trigger is 60 ticks and existing GuardTerrainRecovery is invoked.
- Record full plan before any placement: landing, each block coordinate, support depth, block count, protection/chunk/block-entity checks.
- Confirm each placement is incremental and at existing cooldown; verify all resulting blocks are allowed and the villager walks using Brain WALK_TARGET / VillageManager.caminarHacia.
- Verify no teleport, no direct Navigation.moveTo, no forced position, no protected block edits.
- If no valid plan exists, verify fail-closed/backoff: no blocks changed and no false completion.

Scenario C — militia redistribution while returning
- Trigger normal population/militia repartition repeatedly during the return.
- Assert GUARD_TAG, GUARD_TYPE_TAG, GUARD_INDEX_TAG, equipment, learned attributes and synchronized guard marker remain unchanged.
- Assert guard is not counted as a newly available militia candidate and is not dynamically reassigned or deslisted while RETURNING_TAG=true.
- Assert no duplicate guard goal is installed.

Scenario D — return completion and reintegration
- Verify completion only when the villager is on a safe supported, dry standing cell, inside the wall, and close to the street target.
- Verify RETURNING_TAG is removed exactly once.
- Verify plaza/street is not treated as a patrol post: goal returns without pretending the villager already reached its next patrol destination.
- Run the next normal repartition. It alone decides whether the villager remains a guard or returns to civilian/profession assignment according to population and fixed job quotas.
- Verify no fictitious index/type or permanent guard assignment is created.

Scenario E — save/reload during rescue
- Save while RETURNING_TAG=true and while the villager is still outside/unsafe.
- Reload the world. Verify PersistentData still has RETURNING_TAG and GUARD_TAG/type/index.
- Verify VillageManager.repartirGuardia re-adds/ensures the existing VillagerGuardGoal even though the returning villager is excluded from dynamic candidates.
- Verify it resumes returning without duplicate goal, teleport or reset of the guard identity.
- Save/reload again after successful completion; verify the tag stays cleared and normal distribution proceeds.

Priority/negative controls
- Combat: introduce a hostile within guard combat radius; combat must run before return movement.
- Equipment: when eligible equipment exists, equip flow may take priority/pause recovery; after completion return resumes.
- March: when marchDe is active, march destination remains authoritative; no return destination may override it.
- Flee/emergency and critical needs retain their existing higher GoalSelector priority.
- Healthy guard inside the perimeter with a valid patrol post continues ordinary patrol.
- A guard standing safely inside the perimeter with no available patrol post must not enter an endless return/retry loop.
- Invalid terrain: lava, non-water hazardous fluid, solid feet/head, missing support, protected cell, unloaded chunk, block entity, or no valid landing. Verify no illegal placement and no false success.
- Verify no change to A2/C4 and no regression in existing guard combat, corral, arboleda, training or equipment.

WORLD INTEGRITY
- Hash original world before/after; must be identical.
- Record changed files in disposable world copy and block-level diff.
- Never save runtime test state back to the original world.

REPORT FORMAT
RESULT: PASS | FAIL | BLOCKED | PARTIAL
BRANCH:
TESTED_SHA:
IMPLEMENTATION_ANCESTOR:
WORKTREE_BEFORE:
WORKTREE_AFTER:
STATIC: PASS/FAIL/NOT RUN
COMPILE:
TESTS:
POLICY_TESTS:
STRICT_LINT:
DIFF_CHECK:
RUNTIME_SCENARIO_A:
RUNTIME_SCENARIO_B:
RUNTIME_SCENARIO_C:
RUNTIME_SCENARIO_D:
SAVE_RELOAD:
PRIORITY_NEGATIVES:
A2_C4_UNCHANGED:
ORIGINAL_WORLD_HASH_BEFORE/AFTER:
WORLD_DIFF:
LOGS:
ROOT_CAUSE:
PRODUCTION_MODIFIED_BY_HARNESS: MUST BE false

Every PASS claim must cite exact log/report evidence and tested SHA. If any scenario cannot run, mark BLOCKED/NOT RUN; do not infer PASS. Do not modify production to make tests pass; return findings to ChatGPT.
```

## Related evidence and design

- `docs/autonomous-village/2026-11-27-GUARD-TERRAIN-RECOVERY-RUN011.md`
- `docs/autonomous-village/2026-11-27-GUARD-RETURN-AND-REINTEGRATION.md`
- `docs/autonomous-village/2026-11-27-GUARD-POST-FALLBACK-FIX.md`

## Current status

Implementation and documentation are published, but validation is **NOT RUN**. This handoff is not a PASS claim. Harness is the next actor.
