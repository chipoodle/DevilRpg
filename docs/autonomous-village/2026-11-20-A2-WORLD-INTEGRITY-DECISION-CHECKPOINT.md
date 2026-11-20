# A2 world integrity — operational decision checkpoint (2026-11-20)

## Authority and source

- Branch: `feature/autonomous-village`
- Starting documentation HEAD: `dc8ef0a9f1300ac420f0efc29535f1968c5dbff1`
- Forensic report: `docs/autonomous-village/2026-11-20-A2-WORLD-INTEGRITY-FORENSICS.md`
- Forensic report SHA-256: `1C8E9E51461EACC180ED4503C98452CC7A9CDFDD59E71C4677E9B59FA4835385`
- Forensic investigation found no runtime, world, production, or test modifications.

## Operational classification

The two A2 diagnostic world copies are distinct and must not be conflated:

1. **Canonical pristine reference (preserve):** APPDATA
   `C:\\Users\\Christian\\AppData\\Roaming\\.minecraft\\saves\\DevilRpg-A2-IDX8-Diagnostico`
   SHA-256: `BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7`.
2. **Manually played derivative (preserve as evidence):** repository `run/saves`
   `D:\\Documents\\DevilRpg-autonomous\\run\\saves\\DevilRpg-A2-IDX8-Diagnostico`
   SHA-256: `F4EEAA13C56D92812B7B668EFDAF4AB200973A9977006466E969481C09419B44`.

The run/saves copy was manually opened by user `Dev` on 2026-11-19, reached tick 6104, and acquired playerdata, advancements, stats, expanded region/entity/POI data. It is not the pristine reference. This classification does **not** authorize deleting or replacing it.

## Preservation rules

- Do not modify, overwrite, restore, rename, move, or delete either protected A2 copy.
- Do not change the historical integrity hash to match the derivative.
- Preserve both protected C4 copies and all existing worlds, backups, logs, partial files, and artifacts.
- Do not use either protected A2 copy as the runtime target.

## Next permitted work — preparation only

Harness may prepare a **new disposable A2 world** for a future controlled run, using the canonical APPDATA snapshot as the source only if the established A2 fixture workflow requires that exact snapshot. The copy must be created under a unique disposable path in `build/a2n/<new-run-id>/`, never in either protected directory or `run/saves`.

Preparation must:
1. Read the collaboration policy, workflow contract, this checkpoint, the forensic report, and the D3 static validation report.
2. Verify branch/HEAD and worktree; preserve local-only files and all existing artifacts.
3. Verify the canonical source hash before copying and again afterward; never write to the source.
4. Create the disposable target without launching Minecraft or Gradle runtime.
5. Record source/target paths, full SHA-256 comparison, inventory, and preservation checks.
6. Stop and report. Do not execute the prepared command.

If the fixture workflow instead generates the exact A2 scenario from seed/configuration without cloning the APPDATA world, document that path and prepare it without launching anything. Do not guess or silently change fixture semantics.

## Runtime authorization gate — NOT GRANTED

No new A2 runtime authorization has been granted by this checkpoint. Do not launch Minecraft, runServer, the A2 launcher, A2 Run1, C4, Run2, or Full12K. A separate explicit user authorization is required after the disposable world is prepared and its integrity report is reviewed.

## Acceptance status

- D3: static PASS only (compile/tests/lint/diff as reported in the D3 validation report).
- D3 gameplay traversal: UNKNOWN; no runtime evidence.
- A2: OPEN; no current runtime acceptance.
- C4: not revalidated in this checkpoint.
- Run2 and Full12K: not authorized; Full12K remains gated by the established consecutive 8/8 strict A2 acceptance rule.

## Handoff

The immediate next action is **Harness preparation-only** as specified above. Report the exact resulting HEAD, disposable world path and hashes, source-preservation verification, and explicit `RUNTIME_EXECUTED: false`. Stop for user review and fresh runtime authorization.
