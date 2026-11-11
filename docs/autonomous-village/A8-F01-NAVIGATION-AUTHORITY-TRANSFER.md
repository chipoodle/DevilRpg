# A8-F01 — First Functional Forestry Navigation Authority Transfer

Status: **PASS (first vertical slice)**. In ENFORCE, `VillagerAgent` now acquires the
forestry navigation lease and issues the navigation command. The legacy Goal still selects
targets and performs all forestry operations. OFF is unchanged.

## Baseline

```text
branch feature/autonomous-village
HEAD 97220be5453b57387002b9bc91935807cc665274
staged 0
unstaged 26
untracked 156
```

## Authority before

Before this change, `VillagerLumberjackGoal` acquired/renewed the lease and called
`ForestryNavigationGateway.moveTo/stop` directly. The agent was a credential/observer, not
the command authority.

## Authority after

In ENFORCE:

- `VillagerLumberjackGoal` still computes the target/path step and calls
  `navegar(...)` / `pararNavegacion(...)`.
- `VillagerAgent.navigateTo` / `stopNavigation` issue the gateway command.
- `VillagerAgent.syncForestryNavigation` acquires or renews the lease from the observed
  forestry snapshot.
- `navigateTo` does not issue a command without a real task ID in ENFORCE.
- Completion/clear calls `VillagerAgent.releaseNavigation`, releasing the lease.
- Stale tasks remain protected by the gateway's existing owner check.

In OFF, the legacy Goal calls the gateway exactly as before; no agent lease is required.

## Production files modified

- `agent/VillagerAgent.java` — agent-owned navigate/stop/sync/release.
- `entity/goal/VillagerLumberjackGoal.java` — ENFORCE routes commands through the agent.
- `debug/A5TestController.java` — test-only mode/diagnostic plumbing.
- test harness script — mode property only.

No controller, planner, storage, pantry, NBT or operation logic changed.

## Runtime evidence — Run 216 (ENFORCE)

Disposable A5WALL copy; scenario `lumber-deliver-harvest`; 6-minute bound.

```text
AGENT-AUTH ACQUIRE uuid=1fa6980f-... task=a5dd81fe-...
T2-HARVEST TALAR target=53,97,-17 tick=1167
T2-HARVEST POST-HARVEST DELIVERING target=127,97,20 inv=4 tick=1229
AGENT-AUTH ACQUIRE uuid=1fa6980f-... task=ac599abc-... (delivery task)
T2-HARVEST END warehouse=130 delta=2 tick=1765
[A5] FIN PASS detail=natural harvest delivery
```

The lumberjack navigated, harvested and delivered while the agent held leases for the
forestry task. No production operation was moved into the agent.

## Compatibility

- OFF: Run 209/211 legacy behavior remains PASS.
- ENFORCE: agent commands are gated by lease; unauthorized commands are rejected by the
  existing gateway policy.
- Previous Run 205 stale-stop protection remains valid.
- No vanilla Brain/GoalSelector changes.

## Verification

```text
compileJava = BUILD SUCCESSFUL
NAVIGATION_LEASE_TEST = OK
NAVIGATION_GATEWAY_POLICY_TEST = OK
NAVIGATION_GATEWAY_HARNESS_TEST = OK
lint_aldea.py --strict = PASS
git diff --check = PASS
```

## Limitations

- Only the navigation command is transferred; target selection, harvesting, replanting,
  growing, delivery and accounting remain in `VillagerLumberjackGoal`.
- Agent snapshot is updated by the registry after the goal tick, so a new target can take
  one tick to become command-eligible.
- No persistent task ID; identity is transient.
- ENFORCE remains opt-in; OFF is default.
- The A7-12 OFF/AUDIT resource non-equivalence remains unresolved and is not addressed by
  this milestone.

## Classification

**PASS for the first controlled navigation authority transfer.** The legacy Goal no longer
calls the gateway directly in ENFORCE; the agent does, using its own lease identity.
