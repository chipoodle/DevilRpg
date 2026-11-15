# A2 Run13 — Death and Stall Forensics

Run: `a2n-20261114-013023`. Approved threshold: `A2-ACCEPTANCE-CRITERIA.md`.

Verdict: **A2 FAIL** (criteria 2, 3, 4); **C4 PASS**.

## Casualties

Spawned UUIDs / indices:

| idx | UUID |
|---|---|
| 1 | `43892309-2955-4c79-8d17-a014da1bd920` |
| 2 | `d6915450-57eb-4809-b5d1-ce8bee779c98` |
| 3 | `e969739f-f8e5-416f-a513-99332f2b462d` |
| 4 | `edd0206f-3bec-47f4-8946-dd73949306be` |
| 5 | `07ea1b88-d27d-4a80-a1e1-6b85ccc56223` |
| 6 | `cbea2167-0eb0-41d0-b4dc-24ca1813878b` |
| 7 | `6d70bfca-74b5-4b60-a843-35ee5c5e838e` |
| 8 | `5c8e7765-1afb-48d5-9e47-4d8d55bf246f` |

Final survivors: idx3, idx4, idx8. Casualties: idx1, idx2, idx5, idx6, idx7.

### Death evidence

The run contains **no death/damage event, killer, damage type, health-transition or death-tick
record** in `srv.log` or `nav-trace.log`. Only the final `muertos=5` and per-attacker final status
exist. Therefore:

- confirmed cause: **unknown**;
- likely contributing factor: **not established**;
- exact death ticks: **not available**;
- no claim of combat, fall, suffocation or environment is made.

The next run needs a narrow `LivingDeathEvent`/`hurt` diagnostic (UUID, tick, source, amount,
last position, last navigation state) before any death-related correction is justified.

## idx8 first terminal transition

UUID `5c8e7765-1afb-48d5-9e47-4d8d55bf246f`.

First evidence:

```text
NAV_FAIL ... pos=701,63,457 motivo={replans agotados (8/8)} rearmes=0/4
tick ~8041: A2_REARM_ENTER state=FAILED rearmes=0/4 motivo={replans agotados (8/8)}
objetivo=701,67,457 mision=725,71,533
```

Final: `pos=701,62,457`, `estado=FAILED`, local 0/64, `replans=8/8`, route 79 nodes,
last route action `700,65,458:BREAK-ACERCARSE (dist=3.15)`, decision `noConstruir=[objetivo
701,67,457 a desnivel -5 <= caida segura 3 (miY=62)]`.

Cause: replan budget exhaustion against the same `701,67,457` BREAK waypoint; the bounded rearm
resets the counter but the same unreachable waypoint is selected again, so all four terminal
rearms are consumed by tick ~9525. This is a navigation-lifecycle defect independent of idx3’s
fixed same-level candidate.

## idx3 final-1200 diagnosis

Final `617,86,600`, `PLANNING`, local 4/64, failure 1/10. Last local reason:
`candidato local sin progreso vertical ... candidato=618,86,600 dy=0`. The progress policy is
working (no 64/64 stall), but idx3 still does not reach a usable descent/wall entry. Its
`617,86` position is unchanged across late snapshots.

## idx4 final-1200 diagnosis

Final `588,77,507`, `PLANNING`, local 6/64, one verified stair (`589,92,511`). It remains outside;
`global inicio=588,79,507 meta=597,71,533`, route 26. It is not terminal FAILED, but it has no
enclosure progress.

## Shared failure matrix

| Symptom | Attackers | Established cause | Shared path |
|---|---|---|---|
| Deaths | idx1,2,5,6,7 | unknown; telemetry absent | unknown |
| No entry | idx3,4 | local/global approach does not complete; idx3 non-progress rejection still stalls at 617,86 | shared path/route planning |
| Terminal FAILED | idx8 | replans exhausted (8/8), same unreachable BREAK waypoint | shared replan/route retry lifecycle |

## Corrections in this campaign

No production correction was made from this forensic run. The existing local progress predicate
is preserved and validated (6/6); no death or replan fix is justified without the missing evidence.

## Validation

```text
compileJava / compileTestJava          BUILD SUCCESSFUL
NAV_LOCAL_OBJECTIVE_PROGRESS_POLICY_TEST OK (6 assertions)
NAV_TERRAIN_REARM_POLICY_TEST           OK (9 assertions)
python tools/lint_aldea.py --strict     OK
git diff --check                        PASS
```

## Next action

Add opt-in death/damage telemetry and run a focused reproduction; separately inspect why the
`701,67,457` BREAK waypoint is selected after each rearm. Fix the shared replan lifecycle only
after that evidence, then rerun the full 12000 fixture. No threshold changes.
