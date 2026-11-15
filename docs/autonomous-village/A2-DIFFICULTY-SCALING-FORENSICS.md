# A2 Difficulty Scaling Forensics

Status: audit only. No combat/attribute behavior changed. A2 remains FAIL.

## Facts demonstrated by source

### Distance scaling

`AggressiveZombieEntity.setPos()` computes `spawnDistance` only when it is still `0`:

```java
spawnDistance = sqrt(blockPosition().distSqr(new BlockPos(playerSpawn)));
spawnThreat = ThreatLevel.current(level());
```

The reference is the **nearest player's anchor/spawn point**, not the village centre and not the
current player position. It is computed once at spawn, not per tick.

`adjustAttributesBasedOnSpawnDistance()` then applies:

```text
scaleFactor = profile.scaleFactor(spawnDistance, spawnThreat)
            * (1 + spawnThreat * ThreatLevel.maxExtraDifficulty())

MAX_HEALTH   = 10 * scaleFactor
ATTACK_DAMAGE= 0.7 * scaleFactor
MOVEMENT_SPEED = clamp(baseSpeed * min(scaleFactor, cap) - firstVillageAdjustment)
```

`SpawnScaleProfile`: minDistance 67, maxDistance 3000, maxScaleMultiplier 3.0 (+300%),
baseHealth 10, baseSpeed 0.23, baseDamage 0.7. `ThreatLevel` default extra difficulty 0.8,
full at 3 played hours.

### Temporal scaling

- `spawnThreat` is captured only at spawn.
- Existing entities are **not** updated as world time advances.
- There is no periodic re-evaluation or accumulation.
- A zombie that stays alive for minutes does not get stronger unless it respawns.
- New zombies spawned later use the higher threat.

### Siege attackers

`AggressiveZombieEntity` is the siege attacker class, so it receives the same spawn-time scaling
path. The A2 harness spawns at radius 95 around centre `661,71,533`; if the player anchor is the
same centre, `spawnDistance ≈ 95`.

For d=95, threat≈0:

```text
normalize = (95 - 67) / (3000 - 67) ≈ 0.00954
scaleFactor = 1 + 0.00954*3 ≈ 1.0286
MAX_HEALTH ≈ 10.29
ATTACK_DAMAGE ≈ 0.72
MOVEMENT_SPEED ≈ 0.23 (clamped/anchor)
```

This is almost the base value. The +300% far-distance progression is unreachable in this A2
fixture because the spawn ring is only ~95 blocks from centre.

### Equipment interaction

`equiparAsaltanteDeAsedio()` runs in `aiStep()` after `adjustAttributesBasedOnSpawnDistance()`.

- It sets `MAINHAND/HEAD/CHEST/LEGS/FEET` items only.
- It does **not** call `setBaseValue` on health/damage/speed and does **not** remove attribute
  modifiers.
- Iron armor adds effective armor/protection independently of the distance scale.
- Therefore it does not overwrite distance/time scaling. It does, however, give all siege
  attackers nearly identical defense, compressing differences that would otherwise come from
  health/armor progression.

## Comparative matrix (calculated from source, threat 0)

| Scenario | Distance | Scale | Max health | Attack damage | Speed | Armor source |
|---|---:|---:|---:|---:|---:|---|
| A2 near/ring | 95 | 1.029 | 10.29 | 0.72 | ~0.23 | iron set (siege) |
| Intermediate | 500 | 1.442 | 14.42 | 1.01 | ~0.23–0.24 | iron set |
| Far | 1500 | 2.465 | 24.65 | 1.73 | ~0.29 | iron set |
| Max | 3000 | 4.0 | 40.0 | 2.8 | cap | iron set |

At threat 1, multiply the scale by 1.8 before clamping speed (e.g. max health up to 72).

These are formula-derived, not runtime-measured. No attribute values were changed to obtain them.

## Death cross-check

Runs `093155`, `095200`, `101748`:

- deaths are `minecraft:mob_attack` (one arrow contributor);
- attackers at A2 spawn receive only ~1.03× scale, so ~10.3 health and ~0.72 damage;
- iron armor reduces observed guard melee from ~3.9 to ~1.6, giving roughly 6–7 hits;
- multiple guards and sustained exposure still kill them.

Conclusion: the deaths are coherent with a nearly-unscaled A2 spawn plus numerical guard
superiority and finite health. They are not evidence that the equipment overwrote scaling.

## Answers

1. **Does distance scaling work?** Yes, mechanically. It is applied at spawn from player spawn
   distance.
2. **Does time scaling work?** Only at spawn through `ThreatLevel`; existing entities do not grow.
3. **Applied to siege attackers?** Yes, same class, at spawn.
4. **Does iron equipment overwrite modifiers?** No. It sets items only; attribute scaling survives.
5. **Are far attackers stronger?** In health/damage, yes (up to 4× + threat). In A2's 95-block
   ring, effectively no — scale ≈1.03.
6. **Are deaths coherent?** Yes with near-base stats and focused guard fire.
7. **Justified correction?** None yet from this audit alone. A design decision is needed: either
   siege attackers should use a village/ring-based scaling distance, or A2 expectations must
   accept near-base attackers. No change was made.

## Unmeasured / limitations

- No runtime read of `Attributes` values was captured in this audit.
- The exact player anchor in the A2 harness was not independently instrumented; formula assumes it
  is the village centre, consistent with the fixture.
- Armor/protection effective values are inferred from observed damage, not read from attributes.
- Villager flee speed 1.35→0.945 was not physically measured in this audit.

