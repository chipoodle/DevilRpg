# Decisions — Autonomous Village Integration

Format: decision, rationale, status.

## D-001 — Local worktree is authoritative
GitHub may lag or contain stale docs. Decisions and acceptance use the local
worktree. Status: accepted.

## D-002 — No automatic commit/push
Historical docs that instruct auto-commit are obsolete. Commit/push require
explicit owner authorization for that task. Status: accepted.

## D-003 — Harness CLI is the preferred integration surface
The installed app exposes `dsh` with `headless`, `--json`, and `--session-id`.
Prefer a local process invocation over desktop UI automation. Status: accepted.

## D-004 — File inbox/outbox is the fallback adapter
If a future Harness build lacks a suitable CLI/API, use atomic JSON files in
`build/integration/inbox`, `outbox`, `status`, with a lock file. No database.
Status: proposed fallback.

## D-005 — Java 21 local orchestrator
The orchestrator validates task contracts, enforces authorization, invokes the
Harness CLI, runs validation, and writes reports. It does not modify gameplay.
Status: proposed.

## D-006 — No secrets in contracts or logs
Credentials remain in the Harness profile store; contracts contain no tokens.
Status: accepted.

## D-007 — Evidence over prose
NOT RUN/FAIL cannot be converted to PASS by documentation. Historical acceptance
is preserved verbatim. Status: accepted.

## D-008 — Completed systems are frozen
Forestry Block 1 and Food Economy Block 3.2 are reopened only for reproducible
regression or new owner-approved requirements. Status: accepted.

## D-009 — One authoritative TALAR target predicate; no stale fallback
`TalarStepPlanner.isValidTalarTarget()` (`BlockTags.LOGS`) is the single production
predicate for a TALAR target. A planner result without a valid Step must not reuse the
previous target; invalid targets are abandoned through the existing lifecycle before
navigation authorization. A8-F03 is completed and accepted under this rule. Status:
accepted; reopen only for a reproducible regression.
