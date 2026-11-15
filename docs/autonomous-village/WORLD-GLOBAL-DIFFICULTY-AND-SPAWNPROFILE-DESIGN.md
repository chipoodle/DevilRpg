# World-Global Difficulty and SpawnProfile Design

Audit/design only. **No gameplay code changed, no difficulty values changed, no entity renamed,
no registry IDs changed, no A2/C4 thresholds changed.**

## 1. Owner-confirmed design

- One shared world spawnpoint for all players.
- Geographic difficulty = distance from a creature's spawn location to that shared world
  spawnpoint.
- Applies to custom `AggressiveZombie`, siege attackers, and the future canonical
  `AggressiveVex`.
- Must not use nearest player's bed/spawn, current position, triggering player, join order, or
  connected-player count.
- New entities receive the temporal difficulty at spawn; existing entities retain their
  spawn-time attributes. Time/threat is shared globally.

## 2. Current implementation — exact reference

`CommonForgeInteractionEventSubscriber.onEntitySpawn(EntityJoinLevelEvent)`:

```java
if (playerCapability.getAnchorPoint() == null && event.getLevel() instanceof ServerLevel serverLevel) {
    BlockPos anchor = serverLevel.getSharedSpawnPos();
    playerCapability.setAnchorPoint(Vec3.atCenterOf(anchor), player);
    RitualCircleGenerator.generate(serverLevel, anchor);
}
```

The per-player `anchorPoint` is initialized once from `ServerLevel.getSharedSpawnPos()`. Every
player who joins gets the same anchor value.

`AggressiveZombieEntity.setPos`:

```java
Player nearestPlayer = level().getNearestPlayer(this, maxDistance);
Vec3 playerSpawn = cap.getAnchorPoint();
if (playerSpawn == null) playerSpawn = cap.getSpawnPoint();   // fallback
spawnDistance = sqrt(distSqr(new BlockPos(playerSpawn)));
spawnThreat = ThreatLevel.current(level());
```

`FrostVexEntity.setPos` follows the same pattern with a 128-block search and the same anchor
fallback.

### Classification

**MATCH** for the normal initialized path: the actual geographic reference is the shared world
spawn anchor stored per player. The nearest-player lookup only selects which copy of the shared
anchor to read; it does not make the value player-specific.

**Residual risk:** if `anchorPoint` is null, the zombie falls back to `getSpawnPoint()` — the
player's individual respawn/bed position. That fallback would be a mismatch. It should not occur
after normal player join, but it is not a proof of shared spawn. A future correction should make
the anchor the only reference and resolve shared spawn on demand instead of falling back to a
player-specific value.

## 3. Spawn paths

### A. Custom AggressiveZombie

`AggressiveZombieSpawnRule` → `ModEntities.AGGRESSIVE_ZOMBIE` → entity `setPos` → anchor read →
`adjustAttributesBasedOnSpawnDistance()` in first `aiStep`.

### B. Siege attackers

A2 harness and siege creation instantiate the same `AggressiveZombieEntity`; the same `setPos` and
scaling path applies. No separate siege scaling class exists.

Important test-only exception: `build/a2n/A2NavHarness.java` explicitly calls
`auxiliary.setAnchorPoint(playerPosition.add(-5000,0,-5000), fakePlayer)`. When that fake player is
nearest, A2 attackers are scaled from that overridden anchor, not the real village/shared spawn.
This is harness instrumentation, not production behavior.

### C. Custom Vex

`FrostVexEntity` (current name) extends vanilla `Vex`. `VexSpawnRule` creates `ModEntities.FROST_VEX`.
`FrostVexEntity.setPos` reads the nearest player's anchor (fallback spawn point), captures
`ThreatLevel.current`, then `adjustAttributesBasedOnSpawnDistance()` uses `VexSpawnProfile`.
Same global inputs as the zombie, in the normal path.

## 4. Temporal difficulty

