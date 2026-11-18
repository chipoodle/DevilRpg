# A2 Phase 32 — non-entrant forensic diagnosis (2026-11-18)

## 1. Result and classification

**COMPLETE.** All eight attackers are accounted for. The 2/8 strict result is explained by two
post-entry deaths, two terminal navigation failures and four timeout cases dominated by repeated
verified source snaps. No production/test change was made.

- Branch: `feature/autonomous-village`
- Initial/final HEAD: `8d4c9b4fa2501d06e1a7e96e96ed2adc000a7cf1`
- Production: `ec6aa339cf4abd1691092591c1379792ffdc1575`
- Run: `a2n-20261117-190320`
- Classification: `COMPLETE — 2/8 explained; four timeout causes partially confirmed`

## 2. Artifacts inspected

- `build/a2n/a2n-20261117-190320/nav-trace.log`
- `build/a2n/a2n-20261117-190320/srv.log`
- `build/a2n/a2n-20261117-190320/fin.txt`
- `build/a2n/a2n-20261117-190320/launcher-status.txt`
- `build/a2n/a2n-20261117-190320/estado.txt` (empty)
- `A2NavHarness.java`, `NavExecutor.java`, Phase 29/30/31 reports.

No new runtime was executed.

## 3. Strict/perimeter implementation

From `A2NavHarness.java`:

```java
R_RADIO_ALDEA = 71
R_RADIO_ANILLO = 62
```

- Perimeter: `dist <= R_RADIO_ALDEA`, cumulative `rEntroPerimetro[k]`, logged once as
  `ENTRO-PERIMETRO`.
- Strict: `dist <= R_RADIO_ANILLO`, current `rDentro` and `rDentroParedAhora[k]`, logged as
  `DENTRO-PARED(ahora)`; `rDentroParedAhora` is reset to false whenever the mob is outside 62.
- `rDentro` is **current**, not cumulative. The 8/8 gate uses the first strict-entry event per
  attacker, while the harness summary reports `dentroParedAhora=0` at the end.
- `CRITERIOS` log explicitly says thresholds are not defined in the repository; the acceptance
  convention comes from the A2 gate documentation.
- An attacker that enters and later dies still counts as a strict entrant for the gate.

This ambiguity is real: the raw harness summary is current-radius only, while the gate counts
cumulative first entries.

## 4. Individual timelines

| idx | Spawn | Plan/traversal | Perimeter | Strict | Terminal | Final |
|---|---|---|---|---|---|---|
| 1 | `7611598e…` `728,64,600` | yes | t387 | **t593** | died t2601 | `704,71,581`, health0 |
| 2 | `d2bf3439…` `661,73,628` | route yes | — | — | `NAV_FAIL` t~300; no safe source neighbor | `662,72,608`, FAILED |
| 3 | `ded4119b…` `594,96,600` | yes | — | — | timeout; 139 snap resets | `605,96,600`, PLANNING |
| 4 | `c5ec81bb…` `566,122,533` | yes | — | — | timeout; 135 snap resets | `566,123,527`, TRAVERSING |
| 5 | `05e3bcae…` `594,87,466` | yes | — | — | timeout; 141 snap resets | `595,87,466`, TRAVERSING |
| 6 | `ff5c31e0…` `661,72,438` | yes | t287 | **t650** | died t1293 | `656,71,515`, health0 |
| 7 | `df02b150…` `728,63,466` | route yes | — | — | `NAV_FAIL` t3607; BUILD budget | `707,63,459`, FAILED |
| 8 | `23c25c6d…` `756,63,533` | yes | — | — | timeout; 136 snap resets | `747,64,534`, TRAVERSING |

### idx1 / idx6 (controls)

Both reach the strict ring and later die in combat. No `NAV_FAIL` precedes death; they are successful
entries, not navigation failures.

## 5. Root causes

### idx2 `d2bf3439` — CONFIRMED

```text
NAV_FAIL {DROP source-cell recovery found no safe supported AABB-overlapping neighbor
 at BlockPos{662,72,608}}
5 failures, rearmes 0..4/4
```

No snap reset. Terminal source recovery failure; independent of Phase 29/31 reset.

### idx7 `df02b150` — CONFIRMED

```text
NAV_FAIL {presupuesto agotado sin progreso (10/10) hacia 705,68,459}
```

BUILD/local progress exhaustion at `707,63,459`. No snap reset; independent.

### idx3 / idx4 / idx5 / idx8 — PARTIALLY_CONFIRMED

All four:

- complete verified snaps repeatedly (`SNAP_POST_RESET` counts 139/135/141/136);
- reset cadence ~42–58 ticks;
- have `POST_SNAP_FIRST_PLAN decision=ACCEPT` and `POST_SNAP_ROUTE_START state=TRAVERSING`;
- have **zero** `POST_SNAP_GUARD`, zero duplicate-signature rejection;
- make only a few blocks of net progress and reach the 6000-tick timeout with no terminal `NAV_FAIL`.

