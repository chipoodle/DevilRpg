# Harness CLI Protocol — Characterization

Status: PARTIAL. Credential provisioning blocked the success path.
Audit date: 2026-11-07. No orchestrator implemented.

## 1. Verified command syntax

```powershell
$dsh = 'D:\programas\deepseek\resources\runtime\cli\bin\dsh.cmd'
& $dsh --version
& $dsh --help
& $dsh --profile headless --json "TASK TEXT"
& $dsh --profile headless "TASK TEXT"
'TASK TEXT' | & $dsh --profile headless --json
& $dsh --profile headless --session-id <id> "TASK TEXT"
```

`--help` documents argument task, `-` / stdin task, `--json` NDJSON events, and
`--session-id` continuation. `--version` returns `0.2.1-rc.2`.

## 2. Verified stdin behavior

Invocation was accepted and produced the same event envelope as the argument form.
No success-path output comparison is possible because both stopped at
`MISSING_CREDENTIAL`. Encoding/newline equivalence on a successful answer is
**UNKNOWN**.

## 3. Actual event schema (sanitized)

One JSON object per stdout line:

```json
{"type":"session","sessionId":"session-<uuid>","cwd":"D:\\Documents\\DevilRpg-autonomous"}
{"type":"status","phase":"turn_start","turn":1}
{"type":"status","phase":"step_start","turn":1,"step":1}
{"type":"status","phase":"step_end","turn":1,"step":1}
{"type":"status","phase":"turn_end","turn":1,"reason":{"kind":"error","error":{"message":"<error>","code":"MISSING_CREDENTIAL"}}}
{"type":"final","text":""}
```

- Every observed stdout line was valid JSON.
- `session` is first.
- Failure is represented in `turn_end.reason.kind = "error"` plus an error code.
- `final.text` is empty on failure.
- Success event types and final text semantics are **UNKNOWN**.
- Tool/command events were not observed.

## 4. Exit codes

| Invocation | Exit |
|---|---|
| `--version` | 0 |
| `--help` | 0 |
| headless task, credential missing | 1 |
| non-JSON headless, credential missing | 1 |

Nonzero exit is accompanied by a `dsh: MISSING_CREDENTIAL...` stderr line. No
successful task exit has been observed.

## 5. Session continuation

`--session-id` is documented and accepted by the CLI parser. Actual context
continuation is **NOT VERIFIED** because no session can execute without
credentials. Do not infer continuation from the flag.

## 6. Timeout and process lifecycle

- CLI exits in ~2.7–3.0 s on this failure; process code 1.
- No orphan CLI process remained after the runs.
- Existing desktop Harness processes were already running and were not touched.
- Incremental flush of a long stream and external timeout/kill behavior are
  **UNKNOWN**; no long task was run.
- Recommend an external process timeout plus explicit process-tree termination by
  PID in the orchestrator, with no global process kill.

## 7. Credential blocker (critical)

The CLI requires an API key via the credentials service or an environment
variable:

```text
llm-deepseek: no API key for provider route "deepseek-official";
store DEEPSEEK_API_KEY through the credentials service, or export it.
```

Under this task, credentials could not be read or exported. Therefore:

- success-path PONG output: NOT VERIFIED;
- final-answer extraction: NOT VERIFIED;
- session context: NOT VERIFIED;
- tool execution and workspace effects: NOT VERIFIED.

No credentials or secrets were accessed.

## 8. Capability matrix

| Item | Status |
|---|---|
| CLI exists and version works | VERIFIED |
| Headless profile accepts arg task | VERIFIED (preflight reached) |
| Headless profile accepts stdin task | VERIFIED (preflight reached) |
| `--json` NDJSON envelope | VERIFIED |
| Error code in `turn_end` | VERIFIED |
| Nonzero exit on missing credential | VERIFIED |
| Successful final answer | UNKNOWN |
| Final answer uniqueness | UNKNOWN |
| Session continuation | UNKNOWN |
| Timeout/kill semantics | UNKNOWN |
| Incremental streaming | PARTIAL |
| MCP/public API | NOT AVAILABLE in audited CLI help |
| Structured report file | NOT AVAILABLE (NDJSON only) |

## 9. Recommended orchestrator interface

1. Preflight: `dsh --version`, then a credential-capability check that does not
   print secrets. The owner must provision `DEEPSEEK_API_KEY` through the
   credentials service or an approved launcher environment.
2. Invoke:

```text
dsh --profile headless --json "<task>"
```

3. Parse stdout line-by-line as JSON. Ignore non-JSON lines but record them.
4. Require a `session` event and a terminal `final` event.
5. Treat nonzero exit or `turn_end.reason.kind=error` as FAILED.
6. Use the `final.text` only as the answer after success; do not infer success from
   the presence of status lines.
7. Apply an external timeout by PID; terminate only the spawned process tree.
8. Use `--session-id` only for explicitly approved continuation and verify context
   empirically first.
9. Never read, log, or store credentials. Do not embed keys in JSON contracts.

## 10. Evidence

```text
build/integration/cli-protocol/run-20261107-01-pong/
build/integration/cli-protocol/run-20261107-02-nojson/
build/integration/cli-protocol/run-20261107-03-stdin/
build/integration/cli-protocol/analysis.md
```

Each contains stdout, stderr, and exit/duration recorded in analysis.md.

## 11. Recommendation

CLI syntax, JSON envelope, and failure semantics are sufficiently characterized to
start the orchestrator skeleton, but **end-to-end execution cannot be accepted**
until an authorized credential path is provided and a successful harmless task plus
session-continuation test are run.
