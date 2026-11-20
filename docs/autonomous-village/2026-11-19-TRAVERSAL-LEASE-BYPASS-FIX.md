# Traversal lease bypass correction (2026-11-19)

## 1. Status

**IMPLEMENTED — STATIC VALIDATION PENDING.** This corrects both bypasses reported by Harness.
No compile, test, lint, diff-check, Minecraft launch, runtime scenario, or world operation was executed by ChatGPT.

- Repository: `chipoodle/DevilRpg`
- Branch: `feature/autonomous-village`
- Base HEAD: `4727d86a03c8d6100c489145712f06a8c1890ec7`
- Source implementation HEAD: `9f00cb5ecec462df91f9905072988db90bec08b2`
- Prior Harness report: `docs/autonomous-village/2026-11-19-TRAVERSAL-COORDINATION-STATIC-VALIDATION.md`
- Prior Harness result: FAIL — compile/test/lint/diff PASS, behavioral audit found two lease bypasses.
- `PRODUCTION_MODIFIED_BY_HARNESS=false`
- `RUNTIME_EXECUTED=false`
- `MINECRAFT_LAUNCHED=false`
- `WORLDS_MODIFIED=false`
- A2 remains OPEN; C4 was not revalidated.

## 2. Harness findings addressed

### 2.1 Normal BREAK bypass

Before the correction, `ejecutarBreakDesdePosicionDeAtaque` invoked `recoveryAction.run(..., BREAK)`
directly. It now calls the single executor gateway:

`invokeTerrainAction(BREAK, target, objective)`

The gateway:
1. rejects a missing callback;
2. calls `tryAcquireTraversalAction(transition, target)`;
3. passes the result to `TraversalActionDispatchPolicy.dispatch`;
4. invokes the callback only if the lease was acquired.

The route's active BREAK node must therefore match the exact `actionTarget`, and the per-entity/per-tick
lease is consumed before the callback executes.

### 2.2 DROP-obstruction BREAK

Decision: **DROP cannot authorize a BREAK tool.**

The opportunistic callback that attempted to break a solid block in a rejected drop column has been removed.
The DROP path still identifies the obstruction and preserves the existing landing/edge exclusion behavior so
the planner can route around that destination. If demolition is necessary, the route must contain an explicit
BREAK transition and pass through the normal BREAK lease gateway.

This follows the existing transition matrix: DROP is movement through a validated descent, not permission to
substitute demolition. No excavation or other terrain tool is authorized by DROP.

### 2.3 Recovery BREAK/BRIDGE

The recovery path now calls the same `invokeTerrainAction` gateway. The only direct
`recoveryAction.run(...)` invocation remaining in `NavExecutor` is inside that gateway.

## 3. Tests authored

New production dispatch seam:

`src/main/java/com/chipoodle/devilrpg/entity/nav/TraversalActionDispatchPolicy.java`

New test:

`src/test/java/com/chipoodle/devilrpg/entity/nav/TraversalActionDispatchPolicyTest.java`

Registered in `build.gradle` `policyTest`.

`TRAVERSAL_ACTION_DISPATCH_POLICY_TEST` — 6 assertions authored, **not run by ChatGPT**:

1. callback is not invoked without a lease;
2. exact active BREAK lease dispatches the callback exactly once;
3. active DROP cannot dispatch BREAK;
4. DROP cannot acquire a terrain-tool lease;
5. a mismatched BREAK target cannot dispatch;
6. a failed action still consumes the lease and a second same-tick dispatch remains closed.

This is an integration-seam policy test: it composes the actual lease authorization policy with the exact dispatch
gate called by NavExecutor. It does not instantiate a Minecraft world or a full NavExecutor entity. Harness must
run it and independently audit the production call sites.

The earlier `TRAVERSAL_ACTION_LEASE_POLICY_TEST` (13 assertions) remains registered and unchanged.

## 4. Source commits

- `c2a363448d9d91d8919ad42c8268dc8715ca2f71` — route normal BREAK and recovery through one lease gateway; remove DROP fallback.
- `5cd6428d7ecce82262fcce81f2ec5da2c9df4fb7` — add dispatch policy.
- `a0b2c3cce35efdb06b97765c306a6960411360c1` — add six-assertion dispatch seam test.
- `4ccc694077ad9315a1d13eeb04c5ff6623209c68` — register test in policyTest.
- `9f00cb5ecec462df91f9905072988db90bec08b2` — strengthen same-tick denial test to verify callback is not invoked.

Verified blobs at source implementation HEAD:

| File | Blob SHA |
|---|---|
| `NavExecutor.java` | `908fcaa323c5f6f08cd186c55650cab71c1d28ce` |
| `TraversalActionDispatchPolicy.java` | `6309324398d3d21b54f9a8e34ab24540d3e590a8` |
| `TraversalActionDispatchPolicyTest.java` | `1f62e839c0492e8b21640b37d0488c0fad018532` |
| `build.gradle` | `899208c0b5ba7ad3e50f166fb2dc26c3b9bc1a67` |

## 5. Source audit performed by ChatGPT (not execution)

- `NavExecutor.java` contains one direct `recoveryAction.run(` occurrence, inside `invokeTerrainAction`.
- Normal BREAK calls `invokeTerrainAction(BREAK, target, objective)`.
- Recovery BREAK/BRIDGE calls `invokeTerrainAction(transicion, nodo, towards)`.
- `tryAcquireTraversalAction` is called by the shared gateway and the existing BUILD route branch.
- The DROP rejection branch no longer calls a recovery callback or emits `DROP_OBSTRUCTION_CLEARED`.
- The obstruction is still used by the existing drop-edge and destination exclusion logic.
- No change was made to action budgets, cooldown constants, protection rules, support/headroom, collision, route geometry,
  A2/C4 acceptance criteria, fixtures, or worlds.

This is source inspection only. It is not proof of compilation or runtime behavior.

## 6. Required Harness validation — STATIC ONLY

Harness must:

1. Read the permanent collaboration policy, latest `NEXT-SESSION.md`, workflow contract, continuation indexes,
   previous validation report and this correction report.
2. Inspect local/remote HEAD, divergence, dirty/untracked files and preserve all local-only work, backups, logs,
   partial files, fixtures and worlds. Safe fast-forward only; stop on conflict/overwrite risk.
3. Validate exact source implementation SHA `9f00cb5ecec462df91f9905072988db90bec08b2` (or a later documentation-only
   descendant after verifying all four source blobs above).
4. Run the standard static gate: compileJava, Gradle test/policyTest, strict lint and `git diff --check`.
5. Confirm:
   - `TRAVERSAL_ACTION_LEASE_POLICY_TEST: OK (13 assertions)`;
   - `TRAVERSAL_ACTION_DISPATCH_POLICY_TEST: OK (6 assertions)`;
   - all previously registered policy groups;
   - only one direct `recoveryAction.run(` call exists in NavExecutor and it is inside `invokeTerrainAction`;
   - normal BREAK and recovery use the gateway;
   - DROP rejection has no BREAK callback and continues the exclusion/replan path.
6. Do not modify production source. Harness may publish validation report and continuation documentation only.
7. Stop after static validation.

**No runtime authorization:** do not launch Minecraft, runServer, wrapper, A2, C4, Run2 or Full12K.
A2 remains OPEN. This correction does not revalidate A2 or C4.