| idx | snap resets | first | last | net displacement |
|---|---:|---:|---:|---|
| 3 | 139 | t163 | t5999 | ~11 blocks |
| 4 | 135 | t62 | t5982 | ~6 blocks |
| 5 | 141 | t56 | t5977 | ~1 block |
| 8 | 136 | t254 | t5974 | ~9 blocks |

The confirmed mechanism is **snap churn**: the executor repeatedly re-enters a verified source snap
instead of accumulating route progress. Each snap completes legitimately, but the cycle consumes the
run before the attacker can cross 71/62. Phase 29 exclusivity and Phase 31 reset are working for each
cycle (`POST_SNAP_ROUTE_START`), so this is not the earlier ownership/signature defect.

The exact geometric reason each next source cell becomes invalid again is not logged; therefore the
ultimate churn cause is only partially confirmed.

### Successes/deaths

idx1 and idx6 entered and later died; no navigation failure. Their deaths are normal post-entry combat
outcomes, not entry failures.

## 6. Consolidated table

| Attacker | Spawn | Plan | Traversal | Perimeter | Strict | Terminal | Root cause |
|---|---|---|---|---|---|---|---|
| 1 | OK | yes | yes | t387 | t593 | died t2601 | post-entry combat |
| 2 | OK | yes | yes | — | — | NAV_FAIL t~300 | no safe source neighbor |
| 3 | OK | yes | yes | — | — | timeout | snap churn (partial) |
| 4 | OK | yes | yes | — | — | timeout | snap churn (partial) |
| 5 | OK | yes | yes | — | — | timeout | snap churn (partial) |
| 6 | OK | yes | yes | t287 | t650 | died t1293 | post-entry combat |
| 7 | OK | yes | yes | — | — | NAV_FAIL t3607 | BUILD budget exhaustion |
| 8 | OK | yes | yes | — | — | timeout | snap churn (partial) |

## 7. Why 2/8 strict

Only idx1 and idx6 crossed the 62-block strict radius, at t593 and t650. They later died, which is why
the harness final summary shows `dentroParedAhora=0` and no `RAID RESULTADO` line for them. The gate
counts their first strict-entry events.

The other six never crossed 62 by t6000:
- idx2/idx7 terminated early on navigation failures;
- idx3/4/5/8 remained outside while cycling snaps and moving only a few blocks.

## 8. Phase 29 audit

For all snap completions observed:

```text
SNAP_MOVE_OWNER + SNAP_COMPLETE present
POST_SNAP_ROUTE_START enters TRAVERSING
no POST_SNAP_GUARD for idx3/4/5/8
```

No contradictory route command was found during the snaps examined in Phase 29/31. Phase 29 contract
remains intact. The repeated snaps in idx3/4/5/8 are not a Phase 29 ownership regression; they are
verified completions followed by new source invalidations.

## 9. Phase 31 audit

```text
SNAP_POST_RESET occurrences: 580
POST_SNAP_FIRST_PLAN:        579
POST_SNAP_ROUTE_START:       579
POST_SNAP_GUARD:               5 (later genuine oscillation)
REPLAN_SIGNATURE_DECISION:     1 (normal duplicate after later oscillation)
```

For idx3/4/5/8: reset fires, first plan is ACCEPT, route starts TRAVERSING, no post-snap guard and no
duplicate rejection. Phase 31 is healthy. The remaining failures are downstream churn/timeout or
independent navigation failures.

## 10. Signature sentinel observation

`resetPostSnapNavigationState` sets `lastRouteSignature = 0`. The duplicate comparison still uses:

```java
if (acceptedRoute.signature() == lastRouteSignature)
```

`NavSnapPostResetPolicy.allowsFirstPostSnapSignature(...)` is **not** used in that comparison.
A real route signature of exactly `0` could theoretically collide with the sentinel and be rejected
as a duplicate on the first post-snap plan. No signature `0` was observed in this run; another
signature (e.g. `-223992152`) was accepted normally. This is a robustness observation only, not a
confirmed runtime cause, and was not modified.

## 11. Missing evidence / limitations

- Exact per-step reason the source cell becomes invalid again for idx3/4/5/8 (block states, support at
  each snap boundary).
- Exact block/collision dump around their routes.
- No new runtime; all conclusions reuse `a2n-20261117-190320`.
- Strict/perimeter final-summary ambiguity (`dentroParedAhora` vs cumulative gate events) is documented
  but not changed.

## 12. Recommended next phase

Phase 33 should diagnose the snap-churn geometry for idx3/4/5/8: why a verified source snap completes,
the route starts, and the next source cell immediately becomes invalid again, consuming ~43 ticks per
block. Required evidence: per-snap candidate cell, support/fit predicates at completion and the next
tick, block states around the transition, and net displacement per snap. Do not change Phase 29/31
contracts or budgets. A2 remains OPEN; Run 2 and 12K remain prohibited.
