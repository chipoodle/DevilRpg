# Harness Local Task Runner (Phase 5)

Status: IMPLEMENTED and TESTED with fake process/Git providers. Real Harness
execution remains BLOCKED by credentials; no real task was run.

## Architecture

```text
Main (CLI)
  -> TaskRunner
       -> TaskContract validation
       -> GitStateProvider read-only baseline check
       -> atomic inbox -> running claim
       -> RunLock (ownership token)
       -> Orchestrator -> ProcessAdapter -> dsh headless --json
       -> terminal directory + report
```

Standalone Java 21. No Minecraft Gradle integration, no third-party dependencies.

## Module layout

```text
tools/harness-orchestrator/src/main/java/.../
  Main.java                 CLI
  TaskRunner.java           queue, lock, git gate, reports
  RunLock.java              atomic owner-token lock
  GitStateProvider.java     read-only Git interface
  ProcessGitStateProvider.java
  TaskContract.java, Json.java, NdjsonParser.java,
  ProcessAdapter.java, RealProcessAdapter.java,
  Orchestrator.java, Report.java
```

## CLI

```text
java -cp <classes> com.chipoodle.harness.orchestrator.Main help
java -cp <classes> ...Main validate <task-file>
java -cp <classes> ...Main run <task-file>
java -cp <classes> ...Main status <task-id>
java -cp <classes> ...Main report <task-id> [--format json|markdown]
```

Exit codes: 0 help/validate/status/report, 1 FAILED/TIMED_OUT, 2 usage/invalid,
3 UNVERIFIED, 4 BLOCKED.

`run` validates, checks duplicates, checks branch/HEAD/clean worktree, acquires the
lock, claims the task atomically, invokes the orchestrator, stores the terminal
copy, writes reports, and releases the lock.

## Directory layout

```text
build/integration/
  inbox/       submitted tasks
  running/     claimed task + .meta
  completed/   UNVERIFIED/SUCCEEDED terminal copies
  failed/      FAILED/TIMED_OUT
  blocked/     invalid/authorization/git/lock/duplicate blocks
  reports/     <task-id>.json and .md
  locks/       runner.lock
```

Task files are `<task-id>.json`. Reports use `CREATE_NEW`; existing reports are
never overwritten. Inbox -> running and running -> terminal moves use
`ATOMIC_MOVE`; if the filesystem does not support it, the runner fails rather than
falling back silently.

## Task identity/idempotency

- ID comes from `devilrpg.task/v1`.
- Duplicate IDs are detected across inbox/running/completed/failed/blocked.
- A duplicate is BLOCKED without altering the original task or report.
- No automatic retry or rerun exists.
- Reports are never overwritten.

## Git validation

Read-only `ProcessGitStateProvider` uses argument-safe `git -C ...` calls:

- branch must equal the contract;
- expected HEAD (when present) must equal actual;
- `git status --porcelain` must be clean. Explicit dirty-baseline support is not
  implemented; a dirty worktree is BLOCKED.
- Git command failure -> BLOCKED.
- No stash/commit/reset/checkout/merge/restore is performed.

Tests use a fake Git provider; no destructive Git commands are run.

## Locking

`RunLock.acquire` creates `locks/runner.lock` with `CREATE_NEW` and writes:

```text
task=<id>
pid=<pid>
runner=<id>
token=<uuid>
created=<timestamp>
```

- second runner is rejected (file exists);
- release checks the token and refuses to delete a lock it does not own;
- no automatic stealing;
- a stale lock is refused and shown for manual recovery. Automatic staleness
  detection/stealing is not implemented (conservative by design).

## Interrupted execution

`classifyInterrupted()` scans `running/*.json`, reads the matching `.meta`, and
writes `reports/<id>-recovery.json` with BLOCKED (PID not alive) or UNKNOWN (PID
alive). It never moves the task back to inbox, never retries it, and never kills a
process. Manual recovery: inspect the meta/report, verify ownership, then move the
file explicitly. Never terminate an unverified process.

## Authorization

All flags default false. The runner rejects before Harness:

- commit, push;
- protected worlds;
- network;
- production-code changes;
- test changes.

`TaskContract` rejects protected-world/network requests at parse time; the runner
returns BLOCKED for other unsupported authorizations. Natural-language text grants
nothing. Harness output cannot grant permissions.

