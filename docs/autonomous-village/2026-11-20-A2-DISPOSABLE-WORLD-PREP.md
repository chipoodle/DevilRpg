# A2 disposable world preparation (2026-11-20)

## 1. Result

**COMPLETE — preparation only.** A unique disposable target was reserved. The established A2 workflow
generates its world at runtime from seed/config, so no `level.dat`/region data exists yet; creating it
would require the prohibited Minecraft launch.

- BRANCH: `feature/autonomous-village`
- INITIAL_HEAD: `dc8ef0a9f1300ac420f0efc29535f1968c5dbff1`
- FINAL_HEAD at preparation: `4b67659b8ba1ef459c4f9b9391868b8da5af28ab`
- RUNTIME_EXECUTED: false
- MINECRAFT_LAUNCHED: false
- PROTECTED_WORLDS_MODIFIED: false
- PRODUCTION_MODIFIED: false
- TESTS_MODIFIED: false

## 2. Workflow decision

The A2 launcher does not require cloning the APPDATA snapshot. It creates:

```text
build/a2n/<runId>/game/a2n
```

with:

```text
level-name=a2n
level-seed=12345
level-type=minecraft:normal
server-port=25641
view-distance=10
simulation-distance=10
gamemode=creative
difficulty=easy
```

The canonical APPDATA snapshot is a manual-observation reference, not the runtime source. No clone was
made, so there is no source copy hash to compare.

## 3. Reserved target

```text
build/a2n/a2n-20261120-135343-prep/
  PREP-MANIFEST.txt  (415 bytes)
```

```text
MANIFEST_SHA256:
DD66E458EFC7BE980DCA896BCA6D6AD639423F270974A8726B77311A3BFCE677
```

This is a **reservation**, not a generated world: it has no `game/`, `level.dat`, or region files. The
launcher will create its own unique `a2n-<timestamp>` runtime directory when authorized.

## 4. Prepared runtime command (NOT executed)

```powershell
$env:ORG_GRADLE_PROJECT_a2nDiagnostics='true'
$env:ORG_GRADLE_PROJECT_a2nPopulationQueryHarness='true'
$env:ORG_GRADLE_PROJECT_a2nTraceNav='true'
$env:ORG_GRADLE_PROJECT_a2nTraceGlobalSearch='true'
$env:JDK_JAVA_OPTIONS='-Ddevilrpg.a2n.traceNav=true -Ddevilrpg.a2n.traceGlobalSearch=true -Ddevilrpg.a2n.traceStair=true -Ddevilrpg.a2n.traceStairCandidates=true'

.\build\a2n\lanzar-a2n.ps1 -Modo raid -Semilla 12345 -LimiteMinutos 60 `
  -Pared 0 -FaseA 3000 -FaseB 6000 -Rampa 0 -Perfil 0 -Inundacion 0 `
  -NavCustom true -Presupuesto 900 -GlobalBudget 8000
```

The wrapper is not used because `tools/a2n/lanzar-phase55-diagnostico.ps1` still has the array-splat
named-argument defect (`& $launcher @LauncherArgs`).

## 5. Protected worlds

```text
APPDATA A2: BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
run A2:     F4EEAA13C56D92812B7B668EFDAF4AB200973A9977006466E969481C09419B44
APPDATA C4: 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
run C4:     11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

All unchanged before and after preparation. Neither protected A2 copy was used.

## 6. Preservation

Untracked preserved: `.a2-backups/`, `src/foodTest/`, `tools/a2n/A2NavHarness.java.partial`,
`tools/a2n/consultar-c4.ps1`, `tools/a2n/lanzar-c4.ps1`, `tools/a5-closure/`.

No production/test files changed. No artifact deleted.

## 7. Integrity boundary

The disposable world itself cannot exist until Minecraft generates it. Therefore:

```text
TARGET_RESERVED: yes
TARGET_WORLD_GENERATED: no
TARGET_LEVEL_DAT: absent
```

This is intentional under the no-runtime restriction. Runtime authorization remains separate.
