package com.chipoodle.devilrpg.config;


import net.minecraftforge.fml.config.ModConfig;

/**
 * This bakes the config values to normal fields
 *
 * @author Cadiboo It can be merged into the main DevilRpgConfig class, but is
 separate because of personal preference and to keep the code organised
 */
public final class ConfigHelper {

    public static void bakeClient(final ModConfig config) {
        DevilRpgConfig.clientBoolean = ConfigHolder.CLIENT.clientBoolean.get();
        DevilRpgConfig.clientStringList = ConfigHolder.CLIENT.clientStringList.get();
        DevilRpgConfig.clientDyeColorEnum = ConfigHolder.CLIENT.clientDyeColorEnum.get();

        DevilRpgConfig.modelTranslucency = ConfigHolder.CLIENT.modelTranslucency.get();
        DevilRpgConfig.modelScale = ConfigHolder.CLIENT.modelScale.get().floatValue();
    }

    public static void bakeServer(final ModConfig config) {
        DevilRpgConfig.serverBoolean = ConfigHolder.SERVER.serverBoolean.get();
        DevilRpgConfig.serverStringList = ConfigHolder.SERVER.serverStringList.get();
        DevilRpgConfig.serverEnumDyeColor = ConfigHolder.SERVER.serverEnumDyeColor.get();

        DevilRpgConfig.electricFurnaceEnergySmeltCostPerTick = ConfigHolder.SERVER.electricFurnaceEnergySmeltCostPerTick.get();
        DevilRpgConfig.heatCollectorTransferAmountPerTick = ConfigHolder.SERVER.heatCollectorTransferAmountPerTick.get();
        
        DevilRpgConfig.WOLF_SPAWN_DISTANCE = ConfigHolder.SERVER.wolfSpawnDistance.get();
        DevilRpgConfig.BEAR_SPAWN_DISTANCE = ConfigHolder.SERVER.bearSpawnDistance.get();
        DevilRpgConfig.WISP_SPAWN_DISTANCE = ConfigHolder.SERVER.wispSpawnDistance.get();
    }

}
