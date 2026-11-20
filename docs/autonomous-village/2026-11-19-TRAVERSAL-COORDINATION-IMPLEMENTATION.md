# Traversal coordination — implementation checkpoint (2026-11-19)

## 1. Status

**IMPLEMENTED — STATIC VALIDATION PENDING.** This change implements the first traversal-coordination stage only.
No Minecraft process, runtime scenario, world, or test command was executed by ChatGPT.

- Repository: `chipoodle/DevilRpg`
- Branch: `feature/autonomous-village`
- Base HEAD: `e4692eeb0df6e9f1e83e25a0c6991004360771c8`
- Implementation HEAD: `7aaea19364d958ee0e16f9ea89b3b1be9168e878`
- `PRODUCTION_MODIFIED_BY_HARNESS=false`
- `RUNTIME_EXECUTED=false`
- `MINECRAFT_LAUNCHED_BY_CHATGPT=false`
- `WORLDS_MODIFIED=false`
- `TESTS_EXECUTED_BY_CHATGPT=false`
- A2 remains OPEN; this work does not validate A2 or C4.

## 2. Implemented behavior

### 2.1 NavExecutor owns the terrain actuator

`NavExecutor` now exposes read-only accessors derived from its existing route cursor:

- `activeNode()`
- `activeTransition()`
- `activeActionTarget()`

The active node is exposed only while the executor is actually `TRAVERSING` or `RECOVERING`. Action targets are derived, not stored as duplicate route state:

- BREAK → `node.actionTarget()`
- BUILD / BRIDGE → `node.pos()`
- WALK / STEP_UP / JUMP_UP / DROP / DOOR → no terrain action target.

### 2.2 Single-use per-entity, per-tick lease

The executor owns a small lease state local to its entity instance:

- last lease tick;
- whether that tick's lease was consumed.

`tryAcquireTraversalAction(transition, target)` delegates authorization to the pure
`TraversalActionLeasePolicy`. It grants only when:

1. an active node exists;
2. the active and requested transitions are the same supported terrain action (BREAK, BUILD, BRIDGE);
3. the requested target exactly equals the target derived from the active node; and
4. no earlier request consumed the lease during the same world tick.

The lease is consumed when the attempt is authorized, even if the physical helper later fails. This prevents a
second goal/tool from trying a different modification during the same tick. The lease is per NavExecutor instance,
so no UUID registry or duplicate entity ownership state is needed. No generation counter/token was added: the
authorization and action execute synchronously on the server thread, while `terrainChanged()` invalidates the route;
subsequent requests therefore have no active node and fail closed.

### 2.3 All terrain changes require an explicit route transition

- BUILD acquires the lease for the exact `node.pos()` before calling the existing placement primitive.
- BREAK / BRIDGE recovery acquires the lease for the exact route target before invoking the injected action.
- Generic recovery based only on the strategic/global objective is no longer permitted to modify terrain.
- The entity's injected recovery callback rejects missing transition/node values and no longer falls back to
  `recoverNavigation(towards)`.
- `BreakBlockGoal` and `TraverseGoal` are no longer registered in `GoalSelector`. Their old independent
  target-Y-driven digging, bridging, stacking and breach decisions therefore cannot execute as active goals.
- Existing block helpers and their protections/budgets remain in source; this stage does not relax their internal
  checks. The old generic `recoverNavigation` method remains defined but has no call site in the current entity
  source.

The duplicate `terrainChanged()` invocation after a route BREAK was reduced to one call.

## 3. Added pure policy and test

New production policy:

`src/main/java/com/chipoodle/devilrpg/entity/nav/TraversalActionLeasePolicy.java`

New test:

`src/test/java/com/chipoodle/devilrpg/entity/nav/TraversalActionLeasePolicyTest.java`

Registered in `build.gradle` `policyTest`:

`TRAVERSAL_ACTION_LEASE_POLICY_TEST` — 13 assertions authored, **not executed by ChatGPT**.

Coverage includes exact active BREAK/BUILD/BRIDGE target, transition substitution rejection, missing active node,
missing transition/target, WALK/DROP denial, same-tick second-request denial, and next-tick reacquisition.

Existing support/headroom/protection/budget validations remain in their existing action primitives and tests.
This new pure policy does not replace those physical validations.

## 4. Commits and source blobs

Commits on `feature/autonomous-village`:

- `ff0a06727d78894fd1a502aac7b830438536193a` — NavExecutor lease and route-only recovery.
- `83ba75be5a78958d5784a577acff485276e7bf7a` — pure lease policy.
- `09d2710ed437f25b26efd3153af35ec7c8846fb1` — 13-assertion policy test.
- `7a49c43dc8980d61423b8b655ad6b826b77be8f7` — remove independent terrain goals and generic fallback.
- `9200bfffa70b85719d586ebbd32c0213dea7292b` — register test in policyTest.
- `7aaea19364d958ee0e16f9ea89b3b1be9168e878` — clarify route-only recovery contract.

Verified source blob SHAs at implementation HEAD:

| File | Blob SHA |
|---|---|
| `NavExecutor.java` | `b808fe591d0d2dcdf3b703bfdd4ca5d4e21ab181` |
| `TraversalActionLeasePolicy.java` | `6c748e7c896831b17f7142ad9e81b692b42c6ada` |
| `AggressiveZombieEntity.java` | `c7ff8d65522605f0e7478e90a9bf7a68570c95cc` |
| `TraversalActionLeasePolicyTest.java` | `ee65b408d8d9f04bbf13a0d00ee2850111ce529d` |
| `build.gradle` | `d75d65cd197b29112a5d4915e6e70614777d99c5` |

## 5. Validation state and Harness request

No compile, Gradle test, policy test, lint, diff-check, Minecraft launch, or runtime scenario has been run by ChatGPT.
The exact implementation HEAD is pending Harness validation.

Harness must:

1. Read the collaboration policy, current checkpoint, workflow contract, both continuation indexes, and this report.
2. Inspect local/remote HEAD, divergence, dirty/untracked files, and preserve all local-only work, backups, logs,
   partial files, fixtures and worlds. Use safe fast-forward only; stop on divergence or overwrite risk.
3. Validate exact SHA `7aaea19364d958ee0e16f9ea89b3b1be9168e878`.
4. Run the documented STATIC gate: `compileJava`, `test` (including `policyTest`), strict lint and
   `git diff --check`.
5. Explicitly report `TRAVERSAL_ACTION_LEASE_POLICY_TEST: OK (13 assertions)` or its exact failure.
6. Verify that no `new BreakBlockGoal` / `new TraverseGoal` registrations remain and that the injected
   recovery callback has no generic objective fallback.
7. Preserve production source unchanged. Harness may publish validation documentation only.

**STATIC ONLY. Do not launch Minecraft, runServer, the wrapper, A2, C4, Run2 or Full12K.**
No runtime authorization was given by this approval. A later runtime test must be requested separately and must use
the documented headless Harness configuration.

## 6. Remaining work / limitations

- The atomic custom 3×3 gate breach and wave-aware wall restoration are NOT implemented.
- Existing-gap-first behavior has not received a dedicated explicit check/test in this implementation stage.
  It remains a required follow-up review; do not claim it is complete.
- DROP remains a validated movement transition; this stage does not authorize digging as a DROP substitute.
- A2 remains OPEN. No A2/C4 acceptance status changes.
