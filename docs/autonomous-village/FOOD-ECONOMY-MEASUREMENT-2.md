# Food Economy — Measurement 2

## Run

```text
ID: stabilization-block1-scan-20261030-86
World: build/stabilization-block1-scan-20261030-86
Scenario: food-day
Ticks: 600 -> 24,600
```

## Identities (distinct, verified)

```text
worker = e774aa05-1520-4670-a19a-e2b0eb603d57
guard  = 4a5d61a8-c973-472f-b464-61acda3a1902
farmer = 777fee9e-75a1-455d-a421-0c39c5eb60f8
cook   = a445a483-bc30-40eb-b1a1-0ad7dfc92fb6
```

The scenario aborts if worker and guard are the same; this run passed that gate.

## Whole-item over-withdrawal — exact reproduction

```text
[C1-OVERDRAW] requested=10 returned=10 breadLeft=0 removedPoints=12
```

- 3 bread were available (4 points each).
- 10 points were requested.
- All 3 bread were removed = 12 points.
- The method returned 10 points.
- **2 food points were lost; no refund or carry-over occurred.**

This is runtime evidence through `VillagePantry.sacarComida`, not a mock.

## Ordinary worker

```text
meals: 13 timestamp advances
ticks: 1379 ... 24379
```

The worker ate approximately every 1,200–1,500 ticks while village life ran.
The long gap 11,579 → 20,779 corresponds to attack/enemy conditions.

## Guard

```text
guard meals: 9
last recorded: tick 11,579
```

- The guard consumed independently through the same village ration path.
- It received no tracked meals after the attack period began.
- `tickVillageLife` is skipped under attack/enemies-inside, and the tavern refuses
  under attack. This is consistent with the source-level guard gap.
- No separate night-only meal was observed; guards are on the ordinary cadence.

## Farmer

Aggregate log-derived deliveries:

```text
63 farm delivery items
```

Examples include `guardo 8`, `Guardo 16`, `Trajo 7`. Exact crop mix was not
itemized by existing logs.

## Cook

```text
13 cooked pieces
cooking batches observed in logs
destination: pantry
```

Ingredient-level inputs were not itemized; production logs report pieces and log
consumption, not a full recipe ledger.

## Other production

```text
Fishing events: 10
Animal sacrifices: logged (chicken/pig)
```

## Pantry snapshot

```text
start (after seeding): bread=256 points=256
end:                   bread=0   points=11
end items: bread=0 wheat=1 carrot=5 potato=0 beet=0 cooked=0
```

Production occurred (63 farm items, 13 cooked pieces, 10 fish events) but the
pantry still fell to 11 points. **Daily balance for this population is negative.**

## Answers

1. **Ordinary worker:** 13 meal events in 24,000 ticks; ≥13 points requested plus
   whole-item overdraw.
2. **Guard:** 9 meal events; stopped when the attack period began.
3. **Guard night/raid:** no raid feeding path observed; source skips village life
   under attack.
4. **Farmer:** 63 logged delivery items; exact crop points unknown.
5. **Cook:** 13 cooked pieces; exact ingredient ledger incomplete.
6. **Over-withdrawal:** **2 points lost** in the 10-point/3-bread fixture.
7. **Balance:** negative (256 → 11 points).

## Findings status

| Finding | Status |
|---|---|
| Worker/guard distinct | Runtime verified |
| 60-second ration cadence | Runtime verified |
| Whole-item overdraw | **Runtime verified: 2 points** |
| Guard lacks raid feeding | Source + runtime gap |
| Farm/cook production totals | Partial runtime (aggregate logs) |
| Per-item ledger | Partial (start/end snapshots only) |
| Positive daily balance | **Runtime verified negative** |

## Limitations

- No per-transaction item ledger; only start/end snapshots and aggregate logs.
- Farmer crop mix and cook ingredients are not itemized.
- Attack interrupted feeding and guard observations.
- No save/reload.

## Next correction (not implemented)

Daily meal window, exact withdrawal/refund, and a guard raid feeding rule. Do not
buff production before those are fixed.
