# Governance — Autonomous Village Development

Status: **active and mandatory**. This document is subordinate to the permanent role contract in
`CHATGPT-COLLABORATION-POLICY.md` and `CHATGPT-HARNESS-WORKFLOW.md`.

## 1. Shared repository and source of truth

- Repository: `chipoodle/DevilRpg`.
- Shared working branch: `feature/autonomous-village`.
- The branch is the shared communication channel and durable source of truth for source,
  implementation handoffs, Harness reports, evidence indexes, and continuation state.
- ChatGPT and Harness must read the latest branch state before publishing. Neither may overwrite
  commits or documentation published by the other.
- Harness's local worktree is its execution environment, not a separate competing source of truth.
  Local-only runtime artifacts and diagnostic worlds remain local; their paths, hashes, and report
  references are recorded in the branch.
- Do not publish experimental changes to `master`.

## 2. Roles and authority

| Role | Owns | Must not do |
|---|---|---|
| Project owner | Product direction, scope and protected-world authorization | Perform routine code/test handoffs between agents |
| ChatGPT | Source investigation, root-cause analysis, production implementation, code/test review, direct commits to `feature/autonomous-village`, review/correction of Harness documentation, continuity updates | Claim local builds or runtime execution without Harness/CI evidence; overwrite newer branch work |
| DeepSeek Harness | Synchronize the shared branch locally, preserve local artifacts, run builds/tests/lints and automated Minecraft scenarios, measure behavior, capture logs, write/publish dated validation reports and evidence | Independently implement production source fixes, weaken acceptance criteria, overwrite ChatGPT changes |
| Both agents | Keep the shared branch coherent, review chronology, preserve historical evidence and leave a usable handoff | Reset/clean/stash, force-push, silently resolve conflicting work |

## 3. Mandatory implementation–verification loop

1. ChatGPT reads the current branch, latest checkpoint, permanent collaboration contract and relevant
   technical reports.
2. ChatGPT investigates the actual source and the latest Harness evidence.
3. ChatGPT implements the correction directly on `feature/autonomous-village`, reviews the diff,
   commits it and publishes an exact SHA plus a Harness validation request.
4. Harness synchronizes that exact SHA locally, preserving local-only work, and runs only the
   requested automated validation/scenarios.
5. Harness publishes a dated report and updates the continuation/evidence documents on the same
   branch. It returns the report to the user, who may also paste it into ChatGPT.
6. ChatGPT reviews every Harness report and every Harness-authored documentation change against
   source, logs, metrics, prior reports and acceptance criteria. ChatGPT corrects inconsistencies,
   updates the technical interpretation and continuation handoff, and implements the next source
   correction when warranted.
7. Harness validates the exact new ChatGPT SHA. Repeat until the documented acceptance gates pass.

**Responsibility split: ChatGPT develops and commits; Harness tests, measures and reports; ChatGPT
reviews Harness's evidence/documentation and develops the next correction.**

## 4. Git and conflict safety

- Before each GitHub write, ChatGPT must fetch/read the current branch state and verify that the
  target file's current blob SHA is the one being updated. Never overwrite a newer commit.
- Harness must inspect branch, HEAD, remote HEAD, working tree, untracked files and divergence before
  synchronizing. Use safe fast-forward only when possible.
- If histories diverge, a file changed after it was read, or synchronization would overwrite
  local-only work: stop, preserve both sides, record SHAs/status and report the conflict.
- No `git reset`, `git clean`, `git stash`, force-push, indiscriminate revert, or indiscriminate
  staging. Do not overwrite diagnostic worlds or local-only artifacts.
- ChatGPT may publish implementation and documentation commits directly to the shared branch. Harness
  may publish validation reports, logs/manifests that belong in Git, and its documentation updates.
  ChatGPT reviews and reconciles those changes; Harness does not implement production source fixes.
- Large runtime logs, full world directories and archives remain local. Publish concise reports,
  reproducible paths, hashes and manifests instead.
- Secrets and credentials must never be read, logged, transmitted or committed.

## 5. Protected worlds and acceptance

- Protected worlds remain read-only unless a task explicitly authorizes otherwise.
- Disposable runtime worlds belong under the documented `build/` locations.
- Runtime evidence is required for gameplay acceptance; compilation/source review is not runtime
  acceptance.
- Preserve historical reports; never rewrite old results to make them appear successful.
- Failed, blocked, unknown and unrun criteria retain their true status.
- Never weaken collision, support, C4 cleanliness, siege-entry or other acceptance criteria to obtain
  a PASS.
- Do not manufacture survival, teleport entities, force job sites, or nerf defenses.
- Do not launch the full 12,000-run campaign until the documented gate of five consecutive strict
  8/8 `DENTRO-PARED` runs is met.

## 6. Evidence and durable handoff

Every validation report must identify the exact tested SHA, run ID, commands and exit codes, runtime
scenario/world, relevant entities and coordinates, measured transitions, logs, hashes, result
classification, unresolved defects and exact next action.

After each meaningful batch:
- Harness records the raw validation result in a new dated report and updates the shared checkpoint.
- ChatGPT reviews the report and updates `NEXT-SESSION.md`, `CONTINUAR.md`, and related technical
  documentation as necessary.
- Historical evidence is append-only in meaning: corrections are made in new review notes, not by
  rewriting old outcomes.
- A new session must be able to resume from repository evidence without depending on chat memory.

## 7. Document precedence

1. `CHATGPT-COLLABORATION-POLICY.md` and `CHATGPT-HARNESS-WORKFLOW.md`: permanent role and operating
   contract.
2. `NEXT-SESSION.md`: latest verified continuation checkpoint and next action.
3. Latest dated Harness report: empirical result for its exact tested SHA.
4. Relevant acceptance contracts and invariants.
5. Historical reports and proposals: evidence/context only unless explicitly reactivated.

If documents conflict, preserve the evidence, identify the contradiction and reconcile the active
documents. Do not silently follow obsolete instructions.
