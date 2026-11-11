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

## D-010 — Ephemeral world-scoped cross-worker tree claim
A TALAR tree claim is keyed by `(dimension, tree BlockPos)`, held in memory only, and is
separate from the per-agent navigation lease. The Goal acquires it before exposing a target,
renews it each tick, releases it on completion/abandonment/stop/death/leave, validates
release by owner+task, and lets unrenewed claims expire after 200 ticks. No NBT is added:
claims are transient and a reloaded worker re-discovers/re-claims. Status: accepted; the
runtime D4-A–E evidence is in `LUMBERJACK-PHASE-1-ACCEPTANCE.md`.

## D-011 — Explicit Goal priority tiers and flag arbitration
Village goals use `VillageGoalPriority`: EMERGENCY 0 (flee), CRITICAL 1 (tavern/eating),
UTILITY 2 (gate, door, flag-gated dispatcher), SECURITY 3 (guard, builder repair),
PROFESSION 4 (all profession work), SUPPORT 5 (worker repair, collection), OPPORTUNISTIC 6
(pickup). Lower numbers are more important. NeoForge `GoalSelector` keeps insertion order,
locks MOVE/LOOK independently, lets a lower number preempt a running higher number when the
running goal is interruptable, and never replaces equal priority (first registered wins).
Lumberjack is 4; tavern is 1 (not -1: it must yield to flee). Claims/leases are separate
mechanisms. Status: accepted; D5-A–E runtime evidence in
`LUMBERJACK-PHASE-1-ACCEPTANCE.md`.