- `spawnThreat = ThreatLevel.current(level())` at spawn only.
- Existing entities do not re-evaluate as world time advances.
- Threat is world-derived, not per-player session time.
- New spawns receive the higher threat. This matches the owner design; no change recommended.

Formula currently combines distance and threat:

```text
scale = profile.scaleFactor(distance, threat)
      * (1 + threat * ThreatLevel.maxExtraDifficulty())
```

`ThreatLevel.maxExtraDifficulty` is global config, not per creature. This is the main thing to
move into each profile.

## 5. Multiplayer consistency

| Scenario | Current behavior | Design |
|---|---|---|
| One player | anchor = shared spawn | MATCH |
| Two players, one near/one far | both anchors equal shared spawn; nearest lookup irrelevant to value | MATCH |
| Both far at different distances | spawn distance from shared spawn, not players | MATCH |
| Player joins after creature spawned | existing `spawnDistance`/attributes retained | MATCH |
| Nearest player changes while creature alive | no re-scaling; value already captured | MATCH |
| Siege triggered by player A, player B closer | production anchor shared; A2 harness fake anchor exception | MATCH production / harness-specific |

Runtime multiplayer verification: **NOT RUNTIME VERIFIED**. This audit is static.

## 6. Existing SpawnProfile structure

`SpawnScaleProfile` is an immutable record:

```text
minDistance, maxDistance, minHardDistance, maxScaleMultiplier,
baseHealth, baseSpeed, baseDamage, baseXp, maxXpMultiplier
```

Profiles are static singletons:

- `AggressiveZombieSpawnProfile.INSTANCE`
- `VexSpawnProfile.INSTANCE`

Distance configuration lives in the profile. Temporal response lives outside it, in
`ThreatLevel.maxExtraDifficulty()` and the shared formula.

## 7. Proposed per-creature temporal architecture (design only)

Add an immutable `TemporalDifficultyProfile` referenced by `SpawnScaleProfile`:

```text
enabled
baseTemporalMultiplier       (default 1.0)
maxTemporalMultiplier        (default 1.8, matching +80%)
fullThreatTicks              (default 3h, current global)
curve                        (LINEAR default; future)
```

Proposed combination:

```text
temporal = enabled
    ? baseTemporalMultiplier
      + (maxTemporalMultiplier - baseTemporalMultiplier) * clamp(threat,0,1)
    : 1.0

finalScale = distanceScale * temporal
```

Before:

```text
Entity -> SpawnScaleProfile (distance) + ThreatLevel.maxExtraDifficulty() global
```

After:

```text
Entity -> SpawnScaleProfile (distance) -> TemporalDifficultyProfile
       -> global ThreatLevel.current(level()) as shared input
       -> finalScale
```

Defaults must preserve current `AggressiveZombie` values: base 1.0, max 1.8, full 3h, linear.
Vex may later choose its own values without changing the zombie. Species capabilities (health,
damage, speed) remain profile-specific.

This refactor is **design only**; no class or profile was modified.

## 8. AggressiveVex rename inventory

