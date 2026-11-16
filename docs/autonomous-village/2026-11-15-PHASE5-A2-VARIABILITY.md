# Phase 5 — A2 Variability Forensics and Handoff Reconciliation (2026-11-15)

## Git baseline

- Initial local/remote HEAD: `18b76ee1bbfeca5c18beef024f79b8f8e7fa3426`.
- Branch `feature/autonomous-village`; no divergence.
- No production code changed in Phase 5.

## A2 per-index forensic comparison

`inside` = first `DENTRO-PARED` tick; `—` means no enclosure entry within 6000 ticks.

| idx | 145020 | 150410 | 184205 | 185459 | 194632 |
|---|---|---|---|---|---|
| 1 | 512 | 512 | 512 | 512 | 512 |
| 2 | 667 | 667 | 668 | 667 | 667 |
| 3 | 1494 | 1940 | 1664 | 1497 | 1725 |
| 4 | — | 4275 | — | 3775 | — |
| 5 | 1296 | 1313 | 1393 | 1288 | 1297 |
| 6 | 649 | 649 | 659 | 649 | 649 |
| 7 | — | 5113 | 2922 | 3319 | 3191 |
| 8 | — | perimeter only (5866) | 2920 | — | 3147 |

Enclosure-entry counts: **5, 7, 7, 7, 7**. All runs spawned 8/8 and destroyed the bell with one `SIEGE_COMPLETED`.

### Findings

- The 5/8 run (`145020`) had idx4, idx7 and idx8 alive outside at closure. There was no terminal FAILED; these are late/delayed entries, not three independent terminal navigation failures.
- idx4 is the most frequent non-entrant (145020, 184205, 194632); it entered late in 150410/185459.
- idx8 is variable: absent in 145020/185459, perimeter-only in 150410, inside in 184205/194632.
- idx7 entered in four of five runs; only 145020 missed the enclosure.
- Every entrant died in normal guard combat; no death is classified as navigation failure.
- `150410` was previously called 8/8 “entered”; the strict `DENTRO-PARED` count is 7/8 because idx8 reached only the perimeter. Historical reports are preserved; this correction clarifies the metric.

Classification of non-entrants: **insufficient time / delayed entry** is the dominant category; no shared terminal navigation defect is proven. The elevated-BREAK support bug remains fixed and no support-floor selection recurred.

## A2 status

Focused A2 remains **OPEN** because one valid run reached only 5/8. Full 12k remains unrun. No code changed.

## C4

Valid focused passes at 12/12: `145020`, `150410`, `184205`, `185459`, `194632` — **5/5 consecutive**.

Save/reload: no established harness path. `A2NavHarness` has snapshot/save (`saveEverything`) but no save → stop → reload → resume acceptance flow for C4. No persistence PASS is claimed. A separate controlled persistence test is required.

## Policy and handoff

- Canonical reading order remains policy → NEXT → CONTINUAR → root CONTINUAR → PENDIENTE → referenced reports.
- NEXT updated with Phase-5 evidence, 5/5 C4, artifact inventory.
- PENDIENTE current-status notice updated with the actual introducing commit (see below).

## Worlds

Unchanged and verified:
- A2: 15,966,366 bytes; SHA-256 `BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7`.
- C4: 16,044,629 bytes; SHA-256 `11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF`.

Both copies match; no `session.lock`; originals untouched.

## Tests

```text
gradlew test: PASS
NavBreakTargetPolicyTest: OK (3)
VillagerReturnPolicyTest: OK (4)
lint_aldea.py --strict: OK
git diff --check: PASS
```
