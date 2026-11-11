# Workflow — Local-First Autonomous Village Development

Status: proposed workflow for future ChatGPT/GitHub/Harness integration.

## Authoritative documents (read in this order)

1. `docs/CONTINUAR.md` — live handoff and current state.
2. `docs/PENDIENTE.md` — historical handoff; verify against CONTINUAR before use.
3. `docs/autonomous-village/GOVERNANCE.md` — authority and evidence rules.
4. Task-specific acceptance document.
5. Relevant architecture/design document.
6. `docs/aldea-invariantes.md` — village invariants.

Do not treat old “PENDIENTE” entries as an active queue without checking the latest
CONTINUAR entry.

## Standard loop

```text
owner/planner task -> JSON task contract
  -> local orchestrator validates scope/authorization
  -> Harness executes in worktree
  -> orchestrator runs allowed validation
  -> structured execution report
  -> human/planner review
  -> explicit decision: accept, revise, or reject
```

## Conflict register: obsolete instructions

The following historical instructions conflict with current governance and must not
be followed automatically:

- Older `CONTINUAR.md`/`PENDIENTE.md` sections that describe committing or pushing
  after a successful run. Current rule: no commit/push without explicit,
  task-specific authorization.
- Old harness instructions that copy `run/saves/New World (2)` into `run/world`.
  Current rule: use disposable `build/` worlds; protected worlds are read-only.
- Old instructions to “always run three or four measurements” before reporting.
  Current rule: run the task’s required scenarios and report NOT RUN honestly; do
  not pad with unrelated runs.
- References to a global task reservation/arbiter for forestry. Current accepted
  design is per-goal ownership; do not add a global arbiter.
- Any claim that planner state survives save/reload. Current source-verified fact:
  forestry task state is NBT-persisted only after Phase 1 implementation; Block 1
  historical evidence was in-memory lifecycle only.

## Safety defaults

- Disposable worlds only under `build/`.
- Check Java/Minecraft processes before launch; never stop unrelated processes.
- No destructive Git commands.
- Preserve dirty/untracked work unless the task explicitly owns it.
- Lint and diff checks are mandatory; compilation is not runtime acceptance.

## Harness CLI credential gate (2026-11-07)

The headless CLI fails closed with `MISSING_CREDENTIAL` unless an authorized
credential path is provisioned. Orchestrator work may characterize/parse the NDJSON
envelope, but successful execution/session tests require the owner to provide the
credential through the credentials service or an approved launcher environment.
Never put the key in task JSON, logs, or repository files.