## Reports

JSON and Markdown under `reports/`. JSON includes task ID, schema, state, repository,
branch/HEAD before/after, exit code, timeout, elapsed, reason, timestamp. Markdown
is a concise human summary. Credentials/sensitive environment are never read or
included.

## Conservative execution policy

- nonzero exit -> FAILED
- timeout -> TIMED_OUT
- malformed/missing/empty final -> FAILED
- Harness error event -> FAILED
- exit 0 + nonempty final -> UNVERIFIED
- SUCCEEDED is intentionally unreachable.

## Tests

Fake process (`FakeHarness`) and fake Git provider; no Harness/Gradle/Minecraft/
GitHub/network.

Covered:

- help/validate/run/status/report and exit codes;
- valid/invalid tasks;
- duplicate task rejection;
- branch mismatch;
- production authorization block;
- second-runner lock rejection;
- interrupted running task visibility and no auto-move;
- default-deny authorization;
- process adapter output/stdin/exit/timeout from Phase 4.

Test commands:

```powershell
$classes='build/integration/orchestrator-classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$files=@()
$files += Get-ChildItem 'tools/harness-orchestrator/src/main/java' -Recurse -Filter *.java | % FullName
$files += Get-ChildItem 'tools/harness-orchestrator/src/test/java' -Recurse -Filter *.java | % FullName
javac -encoding UTF-8 -d $classes $files
java -cp $classes com.chipoodle.harness.orchestrator.OrchestratorTest
java -cp $classes com.chipoodle.harness.orchestrator.RealProcessAdapterTest
java -cp $classes com.chipoodle.harness.orchestrator.TaskRunnerTest
```

Expected: all three print OK.

## Limitations

- Real Harness execution blocked by credentials; no end-to-end run.
- No HEAD-mismatch/dirty-baseline/Git-failure automated tests yet.
- No stale-lock ownership diagnostic beyond refusal.
- No orphan-running-task classification/report.
- Reports are compact, not the full task-report schema.
- No automatic recovery/retry by design.

## Phase 5.2 — complete report contract

Schema: `devilrpg.report/v2`. One map-backed `ExecutionReport` produces both JSON and
Markdown; Markdown embeds the same JSON, so the two formats cannot diverge.

Required sections:

```text
schema, reportId, taskId, taskSchema, createdAt, orchestratorVersion, state, reason,
task {objective, acceptance, repository, branch, expectedHead, allowedPaths,
      forbiddenPaths, timeoutSeconds, profile},
repository {path, before, after, inspection},
authorization [flag, requested, effective, decision, reason],
lifecycle {validation, processStarted, pid, startedAt, endedAt, exitCode,
           timedOut, elapsedMs, interrupted, terminationAttempted,
           terminationConfirmed},
events [{seq,type,payload,timestamp,parse}],
stderr, finalResponse {received,text,empty,semanticsVerified,authoritative},
filesChanged [{path,category,allowed,forbidden,complete}],
validations [{id,executed,exitCode,result,evidence,timestamp}],
unresolved [], recovery {required,lastState,taskId,pid,lockStatus,
                         repositoryMayHaveChanged,instructions}
```

Rules:

- PID/start/end timestamps are `UNKNOWN` with the current adapter.
- `authorization` always shows requested/effective/decision/reason; effective is
  false under default-deny.
- Unknown/malformed NDJSON lines are retained in order; stderr is separate.
- `finalResponse.semanticsVerified` and `authoritative` are false.
- `SUCCEEDED` is rejected by the validator.
- BLOCKED/UNKNOWN/TIMED_OUT requires recovery information.
- Validation entries distinguish executed from NOT_RUN.
- Files changed are marked `complete:false` and category/allowed/forbidden UNKNOWN
  unless safely attributable.
- JSON and Markdown use `CREATE_NEW`; collisions preflight-block the task and
  preserve existing files byte-for-byte.
- If report generation/validation fails, the task is moved to failed and is never
  reported successful.

Completeness validator rejects: missing sections, process started without events
or final, `SUCCEEDED`, and missing recovery for blocked/timeout states.

Report tests: `ExecutionReportTest`; collision tests in `TaskRunnerTest`.
