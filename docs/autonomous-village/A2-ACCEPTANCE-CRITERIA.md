# A2 Walled Raid — Approved Acceptance Criteria

Status: **APPROVED by the user on 2026-11-14.** This contract must not be changed to fit results.

## Criteria

1. All 8 attackers register correctly.
2. At least 6 of 8 attackers enter the walled enclosure before tick 12000.
3. At least 1 attacker is alive inside the enclosure at tick 12000.
4. Surviving attackers are not permanently navigation-blocked:
   - no surviving attacker is in terminal `FAILED` at the final snapshot;
   - no surviving attacker outside the enclosure has zero measurable navigation progress during
     the final 1200 ticks.
5. C4 preserves and demonstrates all established PASS criteria.

## Telemetry definitions

- **Registration:** the harness spawn record for all 8 configured attackers, with unique UUIDs and no spawn failure. `spawneados=8/8`, `fallosSpawn=0`.
- **First enclosure entry:** the existing harness wall geometry and its entry detector (`entraronPerimetroEver` / per-attacker first-entry tick). The wall boundary is the fixture's existing radius/height geometry; no new boundary interpretation is introduced.
- **Alive inside at tick 12000:** the phase-B closing snapshot (`RAID SNAPSHOT t=12000`) with `vivos` and `dentroParedAhora`, plus the per-attacker final state.
- **Terminal FAILED:** per-attacker `estado=FAILED` in the final snapshot.
- **Navigation progress in the final 1200 ticks:** measurable change in position/distance-to-centre or a verified movement/route transition between tick 10800 and 12000. No movement and no transition means zero progress.
- **C4 PASS:** the established `fin.txt` C4 fields: guards fought, professionals resumed, clean job-site assignments, 16 survivors, 12/12 workers alive/resumed, 0 worker casualties, 0 unresolved workers, 4 attackers removed for recovery, 0 instrumentation failures.

## Verdict rules

- **PASS** requires all five criteria. A partial entry count, a focused fixture or a single stair
  placement is not PASS.
- **FAIL** if any criterion is unmet.
- **BLOCKED** only for an external infrastructure failure, not an ordinary navigation defect.

## Current evidence

- Run40: C4 PASS, A2 FAIL (6/8 ever entered, 0 inside at t=12000).
- Focused `a2n-20261114-011440`: idx3 progressed from y=95 to y=86 with local 4/64; idx4
  verified stair `589,92,511`; full A2 not yet passed.
