# A2 world integrity — read-only forensics (2026-11-20)

## 1. Result

**COMPLETE.** The `run/saves` copy is not a metadata-only variant: it is a **manually played derivative**
of the pristine A2 diagnostic snapshot. The APPDATA copy remains the original at the historical hash.

- BRANCH: `feature/autonomous-village`
- HEAD: `fb29d7f29c9d3f5bba59555df0a042aba5344bea`
- APPDATA A2, pristine: `BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7`
- run/saves A2, observed: `F4EEAA13C56D92812B7B668EFDAF4AB200973A9977006466E969481C09419B44`
- RUNTIME_EXECUTED: false
- WORLDS_MODIFIED: false
- PRODUCTION_MODIFIED: false

## 2. Paths

```text
A (canonical):
C:\Users\Christian\AppData\Roaming\.minecraft\saves\DevilRpg-A2-IDX8-Diagnostico

B (derivative):
D:\Documents\DevilRpg-autonomous\run\saves\DevilRpg-A2-IDX8-Diagnostico
```

Both preserve the same `README-DIAGNOSTICO.txt` (identical hash).

## 3. Inventory / hashes

```text
A files: 28
B files: 47
A-only:  0
B-only:  19
common:  28
different common: 22
identical common: 6
```

### Different common files

```text
DIM-1/data/raids.dat
  A EA15F66EA9DF6FE5E71A0E77201FB811533101870A655CC794D348EC7B145013 (90)
  B B1259FEC478332D0B66676AB1841D3EC44AF6266F33EA0DAC87293BAD95DF845 (90)

DIM1/data/raids_end.dat
  A EA15F66E... (90)
  B B1259FEC... (90)

data/devilrpg_villages.dat
  A 2383C9B28F736B9D44FB59F3600022D15B031208A7C18B5360531D3783461256 (22412)
  B 6E8B4D74F0489DCDF7B3E905C7AFEC218DE8C3C09889F116B0389B665E6805B9 (22530)

data/raids.dat
  A EA15F66E... (90)
  B B1259FEC... (90)

data/random_sequences.dat
  A ABC0F152EB46EEF9D05CC4D190CFCF76869F074AEABE8382069926C737DB18DF (1124)
  B 67D3214B217A425A9DD9934DB3E8B55D8F98D8AA0F7D7272905DBFD4E706841E (1537)

entities/r.0.-1.mca
  A 7C99C4511D6BD3DAC5FE8B3D5AC56B5F54DB1E54AF7EEEB191B6DF1C383AF1D4 (16384)
  B E32DEED82D91BDECFB60B2110AE5C9B524FC89D04B9D566DFCD79C509BB375E1 (167936)

entities/r.0.0.mca
  A 3571C436C5A2BABCD435B4DBCB3F7255D0E5DAAFBE6966CF3CED088EBDADBC48 (16384)
  B 53D18C3191BFB89FD7B20821FDFF129C4A44BC7388B385EFDFC32A2FCF377836 (536576)

entities/r.0.1.mca
  A E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855 (0)
  B 67FAD49831001BA97A95654551C08E9D1D3ADCFA0774EB4D9E1832EEFFC21AA8 (196608)

entities/r.1.0.mca
  A 928991FF18B109287BC42BC98C5D0550D581DCFB48BF4AB7910C29C40641604D (118784)
  B 11F166B4B170EE2E1500A0FDE49F7B21ED7871E955568B384C60BBBB78FEFB6F (233472)

entities/r.1.1.mca
  A 7E962B361BE5745189D78C6336E8A714B74506F9FA5D2343047A7AE209CAEBC9 (196608)
  B 2D250B0AD7D46A280B57808ECF5D832520FCE01CC1307725F17D2E40B9DAD6F5 (458752)

level.dat
  A BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7 (1673)
  B F4EEAA13C56D92812B7B668EFDAF4AB200973A9977006466E969481C09419B44 (4677)

level.dat_old
  A 1E58AF58E79D54A0BD90A4164BBE9C49EF7CBD00637FCE6E6C2BBA9D53E4472D (1639)
  B 87920C9EAA5117F947D44650F96D5C3A7349E58350F306FF8D52D73622CE9B9E (4676)

poi/r.0.1.mca
  A 7E8B0659111BFEF614B272BFD37DA825CC2C2867643CEC151D4F3B1E4C4108A6 (12288)
  B 31001C78D970C881631C77A523FB1D5B67A0FB8D9FAB3275DC790761BCBA1F10 (16384)

poi/r.1.0.mca
  A 7972E7C9AAE68EB6125C292287323420FEDF7D73048908A4D9A45C6BD4E33EAE (28672)
  B D7D64AD21E28DAABFA95EE3210739F11F34D5AAA6075D246F494FBE0F7A13042 (32768)

poi/r.1.1.mca
  A 0A0DD9FB504069C9288B72D3677203CC6332ED057314CF126666C4BED3F7EDCD (77824)
  B 4C3CE3300B5124A7E2B78AB26E23ABA3544E119AA57370BAC8B7F7E146AA798B (81920)

region/r.-1.-1.mca
  A DD4F8BBD4B935B35B83BD6602BBA3DA1F192717683E2C2499EE35E1AB6B5532F (532480)
  B E791927FB62FDC8B193E4289BFCF907CFAE7E53B6C42EDCEB170A8667F7FEF94 (2650112)

region/r.-1.0.mca
  A 141C110B5951838E4EA2B9432DE3C6CFBEC6DFC55359907FC7DC0751152AD927 (434176)
  B 1DCE3B80A05E193132B08AD803C8E264E4C6C0563DBB0CDFE5274F70B849C663 (3887104)

region/r.0.-1.mca
  A 2ECF2679F7A1915045D4AA53FF1A2CEE1146AA2A4BD57D1A991FEFD9C23DAB39 (2002944)
  B C55413E6DC5A2B9ABF7360AD3AD56360D7DD26B2903703E4500D35E0D23FA2BF (6279168)

region/r.0.0.mca
  A 913ABF6BFBFD2A4AE0D971F04E678F61E242DC4D9D0FD966F73869B232E57FB4 (2686976)
  B C8277B318CBF09826F949E77527C69EEF4E96B37C8BBB411061BD9C3C6CF85BC (10543104)

region/r.0.1.mca
  A D8DD57BC89BF03FD1E61F5DBCB10B8B688535D21AE7B40C62E0E32F2B6BD4C94 (1310720)
  B FC0F6E2295F3BC036D5A13CF062B2C5F378658DF7F8B125E49675D0EBE8E40D8 (7139328)

region/r.1.0.mca
  A 54E33A09F21B9528918A1E553FD282CE9625FB35B7651D54C4CADBCED643DB33 (4018176)
  B DD5BE6F01EF2A0EF35C8FE039EE29B08A59210D7B7CF82EF9057D76E6D27975F (6311936)

region/r.1.1.mca
  A 490DD7609906CB75FE435BE21DF70673AA58D4887D36E83DF94F17B89B467271 (4485120)
  B 8AEE524E5725937706B137E18A6D3D5D04DF9C0C1AD9DA248FF276FE05BFAC8B (7393280)
```

