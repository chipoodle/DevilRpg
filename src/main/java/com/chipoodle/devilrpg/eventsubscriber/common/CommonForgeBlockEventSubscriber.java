package com.chipoodle.devilrpg.eventsubscriber.common;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.block.SoulVineBlock;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Objects;

@Mod.EventBusSubscriber(modid = DevilRpg.MODID)
public class CommonForgeBlockEventSubscriber {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockEventEntityPlaceEvent(BlockEvent event) {

        if (event instanceof BlockEvent.FluidPlaceBlockEvent placeBlockEvent) {
            //DevilRpg.LOGGER.info("------------------>BlockForgeEventHandler.FluidPlaceBlockEvent.");

        }

        if (event instanceof BlockEvent.EntityMultiPlaceEvent placeBlockEvent) {
            DevilRpg.LOGGER.info("------------------>BlockForgeEventHandler.EntityMultiPlaceEvent. Is Client? {}.", Objects.requireNonNull(placeBlockEvent.getEntity()).level.isClientSide);
        }

        if (event instanceof BlockEvent.EntityPlaceEvent placeBlockEvent) {
            DevilRpg.LOGGER.info("------------------>BlockForgeEventHandler.onBlockEventEntityPlaceEvent. Is Client? {}.", Objects.requireNonNull(placeBlockEvent.getEntity()).level.isClientSide);

            if (placeBlockEvent.getPlacedBlock().getBlock() instanceof SoulVineBlock block) {
                //block.setCustomGrowthDirection(Objects.requireNonNull(placeBlockEvent.getEntity()).getDirection());
            }
        }
    }

}
