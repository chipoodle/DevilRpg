# Governance — Autonomous Village Development

Status: governance proposal, effective for future sessions. Read-only audit basis.

## Source of truth

1. The local worktree at `D:\Documents\DevilRpg-autonomous` is authoritative for
   code, docs, evidence, and uncommitted work.
2. GitHub is a publication/backup remote, not the source of truth.
3. Minecraft runtime evidence is required for gameplay acceptance; compilation or
   source inspection is never acceptance.
4. Historical acceptance documents are evidence and must not be rewritten to fit a
   new result.

## Roles and authority

| Role | May do | May not do without explicit owner approval |
|---|---|---|
| Project owner | authorize commits, pushes, protected-world access, scope changes | — |
| ChatGPT / planning agent | read, plan, produce task specs and reviews | commit, push, edit production code, launch Minecraft |
| Local Java 21 orchestrator | validate task JSON, invoke Harness, collect reports, run allowed checks | push, alter governance, bypass authorization |
| Harness Desktop | execute the submitted task in the worktree | expand scope, commit/push, touch protected worlds |
| Human reviewer | inspect evidence, accept/reject task | rewrite historical evidence |

## Authorization gates

- Commit/push: explicit, task-specific owner authorization only.
- Branch creation/merge/rebase: explicit authorization.
- Protected worlds `run/saves/New World` and `run/saves/A2-Observation`: read-only,
  never modified.
- Production code changes: only after a reproducible defect and within task scope.
- Secrets: never read, log, transmit, or commit `.credentials.yaml`, tokens, keys.

## Change control

- One task, one bounded scope, one execution report.
- Failed or unverified criteria remain NOT RUN/FAIL; they are never upgraded by
  prose.
- Accepted systems (Forestry Block 1, Food Economy Block 3.2) are frozen until a
  reproducible regression or new owner-approved requirement.
- Documentation must point to authoritative evidence rather than duplicate run
  logs.

## Evidence standard

Every runtime claim records: run ID, world path, villager/entity UUID, objective,
phase/controller/target, relevant NBT, inventory/storage/blocks, tick numbers,
result, and exact command. A screenshot/log without state evidence is insufficient.
