# A8-F03-R17 — Harness Runtime Capability & Recovery

Status: **PARTIAL**. Runtime control is proven. C1/C2/C3 and physical gate traversal were not
executed.

## Baseline

```text
branch feature/autonomous-village
HEAD 97220be5453b57387002b9bc91935807cc665274
staged 0
unstaged 26
untracked 174
```

## Phase A — runtime capability: PASS

Command:

```powershell
.\build\a5-closure\run-a5.ps1 -Run stabilization-block1-scan-20261030-226 -Level A5WALL -Scenario agent-smoke ...
```

Output:

```text
LAUNCH pid=30852 ... level=A5WALL scenario=agent-smoke
[AGENT-SMOKE] villagers=16 agents=16 missing=0
[A5] FIN result=PASS detail=agent lifecycle smoke
SHUTDOWN ... result=PASS
```

This proves the session can launch/control a disposable Minecraft instance, read its logs,
and inspect the disposable world.

## C1 — target removed before navigation

**NOT RUN.** No dedicated fixture was executed. The existing r6 recovery removes A during
active navigation, which is a different precondition.

## C2 — invalid/non-log target

**NOT RUN.**

## C3 — obstructed/unreachable

**NOT RUN.**

## Gate fixture

**NOT RUN.** No valid outside-wall fixture, `wallGate=true`, physical crossing, external
arrival, or negative control.

## Production changes

None. No NBT, protected world, forestry operation, wall or authority change.

## Verification

```text
compileJava = BUILD SUCCESSFUL
NAVIGATION_LEASE_TEST = OK
NAVIGATION_GATEWAY_POLICY_TEST = OK
NAVIGATION_GATEWAY_HARNESS_TEST = OK
lint_aldea.py --strict = PASS
git diff --check = PASS
```

## Classification

**PARTIAL.** Runtime capability is PASS; remaining scenarios NOT RUN. A8-F03 remains open.

## Next executable step

Build a C1 fixture that removes the target before `navigateTo` authorization, then execute
it on a fresh disposable copy. Gate work remains independent.
