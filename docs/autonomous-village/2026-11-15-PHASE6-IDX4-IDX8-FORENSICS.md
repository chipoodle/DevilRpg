# Phase 6 — IDX4 / IDX8 Timeout Forensics (2026-11-15)

## Evidence sources and limitations

Runs: `145020`, `150410`, `184205`, `185459`, `194632`.

Available evidence:
- `srv.log` per-index `RAID RESULTADO` and periodic `RAID NAV` samples (~300 ticks).
- `RAID PROX` samples exist only while proximity changes; several stalled entities stopped emitting after t~3040.
- No per-tick trace for idx4/idx8 in these runs.

Limitations: positions are sampled, not continuous. “Stalled” means no displacement across every available sample in the final interval; it does not prove lack of internal planning.

## IDX4

| Run | Final-1000 evidence | First inside | Classification |
|---|---|---|---|
| 145020 | `588,86,509`, dist 76.53, repeated 4 samples, `RECOVERING` | none | **Stalled** (high confidence) |
| 150410 | `590,75,508` → `621,71,484` → `632,71,508` → `620,71,522` | 4275 | **Progressing; entered** |
| 184205 | `588,86,509`, dist 76.53, repeated 4 samples, `RECOVERING` | none | **Stalled** (high confidence) |
| 185459 | `588,86,509` → `589,77,508` → `592,71,512` → `614,71,522` | 3775 | **Progressing; entered** |
| 194632 | `588,86,509`, dist 76.53, repeated 4 samples, `RECOVERING` | none | **Stalled** (high confidence) |

The three non-entries are **not merely slow**: idx4 was motionless at `588,86,509` for the entire sampled final interval. The two successes show the same start point followed by a descent to y71 and horizontal entry. Leading mechanism: vertical/descent access stalled on the high shelf; more time at 6000 would not move a stationary entity.

## IDX8

| Run | Final-1000 evidence | First inside | Classification |
|---|---|---|---|
| 145020 | `702,62/63,457`, dist 85.89, repeated 4 samples | none | **Stalled** (high confidence) |
| 150410 | `700,61,456` → `693,62,452` → `682,69,448` → `675,71,461`; dist 85.83→72.93 | perimeter only at 5866 | **Progressing but timed out** (medium-high) |
| 184205 | enters at 2920; final inside `616,71,535` dist 44.19 | 2920 | **Progressing; entered** |
| 185459 | `700,61/62,456`, dist 85.89/85.90, repeated 4 samples | none | **Stalled** (high confidence) |
| 194632 | `732,62,585` → `720,66,597` → `717,71,576` → `712,72,545`; enters 3147 | 3147 | **Progressing; entered** |

IDX8 has two modes: stall at the eastern outcrop (`700–702,456/457`) or real progress/entry. In 150410 it moved 12.9 blocks closer in the last ~900 ticks but was still ~11 blocks outside the wall radius when the 6000-tick window closed. This is the only case where timeout is a credible primary explanation.

## Quantitative progress summary

- IDX4 non-entries: net displacement **0 blocks** across ≥900 ticks.
- IDX8 stalled runs: net displacement **0–1 block** across ≥900 ticks.
- IDX8 150410: net distance reduction **~12.9 blocks**, still moving at closure.
- IDX8 successful runs: enclosure entry at 2920/3147.
- IDX4 successful runs: enclosure entry at 4275/3775 after a descent from y86/98 to y71.

## Timeout behavior

- Focused closure is the configured `a2n.faseB` value; Phase-2/4/5 runs used **6000**.
- The harness samples at the fixed closure tick; it does not wait for attackers to finish.
- Full A2 acceptance uses **12000** ticks, so the focused 6000 window is a shortened experiment, not the acceptance window.
- Extending the existing focused standard would change its meaning and was not done.
- The stalled idx4/idx8 cases are not explained by the 6000 cutoff; the 150410 idx8 perimeter-only case may be.

## C4 save/reload assessment

`A2NavHarness` supports snapshot/save via `saveEverything` for diagnostic capture, but has **no established save → shutdown → reload → resume C4 acceptance path**. No persistence PASS is claimed. A separate test would need an explicit restart driver and worker-state comparison.

## Smallest discriminating next experiment (proposal only)

Run **one separate observation run** with `FaseB=9000`, same seed/config/budgets/collision, marked `OBSERVATION — NOT ACCEPTANCE`, capturing IDX4/IDX8 every 20 ticks.

- If idx4 remains at `588,86,509` and idx8 at `700–702,456/457` through 9000 → **confirm stall**, not timeout.
- If they move/enter after 6000 → **timeout/delayed route** for those cases.
- Do not retroactively count this as focused acceptance or change the official 6000/12000 criteria.

No production code was changed in Phase 6.

## Worlds

Unchanged and verified:
- A2 `15,966,366` / `BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7`.
- C4 `16,044,629` / `11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF`.

## Tests

```text
gradlew test: PASS
lint_aldea.py --strict: OK
git diff --check: PASS
```
