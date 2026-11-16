# ChatGPT Collaboration Policy — DevilRpg Autonomous Village

This document is permanent and mandatory for every future ChatGPT/Harness session.

## Repository and branch

- Repository: `chipoodle/DevilRpg`
- Working branch: `feature/autonomous-village`
- Minecraft 1.21.1 / NeoForge 21.1.249
- Do not push experimental changes to `master`.

## Mandatory session start

1. Identify the repository and branch above.
2. Fetch the remote and verify local HEAD, remote HEAD and divergence.
3. Read this policy FIRST, then `docs/autonomous-village/NEXT-SESSION.md`, then:
   - `docs/autonomous-village/CHATGPT-HARNESS-WORKFLOW.md`
   - `docs/autonomous-village/CONTINUAR.md`
   - `docs/CONTINUAR.md`
   - `docs/PENDIENTE.md`
4. Read every additional document explicitly referenced by `NEXT-SESSION.md`.
5. Read relevant acceptance criteria, invariants, testing/harness instructions and open investigations before proposing changes.
6. Verify the actual source against documented claims. A report saying a fix exists is not proof that the source implements it.
7. Inspect changes since the previous checkpoint and establish the actual state of A2, C4, tests, runs and unresolved defects.
8. Continue from the verified state; do not restart completed investigations.

## Document precedence

- Historical reports are preserved and are not current instructions by default.
- The latest verified `NEXT-SESSION.md` defines the current continuation task.
- This policy and `CHATGPT-HARNESS-WORKFLOW.md` define permanent collaboration rules.
- The latest applicable dated evidence supersedes older status claims.
- Do not follow a historical instruction when a later verified report explicitly supersedes it.
- If two current documents conflict, report and reconcile the conflict; do not guess.

## Roles and strict division of responsibility

- **ChatGPT is the implementation owner.** ChatGPT investigates defects, reviews actual source, designs corrections, modifies production source directly on GitHub in `feature/autonomous-village`, reviews the diff, publishes commits, and records the implementation handoff.
- **Harness is the execution and evidence owner.** Harness synchronizes the published branch locally, preserves local-only work, runs builds/tests/lints and automated Minecraft scenarios, measures behavior, captures logs, and writes validation reports.
- **Harness must not independently modify production source code.** When a defect is found, Harness supplies reproducible evidence and a precise report; ChatGPT performs the source correction on GitHub.
- **ChatGPT must not claim local execution.** A code review or test added is not a test run. ChatGPT may report PASS only when a verifiable Harness/CI result identifies the exact tested commit.
- **Harness must validate the exact ChatGPT commit.** A published commit is pending until Harness has fetched that SHA and executed the required checks.
- Harness may maintain test orchestration, diagnostic scripts and reports without changing production behavior; ambiguous changes must be reported first.
- Neither party may overwrite newer work from the other.
- Unpublished local work is not visible to ChatGPT and must be reported as local-only.
- Every meaningful finding, implementation and validation result must be persisted in the repository.

The required loop is: **ChatGPT implements → Harness validates/measures → Harness documents evidence → ChatGPT reviews/corrects → Harness validates the new SHA.**

See `docs/autonomous-village/CHATGPT-HARNESS-WORKFLOW.md` for the complete operating protocol and handoff formats.

## Evidence and acceptance rules

- Preserve historical reports; never rewrite old results to look better.
- Preserve diagnostic worlds and backups; do not recreate/overwrite them.
- Never weaken collision, support, C4 12/12, or siege acceptance criteria to obtain a PASS.
- Do not manufacture survival, teleport entities, force job sites, or nerf defenses.
- Attacker combat deaths are legitimate; evaluate navigation, access, entry and conquest separately.
- A single passing run is not stable acceptance; report every run, including failures.
- Distinguish code reviewed, code compiled, tests executed, integration behavior and runtime acceptance.
- `NEXT-SESSION.md` is the continuation index, not a replacement for reading this policy and its referenced documents.

## Mandatory session end

1. ChatGPT publishes intentional source changes with descriptive commits; Harness publishes intentional validation/documentation changes with descriptive commits.
2. Verify the relevant local and remote HEADs and report exact SHAs.
3. Update `NEXT-SESSION.md` with the verified SHA and exact state.
4. Record diagnostics, tests, failures and unresolved issues.
5. Record local-only artifacts and diagnostic world paths/hashes.
6. Ensure a new ChatGPT session and a new Harness session can continue without conversational memory.

## Diagnostic worlds

Do not commit complete world directories or large archives. Publish paths, hashes, coordinates and validation manifests. Keep playable worlds in both the standard launcher saves and the project `run/saves`.

## Conflict handling

If local and remote histories diverge, stop and report. Never force-push. Preserve both versions and resolve only with explicit evidence. Never reset/clean/stash/revert work.
