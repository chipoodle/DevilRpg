# A8-F03-R8 — Lease Continuity, Recovery Lifecycle & Physical Gate Closure

Status: **PARTIAL / BLOCKED**. The R7 empty B lease is classified as a probable
OBSERVATION_RACE, not a proven defect. Full B arrival, second cycles, negative cases and
physical gate traversal remain NOT RUN.

## 1. Baseline

```text
branch feature/autonomous-village
HEAD 97220be5453b57387002b9bc91935807cc665274
staged 0
unstaged 26
untracked 168
```

No protected save, NBT change, prohibited Git operation, commit or push.

## 2. Lease call chain

- `VillagerAgent.nextTalarStep` computes a Step; it does **not** acquire a lease.
- `VillagerAgent.navigateTo` calls `syncForestryNavigation`, which acquires/renews the
  lease from the observed snapshot, then calls the gateway.
- `VillagerGoal.navegar` calls `nextTalarStep` via `pasoDeCamino`, then `navigateTo`.
- `syncForestryNavigation` requires a non-null active snapshot task.
- Registry updates the agent snapshot after the Goal tick.

## 3. R7 B lease diagnosis

Run 222 sample at B selection:

```text
targetB=45,97,-38
taskB=af7eb131...
pathNow=1316776743 active=true
lease=Optional.empty
```

The sample was taken in `B-SELECTED`, before the next navigateTo/sync. Since
`nextTalarStep` does not acquire a lease, an empty lease at that instant is compatible with
OBSERVATION_RACE. It is not proof of MISSED_ACQUISITION or PREMATURE_RELEASE.

Missing evidence: whether `navigateTo` for B subsequently acquired a lease and whether the
B path was agent-issued. No such log was captured. Classification:
**INSUFFICIENT_EVIDENCE / probable OBSERVATION_RACE**.

## 4. Track A/B

R7 evidence remains: active A with WORK lease, A removal, B selection, distinct IDs, path
change. Still missing: explicit cache-clear reason, old path stop/release, B lease during
navigation, B arrival, second cycle and negative cases. **NOT RUN**.

## 5. Track C

No outside-wall gate fixture, no `wallGate=true`, no physical crossing, no negative control.
**NOT RUN**.

## 6. Production changes

None. No lease semantics changed.

## 7. Verification

```text
compileJava = BUILD SUCCESSFUL
NAVIGATION_LEASE_TEST = OK
NAVIGATION_GATEWAY_POLICY_TEST = OK
NAVIGATION_GATEWAY_HARNESS_TEST = OK
lint_aldea.py --strict = PASS
git diff --check = PASS
```

## 8. Acceptance matrix

| Criterion | Status |
|---|---|
| A removal during active nav | PASS (R7 historical) |
| Cache invalidation reason | NOT OBSERVED |
| Old path cleanup | PARTIAL |
| Lease release/transfer | PARTIAL |
| B distinct identity | PASS (R7) |
| B Step/command | PARTIAL |
| B lease ownership | INSUFFICIENT EVIDENCE |
| B arrival | NOT RUN |
| Negative cases | NOT RUN |
| Gate selection | NOT RUN |
| Physical crossing | NOT RUN |
| External arrival | NOT RUN |
| Ownership invariants | PASS source |

## 9. Remaining closure criteria

1. Observe B `navigateTo` lease acquisition and the B path under that lease.
2. Capture cache invalidation and old path stop/release in the same run.
3. Prove B arrival and lease cleanup.
4. Build/run an actual outside-wall gate fixture and physical crossing.

## 10. Classification

**PARTIAL / BLOCKED.** R8 did not close A8-F03. No production defect is proven; the empty
lease is currently an observation-race hypothesis, not a confirmed violation. No further
narrow campaign should be created; these four conditions are the remaining acceptance set.
