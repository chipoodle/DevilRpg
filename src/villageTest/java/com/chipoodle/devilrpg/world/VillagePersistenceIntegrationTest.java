package com.chipoodle.devilrpg.world;

import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.Difficulty;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelTickAccess;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

/**
 * Incremento 3 · B · <b>serialización y restauración REALES</b> de los datos de aldea.
 * <p>
 * No hay dobles de {@code CompoundTag}: se usa el NBT real de Minecraft, los codecs reales
 * ({@link NbtUtils#writeBlockState}, {@code VillagerData.CODEC}, el codec de {@code Brain}) y el
 * fichero comprimido real ({@link NbtIo#writeCompressed}/{@link NbtIo#readCompressed}).
 * <p>
 * Los casos de identidad/JOB_SITE construyen y restauran un {@link Villager} de verdad. Para eso hace
 * falta un {@link Level} mínimo (subclase {@link StubLevel}) y completar tres registros que en un
 * arranque normal rellena NeoForge: {@code neoforge:fluid_type} y los atributos
 * {@code neoforge:swim_speed} / {@code neoforge:name_tag_distance} / {@code neoforge:creative_flight}.
 * Sin ellos, el constructor de {@code Entity}/{@code LivingEntity} lanza exactamente
 * {@code IllegalStateException: Registry not present for DeferredHolder{...fluid_type...}} y
 * {@code NullPointerException: Trying to access unbound value: ...attribute/neoforge:swim_speed}.
 * Es un {@code Level} de prueba (sin chunks ni {@code PoiManager}), no un {@code ServerLevel}: se prueba
 * de verdad el NBT y los codecs de la entidad, NO el ticket de POI ni la reasignación en vivo.
 * <p>
 * Programa {@code main}: exit 1 si falla. Imprime el número de aserciones al final.
 */
public final class VillagePersistenceIntegrationTest {

    private static int total;
    private static int fallos;

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CAROL = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final BlockPos FARM_CENTER = new BlockPos(0, 64, 0);
    private static final BlockPos VILLAGE_CENTER = new BlockPos(100, 70, -200);

