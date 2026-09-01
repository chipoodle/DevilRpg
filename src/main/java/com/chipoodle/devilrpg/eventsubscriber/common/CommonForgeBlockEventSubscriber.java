package com.chipoodle.devilrpg.eventsubscriber.common;

import net.neoforged.fml.common.EventBusSubscriber;
import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.block.SoulVineBlock;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

@EventBusSubscriber(modid = DevilRpg.MODID)
public class CommonForgeBlockEventSubscriber {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockEventEntityPlaceEvent(BlockEvent.EntityPlaceEvent event) {
        //DevilRpg.LOGGER.info("------------------>BlockForgeEventHandler.EntityPlaceEvent.");
        if (event.getPlacedBlock().getBlock() instanceof SoulVineBlock block) {
            //block.setCustomGrowthDirection(Objects.requireNonNull(placeBlockEvent.getEntity()).getDirection());
        }
    }

}
