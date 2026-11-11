# Harness Desktop Integration — Read-Only Audit

Date: 2026-11-07. Status: audit + proposal. No orchestrator implemented.

## 1. Application identity

```text
Product: DeepSeek Harness
Executable: D:\programas\deepseek\DeepSeek Harness.exe
FileVersion: 0.2.1-rc.2
ProductVersion: 0.2.1.0
Electron: 44.7.0 (D:\programas\deepseek\version)
Install root: D:\programas\deepseek
User data: C:\Users\Christian\.dsh
Update channel: nightly, generic feed
```

Profiles present (names only): `desktop`, `headless`, `web`.

## 2. Capability audit

| Capability | Status | Evidence |
|---|---|---|
| CLI launcher | **VERIFIED** | `resources\runtime\cli\bin\dsh.cmd`; `dsh --help` |
| Version query | **VERIFIED** | `dsh --version` -> `0.2.1-rc.2` |
| Headless one-task execution | **VERIFIED** | `dsh headless --help`; task/stdin, answer to stdout |
| Structured output | **VERIFIED** | `dsh headless --json` emits newline-delimited run events |
| Session continuation | **VERIFIED** | `--session-id <id>` adopts persisted session |
| Web UI/server | **VERIFIED** | `dsh web --help`; host/port/TLS; current GUI 127.0.0.1:64850 |
| Profile composition/plugins | **VERIFIED** | `dsh plugin`, `--profile`, `--patch`, `--dump-config` |
| Public HTTP API | **UNKNOWN** | Web UI has `/api` trust fencing, but no documented external task API |
| MCP | **UNKNOWN / NOT FOUND** | No MCP command or documented MCP entry in `dsh --help` |
| Desktop UI automation | **NOT AVAILABLE as documented contract** | Desktop app exists; no documented automation interface |
| File-based inbox/outbox | **NOT AVAILABLE built-in** | Proposed adapter only |
| Exit codes/status JSON | **PARTIAL** | CLI process exit code exists; `--json` run events verified by help, payload schema not audited |
| GitHub integration | **NOT AVAILABLE** | No `dsh` GitHub command documented |

No secrets, credentials, or tokens were read. `.credentials.yaml` was not opened.

## 3. Evidence commands (read-only)

```powershell
Get-Content 'D:\programas\deepseek\version'
(Get-Item 'D:\programas\deepseek\DeepSeek Harness.exe').VersionInfo
Get-Content 'D:\programas\deepseek\resources\runtime\cli\bin\dsh.cmd'
dsh --version
dsh --help
dsh headless --help
dsh web --help
```

## 4. Recommended architecture

Preferred:

```text
ChatGPT/planner
  -> GitHub (reviewed task specs, docs)
  -> Local Java 21 orchestrator
       - validates JSON task contract + authorization
       - invokes: dsh --profile headless --json "<task>"
       - captures NDJSON events + exit code
       - runs allowed validation
       - writes execution report + status
  -> Harness Desktop CLI (same install)
  -> D:\Documents\DevilRpg-autonomous worktree
```

Use `--session-id` only for explicitly approved continuation. Do not use desktop
UI automation. Do not invoke Git push from the orchestrator.

Fallback if CLI is unavailable in a future build:

```text
build/integration/inbox/<task-id>.json      atomic write
build/integration/status/<task-id>.json     running/done/failed
build/integration/outbox/<task-id>.json     report
build/integration/.lock                     single-consumer lock
```

The adapter must never place secrets in these files.

## 5. Proposed task contract