| Current identifier/name | File | Category | Proposed AggressiveVex name | Compatibility risk |
|---|---|---|---|---|
| `FrostVexEntity` | `entity/FrostVexEntity.java` | Java class | `AggressiveVexEntity` | Source-only; all references must update |
| `FrostVexEntity` ctor | same | Java constructor | `AggressiveVexEntity` | Source-only |
| `LaunchSnowballGoal` | same | inner class | unchanged | none |
| `FROST_VEX` | `init/ModEntities.java` | registry holder | `AGGRESSIVE_VEX` | source-only, but registry ID must not change casually |
| `"frost_vex"` | `init/ModEntities.java` | registry ID / saved entity id | keep `frost_vex` initially; optional alias | **HIGH** — existing worlds/NBT use it |
| `FROST_VEX_NAME` | same | Java constant | `AGGRESSIVE_VEX_NAME` | source-only |
| `FROST_VEX_SPAWN_EGG` | `init/ModItems.java`, `ModCreativeTabs` | item holder | `AGGRESSIVE_VEX_SPAWN_EGG` | source-only |
| `frost_vex_spawn_egg` | `ModItems`, lang, model JSON | registry item ID | keep old ID initially | **HIGH** — item NBT/recipes |
| `VexSpawnRule` | `spawner/VexSpawnRule.java` | class | `AggressiveVexSpawnRule` | source-only |
| `VexSpawnProfile` | `spawnprofile/VexSpawnProfile.java` | class | `AggressiveVexSpawnProfile` | source-only |
| `Frost Vex` | `assets/.../lang/en_us.json` | display name | `Aggressive Vex` | low (visual) |
| `frost_vex` lang keys | same | translation keys | keep old keys or duplicate | medium; HUD/tooltips |
| `"FrostVex spawned..."` | `FrostVexEntity.java` | log string | `AggressiveVex spawned` | diagnostic only |
| `frost_vex` model/egg assets | `assets/.../models/item/frost_vex_spawn_egg.json` | resource path | new asset alias | medium; resource pack compatibility |
| Vex references in `LairManager`, renderer, tooltip docs | multiple | consumers | update source refs | source-only |
| `FrostVexEntity` in tests/fixtures/docs | repo-wide | references | update docs/tests separately | low |

Recommended sequence: rename Java types/classes and display strings first while **keeping registry
IDs `frost_vex` and `frost_vex_spawn_egg`** for save compatibility; then optionally introduce
`aggressive_vex` with a data migration/alias in a separate task.

## 9. Save/registry compatibility

- Registry IDs are persisted in saved entities. Renaming `frost_vex` → `aggressive_vex` without a
  migration makes existing Frost Vex entities unreadable/removed.
- Java class renames do not affect saves if registry IDs stay.
- Spawn egg item IDs are also persisted in inventories.
- Translation key changes only affect display; safe with fallbacks.
- Recommended: class/display rename now; registry ID migration later with explicit aliases and
  tests loading a world containing `frost_vex`.

## 10. Recommended implementation sequence

1. Production-only hardening: require the shared anchor and remove the player-spawn fallback for
   difficulty, with tests (separate task).
2. Per-creature `TemporalDifficultyProfile` refactor, preserving zombie defaults; Vex unchanged.
3. Java/display `AggressiveVex` rename while keeping registry IDs.
4. Optional registry migration `frost_vex` → `aggressive_vex` with save compatibility tests.
5. Multiplayer/runtime verification.

## 11. Uncertainties / limitations

- Runtime multiplayer and real attribute values were not measured.
- A2 harness's fake anchor override is test instrumentation and can diverge from production.
- The anchor fallback to player spawn point is a real mismatch risk if initialization fails.
- `SpawnScaleProfile` is immutable; adding a temporal record is the least invasive design.
- No build/test was run because this is read-only documentation; lint/diff-check only.

## 2026-11-14 12:05 — Shared world spawn invariant implemented

- AggressiveZombieEntity.setPos now reads ServerLevel.getSharedSpawnPos() directly when spawnDistance == 0; it no longer looks up the nearest player or falls back to getSpawnPoint().
- FrostVexEntity.setPos uses the same direct shared spawn and policy.
- New pure SharedSpawnReferencePolicy + test (5 assertions) uses only the shared origin; player/personal spawn values are not inputs.
- Distance and temporal formulas, profile values, multipliers, temporal snapshot semantics and all unrelated anchor uses are unchanged.
- A2 harness still contains the fake anchor override -5000, but it is now inert for difficulty because entities use the real shared spawn directly. This changes A2 scaling from the synthetic max-distance anchor to the actual world spawn distance; harness cleanup is a separate task.
- No registry IDs/names, C4, A2 threshold, guards or workers changed.
- A2 remains FAIL; no acceptance run was performed. Multiplayer runtime verification remains pending.
