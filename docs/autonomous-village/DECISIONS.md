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

## D-012 — Default-off bounded per-actor resource ledger
`ForestryResourceTrace` is a pure, bounded (2048 events) ledger; the Minecraft adapter counts
only and never mutates inventories, targets, claims or schedules. It is enabled only by
`-Ddevilrpg.resourcetrace=true`. Harvest is creation; deposits/withdrawals are transfers;
inventory inserts are informational and excluded from conservation; pickup is a world source;
drop/fuel consumption are sinks. Every event has a unique ID and transfer correlation, and
truncation/unattributed events force INCOMPLETE. A8-01 is COMPLETE within the declared
post-initialization autonomous scope; A7-12 may not claim equivalence from it yet.

**Resolution (2026-11-11).** The final sentence above was the status when D-012 was accepted.
A7-12 subsequently closed in Campaign 9 (`res-a7abandon3-off-20261111-224500` /
`res-a7abandon3-audit-20261111-225000`): isolated single/two-worker resource and target
equivalence, arrival/path/lease lifecycle, same-tree contention/reselection, and real
abandonment/cleanup/recovery were observed. Scope: controlled A5WALL forestry fixtures only;
no universal OFF/AUDIT equivalence is claimed. Campaign 6’s AUDIT owner non-completion remains
`INSUFFICIENT_EVIDENCE` and non-reproduced (Campaign 8); it is a documented limitation, not a
proven defect. OFF remains default; AUDIT diagnostic-only; no authority transfer.


## D-013 — World-global difficulty reference and per-creature SpawnProfile progression

**Decision (owner-confirmed design):** geographic difficulty is measured from the single shared world spawnpoint, common to all players. Both custom-spawned `AggressiveZombie` and its siege-attacker spawn path use that world-global reference. Temporal difficulty is based on shared world play time; current intended semantics snapshot threat at spawn, so newly spawned creatures scale while existing entities retain their spawn-time attributes. Multiplayer behavior must not depend on nearest-player selection, player-specific bed/spawn anchors, current player position, or the player that triggered spawning.

**Required verification before implementation:** the existing forensic audit reports a nearest player's anchor/spawn as the reference. This is not equivalent by definition to the shared world spawnpoint. Trace the exact production source/API and verify both spawn paths plus multiplayer independence before changing code.

**Future architecture:** temporal progression parameters belong to each creature's `SpawnProfile`; shared world time/threat remains a global input. Current custom creatures are `AggressiveZombie` and custom Vex, which should be consistently named `AggressiveVex`. Plan the rename across source names, registries/resource IDs, serialized identity/save compatibility, configs, translation keys, diagnostics/log strings, tests and docs. Preserve existing worlds; do not blindly change persisted registry identifiers.

**Status:** design accepted; implementation NOT AUTHORIZED by this decision. First verify the current global spawn reference, then design the profile contract and rename migration separately. No balance changes or A2/C4 acceptance changes.
