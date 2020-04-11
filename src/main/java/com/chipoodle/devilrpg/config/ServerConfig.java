package com.chipoodle.devilrpg.config;

import java.util.ArrayList;
import java.util.List;

import com.chipoodle.devilrpg.DevilRpg;

import net.minecraft.item.DyeColor;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * For configuration settings that change the behavior of code on the LOGICAL
 * SERVER. This can be moved to an inner class of ExampleModConfig, but is
 * separate because of personal preference and to keep the code organized
 *
 * @author Cadiboo
 */
final class ServerConfig {

    final ForgeConfigSpec.BooleanValue serverBoolean;
    final ForgeConfigSpec.ConfigValue<List<String>> serverStringList;
    final ForgeConfigSpec.ConfigValue<DyeColor> serverEnumDyeColor;

    final ForgeConfigSpec.IntValue electricFurnaceEnergySmeltCostPerTick;
    final ForgeConfigSpec.IntValue heatCollectorTransferAmountPerTick;

    final ForgeConfigSpec.IntValue wolfSpawnDistance;
    final ForgeConfigSpec.IntValue bearSpawnDistance;
    final ForgeConfigSpec.IntValue wispSpawnDistance;
    
    ServerConfig(final ForgeConfigSpec.Builder builder) {
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
    }

}
