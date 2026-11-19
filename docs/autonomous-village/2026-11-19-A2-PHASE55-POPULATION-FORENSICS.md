# A2 Phase55 fixture population — source forensics (2026-11-19)

**Run:** `a2n-20261119-031255`  
**Runtime result:** BLOCKED before attacker spawn; no retry authorized.  
**Production under review:** `11ff43cd338d7a6222a146a2ed642a5546fb2910`  
**Scope:** read-only source review plus the committed Harness report. No source edits, tests, world edits or runtime execution in this investigation.

## 1. Correction: the four `NONE` professions are expected

The report's phrase “four villagers with null profession” must not be interpreted as four failed worker assignments.

The production generator intentionally creates the four founding guards with `profession == null`:

- `VillageGenerator.spawnGuardiasFundadores(...)` calls `spawnVillagerAt(..., null, false, ...)` once for each of four founder slots.
- The population gate checks `VillagerGuardGoal.esGuardia(villager)` **before** checking profession. Founders are counted as guards, not professionals.
- Thus four `NONE` founders are expected by design. They are not evidence of nondeterministic profession reset.

The report's actual discrepancy is **14 total villagers = 4 founders + 10 counted professionals**, while the generator defines 12 fixed worker posts.

## 2. The two missing roster entries

The current `VillageGenerator.VILLAGER_SPECIALTIES` order is:

| Slot | Profession | Spawn offset from center |
|---:|---|---|
| 0 | FARMER | (-22, 0, -14) |
| 1 | WEAPONSMITH | (21, 0, -14) |
| 2 | CLERIC | (-7, 0, 22) |
| 3 | TOOLSMITH | (26, 0, 5) |
| 4 | NITWIT | (7, 0, 22) |
| 5 | SHEPHERD | (47, 0, 0) |
| 6 | BUTCHER | (6, 0, 6) |
| 7 | FARMER | (-22, 0, 30) |
| 8 | FISHERMAN | (20, 0, 30) |
| 9 | FARMER | (-34, 0, 30) |
| 10 | FLETCHER | (-50, 0, -19) |
| 11 | MASON | (33, 0, -22) |

The reported roster contains no SHEPHERD and no MASON. The missing roles therefore map to **slot 5** (corral-side spawn) and **slot 11** (miner-hut-side spawn). This maps the missing roles to their configured slots; it does not yet prove whether either villager failed to spawn, died, left the query AABB, or had its profession changed later.

## 3. Source path and observability gap

- `VillageGenerator.generate(...)` calls `spawnVillagers(level, center)`.
- `spawnVillagers` iterates all 12 `VILLAGER_SPOTS` and calls `spawnOneVillager` for every slot.
- `spawnOneVillager` currently returns `void` and does not report the result to its caller.
- `spawnVillagerAt` can return `null` if `EntityType.VILLAGER.create(...)` returns null or `level.addFreshEntity(villager)` returns false. Those failure branches currently have no slot-specific failure log.
- `spawnVillager` derives a ground position and calls `huecoLibre`. That helper searches upward for up to eight two-block-clear positions, but if none is found it returns the original position. The caller does not expose whether that fallback happened or record the final collision/support state.
- After generation, `VillageManager.ejecutarMantenimientoParaArnes` runs real village maintenance. The current report has only the final population snapshot, not a before/after per-UUID/per-slot diff.

The present evidence cannot distinguish: (a) failed entity creation/insertion, (b) successful spawn followed by death/removal, (c) worker-profession mutation during maintenance/vanilla brain processing, or (d) a counting/query-boundary issue.

## 4. Required read-only Harness forensics — no runtime

Inspect the existing artifacts for `a2n-20261119-031255` only. Do not launch Minecraft, regenerate the village, edit production, alter the world, or consume another runtime authorization.

1. Extract all available `[Village] aldeano` spawn lines, warnings, entity death/removal/suffocation lines, profession-reset/brain/job-site lines, and the complete generation/maintenance interval from `srv.log`.
2. Determine whether SHEPHERD and MASON ever appear as successful spawn events, and whether either UUID later dies or disappears. Report exact timestamps/ticks and UUIDs; if logging level suppressed the evidence, say so.
3. Inspect the existing disposable world's entity NBT read-only if it is still available and safe. Map all villagers in the fixture radius by UUID, position, profession, XP, age, health/alive state and persistent guard/founder tags. Do not load-and-save or otherwise mutate the world.
4. Compare all villager entities before and after the maintenance call only if snapshots already exist in logs/artifacts. Do not infer a before-state from the final roster.
5. Inspect launcher status and `fin.txt` only for termination timing and whether the fixture's final snapshot was delayed after generation. Do not infer deaths without evidence.

Return a per-slot table for all 12 worker slots: attempted/successful spawn evidence, UUID, final position, profession at each observed point, and terminal reason. Separately list the four expected founders. Mark missing evidence UNKNOWN.

## 5. Decision gate

Do not change navigation, A*, A2 acceptance, fixture population thresholds, guard/founder semantics, profession XP, or world-generation geometry based on this report alone.

After read-only evidence, ChatGPT will decide whether a narrowly scoped, property-gated spawn diagnostic is needed or whether the evidence already identifies a production defect. Any new runtime requires a **new explicit user authorization**; the previous single Run1 authorization is consumed.

A2 remains OPEN. Phase55 runtime behavior remains unmeasured.
