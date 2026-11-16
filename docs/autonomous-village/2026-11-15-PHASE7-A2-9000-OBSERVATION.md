# Phase 7 — A2 9000-Tick Observation (NOT ACCEPTANCE)

## Harness/configuration validation

- `a2n.faseB` sets the observation closure only (`R_FASE_B`); it does not alter AI, budgets, collision, spawn, combat or pathfinding.
- `a2n.faseA=3000`, `a2n.faseB=9000`, seed 12345, same budgets (900/8000) and custom navigation.
- Diagnostic-only `-Da2n.probeTargets=true` logs IDX4/IDX8 every 20 ticks; no production/gameplay code changed.
- The run is explicitly **OBSERVATION — NOT ACCEPTANCE**.

## Observation identity

- Run ID: `a2n-20261115-203001`
- World: `build/a2n/a2n-20261115-203001/game/a2n` (disposable)
- Raw logs: `build/a2n/a2n-20261115-203001/srv.log`
- Target telemetry: `RAID TARGETPROBE idx=4|8` (20-tick interval; 1020 lines)
- Original diagnostic worlds were not used or modified.

## IDX4

### Timeline

| Tick | Position | Distance |
|---:|---|---:|
| 5000 | `588,86,509` | 76.53 |
| 6000 | `588,86,509` | 76.53 |
| 9000 | `603,71,507` | 63.35 |

- 5000–6000: **1 unique position**, no movement, `RECOVERING`, target `588,84,508` (`DROP-EN-CURSO`).
- 6000–9000: **16 unique positions**, descent to y71, then horizontal progress to `603,71,507`.
- Distance reduced by **13.18 blocks** after 6000.
- At 9000 it is stationary again at `603,71,507`, still outside strict enclosure (wall radius 62; distance 63.35).
- It survived and later was removed by harness cleanup after the observation.

**Classification:** **Resumed progress after tick 6000; then stopped just outside strict entry.** The 5/8 historical run was not a permanent stall for IDX4 — the 6000-tick window cut off its route.

Confidence: **high** for the movement measurement; **medium** for whether a longer window would produce `DENTRO-PARED` (it stopped for the final samples).

## IDX8

### Timeline

| Tick | Position | Distance |
|---:|---|---:|
| 5000 | `742,63,501` | 87.21 |
| 6000 | `711,59,469` | 81.21 |
| 9000 | `710,59,469` | 80.49 |

- 5000–6000: **43 unique positions**, net distance reduction ~6 blocks; moving/planning with a villager target.
- 6000–9000: **3 unique positions**, distance 81.21→80.49, target `entity.minecraft.villager`, objective `720,65,470`, state `PLANNING`.
- The route remains `745,64,536 BREAK-VERIFICADO`, but the entity does not advance.
- At 9000 it is still alive, outside, stationary at the eastern outcrop.

**Classification:** **Persistent stall after 6000**, with a live villager target and no enclosure entry. Not a pure timeout case.

Confidence: **high** for lack of displacement over 3000 ticks; **medium** for exact cause (combat-target vs local waypoint interaction).

## Comparison

| Target | 6000 state | 9000 state | Verdict |
|---|---|---|---|
| IDX4 | stationary at shelf | moved to 603,71,507 | timeout/delayed route |
| IDX8 | moving near outcrop | stationary 3 positions | persistent stall |

Historical five-run entry variability is consistent with two distinct behaviors:
1. IDX4/IDX7 can be slow and time out.
2. IDX8 sometimes stalls at the eastern outcrop, independent of the 6000 cutoff.

## C4 during observation

C4 at the end of the 9000 run: **12/12 resumed**, 16 alive, jobSite `[2,0,0]`, no casualties. This is an additional observation, not part of the official focused C4 count.

## Original worlds

Hashes verified before/after:
- A2 `15,966,366` / `BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7`.
- C4 `16,044,629` / `11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF`.
No `session.lock`; neither was modified.

## Interpretation

1. **IDX4 stalled through 6000?** Yes, exactly frozen from 5000–6000.
2. **IDX4 entered after 6000?** No strict `DENTRO-PARED`; it progressed to the boundary and stopped at dist 63.35.
3. **IDX8 stalled at 6000?** It was still moving into 6000, then became stationary after ~6000.
4. **IDX8 entered after 6000?** No.
5. **Timeout vs defect:** IDX4 is a timeout/delayed-route case; IDX8 is a genuine persistent stall at the outcrop.
6. **Smallest next intervention:** add diagnostic correlation for IDX8’s villager target vs `PLANNING`/waypoint replacement; do not change navigation behavior until that trace proves whether combat targeting suppresses the route.

## Tests

```text
gradlew test: PASS
lint_aldea.py --strict: OK
git diff --check: PASS
```

**This observation is not A2 acceptance.**
