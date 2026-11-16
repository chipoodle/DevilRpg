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
3. Read, in this order:
   - `docs/autonomous-village/NEXT-SESSION.md`
   - `docs/autonomous-village/CHATGPT-COLLABORATION-POLICY.md`
   - `docs/autonomous-village/CONTINUAR.md`
   - `docs/CONTINUAR.md`
   - `docs/PENDIENTE.md`
4. Read every additional document explicitly referenced by `NEXT-SESSION.md`.
5. Read relevant acceptance criteria, invariants, testing/harness instructions and open investigations before proposing changes.
6. Verify the actual source against documented claims. A report saying a fix exists is not proof that the source implements it.
7. Inspect changes since the previous checkpoint and establish the actual state of A2, C4, tests, runs and unresolved defects.
8. Continue from the verified state; do not restart completed investigations.

## Roles

- **Harness** performs local development, diagnostics, automated runs and tests.
- **ChatGPT** independently reviews the published GitHub source/docs and may propose or contribute changes through GitHub.
- Harness must inspect the latest remote commit before applying any ChatGPT proposal.
- Neither party may overwrite newer work from the other.
- Unpublished local work is not visible to ChatGPT and must be reported as local-only.
- Every meaningful finding and correction must be published to `feature/autonomous-village`.

## Evidence and acceptance rules

- Preserve historical reports; never rewrite old results to look better.
- Preserve diagnostic worlds and backups; do not recreate/overwrite them.
- Never weaken collision, support, C4 12/12, or siege acceptance criteria to obtain a PASS.
- Do not manufacture survival, teleport entities, force job sites, or nerf defenses.
- Attacker combat deaths are legitimate; evaluate navigation, access, entry and conquest separately.
- A single passing run is not stable acceptance; report every run, including failures.
- `NEXT-SESSION.md` is the continuation index, not a replacement for reading this policy and its referenced documents.

## Mandatory session end

1. Publish intentional source/documentation changes with descriptive commits.
2. Verify local and remote HEAD match.
3. Update `NEXT-SESSION.md` with the verified SHA and exact state.
4. Record diagnostics, tests, failures and unresolved issues.
5. Record local-only artifacts and diagnostic world paths/hashes.
6. Ensure a new ChatGPT session can continue without conversational memory.

## Diagnostic worlds

Do not commit complete world directories or large archives. Publish paths, hashes, coordinates and validation manifests. Keep playable worlds in both the standard launcher saves and the project `run/saves`.

## Conflict handling

If local and remote histories diverge, stop and report. Never force-push. Preserve both versions and resolve only with explicit evidence. Never reset/clean/stash/revert work.
