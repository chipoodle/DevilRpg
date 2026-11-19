# Manual siege observation — breach selection and custom gate (2026-11-19)

## Status

**OBSERVED BY PLAYER — SOURCE DIAGNOSIS RECORDED — IMPLEMENTATION PENDING.**

- Branch: `feature/autonomous-village`
- Production baseline at observation: `b41e9687a4be74d24a671340ac41b7e3f1817045`
- Current branch tip when documented: `c4c1a3a2af197150406a334c4c03fe2b77802134`
- This is a manual gameplay observation, not an A2 Harness run or acceptance result.
- No production code changed for this observation.
- The previously required P0 static validation remains the next authorized task. No runtime is authorized by this report.

## 1. Player observation

During a manually played siege:

- Aggressive zombies spawned outside the village.
- Two zombies broke the wall and entered through the breach.
- Several others appeared to ignore the visible breach and dug downward beneath themselves.
- Some exited their holes and later entered; others remained in the holes without making progress.
- A yellow-named entity identified by the player as a leftover test zombie, approximately `IDX8`, remained at the custom gate without progressing.

Exact entity UUIDs, positions, target coordinates, logs and world save were not supplied. The observations are qualitative and must not be misrepresented as a reproducible Harness result.

## 2. Confirmed source explanation — downward digging

In `AggressiveZombieEntity.TraverseGoal`:

- `objetivoDeLaMarcha()` chooses the current combat target's block position when a live target exists; otherwise it uses the village center.
- After a movement-stall window, `tick()` compares the chosen target Y with the zombie's current Y.
- If the target is more than one block below, it calls `cavarHaciaAbajo(hacia)` before trying the forward breach/bridge and stair alternatives.
- If those alternatives fail, it can call `cavarHaciaAbajo` again as a fallback.

The excavation helper removes the block directly beneath the zombie, provided its existing range, protection and budget checks pass. Thus the observed downward holes are not random indecision: the current traversal policy explicitly prefers digging underfoot whenever its selected combat/village target is sufficiently lower. Different attackers can choose different combat targets/heights and therefore make different decisions even while standing before the same visible breach.

This is a strong source-level explanation, but the exact per-zombie trigger in the reported match cannot be proven without the target positions and runtime trace.

### Required behavioral correction

A visible, reachable opening in the perimeter must be preferred over creating a new hole. A siege attacker should:

1. Search for a traversable opening in the generated wall ring and route through it when reachable.
2. If no suitable opening exists, coordinate selection of one valid wall breach.
3. Only use downward excavation when the selected route contains a verified lower transition that actually requires it—not merely because a combat target or village center has a lower Y.
4. After a terrain change, re-evaluate the route and opening from the updated world.
5. Avoid leaving attackers idle in self-created holes; retain bounded recovery and explicit failure reasons.

Do not globally disable digging: legitimate terrain descent still needs a validated DROP/route transition.

## 3. Confirmed source explanation — custom DoubleGateBlock

File: `src/main/java/com/chipoodle/devilrpg/block/DoubleGateBlock.java`.

- `DoubleGateBlock` extends `HorizontalDirectionalBlock` and implements `EntityBlock`.
- It is a coordinated 3-wide × 3-high gate made of nine block cells.
- Only the hinge cell owns the `PortonDobleBlockEntity`; the other eight cells do not.
- Its source documentation explicitly says the gate is not a vanilla `DoorBlock`, and that zombies were intentionally not allowed to break it; the original design expected them to breach the wall instead.
- `playerWillDestroy` removes the other gate cells when a player breaks one cell. That player-specific cascade is not the same as the mob's `Level.destroyBlock` path.

In `AggressiveZombieEntity`:

- `puedeRomperNodoDeNavegacion` rejects every block with a block entity.
- `breakBlockAt` also immediately rejects a target with a block entity, before it evaluates the wall.
- Consequently the hinge cell is categorically unbreakable through the current mob-break path.
- The other eight gate cells can pass the block-entity check, but the mob's normal `destroyBlock` call destroys only its selected cell; it does not invoke the custom player's nine-cell teardown.

Therefore the player's suspicion is correct: the custom gate is not currently implemented as a coherent siege-destroyable structure. Merely removing the block-entity rejection would be unsafe and insufficient: it could leave a partial gate, orphan hinge entity, or malformed structure.

### Required behavioral correction

Treat the nine-cell gate as one atomic siege structure:

