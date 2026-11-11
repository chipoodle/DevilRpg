# Harness Orchestrator Skeleton (Phase 3)

Status: IMPLEMENTED and UNIT-TESTED; end-to-end Harness execution remains
UNVERIFIED/BLOCKED by credential provisioning. No real Harness task was run.

## Architecture

```text
task JSON (devilrpg.task/v1)
  -> TaskContract parse/validate
  -> Orchestrator state machine
  -> ProcessAdapter (fake in tests, real CLI in production)
       dsh --profile headless --json -
  -> NdjsonParser
  -> Report JSON
```

No Minecraft Gradle integration. Java 21, no third-party dependencies.

## Module layout

```text
tools/harness-orchestrator/
  src/main/java/com/chipoodle/harness/orchestrator/
    Json.java                 dependency-free JSON parser
    TaskContract.java         contract + authorization validation
    NdjsonParser.java         session/status/final, errors, malformed lines
    ProcessAdapter.java       interface + result record
    RealProcessAdapter.java   argument-safe ProcessBuilder, timeout, tree kill
    Orchestrator.java         state machine
    Report.java               JSON execution report
  src/test/java/.../OrchestratorTest.java
```

## Task contract

Required: `schema=devilrpg.task/v1`, `task_id`, existing `repository`,
`branch`, `allowed_paths`, `objective`, `acceptance`.

Optional: `expected_head`, `forbidden_paths`, `timeout_seconds` (default 1800,
1..86400), `profile` (only `headless`).

`authorization` object; every flag defaults false:

```json
{"commit":false,"push":false,"protected_worlds":false,
 "network":false,"production_code":false,"test_changes":false}
```

Rejected:

- unknown schema;
- missing required fields;
- non-directory repository;
- `..` paths;
- protected-world paths (`run/saves/New World`, `run/saves/A2-Observation`);
- overlapping allowed/forbidden paths;
- protected-world or network authorization;
- non-headless profile.

Commit/push/production/test authorization parses but the skeleton returns
`BLOCKED` before starting a process.

## State machine

```text
QUEUED -> VALIDATING -> RUNNING -> SUCCEEDED | FAILED | BLOCKED | TIMED_OUT
                                      \-> UNVERIFIED
```

Implemented outcomes:

- validation/platform request beyond skeleton -> `BLOCKED`;
- nonzero exit -> `FAILED`;
- timeout -> `TIMED_OUT`;
- malformed NDJSON -> `FAILED`;
- `turn_end.reason.kind=error` -> `FAILED`;
- missing `final` -> `FAILED`;
- empty `final.text` -> `FAILED`;
- exit 0 + nonempty final -> **`UNVERIFIED`**, never `SUCCEEDED`.

`SUCCEEDED` is reserved until a successful final-response contract is observed
and approved.

## Process adapter

`RealProcessAdapter`:

- `ProcessBuilder` argument list, no shell interpolation;
- configurable executable path;
- task sent on stdin (`-`);
- stdout/stderr captured separately in threads;
- exit code and elapsed milliseconds;
- timeout via `waitFor(timeout)`;
- on timeout destroys descendants then the process; no unrelated process kill;
- no credential lookup, logging, or environment dump.

It has not been executed against the real Harness in this phase.

## NDJSON parser

Recognizes `session`, `status`, `final` and unknown event types. Captures raw
sanitized line, malformed lines, final presence/text, and status error code/message.
It does not assume `final.text` is the only answer for future versions; current
skeleton uses it only to classify `UNVERIFIED`.

## Authorization boundaries

- Default-deny for all flags.
- No CLI path can reach commit/push.
- Protected-world paths rejected at parse time.
- No credentials read or stored.
- No GitHub operation.
- No arbitrary shell commands from Harness output.

## Credential gate

Real headless execution fails closed without `DEEPSEEK_API_KEY`, as verified in
`HARNESS-CLI-PROTOCOL.md`. The orchestrator must run only in an owner-approved
environment that provides the credential outside task JSON/logs. Until then, a
real invocation is expected to return `FAILED` with `MISSING_CREDENTIAL`.

## How to test independently

```powershell
$classes='build/integration/orchestrator-classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$files=@()
$files += Get-ChildItem 'tools/harness-orchestrator/src/main/java' -Recurse -Filter *.java | % FullName
$files += Get-ChildItem 'tools/harness-orchestrator/src/test/java' -Recurse -Filter *.java | % FullName
javac -encoding UTF-8 -d $classes $files
java -cp $classes com.chipoodle.harness.orchestrator.OrchestratorTest
```

Expected: `HARNESS_ORCHESTRATOR_TEST: OK`.

Tests use a fake `ProcessAdapter`; they do not launch Harness, Gradle, or Minecraft.

## Limitations

- Real success path and NDJSON tool events unverified.
- Session continuation unverified.
- Timeout/tree-kill not exercised against a live Harness.
- `Report` is a compact JSON string, not the full report template yet.
- No CLI `Main`, no inbox/outbox adapter, no GitHub sync.
- `SUCCEEDED` intentionally unreachable.

## Phase 4 — process adapter hardening (2026-11-07)

### Command correction
The adapter now invokes:

```text
dsh --profile headless --json
```

with the task on stdin. The previously assumed positional `-` was NOT verified by
the real CLI characterization and was removed. Whether the real CLI accepts `-`
remains an unresolved external question.

### Fake executable
`FakeHarness.java` is a test-only Java executable. It supports normal events,
stdin echo, empty final, malformed JSON, unknown event, nonzero exit, argument
capture, and an indefinite hang with PID file. It never invokes Harness, Gradle,
Minecraft, Git, or network.

### Verified with the real `RealProcessAdapter` + fake process
- argument boundaries: spaces/quotes preserved;
- stdin UTF-8 delivery;
- stdout/stderr captured separately;
- multiple events captured;
- empty final, malformed line, unknown event captured by parser;
- nonzero exit code preserved;
- timeout -> `timedOut=true`;
- timed-out process terminated; PID no longer alive; no orphan observed.

### Not verified
- Real Harness success output or `-` syntax;
- child-process tree termination (the hang scenario is single-process);
- incremental streaming timing (adapter buffers until process end);
- interruption of the adapter thread itself;
- real CLI exit/error behavior beyond `MISSING_CREDENTIAL`.

### Test commands
```powershell
javac -encoding UTF-8 -d build/integration/orchestrator-classes <main+test>
java -cp build/integration/orchestrator-classes com.chipoodle.harness.orchestrator.OrchestratorTest
java -cp build/integration/orchestrator-classes com.chipoodle.harness.orchestrator.RealProcessAdapterTest
```
Results: `HARNESS_ORCHESTRATOR_TEST: OK`,
`REAL_PROCESS_ADAPTER_TEST: OK`.

## Task runner (Phase 5)

`Main` and `TaskRunner` add a local queue, CLI, atomic lock, Git gate, and reports.
See `HARNESS-TASK-RUNNER.md`. Real Harness execution remains blocked; fake-process
tests pass.

## Event preservation and report model (Phase 5.2)

`NdjsonParser` preserves ordered parsed events (including unknown types) plus
malformed lines. `ExecutionReport` records each event with sequence/type/raw
payload/UNKNOWN timestamp/parse status, stderr separately, and the final-response
flags. JSON and Markdown share one map-backed model; no second report model exists.