### B-only files (19)

```text
advancements/380df991-f603-344c-a090-369bad2a924a.json
entities/r.-1.-1.mca
entities/r.-1.0.mca
entities/r.-1.1.mca
icon.png
playerdata/380df991-f603-344c-a090-369bad2a924a.dat
playerdata/380df991-f603-344c-a090-369bad2a924a.dat_old
poi/r.-1.-1.mca
poi/r.-1.0.mca
poi/r.0.-1.mca
poi/r.0.0.mca
poi/r.2.0.mca
poi/r.2.1.mca
region/r.-1.1.mca
region/r.1.-1.mca
region/r.2.0.mca
region/r.2.1.mca
session.lock
stats/380df991-f603-344c-a090-369bad2a924a.json
```

### Identical common files (6)

```text
DIM-1/data/neoforge_data_attachments.dat
DIM1/data/neoforge_data_attachments.dat
README-DIAGNOSTICO.txt
data/chunks.dat
data/neoforge_data_attachments.dat
serverconfig/readme.txt
```

No file exists only in A.

## 4. level.dat metadata

```text
A current:  Time=600,  DayTime=600,  LastPlayed=1794772524684,
            no Player compound, LevelName=DevilRpg-A2-IDX8-Diagnostico
A old:      Time=599,  LevelName=a2n, LastPlayed=1794772522827
B current:  Time=6104, DayTime=6104, LastPlayed=1795125952597,
            Player compound present
B old:      Time=6103, LastPlayed=1795125939112, Player compound present
```

`LastPlayed` conversion:

```text
A: 2026-11-15T19:55:24Z
B: 2026-11-19T22:05:52Z
```

The B world advanced 5,504 ticks and was saved with a player. `level.dat_old` is a near-current backup of
the same B session (time 6103), not the pristine A state.

## 5. Provenance — conclusive client session

`run/logs/latest.log`:

```text
19nov2026 15:58:16  ModLauncher starting forgeclientdev
19nov2026 15:58:33  Setting user: Dev
19nov2026 15:59:34  Starting integrated minecraft server
19nov2026 15:59:39  Dev logged in
19nov2026 16:00:21  Saving chunks for DevilRpg-A2-IDX8-Diagnostico
19nov2026 16:01:17  A2 IDX1 ... died: slain by Filomena (Guardia espadachín)
19nov2026 16:05:52  All chunks saved
19nov2026 16:09:16  Stopping!
```

This is a manual client session, not an A2 Harness run (A2 harness uses `build/a2n/<run>`). It explains
the larger level.dat, playerdata/advancements/stats, new region files, expanded existing regions, and the
new icon/session.lock.

Historical provenance: `2026-11-15-A2-IDX8-C4-FORENSICS.md` records the A2 diagnostic world source
`build/a2n/a2n-20261115-031530/game/a2n` (tick 600, pre-bell) and lists both copies at `BA00A1C4…`.
The APPDATA copy still matches that reference; the run/saves copy was later played.

## 6. Classification

- **APPDATA A:** pristine historical reference; `BA00A1C4…`; must remain untouched.
- **run/saves B:** manually played derivative; `F4EEAA13…`; not original, not a migration artifact.
- **Disposable?** Not declared by Harness. It is safe to say it is **not the pristine reference**; only the
  owner can authorize deleting/replacing it. Preserve it as evidence until then.

## 7. Recommendation

1. Treat APPDATA A as canonical.
2. Do not use either protected world for the next A2 run; use a new disposable world.
3. Preserve both copies unchanged until the owner decides whether B is disposable.
4. Update the integrity reference only after an explicit owner decision; do not silently re-baseline B.

A2 remains OPEN. No runtime was executed.
