package com.chipoodle.devilrpg;

import com.chipoodle.devilrpg.config.ConfigHolder;
import com.chipoodle.devilrpg.init.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(DevilRpg.MODID)
public class DevilRpg {

    public static final String MODID = "devilrpg";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public DevilRpg(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Initializing DevilRpg");

        // Register Deferred Registers (Does not need to be before Configs)
        ModBlocks.BLOCKS.register(modEventBus);
        ModEntityBlocks.BLOCK_ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModSounds.SOUND_EVENTS.register(modEventBus);
        ModEffects.EFFECTS.register(modEventBus);
        ModContainers.CONTAINERS.register(modEventBus);
        ModCapabilities.ATTACHMENT_TYPES.register(modEventBus);
        ModCreativeTabs.CREATIVE_TABS.register(modEventBus);
        // Register Configs (Does not need to be after Deferred Registers)
        modContainer.registerConfig(ModConfig.Type.CLIENT, ConfigHolder.CLIENT_SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, ConfigHolder.SERVER_SPEC);
        // Register networking payloads
        modEventBus.addListener(ModNetwork::registerPayloads);
    }
}
