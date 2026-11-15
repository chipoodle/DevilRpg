# C4 / A2 — Runtime foundation probe (read-only)

**Result: PASS — diagnostic only. C4 remains BLOCKED. No terrain repair or A2 raid was authorized or performed.**

## Run identity

- Run: `arena-probe4-20261112-204522`
- Repository: `D:\Documents\DevilRpg-autonomous`
- Branch / HEAD: `feature/autonomous-village` / `518fee13fc15bfb9b92b0479d86b5c609d1947da`
- Runtime: Minecraft 1.21.1, NeoForge 21.1.249, Microsoft OpenJDK 21.0.7
- Fresh game directory: `build/a2n/arena-probe4-20261112-204522/game`
- World: `a2n`; `level.dat` NBT `WorldGenSettings.seed=12345`, `DataVersion=3955`
- Fixed center: `661,71,533`; ring radius: 62
- No pre-existing world at preflight. The world was generated inside this run directory only.

## Isolation and no-write proof

- Built with `-Pa2nDiagnostics=true -Pa2nProbeOnly=true`.
- Verified absent from compiled debug output: `A2NavHarness.class`,
  `A2EntryProbe.class`, `A2ProdNavProbe.class`, `AsedioRunA2.class`.
- `ArenaFoundationProbe.class` was present.
- Runtime properties: `a2n.foundationProbe=true`,
  `a2n.foundationRepair=false`, `a2n.mode=none`,
  `a5.scenario=none`, `a4lab.case=NONE`, `n4.case=NONE`,
  `n4.r2wall=false`, `r3.lab=false`,
  `devilrpg.taskBoundaryProbe=false`, `devilrpg.nav.custom=false`.
  Environment variable `DEVILRPG_FOUNDATION_REPAIR` was cleared for the child
  process.
- The existing `A2FoundationBootstrap` class was compiled as part of the
  diagnostics source set, but its repair gate was explicitly false and
  `a2n.mode=none`; no `[A2FOUND]` marker appears in the server log.
- `ArenaFoundationProbe` reads block states and calls
  `getCollisionShape`; it does not call `setBlock` or
  `setBlockAndUpdate`. It only forces the 32 required chunks for loading and
  releases all tickets afterwards.
- No `[A2N]` raid/harness or spawn marker. No A2 raid launched.
- Cleanup: `[A2PROBE] CLEANUP released=32/32`; server halted and saved the
  disposable world. No matching Java/server process remained.
- SHA-256 of protected harness files before and after is identical:
  - `A2NavHarness.java`: `11980C98668EF5D13E4CF3A276C9782392A9187F51B3EF63D7FC88141FA4C940`
  - `A2NavHarness.java.partial`: `2C3EBA9ED6E936B595E26650B796A624838B5CB4FF6CFD7B1804BE7A13552127`

## Runtime result

The probe forced and loaded **32/32 chunks** (READY at tick 1), then inspected
**400/400 unique ring columns** using the runtime collision-shape API.

| Metric | Result |
|---|---:|
| Columns scanned | 400 |
| First non-air state with empty collision shape | **33** |
| First non-air state with non-empty collision shape | 367 |
| No non-air state in scan interval | 0 |
| Missing chunks | 0 |
| Report status | `OK` |

All 33 empty-collision columns are `minecraft:water` at Y=62. Exact coordinates:

```text
(680,62,474) (681,62,474) (682,62,475) (683,62,475)
(684,62,475) (685,62,476) (686,62,476) (687,62,477)
(688,62,477) (689,62,478) (690,62,478) (691,62,479)
(692,62,479) (693,62,480) (694,62,480) (694,62,481)
(711,62,569) (711,62,570) (712,62,568) (712,62,569)
(713,62,566) (713,62,567) (714,62,565) (714,62,566)
(715,62,563) (715,62,564) (716,62,561) (716,62,562)
(717,62,559) (717,62,560) (718,62,557) (718,62,558)
(719,62,556)
```

## Evidence artifacts and hashes

All paths are relative to repository root:

- Full JSON manifest:
  `build/a2n/arena-probe4-20261112-204522/report/runtime-foundation.json`
  SHA-256 `70027562072B9DB6122048BD3B8023089693EFECCADE31ECBB8601A01633EE23`
- Full text manifest:
  `build/a2n/arena-probe4-20261112-204522/report/runtime-foundation.txt`
  SHA-256 `4C25D19B1F23ADE0CBBA65554FCA374A6D774E688EECFDC6910D1D9C8923ED05`
- Server log:
  `build/a2n/arena-probe4-20261112-204522/srv.log`
  SHA-256 `011DCC329C7A7C68B261E4BED41F166F5C2106A8A140C026EF263605331B66CF`
- Disposable world `level.dat` SHA-256:
  `B25FC62749A1783F4375CE32B1095B546D85483AF8FAEF782A4D8AB29B436996`
- Probe source SHA-256:
  `E91B471CB21F21242D6A938B9345A24FC97439C7C663AEF5DAB3B61DB7DF7ED3`

The first launcher attempt failed during Gradle initialization because its temporary
init script had a UTF-8 BOM. It failed before server/world creation. The corrected
BOM-free init script was used for the successful run above.

## Decision boundary / next step

This PASS proves the diagnostic and exact 33-column manifest only. It does **not**
accept C4, prepare a raid arena, or authorize block writes.

**No repair has been applied.** The next step is owner review of the 33 positions
and a separate explicit decision on whether those exact first-non-air water
blocks may be replaced with support blocks in another disposable copy. Do not
modify historical worlds, `run/saves`, `A2NavHarness.java`, or
`A2NavHarness.java.partial`. Do not launch the A2 raid until a separate repair
decision and validation plan are approved.

## Follow-up — foundation repair PASS (12-nov-2026)

The original statement above that no repair had been applied describes the
read-only probe run only and is now superseded by the separately authorized
repair run. A second disposable world was created and first scanned read-only;
its 33 water coordinates matched this manifest exactly. Then the 33 approved
positions were replaced with stone in that second world only. Post-repair
runtime scan: 400/400 non-empty collision shapes, 0 empty, 0 missing chunks.
Coordinate diff: 33 water→stone, 367 unchanged, 0 unexpected changes.

Full repair evidence and hashes:
`A2-FOUNDATION-REPAIR-20261112-ACCEPTANCE.md`.
C4 full-raid acceptance remains open; no A2 raid or protected-harness edit
was authorized or performed.
