# A2 Edit 5 — Read-only source and launcher audit

**Scope: read-only inspection only. No harness, launcher, source, or world changes were made in this audit.**

## Current source identity

- Canonical source: `tools/a2n/A2NavHarness.java`
- SHA-256: `11980C98668EF5D13E4CF3A276C9782392A9187F51B3EF63D7FC88141FA4C940`
- Protected partial SHA-256:
  `2C3EBA9ED6E936B595E26650B796A624838B5CB4FF6CFD7B1804BE7A13552127`
- Neither canonical source nor partial was edited.
- Temporary launcher: `build/a2n/lanzar-a2n.ps1` (ignored build artifact).

## What is already present in the canonical harness

The current canonical source contains a substantial portion of the old Edit 5
work; the handoff's blanket statement that these outputs are absent is stale:

- Raid phase branch calls `informeRaid("A")` at `R_FASE_A` and
  `informeRaid("B")` at `R_FASE_B` (source around lines 70–103).
- `integridadAnillo()` compares the wall against the registered base
  `rBaseValidada`, rather than simply counting arbitrary non-air blocks
  (around lines 1337–1400).
- Wall construction records the per-column base and validates the built ring
  before setting `rFixtureOk` (around lines 1980–2095).
- `informeRaid()` emits `TOTALES A/B`, `PARED total`, `CRITERIOS A/B`,
  `VEREDICTO A/B`, `BAJAS DE COMBATE`, `RETIRADA`, and per-attacker
  `RAID RESULTADO idx=...` lines (around lines 1533–1585).
- `cerrar()` writes `fin.txt` and `estado.txt` under
  `build/a2n/<runId>/` (around lines 2344–2362).

The comments at the raid dispatch and Edit 4 section still say that totals,
verdicts, and wall integrity are absent. Those comments contradict the code and
need reconciliation if the owner authorizes a harness edit.

## Why Edit 5 / C4 is not yet accepted

The existing output is instrumentation, not a contracted verdict:

1. `VEREDICTO A/B` explicitly says
   `SIN UMBRAL CONTRATADO (informativo; no se declara exito ni fracaso)`.
   The code deliberately does not decide whether the measured result passes.
2. The final `cerrar()` call writes `INSTRUMENTADO` when
   `fallosDeInstrumento == 0`; that means the measurement ran, not that the
   defense scenario passed.
3. `lanzar-a2n.ps1` waits for `fin.txt` and prints occurrence counts for
   selected log markers. It does not parse their values, assert 8/8 spawns,
   evaluate wall-integrity fields, or convert the evidence into an acceptance
   verdict.
4. The documented C4 contract also requires guard engagement and villagers
   resuming professions without orphaned assignments. The current
   `A2NavHarness.java` contains no direct instrumentation references for
   `VillagerGuardGoal`, guard engagement, villager GoalSelector recovery,
   profession resumption, or assignment reacquisition. Those outcomes cannot
   be claimed from the current attacker/wall measurements alone.
5. `build/a2n/_raid_e5_block.txt` is an older draft. Its
   `integridadAnillo()` simply counts non-air wall blocks at a fixed Y and does
   not preserve the per-column validated baseline now present in the canonical
   source. **Do not apply that draft over the current source.**

## Launcher behavior verified read-only

- `-MundoPreparado` accepts a source directory containing `level.dat` and
  copies it to the new run's `game/a2n` directory; it refuses an existing
  destination or overlapping source/destination paths.
- Each invocation creates a unique `build/a2n/a2n-<timestamp>/game` world.
- It starts dedicated `runServer` with `-Pa2nDiagnostics=true`, waits for
  `fin.txt`, reports marker occurrence counts, waits for chunk saves, and
  terminates only the server JVM it identified as belonging to that run.
- The launcher does not implement semantic assertions for the marker values.

This gives a safe future route to run against a *copy* of the repaired disposable
world, without reusing or modifying the repair evidence world itself. That route
has not been invoked.

## Required owner decisions before proceeding

A defensible C4 completion needs a reviewed result contract, not a fabricated
threshold. Before editing the protected canonical source, the owner must approve:

1. The exact measurable PASS/FAIL conditions for raid setup, wall integrity,
   attacker outcomes, guard response, and post-raid profession/assignment recovery.
2. Adding the missing guard/recovery observations and semantic result output to
   the canonical harness.
3. One bounded full A2 raid run in a fresh copy of the repaired disposable world.

Until then: no harness edits, no A2 raid launch, no use of fallback `629`,
historical worlds, or `run/saves`. C4 remains OPEN/NOT ACCEPTED.


## Harness scope revalidation after owner authorization — 2026-11-12

