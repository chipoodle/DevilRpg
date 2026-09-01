package com.chipoodle.devilrpg.eventsubscriber.common;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.init.ModBlockTags;
import net.minecraft.data.DataGenerator;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;


@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.MOD)
public class DataGeneratorsModEventSubscriber {
    private DataGeneratorsModEventSubscriber() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DevilRpg.LOGGER.info("----------------------->DataGeneratorsEventSubscriber.GatherDataEvent()");

        DataGenerator gen = event.getGenerator();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        ModBlockTags blockTags = new ModBlockTags(
                event.getGenerator().getPackOutput(),
                event.getLookupProvider(),
                DevilRpg.MODID,
                existingFileHelper);

        /* ModBlockTags blockTags = new ModBlockTags(gen, existingFileHelper);*/
        gen.addProvider(true, blockTags);
    }

}