1. Identify and validate the complete 3×3 gate assembly, its orientation and expected hinge.
2. Allow the siege-only destruction path to handle the hinge block entity deliberately; do not weaken the global rule that arbitrary block entities are protected.
3. Claim the gate as one breach target so multiple attackers do not independently claim different cells of the same gate.
4. Remove all nine cells as one server-side operation with no unintended drops and invalidate navigation after the world changes.
5. Emit the breach event only after the complete gate is confirmed absent.
6. Keep the existing village protection, wall-ring geometry, siege budget/cadence and ownership rules intact.

The gate is a valid breach target only while it is one of the generated perimeter gate assemblies. Other block entities remain protected.

## 4. Confirmed restoration path and a possible repair blocker

The village already has an explicit perimeter restoration mechanism:

- `VillageManager` invokes `VillageGenerator.asegurarMuro` every `VILLAGE_POLL_TICKS` (200 ticks / 10 seconds) for generated villages.
- `asegurarMuro` checks the complete perimeter and all expected custom gate cells. A missing gate cell sets `portonesMal`, making the wall incomplete.
- It calls `rehacerMuro` to reconstruct the ring and its four gates when the perimeter is incomplete.
- Before rebuilding, however, `asegurarMuro` returns if **any** `AggressiveZombieEntity` remains inside an AABB around the village with `FENCE_RADIUS + 4` inflation. This is broader than checking whether the actual siege is still active or whether an entity belongs to the current wave.

This means the yellow-named leftover `IDX8` entity at the gate may be more than a visual nuisance: if it remains alive within that radius, it can prevent the village's existing wall/gate restoration even after the actual siege has ended. This is source-supported as a possible blocker; its presence in the reported save and whether it is counted by this exact query still need runtime confirmation.

### Required restoration behavior

- Keep the perimeter open while a real siege is active and attackers are still operating at the wall.
- Once the siege is resolved and the wave is gone, restore the complete wall and all four gates using the existing deterministic `rehacerMuro` path.
- Do not let an unrelated/stale diagnostic zombie permanently suppress repair. Gate repair eligibility should use the active siege/wave lifecycle and an explicit safe-to-repair condition, not merely the presence of any aggressive zombie in a broad radius.
- Do not repair while a legitimate attacker is still actively breaching or crossing the perimeter.
- Ensure the repair remains idempotent and does not run repeatedly while a siege is active.

The existing wall restoration is performed by the village system (`VillageGenerator` / `VillageManager`), not by an individual villager manually rebuilding each gate cell. The desired player-visible result is nevertheless the same: after the siege is safely over, the village restores its gate and wall.

## 5. Scope and order of implementation

1. Complete the already-pending P0 static validation from `NEXT-SESSION.md` first. Do not overwrite or skip that checkpoint.
2. Implement and test the gate as an atomic siege breach, with unit/policy tests for all nine cells, hinge handling, malformed/incomplete assemblies, duplicate claims and fail-closed behavior.
3. Implement breach/opening selection so all attackers prefer a reachable existing breach and coordinate a new one only when needed.
4. Correct `TraverseGoal` so target-Y difference alone cannot trigger underfoot excavation ahead of a viable wall opening. Preserve legitimate verified descents.
5. Correct wall restoration gating to distinguish a resolved siege from stale/out-of-wave diagnostic entities.
6. Run compile, unit/policy tests, strict lint and diff-check. Harness must not modify production.
7. Only after static PASS, request fresh explicit runtime authorization for a controlled siege validation. The player's manual match is evidence, not authorization for ChatGPT/Harness to launch another runtime.

## 6. Acceptance criteria for the later runtime

- If a valid breach already exists, every attacker with a reachable route uses it; no new hole is dug merely because its target is lower.
- If no opening exists, attackers coordinate a valid breach rather than all attacking unrelated cells.
- A closed custom 3×3 gate can be destroyed as a whole by an authorized siege attacker; the hinge block entity does not make the gate immortal.
- No partial gate, orphan block entity, unintended drops or duplicate breach event remain.
- No non-wall protected block or unrelated block entity becomes breakable.
- Attackers do not remain indefinitely in self-created holes; each recovery ends in measurable progress or a bounded explicit failure.
- The perimeter and custom gates are restored after the real siege has resolved and no active wave member is still operating at the perimeter.
- A stale/out-of-wave `IDX8` entity cannot indefinitely prevent repair.
- Existing A2/C4 criteria, budgets, fixture geometry and unrelated village systems remain unchanged.