**Owner authorized editing only the canonical `tools/a2n/A2NavHarness.java` to
complete C4 instrumentation/verdict. The authorization was honored as a
permission boundary; no source edit was made because the current raid fixture
cannot observe the required production actors.**

### Verified fixture behavior

- `prepararRaidFixture()` (canonical source around lines 1770–2106) sets the
  fixed center `661,71,533`, forces chunks, validates/builds the ring and its
  foundation, and sets `rFixtureOk`.
- `prepararRaidAsaltantes()` (around lines 1127–1295) creates and registers
  exactly the eight `AggressiveZombieEntity` attackers. It does not create
  villagers, install profession Goals, enlist guards, or prepare a completed
  settlement.
- The raid-mode tick then samples those attackers and the artificial wall.
  There are no `Villager`, `VillagerGuardGoal`, `GoalSelector`, profession,
  or assignment-reacquisition references in the canonical harness.
- Production `VillagerGuardGoal` does expose read-only observations such as
  `esGuardia(Villager)` and `enemigoActual()`, but the current fixture
  supplies no actual guard instance to observe. A zero-guard scan would
  therefore be a fixture absence, not evidence that the production guard AI
  idles.
- A generic scan of running GoalSelector entries would not prove
  assignment-ownership cleanup. Goal-local assignments/controllers are
  heterogeneous and transient; “some profession Goal is running” is not
  equivalent to “no orphaned assignment”.

### Decision — do not make a misleading harness-only edit

Adding counters or a PASS/FAIL label against this fixture would fabricate
coverage for guard engagement and profession/assignment recovery. No
acceptance thresholds should be invented, and no absent villager population
should be interpreted as a production failure. Therefore the canonical source
remains byte-for-byte unchanged for this step; the protected `.partial` also
remains untouched.

### Next required design step

Before the authorized canonical-harness edit can be implemented honestly,
design a bounded C4 fixture that contains a real generated/initialized village,
its actual enlisted/equipped guards, and at least the real profession workers
whose post-raid assignments must be reacquired. Prefer invoking existing
production village-generation/registration paths over manually fabricating
persistent tags or duplicating Goal setup in the harness. Then define and log
the observable guard-engagement, deterministic village outcome, profession
resumption, and assignment ownership/reacquisition evidence. If those
preconditions cannot be created solely inside the canonical harness, request
additional narrowly scoped authorization before touching any other source.

**No raid was launched.** No world, launcher, `.partial`, production source,
or protected harness file was modified. C4 remains OPEN / NOT ACCEPTED; the
foundation repair remains PASS only within its disposable-world scope.


### Production fixture path found during the same audit

The first scope finding is still correct for the current harness, but a viable
production fixture entry point exists and was identified before ending this
session:

- `VillageManager.preGenerate(ServerLevel, objectiveIndex, target)` is public
  (source around lines 971–1008). It invokes `VillageGenerator.generate`,
  delivers the initial warehouse shipment, creates the four founding guards via
  `VillageGenerator.spawnGuardiasFundadores`, marks the village generated, and
  persists its authoritative center/blueprint.
- `VillageGenerator.generate` calls `spawnVillagers` (around line 405), which
  spawns the 12 configured profession villagers and an iron golem. The
  profession roster is the production `VILLAGER_SPECIALTIES` array (around
  lines 7923–7928), not a harness-authored substitute.
- `spawnGuardiasFundadores` creates four distinct founder guards, registers
  their persistent village/slot/type identity, installs their real guard Goals
  through `VillageManager.asegurarGuardiaFundadora`, and calls
  `VillagerGuardGoal.equiparDesdeAlmacen` (around lines 8051–8114).
- Profession Goal installation is performed by the private
  `VillageManager.tickVillageLife` maintenance path (around lines 2916 onward).
  The public `manageNearby` path invokes that maintenance, but it takes a real
  `ServerPlayer`; this dependency must be mapped before designing a headless
  harness call. Do not bypass it by manually forging villager persistent tags
  or claiming generic running Goals prove assignment recovery.
- The existing raid fixture also constructs its own artificial ring and writes
  the terrain at the same radius. A future integration must avoid overwriting
  or damaging the generated village structures/wall; either adopt the
  production-generated wall as the measured baseline or isolate the test layout
  carefully. Preserve the repaired disposable-world copy.

This is a design lead, not an implemented or runtime-verified fixture. Next
static task: map a safe headless invocation of the normal village maintenance
path (including its `ServerPlayer` dependency) and reconcile it with the
harness ring builder. Keep the source-edit authorization limited to the
canonical harness; request any additional scope explicitly if required.
