# Workflow Contract — Shared ChatGPT and DeepSeek Harness Branch

**Status:** Permanent and mandatory  
**Repository:** `chipoodle/DevilRpg`  
**Branch:** `feature/autonomous-village`  
**Platform:** Minecraft 1.21.1 / NeoForge

The repository branch is the shared communication channel and durable source of truth. This contract
supersedes the obsolete local-first/task-orchestrator workflow previously described in this file.
The detailed permanent role policy is `CHATGPT-COLLABORATION-POLICY.md`; this file defines the
day-to-day operating loop.

## 1. Single shared branch

- Both agents work from the same branch: `feature/autonomous-village`.
- ChatGPT publishes source changes, implementation reports and corrected continuation documentation
  directly to this branch.
- Harness synchronizes this branch into its local worktree, runs automated validation and publishes
  dated test reports/evidence documentation back to the same branch.
- The user may paste a Harness report into ChatGPT. ChatGPT must also inspect the corresponding
  repository report and current source; the pasted report is a handoff, not a substitute for source
  review.
- The local Harness worktree exists to execute tests and preserve local runtime artifacts. It is not
  a separate development branch or a competing source of truth.

## 2. ChatGPT owns development

For every reported defect, ChatGPT must:

1. Read the latest branch HEAD, collaboration policy, continuation checkpoint and relevant reports.
2. Review Harness's report, including exact tested SHA, logs, metrics, first failing transition and
   what is proven versus unknown.
3. Inspect the current implementation directly; do not assume the report's diagnosis is correct.
4. Identify the root cause and implement the correction directly on GitHub in
   `feature/autonomous-village`.
5. Add/update focused automated tests where appropriate. Mark them **authored, not run** until
   Harness executes them.
6. Review the complete diff and ensure no unrelated or newer work is overwritten.
7. Publish a descriptive commit and a handoff containing parent/implementation SHA, changed files,
   behavior, invariants, tests authored, and exact Harness validation request.
8. Update `NEXT-SESSION.md`, `CONTINUAR.md` and relevant technical documentation so a new session
   can continue without chat history.

ChatGPT is the implementation owner. It must not ask Harness or the user to implement ordinary
production source corrections that belong to ChatGPT.

## 3. Harness owns testing and evidence

For every validation task, Harness must:

1. Read the current collaboration and continuation documents before starting.
2. Inspect local branch/HEAD, remote HEAD, dirty/untracked files and divergence.
3. Synchronize the exact requested ChatGPT commit using safe fast-forward where possible; preserve
   local-only artifacts and stop on conflicts.
4. Run the requested Gradle tests, strict lint, diff checks, diagnostics compilation and automated
   Minecraft scenarios.
5. Use the automated/headless test path. Do not require user interaction, foreground the Minecraft
   client or leave tests dependent on a visible GUI unless explicitly required by an authorized test.
6. Measure observable behavior and capture logs, positions, state transitions, timing, before/after
   state and hashes as required by the scenario.
7. Classify each result accurately as PASS, FAIL, BLOCKED or UNKNOWN/NOT RUN.
8. Write a new dated report with exact tested SHA, run ID, commands, exit codes, measurements,
   artifact paths, unresolved issues and next action.
9. Publish the report and relevant continuation/evidence updates to the same shared branch.
10. Return the report to the user for delivery to ChatGPT as needed.

Harness is the execution, measurement and evidence owner. Harness must not independently implement
production source fixes or change acceptance criteria to make a run pass. It may maintain test
orchestration and diagnostic scripts when those changes do not alter production behavior.

## 4. ChatGPT reviews Harness documentation and reports

This review is mandatory after every Harness handoff, not optional:

- ChatGPT reads the new report and all Harness-authored documentation changes from the shared branch.
- It checks reported SHAs, test claims, metrics, event traces, paths, hashes and conclusions for
  consistency with the raw evidence and actual source.
- It corrects inaccurate interpretations, stale links, contradictory status, missing caveats and
  continuation instructions directly on the shared branch.
- It preserves Harness's original dated report and raw result. Corrections or reinterpretations go
  into a new review note or current checkpoint; historical FAIL/BLOCKED results are never rewritten.
- ChatGPT then decides the next implementation or validation step and records it in the checkpoint.
- If the report and pasted chat text disagree with the repository, inspect commit chronology and
  evidence before deciding; do not silently choose either version.

## 5. Shared-branch synchronization and conflict safety

Before any publication:
- Read the latest branch head and target file's current blob SHA.
- Confirm the intended parent/head and inspect intervening commits.
- Update only the exact file versions that were reviewed.
- Never move the branch backward or overwrite newer Harness/ChatGPT commits.

Harness must record `git status --short`, current branch, local HEAD, fetched remote HEAD, recent log
and divergence before synchronization. If local/remote histories diverge or local-only work could
be overwritten, stop and report.

Prohibited unless the user explicitly authorizes a specific recovery plan:
- `git reset` (especially `--hard`), `git clean`, `git stash`, force-push.
- Indiscriminate revert or `git add .`.
- Overwriting diagnostic worlds, local-only logs, scripts, partial files or backups.

Do not commit secrets or complete world directories/large archives. Preserve local artifacts and
publish their paths, hashes and concise manifests.

## 6. Result vocabulary and evidence

- **PASS:** the specified command/criterion completed successfully on the exact identified SHA.
- **FAIL:** the test completed and demonstrated the criterion was not met.
- **BLOCKED:** the test could not validly run or complete due to environment, setup, sync or
  instrumentation.
- **UNKNOWN / NOT RUN:** sufficient evidence does not exist.

Always distinguish code reviewed from compiled, tests authored from executed, unit tests from runtime,
build success from gameplay acceptance, perimeter contact from strict wall entry, and a single passing
run from stable acceptance. A missing event alone does not prove a specific cause.

Every Harness report must record:
- Date/time, environment, repository, branch, initial/final local HEAD, remote HEAD and tested SHA.
- Working-tree and synchronization state.
- Commands, exit codes, test names/results and log paths.
- Runtime scenario, seed/world identity, actor indices/UUIDs, coordinates, transitions and traces.
- Required before/after hashes, local-only artifacts preserved, blockers and exact next action.

## 7. Required checkpoint updates

After each meaningful validation batch, Harness publishes a new dated report and updates the current
checkpoint with its raw result. ChatGPT reviews and reconciles the documentation, then updates:

- `docs/autonomous-village/NEXT-SESSION.md` — concise current state, exact SHA and next action.
- `docs/autonomous-village/CONTINUAR.md` — detailed continuation state.
- `docs/CONTINUAR.md` and `docs/PENDIENTE.md` when project-wide status/task indexes change.
- Relevant technical/design/acceptance documents when the evidence warrants it.

Every new session starts by reading `CHATGPT-COLLABORATION-POLICY.md`, `NEXT-SESSION.md`, this
contract, both continuation indexes, and every report explicitly referenced by the current checkpoint.
Verify live branch state; never trust an old chat SHA blindly.

## 8. Autonomous-village gates

Continue honoring the current A2/C4 acceptance contracts and world-preservation rules. Do not run
A2 Run 2 before the documented Run 1 gate is met. Do not launch the full 12,000-run campaign until
five consecutive strict 8/8 `DENTRO-PARED` runs are achieved. Never relax thresholds, budgets,
collision/support predicates or cleanliness criteria to manufacture PASS.

---

**Non-negotiable:** ChatGPT develops, commits and reviews/corrects documentation. Harness tests,
measures and publishes evidence. Both communicate through the same repository branch. The user
delivers Harness reports in chat when needed; no one should require the user to perform code edits or
manual Minecraft test operations.
