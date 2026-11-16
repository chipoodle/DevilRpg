# Phase 4 — Repeated Stability and Policy Consistency (2026-11-15)

## Policy reconciliation

- Canonical reading order is now: `CHATGPT-COLLABORATION-POLICY.md` → `NEXT-SESSION.md` → `CONTINUAR.md` → root `CONTINUAR.md` → `PENDIENTE.md` → referenced reports.
- The collaboration policy explicitly declares itself the first document.
- Historical-document precedence is documented: old instructions/HEADs are history unless reconfirmed by the latest verified report; the latest `NEXT-SESSION.md` defines the active task.
- `docs/PENDIENTE.md` has a dated current-status notice at the top. Historical content below it was preserved byte-for-byte after the inserted notice.
- Local-only artifacts are recorded in `NEXT-SESSION.md` and remain uncommitted.

## Git

- Initial local/remote HEAD: `ff9d32d46c848183b6989c58b0f83f0565a876e0`.
- Phase-4 code was not modified; only documentation.
- No force-push, reset, clean, stash or revert.

## A2 focused repetitions

| Run | Spawn | Entered | Bell | `SIEGE_COMPLETED` |
|---|---:|---:|---|---|
| `a2n-20261115-145020` | 8/8 | 5/8 | verified | verified |
| `a2n-20261115-150410` | 8/8 | 8/8 | tick 1284 | one |
| `a2n-20261115-184205` | 8/8 | **7/8** | tick 1200 | one |
| `a2n-20261115-185459` | 8/8 | **7/8** | tick 1103 | one |
| `a2n-20261115-153633` | 8/8 | invalid | — | host suspended |

Valid entry sequence: **5, 8, 7, 7**. All valid runs destroyed the bell and emitted one conquest event. Deaths were legitimate combat. The elevated-BREAK support-floor fix remains in place; no new support-floor regression was observed.

A2 focused status: **PASS for siege mechanics in 3/4 valid runs at ≥7/8 entry; one run 5/8.** Full 12k remains OPEN because the standard “≥6/8 in every valid run” was not met by the 5/8 run.

## C4 repetitions

| Run | Resumed | Job sites |
|---|---:|---|
| `a2n-20261115-145020` | 12/12 | `[1,0,0]` |
| `a2n-20261115-150410` | 12/12 | `[2,0,0]` |
| `a2n-20261115-184205` | **12/12** | `[3,0,0]` |
| `a2n-20261115-185459` | **12/12** | `[1,0,0]` |

C4 focused status: **4/4 consecutive PASS** after the return goal. No worker remained in `VillagerReturnGoal`; the fisherman resumed with its barrel site.

Save/reload: the harness has no established save/reload acceptance path for this scenario; no persistence PASS is claimed.

## Worlds

Unchanged and verified:
- A2: size 15,966,366; SHA-256 `BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7`.
- C4: size 16,044,629; SHA-256 `11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF`.

Both copies in standard and dev saves match; no `session.lock`; no overwrite.

## Tests

```text
gradlew test: PASS
NavBreakTargetPolicyTest: OK (3)
VillagerReturnPolicyTest: OK (4)
lint_aldea.py --strict: OK
git diff --check: PASS
```
