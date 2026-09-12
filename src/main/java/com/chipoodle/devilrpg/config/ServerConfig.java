package com.chipoodle.devilrpg.config;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * For configuration settings that change the behavior of code on the LOGICAL
 * SERVER. This can be moved to an inner class of ExampleModConfig, but is
 * separate because of personal preference and to keep the code organized
 *
 * @author Cadiboo
 */
final class ServerConfig {

    final ModConfigSpec.BooleanValue serverBoolean;
    final ModConfigSpec.ConfigValue<List<String>> serverStringList;
    final ModConfigSpec.ConfigValue<DyeColor> serverEnumDyeColor;

    final ModConfigSpec.IntValue electricFurnaceEnergySmeltCostPerTick;
    final ModConfigSpec.IntValue heatCollectorTransferAmountPerTick;

    final ModConfigSpec.IntValue wolfSpawnDistance;
    final ModConfigSpec.IntValue bearSpawnDistance;
    final ModConfigSpec.IntValue wispSpawnDistance;

    /** Cuánto puede subir la dificultad por la amenaza del mundo a su máximo (0.8 = +80%). */
    final ModConfigSpec.DoubleValue threatMaxExtraDifficulty;
    /** Horas JUGADAS (con el mundo cargado) hasta que la amenaza llega a su máximo. */
    final ModConfigSpec.DoubleValue threatFullHours;

    ServerConfig(final ModConfigSpec.Builder builder) {
        builder.push("general");
        serverBoolean = builder
                .comment("An example boolean in the server config")
                .translation(DevilRpg.MODID + ".config.serverBoolean")
                .define("serverBoolean", true);
        serverStringList = builder
                .comment("An example list of Strings in the server config")
                .translation(DevilRpg.MODID + ".config.serverStringList")
                .define("serverStringList", new ArrayList<>());
        serverEnumDyeColor = builder
                .comment("An example enum DyeColor in the server config")
                .translation(DevilRpg.MODID + ".config.serverEnumDyeColor")
                .defineEnum("serverEnumDyeColor", DyeColor.WHITE);
        electricFurnaceEnergySmeltCostPerTick = builder
                .comment("How much energy for the Electric Furnace to consume to smelt an item per tick")
                .translation(DevilRpg.MODID + ".config.electricFurnaceEnergySmeltCostPerTick")
                .defineInRange("electricFurnaceEnergySmeltCostPerTick", 100, 0, Integer.MAX_VALUE);
        heatCollectorTransferAmountPerTick = builder
                .comment("How much energy for the Heat Collector to try and transfer in each direction per tick")
                .translation(DevilRpg.MODID + ".config.heatCollectorTransferAmountPerTick")
                .defineInRange("heatCollectorTransferAmountPerTick", 100, 0, Integer.MAX_VALUE);

        wolfSpawnDistance = builder
                .comment("Spawn distance when summoning")
                .translation(DevilRpg.MODID + ".config.wolfSpawnDistance")
                .defineInRange("wolfSpawnDistance", 3, 0, Integer.MAX_VALUE);
        bearSpawnDistance = builder
                .comment("Spawn distance when summoning")
                .translation(DevilRpg.MODID + ".config.bearSpawnDistance")
                .defineInRange("bearSpawnDistance", 3, 0, Integer.MAX_VALUE);
        wispSpawnDistance = builder
                .comment("Spawn distance when summoning")
                .translation(DevilRpg.MODID + ".config.wispSpawnDistance")
                .defineInRange("wispSpawnDistance", 3, 0, Integer.MAX_VALUE);
        builder.pop();

        // --- Amenaza del mundo (escalación por TIEMPO; la de distancia es SpawnScaleProfile) ---
        builder.push("threat");
        threatMaxExtraDifficulty = builder
                .comment("Cuanto puede subir la dificultad por la amenaza a su maximo (0.8 = +80%).",
                        "Se multiplica ENCIMA del factor de distancia: a maxima distancia y amenaza maxima el",
                        "factor real de los enemigos es (1 + maxScaleMultiplier del perfil) x (1 + este valor).")
                .translation(DevilRpg.MODID + ".config.threatMaxExtraDifficulty")
                .defineInRange("threatMaxExtraDifficulty", 0.8D, 0.0D, 10.0D);
        threatFullHours = builder
                .comment("Horas JUGADAS (tiempo con el mundo cargado, no tiempo real) hasta que la amenaza",
                        "llega a su maximo. 3 = tres horas de partida. Con el juego cerrado no avanza.")
                .translation(DevilRpg.MODID + ".config.threatFullHours")
                .defineInRange("threatFullHours", 3.0D, 0.05D, 240.0D);
        builder.pop();
    }

}
