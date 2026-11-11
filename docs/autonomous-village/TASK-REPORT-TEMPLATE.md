# Task Report Template

```json
{
  "schema": "devilrpg.task-report/v1",
  "task_id": "TASK-YYYYMMDD-NNN",
  "run_id": "optional-runtime-run-id",
  "status": "PASS|FAIL|PARTIAL|NOT_RUN|BLOCKED",
  "started_at": "RFC3339",
  "finished_at": "RFC3339",
  "repository": {
    "branch": "feature/autonomous-village",
    "head": "40-hex",
    "staged": 0,
    "unstaged": 0,
    "untracked": 0
  },
  "scope": {
    "allowed_paths": ["docs/...", "src/..."],
    "forbidden_paths": ["run/saves/New World", "run/saves/A2-Observation"],
    "production_changes_allowed": false
  },
  "changes": [
    {"path": "relative/path", "kind": "created|modified|deleted", "reason": "..."}
  ],
  "validation": [
    {"command": "gradlew compileJava", "exit_code": 0, "result": "PASS"},
    {"command": "python tools/lint_aldea.py --strict", "exit_code": 0, "result": "PASS"}
  ],
  "runtime_evidence": [
    {
      "run_id": "...",
      "world": "build/.../game/saves/A5WALL",
      "uuid": "...",
      "objective": 0,
      "phase": "...",
      "controller": "...",
      "target": "x,y,z",
      "nbt": "{...}",
      "inventory": {"logs": 0, "saplings": 0, "bone_meal": 0},
      "storage": {"logs": 0},
      "result": "PASS|FAIL|NOT_RUN",
      "evidence": "log excerpt or path"
    }
  ],
  "unverified": ["criterion", "reason"],
  "blockers": ["exact technical blocker"],
  "authorization": {
    "commit": false,
    "push": false,
    "protected_worlds": false
  },
  "notes": "short factual summary"
}
```
