# A2 Phase55 — global A* diagnostics static validation (2026-11-19)

## 1. Result

**PASS — static validation only.**

`NavPlanner.java` at documentation HEAD is byte-identical to implementation SHA
`10719ff82465a0d8997cb2af3af23c9f71292b19` (blob `5666e42693fe2e90dff1d2ac1004b59b9b62d7cd`).
Compile, Gradle tests, strict lint and diff-check pass. No runtime was executed.

- RUN_ID: `phase55-diag-static-20261119-1038`
- DATE: 2026-11-19 10:38 America/Mexico_City
- BRANCH: `feature/autonomous-village`
- INITIAL_HEAD: `646a099f53cbaa52e71db0688d0061ef3e657ea9`
- FINAL_HEAD at validation: `50fb7a5d8244e8eb5e050bf4800458784de6f8fe`
- TESTED_PRODUCTION_SHA: `10719ff82465a0d8997cb2af3af23c9f71292b19`
- NAVPLANNER_BLOB_SHA: `5666e42693fe2e90dff1d2ac1004b59b9b62d7cd`
- PRODUCTION_MODIFIED_BY_HARNESS: false
- RUNTIME_EXECUTED: false
- MINECRAFT_LAUNCHED: false
- WORLDS_MODIFIED: false
- FIXTURES_MODIFIED: false

## 2. Git verification

```text
Local was 13 commits behind origin; fast-forward only.
Production diff 10719ff8..HEAD (src/build.gradle): empty.
NavPlanner.java blob at 10719ff8 == blob at HEAD.
Worktree production files: clean.
Untracked preserved: .a2-backups/, src/foodTest/, tools/a2n/*.partial,
tools/a2n/consultar-c4.ps1, tools/a2n/lanzar-c4.ps1, tools/a5-closure/.
```

## 3. Commands and results

```text
.\gradlew.bat compileJava --console=plain: PASS (exit 0)
.\gradlew.bat test --console=plain: PASS (exit 0)
python tools\lint_aldea.py --strict: PASS (exit 0)
git diff --check: PASS (exit 0)
```

`NAV_PLANNER_STATE_TEST`: PASS, 54 executed assertions (`ok:` lines), no `FALLO:` markers.

Warnings: only the pre-existing Gradle deprecation notice and Java deprecation note; no diagnostic
implementation warning or error.

## 4. API check

```text
LevelReader.hasChunkAt(candidate) is accepted by the compiler in the
NeoForge 21.1.249 / Minecraft 1.21.1 environment.
```

Telemetry gating:

```java
private static final boolean TRACE_GLOBAL_SEARCH =
        Boolean.getBoolean("devilrpg.a2n.traceGlobalSearch");

if (TRACE_GLOBAL_SEARCH && NavTrace.on()) {
    NavTrace.ev(event, detail);
}
```

Both `-Ddevilrpg.a2n.traceNav=true` and `-Ddevilrpg.a2n.traceGlobalSearch=true` are required; default is off.

## 5. Scope

Only `NavPlanner.java` diagnostics were validated. No check was made of runtime event correctness or
volume; no A2 Run1, C4, Run2 or Full12K was launched. The previous Run1 authorization
(`a2n-20261119-093456`) remains consumed.

## 6. Integrity

```text
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

Logs:

```text
build/phase55diag-compile.log
build/phase55diag-test.log
build/phase55diag-lint.log
```

## 7. Conclusion

The opt-in telemetry implementation is statically valid and ready for a separately authorized runtime
diagnostic. It does not fix or alter A2 behavior. A2 remains OPEN.