    public static void main(String[] args) {
        net.neoforged.fml.loading.LoadingModList.of(
                List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        RegistryAccess.Frozen registries = buildRegistries();
        Level level = new StubLevel(registries);

        try {
            emptyNbtDefaults(registries);
            fullRoundTrip(registries);
            twoCycles(registries);
            persistedFile(registries);
            founderMask(registries);
            partialLoads(registries);
            corruptData(registries);
            reconciliation(registries);
            identityAndJobSite(level);
            rosterInvariants(level);
        } catch (Throwable t) {
            fallos++;
            System.out.println("  EXCEPCION NO CONTROLADA: " + t);
            t.printStackTrace(System.out);
        }

        if (fallos > 0) {
            System.out.println("VILLAGE_PERSISTENCE_INTEGRATION_TEST: FAIL (" + fallos + " de " + total + ")");
            System.exit(1);
        }
        System.out.println("VILLAGE_PERSISTENCE_INTEGRATION_TEST: OK (" + total + " assertions)");
    }

    // ---------------------------------------------------------------------------------------------
    // 1 · NBT vacío -> defaults, y save/load estable
    // ---------------------------------------------------------------------------------------------

    private static void emptyNbtDefaults(RegistryAccess.Frozen registries) {
        System.out.println("[1] NBT vacio -> defaults");
        VillageSavedData data = VillageSavedData.load(new CompoundTag(), registries);

        check(!data.isGenerated(0), "vacio: no generada");
        check(!data.isSiegeResolved(0), "vacio: no resuelta");
        check(!data.isOccupied(0), "vacio: no ocupada");
        check(!data.isFallen(0), "vacio: no caida");
        check(!data.isNoticed(0, ALICE), "vacio: no avisada");
        check(data.getPressureTicks(0) == 0, "vacio: presion 0");
        check(data.getHealth(0) == VillageSavedData.HEALTH_UNKNOWN, "vacio: salud desconocida");
        check(data.getRepopulatedAt(0) == 0L, "vacio: repoblacion 0");
        check(data.getStarvingSince(0) == 0L, "vacio: hambre 0");
        check(data.getLayout(0) == 0, "vacio: layout 0");
        check(data.getCasasVersion(0) == 0, "vacio: casas 0");
        check(data.getAnexoAnimales(0) == 0L, "vacio: anexo 0");
        check(data.getFood(0) == 0, "vacio: comida 0");
        check(data.getPantryCredit(0) == 0, "vacio: credito 0");
        check(data.getCentre(0) == null, "vacio: centro null");
        check(!data.hasCentre(0), "vacio: sin centro");
        check(!data.hasBlueprint(0), "vacio: sin plano");
        check(data.getBlueprint(0) == null, "vacio: getBlueprint null");
        check(!data.isRemesaInicialEntregada(0), "vacio: remesa no entregada");
        check(!data.isGuardiaFundadoraCreada(0, 0), "vacio: slot fundador 0 libre");
        for (int slot = 0; slot < 4; slot++) {
            check(!data.isGuardiaFundadoraCreada(0, slot), "vacio: slot fundador " + slot + " libre");
        }
        check(data.getFarmOwner(FARM_CENTER, 0) == null, "vacio: parcela libre");

        // El guardado de un estado vacío debe ser un NBT válido y volver a cargarse igual.
        CompoundTag emptyTag = new CompoundTag();
        CompoundTag saved = data.save(emptyTag, registries);
        check(saved.contains("Generated"), "vacio: save escribe Generated");
        check(saved.contains("Blueprints"), "vacio: save escribe Blueprints");
        VillageSavedData reloaded = VillageSavedData.load(saved, registries);
        check(!reloaded.isGenerated(0), "vacio: roundtrip no genera");
        check(reloaded.getHealth(0) == VillageSavedData.HEALTH_UNKNOWN, "vacio: roundtrip salud");
        check(reloaded.getFood(0) == 0, "vacio: roundtrip comida");
        check(!reloaded.hasBlueprint(0), "vacio: roundtrip sin plano");

        CompoundTag saved2 = reloaded.save(new CompoundTag(), registries);
        check(normalize(saved).equals(normalize(saved2)), "vacio: save/load/save estable");
    }

    // ---------------------------------------------------------------------------------------------
    // 2 · Roundtrip de estado completo
    // ---------------------------------------------------------------------------------------------

    private static VillageSavedData sampleState(RegistryAccess registries) {
        VillageSavedData data = VillageSavedData.load(new CompoundTag(), registries);

        data.markGenerated(0);
        data.markGenerated(3);
        data.markSiegeResolved(1);
        data.markOccupied(2);
        data.markNoticed(1, ALICE);
        data.markNoticed(1, BOB);
        data.markFallen(4); // tambien marca resolved

        // Presion: la primera consulta fija el origen; la segunda acumula ticks.
        data.accruePressure(0, 1000L);
        data.accruePressure(0, 1500L);          // 500 ticks
        data.accruePressure(1, 2000L);
        data.accruePressure(1, 2100L, 2.0D);    // 100 * 2 = 200 ticks

        // Parcelas: ALICE en la 0; BOB reclama la 1 y ALICE intenta liberarla (no debe poder).
        data.claimFarmPlot(FARM_CENTER, 0, ALICE);
        data.claimFarmPlot(FARM_CENTER, 1, BOB);
        data.releaseFarmPlot(FARM_CENTER, 1, ALICE);
        data.claimFarmPlot(FARM_CENTER, 0, ALICE); // idempotente

        data.setHealth(5, 7);
        data.markRepopulated(5, 12345L);
        data.setStarvingSince(5, 222L);
        data.setLayout(5, 7);
        data.setCasasVersion(5, 2);
        data.setAnexoAnimales(5, 444L);
        data.markGuardiaFundadoraCreada(5, 0);
        data.markGuardiaFundadoraCreada(5, 1);
        data.markGuardiaFundadoraCreada(5, 2);
        data.markGuardiaFundadoraCreada(5, 3);
        data.markRemesaInicialEntregada(5);
        data.setCentre(5, VILLAGE_CENTER);
        data.setFood(5, 9);
        data.setPantryCredit(5, 4);
        data.setHealth(6, 0); // aldea mirada y vacia: salud 0 != desconocida

        List<BlockState> palette = List.of(
                Blocks.OAK_PLANKS.defaultBlockState(),
                Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST),
                Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, 3));
        data.setBlueprint(0, new VillageSavedData.Blueprint(
                palette,
                new long[]{new BlockPos(1, 64, 1).asLong(), new BlockPos(2, 64, 1).asLong(),
                        new BlockPos(3, 64, 1).asLong()},
                new int[]{0, 1, 2}));
        data.setBlueprint(7, new VillageSavedData.Blueprint(
                List.of(Blocks.OAK_PLANKS.defaultBlockState()),
                new long[]{new BlockPos(-1234567, -60, 7654321).asLong()},
                new int[]{0}));
        return data;
    }

    private static void fullRoundTrip(RegistryAccess.Frozen registries) {
        System.out.println("[2] Roundtrip de estado completo");
        VillageSavedData original = sampleState(registries);
        CompoundTag saved = original.save(new CompoundTag(), registries);
        VillageSavedData loaded = VillageSavedData.load(saved, registries);
        assertFullState(loaded, "roundtrip");
    }

    private static void assertFullState(VillageSavedData d, String phase) {
        check(d.isGenerated(0) && d.isGenerated(3), phase + ": generated {0,3}");
        check(!d.isGenerated(1) && !d.isGenerated(2), phase + ": generated no contamina");
        check(d.isSiegeResolved(1), phase + ": resolved 1");
        check(d.isSiegeResolved(4), phase + ": fallen implica resolved 4");
        check(d.isOccupied(2), phase + ": occupied 2");
        check(d.isFallen(4), phase + ": fallen 4");
        check(!d.isFallen(0), phase + ": fallen no contamina");
        check(d.isNoticed(1, ALICE) && d.isNoticed(1, BOB), phase + ": noticed por jugador");
        check(!d.isNoticed(1, CAROL), phase + ": noticed no contamina");

        check(d.getPressureTicks(0) == 500, phase + ": presion 0 = 500");
        check(d.getPressureTicks(1) == 200, phase + ": presion 1 = 200 (multiplicador)");
        check(d.getPressureTicks(2) == 0, phase + ": presion 2 = 0");

        check(ALICE.equals(d.getFarmOwner(FARM_CENTER, 0)), phase + ": parcela 0 de ALICE");
        check(BOB.equals(d.getFarmOwner(FARM_CENTER, 1)), phase + ": liberar con otro duenio no borra");
        check(d.getFarmOwner(FARM_CENTER, 2) == null, phase + ": parcela 2 libre");

        check(d.getHealth(5) == 7, phase + ": salud 5");
        check(d.getRepopulatedAt(5) == 12345L, phase + ": repoblada 5");
        check(d.getStarvingSince(5) == 222L, phase + ": hambre 5");
        check(d.getLayout(5) == 7, phase + ": layout 5");
        check(d.getCasasVersion(5) == 2, phase + ": casas 5");
        check(d.getAnexoAnimales(5) == 444L, phase + ": anexo 5");
        check(d.isRemesaInicialEntregada(5), phase + ": remesa 5");
        check(VILLAGE_CENTER.equals(d.getCentre(5)), phase + ": centro 5");
        check(d.getFood(5) == 9, phase + ": comida 5");
        check(d.getPantryCredit(5) == 4, phase + ": credito 5");
        check(d.getHealth(6) == 0, phase + ": salud 0 no es desconocida");
        check(d.getHealth(7) == VillageSavedData.HEALTH_UNKNOWN, phase + ": aldea no mirada = desconocida");

        for (int slot = 0; slot < 4; slot++) {
            check(d.isGuardiaFundadoraCreada(5, slot), phase + ": fundador 5 slot " + slot);
        }
        for (int slot = -2; slot < 6; slot++) {
            if (slot >= 0 && slot < 4) {
                continue;
            }
            check(!d.isGuardiaFundadoraCreada(5, slot), phase + ": fundador fuera de rango " + slot);
        }

        VillageSavedData.Blueprint plano = d.getBlueprint(0);
        check(plano != null, phase + ": plano 0 presente");
        if (plano != null) {
            check(plano.size() == 3, phase + ": plano 0 con 3 celdas");
            check(plano.palette().size() == 3, phase + ": paleta 0 con 3 estados");
            check(new BlockPos(1, 64, 1).equals(plano.posAt(0)), phase + ": pos 0");
            check(new BlockPos(3, 64, 1).equals(plano.posAt(2)), phase + ": pos 2");
            check(plano.stateAt(0).is(Blocks.OAK_PLANKS), phase + ": estado 0 tablones");
            check(plano.stateAt(1).is(Blocks.OAK_STAIRS), phase + ": estado 1 escalera");
            check(plano.stateAt(1).getValue(StairBlock.FACING) == Direction.EAST,
                    phase + ": escalera conserva facing=east");
            check(plano.stateAt(2).is(Blocks.WHEAT), phase + ": estado 2 trigo");
            check(plano.stateAt(2).getValue(BlockStateProperties.AGE_7) == 3,
                    phase + ": trigo conserva age=3");
        }
        VillageSavedData.Blueprint plano7 = d.getBlueprint(7);
        check(plano7 != null && plano7.size() == 1, phase + ": plano 7 presente");
        if (plano7 != null) {
            check(new BlockPos(-1234567, -60, 7654321).equals(plano7.posAt(0)),
                    phase + ": BlockPos.asLong conserva coordenadas negativas");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 3 · Dos ciclos save/load: igualdad normalizada + campo a campo
    // ---------------------------------------------------------------------------------------------

    private static void twoCycles(RegistryAccess.Frozen registries) {
        System.out.println("[3] Dos ciclos save/load");
        VillageSavedData d0 = sampleState(registries);
        CompoundTag s1 = d0.save(new CompoundTag(), registries);
        VillageSavedData d1 = VillageSavedData.load(s1, registries);
        CompoundTag s2 = d1.save(new CompoundTag(), registries);
        VillageSavedData d2 = VillageSavedData.load(s2, registries);
        CompoundTag s3 = d2.save(new CompoundTag(), registries);

        check(normalize(s1).equals(normalize(s2)), "dos ciclos: save1 == save2 (normalizado)");
        check(normalize(s2).equals(normalize(s3)), "dos ciclos: save2 == save3 (normalizado)");
        assertFullState(d1, "ciclo 1");
        assertFullState(d2, "ciclo 2");

        // UUID exacto, no solo igualdad logica: el texto persistido debe ser identico.
        String ownerPath = "FarmOwners";
        ListTag owners1 = s1.getList(ownerPath, Tag.TAG_COMPOUND);
        ListTag owners3 = s3.getList(ownerPath, Tag.TAG_COMPOUND);
        check(owners1.size() == 2, "dos ciclos: dos duenios persistidos");
        check(owners1.size() == owners3.size(), "dos ciclos: no se duplican duenios");
        for (Tag t : owners1) {
            CompoundTag entry = (CompoundTag) t;
            if (entry.getString("Key").endsWith("#0")) {
                check(ALICE.toString().equals(entry.getString("Owner")), "dos ciclos: UUID exacto ALICE");
            }
            if (entry.getString("Key").endsWith("#1")) {
                check(BOB.toString().equals(entry.getString("Owner")), "dos ciclos: UUID exacto BOB");
            }
        }
        check(ALICE.equals(d2.getFarmOwner(FARM_CENTER, 0)), "dos ciclos: ALICE sigue siendo duenia");
        check(ALICE.toString().equals(d2.getFarmOwner(FARM_CENTER, 0).toString()),
                "dos ciclos: toString exacto del UUID");
    }

    // ---------------------------------------------------------------------------------------------
    // 4 · Fichero real comprimido (NbtIo), truncado y basura
    // ---------------------------------------------------------------------------------------------

    private static void persistedFile(RegistryAccess.Frozen registries) throws IOException {
        System.out.println("[4] Fichero NBT real (writeCompressed/readCompressed)");
        Path dir = Path.of("build", "av", "village-persistence");
        Files.createDirectories(dir);
        Path file = dir.resolve("village-saved-data.nbt");

        CompoundTag saved = sampleState(registries).save(new CompoundTag(), registries);
        NbtIo.writeCompressed(saved, file);
        check(Files.exists(file) && Files.size(file) > 0, "fichero: se escribio y no esta vacio");

        CompoundTag fromDisk = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        check(normalize(saved).equals(normalize(fromDisk)), "fichero: NBT releido identico (normalizado)");
        VillageSavedData loaded = VillageSavedData.load(fromDisk, registries);
        assertFullState(loaded, "fichero");
        check(ALICE.equals(loaded.getFarmOwner(FARM_CENTER, 0)), "fichero: UUID de duenio exacto");

        byte[] bytes = Files.readAllBytes(file);
        Path truncated = dir.resolve("village-truncated.nbt");
        Files.write(truncated, Arrays.copyOf(bytes, Math.max(1, bytes.length / 2)));
        Throwable truncatedError = null;
        try {
            NbtIo.readCompressed(truncated, NbtAccounter.unlimitedHeap());
        } catch (Throwable t) {
            truncatedError = t;
        }
        check(truncatedError instanceof IOException
                        || truncatedError instanceof net.minecraft.nbt.ReportedNbtException,
                "fichero truncado: readCompressed no devuelve datos a medias (fue " + truncatedError + ")");

        Path garbage = dir.resolve("village-garbage.nbt");
        Files.write(garbage, new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07});
        Throwable garbageError = null;
        try {
            NbtIo.readCompressed(garbage, NbtAccounter.unlimitedHeap());
        } catch (Throwable t) {
            garbageError = t;
        }
        check(garbageError instanceof IOException,
                "fichero basura: readCompressed lanza IOException (fue " + garbageError + ")");

        // Mecanismo real de recuperacion: si el fichero no se puede leer, el llamador arranca de cero.
        VillageSavedData fallback = VillageSavedData.load(new CompoundTag(), registries);
        check(fallback.getHealth(5) == VillageSavedData.HEALTH_UNKNOWN,
                "fichero corrupto: el fallback vacio no inventa salud");
        check(!fallback.hasBlueprint(0), "fichero corrupto: el fallback vacio no inventa planos");
    }

    // ---------------------------------------------------------------------------------------------
    // 5 · Mascara de guardias fundadoras (4 bits)
    // ---------------------------------------------------------------------------------------------

    private static void founderMask(RegistryAccess.Frozen registries) {
        System.out.println("[5] Mascara de fundadores");
        for (int slot = 0; slot < 4; slot++) {
            VillageSavedData one = VillageSavedData.load(new CompoundTag(), registries);
            one.markGuardiaFundadoraCreada(5, slot);
            CompoundTag tag = one.save(new CompoundTag(), registries);
            VillageSavedData back = VillageSavedData.load(tag, registries);
            for (int other = 0; other < 4; other++) {
                check(back.isGuardiaFundadoraCreada(5, other) == (other == slot),
                        "mascara: solo el slot " + slot + " (comprobado " + other + ")");
            }
            CompoundTag second = back.save(new CompoundTag(), registries);
            VillageSavedData back2 = VillageSavedData.load(second, registries);
            check(back2.isGuardiaFundadoraCreada(5, slot), "mascara: slot " + slot + " tras dos ciclos");
        }

        VillageSavedData partial = VillageSavedData.load(new CompoundTag(), registries);
        partial.markGuardiaFundadoraCreada(5, 1);
        partial.markGuardiaFundadoraCreada(5, 3);
        CompoundTag partialTag = partial.save(new CompoundTag(), registries);
        VillageSavedData partialBack = VillageSavedData.load(partialTag, registries);
        check(partialBack.isGuardiaFundadoraCreada(5, 1) && partialBack.isGuardiaFundadoraCreada(5, 3),
                "mascara: parcial 0b1010 conserva sus bits");
        check(!partialBack.isGuardiaFundadoraCreada(5, 0) && !partialBack.isGuardiaFundadoraCreada(5, 2),
                "mascara: parcial 0b1010 no inventa bits");

        int before = settledMask(partialTag, 5);
        check(before == 0b1010, "mascara: valor persistido 0b1010 (fue " + before + ")");
        partial.markGuardiaFundadoraCreada(5, 4);
        partial.markGuardiaFundadoraCreada(5, -1);
        check(settledMask(partial.save(new CompoundTag(), registries), 5) == 0b1010,
                "mascara: slot 4 y -1 se ignoran");

        // Bits sucios fuera de rango en el NBT: no deben marcar ningun slot ni romper la carga.
        CompoundTag dirty = new CompoundTag();
        ListTag settlement = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putInt("Index", 9);
        entry.putInt("GuardiasFundadoras", 0b10000);
        settlement.add(entry);
        dirty.put("Settlement", settlement);
        VillageSavedData dirtyData = VillageSavedData.load(dirty, registries);
        for (int slot = 0; slot < 4; slot++) {
            check(!dirtyData.isGuardiaFundadoraCreada(9, slot), "mascara: bit sucio no marca slot " + slot);
        }
        dirtyData.markGuardiaFundadoraCreada(9, 0);
        CompoundTag dirtySaved = dirtyData.save(new CompoundTag(), registries);
        check(settledMask(dirtySaved, 9) == 0b10001, "mascara: bit sucio se conserva al marcar (0b10001)");
        VillageSavedData dirtyBack = VillageSavedData.load(dirtySaved, registries);
        check(dirtyBack.isGuardiaFundadoraCreada(9, 0), "mascara: bit sucio no impide marcar el slot 0");
        check(!dirtyBack.isGuardiaFundadoraCreada(9, 1), "mascara: bit sucio no contamina el slot 1");

        CompoundTag full = new CompoundTag();
        ListTag fullList = new ListTag();
        CompoundTag fullEntry = new CompoundTag();
        fullEntry.putInt("Index", 2);
        fullEntry.putInt("GuardiasFundadoras", 0b1111);
        fullList.add(fullEntry);
        full.put("Settlement", fullList);
        VillageSavedData fullData = VillageSavedData.load(full, registries);
        for (int slot = 0; slot < 4; slot++) {
            check(fullData.isGuardiaFundadoraCreada(2, slot), "mascara: 0b1111 slot " + slot);
        }
    }

    private static int settledMask(CompoundTag tag, int index) {
        for (Tag element : tag.getList("Settlement", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            if (entry.getInt("Index") == index) {
                return entry.getInt("GuardiasFundadoras");
            }
        }
        return 0;
    }

    // ---------------------------------------------------------------------------------------------
    // 6 · Carga parcial: falta cada seccion top-level por turno
    // ---------------------------------------------------------------------------------------------

    private static void partialLoads(RegistryAccess.Frozen registries) {
        System.out.println("[6] Carga parcial (falta una seccion top-level)");
        CompoundTag complete = sampleState(registries).save(new CompoundTag(), registries);
        String[] keys = {"Generated", "Resolved", "Occupied", "Noticed", "Fallen",
                "Pressure", "FarmOwners", "Settlement", "Blueprints"};

        for (String key : keys) {
            CompoundTag partial = complete.copy();
            partial.remove(key);
            VillageSavedData data = null;
            Throwable error = null;
            try {
                data = VillageSavedData.load(partial, registries);
            } catch (Throwable t) {
                error = t;
            }
            check(error == null, "parcial sin " + key + ": load no lanza (" + error + ")");
            if (data == null) {
                continue;
            }
            if ("Generated".equals(key)) {
                check(!data.isGenerated(0) && !data.isGenerated(3), "parcial sin Generated: defaults");
            } else {
                check(data.isGenerated(0) && data.isGenerated(3), "parcial sin " + key + ": conserva Generated");
            }
            if ("Resolved".equals(key)) {
                check(!data.isSiegeResolved(1), "parcial sin Resolved: 1 no resuelta");
                check(data.isSiegeResolved(4), "parcial sin Resolved: Fallen(4) sigue resolviendo");
            } else {
                check(data.isSiegeResolved(1) && data.isSiegeResolved(4), "parcial sin " + key + ": conserva Resolved");
            }
            if ("Occupied".equals(key)) {
                check(!data.isOccupied(2), "parcial sin Occupied: default");
            } else {
                check(data.isOccupied(2), "parcial sin " + key + ": conserva Occupied");
            }
            if ("Noticed".equals(key)) {
                check(!data.isNoticed(1, ALICE), "parcial sin Noticed: default");
            } else {
                check(data.isNoticed(1, ALICE) && data.isNoticed(1, BOB), "parcial sin " + key + ": conserva Noticed");
            }
            if ("Fallen".equals(key)) {
                check(!data.isFallen(4), "parcial sin Fallen: default");
                check(data.isSiegeResolved(4), "parcial sin Fallen: Resolved conserva el 4 ya resuelto");
            } else {
                check(data.isFallen(4), "parcial sin " + key + ": conserva Fallen");
            }
            if ("Pressure".equals(key)) {
                check(data.getPressureTicks(0) == 0, "parcial sin Pressure: default");
            } else {
                check(data.getPressureTicks(0) == 500, "parcial sin " + key + ": conserva Pressure");
            }
            if ("FarmOwners".equals(key)) {
                check(data.getFarmOwner(FARM_CENTER, 0) == null, "parcial sin FarmOwners: default");
            } else {
                check(ALICE.equals(data.getFarmOwner(FARM_CENTER, 0)), "parcial sin " + key + ": conserva FarmOwners");
            }
            if ("Settlement".equals(key)) {
                check(data.getHealth(5) == VillageSavedData.HEALTH_UNKNOWN, "parcial sin Settlement: salud default");
                check(data.getFood(5) == 0 && data.getPantryCredit(5) == 0, "parcial sin Settlement: comida default");
                check(data.getCentre(5) == null, "parcial sin Settlement: centro default");
                check(!data.isRemesaInicialEntregada(5), "parcial sin Settlement: remesa default");
            } else {
                check(data.getHealth(5) == 7 && data.getFood(5) == 9 && data.getPantryCredit(5) == 4,
                        "parcial sin " + key + ": conserva Settlement");
                check(VILLAGE_CENTER.equals(data.getCentre(5)), "parcial sin " + key + ": conserva centro");
            }
            if ("Blueprints".equals(key)) {
                check(!data.hasBlueprint(0), "parcial sin Blueprints: default");
            } else {
                check(data.hasBlueprint(0), "parcial sin " + key + ": conserva Blueprints");
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 7 · Datos incompletos / inconsistentes
    // ---------------------------------------------------------------------------------------------

    private static void corruptData(RegistryAccess.Frozen registries) {
        System.out.println("[7] Datos corruptos/inconsistentes");

        // --- FarmOwners con UUID corrupto: se descarta, no revienta.
        CompoundTag farmRoot = new CompoundTag();
        ListTag owners = new ListTag();
        CompoundTag bad = new CompoundTag();
        bad.putString("Key", "0#0");
        bad.putString("Owner", "no-es-un-uuid");
        owners.add(bad);
        CompoundTag good = new CompoundTag();
        good.putString("Key", "0#1");
        good.putString("Owner", BOB.toString());
        owners.add(good);
        farmRoot.put("FarmOwners", owners);
        Throwable farmError = null;
        VillageSavedData farmData = null;
        try {
            farmData = VillageSavedData.load(farmRoot, registries);
        } catch (Throwable t) {
            farmError = t;
        }
        check(farmError == null, "corrupto FarmOwners: load no lanza (" + farmError + ")");
        if (farmData != null) {
            CompoundTag roundtrip = farmData.save(new CompoundTag(), registries);
            ListTag remaining = roundtrip.getList("FarmOwners", Tag.TAG_COMPOUND);
            check(remaining.size() == 1, "corrupto FarmOwners: UUID corrupto descartado");
            check(BOB.toString().equals(((CompoundTag) remaining.get(0)).getString("Owner")),
                    "corrupto FarmOwners: sobrevive el UUID valido");
        }

        // --- Blueprint con Pos/State desalineados: hoy stateAt lanza IOOBE.
        CompoundTag misalignedRoot = new CompoundTag();
        ListTag misalignedList = new ListTag();
        CompoundTag misaligned = new CompoundTag();
        misaligned.putInt("Index", 0);
        misaligned.put("Palette", paletteOf(Blocks.OAK_PLANKS.defaultBlockState()));
        misaligned.putLongArray("Pos", new long[]{new BlockPos(0, 64, 0).asLong(), new BlockPos(1, 64, 0).asLong()});
        misaligned.putIntArray("State", new int[]{0});
        misalignedList.add(misaligned);
        misalignedRoot.put("Blueprints", misalignedList);
        Throwable misalignedError = null;
        VillageSavedData misalignedData = null;
        try {
            misalignedData = VillageSavedData.load(misalignedRoot, registries);
        } catch (Throwable t) {
            misalignedError = t;
        }
        check(misalignedError == null, "corrupto plano desalineado: load no lanza (" + misalignedError + ")");
        if (misalignedData != null) {
            boolean safe = true;
            String detail = "";
            if (misalignedData.hasBlueprint(0)) {
                try {
                    for (int i = 0; i < misalignedData.getBlueprint(0).size(); i++) {
                        misalignedData.getBlueprint(0).stateAt(i);
                    }
                } catch (Throwable t) {
                    safe = false;
                    detail = t.toString();
                }
            }
            check(safe, "corrupto plano desalineado: recorrerlo no lanza IOOBE (" + detail + ")");
            check(!misalignedData.hasBlueprint(0),
                    "corrupto plano desalineado: se descarta para recapturarlo");
        }

        // --- Blueprint con indice de paleta fuera de rango.
        CompoundTag outOfRangeRoot = new CompoundTag();
        ListTag outOfRangeList = new ListTag();
        CompoundTag outOfRange = new CompoundTag();
        outOfRange.putInt("Index", 1);
        outOfRange.put("Palette", paletteOf(Blocks.OAK_PLANKS.defaultBlockState()));
        outOfRange.putLongArray("Pos", new long[]{new BlockPos(4, 64, 4).asLong()});
        outOfRange.putIntArray("State", new int[]{5});
        outOfRangeList.add(outOfRange);
        outOfRangeRoot.put("Blueprints", outOfRangeList);
        Throwable outOfRangeError = null;
        VillageSavedData outOfRangeData = null;
        try {
            outOfRangeData = VillageSavedData.load(outOfRangeRoot, registries);
        } catch (Throwable t) {
            outOfRangeError = t;
        }
        check(outOfRangeError == null, "corrupto plano indice fuera: load no lanza (" + outOfRangeError + ")");
        if (outOfRangeData != null) {
            boolean safe = true;
            if (outOfRangeData.hasBlueprint(1)) {
                try {
                    outOfRangeData.getBlueprint(1).stateAt(0);
                } catch (Throwable t) {
                    safe = false;
                }
            }
            check(safe, "corrupto plano indice fuera: stateAt no lanza");
            check(!outOfRangeData.hasBlueprint(1), "corrupto plano indice fuera: se descarta");
        }

        // --- Blueprint con indice de paleta NEGATIVO: tambien debe descartarse (state < 0).
        CompoundTag negativeRoot = new CompoundTag();
        ListTag negativeList = new ListTag();
        CompoundTag negative = new CompoundTag();
        negative.putInt("Index", 2);
        negative.put("Palette", paletteOf(Blocks.OAK_PLANKS.defaultBlockState()));
        negative.putLongArray("Pos", new long[]{new BlockPos(6, 64, 6).asLong()});
        negative.putIntArray("State", new int[]{-1});
        negativeList.add(negative);
        negativeRoot.put("Blueprints", negativeList);
        Throwable negativeError = null;
        VillageSavedData negativeData = null;
        try {
            negativeData = VillageSavedData.load(negativeRoot, registries);
        } catch (Throwable t) {
            negativeError = t;
        }
        check(negativeError == null, "corrupto plano indice negativo: load no lanza (" + negativeError + ")");
        check(negativeData != null && !negativeData.hasBlueprint(2),
                "corrupto plano indice negativo: se descarta");

        // --- Palette con Name que no es un ResourceLocation valido: hoy lanza al cargar.
        CompoundTag badPaletteRoot = new CompoundTag();
        ListTag badPaletteList = new ListTag();
        CompoundTag badPalette = new CompoundTag();
        badPalette.putInt("Index", 2);
        ListTag palette = new ListTag();
        CompoundTag badState = new CompoundTag();
        badState.putString("Name", "NOT A VALID ID!!!");
        palette.add(badState);
        badPalette.put("Palette", palette);
        badPalette.putLongArray("Pos", new long[]{new BlockPos(8, 64, 8).asLong()});
        badPalette.putIntArray("State", new int[]{0});
        badPaletteList.add(badPalette);
        badPaletteRoot.put("Blueprints", badPaletteList);
        Throwable badPaletteError = null;
        VillageSavedData badPaletteData = null;
        try {
            badPaletteData = VillageSavedData.load(badPaletteRoot, registries);
        } catch (Throwable t) {
            badPaletteError = t;
        }
        check(badPaletteError == null, "corrupto palette Name invalido: load no lanza (" + badPaletteError + ")");
        if (badPaletteData != null) {
            check(!badPaletteData.hasBlueprint(2), "corrupto palette Name invalido: plano descartado");
        }

        // --- Palette VACIA con celdas: el indice 0 ya se sale de la paleta (variante de "paleta
        //     mas corta que states").
        CompoundTag emptyPaletteRoot = new CompoundTag();
        ListTag emptyPaletteList = new ListTag();
        CompoundTag emptyPalette = new CompoundTag();
        emptyPalette.putInt("Index", 5);
        emptyPalette.put("Palette", new ListTag());
        emptyPalette.putLongArray("Pos", new long[]{new BlockPos(11, 64, 11).asLong()});
        emptyPalette.putIntArray("State", new int[]{0});
        emptyPaletteList.add(emptyPalette);
        emptyPaletteRoot.put("Blueprints", emptyPaletteList);
        Throwable emptyPaletteError = null;
        VillageSavedData emptyPaletteData = null;
        try {
            emptyPaletteData = VillageSavedData.load(emptyPaletteRoot, registries);
        } catch (Throwable t) {
            emptyPaletteError = t;
        }
        check(emptyPaletteError == null, "corrupto palette vacia: load no lanza (" + emptyPaletteError + ")");
        if (emptyPaletteData != null) {
            check(!emptyPaletteData.hasBlueprint(5), "corrupto palette vacia: plano descartado");
        }

        // --- NOTA: una lista NBT es homogenea por formato (ListTag.add lanza
        //     "Trying to add tag of type 3 to list of 10"), asi que una "palette mixta" no se puede
        //     construir ni existe en un fichero NBT valido. No se inventa ese caso.

        // --- Bloque desconocido pero sintacticamente valido: NbtUtils lo convierte en AIR (no lanza).
        CompoundTag unknownRoot = new CompoundTag();
        ListTag unknownList = new ListTag();
        CompoundTag unknown = new CompoundTag();
        unknown.putInt("Index", 3);
        CompoundTag unknownState = new CompoundTag();
        unknownState.putString("Name", "minecraft:not_a_real_block");
        ListTag unknownPalette = new ListTag();
        unknownPalette.add(unknownState);
        unknown.put("Palette", unknownPalette);
        unknown.putLongArray("Pos", new long[]{new BlockPos(9, 64, 9).asLong()});
        unknown.putIntArray("State", new int[]{0});
        unknownList.add(unknown);
        unknownRoot.put("Blueprints", unknownList);
        VillageSavedData unknownData = VillageSavedData.load(unknownRoot, registries);
        check(unknownData.hasBlueprint(3), "palette bloque desconocido: se conserva el plano");
        check(unknownData.getBlueprint(3).stateAt(0).isAir(),
                "palette bloque desconocido: NbtUtils.readBlockState -> AIR");

        // --- Propiedad invalida: NbtUtils avisa y deja el valor por defecto (no lanza).
        CompoundTag badPropertyRoot = new CompoundTag();
        ListTag badPropertyList = new ListTag();
        CompoundTag badProperty = new CompoundTag();
        badProperty.putInt("Index", 4);
        CompoundTag stairs = NbtUtils.writeBlockState(
                Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        ((CompoundTag) stairs.getCompound("Properties")).putString("facing", "north_east");
        ListTag stairsPalette = new ListTag();
        stairsPalette.add(stairs);
        badProperty.put("Palette", stairsPalette);
        badProperty.putLongArray("Pos", new long[]{new BlockPos(10, 64, 10).asLong()});
        badProperty.putIntArray("State", new int[]{0});
        badPropertyList.add(badProperty);
        badPropertyRoot.put("Blueprints", badPropertyList);
        VillageSavedData badPropertyData = VillageSavedData.load(badPropertyRoot, registries);
        check(badPropertyData.hasBlueprint(4), "palette propiedad invalida: se conserva el plano");
        check(badPropertyData.getBlueprint(4).stateAt(0).is(Blocks.OAK_STAIRS)
                        && badPropertyData.getBlueprint(4).stateAt(0).getValue(StairBlock.FACING) == Direction.NORTH,
                "palette propiedad invalida: queda el valor por defecto");

        // --- Settlement duplicado: la ultima entrada gana en los escalares; Remesa es monotona.
        CompoundTag dupRoot = new CompoundTag();
        ListTag dupList = new ListTag();
        CompoundTag first = new CompoundTag();
        first.putInt("Index", 3);
        first.putInt("Health", 4);
        first.putInt("Food", 7);
        first.putInt("GuardiasFundadoras", 0b0001);
        first.put("Center", NbtUtils.writeBlockPos(new BlockPos(1, 2, 3)));
        first.putBoolean("RemesaInicialEntregada", true);
        dupList.add(first);
        CompoundTag second = new CompoundTag();
        second.putInt("Index", 3);
        second.putInt("Health", 9);
        second.putInt("GuardiasFundadoras", 0b1000);
        second.put("Center", NbtUtils.writeBlockPos(new BlockPos(4, 5, 6)));
        second.putBoolean("RemesaInicialEntregada", false);
        dupList.add(second);
        dupRoot.put("Settlement", dupList);
        VillageSavedData dupData = VillageSavedData.load(dupRoot, registries);
        check(dupData.getHealth(3) == 9, "duplicado Settlement: gana el ultimo Health");
        check(dupData.getFood(3) == 0, "duplicado Settlement: la ultima entrada sin Food deja 0");
        check(new BlockPos(4, 5, 6).equals(dupData.getCentre(3)), "duplicado Settlement: gana el ultimo Center");
        check(dupData.isRemesaInicialEntregada(3), "duplicado Settlement: Remesa es monotona (true sobrevive)");
        check(settledMask(dupData.save(new CompoundTag(), registries), 3) == 0b1000,
                "duplicado Settlement: gana la ultima mascara");

        // La misma NBT dos veces da exactamente lo mismo (determinista).
        check(normalize(dupData.save(new CompoundTag(), registries))
                        .equals(normalize(VillageSavedData.load(dupRoot, registries).save(new CompoundTag(), registries))),
                "duplicado Settlement: carga determinista");

        // Orden inverso: los escalares cambian como dicta la regla, no de forma aleatoria.
        CompoundTag reversedRoot = new CompoundTag();
        ListTag reversedList = new ListTag();
        reversedList.add(second.copy());
        reversedList.add(first.copy());
        reversedRoot.put("Settlement", reversedList);
        VillageSavedData reversed = VillageSavedData.load(reversedRoot, registries);
        check(reversed.getHealth(3) == 4, "duplicado Settlement invertido: gana el otro Health");
        check(reversed.getFood(3) == 7, "duplicado Settlement invertido: ultima entrada con Food");
        check(new BlockPos(1, 2, 3).equals(reversed.getCentre(3)), "duplicado Settlement invertido: otro Center");

        // --- Claves desconocidas: se ignoran.
        CompoundTag unknownKeys = sampleState(registries).save(new CompoundTag(), registries);
        unknownKeys.putString("ClaveDelFuturo", "ignorar");
        CompoundTag firstSettlement = (CompoundTag) unknownKeys.getList("Settlement", Tag.TAG_COMPOUND).get(0);
        firstSettlement.putInt("CampoDelFuturo", 42);
        CompoundTag firstBlueprint = (CompoundTag) unknownKeys.getList("Blueprints", Tag.TAG_COMPOUND).get(0);
        firstBlueprint.putString("ExtraDelFuturo", "x");
        VillageSavedData unknownKeyData = VillageSavedData.load(unknownKeys, registries);
        check(unknownKeyData.getHealth(5) == 7, "claves desconocidas: no rompen Settlement");
        check(unknownKeyData.hasBlueprint(0), "claves desconocidas: no rompen Blueprints");

        // --- Fallen implica Resolved aunque el NBT no lo traiga.
        CompoundTag fallenOnly = new CompoundTag();
        fallenOnly.putIntArray("Fallen", new int[]{2});
        VillageSavedData fallenData = VillageSavedData.load(fallenOnly, registries);
        check(fallenData.isFallen(2) && fallenData.isSiegeResolved(2),
                "reconciliacion en carga: Fallen(2) implica Resolved(2)");

        // --- Tipos equivocados en secciones: no lanzan y dejan defaults.
        CompoundTag wrongTypes = new CompoundTag();
        wrongTypes.putString("Generated", "no soy un int[]");
        wrongTypes.putString("Resolved", "tampoco");
        wrongTypes.put("Noticed", new CompoundTag());
        wrongTypes.put("Pressure", new CompoundTag());
        wrongTypes.put("FarmOwners", StringTag.valueOf("no soy una lista"));
        wrongTypes.put("Settlement", new CompoundTag());
        wrongTypes.put("Blueprints", new CompoundTag());
        Throwable wrongTypeError = null;
        VillageSavedData wrongTypeData = null;
        try {
            wrongTypeData = VillageSavedData.load(wrongTypes, registries);
        } catch (Throwable t) {
            wrongTypeError = t;
        }
        check(wrongTypeError == null, "tipos equivocados: load no lanza (" + wrongTypeError + ")");
        if (wrongTypeData != null) {
            check(!wrongTypeData.isGenerated(0) && !wrongTypeData.isSiegeResolved(0),
                    "tipos equivocados: defaults");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 8 · Reconciliacion posterior a la carga + politicas
    // ---------------------------------------------------------------------------------------------

    private static void reconciliation(RegistryAccess.Frozen registries) {
        System.out.println("[8] Reconciliacion y politicas");

        // Fallen implica Resolved en el guardado: el siguiente save lo persiste.
        VillageSavedData fallen = VillageSavedData.load(new CompoundTag(), registries);
        fallen.markFallen(3);
        CompoundTag saved = fallen.save(new CompoundTag(), registries);
        check(Arrays.stream(saved.getIntArray("Resolved")).anyMatch(v -> v == 3),
                "reconciliacion: save persiste Resolved de una aldea caida");

        // Claim/release idempotente y persistente.
        VillageSavedData plots = VillageSavedData.load(new CompoundTag(), registries);
        check(plots.claimFarmPlot(FARM_CENTER, 0, ALICE), "parcela: primer claim");
        check(plots.claimFarmPlot(FARM_CENTER, 0, ALICE), "parcela: claim del mismo duenio es idempotente");
        check(!plots.claimFarmPlot(FARM_CENTER, 0, BOB), "parcela: otro duenio no puede reclamar");
        check(ALICE.equals(plots.getFarmOwner(FARM_CENTER, 0)), "parcela: el duenio no cambio");
        plots.releaseFarmPlot(FARM_CENTER, 0, BOB);
        check(ALICE.equals(plots.getFarmOwner(FARM_CENTER, 0)), "parcela: release de otro no borra");
        plots.releaseFarmPlot(FARM_CENTER, 0, ALICE);
        check(plots.getFarmOwner(FARM_CENTER, 0) == null, "parcela: release del duenio libera");
        plots.releaseFarmPlot(FARM_CENTER, 0, ALICE);
        check(plots.getFarmOwner(FARM_CENTER, 0) == null, "parcela: release repetido es idempotente");

        plots.claimFarmPlot(FARM_CENTER, 1, BOB);
        CompoundTag plotTag = plots.save(new CompoundTag(), registries);
        VillageSavedData plotBack = VillageSavedData.load(plotTag, registries);
        check(BOB.equals(plotBack.getFarmOwner(FARM_CENTER, 1)), "parcela: duenio persiste");
        plotBack.releaseFarmPlot(FARM_CENTER, 1, BOB);
        check(plotBack.getFarmOwner(FARM_CENTER, 1) == null, "parcela: liberar tras cargar funciona");

        // Duplicado historico de FarmOwners: no gana el ultimo, gana el UUID menor (compareTo de UUID,
        // no comparacion de String), sea cual sea el orden del NBT.
        String duplicatedKey = FARM_CENTER.asLong() + "#0";
        CompoundTag bobsThenAlice = new CompoundTag();
        ListTag bobsThenAliceList = new ListTag();
        bobsThenAliceList.add(farmOwnerEntry(duplicatedKey, BOB));
        bobsThenAliceList.add(farmOwnerEntry(duplicatedKey, ALICE));
        bobsThenAlice.put("FarmOwners", bobsThenAliceList);
        CompoundTag aliceThenBobs = new CompoundTag();
        ListTag aliceThenBobsList = new ListTag();
        aliceThenBobsList.add(farmOwnerEntry(duplicatedKey, ALICE));
        aliceThenBobsList.add(farmOwnerEntry(duplicatedKey, BOB));
        aliceThenBobs.put("FarmOwners", aliceThenBobsList);
        check(ALICE.equals(VillageSavedData.load(bobsThenAlice, registries).getFarmOwner(FARM_CENTER, 0)),
                "FarmOwners duplicado: gana el UUID menor (orden BOB,ALICE)");
        check(ALICE.equals(VillageSavedData.load(aliceThenBobs, registries).getFarmOwner(FARM_CENTER, 0)),
                "FarmOwners duplicado: gana el UUID menor (orden ALICE,BOB)");
        CompoundTag resolvedOnce = VillageSavedData.load(bobsThenAlice, registries).save(new CompoundTag(), registries);
        check(normalize(resolvedOnce).equals(normalize(
                        VillageSavedData.load(resolvedOnce, registries).save(new CompoundTag(), registries))),
                "FarmOwners duplicado: resolucion estable tras ciclo");

        // Ventana de gracia de carga de la poblacion.
        check(!VillagePopulationLoadPolicy.cargaAsentada(150L, 100L, 200),
                "gracia: antes de la ventana no se repuebla");
        check(VillagePopulationLoadPolicy.cargaAsentada(300L, 100L, 200),
                "gracia: justo en el limite si se repuebla");
        check(VillagePopulationLoadPolicy.cargaAsentada(301L, 100L, 200),
                "gracia: pasada la ventana si se repuebla");
        check(!VillagePopulationLoadPolicy.cargaAsentada(99L, 100L, 200),
                "gracia: reloj incoherente espera");
        check(VillagePopulationLoadPolicy.cargaAsentada(0L, -1L, 200),
                "gracia: sin primer latido no bloquea");
        check(VillagePopulationLoadPolicy.cargaAsentada(0L, 100L, 0),
                "gracia: gracia <= 0 no bloquea");

        // Politica de propiedad unica de JOB_SITE (pura, con POIs reales comprimidos).
        long poiA = new BlockPos(10, 64, 10).asLong();
        long poiB = new BlockPos(20, 64, 20).asLong();
        List<VillageJobSiteOwnershipPolicy.Registro> sharing = List.of(
                new VillageJobSiteOwnershipPolicy.Registro(ALICE, poiA),
                new VillageJobSiteOwnershipPolicy.Registro(BOB, poiA));
        Set<UUID> sobrantes = VillageJobSiteOwnershipPolicy.sobrantes(sharing);
        check(sobrantes.equals(Set.of(BOB)), "JOB_SITE: el UUID mayor es el sobrante");
        check(VillageJobSiteOwnershipPolicy.sobrantes(List.of(
                new VillageJobSiteOwnershipPolicy.Registro(ALICE, poiA),
                new VillageJobSiteOwnershipPolicy.Registro(BOB, poiB))).isEmpty(),
                "JOB_SITE: POIs distintos no se tocan");
        check(VillageJobSiteOwnershipPolicy.sobrantes(List.of(
                new VillageJobSiteOwnershipPolicy.Registro(ALICE, poiA),
                new VillageJobSiteOwnershipPolicy.Registro(ALICE, poiA))).isEmpty(),
                "JOB_SITE: el mismo UUID repetido no produce sobrantes");
    }

    // ---------------------------------------------------------------------------------------------
    // 9 · Identidad real: Villager + UUID + profesion + memoria JOB_SITE
    // ---------------------------------------------------------------------------------------------

    private static void identityAndJobSite(Level level) {
        System.out.println("[9] Identidad real de aldeano (Villager) y JOB_SITE");

        Villager villager = EntityType.VILLAGER.create(level);
        check(villager != null, "identidad: EntityType.VILLAGER.create devuelve entidad real");
        if (villager == null) {
            return;
        }
        villager.setUUID(ALICE);
        villager.setPos(10.5D, 64.0D, -20.5D);
        villager.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.FARMER, 3));
        GlobalPos jobSite = GlobalPos.of(Level.OVERWORLD, new BlockPos(12, 64, -18));
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, jobSite);

        CompoundTag tag = villager.saveWithoutId(new CompoundTag());
        check(ALICE.equals(tag.getUUID("UUID")), "identidad: UUID en NBT");
        check(ALICE.toString().equals(tag.getUUID("UUID").toString()), "identidad: UUID exacto en NBT");
        CompoundTag dataTag = tag.getCompound("VillagerData");
        check("minecraft:farmer".equals(dataTag.getString("profession")),
                "identidad: profesion persistida (farmer)");
        check("minecraft:plains".equals(dataTag.getString("type")), "identidad: tipo persistido (plains)");
        check(dataTag.getInt("level") == 3, "identidad: nivel de profesion persistido");

        CompoundTag memories = tag.getCompound("Brain").getCompound("memories");
        check(memories.contains("minecraft:job_site", Tag.TAG_COMPOUND),
                "identidad: memoria JOB_SITE en el NBT del Brain");
        CompoundTag jobTag = memories.getCompound("minecraft:job_site").getCompound("value");
        check("minecraft:overworld".equals(jobTag.getString("dimension")),
                "identidad: dimension del JOB_SITE exacta");
        check(Arrays.equals(jobTag.getIntArray("pos"), new int[]{12, 64, -18}),
                "identidad: posicion del JOB_SITE exacta");

        // Restauracion por el mismo mecanismo que usa el juego: EntityType.create(CompoundTag, Level).
        CompoundTag withId = tag.copy();
        withId.putString("id", "minecraft:villager");
        Optional<Entity> restoredOpt = EntityType.create(withId, level);
        check(restoredOpt.isPresent(), "identidad: EntityType.create(tag) restaura la entidad");
        if (restoredOpt.isPresent() && restoredOpt.get() instanceof Villager restored) {
            check(ALICE.equals(restored.getUUID()), "identidad: UUID estable tras restaurar");
            check(ALICE.toString().equals(restored.getUUID().toString()), "identidad: UUID exacto tras restaurar");
            check(restored.getVillagerData().getProfession() == VillagerProfession.FARMER,
                    "identidad: profesion estable tras restaurar");
            check(restored.getVillagerData().getLevel() == 3, "identidad: nivel estable tras restaurar");
            Optional<GlobalPos> memory = restored.getBrain().getMemory(MemoryModuleType.JOB_SITE);
            check(memory.isPresent(), "identidad: JOB_SITE sigue en el Brain tras restaurar");
            check(memory.map(jobSite::equals).orElse(false), "identidad: JOB_SITE exacto tras restaurar");

            CompoundTag second = restored.saveWithoutId(new CompoundTag());
            check(tag.equals(second), "identidad: save/load/save byte a byte igual");
            check(ALICE.equals(second.getUUID("UUID")), "identidad: segundo ciclo conserva UUID");

            // Fichero real: el aldeano sobrevive a writeCompressed/readCompressed.
            try {
                Path dir = Path.of("build", "av", "village-persistence");
                Files.createDirectories(dir);
                Path file = dir.resolve("villager-identity.nbt");
                NbtIo.writeCompressed(withId, file);
                CompoundTag fromDisk = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
                Optional<Entity> fromFileOpt = EntityType.create(fromDisk, level);
                check(fromFileOpt.isPresent(), "identidad: el aldeano se restaura desde fichero");
                if (fromFileOpt.isPresent() && fromFileOpt.get() instanceof Villager fromFile) {
                    check(ALICE.equals(fromFile.getUUID()), "identidad: UUID exacto desde fichero");
                    check(fromFile.getVillagerData().getProfession() == VillagerProfession.FARMER,
                            "identidad: profesion desde fichero");
                    check(fromFile.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                                    .map(jobSite::equals).orElse(false),
                            "identidad: JOB_SITE desde fichero");
                }
            } catch (Throwable t) {
                check(false, "identidad: fichero del aldeano fallo: " + t);
            }
        }

        // Dos aldeanos con el mismo POI: la politica deja uno, se borra la memoria del sobrante y
        // tras guardar/cargar no reaparece el duplicado.
        Villager other = EntityType.VILLAGER.create(level);
        check(other != null, "identidad: segundo aldeano real");
        if (other != null) {
            other.setUUID(BOB);
            other.setPos(30.5D, 64.0D, 5.5D);
            other.setVillagerData(new VillagerData(VillagerType.TAIGA, VillagerProfession.NITWIT, 2));
            other.getBrain().setMemory(MemoryModuleType.JOB_SITE, jobSite);

            long poi = jobSite.pos().asLong();
            Set<UUID> duplicates = VillageJobSiteOwnershipPolicy.sobrantes(List.of(
                    new VillageJobSiteOwnershipPolicy.Registro(ALICE, poi),
                    new VillageJobSiteOwnershipPolicy.Registro(BOB, poi)));
            check(duplicates.equals(Set.of(BOB)), "JOB_SITE: hay duplicado antes de reconciliar");
            for (UUID sobrante : duplicates) {
                if (sobrante.equals(other.getUUID())) {
                    other.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
                }
            }
            check(villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent(),
                    "JOB_SITE: el titular conserva la memoria");
            check(other.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(),
                    "JOB_SITE: el sobrante pierde la memoria");

            CompoundTag firstTag = withId.copy();
            CompoundTag otherTag = other.saveWithoutId(new CompoundTag());
            otherTag.putString("id", "minecraft:villager");
            Villager firstBack = (Villager) EntityType.create(firstTag, level).orElse(null);
            Villager otherBack = (Villager) EntityType.create(otherTag, level).orElse(null);
            check(firstBack != null && otherBack != null, "JOB_SITE: ambos aldeanos se restauran");
            if (firstBack != null && otherBack != null) {
                check(firstBack.getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent(),
                        "JOB_SITE: titular tras ciclo");
                check(otherBack.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(),
                        "JOB_SITE: sobrante sin memoria tras ciclo");
                check(otherBack.getVillagerData().getProfession() == VillagerProfession.NITWIT,
                        "JOB_SITE: segunda profesion (nitwit) persistida");
                Set<UUID> again = VillageJobSiteOwnershipPolicy.sobrantes(List.of(
                        new VillageJobSiteOwnershipPolicy.Registro(firstBack.getUUID(),
                                firstBack.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElseThrow().pos().asLong()),
                        new VillageJobSiteOwnershipPolicy.Registro(otherBack.getUUID(),
                                otherBack.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                                        .map(gp -> gp.pos().asLong()).orElse(0L))));
                check(again.isEmpty(), "JOB_SITE: la politica no ve duplicados tras dos ciclos");
                check(!BOB.equals(firstBack.getUUID()) && !ALICE.equals(otherBack.getUUID()),
                        "JOB_SITE: no se intercambian identidades");
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 10 · Invariantes del censo sobre identidades persistidas
    // ---------------------------------------------------------------------------------------------

    private static void rosterInvariants(Level level) {
        System.out.println("[10] Invariantes del censo sobre NBT real");
        List<VillageRosterInvariants.Miembro> roster = new ArrayList<>();
        List<Villager> villagers = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            Villager v = EntityType.VILLAGER.create(level);
            check(v != null, "censo: aldeano profesional " + i + " creado");
            if (v == null) {
                return;
            }
            v.setUUID(uuidFor(i + 10));
            v.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.FARMER, 1 + (i % 5)));
            BlockPos station = new BlockPos(200 + i, 64, 300);
            v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(Level.OVERWORLD, station));
            villagers.add(v);
            roster.add(new VillageRosterInvariants.Miembro(v.getUUID(), professionName(v), station.asLong(), false));
        }
        for (int i = 0; i < 4; i++) {
            Villager v = EntityType.VILLAGER.create(level);
            check(v != null, "censo: guardia fundador " + i + " creado");
            if (v == null) {
                return;
            }
            v.setUUID(uuidFor(100 + i));
            v.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.NONE, 1));
            villagers.add(v);
            roster.add(new VillageRosterInvariants.Miembro(v.getUUID(), professionName(v), 0L, true));
        }

        Map<String, Integer> cupo = Map.of("minecraft:farmer", 12);
        VillageRosterInvariants.Resultado clean = VillageRosterInvariants.comprobar(roster, 12, cupo, 4);
        check(clean.ok(), "censo: 12 profesionales + 4 fundadores sin violaciones " + clean.violaciones());

        // Dos ciclos NBT por cada aldeano y reconstruccion del censo desde lo persistido.
        List<VillageRosterInvariants.Miembro> afterCycles = new ArrayList<>();
        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < villagers.size(); i++) {
                Villager source = villagers.get(i);
                CompoundTag tag = source.saveWithoutId(new CompoundTag());
                tag.putString("id", "minecraft:villager");
                Villager back = (Villager) EntityType.create(tag, level).orElse(null);
                if (back == null) {
                    check(false, "censo: aldeano " + i + " no se restauro en la ronda " + round);
                    return;
                }
                villagers.set(i, back);
            }
        }
        for (Villager v : villagers) {
            boolean founder = v.getVillagerData().getProfession() == VillagerProfession.NONE;
            long puesto = v.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                    .map(gp -> gp.pos().asLong()).orElse(0L);
            afterCycles.add(new VillageRosterInvariants.Miembro(v.getUUID(), professionName(v), puesto, founder));
        }
        VillageRosterInvariants.Resultado persisted = VillageRosterInvariants.comprobar(afterCycles, 12, cupo, 4);
        check(persisted.ok(), "censo: tras dos ciclos NBT sigue coherente " + persisted.violaciones());
        check(VillageJobSiteOwnershipPolicy.sobrantes(afterCycles.stream()
                        .filter(m -> m.puesto() != 0L)
                        .map(m -> new VillageJobSiteOwnershipPolicy.Registro(m.uuid(), m.puesto()))
                        .toList()).isEmpty(),
                "censo: ningun JOB_SITE duplicado tras dos ciclos");

        // El verificador no es un adorno: detecta UUID duplicado, puesto duplicado y cupo excedido.
        List<VillageRosterInvariants.Miembro> dupUuid = new ArrayList<>(roster);
        dupUuid.set(1, new VillageRosterInvariants.Miembro(dupUuid.get(0).uuid(),
                dupUuid.get(1).profesion(), dupUuid.get(1).puesto(), false));
        check(!VillageRosterInvariants.comprobar(dupUuid, 12, cupo, 4).ok(),
                "censo: detecta UUID duplicado");

        List<VillageRosterInvariants.Miembro> dupPuesto = new ArrayList<>(roster);
        dupPuesto.set(1, new VillageRosterInvariants.Miembro(dupPuesto.get(1).uuid(),
                dupPuesto.get(1).profesion(), dupPuesto.get(0).puesto(), false));
        check(!VillageRosterInvariants.comprobar(dupPuesto, 12, cupo, 4).ok(),
                "censo: detecta puesto duplicado");

        check(!VillageRosterInvariants.comprobar(roster, 12, Map.of("minecraft:farmer", 11), 4).ok(),
                "censo: detecta cupo excedido");
        check(!VillageRosterInvariants.comprobar(roster, 13, cupo, 4).ok(),
                "censo: detecta profesionales que faltan");
        check(!VillageRosterInvariants.comprobar(roster, 12, cupo, 5).ok(),
                "censo: detecta fundadores que faltan");
    }

    // ---------------------------------------------------------------------------------------------
    // Infraestructura de prueba
    // ---------------------------------------------------------------------------------------------

    private static void check(boolean ok, String name) {
        total++;
        if (!ok) {
            fallos++;
            System.out.println("  FALLO: " + name);
        }
    }

    private static UUID uuidFor(int value) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012x", value));
    }

    private static String professionName(Villager villager) {
        ResourceLocation key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(
                villager.getVillagerData().getProfession());
        return key == null ? "" : key.toString();
    }

    private static ListTag paletteOf(BlockState state) {
        ListTag palette = new ListTag();
        palette.add(NbtUtils.writeBlockState(state));
        return palette;
    }

    private static CompoundTag farmOwnerEntry(String key, UUID owner) {
        CompoundTag entry = new CompoundTag();
        entry.putString("Key", key);
        entry.putString("Owner", owner.toString());
        return entry;
    }

    /**
     * Normaliza SOLO el orden no determinista de los mapas internos (HashSet/HashMap): ordena los
     * int[] y las listas por su clave, y las claves Noticed. No oculta diferencias de contenido: el
     * CompoundTag normalizado sigue comparando todos los valores.
     */
    private static CompoundTag normalize(CompoundTag tag) {
        CompoundTag copy = tag.copy();
        copy.putIntArray("Generated", sorted(tag.getIntArray("Generated")));
        copy.putIntArray("Resolved", sorted(tag.getIntArray("Resolved")));
        copy.putIntArray("Occupied", sorted(tag.getIntArray("Occupied")));
        copy.putIntArray("Fallen", sorted(tag.getIntArray("Fallen")));

        List<String> noticed = new ArrayList<>();
        for (Tag t : tag.getList("Noticed", Tag.TAG_STRING)) {
            noticed.add(t.getAsString());
        }
        Collections.sort(noticed);
        ListTag noticedTag = new ListTag();
        for (String s : noticed) {
            noticedTag.add(StringTag.valueOf(s));
        }
        copy.put("Noticed", noticedTag);

        copy.put("FarmOwners", sortedStringList(tag.getList("FarmOwners", Tag.TAG_COMPOUND), "Key"));
        copy.put("Pressure", sortedIntList(tag.getList("Pressure", Tag.TAG_COMPOUND), "Index"));
        copy.put("Settlement", sortedIntList(tag.getList("Settlement", Tag.TAG_COMPOUND), "Index"));
        copy.put("Blueprints", sortedIntList(tag.getList("Blueprints", Tag.TAG_COMPOUND), "Index"));
        return copy;
    }

    private static int[] sorted(int[] values) {
        int[] copy = values.clone();
        Arrays.sort(copy);
        return copy;
    }

    private static ListTag sortedIntList(ListTag list, String key) {
        List<CompoundTag> entries = new ArrayList<>();
        for (Tag t : list) {
            entries.add((CompoundTag) t);
        }
        entries.sort(Comparator.comparingInt(t -> t.getInt(key)));
        ListTag out = new ListTag();
        for (CompoundTag entry : entries) {
            out.add(entry);
        }
        return out;
    }

    private static ListTag sortedStringList(ListTag list, String key) {
        List<CompoundTag> entries = new ArrayList<>();
        for (Tag t : list) {
            entries.add((CompoundTag) t);
        }
        entries.sort(Comparator.comparing(t -> t.getString(key)));
        ListTag out = new ListTag();
        for (CompoundTag entry : entries) {
            out.add(entry);
        }
        return out;
    }

    // ---------------------------------------------------------------------------------------------
    // Registros reales que el arranque normal rellena y aqui hay que completar (NeoForge)
    // ---------------------------------------------------------------------------------------------

    private static RegistryAccess.Frozen buildRegistries() {
        RegistryAccess.Frozen base = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        List<Registry<?>> all = new ArrayList<>();
        base.registries().forEach(entry -> all.add(entry.value()));
        all.add(damageTypesRegistry());
        injectNeoForgeRegistries();
        return new RegistryAccess.ImmutableRegistryAccess(all).freeze();
    }

    /**
     * {@code BuiltInRegistries.REGISTRY} no incluye los registros de datapack; {@code DamageSources}
     * (constructor de {@code Level}) resuelve ~24 tipos de danio en el acto. Se bootstrapean los reales
     * con {@code DamageTypes.bootstrap} en un registro propio. Es el mismo mecanismo de vanilla, no una
     * copia a mano de los valores.
     */
    private static Registry<DamageType> damageTypesRegistry() {
        MappedRegistry<DamageType> registry = new MappedRegistry<>(Registries.DAMAGE_TYPE, Lifecycle.stable());
        BootstrapContext<DamageType> context = new BootstrapContext<>() {
            @Override
            public Holder.Reference<DamageType> register(ResourceKey<DamageType> key, DamageType value,
                                                        Lifecycle lifecycle) {
                return registry.register(key, value, RegistrationInfo.BUILT_IN);
            }

            @Override
            public <S> HolderGetter<S> lookup(ResourceKey<? extends Registry<? extends S>> key) {
                return emptyHolderGetter();
            }
        };
        try {
            Method bootstrap = DamageTypes.class.getDeclaredMethod("bootstrap", BootstrapContext.class);
            bootstrap.setAccessible(true);
            bootstrap.invoke(null, context);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo bootstrapear DamageTypes", e);
        }
        return registry;
    }

    private static <S> HolderGetter<S> emptyHolderGetter() {
        return new HolderGetter<>() {
            @Override
            public Optional<Holder.Reference<S>> get(ResourceKey<S> key) {
                return Optional.empty();
            }

            @Override
            public Optional<HolderSet.Named<S>> get(TagKey<S> tag) {
                return Optional.empty();
            }
        };
    }

    /**
     * En un arranque normal, NeoForge registra {@code neoforge:fluid_type} y sus atributos. Sin ellos
     * el constructor de {@code Entity}/{@code LivingEntity} falla. Se registran los MINIMOS reales que
     * pide la cadena de construccion del aldeano; no se sustituye la entidad por una copia.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void injectNeoForgeRegistries() {
        MappedRegistry<Registry<?>> root = (MappedRegistry) BuiltInRegistries.REGISTRY;
        root.unfreeze();
        try {
            MappedRegistry<FluidType> fluidTypes = new MappedRegistry<>(
                    NeoForgeRegistries.Keys.FLUID_TYPES, Lifecycle.stable());
            fluidTypes.register(
                    ResourceKey.create(NeoForgeRegistries.Keys.FLUID_TYPES,
                            ResourceLocation.withDefaultNamespace("empty")),
                    new FluidType(FluidType.Properties.create()),
                    RegistrationInfo.BUILT_IN);
            root.register((ResourceKey) NeoForgeRegistries.Keys.FLUID_TYPES, fluidTypes, RegistrationInfo.BUILT_IN);

            MappedRegistry<Attribute> attributes = (MappedRegistry) BuiltInRegistries.ATTRIBUTE;
            attributes.unfreeze();
            attributes.register(NeoForgeMod.SWIM_SPEED.getKey(),
                    new RangedAttribute("neoforge.swim_speed", 1.0D, 0.0D, 1024.0D).setSyncable(true),
                    RegistrationInfo.BUILT_IN);
            attributes.register(NeoForgeMod.NAMETAG_DISTANCE.getKey(),
                    new RangedAttribute("neoforge.name_tag_distance", 64.0D, 0.0D, 64.0D).setSyncable(true),
                    RegistrationInfo.BUILT_IN);
            attributes.register(NeoForgeMod.CREATIVE_FLIGHT.getKey(),
                    new RangedAttribute("neoforge.creative_flight", 0.0D, 0.0D, 1.0D).setSyncable(true),
                    RegistrationInfo.BUILT_IN);
            attributes.freeze();
        } finally {
            root.freeze();
        }
    }

    /** Level minimo para construir/restaurar entidades headless: sin chunks, sin servidor, sin POIs. */
    private static final class StubLevel extends Level {
        private float dayTimeFraction;
        private float dayTimePerTick = 1.0F;

        StubLevel(RegistryAccess registries) {
            super(new StubLevelData(),
                    Level.OVERWORLD,
                    registries,
                    Holder.direct(overworldDimensionType()),
                    () -> InactiveProfiler.INSTANCE,
                    false,
                    false,
                    0L,
                    1013904223);
        }

        @Override
        public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
            throw new UnsupportedOperationException("StubLevel sin chunks");
        }

        @Override
        public void playSeededSound(Player player, double x, double y, double z, Holder<SoundEvent> sound,
                                    SoundSource source, float volume, float pitch, long seed) {
            // Sin audio en headless.
        }

        @Override
        public void playSeededSound(Player player, Entity entity, Holder<SoundEvent> sound,
                                    SoundSource source, float volume, float pitch, long seed) {
            // Sin audio en headless.
        }

        @Override
        public String gatherChunkSourceStats() {
            return "stub";
        }

        @Override
        public Entity getEntity(int id) {
            return null;
        }

        @Override
        public TickRateManager tickRateManager() {
            return new TickRateManager();
        }

        @Override
        public MapItemSavedData getMapData(MapId id) {
            return null;
        }

        @Override
        public void setMapData(MapId id, MapItemSavedData data) {
        }

        @Override
        public MapId getFreeMapId() {
            return new MapId(0);
        }

        @Override
        public void destroyBlockProgress(int id, BlockPos pos, int progress) {
        }

        @Override
        public net.minecraft.world.scores.Scoreboard getScoreboard() {
            return new net.minecraft.world.scores.Scoreboard();
        }

        @Override
        public net.minecraft.world.item.crafting.RecipeManager getRecipeManager() {
            throw new UnsupportedOperationException("StubLevel sin recetas");
        }

        @Override
        protected LevelEntityGetter<Entity> getEntities() {
            return null;
        }

        @Override
        public PotionBrewing potionBrewing() {
            throw new UnsupportedOperationException("StubLevel sin pociones");
        }

        @Override
        public void setDayTimeFraction(float value) {
            this.dayTimeFraction = value;
        }

        @Override
        public float getDayTimeFraction() {
            return dayTimeFraction;
        }

        @Override
        public float getDayTimePerTick() {
            return dayTimePerTick;
        }

        @Override
        public void setDayTimePerTick(float value) {
            this.dayTimePerTick = value;
        }

        @Override
        public ChunkSource getChunkSource() {
            throw new UnsupportedOperationException("StubLevel sin chunks");
        }

        @Override
        public FeatureFlagSet enabledFeatures() {
            return FeatureFlags.DEFAULT_FLAGS;
        }

        @Override
        public void gameEvent(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context) {
        }

        @Override
        public long nextSubTickCount() {
            return 0L;
        }

        @Override
        public LevelTickAccess<net.minecraft.world.level.block.Block> getBlockTicks() {
            throw new UnsupportedOperationException("StubLevel sin ticks");
        }

        @Override
        public LevelTickAccess<net.minecraft.world.level.material.Fluid> getFluidTicks() {
            throw new UnsupportedOperationException("StubLevel sin ticks");
        }

        @Override
        public void addParticle(net.minecraft.core.particles.ParticleOptions options, double x, double y,
                                double z, double dx, double dy, double dz) {
        }

        @Override
        public void levelEvent(Player player, int type, BlockPos pos, int data) {
        }

        @Override
        public int getHeight(Heightmap.Types type, int x, int z) {
            return 0;
        }

        @Override
        public int getSkyDarken() {
            return 0;
        }

        @Override
        public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
            return null;
        }

        @Override
        public int getSeaLevel() {
            return 0;
        }

        @Override
        public List<? extends Player> players() {
            return List.of();
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            return 0.0F;
        }
    }

    private static DimensionType overworldDimensionType() {
        return new DimensionType(OptionalLong.empty(), true, false, false, true, 1.0D,
                true, false, -64, 384, 384, BlockTags.INFINIBURN_OVERWORLD,
                BuiltinDimensionTypes.OVERWORLD_EFFECTS, 0.0F,
                new DimensionType.MonsterSettings(false, true, UniformInt.of(0, 7), 0));
    }

    private static final class StubLevelData implements WritableLevelData {
        private final long gameTime = 1234L;
        private final long dayTime = 6000L;
        private final GameRules rules = new GameRules();

        @Override
        public void setSpawn(BlockPos pos, float angle) {
        }

        @Override
        public BlockPos getSpawnPos() {
            return BlockPos.ZERO;
        }

        @Override
        public float getSpawnAngle() {
            return 0.0F;
        }

        @Override
        public long getGameTime() {
            return gameTime;
        }

        @Override
        public long getDayTime() {
            return dayTime;
        }

        @Override
        public boolean isThundering() {
            return false;
        }

        @Override
        public boolean isRaining() {
            return false;
        }

        @Override
        public void setRaining(boolean raining) {
        }

        @Override
        public boolean isHardcore() {
            return false;
        }

        @Override
        public GameRules getGameRules() {
            return rules;
        }

        @Override
        public Difficulty getDifficulty() {
            return Difficulty.NORMAL;
        }

        @Override
        public boolean isDifficultyLocked() {
            return false;
        }
    }
}

