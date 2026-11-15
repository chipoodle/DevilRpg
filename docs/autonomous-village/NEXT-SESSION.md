# NEXT SESSION — A2 IDX8 / C4

Read first:

- `docs/autonomous-village/2026-11-15-A2-IDX8-C4-FORENSICS.md`
- `docs/autonomous-village/CONTINUAR.md`
- `docs/CONTINUAR.md`
- `docs/PENDIENTE.md`

Baseline: branch `feature/autonomous-village`, HEAD `518fee13fc15bfb9b92b0479d86b5c609d1947da`, dirty tree preserved.

## Immediate work

### 1. IDX8

- Run `a2n-20261115-024825`, UUID `0fa7101a-5d7d-45cd-906a-b90ae8c1001d`.
- Reproduce the local-target oscillation around `736,64,575` (`BREAK-DELEGADO Stone`) and final `RECOVERING` target `689,66,450`.
- Instrument `NavExecutor` local target selection and BREAK delegation for this UUID. Determine why the local target remains outside/behind the enclosure while the global mission is the village center.
- Do not increase budgets, weaken collision, teleport, or force entry.
- Deliverable to inspect: `DevilRpg-A2-IDX8-Diagnostico`.

### 2. C4

- Reproduce 9/12 with the same scenario; inspect:
  - weaponsmith `254f3367-795c-4c7b-afdb-75fb0a7115f2` at 652,62,431, no job site;
  - fisherman `f170acc0-a486-4c5f-8f0e-6e6833fa1c71` at 678,62,436, valid site 682,71,572;
  - mason `615e8c86-2d7b-4f3a-bdc4-c8756d370c6d` at 698,62,454, site 692,71,502.
- Instrument worker return/recovery when more than 80 blocks from center, and goal restart after reload.
- Do not weaken 12/12 or force job sites.
- Deliverable to inspect: `DevilRpg-C4-WorkerResume-Diagnostico`.

### 3. Worlds

Both are in standard launcher saves and `run/saves`:

```text
%APPDATA%\.minecraft\saves\DevilRpg-A2-IDX8-Diagnostico
%APPDATA%\.minecraft\saves\DevilRpg-C4-WorkerResume-Diagnostico
D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-A2-IDX8-Diagnostico
D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-C4-WorkerResume-Diagnostico
```

A2 inspection: `/tp @s 720 80 540` then look east/north at `747,63,536`.
C4 inspection: spawn `661,80,590`; failed workers at `652,62,431`, `678,62,436`, `698,62,454`.

### 4. Validation

```text
gradlew test
python tools/lint_aldea.py --strict
git diff --check
```

Do not run full 12k until focused corrections are reproducible.