## Next action

If the design intends siege attackers to scale with the **village/ring distance**, correct the
reference and document it; otherwise proceed with combat survival on near-base stats. Do not alter
C4 or the A2 threshold.


## Owner clarification and target architecture — 2026-11-14

The project owner confirmed that the intended existing difficulty mechanic is **world-global and multiplayer-consistent**:

- Geographic difficulty is measured from the **single shared world spawnpoint** (the common world spawn), not from an individual player's bed/spawn, nearest-player anchor, current position, or the village center as an independent origin.
- As a player/entity spawn location gets farther from that common world spawnpoint, both custom-spawned zombies and siege attackers become stronger according to their creature profile.
- Temporal difficulty advances with world play time. The current intended behavior is spawn-time evaluation: newly spawned creatures receive the current threat; already-existing entities retain their spawn-time attributes.
- Multiplayer must not make difficulty depend on which player is nearest, which player triggered a spawn, or the presence/absence/location of another player. The common world spawn and world threat progression are shared references.

**Important discrepancy to resolve:** the source audit above records `nearest player's anchor/spawn point` as the current implementation reference. That finding must not be silently reinterpreted as the common world spawnpoint. It is not yet proven that the implementation reads `ServerLevel.getSharedSpawnPos()` (or the correct equivalent for this codebase). A focused read-only source trace must identify the exact API/value and test multiplayer independence before any code change.

### Future architecture: per-creature SpawnProfile

The owner also established a future refactor direction: move the time-based difficulty progression configuration into each creature's `SpawnProfile`, rather than keeping temporal progression centralized and implicitly shared. The profile should own creature-specific parameters/policies for temporal progression (for example, curve/rate, threshold or maximum), while the shared world time/threat source remains global and deterministic. Distance scaling should likewise continue to use the common world spawn reference, with per-creature profile parameters where appropriate.

Current known custom creature families:

- `AggressiveZombie` — existing custom zombie; includes normal custom spawns and siege attackers.
- `AggressiveVex` — intended canonical name for the custom Vex, replacing the current generic/custom Vex naming consistently.

The future rename is not limited to Java class names. Audit and plan registry identifiers/resource locations, serialized entity IDs and save compatibility, configuration keys, translation keys, log/diagnostic strings, spawn profile identifiers, tests, documentation, and every source reference. Preserve old save compatibility where registry identity changes would otherwise break existing worlds; do not rename registry IDs blindly.

### Scope and execution order

1. Read-only verify the exact shared world spawnpoint API/value currently used by both custom-spawned `AggressiveZombie` and siege attackers; verify no nearest-player or player-specific anchor influences it in multiplayer.
2. Verify the same global time/threat source is used consistently and that existing entities retain their spawn-time attributes.
3. Document the existing `SpawnProfile` structure and design a backward-compatible per-creature temporal progression contract; do not implement it in the verification task.
4. Inventory all Vex names/identifiers and prepare a compatibility-aware `AggressiveVex` rename plan; do not rename in the verification task.
5. Only after evidence and design review, implement the profile refactor and rename as separately testable changes.

No gameplay behavior, multipliers, acceptance thresholds, equipment, or entity attributes were changed by this clarification. A2 remains FAIL; C4 remains unchanged. The calculated matrix above is still formula-derived, not runtime-measured.

## 2026-11-14 12:05 — Shared world spawn invariant implemented

- AggressiveZombieEntity.setPos now reads ServerLevel.getSharedSpawnPos() directly when spawnDistance == 0; it no longer looks up the nearest player or falls back to getSpawnPoint().
- FrostVexEntity.setPos uses the same direct shared spawn and policy.
- New pure SharedSpawnReferencePolicy + test (5 assertions) uses only the shared origin; player/personal spawn values are not inputs.
- Distance and temporal formulas, profile values, multipliers, temporal snapshot semantics and all unrelated anchor uses are unchanged.
- A2 harness still contains the fake anchor override -5000, but it is now inert for difficulty because entities use the real shared spawn directly. This changes A2 scaling from the synthetic max-distance anchor to the actual world spawn distance; harness cleanup is a separate task.
- No registry IDs/names, C4, A2 threshold, guards or workers changed.
- A2 remains FAIL; no acceptance run was performed. Multiplayer runtime verification remains pending.

## 2026-11-14 12:30 — A2 acceptance with shared-world-spawn scaling: FAIL, C4 PASS

- Run 2n-20261114-121647, completed normally with fin.txt (full 12000 ticks).
- spawneados=8/8, allosSpawn=0, ivos=3, muertos=5, desaparecidos=0, ntraronPerimetroEver=5, dentroParedAhora=0.
- Survivors: idx3 617,86,600 health 17.56; idx4 588,79,507 health 16.84; idx8 748,63,533 health 18.24. idx7 entered at t=9783, was inside at t=10476, died t=11078; five attackers died.
- A2 FAIL: criterion 2 (5/8 < 6), criterion 3 (0 inside), criterion 4 (no external survivor enclosure progress; idx3/idx4 static, idx8 far).
- C4 PASS: 16 survivors, 12/12 workers alive/resumed, 0 casualties, jobSite [8,0,0], 3 removed for recovery, 0 instrument failures.
- Measurable difference vs 093155: survivor health rose ~10 -> ~17-18 because the real shared spawn distance now scales difficulty; the A2 harness's -5000 anchor override is inert.
- No A2/C4 threshold or code changed during this run. Shared-spawn invariant held for both zombie and Vex.
