# C4 / A2 — Disposable foundation repair and runtime verification

**Result: PASS — exactly 33 approved foundation cells repaired in a second disposable world. C4 raid acceptance remains OPEN/BLOCKED.**

## Run identity and scope

- Run: `arena-repair1-20261112-205206`
- Repository: `D:\Documents\DevilRpg-autonomous`
- Branch / HEAD: `feature/autonomous-village` / `518fee13fc15bfb9b92b0479d86b5c609d1947da`
- Minecraft 1.21.1 / NeoForge 21.1.249 / OpenJDK 21
- World: `build/a2n/arena-repair1-20261112-205206/game/a2n`
- Fresh second disposable world; NBT `WorldGenSettings.seed=12345`
- Fixed center `661,71,533`, ring radius 62.
- Port 25578 was free; no other A2/server process existed at preflight.
- This world is separate from the read-only diagnostic world
  `arena-probe4-20261112-204522`; its `level.dat` SHA-256 remains
  `B25FC62749A1783F4375CE32B1095B546D85483AF8FAEF782A4D8AB29B436996`.

## Before-write control

Before enabling repair, the second disposable world was started in read-only
mode and scanned with the runtime collision-shape probe:

- 32/32 chunks loaded; 400/400 unique ring columns scanned.
- 33 EMPTY, 367 NON_EMPTY, 0 NO_NON_AIR, 0 missing chunks.
- The set of all 33 `(x,y,z,block)` entries matched the first diagnostic
  manifest exactly (same water positions, all at Y=62).
- No repair was enabled during this baseline run.

This equality check was a hard precondition. Repair was not launched until the
second world's baseline manifest matched all 33 approved coordinates.

## Repair execution

The existing `A2FoundationBootstrap` was used without editing its source.
Its two gates were explicitly enabled for this disposable repair run:
`a2n.foundationRepair=true` and `a2n.mode=raid`. The repair bootstrap is the
only source consumer of `a2n.mode` besides the excluded protected
`A2NavHarness.java`. The probe-only Gradle source set excluded
`A2NavHarness`, `A2EntryProbe`, `A2ProdNavProbe`, and `AsedioRunA2`;
therefore no A2 harness or raid entry point was loaded. No `[A2N]` or raid
spawn marker appears.

Runtime log:

```text
[A2FOUND] pre-selection repair cells=33 chunks=32 center=661,71,533 r=62
[A2PROBE] READY ticks=1 chunks=32
[A2PROBE] DONE columns=400 empty=0 nonEmpty=400 none=0 missing=0
[A2PROBE] CLEANUP released=32/32
BUILD SUCCESSFUL
```

The bootstrap replaced the first non-air empty-collision state with
`minecraft:stone` at each of the 33 approved positions, all at Y=62.

## Exact before/after verification

| Metric | Before repair | After repair |
|---|---:|---:|
| Columns scanned | 400 | 400 |
| Empty collision shape | 33 | **0** |
| Non-empty collision shape | 367 | **400** |
| No non-air state | 0 | 0 |
| Missing chunks | 0 | 0 |

An independent coordinate-by-coordinate comparison of the two runtime JSON
manifests proves:

- **33 changed:** `minecraft:water` / EMPTY → `minecraft:stone` / NON_EMPTY,
  same Y=62 and exact approved X/Z positions.
- **367 unchanged:** same first non-air block, Y, and collision status.
- **0 unexpected changes.**
- **0 remaining non-colliding columns.**

The repair-diff JSON was generated from both runtime manifests and records the
complete 33-row change list. The post-repair probe reports status `OK`.

## Isolation and integrity

- No A2 raid, player/client, or spawn workflow launched.
- No `A2NavHarness.java` or `.partial` modification.
- Protected SHA-256 values before/after:
  - `A2NavHarness.java`:
    `11980C98668EF5D13E4CF3A276C9782392A9187F51B3EF63D7FC88141FA4C940`
  - `A2NavHarness.java.partial`:
    `2C3EBA9ED6E936B595E26650B796A624838B5CB4FF6CFD7B1804BE7A13552127`
- Repair source `A2FoundationBootstrap.java` SHA-256:
  `808D7DB68F6DF4D126DA26C7137E235B1FE87C02E1125D7C3A71968DF9C86487`
  (unchanged; no source patch).
- Probe source SHA-256:
  `E91B471CB21F21242D6A938B9345A24FC97439C7C663AEF5DAB3B61DB7DF7ED3`
- All 32 chunk tickets released; server stopped and saved the disposable world.
  No matching Java/server process remained.

## Evidence artifacts and hashes

All paths are relative to repository root:

- Before manifest:
  `build/a2n/arena-repair1-20261112-205206/report-pre/runtime-foundation.json`
  SHA-256 `70027562072B9DB6122048BD3B8023089693EFECCADE31ECBB8601A01633EE23`
- After manifest:
  `build/a2n/arena-repair1-20261112-205206/report-post/runtime-foundation.json`
  SHA-256 `930BFA251551013929948AF6D4B74FC83786FFAF9E21257D173EF887426C0AC0`
- After text manifest:
  `build/a2n/arena-repair1-20261112-205206/report-post/runtime-foundation.txt`
  SHA-256 `114EE536DA78D8410D9E6EED5E8C4903A56FCCE921660C28E9493E7612E55388`
- Repair log:
  `build/a2n/arena-repair1-20261112-205206/repair.log`
  SHA-256 `B90CE15336DB26C86EA6F927F9905DEE115B1A9ED5E864FEA1189380676325F0`
- Coordinate diff:
  `build/a2n/arena-repair1-20261112-205206/repair-diff.json`
  SHA-256 `60CB4D8371829C9A00F27EEC199C2CC9EDD7E7AAB18C747910D60B288029C7FD`
- Repaired disposable world `level.dat` SHA-256:
  `4D9516A994A6CFF1D50C9EA6667F2E4CD0CCB7EB96438F26815BDA4FCD94EEEF`

## Decision boundary / next step

The foundation repair and 400-column runtime verification are PASS **in this
disposable world only**. This does not accept C4 and does not authorize the
full A2 raid. The A2 handoff still records Edit 5 (launcher/parser result
contract) and the one full end-to-end raid run as pending.

No protected harness edits were made. Before modifying
`A2NavHarness.java` or running the full raid, obtain separate owner approval.
Do not touch historical worlds, `run/saves`, or the protected `.partial`.
No commit or push.
