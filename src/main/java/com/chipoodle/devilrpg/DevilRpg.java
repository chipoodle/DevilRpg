package com.chipoodle.devilrpg;

import com.chipoodle.devilrpg.init.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.chipoodle.devilrpg.config.ConfigHolder;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(DevilRpg.MODID)
public class DevilRpg {

    public static final String MODID = "devilrpg";
    public static final Logger LOGGER = LogManager.getLogger(MODID);
   

    public DevilRpg() {
    	LOGGER.info("Initializing DevilRpg");

        final ModLoadingContext modLoadingContext = ModLoadingContext.get();
        final IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register Deferred Registers (Does not need to be before Configs)
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModSounds.SOUND_EVENTS.register(modEventBus);
        // Register Configs (Does not need to be after Deferred Registers)
        modLoadingContext.registerConfig(ModConfig.Type.CLIENT, ConfigHolder.CLIENT_SPEC);
        modLoadingContext.registerConfig(ModConfig.Type.SERVER, ConfigHolder.SERVER_SPEC);
        ModNetwork.register();
    }
}