```json
{
  "schema": "devilrpg.task/v1",
  "task_id": "TASK-20261107-001",
  "title": "short title",
  "repository": "D:\\Documents\\DevilRpg-autonomous",
  "branch": "feature/autonomous-village",
  "scope": {
    "allowed_paths": ["docs/autonomous-village/"],
    "forbidden_paths": ["run/saves/New World", "run/saves/A2-Observation"],
    "production_changes_allowed": false,
    "runtime_allowed": false
  },
  "objective": "single measurable outcome",
  "acceptance": ["criterion 1", "criterion 2"],
  "authorization": {
    "commit": false,
    "push": false,
    "protected_worlds": false,
    "network": false
  },
  "harness": {
    "profile": "headless",
    "json_events": true,
    "session_id": null
  },
  "deadline_seconds": 1800
}
```

## 6. Proposed status/report contract

Status file:

```json
{
  "schema": "devilrpg.task-status/v1",
  "task_id": "TASK-20261107-001",
  "state": "QUEUED|RUNNING|DONE|FAILED|BLOCKED",
  "pid": 1234,
  "started_at": "RFC3339",
  "updated_at": "RFC3339",
  "exit_code": null,
  "report_path": "build/integration/outbox/TASK-20261107-001.json"
}
```

Report: use `TASK-REPORT-TEMPLATE.md`. Required fields: status, repository state,
changes, validation with exit codes, runtime evidence, unverified items, blockers,
authorization flags. A report with `commit:true` or `push:true` is invalid unless
the task contract authorized it.

## 7. GitHub synchronization risks

Observed read-only:

```text
origin: https://github.com/chipoodle/DevilRpg.git
local branch: feature/autonomous-village
local HEAD: 97220be5453b57387002b9bc91935807cc665274
remote feature/autonomous-village: 863e5c7312e8abb27b17c2f0b24188c6e9cbe5d5
```

Risks:

1. Local and remote feature branches are not the same commit. The local worktree
   contains 26 unstaged and 98 untracked paths not represented by that SHA.
2. No `main` ref was returned by the audited `git ls-remote`; the default remote
   branch may have a different name. Do not assume `main`.
3. Remote documentation can be stale; local `docs/CONTINUAR.md` is the live
   handoff.
4. Uncommitted local work can be lost by a careless fetch/checkout/merge; no such
   operation was performed.
5. Pushing without review would publish unverified Phase 1 work and dirty files.
6. Task contracts must pin the expected local HEAD; a mismatch must fail closed.

## 8. Integration acceptance proposal

- Task contract validates against JSON schema and authorization gates.
- Orchestrator invokes headless CLI with a timeout and captures NDJSON + exit code.
- Report status is derived from exit code and explicit acceptance checks.
- No commit/push/protected-world operation is reachable from the adapter.
- A dry-run mode only validates and prints commands.
- Human approval is required for any GitHub write.

## 9. Limitations

- NDJSON event schema was not audited beyond `dsh headless --help`.
- No external API/MCP documentation was found in the installed help.
- Desktop UI automation is not a supported contract.
- No orchestrator, file adapter, or GitHub write was implemented.

## Implemented skeleton (Phase 3)

`tools/harness-orchestrator/` implements the task contract, NDJSON parser,
process adapter, state machine, and report skeleton with Java 21 and no third-party
dependencies. Unit tests use a fake adapter and pass. It has NOT executed a real
Harness task; `SUCCEEDED` is intentionally unreachable and exit 0 + final text is
classified `UNVERIFIED`. See `HARNESS-ORCHESTRATOR.md`.

## Phase 5 status

The local task runner is implemented in `tools/harness-orchestrator` with
`validate/run/status/report/help`, atomic inbox/running/terminal moves, ownership
lock, read-only Git baseline gate, default-deny authorization, and JSON/Markdown
reports. Tested with fake process/Git only. Real Harness remains BLOCKED by
credentials. See `HARNESS-TASK-RUNNER.md`.

## Phase 5.2 status

Complete report contract implemented as `devilrpg.report/v2`, with JSON/Markdown
serialization, completeness validation, ordered event preservation, explicit
UNKNOWN fields, authorization decisions, and collision-safe persistence. Tested
with fake process/Git; real Harness success remains BLOCKED by credentials.
