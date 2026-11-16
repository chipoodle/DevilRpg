# Workflow Contract — ChatGPT and Harness

**Status:** Permanent collaboration contract  
**Repository:** `chipoodle/DevilRpg`  
**Branch:** `feature/autonomous-village`  
**Platform:** Minecraft 1.21.1 / NeoForge

This document defines the division of work between ChatGPT and Harness. It is written so a completely new session of either agent can resume from repository evidence without depending on chat history.

## 1. Operating model

The workflow is a strict implementation–verification loop:

1. **ChatGPT owns source-code changes.** It investigates the reported defect, reviews the actual repository source, designs the correction, edits files directly on GitHub in `feature/autonomous-village`, reviews its diff, and publishes commits.
2. **Harness owns local execution and empirical verification.** It synchronizes the published branch into the local working copy, preserves local-only material, runs builds/tests/lints, operates the automated Minecraft test harness, measures runtime behavior, captures logs/evidence, and writes reports.
3. **Harness does not independently implement or alter production source code.** If a test exposes a defect or the implementation needs adjustment, Harness documents a reproducible finding and returns the evidence to ChatGPT. ChatGPT makes the source correction on GitHub.
4. **ChatGPT does not claim local execution.** Unless an execution result is explicitly returned by Harness or another verifiable CI system, ChatGPT must say that tests/runtime have not been run. A code review, static inspection, or added test is not a passing test result.
5. **Harness does not treat a ChatGPT commit as validated.** A change remains pending until Harness has fetched that exact commit and reports the results against its SHA.

The loop repeats: **ChatGPT implements → Harness validates and measures → Harness documents evidence → ChatGPT reviews and corrects → Harness validates the new SHA.**

## 2. ChatGPT responsibilities

For every implementation task, ChatGPT must:

- Read this contract, `CHATGPT-COLLABORATION-POLICY.md`, `NEXT-SESSION.md`, continuation indexes, and the relevant current/dated reports before acting.
- Inspect the actual current source and compare it with the latest Harness evidence. Do not assume a historical report still describes the current code.
- Verify the current remote branch head and its ancestry before writing. Do not overwrite a commit or work published after the last checkpoint.
- Make production source changes directly on GitHub, only on `feature/autonomous-village`; do not ask Harness to implement the same correction.
- Keep changes focused and review the complete diff, including tests and documentation.
- Add or update automated tests where appropriate, but distinguish tests authored from tests executed.
- Avoid changing acceptance criteria, budgets, cooldowns, action ranges, collision/support rules, protection behavior, or other invariants merely to obtain a passing result.
- Publish meaningful commits with descriptive messages.
- Update the handoff documentation with the exact implementation SHA, what changed, what remains unverified, and Harness's next validation task.
- Report commit links and exact SHAs. Never say a local file, runtime, or command was changed/executed unless that action is actually confirmed.

## 3. Harness responsibilities

For every validation task, Harness must:

- Read this contract and the current continuation documents before starting.
- Inspect local branch, local HEAD, remote HEAD, working tree, untracked files, and divergence before syncing.
- Fetch the latest remote branch and integrate only by a safe fast-forward when possible.
- Preserve all local-only changes, scripts, partial files, diagnostic outputs, backups, and worlds. Never use destructive cleanup to make synchronization easier.
- Validate the exact ChatGPT implementation SHA, not merely a similarly named or ancestor commit.
- Run the requested Gradle tests, strict lint, diff checks, and focused runtime scenarios. Record the exact command, timestamp, exit code, tested SHA, and relevant output.
- Use the automated/headless Harness path. Do not require user interaction, bring the Minecraft client to the foreground, or leave the test dependent on a visible GUI unless a test explicitly requires it and the user has authorized it.
- Measure observable behavior, not inferred behavior. Capture event traces, positions, state transitions, timing, and before/after state where relevant.
- Preserve diagnostic worlds and calculate/compare hashes when the test contract requires it.
- Never edit production source to repair a failure. Produce a precise finding, logs, minimal reproduction, and proposed diagnosis for ChatGPT.
- Persist all results before ending the session, including failures, blocked runs, timeouts, and incomplete work.

Harness may make changes to test orchestration, diagnostic scripts, logs, and reports only when that does not alter production behavior or violate preservation rules. Any ambiguous change must be reported rather than silently made.

## 4. Synchronization and conflict safety

Before any sync or code operation:

1. Record `git status --short`, `git branch --show-current`, `git rev-parse HEAD`, and `git rev-parse origin/feature/autonomous-village` after fetching.
2. Inspect `git log --oneline --decorate -n 10` and divergence.
3. Identify local-only files and changes.
4. Confirm the expected parent/head from the latest handoff.

Prohibited unless the user explicitly authorizes a specific recovery plan:

