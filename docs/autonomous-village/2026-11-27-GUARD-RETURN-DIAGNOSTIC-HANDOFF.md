# Harness handoff — diagnose guard return failure (27-nov-2026)

## Exact target

- Repository: `chipoodle/DevilRpg`
- Branch: `feature/autonomous-village`
- Exact commit: `968d78ffcaac4e345868583cd21f3a59e35e2703`
- Parent: `7d65b74f501a741f0be69fd029cb03e6039b2fd5`
- Minecraft 1.21.1 / NeoForge 21.1.249
- This commit adds diagnostics only; it does not claim to fix the runtime failure.

## Copy-ready task

```text
TASK: Diagnose why Ximeno's guard return does not move. Harness is verification-only.

REPOSITORY
- chipoodle/DevilRpg
- feature/autonomous-village
- Exact target 968d78ffcaac4e345868583cd21f3a59e35e2703
- Prove the tested SHA contains this exact commit.

SAFE SYNC
1. Inspect branch, local HEAD, fetched remote HEAD, upstream, status, untracked files and divergence.
2. Preserve all local-only files, including the temporary probe reported after the previous run, logs, worlds and backups.
3. Never reset, clean, stash, force-push, overwrite, or use git add .
4. If the branch diverged or fast-forward would overwrite local material, stop and report BLOCKED.
5. Fast-forward only if safe; record before/after state and exact tested SHA.

STATIC GATE
Run:
- .\gradlew.bat clean compileJava compileTestJava test policyTest --console=plain --offline
- python tools\lint_aldea.py --strict
- git diff --check
Report exact commands, exit codes, test names/counts, and tested SHA.
If any static gate fails, do not launch runtime.

RUNTIME — SCENARIO A ONLY
1. Create a NEW disposable copy from the known baseline. Do not reuse Run010, Run011, or the previous failed Scenario A copy. Never touch the original player world.
2. Use Ximeno UUID 5b42b9cf-8e30-4c9f-ad16-2ad31491a2e5 if available. Record actual coordinates; do not force or teleport him.
3. Let the normal goal activate return and run for at least 600 ticks, or until physical movement/recovery is established.
4. Capture every relevant log line for this UUID:
   - [GuardReturn]
   - [GuardReturnDiag]
   - [GuardTerrain]
5. Specifically report:
   a) At stuckTicks=60: target, brainTarget, brainMatches, naturalRouteReachable, navNodes, navDone, navReachable.
   b) At subsequent 100-tick diagnostics: exact WALK_TARGET and whether the recovery gate is closed.
   c) Every GuardTerrain plan attempt: accepted plan with exact block list and landing, OR rejected-plan counters and origin/target/feet/head/support/fluid/chunk data.
   d) Whether the Navigation path is absent, done, reachable, or changes over time.
   e) Position samples and movement delta; do not infer movement from WALK_TARGET alone.
   f) Whether any blocks changed, and whether RETURNING_TAG remains set.
6. If no diagnostic line appears, prove why the relevant code path did not execute; do not infer a cause from missing logs.
7. Do not manually alter the entity, position, Brain memories, blocks, or persistent tags. No teleport, forced movement, or hand-authored recovery plan.

WORLD INTEGRITY
- Hash original world before/after; require identical manifests.
- Keep all disposable copies and previous runs preserved.
- Record block-level diff for the new disposable copy.

REPORT
RESULT: PASS | FAIL | BLOCKED | PARTIAL
BRANCH:
TESTED_SHA:
IMPLEMENTATION_ANCESTOR:
WORKTREE_BEFORE/AFTER:
STATIC / COMPILE / TESTS / POLICY_TESTS / STRICT_LINT / DIFF_CHECK:
SCENARIO_A:
- Ximeno UUID and actual position samples:
- [GuardReturnDiag] threshold line:
- brainTarget / brainMatches:
- naturalRouteReachable:
- Navigation path state:
- GuardTerrain plan accepted/rejected evidence:
- physical movement delta:
- blocks changed:
- RETURNING_TAG after 600 ticks:
ORIGINAL_WORLD_HASH_BEFORE/AFTER:
WORLD_DIFF:
LOGS:
ROOT_CAUSE: CONFIRMED | INCONCLUSIVE
PRODUCTION_MODIFIED_BY_HARNESS: false

Do not run B–F or priority negatives in this campaign. Do not modify production, tests, build scripts, or documentation. Preserve the temporary probe and all prior artifacts.
```

## Interpretation rules

- If `brainMatches=false`, include the exact brainTarget and strategic target. Do not automatically relax exact equality; ChatGPT will decide whether it is an adjusted stand cell or a competing Brain task.
- If `naturalRouteReachable=true`, do not build; capture the live Navigation path and movement evidence.
- If `brainMatches=true` and natural route is false, expect a GuardTerrain plan attempt. A rejected-plan log identifies why no plan exists.
- If a plan is accepted, verify the exact planned blocks and landing before judging placement/movement.
- A missing `[GuardTerrain]` line alone is not proof that the planner was not called.

Harness must not change production source or acceptance criteria.
```

