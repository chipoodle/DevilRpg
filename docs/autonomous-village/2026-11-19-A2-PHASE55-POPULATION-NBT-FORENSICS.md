# A2 Phase55 fixture population — read-only NBT forensics (2026-11-19)

## 1. Result

**Both missing workers exist and are alive in the saved world.** All 12 specialist slots spawned at
03:19:05, including SHEPHERD slot 5 and MASON slot 11. The final NBT shows them alive, health 20,
`DeathTime=0`, `PersistenceRequired=1`, at their configured positions. Both are inside the verifier
AABB. The 03:19:08 `getEntitiesOfClass` snapshot omitted them despite their presence.

- RUN: `a2n-20261119-031255`
- Production: `11ff43cd338d7a6222a146a2ed642a5546fb2910`
- Read-only artifacts; no runtime, no world edits, no production edits.
- Four NONE founders are expected and excluded from the 12-slot table.

## 2. Evidence files

```text
build/a2n/a2n-20261119-031255/srv.log
build/a2n/a2n-20261119-031255/nav-trace.log
build/a2n/a2n-20261119-031255/launcher-status.txt
build/a2n/a2n-20261119-031255/fin.txt
build/a2n/a2n-20261119-031255/game/a2n/entities/r.1.1.mca
build/a2n/a2n-20261119-031255/game/a2n/entities/r.1.0.mca
```

NBT was parsed read-only with `nbtlib`; the region files were not loaded by Minecraft.

## 3. Twelve worker slots

All 12 spawn log lines are at 03:19:05 and say `(bebe: false)`. NBT state is from the saved world at
shutdown (03:19:26).

| Slot | Profession | Spawn pos | UUID | Final NBT pos | Alive/HP/Death | Verifier counted |
|---:|---|---|---|---|---|---|
| 0 | FARMER | 639,71,519 | `d01b322a-660a-4118-a351-f5a0cc67f010` | 639.5,71,519.5 | yes/20/0 | yes |
| 1 | WEAPONSMITH | 682,71,519 | `eedcf5e4-7a19-4024-87b5-0bc14ffc7b2c` | 682.5,71,519.5 | yes/20/0 | yes |
| 2 | CLERIC | 654,71,555 | `bd86ba99-5008-4524-a19b-da4c16871c26` | 654.5,71,555.5 | yes/20/0 | yes |
| 3 | TOOLSMITH | 687,71,538 | `6cd3b699-ab60-4b94-a90a-6289c4d08504` | 687.5,71,538.5 | yes/20/0 | yes |
| 4 | NITWIT | 668,71,555 | `fdb4d214-bbd0-43cd-90ef-367fa4fbbfd9` | 668.5,71,555.5 | yes/20/0 | yes |
| 5 | **SHEPHERD** | **708,71,533** | **`c690ddc5-2463-45db-9774-748b68a8e2eb`** | **708.5,71,533.5** | **yes/20/0** | **NO** |
| 6 | BUTCHER | 667,71,539 | `11458b18-2f2f-44e7-b532-5d1dc2a80930` | 667.5,71,539.5 | yes/20/0 | yes |
| 7 | FARMER | 639,71,563 | `a23dd3ab-ab48-4124-9565-ae19f89adc9d` | 639.5,71,563.5 | yes/20/0 | yes |
| 8 | FISHERMAN | 681,71,563 | `be3af596-6113-43b3-ac5d-30f330797014` | 681.5,71,563.5 | yes/20/0 | yes |
| 9 | FARMER | 627,71,563 | `17f1f946-10ec-4ea3-904c-ad9c2512026f` | 627.5,71,563.5 | yes/20/0 | yes |
| 10 | FLETCHER | 611,71,514 | `4a218c77-9ea8-4d41-8a7c-9b0866606511` | 611.5,71,514.5 | yes/20/0 | yes |
| 11 | **MASON** | **694,71,511** | **`c6ba9a5c-6060-44ff-b4b5-c5776fad28fb`** | **694.5,71,511.5** | **yes/20/0** | **NO** |

Four expected founders (profession NONE, health20/death0, persistent):
`865330cf…`, `7b160d6f…`, `2741799d…`, `808bc3f9…` at `615/616,71,554/555`.

## 4. Findings for slots 5 and 11

### SHEPHERD slot 5

```text
03:19:05 [Village] aldeano shepherd en BlockPos{x=708, y=71, z=533} (bebe: false)
NBT: c690ddc5-2463-45db-9774-748b68a8e2eb
     minecraft:shepherd 708.5,71.0,533.5
     Health=20, DeathTime=0, PersistenceRequired=1, Age=0
```

### MASON slot 11

```text
03:19:05 [Village] aldeano mason en BlockPos{x=694, y=71, z=511} (bebe: false)
NBT: c6ba9a5c-6060-44ff-b4b5-c5776fad28fb
     minecraft:mason 694.5,71.0,511.5
     Health=20, DeathTime=0, PersistenceRequired=1, Age=0
```

- Spawn evidence: both log lines appear after `addFreshEntity` succeeded (the log is emitted only after
  the insertion check).
- Insertion: both persisted into the region entities and have stable UUIDs.
- Death/asphyxia/collision: **not supported**; both have health20 and deathTime0 in the final save.
- Profession mutation: **not supported**; NBT still shows shepherd/mason.
- Outside verifier AABB: **false**. The verifier uses `AABB(center).inflate(FENCE_RADIUS+20)` =
  inflate82 around `661,71,533` (x579..743, z451..615). Shepherd distance47, mason distance39.66; both
  inside. The initial `inflate(100)` check also contains them.

## 5. Demonstrated vs unknown

**Demonstrated**

- All 12 worker entities were created and inserted.
- SHEPHERD/MASON survived to world shutdown with their configured professions.
- They were omitted by the 03:19:08 `getEntitiesOfClass` population snapshot.
- Neither is outside the AABB.

**Unknown / insufficient evidence**

Why `getEntitiesOfClass` returned only 14 villagers at 03:19:08 although all 16 existed in the save.
There is no per-tick entity-section/loaded-chunk snapshot. Possible causes are query-time entity-section
visibility/ticking or a deferred entity registration, but the artifacts cannot distinguish them.

## 6. No world mutation

NBT was read directly from the `.mca` region files. `level.dat` and worlds were not loaded/saved by
Minecraft during this forensic phase. A2/C4 protected hashes remain untouched.

## 7. Recommendation

Do not treat the fixture population failure as a spawn/profession defect. Add read-only diagnostics to the
population query: per-UUID result of `getEntitiesOfClass`, chunk/entity-section loaded state, and
`level.getEntity(uuid)` presence. Then decide whether the query should refresh/force entity sections or
whether the population gate must account for deferred entity visibility. No change to spawn geometry or
profession assignment is supported by this evidence.