- `git reset` (especially `--hard`)
- `git clean`
- `git stash`
- force-push
- indiscriminate revert
- indiscriminate `git add .` or staging all files
- overwriting diagnostic worlds or local-only artifacts

If local and remote histories diverge, if a remote head unexpectedly changed, or if a sync would overwrite local work: **stop, preserve both sides, capture the SHAs/status, and report the conflict.** Do not guess which version wins.

ChatGPT must apply the same principle to GitHub writes: check the current branch state before publishing and do not move the branch backwards or overwrite newer work.

## 5. Evidence and result vocabulary

Every result must be classified accurately:

- **PASS:** The specified command or acceptance criterion completed successfully on the identified SHA, with evidence.
- **FAIL:** The test completed and demonstrated that the criterion was not met.
- **BLOCKED:** The test could not validly run or complete because of an environmental, setup, synchronization, or instrumentation problem.
- **UNKNOWN / NOT RUN:** No sufficient evidence exists. Do not convert absence of an event into proof of a specific cause.

Always distinguish:

- Code reviewed vs. code compiled.
- Test added vs. test executed.
- Unit/policy test vs. integration test.
- Build success vs. runtime success.
- Perimeter contact vs. strict wall entry.
- One successful run vs. stable acceptance.
- A missing log event vs. proof that the behavior did not occur.

Never fabricate measurements, timestamps, logs, hashes, test results, or runtime events.

## 6. Reporting and durable state

Harness must create a new dated report for each meaningful validation batch. Do not rewrite historical reports to improve their outcome.

Each report must include:

- Date/time and machine/environment.
- Repository, branch, initial/final local HEAD, remote HEAD, and exact tested SHA.
- Working-tree and synchronization state.
- Commands, exit codes, outputs/log paths, and per-test results.
- Runtime scenario, seed/world identity, actors/indices, relevant coordinates, measured transitions, and event traces.
- Before/after hashes for protected worlds or files when applicable.
- PASS/FAIL/BLOCKED/UNKNOWN with the evidence supporting each classification.
- Local-only artifacts preserved.
- Unresolved defects, limitations, and exact next action.

At the end of each batch, update:

- `docs/autonomous-village/NEXT-SESSION.md` — concise current checkpoint and next action.
- `docs/autonomous-village/CONTINUAR.md` — continuation instructions and current state.
- `docs/CONTINUAR.md` and `docs/PENDIENTE.md` when their project-level status or task list changes.
- The relevant dated technical report — detailed evidence.

The checkpoint must make it possible for a new ChatGPT session and a new Harness session to continue independently. Record exact SHAs, file paths, commands, world names, hashes, log paths, decisions, and open questions. Do not rely on phrases such as “continue where we left off” without a concrete pointer.

## 7. Session-start protocol for either agent

At the beginning of every new session:

1. Read `CHATGPT-COLLABORATION-POLICY.md`.
2. Read `NEXT-SESSION.md`.
3. Read this workflow contract.
4. Read `CONTINUAR.md`, `docs/CONTINUAR.md`, and `docs/PENDIENTE.md`.
5. Read every report explicitly referenced by the current checkpoint and the relevant acceptance contract.
6. Verify current GitHub/local state rather than trusting a SHA copied from an old conversation.
7. Identify the last completed action, the last verified evidence, the first pending action, and the responsible role.
8. Continue from that point without repeating completed investigations unless new evidence invalidates them.

If the current documents disagree, do not silently choose one. Preserve the discrepancy, inspect commit chronology and evidence, then reconcile the checkpoint.

## 8. Handoff format

Every ChatGPT implementation handoff to Harness should state:

- Problem and evidence being addressed.
- Implementation commit SHA and parent SHA.
- Files changed and behavioral contract.
- Tests added/changed (explicitly marked **not run** unless actually run).
- Exact synchronization and validation commands.
- Focused runtime scenarios and expected observable evidence.
- Invariants and prohibited shortcuts.
- Required report/document updates.

Every Harness validation handoff to ChatGPT should state:

- Exact tested SHA and whether it equals the intended implementation SHA.
- Commands and exit codes.
- Test names and results.
- Runtime measurements and raw log locations.
- Reproduction steps and the first failing transition.
- What is proven, disproven, and still unknown.
- Whether the source tree was left untouched.
- Exact next correction or validation decision needed.

## 9. Project-specific invariants

This workflow does not replace project-specific acceptance criteria. For autonomous-village work, continue to honor all current A2/C4 contracts, world-preservation rules, navigation invariants, and campaign gates in their authoritative documents.

In particular, do not launch the 12,000-run campaign until the documented gate of five consecutive 8/8 strict `DENTRO-PARED` runs is met. Do not alter thresholds or redefine success to manufacture a PASS.

---

**Non-negotiable principle:** ChatGPT is the implementation owner; Harness is the execution, measurement, and evidence owner. Both are responsible for durable documentation. Neither may silently take over the other's role or erase the other's work.
