# A2 idx3 — Local Stair Counter Write Investigation

Status: **UNRESOLVED — A2 FAIL / OPEN.** C4 remains PASS in preserved evidence.

## Baseline

- Repository `D:\Documents\DevilRpg-autonomous`.
- Branch `feature/autonomous-village`, HEAD `518fee1`.
- No reset/clean/stash/revert/commit/push; all pre-existing work preserved.
- Focused runs: `a2n-20261114-003310`, `a2n-20261114-004219`.
- No GUI; all worlds/artifacts under `build/a2n/<runId>/`.

## Complete writer inventory — `NavExecutor`

| Line | Operation | Context |
|---|---|---|
| 1247/1250 | reset both counters | executor/mission initialization |
| 1699/1700 | reset both | verified stair-access clear |
| 1755 | `fallosDeEscalonDeObjetivo = 10` | local step counter already at 64 at `escalonarObjetivoLocal` entry |
| 1763 | `fallosDeEscalonDeObjetivo++` | `elegirObjetivoLocal()` returned null |
| 1768 | `fallosDeEscalonDeObjetivo = 0` | a candidate was selected |
| **1778** | **`escalonesDeObjetivo++`** | **accepted local stair/objective step** |
| 2071/2075 | reset both | mission/objective transition |
| 2138/2141 | reset both | mission/objective transition |
| 2216 | reset steps | mission/objective transition |
| 2588/2589 | reset both | terminal rearm |
| 2615/2616 | reset both | terrain-change rearm in `fallar` |
| 3002 | reset steps | verified progress at route close |
| 3747/3748 | reset both | verified terrain change |
| 3759/3760 | reset both | one-shot BREAK terrain rearm |

`escalonesDeObjetivo` is an authoritative mutable executor field. There is no duplicate counter
object, deserialization or snapshot restore for it.

## Exact 0 → 64 writer

`NavExecutor.escalonarObjetivoLocal()` line 1778:

```java
escalonesDeObjetivo++;
```

Runtime evidence (`a2n-20261114-004219`, UUID `40f10aeb-c44a-4787-9f24-634f92ddde5a`):

```text
A2_LOCAL_STAIR_ENTER tick=148 local=1/64
A2_LOCAL_STAIR_WRITE  tick=148 op=INC_STEP new=2 objective=608,95,601
A2_LOCAL_STAIR_ENTER tick=150 local=2/64
A2_LOCAL_STAIR_WRITE  tick=150 op=INC_STEP new=3 objective=608,95,601
...
```

The mission is `616,71,578`; `elegirObjetivoLocal()` repeatedly returns the same-level
`608,95,601`, which does not descend toward the mission. `escalonarObjetivoLocal()` accepts it
and increments the counter every ~2 ticks.

## Is 64 a candidate count or accounting defect?

- 64 is **actual cumulative increments**, one per accepted local planning step.
- It is not 64 failed stair placements and not one-tick loop.
- The earlier same-tick “0 → 64” reading was a reporting/state mismatch before per-write
  instrumentation; the write trace proves incremental consumption.
- At 64, line 1755 forces `fallosDeEscalonDeObjetivo = 10`. The “10/10 failures” is therefore
  an accounting artifact, not ten genuine placement failures.

## idx3 vs idx4 first divergence

- idx4 (`e9c0f390…`) selects a genuine descending continuation and reaches
  `A2_STAIR_FALLBACK_RESULT`; it places/verifies `589,92,511`.
- idx3 selects same-level `608,95,601` repeatedly and increments the counter without progress.
- The fallback is functional; the divergence is candidate acceptance in
  `escalonarObjetivoLocal`.

## Root cause

`escalonarObjetivoLocal()` treats a same-level, non-progressing candidate as a valid local step.
After 64 such candidates the local limit sets the failure counter to 10. The executor then enters
`FAILED`; terminal rearmes are consumed, and `tryPlan`/fallback become unreachable.

## Correction

**No acceptance correction was applied.** A naive “candidate must be closer to the mission”
guard would also reject idx4’s valid `588 → 589,92,511` step; that would be speculative and could
break the working fallback. Only instrumentation and bounded rearm/reset diagnostics were added.

## Tests and validation

```text
compileJava / compileTestJava        BUILD SUCCESSFUL
NAV_TERRAIN_REARM_POLICY_TEST         OK (9 assertions)
python tools/lint_aldea.py --strict   OK
git diff --check                      PASS
```

No new regression test was added because no safe acceptance fix has been proven.

## Focused runtime

- `a2n-20261114-003310`: A2 FAIL; t=3900 3 alive, 5 dead, 5 ever entered, 0 inside.
- `a2n-20261114-004219`: per-write trace proving line 1778 and `608,95,601`; A2 FAIL, 0 inside.

## Verdicts

- **A2: FAIL / OPEN.** Threshold remains `SIN UMBRAL CONTRATADO`; no PASS invented.
- **C4: PASS preserved**; no C4/recovery/worker changes.

## Exact next action

Instrument `elegirObjetivoLocal()` candidate generation for idx3 (anchor, candidate, dy,
distance-to-mission, transition) and add a progress predicate that accepts idx4’s valid
descending step while rejecting idx3’s same-level `608,95,601`. Regression-test both before
changing line 1778. Do not implement the mobility proposal.

## 2026-11-14 01:25 — progress predicate added

- `NavLocalObjectiveProgressPolicy` rejects same-level/higher local candidates when the mission requires descent, unless a non-WALK route continuation or pending stair exists.
- Focused run `a2n-20261114-011440`: idx3 descended to 617,86,600 with local 4/64 and explicit rejection reason; idx4 still places 589,92,511.
- Policy test 6/6; compile/lint/diff pass; A2 remains FAIL/OPEN; C4 PASS preserved.
