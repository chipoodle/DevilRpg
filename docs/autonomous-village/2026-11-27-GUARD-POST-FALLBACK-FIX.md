# Guard post fallback correction — 27-nov-2026

## Evidence

Harness Run 008: `20261127-008`, tested production SHA `cd532f1d630f26c81e6acbcde91112390e157eb1`.

- `puestoLibre()` selected branch D: final fallback `return villager.blockPosition()`.
- Ximeno (UUID `5b42b9cf-8e30-4c9f-ad16-2ad31491a2e5`) was at `637,62,577`; its feet and support were `minecraft:water`, head was air.
- `sePuedeEstar=false`, `ES_AIRE_DE_LA_CALLE=false`.
- No dry, supported, reachable cardinal alternative existed.
- The fallback destination was the guard's current position; distance was 0.633, below `REACH=3.0`.
- Goal remained installed/running, but `WALK_TARGET` was empty and `stuckTicks=0`; therefore terrain recovery had no valid direction and did not trigger.
- Original world preserved: 48 files, `DIFF=0`; no runtime recovery was executed.

## Production correction

Commits:
- `65bb7be61e4c88b2df548afdf412f0f475820f18` — Reject unsafe guard posts and remove current-position fallback.
- `73016ae093de18d1d9744d9bd8164ef35189ec2d` — Handle unavailable guard corral post safely.

Changes in `VillagerGuardGoal.java`:

1. `sePuedeEstar()` now rejects non-empty fluid states at feet and head, in addition to its existing collision and support checks. A collision-empty water cell can no longer qualify as a standing post.
2. `puestoLibre()` is nullable. If no safe, supported, reachable post exists, it returns `null` rather than pretending the guard's current position is a valid destination.
3. `puntoDeGuardia()` and `puestoDelCorral()` explicitly declare nullable results.
4. When the corral has no valid post, the corral turn is skipped and the normal patrol selection continues; the null value is not passed to the failed-post lookup.
5. Existing `REACH`, route navigation, support collision requirement, protected-area checks, and terrain-recovery thresholds are unchanged.

## Expected behavior

- If a dry, supported, reachable post exists, it is selected through the existing candidate search.
- If none exists, no fictitious current-position destination is assigned. The guard goal cannot start with a null destination and can retry selection on a later scheduler evaluation.
- If a corral post is unavailable, the guard skips that corral turn and tries the ordinary patrol route.
- This change does not itself guarantee that Ximeno escapes the water. It removes the false-arrival dead end; `GuardTerrainRecovery` runtime behavior remains to be validated.

## Validation status

ChatGPT reviewed the GitHub diffs and null-handling call sites. ChatGPT did not run Gradle, tests, lint, Minecraft, or local commands.

- Compile: NOT RUN
- Tests: NOT RUN
- Strict lint: NOT RUN
- Runtime: NOT RUN
- Harness must validate exact HEAD `73016ae093de18d1d9744d9bd8164ef35189ec2d`.

## Harness next

1. Preserve all local work and diagnostic worlds; safe fast-forward only, stop on divergence.
2. Verify exact tested SHA.
3. Run compileJava, compileTestJava, test/policyTest, strict lint, and `git diff --check`.
4. Run the automated client scenario on a NEW copy of the world. Do not modify the original or reuse/overwrite Run 008.
5. Verify:
   - no guard destination equals the current position when that cell is wet/unsupported;
   - no wet feet/head candidate is accepted by `sePuedeEstar`;
   - no NPE occurs when `puestoLibre()` returns null, especially in the corral branch;
   - the guard either selects a valid alternative or remains without a fabricated destination;
   - when a recovery direction can be formed from a valid strategic destination, `[GuardTerrain]` events, block placements, WALK_TARGET, coordinates, and escape are measured;
   - normal patrol, corral, arboleda, training, equipment, march, and combat behavior do not regress.
6. Preserve original-world hashes and report exact paths, coordinates, logs, commands, exit codes, and tested SHA.

Harness must not modify production source. Do not reduce REACH, weaken support/protection, teleport the guard, or force recovery.
